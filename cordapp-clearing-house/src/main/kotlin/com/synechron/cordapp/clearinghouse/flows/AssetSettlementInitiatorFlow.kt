package com.synechron.cordapp.clearinghouse.flows

import co.paralleluniverse.fibers.Suspendable
import com.synechron.cordapp.common.exception.InvalidPartyException
import com.synechron.cordapp.common.flows.IdentitySyncFlow
import com.synechron.cordapp.common.flows.ReceiveTransactionUnVerifiedFlow
import com.synechron.cordapp.contract.AssetTransferContract
import com.synechron.cordapp.flows.AbstractAssetSettlementFlow
import com.synechron.cordapp.state.AssetTransfer
import com.synechron.cordapp.state.RequestStatus
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.SendTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds

/**
 * Create new transaction to process the received transaction to settle [AssetTransfer] request.
 * It accepts the [AssetTransfer] state's [linearId] as input to start this flow and collects the [Cash] and [Asset] input and output states from counter-party.
 * For demo:
 * 1. Clearing House set requestStatus to [RequestStatus.TRANSFERRED] if everything is okay
 *    (i.e. by offline verifying the data of [AssetTransfer.asset] is valid).
 *
 * On successful completion of a flow, [Asset] state ownership is transferred to `Buyer` party
 * and [Cash] tokens equals to [Asset.purchaseCost] is transferred to `Seller` party.
 */
@StartableByRPC
class AssetSettlementInitiatorFlow(val linearId: UniqueIdentifier) : AbstractAssetSettlementFlow<SignedTransaction>() {
    companion object {
        object INITIALISING : ProgressTracker.Step("Performing initial steps.")
        object BUILDING : ProgressTracker.Step("Building and verifying transaction.")
        object COLLECT_STATES : ProgressTracker.Step("Collect Asset and Cash states from counterparty.")
        object IDENTITY_SYNC : ProgressTracker.Step("Sync identities with counter parties.") {
            override fun childProgressTracker() = IdentitySyncFlow.Send.tracker()
        }

        object SIGNING : ProgressTracker.Step("Signing transaction.")
        object COLLECTING : ProgressTracker.Step("Collecting counterparty signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING : ProgressTracker.Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(INITIALISING, BUILDING, COLLECT_STATES, IDENTITY_SYNC, SIGNING, COLLECTING, FINALISING)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = INITIALISING
        val inAssetTransfer = serviceHub.loadState(linearId, AssetTransfer::class.java)
        val participants = inAssetTransfer.state.data.participants
        val outAssetTransfer = inAssetTransfer.state.data.copy(status = RequestStatus.TRANSFERRED)

        if (ourIdentity.name != serviceHub.resolveIdentity(outAssetTransfer.clearingHouse!!).name) {
            throw InvalidPartyException("Flow must be initiated by Custodian.")
        }

        progressTracker.currentStep = BUILDING
        val txb = TransactionBuilder(inAssetTransfer.state.notary)
                .addInputState(inAssetTransfer)
                .addOutputState(outAssetTransfer, AssetTransferContract.ASSET_TRANSFER_CONTRACT_ID)
                .addCommand(AssetTransferContract.Commands.SettleRequest(), participants.map { it.owningKey })

        progressTracker.currentStep = COLLECT_STATES
        //Create temporary partial transaction.
        val tempPtx = serviceHub.signInitialTransaction(txb)
        val securitySellerSession = initiateFlow(serviceHub.resolveIdentity(outAssetTransfer.securitySeller))
        //Send partial transaction to Security Owner i.e. `Seller`.
        subFlow(SendTransactionFlow(securitySellerSession, tempPtx))
        //Receive transaction with input and output of Asset state.
        val assetPtx = subFlow(ReceiveTransactionUnVerifiedFlow(securitySellerSession))

        //Send partial transaction to `Buyer`.
        val securityBuyerSession = initiateFlow(serviceHub.resolveIdentity(outAssetTransfer.securityBuyer))
        subFlow(SendTransactionFlow(securityBuyerSession, tempPtx))
        //Send flows ID for soft lock of the cash state.
        securityBuyerSession.send(txb.lockId)
        //Receive and register the anonymous identity were created for Cash transfer.
        subFlow(net.corda.confidential.IdentitySyncFlow.Receive(securityBuyerSession))
        //Receive transaction with input and output state of Cash state.
        val cashPtx = subFlow(ReceiveTransactionUnVerifiedFlow(securityBuyerSession))

        //Add Asset states and commands to origin Transaction Builder `txb`.
        val assetLtx = assetPtx.toLedgerTransaction(serviceHub, false)
        assetLtx.inputs.forEach {
            txb.addInputState(it)
        }
        assetLtx.outputs.forEach {
            txb.addOutputState(it)
        }
        assetLtx.commands.forEach {
            txb.addCommand(Command(it.value, it.signers))
        }

        //Add Cash states and commands to origin Transaction Builder `txb`.
        val cashLtx = cashPtx.toLedgerTransaction(serviceHub, false)
        cashLtx.inputs.forEach {
            txb.addInputState(it)
        }
        cashLtx.outputs.forEach {
            txb.addOutputState(it)
        }
        cashLtx.commands.forEach {
            txb.addCommand(Command(it.value, it.signers))
        }

        progressTracker.currentStep = IDENTITY_SYNC
        val counterPartySessions = setOf(securityBuyerSession, securitySellerSession)
        subFlow(IdentitySyncFlow.Send(counterPartySessions,
                txb.toWireTransaction(serviceHub),
                IDENTITY_SYNC.childProgressTracker()))

        progressTracker.currentStep = SIGNING
        txb.setTimeWindow(serviceHub.clock.instant(), 60.seconds)
        val ptx = serviceHub.signInitialTransaction(txb, outAssetTransfer.clearingHouse!!.owningKey)

        progressTracker.currentStep = COLLECTING
        val stx = subFlow(CollectSignaturesFlow(
                ptx,
                counterPartySessions,
                listOf(outAssetTransfer.clearingHouse!!.owningKey),
                COLLECTING.childProgressTracker())
        )

        progressTracker.currentStep = FINALISING
        val ftx = subFlow(FinalityFlow(stx, FINALISING.childProgressTracker()))
        return ftx
    }
}

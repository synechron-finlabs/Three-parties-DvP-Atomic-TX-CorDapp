package com.synechron.cordapp.buyer.flows

import co.paralleluniverse.fibers.Suspendable
import com.synechron.cordapp.common.exception.InvalidPartyException
import com.synechron.cordapp.common.flows.IdentitySyncFlow
import com.synechron.cordapp.contract.AssetTransferContract
import com.synechron.cordapp.contract.AssetTransferContract.Companion.ASSET_TRANSFER_CONTRACT_ID
import com.synechron.cordapp.flows.AbstractConfirmAssetTransferRequestFlow
import com.synechron.cordapp.state.AssetTransfer
import com.synechron.cordapp.state.RequestStatus.PENDING
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.core.utilities.seconds

/**
 * The security buyer uses this flow to review and confirm received transaction from seller of security.
 * If everything is okay then `Buyer` party initiate this flow to send received transaction to `Clearing House` for further
 * verification and settlement.
 */
@StartableByRPC
class ConfirmAssetTransferRequestInitiatorFlow(val linearId: UniqueIdentifier, val clearingHouse: Party)
    : AbstractConfirmAssetTransferRequestFlow<SignedTransaction>() {
    companion object {
        object SWAP_IDENTITY : ProgressTracker.Step("Swap Identity.")
        object INITIALISING : ProgressTracker.Step("Performing initial steps.")
        object BUILDING : ProgressTracker.Step("Building and verifying transaction.")
        object SIGNING : Step("Signing transaction.")

        object IDENTITY_SYNC : Step("Sync identities with counter parties.") {
            override fun childProgressTracker() = IdentitySyncFlow.Send.tracker()
        }

        object COLLECTING : Step("Collecting counterparty signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING : Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(SWAP_IDENTITY, INITIALISING, BUILDING, SIGNING, IDENTITY_SYNC, COLLECTING, FINALISING)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = SWAP_IDENTITY
        val txKeys = subFlow(SwapIdentitiesFlow(clearingHouse))
        check(txKeys.size == 2) { "Something went wrong when generating confidential identities." }
        val anonymousCustodian = txKeys[clearingHouse]
                ?: throw FlowException("Couldn't create anonymous identity for `$clearingHouse` party.")

        progressTracker.currentStep = INITIALISING
        val input = serviceHub.loadState(linearId, AssetTransfer::class.java)
        val participants = input.state.data.participants + anonymousCustodian
        val output = input.state.data.copy(clearingHouse = anonymousCustodian, participants = participants, status = PENDING)

        if (ourIdentity.name != serviceHub.resolveIdentity(output.securityBuyer).name) {
            throw InvalidPartyException("Flow must be initiated by Lender Of Cash.")
        }
        //TODO verify clearingHouse party should not be one of [output.securityBuyer, output.securitySeller].

        progressTracker.currentStep = BUILDING
        val txb = TransactionBuilder(input.state.notary)
                .addInputState(input)
                .addOutputState(output, ASSET_TRANSFER_CONTRACT_ID)
                .addCommand(AssetTransferContract.Commands.ConfirmRequest(), participants.map { it.owningKey })
                .setTimeWindow(serviceHub.clock.instant(), 60.seconds)

        progressTracker.currentStep = SIGNING
        val ptx = serviceHub.signInitialTransaction(txb, output.securityBuyer.owningKey)

        //Get counterparty flow session.
        val counterPartySessions = participants.map { serviceHub.resolveIdentity(it) }.filter { it.name != ourIdentity.name }
                .map { initiateFlow(it) }.toSet()

        progressTracker.currentStep = IDENTITY_SYNC
        subFlow(IdentitySyncFlow.Send(
                counterPartySessions,
                txb.toWireTransaction(serviceHub),
                IDENTITY_SYNC.childProgressTracker())
        )

        // Step 5. Get the counter-party signatures.
        progressTracker.currentStep = COLLECTING
        val stx = subFlow(CollectSignaturesFlow(
                ptx,
                counterPartySessions,
                listOf(output.securityBuyer.owningKey),
                COLLECTING.childProgressTracker())
        )

        // Step 6. Finalise the transaction.
        progressTracker.currentStep = FINALISING
        val ftx = subFlow(FinalityFlow(stx, FINALISING.childProgressTracker()))
        return ftx
    }
}
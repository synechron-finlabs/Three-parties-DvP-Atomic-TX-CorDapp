package com.synechron.cordapp.seller.flows

import co.paralleluniverse.fibers.Suspendable
import com.synechron.cordapp.common.exception.TooManyStatesFoundException
import com.synechron.cordapp.common.flows.IdentitySyncFlow
import com.synechron.cordapp.common.flows.SignTxFlow
import com.synechron.cordapp.contract.AssetContract
import com.synechron.cordapp.flows.AbstractAssetSettlementFlow
import com.synechron.cordapp.flows.FlowLogicCommonMethods
import com.synechron.cordapp.state.AssetTransfer
import com.synechron.cordapp.utils.getAssetByCusip
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

/**
 * Seller review the received settlement transaction then create and send new temporary transaction
 * to send input, output [Asset] states and command to change ownership to `Buyer` party.
 */
@InitiatedBy(AbstractAssetSettlementFlow::class)
class AssetSettlementResponderFlow(val otherSideSession: FlowSession) : FlowLogic<SignedTransaction>(), FlowLogicCommonMethods {
    companion object {
        object ADD_ASSET : ProgressTracker.Step("Add Asset states to transaction builder.")
        object SYNC_IDENTITY : ProgressTracker.Step("Sync identities.")
    }

    override val progressTracker: ProgressTracker = ProgressTracker(ADD_ASSET, SYNC_IDENTITY)

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = ADD_ASSET
        val ptx1 = subFlow(ReceiveTransactionFlow(otherSideSession, false, StatesToRecord.NONE))
        val ltx1 = ptx1.toLedgerTransaction(serviceHub, false)

        val assetTransfer = ltx1.inputStates.filterIsInstance<AssetTransfer>().singleOrNull()
                ?: throw TooManyStatesFoundException("Transaction with more than one `AssetTransfer` " +
                "input states received from `${otherSideSession.counterparty}` party")
        val assetStateAndRef = serviceHub.getAssetByCusip(assetTransfer.asset.cusip)
        val (cmd, assetOutState) = assetStateAndRef.state.data.withNewOwner(assetTransfer.securityBuyer)

        val txb = TransactionBuilder(ltx1.notary)
        txb.addInputState(assetStateAndRef)
        txb.addOutputState(assetOutState, AssetContract.ASSET_CONTRACT_ID)
        txb.addCommand(Command(cmd, assetOutState.owner.owningKey))
        val ptx2 = serviceHub.signInitialTransaction(txb)
        //Send transaction with required in/out Asset state and command.
        subFlow(SendTransactionFlow(otherSideSession, ptx2))

        progressTracker.currentStep = SYNC_IDENTITY
        subFlow(IdentitySyncFlow.Receive(otherSideSession))

        val stx = subFlow(SignTxFlow(otherSideSession))
        return waitForLedgerCommit(stx.id)
    }
}
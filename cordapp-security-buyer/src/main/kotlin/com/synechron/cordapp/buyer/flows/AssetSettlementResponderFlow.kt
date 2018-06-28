package com.synechron.cordapp.buyer.flows

import co.paralleluniverse.fibers.Suspendable
import com.synechron.cordapp.common.exception.TooManyStatesFoundException
import com.synechron.cordapp.common.flows.IdentitySyncFlow
import com.synechron.cordapp.common.flows.SignTxFlow
import com.synechron.cordapp.flows.AbstractAssetSettlementFlow
import com.synechron.cordapp.state.AssetTransfer
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.finance.contracts.asset.Cash
import java.util.*

/**
 * Buyer review the received settlement transaction then issue the cash to `Seller` party.
 */
@InitiatedBy(AbstractAssetSettlementFlow::class)
class AssetSettlementResponderFlow(val otherSideSession: FlowSession) : FlowLogic<SignedTransaction>() {
    companion object {
        object ADD_CASH : ProgressTracker.Step("Add cash states.")
        object SYNC_IDENTITY : ProgressTracker.Step("Sync identities.")
    }

    override val progressTracker: ProgressTracker = ProgressTracker(ADD_CASH, SYNC_IDENTITY)

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = ADD_CASH
        val ptx1 = subFlow(ReceiveTransactionFlow(otherSideSession, false, StatesToRecord.NONE))
        val ltx1 = ptx1.toLedgerTransaction(serviceHub, false)
        val assetTransfer = ltx1.inputStates.filterIsInstance<AssetTransfer>().singleOrNull()
                ?: throw TooManyStatesFoundException("Transaction with more than one `AssetTransfer` " +
                "input states received from `${otherSideSession.counterparty}` party")
        //Using initiating flow id to soft lock reserve the Cash state.
        val initiatingFlowId = otherSideSession.receive<UUID>().unwrap { it }

        //TODO Does it required to send cashSignKeys to initiating party?
        //Issue cash to security owner i.e. `Seller` party.
        val (txbWithCash, cashSignKeys) = Cash.generateSpend(serviceHub,
                TransactionBuilder(notary = ltx1.notary, lockId = initiatingFlowId), //soft reserve the cash state.
                assetTransfer.asset.purchaseCost,
                ourIdentityAndCert,
                assetTransfer.securitySeller)

        val ptx2 = serviceHub.signInitialTransaction(txbWithCash)
        //Send the anonymous identity created for cash transfer request.
        subFlow(net.corda.confidential.IdentitySyncFlow.Send(otherSideSession, ptx2.tx))
        //Send the transaction that contain the Cash input and output states.
        subFlow(SendTransactionFlow(otherSideSession, ptx2))

        progressTracker.currentStep = SYNC_IDENTITY
        subFlow(IdentitySyncFlow.Receive(otherSideSession))

        val stx = subFlow(SignTxFlow(otherSideSession))
        return waitForLedgerCommit(stx.id)
    }
}

package com.synechron.cordapp.buyer.flows

import co.paralleluniverse.fibers.Suspendable
import com.synechron.cordapp.common.flows.SignTxFlow
import com.synechron.cordapp.flows.AbstractCreateAssetTransferRequestFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.transactions.SignedTransaction

@InitiatedBy(AbstractCreateAssetTransferRequestFlow::class)
class CreateAssetTransferRequestResponderFlow(private val otherSideSession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        //Transaction verification and signing.
        val stx = subFlow(SignTxFlow(otherSideSession))
        return waitForLedgerCommit(stx.id)
    }
}
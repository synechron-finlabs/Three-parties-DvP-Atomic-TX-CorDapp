package com.synechron.cordapp.clearinghouse.flows

import co.paralleluniverse.fibers.Suspendable
import com.synechron.cordapp.common.flows.IdentitySyncFlow
import com.synechron.cordapp.common.flows.SignTxFlow
import com.synechron.cordapp.flows.AbstractConfirmAssetTransferRequestFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.transactions.SignedTransaction

@InitiatedBy(AbstractConfirmAssetTransferRequestFlow::class)
class ConfirmAssetTransferRequestResponderFlow(private val otherSideSession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        //Identity sync flow.
        subFlow(IdentitySyncFlow.Receive(otherSideSession))
        //Transaction verification and signing.
        val stx = subFlow(SignTxFlow(otherSideSession))
        return waitForLedgerCommit(stx.id)
    }
}

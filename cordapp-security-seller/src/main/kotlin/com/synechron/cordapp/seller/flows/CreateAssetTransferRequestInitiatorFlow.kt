package com.synechron.cordapp.seller.flows

import co.paralleluniverse.fibers.Suspendable
import com.synechron.cordapp.common.exception.InvalidPartyException
import com.synechron.cordapp.contract.AssetTransferContract
import com.synechron.cordapp.contract.AssetTransferContract.Companion.ASSET_TRANSFER_CONTRACT_ID
import com.synechron.cordapp.flows.AbstractCreateAssetTransferRequestFlow
import com.synechron.cordapp.state.AssetTransfer
import com.synechron.cordapp.state.RequestStatus.PENDING_CONFIRMATION
import com.synechron.cordapp.utils.getAssetByCusip
import net.corda.confidential.SwapIdentitiesFlow
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
 * Owner of security (i.e. seller) creates [AssetTransfer] request state in-order to start deal with buyer.
 */
@StartableByRPC
class CreateAssetTransferRequestInitiatorFlow(val cusip: String,
                                              val securityBuyer: Party) : AbstractCreateAssetTransferRequestFlow<SignedTransaction>() {

    companion object {
        object INITIALISING : ProgressTracker.Step("Performing initial steps.")
        object BUILDING : ProgressTracker.Step("Building and verifying transaction.")
        object SIGNING : Step("Signing transaction.")
        object COLLECTING : Step("Collecting counterparty signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING : Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(INITIALISING, BUILDING, SIGNING, COLLECTING, FINALISING)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        if (ourIdentity.name == securityBuyer.name) throw InvalidPartyException("Flow initiating party should not equals to Lender of Cash party.")
        // Step 1. Initialisation.
        progressTracker.currentStep = INITIALISING
        val txKeys = subFlow(SwapIdentitiesFlow(securityBuyer))
        check(txKeys.size == 2) { "Something went wrong when generating confidential identities." }

        val anonymousMe = txKeys[ourIdentity] ?: throw FlowException("Couldn't create our anonymous identity.")
        val anonymousCashLender = txKeys[securityBuyer]
                ?: throw FlowException("Couldn't create lender's (securityBuyer) anonymous identity.")

        val asset = serviceHub.getAssetByCusip(cusip).state.data
        val assetTransfer = AssetTransfer(asset, anonymousMe, anonymousCashLender, null, PENDING_CONFIRMATION)
        val ourSigningKey = assetTransfer.securitySeller.owningKey

        // Step 2. Building.
        progressTracker.currentStep = BUILDING
        val txb = TransactionBuilder(serviceHub.firstNotary())
                .addOutputState(assetTransfer, ASSET_TRANSFER_CONTRACT_ID)
                .addCommand(AssetTransferContract.Commands.CreateRequest(), assetTransfer.participants.map { it.owningKey })
                .setTimeWindow(serviceHub.clock.instant(), 30.seconds)

        // Step 3. Sign the transaction.
        progressTracker.currentStep = SIGNING
        val ptx = serviceHub.signInitialTransaction(txb, ourSigningKey)

        // Step 4. Get the counter-party signature.
        progressTracker.currentStep = COLLECTING
        val lenderOfCashFlowSession = initiateFlow(securityBuyer)
        val stx = subFlow(CollectSignaturesFlow(
                ptx,
                setOf(lenderOfCashFlowSession),
                listOf(ourSigningKey),
                COLLECTING.childProgressTracker())
        )

        // Step 5. Finalise the transaction.
        progressTracker.currentStep = FINALISING
        val ftx = subFlow(FinalityFlow(stx, FINALISING.childProgressTracker()))
        return ftx
    }
}
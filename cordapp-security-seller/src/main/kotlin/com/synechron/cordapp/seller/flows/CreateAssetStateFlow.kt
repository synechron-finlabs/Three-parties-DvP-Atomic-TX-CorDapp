package com.synechron.cordapp.seller.flows

import co.paralleluniverse.fibers.Suspendable
import com.synechron.cordapp.contract.AssetContract
import com.synechron.cordapp.contract.AssetContract.Companion.ASSET_CONTRACT_ID
import com.synechron.cordapp.flows.FlowLogicCommonMethods
import com.synechron.cordapp.state.Asset
import net.corda.core.contracts.Amount
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.core.utilities.seconds
import java.util.*

/**
 * Create the [Asset] state on ledger. This state acting as security/bond on ledger which going to be sold for cash.
 */
object CreateAssetStateFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val cusip: String,
                    val assetName: String,
                    val purchaseCost: Amount<Currency>) : FlowLogic<SignedTransaction>(), FlowLogicCommonMethods {

        companion object {
            object INITIALISING : ProgressTracker.Step("Performing initial steps.")
            object BUILDING : ProgressTracker.Step("Building and verifying transaction.")
            object SIGNING : Step("Signing transaction.")

            object FINALISING : Step("Finalising transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(INITIALISING, BUILDING, SIGNING, FINALISING)
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            // Step 1. Initialisation.
            progressTracker.currentStep = INITIALISING
            val asset = Asset(cusip, assetName, purchaseCost, ourIdentity)

            // Step 2. Building.
            progressTracker.currentStep = BUILDING
            val txb = TransactionBuilder(serviceHub.firstNotary())
                    .addOutputState(asset, ASSET_CONTRACT_ID)
                    .addCommand(AssetContract.Commands.Create(), ourIdentity.owningKey)
                    .setTimeWindow(serviceHub.clock.instant(), 30.seconds)

            // Step 3. Sign the transaction.
            progressTracker.currentStep = SIGNING
            val stx = serviceHub.signInitialTransaction(txb)

            // Step 4. Finalise the transaction.
            progressTracker.currentStep = FINALISING
            val ftx = subFlow(FinalityFlow(stx, FINALISING.childProgressTracker()))
            return ftx
        }
    }
}

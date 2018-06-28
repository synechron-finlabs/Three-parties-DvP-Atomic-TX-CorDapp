package com.synechron.cordapp.contract

import com.synechron.cordapp.state.Asset
import com.synechron.cordapp.state.AssetTransfer
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.utils.sumCash
import java.security.PublicKey

class AssetContract : Contract {
    companion object {
        @JvmStatic
        val ASSET_CONTRACT_ID = AssetContract::class.java.name!!
    }

    interface Commands : CommandData {
        class Create : TypeOnlyCommandData(), Commands
        class Transfer : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val setOfSigners = command.signers.toSet()
        when (command.value) {
            is Commands.Create -> verifyCreate(tx, setOfSigners)
            is Commands.Transfer -> verifyTransfer(tx, setOfSigners)
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "No inputs must be consumed." using (tx.inputStates.isEmpty())
        "Only one out state should be created." using (tx.outputStates.size == 1)
        val output = tx.outputsOfType<Asset>().single()
        "Must have a positive amount." using (output.purchaseCost > output.purchaseCost.copy(quantity = 0))
        "Owner only may sign the Asset issue transaction." using (output.owner.owningKey in signers)
    }

    private fun verifyTransfer(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        val inputAssets = tx.inputsOfType<Asset>()
        val inputAssetTransfers = tx.inputsOfType<AssetTransfer>()
        "There must be one input obligation." using (inputAssets.size == 1)
        // Check there are output cash states.
        // We don't care about cash inputs, the Cash contract handles those.
        val cash = tx.outputsOfType<Cash.State>()
        "There must be output cash." using (cash.isNotEmpty())

        // Check that the cash is being assigned to us.
        val inputAsset = inputAssets.single()
        val inputAssetTransfer = inputAssetTransfers.single()
        val acceptableCash = cash.filter { it.owner in listOf(inputAsset.owner, inputAssetTransfer.securitySeller) }
        "There must be output cash paid to the recipient." using (acceptableCash.isNotEmpty())

        // Sum the cash being sent to us (we don't care about the issuer).
        val sumAcceptableCash = acceptableCash.sumCash().withoutIssuer()
        "The amount settled must be equal to the asset's purchase cost amount." using (inputAsset.purchaseCost == sumAcceptableCash)

        val outputs = tx.outputsOfType<Asset>()
        // If the obligation has been partially settled then it should still exist.
        "There must be one output Asset." using (outputs.size == 1)

        // Check only the paid property changes.
        val output = outputs.single()
        "Must not not change Asset data except owner field value." using (inputAsset == output.copy(owner = inputAsset.owner))
        "Owner only may sign the Asset issue transaction." using (output.owner.owningKey in signers)
    }
}
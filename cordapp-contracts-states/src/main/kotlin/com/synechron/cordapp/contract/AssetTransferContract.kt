package com.synechron.cordapp.contract

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class AssetTransferContract : Contract {
    companion object {
        @JvmStatic
        val ASSET_TRANSFER_CONTRACT_ID = AssetTransferContract::class.java.name!!
    }

    interface Commands : CommandData {
        class CreateRequest : TypeOnlyCommandData(), Commands
        class ConfirmRequest : TypeOnlyCommandData(), Commands
        class SettleRequest : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val setOfSigners = command.signers.toSet()
        when (command.value) {
            is Commands.CreateRequest -> verifyCreateRequest(tx, setOfSigners)
            is Commands.ConfirmRequest -> verifyConfirmRequest(tx, setOfSigners)
            is Commands.SettleRequest -> verifySettleRequest(tx, setOfSigners)
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    private fun verifyCreateRequest(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "No inputs must be consumed." using (tx.inputStates.isEmpty())
        "Only one out state should be created." using (tx.outputStates.size == 1)
        //TODO Add more rules to verify create asset transfer request.
    }

    private fun verifyConfirmRequest(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //TODO Add rules to confirm asset transfer request.
    }

    private fun verifySettleRequest(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //TODO Add rules to settle asset transfer request.
    }
}

package com.synechron.cordapp.state

import com.synechron.cordapp.contract.AssetContract
import com.synechron.cordapp.schema.AssetSchemaV1
import net.corda.core.contracts.Amount
import net.corda.core.contracts.CommandAndState
import net.corda.core.contracts.OwnableState
import net.corda.core.crypto.NullKeys
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.util.*

/**
 * This states plays role of digital asset (i.e. bond, securities, stock, etc.) on ledger.
 */
//TODO Think of using [FungibleAsset] interface to implement [Asset] state.
data class Asset(val cusip: String,
                 val assetName: String,
                 val purchaseCost: Amount<Currency>,
                 override val owner: AbstractParty
) : OwnableState, QueryableState {
    override val participants: List<AbstractParty> = listOf(owner)

    fun withoutOwner() = copy(owner = NullKeys.NULL_PARTY)

    override fun withNewOwner(newOwner: AbstractParty): CommandAndState {
        return CommandAndState(AssetContract.Commands.Transfer(), this.copy(owner = newOwner))
    }

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is AssetSchemaV1 -> AssetSchemaV1.PersistentAsset(
                    cusip = this.cusip,
                    assetName = this.assetName,
                    purchaseCost = this.purchaseCost.toString(),
                    owner = this.owner,
                    participants = this.participants.toMutableSet()
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = setOf(AssetSchemaV1)
}
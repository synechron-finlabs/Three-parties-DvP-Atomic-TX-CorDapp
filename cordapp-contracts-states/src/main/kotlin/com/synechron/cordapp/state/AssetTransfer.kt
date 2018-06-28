package com.synechron.cordapp.state

import com.synechron.cordapp.schema.AssetTransferSchemaV1
import com.fasterxml.jackson.annotation.JsonValue
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable

/**
 * This state acting as deal data before the actual [Asset] being transfer to target buyer party on settlement.
 */
data class AssetTransfer(val asset: Asset,
                         val securitySeller: AbstractParty,
                         val securityBuyer: AbstractParty,
                         val clearingHouse: AbstractParty?,
                         val status: RequestStatus,
                         override val participants: List<AbstractParty> = listOf(securityBuyer, securitySeller),
                         override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState, QueryableState {
    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is AssetTransferSchemaV1 -> AssetTransferSchemaV1.PersistentAssetTransfer(
                    cusip = this.asset.cusip,
                    securitySeller = this.securitySeller,
                    securityBuyer = this.securityBuyer,
                    clearingHouse = this.clearingHouse,
                    status = this.status.value,
                    participants = this.participants.toMutableSet(),
                    linearId = this.linearId.toString()
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = setOf(AssetTransferSchemaV1)
}

@CordaSerializable
enum class RequestStatus(@JsonValue val value: String) {
    PENDING_CONFIRMATION("Pending Confirmation"), //Initial status
    PENDING("Pending"), // updated by buyer
    TRANSFERRED("Transferred"), // on valid asset data clearing house update this status
    REJECTED("Rejected"), // on invalid asset data clearing house reject transaction with this status.
    FAILED("Failed") // on fail of settlement e.g. with insufficient cash from Buyer party.
}
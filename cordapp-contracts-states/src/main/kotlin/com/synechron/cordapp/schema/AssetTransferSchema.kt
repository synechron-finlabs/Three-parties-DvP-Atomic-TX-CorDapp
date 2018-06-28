package com.synechron.cordapp.schema

import net.corda.core.crypto.NullKeys
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.*

/**
 * The family of schemas for [AssetTransferSchema].
 */
object AssetTransferSchema

/**
 * First version of an [AssetTransferSchema] schema.
 */
object AssetTransferSchemaV1 : MappedSchema(schemaFamily = AssetSchema.javaClass,
        version = 1, mappedTypes = listOf(PersistentAssetTransfer::class.java)) {
    @Entity
    @Table(name = "asset_transfer", indexes = arrayOf(Index(name = "idx_asset_transfer_linearId", columnList = "linear_id"),
            Index(name = "idx_asset_transfer_cusip", columnList = "cusip")))
    class PersistentAssetTransfer(
            @Column(name = "cusip")
            val cusip: String,

            @Column(name = "lender_of_security")
            val securitySeller: AbstractParty,

            @Column(name = "lender_of_cash")
            val securityBuyer: AbstractParty,

            @Column(name = "clearing_house")
            val clearingHouse: AbstractParty?,

            @Column(name = "status")
            val status: String,

            @ElementCollection
            @Column(name = "participants")
            @CollectionTable(name = "asset_transfer_participants", joinColumns = arrayOf(
                    JoinColumn(name = "output_index", referencedColumnName = "output_index"),
                    JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id")))
            var participants: MutableSet<AbstractParty>? = null,

            @Column(name = "linear_id")
            val linearId: String
    ) : PersistentState() {
        constructor() : this("default-constructor-required-for-hibernate", NullKeys.NULL_PARTY, NullKeys.NULL_PARTY,
                NullKeys.NULL_PARTY, "", mutableSetOf(), "")
    }
}


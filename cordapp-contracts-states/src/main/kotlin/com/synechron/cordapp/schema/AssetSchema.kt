package com.synechron.cordapp.schema

import net.corda.core.crypto.NullKeys
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.*

/**
 * The family of schemas for [AssetSchema].
 */
object AssetSchema

/**
 * First version of an [AssetSchema] schema.
 */
object AssetSchemaV1 : MappedSchema(schemaFamily = AssetSchema.javaClass,
        version = 1, mappedTypes = listOf(PersistentAsset::class.java)) {
    @Entity
    @Table(name = "asset", indexes = arrayOf(Index(name = "idx_asset_owner", columnList = "owner"),
            Index(name = "idx_asset_cusip", columnList = "cusip")))
    class PersistentAsset(
            @Column(name = "cusip")
            val cusip: String,

            @Column(name = "asset_name")
            val assetName: String,

            @Column(name = "purchase_cost")
            val purchaseCost: String,

            @Column(name = "owner")
            val owner: AbstractParty,

            @ElementCollection
            @Column(name = "participants")
            @CollectionTable(name = "asset_participants", joinColumns = arrayOf(
                    JoinColumn(name = "output_index", referencedColumnName = "output_index"),
                    JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id")))
            var participants: MutableSet<AbstractParty>? = null
    ) : PersistentState() {
        constructor() : this("default-constructor-required-for-hibernate", "", "", NullKeys.NULL_PARTY, mutableSetOf())
    }
}


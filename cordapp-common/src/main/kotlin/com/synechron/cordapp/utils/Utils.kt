package com.synechron.cordapp.utils

import com.synechron.cordapp.schema.AssetSchemaV1
import com.synechron.cordapp.state.Asset
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowException
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.QueryCriteria

fun ServiceHub.getAssetByCusip(cusip: String): StateAndRef<Asset> {
    val cusipExpr = AssetSchemaV1.PersistentAsset::cusip.equal(cusip)
    val cusipCriteria = QueryCriteria.VaultCustomQueryCriteria(cusipExpr)

    return this.vaultService.queryBy<Asset>(cusipCriteria).states.singleOrNull()
            ?: throw FlowException("Asset with id $cusip not found.")
}
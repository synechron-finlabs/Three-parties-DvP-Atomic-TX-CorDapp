package com.synechron.cordapp.flows

import com.synechron.cordapp.obligation.exception.NotaryNotFoundException
import com.synechron.cordapp.obligation.exception.StateNotFoundOnVaultException
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria

/***
 * Interface offers default functions implementation for get first notary from NetworkMap,
 * find State on vault by linearId, and resolve anonymous or abstract party to well known identity.
 */
interface FlowLogicCommonMethods {
    fun ServiceHub.firstNotary(): Party {
        return this.networkMapCache.notaryIdentities.firstOrNull()
                ?: throw NotaryNotFoundException("No available notary.")
    }

    fun <T : ContractState> ServiceHub.loadState(linearId: UniqueIdentifier, clazz: Class<T>): StateAndRef<T> {
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(null,
                listOf(linearId), Vault.StateStatus.UNCONSUMED, null)
        return this.vaultService.queryBy(clazz, queryCriteria).states.singleOrNull()
                ?: throw StateNotFoundOnVaultException("State with id $linearId not found.")
    }

    fun ServiceHub.resolveIdentity(abstractParty: AbstractParty): Party {
        return this.identityService.requireWellKnownPartyFromAnonymous(abstractParty)
    }
}
package com.synechron.cordapp.contract

import net.corda.core.contracts.ContractState
import java.security.PublicKey

fun keysFromParticipants(state: ContractState): Set<PublicKey> {
    return state.participants.map {
        it.owningKey
    }.toSet()
}
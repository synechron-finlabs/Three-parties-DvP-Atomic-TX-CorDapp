package com.synechron.cordapp.plugins

import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.transactions.TransactionBuilder

class SerializationWhitelistImpl : SerializationWhitelist {
    override val whitelist: List<Class<*>>
        get() = listOf(TransactionBuilder::class.java)
}
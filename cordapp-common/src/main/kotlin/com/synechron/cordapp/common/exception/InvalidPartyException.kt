package com.synechron.cordapp.common.exception

import net.corda.core.CordaRuntimeException

class InvalidPartyException(override val message: String) : CordaRuntimeException(message)
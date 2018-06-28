package com.synechron.cordapp.obligation.exception

import net.corda.core.CordaRuntimeException

class StateNotFoundOnVaultException(override val message: String) : CordaRuntimeException(message)
package com.synechron.cordapp.common.exception

import net.corda.core.CordaRuntimeException

class StateNotFoundException(override val message: String) : CordaRuntimeException(message)
package com.synechron.cordapp.obligation.exception

import net.corda.core.CordaRuntimeException

class NotaryNotFoundException(override val message: String) : CordaRuntimeException(message)
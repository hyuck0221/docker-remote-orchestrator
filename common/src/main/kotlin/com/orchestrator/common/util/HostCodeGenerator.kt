package com.orchestrator.common.util

import java.util.UUID

object HostCodeGenerator {

    fun generate(): String {
        val uuid = UUID.randomUUID().toString().replace("-", "")
        return uuid.take(8).uppercase()
    }

    fun isValid(code: String): Boolean {
        return code.length == 8 && code.all { it.isLetterOrDigit() }
    }
}

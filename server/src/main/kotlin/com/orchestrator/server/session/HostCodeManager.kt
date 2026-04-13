package com.orchestrator.server.session

import com.orchestrator.common.util.HostCodeGenerator
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class HostCodeManager {

    private val logger = LoggerFactory.getLogger(HostCodeManager::class.java)
    private val activeCodes = ConcurrentHashMap<String, Long>()

    fun generateCode(): String {
        val code = HostCodeGenerator.generate()
        activeCodes[code] = System.currentTimeMillis()
        logger.info("Generated host code: $code")
        return code
    }

    fun restoreCode(code: String): String {
        activeCodes[code] = System.currentTimeMillis()
        logger.info("Restored host code: $code")
        return code
    }

    fun validateCode(code: String): Boolean {
        val normalized = code.trim()
        if (activeCodes.containsKey(normalized)) return true
        // Case-insensitive fallback — host codes are always stored uppercase but
        // be lenient with user input/copy artifacts.
        val upper = normalized.uppercase()
        if (activeCodes.containsKey(upper)) return true
        logger.warn("validateCode failed: received='$code' active=${activeCodes.keys}")
        return false
    }

    fun revokeCode(code: String) {
        activeCodes.remove(code)
        logger.info("Revoked host code: $code")
    }

    fun getActiveCode(): String? {
        return activeCodes.keys.firstOrNull()
    }
}

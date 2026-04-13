package com.orchestrator.common.util

import java.util.Base64

data class ConnectionInfo(
    val host: String,
    val port: Int,
    val code: String
)

object ConnectionToken {

    private const val PREFIX = "dro1_"

    fun encode(host: String, port: Int, code: String): String {
        val payload = "${host.trim()}|$port|${code.trim().uppercase()}"
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
        return PREFIX + b64
    }

    fun decode(token: String): ConnectionInfo? {
        val raw = token.trim()
        if (!raw.startsWith(PREFIX)) return null
        val body = raw.removePrefix(PREFIX)
        val decoded = try {
            String(Base64.getUrlDecoder().decode(body), Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
        val parts = decoded.split("|")
        if (parts.size != 3) return null
        val host = parts[0].ifBlank { return null }
        val port = parts[1].toIntOrNull() ?: return null
        val code = parts[2].ifBlank { return null }
        return ConnectionInfo(host = host, port = port, code = code)
    }

    fun looksLikeToken(input: String): Boolean = input.trim().startsWith(PREFIX)
}

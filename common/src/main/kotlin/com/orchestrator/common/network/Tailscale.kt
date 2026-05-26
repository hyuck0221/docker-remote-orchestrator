package com.orchestrator.common.network

import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

data class TailscaleStatus(
    val installed: Boolean,
    val running: Boolean = false,
    val dnsName: String? = null,
    val machineName: String? = null,
    val ipv4: String? = null
) {
    val reachableName: String?
        get() = dnsName?.trimEnd('.') ?: machineName ?: ipv4
}

object Tailscale {
    private val logger = LoggerFactory.getLogger(Tailscale::class.java)

    fun status(): TailscaleStatus {
        val binary = findBinary() ?: return TailscaleStatus(installed = false)
        val output = runCommand(listOf(binary, "status", "--json"), timeoutSeconds = 5) ?: return TailscaleStatus(installed = true)
        val backendState = jsonString(output, "BackendState")
        val selfBlock = jsonObject(output, "Self")
        val running = backendState.equals("Running", ignoreCase = true) || selfBlock != null
        val dnsName = selfBlock?.let { jsonString(it, "DNSName") }?.trimEnd('.')
        val hostName = selfBlock?.let { jsonString(it, "HostName") }
        val ips = selfBlock?.let { jsonStringArray(it, "TailscaleIPs") }.orEmpty()
        val ipv4 = ips.firstOrNull { it.count { c -> c == '.' } == 3 }
        return TailscaleStatus(
            installed = true,
            running = running,
            dnsName = dnsName,
            machineName = hostName,
            ipv4 = ipv4
        )
    }

    fun copyFileToTaildrop(file: File, target: String): Boolean {
        val binary = findBinary() ?: return false
        if (!file.exists()) return false
        val cleanTarget = target.trim().trimEnd('.').trimEnd(':')
        if (cleanTarget.isBlank()) return false
        val output = runCommand(listOf(binary, "file", "cp", file.absolutePath, "$cleanTarget:"), timeoutSeconds = 120)
        val ok = output != null
        if (!ok) logger.info("Taildrop copy failed for ${file.name} to $cleanTarget")
        return ok
    }

    fun receiveTaildropFiles(destinationDir: File, timeoutSeconds: Long = 60): Boolean {
        val binary = findBinary() ?: return false
        destinationDir.mkdirs()
        val waitArg = if (timeoutSeconds > 0) "--wait=true" else "--wait=false"
        return runCommand(listOf(binary, "file", "get", waitArg, destinationDir.absolutePath), timeoutSeconds = timeoutSeconds.coerceAtLeast(5)) != null
    }

    private fun findBinary(): String? {
        runCatching {
            val p = ProcessBuilder("tailscale", "version").redirectErrorStream(true).start()
            if (p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0) return "tailscale"
        }
        val os = System.getProperty("os.name", "").lowercase()
        val home = System.getProperty("user.home")
        val candidates = when {
            os.contains("mac") || os.contains("darwin") -> listOf(
                "/Applications/Tailscale.app/Contents/MacOS/Tailscale",
                "/opt/homebrew/bin/tailscale",
                "/usr/local/bin/tailscale",
                "$home/bin/tailscale"
            )
            os.contains("win") -> listOf(
                "${System.getenv("ProgramFiles")}\\Tailscale\\tailscale.exe",
                "${System.getenv("LOCALAPPDATA")}\\Tailscale\\tailscale.exe"
            )
            else -> listOf("/usr/bin/tailscale", "/usr/local/bin/tailscale", "/snap/bin/tailscale", "$home/bin/tailscale")
        }
        return candidates.firstOrNull { File(it).exists() }
    }

    private fun runCommand(command: List<String>, timeoutSeconds: Long): String? {
        return try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            val output = process.inputStream.bufferedReader().readText()
            if (!finished) {
                process.destroyForcibly()
                null
            } else if (process.exitValue() == 0) {
                output
            } else {
                logger.debug("${command.joinToString(" ")} failed: $output")
                null
            }
        } catch (e: Exception) {
            logger.debug("${command.joinToString(" ")} failed: ${e.message}")
            null
        }
    }

    private fun jsonString(json: String, key: String): String? {
        val regex = Regex(""""${Regex.escape(key)}"\s*:\s*"([^"]*)"""")
        return regex.find(json)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }

    private fun jsonStringArray(json: String, key: String): List<String> {
        val body = Regex(""""${Regex.escape(key)}"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
            .find(json)
            ?.groupValues
            ?.get(1)
            ?: return emptyList()
        return Regex(""""([^"]+)"""").findAll(body).map { it.groupValues[1] }.toList()
    }

    private fun jsonObject(json: String, key: String): String? {
        val start = Regex(""""${Regex.escape(key)}"\s*:\s*\{""").find(json)?.range?.last ?: return null
        var depth = 1
        var index = start + 1
        while (index < json.length && depth > 0) {
            when (json[index]) {
                '{' -> depth++
                '}' -> depth--
            }
            index++
        }
        return if (depth == 0) json.substring(start, index) else null
    }
}

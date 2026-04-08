package com.orchestrator.common.network

import org.slf4j.LoggerFactory
import java.net.*

object NetworkDiagnostics {

    private val logger = LoggerFactory.getLogger(NetworkDiagnostics::class.java)

    data class NetworkInfo(
        val localIp: String,
        val hostname: String,
        val allAddresses: List<String>,
        val publicIp: String?
    )

    data class ConnectivityResult(
        val reachable: Boolean,
        val latencyMs: Long,
        val error: String? = null
    )

    fun getNetworkInfo(): NetworkInfo {
        val hostname = try {
            InetAddress.getLocalHost().hostName
        } catch (_: Exception) { "unknown" }

        val localIp = try {
            InetAddress.getLocalHost().hostAddress
        } catch (_: Exception) { "unknown" }

        val allAddresses = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { iface ->
                    iface.inetAddresses.toList()
                        .filter { !it.isLoopbackAddress && it is Inet4Address }
                        .map { "${iface.displayName}: ${it.hostAddress}" }
                }
        } catch (_: Exception) { emptyList() }

        val publicIp = try {
            URL("https://api.ipify.org").readText().trim()
        } catch (_: Exception) { null }

        return NetworkInfo(
            localIp = localIp,
            hostname = hostname,
            allAddresses = allAddresses,
            publicIp = publicIp
        )
    }

    fun checkConnectivity(host: String, port: Int, timeoutMs: Int = 5000): ConnectivityResult {
        val start = System.currentTimeMillis()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val latency = System.currentTimeMillis() - start
                logger.info("Connection to $host:$port successful (${latency}ms)")
                ConnectivityResult(reachable = true, latencyMs = latency)
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            logger.warn("Connection to $host:$port failed: ${e.message}")
            ConnectivityResult(reachable = false, latencyMs = latency, error = e.message)
        }
    }

    fun checkPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use { true }
        } catch (_: Exception) {
            false
        }
    }

    fun printDiagnostics() {
        val info = getNetworkInfo()
        logger.info("=== Network Diagnostics ===")
        logger.info("Hostname: ${info.hostname}")
        logger.info("Local IP: ${info.localIp}")
        logger.info("Public IP: ${info.publicIp ?: "unavailable"}")
        info.allAddresses.forEach { logger.info("  Interface: $it") }
    }
}

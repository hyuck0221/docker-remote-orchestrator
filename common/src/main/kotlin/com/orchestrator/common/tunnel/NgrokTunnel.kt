package com.orchestrator.common.tunnel

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI

enum class TunnelState {
    STOPPED, STARTING, RUNNING, ERROR
}

enum class NgrokStatus {
    READY, NOT_INSTALLED, NOT_CONFIGURED
}

class NgrokTunnel {

    private val logger = LoggerFactory.getLogger(NgrokTunnel::class.java)

    private val _state = MutableStateFlow(TunnelState.STOPPED)
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    private val _publicUrl = MutableStateFlow<String?>(null)
    val publicUrl: StateFlow<String?> = _publicUrl.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var process: Process? = null
    private var pollJob: Job? = null

    fun start(port: Int, scope: CoroutineScope) {
        if (_state.value == TunnelState.RUNNING || _state.value == TunnelState.STARTING) return

        _state.value = TunnelState.STARTING
        _errorMessage.value = null
        _publicUrl.value = null

        scope.launch(Dispatchers.IO) {
            try {
                val binary = getNgrokBinary()
                if (binary == null) {
                    _state.value = TunnelState.ERROR
                    _errorMessage.value = "ngrok not found. Install from https://ngrok.com"
                    return@launch
                }

                // Kill any existing ngrok process on the API port
                try {
                    val apiUrl = URI("http://localhost:4040/api/tunnels").toURL()
                    val conn = apiUrl.openConnection() as HttpURLConnection
                    conn.connectTimeout = 1000
                    conn.readTimeout = 1000
                    conn.responseCode
                    conn.disconnect()
                    ProcessBuilder(binary, "http", "--log=stdout", "0")
                        .start().destroyForcibly()
                    delay(500)
                } catch (_: Exception) {}

                // Start ngrok
                val pb = ProcessBuilder(binary, "http", port.toString())
                    .redirectErrorStream(true)
                pb.environment()["NGROK_LOG"] = "stdout"
                process = pb.start()

                // Consume output in background to prevent buffer blocking
                launch(Dispatchers.IO) {
                    try {
                        val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                        while (isActive) {
                            reader.readLine() ?: break
                        }
                    } catch (_: Exception) {}
                }

                // Poll the ngrok API for the public URL
                pollJob = launch {
                    var attempts = 0
                    val maxAttempts = 30 // 15 seconds max

                    while (isActive && attempts < maxAttempts) {
                        delay(500)
                        attempts++
                        val url = fetchPublicUrl()
                        if (url != null) {
                            _publicUrl.value = url
                            _state.value = TunnelState.RUNNING
                            logger.info("ngrok tunnel active: $url")
                            return@launch
                        }
                    }

                    if (_publicUrl.value == null) {
                        _state.value = TunnelState.ERROR
                        _errorMessage.value = "Failed to get tunnel URL. Check ngrok auth: ngrok config add-authtoken <token>"
                        stop()
                    }
                }
            } catch (e: Exception) {
                _state.value = TunnelState.ERROR
                _errorMessage.value = when {
                    e.message?.contains("No such file") == true ||
                    e.message?.contains("cannot find") == true ->
                        "ngrok not found. Install from https://ngrok.com"
                    else -> "Failed to start ngrok: ${e.message}"
                }
                logger.error("ngrok start failed", e)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        process?.destroyForcibly()
        process = null
        _publicUrl.value = null
        _state.value = TunnelState.STOPPED
        _errorMessage.value = null
        logger.info("ngrok tunnel stopped")
    }

    private fun fetchPublicUrl(): String? {
        return try {
            val apiUrl = URI("http://localhost:4040/api/tunnels").toURL()
            val conn = apiUrl.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.requestMethod = "GET"

            if (conn.responseCode != 200) {
                conn.disconnect()
                return null
            }

            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            // Parse the public_url from JSON response
            // Response format: {"tunnels":[{"public_url":"https://xxxx.ngrok-free.app",...}]}
            val regex = """"public_url"\s*:\s*"(https://[^"]+)"""".toRegex()
            regex.find(response)?.groupValues?.get(1)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NgrokTunnel::class.java)

        private fun findNgrokPath(): String? {
            // Try direct command first
            try {
                val p = ProcessBuilder("ngrok", "version").redirectErrorStream(true).start()
                if (p.waitFor() == 0) return "ngrok"
            } catch (_: Exception) {}

            // Search common installation paths
            val os = System.getProperty("os.name", "").lowercase()
            val candidates = when {
                os.contains("mac") || os.contains("darwin") -> listOf(
                    "/opt/homebrew/bin/ngrok",
                    "/usr/local/bin/ngrok",
                    "${System.getProperty("user.home")}/bin/ngrok",
                    "${System.getProperty("user.home")}/.ngrok/ngrok"
                )
                os.contains("win") -> listOf(
                    "${System.getenv("LOCALAPPDATA")}\\ngrok\\ngrok.exe",
                    "${System.getenv("ProgramFiles")}\\ngrok\\ngrok.exe",
                    "C:\\ngrok\\ngrok.exe"
                )
                else -> listOf(
                    "/usr/local/bin/ngrok",
                    "/snap/bin/ngrok",
                    "${System.getProperty("user.home")}/bin/ngrok"
                )
            }
            for (path in candidates) {
                if (java.io.File(path).exists()) return path
            }
            return null
        }

        internal var ngrokBinary: String? = null

        private fun getNgrokBinary(): String? {
            if (ngrokBinary == null) ngrokBinary = findNgrokPath()
            return ngrokBinary
        }

        fun checkStatus(): NgrokStatus {
            val binary = getNgrokBinary() ?: return NgrokStatus.NOT_INSTALLED

            // Check if authtoken is configured
            try {
                val os = System.getProperty("os.name", "").lowercase()
                val home = System.getProperty("user.home")
                val configPaths = when {
                    os.contains("mac") || os.contains("darwin") ->
                        listOf("$home/Library/Application Support/ngrok/ngrok.yml", "$home/.ngrok2/ngrok.yml")
                    os.contains("win") ->
                        listOf("${System.getenv("APPDATA")}\\ngrok\\ngrok.yml", "$home\\.ngrok2\\ngrok.yml")
                    else ->
                        listOf("$home/.config/ngrok/ngrok.yml", "$home/.ngrok2/ngrok.yml")
                }
                val hasConfig = configPaths.any { path ->
                    val file = java.io.File(path)
                    file.exists() && file.readText().contains("authtoken")
                }
                return if (hasConfig) NgrokStatus.READY else NgrokStatus.NOT_CONFIGURED
            } catch (_: Exception) {
                return NgrokStatus.NOT_CONFIGURED
            }
        }

        fun setAuthToken(token: String): Boolean {
            val binary = getNgrokBinary() ?: return false
            return try {
                val process = ProcessBuilder(binary, "config", "add-authtoken", token)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                logger.info("ngrok authtoken set: exitCode=$exitCode output=$output")
                exitCode == 0
            } catch (e: Exception) {
                logger.error("Failed to set ngrok authtoken", e)
                false
            }
        }

        fun isNgrokUrl(address: String): Boolean {
            return address.contains("ngrok") ||
                   address.contains(".app") ||
                   address.startsWith("https://") ||
                   address.startsWith("wss://")
        }

        fun parseUrl(address: String): Triple<String, Int, Boolean> {
            val cleaned = address
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("wss://")
                .removePrefix("ws://")
                .trimEnd('/')

            return Triple(cleaned, 443, true)
        }
    }
}

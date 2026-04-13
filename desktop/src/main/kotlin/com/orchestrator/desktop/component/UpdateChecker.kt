package com.orchestrator.desktop.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orchestrator.desktop.i18n.LocalStrings
import com.orchestrator.desktop.theme.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.io.readByteArray
import kotlinx.serialization.json.*
import java.awt.Desktop
import java.io.File
import java.net.URI
import kotlin.system.exitProcess

sealed class UpdateInstallState {
    object Idle : UpdateInstallState()
    data class Downloading(val bytesRead: Long, val total: Long) : UpdateInstallState() {
        val percent: Int get() = if (total > 0) ((bytesRead * 100) / total).toInt().coerceIn(0, 100) else -1
    }
    data class Failed(val message: String) : UpdateInstallState()
}

@Composable
fun UpdateBanner(
    currentVersion: String,
    githubRepo: String,
    scope: CoroutineScope
) {
    val s = LocalStrings.current
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var downloadUrl by remember { mutableStateOf<String?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    var installState by remember { mutableStateOf<UpdateInstallState>(UpdateInstallState.Idle) }

    LaunchedEffect(Unit) {
        try {
            val (version, url) = checkForUpdate(githubRepo, currentVersion)
            latestVersion = version
            downloadUrl = url
        } catch (_: Exception) {}
    }

    if (!dismissed && latestVersion != null && latestVersion != currentVersion) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AccentBlue.copy(alpha = 0.08f)
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.updateAvailable(latestVersion!!),
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentBlue,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )

                    UpdateActionButtons(
                        state = installState,
                        url = downloadUrl,
                        onStart = { url ->
                            scope.launch(Dispatchers.IO) {
                                runDownloadAndInstall(url) { installState = it }
                            }
                        },
                        onBrowser = { url ->
                            try { Desktop.getDesktop().browse(URI(url)) } catch (_: Exception) {}
                        }
                    )

                    if (installState !is UpdateInstallState.Downloading) {
                        TextButton(
                            onClick = { dismissed = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(s.dismiss, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                }

                val st = installState
                if (st is UpdateInstallState.Downloading && st.total > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (st.bytesRead.toFloat() / st.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = AccentBlue
                    )
                }
            }
        }
    }
}

@Composable
fun UpdateActionButtons(
    state: UpdateInstallState,
    url: String?,
    onStart: (String) -> Unit,
    onBrowser: (String) -> Unit
) {
    val s = LocalStrings.current
    when (state) {
        UpdateInstallState.Idle -> {
            TextButton(
                onClick = { url?.let(onStart) },
                enabled = url != null,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(s.download, style = MaterialTheme.typography.labelMedium, color = AccentBlue, fontWeight = FontWeight.SemiBold)
            }
        }
        is UpdateInstallState.Downloading -> {
            val label = if (state.percent >= 0) s.downloadingPercent(state.percent) else s.downloading
            TextButton(
                onClick = {},
                enabled = false,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = AccentBlue)
            }
        }
        is UpdateInstallState.Failed -> {
            TextButton(
                onClick = { url?.let(onStart) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(s.downloadFailedRetry, style = MaterialTheme.typography.labelMedium, color = AccentBlue, fontWeight = FontWeight.SemiBold)
            }
            TextButton(
                onClick = { url?.let(onBrowser) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(s.openInBrowser, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
}

private suspend fun runDownloadAndInstall(
    url: String,
    onStateChange: (UpdateInstallState) -> Unit
) {
    try {
        onStateChange(UpdateInstallState.Downloading(0, -1))
        val file = downloadUpdateFile(url) { read, total ->
            onStateChange(UpdateInstallState.Downloading(read, total))
        }
        launchInstallerAndExit(file)
    } catch (e: Exception) {
        onStateChange(UpdateInstallState.Failed(e.message ?: "unknown"))
    }
}

suspend fun downloadUpdateFile(
    url: String,
    onProgress: (bytesRead: Long, total: Long) -> Unit
): File {
    val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30 * 60 * 1000L
            connectTimeoutMillis = 30_000L
            socketTimeoutMillis = 5 * 60 * 1000L
        }
        followRedirects = true
    }
    try {
        val fileName = url.substringAfterLast('/').substringBefore('?').ifEmpty { "dro-update.bin" }
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "dro-update")
        tmpDir.mkdirs()
        val outFile = File(tmpDir, fileName)
        if (outFile.exists()) outFile.delete()

        client.prepareGet(url).execute { response ->
            val total = response.contentLength() ?: -1L
            val channel: ByteReadChannel = response.bodyAsChannel()
            var read = 0L
            outFile.outputStream().buffered().use { out ->
                while (!channel.isClosedForRead) {
                    val packet = channel.readRemaining(65536L)
                    val bytes = packet.readByteArray()
                    if (bytes.isNotEmpty()) {
                        out.write(bytes)
                        read += bytes.size
                        onProgress(read, total)
                    }
                }
            }
        }
        return outFile
    } finally {
        client.close()
    }
}

fun launchInstallerAndExit(file: File) {
    val os = System.getProperty("os.name", "").lowercase()
    val launched = try {
        when {
            os.contains("win") -> {
                // Inno Setup silent flags: install without user interaction but show progress,
                // force-close any running DRO instance, and relaunch after install.
                // We spawn via cmd "start" so the installer survives after this process exits.
                val command = listOf(
                    "cmd.exe", "/c", "start", "\"DRO Updater\"", "/B",
                    file.absolutePath,
                    "/SILENT",
                    "/CLOSEAPPLICATIONS",
                    "/RESTARTAPPLICATIONS",
                    "/NORESTART"
                )
                ProcessBuilder(command).inheritIO().start()
                true
            }
            os.contains("mac") -> {
                // On macOS updates are typically .dmg — let Finder handle it.
                Desktop.getDesktop().open(file); true
            }
            else -> {
                Desktop.getDesktop().open(file); true
            }
        }
    } catch (e: Exception) {
        // Fallback: try generic Desktop.open; if that also fails, bail out without exiting.
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file)
            true
        } catch (_: Exception) { false }
    }
    if (!launched) return
    // Give the OS a brief moment to spawn the installer process before we exit,
    // so file handles on the executable are released for overwrite.
    Thread {
        try { Thread.sleep(1500) } catch (_: Exception) {}
        exitProcess(0)
    }.start()
}

suspend fun checkForUpdate(repo: String, currentVersion: String): Pair<String?, String?> {
    val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    return try {
        val response = client.get("https://api.github.com/repos/$repo/releases/latest") {
            header("Accept", "application/vnd.github.v3+json")
        }
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val tagName = json["tag_name"]?.jsonPrimitive?.content?.removePrefix("v") ?: return null to null

        if (isNewer(tagName, currentVersion)) {
            val os = System.getProperty("os.name", "").lowercase()
            val arch = System.getProperty("os.arch", "").lowercase()
            val platform = when {
                os.contains("mac") && arch.contains("aarch64") -> "macos-arm64"
                os.contains("mac") -> "macos-x64"
                os.contains("win") -> "windows-x64"
                else -> "linux"
            }

            val assets = json["assets"]?.jsonArray ?: return tagName to null
            val asset = assets.firstOrNull { a ->
                val name = a.jsonObject["name"]?.jsonPrimitive?.content ?: ""
                name.contains(platform)
            }
            val url = asset?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content
                ?: json["html_url"]?.jsonPrimitive?.content

            tagName to url
        } else {
            null to null
        }
    } catch (_: Exception) {
        null to null
    } finally {
        client.close()
    }
}

private fun isNewer(latest: String, current: String): Boolean {
    val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
    val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
    for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
        val l = latestParts.getOrElse(i) { 0 }
        val c = currentParts.getOrElse(i) { 0 }
        if (l > c) return true
        if (l < c) return false
    }
    return false
}

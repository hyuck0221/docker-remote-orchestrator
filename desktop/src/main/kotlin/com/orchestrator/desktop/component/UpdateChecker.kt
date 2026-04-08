package com.orchestrator.desktop.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orchestrator.desktop.theme.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.awt.Desktop
import java.net.URI

@Composable
fun UpdateBanner(
    currentVersion: String,
    githubRepo: String,
    scope: CoroutineScope
) {
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var downloadUrl by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        checking = true
        try {
            val (version, url) = checkForUpdate(githubRepo, currentVersion)
            latestVersion = version
            downloadUrl = url
        } catch (_: Exception) {}
        checking = false
    }

    if (!dismissed && latestVersion != null && latestVersion != currentVersion) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AccentBlue.copy(alpha = 0.08f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Update available: v$latestVersion",
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentBlue,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                TextButton(
                    onClick = {
                        downloadUrl?.let { url ->
                            try {
                                Desktop.getDesktop().browse(URI(url))
                            } catch (_: Exception) {}
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Download", style = MaterialTheme.typography.labelMedium, color = AccentBlue, fontWeight = FontWeight.SemiBold)
                }

                TextButton(
                    onClick = { dismissed = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Dismiss", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
    }
}

private suspend fun checkForUpdate(repo: String, currentVersion: String): Pair<String?, String?> {
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

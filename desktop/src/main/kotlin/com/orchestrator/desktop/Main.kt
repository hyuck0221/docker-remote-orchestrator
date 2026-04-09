package com.orchestrator.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.orchestrator.desktop.i18n.AppLanguage
import com.orchestrator.desktop.i18n.LocalStrings
import com.orchestrator.desktop.i18n.toStrings
import com.orchestrator.desktop.component.OptionChip
import com.orchestrator.desktop.component.UpdateBanner
import com.orchestrator.desktop.component.checkForUpdate
import com.orchestrator.desktop.screen.ClientScreen
import com.orchestrator.desktop.screen.HomeScreen
import com.orchestrator.desktop.screen.HostDashboardScreen
import com.orchestrator.desktop.theme.*
import com.orchestrator.desktop.util.AutoStartManager
import com.orchestrator.desktop.viewmodel.AppScreen
import com.orchestrator.desktop.viewmodel.AppViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

const val GITHUB_REPO = "hyuck0221/docker-remote-orchestrator"

fun main() = application {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val viewModel = remember { AppViewModel(scope) }
    val currentVersion = try { BuildVersion.VERSION } catch (_: Exception) { "1.0.0" }
    var showSettingsDialog by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "DRO v$currentVersion",
        icon = painterResource("icons/icon.png"),
        state = rememberWindowState(width = 960.dp, height = 700.dp)
    ) {
        val language by viewModel.language.collectAsState()
        CompositionLocalProvider(LocalStrings provides language.toStrings()) {
        val s = LocalStrings.current
        MenuBar {
            Menu(s.menuSettings) {
                Item(s.menuPreferences, onClick = { showSettingsDialog = true })
            }
        }
        AppTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    UpdateBanner(
                        currentVersion = currentVersion,
                        githubRepo = GITHUB_REPO,
                        scope = scope
                    )

                    val currentScreen by viewModel.currentScreen.collectAsState()
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (currentScreen) {
                            AppScreen.HOME -> HomeScreen(viewModel)
                            AppScreen.HOST_DASHBOARD -> HostDashboardScreen(viewModel)
                            AppScreen.CLIENT_CONNECT -> ClientScreen(viewModel)
                            AppScreen.SETTINGS -> HomeScreen(viewModel)
                        }
                    }
                }
            }

            // Global Settings Dialog (accessible from menu bar)
            if (showSettingsDialog) {
                GlobalSettingsDialog(
                    viewModel = viewModel,
                    currentVersion = currentVersion,
                    scope = scope,
                    onDismiss = { showSettingsDialog = false }
                )
            }
        }
        } // CompositionLocalProvider
    }
}

@Composable
fun GlobalSettingsDialog(
    viewModel: AppViewModel,
    currentVersion: String,
    scope: CoroutineScope,
    onDismiss: () -> Unit
) {
    val s = LocalStrings.current
    val displayName by viewModel.displayName.collectAsState()
    var name by remember { mutableStateOf(displayName) }
    var autoStart by remember { mutableStateOf(AutoStartManager.isAutoStartEnabled()) }

    // Version check state
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var downloadUrl by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.settings, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Display Name
                Text(s.displayName, style = MaterialTheme.typography.labelLarge)
                Text(s.displayNameDesc, style = MaterialTheme.typography.bodySmall, color = TextSubtle)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(s.nameLabel) },
                    placeholder = { Text(s.namePlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Auto Start
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(s.launchAtStartup, style = MaterialTheme.typography.labelLarge)
                        Text(s.launchAtStartupDesc, style = MaterialTheme.typography.bodySmall, color = TextSubtle)
                    }
                    Switch(checked = autoStart, onCheckedChange = { enabled ->
                        autoStart = enabled
                        scope.launch(Dispatchers.IO) {
                            AutoStartManager.setAutoStart(enabled)
                            val settings = com.orchestrator.common.util.AppStateManager.loadUserSettings()
                            com.orchestrator.common.util.AppStateManager.saveUserSettings(settings.copy(autoStart = enabled))
                        }
                    })
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Language
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(s.language, style = MaterialTheme.typography.labelLarge)
                        Text(s.languageDesc, style = MaterialTheme.typography.bodySmall, color = TextSubtle)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppLanguage.entries.forEach { lang ->
                            val currentLang by viewModel.language.collectAsState()
                            OptionChip(lang.label, currentLang == lang) { viewModel.updateLanguage(lang) }
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Version Info
                Text(s.version, style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(s.currentVersion(currentVersion), style = MaterialTheme.typography.bodyMedium)
                        if (checked && latestVersion != null && latestVersion != currentVersion) {
                            Text(s.newVersionAvailable(latestVersion!!), style = MaterialTheme.typography.bodySmall, color = AccentBlue, fontWeight = FontWeight.Medium)
                        } else if (checked && (latestVersion == null || latestVersion == currentVersion)) {
                            Text(s.upToDate, style = MaterialTheme.typography.bodySmall, color = StatusRunning)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (checked && latestVersion != null && latestVersion != currentVersion) {
                            TextButton(onClick = {
                                downloadUrl?.let { url ->
                                    try { Desktop.getDesktop().browse(URI(url)) } catch (_: Exception) {}
                                }
                            }) {
                                Text(s.download, style = MaterialTheme.typography.labelMedium, color = AccentBlue, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                checking = true
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val (version, url) = checkForUpdate(GITHUB_REPO, currentVersion)
                                        latestVersion = version
                                        downloadUrl = url
                                    } catch (_: Exception) {}
                                    checking = false
                                    checked = true
                                }
                            },
                            enabled = !checking,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (checking) s.checking else s.checkForUpdates,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                viewModel.updateDisplayName(name)
                onDismiss()
            }) { Text(s.save) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel) }
        },
        containerColor = Surface2,
        shape = RoundedCornerShape(12.dp)
    )
}

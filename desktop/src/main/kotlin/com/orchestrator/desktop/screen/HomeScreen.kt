package com.orchestrator.desktop.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orchestrator.common.tunnel.NgrokStatus
import com.orchestrator.common.tunnel.NgrokTunnel
import com.orchestrator.desktop.i18n.LocalStrings
import com.orchestrator.desktop.theme.*
import com.orchestrator.desktop.viewmodel.AppRole
import com.orchestrator.desktop.viewmodel.AppScreen
import com.orchestrator.desktop.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(viewModel: AppViewModel) {
    val s = LocalStrings.current
    var showHostDialog by remember { mutableStateOf(false) }
    var showClientDialog by remember { mutableStateOf(false) }
    val statusMessage by viewModel.statusMessage.collectAsState()
    val role by viewModel.role.collectAsState()
    val hostCode by viewModel.hostCode.collectAsState()
    val connectedNodes by viewModel.connectedNodes.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 380.dp).padding(32.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = s.appTitle,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = s.appSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSubtle,
                textAlign = TextAlign.Center
            )

            if (statusMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StatusExited.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusMessage,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusExited,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Button(
                onClick = { showHostDialog = true },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text(
                    s.startAsHost,
                    style = MaterialTheme.typography.labelLarge,
                    color = Surface0
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { showClientDialog = true },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(8.dp),
                border = ButtonDefaults.outlinedButtonBorder(true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(AccentTeal.copy(alpha = 0.4f))
                )
            ) {
                Text(
                    s.joinAsClient,
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentTeal
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Show running session if active
            if (role == AppRole.HOST) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StatusRunning.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(s.hostServerRunning, style = MaterialTheme.typography.labelMedium, color = StatusRunning, fontWeight = FontWeight.SemiBold)
                            Text(s.hostCodeNodes(hostCode, connectedNodes.size), style = MaterialTheme.typography.bodySmall, color = TextSubtle)
                        }
                        // Open dashboard
                        TextButton(onClick = { viewModel.navigateTo(AppScreen.HOST_DASHBOARD) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(s.open, style = MaterialTheme.typography.labelMedium, color = AccentBlue)
                        }
                        // Stop button
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = StatusExited.copy(alpha = 0.12f),
                            onClick = { viewModel.stopHost() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("\u2715", fontSize = 13.sp, color = StatusExited)
                            }
                        }
                    }
                }
            }

            if (role == AppRole.CLIENT) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentTeal.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(s.connectedAsClient, style = MaterialTheme.typography.labelMedium, color = AccentTeal, fontWeight = FontWeight.SemiBold)
                            Text(s.statusLabel(connectionState.name), style = MaterialTheme.typography.bodySmall, color = TextSubtle)
                        }
                        TextButton(onClick = { viewModel.navigateTo(AppScreen.CLIENT_CONNECT) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(s.open, style = MaterialTheme.typography.labelMedium, color = AccentBlue)
                        }
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = StatusExited.copy(alpha = 0.12f),
                            onClick = { viewModel.disconnectFromHost() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("\u2715", fontSize = 13.sp, color = StatusExited)
                            }
                        }
                    }
                }
            }
        }
    }

    var showNgrokSetup by remember { mutableStateOf(false) }

    if (showHostDialog) {
        MinimalDialog(
            title = s.startHost,
            onDismiss = { showHostDialog = false }
        ) {
            var port by remember { mutableStateOf("9090") }
            var enableNgrok by remember { mutableStateOf(false) }
            var ngrokStatus by remember { mutableStateOf<NgrokStatus?>(null) }

            // Check ngrok status on first render
            LaunchedEffect(Unit) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ngrokStatus = NgrokTunnel.checkStatus()
                }
            }

            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { c -> c.isDigit() } },
                label = { Text(s.port) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(s.externalAccess, style = MaterialTheme.typography.bodyMedium)
                    when (ngrokStatus) {
                        NgrokStatus.READY -> Text(
                            s.allowExternalConnections,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSubtle
                        )
                        NgrokStatus.NOT_INSTALLED -> Text(
                            s.ngrokNotInstalled,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusExited
                        )
                        NgrokStatus.NOT_CONFIGURED -> Text(
                            s.ngrokNotConfigured,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusPaused
                        )
                        null -> Text(
                            s.checkingNgrok,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSubtle
                        )
                    }
                }
                if (ngrokStatus == NgrokStatus.READY) {
                    Switch(checked = enableNgrok, onCheckedChange = { enableNgrok = it })
                } else {
                    TextButton(
                        onClick = { showNgrokSetup = true },
                        enabled = ngrokStatus != null
                    ) { Text(s.setup, style = MaterialTheme.typography.labelMedium, color = AccentBlue) }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    showHostDialog = false
                    viewModel.startHost(port.toIntOrNull() ?: 9090, enableNgrok = enableNgrok)
                },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(8.dp)
            ) { Text(s.start) }
        }
    }

    if (showNgrokSetup) {
        NgrokSetupDialog(
            onDismiss = { showNgrokSetup = false },
            onTokenSaved = { showNgrokSetup = false }
        )
    }

    if (showClientDialog) {
        MinimalDialog(
            title = s.connectToHost,
            onDismiss = { showClientDialog = false }
        ) {
            var host by remember { mutableStateOf("localhost") }
            var port by remember { mutableStateOf("9090") }
            var code by remember { mutableStateOf("") }
            val isNgrok = NgrokTunnel.isNgrokUrl(host)

            OutlinedTextField(
                value = host, onValueChange = { host = it.trim() },
                label = { Text(if (isNgrok) s.ngrokUrl else s.address) },
                placeholder = { Text(s.addressPlaceholder) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            if (isNgrok) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    s.ngrokDetected,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSubtle
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = port, onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text(s.port) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = code, onValueChange = { code = it.uppercase().take(8) },
                label = { Text(s.hostCode) }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    showClientDialog = false
                    val connectPort = if (isNgrok) 443 else (port.toIntOrNull() ?: 9090)
                    viewModel.connectToHost(host, connectPort, code)
                },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = code.length == 8
            ) { Text(s.connect) }
        }
    }
}

@Composable
private fun NgrokSetupDialog(onDismiss: () -> Unit, onTokenSaved: () -> Unit) {
    val s = LocalStrings.current
    var ngrokStatus by remember { mutableStateOf<NgrokStatus?>(null) }
    var authToken by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var saveResult by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ngrokStatus = NgrokTunnel.checkStatus()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.ngrokSetup, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                when (ngrokStatus) {
                    NgrokStatus.NOT_INSTALLED -> {
                        Text(s.ngrokNotInstalledDesc, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(s.installSteps, style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))

                        val osName = System.getProperty("os.name", "").lowercase()
                        val installCmd = when {
                            osName.contains("mac") -> "brew install ngrok"
                            osName.contains("win") -> "choco install ngrok"
                            else -> "snap install ngrok"
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            SelectionContainer {
                                Text(
                                    installCmd,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            s.orDownloadFrom,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSubtle
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            s.afterInstallingReopen,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSubtle
                        )
                    }

                    NgrokStatus.NOT_CONFIGURED -> {
                        Text(s.ngrokNeedsToken, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            s.ngrokTokenInstructions,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSubtle
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = authToken,
                            onValueChange = { authToken = it.trim(); saveResult = null },
                            label = { Text(s.authToken) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("2abc...") }
                        )
                        if (saveResult == true) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(s.tokenSaved, style = MaterialTheme.typography.bodySmall, color = StatusRunning)
                        } else if (saveResult == false) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(s.tokenSaveFailed, style = MaterialTheme.typography.bodySmall, color = StatusExited)
                        }
                    }

                    NgrokStatus.READY -> {
                        Text(s.ngrokReady, style = MaterialTheme.typography.bodyMedium, color = StatusRunning)
                    }

                    null -> {
                        Text(s.checkingNgrokStatus, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            when (ngrokStatus) {
                NgrokStatus.NOT_CONFIGURED -> {
                    Button(
                        onClick = {
                            saving = true
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                val success = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    NgrokTunnel.setAuthToken(authToken)
                                }
                                saveResult = success
                                saving = false
                                if (success) {
                                    kotlinx.coroutines.delay(1000)
                                    onTokenSaved()
                                }
                            }
                        },
                        enabled = authToken.length > 10 && !saving
                    ) { Text(if (saving) s.saving else s.saveToken) }
                }
                NgrokStatus.READY -> {
                    Button(onClick = onTokenSaved) { Text(s.done) }
                }
                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel) }
        },
        containerColor = Surface2,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun MinimalDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(content = content)
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel) }
        },
        containerColor = Surface2,
        shape = RoundedCornerShape(12.dp)
    )
}

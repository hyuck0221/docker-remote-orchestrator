package com.orchestrator.desktop.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orchestrator.desktop.i18n.LocalStrings
import com.orchestrator.desktop.theme.*
import com.orchestrator.desktop.viewmodel.AppRole
import com.orchestrator.desktop.viewmodel.AppScreen
import com.orchestrator.desktop.viewmodel.AppViewModel

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
                enabled = role == AppRole.NONE,
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
                enabled = role == AppRole.NONE,
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

    if (showHostDialog) {
        MinimalDialog(
            title = s.startHost,
            onDismiss = { showHostDialog = false }
        ) {
            var port by remember { mutableStateOf("9090") }

            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { c -> c.isDigit() } },
                label = { Text(s.port) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    showHostDialog = false
                    viewModel.startHost(port.toIntOrNull() ?: 9090)
                },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(8.dp)
            ) { Text(s.start) }
        }
    }

    if (showClientDialog) {
        MinimalDialog(
            title = s.connectToHost,
            onDismiss = { showClientDialog = false }
        ) {
            var manualMode by remember { mutableStateOf(false) }
            var token by remember { mutableStateOf("") }
            var tokenError by remember { mutableStateOf(false) }
            var host by remember { mutableStateOf("localhost") }
            var port by remember { mutableStateOf("9090") }
            var code by remember { mutableStateOf("") }

            if (!manualMode) {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.trim(); tokenError = false },
                    label = { Text(s.connectionToken) },
                    placeholder = { Text(s.connectionTokenPlaceholder) },
                    singleLine = true,
                    isError = tokenError,
                    modifier = Modifier.fillMaxWidth()
                )
                // Live preview of decoded values so the user can verify host/port/code match the host.
                val preview = remember(token) {
                    if (token.isNotBlank()) com.orchestrator.common.util.ConnectionToken.decode(token) else null
                }
                if (preview != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "→ ${preview.host}:${preview.port}  ·  ${preview.code}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSubtle,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                if (tokenError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(s.connectionTokenInvalid, style = MaterialTheme.typography.bodySmall, color = StatusExited)
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { manualMode = true }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(s.manualEntry, style = MaterialTheme.typography.labelSmall, color = TextSubtle)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (viewModel.connectWithToken(token)) {
                            showClientDialog = false
                        } else {
                            tokenError = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    enabled = token.isNotBlank()
                ) { Text(s.connect) }
            } else {
                OutlinedTextField(
                    value = host, onValueChange = { host = it.trim() },
                    label = { Text(s.address) },
                    placeholder = { Text(s.addressPlaceholder) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = port, onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text(s.port) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = code, onValueChange = { code = it.uppercase().take(8) },
                    label = { Text(s.hostCode) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { manualMode = false }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(s.connectionToken, style = MaterialTheme.typography.labelSmall, color = TextSubtle)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        showClientDialog = false
                        viewModel.connectToHost(host, port.toIntOrNull() ?: 9090, code)
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    enabled = code.length == 8
                ) { Text(s.connect) }
            }
        }
    }
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

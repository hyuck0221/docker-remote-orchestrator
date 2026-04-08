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
import com.orchestrator.desktop.theme.*
import com.orchestrator.desktop.viewmodel.AppViewModel

@Composable
fun HomeScreen(viewModel: AppViewModel) {
    var showHostDialog by remember { mutableStateOf(false) }
    var showClientDialog by remember { mutableStateOf(false) }
    val statusMessage by viewModel.statusMessage.collectAsState()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 380.dp).padding(32.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Docker Remote\nOrchestrator",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Manage containers across your network",
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
                    "Start as Host",
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
                    "Join as Client",
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentTeal
                )
            }
        }
    }

    if (showHostDialog) {
        MinimalDialog(
            title = "Start Host",
            onDismiss = { showHostDialog = false }
        ) {
            var port by remember { mutableStateOf("9090") }
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { c -> c.isDigit() } },
                label = { Text("Port") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { showHostDialog = false; viewModel.startHost(port.toIntOrNull() ?: 9090) },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Start") }
        }
    }

    if (showClientDialog) {
        MinimalDialog(
            title = "Connect to Host",
            onDismiss = { showClientDialog = false }
        ) {
            var host by remember { mutableStateOf("localhost") }
            var port by remember { mutableStateOf("9090") }
            var code by remember { mutableStateOf("") }

            OutlinedTextField(
                value = host, onValueChange = { host = it },
                label = { Text("Address") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = port, onValueChange = { port = it.filter { c -> c.isDigit() } },
                label = { Text("Port") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = code, onValueChange = { code = it.uppercase().take(8) },
                label = { Text("Host Code") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { showClientDialog = false; viewModel.connectToHost(host, port.toIntOrNull() ?: 9090, code) },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = code.length == 8
            ) { Text("Connect") }
        }
    }
}

@Composable
private fun MinimalDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
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
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = Surface2,
        shape = RoundedCornerShape(12.dp)
    )
}

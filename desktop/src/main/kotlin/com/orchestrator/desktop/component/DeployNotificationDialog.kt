package com.orchestrator.desktop.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orchestrator.common.protocol.WsMessage
import com.orchestrator.desktop.theme.*
import androidx.compose.ui.window.Dialog

@Composable
fun DeployNotificationDialog(
    request: WsMessage.DeployRequest,
    onAccept: () -> Unit,
    onDefer: () -> Unit
) {
    Dialog(onDismissRequest = {}) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Surface1,
            modifier = Modifier.width(380.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Deploy Request",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "${request.fromHostName} wants to deploy a container to this node.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Config preview
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Surface2.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        DetailRow("Image", request.config.image)
                        request.config.containerName?.let { DetailRow("Name", it) }
                        if (request.config.ports.isNotEmpty()) {
                            DetailRow(
                                "Ports",
                                request.config.ports.filter { it.publicPort != null }
                                    .joinToString(", ") { "${it.publicPort}:${it.privatePort}" }
                            )
                        }
                        if (request.config.env.isNotEmpty()) {
                            DetailRow("Env", "${request.config.env.size} var(s)")
                        }
                        if (request.config.volumes.isNotEmpty()) {
                            DetailRow("Volumes", "${request.config.volumes.size} mount(s)")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDefer) {
                        Text("Later", color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                    ) {
                        Text("Accept", color = Surface0)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.width(60.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

package com.orchestrator.desktop.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.orchestrator.common.model.ContainerInfo
import com.orchestrator.common.model.DeployConfig
import com.orchestrator.common.model.NodeInfo
import com.orchestrator.common.protocol.DeployMode
import com.orchestrator.desktop.i18n.LocalStrings
import com.orchestrator.desktop.theme.*

@Composable
fun DeployDialog(
    container: ContainerInfo,
    remoteNodes: Map<String, NodeInfo>,
    selfNodeId: String? = null,
    initialConfig: DeployConfig? = null,
    onDeploy: (targetNodeIds: List<String>, config: DeployConfig, mode: DeployMode) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalStrings.current
    var selectedMode by remember { mutableStateOf(DeployMode.INSTANT) }
    val selectedNodes = remember { mutableStateMapOf<String, Boolean>() }
    var containerName by remember { mutableStateOf(initialConfig?.containerName ?: container.name) }
    var envVars by remember { mutableStateOf(initialConfig?.env?.joinToString("\n") ?: "") }
    var volumes by remember { mutableStateOf(initialConfig?.volumes?.joinToString("\n") ?: "") }

    // Filter out self — all other nodes (including host) are deployable targets
    val deployableNodes = remoteNodes.filter { it.key != selfNodeId }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Surface1,
            modifier = Modifier.width(420.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    s.deployContainer,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Source container info
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Surface2.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(s.sourceContainer, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(container.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            container.image,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = TextSubtle
                        )
                        if (container.ports.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Ports: " + container.ports.filter { it.publicPort != null }
                                    .joinToString(", ") { "${it.publicPort}:${it.privatePort}" },
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Deploy mode selection
                Text(s.deployMode, style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeployModeChip(s.instant, selectedMode == DeployMode.INSTANT) { selectedMode = DeployMode.INSTANT }
                    DeployModeChip(s.approval, selectedMode == DeployMode.APPROVAL) { selectedMode = DeployMode.APPROVAL }
                }
                Text(
                    if (selectedMode == DeployMode.INSTANT) s.instantDesc
                    else s.approvalDesc,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Target node selection
                Text(s.targetNodes, style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Spacer(modifier = Modifier.height(6.dp))

                if (deployableNodes.isEmpty()) {
                    Text(
                        s.noRemoteNodes,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                        items(deployableNodes.entries.toList()) { (nodeId, nodeInfo) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Checkbox(
                                    checked = selectedNodes[nodeId] == true,
                                    onCheckedChange = { selectedNodes[nodeId] = it },
                                    colors = CheckboxDefaults.colors(checkedColor = AccentTeal)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(nodeInfo.hostName, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${nodeInfo.containers.size} containers | ${nodeInfo.os}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Optional config overrides
                Text(s.options, style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = containerName,
                    onValueChange = { containerName = it },
                    label = { Text(s.containerName, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = envVars,
                    onValueChange = { envVars = it },
                    label = { Text(s.environmentLabel, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 60.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = volumes,
                    onValueChange = { volumes = it },
                    label = { Text(s.volumesLabel, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 60.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(s.cancel, color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    val hasTargets = selectedNodes.any { it.value }
                    Button(
                        onClick = {
                            val targets = selectedNodes.filter { it.value }.keys.toList()
                            val labels = initialConfig?.labels?.toMutableMap() ?: mutableMapOf<String, String>().also { m ->
                                container.composeProject?.let { m["com.docker.compose.project"] = it }
                                container.composeService?.let { m["com.docker.compose.service"] = it }
                            }
                            val config = DeployConfig(
                                image = container.image,
                                containerName = containerName.ifBlank { null },
                                ports = initialConfig?.ports ?: container.ports,
                                env = envVars.lines().filter { it.contains("=") },
                                volumes = volumes.lines().filter { it.contains(":") },
                                restartPolicy = initialConfig?.restartPolicy ?: "no",
                                labels = labels
                            )
                            onDeploy(targets, config, selectedMode)
                        },
                        enabled = hasTargets,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                    ) {
                        Text(s.deploy, color = Surface0)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeployModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) AccentTeal.copy(alpha = 0.15f) else Surface2.copy(alpha = 0.4f),
        onClick = onClick
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) AccentTeal else TextMuted,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

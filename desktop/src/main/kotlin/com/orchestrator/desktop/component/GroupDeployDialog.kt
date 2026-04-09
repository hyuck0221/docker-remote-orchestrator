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
fun GroupDeployDialog(
    containers: List<ContainerInfo>,
    projectName: String,
    remoteNodes: Map<String, NodeInfo>,
    selfNodeId: String? = null,
    initialConfigs: List<DeployConfig>? = null,
    onDeploy: (targetNodeIds: List<String>, configs: List<DeployConfig>, mode: DeployMode) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalStrings.current
    var selectedMode by remember { mutableStateOf(DeployMode.INSTANT) }
    val selectedNodes = remember { mutableStateMapOf<String, Boolean>() }

    val deployableNodes = remoteNodes.filter { it.key != selfNodeId }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Surface1,
            modifier = Modifier.width(420.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    s.deployGroup,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Group info
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentMauve.copy(alpha = 0.06f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.project, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                projectName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentMauve
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            s.containersCount(containers.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        containers.forEach { c ->
                            Text(
                                "  ${c.name}  ${c.image}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = TextSubtle,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Deploy mode
                Text(s.deployMode, style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip(s.instant, selectedMode == DeployMode.INSTANT) { selectedMode = DeployMode.INSTANT }
                    ModeChip(s.approval, selectedMode == DeployMode.APPROVAL) { selectedMode = DeployMode.APPROVAL }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Target nodes
                Text(s.targetNodes, style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Spacer(modifier = Modifier.height(6.dp))

                if (deployableNodes.isEmpty()) {
                    Text(s.noRemoteNodes, style = MaterialTheme.typography.bodySmall, color = TextMuted)
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

                Spacer(modifier = Modifier.height(20.dp))

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
                            val configs = if (initialConfigs != null && initialConfigs.size == containers.size) {
                                initialConfigs
                            } else {
                                containers.map { container ->
                                    val labels = mutableMapOf<String, String>()
                                    container.composeProject?.let { labels["com.docker.compose.project"] = it }
                                    container.composeService?.let { labels["com.docker.compose.service"] = it }
                                    DeployConfig(
                                        image = container.image,
                                        containerName = container.name,
                                        ports = container.ports,
                                        labels = labels
                                    )
                                }
                            }
                            onDeploy(targets, configs, selectedMode)
                        },
                        enabled = hasTargets,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                    ) {
                        Text(s.deployAll(containers.size), color = Surface0)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
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

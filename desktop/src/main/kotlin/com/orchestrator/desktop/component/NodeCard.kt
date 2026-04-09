package com.orchestrator.desktop.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orchestrator.common.model.ContainerStatus
import com.orchestrator.common.model.NodeInfo
import com.orchestrator.common.model.Permission
import com.orchestrator.common.protocol.ContainerAction
import com.orchestrator.desktop.theme.*

@Composable
fun NodeCard(
    nodeId: String,
    nodeInfo: NodeInfo,
    onPermissionChange: (Permission) -> Unit,
    onContainerAction: (String, ContainerAction) -> Unit,
    showPermissionToggle: Boolean = true,
    canControl: Boolean = nodeInfo.permission == Permission.FULL_CONTROL,
    processingContainers: Set<String> = emptySet(),
    onLog: ((String, String) -> Unit)? = null  // (containerId, containerName)
) {
    var expanded by remember { mutableStateOf(false) }
    val running = nodeInfo.containers.count { it.status == ContainerStatus.RUNNING }
    val total = nodeInfo.containers.size

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = Surface2
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Running count badge
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = if (running > 0) StatusRunning.copy(alpha = 0.1f) else Surface3
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "$running",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (running > 0) StatusRunning else TextMuted
                        )
                        Text(
                            text = "/ $total",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Node info
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = nodeInfo.hostName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${nodeInfo.os}  \u00B7  Docker ${nodeInfo.dockerVersion}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }

                // Permission controls or label
                if (showPermissionToggle) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        PermChip("Full", Permission.FULL_CONTROL, nodeInfo.permission, onPermissionChange)
                        PermChip("Read", Permission.READ_ONLY, nodeInfo.permission, onPermissionChange)
                        PermChip("Deny", Permission.DENIED, nodeInfo.permission, onPermissionChange)
                    }
                } else {
                    PermLabel(nodeInfo.permission)
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Chevron
                Text(
                    text = if (expanded) "\u25B4" else "\u25BE",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            // Container list
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                ) {
                    Divider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (nodeInfo.containers.isEmpty()) {
                        Text(
                            text = "No containers",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        val grouped = nodeInfo.containers.groupBy { it.composeProject }
                        val composeGroups = grouped.filterKeys { it != null }
                        val ungrouped = grouped[null] ?: emptyList()

                        composeGroups.forEach { (project, containers) ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                color = AccentMauve.copy(alpha = 0.04f)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    val grpRunning = containers.count { it.status == ContainerStatus.RUNNING }
                                    Text(
                                        "$project  ($grpRunning/${containers.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = AccentMauve,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                    containers.forEach { container ->
                                        ContainerCard(
                                            container = container,
                                            showControls = canControl,
                                            isProcessing = container.id in processingContainers,
                                            onAction = { action -> onContainerAction(container.id, action) },
                                            onLog = if (onLog != null) {
                                                { onLog(container.id, container.name) }
                                            } else null
                                        )
                                    }
                                }
                            }
                        }

                        ungrouped.forEach { container ->
                            ContainerCard(
                                container = container,
                                showControls = canControl,
                                isProcessing = container.id in processingContainers,
                                onAction = { action -> onContainerAction(container.id, action) },
                                onLog = if (onLog != null) {
                                    { onLog(container.id, container.name) }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermChip(
    label: String,
    permission: Permission,
    current: Permission,
    onClick: (Permission) -> Unit
) {
    val selected = current == permission
    Surface(
        modifier = Modifier
            .height(24.dp)
            .clickable { onClick(permission) },
        shape = RoundedCornerShape(4.dp),
        color = if (selected) AccentBlue.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 7.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) AccentBlue else TextMuted,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun PermLabel(permission: Permission) {
    val (text, color) = when (permission) {
        Permission.FULL_CONTROL -> "Full Control" to AccentTeal
        Permission.READ_ONLY -> "Read Only" to AccentBlue
        Permission.DENIED -> "Denied" to StatusExited
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Medium
    )
}


package com.orchestrator.desktop.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orchestrator.client.network.ConnectionState
import com.orchestrator.common.model.ContainerInfo
import com.orchestrator.common.model.ContainerStatus
import com.orchestrator.common.model.NodeInfo
import com.orchestrator.common.model.Permission
import com.orchestrator.desktop.component.*
import com.orchestrator.desktop.theme.*
import com.orchestrator.desktop.viewmodel.AppViewModel

@Composable
fun ClientScreen(viewModel: AppViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val localContainers by viewModel.localContainers.collectAsState()
    val permission by viewModel.clientPermission.collectAsState()
    val remoteNodes by viewModel.remoteNodes.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val processing by viewModel.processingContainers.collectAsState()
    val logOutput by viewModel.logOutput.collectAsState()
    val logName by viewModel.logContainerName.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Bar ──
            Surface(color = Surface1, shadowElevation = 1.dp) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Client", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        StatusPill(connectionState.name, when (connectionState) {
                            ConnectionState.CONNECTED -> StatusRunning
                            ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> StatusPaused
                            ConnectionState.DISCONNECTED -> StatusExited
                        })
                        Spacer(modifier = Modifier.width(6.dp))
                        StatusPill(permission.name.replace("_", " "), AccentBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { viewModel.disconnectFromHost() }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            Text("Disconnect", style = MaterialTheme.typography.labelMedium, color = StatusExited)
                        }
                    }
                    Row(modifier = Modifier.padding(horizontal = 24.dp)) {
                        TabButton("My Containers", selectedTab == 0) { selectedTab = 0 }
                        Spacer(modifier = Modifier.width(16.dp))
                        if (permission != Permission.DENIED) {
                            TabButton("Network", selectedTab == 1) { selectedTab = 1 }
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 0.5.dp)
                }
            }

            when (selectedTab) {
                0 -> ContainersTab(viewModel, localContainers, processing)
                1 -> NetworkTab(viewModel, remoteNodes, permission, processing)
            }
        }

        // Log overlay
        if (logName.isNotEmpty()) {
            Surface(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.45f).align(Alignment.BottomCenter), shadowElevation = 8.dp) {
                LogPanel(output = logOutput, containerName = logName, onClose = { viewModel.closeLogViewer() })
            }
        }
    }
}

@Composable
private fun ContainersTab(viewModel: AppViewModel, containers: List<ContainerInfo>, processing: Set<String>) {
    if (containers.isEmpty()) { EmptyState("No containers found", "Make sure Docker is running"); return }
    val running = containers.count { it.status == ContainerStatus.RUNNING }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
        item { Text("$running of ${containers.size} running", style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(bottom = 8.dp)) }
        items(containers) { container ->
            ContainerCard(
                container = container, showControls = true, isProcessing = container.id in processing,
                onAction = { action -> viewModel.executeLocalCommand(container.id, action) },
                onLog = if (container.status == ContainerStatus.RUNNING) { { viewModel.openLogViewer(container.id, container.name) } } else null
            )
        }
    }
}

@Composable
private fun NetworkTab(viewModel: AppViewModel, remoteNodes: Map<String, NodeInfo>, permission: Permission, processing: Set<String>) {
    if (permission == Permission.DENIED) { EmptyState("Access Denied", "You don't have permission to view the network"); return }
    if (remoteNodes.isEmpty()) { EmptyState("No other nodes", "Waiting for nodes to connect"); return }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
        item { Text("${remoteNodes.size} node(s) on network", style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(bottom = 8.dp)) }
        items(remoteNodes.entries.toList()) { (nodeId, nodeInfo) ->
            NodeCard(
                nodeId = nodeId, nodeInfo = nodeInfo, onPermissionChange = {},
                onContainerAction = { cid, action -> viewModel.sendContainerCommand(nodeId, cid, action) },
                showPermissionToggle = false, canControl = permission == Permission.FULL_CONTROL, processingContainers = processing,
                onLog = { cid, cname -> viewModel.openLogViewer(cid, cname) }
            )
        }
    }
}

@Composable
private fun StatusPill(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(5.dp), color = color.copy(alpha = 0.08f)) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
    }
}

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orchestrator.common.model.ContainerInfo
import com.orchestrator.common.model.ContainerStatus
import com.orchestrator.common.model.NodeInfo
import com.orchestrator.common.model.Permission
import com.orchestrator.common.tunnel.TunnelState
import com.orchestrator.desktop.component.*
import com.orchestrator.desktop.theme.*
import com.orchestrator.desktop.viewmodel.AppViewModel

@Composable
fun HostDashboardScreen(viewModel: AppViewModel) {
    val hostCode by viewModel.hostCode.collectAsState()
    val connectedNodes by viewModel.connectedNodes.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val processing by viewModel.processingContainers.collectAsState()
    val logOutput by viewModel.logOutput.collectAsState()
    val logName by viewModel.logContainerName.collectAsState()
    val tunnelState by viewModel.tunnelState.collectAsState()
    val tunnelUrl by viewModel.tunnelUrl.collectAsState()
    val tunnelError by viewModel.tunnelError.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    val hostNode = connectedNodes.entries.firstOrNull { it.key.startsWith("host-") }
    val remoteNodes = connectedNodes.filterKeys { !it.startsWith("host-") }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Bar ──
            Surface(color = Surface1, shadowElevation = 1.dp) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Host", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))

                        var codeCopied by remember { mutableStateOf(false) }
                        Surface(
                            shape = RoundedCornerShape(6.dp), color = AccentBlue.copy(alpha = 0.08f),
                            onClick = {
                                java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                    .setContents(java.awt.datatransfer.StringSelection(hostCode), null)
                                codeCopied = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(if (codeCopied) "COPIED" else "CODE", style = MaterialTheme.typography.labelSmall, color = if (codeCopied) StatusRunning else TextMuted)
                                Text(hostCode, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AccentBlue, letterSpacing = 1.5.sp)
                            }
                        }
                        LaunchedEffect(codeCopied) { if (codeCopied) { kotlinx.coroutines.delay(2000); codeCopied = false } }

                        // Ngrok tunnel URL
                        if (tunnelState != TunnelState.STOPPED) {
                            Spacer(modifier = Modifier.width(8.dp))
                            var urlCopied by remember { mutableStateOf(false) }
                            val tunnelColor = when (tunnelState) {
                                TunnelState.RUNNING -> AccentTeal
                                TunnelState.STARTING -> StatusPaused
                                TunnelState.ERROR -> StatusExited
                                TunnelState.STOPPED -> TextMuted
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = tunnelColor.copy(alpha = 0.08f),
                                onClick = {
                                    tunnelUrl?.let { url ->
                                        java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                            .setContents(java.awt.datatransfer.StringSelection(url), null)
                                        urlCopied = true
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        when {
                                            urlCopied -> "COPIED"
                                            tunnelState == TunnelState.STARTING -> "TUNNEL..."
                                            tunnelState == TunnelState.ERROR -> tunnelError?.take(30) ?: "ERROR"
                                            else -> tunnelUrl?.removePrefix("https://") ?: ""
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tunnelColor,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                }
                            }
                            LaunchedEffect(urlCopied) { if (urlCopied) { kotlinx.coroutines.delay(2000); urlCopied = false } }
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = StatusRunning.copy(alpha = 0.08f)) {
                            Text("${connectedNodes.size} nodes", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = StatusRunning)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { viewModel.stopHost() }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            Text("Stop", style = MaterialTheme.typography.labelMedium, color = StatusExited)
                        }
                    }

                    Row(modifier = Modifier.padding(horizontal = 24.dp)) {
                        TabButton("My Node", selectedTab == 0) { selectedTab = 0 }
                        Spacer(modifier = Modifier.width(16.dp))
                        TabButton("Nodes (${remoteNodes.size})", selectedTab == 1) { selectedTab = 1 }
                        Spacer(modifier = Modifier.width(16.dp))
                        TabButton("Settings", selectedTab == 2) { selectedTab = 2 }
                    }
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 0.5.dp)
                }
            }

            when (selectedTab) {
                0 -> MyNodeTab(viewModel, hostNode, processing)
                1 -> RemoteNodesTab(viewModel, remoteNodes, processing)
                2 -> SettingsContent(viewModel)
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
private fun MyNodeTab(viewModel: AppViewModel, hostNode: Map.Entry<String, NodeInfo>?, processing: Set<String>) {
    if (hostNode == null) { EmptyState("Docker not available", "Could not connect to local Docker engine"); return }
    val (nodeId, nodeInfo) = hostNode
    val running = nodeInfo.containers.count { it.status == ContainerStatus.RUNNING }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
        item {
            Text("$running of ${nodeInfo.containers.size} running", style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(bottom = 8.dp))
        }
        items(nodeInfo.containers) { container ->
            ContainerCard(
                container = container, showControls = true, isProcessing = container.id in processing,
                onAction = { action -> viewModel.sendContainerCommand(nodeId, container.id, action) },
                onLog = if (container.status == ContainerStatus.RUNNING) { { viewModel.openLogViewer(container.id, container.name) } } else null
            )
        }
    }
}

@Composable
private fun RemoteNodesTab(viewModel: AppViewModel, remoteNodes: Map<String, NodeInfo>, processing: Set<String>) {
    if (remoteNodes.isEmpty()) { EmptyState("No remote nodes", "Share the host code with your team"); return }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
        items(remoteNodes.entries.toList()) { (nodeId, nodeInfo) ->
            NodeCard(
                nodeId = nodeId, nodeInfo = nodeInfo,
                onPermissionChange = { perm -> viewModel.updateNodePermission(nodeId, perm) },
                onContainerAction = { cid, action -> viewModel.sendContainerCommand(nodeId, cid, action) },
                showPermissionToggle = true, canControl = true, processingContainers = processing,
                onLog = { cid, cname -> viewModel.openLogViewer(cid, cname) }
            )
        }
    }
}

@Composable
private fun SettingsContent(viewModel: AppViewModel) {
    val defaultPerm by viewModel.defaultPermission.collectAsState()
    val pingInterval by viewModel.pingInterval.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("General", style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(bottom = 4.dp)) }
        item {
            SettingCard(title = "Default permission for new nodes", subtitle = "Applied when a new client joins") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Permission.entries.forEach { perm -> OptionChip(perm.name.replace("_", " "), defaultPerm == perm) { viewModel.updateDefaultPermission(perm) } }
                }
            }
        }
        item {
            SettingCard(title = "WebSocket ping interval", subtitle = "How often to check connection health") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5L, 10L, 15L, 30L, 60L).forEach { sec -> OptionChip("${sec}s", pingInterval == sec) { viewModel.updatePingInterval(sec) } }
                }
            }
        }
    }
}

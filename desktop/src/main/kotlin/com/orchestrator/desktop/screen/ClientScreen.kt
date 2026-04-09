package com.orchestrator.desktop.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.unit.sp
import com.orchestrator.client.network.ConnectionState
import com.orchestrator.common.model.ContainerInfo
import com.orchestrator.common.model.ContainerStatus
import com.orchestrator.common.model.DeployConfig
import com.orchestrator.common.model.NodeInfo
import com.orchestrator.common.model.Permission
import com.orchestrator.common.protocol.DeployMode
import com.orchestrator.desktop.component.*
import com.orchestrator.desktop.i18n.LocalStrings
import com.orchestrator.desktop.theme.*
import com.orchestrator.desktop.viewmodel.AppViewModel

@Composable
fun ClientScreen(viewModel: AppViewModel) {
    val s = LocalStrings.current
    val connectionState by viewModel.connectionState.collectAsState()
    val localContainers by viewModel.localContainers.collectAsState()
    val permission by viewModel.clientPermission.collectAsState()
    val remoteNodes by viewModel.remoteNodes.collectAsState()
    val processing by viewModel.processingContainers.collectAsState()
    val logOutput by viewModel.logOutput.collectAsState()
    val logName by viewModel.logContainerName.collectAsState()
    val activeDeployNotification by viewModel.activeDeployNotification.collectAsState()
    val pendingDeploys by viewModel.pendingDeploys.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var logExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Bar ──
            Surface(color = Surface1, shadowElevation = 1.dp) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(s.client, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        StatusPill(connectionState.name, when (connectionState) {
                            ConnectionState.CONNECTED -> StatusRunning
                            ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> StatusPaused
                            ConnectionState.DISCONNECTED -> StatusExited
                        })
                        Spacer(modifier = Modifier.width(6.dp))
                        StatusPill(permission.name.replace("_", " "), AccentBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { viewModel.disconnectFromHost() }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            Text(s.disconnect, style = MaterialTheme.typography.labelMedium, color = StatusExited)
                        }
                    }
                    Row(modifier = Modifier.padding(horizontal = 24.dp)) {
                        val pendingBadge = if (pendingDeploys.isNotEmpty()) " (${pendingDeploys.size})" else ""
                        TabButton("${s.tabMyNode}$pendingBadge", selectedTab == 0) { selectedTab = 0 }
                        Spacer(modifier = Modifier.width(16.dp))
                        if (permission != Permission.DENIED) {
                            TabButton(s.tabNodes(remoteNodes.size), selectedTab == 1) { selectedTab = 1 }
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        TabButton(s.tabSettings, selectedTab == 2) { selectedTab = 2 }
                    }
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 0.5.dp)
                }
            }

            when (selectedTab) {
                0 -> MyNodeTab(viewModel, localContainers, pendingDeploys, processing, permission, remoteNodes)
                1 -> NodesTab(viewModel, remoteNodes, permission, processing)
                2 -> SettingsContent(viewModel)
            }
        }

        // Log overlay
        if (logName.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth()
                    .fillMaxHeight(if (logExpanded) 1f else 0.45f)
                    .align(Alignment.BottomCenter),
                shadowElevation = 8.dp
            ) {
                LogPanel(
                    output = logOutput,
                    containerName = logName,
                    onClose = { logExpanded = false; viewModel.closeLogViewer() },
                    expanded = logExpanded,
                    onToggleExpand = { logExpanded = !logExpanded }
                )
            }
        }

        // Deploy notification dialog
        activeDeployNotification?.let { request ->
            DeployNotificationDialog(
                request = request,
                onAccept = { viewModel.acceptDeploy(request.requestId) },
                onDefer = { viewModel.deferDeploy(request.requestId) }
            )
        }
    }
}

@Composable
private fun MyNodeTab(
    viewModel: AppViewModel,
    containers: List<ContainerInfo>,
    pendingDeploys: List<com.orchestrator.common.protocol.WsMessage.DeployRequest>,
    processing: Set<String>,
    permission: Permission,
    remoteNodes: Map<String, NodeInfo>
) {
    val s = LocalStrings.current
    if (containers.isEmpty() && pendingDeploys.isEmpty()) { EmptyState(s.dockerNotAvailable, "Make sure Docker is running"); return }
    val running = containers.count { it.status == ContainerStatus.RUNNING }
    val canDeploy = permission == Permission.FULL_CONTROL && remoteNodes.isNotEmpty()
    var deployTarget by remember { mutableStateOf<ContainerInfo?>(null) }
    var deployGroupTarget by remember { mutableStateOf<List<ContainerInfo>?>(null) }

    // Group containers by compose project
    val grouped = containers.groupBy { it.composeProject }
    val composeGroups = grouped.filterKeys { it != null }.map { (project, items) -> project!! to items }
    val ungrouped = grouped[null] ?: emptyList()

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
        // Pending deploys section
        if (pendingDeploys.isNotEmpty()) {
            item {
                Text(
                    s.pendingDeploys(pendingDeploys.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentTeal,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(pendingDeploys) { request ->
                PendingDeployCard(
                    request = request,
                    onAccept = { viewModel.acceptDeploy(request.requestId) }
                )
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
        }

        item {
            Text(s.runningOf(running, containers.size), style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(bottom = 8.dp))
        }

        // Compose project groups
        composeGroups.forEach { (project, groupContainers) ->
            item(key = "group-$project") {
                ClientContainerGroupHeader(
                    projectName = project,
                    containers = groupContainers,
                    processing = processing,
                    viewModel = viewModel,
                    onDeployGroup = if (canDeploy) { { deployGroupTarget = groupContainers } } else null,
                    onDeploySingle = if (canDeploy) { { c -> deployTarget = c } } else null
                )
            }
        }

        // Ungrouped containers
        items(ungrouped, key = { it.id }) { container ->
            ContainerCard(
                container = container, showControls = true, isProcessing = container.id in processing,
                onAction = { action -> viewModel.executeLocalCommand(container.id, action) },
                onLog = if (container.status == ContainerStatus.RUNNING) { { viewModel.openLogViewer(container.id, container.name) } } else null,
                onDeploy = if (canDeploy) { { deployTarget = container } } else null
            )
        }
    }

    // Single deploy dialog
    deployTarget?.let { container ->
        val inspectedConfig = remember(container.id) { viewModel.extractDeployConfig(container) }
        DeployDialog(
            container = container,
            remoteNodes = remoteNodes,
            selfNodeId = viewModel.selfNodeId,
            initialConfig = inspectedConfig,
            onDeploy = { targets, config, mode ->
                viewModel.sendDeployCommand(targets, config, mode)
                deployTarget = null
            },
            onDismiss = { deployTarget = null }
        )
    }

    // Group deploy dialog
    deployGroupTarget?.let { groupContainers ->
        val inspectedConfigs = remember(groupContainers.map { it.id }) {
            groupContainers.map { viewModel.extractDeployConfig(it) }
        }
        GroupDeployDialog(
            containers = groupContainers,
            projectName = groupContainers.first().composeProject ?: "",
            remoteNodes = remoteNodes,
            selfNodeId = viewModel.selfNodeId,
            initialConfigs = inspectedConfigs,
            onDeploy = { targets, configs, mode ->
                configs.forEach { config ->
                    viewModel.sendDeployCommand(targets, config, mode)
                }
                deployGroupTarget = null
            },
            onDismiss = { deployGroupTarget = null }
        )
    }
}

@Composable
private fun ClientContainerGroupHeader(
    projectName: String,
    containers: List<ContainerInfo>,
    processing: Set<String>,
    viewModel: AppViewModel,
    onDeployGroup: (() -> Unit)?,
    onDeploySingle: ((ContainerInfo) -> Unit)?
) {
    val s = LocalStrings.current
    var expanded by remember { mutableStateOf(true) }
    val running = containers.count { it.status == ContainerStatus.RUNNING }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = AccentMauve.copy(alpha = 0.06f)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(4.dp), color = AccentMauve.copy(alpha = 0.12f)) {
                    Text(
                        "$running / ${containers.size}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AccentMauve
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(projectName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (onDeployGroup != null) {
                    Surface(shape = RoundedCornerShape(4.dp), color = AccentTeal.copy(alpha = 0.12f), onClick = onDeployGroup) {
                        Text(
                            s.group,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentTeal,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(if (expanded) "\u25B4" else "\u25BE", color = TextMuted, fontSize = 12.sp)
            }

            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp)) {
                    containers.forEach { container ->
                        ContainerCard(
                            container = container, showControls = true, isProcessing = container.id in processing,
                            onAction = { action -> viewModel.executeLocalCommand(container.id, action) },
                            onLog = if (container.status == ContainerStatus.RUNNING) { { viewModel.openLogViewer(container.id, container.name) } } else null,
                            onDeploy = onDeploySingle?.let { { it(container) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NodesTab(viewModel: AppViewModel, remoteNodes: Map<String, NodeInfo>, permission: Permission, processing: Set<String>) {
    val s = LocalStrings.current
    if (permission == Permission.DENIED) { EmptyState(s.denied, "You don't have permission to view the network"); return }
    if (remoteNodes.isEmpty()) { EmptyState(s.noRemoteNodes, "Waiting for nodes to connect"); return }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
        item { Text(s.nodesCount(remoteNodes.size), style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(bottom = 8.dp)) }
        items(remoteNodes.entries.toList()) { (nodeId, nodeInfo) ->
            NodeCard(
                nodeId = nodeId, nodeInfo = nodeInfo, onPermissionChange = {},
                onContainerAction = { cid, action -> viewModel.sendContainerCommand(nodeId, cid, action) },
                showPermissionToggle = false, canControl = permission == Permission.FULL_CONTROL, processingContainers = processing,
                onLog = { cid, cname -> viewModel.openLogViewer(cid, cname, nodeId) }
            )
        }
    }
}

@Composable
private fun SettingsContent(viewModel: AppViewModel) {
    val s = LocalStrings.current
    val displayName by viewModel.displayName.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(s.general, style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(bottom = 4.dp)) }
        item {
            SettingCard(title = s.displayName, subtitle = s.displayNameDesc) {
                var name by remember { mutableStateOf(displayName) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; viewModel.updateDisplayName(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PendingDeployCard(
    request: com.orchestrator.common.protocol.WsMessage.DeployRequest,
    onAccept: () -> Unit
) {
    val s = LocalStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = AccentTeal.copy(alpha = 0.06f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(request.config.image, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(s.deployRequestFrom(request.fromHostName), style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            Surface(shape = RoundedCornerShape(6.dp), color = AccentTeal.copy(alpha = 0.15f), onClick = onAccept) {
                Text(s.deploy, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = AccentTeal, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(5.dp), color = color.copy(alpha = 0.08f)) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
    }
}

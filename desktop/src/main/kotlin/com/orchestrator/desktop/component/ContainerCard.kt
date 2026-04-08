package com.orchestrator.desktop.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orchestrator.common.model.ContainerInfo
import com.orchestrator.common.model.ContainerStatus
import com.orchestrator.common.protocol.ContainerAction
import com.orchestrator.desktop.theme.*

@Composable
fun ContainerCard(
    container: ContainerInfo,
    showControls: Boolean = false,
    isProcessing: Boolean = false,
    onAction: ((ContainerAction) -> Unit)? = null,
    onLog: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = Surface2.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(container.status.toColor())
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Name + image
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = container.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = container.image.substringAfterLast("/"),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Ports
            container.ports.filter { it.publicPort != null }.take(2).forEach { port ->
                Text(
                    text = "${port.publicPort}:${port.privatePort}",
                    modifier = Modifier
                        .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = TextSubtle,
                    fontSize = 9.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Status label
            Text(
                text = container.status.label(),
                style = MaterialTheme.typography.labelSmall,
                color = container.status.toColor(),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(56.dp)
            )

            // Log/Console button
            if (onLog != null && container.status == ContainerStatus.RUNNING) {
                ActionBtn(">_", AccentBlue) { onLog() }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Controls or spinner
            if (showControls && onAction != null) {
                Spacer(modifier = Modifier.width(4.dp))
                if (isProcessing) {
                    Box(modifier = Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 1.5.dp,
                            color = AccentBlue
                        )
                    }
                } else {
                    Row(modifier = Modifier.width(72.dp), horizontalArrangement = Arrangement.End) {
                        if (container.status != ContainerStatus.RUNNING) {
                            ActionBtn("\u25B6", StatusRunning) { onAction(ContainerAction.START) }
                        }
                        if (container.status == ContainerStatus.RUNNING) {
                            ActionBtn("\u25A0", StatusExited) { onAction(ContainerAction.STOP) }
                            Spacer(modifier = Modifier.width(2.dp))
                            ActionBtn("\u21BB", StatusPaused) { onAction(ContainerAction.RESTART) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionBtn(symbol: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(24.dp),
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.12f),
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, fontSize = 11.sp, color = color)
        }
    }
}

private fun ContainerStatus.toColor() = when (this) {
    ContainerStatus.RUNNING -> StatusRunning
    ContainerStatus.EXITED, ContainerStatus.DEAD -> StatusExited
    ContainerStatus.PAUSED -> StatusPaused
    else -> StatusOther
}

private fun ContainerStatus.label() = when (this) {
    ContainerStatus.RUNNING -> "Running"
    ContainerStatus.EXITED -> "Exited"
    ContainerStatus.PAUSED -> "Paused"
    ContainerStatus.DEAD -> "Dead"
    ContainerStatus.CREATED -> "Created"
    else -> name.lowercase().replaceFirstChar { it.uppercase() }
}

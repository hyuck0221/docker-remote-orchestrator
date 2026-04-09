package com.orchestrator.desktop.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orchestrator.desktop.i18n.LocalStrings
import com.orchestrator.desktop.theme.*

@Composable
fun LogPanel(
    output: List<String>,
    containerName: String,
    onClose: () -> Unit,
    expanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null
) {
    val s = LocalStrings.current
    val scrollState = rememberScrollState()

    LaunchedEffect(output.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(10.dp),
        color = Surface0
    ) {
        Column {
            // Header
            Surface(color = Surface2) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(8.dp)
                            .background(AccentMauve, RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = s.logsFor(containerName),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    // Expand/Collapse button
                    if (onToggleExpand != null) {
                        Surface(
                            modifier = Modifier.size(24.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = AccentBlue.copy(alpha = 0.12f),
                            onClick = onToggleExpand
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    if (expanded) "\u2913" else "\u2912",  // ⤓ ⤒
                                    fontSize = 13.sp,
                                    color = AccentBlue
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    TextButton(
                        onClick = onClose,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(s.close, style = MaterialTheme.typography.labelSmall, color = StatusExited)
                    }
                }
            }

            // Log output - selectable text
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(8.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = output.joinToString(""),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

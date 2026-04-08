package com.orchestrator.desktop.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orchestrator.desktop.theme.*

@Composable
fun EmptyState(title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextSubtle)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

@Composable
fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSurface else TextMuted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        if (selected) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                modifier = Modifier.width(24.dp).height(2.dp),
                shape = RoundedCornerShape(1.dp),
                color = AccentBlue
            ) {}
        }
    }
}

@Composable
fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.height(30.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = if (selected) AccentBlue.copy(alpha = 0.15f) else Surface3
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) AccentBlue else TextSubtle,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun SettingCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = Surface2) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

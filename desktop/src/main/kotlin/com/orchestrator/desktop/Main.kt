package com.orchestrator.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.orchestrator.desktop.component.UpdateBanner
import com.orchestrator.desktop.screen.ClientScreen
import com.orchestrator.desktop.screen.HomeScreen
import com.orchestrator.desktop.screen.HostDashboardScreen
import com.orchestrator.desktop.theme.AppTheme
import com.orchestrator.desktop.viewmodel.AppScreen
import com.orchestrator.desktop.viewmodel.AppViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

const val GITHUB_REPO = "shimhyuck/docker-remote-orchestrator"

fun main() = application {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val viewModel = remember { AppViewModel(scope) }
    val currentVersion = try { BuildVersion.VERSION } catch (_: Exception) { "1.0.0" }

    Window(
        onCloseRequest = ::exitApplication,
        title = "DRO v$currentVersion",
        icon = painterResource("icons/icon.png"),
        state = rememberWindowState(width = 960.dp, height = 700.dp)
    ) {
        AppTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    UpdateBanner(
                        currentVersion = currentVersion,
                        githubRepo = GITHUB_REPO,
                        scope = scope
                    )

                    val currentScreen by viewModel.currentScreen.collectAsState()
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (currentScreen) {
                            AppScreen.HOME -> HomeScreen(viewModel)
                            AppScreen.HOST_DASHBOARD -> HostDashboardScreen(viewModel)
                            AppScreen.CLIENT_CONNECT -> ClientScreen(viewModel)
                            AppScreen.SETTINGS -> HomeScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}

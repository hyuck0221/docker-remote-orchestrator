package com.orchestrator.common.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

@Serializable
data class AppState(
    val role: String = "NONE",  // NONE, HOST, CLIENT
    val hostConfig: HostConfig? = null,
    val clientConfig: ClientConfig? = null,
    val userSettings: UserSettings = UserSettings()
)

@Serializable
data class UserSettings(
    val displayName: String = "",
    val autoStart: Boolean = false,
    val language: String = "EN",
    val savedHostCode: String = ""
)

@Serializable
data class HostConfig(
    val port: Int = 9090,
    val hostCode: String = "",
    val enableTls: Boolean = false,
    val enableNgrok: Boolean = false
)

@Serializable
data class ClientConfig(
    val serverHost: String = "localhost",
    val serverPort: Int = 9090,
    val hostCode: String = ""
)

object AppStateManager {
    private val logger = LoggerFactory.getLogger(AppStateManager::class.java)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val stateDir = File(System.getProperty("user.home"), ".docker-orchestrator")
    private val stateFile = File(stateDir, "state.json")

    fun save(state: AppState) {
        try {
            stateDir.mkdirs()
            // Defensive: if the caller passed the default UserSettings, preserve whatever
            // is already on disk so we don't accidentally wipe persisted fields like
            // savedHostCode. Callers that intentionally mutate settings should go through
            // saveUserSettings() or pass a non-default UserSettings explicitly.
            val merged = if (state.userSettings == UserSettings()) {
                val existing = runCatching { load().userSettings }.getOrElse { UserSettings() }
                state.copy(userSettings = existing)
            } else state
            stateFile.writeText(json.encodeToString(merged))
            logger.info("State saved: role=${merged.role}")
        } catch (e: Exception) {
            logger.warn("Failed to save state: ${e.message}")
        }
    }

    fun load(): AppState {
        return try {
            if (stateFile.exists()) {
                val state = json.decodeFromString<AppState>(stateFile.readText())
                logger.info("State loaded: role=${state.role}")
                state
            } else {
                AppState()
            }
        } catch (e: Exception) {
            logger.warn("Failed to load state: ${e.message}")
            AppState()
        }
    }

    fun clear() {
        val current = load()
        save(AppState(userSettings = current.userSettings))
    }

    fun saveUserSettings(settings: UserSettings) {
        val current = load()
        save(current.copy(userSettings = settings))
    }

    fun loadUserSettings(): UserSettings {
        return load().userSettings
    }
}

package com.orchestrator.desktop.util

import org.slf4j.LoggerFactory
import java.io.File

object AutoStartManager {
    private val logger = LoggerFactory.getLogger(AutoStartManager::class.java)
    private val osName = System.getProperty("os.name", "").lowercase()

    fun setAutoStart(enabled: Boolean) {
        try {
            when {
                osName.contains("mac") -> setMacAutoStart(enabled)
                osName.contains("win") -> setWindowsAutoStart(enabled)
                else -> setLinuxAutoStart(enabled)
            }
            logger.info("Auto-start ${if (enabled) "enabled" else "disabled"}")
        } catch (e: Exception) {
            logger.error("Failed to set auto-start: ${e.message}", e)
        }
    }

    fun isAutoStartEnabled(): Boolean {
        return try {
            when {
                osName.contains("mac") -> isMacAutoStartEnabled()
                osName.contains("win") -> isWindowsAutoStartEnabled()
                else -> isLinuxAutoStartEnabled()
            }
        } catch (_: Exception) {
            false
        }
    }

    // ── macOS: LaunchAgent plist ──

    private val macPlistFile = File(
        System.getProperty("user.home"),
        "Library/LaunchAgents/com.orchestrator.dro.plist"
    )

    private fun getAppPath(): String {
        // Try to find the .app bundle or jar path
        val classPath = System.getProperty("java.class.path", "")
        val appDir = File(classPath.split(File.pathSeparator).firstOrNull() ?: "").parentFile

        // Check if running from .app bundle
        val appBundle = generateSequence(appDir) { it.parentFile }
            .firstOrNull { it.name.endsWith(".app") }

        return if (appBundle != null) {
            "open -a \"${appBundle.absolutePath}\""
        } else {
            // Fallback to jar execution
            val jarFile = appDir?.listFiles()?.firstOrNull { it.name.endsWith(".jar") }
            if (jarFile != null) "java -jar \"${jarFile.absolutePath}\"" else ""
        }
    }

    private fun setMacAutoStart(enabled: Boolean) {
        if (enabled) {
            val appPath = getAppPath()
            if (appPath.isEmpty()) {
                logger.warn("Could not determine application path for auto-start")
                return
            }
            val plist = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.orchestrator.dro</string>
    <key>ProgramArguments</key>
    <array>
        <string>/bin/sh</string>
        <string>-c</string>
        <string>$appPath</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <false/>
</dict>
</plist>"""
            macPlistFile.parentFile.mkdirs()
            macPlistFile.writeText(plist)
        } else {
            if (macPlistFile.exists()) {
                macPlistFile.delete()
            }
        }
    }

    private fun isMacAutoStartEnabled(): Boolean = macPlistFile.exists()

    // ── Windows: Registry ──

    private const val WIN_REG_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val WIN_APP_NAME = "DockerRemoteOrchestrator"

    private fun setWindowsAutoStart(enabled: Boolean) {
        val appPath = getAppPath()
        if (enabled && appPath.isEmpty()) {
            logger.warn("Could not determine application path for auto-start")
            return
        }
        val command = if (enabled) {
            arrayOf("reg", "add", WIN_REG_KEY, "/v", WIN_APP_NAME, "/t", "REG_SZ", "/d", "\"$appPath\"", "/f")
        } else {
            arrayOf("reg", "delete", WIN_REG_KEY, "/v", WIN_APP_NAME, "/f")
        }
        Runtime.getRuntime().exec(command).waitFor()
    }

    private fun isWindowsAutoStartEnabled(): Boolean {
        val process = Runtime.getRuntime().exec(arrayOf("reg", "query", WIN_REG_KEY, "/v", WIN_APP_NAME))
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return output.contains(WIN_APP_NAME)
    }

    // ── Linux: .desktop autostart ──

    private val linuxDesktopFile = File(
        System.getProperty("user.home"),
        ".config/autostart/docker-remote-orchestrator.desktop"
    )

    private fun setLinuxAutoStart(enabled: Boolean) {
        if (enabled) {
            val appPath = getAppPath()
            if (appPath.isEmpty()) {
                logger.warn("Could not determine application path for auto-start")
                return
            }
            val desktop = """[Desktop Entry]
Type=Application
Name=Docker Remote Orchestrator
Exec=$appPath
Hidden=false
NoDisplay=false
X-GNOME-Autostart-enabled=true
"""
            linuxDesktopFile.parentFile.mkdirs()
            linuxDesktopFile.writeText(desktop)
        } else {
            if (linuxDesktopFile.exists()) {
                linuxDesktopFile.delete()
            }
        }
    }

    private fun isLinuxAutoStartEnabled(): Boolean = linuxDesktopFile.exists()
}

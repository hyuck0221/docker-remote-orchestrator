plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

val appVersion: String by project.rootProject.extra {
    project.rootProject.properties["app.version"]?.toString() ?: "1.0.0"
}

dependencies {
    implementation(project(":common"))
    implementation(project(":client"))
    implementation(project(":server"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)

    implementation(libs.docker.java.core)
    implementation(libs.docker.java.transport.httpclient5)

    implementation(libs.logback.classic)
    implementation(libs.slf4j.api)
}

// Generate version file for runtime access
val generateVersionFile by tasks.registering {
    val versionValue = project.rootProject.properties["app.version"]?.toString() ?: "1.0.0"
    val outputDir = layout.buildDirectory.dir("generated/version")
    inputs.property("appVersion", versionValue)
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile.resolve("com/orchestrator/desktop")
        dir.mkdirs()
        dir.resolve("BuildVersion.kt").writeText("""
            package com.orchestrator.desktop
            object BuildVersion {
                const val VERSION = "$versionValue"
            }
        """.trimIndent())
    }
}

kotlin {
    sourceSets.main {
        kotlin.srcDir(generateVersionFile.map { layout.buildDirectory.dir("generated/version") })
    }
}

compose.desktop {
    application {
        mainClass = "com.orchestrator.desktop.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "DRO"
            packageVersion = project.rootProject.properties["app.version"]?.toString() ?: "1.0.0"
            description = "DRO - Docker Remote Orchestrator"
            includeAllModules = true

            macOS {
                bundleID = "com.orchestrator.dro"
                val icnsFile = project.file("build/generated/icons/icon.icns")
                if (icnsFile.exists()) iconFile.set(icnsFile)
                entitlementsFile.set(project.file("entitlements.plist"))
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSAppTransportSecurity</key>
                        <dict>
                            <key>NSAllowsArbitraryLoads</key>
                            <true/>
                        </dict>
                    """
                }
            }

            windows {
                val icoFile = project.file("build/generated/icons/icon.ico")
                if (icoFile.exists()) iconFile.set(icoFile)
                shortcut = true
                dirChooser = true
                menuGroup = "DRO"
                upgradeUuid = "e4a5f6c7-8b9d-4e2a-b1c3-d5e6f7a8b9c0"
                perUserInstall = true
            }

            linux {
                val pngFile = project.file("build/generated/icons/icon.png")
                if (pngFile.exists()) iconFile.set(pngFile)
            }
        }
    }
}

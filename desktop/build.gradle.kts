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
    val outputDir = layout.buildDirectory.dir("generated/version")
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile.resolve("com/orchestrator/desktop")
        dir.mkdirs()
        dir.resolve("BuildVersion.kt").writeText("""
            package com.orchestrator.desktop
            object BuildVersion {
                const val VERSION = "${project.rootProject.properties["app.version"] ?: "1.0.0"}"
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
            packageName = "DockerRemoteOrchestrator"
            packageVersion = project.rootProject.properties["app.version"]?.toString() ?: "1.0.0"
            description = "Docker Remote Orchestrator Desktop"

            macOS {
                bundleID = "com.orchestrator.desktop"
            }
        }
    }
}

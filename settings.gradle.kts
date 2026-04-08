pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "docker-remote-orchestrator"

include(":common")
include(":server")
include(":client")
include(":desktop")

plugins {
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.orchestrator.server.ApplicationKt")
}

dependencies {
    implementation(project(":common"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.call.logging)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.docker.java.core)
    implementation(libs.docker.java.transport.httpclient5)

    implementation(libs.logback.classic)
    implementation(libs.slf4j.api)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.test.host)
}

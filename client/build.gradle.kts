plugins {
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.orchestrator.client.ClientMainKt")
}

dependencies {
    implementation(project(":common"))

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.docker.java.core)
    implementation(libs.docker.java.transport.httpclient5)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.logback.classic)
    implementation(libs.slf4j.api)

    testImplementation(libs.kotlin.test)
}

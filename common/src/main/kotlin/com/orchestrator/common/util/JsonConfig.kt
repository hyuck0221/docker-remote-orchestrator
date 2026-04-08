package com.orchestrator.common.util

import kotlinx.serialization.json.Json

val AppJson = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}

val AppJsonPretty = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

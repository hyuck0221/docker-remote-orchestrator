package com.orchestrator.common.model

import kotlinx.serialization.Serializable

@Serializable
enum class Permission {
    FULL_CONTROL,
    READ_ONLY,
    DENIED
}

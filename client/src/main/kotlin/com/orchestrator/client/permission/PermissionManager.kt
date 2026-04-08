package com.orchestrator.client.permission

import com.orchestrator.common.model.Permission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.slf4j.LoggerFactory

class PermissionManager(
    initialPermission: Permission = Permission.READ_ONLY
) {
    private val logger = LoggerFactory.getLogger(PermissionManager::class.java)

    private val _currentPermission = MutableStateFlow(initialPermission)
    val currentPermission: StateFlow<Permission> = _currentPermission.asStateFlow()

    private val _localOverride = MutableStateFlow<Permission?>(null)
    val localOverride: StateFlow<Permission?> = _localOverride.asStateFlow()

    fun updatePermission(serverPermission: Permission) {
        val effective = _localOverride.value?.let { override ->
            if (override.ordinal > serverPermission.ordinal) override else serverPermission
        } ?: serverPermission

        _currentPermission.value = effective
        logger.info("Permission updated: $effective (server=$serverPermission, override=${_localOverride.value})")
    }

    fun setLocalOverride(permission: Permission?) {
        _localOverride.value = permission
        logger.info("Local permission override set: $permission")
    }

    fun isControlAllowed(): Boolean {
        return _currentPermission.value == Permission.FULL_CONTROL
    }
}

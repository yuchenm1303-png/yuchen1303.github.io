package com.yuchen.ailedger.service

import java.util.Locale

/**
 * Single source of truth for client tools that can change Android-local state or launch an
 * externally visible action.
 *
 * Read-only tools never enter the persistent at-most-once ledger. Unknown tools also default to
 * unprotected and are rejected by their domain router, rather than being silently classified here.
 */
internal object ClientToolMutationPolicy {
    private val projectMutations = setOf(
        "project_create",
        "project_write_files",
        "project_apply_edits",
        "project_delete_files",
        "project_rollback",
    )

    private val planMutations = setOf(
        "plan_create_task",
        "plan_update_task",
        "plan_delete_task",
        "plan_toggle_task",
    )

    private val ledgerMutations = setOf(
        "ledger_add_record",
        "ledger_set_budget",
    )

    private val directDeviceActions = setOf(
        "open_app",
        "open_system_settings",
        "open_app_settings",
        "set_brightness",
        "set_screen_timeout",
        "set_auto_rotate",
        "set_media_volume",
        "set_wifi_enabled",
        "set_bluetooth_enabled",
        "set_mobile_data_enabled",
        "set_dark_mode",
        "request_shizuku_permission",
        "set_animation_scale",
        "force_stop_app",
        "clear_app_data",
        "uninstall_app",
        "disable_app",
        "enable_app",
    )

    fun requiresAtMostOnce(
        call: CloudClientToolCall,
        projectedStepType: String? = null,
    ): Boolean = requiresAtMostOnce(call.name, projectedStepType)

    fun requiresAtMostOnce(
        toolName: String,
        projectedStepType: String? = null,
    ): Boolean {
        val name = normalize(toolName)
        if (name in projectMutations || name in planMutations || name in ledgerMutations) return true
        if (name in directDeviceActions) return true
        if (name != "device_control") return false
        return normalize(projectedStepType.orEmpty()) in directDeviceActions
    }

    private fun normalize(value: String): String = value
        .trim()
        .lowercase(Locale.US)
        .replace('-', '_')
}

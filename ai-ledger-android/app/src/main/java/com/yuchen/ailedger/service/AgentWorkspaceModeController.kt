package com.yuchen.ailedger.service

import com.yuchen.ailedger.AiLedgerApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val AGENT_WORKSPACE_PREFS = "agent_workspace_mode"
private const val KEY_WORKSPACE_MODE_ENABLED = "workspace_mode_enabled"

/**
 * Controls the chat workspace mode from the title-bar workspace switch.
 *
 * This is intentionally separate from [AgentRuntimeController], whose switch is still the
 * visual/GUI agent runtime. Workspace mode changes the cloud chat request contract: enabled means
 * the cloud may enter the multi-step workspace loop; disabled keeps the original chat/tool mode.
 */
object AgentWorkspaceModeController {
    private val mutableEnabled = MutableStateFlow(loadInitialEnabled())
    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    fun isEnabled(): Boolean = mutableEnabled.value

    fun setEnabled(value: Boolean) {
        if (mutableEnabled.value == value) return
        mutableEnabled.value = value
        persist(value)
    }

    fun toggle() {
        setEnabled(!mutableEnabled.value)
    }

    private fun loadInitialEnabled(): Boolean {
        val context = AiLedgerApplication.contextOrNull() ?: return false
        return runCatching {
            context
                .getSharedPreferences(AGENT_WORKSPACE_PREFS, android.content.Context.MODE_PRIVATE)
                .getBoolean(KEY_WORKSPACE_MODE_ENABLED, false)
        }.getOrDefault(false)
    }

    private fun persist(value: Boolean) {
        val context = AiLedgerApplication.contextOrNull() ?: return
        runCatching {
            context
                .getSharedPreferences(AGENT_WORKSPACE_PREFS, android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_WORKSPACE_MODE_ENABLED, value)
                .apply()
        }
    }
}

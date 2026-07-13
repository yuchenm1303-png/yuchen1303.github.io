package com.yuchen.ailedger.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Agent O 悬浮对话的唯一开关状态。
 *
 * 它与 [AgentRuntimeController] 完全分离：Agent O 只控制普通聊天悬浮窗；带无限符号的
 * Agent 开关仍由 [AgentRuntimeController] 控制视觉智能体 HUD 与智能体交互浮窗。
 */
object AgentOFloatingChatController {
    private val mutableEnabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    fun isEnabled(): Boolean = mutableEnabled.value

    fun setEnabled(value: Boolean) {
        if (mutableEnabled.value != value) mutableEnabled.value = value
    }

    fun toggle() {
        setEnabled(!mutableEnabled.value)
    }
}

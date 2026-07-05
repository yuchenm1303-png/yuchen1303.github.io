package com.yuchen.ailedger.service

/**
 * 录制期间只临时启用动作事件作为时间锚点。
 *
 * 事件不会进入 Skill，不会保存节点、Resource ID 或选择器；结束后立即恢复 Idle。
 */
internal object VisualDemonstrationEventHints {
    fun begin(config: OperationRecordingConfig): Boolean =
        AiAgentAccessibilityService.beginOperationRecording(config)

    fun end(demonstrationId: String): Boolean =
        AiAgentAccessibilityService.endOperationRecording(demonstrationId)
}

package com.yuchen.ailedger.service

internal enum class VisualAgentCapsulePhase(
    val step: Int,
    val title: String,
) {
    Observe(1, "正在观察页面"),
    Analyze(2, "正在分析目标"),
    Move(3, "正在移动光标"),
    Tap(4, "正在执行点击"),
    Verify(5, "正在验证结果"),
}

internal enum class VisualAgentCapsuleMode {
    Running,
    UserTakeover,
    PendingInput,
    PendingConfirmation,
    Idle,
}

internal data class VisualAgentCapsulePresentation(
    val active: Boolean,
    val mode: VisualAgentCapsuleMode,
    val phase: VisualAgentCapsulePhase,
    val title: String,
    val meta: String,
    val canPauseOrResume: Boolean,
    val paused: Boolean,
    val showConversationInput: Boolean,
    val showSensitiveCompletion: Boolean,
    val showConfirmation: Boolean,
    val autoExpandKey: String?,
)

internal object VisualAgentCapsuleStateResolver {
    fun resolve(
        progress: AgentOverlayProgress,
        target: VisualAgentHudTarget?,
        nowMs: Long = System.currentTimeMillis(),
    ): VisualAgentCapsulePresentation {
        val matchingTarget = target?.takeIf { it.taskId == progress.taskId }
        val phase = resolvePhase(progress, matchingTarget, nowMs)
        val pendingInput = progress.pendingUserInput
        val pendingConfirmation = progress.pendingConfirmation
        val mode = when {
            pendingInput != null -> VisualAgentCapsuleMode.PendingInput
            pendingConfirmation != null -> VisualAgentCapsuleMode.PendingConfirmation
            progress.userTakeoverPaused -> VisualAgentCapsuleMode.UserTakeover
            progress.running -> VisualAgentCapsuleMode.Running
            else -> VisualAgentCapsuleMode.Idle
        }
        val active = progress.running ||
            pendingInput != null ||
            pendingConfirmation != null ||
            progress.userTakeoverPaused
        val title = when (mode) {
            VisualAgentCapsuleMode.PendingInput -> {
                if (pendingInput?.sensitive == true) "等待你完成隐私操作"
                else pendingInput?.title?.takeIf(String::isNotBlank) ?: "GUI Plus 等待回复"
            }
            VisualAgentCapsuleMode.PendingConfirmation -> "需要你的确认"
            VisualAgentCapsuleMode.UserTakeover -> "已暂停，等待你的操作"
            VisualAgentCapsuleMode.Running -> phase.title
            VisualAgentCapsuleMode.Idle -> progress.status.ifBlank { "视觉智能体待命" }
        }
        val meta = when (mode) {
            VisualAgentCapsuleMode.PendingInput -> if (pendingInput?.sensitive == true) "隐私接管" else "待回复"
            VisualAgentCapsuleMode.PendingConfirmation -> "待确认"
            VisualAgentCapsuleMode.UserTakeover -> "已暂停"
            VisualAgentCapsuleMode.Running -> "Step ${phase.step} / 5"
            VisualAgentCapsuleMode.Idle -> "待命"
        }
        val autoExpandKey = when (mode) {
            VisualAgentCapsuleMode.PendingInput -> pendingInput?.id?.let { "input:$it" }
            VisualAgentCapsuleMode.PendingConfirmation -> pendingConfirmation?.id?.let { "confirm:$it" }
            VisualAgentCapsuleMode.UserTakeover -> "pause:${progress.taskId}:${progress.logs.size}"
            else -> null
        }
        return VisualAgentCapsulePresentation(
            active = active,
            mode = mode,
            phase = phase,
            title = title,
            meta = meta,
            canPauseOrResume = progress.running && pendingInput == null && pendingConfirmation == null,
            paused = progress.userTakeoverPaused,
            showConversationInput = (pendingInput != null && !pendingInput.sensitive) || progress.userTakeoverPaused,
            showSensitiveCompletion = pendingInput?.sensitive == true,
            showConfirmation = pendingConfirmation != null,
            autoExpandKey = autoExpandKey,
        )
    }

    private fun resolvePhase(
        progress: AgentOverlayProgress,
        target: VisualAgentHudTarget?,
        nowMs: Long,
    ): VisualAgentCapsulePhase {
        val lastLog = progress.logs.lastOrNull().orEmpty()
        if (progress.pendingConfirmation != null ||
            progress.pendingUserInput != null ||
            progress.userTakeoverPaused
        ) return VisualAgentCapsulePhase.Analyze
        if (!progress.running) return VisualAgentCapsulePhase.Verify
        if (lastLog.startsWith("结果：") || progress.status == "重新规划") {
            return VisualAgentCapsulePhase.Verify
        }
        if (target?.positioned == true &&
            target.actionType in setOf("tap_xy", "tap_node") &&
            nowMs - target.plannedAt <= TARGET_MOVE_VISIBLE_MS
        ) return VisualAgentCapsulePhase.Move
        if (lastLog == progress.currentAction && progress.currentAction.isNotBlank()) {
            return VisualAgentCapsulePhase.Tap
        }
        if (lastLog.startsWith("模型") ||
            progress.lastResult.contains("分析") ||
            progress.lastResult.contains("GUI Plus") ||
            progress.lastResult.contains("VisualDirect")
        ) return VisualAgentCapsulePhase.Analyze
        return VisualAgentCapsulePhase.Observe
    }

    private const val TARGET_MOVE_VISIBLE_MS = 1_200L
}

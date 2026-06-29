package com.yuchen.ailedger.service

/**
 * Keeps user guidance entered while the visual loop is paused for manual takeover.
 *
 * Every accepted directive is also written to the authoritative visual task revision queue. The
 * dialogue turn remains the verbatim user message, while the revision queue carries invalidation and
 * priority metadata without asking Android to invent page semantics.
 */
internal object AgentTakeoverDialogueBridge {
    private const val MAX_MESSAGES = 8
    private const val MAX_TEXT_CHARS = 1_000
    private const val LATEST_DIRECTIVE_PREFIX =
        "[LATEST_USER_DIRECTIVE][OVERRIDES_CONFLICTING_PRIOR_PLAN][REPLAN_FROM_CURRENT_SCREEN] "
    private const val USER_DIRECTIVE_REPLAN_SIGNAL =
        "visual_replan_requested:reason=user_instruction|semanticStatus=user_directive|priority=highest|overrideConflictingPlan=true|replanRequired=true"

    private val lock = Any()
    private var taskId: Long = 0L
    private val messages = ArrayDeque<String>()

    fun submit(text: String): Boolean {
        val clean = text.trim().take(MAX_TEXT_CHARS)
        val progress = AgentRuntimeController.progress.value
        if (clean.isBlank() || !progress.running || !progress.userTakeoverPaused || progress.taskId <= 0L) {
            return false
        }
        synchronized(lock) {
            alignTaskLocked(progress.taskId)
            messages.addLast(clean)
            while (messages.size > MAX_MESSAGES) messages.removeFirst()
        }
        VisualUserTaskUpdateRuntime.record(
            rawReply = clean,
            sourceReason = "manual_takeover_directive",
            prompt = progress.lastResult.ifBlank { progress.currentAction },
        )
        AgentRuntimeController.noteDiagnostic("已记录用户接管指令；恢复后将要求 GUI Plus 放弃冲突旧计划并重新规划")
        return true
    }

    fun interactionActions(): List<String> {
        val currentTaskId = AgentRuntimeController.currentTaskId()
        if (currentTaskId <= 0L) {
            synchronized(lock) {
                taskId = 0L
                messages.clear()
            }
            return emptyList()
        }
        return synchronized(lock) {
            alignTaskLocked(currentTaskId)
            encodeInteractionActions(messages.toList())
        }
    }

    internal fun encodeInteractionActions(rawMessages: List<String>): List<String> {
        val cleanMessages = rawMessages
            .map { it.trim().take(MAX_TEXT_CHARS) }
            .filter(String::isNotBlank)
            .takeLast(MAX_MESSAGES)
        if (cleanMessages.isEmpty()) return emptyList()

        return buildList {
            cleanMessages.forEachIndexed { index, message ->
                val content = if (index == cleanMessages.lastIndex) {
                    LATEST_DIRECTIVE_PREFIX + message
                } else {
                    message
                }
                add("userInstruction:$content")
            }
            add(USER_DIRECTIVE_REPLAN_SIGNAL)
        }
    }

    private fun alignTaskLocked(currentTaskId: Long) {
        if (taskId != currentTaskId) {
            taskId = currentTaskId
            messages.clear()
        }
    }
}

package com.yuchen.ailedger.service

import org.json.JSONObject

enum class VisualUserTaskUpdateKind(val wireValue: String) {
    Supplement("supplement"),
    Correction("correction"),
    GoalRevision("goal_revision"),
    CancelSubgoal("cancel_subgoal"),
    ManualStepCompleted("manual_step_completed"),
}

data class VisualUserTaskUpdate(
    val revision: Int = 0,
    val kind: VisualUserTaskUpdateKind,
    val content: String,
    val sourceReason: String = "",
    val replyToPrompt: String = "",
    val invalidatesCurrentMilestone: Boolean = false,
    val invalidatesVisualHistory: Boolean = false,
    val manualStepCompleted: Boolean = false,
) {
    val isDirective: Boolean
        get() = kind == VisualUserTaskUpdateKind.Correction ||
            kind == VisualUserTaskUpdateKind.GoalRevision ||
            kind == VisualUserTaskUpdateKind.CancelSubgoal

    fun toJson(): JSONObject = JSONObject().apply {
        put("revision", revision)
        put("kind", kind.wireValue)
        put("content", content)
        put("sourceReason", sourceReason)
        put("replyToPrompt", replyToPrompt)
        put("invalidatesCurrentMilestone", invalidatesCurrentMilestone)
        put("invalidatesVisualHistory", invalidatesVisualHistory)
        put("manualStepCompleted", manualStepCompleted)
    }

    fun toPromptLine(): String = buildString {
        append("visual_replan_requested:reason=user_task_revision")
        append("|schema=visual_task_revision_v1")
        append("|taskRevision=").append(revision)
        append("|kind=").append(kind.wireValue)
        append("|invalidateCurrentMilestone=").append(invalidatesCurrentMilestone)
        append("|invalidateVisualHistory=").append(invalidatesVisualHistory)
        append("|manualStepCompleted=").append(manualStepCompleted)
        append("|latestUserTurnAuthoritative=true")
        append("|completionCandidateInvalidated=true")
        append("|semanticStatus=user_revision")
        append("|priority=highest")
        append("|replanRequired=true")
    }
}

internal object VisualUserTaskUpdateRuntime {
    private const val MAX_UPDATES = 12
    private val lock = Any()
    private var taskId: Long = 0L
    private var revision: Int = 0
    private var dispatchedRevision: Int = 0
    private val updates = ArrayDeque<VisualUserTaskUpdate>()

    fun record(
        rawReply: String?,
        sourceReason: String,
        prompt: String,
    ): VisualUserTaskUpdate? {
        val currentTaskId = currentTaskIdOrZero()
        if (currentTaskId <= 0L) return null
        val classified = VisualUserTaskUpdateClassifier.classify(
            rawReply = rawReply.orEmpty(),
            sourceReason = sourceReason,
            prompt = prompt,
        ) ?: return null
        return synchronized(lock) {
            alignTaskLocked(currentTaskId)
            val existing = updates.lastOrNull { previous ->
                previous.kind == classified.kind &&
                    previous.content == classified.content &&
                    previous.sourceReason == classified.sourceReason &&
                    previous.replyToPrompt == classified.replyToPrompt
            }
            if (existing != null) return@synchronized existing
            val applied = classified.copy(revision = revision + 1)
            revision = applied.revision
            updates.addLast(applied)
            while (updates.size > MAX_UPDATES) updates.removeFirst()
            applied
        }
    }

    fun updatesAfter(lastAppliedRevision: Int): List<VisualUserTaskUpdate> {
        val currentTaskId = currentTaskIdOrZero()
        if (currentTaskId <= 0L) return emptyList()
        return synchronized(lock) {
            if (taskId != currentTaskId) return@synchronized emptyList()
            updates.filter { it.revision > lastAppliedRevision }
        }
    }

    fun takeUndispatchedPromptLines(): List<String> {
        val currentTaskId = currentTaskIdOrZero()
        if (currentTaskId <= 0L) return emptyList()
        return synchronized(lock) {
            if (taskId != currentTaskId) return@synchronized emptyList()
            val pending = updates.filter { it.revision > dispatchedRevision }
            if (pending.isEmpty()) return@synchronized emptyList()
            dispatchedRevision = pending.maxOf { it.revision }
            pending.takeLast(4).map(VisualUserTaskUpdate::toPromptLine)
        }
    }

    fun currentRevision(): Int {
        val currentTaskId = currentTaskIdOrZero()
        if (currentTaskId <= 0L) return 0
        return synchronized(lock) {
            if (taskId == currentTaskId) revision else 0
        }
    }

    fun latestDispatchedRevision(): Int {
        val currentTaskId = currentTaskIdOrZero()
        if (currentTaskId <= 0L) return 0
        return synchronized(lock) {
            if (taskId == currentTaskId) dispatchedRevision else 0
        }
    }

    internal fun resetForTests() {
        synchronized(lock) {
            taskId = 0L
            revision = 0
            dispatchedRevision = 0
            updates.clear()
        }
    }

    private fun currentTaskIdOrZero(): Long =
        runCatching { AgentRuntimeController.currentTaskId() }.getOrDefault(0L)

    private fun alignTaskLocked(currentTaskId: Long) {
        if (taskId != currentTaskId) {
            taskId = currentTaskId
            revision = 0
            dispatchedRevision = 0
            updates.clear()
        }
    }
}

internal object VisualUserTaskUpdateClassifier {
    fun classify(
        rawReply: String,
        sourceReason: String,
        prompt: String,
    ): VisualUserTaskUpdate? {
        val raw = rawReply.trim()
        if (raw.isBlank()) return null
        if (raw == VisualLoopSupport.PRIVATE_COMPLETION_TOKEN || raw == SAFE_MANUAL_COMPLETION_TEXT) {
            return VisualUserTaskUpdate(
                kind = VisualUserTaskUpdateKind.ManualStepCompleted,
                content = SAFE_MANUAL_COMPLETION_TEXT,
                sourceReason = sourceReason.take(MAX_REASON_CHARS),
                replyToPrompt = prompt.take(MAX_PROMPT_CHARS),
                manualStepCompleted = true,
            )
        }

        val content = raw.take(VisualLoopSupport.MAX_INTERACTION_TEXT_CHARS)
        val compact = content.lowercase().replace(" ", "")
        val kind = when {
            CANCEL_MARKERS.any(compact::contains) -> VisualUserTaskUpdateKind.CancelSubgoal
            GOAL_REVISION_MARKERS.any(compact::contains) -> VisualUserTaskUpdateKind.GoalRevision
            CORRECTION_PREFIXES.any(compact::startsWith) || CORRECTION_MARKERS.any(compact::contains) ->
                VisualUserTaskUpdateKind.Correction
            else -> VisualUserTaskUpdateKind.Supplement
        }
        val invalidatesCurrentMilestone = kind in setOf(
            VisualUserTaskUpdateKind.Correction,
            VisualUserTaskUpdateKind.GoalRevision,
            VisualUserTaskUpdateKind.CancelSubgoal,
        )
        return VisualUserTaskUpdate(
            kind = kind,
            content = content,
            sourceReason = sourceReason.take(MAX_REASON_CHARS),
            replyToPrompt = prompt.take(MAX_PROMPT_CHARS),
            invalidatesCurrentMilestone = invalidatesCurrentMilestone,
            invalidatesVisualHistory = invalidatesCurrentMilestone,
        )
    }

    private val CANCEL_MARKERS = listOf(
        "取消当前步骤",
        "取消这一步",
        "取消这个步骤",
        "取消当前子任务",
        "取消这个子任务",
        "跳过当前步骤",
        "跳过这一步",
        "跳过这个步骤",
        "不要再做这一步",
        "停止当前步骤",
    )
    private val GOAL_REVISION_MARKERS = listOf(
        "目标改为",
        "任务改为",
        "改成",
        "改为",
        "换成",
        "改做",
        "接下来改",
    )
    private val CORRECTION_PREFIXES = listOf("不是", "不对", "错了", "应该", "应当")
    private val CORRECTION_MARKERS = listOf("我说的是", "不是这个", "理解错了", "目标不对")
    private const val SAFE_MANUAL_COMPLETION_TEXT = "[用户已完成手动步骤]"
    private const val MAX_REASON_CHARS = 120
    private const val MAX_PROMPT_CHARS = 320
}

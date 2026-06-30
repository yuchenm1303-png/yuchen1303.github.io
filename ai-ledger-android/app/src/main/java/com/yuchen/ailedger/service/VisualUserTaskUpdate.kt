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
        append("visual_task_revision:v2")
        append("|taskRevision=").append(revision)
        append("|kind=").append(kind.wireValue)
        append("|manualStepCompleted=").append(manualStepCompleted)
        append("|latestUserTurnAuthoritative=true")
        append("|completionCandidateInvalidated=true")
        append("|replanRequired=true")
    }
}

/**
 * Task-scoped user-turn ledger. Android preserves the user's words and ordering but never classifies
 * their semantic intent. GUI Plus/DeepSeek decide whether the update is a correction, replacement or
 * supplement from the actual conversation content.
 */
internal object VisualUserTaskUpdateRuntime {
    private const val MAX_UPDATES = 12
    private val lock = Any()
    private var taskId: Long = 0L
    private var revision: Int = 0
    private var dispatchedRevision: Int = 0
    private var acceptedRevision: Int = 0
    private val updates = ArrayDeque<VisualUserTaskUpdate>()

    fun record(
        rawReply: String?,
        sourceReason: String,
        prompt: String,
    ): VisualUserTaskUpdate? {
        val currentTaskId = currentTaskIdOrZero()
        if (currentTaskId <= 0L) return null
        val update = VisualUserTaskUpdateClassifier.classify(
            rawReply = rawReply.orEmpty(),
            sourceReason = sourceReason,
            prompt = prompt,
        ) ?: return null
        return synchronized(lock) {
            alignTaskLocked(currentTaskId)
            val existing = updates.lastOrNull { previous ->
                previous.content == update.content &&
                    previous.sourceReason == update.sourceReason &&
                    previous.replyToPrompt == update.replyToPrompt
            }
            if (existing != null) return@synchronized existing
            val applied = update.copy(revision = revision + 1)
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
            pending.takeLast(2).map(VisualUserTaskUpdate::toPromptLine)
        }
    }

    fun hasUndispatchedRevision(): Boolean {
        val currentTaskId = currentTaskIdOrZero()
        if (currentTaskId <= 0L) return false
        return synchronized(lock) {
            taskId == currentTaskId && revision > dispatchedRevision
        }
    }

    fun markDispatchedPlanValidated() {
        val currentTaskId = currentTaskIdOrZero()
        if (currentTaskId <= 0L) return
        synchronized(lock) {
            if (taskId == currentTaskId) acceptedRevision = maxOf(acceptedRevision, dispatchedRevision)
        }
    }

    fun isRevisionPending(value: Int): Boolean {
        if (value <= 0) return false
        val currentTaskId = currentTaskIdOrZero()
        if (currentTaskId <= 0L) return false
        return synchronized(lock) {
            taskId == currentTaskId && acceptedRevision < value
        }
    }

    fun currentRevision(): Int {
        val currentTaskId = currentTaskIdOrZero()
        if (currentTaskId <= 0L) return 0
        return synchronized(lock) { if (taskId == currentTaskId) revision else 0 }
    }

    fun latestDispatchedRevision(): Int {
        val currentTaskId = currentTaskIdOrZero()
        if (currentTaskId <= 0L) return 0
        return synchronized(lock) { if (taskId == currentTaskId) dispatchedRevision else 0 }
    }

    internal fun resetForTests() {
        synchronized(lock) {
            taskId = 0L
            revision = 0
            dispatchedRevision = 0
            acceptedRevision = 0
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
            acceptedRevision = 0
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
        return VisualUserTaskUpdate(
            kind = VisualUserTaskUpdateKind.Supplement,
            content = raw.take(VisualLoopSupport.MAX_INTERACTION_TEXT_CHARS),
            sourceReason = sourceReason.take(MAX_REASON_CHARS),
            replyToPrompt = prompt.take(MAX_PROMPT_CHARS),
        )
    }

    private const val SAFE_MANUAL_COMPLETION_TEXT = "[用户已完成手动步骤]"
    private const val MAX_REASON_CHARS = 120
    private const val MAX_PROMPT_CHARS = 320
}

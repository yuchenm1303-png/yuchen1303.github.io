package com.yuchen.ailedger.service

/**
 * Keeps user guidance entered while the visual loop is paused for manual takeover.
 * The next GUI Plus planning request receives the guidance through VisualLoopSupport.
 */
internal object AgentTakeoverDialogueBridge {
    private const val MAX_MESSAGES = 8
    private const val MAX_TEXT_CHARS = 1_000

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
            messages.map { "userTakeoverGuidance:$it" }
        }
    }

    private fun alignTaskLocked(currentTaskId: Long) {
        if (taskId != currentTaskId) {
            taskId = currentTaskId
            messages.clear()
        }
    }
}

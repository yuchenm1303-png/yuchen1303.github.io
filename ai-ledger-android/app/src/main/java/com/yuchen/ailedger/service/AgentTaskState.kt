package com.yuchen.ailedger.service

enum class AgentTaskPhase(val label: String) {
    Idle("空闲"),
    Planning("规划中"),
    WaitingForUserConfirmation("等待确认"),
    Executing("执行中"),
    Observing("观察中"),
    Paused("已暂停"),
    Finished("已完成"),
    Failed("失败"),
}

data class AgentTaskEvent(
    val title: String,
    val detail: String,
    val createdAt: Long = System.currentTimeMillis(),
)

data class AgentTaskState(
    val id: String = "",
    val goal: String = "",
    val phase: AgentTaskPhase = AgentTaskPhase.Idle,
    val currentApp: String = "",
    val snapshotNodeCount: Int = 0,
    val stepIndex: Int = 0,
    val suggestedStep: CloudAgentStep? = null,
    val events: List<AgentTaskEvent> = emptyList(),
    val errorText: String? = null,
) {
    val isActive: Boolean
        get() = phase !in setOf(AgentTaskPhase.Idle, AgentTaskPhase.Finished, AgentTaskPhase.Failed)

    fun appendEvent(title: String, detail: String): AgentTaskState {
        return copy(events = (events + AgentTaskEvent(title = title, detail = detail)).takeLast(12))
    }
}

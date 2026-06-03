package com.yuchen.ailedger.service

import java.util.concurrent.atomic.AtomicLong

class AgentTaskController {
    private val idSeed = AtomicLong(System.currentTimeMillis())
    var state: AgentTaskState = AgentTaskState()
        private set

    fun start(goal: String): AgentTaskState {
        val cleanGoal = goal.trim().take(240)
        state = AgentTaskState(
            id = "agent-task-${idSeed.incrementAndGet()}",
            goal = cleanGoal,
            phase = AgentTaskPhase.Planning,
        ).appendEvent("创建任务", cleanGoal)
        return state
    }

    fun markPlanning(snapshot: AgentScreenSnapshot): AgentTaskState {
        state = state.copy(
            phase = AgentTaskPhase.Planning,
            currentApp = snapshot.currentApp,
            snapshotNodeCount = snapshot.nodeCount,
        ).appendEvent("观察屏幕", "${snapshot.currentApp} · ${snapshot.nodeCount} 个节点")
        return state
    }

    fun acceptStep(step: CloudAgentStep): AgentTaskState {
        val needsConfirmation = AgentSafetyPolicy.requiresConfirmation(state.goal, step)
        val nextPhase = when {
            step.type == "finish" -> AgentTaskPhase.Finished
            step.type == "need_user_help" -> AgentTaskPhase.WaitingForUserConfirmation
            needsConfirmation -> AgentTaskPhase.WaitingForUserConfirmation
            AgentSafetyPolicy.canAutoExecuteInCurrentStage(state.goal, step) -> AgentTaskPhase.Executing
            else -> AgentTaskPhase.WaitingForUserConfirmation
        }
        val detail = buildString {
            append(step.typeLabel)
            step.targetNodeId?.let { append(" · 节点 $it") }
            step.targetText?.let { append(" · $it") }
            step.direction?.let { append(" · $it") }
            step.reason?.let { append(" · $it") }
        }
        state = state.copy(
            phase = nextPhase,
            stepIndex = state.stepIndex + 1,
            suggestedStep = step,
            errorText = null,
        ).appendEvent("建议第 ${state.stepIndex + 1} 步", detail)
        return state
    }

    fun pause(reason: String = "用户暂停任务"): AgentTaskState {
        state = state.copy(phase = AgentTaskPhase.Paused).appendEvent("暂停任务", reason)
        return state
    }

    fun fail(message: String): AgentTaskState {
        state = state.copy(phase = AgentTaskPhase.Failed, errorText = message).appendEvent("任务失败", message)
        return state
    }

    fun reset(): AgentTaskState {
        state = AgentTaskState()
        return state
    }
}

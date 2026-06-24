package com.yuchen.ailedger.service

/**
 * Distinguishes a recoverable GUI focus miss from a real user-information request.
 *
 * focused_direct means GUI Plus already owns the verified work surface and expects Android to
 * reuse the current input focus. If that transient focus disappears, the visual loop must receive
 * a retry signal and re-observe/re-click the field instead of opening the user dialogue panel.
 */
internal object AgentInputRecoveryPolicy {
    fun onInputFailure(step: CloudAgentStep, candidateCount: Int): AgentExecutionResult {
        val detail = if (candidateCount > 0) {
            "候选输入节点存在，但 SET_TEXT 与剪贴板粘贴均未生效"
        } else {
            "当前观察帧没有暴露可写焦点或输入节点"
        }

        if (step.shouldUseFocusedDirectInput) {
            return AgentExecutionResult(
                ok = true,
                message = buildString {
                    append("visual_action_retry:type=input_text")
                    append("|failureClass=visual_local")
                    append("|reason=focused_input_unavailable:")
                    append(detail)
                    append("；请 GUI Plus 在新观察帧中重新点击输入区域后再输入")
                    append("|replanRequired=false")
                },
                shouldContinue = true,
            )
        }

        return AgentExecutionResult(
            ok = false,
            message = detail,
            shouldContinue = false,
        )
    }
}

package com.yuchen.ailedger.service

object AgentSafetyPolicy {
    private val executableStepTypes = setOf(
        "open_app",
        "home",
        "back",
        "recents",
        "tap_node",
        "tap_xy",
        "input_text",
        "scroll",
        "swipe",
        "wait",
    )

    private val passiveStepTypes = setOf(
        "back",
        "home",
        "recents",
        "notifications",
        "quick_settings",
        "scroll",
        "swipe",
        "wait",
    )

    private val foregroundStepTypes = setOf(
        "tap_node",
        "tap_xy",
        "input_text",
    )

    fun requiresConfirmation(goal: String, step: CloudAgentStep): Boolean {
        if (step.type in passiveStepTypes && !step.requiresConfirmation) return false
        if (step.type !in foregroundStepTypes) return false
        if (requiresUserProvidedInput(goal, step)) return false
        val level = step.riskLevel.normalizedPolicyLevel()
        return step.requiresConfirmation || (level.isNotBlank() && level != "low")
    }

    fun canAutoExecuteInCurrentStage(goal: String, step: CloudAgentStep): Boolean {
        if (requiresConfirmation(goal, step)) return false
        if (requiresUserProvidedInput(goal, step)) return false
        return step.type in executableStepTypes
    }

    fun requiresUserProvidedInput(goal: String, step: CloudAgentStep): Boolean {
        val level = step.riskLevel.normalizedPolicyLevel()
        return step.type == "need_user_help" || level.endsWith("_input")
    }

    private fun String?.normalizedPolicyLevel(): String {
        return orEmpty()
            .trim()
            .lowercase()
            .replace('-', '_')
            .replace(' ', '_')
    }
}

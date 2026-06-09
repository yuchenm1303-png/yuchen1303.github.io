package com.yuchen.ailedger.service

object AgentSafetyPolicy {
    private val deviceToolStepTypes = CloudAgentStep.deviceToolTypes

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
    ) + deviceToolStepTypes

    private val passiveStepTypes = setOf(
        "back",
        "home",
        "recents",
        "notifications",
        "quick_settings",
        "scroll",
        "swipe",
        "wait",
        "device_status",
        "shizuku_status",
    )

    private val foregroundStepTypes = setOf(
        "tap_node",
        "tap_xy",
        "input_text",
    )

    fun requiresConfirmation(goal: String, step: CloudAgentStep): Boolean {
        if (requiresUserProvidedInput(goal, step)) return false
        val level = step.riskLevel.normalizedPolicyLevel()
        val highRisk = level == "high" || level == "critical"
        if (step.requiresConfirmation || highRisk) return true
        if (step.type in passiveStepTypes) return false
        if (step.type in deviceToolStepTypes) return false
        if (step.type !in foregroundStepTypes) return false
        return level.isNotBlank() && level != "low"
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

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

    private val confirmationProtectedDeviceTools = setOf(
        "set_animation_scale",
        "force_stop_app",
        "clear_app_data",
        "uninstall_app",
        "disable_app",
        "enable_app",
    )

    /**
     * Android does not infer semantic risk from goal text, target labels, coordinates or riskLevel
     * wording. The cloud semantic owner must explicitly set requiresConfirmation=true for a
     * consequential action; Android then enforces that confirmation and cannot silently bypass it.
     */
    @Suppress("UNUSED_PARAMETER")
    fun requiresConfirmation(goal: String, step: CloudAgentStep): Boolean {
        return step.requiresConfirmation || step.type in confirmationProtectedDeviceTools
    }

    fun canAutoExecuteInCurrentStage(goal: String, step: CloudAgentStep): Boolean {
        if (requiresConfirmation(goal, step)) return false
        if (requiresUserProvidedInput(goal, step)) return false
        return step.type in executableStepTypes
    }

    @Suppress("UNUSED_PARAMETER")
    fun requiresUserProvidedInput(goal: String, step: CloudAgentStep): Boolean {
        val level = step.riskLevel.normalizedPolicyLevel()
        return level.endsWith("_input") || level == "sensitive" || level == "private"
    }

    private fun String?.normalizedPolicyLevel(): String {
        return orEmpty()
            .trim()
            .lowercase()
            .replace('-', '_')
            .replace(' ', '_')
    }
}

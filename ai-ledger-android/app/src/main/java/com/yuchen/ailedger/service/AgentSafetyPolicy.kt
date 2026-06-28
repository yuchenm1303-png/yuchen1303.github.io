package com.yuchen.ailedger.service

import org.json.JSONObject

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

    /**
     * Android does not infer semantic risk from goal text, target labels, coordinates or riskLevel
     * wording. The cloud semantic owner must explicitly set requiresConfirmation=true for a
     * consequential action; Android then enforces that confirmation and cannot silently bypass it.
     */
    @Suppress("UNUSED_PARAMETER")
    fun requiresConfirmation(goal: String, step: CloudAgentStep): Boolean {
        val required = requiresConfirmationValue(step)
        recordDecision(
            stage = "confirmation_gate",
            step = step,
            requiresConfirmation = required,
            requiresUserInput = requiresUserProvidedInputValue(step),
            executableType = step.type in executableStepTypes,
            canAutoExecute = !required && !requiresUserProvidedInputValue(step) && step.type in executableStepTypes,
        )
        return required
    }

    fun canAutoExecuteInCurrentStage(goal: String, step: CloudAgentStep): Boolean {
        val requiresConfirmation = requiresConfirmationValue(step)
        val requiresUserInput = requiresUserProvidedInputValue(step)
        val executableType = step.type in executableStepTypes
        val canAutoExecute = !requiresConfirmation && !requiresUserInput && executableType
        recordDecision(
            stage = "auto_execute_gate",
            step = step,
            requiresConfirmation = requiresConfirmation,
            requiresUserInput = requiresUserInput,
            executableType = executableType,
            canAutoExecute = canAutoExecute,
        )
        return canAutoExecute
    }

    @Suppress("UNUSED_PARAMETER")
    fun requiresUserProvidedInput(goal: String, step: CloudAgentStep): Boolean {
        val required = requiresUserProvidedInputValue(step)
        recordDecision(
            stage = "user_input_gate",
            step = step,
            requiresConfirmation = requiresConfirmationValue(step),
            requiresUserInput = required,
            executableType = step.type in executableStepTypes,
            canAutoExecute = !required && !requiresConfirmationValue(step) && step.type in executableStepTypes,
        )
        return required
    }

    private fun requiresConfirmationValue(step: CloudAgentStep): Boolean {
        return if (step.type in deviceToolStepTypes) {
            DeviceControlSpecs.requiresConfirmation(step)
        } else {
            step.requiresConfirmation
        }
    }

    private fun requiresUserProvidedInputValue(step: CloudAgentStep): Boolean {
        val level = step.riskLevel.normalizedPolicyLevel()
        return level.endsWith("_input") || level == "sensitive" || level == "private"
    }

    private fun recordDecision(
        stage: String,
        step: CloudAgentStep,
        requiresConfirmation: Boolean,
        requiresUserInput: Boolean,
        executableType: Boolean,
        canAutoExecute: Boolean,
    ) {
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "safety_policy",
            details = JSONObject().apply {
                put("stage", stage)
                put("stepType", step.type)
                put("targetText", step.targetText ?: JSONObject.NULL)
                put("packageName", step.packageName ?: JSONObject.NULL)
                put("riskLevel", step.riskLevel)
                put("cloudRequiresConfirmation", step.requiresConfirmation)
                put("requiresConfirmation", requiresConfirmation)
                put("requiresUserInput", requiresUserInput)
                put("executableType", executableType)
                put("canAutoExecute", canAutoExecute)
                put("reason", step.reason ?: JSONObject.NULL)
                put("toolArgs", if (step.type == "input_text") "[输入参数已隐藏]" else step.toolArgs ?: JSONObject.NULL)
            },
        )
    }

    private fun String?.normalizedPolicyLevel(): String {
        return orEmpty()
            .trim()
            .lowercase()
            .replace('-', '_')
            .replace(' ', '_')
    }
}

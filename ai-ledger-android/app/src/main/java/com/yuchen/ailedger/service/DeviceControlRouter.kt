package com.yuchen.ailedger.service

import org.json.JSONObject

/**
 * Pure structured-tool protocol boundary between the cloud Final Chat Model and Android.
 *
 * Android never reads user wording here. It accepts an exact declared tool name, copies only that
 * tool's canonical arguments, and forwards the typed command to the matching transaction executor.
 */
object DeviceControlRouter {
    fun fromAgentActionJson(agentAction: JSONObject?): CloudAgentStep? {
        if (agentAction == null) return null
        val source = listOf(
            "deviceControlAction",
            "device_control_action",
            "agentStep",
            "step",
        ).firstNotNullOfOrNull(agentAction::optJSONObject) ?: agentAction
        return fromDeviceControlJson(source, fallbackReason = agentAction.canonicalString("reason"))
    }

    fun fromClientToolCall(toolCall: JSONObject?): CloudAgentStep? {
        if (toolCall == null) return null
        if (toolCall.canonicalString("schema") != AI_WORKER_CLIENT_TOOL_CALL_SCHEMA) return null
        val name = toolCall.canonicalString("name")
        val arguments = toolCall.optJSONObject("arguments") ?: JSONObject()
        val step = when {
            name in CloudAgentStep.ledgerToolTypes -> fromCanonicalTool(
                tool = name,
                arguments = arguments,
                reason = toolCall.canonicalString("reason"),
                riskLevel = toolCall.canonicalString("riskLevel").ifBlank { "low" },
                requiresConfirmation = toolCall.canonicalBoolean("requiresConfirmation") ?: false,
            )
            name == "device_control" -> {
                val action = arguments.canonicalString("action")
                val args = arguments.optJSONObject("args") ?: JSONObject()
                fromCanonicalTool(
                    tool = action,
                    arguments = args,
                    reason = toolCall.canonicalString("reason"),
                    riskLevel = toolCall.canonicalString("riskLevel"),
                    requiresConfirmation = toolCall.canonicalBoolean("requiresConfirmation"),
                )
            }
            else -> null
        } ?: return null
        val callId = toolCall.canonicalString("id")
        if (callId.isNotBlank()) {
            ClientToolCallRegistry.attach(
                step,
                CloudClientToolCall(
                    schema = AI_WORKER_CLIENT_TOOL_CALL_SCHEMA,
                    id = callId,
                    name = name,
                    arguments = JSONObject(arguments.toString()),
                    resultProtocol = toolCall.canonicalString("resultProtocol")
                        .ifBlank { AI_WORKER_CLIENT_TOOL_RESULT_PROTOCOL },
                    riskLevel = toolCall.canonicalString("riskLevel").ifBlank { "low" },
                    requiresConfirmation = toolCall.canonicalBoolean("requiresConfirmation") ?: false,
                    reason = toolCall.canonicalString("reason").ifBlank { null },
                    originalUserGoal = toolCall.canonicalString("originalUserGoal").ifBlank { null },
                    finalModel = toolCall.canonicalString("finalModel").ifBlank { null },
                ),
            )
        }
        return step
    }

    fun fromDeviceControlJson(raw: JSONObject?, fallbackReason: String? = null): CloudAgentStep? {
        if (raw == null) return null
        val declaredTool = firstCanonicalString(raw, "tool", "capability", "type", "action")
            ?: return null
        val stepType = normalizeDeclaredTool(declaredTool) ?: return null
        val nestedArgs = raw.optJSONObject("args")
            ?: raw.optJSONObject("arguments")
            ?: raw.optJSONObject("params")
            ?: JSONObject()
        val allowed = allowedArgNames(stepType) ?: return null
        val args = sanitizeCanonicalArgs(raw, nestedArgs, allowed)
        return buildStep(
            stepType = stepType,
            raw = raw,
            args = args,
            fallbackReason = fallbackReason,
        )
    }

    fun supportedCapabilities(): List<String> = DeviceControlSpecs.supportedCapabilities()

    fun normalChatSupportedCapabilities(): List<String> = DeviceControlSpecs.normalChatSupportedCapabilities()

    fun normalChatSupportedStepTypes(): List<String> =
        (DeviceControlSpecs.normalChatSupportedStepTypes() + CloudAgentStep.ledgerToolTypes)
            .distinct()
            .sorted()

    private fun fromCanonicalTool(
        tool: String,
        arguments: JSONObject,
        reason: String,
        riskLevel: String = "",
        requiresConfirmation: Boolean? = null,
    ): CloudAgentStep? {
        val stepType = normalizeDeclaredTool(tool) ?: return null
        val allowed = allowedArgNames(stepType) ?: return null
        if (arguments.keys().asSequence().any { it !in allowed }) return null
        val raw = JSONObject().apply {
            put("tool", stepType)
            put("args", arguments)
            if (reason.isNotBlank()) put("reason", reason)
            if (riskLevel.isNotBlank()) put("riskLevel", riskLevel)
            requiresConfirmation?.let { put("requiresConfirmation", it) }
        }
        return buildStep(stepType, raw, JSONObject(arguments.toString()), reason)
    }

    private fun buildStep(
        stepType: String,
        raw: JSONObject,
        args: JSONObject,
        fallbackReason: String?,
    ): CloudAgentStep {
        val spec = DeviceControlSpecs.specFor(stepType)
        val topLevelPackage = raw.canonicalString("packageName")
        val argsPackage = args.canonicalString("packageName")
        val packageName = topLevelPackage.ifBlank { argsPackage }.ifBlank { null }
        val defaultRisk = spec?.riskLevel?.name?.lowercase() ?: "low"
        val defaultConfirmation = spec?.requiresConfirmation ?: false
        return CloudAgentStep(
            type = stepType,
            targetText = raw.canonicalString("targetText").ifBlank { null },
            text = raw.canonicalString("text").ifBlank { null },
            reason = raw.canonicalString("reason").ifBlank {
                fallbackReason?.trim().orEmpty()
            }.ifBlank { null },
            riskLevel = raw.canonicalString("riskLevel")
                .ifBlank { defaultRisk }
                .lowercase()
                .replace('-', '_'),
            requiresConfirmation = raw.canonicalBoolean("requiresConfirmation")
                ?: defaultConfirmation,
            appName = raw.canonicalString("appName").ifBlank { null },
            packageName = packageName,
            toolArgs = args.takeIf { it.length() > 0 },
        )
    }

    private fun allowedArgNames(stepType: String): Set<String>? {
        DeviceControlSpecs.specFor(stepType)?.let { return it.allowedArgNames }
        return LedgerInternalToolExecutor.allowedArgNames(stepType)
    }

    private fun normalizeDeclaredTool(raw: String): String? {
        val exact = raw.trim().lowercase().replace('-', '_')
        if (exact in CloudAgentStep.ledgerToolTypes) return exact
        DeviceControlSpecs.normalizeCapability(raw)?.let { return it }
        return exact.takeIf { DeviceControlSpecs.specFor(it) != null }
    }

    private fun sanitizeCanonicalArgs(
        raw: JSONObject,
        nestedArgs: JSONObject,
        allowedArgNames: Set<String>,
    ): JSONObject {
        val clean = JSONObject()
        nestedArgs.keys().asSequence().forEach { key ->
            if (key in allowedArgNames && nestedArgs.has(key) && !nestedArgs.isNull(key)) {
                clean.put(key, nestedArgs.opt(key))
            }
        }
        allowedArgNames.forEach { key ->
            if (!clean.has(key) && raw.has(key) && !raw.isNull(key)) {
                clean.put(key, raw.opt(key))
            }
        }
        return clean
    }

    private fun firstCanonicalString(source: JSONObject, vararg names: String): String? {
        names.forEach { name ->
            source.canonicalString(name).takeIf(String::isNotBlank)?.let { return it }
        }
        return null
    }
}

private fun JSONObject.canonicalString(name: String): String =
    (opt(name) as? String)?.trim().orEmpty()

private fun JSONObject.canonicalBoolean(name: String): Boolean? = opt(name) as? Boolean

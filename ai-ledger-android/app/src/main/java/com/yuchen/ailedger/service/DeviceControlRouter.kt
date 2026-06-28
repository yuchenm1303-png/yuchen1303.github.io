package com.yuchen.ailedger.service

import org.json.JSONObject

/**
 * Pure protocol boundary between the cloud AgentBrain and Android internal controls.
 *
 * DeepSeek owns intent understanding, tool selection and argument calculation. Android only:
 * 1. identifies the declared structured tool/capability;
 * 2. separates step-envelope metadata from the canonical args object;
 * 3. forwards the canonical command to [DeviceControlSpecs] and [DeviceToolExecutor].
 *
 * No user text, display text, evidence text, aliases or keywords are interpreted here.
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

    fun fromDeviceControlJson(raw: JSONObject?, fallbackReason: String? = null): CloudAgentStep? {
        if (raw == null) return null

        val declaredTool = firstCanonicalString(raw, "tool", "capability", "type", "action")
            ?: return null
        val stepType = normalizeDeclaredTool(declaredTool) ?: return null
        val spec = DeviceControlSpecs.specFor(stepType) ?: return null

        val nestedArgs = raw.optJSONObject("args")
            ?: raw.optJSONObject("arguments")
            ?: raw.optJSONObject("params")
        val args = sanitizeCanonicalArgs(
            raw = raw,
            nestedArgs = nestedArgs,
            allowedArgNames = spec.allowedArgNames,
        )

        val topLevelPackage = raw.canonicalString("packageName")
        val argsPackage = args.canonicalString("packageName")
        val packageName = topLevelPackage.ifBlank { argsPackage }.ifBlank { null }

        return CloudAgentStep(
            type = stepType,
            targetText = raw.canonicalString("targetText").ifBlank { null },
            text = raw.canonicalString("text").ifBlank { null },
            reason = raw.canonicalString("reason").ifBlank {
                fallbackReason?.trim().orEmpty()
            }.ifBlank { null },
            riskLevel = raw.canonicalString("riskLevel")
                .ifBlank { DeviceControlSpecs.riskFor(stepType) }
                .lowercase()
                .replace('-', '_'),
            requiresConfirmation = raw.canonicalBoolean("requiresConfirmation")
                ?: spec.requiresConfirmation,
            appName = raw.canonicalString("appName").ifBlank { null },
            packageName = packageName,
            toolArgs = args.takeIf { it.length() > 0 },
        )
    }

    fun supportedCapabilities(): List<String> = DeviceControlSpecs.supportedCapabilities()

    fun normalChatSupportedCapabilities(): List<String> = DeviceControlSpecs.normalChatSupportedCapabilities()

    fun normalChatSupportedStepTypes(): List<String> = DeviceControlSpecs.normalChatSupportedStepTypes()

    private fun normalizeDeclaredTool(raw: String): String? {
        DeviceControlSpecs.normalizeCapability(raw)?.let { return it }
        val exactStepType = raw.trim().lowercase().replace('-', '_')
        return exactStepType.takeIf { DeviceControlSpecs.specFor(it) != null }
    }

    /**
     * A backend step can carry execution evidence and planner metadata beside its args. Some older
     * envelopes placed those fields inside the same JSON object. They are not device arguments and
     * must never reach [DeviceControlSpecs.validate].
     *
     * Canonical fields are copied without conversion. Unknown non-envelope fields are deliberately
     * retained so strict validation can still reject genuinely malformed tool arguments.
     */
    private fun sanitizeCanonicalArgs(
        raw: JSONObject,
        nestedArgs: JSONObject?,
        allowedArgNames: Set<String>,
    ): JSONObject {
        val source = nestedArgs ?: raw
        val clean = JSONObject()

        source.keys().asSequence().forEach { key ->
            if (key !in DeviceControlEnvelopeKeys && source.has(key) && !source.isNull(key)) {
                clean.put(key, source.opt(key))
            }
        }

        // Canonical arguments may be emitted at the step root by a structured cloud response.
        // Copy only exact schema names; no aliases, text parsing or value conversion is allowed.
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

private fun JSONObject.canonicalBoolean(name: String): Boolean? =
    opt(name) as? Boolean

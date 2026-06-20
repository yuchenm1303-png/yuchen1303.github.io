package com.yuchen.ailedger.service

import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

enum class AgentSurfacePreference(val wireValue: String) {
    Any("any"),
    NativeApp("native_app"),
    SystemSettings("system_settings"),
    Browser("browser");

    companion object {
        fun fromWireValue(value: String?): AgentSurfacePreference {
            return when (value.orEmpty().trim().lowercase().replace('-', '_')) {
                "native", "native_app", "app" -> NativeApp
                "system", "settings", "system_settings" -> SystemSettings
                "browser", "web", "web_page" -> Browser
                else -> Any
            }
        }
    }
}

data class AgentTaskExecutionContract(
    val preferredSurface: AgentSurfacePreference = AgentSurfacePreference.Any,
    val browserFallbackAllowed: Boolean = true,
    val requiredCapabilities: Set<String> = emptySet(),
    val requirePostActionVerification: Boolean = true,
    val highImpactFlow: Boolean = false,
    val reason: String = "",
) {
    /**
     * Legacy Agent Step clients used task phases before the visual-loop contract became immutable.
     * Phase is no longer part of contract identity; completion is owned by [CloudAgentState].
     */
    val phase: String
        get() = "unknown"

    fun toPromptLine(): String {
        val capabilities = requiredCapabilities.sorted().joinToString(",").ifBlank { "none" }
        return buildString {
            append("task_contract:v1")
            append("|preferredSurface=").append(preferredSurface.wireValue)
            append("|browserFallbackAllowed=").append(browserFallbackAllowed)
            append("|requiredCapabilities=").append(capabilities)
            append("|postActionVerification=").append(requirePostActionVerification)
            append("|highImpactFlow=").append(highImpactFlow)
            reason.takeIf { it.isNotBlank() }?.let { append("|reason=").append(it.take(160)) }
        }
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("schema", "android_task_execution_contract_v1")
            put("preferredSurface", preferredSurface.wireValue)
            put("browserFallbackAllowed", browserFallbackAllowed)
            put("requiredCapabilities", JSONArray(requiredCapabilities.sorted()))
            put("requirePostActionVerification", requirePostActionVerification)
            put("highImpactFlow", highImpactFlow)
            put("taskContractReason", reason.take(200))
        }
    }

    companion object {
        fun controllerRequest(): AgentTaskExecutionContract {
            return AgentTaskExecutionContract(
                preferredSurface = AgentSurfacePreference.Any,
                browserFallbackAllowed = true,
                requiredCapabilities = emptySet(),
                requirePostActionVerification = true,
                highImpactFlow = false,
                reason = "任务语义与应用能力由云端规划器判断；Android 仅校验结构化契约和已安装应用能力。",
            )
        }

        fun fromPlannerStep(step: CloudAgentStep?): AgentTaskExecutionContract? {
            step ?: return null
            val preferredSurfaceRaw = step.argString(
                "preferredSurface",
                "preferred_surface",
                "surfacePreference",
                "surface_preference",
            )
            val browserFallbackRaw = step.argString(
                "browserFallbackAllowed",
                "browser_fallback_allowed",
            )
            val requiredCapabilitiesRaw = step.argString(
                "requiredCapabilities",
                "required_capabilities",
                "requiredCapability",
                "required_capability",
            )
            val verificationRaw = step.argString(
                "requirePostActionVerification",
                "require_post_action_verification",
            )
            val contractReason = step.argString(
                "taskContractReason",
                "task_contract_reason",
                "contractReason",
            ).orEmpty()

            val hasContractFields = !preferredSurfaceRaw.isNullOrBlank() ||
                !browserFallbackRaw.isNullOrBlank() ||
                !requiredCapabilitiesRaw.isNullOrBlank() ||
                !verificationRaw.isNullOrBlank() ||
                contractReason.isNotBlank()
            if (!hasContractFields) return null

            val requiredCapabilities = requiredCapabilitiesRaw.orEmpty()
                .split(',', ';', '|')
                .map(::normalizeCapability)
                .filter { it.isNotBlank() }
                .toSet()
            val highImpact = step.requiresConfirmation ||
                step.riskLevel.orEmpty().trim().lowercase() in setOf("high", "critical")

            return AgentTaskExecutionContract(
                preferredSurface = AgentSurfacePreference.fromWireValue(preferredSurfaceRaw),
                browserFallbackAllowed = browserFallbackRaw.toFlexibleBoolean(defaultValue = true),
                requiredCapabilities = requiredCapabilities,
                requirePostActionVerification = verificationRaw.toFlexibleBoolean(defaultValue = true),
                highImpactFlow = highImpact,
                reason = contractReason.take(200),
            )
        }

        /**
         * Canonical response parser shared by the legacy Agent Step client and compatibility runtime.
         * It accepts the top-level contract and the declared agentStep args/arguments locations.
         */
        fun fromResponse(root: JSONObject?): AgentTaskExecutionContract? {
            val item = root.findContractObject() ?: return null
            val preferredSurfaceRaw = item.firstString(
                "preferredSurface",
                "preferred_surface",
                "surfacePreference",
                "surface_preference",
            )
            val browserFallbackRaw = item.firstString(
                "browserFallbackAllowed",
                "browser_fallback_allowed",
            )
            val requiredCapabilities = item.stringValues(
                "requiredCapabilities",
                "required_capabilities",
                "requiredCapability",
                "required_capability",
            ).map(::normalizeCapability).filter(String::isNotBlank).toSet()
            val verificationRaw = item.firstString(
                "requirePostActionVerification",
                "require_post_action_verification",
                "postActionVerification",
            )
            val highImpactRaw = item.firstString(
                "highImpactFlow",
                "high_impact_flow",
            )
            val contractReason = item.firstString(
                "taskContractReason",
                "task_contract_reason",
                "contractReason",
                "reason",
            ).orEmpty()

            val hasContractFields = !preferredSurfaceRaw.isNullOrBlank() ||
                !browserFallbackRaw.isNullOrBlank() ||
                requiredCapabilities.isNotEmpty() ||
                !verificationRaw.isNullOrBlank() ||
                !highImpactRaw.isNullOrBlank() ||
                contractReason.isNotBlank()
            if (!hasContractFields) return null

            return AgentTaskExecutionContract(
                preferredSurface = AgentSurfacePreference.fromWireValue(preferredSurfaceRaw),
                browserFallbackAllowed = browserFallbackRaw.toFlexibleBoolean(defaultValue = true),
                requiredCapabilities = requiredCapabilities,
                requirePostActionVerification = verificationRaw.toFlexibleBoolean(defaultValue = true),
                highImpactFlow = highImpactRaw.toFlexibleBoolean(defaultValue = false),
                reason = contractReason.take(200),
            )
        }

        private fun JSONObject?.findContractObject(): JSONObject? {
            if (this == null) return null
            val step = optJSONObject("agentStep") ?: optJSONObject("step")
            return optJSONObject("taskExecutionContract")
                ?: optJSONObject("taskContract")
                ?: step?.optJSONObject("arguments")
                ?: step?.optJSONObject("args")
                ?: optJSONObject("data")?.findContractObject()
                ?: optJSONObject("result")?.findContractObject()
                ?: optJSONObject("plan")?.findContractObject()
        }

        private fun JSONObject.firstString(vararg keys: String): String? =
            keys.asSequence()
                .map { optString(it).trim() }
                .firstOrNull(String::isNotBlank)

        private fun JSONObject.stringValues(vararg keys: String): List<String> {
            keys.forEach { key ->
                optJSONArray(key)?.let { array ->
                    return buildList {
                        for (index in 0 until array.length()) {
                            array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
                        }
                    }
                }
                optString(key).trim().takeIf(String::isNotBlank)?.let { text ->
                    return text.split(',', '，', ';', '；', '|')
                        .map(String::trim)
                        .filter(String::isNotBlank)
                }
            }
            return emptyList()
        }

        private fun normalizeCapability(value: String): String =
            value.trim().lowercase().replace('-', '_').replace(' ', '_')

        private fun String?.toFlexibleBoolean(defaultValue: Boolean): Boolean {
            return when (orEmpty().trim().lowercase()) {
                "true", "1", "yes", "allow", "allowed" -> true
                "false", "0", "no", "deny", "denied" -> false
                else -> defaultValue
            }
        }
    }
}

object AppCapability {
    const val NativeApp = "native_app"
    const val Browser = "browser"
    const val SystemSettings = "system_settings"
    const val HomeLauncher = "home_launcher"
    const val Maps = "maps"
    const val Camera = "camera"
    const val Dialer = "dialer"
    const val Email = "email"
    const val Sms = "sms"
    const val SystemApp = "system_app"
    const val UserApp = "user_app"
}

data class AgentDeviceProfile(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val release: String,
    val sdkInt: Int,
    val display: String,
) {
    fun toPromptLine(): String {
        return buildString {
            append("device_profile:v1")
            append("|manufacturer=").append(manufacturer.take(60))
            append("|brand=").append(brand.take(60))
            append("|model=").append(model.take(80))
            append("|androidRelease=").append(release.take(40))
            append("|sdkInt=").append(sdkInt)
            append("|buildDisplay=").append(display.take(100))
        }
    }

    companion object {
        fun current(): AgentDeviceProfile {
            return AgentDeviceProfile(
                manufacturer = Build.MANUFACTURER.orEmpty(),
                brand = Build.BRAND.orEmpty(),
                model = Build.MODEL.orEmpty(),
                release = Build.VERSION.RELEASE.orEmpty(),
                sdkInt = Build.VERSION.SDK_INT,
                display = Build.DISPLAY.orEmpty(),
            )
        }
    }
}
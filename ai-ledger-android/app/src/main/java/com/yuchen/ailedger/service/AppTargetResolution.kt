package com.yuchen.ailedger.service

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

const val AGENT_TASK_PHASE_RESOLVE_REQUIREMENTS = "resolve_requirements"
const val AGENT_TASK_PHASE_RESOLVE_TARGET_APP = "resolve_target_app"
const val AGENT_TASK_PHASE_OPEN_TARGET_APP = "open_target_app"
const val AGENT_TASK_PHASE_VERIFY_TARGET_APP = "verify_target_app"
const val AGENT_TASK_PHASE_VISUAL_NAVIGATION = "visual_navigation"
const val AGENT_TASK_PHASE_VERIFY_RESULT = "verify_result"
const val AGENT_TASK_PHASE_COMPLETED = "completed"
const val AGENT_TASK_PHASE_USER_ASSISTANCE = "user_assistance"
const val AGENT_TASK_PHASE_UNKNOWN = "unknown"

fun normalizeAgentTaskPhase(raw: String?): String = when (
    raw.orEmpty().trim().lowercase().replace('-', '_')
) {
    AGENT_TASK_PHASE_RESOLVE_REQUIREMENTS -> AGENT_TASK_PHASE_RESOLVE_REQUIREMENTS
    AGENT_TASK_PHASE_RESOLVE_TARGET_APP -> AGENT_TASK_PHASE_RESOLVE_TARGET_APP
    AGENT_TASK_PHASE_OPEN_TARGET_APP -> AGENT_TASK_PHASE_OPEN_TARGET_APP
    AGENT_TASK_PHASE_VERIFY_TARGET_APP -> AGENT_TASK_PHASE_VERIFY_TARGET_APP
    AGENT_TASK_PHASE_VISUAL_NAVIGATION -> AGENT_TASK_PHASE_VISUAL_NAVIGATION
    AGENT_TASK_PHASE_VERIFY_RESULT -> AGENT_TASK_PHASE_VERIFY_RESULT
    AGENT_TASK_PHASE_COMPLETED -> AGENT_TASK_PHASE_COMPLETED
    AGENT_TASK_PHASE_USER_ASSISTANCE -> AGENT_TASK_PHASE_USER_ASSISTANCE
    else -> AGENT_TASK_PHASE_UNKNOWN
}

/**
 * Keeps the cloud-declared execution contract for the active visual session.
 *
 * The canonical contract type lives in [AgentTaskExecutionContract.kt]. This runtime only parses
 * and caches that same protocol; it must not introduce a second contract model or infer task
 * semantics from local keywords.
 */
object AgentTaskContractRuntime {
    private data class Entry(
        val sessionId: String,
        val goal: String,
        val contract: AgentTaskExecutionContract,
        val updatedAt: Long,
    )

    private var entry: Entry? = null

    @Synchronized
    fun ensureSession(sessionId: String, goal: String): AgentTaskExecutionContract? {
        val current = entry
        if (current == null || current.sessionId != sessionId || current.goal != goal ||
            SystemClock.elapsedRealtime() - current.updatedAt > TTL_MS
        ) {
            entry = null
            return null
        }
        return current.contract
    }

    @Synchronized
    fun update(sessionId: String, goal: String, root: JSONObject?): AgentTaskExecutionContract? {
        val contract = root.toAgentTaskExecutionContractOrNull() ?: return null
        entry = Entry(sessionId, goal, contract, SystemClock.elapsedRealtime())
        return contract
    }

    @Synchronized
    fun current(goal: String): AgentTaskExecutionContract? =
        entry?.takeIf {
            it.goal == goal && SystemClock.elapsedRealtime() - it.updatedAt <= TTL_MS
        }?.contract

    @Synchronized
    fun clear(sessionId: String) {
        if (entry?.sessionId == sessionId) entry = null
    }

    private const val TTL_MS = 5 * 60 * 1_000L
}

private fun JSONObject?.toAgentTaskExecutionContractOrNull(): AgentTaskExecutionContract? {
    val item = findTaskContractObject() ?: return null
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
    ).map(::normalizeCapabilityValue).filter(String::isNotBlank).toSet()
    val verificationRaw = item.firstString(
        "requirePostActionVerification",
        "require_post_action_verification",
        "postActionVerification",
    )
    val reason = item.firstString(
        "taskContractReason",
        "task_contract_reason",
        "contractReason",
        "reason",
    ).orEmpty()
    val highImpactRaw = item.firstString(
        "highImpactFlow",
        "high_impact_flow",
    )

    val hasContractFields = !preferredSurfaceRaw.isNullOrBlank() ||
        !browserFallbackRaw.isNullOrBlank() ||
        requiredCapabilities.isNotEmpty() ||
        !verificationRaw.isNullOrBlank() ||
        !highImpactRaw.isNullOrBlank() ||
        reason.isNotBlank()
    if (!hasContractFields) return null

    return AgentTaskExecutionContract(
        preferredSurface = AgentSurfacePreference.fromWireValue(preferredSurfaceRaw),
        browserFallbackAllowed = browserFallbackRaw.toFlexibleBoolean(defaultValue = true),
        requiredCapabilities = requiredCapabilities,
        requirePostActionVerification = verificationRaw.toFlexibleBoolean(defaultValue = true),
        highImpactFlow = highImpactRaw.toFlexibleBoolean(defaultValue = false),
        reason = reason.take(200),
    )
}

private fun JSONObject?.findTaskContractObject(): JSONObject? {
    if (this == null) return null
    val step = optJSONObject("agentStep") ?: optJSONObject("step")
    return optJSONObject("taskExecutionContract")
        ?: optJSONObject("taskContract")
        ?: step?.optJSONObject("arguments")
        ?: step?.optJSONObject("args")
        ?: optJSONObject("data")?.findTaskContractObject()
        ?: optJSONObject("result")?.findTaskContractObject()
        ?: optJSONObject("plan")?.findTaskContractObject()
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

private fun String?.toFlexibleBoolean(defaultValue: Boolean): Boolean = when (
    orEmpty().trim().lowercase()
) {
    "true", "1", "yes", "allow", "allowed" -> true
    "false", "0", "no", "deny", "denied" -> false
    else -> defaultValue
}

private fun normalizeCapabilityValue(value: String): String =
    value.trim().lowercase().replace('-', '_').replace(' ', '_')

data class TargetAppResolution(
    val status: String,
    val selectedApp: InstalledAppEntry?,
    val candidates: List<InstalledAppEntry>,
    val requiredCapabilities: Set<String>,
    val reason: String,
    val capabilityProfiles: Map<String, Set<String>> = emptyMap(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("schema", "target_app_resolution_v2_cloud_contract")
        put("status", status)
        put("requiredCapabilities", JSONArray(requiredCapabilities.toList().sorted()))
        selectedApp?.let { put("selectedApp", appJson(it)) }
        put("candidates", JSONArray().apply { candidates.forEach { put(appJson(it)) } })
        put("reason", reason)
    }

    private fun appJson(app: InstalledAppEntry): JSONObject = JSONObject().apply {
        put("label", app.label)
        put("packageName", app.packageName)
        put("launchable", true)
        put("capabilityProfile", JSONObject().apply {
            put("capabilities", JSONArray(capabilityProfiles[app.packageName].orEmpty().toList().sorted()))
        })
    }
}

/**
 * Resolves a cloud-declared task contract against the real launchable-app inventory.
 *
 * This class performs only mechanical capability/surface validation. It never derives a target
 * app from the user's wording and never owns task semantics.
 */
class TargetAppResolver(
    context: Context,
    private val appIndex: InstalledAppIndex,
) {
    private val capabilityRegistry = AppCapabilityRegistry(context.applicationContext, appIndex)
    private val preferences = context.applicationContext.getSharedPreferences(
        "ai_ledger_default_target_apps",
        Context.MODE_PRIVATE,
    )

    fun resolve(contract: AgentTaskExecutionContract?): TargetAppResolution {
        if (contract == null) {
            return TargetAppResolution(
                status = "not_required",
                selectedApp = null,
                candidates = emptyList(),
                requiredCapabilities = emptySet(),
                reason = "尚无云端任务契约。",
            )
        }

        val required = contract.requiredCapabilities
            .map(::normalizeCapabilityValue)
            .filter(String::isNotBlank)
            .toSet()
        if (required.isEmpty() && contract.preferredSurface == AgentSurfacePreference.Any) {
            return TargetAppResolution(
                status = "not_required",
                selectedApp = null,
                candidates = emptyList(),
                requiredCapabilities = required,
                reason = "任务契约未声明目标界面或应用能力。",
            )
        }

        val apps = appIndex.getLaunchableApps(false)
        val profiles = apps.associate { app ->
            app.packageName to capabilityRegistry.profileFor(app).capabilities
        }
        val preferredPackage = preferences.getString(preferenceKey(contract, required), null)
        val matched = apps
            .filter { app ->
                val capabilities = profiles[app.packageName].orEmpty()
                capabilities.containsAll(required) &&
                    matchesPreferredSurface(contract, capabilities) &&
                    capabilityRegistry.validateSelection(contract, app).ok
            }
            .sortedByDescending { app ->
                val capabilities = profiles[app.packageName].orEmpty()
                var score = capabilities.size
                if (app.packageName == preferredPackage) score += 2_000
                if (contract.preferredSurface == AgentSurfacePreference.NativeApp &&
                    AppCapability.Browser !in capabilities
                ) {
                    score += 200
                }
                score
            }
            .take(8)

        matched.firstOrNull { it.packageName == preferredPackage }?.let { preferred ->
            return TargetAppResolution(
                status = "resolved",
                selectedApp = preferred,
                candidates = matched,
                requiredCapabilities = required,
                reason = "已使用用户保存的默认能力应用。",
                capabilityProfiles = profiles,
            )
        }
        return choose(
            required = required,
            apps = matched,
            resolvedReason = "设备上仅有一个满足云端任务契约的可启动应用。",
            profiles = profiles,
        )
    }

    fun remember(required: Set<String>, packageName: String) {
        val normalized = required.map(::normalizeCapabilityValue).filter(String::isNotBlank).toSet()
        val key = if (normalized.isEmpty()) "capability.any" else "capability.${normalized.sorted().first()}"
        preferences.edit().putString(key, packageName).apply()
    }

    private fun matchesPreferredSurface(
        contract: AgentTaskExecutionContract,
        capabilities: Set<String>,
    ): Boolean = when (contract.preferredSurface) {
        AgentSurfacePreference.Any -> true
        AgentSurfacePreference.NativeApp -> AppCapability.NativeApp in capabilities
        AgentSurfacePreference.SystemSettings -> AppCapability.SystemSettings in capabilities
        AgentSurfacePreference.Browser -> AppCapability.Browser in capabilities
    }

    private fun preferenceKey(
        contract: AgentTaskExecutionContract,
        required: Set<String>,
    ): String {
        val capability = required.sorted().firstOrNull()
        return if (capability != null) {
            "capability.$capability"
        } else {
            "surface.${contract.preferredSurface.wireValue}"
        }
    }

    private fun choose(
        required: Set<String>,
        apps: List<InstalledAppEntry>,
        resolvedReason: String,
        profiles: Map<String, Set<String>>,
    ): TargetAppResolution = when (apps.size) {
        0 -> TargetAppResolution(
            status = "not_found",
            selectedApp = null,
            candidates = emptyList(),
            requiredCapabilities = required,
            reason = "设备上未识别到满足云端任务契约的可启动应用。",
            capabilityProfiles = profiles,
        )
        1 -> TargetAppResolution(
            status = "resolved",
            selectedApp = apps.first(),
            candidates = apps,
            requiredCapabilities = required,
            reason = resolvedReason,
            capabilityProfiles = profiles,
        )
        else -> TargetAppResolution(
            status = "ambiguous",
            selectedApp = null,
            candidates = apps,
            requiredCapabilities = required,
            reason = "存在多个满足云端任务契约的应用，需要用户选择一次。",
            capabilityProfiles = profiles,
        )
    }
}
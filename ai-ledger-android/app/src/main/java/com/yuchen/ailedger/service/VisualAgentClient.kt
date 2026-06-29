package com.yuchen.ailedger.service

import android.os.SystemClock
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

private const val VISUAL_AGENT_CONNECT_TIMEOUT_MS = 8_000
private const val VISUAL_AGENT_READ_TIMEOUT_MS = 25_000
private const val VISUAL_AGENT_MAX_RECENT_ACTIONS = 14
private const val VISUAL_AGENT_MAX_RECENT_ACTION_CHARS = 1_200
private const val VISUAL_AGENT_NORMAL_HISTORY_ITEMS = 2
private const val VISUAL_AGENT_RECOVERY_HISTORY_ITEMS = 4
private const val VISUAL_AGENT_MAX_HISTORY_OUTPUT_CHARS = 1_200
private const val VISUAL_AGENT_MAX_HISTORY_RESULT_CHARS = 240
private const val VISUAL_AGENT_MAX_APP_CONTEXT_ITEMS = 160
private const val VISUAL_AGENT_MAX_APP_TEXT_CHARS = 120
private const val VISUAL_AGENT_MAX_APP_ALIASES = 24
private const val VISUAL_AGENT_MAX_APP_CAPABILITIES = 32
private const val VISUAL_AGENT_MAX_VERIFICATION_EVENTS = 10
private const val VISUAL_AGENT_MAX_BLOCKED_SIGNATURES = 8
private const val VISUAL_AGENT_MAX_INTERACTION_ITEMS = 16
private const val VISUAL_AGENT_MAX_INTERACTION_CHARS = 1_200
private const val VISUAL_AGENT_EXPLORATION_PRESSURE_STEPS = 6
private const val VISUAL_AGENT_EXPLORATION_BUDGET_STEPS = 9
private const val VISUAL_AGENT_SESSION_PROTOCOL = "android_visual_agent_v14_task_contract_harness"
private const val VISUAL_AGENT_INTERACTION_PROTOCOL = "gui_plus_dialogue_v1"
private const val STRUCTURAL_NO_PROGRESS_THRESHOLD = 3
private const val STRUCTURAL_FAILURE_THRESHOLD = 3

internal object VisualAgentProtocol {
    const val coordinateProtocol = "normalized_screen_0_1"
    const val appIdentityProtocol = "package_name_v2"

    /** Single source of truth shared by parser, validator, advertised protocol and executor. */
    val supportedStepTypes: Set<String> = CloudAgentStep.supportedTypes
}

data class VisualAgentHistoryItem(
    val screenshot: AgentScreenVisual,
    val assistantOutput: String,
    val executionResult: String,
)

data class VisualAgentAppContextItem(
    val label: String,
    val packageName: String,
    val aliases: List<String> = emptyList(),
    val capabilities: List<String> = emptyList(),
)

class VisualAgentRequestException(
    val httpStatus: Int?,
    val code: String,
    val retryable: Boolean,
    val backendMessage: String,
    cause: Throwable? = null,
) : IOException(buildVisualAgentErrorMessage(httpStatus, code, retryable, backendMessage), cause)

@Throws(IOException::class)
fun AiWorkerClient.requestVisualAgentStep(
    goal: String,
    snapshot: AgentScreenSnapshot,
    recentActions: List<String> = emptyList(),
    visualHistory: List<VisualAgentHistoryItem> = emptyList(),
    appContext: List<VisualAgentAppContextItem> = emptyList(),
    deviceId: String = "android-compose-visual",
    agentSessionId: String = "visual-session-${System.currentTimeMillis()}",
    executionMode: AgentExecutionMode = AgentExecutionMode.ExplicitAgent,
    deviceProfile: AgentDeviceProfile? = null,
    runtimeContext: VisualAgentRuntimeContext? = null,
    taskMemory: VisualTaskMemory? = null,
): CloudAgentPlan {
    val endpointBase = endpoint.trim().trimEnd('/')
    if (endpointBase.isBlank()) throw IOException("AI Worker endpoint is not configured")
    val payload = buildVisualAgentPayload(
        goal = goal,
        snapshot = snapshot,
        recentActions = recentActions,
        visualHistory = visualHistory,
        appContext = appContext,
        deviceId = deviceId,
        agentSessionId = agentSessionId,
        executionMode = executionMode,
        deviceProfile = deviceProfile,
        runtimeContext = runtimeContext,
        taskMemory = taskMemory,
    )
    // 记录最终发送的真实 JSON；存储层会剥离截图字节和敏感字段。
    VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordModelRequestPayload(payload)
    return postVisualAgentStep(endpointBase, payload, deviceId, agentSessionId)
}

internal fun buildVisualAgentPayload(
    goal: String,
    snapshot: AgentScreenSnapshot,
    recentActions: List<String>,
    visualHistory: List<VisualAgentHistoryItem> = emptyList(),
    appContext: List<VisualAgentAppContextItem> = emptyList(),
    deviceId: String = "android-compose-visual",
    agentSessionId: String = "visual-session-test",
    executionMode: AgentExecutionMode = AgentExecutionMode.ExplicitAgent,
    deviceProfile: AgentDeviceProfile? = null,
    runtimeContext: VisualAgentRuntimeContext? = null,
    taskMemory: VisualTaskMemory? = null,
): JSONObject {
    val cleanGoal = goal.trim().take(240)
    val cleanDeviceId = deviceId.trim().take(120).ifBlank { "android-compose-visual" }
    val cleanSessionId = agentSessionId.trim().take(120).ifBlank { "visual-session-${System.currentTimeMillis()}" }
    val resolvedRuntimeContext = runtimeContext ?: VisualAgentRuntimeContext(
        surfaceState = VisualSurfaceState.Planning,
        currentPackage = snapshot.packageName,
        observationId = VisualObservationProtocol.observationId(snapshot, 0L, 0L),
    )
    val modeKey = when (executionMode) {
        AgentExecutionMode.VisualForce -> "visual_force"
        AgentExecutionMode.ExplicitAgent -> "explicit_agent"
        AgentExecutionMode.NormalChatDeviceTool -> "normal_chat_device_tool"
    }
    val cleanRecentActionLines = recentActions
        .takeLast(VISUAL_AGENT_MAX_RECENT_ACTIONS)
        .map { it.trim().take(VISUAL_AGENT_MAX_RECENT_ACTION_CHARS) }
        .filter(String::isNotBlank)
    val recentAgentActions = JSONArray(cleanRecentActionLines)
    val interactionHistory = buildInteractionHistory(cleanRecentActionLines)
    val historyExecutionResults = visualHistory
        .takeLast(VISUAL_AGENT_RECOVERY_HISTORY_ITEMS)
        .map { it.executionResult.trim().take(VISUAL_AGENT_MAX_HISTORY_RESULT_CHARS) }
        .filter(String::isNotBlank)
    val feedbackLines = (cleanRecentActionLines + historyExecutionResults).distinct().takeLast(VISUAL_AGENT_MAX_VERIFICATION_EVENTS)
    val verificationEvents = feedbackLines.filter(String::isVisualRuntimeFeedback)
    val lastAdvancedIndex = feedbackLines.indexOfLast(String::isVisualAdvancedFeedback)
    val activeFeedbackWindow = if (lastAdvancedIndex >= 0) feedbackLines.drop(lastAdvancedIndex + 1) else feedbackLines
    val activeVerificationEvents = activeFeedbackWindow.filter(String::isVisualRuntimeFeedback)
    val screenChangedCount = verificationEvents.count(String::isVisualAdvancedFeedback)
    val explorationSprawlCount = verificationEvents.count(String::isVisualExplorationSprawlFeedback)
    val noProgressCount = activeVerificationEvents.count(String::isVisualNoProgressFeedback)
    val structuralFailureCount = activeVerificationEvents.count(String::isStructuralRouteFailureFeedback)
    val localVisualRetryCount = activeVerificationEvents.count(String::isLocalVisualRetryFeedback)
    val finishVerificationRequested = activeVerificationEvents.any(String::isVisualFinishVerificationFeedback)
    val semanticReplanRequested = activeVerificationEvents.any { it.contains("replanRequired=true", ignoreCase = true) } ||
        taskMemory?.replanRequested == true
    val blockedActionSignatures = (
        activeVerificationEvents
            .filter { it.isVisualNoProgressFeedback() || it.isVisualFailureFeedback() || it.isLocalVisualRetryFeedback() }
            .mapNotNull(String::visualActionSignatureOrNull) +
            taskMemory.orEmptyBlockedSignatures()
        ).distinct().takeLast(VISUAL_AGENT_MAX_BLOCKED_SIGNATURES)
    val lastVerificationEvent = verificationEvents.lastOrNull().orEmpty()
    val lastVerification = when {
        lastVerificationEvent.isVisualFinishVerificationFeedback() -> "finish_verification_pending"
        lastVerificationEvent.isStructuralRouteFailureFeedback() -> "structural_route_failure"
        lastVerificationEvent.isVisualExplorationSprawlFeedback() -> "visual_exploration_sprawl"
        lastVerificationEvent.contains("semanticStatus=ambiguous", true) -> "semantic_ambiguous"
        lastVerificationEvent.contains("semanticStatus=regressed", true) -> "semantic_regressed"
        lastVerificationEvent.contains("semanticStatus=stalled", true) -> "semantic_stalled"
        lastVerificationEvent.isVisualNoProgressFeedback() -> "visual_no_progress"
        lastVerificationEvent.isLocalVisualRetryFeedback() -> "visual_local_retry"
        lastVerificationEvent.isVisualFailureFeedback() -> "execution_failed"
        lastVerificationEvent.isVisualAdvancedFeedback() -> "semantic_advanced"
        else -> taskMemory?.progressStatus ?: "unknown"
    }
    val executedActionLines = cleanRecentActionLines.filter {
        it.contains(":ok:", true) || it.contains(":failed:", true) || it.contains(":retry:", true)
    }
    val historyResultLines = historyExecutionResults.filter {
        it.contains(":ok:", true) || it.contains(":failed:", true) || it.contains(":retry:", true)
    }
    val lastExecutionResultOk = executedActionLines.asReversed().firstNotNullOfOrNull(String::visualResultOkOrNull)
        ?: historyResultLines.asReversed().firstNotNullOfOrNull(String::visualResultOkOrNull)
    val lastResultOk = when {
        lastVerificationEvent.isVisualNoProgressFeedback() ||
            lastVerificationEvent.isVisualExplorationSprawlFeedback() ||
            lastVerificationEvent.isVisualFailureFeedback() ||
            lastVerificationEvent.isLocalVisualRetryFeedback() -> false
        lastVerificationEvent.isVisualAdvancedFeedback() -> true
        else -> lastExecutionResultOk
    }
    val executedActionSignatures = executedActionLines.mapNotNull(String::visualActionSignatureOrNull)
    val lastActionSignature = executedActionSignatures.lastOrNull()
        ?: activeVerificationEvents.asReversed().firstNotNullOfOrNull(String::visualActionSignatureOrNull)
        ?: ""
    val fallbackBudgetRemaining = (VISUAL_AGENT_EXPLORATION_BUDGET_STEPS - executedActionSignatures.size).coerceAtLeast(0)
    val explorationBudgetRemaining = taskMemory?.remainingExplorationBudget ?: fallbackBudgetRemaining
    val explorationBudgetExceeded = taskMemory?.let { it.remainingExplorationBudget <= 0 && it.replanRequested }
        ?: (executedActionSignatures.size >= VISUAL_AGENT_EXPLORATION_BUDGET_STEPS)
    val explorationPressureLevel = when {
        explorationBudgetExceeded || explorationSprawlCount > 0 || taskMemory?.recoveryMode == true -> "high"
        executedActionSignatures.size >= VISUAL_AGENT_EXPLORATION_PRESSURE_STEPS || interactionHistory.length() > 0 -> "medium"
        else -> "low"
    }
    val sameActionCount = if (lastActionSignature.isBlank()) 0
        else executedActionSignatures.asReversed().takeWhile { it == lastActionSignature }.count()

    val visualOwnershipActive = resolvedRuntimeContext.guiPlusEligible && resolvedRuntimeContext.verifiedTargetPackage.isNotBlank()
    val runtimeRequiresRouteRefresh = !visualOwnershipActive && resolvedRuntimeContext.surfaceState == VisualSurfaceState.Replanning
    val eventRequiresRouteRefresh = !visualOwnershipActive && (
        activeVerificationEvents.any(String::isStructuralRouteFailureFeedback) ||
            noProgressCount >= STRUCTURAL_NO_PROGRESS_THRESHOLD || structuralFailureCount >= STRUCTURAL_FAILURE_THRESHOLD
        )
    val routeRefreshRequested = runtimeRequiresRouteRefresh || eventRequiresRouteRefresh
    val localVisualRetryRequested = !routeRefreshRequested && (
        localVisualRetryCount > 0 || explorationSprawlCount > 0 || semanticReplanRequested ||
            (noProgressCount in 1 until STRUCTURAL_NO_PROGRESS_THRESHOLD) ||
            activeVerificationEvents.any { it.isVisualFailureFeedback() && !it.isStructuralRouteFailureFeedback() }
        )
    val guiPlusReplanRequested = finishVerificationRequested || localVisualRetryRequested ||
        (visualOwnershipActive && (activeVerificationEvents.any(String::isVisualFailureFeedback) || noProgressCount > 0 || structuralFailureCount > 0))
    val currentPackageMatchesVerifiedTarget = snapshot.packageName == resolvedRuntimeContext.verifiedTargetPackage

    val memoryJson = taskMemory?.toJson()
    val executionFeedback = JSONObject().apply {
        put("lastResultOk", lastResultOk ?: JSONObject.NULL)
        put("lastVerification", lastVerification)
        put("screenChangedCount", screenChangedCount)
        put("explorationSprawlCount", explorationSprawlCount)
        put("explorationPressureLevel", explorationPressureLevel)
        put("explorationBudgetRemaining", explorationBudgetRemaining)
        put("explorationBudgetExceeded", explorationBudgetExceeded)
        put("noProgressCount", noProgressCount)
        put("structuralFailureCount", structuralFailureCount)
        put("localVisualRetryCount", localVisualRetryCount)
        put("sameActionCount", sameActionCount)
        put("lastActionSignature", lastActionSignature)
        put("blockedActionSignatures", JSONArray(blockedActionSignatures))
        put("verificationEvents", JSONArray(verificationEvents))
        put("latestEvent", lastVerificationEvent)
        put("finishVerificationRequested", finishVerificationRequested)
        put("localVisualRetryRequested", localVisualRetryRequested)
        put("visualReplanRequested", guiPlusReplanRequested)
        put("guiPlusReplanRequested", guiPlusReplanRequested)
        put("routeRefreshRequested", routeRefreshRequested)
        put("invalidateCachedAgentBrainRoute", routeRefreshRequested)
        mergeTaskMemorySummary(taskMemory)
    }
    val lastToolResponse = JSONObject().apply {
        put("type", "tool_response")
        put("toolName", "mobile_use")
        put("success", lastResultOk ?: JSONObject.NULL)
        put("result", lastVerificationEvent.ifBlank { executedActionLines.lastOrNull() ?: historyExecutionResults.lastOrNull().orEmpty() })
        put("verification", lastVerification)
        put("actionSignature", lastActionSignature)
        put("screenChanged", lastVerification == "semantic_advanced")
        put("screenChangedCount", screenChangedCount)
        put("explorationSprawlCount", explorationSprawlCount)
        put("explorationPressureLevel", explorationPressureLevel)
        put("explorationBudgetRemaining", explorationBudgetRemaining)
        put("explorationBudgetExceeded", explorationBudgetExceeded)
        put("finishVerificationRequested", finishVerificationRequested)
        put("localVisualRetryRequested", localVisualRetryRequested)
        put("routeRefreshRequested", routeRefreshRequested)
        put("observationId", resolvedRuntimeContext.observationId)
        mergeTaskMemorySummary(taskMemory)
    }

    val canonicalAppItems = appContext.asSequence()
        .filter { it.label.isNotBlank() && it.packageName.isNotBlank() }
        .distinctBy { it.packageName }
        .take(VISUAL_AGENT_MAX_APP_CONTEXT_ITEMS)
        .map { item ->
            CanonicalVisualApp(
                appRef = item.packageName.trim().take(VISUAL_AGENT_MAX_APP_TEXT_CHARS),
                label = item.label.trim().take(VISUAL_AGENT_MAX_APP_TEXT_CHARS),
                packageName = item.packageName.trim().take(VISUAL_AGENT_MAX_APP_TEXT_CHARS),
                aliases = item.aliases.map { it.trim().take(VISUAL_AGENT_MAX_APP_TEXT_CHARS) }
                    .filter(String::isNotBlank).distinct().take(VISUAL_AGENT_MAX_APP_ALIASES),
                capabilities = item.capabilities.map { it.trim().lowercase().replace('-', '_').take(VISUAL_AGENT_MAX_APP_TEXT_CHARS) }
                    .filter(String::isNotBlank).distinct().take(VISUAL_AGENT_MAX_APP_CAPABILITIES),
            )
        }.toList()
    val appInventoryHash = buildVisualAppInventoryHash(canonicalAppItems)
    val canonicalApps = JSONArray().apply {
        canonicalAppItems.forEach { item ->
            put(JSONObject().apply {
                put("appRef", item.appRef)
                put("label", item.label)
                put("displayName", item.label)
                put("packageName", item.packageName)
                put("identityType", "package_name")
                put("launchable", true)
                put("aliases", JSONArray(item.aliases))
                put("capabilities", JSONArray(item.capabilities))
            })
        }
    }
    val appCatalog = JSONObject().apply {
        put("schema", "android_visual_app_catalog_v4_single_directory")
        put("identityProtocol", VisualAgentProtocol.appIdentityProtocol)
        put("identityField", "packageName")
        put("appRefField", "appRef")
        put("displayField", "label")
        put("appNameRole", "display_only")
        put("selectionOwner", "deepseek")
        put("validationOwner", "android_package_identity_and_safety_only")
        put("inventoryHash", appInventoryHash)
        put("entryCount", canonicalApps.length())
        put("entriesField", "appContext")
        put("entriesIncludedOnce", true)
    }
    val appSelectionProtocol = JSONObject().apply {
        put("schema", "android_app_selection_protocol_v4_single_directory")
        put("semanticOwner", "deepseek")
        put("machineIdentity", "packageName")
        put("acceptedModelFields", JSONArray(listOf("packageName", "appRef")))
        put("appNameRole", "display_only")
        put("androidCanonicalizesAppName", true)
        put("androidSelectsApps", false)
        put("androidRanksApps", false)
        put("androidResolvesUserIntent", false)
        put("localKeywordMatching", false)
        put("catalogEntriesField", "appContext")
        put("mustSelectFromInventoryHash", appInventoryHash)
    }

    val visual = snapshot.visual?.takeIf { it.hasImage }
    val guiPlusEligible = resolvedRuntimeContext.guiPlusEligible && !routeRefreshRequested
    val runtimeExecutionContext = JSONObject().apply {
        put("schema", "android_visual_execution_runtime_v1")
        put("surfaceState", resolvedRuntimeContext.surfaceState.wireValue)
        put("selectedTargetPackage", resolvedRuntimeContext.selectedTargetPackage)
        put("verifiedTargetPackage", resolvedRuntimeContext.verifiedTargetPackage)
        put("currentPackage", snapshot.packageName)
        put("observationId", resolvedRuntimeContext.observationId)
        put("routeEpoch", resolvedRuntimeContext.routeEpoch)
        put("surfaceEpoch", resolvedRuntimeContext.surfaceEpoch)
        put("guiPlusEligible", guiPlusEligible)
        put("targetPackageBound", resolvedRuntimeContext.verifiedTargetPackage.isNotBlank())
        put("currentPackageMatchesVerifiedTarget", currentPackageMatchesVerifiedTarget)
        put("localSemanticDecision", false)
    }
    val surfaceContext = JSONObject().apply {
        put("role", if (guiPlusEligible) "work_surface" else "planning")
        put("surfaceState", resolvedRuntimeContext.surfaceState.wireValue)
        put("selectedTargetPackage", resolvedRuntimeContext.selectedTargetPackage)
        put("verifiedTargetPackage", resolvedRuntimeContext.verifiedTargetPackage)
        put("currentPackage", snapshot.packageName)
        put("currentPackageMatchesVerifiedTarget", currentPackageMatchesVerifiedTarget)
        put("guiPlusEligible", guiPlusEligible)
        put("observationId", resolvedRuntimeContext.observationId)
    }
    val deviceProfileJson = deviceProfile?.toVisualAgentJson()
    val historyLimit = if (
        taskMemory?.recoveryMode == true || routeRefreshRequested || guiPlusReplanRequested
    ) VISUAL_AGENT_RECOVERY_HISTORY_ITEMS else VISUAL_AGENT_NORMAL_HISTORY_ITEMS

    return JSONObject().apply {
        put("action", "visual_agent_step")
        put("intent", "visual_agent_step")
        put("type", "agent_step")
        put("requestType", "visual_agent_step")
        put("agentStepRequest", true)
        put("visualAgentDirect", guiPlusEligible)
        put("agentMode", true)
        put("computerUseMode", true)
        put("forceVisualAgent", true)
        put("allowInternalDeviceTools", true)
        put("decisionOwner", "deepseek_then_selected_executor")
        put("visualDecisionOwner", if (guiPlusEligible) "gui_plus" else "deepseek")
        put("exclusiveVisualSession", guiPlusEligible)
        put("allowAgentBrain", !guiPlusEligible)
        put("allowRoutePlanner", false)
        put("allowSemanticJudge", false)
        put("executionMode", modeKey)
        put("goal", cleanGoal)
        put("agentGoal", cleanGoal)
        put("message", cleanGoal)
        put("agentSessionId", cleanSessionId)
        put("sessionId", cleanSessionId)
        put("agentSessionProtocol", VISUAL_AGENT_SESSION_PROTOCOL)
        put("interactionProtocol", VISUAL_AGENT_INTERACTION_PROTOCOL)
        put("interactionHistory", interactionHistory)
        put("interactionTurnCount", interactionHistory.length())
        put("deviceId", cleanDeviceId)
        put("clientId", cleanDeviceId)
        put("deviceProfile", deviceProfileJson ?: JSONObject.NULL)
        put("appIdentityProtocol", VisualAgentProtocol.appIdentityProtocol)
        put("appInventoryHash", appInventoryHash)
        put("appCatalog", appCatalog)
        put("appSelectionProtocol", appSelectionProtocol)
        put("runtimeExecutionContext", runtimeExecutionContext)
        put("surfaceContext", surfaceContext)
        put("observationId", resolvedRuntimeContext.observationId)
        put("expectedActionObservationId", resolvedRuntimeContext.observationId)
        put("currentPackage", snapshot.packageName)
        put("selectedTargetPackage", resolvedRuntimeContext.selectedTargetPackage)
        put("verifiedTargetPackage", resolvedRuntimeContext.verifiedTargetPackage)
        put("surfaceState", resolvedRuntimeContext.surfaceState.wireValue)
        put("screenSnapshot", snapshot.toJson(includeImage = false))
        put("recentAgentActions", recentAgentActions)
        put("recentActions", recentAgentActions)
        put("executionFeedback", executionFeedback)
        put("lastToolResponse", lastToolResponse)
        put("toolResponse", lastToolResponse)
        put("finishVerificationRequested", finishVerificationRequested)
        put("localVisualRetryRequested", localVisualRetryRequested)
        put("visualReplanRequested", guiPlusReplanRequested)
        put("guiPlusReplanRequested", guiPlusReplanRequested)
        put("routeRefreshRequested", routeRefreshRequested)
        put("invalidateCachedAgentBrainRoute", routeRefreshRequested)
        taskMemory?.taskContract?.let { put("taskContract", it.toJson()) }
        put("agentMemory", JSONObject().apply {
            put("schema", "android_visual_agent_loop_memory_v14_task_contract_harness")
            put("recentActions", recentAgentActions)
            put("interactionProtocol", VISUAL_AGENT_INTERACTION_PROTOCOL)
            put("interactionHistory", interactionHistory)
            put("verificationEvents", JSONArray(verificationEvents))
            put("blockedActionSignatures", JSONArray(blockedActionSignatures))
            put("executionFeedback", executionFeedback)
            put("lastToolResponse", lastToolResponse)
            put("runtimeExecutionContext", runtimeExecutionContext)
            put("surfaceContext", surfaceContext)
            put("deviceProfile", deviceProfileJson ?: JSONObject.NULL)
            put("appInventoryHash", appInventoryHash)
            put("appSelectionProtocol", appSelectionProtocol)
            put("taskMemory", memoryJson ?: JSONObject.NULL)
            taskMemory?.taskContract?.let { put("taskContract", it.toJson()) }
            put("loopSignals", JSONObject().apply {
                put("agentSessionId", cleanSessionId)
                put("loopIndex", cleanRecentActionLines.size)
                put("executedStepCount", executedActionSignatures.size)
                put("screenChangedCount", screenChangedCount)
                put("explorationSprawlCount", explorationSprawlCount)
                put("explorationPressureLevel", explorationPressureLevel)
                put("explorationBudgetRemaining", explorationBudgetRemaining)
                put("explorationBudgetExceeded", explorationBudgetExceeded)
                put("noProgressCount", noProgressCount)
                put("structuralFailureCount", structuralFailureCount)
                put("localVisualRetryCount", localVisualRetryCount)
                put("sameActionCount", sameActionCount)
                put("lastResultOk", lastResultOk ?: JSONObject.NULL)
                put("lastVerification", lastVerification)
                put("finishVerificationRequested", finishVerificationRequested)
                put("localVisualRetryRequested", localVisualRetryRequested)
                put("visualReplanRequested", guiPlusReplanRequested)
                put("guiPlusReplanRequested", guiPlusReplanRequested)
                put("routeRefreshRequested", routeRefreshRequested)
                put("invalidateCachedAgentBrainRoute", routeRefreshRequested)
                put("guiPlusEligible", guiPlusEligible)
                put("observationId", resolvedRuntimeContext.observationId)
                put("routeEpoch", resolvedRuntimeContext.routeEpoch)
                put("surfaceEpoch", resolvedRuntimeContext.surfaceEpoch)
                put("lastActionSignature", lastActionSignature)
                put("currentPackageMatchesVerifiedTarget", currentPackageMatchesVerifiedTarget)
                put("postActionFeedback", executionFeedback)
                put("lastToolResponse", lastToolResponse)
                mergeTaskMemorySummary(taskMemory)
            })
        })
        put("visualHistory", JSONArray().apply {
            visualHistory.takeLast(historyLimit)
                .filter { it.assistantOutput.isNotBlank() || it.executionResult.isNotBlank() }
                .forEach { item ->
                    put(JSONObject().apply {
                        put("assistantOutput", item.assistantOutput.take(VISUAL_AGENT_MAX_HISTORY_OUTPUT_CHARS))
                        put("executionResult", item.executionResult.take(VISUAL_AGENT_MAX_HISTORY_RESULT_CHARS))
                    })
                }
        })
        put("appContext", canonicalApps)
        put("deviceContext", JSONObject().apply {
            put("schema", "android_visual_agent_context_v9_task_contract_harness")
            put("deviceProfile", deviceProfileJson ?: JSONObject.NULL)
            put("appIdentityProtocol", VisualAgentProtocol.appIdentityProtocol)
            put("appInventoryHash", appInventoryHash)
            put("appCatalogEntryCount", canonicalApps.length())
            put("appCatalogEntriesField", "appContext")
            put("appSelectionProtocol", appSelectionProtocol)
            put("runtimeExecutionContext", runtimeExecutionContext)
            put("surfaceContext", surfaceContext)
            put("currentApp", JSONObject().apply {
                put("packageName", snapshot.packageName)
                put("matchesVerifiedTarget", currentPackageMatchesVerifiedTarget)
            })
            put("screen", JSONObject().apply {
                put("widthPx", visual?.displayWidth ?: 0)
                put("heightPx", visual?.displayHeight ?: 0)
                put("coordinateProtocol", VisualAgentProtocol.coordinateProtocol)
                put("observationId", resolvedRuntimeContext.observationId)
            })
            put("installedAppCount", canonicalApps.length())
            put("uploadedAppCount", canonicalApps.length())
            put("installedAppsTruncated", appContext.size > canonicalApps.length())
        })
        put("coordinateProtocol", VisualAgentProtocol.coordinateProtocol)
        put("supportedAgentSteps", JSONArray(VisualAgentProtocol.supportedStepTypes.toList()))
        put("supportedDeviceTools", JSONArray(CloudAgentStep.deviceToolTypes.toList()))
        put("supportsAgentStepBatch", false)
        put("actionBatchMax", 1)
        put("hasScreenshot", visual != null)
        put("hasImage", visual != null)
        put("hasImages", visual != null)
        put("imageCount", if (visual != null) 1 else 0)
        visual?.let { item ->
            put("screenshot", JSONObject().apply {
                put("mimeType", item.mimeType)
                put("base64Data", item.base64Jpeg)
                put("width", item.width)
                put("height", item.height)
                put("displayWidth", item.displayWidth)
                put("displayHeight", item.displayHeight)
                put("source", item.source)
                put("reason", item.reason)
                put("observationId", resolvedRuntimeContext.observationId)
            })
        }
        put("responseFormat", JSONObject().apply {
            put("type", "json_object")
            put("includeAgentState", true)
            put("includeAgentStep", true)
            put("includeAgentSteps", true)
            put("includeStopConditions", true)
            put("includePerformanceDebug", true)
            put("includeAppCatalogAck", true)
            put("includeTaskContract", true)
            put("echoObservationId", true)
        })
        put("client", "android-compose")
        put("clientVersion", "task-contract-harness-v1")
        put("now", System.currentTimeMillis())
    }
}

private fun JSONObject.mergeTaskMemorySummary(memory: VisualTaskMemory?) {
    if (memory == null) return
    put("currentMilestoneId", memory.currentMilestoneId)
    put("completedMilestoneIds", JSONArray(memory.completedMilestoneIds))
    put("failedHypotheses", JSONArray().apply { memory.failedHypotheses.forEach { put(it.toJson()) } })
    put("blockedActions", JSONArray().apply { memory.blockedActions.forEach { put(it.toJson()) } })
    put("remainingExplorationBudget", memory.remainingExplorationBudget)
    put("lastConfirmedPage", memory.lastConfirmedPage?.toJson() ?: JSONObject.NULL)
    put("semanticProgressStatus", memory.progressStatus)
    put("semanticReplanRequested", memory.replanRequested)
    put("legacyTaskContract", memory.legacyMode)
}

private fun VisualTaskMemory?.orEmptyBlockedSignatures(): List<String> {
    val memory = this ?: return emptyList()
    return memory.blockedActions.map { it.actionCluster } + memory.failedHypotheses.map { it.actionCluster }
}

private data class CanonicalVisualApp(
    val appRef: String,
    val label: String,
    val packageName: String,
    val aliases: List<String>,
    val capabilities: List<String>,
)

private fun buildVisualAppInventoryHash(items: List<CanonicalVisualApp>): String {
    val canonical = items.sortedBy { it.packageName }.joinToString("\n") { item ->
        listOf(item.packageName, item.label, item.aliases.sorted().joinToString(","), item.capabilities.sorted().joinToString(",")).joinToString("|")
    }
    return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }.take(24)
}

private fun AgentDeviceProfile.toVisualAgentJson(): JSONObject = JSONObject().apply {
    put("schema", "android_device_profile_v1")
    put("manufacturer", manufacturer.take(60))
    put("brand", brand.take(60))
    put("model", model.take(80))
    put("androidRelease", release.take(40))
    put("sdkInt", sdkInt)
    put("buildDisplay", display.take(100))
}

private fun buildInteractionHistory(recentActions: List<String>): JSONArray {
    val turns = recentActions.mapNotNull { line ->
        when {
            line.startsWith("guiPlusQuestion:") -> "assistant" to line.substringAfter("guiPlusQuestion:")
            line.startsWith("userReply:") -> "user" to line.substringAfter("userReply:")
            line.startsWith("userInstruction:") -> "user" to line.substringAfter("userInstruction:")
            else -> null
        }
    }.takeLast(VISUAL_AGENT_MAX_INTERACTION_ITEMS)
    return JSONArray().apply {
        turns.forEachIndexed { index, (role, content) ->
            val clean = content.trim().take(VISUAL_AGENT_MAX_INTERACTION_CHARS)
            if (clean.isNotBlank()) put(JSONObject().apply {
                put("index", index)
                put("role", role)
                put("content", clean)
                put("sensitiveRedacted", clean.contains("敏感输入") || clean.contains("private_step"))
            })
        }
    }
}

private fun String.isVisualRuntimeFeedback(): Boolean {
    val value = lowercase()
    return value.contains(":failed:") || value.contains(":retry:") ||
        value.contains("visual_action_rejected") || value.contains("visual_action_retry") ||
        value.contains("visual_action_stale") || value.contains("visual_local_retry") ||
        value.contains("visual_exploration_sprawl") || value.contains("visual_no_progress") ||
        value.contains("visual_screen_changed") || value.contains("visual_replan_requested") ||
        value.contains("finish_verification_pending") || value.contains("open_app_package_verification_failed") ||
        value.contains("controller_selection_rejected") || value.contains("no_progress") ||
        value.contains("no progress") || value.contains("same screen") || value.contains("没有变化") ||
        value.contains("未生效") || value.contains("重复循环") || value.contains("blocked")
}

private fun String.isVisualNoProgressFeedback(): Boolean {
    val value = lowercase()
    return value.contains("visual_no_progress") || value.contains("semanticstatus=stalled") ||
        value.contains("no_progress") || value.contains("no progress") || value.contains("same screen") ||
        value.contains("没有变化") || value.contains("未生效") || value.contains("重复循环")
}

private fun String.isVisualAdvancedFeedback(): Boolean {
    val value = lowercase()
    return value.contains("visual_screen_changed") || value.contains("visual_progress")
}

private fun String.isVisualExplorationSprawlFeedback(): Boolean = lowercase().contains("visual_exploration_sprawl")
private fun String.isVisualFinishVerificationFeedback(): Boolean = lowercase().contains("finish_verification_pending")

private fun String.isVisualFailureFeedback(): Boolean {
    val value = lowercase()
    return value.contains(":failed:") || value.contains("visual_action_rejected") ||
        value.contains("visual_action_retry") || value.contains("visual_action_stale") ||
        value.contains("visual_local_retry") || value.contains("semanticstatus=regressed") ||
        value.contains("open_app_package_verification_failed") || value.contains("controller_selection_rejected") ||
        value.contains("blocked") || value.contains("执行失败")
}

private fun String.isStructuralRouteFailureFeedback(): Boolean {
    val value = lowercase()
    return value.contains("failureclass=structural_route") || value.contains("open_app_package_verification_failed") ||
        value.contains("controller_selection_rejected") || value.contains("visual_action_rejected")
}

private fun String.isLocalVisualRetryFeedback(): Boolean {
    val value = lowercase()
    return value.contains("failureclass=visual_local") || value.contains("visual_action_retry") ||
        value.contains("visual_action_stale") || value.contains("visual_exploration_sprawl") ||
        value.contains("visual_local_retry") || value.contains("visual_replan_requested") || value.contains(":retry:")
}

private fun String.visualResultOkOrNull(): Boolean? {
    val value = lowercase()
    return when {
        value.contains(":failed:") || value.contains(":retry:") || value.contains("visual_action_rejected") ||
            value.contains("visual_action_retry") || value.contains("visual_action_stale") ||
            value.contains("执行失败") || value.contains("verification_failed") -> false
        value.contains(":ok:") || value.contains("package_verified") -> true
        else -> null
    }
}

private fun String.visualActionSignatureOrNull(): String? {
    val clean = trim()
    val signature = when {
        ":failed:" in clean -> clean.substringBefore(":failed:")
        ":retry:" in clean -> clean.substringBefore(":retry:")
        ":ok:" in clean -> clean.substringBefore(":ok:")
        clean.startsWith("visual_action_rejected:") -> clean.substringAfter("action=", clean.substringAfter("type=")).substringBefore("|")
        clean.startsWith("visual_action_retry:") -> clean.substringAfter("action=", clean.substringAfter("type=")).substringBefore("|")
        clean.startsWith("visual_action_stale:") -> clean.substringAfter("type=").substringBefore("|")
        clean.startsWith("visual_local_retry:") -> clean.substringAfter("action=").substringBefore(":count=")
        clean.startsWith("visual_no_progress:") -> clean.substringAfter("visual_no_progress:").substringBefore(":count=")
        clean.startsWith("visual_screen_changed:") -> clean.substringAfter("visual_screen_changed:").substringBefore(":screen=")
        clean.startsWith("finish_verification_pending:") -> "finish"
        clean.startsWith("open_app_package_verification_failed:") -> "open_app"
        clean.startsWith("open_app_package_verified:") -> "open_app"
        else -> Regex("(?:tap@\\d+,\\d+|tap_node@[^\\s，。；;:：]+|open@[^\\s，。；;:：]+|input@[^\\s，。；;:：]+|scroll@[a-z]+|swipe@[a-z]+|back|home|recents|notifications|quick_settings)")
            .find(clean)?.value
    }
    return signature?.trim()?.take(160)?.takeIf(String::isNotBlank)
}

@Throws(IOException::class)
internal fun validateVisualAgentResponseObservationId(
    expectedObservationId: String,
    data: JSONObject?,
): String {
    val expected = expectedObservationId.trim()
    if (expected.isBlank()) throw IOException("visual_agent_step request is missing expectedActionObservationId")
    if (data == null) throw IOException("visual_agent_step returned invalid JSON without an observationId")
    val envelopes = buildList {
        add(data)
        data.optJSONObject("data")?.let(::add)
        data.optJSONObject("result")?.let(::add)
        data.optJSONObject("agentPlan")?.let(::add)
    }
    val echoedIds = linkedSetOf<String>()
    envelopes.forEach { envelope ->
        listOf("expectedActionObservationId", "actionObservationId", "observationId")
            .map { envelope.optString(it).trim() }.filterTo(echoedIds, String::isNotBlank)
        envelope.optJSONObject("verifiedSurfaceProtocol")?.optString("observationId")?.trim()
            ?.takeIf(String::isNotBlank)?.let(echoedIds::add)
        envelope.optJSONObject("runtimeExecutionContext")?.optString("observationId")?.trim()
            ?.takeIf(String::isNotBlank)?.let(echoedIds::add)
    }
    if (echoedIds.isEmpty()) throw IOException("visual_agent_step response did not echo expectedActionObservationId")
    if (echoedIds.size != 1) throw IOException("visual_agent_step response contains conflicting observationIds")
    val actual = echoedIds.single()
    if (actual != expected) throw IOException("visual_agent_step returned a stale observationId")
    return actual
}

private fun AiWorkerClient.postVisualAgentStep(
    endpoint: String,
    payload: JSONObject,
    deviceId: String,
    agentSessionId: String,
): CloudAgentPlan {
    val requestStart = SystemClock.elapsedRealtime()
    var requestByteCount = 0
    var terminalDiagnosticRecorded = false
    val diagnostics = VisualIntelligenceDiagnosticsStore.currentOrNull()
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = VISUAL_AGENT_CONNECT_TIMEOUT_MS
        readTimeout = VISUAL_AGENT_READ_TIMEOUT_MS
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("X-Client", "android-compose-visual-agent-v14-task-contract")
        setRequestProperty("X-Client-Id", deviceId.take(120))
        setRequestProperty("X-Device-Id", deviceId.take(120))
        setRequestProperty("X-Agent-Session-Protocol", VISUAL_AGENT_SESSION_PROTOCOL)
        setRequestProperty("X-Agent-Session-Id", agentSessionId.take(120))
        AiWorkerRequestIdentity.applyTo(
            connection = this,
            appClientToken = AiWorkerRequestIdentity.defaultAppClientToken(),
            mode = AiWorkerIdentityMode.AppOnly,
        )
    }
    return try {
        val requestBytes = payload.toString().toByteArray(Charsets.UTF_8)
        requestByteCount = requestBytes.size
        connection.outputStream.use { it.write(requestBytes) }
        val status = connection.responseCode
        val body = connection.visualAgentReadBody(status)
        val responseByteCount = body.toByteArray(Charsets.UTF_8).size
        val data = body.visualAgentJsonOrNull()
        val workerVersion = connection.getHeaderField("X-AI-Ledger-Worker-Version").orEmpty().take(48)
        val routeProtocol = connection.getHeaderField("X-AI-Ledger-Route-Protocol").orEmpty().take(48)
        val durationMs = SystemClock.elapsedRealtime() - requestStart
        AgentRuntimeController.noteDiagnostic(buildString {
            append("VisualDirect q=").append(visualAgentBytesToKb(requestBytes.size)).append("K")
            append(" r=").append(visualAgentBytesToKb(responseByteCount)).append("K")
            append(" h=").append(durationMs)
            if (workerVersion.isNotBlank()) append(" w=").append(workerVersion)
            if (routeProtocol.isNotBlank()) append(" p=").append(routeProtocol)
        })

        if (status !in 200..299) {
            diagnostics?.recordModelTransportResponse(
                httpStatus = status,
                body = body,
                requestBytes = requestByteCount,
                responseBytes = responseByteCount,
                durationMs = durationMs,
                workerVersion = workerVersion,
                routeProtocol = routeProtocol,
                parseOutcome = "http_error",
                parsedStepType = "",
                observationIdValid = false,
            )
            terminalDiagnosticRecorded = true
            throw parseVisualAgentHttpFailure(status, body)
        }

        val observationValidation = runCatching {
            validateVisualAgentResponseObservationId(payload.optString("expectedActionObservationId"), data)
        }
        if (observationValidation.isFailure) {
            diagnostics?.recordModelTransportResponse(
                httpStatus = status,
                body = body,
                requestBytes = requestByteCount,
                responseBytes = responseByteCount,
                durationMs = durationMs,
                workerVersion = workerVersion,
                routeProtocol = routeProtocol,
                parseOutcome = "observation_id_invalid",
                parsedStepType = "",
                observationIdValid = false,
            )
            terminalDiagnosticRecorded = true
            throw (observationValidation.exceptionOrNull() as? IOException
                ?: IOException("visual_agent_step observation validation failed", observationValidation.exceptionOrNull()))
        }

        val plan = CloudAgentPlan.fromJson(data)
            ?: CloudAgentStep.fromJson(data)?.let { CloudAgentPlan(step = it, state = CloudAgentState.fromJson(data)) }
        if (plan == null) {
            diagnostics?.recordModelTransportResponse(
                httpStatus = status,
                body = body,
                requestBytes = requestByteCount,
                responseBytes = responseByteCount,
                durationMs = durationMs,
                workerVersion = workerVersion,
                routeProtocol = routeProtocol,
                parseOutcome = if (data == null) "invalid_json" else "agent_step_missing",
                parsedStepType = "",
                observationIdValid = true,
            )
            terminalDiagnosticRecorded = true
            throw IOException("visual_agent_step did not return one agentStep")
        }

        diagnostics?.recordModelTransportResponse(
            httpStatus = status,
            body = body,
            requestBytes = requestByteCount,
            responseBytes = responseByteCount,
            durationMs = durationMs,
            workerVersion = workerVersion,
            routeProtocol = routeProtocol,
            parseOutcome = "ok",
            parsedStepType = plan.step.type,
            observationIdValid = true,
        )
        terminalDiagnosticRecorded = true
        plan
    } catch (error: SocketTimeoutException) {
        if (!terminalDiagnosticRecorded) {
            diagnostics?.recordModelTransportFailure(
                code = "network_timeout",
                message = error.message.orEmpty(),
                durationMs = SystemClock.elapsedRealtime() - requestStart,
                requestBytes = requestByteCount,
            )
        }
        throw VisualAgentRequestException(
            httpStatus = null,
            code = "network_timeout",
            retryable = true,
            backendMessage = "visual_agent_step timed out after ${VISUAL_AGENT_READ_TIMEOUT_MS / 1000}s",
            cause = error,
        )
    } catch (error: IOException) {
        if (!terminalDiagnosticRecorded) {
            diagnostics?.recordModelTransportFailure(
                code = (error as? VisualAgentRequestException)?.code ?: "io_error",
                message = error.message.orEmpty(),
                durationMs = SystemClock.elapsedRealtime() - requestStart,
                requestBytes = requestByteCount,
            )
        }
        throw error
    } finally {
        connection.disconnect()
    }
}

internal fun parseVisualAgentHttpFailure(status: Int, body: String): VisualAgentRequestException {
    val data = body.visualAgentJsonOrNull()
    val errorObject = data?.optJSONObject("error")
    val code = listOfNotNull(
        data?.visualAgentString("code"), errorObject?.visualAgentString("code"),
        data?.visualAgentString("errorCode"), errorObject?.visualAgentString("errorCode"),
    ).firstOrNull(String::isNotBlank) ?: "http_$status"
    val message = listOfNotNull(
        data?.visualAgentString("message"), errorObject?.visualAgentString("message"),
        data?.visualAgentString("error"), errorObject?.visualAgentString("error"), body.trim().take(240),
    ).firstOrNull(String::isNotBlank) ?: "visual_agent_step HTTP $status"
    val explicitRetryable = when {
        data?.has("retryable") == true -> data.optBoolean("retryable")
        errorObject?.has("retryable") == true -> errorObject.optBoolean("retryable")
        else -> null
    }
    return VisualAgentRequestException(
        httpStatus = status,
        code = code.take(120),
        retryable = explicitRetryable ?: status in RETRYABLE_VISUAL_AGENT_HTTP_STATUSES,
        backendMessage = message.take(320),
    )
}

private fun buildVisualAgentErrorMessage(
    httpStatus: Int?,
    code: String,
    retryable: Boolean,
    backendMessage: String,
): String = buildString {
    append(backendMessage.ifBlank { "visual_agent_step failed" }).append(" [")
    httpStatus?.let { append("HTTP ").append(it).append(", ") }
    append("code=").append(code.ifBlank { "unknown" })
    append(", retryable=").append(retryable).append(']')
}

private fun JSONObject.visualAgentString(key: String): String? =
    (opt(key) as? String)?.trim()?.takeIf(String::isNotBlank)

private fun HttpURLConnection.visualAgentReadBody(status: Int): String {
    val stream = if (status in 200..299) inputStream else errorStream
    return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
}

private fun String.visualAgentJsonOrNull(): JSONObject? =
    try { takeIf(String::isNotBlank)?.let(::JSONObject) } catch (_: Exception) { null }

private fun visualAgentBytesToKb(bytes: Int): Int = if (bytes <= 0) 0 else (bytes + 1023) / 1024
private val RETRYABLE_VISUAL_AGENT_HTTP_STATUSES = setOf(408, 425, 429, 500, 502, 503, 504)

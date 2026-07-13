package com.yuchen.ailedger.service

import com.yuchen.ailedger.AiLedgerApplication
import com.yuchen.ailedger.data.AssistantMemoryCompiler
import com.yuchen.ailedger.data.AssistantMemoryRepository
import com.yuchen.ailedger.data.AssistantMemoryRequestContextRuntime
import com.yuchen.ailedger.model.ChatAttachment
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
import com.yuchen.ailedger.ui.InlineStickerDisplaySettings
import org.json.JSONArray
import org.json.JSONObject

private const val CLIENT_TOOL_RESULT_MARKER = "[[AI_LEDGER_CLIENT_TOOL_RESULT_V1]]"
private const val AI_WORKER_HISTORY_LIMIT = 24
private const val TOOL_EXECUTION_POLICY_SCHEMA = "ai_ledger_tool_execution_policy_v1"
private const val TOOL_EXECUTION_POLICY_AUTO = "auto"
private const val TOOL_EXECUTION_POLICY_REQUIRED_SPECIFIC = "required_specific"
private const val TOOL_EXECUTION_POLICY_NONE = "none"
private const val TOOL_COMPUTER_RUN_TASK = "computer_run_task"
private const val MESSAGE_CONTENT_BLOCK_SCHEMA = "ai_ledger_message_content_blocks_v1"
private const val PROJECT_WORKSPACE_CAPABILITY_SCHEMA = "ai_ledger_android_project_workspace_v1"
private const val PROJECT_WORKSPACE_CLIENT_VERSION = "compose-native-project-workspace-v3-thread-scoped"
private const val CONVERSATION_ID_PREFIX = "chat_"
private val MESSAGE_CONTENT_BLOCK_TYPES = listOf(
    "rich_text",
    "code",
    "table",
    "chart",
    "image",
    "image_gallery",
    "key_value",
    "callout",
    "action_group",
)
private val PROJECT_CLIENT_TOOL_NAMES = listOf(
    "project_create",
    "project_list",
    "project_get",
    "project_list_files",
    "project_read_file",
    "project_write_files",
    "project_apply_edits",
    "project_delete_files",
    "project_validate",
    "project_build_preview",
    "project_list_revisions",
    "project_rollback",
)

private object InstalledAppsPayloadJsonCache {
    private val lock = Any()
    private var source: List<InstalledAppEntry>? = null
    private var payload: JSONArray? = null

    fun get(apps: List<InstalledAppEntry>): JSONArray = synchronized(lock) {
        val cached = payload
        if (source === apps && cached != null) return@synchronized cached

        JSONArray().apply {
            apps.asSequence()
                .take(AI_WORKER_NORMAL_CHAT_DEVICE_PROBE_MAX_APPS)
                .forEach { app ->
                    put(JSONObject().apply {
                        put("label", app.label)
                        put("packageName", app.packageName)
                    })
                }
        }.also { next ->
            source = apps
            payload = next
        }
    }
}

internal object AiWorkerPayloadBuilder {
    fun build(
        messages: List<ChatMessage>,
        route: AiWorkerModelRoute,
        onlineEnabled: Boolean,
        resolvedClientId: String,
    ): JSONObject {
        messages.clientToolResultReceiptOrNull()?.let { receipt ->
            AssistantMemoryRequestContextRuntime.clearCurrentThread()
            AiLedgerApplication.contextOrNull()?.let { appContext ->
                runCatching { ClientToolReceiptDeliveryRuntime.enqueue(appContext, receipt) }
            }
            return buildClientToolResultPayload(receipt, route, resolvedClientId)
        }
        val latestUserContext = messages.latestUserContext()
        val latestUserText = latestUserContext.latestText
        val conversationId = messages.conversationId()
        val imageArray = latestUserContext.imageAttachments.toImageJsonArray()
        val hasImage = imageArray.length() > 0
        val agentModeSnapshot = AgentRuntimeController.isEnabled()
        val visualAgentModeEnabled = !hasImage && agentModeSnapshot
        val workspaceModeEnabled = !hasImage && AgentWorkspaceModeController.isEnabled()
        val requestId = java.util.UUID.randomUUID().toString()
        val toolExecutionPolicy = JSONObject().apply {
            put("schema", TOOL_EXECUTION_POLICY_SCHEMA)
            when {
                hasImage -> {
                    put("mode", TOOL_EXECUTION_POLICY_NONE)
                    put("source", "vision_attachment")
                }
                visualAgentModeEnabled -> {
                    put("mode", TOOL_EXECUTION_POLICY_REQUIRED_SPECIFIC)
                    put("requiredTool", TOOL_COMPUTER_RUN_TASK)
                    put("source", "agent_mode")
                }
                else -> {
                    put("mode", TOOL_EXECUTION_POLICY_AUTO)
                    put("source", "normal_chat")
                }
            }
            put("messageId", latestUserContext.messageId.ifBlank { requestId })
            if (conversationId.isNotBlank()) put("conversationId", conversationId)
        }
        val appContext = AiLedgerApplication.contextOrNull()
        val activeProject = if (!hasImage && !visualAgentModeEnabled) {
            ProjectWorkspaceSessionContext.current(appContext, conversationId)
        } else {
            null
        }
        val memoryCompilation = appContext
            ?.let { context ->
                runCatching {
                    AssistantMemoryCompiler.compile(
                        userText = latestUserText,
                        memoryState = AssistantMemoryRepository.get(context).state.value,
                    )
                }.getOrElse {
                    AssistantMemoryCompiler.compileBackendOwned(latestUserText)
                }
            }
            ?: AssistantMemoryCompiler.compileBackendOwned(latestUserText)
        val stickerPreferences = InlineStickerDisplaySettings.currentExpressionPreferences(appContext)
        val installedAppsJson = if (hasImage) {
            JSONArray()
        } else {
            appContext
                ?.let { context ->
                    InstalledAppsPayloadJsonCache.get(
                        InstalledAppIndex(context).getLaunchableApps(),
                    )
                }
                ?: JSONArray()
        }
        val selectedModelId = when {
            hasImage -> AI_WORKER_QWEN_VISION_ROUTE_ID
            route.isAuto -> "auto"
            else -> route.resolved.id
        }
        val supportedAgentActions = if (visualAgentModeEnabled) {
            listOf("run_agent_task", "observe_screen")
        } else {
            listOf("run_device_control", "run_agent_task", "observe_screen")
        }
        val supportedDeviceSteps = if (visualAgentModeEnabled) {
            emptyList()
        } else {
            AI_WORKER_NORMAL_CHAT_DEVICE_TOOL_TYPES
        }
        val supportedMobileActions = if (visualAgentModeEnabled) emptyList() else listOf("set_alarm", "navigate")
        val supportedPreferenceUpdates = if (visualAgentModeEnabled) emptyList() else listOf("navigation_address")
        val searchMode = if (onlineEnabled) "auto" else "off"

        return JSONObject().apply {
            put("requestId", requestId)
            put("action", "chat")
            put("intent", if (hasImage) "vision_chat" else "chat")
            if (conversationId.isNotBlank()) {
                put("conversationId", conversationId)
                put("sessionId", conversationId)
                put("chatThreadId", conversationId)
            }
            put("messages", messages.toWorkerMessages())
            put("message", latestUserText)
            put("toolExecutionPolicy", toolExecutionPolicy)
            put("memoryMode", memoryCompilation.requestMode)
            put("memoryEnabled", memoryCompilation.memoryRequested)
            put("memoryRequest", memoryCompilation.diagnosticsJson())
            put("workspaceModeEnabled", workspaceModeEnabled)
            put("agentWorkspaceMode", if (workspaceModeEnabled) "workspace" else "classic")
            put("agentProgressStream", workspaceModeEnabled)
            put("workspaceProgressStream", workspaceModeEnabled)
            put("projectWorkspaceEnabled", !hasImage && !visualAgentModeEnabled)
            put("projectWorkspaceSchema", PROJECT_WORKSPACE_CAPABILITY_SCHEMA)
            activeProject?.let { put("activeProject", JSONObject(it.toString())) }
            put("visualDecisionOwner", "gui_plus_exclusive")
            put("visualAgentBrainEnabled", false)
            put("visualRouteMode", "gui_plus_exclusive")
            put("computerUseOwner", "gui_plus")

            put("chatExpressionPreferences", JSONObject().apply {
                put("schema", "ai_ledger_chat_expression_preferences_v1")
                put("inlineStickerFrequency", stickerPreferences.frequency)
                put("inlineStickerIntensity", stickerPreferences.intensity)
                put("inlineStickerMaxPerReply", stickerPreferences.maxPerReply)
                put("inlineStickerRepeatCount", stickerPreferences.repeatCount)
            })

            put("modelPreference", selectedModelId)
            put("requestedModelPreference", selectedModelId)
            put("originalModelPreference", route.requested.id)
            put("autoRequested", route.isAuto)
            put("autoRouteAuthority", AI_WORKER_AUTO_ROUTE_AUTHORITY)
            if (workspaceModeEnabled) {
                put("workspaceToolLoop", "cloud_controlled_multi_step")
                put("workspaceDecisionOwner", "cloud_workspace_agent")
            }
            if (visualAgentModeEnabled) {
                put("agentModeEnabled", true)
                put("forceVisualAgent", true)
                put("agentToolDomain", "visual_only")
            }

            put("hasImage", hasImage)
            put("imageCount", imageArray.length())
            if (hasImage) put("images", imageArray)

            put("onlineEnabled", onlineEnabled)
            put("webSearchMode", searchMode)
            put("webSearch", JSONObject().apply {
                put("mode", searchMode)
                put("enabled", onlineEnabled)
                put("force", false)
            })

            put("commandProtocol", JSONObject().apply {
                put("version", AI_WORKER_CHAT_PROTOCOL_VERSION)
                put("client", AI_WORKER_CHAT_CLIENT_NAME)
                put("decisionOwner", if (workspaceModeEnabled) "cloud_workspace_agent" else "cloud_final_chat_model")
                put("executionOwner", "android_structured_tool_executor")
                put("clientToolCallSchema", AI_WORKER_CLIENT_TOOL_CALL_SCHEMA)
                put("clientToolResultProtocol", AI_WORKER_CLIENT_TOOL_RESULT_PROTOCOL)
                put("toolExecutionPolicySchema", TOOL_EXECUTION_POLICY_SCHEMA)
                put("workspaceMode", if (workspaceModeEnabled) "workspace" else "classic")
                put("workspaceModeEnabled", workspaceModeEnabled)
                put("projectWorkspaceSchema", PROJECT_WORKSPACE_CAPABILITY_SCHEMA)
                put("projectExecutionOwner", "android_local_project_workspace")
                put("projectVerificationSchema", AGENT_ARTIFACT_VERIFICATION_SCHEMA)
                if (conversationId.isNotBlank()) put("conversationId", conversationId)
                activeProject?.let {
                    put("activeProjectId", it.optString("projectId"))
                    put("activeProjectRevisionId", it.optString("currentRevisionId"))
                }
                put("visualDecisionOwner", "gui_plus_exclusive")
                put("visualAgentBrainEnabled", false)
                put("visualRouteMode", "gui_plus_exclusive")
                put("computerUseOwner", "gui_plus")
                if (visualAgentModeEnabled) put("agentToolDomain", "visual_only")
            })
            put("clientCapabilities", JSONObject().apply {
                put("schema", "ai_ledger_android_client_capabilities_v4")
                put("agentActions", JSONArray(supportedAgentActions))
                put("deviceTools", JSONArray(supportedDeviceSteps))
                put("mobileActions", JSONArray(supportedMobileActions))
                put("preferenceUpdates", JSONArray(supportedPreferenceUpdates))
                put("installedApps", installedAppsJson)
                put("workspaceModeToggle", true)
                put("workspaceModeEnabled", workspaceModeEnabled)
                put("visualDecisionOwner", "gui_plus_exclusive")
                put("visualAgentBrainEnabled", false)
                put("visualRouteMode", "gui_plus_exclusive")
                put("computerUseOwner", "gui_plus")
                put("messageContentBlockSchema", MESSAGE_CONTENT_BLOCK_SCHEMA)
                put("messageContentBlocks", JSONArray(MESSAGE_CONTENT_BLOCK_TYPES))
                put("projectTools", JSONArray(PROJECT_CLIENT_TOOL_NAMES))
                put("projectWorkspace", JSONObject().apply {
                    put("schema", PROJECT_WORKSPACE_CAPABILITY_SCHEMA)
                    put("enabled", !hasImage && !visualAgentModeEnabled)
                    put("projectType", "static_web")
                    put("frameworks", JSONArray(listOf("html_css_javascript")))
                    put("tools", JSONArray(PROJECT_CLIENT_TOOL_NAMES))
                    put("executionOwner", "android_local_project_workspace")
                    put("previewMode", "isolated_local_webview")
                    put("networkPolicy", "blocked")
                    put("revisioning", true)
                    put("optimisticRevisionLock", true)
                    put("verificationSchema", AGENT_ARTIFACT_VERIFICATION_SCHEMA)
                    put("deterministicValidation", true)
                    put("previewRequiresValidation", true)
                    put("conversationScoped", true)
                    if (conversationId.isNotBlank()) put("conversationId", conversationId)
                    activeProject?.let { put("activeProject", JSONObject(it.toString())) }
                })
            })
            put("responseFormat", JSONObject().apply {
                put("includeSources", true)
                put("includeStructuredData", true)
                put("includeContentBlocks", true)
                put("contentBlockSchema", MESSAGE_CONTENT_BLOCK_SCHEMA)
                put("supportedContentBlockTypes", JSONArray(MESSAGE_CONTENT_BLOCK_TYPES))
                put("contentBlockPlacement", "supplementary_after_reply")
                put("includeClientToolCall", true)
                put("includeEmbeddedCommandMarker", false)
                put("includeAgentProgress", workspaceModeEnabled)
                put("projectPreviewLinkProtocol", "https://project.ai-ledger.local/open")
                if (workspaceModeEnabled || visualAgentModeEnabled) put("deferClientToolReply", true)
            })

            put("client", AI_WORKER_CHAT_CLIENT_NAME)
            put("clientId", resolvedClientId)
            put("deviceId", resolvedClientId)
            put("clientVersion", PROJECT_WORKSPACE_CLIENT_VERSION)
            put("now", System.currentTimeMillis())
        }
    }

    private fun buildClientToolResultPayload(
        receipt: JSONObject,
        route: AiWorkerModelRoute,
        resolvedClientId: String,
    ): JSONObject {
        val selectedModelId = if (route.isAuto) "auto" else route.resolved.id
        val workspaceModeEnabled = AgentWorkspaceModeController.isEnabled()
        val appContext = AiLedgerApplication.contextOrNull()
        val conversationId = receipt.clientConversationId()
        val activeProject = receipt.optJSONObject("project")
            ?.also { project ->
                ProjectWorkspaceSessionContext.update(appContext, conversationId, project)
            }
            ?: ProjectWorkspaceSessionContext.current(appContext, conversationId)
        val workspaceId = receipt.optString("workspaceId")
            .ifBlank { receipt.optString("agentWorkspaceId") }
            .trim()
        val workspaceSchema = receipt.optString("workspaceSchema")
            .ifBlank { receipt.optString("agentWorkspaceSchema") }
            .trim()
        return JSONObject().apply {
            put("requestId", java.util.UUID.randomUUID().toString())
            put("action", "internal_control_report")
            put("intent", "internal_control_report")
            put("mode", "internal_control_report")
            if (conversationId.isNotBlank()) {
                put("conversationId", conversationId)
                put("sessionId", conversationId)
                put("chatThreadId", conversationId)
            }
            put("message", "client_tool_result:${receipt.optString("toolCallId")}")
            put("internalControlReceipt", JSONObject(receipt.toString()))
            if (workspaceId.isNotBlank()) {
                put("workspaceId", workspaceId)
                put("agentWorkspaceId", workspaceId)
            }
            if (workspaceSchema.isNotBlank()) {
                put("workspaceSchema", workspaceSchema)
                put("agentWorkspaceSchema", workspaceSchema)
            }
            put("workspaceModeEnabled", workspaceModeEnabled)
            put("agentWorkspaceMode", if (workspaceModeEnabled) "workspace" else "classic")
            put("agentProgressStream", workspaceModeEnabled)
            put("workspaceProgressStream", workspaceModeEnabled)
            put("projectWorkspaceEnabled", true)
            put("projectWorkspaceSchema", PROJECT_WORKSPACE_CAPABILITY_SCHEMA)
            put("projectVerificationSchema", AGENT_ARTIFACT_VERIFICATION_SCHEMA)
            put("projectTools", JSONArray(PROJECT_CLIENT_TOOL_NAMES))
            activeProject?.let { put("activeProject", JSONObject(it.toString())) }
            put("visualDecisionOwner", "gui_plus_exclusive")
            put("visualAgentBrainEnabled", false)
            put("visualRouteMode", "gui_plus_exclusive")
            put("computerUseOwner", "gui_plus")
            put("modelPreference", selectedModelId)
            put("requestedModelPreference", selectedModelId)
            put("resolvedFinalModel", selectedModelId)
            put("autoRequested", route.isAuto)
            put("client", AI_WORKER_CHAT_CLIENT_NAME)
            put("clientId", resolvedClientId)
            put("deviceId", resolvedClientId)
            put("clientVersion", PROJECT_WORKSPACE_CLIENT_VERSION)
        }
    }

    private fun List<ChatMessage>.clientToolResultReceiptOrNull(): JSONObject? {
        val message = singleOrNull() ?: return null
        if (message.role != MessageRole.User) return null
        val text = message.text.trim()
        if (!text.startsWith(CLIENT_TOOL_RESULT_MARKER)) return null
        val json = text.removePrefix(CLIENT_TOOL_RESULT_MARKER).trim()
        if (json.isBlank()) return null
        return runCatching { JSONObject(json) }.getOrNull()
    }

    private fun List<ChatMessage>.conversationId(): String {
        val firstUserId = firstOrNull { message ->
            message.role == MessageRole.User && message.id.isNotBlank()
        }?.id.orEmpty()
        val safeId = firstUserId
            .trim()
            .filter { char -> char.isLetterOrDigit() || char == '-' || char == '_' || char == '.' }
            .take(100)
        return safeId.takeIf(String::isNotBlank)?.let { CONVERSATION_ID_PREFIX + it }.orEmpty()
    }

    private fun JSONObject.clientConversationId(): String {
        val toolArguments = optJSONObject("toolArguments")
        return optString("conversationId")
            .ifBlank { optString("chatThreadId") }
            .ifBlank { toolArguments?.optString("clientConversationId").orEmpty() }
            .ifBlank { toolArguments?.optString("conversationId").orEmpty() }
            .trim()
            .take(180)
    }

    private data class LatestUserContext(
        val messageId: String,
        val latestText: String,
        val imageAttachments: List<ResolvedImageAttachment>,
    )

    private data class ResolvedImageAttachment(
        val attachment: ChatAttachment,
        val base64Data: String,
    )

    private fun List<ChatMessage>.latestUserContext(): LatestUserContext {
        var latestUserMessage: ChatMessage? = null
        var latestText = ""
        for (index in indices.reversed()) {
            val message = this[index]
            if (message.role != MessageRole.User || message.status == MessageStatus.Sending) continue
            if (latestUserMessage == null) latestUserMessage = message
            if (latestText.isBlank() && message.text.isNotBlank()) latestText = message.text
            if (latestUserMessage != null && latestText.isNotBlank()) break
        }
        val images = latestUserMessage
            ?.attachments
            .orEmpty()
            .mapNotNull { attachment ->
                if (!attachment.mimeType.startsWith("image/")) return@mapNotNull null
                val payload = attachment.base64Data
                payload.takeIf(String::isNotBlank)?.let {
                    ResolvedImageAttachment(attachment = attachment, base64Data = it)
                }
            }
        return LatestUserContext(
            messageId = latestUserMessage?.id.orEmpty(),
            latestText = latestText,
            imageAttachments = images,
        )
    }

    private fun List<ResolvedImageAttachment>.toImageJsonArray(): JSONArray = JSONArray().apply {
        forEach { resolved ->
            val attachment = resolved.attachment
            put(JSONObject().apply {
                put("id", attachment.id)
                put("type", "image")
                put("mimeType", attachment.mimeType)
                put("base64Data", resolved.base64Data)
                put("fileName", attachment.fileName.orEmpty())
                attachment.width?.let { put("width", it) }
                attachment.height?.let { put("height", it) }
                attachment.sizeBytes?.let { put("sizeBytes", it) }
            })
        }
    }

    private fun List<ChatMessage>.toWorkerMessages(): JSONArray {
        val recentReversed = ArrayList<ChatMessage>(AI_WORKER_HISTORY_LIMIT)
        for (index in indices.reversed()) {
            val message = this[index]
            val eligible = when (message.role) {
                MessageRole.User ->
                    (message.text.isNotBlank() || message.hasImageAttachments) &&
                        message.status != MessageStatus.Sending
                MessageRole.Assistant ->
                    message.text.isNotBlank() && message.status == MessageStatus.Sent
            }
            if (!eligible) continue
            recentReversed += message
            if (recentReversed.size >= AI_WORKER_HISTORY_LIMIT) break
        }

        var firstUserFound = false
        return JSONArray().apply {
            for (index in recentReversed.indices.reversed()) {
                val message = recentReversed[index]
                if (!firstUserFound) {
                    if (message.role != MessageRole.User) continue
                    firstUserFound = true
                }
                put(JSONObject().apply {
                    put("role", if (message.role == MessageRole.User) "user" else "assistant")
                    put("content", message.text)
                })
            }
        }
    }
}

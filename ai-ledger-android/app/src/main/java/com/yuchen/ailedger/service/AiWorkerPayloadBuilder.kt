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

internal object AiWorkerPayloadBuilder {
    fun build(
        messages: List<ChatMessage>,
        route: AiWorkerModelRoute,
        onlineEnabled: Boolean,
        resolvedClientId: String,
    ): JSONObject {
        messages.clientToolResultReceiptOrNull()?.let { receipt ->
            AssistantMemoryRequestContextRuntime.clearCurrentThread()
            return buildClientToolResultPayload(receipt, route, resolvedClientId)
        }
        val latestUserText = messages.latestUserText()
        val imageArray = messages.latestUserImageAttachments().toImageJsonArray()
        val hasImage = imageArray.length() > 0
        val agentModeEnabled = !hasImage && AgentRuntimeController.isEnabled()
        val appContext = AiLedgerApplication.contextOrNull()
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
        val installedApps = if (hasImage) {
            emptyList()
        } else {
            appContext
                ?.let { context -> InstalledAppIndex(context).getLaunchableApps() }
                .orEmpty()
                .take(AI_WORKER_NORMAL_CHAT_DEVICE_PROBE_MAX_APPS)
        }
        val selectedModelId = when {
            hasImage -> AI_WORKER_QWEN_VISION_ROUTE_ID
            route.isAuto -> "auto"
            else -> route.resolved.id
        }
        val supportedAgentActions = if (agentModeEnabled) {
            listOf("run_agent_task", "observe_screen")
        } else {
            listOf("run_device_control", "run_agent_task", "observe_screen")
        }
        val supportedDeviceSteps = if (agentModeEnabled) {
            emptyList()
        } else {
            AI_WORKER_NORMAL_CHAT_DEVICE_TOOL_TYPES
        }
        val supportedMobileActions = if (agentModeEnabled) emptyList() else listOf("set_alarm", "navigate")
        val supportedPreferenceUpdates = if (agentModeEnabled) emptyList() else listOf("navigation_address")
        val searchMode = if (onlineEnabled) "auto" else "off"

        return JSONObject().apply {
            put("requestId", java.util.UUID.randomUUID().toString())
            put("action", "chat")
            put("intent", if (hasImage) "vision_chat" else "chat")
            put("messages", messages.toWorkerMessages())
            put("message", latestUserText)
            put("memoryMode", memoryCompilation.requestMode)
            put("memoryEnabled", memoryCompilation.memoryRequested)
            put("memoryRequest", memoryCompilation.diagnosticsJson())
            put("agentProgressStream", true)
            put("workspaceProgressStream", true)

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
            if (agentModeEnabled) {
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
                put("decisionOwner", "cloud_final_chat_model")
                put("executionOwner", "android_structured_tool_executor")
                put("clientToolCallSchema", AI_WORKER_CLIENT_TOOL_CALL_SCHEMA)
                put("clientToolResultProtocol", AI_WORKER_CLIENT_TOOL_RESULT_PROTOCOL)
                if (agentModeEnabled) put("agentToolDomain", "visual_only")
            })
            put("clientCapabilities", JSONObject().apply {
                put("schema", if (agentModeEnabled) "ai_ledger_android_client_capabilities_v3" else "ai_ledger_android_client_capabilities_v2")
                put("agentActions", JSONArray(supportedAgentActions))
                put("deviceTools", JSONArray(supportedDeviceSteps))
                put("mobileActions", JSONArray(supportedMobileActions))
                put("preferenceUpdates", JSONArray(supportedPreferenceUpdates))
                put("installedApps", installedApps.toInstalledAppsJson())
            })
            put("responseFormat", JSONObject().apply {
                put("includeSources", true)
                put("includeStructuredData", true)
                put("includeClientToolCall", true)
                put("includeEmbeddedCommandMarker", false)
                put("includeAgentProgress", true)
                if (agentModeEnabled) put("deferClientToolReply", true)
            })

            put("client", AI_WORKER_CHAT_CLIENT_NAME)
            put("clientId", resolvedClientId)
            put("deviceId", resolvedClientId)
            put("clientVersion", "compose-native-cloud-first-v2")
            put("now", System.currentTimeMillis())
        }
    }

    private fun buildClientToolResultPayload(
        receipt: JSONObject,
        route: AiWorkerModelRoute,
        resolvedClientId: String,
    ): JSONObject {
        val selectedModelId = if (route.isAuto) "auto" else route.resolved.id
        return JSONObject().apply {
            put("requestId", java.util.UUID.randomUUID().toString())
            put("action", "internal_control_report")
            put("intent", "internal_control_report")
            put("mode", "internal_control_report")
            put("message", "client_tool_result:${receipt.optString("toolCallId")}")
            put("internalControlReceipt", JSONObject(receipt.toString()))
            put("modelPreference", selectedModelId)
            put("requestedModelPreference", selectedModelId)
            put("resolvedFinalModel", selectedModelId)
            put("autoRequested", route.isAuto)
            put("client", AI_WORKER_CHAT_CLIENT_NAME)
            put("clientId", resolvedClientId)
            put("deviceId", resolvedClientId)
            put("clientVersion", "compose-native-cloud-first-v2")
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

    private fun List<ChatMessage>.latestUserText(): String = lastOrNull {
        it.role == MessageRole.User && it.status != MessageStatus.Sending && it.text.isNotBlank()
    }?.text.orEmpty()

    private fun List<ChatMessage>.latestUserMessage(): ChatMessage? = lastOrNull {
        it.role == MessageRole.User && it.status != MessageStatus.Sending
    }

    private fun List<ChatAttachment>.toImageJsonArray(): JSONArray = JSONArray().apply {
        forEach { attachment ->
            put(JSONObject().apply {
                put("id", attachment.id)
                put("type", "image")
                put("mimeType", attachment.mimeType)
                put("base64Data", attachment.base64Data)
                put("fileName", attachment.fileName.orEmpty())
                attachment.width?.let { put("width", it) }
                attachment.height?.let { put("height", it) }
                attachment.sizeBytes?.let { put("sizeBytes", it) }
            })
        }
    }

    private fun List<ChatMessage>.latestUserImageAttachments(): List<ChatAttachment> =
        latestUserMessage()?.attachments?.filter { attachment ->
            attachment.mimeType.startsWith("image/") && attachment.base64Data.isNotBlank()
        }.orEmpty()

    private fun List<InstalledAppEntry>.toInstalledAppsJson(): JSONArray = JSONArray().apply {
        forEach { app ->
            put(JSONObject().apply {
                put("label", app.label)
                put("packageName", app.packageName)
            })
        }
    }

    private fun List<ChatMessage>.toWorkerMessages(): JSONArray {
        val recent = filter { message ->
            when (message.role) {
                MessageRole.User ->
                    (message.text.isNotBlank() || message.hasImageAttachments) &&
                        message.status != MessageStatus.Sending
                MessageRole.Assistant ->
                    message.text.isNotBlank() && message.status == MessageStatus.Sent
            }
        }.takeLast(24)
        val clean = recent.dropWhile { it.role != MessageRole.User }
        return JSONArray().apply {
            clean.forEach { message ->
                put(JSONObject().apply {
                    put("role", if (message.role == MessageRole.User) "user" else "assistant")
                    put("content", message.text)
                })
            }
        }
    }
}

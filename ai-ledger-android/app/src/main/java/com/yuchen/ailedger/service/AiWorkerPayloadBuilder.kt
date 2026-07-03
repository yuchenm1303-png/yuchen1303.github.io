package com.yuchen.ailedger.service

import com.yuchen.ailedger.AiLedgerApplication
import com.yuchen.ailedger.data.AssistantAccountSessionRuntime
import com.yuchen.ailedger.data.AssistantMemoryCompiler
import com.yuchen.ailedger.data.AssistantMemoryDiagnostics
import com.yuchen.ailedger.data.AssistantMemoryMutationRuntime
import com.yuchen.ailedger.data.AssistantMemoryRequestContextRuntime
import com.yuchen.ailedger.data.SupabaseAuthRepository
import com.yuchen.ailedger.data.switchAccount
import com.yuchen.ailedger.model.ChatAttachment
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
import com.yuchen.ailedger.ui.InlineStickerDisplaySettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a capability declaration and conversation payload only.
 *
 * It performs no natural-language intent routing. The cloud Final Chat Model receives the original
 * user message plus the full set of mechanically executable client tools and decides what to call.
 */
internal object AiWorkerPayloadBuilder {
    fun build(
        messages: List<ChatMessage>,
        route: AiWorkerModelRoute,
        onlineEnabled: Boolean,
        resolvedClientId: String,
    ): JSONObject {
        val latestUserText = latestUserText(messages)
        val imageArray = messages.latestUserImageAttachments().toImageJsonArray()
        val hasImage = imageArray.length() > 0
        val requestText = latestUserText
        val appContext = AiLedgerApplication.contextOrNull()
        val accountTicket = appContext?.let { context ->
            val accountState = SupabaseAuthRepository.get(context).state.value
            AssistantAccountSessionRuntime.updateSession(
                accountState.session?.takeIf { accountState.isLoggedIn },
            )
        }
        AssistantMemoryMutationRuntime.switchAccount(accountTicket)
        AssistantMemoryDiagnostics.switchAccount(accountTicket)
        AssistantMemoryRequestContextRuntime.clearCurrentThread()
        val memoryCompilation = requestText.takeIf(String::isNotBlank)?.let { userText ->
            AssistantMemoryCompiler.compileBackendOwned(userText = userText)
        }
        val stickerPreferences = InlineStickerDisplaySettings.currentExpressionPreferences(appContext)
        val installedApps = if (!hasImage) {
            appContext
                ?.let { context -> InstalledAppIndex(context).getLaunchableApps() }
                .orEmpty()
                .take(AI_WORKER_NORMAL_CHAT_DEVICE_PROBE_MAX_APPS)
        } else {
            emptyList()
        }
        val selectedModelId = when {
            hasImage -> AI_WORKER_QWEN_VISION_ROUTE_ID
            route.isAuto -> "auto"
            else -> route.resolved.id
        }
        val agentModeEnabled = !hasImage && AgentRuntimeController.isEnabled()
        val supportedAgentActions = listOf("run_device_control", "run_agent_task", "observe_screen")
        val supportedDeviceSteps = AI_WORKER_NORMAL_CHAT_DEVICE_TOOL_TYPES
        val supportedMobileActions = listOf("set_alarm", "navigate")
        val supportedPreferenceUpdates = listOf("navigation_address")
        val searchMode = if (onlineEnabled) "auto" else "off"
        val systemInstruction = commandProtocolSystemPrompt()

        return JSONObject().apply {
            put("requestId", java.util.UUID.randomUUID().toString())
            put("action", "chat")
            put("intent", if (hasImage) "vision_chat" else "chat")
            put("messages", messages.toWorkerMessages(systemInstruction))
            put("systemPrompt", systemInstruction)
            put("commandProtocolInstruction", systemInstruction)
            put("message", requestText)
            put("prompt", requestText)
            put("text", requestText)
            put("content", requestText)

            memoryCompilation?.memorySnapshot?.let { put("memorySnapshot", it) }
            memoryCompilation?.personaConfigJson()?.let { put("personaConfig", it) }
            memoryCompilation?.diagnosticsJson()?.let { put("memoryContextDiagnostics", it) }
            put("memoryMode", memoryCompilation?.requestMode ?: "off")
            put("memoryEnabled", memoryCompilation?.hasAnyContext == true)
            put("memorySchema", memoryCompilation?.schema ?: "ai_ledger_memory_context_v3")

            put("chatExpressionPreferences", JSONObject().apply {
                put("schema", "ai_ledger_chat_expression_preferences_v1")
                put("inlineStickerFrequency", stickerPreferences.frequency)
                put("inlineStickerIntensity", stickerPreferences.intensity)
                put("inlineStickerMaxPerReply", stickerPreferences.maxPerReply)
                put("inlineStickerRepeatCount", stickerPreferences.repeatCount)
            })

            put("modelPreference", selectedModelId)
            put("aiModelPreference", selectedModelId)
            put("requestedModelPreference", selectedModelId)
            put("model", selectedModelId)
            put("modelId", selectedModelId)
            put("originalModelPreference", route.requested.id)
            put("autoRequested", route.isAuto)
            put("autoResolvedModel", if (route.isAuto) "" else route.resolved.id)
            put("autoRouteAuthority", AI_WORKER_AUTO_ROUTE_AUTHORITY)
            put("autoRouteReason", route.reason)

            put("hasImage", hasImage)
            put("hasImages", hasImage)
            put("imageCount", imageArray.length())
            put("imageTransport", "top_level_images_v2")
            if (hasImage) put("images", imageArray)
            put("vision", JSONObject().apply {
                put("enabled", hasImage)
                put("provider", "qwen")
                put("route", AI_WORKER_QWEN_VISION_ROUTE_ID)
                put("modelEnv", "QWEN_VISION_MODEL")
            })

            put("agentModeEnabled", agentModeEnabled)
            put("agentStartRequested", false)
            put("agentExecutionPreference", if (agentModeEnabled) "visual_preferred" else "auto")

            put("onlineEnabled", onlineEnabled)
            put("searchEnabled", onlineEnabled)
            put("forceWebSearch", false)
            put("webSearchMode", searchMode)
            put("searchMode", searchMode)
            put("webSearch", JSONObject().apply {
                put("mode", searchMode)
                put("force", false)
                put("enabled", onlineEnabled)
                put("requireCitationsWhenForced", true)
            })
            put("structuredRealtime", JSONObject().apply {
                put("enabled", onlineEnabled)
                put("supportedTypes", JSONArray(listOf("stock", "weather", "exchange_rate", "sports")))
            })

            put("allowModelCommands", false)
            put("commandProtocol", JSONObject().apply {
                put("enabled", true)
                put("version", AI_WORKER_CHAT_PROTOCOL_VERSION)
                put("client", AI_WORKER_CHAT_CLIENT_NAME)
                put("allowModelCommands", false)
                put("structuredCommandsOnly", true)
                put("decisionOwner", "cloud_final_chat_model")
                put("executionOwner", "android_local_transaction_executor")
                put("returnNaturalReply", true)
                put("requireConfirmationForActions", true)
                put("supportedAgentActions", JSONArray(supportedAgentActions))
                put("supportedDeviceControlActions", JSONArray())
                put("supportedDeviceToolSteps", JSONArray(supportedDeviceSteps))
                put("supportedMobileActions", JSONArray(supportedMobileActions))
                put("supportedPreferenceUpdates", JSONArray(supportedPreferenceUpdates))
                put("navigationAddressSlots", JSONArray(listOf("home", "school", "company", "dorm")))
                put("clientToolCallSchema", AI_WORKER_CLIENT_TOOL_CALL_SCHEMA)
                put("clientToolResultProtocol", AI_WORKER_CLIENT_TOOL_RESULT_PROTOCOL)
                put("fallbackTransport", "structured_response_only")
            })
            put("clientCapabilities", JSONObject().apply {
                put("schema", "ai_ledger_android_client_capabilities_v1")
                put("agentActions", JSONArray(supportedAgentActions))
                put("deviceTools", JSONArray(supportedDeviceSteps))
                put("mobileActions", JSONArray(supportedMobileActions))
                put("preferenceUpdates", JSONArray(supportedPreferenceUpdates))
                put("installedApps", installedApps.toInstalledAppsJson())
            })

            put("normalChatDeviceToolProbe", JSONObject().apply {
                put("schema", AI_WORKER_NORMAL_CHAT_DEVICE_PROBE_SCHEMA)
                put("enabled", false)
                put("decisionOwner", "cloud_final_chat_model")
                put("executionOwner", "android_local_transaction_executor")
                put("supportedDeviceToolSteps", JSONArray(supportedDeviceSteps))
                put("supportedMobileActions", JSONArray(supportedMobileActions))
                put("supportedPreferenceUpdates", JSONArray(supportedPreferenceUpdates))
                put("installedApps", installedApps.toInstalledAppsJson())
            })
            put("responseFormat", JSONObject().apply {
                put("includeSources", true)
                put("includeStructuredData", true)
                put("includeMobileAction", true)
                put("includePreferenceUpdate", true)
                put("includeAgentAction", true)
                put("includeClientToolCall", true)
                put("includeEmbeddedCommandMarker", false)
                put("allowModelCommands", false)
            })

            put("accessPolicy", "cn_gateway_primary")
            put("primaryEndpointRole", "aliyun_cn_gateway")
            put("fallbackEndpointRole", "cloudflare_worker")
            put("client", AI_WORKER_CHAT_CLIENT_NAME)
            put("clientId", resolvedClientId)
            put("deviceId", resolvedClientId)
            put("clientVersion", "compose-native-unified-client-tools-v1")
            put("now", System.currentTimeMillis())
        }
    }

    private fun commandProtocolSystemPrompt(): String = """
        你正在服务一个 Android Compose AI 助手。用户原话由云端 Final Chat Model 独立理解。
        Android 不做关键词、正则、意图分类、模型选择或工具选择，只声明真实能力并执行结构化工具。
        需要记账、记忆、联网、设备控制、导航提醒或视觉智能时，由云端 Final Chat Model 自主调用相应原生工具。
        客户端只有收到严格 clientToolCall 后才会执行；只有真实 tool result 返回 verified 后才能声称操作成功。
        普通问答直接自然回复，不得在正文嵌入机器命令标记。
    """.trimIndent()

    private fun latestUserText(messages: List<ChatMessage>): String = messages.lastOrNull {
        it.role == MessageRole.User && it.text.isNotBlank()
    }?.text.orEmpty()

    private fun List<ChatMessage>.latestUserMessage(): ChatMessage? = lastOrNull {
        it.role == MessageRole.User && it.status != MessageStatus.Sending
    }

    private fun List<ChatMessage>.latestUserImageAttachments(): List<ChatAttachment> =
        latestUserMessage()?.attachments?.filter { attachment ->
            attachment.mimeType.startsWith("image/") && attachment.base64Data.isNotBlank()
        }.orEmpty()

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

    private fun List<InstalledAppEntry>.toInstalledAppsJson(): JSONArray = JSONArray().apply {
        forEach { app ->
            put(JSONObject().apply {
                put("label", app.label)
                put("packageName", app.packageName)
            })
        }
    }

    private fun List<ChatMessage>.toWorkerMessages(systemInstruction: String): JSONArray {
        val recent = filter { message ->
            when (message.role) {
                MessageRole.User ->
                    (message.text.isNotBlank() || message.hasImageAttachments) &&
                        message.status != MessageStatus.Sending
                MessageRole.Assistant -> message.isCloudAssistantContextMessage()
            }
        }.takeLast(16)
        val clean = recent.dropWhile { it.role != MessageRole.User }
        return JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemInstruction)
            })
            clean.forEach { message ->
                put(JSONObject().apply {
                    put("role", if (message.role == MessageRole.User) "user" else "assistant")
                    put("content", message.text)
                })
            }
        }
    }

    private fun ChatMessage.isCloudAssistantContextMessage(): Boolean {
        if (text.isBlank() || status != MessageStatus.Sent) return false
        return when (source) {
            null, "", "local", "local_ledger", "local_mobile", "local_agent",
            "cloud_fetch_failed", "cloud_error_normalized" -> false
            else -> true
        }
    }
}

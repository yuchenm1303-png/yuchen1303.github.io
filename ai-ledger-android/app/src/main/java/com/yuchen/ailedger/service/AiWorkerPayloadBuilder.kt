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
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
import com.yuchen.ailedger.ui.InlineStickerDisplaySettings
import org.json.JSONArray
import org.json.JSONObject

internal object AiWorkerPayloadBuilder {
    fun build(
        messages: List<ChatMessage>,
        route: AiWorkerModelRoute,
        onlineEnabled: Boolean,
        resolvedClientId: String,
    ): JSONObject {
        val commandInstruction = commandProtocolSystemPrompt()
        val workerMessages = messages.toWorkerMessages(commandInstruction)
        val latestUserText = latestUserText(messages)
        val imageArray = messages.latestUserImageAttachments().toImageJsonArray()
        val hasImage = imageArray.length() > 0
        val explicitAgentGoal = resolveExplicitAgentGoal(latestUserText)
        val agentModeEnabled =
            !hasImage && latestUserText.isNotBlank() && AgentRuntimeController.isEnabled()
        val shouldStartAgent =
            !hasImage && latestUserText.isNotBlank() &&
                (explicitAgentGoal != null || agentModeEnabled)
        val allowModelCommands = false
        val requestText = explicitAgentGoal ?: latestUserText
        val resolvedId = if (hasImage) AI_WORKER_QWEN_VISION_ROUTE_ID else route.resolved.id
        val searchEnabled = onlineEnabled && !hasImage && !shouldStartAgent
        val searchMode = if (searchEnabled) "force" else "off"
        val intent = when {
            hasImage -> "vision_chat"
            shouldStartAgent -> "agent_start"
            else -> "chat"
        }
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
        val memoryCompilation = if (!shouldStartAgent && requestText.isNotBlank()) {
            AssistantMemoryCompiler.compileBackendOwned(userText = requestText)
        } else {
            null
        }
        val stickerExpressionPreferences =
            InlineStickerDisplaySettings.currentExpressionPreferences(appContext)

        val normalChatDeviceProbeEnabled =
            intent == "chat" &&
                !hasImage &&
                !shouldStartAgent &&
                NormalChatDeviceIntentPolicy.shouldProbe(requestText)
        val includeInstalledApps = normalChatDeviceProbeEnabled &&
            NormalChatDeviceIntentPolicy.shouldIncludeInstalledApps(requestText)
        val normalChatInstalledApps = if (includeInstalledApps) {
            appContext
                ?.let { context -> InstalledAppIndex(context).getLaunchableApps() }
                .orEmpty()
                .take(AI_WORKER_NORMAL_CHAT_DEVICE_PROBE_MAX_APPS)
        } else {
            emptyList()
        }
        val supportedDeviceSteps = if (normalChatDeviceProbeEnabled) {
            AI_WORKER_NORMAL_CHAT_DEVICE_TOOL_TYPES
        } else {
            emptyList()
        }
        val supportedMobileActions = if (normalChatDeviceProbeEnabled) {
            listOf("set_alarm", "navigate")
        } else {
            emptyList()
        }
        val supportedPreferenceUpdates = if (normalChatDeviceProbeEnabled) {
            listOf("navigation_address")
        } else {
            emptyList()
        }
        val navigationAddressSlots = if (normalChatDeviceProbeEnabled) {
            listOf("home", "school", "company", "dorm")
        } else {
            emptyList()
        }

        return JSONObject().apply {
            put("requestId", java.util.UUID.randomUUID().toString())
            put("action", "chat")
            put("intent", intent)
            put("messages", workerMessages)
            put("systemPrompt", commandInstruction)
            put("commandProtocolInstruction", commandInstruction)
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
                put("inlineStickerFrequency", stickerExpressionPreferences.frequency)
                put("inlineStickerIntensity", stickerExpressionPreferences.intensity)
                put("inlineStickerMaxPerReply", stickerExpressionPreferences.maxPerReply)
                put("inlineStickerRepeatCount", stickerExpressionPreferences.repeatCount)
            })
            put("modelPreference", resolvedId)
            put("aiModelPreference", resolvedId)
            put("requestedModelPreference", resolvedId)
            put("model", resolvedId)
            put("modelId", resolvedId)
            put("legacyModelPreference", if (route.resolved == ChatModel.Kimi) "kimi" else resolvedId)
            put("originalModelPreference", route.requested.id)
            put("autoRequested", route.isAuto)
            put("autoResolvedModel", resolvedId)
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
            put("agentExplicitPrefix", explicitAgentGoal != null)
            put("agentStartRequested", shouldStartAgent)
            if (shouldStartAgent) {
                put("agentGoal", requestText)
                put("agentActionRequest", JSONObject().apply {
                    put("capability", "run_agent_task")
                    put("goal", requestText)
                    put("title", "手机智能体任务")
                    put("requiresConfirmation", false)
                    put(
                        "reason",
                        if (explicitAgentGoal != null) {
                            "用户使用显式智能体前缀"
                        } else {
                            "首页 Agent 开关已开启"
                        },
                    )
                })
            }
            put("onlineEnabled", searchEnabled)
            put("searchEnabled", searchEnabled)
            put("forceWebSearch", searchEnabled)
            put("webSearchMode", searchMode)
            put("searchMode", searchMode)
            put("webSearch", JSONObject().apply {
                put("mode", searchMode)
                put("force", searchEnabled)
                put("requireCitationsWhenForced", true)
                put("keepAutoSearchWhenOff", false)
            })
            put("structuredRealtime", JSONObject().apply {
                put("enabled", searchEnabled)
                put(
                    "supportedTypes",
                    JSONArray(listOf("stock", "weather", "exchange_rate", "sports")),
                )
            })
            put("allowModelCommands", allowModelCommands)
            put("commandProtocol", JSONObject().apply {
                put("enabled", true)
                put("version", AI_WORKER_CHAT_PROTOCOL_VERSION)
                put("client", AI_WORKER_CHAT_CLIENT_NAME)
                put("allowModelCommands", allowModelCommands)
                put("structuredCommandsOnly", true)
                put("deviceControlMode", "structured_low_risk_only")
                put("agentModeEnabled", agentModeEnabled)
                put("agentStartRequested", shouldStartAgent)
                put("returnNaturalReply", true)
                put("requireConfirmationForActions", true)
                put(
                    "supportedAgentActions",
                    JSONArray(
                        when {
                            shouldStartAgent -> listOf("run_agent_task")
                            normalChatDeviceProbeEnabled -> listOf("run_device_control")
                            else -> emptyList<String>()
                        },
                    ),
                )
                put("supportedDeviceControlActions", JSONArray())
                put("supportedDeviceToolSteps", JSONArray(supportedDeviceSteps))
                put("supportedMobileActions", JSONArray(supportedMobileActions))
                put("supportedPreferenceUpdates", JSONArray(supportedPreferenceUpdates))
                put("navigationAddressSlots", JSONArray(navigationAddressSlots))
                put("fallbackTransport", "structured_response_only")
            })
            put("normalChatDeviceToolProbe", JSONObject().apply {
                put("schema", AI_WORKER_NORMAL_CHAT_DEVICE_PROBE_SCHEMA)
                put("enabled", normalChatDeviceProbeEnabled)
                put("installedAppsIncluded", includeInstalledApps)
                put("decisionOwner", "deepseek_primary_qwen_failure_fallback")
                put("executionOwner", "android_local_verified")
                put("singleRequestParallel", true)
                put("supportedDeviceToolSteps", JSONArray(supportedDeviceSteps))
                put("supportedMobileActions", JSONArray(supportedMobileActions))
                put("supportedPreferenceUpdates", JSONArray(supportedPreferenceUpdates))
                put("installedApps", JSONArray().apply {
                    normalChatInstalledApps.forEach { app ->
                        put(JSONObject().apply {
                            put("label", app.label)
                            put("packageName", app.packageName)
                        })
                    }
                })
            })
            put("responseFormat", JSONObject().apply {
                put("includeSources", true)
                put("includeStructuredData", true)
                put("includeMobileAction", normalChatDeviceProbeEnabled)
                put("includePreferenceUpdate", normalChatDeviceProbeEnabled)
                put("includeAgentAction", shouldStartAgent || normalChatDeviceProbeEnabled)
                put("includeEmbeddedCommandMarker", false)
                put("allowModelCommands", allowModelCommands)
            })
            put("accessPolicy", "cn_gateway_primary")
            put("primaryEndpointRole", "aliyun_cn_gateway")
            put("fallbackEndpointRole", "cloudflare_worker")
            put("client", AI_WORKER_CHAT_CLIENT_NAME)
            put("clientId", resolvedClientId)
            put("deviceId", resolvedClientId)
            put(
                "clientVersion",
                when {
                    hasImage -> "compose-native-qwen-vision-v3-memory-retrieval"
                    shouldStartAgent -> "compose-native-agent-switch-v5"
                    else -> "compose-native-command-chat-v9-intent-gated"
                },
            )
            put("now", System.currentTimeMillis())
        }
    }

    private fun resolveExplicitAgentGoal(text: String): String? {
        val clean = text.trim()
        val prefixes = listOf(
            "/agent",
            "/智能体",
            "智能体：",
            "智能体:",
            "Agent：",
            "Agent:",
            "agent：",
            "agent:",
        )
        return prefixes.firstOrNull { clean.startsWith(it, ignoreCase = true) }
            ?.let { prefix ->
                clean.drop(prefix.length).trim().takeIf { goal -> goal.isNotBlank() }
            }
    }

    private fun commandProtocolSystemPrompt(): String = """
        你正在服务一个 Android Compose AI 助手。正常问题直接中文回答。
        只有用户明确要求操作手机、系统设置或应用时，才返回结构化手机动作；普通问答、代码、数学和项目讨论不得返回动作。
        commandProtocol.allowModelCommands=false；普通聊天不得在自然语言中返回机器命令。
        本次普通聊天不允许在自然语言回复里嵌入机器命令标记。
        普通问答、问候、解释、代码、数学、项目讨论、翻译和写作都只能自然回复，不得返回任何手机动作。
        普通聊天的内部控制由独立 DeepSeek 原生工具规划器处理；本聊天回复不得自行返回 run_device_control。
        Android 本地会再次校验工具、参数、权限和高风险确认；模型不得声称动作已经成功，除非本地执行器返回成功。
        如果请求里 agentStartRequested=true 或 intent=agent_start，后端可以返回 agentAction.capability=run_agent_task，goal 使用请求里的 agentGoal/message。
    """.trimIndent()

    private fun latestUserText(messages: List<ChatMessage>): String {
        return messages.lastOrNull {
            it.role == MessageRole.User && it.text.isNotBlank()
        }?.text.orEmpty()
    }

    private fun List<ChatMessage>.latestUserMessage(): ChatMessage? {
        return lastOrNull {
            it.role == MessageRole.User && it.status != MessageStatus.Sending
        }
    }

    private fun List<ChatMessage>.latestUserImageAttachments(): List<ChatAttachment> {
        return latestUserMessage()
            ?.attachments
            ?.filter { attachment ->
                attachment.mimeType.startsWith("image/") && attachment.base64Data.isNotBlank()
            }
            .orEmpty()
    }

    private fun List<ChatAttachment>.toImageJsonArray(): JSONArray {
        return JSONArray().apply {
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
                    put(
                        "role",
                        if (message.role == MessageRole.User) "user" else "assistant",
                    )
                    put("content", message.text)
                })
            }
        }
    }

    private fun ChatMessage.isCloudAssistantContextMessage(): Boolean {
        if (text.isBlank() || status != MessageStatus.Sent) return false
        return when (source) {
            null,
            "",
            "local",
            "local_ledger",
            "local_mobile",
            "local_agent",
            "cloud_fetch_failed",
            "cloud_error_normalized" -> false
            else -> true
        }
    }
}

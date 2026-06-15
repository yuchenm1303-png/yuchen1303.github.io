package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatAttachment
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
import com.yuchen.ailedger.model.StructuredDataCard
import com.yuchen.ailedger.model.StructuredMetric
import com.yuchen.ailedger.model.WebSource
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

private const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000
private const val DEFAULT_READ_TIMEOUT_MS = 45_000
private const val QWEN_VISION_ROUTE_ID = "qwen_vision"
private val embeddedCommandRegex = Regex("""\[\[AI_LEDGER_COMMAND:(\{.*?\})]]""", setOf(RegexOption.DOT_MATCHES_ALL))

data class AiWorkerConfig(
    val endpoint: String = AiWorkerClient.DEFAULT_ENDPOINT,
    val fallbackEndpoints: List<String> = AiWorkerClient.DEFAULT_FALLBACK_ENDPOINTS,
    val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS
)

data class CloudMobileAction(val type: String, val title: String? = null, val destination: String? = null, val appName: String? = null, val packageName: String? = null, val hour: Int? = null, val minute: Int? = null, val label: String? = null)
data class CloudPreferenceUpdate(val type: String, val slot: String, val label: String, val value: String)
data class CloudAgentAction(
    val capability: String,
    val title: String? = null,
    val goal: String? = null,
    val requiresConfirmation: Boolean = false,
    val reason: String? = null,
    val deviceControlStep: CloudAgentStep? = null,
)

data class AiChatResponse(
    val reply: String,
    val source: String = "cloud_ai",
    val model: String? = null,
    val modelLabel: String? = null,
    val version: String? = null,
    val webSources: List<WebSource> = emptyList(),
    val structuredData: StructuredDataCard? = null,
    val mobileAction: CloudMobileAction? = null,
    val preferenceUpdate: CloudPreferenceUpdate? = null,
    val agentAction: CloudAgentAction? = null,
    val searchUsed: Boolean = false,
    val searchProvider: String? = null
)

private data class ModelRoute(val requested: ChatModel, val resolved: ChatModel, val reason: String) { val isAuto: Boolean get() = requested == ChatModel.Auto }
private data class RouteScore(val model: ChatModel, val score: Int, val reason: String)

class AiWorkerClient(private val config: AiWorkerConfig = AiWorkerConfig()) {
    val endpoint: String get() = config.endpoint

    @Throws(IOException::class)
    fun sendChat(messages: List<ChatMessage>, modelPreference: ChatModel = ChatModel.Auto, onlineEnabled: Boolean = false): AiChatResponse {
        val route = resolveModelRoute(messages, modelPreference)
        val endpoints = endpointPlan(route)
        if (endpoints.isEmpty()) throw IOException("AI Worker endpoint 未配置")
        val payload = buildPayload(messages, route, onlineEnabled)
        var lastError: IOException? = null
        endpointLoop@ for (cleanEndpoint in endpoints) {
            for (candidate in endpointCandidates(cleanEndpoint)) {
                try { return postChat(candidate, payload, route) } catch (error: IOException) {
                    lastError = error
                    if (error is SocketTimeoutException || error.cause is SocketTimeoutException) continue@endpointLoop
                }
            }
        }
        throw lastError ?: IOException("云端 AI 请求失败，请检查 Worker 配置。")
    }

    @Throws(IOException::class)
    fun streamChat(
        messages: List<ChatMessage>,
        modelPreference: ChatModel = ChatModel.Auto,
        onlineEnabled: Boolean = false,
        onDelta: (String) -> Unit
    ): AiChatResponse {
        val route = resolveModelRoute(messages, modelPreference)
        val endpoints = endpointPlan(route)
        if (endpoints.isEmpty()) throw IOException("AI Worker endpoint 未配置")
        val payload = buildPayload(messages, route, onlineEnabled).apply {
            put("stream", true)
            put("streaming", true)
            put("streamFormat", "sse")
            put("responseMode", "stream")
        }
        var lastError: IOException? = null
        endpointLoop@ for (cleanEndpoint in endpoints) {
            for (candidate in endpointCandidates(cleanEndpoint)) {
                try {
                    return postStreamChat(candidate, payload, route, onDelta)
                } catch (error: IOException) {
                    lastError = error
                    if (error is SocketTimeoutException || error.cause is SocketTimeoutException) continue@endpointLoop
                }
            }
        }
        throw lastError ?: IOException("云端 AI 流式请求失败，请检查 Worker 配置。")
    }

    private fun endpointPool(primary: String, fallbacks: List<String>): List<String> = (listOf(primary) + fallbacks).map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }.distinct()

    private fun endpointPlan(route: ModelRoute): List<String> {
        val cn = config.endpoint.trim().trimEnd('/')
        val cf = (config.fallbackEndpoints.firstOrNull() ?: CLOUDFLARE_WORKER_ENDPOINT).trim().trimEnd('/')
        val resolvedIsCnModel = route.resolved == ChatModel.Kimi || route.resolved == ChatModel.DeepSeekV4
        return if (resolvedIsCnModel) endpointPool(cn, emptyList()) else endpointPool(cn, listOf(cf))
    }

    private fun resolveModelRoute(messages: List<ChatMessage>, modelPreference: ChatModel): ModelRoute {
        if (messages.hasImageAttachments()) return ModelRoute(modelPreference, ChatModel.Kimi, "qwen_vision_image_attachment")
        if (modelPreference != ChatModel.Auto) return ModelRoute(modelPreference, modelPreference, "manual_selection")
        val latest = latestUserText(messages)
        val text = latest.lowercase()
        val route = scoreAutoV2(latest, text).maxWithOrNull(compareBy<RouteScore> { it.score }.thenBy { autoTieBreakPriority(it.model) }) ?: RouteScore(ChatModel.Kimi, 1, "qwen_default")
        return ModelRoute(ChatModel.Auto, route.model, "auto_v2:${route.reason}")
    }

    private fun scoreAutoV2(latest: String, text: String): List<RouteScore> {
        val codeScore = 10 * countMatches(text, codeKeywords) + 7 * countMatches(text, appDevKeywords) + if (looksLikeCodeOrError(latest, text)) 18 else 0
        val reasoningScore = 9 * countMatches(text, reasoningKeywords) + 11 * countMatches(text, stemKeywords) + 6 * countMatches(text, designKeywords) + if (hasFormulaSignal(latest)) 14 else 0
        val translateScore = 10 * countMatches(text, translationKeywords)
        val longWritingScore = 8 * countMatches(text, writingKeywords) + when { latest.length >= 1600 -> 18; latest.length >= 900 -> 12; latest.length >= 420 && hasAny(text, writingKeywords) -> 8; else -> 0 }
        val qwenGeneralScore = 12 + (if (containsChinese(latest)) 6 else 0) + (if (latest.length < 420) 4 else 0)
        return listOf(
            RouteScore(ChatModel.GptOss, codeScore, "code_android_api"),
            RouteScore(ChatModel.DeepSeekV4, reasoningScore, "reasoning_stem_design"),
            RouteScore(ChatModel.Kimi, qwenGeneralScore + translateScore + longWritingScore / 2, "qwen_general_cn_translation_writing"),
            RouteScore(ChatModel.Gemini, if (text.contains("gemini")) 16 else translateScore / 2, "translation_or_explicit_gemini"),
            RouteScore(ChatModel.Mistral, longWritingScore, "long_summary_polish")
        )
    }

    private fun autoTieBreakPriority(model: ChatModel): Int = when (model) { ChatModel.Kimi -> 5; ChatModel.DeepSeekV4 -> 4; ChatModel.GptOss -> 3; ChatModel.Mistral -> 2; ChatModel.Gemini -> 1; ChatModel.Workers -> 0; ChatModel.Auto -> -1 }
    private fun looksLikeCodeOrError(latest: String, text: String): Boolean = latest.contains("```") || latest.contains("Exception") || latest.contains("Traceback") || latest.contains("NullPointer") || latest.contains("Unresolved reference") || latest.contains("Cannot resolve") || text.contains("build failed") || text.contains("stacktrace") || Regex("\\b(error|failed|exception|fatal):").containsMatchIn(text)
    private fun hasFormulaSignal(text: String): Boolean = text.any { it in listOf('∂', '∫', '∑', '√', 'θ', 'π', '∞') } || Regex("[a-zA-Z][0-9]?\\s*=\\s*[-+]?\\d").containsMatchIn(text) || Regex("\\d+\\s*/\\s*\\d+").containsMatchIn(text)
    private fun containsChinese(text: String): Boolean = text.any { it in '\u4e00'..'\u9fff' }
    private fun countMatches(text: String, keywords: List<String>): Int = keywords.count { text.contains(it) }
    private fun hasAny(text: String, keywords: List<String>): Boolean = keywords.any { text.contains(it) }

    private val codeKeywords = listOf("代码", "报错", "bug", "修复", "编译", "构建", "函数", "类", "脚本", "依赖", "库", "接口", "api", "kotlin", "compose", "android", "github", "gradle", "cloudflare", "worker", "python", "java", "javascript", "typescript", "html", "css", "json", "http", "request", "response")
    private val appDevKeywords = listOf("app", "apk", "workflow", "actions", "commit", "分支", "仓库", "源码", "viewmodel", "client", "repository", "compose 原生")
    private val reasoningKeywords = listOf("推理", "证明", "分析", "为什么", "原理", "思路", "计算", "求解", "推导", "判别", "极限", "偏导", "积分", "二重积分", "链式法则", "全微分")
    private val stemKeywords = listOf("数学", "电路", "模电", "数电", "单片机", "stm32", "传感器", "建模", "模型", "仿真", "控制", "信号", "滤波", "放大器", "电磁", "物理")
    private val designKeywords = listOf("方案", "设计", "架构", "策略", "优化", "规划", "迁移", "实现思路", "怎么做", "怎么设计", "技术路线", "系统设计")
    private val translationKeywords = listOf("什么意思", "翻译", "英文", "英语", "日语", "德语", "怎么读", "读音", "单词", "词语", "translate", "meaning", "pronunciation")
    private val writingKeywords = listOf("总结", "概括", "归纳", "提纲", "大纲", "报告", "整理", "润色", "改写", "论文", "summary", "summarize", "outline", "polish", "rewrite")

    private fun buildPayload(messages: List<ChatMessage>, route: ModelRoute, onlineEnabled: Boolean): JSONObject {
        val commandInstruction = commandProtocolSystemPrompt()
        val workerMessages = messages.toWorkerMessages(commandInstruction)
        val latestUserText = latestUserText(messages)
        val hasImage = messages.hasImageAttachments()
        val imageArray = messages.latestUserImageAttachments().toImageJsonArray()
        val explicitAgentGoal = resolveExplicitAgentGoal(latestUserText)
        val agentModeEnabled = !hasImage && latestUserText.isNotBlank() && AgentRuntimeController.isEnabled()
        val shouldStartAgent = !hasImage && latestUserText.isNotBlank() && (explicitAgentGoal != null || agentModeEnabled)
        val requestText = explicitAgentGoal ?: latestUserText
        val resolvedId = if (hasImage) QWEN_VISION_ROUTE_ID else route.resolved.id
        val searchMode = if (onlineEnabled && !hasImage && !shouldStartAgent) "force" else "off"
        val intent = when { hasImage -> "vision_chat"; shouldStartAgent -> "agent_start"; else -> "chat" }
        return JSONObject().apply {
            put("action", "chat"); put("intent", intent)
            put("messages", workerMessages); put("systemPrompt", commandInstruction); put("commandProtocolInstruction", commandInstruction)
            put("message", requestText); put("prompt", requestText); put("text", requestText); put("content", requestText)
            put("modelPreference", resolvedId); put("aiModelPreference", resolvedId); put("requestedModelPreference", resolvedId); put("model", resolvedId); put("modelId", resolvedId)
            put("legacyModelPreference", if (route.resolved == ChatModel.Kimi) "kimi" else resolvedId); put("originalModelPreference", route.requested.id)
            put("autoRequested", route.isAuto); put("autoResolvedModel", resolvedId); put("autoRouteReason", route.reason)
            put("hasImage", hasImage); put("hasImages", hasImage); put("imageCount", imageArray.length()); put("images", imageArray); put("attachments", imageArray)
            put("vision", JSONObject().apply { put("enabled", hasImage); put("provider", "qwen"); put("route", QWEN_VISION_ROUTE_ID); put("modelEnv", "QWEN_VISION_MODEL") })
            put("agentModeEnabled", agentModeEnabled); put("agentExplicitPrefix", explicitAgentGoal != null); put("agentStartRequested", shouldStartAgent)
            if (shouldStartAgent) {
                put("agentGoal", requestText)
                put("agentActionRequest", JSONObject().apply { put("capability", "run_agent_task"); put("goal", requestText); put("title", "手机智能体任务"); put("requiresConfirmation", false); put("reason", if (explicitAgentGoal != null) "用户使用显式智能体前缀" else "首页 Agent 开关已开启") })
            }
            put("onlineEnabled", onlineEnabled && !hasImage && !shouldStartAgent); put("searchEnabled", onlineEnabled && !hasImage && !shouldStartAgent); put("forceWebSearch", onlineEnabled && !hasImage && !shouldStartAgent)
            put("webSearchMode", searchMode); put("searchMode", searchMode)
            put("webSearch", JSONObject().apply { put("mode", searchMode); put("force", onlineEnabled && !hasImage && !shouldStartAgent); put("requireCitationsWhenForced", true); put("keepAutoSearchWhenOff", false) })
            put("structuredRealtime", JSONObject().apply { put("enabled", onlineEnabled && !hasImage && !shouldStartAgent); put("supportedTypes", JSONArray(listOf("stock", "weather", "exchange_rate", "sports"))) })
            put("commandProtocol", JSONObject().apply { put("enabled", true); put("version", 5); put("client", "android-compose"); put("agentModeEnabled", agentModeEnabled); put("agentStartRequested", shouldStartAgent); put("returnNaturalReply", true); put("requireConfirmationForActions", true); put("supportedAgentActions", JSONArray(listOf("observe_screen", "run_agent_task", "run_device_control"))); put("supportedDeviceControlActions", JSONArray(DeviceControlRouter.supportedCapabilities())); put("supportedDeviceToolSteps", JSONArray(CloudAgentStep.deviceToolTypes.toList())); put("supportedMobileActions", JSONArray(listOf("set_alarm", "navigate"))); put("supportedPreferenceUpdates", JSONArray(listOf("navigation_address"))); put("navigationAddressSlots", JSONArray(listOf("home", "school", "company", "dorm"))); put("fallbackTransport", "embedded_marker"); put("embeddedMarker", "[[AI_LEDGER_COMMAND:{...}]]") })
            put("responseFormat", JSONObject().apply { put("includeSources", true); put("includeStructuredData", true); put("includeMobileAction", true); put("includePreferenceUpdate", true); put("includeAgentAction", true); put("includeEmbeddedCommandMarker", true) })
            put("accessPolicy", "cn_gateway_primary"); put("primaryEndpointRole", "aliyun_cn_gateway"); put("fallbackEndpointRole", "cloudflare_worker"); put("client", "android-compose")
            put("clientVersion", if (hasImage) "compose-native-qwen-vision-v1" else if (shouldStartAgent) "compose-native-agent-switch-v4" else "compose-native-agent-action-v3")
            put("now", System.currentTimeMillis())
        }
    }

    private fun resolveExplicitAgentGoal(text: String): String? {
        val clean = text.trim()
        val prefixes = listOf("/agent", "/智能体", "智能体：", "智能体:", "Agent：", "Agent:", "agent：", "agent:")
        return prefixes.firstOrNull { clean.startsWith(it, ignoreCase = true) }?.let { clean.drop(it.length).trim().takeIf { goal -> goal.isNotBlank() } }
    }

    private fun commandProtocolSystemPrompt(): String = """
        你正在服务一个 Android Compose AI 助手。正常问题直接中文回答。
        只有明确要操作手机、观察屏幕、打开 App 后继续找页面、点击、输入、滑动时，才返回手机动作标记。
        可由内部控制直接完成的系统任务，优先返回 agentAction.capability=run_device_control，并附带 deviceControlAction；不要让视觉智能去点设置页。
        支持的 deviceControlAction.capability 包括：${DeviceControlRouter.supportedCapabilities().joinToString(", ")}。
        deviceControlAction 示例：{"capability":"network.wifi.set","arguments":{"enabled":true},"riskLevel":"medium","requiresConfirmation":false}
        如果请求里 agentStartRequested=true 或 intent=agent_start，直接返回 agentAction.capability=run_agent_task，goal 使用请求里的 agentGoal/message。
        不要把普通问答、代码、数学、项目讨论误判为手机动作。
        内部控制标记示例：[[AI_LEDGER_COMMAND:{"agentAction":{"capability":"run_device_control","title":"打开 Wi-Fi","goal":"打开 Wi-Fi","deviceControlAction":{"capability":"network.wifi.set","arguments":{"enabled":true},"riskLevel":"medium","requiresConfirmation":false},"reason":"Wi-Fi 开关属于内部设备控制"}}]]
        标记格式示例：[[AI_LEDGER_COMMAND:{"agentAction":{"capability":"run_agent_task","title":"手机智能体任务","goal":"用户目标","requiresConfirmation":false,"reason":"用户要求手机操作"}}]]
    """.trimIndent()

    private fun endpointCandidates(cleanEndpoint: String): List<String> = if (cleanEndpoint.endsWith("/chat") || cleanEndpoint.endsWith("/api/chat")) listOf(cleanEndpoint) else listOf(cleanEndpoint, "$cleanEndpoint/chat", "$cleanEndpoint/api/chat").distinct()

    private fun postChat(endpoint: String, payload: JSONObject, route: ModelRoute): AiChatResponse {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply { requestMethod = "POST"; connectTimeout = config.connectTimeoutMs; readTimeout = config.readTimeoutMs; doOutput = true; setRequestProperty("Content-Type", "application/json; charset=utf-8"); setRequestProperty("Accept", "application/json, text/plain"); setRequestProperty("X-Client", "android-compose") }
        return try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val body = readBody(connection, status)
            val data = body.toJsonOrNull()
            if (status !in 200..299) throw IOException(data?.optString("error").notBlankOrNull() ?: data?.optString("message").notBlankOrNull() ?: body.take(120).ifBlank { "云端 AI 调用失败：HTTP $status" })
            throwIfServerReturnedFallbackSignal(data)
            parseChatResponse(data = data, body = body, payload = payload, route = route)
        } catch (error: SocketTimeoutException) { throw IOException("云端 AI 请求超时：${endpoint.substringAfter("://")}", error) } finally { connection.disconnect() }
    }

    private fun postStreamChat(
        endpoint: String,
        payload: JSONObject,
        route: ModelRoute,
        onDelta: (String) -> Unit
    ): AiChatResponse {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = config.connectTimeoutMs
            readTimeout = config.readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream, application/x-ndjson, application/json, text/plain")
            setRequestProperty("X-Client", "android-compose")
            setRequestProperty("X-AI-Ledger-Stream", "sse")
        }

        return try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val contentType = connection.contentType.orEmpty().lowercase()

            if (status !in 200..299) {
                val body = readBody(connection, status)
                val data = body.toJsonOrNull()
                throw IOException(
                    data?.optString("error").notBlankOrNull()
                        ?: data?.optString("message").notBlankOrNull()
                        ?: body.take(120).ifBlank { "云端 AI 调用失败：HTTP $status" }
                )
            }

            if (!contentType.contains("text/event-stream") && !contentType.contains("application/x-ndjson")) {
                val body = readBody(connection, status)
                val data = body.toJsonOrNull()
                throwIfServerReturnedFallbackSignal(data)
                return parseChatResponse(data = data, body = body, payload = payload, route = route)
            }

            val streamedReply = StringBuilder()
            var finalData: JSONObject? = null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.forEachStreamPayload { payloadText ->
                    val event = parseStreamPayload(payloadText) ?: return@forEachStreamPayload
                    if (event.delta.isNotBlank()) {
                        streamedReply.append(event.delta)
                        onDelta(event.delta)
                    }
                    if (event.done) {
                        finalData = event.data ?: finalData
                    }
                }
            }

            val streamedText = streamedReply.toString().trim()
            val finalJson = finalData
            when {
                finalJson != null -> parseChatResponse(
                    data = finalJson,
                    body = finalJson.toString(),
                    payload = payload,
                    route = route,
                    replyOverride = streamedText.takeIf { it.isNotBlank() }
                )
                streamedText.isNotBlank() -> AiChatResponse(
                    reply = streamedText,
                    source = "cloud_ai",
                    model = route.resolved.id,
                    modelLabel = if (route.isAuto) "自动选择 · ${route.resolved.label}" else route.resolved.label
                )
                else -> throw IOException("云端流式回复结束，但没有返回有效内容")
            }
        } catch (error: SocketTimeoutException) {
            throw IOException("云端 AI 流式请求超时：${endpoint.substringAfter("://")}", error)
        } finally {
            connection.disconnect()
        }
    }

    private data class StreamPayload(
        val delta: String = "",
        val done: Boolean = false,
        val data: JSONObject? = null
    )

    private fun BufferedReader.forEachStreamPayload(block: (String) -> Unit) {
        val sseData = StringBuilder()
        while (true) {
            val line = readLine() ?: break
            when {
                line.isBlank() -> {
                    val payload = sseData.toString().trim()
                    if (payload.isNotBlank()) block(payload)
                    sseData.clear()
                }
                line.startsWith("data:") -> {
                    val data = line.removePrefix("data:").trimStart()
                    if (data == "[DONE]") {
                        block(data)
                    } else {
                        if (sseData.isNotEmpty()) sseData.append('\n')
                        sseData.append(data)
                    }
                }
                line.startsWith("{") -> block(line)
            }
        }
        val tail = sseData.toString().trim()
        if (tail.isNotBlank()) block(tail)
    }

    private fun parseStreamPayload(payload: String): StreamPayload? {
        val clean = payload.trim()
        if (clean.isBlank()) return null
        if (clean == "[DONE]") return StreamPayload(done = true)

        val data = clean.toJsonOrNull() ?: return StreamPayload(delta = clean)
        val type = data.optString("type").lowercase()
        val choices = data.optJSONArray("choices")
        val firstChoice = choices?.optJSONObject(0)
        val choiceDelta = firstChoice?.optJSONObject("delta")?.optString("content").orEmpty()
        val choiceText = firstChoice?.optString("text").orEmpty()
        val finishReason = firstChoice?.optString("finish_reason").orEmpty()

        val hasFinalReply = data.has("reply") || data.has("response") || data.has("answer") ||
            data.optJSONObject("data") != null || data.optJSONObject("result") != null
        val done = type in setOf("done", "final", "complete", "completed") ||
            data.optBoolean("done", false) ||
            data.optBoolean("completed", false) ||
            finishReason.isNotBlank() ||
            (type.isBlank() && hasFinalReply && data.optString("delta").isBlank() && choiceDelta.isBlank())

        if (done) {
            val responseData = data.optJSONObject("response") ?: data.optJSONObject("final") ?: data
            return StreamPayload(done = true, data = responseData)
        }

        val delta = data.optString("delta")
            .ifBlank { data.optString("text") }
            .ifBlank { data.optString("content") }
            .ifBlank { data.optString("replyDelta") }
            .ifBlank { choiceDelta }
            .ifBlank { choiceText }

        return if (delta.isNotBlank()) StreamPayload(delta = delta) else null
    }

    private fun parseChatResponse(
        data: JSONObject?,
        body: String,
        payload: JSONObject,
        route: ModelRoute,
        replyOverride: String? = null
    ): AiChatResponse {
        val rawReply = (replyOverride?.takeIf { it.isNotBlank() } ?: extractReply(data, body)).trim()
        val embeddedCommand = extractEmbeddedCommandJson(rawReply) ?: extractEmbeddedCommandJson(body)
        val displayReply = stripEmbeddedCommandMarker(rawReply).trim()
        val parsedMobileAction = parseCloudMobileAction(data) ?: parseCloudMobileAction(embeddedCommand)
        val parsedPreferenceUpdate = parseCloudPreferenceUpdate(data) ?: parseCloudPreferenceUpdate(embeddedCommand)
        val parsedAgentAction = parseCloudAgentAction(data) ?: parseCloudAgentAction(embeddedCommand) ?: payloadToAgentAction(payload)
        if (displayReply.isBlank() && embeddedCommand == null && parsedMobileAction == null && parsedPreferenceUpdate == null && parsedAgentAction == null) {
            throw IOException("云端没有返回有效回复")
        }
        val rawModel = data?.optString("model").notBlankOrNull()
            ?: data?.optString("modelId").notBlankOrNull()
            ?: if (payload.optBoolean("hasImage")) QWEN_VISION_ROUTE_ID else route.resolved.id
        val rawVersion = data?.optString("version").notBlankOrNull()
        val resolvedLabel = data?.optString("modelLabel").notBlankOrNull()
            ?: data?.optString("modelName").notBlankOrNull()
            ?: modelLabelFromId(rawModel)
            ?: if (rawModel == QWEN_VISION_ROUTE_ID) "Qwen 识图 · Omni Plus" else route.resolved.label
        val displayLabel = if (route.isAuto && !resolvedLabel.startsWith("自动选择")) "自动选择 · $resolvedLabel" else resolvedLabel
        val fallbackReply = when {
            parsedAgentAction != null -> "我识别到一个手机智能体动作。"
            parsedMobileAction != null -> "我识别到一个手机动作，请确认后执行。"
            parsedPreferenceUpdate != null -> "我已识别到一项偏好更新。"
            else -> rawReply
        }
        return AiChatResponse(
            reply = displayReply.ifBlank { fallbackReply },
            source = data?.optString("source").notBlankOrNull() ?: "cloud_ai",
            model = rawModel,
            modelLabel = displayLabel,
            version = rawVersion,
            webSources = parseWebSources(data),
            structuredData = parseStructuredData(data),
            mobileAction = parsedMobileAction,
            preferenceUpdate = parsedPreferenceUpdate,
            agentAction = parsedAgentAction,
            searchUsed = data?.optBoolean("searchUsed", false) ?: false,
            searchProvider = data?.optString("searchProvider").notBlankOrNull()
        )
    }

    private fun payloadToAgentAction(payload: JSONObject): CloudAgentAction? {
        if (!payload.optBoolean("agentStartRequested", false) && payload.optString("intent") != "agent_start") return null
        val goal = payload.optString("agentGoal").notBlankOrNull() ?: payload.optString("message").notBlankOrNull() ?: return null
        return CloudAgentAction("run_agent_task", "手机智能体任务", goal, false, "首页 Agent 开关已开启")
    }

    private fun throwIfServerReturnedFallbackSignal(data: JSONObject?) { if (data == null) return; val normalized = (data.optString("code") + " " + data.optString("error") + " " + data.optString("message")).lowercase(); if (data.optBoolean("unsupportedModel", false) || data.optBoolean("shouldFallback", false) || normalized.contains("unsupported") || normalized.contains("not supported") || normalized.contains("not_configured") || normalized.contains("model_not_available") || normalized.contains("provider_not_available") || normalized.contains("不支持") || normalized.contains("未配置") || normalized.contains("不可用")) throw IOException(data.optString("error").ifBlank { "当前入口不支持该模型，正在尝试备用入口。" }) }

    private fun parseWebSources(data: JSONObject?): List<WebSource> { val array = data?.optJSONArray("sources") ?: data?.optJSONArray("webSources") ?: data?.optJSONObject("data")?.optJSONArray("sources") ?: return emptyList(); return buildList { for (index in 0 until array.length()) { val item = array.optJSONObject(index) ?: continue; val url = item.optString("url").notBlankOrNull() ?: item.optString("link").notBlankOrNull() ?: item.optString("href").notBlankOrNull() ?: ""; val title = item.optString("title").notBlankOrNull() ?: item.optString("name").notBlankOrNull() ?: url.substringAfter("://").substringBefore('/').ifBlank { "来源 ${index + 1}" }; val snippet = item.optString("snippet").notBlankOrNull() ?: item.optString("summary").notBlankOrNull() ?: item.optString("content").notBlankOrNull() ?: ""; val domain = item.optString("domain").notBlankOrNull() ?: url.substringAfter("://").substringBefore('/'); add(WebSource(title.take(80), url, domain.take(60), snippet.take(180), item.optString("publishedAt").notBlankOrNull() ?: item.optString("published").notBlankOrNull() ?: item.optString("date").notBlankOrNull())) } }.take(6) }
    private fun parseStructuredData(data: JSONObject?): StructuredDataCard? { val item = data?.optJSONObject("structuredData") ?: data?.optJSONObject("structured") ?: data?.optJSONObject("data")?.optJSONObject("structuredData") ?: return null; val type = item.optString("type").notBlankOrNull() ?: data.optString("type").notBlankOrNull() ?: "realtime"; val title = item.optString("title").notBlankOrNull() ?: item.optString("name").notBlankOrNull() ?: structuredTypeLabel(type); val subtitle = item.optString("subtitle").notBlankOrNull() ?: item.optString("symbol").notBlankOrNull() ?: item.optString("location").notBlankOrNull(); return StructuredDataCard(type, title, subtitle, item.optString("timestamp").notBlankOrNull() ?: item.optString("updatedAt").notBlankOrNull(), parseStructuredMetrics(item), item.optString("rawText").notBlankOrNull() ?: item.optString("summary").notBlankOrNull()) }
    private fun parseStructuredMetrics(item: JSONObject): List<StructuredMetric> { val explicit = item.optJSONArray("metrics"); if (explicit != null) return buildList { for (index in 0 until explicit.length()) { val metric = explicit.optJSONObject(index) ?: continue; val label = metric.optString("label").notBlankOrNull() ?: metric.optString("name").notBlankOrNull(); val value = metric.optString("value").notBlankOrNull() ?: metric.optString("text").notBlankOrNull(); if (!label.isNullOrBlank() && !value.isNullOrBlank()) add(StructuredMetric(label.take(24), value.take(40), metric.optString("unit").notBlankOrNull(), metric.optString("detail").notBlankOrNull())) } }.take(8); val preferredKeys = listOf("price", "change", "changePercent", "temperature", "condition", "humidity", "rate", "from", "to", "score", "status"); return preferredKeys.mapNotNull { key -> item.optString(key).notBlankOrNull()?.let { StructuredMetric(structuredMetricLabel(key), it) } }.take(8) }
    private fun parseCloudMobileAction(data: JSONObject?): CloudMobileAction? { val item = data?.optJSONObject("mobileAction") ?: data?.optJSONObject("command") ?: data?.optJSONObject("data")?.optJSONObject("mobileAction") ?: data?.optJSONObject("result")?.optJSONObject("mobileAction") ?: return null; val rawType = item.optString("type").notBlankOrNull() ?: item.optString("action").notBlankOrNull() ?: return null; val type = rawType.lowercase().replace('-', '_'); if (type !in setOf("set_alarm", "navigate")) return null; return CloudMobileAction(type, item.optString("title").notBlankOrNull(), item.optString("destination").notBlankOrNull() ?: item.optString("target").notBlankOrNull(), item.optString("appName").notBlankOrNull() ?: item.optString("app").notBlankOrNull(), item.optString("packageName").notBlankOrNull() ?: item.optString("package").notBlankOrNull(), item.optIntOrNull("hour"), item.optIntOrNull("minute"), item.optString("label").notBlankOrNull() ?: item.optString("message").notBlankOrNull()) }
    private fun parseCloudPreferenceUpdate(data: JSONObject?): CloudPreferenceUpdate? { val item = data?.optJSONObject("preferenceUpdate") ?: data?.optJSONObject("preference") ?: data?.optJSONObject("data")?.optJSONObject("preferenceUpdate") ?: data?.optJSONObject("result")?.optJSONObject("preferenceUpdate") ?: return null; val type = item.optString("type").notBlankOrNull()?.lowercase()?.replace('-', '_') ?: return null; if (type != "navigation_address") return null; val slot = item.optString("slot").notBlankOrNull()?.lowercase()?.replace('-', '_') ?: return null; if (slot !in setOf("home", "school", "company", "dorm")) return null; val value = item.optString("value").notBlankOrNull() ?: item.optString("address").notBlankOrNull() ?: item.optString("destination").notBlankOrNull() ?: return null; val label = item.optString("label").notBlankOrNull() ?: when (slot) { "home" -> "家"; "school" -> "学校"; "company" -> "公司"; "dorm" -> "宿舍"; else -> slot }; return CloudPreferenceUpdate(type, slot, label.take(12), value.trim().take(80)) }
    private fun parseCloudAgentAction(data: JSONObject?): CloudAgentAction? { val item = data?.optJSONObject("agentAction") ?: data?.optJSONObject("agent") ?: data?.optJSONObject("data")?.optJSONObject("agentAction") ?: data?.optJSONObject("result")?.optJSONObject("agentAction") ?: return null; val capability = item.optString("capability").notBlankOrNull()?.lowercase()?.replace('-', '_') ?: item.optString("type").notBlankOrNull()?.lowercase()?.replace('-', '_') ?: return null; if (capability !in setOf("observe_screen", "run_agent_task", "run_device_control", "device_control", "run_internal_device_control")) return null; val normalizedCapability = if (capability == "device_control" || capability == "run_internal_device_control") "run_device_control" else capability; val goal = item.optString("goal").notBlankOrNull() ?: item.optString("task").notBlankOrNull() ?: item.optString("instruction").notBlankOrNull() ?: item.optString("query").notBlankOrNull(); val deviceStep = if (normalizedCapability == "run_device_control") DeviceControlRouter.fromAgentActionJson(item) else null; if (normalizedCapability == "run_device_control" && deviceStep == null) return null; return CloudAgentAction(normalizedCapability, item.optString("title").notBlankOrNull(), goal, item.optBoolean("requiresConfirmation", false), item.optString("reason").notBlankOrNull(), deviceStep) }

    private fun extractEmbeddedCommandJson(text: String): JSONObject? = embeddedCommandRegex.find(text)?.groupValues?.getOrNull(1)?.toJsonOrNull()
    private fun stripEmbeddedCommandMarker(text: String): String = embeddedCommandRegex.replace(text, "").trim()
    private fun structuredTypeLabel(type: String): String = when (type.lowercase()) { "stock" -> "股票行情"; "weather" -> "天气"; "exchange_rate", "rate", "currency" -> "汇率"; "sports" -> "比赛"; else -> "实时数据" }
    private fun structuredMetricLabel(key: String): String = when (key) { "price" -> "价格"; "change" -> "涨跌"; "changePercent" -> "涨跌幅"; "temperature" -> "温度"; "condition" -> "天气"; "humidity" -> "湿度"; "rate" -> "汇率"; "from" -> "来源币种"; "to" -> "目标币种"; "score" -> "比分"; "status" -> "状态"; else -> key }
    private fun extractReply(data: JSONObject?, body: String): String { if (data == null) return body; return data.optString("reply").ifBlank { data.optString("response") }.ifBlank { data.optString("answer") }.ifBlank { data.optString("text") }.ifBlank { data.optString("content") }.ifBlank { data.optJSONObject("data")?.optString("reply").orEmpty() }.ifBlank { data.optJSONObject("result")?.optString("reply").orEmpty() }.ifBlank { data.optJSONObject("result")?.optString("text").orEmpty() } }
    private fun latestUserText(messages: List<ChatMessage>): String = messages.lastOrNull { it.role == MessageRole.User && it.text.isNotBlank() }?.text.orEmpty()
    private fun modelLabelFromId(modelId: String?): String? = modelId?.takeIf { it.isNotBlank() }?.let { ChatModel.fromId(it).takeIf { model -> model != ChatModel.Auto }?.label }
    private fun String?.notBlankOrNull(): String? = this?.takeIf { it.isNotBlank() }
    private fun JSONObject.optIntOrNull(key: String): Int? { if (!has(key) || isNull(key)) return null; return try { getInt(key) } catch (_: Exception) { optString(key).toIntOrNull() } }
    private fun List<ChatMessage>.hasImageAttachments(): Boolean = any { it.hasImageAttachments }
    private fun List<ChatMessage>.latestUserImageAttachments(): List<ChatAttachment> = lastOrNull { it.role == MessageRole.User && it.hasImageAttachments }?.attachments?.filter { it.mimeType.startsWith("image/") && it.base64Data.isNotBlank() }.orEmpty()
    private fun List<ChatAttachment>.toImageJsonArray(): JSONArray = JSONArray().apply { forEach { attachment -> put(JSONObject().apply { put("id", attachment.id); put("type", "image"); put("mimeType", attachment.mimeType); put("mediaType", attachment.mimeType); put("base64Data", attachment.base64Data); put("data", attachment.base64Data); put("imageBase64", attachment.base64Data); put("fileName", attachment.fileName.orEmpty()); attachment.width?.let { put("width", it) }; attachment.height?.let { put("height", it) }; attachment.sizeBytes?.let { put("sizeBytes", it) } }) } }
    private fun List<ChatMessage>.toWorkerMessages(systemInstruction: String): JSONArray { val recent = filter { message -> when (message.role) { MessageRole.User -> (message.text.isNotBlank() || message.hasImageAttachments) && message.status != MessageStatus.Sending; MessageRole.Assistant -> message.isCloudAssistantContextMessage() } }.takeLast(16); val clean = recent.dropWhile { it.role != MessageRole.User }; return JSONArray().apply { put(JSONObject().apply { put("role", "system"); put("content", systemInstruction) }); clean.forEach { message -> put(JSONObject().apply { put("role", if (message.role == MessageRole.User) "user" else "assistant"); put("content", message.text); if (message.role == MessageRole.User && message.hasImageAttachments) { val images = message.attachments.filter { it.base64Data.isNotBlank() }.toImageJsonArray(); put("attachments", images); put("images", images) } }) } } }
    private fun ChatMessage.isCloudAssistantContextMessage(): Boolean { if (text.isBlank() || status != MessageStatus.Sent) return false; return when (source) { null, "", "local", "local_ledger", "local_mobile", "local_agent", "cloud_fetch_failed", "cloud_error_normalized" -> false; else -> true } }
    private fun readBody(connection: HttpURLConnection, status: Int): String { val stream = if (status in 200..299) connection.inputStream else connection.errorStream; return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty() }
    private fun String.toJsonOrNull(): JSONObject? = try { takeIf { it.isNotBlank() }?.let { JSONObject(it) } } catch (_: Exception) { null }

    companion object {
        const val ALIYUN_CN_ENDPOINT = "https://" + "ai-ledg-chat-cn-dnuxlrhytb.cn-hangzhou.fcapp.run"
        const val CLOUDFLARE_WORKER_ENDPOINT = "https://" + "ai-ledger-parser.552078638.workers.dev"
        const val DEFAULT_ENDPOINT = ALIYUN_CN_ENDPOINT
        val DEFAULT_FALLBACK_ENDPOINTS = listOf(CLOUDFLARE_WORKER_ENDPOINT)
    }
}

package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.StructuredDataCard
import com.yuchen.ailedger.model.StructuredMetric
import com.yuchen.ailedger.model.WebSource
import java.io.IOException
import org.json.JSONObject

internal object AiWorkerResponseParser {
    fun parse(
        data: JSONObject?,
        body: String,
        payload: JSONObject,
        route: AiWorkerModelRoute,
        replyOverride: String? = null,
    ): AiChatResponse {
        val rawReply = (replyOverride?.takeIf { it.isNotBlank() } ?: extractReply(data, body)).trim()
        val clientToolCall = parseClientToolCall(data)
        val projection = clientToolCall?.let(::projectClientToolCall)
        val parsedMobileAction = projection?.mobileAction ?: parseCloudMobileAction(data)
        val parsedPreferenceUpdate = projection?.preferenceUpdate ?: parseCloudPreferenceUpdate(data)
        val parsedAgentAction = projection?.agentAction
            ?: parseCloudAgentAction(data)
            ?: payloadToAgentAction(payload)

        if (
            rawReply.isBlank() &&
            parsedMobileAction == null &&
            parsedPreferenceUpdate == null &&
            parsedAgentAction == null
        ) {
            throw IOException("云端没有返回有效回复")
        }

        val rawModel = data?.optString("model").notBlankOrNull()
            ?: data?.optString("modelId").notBlankOrNull()
            ?: if (payload.optBoolean("hasImage")) AI_WORKER_QWEN_VISION_ROUTE_ID else route.resolved.id
        val resolvedLabel = data?.optString("modelLabel").notBlankOrNull()
            ?: data?.optString("modelName").notBlankOrNull()
            ?: modelLabelFromId(rawModel)
            ?: if (rawModel == AI_WORKER_QWEN_VISION_ROUTE_ID) "Qwen 识图 · Omni Plus" else route.resolved.label
        val displayLabel = if (route.isAuto && !resolvedLabel.startsWith("自动选择")) {
            "自动选择 · $resolvedLabel"
        } else {
            resolvedLabel
        }
        val fallbackReply = when {
            parsedAgentAction?.capability == "run_device_control" -> "已收到结构化客户端工具调用，正在交给本地事务执行器。"
            parsedAgentAction != null -> "已收到结构化手机智能体动作。"
            parsedMobileAction != null -> "已收到结构化手机动作，请确认后执行。"
            parsedPreferenceUpdate != null -> "已收到结构化偏好更新。"
            else -> rawReply
        }

        return AiChatResponse(
            reply = rawReply.ifBlank { fallbackReply },
            source = data?.optString("source").notBlankOrNull() ?: "cloud_ai",
            model = rawModel,
            modelLabel = displayLabel,
            version = data?.optString("version").notBlankOrNull(),
            webSources = parseWebSources(data),
            structuredData = parseStructuredData(data),
            mobileAction = parsedMobileAction,
            preferenceUpdate = parsedPreferenceUpdate,
            agentAction = parsedAgentAction,
            clientToolCall = clientToolCall,
            searchUsed = data?.optBoolean("searchUsed", false) ?: false,
            searchProvider = data?.optString("searchProvider").notBlankOrNull(),
            stickerDiagnosticsJson = parseStickerDiagnostics(data)?.toString(2),
        )
    }

    fun extractReply(data: JSONObject?, body: String): String {
        if (data == null) return body
        return data.optString("reply")
            .ifBlank { data.optString("response") }
            .ifBlank { data.optString("answer") }
            .ifBlank { data.optString("text") }
            .ifBlank { data.optString("content") }
            .ifBlank { data.optJSONObject("data")?.optString("reply").orEmpty() }
            .ifBlank { data.optJSONObject("result")?.optString("reply").orEmpty() }
            .ifBlank { data.optJSONObject("result")?.optString("text").orEmpty() }
    }

    fun mergeStreamedReplyWithFinalReply(streamedReply: String, finalReply: String): String {
        val streamed = streamedReply.trim()
        val final = finalReply.trim()
        val streamedStickerCount = countInlineStickerProtocolMarkers(streamed)
        val finalStickerCount = countInlineStickerProtocolMarkers(final)
        return when {
            streamed.isBlank() -> final
            final.isBlank() -> streamed
            final == streamed -> final
            final.startsWith(streamed) -> final
            streamedStickerCount > finalStickerCount -> streamed
            finalStickerCount > 0 || streamedStickerCount > 0 -> final
            else -> streamed
        }
    }

    internal fun countInlineStickerProtocolMarkers(value: String): Int {
        val visibleCount = Regex(
            """\[\[AI_LEDGER_INLINE_STICKER:[a-z0-9_]{2,48}]]""",
            RegexOption.IGNORE_CASE
        ).findAll(value).count()
        if (visibleCount > 0) return visibleCount
        return if (value.any { char -> char.code in 0xDB40..0xDB7F }) 1 else 0
    }

    internal fun buildClientStickerMergeDiagnostics(
        streamedReply: String,
        finalReply: String,
        mergedReply: String,
    ): JSONObject {
        val streamed = streamedReply.trim()
        val final = finalReply.trim()
        val merged = mergedReply.trim()
        val streamedStickerCount = countInlineStickerProtocolMarkers(streamed)
        val finalStickerCount = countInlineStickerProtocolMarkers(final)
        val mergedStickerCount = countInlineStickerProtocolMarkers(merged)
        val decision = when {
            streamed.isBlank() -> "final_stream_blank"
            final.isBlank() -> "streamed_final_blank"
            final == streamed -> "final_equal_streamed"
            final.startsWith(streamed) -> "final_extends_streamed"
            streamedStickerCount > finalStickerCount -> "streamed_more_stickers"
            finalStickerCount > 0 || streamedStickerCount > 0 -> "final_has_sticker_protocol"
            else -> "streamed_default"
        }
        return JSONObject().apply {
            put("schema", "inline_sticker_client_merge_diagnostics_v1")
            put("streamedStickerCount", streamedStickerCount)
            put("finalStickerCount", finalStickerCount)
            put("mergedStickerCount", mergedStickerCount)
            put("streamedLength", streamed.length)
            put("finalLength", final.length)
            put("mergedLength", merged.length)
            put("mergeDecision", decision)
            put("streamedHead", streamed.take(160))
            put("finalHead", final.take(160))
            put("mergedHead", merged.take(160))
        }
    }

    internal fun attachClientStickerDiagnostics(data: JSONObject, clientDiagnostics: JSONObject) {
        val root = parseStickerDiagnostics(data) ?: JSONObject()
        root.put("clientMerge", clientDiagnostics)
        data.put("stickerDiagnostics", root)
    }

    private fun parseStickerDiagnostics(data: JSONObject?): JSONObject? {
        return data?.optJSONObject("stickerDiagnostics")
            ?: data?.optJSONObject("data")?.optJSONObject("stickerDiagnostics")
            ?: data?.optJSONObject("result")?.optJSONObject("stickerDiagnostics")
    }

    fun throwIfServerReturnedFallbackSignal(data: JSONObject?) {
        if (data == null) return
        val normalized = (
            data.optString("code") + " " + data.optString("error") + " " + data.optString("message")
            ).lowercase()
        if (
            data.optBoolean("unsupportedModel", false) ||
            data.optBoolean("shouldFallback", false) ||
            normalized.contains("unsupported") ||
            normalized.contains("not supported") ||
            normalized.contains("not_configured") ||
            normalized.contains("model_not_available") ||
            normalized.contains("provider_not_available") ||
            normalized.contains("不支持") ||
            normalized.contains("未配置") ||
            normalized.contains("不可用")
        ) {
            throw IOException(data.optString("error").ifBlank { "当前入口不支持该模型，正在尝试备用入口。" })
        }
    }

    private fun parseClientToolCall(data: JSONObject?): CloudClientToolCall? {
        val item = data?.optJSONObject("clientToolCall")
            ?: data?.optJSONObject("data")?.optJSONObject("clientToolCall")
            ?: data?.optJSONObject("result")?.optJSONObject("clientToolCall")
            ?: data?.optJSONObject("deviceIntent")?.optJSONObject("clientToolCall")
            ?: return null
        if (item.optString("schema") != AI_WORKER_CLIENT_TOOL_CALL_SCHEMA) return null
        val id = item.optString("id").notBlankOrNull() ?: return null
        val name = item.optString("name")
            .notBlankOrNull()
            ?.lowercase()
            ?.replace('-', '_')
            ?: return null
        val arguments = item.optJSONObject("arguments") ?: JSONObject()
        return CloudClientToolCall(
            schema = AI_WORKER_CLIENT_TOOL_CALL_SCHEMA,
            id = id.take(120),
            name = name.take(80),
            arguments = JSONObject(arguments.toString()),
            resultProtocol = item.optString("resultProtocol")
                .notBlankOrNull()
                ?: AI_WORKER_CLIENT_TOOL_RESULT_PROTOCOL,
            riskLevel = item.optString("riskLevel").notBlankOrNull() ?: "low",
            requiresConfirmation = item.optBoolean("requiresConfirmation", false),
            reason = item.optString("reason").notBlankOrNull(),
            originalUserGoal = item.optString("originalUserGoal").notBlankOrNull(),
            finalModel = item.optString("finalModel").notBlankOrNull(),
        )
    }

    private fun projectClientToolCall(call: CloudClientToolCall): ClientToolProjection {
        return when {
            call.name in CloudAgentStep.ledgerToolTypes || call.name == "device_control" -> {
                val step = DeviceControlRouter.fromClientToolCall(call.toJson()) ?: return ClientToolProjection()
                ClientToolProjection(
                    agentAction = CloudAgentAction(
                        capability = "run_device_control",
                        title = if (step.type in CloudAgentStep.ledgerToolTypes) "账本工具" else "内部设备控制",
                        goal = call.originalUserGoal,
                        requiresConfirmation = call.requiresConfirmation,
                        reason = call.reason,
                        deviceControlStep = step,
                        clientToolCall = call,
                    ),
                )
            }
            call.name == "computer_run_task" -> ClientToolProjection(
                agentAction = CloudAgentAction(
                    capability = "run_agent_task",
                    title = "手机智能体任务",
                    goal = call.arguments.optString("goal").notBlankOrNull() ?: call.originalUserGoal,
                    requiresConfirmation = call.requiresConfirmation,
                    reason = call.reason,
                    clientToolCall = call,
                ),
            )
            call.name == "computer_observe_screen" -> ClientToolProjection(
                agentAction = CloudAgentAction(
                    capability = "observe_screen",
                    title = "观察当前屏幕",
                    goal = call.originalUserGoal,
                    requiresConfirmation = false,
                    reason = call.reason,
                    clientToolCall = call,
                ),
            )
            call.name == "mobile_set_alarm" -> ClientToolProjection(
                mobileAction = CloudMobileAction(
                    type = "set_alarm",
                    hour = call.arguments.optIntOrNull("hour"),
                    minute = call.arguments.optIntOrNull("minute"),
                    label = call.arguments.optString("label").notBlankOrNull(),
                ),
            )
            call.name == "mobile_navigate" -> ClientToolProjection(
                mobileAction = CloudMobileAction(
                    type = "navigate",
                    destination = call.arguments.optString("destination").notBlankOrNull(),
                ),
            )
            call.name == "navigation_address_upsert" -> {
                val slot = call.arguments.optString("slot").notBlankOrNull()
                val value = call.arguments.optString("value").notBlankOrNull()
                if (slot == null || value == null) ClientToolProjection() else ClientToolProjection(
                    preferenceUpdate = CloudPreferenceUpdate(
                        type = "navigation_address",
                        slot = slot,
                        label = call.arguments.optString("label").ifBlank { slot },
                        value = value,
                    ),
                )
            }
            else -> ClientToolProjection()
        }
    }

    private fun payloadToAgentAction(payload: JSONObject): CloudAgentAction? {
        if (!payload.optBoolean("agentStartRequested", false) && payload.optString("intent") != "agent_start") return null
        val goal = payload.optString("agentGoal").notBlankOrNull()
            ?: payload.optString("message").notBlankOrNull()
            ?: return null
        return CloudAgentAction(
            capability = "run_agent_task",
            title = "手机智能体任务",
            goal = goal,
            requiresConfirmation = false,
            reason = "客户端显式请求视觉智能体",
        )
    }

    private fun parseWebSources(data: JSONObject?): List<WebSource> {
        val array = data?.optJSONArray("sources")
            ?: data?.optJSONArray("webSources")
            ?: data?.optJSONObject("data")?.optJSONArray("sources")
            ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optString("url").notBlankOrNull()
                    ?: item.optString("link").notBlankOrNull()
                    ?: item.optString("href").notBlankOrNull()
                    ?: ""
                val title = item.optString("title").notBlankOrNull()
                    ?: item.optString("name").notBlankOrNull()
                    ?: url.substringAfter("://").substringBefore('/').ifBlank { "来源 ${index + 1}" }
                val snippet = item.optString("snippet").notBlankOrNull()
                    ?: item.optString("summary").notBlankOrNull()
                    ?: item.optString("content").notBlankOrNull()
                    ?: ""
                val domain = item.optString("domain").notBlankOrNull()
                    ?: url.substringAfter("://").substringBefore('/')
                add(
                    WebSource(
                        title = title.take(80),
                        url = url,
                        domain = domain.take(60),
                        snippet = snippet.take(180),
                        publishedAt = item.optString("publishedAt").notBlankOrNull()
                            ?: item.optString("published").notBlankOrNull()
                            ?: item.optString("date").notBlankOrNull(),
                    ),
                )
            }
        }.take(6)
    }

    private fun parseStructuredData(data: JSONObject?): StructuredDataCard? {
        val item = data?.optJSONObject("structuredData")
            ?: data?.optJSONObject("structured")
            ?: data?.optJSONObject("data")?.optJSONObject("structuredData")
            ?: return null
        val type = item.optString("type").notBlankOrNull()
            ?: data?.optString("type").notBlankOrNull()
            ?: "realtime"
        val title = item.optString("title").notBlankOrNull()
            ?: item.optString("name").notBlankOrNull()
            ?: structuredTypeLabel(type)
        return StructuredDataCard(
            type = type,
            title = title,
            subtitle = item.optString("subtitle").notBlankOrNull()
                ?: item.optString("symbol").notBlankOrNull()
                ?: item.optString("location").notBlankOrNull(),
            timestamp = item.optString("timestamp").notBlankOrNull()
                ?: item.optString("updatedAt").notBlankOrNull(),
            metrics = parseStructuredMetrics(item),
            rawText = item.optString("rawText").notBlankOrNull()
                ?: item.optString("summary").notBlankOrNull(),
        )
    }

    private fun parseStructuredMetrics(item: JSONObject): List<StructuredMetric> {
        val explicit = item.optJSONArray("metrics")
        if (explicit != null) {
            return buildList {
                for (index in 0 until explicit.length()) {
                    val metric = explicit.optJSONObject(index) ?: continue
                    val label = metric.optString("label").notBlankOrNull()
                        ?: metric.optString("name").notBlankOrNull()
                    val value = metric.optString("value").notBlankOrNull()
                        ?: metric.optString("text").notBlankOrNull()
                    if (!label.isNullOrBlank() && !value.isNullOrBlank()) {
                        add(
                            StructuredMetric(
                                label = label.take(24),
                                value = value.take(40),
                                unit = metric.optString("unit").notBlankOrNull(),
                                detail = metric.optString("detail").notBlankOrNull(),
                            ),
                        )
                    }
                }
            }.take(8)
        }
        return listOf(
            "price", "change", "changePercent", "temperature", "condition", "humidity",
            "rate", "from", "to", "score", "status",
        ).mapNotNull { key ->
            item.optString(key).notBlankOrNull()?.let { value ->
                StructuredMetric(structuredMetricLabel(key), value)
            }
        }.take(8)
    }

    private fun parseCloudMobileAction(data: JSONObject?): CloudMobileAction? {
        val item = data?.optJSONObject("mobileAction")
            ?: data?.optJSONObject("command")
            ?: data?.optJSONObject("data")?.optJSONObject("mobileAction")
            ?: data?.optJSONObject("result")?.optJSONObject("mobileAction")
            ?: return null
        val type = (item.optString("type").notBlankOrNull()
            ?: item.optString("action").notBlankOrNull())
            ?.lowercase()
            ?.replace('-', '_')
            ?: return null
        if (type !in setOf("set_alarm", "navigate")) return null
        return CloudMobileAction(
            type = type,
            title = item.optString("title").notBlankOrNull(),
            destination = item.optString("destination").notBlankOrNull()
                ?: item.optString("target").notBlankOrNull(),
            appName = item.optString("appName").notBlankOrNull()
                ?: item.optString("app").notBlankOrNull(),
            packageName = item.optString("packageName").notBlankOrNull()
                ?: item.optString("package").notBlankOrNull(),
            hour = item.optIntOrNull("hour"),
            minute = item.optIntOrNull("minute"),
            label = item.optString("label").notBlankOrNull()
                ?: item.optString("message").notBlankOrNull(),
        )
    }

    private fun parseCloudPreferenceUpdate(data: JSONObject?): CloudPreferenceUpdate? {
        val item = data?.optJSONObject("preferenceUpdate")
            ?: data?.optJSONObject("preference")
            ?: data?.optJSONObject("data")?.optJSONObject("preferenceUpdate")
            ?: data?.optJSONObject("result")?.optJSONObject("preferenceUpdate")
            ?: return null
        val type = item.optString("type").notBlankOrNull()?.lowercase()?.replace('-', '_') ?: return null
        if (type != "navigation_address") return null
        val slot = item.optString("slot").notBlankOrNull()?.lowercase()?.replace('-', '_') ?: return null
        if (slot !in setOf("home", "school", "company", "dorm")) return null
        val value = item.optString("value").notBlankOrNull()
            ?: item.optString("address").notBlankOrNull()
            ?: item.optString("destination").notBlankOrNull()
            ?: return null
        val label = item.optString("label").notBlankOrNull() ?: when (slot) {
            "home" -> "家"
            "school" -> "学校"
            "company" -> "公司"
            "dorm" -> "宿舍"
            else -> slot
        }
        return CloudPreferenceUpdate(type, slot, label.take(12), value.trim().take(80))
    }

    private fun parseCloudAgentAction(data: JSONObject?): CloudAgentAction? {
        val item = data?.optJSONObject("agentAction")
            ?: data?.optJSONObject("agent")
            ?: data?.optJSONObject("data")?.optJSONObject("agentAction")
            ?: data?.optJSONObject("result")?.optJSONObject("agentAction")
            ?: return null
        val capability = (item.optString("capability").notBlankOrNull()
            ?: item.optString("type").notBlankOrNull())
            ?.lowercase()
            ?.replace('-', '_')
            ?: return null
        if (
            capability !in setOf(
                "observe_screen", "run_agent_task", "run_device_control", "device_control",
                "run_internal_device_control",
            )
        ) return null
        val normalizedCapability = when (capability) {
            "device_control", "run_internal_device_control" -> "run_device_control"
            else -> capability
        }
        val deviceStep = if (normalizedCapability == "run_device_control") {
            DeviceControlRouter.fromAgentActionJson(item)
        } else {
            null
        }
        if (normalizedCapability == "run_device_control" && deviceStep == null) return null
        return CloudAgentAction(
            capability = normalizedCapability,
            title = item.optString("title").notBlankOrNull(),
            goal = item.optString("goal").notBlankOrNull()
                ?: item.optString("task").notBlankOrNull()
                ?: item.optString("instruction").notBlankOrNull()
                ?: item.optString("query").notBlankOrNull(),
            requiresConfirmation = item.optBoolean("requiresConfirmation", false),
            reason = item.optString("reason").notBlankOrNull(),
            deviceControlStep = deviceStep,
        )
    }

    private fun structuredTypeLabel(type: String): String = when (type.lowercase()) {
        "stock" -> "股票行情"
        "weather" -> "天气"
        "exchange_rate", "rate", "currency" -> "汇率"
        "sports" -> "比赛"
        else -> "实时数据"
    }

    private fun structuredMetricLabel(key: String): String = when (key) {
        "price" -> "价格"
        "change" -> "涨跌"
        "changePercent" -> "涨跌幅"
        "temperature" -> "温度"
        "condition" -> "天气"
        "humidity" -> "湿度"
        "rate" -> "汇率"
        "from" -> "来源币种"
        "to" -> "目标币种"
        "score" -> "比分"
        "status" -> "状态"
        else -> key
    }

    private fun modelLabelFromId(modelId: String?): String? = modelId
        ?.takeIf(String::isNotBlank)
        ?.let { id -> ChatModel.fromId(id).takeIf { model -> model != ChatModel.Auto }?.label }

    private fun String?.notBlankOrNull(): String? = this?.takeIf { it.isNotBlank() }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        val value = opt(key) as? Number ?: return null
        val number = value.toDouble()
        if (!number.isFinite() || number % 1.0 != 0.0) return null
        return number.toInt()
    }
}

private data class ClientToolProjection(
    val agentAction: CloudAgentAction? = null,
    val mobileAction: CloudMobileAction? = null,
    val preferenceUpdate: CloudPreferenceUpdate? = null,
)

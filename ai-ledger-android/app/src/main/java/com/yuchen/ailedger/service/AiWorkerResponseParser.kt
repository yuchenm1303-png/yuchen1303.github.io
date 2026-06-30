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
        val rawReply = (
            replyOverride?.takeIf { it.isNotBlank() } ?: extractReply(data, body)
            ).trim()
        val displayReply = rawReply.trim()
        val parsedMobileAction = parseCloudMobileAction(data)
        val parsedPreferenceUpdate = parseCloudPreferenceUpdate(data)
        val parsedAgentAction =
            parseCloudAgentActionForPayload(data, payload) ?: payloadToAgentAction(payload)
        if (
            displayReply.isBlank() &&
            parsedMobileAction == null &&
            parsedPreferenceUpdate == null &&
            parsedAgentAction == null
        ) {
            throw IOException("云端没有返回有效回复")
        }
        val rawModel = data?.optString("model").notBlankOrNull()
            ?: data?.optString("modelId").notBlankOrNull()
            ?: if (payload.optBoolean("hasImage")) {
                AI_WORKER_QWEN_VISION_ROUTE_ID
            } else {
                route.resolved.id
            }
        val rawVersion = data?.optString("version").notBlankOrNull()
        val resolvedLabel = data?.optString("modelLabel").notBlankOrNull()
            ?: data?.optString("modelName").notBlankOrNull()
            ?: modelLabelFromId(rawModel)
            ?: if (rawModel == AI_WORKER_QWEN_VISION_ROUTE_ID) {
                "Qwen 识图 · Omni Plus"
            } else {
                route.resolved.label
            }
        val displayLabel = if (route.isAuto && !resolvedLabel.startsWith("自动选择")) {
            "自动选择 · $resolvedLabel"
        } else {
            resolvedLabel
        }
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
            searchProvider = data?.optString("searchProvider").notBlankOrNull(),
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

    fun mergeStreamedReplyWithFinalReply(
        streamedReply: String,
        finalReply: String,
    ): String {
        val streamed = streamedReply.trim()
        val final = finalReply.trim()
        return when {
            streamed.isBlank() -> final
            final.isBlank() || final == streamed -> streamed
            final.startsWith(streamed) -> final
            else -> streamed
        }
    }

    fun throwIfServerReturnedFallbackSignal(data: JSONObject?) {
        if (data == null) return
        val normalized = (
            data.optString("code") + " " +
                data.optString("error") + " " +
                data.optString("message")
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
            throw IOException(
                data.optString("error")
                    .ifBlank { "当前入口不支持该模型，正在尝试备用入口。" },
            )
        }
    }

    private fun payloadToAgentAction(payload: JSONObject): CloudAgentAction? {
        if (
            !payload.optBoolean("agentStartRequested", false) &&
            payload.optString("intent") != "agent_start"
        ) {
            return null
        }
        val goal = payload.optString("agentGoal").notBlankOrNull()
            ?: payload.optString("message").notBlankOrNull()
            ?: return null
        return CloudAgentAction(
            capability = "run_agent_task",
            title = "手机智能体任务",
            goal = goal,
            requiresConfirmation = false,
            reason = "首页 Agent 开关已开启",
        )
    }

    private fun parseCloudAgentActionForPayload(
        data: JSONObject?,
        payload: JSONObject,
    ): CloudAgentAction? {
        val action = parseCloudAgentAction(data) ?: return null
        val explicitAgentStart =
            payload.optBoolean("agentStartRequested", false) ||
                payload.optString("intent") == "agent_start"
        if (explicitAgentStart) {
            return action.takeIf { it.capability == "run_agent_task" }
        }

        val probe = payload.optJSONObject("normalChatDeviceToolProbe")
        val parallelProbeEnabled =
            payload.optString("intent") == "chat" &&
                probe?.let { config ->
                    config.optBoolean("enabled", false) &&
                        config.optString("schema") ==
                        AI_WORKER_NORMAL_CHAT_DEVICE_PROBE_SCHEMA
                } == true
        return action.takeIf {
            parallelProbeEnabled && it.capability == "run_device_control"
        }
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
                    ?: url.substringAfter("://")
                        .substringBefore('/')
                        .ifBlank { "来源 ${index + 1}" }
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
            ?: data.optString("type").notBlankOrNull()
            ?: "realtime"
        val title = item.optString("title").notBlankOrNull()
            ?: item.optString("name").notBlankOrNull()
            ?: structuredTypeLabel(type)
        val subtitle = item.optString("subtitle").notBlankOrNull()
            ?: item.optString("symbol").notBlankOrNull()
            ?: item.optString("location").notBlankOrNull()
        return StructuredDataCard(
            type = type,
            title = title,
            subtitle = subtitle,
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
        val preferredKeys = listOf(
            "price",
            "change",
            "changePercent",
            "temperature",
            "condition",
            "humidity",
            "rate",
            "from",
            "to",
            "score",
            "status",
        )
        return preferredKeys.mapNotNull { key ->
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
        val rawType = item.optString("type").notBlankOrNull()
            ?: item.optString("action").notBlankOrNull()
            ?: return null
        val type = rawType.lowercase().replace('-', '_')
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
        val type = item.optString("type")
            .notBlankOrNull()
            ?.lowercase()
            ?.replace('-', '_')
            ?: return null
        if (type != "navigation_address") return null
        val slot = item.optString("slot")
            .notBlankOrNull()
            ?.lowercase()
            ?.replace('-', '_')
            ?: return null
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
        return CloudPreferenceUpdate(
            type = type,
            slot = slot,
            label = label.take(12),
            value = value.trim().take(80),
        )
    }

    private fun parseCloudAgentAction(data: JSONObject?): CloudAgentAction? {
        val item = data?.optJSONObject("agentAction")
            ?: data?.optJSONObject("agent")
            ?: data?.optJSONObject("data")?.optJSONObject("agentAction")
            ?: data?.optJSONObject("result")?.optJSONObject("agentAction")
            ?: return null
        val capability = item.optString("capability")
            .notBlankOrNull()
            ?.lowercase()
            ?.replace('-', '_')
            ?: item.optString("type")
                .notBlankOrNull()
                ?.lowercase()
                ?.replace('-', '_')
            ?: return null
        if (
            capability !in setOf(
                "observe_screen",
                "run_agent_task",
                "run_device_control",
                "device_control",
                "run_internal_device_control",
            )
        ) {
            return null
        }
        val normalizedCapability = when (capability) {
            "device_control", "run_internal_device_control" -> "run_device_control"
            else -> capability
        }
        val goal = item.optString("goal").notBlankOrNull()
            ?: item.optString("task").notBlankOrNull()
            ?: item.optString("instruction").notBlankOrNull()
            ?: item.optString("query").notBlankOrNull()
        val deviceStep = if (normalizedCapability == "run_device_control") {
            DeviceControlRouter.fromAgentActionJson(item)
        } else {
            null
        }
        if (normalizedCapability == "run_device_control" && deviceStep == null) return null
        return CloudAgentAction(
            capability = normalizedCapability,
            title = item.optString("title").notBlankOrNull(),
            goal = goal,
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

    private fun modelLabelFromId(modelId: String?): String? {
        return modelId
            ?.takeIf { it.isNotBlank() }
            ?.let { id ->
                ChatModel.fromId(id).takeIf { model -> model != ChatModel.Auto }?.label
            }
    }

    private fun String?.notBlankOrNull(): String? = this?.takeIf { it.isNotBlank() }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return try {
            getInt(key)
        } catch (_: Exception) {
            optString(key).toIntOrNull()
        }
    }
}

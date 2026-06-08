package com.yuchen.ailedger.service

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

data class AgentBrainRoutePlan(
    val route: String,
    val confidence: Float,
    val risk: String,
    val reason: String,
    val question: String = "",
    val refusalReason: String = "",
    val steps: List<AgentBrainRouteStep> = emptyList(),
) {
    val isVisualOnly: Boolean get() = route == ROUTE_VISUAL_AGENT
    val isDeviceOnly: Boolean get() = route == ROUTE_DEVICE_TOOL
    val isHybrid: Boolean get() = route == ROUTE_HYBRID
    val isAskUser: Boolean get() = route == ROUTE_ASK_USER
    val isRefuse: Boolean get() = route == ROUTE_REFUSE

    fun firstDeviceStep(): AgentBrainRouteStep? = steps.firstOrNull { it.executor == EXECUTOR_DEVICE_TOOL }
    fun visualGoalOrDefault(defaultGoal: String): String {
        return steps.firstOrNull { it.executor == EXECUTOR_VISUAL_AGENT }?.goal?.takeIf { it.isNotBlank() } ?: defaultGoal
    }

    companion object {
        const val ROUTE_DEVICE_TOOL = "device_tool"
        const val ROUTE_VISUAL_AGENT = "visual_agent"
        const val ROUTE_HYBRID = "hybrid"
        const val ROUTE_ASK_USER = "ask_user"
        const val ROUTE_REFUSE = "refuse"
        const val EXECUTOR_DEVICE_TOOL = "device_tool"
        const val EXECUTOR_VISUAL_AGENT = "visual_agent"
    }
}

data class AgentBrainRouteStep(
    val executor: String,
    val tool: String,
    val args: JSONObject = JSONObject(),
    val goal: String = "",
    val risk: String = "low",
    val requiresConfirmation: Boolean = false,
    val reason: String = "",
) {
    fun argString(vararg keys: String): String {
        for (key in keys) {
            val value = args.optString(key).trim()
            if (value.isNotBlank()) return value
        }
        return ""
    }

    fun argNumber(vararg keys: String): Double? {
        for (key in keys) {
            if (!args.has(key) || args.isNull(key)) continue
            val value = runCatching { args.getDouble(key) }.getOrNull()
            if (value != null && value.isFinite()) return value
            args.optString(key).toDoubleOrNull()?.let { return it }
        }
        return null
    }
}

@Throws(IOException::class)
fun AiWorkerClient.requestAgentBrainRoute(
    goal: String,
    snapshot: AgentScreenSnapshot,
    recentActions: List<String>,
    deviceContext: AgentDeviceContextSnapshot?,
    agentMemory: JSONObject,
): AgentBrainRoutePlan {
    val payload = JSONObject().apply {
        put("action", "agent_brain_route")
        put("intent", "agent_brain_route")
        put("agentBrainRoute", true)
        put("message", goal)
        put("prompt", goal)
        put("goal", goal)
        put("agentGoal", goal)
        put("modelPreference", "deepseek_v4")
        put("model", "deepseek_v4")
        put("screenSnapshot", snapshot.toJson(includeImage = false))
        put("deviceContext", deviceContext?.json ?: JSONObject())
        put("agentMemory", agentMemory)
        put("recentAgentActions", JSONArray(recentActions))
        put("responseFormat", JSONObject().apply { put("includeAgentBrainRoute", true) })
        put("supportedAgentBrainRoutes", JSONArray(listOf("device_tool", "visual_agent", "hybrid", "ask_user", "refuse")))
        put(
            "supportedDeviceTools",
            JSONArray(
                listOf(
                    "open_app",
                    "open_system_settings",
                    "open_app_settings",
                    "set_brightness",
                    "set_screen_timeout",
                    "device_status",
                    "shizuku_status",
                    "request_shizuku_permission",
                    "set_animation_scale",
                    "force_stop_app",
                    "clear_app_data",
                    "uninstall_app",
                    "disable_app",
                    "enable_app",
                )
            )
        )
        put("client", "android-compose")
        put("clientFeature", "agent_brain_route_v1")
        put("now", System.currentTimeMillis())
    }

    val endpoints = endpointPlanForAgentBrain(endpoint)
    if (endpoints.isEmpty()) throw IOException("AI Worker endpoint 未配置")
    var lastError: IOException? = null
    for (base in endpoints) {
        for (candidate in endpointCandidates(base)) {
            try {
                return postAgentBrainRoute(candidate, payload)
            } catch (error: IOException) {
                lastError = error
                if (error is SocketTimeoutException || error.cause is SocketTimeoutException) continue
            }
        }
    }
    throw lastError ?: IOException("DeepSeek AgentBrain 路由请求失败")
}

@Throws(IOException::class)
private fun postAgentBrainRoute(endpoint: String, payload: JSONObject): AgentBrainRoutePlan {
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = AGENT_BRAIN_CONNECT_TIMEOUT_MS
        readTimeout = AGENT_BRAIN_READ_TIMEOUT_MS
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("X-Client", "android-compose-agent-brain")
    }
    return try {
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val body = readAgentBrainBody(connection, status)
        val data = body.toAgentBrainJsonOrNull()
        if (status !in 200..299) {
            throw IOException(data?.optString("error")?.takeIf { it.isNotBlank() } ?: body.take(160).ifBlank { "AgentBrain HTTP $status" })
        }
        val routeJson = data?.optJSONObject("agentBrainRoute")
            ?: data?.optJSONObject("agentBrain")
            ?: data?.optJSONObject("routePlan")
            ?: data?.optJSONObject("result")?.optJSONObject("agentBrainRoute")
            ?: data
            ?: throw IOException("AgentBrain 返回为空")
        parseAgentBrainRoutePlan(routeJson)
    } catch (error: SocketTimeoutException) {
        throw IOException("DeepSeek AgentBrain 路由请求超时：${endpoint.substringAfter("://")}", error)
    } finally {
        connection.disconnect()
    }
}

private fun parseAgentBrainRoutePlan(json: JSONObject): AgentBrainRoutePlan {
    val route = normalizeAgentBrainRoute(json.optString("route").ifBlank { json.optString("mode") })
    val stepsArray = json.optJSONArray("steps") ?: json.optJSONArray("actions") ?: JSONArray()
    val steps = buildList {
        for (index in 0 until stepsArray.length()) {
            val item = stepsArray.optJSONObject(index) ?: continue
            add(parseAgentBrainRouteStep(item, fallbackRoute = route))
        }
    }.take(4)
    val safeSteps = if (steps.isNotEmpty()) steps else if (route == AgentBrainRoutePlan.ROUTE_VISUAL_AGENT) {
        listOf(AgentBrainRouteStep(executor = AgentBrainRoutePlan.EXECUTOR_VISUAL_AGENT, tool = "visual_agent"))
    } else {
        emptyList()
    }
    return AgentBrainRoutePlan(
        route = route,
        confidence = json.optDouble("confidence", json.optDouble("score", 0.0)).toFloat().coerceIn(0f, 1f),
        risk = normalizeAgentBrainRisk(json.optString("risk").ifBlank { json.optString("riskLevel") }),
        reason = json.optString("reason").ifBlank { json.optString("rationale") }.take(260),
        question = json.optString("question").ifBlank { json.optString("ask") }.take(180),
        refusalReason = json.optString("refusalReason").ifBlank { json.optString("refuseReason") }.take(220),
        steps = safeSteps,
    )
}

private fun parseAgentBrainRouteStep(json: JSONObject, fallbackRoute: String): AgentBrainRouteStep {
    val executor = normalizeAgentBrainExecutor(json.optString("executor").ifBlank { json.optString("route") }.ifBlank { fallbackRoute })
    val args = json.optJSONObject("args") ?: json.optJSONObject("arguments") ?: JSONObject()
    val mergedArgs = JSONObject(args.toString()).apply {
        listOf("appName", "app", "packageName", "package", "page", "kind", "percent", "seconds", "minutes", "timeoutSeconds", "timeoutMs", "scale", "value").forEach { key ->
            if (!has(key) && json.has(key)) put(key, json.opt(key))
        }
    }
    val risk = normalizeAgentBrainRisk(json.optString("risk").ifBlank { json.optString("riskLevel") })
    return AgentBrainRouteStep(
        executor = executor,
        tool = normalizeAgentBrainTool(json.optString("tool").ifBlank { json.optString("action") }.ifBlank { json.optString("name") }.ifBlank { if (executor == AgentBrainRoutePlan.EXECUTOR_VISUAL_AGENT) "visual_agent" else "" }),
        args = mergedArgs,
        goal = json.optString("goal").ifBlank { json.optString("subgoal") }.ifBlank { mergedArgs.optString("goal") }.take(240),
        risk = risk,
        requiresConfirmation = json.optBoolean("requiresConfirmation", false) || json.optBoolean("confirm", false) || risk in setOf("high", "critical"),
        reason = json.optString("reason").ifBlank { json.optString("rationale") }.take(220),
    )
}

private fun normalizeAgentBrainRoute(value: String): String {
    return when (value.lowercase().replace('-', '_').trim()) {
        "device", "device_tool", "internal", "internal_tool" -> AgentBrainRoutePlan.ROUTE_DEVICE_TOOL
        "hybrid", "mixed", "mix" -> AgentBrainRoutePlan.ROUTE_HYBRID
        "ask", "ask_user", "need_user_help", "clarify" -> AgentBrainRoutePlan.ROUTE_ASK_USER
        "refuse", "deny", "blocked" -> AgentBrainRoutePlan.ROUTE_REFUSE
        else -> AgentBrainRoutePlan.ROUTE_VISUAL_AGENT
    }
}

private fun normalizeAgentBrainExecutor(value: String): String {
    return when (value.lowercase().replace('-', '_').trim()) {
        "device", "device_tool", "internal", "internal_tool" -> AgentBrainRoutePlan.EXECUTOR_DEVICE_TOOL
        else -> AgentBrainRoutePlan.EXECUTOR_VISUAL_AGENT
    }
}

private fun normalizeAgentBrainRisk(value: String): String {
    return when (value.lowercase().replace('-', '_').trim()) {
        "critical", "danger", "dangerous", "very_high" -> "critical"
        "high" -> "high"
        "medium", "mid" -> "medium"
        else -> "low"
    }
}

private fun normalizeAgentBrainTool(value: String): String {
    val raw = value.lowercase().replace('-', '_').trim()
    return when (raw) {
        "open_application", "launch_app", "app_open" -> "open_app"
        "settings", "open_settings", "system_settings" -> "open_system_settings"
        "app_settings", "app_info", "open_app_detail" -> "open_app_settings"
        "brightness", "screen_brightness" -> "set_brightness"
        "screen_timeout", "sleep_timeout" -> "set_screen_timeout"
        "health", "device_health" -> "device_status"
        "shell_status", "enhanced_status", "shizuku" -> "shizuku_status"
        "shizuku_permission", "request_shizuku" -> "request_shizuku_permission"
        "animation_scale" -> "set_animation_scale"
        "force_stop", "force_stop_application" -> "force_stop_app"
        "clear_data" -> "clear_app_data"
        "uninstall" -> "uninstall_app"
        "disable" -> "disable_app"
        "enable" -> "enable_app"
        "open_app", "open_system_settings", "open_app_settings", "set_brightness", "set_screen_timeout", "device_status", "shizuku_status", "request_shizuku_permission", "set_animation_scale", "force_stop_app", "clear_app_data", "uninstall_app", "disable_app", "enable_app", "visual_agent" -> raw
        else -> "visual_agent"
    }
}

private fun endpointPlanForAgentBrain(primary: String): List<String> {
    return listOf(primary.trim().trimEnd('/'))
        .filter { it.isNotBlank() }
        .distinct()
}

private fun endpointCandidates(cleanEndpoint: String): List<String> {
    return if (cleanEndpoint.endsWith("/chat") || cleanEndpoint.endsWith("/api/chat")) {
        listOf(cleanEndpoint)
    } else {
        listOf(cleanEndpoint, "$cleanEndpoint/chat", "$cleanEndpoint/api/chat").distinct()
    }
}

private fun readAgentBrainBody(connection: HttpURLConnection, status: Int): String {
    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
    return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
}

private fun String.toAgentBrainJsonOrNull(): JSONObject? {
    return try { takeIf { it.isNotBlank() }?.let { JSONObject(it) } } catch (_: Exception) { null }
}

private const val AGENT_BRAIN_CONNECT_TIMEOUT_MS = 10_000
private const val AGENT_BRAIN_READ_TIMEOUT_MS = 12_000

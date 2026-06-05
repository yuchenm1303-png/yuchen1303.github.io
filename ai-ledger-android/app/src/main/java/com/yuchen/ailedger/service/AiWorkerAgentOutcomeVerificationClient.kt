package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatModel
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

private const val AGENT_VERIFY_CONNECT_TIMEOUT_MS = 15_000
private const val AGENT_VERIFY_READ_TIMEOUT_MS = 60_000
private const val AGENT_VERIFY_VISION_ROUTE_ID = "qwen_vision"

data class AgentOutcomeVerificationResult(
    val isExpected: Boolean,
    val isWrong: Boolean,
    val confidence: Double = 0.0,
    val reason: String = "",
)

@Throws(IOException::class)
fun AiWorkerClient.requestAgentOutcomeVerification(
    goal: String,
    action: CloudAgentStep,
    snapshot: AgentScreenSnapshot,
    modelPreference: ChatModel = ChatModel.Auto,
): AgentOutcomeVerificationResult {
    val payload = buildAgentOutcomePayload(goal, action, snapshot, modelPreference)
    val endpoints = (listOf(endpoint) + AiWorkerClient.DEFAULT_FALLBACK_ENDPOINTS)
        .map { it.trim().trimEnd('/') }
        .filter { it.isNotBlank() }
        .distinct()
    var lastError: IOException? = null
    for (base in endpoints) {
        for (candidate in agentVerifyEndpointCandidates(base)) {
            try {
                return postAgentOutcomeVerification(candidate, payload)
            } catch (error: IOException) {
                lastError = error
                if (error is SocketTimeoutException || error.cause is SocketTimeoutException) break
            }
        }
    }
    throw lastError ?: IOException("云端智能体结果验证失败")
}

private fun buildAgentOutcomePayload(
    goal: String,
    action: CloudAgentStep,
    snapshot: AgentScreenSnapshot,
    modelPreference: ChatModel,
): JSONObject {
    val systemPrompt = agentOutcomeSystemPrompt()
    val cleanGoal = goal.trim().take(240)
    val modelId = if (snapshot.hasVisualImage) AGENT_VERIFY_VISION_ROUTE_ID else if (modelPreference == ChatModel.Auto) ChatModel.Kimi.id else modelPreference.id
    val snapshotText = snapshot.toJson(includeImage = false)
    val message = buildAgentOutcomeMessage(cleanGoal, action, snapshotText, snapshot.visual)
    return JSONObject().apply {
        put("action", "chat")
        put("intent", "agent_outcome_verification")
        put("agentMode", true)
        put("computerUseMode", true)
        put("visionFirst", snapshot.hasVisualImage)
        put("coordinateProtocol", "normalized_screen_0_1")
        put("agentGoal", cleanGoal)
        put("agentAction", action.toAgentJson())
        put("screenSnapshot", snapshot.toJson(includeImage = true))
        put("screenSnapshotText", snapshotText)
        put("hasScreenshot", snapshot.hasVisualImage)
        put("hasImage", snapshot.hasVisualImage)
        put("hasImages", snapshot.hasVisualImage)
        put("imageCount", if (snapshot.hasVisualImage) 1 else 0)
        snapshot.visual?.takeIf { it.hasImage }?.let { visual ->
            val imageItem = JSONObject().apply {
                put("mimeType", visual.mimeType)
                put("base64Data", visual.base64Jpeg)
                put("base64", visual.base64Jpeg)
                put("width", visual.width)
                put("height", visual.height)
                put("displayWidth", visual.displayWidth)
                put("displayHeight", visual.displayHeight)
                put("source", visual.source)
                put("reason", visual.reason)
            }
            put("screenshot", imageItem)
            put("images", JSONArray().apply { put(imageItem) })
            put("attachments", JSONArray().apply { put(imageItem) })
            put("vision", JSONObject().apply {
                put("enabled", true)
                put("provider", "qwen")
                put("route", AGENT_VERIFY_VISION_ROUTE_ID)
                put("coordinateSystem", "normalized_screen_0_1")
                put("displayWidth", visual.displayWidth)
                put("displayHeight", visual.displayHeight)
            })
        } ?: run {
            put("images", JSONArray())
            put("attachments", JSONArray())
            put("vision", JSONObject().apply { put("enabled", false) })
        }
        put("modelPreference", modelId)
        put("aiModelPreference", modelId)
        put("requestedModelPreference", modelId)
        put("model", modelId)
        put("modelId", modelId)
        put("client", "android-compose")
        put("clientVersion", "compose-native-agent-outcome-v1")
        put("systemPrompt", systemPrompt)
        put("message", message)
        put("prompt", message)
        put("text", message)
        put("messages", JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
            put(JSONObject().apply { put("role", "user"); put("content", message) })
        })
        put("responseFormat", JSONObject().apply {
            put("type", "json_object")
            put("includeOutcomeVerification", true)
        })
        put("now", System.currentTimeMillis())
    }
}

private fun buildAgentOutcomeMessage(goal: String, action: CloudAgentStep, snapshotJsonWithoutImage: JSONObject, visual: AgentScreenVisual?): String {
    return buildString {
        append("目标：").append(goal).append('\n')
        append("刚执行的动作：").append(action.toAgentJson()).append('\n')
        append("动作后的结构化快照：").append(snapshotJsonWithoutImage).append('\n')
        if (visual?.hasImage == true) {
            append("本次包含动作后的屏幕截图，请以截图为主判断动作是否到达预期页面。\n")
            append("如果目标是进入微信发现页，截图中出现朋友圈、视频号、搜一搜、看一看等入口时，应判断 isExpected=true。\n")
        }
        append("请只返回 JSON：{\"isExpected\":true/false,\"isWrong\":true/false,\"confidence\":0到1,\"reason\":\"简短原因\"}")
    }
}

private fun agentOutcomeSystemPrompt(): String = """
你是手机智能体的结果验证器。
输入是用户目标、刚执行的动作、动作后的屏幕截图和辅助节点。
有截图时必须优先看截图，节点只是辅助信息，节点缺失不能直接判定失败。
只判断这一步是否到达预期页面，不要规划下一步。
如果截图中已经出现目标页面的明显入口或标题，返回 isExpected=true。
只有明确进入错误页面或明显偏离目标时，返回 isWrong=true。
无法确定时 isExpected=false, isWrong=false。
必须只返回 JSON。
""".trimIndent()

private fun postAgentOutcomeVerification(endpoint: String, payload: JSONObject): AgentOutcomeVerificationResult {
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = AGENT_VERIFY_CONNECT_TIMEOUT_MS
        readTimeout = AGENT_VERIFY_READ_TIMEOUT_MS
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json, text/plain")
        setRequestProperty("X-Client", "android-compose-agent")
    }
    return try {
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val body = connection.agentVerifyReadBody(status)
        val data = body.agentVerifyJsonOrNull()
        if (status !in 200..299) {
            val message = data?.optString("error")?.takeIf { it.isNotBlank() }
                ?: data?.optString("message")?.takeIf { it.isNotBlank() }
                ?: body.take(120).ifBlank { "云端结果验证失败：HTTP $status" }
            throw IOException(message)
        }
        parseAgentOutcome(data) ?: extractAgentOutcomeFromText(body) ?: AgentOutcomeVerificationResult(false, false, 0.0, "云端没有返回结构化验证结果")
    } catch (error: SocketTimeoutException) {
        throw IOException("云端结果验证超时：${endpoint.substringAfter("://")}", error)
    } finally {
        connection.disconnect()
    }
}

private fun parseAgentOutcome(root: JSONObject?): AgentOutcomeVerificationResult? {
    val item = root?.optJSONObject("outcome")
        ?: root?.optJSONObject("verification")
        ?: root?.optJSONObject("result")?.optJSONObject("outcome")
        ?: root?.optJSONObject("data")?.optJSONObject("outcome")
        ?: root?.takeIf { it.has("isExpected") || it.has("expected") || it.has("isWrong") || it.has("wrong") }
        ?: return null
    val expected = item.optFlexibleBoolean("isExpected") ?: item.optFlexibleBoolean("expected") ?: item.optFlexibleBoolean("success") ?: false
    val wrong = item.optFlexibleBoolean("isWrong") ?: item.optFlexibleBoolean("wrong") ?: item.optFlexibleBoolean("wrongPage") ?: false
    val confidence = runCatching { item.optDouble("confidence", if (expected || wrong) 0.8 else 0.0) }.getOrDefault(0.0)
    val reason = item.optString("reason").ifBlank { item.optString("message") }
    return AgentOutcomeVerificationResult(expected, wrong, confidence, reason)
}

private fun extractAgentOutcomeFromText(text: String): AgentOutcomeVerificationResult? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return runCatching { parseAgentOutcome(JSONObject(text.substring(start, end + 1))) }.getOrNull()
}

private fun CloudAgentStep.toAgentJson(): JSONObject = JSONObject().apply {
    put("type", type)
    targetNodeId?.let { put("targetNodeId", it) }
    targetText?.let { put("targetText", it) }
    text?.let { put("text", it) }
    direction?.let { put("direction", it) }
    reason?.let { put("reason", it) }
    put("riskLevel", riskLevel)
    put("requiresConfirmation", requiresConfirmation)
    appName?.let { put("appName", it) }
    packageName?.let { put("packageName", it) }
    x?.let { put("x", it) }
    y?.let { put("y", it) }
    durationMs?.let { put("durationMs", it) }
}

private fun agentVerifyEndpointCandidates(cleanEndpoint: String): List<String> {
    val knownChatPath = cleanEndpoint.endsWith("/chat") || cleanEndpoint.endsWith("/api/chat")
    if (knownChatPath) return listOf(cleanEndpoint)
    return listOf(cleanEndpoint, "$cleanEndpoint/chat", "$cleanEndpoint/api/chat").distinct()
}

private fun HttpURLConnection.agentVerifyReadBody(status: Int): String {
    val stream = if (status in 200..299) inputStream else errorStream
    return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
}

private fun String.agentVerifyJsonOrNull(): JSONObject? {
    return try { takeIf { it.isNotBlank() }?.let { JSONObject(it) } } catch (_: Exception) { null }
}

private fun JSONObject.optFlexibleBoolean(name: String): Boolean? {
    if (!has(name) || isNull(name)) return null
    return when (val value = opt(name)) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.equals("true", ignoreCase = true) || value == "1" || value.equals("yes", ignoreCase = true)
        else -> null
    }
}

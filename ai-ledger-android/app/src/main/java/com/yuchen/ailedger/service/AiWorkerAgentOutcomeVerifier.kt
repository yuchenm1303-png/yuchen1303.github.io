package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatModel
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

private const val AGENT_OUTCOME_CONNECT_TIMEOUT_MS = 12_000
private const val AGENT_OUTCOME_READ_TIMEOUT_MS = 45_000
private const val AGENT_OUTCOME_VISION_ROUTE_ID = "qwen_vision"

data class CloudAgentOutcomeVerification(
    val result: String,
    val reason: String,
) {
    val isExpected: Boolean get() = result == RESULT_EXPECTED
    val isWrong: Boolean get() = result == RESULT_WRONG
    val isUncertain: Boolean get() = result == RESULT_UNCERTAIN

    companion object {
        const val RESULT_EXPECTED = "expected"
        const val RESULT_WRONG = "wrong"
        const val RESULT_UNCERTAIN = "uncertain"

        fun fromJson(json: JSONObject?): CloudAgentOutcomeVerification? {
            if (json == null) return null
            val container = json.optJSONObject("outcomeVerification")
                ?: json.optJSONObject("verification")
                ?: json.optJSONObject("agentOutcome")
                ?: json
            val rawResult = container.optString("result")
                .ifBlank { container.optString("outcome") }
                .ifBlank { container.optString("status") }
                .lowercase()
                .trim()
            val normalized = when {
                rawResult in setOf(RESULT_EXPECTED, "ok", "success", "correct", "matched", "pass") -> RESULT_EXPECTED
                rawResult in setOf(RESULT_WRONG, "failed", "failure", "incorrect", "mismatch", "misclick", "bad") -> RESULT_WRONG
                rawResult in setOf(RESULT_UNCERTAIN, "unknown", "unsure", "ambiguous") -> RESULT_UNCERTAIN
                else -> RESULT_UNCERTAIN
            }
            val reason = container.optString("reason")
                .ifBlank { container.optString("message") }
                .ifBlank { "云端无法确定页面结果" }
            return CloudAgentOutcomeVerification(normalized, reason.take(160))
        }
    }
}

@Throws(IOException::class)
fun AiWorkerClient.requestAgentOutcomeVerification(
    goal: String,
    action: CloudAgentStep,
    snapshot: AgentScreenSnapshot,
    modelPreference: ChatModel = ChatModel.Auto,
): CloudAgentOutcomeVerification {
    if (!snapshot.hasVisualImage) return CloudAgentOutcomeVerification(
        result = CloudAgentOutcomeVerification.RESULT_UNCERTAIN,
        reason = "没有截图，无法进行云端视觉复核",
    )
    val payload = buildOutcomeVerificationPayload(goal, action, snapshot, modelPreference)
    val endpoints = (listOf(endpoint) + AiWorkerClient.DEFAULT_FALLBACK_ENDPOINTS)
        .map { it.trim().trimEnd('/') }
        .filter { it.isNotBlank() }
        .distinct()
    var lastError: IOException? = null
    for (base in endpoints) {
        for (candidate in outcomeEndpointCandidates(base)) {
            try {
                return postOutcomeVerification(candidate, payload)
            } catch (error: IOException) {
                lastError = error
                if (error is SocketTimeoutException || error.cause is SocketTimeoutException) break
            }
        }
    }
    throw lastError ?: IOException("云端智能体结果验证请求失败")
}

private fun buildOutcomeVerificationPayload(
    goal: String,
    action: CloudAgentStep,
    snapshot: AgentScreenSnapshot,
    modelPreference: ChatModel,
): JSONObject {
    val cleanGoal = goal.trim().take(240)
    val snapshotForText = snapshot.toJson(includeImage = false)
    val message = buildOutcomeVerificationMessage(cleanGoal, action, snapshotForText, snapshot.visual)
    val modelId = if (snapshot.hasVisualImage) AGENT_OUTCOME_VISION_ROUTE_ID else if (modelPreference == ChatModel.Auto) ChatModel.Kimi.id else modelPreference.id
    return JSONObject().apply {
        put("action", "chat")
        put("intent", "agent_outcome_verification")
        put("agentMode", true)
        put("computerUseMode", true)
        put("agentGoal", cleanGoal)
        put("executedAgentStep", action.toJsonCompat())
        put("screenSnapshot", snapshot.toJson(includeImage = true))
        put("screenSnapshotText", snapshotForText)
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
                put("route", AGENT_OUTCOME_VISION_ROUTE_ID)
                put("coordinateSystem", "display")
                put("screenshotWidth", visual.width)
                put("screenshotHeight", visual.height)
                put("displayWidth", visual.displayWidth)
                put("displayHeight", visual.displayHeight)
            })
        }
        put("modelPreference", modelId)
        put("aiModelPreference", modelId)
        put("requestedModelPreference", modelId)
        put("model", modelId)
        put("modelId", modelId)
        put("client", "android-compose")
        put("clientVersion", "compose-native-agent-outcome-verifier-v1")
        put("systemPrompt", outcomeVerifierSystemPrompt())
        put("message", message)
        put("prompt", message)
        put("text", message)
        put("messages", JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", outcomeVerifierSystemPrompt()) })
            put(JSONObject().apply { put("role", "user"); put("content", message) })
        })
        put("responseFormat", JSONObject().apply {
            put("type", "json_object")
            put("includeOutcomeVerification", true)
        })
        put("now", System.currentTimeMillis())
    }
}

private fun buildOutcomeVerificationMessage(
    goal: String,
    action: CloudAgentStep,
    snapshotJsonWithoutImage: JSONObject,
    visual: AgentScreenVisual?,
): String {
    return buildString {
        append("用户目标：").append(goal).append('\n')
        append("刚执行的动作：").append(action.toJsonCompat()).append('\n')
        append("动作后的结构化屏幕快照：").append(snapshotJsonWithoutImage).append('\n')
        if (visual?.hasImage == true) {
            append("动作后的截图尺寸为 ").append(visual.width).append("x").append(visual.height)
                .append("，真实屏幕尺寸为 ").append(visual.displayWidth).append("x").append(visual.displayHeight).append("。\n")
            append("请根据截图和节点判断：这个页面是否符合用户目标和刚才动作的预期结果。")
        } else {
            append("当前没有截图，只能根据节点文本谨慎判断。")
        }
    }
}

private fun outcomeVerifierSystemPrompt(): String = """
你是手机 Computer Use 智能体的结果验证器，只判断刚执行的动作是否把页面带到了预期位置。
你不是下一步规划器，不要给点击坐标，不要继续执行任务。
必须只返回 JSON，不要 Markdown。

返回格式：{"outcomeVerification":{"result":"expected|wrong|uncertain","reason":"一句话说明依据"}}

判断规则：
1. expected：动作后的页面明确符合用户目标，或者已经进入目标相关页面。
2. wrong：动作后的页面明显偏离目标，例如本应进入底部导航页却进入了聊天框、详情页、广告页、支付页、发送页等。
3. uncertain：截图或节点不足、页面仍在加载、或者你不能可靠判断。
4. 只要不确定，就返回 uncertain，不要猜。
5. 涉及发送、支付、授权、删除、公开发布等高风险状态，如果用户目标没有明确要求，优先 wrong 或 uncertain。
""".trimIndent()

private fun outcomeEndpointCandidates(cleanEndpoint: String): List<String> {
    val knownChatPath = cleanEndpoint.endsWith("/chat") || cleanEndpoint.endsWith("/api/chat")
    if (knownChatPath) return listOf(cleanEndpoint)
    return listOf(cleanEndpoint, "$cleanEndpoint/chat", "$cleanEndpoint/api/chat").distinct()
}

private fun postOutcomeVerification(endpoint: String, payload: JSONObject): CloudAgentOutcomeVerification {
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = AGENT_OUTCOME_CONNECT_TIMEOUT_MS
        readTimeout = AGENT_OUTCOME_READ_TIMEOUT_MS
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json, text/plain")
        setRequestProperty("X-Client", "android-compose-agent-outcome")
    }
    return try {
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val body = connection.outcomeReadBody(status)
        val data = body.outcomeJsonOrNull()
        if (status !in 200..299) {
            val message = data?.optString("error")?.takeIf { it.isNotBlank() }
                ?: data?.optString("message")?.takeIf { it.isNotBlank() }
                ?: body.take(120).ifBlank { "云端智能体结果验证失败：HTTP $status" }
            throw IOException(message)
        }
        CloudAgentOutcomeVerification.fromJson(data)
            ?: extractOutcomeVerificationFromText(body)
            ?: CloudAgentOutcomeVerification(CloudAgentOutcomeVerification.RESULT_UNCERTAIN, "云端没有返回有效验证结果")
    } catch (error: SocketTimeoutException) {
        throw IOException("云端智能体结果验证超时：${endpoint.substringAfter("://")}", error)
    } finally {
        connection.disconnect()
    }
}

private fun HttpURLConnection.outcomeReadBody(status: Int): String {
    val stream = if (status in 200..299) inputStream else errorStream
    return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
}

private fun String.outcomeJsonOrNull(): JSONObject? {
    return try { takeIf { it.isNotBlank() }?.let { JSONObject(it) } } catch (_: Exception) { null }
}

private fun extractOutcomeVerificationFromText(text: String): CloudAgentOutcomeVerification? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return try { CloudAgentOutcomeVerification.fromJson(JSONObject(text.substring(start, end + 1))) } catch (_: Exception) { null }
}

private fun CloudAgentStep.toJsonCompat(): JSONObject {
    return JSONObject().apply {
        put("type", type)
        targetNodeId?.let { put("targetNodeId", it) }
        targetText?.let { put("targetText", it) }
        appName?.let { put("appName", it) }
        packageName?.let { put("packageName", it) }
        text?.let { put("text", it) }
        direction?.let { put("direction", it) }
        x?.let { put("x", it) }
        y?.let { put("y", it) }
        durationMs?.let { put("durationMs", it) }
        reason?.let { put("reason", it) }
        put("riskLevel", riskLevel)
        put("requiresConfirmation", requiresConfirmation)
    }
}

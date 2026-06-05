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
    val expectedProgress: Boolean = false,
    val confidence: Float = 0f,
    val nextHint: String = "",
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
                ?: json.optJSONObject("outcome")
                ?: json.optJSONObject("result")
                ?: json

            val explicitExpected = container.optFlexibleBoolean("isExpected")
                ?: container.optFlexibleBoolean("expected")
                ?: container.optFlexibleBoolean("success")
                ?: container.optFlexibleBoolean("reachedTarget")
                ?: container.optFlexibleBoolean("onExpectedPage")
            val explicitProgress = container.optFlexibleBoolean("expectedProgress")
                ?: container.optFlexibleBoolean("progress")
                ?: container.optFlexibleBoolean("isProgress")
                ?: container.optFlexibleBoolean("onRightTrack")
                ?: container.optFlexibleBoolean("closerToGoal")
            val explicitWrong = container.optFlexibleBoolean("isWrong")
                ?: container.optFlexibleBoolean("wrong")
                ?: container.optFlexibleBoolean("failed")
                ?: container.optFlexibleBoolean("wrongPage")
                ?: container.optFlexibleBoolean("offTarget")

            val rawResult = container.optString("result")
                .ifBlank { container.optString("outcome") }
                .ifBlank { container.optString("status") }
                .lowercase()
                .trim()

            val normalized = when {
                explicitWrong == true -> RESULT_WRONG
                explicitExpected == true -> RESULT_EXPECTED
                rawResult in setOf(RESULT_EXPECTED, "ok", "success", "correct", "matched", "pass") -> RESULT_EXPECTED
                rawResult in setOf(RESULT_WRONG, "failed", "failure", "incorrect", "mismatch", "misclick", "bad") -> RESULT_WRONG
                else -> RESULT_UNCERTAIN
            }
            val progress = normalized == RESULT_EXPECTED || explicitProgress == true || rawResult in setOf("progress", "expected_progress", "closer", "closer_to_goal", "right_track")
            val confidence = container.optNullableFloat("confidence")
                ?: container.optNullableFloat("score")
                ?: when {
                    normalized == RESULT_EXPECTED || normalized == RESULT_WRONG -> 0.75f
                    progress -> 0.6f
                    else -> 0.35f
                }
            val reason = container.optString("reason")
                .ifBlank { container.optString("message") }
                .ifBlank { container.optString("explanation") }
                .ifBlank { container.optString("rationale") }
                .ifBlank { "云端无法确定页面结果" }
            val nextHint = container.optString("nextHint")
                .ifBlank { container.optString("hint") }
                .ifBlank { container.optString("suggestion") }

            return CloudAgentOutcomeVerification(
                result = normalized,
                reason = reason.take(180),
                expectedProgress = progress && normalized != RESULT_WRONG,
                confidence = confidence.coerceIn(0f, 1f),
                nextHint = nextHint.take(120),
            )
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
        put("lastAction", action.toJsonCompat())
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
                put("coordinateSystem", "normalized_screen_0_1")
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
        put("clientVersion", "compose-native-agent-outcome-verifier-v2")
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
            put("includeExpectedProgress", true)
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
        append("用户原始目标：").append(goal).append('\n')
        append("刚执行的动作：").append(action.toJsonCompat()).append('\n')
        append("动作后的结构化屏幕快照：").append(snapshotJsonWithoutImage).append('\n')
        if (visual?.hasImage == true) {
            append("动作后的截图尺寸为 ").append(visual.width).append("x").append(visual.height)
                .append("，真实屏幕尺寸为 ").append(visual.displayWidth).append("x").append(visual.displayHeight).append("。\n")
            append("请以截图为主、节点为辅，判断当前页面是否比执行前更接近目标，是否已经到达目标页或正确中间页，是否明显走错，以及下一轮是否应该继续规划。")
        } else {
            append("当前没有截图，只能根据节点文本谨慎判断。")
        }
    }
}

private fun outcomeVerifierSystemPrompt(): String = """
你是 Android 手机 Computer Use 智能体的通用目标状态验证器，只能验证刚执行动作后的状态，不规划下一步动作。
截图是主输入，辅助节点文本只是补充；不要因为节点少就直接判定失败或等待。
必须只返回 JSON，不要 Markdown，不要解释额外文本。

返回格式：
{"outcomeVerification":{"isExpected":false,"expectedProgress":false,"isWrong":false,"confidence":0.0,"reason":"一句话说明依据","nextHint":"可选的下一步观察提示"}}

字段语义：
1. isExpected=true：当前页面已经达到用户目标，或者已经处在可以直接完成目标的页面。
2. expectedProgress=true：当前页面尚未完成最终目标，但比上一步更接近目标，或者进入了合理的中间页面/导航路径，应该继续观察和规划，而不是返回。
3. isWrong=true：当前页面明显偏离目标，进入了与目标无关或高风险的错误页面，才允许前端自动返回。
4. confidence：0 到 1。只有明显看到目标页、正确中间页或明显走错时才给较高置信度。
5. reason：说明你依据截图和节点看到的通用页面状态，不要依赖固定 App 脚本。
6. nextHint：如果 expectedProgress 或 uncertain，可以简短提示下一轮应该继续找什么入口或目标线索。

判断规则：
- 只要当前页面明显更接近用户目标，就返回 expectedProgress=true，即使还没完成最终目标。
- 只有明确走错时才返回 isWrong=true；不确定不能当作 wrong。
- 如果截图显示页面仍在加载、动画过渡、信息不足或无法可靠判断，返回三个布尔值都为 false。
- 涉及发送、支付、授权、删除、公开发布等高风险状态，如果用户目标没有明确要求，优先 isWrong=true 或保持不确定。
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

private fun JSONObject.optFlexibleBoolean(name: String): Boolean? {
    if (!has(name) || isNull(name)) return null
    val raw = opt(name)
    return when (raw) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        is String -> when (raw.lowercase().trim()) {
            "true", "yes", "1", "expected", "progress", "wrong", "success", "failed" -> true
            "false", "no", "0", "uncertain", "unknown", "" -> false
            else -> null
        }
        else -> null
    }
}

private fun JSONObject.optNullableFloat(name: String): Float? {
    if (!has(name) || isNull(name)) return null
    return runCatching { optDouble(name).toFloat() }.getOrNull()
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
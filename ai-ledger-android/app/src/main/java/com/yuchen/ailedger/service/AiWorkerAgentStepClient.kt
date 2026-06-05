package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatModel
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

private const val AGENT_STEP_CONNECT_TIMEOUT_MS = 15_000
private const val AGENT_STEP_READ_TIMEOUT_MS = 60_000
private const val AGENT_VISION_ROUTE_ID = "qwen_vision"

@Throws(IOException::class)
fun AiWorkerClient.requestAgentStep(
    goal: String,
    snapshot: AgentScreenSnapshot,
    modelPreference: ChatModel = ChatModel.Auto,
): CloudAgentStep {
    val payload = buildAgentStepPayload(goal, snapshot, modelPreference)
    val endpoints = (listOf(endpoint) + AiWorkerClient.DEFAULT_FALLBACK_ENDPOINTS)
        .map { it.trim().trimEnd('/') }
        .filter { it.isNotBlank() }
        .distinct()
    var lastError: IOException? = null
    for (base in endpoints) {
        for (candidate in agentEndpointCandidates(base)) {
            try {
                return postAgentStep(candidate, payload)
            } catch (error: IOException) {
                lastError = error
                if (error is SocketTimeoutException || error.cause is SocketTimeoutException) break
            }
        }
    }
    throw lastError ?: IOException("云端智能体规划请求失败")
}

private fun buildAgentStepPayload(
    goal: String,
    snapshot: AgentScreenSnapshot,
    modelPreference: ChatModel,
): JSONObject {
    val instruction = agentPlannerSystemPrompt()
    val cleanGoal = goal.trim().take(240)
    val modelId = if (snapshot.hasVisualImage) AGENT_VISION_ROUTE_ID else if (modelPreference == ChatModel.Auto) ChatModel.Kimi.id else modelPreference.id
    val snapshotForText = snapshot.toJson(includeImage = false)
    val plannerMessage = buildPlannerMessage(cleanGoal, snapshotForText, snapshot.visual)
    return JSONObject().apply {
        put("action", "chat")
        put("intent", "agent_step")
        put("agentMode", true)
        put("computerUseMode", true)
        put("visionFirst", snapshot.hasVisualImage)
        put("agentGoal", cleanGoal)
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
                put("route", AGENT_VISION_ROUTE_ID)
                put("coordinateSystem", "display")
                put("screenshotWidth", visual.width)
                put("screenshotHeight", visual.height)
                put("displayWidth", visual.displayWidth)
                put("displayHeight", visual.displayHeight)
            })
        } ?: run {
            put("images", JSONArray())
            put("attachments", JSONArray())
            put("vision", JSONObject().apply { put("enabled", false) })
        }
        put("supportedAgentSteps", JSONArray(CloudAgentStep.supportedTypes.toList()))
        put("modelPreference", modelId)
        put("aiModelPreference", modelId)
        put("requestedModelPreference", modelId)
        put("model", modelId)
        put("modelId", modelId)
        put("client", "android-compose")
        put("clientVersion", if (snapshot.hasVisualImage) "compose-native-vision-first-v3" else "compose-native-agent-v3")
        put("systemPrompt", instruction)
        put("message", plannerMessage)
        put("prompt", plannerMessage)
        put("text", plannerMessage)
        put("messages", JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", instruction) })
            put(JSONObject().apply { put("role", "user"); put("content", plannerMessage) })
        })
        put("responseFormat", JSONObject().apply {
            put("type", "json_object")
            put("includeAgentStep", true)
        })
        put("now", System.currentTimeMillis())
    }
}

private fun buildPlannerMessage(goal: String, snapshotJsonWithoutImage: JSONObject, visual: AgentScreenVisual?): String {
    return buildString {
        append("目标：").append(goal).append('\n')
        append("结构化快照：").append(snapshotJsonWithoutImage).append('\n')
        if (visual?.hasImage == true) {
            append("本次包含屏幕截图，真实屏幕尺寸为 ")
                .append(visual.displayWidth).append("x").append(visual.displayHeight).append("。\n")
            append("请先看截图，再参考节点；节点可能缺失。tap_xy 必须使用真实屏幕坐标。\n")
            append("如果当前页面走错，请返回 back；如果目标可见，请返回 tap_xy；如果页面刚变化，请 wait。\n")
        } else {
            append("当前没有截图，只能根据节点谨慎规划。\n")
        }
        append("请每次只返回一步 agentStep JSON。")
    }
}

private fun agentPlannerSystemPrompt(): String = """
你是手机智能体规划器。
如果请求包含截图，截图是主输入，节点只是辅助信息。
每次只返回一步 JSON，不要输出 Markdown。

支持动作：open_app, home, back, recents, notifications, quick_settings, tap_node, tap_xy, input_text, scroll, swipe, wait, finish, need_user_help。
JSON 格式：{"agentStep":{"type":"open_app|home|back|recents|notifications|quick_settings|tap_node|tap_xy|input_text|scroll|swipe|wait|finish|need_user_help","targetNodeId":"可选节点 id","targetText":"可选目标文字","appName":"可选应用名","packageName":"可选包名","text":"可选输入文字","direction":"up|down|left|right 可选","x":可选数字,"y":可选数字,"durationMs":可选毫秒,"reason":"简短行动理由","riskLevel":"low|medium|high","requiresConfirmation":false}}

规则：
1. 有截图时必须先看截图，不能因为节点少就直接 need_user_help。
2. 截图里目标、导航入口、返回键、输入框可见时，直接返回对应动作。
3. tap_xy 使用真实屏幕坐标 displayWidth/displayHeight。
4. 当前页面走错时，优先 back。
5. 页面刚变化或加载中时，返回 wait。
6. 微信朋友圈常见路径：先回微信主会话页，再点底部“发现”，再点“朋友圈”。
7. 目标属于某个 App 且当前不在该 App 时，优先 open_app。
8. 目标已完成时，返回 finish。
9. 只有截图和节点都无法判断且继续操作可能误触时，才返回 need_user_help。
""".trimIndent()

private fun agentEndpointCandidates(cleanEndpoint: String): List<String> {
    val knownChatPath = cleanEndpoint.endsWith("/chat") || cleanEndpoint.endsWith("/api/chat")
    if (knownChatPath) return listOf(cleanEndpoint)
    return listOf(cleanEndpoint, "$cleanEndpoint/chat", "$cleanEndpoint/api/chat").distinct()
}

private fun postAgentStep(endpoint: String, payload: JSONObject): CloudAgentStep {
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = AGENT_STEP_CONNECT_TIMEOUT_MS
        readTimeout = AGENT_STEP_READ_TIMEOUT_MS
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json, text/plain")
        setRequestProperty("X-Client", "android-compose-agent")
    }
    return try {
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val body = connection.agentReadBody(status)
        val data = body.agentJsonOrNull()
        if (status !in 200..299) {
            val message = data?.optString("error")?.takeIf { it.isNotBlank() }
                ?: data?.optString("message")?.takeIf { it.isNotBlank() }
                ?: body.take(120).ifBlank { "云端智能体规划失败：HTTP $status" }
            throw IOException(message)
        }
        CloudAgentStep.fromJson(data) ?: extractAgentStepFromText(body)?.let { return it }
            ?: throw IOException("云端没有返回有效的智能体下一步动作")
    } catch (error: SocketTimeoutException) {
        throw IOException("云端智能体规划超时：${endpoint.substringAfter("://")}", error)
    } finally {
        connection.disconnect()
    }
}

private fun HttpURLConnection.agentReadBody(status: Int): String {
    val stream = if (status in 200..299) inputStream else errorStream
    return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
}

private fun String.agentJsonOrNull(): JSONObject? {
    return try { takeIf { it.isNotBlank() }?.let { JSONObject(it) } } catch (_: Exception) { null }
}

private fun extractAgentStepFromText(text: String): CloudAgentStep? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return try { CloudAgentStep.fromJson(JSONObject(text.substring(start, end + 1))) } catch (_: Exception) { null }
}

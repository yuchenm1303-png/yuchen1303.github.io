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
    val modelId = if (modelPreference == ChatModel.Auto) ChatModel.Kimi.id else modelPreference.id
    val snapshotForText = snapshot.toJson(includeImage = false)
    return JSONObject().apply {
        put("action", "chat")
        put("intent", "agent_step")
        put("agentMode", true)
        put("computerUseMode", true)
        put("agentGoal", cleanGoal)
        put("screenSnapshot", snapshot.toJson(includeImage = true))
        put("screenSnapshotText", snapshotForText)
        put("hasScreenshot", snapshot.hasVisualImage)
        snapshot.visual?.takeIf { it.hasImage }?.let { visual ->
            put("screenshot", JSONObject().apply {
                put("mimeType", visual.mimeType)
                put("width", visual.width)
                put("height", visual.height)
                put("base64", visual.base64Jpeg)
                put("source", visual.source)
                put("reason", visual.reason)
            })
        }
        put("supportedAgentSteps", JSONArray(CloudAgentStep.supportedTypes.toList()))
        put("modelPreference", modelId)
        put("aiModelPreference", modelId)
        put("client", "android-compose")
        put("clientVersion", "compose-native-computer-use-v1")
        put("systemPrompt", instruction)
        put("message", buildPlannerMessage(cleanGoal, snapshotForText, snapshot.hasVisualImage))
        put("messages", JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", instruction) })
            put(JSONObject().apply {
                put("role", "user")
                put("content", buildPlannerMessage(cleanGoal, snapshotForText, snapshot.hasVisualImage))
            })
        })
        put("responseFormat", JSONObject().apply {
            put("type", "json_object")
            put("includeAgentStep", true)
        })
        put("now", System.currentTimeMillis())
    }
}

private fun buildPlannerMessage(goal: String, snapshotJsonWithoutImage: JSONObject, hasScreenshot: Boolean): String {
    return buildString {
        append("目标：").append(goal).append('\n')
        append("当前屏幕结构化快照：").append(snapshotJsonWithoutImage).append('\n')
        if (hasScreenshot) {
            append("当前请求还包含一张屏幕截图。无障碍节点不足时，请直接根据截图中的可见文字、图标和位置返回 tap_xy 坐标。坐标必须使用截图/屏幕坐标系。\n")
        } else {
            append("当前请求没有截图。请优先根据节点树规划；节点不足时可返回 wait、swipe、back 或 need_user_help。\n")
        }
        append("请每次只返回一步 agentStep JSON。")
    }
}

private fun agentPlannerSystemPrompt(): String = """
你是手机 Computer Use 智能体任务规划器，不是普通聊天助手。
你只能基于 screenSnapshot、可选 screenshot 和用户目标决定下一步，不要假装已经点击、输入或滚动。
每次只返回一步，必须返回 JSON，不要输出 Markdown，不要输出解释性正文。

支持动作：open_app, home, back, recents, notifications, quick_settings, tap_node, tap_xy, input_text, scroll, swipe, wait, finish, need_user_help。
JSON 格式：{"agentStep":{"type":"open_app|home|back|recents|notifications|quick_settings|tap_node|tap_xy|input_text|scroll|swipe|wait|finish|need_user_help","targetNodeId":"可选节点 id","targetText":"可选目标文字","appName":"可选应用名","packageName":"可选包名","text":"可选输入文字","direction":"up|down|left|right 可选","x":可选数字,"y":可选数字,"durationMs":可选毫秒,"reason":"可展示给用户的行动理由，不要写内心推理","riskLevel":"low|medium|high","requiresConfirmation":false}}

观察规则：
1. screenSnapshot.clickableNodes/inputNodes/scrollableNodes 是结构化无障碍节点，优先使用。
2. 如果 screenSnapshot.confidence.needsVisualFallback=true 或节点很少，但请求包含 screenshot，你必须像 Computer Use 一样看截图，根据可见 UI 返回 tap_xy、swipe、wait 或 back，不要直接说看不见。
3. 如果截图里能看见目标文字或明显导航入口，返回 tap_xy，并填 x/y。坐标使用屏幕截图坐标系。
4. 如果节点树和截图冲突，以截图可见内容为准，但涉及输入、付款、发送、授权时必须谨慎。

规划规则：
1. 如果目标属于某个 App，而当前不在该 App，优先返回 open_app，不要让用户手动打开。
2. 不要因为当前屏幕没有目标文字就直接 need_user_help。先判断是否可以搜索、切换标签、返回、滑动、滚动、等待或重新打开目标 App。
3. 对可点击控件优先使用 tap_node，并给出 targetNodeId。
4. 无障碍节点不可用但截图可见目标时，使用 tap_xy。
5. 有输入框并需要查找目标时，返回 input_text，text 使用用户目标中的核心关键词。
6. 页面刚打开、节点很少、疑似还在加载时，返回 wait。
7. 如果目标在当前页不可见，但页面可滚动或截图显示列表，先 scroll 或 swipe 扩大搜索范围。
8. 如果当前页面明显走错，优先 back，再重新判断。
9. 目标已完成时，返回 finish。
10. 只有在节点和截图都无法判断、继续操作可能误触时，才返回 need_user_help。
11. 高风险动作必须 riskLevel=high 且 requiresConfirmation=true。
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

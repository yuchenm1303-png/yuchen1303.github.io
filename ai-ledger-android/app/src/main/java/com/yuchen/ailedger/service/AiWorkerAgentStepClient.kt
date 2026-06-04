package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatModel
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

private const val AGENT_STEP_CONNECT_TIMEOUT_MS = 15_000
private const val AGENT_STEP_READ_TIMEOUT_MS = 45_000

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
    return JSONObject().apply {
        put("action", "chat")
        put("intent", "agent_step")
        put("agentMode", true)
        put("agentGoal", cleanGoal)
        put("screenSnapshot", snapshot.toJson())
        put("supportedAgentSteps", JSONArray(CloudAgentStep.supportedTypes.toList()))
        put("modelPreference", if (modelPreference == ChatModel.Auto) ChatModel.Kimi.id else modelPreference.id)
        put("aiModelPreference", if (modelPreference == ChatModel.Auto) ChatModel.Kimi.id else modelPreference.id)
        put("client", "android-compose")
        put("clientVersion", "compose-native-agent-task-v3")
        put("systemPrompt", instruction)
        put("message", "目标：$cleanGoal\n请只根据 screenSnapshot 返回下一步 agentStep JSON。")
        put("messages", JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", instruction) })
            put(JSONObject().apply {
                put("role", "user")
                put("content", "目标：$cleanGoal\n当前屏幕快照：${snapshot.toJson()}\n请每次只返回一步。")
            })
        })
        put("responseFormat", JSONObject().apply {
            put("type", "json_object")
            put("includeAgentStep", true)
        })
        put("now", System.currentTimeMillis())
    }
}

private fun agentPlannerSystemPrompt(): String = """
你是手机智能体任务规划器，不是普通聊天助手。
你只能基于 screenSnapshot 和用户目标决定下一步，不要假装已经点击、输入或滚动。
每次只返回一步，必须返回 JSON，不要输出 Markdown，不要输出解释性正文。

支持动作：open_app, home, back, recents, tap_node, tap_xy, input_text, scroll, swipe, wait, finish, need_user_help。
JSON 格式：{"agentStep":{"type":"open_app|home|back|recents|tap_node|tap_xy|input_text|scroll|swipe|wait|finish|need_user_help","targetNodeId":"可选节点 id","targetText":"可选目标文字","appName":"可选应用名","packageName":"可选包名","text":"可选输入文字","direction":"up|down|left|right 可选","x":可选数字,"y":可选数字,"durationMs":可选毫秒,"reason":"可展示给用户的行动理由，不要写内心推理","riskLevel":"low|medium|high","requiresConfirmation":false}}

规划规则：
1. 如果目标属于某个 App，而当前不在该 App，优先返回 open_app，不要让用户手动打开。
2. 不要因为当前屏幕没有目标文字就直接 need_user_help。先判断是否可以搜索、切换标签、返回、滑动、滚动、等待或重新打开目标 App。
3. 对可点击控件优先使用 tap_node，并给出 targetNodeId。
4. 有输入框并需要查找目标时，返回 input_text，text 使用用户目标中的核心关键词。
5. 页面刚打开、节点很少、疑似还在加载时，返回 wait。
6. 如果目标在当前页不可见，但页面可滚动，先 scroll 或 swipe 扩大搜索范围。
7. 如果当前页面明显走错，优先 back，再重新判断。
8. 目标已完成时，返回 finish。
9. 只有在动作空间全部无法推进、并且继续操作可能误触时，才返回 need_user_help。
10. 高风险动作必须 riskLevel=high 且 requiresConfirmation=true。
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

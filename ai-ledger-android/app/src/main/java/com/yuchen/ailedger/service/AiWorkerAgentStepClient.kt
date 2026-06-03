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
        put("clientVersion", "compose-native-agent-task-v1")
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
你是手机智能体规划器。
你只能基于 screenSnapshot 判断下一步，不要假装已经点击、输入或滚动。
每次只返回一步，必须返回 JSON，不要输出 Markdown，不要输出解释性正文。
JSON 格式：{"agentStep":{"type":"tap_node|input_text|scroll|back|finish|need_user_help","targetNodeId":"可选节点 id","targetText":"可选目标文字","text":"可选输入文字","direction":"up|down|left|right 可选","reason":"为什么这一步合理","riskLevel":"low|medium|high","requiresConfirmation":false}}
当前阶段只生成动作建议，不要求本地自动连续执行。
禁止支付、转账、下单、删除、发送消息、授权、登录、验证码、密码等高风险动作自动执行；遇到这些动作必须 requiresConfirmation=true，riskLevel=high。
如果当前屏幕信息不足，返回 type=need_user_help。
如果目标已经完成，返回 type=finish。
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

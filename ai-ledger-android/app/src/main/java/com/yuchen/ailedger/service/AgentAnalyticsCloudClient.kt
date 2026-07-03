package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.AgentDailyActivity
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

private const val ANALYTICS_SYNC_SCHEMA = "ai_ledger_agent_analytics_sync_v1"
private const val ANALYTICS_CONNECT_TIMEOUT_MS = 8_000
private const val ANALYTICS_READ_TIMEOUT_MS = 12_000

internal class AgentAnalyticsCloudClient(
    private val endpoints: List<String> = listOf(
        AiWorkerClient.DEFAULT_ENDPOINT,
        *AiWorkerClient.DEFAULT_FALLBACK_ENDPOINTS.toTypedArray(),
    ),
) {
    @Throws(IOException::class)
    fun syncDaily(
        session: SupabaseUserSession,
        deviceId: String,
        changedDaily: List<AgentDailyActivity>,
        sinceDateKey: String,
    ): List<AgentDailyActivity> {
        val payload = JSONObject().apply {
            put("action", "agent_analytics_sync")
            put("agentAnalyticsRequest", JSONObject().apply {
                put("schema", ANALYTICS_SYNC_SCHEMA)
                put("action", "sync_daily")
                put("deviceId", deviceId.take(120))
                put("sinceDateKey", sinceDateKey)
                put("daily", JSONArray().apply {
                    changedDaily.forEach { put(it.toJson()) }
                })
            })
        }
        val identityClient = AiWorkerClient(
            AiWorkerConfig(userAccessTokenProvider = { session.accessToken }),
        )
        var lastError: IOException? = null
        endpointLoop@ for (base in endpoints.asSequence().map(String::trim).filter(String::isNotBlank).distinct()) {
            for (candidate in endpointCandidates(base.trimEnd('/'))) {
                try {
                    val response = post(candidate, payload, identityClient)
                    if (!response.optBoolean("ok", false)) {
                        throw IOException(
                            response.optString("error")
                                .ifBlank { response.optString("code") }
                                .ifBlank { "智能体统计云同步失败" },
                        )
                    }
                    return response.optJSONArray("otherDevicesDaily").toDailyList()
                } catch (error: IOException) {
                    lastError = error
                    if (!error.retryable()) throw error
                    if (error is SocketTimeoutException || error.cause is SocketTimeoutException) {
                        continue@endpointLoop
                    }
                }
            }
        }
        throw lastError ?: IOException("智能体统计云端入口不可用")
    }

    private fun post(
        endpoint: String,
        payload: JSONObject,
        identityClient: AiWorkerClient,
    ): JSONObject {
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = ANALYTICS_CONNECT_TIMEOUT_MS
            readTimeout = ANALYTICS_READ_TIMEOUT_MS
            doInput = true
            doOutput = true
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setFixedLengthStreamingMode(bytes.size)
            identityClient.applyRequestIdentityHeaders(this)
        }
        return try {
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            val text = if (status in 200..299) {
                connection.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            } else {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
            if (status !in 200..299) {
                val message = runCatching {
                    JSONObject(text).optString("error")
                        .ifBlank { JSONObject(text).optString("code") }
                }.getOrDefault(text).trim()
                throw IOException(message.ifBlank { "智能体统计云同步失败：HTTP $status" })
            }
            if (text.isBlank()) JSONObject().put("ok", true) else JSONObject(text)
        } catch (error: IOException) {
            throw error
        } catch (error: Throwable) {
            throw IOException("智能体统计云同步响应无效", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun AgentDailyActivity.toJson(): JSONObject = JSONObject().apply {
        put("dateKey", dateKey)
        DAILY_FIELDS.forEach { field -> put(field.first, field.second(this@toJson)) }
    }

    private fun JSONArray?.toDailyList(): List<AgentDailyActivity> {
        val array = this ?: return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                val date = row.optString("dateKey").trim()
                if (!DATE_KEY_REGEX.matches(date)) continue
                fun value(key: String) = row.optLong(key, 0L).coerceAtLeast(0L)
                add(
                    AgentDailyActivity(
                        dateKey = date,
                        firstActivityAtMillis = value("firstActivityAtMillis"),
                        lastActivityAtMillis = value("lastActivityAtMillis"),
                        chatCalls = value("chatCalls"),
                        chatFailures = value("chatFailures"),
                        agentTasks = value("agentTasks"),
                        completedTasks = value("completedTasks"),
                        autonomousCompletedTasks = value("autonomousCompletedTasks"),
                        assistedCompletedTasks = value("assistedCompletedTasks"),
                        failedTasks = value("failedTasks"),
                        pausedTasks = value("pausedTasks"),
                        cancelledTasks = value("cancelledTasks"),
                        budgetExceededTasks = value("budgetExceededTasks"),
                        modelCalls = value("modelCalls"),
                        modelFailures = value("modelFailures"),
                        agentModelTurns = value("agentModelTurns"),
                        inputTokens = value("inputTokens"),
                        outputTokens = value("outputTokens"),
                        reasoningTokens = value("reasoningTokens"),
                        cachedInputTokens = value("cachedInputTokens"),
                        totalTokens = value("totalTokens"),
                        providerTokens = value("providerTokens"),
                        estimatedTokens = value("estimatedTokens"),
                        modelLatencyMs = value("modelLatencyMs"),
                        requestBytes = value("requestBytes"),
                        responseBytes = value("responseBytes"),
                        taskDurationMs = value("taskDurationMs"),
                        executedActions = value("executedActions"),
                        successfulActions = value("successfulActions"),
                        failedActions = value("failedActions"),
                        observations = value("observations"),
                        reobservations = value("reobservations"),
                        rejectedPlans = value("rejectedPlans"),
                        executionFailures = value("executionFailures"),
                        confirmationRequests = value("confirmationRequests"),
                        confirmationsAccepted = value("confirmationsAccepted"),
                        userInputRequests = value("userInputRequests"),
                        userInputsSubmitted = value("userInputsSubmitted"),
                        userTakeovers = value("userTakeovers"),
                        takeoverResumes = value("takeoverResumes"),
                        webSearches = value("webSearches"),
                        imageRequests = value("imageRequests"),
                    ),
                )
            }
        }
    }

    private fun endpointCandidates(endpoint: String): List<String> =
        if (endpoint.endsWith("/chat") || endpoint.endsWith("/api/chat")) {
            listOf(endpoint)
        } else {
            listOf(endpoint, "$endpoint/chat", "$endpoint/api/chat").distinct()
        }

    private fun IOException.retryable(): Boolean {
        val text = message.orEmpty().lowercase()
        return this is SocketTimeoutException || cause is SocketTimeoutException ||
            text.contains("timeout") || text.contains("connection reset") ||
            text.contains("failed to connect") || text.contains("unexpected end") ||
            text.contains("http 404") || text.contains("http 408") ||
            text.contains("http 429") || text.contains("http 5")
    }

    private companion object {
        val DATE_KEY_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}")
        val DAILY_FIELDS: List<Pair<String, (AgentDailyActivity) -> Long>> = listOf(
            "firstActivityAtMillis" to { it.firstActivityAtMillis }, "lastActivityAtMillis" to { it.lastActivityAtMillis },
            "chatCalls" to { it.chatCalls }, "chatFailures" to { it.chatFailures }, "agentTasks" to { it.agentTasks },
            "completedTasks" to { it.completedTasks }, "autonomousCompletedTasks" to { it.autonomousCompletedTasks },
            "assistedCompletedTasks" to { it.assistedCompletedTasks }, "failedTasks" to { it.failedTasks },
            "pausedTasks" to { it.pausedTasks }, "cancelledTasks" to { it.cancelledTasks },
            "budgetExceededTasks" to { it.budgetExceededTasks }, "modelCalls" to { it.modelCalls },
            "modelFailures" to { it.modelFailures }, "agentModelTurns" to { it.agentModelTurns },
            "inputTokens" to { it.inputTokens }, "outputTokens" to { it.outputTokens },
            "reasoningTokens" to { it.reasoningTokens }, "cachedInputTokens" to { it.cachedInputTokens },
            "totalTokens" to { it.totalTokens }, "providerTokens" to { it.providerTokens },
            "estimatedTokens" to { it.estimatedTokens }, "modelLatencyMs" to { it.modelLatencyMs },
            "requestBytes" to { it.requestBytes }, "responseBytes" to { it.responseBytes },
            "taskDurationMs" to { it.taskDurationMs }, "executedActions" to { it.executedActions },
            "successfulActions" to { it.successfulActions }, "failedActions" to { it.failedActions },
            "observations" to { it.observations }, "reobservations" to { it.reobservations },
            "rejectedPlans" to { it.rejectedPlans }, "executionFailures" to { it.executionFailures },
            "confirmationRequests" to { it.confirmationRequests }, "confirmationsAccepted" to { it.confirmationsAccepted },
            "userInputRequests" to { it.userInputRequests }, "userInputsSubmitted" to { it.userInputsSubmitted },
            "userTakeovers" to { it.userTakeovers }, "takeoverResumes" to { it.takeoverResumes },
            "webSearches" to { it.webSearches }, "imageRequests" to { it.imageRequests },
        )
    }
}

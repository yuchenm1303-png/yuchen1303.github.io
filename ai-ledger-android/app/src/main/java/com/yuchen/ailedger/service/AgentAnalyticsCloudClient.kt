package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.AgentDailyActivity
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

private const val ANALYTICS_DAILY_TABLE = "agent_analytics_daily_v1"
private const val ANALYTICS_ROLLUP_FUNCTION = "get_agent_analytics_daily_rollup"
private const val ANALYTICS_CONNECT_TIMEOUT_MS = 8_000
private const val ANALYTICS_READ_TIMEOUT_MS = 12_000

/**
 * 账号统计直接通过 Supabase PostgREST 与 RPC 同步。
 *
 * 认证沿用当前 Supabase 会话，写入受 RLS 约束；云端只保存按设备、日期聚合的数值，
 * 不经过聊天 Worker，也不会与模型请求共用提示词、限流或身份头解析链。
 */
internal class AgentAnalyticsCloudClient(
    private val supabaseUrl: String = SupabaseAuthClient.DEFAULT_SUPABASE_URL,
    private val publishableKey: String = SupabaseAuthClient.DEFAULT_SUPABASE_PUBLISHABLE_KEY,
) {
    @Throws(IOException::class)
    fun syncDaily(
        session: SupabaseUserSession,
        deviceId: String,
        changedDaily: List<AgentDailyActivity>,
        sinceDateKey: String,
    ): List<AgentDailyActivity> {
        val cleanDeviceId = deviceId.trim().take(120)
        if (cleanDeviceId.isBlank()) throw IOException("无法识别当前设备，统计同步已暂停。")

        val rows = changedDaily
            .asSequence()
            .filter { DATE_KEY_REGEX.matches(it.dateKey) }
            .map { activity -> activity.toSupabaseJson(session.userId, cleanDeviceId) }
            .toList()
        if (rows.isNotEmpty()) {
            val body = JSONArray().apply { rows.forEach(::put) }.toString()
            requestText(
                session = session,
                path = "/rest/v1/$ANALYTICS_DAILY_TABLE?on_conflict=user_id,device_id,date_key",
                body = body,
                prefer = "resolution=merge-duplicates,return=minimal",
            )
        }

        val rollupBody = JSONObject()
            .put("p_since_date", sinceDateKey.takeIf(DATE_KEY_REGEX::matches) ?: "2020-01-01")
            .put("p_exclude_device_id", cleanDeviceId)
            .toString()
        val response = requestText(
            session = session,
            path = "/rest/v1/rpc/$ANALYTICS_ROLLUP_FUNCTION",
            body = rollupBody,
        )
        return response.toDailyList()
    }

    private fun requestText(
        session: SupabaseUserSession,
        path: String,
        body: String,
        prefer: String? = null,
    ): String {
        val cleanBase = supabaseUrl.trim().trimEnd('/')
        if (cleanBase.isBlank() || publishableKey.isBlank()) {
            throw IOException("Supabase 尚未配置完整。")
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        val connection = (URL("$cleanBase$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = ANALYTICS_CONNECT_TIMEOUT_MS
            readTimeout = ANALYTICS_READ_TIMEOUT_MS
            doInput = true
            doOutput = true
            useCaches = false
            setFixedLengthStreamingMode(bytes.size)
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (!prefer.isNullOrBlank()) setRequestProperty("Prefer", prefer)
        }
        return try {
            connection.outputStream.use { output -> output.write(bytes) }
            val status = connection.responseCode
            val text = if (status in 200..299) {
                connection.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            } else {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
            if (status !in 200..299) throw IOException(translateAnalyticsError(text, status))
            text
        } catch (error: IOException) {
            throw error
        } catch (error: Throwable) {
            throw IOException("智能体统计云端响应无效。", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun AgentDailyActivity.toSupabaseJson(
        userId: String,
        deviceId: String,
    ): JSONObject = JSONObject().apply {
        put("user_id", userId)
        put("device_id", deviceId)
        put("date_key", dateKey)
        put("first_activity_at_millis", firstActivityAtMillis.coerceAtLeast(0L))
        put("last_activity_at_millis", lastActivityAtMillis.coerceAtLeast(0L))
        put("chat_calls", chatCalls.coerceAtLeast(0L))
        put("chat_failures", chatFailures.coerceAtLeast(0L))
        put("agent_tasks", agentTasks.coerceAtLeast(0L))
        put("completed_tasks", completedTasks.coerceAtLeast(0L))
        put("autonomous_completed_tasks", autonomousCompletedTasks.coerceAtLeast(0L))
        put("assisted_completed_tasks", assistedCompletedTasks.coerceAtLeast(0L))
        put("failed_tasks", failedTasks.coerceAtLeast(0L))
        put("paused_tasks", pausedTasks.coerceAtLeast(0L))
        put("cancelled_tasks", cancelledTasks.coerceAtLeast(0L))
        put("budget_exceeded_tasks", budgetExceededTasks.coerceAtLeast(0L))
        put("model_calls", modelCalls.coerceAtLeast(0L))
        put("model_failures", modelFailures.coerceAtLeast(0L))
        put("agent_model_turns", agentModelTurns.coerceAtLeast(0L))
        put("input_tokens", inputTokens.coerceAtLeast(0L))
        put("output_tokens", outputTokens.coerceAtLeast(0L))
        put("reasoning_tokens", reasoningTokens.coerceAtLeast(0L))
        put("cached_input_tokens", cachedInputTokens.coerceAtLeast(0L))
        put("total_tokens", totalTokens.coerceAtLeast(0L))
        put("provider_tokens", providerTokens.coerceAtLeast(0L))
        put("estimated_tokens", estimatedTokens.coerceAtLeast(0L))
        put("model_latency_ms", modelLatencyMs.coerceAtLeast(0L))
        put("request_bytes", requestBytes.coerceAtLeast(0L))
        put("response_bytes", responseBytes.coerceAtLeast(0L))
        put("task_duration_ms", taskDurationMs.coerceAtLeast(0L))
        put("executed_actions", executedActions.coerceAtLeast(0L))
        put("successful_actions", successfulActions.coerceAtLeast(0L))
        put("failed_actions", failedActions.coerceAtLeast(0L))
        put("observations", observations.coerceAtLeast(0L))
        put("reobservations", reobservations.coerceAtLeast(0L))
        put("rejected_plans", rejectedPlans.coerceAtLeast(0L))
        put("execution_failures", executionFailures.coerceAtLeast(0L))
        put("confirmation_requests", confirmationRequests.coerceAtLeast(0L))
        put("confirmations_accepted", confirmationsAccepted.coerceAtLeast(0L))
        put("user_input_requests", userInputRequests.coerceAtLeast(0L))
        put("user_inputs_submitted", userInputsSubmitted.coerceAtLeast(0L))
        put("user_takeovers", userTakeovers.coerceAtLeast(0L))
        put("takeover_resumes", takeoverResumes.coerceAtLeast(0L))
        put("web_searches", webSearches.coerceAtLeast(0L))
        put("image_requests", imageRequests.coerceAtLeast(0L))
    }

    private fun String.toDailyList(): List<AgentDailyActivity> {
        val rows = JSONArray(ifBlank { "[]" })
        return buildList(rows.length()) {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val date = row.optString("date_key").trim()
                if (!DATE_KEY_REGEX.matches(date)) continue
                fun value(key: String): Long = row.optLong(key, 0L).coerceAtLeast(0L)
                add(
                    AgentDailyActivity(
                        dateKey = date,
                        firstActivityAtMillis = value("first_activity_at_millis"),
                        lastActivityAtMillis = value("last_activity_at_millis"),
                        chatCalls = value("chat_calls"),
                        chatFailures = value("chat_failures"),
                        agentTasks = value("agent_tasks"),
                        completedTasks = value("completed_tasks"),
                        autonomousCompletedTasks = value("autonomous_completed_tasks"),
                        assistedCompletedTasks = value("assisted_completed_tasks"),
                        failedTasks = value("failed_tasks"),
                        pausedTasks = value("paused_tasks"),
                        cancelledTasks = value("cancelled_tasks"),
                        budgetExceededTasks = value("budget_exceeded_tasks"),
                        modelCalls = value("model_calls"),
                        modelFailures = value("model_failures"),
                        agentModelTurns = value("agent_model_turns"),
                        inputTokens = value("input_tokens"),
                        outputTokens = value("output_tokens"),
                        reasoningTokens = value("reasoning_tokens"),
                        cachedInputTokens = value("cached_input_tokens"),
                        totalTokens = value("total_tokens"),
                        providerTokens = value("provider_tokens"),
                        estimatedTokens = value("estimated_tokens"),
                        modelLatencyMs = value("model_latency_ms"),
                        requestBytes = value("request_bytes"),
                        responseBytes = value("response_bytes"),
                        taskDurationMs = value("task_duration_ms"),
                        executedActions = value("executed_actions"),
                        successfulActions = value("successful_actions"),
                        failedActions = value("failed_actions"),
                        observations = value("observations"),
                        reobservations = value("reobservations"),
                        rejectedPlans = value("rejected_plans"),
                        executionFailures = value("execution_failures"),
                        confirmationRequests = value("confirmation_requests"),
                        confirmationsAccepted = value("confirmations_accepted"),
                        userInputRequests = value("user_input_requests"),
                        userInputsSubmitted = value("user_inputs_submitted"),
                        userTakeovers = value("user_takeovers"),
                        takeoverResumes = value("takeover_resumes"),
                        webSearches = value("web_searches"),
                        imageRequests = value("image_requests"),
                    ),
                )
            }
        }
    }

    private fun translateAnalyticsError(raw: String, status: Int): String {
        val json = runCatching { JSONObject(raw) }.getOrNull()
        val code = json?.optString("code").orEmpty()
        val message = json?.let { value ->
            value.optString("message")
                .ifBlank { value.optString("hint") }
                .ifBlank { value.optString("details") }
                .ifBlank { value.optString("error") }
        }.orEmpty().ifBlank { raw.trim() }
        return when {
            code == "42P01" || code == "PGRST205" ||
                message.contains(ANALYTICS_DAILY_TABLE, ignoreCase = true) &&
                message.contains("schema cache", ignoreCase = true) ->
                "云端统计表尚未建立，请执行 supabase-agent-analytics-v1.sql。"

            code == "PGRST202" ||
                message.contains(ANALYTICS_ROLLUP_FUNCTION, ignoreCase = true) &&
                message.contains("schema cache", ignoreCase = true) ->
                "云端统计聚合函数尚未建立，请执行 supabase-agent-analytics-v1.sql。"

            status == 401 -> "登录状态已失效，请重新登录。"
            status == 403 || message.contains("row-level security", ignoreCase = true) ||
                message.contains("policy", ignoreCase = true) ->
                "云端统计权限尚未配置，请检查统计表 RLS。"

            message.isNotBlank() -> message.take(180)
            else -> "智能体统计同步失败：HTTP $status"
        }
    }

    private companion object {
        val DATE_KEY_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}")
    }
}

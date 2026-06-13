package com.yuchen.ailedger.service

import com.yuchen.ailedger.data.LedgerStore
import com.yuchen.ailedger.model.LedgerRecord
import com.yuchen.ailedger.model.LedgerRecordType
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

private const val LEDGER_CONNECT_TIMEOUT_MS = 12_000
private const val LEDGER_READ_TIMEOUT_MS = 20_000

class SupabaseLedgerClient(
    private val supabaseUrl: String = SupabaseAuthClient.DEFAULT_SUPABASE_URL,
    private val publishableKey: String = SupabaseAuthClient.DEFAULT_SUPABASE_PUBLISHABLE_KEY
) {
    @Throws(IOException::class)
    fun fetchRecords(session: SupabaseUserSession): List<LedgerRecord> {
        val userId = encode(session.userId)
        val text = request(
            path = "/rest/v1/records?select=id,title,amount,type,category,date&user_id=eq.$userId&order=date.desc",
            method = "GET",
            session = session
        )
        val array = if (text.isBlank()) JSONArray() else JSONArray(text)
        return buildList {
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                parseRecord(row)?.let(::add)
            }
        }.sortedWith(compareByDescending<LedgerRecord> { LedgerStore.normalizeDate(it.dateLabel) }.thenByDescending { it.id })
    }

    @Throws(IOException::class)
    fun upsertRecords(session: SupabaseUserSession, records: List<LedgerRecord>) {
        if (records.isEmpty()) return
        val rows = JSONArray()
        records.forEach { record ->
            if (record.id.isBlank() || record.amount <= 0f) return@forEach
            rows.put(
                JSONObject()
                    .put("id", record.id)
                    .put("user_id", session.userId)
                    .put("title", record.title.trim().ifBlank { "未命名账单" }.take(30))
                    .put("amount", record.amount.toDouble())
                    .put("type", if (record.type == LedgerRecordType.Income) "income" else "expense")
                    .put("category", record.category.trim().ifBlank { "其他" })
                    .put("date", LedgerStore.normalizeDate(record.dateLabel))
            )
        }
        if (rows.length() == 0) return
        request(
            path = "/rest/v1/records?on_conflict=id",
            method = "POST",
            session = session,
            body = rows.toString(),
            prefer = "resolution=merge-duplicates,return=minimal"
        )
    }

    @Throws(IOException::class)
    fun deleteRecords(session: SupabaseUserSession, ids: Set<String>) {
        ids.filter { it.isNotBlank() }.forEach { id ->
            request(
                path = "/rest/v1/records?id=eq.${encode(id)}&user_id=eq.${encode(session.userId)}",
                method = "DELETE",
                session = session,
                prefer = "return=minimal"
            )
        }
    }

    @Throws(IOException::class)
    fun fetchBudget(session: SupabaseUserSession): String? {
        val text = request(
            path = "/rest/v1/user_settings?select=monthly_budget&user_id=eq.${encode(session.userId)}&limit=1",
            method = "GET",
            session = session
        )
        val array = if (text.isBlank()) JSONArray() else JSONArray(text)
        val value = array.optJSONObject(0)?.optDouble("monthly_budget", Double.NaN) ?: Double.NaN
        if (!value.isFinite() || value < 0.0) return null
        val integer = value.toLong()
        return if (value == integer.toDouble()) integer.toString() else value.toString().trimEnd('0').trimEnd('.')
    }

    @Throws(IOException::class)
    fun upsertBudget(session: SupabaseUserSession, budgetText: String) {
        val value = budgetText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: return
        val rows = JSONArray().put(
            JSONObject()
                .put("user_id", session.userId)
                .put("monthly_budget", value)
        )
        request(
            path = "/rest/v1/user_settings?on_conflict=user_id",
            method = "POST",
            session = session,
            body = rows.toString(),
            prefer = "resolution=merge-duplicates,return=minimal"
        )
    }

    @Throws(IOException::class)
    private fun request(
        path: String,
        method: String,
        session: SupabaseUserSession,
        body: String? = null,
        prefer: String? = null
    ): String {
        val cleanBase = supabaseUrl.trim().trimEnd('/')
        if (cleanBase.isBlank() || publishableKey.isBlank()) throw IOException("Supabase 尚未配置完整。")
        val connection = (URL("$cleanBase$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = LEDGER_CONNECT_TIMEOUT_MS
            readTimeout = LEDGER_READ_TIMEOUT_MS
            doInput = true
            doOutput = body != null
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            prefer?.let { setRequestProperty("Prefer", it) }
        }
        return try {
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val text = if (status in 200..299) {
                connection.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            } else {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
            if (status !in 200..299) throw IOException(translateError(text, status))
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRecord(row: JSONObject): LedgerRecord? {
        val id = row.optString("id").trim()
        val amount = row.optDouble("amount", Double.NaN).toFloat()
        if (id.isBlank() || !amount.isFinite() || amount <= 0f) return null
        return LedgerRecord(
            id = id,
            title = row.optString("title", "未命名账单").trim().ifBlank { "未命名账单" }.take(30),
            amount = amount,
            type = if (row.optString("type").equals("income", ignoreCase = true)) LedgerRecordType.Income else LedgerRecordType.Expense,
            category = row.optString("category", "其他").trim().ifBlank { "其他" },
            dateLabel = LedgerStore.normalizeDate(row.optString("date", LedgerStore.todayIso()))
        )
    }

    private fun translateError(raw: String, status: Int): String {
        val message = runCatching {
            val json = JSONObject(raw)
            json.optString("message")
                .ifBlank { json.optString("hint") }
                .ifBlank { json.optString("details") }
                .ifBlank { json.optString("code") }
        }.getOrDefault(raw).trim()
        return when (status) {
            401 -> "登录状态已过期，请刷新账号会话后重试。"
            403 -> "当前账号没有账单同步权限，请检查 Supabase RLS。"
            404 -> "云端账单表不存在，请检查 records / user_settings 表。"
            else -> message.takeIf { it.isNotBlank() } ?: "账单云同步失败：HTTP $status"
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}

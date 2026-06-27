package com.yuchen.ailedger.service

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

private const val SUPABASE_AUTH_CONNECT_TIMEOUT_MS = 12_000
private const val SUPABASE_AUTH_READ_TIMEOUT_MS = 18_000
private const val SUPABASE_SESSION_EXPIRY_LEEWAY_SECONDS = 30L
private const val SUPABASE_SESSION_REFRESH_EARLY_SECONDS = 300L

data class SupabaseUserSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long
) {
    val hasRequiredCredentials: Boolean
        get() = userId.isNotBlank() && email.isNotBlank() && accessToken.isNotBlank()

    val isUsable: Boolean
        get() = hasRequiredCredentials && !isExpired(SUPABASE_SESSION_EXPIRY_LEEWAY_SECONDS)

    fun isExpired(
        leewaySeconds: Long = 0L,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L
    ): Boolean {
        if (expiresAtEpochSeconds <= 0L) return true
        return expiresAtEpochSeconds <= nowEpochSeconds + leewaySeconds.coerceAtLeast(0L)
    }

    fun shouldRefreshSoon(
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L
    ): Boolean {
        return expiresAtEpochSeconds <= 0L ||
            expiresAtEpochSeconds <= nowEpochSeconds + SUPABASE_SESSION_REFRESH_EARLY_SECONDS
    }
}

data class SupabaseAuthResult(
    val session: SupabaseUserSession?,
    val message: String,
    val requiresEmailConfirmation: Boolean = false
)

class SupabaseAuthClient(
    private val supabaseUrl: String = DEFAULT_SUPABASE_URL,
    private val publishableKey: String = DEFAULT_SUPABASE_PUBLISHABLE_KEY
) {
    @Throws(IOException::class)
    fun signInWithPassword(email: String, password: String): SupabaseAuthResult {
        val response = requestJson(
            path = "/auth/v1/token?grant_type=password",
            method = "POST",
            body = JSONObject()
                .put("email", email.trim())
                .put("password", password)
        )
        val session = parseSession(response) ?: throw IOException("登录成功但没有拿到会话，请稍后重试。")
        return SupabaseAuthResult(session = session, message = "登录成功。")
    }

    @Throws(IOException::class)
    fun signUp(email: String, password: String): SupabaseAuthResult {
        val response = requestJson(
            path = "/auth/v1/signup",
            method = "POST",
            body = JSONObject()
                .put("email", email.trim())
                .put("password", password)
        )
        val session = parseSession(response)
        return if (session != null) {
            SupabaseAuthResult(session = session, message = "注册成功，已直接登录。")
        } else {
            SupabaseAuthResult(
                session = null,
                message = "注册成功，验证邮件已发送；请先去邮箱确认。",
                requiresEmailConfirmation = true
            )
        }
    }

    @Throws(IOException::class)
    fun refreshSession(refreshToken: String): SupabaseAuthResult {
        val response = requestJson(
            path = "/auth/v1/token?grant_type=refresh_token",
            method = "POST",
            body = JSONObject().put("refresh_token", refreshToken)
        )
        val session = parseSession(response) ?: throw IOException("会话刷新失败，请重新登录。")
        return SupabaseAuthResult(session = session, message = "登录状态已刷新。")
    }

    @Throws(IOException::class)
    fun signOut(accessToken: String) {
        requestJson(
            path = "/auth/v1/logout",
            method = "POST",
            body = JSONObject(),
            bearerToken = accessToken
        )
    }

    @Throws(IOException::class)
    private fun requestJson(
        path: String,
        method: String,
        body: JSONObject,
        bearerToken: String? = null
    ): JSONObject {
        val cleanBase = supabaseUrl.trim().trimEnd('/')
        if (cleanBase.isBlank() || publishableKey.isBlank()) throw IOException("Supabase 尚未配置完整。")

        val connection = (URL("$cleanBase$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = SUPABASE_AUTH_CONNECT_TIMEOUT_MS
            readTimeout = SUPABASE_AUTH_READ_TIMEOUT_MS
            doInput = true
            doOutput = method != "GET"
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("content-type", "application/json")
            setRequestProperty("accept", "application/json")
            if (!bearerToken.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearerToken")
        }

        return try {
            if (method != "GET") {
                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val text = if (status in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
            if (status !in 200..299) throw IOException(translateAuthError(text, status))
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSession(json: JSONObject): SupabaseUserSession? {
        val accessToken = json.optString("access_token").takeIf { it.isNotBlank() } ?: return null
        val refreshToken = json.optString("refresh_token")
        val user = json.optJSONObject("user") ?: return null
        val userId = user.optString("id")
        val email = user.optString("email")
        if (userId.isBlank() || email.isBlank()) return null
        val nowSeconds = System.currentTimeMillis() / 1000L
        val expiresAt = json.optLong("expires_at", 0L).takeIf { it > 0L }
            ?: (nowSeconds + json.optLong("expires_in", 3600L))
        return SupabaseUserSession(
            userId = userId,
            email = email,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochSeconds = expiresAt
        )
    }

    private fun translateAuthError(raw: String, status: Int): String {
        val message = runCatching {
            val json = JSONObject(raw)
            json.optString("msg")
                .ifBlank { json.optString("message") }
                .ifBlank { json.optString("error_description") }
                .ifBlank { json.optString("error") }
        }.getOrDefault(raw).trim()
        return when {
            message.contains("Invalid login credentials", ignoreCase = true) -> "邮箱或密码不对。"
            message.contains("Email not confirmed", ignoreCase = true) -> "邮箱还没验证，请先去邮箱点确认链接。"
            message.contains("User already registered", ignoreCase = true) -> "这个邮箱已经注册过了，直接登录就行。"
            message.contains("Password", ignoreCase = true) && message.contains("at least", ignoreCase = true) -> "密码太短，请至少使用 6 位。"
            message.isNotBlank() -> message
            else -> "账号服务请求失败：HTTP $status"
        }
    }

    companion object {
        const val DEFAULT_SUPABASE_URL = "https://nfzkphjbelyltrzgkdwt.supabase.co"
        const val DEFAULT_SUPABASE_PUBLISHABLE_KEY = "sb_publishable_tE8SeTOj-ERgmqvP4l5Hiw_arCxCJLa"
    }
}

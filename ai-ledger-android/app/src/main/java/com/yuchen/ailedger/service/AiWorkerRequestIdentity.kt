package com.yuchen.ailedger.service

import com.yuchen.ailedger.AiLedgerApplication
import com.yuchen.ailedger.BuildConfig
import com.yuchen.ailedger.data.SupabaseAuthRepository
import java.net.HttpURLConnection

private const val MAX_APP_CLIENT_TOKEN_CHARS = 4_096
private const val MAX_USER_ACCESS_TOKEN_CHARS = 8_192

/**
 * Worker 请求身份的唯一组装入口。
 *
 * X-AI-Ledger-Token 只证明请求来自受支持的 App；
 * Authorization Bearer 只承载当前 Supabase 登录用户的访问令牌。
 */
internal object AiWorkerRequestIdentity {
    fun defaultAppClientToken(): String? = normalize(
        BuildConfig.AI_LEDGER_CLIENT_TOKEN,
        MAX_APP_CLIENT_TOKEN_CHARS,
    )

    fun headers(
        appClientToken: String?,
        userAccessTokenProvider: (() -> String?)? = null,
        stream: Boolean = false,
    ): Map<String, String> {
        val appToken = normalize(appClientToken, MAX_APP_CLIENT_TOKEN_CHARS)
        val userToken = resolveUserAccessToken(userAccessTokenProvider)
            ?.takeUnless { token -> appToken != null && token == appToken }

        return buildMap {
            if (stream) put("X-AI-Ledger-Stream", "sse")
            appToken?.let { put("X-AI-Ledger-Token", it) }
            userToken?.let { put("Authorization", "Bearer $it") }
        }
    }

    fun applyTo(
        connection: HttpURLConnection,
        appClientToken: String?,
        userAccessTokenProvider: (() -> String?)? = null,
        stream: Boolean = false,
    ) {
        headers(
            appClientToken = appClientToken,
            userAccessTokenProvider = userAccessTokenProvider,
            stream = stream,
        ).forEach(connection::setRequestProperty)
    }

    private fun resolveUserAccessToken(provider: (() -> String?)?): String? {
        val raw = if (provider != null) {
            runCatching(provider).getOrNull()
        } else {
            val context = AiLedgerApplication.contextOrNull() ?: return null
            runCatching {
                SupabaseAuthRepository.get(context)
                    .state
                    .value
                    .session
                    ?.takeIf { it.isUsable }
                    ?.accessToken
            }.getOrNull()
        }
        return normalize(raw, MAX_USER_ACCESS_TOKEN_CHARS)
    }

    private fun normalize(value: String?, maxChars: Int): String? {
        val clean = value?.trim().orEmpty()
        return clean.takeIf { it.isNotBlank() && it.length <= maxChars }
    }
}

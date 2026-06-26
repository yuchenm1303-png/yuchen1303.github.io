package com.yuchen.ailedger.data

import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 股票模块共享网络传输层。
 *
 * 所有高频行情请求复用同一个连接池，避免每秒刷新时重复建立 TCP/TLS 连接；
 * OkHttp 会自动处理 gzip 与 HTTP/2，并允许每个请求独立设置总超时。
 */
internal object StockHttpClient {
    private val dispatcher = Dispatcher().apply {
        maxRequests = 24
        maxRequestsPerHost = 12
    }

    private val client = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .connectTimeout(1_500, TimeUnit.MILLISECONDS)
        .readTimeout(4_000, TimeUnit.MILLISECONDS)
        .writeTimeout(2_000, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun get(url: String, timeoutMs: Int, emptyMessage: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "AI-Ledger-Android/1.0")
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .build()
        val call = client.newCall(request)
        call.timeout().timeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        call.execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} ${body.take(160)}".trim())
            }
            if (body.isBlank()) throw IllegalStateException(emptyMessage)
            return body
        }
    }
}

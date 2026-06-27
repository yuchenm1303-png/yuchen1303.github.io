package com.yuchen.ailedger.data

import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.LinkedHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 股票模块共享网络传输层。
 *
 * 高频行情统一复用 HTTP/2、TCP/TLS 连接和 gzip 解压；相同 URL 的并发请求通过
 * singleflight 合并，极短时间内的重复读取直接命中微缓存，避免页面切换和轮询边界
 * 同时发出重复网络请求。
 *
 * Render 免费实例可能在闲置后冷启动。这里不再使用固定 4 秒 readTimeout 截断所有
 * 股票请求，而是只由每个接口传入的 call timeout 决定预算。实时轮询仍保持短预算，
 * 首页、K 线和详情首屏则可以等待冷启动完成。
 */
internal object StockHttpClient {
    private data class CachedBody(
        val body: String,
        val storedAtMs: Long
    )

    private val dispatcher = Dispatcher().apply {
        maxRequests = 24
        maxRequestsPerHost = 12
    }

    private val client = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val inFlight = ConcurrentHashMap<String, CompletableFuture<String>>()
    private val recentLock = Any()
    private val recentBodies = object : LinkedHashMap<String, CachedBody>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedBody>?): Boolean {
            return size > MAX_RECENT_RESPONSES
        }
    }

    fun get(
        url: String,
        timeoutMs: Int,
        emptyMessage: String,
        microCacheMs: Long = DEFAULT_MICRO_CACHE_MS
    ): String {
        recent(url, microCacheMs)?.let { return it }

        val owned = CompletableFuture<String>()
        val existing = inFlight.putIfAbsent(url, owned)
        if (existing != null) {
            return awaitShared(existing, timeoutMs, url)
        }

        try {
            recent(url, microCacheMs)?.let {
                owned.complete(it)
                return it
            }
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "AI-Ledger-Android/1.0")
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .build()
            val call = client.newCall(request)
            call.timeout().timeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            val body = call.execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code} ${text.take(160)}".trim())
                }
                if (text.isBlank()) throw IllegalStateException(emptyMessage)
                text
            }
            synchronized(recentLock) {
                recentBodies[url] = CachedBody(body, System.currentTimeMillis())
            }
            owned.complete(body)
            return body
        } catch (error: Throwable) {
            val normalized = normalizeTimeout(error, timeoutMs)
            owned.completeExceptionally(normalized)
            throw normalized
        } finally {
            inFlight.remove(url, owned)
        }
    }

    private fun normalizeTimeout(error: Throwable, timeoutMs: Int): Throwable {
        return if (error is SocketTimeoutException || error is InterruptedIOException) {
            IllegalStateException(
                "行情服务响应超时（${timeoutMs / 1000.0}秒），可能正在冷启动，请稍后重试",
                error
            )
        } else {
            error
        }
    }

    private fun recent(url: String, maxAgeMs: Long): String? {
        if (maxAgeMs <= 0L) return null
        val now = System.currentTimeMillis()
        synchronized(recentLock) {
            val cached = recentBodies[url] ?: return null
            if (now - cached.storedAtMs <= maxAgeMs) return cached.body
            recentBodies.remove(url)
        }
        return null
    }

    private fun awaitShared(
        future: CompletableFuture<String>,
        timeoutMs: Int,
        url: String
    ): String {
        return try {
            future.get(timeoutMs.toLong() + SHARED_WAIT_GRACE_MS, TimeUnit.MILLISECONDS)
        } catch (error: ExecutionException) {
            throw (error.cause ?: error)
        } catch (error: TimeoutException) {
            throw IllegalStateException("共享行情请求等待超时：${url.take(96)}", error)
        }
    }

    private const val DEFAULT_MICRO_CACHE_MS = 220L
    private const val SHARED_WAIT_GRACE_MS = 250L
    private const val MAX_RECENT_RESPONSES = 64
}

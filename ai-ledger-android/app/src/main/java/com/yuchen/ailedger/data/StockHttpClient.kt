package com.yuchen.ailedger.data

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
        .connectTimeout(1_500, TimeUnit.MILLISECONDS)
        .readTimeout(4_000, TimeUnit.MILLISECONDS)
        .writeTimeout(2_000, TimeUnit.MILLISECONDS)
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
            owned.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(url, owned)
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

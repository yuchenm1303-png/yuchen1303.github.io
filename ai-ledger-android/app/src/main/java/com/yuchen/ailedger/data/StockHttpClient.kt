package com.yuchen.ailedger.data

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 股票模块共享网络传输层。
 *
 * 所有股票接口复用连接池、singleflight 和极短响应微缓存。传输层超时、限流或服务端错误后，
 * 会对同一接口族进行短暂冷却，避免主路由失败后立即用 `/crawl/` 别名重复请求同一个
 * Render 实例。HTTP 404/405 不进入冷却，旧服务的正式兼容 fallback 仍可正常工作。
 */
internal object StockHttpClient {
    private data class CachedBody(
        val body: String,
        val storedAtMs: Long
    )

    private data class TransportFailure(
        val expiresAtMs: Long,
        val error: Throwable
    )

    private val dispatcher = Dispatcher().apply {
        maxRequests = 16
        maxRequestsPerHost = 8
    }

    private val client = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectionPool(ConnectionPool(6, 5, TimeUnit.MINUTES))
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val inFlight = ConcurrentHashMap<String, CompletableFuture<String>>()
    private val transportFailures = ConcurrentHashMap<String, TransportFailure>()
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
        val effectiveTimeoutMs = effectiveTimeoutMs(url, timeoutMs)
        recent(url, microCacheMs)?.let { return it }
        recentTransportFailure(url)?.let { throw it }

        val owned = CompletableFuture<String>()
        val existing = inFlight.putIfAbsent(url, owned)
        if (existing != null) {
            return awaitShared(existing, effectiveTimeoutMs, url)
        }

        val startedAtNs = System.nanoTime()
        try {
            recent(url, microCacheMs)?.let {
                owned.complete(it)
                return it
            }
            recentTransportFailure(url)?.let { throw it }

            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "AI-Ledger-Android/1.0")
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .build()
            val call = client.newCall(request)
            call.timeout().timeout(effectiveTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            val body = call.execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val backendState = response.header("X-Market-Home-State")
                        ?.takeIf(String::isNotBlank)
                        ?.let { " state=$it" }
                        .orEmpty()
                    throw IllegalStateException(
                        "HTTP ${response.code}$backendState ${text.take(160)}".trim()
                    )
                }
                if (text.isBlank()) throw IllegalStateException(emptyMessage)
                text
            }
            synchronized(recentLock) {
                recentBodies[url] = CachedBody(body, System.currentTimeMillis())
            }
            transportFailures.remove(requestFamily(url))
            owned.complete(body)
            return body
        } catch (error: Throwable) {
            val elapsedMs = elapsedMs(startedAtNs)
            val normalized = normalizeTransportError(error, effectiveTimeoutMs, elapsedMs)
            if (isTransportFailure(error) || isRetryableServiceFailure(error)) {
                rememberTransportFailure(url, normalized)
            }
            owned.completeExceptionally(normalized)
            throw normalized
        } finally {
            inFlight.remove(url, owned)
        }
    }

    private fun effectiveTimeoutMs(url: String, requestedTimeoutMs: Int): Int {
        val routeCapMs = when {
            "/api/stock/a-share/realtime" in url -> 2_300
            "/api/stock/a-share/quotes" in url -> 3_200
            "/api/stock/a-share/market/home" in url -> 4_500
            "/api/stock/a-share/kline" in url -> 6_500
            "/api/stock/a-share/stock/full" in url -> 8_000
            "/api/stock/a-share/detail" in url && "mode=full" in url -> 8_000
            "/api/stock/a-share/detail" in url -> 4_000
            "/api/stock/crawl/a-share" in url -> 6_500
            else -> 6_500
        }
        return requestedTimeoutMs.coerceAtMost(routeCapMs).coerceAtLeast(MIN_REQUEST_TIMEOUT_MS)
    }

    private fun normalizeTransportError(
        error: Throwable,
        timeoutMs: Int,
        elapsedMs: Long
    ): Throwable {
        val elapsedSeconds = String.format(Locale.US, "%.1f", elapsedMs / 1_000.0)
        val budgetSeconds = String.format(Locale.US, "%.1f", timeoutMs / 1_000.0)
        val message = when (error) {
            is UnknownHostException -> "无法解析行情服务地址，请检查网络或 DNS"
            is ConnectException -> "无法连接行情服务，请稍后重试"
            is SSLException -> "行情服务安全连接失败，请稍后重试"
            is SocketTimeoutException -> {
                "行情服务读取超时（实际${elapsedSeconds}秒，预算${budgetSeconds}秒）"
            }
            is InterruptedIOException -> {
                "行情请求整体超时（实际${elapsedSeconds}秒，预算${budgetSeconds}秒）"
            }
            else -> return error
        }
        return IllegalStateException(message, error)
    }

    private fun isTransportFailure(error: Throwable): Boolean {
        return error is UnknownHostException ||
            error is ConnectException ||
            error is SSLException ||
            error is SocketTimeoutException ||
            error is InterruptedIOException
    }

    private fun isRetryableServiceFailure(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        if (!message.startsWith("HTTP ")) return false
        val status = message.removePrefix("HTTP ").takeWhile(Char::isDigit).toIntOrNull() ?: return false
        return status == 408 || status == 425 || status == 429 || status in 500..599
    }

    private fun rememberTransportFailure(url: String, error: Throwable) {
        val now = System.currentTimeMillis()
        if (transportFailures.size > MAX_TRANSPORT_FAILURES) {
            transportFailures.entries.forEach { entry ->
                if (entry.value.expiresAtMs <= now) {
                    transportFailures.remove(entry.key, entry.value)
                }
            }
            if (transportFailures.size > MAX_TRANSPORT_FAILURES) transportFailures.clear()
        }
        transportFailures[requestFamily(url)] = TransportFailure(
            expiresAtMs = now + TRANSPORT_FAILURE_COOLDOWN_MS,
            error = error
        )
    }

    private fun recentTransportFailure(url: String): Throwable? {
        val key = requestFamily(url)
        val failure = transportFailures[key] ?: return null
        if (failure.expiresAtMs > System.currentTimeMillis()) return failure.error
        transportFailures.remove(key, failure)
        return null
    }

    private fun requestFamily(url: String): String {
        return url.replace("/api/stock/crawl/", "/api/stock/")
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

    private fun elapsedMs(startedAtNs: Long): Long {
        return ((System.nanoTime() - startedAtNs) / 1_000_000L).coerceAtLeast(0L)
    }

    private const val DEFAULT_MICRO_CACHE_MS = 220L
    private const val MIN_REQUEST_TIMEOUT_MS = 700
    private const val SHARED_WAIT_GRACE_MS = 250L
    private const val TRANSPORT_FAILURE_COOLDOWN_MS = 2_500L
    private const val MAX_RECENT_RESPONSES = 64
    private const val MAX_TRANSPORT_FAILURES = 96
}

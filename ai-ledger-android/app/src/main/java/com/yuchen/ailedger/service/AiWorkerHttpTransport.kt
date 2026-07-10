package com.yuchen.ailedger.service

import com.yuchen.ailedger.AiLedgerApplication
import com.yuchen.ailedger.data.AssistantAccountSessionRuntime
import com.yuchen.ailedger.data.AssistantMemoryRepository
import com.yuchen.ailedger.data.AssistantMemoryRequestContextRuntime
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.LinkedHashMap
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

private const val MEMORY_SETTINGS_REFRESH_MAX_SCOPES = 16

internal class AiWorkerHttpTransport(
    private val config: AiWorkerConfig,
    private val resolvedClientId: String,
) {
    private val cancellationGeneration = AtomicLong(0L)
    private val activeConnections = ConcurrentHashMap.newKeySet<HttpURLConnection>()

    fun cancelActiveRequests() {
        cancellationGeneration.incrementAndGet()
        activeConnections.toList().forEach { connection ->
            runCatching { connection.disconnect() }
        }
    }

    fun postChat(
        endpoint: String,
        payload: JSONObject,
        route: AiWorkerModelRoute,
    ): AiChatResponse {
        AssistantMemoryUsageBridge.beginTransportAttempt()
        AssistantMemorySettingsRefreshCoordinator.decoratePayload(payload)
        val requestGeneration = cancellationGeneration.get()
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = config.connectTimeoutMs
            readTimeout = config.readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json, text/plain")
            applyClientHeaders(this, stream = false)
        }
        registerConnection(connection, requestGeneration)
        return try {
            val requestBytes = payload.toString().toByteArray(Charsets.UTF_8)
            AssistantMemoryUsageBridge.captureRequestBytes(requestBytes.size)
            connection.outputStream.use { output ->
                output.write(requestBytes)
            }
            val status = connection.responseCode
            val body = readBody(connection, status)
            val data = body.toJsonOrNull()
            if (status !in 200..299) {
                throw IOException(
                    data?.optString("error").notBlankOrNull()
                        ?: data?.optString("message").notBlankOrNull()
                        ?: body.take(120).ifBlank {
                            "云端 AI 调用失败：HTTP $status"
                        },
                )
            }
            data?.ensureClientToolFallbackReply()
            AiWorkerResponseParser.throwIfServerReturnedFallbackSignal(data)
            val response = AiWorkerResponseParser.parse(
                data = data,
                body = body,
                payload = payload,
                route = route,
            )
            InlineStickerDiagnosticsStore.recordHttpExchange(
                route = route,
                payload = payload,
                stream = false,
                responseData = data,
                streamedReply = "",
                finalReply = response.reply,
                mergedReply = response.reply,
                responseReply = response.reply,
            )
            response.also { parsedResponse ->
                InlineStickerDiagnosticsStore.recordLatest(
                    AiLedgerApplication.contextOrNull(),
                    parsedResponse.stickerDiagnosticsJson,
                )
                AssistantMemorySettingsRefreshCoordinator.acknowledgeSuccessfulPayload(payload)
            }
        } catch (error: SocketTimeoutException) {
            throwIfCancelled(requestGeneration, error)
            throw IOException(
                "云端 AI 请求超时：${endpoint.substringAfter("://")}",
                error,
            )
        } catch (error: IOException) {
            throwIfCancelled(requestGeneration, error)
            throw error
        } finally {
            releaseConnection(connection)
        }
    }

    fun postStreamChat(
        endpoint: String,
        payload: JSONObject,
        route: AiWorkerModelRoute,
        onDelta: (String) -> Unit,
    ): AiChatResponse {
        AssistantMemoryUsageBridge.beginTransportAttempt()
        AssistantMemorySettingsRefreshCoordinator.decoratePayload(payload)
        val requestGeneration = cancellationGeneration.get()
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = config.connectTimeoutMs
            readTimeout = config.readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty(
                "Accept",
                "text/event-stream, application/x-ndjson, application/json, text/plain",
            )
            applyClientHeaders(this, stream = true)
        }
        registerConnection(connection, requestGeneration)
        val deltaCoalescer = StreamingDeltaCoalescer(onDelta = onDelta)

        return try {
            val requestBytes = payload.toString().toByteArray(Charsets.UTF_8)
            AssistantMemoryUsageBridge.captureRequestBytes(requestBytes.size)
            connection.outputStream.use { output ->
                output.write(requestBytes)
            }
            val status = connection.responseCode
            val contentType = connection.contentType.orEmpty().lowercase()

            if (status !in 200..299) {
                val body = readBody(connection, status)
                val data = body.toJsonOrNull()
                throw IOException(
                    data?.optString("error").notBlankOrNull()
                        ?: data?.optString("message").notBlankOrNull()
                        ?: body.take(120).ifBlank {
                            "云端 AI 调用失败：HTTP $status"
                        },
                )
            }

            if (
                !contentType.contains("text/event-stream") &&
                !contentType.contains("application/x-ndjson")
            ) {
                val body = readBody(connection, status)
                val data = body.toJsonOrNull()
                data?.ensureClientToolFallbackReply()
                AiWorkerResponseParser.throwIfServerReturnedFallbackSignal(data)
                val response = AiWorkerResponseParser.parse(
                    data = data,
                    body = body,
                    payload = payload,
                    route = route,
                )
                InlineStickerDiagnosticsStore.recordHttpExchange(
                    route = route,
                    payload = payload,
                    stream = false,
                    responseData = data,
                    streamedReply = "",
                    finalReply = response.reply,
                    mergedReply = response.reply,
                    responseReply = response.reply,
                )
                return response.also { parsedResponse ->
                    InlineStickerDiagnosticsStore.recordLatest(
                        AiLedgerApplication.contextOrNull(),
                        parsedResponse.stickerDiagnosticsJson,
                    )
                    AssistantMemorySettingsRefreshCoordinator.acknowledgeSuccessfulPayload(payload)
                }
            }

            val streamedReply = StringBuilder()
            var finalData: JSONObject? = null
            var progressDisplayed = false
            var assistantContentStarted = false
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.forEachStreamPayload { payloadText ->
                    val event = parseStreamPayload(payloadText)
                        ?: return@forEachStreamPayload
                    if (event.progressLabel.isNotBlank() && !assistantContentStarted) {
                        val text = if (progressDisplayed) {
                            "\n${event.progressLabel}"
                        } else {
                            "AI 正在工作…\n${event.progressLabel}"
                        }
                        progressDisplayed = true
                        deltaCoalescer.append(text)
                    }
                    if (event.delta.isNotBlank()) {
                        assistantContentStarted = true
                        streamedReply.append(event.delta)
                        deltaCoalescer.append(event.delta)
                    }
                    if (event.done) finalData = event.data ?: finalData
                }
            }

            val streamedText = streamedReply.toString().trim()
            val finalJson = finalData?.also { it.ensureClientToolFallbackReply() }
            val finalReply = finalJson
                ?.let { data -> AiWorkerResponseParser.extractReply(data, data.toString()) }
                .orEmpty()
            val mergedReply = AiWorkerResponseParser.mergeStreamedReplyWithFinalReply(
                streamedReply = streamedText,
                finalReply = finalReply,
            )
            val clientMergeDiagnostics = AiWorkerResponseParser.buildClientStickerMergeDiagnostics(
                streamedReply = streamedText,
                finalReply = finalReply,
                mergedReply = mergedReply,
            )
            if (finalJson != null) {
                AiWorkerResponseParser.attachClientStickerDiagnostics(finalJson, clientMergeDiagnostics)
            }
            val response = when {
                finalJson != null -> AiWorkerResponseParser.parse(
                    data = finalJson,
                    body = finalJson.toString(),
                    payload = payload,
                    route = route,
                    replyOverride = mergedReply.takeIf { it.isNotBlank() },
                )
                streamedText.isNotBlank() -> AiChatResponse(
                    reply = streamedText,
                    source = "cloud_ai",
                    model = route.resolved.id,
                    modelLabel = if (route.isAuto) {
                        "自动选择 · ${route.resolved.label}"
                    } else {
                        route.resolved.label
                    },
                    stickerDiagnosticsJson = clientMergeDiagnostics.toString(2),
                )
                else -> throw IOException("云端流式回复结束，但没有返回有效内容")
            }
            InlineStickerDiagnosticsStore.recordHttpExchange(
                route = route,
                payload = payload,
                stream = true,
                responseData = finalJson,
                streamedReply = streamedText,
                finalReply = finalReply,
                mergedReply = mergedReply,
                responseReply = response.reply,
            )
            response.also { parsedResponse ->
                InlineStickerDiagnosticsStore.recordLatest(
                    AiLedgerApplication.contextOrNull(),
                    parsedResponse.stickerDiagnosticsJson,
                )
                AssistantMemorySettingsRefreshCoordinator.acknowledgeSuccessfulPayload(payload)
            }
        } catch (error: SocketTimeoutException) {
            throwIfCancelled(requestGeneration, error)
            throw IOException(
                "云端 AI 流式请求超时：${endpoint.substringAfter("://")}",
                error,
            )
        } catch (error: IOException) {
            throwIfCancelled(requestGeneration, error)
            throw error
        } finally {
            // Preserve every received character even if the network closes or times out between
            // two coalescing thresholds. The existing ViewModel smoother remains the UI authority.
            deltaCoalescer.drain()
            releaseConnection(connection)
        }
    }

    fun requestHeaders(stream: Boolean): Map<String, String> {
        return buildMap {
            put("X-Client", AI_WORKER_CHAT_CLIENT_NAME)
            put("X-Client-Id", resolvedClientId)
            put("X-Device-Id", resolvedClientId)
            putAll(
                AiWorkerRequestIdentity.headers(
                    appClientToken = config.clientAuthToken,
                    userAccessTokenProvider = config.userAccessTokenProvider,
                    stream = stream,
                ),
            )
        }
    }

    fun applyRequestIdentityHeaders(
        connection: HttpURLConnection,
        stream: Boolean,
    ) {
        AiWorkerRequestIdentity.applyTo(
            connection = connection,
            appClientToken = config.clientAuthToken,
            userAccessTokenProvider = config.userAccessTokenProvider,
            stream = stream,
        )
    }

    private fun registerConnection(connection: HttpURLConnection, requestGeneration: Long) {
        activeConnections += connection
        if (requestGeneration != cancellationGeneration.get()) {
            activeConnections.remove(connection)
            runCatching { connection.disconnect() }
            throw CancellationException("AI request cancelled")
        }
    }

    private fun releaseConnection(connection: HttpURLConnection) {
        activeConnections.remove(connection)
        runCatching { connection.disconnect() }
    }

    private fun throwIfCancelled(requestGeneration: Long, cause: Throwable): Nothing? {
        if (requestGeneration == cancellationGeneration.get()) return null
        throw CancellationException("AI request cancelled").also { it.initCause(cause) }
    }

    private fun applyClientHeaders(
        connection: HttpURLConnection,
        stream: Boolean,
    ) {
        requestHeaders(stream).forEach(connection::setRequestProperty)
    }

    private data class StreamPayload(
        val delta: String = "",
        val progressLabel: String = "",
        val done: Boolean = false,
        val data: JSONObject? = null,
    )

    private fun BufferedReader.forEachStreamPayload(block: (String) -> Unit) {
        val sseData = StringBuilder()
        while (true) {
            val line = readLine() ?: break
            AssistantMemoryUsageBridge.addResponseBytes(
                line.toByteArray(Charsets.UTF_8).size + 1,
            )
            when {
                line.isBlank() -> {
                    val payload = sseData.toString().trim()
                    if (payload.isNotBlank()) block(payload)
                    sseData.clear()
                }
                line.startsWith("data:") -> {
                    val data = line.removePrefix("data:").trimStart()
                    if (data == "[DONE]") {
                        block(data)
                    } else {
                        if (sseData.isNotEmpty()) sseData.append('\n')
                        sseData.append(data)
                    }
                }
                line.startsWith("{") -> block(line)
            }
        }
        val tail = sseData.toString().trim()
        if (tail.isNotBlank()) block(tail)
    }

    private fun parseStreamPayload(payload: String): StreamPayload? {
        val clean = payload.trim()
        if (clean.isBlank()) return null
        if (clean == "[DONE]") return StreamPayload(done = true)

        val data = clean.toJsonOrNull() ?: return StreamPayload(delta = clean)
        val type = data.optString("type").lowercase()
        if (type == "agent_progress") {
            val label = data.optString("label")
                .ifBlank { data.optString("status") }
                .trim()
            return if (label.isNotBlank()) StreamPayload(progressLabel = label) else null
        }
        val choices = data.optJSONArray("choices")
        val firstChoice = choices?.optJSONObject(0)
        val choiceDelta = firstChoice
            ?.optJSONObject("delta")
            ?.optString("content")
            .orEmpty()
        val choiceText = firstChoice?.optString("text").orEmpty()
        val finishReason = firstChoice?.optString("finish_reason").orEmpty()

        val hasFinalReply =
            data.has("reply") ||
                data.has("response") ||
                data.has("answer") ||
                data.optJSONObject("data") != null ||
                data.optJSONObject("result") != null ||
                data.optJSONObject("clientToolCall") != null ||
                data.optJSONObject("deviceIntent")?.optJSONObject("clientToolCall") != null
        val done =
            type in setOf("done", "final", "complete", "completed") ||
                data.optBoolean("done", false) ||
                data.optBoolean("completed", false) ||
                finishReason.isNotBlank() ||
                (
                    type.isBlank() &&
                        hasFinalReply &&
                        data.optString("delta").isBlank() &&
                        choiceDelta.isBlank()
                    )

        if (done) {
            val responseData = data.optJSONObject("response")
                ?: data.optJSONObject("final")
                ?: data
            return StreamPayload(done = true, data = responseData)
        }

        val delta = data.optString("delta")
            .ifBlank { data.optString("text") }
            .ifBlank { data.optString("content") }
            .ifBlank { data.optString("replyDelta") }
            .ifBlank { choiceDelta }
            .ifBlank { choiceText }
        return if (delta.isNotBlank()) StreamPayload(delta = delta) else null
    }

    private fun JSONObject.ensureClientToolFallbackReply() {
        if (AiWorkerResponseParser.extractReply(this, toString()).isNotBlank()) return
        val call = optJSONObject("clientToolCall")
            ?: optJSONObject("data")?.optJSONObject("clientToolCall")
            ?: optJSONObject("result")?.optJSONObject("clientToolCall")
            ?: optJSONObject("deviceIntent")?.optJSONObject("clientToolCall")
            ?: return
        val toolName = call.optString("name").ifBlank { "client_tool" }
        put("reply", "正在调用本地工具：$toolName…")
    }

    private fun readBody(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        val body = stream
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { reader -> reader.readText() }
            .orEmpty()
        AssistantMemoryUsageBridge.addResponseBytes(body.toByteArray(Charsets.UTF_8).size)
        return body
    }

    private fun String.toJsonOrNull(): JSONObject? = try {
        takeIf { it.isNotBlank() }?.let { raw ->
            JSONObject(raw).also(AssistantMemoryUsageBridge::captureResponseJson)
        }
    } catch (_: Exception) {
        null
    }

    private fun String?.notBlankOrNull(): String? = this?.takeIf { it.isNotBlank() }
}

private object AssistantMemorySettingsRefreshCoordinator {
    private data class RefreshEntry(
        val settingsFingerprint: String,
        val pendingGeneration: Long?,
    )

    private val lock = Any()
    private val entries = LinkedHashMap<String, RefreshEntry>(16, 0.75f, true)
    private var generationCounter = 0L

    fun decoratePayload(payload: JSONObject) {
        payload.remove("memorySettingsRefresh")
        payload.remove("memorySettingsRefreshGeneration")

        val requestContext = AssistantMemoryRequestContextRuntime.peekCurrentThread() ?: return
        val ticket = requestContext.ticket ?: return
        if (!AssistantAccountSessionRuntime.isCurrent(ticket)) return
        val appContext = AiLedgerApplication.contextOrNull() ?: return
        val memoryState = AssistantMemoryRepository.get(appContext).state.value
        if (!memoryState.cloudReady || memoryState.accountUserId != ticket.userId) return

        val scope = AssistantAccountSessionRuntime.diagnosticsScope(ticket)
        val fingerprint = buildString {
            append(memoryState.memoryEnabled)
            append('|')
            append(memoryState.autoMemoryEnabled)
            append('|')
            append(memoryState.historyReferenceEnabled)
            append('|')
            append(memoryState.sensitivePolicy.trim().lowercase())
        }
        val pendingGeneration = synchronized(lock) {
            val existing = entries[scope]
            val next = when {
                existing == null -> RefreshEntry(
                    settingsFingerprint = fingerprint,
                    pendingGeneration = nextGenerationLocked(),
                )
                existing.settingsFingerprint != fingerprint -> RefreshEntry(
                    settingsFingerprint = fingerprint,
                    pendingGeneration = nextGenerationLocked(),
                )
                else -> existing
            }
            entries[scope] = next
            trimLocked()
            next.pendingGeneration
        } ?: return

        payload.put("memorySettingsRefresh", true)
        payload.put("memorySettingsRefreshGeneration", pendingGeneration)
    }

    fun acknowledgeSuccessfulPayload(payload: JSONObject) {
        if (!payload.optBoolean("memorySettingsRefresh", false)) return
        val generation = payload.optLong("memorySettingsRefreshGeneration", 0L)
        if (generation <= 0L) return
        val requestContext = AssistantMemoryRequestContextRuntime.peekCurrentThread() ?: return
        val ticket = requestContext.ticket ?: return
        if (!AssistantAccountSessionRuntime.isCurrent(ticket)) return
        val scope = AssistantAccountSessionRuntime.diagnosticsScope(ticket)

        synchronized(lock) {
            val existing = entries[scope] ?: return@synchronized
            if (existing.pendingGeneration != generation) return@synchronized
            entries[scope] = existing.copy(pendingGeneration = null)
        }
    }

    private fun nextGenerationLocked(): Long {
        generationCounter = if (generationCounter == Long.MAX_VALUE) 1L else generationCounter + 1L
        return generationCounter
    }

    private fun trimLocked() {
        while (entries.size > MEMORY_SETTINGS_REFRESH_MAX_SCOPES) {
            val iterator = entries.entries.iterator()
            if (!iterator.hasNext()) return
            iterator.next()
            iterator.remove()
        }
    }
}

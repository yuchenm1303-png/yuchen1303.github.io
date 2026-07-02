package com.yuchen.ailedger.service

import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONObject

internal class AiWorkerHttpTransport(
    private val config: AiWorkerConfig,
    private val resolvedClientId: String,
) {
    fun postChat(
        endpoint: String,
        payload: JSONObject,
        route: AiWorkerModelRoute,
    ): AiChatResponse {
        AssistantMemoryUsageBridge.beginTransportAttempt()
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = config.connectTimeoutMs
            readTimeout = config.readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json, text/plain")
            applyClientHeaders(this, stream = false)
        }
        return try {
            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
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
            AiWorkerResponseParser.throwIfServerReturnedFallbackSignal(data)
            AiWorkerResponseParser.parse(
                data = data,
                body = body,
                payload = payload,
                route = route,
            )
        } catch (error: SocketTimeoutException) {
            throw IOException(
                "云端 AI 请求超时：${endpoint.substringAfter("://")}",
                error,
            )
        } finally {
            connection.disconnect()
        }
    }

    fun postStreamChat(
        endpoint: String,
        payload: JSONObject,
        route: AiWorkerModelRoute,
        onDelta: (String) -> Unit,
    ): AiChatResponse {
        AssistantMemoryUsageBridge.beginTransportAttempt()
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
        val deltaCoalescer = StreamingDeltaCoalescer(onDelta = onDelta)

        return try {
            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
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
                AiWorkerResponseParser.throwIfServerReturnedFallbackSignal(data)
                return AiWorkerResponseParser.parse(
                    data = data,
                    body = body,
                    payload = payload,
                    route = route,
                )
            }

            val streamedReply = StringBuilder()
            var finalData: JSONObject? = null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.forEachStreamPayload { payloadText ->
                    val event = parseStreamPayload(payloadText)
                        ?: return@forEachStreamPayload
                    if (event.delta.isNotBlank()) {
                        streamedReply.append(event.delta)
                        deltaCoalescer.append(event.delta)
                    }
                    if (event.done) finalData = event.data ?: finalData
                }
            }

            val streamedText = streamedReply.toString().trim()
            val finalJson = finalData
            val finalReply = finalJson
                ?.let { data -> AiWorkerResponseParser.extractReply(data, data.toString()) }
                .orEmpty()
            val mergedReply = AiWorkerResponseParser.mergeStreamedReplyWithFinalReply(
                streamedReply = streamedText,
                finalReply = finalReply,
            )
            when {
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
                )
                else -> throw IOException("云端流式回复结束，但没有返回有效内容")
            }
        } catch (error: SocketTimeoutException) {
            throw IOException(
                "云端 AI 流式请求超时：${endpoint.substringAfter("://")}",
                error,
            )
        } finally {
            // Preserve every received character even if the network closes or times out between
            // two coalescing thresholds. The existing ViewModel smoother remains the UI authority.
            deltaCoalescer.drain()
            connection.disconnect()
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

    private fun applyClientHeaders(
        connection: HttpURLConnection,
        stream: Boolean,
    ) {
        requestHeaders(stream).forEach(connection::setRequestProperty)
    }

    private data class StreamPayload(
        val delta: String = "",
        val done: Boolean = false,
        val data: JSONObject? = null,
    )

    private fun BufferedReader.forEachStreamPayload(block: (String) -> Unit) {
        val sseData = StringBuilder()
        while (true) {
            val line = readLine() ?: break
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
                data.optJSONObject("result") != null
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

    private fun readBody(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        return stream
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { reader -> reader.readText() }
            .orEmpty()
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

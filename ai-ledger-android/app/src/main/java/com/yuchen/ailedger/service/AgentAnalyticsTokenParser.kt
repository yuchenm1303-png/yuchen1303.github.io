package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.AgentTokenAccuracy
import com.yuchen.ailedger.model.AgentTokenUsage
import kotlin.math.ceil
import org.json.JSONArray
import org.json.JSONObject

/**
 * 兼容 OpenAI、DashScope、Gemini 代理层和自定义 Worker 的 usage 结构。
 * 真实 usage 缺失时仅生成明确标记为 Estimated 的活动估算，绝不伪装成计费数据。
 */
internal object AgentAnalyticsTokenParser {
    fun resolveUsage(payload: JSONObject?, response: JSONObject?): AgentTokenUsage {
        return parseProviderUsage(response) ?: estimateUsage(payload, response)
    }

    fun parseProviderUsage(response: JSONObject?): AgentTokenUsage? {
        response ?: return null
        val usage = usageCandidates(response)
            .map { candidate -> candidate to usageScore(candidate) }
            .filter { (_, score) -> score > 0 }
            .maxByOrNull { (_, score) -> score }
            ?.first
            ?: return null

        val input = usage.firstLong(
            "prompt_tokens", "input_tokens", "promptTokens", "inputTokens",
            "promptTokenCount", "inputTokenCount",
        )
        val output = usage.firstLong(
            "completion_tokens", "output_tokens", "completionTokens", "outputTokens",
            "candidatesTokenCount", "outputTokenCount",
        )
        val reasoning = usage.firstLong(
            "reasoning_tokens", "reasoningTokens", "thoughtsTokenCount", "thinking_tokens",
        )
        val cached = usage.firstLong(
            "cached_tokens", "cachedInputTokens", "cache_read_input_tokens",
            "cacheReadInputTokens", "prompt_cache_hit_tokens",
        )
        val explicitTotal = usage.firstLong(
            "total_tokens", "totalTokens", "totalTokenCount", "tokens",
        )
        val components = input + output + reasoning
        val total = maxOf(explicitTotal, components)
        if (total <= 0L && cached <= 0L) return null
        return AgentTokenUsage(
            inputTokens = input,
            outputTokens = output,
            reasoningTokens = reasoning,
            cachedInputTokens = cached,
            totalTokens = total,
            accuracy = AgentTokenAccuracy.Provider,
        )
    }

    fun estimateUsage(payload: JSONObject?, response: JSONObject?): AgentTokenUsage {
        val input = estimateJsonTokens(payload, keyHint = "request").coerceAtLeast(1L)
        val responseText = extractResponseText(response)
        val output = estimateTextTokens(responseText).coerceAtLeast(
            if (response == null || response.length() == 0) 0L else 1L,
        )
        return AgentTokenUsage(
            inputTokens = input,
            outputTokens = output,
            totalTokens = input + output,
            accuracy = AgentTokenAccuracy.Estimated,
        )
    }

    fun modelId(payload: JSONObject?, response: JSONObject?): String {
        return envelopeStrings(response, "model", "modelId", "model_id", "providerModel")
            .firstOrNull()
            ?: envelopeStrings(payload, "model", "modelId", "model_id", "requestedModel")
                .firstOrNull()
            ?: "unknown"
    }

    fun modelLabel(payload: JSONObject?, response: JSONObject?): String {
        return envelopeStrings(response, "modelLabel", "modelName", "model_name")
            .firstOrNull()
            ?: modelId(payload, response)
    }

    fun webSearchUsed(response: JSONObject?): Boolean {
        return envelopeObjects(response).any { envelope ->
            envelope.optBoolean("searchUsed", false) ||
                envelope.optBoolean("webSearchUsed", false) ||
                envelope.optJSONArray("sources")?.length()?.let { it > 0 } == true ||
                envelope.optJSONArray("webSources")?.length()?.let { it > 0 } == true
        }
    }

    fun imageRequest(payload: JSONObject?): Boolean {
        payload ?: return false
        if (payload.optBoolean("hasImage", false)) return true
        return containsImagePayload(payload, depth = 0)
    }

    fun toolKeys(response: JSONObject?): List<Pair<String, String>> {
        val result = linkedMapOf<String, String>()
        envelopeObjects(response).forEach { envelope ->
            envelope.optJSONObject("agentAction")?.let { action ->
                val key = action.optString("capability").trim().ifBlank { "agent_action" }
                result[key] = action.optString("title").trim().ifBlank { capabilityLabel(key) }
            }
            envelope.optJSONObject("mobileAction")?.let { action ->
                val key = action.optString("type").trim().ifBlank { "mobile_action" }
                result[key] = action.optString("title").trim().ifBlank { capabilityLabel(key) }
            }
            envelope.optJSONObject("preferenceUpdate")?.let { update ->
                val key = update.optString("type").trim().ifBlank { "preference_update" }
                result[key] = capabilityLabel(key)
            }
            envelope.optJSONObject("structuredData")?.let { structured ->
                val type = structured.optString("type").trim().ifBlank { "structured_data" }
                result["structured:$type"] = structured.optString("title").trim().ifBlank { "实时数据" }
            }
        }
        return result.entries.map { it.key to it.value }
    }

    fun requestBytes(payload: JSONObject?): Long = payload
        ?.toString()
        ?.toByteArray(Charsets.UTF_8)
        ?.size
        ?.toLong()
        ?: 0L

    fun responseBytes(response: JSONObject?): Long = response
        ?.toString()
        ?.toByteArray(Charsets.UTF_8)
        ?.size
        ?.toLong()
        ?: 0L

    private fun usageCandidates(root: JSONObject): List<JSONObject> {
        val candidates = mutableListOf<JSONObject>()
        envelopeObjects(root).forEach { envelope ->
            candidates += envelope
            listOf("usage", "tokenUsage", "token_usage", "usageMetadata", "tokenUsageMetadata")
                .mapNotNull(envelope::optJSONObject)
                .forEach(candidates::add)
        }
        return candidates.distinctBy { it.toString() }
    }

    private fun usageScore(candidate: JSONObject): Int {
        val keys = TOKEN_KEYS.count(candidate::has)
        val nestedUsage = listOf("usage", "tokenUsage", "usageMetadata").count(candidate::has)
        return keys * 4 + nestedUsage
    }

    private fun envelopeObjects(root: JSONObject?): List<JSONObject> {
        root ?: return emptyList()
        val output = mutableListOf<JSONObject>()
        val queue = ArrayDeque<Pair<JSONObject, Int>>()
        queue.add(root to 0)
        while (queue.isNotEmpty()) {
            val (current, depth) = queue.removeFirst()
            output += current
            if (depth >= 3) continue
            ENVELOPE_KEYS.mapNotNull(current::optJSONObject).forEach { queue.add(it to depth + 1) }
        }
        return output.distinctBy { it.toString() }
    }

    private fun envelopeStrings(root: JSONObject?, vararg keys: String): Sequence<String> {
        return envelopeObjects(root).asSequence().flatMap { envelope ->
            keys.asSequence().mapNotNull { key ->
                envelope.opt(key)?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
            }
        }
    }

    private fun JSONObject.firstLong(vararg keys: String): Long {
        for (key in keys) {
            val value = opt(key)
            val number = when (value) {
                is Number -> value.toLong()
                is String -> value.trim().toDoubleOrNull()?.toLong()
                else -> null
            }
            if (number != null && number >= 0L) return number
        }
        return 0L
    }

    private fun extractResponseText(response: JSONObject?): String {
        return envelopeObjects(response).asSequence()
            .flatMap { envelope ->
                RESPONSE_TEXT_KEYS.asSequence().mapNotNull { key ->
                    val raw = envelope.opt(key)
                    when (raw) {
                        is String -> raw.takeIf(String::isNotBlank)
                        else -> null
                    }
                }
            }
            .firstOrNull()
            .orEmpty()
    }

    private fun estimateJsonTokens(value: Any?, keyHint: String, depth: Int = 0): Long {
        if (value == null || value == JSONObject.NULL || depth > 10) return 0L
        val normalizedKey = keyHint.lowercase()
        if (SENSITIVE_OR_NOISE_KEYS.any(normalizedKey::contains)) return 0L
        if (IMAGE_DATA_KEYS.any(normalizedKey::contains)) {
            return when (value) {
                is String -> if (value.isBlank()) 0L else ESTIMATED_IMAGE_TOKENS
                is JSONArray -> value.length().toLong() * ESTIMATED_IMAGE_TOKENS
                else -> ESTIMATED_IMAGE_TOKENS
            }
        }
        return when (value) {
            is JSONObject -> {
                var total = 0L
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    total = safeAdd(total, estimateJsonTokens(value.opt(key), key, depth + 1))
                }
                total
            }
            is JSONArray -> {
                var total = 0L
                for (index in 0 until value.length()) {
                    total = safeAdd(total, estimateJsonTokens(value.opt(index), keyHint, depth + 1))
                }
                total
            }
            is String -> estimateTextTokens(value)
            is Number, is Boolean -> 1L
            else -> estimateTextTokens(value.toString())
        }
    }

    private fun estimateTextTokens(text: String): Long {
        if (text.isBlank()) return 0L
        var cjk = 0
        var latinLike = 0
        var punctuation = 0
        text.forEach { char ->
            when {
                char.isWhitespace() -> Unit
                char.isCjkCharacter() -> cjk += 1
                char.isLetterOrDigit() -> latinLike += 1
                else -> punctuation += 1
            }
        }
        return cjk.toLong() +
            ceil(latinLike / 4.0).toLong() +
            ceil(punctuation / 3.0).toLong()
    }

    private fun Char.isCjkCharacter(): Boolean {
        val code = code
        return code in 0x3400..0x4DBF ||
            code in 0x4E00..0x9FFF ||
            code in 0xF900..0xFAFF ||
            code in 0x3040..0x30FF ||
            code in 0xAC00..0xD7AF
    }

    private fun containsImagePayload(value: Any?, depth: Int): Boolean {
        if (value == null || value == JSONObject.NULL || depth > 8) return false
        return when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val normalized = key.lowercase()
                    if (IMAGE_DATA_KEYS.any(normalized::contains) && value.optString(key).isNotBlank()) return true
                    if (containsImagePayload(value.opt(key), depth + 1)) return true
                }
                false
            }
            is JSONArray -> (0 until value.length()).any { containsImagePayload(value.opt(it), depth + 1) }
            else -> false
        }
    }

    private fun capabilityLabel(key: String): String = when (key.lowercase()) {
        "run_agent_task" -> "智能体任务"
        "run_device_control" -> "设备控制"
        "set_alarm" -> "设置闹钟"
        "navigate" -> "导航"
        "navigation_address" -> "地址记忆"
        else -> key.replace('_', ' ').trim().ifBlank { "工具调用" }
    }

    private fun safeAdd(left: Long, right: Long): Long {
        if (right <= 0L) return left
        return if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
    }

    private val ENVELOPE_KEYS = listOf("data", "result", "response", "final", "metadata", "meta", "output")
    private val RESPONSE_TEXT_KEYS = listOf("reply", "answer", "text", "content", "rawModelOutput")
    private val TOKEN_KEYS = listOf(
        "prompt_tokens", "input_tokens", "promptTokens", "inputTokens", "promptTokenCount",
        "completion_tokens", "output_tokens", "completionTokens", "outputTokens", "candidatesTokenCount",
        "reasoning_tokens", "reasoningTokens", "thoughtsTokenCount", "thinking_tokens",
        "cached_tokens", "cachedInputTokens", "cache_read_input_tokens", "prompt_cache_hit_tokens",
        "total_tokens", "totalTokens", "totalTokenCount", "tokens",
    )
    private val SENSITIVE_OR_NOISE_KEYS = listOf(
        "authorization", "accesstoken", "refreshtoken", "clienttoken", "password", "secret", "cookie",
    )
    private val IMAGE_DATA_KEYS = listOf("base64", "imagedata", "image_data", "screenshotdata", "screenshot_data")
    private const val ESTIMATED_IMAGE_TOKENS = 1_024L
}

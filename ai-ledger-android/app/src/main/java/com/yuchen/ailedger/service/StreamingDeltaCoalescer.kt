package com.yuchen.ailedger.service

/**
 * Coalesces tiny SSE fragments before they cross into Compose-visible state.
 *
 * The first fragment is emitted immediately to preserve time-to-first-text. Later fragments are
 * grouped by size, punctuation or a short time budget. [drain] guarantees byte-for-byte ordering
 * and releases any remaining suffix when the stream ends.
 */
internal class StreamingDeltaCoalescer(
    private val onDelta: (String) -> Unit,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val targetChunkChars: Int = 48,
    private val maxDelayMs: Long = 120L,
) {
    private val pending = StringBuilder()
    private var emittedAny = false
    private var lastEmitAt = clockMs()

    fun append(delta: String) {
        if (delta.isEmpty()) return
        pending.append(delta)
        val now = clockMs()
        val punctuationBoundary = pending.length >= MIN_PUNCTUATION_CHARS &&
            pending.lastOrNull()?.let(::isBreakChar) == true
        if (
            !emittedAny ||
            pending.length >= targetChunkChars ||
            punctuationBoundary ||
            now - lastEmitAt >= maxDelayMs
        ) {
            emit(now)
        }
    }

    fun drain() {
        if (pending.isNotEmpty()) emit(clockMs())
    }

    private fun emit(now: Long) {
        if (pending.isEmpty()) return
        val chunk = pending.toString()
        pending.clear()
        emittedAny = true
        lastEmitAt = now
        onDelta(chunk)
    }

    private fun isBreakChar(char: Char): Boolean {
        return char == '。' || char == '！' || char == '？' || char == '；' ||
            char == '，' || char == ',' || char == '.' || char == '!' || char == '?' ||
            char == ';' || char == ':' || char == '：' || char == '\n' ||
            char == '）' || char == ')' || char == '】' || char == ']'
    }

    private companion object {
        const val MIN_PUNCTUATION_CHARS = 5
    }
}

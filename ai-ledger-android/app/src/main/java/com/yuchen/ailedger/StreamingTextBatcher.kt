package com.yuchen.ailedger

/**
 * Thread-safe boundary between high-frequency network deltas and Compose-visible message state.
 *
 * Network callbacks only append to the private buffer. The ViewModel polls a bounded text snapshot
 * on its existing cadence, so individual SSE fragments never mutate AssistantUiState directly.
 * The batching thresholds intentionally preserve the current visual streaming rhythm.
 */
internal class StreamingTextBatcher(
    private val clockMs: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val buffer = StringBuilder()
    private var emittedLength = 0
    private var lastFlushAt = clockMs()
    private var dirty = false

    fun append(delta: String) {
        if (delta.isEmpty()) return
        synchronized(lock) {
            buffer.append(delta)
            dirty = true
        }
    }

    fun poll(force: Boolean = false): String? {
        return synchronized(lock) {
            if (buffer.isEmpty()) return@synchronized null
            if (!force && !dirty) return@synchronized null

            val now = clockMs()
            val nextEnd = nextFlushEnd(
                text = buffer,
                displayed = emittedLength,
                force = force,
                now = now,
            )
            if (nextEnd <= emittedLength) return@synchronized null

            emittedLength = nextEnd
            lastFlushAt = now
            dirty = emittedLength < buffer.length
            buffer.substring(0, nextEnd)
        }
    }

    fun hasPendingText(): Boolean = synchronized(lock) {
        emittedLength < buffer.length
    }

    private fun nextFlushEnd(
        text: CharSequence,
        displayed: Int,
        force: Boolean,
        now: Long,
    ): Int {
        if (force) return text.length
        val available = text.length - displayed
        if (available <= 0) return displayed

        val elapsed = now - lastFlushAt
        val total = text.length
        val firstFlush = displayed == 0
        if (firstFlush) {
            if (available < 4 && elapsed < 180L) return displayed
            val firstMax = when {
                total >= 96 -> 12
                total >= 48 -> 10
                else -> 8
            }
            val softEnd = minOf(text.length, displayed + firstMax)
            for (index in displayed + 5 until softEnd) {
                if (isStreamingBreakChar(text[index])) return index + 1
            }
            return softEnd
        }

        val minChunk = when {
            total >= 1600 -> 28
            total >= 900 -> 24
            total >= 420 -> 18
            else -> 10
        }
        val idealChunk = when {
            total >= 1600 -> 56
            total >= 900 -> 46
            total >= 420 -> 36
            else -> 24
        }
        val relaxedMin = maxOf(8, minChunk / 2)
        if (available < minChunk && elapsed < 140L) return displayed
        if (available < relaxedMin && elapsed < 260L) return displayed

        val minEnd = minOf(text.length, displayed + minOf(available, minChunk))
        val maxEnd = minOf(text.length, displayed + minOf(available, idealChunk))
        for (index in minEnd - 1 until maxEnd) {
            if (index in 0 until text.length && isStreamingBreakChar(text[index])) return index + 1
        }
        if (available >= idealChunk || elapsed >= 190L) return maxEnd
        return displayed
    }

    private fun isStreamingBreakChar(char: Char): Boolean {
        return char == '。' || char == '！' || char == '？' || char == '；' ||
            char == '，' || char == ',' || char == '.' || char == '!' || char == '?' ||
            char == ';' || char == ':' || char == '：' || char == '\n' ||
            char == '）' || char == ')' || char == '】' || char == ']'
    }
}

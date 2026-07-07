package com.yuchen.ailedger.service

/**
 * Coalesces tiny SSE fragments before they cross into Compose-visible state.
 *
 * The first visible fragment is released with a very small budget to preserve perceived latency.
 * Later fragments are grouped by size, punctuation or a short time budget. [drain] guarantees
 * byte-for-byte ordering and releases any remaining suffix when the stream ends.
 */
internal class StreamingDeltaCoalescer(
    private val onDelta: (String) -> Unit,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val targetChunkChars: Int = 96,
    private val maxDelayMs: Long = 190L,
) {
    private val pending = StringBuilder(targetChunkChars.coerceAtLeast(MIN_BUFFER_CAPACITY))
    private val emittedProgressLines = LinkedHashSet<String>()
    private var emittedAny = false
    private var lastEmitAt = clockMs()

    fun append(delta: String) {
        if (delta.isEmpty()) return
        val polishedDelta = polishAgentProgressDelta(delta)
        if (polishedDelta.isEmpty()) return
        pending.append(polishedDelta)
        val pendingLength = pending.length
        val now = clockMs()
        val punctuationBoundary = pendingLength >= MIN_PUNCTUATION_CHARS &&
            isBreakChar(pending[pendingLength - 1])
        val firstFragmentReady = !emittedAny && (
            pendingLength >= FIRST_CHUNK_CHARS ||
                punctuationBoundary ||
                now - lastEmitAt >= FIRST_MAX_DELAY_MS
            )
        val steadyFragmentReady = emittedAny && (
            pendingLength >= targetChunkChars ||
                punctuationBoundary ||
                now - lastEmitAt >= maxDelayMs
            )
        if (firstFragmentReady || steadyFragmentReady) {
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

    private fun polishAgentProgressDelta(delta: String): String {
        if (!delta.contains("AI 正在工作") && !delta.contains("正在调用") && !delta.contains("工作空间") && !delta.contains("手机端执行")) {
            return delta
        }
        val polished = delta
            .split('\n')
            .mapNotNull { line ->
                val prefix = line.takeWhile { it == '\n' }
                val clean = line.trim()
                if (clean.isBlank()) return@mapNotNull line
                if (clean == "AI 正在工作…" || clean == "AI 正在工作..." || clean == "AI 正在工作") {
                    return@mapNotNull "AI 正在工作…"
                }
                val mapped = mapProgressLine(clean)
                val key = mapped.lowercase()
                if (key.isBlank()) return@mapNotNull null
                if (!emittedProgressLines.add(key)) return@mapNotNull null
                prefix + mapped
            }
            .joinToString("\n")
        return polished
    }

    private fun mapProgressLine(line: String): String {
        val tool = extractToolName(line)
        if (tool.isNotBlank()) {
            return "${toolTitle(tool)}    $tool · 内部工具"
        }
        return when {
            line.contains("进入工作空间") -> "已进入工作空间"
            line.contains("分析下一步") -> "正在分析下一步"
            line.contains("等待手机端执行") -> "等待手机端执行结果"
            line.contains("继续检查") -> "继续检查任务结果"
            line.contains("整理最终回复") -> "正在整理最终回复"
            line.contains("收到") && line.contains("结果") -> "已收到工具结果"
            line.contains("完成") -> "任务已完成"
            else -> line
        }
    }

    private fun extractToolName(line: String): String {
        return Regex("[a-z][a-z0-9]*(?:_[a-z0-9]+)+").find(line)?.value.orEmpty()
    }

    private fun toolTitle(tool: String): String = when {
        tool.startsWith("plan_list") -> "读取计划"
        tool.startsWith("plan_create") -> "创建计划"
        tool.startsWith("plan_update") -> "调整计划"
        tool.startsWith("plan_toggle") -> "切换计划"
        tool.startsWith("computer_observe") -> "观察屏幕"
        tool.startsWith("computer_run") -> "视觉执行"
        tool.startsWith("device_control") -> "设备控制"
        tool.startsWith("ledger_") -> "账本工具"
        tool.startsWith("memory_") -> "记忆工具"
        tool.contains("search") -> "联网搜索"
        else -> "内部工具"
    }

    private fun isBreakChar(char: Char): Boolean {
        return char == '。' || char == '！' || char == '？' || char == '；' ||
            char == '，' || char == ',' || char == '.' || char == '!' || char == '?' ||
            char == ';' || char == ':' || char == '：' || char == '\n' ||
            char == '）' || char == ')' || char == '】' || char == ']'
    }

    private companion object {
        const val MIN_BUFFER_CAPACITY = 16
        const val FIRST_CHUNK_CHARS = 8
        const val FIRST_MAX_DELAY_MS = 80L
        const val MIN_PUNCTUATION_CHARS = 22
    }
}

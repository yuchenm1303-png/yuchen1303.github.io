package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingDeltaCoalescerTest {
    @Test
    fun firstFragmentIsImmediateAndRemainingTextIsPreserved() {
        val emitted = mutableListOf<String>()
        val coalescer = StreamingDeltaCoalescer(
            onDelta = emitted::add,
            clockMs = { 0L },
            targetChunkChars = 32,
            maxDelayMs = 1_000L,
        )

        coalescer.append("你")
        coalescer.append("好")
        coalescer.append("，世界")
        coalescer.drain()

        assertEquals("你好，世界", emitted.joinToString(separator = ""))
        assertEquals("你", emitted.first())
    }

    @Test
    fun tinyFollowUpFragmentsAreGrouped() {
        val emitted = mutableListOf<String>()
        val coalescer = StreamingDeltaCoalescer(
            onDelta = emitted::add,
            clockMs = { 0L },
            targetChunkChars = 6,
            maxDelayMs = 1_000L,
        )

        coalescer.append("a")
        coalescer.append("b")
        coalescer.append("c")
        coalescer.append("d")
        coalescer.append("e")
        coalescer.drain()

        assertEquals(listOf("a", "bcde"), emitted)
    }

    @Test
    fun punctuationAndDelayCanFlushBeforeTargetSize() {
        var now = 0L
        val emitted = mutableListOf<String>()
        val coalescer = StreamingDeltaCoalescer(
            onDelta = emitted::add,
            clockMs = { now },
            targetChunkChars = 64,
            maxDelayMs = 48L,
        )

        coalescer.append("首")
        coalescer.append("这是句号。")
        now = 60L
        coalescer.append("延迟")

        assertEquals(listOf("首", "这是句号。", "延迟"), emitted)
    }
}

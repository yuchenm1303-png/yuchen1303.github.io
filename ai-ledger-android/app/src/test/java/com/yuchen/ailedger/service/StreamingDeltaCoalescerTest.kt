package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingDeltaCoalescerTest {
    @Test
    fun firstTinyFragmentWaitsForSmallLatencyBudgetAndTextIsPreserved() {
        var now = 0L
        val emitted = mutableListOf<String>()
        val coalescer = StreamingDeltaCoalescer(
            onDelta = emitted::add,
            clockMs = { now },
            targetChunkChars = 32,
            maxDelayMs = 1_000L,
        )

        coalescer.append("你")
        assertTrue(emitted.isEmpty())

        now = 80L
        coalescer.append("好")
        coalescer.append("，世界")
        coalescer.drain()

        assertEquals("你好，世界", emitted.joinToString(separator = ""))
        assertEquals(listOf("你好", "，世界"), emitted)
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

        coalescer.append("abcdefgh")
        coalescer.append("i")
        coalescer.append("j")
        coalescer.append("k")
        coalescer.append("l")
        coalescer.append("m")
        coalescer.append("n")
        coalescer.append("op")
        coalescer.drain()

        assertEquals(listOf("abcdefgh", "ijklmn", "op"), emitted)
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

        coalescer.append("首段已经足够长啦")
        coalescer.append("这一段内容已经超过标点最小长度并且到了句号。")
        now = 60L
        coalescer.append("延迟")

        assertEquals(listOf("首段已经足够长啦", "这一段内容已经超过标点最小长度并且到了句号。", "延迟"), emitted)
    }
}

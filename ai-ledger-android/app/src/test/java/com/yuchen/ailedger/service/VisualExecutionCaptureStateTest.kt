package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisualExecutionCaptureStateTest {
    @Test
    fun matchingFreshCaptureIsConsumedOnlyOnce() {
        var now = 100L
        val state = VisualExecutionCaptureState<String>({ now }, ttlMs = 800L)
        state.store("com.example.app", "root-a", "capture")

        now = 420L
        assertEquals("capture", state.take("com.example.app", "root-a"))
        assertNull(state.take("com.example.app", "root-a"))
    }

    @Test
    fun expiredCaptureFallsBack() {
        var now = 0L
        val state = VisualExecutionCaptureState<String>({ now }, ttlMs = 800L)
        state.store("com.example.app", "root-a", "capture")

        now = 801L
        assertNull(state.take("com.example.app", "root-a"))
    }

    @Test
    fun packageOrRootMismatchConsumesAndRejectsCapture() {
        val state = VisualExecutionCaptureState<String>({ 100L }, ttlMs = 800L)
        state.store("com.example.app", "root-a", "capture")

        assertNull(state.take("com.example.other", "root-a"))
        assertNull(state.take("com.example.app", "root-a"))

        state.store("com.example.app", "root-a", "capture-2")
        assertNull(state.take("com.example.app", "root-b"))
    }
}

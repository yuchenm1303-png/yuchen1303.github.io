package com.yuchen.ailedger.service

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pure state holder for nested clean-visual leases. The first lease starts the settle clock; nested
 * leases reuse the same hidden window instead of restarting fixed waits or posting extra watchdogs.
 */
internal class CleanVisualCaptureState(
    private val elapsedRealtime: () -> Long,
) {
    private var depth: Int = 0
    private var hiddenSinceMs: Long = 0L

    val active: Boolean
        get() = depth > 0

    fun acquire(): Boolean {
        val becameActive = depth == 0
        if (becameActive) hiddenSinceMs = elapsedRealtime()
        depth += 1
        return becameActive
    }

    fun release(): Boolean {
        if (depth <= 0) return false
        depth -= 1
        val becameIdle = depth == 0
        if (becameIdle) hiddenSinceMs = 0L
        return becameIdle
    }

    fun reset(): Boolean {
        val wasActive = depth > 0
        depth = 0
        hiddenSinceMs = 0L
        return wasActive
    }

    fun settleRemaining(requiredMs: Long): Long {
        if (!active || requiredMs <= 0L) return requiredMs.coerceAtLeast(0L)
        val elapsed = (elapsedRealtime() - hiddenSinceMs).coerceAtLeast(0L)
        return (requiredMs - elapsed).coerceAtLeast(0L)
    }
}

internal class CleanVisualCaptureLease(
    private val release: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) release()
    }
}

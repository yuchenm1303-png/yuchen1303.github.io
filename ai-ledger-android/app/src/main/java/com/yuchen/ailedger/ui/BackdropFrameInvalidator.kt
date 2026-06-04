package com.yuchen.ailedger.ui

import android.os.SystemClock

private const val SCROLL_BACKDROP_MIN_INTERVAL_MS = 12L

/**
 * Lightweight scroll/backdrop invalidation gate.
 *
 * Normal scroll traffic is cheap-throttled like the stable baseline: multiple
 * nested-scroll callbacks inside the interval are ignored immediately, without
 * posting extra frame callbacks. Forced requests still pass through so fling and
 * geometry boundary updates are not left stale.
 */
internal class BackdropFrameInvalidator(
    private val ticker: BackdropFrameTicker,
    private val scrollMinIntervalMs: Long = SCROLL_BACKDROP_MIN_INTERVAL_MS
) {
    private var lastDispatchUptimeMs = 0L
    private var disposed = false

    fun request(force: Boolean = false) {
        if (disposed) return
        val now = SystemClock.uptimeMillis()
        if (force || now - lastDispatchUptimeMs >= scrollMinIntervalMs) {
            lastDispatchUptimeMs = now
            ticker.requestFrame(force = force)
        }
    }

    fun dispose() {
        disposed = true
    }
}

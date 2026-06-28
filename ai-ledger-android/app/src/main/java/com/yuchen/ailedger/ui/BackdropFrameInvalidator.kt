package com.yuchen.ailedger.ui

/**
 * Lightweight scroll/backdrop invalidation gate.
 *
 * 所有滚动回调直接交给 [BackdropFrameTicker]；Ticker 使用 Choreographer 保证同一显示帧
 * 最多提交一次，因此不再用固定 12ms 节流与 60/90/120Hz 屏幕节奏互相打架。
 */
internal class BackdropFrameInvalidator(
    private val ticker: BackdropFrameTicker,
) {
    private var disposed = false

    fun request(force: Boolean = false) {
        if (disposed) return
        ticker.requestFrame(force = force)
    }

    fun dispose() {
        disposed = true
    }
}

package com.yuchen.ailedger.ui

/**
 * 滚动到 OpenGL 背景坐标的最短失效链。
 *
 * NestedScroll 回调已经发生在当前 Android traversal 内，因此无需再绕一层 Choreographer。
 * 直接登记到根视图 PreDraw：同一帧的 LazyColumn 位移、卡片最终矩形、背景原点和按压状态
 * 只保留最后一份快照，避免 OpenGL 先显示旧滚动坐标、下一帧再突然纠正。
 */
internal class BackdropFrameInvalidator(
    private val ticker: BackdropFrameTicker,
) {
    private var disposed = false

    @Suppress("UNUSED_PARAMETER")
    fun request(force: Boolean = false) {
        if (disposed) return
        ticker.requestFinalizedFrame()
    }

    fun dispose() {
        disposed = true
    }
}

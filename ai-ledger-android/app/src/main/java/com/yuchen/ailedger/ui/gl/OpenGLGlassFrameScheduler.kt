package com.yuchen.ailedger.ui.gl

import java.util.WeakHashMap

/**
 * 将同一显示帧内来自 Compose 布局、参数与按压状态的多次渲染请求合并为一次。
 *
 * 这里只调整调度节拍，不改变 Shader、纹理、玻璃几何或 OpenGL Host 尺寸链。
 */
private val scheduledFrameRenders = WeakHashMap<WebOpenGLGlassCardHostView, Unit>()

internal fun WebOpenGLGlassCardHostView.requestRenderOnNextAnimationFrame() {
    val shouldSchedule = synchronized(scheduledFrameRenders) {
        if (scheduledFrameRenders.containsKey(this)) {
            false
        } else {
            scheduledFrameRenders[this] = Unit
            true
        }
    }
    if (!shouldSchedule) return

    postOnAnimation {
        synchronized(scheduledFrameRenders) {
            scheduledFrameRenders.remove(this)
        }
        requestRender()
    }
}

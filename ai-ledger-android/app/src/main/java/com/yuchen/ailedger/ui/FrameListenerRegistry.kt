package com.yuchen.ailedger.ui

/**
 * 主线程帧监听器注册表。
 *
 * 监听器只在 Host attach/detach 时变化，因此把快照复制成本放在低频注册阶段；
 * VSync 热路径使用稳定列表和下标循环，不创建临时 List 或 Iterator。
 */
internal class FrameListenerRegistry {
    private var listeners: List<() -> Unit> = emptyList()

    fun add(listener: () -> Unit): () -> Unit {
        val current = listeners
        var index = 0
        while (index < current.size) {
            if (current[index] === listener) return { remove(listener) }
            index += 1
        }
        listeners = current + listener
        return { remove(listener) }
    }

    fun dispatch() {
        val snapshot = listeners
        var index = 0
        while (index < snapshot.size) {
            snapshot[index].invoke()
            index += 1
        }
    }

    private fun remove(listener: () -> Unit) {
        val current = listeners
        var removeIndex = -1
        var index = 0
        while (index < current.size) {
            if (current[index] === listener) {
                removeIndex = index
                break
            }
            index += 1
        }
        if (removeIndex < 0) return
        val next = ArrayList<() -> Unit>((current.size - 1).coerceAtLeast(0))
        index = 0
        while (index < current.size) {
            if (index != removeIndex) next += current[index]
            index += 1
        }
        listeners = next
    }
}

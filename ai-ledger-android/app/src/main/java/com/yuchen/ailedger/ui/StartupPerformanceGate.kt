package com.yuchen.ailedger.ui

import android.os.SystemClock
import androidx.compose.runtime.withFrameNanos
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Process-scoped cold-start coordinator.
 *
 * Expensive work is released by measured frame stability instead of arbitrary short delays:
 * 1. the first screen and entrance layout settle;
 * 2. backdrop textures are decoded or generated;
 * 3. the OpenGL Shell mounts, compiles and uploads its textures;
 * 4. continuous visual effects and deferred business work are released.
 *
 * The gates reset naturally when Android creates a new process.
 */
internal object StartupPerformanceGate {
    private const val STABLE_FRAME_LIMIT_NS = 25_000_000L
    private const val DEFERRED_BUSINESS_SETTLE_MS = 320L

    private val initialWindowOwner = AtomicBoolean(false)
    private val postBackdropWindowOwner = AtomicBoolean(false)
    private val deferredBusinessWindowOwner = AtomicBoolean(false)

    private val initialWindowReady = CompletableDeferred<Unit>()
    private val backdropWorkFinished = CompletableDeferred<Unit>()
    private val postBackdropWindowReady = CompletableDeferred<Unit>()
    private val fullEffectsReady = CompletableDeferred<Unit>()
    private val deferredBusinessWindowReady = CompletableDeferred<Unit>()

    suspend fun awaitInitialTextureBuildWindow() {
        if (initialWindowReady.isCompleted) return
        if (initialWindowOwner.compareAndSet(false, true)) {
            withContext(NonCancellable) {
                try {
                    StartupMetrics.setWarmupState("首屏稳定中")
                    awaitStableFrameWindow(
                        minimumElapsedMs = 460L,
                        requiredStableFrames = 5,
                        maximumWaitMs = 1_800L
                    )
                    StartupMetrics.markOnce("首屏稳定窗口完成")
                } finally {
                    initialWindowReady.complete(Unit)
                }
            }
        } else {
            initialWindowReady.await()
        }
    }

    fun markBackdropWorkFinished(success: Boolean) {
        if (!backdropWorkFinished.isCompleted) {
            StartupMetrics.setWarmupState(if (success) "背景纹理已准备" else "背景纹理使用安全降级")
            StartupMetrics.markOnce(if (success) "背景纹理准备完成" else "背景纹理准备失败")
            backdropWorkFinished.complete(Unit)
        }
    }

    suspend fun awaitPostBackdropStability() {
        backdropWorkFinished.await()
        if (postBackdropWindowReady.isCompleted) return
        if (postBackdropWindowOwner.compareAndSet(false, true)) {
            withContext(NonCancellable) {
                try {
                    StartupMetrics.setWarmupState("OpenGL稳定中")
                    awaitStableFrameWindow(
                        minimumElapsedMs = 140L,
                        requiredStableFrames = 5,
                        maximumWaitMs = 1_600L
                    )
                    StartupMetrics.markOnce("OpenGL与纹理稳定窗口完成")
                } finally {
                    postBackdropWindowReady.complete(Unit)
                }
            }
        } else {
            postBackdropWindowReady.await()
        }
    }

    fun markFullEffectsReady() {
        if (!fullEffectsReady.isCompleted) {
            StartupMetrics.setWarmupState("完整效果已开启")
            StartupMetrics.markOnce("完整动态效果开启")
            fullEffectsReady.complete(Unit)
        }
    }

    /**
     * This gate may be awaited from a background dispatcher. It intentionally uses a short settling
     * delay after the Compose-measured stability gates instead of withFrameNanos, which would require
     * a MonotonicFrameClock and fail outside a composition coroutine.
     */
    suspend fun awaitDeferredBusinessWindow() {
        fullEffectsReady.await()
        if (deferredBusinessWindowReady.isCompleted) return
        if (deferredBusinessWindowOwner.compareAndSet(false, true)) {
            withContext(NonCancellable) {
                try {
                    delay(DEFERRED_BUSINESS_SETTLE_MS)
                    StartupMetrics.markOnce("延后业务任务窗口开启")
                } finally {
                    deferredBusinessWindowReady.complete(Unit)
                }
            }
        } else {
            deferredBusinessWindowReady.await()
        }
    }

    private suspend fun awaitStableFrameWindow(
        minimumElapsedMs: Long,
        requiredStableFrames: Int,
        maximumWaitMs: Long
    ) {
        val startedAt = SystemClock.elapsedRealtime()
        var previousFrameNanos = 0L
        var stableFrames = 0

        withTimeoutOrNull(maximumWaitMs) {
            while (stableFrames < requiredStableFrames ||
                SystemClock.elapsedRealtime() - startedAt < minimumElapsedMs
            ) {
                val frameNanos = withFrameNanos { it }
                if (previousFrameNanos != 0L) {
                    val frameDelta = frameNanos - previousFrameNanos
                    stableFrames = if (frameDelta in 1L..STABLE_FRAME_LIMIT_NS) {
                        stableFrames + 1
                    } else {
                        0
                    }
                }
                previousFrameNanos = frameNanos
            }
        }
    }
}

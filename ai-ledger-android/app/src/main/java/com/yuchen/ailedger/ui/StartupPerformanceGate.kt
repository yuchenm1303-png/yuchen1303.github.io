package com.yuchen.ailedger.ui

import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.withFrameNanos
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Process-scoped cold-start coordinator.
 *
 * Expensive work is released by measured readiness instead of arbitrary long delays:
 * 1. the first screen is stable and persisted preferences have produced their first snapshot;
 * 2. the critical clear + medium backdrop sampler is ready;
 * 3. the real OpenGL Shell has presented its first frame;
 * 4. the remaining low/high pyramid, continuous effects and deferred business work are released.
 *
 * The gates reset naturally when Android creates a new process.
 */
internal object StartupPerformanceGate {
    private const val STABLE_FRAME_LIMIT_NS = 25_000_000L
    private const val PREFERENCES_READY_TIMEOUT_MS = 900L
    private const val OPENGL_FIRST_FRAME_TIMEOUT_MS = 1_600L
    private const val OPENGL_PYRAMID_PAUSE_TIMEOUT_MS = 700L
    private const val DEFERRED_BUSINESS_SETTLE_MS = 140L

    private val initialWindowOwner = AtomicBoolean(false)
    private val postBackdropWindowOwner = AtomicBoolean(false)
    private val deferredBusinessWindowOwner = AtomicBoolean(false)

    private val preferencesReady = CompletableDeferred<Unit>()
    private val initialWindowReady = CompletableDeferred<Unit>()
    private val backdropWorkFinished = CompletableDeferred<Unit>()
    private val openGlFirstFrameReady = CompletableDeferred<Unit>()
    private val openGlFirstFrameLatch = CountDownLatch(1)
    private val postBackdropWindowReady = CompletableDeferred<Unit>()
    private val fullEffectsReady = CompletableDeferred<Unit>()
    private val deferredBusinessWindowReady = CompletableDeferred<Unit>()

    fun markPreferencesReady() {
        if (!preferencesReady.isCompleted) preferencesReady.complete(Unit)
    }

    fun markOpenGlFirstFrameReady() {
        openGlFirstFrameLatch.countDown()
        if (!openGlFirstFrameReady.isCompleted) {
            StartupMetrics.markOnce("OpenGL真实首帧完成")
            openGlFirstFrameReady.complete(Unit)
        }
    }

    /**
     * Called only by the low-priority backdrop builder after the exact medium sampler has been
     * published. Pausing the remaining low/high CPU passes keeps them from overlapping EGL creation,
     * shader compilation and the first texture upload. The timeout protects non-UI/background starts.
     */
    fun awaitOpenGlFirstFrameBeforePyramidCompletion() {
        if (openGlFirstFrameReady.isCompleted) return
        if (Looper.myLooper() == Looper.getMainLooper()) return
        runCatching {
            openGlFirstFrameLatch.await(OPENGL_PYRAMID_PAUSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
    }

    suspend fun awaitInitialTextureBuildWindow() {
        if (initialWindowReady.isCompleted) return
        if (initialWindowOwner.compareAndSet(false, true)) {
            withContext(NonCancellable) {
                try {
                    StartupMetrics.setWarmupState("首屏与偏好稳定中")
                    coroutineScope {
                        val frameWindow = async {
                            awaitStableFrameWindow(
                                minimumElapsedMs = 260L,
                                requiredStableFrames = 4,
                                maximumWaitMs = 1_200L
                            )
                        }
                        val preferenceWindow = async {
                            withTimeoutOrNull(PREFERENCES_READY_TIMEOUT_MS) {
                                preferencesReady.await()
                            }
                        }
                        frameWindow.await()
                        preferenceWindow.await()
                    }
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
                    StartupMetrics.setWarmupState("OpenGL真实首帧等待中")
                    withTimeoutOrNull(OPENGL_FIRST_FRAME_TIMEOUT_MS) {
                        openGlFirstFrameReady.await()
                    }
                    StartupMetrics.setWarmupState("OpenGL首帧后稳定中")
                    awaitStableFrameWindow(
                        minimumElapsedMs = 96L,
                        requiredStableFrames = 4,
                        maximumWaitMs = 900L
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

    /**
     * The display-mode request is released at the business gate. Notification permission waits for
     * another measured stable-frame window so the system dialog never lands in the same frame burst
     * as a refresh-rate switch or the first background cache write.
     */
    suspend fun awaitNotificationPermissionWindow() {
        awaitDeferredBusinessWindow()
        awaitStableFrameWindow(
            minimumElapsedMs = 140L,
            requiredStableFrames = 3,
            maximumWaitMs = 700L
        )
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

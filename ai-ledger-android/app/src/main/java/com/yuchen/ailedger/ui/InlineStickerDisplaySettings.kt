package com.yuchen.ailedger.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lightweight display preference for inline chat stickers.
 *
 * The current value is snapshot state so the real chat renderer and the settings preview update
 * immediately while the user drags. Persistence is conflated and delayed until dragging settles,
 * which avoids issuing a SharedPreferences write for every slider frame.
 */
internal object InlineStickerDisplaySettings {
    const val DefaultSizeDp = 60f
    const val MinSizeDp = 40f
    const val MaxSizeDp = 88f
    val SizeRange: ClosedFloatingPointRange<Float> = MinSizeDp..MaxSizeDp

    private const val PreferencesName = "inline_sticker_display_settings"
    private const val SizeKey = "inline_sticker_size_dp"
    private const val PersistSettleMs = 140L
    private const val ValueEpsilon = 0.001f

    private val initialized = AtomicBoolean(false)
    private val writerStarted = AtomicBoolean(false)
    private val pendingWrites = Channel<Float>(Channel.CONFLATED)
    private val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var applicationContext: Context? = null

    private var sizeDpState by mutableFloatStateOf(DefaultSizeDp)

    @Composable
    fun sizeDp(context: Context): Float {
        val appContext = context.applicationContext
        LaunchedEffect(appContext) {
            initialize(appContext)
        }
        return sizeDpState
    }

    fun updateSizeDp(context: Context, value: Float) {
        initialize(context.applicationContext)
        val normalized = value.coerceIn(MinSizeDp, MaxSizeDp)
        if (abs(sizeDpState - normalized) <= ValueEpsilon) return
        sizeDpState = normalized
        ensureWriterStarted()
        pendingWrites.trySend(normalized)
    }

    private fun initialize(context: Context) {
        if (initialized.get()) return
        synchronized(this) {
            if (initialized.get()) return
            applicationContext = context.applicationContext
            sizeDpState = context
                .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
                .getFloat(SizeKey, DefaultSizeDp)
                .coerceIn(MinSizeDp, MaxSizeDp)
            initialized.set(true)
        }
    }

    private fun ensureWriterStarted() {
        if (!writerStarted.compareAndSet(false, true)) return
        writerScope.launch {
            for (firstValue in pendingWrites) {
                var latestValue = firstValue
                while (true) {
                    val nextValue = withTimeoutOrNull(PersistSettleMs) {
                        pendingWrites.receive()
                    } ?: break
                    latestValue = nextValue
                }
                applicationContext
                    ?.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
                    ?.edit()
                    ?.putFloat(SizeKey, latestValue.coerceIn(MinSizeDp, MaxSizeDp))
                    ?.apply()
            }
        }
    }
}

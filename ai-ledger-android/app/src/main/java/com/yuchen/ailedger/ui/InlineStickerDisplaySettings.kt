package com.yuchen.ailedger.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal data class InlineStickerExpressionPreferences(
    val frequency: Int,
    val intensity: Int,
    val maxPerReply: Int,
    val repeatCount: Int,
)

/**
 * Lightweight display and model-expression preferences for inline chat stickers.
 *
 * Values are snapshot state so the settings preview updates immediately. Persistence is conflated
 * and delayed until dragging settles, which avoids issuing a SharedPreferences write per frame.
 */
internal object InlineStickerDisplaySettings {
    const val DefaultSizeDp = 42f
    const val MinSizeDp = 40f
    const val MaxSizeDp = 88f
    val SizeRange: ClosedFloatingPointRange<Float> = MinSizeDp..MaxSizeDp

    const val DefaultFrequency = 50
    const val DefaultIntensity = 50
    const val DefaultMaxPerReply = 0
    const val DefaultRepeatCount = 1
    val FrequencyRange: ClosedFloatingPointRange<Float> = 0f..100f
    val IntensityRange: ClosedFloatingPointRange<Float> = 0f..100f
    val MaxPerReplyRange: ClosedFloatingPointRange<Float> = 0f..64f
    val RepeatCountRange: ClosedFloatingPointRange<Float> = 1f..4f

    private const val PreferencesName = "inline_sticker_display_settings"
    private const val SizeKey = "inline_sticker_size_dp"
    private const val FrequencyKey = "inline_sticker_frequency"
    private const val IntensityKey = "inline_sticker_intensity"
    private const val MaxPerReplyKey = "inline_sticker_max_per_reply"
    private const val RepeatCountKey = "inline_sticker_repeat_count"
    private const val PersistSettleMs = 140L
    private const val ValueEpsilon = 0.001f

    private data class PersistedValues(
        val sizeDp: Float,
        val frequency: Int,
        val intensity: Int,
        val maxPerReply: Int,
        val repeatCount: Int,
    )

    private val initialized = AtomicBoolean(false)
    private val writerStarted = AtomicBoolean(false)
    private val pendingWrites = Channel<PersistedValues>(Channel.CONFLATED)
    private val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    private var sizeDpValue = DefaultSizeDp

    @Volatile
    private var frequencyValue = DefaultFrequency

    @Volatile
    private var intensityValue = DefaultIntensity

    @Volatile
    private var maxPerReplyValue = DefaultMaxPerReply

    @Volatile
    private var repeatCountValue = DefaultRepeatCount

    private var sizeDpState by mutableFloatStateOf(DefaultSizeDp)
    private var frequencyState by mutableIntStateOf(DefaultFrequency)
    private var intensityState by mutableIntStateOf(DefaultIntensity)
    private var maxPerReplyState by mutableIntStateOf(DefaultMaxPerReply)
    private var repeatCountState by mutableIntStateOf(DefaultRepeatCount)

    @Composable
    fun sizeDp(context: Context): Float {
        ensureInitialized(context)
        return sizeDpState
    }

    @Composable
    fun expressionPreferences(context: Context): InlineStickerExpressionPreferences {
        ensureInitialized(context)
        return InlineStickerExpressionPreferences(
            frequency = frequencyState,
            intensity = intensityState,
            maxPerReply = maxPerReplyState,
            repeatCount = repeatCountState,
        )
    }

    fun currentExpressionPreferences(context: Context?): InlineStickerExpressionPreferences {
        if (initialized.get()) return currentValues()
        val appContext = context?.applicationContext ?: return currentValues()
        return readExpressionPreferences(appContext)
    }

    fun updateSizeDp(context: Context, value: Float) {
        initialize(context.applicationContext)
        val normalized = value.coerceIn(MinSizeDp, MaxSizeDp)
        if (abs(sizeDpValue - normalized) <= ValueEpsilon) return
        sizeDpValue = normalized
        sizeDpState = normalized
        enqueuePersistence()
    }

    fun updateFrequency(context: Context, value: Float) {
        initialize(context.applicationContext)
        val normalized = value.roundToInt().coerceIn(0, 100)
        if (frequencyValue == normalized) return
        frequencyValue = normalized
        frequencyState = normalized
        enqueuePersistence()
    }

    fun updateIntensity(context: Context, value: Float) {
        initialize(context.applicationContext)
        val normalized = value.roundToInt().coerceIn(0, 100)
        if (intensityValue == normalized) return
        intensityValue = normalized
        intensityState = normalized
        enqueuePersistence()
    }

    fun updateMaxPerReply(context: Context, value: Float) {
        initialize(context.applicationContext)
        val normalized = value.roundToInt().coerceIn(0, 64)
        if (maxPerReplyValue == normalized) return
        maxPerReplyValue = normalized
        maxPerReplyState = normalized
        enqueuePersistence()
    }

    fun updateRepeatCount(context: Context, value: Float) {
        initialize(context.applicationContext)
        val normalized = value.roundToInt().coerceIn(1, 4)
        if (repeatCountValue == normalized) return
        repeatCountValue = normalized
        repeatCountState = normalized
        enqueuePersistence()
    }

    @Composable
    private fun ensureInitialized(context: Context) {
        val appContext = context.applicationContext
        LaunchedEffect(appContext) {
            initialize(appContext)
        }
    }

    private fun initialize(context: Context) {
        if (initialized.get()) return
        synchronized(this) {
            if (initialized.get()) return
            val appContext = context.applicationContext
            val preferences = appContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            applicationContext = appContext
            sizeDpValue = preferences
                .getFloat(SizeKey, DefaultSizeDp)
                .coerceIn(MinSizeDp, MaxSizeDp)
            val expression = readExpressionPreferences(appContext)
            frequencyValue = expression.frequency
            intensityValue = expression.intensity
            maxPerReplyValue = expression.maxPerReply
            repeatCountValue = expression.repeatCount
            sizeDpState = sizeDpValue
            frequencyState = frequencyValue
            intensityState = intensityValue
            maxPerReplyState = maxPerReplyValue
            repeatCountState = repeatCountValue
            initialized.set(true)
        }
    }

    private fun readExpressionPreferences(context: Context): InlineStickerExpressionPreferences {
        val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        return InlineStickerExpressionPreferences(
            frequency = preferences
                .getInt(FrequencyKey, DefaultFrequency)
                .coerceIn(0, 100),
            intensity = preferences
                .getInt(IntensityKey, DefaultIntensity)
                .coerceIn(0, 100),
            maxPerReply = preferences
                .getInt(MaxPerReplyKey, DefaultMaxPerReply)
                .coerceIn(0, 64),
            repeatCount = preferences
                .getInt(RepeatCountKey, DefaultRepeatCount)
                .coerceIn(1, 4),
        )
    }

    private fun currentValues(): InlineStickerExpressionPreferences {
        return InlineStickerExpressionPreferences(
            frequency = frequencyValue,
            intensity = intensityValue,
            maxPerReply = maxPerReplyValue,
            repeatCount = repeatCountValue,
        )
    }

    private fun enqueuePersistence() {
        ensureWriterStarted()
        pendingWrites.trySend(
            PersistedValues(
                sizeDp = sizeDpValue,
                frequency = frequencyValue,
                intensity = intensityValue,
                maxPerReply = maxPerReplyValue,
                repeatCount = repeatCountValue,
            )
        )
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
                    ?.putFloat(SizeKey, latestValue.sizeDp.coerceIn(MinSizeDp, MaxSizeDp))
                    ?.putInt(FrequencyKey, latestValue.frequency.coerceIn(0, 100))
                    ?.putInt(IntensityKey, latestValue.intensity.coerceIn(0, 100))
                    ?.putInt(MaxPerReplyKey, latestValue.maxPerReply.coerceIn(0, 64))
                    ?.putInt(RepeatCountKey, latestValue.repeatCount.coerceIn(1, 4))
                    ?.apply()
            }
        }
    }
}

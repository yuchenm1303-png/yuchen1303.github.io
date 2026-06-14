package com.yuchen.ailedger.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BUILTIN_THEME_BACKGROUND_PATH
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.RainbowPrismStyle
import com.yuchen.ailedger.model.RenderQuality
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val Context.assistantPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "assistant_preferences"
)

private const val SLIDER_PERSIST_SETTLE_MS = 140L

data class AssistantPreferences(
    val quality: RenderQuality = RenderQuality.Balanced,
    val showPreviewConversation: Boolean = true,
    val glassPreset: GlassPreset = GlassPreset.Liquid,
    val backgroundTheme: BackgroundTheme = BackgroundTheme.Aurora,
    val customBackgroundPath: String? = null,
    val glassIntensity: Float = 1f,
    val motionIntensity: Float = 1f,
    val rainbowPrismStyle: RainbowPrismStyle = RainbowPrismStyle(),
    val navigationHomeAddress: String = "",
    val navigationSchoolAddress: String = "",
    val navigationCompanyAddress: String = "",
    val navigationDormAddress: String = ""
)

class AssistantPreferencesStore(private val context: Context) {
    private var pendingThemeSelection = false

    // 参数滑块在拖动时可能每帧产生新值。这里使用 CONFLATED 通道，
    // 仅在数值稳定一小段时间后写入最后一个值，避免 DataStore 历史写入
    // 排队后通过 preferencesFlow 回灌旧值，造成松手回跳和额外 I/O。
    private val sliderWriterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val glassIntensityWrites = Channel<Float>(Channel.CONFLATED)
    private val motionIntensityWrites = Channel<Float>(Channel.CONFLATED)
    private val rainbowPrismWrites = Channel<RainbowPrismStyle>(Channel.CONFLATED)

    private object Keys {
        val renderQuality = stringPreferencesKey("render_quality")
        val showPreviewConversation = booleanPreferencesKey("show_preview_conversation")
        val glassPreset = stringPreferencesKey("glass_preset")
        val backgroundTheme = stringPreferencesKey("background_theme")
        val customBackgroundPath = stringPreferencesKey("custom_background_path")
        val glassIntensity = floatPreferencesKey("glass_intensity")
        val motionIntensity = floatPreferencesKey("motion_intensity")
        val rainbowOverall = floatPreferencesKey("rainbow_overall")
        val rainbowEdgeHighlight = floatPreferencesKey("rainbow_edge_highlight")
        val rainbowSweepMin = floatPreferencesKey("rainbow_sweep_min")
        val rainbowSweepMax = floatPreferencesKey("rainbow_sweep_max")
        val legacyRainbowDiagonalSweep = floatPreferencesKey("rainbow_diagonal_sweep")
        val rainbowHalo = floatPreferencesKey("rainbow_halo")
        val navigationHomeAddress = stringPreferencesKey("navigation_home_address")
        val navigationSchoolAddress = stringPreferencesKey("navigation_school_address")
        val navigationCompanyAddress = stringPreferencesKey("navigation_company_address")
        val navigationDormAddress = stringPreferencesKey("navigation_dorm_address")
    }

    init {
        sliderWriterScope.launch {
            consumeSettledValues(glassIntensityWrites) { value ->
                context.assistantPreferencesDataStore.edit {
                    it[Keys.glassIntensity] = value.coerceIn(0.6f, 1.4f)
                }
            }
        }
        sliderWriterScope.launch {
            consumeSettledValues(motionIntensityWrites) { value ->
                context.assistantPreferencesDataStore.edit {
                    it[Keys.motionIntensity] = value.coerceIn(0f, 1.4f)
                }
            }
        }
        sliderWriterScope.launch {
            consumeSettledValues(rainbowPrismWrites) { style ->
                context.assistantPreferencesDataStore.edit {
                    it[Keys.rainbowOverall] = style.overall
                    it[Keys.rainbowEdgeHighlight] = style.edgeHighlight
                    it[Keys.rainbowSweepMin] = style.sweepMin
                    it[Keys.rainbowSweepMax] = style.sweepMax
                    it[Keys.rainbowHalo] = style.rainbowHalo
                }
            }
        }
    }

    val preferencesFlow: Flow<AssistantPreferences> = context.assistantPreferencesDataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences ->
            val customPath = preferences[Keys.customBackgroundPath]?.takeIf { it.isNotBlank() }
            val preset = RainbowPrismStyle()
            val legacySweep = preferences[Keys.legacyRainbowDiagonalSweep]
            val rawMin = preferences[Keys.rainbowSweepMin] ?: legacySweep?.let { (it * 0.50f).coerceIn(0f, 2f) } ?: preset.sweepMin
            val rawMax = preferences[Keys.rainbowSweepMax] ?: legacySweep?.let { it.coerceIn(0f, 2f) } ?: preset.sweepMax
            val sweepMin = minOf(rawMin, rawMax).coerceIn(0f, 2f)
            val sweepMax = maxOf(rawMin, rawMax).coerceIn(0f, 2f)
            AssistantPreferences(
                quality = preferences[Keys.renderQuality]?.let(RenderQuality::fromStorage) ?: RenderQuality.Balanced,
                showPreviewConversation = preferences[Keys.showPreviewConversation] ?: true,
                glassPreset = preferences[Keys.glassPreset]?.let(GlassPreset::fromStorage) ?: GlassPreset.Liquid,
                backgroundTheme = preferences[Keys.backgroundTheme]?.let(BackgroundTheme::fromStorage) ?: BackgroundTheme.Aurora,
                customBackgroundPath = customPath,
                glassIntensity = (preferences[Keys.glassIntensity] ?: 1f).coerceIn(0.6f, 1.4f),
                motionIntensity = (preferences[Keys.motionIntensity] ?: 1f).coerceIn(0f, 1.4f),
                rainbowPrismStyle = RainbowPrismStyle(
                    overall = (preferences[Keys.rainbowOverall] ?: preset.overall).coerceIn(0f, 2f),
                    edgeHighlight = (preferences[Keys.rainbowEdgeHighlight] ?: preset.edgeHighlight).coerceIn(0f, 2f),
                    sweepMin = sweepMin,
                    sweepMax = sweepMax,
                    rainbowHalo = (preferences[Keys.rainbowHalo] ?: preset.rainbowHalo).coerceIn(0f, 2f)
                ),
                navigationHomeAddress = preferences[Keys.navigationHomeAddress].orEmpty(),
                navigationSchoolAddress = preferences[Keys.navigationSchoolAddress].orEmpty(),
                navigationCompanyAddress = preferences[Keys.navigationCompanyAddress].orEmpty(),
                navigationDormAddress = preferences[Keys.navigationDormAddress].orEmpty()
            ).also { AssistantLocalMemoryRuntime.update(it) }
        }

    suspend fun setRenderQuality(quality: RenderQuality) {
        context.assistantPreferencesDataStore.edit { it[Keys.renderQuality] = quality.storageValue }
    }

    suspend fun setShowPreviewConversation(showPreviewConversation: Boolean) {
        context.assistantPreferencesDataStore.edit { it[Keys.showPreviewConversation] = showPreviewConversation }
    }

    suspend fun setGlassPreset(glassPreset: GlassPreset) {
        context.assistantPreferencesDataStore.edit { it[Keys.glassPreset] = glassPreset.storageValue }
    }

    suspend fun setBackgroundTheme(backgroundTheme: BackgroundTheme) {
        pendingThemeSelection = true
        context.assistantPreferencesDataStore.edit { it[Keys.backgroundTheme] = backgroundTheme.storageValue }
    }

    suspend fun setCustomBackgroundPath(path: String?) {
        context.assistantPreferencesDataStore.edit { preferences ->
            if (path.isNullOrBlank()) {
                if (pendingThemeSelection) preferences[Keys.customBackgroundPath] = BUILTIN_THEME_BACKGROUND_PATH
                else preferences.remove(Keys.customBackgroundPath)
                pendingThemeSelection = false
            } else {
                pendingThemeSelection = false
                preferences[Keys.customBackgroundPath] = path
            }
        }
    }

    suspend fun setGlassIntensity(glassIntensity: Float) {
        glassIntensityWrites.send(glassIntensity.coerceIn(0.6f, 1.4f))
    }

    suspend fun setMotionIntensity(motionIntensity: Float) {
        motionIntensityWrites.send(motionIntensity.coerceIn(0f, 1.4f))
    }

    suspend fun setRainbowPrismStyle(style: RainbowPrismStyle) {
        rainbowPrismWrites.send(style.normalized())
    }

    suspend fun setNavigationAddress(slot: String, address: String) {
        val cleanAddress = address.trim().take(80)
        context.assistantPreferencesDataStore.edit { preferences ->
            when (slot) {
                "home" -> preferences[Keys.navigationHomeAddress] = cleanAddress
                "school" -> preferences[Keys.navigationSchoolAddress] = cleanAddress
                "company" -> preferences[Keys.navigationCompanyAddress] = cleanAddress
                "dorm" -> preferences[Keys.navigationDormAddress] = cleanAddress
            }
        }
    }

    private suspend fun <T> consumeSettledValues(
        channel: Channel<T>,
        persist: suspend (T) -> Unit
    ) {
        for (firstValue in channel) {
            var latestValue = firstValue
            while (true) {
                val nextValue = withTimeoutOrNull(SLIDER_PERSIST_SETTLE_MS) {
                    channel.receive()
                } ?: break
                latestValue = nextValue
            }
            persist(latestValue)
        }
    }

    private fun RainbowPrismStyle.normalized(): RainbowPrismStyle {
        val minValue = minOf(sweepMin, sweepMax).coerceIn(0f, 2f)
        val maxValue = maxOf(sweepMin, sweepMax).coerceIn(0f, 2f)
        return RainbowPrismStyle(
            overall = overall.coerceIn(0f, 2f),
            edgeHighlight = edgeHighlight.coerceIn(0f, 2f),
            sweepMin = minValue,
            sweepMax = maxValue,
            rainbowHalo = rainbowHalo.coerceIn(0f, 2f)
        )
    }
}

package com.yuchen.ailedger.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Exact parameter set used by the approved web HUD. */
data class VisualAgentHudParameters(
    val p0x: Float = 7.8f,
    val p0y: Float = 7.8f,
    val p1x: Float = 20.4f,
    val p1y: Float = 10.4f,
    val p2x: Float = 51.1f,
    val p2y: Float = 29.1f,
    val p3x: Float = 34.1f,
    val p3y: Float = 35.2f,
    val p4x: Float = 29.2f,
    val p4y: Float = 54.3f,
    val p5x: Float = 22.7f,
    val p5y: Float = 45.2f,
    val p6x: Float = 13.1f,
    val p6y: Float = 24.2f,
    val tension: Float = 0.91f,
    val size: Float = 62f,
    val scaleX: Float = 1f,
    val scaleY: Float = 0.95f,
    val rotation: Float = -2.5f,
    val offsetX: Float = -0.5f,
    val offsetY: Float = -0.5f,
    val hotspotX: Float = 10f,
    val hotspotY: Float = 10.5f,
    val cyanOpacity: Float = 0.92f,
    val whiteOpacity: Float = 0.86f,
    val pinkOpacity: Float = 0.72f,
    val outerRimWidth: Float = 1.02f,
    val innerRimWidth: Float = 0.42f,
    val rimOpacity: Float = 0.95f,
    val glowBlur: Float = 1.45f,
    val glowOpacity: Float = 0.18f,
    val auraSize: Float = 72f,
    val auraBlur: Float = 8f,
    val auraOpacity: Float = 0.54f,
    val cyanX: Float = 33.7f,
    val cyanY: Float = 16f,
    val cyanSizeX: Float = 23f,
    val cyanSizeY: Float = 18f,
    val whiteX: Float = 29f,
    val whiteY: Float = 28f,
    val whiteSizeX: Float = 18f,
    val whiteSizeY: Float = 14f,
    val pinkX: Float = 30f,
    val pinkY: Float = 50f,
    val pinkSizeX: Float = 18f,
    val pinkSizeY: Float = 18f,
    val innerGlowX: Float = 28.2f,
    val innerGlowY: Float = 28.1f,
    val innerGlowRx: Float = 11.8f,
    val innerGlowRy: Float = 8.2f,
    val innerGlowOpacity: Float = 0.09f,
    val innerGlowBlur: Float = 2.5f,
    val edgeInset: Float = 0f,
    val edgeRadius: Float = 0f,
    val edgeHaloWidth: Float = 42f,
    val edgeHaloBlur: Float = 0f,
    val edgeHaloOpacity: Float = 0.58f,
    val edgeCastDepth: Float = 120f,
    val edgeCastBlur: Float = 0f,
    val edgeCastOpacity: Float = 0.8f,
    val edgeFlowDuration: Float = 7.5f,
    val edgeBreathDuration: Float = 1.5f,
    val edgeBreathStrength: Float = 0.55f,
) {
    fun withValue(key: String, value: Float): VisualAgentHudParameters = when (key) {
        "p0x" -> copy(p0x = value); "p0y" -> copy(p0y = value)
        "p1x" -> copy(p1x = value); "p1y" -> copy(p1y = value)
        "p2x" -> copy(p2x = value); "p2y" -> copy(p2y = value)
        "p3x" -> copy(p3x = value); "p3y" -> copy(p3y = value)
        "p4x" -> copy(p4x = value); "p4y" -> copy(p4y = value)
        "p5x" -> copy(p5x = value); "p5y" -> copy(p5y = value)
        "p6x" -> copy(p6x = value); "p6y" -> copy(p6y = value)
        "tension" -> copy(tension = value); "size" -> copy(size = value)
        "scaleX" -> copy(scaleX = value); "scaleY" -> copy(scaleY = value)
        "rotation" -> copy(rotation = value); "offsetX" -> copy(offsetX = value)
        "offsetY" -> copy(offsetY = value); "hotspotX" -> copy(hotspotX = value)
        "hotspotY" -> copy(hotspotY = value); "cyanOpacity" -> copy(cyanOpacity = value)
        "whiteOpacity" -> copy(whiteOpacity = value); "pinkOpacity" -> copy(pinkOpacity = value)
        "outerRimWidth" -> copy(outerRimWidth = value); "innerRimWidth" -> copy(innerRimWidth = value)
        "rimOpacity" -> copy(rimOpacity = value); "glowBlur" -> copy(glowBlur = value)
        "glowOpacity" -> copy(glowOpacity = value); "auraSize" -> copy(auraSize = value)
        "auraBlur" -> copy(auraBlur = value); "auraOpacity" -> copy(auraOpacity = value)
        "cyanX" -> copy(cyanX = value); "cyanY" -> copy(cyanY = value)
        "cyanSizeX" -> copy(cyanSizeX = value); "cyanSizeY" -> copy(cyanSizeY = value)
        "whiteX" -> copy(whiteX = value); "whiteY" -> copy(whiteY = value)
        "whiteSizeX" -> copy(whiteSizeX = value); "whiteSizeY" -> copy(whiteSizeY = value)
        "pinkX" -> copy(pinkX = value); "pinkY" -> copy(pinkY = value)
        "pinkSizeX" -> copy(pinkSizeX = value); "pinkSizeY" -> copy(pinkSizeY = value)
        "innerGlowX" -> copy(innerGlowX = value); "innerGlowY" -> copy(innerGlowY = value)
        "innerGlowRx" -> copy(innerGlowRx = value); "innerGlowRy" -> copy(innerGlowRy = value)
        "innerGlowOpacity" -> copy(innerGlowOpacity = value); "innerGlowBlur" -> copy(innerGlowBlur = value)
        "edgeInset" -> copy(edgeInset = value); "edgeRadius" -> copy(edgeRadius = value)
        "edgeHaloWidth" -> copy(edgeHaloWidth = value); "edgeHaloBlur" -> copy(edgeHaloBlur = value)
        "edgeHaloOpacity" -> copy(edgeHaloOpacity = value); "edgeCastDepth" -> copy(edgeCastDepth = value)
        "edgeCastBlur" -> copy(edgeCastBlur = value); "edgeCastOpacity" -> copy(edgeCastOpacity = value)
        "edgeFlowDuration" -> copy(edgeFlowDuration = value)
        "edgeBreathDuration" -> copy(edgeBreathDuration = value)
        "edgeBreathStrength" -> copy(edgeBreathStrength = value)
        else -> this
    }

    fun valueOf(key: String): Float = when (key) {
        "p0x" -> p0x; "p0y" -> p0y; "p1x" -> p1x; "p1y" -> p1y
        "p2x" -> p2x; "p2y" -> p2y; "p3x" -> p3x; "p3y" -> p3y
        "p4x" -> p4x; "p4y" -> p4y; "p5x" -> p5x; "p5y" -> p5y
        "p6x" -> p6x; "p6y" -> p6y; "tension" -> tension; "size" -> size
        "scaleX" -> scaleX; "scaleY" -> scaleY; "rotation" -> rotation
        "offsetX" -> offsetX; "offsetY" -> offsetY; "hotspotX" -> hotspotX; "hotspotY" -> hotspotY
        "cyanOpacity" -> cyanOpacity; "whiteOpacity" -> whiteOpacity; "pinkOpacity" -> pinkOpacity
        "outerRimWidth" -> outerRimWidth; "innerRimWidth" -> innerRimWidth; "rimOpacity" -> rimOpacity
        "glowBlur" -> glowBlur; "glowOpacity" -> glowOpacity; "auraSize" -> auraSize
        "auraBlur" -> auraBlur; "auraOpacity" -> auraOpacity; "cyanX" -> cyanX; "cyanY" -> cyanY
        "cyanSizeX" -> cyanSizeX; "cyanSizeY" -> cyanSizeY; "whiteX" -> whiteX; "whiteY" -> whiteY
        "whiteSizeX" -> whiteSizeX; "whiteSizeY" -> whiteSizeY; "pinkX" -> pinkX; "pinkY" -> pinkY
        "pinkSizeX" -> pinkSizeX; "pinkSizeY" -> pinkSizeY; "innerGlowX" -> innerGlowX
        "innerGlowY" -> innerGlowY; "innerGlowRx" -> innerGlowRx; "innerGlowRy" -> innerGlowRy
        "innerGlowOpacity" -> innerGlowOpacity; "innerGlowBlur" -> innerGlowBlur
        "edgeInset" -> edgeInset; "edgeRadius" -> edgeRadius; "edgeHaloWidth" -> edgeHaloWidth
        "edgeHaloBlur" -> edgeHaloBlur; "edgeHaloOpacity" -> edgeHaloOpacity; "edgeCastDepth" -> edgeCastDepth
        "edgeCastBlur" -> edgeCastBlur; "edgeCastOpacity" -> edgeCastOpacity
        "edgeFlowDuration" -> edgeFlowDuration; "edgeBreathDuration" -> edgeBreathDuration
        "edgeBreathStrength" -> edgeBreathStrength
        else -> 0f
    }

    fun toJson(): JSONObject = JSONObject().apply {
        ALL_KEYS.forEach { put(it, valueOf(it).toDouble()) }
    }

    companion object {
        val ALL_KEYS = listOf(
            "p0x","p0y","p1x","p1y","p2x","p2y","p3x","p3y","p4x","p4y","p5x","p5y","p6x","p6y",
            "tension","size","scaleX","scaleY","rotation","offsetX","offsetY","hotspotX","hotspotY",
            "cyanOpacity","whiteOpacity","pinkOpacity","outerRimWidth","innerRimWidth","rimOpacity","glowBlur","glowOpacity",
            "auraSize","auraBlur","auraOpacity","cyanX","cyanY","cyanSizeX","cyanSizeY","whiteX","whiteY","whiteSizeX",
            "whiteSizeY","pinkX","pinkY","pinkSizeX","pinkSizeY","innerGlowX","innerGlowY","innerGlowRx","innerGlowRy",
            "innerGlowOpacity","innerGlowBlur","edgeInset","edgeRadius","edgeHaloWidth","edgeHaloBlur","edgeHaloOpacity",
            "edgeCastDepth","edgeCastBlur","edgeCastOpacity","edgeFlowDuration","edgeBreathDuration","edgeBreathStrength"
        )

        fun fromJson(raw: String?): VisualAgentHudParameters {
            if (raw.isNullOrBlank()) return VisualAgentHudParameters()
            return runCatching {
                val json = JSONObject(raw)
                var result = VisualAgentHudParameters()
                ALL_KEYS.forEach { key ->
                    if (json.has(key)) result = result.withValue(key, json.optDouble(key, result.valueOf(key).toDouble()).toFloat())
                }
                result
            }.getOrDefault(VisualAgentHudParameters())
        }
    }
}

data class VisualAgentHudTuningState(
    val parameters: VisualAgentHudParameters = VisualAgentHudParameters(),
    val previewEnabled: Boolean = false,
    val previewGeneration: Long = 0L,
)

class VisualAgentHudTuningStore private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(
        VisualAgentHudTuningState(
            parameters = VisualAgentHudParameters.fromJson(preferences.getString(KEY_PARAMETERS, null))
        )
    )
    val state: StateFlow<VisualAgentHudTuningState> = mutableState.asStateFlow()

    fun setParameter(key: String, value: Float) {
        val next = mutableState.value.parameters.withValue(key, value)
        if (next == mutableState.value.parameters) return
        mutableState.value = mutableState.value.copy(parameters = next)
        preferences.edit().putString(KEY_PARAMETERS, next.toJson().toString()).apply()
    }

    fun setPreviewEnabled(enabled: Boolean) {
        val current = mutableState.value
        if (current.previewEnabled == enabled) return
        mutableState.value = current.copy(
            previewEnabled = enabled,
            previewGeneration = if (enabled) current.previewGeneration + 1L else current.previewGeneration,
        )
    }

    fun resetParameters() {
        val defaults = VisualAgentHudParameters()
        mutableState.value = mutableState.value.copy(parameters = defaults)
        preferences.edit().remove(KEY_PARAMETERS).apply()
    }

    companion object {
        private const val PREFS_NAME = "visual_agent_hud_tuning"
        private const val KEY_PARAMETERS = "parameters_json"
        @Volatile private var instance: VisualAgentHudTuningStore? = null

        fun get(context: Context): VisualAgentHudTuningStore = instance ?: synchronized(this) {
            instance ?: VisualAgentHudTuningStore(context).also { instance = it }
        }
    }
}

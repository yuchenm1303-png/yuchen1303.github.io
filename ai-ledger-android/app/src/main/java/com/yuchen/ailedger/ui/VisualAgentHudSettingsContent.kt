package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.AgentAccessibilityGuideActivity
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.service.AiAgentAccessibilityService
import com.yuchen.ailedger.service.VisualAgentHudTuningStore
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collect

private data class VisualHudParameterSpec(
    val key: String,
    val title: String,
    val description: String,
    val range: ClosedFloatingPointRange<Float>,
    val unit: String = "",
    val decimals: Int = 2,
)

private data class VisualHudParameterSection(
    val title: String,
    val specs: List<VisualHudParameterSpec>,
)

private val visualHudParameterSections = listOf(
    VisualHudParameterSection(
        "光标轮廓控制点",
        listOf(
            VisualHudParameterSpec("p0x", "P0 X", "光标起点横坐标。", 0f..64f),
            VisualHudParameterSpec("p0y", "P0 Y", "光标起点纵坐标。", 0f..64f),
            VisualHudParameterSpec("p1x", "P1 X", "轮廓控制点 1 横坐标。", 0f..64f),
            VisualHudParameterSpec("p1y", "P1 Y", "轮廓控制点 1 纵坐标。", 0f..64f),
            VisualHudParameterSpec("p2x", "P2 X", "轮廓控制点 2 横坐标。", 0f..64f),
            VisualHudParameterSpec("p2y", "P2 Y", "轮廓控制点 2 纵坐标。", 0f..64f),
            VisualHudParameterSpec("p3x", "P3 X", "轮廓控制点 3 横坐标。", 0f..64f),
            VisualHudParameterSpec("p3y", "P3 Y", "轮廓控制点 3 纵坐标。", 0f..64f),
            VisualHudParameterSpec("p4x", "P4 X", "轮廓控制点 4 横坐标。", 0f..64f),
            VisualHudParameterSpec("p4y", "P4 Y", "轮廓控制点 4 纵坐标。", 0f..64f),
            VisualHudParameterSpec("p5x", "P5 X", "轮廓控制点 5 横坐标。", 0f..64f),
            VisualHudParameterSpec("p5y", "P5 Y", "轮廓控制点 5 纵坐标。", 0f..64f),
            VisualHudParameterSpec("p6x", "P6 X", "轮廓控制点 6 横坐标。", 0f..64f),
            VisualHudParameterSpec("p6y", "P6 Y", "轮廓控制点 6 纵坐标。", 0f..64f),
            VisualHudParameterSpec("tension", "曲线张力", "闭合 Cardinal 曲线的张力。", 0f..1.5f),
        )
    ),
    VisualHudParameterSection(
        "光标几何与热点",
        listOf(
            VisualHudParameterSpec("size", "光标尺寸", "SVG 光标显示尺寸，默认 36.1 px。", 24f..96f, " px", 1),
            VisualHudParameterSpec("scaleX", "横向缩放", "光标横向比例。", 0.5f..1.5f),
            VisualHudParameterSpec("scaleY", "纵向缩放", "光标纵向比例。", 0.5f..1.5f),
            VisualHudParameterSpec("rotation", "旋转角度", "围绕热点旋转光标。", -30f..30f, "°", 1),
            VisualHudParameterSpec("offsetX", "横向偏移", "光标图形相对热点的横向偏移。", -20f..20f, " px", 1),
            VisualHudParameterSpec("offsetY", "纵向偏移", "光标图形相对热点的纵向偏移。", -20f..20f, " px", 1),
            VisualHudParameterSpec("hotspotX", "热点 X", "实际点击点在 SVG 坐标中的横坐标。", 0f..64f, " px", 1),
            VisualHudParameterSpec("hotspotY", "热点 Y", "实际点击点在 SVG 坐标中的纵坐标。", 0f..64f, " px", 1),
        )
    ),
    VisualHudParameterSection(
        "光标旁信息栏",
        listOf(
            VisualHudParameterSpec("infoBubbleWidth", "信息栏宽度", "光标旁动作信息栏的基础宽度。", 180f..420f, " px", 1),
            VisualHudParameterSpec("infoBubbleScale", "信息栏整体大小", "统一缩放信息栏、文字和内部间距。", 0.5f..1.5f, "×"),
        )
    ),
    VisualHudParameterSection(
        "光标材质与描边",
        listOf(
            VisualHudParameterSpec("cyanOpacity", "青色层透明度", "青色椭圆渐变的总透明度。", 0f..1f),
            VisualHudParameterSpec("whiteOpacity", "白色层透明度", "中央白色高光的总透明度。", 0f..1f),
            VisualHudParameterSpec("pinkOpacity", "粉色层透明度", "尾部粉紫渐变的总透明度。", 0f..1f),
            VisualHudParameterSpec("outerRimWidth", "外描边宽度", "外侧彩色轮廓宽度。", 0f..4f, " px"),
            VisualHudParameterSpec("innerRimWidth", "内描边宽度", "内侧白色轮廓宽度。", 0f..3f, " px"),
            VisualHudParameterSpec("rimOpacity", "描边透明度", "外描边和内描边整体可见度。", 0f..1f),
            VisualHudParameterSpec("glowBlur", "外发光模糊", "SVG 外发光高斯模糊半径。", 0f..12f, " px"),
            VisualHudParameterSpec("glowOpacity", "外发光透明度", "青色外发光的能量。", 0f..1f),
            VisualHudParameterSpec("auraSize", "光标 Aura 尺寸", "光标外围柔光的尺寸。", 0f..160f, " px", 1),
            VisualHudParameterSpec("auraBlur", "光标 Aura 模糊", "光标外围柔光模糊。", 0f..30f, " px", 1),
            VisualHudParameterSpec("auraOpacity", "光标 Aura 透明度", "光标外围柔光可见度。", 0f..1f),
        )
    ),
    VisualHudParameterSection(
        "青色渐变",
        listOf(
            VisualHudParameterSpec("cyanX", "青色中心 X", "青色椭圆中心横坐标。", 0f..64f),
            VisualHudParameterSpec("cyanY", "青色中心 Y", "青色椭圆中心纵坐标。", 0f..64f),
            VisualHudParameterSpec("cyanSizeX", "青色横向尺寸", "青色椭圆横向半径。", 1f..64f),
            VisualHudParameterSpec("cyanSizeY", "青色纵向尺寸", "青色椭圆纵向半径。", 1f..64f),
        )
    ),
    VisualHudParameterSection(
        "白色渐变",
        listOf(
            VisualHudParameterSpec("whiteX", "白色中心 X", "白色椭圆中心横坐标。", 0f..64f),
            VisualHudParameterSpec("whiteY", "白色中心 Y", "白色椭圆中心纵坐标。", 0f..64f),
            VisualHudParameterSpec("whiteSizeX", "白色横向尺寸", "白色椭圆横向半径。", 1f..64f),
            VisualHudParameterSpec("whiteSizeY", "白色纵向尺寸", "白色椭圆纵向半径。", 1f..64f),
        )
    ),
    VisualHudParameterSection(
        "粉色渐变",
        listOf(
            VisualHudParameterSpec("pinkX", "粉色中心 X", "粉色椭圆中心横坐标。", 0f..64f),
            VisualHudParameterSpec("pinkY", "粉色中心 Y", "粉色椭圆中心纵坐标。", 0f..64f),
            VisualHudParameterSpec("pinkSizeX", "粉色横向尺寸", "粉色椭圆横向半径。", 1f..64f),
            VisualHudParameterSpec("pinkSizeY", "粉色纵向尺寸", "粉色椭圆纵向半径。", 1f..64f),
        )
    ),
    VisualHudParameterSection(
        "内部高光",
        listOf(
            VisualHudParameterSpec("innerGlowX", "内部高光 X", "内部椭圆高光中心横坐标。", 0f..64f),
            VisualHudParameterSpec("innerGlowY", "内部高光 Y", "内部椭圆高光中心纵坐标。", 0f..64f),
            VisualHudParameterSpec("innerGlowRx", "内部高光横向半径", "内部高光椭圆横向半径。", 1f..40f),
            VisualHudParameterSpec("innerGlowRy", "内部高光纵向半径", "内部高光椭圆纵向半径。", 1f..40f),
            VisualHudParameterSpec("innerGlowOpacity", "内部高光透明度", "内部柔光可见度。", 0f..1f),
            VisualHudParameterSpec("innerGlowBlur", "内部高光模糊", "内部高光高斯模糊半径。", 0f..12f, " px"),
        )
    ),
    VisualHudParameterSection(
        "边缘光带",
        listOf(
            VisualHudParameterSpec("edgeInset", "边缘内缩", "边缘光层距离屏幕边界的内缩。", 0f..32f, " px", 1),
            VisualHudParameterSpec("edgeRadius", "边缘圆角", "边缘光带圆角半径。", 0f..64f, " px", 1),
            VisualHudParameterSpec("edgeHaloWidth", "近场光带宽度", "贴近屏幕边缘的模糊光带深度。", 0f..160f, " px", 1),
            VisualHudParameterSpec("edgeHaloBlur", "近场光带模糊", "近场光带滤镜模糊。", 0f..40f, " px", 1),
            VisualHudParameterSpec("edgeHaloOpacity", "近场光带强度", "近场光带整体透明度。", 0f..1f),
            VisualHudParameterSpec("edgeCastDepth", "内投射深度", "彩色边缘向屏幕内部投射的距离。", 0f..240f, " px", 1),
            VisualHudParameterSpec("edgeCastBlur", "内投射模糊", "内投射层滤镜模糊。", 0f..40f, " px", 1),
            VisualHudParameterSpec("edgeCastOpacity", "内投射强度", "内投射层整体透明度。", 0f..1f),
            VisualHudParameterSpec("edgeFlowDuration", "流动周期", "彩色光带完整环绕一周的时间。", 1f..30f, " s"),
            VisualHudParameterSpec("edgeBreathDuration", "呼吸周期", "边缘光完成一次呼吸的时间。", 0.5f..10f, " s"),
            VisualHudParameterSpec("edgeBreathStrength", "呼吸幅度", "边缘光明暗呼吸的幅度。", 0f..1f),
        )
    ),
)

@Composable
internal fun VisualAgentHudSettingsContent(state: AssistantUiState) {
    val context = LocalContext.current
    val store = remember(context) { VisualAgentHudTuningStore.get(context.applicationContext) }
    val initialTuning = remember(store) { store.state.value }
    var previewEnabled by remember(store) { mutableStateOf(initialTuning.previewEnabled) }
    val parameterValues = remember(store) {
        mutableStateMapOf<String, Float>().apply {
            visualHudParameterSections.forEach { section ->
                section.specs.forEach { spec ->
                    put(spec.key, initialTuning.parameters.valueOf(spec.key))
                }
            }
        }
    }

    LaunchedEffect(store) {
        store.state.collect { tuning ->
            previewEnabled = tuning.previewEnabled
            visualHudParameterSections.forEach { section ->
                section.specs.forEach { spec ->
                    val nextValue = tuning.parameters.valueOf(spec.key)
                    if (parameterValues[spec.key] != nextValue) {
                        parameterValues[spec.key] = nextValue
                    }
                }
            }
        }
    }

    fun updatePreview(enabled: Boolean) {
        if (enabled && !AiAgentAccessibilityService.isConnected()) {
            store.setPreviewEnabled(false)
            AgentAccessibilityGuideActivity.open(context)
            return
        }
        store.setPreviewEnabled(enabled)
    }

    DisposableEffect(store) {
        onDispose { store.setPreviewEnabled(false) }
    }

    VisualIntelligenceDiagnosticsSettingsContent(state)

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "样本预览",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "由无障碍服务在整机顶层显示真实 HUD，无需额外开启悬浮窗权限。",
                color = Color.White.copy(alpha = 0.56f),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
        Switch(
            checked = previewEnabled,
            onCheckedChange = ::updatePreview,
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        VisualHudActionButton(
            title = "恢复默认参数",
            subtitle = "光标 36.1 px · 信息栏 420 / 0.65",
            state = state,
            modifier = Modifier.weight(1f),
            onClick = store::resetParameters,
        )
        VisualHudActionButton(
            title = if (previewEnabled) "关闭样本" else "打开样本",
            subtitle = if (previewEnabled) "停止顶层预览" else "实时查看调整结果",
            state = state,
            modifier = Modifier.weight(1f),
            onClick = { updatePreview(!previewEnabled) },
        )
    }

    visualHudParameterSections.forEach { section ->
        Text(
            section.title,
            color = Color.White.copy(alpha = 0.84f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 4.dp)
        )
        section.specs.forEach { spec ->
            VisualHudParameterSlider(
                spec = spec,
                values = parameterValues,
                store = store,
            )
        }
    }
}

@Composable
private fun VisualHudParameterSlider(
    spec: VisualHudParameterSpec,
    values: SnapshotStateMap<String, Float>,
    store: VisualAgentHudTuningStore,
) {
    val value = values[spec.key] ?: store.state.value.parameters.valueOf(spec.key)
    InsetGlassParameterSlider(
        title = spec.title,
        description = spec.description,
        value = value,
        valueRange = spec.range,
        onValueChange = { store.setParameter(spec.key, it) },
        valueText = formatVisualHudValue(value, spec.decimals) + spec.unit,
    )
}

@Composable
private fun VisualHudActionButton(
    title: String,
    subtitle: String,
    state: AssistantUiState,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 23,
        modifier = modifier.height(60.dp),
        role = GlassRole.Chip,
        onClick = onClick,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(23.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = Color.White.copy(alpha = 0.50f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatVisualHudValue(value: Float, decimals: Int): String {
    val scale = when (decimals) { 0 -> 1f; 1 -> 10f; else -> 100f }
    val rounded = (value * scale).roundToInt() / scale
    return when (decimals) {
        0 -> rounded.roundToInt().toString()
        1 -> String.format(java.util.Locale.US, "%.1f", rounded)
        else -> String.format(java.util.Locale.US, "%.2f", rounded)
    }
}

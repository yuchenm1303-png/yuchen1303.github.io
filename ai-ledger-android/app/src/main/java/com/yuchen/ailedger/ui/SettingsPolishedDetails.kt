package com.yuchen.ailedger.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.RainbowPrismStyle
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.service.InlineStickerDiagnosticsStore
import kotlin.math.roundToInt

@Composable
internal fun SettingsDetailPanel(
    panel: SettingsDetailSection,
    state: AssistantUiState,
    aiEndpoint: String,
    @Suppress("UNUSED_PARAMETER") onQualityChange: (RenderQuality) -> Unit,
    @Suppress("UNUSED_PARAMETER") onPreviewConversationChange: (Boolean) -> Unit,
    @Suppress("UNUSED_PARAMETER") onGlassPresetChange: (GlassPreset) -> Unit,
    onBackgroundThemeChange: (BackgroundTheme) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit,
    onRainbowPrismChange: (RainbowPrismStyle) -> Unit,
    onBackdropChange: (BackdropDebugParams) -> Unit,
    onBorderChange: (GlassBorderStyle) -> Unit,
    onUploadBackgroundClick: () -> Unit,
    onClearCustomBackgroundClick: () -> Unit,
) {
    SettingsGlassFrame(state = state, radius = 28) {
        AnimatedContent(
            targetState = panel,
            transitionSpec = {
                val direction = if (targetState.settingsOrder() >= initialState.settingsOrder()) 1 else -1
                fadeIn(
                    animationSpec = tween(
                        170,
                        delayMillis = 42,
                        easing = FastOutSlowInEasing,
                    )
                ) + slideInVertically(
                    animationSpec = tween(310, easing = FastOutSlowInEasing)
                ) { 46 * direction } + scaleIn(
                    initialScale = 0.955f,
                    animationSpec = tween(310, easing = FastOutSlowInEasing),
                ) togetherWith fadeOut(
                    animationSpec = tween(135, easing = FastOutSlowInEasing)
                ) + slideOutVertically(
                    animationSpec = tween(170, easing = FastOutSlowInEasing)
                ) { -30 * direction } + scaleOut(
                    targetScale = 0.982f,
                    animationSpec = tween(170, easing = FastOutSlowInEasing),
                )
            },
            label = "settings-detail-panel-switch",
        ) { activePanel ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DetailHeader(panelTitle(activePanel), panelSubtitle(activePanel))
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    when (activePanel) {
                        SettingsDetailSection.Appearance -> AppearanceContent(
                            state,
                            onBackgroundThemeChange,
                            onUploadBackgroundClick,
                            onClearCustomBackgroundClick,
                        )

                        SettingsDetailSection.Glass -> GlassContent(
                            state,
                            onGlassIntensityChange,
                            onMotionIntensityChange,
                            onRainbowPrismChange,
                            onBackdropChange,
                            onBorderChange,
                        )

                        SettingsDetailSection.Assistant -> VisualAgentHudSettingsContent(state)
                        SettingsDetailSection.Data -> DataContent(state)
                        SettingsDetailSection.Service -> ServiceContent(state, aiEndpoint)
                        SettingsDetailSection.Advanced -> AdvancedContent()
                        SettingsDetailSection.Chat -> ChatPageSettingsContent(state)
                        SettingsDetailSection.Memory -> AccountMemorySettingsContent(state)
                        SettingsDetailSection.Debug -> GlassDebugFloatingPanel(
                            state,
                            onBackdropChange,
                            onBorderChange,
                            onUploadBackgroundClick,
                            onClearCustomBackgroundClick,
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGlassFrame(
    state: AssistantUiState,
    modifier: Modifier = Modifier,
    radius: Int = 28,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(radius.dp)
    Box(modifier.fillMaxWidth().clip(shape)) {
        GlassPanel(
            quality = state.quality,
            glassIntensity = state.glassIntensity,
            motionIntensity = state.motionIntensity,
            radius = radius,
            modifier = Modifier.matchParentSize(),
            role = GlassRole.Card,
        ) {}
        Box(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun DetailHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            title,
            color = Color.White,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AppearanceContent(
    state: AssistantUiState,
    onBackgroundThemeChange: (BackgroundTheme) -> Unit,
    onUploadBackgroundClick: () -> Unit,
    onClearCustomBackgroundClick: () -> Unit,
) {
    SettingChipGrid(
        BackgroundTheme.entries,
        state.backgroundTheme,
        { themeLabel(it) },
        state,
        onBackgroundThemeChange,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SettingActionButton(
            "上传背景",
            if (state.customBackgroundPath == null) "选择图片" else "已自定义",
            state,
            Modifier.weight(1f),
            onUploadBackgroundClick,
        )
        SettingActionButton(
            "清除背景",
            "恢复主题",
            state,
            Modifier.weight(1f),
            onClearCustomBackgroundClick,
        )
    }
}

@Composable
private fun GlassContent(
    state: AssistantUiState,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit,
    onRainbowPrismChange: (RainbowPrismStyle) -> Unit,
    onBackdropChange: (BackdropDebugParams) -> Unit,
    onBorderChange: (GlassBorderStyle) -> Unit,
) {
    val prism = state.rainbowPrismStyle
    val backdrop = state.backdropParams
    val border = state.glassBorderStyle

    GlassDebugParameterSections(
        state = state,
        prism = prism,
        backdrop = backdrop,
        border = border,
        onGlassIntensityChange = onGlassIntensityChange,
        onMotionIntensityChange = onMotionIntensityChange,
        onRainbowPrismChange = onRainbowPrismChange,
        onBackdropChange = onBackdropChange,
        onBorderChange = onBorderChange,
    )

    SettingsParameterGroup(title = "玻璃基础", subtitle = "通用玻璃材质与动画幅度") {
        SliderSettingRow("玻璃强度", "控制通用玻璃的可见度、雾感和边缘能量。", state.glassIntensity, 0.6f..1.4f, onGlassIntensityChange)
        SliderSettingRow("动态强度", "控制呼吸、扫光和形变动画幅度，0 为静态。", state.motionIntensity, 0f..1.4f, onMotionIntensityChange)
    }

    SettingsParameterGroup(title = "彩虹镀膜", subtitle = "聊天大玻璃边缘与外缘彩虹能量") {
        SliderSettingRow("整体彩虹强度", "统一调节聊天大玻璃彩虹镀膜的总能量。", prism.overall, 0f..2f) { onRainbowPrismChange(prism.copy(overall = it)) }
        SliderSettingRow("棱彩边缘高光", "增强圆角和玻璃边缘对彩色入射光的捕获。", prism.edgeHighlight, 0f..2f) { onRainbowPrismChange(prism.copy(edgeHighlight = it)) }
        SliderSettingRow("粉金青蓝彩虹光晕", "调节粉、金、青、蓝在玻璃外缘形成的柔和光晕。", prism.rainbowHalo, 0f..2f) { onRainbowPrismChange(prism.copy(rainbowHalo = it)) }
    }

    SettingsParameterGroup(title = "随机渐变扫光", subtitle = "聊天大玻璃随机扫光亮度区间") {
        SliderSettingRow("扫光强度下限", "随机扫光每次出现时允许的最低亮度。", prism.sweepMin, 0f..2f) { onRainbowPrismChange(prism.copy(sweepMin = it)) }
        SliderSettingRow("扫光强度上限", "随机扫光每次出现时允许的最高亮度。", prism.sweepMax, 0f..2f) { onRainbowPrismChange(prism.copy(sweepMax = it)) }
    }

    SettingsParameterGroup(title = "背景模糊金字塔", subtitle = "单一背景源的清晰、低、中、高四级采样") {
        SettingsParameterSlider("缓存分辨率", "调节背景模糊缓存的有效分辨率；范围与运行时安全边界完全一致。", backdrop.scale.coerceIn(0.28f, 0.72f), 0.28f..0.72f, { "${it.settingsRoundedValue()}×" }) { onBackdropChange(backdrop.copy(scale = it)) }
        SettingsParameterSlider("模糊层级", "0=清晰，1=低，2=中，4=高；中间值连续插值。", backdrop.radius, 0f..4f, { "${it.settingsRoundedValue()} 级" }) { onBackdropChange(backdrop.copy(radius = it)) }
        SettingsParameterSlider("模糊迭代", "0 跳过全部模糊 pass；1–12 控制低、中、高缓存生成轮数。", backdrop.iterations, 0f..12f, { "${it.roundToInt()} 次" }) { onBackdropChange(backdrop.copy(iterations = it.roundToInt().toFloat())) }
    }

    SettingsParameterGroup(title = "背景色彩输出", subtitle = "模糊缓存生成后的明暗与色彩") {
        SettingsParameterSlider("背景亮度", "调节玻璃采样背景的整体明暗。", backdrop.brightness, 0.4f..2.2f) { onBackdropChange(backdrop.copy(brightness = it)) }
        SettingsParameterSlider("背景对比度", "调节玻璃采样背景的明暗反差。", backdrop.contrast, 0.5f..1.8f) { onBackdropChange(backdrop.copy(contrast = it)) }
        SettingsParameterSlider("背景饱和度", "调节玻璃采样背景的综合色彩浓度；范围与纹理生成器一致。", backdrop.saturation.coerceIn(0.3f, 1.8f), 0.3f..1.8f) { onBackdropChange(backdrop.copy(saturation = it)) }
    }

    SettingsParameterGroup(title = "上传图片亮度保护", subtitle = "只在参数稳定后重建一次自定义背景缓存") {
        SettingsParameterSlider("上传图亮度", "只调节用户上传原图的基础亮度；内置主题和默认壁纸不受影响。", backdrop.customImageBrightness, 0.50f..1.10f) { onBackdropChange(backdrop.copy(customImageBrightness = it)) }
        SettingsParameterSlider("高光压缩起点", "图片亮度超过该位置后开始柔和压缩，暗部和中间调尽量保持原样。", backdrop.customImageHighlightStart, 0.35f..0.85f, { "${(it * 100f).roundToInt()}%" }) {
            val start = it
            val limit = maxOf(backdrop.customImageHighlightLimit, start + 0.02f).coerceAtMost(0.92f)
            onBackdropChange(backdrop.copy(customImageHighlightStart = start, customImageHighlightLimit = limit))
        }
        SettingsParameterSlider("亮度输出上限", "限制上传图片最亮区域的最终亮度，避免白色背景冲淡玻璃上的文字。", backdrop.customImageHighlightLimit, 0.50f..0.92f, { "${(it * 100f).roundToInt()}%" }) {
            val limit = it
            val start = minOf(backdrop.customImageHighlightStart, limit - 0.02f).coerceAtLeast(0.35f)
            onBackdropChange(backdrop.copy(customImageHighlightStart = start, customImageHighlightLimit = limit))
        }
    }

    SettingsParameterGroup(title = "背景云雾层", subtitle = "内置主题的云层形态与高光") {
        SettingsParameterSlider("云雾透明度", "调节内置主题背景云雾层的整体可见度。", backdrop.cloudAlpha, 0f..2f) { onBackdropChange(backdrop.copy(cloudAlpha = it)) }
        SettingsParameterSlider("云雾柔化", "调节云层边缘的扩散与柔和程度。", backdrop.cloudSoftness, 0f..3f) { onBackdropChange(backdrop.copy(cloudSoftness = it)) }
        SettingsParameterSlider("云层横向拉伸", "调节云雾层在水平方向的铺展范围。", backdrop.cloudStretchX, 0.4f..4f) { onBackdropChange(backdrop.copy(cloudStretchX = it)) }
        SettingsParameterSlider("云层纵向拉伸", "调节云雾层在垂直方向的厚度。", backdrop.cloudStretchY, 0.2f..2f) { onBackdropChange(backdrop.copy(cloudStretchY = it)) }
        SettingsParameterSlider("云层高光", "调节云雾亮部的局部高光透明度。", backdrop.cloudHighlightAlpha, 0f..1f) { onBackdropChange(backdrop.copy(cloudHighlightAlpha = it)) }
    }

    SettingsParameterGroup(title = "背景月亮层", subtitle = "内置主题的月体、光晕与边缘") {
        SettingsParameterSlider("月亮尺寸", "调节内置主题月体的整体尺寸。", backdrop.moonScale, 0.5f..1.8f) { onBackdropChange(backdrop.copy(moonScale = it)) }
        SettingsParameterSlider("月亮光晕", "调节月体周围柔和光晕的透明度。", backdrop.moonHaloAlpha, 0f..1f) { onBackdropChange(backdrop.copy(moonHaloAlpha = it)) }
        SettingsParameterSlider("月亮边缘光", "调节月体轮廓边缘的亮度。", backdrop.moonRimAlpha, 0f..1.2f) { onBackdropChange(backdrop.copy(moonRimAlpha = it)) }
    }
}

@Composable
private fun GlassDebugParameterSections(
    state: AssistantUiState,
    prism: RainbowPrismStyle,
    backdrop: BackdropDebugParams,
    border: GlassBorderStyle,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit,
    onRainbowPrismChange: (RainbowPrismStyle) -> Unit,
    onBackdropChange: (BackdropDebugParams) -> Unit,
    onBorderChange: (GlassBorderStyle) -> Unit,
) {
    SettingsParameterGroup(title = "调试｜全局玻璃与动效", subtitle = "所有玻璃共用入口；默认折叠，适合先做总量级校准。") {
        SettingsParameterSlider("全局玻璃强度", "调试全局玻璃可见度入口；运行时持久层仍会保护正式范围。", state.glassIntensity, 0f..4f) { onGlassIntensityChange(it) }
        SettingsParameterSlider("全局动态强度", "调试呼吸、扫光、按压和流体形变的总入口；0 为完全静态。", state.motionIntensity, 0f..4f) { onMotionIntensityChange(it) }
    }

    SettingsParameterGroup(title = "调试｜彩虹镀膜与随机扫光", subtitle = "彩虹边缘、棱彩高光、随机扫光和外缘彩色光晕。") {
        SettingsParameterSlider("彩虹总强度", "控制彩虹镀膜整体能量，用于快速判断边缘光是否过强或过弱。", prism.overall, 0f..6f) { onRainbowPrismChange(prism.copy(overall = it)) }
        SettingsParameterSlider("棱彩边缘高光", "放大圆角、边缘和肩部捕获彩色入射光的能力。", prism.edgeHighlight, 0f..6f) { onRainbowPrismChange(prism.copy(edgeHighlight = it)) }
        SettingsParameterSlider("随机扫光下限", "随机扫光每次出现时允许的最低亮度，大范围便于测试弱扫光。", prism.sweepMin, 0f..6f) { onRainbowPrismChange(prism.copy(sweepMin = it)) }
        SettingsParameterSlider("随机扫光上限", "随机扫光每次出现时允许的最高亮度，大范围便于测试强扫光。", prism.sweepMax, 0f..8f) { onRainbowPrismChange(prism.copy(sweepMax = it)) }
        SettingsParameterSlider("粉金青蓝光晕", "控制玻璃外缘粉、金、青、蓝彩色雾化光晕。", prism.rainbowHalo, 0f..6f) { onRainbowPrismChange(prism.copy(rainbowHalo = it)) }
    }

    SettingsParameterGroup(title = "调试｜OpenGL Shell 主体与折射", subtitle = "只对应大型 Shell 玻璃；调主体显影、亮度、镜片折射和中心厚度。") {
        SettingsParameterSlider("OpenGL 主体强度", "新版 OpenGL Shell 的主渲染强度，影响主体折射、圆肩和色散混合。", border.newOpenGlGlassIntensity, 0f..4f) { onBorderChange(border.copy(newOpenGlGlassIntensity = it)) }
        SettingsParameterSlider("OpenGL 主体可见度", "主体区域显影门槛，极大范围用于判断玻璃是否被压暗或过曝。", border.newOpenGlBodyVisibility, 0f..80f) { onBorderChange(border.copy(newOpenGlBodyVisibility = it)) }
        SettingsParameterSlider("OpenGL 主体最大透明", "限制主体输出 alpha 的上限，可测试厚玻璃与轻玻璃边界。", border.newOpenGlBodyMaxAlpha, 0f..4f) { onBorderChange(border.copy(newOpenGlBodyMaxAlpha = it)) }
        SettingsParameterSlider("OpenGL 输出亮度", "控制主体采样后的输出亮度，用于排查玻璃发灰或发白。", border.newOpenGlBodyOutputBrightness, 0f..6f) { onBorderChange(border.copy(newOpenGlBodyOutputBrightness = it)) }
        SettingsParameterSlider("主体基础折射", "新版 OpenGL 主体中心区域的基础折射拉力。", border.newOpenGlBodyLensBasePull, -1200f..1200f) { onBorderChange(border.copy(newOpenGlBodyLensBasePull = it)) }
        SettingsParameterSlider("主体折射距离", "主体镜片向外采样的距离，正负大范围用于测试反向折射。", border.newOpenGlBodyLensPullDp, -1600f..1600f, { "${it.roundToInt()} dp" }) { onBorderChange(border.copy(newOpenGlBodyLensPullDp = it)) }
        SettingsParameterSlider("主体折射集中度", "控制主体折射场从中心到边缘的聚合程度。", border.newOpenGlBodyLensConcentration, 0f..40f) { onBorderChange(border.copy(newOpenGlBodyLensConcentration = it)) }
        SettingsParameterSlider("主体额外采样距离", "为主体镜片增加额外采样半径，便于观察背景位移边界。", border.newOpenGlBodyLensExtraDistance, -800f..1200f) { onBorderChange(border.copy(newOpenGlBodyLensExtraDistance = it)) }
        SettingsParameterSlider("主体折射触达", "控制主体折射场在 dp 空间内的触达范围。", border.newOpenGlBodyLensReachDp, 0f..800f, { "${it.roundToInt()} dp" }) { onBorderChange(border.copy(newOpenGlBodyLensReachDp = it)) }
        SettingsParameterSlider("主体暗部提取", "控制主体折射后的暗部压制和暗边可见度。", border.newOpenGlBodyLensDark, -4f..8f) { onBorderChange(border.copy(newOpenGlBodyLensDark = it)) }
        SettingsParameterSlider("主体宽度", "调试主体玻璃中间带宽度，影响厚度和空间感。", border.newOpenGlBodyWidth, 0f..6f) { onBorderChange(border.copy(newOpenGlBodyWidth = it)) }
        SettingsParameterSlider("主体曲线", "控制主体折射曲线形状，低值硬、高值软。", border.newOpenGlBodyCurve, 0f..4f) { onBorderChange(border.copy(newOpenGlBodyCurve = it)) }
        SettingsParameterSlider("主体增益", "主体玻璃能量增益，大范围用于找厚玻璃临界点。", border.newOpenGlBodyGain, 0f..80f) { onBorderChange(border.copy(newOpenGlBodyGain = it)) }
        SettingsParameterSlider("主体亮度混合", "新版 OpenGL 主体亮度混入比例。", border.newOpenGlBrightness, -2f..4f) { onBorderChange(border.copy(newOpenGlBrightness = it)) }
        SettingsParameterSlider("主体光带位置", "调试主体玻璃内部亮带的位置。", border.newOpenGlBodyBandPos, -2f..3f) { onBorderChange(border.copy(newOpenGlBodyBandPos = it)) }
        SettingsParameterSlider("主体光带宽度", "调试主体内部亮带宽度，过大时会变成整体泛白。", border.newOpenGlBodyBandWidth, 0f..2f) { onBorderChange(border.copy(newOpenGlBodyBandWidth = it)) }
        SettingsParameterSlider("主体光带增益", "主体内部亮带的亮度增益。", border.newOpenGlBodyBandGain, 0f..160f) { onBorderChange(border.copy(newOpenGlBodyBandGain = it)) }
    }

    SettingsParameterGroup(title = "调试｜OpenGL Shell 圆肩与色散", subtitle = "只对应大型 Shell 玻璃；调圆肩捕光、肩部材质、切向流动和 RGB 色散。") {
        SettingsParameterSlider("圆肩宽度", "玻璃肩部高光宽度，决定边缘厚度。", border.newOpenGlShoulderWidthDp, 0f..180f, { "${it.roundToInt()} dp" }) { onBorderChange(border.copy(newOpenGlShoulderWidthDp = it)) }
        SettingsParameterSlider("圆肩捕获宽度", "肩部从背景中捕获颜色和光线的采样范围。", border.newOpenGlShoulderCaptureWidthDp, 0f..360f, { "${it.roundToInt()} dp" }) { onBorderChange(border.copy(newOpenGlShoulderCaptureWidthDp = it)) }
        SettingsParameterSlider("圆肩最大角度", "控制肩部折射允许的最大入射角。", border.newOpenGlShoulderMaxAngleDeg, 0f..180f, { "${it.roundToInt()}°" }) { onBorderChange(border.copy(newOpenGlShoulderMaxAngleDeg = it)) }
        SettingsParameterSlider("圆肩衰减圆润度", "调试肩部从亮到暗的过渡曲线。", border.newOpenGlShoulderFalloffRoundness, 0f..6f) { onBorderChange(border.copy(newOpenGlShoulderFalloffRoundness = it)) }
        SettingsParameterSlider("圆肩材质强度", "圆肩材质感和硬边能量的总强度。", border.newOpenGlShoulderMaterialStrength, 0f..12f) { onBorderChange(border.copy(newOpenGlShoulderMaterialStrength = it)) }
        SettingsParameterSlider("圆肩切向流动", "肩部沿边缘方向的流动/拖影强度。", border.newOpenGlShoulderTangentialFlowStrength, -4f..8f) { onBorderChange(border.copy(newOpenGlShoulderTangentialFlowStrength = it)) }
        SettingsParameterSlider("色散强度", "红绿蓝边缘分离强度，主要影响高级玻璃的棱镜感。", border.newOpenGlDispersionStrength, 0f..8f) { onBorderChange(border.copy(newOpenGlDispersionStrength = it)) }
        SettingsParameterSlider("色散距离", "色散通道采样间距，数值越大彩边越明显。", border.newOpenGlDispersionDistanceDp, 0f..40f, { "${it.settingsRoundedValue()} dp" }) { onBorderChange(border.copy(newOpenGlDispersionDistanceDp = it)) }
        SettingsParameterSlider("色散边缘宽度", "色散在边缘区域扩散的宽度。", border.newOpenGlDispersionEdgeWidthDp, 0f..220f, { "${it.roundToInt()} dp" }) { onBorderChange(border.copy(newOpenGlDispersionEdgeWidthDp = it)) }
        SettingsParameterSlider("色散集中度", "控制彩边向圆角和边缘集中的程度。", border.newOpenGlDispersionConcentration, 0f..16f) { onBorderChange(border.copy(newOpenGlDispersionConcentration = it)) }
    }

    SettingsParameterGroup(title = "调试｜OpenGL Shell 外缘与内壁", subtitle = "只对应大型 Shell 玻璃；调外缘压缩、内壁亮边、暗部抽取、清晰度和切向涂抹。") {
        SettingsParameterSlider("外缘压缩宽度", "旧/新边缘外轮廓压缩宽度，用于测试边缘厚玻璃。", border.newOpenGlOuterRimWidthPx, 0f..160f, { "${it.roundToInt()} px" }) { onBorderChange(border.copy(newOpenGlOuterRimWidthPx = it)) }
        SettingsParameterSlider("外缘压缩强度", "外缘向内压缩和凸起的强度。", border.newOpenGlOuterRimCompression, -8f..12f) { onBorderChange(border.copy(newOpenGlOuterRimCompression = it)) }
        SettingsParameterSlider("外缘触达", "外缘压缩采样触达范围。", border.newOpenGlOuterRimReachPx, 0f..480f, { "${it.roundToInt()} px" }) { onBorderChange(border.copy(newOpenGlOuterRimReachPx = it)) }
        SettingsParameterSlider("外缘增益", "外缘轮廓亮度和厚度增益。", border.newOpenGlOuterRimGain, -8f..20f) { onBorderChange(border.copy(newOpenGlOuterRimGain = it)) }
        SettingsParameterSlider("内壁偏移", "玻璃内壁相对边缘的偏移。", border.newOpenGlInnerWallOffsetPx, -120f..160f, { "${it.roundToInt()} px" }) { onBorderChange(border.copy(newOpenGlInnerWallOffsetPx = it)) }
        SettingsParameterSlider("内壁宽度", "玻璃内侧墙体/内描边宽度。", border.newOpenGlInnerWallWidthPx, 0f..180f, { "${it.roundToInt()} px" }) { onBorderChange(border.copy(newOpenGlInnerWallWidthPx = it)) }
        SettingsParameterSlider("内壁增益", "内壁亮边与暗边的能量增益。", border.newOpenGlInnerWallGain, -8f..20f) { onBorderChange(border.copy(newOpenGlInnerWallGain = it)) }
        SettingsParameterSlider("内壁衰减", "内壁从边缘向中心衰减的曲线。", border.newOpenGlInnerWallFalloff, 0f..12f) { onBorderChange(border.copy(newOpenGlInnerWallFalloff = it)) }
        SettingsParameterSlider("内壁触达", "内壁材质向玻璃内部扩散的范围。", border.newOpenGlInnerWallReachPx, 0f..480f, { "${it.roundToInt()} px" }) { onBorderChange(border.copy(newOpenGlInnerWallReachPx = it)) }
        SettingsParameterSlider("暗部抽取", "从背景中抽取暗部用于玻璃厚度和压暗。", border.newOpenGlDarkExtract, -4f..8f) { onBorderChange(border.copy(newOpenGlDarkExtract = it)) }
        SettingsParameterSlider("边肩宽度", "边缘 shoulder 的像素宽度调试项。", border.newOpenGlEdgeShoulderWidthPx, 0f..240f, { "${it.roundToInt()} px" }) { onBorderChange(border.copy(newOpenGlEdgeShoulderWidthPx = it)) }
        SettingsParameterSlider("边缘切向拖影", "边缘沿切线方向 smear/拖影强度。", border.newOpenGlEdgeTangentSmear, -4f..8f) { onBorderChange(border.copy(newOpenGlEdgeTangentSmear = it)) }
        SettingsParameterSlider("清晰度混合", "清晰采样与模糊采样的混合权重。", border.newOpenGlClarity, 0f..4f) { onBorderChange(border.copy(newOpenGlClarity = it)) }
        SettingsParameterSlider("切向涂抹", "主体和边缘共同的切向 smear 强度。", border.newOpenGlTangentSmear, -4f..8f) { onBorderChange(border.copy(newOpenGlTangentSmear = it)) }
    }

    SettingsParameterGroup(title = "调试｜兼容玻璃与旧版 OpenGL", subtitle = "旧版 OpenGL / Compose 玻璃对照参数，用于排查新旧渲染差异。") {
        SettingsParameterSlider("旧版边缘拉力", "兼容旧 OpenGL/Compose 玻璃边缘折射拉力。", border.openGlPullScale, -1200f..1200f) { onBorderChange(border.copy(openGlPullScale = it)) }
        SettingsParameterSlider("旧版压缩倍率", "旧版 OpenGL 压缩响应倍率。", border.openGlCompressionScale, -120f..120f) { onBorderChange(border.copy(openGlCompressionScale = it)) }
        SettingsParameterSlider("旧版圆角倍率", "旧版圆角折射增强倍率。", border.openGlCornerScale, -600f..600f) { onBorderChange(border.copy(openGlCornerScale = it)) }
        SettingsParameterSlider("旧版暗部倍率", "旧版暗部提取和边缘压暗倍率。", border.openGlDarkScale, -12f..12f) { onBorderChange(border.copy(openGlDarkScale = it)) }
        SettingsParameterSlider("旧版采样半径", "旧版采样半径缩放，方便和新版 OpenGL 对照。", border.openGlSampleRadiusScale, 0f..80f) { onBorderChange(border.copy(openGlSampleRadiusScale = it)) }
    }

    SettingsParameterGroup(title = "调试｜背景模糊与色彩采样", subtitle = "所有玻璃共用背景源；调模糊缓存、迭代次数、亮度、对比度和饱和度。") {
        SettingsParameterSlider("背景缓存分辨率", "调试背景模糊缓存分辨率，大范围用于压力测试；实际生成器仍有安全边界。", backdrop.scale, 0.1f..3f, { "${it.settingsRoundedValue()}×" }) { onBackdropChange(backdrop.copy(scale = it)) }
        SettingsParameterSlider("背景模糊级别", "调试清晰、低、中、高模糊层连续插值。", backdrop.radius, 0f..16f, { "${it.settingsRoundedValue()} 级" }) { onBackdropChange(backdrop.copy(radius = it)) }
        SettingsParameterSlider("背景模糊迭代", "调试模糊 pass 次数，范围放大用于寻找性能和质感边界。", backdrop.iterations, 0f..40f, { "${it.roundToInt()} 次" }) { onBackdropChange(backdrop.copy(iterations = it.roundToInt().toFloat())) }
        SettingsParameterSlider("背景输出亮度", "调试背景采样亮度输出。", backdrop.brightness, 0f..6f) { onBackdropChange(backdrop.copy(brightness = it)) }
        SettingsParameterSlider("背景输出对比度", "调试背景采样明暗反差。", backdrop.contrast, 0f..6f) { onBackdropChange(backdrop.copy(contrast = it)) }
        SettingsParameterSlider("背景输出饱和度", "调试背景采样色彩浓度。", backdrop.saturation, 0f..6f) { onBackdropChange(backdrop.copy(saturation = it)) }
    }

    SettingsParameterGroup(title = "调试｜上传图片亮度保护", subtitle = "只影响自定义背景图；调基础亮度、高光压缩起点和输出上限。") {
        SettingsParameterSlider("上传图亮度", "调试自定义背景基础亮度。", backdrop.customImageBrightness, 0f..3f) { onBackdropChange(backdrop.copy(customImageBrightness = it)) }
        SettingsParameterSlider("上传图高光起点", "调试高光压缩起点，允许极宽范围观察过曝保护。", backdrop.customImageHighlightStart, 0f..1f, { "${(it * 100f).roundToInt()}%" }) { onBackdropChange(backdrop.copy(customImageHighlightStart = it)) }
        SettingsParameterSlider("上传图高光上限", "调试高光输出上限，越低越压白。", backdrop.customImageHighlightLimit, 0f..1.5f, { "${(it * 100f).roundToInt()}%" }) { onBackdropChange(backdrop.copy(customImageHighlightLimit = it)) }
    }

    SettingsParameterGroup(title = "调试｜内置背景云雾与月亮", subtitle = "只影响内置主题背景；调云层、月亮、光晕和边缘光。") {
        SettingsParameterSlider("云雾透明度", "调试内置背景云雾层可见度。", backdrop.cloudAlpha, 0f..6f) { onBackdropChange(backdrop.copy(cloudAlpha = it)) }
        SettingsParameterSlider("云雾柔化", "调试云层边缘扩散。", backdrop.cloudSoftness, 0f..8f) { onBackdropChange(backdrop.copy(cloudSoftness = it)) }
        SettingsParameterSlider("云层横向拉伸", "调试云层横向铺展。", backdrop.cloudStretchX, 0f..10f) { onBackdropChange(backdrop.copy(cloudStretchX = it)) }
        SettingsParameterSlider("云层纵向拉伸", "调试云层纵向厚度。", backdrop.cloudStretchY, 0f..8f) { onBackdropChange(backdrop.copy(cloudStretchY = it)) }
        SettingsParameterSlider("云层高光", "调试云雾亮部高光透明度。", backdrop.cloudHighlightAlpha, 0f..4f) { onBackdropChange(backdrop.copy(cloudHighlightAlpha = it)) }
        SettingsParameterSlider("月亮尺寸", "调试内置背景月体尺寸。", backdrop.moonScale, 0f..4f) { onBackdropChange(backdrop.copy(moonScale = it)) }
        SettingsParameterSlider("月亮光晕", "调试月体外部光晕。", backdrop.moonHaloAlpha, 0f..4f) { onBackdropChange(backdrop.copy(moonHaloAlpha = it)) }
        SettingsParameterSlider("月亮边缘光", "调试月体轮廓边缘亮度。", backdrop.moonRimAlpha, 0f..4f) { onBackdropChange(backdrop.copy(moonRimAlpha = it)) }
    }
}

@Composable
private fun SettingsParameterGroup(title: String, subtitle: String, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = if (expanded) 0.070f else 0.048f))
            .animateContentSize(animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.88f), fontSize = 14.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (expanded) "收起 ︿" else "展开 ﹀", color = Color.White.copy(alpha = 0.56f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        if (expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

@Composable
private fun SettingsParameterSlider(
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String = { "${it.settingsRoundedValue()}×" },
    onValueChange: (Float) -> Unit,
) {
    val safeValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    InsetGlassParameterSlider(title = title, description = description, value = safeValue, valueRange = valueRange, onValueChange = onValueChange, valueText = valueText(safeValue))
}

private fun Float.settingsRoundedValue(): String = ((this * 100f).roundToInt() / 100f).toString()

@Composable
private fun DataContent(state: AssistantUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MiniSettingMetric("账单", "${state.ledgerRecords.size} 笔", Modifier.weight(1f))
        MiniSettingMetric("预算", "¥${state.ledgerBudgetText.ifBlank { "0" }}", Modifier.weight(1f))
        MiniSettingMetric("同步", "自动", Modifier.weight(1f))
    }
    SettingInfoRow("数据保存", "LedgerStore 统一持久化，手动与 AI 记账共用数据源")
    SettingInfoRow("云同步", "登录后自动合并并同步；未登录时保存在本机")
    SettingInfoRow("家", state.navigationHomeAddress.ifBlank { "未设置" })
    SettingInfoRow("学校", state.navigationSchoolAddress.ifBlank { "未设置" })
    SettingInfoRow("公司", state.navigationCompanyAddress.ifBlank { "未设置" })
    SettingInfoRow("宿舍", state.navigationDormAddress.ifBlank { "未设置" })
}

@Composable
private fun ServiceContent(state: AssistantUiState, aiEndpoint: String) {
    SettingsNestedOrdinaryGlassHost { NativeAccountSettingsCard(state) }
    SettingInfoRow("AI 接口", if (aiEndpoint.isBlank()) "未配置，使用本地占位回复" else aiEndpoint)
    SettingInfoRow("执行模式", "云端理解，本地确认后执行")
    SettingInfoRow("云端协议", "mobileAction / preferenceUpdate")
}

@Composable
private fun AdvancedContent() {
    SettingInfoRow("玻璃渲染", "仅真正的大型 Shell 使用 OpenGL")
    SettingInfoRow("功能页栏目", "普通入口卡片固定使用 Compose 玻璃")
    SettingInfoRow("隔离范围", "Card / Chip / Floating / Nav / Flex")
    SettingInfoRow("几何同步", "普通控件不注册 registry，也不请求 geometry sync")
    SettingInfoRow("账号控件", "纯 Compose + REST API，不接入 OpenGL registry")
}

@Composable
private fun ChatPageSettingsContent(@Suppress("UNUSED_PARAMETER") state: AssistantUiState) {
    val context = LocalContext.current
    val stickerLayout = InlineStickerDisplaySettings.layoutPreferences(context)

    SettingsParameterGroup(
        title = "内联表情排版",
        subtitle = "直接控制聊天正文里的表情大小、偏移、间距和行高占位。",
    ) {
        SettingsParameterSlider(
            title = "表情包大小",
            description = "控制聊天消息中内联表情的实际绘制尺寸，不再只作为上限。",
            value = stickerLayout.sizeDp,
            valueRange = InlineStickerDisplaySettings.SizeRange,
            valueText = { "${it.roundToInt()} dp" },
        ) { InlineStickerDisplaySettings.updateSizeDp(context, it) }

        SettingsParameterSlider(
            title = "上下偏移",
            description = "控制表情相对文字基线的上下位置；负数上移，正数下移。",
            value = stickerLayout.verticalOffsetDp,
            valueRange = InlineStickerDisplaySettings.VerticalOffsetRange,
            valueText = {
                val rounded = it.roundToInt()
                if (rounded > 0) "+$rounded dp" else "$rounded dp"
            },
        ) { InlineStickerDisplaySettings.updateVerticalOffsetDp(context, it) }

        SettingsParameterSlider(
            title = "左右间距",
            description = "控制表情左右两侧留白，避免贴字或过度挤压正文。",
            value = stickerLayout.horizontalGapDp,
            valueRange = InlineStickerDisplaySettings.HorizontalGapRange,
            valueText = { "${it.roundToInt()} dp" },
        ) { InlineStickerDisplaySettings.updateHorizontalGapDp(context, it) }

        SettingsParameterSlider(
            title = "行高余量",
            description = "控制表情参与文字行高测量时额外预留的上下空间。",
            value = stickerLayout.lineExtraDp,
            valueRange = InlineStickerDisplaySettings.LineExtraRange,
            valueText = { "${it.roundToInt()} dp" },
        ) { InlineStickerDisplaySettings.updateLineExtraDp(context, it) }
    }

    SettingsNestedOrdinaryGlassHost { InlineStickerExpressionSettingsControls() }
    InlineStickerDiagnosticsSettingsCard()

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.060f))
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("示例消息", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.ExtraBold)
        OptimizedRichMessageContent(
            text = "这次终于调顺了[[AI_LEDGER_INLINE_STICKER:joy_burst]][[AI_LEDGER_INLINE_STICKER:sparkle_excited]]，句中的表情会跟着大小、偏移、间距和行高设置实时变化。",
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("拖动上方滑块，示例和聊天页中的表情会同步更新。", color = Color.White.copy(alpha = 0.42f), fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InlineStickerDiagnosticsSettingsCard() {
    val context = LocalContext.current
    val latestDiagnostics by InlineStickerDiagnosticsStore
        .observe(context.applicationContext)
        .collectAsState(initial = InlineStickerDiagnosticsStore.latestJson(context.applicationContext))
    val clean = latestDiagnostics.trim()
    val summary = remember(clean) { inlineStickerDiagnosticsSummary(clean) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.060f))
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "表情诊断",
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 14.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                Text(
                    text = summary,
                    color = Color.White.copy(alpha = 0.46f),
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF8DF9EA).copy(alpha = if (clean.isBlank()) 0.070f else 0.14f))
                    .clickable(enabled = clean.isNotBlank()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("AI Ledger 表情诊断", clean))
                        Toast.makeText(context, "表情诊断已复制", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (clean.isBlank()) "暂无" else "复制",
                    color = Color.White.copy(alpha = if (clean.isBlank()) 0.40f else 0.88f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
            }
        }
        if (clean.isNotBlank()) {
            Text(
                text = clean.replace('\n', ' ').take(220),
                color = Color.White.copy(alpha = 0.30f),
                fontSize = 9.5.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = "发送一条云端回复后，这里会保存后端表情策略和 App 流式合并诊断。复制给我后能直接判断卡在哪一层。",
                color = Color.White.copy(alpha = 0.34f),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun inlineStickerDiagnosticsSummary(json: String): String {
    if (json.isBlank()) return "还没有收到诊断数据"
    fun findNumber(key: String): String? {
        val match = Regex("\\\"$key\\\"\\s*:\\s*(-?\\d+)").find(json) ?: return null
        return match.groupValues.getOrNull(1)
    }
    fun findText(key: String): String? {
        val match = Regex("\\\"$key\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").find(json) ?: return null
        return match.groupValues.getOrNull(1)
    }
    val frequency = findNumber("frequency") ?: "?"
    val intensity = findNumber("intensity") ?: "?"
    val target = findNumber("targetLocationCount") ?: "?"
    val model = findNumber("modelMarkerLocationCount") ?: findNumber("modelMarkerCount") ?: "?"
    val final = findNumber("finalRetainedLocationCount") ?: findNumber("finalRetainedMarkerCount") ?: findNumber("outputMarkerCount") ?: "?"
    val merge = findText("mergeDecision") ?: "无合并记录"
    return "频率 $frequency · 强度 $intensity · 目标 $target · 模型 $model · 最终 $final · $merge"
}

@Composable
private fun SettingsNestedOrdinaryGlassHost(content: @Composable () -> Unit) {
    OrdinaryGlassSceneHost(group = LocalGlassSceneContext.current.group, modifier = Modifier.fillMaxWidth(), renderMode = OrdinaryGlassRenderMode.ParentDraw, content = content)
}

private fun SettingsDetailSection.settingsOrder(): Int = when (this) {
    SettingsDetailSection.Appearance -> 0
    SettingsDetailSection.Glass -> 1
    SettingsDetailSection.Assistant -> 2
    SettingsDetailSection.Data -> 3
    SettingsDetailSection.Service -> 4
    SettingsDetailSection.Advanced -> 5
    SettingsDetailSection.Chat -> 6
    SettingsDetailSection.Memory -> 7
    SettingsDetailSection.Debug -> 8
}

private fun panelTitle(panel: SettingsDetailSection): String = when (panel) {
    SettingsDetailSection.Appearance -> "主题"
    SettingsDetailSection.Glass -> "玻璃"
    SettingsDetailSection.Assistant -> "视觉智能"
    SettingsDetailSection.Data -> "数据偏好"
    SettingsDetailSection.Service -> "账号设置"
    SettingsDetailSection.Advanced -> "系统信息"
    SettingsDetailSection.Chat -> "聊天设置"
    SettingsDetailSection.Memory -> "记忆"
    SettingsDetailSection.Debug -> "玻璃实验室"
}

private fun panelSubtitle(panel: SettingsDetailSection): String = when (panel) {
    SettingsDetailSection.Appearance -> "背景、主题和自定义图片。"
    SettingsDetailSection.Glass -> "玻璃、彩虹光效、背景模糊与上传图亮度保护。"
    SettingsDetailSection.Assistant -> "边缘光效、鼠标光标与运行 HUD 的全部参数。"
    SettingsDetailSection.Data -> "账单状态、预算、本地数据和常用导航地址。"
    SettingsDetailSection.Service -> "账号登录、AI Worker 和云端接口。"
    SettingsDetailSection.Advanced -> "渲染边界和 OpenGL 隔离状态。"
    SettingsDetailSection.Chat -> "聊天消息、内联表情显示与云端表达偏好。"
    SettingsDetailSection.Memory -> "登录后查看、整理并控制 AI 的长期记忆。"
    SettingsDetailSection.Debug -> "高级玻璃参数与实验入口。"
}

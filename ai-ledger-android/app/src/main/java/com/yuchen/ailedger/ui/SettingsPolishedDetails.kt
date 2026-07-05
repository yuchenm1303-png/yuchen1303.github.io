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
    @Suppress("UNUSED_PARAMETER") onBorderChange: (GlassBorderStyle) -> Unit,
) {
    val prism = state.rainbowPrismStyle
    val backdrop = state.backdropParams
    val composeMotion = ComposeGlassLabState.motionStyle

    SettingsParameterGroup(title = "Compose 玻璃光动效", subtitle = "只调普通 Compose 卡片、按钮和 Chip 的按压形变、触点白光、棱彩扫光与余辉。") {
        SettingsParameterSlider("总光动效", "全局控制普通 Compose 点击光动效能量。", composeMotion.master, 0f..1.5f) {
            ComposeGlassLabState.updateMotion(composeMotion.copy(master = it))
        }
        SettingsParameterSlider("按压形变", "控制横向膨胀、纵向压缩和下沉幅度。", composeMotion.deformation, 0f..1.5f) {
            ComposeGlassLabState.updateMotion(composeMotion.copy(deformation = it))
        }
        SettingsParameterSlider("触点白光", "控制触点附近的连续体积白光与青白捕光。", composeMotion.touchLight, 0f..1.8f) {
            ComposeGlassLabState.updateMotion(composeMotion.copy(touchLight = it))
        }
        SettingsParameterSlider("棱彩色散", "控制普通 Compose 组件上的粉黄青蓝色散，默认保持白光为主。", composeMotion.prism, 0f..1.5f) {
            ComposeGlassLabState.updateMotion(composeMotion.copy(prism = it))
        }
        SettingsParameterSlider("棱彩扫光", "控制按下后沿组件横向流动的彩色光带。", composeMotion.sweep, 0f..1.5f) {
            ComposeGlassLabState.updateMotion(composeMotion.copy(sweep = it))
        }
        SettingsParameterSlider("释放回弹", "控制松手后的反向弹起幅度。", composeMotion.rebound, 0f..1.5f) {
            ComposeGlassLabState.updateMotion(composeMotion.copy(rebound = it))
        }
        SettingsParameterSlider("松手余辉", "控制透镜亮度和扫光在松手后的消散时间。", composeMotion.afterglow, 0f..1.5f) {
            ComposeGlassLabState.updateMotion(composeMotion.copy(afterglow = it))
        }
    }

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

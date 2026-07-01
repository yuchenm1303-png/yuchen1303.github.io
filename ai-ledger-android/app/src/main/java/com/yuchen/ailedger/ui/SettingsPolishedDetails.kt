package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlin.math.roundToInt

@Composable
internal fun SettingsDetailPanel(
    panel: SettingsDetailSection,
    state: AssistantUiState,
    aiEndpoint: String,
    onQualityChange: (RenderQuality) -> Unit,
    @Suppress("UNUSED_PARAMETER") onPreviewConversationChange: (Boolean) -> Unit,
    onGlassPresetChange: (GlassPreset) -> Unit,
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
                val direction = if (
                    targetState.settingsOrder() >= initialState.settingsOrder()
                ) 1 else -1
                fadeIn(
                    animationSpec = tween(
                        170,
                        delayMillis = 42,
                        easing = FastOutSlowInEasing,
                    )
                ) +
                    slideInVertically(
                        animationSpec = tween(310, easing = FastOutSlowInEasing)
                    ) { 46 * direction } +
                    scaleIn(
                        initialScale = 0.955f,
                        animationSpec = tween(310, easing = FastOutSlowInEasing),
                    ) togetherWith
                    fadeOut(animationSpec = tween(135, easing = FastOutSlowInEasing)) +
                    slideOutVertically(
                        animationSpec = tween(170, easing = FastOutSlowInEasing)
                    ) { -30 * direction } +
                    scaleOut(
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
                            onQualityChange,
                            onGlassPresetChange,
                            onGlassIntensityChange,
                            onMotionIntensityChange,
                            onRainbowPrismChange,
                        )
                        SettingsDetailSection.Assistant -> VisualAgentHudSettingsContent(state)
                        SettingsDetailSection.Data -> DataContent(state)
                        SettingsDetailSection.Service -> ServiceContent(state, aiEndpoint)
                        SettingsDetailSection.Advanced -> AdvancedContent()
                        SettingsDetailSection.Chat -> ChatPageSettingsContent()
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
    Box(
        modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.80f,
                    stiffness = Spring.StiffnessMediumLow,
                )
            )
            .clip(shape)
    ) {
        GlassPanel(
            quality = state.quality,
            glassIntensity = state.glassIntensity,
            motionIntensity = state.motionIntensity,
            radius = radius,
            modifier = Modifier.matchParentSize(),
            role = GlassRole.Card,
        ) {}
        Box(Modifier.fillMaxWidth()) {
            content()
        }
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
    onQualityChange: (RenderQuality) -> Unit,
    onGlassPresetChange: (GlassPreset) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit,
    onRainbowPrismChange: (RainbowPrismStyle) -> Unit,
) {
    val prism = state.rainbowPrismStyle
    SettingChipGrid(
        RenderQuality.entries,
        state.quality,
        { qualityLabel(it) },
        state,
        onQualityChange,
    )
    SettingChipGrid(
        GlassPreset.entries,
        state.glassPreset,
        { glassPresetLabel(it) },
        state,
        onGlassPresetChange,
    )
    SliderSettingRow(
        "玻璃强度",
        "控制通用玻璃的可见度、雾感和边缘能量。",
        state.glassIntensity,
        0.6f..1.4f,
        onGlassIntensityChange,
    )
    SliderSettingRow(
        "动态强度",
        "控制呼吸、扫光和形变动画幅度，0 为静态。",
        state.motionIntensity,
        0f..1.4f,
        onMotionIntensityChange,
    )
    SectionTitleInline("首页聊天大玻璃彩虹")
    SliderSettingRow(
        "整体彩虹强度",
        "统一调节聊天大玻璃彩虹镀膜的总能量。",
        prism.overall,
        0f..2f,
    ) { onRainbowPrismChange(prism.copy(overall = it)) }
    SliderSettingRow(
        "棱彩边缘高光",
        "增强圆角和玻璃边缘对彩色入射光的捕获。",
        prism.edgeHighlight,
        0f..2f,
    ) { onRainbowPrismChange(prism.copy(edgeHighlight = it)) }
    SectionTitleInline("随机渐变扫光区间")
    SliderSettingRow(
        "扫光强度下限",
        "随机扫光每次出现时允许的最低亮度。",
        prism.sweepMin,
        0f..2f,
    ) { onRainbowPrismChange(prism.copy(sweepMin = it)) }
    SliderSettingRow(
        "扫光强度上限",
        "随机扫光每次出现时允许的最高亮度。",
        prism.sweepMax,
        0f..2f,
    ) { onRainbowPrismChange(prism.copy(sweepMax = it)) }
    SliderSettingRow(
        "粉金青蓝彩虹光晕",
        "调节粉、金、青、蓝在玻璃外缘形成的柔和光晕。",
        prism.rainbowHalo,
        0f..2f,
    ) { onRainbowPrismChange(prism.copy(rainbowHalo = it)) }
}

@Composable
private fun DataContent(state: AssistantUiState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        MiniSettingMetric("账单", "${state.ledgerRecords.size} 笔", Modifier.weight(1f))
        MiniSettingMetric(
            "预算",
            "¥${state.ledgerBudgetText.ifBlank { "0" }}",
            Modifier.weight(1f),
        )
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
    SettingsNestedOrdinaryGlassHost {
        NativeAccountSettingsCard(state)
    }
    SettingInfoRow(
        "AI 接口",
        if (aiEndpoint.isBlank()) "未配置，使用本地占位回复" else aiEndpoint,
    )
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
private fun ChatPageSettingsContent() {
    val context = LocalContext.current
    val stickerSizeDp = InlineStickerDisplaySettings.sizeDp(context)
    InsetGlassParameterSlider(
        title = "表情包大小",
        description = "调节聊天消息中内联表情的显示与排版占位尺寸。",
        value = stickerSizeDp,
        valueRange = InlineStickerDisplaySettings.SizeRange,
        onValueChange = { InlineStickerDisplaySettings.updateSizeDp(context, it) },
        valueText = "${stickerSizeDp.roundToInt()} dp",
    )
    SettingsNestedOrdinaryGlassHost {
        InlineStickerExpressionSettingsControls()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.060f))
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "示例消息",
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        OptimizedRichMessageContent(
            text = "这次终于调顺了[[AI_LEDGER_INLINE_STICKER:joy_burst]][[AI_LEDGER_INLINE_STICKER:sparkle_excited]]，句中的表情也会跟着当前尺寸实时变化。",
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "拖动上方滑块，示例和聊天页中的表情会同步更新。",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * 嵌套在雾面信息卡中的普通玻璃必须在信息卡内容层内批绘制，
 * 否则页面总父层会把它们画到外层雾面背景下面。
 */
@Composable
private fun SettingsNestedOrdinaryGlassHost(content: @Composable () -> Unit) {
    OrdinaryGlassSceneHost(
        group = LocalGlassSceneContext.current.group,
        modifier = Modifier.fillMaxWidth(),
        renderMode = OrdinaryGlassRenderMode.ParentDraw,
        content = content,
    )
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
    SettingsDetailSection.Glass -> "画质、玻璃质感和聊天大玻璃彩虹。"
    SettingsDetailSection.Assistant -> "边缘光效、鼠标光标与运行 HUD 的全部参数。"
    SettingsDetailSection.Data -> "账单状态、预算、本地数据和常用导航地址。"
    SettingsDetailSection.Service -> "账号登录、AI Worker 和云端接口。"
    SettingsDetailSection.Advanced -> "渲染边界和 OpenGL 隔离状态。"
    SettingsDetailSection.Chat -> "聊天消息、内联表情显示与云端表达偏好。"
    SettingsDetailSection.Memory -> "登录后查看、整理并控制 AI 的长期记忆。"
    SettingsDetailSection.Debug -> "高级玻璃参数与实验入口。"
}

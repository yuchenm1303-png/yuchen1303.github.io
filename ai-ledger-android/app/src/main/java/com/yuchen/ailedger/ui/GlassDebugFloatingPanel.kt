package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import kotlin.math.roundToInt

@Composable
fun GlassDebugFloatingPanel(
    state: AssistantUiState,
    onBackdropChange: (BackdropDebugParams) -> Unit,
    onBorderChange: (GlassBorderStyle) -> Unit,
    onUploadBackgroundClick: () -> Unit,
    onClearCustomBackgroundClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val params = state.backdropParams
    val border = state.glassBorderStyle
    val motion = ComposeGlassLabState.motionStyle
    var legacyBorder by remember { mutableStateOf(legacyOpenGlLabStyle()) }
    val parentDrawEnabled = GlassFoldoutParentDrawGate.displayedEnabled

    GlassSceneScope(
        group = GlassSceneGroup.SettingsDebugInnerScroll,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            OrdinaryParentDrawValidationToggle(
                enabled = parentDrawEnabled,
                onEnabledChange = GlassFoldoutParentDrawGate::setUserEnabled
            )
            GlassLabFoldout(
                "Compose光动效效果",
                "全局小按钮与卡片的按压形变、触点白光、棱彩扫光和余辉",
                true,
                state,
            ) {
                ComposeGlassMotionPreview()
                LabSlider("总光动效", "全局控制普通 Compose 点击光动效能量", motion.master, 0f..1.5f) {
                    ComposeGlassLabState.updateMotion(motion.copy(master = it))
                }
                LabSlider("光动效速度", "控制普通 Compose 白光扩散、扫光流动和余辉消散速度", motion.speed, 0.35f..2.5f) {
                    ComposeGlassLabState.updateMotion(motion.copy(speed = it))
                }
                LabSlider("按压形变", "控制横向膨胀、纵向压缩和下沉幅度", motion.deformation, 0f..1.5f) {
                    ComposeGlassLabState.updateMotion(motion.copy(deformation = it))
                }
                LabSlider("触点白光", "控制触点附近的连续体积白光与青白捕光", motion.touchLight, 0f..1.8f) {
                    ComposeGlassLabState.updateMotion(motion.copy(touchLight = it))
                }
                LabSlider("棱彩色散", "控制粉黄青蓝色散，默认保持白光为主", motion.prism, 0f..1.5f) {
                    ComposeGlassLabState.updateMotion(motion.copy(prism = it))
                }
                LabSlider("棱彩扫光", "控制按下后沿组件横向流动的彩色光带", motion.sweep, 0f..1.5f) {
                    ComposeGlassLabState.updateMotion(motion.copy(sweep = it))
                }
                LabSlider("释放回弹", "控制松手后的反向弹起幅度", motion.rebound, 0f..1.5f) {
                    ComposeGlassLabState.updateMotion(motion.copy(rebound = it))
                }
                LabSlider("松手余辉", "控制透镜亮度和扫光在松手后的消散时间", motion.afterglow, 0f..1.5f) {
                    ComposeGlassLabState.updateMotion(motion.copy(afterglow = it))
                }
                LabActionButton(
                    title = "恢复光动效默认值",
                    subtitle = "白光约 75% · 棱彩约 25% · 速度 1x",
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = ComposeGlassLabState::resetMotion,
                )
            }
            GlassLabFoldout("OpenGL", "旧 Shell 样本 / 保留原实现，不随新版替换", false, state) {
                OpenGlGlassLab(state, params, legacyBorder) { legacyBorder = it }
            }
            GlassLabFoldout("新版 OpenGL", "fc725b/V29.5 整圈统一映射 + 当前色散", false, state) {
                LatestOpenGLGlassLab(state, params, border, onBackdropChange, onBorderChange)
            }
            GlassLabFoldout("玻璃调试", "背景采样与全局背景参数", false, state) {
                LabSlider("背景云雾", "背景云雾透明度", params.cloudAlpha, 0f..2f) { onBackdropChange(params.copy(cloudAlpha = it)) }
                LabSlider("云雾柔化", "云层边缘柔和程度", params.cloudSoftness, 0f..3f) { onBackdropChange(params.copy(cloudSoftness = it)) }
                LabSlider("背景亮度", "背景整体明暗", params.brightness, 0.4f..2.2f) { onBackdropChange(params.copy(brightness = it)) }
                LabSlider("背景对比", "背景明暗反差", params.contrast, 0.5f..1.8f) { onBackdropChange(params.copy(contrast = it)) }
                LabSlider("边缘宽度", "玻璃外缘可见宽度", border.ringWidthDp, 0f..24f) { onBorderChange(border.copy(ringWidthDp = it)) }
                LabSlider("外描边", "外侧细边透明度", border.outerStrokeAlpha, 0f..1.5f) { onBorderChange(border.copy(outerStrokeAlpha = it)) }
                LabSlider("顶部高光", "上沿高光强度", border.topHighlightAlpha, 0f..2f) { onBorderChange(border.copy(topHighlightAlpha = it)) }
                LabSlider("底部阴影", "下沿暗部压边", border.bottomShadowAlpha, 0f..1.2f) { onBorderChange(border.copy(bottomShadowAlpha = it)) }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    LabActionButton("清除背景", "恢复主题", state, Modifier.weight(1f), onClearCustomBackgroundClick)
                    LabActionButton("背景图片", "上传", state, Modifier.weight(1f), onUploadBackgroundClick)
                }
            }
            RestoredGlassLabSections(state)
        }
    }
}

@Composable
private fun ComposeGlassMotionPreview() {
    val chipShape = RoundedCornerShape(999.dp)
    val cardShape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(0.82f)
                .height(46.dp)
                .composeGlassMotionClickable(shape = chipShape, onClick = {})
                .clip(chipShape)
                .background(Color(0xFF8DF9EA).copy(alpha = 0.085f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "按住小按钮",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Box(
            modifier = Modifier
                .weight(1.18f)
                .height(72.dp)
                .composeGlassMotionClickable(shape = cardShape, onClick = {})
                .clip(cardShape)
                .background(Color.White.copy(alpha = 0.055f)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 13.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("按住卡片", color = Color.White.copy(alpha = 0.90f), fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text("实时预览全局参数", color = Color.White.copy(alpha = 0.44f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OrdinaryParentDrawValidationToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.085f else 0.045f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "全 App 普通 Compose 父级绘制",
                color = Color.White.copy(alpha = 0.90f),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                if (enabled) "已接管页面、滚动子场景和持久底栏；关闭立即恢复子级绘制。"
                else "当前已关闭；Shell、OpenGL、聊天气泡、Frost、Inset 始终排除。",
                color = Color.White.copy(alpha = 0.46f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
private fun OpenGlGlassLab(
    state: AssistantUiState,
    params: BackdropDebugParams,
    style: GlassBorderStyle,
    onStyleChange: (GlassBorderStyle) -> Unit
) {
    val legacySpec = remember(state.quality, state.motionIntensity, state.backgroundTheme, params, style) {
        GlassBackdropSpec(
            quality = state.quality,
            motionIntensity = state.motionIntensity,
            theme = state.backgroundTheme,
            params = params,
            borderStyle = style
        )
    }
    CompositionLocalProvider(LocalGlassBackdrop provides legacySpec) {
        LegacyOpenGLGlassPreviewShell(
            quality = state.quality,
            glassIntensity = state.glassIntensity * 0.70f,
            motionIntensity = state.motionIntensity,
            radius = 26,
            modifier = Modifier.fillMaxWidth().height(120.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("旧 OpenGL Shell 样本", color = Color.White.copy(alpha = 0.94f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("可见", style.openGlVisibility, Modifier.weight(1f))
                    Metric("透明", style.openGlMaxAlpha, Modifier.weight(1f))
                    Metric("亮度", style.edgeBrightness, Modifier.weight(1f))
                }
            }
        }
    }
    Group("旧样本参数", "只影响这一栏旧样本", state) {
        LabSlider("可见强度", "OpenGL Shell 图层整体可见度", style.openGlVisibility, 0f..20f) { onStyleChange(style.copy(openGlVisibility = it)) }
        LabSlider("最大透明", "OpenGL Shell 最大 alpha 上限", style.openGlMaxAlpha, 0f..1f) { onStyleChange(style.copy(openGlMaxAlpha = it)) }
        LabSlider("旧边缘亮度", "旧 shader 的折射亮度", style.edgeBrightness, 0.20f..2.40f) { onStyleChange(style.copy(edgeBrightness = it)) }
        LabSlider("旧边缘宽度", "旧 shader rim 宽度", style.ringWidthDp, 0f..96f) { onStyleChange(style.copy(ringWidthDp = it)) }
    }
}

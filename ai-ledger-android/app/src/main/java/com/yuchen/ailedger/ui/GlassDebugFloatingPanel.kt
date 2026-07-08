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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import kotlin.math.roundToInt

private val ComposeMotionEnergyRange = 0f..12f
private val ComposeMotionLightRange = 0f..24f
private val ComposeMotionSpeedRange = 0.05f..8f
private val ComposeSizeBoostRange = 0f..5f
private val ComposeLargeDampRange = 0f..1.2f
private val ComposePivotRange = 60f..520f
private val ComposeVisualPxRange = 0.5f..18f
private val ComposeLightBoostRange = 0f..4f

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
    val sizeTuning = ComposeGlassLabState.sizeAdaptiveTuning
    val clipboard = LocalClipboardManager.current
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
                "8830 版按压鼓起 / 触点 bloom / 边缘 sweep / 释放余辉",
                true,
                state,
            ) {
                Group("8830 动画曲线", "只保留当前新光效真正消费的动画参数；范围放大方便压测", state, initiallyExpanded = true) {
                    ComposeGlassMotionPreview(state)
                    LabSlider("总强度 master", "普通 Compose 光动效总能量，0 关闭，越高越夸张", motion.master, ComposeMotionEnergyRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(master = it))
                    }
                    LabSlider("速度 speed", "控制按下、扫光、余辉退场速度；越高越快", motion.speed, ComposeMotionSpeedRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(speed = it))
                    }
                    LabSlider("本体形变 deformation", "控制玻璃本体鼓起和按压体积", motion.deformation, ComposeMotionEnergyRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(deformation = it))
                    }
                    LabSlider("释放回弹 rebound", "控制松手时的反向回弹幅度", motion.rebound, ComposeMotionEnergyRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(rebound = it))
                    }
                }
                Group("8830 白光与扫光", "触点径向 bloom、彩色棱镜和边缘 sweep；不再显示旧压力场参数", state, initiallyExpanded = true) {
                    ComposeGlassMotionPreview(state)
                    LabSlider("触点白光 touchLight", "控制按下中心 bloom 和 lens 的亮度", motion.touchLight, ComposeMotionLightRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(touchLight = it))
                    }
                    LabSlider("棱镜色散 prism", "控制粉、暖黄、青色的轻微色散；0 为纯白光", motion.prism, ComposeMotionLightRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(prism = it))
                    }
                    LabSlider("边缘扫光 sweep", "控制边缘 sweep 推进和描边光强", motion.sweep, ComposeMotionLightRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(sweep = it))
                    }
                    LabSlider("释放余辉 afterglow", "控制松手后白光和边缘扫光的保留时间", motion.afterglow, ComposeMotionLightRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(afterglow = it))
                    }
                }
                Group("尺寸归一化", "解决小玻璃不明显、大玻璃过夸张：按实际像素反推 scale", state, initiallyExpanded = true) {
                    ComposeGlassMotionPreview(state)
                    LabSlider("小玻璃增强", "增强 Chip/Floating 等小组件的可感知形变和光效", sizeTuning.smallBoost, ComposeSizeBoostRange) {
                        ComposeGlassLabState.updateSizeAdaptiveTuning(sizeTuning.copy(smallBoost = it))
                    }
                    LabSlider("大玻璃压制", "压低大 Card/Flex 的整体形变和大面积 bloom", sizeTuning.largeDamp, ComposeLargeDampRange) {
                        ComposeGlassLabState.updateSizeAdaptiveTuning(sizeTuning.copy(largeDamp = it))
                    }
                    LabSlider("尺寸分界 px", "小/大玻璃过渡分界，越大则更多组件走小玻璃增强", sizeTuning.pivotPx, ComposePivotRange) {
                        ComposeGlassLabState.updateSizeAdaptiveTuning(sizeTuning.copy(pivotPx = it))
                    }
                    LabSlider("目标形变 px", "以像素为单位的视觉鼓起目标，代替固定 scale 百分比", sizeTuning.visualPx, ComposeVisualPxRange) {
                        ComposeGlassLabState.updateSizeAdaptiveTuning(sizeTuning.copy(visualPx = it))
                    }
                    LabSlider("光效补偿", "整体补偿尺寸归一化后的白光强度", sizeTuning.lightBoost, ComposeLightBoostRange) {
                        ComposeGlassLabState.updateSizeAdaptiveTuning(sizeTuning.copy(lightBoost = it))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    LabActionButton(
                        title = "复制参数 JSON",
                        subtitle = "导出新光效与尺寸归一化参数",
                        state = state,
                        modifier = Modifier.weight(1f),
                        onClick = { clipboard.setText(AnnotatedString(composeGlassMotionExportJson(motion, sizeTuning))) },
                    )
                    LabActionButton(
                        title = "恢复 8830 默认",
                        subtitle = "恢复新光效基准值",
                        state = state,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ComposeGlassLabState.resetMotion()
                            ComposeGlassLabState.resetSizeAdaptiveTuning()
                        },
                    )
                }
            }
            GlassLabFoldout("新版 OpenGL", "fc725b/V29.5 整圈统一映射 + 当前色散", false, state) {
                LatestOpenGLGlassLab(state, params, border, onBackdropChange, onBorderChange)
            }
            GlassLabFoldout("玻璃调试", "背景采样与全局背景参数", false, state) {
                LabSlider("背景云雾", "背景云雾透明度", params.cloudAlpha, 0f..2f) { onBackdropChange(params.copy(cloudAlpha = it)) }
                LabSlider("云雾柔化", "云层边缘柔和程度", params.cloudSoftness, 0f..3f) { onBackdropChange(params.copy(cloudSoftness = it)) }
                LabSlider("背景亮度", "背景整体明暗", params.brightness, 0.4f..2.2f) { onBackdropChange(params.copy(brightness = it)) }
                LabSlider("背景对比", "背景整体明暗反差", params.contrast, 0.5f..1.8f) { onBackdropChange(params.copy(contrast = it)) }
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
private fun ComposeGlassMotionPreview(state: AssistantUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PressableGlass(
            state.quality,
            state.glassIntensity * 0.74f,
            state.motionIntensity,
            999,
            Modifier.weight(0.82f).height(46.dp),
            GlassRole.Chip,
            onClick = {},
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("按住小按钮", color = Color.White.copy(alpha = 0.88f), fontSize = 11.5.sp, fontWeight = FontWeight.Black)
            }
        }
        PressableGlass(
            state.quality,
            state.glassIntensity * 0.72f,
            state.motionIntensity,
            22,
            Modifier.weight(1.18f).height(72.dp),
            GlassRole.Card,
            onClick = {},
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalArrangement = Arrangement.Center) {
                Text("按住卡片", color = Color.White.copy(alpha = 0.90f), fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text("8830 光动效样本", color = Color.White.copy(alpha = 0.44f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OrdinaryParentDrawValidationToggle(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
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
            Text("全 App 普通 Compose 父级绘制", color = Color.White.copy(alpha = 0.90f), fontSize = 13.5.sp, fontWeight = FontWeight.Black)
            Text(
                if (enabled) "已接管页面、滚动子场景和持久底栏；关闭立即恢复子级绘制。" else "当前已关闭；Shell、OpenGL、聊天气泡、Frost、Inset 始终排除。",
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
private fun GlassLabFoldout(title: String, subtitle: String, initiallyExpanded: Boolean, state: AssistantUiState, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        PressableGlass(
            state.quality,
            state.glassIntensity * if (expanded) 0.94f else 0.76f,
            state.motionIntensity,
            24,
            Modifier.fillMaxWidth().height(58.dp),
            GlassRole.Flex,
            onClick = { expanded = !expanded }
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(subtitle, color = Color.White.copy(alpha = 0.44f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (expanded) "收起 ︿" else "展开 ﹀", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        GlassFoldoutAnimatedContent(expanded = expanded, modifier = Modifier.fillMaxWidth()) {
            OrdinaryParentDrawScopeIfEnabled {
                InsetGlassSliderBatchGroup(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { content() }
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, subtitle: String, state: AssistantUiState, initiallyExpanded: Boolean = false, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val groupShape = RoundedCornerShape(20.dp)
    val actionShape = RoundedCornerShape(999.dp)
    Column(
        modifier = Modifier.fillMaxWidth().clip(groupShape).background(Color.White.copy(alpha = 0.045f)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White.copy(alpha = 0.86f), fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                if (expanded) "收起" else "展开",
                color = Color.White.copy(alpha = 0.54f),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.composeGlassMotionClickable(shape = actionShape) { expanded = !expanded }.clip(actionShape).background(Color.White.copy(alpha = 0.060f)).padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        GlassFoldoutAnimatedContent(expanded = expanded, modifier = Modifier.fillMaxWidth()) {
            OrdinaryParentDrawScopeIfEnabled {
                InsetGlassSliderBatchGroup(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { content() }
                }
            }
        }
    }
}

@Composable
private fun OrdinaryParentDrawScopeIfEnabled(content: @Composable () -> Unit) {
    if (GlassFoldoutParentDrawGate.displayedEnabled) {
        CompositionLocalProvider(LocalOrdinaryGlassRenderMode provides OrdinaryGlassRenderMode.ParentDraw) { content() }
    } else {
        content()
    }
}

@Composable
private fun LabSlider(title: String, subtitle: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    InsetGlassParameterSlider(title = title, description = subtitle, value = value, valueRange = range, onValueChange = onValueChange, valueText = value.formatLabValue())
}

@Composable
private fun LabActionButton(title: String, subtitle: String, state: AssistantUiState, modifier: Modifier, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity * 0.72f, state.motionIntensity, 22, modifier.height(54.dp), GlassRole.Chip, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White.copy(alpha = 0.86f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.44f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun composeGlassMotionExportJson(motion: ComposeGlassMotionStyle, size: OrdinaryGlassSizeAdaptiveTuning): String {
    fun Float.exportValue(): String = ((this * 1000f).roundToInt() / 1000f).toString()
    return """
        {
          "composeGlassMotionStyle8830": {
            "master": ${motion.master.exportValue()},
            "speed": ${motion.speed.exportValue()},
            "deformation": ${motion.deformation.exportValue()},
            "rebound": ${motion.rebound.exportValue()},
            "touchLight": ${motion.touchLight.exportValue()},
            "prism": ${motion.prism.exportValue()},
            "sweep": ${motion.sweep.exportValue()},
            "afterglow": ${motion.afterglow.exportValue()}
          },
          "ordinaryGlassSizeAdaptiveTuning": {
            "smallBoost": ${size.smallBoost.exportValue()},
            "largeDamp": ${size.largeDamp.exportValue()},
            "pivotPx": ${size.pivotPx.exportValue()},
            "visualPx": ${size.visualPx.exportValue()},
            "lightBoost": ${size.lightBoost.exportValue()}
          }
        }
    """.trimIndent()
}

private fun Float.formatLabValue(): String {
    val rounded = (this * 100f).roundToInt() / 100f
    return if (rounded == rounded.roundToInt().toFloat()) rounded.roundToInt().toString() else rounded.toString()
}

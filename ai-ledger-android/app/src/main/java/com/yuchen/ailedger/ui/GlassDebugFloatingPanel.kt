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
private val ComposePressGainRange = 0f..24f
private val ComposePressBoostRange = 0f..16f
private val ComposePressMinFeedbackRange = 0f..8f
private val ComposePressLensRange = 0.05f..12f

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
                "只保留可直接调效果的新版归一化参数",
                true,
                state,
            ) {
                Group("基础动效", "先调总强度和速度，再调下面的小组件反馈", state, initiallyExpanded = true) {
                    ComposeGlassMotionPreview(state)
                    LabSlider("总强度", "普通 Compose 玻璃按压总能量；0 关闭，越高越夸张", motion.master, ComposeMotionEnergyRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(master = it))
                    }
                    LabSlider("速度", "按下、扫光、余辉退场速度", motion.speed, ComposeMotionSpeedRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(speed = it))
                    }
                    LabSlider("形变", "玻璃本体压缩、鼓起和下沉幅度", motion.deformation, ComposeMotionEnergyRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(deformation = it))
                    }
                    LabSlider("回弹", "松手时的反向弹性", motion.rebound, ComposeMotionEnergyRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(rebound = it))
                    }
                }
                Group("小组件反馈", "发送、加号、Chip、Floating 等小按钮主要看这里", state, initiallyExpanded = true) {
                    ComposeGlassMotionPreview(state)
                    LabSlider("小按钮灵敏度", "越高，小圆按钮和短 Chip 越容易有明显反馈", sizeTuning.pressSmallBoost, ComposePressBoostRange) {
                        ComposeGlassLabState.updateSizeAdaptiveTuning(sizeTuning.copy(pressSmallBoost = it))
                    }
                    LabSlider("最低点击反馈", "防止小按钮因为全局动效或尺寸归一化而按下没反应", sizeTuning.pressMinOptics, ComposePressMinFeedbackRange) {
                        ComposeGlassLabState.updateSizeAdaptiveTuning(sizeTuning.copy(pressMinOptics = it))
                    }
                    LabSlider("点击亮度", "触点白光、bloom 和按压增亮", sizeTuning.pressOpticsGain, ComposePressGainRange) {
                        ComposeGlassLabState.updateSizeAdaptiveTuning(sizeTuning.copy(pressOpticsGain = it))
                    }
                    LabSlider("光斑半径", "越高光斑越大；想更集中就调低", sizeTuning.pressLensGain, ComposePressLensRange) {
                        ComposeGlassLabState.updateSizeAdaptiveTuning(sizeTuning.copy(pressLensGain = it))
                    }
                    LabSlider("边缘扫光", "按压时边缘彩光和扫光强度", sizeTuning.pressRimGain, ComposePressGainRange) {
                        ComposeGlassLabState.updateSizeAdaptiveTuning(sizeTuning.copy(pressRimGain = it))
                    }
                }
                Group("大卡片和长条", "用于设置页长条卡片；首页小按钮一般不用先调这里", state, initiallyExpanded = false) {
                    LabSlider("长条反馈", "全宽低高度卡片的光效补偿", sizeTuning.pressRowBoost, ComposePressBoostRange) {
                        ComposeGlassLabState.updateSizeAdaptiveTuning(sizeTuning.copy(pressRowBoost = it))
                    }
                    LabSlider("本体形变增益", "所有普通 Compose 玻璃的身体缩放和下沉", sizeTuning.pressBodyGain, ComposePressGainRange) {
                        ComposeGlassLabState.updateSizeAdaptiveTuning(sizeTuning.copy(pressBodyGain = it))
                    }
                    LabSlider("玻璃整体增亮", "按压后玻璃本体透明度和亮度变化", sizeTuning.pressIntensityGain, ComposePressGainRange) {
                        ComposeGlassLabState.updateSizeAdaptiveTuning(sizeTuning.copy(pressIntensityGain = it))
                    }
                    LabSlider("阴影下沉", "按压时的下沉和阴影反馈", sizeTuning.pressShadowGain, ComposePressGainRange) {
                        ComposeGlassLabState.updateSizeAdaptiveTuning(sizeTuning.copy(pressShadowGain = it))
                    }
                    LabSlider("释放回弹", "尺寸归一化层的回弹倍率", sizeTuning.pressReboundGain, ComposePressGainRange) {
                        ComposeGlassLabState.updateSizeAdaptiveTuning(sizeTuning.copy(pressReboundGain = it))
                    }
                }
                Group("白光和色散", "这是光效颜色层，不负责尺寸归一化", state, initiallyExpanded = false) {
                    LabSlider("触点白光", "按下中心白光亮度", motion.touchLight, ComposeMotionLightRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(touchLight = it))
                    }
                    LabSlider("棱镜色散", "粉、暖黄、青色的彩色分离", motion.prism, ComposeMotionLightRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(prism = it))
                    }
                    LabSlider("边缘流光", "边缘 sweep 推进和描边光", motion.sweep, ComposeMotionLightRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(sweep = it))
                    }
                    LabSlider("释放余辉", "松手后白光和边缘光保留时间", motion.afterglow, ComposeMotionLightRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(afterglow = it))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    LabActionButton(
                        title = "复制参数 JSON",
                        subtitle = "只导出当前可调主参数",
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
                Text("光动效样本", color = Color.White.copy(alpha = 0.44f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
          "composeGlassMotion": {
            "master": ${motion.master.exportValue()},
            "speed": ${motion.speed.exportValue()},
            "deformation": ${motion.deformation.exportValue()},
            "rebound": ${motion.rebound.exportValue()},
            "touchLight": ${motion.touchLight.exportValue()},
            "prism": ${motion.prism.exportValue()},
            "sweep": ${motion.sweep.exportValue()},
            "afterglow": ${motion.afterglow.exportValue()}
          },
          "composePressTuning": {
            "smallButtonSensitivity": ${size.pressSmallBoost.exportValue()},
            "minimumPressFeedback": ${size.pressMinOptics.exportValue()},
            "pressLight": ${size.pressOpticsGain.exportValue()},
            "lensRadius": ${size.pressLensGain.exportValue()},
            "rimSweep": ${size.pressRimGain.exportValue()},
            "rowFeedback": ${size.pressRowBoost.exportValue()},
            "body": ${size.pressBodyGain.exportValue()},
            "intensity": ${size.pressIntensityGain.exportValue()},
            "shadow": ${size.pressShadowGain.exportValue()},
            "rebound": ${size.pressReboundGain.exportValue()}
          }
        }
    """.trimIndent()
}

private fun Float.formatLabValue(): String {
    val rounded = (this * 100f).roundToInt() / 100f
    return if (rounded == rounded.roundToInt().toFloat()) rounded.roundToInt().toString() else rounded.toString()
}

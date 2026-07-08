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

private val MotionHugeRange = 0f..120f
private val MotionLightHugeRange = 0f..160f
private val MotionSpeedHugeRange = 0.02f..40f
private val CapsulePxHugeRange = 0f..1.2f
private val CapsuleWideHugeRange = 0f..24f
private val CapsulePopHugeRange = 0.05f..40f
private val OpticsHugeRange = 0f..160f
private val EdgeWidthHugeRange = 0f..80f

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
    val capsule = ComposeGlassLabState.capsuleTuning
    val optics = ComposeGlassLabState.pressureOpticsTuning
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
                "Compose白光胶囊动效",
                "真实玻璃本体动画、白光余辉、胶囊形变调试",
                true,
                state,
            ) {
                Group("总控", "全局能量、速度、余辉收尾；上限已拉大用于极端调试", state, initiallyExpanded = false) {
                    ComposeGlassMotionPreview(state)
                    LabSlider("总光动效", "全局控制普通 Compose 点击光效与胶囊能量", motion.master, MotionHugeRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(master = it))
                    }
                    LabSlider("胶囊速度", "控制按下、扫光、余辉退场速度；越高越快", motion.speed, MotionSpeedHugeRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(speed = it))
                    }
                    LabSlider("余辉收尾", "控制松手后白光淡出时长和连续性，专门修复啪一下断层", motion.afterglow, MotionLightHugeRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(afterglow = it))
                    }
                }
                Group("胶囊本体", "真实 PressableGlass 本体同向鼓起，不是外面套壳", state, initiallyExpanded = false) {
                    ComposeGlassMotionPreview(state)
                    LabSlider("按压形变", "控制胶囊整体鼓起体积，越高越明显", motion.deformation, MotionHugeRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(deformation = it))
                    }
                    LabSlider("短点击冲量", "点一下时瞬间鼓起和白光起跳能量", motion.tapImpulse, MotionHugeRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(tapImpulse = it))
                    }
                    LabSlider("释放回弹", "控制松手后的轻微反向回弹", motion.rebound, MotionHugeRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(rebound = it))
                    }
                    LabSlider("释放凝聚", "控制回落阻尼，越高越黏、越不散", motion.releaseCohesion, MotionHugeRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(releaseCohesion = it))
                    }
                    LabSlider("光场连续", "控制白光从按下到松手的衔接强度", motion.fieldContinuity, MotionHugeRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(fieldContinuity = it))
                    }
                    LabSlider("扫光惯性", "控制边缘扫光在松手后的保留和滑走", motion.sweepMomentum, MotionHugeRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(sweepMomentum = it))
                    }
                }
                Group("白光与扫光", "主白光 bloom、边缘轻 sweep，不走高饱和彩虹", state, initiallyExpanded = false) {
                    ComposeGlassMotionPreview(state)
                    LabSlider("白光镜头", "控制触点白光和中心大 bloom 的亮度", motion.touchLight, MotionLightHugeRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(touchLight = it))
                    }
                    LabSlider("边缘扫光", "控制边缘粉白、暖白、青白轻扫强度", motion.sweep, MotionLightHugeRange) {
                        ComposeGlassLabState.updateMotion(motion.copy(sweep = it))
                    }
                }
                Group("胶囊尺寸细调", "强化胶囊感：长条纵向鼓起、小按钮体积、点击尾巴", state, initiallyExpanded = false) {
                    ComposeGlassMotionPreview(state)
                    LabSlider("小尺寸增强", "小按钮、小卡片胶囊体积增强", capsule.compactBoost, 0f..12f) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(compactBoost = it))
                    }
                    LabSlider("长条横向抑制", "越高越抑制长条左右拉爆；新版默认接近 0", capsule.elongatedX, 0f..8f) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(elongatedX = it))
                    }
                    LabSlider("长条纵向补偿", "越高长胶囊上下鼓起越强", capsule.elongatedY, 0f..8f) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(elongatedY = it))
                    }
                    LabSlider("基础像素形变", "真实像素膨胀基准，控制长按体积", capsule.basePx, CapsulePxHugeRange) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(basePx = it))
                    }
                    LabSlider("短点击像素", "点一下额外鼓起体积，优先调这个找胶囊感", capsule.tapPx, CapsulePxHugeRange) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(tapPx = it))
                    }
                    LabSlider("点击峰值", "短点击峰值放大，越高越像软胶囊弹起", capsule.tapPop, CapsulePopHugeRange) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(tapPop = it))
                    }
                    LabSlider("点击拖尾", "短点击松手后托住胶囊和白光的尾巴", capsule.tapCarry, CapsuleWideHugeRange) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(tapCarry = it))
                    }
                    LabSlider("黏滞白胶", "给胶囊源头增加白胶黏性，过高会拖慢", capsule.sticky, 0f..4f) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(sticky = it))
                    }
                    LabSlider("下沉重量", "按下时向下沉入量，过高会显重", capsule.sink, CapsuleWideHugeRange) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(sink = it))
                    }
                    LabSlider("释放回落", "释放阶段的回落和收束强度", capsule.settle, 0f..12f) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(settle = it))
                    }
                }
                Group("白光光场", "大半径、低梯度、缓慢退场的乳白压力场", state, initiallyExpanded = false) {
                    ComposeGlassMotionPreview(state)
                    LabSlider("光场强度", "整片白光亮度，上限极大用于过曝边界测试", optics.fieldIntensity, OpticsHugeRange) {
                        ComposeGlassLabState.updatePressureOpticsTuning(optics.copy(fieldIntensity = it))
                    }
                    LabSlider("铺开范围", "白光半径和铺开面积", optics.fieldSpread, OpticsHugeRange) {
                        ComposeGlassLabState.updatePressureOpticsTuning(optics.copy(fieldSpread = it))
                    }
                    LabSlider("揉开柔度", "渐变柔度，越高越没有硬圆心", optics.fieldSoftness, OpticsHugeRange) {
                        ComposeGlassLabState.updatePressureOpticsTuning(optics.copy(fieldSoftness = it))
                    }
                    LabSlider("形状消隐", "削弱中心硬斑，让白光更像整片雾", optics.fieldUniformity, OpticsHugeRange) {
                        ComposeGlassLabState.updatePressureOpticsTuning(optics.copy(fieldUniformity = it))
                    }
                    LabSlider("触点跟随", "光场中心跟随手指程度，过高会看到圆心移动", optics.fieldFollow, 0f..8f) {
                        ComposeGlassLabState.updatePressureOpticsTuning(optics.copy(fieldFollow = it))
                    }
                }
                Group("边缘白光", "边缘细 sweep、边缘 bloom 和柔化宽度", state, initiallyExpanded = false) {
                    ComposeGlassMotionPreview(state)
                    LabSlider("边缘强度", "边缘高光总亮度", optics.edgeIntensity, OpticsHugeRange) {
                        ComposeGlassLabState.updatePressureOpticsTuning(optics.copy(edgeIntensity = it))
                    }
                    LabSlider("边缘宽度", "边缘白光 stroke 宽度", optics.edgeWidth, EdgeWidthHugeRange) {
                        ComposeGlassLabState.updatePressureOpticsTuning(optics.copy(edgeWidth = it))
                    }
                    LabSlider("边缘柔化", "边缘高光柔化程度", optics.edgeSoftness, OpticsHugeRange) {
                        ComposeGlassLabState.updatePressureOpticsTuning(optics.copy(edgeSoftness = it))
                    }
                    LabSlider("边缘泛光", "边缘附近白色 bloom，配合收尾看是否断层", optics.edgeBloom, OpticsHugeRange) {
                        ComposeGlassLabState.updatePressureOpticsTuning(optics.copy(edgeBloom = it))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    LabActionButton(
                        title = "复制参数 JSON",
                        subtitle = "导出白光胶囊完整参数",
                        state = state,
                        modifier = Modifier.weight(1f),
                        onClick = { clipboard.setText(AnnotatedString(composeGlassMotionExportJson(motion, capsule, optics))) },
                    )
                    LabActionButton(
                        title = "恢复默认值",
                        subtitle = "恢复新版白光胶囊默认值",
                        state = state,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ComposeGlassLabState.resetMotion()
                            ComposeGlassLabState.resetCapsuleTuning()
                            ComposeGlassLabState.resetPressureOpticsTuning()
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
                Text("真实 PressableGlass 样本", color = Color.White.copy(alpha = 0.44f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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

private fun composeGlassMotionExportJson(motion: ComposeGlassMotionStyle, capsule: OrdinaryGlassCapsuleTuning, optics: OrdinaryGlassPressureOpticsTuning): String {
    fun Float.exportValue(): String = ((this * 1000f).roundToInt() / 1000f).toString()
    return """
        {
          "composeGlassMotionStyle": {
            "master": ${motion.master.exportValue()},
            "deformation": ${motion.deformation.exportValue()},
            "touchLight": ${motion.touchLight.exportValue()},
            "prism": ${motion.prism.exportValue()},
            "sweep": ${motion.sweep.exportValue()},
            "rebound": ${motion.rebound.exportValue()},
            "afterglow": ${motion.afterglow.exportValue()},
            "speed": ${motion.speed.exportValue()},
            "tapImpulse": ${motion.tapImpulse.exportValue()},
            "releaseCohesion": ${motion.releaseCohesion.exportValue()},
            "fieldContinuity": ${motion.fieldContinuity.exportValue()},
            "sweepMomentum": ${motion.sweepMomentum.exportValue()}
          },
          "ordinaryGlassCapsuleTuning": {
            "compactBoost": ${capsule.compactBoost.exportValue()},
            "elongatedX": ${capsule.elongatedX.exportValue()},
            "elongatedY": ${capsule.elongatedY.exportValue()},
            "basePx": ${capsule.basePx.exportValue()},
            "tapPx": ${capsule.tapPx.exportValue()},
            "tapPop": ${capsule.tapPop.exportValue()},
            "tapCarry": ${capsule.tapCarry.exportValue()},
            "sticky": ${capsule.sticky.exportValue()},
            "sink": ${capsule.sink.exportValue()},
            "settle": ${capsule.settle.exportValue()}
          },
          "ordinaryGlassPressureOpticsTuning": {
            "fieldIntensity": ${optics.fieldIntensity.exportValue()},
            "fieldSpread": ${optics.fieldSpread.exportValue()},
            "fieldSoftness": ${optics.fieldSoftness.exportValue()},
            "fieldUniformity": ${optics.fieldUniformity.exportValue()},
            "fieldFollow": ${optics.fieldFollow.exportValue()},
            "edgeIntensity": ${optics.edgeIntensity.exportValue()},
            "edgeWidth": ${optics.edgeWidth.exportValue()},
            "edgeSoftness": ${optics.edgeSoftness.exportValue()},
            "edgeBloom": ${optics.edgeBloom.exportValue()}
          }
        }
    """.trimIndent()
}

private fun Float.formatLabValue(): String {
    val rounded = (this * 100f).roundToInt() / 100f
    return if (rounded == rounded.roundToInt().toFloat()) rounded.roundToInt().toString() else rounded.toString()
}

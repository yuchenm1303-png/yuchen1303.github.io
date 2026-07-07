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
    val capsule = ComposeGlassLabState.capsuleTuning
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
                "普通 Compose 玻璃按压胶囊、白光场、释放余辉",
                true,
                state,
            ) {
                ComposeGlassMotionPreview()
                Group("总控", "全局能量、速度和整体光动效开关", state, initiallyExpanded = true) {
                    ComposeGlassMotionPreview()
                    LabSlider("总光动效", "全局控制普通 Compose 点击光动效能量", motion.master, 0f..3f) {
                        ComposeGlassLabState.updateMotion(motion.copy(master = it))
                    }
                    LabSlider("胶囊速度", "只控制普通 Compose 玻璃按压胶囊与白光场速度", motion.speed, 0.08f..8f) {
                        ComposeGlassLabState.updateMotion(motion.copy(speed = it))
                    }
                }
                Group("胶囊源头", "Glass.kt 源头状态机派生的按压、冲量和释放包络", state, initiallyExpanded = true) {
                    ComposeGlassMotionPreview()
                    LabSlider("按压形变", "控制胶囊膨胀、下沉和压入幅度", motion.deformation, 0f..3f) {
                        ComposeGlassLabState.updateMotion(motion.copy(deformation = it))
                    }
                    LabSlider("短点击冲量", "点一下时额外推高 tap 相位和释放前胶囊能量", motion.tapImpulse, 0f..3f) {
                        ComposeGlassLabState.updateMotion(motion.copy(tapImpulse = it))
                    }
                    LabSlider("释放粘度", "越高回弹越明显；默认降低，保留粘滞回落", motion.rebound, 0f..3f) {
                        ComposeGlassLabState.updateMotion(motion.copy(rebound = it))
                    }
                    LabSlider("释放凝聚", "控制松手时负向释放包络的连贯和黏性", motion.releaseCohesion, 0f..3f) {
                        ComposeGlassLabState.updateMotion(motion.copy(releaseCohesion = it))
                    }
                    LabSlider("光场连续", "控制二次点击继承尾迹和释放阶段的连续感", motion.fieldContinuity, 0f..3f) {
                        ComposeGlassLabState.updateMotion(motion.copy(fieldContinuity = it))
                    }
                }
                Group("App内胶囊细调", "直接调真实 App 内的尺寸函数和父级胶囊形变", state, initiallyExpanded = true) {
                    ComposeGlassMotionPreview()
                    LabSlider("小尺寸增强", "越高小按钮、小卡片越明显；大卡片基本不变", capsule.compactBoost, 0f..2.4f) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(compactBoost = it))
                    }
                    LabSlider("长条横向抑制", "越高长按钮越不左右拉爆；方形按钮几乎不受影响", capsule.elongatedX, 0f..0.9f) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(elongatedX = it))
                    }
                    LabSlider("长条纵向补偿", "越高长按钮上下胶囊感越明显；过高会显得竖向弹", capsule.elongatedY, 0f..0.6f) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(elongatedY = it))
                    }
                    LabSlider("基础像素形变", "真实像素膨胀基准，控制按压/长按的胶囊体积", capsule.basePx, 0.005f..0.085f) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(basePx = it))
                    }
                    LabSlider("短点击像素", "点一下时额外鼓起的胶囊体积，优先调这个找点击手感", capsule.tapPx, 0f..0.12f) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(tapPx = it))
                    }
                    LabSlider("点击峰值", "短点击 tap 相位的峰值放大，越高越像弹起的胶囊", capsule.tapPop, 0.2f..2.8f) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(tapPop = it))
                    }
                    LabSlider("点击拖尾", "短点击松手后继续托住胶囊的黏滞尾巴", capsule.tapCarry, 0f..1.4f) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(tapCarry = it))
                    }
                    LabSlider("黏滞白胶", "跟随 lens/sweep 的额外粘性，过高会糊", capsule.sticky, 0f..0.08f) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(sticky = it))
                    }
                    LabSlider("下沉重量", "点击和按压的向下沉入量，控制手指压下的重量感", capsule.sink, 0f..1.8f) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(sink = it))
                    }
                    LabSlider("释放回落", "释放阶段反向回落的可见程度，过高会抖", capsule.settle, 0f..1f) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(settle = it))
                    }
                }
                Group("白光光场", "触点白光、连续扩散和松手余辉", state, initiallyExpanded = false) {
                    ComposeGlassMotionPreview()
                    LabSlider("触点白光", "控制触点附近的连续体积白光与青白捕光", motion.touchLight, 0f..3f) {
                        ComposeGlassLabState.updateMotion(motion.copy(touchLight = it))
                    }
                    LabSlider("白光扩散", "控制按下后光场沿组件内部扩散的强度", motion.sweep, 0f..3f) {
                        ComposeGlassLabState.updateMotion(motion.copy(sweep = it))
                    }
                    LabSlider("扩散惯性", "控制释放阶段扫光尾迹和白光扩散的持续感", motion.sweepMomentum, 0f..3f) {
                        ComposeGlassLabState.updateMotion(motion.copy(sweepMomentum = it))
                    }
                    LabSlider("松手余辉", "控制透镜亮度和光场在松手后的消散时间", motion.afterglow, 0f..3f) {
                        ComposeGlassLabState.updateMotion(motion.copy(afterglow = it))
                    }
                    LabSlider("棱彩色散", "当前普通玻璃 normalized 后保持白光为主，默认禁用色散", motion.prism, 0f..1.5f) {
                        ComposeGlassLabState.updateMotion(motion.copy(prism = it))
                    }
                }
                Group("背景采样", "普通 Compose 玻璃背景透明、模糊和底层乳化", state, initiallyExpanded = false) {
                    ComposeGlassMotionPreview()
                    val style = ComposeGlassLabState.style
                    LabSlider("背景采样", "控制普通玻璃背景采样透明度", style.backdrop, 0.12f..1.55f) {
                        ComposeGlassLabState.update(style.copy(backdrop = it))
                    }
                    LabSlider("背景模糊", "控制普通玻璃背景模糊缩放", style.backdropBlur, 0.35f..2.2f) {
                        ComposeGlassLabState.update(style.copy(backdropBlur = it))
                    }
                    LabSlider("背景压暗", "保留字段，方便排查背景暗化参数", style.backdropDim, 0f..2f) {
                        ComposeGlassLabState.update(style.copy(backdropDim = it))
                    }
                    LabSlider("背景乳化", "保留字段，方便排查玻璃乳白底层", style.backdropMilk, 0f..2f) {
                        ComposeGlassLabState.update(style.copy(backdropMilk = it))
                    }
                    LabSlider("背景高光", "保留字段，方便排查背景高光注入", style.backdropHighlight, 0f..2f) {
                        ComposeGlassLabState.update(style.copy(backdropHighlight = it))
                    }
                }
                Group("静态玻璃材质", "普通 Compose 玻璃底材、边缘、雾面和暗部质量", state, initiallyExpanded = false) {
                    ComposeGlassMotionPreview()
                    val style = ComposeGlassLabState.style
                    LabSlider("安静吸收", "控制玻璃内部暗部与安静度", style.quiet, 0.2f..2.2f) {
                        ComposeGlassLabState.update(style.copy(quiet = it))
                    }
                    LabSlider("主体吸收", "保留字段，方便排查主体吸收参数", style.bodyAbsorption, 0f..2f) {
                        ComposeGlassLabState.update(style.copy(bodyAbsorption = it))
                    }
                    LabSlider("下部质量", "保留字段，方便排查下部厚重感参数", style.lowerBodyMass, 0f..2f) {
                        ComposeGlassLabState.update(style.copy(lowerBodyMass = it))
                    }
                    LabSlider("内部过渡", "控制/保留普通玻璃内缘过渡强度", style.innerTransition, 0f..2.8f) {
                        ComposeGlassLabState.update(style.copy(innerTransition = it))
                    }
                    LabSlider("外缘光", "控制普通玻璃外缘描边亮度", style.outerRim, 0.02f..3.2f) {
                        ComposeGlassLabState.update(style.copy(outerRim = it))
                    }
                    LabSlider("底部重量", "控制普通玻璃底部暗部压边", style.bottomMass, 0.02f..3f) {
                        ComposeGlassLabState.update(style.copy(bottomMass = it))
                    }
                    LabSlider("侧边承光", "控制普通玻璃两侧轻微承光", style.sideLight, 0f..3f) {
                        ComposeGlassLabState.update(style.copy(sideLight = it))
                    }
                    LabSlider("圆角缩放", "控制普通玻璃非 Shell 圆角缩放", style.radius, 18f..86f) {
                        ComposeGlassLabState.update(style.copy(radius = it))
                    }
                    LabSlider("光带保留", "保留字段，方便排查 ribbon 参数", style.ribbon, 0f..2f) {
                        ComposeGlassLabState.update(style.copy(ribbon = it))
                    }
                }
                Group("边缘高光", "普通 Compose 玻璃上下沿光带和横向流动", state, initiallyExpanded = false) {
                    ComposeGlassMotionPreview()
                    val style = ComposeGlassLabState.style
                    LabSlider("顶部高光", "控制普通玻璃上沿高光强度", style.topLight, 0.02f..3.4f) {
                        ComposeGlassLabState.update(style.copy(topLight = it))
                    }
                    LabSlider("顶部宽度", "控制普通玻璃上沿光带宽度", style.topWidthDp, 0.2f..8f) {
                        ComposeGlassLabState.update(style.copy(topWidthDp = it))
                    }
                    LabSlider("顶部流动", "控制上沿高光横向变化", style.topVariation, 0f..3f) {
                        ComposeGlassLabState.update(style.copy(topVariation = it))
                    }
                    LabSlider("底部高光", "控制普通玻璃下沿亮部", style.bottomLight, 0.02f..3f) {
                        ComposeGlassLabState.update(style.copy(bottomLight = it))
                    }
                    LabSlider("底部宽度", "控制普通玻璃下沿光带宽度", style.bottomWidthDp, 0.2f..8f) {
                        ComposeGlassLabState.update(style.copy(bottomWidthDp = it))
                    }
                }
                LabActionButton(
                    title = "恢复光动效默认值",
                    subtitle = "同时恢复动效、胶囊细调和普通玻璃材质",
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = ComposeGlassLabState::resetAll,
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

private fun legacyOpenGlLabStyle(): GlassBorderStyle = GlassBorderStyle(
    ringWidthDp = 8.295f,
    edgePullDp = -199.078f,
    edgeBrightness = 1.083f,
    edgeTint = 0.287f,
    cornerBoost = 0.15f,
    openGlVisibility = 11.6f,
    openGlMaxAlpha = 0.665f,
    fillAlpha = 0.047f,
    edgeWhiteAlpha = 0.152f,
    edgeCyanAlpha = 0.285f,
    edgeMagentaAlpha = 0.285f,
    edgeYellowAlpha = 0.135f,
    edgeBlueAlpha = 0.405f,
    outerStrokeAlpha = 0.225f,
    topHighlightAlpha = 0.381f,
    bottomShadowAlpha = 0.283f,
    refraction = 1.38f,
    chromaShift = 2.2f
)

@Composable
private fun GlassLabFoldout(
    title: String,
    subtitle: String,
    initiallyExpanded: Boolean,
    state: AssistantUiState,
    content: @Composable () -> Unit
) {
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
            Row(
                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(subtitle, color = Color.White.copy(alpha = 0.44f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (expanded) "收起 ︿" else "展开 ﹀", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        GlassFoldoutAnimatedContent(
            expanded = expanded,
            modifier = Modifier.fillMaxWidth()
        ) {
            InsetGlassSliderBatchGroup(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { content() }
            }
        }
    }
}

@Composable
private fun Group(
    title: String,
    subtitle: String,
    state: AssistantUiState,
    initiallyExpanded: Boolean = true,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val groupShape = RoundedCornerShape(20.dp)
    val actionShape = RoundedCornerShape(999.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(groupShape)
            .background(Color.White.copy(alpha = 0.045f))
            .padding(10.dp),
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
                modifier = Modifier
                    .composeGlassMotionClickable(shape = actionShape) { expanded = !expanded }
                    .clip(actionShape)
                    .background(Color.White.copy(alpha = 0.060f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        GlassFoldoutAnimatedContent(
            expanded = expanded,
            modifier = Modifier.fillMaxWidth()
        ) {
            InsetGlassSliderBatchGroup(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { content() }
            }
        }
    }
}

@Composable
private fun LabSlider(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    InsetGlassParameterSlider(
        title = title,
        description = subtitle,
        value = value,
        valueRange = range,
        onValueChange = onValueChange,
        valueText = value.formatLabValue()
    )
}

@Composable
private fun LabActionButton(
    title: String,
    subtitle: String,
    state: AssistantUiState,
    modifier: Modifier,
    onClick: () -> Unit
) {
    PressableGlass(
        state.quality,
        state.glassIntensity * 0.72f,
        state.motionIntensity,
        22,
        modifier.height(54.dp),
        GlassRole.Chip,
        onClick = onClick
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White.copy(alpha = 0.86f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.44f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Metric(label: String, value: Float, modifier: Modifier = Modifier) {
    Column(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.060f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.46f), fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(value.formatLabValue(), color = Color.White.copy(alpha = 0.86f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

private fun Float.formatLabValue(): String = ((this * 100f).roundToInt() / 100f).toString()
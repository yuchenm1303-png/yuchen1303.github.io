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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import java.util.Locale
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
    val style = ComposeGlassLabState.style
    val motion = ComposeGlassLabState.motionStyle
    val capsule = ComposeGlassLabState.capsuleTuning
    val clipboard = LocalClipboardManager.current
    var exportNotice by rememberSaveable { mutableStateOf("") }
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
                "普通 Compose 玻璃按压胶囊、白光场、释放余辉、材质边缘参数",
                true,
                state,
            ) {
                ComposeGlassMotionPreview(state)
                GlassLabFoldout("总开关与导出", "预设切换、总能量、一键复制完整参数", false, state) {
                    ComposeGlassGroupSample(state, "预设样本", "切换预设后长按这里确认材质", GlassRole.Card, 24, 58.dp)
                    Text(
                        "当前预设：${style.preset}",
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        LabActionButton("Clear", "清透", state, Modifier.weight(1f)) { ComposeGlassLabState.usePreset(ComposeGlassPreset.Clear) }
                        LabActionButton("Frost", "默认", state, Modifier.weight(1f)) { ComposeGlassLabState.usePreset(ComposeGlassPreset.Frost) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        LabActionButton("Crystal", "亮边", state, Modifier.weight(1f)) { ComposeGlassLabState.usePreset(ComposeGlassPreset.Crystal) }
                        LabActionButton("Dense", "厚重", state, Modifier.weight(1f)) { ComposeGlassLabState.usePreset(ComposeGlassPreset.Dense) }
                        LabActionButton("Aurora", "柔彩", state, Modifier.weight(1f)) { ComposeGlassLabState.usePreset(ComposeGlassPreset.Aurora) }
                    }
                    LabSlider("总光动效", "全局控制普通 Compose 点击光动效能量；0 为关闭，1 为默认", motion.master, 0f..3f) {
                        ComposeGlassLabState.updateMotion(motion.copy(master = it))
                    }
                    LabActionButton(
                        title = "一键导出当前 Compose 参数",
                        subtitle = "复制 style / motion / capsule 全量参数到剪贴板",
                        state = state,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            clipboard.setText(AnnotatedString(composeGlassLabExportText(style, motion, capsule)))
                            exportNotice = "已复制当前 Compose 玻璃实验室参数，可直接粘贴保存。"
                        },
                    )
                    if (exportNotice.isNotBlank()) {
                        Text(
                            exportNotice,
                            color = Color.White.copy(alpha = 0.52f),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                GlassLabFoldout("全局时间与连续场", "统一控制 press / lens / sweep 的连续动效场", false, state) {
                    ComposeGlassGroupSample(state, "连续场样本", "重点看按下到松手是否像一个整体", GlassRole.Flex, 999, 46.dp)
                    LabSlider("胶囊速度", "控制普通 Compose 玻璃按压胶囊、白光场、释放尾迹的整体速度", motion.speed, 0.08f..8f) {
                        ComposeGlassLabState.updateMotion(motion.copy(speed = it))
                    }
                    LabSlider("按压形变", "控制胶囊膨胀、下沉和压入幅度；小按钮会叠加尺寸增强", motion.deformation, 0f..3f) {
                        ComposeGlassLabState.updateMotion(motion.copy(deformation = it))
                    }
                    LabSlider("释放粘度", "越高回弹和反向回落越明显；过高会有橡皮感", motion.rebound, 0f..3f) {
                        ComposeGlassLabState.updateMotion(motion.copy(rebound = it))
                    }
                    LabSlider("点击冲量", "短点击向连续场注入的瞬时能量，不再只靠松手后补动画", motion.tapImpulse, 0f..3f) {
                        ComposeGlassLabState.updateMotion(motion.copy(tapImpulse = it))
                    }
                    LabSlider("释放锁相", "形变、白光和扫光在松手后保持同一释放包络的程度", motion.releaseCohesion, 0f..3f) {
                        ComposeGlassLabState.updateMotion(motion.copy(releaseCohesion = it))
                    }
                    LabSlider("场连续性", "越高越保留上一帧尾场，减少光效和形变硬切", motion.fieldContinuity, 0f..3f) {
                        ComposeGlassLabState.updateMotion(motion.copy(fieldContinuity = it))
                    }
                }
                GlassLabFoldout("尺寸与形状映射", "小按钮增强、长胶囊抑制、圆角和流带", false, state) {
                    ComposeGlassGroupSample(state, "小按钮样本", "专门观察 compactBoost 和长条抑制", GlassRole.Chip, 999, 38.dp)
                    LabSlider("小尺寸增强", "越高小按钮、小卡片越明显；大卡片基本不变", capsule.compactBoost, 0f..2.4f) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(compactBoost = it))
                    }
                    LabSlider("长条横向抑制", "越高长按钮越不左右拉爆；方形按钮几乎不受影响", capsule.elongatedX, 0f..0.9f) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(elongatedX = it))
                    }
                    LabSlider("长条纵向补偿", "越高长按钮上下胶囊感越明显；过高会显得竖向弹", capsule.elongatedY, 0f..0.6f) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(elongatedY = it))
                    }
                    LabSlider("圆角缩放", "普通 Compose 玻璃圆角整体缩放；不影响 Shell OpenGL", style.radius, 18f..86f) {
                        ComposeGlassLabState.update(style.copy(radius = it))
                    }
                    LabSlider("流带权重", "保留给材质流动/缎带感的轻量调参入口", style.ribbon, 0f..1.5f) {
                        ComposeGlassLabState.update(style.copy(ribbon = it))
                    }
                }
                GlassLabFoldout("点击胶囊核心", "短点击、长按、下沉、拖尾和释放回落", false, state) {
                    ComposeGlassGroupSample(state, "点击样本", "短点和长按都用这里判断胶囊体积", GlassRole.Flex, 999, 44.dp)
                    LabSlider("基础像素形变", "真实像素膨胀基准，控制按压/长按的胶囊体积", capsule.basePx, 0.005f..0.085f) {
                        ComposeGlassLabState.updateCapsuleTuning(capsule.copy(basePx = it))
                    }
                    LabSlider("短点击冲量", "点一下时额外鼓起的胶囊体积，优先调这个找点击手感", capsule.tapPx, 0f..0.12f) {
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
                GlassLabFoldout("白光光场与释放尾迹", "触点白光、扩散、余辉、扫光惯性和色散", false, state) {
                    ComposeGlassGroupSample(state, "白光样本", "观察触点光、扫光和尾迹消散", GlassRole.Card, 24, 60.dp)
                    LabSlider("触点白光", "控制触点附近的连续体积白光与青白捕光", motion.touchLight, 0f..3f) {
                        ComposeGlassLabState.updateMotion(motion.copy(touchLight = it))
                    }
                    LabSlider("白光扩散", "控制按下后光场沿组件内部扩散的强度和扫光尾迹", motion.sweep, 0f..3f) {
                        ComposeGlassLabState.updateMotion(motion.copy(sweep = it))
                    }
                    LabSlider("松手余辉", "控制透镜亮度和光场在松手后的消散时间", motion.afterglow, 0f..3f) {
                        ComposeGlassLabState.updateMotion(motion.copy(afterglow = it))
                    }
                    LabSlider("扫光惯性", "扫光相位在连续场中的保留程度，越高越像光在玻璃里滑过去", motion.sweepMomentum, 0f..3f) {
                        ComposeGlassLabState.updateMotion(motion.copy(sweepMomentum = it))
                    }
                    LabSlider("棱彩色散", "当前普通玻璃 normalized 后保持白光为主，此项保留为未来色散入口", motion.prism, 0f..1.5f) {
                        ComposeGlassLabState.updateMotion(motion.copy(prism = it))
                    }
                }
                GlassLabFoldout("背景采样与雾面材质", "背景透明、模糊、压暗、乳白和高光", false, state) {
                    ComposeGlassGroupSample(state, "雾面样本", "观察背景透出和乳白厚度", GlassRole.Card, 26, 62.dp)
                    LabSlider("背景透明", "普通玻璃采样背景的整体可见度", style.backdrop, 0.12f..1.55f) {
                        ComposeGlassLabState.update(style.copy(backdrop = it))
                    }
                    LabSlider("背景模糊缩放", "普通玻璃背景 blur 半径缩放", style.backdropBlur, 0.35f..2.2f) {
                        ComposeGlassLabState.update(style.copy(backdropBlur = it))
                    }
                    LabSlider("背景压暗", "提高会让玻璃内部背景更沉、更稳", style.backdropDim, 0f..1.6f) {
                        ComposeGlassLabState.update(style.copy(backdropDim = it))
                    }
                    LabSlider("背景乳白", "提高会让雾面更白、更厚", style.backdropMilk, 0f..1.6f) {
                        ComposeGlassLabState.update(style.copy(backdropMilk = it))
                    }
                    LabSlider("背景高光", "提高背景上的浮光亮度", style.backdropHighlight, 0f..1.6f) {
                        ComposeGlassLabState.update(style.copy(backdropHighlight = it))
                    }
                }
                GlassLabFoldout("玻璃体积与暗部", "主体厚度、吸收、内层过渡和底部重量", false, state) {
                    ComposeGlassGroupSample(state, "体积样本", "看主体厚度、底部暗部和内层过渡", GlassRole.Card, 28, 66.dp)
                    LabSlider("静默强度", "越高整体越收敛、越不刺眼", style.quiet, 0.25f..2.2f) {
                        ComposeGlassLabState.update(style.copy(quiet = it))
                    }
                    LabSlider("主体吸收", "控制玻璃身体对背景的吸收感", style.bodyAbsorption, 0f..1.6f) {
                        ComposeGlassLabState.update(style.copy(bodyAbsorption = it))
                    }
                    LabSlider("下半体积", "控制玻璃下半部分的厚重暗部", style.lowerBodyMass, 0f..1.6f) {
                        ComposeGlassLabState.update(style.copy(lowerBodyMass = it))
                    }
                    LabSlider("内层过渡", "控制内边缘和中心区域之间的过渡层", style.innerTransition, 0f..2.8f) {
                        ComposeGlassLabState.update(style.copy(innerTransition = it))
                    }
                    LabSlider("底部压暗", "控制底边和下方暗部重量", style.bottomMass, 0.02f..3f) {
                        ComposeGlassLabState.update(style.copy(bottomMass = it))
                    }
                }
                GlassLabFoldout("边缘、高光与轮廓", "顶部高光、底部亮线、外轮廓和侧向补光", false, state) {
                    ComposeGlassGroupSample(state, "边缘样本", "观察上沿高光、底线和外轮廓", GlassRole.Floating, 28, 58.dp)
                    LabSlider("顶部高光", "上沿白光强度，也是 edge 派生值", style.topLight, 0.02f..3.4f) {
                        ComposeGlassLabState.update(style.copy(topLight = it))
                    }
                    LabSlider("顶部宽度", "上沿高光带宽度 dp", style.topWidthDp, 0.05f..8f) {
                        ComposeGlassLabState.update(style.copy(topWidthDp = it))
                    }
                    LabSlider("顶部变化", "上沿高光横向流动变化", style.topVariation, 0f..3f) {
                        ComposeGlassLabState.update(style.copy(topVariation = it))
                    }
                    LabSlider("底部亮线", "底部边缘亮度", style.bottomLight, 0f..3f) {
                        ComposeGlassLabState.update(style.copy(bottomLight = it))
                    }
                    LabSlider("底部宽度", "底部亮线和暗部边界宽度 dp", style.bottomWidthDp, 0.05f..8f) {
                        ComposeGlassLabState.update(style.copy(bottomWidthDp = it))
                    }
                    LabSlider("外轮廓", "普通玻璃最外侧 rim 亮度和描边权重", style.outerRim, 0.02f..3.2f) {
                        ComposeGlassLabState.update(style.copy(outerRim = it))
                    }
                    LabSlider("侧向补光", "左右侧边的轻微承光", style.sideLight, 0f..2f) {
                        ComposeGlassLabState.update(style.copy(sideLight = it))
                    }
                }
                LabActionButton(
                    title = "恢复 Compose 光动效默认值",
                    subtitle = "同时恢复材质、动效和胶囊细调参数",
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { ComposeGlassLabState.resetAll() },
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
private fun ComposeGlassMotionPreview(state: AssistantUiState) {
    val chipShape = RoundedCornerShape(999.dp)
    val cardShape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
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
                    "轻量预览",
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
                    Text("桥接样本", color = Color.White.copy(alpha = 0.90f), fontSize = 13.sp, fontWeight = FontWeight.Black)
                    Text("快速看速度和光效趋势", color = Color.White.copy(alpha = 0.44f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            "真实 PressableGlass 样本：用于观察小按钮、长胶囊和卡片在当前参数下的实际手感。",
            color = Color.White.copy(alpha = 0.46f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PressableGlass(
                quality = state.quality,
                glassIntensity = state.glassIntensity,
                motionIntensity = state.motionIntensity,
                radius = 999,
                role = GlassRole.Chip,
                modifier = Modifier.weight(0.78f).height(38.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("小按钮", color = Color.White.copy(alpha = 0.90f), fontSize = 10.5.sp, fontWeight = FontWeight.Black)
                }
            }
            PressableGlass(
                quality = state.quality,
                glassIntensity = state.glassIntensity,
                motionIntensity = state.motionIntensity,
                radius = 999,
                role = GlassRole.Flex,
                modifier = Modifier.weight(1.26f).height(42.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("长胶囊按钮", color = Color.White.copy(alpha = 0.90f), fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        PressableGlass(
            quality = state.quality,
            glassIntensity = state.glassIntensity,
            motionIntensity = state.motionIntensity,
            radius = 26,
            role = GlassRole.Card,
            modifier = Modifier.fillMaxWidth().height(76.dp),
        ) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("真实卡片样本", color = Color.White.copy(alpha = 0.92f), fontSize = 13.5.sp, fontWeight = FontWeight.Black)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("速度", ComposeGlassLabState.motionStyle.speed, Modifier.weight(1f))
                    Metric("冲量", ComposeGlassLabState.motionStyle.tapImpulse, Modifier.weight(1f))
                    Metric("连续", ComposeGlassLabState.motionStyle.fieldContinuity, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ComposeGlassGroupSample(
    state: AssistantUiState,
    title: String,
    subtitle: String,
    role: GlassRole,
    radius: Int,
    height: Dp,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = radius,
        role = role,
        modifier = Modifier.fillMaxWidth().height(height),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 12.5.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = Color.White.copy(alpha = 0.46f), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Metric("速", ComposeGlassLabState.motionStyle.speed, Modifier.weight(0.38f))
            Metric("连", ComposeGlassLabState.motionStyle.fieldContinuity, Modifier.weight(0.38f))
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
    GlassLabFoldout("旧样本参数", "只影响这一栏旧样本", false, state) {
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
    openGlVisibility = 19.954f,
    openGlMaxAlpha = 1f,
    openGlPullScale = -5.53f,
    openGlCompressionScale = -10f,
    openGlCornerScale = 54.378f,
    openGlDarkScale = -2.21f,
    openGlSampleRadiusScale = 66.359f
)

private fun composeGlassLabExportText(
    style: ComposeGlassStyle,
    motion: ComposeGlassMotionStyle,
    capsule: OrdinaryGlassCapsuleTuning,
): String = buildString {
    appendLine("AI Ledger Compose Glass Lab Export")
    appendLine("preset=${style.preset}")
    appendLine()
    appendLine("[motion]")
    appendGlassValue("master", motion.master)
    appendGlassValue("speed", motion.speed)
    appendGlassValue("deformation", motion.deformation)
    appendGlassValue("rebound", motion.rebound)
    appendGlassValue("tapImpulse", motion.tapImpulse)
    appendGlassValue("releaseCohesion", motion.releaseCohesion)
    appendGlassValue("fieldContinuity", motion.fieldContinuity)
    appendGlassValue("touchLight", motion.touchLight)
    appendGlassValue("sweep", motion.sweep)
    appendGlassValue("sweepMomentum", motion.sweepMomentum)
    appendGlassValue("afterglow", motion.afterglow)
    appendGlassValue("prism", motion.prism)
    appendLine()
    appendLine("[capsule]")
    appendGlassValue("compactBoost", capsule.compactBoost)
    appendGlassValue("elongatedX", capsule.elongatedX)
    appendGlassValue("elongatedY", capsule.elongatedY)
    appendGlassValue("basePx", capsule.basePx)
    appendGlassValue("tapPx", capsule.tapPx)
    appendGlassValue("tapPop", capsule.tapPop)
    appendGlassValue("tapCarry", capsule.tapCarry)
    appendGlassValue("sticky", capsule.sticky)
    appendGlassValue("sink", capsule.sink)
    appendGlassValue("settle", capsule.settle)
    appendLine()
    appendLine("[style]")
    appendGlassValue("backdrop", style.backdrop)
    appendGlassValue("backdropBlur", style.backdropBlur)
    appendGlassValue("backdropDim", style.backdropDim)
    appendGlassValue("backdropMilk", style.backdropMilk)
    appendGlassValue("backdropHighlight", style.backdropHighlight)
    appendGlassValue("quiet", style.quiet)
    appendGlassValue("bodyAbsorption", style.bodyAbsorption)
    appendGlassValue("lowerBodyMass", style.lowerBodyMass)
    appendGlassValue("innerTransition", style.innerTransition)
    appendGlassValue("topLight", style.topLight)
    appendGlassValue("topWidthDp", style.topWidthDp)
    appendGlassValue("topVariation", style.topVariation)
    appendGlassValue("bottomLight", style.bottomLight)
    appendGlassValue("bottomWidthDp", style.bottomWidthDp)
    appendGlassValue("outerRim", style.outerRim)
    appendGlassValue("bottomMass", style.bottomMass)
    appendGlassValue("sideLight", style.sideLight)
    appendGlassValue("radius", style.radius)
    appendGlassValue("ribbon", style.ribbon)
}

private fun StringBuilder.appendGlassValue(name: String, value: Float) {
    append(name)
    append('=')
    appendLine(String.format(Locale.US, "%.4f", value))
}

@Composable
private fun Metric(label: String, value: Float, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.48f), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        Text(((value * 100f).roundToInt() / 100f).toString(), color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.model.WorkflowDraftStatus
import kotlin.math.abs

private val SkillNoFontPadding = PlatformTextStyle(includeFontPadding = false)
private val SkillPanelWidth = 292.dp
private val SkillPanelHeight = 252.dp
private val SkillPanelMinHeight = 196.dp
private val SkillTailHeight = 12.dp
private val SkillTailHalfWidth = 15.dp

@Stable
private object SkillQuickOverlayState {
    var expanded by mutableStateOf(false)
    var anchorBounds by mutableStateOf(Rect.Zero)

    fun updateAnchor(next: Rect) {
        val current = anchorBounds
        if (
            abs(current.left - next.left) > 0.5f ||
            abs(current.top - next.top) > 0.5f ||
            abs(current.right - next.right) > 0.5f ||
            abs(current.bottom - next.bottom) > 0.5f
        ) {
            anchorBounds = next
        }
    }

    fun toggle() {
        expanded = !expanded
    }

    fun dismiss() {
        expanded = false
    }
}

@Composable
internal fun SkillQuickPanelButtonHost(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(32.dp)
            .height(22.dp)
            .onGloballyPositioned { coordinates ->
                SkillQuickOverlayState.updateAnchor(coordinates.boundsInWindow())
            },
        contentAlignment = Alignment.Center,
    ) {
        SkillCubeButton(
            expanded = SkillQuickOverlayState.expanded,
            onClick = SkillQuickOverlayState::toggle,
        )
    }
}

@Composable
internal fun SkillQuickPanelSameWindowOverlayHost() {
    val viewModel: OperationLearningViewModel = viewModel()
    val uiState = viewModel.uiState
    val usableDrafts = remember(uiState.drafts, uiState.skillArtifacts) {
        uiState.drafts.filter { draft ->
            draft.status == WorkflowDraftStatus.Approved || draft.status == WorkflowDraftStatus.Verified ||
                uiState.skillArtifacts.containsKey(draft.id)
        }
    }
    var panelPrecomposed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        panelPrecomposed = true
        viewModel.refresh()
    }

    AnchoredQuickPanel(
        visible = SkillQuickOverlayState.expanded,
        anchorBounds = SkillQuickOverlayState.anchorBounds,
        desiredWidth = SkillPanelWidth,
        desiredHeight = SkillPanelHeight,
        minHeight = SkillPanelMinHeight,
        preferredPlacement = AnchoredQuickPanelPlacement.Above,
        horizontalBias = 0.80f,
        quality = RenderQuality.Balanced,
        glassIntensity = 1.04f,
        motionIntensity = 0.72f,
        onDismiss = SkillQuickOverlayState::dismiss,
        cornerRadius = 25.dp,
        tailHeight = SkillTailHeight,
        tailHalfWidth = SkillTailHalfWidth,
        precomposeWhenHidden = panelPrecomposed,
    ) { layout ->
        SkillPanelContent(
            uiState = uiState,
            compact = layout.compact,
            onRefresh = viewModel::refresh,
            onOpenLearning = {
                SkillQuickOverlayState.dismiss()
                viewModel.openIntentEditor()
            },
            onRun = { draftId ->
                SkillQuickOverlayState.dismiss()
                viewModel.runSkill(draftId)
            },
        )
    }
}

@Composable
private fun SkillCubeButton(expanded: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(150),
        label = "skill-cube-active",
    )
    val press by animateFloatAsState(
        targetValue = when {
            pressed -> 1f
            expanded -> 0.34f
            else -> 0f
        },
        animationSpec = spring(0.70f, Spring.StiffnessMedium),
        label = "skill-cube-press",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = 1f - press * 0.055f
                scaleY = 1f + press * 0.045f
                translationY = press * 0.72.dp.toPx()
            }
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.066f + active * 0.025f),
                        Color(0xFFFFD66E).copy(alpha = 0.036f + active * 0.070f),
                        Color(0xFFB388FF).copy(alpha = 0.040f + active * 0.075f),
                    ),
                ),
            )
            .border(
                0.7.dp,
                Color.White.copy(alpha = 0.09f + active * 0.045f),
                RoundedCornerShape(999.dp),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.095f - press * 0.035f), Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
                size = Size(size.width, size.height * 0.55f),
                cornerRadius = CornerRadius(size.height / 2f),
            )
            drawSkillCubeGlyph(active = active, press = press)
        }
    }
}

@Composable
private fun SkillPanelContent(
    uiState: OperationLearningUiState,
    compact: Boolean,
    onRefresh: () -> Unit,
    onOpenLearning: () -> Unit,
    onRun: (String) -> Unit,
) {
    val skills = uiState.drafts.filter { draft ->
        draft.status == WorkflowDraftStatus.Approved || draft.status == WorkflowDraftStatus.Verified ||
            uiState.skillArtifacts.containsKey(draft.id)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (compact) 11.dp else 13.dp, vertical = if (compact) 10.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(if (compact) 30.dp else 34.dp)
                    .clip(RoundedCornerShape(if (compact) 11.dp else 13.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color(0xFFFFD66E).copy(alpha = 0.11f),
                                Color(0xFFB388FF).copy(alpha = 0.14f),
                            ),
                        ),
                    )
                    .border(0.7.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(if (compact) 11.dp else 13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(if (compact) 16.dp else 18.dp)) { drawSkillCubeGlyph(1f, 0f) }
            }
            Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Skill", color = Color.White.copy(alpha = 0.96f), fontSize = if (compact) 15.sp else 17.sp, fontWeight = FontWeight.Black, style = TextStyle(platformStyle = SkillNoFontPadding))
                Text(
                    if (skills.isEmpty()) "还没有可运行的视觉 Skill" else "可运行 ${skills.size} 个 · 点击立即执行",
                    color = Color.White.copy(alpha = 0.40f),
                    fontSize = if (compact) 8.sp else 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(platformStyle = SkillNoFontPadding),
                )
            }
            SkillSmallAction("刷新", onRefresh)
        }
        if (skills.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.040f))
                    .clickable(onClick = onOpenLearning)
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("先到功能页录制并批准一个 Skill", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
            ) {
                items(skills, key = { it.id }) { draft ->
                    val skill = uiState.skillArtifacts[draft.id]
                    SkillRunCard(
                        title = skill?.name?.takeIf(String::isNotBlank) ?: draft.title,
                        subtitle = skill?.description?.takeIf(String::isNotBlank) ?: draft.goal,
                        approved = draft.status == WorkflowDraftStatus.Approved || draft.status == WorkflowDraftStatus.Verified,
                        running = uiState.runningSkillId == draft.id,
                        compact = compact,
                        onClick = { onRun(draft.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillRunCard(
    title: String,
    subtitle: String,
    approved: Boolean,
    running: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compact) 14.dp else 17.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = if (approved) 0.060f else 0.034f),
                        Color(0xFFFFD66E).copy(alpha = if (approved) 0.040f else 0.018f),
                        Color.White.copy(alpha = 0.016f),
                    ),
                ),
            )
            .border(0.65.dp, Color(0xFFFFD66E).copy(alpha = if (approved) 0.13f else 0.050f), RoundedCornerShape(if (compact) 14.dp else 17.dp))
            .clickable(enabled = approved && !running, onClick = onClick)
            .padding(horizontal = if (compact) 9.dp else 11.dp, vertical = if (compact) 7.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(999.dp)).background(if (approved) Color(0xFFFFD66E) else Color.White.copy(alpha = 0.30f)))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White.copy(alpha = if (approved) 0.88f else 0.50f), fontSize = if (compact) 10.sp else 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = if (compact) 8.sp else 9.sp, lineHeight = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(7.dp))
        Text(if (running) "运行中" else if (approved) "运行" else "待批准", color = Color(0xFFFFD66E).copy(alpha = if (approved) 0.78f else 0.38f), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SkillSmallAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        color = Color.White.copy(alpha = 0.60f),
        fontSize = 8.5.sp,
        fontWeight = FontWeight.ExtraBold,
        style = TextStyle(platformStyle = SkillNoFontPadding),
    )
}

private fun DrawScope.drawSkillCubeGlyph(active: Float, press: Float) {
    val s = size.minDimension
    val cx = size.width / 2f
    val cy = size.height / 2f + s * (0.015f + press * 0.018f)
    val r = s * 0.30f
    val top = Path().apply {
        moveTo(cx, cy - r)
        lineTo(cx + r * 0.84f, cy - r * 0.48f)
        lineTo(cx, cy + r * 0.02f)
        lineTo(cx - r * 0.84f, cy - r * 0.48f)
        close()
    }
    val left = Path().apply {
        moveTo(cx - r * 0.84f, cy - r * 0.48f)
        lineTo(cx, cy + r * 0.02f)
        lineTo(cx, cy + r * 0.98f)
        lineTo(cx - r * 0.84f, cy + r * 0.46f)
        close()
    }
    val right = Path().apply {
        moveTo(cx + r * 0.84f, cy - r * 0.48f)
        lineTo(cx, cy + r * 0.02f)
        lineTo(cx, cy + r * 0.98f)
        lineTo(cx + r * 0.84f, cy + r * 0.46f)
        close()
    }
    drawPath(top, Brush.linearGradient(listOf(Color(0xFFFFF3A6), Color(0xFFFFB55E))))
    drawPath(left, Brush.linearGradient(listOf(Color(0xFFFF9A72), Color(0xFFB873FF))))
    drawPath(right, Brush.linearGradient(listOf(Color(0xFF8DFFF4), Color(0xFF6A7CFF))))
    drawCircle(Color.White.copy(alpha = 0.16f + active * 0.08f), radius = s * 0.055f, center = Offset(cx - r * 0.30f, cy - r * 0.38f))
}

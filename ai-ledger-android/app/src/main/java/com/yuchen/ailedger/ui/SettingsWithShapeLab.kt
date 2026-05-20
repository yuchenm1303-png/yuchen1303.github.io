package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.RenderQuality

@Composable
fun SettingsScreenWithShapeLab(
    state: AssistantUiState,
    aiEndpoint: String,
    onQualityChange: (RenderQuality) -> Unit,
    onPreviewConversationChange: (Boolean) -> Unit,
    onGlassPresetChange: (GlassPreset) -> Unit,
    onBackgroundThemeChange: (BackgroundTheme) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit,
    onBackdropChange: (BackdropDebugParams) -> Unit,
    onBorderChange: (GlassBorderStyle) -> Unit,
    onUploadBackgroundClick: () -> Unit,
    onClearCustomBackgroundClick: () -> Unit
) {
    var shapeLabOpen by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = !shapeLabOpen,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = 0.98f, animationSpec = spring(dampingRatio = 0.76f)),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.99f, animationSpec = tween(120))
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShapeLabEntryCard(state = state, onOpen = { shapeLabOpen = true })
                Box(Modifier.weight(1f)) {
                    SettingsScreenV2(
                        state = state,
                        aiEndpoint = aiEndpoint,
                        onQualityChange = onQualityChange,
                        onPreviewConversationChange = onPreviewConversationChange,
                        onGlassPresetChange = onGlassPresetChange,
                        onBackgroundThemeChange = onBackgroundThemeChange,
                        onGlassIntensityChange = onGlassIntensityChange,
                        onMotionIntensityChange = onMotionIntensityChange,
                        onBackdropChange = onBackdropChange,
                        onBorderChange = onBorderChange,
                        onUploadBackgroundClick = onUploadBackgroundClick,
                        onClearCustomBackgroundClick = onClearCustomBackgroundClick
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = shapeLabOpen,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = 0.97f, animationSpec = spring(dampingRatio = 0.74f)),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.99f, animationSpec = tween(120))
        ) {
            GlassShapeLabScreenV3(
                state = state,
                onBack = { shapeLabOpen = false }
            )
        }
    }
}

@Composable
private fun ShapeLabEntryCard(state: AssistantUiState, onOpen: () -> Unit) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.98f,
        motionIntensity = state.motionIntensity,
        radius = 28,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(top = 2.dp)
            .shapeEntryGlow(),
        role = GlassRole.Card,
        onClick = onOpen
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 15.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("玻璃形态预览", color = Color.White.copy(alpha = 0.96f), fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text("V3 复用老 Compose 玻璃内核，滑块范围更大，并修掉矩形分区线。", color = Color.White.copy(alpha = 0.50f), fontSize = 10.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("进入 Shape Lab V3 ›", color = Color(0xFF8DF9EA).copy(alpha = 0.78f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.End))
        }
    }
}

private fun Modifier.shapeEntryGlow(): Modifier = drawWithCache {
    val glow = Brush.radialGradient(
        listOf(Color(0xFF8DF9EA).copy(alpha = 0.12f), Color(0xFF9EB7FF).copy(alpha = 0.08f), Color.Transparent),
        center = Offset(size.width * 0.14f, size.height * 0.10f),
        radius = size.maxDimension * 0.86f
    )
    onDrawWithContent {
        drawRect(glow, blendMode = BlendMode.Screen)
        drawContent()
    }
}

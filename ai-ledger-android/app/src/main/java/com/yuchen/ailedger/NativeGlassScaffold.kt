package com.yuchen.ailedger

import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun AiLedgerNativeShell(
    createWebView: ((GlassMode) -> Unit) -> WebView,
    nativeMessages: List<NativeChatMessage>,
    onNavSelected: (String) -> Unit,
    onHaptic: (String) -> Unit,
    onPromptSubmit: (String) -> Unit,
) {
    var selectedView by remember { mutableStateOf("ai") }
    var glassMode by remember { mutableStateOf(GlassMode.Safe) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF07142E),
                        Color(0xFF102545),
                        Color(0xFF07101F),
                    ),
                ),
            ),
    ) {
        AmbientBackground()

        AndroidView(
            factory = { createWebView { mode -> glassMode = mode } },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (selectedView == "ai") 0.001f else 1f }
                .padding(bottom = if (selectedView == "ai") 156.dp else 84.dp),
        )

        if (selectedView == "ai") {
            NativeChatPanel(
                messages = nativeMessages,
                modifier = Modifier.fillMaxSize(),
            )
        }

        NativeTopBadge(
            glassMode = glassMode,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(top = 8.dp, end = 14.dp),
        )

        AnimatedVisibility(
            visible = selectedView == "ai",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(horizontal = 16.dp)
                .padding(bottom = 88.dp),
        ) {
            NativeLiquidComposer(
                onHaptic = onHaptic,
                onSubmit = onPromptSubmit,
            )
        }

        NativeGlassBottomNav(
            selectedView = selectedView,
            glassMode = glassMode,
            onSelected = { item ->
                selectedView = item.view
                onHaptic("tick")
                onNavSelected(item.view)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun AmbientBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 0.42f }
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0x666AD7FF),
                        Color.Transparent,
                    ),
                    radius = 760f,
                ),
            ),
    )
}

@Composable
private fun NativeTopBadge(
    glassMode: GlassMode,
    modifier: Modifier = Modifier,
) {
    val label = when (glassMode) {
        GlassMode.Basic -> "原生玻璃 Basic"
        GlassMode.Blur -> "原生玻璃 Blur"
        GlassMode.Liquid -> "原生玻璃 Liquid"
        GlassMode.Safe -> "流畅优先 Safe"
    }

    Surface(
        modifier = modifier.shadow(6.dp, CircleShape, clip = false),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun NativeLiquidComposer(
    onHaptic: (String) -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val canSend = text.trim().isNotEmpty()
    val sendAlpha by animateFloatAsState(
        targetValue = if (canSend) 1f else 0.46f,
        animationSpec = tween(durationMillis = 120),
        label = "composerSendAlpha",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .shadow(14.dp, RoundedCornerShape(25.dp), clip = false),
        shape = RoundedCornerShape(25.dp),
        color = Color.White.copy(alpha = 0.17f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color.White.copy(alpha = 0.055f),
                            Color(0x1D6AD7FF),
                        ),
                    ),
                )
                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isBlank()) {
                    Text(
                        text = "直接说：设闹钟、导航、记账、查一下…",
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 14.sp,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(180) },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White.copy(alpha = 0.94f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    cursorBrush = SolidColor(Color.White.copy(alpha = 0.86f)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            val clean = text.trim()
                            if (clean.isNotEmpty()) {
                                onHaptic("tick")
                                onSubmit(clean)
                                text = ""
                            }
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(44.dp)
                    .graphicsLayer { alpha = sendAlpha }
                    .clip(RoundedCornerShape(17.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xAA8BF7FF),
                                Color(0xBB5C8DFF),
                            ),
                        ),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(17.dp))
                    .clickable(enabled = canSend) {
                        val clean = text.trim()
                        if (clean.isNotEmpty()) {
                            onHaptic("tick")
                            onSubmit(clean)
                            text = ""
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "➤",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

private data class NativeNavItem(
    val view: String,
    val icon: String,
    val label: String,
)

@Composable
private fun NativeGlassBottomNav(
    selectedView: String,
    glassMode: GlassMode,
    onSelected: (NativeNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        NativeNavItem("ai", "✦", "AI"),
        NativeNavItem("tools", "▦", "功能"),
        NativeNavItem("settings", "⚙", "设置"),
    )
    val selectedIndex = items.indexOfFirst { it.view == selectedView }.coerceAtLeast(0)
    val navShape = RoundedCornerShape(26.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .shadow(12.dp, navShape, clip = false),
        shape = navShape,
        color = Color.White.copy(alpha = if (glassMode == GlassMode.Safe) 0.16f else 0.20f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color.White.copy(alpha = 0.06f),
                            Color(0x1A6AD7FF),
                        ),
                    ),
                ),
        ) {
            val innerWidth = maxWidth - 16.dp
            val itemWidth = innerWidth / items.size.toFloat()
            val indicatorWidth = itemWidth - 6.dp
            val indicatorOffsetX by animateDpAsState(
                targetValue = 8.dp + itemWidth * selectedIndex.toFloat() + 3.dp,
                animationSpec = tween(durationMillis = 170),
                label = "nativeLiquidIndicatorX",
            )

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffsetX, y = 8.dp)
                    .width(indicatorWidth)
                    .height(52.dp)
                    .shadow(8.dp, RoundedCornerShape(21.dp), clip = false)
                    .clip(RoundedCornerShape(21.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (glassMode == GlassMode.Safe) 0.24f else 0.33f),
                                Color(0x556AD7FF),
                                Color.White.copy(alpha = 0.13f),
                            ),
                        ),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.26f), RoundedCornerShape(21.dp)),
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    NativeGlassNavButton(
                        item = item,
                        selected = selectedView == item.view,
                        onClick = { onSelected(item) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NativeGlassNavButton(
    item: NativeNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 0.985f,
        animationSpec = tween(durationMillis = 130),
        label = "navScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.72f,
        animationSpec = tween(durationMillis = 110),
        label = "navAlpha",
    )

    Box(
        modifier = modifier
            .padding(horizontal = 3.dp)
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(RoundedCornerShape(21.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = item.icon,
                color = Color.White,
                fontSize = if (selected) 18.sp else 16.sp,
                fontWeight = FontWeight.Bold,
            )
            if (selected) {
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = item.label,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

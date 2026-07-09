package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.service.BackendEndpointMode
import com.yuchen.ailedger.service.BackendEndpointStore
import kotlin.math.roundToInt

internal val SettingsChipRole = GlassRole.Chip
internal val SettingsFloatingRole = GlassRole.Floating

@Composable
internal fun SettingsIconBadge(
    text: String,
    active: Boolean,
    glow: Float = if (active) 1f else 0f,
) {
    Box(
        Modifier
            .size(42.dp)
            .graphicsLayer {
                scaleX = 1f + glow * 0.030f
                scaleY = 1f + glow * 0.030f
            }
            .clip(RoundedCornerShape(15.dp))
            .background(
                Color.White.copy(
                    alpha = if (active) {
                        0.105f + glow * 0.025f
                    } else {
                        0.055f + glow * 0.040f
                    }
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color.White.copy(
                alpha = if (active) 0.94f else 0.66f + glow * 0.20f
            ),
            fontSize = if (text.length > 1) 13.sp else 17.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun SettingsLabEntry(
    state: AssistantUiState,
    selected: Boolean,
    onClick: () -> Unit,
) {
    PressableGlass(
        state.quality,
        state.glassIntensity * if (selected) 0.92f else 0.76f,
        state.motionIntensity,
        26,
        Modifier.fillMaxWidth().height(62.dp),
        SettingsChipRole,
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsIconBadge("⚗", selected)
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "玻璃实验室",
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                Text(
                    "高级调试与实验功能",
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Text(
                if (selected) "已打开" else "进入",
                color = Color.White.copy(alpha = 0.52f),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun SectionTitleInline(title: String) {
    Text(
        title,
        color = Color.White.copy(alpha = 0.82f),
        fontSize = 15.sp,
        fontWeight = FontWeight.Black,
    )
}

@Composable
internal fun <T> SettingChipGrid(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    state: AssistantUiState,
    onSelected: (T) -> Unit,
) {
    items.chunked(2).forEach { row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            row.forEach { item ->
                val active = item == selected
                PressableGlass(
                    state.quality,
                    state.glassIntensity,
                    state.motionIntensity,
                    999,
                    Modifier.weight(1f).height(42.dp),
                    if (active) SettingsFloatingRole else SettingsChipRole,
                    onClick = { onSelected(item) },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            label(item),
                            color = Color.White.copy(alpha = if (active) 0.96f else 0.62f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun SliderSettingRow(
    label: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    InsetGlassParameterSlider(
        title = label,
        description = description,
        value = value,
        valueRange = range,
        onValueChange = onValueChange,
        valueText = "${value.formatSettingValue()}×",
    )
}

@Composable
internal fun SettingActionButton(
    title: String,
    subtitle: String,
    state: AssistantUiState,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    PressableGlass(
        state.quality,
        state.glassIntensity,
        state.motionIntensity,
        23,
        modifier.height(58.dp),
        SettingsChipRole,
        onClick = onClick,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.52f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun SettingInfoRow(title: String, value: String) {
    if (title == "AI 接口") {
        BackendEndpointSettingRow(value)
        return
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.060f))
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Text(
            value,
            color = Color.White.copy(alpha = 0.56f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun BackendEndpointSettingRow(endpointValue: String) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { BackendEndpointStore.get(context) }
    val backendState by store.state.collectAsState()
    val endpointText = backendState.endpoint.ifBlank { endpointValue }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.060f))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "后端环境",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 14.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                Text(
                    text = "当前：${backendState.label} · ${backendState.mode.description}",
                    color = Color.White.copy(alpha = 0.44f),
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = endpointText.removePrefix("https://").removePrefix("http://"),
                color = Color.White.copy(alpha = 0.38f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1.08f),
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BackendEndpointMode.entries.forEach { mode ->
                val active = mode == backendState.mode
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (active) Color(0xFF8DF9EA).copy(alpha = 0.145f)
                            else Color.White.copy(alpha = 0.065f)
                        )
                        .clickable { store.setMode(mode) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (active) "✓ ${mode.shortLabel}" else mode.shortLabel,
                        color = Color.White.copy(alpha = if (active) 0.94f else 0.58f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Text(
            text = "这里只切换请求地址；Qwen / DeepSeek 由对应后端文件自己决定。",
            color = Color.White.copy(alpha = 0.34f),
            fontSize = 9.8.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun MiniSettingMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.070f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.50f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            value,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SettingsHairline(alpha: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = alpha))
    )
}

@Composable
internal fun SettingsDivider() {
    Box(
        Modifier
            .size(1.dp, 38.dp)
            .background(Color.White.copy(alpha = 0.10f))
    )
}

internal fun qualityLabel(quality: RenderQuality): String = when (quality) {
    RenderQuality.Smooth -> "流畅"
    RenderQuality.Balanced -> "均衡"
    RenderQuality.Experimental -> "高画质"
}

internal fun glassPresetLabel(preset: GlassPreset): String = when (preset) {
    GlassPreset.Basic -> "基础"
    GlassPreset.Blur -> "模糊"
    GlassPreset.Liquid -> "液态"
    GlassPreset.Safe -> "安全"
}

internal fun themeLabel(theme: BackgroundTheme): String = when (theme) {
    BackgroundTheme.Aurora -> "极光"
    BackgroundTheme.Jade -> "翡翠"
    BackgroundTheme.Sunset -> "暮色"
    BackgroundTheme.Dawn -> "晨雾"
}

private fun Float.formatSettingValue(): String =
    (this * 100).roundToInt().div(100f).toString()

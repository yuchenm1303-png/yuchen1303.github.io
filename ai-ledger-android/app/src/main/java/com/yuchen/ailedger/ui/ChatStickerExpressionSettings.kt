package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun InlineStickerExpressionSettingsControls() {
    val context = LocalContext.current
    val preferences = InlineStickerDisplaySettings.expressionPreferences(context)

    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text(
            text = "模型表情表达",
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Black
        )
        InsetGlassParameterSlider(
            title = "表情发送频率",
            description = "控制模型在普通聊天中主动使用表情的频繁程度；用户明确要求时仍优先服从当前指令。",
            value = preferences.frequency.toFloat(),
            valueRange = InlineStickerDisplaySettings.FrequencyRange,
            onValueChange = { InlineStickerDisplaySettings.updateFrequency(context, it) },
            valueText = stickerFrequencyLabel(preferences.frequency)
        )
        InsetGlassParameterSlider(
            title = "表情表达强度",
            description = "控制模型使用表情时的丰富程度、位置变化和组合倾向，最高可调至强烈。",
            value = preferences.intensity.toFloat(),
            valueRange = InlineStickerDisplaySettings.IntensityRange,
            onValueChange = { InlineStickerDisplaySettings.updateIntensity(context, it) },
            valueText = stickerIntensityLabel(preferences.intensity)
        )
        InsetGlassParameterSlider(
            title = "单条回复表情上限",
            description = "限制模型在一条普通回复中自主发送的表情总数；设为不限时保持当前版本的无限上限。",
            value = preferences.maxPerReply.toFloat(),
            valueRange = InlineStickerDisplaySettings.MaxPerReplyRange,
            onValueChange = { InlineStickerDisplaySettings.updateMaxPerReply(context, it) },
            valueText = stickerMaxPerReplyLabel(preferences.maxPerReply)
        )
        InsetGlassParameterSlider(
            title = "连续重复表情",
            description = "允许模型在同一位置连续重复发送相同表情，范围为 1～4 张，由模型结合语境决定是否使用。",
            value = preferences.repeatCount.toFloat(),
            valueRange = InlineStickerDisplaySettings.RepeatCountRange,
            onValueChange = { InlineStickerDisplaySettings.updateRepeatCount(context, it) },
            valueText = "${preferences.repeatCount} 张"
        )
        Text(
            text = "这些设置从下一次云端回复开始生效；默认值保持当前版本的自主表达方式，不在本地随机选择、复制或截断表情。",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun stickerFrequencyLabel(value: Int): String = when {
    value <= 0 -> "关闭"
    value < 25 -> "很少 · $value"
    value < 45 -> "较少 · $value"
    value <= 55 -> "当前默认 · $value"
    value < 75 -> "较多 · $value"
    else -> "高频 · $value"
}

private fun stickerIntensityLabel(value: Int): String = when {
    value < 25 -> "克制 · $value"
    value < 45 -> "轻度 · $value"
    value <= 55 -> "当前默认 · $value"
    value < 75 -> "丰富 · $value"
    else -> "强烈 · $value"
}

private fun stickerMaxPerReplyLabel(value: Int): String =
    if (value <= 0) "不限" else "$value 张"

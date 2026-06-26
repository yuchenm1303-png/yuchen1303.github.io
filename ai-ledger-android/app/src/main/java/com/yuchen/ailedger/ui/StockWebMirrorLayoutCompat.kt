package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Modifier

/**
 * 仅为网页镜像终端中两个跨 Composable 边界的末级内容提供“占满剩余高度”语义。
 * RowScope/ColumnScope 内原生 weight 成员扩展优先级更高，不会受此扩展影响。
 */
internal fun Modifier.weight(weight: Float): Modifier =
    if (weight > 0f) fillMaxHeight() else this

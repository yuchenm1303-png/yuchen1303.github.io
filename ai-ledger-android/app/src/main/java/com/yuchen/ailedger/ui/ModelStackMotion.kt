package com.yuchen.ailedger.ui

import com.yuchen.ailedger.model.ChatModel
import kotlin.math.abs

internal const val MODEL_CARD_COLLAPSED_HEIGHT_DP = 56f
internal const val MODEL_CARD_EXPANDED_HEIGHT_DP = 64f
internal const val MODEL_CARD_ROW_STEP_DP = 74f
internal const val MODEL_CARD_COLLAPSED_BACK_X_STEP_DP = 5f
internal const val MODEL_CARD_COLLAPSED_BACK_Y_STEP_DP = 1.6f
internal const val MODEL_CARD_GAP_DP = 10f
internal const val MODEL_CARD_RESERVED_GAP_DP = 8f
internal const val MODEL_CARD_TRAVEL_NORMALIZER_DP = 220f

internal data class ModelStackGeometryDp(
    val collapsedX: Float,
    val collapsedY: Float,
    val expandedX: Float,
    val expandedY: Float,
    val horizontalMotion: Float,
    val verticalMotion: Float,
    val overshoot: Float
)

internal fun ChatModel.modelExpandedLayoutSlot(): Int = when (this) {
    ChatModel.Auto -> 0
    ChatModel.Kimi -> 2
    ChatModel.Workers -> 4
    ChatModel.Mistral -> 3
    ChatModel.Gemini -> 5
    ChatModel.DeepSeekV4 -> 6
    ChatModel.GptOss -> 7
}

internal fun modelStackGeometryDp(
    availableWidthDp: Float,
    selected: Boolean,
    layoutSlot: Int,
    stackRank: Int
): ModelStackGeometryDp {
    val safeWidth = availableWidthDp.coerceAtLeast(MODEL_CARD_RESERVED_GAP_DP + MODEL_CARD_GAP_DP)
    val collapsedWidth = (safeWidth - MODEL_CARD_RESERVED_GAP_DP) * 0.642f
    val halfWidth = (safeWidth - MODEL_CARD_GAP_DP) / 2f
    val expandedX = if (layoutSlot % 2 == 1) halfWidth + MODEL_CARD_GAP_DP else 0f
    val expandedY = MODEL_CARD_ROW_STEP_DP * (layoutSlot / 2)
    val collapsedX = if (selected) 0f else stackRank * MODEL_CARD_COLLAPSED_BACK_X_STEP_DP
    val collapsedY = if (selected) 0f else stackRank * MODEL_CARD_COLLAPSED_BACK_Y_STEP_DP
    val motionX = expandedX - collapsedX
    val motionY = expandedY - collapsedY
    val travelTotal = abs(motionX) + abs(motionY) + 0.001f
    val distanceWeight = (travelTotal / MODEL_CARD_TRAVEL_NORMALIZER_DP).coerceIn(0.35f, 1f)
    return ModelStackGeometryDp(
        collapsedX = collapsedX,
        collapsedY = collapsedY,
        expandedX = expandedX,
        expandedY = expandedY,
        horizontalMotion = abs(motionX) / travelTotal,
        verticalMotion = abs(motionY) / travelTotal,
        overshoot = 0.040f + 0.020f * distanceWeight
    )
}

internal fun modelStackContactDeltaDp(
    progress: Float,
    expanding: Boolean,
    selectedModel: ChatModel,
    availableWidthDp: Float
): Float {
    val collapsedBottom = modelStackContactBottomDp(
        progress = 0f,
        expanding = true,
        selectedModel = selectedModel,
        availableWidthDp = availableWidthDp
    )
    val expandedBottom = modelStackContactBottomDp(
        progress = 1f,
        expanding = true,
        selectedModel = selectedModel,
        availableWidthDp = availableWidthDp
    )
    val currentBottom = modelStackContactBottomDp(
        progress = progress,
        expanding = expanding,
        selectedModel = selectedModel,
        availableWidthDp = availableWidthDp
    )
    return (currentBottom - collapsedBottom).coerceIn(0f, expandedBottom - collapsedBottom)
}

internal fun modelStackRankedProgress(
    progress: Float,
    expanding: Boolean,
    stackRank: Int,
    totalBackCount: Int,
    visual: Boolean
): Float {
    val safeProgress = progress.coerceIn(0f, 1f)
    if (stackRank <= 0) return safeProgress

    val rank = stackRank.coerceAtLeast(1)
    val delayStep = if (visual) 0.026f else 0.034f
    val collapseStep = if (visual) 0.014f else 0.018f
    val delay = if (expanding) {
        rank * delayStep
    } else {
        rank.coerceAtMost(totalBackCount.coerceAtLeast(1)) * collapseStep
    }.coerceIn(0f, 0.22f)
    val span = (1f - delay).coerceAtLeast(0.58f)
    return ((safeProgress - delay) / span).coerceIn(0f, 1f)
}

internal fun modelStackDockingProgress(phase: Float, overshoot: Float): Float {
    val t = phase.coerceIn(0f, 1f)
    val out = overshoot.coerceIn(0.030f, 0.070f)
    return when {
        t < 0.16f -> modelStackLerpRawFloat(0f, 0.34f, modelStackEaseOutCubic(t / 0.16f))
        t < 0.54f -> modelStackLerpRawFloat(0.34f, 0.88f, modelStackSmoother((t - 0.16f) / 0.38f))
        t < 0.74f -> modelStackLerpRawFloat(0.88f, 1f + out, modelStackEaseOutCubic((t - 0.54f) / 0.20f))
        t < 0.88f -> modelStackLerpRawFloat(1f + out, 1f + out * 0.34f, modelStackSmoother((t - 0.74f) / 0.14f))
        else -> modelStackLerpRawFloat(1f + out * 0.34f, 1f, modelStackSmoother((t - 0.88f) / 0.12f))
    }
}

internal fun modelStackVisualSmooth(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

internal fun modelStackSmoother(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * x * (x * (x * 6f - 15f) + 10f)
}

internal fun modelStackEaseOutCubic(value: Float): Float {
    val x = 1f - value.coerceIn(0f, 1f)
    return 1f - x * x * x
}

internal fun modelStackLerpRawFloat(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}

private fun modelStackContactBottomDp(
    progress: Float,
    expanding: Boolean,
    selectedModel: ChatModel,
    availableWidthDp: Float
): Float {
    val safeProgress = progress.coerceIn(0f, 1f)
    val totalBackCount = (ChatModel.entries.size - 1).coerceAtLeast(1)
    var backRank = 0
    var bottom = 0f
    ChatModel.entries.forEach { model ->
        val selected = model == selectedModel
        val stackRank = if (selected) 0 else ++backRank
        val geometry = modelStackGeometryDp(
            availableWidthDp = availableWidthDp,
            selected = selected,
            layoutSlot = model.modelExpandedLayoutSlot(),
            stackRank = stackRank
        )
        val rawVisual = modelStackRankedProgress(safeProgress, expanding, stackRank, totalBackCount, visual = true)
        val targetProgress = modelStackVisualSmooth(rawVisual)
        val rawMotion = modelStackRankedProgress(safeProgress, expanding, stackRank, totalBackCount, visual = false)
        val motionPhase = if (expanding) rawMotion else 1f - rawMotion
        val dockedProgress = modelStackDockingProgress(motionPhase, geometry.overshoot)
        val pathProgress = if (expanding) dockedProgress else 1f - dockedProgress
        val cardTop = modelStackLerpRawFloat(geometry.collapsedY, geometry.expandedY, pathProgress)
        val cardHeight = modelStackLerpRawFloat(MODEL_CARD_COLLAPSED_HEIGHT_DP, MODEL_CARD_EXPANDED_HEIGHT_DP, targetProgress)
        bottom = maxOf(bottom, cardTop + cardHeight)
    }
    return bottom
}

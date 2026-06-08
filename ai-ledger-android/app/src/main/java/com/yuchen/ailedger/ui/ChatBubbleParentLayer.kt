package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
import kotlin.math.abs

@Stable
class ChatBubbleLayerState {
    private val visuals = mutableStateMapOf<String, ChatBubbleLayerVisual>()

    fun updateBubbleVisual(
        id: String,
        fromUser: Boolean,
        status: MessageStatus,
        appear: Float,
        phaseOffset: Float,
        speedFactor: Float,
        radiusDp: Int,
        thinkingSweepStrength: Float = if (!fromUser && status == MessageStatus.Sending) 1f else 0f
    ) {
        val nextAppear = appear.coerceIn(0f, 1.18f)
        val nextThinkingSweepStrength = thinkingSweepStrength.coerceIn(0f, 1f)
        val current = visuals[id]
        if (
            current == null ||
            abs(current.appear - nextAppear) > 0.001f ||
            abs(current.thinkingSweepStrength - nextThinkingSweepStrength) > 0.001f ||
            current.status != status ||
            current.fromUser != fromUser ||
            current.radiusDp != radiusDp
        ) {
            visuals[id] = ChatBubbleLayerVisual(
                id = id,
                fromUser = fromUser,
                status = status,
                appear = nextAppear,
                phaseOffset = phaseOffset,
                speedFactor = speedFactor,
                radiusDp = radiusDp,
                thinkingSweepStrength = nextThinkingSweepStrength
            )
        }
    }

    fun removeBubble(id: String) {
        visuals.remove(id)
    }

    fun removeMissing(activeIds: Set<String>) {
        if (visuals.isEmpty()) return
        if (activeIds.isEmpty()) {
            visuals.clear()
            return
        }
        visuals.keys.toList().forEach { id ->
            if (!activeIds.contains(id)) visuals.remove(id)
        }
    }

    fun visualFor(message: ChatMessage): ChatBubbleLayerVisual {
        return visuals[message.id] ?: ChatBubbleLayerVisual(
            id = message.id,
            fromUser = message.role == MessageRole.User,
            status = message.status,
            appear = 1f,
            phaseOffset = ((message.id.hashCode() ushr 1) % 997) / 997f,
            speedFactor = 1f,
            radiusDp = if (message.role == MessageRole.User) 26 else 28,
            thinkingSweepStrength = chatBubbleFallbackThinkingSweepStrength(message)
        )
    }
}

private fun chatBubbleFallbackThinkingSweepStrength(message: ChatMessage): Float {
    if (message.role != MessageRole.Assistant || message.status != MessageStatus.Sending) return 0f
    val clean = message.text.trim()
    return if (
        clean == "正在思考…" ||
        clean == "正在重新生成…" ||
        clean == "正在思考" ||
        clean == "正在重新生成" ||
        clean == "正在理解视觉附件…" ||
        clean == "正在理解视觉附件" ||
        clean == "正在执行手机智能体任务…" ||
        clean == "正在执行手机智能体任务"
    ) 1f else 0f
}

@Stable
data class ChatBubbleLayerVisual(
    val id: String,
    val fromUser: Boolean,
    val status: MessageStatus,
    val appear: Float,
    val phaseOffset: Float,
    val speedFactor: Float,
    val radiusDp: Int,
    val thinkingSweepStrength: Float
)

@Stable
data class ChatBubbleVisualTransform(
    val alpha: Float,
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float,
    val originX: Float,
    val originY: Float
)

fun chatBubbleVisualTransform(appear: Float, fromUser: Boolean): ChatBubbleVisualTransform {
    val raw = appear.coerceIn(0f, 1.18f)
    val settled = raw.coerceIn(0f, 1f)
    val overshoot = ((raw - 1f) / 0.18f).coerceIn(0f, 1f)
    return ChatBubbleVisualTransform(
        alpha = settled,
        scaleX = 0.74f + settled * 0.26f + overshoot * 0.038f,
        scaleY = 0.62f + settled * 0.38f - overshoot * 0.030f,
        translationX = (1f - settled) * if (fromUser) 12f else -12f,
        translationY = (1f - settled) * 14f,
        originX = if (fromUser) 0.96f else 0.04f,
        originY = if (fromUser) 0.82f else 0.22f
    )
}

@Composable
fun rememberChatBubbleLayerState(): ChatBubbleLayerState = remember { ChatBubbleLayerState() }

@Composable
fun ChatBubbleMaterialLayer(
    layerState: ChatBubbleLayerState,
    listState: LazyListState,
    messages: List<ChatMessage>,
    phase: Float,
    motionIntensity: Float,
    modifier: Modifier = Modifier
) {
    if (messages.isEmpty()) return

    Canvas(modifier = modifier) {
        val viewportWidth = size.width
        val viewportHeight = size.height
        if (viewportWidth <= 1f || viewportHeight <= 1f) return@Canvas

        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@Canvas

        val messageCount = messages.size
        val horizontalPadding = 6.dp.toPx()
        val verticalPadding = 3.dp.toPx()
        val contentWidth = (viewportWidth - horizontalPadding * 2f).coerceAtLeast(1f)
        val motion = motionIntensity.coerceIn(0f, 1f)
        val basePhase = if (motion > 0.001f) phase else 0f

        visibleItems.forEach { item ->
            if (item.index !in 0 until messageCount) return@forEach

            val message = messages[item.index]
            val visualInfo = layerState.visualFor(message)
            val fromUser = visualInfo.fromUser
            val bubbleFraction = if (fromUser) 0.76f else 0.90f
            val bubbleWidth = contentWidth * bubbleFraction
            val rawLeft = if (fromUser) {
                horizontalPadding + contentWidth - bubbleWidth
            } else {
                horizontalPadding
            }
            val rawTop = item.offset.toFloat() + verticalPadding
            val rawHeight = (item.size.toFloat() - verticalPadding * 2f).coerceAtLeast(1f)

            val transform = chatBubbleVisualTransform(visualInfo.appear, fromUser)
            val pivotX = rawLeft + bubbleWidth * transform.originX
            val pivotY = rawTop + rawHeight * transform.originY
            val rectLeft = pivotX + (rawLeft - pivotX) * transform.scaleX + transform.translationX
            val rectTop = pivotY + (rawTop - pivotY) * transform.scaleY + transform.translationY
            val rectRight = pivotX + (rawLeft + bubbleWidth - pivotX) * transform.scaleX + transform.translationX
            val rectBottom = pivotY + (rawTop + rawHeight - pivotY) * transform.scaleY + transform.translationY

            val intersectsViewport = rectRight > 0f && rectLeft < viewportWidth && rectBottom > 0f && rectTop < viewportHeight
            if (intersectsViewport && rectRight - rectLeft > 1f && rectBottom - rectTop > 1f) {
                val thinkingSweepStrength = if (!fromUser && visualInfo.status == MessageStatus.Sending) {
                    visualInfo.thinkingSweepStrength.coerceIn(0f, 1f)
                } else {
                    0f
                }
                val sending = thinkingSweepStrength > 0.001f
                val itemPhase = if (sending) {
                    ((basePhase * 3f) + visualInfo.phaseOffset) % 1f
                } else {
                    (basePhase + visualInfo.phaseOffset) % 1f
                }
                drawChatBubblePrismMaterial(
                    rect = Rect(rectLeft, rectTop, rectRight, rectBottom),
                    phase = itemPhase,
                    fromUser = fromUser,
                    sending = sending,
                    failed = visualInfo.status == MessageStatus.Failed,
                    motionIntensity = motion,
                    radiusDp = visualInfo.radiusDp,
                    layerAlpha = transform.alpha,
                    thinkingSweepStrength = thinkingSweepStrength
                )
            }
        }
    }
}

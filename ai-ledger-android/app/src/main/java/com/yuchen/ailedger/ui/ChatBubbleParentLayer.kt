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
        radiusDp: Int
    ) {
        val nextAppear = appear.coerceIn(0f, 1.18f)
        val current = visuals[id]
        if (
            current == null ||
            abs(current.appear - nextAppear) > 0.001f ||
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
                radiusDp = radiusDp
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
            radiusDp = if (message.role == MessageRole.User) 26 else 28
        )
    }
}

@Stable
data class ChatBubbleLayerVisual(
    val id: String,
    val fromUser: Boolean,
    val status: MessageStatus,
    val appear: Float,
    val phaseOffset: Float,
    val speedFactor: Float,
    val radiusDp: Int
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

private fun Rect.transformedBy(transform: ChatBubbleVisualTransform): Rect {
    val pivotX = left + width * transform.originX
    val pivotY = top + height * transform.originY
    val nextLeft = pivotX + (left - pivotX) * transform.scaleX + transform.translationX
    val nextTop = pivotY + (top - pivotY) * transform.scaleY + transform.translationY
    val nextRight = pivotX + (right - pivotX) * transform.scaleX + transform.translationX
    val nextBottom = pivotY + (bottom - pivotY) * transform.scaleY + transform.translationY
    return Rect(nextLeft, nextTop, nextRight, nextBottom)
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
            val bubbleLeft = if (fromUser) {
                horizontalPadding + contentWidth - bubbleWidth
            } else {
                horizontalPadding
            }
            val bubbleTop = item.offset.toFloat() + verticalPadding
            val bubbleHeight = (item.size.toFloat() - verticalPadding * 2f).coerceAtLeast(1f)
            val rawRect = Rect(
                left = bubbleLeft,
                top = bubbleTop,
                right = bubbleLeft + bubbleWidth,
                bottom = bubbleTop + bubbleHeight
            )
            val transform = chatBubbleVisualTransform(visualInfo.appear, fromUser)
            val rect = rawRect.transformedBy(transform)
            val intersectsViewport = rect.right > 0f && rect.left < viewportWidth && rect.bottom > 0f && rect.top < viewportHeight
            if (intersectsViewport && rect.width > 1f && rect.height > 1f) {
                val sending = visualInfo.status == MessageStatus.Sending && !fromUser
                val itemPhase = if (sending) {
                    ((basePhase * 3f) + visualInfo.phaseOffset) % 1f
                } else {
                    (basePhase + visualInfo.phaseOffset) % 1f
                }
                drawChatBubblePrismMaterial(
                    rect = rect,
                    phase = itemPhase,
                    fromUser = fromUser,
                    sending = sending,
                    failed = visualInfo.status == MessageStatus.Failed,
                    motionIntensity = motion,
                    radiusDp = visualInfo.radiusDp,
                    layerAlpha = transform.alpha
                )
            }
        }
    }
}

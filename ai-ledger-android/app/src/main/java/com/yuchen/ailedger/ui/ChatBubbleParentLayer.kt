package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.yuchen.ailedger.model.MessageStatus
import kotlin.math.abs

@Stable
class ChatBubbleLayerState {
    private val bubbles = mutableStateMapOf<String, ChatBubbleLayerItem>()
    private var rootInWindow: Offset = Offset.Zero

    fun updateRoot(coordinates: LayoutCoordinates) {
        val nextRoot = coordinates.boundsInRoot().topLeft
        if (abs(nextRoot.x - rootInWindow.x) > 0.5f || abs(nextRoot.y - rootInWindow.y) > 0.5f) {
            rootInWindow = nextRoot
            // Visible bubbles report fresh bounds through onGloballyPositioned.
            // Off-screen bubbles are removed by DisposableEffect in the LazyColumn item.
        }
    }

    fun updateBubble(
        id: String,
        coordinates: LayoutCoordinates,
        fromUser: Boolean,
        status: MessageStatus,
        appear: Float,
        phaseOffset: Float,
        speedFactor: Float,
        radiusDp: Int
    ) {
        val bounds = coordinates.boundsInRoot()
        val local = Rect(
            offset = bounds.topLeft - rootInWindow,
            size = bounds.size
        )
        val current = bubbles[id]
        if (
            current == null ||
            current.rect != local ||
            current.appear != appear ||
            current.status != status ||
            current.fromUser != fromUser ||
            current.radiusDp != radiusDp
        ) {
            bubbles[id] = ChatBubbleLayerItem(
                id = id,
                rect = local,
                fromUser = fromUser,
                status = status,
                appear = appear,
                phaseOffset = phaseOffset,
                speedFactor = speedFactor,
                radiusDp = radiusDp
            )
        }
    }

    fun updateBubbleAppearance(id: String, appear: Float) {
        val current = bubbles[id] ?: return
        val next = appear.coerceIn(0f, 1.18f)
        if (abs(current.appear - next) > 0.001f) {
            bubbles[id] = current.copy(appear = next)
        }
    }

    fun removeBubble(id: String) {
        bubbles.remove(id)
    }

    fun removeMissing(activeIds: Set<String>) {
        bubbles.keys.toList().forEach { id ->
            if (!activeIds.contains(id)) bubbles.remove(id)
        }
    }

    fun items(): List<ChatBubbleLayerItem> = bubbles.values.toList()
}

@Stable
data class ChatBubbleLayerItem(
    val id: String,
    val rect: Rect,
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
    phase: Float,
    motionIntensity: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.onGloballyPositioned { layerState.updateRoot(it) }
    ) {
        val viewportWidth = size.width
        val viewportHeight = size.height
        layerState.items().forEach { item ->
            val transform = chatBubbleVisualTransform(item.appear, item.fromUser)
            val r = item.rect.transformedBy(transform)
            val intersectsViewport = r.right > 0f && r.left < viewportWidth && r.bottom > 0f && r.top < viewportHeight
            if (intersectsViewport && r.width > 1f && r.height > 1f) {
                val sending = item.status == MessageStatus.Sending && !item.fromUser
                val itemPhase = if (sending) {
                    ((phase * 3f) + item.phaseOffset) % 1f
                } else {
                    (phase + item.phaseOffset) % 1f
                }
                drawChatBubblePrismMaterial(
                    rect = r,
                    phase = itemPhase,
                    fromUser = item.fromUser,
                    sending = sending,
                    failed = item.status == MessageStatus.Failed,
                    motionIntensity = motionIntensity,
                    radiusDp = item.radiusDp,
                    layerAlpha = transform.alpha
                )
            }
        }
    }
}

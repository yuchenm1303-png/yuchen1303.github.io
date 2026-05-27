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
            // Do not translate cached bubble rects here. Any visible bubble will report its fresh
            // bounds through onGloballyPositioned; stale off-screen bubbles are removed on dispose.
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
            val r = item.rect
            val intersectsViewport = r.right > 0f && r.left < viewportWidth && r.bottom > 0f && r.top < viewportHeight
            if (intersectsViewport && r.width > 1f && r.height > 1f) {
                val sending = item.status == MessageStatus.Sending && !item.fromUser
                val itemPhase = ((phase * if (sending) 2.85f else item.speedFactor) + item.phaseOffset) % 1f
                drawChatBubblePrismMaterial(
                    rect = r,
                    phase = itemPhase,
                    fromUser = item.fromUser,
                    sending = sending,
                    failed = item.status == MessageStatus.Failed,
                    motionIntensity = motionIntensity,
                    radiusDp = item.radiusDp,
                    layerAlpha = item.appear
                )
            }
        }
    }
}

package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import com.yuchen.ailedger.model.MessageStatus
import kotlin.math.abs

@Stable
class ChatBubbleLayerState {
    private val bubbles = mutableStateMapOf<String, ChatBubbleLayerItem>()
    var rootInWindow: Offset = Offset.Zero
        private set

    fun updateRoot(coordinates: LayoutCoordinates) {
        val root = coordinates.boundsInRoot().topLeft
        if (abs(root.x - rootInWindow.x) > 0.5f || abs(root.y - rootInWindow.y) > 0.5f) {
            rootInWindow = root
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
        if (current == null || current.rect != local || current.appear != appear || current.status != status) {
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

    fun removeMissing(activeIds: Set<String>) {
        val iterator = bubbles.keys.toList().iterator()
        while (iterator.hasNext()) {
            val id = iterator.next()
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
    Canvas(modifier = modifier) {
        layerState.items().forEach { item ->
            if (item.rect.width > 1f && item.rect.height > 1f) {
                val sending = item.status == MessageStatus.Sending && !item.fromUser
                val itemPhase = ((phase * if (sending) 2.85f else item.speedFactor) + item.phaseOffset) % 1f
                drawChatBubblePrismMaterial(
                    rect = item.rect,
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

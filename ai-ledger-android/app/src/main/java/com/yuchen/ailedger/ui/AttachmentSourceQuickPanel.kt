package com.yuchen.ailedger.ui

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.RenderQuality

private const val AttachmentSourceCompactScale = 0.90f

@Composable
internal fun AttachmentSourceQuickPanel(
    visible: Boolean,
    rootView: View,
    bottomDockVisible: Boolean,
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    onDismiss: () -> Unit,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
) {
    AnchoredQuickPanel(
        visible = visible,
        anchorBounds = attachmentSourceButtonAnchorBounds(rootView, bottomDockVisible),
        desiredWidth = 260.dp,
        desiredHeight = 148.dp,
        minHeight = 126.dp,
        preferredPlacement = AnchoredQuickPanelPlacement.Above,
        horizontalBias = 0.18f,
        quality = RenderQuality.Balanced,
        glassIntensity = 1.04f,
        motionIntensity = 0.72f,
        onDismiss = onDismiss,
        cornerRadius = 25.dp,
        tailHeight = 12.dp,
        tailHalfWidth = 15.dp,
        safeMargin = 0.dp,
        precomposeWhenHidden = false,
    ) { layout ->
        val topTailInset = if (layout.placement == AnchoredQuickPanelPlacement.Below) layout.tailHeight else 0.dp
        val bottomTailInset = if (layout.placement == AnchoredQuickPanelPlacement.Above) layout.tailHeight else 0.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 13.dp,
                    top = topTailInset + 11.dp,
                    end = 13.dp,
                    bottom = bottomTailInset + 10.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "添加图片",
                color = Color.White.copy(alpha = 0.96f),
                fontSize = 16.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Black,
            )
            AttachmentQuickPanelAction(label = "从相册选择", tag = "LIB", onClick = onPickGallery)
            AttachmentQuickPanelAction(label = "拍照上传", tag = "CAM", onClick = onTakePhoto)
        }
    }
}

@Composable
private fun AttachmentQuickPanelAction(
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Color.White.copy(alpha = 0.070f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(27.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF8DF9EA).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = tag,
                color = Color(0xFF8DF9EA).copy(alpha = 0.90f),
                fontSize = 8.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

private fun attachmentSourceButtonAnchorBounds(rootView: View, bottomDockVisible: Boolean): Rect {
    val location = IntArray(2)
    rootView.getLocationInWindow(location)
    val density = rootView.resources.displayMetrics.density.coerceAtLeast(1f)
    val rootHeight = rootView.height.takeIf { it > 0 } ?: 1

    // 加号按钮位于 AssistantScreenV2 的 compact density 区域内：外层水平 padding 为 12dp，
    // Composer 底部留白为 bottomPadding，按钮本体为 48dp。锚点必须按同一 compact scale
    // 换算成窗口像素，否则 AnchoredQuickPanel 会认为按钮更靠上，导致弹窗和按钮脱节。
    val buttonSizePx = 48f * AttachmentSourceCompactScale * density
    val left = location[0] + 12f * AttachmentSourceCompactScale * density
    val bottomInsetDp = if (bottomDockVisible) 68f else 8f
    val bottom = location[1] + rootHeight - bottomInsetDp * AttachmentSourceCompactScale * density
    val top = bottom - buttonSizePx
    return Rect(
        left = left,
        top = top,
        right = left + buttonSizePx,
        bottom = bottom,
    )
}

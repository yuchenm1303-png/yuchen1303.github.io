package com.yuchen.ailedger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val STORAGE_FILE_PREVIEW_COUNT = 8
internal const val STORAGE_GROUP_PREVIEW_COUNT = 4
internal const val STORAGE_HISTORY_PREVIEW_COUNT = 6

internal fun <T> storagePreviewItems(
    items: List<T>,
    expanded: Boolean,
    previewCount: Int,
): List<T> {
    return if (expanded || items.size <= previewCount) items else items.take(previewCount)
}

@Composable
internal fun StorageLongListControls(
    totalCount: Int,
    expanded: Boolean,
    previewCount: Int,
    selectedCount: Int = 0,
    selectAllLabel: String = "全选当前",
    onToggleExpanded: (() -> Unit)? = null,
    onSelectAll: (() -> Unit)? = null,
    onClearSelection: (() -> Unit)? = null,
    tone: Color = StorageAccent,
) {
    val canFold = totalCount > previewCount && onToggleExpanded != null
    val hasSelectionActions = onSelectAll != null || (selectedCount > 0 && onClearSelection != null)
    if (!canFold && !hasSelectionActions) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (canFold) {
            StorageLongListAction(
                text = if (expanded) "收起列表" else "展开全部 $totalCount 项",
                tone = tone,
                emphasized = expanded,
                onClick = onToggleExpanded,
            )
        }
        onSelectAll?.let { action ->
            StorageLongListAction(
                text = selectAllLabel,
                tone = tone,
                emphasized = false,
                onClick = action,
            )
        }
        if (selectedCount > 0 && onClearSelection != null) {
            StorageLongListAction(
                text = "清除当前选择 $selectedCount",
                tone = Color.White,
                emphasized = false,
                onClick = onClearSelection,
            )
        }
    }
}

@Composable
private fun StorageLongListAction(
    text: String,
    tone: Color,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    Surface(
        modifier = Modifier.composeGlassMotionClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = tone.copy(alpha = if (emphasized) 0.16f else 0.075f),
        border = BorderStroke(1.dp, tone.copy(alpha = if (emphasized) 0.34f else 0.15f)),
    ) {
        Text(
            text = text,
            color = tone.copy(alpha = if (tone == Color.White) 0.68f else 0.90f),
            fontSize = 9.8.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
        )
    }
}

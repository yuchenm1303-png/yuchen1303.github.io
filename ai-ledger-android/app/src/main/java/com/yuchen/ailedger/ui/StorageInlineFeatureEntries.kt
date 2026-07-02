package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState

internal data class StorageInlineFeatureEntry(
    val title: String,
    val subtitle: String,
    val tone: Color,
    val onClick: () -> Unit,
)

@Composable
internal fun StorageInlineFeatureSection(
    state: AssistantUiState,
    entries: List<StorageInlineFeatureEntry>,
) {
    if (entries.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "进阶工具",
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
        )
        entries.forEach { entry ->
            PressableGlass(
                quality = state.quality,
                glassIntensity = state.glassIntensity,
                motionIntensity = state.motionIntensity,
                radius = 22,
                modifier = Modifier.fillMaxWidth().height(68.dp),
                role = GlassRole.Card,
                onClick = entry.onClick,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                entry.title,
                                color = Color.White.copy(alpha = 0.94f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                entry.subtitle,
                                color = Color.White.copy(alpha = 0.48f),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        "进入 ›",
                        color = entry.tone.copy(alpha = 0.92f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

package com.yuchen.ailedger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.service.StorageOrganizationFile

@Composable
internal fun OrganizationSelectionPanel(
    files: List<StorageOrganizationFile>,
    operationRunning: Boolean,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = OrganizationCritical.copy(alpha = 0.085f),
        border = BorderStroke(1.dp, OrganizationCritical.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            OrganizationMetric(
                label = "已选择",
                value = "${files.size} 个 · ${formatOrganizationBytes(files.sumOf { it.sizeBytes })}",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OrganizationSecondaryAction("清除选择", Modifier.weight(1f), onClear)
                OrganizationDangerAction(
                    text = if (operationRunning) "正在处理…" else "清理已选",
                    enabled = !operationRunning,
                    modifier = Modifier.weight(1f),
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
internal fun OrganizationOverviewCard(
    title: String,
    value: String,
    detail: String,
    tone: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .composeGlassMotionClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = tone.copy(alpha = 0.065f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text(value, color = tone.copy(alpha = 0.90f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text(detail, color = Color.White.copy(alpha = 0.43f), fontSize = 9.8.sp)
            }
            Text("进入 ›", color = Color.White.copy(alpha = 0.52f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun OrganizationSectionHeader(title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f),
        )
        Text(detail, color = Color.White.copy(alpha = 0.43f), fontSize = 9.8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun OrganizationInfoPanel(title: String, text: String, tone: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = tone.copy(alpha = 0.075f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, color = tone.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(text, color = Color.White.copy(alpha = 0.60f), fontSize = 10.8.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
internal fun OrganizationEmptyPanel(text: String) {
    OrganizationInfoPanel("暂无结果", text, Color.White)
}

@Composable
internal fun OrganizationLoadingPanel(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.055f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(21.dp),
                strokeWidth = 2.dp,
                color = OrganizationAccent,
            )
            Text(text, color = Color.White.copy(alpha = 0.60f), fontSize = 11.5.sp)
        }
    }
}

@Composable
internal fun OrganizationMetric(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White.copy(alpha = 0.46f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White.copy(alpha = 0.86f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

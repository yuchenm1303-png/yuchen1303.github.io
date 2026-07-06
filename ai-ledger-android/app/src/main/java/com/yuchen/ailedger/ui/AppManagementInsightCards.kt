package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.yuchen.ailedger.service.AppManagementRepository
import com.yuchen.ailedger.service.AppOptimizationSignal
import com.yuchen.ailedger.service.ManagedAppSummary
import com.yuchen.ailedger.service.appControlHumanBytes

@Composable
internal fun ManagedAppCard(
    app: ManagedAppSummary,
    state: AssistantUiState,
    repository: AppManagementRepository,
    signal: AppOptimizationSignal?,
    onClick: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.93f,
        motionIntensity = state.motionIntensity,
        radius = 24,
        modifier = Modifier.fillMaxWidth().height(112.dp),
        role = GlassRole.Card,
        onClick = onClick,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            ManagedAppIcon(app, repository, 50)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app.label,
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(formatBytes(app.apkBytes), color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
                }
                Text(
                    app.packageName,
                    color = Color.White.copy(alpha = 0.46f),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppTinyBadge(if (app.isSystemApp) "系统" else "用户", AppAccent)
                    AppTinyBadge(if (app.isEnabled) "已启用" else "已禁用", if (app.isEnabled) AppSuccess else AppWarning)
                    signal?.runtime?.stateLabel?.let { AppTinyBadge(it, AppAccent) }
                    if (signal?.cleanCandidate == true) AppTinyBadge("可清后台", AppWarning)
                    if (signal?.storageHeavy == true) AppTinyBadge("空间大户", AppWarning)
                    if (app.isProtected) AppTinyBadge("受保护", AppWarning)
                }
                Text(
                    signal?.recommendation ?: "暂无明显异常，可进入详情查看管理入口。",
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    signal?.score?.toString() ?: "--",
                    color = AppAccent.copy(alpha = 0.86f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                )
                Text("体检分", color = Color.White.copy(alpha = 0.36f), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                Text(
                    signal?.runtime?.estimatedMemoryBytes?.appControlHumanBytes() ?: "受限",
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text("›", color = Color.White.copy(alpha = 0.55f), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Arrangement
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

@androidx.compose.runtime.Composable
internal fun ManagedAppCard(
    app: ManagedAppSummary,
    state: AssistantUiState,
    repository: AppManagementRepository,
    signal: AppOptimizationSignal?,
    onClean: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.93f,
        motionIntensity = state.motionIntensity,
        radius = 24,
        modifier = Modifier.fillMaxWidth().height(124.dp),
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
                    Text(signal?.runtime?.estimatedMemoryBytes?.appControlHumanBytes() ?: formatBytes(app.apkBytes), color = AppAccent.copy(alpha = 0.78f), fontSize = 10.5.sp, fontWeight = FontWeight.Black)
                }
                Text(
                    app.packageName,
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 10.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppTinyBadge(signal?.runtime?.stateLabel ?: if (app.isEnabled) "未运行" else "已禁用", if (signal?.runtime != null) AppAccent else Color.White)
                    AppTinyBadge("${signal?.runtime?.processCount ?: 0} 进程", Color.White)
                    if (signal?.cleanCandidate == true) AppTinyBadge("可清后台", AppWarning)
                    if (signal?.storageHeavy == true) AppTinyBadge("空间大户", AppWarning)
                    if (app.isProtected) AppTinyBadge("受保护", AppWarning)
                }
                Text(
                    signal?.runtime?.processNames?.takeIf { it.isNotEmpty() }?.joinToString(" · ")
                        ?: signal?.recommendation
                        ?: "暂无明显异常，可进入详情查看管理入口。",
                    color = Color.White.copy(alpha = 0.46f),
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    signal?.recommendation ?: if (app.isLaunchable) "可打开或进入详情管理。" else "无桌面入口，建议只查看详情。",
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    signal?.score?.toString() ?: "--",
                    color = AppAccent.copy(alpha = 0.86f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                )
                Text("体检分", color = Color.White.copy(alpha = 0.36f), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                if (onClean != null) {
                    AppCompactAction("清理", onClean)
                } else {
                    Text(
                        if (signal?.runtime != null) "运行中" else "详情",
                        color = Color.White.copy(alpha = 0.42f),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Text("›", color = Color.White.copy(alpha = 0.55f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

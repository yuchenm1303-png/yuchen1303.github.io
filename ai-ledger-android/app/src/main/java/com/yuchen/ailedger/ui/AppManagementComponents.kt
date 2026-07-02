package com.yuchen.ailedger.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.service.AgentExecutionResult
import com.yuchen.ailedger.service.AppManagementActionPolicy
import com.yuchen.ailedger.service.AppManagementRepository
import com.yuchen.ailedger.service.DeviceShellStatus
import com.yuchen.ailedger.service.ManagedAppAction
import com.yuchen.ailedger.service.ManagedAppActionTone
import com.yuchen.ailedger.service.ManagedAppDetails
import com.yuchen.ailedger.service.ManagedAppPermission
import com.yuchen.ailedger.service.ManagedAppSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ManagedAppCard(
    app: ManagedAppSummary,
    state: AssistantUiState,
    repository: AppManagementRepository,
    onClick: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.93f,
        motionIntensity = state.motionIntensity,
        radius = 24,
        modifier = Modifier.fillMaxWidth().height(88.dp),
        role = GlassRole.Card,
        onClick = onClick,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            ManagedAppIcon(app, repository, 50)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                    if (app.isLaunchable) AppTinyBadge("可打开", AppSuccess)
                    if (app.isProtected) AppTinyBadge("受保护", AppWarning)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("›", color = Color.White.copy(alpha = 0.55f), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun ManagedAppHero(app: ManagedAppSummary, details: ManagedAppDetails, repository: AppManagementRepository) {
    FrostInfoGlassPanel(radius = 19f, backdropAlpha = 1f, frostAlpha = 0.10f, dimAlpha = 0f, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(27.dp))
                .background(Color(0xFF121743).copy(alpha = 0.31f)).padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ManagedAppIcon(app, repository, 68)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(app.label, color = Color.White, fontSize = 23.sp, lineHeight = 27.sp, fontWeight = FontWeight.Black)
                Text(
                    app.packageName,
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 10.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${details.versionName} · ${if (app.isSystemApp) "系统应用" else "用户应用"}",
                    color = AppAccent.copy(alpha = 0.77f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
internal fun ManagedAppIcon(app: ManagedAppSummary, repository: AppManagementRepository, sizeDp: Int) {
    val density = LocalDensity.current
    val sizePx = with(density) { sizeDp.dp.roundToPx() }
    val bitmap by produceState<Bitmap?>(null, app.packageName, sizePx) {
        value = withContext(Dispatchers.IO) { repository.loadIcon(app.packageName, sizePx) }
    }
    Box(
        modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape((sizeDp * 0.25f).dp))
            .background(Color.White.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "${app.label} 图标",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                app.label.take(1).uppercase(Locale.ROOT),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = (sizeDp * 0.34f).sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
internal fun AppSearchField(query: String, onQueryChange: (String) -> Unit) {
    FrostInfoGlassPanel(radius = 16f, backdropAlpha = 1f, frostAlpha = 0.075f, dimAlpha = 0f, modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = query,
            onValueChange = { onQueryChange(it.take(80)) },
            singleLine = true,
            textStyle = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF111742).copy(alpha = 0.25f)).padding(horizontal = 15.dp, vertical = 13.dp),
            decorationBox = { inner ->
                Box {
                    if (query.isBlank()) Text("搜索应用名称或包名", color = Color.White.copy(alpha = 0.38f), fontSize = 13.sp)
                    inner()
                }
            },
        )
    }
}

@Composable
internal fun AppSelectionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Surface(
        modifier = Modifier.composeGlassMotionClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = if (selected) AppAccent.copy(alpha = 0.17f) else Color.White.copy(alpha = 0.075f),
        border = BorderStroke(1.dp, if (selected) AppAccent.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.12f)),
    ) {
        Text(
            label,
            color = if (selected) AppAccent.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.65f),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun AppManagementBackButton(state: AssistantUiState, text: String, onClick: () -> Unit) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = Modifier.width(116.dp).height(40.dp),
        role = GlassRole.Chip,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
internal fun AppSummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.055f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White.copy(alpha = 0.94f), fontSize = 18.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
internal fun AppDetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    FrostInfoGlassPanel(radius = 17f, backdropAlpha = 1f, frostAlpha = 0.085f, dimAlpha = 0f, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF121743).copy(alpha = 0.27f)).padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(title, color = Color.White.copy(alpha = 0.94f), fontSize = 15.sp, fontWeight = FontWeight.Black)
            content()
        }
    }
}

@Composable
internal fun AppDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = Color.White.copy(alpha = 0.47f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(78.dp))
        Text(value, color = Color.White.copy(alpha = 0.82f), fontSize = 11.5.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun ColumnScope.actionRows(
    actions: List<ManagedAppAction>,
    app: ManagedAppSummary,
    runningAction: ManagedAppAction?,
    onAction: (ManagedAppAction, ManagedAppSummary) -> Unit,
) {
    actions.forEach { action ->
        val availability = AppManagementActionPolicy.availability(action, app)
        AppActionCard(action, availability.enabled, availability.reason, runningAction == action) { onAction(action, app) }
    }
}

@Composable
internal fun AppActionCard(
    action: ManagedAppAction,
    availability: Boolean,
    disabledReason: String,
    running: Boolean,
    onClick: () -> Unit,
) {
    val tone = when (action.tone) {
        ManagedAppActionTone.Normal -> AppAccent
        ManagedAppActionTone.Warning -> AppWarning
        ManagedAppActionTone.Critical -> AppCritical
    }
    val shape = RoundedCornerShape(19.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().composeGlassMotionClickable(
            shape = shape,
            enabled = availability && !running,
            onClick = onClick,
        ),
        shape = shape,
        color = tone.copy(alpha = if (availability) 0.09f else 0.035f),
        border = BorderStroke(1.dp, tone.copy(alpha = if (availability) 0.22f else 0.08f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    action.title,
                    color = if (availability) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.32f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    if (availability) action.description else disabledReason,
                    color = Color.White.copy(alpha = if (availability) 0.48f else 0.28f),
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                )
            }
            if (running) CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp, color = tone)
            else Text(
                if (availability) "执行" else "不可用",
                color = tone.copy(alpha = if (availability) 0.85f else 0.36f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
internal fun ManagedPermissionCard(permission: ManagedAppPermission) {
    val tone = when {
        permission.dangerous && permission.granted -> AppWarning
        permission.granted -> AppSuccess
        else -> Color.White
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.065f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(permission.label, color = Color.White.copy(alpha = 0.88f), fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    permission.name,
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 9.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                when {
                    permission.granted && permission.dangerous -> "敏感 · 已授权"
                    permission.granted -> "已授权"
                    permission.dangerous -> "敏感 · 未授权"
                    else -> "未授权"
                },
                color = tone.copy(alpha = 0.78f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
internal fun AppActionResultPanel(result: AgentExecutionResult) {
    AppNoticePanel(if (result.ok) "操作已完成" else "操作未完成", result.message, if (result.ok) AppSuccess else AppWarning)
}

@Composable
internal fun AppNoticePanel(title: String, text: String, tone: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = tone.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.20f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = tone.copy(alpha = 0.90f), fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(text, color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
internal fun AppTinyBadge(text: String, tone: Color) {
    Text(
        text,
        color = tone.copy(alpha = 0.86f),
        fontSize = 8.5.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(tone.copy(alpha = 0.10f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
internal fun AppCompactAction(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Surface(
        modifier = Modifier.composeGlassMotionClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = AppAccent.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, AppAccent.copy(alpha = 0.25f)),
    ) {
        Text(
            text,
            color = AppAccent.copy(alpha = 0.88f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun AppInlineButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().composeGlassMotionClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = AppAccent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, AppAccent.copy(alpha = 0.20f)),
    ) {
        Text(
            text,
            color = AppAccent.copy(alpha = 0.90f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
internal fun AppLoadingPanel(text: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White.copy(alpha = 0.065f)) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AppAccent)
            Text(text, color = Color.White.copy(alpha = 0.60f), fontSize = 12.sp)
        }
    }
}

@Composable
internal fun AppErrorPanel(text: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppNoticePanel("读取失败", text, AppWarning)
        AppInlineButton("重新加载", onRetry)
    }
}

@Composable
internal fun AppEmptyPanel(text: String) {
    AppNoticePanel("暂无结果", text, Color.White)
}

@Composable
internal fun ManagedAppConfirmationDialog(
    pending: PendingManagedAppAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val action = pending.action
    val app = pending.app
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10163A).copy(alpha = 0.98f),
        tonalElevation = 0.dp,
        title = { Text("确认${action.title}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("目标：${app.label}", color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text(confirmationMessage(action), color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp, lineHeight = 18.sp)
                Text(app.packageName, color = Color.White.copy(alpha = 0.34f), fontSize = 10.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    when (action) {
                        ManagedAppAction.ClearData -> "确认清除全部数据"
                        ManagedAppAction.Uninstall -> "确认卸载"
                        ManagedAppAction.Disable -> "确认禁用"
                        else -> "继续执行"
                    },
                    color = if (action.tone == ManagedAppActionTone.Critical) AppCritical else AppWarning,
                    fontWeight = FontWeight.Black,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color.White.copy(alpha = 0.62f), fontWeight = FontWeight.Bold)
            }
        },
    )
}

internal fun confirmationMessage(action: ManagedAppAction): String = when (action) {
    ManagedAppAction.ForceStop -> "应用当前进程会被立即终止，未保存的任务可能丢失。重新打开应用后通常可以恢复。"
    ManagedAppAction.ClearData -> "这不是清缓存。该操作会删除账号登录、应用设置、本地数据库和离线文件，执行后无法由本应用恢复。"
    ManagedAppAction.Disable -> "禁用后应用将无法运行，部分关联功能可能失效。可以稍后从这里重新启用。"
    ManagedAppAction.Enable -> "将恢复该应用的运行和启动能力。"
    ManagedAppAction.Uninstall -> "应用将从当前用户中移除，本地数据可能一并删除。系统应用已被策略禁止执行此操作。"
    else -> action.description
}

internal fun shellStatusLabel(status: DeviceShellStatus?): String = when {
    status == null -> "正在检测 Shizuku/增强 Shell…"
    status.shizukuGranted -> "Shizuku 已授权，强停、禁用、清除数据和卸载等增强能力可用。"
    status.shizukuAvailable -> "已检测到 Shizuku，但尚未授权；普通打开与系统设置入口仍可使用。"
    status.isAdbShellLike -> "检测到 ADB Shell 增强身份。"
    else -> "当前为普通 App 权限，只开放打开应用和系统设置入口。"
}

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "未知"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index += 1
    }
    val digits = if (value >= 100 || index == 0) 0 else 1
    return String.format(Locale.CHINA, "%.${digits}f %s", value, units[index])
}

internal fun formatOptionalBytes(bytes: Long?): String = bytes?.let(::formatBytes) ?: "需要使用情况访问权"

internal fun formatAppDate(timestamp: Long): String {
    if (timestamp <= 0L) return "未知"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}

internal val dailyActions = listOf(
    ManagedAppAction.Open,
    ManagedAppAction.ManageStorage,
    ManagedAppAction.NotificationSettings,
    ManagedAppAction.PermissionSettings,
    ManagedAppAction.BatterySettings,
)

internal val stateChangingActions = setOf(
    ManagedAppAction.ForceStop,
    ManagedAppAction.ClearData,
    ManagedAppAction.Disable,
    ManagedAppAction.Enable,
    ManagedAppAction.Uninstall,
)

internal val AppAccent = Color(0xFF8DF9EA)
internal val AppSuccess = Color(0xFF83F3B8)
internal val AppWarning = Color(0xFFFFCA72)
internal val AppCritical = Color(0xFFFF7F8D)

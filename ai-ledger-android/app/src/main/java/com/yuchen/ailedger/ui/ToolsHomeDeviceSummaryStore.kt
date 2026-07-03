package com.yuchen.ailedger.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Environment
import android.os.Process
import android.os.StatFs
import com.yuchen.ailedger.service.AppManagementRepository
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ToolsHomeDeviceSummary(
    val loaded: Boolean = false,
    val installedApps: Int = 0,
    val userApps: Int = 0,
    val appIcons: List<Bitmap> = emptyList(),
    val usedBytes: Long = 0L,
    val totalBytes: Long = 0L,
)

/**
 * 功能首页唯一的设备摘要数据源。
 *
 * 使用低优先级单线程串行访问 PackageManager 和图标解码，避免与 Compose、OpenGL、
 * 背景纹理以及 Room 查询争抢 Default dispatcher。应用入口只查询 Launcher activity，
 * 不再对每个安装包逐一调用 getLaunchIntentForPackage 或读取全部应用名称排序。
 */
internal object ToolsHomeDeviceSummaryStore {
    private val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { task ->
        Thread(
            {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                task.run()
            },
            "ToolsHomeDeviceSummary",
        ).apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val started = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(ToolsHomeDeviceSummary())
    val state: StateFlow<ToolsHomeDeviceSummary> = mutableState.asStateFlow()

    fun request(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch {
            val packageManager = appContext.packageManager
            val applications = installedApplications(packageManager)
            // 与原首页口径保持一致：只排除 FLAG_SYSTEM，更新后的系统应用仍沿用原计数语义。
            val userAppCount = applications.count { info ->
                info.flags and ApplicationInfo.FLAG_SYSTEM == 0
            }
            val statFs = runCatching {
                StatFs(Environment.getDataDirectory().absolutePath)
            }.getOrNull()
            val totalBytes = statFs?.totalBytes ?: 0L
            val freeBytes = statFs?.availableBytes ?: 0L

            // 先发布数字摘要，让卡片尽快完成；图标解码继续在同一低优先级线程串行进行。
            mutableState.value = ToolsHomeDeviceSummary(
                loaded = true,
                installedApps = applications.size,
                userApps = userAppCount,
                usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L),
                totalBytes = totalBytes,
            )

            val launchablePackages = launcherUserPackages(packageManager)
            val selectedPackages = selectPreviewPackages(launchablePackages)
            val iconRepository = AppManagementRepository(appContext)
            val icons = selectedPackages.mapNotNull { packageName ->
                iconRepository.loadIcon(packageName, PREVIEW_ICON_SIZE_PX)
            }
            mutableState.value = mutableState.value.copy(appIcons = icons)
        }
    }

    private fun installedApplications(packageManager: PackageManager): List<ApplicationInfo> {
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(0L),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledApplications(0)
            }
        }.getOrDefault(emptyList())
    }

    private fun launcherUserPackages(packageManager: PackageManager): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val rows = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0L),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }
        }.getOrDefault(emptyList())
        return rows.mapNotNull { row ->
            val appInfo = row.activityInfo?.applicationInfo ?: return@mapNotNull null
            if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) return@mapNotNull null
            row.activityInfo?.packageName?.trim()?.takeIf(String::isNotBlank)
        }.distinct()
    }

    private fun selectPreviewPackages(launchablePackages: List<String>): List<String> {
        val available = launchablePackages.toHashSet()
        val priority = PREVIEW_PRIORITY_PACKAGES.filter { it in available }
        return (priority + launchablePackages)
            .distinct()
            .take(PREVIEW_ICON_COUNT)
    }

    private const val PREVIEW_ICON_COUNT = 4
    private const val PREVIEW_ICON_SIZE_PX = 128
    private val PREVIEW_PRIORITY_PACKAGES = listOf(
        "com.tencent.mm",
        "com.tencent.mobileqq",
        "com.eg.android.AlipayGphone",
        "com.ss.android.ugc.aweme",
        "com.microsoft.emmx",
        "com.android.chrome",
    )
}

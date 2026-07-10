package com.yuchen.ailedger.service

import android.content.Context
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledLaunchableApp(
    val displayName: String,
    val packageName: String,
)

/**
 * 只枚举用户能够从桌面启动的应用。界面仅展示应用名称，包名作为内部授权标识保存。
 *
 * 设备可启动应用事实源统一复用 InstalledAppIndex。安装、卸载和替换广播会使共享索引
 * 立即失效，避免操作学习与 AI 请求链分别执行一次 PackageManager 全量扫描。
 */
object InstalledLaunchableAppCatalog {
    suspend fun load(context: Context): List<InstalledLaunchableApp> = withContext(Dispatchers.IO) {
        val applicationContext = context.applicationContext
        val collator = Collator.getInstance(Locale.getDefault())

        InstalledAppIndex(applicationContext)
            .getLaunchableApps()
            .asSequence()
            .filter { app -> app.packageName != applicationContext.packageName }
            .map { app ->
                InstalledLaunchableApp(
                    displayName = app.label.ifBlank { app.packageName.substringAfterLast('.') },
                    packageName = app.packageName,
                )
            }
            .sortedWith { left, right ->
                val nameOrder = collator.compare(left.displayName, right.displayName)
                if (nameOrder != 0) nameOrder else left.packageName.compareTo(right.packageName)
            }
            .toList()
    }
}

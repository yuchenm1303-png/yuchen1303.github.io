package com.yuchen.ailedger.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
 */
object InstalledLaunchableAppCatalog {
    suspend fun load(context: Context): List<InstalledLaunchableApp> = withContext(Dispatchers.IO) {
        val applicationContext = context.applicationContext
        val packageManager = applicationContext.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = queryLaunchableActivities(packageManager, launcherIntent)
        val collator = Collator.getInstance(Locale.getDefault())

        resolved
            .asSequence()
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName?.trim().orEmpty()
                if (packageName.isBlank() || packageName == applicationContext.packageName) {
                    return@mapNotNull null
                }
                val displayName = runCatching {
                    resolveInfo.loadLabel(packageManager).toString().trim()
                }.getOrDefault("").ifBlank {
                    packageName.substringAfterLast('.')
                }
                InstalledLaunchableApp(
                    displayName = displayName,
                    packageName = packageName,
                )
            }
            .distinctBy(InstalledLaunchableApp::packageName)
            .sortedWith { left, right ->
                val nameOrder = collator.compare(left.displayName, right.displayName)
                if (nameOrder != 0) nameOrder else left.packageName.compareTo(right.packageName)
            }
            .toList()
    }

    @Suppress("DEPRECATION")
    private fun queryLaunchableActivities(
        packageManager: PackageManager,
        intent: Intent,
    ) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
        )
    } else {
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    }
}

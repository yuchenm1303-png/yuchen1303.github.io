package com.yuchen.ailedger.service

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.util.Locale

/**
 * Full application inventory used only by the explicit application-management page.
 *
 * The lightweight [InstalledAppIndex] remains dedicated to normal chat so opening the tools page
 * can never make ordinary assistant requests enumerate every installed package or decode icons.
 */
data class ManagedAppSummary(
    val label: String,
    val packageName: String,
    val uid: Int,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val isLaunchable: Boolean,
    val isProtected: Boolean,
    val protectionReason: String,
    val apkBytes: Long,
)

data class ManagedAppPermission(
    val name: String,
    val label: String,
    val granted: Boolean,
    val dangerous: Boolean,
)

data class ManagedAppStorage(
    val apkBytes: Long,
    val appBytes: Long? = null,
    val dataBytes: Long? = null,
    val cacheBytes: Long? = null,
) {
    val totalPrivateBytes: Long?
        get() = if (appBytes != null && dataBytes != null) appBytes + dataBytes else null
}

data class ManagedAppDetails(
    val summary: ManagedAppSummary,
    val versionName: String,
    val versionCode: Long,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val targetSdk: Int,
    val minSdk: Int,
    val installerPackage: String,
    val sourceDir: String,
    val dataDir: String,
    val nativeLibraryDir: String,
    val splitApkCount: Int,
    val permissions: List<ManagedAppPermission>,
    val storage: ManagedAppStorage,
    val storageAccessGranted: Boolean,
)

class AppManagementRepository(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val iconCache = object : LruCache<String, Bitmap>(12 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun loadApps(): List<ManagedAppSummary> {
        val applications = installedApplications()
        val protectedPackages = protectedPackageReasons(applications)
        return applications
            .map { info -> info.toSummary(protectedPackages[info.packageName].orEmpty()) }
            .sortedWith(
                compareBy<ManagedAppSummary> { normalizeLabel(it.label) }
                    .thenBy { it.packageName },
            )
    }

    fun loadDetails(packageName: String): ManagedAppDetails? {
        val packageInfo = packageInfo(packageName, PackageManager.GET_PERMISSIONS) ?: return null
        val applicationInfo = packageInfo.applicationInfo ?: applicationInfo(packageName) ?: return null
        val protectedReason = protectedPackageReasons(installedApplications())[packageName].orEmpty()
        val summary = applicationInfo.toSummary(protectedReason)
        val usageAccessGranted = hasStorageStatsAccess()
        val storage = queryStorage(applicationInfo, summary.apkBytes, usageAccessGranted)
        return ManagedAppDetails(
            summary = summary,
            versionName = packageInfo.versionName.orEmpty().ifBlank { "未知" },
            versionCode = packageInfo.longVersionCodeCompat(),
            firstInstallTime = packageInfo.firstInstallTime,
            lastUpdateTime = packageInfo.lastUpdateTime,
            targetSdk = applicationInfo.targetSdkVersion,
            minSdk = applicationInfo.minSdkVersion,
            installerPackage = installerPackageName(packageName).orEmpty().ifBlank { "未知来源" },
            sourceDir = applicationInfo.sourceDir.orEmpty(),
            dataDir = applicationInfo.dataDir.orEmpty(),
            nativeLibraryDir = applicationInfo.nativeLibraryDir.orEmpty(),
            splitApkCount = applicationInfo.splitSourceDirs?.size ?: 0,
            permissions = permissionsFor(packageInfo),
            storage = storage,
            storageAccessGranted = usageAccessGranted,
        )
    }

    fun loadIcon(packageName: String, sizePx: Int): Bitmap? {
        val boundedSize = sizePx.coerceIn(48, 256)
        val key = "$packageName@$boundedSize"
        iconCache.get(key)?.let { return it }
        return runCatching {
            packageManager.getApplicationIcon(packageName)
                .toBitmap(width = boundedSize, height = boundedSize, config = Bitmap.Config.ARGB_8888)
                .also { iconCache.put(key, it) }
        }.getOrNull()
    }

    fun hasStorageStatsAccess(): Boolean {
        val manager = appContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            manager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            manager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun installedApplications(): List<ApplicationInfo> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
        }
    }.getOrDefault(emptyList())

    private fun applicationInfo(packageName: String): ApplicationInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
        }
    }.getOrNull()

    private fun packageInfo(packageName: String, flags: Int): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, flags)
        }
    }.getOrNull()

    private fun ApplicationInfo.toSummary(protectionReason: String): ManagedAppSummary {
        val label = runCatching { packageManager.getApplicationLabel(this).toString().trim() }
            .getOrDefault("")
            .ifBlank { packageName }
        val system = flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
            flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        return ManagedAppSummary(
            label = label,
            packageName = packageName,
            uid = uid,
            isSystemApp = system,
            isEnabled = enabled,
            isLaunchable = packageManager.getLaunchIntentForPackage(packageName) != null,
            isProtected = protectionReason.isNotBlank(),
            protectionReason = protectionReason,
            apkBytes = apkBytes(this),
        )
    }

    private fun apkBytes(info: ApplicationInfo): Long {
        val paths = buildList {
            info.sourceDir?.takeIf(String::isNotBlank)?.let(::add)
            info.splitSourceDirs?.filter(String::isNotBlank)?.let(::addAll)
        }
        return paths.sumOf { path -> runCatching { File(path).length() }.getOrDefault(0L) }
    }

    private fun queryStorage(info: ApplicationInfo, apkBytes: Long, accessGranted: Boolean): ManagedAppStorage {
        if (!accessGranted) return ManagedAppStorage(apkBytes = apkBytes)
        val manager = appContext.getSystemService(StorageStatsManager::class.java)
            ?: return ManagedAppStorage(apkBytes = apkBytes)
        return runCatching {
            val stats = manager.queryStatsForUid(StorageManager.UUID_DEFAULT, info.uid)
            ManagedAppStorage(
                apkBytes = apkBytes,
                appBytes = stats.appBytes,
                dataBytes = stats.dataBytes,
                cacheBytes = stats.cacheBytes,
            )
        }.getOrElse { ManagedAppStorage(apkBytes = apkBytes) }
    }

    private fun permissionsFor(info: PackageInfo): List<ManagedAppPermission> {
        val names = info.requestedPermissions.orEmpty()
        val flags = info.requestedPermissionsFlags.orEmpty()
        return names.mapIndexed { index, name ->
            val permissionInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPermissionInfo(name, PackageManager.PermissionInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPermissionInfo(name, 0)
                }
            }.getOrNull()
            val label = permissionInfo
                ?.let { runCatching { it.loadLabel(packageManager).toString().trim() }.getOrNull() }
                .orEmpty()
                .ifBlank { name.substringAfterLast('.') }
            val baseProtection = permissionInfo?.protectionLevel?.and(android.content.pm.PermissionInfo.PROTECTION_MASK_BASE)
            ManagedAppPermission(
                name = name,
                label = label,
                granted = ((flags.getOrNull(index) ?: 0) and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0,
                dangerous = baseProtection == android.content.pm.PermissionInfo.PROTECTION_DANGEROUS,
            )
        }.sortedWith(compareByDescending<ManagedAppPermission> { it.dangerous }.thenBy { normalizeLabel(it.label) })
    }

    private fun protectedPackageReasons(applications: List<ApplicationInfo>): Map<String, String> {
        val reasons = linkedMapOf<String, String>()
        reasons[appContext.packageName] = "当前 AI 助手自身，禁止自毁式管理"
        hardProtectedPackages.forEach { reasons[it] = "Android 核心系统组件" }
        resolveHomePackage()?.let { reasons[it] = "当前默认桌面，禁用后可能无法回到主屏幕" }
        resolveInputMethodPackage()?.let { reasons[it] = "当前默认输入法，禁用后可能无法输入" }
        applications.forEach { info ->
            when {
                info.uid in 0 until Process.FIRST_APPLICATION_UID -> reasons.putIfAbsent(
                    info.packageName,
                    "系统保留 UID 组件",
                )
                info.flags and ApplicationInfo.FLAG_PERSISTENT != 0 -> reasons.putIfAbsent(
                    info.packageName,
                    "系统常驻组件",
                )
            }
        }
        return reasons
    }

    private fun resolveHomePackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf(String::isNotBlank)
    }

    private fun resolveInputMethodPackage(): String? {
        val flattened = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        ).orEmpty()
        return ComponentName.unflattenFromString(flattened)?.packageName?.takeIf(String::isNotBlank)
    }

    private fun installerPackageName(packageName: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(packageName)
        }
    }.getOrNull()

    private fun PackageInfo.longVersionCodeCompat(): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }

    private fun normalizeLabel(value: String): String = value.trim().lowercase(Locale.ROOT)

    companion object {
        private val hardProtectedPackages = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.shell",
            "com.android.providers.settings",
            "com.android.providers.media",
            "com.android.providers.downloads",
            "com.android.phone",
            "com.android.bluetooth",
            "moe.shizuku.privileged.api",
            "rikka.shizuku",
        )
    }
}

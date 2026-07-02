package com.yuchen.ailedger.service

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.UserHandle
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private const val PRODUCT_PREFS = "storage_productization"
private const val APP_SCAN_PROGRESS_KEY = "app_scan_progress"
private const val APP_SCAN_RESULTS_KEY = "app_scan_results"
private const val CAPACITY_SNAPSHOTS_KEY = "capacity_snapshots"
private const val APP_UNUSED_DAYS = 90
private const val APP_SCAN_PAGE_SIZE = 20
private const val MAX_APP_RESULTS = 240
private const val MAX_CAPACITY_SNAPSHOTS = 60
private const val MIN_CAPACITY_SNAPSHOT_INTERVAL_MS = 6L * 60L * 60L * 1000L
private const val LOW_BATTERY_PERCENT = 25

data class StorageDeviceGuard(
    val batteryPercent: Int?,
    val charging: Boolean,
    val thermalStatus: Int?,
    val thermalLabel: String,
    val heavyWorkAllowed: Boolean,
    val reason: String,
)

data class StoragePermissionHealth(
    val usageAccessGranted: Boolean,
    val imageAccessGranted: Boolean,
    val videoAccessGranted: Boolean,
    val audioAccessGranted: Boolean,
    val selectedPhotoAccessOnly: Boolean,
    val authorizedFolderPresent: Boolean,
    val authorizedFolderPermissionValid: Boolean,
) {
    val mediaAccessGranted: Boolean
        get() = imageAccessGranted || videoAccessGranted || audioAccessGranted || selectedPhotoAccessOnly

    val healthy: Boolean
        get() = usageAccessGranted && mediaAccessGranted &&
            (!authorizedFolderPresent || authorizedFolderPermissionValid)
}

data class StorageCompatibilityReport(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val sdk: Int,
    val title: String,
    val guidance: List<String>,
)

data class StorageAppOptimizationItem(
    val label: String,
    val packageName: String,
    val apkBytes: Long,
    val appBytes: Long?,
    val dataBytes: Long?,
    val cacheBytes: Long?,
    val totalBytes: Long?,
    val firstInstallTime: Long,
    val lastUsedAt: Long?,
    val unusedDays: Int?,
    val isProtected: Boolean,
    val suggestionReason: String,
) {
    val longUnusedCandidate: Boolean get() = unusedDays != null && unusedDays >= APP_UNUSED_DAYS && !isProtected
}

data class StorageAppScanProgress(
    val packageSignature: String,
    val processedCount: Int,
    val totalCount: Int,
    val startedAt: Long,
    val updatedAt: Long,
    val complete: Boolean,
    val interrupted: Boolean,
) {
    val fraction: Float
        get() = if (totalCount > 0) processedCount.toFloat() / totalCount.toFloat() else 0f
}

data class StorageAppAnalysisState(
    val progress: StorageAppScanProgress,
    val items: List<StorageAppOptimizationItem>,
    val usageAccessGranted: Boolean,
    val deviceGuard: StorageDeviceGuard,
    val blockedReason: String? = null,
) {
    val longUnusedApps: List<StorageAppOptimizationItem>
        get() = items.filter(StorageAppOptimizationItem::longUnusedCandidate)
            .sortedWith(compareByDescending<StorageAppOptimizationItem> { it.unusedDays ?: 0 }.thenByDescending { it.totalBytes ?: it.apkBytes })

    val largestApps: List<StorageAppOptimizationItem>
        get() = items.sortedByDescending { it.totalBytes ?: it.apkBytes }
}

data class StorageCleanupTrendPoint(
    val day: LocalDate,
    val releasedBytes: Long,
    val deletedCount: Int,
    val failedCount: Int,
)

data class StorageCapacitySnapshot(
    val createdAt: Long,
    val totalBytes: Long,
    val freeBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
}

data class StorageProductDashboard(
    val overview: DeviceStorageOverview,
    val deviceGuard: StorageDeviceGuard,
    val permissions: StoragePermissionHealth,
    val compatibility: StorageCompatibilityReport,
    val cleanupHistory: List<StorageCleanupHistoryEntry>,
    val cleanupTrend: List<StorageCleanupTrendPoint>,
    val capacitySnapshots: List<StorageCapacitySnapshot>,
    val appAnalysis: StorageAppAnalysisState,
)

internal object StorageProductizationPolicy {
    fun unusedDays(lastUsedAt: Long?, firstInstallTime: Long, now: Long): Int? {
        val reference = when {
            lastUsedAt != null && lastUsedAt > 0L -> lastUsedAt
            firstInstallTime > 0L -> firstInstallTime
            else -> return null
        }
        return ((now - reference).coerceAtLeast(0L) / (24L * 60L * 60L * 1000L)).toInt()
    }

    fun longUnusedReason(unusedDays: Int?, usageKnown: Boolean): String {
        return when {
            !usageKnown -> "未获得使用情况访问权限，只展示占用信息"
            unusedDays == null -> "系统未提供可用的最近使用时间"
            unusedDays >= APP_UNUSED_DAYS -> "约 $unusedDays 天未使用，建议确认后进入系统页处理"
            else -> "最近约 $unusedDays 天内使用过"
        }
    }

    fun heavyWorkAllowed(batteryPercent: Int?, charging: Boolean, thermalStatus: Int?): Pair<Boolean, String> {
        if (thermalStatus != null && thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
            return false to "设备温度较高，已暂停批量应用分析"
        }
        if (!charging && batteryPercent != null && batteryPercent < LOW_BATTERY_PERCENT) {
            return false to "电量低于 $LOW_BATTERY_PERCENT%，连接电源后再继续分析"
        }
        return true to when {
            thermalStatus != null && thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> "设备温度偏高，将按小批次执行"
            charging -> "设备正在充电，可以继续批量分析"
            else -> "设备状态正常"
        }
    }
}

class StorageProductizationRepository(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val prefs = appContext.getSharedPreferences(PRODUCT_PREFS, Context.MODE_PRIVATE)
    private val appRepository = AppManagementRepository(appContext)
    private val storageRepository = StorageManagementRepository(appContext)
    private val historyStore = StorageCleanupHistoryStore(appContext)

    fun loadDashboard(): StorageProductDashboard {
        val overview = storageRepository.loadOverview()
        recordCapacitySnapshot(overview)
        val history = historyStore.load()
        return StorageProductDashboard(
            overview = overview,
            deviceGuard = readDeviceGuard(),
            permissions = readPermissionHealth(),
            compatibility = compatibilityReport(),
            cleanupHistory = history,
            cleanupTrend = buildCleanupTrend(history),
            capacitySnapshots = loadCapacitySnapshots(),
            appAnalysis = loadAppAnalysisState(),
        )
    }

    fun loadAppAnalysisState(): StorageAppAnalysisState {
        val candidates = candidateApps()
        val signature = packageSignature(candidates)
        val savedProgress = readProgress()
        val progress = if (savedProgress == null || savedProgress.packageSignature != signature || savedProgress.totalCount != candidates.size) {
            resetForCandidates(candidates, signature)
        } else {
            savedProgress
        }
        return StorageAppAnalysisState(
            progress = progress,
            items = readAppResults(),
            usageAccessGranted = hasUsageAccess(),
            deviceGuard = readDeviceGuard(),
        )
    }

    fun resetAppAnalysis(): StorageAppAnalysisState {
        val candidates = candidateApps()
        val signature = packageSignature(candidates)
        val progress = resetForCandidates(candidates, signature)
        return StorageAppAnalysisState(
            progress = progress,
            items = emptyList(),
            usageAccessGranted = hasUsageAccess(),
            deviceGuard = readDeviceGuard(),
        )
    }

    fun analyzeNextAppPage(
        pageSize: Int = APP_SCAN_PAGE_SIZE,
        stopSignal: AtomicBoolean = AtomicBoolean(false),
    ): StorageAppAnalysisState {
        val candidates = candidateApps()
        val signature = packageSignature(candidates)
        var progress = readProgress()
        var results = readAppResults().toMutableList()
        if (progress == null || progress.packageSignature != signature || progress.totalCount != candidates.size) {
            progress = resetForCandidates(candidates, signature)
            results = mutableListOf()
        }
        val guard = readDeviceGuard()
        val usageGranted = hasUsageAccess()
        if (!guard.heavyWorkAllowed) {
            return StorageAppAnalysisState(
                progress = progress,
                items = results,
                usageAccessGranted = usageGranted,
                deviceGuard = guard,
                blockedReason = guard.reason,
            )
        }
        if (progress.complete) {
            return StorageAppAnalysisState(progress, results, usageGranted, guard)
        }
        val endExclusive = (progress.processedCount + pageSize.coerceIn(1, 40)).coerceAtMost(candidates.size)
        val usageMap = if (usageGranted) aggregateUsageStats() else emptyMap()
        var processed = progress.processedCount
        var interrupted = false
        while (processed < endExclusive) {
            if (stopSignal.get()) {
                interrupted = true
                break
            }
            val app = candidates[processed]
            analyzeApp(app, usageGranted, usageMap[app.packageName]?.lastTimeUsed)
                ?.let { analyzed ->
                    results.removeAll { it.packageName == analyzed.packageName }
                    results += analyzed
                    if (results.size > MAX_APP_RESULTS) {
                        results = results.sortedByDescending { it.totalBytes ?: it.apkBytes }
                            .take(MAX_APP_RESULTS)
                            .toMutableList()
                    }
                }
            processed += 1
            progress = progress.copy(
                processedCount = processed,
                updatedAt = System.currentTimeMillis(),
                complete = processed >= candidates.size,
                interrupted = false,
            )
            persistAppState(progress, results)
        }
        if (interrupted) {
            progress = progress.copy(updatedAt = System.currentTimeMillis(), interrupted = true)
            persistAppState(progress, results)
        }
        return StorageAppAnalysisState(
            progress = progress,
            items = results.sortedByDescending { it.totalBytes ?: it.apkBytes },
            usageAccessGranted = usageGranted,
            deviceGuard = guard,
        )
    }

    fun readDeviceGuard(): StorageDeviceGuard {
        val batteryIntent = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else null
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { appContext.getSystemService(PowerManager::class.java)?.currentThermalStatus }.getOrNull()
        } else {
            null
        }
        val (allowed, reason) = StorageProductizationPolicy.heavyWorkAllowed(batteryPercent, charging, thermalStatus)
        return StorageDeviceGuard(
            batteryPercent = batteryPercent,
            charging = charging,
            thermalStatus = thermalStatus,
            thermalLabel = thermalLabel(thermalStatus),
            heavyWorkAllowed = allowed,
            reason = reason,
        )
    }

    fun readPermissionHealth(): StoragePermissionHealth {
        val usage = hasUsageAccess()
        val legacyMedia = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
            hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        val images = legacyMedia || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hasPermission(Manifest.permission.READ_MEDIA_IMAGES))
        val videos = legacyMedia || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hasPermission(Manifest.permission.READ_MEDIA_VIDEO))
        val audio = legacyMedia || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hasPermission(Manifest.permission.READ_MEDIA_AUDIO))
        val selected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) && !images && !videos
        val treeUri = storageRepository.savedTreeUri()
        val persisted = appContext.contentResolver.persistedUriPermissions
        val validTree = treeUri == null || persisted.any { permission ->
            permission.uri == treeUri && permission.isReadPermission
        }
        return StoragePermissionHealth(
            usageAccessGranted = usage,
            imageAccessGranted = images,
            videoAccessGranted = videos,
            audioAccessGranted = audio,
            selectedPhotoAccessOnly = selected,
            authorizedFolderPresent = treeUri != null,
            authorizedFolderPermissionValid = validTree,
        )
    }

    fun compatibilityReport(): StorageCompatibilityReport {
        val manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "未知" }
        val brand = Build.BRAND.orEmpty().ifBlank { "未知" }
        val model = Build.MODEL.orEmpty().ifBlank { "未知" }
        val clean = "$manufacturer $brand".lowercase(Locale.ROOT)
        val guidance = when {
            listOf("xiaomi", "redmi", "poco").any(clean::contains) -> listOf(
                "MIUI/HyperOS 可能额外限制后台和文件访问，权限失效时请重新打开系统授权页。",
                "系统清理工具可能与本页同时修改媒体库，清理后建议重新扫描核验。",
            )
            listOf("huawei", "honor").any(clean::contains) -> listOf(
                "部分系统版本会主动回收使用情况访问权限，应用占用为空时请检查系统特殊访问权限。",
                "云图库占位文件可能无法生成缩略图，本页会跳过而不是误判。",
            )
            listOf("oppo", "realme", "oneplus").any(clean::contains) -> listOf(
                "ColorOS 系列可能对文档树授权做额外限制，目录失效后需要重新选择。",
                "系统媒体删除确认窗口由系统控制，取消后本页不会继续删除授权目录文件。",
            )
            listOf("vivo", "iqoo").any(clean::contains) -> listOf(
                "OriginOS/Funtouch OS 可能限制使用情况统计刷新，最近使用时间仅作为建议依据。",
                "高温保护触发后，批量应用分析会暂停，降温后可从断点继续。",
            )
            clean.contains("samsung") -> listOf(
                "Samsung 设备的设备维护可能自动清理缓存，本页显示值可能在刷新后变化。",
                "受限照片权限下只分析系统授予的媒体范围，不会推断未授权照片。",
            )
            else -> listOf(
                "不同厂商可能回收使用情况或目录授权；权限健康区会显示当前实际状态。",
                "所有建议都基于 Android 当前返回的数据，不会绕过系统权限读取隐藏目录。",
            )
        }
        return StorageCompatibilityReport(
            manufacturer = manufacturer,
            brand = brand,
            model = model,
            sdk = Build.VERSION.SDK_INT,
            title = "$brand $model · Android API ${Build.VERSION.SDK_INT}",
            guidance = guidance,
        )
    }

    private fun candidateApps(): List<ManagedAppSummary> {
        return appRepository.loadApps()
            .asSequence()
            .filter { !it.isSystemApp && it.isLaunchable && it.isEnabled }
            .sortedBy { it.packageName }
            .toList()
    }

    private fun analyzeApp(
        app: ManagedAppSummary,
        usageGranted: Boolean,
        lastUsedRaw: Long?,
    ): StorageAppOptimizationItem? {
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(app.packageName, 0)
            }
        }.getOrNull() ?: return null
        val storage = queryPackageStorage(app)
        val lastUsed = lastUsedRaw?.takeIf { it > 0L }
        val unusedDays = if (usageGranted) {
            StorageProductizationPolicy.unusedDays(lastUsed, packageInfo.firstInstallTime, System.currentTimeMillis())
        } else {
            null
        }
        return StorageAppOptimizationItem(
            label = app.label,
            packageName = app.packageName,
            apkBytes = app.apkBytes,
            appBytes = storage?.appBytes,
            dataBytes = storage?.dataBytes,
            cacheBytes = storage?.cacheBytes,
            totalBytes = storage?.let { it.appBytes + it.dataBytes + it.cacheBytes },
            firstInstallTime = packageInfo.firstInstallTime,
            lastUsedAt = lastUsed,
            unusedDays = unusedDays,
            isProtected = app.isProtected,
            suggestionReason = StorageProductizationPolicy.longUnusedReason(unusedDays, usageGranted),
        )
    }

    private fun queryPackageStorage(app: ManagedAppSummary): PackageStorage? {
        if (!hasUsageAccess()) return null
        val manager = appContext.getSystemService(StorageStatsManager::class.java) ?: return null
        return runCatching {
            val stats = manager.queryStatsForPackage(
                StorageManager.UUID_DEFAULT,
                app.packageName,
                UserHandle.getUserHandleForUid(app.uid),
            )
            PackageStorage(stats.appBytes, stats.dataBytes, stats.cacheBytes)
        }.getOrNull()
    }

    private fun aggregateUsageStats(): Map<String, android.app.usage.UsageStats> {
        val manager = appContext.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val end = System.currentTimeMillis()
        val begin = end - 370L * 24L * 60L * 60L * 1000L
        return runCatching { manager.queryAndAggregateUsageStats(begin, end) }.getOrDefault(emptyMap())
    }

    private fun hasUsageAccess(): Boolean {
        val manager = appContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            manager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), appContext.packageName)
        } else {
            @Suppress("DEPRECATION")
            manager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), appContext.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildCleanupTrend(history: List<StorageCleanupHistoryEntry>): List<StorageCleanupTrendPoint> {
        val zone = ZoneId.systemDefault()
        return history.groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
            .map { (day, entries) ->
                StorageCleanupTrendPoint(
                    day = day,
                    releasedBytes = entries.sumOf { it.releasedBytes },
                    deletedCount = entries.sumOf { it.deletedCount },
                    failedCount = entries.sumOf { it.failedCount },
                )
            }
            .sortedBy { it.day }
            .takeLast(14)
    }

    private fun recordCapacitySnapshot(overview: DeviceStorageOverview) {
        val existing = loadCapacitySnapshots().toMutableList()
        val now = System.currentTimeMillis()
        val last = existing.lastOrNull()
        val changedEnough = last == null || kotlin.math.abs(last.freeBytes - overview.freeBytes) >= 32L * 1024L * 1024L
        val intervalReached = last == null || now - last.createdAt >= MIN_CAPACITY_SNAPSHOT_INTERVAL_MS
        if (!changedEnough && !intervalReached) return
        existing += StorageCapacitySnapshot(now, overview.totalBytes, overview.freeBytes)
        writeCapacitySnapshots(existing.takeLast(MAX_CAPACITY_SNAPSHOTS))
    }

    private fun loadCapacitySnapshots(): List<StorageCapacitySnapshot> {
        val raw = prefs.getString(CAPACITY_SNAPSHOTS_KEY, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        StorageCapacitySnapshot(
                            createdAt = item.optLong("createdAt"),
                            totalBytes = item.optLong("totalBytes"),
                            freeBytes = item.optLong("freeBytes"),
                        ),
                    )
                }
            }.filter { it.totalBytes > 0L }.sortedBy { it.createdAt }
        }.getOrDefault(emptyList())
    }

    private fun writeCapacitySnapshots(items: List<StorageCapacitySnapshot>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("createdAt", item.createdAt)
                    .put("totalBytes", item.totalBytes)
                    .put("freeBytes", item.freeBytes),
            )
        }
        prefs.edit().putString(CAPACITY_SNAPSHOTS_KEY, array.toString()).apply()
    }

    private fun readProgress(): StorageAppScanProgress? {
        val raw = prefs.getString(APP_SCAN_PROGRESS_KEY, null).orEmpty()
        if (raw.isBlank()) return null
        return runCatching {
            val item = JSONObject(raw)
            StorageAppScanProgress(
                packageSignature = item.optString("packageSignature"),
                processedCount = item.optInt("processedCount"),
                totalCount = item.optInt("totalCount"),
                startedAt = item.optLong("startedAt"),
                updatedAt = item.optLong("updatedAt"),
                complete = item.optBoolean("complete"),
                interrupted = item.optBoolean("interrupted"),
            )
        }.getOrNull()
    }

    private fun readAppResults(): List<StorageAppOptimizationItem> {
        val raw = prefs.getString(APP_SCAN_RESULTS_KEY, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        StorageAppOptimizationItem(
                            label = item.optString("label"),
                            packageName = item.optString("packageName"),
                            apkBytes = item.optLong("apkBytes"),
                            appBytes = item.optionalLong("appBytes"),
                            dataBytes = item.optionalLong("dataBytes"),
                            cacheBytes = item.optionalLong("cacheBytes"),
                            totalBytes = item.optionalLong("totalBytes"),
                            firstInstallTime = item.optLong("firstInstallTime"),
                            lastUsedAt = item.optionalLong("lastUsedAt"),
                            unusedDays = item.optionalInt("unusedDays"),
                            isProtected = item.optBoolean("isProtected"),
                            suggestionReason = item.optString("suggestionReason"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistAppState(progress: StorageAppScanProgress, results: List<StorageAppOptimizationItem>) {
        val progressJson = JSONObject()
            .put("packageSignature", progress.packageSignature)
            .put("processedCount", progress.processedCount)
            .put("totalCount", progress.totalCount)
            .put("startedAt", progress.startedAt)
            .put("updatedAt", progress.updatedAt)
            .put("complete", progress.complete)
            .put("interrupted", progress.interrupted)
        val resultsJson = JSONArray()
        results.forEach { item ->
            resultsJson.put(
                JSONObject()
                    .put("label", item.label)
                    .put("packageName", item.packageName)
                    .put("apkBytes", item.apkBytes)
                    .putNullable("appBytes", item.appBytes)
                    .putNullable("dataBytes", item.dataBytes)
                    .putNullable("cacheBytes", item.cacheBytes)
                    .putNullable("totalBytes", item.totalBytes)
                    .put("firstInstallTime", item.firstInstallTime)
                    .putNullable("lastUsedAt", item.lastUsedAt)
                    .putNullable("unusedDays", item.unusedDays)
                    .put("isProtected", item.isProtected)
                    .put("suggestionReason", item.suggestionReason),
            )
        }
        prefs.edit()
            .putString(APP_SCAN_PROGRESS_KEY, progressJson.toString())
            .putString(APP_SCAN_RESULTS_KEY, resultsJson.toString())
            .commit()
    }

    private fun resetForCandidates(candidates: List<ManagedAppSummary>, signature: String): StorageAppScanProgress {
        val now = System.currentTimeMillis()
        val progress = StorageAppScanProgress(
            packageSignature = signature,
            processedCount = 0,
            totalCount = candidates.size,
            startedAt = now,
            updatedAt = now,
            complete = candidates.isEmpty(),
            interrupted = false,
        )
        persistAppState(progress, emptyList())
        return progress
    }

    private fun packageSignature(candidates: List<ManagedAppSummary>): String {
        return candidates.joinToString("|") { "${it.packageName}:${it.uid}" }.hashCode().toString()
    }

    private fun thermalLabel(status: Int?): String = when (status) {
        null -> "系统未提供"
        PowerManager.THERMAL_STATUS_NONE -> "正常"
        PowerManager.THERMAL_STATUS_LIGHT -> "轻微升温"
        PowerManager.THERMAL_STATUS_MODERATE -> "温度偏高"
        PowerManager.THERMAL_STATUS_SEVERE -> "高温"
        PowerManager.THERMAL_STATUS_CRITICAL -> "严重高温"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "紧急"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "即将关机"
        else -> "未知"
    }

    private fun JSONObject.optionalLong(key: String): Long? {
        return if (has(key) && !isNull(key)) optLong(key) else null
    }

    private fun JSONObject.optionalInt(key: String): Int? {
        return if (has(key) && !isNull(key)) optInt(key) else null
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject {
        return put(key, value ?: JSONObject.NULL)
    }

    private data class PackageStorage(
        val appBytes: Long,
        val dataBytes: Long,
        val cacheBytes: Long,
    )
}

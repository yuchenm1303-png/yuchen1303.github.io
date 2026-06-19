package com.yuchen.ailedger.service

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.text.Normalizer

/**
 * Builds a lightweight local index of launchable apps installed on the device.
 * This only opens apps through their launcher activity; it does not inspect or operate inside other apps.
 */
data class InstalledAppEntry(
    val label: String,
    val packageName: String,
)

enum class ExplicitAppResolutionStatus {
    Exact,
    Ambiguous,
    NotFound,
}

data class ExplicitAppResolution(
    val status: ExplicitAppResolutionStatus,
    val app: InstalledAppEntry? = null,
    val candidates: List<InstalledAppEntry> = emptyList(),
)

class InstalledAppIndex(
    private val context: Context,
) {
    private var cachedApps: List<InstalledAppEntry> = emptyList()
    private var lastLoadedAt: Long = 0L
    private var cachedQueryRows: List<IndexedAppRow> = emptyList()

    fun findBestApp(rawQuery: String): InstalledAppEntry? {
        return findCandidateApps(rawQuery, limit = 1).firstOrNull()
            ?: knownAppFallback(aliasCandidates(normalizeAppName(rawQuery)))
    }

    fun findCandidateApps(rawQuery: String, limit: Int = 12): List<InstalledAppEntry> {
        val query = normalizeAppName(rawQuery)
        if (query.isBlank()) return emptyList()
        val directCandidates = queryFragments(query)
        val installedMatches = getIndexedRows()
            .mapNotNull { row ->
                val score = scoreAppMention(query, directCandidates, row)
                if (score <= 0) null else row.app to score
            }
            .sortedWith(
                compareByDescending<Pair<InstalledAppEntry, Int>> { it.second }
                    .thenBy { normalizeAppName(it.first.label).length }
                    .thenBy { it.first.label }
            )
            .map { it.first }
            .distinctBy { it.packageName }
            .take(limit)
        if (installedMatches.isNotEmpty()) return installedMatches

        return knownAppFallback(aliasCandidates(query))?.let { listOf(it) }.orEmpty()
    }

    fun aliasesFor(app: InstalledAppEntry): List<String> {
        val packageAliases = KNOWN_ALIASES_BY_PACKAGE[app.packageName].orEmpty()
        val normalizedLabel = normalizeAppName(app.label)
        val packageTail = app.packageName.substringAfterLast('.').takeIf { it.isNotBlank() }.orEmpty()
        return (listOf(app.label, normalizedLabel, packageTail) + packageAliases + aliasCandidates(normalizedLabel))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { normalizeAppName(it) }
    }

    fun resolveExplicitAppName(
        appName: String,
        packageName: String? = null,
    ): ExplicitAppResolution {
        return resolveExplicitAppNameInEntries(
            apps = getLaunchableApps(false),
            appName = appName,
            packageName = packageName,
            aliasesForPackage = ::aliasesFor,
        )
    }

    fun getLaunchableApps(forceReload: Boolean = false): List<InstalledAppEntry> {
        val now = System.currentTimeMillis()
        if (!forceReload && cachedApps.isNotEmpty() && now - lastLoadedAt < CACHE_TTL_MS) return cachedApps

        val packageManager = context.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val launcherApps = packageManager.queryIntentActivities(launchIntent, launcherQueryFlags())
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName.orEmpty()
                if (packageName.isBlank()) return@mapNotNull null
                val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                    .ifBlank { safeApplicationLabel(packageManager, packageName) }
                if (label.isBlank()) return@mapNotNull null
                if (packageManager.getLaunchIntentForPackage(packageName) == null) return@mapNotNull null
                InstalledAppEntry(label = label, packageName = packageName)
            }

        val verifiedKnownApps = KNOWN_APPS.mapNotNull { known ->
            if (packageManager.getLaunchIntentForPackage(known.packageName) == null) return@mapNotNull null
            val label = safeApplicationLabel(packageManager, known.packageName).ifBlank { known.label }
            InstalledAppEntry(label = label, packageName = known.packageName)
        }

        val apps = (launcherApps + verifiedKnownApps)
            .distinctBy { it.packageName }
            .sortedWith(compareBy<InstalledAppEntry> { normalizeAppName(it.label) }.thenBy { it.packageName })

        cachedApps = apps
        cachedQueryRows = emptyList()
        lastLoadedAt = now
        return apps
    }

    private fun getIndexedRows(): List<IndexedAppRow> {
        if (cachedQueryRows.isNotEmpty()) return cachedQueryRows
        cachedQueryRows = getLaunchableApps().map { app ->
            val aliases = aliasesFor(app).map(::normalizeAppName).filter { it.length >= MIN_ALIAS_MATCH_LENGTH }.distinct()
            IndexedAppRow(app = app, label = normalizeAppName(app.label), aliases = aliases)
        }
        return cachedQueryRows
    }

    private fun launcherQueryFlags(): Int {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) PackageManager.MATCH_ALL else 0
    }

    private fun safeApplicationLabel(packageManager: PackageManager, packageName: String): String {
        return runCatching {
            val info: ApplicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info)?.toString()?.trim().orEmpty()
        }.getOrDefault("")
    }

    private fun knownAppFallback(candidates: List<String>): InstalledAppEntry? {
        return KNOWN_APPS.firstOrNull { app ->
            val aliases = aliasesFor(app).map(::normalizeAppName)
            candidates.any { candidate -> aliases.any { alias -> scoreNameMatch(candidate, alias) > 0 || scoreNameMatch(candidate, normalizeAppName(app.label)) > 0 } }
        }?.takeIf { context.packageManager.getLaunchIntentForPackage(it.packageName) != null }
    }

    private fun scoreAppMention(query: String, queryFragments: List<String>, row: IndexedAppRow): Int {
        if (row.label.isBlank()) return 0
        val allNeedles = (listOf(query) + queryFragments).filter { it.length >= MIN_ALIAS_MATCH_LENGTH }.distinct()
        val aliasScore = allNeedles.maxOfOrNull { needle ->
            row.aliases.maxOfOrNull { alias -> scoreNameMatch(needle, alias) } ?: 0
        } ?: 0
        val labelScore = allNeedles.maxOfOrNull { needle -> scoreNameMatch(needle, row.label) } ?: 0
        return maxOf(aliasScore, labelScore)
    }

    private fun queryFragments(query: String): List<String> {
        if (query.length <= MAX_QUERY_FRAGMENT_LENGTH) return listOf(query)
        val segments = Regex("[\\p{L}\\p{N}]{2,}").findAll(query).map { it.value }.toList()
        val windows = buildList {
            val maxLen = minOf(MAX_QUERY_FRAGMENT_LENGTH, query.length)
            for (length in MIN_ALIAS_MATCH_LENGTH..maxLen) {
                for (start in 0..query.length - length) add(query.substring(start, start + length))
            }
        }
        return (segments + windows).distinct().take(200)
    }

    private fun aliasCandidates(query: String): List<String> {
        val aliases = when (query) {
            "b站", "bili", "bilibili", "哔哩", "哔哩哔哩" -> listOf("哔哩哔哩", "bilibili", "b站", "哔哩")
            "微信", "wechat", "wx" -> listOf("微信", "wechat", "wx")
            "支付宝", "alipay" -> listOf("支付宝", "alipay")
            "qq", "腾讯qq" -> listOf("qq", "腾讯qq")
            "高德", "高德地图", "amap" -> listOf("高德", "高德地图", "amap")
            "百度地图", "百度", "baidumap" -> listOf("百度地图", "百度", "baidumap")
            "淘宝", "taobao" -> listOf("淘宝", "taobao")
            "京东", "jd" -> listOf("京东", "jd")
            "抖音", "douyin" -> listOf("抖音", "douyin")
            "小红书" -> listOf("小红书")
            else -> emptyList()
        }
        return (listOf(query) + aliases.map(::normalizeAppName)).distinct().filter { it.isNotBlank() }
    }

    private fun scoreNameMatch(query: String, label: String): Int {
        if (query == label) return 1000 + query.length
        if (label.startsWith(query) && query.length >= MIN_ALIAS_MATCH_LENGTH) return 900 - (label.length - query.length).coerceAtLeast(0)
        if (label.contains(query) && query.length >= MIN_ALIAS_MATCH_LENGTH) return 760 - (label.length - query.length).coerceAtLeast(0)
        if (query.contains(label) && label.length >= MIN_ALIAS_MATCH_LENGTH) return 620 - (query.length - label.length).coerceAtLeast(0)
        return 0
    }

    private fun normalizeAppName(value: String): String {
        return Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFKC)
            .replace(Regex("\\s+"), "")
            .replace(Regex("[·・.。_\\-]+"), "")
            .removeSuffix("app")
            .removeSuffix("应用")
    }

    private data class IndexedAppRow(
        val app: InstalledAppEntry,
        val label: String,
        val aliases: List<String>,
    )

    companion object {
        private const val CACHE_TTL_MS = 5 * 60_000L
        private const val MIN_ALIAS_MATCH_LENGTH = 2
        private const val MAX_QUERY_FRAGMENT_LENGTH = 10
        private val KNOWN_ALIASES_BY_PACKAGE = mapOf(
            "com.tencent.mm" to listOf("微信", "wechat", "wx"),
            "com.eg.android.AlipayGphone" to listOf("支付宝", "alipay"),
            "com.autonavi.minimap" to listOf("高德", "高德地图", "amap"),
            "com.baidu.BaiduMap" to listOf("百度地图", "百度", "baidumap"),
            "com.tencent.mobileqq" to listOf("QQ", "腾讯QQ"),
            "com.taobao.taobao" to listOf("淘宝", "taobao"),
            "com.jingdong.app.mall" to listOf("京东", "jd"),
            "tv.danmaku.bili" to listOf("哔哩哔哩", "哔哩", "B站", "bilibili", "bili"),
            "com.ss.android.ugc.aweme" to listOf("抖音", "douyin"),
            "com.xingin.xhs" to listOf("小红书")
        )
        private val KNOWN_APPS = listOf(
            InstalledAppEntry("微信", "com.tencent.mm"),
            InstalledAppEntry("支付宝", "com.eg.android.AlipayGphone"),
            InstalledAppEntry("高德地图", "com.autonavi.minimap"),
            InstalledAppEntry("百度地图", "com.baidu.BaiduMap"),
            InstalledAppEntry("QQ", "com.tencent.mobileqq"),
            InstalledAppEntry("淘宝", "com.taobao.taobao"),
            InstalledAppEntry("京东", "com.jingdong.app.mall"),
            InstalledAppEntry("哔哩哔哩", "tv.danmaku.bili"),
            InstalledAppEntry("抖音", "com.ss.android.ugc.aweme"),
            InstalledAppEntry("小红书", "com.xingin.xhs")
        )

        fun resolveExplicitAppNameInEntries(
            apps: List<InstalledAppEntry>,
            appName: String,
            packageName: String? = null,
            aliasesForPackage: (InstalledAppEntry) -> List<String> = { emptyList() },
        ): ExplicitAppResolution {
            val normalizedPackage = packageName.orEmpty().trim()
            if (normalizedPackage.isNotBlank()) {
                val packageMatches = apps.filter { it.packageName == normalizedPackage }
                return when (packageMatches.size) {
                    1 -> ExplicitAppResolution(ExplicitAppResolutionStatus.Exact, packageMatches.first(), packageMatches)
                    0 -> ExplicitAppResolution(ExplicitAppResolutionStatus.NotFound)
                    else -> ExplicitAppResolution(ExplicitAppResolutionStatus.Ambiguous, candidates = packageMatches)
                }
            }

            val query = normalizeExplicitName(appName)
            if (query.isBlank()) return ExplicitAppResolution(ExplicitAppResolutionStatus.NotFound)
            val matches = apps.filter { app ->
                (listOf(app.label) + aliasesForPackage(app))
                    .map(::normalizeExplicitName)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .any { it == query }
            }.distinctBy { it.packageName }

            return when (matches.size) {
                1 -> ExplicitAppResolution(ExplicitAppResolutionStatus.Exact, matches.first(), matches)
                0 -> ExplicitAppResolution(ExplicitAppResolutionStatus.NotFound)
                else -> ExplicitAppResolution(ExplicitAppResolutionStatus.Ambiguous, candidates = matches)
            }
        }

        private fun normalizeExplicitName(value: String): String {
            return Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFKC)
                .replace(Regex("\\s+"), "")
                .replace(Regex("[·・.。_\\-]+"), "")
                .removeSuffix("app")
                .removeSuffix("应用")
        }
    }
}

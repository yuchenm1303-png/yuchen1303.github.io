package com.yuchen.ailedger.service

import android.content.Context
import android.content.Intent
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

class InstalledAppIndex(
    private val context: Context,
) {
    private var cachedApps: List<InstalledAppEntry> = emptyList()
    private var lastLoadedAt: Long = 0L

    fun findBestApp(rawQuery: String): InstalledAppEntry? {
        val query = normalizeAppName(rawQuery)
        if (query.isBlank()) return null

        val installedMatch = getLaunchableApps()
            .mapNotNull { app ->
                val score = scoreAppMention(query, app)
                if (score <= 0) null else app to score
            }
            .maxWithOrNull(
                compareBy<Pair<InstalledAppEntry, Int>> { it.second }
                    .thenByDescending { normalizeAppName(it.first.label).length }
                    .thenBy { it.first.label }
            )
            ?.first
        if (installedMatch != null) return installedMatch

        return knownAppFallback(aliasCandidates(query))
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

    fun getLaunchableApps(forceReload: Boolean = false): List<InstalledAppEntry> {
        val now = System.currentTimeMillis()
        if (!forceReload && cachedApps.isNotEmpty() && now - lastLoadedAt < CACHE_TTL_MS) return cachedApps

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val packageManager = context.packageManager
        val apps = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName.orEmpty()
                if (packageName.isBlank()) return@mapNotNull null
                val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                if (label.isBlank()) return@mapNotNull null
                InstalledAppEntry(label = label, packageName = packageName)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label }

        cachedApps = apps
        lastLoadedAt = now
        return apps
    }

    private fun knownAppFallback(candidates: List<String>): InstalledAppEntry? {
        return KNOWN_APPS.firstOrNull { app ->
            val aliases = aliasesFor(app).map(::normalizeAppName)
            candidates.any { candidate -> aliases.any { alias -> scoreNameMatch(candidate, alias) > 0 || scoreNameMatch(candidate, normalizeAppName(app.label)) > 0 } }
        }
    }

    private fun scoreAppMention(query: String, app: InstalledAppEntry): Int {
        val label = normalizeAppName(app.label)
        if (label.isBlank()) return 0
        val aliases = aliasesFor(app).map(::normalizeAppName).filter { it.length >= MIN_ALIAS_MATCH_LENGTH }.distinct()
        val aliasScore = aliases.maxOfOrNull { alias ->
            when {
                query == alias -> 1200 + alias.length
                query.startsWith(alias) -> 1050 + alias.length
                query.contains(alias) -> 900 + alias.length
                alias.contains(query) && query.length >= MIN_ALIAS_MATCH_LENGTH -> 760 + query.length
                else -> 0
            }
        } ?: 0
        val labelScore = scoreNameMatch(query, label)
        return maxOf(aliasScore, labelScore)
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
        if (query == label) return 1000
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

    companion object {
        private const val CACHE_TTL_MS = 60_000L
        private const val MIN_ALIAS_MATCH_LENGTH = 2
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
    }
}
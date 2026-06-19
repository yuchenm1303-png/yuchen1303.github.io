package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstalledAppIndexExplicitResolutionTest {
    private val apps = listOf(
        InstalledAppEntry("QQ", "com.tencent.mobileqq"),
        InstalledAppEntry("QQ音乐", "com.tencent.qqmusic"),
        InstalledAppEntry("京东", "com.jingdong.app.mall"),
        InstalledAppEntry("京东方A", "com.boe.stock"),
        InstalledAppEntry("腾讯地图", "com.tencent.map"),
        InstalledAppEntry("腾讯控股", "com.tencent.holdings"),
    )

    private val aliases: (InstalledAppEntry) -> List<String> = { app ->
        when (app.packageName) {
            "com.tencent.mobileqq" -> listOf("腾讯QQ")
            "com.jingdong.app.mall" -> listOf("JD")
            else -> emptyList()
        }
    }

    @Test
    fun exactNameAndAliasResolveOnlyExactMatches() {
        val qq = resolve("QQ")
        assertEquals(ExplicitAppResolutionStatus.Exact, qq.status)
        assertEquals("com.tencent.mobileqq", qq.app?.packageName)

        val alias = resolve("腾讯QQ")
        assertEquals(ExplicitAppResolutionStatus.Exact, alias.status)
        assertEquals("com.tencent.mobileqq", alias.app?.packageName)
    }

    @Test
    fun exactNameDoesNotUseContainsOrPrefixMatching() {
        assertEquals("com.tencent.mobileqq", resolve("QQ").app?.packageName)
        assertEquals(ExplicitAppResolutionStatus.NotFound, resolve("腾讯").status)
        assertEquals(ExplicitAppResolutionStatus.NotFound, resolve("京").status)
        assertEquals(ExplicitAppResolutionStatus.Exact, resolve("京东").status)
        assertEquals("com.tencent.map", resolve("腾讯地图").app?.packageName)
    }

    @Test
    fun explicitNameNormalizesPunctuationWidthAndAppSuffix() {
        assertEquals("com.tencent.mobileqq", resolve("ＱＱ App").app?.packageName)
        assertEquals("com.jingdong.app.mall", resolve("京东应用").app?.packageName)
        assertEquals("com.tencent.map", resolve("腾讯・地图").app?.packageName)
        assertEquals("com.tencent.mobileqq", resolve("腾讯-QQ应用").app?.packageName)
    }

    @Test
    fun notFoundAndAmbiguousAreExplicit() {
        val notFound = resolve("不存在的 App")
        assertEquals(ExplicitAppResolutionStatus.NotFound, notFound.status)
        assertNull(notFound.app)

        val duplicate = InstalledAppIndex.resolveExplicitAppNameInEntries(
            apps = apps + InstalledAppEntry("QQ", "example.duplicate.qq"),
            appName = "QQ",
            aliasesForPackage = aliases,
        )
        assertEquals(ExplicitAppResolutionStatus.Ambiguous, duplicate.status)
        assertNull(duplicate.app)
    }

    @Test
    fun packageNameExactMatchHasPriority() {
        val result = InstalledAppIndex.resolveExplicitAppNameInEntries(
            apps = apps,
            appName = "wrong",
            packageName = "com.tencent.mobileqq",
            aliasesForPackage = aliases,
        )
        assertEquals(ExplicitAppResolutionStatus.Exact, result.status)
        assertEquals("QQ", result.app?.label)
    }

    private fun resolve(name: String): ExplicitAppResolution {
        return InstalledAppIndex.resolveExplicitAppNameInEntries(
            apps = apps,
            appName = name,
            aliasesForPackage = aliases,
        )
    }
}

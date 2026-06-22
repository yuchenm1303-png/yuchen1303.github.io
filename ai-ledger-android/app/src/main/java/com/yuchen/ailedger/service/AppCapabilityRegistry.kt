package com.yuchen.ailedger.service

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Collects factual, device-local app metadata for cloud models.
 *
 * This class must never inspect the user's instruction, rank apps, select an app, or alter a
 * cloud-selected route. Labels, aliases and capabilities are evidence only; DeepSeek remains the
 * semantic owner of app selection.
 */
class AppCapabilityRegistry(
    context: Context,
    private val installedAppIndex: InstalledAppIndex = InstalledAppIndex(context.applicationContext),
) {
    private val applicationContext = context.applicationContext
    private val packageManager = applicationContext.packageManager

    private val browserPackages by lazy {
        resolvePackages(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            },
        )
    }
    private val settingsPackages by lazy {
        resolvePackages(Intent(Settings.ACTION_SETTINGS))
    }
    private val homePackages by lazy {
        resolvePackages(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            },
        )
    }
    private val mapPackages by lazy { resolvePackages(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=0,0"))) }
    private val cameraPackages by lazy { resolvePackages(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)) }
    private val dialerPackages by lazy { resolvePackages(Intent(Intent.ACTION_DIAL, Uri.parse("tel:10086"))) }
    private val emailPackages by lazy { resolvePackages(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:test@example.com"))) }
    private val smsPackages by lazy { resolvePackages(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:10086"))) }

    fun buildVisualContext(apps: List<InstalledAppEntry>): List<VisualAgentAppContextItem> {
        return apps
            .asSequence()
            .distinctBy { it.packageName }
            .map { app ->
                val profile = profileFor(app)
                VisualAgentAppContextItem(
                    label = app.label.trim(),
                    packageName = app.packageName.trim(),
                    aliases = installedAppIndex.aliasesFor(app)
                        .asSequence()
                        .map(String::trim)
                        .filter(String::isNotBlank)
                        .distinct()
                        .sorted()
                        .toList(),
                    capabilities = profile.capabilities.sorted(),
                )
            }
            .filter { it.label.isNotBlank() && it.packageName.isNotBlank() }
            .sortedWith(compareBy<VisualAgentAppContextItem> { it.label.lowercase() }.thenBy { it.packageName })
            .take(MAX_VISUAL_CONTEXT_ITEMS)
            .toList()
    }

    fun profileFor(app: InstalledAppEntry): AppCapabilityProfile {
        val capabilities = linkedSetOf(AppCapability.NativeApp)
        if (app.packageName in browserPackages) capabilities += AppCapability.Browser
        if (app.packageName in settingsPackages) capabilities += AppCapability.SystemSettings
        if (app.packageName in homePackages) capabilities += AppCapability.HomeLauncher
        if (app.packageName in mapPackages) capabilities += AppCapability.Maps
        if (app.packageName in cameraPackages) capabilities += AppCapability.Camera
        if (app.packageName in dialerPackages) capabilities += AppCapability.Dialer
        if (app.packageName in emailPackages) capabilities += AppCapability.Email
        if (app.packageName in smsPackages) capabilities += AppCapability.Sms
        capabilities += KNOWN_PACKAGE_CAPABILITIES[app.packageName].orEmpty()

        val applicationInfo = runCatching {
            packageManager.getApplicationInfo(app.packageName, 0)
        }.getOrNull()
        if (applicationInfo != null && applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
            capabilities += AppCapability.SystemApp
        } else {
            capabilities += AppCapability.UserApp
        }
        applicationCategoryName(applicationInfo)?.let { capabilities += it }

        return AppCapabilityProfile(
            app = app,
            capabilities = capabilities,
        )
    }

    /**
     * Compatibility-only API for older callers. It deliberately never approves or rejects an app
     * semantically: DeepSeek owns selection, while Android validates only exact package identity at
     * execution time.
     */
    @Deprecated("App selection belongs to DeepSeek; this compatibility method never makes a selection decision.")
    @Suppress("UNUSED_PARAMETER")
    fun validateSelection(
        contract: AgentTaskExecutionContract,
        app: InstalledAppEntry,
        userExplicitlyNamed: Boolean = false,
    ): AppSelectionValidation = AppSelectionValidation(ok = true)

    fun compactPromptLine(items: List<VisualAgentAppContextItem>): String {
        val specialItems = items
            .asSequence()
            .map { item -> item to item.capabilities.filterNot { it in LOW_SIGNAL_CAPABILITIES } }
            .filter { (_, capabilities) -> capabilities.isNotEmpty() }
            .take(MAX_PROMPT_ITEMS)
            .joinToString(";") { (item, capabilities) ->
                "${item.label.take(28)}=${item.packageName.take(60)}[${capabilities.joinToString(",")}]"
            }
        return "installed_app_facts:v2|selectionOwner=deepseek|${specialItems.ifBlank { "none" }}".take(MAX_PROMPT_CHARS)
    }

    private fun resolvePackages(intent: Intent): Set<String> {
        return runCatching {
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNull { it.activityInfo?.packageName?.takeIf(String::isNotBlank) }
                .toSet()
        }.getOrDefault(emptySet())
    }

    private fun applicationCategoryName(info: ApplicationInfo?): String? {
        if (info == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return when (info.category) {
            ApplicationInfo.CATEGORY_GAME -> "android_category_game"
            ApplicationInfo.CATEGORY_AUDIO -> "android_category_audio"
            ApplicationInfo.CATEGORY_VIDEO -> "android_category_video"
            ApplicationInfo.CATEGORY_IMAGE -> "android_category_image"
            ApplicationInfo.CATEGORY_SOCIAL -> "android_category_social"
            ApplicationInfo.CATEGORY_NEWS -> "android_category_news"
            ApplicationInfo.CATEGORY_MAPS -> "android_category_maps"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "android_category_productivity"
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> "android_category_accessibility"
            else -> null
        }
    }

    companion object {
        private const val MAX_VISUAL_CONTEXT_ITEMS = 160
        private const val MAX_PROMPT_ITEMS = 24
        private const val MAX_PROMPT_CHARS = 1_150
        private val LOW_SIGNAL_CAPABILITIES = setOf(
            AppCapability.NativeApp,
            AppCapability.SystemApp,
            AppCapability.UserApp,
        )

        /**
         * Package identities are stable factual evidence. They are never matched against user text
         * and never used locally to choose or launch an app.
         */
        private val KNOWN_PACKAGE_CAPABILITIES: Map<String, Set<String>> = mapOf(
            "com.hexin.plat.android" to setOf("finance", "securities_market", "securities_trading", "order_entry"),
            "com.eastmoney.android.berlin" to setOf("finance", "securities_market", "securities_trading", "order_entry"),
            "com.tencent.mobileqq" to setOf("social_chat", "messaging"),
            "com.tencent.mm" to setOf("social_chat", "messaging", "payments"),
            "com.jingdong.app.mall" to setOf("ecommerce", "shopping", "order_entry"),
            "com.taobao.taobao" to setOf("ecommerce", "shopping", "order_entry"),
            "com.autonavi.minimap" to setOf("maps", "navigation"),
            "com.baidu.BaiduMap" to setOf("maps", "navigation"),
            "com.sankuai.meituan" to setOf("local_services", "food_delivery", "order_entry"),
            "me.ele" to setOf("food_delivery", "order_entry"),
        )

        /**
         * Compatibility-only helper. Capability facts are uploaded to the cloud; Android must not
         * use them to accept, reject, rank or replace a cloud-selected application.
         */
        @Suppress("UNUSED_PARAMETER")
        internal fun validateCapabilities(
            contract: AgentTaskExecutionContract,
            capabilities: Set<String>,
            appLabel: String = "目标应用",
        ): AppSelectionValidation = AppSelectionValidation(ok = true)
    }
}

data class AppCapabilityProfile(
    val app: InstalledAppEntry,
    val capabilities: Set<String>,
)

data class AppSelectionValidation(
    val ok: Boolean,
    val message: String = "",
)

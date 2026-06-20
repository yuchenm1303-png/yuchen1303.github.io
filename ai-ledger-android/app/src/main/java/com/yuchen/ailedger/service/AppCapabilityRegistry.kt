package com.yuchen.ailedger.service

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

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
        return apps.map { app ->
            val profile = profileFor(app)
            VisualAgentAppContextItem(
                label = app.label,
                packageName = app.packageName,
                aliases = installedAppIndex.aliasesFor(app),
                capabilities = profile.capabilities.sorted(),
            )
        }
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

    fun validateSelection(
        contract: AgentTaskExecutionContract,
        app: InstalledAppEntry,
        userExplicitlyNamed: Boolean = false,
    ): AppSelectionValidation {
        if (userExplicitlyNamed) return AppSelectionValidation(true)
        return validateCapabilities(contract, profileFor(app).capabilities, app.label)
    }

    fun compactPromptLine(items: List<VisualAgentAppContextItem>): String {
        val specialItems = items
            .asSequence()
            .map { item -> item to item.capabilities.filterNot { it in LOW_SIGNAL_CAPABILITIES } }
            .filter { (_, capabilities) -> capabilities.isNotEmpty() }
            .take(MAX_PROMPT_ITEMS)
            .joinToString(";") { (item, capabilities) ->
                "${item.label.take(28)}=${item.packageName.take(60)}[${capabilities.joinToString(",")}]"
            }
        return "installed_app_roles:v1|${specialItems.ifBlank { "none" }}".take(MAX_PROMPT_CHARS)
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
        private const val MAX_PROMPT_ITEMS = 24
        private const val MAX_PROMPT_CHARS = 1_150
        private val LOW_SIGNAL_CAPABILITIES = setOf(
            AppCapability.NativeApp,
            AppCapability.SystemApp,
            AppCapability.UserApp,
        )

        internal fun validateCapabilities(
            contract: AgentTaskExecutionContract,
            capabilities: Set<String>,
            appLabel: String = "目标应用",
        ): AppSelectionValidation {
            if (contract.preferredSurface == AgentSurfacePreference.SystemSettings &&
                AppCapability.SystemSettings !in capabilities
            ) {
                return AppSelectionValidation(
                    ok = false,
                    message = "$appLabel 不是系统设置入口；当前任务必须进入设备系统设置后再继续。",
                )
            }

            if (!contract.browserFallbackAllowed && AppCapability.Browser in capabilities) {
                return AppSelectionValidation(
                    ok = false,
                    message = "$appLabel 是浏览器，但当前任务要求优先原生应用或系统设置；请重新选择具备任务能力的已安装应用。",
                )
            }

            val missing = contract.requiredCapabilities.filterNot(capabilities::contains)
            if (missing.isNotEmpty() && contract.preferredSurface != AgentSurfacePreference.Any) {
                return AppSelectionValidation(
                    ok = false,
                    message = "$appLabel 缺少任务契约要求的能力：${missing.joinToString(",")}。",
                )
            }

            return AppSelectionValidation(true)
        }
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

package com.yuchen.ailedger.service

import android.os.Build
import java.text.Normalizer

enum class AgentSurfacePreference(val wireValue: String) {
    Any("any"),
    NativeApp("native_app"),
    SystemSettings("system_settings"),
    Browser("browser"),
}

data class AgentTaskExecutionContract(
    val preferredSurface: AgentSurfacePreference = AgentSurfacePreference.Any,
    val browserFallbackAllowed: Boolean = true,
    val requiredCapabilities: Set<String> = emptySet(),
    val requirePostActionVerification: Boolean = true,
    val highImpactFlow: Boolean = false,
    val reason: String = "",
) {
    fun toPromptLine(): String {
        val capabilities = requiredCapabilities.sorted().joinToString(",").ifBlank { "none" }
        return buildString {
            append("task_contract:v1")
            append("|preferredSurface=").append(preferredSurface.wireValue)
            append("|browserFallbackAllowed=").append(browserFallbackAllowed)
            append("|requiredCapabilities=").append(capabilities)
            append("|postActionVerification=").append(requirePostActionVerification)
            append("|highImpactFlow=").append(highImpactFlow)
            reason.takeIf { it.isNotBlank() }?.let { append("|reason=").append(it.take(160)) }
        }
    }

    companion object {
        fun fromGoal(goal: String): AgentTaskExecutionContract {
            val normalized = normalizeGoal(goal)
            val explicitBrowser = containsAny(normalized, EXPLICIT_BROWSER_MARKERS) || URL_PATTERN.containsMatchIn(goal)
            val systemSettings = containsAny(normalized, SYSTEM_SETTINGS_MARKERS)
            val highImpact = containsAny(normalized, HIGH_IMPACT_MARKERS)

            return when {
                systemSettings -> AgentTaskExecutionContract(
                    preferredSurface = AgentSurfacePreference.SystemSettings,
                    browserFallbackAllowed = false,
                    requiredCapabilities = setOf(AppCapability.SystemSettings),
                    highImpactFlow = highImpact,
                    reason = "任务目标属于设备系统设置，应进入系统设置界面并逐步验证路径。",
                )
                explicitBrowser -> AgentTaskExecutionContract(
                    preferredSurface = AgentSurfacePreference.Browser,
                    browserFallbackAllowed = true,
                    requiredCapabilities = setOf(AppCapability.Browser),
                    highImpactFlow = highImpact,
                    reason = "用户明确要求网页、网站或浏览器表面。",
                )
                highImpact -> AgentTaskExecutionContract(
                    preferredSurface = AgentSurfacePreference.NativeApp,
                    browserFallbackAllowed = false,
                    requiredCapabilities = setOf(AppCapability.NativeApp),
                    highImpactFlow = true,
                    reason = "任务包含交易、提交或发送等高影响操作，应优先使用具备对应能力的原生应用。",
                )
                else -> AgentTaskExecutionContract(
                    preferredSurface = AgentSurfacePreference.Any,
                    browserFallbackAllowed = true,
                    requirePostActionVerification = true,
                    reason = "未限定唯一交互表面，由模型结合已安装应用和当前页面选择。",
                )
            }
        }

        private fun normalizeGoal(value: String): String {
            return Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFKC)
                .replace(Regex("\\s+"), "")
        }

        private fun containsAny(value: String, markers: List<String>): Boolean {
            return markers.any(value::contains)
        }

        private val URL_PATTERN = Regex("(?i)https?://|www\\.")
        private val EXPLICIT_BROWSER_MARKERS = listOf(
            "浏览器", "网页", "网站", "网页版", "webpage", "website", "browser",
        )
        private val SYSTEM_SETTINGS_MARKERS = listOf(
            "开发人员选项", "开发者选项", "系统与更新", "系统设置", "手机设置",
            "蓝牙设置", "网络设置", "通知设置", "权限设置", "电池优化", "无障碍设置",
            "developeroptions", "systemsettings",
        )
        private val HIGH_IMPACT_MARKERS = listOf(
            "下单", "提交订单", "买入", "卖出", "交易", "支付", "付款", "转账",
            "发送消息", "发送邮件", "发布", "提交", "预约", "订票", "打车",
            "placeorder", "buy", "sell", "trade", "pay", "transfer", "send", "publish", "submit",
        )
    }
}

object AppCapability {
    const val NativeApp = "native_app"
    const val Browser = "browser"
    const val SystemSettings = "system_settings"
    const val HomeLauncher = "home_launcher"
    const val Maps = "maps"
    const val Camera = "camera"
    const val Dialer = "dialer"
    const val Email = "email"
    const val Sms = "sms"
    const val SystemApp = "system_app"
    const val UserApp = "user_app"
}

data class AgentDeviceProfile(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val release: String,
    val sdkInt: Int,
    val display: String,
) {
    fun toPromptLine(): String {
        return buildString {
            append("device_profile:v1")
            append("|manufacturer=").append(manufacturer.take(60))
            append("|brand=").append(brand.take(60))
            append("|model=").append(model.take(80))
            append("|androidRelease=").append(release.take(40))
            append("|sdkInt=").append(sdkInt)
            append("|buildDisplay=").append(display.take(100))
        }
    }

    companion object {
        fun current(): AgentDeviceProfile {
            return AgentDeviceProfile(
                manufacturer = Build.MANUFACTURER.orEmpty(),
                brand = Build.BRAND.orEmpty(),
                model = Build.MODEL.orEmpty(),
                release = Build.VERSION.RELEASE.orEmpty(),
                sdkInt = Build.VERSION.SDK_INT,
                display = Build.DISPLAY.orEmpty(),
            )
        }
    }
}

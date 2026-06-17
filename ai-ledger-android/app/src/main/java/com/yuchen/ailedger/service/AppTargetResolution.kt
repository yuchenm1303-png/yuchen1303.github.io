package com.yuchen.ailedger.service

import android.content.Context
import android.os.SystemClock
import java.text.Normalizer
import org.json.JSONArray
import org.json.JSONObject

object AppCapability {
    const val STOCK_QUOTE = "stock_quote"
    const val STOCK_SEARCH = "stock_search"
    const val SECURITIES_TRADE = "securities_trade"
    const val ORDER_ENTRY = "order_entry"
    const val NAVIGATION = "navigation"
    const val SHOPPING = "shopping"
    const val VIDEO = "video"
    const val MESSAGING = "messaging"
    const val PAYMENT = "payment"

    fun normalize(raw: String): String = when (
        raw.trim().lowercase().replace('-', '_').replace(' ', '_')
    ) {
        "stock", "stock_detail", "stock_quotes", "quote" -> STOCK_QUOTE
        "stock_lookup", "search_stock", "security_search" -> STOCK_SEARCH
        "securities", "stock_trade", "security_trade", "brokerage_trade" -> SECURITIES_TRADE
        "stock_order", "stock_order_entry", "securities_order_entry", "trade_order_entry" -> ORDER_ENTRY
        "maps", "map", "route", "directions" -> NAVIGATION
        "ecommerce", "commerce" -> SHOPPING
        "chat", "im" -> MESSAGING
        "pay", "wallet" -> PAYMENT
        else -> raw.trim().lowercase().replace('-', '_').replace(' ', '_')
    }
}

data class AppCapabilityProfile(
    val categories: Set<String>,
    val capabilities: Set<String>,
    val confidence: Float,
    val evidence: List<String>,
) {
    fun supports(required: Set<String>): Boolean = capabilities.containsAll(required)

    fun toJson(): JSONObject = JSONObject().apply {
        put("categories", JSONArray(categories.toList().sorted()))
        put("capabilities", JSONArray(capabilities.toList().sorted()))
        put("confidence", confidence.toDouble())
        put("evidence", JSONArray(evidence))
    }
}

object AppCapabilityRegistry {
    private val verifiedPackages = mapOf(
        "com.hexin.plat.android" to setOf(
            AppCapability.STOCK_QUOTE,
            AppCapability.STOCK_SEARCH,
            AppCapability.SECURITIES_TRADE,
            AppCapability.ORDER_ENTRY,
        ),
        "com.eastmoney.android.berlin" to setOf(
            AppCapability.STOCK_QUOTE,
            AppCapability.STOCK_SEARCH,
            AppCapability.SECURITIES_TRADE,
            AppCapability.ORDER_ENTRY,
        ),
        "com.autonavi.minimap" to setOf(AppCapability.NAVIGATION),
        "com.baidu.baidumap" to setOf(AppCapability.NAVIGATION),
        "com.taobao.taobao" to setOf(AppCapability.SHOPPING),
        "com.jingdong.app.mall" to setOf(AppCapability.SHOPPING),
        "tv.danmaku.bili" to setOf(AppCapability.VIDEO),
        "com.tencent.mm" to setOf(AppCapability.MESSAGING, AppCapability.PAYMENT),
        "com.tencent.mobileqq" to setOf(AppCapability.MESSAGING),
        "com.eg.android.alipaygphone" to setOf(AppCapability.PAYMENT),
    )

    private val tradeTokens = listOf(
        "证券", "券商", "交易", "委托", "佣金", "涨乐", "蜻蜓点金",
        "金太阳", "君弘", "海通财", "财富通",
    )
    private val quoteTokens = listOf("股票", "行情", "同花顺", "东方财富", "雪球", "大智慧")

    fun profile(app: InstalledAppEntry): AppCapabilityProfile {
        val categories = linkedSetOf<String>()
        val capabilities = linkedSetOf<String>()
        val evidence = mutableListOf<String>()
        var confidence = 0f
        verifiedPackages[app.packageName.lowercase()]?.let {
            capabilities += it
            evidence += "verified_package"
            confidence = 1f
        }
        val searchable = normalize(app.label + app.packageName)
        if (tradeTokens.any { searchable.contains(normalize(it)) }) {
            categories += setOf("finance", "securities")
            capabilities += setOf(
                AppCapability.STOCK_QUOTE,
                AppCapability.STOCK_SEARCH,
                AppCapability.SECURITIES_TRADE,
                AppCapability.ORDER_ENTRY,
            )
            evidence += "securities_trade_semantics"
            confidence = maxOf(confidence, 0.84f)
        } else if (quoteTokens.any { searchable.contains(normalize(it)) }) {
            categories += setOf("finance", "stock")
            capabilities += setOf(AppCapability.STOCK_QUOTE, AppCapability.STOCK_SEARCH)
            evidence += "stock_quote_semantics"
            confidence = maxOf(confidence, 0.78f)
        }
        if (searchable.contains("地图") || searchable.contains("导航")) {
            categories += "navigation"
            capabilities += AppCapability.NAVIGATION
            evidence += "navigation_semantics"
            confidence = maxOf(confidence, 0.78f)
        }
        return AppCapabilityProfile(categories, capabilities, confidence, evidence.distinct())
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFKC)
            .replace(Regex("\\s+"), "")
            .replace(Regex("[·・.。_\\-]+"), "")
}

object AgentTaskPhase {
    const val ResolveRequirements = "resolve_requirements"
    const val ResolveTargetApp = "resolve_target_app"
    const val OpenTargetApp = "open_target_app"
    const val VerifyTargetApp = "verify_target_app"
    const val VisualNavigation = "visual_navigation"
    const val VerifyResult = "verify_result"
    const val Completed = "completed"
    const val UserAssistance = "user_assistance"
    const val Unknown = "unknown"

    fun normalize(raw: String?): String = when (
        raw.orEmpty().trim().lowercase().replace('-', '_')
    ) {
        ResolveRequirements -> ResolveRequirements
        ResolveTargetApp -> ResolveTargetApp
        OpenTargetApp -> OpenTargetApp
        VerifyTargetApp -> VerifyTargetApp
        VisualNavigation -> VisualNavigation
        VerifyResult -> VerifyResult
        Completed -> Completed
        UserAssistance -> UserAssistance
        else -> Unknown
    }
}

data class AgentTaskExecutionContract(
    val taskId: String,
    val phase: String,
    val requiredCapabilities: Set<String>,
    val targetAppName: String,
    val targetPackageName: String,
    val targetEntityName: String,
    val targetAction: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("schema", "agent_task_execution_contract_v1")
        put("taskId", taskId)
        put("phase", phase)
        put("requiredCapabilities", JSONArray(requiredCapabilities.toList().sorted()))
        put("targetApp", JSONObject().apply {
            put("appName", targetAppName)
            put("packageName", targetPackageName)
        })
        put("targetEntity", targetEntityName)
        put("targetAction", targetAction)
    }

    companion object {
        fun fromResponse(root: JSONObject?): AgentTaskExecutionContract? {
            val item = root.findContract() ?: return null
            val app = item.optJSONObject("targetApp") ?: item.optJSONObject("resolvedTargetApp")
            return AgentTaskExecutionContract(
                taskId = item.first("taskId", "task_id", "id").orEmpty(),
                phase = AgentTaskPhase.normalize(item.first("phase", "currentPhase", "stage")),
                requiredCapabilities = item.strings(
                    "requiredCapabilities",
                    "required_capabilities",
                    "requiredAppCapabilities",
                ).map(AppCapability::normalize).filter { it.isNotBlank() }.toSet(),
                targetAppName = app?.first("appName", "label", "name")
                    ?: item.first("targetAppName", "requiredApp").orEmpty(),
                targetPackageName = app?.first("packageName", "package", "pkg")
                    ?: item.first("targetPackageName", "packageName").orEmpty(),
                targetEntityName = item.optJSONObject("targetEntity")?.first("name", "label", "value")
                    ?: item.first("targetEntityName", "targetEntity").orEmpty(),
                targetAction = item.first("targetAction", "desiredAction", "action").orEmpty(),
            )
        }

        private fun JSONObject?.findContract(): JSONObject? {
            if (this == null) return null
            return optJSONObject("taskExecutionContract")
                ?: optJSONObject("taskContract")
                ?: optJSONObject("data")?.findContract()
                ?: optJSONObject("result")?.findContract()
                ?: optJSONObject("plan")?.findContract()
        }

        private fun JSONObject.first(vararg keys: String): String? =
            keys.asSequence().map { optString(it).trim() }.firstOrNull { it.isNotBlank() }

        private fun JSONObject.strings(vararg keys: String): List<String> {
            keys.forEach { key ->
                optJSONArray(key)?.let { array ->
                    return buildList {
                        for (index in 0 until array.length()) {
                            array.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }
                optString(key).trim().takeIf { it.isNotBlank() }?.let { text ->
                    return text.split(',', '，', ';', '；')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                }
            }
            return emptyList()
        }
    }
}

object AgentTaskContractRuntime {
    private data class Entry(
        val sessionId: String,
        val goal: String,
        val contract: AgentTaskExecutionContract,
        val updatedAt: Long,
    )

    private var entry: Entry? = null

    @Synchronized
    fun ensureSession(sessionId: String, goal: String): AgentTaskExecutionContract? {
        val current = entry
        if (current == null || current.sessionId != sessionId || current.goal != goal ||
            SystemClock.elapsedRealtime() - current.updatedAt > TTL_MS
        ) {
            entry = null
            return null
        }
        return current.contract
    }

    @Synchronized
    fun update(sessionId: String, goal: String, root: JSONObject?): AgentTaskExecutionContract? {
        val contract = AgentTaskExecutionContract.fromResponse(root) ?: return null
        entry = Entry(sessionId, goal, contract, SystemClock.elapsedRealtime())
        return contract
    }

    @Synchronized
    fun current(goal: String): AgentTaskExecutionContract? =
        entry?.takeIf { it.goal == goal && SystemClock.elapsedRealtime() - it.updatedAt <= TTL_MS }?.contract

    @Synchronized
    fun clear(sessionId: String) {
        if (entry?.sessionId == sessionId) entry = null
    }

    private const val TTL_MS = 5 * 60 * 1_000L
}

data class TargetAppResolution(
    val status: String,
    val selectedApp: InstalledAppEntry?,
    val candidates: List<InstalledAppEntry>,
    val requiredCapabilities: Set<String>,
    val reason: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("schema", "target_app_resolution_v1")
        put("status", status)
        put("requiredCapabilities", JSONArray(requiredCapabilities.toList().sorted()))
        selectedApp?.let { put("selectedApp", appJson(it)) }
        put("candidates", JSONArray().apply { candidates.forEach { put(appJson(it)) } })
        put("reason", reason)
    }

    private fun appJson(app: InstalledAppEntry): JSONObject = JSONObject().apply {
        val profile = AppCapabilityRegistry.profile(app)
        put("label", app.label)
        put("packageName", app.packageName)
        put("launchable", true)
        put("capabilityProfile", profile.toJson())
    }
}

class TargetAppResolver(
    context: Context,
    private val appIndex: InstalledAppIndex,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "ai_ledger_default_target_apps",
        Context.MODE_PRIVATE,
    )

    fun resolve(contract: AgentTaskExecutionContract?): TargetAppResolution {
        if (contract == null) {
            return TargetAppResolution("not_required", null, emptyList(), emptySet(), "尚无云端任务合同。")
        }
        val required = contract.requiredCapabilities
        val apps = appIndex.getLaunchableApps(false)
        if (contract.targetPackageName.isNotBlank()) {
            val exact = apps.firstOrNull { it.packageName == contract.targetPackageName }
            return if (exact != null) {
                TargetAppResolution("resolved", exact, listOf(exact), required, "已按明确包名解析。")
            } else {
                TargetAppResolution("not_found", null, emptyList(), required, "明确包名未安装或不可启动。")
            }
        }
        if (contract.targetAppName.isNotBlank()) {
            val named = appIndex.findCandidateApps(contract.targetAppName, 8)
            return choose(required, named, "已按用户明确指定的应用名称解析。")
        }
        if (required.isEmpty()) {
            return TargetAppResolution("not_required", null, emptyList(), required, "任务未声明应用能力。")
        }
        val preferred = preferences.getString("capability.${primary(required)}", null)
        val matched = apps.filter { AppCapabilityRegistry.profile(it).supports(required) }
            .sortedByDescending { app ->
                val profile = AppCapabilityRegistry.profile(app)
                (profile.confidence * 1000).toInt() + if (app.packageName == preferred) 2000 else 0
            }
            .take(8)
        matched.firstOrNull { it.packageName == preferred }?.let {
            return TargetAppResolution("resolved", it, matched, required, "已使用用户保存的默认能力应用。")
        }
        return choose(required, matched, "设备上仅有一个满足能力的可启动应用。")
    }

    fun remember(required: Set<String>, packageName: String) {
        val editor = preferences.edit()
        required.forEach { editor.putString("capability.${AppCapability.normalize(it)}", packageName) }
        editor.apply()
    }

    private fun choose(
        required: Set<String>,
        apps: List<InstalledAppEntry>,
        resolvedReason: String,
    ): TargetAppResolution = when (apps.size) {
        0 -> TargetAppResolution("not_found", null, emptyList(), required, "设备上未识别到满足条件的可启动应用。")
        1 -> TargetAppResolution("resolved", apps.first(), apps, required, resolvedReason)
        else -> TargetAppResolution("ambiguous", null, apps, required, "存在多个满足条件的应用，需要用户选择一次。")
    }

    private fun primary(required: Set<String>): String = listOf(
        AppCapability.ORDER_ENTRY,
        AppCapability.SECURITIES_TRADE,
        AppCapability.STOCK_SEARCH,
        AppCapability.STOCK_QUOTE,
        AppCapability.NAVIGATION,
        AppCapability.SHOPPING,
        AppCapability.PAYMENT,
        AppCapability.MESSAGING,
        AppCapability.VIDEO,
    ).firstOrNull(required::contains) ?: required.sorted().first()
}

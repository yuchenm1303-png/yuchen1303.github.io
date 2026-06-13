package com.yuchen.ailedger.service

import android.content.Context
import com.yuchen.ailedger.data.LedgerStore
import com.yuchen.ailedger.data.SupabaseSessionStore
import com.yuchen.ailedger.model.LedgerRecord
import com.yuchen.ailedger.model.LedgerRecordType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * 执行由云端主脑选定的结构化账本工具。
 *
 * 这一层不读取用户原始自然语言，也不根据关键词猜测意图。主脑必须先给出明确工具和参数，
 * Android 只做类型、范围和持久化校验。
 */
class LedgerInternalToolExecutor(context: Context) {
    private val appContext = context.applicationContext
    private val store = LedgerStore(appContext)
    private val sessionStore = SupabaseSessionStore(appContext)
    private val cloudClient = SupabaseLedgerClient()
    private val idSeed = AtomicLong(System.currentTimeMillis())

    fun canExecute(step: CloudAgentStep): Boolean = step.type in CloudAgentStep.ledgerToolTypes

    fun execute(step: CloudAgentStep): AgentExecutionResult {
        return runCatching {
            when (step.type) {
                "ledger_add_record" -> addRecord(step)
                "ledger_set_budget" -> setBudget(step)
                "ledger_query_summary" -> querySummary(step)
                "ledger_list_records" -> listRecords(step)
                else -> AgentExecutionResult(false, "不支持的账本内部工具：${step.type}", false)
            }
        }.getOrElse { error ->
            AgentExecutionResult(
                ok = false,
                message = "账本内部工具执行异常：${error.message?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName}",
                shouldContinue = false,
            )
        }
    }

    private fun addRecord(step: CloudAgentStep): AgentExecutionResult {
        val amount = step.argFloat("amount", "value")
            ?.takeIf { it.isFinite() && it > 0f }
            ?: return failure("新增账单失败：主脑没有提供有效的正数金额 amount。")
        val type = parseRecordType(step.argString("recordType", "transactionType", "entryType"))
            ?: return failure("新增账单失败：主脑必须明确提供 recordType=expense 或 income。")
        val title = step.argString("title", "name", "description")
            ?.trim()
            ?.take(30)
            ?.takeIf { it.isNotBlank() }
            ?: if (type == LedgerRecordType.Income) "未命名收入" else "未命名支出"
        val category = normalizeCategory(step.argString("category"))
        val date = normalizeStructuredDate(step.argString("date", "dateLabel"))
        val record = LedgerRecord(
            id = nextRecordId(),
            title = title,
            amount = amount,
            type = type,
            category = category,
            dateLabel = date,
        )
        val nextRecords = (listOf(record) + store.loadRecords())
            .distinctBy { it.id }
            .sortedWith(compareByDescending<LedgerRecord> { LedgerStore.normalizeDate(it.dateLabel) }.thenByDescending { it.id })
        store.saveRecords(nextRecords)

        val persisted = store.loadRecords().firstOrNull { it.id == record.id }
            ?: return failure("账单写入后复核失败，没有在本地账本中找到新记录。")
        val cloudStatus = syncRecordBestEffort(persisted)
        val sign = if (persisted.type == LedgerRecordType.Income) "收入" else "支出"
        return success(
            "已记录：${persisted.title}，$sign ${formatMoney(persisted.amount.toDouble())}，分类 ${persisted.category}，日期 ${persisted.dateLabel}。$cloudStatus",
            "verified=true；已重新读取本地账本并确认记录 ${persisted.id} 存在。",
        )
    }

    private fun setBudget(step: CloudAgentStep): AgentExecutionResult {
        val amount = step.argFloat("amount", "budget", "value")
            ?.takeIf { it.isFinite() && it >= 0f }
            ?: return failure("设置预算失败：主脑没有提供有效的非负金额 amount。")
        val text = formatPlainNumber(amount.toDouble())
        store.saveBudget(text)
        val persisted = store.loadBudget()
        if (persisted.toDoubleOrNull() != text.toDoubleOrNull()) {
            return failure("预算写入后复核失败。")
        }
        val cloudStatus = syncBudgetBestEffort(persisted)
        return success(
            "本月预算已设置为 ${formatMoney(amount.toDouble())}。$cloudStatus",
            "verified=true；已重新读取本地预算，当前值为 $persisted。",
        )
    }

    private fun querySummary(step: CloudAgentStep): AgentExecutionResult {
        val range = normalizeRange(step.argString("range", "period", "timeRange"))
        val category = step.argString("category")?.trim()?.takeIf { it.isNotBlank() && it != "全部" }
        val typeFilter = parseOptionalRecordType(step.argString("recordType", "transactionType", "entryType"))
        val records = filteredRecords(store.loadRecords(), range, category, typeFilter)
        val income = records.filter { it.type == LedgerRecordType.Income }.sumOf { it.amount.toDouble() }
        val expense = records.filter { it.type == LedgerRecordType.Expense }.sumOf { it.amount.toDouble() }
        val categoryText = category?.let { "，分类 $it" }.orEmpty()
        val typeText = typeFilter?.let { "，类型 ${if (it == LedgerRecordType.Income) "收入" else "支出"}" }.orEmpty()
        val budgetText = if (range == LedgerRange.CurrentMonth) {
            val budget = store.loadBudget().toDoubleOrNull() ?: 0.0
            "，本月预算 ${formatMoney(budget)}，剩余 ${formatSignedMoney(budget - expense)}"
        } else {
            ""
        }
        return success(
            "${range.label}$categoryText$typeText：收入 ${formatMoney(income)}，支出 ${formatMoney(expense)}，结余 ${formatSignedMoney(income - expense)}，共 ${records.size} 笔$budgetText。",
            "verified=true；汇总直接读取原生本地账本 ${records.size} 笔记录。",
        )
    }

    private fun listRecords(step: CloudAgentStep): AgentExecutionResult {
        val range = normalizeRange(step.argString("range", "period", "timeRange"))
        val category = step.argString("category")?.trim()?.takeIf { it.isNotBlank() && it != "全部" }
        val typeFilter = parseOptionalRecordType(step.argString("recordType", "transactionType", "entryType"))
        val limit = (step.argLong("limit", "count") ?: 10L).toInt().coerceIn(1, 20)
        val records = filteredRecords(store.loadRecords(), range, category, typeFilter).take(limit)
        if (records.isEmpty()) {
            return success(
                "${range.label}没有符合条件的账单记录。",
                "verified=true；已读取原生本地账本，匹配记录为 0 笔。",
            )
        }
        val detail = records.joinToString(separator = "\n") { record ->
            val sign = if (record.type == LedgerRecordType.Income) "+" else "-"
            "${record.dateLabel} · ${record.title} · ${record.category} · $sign${formatMoney(record.amount.toDouble())}"
        }
        return success(
            "${range.label}最近 ${records.size} 笔账单：\n$detail",
            "verified=true；已从原生本地账本读取 ${records.size} 笔明细。",
        )
    }

    private fun filteredRecords(
        source: List<LedgerRecord>,
        range: LedgerRange,
        category: String?,
        type: LedgerRecordType?,
    ): List<LedgerRecord> {
        val bounds = rangeBounds(range)
        return source.asSequence()
            .filter { record ->
                val date = LedgerStore.normalizeDate(record.dateLabel)
                val inRange = bounds == null || (date >= bounds.first && date <= bounds.second)
                val categoryMatches = category == null || record.category == normalizeCategory(category)
                val typeMatches = type == null || record.type == type
                inRange && categoryMatches && typeMatches
            }
            .sortedWith(compareByDescending<LedgerRecord> { LedgerStore.normalizeDate(it.dateLabel) }.thenByDescending { it.id })
            .toList()
    }

    private fun rangeBounds(range: LedgerRange): Pair<String, String>? {
        if (range == LedgerRange.All) return null
        val calendar = Calendar.getInstance()
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return when (range) {
            LedgerRange.CurrentMonth -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val start = formatter.format(calendar.time)
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                start to formatter.format(calendar.time)
            }
            LedgerRange.LastMonth -> {
                calendar.add(Calendar.MONTH, -1)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val start = formatter.format(calendar.time)
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                start to formatter.format(calendar.time)
            }
            LedgerRange.Last30Days -> {
                val end = formatter.format(calendar.time)
                calendar.add(Calendar.DAY_OF_YEAR, -29)
                formatter.format(calendar.time) to end
            }
            LedgerRange.CurrentYear -> {
                val year = calendar.get(Calendar.YEAR)
                "$year-01-01" to "$year-12-31"
            }
            LedgerRange.All -> null
        }
    }

    private fun parseRecordType(value: String?): LedgerRecordType? {
        return when (value?.trim()?.lowercase()?.replace('-', '_')) {
            "expense", "outcome", "spending", "支出" -> LedgerRecordType.Expense
            "income", "earning", "收入" -> LedgerRecordType.Income
            else -> null
        }
    }

    private fun parseOptionalRecordType(value: String?): LedgerRecordType? {
        val clean = value?.trim().orEmpty()
        return if (clean.isBlank() || clean.equals("all", ignoreCase = true) || clean == "全部") null else parseRecordType(clean)
    }

    private fun normalizeCategory(value: String?): String {
        val clean = value?.trim().orEmpty()
        return when (clean.lowercase()) {
            "food", "meal", "dining", "餐饮" -> "餐饮"
            "transport", "transportation", "交通" -> "交通"
            "shopping", "购物" -> "购物"
            "housing", "home", "居住" -> "居住"
            "drink", "beverage", "饮品" -> "饮品"
            "salary", "wage", "工资" -> "工资"
            "gift", "礼物" -> "礼物"
            "other", "others", "其他", "" -> "其他"
            else -> clean.takeIf { it in LedgerStore.LEDGER_CATEGORIES } ?: "其他"
        }
    }

    private fun normalizeStructuredDate(value: String?): String {
        val clean = value?.trim().orEmpty()
        return when (clean.lowercase()) {
            "", "today", "今天" -> LedgerStore.todayIso()
            "yesterday", "昨天" -> shiftedDate(-1)
            "day_before_yesterday", "前天" -> shiftedDate(-2)
            else -> LedgerStore.normalizeDate(clean)
        }
    }

    private fun normalizeRange(value: String?): LedgerRange {
        return when (value?.trim()?.lowercase()?.replace('-', '_')) {
            "last_month", "previous_month", "上月" -> LedgerRange.LastMonth
            "last_30_days", "recent_30_days", "30_days", "最近30天" -> LedgerRange.Last30Days
            "current_year", "this_year", "year", "本年", "今年" -> LedgerRange.CurrentYear
            "all", "all_time", "全部" -> LedgerRange.All
            else -> LedgerRange.CurrentMonth
        }
    }

    private fun syncRecordBestEffort(record: LedgerRecord): String {
        val session = sessionStore.load()?.takeIf { it.isUsable } ?: return "当前为本地模式。"
        return runCatching {
            cloudClient.upsertRecords(session, listOf(record))
            "已同步到云端。"
        }.getOrElse { "已保存到本机，云同步将在账单中心重试。" }
    }

    private fun syncBudgetBestEffort(value: String): String {
        val session = sessionStore.load()?.takeIf { it.isUsable } ?: return "当前为本地模式。"
        return runCatching {
            cloudClient.upsertBudget(session, value)
            "已同步到云端。"
        }.getOrElse { "已保存到本机，云同步将在账单中心重试。" }
    }

    private fun shiftedDate(days: Int): String {
        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    }

    private fun nextRecordId(): String = "record-agent-${idSeed.incrementAndGet()}"

    private fun success(message: String, verification: String): AgentExecutionResult {
        return AgentExecutionResult(true, "$message\n\n执行后验证：$verification", false)
    }

    private fun failure(message: String): AgentExecutionResult = AgentExecutionResult(false, message, false)

    private fun formatMoney(value: Double): String = "¥${String.format(Locale.CHINA, "%.2f", value)}"

    private fun formatSignedMoney(value: Double): String {
        val prefix = if (value >= 0.0) "+" else "-"
        return prefix + formatMoney(kotlin.math.abs(value))
    }

    private fun formatPlainNumber(value: Double): String {
        val integer = value.toLong()
        return if (value == integer.toDouble()) integer.toString() else value.toString().trimEnd('0').trimEnd('.')
    }

    private enum class LedgerRange(val label: String) {
        CurrentMonth("本月"),
        LastMonth("上月"),
        Last30Days("最近30天"),
        CurrentYear("本年"),
        All("全部时间"),
    }
}

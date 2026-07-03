package com.yuchen.ailedger.service

import android.content.Context
import com.yuchen.ailedger.data.LedgerStore
import com.yuchen.ailedger.data.SupabaseSessionStore
import com.yuchen.ailedger.model.LedgerRecord
import com.yuchen.ailedger.model.LedgerRecordType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

/**
 * Pure transaction executor for cloud-selected ledger tools.
 *
 * It accepts only canonical JSON fields and enum values. It never reads user text, accepts aliases,
 * translates synonyms, or guesses a missing transaction intent.
 */
class LedgerInternalToolExecutor(context: Context) {
    private val appContext = context.applicationContext
    private val store = LedgerStore(appContext)
    private val sessionStore = SupabaseSessionStore(appContext)
    private val cloudClient = SupabaseLedgerClient()
    private val idSeed = AtomicLong(System.currentTimeMillis())

    fun canExecute(step: CloudAgentStep): Boolean = step.type in CloudAgentStep.ledgerToolTypes

    fun execute(step: CloudAgentStep): AgentExecutionResult = runCatching {
        validateEnvelope(step)?.let { return failure(it) }
        when (step.type) {
            "ledger_add_record" -> addRecord(step)
            "ledger_set_budget" -> setBudget(step)
            "ledger_query_summary" -> querySummary(step)
            "ledger_list_records" -> listRecords(step)
            else -> failure("不支持的账本工具：${step.type}。")
        }
    }.getOrElse { error ->
        AgentExecutionResult(
            ok = false,
            message = "账本工具执行异常：${error.message?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName}",
            shouldContinue = false,
            diagnostics = "ledger_execution_exception:${error::class.java.simpleName}",
        )
    }

    private fun validateEnvelope(step: CloudAgentStep): String? {
        val allowed = allowedArgNames(step.type) ?: return "账本工具未注册：${step.type}。"
        val args = step.toolArgs ?: JSONObject()
        val unknown = args.keys().asSequence().firstOrNull { it !in allowed }
        return unknown?.let { "账本工具包含未声明参数：$it。" }
    }

    private fun addRecord(step: CloudAgentStep): AgentExecutionResult {
        val amount = step.numberArg("amount")
            ?.takeIf { it.isFinite() && it > 0.0 && it <= MAX_LEDGER_AMOUNT }
            ?: return failure("新增账单失败：amount 必须是有效正数。")
        val type = when (step.stringArg("recordType")) {
            "expense" -> LedgerRecordType.Expense
            "income" -> LedgerRecordType.Income
            else -> return failure("新增账单失败：recordType 必须是 expense 或 income。")
        }
        val title = step.stringArg("title")
            ?.take(MAX_TITLE_CHARS)
            ?.takeIf(String::isNotBlank)
            ?: return failure("新增账单失败：缺少非空 title。")
        val category = step.stringArg("category")
            ?.takeIf { it in LedgerStore.LEDGER_CATEGORIES }
            ?: return failure("新增账单失败：category 不在账本分类枚举中。")
        val date = canonicalDate(step.stringArg("date"))
            ?: return failure("新增账单失败：date 必须是 today、yesterday 或 YYYY-MM-DD。")

        val record = LedgerRecord(
            id = nextRecordId(),
            title = title,
            amount = amount.toFloat(),
            type = type,
            category = category,
            dateLabel = date,
        )
        val nextRecords = (listOf(record) + store.loadRecords())
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<LedgerRecord> { LedgerStore.normalizeDate(it.dateLabel) }
                    .thenByDescending { it.id },
            )
        store.saveRecords(nextRecords)

        val persisted = store.loadRecords().firstOrNull { it.id == record.id }
            ?: return failure("账单写入后复核失败，没有在本地账本中找到新记录。")
        val cloudStatus = syncRecordBestEffort(persisted)
        val sign = if (persisted.type == LedgerRecordType.Income) "收入" else "支出"
        return success(
            message = "已记录：${persisted.title}，$sign ${formatMoney(persisted.amount.toDouble())}，分类 ${persisted.category}，日期 ${persisted.dateLabel}。$cloudStatus",
            verification = "verified=true；recordId=${persisted.id}；已重新读取本地账本确认记录存在。",
        )
    }

    private fun setBudget(step: CloudAgentStep): AgentExecutionResult {
        val amount = step.numberArg("amount")
            ?.takeIf { it.isFinite() && it >= 0.0 && it <= MAX_LEDGER_AMOUNT }
            ?: return failure("设置预算失败：amount 必须是有效非负数。")
        val value = formatPlainNumber(amount)
        store.saveBudget(value)
        val persisted = store.loadBudget()
        if (persisted.toDoubleOrNull() != value.toDoubleOrNull()) {
            return failure("预算写入后复核失败。")
        }
        val cloudStatus = syncBudgetBestEffort(persisted)
        return success(
            message = "本月预算已设置为 ${formatMoney(amount)}。$cloudStatus",
            verification = "verified=true；已重新读取本地预算，当前值为 $persisted。",
        )
    }

    private fun querySummary(step: CloudAgentStep): AgentExecutionResult {
        val filters = parseFilters(step) ?: return failure("查询账单汇总失败：参数不符合规范 Schema。")
        val records = filteredRecords(store.loadRecords(), filters)
        val income = records.filter { it.type == LedgerRecordType.Income }.sumOf { it.amount.toDouble() }
        val expense = records.filter { it.type == LedgerRecordType.Expense }.sumOf { it.amount.toDouble() }
        val categoryText = filters.category?.let { "，分类 $it" }.orEmpty()
        val typeText = filters.type?.let { "，类型 ${if (it == LedgerRecordType.Income) "收入" else "支出"}" }.orEmpty()
        val budgetText = if (filters.range == LedgerRange.CurrentMonth) {
            val budget = store.loadBudget().toDoubleOrNull() ?: 0.0
            "，本月预算 ${formatMoney(budget)}，剩余 ${formatSignedMoney(budget - expense)}"
        } else {
            ""
        }
        return success(
            message = "${filters.range.label}$categoryText$typeText：收入 ${formatMoney(income)}，支出 ${formatMoney(expense)}，结余 ${formatSignedMoney(income - expense)}，共 ${records.size} 笔$budgetText。",
            verification = "verified=true；汇总直接读取原生本地账本 ${records.size} 笔记录。",
        )
    }

    private fun listRecords(step: CloudAgentStep): AgentExecutionResult {
        val filters = parseFilters(step) ?: return failure("查询账单明细失败：参数不符合规范 Schema。")
        val limit = step.integerArg("limit")?.takeIf { it in 1..20 }
            ?: return failure("查询账单明细失败：limit 必须是 1 到 20 的整数。")
        val records = filteredRecords(store.loadRecords(), filters).take(limit)
        if (records.isEmpty()) {
            return success(
                message = "${filters.range.label}没有符合条件的账单记录。",
                verification = "verified=true；已读取原生本地账本，匹配记录为 0 笔。",
            )
        }
        val detail = records.joinToString(separator = "\n") { record ->
            val sign = if (record.type == LedgerRecordType.Income) "+" else "-"
            "${record.dateLabel} · ${record.title} · ${record.category} · $sign${formatMoney(record.amount.toDouble())}"
        }
        return success(
            message = "${filters.range.label}最近 ${records.size} 笔账单：\n$detail",
            verification = "verified=true；已从原生本地账本读取 ${records.size} 笔明细。",
        )
    }

    private fun parseFilters(step: CloudAgentStep): LedgerFilters? {
        val range = when (step.stringArg("range")) {
            "current_month" -> LedgerRange.CurrentMonth
            "last_month" -> LedgerRange.LastMonth
            "last_30_days" -> LedgerRange.Last30Days
            "current_year" -> LedgerRange.CurrentYear
            "all" -> LedgerRange.All
            else -> return null
        }
        val type = when (val raw = step.stringArg("recordType")) {
            null -> null
            "expense" -> LedgerRecordType.Expense
            "income" -> LedgerRecordType.Income
            else -> return null
        }
        val category = step.stringArg("category")?.let { raw ->
            raw.takeIf { it in LedgerStore.LEDGER_CATEGORIES } ?: return null
        }
        return LedgerFilters(range, category, type)
    }

    private fun filteredRecords(source: List<LedgerRecord>, filters: LedgerFilters): List<LedgerRecord> {
        val bounds = rangeBounds(filters.range)
        return source.asSequence()
            .filter { record ->
                val date = LedgerStore.normalizeDate(record.dateLabel)
                val inRange = bounds == null || (date >= bounds.first && date <= bounds.second)
                val categoryMatches = filters.category == null || record.category == filters.category
                val typeMatches = filters.type == null || record.type == filters.type
                inRange && categoryMatches && typeMatches
            }
            .sortedWith(
                compareByDescending<LedgerRecord> { LedgerStore.normalizeDate(it.dateLabel) }
                    .thenByDescending { it.id },
            )
            .toList()
    }

    private fun canonicalDate(value: String?): String? = when (value) {
        "today" -> LedgerStore.todayIso()
        "yesterday" -> shiftedDate(-1)
        null -> null
        else -> value.takeIf(::isStrictIsoDate)
    }

    private fun isStrictIsoDate(value: String): Boolean {
        if (!ISO_DATE_REGEX.matches(value)) return false
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value)
        }.getOrNull() != null
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

    private fun CloudAgentStep.stringArg(name: String): String? {
        val args = toolArgs ?: return null
        if (!args.has(name) || args.isNull(name)) return null
        return (args.opt(name) as? String)?.trim()?.takeIf(String::isNotBlank)
    }

    private fun CloudAgentStep.numberArg(name: String): Double? {
        val args = toolArgs ?: return null
        if (!args.has(name) || args.isNull(name)) return null
        return (args.opt(name) as? Number)?.toDouble()
    }

    private fun CloudAgentStep.integerArg(name: String): Int? {
        val args = toolArgs ?: return null
        if (!args.has(name) || args.isNull(name)) return null
        val number = args.opt(name) as? Number ?: return null
        val value = number.toDouble()
        if (!value.isFinite() || value % 1.0 != 0.0) return null
        return value.toInt()
    }

    private fun nextRecordId(): String = "record-agent-${idSeed.incrementAndGet()}"

    private fun success(message: String, verification: String): AgentExecutionResult = AgentExecutionResult(
        ok = true,
        message = "$message\n\n执行后验证：$verification",
        shouldContinue = false,
        diagnostics = "ledger_verified",
    )

    private fun failure(message: String): AgentExecutionResult = AgentExecutionResult(
        ok = false,
        message = message,
        shouldContinue = false,
        diagnostics = "ledger_validation_failed",
    )

    private fun formatMoney(value: Double): String = "¥${String.format(Locale.CHINA, "%.2f", value)}"

    private fun formatSignedMoney(value: Double): String =
        (if (value >= 0.0) "+" else "-") + formatMoney(kotlin.math.abs(value))

    private fun formatPlainNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

    companion object {
        private const val MAX_TITLE_CHARS = 80
        private const val MAX_LEDGER_AMOUNT = 1_000_000_000.0
        private val ISO_DATE_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}")

        fun allowedArgNames(stepType: String): Set<String>? = when (stepType) {
            "ledger_add_record" -> setOf("amount", "recordType", "title", "category", "date")
            "ledger_set_budget" -> setOf("amount")
            "ledger_query_summary" -> setOf("range", "recordType", "category")
            "ledger_list_records" -> setOf("range", "recordType", "category", "limit")
            else -> null
        }
    }
}

private data class LedgerFilters(
    val range: LedgerRange,
    val category: String?,
    val type: LedgerRecordType?,
)

private enum class LedgerRange(val label: String) {
    CurrentMonth("本月"),
    LastMonth("上月"),
    Last30Days("最近 30 天"),
    CurrentYear("今年"),
    All("全部时间"),
}

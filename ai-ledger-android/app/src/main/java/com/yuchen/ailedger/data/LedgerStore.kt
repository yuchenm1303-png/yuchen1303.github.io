package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.model.LedgerRecord
import com.yuchen.ailedger.model.LedgerRecordType
import com.yuchen.ailedger.model.LedgerStateBridge
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.json.JSONArray
import org.json.JSONObject

data class LedgerSnapshot(
    val records: List<LedgerRecord>,
    val budgetText: String,
)

class LedgerStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun observeSnapshots(): Flow<LedgerSnapshot> {
        return changeEvents
            .onStart { emit(Unit) }
            .map { currentSnapshot().also(::publishBridge) }
            .distinctUntilChanged()
    }

    fun loadRecords(): List<LedgerRecord> {
        val raw = preferences.getString(KEY_RECORDS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    parseRecord(json)?.let(::add)
                }
            }.sortedWith(compareByDescending<LedgerRecord> { normalizeDate(it.dateLabel) }.thenByDescending { it.id })
        }.getOrDefault(emptyList())
    }

    fun saveRecords(records: List<LedgerRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            val safe = normalizeRecord(record) ?: return@forEach
            array.put(
                JSONObject()
                    .put("id", safe.id)
                    .put("title", safe.title)
                    .put("amount", safe.amount.toDouble())
                    .put("type", if (safe.type == LedgerRecordType.Income) "income" else "expense")
                    .put("category", safe.category)
                    .put("date", normalizeDate(safe.dateLabel))
            )
        }
        preferences.edit().putString(KEY_RECORDS, array.toString()).apply()
        publishCurrentSnapshot()
    }

    fun loadBudget(): String {
        return preferences.getString(KEY_BUDGET, null)
            ?.trim()
            ?.takeIf { it.toDoubleOrNull() != null }
            ?: DEFAULT_BUDGET
    }

    fun hasSavedBudget(): Boolean = preferences.contains(KEY_BUDGET)

    fun saveBudget(value: String) {
        val amount = value.toDoubleOrNull()?.coerceAtLeast(0.0) ?: return
        preferences.edit().putString(KEY_BUDGET, formatPlainNumber(amount)).apply()
        publishCurrentSnapshot()
    }

    fun loadDeletedIds(): Set<String> {
        return preferences.getStringSet(KEY_DELETED_IDS, emptySet())?.filter { it.isNotBlank() }?.toSet().orEmpty()
    }

    fun markDeleted(id: String) {
        if (id.isBlank()) return
        val next = loadDeletedIds() + id
        preferences.edit().putStringSet(KEY_DELETED_IDS, next).apply()
    }

    fun clearDeletedIds(ids: Set<String>? = null) {
        if (ids == null) {
            preferences.edit().remove(KEY_DELETED_IDS).apply()
            return
        }
        val remaining = loadDeletedIds() - ids
        preferences.edit().putStringSet(KEY_DELETED_IDS, remaining).apply()
    }

    fun exportJson(records: List<LedgerRecord>, budget: String): String {
        val array = JSONArray()
        records.forEach { record ->
            normalizeRecord(record)?.let { safe ->
                array.put(
                    JSONObject()
                        .put("id", safe.id)
                        .put("title", safe.title)
                        .put("amount", safe.amount.toDouble())
                        .put("type", if (safe.type == LedgerRecordType.Income) "income" else "expense")
                        .put("category", safe.category)
                        .put("date", normalizeDate(safe.dateLabel))
                )
            }
        }
        return JSONObject()
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("monthlyBudget", budget.toDoubleOrNull() ?: 0.0)
            .put("records", array)
            .toString(2)
    }

    fun normalizeRecord(record: LedgerRecord): LedgerRecord? {
        val amount = record.amount
        if (record.id.isBlank() || !amount.isFinite() || amount <= 0f) return null
        return record.copy(
            title = record.title.trim().ifBlank { if (record.type == LedgerRecordType.Income) "未命名收入" else "未命名支出" }.take(30),
            category = record.category.trim().takeIf { it in LEDGER_CATEGORIES } ?: "其他",
            dateLabel = normalizeDate(record.dateLabel)
        )
    }

    private fun currentSnapshot(): LedgerSnapshot = LedgerSnapshot(loadRecords(), loadBudget())

    private fun publishCurrentSnapshot() {
        publishBridge(currentSnapshot())
        changeEvents.tryEmit(Unit)
    }

    private fun publishBridge(snapshot: LedgerSnapshot) {
        LedgerStateBridge.update(snapshot.records, snapshot.budgetText)
    }

    private fun parseRecord(json: JSONObject): LedgerRecord? {
        val id = json.optString("id").trim()
        val amount = json.optDouble("amount", Double.NaN).toFloat()
        if (id.isBlank() || !amount.isFinite() || amount <= 0f) return null
        val type = if (json.optString("type").equals("income", ignoreCase = true)) {
            LedgerRecordType.Income
        } else {
            LedgerRecordType.Expense
        }
        return normalizeRecord(
            LedgerRecord(
                id = id,
                title = json.optString("title", "未命名账单"),
                amount = amount,
                type = type,
                category = json.optString("category", "其他"),
                dateLabel = json.optString("date", todayIso())
            )
        )
    }

    companion object {
        const val DEFAULT_BUDGET = "3000"
        val LEDGER_CATEGORIES = listOf("餐饮", "交通", "购物", "居住", "饮品", "工资", "礼物", "其他")

        private val changeEvents = MutableSharedFlow<Unit>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

        fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        fun currentMonthPrefix(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

        fun normalizeDate(value: String): String {
            val clean = value.trim()
            if (DATE_PATTERN.matches(clean)) return clean
            val calendar = Calendar.getInstance()
            when (clean) {
                "昨天" -> calendar.add(Calendar.DAY_OF_YEAR, -1)
                "前天" -> calendar.add(Calendar.DAY_OF_YEAR, -2)
            }
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
        }

        fun displayDate(value: String): String {
            val normalized = normalizeDate(value)
            val today = todayIso()
            if (normalized == today) return "今天"
            val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
            return if (normalized == yesterday) "昨天" else normalized
        }

        private fun formatPlainNumber(value: Double): String {
            val asLong = value.toLong()
            return if (value == asLong.toDouble()) asLong.toString() else value.toString().trimEnd('0').trimEnd('.')
        }

        private val DATE_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        private const val PREFS_NAME = "native_ledger_store_v1"
        private const val KEY_RECORDS = "records"
        private const val KEY_BUDGET = "monthly_budget"
        private const val KEY_DELETED_IDS = "deleted_record_ids"
    }
}

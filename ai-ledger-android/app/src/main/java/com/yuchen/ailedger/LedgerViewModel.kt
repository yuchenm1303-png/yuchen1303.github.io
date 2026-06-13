package com.yuchen.ailedger

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.data.LedgerStore
import com.yuchen.ailedger.data.SupabaseSessionStore
import com.yuchen.ailedger.model.LedgerRecord
import com.yuchen.ailedger.model.LedgerRecordType
import com.yuchen.ailedger.service.SupabaseAuthClient
import com.yuchen.ailedger.service.SupabaseLedgerClient
import com.yuchen.ailedger.service.SupabaseUserSession
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LedgerSyncPhase {
    LocalOnly,
    Ready,
    Syncing,
    Synced,
    Error
}

data class LedgerScreenState(
    val records: List<LedgerRecord> = emptyList(),
    val budgetText: String = LedgerStore.DEFAULT_BUDGET,
    val draftTitle: String = "",
    val draftAmount: String = "",
    val draftType: LedgerRecordType = LedgerRecordType.Expense,
    val draftCategory: String = "餐饮",
    val draftDate: String = LedgerStore.todayIso(),
    val editingRecordId: String? = null,
    val smartInput: String = "",
    val smartMessage: String = "可输入“午饭18元，地铁4元”快速生成账单。",
    val accountEmail: String? = null,
    val syncPhase: LedgerSyncPhase = LedgerSyncPhase.LocalOnly,
    val syncMessage: String = "当前仅保存在本机",
    val lastSyncedAt: Long? = null
) {
    val isLoggedIn: Boolean get() = !accountEmail.isNullOrBlank()
    val isSyncing: Boolean get() = syncPhase == LedgerSyncPhase.Syncing
}

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val ledgerStore = LedgerStore(application)
    private val sessionStore = SupabaseSessionStore(application)
    private val authClient = SupabaseAuthClient()
    private val ledgerClient = SupabaseLedgerClient()
    private val idSeed = AtomicLong(System.currentTimeMillis())
    private var syncJob: Job? = null
    private var delayedSyncJob: Job? = null

    var state by mutableStateOf(
        LedgerScreenState(
            records = ledgerStore.loadRecords(),
            budgetText = ledgerStore.loadBudget()
        )
    )
        private set

    fun onScreenOpened() {
        refreshAccountAndSync()
    }

    fun refreshAccountAndSync() {
        val stored = sessionStore.load()
        if (stored == null || !stored.isUsable) {
            state = state.copy(
                accountEmail = null,
                syncPhase = LedgerSyncPhase.LocalOnly,
                syncMessage = "登录后自动开启云同步"
            )
            return
        }
        state = state.copy(
            accountEmail = stored.email,
            syncPhase = if (state.syncPhase == LedgerSyncPhase.Synced) state.syncPhase else LedgerSyncPhase.Ready,
            syncMessage = if (state.syncPhase == LedgerSyncPhase.Synced) state.syncMessage else "账号已连接，准备同步"
        )
        syncNow()
    }

    fun updateTitle(value: String) {
        state = state.copy(draftTitle = value.take(40))
    }

    fun updateAmount(value: String) {
        state = state.copy(draftAmount = sanitizeMoney(value))
    }

    fun updateType(type: LedgerRecordType) {
        state = state.copy(draftType = type)
    }

    fun updateCategory(category: String) {
        if (category in LedgerStore.LEDGER_CATEGORIES) state = state.copy(draftCategory = category)
    }

    fun updateDate(value: String) {
        state = state.copy(draftDate = value.filter { it.isDigit() || it == '-' }.take(10))
    }

    fun updateBudget(value: String) {
        val clean = sanitizeMoney(value)
        state = state.copy(budgetText = clean)
        clean.toDoubleOrNull()?.let {
            ledgerStore.saveBudget(clean)
            scheduleSync()
        }
    }

    fun updateSmartInput(value: String) {
        state = state.copy(smartInput = value.take(240))
    }

    fun saveRecord() {
        val amount = state.draftAmount.toFloatOrNull() ?: run {
            state = state.copy(smartMessage = "请填写有效金额。")
            return
        }
        if (!amount.isFinite() || amount <= 0f) {
            state = state.copy(smartMessage = "金额必须大于 0。")
            return
        }
        val title = state.draftTitle.trim().ifBlank {
            if (state.draftType == LedgerRecordType.Income) "未命名收入" else "未命名支出"
        }.take(30)
        val record = LedgerRecord(
            id = state.editingRecordId ?: nextId(),
            title = title,
            amount = amount,
            type = state.draftType,
            category = state.draftCategory.takeIf { it in LedgerStore.LEDGER_CATEGORIES } ?: "其他",
            dateLabel = LedgerStore.normalizeDate(state.draftDate)
        )
        val next = if (state.editingRecordId == null) {
            listOf(record) + state.records
        } else {
            state.records.map { current -> if (current.id == record.id) record else current }
        }.sortedWith(compareByDescending<LedgerRecord> { LedgerStore.normalizeDate(it.dateLabel) }.thenByDescending { it.id })
        ledgerStore.saveRecords(next)
        state = state.copy(
            records = next,
            draftTitle = "",
            draftAmount = "",
            draftDate = LedgerStore.todayIso(),
            editingRecordId = null,
            smartMessage = if (state.editingRecordId == null) "已保存 1 笔账单。" else "账单已更新。"
        )
        scheduleSync()
    }

    fun beginEdit(record: LedgerRecord) {
        state = state.copy(
            draftTitle = record.title,
            draftAmount = formatPlainNumber(record.amount.toDouble()),
            draftType = record.type,
            draftCategory = record.category,
            draftDate = LedgerStore.normalizeDate(record.dateLabel),
            editingRecordId = record.id,
            smartMessage = "正在编辑“${record.title}”。"
        )
    }

    fun cancelEdit() {
        state = state.copy(
            draftTitle = "",
            draftAmount = "",
            draftDate = LedgerStore.todayIso(),
            editingRecordId = null,
            smartMessage = "已取消编辑。"
        )
    }

    fun deleteRecord(id: String) {
        if (id.isBlank()) return
        val next = state.records.filterNot { it.id == id }
        if (next.size == state.records.size) return
        ledgerStore.saveRecords(next)
        ledgerStore.markDeleted(id)
        state = state.copy(
            records = next,
            editingRecordId = state.editingRecordId?.takeUnless { it == id },
            smartMessage = "已删除 1 笔账单。"
        )
        scheduleSync()
    }

    fun addSmartRecords() {
        val input = state.smartInput.trim()
        if (input.isBlank()) {
            state = state.copy(smartMessage = "先输入一句记账内容。")
            return
        }
        val parsed = parseSmartRecords(input)
        if (parsed.isEmpty()) {
            state = state.copy(smartMessage = "没有识别到金额，可以试试“午饭18元”。")
            return
        }
        val next = (parsed + state.records)
            .distinctBy { it.id }
            .sortedWith(compareByDescending<LedgerRecord> { LedgerStore.normalizeDate(it.dateLabel) }.thenByDescending { it.id })
        ledgerStore.saveRecords(next)
        state = state.copy(
            records = next,
            smartInput = "",
            smartMessage = "已从自然语言保存 ${parsed.size} 笔账单。"
        )
        scheduleSync()
    }

    fun syncNow() {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            val session = resolveSession()
            if (session == null) {
                state = state.copy(
                    accountEmail = null,
                    syncPhase = LedgerSyncPhase.LocalOnly,
                    syncMessage = "请先在设置页登录账号"
                )
                return@launch
            }
            state = state.copy(
                accountEmail = session.email,
                syncPhase = LedgerSyncPhase.Syncing,
                syncMessage = "正在同步账单…"
            )
            try {
                val result = withContext(Dispatchers.IO) {
                    val localRecords = ledgerStore.loadRecords()
                    val deletedIds = ledgerStore.loadDeletedIds()
                    if (deletedIds.isNotEmpty()) ledgerClient.deleteRecords(session, deletedIds)
                    val remoteRecords = ledgerClient.fetchRecords(session).filterNot { it.id in deletedIds }
                    val mergedMap = LinkedHashMap<String, LedgerRecord>()
                    remoteRecords.forEach { mergedMap[it.id] = it }
                    localRecords.forEach { mergedMap[it.id] = it }
                    val merged = mergedMap.values
                        .sortedWith(compareByDescending<LedgerRecord> { LedgerStore.normalizeDate(it.dateLabel) }.thenByDescending { it.id })
                    ledgerStore.saveRecords(merged)
                    if (merged.isNotEmpty()) ledgerClient.upsertRecords(session, merged)

                    val localBudget = ledgerStore.loadBudget()
                    val remoteBudget = ledgerClient.fetchBudget(session)
                    val finalBudget = if (!ledgerStore.hasSavedBudget() && remoteBudget != null) remoteBudget else localBudget
                    ledgerStore.saveBudget(finalBudget)
                    if (remoteBudget == null || ledgerStore.hasSavedBudget()) ledgerClient.upsertBudget(session, finalBudget)
                    if (deletedIds.isNotEmpty()) ledgerStore.clearDeletedIds(deletedIds)
                    merged to finalBudget
                }
                state = state.copy(
                    records = result.first,
                    budgetText = result.second,
                    accountEmail = session.email,
                    syncPhase = LedgerSyncPhase.Synced,
                    syncMessage = "已同步到云端",
                    lastSyncedAt = System.currentTimeMillis()
                )
            } catch (error: Throwable) {
                state = state.copy(
                    accountEmail = session.email,
                    syncPhase = LedgerSyncPhase.Error,
                    syncMessage = error.message?.takeIf { it.isNotBlank() } ?: "同步失败，请稍后重试"
                )
            }
        }
    }

    fun exportJson(): String = ledgerStore.exportJson(state.records, state.budgetText)

    private fun scheduleSync() {
        if (!state.isLoggedIn && sessionStore.load() == null) return
        delayedSyncJob?.cancel()
        delayedSyncJob = viewModelScope.launch {
            delay(650L)
            syncNow()
        }
    }

    private suspend fun resolveSession(): SupabaseUserSession? = withContext(Dispatchers.IO) {
        val stored = sessionStore.load() ?: return@withContext null
        val nowSeconds = System.currentTimeMillis() / 1000L
        if (stored.expiresAtEpochSeconds <= 0L || stored.expiresAtEpochSeconds > nowSeconds + 60L) return@withContext stored
        if (stored.refreshToken.isBlank()) return@withContext stored
        runCatching { authClient.refreshSession(stored.refreshToken).session }
            .getOrNull()
            ?.also { sessionStore.save(it) }
            ?: stored
    }

    private fun parseSmartRecords(text: String): List<LedgerRecord> {
        val parts = text.split(Regex("[，,。；;、\\n]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return parts.mapNotNull { part ->
            val match = AMOUNT_PATTERN.find(part) ?: return@mapNotNull null
            val amount = match.groupValues.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
            if (!amount.isFinite() || amount <= 0f) return@mapNotNull null
            val type = inferType(part)
            val date = when {
                part.contains("前天") -> shiftToday(-2)
                part.contains("昨天") -> shiftToday(-1)
                else -> LedgerStore.todayIso()
            }
            LedgerRecord(
                id = nextId(),
                title = cleanTitle(part, match.value, type),
                amount = amount,
                type = type,
                category = inferCategory(part),
                dateLabel = date
            )
        }
    }

    private fun inferType(text: String): LedgerRecordType {
        return if (Regex("(收入|工资|兼职|奖金|补贴|报销|收到|进账|红包收入)").containsMatchIn(text)) {
            LedgerRecordType.Income
        } else {
            LedgerRecordType.Expense
        }
    }

    private fun inferCategory(text: String): String = when {
        Regex("(饭|早餐|午餐|晚餐|外卖|面|米线|火锅|烧烤|餐)").containsMatchIn(text) -> "餐饮"
        Regex("(奶茶|咖啡|饮料|可乐|茶)").containsMatchIn(text) -> "饮品"
        Regex("(打车|出租|公交|地铁|高铁|火车|机票|加油|停车)").containsMatchIn(text) -> "交通"
        Regex("(淘宝|京东|拼多多|买|衣服|鞋|超市|购物)").containsMatchIn(text) -> "购物"
        Regex("(房租|水电|物业|宿舍|宽带|燃气)").containsMatchIn(text) -> "居住"
        Regex("(工资|兼职|奖金|补贴|报销|收入)").containsMatchIn(text) -> "工资"
        Regex("(礼物|红包)").containsMatchIn(text) -> "礼物"
        else -> "其他"
    }

    private fun cleanTitle(text: String, amountToken: String, type: LedgerRecordType): String {
        val clean = text
            .replace(amountToken, "")
            .replace(Regex("(今天|昨天|前天|花了|花费|消费|支出|收入|进账|收到|记一笔|记账|元|块钱|块)"), "")
            .replace(Regex("[，,。；;、]"), "")
            .trim()
        return clean.ifBlank { if (type == LedgerRecordType.Income) "未命名收入" else "未命名支出" }.take(30)
    }

    private fun shiftToday(days: Int): String {
        val calendar = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, days) }
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(calendar.time)
    }

    private fun sanitizeMoney(value: String): String {
        val filtered = value.filter { it.isDigit() || it == '.' }.take(12)
        val firstDot = filtered.indexOf('.')
        return if (firstDot < 0) filtered else filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "").take(2)
    }

    private fun nextId(): String = "record-${idSeed.incrementAndGet()}"

    private fun formatPlainNumber(value: Double): String {
        val integer = value.toLong()
        return if (value == integer.toDouble()) integer.toString() else value.toString().trimEnd('0').trimEnd('.')
    }

    private companion object {
        val AMOUNT_PATTERN = Regex("(\\d+(?:\\.\\d{1,2})?)")
    }
}

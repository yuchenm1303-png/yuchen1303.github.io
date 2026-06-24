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
import kotlinx.coroutines.flow.collect
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
    val statusMessage: String = "自然语言记账由 AI 主脑理解后调用账本内部工具。",
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

    init {
        viewModelScope.launch {
            ledgerStore.observeSnapshots().collect { snapshot ->
                state = state.copy(
                    records = snapshot.records,
                    budgetText = snapshot.budgetText,
                )
            }
        }
    }

    fun onScreenOpened() {
        reloadLocalState()
        refreshAccountAndSync()
    }

    fun reloadLocalState() {
        state = state.copy(
            records = ledgerStore.loadRecords(),
            budgetText = ledgerStore.loadBudget()
        )
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
        if (category in LedgerStore.LEDGER_CATEGORIES) {
            state = state.copy(draftCategory = category)
        }
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

    fun saveRecord() {
        val amount = state.draftAmount.toFloatOrNull() ?: run {
            state = state.copy(statusMessage = "请填写有效金额。")
            return
        }
        if (!amount.isFinite() || amount <= 0f) {
            state = state.copy(statusMessage = "金额必须大于 0。")
            return
        }
        val title = state.draftTitle.trim().ifBlank {
            if (state.draftType == LedgerRecordType.Income) "未命名收入" else "未命名支出"
        }.take(30)
        val editingId = state.editingRecordId
        val record = LedgerRecord(
            id = editingId ?: nextId(),
            title = title,
            amount = amount,
            type = state.draftType,
            category = state.draftCategory.takeIf { it in LedgerStore.LEDGER_CATEGORIES } ?: "其他",
            dateLabel = LedgerStore.normalizeDate(state.draftDate)
        )
        val next = if (editingId == null) {
            listOf(record) + state.records
        } else {
            state.records.map { current -> if (current.id == record.id) record else current }
        }.sortedWith(
            compareByDescending<LedgerRecord> { LedgerStore.normalizeDate(it.dateLabel) }
                .thenByDescending { it.id }
        )
        ledgerStore.saveRecords(next)
        state = state.copy(
            records = next,
            draftTitle = "",
            draftAmount = "",
            draftDate = LedgerStore.todayIso(),
            editingRecordId = null,
            statusMessage = if (editingId == null) "已保存 1 笔账单。" else "账单已更新。"
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
            statusMessage = "正在编辑“${record.title}”。"
        )
    }

    fun cancelEdit() {
        state = state.copy(
            draftTitle = "",
            draftAmount = "",
            draftDate = LedgerStore.todayIso(),
            editingRecordId = null,
            statusMessage = "已取消编辑。"
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
            statusMessage = "已删除 1 笔账单。"
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
                    val merged = mergedMap.values.sortedWith(
                        compareByDescending<LedgerRecord> { LedgerStore.normalizeDate(it.dateLabel) }
                            .thenByDescending { it.id }
                    )
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
}

package com.yuchen.ailedger

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.data.LedgerStore
import com.yuchen.ailedger.data.OperationWorkflowSummaryRepository
import com.yuchen.ailedger.data.PlanTaskStore
import com.yuchen.ailedger.model.LedgerRecordType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class ToolsDashboardSummaryState(
    val monthExpense: Double = 0.0,
    val budgetRemaining: Double = 0.0,
    val nextPlanTitle: String? = null,
    val nextPlanAtMillis: Long? = null,
    val activePlanCount: Int = 0,
    val latestWorkflowTitle: String? = null,
    val latestWorkflowStatusName: String? = null,
    val workflowDraftCount: Int = 0,
    val loaded: Boolean = false,
)

/**
 * Read-only summary source for the tools landing page.
 *
 * The landing page does not need the editing, cloud-sync, alarm restoration or full workflow graph
 * owned by the feature ViewModels. Those ViewModels are still created normally when their concrete
 * screen is opened.
 */
class ToolsDashboardSummaryViewModel(application: Application) : AndroidViewModel(application) {
    private val ledgerStore = LedgerStore(application)
    private val planStore = PlanTaskStore(application)
    private val workflowSummaryRepository = OperationWorkflowSummaryRepository(application)
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L

    var state by mutableStateOf(ToolsDashboardSummaryState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        val generation = ++refreshGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val summary = withContext(Dispatchers.IO) {
                val records = ledgerStore.loadRecords()
                val budget = ledgerStore.loadBudget().toDoubleOrNull() ?: 0.0
                val monthKey = LedgerStore.todayIso().take(7)
                val monthExpense = records.asSequence()
                    .filter { record ->
                        record.type == LedgerRecordType.Expense &&
                            LedgerStore.normalizeDate(record.dateLabel).startsWith(monthKey)
                    }
                    .sumOf { record -> record.amount.toDouble() }

                val now = System.currentTimeMillis()
                val tasks = planStore.loadTasks()
                val nextPlan = tasks.asSequence()
                    .filter { task -> task.enabled && !task.isFinished }
                    .filter { task -> task.nextRunAtMillis?.let { it >= now } == true }
                    .minByOrNull { task -> task.nextRunAtMillis ?: Long.MAX_VALUE }

                val drafts = workflowSummaryRepository.loadSummaries()
                val latestWorkflow = drafts.maxByOrNull { draft -> draft.updatedAtMillis }

                ToolsDashboardSummaryState(
                    monthExpense = monthExpense,
                    budgetRemaining = (budget - monthExpense).coerceAtLeast(0.0),
                    nextPlanTitle = nextPlan?.title,
                    nextPlanAtMillis = nextPlan?.nextRunAtMillis,
                    activePlanCount = tasks.count { task -> task.enabled && !task.isFinished },
                    latestWorkflowTitle = latestWorkflow?.title,
                    latestWorkflowStatusName = latestWorkflow?.status?.name,
                    workflowDraftCount = drafts.size,
                    loaded = true,
                )
            }
            if (generation == refreshGeneration) {
                state = summary
                refreshJob = null
            }
        }
    }

    override fun onCleared() {
        refreshJob?.cancel()
        super.onCleared()
    }
}

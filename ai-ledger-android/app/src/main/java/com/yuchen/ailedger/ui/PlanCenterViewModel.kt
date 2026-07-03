package com.yuchen.ailedger.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.data.PlanTaskStore
import com.yuchen.ailedger.model.PlanCenterUiState
import com.yuchen.ailedger.model.PlanDraft
import com.yuchen.ailedger.model.PlanRepeatMode
import com.yuchen.ailedger.model.PlanTask
import com.yuchen.ailedger.model.PlanTaskFilter
import com.yuchen.ailedger.model.PlanTaskType
import com.yuchen.ailedger.service.PlanScheduleCalculator
import com.yuchen.ailedger.service.PlanScheduler
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlanMutationResult(
    val ok: Boolean,
    val message: String,
)

class PlanCenterViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PlanTaskStore(application)
    private val scheduler = PlanScheduler(application)
    private var loadJob: Job? = null

    var uiState by mutableStateOf(PlanCenterUiState())
        private set

    init {
        loadLightweightSnapshot()
    }

    /**
     * 功能首页只需要计划标题、数量和下次时间。
     * 这里仅在 IO 线程解析本地快照，不重新注册 AlarmManager，也不做任何磁盘写回。
     */
    private fun loadLightweightSnapshot() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                store.loadTasks().sortedForDisplay() to scheduler.exactAlarmReady()
            }
            uiState = uiState.copy(
                tasks = snapshot.first,
                exactAlarmReady = snapshot.second,
                lastError = null,
            )
            loadJob = null
        }
    }

    /**
     * 只有计划详情页真正打开时才执行完整恢复：校正过期计划并重新注册系统闹钟。
     * 整条恢复链固定在 IO 调度器，避免首次进入功能首页时阻塞 Compose 入场帧。
     */
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                scheduler.restoreEnabledTasks().sortedForDisplay() to scheduler.exactAlarmReady()
            }
            uiState = uiState.copy(
                tasks = snapshot.first,
                exactAlarmReady = snapshot.second,
                lastError = null,
            )
            loadJob = null
        }
    }

    fun setFilter(filter: PlanTaskFilter) {
        if (uiState.filter != filter) uiState = uiState.copy(filter = filter)
    }

    fun saveTask(existingId: String?, draft: PlanDraft): PlanMutationResult {
        cancelPendingLoad()
        val title = draft.title.trim().take(80)
        val note = draft.note.trim().take(240)
        if (title.isBlank()) return failure("请输入计划名称。")
        if (draft.type == PlanTaskType.AiTask || draft.type == PlanTaskType.ConditionWatch) {
            return failure("AI 定时任务和条件监控已预留入口，当前版本先支持提醒与闹钟。")
        }

        val now = System.currentTimeMillis()
        val nextRun = PlanScheduleCalculator.firstOccurrence(draft, now)
            ?: return failure("请选择一个未来时间。")
        val previous = existingId?.let { id -> uiState.tasks.firstOrNull { it.id == id } }
        val task = PlanTask(
            id = previous?.id ?: UUID.randomUUID().toString(),
            title = title,
            note = note,
            type = draft.type,
            repeatMode = draft.repeatMode,
            scheduledAtMillis = draft.scheduledAtMillis,
            nextRunAtMillis = nextRun,
            enabled = true,
            createdAtMillis = previous?.createdAtMillis ?: now,
            lastRunAtMillis = previous?.lastRunAtMillis,
            lastResult = previous?.lastResult,
        )

        previous?.let { scheduler.cancel(it.id) }
        val scheduleResult = scheduler.schedule(task)
        if (!scheduleResult.scheduled) {
            previous?.takeIf { it.enabled && it.nextRunAtMillis != null }?.let(scheduler::schedule)
            return failure(scheduleResult.message ?: "计划调度失败。")
        }

        val updated = (uiState.tasks.filterNot { it.id == task.id } + task).sortedForDisplay()
        store.saveTasks(updated)
        uiState = uiState.copy(
            tasks = updated,
            exactAlarmReady = scheduler.exactAlarmReady(),
            lastError = null,
        )
        val action = if (previous == null) "已创建" else "已更新"
        val suffix = scheduleResult.message?.let { " $it" }.orEmpty()
        return PlanMutationResult(true, "$action“$title”。$suffix".trim())
    }

    fun toggleTask(taskId: String, enabled: Boolean): PlanMutationResult {
        cancelPendingLoad()
        val task = uiState.tasks.firstOrNull { it.id == taskId }
            ?: return failure("没有找到这个计划。")

        if (!enabled) {
            scheduler.cancel(taskId)
            publishReplacement(task.copy(enabled = false))
            return PlanMutationResult(true, "已暂停“${task.title}”。")
        }

        val now = System.currentTimeMillis()
        val nextRun = when {
            task.repeatMode == PlanRepeatMode.Once -> task.scheduledAtMillis.takeIf { it > now + 5_000L }
            else -> PlanScheduleCalculator.nextOccurrence(task, now)
        } ?: return failure("这个单次计划的时间已经过去，请编辑后再启用。")

        val updatedTask = task.copy(enabled = true, nextRunAtMillis = nextRun)
        val result = scheduler.schedule(updatedTask)
        if (!result.scheduled) return failure(result.message ?: "无法重新启用计划。")

        publishReplacement(updatedTask)
        uiState = uiState.copy(exactAlarmReady = scheduler.exactAlarmReady())
        return PlanMutationResult(true, "已启用“${task.title}”。")
    }

    fun deleteTask(taskId: String): PlanMutationResult {
        cancelPendingLoad()
        val task = uiState.tasks.firstOrNull { it.id == taskId }
            ?: return failure("没有找到这个计划。")
        scheduler.cancel(taskId)
        val updated = uiState.tasks.filterNot { it.id == taskId }.sortedForDisplay()
        store.saveTasks(updated)
        uiState = uiState.copy(tasks = updated, lastError = null)
        return PlanMutationResult(true, "已删除“${task.title}”。")
    }

    fun requestExactAlarmAccess(): Boolean = scheduler.openExactAlarmSettings()

    private fun publishReplacement(task: PlanTask) {
        val updated = uiState.tasks.map { if (it.id == task.id) task else it }.sortedForDisplay()
        store.saveTasks(updated)
        uiState = uiState.copy(tasks = updated, lastError = null)
    }

    private fun cancelPendingLoad() {
        loadJob?.cancel()
        loadJob = null
    }

    private fun failure(message: String): PlanMutationResult {
        uiState = uiState.copy(lastError = message)
        return PlanMutationResult(false, message)
    }

    private fun List<PlanTask>.sortedForDisplay(): List<PlanTask> {
        return sortedWith(
            compareByDescending<PlanTask> { it.enabled && !it.isFinished }
                .thenBy { it.nextRunAtMillis ?: Long.MAX_VALUE }
                .thenByDescending { it.createdAtMillis },
        )
    }
}

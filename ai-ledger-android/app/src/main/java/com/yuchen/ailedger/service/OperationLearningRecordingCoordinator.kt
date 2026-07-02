package com.yuchen.ailedger.service

import android.content.Context
import com.yuchen.ailedger.data.OperationTraceStore
import com.yuchen.ailedger.data.OperationTraceWriter
import com.yuchen.ailedger.data.OperationWorkflowRepository
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.WorkflowDraftStatus
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class OperationRecordingStartResult(
    val started: Boolean,
    val message: String,
)

object OperationLearningRecordingCoordinator {
    private data class ActiveSession(
        val config: OperationRecordingConfig,
        val writer: OperationTraceWriter,
        val eventCount: AtomicInteger = AtomicInteger(0),
        val stopRequested: AtomicBoolean = AtomicBoolean(false),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(OperationRecordingState())

    val state: StateFlow<OperationRecordingState> = mutableState.asStateFlow()

    @Volatile
    private var activeSession: ActiveSession? = null

    suspend fun start(
        context: Context,
        draft: LearnedWorkflowDraft,
    ): OperationRecordingStartResult = mutex.withLock {
        if (activeSession != null || mutableState.value.active) {
            return OperationRecordingStartResult(false, "已有操作正在录制，请先结束当前录制。")
        }
        val report = OperationWorkflowValidator.validate(draft, WorkflowValidationStage.RecordingIntent)
        if (!report.canProceed) {
            return OperationRecordingStartResult(
                started = false,
                message = report.blockingIssues.firstOrNull()?.message ?: "操作草稿未通过录制前校验。",
            )
        }
        if (!AiAgentAccessibilityService.isConnected()) {
            return OperationRecordingStartResult(false, "请先启用 AI 智能体无障碍服务。")
        }

        val applicationContext = context.applicationContext
        val repository = OperationWorkflowRepository.get(applicationContext)
        val traceStore = OperationTraceStore(applicationContext)
        val demonstrationId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val config = OperationRecordingConfig(
            workflowId = draft.id,
            demonstrationId = demonstrationId,
            workflowTitle = draft.title,
            allowedPackages = draft.appScope.normalizedPackages.toSet(),
            allowSystemSurfaces = draft.appScope.allowSystemSurfaces,
            startedAtMillis = startedAt,
        )

        mutableState.value = OperationRecordingState(
            phase = OperationRecordingPhase.Starting,
            workflowId = draft.id,
            demonstrationId = demonstrationId,
            workflowTitle = draft.title,
            allowedPackages = config.allowedPackages,
            startedAtMillis = startedAt,
            message = "正在准备加密轨迹存储…",
        )

        val writer = runCatching {
            withContext(Dispatchers.IO) {
                traceStore.openSession(
                    demonstrationId = demonstrationId,
                    workflowId = draft.id,
                    startedAtMillis = startedAt,
                )
            }
        }.getOrElse { error ->
            mutableState.value = OperationRecordingState(
                phase = OperationRecordingPhase.Failed,
                workflowId = draft.id,
                workflowTitle = draft.title,
                message = "无法创建加密轨迹：${error.message ?: "未知错误"}",
            )
            return OperationRecordingStartResult(false, mutableState.value.message.orEmpty())
        }

        val prepared = runCatching {
            repository.sealInterruptedDemonstrations(startedAt)
            repository.beginDemonstration(
                demonstrationId = demonstrationId,
                workflowId = draft.id,
                encryptedTracePath = writer.path,
                createdAtMillis = startedAt,
            )
        }.isSuccess
        if (!prepared) {
            withContext(Dispatchers.IO) {
                writer.close(
                    OperationRecordingMarkerRecord(
                        capturedAtMillis = System.currentTimeMillis(),
                        marker = "session_prepare_failed",
                    ),
                )
                traceStore.deleteTrace(writer.path)
            }
            mutableState.value = OperationRecordingState(
                phase = OperationRecordingPhase.Failed,
                workflowId = draft.id,
                workflowTitle = draft.title,
                message = "无法登记演示会话，请稍后重试。",
            )
            return OperationRecordingStartResult(false, mutableState.value.message.orEmpty())
        }

        val session = ActiveSession(config = config, writer = writer)
        activeSession = session
        val serviceStarted = withContext(Dispatchers.Main.immediate) {
            AiAgentAccessibilityService.beginOperationRecording(config)
        }
        if (!serviceStarted) {
            activeSession = null
            withContext(Dispatchers.IO) {
                writer.close(
                    OperationRecordingMarkerRecord(
                        capturedAtMillis = System.currentTimeMillis(),
                        marker = "service_start_rejected",
                    ),
                )
                traceStore.deleteTrace(writer.path)
                repository.finishDemonstration(
                    demonstrationId = demonstrationId,
                    workflowId = draft.id,
                    status = "failed",
                    redactionStatus = "trace_deleted",
                    workflowStatus = WorkflowDraftStatus.Intent,
                    completedAtMillis = System.currentTimeMillis(),
                )
            }
            mutableState.value = OperationRecordingState(
                phase = OperationRecordingPhase.Failed,
                workflowId = draft.id,
                workflowTitle = draft.title,
                message = "无障碍服务当前正执行其他任务，无法开始录制。",
            )
            return OperationRecordingStartResult(false, mutableState.value.message.orEmpty())
        }

        mutableState.value = OperationRecordingState(
            phase = OperationRecordingPhase.Recording,
            workflowId = draft.id,
            demonstrationId = demonstrationId,
            workflowTitle = draft.title,
            allowedPackages = config.allowedPackages,
            startedAtMillis = startedAt,
            capturedEventCount = 0,
            message = "录制已开始。只会采集允许应用内的脱敏动作证据。",
        )
        OperationRecordingStartResult(true, "录制已开始。")
    }

    fun append(record: OperationTraceRecord): Boolean {
        val session = activeSession ?: return false
        if (mutableState.value.phase != OperationRecordingPhase.Recording) return false
        val appended = session.writer.append(record)
        if (!appended) {
            if (session.stopRequested.compareAndSet(false, true)) {
                requestStop(null, OperationRecordingStopReason.InternalError)
            }
            return false
        }

        val nextCount = session.eventCount.incrementAndGet()
        if (nextCount == 1 || nextCount % STATE_EVENT_COUNT_STEP == 0) {
            mutableState.value = mutableState.value.copy(capturedEventCount = nextCount)
        }
        if (nextCount >= MAX_TRACE_RECORDS && session.stopRequested.compareAndSet(false, true)) {
            session.writer.append(
                OperationRecordingMarkerRecord(
                    capturedAtMillis = System.currentTimeMillis(),
                    marker = "event_limit_reached",
                    detail = "count=$nextCount",
                ),
            )
            requestStop(null, OperationRecordingStopReason.EventLimit)
        }
        return true
    }

    suspend fun stop(
        context: Context?,
        reason: OperationRecordingStopReason,
    ): Boolean = mutex.withLock {
        val session = activeSession ?: return false
        activeSession = null
        val recordCount = session.eventCount.get()
        mutableState.value = mutableState.value.copy(
            phase = OperationRecordingPhase.Stopping,
            capturedEventCount = recordCount,
            message = "正在封存加密轨迹…",
        )

        withContext(Dispatchers.Main.immediate) {
            AiAgentAccessibilityService.endOperationRecording(session.config.demonstrationId)
        }

        val applicationContext = context?.applicationContext
            ?: AiAgentAccessibilityService.applicationContextOrNull()
        val userAcceptedCapture = reason in setOf(
            OperationRecordingStopReason.UserFinished,
            OperationRecordingStopReason.NotificationFinished,
            OperationRecordingStopReason.DurationLimit,
            OperationRecordingStopReason.EventLimit,
        )
        val captured = userAcceptedCapture && recordCount > 0
        val finalMarker = OperationRecordingMarkerRecord(
            capturedAtMillis = System.currentTimeMillis(),
            marker = if (captured) "session_captured" else "session_aborted",
            detail = "reason=${reason.storageValue};records=$recordCount",
        )

        val finalized = runCatching {
            val resolvedContext = requireNotNull(applicationContext) { "application context unavailable" }
            withContext(Dispatchers.IO) {
                session.writer.close(finalMarker)
                val repository = OperationWorkflowRepository.get(resolvedContext)
                val traceStore = OperationTraceStore(resolvedContext)
                if (!captured) traceStore.deleteTrace(session.writer.path)
                repository.finishDemonstration(
                    demonstrationId = session.config.demonstrationId,
                    workflowId = session.config.workflowId,
                    status = if (captured) "captured" else "aborted",
                    redactionStatus = if (captured) "sealed" else "trace_deleted",
                    workflowStatus = if (captured) WorkflowDraftStatus.Compiling else WorkflowDraftStatus.Intent,
                    completedAtMillis = System.currentTimeMillis(),
                )
            }
        }.isSuccess

        mutableState.value = when {
            captured && finalized -> OperationRecordingState(
                phase = OperationRecordingPhase.Captured,
                workflowId = session.config.workflowId,
                demonstrationId = session.config.demonstrationId,
                workflowTitle = session.config.workflowTitle,
                allowedPackages = session.config.allowedPackages,
                startedAtMillis = session.config.startedAtMillis,
                capturedEventCount = recordCount,
                message = "演示已加密封存，等待整理为可审核流程。",
            )
            !captured && finalized -> OperationRecordingState(
                phase = OperationRecordingPhase.Idle,
                message = when {
                    userAcceptedCapture && recordCount == 0 -> "没有采集到有效操作，录制已结束且未保留空轨迹。"
                    reason == OperationRecordingStopReason.ScopeViolation -> "检测到未授权应用，录制已停止且轨迹已删除。"
                    reason == OperationRecordingStopReason.TaskStarted -> "智能体任务已启动，操作录制已安全结束。"
                    else -> "录制已取消，未保留轨迹。"
                },
            )
            else -> OperationRecordingState(
                phase = OperationRecordingPhase.Failed,
                workflowId = session.config.workflowId,
                workflowTitle = session.config.workflowTitle,
                message = "轨迹封存失败，未进入后续编译阶段。",
            )
        }
        finalized
    }

    fun requestStop(
        context: Context?,
        reason: OperationRecordingStopReason,
    ) {
        scope.launch { stop(context, reason) }
    }

    fun resetTerminalState() {
        if (!mutableState.value.active) mutableState.value = OperationRecordingState()
    }

    fun activeConfig(): OperationRecordingConfig? = activeSession?.config

    private const val MAX_TRACE_RECORDS = 600
    private const val STATE_EVENT_COUNT_STEP = 4
}

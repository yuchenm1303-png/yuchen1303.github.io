package com.yuchen.ailedger.service

import android.content.Context
import android.content.Intent
import com.yuchen.ailedger.MainActivity
import com.yuchen.ailedger.data.OperationWorkflowRepository
import com.yuchen.ailedger.data.VisualDemonstrationSession
import com.yuchen.ailedger.data.VisualDemonstrationStore
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.model.WorkflowExecutionMode
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
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

/**
 * 薄客户端 Record 阶段。
 *
 * 本地只采集授权应用的视觉关键帧并加密封存；结束后把整段演示交给云端生成 Skill。
 * 本地不会扫描或编译控件节点；也不再开启无障碍事件录制、保存节点树或调用固定路线编译器。
 */
object OperationLearningRecordingCoordinator {
    private class ActiveSession(
        val config: OperationRecordingConfig,
        val visualSession: VisualDemonstrationSession,
        val recorder: VisualDemonstrationRecorder,
        val frameCount: AtomicInteger = AtomicInteger(0),
        val stopRequested: AtomicBoolean = AtomicBoolean(false),
    ) {
        var captureJob: Job? = null
        var timeoutJob: Job? = null
    }

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
            return OperationRecordingStartResult(false, "已有视觉演示正在录制，请先结束当前录制。")
        }
        val report = OperationWorkflowValidator.validate(draft, WorkflowValidationStage.RecordingIntent)
        if (!report.canProceed) {
            return OperationRecordingStartResult(
                started = false,
                message = report.blockingIssues.firstOrNull()?.message ?: "Skill 草稿未通过录制前校验。",
            )
        }
        if (!AiAgentAccessibilityService.isConnected()) {
            return OperationRecordingStartResult(false, "请先启用 AI 智能体无障碍服务，以便安全截图和执行手势。")
        }

        val applicationContext = context.applicationContext
        val repository = OperationWorkflowRepository.get(applicationContext)
        val visualStore = VisualDemonstrationStore(applicationContext)
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
            message = "正在准备加密视觉演示空间…",
        )

        val visualSession = runCatching {
            withContext(Dispatchers.IO) {
                visualStore.createSession(
                    demonstrationId = demonstrationId,
                    workflowId = draft.id,
                    workflowTitle = draft.title,
                    goal = draft.goal,
                    allowedPackages = config.allowedPackages,
                    startedAtMillis = startedAt,
                )
            }
        }.getOrElse { error ->
            mutableState.value = OperationRecordingState(
                phase = OperationRecordingPhase.Failed,
                workflowId = draft.id,
                workflowTitle = draft.title,
                message = "无法创建视觉演示空间：${error.message ?: "未知错误"}",
            )
            return OperationRecordingStartResult(false, mutableState.value.message.orEmpty())
        }

        val prepared = runCatching {
            repository.sealInterruptedDemonstrations(startedAt)
            repository.beginDemonstration(
                demonstrationId = demonstrationId,
                workflowId = draft.id,
                encryptedTracePath = visualSession.manifestPath,
                createdAtMillis = startedAt,
            )
        }.isSuccess
        if (!prepared) {
            visualStore.delete(visualSession.manifestPath)
            mutableState.value = OperationRecordingState(
                phase = OperationRecordingPhase.Failed,
                workflowId = draft.id,
                workflowTitle = draft.title,
                message = "无法登记视觉演示会话，请稍后重试。",
            )
            return OperationRecordingStartResult(false, mutableState.value.message.orEmpty())
        }

        lateinit var session: ActiveSession
        val recorder = VisualDemonstrationRecorder(
            session = visualSession,
            allowedPackages = config.allowedPackages,
            onFrameCountChanged = { count ->
                session.frameCount.set(count)
                val current = mutableState.value
                if (current.phase == OperationRecordingPhase.Recording) {
                    mutableState.value = current.copy(capturedEventCount = count)
                }
            },
        )
        session = ActiveSession(
            config = config,
            visualSession = visualSession,
            recorder = recorder,
        )
        activeSession = session

        if (!AiAgentAccessibilityService.beginVisualDemonstrationEventHints(config)) {
            activeSession = null
            visualStore.delete(visualSession.manifestPath)
            repository.finishDemonstration(
                demonstrationId = demonstrationId,
                workflowId = draft.id,
                status = "aborted",
                redactionStatus = "visual_deleted",
                workflowStatus = WorkflowDraftStatus.Intent,
                completedAtMillis = System.currentTimeMillis(),
            )
            mutableState.value = OperationRecordingState(
                phase = OperationRecordingPhase.Failed,
                workflowId = draft.id,
                workflowTitle = draft.title,
                message = "无法开启演示动作锚点，请稍后重试。",
            )
            return OperationRecordingStartResult(false, mutableState.value.message.orEmpty())
        }

        val targetPackage = config.allowedPackages.firstOrNull()
        val targetOpened = targetPackage?.let { packageName ->
            launchTargetApplication(applicationContext, packageName)
        } == true
        val startedMessage = if (targetOpened) {
            "视觉演示已开始，已打开所选应用。请正常完成一次任务。"
        } else {
            "视觉演示已开始。请手动打开所选应用并正常完成一次任务。"
        }

        mutableState.value = OperationRecordingState(
            phase = OperationRecordingPhase.Recording,
            workflowId = draft.id,
            demonstrationId = demonstrationId,
            workflowTitle = draft.title,
            allowedPackages = config.allowedPackages,
            startedAtMillis = startedAt,
            capturedEventCount = 0,
            message = startedMessage,
        )
        session.captureJob = scope.launch(Dispatchers.Default) {
            if (targetOpened) delay(TARGET_APP_SETTLE_DELAY_MS)
            recorder.runCaptureLoop()
        }
        session.timeoutJob = scope.launch {
            delay(MAX_RECORDING_DURATION_MS)
            if (session.stopRequested.compareAndSet(false, true)) {
                requestStop(applicationContext, OperationRecordingStopReason.DurationLimit)
            }
        }

        OperationRecordingStartResult(true, startedMessage)
    }

    /** 旧无障碍录制入口仅为二进制兼容保留，新主链永远不接收节点或事件记录。 */
    fun append(@Suppress("UNUSED_PARAMETER") record: OperationTraceRecord): Boolean = false

    fun onUserActionEvent(
        packageName: String,
        eventType: String,
        occurredAtMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val session = activeSession ?: return false
        if (packageName !in session.config.allowedPackages) return false
        session.recorder.requestActionCapture(
            eventType = eventType,
            packageName = packageName,
            occurredAtMillis = occurredAtMillis,
        )
        return true
    }

    suspend fun stop(
        context: Context?,
        reason: OperationRecordingStopReason,
    ): Boolean = mutex.withLock {
        val session = activeSession ?: return false
        activeSession = null
        AiAgentAccessibilityService.endVisualDemonstrationEventHints(session.config.demonstrationId)
        session.timeoutJob?.cancel()
        val applicationContext = context?.applicationContext
            ?: AiAgentAccessibilityService.applicationContextOrNull()
        mutableState.value = mutableState.value.copy(
            phase = OperationRecordingPhase.Stopping,
            capturedEventCount = session.visualSession.frameCount,
            message = "正在封存视觉演示并交给云端理解…",
        )
        if (reason.shouldReturnToHostApp()) {
            applicationContext?.let(::returnToHostApp)
        }

        session.captureJob?.cancelAndJoin()
        runCatching { session.recorder.captureFinalFrame() }
        session.recorder.stop()
        val frameCount = session.visualSession.frameCount
        session.frameCount.set(frameCount)

        val userAcceptedCapture = reason in setOf(
            OperationRecordingStopReason.UserFinished,
            OperationRecordingStopReason.NotificationFinished,
            OperationRecordingStopReason.DurationLimit,
            OperationRecordingStopReason.EventLimit,
        )
        val captured = userAcceptedCapture && frameCount >= MINIMUM_VISUAL_FRAMES
        val completedAt = System.currentTimeMillis()
        val finalized = runCatching {
            val resolvedContext = requireNotNull(applicationContext) { "application context unavailable" }
            withContext(Dispatchers.IO) {
                session.visualSession.seal(completedAt)
                val repository = OperationWorkflowRepository.get(resolvedContext)
                val visualStore = VisualDemonstrationStore(resolvedContext)
                if (!captured) visualStore.delete(session.visualSession.manifestPath)
                repository.finishDemonstration(
                    demonstrationId = session.config.demonstrationId,
                    workflowId = session.config.workflowId,
                    status = if (captured) "captured" else "aborted",
                    redactionStatus = if (captured) "visual_encrypted" else "visual_deleted",
                    workflowStatus = if (captured) WorkflowDraftStatus.Compiling else WorkflowDraftStatus.Intent,
                    completedAtMillis = completedAt,
                )
            }
        }.isSuccess

        val learningOutcome = if (captured && finalized && applicationContext != null) {
            OperationSkillLearningCoordinator.learn(
                context = applicationContext,
                workflowId = session.config.workflowId,
                demonstrationId = session.config.demonstrationId,
                manifestPath = session.visualSession.manifestPath,
            )
        } else {
            null
        }

        mutableState.value = when {
            captured && finalized -> OperationRecordingState(
                phase = OperationRecordingPhase.Captured,
                workflowId = session.config.workflowId,
                demonstrationId = session.config.demonstrationId,
                workflowTitle = session.config.workflowTitle,
                allowedPackages = session.config.allowedPackages,
                startedAtMillis = session.config.startedAtMillis,
                capturedEventCount = frameCount,
                message = when {
                    learningOutcome?.completed == true -> learningOutcome.message
                    learningOutcome != null -> "视觉演示已加密保存，但${learningOutcome.message}。演示证据仍保留，可重新提交云端理解。"
                    else -> "视觉演示已加密保存，等待云端生成 Skill。"
                },
            )
            !captured && finalized -> OperationRecordingState(
                phase = OperationRecordingPhase.Idle,
                message = when {
                    userAcceptedCapture && frameCount < MINIMUM_VISUAL_FRAMES -> "有效视觉关键帧不足，未保留这次演示。请进入目标应用后再完整演示一次。"
                    reason == OperationRecordingStopReason.ScopeViolation -> "未采集授权范围外的画面，本次演示已取消。"
                    reason == OperationRecordingStopReason.TaskStarted -> "智能体任务已启动，视觉演示已安全结束。"
                    else -> "视觉演示已取消，未保留画面。"
                },
            )
            else -> OperationRecordingState(
                phase = OperationRecordingPhase.Failed,
                workflowId = session.config.workflowId,
                workflowTitle = session.config.workflowTitle,
                message = "视觉演示封存失败，未提交云端理解。",
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

    private fun launchTargetApplication(
        context: Context,
        packageName: String,
    ): Boolean = runCatching {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return@runCatching false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        true
    }.getOrDefault(false)

    private fun returnToHostApp(context: Context) {
        runCatching {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                )
            }
            context.startActivity(intent)
        }
    }

    private fun OperationRecordingStopReason.shouldReturnToHostApp(): Boolean = this in setOf(
        OperationRecordingStopReason.UserFinished,
        OperationRecordingStopReason.UserCancelled,
        OperationRecordingStopReason.NotificationFinished,
        OperationRecordingStopReason.DurationLimit,
        OperationRecordingStopReason.EventLimit,
    )

    private const val TARGET_APP_SETTLE_DELAY_MS = 650L
    private const val MINIMUM_VISUAL_FRAMES = 2
    private const val MAX_RECORDING_DURATION_MS = 3L * 60L * 1_000L
}

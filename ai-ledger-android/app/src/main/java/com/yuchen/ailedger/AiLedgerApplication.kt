package com.yuchen.ailedger

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import com.yuchen.ailedger.data.AgentAnalyticsOwnerRuntime
import com.yuchen.ailedger.data.AppCacheMaintenance
import com.yuchen.ailedger.data.AssistantAccountSessionRuntime
import com.yuchen.ailedger.data.AssistantCustomInstructionsRepository
import com.yuchen.ailedger.data.AssistantMemoryDiagnostics
import com.yuchen.ailedger.data.AssistantMemoryMutationRuntime
import com.yuchen.ailedger.data.AssistantMemoryRepository
import com.yuchen.ailedger.data.StockHttpClient
import com.yuchen.ailedger.data.SupabaseAuthRepository
import com.yuchen.ailedger.data.UserProfileRepository
import com.yuchen.ailedger.data.switchAccount
import com.yuchen.ailedger.model.ChatAttachmentPayloadStore
import com.yuchen.ailedger.service.AgentAnalyticsRuntime
import com.yuchen.ailedger.service.AgentOverlayProgress
import com.yuchen.ailedger.service.AgentOverlayService
import com.yuchen.ailedger.service.AgentRuntimeController
import com.yuchen.ailedger.service.AssistantFloatingChatBridge
import com.yuchen.ailedger.service.ClientToolReceiptDeliveryRuntime
import com.yuchen.ailedger.service.VisualIntelligenceDiagnosticsStore
import com.yuchen.ailedger.ui.StartupPerformanceGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AiLedgerApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 普通聊天的进程级唯一 ViewModel。
     *
     * 首页和 Agent O 无障碍浮窗共享同一实例，因此退出 Activity 后流式请求、消息、附件和
     * 联网状态仍保持同一条业务链，不会出现两套聊天状态。
     */
    val assistantViewModel: AssistantViewModel by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AssistantViewModel(this)
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        AgentAnalyticsOwnerRuntime.initialize(applicationContext)
        AssistantFloatingChatBridge.attach(
            viewModel = assistantViewModel,
            scope = applicationScope,
            appContext = applicationContext,
        )

        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                ClientToolReceiptDeliveryRuntime.reschedulePending(applicationContext)
            }
        }

        applicationScope.launch {
            var authenticatedSettingsRepositoriesReady = false
            SupabaseAuthRepository.get(applicationContext).state.collectLatest { accountState ->
                val activeSession = accountState.session?.takeIf { accountState.isLoggedIn }
                AgentAnalyticsOwnerRuntime.switchAccount(applicationContext, activeSession)

                val ticket = AssistantAccountSessionRuntime.updateSession(activeSession)
                AssistantMemoryMutationRuntime.switchAccount(ticket)
                AssistantMemoryDiagnostics.switchAccount(ticket)

                if (accountState.isLoggedIn && !authenticatedSettingsRepositoriesReady) {
                    withTimeoutOrNull(5_000L) {
                        StartupPerformanceGate.awaitDeferredBusinessWindow()
                    }
                    UserProfileRepository.get(applicationContext)
                    AssistantMemoryRepository.get(applicationContext)
                    AssistantCustomInstructionsRepository.get(applicationContext)
                    authenticatedSettingsRepositoriesReady = true
                }
            }
        }

        applicationScope.launch {
            var previousAnalyticsProgress = AgentOverlayProgress()
            var previousOverlaySyncKey: OverlaySyncKey? = null
            var previousObservationKey: ProgressObservationKey? = null
            var diagnosticsStore: VisualIntelligenceDiagnosticsStore? = null

            AgentRuntimeController.progress.collectLatest { progress ->
                val overlaySyncKey = OverlaySyncKey(
                    enabled = progress.enabled,
                    running = progress.running,
                    hasTask = progress.taskId > 0L,
                    awaitingConfirmation = progress.pendingConfirmation != null,
                    awaitingUserInput = progress.pendingUserInput != null,
                    userTakeoverPaused = progress.userTakeoverPaused,
                )
                if (overlaySyncKey != previousOverlaySyncKey) {
                    AgentOverlayService.syncForProgress(this@AiLedgerApplication, progress)
                    previousOverlaySyncKey = overlaySyncKey
                }

                val terminalAnalyticsProgress = previousAnalyticsProgress.takeIf { previous ->
                    previous.taskId > 0L &&
                        previous.running &&
                        progress.taskId == 0L &&
                        !progress.running
                }?.let { previous ->
                    progress.copy(
                        taskId = previous.taskId,
                        currentAction = previous.currentAction,
                        logs = (previous.logs + progress.logs).takeLast(24),
                    )
                }
                val observationProgress = terminalAnalyticsProgress ?: progress
                val observationKey = ProgressObservationKey.from(observationProgress)
                val observationChanged = observationKey != previousObservationKey
                if (observationChanged) {
                    AgentAnalyticsRuntime.observeProgress(observationProgress)
                    previousObservationKey = observationKey
                }
                previousAnalyticsProgress = progress

                if (observationChanged && (progress.taskId > 0L || progress.running)) {
                    val store = diagnosticsStore
                        ?: VisualIntelligenceDiagnosticsStore.get(applicationContext).also {
                            diagnosticsStore = it
                        }
                    store.observeProgress(progress)
                }
            }
        }

        applicationScope.launch(Dispatchers.IO) {
            val uiReachedStableWindow = withTimeoutOrNull(10_000L) {
                StartupPerformanceGate.awaitDeferredBusinessWindow()
                true
            } == true
            if (uiReachedStableWindow) {
                AppCacheMaintenance.run(applicationContext)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val appHiddenOrWorse = level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        val runtimePressure = level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
        if (appHiddenOrWorse || runtimePressure) {
            ChatAttachmentPayloadStore.trimMemory(aggressive = appHiddenOrWorse)
            StockHttpClient.trimMemory(aggressive = appHiddenOrWorse)
        }
    }

    override fun onLowMemory() {
        ChatAttachmentPayloadStore.trimMemory(aggressive = true)
        StockHttpClient.trimMemory(aggressive = true)
        super.onLowMemory()
    }

    private data class OverlaySyncKey(
        val enabled: Boolean,
        val running: Boolean,
        val hasTask: Boolean,
        val awaitingConfirmation: Boolean,
        val awaitingUserInput: Boolean,
        val userTakeoverPaused: Boolean,
    )

    private data class ProgressObservationKey(
        val taskId: Long,
        val enabled: Boolean,
        val running: Boolean,
        val status: String,
        val currentAction: String,
        val lastResult: String,
        val logCount: Int,
        val lastLog: String,
        val pendingConfirmationHash: Int,
        val pendingUserInputHash: Int,
        val userTakeoverPaused: Boolean,
    ) {
        companion object {
            fun from(progress: AgentOverlayProgress): ProgressObservationKey = ProgressObservationKey(
                taskId = progress.taskId,
                enabled = progress.enabled,
                running = progress.running,
                status = progress.status,
                currentAction = progress.currentAction,
                lastResult = progress.lastResult,
                logCount = progress.logs.size,
                lastLog = progress.logs.lastOrNull().orEmpty(),
                pendingConfirmationHash = progress.pendingConfirmation?.hashCode() ?: 0,
                pendingUserInputHash = progress.pendingUserInput?.hashCode() ?: 0,
                userTakeoverPaused = progress.userTakeoverPaused,
            )
        }
    }

    companion object {
        @Volatile
        private var appContext: Context? = null

        fun contextOrNull(): Context? = appContext
    }
}

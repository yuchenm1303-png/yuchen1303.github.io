package com.yuchen.ailedger.service

import android.content.Context
import com.yuchen.ailedger.model.ChatModel
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class ControllerHandoffResult(
    val sourcePackage: String,
    val step: CloudAgentStep,
    val execution: AgentExecutionResult,
    val contract: AgentTaskExecutionContract? = null,
) {
    fun prependTo(result: AgentTaskRunResult): AgentTaskRunResult {
        val first = AgentTaskStepLog(
            index = 1,
            app = sourcePackage,
            step = step,
            execution = execution,
        )
        val rest = result.logs.mapIndexed { index, log ->
            log.copy(index = index + 2)
        }
        return result.copy(logs = listOf(first) + rest)
    }
}

internal data class ControllerPlannerSelection(
    val contract: AgentTaskExecutionContract? = null,
    val app: InstalledAppEntry? = null,
    val step: CloudAgentStep? = null,
    val rejectionReason: String = "",
) {
    val accepted: Boolean
        get() = contract != null && app != null && step != null && rejectionReason.isBlank()
}

class AgentOrchestrator(
    private val aiWorkerClient: AiWorkerClient,
    appContext: Context,
) {
    private val applicationContext = appContext.applicationContext
    private val installedAppIndex = InstalledAppIndex(applicationContext)
    private val appCapabilityRegistry = AppCapabilityRegistry(applicationContext, installedAppIndex)
    private val deviceToolExecutor = DeviceToolExecutor(applicationContext, installedAppIndex)
    private val clientDeviceId by lazy { AgentClientIdentity.getOrCreateDeviceId(applicationContext) }

    suspend fun run(
        goal: String,
        modelPreference: ChatModel,
        maxSteps: Int = Int.MAX_VALUE,
        executionMode: AgentExecutionMode,
    ): AgentTaskRunResult {
        return when (routeFor(executionMode)) {
            AgentOrchestratorRoute.LegacyRunner -> AgentTaskRunner(aiWorkerClient, applicationContext).run(
                goal = goal,
                modelPreference = modelPreference,
                maxSteps = maxSteps,
                executionMode = executionMode,
            )
            AgentOrchestratorRoute.VisualLoop -> {
                val handoff = prepareControllerHandoff(goal, executionMode)
                if (handoff != null && !handoff.execution.ok) {
                    val message = handoff.execution.message.ifBlank { "目标应用打开失败，视觉任务未启动。" }
                    AgentRuntimeController.finishTask(message, completed = false)
                    return AgentTaskRunResult(
                        completed = false,
                        stoppedForConfirmation = false,
                        message = message,
                        logs = listOf(
                            AgentTaskStepLog(1, handoff.sourcePackage, handoff.step, handoff.execution),
                        ),
                    )
                }

                val result = VisualLoopRunner(aiWorkerClient, applicationContext).run(
                    goal = goal,
                    maxSteps = Int.MAX_VALUE,
                    executionMode = executionMode,
                    initialTaskContract = handoff?.contract,
                )
                handoff?.prependTo(result) ?: result
            }
        }
    }

    private suspend fun prepareControllerHandoff(
        goal: String,
        executionMode: AgentExecutionMode,
    ): ControllerHandoffResult? {
        if (executionMode == AgentExecutionMode.NormalChatDeviceTool) return null
        if (executionMode == AgentExecutionMode.VisualForce && !AgentRuntimeController.isEnabled()) return null
        if (!AiAgentAccessibilityService.isConnected()) return null

        val observation = try {
            withContext(Dispatchers.Default) {
                AiAgentAccessibilityService.captureFreshSnapshot(forceVisual = false)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return null
        }
        if (!observation.enabled || !observation.serviceConnected) return null

        val sourcePackage = observation.packageName.ifBlank { "unknown" }
        if (sourcePackage != applicationContext.packageName) return null

        val installedApps = withContext(Dispatchers.IO) {
            installedAppIndex.getLaunchableApps()
        }.filterNot { it.packageName == applicationContext.packageName }
        if (installedApps.isEmpty()) {
            return controllerFailure(sourcePackage, "设备上没有可供智能体启动的目标应用。")
        }

        val contractRequest = AgentTaskExecutionContract.controllerRequest()
        val deviceProfile = AgentDeviceProfile.current()
        var lastDeclaredContract: AgentTaskExecutionContract? = null
        val appContext = appCapabilityRegistry.buildVisualContext(installedApps)
            .sortedWith(compareBy<VisualAgentAppContextItem> { it.label.lowercase() }.thenBy { it.packageName })
            .take(MAX_CONTROLLER_APP_CONTEXT_ITEMS)
        val recentActions = mutableListOf(
            contractRequest.toPromptLine(),
            deviceProfile.toPromptLine(),
            appCapabilityRegistry.compactPromptLine(appContext),
            "semantic_routing:v1|owner=gui_plus|androidLocalGoalParsing=false|modelMustUnderstandFullUserInstruction=true",
            "task_contract_request:v1|plannerMustDeclare=preferredSurface,browserFallbackAllowed,requiredCapabilities,requirePostActionVerification|returnIn=agentStep.arguments",
            "controller_handoff:v3|currentSurface=assistant_controller|mustReturn=open_app|homeNotRequired=true|validateAppCapability=true|selectFromCanonicalInstalledApps=true",
        )
        val syntheticControllerSnapshot = AgentScreenSnapshot(
            currentApp = applicationContext.packageName,
            packageName = applicationContext.packageName,
            nodeCount = 0,
            capturedNodeCount = 0,
            texts = emptyList(),
            allNodes = emptyList(),
            clickableNodes = emptyList(),
            inputNodes = emptyList(),
            scrollableNodes = emptyList(),
            visual = null,
        )
        val sessionId = AgentClientIdentity.newVisualSessionId()

        repeat(MAX_CONTROLLER_SELECTION_ATTEMPTS) { attempt ->
            val modelStep = try {
                withContext(Dispatchers.IO) {
                    aiWorkerClient.requestVisualAgentStep(
                        goal = goal,
                        snapshot = syntheticControllerSnapshot,
                        recentActions = recentActions,
                        visualHistory = emptyList(),
                        appContext = appContext,
                        deviceId = clientDeviceId,
                        agentSessionId = sessionId,
                        executionMode = executionMode,
                        deviceProfile = deviceProfile,
                        taskContract = contractRequest,
                        taskContractRequired = true,
                    )
                }.also { AgentRuntimeController.noteModelOutput(it.rawModelOutput) }
                    .step
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                recentActions += "controller_selection_failed:network=${error.message.orEmpty().take(220)}"
                null
            }

            val selection = evaluateControllerPlannerStep(
                step = modelStep,
                installedApps = installedApps,
                assistantPackageName = applicationContext.packageName,
            )
            if (!selection.accepted) {
                recentActions += "controller_selection_rejected:attempt=${attempt + 1}|reason=${selection.rejectionReason.take(260)}"
                return@repeat
            }

            val declaredContract = requireNotNull(selection.contract)
            val selectedApp = requireNotNull(selection.app)
            val selectedStep = requireNotNull(selection.step)
            lastDeclaredContract = declaredContract

            val validation = appCapabilityRegistry.validateSelection(declaredContract, selectedApp)
            if (!validation.ok) {
                recentActions += "controller_selection_rejected:attempt=${attempt + 1}|app=${selectedApp.label.take(40)}|package=${selectedApp.packageName.take(80)}|reason=${validation.message.take(220)}"
                return@repeat
            }

            return executeControllerOpenApp(
                sourcePackage = sourcePackage,
                step = selectedStep,
                contract = declaredContract,
            )
        }

        val message = buildString {
            append("云端模型未能选择符合任务契约的已安装目标应用。")
            when (lastDeclaredContract?.preferredSurface) {
                AgentSurfacePreference.SystemSettings -> append(" 云端已声明系统设置界面，但没有返回可执行的规范设置应用。")
                AgentSurfacePreference.NativeApp -> append(" 云端已声明原生应用界面，但没有返回与本机清单一致的目标应用。")
                AgentSurfacePreference.Browser -> append(" 云端已声明浏览器界面，但没有返回与本机清单一致的浏览器。")
                AgentSurfacePreference.Any -> append(" 云端没有完成目标应用选择。")
                null -> append(" 云端没有返回完整任务契约和规范 open_app；Android 未进行本地语义猜测。")
            }
        }
        return controllerFailure(sourcePackage, message)
    }

    private suspend fun executeControllerOpenApp(
        sourcePackage: String,
        step: CloudAgentStep,
        contract: AgentTaskExecutionContract,
    ): ControllerHandoffResult {
        AgentRuntimeController.noteAction(step)
        val execution = withContext(Dispatchers.IO) {
            deviceToolExecutor.execute(step, confirmedHighRisk = false)
        }
        AgentRuntimeController.noteResult(step, execution)
        if (execution.ok && execution.shouldContinue) delay(CONTROLLER_APP_OPEN_SETTLE_MS)
        return ControllerHandoffResult(
            sourcePackage = sourcePackage,
            step = step,
            execution = execution,
            contract = contract,
        )
    }

    private fun controllerFailure(sourcePackage: String, message: String): ControllerHandoffResult {
        val step = CloudAgentStep(
            type = "need_user_help",
            reason = message,
        )
        return ControllerHandoffResult(
            sourcePackage = sourcePackage,
            step = step,
            execution = AgentExecutionResult(
                ok = false,
                message = message,
                shouldContinue = false,
            ),
        )
    }

    companion object {
        private const val CONTROLLER_APP_OPEN_SETTLE_MS = 700L
        private const val MAX_CONTROLLER_APP_CONTEXT_ITEMS = 160
        private const val MAX_CONTROLLER_SELECTION_ATTEMPTS = 2
        private val CONTROLLER_FORBIDDEN_STEP_TYPES = setOf("wait", "home", "tap_xy")

        fun routeFor(executionMode: AgentExecutionMode): AgentOrchestratorRoute {
            return when (executionMode) {
                AgentExecutionMode.NormalChatDeviceTool -> AgentOrchestratorRoute.LegacyRunner
                AgentExecutionMode.VisualForce,
                AgentExecutionMode.ExplicitAgent -> AgentOrchestratorRoute.VisualLoop
            }
        }

        internal fun evaluateControllerPlannerStep(
            step: CloudAgentStep?,
            installedApps: List<InstalledAppEntry>,
            assistantPackageName: String,
        ): ControllerPlannerSelection {
            if (step == null) {
                return ControllerPlannerSelection(
                    rejectionReason = "GUI Plus 未返回控制器阶段动作；必须基于完整用户指令返回已安装应用的规范 open_app 和结构化任务契约。",
                )
            }

            val stepType = step.type.trim().lowercase()
            if (stepType in CONTROLLER_FORBIDDEN_STEP_TYPES) {
                return ControllerPlannerSelection(
                    rejectionReason = "控制器阶段禁止返回 $stepType；必须由云端直接选择已安装目标应用并返回 open_app。",
                )
            }
            if (stepType != "open_app") {
                return ControllerPlannerSelection(
                    rejectionReason = "控制器阶段只接受 open_app，不接受 $stepType。",
                )
            }

            val declaredContract = AgentTaskExecutionContract.fromPlannerStep(step)
                ?: return ControllerPlannerSelection(
                    rejectionReason = "Planner 缺少结构化任务契约；必须声明 preferredSurface、browserFallbackAllowed、requiredCapabilities 和 requirePostActionVerification。",
                )

            val requestedName = step.appName?.trim().orEmpty()
            val requestedPackage = step.packageName?.trim().orEmpty()
            if (requestedName.isBlank() || requestedPackage.isBlank()) {
                return ControllerPlannerSelection(
                    rejectionReason = "open_app 必须同时返回规范 appName 和 packageName。",
                )
            }
            if (requestedPackage == assistantPackageName) {
                return ControllerPlannerSelection(
                    rejectionReason = "控制器阶段不能再次选择 AI Ledger 自身。",
                )
            }

            val installed = installedApps.firstOrNull { it.packageName == requestedPackage }
                ?: return ControllerPlannerSelection(
                    rejectionReason = "目标包 $requestedPackage 不在真实已安装应用清单。",
                )
            if (installed.label != requestedName) {
                return ControllerPlannerSelection(
                    rejectionReason = "模型返回的应用名与规范清单不一致：$requestedName 不对应 $requestedPackage，规范名称应为 ${installed.label}。",
                )
            }

            return ControllerPlannerSelection(
                contract = declaredContract,
                app = installed,
                step = step.copy(
                    type = "open_app",
                    appName = installed.label,
                    packageName = installed.packageName,
                    reason = step.reason ?: "GUI Plus 已根据完整用户指令选择目标应用，先打开应用再开始视觉导航。",
                ),
            )
        }
    }
}

enum class AgentOrchestratorRoute {
    LegacyRunner,
    VisualLoop,
}

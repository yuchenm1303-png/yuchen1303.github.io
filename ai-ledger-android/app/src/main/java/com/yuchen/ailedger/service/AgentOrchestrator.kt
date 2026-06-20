package com.yuchen.ailedger.service

import android.content.Context
import com.yuchen.ailedger.model.ChatModel
import java.io.IOException
import java.text.Normalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class ControllerHandoffResult(
    val sourcePackage: String,
    val step: CloudAgentStep,
    val execution: AgentExecutionResult,
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

        val explicitResolution = resolveExplicitControllerTarget(
            goal = goal,
            apps = installedApps,
            aliasesForPackage = installedAppIndex::aliasesFor,
            excludedPackages = setOf(applicationContext.packageName),
        )
        explicitResolution.app
            ?.takeIf { explicitResolution.status == ExplicitAppResolutionStatus.Exact }
            ?.let { app ->
                return executeControllerOpenApp(
                    sourcePackage = sourcePackage,
                    step = CloudAgentStep(
                        type = "open_app",
                        appName = app.label,
                        packageName = app.packageName,
                        reason = "用户指令已明确指定安装应用，控制器直接打开后再进入视觉导航。",
                    ),
                )
            }

        val contractRequest = AgentTaskExecutionContract.controllerRequest()
        var lastDeclaredContract: AgentTaskExecutionContract? = null
        val appContext = appCapabilityRegistry.buildVisualContext(installedApps)
            .sortedWith(compareBy<VisualAgentAppContextItem> { it.label.lowercase() }.thenBy { it.packageName })
            .take(MAX_CONTROLLER_APP_CONTEXT_ITEMS)
        val recentActions = mutableListOf(
            contractRequest.toPromptLine(),
            AgentDeviceProfile.current().toPromptLine(),
            appCapabilityRegistry.compactPromptLine(appContext),
            "task_contract_request:v1|plannerMustDeclare=preferredSurface,browserFallbackAllowed,requiredCapabilities,requirePostActionVerification|returnIn=agentStep.arguments",
            "controller_handoff:v2|currentSurface=assistant_controller|mustReturn=open_app|homeNotRequired=true|validateAppCapability=true",
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
                    )
                }.also { AgentRuntimeController.noteModelOutput(it.rawModelOutput) }
                    .step
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                recentActions += "controller_selection_failed:network=${error.message.orEmpty().take(120)}"
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

            return executeControllerOpenApp(sourcePackage, selectedStep)
        }

        val message = buildString {
            append("没有找到符合云端任务契约的目标应用。")
            when (lastDeclaredContract?.preferredSurface) {
                AgentSurfacePreference.SystemSettings -> append(" 请确认系统设置可用，或明确告诉我应进入哪个设置入口。")
                AgentSurfacePreference.NativeApp -> append(" 请明确选择一个具备该操作能力的原生应用。")
                AgentSurfacePreference.Browser -> append(" 请确认设备上存在可用浏览器，或明确指定目标应用。")
                AgentSurfacePreference.Any -> append(" 请在指令中明确应用名称后重试。")
                null -> append(" 云端规划器未返回完整任务契约，已停止在控制器界面继续尝试。")
            }
        }
        return controllerFailure(sourcePackage, message)
    }

    private suspend fun executeControllerOpenApp(
        sourcePackage: String,
        step: CloudAgentStep,
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
        private const val MIN_EXPLICIT_APP_IDENTITY_LENGTH = 2
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
                    rejectionReason = "GUI Plus 未返回控制器阶段动作；必须返回已安装应用的规范 open_app 和结构化任务契约。",
                )
            }

            val stepType = step.type.trim().lowercase()
            if (stepType in CONTROLLER_FORBIDDEN_STEP_TYPES) {
                return ControllerPlannerSelection(
                    rejectionReason = "控制器阶段禁止返回 $stepType；必须直接选择已安装目标应用并返回 open_app。",
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
            if (normalizeAppIdentity(installed.label) != normalizeAppIdentity(requestedName)) {
                return ControllerPlannerSelection(
                    rejectionReason = "模型返回的应用名与包名不一致：$requestedName 不对应 $requestedPackage。",
                )
            }

            return ControllerPlannerSelection(
                contract = declaredContract,
                app = installed,
                step = step.copy(
                    type = "open_app",
                    appName = installed.label,
                    packageName = installed.packageName,
                    reason = step.reason ?: "GUI Plus 已在控制器阶段选择目标应用，先打开应用再开始视觉导航。",
                ),
            )
        }

        internal fun resolveExplicitControllerTarget(
            goal: String,
            apps: List<InstalledAppEntry>,
            aliasesForPackage: (InstalledAppEntry) -> List<String> = { emptyList() },
            excludedPackages: Set<String> = emptySet(),
        ): ExplicitAppResolution {
            val normalizedGoal = normalizeAppIdentity(goal)
            if (normalizedGoal.isBlank()) {
                return ExplicitAppResolution(ExplicitAppResolutionStatus.NotFound)
            }

            val scoredMatches = apps
                .asSequence()
                .filterNot { it.packageName in excludedPackages }
                .mapNotNull { app ->
                    val aliases = runCatching { aliasesForPackage(app) }.getOrDefault(emptyList())
                    val matchedLength = (listOf(app.label) + aliases)
                        .asSequence()
                        .map(::normalizeAppIdentity)
                        .filter { it.length >= MIN_EXPLICIT_APP_IDENTITY_LENGTH }
                        .filter(normalizedGoal::contains)
                        .maxOfOrNull(String::length)
                        ?: return@mapNotNull null
                    app to matchedLength
                }
                .toList()

            val bestLength = scoredMatches.maxOfOrNull { it.second }
                ?: return ExplicitAppResolution(ExplicitAppResolutionStatus.NotFound)
            val candidates = scoredMatches
                .filter { it.second == bestLength }
                .map { it.first }
                .distinctBy { it.packageName }

            return when (candidates.size) {
                1 -> ExplicitAppResolution(
                    status = ExplicitAppResolutionStatus.Exact,
                    app = candidates.first(),
                    candidates = candidates,
                )
                0 -> ExplicitAppResolution(ExplicitAppResolutionStatus.NotFound)
                else -> ExplicitAppResolution(
                    status = ExplicitAppResolutionStatus.Ambiguous,
                    candidates = candidates,
                )
            }
        }

        private fun normalizeAppIdentity(value: String): String {
            return Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFKC)
                .replace(Regex("[^\\p{L}\\p{N}]+"), "")
        }
    }
}

enum class AgentOrchestratorRoute {
    LegacyRunner,
    VisualLoop,
}

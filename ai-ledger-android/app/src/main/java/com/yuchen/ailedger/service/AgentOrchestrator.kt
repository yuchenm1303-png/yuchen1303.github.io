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

        val contract = AgentTaskExecutionContract.fromGoal(goal)
        val appContext = appCapabilityRegistry.buildVisualContext(installedApps)
            .sortedWith(compareBy<VisualAgentAppContextItem> { it.label.lowercase() }.thenBy { it.packageName })
            .take(MAX_CONTROLLER_APP_CONTEXT_ITEMS)
        val recentActions = mutableListOf(
            contract.toPromptLine(),
            AgentDeviceProfile.current().toPromptLine(),
            appCapabilityRegistry.compactPromptLine(appContext),
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

            val selectedStep = prepareControllerOpenApp(modelStep, installedApps)
            if (selectedStep == null) {
                recentActions += "controller_selection_rejected:attempt=${attempt + 1}|reason=GUI Plus 必须返回已安装应用的规范 open_app(appName,packageName)，不能 wait、home 或点击控制器界面。"
                return@repeat
            }

            val selectedApp = installedApps.firstOrNull { it.packageName == selectedStep.packageName }
            if (selectedApp == null) {
                recentActions += "controller_selection_rejected:attempt=${attempt + 1}|reason=目标包不在已安装应用清单。"
                return@repeat
            }
            val validation = appCapabilityRegistry.validateSelection(contract, selectedApp)
            if (!validation.ok) {
                recentActions += "controller_selection_rejected:attempt=${attempt + 1}|app=${selectedApp.label.take(40)}|package=${selectedApp.packageName.take(80)}|reason=${validation.message.take(220)}"
                return@repeat
            }

            return executeControllerOpenApp(sourcePackage, selectedStep)
        }

        val message = buildString {
            append("没有找到符合任务能力契约的目标应用。")
            when (contract.preferredSurface) {
                AgentSurfacePreference.SystemSettings -> append(" 请确认系统设置可用，或明确告诉我应进入哪个设置入口。")
                AgentSurfacePreference.NativeApp -> append(" 请明确选择一个具备该操作能力的原生应用。")
                else -> append(" 请在指令中明确应用名称后重试。")
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

    private fun prepareControllerOpenApp(
        step: CloudAgentStep?,
        installedApps: List<InstalledAppEntry>,
    ): CloudAgentStep? {
        if (step?.type != "open_app") return null
        val requestedName = step.appName?.trim().orEmpty()
        val requestedPackage = step.packageName?.trim().orEmpty()
        if (requestedName.isBlank() || requestedPackage.isBlank()) return null
        if (requestedPackage == applicationContext.packageName) return null

        val installed = installedApps.firstOrNull { it.packageName == requestedPackage } ?: return null
        if (normalizeAppIdentity(installed.label) != normalizeAppIdentity(requestedName)) return null
        return step.copy(
            appName = installed.label,
            packageName = installed.packageName,
            reason = step.reason ?: "GUI Plus 已在控制器阶段选择目标应用，先打开应用再开始视觉导航。",
        )
    }

    companion object {
        private const val CONTROLLER_APP_OPEN_SETTLE_MS = 700L
        private const val MAX_CONTROLLER_APP_CONTEXT_ITEMS = 160
        private const val MAX_CONTROLLER_SELECTION_ATTEMPTS = 2
        private const val MIN_EXPLICIT_APP_IDENTITY_LENGTH = 2

        fun routeFor(executionMode: AgentExecutionMode): AgentOrchestratorRoute {
            return when (executionMode) {
                AgentExecutionMode.NormalChatDeviceTool -> AgentOrchestratorRoute.LegacyRunner
                AgentExecutionMode.VisualForce,
                AgentExecutionMode.ExplicitAgent -> AgentOrchestratorRoute.VisualLoop
            }
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

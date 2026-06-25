package com.yuchen.ailedger.service

import android.content.Context

internal class VisualTaskExecutor(
    internal val aiWorkerClient: AiWorkerClient,
    appContext: Context,
) {
    internal val applicationContext = appContext.applicationContext
    internal val installedAppIndex = InstalledAppIndex(applicationContext)
    internal val deviceToolExecutor = DeviceToolExecutor(applicationContext, installedAppIndex)
    internal val observationCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        VisualObservationCoordinator(
            captureSource = AccessibilityVisualObservationCaptureSource,
            foregroundPackageReader = ForegroundPackageProbe(DeviceShellBridge(applicationContext)),
        )
    }
    internal val clientDeviceId by lazy { AgentClientIdentity.getOrCreateDeviceId(applicationContext) }

    suspend fun run(
        goal: String,
        maxSteps: Int,
        executionMode: AgentExecutionMode,
    ): AgentTaskRunResult = runVisualTask(goal, maxSteps, executionMode)
}

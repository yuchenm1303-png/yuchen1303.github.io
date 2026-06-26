package com.yuchen.ailedger.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns only the interactive control overlay lifecycle.
 *
 * Manual visibility is independent from the visual HUD. A pending GUI Plus user-input request may
 * temporarily open the control overlay once, without changing the user's manual switch preference.
 */
internal object AgentOverlayLaunchPolicy {
    private val lock = Any()

    private val mutableManualEnabled = MutableStateFlow(false)
    val manualEnabled: StateFlow<Boolean> = mutableManualEnabled.asStateFlow()

    @Volatile
    private var pendingManualEnableAfterPermission: Boolean = false

    private var activeHelpRequestId: Long = 0L
    private var lastObservedHelpRequestId: Long = 0L
    private var dismissedHelpRequestId: Long = 0L

    fun isManualEnabled(): Boolean = mutableManualEnabled.value

    fun canDrawOverlays(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun isServiceAllowed(progress: AgentOverlayProgress = AgentRuntimeController.progress.value): Boolean {
        if (isManualEnabled()) return true
        val pendingId = progress.pendingUserInput?.id ?: return false
        return synchronized(lock) {
            activeHelpRequestId == pendingId && dismissedHelpRequestId != pendingId
        }
    }

    fun enableManually(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!canDrawOverlays(appContext)) {
            pendingManualEnableAfterPermission = true
            return false
        }
        pendingManualEnableAfterPermission = false
        mutableManualEnabled.value = true
        startService(appContext)
        return true
    }

    fun disableManually(context: Context) {
        val appContext = context.applicationContext
        mutableManualEnabled.value = false
        pendingManualEnableAfterPermission = false
        synchronized(lock) {
            val currentHelpId = AgentRuntimeController.progress.value.pendingUserInput?.id ?: 0L
            if (currentHelpId > 0L) dismissedHelpRequestId = currentHelpId
            activeHelpRequestId = 0L
        }
        stopService(appContext)
    }

    fun restoreManualEnableAfterPermission(context: Context): Boolean {
        if (!pendingManualEnableAfterPermission || !canDrawOverlays(context)) return false
        return enableManually(context)
    }

    fun syncForProgress(context: Context, progress: AgentOverlayProgress) {
        val appContext = context.applicationContext
        val pendingId = progress.pendingUserInput?.id ?: 0L
        var shouldStart = false
        var shouldStop = false

        synchronized(lock) {
            if (pendingId > 0L) {
                if (pendingId != lastObservedHelpRequestId) {
                    lastObservedHelpRequestId = pendingId
                    if (!isManualEnabled() && dismissedHelpRequestId != pendingId) {
                        activeHelpRequestId = pendingId
                    }
                }
                // Starting is idempotent. Rechecking the same active request lets the overlay appear
                // after the user returns from Android's permission page, while a manually dismissed
                // request remains suppressed until GUI Plus creates a new request id.
                shouldStart = !isManualEnabled() &&
                    activeHelpRequestId == pendingId &&
                    dismissedHelpRequestId != pendingId
            } else {
                lastObservedHelpRequestId = 0L
                dismissedHelpRequestId = 0L
                if (activeHelpRequestId != 0L) {
                    activeHelpRequestId = 0L
                    shouldStop = !isManualEnabled()
                }
            }
        }

        when {
            shouldStart && canDrawOverlays(appContext) -> startService(appContext)
            shouldStop -> stopService(appContext)
        }
    }

    private fun startService(context: Context) {
        runCatching {
            context.startService(Intent(context, AgentOverlayService::class.java))
        }
    }

    private fun stopService(context: Context) {
        context.stopService(Intent(context, AgentOverlayService::class.java))
    }
}

package com.yuchen.ailedger.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns only the optional legacy control-overlay lifecycle.
 *
 * GUI Plus confirmation, user input and takeover dialogue now live in the accessibility-overlay
 * dynamic capsule. The ordinary application overlay is manual-only and is no longer required for
 * the agent to communicate with the user.
 */
internal object AgentOverlayLaunchPolicy {
    private val mutableManualEnabled = MutableStateFlow(false)
    val manualEnabled: StateFlow<Boolean> = mutableManualEnabled.asStateFlow()

    private val serviceRequested = AtomicBoolean(false)

    @Volatile
    private var pendingManualEnableAfterPermission: Boolean = false

    fun isManualEnabled(): Boolean = mutableManualEnabled.value

    fun canDrawOverlays(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun isServiceAllowed(
        @Suppress("UNUSED_PARAMETER")
        progress: AgentOverlayProgress = AgentRuntimeController.progress.value,
    ): Boolean = isManualEnabled()

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
        stopService(appContext)
    }

    fun restoreManualEnableAfterPermission(context: Context): Boolean {
        if (!pendingManualEnableAfterPermission || !canDrawOverlays(context)) return false
        return enableManually(context)
    }

    fun syncForProgress(
        context: Context,
        @Suppress("UNUSED_PARAMETER")
        progress: AgentOverlayProgress,
    ) {
        val appContext = context.applicationContext
        if (isManualEnabled() && canDrawOverlays(appContext)) {
            startService(appContext)
        } else if (serviceRequested.get()) {
            // 只有本进程确实请求过服务时才停止。默认空闲冷启动不会再产生无意义的
            // Binder/ServiceManager 往返，同时权限被撤销时仍能正确清理已启动服务。
            stopService(appContext)
        }
    }

    private fun startService(context: Context) {
        runCatching {
            context.startService(Intent(context, AgentOverlayService::class.java))
        }.onSuccess {
            serviceRequested.set(true)
        }
    }

    private fun stopService(context: Context) {
        serviceRequested.set(false)
        runCatching {
            context.stopService(Intent(context, AgentOverlayService::class.java))
        }
    }
}

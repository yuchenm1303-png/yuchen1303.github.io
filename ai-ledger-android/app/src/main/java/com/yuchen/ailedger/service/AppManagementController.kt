package com.yuchen.ailedger.service

import android.content.Context
import org.json.JSONObject

enum class ManagedAppActionTone {
    Normal,
    Warning,
    Critical,
}

enum class ManagedAppAction(
    val title: String,
    val description: String,
    val stepType: String,
    val settingsPage: String? = null,
    val tone: ManagedAppActionTone = ManagedAppActionTone.Normal,
) {
    Open(
        title = "打开应用",
        description = "启动应用的桌面入口",
        stepType = "open_app",
    ),
    ManageStorage(
        title = "管理存储与缓存",
        description = "进入系统应用信息页，安全管理缓存和空间",
        stepType = "open_app_settings",
        settingsPage = "details",
    ),
    NotificationSettings(
        title = "通知设置",
        description = "管理通知权限、类别和提示方式",
        stepType = "open_app_settings",
        settingsPage = "notification",
    ),
    PermissionSettings(
        title = "权限设置",
        description = "查看并调整相机、位置等权限",
        stepType = "open_app_settings",
        settingsPage = "permission",
    ),
    BatterySettings(
        title = "电池与后台",
        description = "进入系统电池和后台运行管理入口",
        stepType = "open_app_settings",
        settingsPage = "battery",
    ),
    ForceStop(
        title = "强制停止",
        description = "终止应用当前进程，重新打开后可恢复",
        stepType = "force_stop_app",
        tone = ManagedAppActionTone.Warning,
    ),
    ClearData(
        title = "清除全部数据",
        description = "删除登录状态、设置和本地数据，不等同于清缓存",
        stepType = "clear_app_data",
        tone = ManagedAppActionTone.Critical,
    ),
    Disable(
        title = "禁用应用",
        description = "阻止应用运行并从启动入口隐藏",
        stepType = "disable_app",
        tone = ManagedAppActionTone.Critical,
    ),
    Enable(
        title = "启用应用",
        description = "恢复已禁用应用",
        stepType = "enable_app",
        tone = ManagedAppActionTone.Warning,
    ),
    Uninstall(
        title = "卸载当前用户应用",
        description = "从当前用户中移除应用",
        stepType = "uninstall_app",
        tone = ManagedAppActionTone.Critical,
    );

    val requiresConfirmation: Boolean
        get() = DeviceControlSpecs.specFor(stepType)?.requiresConfirmation == true
}

data class ManagedAppActionAvailability(
    val enabled: Boolean,
    val reason: String = "",
)

internal object AppManagementActionPolicy {
    fun availability(action: ManagedAppAction, app: ManagedAppSummary): ManagedAppActionAvailability {
        if (action == ManagedAppAction.Open && !app.isLaunchable) {
            return ManagedAppActionAvailability(false, "该应用没有桌面启动入口")
        }
        if (action == ManagedAppAction.Enable) {
            return if (app.isEnabled) {
                ManagedAppActionAvailability(false, "应用当前已经启用")
            } else {
                ManagedAppActionAvailability(true)
            }
        }
        if (action == ManagedAppAction.Disable && !app.isEnabled) {
            return ManagedAppActionAvailability(false, "应用当前已经禁用")
        }
        if (action in protectedRestrictedActions && app.isProtected) {
            return ManagedAppActionAvailability(false, app.protectionReason.ifBlank { "系统关键应用受到保护" })
        }
        if (action in systemRestrictedActions && app.isSystemApp) {
            return ManagedAppActionAvailability(false, "系统应用不允许执行这个不可逆操作")
        }
        return ManagedAppActionAvailability(true)
    }

    private val protectedRestrictedActions = setOf(
        ManagedAppAction.ForceStop,
        ManagedAppAction.ClearData,
        ManagedAppAction.Disable,
        ManagedAppAction.Uninstall,
    )
    private val systemRestrictedActions = setOf(
        ManagedAppAction.ClearData,
        ManagedAppAction.Uninstall,
    )
}

internal object AppManagementActionFactory {
    fun create(action: ManagedAppAction, app: ManagedAppSummary): CloudAgentStep {
        val args = JSONObject().put("packageName", app.packageName)
        action.settingsPage?.let { args.put("page", it) }
        return CloudAgentStep(
            type = action.stepType,
            appName = app.label,
            packageName = app.packageName,
            reason = "应用控制详情页：${action.title}",
            riskLevel = DeviceControlSpecs.riskFor(action.stepType),
            requiresConfirmation = action.requiresConfirmation,
            toolArgs = args,
            reversible = DeviceControlSpecs.specFor(action.stepType)?.reversible ?: true,
            legacyIntent = false,
        )
    }
}

class AppManagementController(context: Context) {
    private val appContext = context.applicationContext
    private val executor = DeviceToolExecutor(appContext)
    private val verifier = DeviceControlActionVerifier(appContext)
    private val shellBridge = DeviceShellBridge(appContext)

    fun shellStatus(forceRefresh: Boolean = false): DeviceShellStatus = shellBridge.probe(forceRefresh)

    fun requestShizukuPermission(): AgentExecutionResult {
        val step = CloudAgentStep(
            type = "request_shizuku_permission",
            reason = "应用控制页请求增强控制授权",
            riskLevel = DeviceControlSpecs.riskFor("request_shizuku_permission"),
            toolArgs = JSONObject(),
            legacyIntent = false,
        )
        val validation = DeviceControlSpecs.validate(step)
        if (!validation.ok) {
            return AgentExecutionResult(
                ok = false,
                message = "Shizuku 授权动作校验失败：${validation.reason}",
                shouldContinue = false,
                diagnostics = "app_management_shizuku_validation_failed",
            )
        }
        return executor.execute(step, confirmedHighRisk = false)
    }

    fun execute(
        action: ManagedAppAction,
        app: ManagedAppSummary,
        confirmed: Boolean,
    ): AgentExecutionResult {
        val availability = AppManagementActionPolicy.availability(action, app)
        if (!availability.enabled) {
            return AgentExecutionResult(
                ok = false,
                message = "已阻止“${action.title}”：${availability.reason}",
                shouldContinue = false,
                diagnostics = "app_management_policy_blocked:${action.stepType}",
            )
        }
        val step = AppManagementActionFactory.create(action, app)
        val validation = DeviceControlSpecs.validate(step)
        if (!validation.ok) {
            return AgentExecutionResult(
                ok = false,
                message = "应用控制参数校验失败：${validation.reason}",
                shouldContinue = false,
                diagnostics = "app_management_validation_failed:${validation.reason}",
            )
        }
        if (action.requiresConfirmation && !confirmed) {
            return AgentExecutionResult(
                ok = false,
                message = "“${action.title}”需要用户明确确认后才能执行。",
                shouldContinue = false,
                diagnostics = "app_management_waiting_confirmation:${action.stepType}",
            )
        }
        val raw = executor.execute(step, confirmedHighRisk = confirmed)
        return verifier.verify(step, raw)
    }
}

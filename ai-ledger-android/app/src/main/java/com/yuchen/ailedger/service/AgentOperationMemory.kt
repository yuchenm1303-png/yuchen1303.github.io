package com.yuchen.ailedger.service

import com.yuchen.ailedger.data.AssistantLocalMemorySnapshot
import org.json.JSONArray
import org.json.JSONObject

/**
 * Supplies execution safeguards and user-owned memory to the legacy internal-tool client.
 * Task semantics, target apps and routes are owned by the cloud planner and are never inferred
 * from local goal keywords here.
 */
object AgentOperationMemory {
    fun build(
        goal: String,
        currentPackage: String,
        localMemory: AssistantLocalMemorySnapshot,
    ): JSONObject {
        return JSONObject().apply {
            put("schema", "android_agent_operation_memory_v2_cloud_semantics")
            put("semanticOwner", "cloud")
            put("localKeywordClassificationEnabled", false)
            put("goalPresent", goal.isNotBlank())
            put("hostAppGuard", buildHostAppGuard(currentPackage))
            put("localUserMemory", localMemory.toJson())
            put("executionRules", buildExecutionRules())
            put("failureAvoidance", buildFailureAvoidanceRules())
            put("safetyMemory", buildSafetyMemory())
        }
    }

    private fun buildHostAppGuard(currentPackage: String): JSONObject {
        val isHostApp = currentPackage == HOST_PACKAGE || currentPackage.endsWith(".ailedger")
        return JSONObject().apply {
            put("currentPackage", currentPackage)
            put("isHostApp", isHostApp)
            put("rules", JSONArray().apply {
                if (isHostApp) {
                    put("当前页面是 AI 助手宿主 App；只有云端明确选择本 App 时才可在此执行页面动作。")
                    put("需要跨应用或进入系统界面时，必须使用云端返回的规范动作和真实包名，不得由本地关键词猜测目标。")
                }
            })
        }
    }

    private fun buildExecutionRules(): JSONArray = JSONArray().apply {
        put("任务语义、目标应用与页面路线由云端规划器决定；Android 不执行本地关键词分类。")
        put("仅执行 CloudAgentStep 支持且通过 Android 安全策略校验的动作。")
        put("可由内部设备工具完成的动作应使用对应工具，并在执行后读取系统状态验证结果。")
        put("需要完整跨应用视觉操作时应交由 VisualLoopRunner，不在旧内部工具链中建立第二套目标应用解析。")
        put("云端返回的 appName 与 packageName 必须对应设备真实可启动应用，不得编造包名。")
    }

    private fun buildFailureAvoidanceRules(): JSONArray = JSONArray().apply {
        put("同一页面连续执行相同动作且主要内容没有变化时，应判定该路线无进展并更换策略。")
        put("本地执行失败或被安全策略拦截后，不得无条件重复相同动作。")
        put("无法确认目标或需要用户输入时，应返回 need_user_help，不得由本地逻辑补猜任务语义。")
    }

    private fun buildSafetyMemory(): JSONArray = JSONArray().apply {
        put("密码、验证码、账号安全验证、支付确认、交易提交和权限授权必须暂停交给用户。")
        put("可以打开系统页面，但不要替用户开启高风险开关。")
        put("任何真实金融交易的提交或确认阶段都必须停止自动执行。")
    }

    private const val HOST_PACKAGE = "com.yuchen.ailedger"
}

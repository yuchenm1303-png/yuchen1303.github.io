package com.yuchen.ailedger.service

import com.yuchen.ailedger.data.AssistantLocalMemorySnapshot
import org.json.JSONArray
import org.json.JSONObject

object AgentOperationMemory {
    fun build(
        goal: String,
        currentPackage: String,
        localMemory: AssistantLocalMemorySnapshot,
    ): JSONObject {
        val taskType = classifyTask(goal)
        return JSONObject().apply {
            put("schema", "android_agent_operation_memory_v1")
            put("taskType", taskType)
            put("hostAppGuard", buildHostAppGuard(currentPackage))
            put("localUserMemory", localMemory.toJson())
            put("routeSkills", buildRouteSkills(taskType))
            put("failureAvoidance", buildFailureAvoidanceRules())
            put("safetyMemory", buildSafetyMemory())
        }
    }

    private fun classifyTask(goal: String): String {
        val text = goal.lowercase()
        return when {
            hasAny(text, "开发人员选项", "开发者选项", "developer options", "development settings") -> "android_settings.developer_options"
            hasAny(text, "无障碍", "辅助功能", "accessibility") -> "android_settings.accessibility"
            hasAny(text, "悬浮窗", "显示在其他应用", "overlay", "draw over") -> "android_settings.overlay_permission"
            hasAny(text, "电池优化", "省电", "battery optimization", "后台限制") -> "android_settings.battery_optimization"
            hasAny(text, "通知权限", "通知设置", "notification") -> "android_settings.notification_permission"
            hasAny(text, "应用详情", "应用信息", "权限管理", "app info") -> "android_settings.app_details"
            hasAny(text, "设置", "系统设置", "settings") -> "android_settings.generic"
            hasAny(text, "导航", "去", "回家", "学校", "公司", "宿舍", "地图") -> "navigation"
            else -> "general_gui_task"
        }
    }

    private fun buildHostAppGuard(currentPackage: String): JSONObject {
        val isHostApp = currentPackage == HOST_PACKAGE || currentPackage.endsWith(".ailedger")
        return JSONObject().apply {
            put("currentPackage", currentPackage)
            put("isHostApp", isHostApp)
            put("rules", JSONArray().apply {
                if (isHostApp) {
                    put("当前页面是 AI 助手宿主 App，不是大多数系统设置/第三方 App 操作的目标页面。")
                    put("除非用户明确要求操作本 App，否则不要点击宿主 App 底部导航里的 AI助手、功能、设置。")
                    put("如果目标是 Android 系统设置、开发者选项、无障碍、权限或打开其他 App，应先使用 open_app/internal settings route 离开宿主 App。")
                }
            })
        }
    }

    private fun buildRouteSkills(taskType: String): JSONArray = JSONArray().apply {
        if (taskType.startsWith("android_settings")) {
            put(JSONObject().apply {
                put("name", "android_settings_route_first")
                put("priority", 1)
                put("description", "系统设置类任务不要先在当前 App 内视觉乱点，应优先使用系统 Intent/内部设置路由；失败后再打开设置 App 搜索目标页面。")
            })
        }
        when (taskType) {
            "android_settings.developer_options" -> put(JSONObject().apply {
                put("name", "open_developer_options")
                put("priority", 0)
                put("steps", JSONArray().apply {
                    put("优先尝试 Android 原生开发者选项设置页 Intent：android.settings.APPLICATION_DEVELOPMENT_SETTINGS。")
                    put("如果直接打开失败，打开系统设置并搜索“开发者选项/开发人员选项”。")
                    put("如果开发者选项尚未启用，进入关于手机，找到版本号/Build number/系统版本，连续点击约 7 次。")
                    put("遇到锁屏密码、账号验证、图案、指纹或系统安全确认时，返回 need_user_help 或进入接管暂停。")
                    put("打开开发者选项页面后停止，不要自动开启 USB 调试、无线调试、OEM 解锁等高风险开关。")
                })
            })
            "android_settings.accessibility" -> put(JSONObject().apply {
                put("name", "open_accessibility_settings")
                put("priority", 0)
                put("steps", JSONArray().apply {
                    put("优先打开 Android 无障碍设置页 Intent：android.settings.ACCESSIBILITY_SETTINGS。")
                    put("需要打开具体服务时只导航到服务项，系统授权弹窗必须交给用户确认。")
                })
            })
            "android_settings.overlay_permission" -> put(JSONObject().apply {
                put("name", "open_overlay_permission")
                put("priority", 0)
                put("steps", JSONArray().apply {
                    put("优先打开显示在其他应用上层/悬浮窗权限页面。")
                    put("授权开关属于权限变更，最终确认应暂停交给用户。")
                })
            })
            "navigation" -> put(JSONObject().apply {
                put("name", "use_saved_navigation_memory")
                put("priority", 0)
                put("steps", JSONArray().apply {
                    put("如果用户说回家、去学校、去公司、回宿舍，先查询 localUserMemory.navigation 中已保存地址。")
                    put("若对应地址为空，先询问用户或返回 need_user_help，不要凭空猜地址。")
                })
            })
        }
    }

    private fun buildFailureAvoidanceRules(): JSONArray = JSONArray().apply {
        put("如果同一页面连续点击相同坐标/相同文字后 currentApp、主要文字和目标进展没有变化，应判定为失败路线，下一步必须换路径。")
        put("系统设置类任务如果仍停留在宿主 App，说明当前路线无进展；不要重复点击宿主 App 底部 Tab。")
        put("如果目标页面搜索不到，不要无限重复搜索；应切换到上一级分类路径或请求用户协助。")
        put("如果本地执行返回失败或被安全策略拦截，下一轮必须避开相同动作。")
    }

    private fun buildSafetyMemory(): JSONArray = JSONArray().apply {
        put("密码、验证码、账号安全验证、支付确认、交易提交、权限授权、USB 调试、无线调试、OEM 解锁都必须暂停交给用户。")
        put("可以打开设置页面，但不要替用户开启高风险开关。")
        put("如果任务目标包含金融交易/下单/委托，进入提交或确认阶段必须停止自动执行。")
    }

    private fun hasAny(text: String, vararg keywords: String): Boolean = keywords.any { text.contains(it.lowercase()) }

    private const val HOST_PACKAGE = "com.yuchen.ailedger"
}

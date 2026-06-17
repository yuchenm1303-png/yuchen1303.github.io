package com.yuchen.ailedger.service

object AgentSafetyPolicy {
    private val deviceToolStepTypes = CloudAgentStep.deviceToolTypes

    private val executableStepTypes = setOf(
        "open_app",
        "home",
        "back",
        "recents",
        "tap_node",
        "tap_xy",
        "input_text",
        "scroll",
        "swipe",
        "wait",
    ) + deviceToolStepTypes

    private val passiveStepTypes = setOf(
        "back",
        "home",
        "recents",
        "notifications",
        "quick_settings",
        "scroll",
        "swipe",
        "wait",
        "device_status",
        "shizuku_status",
    )

    private val foregroundStepTypes = setOf(
        "tap_node",
        "tap_xy",
        "input_text",
    )

    private val localHighRiskDeviceTools = setOf(
        "set_animation_scale",
        "force_stop_app",
    )

    private val localCriticalDeviceTools = setOf(
        "clear_app_data",
        "uninstall_app",
        "disable_app",
        "enable_app",
    )

    private val consequentialActionKeywords = listOf(
        "确认支付",
        "立即支付",
        "付款",
        "转账",
        "确认购买",
        "立即购买",
        "提交订单",
        "确认订单",
        "确认下单",
        "提交委托",
        "确认买入",
        "确认卖出",
        "发送消息",
        "发送邮件",
        "确认发送",
        "发布内容",
        "确认发布",
        "永久删除",
        "确认删除",
        "清空数据",
        "注销账号",
        "删除账号",
        "授权访问",
        "允许访问",
        "确认授权",
        "确认提交",
        "确认安装",
        "确认卸载",
        "恢复出厂",
    )

    private val sensitiveInputKeywords = listOf(
        "登录密码",
        "支付密码",
        "银行卡密码",
        "短信验证码",
        "验证码",
        "动态口令",
        "二次验证",
        "身份证号",
        "银行卡号",
        "cvv",
        "安全码",
        "私钥",
        "助记词",
    )

    fun requiresConfirmation(goal: String, step: CloudAgentStep): Boolean {
        if (requiresUserProvidedInput(goal, step)) return false
        if (step.type in localHighRiskDeviceTools || step.type in localCriticalDeviceTools) return true

        val level = step.riskLevel.normalizedPolicyLevel()
        val highRisk = level == "high" || level == "critical"
        if (step.requiresConfirmation || highRisk) return true

        // 不完全信任云端自报的风险级别。对于可能产生真实外部后果的前台点击，
        // Android 端再做一次确定性语义兜底，避免低风险误标直接执行。
        if (step.type in foregroundStepTypes && hasConsequentialActionIntent(goal, step)) return true

        if (step.type in passiveStepTypes) return false
        if (step.type in deviceToolStepTypes) return false
        if (step.type !in foregroundStepTypes) return false
        return level.isNotBlank() && level != "low"
    }

    fun canAutoExecuteInCurrentStage(goal: String, step: CloudAgentStep): Boolean {
        if (requiresConfirmation(goal, step)) return false
        if (requiresUserProvidedInput(goal, step)) return false
        return step.type in executableStepTypes
    }

    fun requiresUserProvidedInput(goal: String, step: CloudAgentStep): Boolean {
        val level = step.riskLevel.normalizedPolicyLevel()
        if (step.type == "need_user_help" || level.endsWith("_input")) return true
        if (step.type != "input_text") return false

        val context = listOfNotNull(
            goal,
            step.targetText,
            step.reason,
            step.appName,
            step.argString("field", "label", "hint", "target"),
        ).joinToString(" ").lowercase()
        return sensitiveInputKeywords.any { keyword -> context.contains(keyword.lowercase()) }
    }

    private fun hasConsequentialActionIntent(goal: String, step: CloudAgentStep): Boolean {
        val context = listOfNotNull(
            goal,
            step.targetText,
            step.reason,
            step.appName,
            step.argString("title", "label", "target", "action", "description"),
        ).joinToString(" ").lowercase()
        return consequentialActionKeywords.any { keyword -> context.contains(keyword.lowercase()) }
    }

    private fun String?.normalizedPolicyLevel(): String {
        return orEmpty()
            .trim()
            .lowercase()
            .replace('-', '_')
            .replace(' ', '_')
    }
}

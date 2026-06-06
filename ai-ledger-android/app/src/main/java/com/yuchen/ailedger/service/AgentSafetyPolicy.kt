package com.yuchen.ailedger.service

object AgentSafetyPolicy {
    private val highRiskWords = listOf(
        "支付", "付款", "转账", "红包", "下单", "购买", "删除", "卸载", "授权", "同意",
        "发送", "发给", "提交", "发布", "评论", "私信", "验证码", "密码", "登录", "确认付款",
        "绑定", "实名", "银行卡", "扣款", "充值", "提现", "评价", "拉黑", "举报",
    )

    private val executableLowRiskTypes = setOf(
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
    )

    private val passiveRecoveryTypes = setOf(
        "back",
        "home",
        "recents",
        "notifications",
        "quick_settings",
        "scroll",
        "swipe",
        "wait",
    )

    private val activeTouchOrInputTypes = setOf(
        "tap_node",
        "tap_xy",
        "input_text",
    )

    fun requiresConfirmation(goal: String, step: CloudAgentStep): Boolean {
        if (step.type in passiveRecoveryTypes && step.requiresConfirmation.not()) return false
        if (isLowRiskAppLaunchConfirmation(step)) return false
        if (step.requiresConfirmation) return true
        if (step.riskLevel !in setOf("", "low")) return step.type in activeTouchOrInputTypes
        if (step.type !in executableLowRiskTypes && step.type != "finish") return true
        if (step.type !in activeTouchOrInputTypes) return false
        val joined = listOf(goal, step.targetText, step.text, step.reason, step.appName).joinToString(" ")
        return highRiskWords.any { joined.contains(it, ignoreCase = true) }
    }

    fun canAutoExecuteInCurrentStage(goal: String, step: CloudAgentStep): Boolean {
        if (requiresConfirmation(goal, step)) return false
        return step.type in executableLowRiskTypes
    }

    private fun isLowRiskAppLaunchConfirmation(step: CloudAgentStep): Boolean {
        if (step.type !in setOf("tap_node", "tap_xy")) return false
        val joined = listOf(step.targetText, step.reason, step.appName).joinToString(" ").lowercase()
        val isOpenAppDialog = listOf(
            "是否允许打开",
            "允许打开",
            "继续打开",
            "打开应用",
            "启动应用",
            "系统打开确认",
            "系统启动确认",
            "跳转确认",
            "外部应用",
        ).any { joined.contains(it.lowercase()) }
        val isDangerous = listOf(
            "支付",
            "付款",
            "转账",
            "下单",
            "购买",
            "删除",
            "授权登录",
            "验证码",
            "密码",
        ).any { joined.contains(it.lowercase()) }
        return isOpenAppDialog && !isDangerous
    }
}

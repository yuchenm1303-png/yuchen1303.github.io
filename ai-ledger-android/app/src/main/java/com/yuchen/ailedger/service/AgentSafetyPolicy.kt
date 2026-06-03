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

    fun requiresConfirmation(goal: String, step: CloudAgentStep): Boolean {
        if (step.requiresConfirmation) return true
        if (step.riskLevel !in setOf("", "low")) return true
        if (step.type !in executableLowRiskTypes && step.type != "finish") return true
        val joined = listOf(goal, step.targetText, step.text, step.reason, step.appName).joinToString(" ")
        return highRiskWords.any { joined.contains(it, ignoreCase = true) }
    }

    fun canAutoExecuteInCurrentStage(goal: String, step: CloudAgentStep): Boolean {
        if (requiresConfirmation(goal, step)) return false
        return step.type in executableLowRiskTypes
    }
}

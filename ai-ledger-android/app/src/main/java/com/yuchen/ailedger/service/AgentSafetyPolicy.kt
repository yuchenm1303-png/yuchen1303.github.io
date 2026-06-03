package com.yuchen.ailedger.service

object AgentSafetyPolicy {
    private val highRiskTypes = setOf("finish")
    private val highRiskWords = listOf(
        "支付", "付款", "转账", "红包", "下单", "购买", "删除", "卸载", "授权", "同意",
        "发送", "发给", "提交", "发布", "评论", "私信", "验证码", "密码", "登录",
    )

    fun requiresConfirmation(goal: String, step: CloudAgentStep): Boolean {
        if (step.requiresConfirmation) return true
        if (step.riskLevel !in setOf("", "low")) return true
        if (step.type in highRiskTypes) return false
        val joined = listOf(goal, step.targetText, step.text, step.reason).joinToString(" ")
        return highRiskWords.any { joined.contains(it, ignoreCase = true) }
    }

    fun canAutoExecuteInCurrentStage(step: CloudAgentStep): Boolean {
        return false
    }
}

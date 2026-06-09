package com.yuchen.ailedger.service

object AgentSafetyPolicy {
    private val destructiveOrSensitiveWords = listOf(
        "支付", "付款", "转账", "红包", "下单", "购买", "确认付款", "扣款", "充值", "提现",
        "删除", "卸载", "清空", "注销", "退出登录",
        "授权", "同意授权", "实名", "绑定", "银行卡",
        "发送消息", "发消息", "发给", "发送短信", "确认发送",
        "发表评论", "发布评论", "提交评论", "回复评论", "发布帖子", "提交表单", "私信",
        "拨打", "呼叫", "拉黑", "举报",
        "pay", "transfer", "purchase", "delete", "uninstall", "authorize", "submit", "publish", "send message", "post comment", "call"
    )

    private val sensitiveUserInputWords = listOf(
        "验证码", "校验码", "短信码", "动态码", "密码", "口令", "支付密码", "登录密码", "二次验证",
        "otp", "verification code", "password", "passcode", "pin"
    )

    private val navigationOnlyWords = listOf(
        "打开", "找到", "查找", "寻找", "查看", "进入", "搜索", "跳到", "定位到", "浏览",
        "聊天框", "详情页", "资料页", "主页", "页面", "列表", "结果页",
        "评论区", "评论社区", "社区", "股吧", "讨论区", "评论页", "看评论", "浏览评论",
        "open", "find", "search", "view", "show", "go to", "details", "profile", "community", "comment section"
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
        if (step.type in passiveRecoveryTypes && !step.requiresConfirmation) return false
        if (isLowRiskAppLaunchConfirmation(step)) return false
        if (step.type !in activeTouchOrInputTypes) return false

        val joined = joinedText(goal, step)
        if (requiresUserProvidedInput(goal, step)) return false
        if (isNavigationOnly(joined)) return false
        if (hasDestructiveOrSensitiveIntent(joined)) return true

        val modelFlagged = step.requiresConfirmation || step.riskLevel !in setOf("", "low")
        return modelFlagged && hasDestructiveOrSensitiveIntent(joined)
    }

    fun canAutoExecuteInCurrentStage(goal: String, step: CloudAgentStep): Boolean {
        if (requiresConfirmation(goal, step)) return false
        if (requiresUserProvidedInput(goal, step)) return false
        return step.type in executableLowRiskTypes
    }

    fun requiresUserProvidedInput(goal: String, step: CloudAgentStep): Boolean {
        if (step.type != "input_text" && step.type != "need_user_help") return false
        val joined = joinedText(goal, step)
        return sensitiveUserInputWords.any { joined.contains(it, ignoreCase = true) }
    }

    private fun joinedText(goal: String, step: CloudAgentStep): String {
        return listOf(goal, step.targetText, step.text, step.reason, step.appName)
            .joinToString(" ")
            .trim()
    }

    private fun hasDestructiveOrSensitiveIntent(text: String): Boolean {
        return destructiveOrSensitiveWords.any { text.contains(it, ignoreCase = true) }
    }

    private fun isNavigationOnly(text: String): Boolean {
        val hasNavigation = navigationOnlyWords.any { text.contains(it, ignoreCase = true) }
        return hasNavigation && !hasDestructiveOrSensitiveIntent(text)
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

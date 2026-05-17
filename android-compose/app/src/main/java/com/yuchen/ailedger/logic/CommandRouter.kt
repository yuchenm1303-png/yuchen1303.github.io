package com.yuchen.ailedger.logic

import com.yuchen.ailedger.model.AssistantCommand
import com.yuchen.ailedger.model.CommandType
import com.yuchen.ailedger.model.LedgerDraft

object CommandRouter {
    fun route(text: String): AssistantResult {
        val clean = text.trim()
        if (clean.isBlank()) return AssistantResult.chat("你可以直接说：明天八点叫我起床、导航回家、打开微信、今天午饭28。")

        parseAlarm(clean)?.let { return it }
        parseNavigation(clean)?.let { return it }
        parseOpenApp(clean)?.let { return it }
        parseLedger(clean)?.let { return it }

        return AssistantResult.chat(
            reply = "收到。现在这条消息已经走 Kotlin 原生命令路由，后面会继续接云端 AI。",
            source = "compose_kotlin_router"
        )
    }

    private fun parseAlarm(text: String): AssistantResult? {
        if (!Regex("闹钟|提醒|叫我|起床").containsMatchIn(text)) return null
        val hour = Regex("(早上|上午|明天早上|明早)?\\s*(\\d{1,2})\\s*[点:：时]").find(text)?.groupValues?.getOrNull(2)?.toIntOrNull()
        val minute = Regex("[点:：时]\\s*(\\d{1,2})\\s*分?").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val safeHour = hour?.coerceIn(0, 23) ?: 8
        val safeMinute = minute.coerceIn(0, 59)
        val label = text.replace(Regex("明天|明早|早上|上午|晚上|闹钟|提醒|叫我|起床|\\d{1,2}[点:：时](\\d{1,2}分?)?"), "").trim().ifBlank { "AI 助手提醒" }
        val command = AssistantCommand(
            type = CommandType.SetAlarm,
            title = "设置闹钟",
            description = "时间：%02d:%02d\n备注：%s".format(safeHour, safeMinute, label),
            primaryActionLabel = "打开系统闹钟",
            payload = mapOf(
                "hour" to safeHour.toString(),
                "minute" to safeMinute.toString(),
                "label" to label
            )
        )
        return AssistantResult(
            reply = "我识别到了闹钟任务，确认后会交给系统闹钟处理。",
            source = "local_alarm_router",
            command = command
        )
    }

    private fun parseNavigation(text: String): AssistantResult? {
        if (!Regex("导航|路线|去|回家").containsMatchIn(text)) return null
        val destination = when {
            text.contains("回家") -> "家"
            else -> text.replace(Regex("导航|路线|带我去|去|到"), "").trim().ifBlank { "目的地" }
        }
        val command = AssistantCommand(
            type = CommandType.Navigate,
            title = "导航任务",
            description = "目的地：$destination",
            primaryActionLabel = "打开地图导航",
            payload = mapOf("destination" to destination)
        )
        return AssistantResult(
            reply = "我识别到了导航任务，确认后会打开地图应用。",
            source = "local_navigation_router",
            command = command
        )
    }

    private fun parseOpenApp(text: String): AssistantResult? {
        val knownApps = mapOf(
            "微信" to "com.tencent.mm",
            "支付宝" to "com.eg.android.AlipayGphone",
            "淘宝" to "com.taobao.taobao",
            "QQ" to "com.tencent.mobileqq",
            "高德" to "com.autonavi.minimap",
            "地图" to "com.autonavi.minimap",
            "浏览器" to "com.android.browser"
        )
        val appName = knownApps.keys.firstOrNull { text.contains("打开$it") || text == it || text.contains(it) } ?: return null
        val command = AssistantCommand(
            type = CommandType.OpenApp,
            title = "打开应用",
            description = "应用：$appName",
            primaryActionLabel = "打开$appName",
            payload = mapOf(
                "appName" to appName,
                "packageName" to knownApps.getValue(appName)
            )
        )
        return AssistantResult(
            reply = "我识别到了打开应用任务，确认后会尝试启动 $appName。",
            source = "local_open_app_router",
            command = command
        )
    }

    private fun parseLedger(text: String): AssistantResult? {
        if (!Regex("元|块|花|买|午饭|晚饭|早餐|奶茶|打车|收入|工资").containsMatchIn(text)) return null
        val amount = Regex("(\\d+(?:\\.\\d+)?)").find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return null
        val type = if (Regex("收入|工资|报销|收到|进账").containsMatchIn(text)) "income" else "expense"
        val title = text
            .replace(Regex("今天|昨天|前天|花了|花费|消费|支出|收入|收到|工资|报销|元|块钱|块|\\d+(?:\\.\\d+)?"), "")
            .trim()
            .ifBlank { if (type == "income") "收入" else "消费" }
        val category = when {
            Regex("饭|早餐|午饭|晚饭|外卖|餐").containsMatchIn(text) -> "餐饮"
            Regex("奶茶|咖啡|饮料").containsMatchIn(text) -> "饮品"
            Regex("打车|地铁|公交|高铁|火车").containsMatchIn(text) -> "交通"
            Regex("买|淘宝|京东|购物").containsMatchIn(text) -> "购物"
            Regex("工资|收入|报销").containsMatchIn(text) -> "工资"
            else -> "其他"
        }
        val draft = LedgerDraft(title = title, amount = amount, type = type, category = category)
        val command = AssistantCommand(
            type = CommandType.LedgerDraft,
            title = "记账草稿",
            description = "${if (type == "income") "收入" else "支出"}：$title\n金额：¥${"%.2f".format(amount)}\n分类：$category",
            primaryActionLabel = "确认记账",
            payload = mapOf(
                "title" to title,
                "amount" to amount.toString(),
                "type" to type,
                "category" to category
            )
        )
        return AssistantResult(
            reply = "我整理出一笔账单草稿，后面会接 Room 数据库保存。",
            source = "local_ledger_router",
            command = command,
            ledgerDraft = draft
        )
    }
}

data class AssistantResult(
    val reply: String,
    val source: String,
    val command: AssistantCommand? = null,
    val ledgerDraft: LedgerDraft? = null
) {
    companion object {
        fun chat(reply: String, source: String = "compose_local") = AssistantResult(reply = reply, source = source)
    }
}

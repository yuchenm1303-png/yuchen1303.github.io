package com.yuchen.ailedger.service

sealed class MobileCommand {
    abstract val title: String
    abstract val summary: String

    data class SetAlarm(
        val hour: Int,
        val minute: Int,
        val label: String,
        val dateLabel: String,
    ) : MobileCommand() {
        override val title: String = "设置系统闹钟"
        override val summary: String = "$dateLabel ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} · $label"
    }

    data class OpenApp(
        val appName: String,
        val packageName: String?,
    ) : MobileCommand() {
        override val title: String = "打开手机应用"
        override val summary: String = if (packageName.isNullOrBlank()) appName else "$appName · $packageName"
    }

    data class Navigate(
        val destination: String,
        val mode: String,
    ) : MobileCommand() {
        override val title: String = "地图导航"
        override val summary: String = "到 $destination"
    }
}

object MobileCommandParser {
    fun parse(text: String): MobileCommand? = null
}

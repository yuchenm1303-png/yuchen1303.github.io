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
        override val title: String = "Set alarm"
        override val summary: String = "$dateLabel ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} · $label"
    }

    data class OpenApp(
        val appName: String,
        val packageName: String?,
    ) : MobileCommand() {
        override val title: String = "Open app"
        override val summary: String = appName
    }

    data class Navigate(
        val destination: String,
        val mode: String,
    ) : MobileCommand() {
        override val title: String = "Navigate"
        override val summary: String = destination
    }
}

object MobileCommandParser {
    fun parse(text: String): MobileCommand? = null
}

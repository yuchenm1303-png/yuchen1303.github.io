package com.yuchen.ailedger.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.widget.Toast
import com.yuchen.ailedger.model.AssistantCommand
import com.yuchen.ailedger.model.CommandType

object AndroidActionExecutor {
    fun execute(context: Context, command: AssistantCommand) {
        when (command.type) {
            CommandType.SetAlarm -> setAlarm(context, command)
            CommandType.Navigate -> navigate(context, command)
            CommandType.OpenApp -> openApp(context, command)
            CommandType.LedgerDraft -> toast(context, "记账数据库下一步接入，现在先保留草稿卡片。")
            CommandType.Chat -> Unit
        }
    }

    private fun setAlarm(context: Context, command: AssistantCommand) {
        val hour = command.payload["hour"]?.toIntOrNull() ?: 8
        val minute = command.payload["minute"]?.toIntOrNull() ?: 0
        val label = command.payload["label"].orEmpty().ifBlank { "AI 助手提醒" }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { toast(context, "没有找到可用的系统闹钟应用。") }
    }

    private fun navigate(context: Context, command: AssistantCommand) {
        val destination = command.payload["destination"].orEmpty().ifBlank { "家" }
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        runCatching { context.startActivity(intent) }
            .onFailure { toast(context, "没有找到可用的地图应用。") }
    }

    private fun openApp(context: Context, command: AssistantCommand) {
        val packageName = command.payload["packageName"].orEmpty()
        val appName = command.payload["appName"].orEmpty().ifBlank { "应用" }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
            return
        }
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        try {
            context.startActivity(marketIntent)
        } catch (_: ActivityNotFoundException) {
            toast(context, "没有安装 $appName，也没有找到应用商店。")
        }
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

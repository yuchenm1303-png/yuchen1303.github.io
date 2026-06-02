package com.yuchen.ailedger

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.widget.Toast

class SystemActionRouter(
    private val activity: Activity,
) {
    fun openApp(packageName: String, fallbackLabel: String? = null): Boolean {
        val launchIntent = activity.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            activity.startActivity(launchIntent)
            return true
        }
        toast("没有找到${fallbackLabel ?: packageName}")
        return false
    }

    fun openDeepLink(uriText: String, fallbackPackageName: String? = null, fallbackLabel: String? = null): Boolean {
        val cleanUri = uriText.trim()
        if (cleanUri.isBlank()) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUri)).apply {
            if (!fallbackPackageName.isNullOrBlank()) setPackage(fallbackPackageName)
        }
        val deepLinkOpened = runCatching {
            activity.startActivity(intent)
            true
        }.getOrElse { false }
        if (deepLinkOpened) return true
        return if (!fallbackPackageName.isNullOrBlank()) {
            openApp(fallbackPackageName, fallbackLabel)
        } else {
            toast("无法打开${fallbackLabel ?: "入口"}")
            false
        }
    }

    fun startNavigation(target: String): Boolean {
        if (target.isBlank()) return false
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(target)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        return runCatching {
            activity.startActivity(intent)
            true
        }.getOrElse {
            toast("没有可用的地图应用")
            false
        }
    }

    fun setAlarm(hour: Int, minute: Int, message: String = "AI 助手提醒"): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        return runCatching {
            activity.startActivity(intent)
            true
        }.getOrElse {
            toast("无法打开系统闹钟")
            false
        }
    }

    private fun toast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }
}

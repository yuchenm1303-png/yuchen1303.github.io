package com.yuchen.ailedger

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings

class AgentAccessibilityGuideActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlertDialog.Builder(this)
            .setTitle("开启手机智能体")
            .setMessage("手机智能体需要无障碍权限，才能在你主动发起任务时读取当前屏幕上的文字、按钮和输入框。\n\n当前版本采用按需快照模式，不会持续后台扫描，也不会自动点击、输入或滚动。")
            .setPositiveButton("去开启") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                finish()
            }
            .setNegativeButton("暂不开启") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    companion object {
        fun open(context: Context) {
            context.startActivity(
                Intent(context, AgentAccessibilityGuideActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}

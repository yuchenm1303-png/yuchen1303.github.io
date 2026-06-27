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
            .setMessage(
                "手机智能体需要你手动开启无障碍服务，才能在主动发起任务时读取当前页面、执行手势，并在其他应用上方显示只读视觉 HUD。\n\n" +
                    "视觉 HUD 由无障碍服务承载，不需要额外开启“显示在其他应用上层”。空闲时不会监听窗口事件、持续扫描节点或持续截图。"
            )
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

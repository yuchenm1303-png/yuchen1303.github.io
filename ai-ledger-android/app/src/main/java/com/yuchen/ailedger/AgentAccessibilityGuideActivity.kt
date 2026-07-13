package com.yuchen.ailedger

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.yuchen.ailedger.service.AgentOFloatingChatController

class AgentAccessibilityGuideActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlertDialog.Builder(this)
            .setTitle("开启悬浮交互能力")
            .setMessage(
                "Agent O 普通聊天悬浮窗，以及带无限符号 Agent 开关控制的视觉智能体浮窗，都由同一个无障碍服务承载。视觉智能体只有在你主动发起任务时才会读取页面和执行手势。\n\n" +
                    "不需要额外开启“显示在其他应用上层”。空闲时不会监听窗口事件、持续扫描节点或持续截图。"
            )
            .setPositiveButton("去开启") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                finish()
            }
            .setNegativeButton("暂不开启") { _, _ ->
                AgentOFloatingChatController.setEnabled(false)
                finish()
            }
            .setOnCancelListener {
                AgentOFloatingChatController.setEnabled(false)
                finish()
            }
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

package com.yuchen.ailedger

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.yuchen.ailedger.service.AssistantFloatingChatBridge
import org.json.JSONObject

/**
 * Agent O 无障碍浮窗的图片选择跳板。
 *
 * WebView 不直接读取文件系统；系统选择器返回的 URI 交回现有 AssistantViewModel 图片压缩链。
 */
class AgentOAttachmentPickerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                },
                REQUEST_PICK_IMAGE,
            )
        }
    }

    @Deprecated("Deprecated in Android SDK but retained for the API 26 activity-result bridge.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                AssistantFloatingChatBridge.dispatch(
                    action = "attachment.selected",
                    payload = JSONObject().put("uri", uri.toString()),
                )
            }
        }
        finish()
    }

    companion object {
        private const val REQUEST_PICK_IMAGE = 8401

        fun open(context: Context) {
            context.startActivity(
                Intent(context, AgentOAttachmentPickerActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
            )
        }
    }
}

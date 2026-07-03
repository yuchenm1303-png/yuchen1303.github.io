package com.yuchen.ailedger.service

import android.content.Context
import android.util.Base64
import com.yuchen.ailedger.data.VisualDemonstrationStore
import com.yuchen.ailedger.model.ChatAttachment
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.LearnedVisualSkill
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.VisualDemonstrationFrame
import com.yuchen.ailedger.model.VisualSkillInput
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 云端是演示理解的唯一决策者。本地只按时间顺序上传视觉证据和教学目标，
 * 不在本地推断步骤、控件、选择器、分支或恢复路线。
 */
class OperationSkillCloudClient(
    context: Context,
    private val aiWorkerClient: AiWorkerClient = AiWorkerClient(),
) {
    private val visualStore = VisualDemonstrationStore(context.applicationContext)

    suspend fun synthesize(manifestPath: String): LearnedVisualSkill = withContext(Dispatchers.IO) {
        val manifest = visualStore.load(manifestPath)
        require(manifest.frames.size >= MINIMUM_VISUAL_FRAMES) {
            "视觉演示关键帧不足，无法可靠理解 Skill"
        }
        val sampledFrames = sampleFrames(manifest.frames, MAX_UPLOAD_FRAMES)
        val attachments = sampledFrames.mapIndexed { index, frame ->
            val bytes = visualStore.readFrameBytes(manifestPath, frame)
            ChatAttachment(
                id = frame.id,
                mimeType = frame.mimeType.ifBlank { "image/jpeg" },
                base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
                fileName = "demo-${(index + 1).toString().padStart(2, '0')}.jpg",
                width = frame.width,
                height = frame.height,
                sizeBytes = bytes.size,
            )
        }
        val response = aiWorkerClient.sendChat(
            messages = listOf(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = buildPrompt(
                        workflowId = manifest.workflowId,
                        title = manifest.workflowTitle,
                        goal = manifest.goal,
                        allowedPackages = manifest.allowedPackages,
                        frameCount = sampledFrames.size,
                    ),
                    role = MessageRole.User,
                    attachments = attachments,
                ),
            ),
            modelPreference = ChatModel.Kimi,
            onlineEnabled = false,
        )
        parseSkill(
            workflowId = manifest.workflowId,
            fallbackName = manifest.workflowTitle,
            fallbackGoal = manifest.goal,
            rawReply = response.reply,
        )
    }

    private fun buildPrompt(
        workflowId: String,
        title: String,
        goal: String,
        allowedPackages: List<String>,
        frameCount: Int,
    ): String = """
        你是 Android Record & Replay 的 Skill 学习大脑。下面 $frameCount 张图片按时间顺序展示用户亲自完成一次任务时的屏幕关键帧。

        教学名称：$title
        用户目标：$goal
        Skill ID：$workflowId
        允许应用：${allowedPackages.joinToString()}

        请理解整次示范的目的和用户方法，生成可泛化的视觉 Skill。Skill 运行时会由云端视觉智能重新观察当前屏幕并决定下一步，因此：
        1. 不得输出固定坐标、Resource ID、无障碍节点、选择器、页面指纹或机械点击脚本。
        2. 不要逐帧复述；提炼触发场景、每次运行可能变化的输入、操作原则、成功标准和安全边界。
        3. 无法从图片确认的内容必须保持抽象，不得编造应用内部结构。
        4. 密码、验证码、支付确认、删除和不可逆操作必须交给用户或再次确认。
        5. operatingPrinciples 应描述语义方法，例如“进入账单页面后选择目标月份”，而不是“点击右上角按钮”。

        只返回一个合法 JSON 对象，不要 Markdown，不要解释，结构严格为：
        {
          "name": "Skill 名称",
          "description": "这个 Skill 何时使用以及完成什么",
          "triggerExamples": ["用户可能说的话"],
          "inputs": [
            {"key":"英文下划线标识","label":"中文名称","description":"为何需要","required":true,"sensitive":false}
          ],
          "operatingPrinciples": ["按语义描述的方法和必要条件"],
          "successCriteria": ["视觉上可以确认的完成结果"],
          "safetyRules": ["必须遵守的风险边界"],
          "cloudSummary": "你从这次示范中学到的核心方法",
          "confidence": 0.0
        }
    """.trimIndent()

    private fun parseSkill(
        workflowId: String,
        fallbackName: String,
        fallbackGoal: String,
        rawReply: String,
    ): LearnedVisualSkill {
        val source = JSONObject(extractJsonObject(rawReply))
        val inputs = buildList {
            val array = source.optJSONArray("inputs") ?: JSONArray()
            for (index in 0 until array.length().coerceAtMost(MAX_INPUTS)) {
                val item = array.optJSONObject(index) ?: continue
                val label = item.optString("label").trim().take(MAX_FIELD_LENGTH)
                if (label.isBlank()) continue
                val key = normalizeInputKey(item.optString("key"), index)
                add(
                    VisualSkillInput(
                        key = key,
                        label = label,
                        description = item.optString("description").trim().take(MAX_DESCRIPTION_LENGTH),
                        required = item.optBoolean("required", true),
                        sensitive = item.optBoolean("sensitive", false),
                    ),
                )
            }
        }.distinctBy(VisualSkillInput::key)
        return LearnedVisualSkill(
            workflowId = workflowId,
            name = source.optString("name").trim().take(MAX_FIELD_LENGTH).ifBlank { fallbackName },
            description = source.optString("description").trim().take(MAX_DESCRIPTION_LENGTH).ifBlank { fallbackGoal },
            triggerExamples = source.optJSONArray("triggerExamples").toStringList(MAX_LIST_ITEMS),
            inputs = inputs,
            operatingPrinciples = source.optJSONArray("operatingPrinciples").toStringList(MAX_LIST_ITEMS),
            successCriteria = source.optJSONArray("successCriteria").toStringList(MAX_LIST_ITEMS),
            safetyRules = source.optJSONArray("safetyRules").toStringList(MAX_LIST_ITEMS),
            cloudSummary = source.optString("cloudSummary").trim().take(MAX_SUMMARY_LENGTH),
            confidence = source.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f),
            learnedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun extractJsonObject(raw: String): String {
        val text = raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = text.indexOf('{')
        require(start >= 0) { "云端没有返回 Skill JSON" }
        var depth = 0
        var quoted = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (quoted) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> quoted = false
                }
            } else {
                when (char) {
                    '"' -> quoted = true
                    '{' -> depth += 1
                    '}' -> {
                        depth -= 1
                        if (depth == 0) return text.substring(start, index + 1)
                    }
                }
            }
        }
        error("云端 Skill JSON 不完整")
    }

    private fun sampleFrames(
        frames: List<VisualDemonstrationFrame>,
        maximum: Int,
    ): List<VisualDemonstrationFrame> {
        if (frames.size <= maximum) return frames
        val lastIndex = frames.lastIndex.toDouble()
        return (0 until maximum)
            .map { index -> frames[(index * lastIndex / (maximum - 1)).toInt()] }
            .distinctBy(VisualDemonstrationFrame::id)
    }

    private fun normalizeInputKey(value: String, index: Int): String {
        val normalized = value.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .take(48)
        return normalized.ifBlank { "input_${index + 1}" }
    }

    private fun JSONArray?.toStringList(limit: Int): List<String> = buildList {
        val array = this@toStringList ?: return@buildList
        for (index in 0 until array.length().coerceAtMost(limit)) {
            array.optString(index).trim().take(MAX_DESCRIPTION_LENGTH)
                .takeIf(String::isNotBlank)
                ?.let(::add)
        }
    }.distinct()

    companion object {
        private const val MINIMUM_VISUAL_FRAMES = 2
        private const val MAX_UPLOAD_FRAMES = 12
        private const val MAX_INPUTS = 12
        private const val MAX_LIST_ITEMS = 16
        private const val MAX_FIELD_LENGTH = 80
        private const val MAX_DESCRIPTION_LENGTH = 320
        private const val MAX_SUMMARY_LENGTH = 800
    }
}

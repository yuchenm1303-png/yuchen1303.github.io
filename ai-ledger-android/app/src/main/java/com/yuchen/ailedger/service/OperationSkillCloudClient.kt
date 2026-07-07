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
import com.yuchen.ailedger.model.VisualSkillRouteStep
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
                        frames = sampledFrames,
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
        frames: List<VisualDemonstrationFrame>,
    ): String = """
        你是 Android Record & Replay 的 Skill 学习大脑。下面 ${frames.size} 张图片按时间顺序展示用户亲自完成一次任务时的屏幕关键帧。

        教学名称：$title
        用户目标：$goal
        Skill ID：$workflowId
        允许应用：${allowedPackages.joinToString()}

        帧时间线：
        ${frames.joinToString("\n") { frame -> frame.timelineLine() }}

        请理解整次示范的目的和用户方法，生成可泛化但尊重演示路线的视觉 Skill。Skill 运行时会由云端视觉智能重新观察当前屏幕并决定下一步，因此：
        1. 不得输出固定坐标、Resource ID、无障碍节点、选择器、页面指纹或机械点击脚本。
        2. 不要逐帧复述，也不要把演示过度抽象成“自己探索到目标”。必须提炼 routeSteps：按演示顺序写出路线检查点。
        3. 每个 routeStep 要包含开始页面状态、视觉锚点、推荐语义动作、完成证据、不建议的改道动作、兜底策略和是否可跳过。
        4. routeSteps 只能描述语义动作和视觉锚点，例如“在 QQ 主消息页点击左上角头像进入个人中心”“在个人中心底部点击设置入口”，不能写具体坐标或节点 ID。
        5. 运行时应优先沿 routeSteps 走；只有当前页面确实缺少该视觉锚点或路线被界面版本阻断，才允许使用 fallbackPolicy。
        6. 无法从图片确认的内容必须保持抽象，不得编造应用内部结构。
        7. 密码、验证码、支付确认、删除和不可逆操作必须交给用户或再次确认。

        只返回一个合法 JSON 对象，不要 Markdown，不要解释，结构严格为：
        {
          "name": "Skill 名称",
          "description": "这个 Skill 何时使用以及完成什么",
          "triggerExamples": ["用户可能说的话"],
          "inputs": [
            {"key":"英文下划线标识","label":"中文名称","description":"为何需要","required":true,"sensitive":false}
          ],
          "operatingPrinciples": ["抽象方法和必要条件"],
          "routeSteps": [
            {
              "order":1,
              "instruction":"演示路线中的语义动作",
              "startState":"执行这一步前通常应看到的页面状态",
              "visualAnchor":"兼容旧字段：最主要视觉锚点",
              "visualAnchors":["应寻找的视觉锚点"],
              "preferredAction":"围绕视觉锚点执行的推荐动作",
              "expectedEvidence":"兼容旧字段：完成此步后的主要证据",
              "expectedEvidenceList":["完成此步后的视觉证据"],
              "discouragedActions":["看似可行但偏离演示路线的动作"],
              "fallback":"兼容旧字段：最小兜底",
              "fallbackPolicy":"锚点缺失或界面版本阻断时允许的最小改道",
              "skippable":false
            }
          ],
          "successCriteria": ["视觉上可以确认的完成结果"],
          "safetyRules": ["必须遵守的风险边界"],
          "cloudSummary": "你从这次示范中学到的核心方法",
          "confidence": 0.0
        }
    """.trimIndent()

    private fun VisualDemonstrationFrame.timelineLine(): String {
        val relative = if (eventOccurredAtMillis > 0L) eventOccurredAtMillis else capturedAtMillis
        return buildString {
            append("- frame=").append(id.take(18))
            append(" package=").append(packageName.ifBlank { "unknown" })
            append(" kind=").append(captureKind.ifBlank { "timed" })
            if (eventType.isNotBlank()) append(" event=").append(eventType)
            if (eventIndex > 0) append(" eventIndex=").append(eventIndex)
            append(" t=").append(relative)
        }
    }

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
            routeSteps = source.optJSONArray("routeSteps").toRouteStepList(),
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
        val priorityKinds = setOf("initial", "after_action", "action_settle", "final")
        val selected = linkedMapOf<String, VisualDemonstrationFrame>()
        fun add(frame: VisualDemonstrationFrame) {
            if (selected.size < maximum) selected[frame.id] = frame
        }
        frames.firstOrNull()?.let(::add)
        frames.filter { it.captureKind in priorityKinds || it.eventIndex > 0 }
            .sortedWith(compareBy<VisualDemonstrationFrame> { it.eventIndex.takeIf { index -> index > 0 } ?: Int.MAX_VALUE }
                .thenBy { it.capturedAtMillis })
            .forEach(::add)
        frames.lastOrNull()?.let(::add)
        if (selected.size < maximum) {
            val remaining = frames.filterNot { selected.containsKey(it.id) }
            val slots = maximum - selected.size
            if (remaining.size <= slots) {
                remaining.forEach(::add)
            } else {
                val lastIndex = remaining.lastIndex.toDouble()
                (0 until slots).forEach { index ->
                    add(remaining[(index * lastIndex / (slots - 1).coerceAtLeast(1)).toInt()])
                }
            }
        }
        return selected.values.sortedBy { it.capturedAtMillis }
    }

    private fun normalizeInputKey(value: String, index: Int): String {
        val normalized = value.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .take(48)
        return normalized.ifBlank { "input_${index + 1}" }
    }

    private fun JSONArray?.toRouteStepList(): List<VisualSkillRouteStep> = buildList {
        val array = this@toRouteStepList ?: return@buildList
        for (index in 0 until array.length().coerceAtMost(MAX_ROUTE_STEPS)) {
            val item = array.optJSONObject(index) ?: continue
            val instruction = item.optString("instruction").trim().take(MAX_DESCRIPTION_LENGTH)
                .ifBlank { item.optString("preferredAction").trim().take(MAX_DESCRIPTION_LENGTH) }
            if (instruction.isBlank()) continue
            add(
                VisualSkillRouteStep(
                    order = item.optInt("order", index + 1).coerceAtLeast(1),
                    instruction = instruction,
                    visualAnchor = item.optString("visualAnchor").trim().take(MAX_DESCRIPTION_LENGTH),
                    expectedEvidence = item.optString("expectedEvidence").trim().take(MAX_DESCRIPTION_LENGTH),
                    fallback = item.optString("fallback").trim().take(MAX_DESCRIPTION_LENGTH),
                    startState = item.optString("startState").trim().take(MAX_DESCRIPTION_LENGTH),
                    visualAnchors = item.optJSONArray("visualAnchors").toStringList(MAX_LIST_ITEMS),
                    preferredAction = item.optString("preferredAction").trim().take(MAX_DESCRIPTION_LENGTH),
                    expectedEvidenceList = item.optJSONArray("expectedEvidenceList").toStringList(MAX_LIST_ITEMS),
                    discouragedActions = item.optJSONArray("discouragedActions").toStringList(MAX_LIST_ITEMS),
                    fallbackPolicy = item.optString("fallbackPolicy").trim().take(MAX_DESCRIPTION_LENGTH),
                    skippable = item.optBoolean("skippable", false),
                ),
            )
        }
    }.sortedBy(VisualSkillRouteStep::order)

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
        private const val MAX_ROUTE_STEPS = 10
        private const val MAX_LIST_ITEMS = 16
        private const val MAX_FIELD_LENGTH = 80
        private const val MAX_DESCRIPTION_LENGTH = 320
        private const val MAX_SUMMARY_LENGTH = 800
    }
}

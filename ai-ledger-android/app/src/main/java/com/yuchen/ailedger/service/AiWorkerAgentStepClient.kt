package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatModel
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

private const val AGENT_STEP_CONNECT_TIMEOUT_MS = 12_000
private const val AGENT_STEP_READ_TIMEOUT_MS = 38_000
private const val AGENT_VISION_ROUTE_ID = "qwen_vision"

@Throws(IOException::class)
fun AiWorkerClient.requestAgentStep(
    goal: String,
    snapshot: AgentScreenSnapshot,
    modelPreference: ChatModel = ChatModel.Auto,
): CloudAgentStep {
    return requestAgentPlan(goal, snapshot, modelPreference).step
}

@Throws(IOException::class)
fun AiWorkerClient.requestAgentPlan(
    goal: String,
    snapshot: AgentScreenSnapshot,
    modelPreference: ChatModel = ChatModel.Auto,
    recentActions: List<String> = emptyList(),
    deviceContext: AgentDeviceContextSnapshot? = null,
    agentMemory: JSONObject? = null,
): CloudAgentPlan {
    val payload = buildAgentStepPayload(goal, snapshot, modelPreference, recentActions, deviceContext, agentMemory)
    val endpoints = listOf(endpoint.trim().trimEnd('/')).filter { it.isNotBlank() }.distinct()
    var lastError: IOException? = null
    for (base in endpoints) {
        for (candidate in agentEndpointCandidates(base)) {
            try {
                return postAgentPlan(candidate, payload)
            } catch (error: IOException) {
                lastError = error
                if (error is SocketTimeoutException || error.cause is SocketTimeoutException) break
            }
        }
    }
    throw lastError ?: IOException("云端智能体规划请求失败")
}

private fun buildAgentStepPayload(
    goal: String,
    snapshot: AgentScreenSnapshot,
    modelPreference: ChatModel,
    recentActions: List<String>,
    deviceContext: AgentDeviceContextSnapshot?,
    agentMemory: JSONObject?,
): JSONObject {
    val instruction = agentPlannerSystemPrompt()
    val cleanGoal = goal.trim().take(240)
    val modelId = if (snapshot.hasVisualImage) AGENT_VISION_ROUTE_ID else if (modelPreference == ChatModel.Auto) ChatModel.Kimi.id else modelPreference.id
    val snapshotForText = snapshot.toJson(includeImage = false)
    val plannerMessage = buildPlannerMessage(cleanGoal, snapshotForText, snapshot.visual, recentActions, deviceContext, agentMemory)
    return JSONObject().apply {
        put("action", "chat")
        put("intent", "agent_step")
        put("agentMode", true)
        put("computerUseMode", true)
        put("visionFirst", snapshot.hasVisualImage)
        put("coordinateProtocol", "normalized_screen_0_1")
        put("agentGoal", cleanGoal)
        put("recentAgentActions", JSONArray().apply { recentActions.takeLast(8).forEach { put(it) } })
        agentMemory?.let { put("agentMemory", it) }
        deviceContext?.let {
            put("deviceContext", it.json)
            put("deviceContextSummary", it.summary)
        }
        put("screenSnapshot", snapshotForText)
        put("screenSnapshotText", snapshotForText)
        put("hasScreenshot", snapshot.hasVisualImage)
        put("hasImage", snapshot.hasVisualImage)
        put("hasImages", snapshot.hasVisualImage)
        put("imageCount", if (snapshot.hasVisualImage) 1 else 0)
        snapshot.visual?.takeIf { it.hasImage }?.let { visual ->
            val imageItem = JSONObject().apply {
                put("mimeType", visual.mimeType)
                put("base64Data", visual.base64Jpeg)
                put("base64", visual.base64Jpeg)
                put("width", visual.width)
                put("height", visual.height)
                put("displayWidth", visual.displayWidth)
                put("displayHeight", visual.displayHeight)
                put("source", visual.source)
                put("reason", visual.reason)
            }
            put("screenshot", imageItem)
            put("images", JSONArray().apply { put(imageItem) })
            put("attachments", JSONArray().apply { put(imageItem) })
            put("vision", JSONObject().apply {
                put("enabled", true)
                put("provider", "qwen")
                put("route", AGENT_VISION_ROUTE_ID)
                put("coordinateSystem", "normalized_screen_0_1")
                put("screenshotWidth", visual.width)
                put("screenshotHeight", visual.height)
                put("displayWidth", visual.displayWidth)
                put("displayHeight", visual.displayHeight)
            })
        } ?: run {
            put("images", JSONArray())
            put("attachments", JSONArray())
            put("vision", JSONObject().apply { put("enabled", false) })
        }
        put("supportedAgentSteps", JSONArray(CloudAgentStep.supportedTypes.toList()))
        put("modelPreference", modelId)
        put("aiModelPreference", modelId)
        put("requestedModelPreference", modelId)
        put("model", modelId)
        put("modelId", modelId)
        put("client", "android-compose")
        put("clientVersion", if (snapshot.hasVisualImage) "compose-native-agent-fast-vision-v3" else "compose-native-agent-fast-v3")
        put("systemPrompt", instruction)
        put("message", plannerMessage)
        put("prompt", plannerMessage)
        put("text", plannerMessage)
        put("messages", JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", instruction) })
            put(JSONObject().apply { put("role", "user"); put("content", plannerMessage) })
        })
        put("responseFormat", JSONObject().apply {
            put("type", "json_object")
            put("includeAgentState", true)
            put("includeAgentStep", true)
        })
        put("now", System.currentTimeMillis())
    }
}

private fun buildPlannerMessage(
    goal: String,
    snapshotJsonWithoutImage: JSONObject,
    visual: AgentScreenVisual?,
    recentActions: List<String>,
    deviceContext: AgentDeviceContextSnapshot?,
    agentMemory: JSONObject?,
): String {
    return buildString {
        append("用户目标：").append(goal).append('\n')
        deviceContext?.let { context ->
            append("设备上下文：").append(context.summary).append('\n')
            append("目标相关可启动应用候选：").append(appsPromptList(context.json.optJSONArray("targetAppCandidates"), 16)).append('\n')
            append("可启动应用摘要：").append(appsPromptList(context.json.optJSONArray("installedApps"), 48)).append('\n')
            append("重要：打开应用时优先从 targetAppCandidates 选择；不要猜包名，不要在桌面文件夹里找图标。\n")
        }
        agentMemory?.let { memory -> append("智能体短期记忆：").append(memory).append('\n') }
        if (recentActions.isNotEmpty()) {
            append("最近动作记录：\n")
            recentActions.takeLast(8).forEachIndexed { index, item -> append(index + 1).append(". ").append(item).append('\n') }
        }
        append("当前结构化快照：").append(snapshotJsonWithoutImage).append('\n')
        if (visual?.hasImage == true) {
            append("本次包含屏幕截图。截图尺寸为 ")
                .append(visual.width).append("x").append(visual.height)
                .append("，真实屏幕尺寸为 ")
                .append(visual.displayWidth).append("x").append(visual.displayHeight).append("。\n")
            append("请以截图为主、节点为辅判断当前状态；节点可能缺失、滞后或只暴露底部入口文字。\n")
            append("tap_xy 必须返回 0 到 1 的归一化屏幕坐标，不要返回像素。\n")
            append("如果用户目标是进入某个页面、界面、Tab、栏目或列表，仅看到入口文字/按钮不等于完成；只有目标 Tab 已选中且主体内容切换到目标界面，才可以 finish。\n")
        } else {
            append("当前没有截图；如果目标只是打开应用，请直接用 deviceContext 的 open_app 工具规划，不要等待截图。\n")
            append("如果当前已经在目标 App 内或需要判断 App 内页面，再结合节点与历史谨慎规划。\n")
        }
        append("请先判断 agentState，再返回一步 agentStep。")
    }
}

private fun appsPromptList(array: JSONArray?, limit: Int): String {
    if (array == null || array.length() == 0) return "无"
    val items = buildList {
        for (index in 0 until minOf(array.length(), limit)) {
            val item = array.optJSONObject(index) ?: continue
            val label = item.optString("label").takeIf { it.isNotBlank() } ?: continue
            val pkg = item.optString("packageName").takeIf { it.isNotBlank() } ?: "unknown"
            add("$label($pkg)")
        }
    }
    return items.joinToString(" / ").ifBlank { "无" }
}

private fun agentPlannerSystemPrompt(): String = """
你是 Android 手机 Computer Use 智能体的云端状态规划器，只能输出严格 JSON，不要 Markdown。
本地执行层不做 App 内页面语义判断，因此你必须负责判断当前是否完成、是否在正确路径、是否需要继续操作。

返回格式：
{"agentState":{"isComplete":false,"expectedProgress":false,"isWrong":false,"confidence":0.0,"reason":"当前状态判断","nextHint":"下一步提示"},"agentStep":{"type":"open_app|home|back|recents|notifications|quick_settings|tap_node|tap_xy|input_text|scroll|swipe|wait|finish|need_user_help","targetNodeId":"可选节点 id","targetText":"可选目标文字","appName":"可选应用名","packageName":"可选包名","text":"可选输入文字","direction":"up|down|left|right 可选","x":可选数字,"y":可选数字,"durationMs":可选毫秒,"reason":"简短行动理由","riskLevel":"low|medium|high","requiresConfirmation":false}}

设备上下文规则：
1. deviceContext.targetAppCandidates 是根据当前任务从本机真实可启动应用里筛出的候选；打开应用优先从这里选 appName/packageName。
2. targetAppCandidates 为空时，再从 deviceContext.installedApps 选择真实 appName/packageName。
3. 不要凭常识编 packageName。目标应用不在 targetAppCandidates 或 installedApps 时，返回 need_user_help。
4. 在桌面、启动器或文件夹界面时，不要点击文件夹、不要翻桌面页去肉眼找 App；直接使用 open_app。
5. open_app 是系统工具能力，不需要桌面图标可见。
6. 当前没有截图时，如果目标是打开应用，仍然应该根据 deviceContext 返回 open_app，而不是 wait 或 need_user_help。

循环记忆规则：
1. 必须阅读 agentMemory 和最近动作记录，避免重复执行刚失败或刚被拒绝的动作。
2. 如果刚刚点击某文件夹、某入口或某坐标后失败/返回，下一轮不要再点同一位置或同一路径。
3. 如果同一个动作已经重复或被本地标记 blocked，必须换路径、使用 open_app/search/back，或说明需要用户协助。
4. 不能把“回到相同截图”误认为还没有尝试过；要结合历史动作判断。

坐标协议：
1. tap_xy 的 x/y 一律返回归一化屏幕坐标，范围 0 到 1，不要返回像素坐标。
2. x=0 表示最左侧，x=1 表示最右侧；y=0 表示最顶部，y=1 表示最底部。
3. 点击底部导航栏时，y 通常在 0.91 到 0.97 之间；不要点到导航栏上方内容列表。

状态判断规则：
1. screenshot 是主输入；但没有截图时也要用 screenSnapshot、deviceContext、agentMemory 和最近动作记录规划低风险系统动作。
2. 先根据最近动作记录理解刚刚发生了什么，再看当前状态决定下一步，避免重复点击同一个无效入口。
3. 用户目标若是“打开某 App”且当前已在该 App，可以 isComplete=true 并返回 finish。
4. 用户目标若是进入某个页面、界面、Tab、栏目或列表，仅看到入口按钮/底部 Tab 文字，不等于完成；这只表示 expectedProgress=true，应继续点击入口。
5. 只有目标 Tab 已选中、页面标题/主体内容已经切换到目标界面，或者目标内容已经展开，才可以 isComplete=true 并返回 finish。
6. 如果当前页面比上一阶段更接近目标但还没完成，agentState.expectedProgress=true，agentStep 返回下一步操作，不要返回 finish。
7. 如果当前明显走错，agentState.isWrong=true，优先返回 back。
8. 如果 isComplete=true 且 confidence >= 0.72，agentStep 应返回 finish；如果只是 expectedProgress=true，不要 finish。

动作规划规则：
1. 当前不在目标 App，且 open_app 可用，优先 open_app。
2. 截图里目标文字、导航入口、返回键、输入框可见时，优先 tap_xy 或 tap_node。
3. 目标不在当前 App 内页面时，优先寻找语义相关入口、搜索入口、底部/顶部导航入口。
4. 找社交动态或内容流时，发现、动态、朋友、社区、广场、频道等入口通常比聊天列表更有价值。
5. 找联系人/商品/视频/文件时，搜索入口或对应 Tab 通常比随机列表项更有价值。
6. 不要盲目点击聊天列表、联系人列表、支付、设置等低相关或高风险入口。
7. 没有可靠入口但页面可探索时，可以 scroll 或 swipe，不要直接 need_user_help。
8. 页面刚变化或截图显示加载/动画过渡时，返回 wait。
9. 目标已完成时才返回 finish。
10. 只有截图、节点、设备上下文和历史记忆都无法判断且继续操作可能误触时，才返回 need_user_help。

安全规则：
- 涉及支付、转账、下单、删除、发送消息、发布、评论、授权、登录、验证码、密码等高风险动作，必须 riskLevel=high 且 requiresConfirmation=true。
- 不确定时不要乱点高风险按钮。
""".trimIndent()

private fun agentEndpointCandidates(cleanEndpoint: String): List<String> {
    val knownChatPath = cleanEndpoint.endsWith("/chat") || cleanEndpoint.endsWith("/api/chat")
    if (knownChatPath) return listOf(cleanEndpoint)
    return listOf(cleanEndpoint, "$cleanEndpoint/chat", "$cleanEndpoint/api/chat").distinct()
}

private fun postAgentPlan(endpoint: String, payload: JSONObject): CloudAgentPlan {
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = AGENT_STEP_CONNECT_TIMEOUT_MS
        readTimeout = AGENT_STEP_READ_TIMEOUT_MS
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json, text/plain")
        setRequestProperty("X-Client", "android-compose-agent")
    }
    return try {
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val body = connection.agentReadBody(status)
        val data = body.agentJsonOrNull()
        if (status !in 200..299) {
            val message = data?.optString("error")?.takeIf { it.isNotBlank() }
                ?: data?.optString("message")?.takeIf { it.isNotBlank() }
                ?: body.take(120).ifBlank { "云端智能体规划失败：HTTP $status" }
            throw IOException(message)
        }
        val step = CloudAgentStep.fromJson(data) ?: extractAgentStepFromText(body)
            ?: throw IOException("云端没有返回有效的智能体下一步动作")
        val state = CloudAgentState.fromJson(data) ?: extractAgentStateFromText(body)
        CloudAgentPlan(step = step, state = state)
    } catch (error: SocketTimeoutException) {
        throw IOException("云端智能体规划超时：${endpoint.substringAfter("://")}", error)
    } finally {
        connection.disconnect()
    }
}

private fun HttpURLConnection.agentReadBody(status: Int): String {
    val stream = if (status in 200..299) inputStream else errorStream
    return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
}

private fun String.agentJsonOrNull(): JSONObject? {
    return try { takeIf { it.isNotBlank() }?.let { JSONObject(it) } } catch (_: Exception) { null }
}

private fun extractAgentStepFromText(text: String): CloudAgentStep? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return try { CloudAgentStep.fromJson(JSONObject(text.substring(start, end + 1))) } catch (_: Exception) { null }
}

private fun extractAgentStateFromText(text: String): CloudAgentState? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return try { CloudAgentState.fromJson(JSONObject(text.substring(start, end + 1))) } catch (_: Exception) { null }
}

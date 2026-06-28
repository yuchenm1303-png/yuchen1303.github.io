package com.yuchen.ailedger.service

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal object VisualIntelligenceDiagnosticsReport {
    private val requestBoundaryTypes = setOf("model_request", "model_request_context", "model_request_memory")

    private data class TraceEvent(
        val index: Int,
        val type: String,
        val turnId: String,
        val capturedAt: Long,
        val raw: JSONObject,
    ) {
        val details: JSONObject?
            get() = raw.optJSONObject("details")

        fun frameFile(): String = raw.optJSONObject("visual")?.optString("frameFile").orEmpty()
    }

    private data class Finding(
        val severity: String,
        val title: String,
        val detail: String,
        val turnId: String = "",
        val eventIndex: Int = -1,
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("severity", severity)
            put("title", title)
            put("detail", detail)
            put("turnId", turnId)
            put("eventIndex", eventIndex)
        }
    }

    fun build(session: File) {
        val events = readEvents(session)
        val summary = readJson(File(session, "summary.json")) ?: JSONObject()
        val findings = analyze(events)
        File(session, "findings.json").writeText(
            JSONObject().apply {
                put("schema", "visual_intelligence_findings_v3")
                put("generatedAt", System.currentTimeMillis())
                put("taskId", summary.optLong("taskId"))
                put("findings", JSONArray().apply { findings.forEach { put(it.toJson()) } })
            }.toString(2)
        )
        File(session, "findings.txt").writeText(buildFindingsText(summary, events, findings))
        File(session, "report.html").writeText(buildHtml(summary, events, findings))
    }

    private fun readEvents(session: File): List<TraceEvent> {
        val trace = File(session, "trace.jsonl")
        if (!trace.isFile) return emptyList()
        var syntheticTurn = 0
        var currentTurn = ""
        return trace.useLines { lines ->
            lines.mapIndexedNotNull { index, line ->
                val json = runCatching { JSONObject(line) }.getOrNull() ?: return@mapIndexedNotNull null
                val type = json.optString("type")
                val explicitTurn = json.optString("turnId")
                if (explicitTurn.isNotBlank()) {
                    currentTurn = explicitTurn
                } else if (type in requestBoundaryTypes) {
                    syntheticTurn += 1
                    currentTurn = "turn-${syntheticTurn.toString().padStart(3, '0')}"
                }
                TraceEvent(
                    index = index,
                    type = type,
                    turnId = explicitTurn.ifBlank { currentTurn },
                    capturedAt = json.optLong("capturedAt"),
                    raw = json,
                )
            }.toList()
        }
    }

    private fun analyze(events: List<TraceEvent>): List<Finding> {
        val findings = mutableListOf<Finding>()
        val exactRequests = events.filter { it.type == "model_request" }
        val requestContexts = events.filter { it.type in requestBoundaryTypes }
        val transportResponses = events.filter { it.type == "model_transport_response" }
        val parsedResponses = events.filter { it.type == "model_response" }
        val responseTurns = (transportResponses + parsedResponses).map { it.turnId }.toSet()

        exactRequests.forEach { request ->
            val bytes = request.raw.optInt("requestBytes")
            if (bytes >= 350_000) {
                findings += Finding(
                    "warning",
                    "模型请求体偏大",
                    "本轮上传约 ${bytes / 1024} KB，可能显著增加视觉推理延迟。",
                    request.turnId,
                    request.index,
                )
            }
            if (request.turnId !in responseTurns) {
                findings += Finding(
                    "error",
                    "模型请求缺少对应响应",
                    "已记录真实请求，但没有找到 HTTP 响应或解析后的模型响应。",
                    request.turnId,
                    request.index,
                )
            }
        }

        transportResponses.forEach { event ->
            val details = event.details ?: JSONObject()
            val duration = details.optLong("durationMs")
            val status = details.optInt("httpStatus")
            if (duration >= 10_000L) {
                findings += Finding(
                    "warning",
                    "模型请求耗时过长",
                    "HTTP $status，本轮耗时 ${duration} ms。",
                    event.turnId,
                    event.index,
                )
            }
            if (status !in 200..299) {
                findings += Finding(
                    "error",
                    "模型服务返回错误",
                    "HTTP $status，${details.optString("parseOutcome", "unknown")}。",
                    event.turnId,
                    event.index,
                )
            }
            if (!details.optBoolean("observationIdValid", true)) {
                findings += Finding(
                    "error",
                    "模型响应绑定了错误观察帧",
                    "响应没有通过 observationId 新鲜度校验，旧坐标不能安全执行。",
                    event.turnId,
                    event.index,
                )
            }
        }

        events.filter { it.type in setOf("tap_permit_rejected", "tap_permit_validation") }.forEach { event ->
            val details = event.details ?: JSONObject()
            if (event.type == "tap_permit_rejected" || !details.optBoolean("valid", true)) {
                findings += Finding(
                    "error",
                    "点击许可校验失败",
                    details.optString("reason").ifBlank { "坐标没有通过独立 GUI 执行许可校验。" },
                    event.turnId,
                    event.index,
                )
            }
        }

        events.filter { it.type == "action_validation" }.forEach { event ->
            val details = event.details ?: JSONObject()
            if (!details.optBoolean("ok", true)) {
                findings += Finding(
                    "error",
                    "动作在 Android 验证层被拒绝",
                    "${details.optString("failureClass")}: ${details.optString("message")}",
                    event.turnId,
                    event.index,
                )
            }
        }

        events.filter { it.type == "execution_result" }.forEach { event ->
            val details = event.details ?: JSONObject()
            val message = details.optString("message")
            val summary = details.optString("summary")
            if (message.contains("边界保护") || summary.contains("boundaryAdjusted=true")) {
                findings += Finding(
                    "warning",
                    "点击坐标被边界保护调整",
                    message.ifBlank { summary }.take(360),
                    event.turnId,
                    event.index,
                )
            }
            if (!details.optBoolean("ok", true)) {
                findings += Finding(
                    "error",
                    "动作执行失败",
                    message.take(360),
                    event.turnId,
                    event.index,
                )
            }
        }

        events.filter { it.type == "open_app_verification" }.forEach { event ->
            val details = event.details ?: JSONObject()
            val expected = details.optString("expectedPackage")
            val actual = details.optString("actualPackage")
            if (!details.optBoolean("verified")) {
                findings += Finding(
                    if (expected.isNotBlank() && expected == actual) "error" else "warning",
                    if (expected == actual) "目标应用已在前台却验证失败" else "目标应用前台验证失败",
                    "expected=$expected, actual=$actual, stable=${details.optInt("stableSamples")}/${details.optInt("requiredStableSamples")}, reason=${details.optString("reason")}。",
                    event.turnId,
                    event.index,
                )
            }
        }

        val safetyBlocks = events.filter { event ->
            event.type == "safety_policy" && event.details?.optString("stage") == "auto_execute_gate" &&
                event.details?.optBoolean("canAutoExecute") == false
        }
        safetyBlocks.forEach { event ->
            val details = event.details ?: JSONObject()
            findings += Finding(
                "info",
                "动作被安全策略转交用户",
                "step=${details.optString("stepType")}, confirm=${details.optBoolean("requiresConfirmation")}, input=${details.optBoolean("requiresUserInput")}, executable=${details.optBoolean("executableType")}。",
                event.turnId,
                event.index,
            )
        }

        events.filter { it.type == "completion_protocol" }.forEach { event ->
            val details = event.details ?: JSONObject()
            if (!details.optBoolean("valid", true)) {
                findings += Finding(
                    "error",
                    "完成协议校验失败",
                    "stage=${details.optString("stage")}, reason=${details.optString("reason")}。",
                    event.turnId,
                    event.index,
                )
            }
        }

        val waitingEvents = events.filter { event ->
            event.type == "runtime_progress" && event.raw.optString("status") == "等待输入"
        }
        val repliedEvents = events.filter { event ->
            event.type == "runtime_progress" && event.raw.optString("status") in setOf("已输入", "已确认")
        }
        repliedEvents.forEach { reply ->
            val nextWaiting = waitingEvents.firstOrNull { it.index > reply.index && it.index - reply.index <= 35 }
            if (nextWaiting != null) {
                findings += Finding(
                    "error",
                    "用户已经回复但很快再次被询问",
                    "交互回复可能没有被模型、完成协议或安全策略正确消费。请查看两轮之间的模型输出和 safety_policy 事件。",
                    nextWaiting.turnId,
                    nextWaiting.index,
                )
            }
        }

        val visualFrames = events.filter { it.type == "screen_observation" && it.frameFile().isNotBlank() }
        visualFrames.zipWithNext().forEach { pair ->
            val before = pair.first
            val after = pair.second
            val beforeVisual = before.raw.optJSONObject("visual") ?: return@forEach
            val afterVisual = after.raw.optJSONObject("visual") ?: return@forEach
            val beforeStructural = before.raw.optString("structuralFingerprint")
            val afterStructural = after.raw.optString("structuralFingerprint")
            val distance = VisualDiagnosticFrameAnalyzer.hammingDistance(
                beforeVisual.optString("differenceHash"),
                afterVisual.optString("differenceHash"),
            ) ?: return@forEach
            if (beforeStructural.isNotBlank() && beforeStructural == afterStructural && distance >= 14) {
                findings += Finding(
                    "warning",
                    "视觉画面明显变化但结构指纹未变化",
                    "连续截图 dHash 距离为 $distance，但 structuralFingerprint 仍为 $beforeStructural。",
                    after.turnId,
                    after.index,
                )
            }
        }

        val actionKeys = events.filter { it.type == "planned_action" }.mapNotNull { event ->
            event.details?.let { "${it.optString("type")}|${it.optString("packageName")}|${it.optString("targetText")}" to event }
        }
        actionKeys.zipWithNext().forEach { pair ->
            if (pair.first.first.isNotBlank() && pair.first.first == pair.second.first) {
                findings += Finding(
                    "warning",
                    "连续重复相同动作",
                    pair.second.first,
                    pair.second.second.turnId,
                    pair.second.second.index,
                )
            }
        }

        if (exactRequests.isEmpty()) {
            findings += Finding(
                "warning",
                "尚未采集完整 HTTP 请求体",
                "当前可以还原有效动作记忆、运行时状态和截图，但仍缺少最终发送 JSON 的逐字段副本。",
            )
        }
        if (transportResponses.isEmpty()) {
            findings += Finding(
                "warning",
                "尚未采集完整 HTTP 响应体",
                "当前依赖解析后的 model_response 和运行日志，仍不能百分之百区分后端原始输出与解析器处理。",
            )
        }
        if (requestContexts.isEmpty()) {
            findings += Finding("error", "没有模型轮次边界", "无法把截图、模型决策和动作执行按轮次配对。")
        }

        return findings.distinctBy { listOf(it.severity, it.title, it.detail, it.turnId) }
    }

    private fun buildFindingsText(
        summary: JSONObject,
        events: List<TraceEvent>,
        findings: List<Finding>,
    ): String = buildString {
        appendLine("视觉智能诊断自动分析")
        appendLine("任务：${summary.optString("goal").ifBlank { "未知" }}")
        appendLine("任务 ID：${summary.optLong("taskId")}")
        appendLine("事件数：${events.size}")
        appendLine("模型轮次：${events.filter { it.type in requestBoundaryTypes }.map { it.turnId }.distinct().size}")
        appendLine("完整请求：${events.count { it.type == "model_request" }}")
        appendLine("完整响应：${events.count { it.type == "model_transport_response" }}")
        appendLine("解析响应：${events.count { it.type == "model_response" }}")
        appendLine("视觉帧：${events.count { it.type == "screen_observation" && it.frameFile().isNotBlank() }}")
        appendLine()
        if (findings.isEmpty()) {
            appendLine("未检测到自动规则可识别的异常。")
        } else {
            findings.forEachIndexed { index, finding ->
                append(index + 1).append(". [").append(finding.severity.uppercase()).append("] ")
                    .appendLine(finding.title)
                if (finding.turnId.isNotBlank()) appendLine("   轮次：${finding.turnId}")
                appendLine("   ${finding.detail}")
            }
        }
    }

    private fun buildHtml(
        summary: JSONObject,
        events: List<TraceEvent>,
        findings: List<Finding>,
    ): String {
        val turnIds = events.map { it.turnId }.filter(String::isNotBlank).distinct()
        val unassigned = events.filter { it.turnId.isBlank() }
        return buildString {
            append("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
            append("<title>视觉智能诊断报告</title><style>").append(CSS).append("</style></head><body><main>")
            append("<h1>视觉智能诊断报告</h1><div class=\"meta\"><b>任务：</b>")
                .append(summary.optString("goal").html())
                .append("<br><b>任务 ID：</b>").append(summary.optLong("taskId"))
                .append("<br><b>状态：</b>").append(summary.optString("status").html())
                .append("<br><b>事件：</b>").append(events.size)
                .append("　<b>轮次：</b>").append(turnIds.size)
                .append("　<b>截图：</b>").append(events.count { it.frameFile().isNotBlank() })
                .append("</div>")

            append("<h2>自动发现</h2>")
            if (findings.isEmpty()) {
                append("<div class=\"finding ok\">未检测到自动规则可识别的异常。</div>")
            } else {
                findings.forEach { finding ->
                    append("<div class=\"finding ").append(finding.severity.html()).append("\"><b>")
                        .append(finding.title.html()).append("</b>")
                    if (finding.turnId.isNotBlank()) append(" <code>").append(finding.turnId.html()).append("</code>")
                    append("<p>").append(finding.detail.html()).append("</p></div>")
                }
            }

            append("<h2>逐轮回放</h2>")
            turnIds.forEach { turnId ->
                appendTurn(this, turnId, events.filter { it.turnId == turnId }, events)
            }
            if (unassigned.isNotEmpty()) {
                append("<h2>任务级事件</h2>")
                appendEventList(this, unassigned)
            }
            append("</main></body></html>")
        }
    }

    private fun appendTurn(
        output: StringBuilder,
        turnId: String,
        turnEvents: List<TraceEvent>,
        allEvents: List<TraceEvent>,
    ) {
        val request = turnEvents.firstOrNull { it.type in requestBoundaryTypes }
        val transport = turnEvents.firstOrNull { it.type == "model_transport_response" }
        val parsed = turnEvents.lastOrNull { it.type == "model_response" }
        val planned = turnEvents.lastOrNull { it.type == "planned_action" }
        val result = turnEvents.lastOrNull { it.type == "execution_result" }
        val requestIndex = request?.index ?: turnEvents.firstOrNull()?.index ?: 0
        val explicitInput = request?.raw?.optString("requestFrameFile").orEmpty()
        val inputFrame = explicitInput.ifBlank {
            allEvents.filter { it.index < requestIndex && it.frameFile().isNotBlank() }.lastOrNull()?.frameFile().orEmpty()
        }
        val outputFrame = turnEvents.filter { it.index > requestIndex && it.frameFile().isNotBlank() }
            .lastOrNull()?.frameFile().orEmpty()
        val marker = planned?.details?.marker()
        val actionTitle = planned?.details?.let { details ->
            listOf(details.optString("type"), details.optString("targetText"))
                .filter(String::isNotBlank).joinToString(" · ")
        }.orEmpty().ifBlank {
            parsed?.details?.optJSONObject("parsedStep")?.let { step ->
                listOf(step.optString("type"), step.optString("targetText")).filter(String::isNotBlank).joinToString(" · ")
            }.orEmpty()
        }.ifBlank {
            turnEvents.lastOrNull { it.type == "runtime_progress" }?.raw?.optString("currentAction").orEmpty()
        }.ifBlank { "未记录动作" }

        output.append("<section class=\"turn\"><div class=\"turn-head\"><h3>")
            .append(turnId.html()).append("</h3><span>").append(actionTitle.html()).append("</span></div>")
        output.append("<div class=\"shots\">")
        appendShot(output, "模型输入帧", inputFrame, marker)
        appendShot(output, "动作后帧", outputFrame, null)
        output.append("</div><div class=\"facts\">")
        request?.let { event ->
            val bytes = event.raw.optInt("requestBytes")
            output.append("<div><b>请求：</b>")
                .append(if (bytes > 0) "${bytes / 1024} KB" else event.type.html())
                .append("</div>")
        }
        transport?.details?.let { details ->
            output.append("<div><b>响应：</b>HTTP ").append(details.optInt("httpStatus"))
                .append(" · ").append(details.optLong("durationMs")).append(" ms · ")
                .append(details.optString("parseOutcome").html()).append("</div>")
        }
        result?.details?.let { details ->
            output.append("<div><b>执行：</b>").append(details.optString("message").html()).append("</div>")
        }
        output.append("</div>")
        appendEventList(output, turnEvents)
        output.append("</section>")
    }

    private fun appendShot(
        output: StringBuilder,
        label: String,
        frameFile: String,
        marker: Pair<Double, Double>?,
    ) {
        output.append("<div class=\"shot-card\"><b>").append(label.html()).append("</b>")
        if (frameFile.isBlank()) {
            output.append("<div class=\"empty\">没有关联截图</div>")
        } else {
            output.append("<div class=\"shot\"><img src=\"").append(frameFile.attribute())
                .append("\" alt=\"").append(label.attribute()).append("\">")
            marker?.let { (x, y) ->
                output.append("<span class=\"marker\" style=\"left:")
                    .append((x * 100.0).coerceIn(0.0, 100.0)).append("%;top:")
                    .append((y * 100.0).coerceIn(0.0, 100.0)).append("%\"></span>")
            }
            output.append("</div><small>").append(frameFile.html()).append("</small>")
        }
        output.append("</div>")
    }

    private fun appendEventList(output: StringBuilder, events: List<TraceEvent>) {
        events.forEach { event ->
            output.append("<details><summary>").append(event.index).append(" · ")
                .append(event.type.html()).append(" · ").append(event.capturedAt).append("</summary><pre>")
                .append(event.raw.toString(2).html()).append("</pre></details>")
        }
    }

    private fun JSONObject.marker(): Pair<Double, Double>? {
        val args = optJSONObject("toolArgs")
        val x = args?.optNullableDouble("__androidModelX") ?: args?.optNullableDouble("executionPermitX")
        val y = args?.optNullableDouble("__androidModelY") ?: args?.optNullableDouble("executionPermitY")
        return if (x != null && y != null) x to y else null
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return runCatching { getDouble(key) }.getOrNull() ?: optString(key).toDoubleOrNull()
    }

    private fun readJson(file: File): JSONObject? =
        runCatching { file.takeIf(File::isFile)?.readText()?.let(::JSONObject) }.getOrNull()

    private fun String.html(): String = replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun String.attribute(): String = html()

    private const val CSS = """
        :root{color-scheme:dark;font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}
        body{margin:0;background:#101116;color:#eef0f6}main{max-width:1180px;margin:auto;padding:24px}
        h1{margin:0 0 16px}h2{margin-top:30px}.meta,.turn,.finding{background:#191b23;border:1px solid #2d303b;border-radius:18px;padding:16px;margin:12px 0}
        .finding p{margin:8px 0 0;color:#c7cad4}.finding.error{border-color:#8e3d48}.finding.warning{border-color:#8a6d35}.finding.info{border-color:#3f5f83}.finding.ok{border-color:#3c7254}
        code{background:#272a34;padding:2px 6px;border-radius:7px}.turn-head{display:flex;gap:14px;align-items:baseline;flex-wrap:wrap}.turn-head h3{margin:0}
        .turn-head span{color:#c8cbd5}.shots{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:14px;margin:15px 0}
        .shot-card{background:#111218;border-radius:14px;padding:10px}.shot{position:relative;margin-top:8px}.shot img{display:block;width:100%;height:auto;border-radius:10px}
        .marker{position:absolute;width:18px;height:18px;border:3px solid #ff4d6d;border-radius:50%;transform:translate(-50%,-50%);box-shadow:0 0 0 4px rgba(0,0,0,.5)}
        .empty{height:160px;display:grid;place-items:center;color:#777}.facts{display:grid;gap:6px;color:#d4d6de;margin-bottom:12px}
        details{border-top:1px solid #2c2f38;padding:9px 0}summary{cursor:pointer;color:#bfc3cf}pre{white-space:pre-wrap;word-break:break-word;background:#0d0e12;padding:12px;border-radius:10px;max-height:520px;overflow:auto;font-size:12px}
        small{color:#777;word-break:break-all}@media(max-width:600px){main{padding:14px}.meta,.turn,.finding{padding:12px}}
    """
}

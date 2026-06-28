package com.yuchen.ailedger.service

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal object VisualIntelligenceDiagnosticsReport {
    private data class TraceEvent(
        val index: Int,
        val type: String,
        val turnId: String,
        val capturedAt: Long,
        val raw: JSONObject,
    ) {
        val details: JSONObject?
            get() = raw.optJSONObject("details")
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
                put("schema", "visual_intelligence_findings_v2")
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
        return trace.useLines { lines ->
            lines.mapIndexedNotNull { index, line ->
                val json = runCatching { JSONObject(line) }.getOrNull() ?: return@mapIndexedNotNull null
                TraceEvent(
                    index = index,
                    type = json.optString("type"),
                    turnId = json.optString("turnId"),
                    capturedAt = json.optLong("capturedAt"),
                    raw = json,
                )
            }.toList()
        }
    }

    private fun analyze(events: List<TraceEvent>): List<Finding> {
        val findings = mutableListOf<Finding>()
        val requests = events.filter { it.type == "model_request" }
        val responses = events.filter { it.type == "model_transport_response" }
        val responseTurns = responses.map { it.turnId }.filter(String::isNotBlank).toSet()

        requests.forEach { request ->
            val bytes = request.raw.optInt("requestBytes")
            if (bytes >= 350_000) {
                findings += Finding(
                    severity = "warning",
                    title = "模型请求体偏大",
                    detail = "本轮上传约 ${bytes / 1024} KB，可能显著增加视觉推理延迟。",
                    turnId = request.turnId,
                    eventIndex = request.index,
                )
            }
            if (request.turnId.isNotBlank() && request.turnId !in responseTurns) {
                findings += Finding(
                    severity = "error",
                    title = "模型请求缺少对应响应",
                    detail = "该轮已记录真实请求，但没有找到 HTTP 响应或失败终态，无法确定请求在哪一层中断。",
                    turnId = request.turnId,
                    eventIndex = request.index,
                )
            }
        }

        responses.forEach { response ->
            val details = response.details ?: JSONObject()
            val duration = details.optLong("durationMs")
            val status = details.optInt("httpStatus")
            if (duration >= 10_000L) {
                findings += Finding(
                    severity = "warning",
                    title = "模型请求耗时过长",
                    detail = "HTTP $status，本轮耗时 ${duration} ms。",
                    turnId = response.turnId,
                    eventIndex = response.index,
                )
            }
            if (status !in 200..299) {
                findings += Finding(
                    severity = "error",
                    title = "模型服务返回错误",
                    detail = "HTTP $status，解析状态：${details.optString("parseOutcome", "unknown")}。",
                    turnId = response.turnId,
                    eventIndex = response.index,
                )
            }
            if (!details.optBoolean("observationIdValid", status !in 200..299)) {
                findings += Finding(
                    severity = "error",
                    title = "模型响应绑定了错误观察帧",
                    detail = "响应未通过 observationId 新鲜度校验，旧坐标不能安全执行。",
                    turnId = response.turnId,
                    eventIndex = response.index,
                )
            }
        }

        events.filter { it.type == "action_validation" }.forEach { event ->
            val details = event.details ?: return@forEach
            if (!details.optBoolean("ok", true)) {
                findings += Finding(
                    severity = "error",
                    title = "动作在 Android 验证层被拒绝",
                    detail = "${details.optString("failureClass")}: ${details.optString("message")}",
                    turnId = event.turnId,
                    eventIndex = event.index,
                )
            }
        }

        events.filter { it.type == "tap_permit_rejected" }.forEach { event ->
            findings += Finding(
                severity = "error",
                title = "点击许可无效",
                detail = event.details?.optString("reason").orEmpty().ifBlank { "点击坐标没有通过独立 GUI 许可校验。" },
                turnId = event.turnId,
                eventIndex = event.index,
            )
        }

        events.filter { it.type == "action_freshness" }.forEach { event ->
            val details = event.details ?: return@forEach
            if (!details.optBoolean("fresh", true) || !details.optBoolean("verifiedWorkSurface", true)) {
                findings += Finding(
                    severity = "warning",
                    title = "动作执行前页面已经变化",
                    detail = "fresh=${details.optBoolean("fresh")}, workSurface=${details.optBoolean("verifiedWorkSurface")}, reason=${details.optString("reason")}",
                    turnId = event.turnId,
                    eventIndex = event.index,
                )
            }
        }

        events.filter { it.type == "execution_result" }.forEach { event ->
            val details = event.details ?: return@forEach
            val message = details.optString("message")
            val summary = details.optString("summary")
            if (message.contains("边界保护") || summary.contains("boundaryAdjusted=true")) {
                findings += Finding(
                    severity = "warning",
                    title = "点击坐标被边界保护调整",
                    detail = message.ifBlank { summary }.take(360),
                    turnId = event.turnId,
                    eventIndex = event.index,
                )
            }
            if (!details.optBoolean("ok", true)) {
                findings += Finding(
                    severity = "error",
                    title = "动作执行失败",
                    detail = message.take(360),
                    turnId = event.turnId,
                    eventIndex = event.index,
                )
            }
        }

        events.filter { it.type == "open_app_verification" }.forEach { event ->
            val details = event.details ?: return@forEach
            val verified = details.optBoolean("verified")
            val expected = details.optString("expectedPackage")
            val actual = details.optString("actualPackage")
            if (!verified && expected.isNotBlank() && expected == actual) {
                findings += Finding(
                    severity = "error",
                    title = "目标应用已在前台却验证失败",
                    detail = "expected 与 actual 都是 $expected，但稳定证明未完成，容易触发重复打开应用。",
                    turnId = event.turnId,
                    eventIndex = event.index,
                )
            } else if (!verified) {
                findings += Finding(
                    severity = "warning",
                    title = "目标应用前台验证失败",
                    detail = "expected=$expected, actual=$actual, reason=${details.optString("reason")}。",
                    turnId = event.turnId,
                    eventIndex = event.index,
                )
            }
        }

        val plannedOpenApps = events.filter { event ->
            event.type == "planned_action" && event.details?.optString("type") == "open_app"
        }
        plannedOpenApps.zipWithNext().forEach { (first, second) ->
            val firstPackage = first.details?.optString("packageName").orEmpty()
            val secondPackage = second.details?.optString("packageName").orEmpty()
            if (firstPackage.isNotBlank() && firstPackage == secondPackage) {
                findings += Finding(
                    severity = "warning",
                    title = "连续重复打开同一应用",
                    detail = "连续两轮都请求 open_app($firstPackage)，应检查工作面绑定或启动验证。",
                    turnId = second.turnId,
                    eventIndex = second.index,
                )
            }
        }

        val helpResults = events.filter { it.type == "user_help_result" && it.details?.optBoolean("replyProvided") == true }
        helpResults.forEach { result ->
            val nextHelp = events.firstOrNull {
                it.index > result.index && it.type == "user_help_requested" && it.index - result.index <= 30
            }
            if (nextHelp != null) {
                findings += Finding(
                    severity = "error",
                    title = "用户已回复后再次请求相同阶段协助",
                    detail = "用户回复没有被后续规划正确消费，需检查交互历史、模型响应和安全策略的责任归属。",
                    turnId = nextHelp.turnId,
                    eventIndex = nextHelp.index,
                )
            }
        }

        events.filter { it.type == "completion_protocol" }.forEach { event ->
            val details = event.details ?: return@forEach
            if (!details.optBoolean("valid", true)) {
                findings += Finding(
                    severity = "error",
                    title = "完成协议校验失败",
                    detail = "stage=${details.optString("stage")}, reason=${details.optString("reason")}。",
                    turnId = event.turnId,
                    eventIndex = event.index,
                )
            }
        }

        val visualFrames = events.filter { event ->
            event.type == "screen_observation" && event.raw.optJSONObject("visual")?.optString("frameFile").orEmpty().isNotBlank()
        }
        visualFrames.zipWithNext().forEach { (before, after) ->
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
                    severity = "warning",
                    title = "视觉画面明显变化但结构指纹未变化",
                    detail = "连续截图 dHash 距离为 $distance，但 structuralFingerprint 仍为 $beforeStructural，结构记忆可能无法区分页面。",
                    turnId = after.turnId,
                    eventIndex = after.index,
                )
            }
        }

        if (events.none { it.type == "model_request" }) {
            findings += Finding(
                severity = "error",
                title = "没有采集到真实模型请求",
                detail = "只能看到预算后的动作记忆，无法确认模型最终收到的完整 JSON。",
            )
        }
        if (events.none { it.type == "model_transport_response" }) {
            findings += Finding(
                severity = "error",
                title = "没有采集到原始模型响应",
                detail = "无法区分模型输出、解析器改写和 Android 策略拦截。",
            )
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
        appendLine("模型请求：${events.count { it.type == "model_request" }}")
        appendLine("模型响应：${events.count { it.type == "model_transport_response" }}")
        appendLine("视觉帧：${events.count { it.type == "screen_observation" && it.raw.optJSONObject("visual")?.optString("frameFile").orEmpty().isNotBlank() }}")
        appendLine()
        if (findings.isEmpty()) {
            appendLine("未检测到自动规则可识别的异常。仍需结合逐轮报告检查任务语义是否正确。")
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
        val requestTurns = events.filter { it.type == "model_request" }
        val turnIds = (requestTurns.map { it.turnId } + events.map { it.turnId })
            .filter(String::isNotBlank)
            .distinct()
        val unassigned = events.filter { it.turnId.isBlank() }
        return buildString {
            append("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
            append("<title>视觉智能诊断报告</title><style>")
            append(CSS)
            append("</style></head><body><main>")
            append("<h1>视觉智能诊断报告</h1>")
            append("<div class=\"meta\"><b>任务：</b>").append(summary.optString("goal").html())
            append("<br><b>任务 ID：</b>").append(summary.optLong("taskId"))
            append("<br><b>状态：</b>").append(summary.optString("status").html())
            append("<br><b>事件：</b>").append(events.size)
            append("　<b>请求：</b>").append(events.count { it.type == "model_request" })
            append("　<b>响应：</b>").append(events.count { it.type == "model_transport_response" })
            append("</div>")

            append("<h2>自动发现</h2>")
            if (findings.isEmpty()) {
                append("<div class=\"finding ok\">未检测到自动规则可识别的异常。</div>")
            } else {
                findings.forEach { finding ->
                    append("<div class=\"finding ").append(finding.severity.html()).append("\">")
                    append("<b>").append(finding.title.html()).append("</b>")
                    if (finding.turnId.isNotBlank()) append(" <code>").append(finding.turnId.html()).append("</code>")
                    append("<p>").append(finding.detail.html()).append("</p></div>")
                }
            }

            append("<h2>逐轮回放</h2>")
            turnIds.forEach { turnId -> appendTurn(this, turnId, events.filter { it.turnId == turnId }) }
            if (unassigned.isNotEmpty()) {
                append("<h2>任务级事件</h2>")
                appendEventList(this, unassigned)
            }
            append("</main></body></html>")
        }
    }

    private fun appendTurn(output: StringBuilder, turnId: String, events: List<TraceEvent>) {
        val request = events.firstOrNull { it.type == "model_request" }
        val response = events.firstOrNull { it.type == "model_transport_response" }
        val planned = events.lastOrNull { it.type == "planned_action" }
        val result = events.lastOrNull { it.type == "execution_result" }
        val observations = events.filter { it.type == "screen_observation" }
        val inputFrame = request?.raw?.optString("requestFrameFile").orEmpty()
        val outputFrame = observations.mapNotNull { it.raw.optJSONObject("visual")?.optString("frameFile") }
            .lastOrNull(String::isNotBlank).orEmpty()
        val marker = planned?.details?.marker()
        val actionTitle = planned?.details?.let { details ->
            listOf(details.optString("type"), details.optString("targetText")).filter(String::isNotBlank).joinToString(" · ")
        }.orEmpty().ifBlank {
            response?.details?.optString("parsedStepType").orEmpty().ifBlank { "未记录动作" }
        }

        output.append("<section class=\"turn\"><div class=\"turn-head\"><h3>")
            .append(turnId.html()).append("</h3><span>").append(actionTitle.html()).append("</span></div>")
        output.append("<div class=\"shots\">")
        appendShot(output, "模型输入帧", inputFrame, marker)
        appendShot(output, "动作后帧", outputFrame, null)
        output.append("</div>")

        output.append("<div class=\"facts\">")
        request?.let {
            output.append("<div><b>请求：</b>")
                .append((it.raw.optInt("requestBytes") / 1024).toString()).append(" KB · ")
                .append(it.raw.optString("observationId").html()).append("</div>")
        }
        response?.details?.let { details ->
            output.append("<div><b>响应：</b>HTTP ").append(details.optInt("httpStatus"))
                .append(" · ").append(details.optLong("durationMs")).append(" ms · ")
                .append(details.optString("parseOutcome").html()).append("</div>")
        }
        result?.details?.let { details ->
            output.append("<div><b>执行：</b>").append(details.optString("message").html()).append("</div>")
        }
        output.append("</div>")
        appendEventList(output, events)
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
            output.append("<div class=\"shot\"><img src=\"").append(frameFile.attribute()).append("\" alt=\"")
                .append(label.attribute()).append("\">")
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
        val normalizedX = args?.optNullableDouble("__androidModelX")
            ?: args?.optNullableDouble("executionPermitX")
        val normalizedY = args?.optNullableDouble("__androidModelY")
            ?: args?.optNullableDouble("executionPermitY")
        if (normalizedX != null && normalizedY != null) return normalizedX to normalizedY
        return null
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return runCatching { getDouble(key) }.getOrNull()
            ?: optString(key).toDoubleOrNull()
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
        .finding p{margin:8px 0 0;color:#c7cad4}.finding.error{border-color:#8e3d48}.finding.warning{border-color:#8a6d35}.finding.ok{border-color:#3c7254}
        code{background:#272a34;padding:2px 6px;border-radius:7px}.turn-head{display:flex;gap:14px;align-items:baseline;flex-wrap:wrap}.turn-head h3{margin:0}
        .turn-head span{color:#c8cbd5}.shots{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:14px;margin:15px 0}
        .shot-card{background:#111218;border-radius:14px;padding:10px}.shot{position:relative;margin-top:8px}.shot img{display:block;width:100%;height:auto;border-radius:10px}
        .marker{position:absolute;width:18px;height:18px;border:3px solid #ff4d6d;border-radius:50%;transform:translate(-50%,-50%);box-shadow:0 0 0 4px rgba(0,0,0,.5)}
        .empty{height:160px;display:grid;place-items:center;color:#777}.facts{display:grid;gap:6px;color:#d4d6de;margin-bottom:12px}
        details{border-top:1px solid #2c2f38;padding:9px 0}summary{cursor:pointer;color:#bfc3cf}pre{white-space:pre-wrap;word-break:break-word;background:#0d0e12;padding:12px;border-radius:10px;max-height:520px;overflow:auto;font-size:12px}
        small{color:#777;word-break:break-all}@media(max-width:600px){main{padding:14px}.meta,.turn,.finding{padding:12px}}
    """
}

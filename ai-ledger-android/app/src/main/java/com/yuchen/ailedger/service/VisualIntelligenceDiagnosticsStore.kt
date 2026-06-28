package com.yuchen.ailedger.service

import android.content.Context
import android.util.Base64
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val VISUAL_DIAGNOSTICS_PREFS = "visual_intelligence_diagnostics"
private const val VISUAL_DIAGNOSTICS_ENABLED = "enabled"
private const val VISUAL_DIAGNOSTICS_DIR = "visual_intelligence_diagnostics"
private const val VISUAL_DIAGNOSTICS_EXPORT_DIR = "visual-diagnostics-exports"
private const val MAX_DIAGNOSTIC_SESSIONS = 6
private const val MAX_FRAMES_PER_SESSION = 32
private const val MAX_TRACE_BYTES_PER_SESSION = 5_000_000L
private const val MAX_COPY_TEXT_CHARS = 900_000
private const val MAX_RESPONSE_TEXT_CHARS = 80_000

internal data class VisualDiagnosticSessionSummary(
    val taskId: Long,
    val goal: String,
    val status: String,
    val startedAt: Long,
    val updatedAt: Long,
    val endedAt: Long,
    val eventCount: Int,
    val observationCount: Int,
    val frameCount: Int,
    val latestAction: String,
    val latestResult: String,
)

internal data class VisualIntelligenceDiagnosticsState(
    val enabled: Boolean = true,
    val sessions: List<VisualDiagnosticSessionSummary> = emptyList(),
)

private data class SavedDiagnosticFrame(
    val file: File,
    val analysis: VisualDiagnosticFrameAnalysis,
)

private data class LatestDiagnosticFrame(
    val fileName: String,
    val capturedAt: Long,
    val displayWidth: Int,
    val displayHeight: Int,
)

/**
 * 智力升级阶段一的只读诊断黑匣子。
 *
 * 它只旁路记录已经发生的观察、模型请求/响应、验证、执行和完成协议，
 * 不参与动作选择、工作面判断、点击许可或完成许可。截图来自视觉循环已经采集的帧。
 */
internal class VisualIntelligenceDiagnosticsStore private constructor(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(VISUAL_DIAGNOSTICS_PREFS, Context.MODE_PRIVATE)
    private val rootDir = File(appContext.filesDir, VISUAL_DIAGNOSTICS_DIR)
    private val exportDir = File(appContext.cacheDir, VISUAL_DIAGNOSTICS_EXPORT_DIR)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "visual-intelligence-diagnostics").apply { isDaemon = true }
    }
    private val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(
        VisualIntelligenceDiagnosticsState(enabled = preferences.getBoolean(VISUAL_DIAGNOSTICS_ENABLED, true))
    )
    val state: StateFlow<VisualIntelligenceDiagnosticsState> = mutableState.asStateFlow()

    private val correlationLock = Any()
    private val turnSequenceByTask = mutableMapOf<Long, Int>()
    private val activeTurnByTask = mutableMapOf<Long, String>()
    private val latestFrameByTask = mutableMapOf<Long, LatestDiagnosticFrame>()

    @Volatile
    private var currentTaskId: Long = 0L
    private var lastProgressFingerprint: String = ""

    init {
        scope.launch {
            rootDir.mkdirs()
            exportDir.mkdirs()
            refreshStateLocked()
        }
    }

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(VISUAL_DIAGNOSTICS_ENABLED, enabled).apply()
        mutableState.value = mutableState.value.copy(enabled = enabled)
        if (!enabled) {
            synchronized(correlationLock) {
                turnSequenceByTask.clear()
                activeTurnByTask.clear()
                latestFrameByTask.clear()
            }
            currentTaskId = 0L
            lastProgressFingerprint = ""
        }
    }

    fun beginTask(taskId: Long, goal: String) {
        if (!mutableState.value.enabled || taskId <= 0L) return
        currentTaskId = taskId
        synchronized(correlationLock) {
            turnSequenceByTask[taskId] = 0
            activeTurnByTask.remove(taskId)
            latestFrameByTask.remove(taskId)
        }
        scope.launch {
            val session = ensureSessionLocked(taskId, goal, System.currentTimeMillis())
            updateSummaryLocked(session) { summary ->
                summary.put("goal", redactText(goal).take(240))
                summary.put("status", "准备执行")
            }
            appendEventLocked(
                session,
                JSONObject().apply {
                    put("schema", "visual_intelligence_task_lifecycle_v2")
                    put("type", "task_started")
                    put("taskId", taskId)
                    put("capturedAt", System.currentTimeMillis())
                    put("goal", redactText(goal).take(240))
                },
            )
            pruneLocked()
            refreshStateLocked()
        }
    }

    fun observeProgress(progress: AgentOverlayProgress) {
        if (progress.taskId > 0L) currentTaskId = progress.taskId
        if (!mutableState.value.enabled || progress.taskId <= 0L) return
        val fingerprint = listOf(
            progress.taskId,
            progress.running,
            progress.status,
            progress.currentAction,
            progress.lastResult,
            progress.pendingConfirmation?.id,
            progress.pendingUserInput?.id,
            progress.userTakeoverPaused,
            progress.logs.joinToString("\u001f"),
        ).joinToString("|")
        if (fingerprint == lastProgressFingerprint) return
        lastProgressFingerprint = fingerprint

        scope.launch {
            val explicitGoal = progress.logs.firstOrNull { it.startsWith("目标：") }
                ?.removePrefix("目标：")
                ?.trim()
                .orEmpty()
            val session = ensureSessionLocked(progress.taskId, explicitGoal, progress.updatedAt)
            val event = JSONObject().apply {
                put("schema", "visual_intelligence_progress_v2")
                put("type", "runtime_progress")
                put("taskId", progress.taskId)
                put("turnId", activeTurn(progress.taskId))
                put("capturedAt", System.currentTimeMillis())
                put("running", progress.running)
                put("status", redactText(progress.status))
                put("currentAction", redactText(progress.currentAction))
                put("lastResult", redactText(progress.lastResult))
                put("userTakeoverPaused", progress.userTakeoverPaused)
                put("pendingConfirmation", progress.pendingConfirmation?.let { item ->
                    JSONObject().apply {
                        put("title", redactText(item.title))
                        put("actionText", redactText(item.actionText))
                        put("message", redactText(item.message))
                    }
                } ?: JSONObject.NULL)
                put("pendingUserInput", progress.pendingUserInput?.let { item ->
                    JSONObject().apply {
                        put("title", redactText(item.title))
                        put("actionText", redactText(item.actionText))
                        put("message", redactText(item.message))
                        put("sensitive", item.sensitive)
                    }
                } ?: JSONObject.NULL)
                put("logs", JSONArray(progress.logs.map(::redactText)))
            }
            appendEventLocked(session, event)
            updateSummaryLocked(session) { summary ->
                if (explicitGoal.isNotBlank() && summary.optString("goal").isBlank()) {
                    summary.put("goal", redactText(explicitGoal).take(240))
                }
                summary.put("status", redactText(progress.status).take(80))
                summary.put("updatedAt", progress.updatedAt)
                summary.put("latestAction", redactText(progress.currentAction).take(240))
                summary.put("latestResult", redactText(progress.lastResult).take(300))
                if (!progress.running) summary.put("endedAt", progress.updatedAt)
            }
            pruneLocked()
            refreshStateLocked()
        }
    }

    fun recordObservation(
        forceVisual: Boolean,
        expectedPackage: String,
        observation: ScreenObservation,
    ) {
        if (!mutableState.value.enabled) return
        val taskId = resolveTaskId()
        if (taskId <= 0L) return
        val turnId = activeTurn(taskId)
        scope.launch {
            val session = ensureSessionLocked(taskId, "", observation.updatedAt)
            val frame = saveFrameLocked(session, observation)
            val visual = observation.visual
            if (frame != null && visual != null) {
                synchronized(correlationLock) {
                    latestFrameByTask[taskId] = LatestDiagnosticFrame(
                        fileName = frame.file.name,
                        capturedAt = visual.capturedAt,
                        displayWidth = visual.displayWidth,
                        displayHeight = visual.displayHeight,
                    )
                }
            }
            val structuralFingerprint = runCatching {
                VisualActionValidator.completionFingerprint(observation.toAgentScreenSnapshot())
            }.getOrDefault("")
            val event = JSONObject().apply {
                put("schema", "visual_intelligence_observation_v2")
                put("type", "screen_observation")
                put("taskId", taskId)
                put("turnId", turnId)
                put("capturedAt", System.currentTimeMillis())
                put("forceVisual", forceVisual)
                put("expectedPackage", expectedPackage.take(160))
                put("packageName", observation.packageName.take(160))
                put("windowTitle", redactText(observation.windowTitle).take(240))
                put("serviceConnected", observation.serviceConnected)
                put("accessibilityEnabled", observation.enabled)
                put("updatedAt", observation.updatedAt)
                put("structuralFingerprint", structuralFingerprint)
                put("visual", if (visual == null) JSONObject.NULL else JSONObject().apply {
                    put("available", visual.available)
                    put("hasImage", visual.hasImage)
                    put("mimeType", visual.mimeType)
                    put("width", visual.width)
                    put("height", visual.height)
                    put("displayWidth", visual.displayWidth)
                    put("displayHeight", visual.displayHeight)
                    put("source", visual.source.take(120))
                    put("reason", redactText(visual.reason).take(300))
                    put("capturedAt", visual.capturedAt)
                    put("frameFile", frame?.file?.name ?: JSONObject.NULL)
                    put("byteSize", frame?.analysis?.byteSize ?: 0)
                    put("sha256", frame?.analysis?.sha256 ?: "")
                    put("differenceHash", frame?.analysis?.differenceHash ?: "")
                })
            }
            appendEventLocked(session, event)
            updateSummaryLocked(session) { summary ->
                summary.put("updatedAt", System.currentTimeMillis())
                summary.put("observationCount", summary.optInt("observationCount") + 1)
                summary.put("frameCount", session.listFiles { file -> file.extension.equals("jpg", true) }?.size ?: 0)
                summary.put("latestPackage", observation.packageName.take(160))
            }
            refreshStateLocked()
        }
    }

    /** Records the exact payload that is about to be sent, with image bytes and sensitive fields removed. */
    fun recordModelRequestPayload(payload: JSONObject): String {
        if (!mutableState.value.enabled) return ""
        val taskId = resolveTaskId()
        if (taskId <= 0L) return ""
        val turnId = synchronized(correlationLock) {
            val next = (turnSequenceByTask[taskId] ?: 0) + 1
            turnSequenceByTask[taskId] = next
            "turn-${next.toString().padStart(3, '0')}"
                .also { activeTurnByTask[taskId] = it }
        }
        val copy = sanitizeJson(payload) as? JSONObject ?: JSONObject()
        val requestBytes = payload.toString().toByteArray(Charsets.UTF_8).size
        scope.launch {
            val session = ensureSessionLocked(taskId, copy.optString("goal"), System.currentTimeMillis())
            val frame = synchronized(correlationLock) { latestFrameByTask[taskId] }
            val event = JSONObject().apply {
                put("schema", "visual_intelligence_model_request_v2")
                put("type", "model_request")
                put("taskId", taskId)
                put("turnId", turnId)
                put("capturedAt", System.currentTimeMillis())
                put("requestBytes", requestBytes)
                put("observationId", copy.optString("observationId"))
                put("decisionOwner", copy.optString("visualDecisionOwner"))
                put("requestFrameFile", frame?.fileName ?: "")
                put("requestFrameCapturedAt", frame?.capturedAt ?: 0L)
                put("displayWidth", frame?.displayWidth ?: 0)
                put("displayHeight", frame?.displayHeight ?: 0)
                put("payload", copy)
            }
            appendEventLocked(session, event)
            updateSummaryLocked(session) { summary ->
                if (summary.optString("goal").isBlank() && copy.optString("goal").isNotBlank()) {
                    summary.put("goal", redactText(copy.optString("goal")).take(240))
                }
                summary.put("updatedAt", System.currentTimeMillis())
                summary.put("modelRequestCount", summary.optInt("modelRequestCount") + 1)
                summary.put("latestObservationId", copy.optString("observationId").take(180))
                summary.put("latestDecisionOwner", copy.optString("visualDecisionOwner").take(80))
            }
            refreshStateLocked()
        }
        return turnId
    }

    fun recordModelTransportResponse(
        httpStatus: Int,
        body: String,
        requestBytes: Int,
        responseBytes: Int,
        durationMs: Long,
        workerVersion: String,
        routeProtocol: String,
        parseOutcome: String,
        parsedStepType: String,
        observationIdValid: Boolean,
    ) {
        if (!mutableState.value.enabled) return
        val taskId = resolveTaskId()
        if (taskId <= 0L) return
        val turnId = activeTurn(taskId)
        val responseValue = sanitizeResponseBody(body)
        scope.launch {
            val session = ensureSessionLocked(taskId, "", System.currentTimeMillis())
            appendEventLocked(
                session,
                JSONObject().apply {
                    put("schema", "visual_intelligence_transport_v2")
                    put("type", "model_transport_response")
                    put("taskId", taskId)
                    put("turnId", turnId)
                    put("capturedAt", System.currentTimeMillis())
                    put("details", JSONObject().apply {
                        put("httpStatus", httpStatus)
                        put("requestBytes", requestBytes)
                        put("responseBytes", responseBytes)
                        put("durationMs", durationMs)
                        put("workerVersion", workerVersion.take(80))
                        put("routeProtocol", routeProtocol.take(80))
                        put("parseOutcome", parseOutcome.take(120))
                        put("parsedStepType", parsedStepType.take(80))
                        put("observationIdValid", observationIdValid)
                        put("responseBody", responseValue)
                    })
                },
            )
            updateSummaryLocked(session) { summary ->
                summary.put("modelResponseCount", summary.optInt("modelResponseCount") + 1)
                summary.put("modelRequestBytes", summary.optLong("modelRequestBytes") + requestBytes)
                summary.put("modelResponseBytes", summary.optLong("modelResponseBytes") + responseBytes)
                summary.put("modelDurationMs", summary.optLong("modelDurationMs") + durationMs)
                summary.put("updatedAt", System.currentTimeMillis())
            }
            refreshStateLocked()
        }
    }

    fun recordModelTransportFailure(
        code: String,
        message: String,
        durationMs: Long,
        requestBytes: Int = 0,
    ) {
        if (!mutableState.value.enabled) return
        val taskId = resolveTaskId()
        if (taskId <= 0L) return
        val turnId = activeTurn(taskId)
        scope.launch {
            val session = ensureSessionLocked(taskId, "", System.currentTimeMillis())
            appendEventLocked(
                session,
                JSONObject().apply {
                    put("schema", "visual_intelligence_transport_v2")
                    put("type", "model_transport_failure")
                    put("taskId", taskId)
                    put("turnId", turnId)
                    put("capturedAt", System.currentTimeMillis())
                    put("details", JSONObject().apply {
                        put("code", code.take(120))
                        put("message", redactText(message).take(2_000))
                        put("durationMs", durationMs)
                        put("requestBytes", requestBytes)
                    })
                },
            )
            updateSummaryLocked(session) { summary ->
                summary.put("modelFailureCount", summary.optInt("modelFailureCount") + 1)
                summary.put("updatedAt", System.currentTimeMillis())
            }
            refreshStateLocked()
        }
    }

    fun recordDiagnosticEvent(type: String, details: JSONObject) {
        if (!mutableState.value.enabled) return
        val taskId = resolveTaskId()
        if (taskId <= 0L) return
        val turnId = activeTurn(taskId)
        val safeDetails = sanitizeJson(details) as? JSONObject ?: JSONObject()
        scope.launch {
            val session = ensureSessionLocked(taskId, "", System.currentTimeMillis())
            appendEventLocked(
                session,
                JSONObject().apply {
                    put("schema", "visual_intelligence_event_v2")
                    put("type", type.take(80))
                    put("taskId", taskId)
                    put("turnId", turnId)
                    put("capturedAt", System.currentTimeMillis())
                    put("details", safeDetails)
                }
            )
            updateSummaryLocked(session) { summary ->
                summary.put("updatedAt", System.currentTimeMillis())
            }
            refreshStateLocked()
        }
    }

    fun clearAll() {
        scope.launch {
            rootDir.deleteRecursively()
            rootDir.mkdirs()
            exportDir.deleteRecursively()
            exportDir.mkdirs()
            synchronized(correlationLock) {
                turnSequenceByTask.clear()
                activeTurnByTask.clear()
                latestFrameByTask.clear()
            }
            currentTaskId = 0L
            lastProgressFingerprint = ""
            refreshStateLocked()
        }
    }

    suspend fun readSessionText(taskId: Long): String = withContext(dispatcher) {
        val session = sessionDir(taskId)
        if (!session.isDirectory) return@withContext "没有找到该诊断记录。"
        runCatching { VisualIntelligenceDiagnosticsReport.build(session) }
        buildString {
            append("视觉智能诊断（已脱敏）\n")
            append("任务 ID：").append(taskId).append("\n\n")
            val findings = File(session, "findings.txt")
            if (findings.isFile) {
                append(findings.readText()).append("\n\n")
            }
            val summary = File(session, "summary.json")
            if (summary.isFile) {
                append("任务摘要\n")
                append(runCatching { JSONObject(summary.readText()).toString(2) }.getOrDefault(summary.readText()))
                append("\n\n")
            }
            append("逐轮记录\n")
            val trace = File(session, "trace.jsonl")
            if (!trace.isFile) {
                append("暂无逐轮数据")
            } else {
                trace.useLines { lines ->
                    lines.forEach { line ->
                        if (length >= MAX_COPY_TEXT_CHARS) return@forEach
                        val formatted = runCatching { JSONObject(line).toString(2) }.getOrDefault(line)
                        append(formatted).append("\n\n")
                    }
                }
            }
            if (length >= MAX_COPY_TEXT_CHARS) append("\n[内容过长，已在复制文本中截断；ZIP 内保留完整记录]\n")
            val frames = session.listFiles { file -> file.extension.equals("jpg", true) }
                ?.sortedBy { it.name }
                .orEmpty()
            if (frames.isNotEmpty()) {
                append("\n截图文件\n")
                frames.forEach { append(it.name).append('\n') }
            }
        }.take(MAX_COPY_TEXT_CHARS)
    }

    suspend fun exportSession(taskId: Long): File? = withContext(dispatcher) {
        val session = sessionDir(taskId)
        if (!session.isDirectory) return@withContext null
        exportDir.mkdirs()
        runCatching { VisualIntelligenceDiagnosticsReport.build(session) }
        val output = File(exportDir, "visual-intelligence-task-${taskId}-${System.currentTimeMillis()}.zip")
        runCatching {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zip ->
                val note = buildString {
                    appendLine("视觉智能诊断包（阶段一增强版）")
                    appendLine("内容已进行敏感字段脱敏，截图来自视觉循环既有观察，不会额外截图。")
                    appendLine("解压后优先打开 report.html；findings.txt 是自动异常摘要；trace.jsonl 是完整逐轮数据。")
                }
                zip.putNextEntry(ZipEntry("README.txt"))
                zip.write(note.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                session.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relative = file.relativeTo(session).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(relative))
                    BufferedInputStream(FileInputStream(file)).use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            output
        }.getOrElse {
            output.delete()
            null
        }
    }

    private fun ensureSessionLocked(taskId: Long, goal: String, timestamp: Long): File {
        val session = sessionDir(taskId).apply { mkdirs() }
        val summaryFile = File(session, "summary.json")
        if (!summaryFile.isFile) {
            summaryFile.writeText(
                JSONObject().apply {
                    put("schema", "visual_intelligence_session_v2")
                    put("taskId", taskId)
                    put("goal", redactText(goal).take(240))
                    put("status", "执行中")
                    put("startedAt", timestamp.takeIf { it > 0L } ?: System.currentTimeMillis())
                    put("updatedAt", timestamp.takeIf { it > 0L } ?: System.currentTimeMillis())
                    put("endedAt", 0L)
                    put("eventCount", 0)
                    put("observationCount", 0)
                    put("frameCount", 0)
                    put("modelRequestCount", 0)
                    put("modelResponseCount", 0)
                    put("modelFailureCount", 0)
                    put("modelRequestBytes", 0L)
                    put("modelResponseBytes", 0L)
                    put("modelDurationMs", 0L)
                    put("latestAction", "")
                    put("latestResult", "")
                }.toString(2)
            )
        } else if (goal.isNotBlank()) {
            updateSummaryLocked(session) { summary ->
                if (summary.optString("goal").isBlank()) summary.put("goal", redactText(goal).take(240))
            }
        }
        return session
    }

    private fun appendEventLocked(session: File, event: JSONObject) {
        val trace = File(session, "trace.jsonl")
        if (trace.length() >= MAX_TRACE_BYTES_PER_SESSION) return
        trace.appendText(event.toString() + "\n")
        updateSummaryLocked(session) { summary ->
            summary.put("eventCount", summary.optInt("eventCount") + 1)
            summary.put("updatedAt", event.optLong("capturedAt", System.currentTimeMillis()))
        }
    }

    private fun updateSummaryLocked(session: File, update: (JSONObject) -> Unit) {
        val file = File(session, "summary.json")
        val summary = runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }
        update(summary)
        file.writeText(summary.toString(2))
    }

    private fun saveFrameLocked(session: File, observation: ScreenObservation): SavedDiagnosticFrame? {
        val visual = observation.visual?.takeIf { it.hasImage && it.base64Jpeg.isNotBlank() } ?: return null
        val existing = session.listFiles { file -> file.extension.equals("jpg", true) }.orEmpty()
        val hash = shortHash("${observation.packageName}|${visual.capturedAt}|${visual.base64Jpeg.take(96)}")
        val output = File(session, "frame_${visual.capturedAt}_$hash.jpg")
        if (output.isFile) {
            val bytes = runCatching { output.readBytes() }.getOrNull() ?: return null
            return SavedDiagnosticFrame(output, VisualDiagnosticFrameAnalyzer.analyze(bytes))
        }
        if (existing.size >= MAX_FRAMES_PER_SESSION) return null
        return runCatching {
            val bytes = Base64.decode(visual.base64Jpeg, Base64.DEFAULT)
            output.writeBytes(bytes)
            SavedDiagnosticFrame(output, VisualDiagnosticFrameAnalyzer.analyze(bytes))
        }.getOrElse {
            output.delete()
            null
        }
    }

    private fun refreshStateLocked() {
        rootDir.mkdirs()
        val summaries = rootDir.listFiles { file -> file.isDirectory && file.name.startsWith("task_") }
            .orEmpty()
            .mapNotNull { dir ->
                val file = File(dir, "summary.json")
                val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return@mapNotNull null
                VisualDiagnosticSessionSummary(
                    taskId = json.optLong("taskId"),
                    goal = json.optString("goal"),
                    status = json.optString("status", "未知"),
                    startedAt = json.optLong("startedAt"),
                    updatedAt = json.optLong("updatedAt"),
                    endedAt = json.optLong("endedAt"),
                    eventCount = json.optInt("eventCount"),
                    observationCount = json.optInt("observationCount"),
                    frameCount = json.optInt("frameCount"),
                    latestAction = json.optString("latestAction"),
                    latestResult = json.optString("latestResult"),
                )
            }
            .sortedByDescending { it.updatedAt }
        mutableState.value = VisualIntelligenceDiagnosticsState(
            enabled = preferences.getBoolean(VISUAL_DIAGNOSTICS_ENABLED, true),
            sessions = summaries,
        )
    }

    private fun pruneLocked() {
        val sessions = rootDir.listFiles { file -> file.isDirectory && file.name.startsWith("task_") }
            .orEmpty()
            .sortedByDescending { File(it, "summary.json").lastModified().coerceAtLeast(it.lastModified()) }
        sessions.drop(MAX_DIAGNOSTIC_SESSIONS).forEach { it.deleteRecursively() }
    }

    private fun resolveTaskId(): Long =
        AgentRuntimeController.currentTaskId().takeIf { it > 0L } ?: currentTaskId

    private fun activeTurn(taskId: Long): String = synchronized(correlationLock) {
        activeTurnByTask[taskId].orEmpty()
    }

    private fun sessionDir(taskId: Long): File = File(rootDir, "task_$taskId")

    private fun sanitizeResponseBody(body: String): Any {
        if (body.isBlank()) return ""
        val parsed = runCatching { JSONObject(body) }.getOrNull()
        return if (parsed != null) {
            sanitizeJson(parsed) ?: JSONObject.NULL
        } else {
            redactText(body.take(MAX_RESPONSE_TEXT_CHARS))
        }
    }

    private fun sanitizeJson(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> JSONObject.NULL
        is JSONObject -> JSONObject().apply {
            val inputAction = value.optString("type").equals("input_text", true) ||
                value.optString("stepType").equals("input_text", true)
            val keys = value.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val raw = value.opt(key)
                val sensitiveInputField = inputAction && key.lowercase() in setOf(
                    "text", "inputtext", "query", "content", "value", "toolargs",
                )
                when {
                    key.equals("base64Data", true) || key.equals("base64Jpeg", true) ->
                        put(key, "[图像已单独保存]")
                    key.contains("token", true) || key.contains("password", true) ||
                        key.contains("secret", true) || key.contains("authorization", true) ||
                        key.contains("cookie", true) -> put(key, "[敏感内容已隐藏]")
                    sensitiveInputField -> put(key, "[输入内容已隐藏]")
                    else -> put(key, sanitizeJson(raw))
                }
            }
        }
        is JSONArray -> JSONArray().apply {
            for (index in 0 until value.length()) put(sanitizeJson(value.opt(index)))
        }
        is String -> redactText(value)
        else -> value
    }

    private fun redactText(input: String): String {
        if (input.isBlank()) return input
        if (
            input.contains("private_step", true) ||
            input.contains("敏感输入", true) ||
            input.contains("PRIVATE_COMPLETION_TOKEN", true)
        ) return "[敏感内容已隐藏]"
        var value = input
        val patterns = listOf(
            Regex("(?i)(password|passcode|verification\\s*code|otp|pin)\\s*[:=：]?\\s*[^\\s|,，;；]{2,}"),
            Regex("(密码|验证码|支付密码|口令)\\s*[:=：]?\\s*[^\\s|,，;；]{2,}"),
        )
        patterns.forEach { pattern -> value = value.replace(pattern) { "${it.groupValues[1]}：[敏感内容已隐藏]" } }
        return value.take(20_000)
    }

    private fun shortHash(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
            .take(12)
    }

    companion object {
        @Volatile
        private var instance: VisualIntelligenceDiagnosticsStore? = null

        fun get(context: Context): VisualIntelligenceDiagnosticsStore {
            return instance ?: synchronized(this) {
                instance ?: VisualIntelligenceDiagnosticsStore(context).also { instance = it }
            }
        }

        fun currentOrNull(): VisualIntelligenceDiagnosticsStore? = instance
    }
}

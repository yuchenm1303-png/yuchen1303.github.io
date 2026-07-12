package com.yuchen.ailedger.service

import android.content.Context
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

private const val CLIENT_TOOL_LEDGER_SCHEMA = "ai_ledger_client_tool_execution_ledger_v1"
private const val CLIENT_TOOL_LEDGER_DIR = "client-tool-execution-ledger-v1"
private const val CLIENT_TOOL_LEDGER_RETENTION_MS = 7L * 24L * 60L * 60L * 1000L
private const val CLIENT_TOOL_LEDGER_MAX_RECORDS = 256

/**
 * Persistent at-most-once gate for client tools that mutate durable local state.
 *
 * The inflight marker is committed before execution. A repeated completed call replays the original
 * receipt. A repeated inflight call is stopped with an explicit unknown-state result instead of
 * risking a duplicate project creation, revision, deletion or rollback.
 */
internal class ClientToolExecutionLedger private constructor(
    private val rootDir: File,
) {
    constructor(context: Context) : this(File(context.applicationContext.filesDir, CLIENT_TOOL_LEDGER_DIR))

    init {
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            throw IllegalStateException("无法创建客户端工具幂等账本。")
        }
    }

    fun begin(call: CloudClientToolCall): ClientToolExecutionDecision = synchronized(lock) {
        pruneLocked()
        val argumentsHash = argumentsHash(call)
        val file = recordFile(call.id)
        if (file.isFile) {
            val existing = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
            if (existing == null) {
                return@synchronized ClientToolExecutionDecision.Reject(
                    code = "tool_execution_ledger_corrupt",
                    summary = "客户端工具幂等记录损坏，已停止重复执行以保护本地项目。",
                )
            }
            if (
                existing.optString("toolCallId") != call.id ||
                existing.optString("toolName") != call.name ||
                existing.optString("argumentsHash") != argumentsHash
            ) {
                return@synchronized ClientToolExecutionDecision.Reject(
                    code = "tool_call_id_conflict",
                    summary = "相同 toolCallId 对应了不同的工具或参数，已拒绝执行。",
                )
            }
            return@synchronized when (existing.optString("status")) {
                "completed" -> {
                    val receipt = existing.optJSONObject("receipt")
                    if (receipt == null) {
                        ClientToolExecutionDecision.Reject(
                            code = "tool_execution_receipt_missing",
                            summary = "工具已执行，但幂等记录缺少原始回执，已停止重复执行。",
                        )
                    } else {
                        ClientToolExecutionDecision.Replay(
                            JSONObject(receipt.toString()).put("idempotentReplay", true),
                        )
                    }
                }
                "inflight" -> ClientToolExecutionDecision.Reject(
                    code = "tool_execution_state_unknown",
                    summary = "该客户端工具此前已开始执行，但没有留下最终回执。为避免重复副作用，本次不会再次执行。",
                )
                else -> ClientToolExecutionDecision.Reject(
                    code = "tool_execution_ledger_invalid",
                    summary = "客户端工具幂等记录状态无效，已停止执行。",
                )
            }
        }

        val now = System.currentTimeMillis()
        val record = JSONObject().apply {
            put("schema", CLIENT_TOOL_LEDGER_SCHEMA)
            put("toolCallId", call.id)
            put("toolName", call.name)
            put("argumentsHash", argumentsHash)
            put("status", "inflight")
            put("startedAt", now)
            put("updatedAt", now)
        }
        writeJsonAtomically(file, record)
        ClientToolExecutionDecision.Execute
    }

    fun complete(call: CloudClientToolCall, receipt: JSONObject) = synchronized(lock) {
        val file = recordFile(call.id)
        val now = System.currentTimeMillis()
        val record = JSONObject().apply {
            put("schema", CLIENT_TOOL_LEDGER_SCHEMA)
            put("toolCallId", call.id)
            put("toolName", call.name)
            put("argumentsHash", argumentsHash(call))
            put("status", "completed")
            put("startedAt", runCatching {
                if (file.isFile) JSONObject(file.readText(Charsets.UTF_8)).optLong("startedAt", now) else now
            }.getOrDefault(now))
            put("completedAt", now)
            put("updatedAt", now)
            put("receipt", JSONObject(receipt.toString()))
        }
        writeJsonAtomically(file, record)
        pruneLocked()
    }

    private fun recordFile(toolCallId: String): File = File(rootDir, "${sha256(toolCallId)}.json")

    private fun argumentsHash(call: CloudClientToolCall): String = sha256(
        buildString {
            append(call.name)
            append('\n')
            append(canonicalJson(call.arguments))
        },
    )

    private fun canonicalJson(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { key ->
            "${JSONObject.quote(key)}:${canonicalJson(value.opt(key))}"
        }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { index ->
            canonicalJson(value.opt(index))
        }
        is String -> JSONObject.quote(value)
        is Boolean, is Number -> value.toString()
        else -> JSONObject.quote(value.toString())
    }

    private fun writeJsonAtomically(target: File, value: JSONObject) {
        val temp = File(rootDir, ".${target.name}.${System.nanoTime()}.tmp")
        temp.writeText(value.toString(), Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private fun pruneLocked() {
        val now = System.currentTimeMillis()
        val files = rootDir.listFiles().orEmpty().filter { it.isFile && it.extension == "json" }
        files.forEach { file ->
            val updatedAt = runCatching { JSONObject(file.readText(Charsets.UTF_8)).optLong("updatedAt", file.lastModified()) }
                .getOrDefault(file.lastModified())
            if (now - updatedAt > CLIENT_TOOL_LEDGER_RETENTION_MS) file.delete()
        }
        rootDir.listFiles().orEmpty()
            .filter { it.isFile && it.extension == "json" }
            .sortedByDescending(File::lastModified)
            .drop(CLIENT_TOOL_LEDGER_MAX_RECORDS)
            .forEach(File::delete)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    companion object {
        private val lock = Any()

        internal fun createForTest(rootDir: File): ClientToolExecutionLedger = ClientToolExecutionLedger(rootDir)
    }
}

internal sealed interface ClientToolExecutionDecision {
    object Execute : ClientToolExecutionDecision
    data class Replay(val receipt: JSONObject) : ClientToolExecutionDecision
    data class Reject(val code: String, val summary: String) : ClientToolExecutionDecision
}

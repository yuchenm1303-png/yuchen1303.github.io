package com.yuchen.ailedger.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

private const val CLIENT_TOOL_RECEIPT_OUTBOX_SCHEMA = "ai_ledger_client_tool_receipt_outbox_v1"
private const val CLIENT_TOOL_RECEIPT_OUTBOX_DIR = "client-tool-receipt-outbox-v1"
private const val CLIENT_TOOL_RECEIPT_MARKER = "[[AI_LEDGER_CLIENT_TOOL_RESULT_V1]]"
private const val CLIENT_TOOL_RECEIPT_WORK_TAG = "ai-ledger-client-tool-receipt"
private const val CLIENT_TOOL_RECEIPT_INPUT_ID = "toolCallId"
private const val CLIENT_TOOL_RECEIPT_RETENTION_MS = 7L * 24L * 60L * 60L * 1000L
private const val CLIENT_TOOL_RECEIPT_LEASE_MS = 90_000L
private const val CLIENT_TOOL_RECEIPT_MAX_RECORDS = 256
private const val CLIENT_TOOL_RECEIPT_MAX_ATTEMPTS = 8

internal data class PendingClientToolReceipt(
    val toolCallId: String,
    val receipt: JSONObject,
    val finalModel: String,
    val attemptCount: Int,
)

internal sealed interface ClientToolReceiptClaim {
    data class Ready(val pending: PendingClientToolReceipt) : ClientToolReceiptClaim
    object Wait : ClientToolReceiptClaim
    object Done : ClientToolReceiptClaim
}

/**
 * Durable outbox for client-tool receipts.
 *
 * Tool execution and receipt delivery are intentionally separate. Executors commit local state and
 * their idempotent execution receipt first; this outbox then guarantees that the exact receipt can
 * be retried after a transient network failure or process restart without executing the tool again.
 */
internal class ClientToolReceiptOutbox private constructor(
    private val rootDir: File,
) {
    constructor(context: Context) : this(
        File(context.applicationContext.filesDir, CLIENT_TOOL_RECEIPT_OUTBOX_DIR),
    )

    init {
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            throw IllegalStateException("无法创建客户端工具回执队列。")
        }
    }

    fun enqueue(receipt: JSONObject): String = synchronized(lock) {
        pruneLocked()
        val toolCallId = receipt.optString("toolCallId").trim().take(120)
        require(toolCallId.isNotBlank()) { "客户端工具回执缺少 toolCallId。" }
        val receiptCopy = JSONObject(receipt.toString())
        val receiptHash = sha256(canonicalJson(receiptCopy))
        val target = recordFile(toolCallId)
        val existing = readRecord(target)
        if (existing != null) {
            if (existing.optString("receiptHash") != receiptHash) {
                throw IllegalStateException("相同 toolCallId 对应了不同回执，已拒绝覆盖。")
            }
            val status = existing.optString("status")
            if (status == "reported" || status == "abandoned") return@synchronized toolCallId
            existing.put("receipt", receiptCopy)
            existing.put("updatedAt", System.currentTimeMillis())
            writeJsonAtomically(target, existing)
            return@synchronized toolCallId
        }

        val now = System.currentTimeMillis()
        val record = JSONObject().apply {
            put("schema", CLIENT_TOOL_RECEIPT_OUTBOX_SCHEMA)
            put("toolCallId", toolCallId)
            put("receiptHash", receiptHash)
            put("status", "pending")
            put("receipt", receiptCopy)
            put("finalModel", receiptCopy.optString("finalModel").trim().take(80))
            put("attemptCount", 0)
            put("nextAttemptAt", now)
            put("leaseUntil", 0L)
            put("createdAt", now)
            put("updatedAt", now)
        }
        writeJsonAtomically(target, record)
        pruneLocked()
        toolCallId
    }

    fun claim(toolCallId: String, now: Long = System.currentTimeMillis()): ClientToolReceiptClaim = synchronized(lock) {
        val target = recordFile(toolCallId)
        val record = readRecord(target) ?: return@synchronized ClientToolReceiptClaim.Done
        when (record.optString("status")) {
            "reported", "abandoned" -> return@synchronized ClientToolReceiptClaim.Done
        }
        if (record.optLong("nextAttemptAt", 0L) > now || record.optLong("leaseUntil", 0L) > now) {
            return@synchronized ClientToolReceiptClaim.Wait
        }
        val nextAttempt = record.optInt("attemptCount", 0) + 1
        if (nextAttempt > CLIENT_TOOL_RECEIPT_MAX_ATTEMPTS) {
            record.put("status", "abandoned")
            record.put("leaseUntil", 0L)
            record.put("updatedAt", now)
            writeJsonAtomically(target, record)
            return@synchronized ClientToolReceiptClaim.Done
        }
        val receipt = record.optJSONObject("receipt")
            ?: run {
                record.put("status", "abandoned")
                record.put("lastError", "receipt_missing")
                record.put("updatedAt", now)
                writeJsonAtomically(target, record)
                return@synchronized ClientToolReceiptClaim.Done
            }
        record.put("status", "delivering")
        record.put("attemptCount", nextAttempt)
        record.put("leaseUntil", now + CLIENT_TOOL_RECEIPT_LEASE_MS)
        record.put("updatedAt", now)
        writeJsonAtomically(target, record)
        ClientToolReceiptClaim.Ready(
            PendingClientToolReceipt(
                toolCallId = toolCallId,
                receipt = JSONObject(receipt.toString()),
                finalModel = record.optString("finalModel").trim(),
                attemptCount = nextAttempt,
            ),
        )
    }

    fun release(toolCallId: String, error: Throwable, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        val target = recordFile(toolCallId)
        val record = readRecord(target) ?: return@synchronized
        if (record.optString("status") == "reported") return@synchronized
        val attempt = record.optInt("attemptCount", 1).coerceAtLeast(1)
        if (attempt >= CLIENT_TOOL_RECEIPT_MAX_ATTEMPTS) {
            record.put("status", "abandoned")
            record.put("nextAttemptAt", 0L)
        } else {
            record.put("status", "pending")
            record.put("nextAttemptAt", now + retryDelayMillis(attempt))
        }
        record.put("leaseUntil", 0L)
        record.put("lastError", error.message.orEmpty().take(300))
        record.put("updatedAt", now)
        writeJsonAtomically(target, record)
    }

    fun acknowledge(toolCallId: String, responseSummary: String = "", now: Long = System.currentTimeMillis()) = synchronized(lock) {
        val target = recordFile(toolCallId)
        val record = readRecord(target) ?: return@synchronized
        record.put("status", "reported")
        record.put("leaseUntil", 0L)
        record.put("nextAttemptAt", 0L)
        record.put("reportedAt", now)
        record.put("updatedAt", now)
        if (responseSummary.isNotBlank()) record.put("responseSummary", responseSummary.take(500))
        writeJsonAtomically(target, record)
        pruneLocked()
    }

    fun pendingToolCallIds(now: Long = System.currentTimeMillis()): List<String> = synchronized(lock) {
        pruneLocked()
        rootDir.listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension == "json" }
            .mapNotNull(::readRecord)
            .filter { record ->
                record.optString("status") in setOf("pending", "delivering") &&
                    record.optLong("nextAttemptAt", 0L) <= now
            }
            .sortedBy { it.optLong("createdAt", 0L) }
            .map { it.optString("toolCallId") }
            .filter(String::isNotBlank)
            .take(CLIENT_TOOL_RECEIPT_MAX_RECORDS)
            .toList()
    }

    internal fun statusForTest(toolCallId: String): String? = synchronized(lock) {
        readRecord(recordFile(toolCallId))?.optString("status")
    }

    private fun recordFile(toolCallId: String): File = File(rootDir, "${sha256(toolCallId)}.json")

    private fun readRecord(file: File): JSONObject? = runCatching {
        file.takeIf(File::isFile)?.readText(Charsets.UTF_8)?.let(::JSONObject)
    }.getOrNull()

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
        val records = rootDir.listFiles().orEmpty().filter { it.isFile && it.extension == "json" }
        records.forEach { file ->
            val record = readRecord(file)
            val updatedAt = record?.optLong("updatedAt", file.lastModified()) ?: file.lastModified()
            val terminal = record?.optString("status") in setOf("reported", "abandoned")
            if (terminal && now - updatedAt > CLIENT_TOOL_RECEIPT_RETENTION_MS) file.delete()
        }
        rootDir.listFiles().orEmpty()
            .filter { it.isFile && it.extension == "json" }
            .sortedByDescending(File::lastModified)
            .drop(CLIENT_TOOL_RECEIPT_MAX_RECORDS)
            .forEach(File::delete)
    }

    private fun retryDelayMillis(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, 6)
        return (10_000L shl exponent).coerceAtMost(10L * 60L * 1000L)
    }

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

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    companion object {
        private val lock = Any()

        internal fun createForTest(rootDir: File): ClientToolReceiptOutbox = ClientToolReceiptOutbox(rootDir)
    }
}

internal object ClientToolReceiptDeliveryRuntime {
    fun enqueue(context: Context, receipt: JSONObject) {
        val appContext = context.applicationContext
        val toolCallId = ClientToolReceiptOutbox(appContext).enqueue(receipt)
        schedule(appContext, toolCallId)
    }

    fun acknowledgeSuccessfulPayload(context: Context, payload: JSONObject, responseSummary: String = "") {
        val toolCallId = toolCallIdFromReportPayload(payload) ?: return
        ClientToolReceiptOutbox(context.applicationContext).acknowledge(toolCallId, responseSummary)
    }

    fun reschedulePending(context: Context) {
        val appContext = context.applicationContext
        ClientToolReceiptOutbox(appContext).pendingToolCallIds().forEach { schedule(appContext, it) }
    }

    internal fun toolCallIdFromReportPayload(payload: JSONObject): String? {
        if (payload.optString("action") != "internal_control_report") return null
        return payload.optJSONObject("internalControlReceipt")
            ?.optString("toolCallId")
            ?.trim()
            ?.take(120)
            ?.takeIf(String::isNotBlank)
    }

    private fun schedule(context: Context, toolCallId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val input = Data.Builder()
            .putString(CLIENT_TOOL_RECEIPT_INPUT_ID, toolCallId)
            .build()
        val request = OneTimeWorkRequest.Builder(ClientToolReceiptReportWorker::class.java)
            .setConstraints(constraints)
            .setInitialDelay(8L, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10L, TimeUnit.SECONDS)
            .setInputData(input)
            .addTag(CLIENT_TOOL_RECEIPT_WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName(toolCallId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun uniqueWorkName(toolCallId: String): String = buildString {
        append(CLIENT_TOOL_RECEIPT_WORK_TAG)
        append('-')
        append(
            MessageDigest.getInstance("SHA-256")
                .digest(toolCallId.toByteArray(Charsets.UTF_8))
                .take(12)
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) },
        )
    }
}

class ClientToolReceiptReportWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {
    override fun doWork(): Result {
        val toolCallId = inputData.getString(CLIENT_TOOL_RECEIPT_INPUT_ID)?.trim().orEmpty()
        if (toolCallId.isBlank()) return Result.failure()
        val outbox = ClientToolReceiptOutbox(applicationContext)
        return when (val claim = outbox.claim(toolCallId)) {
            ClientToolReceiptClaim.Done -> Result.success()
            ClientToolReceiptClaim.Wait -> Result.retry()
            is ClientToolReceiptClaim.Ready -> deliver(outbox, claim.pending)
        }
    }

    private fun deliver(
        outbox: ClientToolReceiptOutbox,
        pending: PendingClientToolReceipt,
    ): Result {
        return try {
            val model = pending.finalModel
                .takeIf(String::isNotBlank)
                ?.let(ChatModel::fromId)
                ?: ChatModel.Auto
            val response = AiWorkerClient().sendChat(
                messages = listOf(
                    ChatMessage(
                        id = "client-tool-outbox-${pending.toolCallId}",
                        text = "$CLIENT_TOOL_RECEIPT_MARKER${pending.receipt}",
                        role = MessageRole.User,
                    ),
                ),
                modelPreference = model,
                onlineEnabled = false,
            )
            outbox.acknowledge(pending.toolCallId, response.reply)
            Result.success()
        } catch (error: IOException) {
            outbox.release(pending.toolCallId, error)
            if (pending.attemptCount >= CLIENT_TOOL_RECEIPT_MAX_ATTEMPTS) Result.failure() else Result.retry()
        } catch (error: Throwable) {
            outbox.release(pending.toolCallId, error)
            if (pending.attemptCount >= CLIENT_TOOL_RECEIPT_MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }
}

package com.yuchen.ailedger.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.yuchen.ailedger.service.OperationAccessibilityEventRecord
import com.yuchen.ailedger.service.OperationNodeEvidence
import com.yuchen.ailedger.service.OperationNodeSnapshotRecord
import com.yuchen.ailedger.service.OperationRecordingMarkerRecord
import com.yuchen.ailedger.service.OperationTraceRecord
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.BufferOverflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class OperationTraceStore(private val context: Context) {
    fun openSession(
        demonstrationId: String,
        workflowId: String,
        startedAtMillis: Long,
    ): OperationTraceWriter {
        require(demonstrationId.isNotBlank()) { "demonstrationId must not be blank" }
        require(workflowId.isNotBlank()) { "workflowId must not be blank" }
        cleanupExpiredFiles()
        val directory = File(context.noBackupFilesDir, TRACE_DIRECTORY).apply { mkdirs() }
        val file = File(directory, "$demonstrationId.trace")
        return OperationTraceWriter(
            file = file,
            sessionId = demonstrationId,
            key = getOrCreateKey(),
            header = OperationRecordingMarkerRecord(
                capturedAtMillis = startedAtMillis,
                marker = "session_started",
                detail = "workflow=$workflowId;format=$TRACE_FORMAT_VERSION",
            ),
        )
    }

    fun deleteTrace(path: String?) {
        val file = path?.takeIf(String::isNotBlank)?.let(::File) ?: return
        if (!file.isInsideTraceDirectory()) return
        runCatching { file.delete() }
    }

    fun cleanupExpiredFiles(nowMillis: Long = System.currentTimeMillis()) {
        val directory = File(context.noBackupFilesDir, TRACE_DIRECTORY)
        directory.listFiles().orEmpty().forEach { file ->
            if (!file.isFile) return@forEach
            if (nowMillis - file.lastModified() > TRACE_RETENTION_MS) {
                runCatching { file.delete() }
            }
        }
    }

    private fun File.isInsideTraceDirectory(): Boolean {
        val traceRoot = File(context.noBackupFilesDir, TRACE_DIRECTORY).canonicalFile
        val candidate = canonicalFile
        return candidate.parentFile == traceRoot
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "ai_ledger_operation_trace_v1"
        private const val TRACE_DIRECTORY = "operation-traces"
        private const val TRACE_FORMAT_VERSION = 1
        private const val TRACE_RETENTION_MS = 24L * 60L * 60L * 1_000L
    }
}

class OperationTraceWriter internal constructor(
    val file: File,
    private val sessionId: String,
    private val key: SecretKey,
    header: OperationTraceRecord,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val records = Channel<OperationTraceRecord>(
        capacity = MAX_BUFFERED_RECORDS,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val writerJob = scope.launch {
        DataOutputStream(BufferedOutputStream(FileOutputStream(file, false))).use { output ->
            writeEncryptedRecord(output, header)
            for (record in records) {
                if (file.length() >= MAX_TRACE_BYTES) break
                writeEncryptedRecord(output, record)
            }
            output.flush()
        }
    }

    val path: String
        get() = file.absolutePath

    fun append(record: OperationTraceRecord): Boolean {
        if (!writerJob.isActive) return false
        return records.trySend(record).isSuccess
    }

    suspend fun close(finalRecord: OperationTraceRecord? = null) {
        finalRecord?.let(::append)
        records.close()
        writerJob.join()
        scope.cancel()
    }

    private fun writeEncryptedRecord(
        output: DataOutputStream,
        record: OperationTraceRecord,
    ) {
        val plain = record.toJson().toString().toByteArray(Charsets.UTF_8)
        if (plain.size > MAX_RECORD_BYTES) return
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(sessionId.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(plain)
        val iv = cipher.iv
        val frameSize = 1 + iv.size + encrypted.size
        output.writeInt(frameSize)
        output.writeByte(iv.size)
        output.write(iv)
        output.write(encrypted)
    }

    private fun OperationTraceRecord.toJson(): JSONObject = when (this) {
        is OperationAccessibilityEventRecord -> JSONObject().apply {
            put("kind", "accessibility_event")
            put("capturedAtMillis", capturedAtMillis)
            put("eventType", eventType)
            put("eventTypeLabel", eventTypeLabel)
            put("packageName", packageName)
            put("className", className ?: JSONObject.NULL)
            put("windowTitle", windowTitle ?: JSONObject.NULL)
            put("contentChangeTypes", contentChangeTypes)
            put("source", source?.toJson() ?: JSONObject.NULL)
            put("eventText", eventText ?: JSONObject.NULL)
            put("inputLengthBucket", inputLengthBucket ?: JSONObject.NULL)
            put("redactionApplied", redactionApplied)
        }

        is OperationNodeSnapshotRecord -> JSONObject().apply {
            put("kind", "node_snapshot")
            put("capturedAtMillis", capturedAtMillis)
            put("packageName", packageName)
            put("windowTitle", windowTitle ?: JSONObject.NULL)
            put("rawNodeCount", rawNodeCount)
            put("truncated", truncated)
            put("nodes", JSONArray().apply { nodes.forEach { put(it.toJson()) } })
        }

        is OperationRecordingMarkerRecord -> JSONObject().apply {
            put("kind", "marker")
            put("capturedAtMillis", capturedAtMillis)
            put("marker", marker)
            put("detail", detail ?: JSONObject.NULL)
        }
    }

    private fun OperationNodeEvidence.toJson(): JSONObject = JSONObject().apply {
        put("viewId", viewId ?: JSONObject.NULL)
        put("className", className ?: JSONObject.NULL)
        put("role", role ?: JSONObject.NULL)
        put("text", text ?: JSONObject.NULL)
        put("contentDescription", contentDescription ?: JSONObject.NULL)
        put("hint", hint ?: JSONObject.NULL)
        put("bounds", bounds ?: JSONObject.NULL)
        put("screenWidth", screenWidth)
        put("screenHeight", screenHeight)
        put("clickable", clickable)
        put("longClickable", longClickable)
        put("editable", editable)
        put("scrollable", scrollable)
        put("password", password)
        put("sensitive", sensitive)
        put("inputLengthBucket", inputLengthBucket ?: JSONObject.NULL)
        put("riskHints", JSONArray().apply { riskHints.sorted().forEach(::put) })
    }

    companion object {
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val MAX_BUFFERED_RECORDS = 256
        private const val MAX_RECORD_BYTES = 512 * 1024
        private const val MAX_TRACE_BYTES = 8L * 1024L * 1024L
    }
}

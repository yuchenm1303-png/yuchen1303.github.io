package com.yuchen.ailedger.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.yuchen.ailedger.service.OperationAccessibilityEventRecord
import com.yuchen.ailedger.service.OperationNodeEvidence
import com.yuchen.ailedger.service.OperationNodeSnapshotRecord
import com.yuchen.ailedger.service.OperationRecordingMarkerRecord
import com.yuchen.ailedger.service.OperationTraceRecord
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
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

    fun readSession(
        path: String,
        demonstrationId: String,
        maxRecords: Int = DEFAULT_MAX_READ_RECORDS,
    ): List<OperationTraceRecord> {
        require(demonstrationId.isNotBlank()) { "demonstrationId must not be blank" }
        require(maxRecords in 1..MAX_READ_RECORDS) { "maxRecords out of range" }
        val file = File(path)
        require(file.isInsideTraceDirectory()) { "trace path outside private directory" }
        require(file.isFile) { "trace file not found" }
        require(file.length() in 1..MAX_TRACE_FILE_BYTES) { "trace file size invalid" }

        val key = getOrCreateKey()
        val records = mutableListOf<OperationTraceRecord>()
        DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            while (records.size < maxRecords) {
                val frameSize = try {
                    input.readInt()
                } catch (_: EOFException) {
                    break
                }
                require(frameSize in MIN_ENCRYPTED_FRAME_BYTES..MAX_ENCRYPTED_FRAME_BYTES) {
                    "encrypted trace frame size invalid"
                }
                val ivSize = input.readUnsignedByte()
                require(ivSize in MIN_GCM_IV_BYTES..MAX_GCM_IV_BYTES) { "encrypted trace IV invalid" }
                val encryptedSize = frameSize - FRAME_IV_LENGTH_BYTES - ivSize
                require(encryptedSize >= GCM_TAG_BYTES) { "encrypted trace payload invalid" }

                val iv = ByteArray(ivSize)
                input.readFully(iv)
                val encrypted = ByteArray(encryptedSize)
                input.readFully(encrypted)

                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.updateAAD(demonstrationId.toByteArray(Charsets.UTF_8))
                val plain = cipher.doFinal(encrypted)
                require(plain.size <= MAX_RECORD_BYTES) { "decrypted trace record too large" }
                parseRecord(JSONObject(plain.toString(Charsets.UTF_8)))?.let(records::add)
            }
        }
        return records
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

    private fun parseRecord(source: JSONObject): OperationTraceRecord? {
        val capturedAtMillis = source.optLong("capturedAtMillis", 0L)
        if (capturedAtMillis <= 0L) return null
        return when (source.optString("kind")) {
            "accessibility_event" -> OperationAccessibilityEventRecord(
                capturedAtMillis = capturedAtMillis,
                eventType = source.optInt("eventType"),
                eventTypeLabel = source.optString("eventTypeLabel"),
                packageName = source.optString("packageName"),
                className = source.optNullableString("className"),
                windowTitle = source.optNullableString("windowTitle"),
                contentChangeTypes = source.optInt("contentChangeTypes"),
                source = source.optJSONObject("source")?.toNodeEvidence(),
                eventText = source.optNullableString("eventText"),
                inputLengthBucket = source.optNullableString("inputLengthBucket"),
                redactionApplied = source.optBoolean("redactionApplied"),
            )

            "node_snapshot" -> OperationNodeSnapshotRecord(
                capturedAtMillis = capturedAtMillis,
                packageName = source.optString("packageName"),
                windowTitle = source.optNullableString("windowTitle"),
                nodes = source.optJSONArray("nodes").toNodeEvidenceList(),
                rawNodeCount = source.optInt("rawNodeCount"),
                truncated = source.optBoolean("truncated"),
            )

            "marker" -> OperationRecordingMarkerRecord(
                capturedAtMillis = capturedAtMillis,
                marker = source.optString("marker"),
                detail = source.optNullableString("detail"),
            )

            else -> null
        }
    }

    private fun JSONObject.toNodeEvidence(): OperationNodeEvidence = OperationNodeEvidence(
        viewId = optNullableString("viewId"),
        className = optNullableString("className"),
        role = optNullableString("role"),
        text = optNullableString("text"),
        contentDescription = optNullableString("contentDescription"),
        hint = optNullableString("hint"),
        bounds = optNullableString("bounds"),
        screenWidth = optInt("screenWidth"),
        screenHeight = optInt("screenHeight"),
        clickable = optBoolean("clickable"),
        longClickable = optBoolean("longClickable"),
        editable = optBoolean("editable"),
        scrollable = optBoolean("scrollable"),
        password = optBoolean("password"),
        sensitive = optBoolean("sensitive"),
        inputLengthBucket = optNullableString("inputLengthBucket"),
        riskHints = optJSONArray("riskHints").toStringSet(),
    )

    private fun JSONArray?.toNodeEvidenceList(): List<OperationNodeEvidence> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length().coerceAtMost(MAX_NODES_PER_SNAPSHOT)) {
                optJSONObject(index)?.let { add(it.toNodeEvidence()) }
            }
        }
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return buildSet {
            for (index in 0 until length().coerceAtMost(MAX_RISK_HINTS)) {
                optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).trim().takeIf(String::isNotBlank)
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
        internal const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        internal const val MAX_RECORD_BYTES = 512 * 1024
        internal const val FRAME_IV_LENGTH_BYTES = 1
        internal const val GCM_TAG_BYTES = 16
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "ai_ledger_operation_trace_v1"
        private const val TRACE_DIRECTORY = "operation-traces"
        private const val TRACE_FORMAT_VERSION = 1
        private const val TRACE_RETENTION_MS = 24L * 60L * 60L * 1_000L
        private const val DEFAULT_MAX_READ_RECORDS = 1_200
        private const val MAX_READ_RECORDS = 2_000
        private const val MAX_TRACE_FILE_BYTES = 8L * 1024L * 1024L
        private const val MIN_GCM_IV_BYTES = 12
        private const val MAX_GCM_IV_BYTES = 16
        private const val GCM_TAG_BITS = 128
        private const val MIN_ENCRYPTED_FRAME_BYTES = FRAME_IV_LENGTH_BYTES + MIN_GCM_IV_BYTES + GCM_TAG_BYTES
        private const val MAX_ENCRYPTED_FRAME_BYTES = FRAME_IV_LENGTH_BYTES + MAX_GCM_IV_BYTES + MAX_RECORD_BYTES + GCM_TAG_BYTES
        private const val MAX_NODES_PER_SNAPSHOT = 96
        private const val MAX_RISK_HINTS = 8
    }
}

class OperationTraceWriter internal constructor(
    val file: File,
    private val sessionId: String,
    private val key: SecretKey,
    header: OperationTraceRecord,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val accepting = AtomicBoolean(true)
    private val records = Channel<OperationTraceRecord>(
        capacity = MAX_BUFFERED_RECORDS,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val writerJob = scope.launch {
        var writtenBytes = 0L
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(file, false))).use { output ->
                writtenBytes += writeEncryptedRecord(output, header)
                for (record in records) {
                    val estimated = estimateEncryptedFrameBytes(record)
                    if (writtenBytes + estimated > MAX_TRACE_BYTES) {
                        accepting.set(false)
                        break
                    }
                    writtenBytes += writeEncryptedRecord(output, record)
                }
                output.flush()
            }
        } finally {
            accepting.set(false)
        }
    }

    val path: String
        get() = file.absolutePath

    fun append(record: OperationTraceRecord): Boolean {
        if (!accepting.get() || !writerJob.isActive) return false
        return records.trySend(record).isSuccess
    }

    suspend fun close(finalRecord: OperationTraceRecord? = null) {
        if (accepting.compareAndSet(true, false)) {
            finalRecord?.let { records.send(it) }
            records.close()
        } else {
            records.close()
        }
        writerJob.join()
        scope.cancel()
    }

    private fun estimateEncryptedFrameBytes(record: OperationTraceRecord): Long {
        val plainBytes = record.toJson().toString().toByteArray(Charsets.UTF_8).size
        return (
            FRAME_LENGTH_PREFIX_BYTES +
                OperationTraceStore.FRAME_IV_LENGTH_BYTES +
                GCM_IV_BYTES +
                plainBytes +
                OperationTraceStore.GCM_TAG_BYTES
            ).toLong()
    }

    private fun writeEncryptedRecord(
        output: DataOutputStream,
        record: OperationTraceRecord,
    ): Long {
        val plain = record.toJson().toString().toByteArray(Charsets.UTF_8)
        if (plain.size > OperationTraceStore.MAX_RECORD_BYTES) return 0L
        val cipher = Cipher.getInstance(OperationTraceStore.CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(sessionId.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(plain)
        val iv = cipher.iv
        val frameSize = OperationTraceStore.FRAME_IV_LENGTH_BYTES + iv.size + encrypted.size
        output.writeInt(frameSize)
        output.writeByte(iv.size)
        output.write(iv)
        output.write(encrypted)
        return (FRAME_LENGTH_PREFIX_BYTES + frameSize).toLong()
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
        private const val MAX_BUFFERED_RECORDS = 256
        private const val MAX_TRACE_BYTES = 8L * 1024L * 1024L
        private const val FRAME_LENGTH_PREFIX_BYTES = 4
        private const val GCM_IV_BYTES = 12
    }
}

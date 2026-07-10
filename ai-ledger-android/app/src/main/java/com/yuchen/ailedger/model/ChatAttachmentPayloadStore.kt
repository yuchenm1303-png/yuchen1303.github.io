package com.yuchen.ailedger.model

import androidx.compose.runtime.Immutable
import com.yuchen.ailedger.AiLedgerApplication
import java.io.File
import java.security.MessageDigest
import java.util.LinkedHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Immutable
data class ChatAttachmentPayloadRef internal constructor(
    val id: String,
)

/**
 * Keeps large attachment payloads out of Compose state.
 *
 * Recent payloads stay in a bounded process-wide LRU so the initial request remains allocation-free.
 * A cache-file copy preserves retry behaviour after the in-memory entry is evicted. Neither the
 * OpenGL host nor the message rendering chain observes this store.
 */
internal object ChatAttachmentPayloadStore {
    private const val MAX_MEMORY_CHARS = 32 * 1024 * 1024
    private const val MAX_DISK_BYTES = 128L * 1024L * 1024L
    private const val MAX_DISK_AGE_MS = 30L * 24L * 60L * 60L * 1_000L
    private const val CACHE_DIRECTORY_NAME = "chat_attachment_payloads"
    private const val CACHE_FILE_SUFFIX = ".payload"

    private val lock = Any()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memoryPayloads = LinkedHashMap<String, String>(8, 0.75f, true)
    private val pendingWrites = mutableSetOf<String>()
    private var memoryChars: Int = 0

    fun register(id: String, base64Data: String): ChatAttachmentPayloadRef {
        val cleanId = id.trim()
        if (cleanId.isBlank()) return ChatAttachmentPayloadRef(id)
        if (base64Data.isNotBlank()) {
            synchronized(lock) {
                putMemoryLocked(cleanId, base64Data)
                pendingWrites += cleanId
            }
            persistAsync(cleanId, base64Data)
        }
        return ChatAttachmentPayloadRef(cleanId)
    }

    fun resolve(ref: ChatAttachmentPayloadRef): String {
        val id = ref.id
        synchronized(lock) {
            memoryPayloads[id]?.let { return it }
        }

        val file = payloadFile(id) ?: return ""
        val payload = runCatching {
            if (!file.isFile) return@runCatching ""
            file.readText(Charsets.UTF_8).also {
                file.setLastModified(System.currentTimeMillis())
            }
        }.getOrDefault("")
        if (payload.isNotBlank()) {
            synchronized(lock) {
                putMemoryLocked(id, payload)
            }
        }
        return payload
    }

    fun hasPayload(ref: ChatAttachmentPayloadRef): Boolean {
        synchronized(lock) {
            if (memoryPayloads.containsKey(ref.id) || pendingWrites.contains(ref.id)) return true
        }
        return payloadFile(ref.id)?.isFile == true
    }

    fun remove(ref: ChatAttachmentPayloadRef) {
        synchronized(lock) {
            memoryPayloads.remove(ref.id)?.let { memoryChars -= it.length }
            pendingWrites.remove(ref.id)
        }
        ioScope.launch {
            runCatching { payloadFile(ref.id)?.delete() }
        }
    }

    private fun persistAsync(id: String, payload: String) {
        val target = payloadFile(id)
        if (target == null) {
            synchronized(lock) {
                pendingWrites.remove(id)
                trimMemoryLocked()
            }
            return
        }
        ioScope.launch {
            try {
                val directory = target.parentFile
                if (directory != null && !directory.exists()) directory.mkdirs()
                val temporary = File(directory, "${target.name}.tmp-${System.nanoTime()}")
                temporary.writeText(payload, Charsets.UTF_8)
                if (!temporary.renameTo(target)) {
                    target.writeText(payload, Charsets.UTF_8)
                    temporary.delete()
                }
                target.setLastModified(System.currentTimeMillis())
                cleanupDiskCache(directory)
            } finally {
                synchronized(lock) {
                    pendingWrites.remove(id)
                    trimMemoryLocked()
                }
            }
        }
    }

    private fun putMemoryLocked(id: String, payload: String) {
        val previous = memoryPayloads.put(id, payload)
        if (previous != null) memoryChars -= previous.length
        memoryChars += payload.length
        trimMemoryLocked()
    }

    private fun trimMemoryLocked() {
        if (memoryChars <= MAX_MEMORY_CHARS) return
        val iterator = memoryPayloads.entries.iterator()
        while (memoryChars > MAX_MEMORY_CHARS && iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key in pendingWrites) continue
            memoryChars -= entry.value.length
            iterator.remove()
        }
    }

    private fun payloadFile(id: String): File? {
        val context = AiLedgerApplication.contextOrNull() ?: return null
        val directory = File(context.cacheDir, CACHE_DIRECTORY_NAME)
        return File(directory, stableFileName(id) + CACHE_FILE_SUFFIX)
    }

    private fun cleanupDiskCache(directory: File?) {
        if (directory == null || !directory.isDirectory) return
        val now = System.currentTimeMillis()
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(CACHE_FILE_SUFFIX) }
            ?.toList()
            .orEmpty()
        files.filter { now - it.lastModified() > MAX_DISK_AGE_MS }
            .forEach { runCatching { it.delete() } }

        val survivors = directory.listFiles { file -> file.isFile && file.name.endsWith(CACHE_FILE_SUFFIX) }
            ?.sortedByDescending(File::lastModified)
            .orEmpty()
        var totalBytes = 0L
        survivors.forEach { file ->
            totalBytes += file.length()
            if (totalBytes > MAX_DISK_BYTES) runCatching { file.delete() }
        }
    }

    private fun stableFileName(id: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
        val hex = CharArray(bytes.size * 2)
        val digits = "0123456789abcdef"
        bytes.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            hex[index * 2] = digits[value ushr 4]
            hex[index * 2 + 1] = digits[value and 0x0f]
        }
        return String(hex)
    }
}

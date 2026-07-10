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
    // A full request is limited to 10 MiB of binary images, roughly 13.4 MiB after Base64 encoding.
    // Keep one complete active request hot without allowing several old conversations to retain
    // tens of megabytes of String payloads indefinitely.
    private const val MAX_MEMORY_CHARS = 16 * 1024 * 1024
    private const val LOW_MEMORY_TARGET_CHARS = 4 * 1024 * 1024
    private const val MAX_PERSISTED_ID_MARKERS = 128
    private const val MAX_DISK_BYTES = 128L * 1024L * 1024L
    private const val MAX_DISK_AGE_MS = 30L * 24L * 60L * 60L * 1_000L
    private const val DISK_CLEANUP_INTERVAL_MS = 10L * 60L * 1_000L
    private const val CACHE_DIRECTORY_NAME = "chat_attachment_payloads"
    private const val CACHE_FILE_SUFFIX = ".payload"

    private val lock = Any()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memoryPayloads = LinkedHashMap<String, String>(8, 0.75f, true)

    /**
     * One pending value per attachment id. A newer value replaces the queued value and the existing
     * writer drains it before exiting, so copies of the same attachment never create parallel writes.
     */
    private val pendingWrites = mutableMapOf<String, String>()
    private val persistedIds = LinkedHashMap<String, Unit>(16, 0.75f, true)
    private var memoryChars: Int = 0
    private var lastDiskCleanupAtMs: Long = 0L

    fun register(id: String, base64Data: String): ChatAttachmentPayloadRef {
        val cleanId = id.trim()
        if (cleanId.isBlank()) return ChatAttachmentPayloadRef(id)
        if (base64Data.isNotBlank()) {
            var launchWriter = false
            synchronized(lock) {
                val previousMemory = memoryPayloads[cleanId]
                val pending = pendingWrites[cleanId]
                val alreadyAvailable = previousMemory == base64Data && (
                    pending == base64Data || persistedIds.containsKey(cleanId)
                    )
                putMemoryLocked(cleanId, base64Data)
                if (!alreadyAvailable) {
                    persistedIds.remove(cleanId)
                    launchWriter = pendingWrites.put(cleanId, base64Data) == null
                }
            }
            if (launchWriter) persistAsync(cleanId)
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
                markPersistedLocked(id)
            }
        }
        return payload
    }

    fun hasPayload(ref: ChatAttachmentPayloadRef): Boolean {
        synchronized(lock) {
            if (
                memoryPayloads.containsKey(ref.id) ||
                pendingWrites.containsKey(ref.id) ||
                persistedIds.containsKey(ref.id)
            ) {
                return true
            }
        }
        val exists = payloadFile(ref.id)?.isFile == true
        if (exists) {
            synchronized(lock) { markPersistedLocked(ref.id) }
        }
        return exists
    }

    fun remove(ref: ChatAttachmentPayloadRef) {
        synchronized(lock) {
            memoryPayloads.remove(ref.id)?.let { memoryChars -= it.length }
            pendingWrites.remove(ref.id)
            persistedIds.remove(ref.id)
        }
        ioScope.launch {
            runCatching { payloadFile(ref.id)?.delete() }
        }
    }

    /**
     * Releases only payloads that already have no pending disk write. The backing cache files remain
     * available, so retry and resend behaviour is preserved after Android asks the process to trim.
     */
    fun trimMemory(aggressive: Boolean) {
        synchronized(lock) {
            trimMemoryToLocked(if (aggressive) 0 else LOW_MEMORY_TARGET_CHARS)
        }
    }

    private fun persistAsync(id: String) {
        ioScope.launch {
            while (true) {
                val payload = synchronized(lock) { pendingWrites[id] } ?: return@launch
                val target = payloadFile(id)
                var persisted = false
                if (target != null) {
                    val directory = target.parentFile
                    try {
                        if (directory != null && !directory.exists()) directory.mkdirs()
                        val temporary = File(directory, "${target.name}.tmp-${System.nanoTime()}")
                        temporary.writeText(payload, Charsets.UTF_8)
                        if (!temporary.renameTo(target)) {
                            target.writeText(payload, Charsets.UTF_8)
                            temporary.delete()
                        }
                        target.setLastModified(System.currentTimeMillis())
                        persisted = true
                        cleanupDiskCache(directory)
                    } catch (_: Throwable) {
                        // The in-memory value remains available for the active request. A future
                        // registration of the same id can retry persistence.
                    }
                }

                val finished = synchronized(lock) {
                    if (pendingWrites[id] === payload) {
                        pendingWrites.remove(id)
                        if (persisted) markPersistedLocked(id)
                        trimMemoryLocked()
                        true
                    } else {
                        false
                    }
                }
                if (finished) return@launch
            }
        }
    }

    private fun markPersistedLocked(id: String) {
        persistedIds[id] = Unit
        while (persistedIds.size > MAX_PERSISTED_ID_MARKERS) {
            val iterator = persistedIds.entries.iterator()
            if (!iterator.hasNext()) return
            iterator.next()
            iterator.remove()
        }
    }

    private fun putMemoryLocked(id: String, payload: String) {
        val previous = memoryPayloads.put(id, payload)
        if (previous != null) memoryChars -= previous.length
        memoryChars += payload.length
        trimMemoryLocked()
    }

    private fun trimMemoryLocked() {
        trimMemoryToLocked(MAX_MEMORY_CHARS)
    }

    private fun trimMemoryToLocked(targetChars: Int) {
        if (memoryChars <= targetChars) return
        val iterator = memoryPayloads.entries.iterator()
        while (memoryChars > targetChars && iterator.hasNext()) {
            val entry = iterator.next()
            if (pendingWrites.containsKey(entry.key)) continue
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
        synchronized(lock) {
            if (lastDiskCleanupAtMs > 0L && now - lastDiskCleanupAtMs < DISK_CLEANUP_INTERVAL_MS) return
            lastDiskCleanupAtMs = now
        }

        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(CACHE_FILE_SUFFIX) }
            ?.toList()
            .orEmpty()
        val survivors = files.filter { file ->
            if (now - file.lastModified() > MAX_DISK_AGE_MS) {
                runCatching { file.delete() }
                false
            } else {
                true
            }
        }.sortedByDescending(File::lastModified)

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

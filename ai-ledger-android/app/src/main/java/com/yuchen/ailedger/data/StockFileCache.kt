package com.yuchen.ailedger.data

import com.yuchen.ailedger.AiLedgerApplication
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** 股票行情的小型原子文件缓存，带进程内热缓存，避免重复磁盘读取。 */
internal object StockFileCache {
    data class Entry(
        val body: String,
        val ageMs: Long,
        val source: String
    )

    private data class MemoryEntry(
        val body: String,
        val storedAtMs: Long
    )

    private val memory = ConcurrentHashMap<String, MemoryEntry>()
    private val fileLocks = ConcurrentHashMap<String, Any>()

    fun read(
        fileName: String,
        maxAgeMs: Long,
        source: String = fileName
    ): Entry? {
        val now = System.currentTimeMillis()
        memory[fileName]?.let { cached ->
            val ageMs = (now - cached.storedAtMs).coerceAtLeast(0L)
            if (ageMs <= maxAgeMs) return Entry(cached.body, ageMs, source)
            memory.remove(fileName, cached)
        }

        val file = cacheFile(fileName) ?: return null
        val lock = fileLocks.getOrPut(fileName) { Any() }
        synchronized(lock) {
            if (!file.isFile || file.length() <= 2L) return null
            val ageMs = (now - file.lastModified()).coerceAtLeast(0L)
            if (ageMs > maxAgeMs) {
                file.delete()
                memory.remove(fileName)
                return null
            }
            val body = runCatching { file.readText() }
                .getOrNull()
                ?.takeIf(String::isNotBlank)
                ?: return null
            memory[fileName] = MemoryEntry(body, now - ageMs)
            return Entry(body, ageMs, source)
        }
    }

    fun write(fileName: String, body: String) {
        if (body.isBlank()) return
        val now = System.currentTimeMillis()
        memory[fileName] = MemoryEntry(body, now)
        val file = cacheFile(fileName) ?: return
        val lock = fileLocks.getOrPut(fileName) { Any() }
        synchronized(lock) {
            runCatching {
                file.parentFile?.mkdirs()
                val temporary = File(file.parentFile, "${file.name}.tmp")
                temporary.writeText(body)
                if (!temporary.renameTo(file)) {
                    file.writeText(body)
                    temporary.delete()
                }
            }
        }
    }

    fun delete(fileName: String) {
        memory.remove(fileName)
        val file = cacheFile(fileName) ?: return
        val lock = fileLocks.getOrPut(fileName) { Any() }
        synchronized(lock) {
            runCatching { file.delete() }
        }
    }

    private fun cacheFile(fileName: String): File? {
        val context = AiLedgerApplication.contextOrNull() ?: return null
        return File(context.filesDir, fileName)
    }
}

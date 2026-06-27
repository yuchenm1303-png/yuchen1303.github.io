package com.yuchen.ailedger.service

/**
 * One-shot holder for the exact accessibility capture that passed the runner's fresh-screen guard.
 * A cached capture is never reused across roots, packages or actions, and expiration always falls
 * back to the original full scan path.
 */
internal class VisualExecutionCaptureState<T>(
    private val elapsedRealtime: () -> Long,
    private val ttlMs: Long,
) {
    private var entry: Entry<T>? = null

    @Synchronized
    fun store(
        packageName: String,
        rootIdentity: String,
        value: T,
    ) {
        val cleanPackage = packageName.trim()
        val cleanRoot = rootIdentity.trim()
        entry = if (cleanPackage.isNotBlank() && cleanRoot.isNotBlank()) {
            Entry(
                packageName = cleanPackage,
                rootIdentity = cleanRoot,
                storedAtMs = elapsedRealtime(),
                value = value,
            )
        } else {
            null
        }
    }

    @Synchronized
    fun take(
        packageName: String,
        rootIdentity: String,
    ): T? {
        val current = entry ?: return null
        entry = null
        val ageMs = (elapsedRealtime() - current.storedAtMs).coerceAtLeast(0L)
        if (ageMs > ttlMs.coerceAtLeast(0L)) return null
        if (current.packageName != packageName.trim()) return null
        if (current.rootIdentity != rootIdentity.trim()) return null
        return current.value
    }

    @Synchronized
    fun clear() {
        entry = null
    }

    private data class Entry<T>(
        val packageName: String,
        val rootIdentity: String,
        val storedAtMs: Long,
        val value: T,
    )
}

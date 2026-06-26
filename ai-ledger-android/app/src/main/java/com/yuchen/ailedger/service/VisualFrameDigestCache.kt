package com.yuchen.ailedger.service

import java.security.MessageDigest
import java.util.WeakHashMap

/**
 * A visual frame keeps the same immutable Base64 String through one observation lifecycle. Weak
 * keys avoid retaining screenshots, while repeated observation-id/fingerprint calls reuse the same
 * digest instead of rebuilding a large UTF-8 byte array each time.
 */
internal object VisualFrameDigestCache {
    private val cache = WeakHashMap<String, String>()

    fun digest(base64Jpeg: String): String {
        if (base64Jpeg.isBlank()) return ""
        synchronized(cache) {
            cache[base64Jpeg]?.let { return it }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(base64Jpeg.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        synchronized(cache) {
            return cache.getOrPut(base64Jpeg) { digest }
        }
    }
}

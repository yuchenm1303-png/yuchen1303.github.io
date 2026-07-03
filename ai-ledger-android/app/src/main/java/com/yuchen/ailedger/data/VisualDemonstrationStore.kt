package com.yuchen.ailedger.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.yuchen.ailedger.model.VisualDemonstrationFrame
import com.yuchen.ailedger.model.VisualDemonstrationManifest
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * 视觉演示只在应用私有目录短期保存。截图逐帧使用 Android Keystore AES-GCM 加密，
 * manifest 只包含时间、尺寸和包名等非图像元数据。
 */
class VisualDemonstrationStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val root = File(applicationContext.noBackupFilesDir, DIRECTORY).apply { mkdirs() }

    fun createSession(
        demonstrationId: String,
        workflowId: String,
        workflowTitle: String,
        goal: String,
        allowedPackages: Set<String>,
        startedAtMillis: Long,
    ): VisualDemonstrationSession {
        cleanupExpired()
        val cleanId = demonstrationId.safeFileToken()
        require(cleanId.isNotBlank()) { "invalid demonstration id" }
        val directory = File(root, cleanId).apply {
            deleteRecursively()
            require(mkdirs()) { "cannot create visual demonstration directory" }
        }
        val manifest = VisualDemonstrationManifest(
            demonstrationId = demonstrationId,
            workflowId = workflowId,
            workflowTitle = workflowTitle,
            goal = goal,
            allowedPackages = allowedPackages.map(String::trim).filter(String::isNotBlank).distinct(),
            startedAtMillis = startedAtMillis,
        )
        return VisualDemonstrationSession(this, directory, manifest).also { it.persist() }
    }

    fun load(manifestPath: String): VisualDemonstrationManifest {
        val file = checkedManifestFile(manifestPath)
        require(file.isFile) { "visual demonstration manifest not found" }
        return decodeManifest(JSONObject(file.readText(Charsets.UTF_8)))
    }

    fun readFrameBytes(
        manifestPath: String,
        frame: VisualDemonstrationFrame,
    ): ByteArray {
        val manifestFile = checkedManifestFile(manifestPath)
        val frameFile = File(manifestFile.parentFile, frame.encryptedFileName).canonicalFile
        require(frameFile.isInsideRoot() && frameFile.parentFile == manifestFile.parentFile.canonicalFile) {
            "visual frame path outside session"
        }
        require(frameFile.isFile && frameFile.length() in 1..MAX_ENCRYPTED_FRAME_BYTES) {
            "visual frame file invalid"
        }
        DataInputStream(FileInputStream(frameFile)).use { input ->
            val ivSize = input.readUnsignedByte()
            require(ivSize in 12..16) { "visual frame IV invalid" }
            val iv = ByteArray(ivSize).also { input.readFully(it) }
            val encryptedSize = input.readInt()
            require(encryptedSize in 16..MAX_ENCRYPTED_FRAME_BYTES.toInt()) { "visual frame payload invalid" }
            val encrypted = ByteArray(encryptedSize).also { input.readFully(it) }
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.updateAAD(frame.id.toByteArray(Charsets.UTF_8))
            return cipher.doFinal(encrypted)
        }
    }

    fun delete(manifestPath: String?) {
        val file = manifestPath?.takeIf(String::isNotBlank)?.let(::File) ?: return
        runCatching {
            val checked = checkedManifestFile(file.absolutePath)
            checked.parentFile?.let { sessionDirectory ->
                if (sessionDirectory.isInsideRoot()) sessionDirectory.deleteRecursively()
            }
        }
    }

    fun cleanupExpired(nowMillis: Long = System.currentTimeMillis()) {
        root.listFiles().orEmpty().forEach { directory ->
            if (directory.isDirectory && nowMillis - directory.lastModified() > RETENTION_MS) {
                runCatching { directory.deleteRecursively() }
            }
        }
    }

    internal fun writeEncryptedFrame(
        directory: File,
        frameId: String,
        bytes: ByteArray,
        fileName: String,
    ) {
        require(bytes.isNotEmpty() && bytes.size <= MAX_PLAIN_FRAME_BYTES) { "visual frame too large" }
        val file = File(directory, fileName).canonicalFile
        require(file.isInsideRoot() && file.parentFile == directory.canonicalFile) { "invalid visual frame path" }
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(frameId.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(bytes)
        DataOutputStream(FileOutputStream(file, false)).use { output ->
            output.writeByte(cipher.iv.size)
            output.write(cipher.iv)
            output.writeInt(encrypted.size)
            output.write(encrypted)
            output.flush()
        }
    }

    internal fun writeManifest(directory: File, manifest: VisualDemonstrationManifest): String {
        require(directory.canonicalFile.isInsideRoot()) { "invalid visual demonstration directory" }
        val target = File(directory, MANIFEST_FILE)
        val temporary = File(directory, "$MANIFEST_FILE.tmp")
        temporary.writeText(encodeManifest(manifest).toString(), Charsets.UTF_8)
        if (!temporary.renameTo(target)) {
            target.writeText(temporary.readText(Charsets.UTF_8), Charsets.UTF_8)
            temporary.delete()
        }
        return target.absolutePath
    }

    private fun checkedManifestFile(path: String): File {
        val file = File(path).canonicalFile
        require(file.name == MANIFEST_FILE && file.isInsideRoot()) { "manifest path outside visual store" }
        return file
    }

    private fun File.isInsideRoot(): Boolean {
        val rootPath = root.canonicalFile.path + File.separator
        return canonicalFile.path.startsWith(rootPath)
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

    private fun encodeManifest(manifest: VisualDemonstrationManifest): JSONObject = JSONObject().apply {
        put("schemaVersion", manifest.schemaVersion)
        put("demonstrationId", manifest.demonstrationId)
        put("workflowId", manifest.workflowId)
        put("workflowTitle", manifest.workflowTitle)
        put("goal", manifest.goal)
        put("allowedPackages", JSONArray(manifest.allowedPackages))
        put("startedAtMillis", manifest.startedAtMillis)
        put("completedAtMillis", manifest.completedAtMillis ?: JSONObject.NULL)
        put("frames", JSONArray().apply {
            manifest.frames.forEach { frame ->
                put(JSONObject().apply {
                    put("id", frame.id)
                    put("capturedAtMillis", frame.capturedAtMillis)
                    put("packageName", frame.packageName)
                    put("mimeType", frame.mimeType)
                    put("width", frame.width)
                    put("height", frame.height)
                    put("displayWidth", frame.displayWidth)
                    put("displayHeight", frame.displayHeight)
                    put("encryptedFileName", frame.encryptedFileName)
                    put("digest", frame.digest)
                })
            }
        })
    }

    private fun decodeManifest(source: JSONObject): VisualDemonstrationManifest {
        val allowed = source.optJSONArray("allowedPackages")
        val frames = source.optJSONArray("frames")
        return VisualDemonstrationManifest(
            schemaVersion = source.optString("schemaVersion", "ai_ledger_visual_demonstration_v1"),
            demonstrationId = source.getString("demonstrationId"),
            workflowId = source.getString("workflowId"),
            workflowTitle = source.optString("workflowTitle"),
            goal = source.optString("goal"),
            allowedPackages = buildList {
                if (allowed != null) for (index in 0 until allowed.length()) {
                    allowed.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
                }
            },
            startedAtMillis = source.getLong("startedAtMillis"),
            completedAtMillis = source.optLong("completedAtMillis").takeIf { it > 0L },
            frames = buildList {
                if (frames != null) for (index in 0 until frames.length()) {
                    val frame = frames.optJSONObject(index) ?: continue
                    add(
                        VisualDemonstrationFrame(
                            id = frame.getString("id"),
                            capturedAtMillis = frame.getLong("capturedAtMillis"),
                            packageName = frame.optString("packageName"),
                            mimeType = frame.optString("mimeType", "image/jpeg"),
                            width = frame.optInt("width"),
                            height = frame.optInt("height"),
                            displayWidth = frame.optInt("displayWidth"),
                            displayHeight = frame.optInt("displayHeight"),
                            encryptedFileName = frame.getString("encryptedFileName"),
                            digest = frame.optString("digest"),
                        ),
                    )
                }
            },
        )
    }

    companion object {
        private const val DIRECTORY = "visual-demonstrations"
        private const val MANIFEST_FILE = "manifest.json"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "ai_ledger_visual_demonstration_v1"
        private const val CIPHER = "AES/GCM/NoPadding"
        private const val MAX_PLAIN_FRAME_BYTES = 2 * 1024 * 1024
        private const val MAX_ENCRYPTED_FRAME_BYTES = 3L * 1024L * 1024L
        private const val RETENTION_MS = 24L * 60L * 60L * 1_000L
    }
}

class VisualDemonstrationSession internal constructor(
    private val store: VisualDemonstrationStore,
    private val directory: File,
    initialManifest: VisualDemonstrationManifest,
) {
    private var manifest: VisualDemonstrationManifest = initialManifest
    private var sealed = false

    val manifestPath: String
        get() = File(directory, "manifest.json").absolutePath

    val frameCount: Int
        @Synchronized get() = manifest.frames.size

    @Synchronized
    fun appendFrame(
        capturedAtMillis: Long,
        packageName: String,
        mimeType: String,
        width: Int,
        height: Int,
        displayWidth: Int,
        displayHeight: Int,
        bytes: ByteArray,
    ): Boolean {
        if (sealed || manifest.frames.size >= MAX_FRAMES) return false
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
        if (manifest.frames.lastOrNull()?.digest == digest) return false
        val index = manifest.frames.size
        val id = "${manifest.demonstrationId}-frame-$index"
        val fileName = "frame-${index.toString().padStart(3, '0')}.bin"
        store.writeEncryptedFrame(directory, id, bytes, fileName)
        val frame = VisualDemonstrationFrame(
            id = id,
            capturedAtMillis = capturedAtMillis,
            packageName = packageName,
            mimeType = mimeType,
            width = width,
            height = height,
            displayWidth = displayWidth,
            displayHeight = displayHeight,
            encryptedFileName = fileName,
            digest = digest,
        )
        manifest = manifest.copy(frames = manifest.frames + frame)
        persist()
        return true
    }

    @Synchronized
    fun seal(completedAtMillis: Long): VisualDemonstrationManifest {
        sealed = true
        manifest = manifest.copy(completedAtMillis = completedAtMillis)
        persist()
        return manifest
    }

    @Synchronized
    internal fun persist() {
        store.writeManifest(directory, manifest)
    }

    companion object {
        const val MAX_FRAMES = 24
    }
}

private fun String.safeFileToken(): String = replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)

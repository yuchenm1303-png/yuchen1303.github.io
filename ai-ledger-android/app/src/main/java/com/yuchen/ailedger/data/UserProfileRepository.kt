package com.yuchen.ailedger.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import com.yuchen.ailedger.service.SupabaseAuthClient
import com.yuchen.ailedger.service.SupabaseUserSession
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.Instant
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

private const val USER_PROFILE_TABLE = "user_profiles"
private const val USER_AVATAR_BUCKET = "user-avatars"
private const val USER_PROFILE_CONNECT_TIMEOUT_MS = 12_000
private const val USER_PROFILE_READ_TIMEOUT_MS = 24_000
private const val USER_PROFILE_MAX_NAME_LENGTH = 24
private const val USER_AVATAR_EDGE_PX = 512
private const val USER_AVATAR_MAX_DECODE_EDGE_PX = 1600
private const val USER_AVATAR_MAX_UPLOAD_BYTES = 1_048_576L

private const val PROFILE_CACHE_PREFERENCES = "user_profile_cache_v1"
private const val PROFILE_CACHE_PREFIX = "profile_"

data class UserProfile(
    val userId: String,
    val displayName: String,
    val avatarPath: String? = null,
    val avatarVersion: Long = 0L,
    val updatedAt: String = "",
)

data class UserProfileState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val uploadingAvatar: Boolean = false,
    val accountUserId: String? = null,
    val accountEmail: String? = null,
    val cloudReady: Boolean = false,
    val profile: UserProfile? = null,
    val localAvatarPath: String? = null,
    val message: String = "登录后可设置昵称和头像。",
    val error: Boolean = false,
) {
    val isLoggedIn: Boolean
        get() = accountUserId != null

    val isBusy: Boolean
        get() = loading || saving || uploadingAvatar

    val hasCustomAvatar: Boolean
        get() = !profile?.avatarPath.isNullOrBlank() && !localAvatarPath.isNullOrBlank()
}

class UserProfileRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val authRepository = SupabaseAuthRepository.get(appContext)
    private val localStore = UserProfileLocalStore(appContext)
    private val client = SupabaseUserProfileClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()

    private val _state = MutableStateFlow(UserProfileState())
    val state: StateFlow<UserProfileState> = _state.asStateFlow()

    @Volatile
    private var currentSession: SupabaseUserSession? = null

    init {
        scope.launch {
            authRepository.state.collectLatest { accountState ->
                val session = accountState.session?.takeIf { accountState.isLoggedIn }
                operationMutex.withLock {
                    if (session == null) {
                        currentSession = null
                        _state.value = UserProfileState(
                            message = "未登录，头像和昵称不会关联到账号。"
                        )
                        return@withLock
                    }

                    val userChanged = currentSession?.userId != session.userId
                    currentSession = session
                    if (userChanged || _state.value.accountUserId == null) {
                        loadProfileLocked(session)
                    }
                }
            }
        }
    }

    fun refresh() {
        scope.launch {
            operationMutex.withLock {
                val session = currentSession
                if (session == null) {
                    _state.value = UserProfileState(
                        message = "请先登录后再刷新个人资料。",
                        error = true,
                    )
                } else {
                    loadProfileLocked(session)
                }
            }
        }
    }

    fun updateDisplayName(name: String) {
        val normalized = normalizeDisplayName(name)
        if (normalized.isBlank()) {
            _state.value = _state.value.copy(
                message = "昵称不能为空。",
                error = true,
            )
            return
        }

        scope.launch {
            operationMutex.withLock {
                val session = currentSession
                if (session == null) {
                    _state.value = UserProfileState(
                        message = "请先登录后再修改昵称。",
                        error = true,
                    )
                    return@withLock
                }

                val current = currentProfileOrDefault(session)
                val next = current.copy(
                    displayName = normalized,
                    updatedAt = Instant.now().toString(),
                )
                localStore.saveProfile(next)
                _state.value = _state.value.copy(
                    saving = true,
                    profile = next,
                    message = "正在保存昵称…",
                    error = false,
                )

                try {
                    client.upsertProfile(session, next)
                    _state.value = _state.value.copy(
                        saving = false,
                        cloudReady = true,
                        profile = next,
                        message = "昵称已同步到账号。",
                        error = false,
                    )
                } catch (error: Throwable) {
                    _state.value = _state.value.copy(
                        saving = false,
                        cloudReady = false,
                        profile = next,
                        message = error.friendlyProfileMessage(),
                        error = true,
                    )
                }
            }
        }
    }

    fun updateAvatar(uri: Uri) {
        scope.launch {
            operationMutex.withLock {
                val session = currentSession
                if (session == null) {
                    _state.value = UserProfileState(
                        message = "请先登录后再设置头像。",
                        error = true,
                    )
                    return@withLock
                }

                _state.value = _state.value.copy(
                    uploadingAvatar = true,
                    message = "正在处理头像…",
                    error = false,
                )

                var preparedFile: File? = null
                try {
                    preparedFile = prepareAvatarFile(appContext, uri)
                    if (preparedFile.length() > USER_AVATAR_MAX_UPLOAD_BYTES) {
                        throw IOException("头像压缩后仍然过大，请换一张图片。")
                    }

                    val avatarPath = "${session.userId}/avatar.webp"
                    val avatarVersion = System.currentTimeMillis()
                    client.uploadAvatar(session, avatarPath, preparedFile)

                    val next = currentProfileOrDefault(session).copy(
                        avatarPath = avatarPath,
                        avatarVersion = avatarVersion,
                        updatedAt = Instant.now().toString(),
                    )
                    client.upsertProfile(session, next)
                    val localAvatar = localStore.replaceAvatar(session.userId, preparedFile)
                    localStore.saveProfile(next)

                    _state.value = _state.value.copy(
                        uploadingAvatar = false,
                        cloudReady = true,
                        profile = next,
                        localAvatarPath = localAvatar.absolutePath,
                        message = "头像已同步到账号。",
                        error = false,
                    )
                } catch (error: Throwable) {
                    _state.value = _state.value.copy(
                        uploadingAvatar = false,
                        message = error.friendlyProfileMessage(),
                        error = true,
                    )
                } finally {
                    preparedFile?.delete()
                }
            }
        }
    }

    fun removeAvatar() {
        scope.launch {
            operationMutex.withLock {
                val session = currentSession
                if (session == null) {
                    _state.value = UserProfileState(
                        message = "请先登录后再移除头像。",
                        error = true,
                    )
                    return@withLock
                }

                val current = currentProfileOrDefault(session)
                if (current.avatarPath.isNullOrBlank() && _state.value.localAvatarPath.isNullOrBlank()) {
                    _state.value = _state.value.copy(message = "当前正在使用默认头像。")
                    return@withLock
                }

                _state.value = _state.value.copy(
                    uploadingAvatar = true,
                    message = "正在恢复默认头像…",
                    error = false,
                )

                try {
                    current.avatarPath?.takeIf { it.isNotBlank() }?.let { path ->
                        client.deleteAvatar(session, path)
                    }
                    val next = current.copy(
                        avatarPath = null,
                        avatarVersion = System.currentTimeMillis(),
                        updatedAt = Instant.now().toString(),
                    )
                    client.upsertProfile(session, next)
                    localStore.deleteAvatar(session.userId)
                    localStore.saveProfile(next)
                    _state.value = _state.value.copy(
                        uploadingAvatar = false,
                        cloudReady = true,
                        profile = next,
                        localAvatarPath = null,
                        message = "已恢复默认头像。",
                        error = false,
                    )
                } catch (error: Throwable) {
                    _state.value = _state.value.copy(
                        uploadingAvatar = false,
                        message = error.friendlyProfileMessage(),
                        error = true,
                    )
                }
            }
        }
    }

    private suspend fun loadProfileLocked(session: SupabaseUserSession) {
        val cachedProfile = localStore.loadProfile(session.userId)
        val cachedAvatar = localStore.avatarFile(session.userId).takeIf(File::isFile)
        val fallbackProfile = cachedProfile ?: defaultProfile(session)

        _state.value = UserProfileState(
            loading = true,
            accountUserId = session.userId,
            accountEmail = session.email,
            cloudReady = false,
            profile = fallbackProfile,
            localAvatarPath = cachedAvatar?.absolutePath,
            message = "正在同步个人资料…",
        )

        try {
            val cloudProfile = client.fetchProfile(session) ?: defaultProfile(session).also { created ->
                client.upsertProfile(session, created)
            }
            val localAvatar = syncAvatarCache(session, cachedProfile, cloudProfile)
            localStore.saveProfile(cloudProfile)
            _state.value = UserProfileState(
                accountUserId = session.userId,
                accountEmail = session.email,
                cloudReady = true,
                profile = cloudProfile,
                localAvatarPath = localAvatar?.absolutePath,
                message = "个人资料已同步。",
            )
        } catch (error: Throwable) {
            _state.value = UserProfileState(
                accountUserId = session.userId,
                accountEmail = session.email,
                cloudReady = false,
                profile = fallbackProfile,
                localAvatarPath = cachedAvatar?.absolutePath,
                message = error.friendlyProfileMessage(),
                error = true,
            )
        }
    }

    private fun syncAvatarCache(
        session: SupabaseUserSession,
        cachedProfile: UserProfile?,
        cloudProfile: UserProfile,
    ): File? {
        val avatarPath = cloudProfile.avatarPath?.takeIf { it.isNotBlank() }
        if (avatarPath == null) {
            localStore.deleteAvatar(session.userId)
            return null
        }

        val existing = localStore.avatarFile(session.userId)
        val cacheCurrent = existing.isFile &&
            cachedProfile?.avatarPath == cloudProfile.avatarPath &&
            cachedProfile.avatarVersion == cloudProfile.avatarVersion
        if (cacheCurrent) return existing

        val downloaded = client.downloadAvatar(session, avatarPath, localStore.createAvatarTempFile(session.userId))
        return try {
            localStore.replaceAvatar(session.userId, downloaded)
        } finally {
            downloaded.delete()
        }
    }

    private fun currentProfileOrDefault(session: SupabaseUserSession): UserProfile {
        return _state.value.profile?.takeIf { it.userId == session.userId }
            ?: localStore.loadProfile(session.userId)
            ?: defaultProfile(session)
    }

    private fun defaultProfile(session: SupabaseUserSession): UserProfile {
        return UserProfile(
            userId = session.userId,
            displayName = defaultDisplayName(session.email),
            updatedAt = Instant.now().toString(),
        )
    }

    companion object {
        @Volatile
        private var instance: UserProfileRepository? = null

        fun get(context: Context): UserProfileRepository {
            return instance ?: synchronized(this) {
                instance ?: UserProfileRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

private class UserProfileLocalStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        PROFILE_CACHE_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    fun loadProfile(userId: String): UserProfile? {
        val raw = preferences.getString(PROFILE_CACHE_PREFIX + userId, null).orEmpty()
        if (raw.isBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            UserProfile(
                userId = json.optString("user_id").ifBlank { userId },
                displayName = json.optString("display_name"),
                avatarPath = json.optString("avatar_path").takeIf { it.isNotBlank() },
                avatarVersion = json.optLong("avatar_version", 0L),
                updatedAt = json.optString("updated_at"),
            )
        }.getOrNull()?.takeIf { it.userId == userId }
    }

    fun saveProfile(profile: UserProfile) {
        val json = JSONObject()
            .put("user_id", profile.userId)
            .put("display_name", profile.displayName)
            .put("avatar_path", profile.avatarPath ?: JSONObject.NULL)
            .put("avatar_version", profile.avatarVersion)
            .put("updated_at", profile.updatedAt)
        preferences.edit()
            .putString(PROFILE_CACHE_PREFIX + profile.userId, json.toString())
            .apply()
    }

    fun avatarFile(userId: String): File {
        return File(profileDirectory(userId), "avatar.webp")
    }

    fun createAvatarTempFile(userId: String): File {
        val directory = profileDirectory(userId)
        return File.createTempFile("avatar_", ".webp", directory)
    }

    fun replaceAvatar(userId: String, source: File): File {
        val destination = avatarFile(userId)
        val temporary = File(destination.parentFile, "avatar.pending.webp")
        source.inputStream().use { input ->
            FileOutputStream(temporary).use { output -> input.copyTo(output) }
        }
        if (destination.exists() && !destination.delete()) {
            temporary.delete()
            throw IOException("无法替换本地头像缓存。")
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            throw IOException("无法保存本地头像缓存。")
        }
        return destination
    }

    fun deleteAvatar(userId: String) {
        avatarFile(userId).delete()
        File(profileDirectory(userId), "avatar.pending.webp").delete()
    }

    private fun profileDirectory(userId: String): File {
        val safeUserId = userId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(appContext.filesDir, "user_profiles/$safeUserId").apply { mkdirs() }
    }
}

private class SupabaseUserProfileClient(
    private val supabaseUrl: String = SupabaseAuthClient.DEFAULT_SUPABASE_URL,
    private val publishableKey: String = SupabaseAuthClient.DEFAULT_SUPABASE_PUBLISHABLE_KEY,
) {
    fun fetchProfile(session: SupabaseUserSession): UserProfile? {
        val response = requestText(
            session = session,
            path = "/rest/v1/$USER_PROFILE_TABLE?select=user_id,display_name,avatar_path,avatar_version,updated_at&user_id=eq.${session.userId.urlEncode()}&limit=1",
            method = "GET",
        )
        val rows = JSONArray(response.ifBlank { "[]" })
        if (rows.length() == 0) return null
        val row = rows.getJSONObject(0)
        return UserProfile(
            userId = row.optString("user_id").ifBlank { session.userId },
            displayName = normalizeDisplayName(row.optString("display_name"))
                .ifBlank { defaultDisplayName(session.email) },
            avatarPath = row.optString("avatar_path").takeIf { it.isNotBlank() },
            avatarVersion = row.optLong("avatar_version", 0L),
            updatedAt = row.optString("updated_at"),
        )
    }

    fun upsertProfile(session: SupabaseUserSession, profile: UserProfile) {
        val body = JSONObject()
            .put("user_id", session.userId)
            .put("display_name", normalizeDisplayName(profile.displayName))
            .put("avatar_path", profile.avatarPath ?: JSONObject.NULL)
            .put("avatar_version", profile.avatarVersion)
            .put("updated_at", profile.updatedAt.ifBlank { Instant.now().toString() })
        requestText(
            session = session,
            path = "/rest/v1/$USER_PROFILE_TABLE?on_conflict=user_id",
            method = "POST",
            body = body.toString(),
            contentType = "application/json; charset=utf-8",
            prefer = "resolution=merge-duplicates,return=minimal",
        )
    }

    fun uploadAvatar(session: SupabaseUserSession, avatarPath: String, file: File) {
        requestBinary(
            session = session,
            path = "/storage/v1/object/$USER_AVATAR_BUCKET/${avatarPath.storagePathEncode()}",
            method = "POST",
            uploadFile = file,
            contentType = "image/webp",
            upsert = true,
        )
    }

    fun downloadAvatar(session: SupabaseUserSession, avatarPath: String, destination: File): File {
        requestBinary(
            session = session,
            path = "/storage/v1/object/authenticated/$USER_AVATAR_BUCKET/${avatarPath.storagePathEncode()}",
            method = "GET",
            downloadFile = destination,
        )
        return destination
    }

    fun deleteAvatar(session: SupabaseUserSession, avatarPath: String) {
        requestBinary(
            session = session,
            path = "/storage/v1/object/$USER_AVATAR_BUCKET/${avatarPath.storagePathEncode()}",
            method = "DELETE",
            ignoreMissing = true,
        )
    }

    private fun requestText(
        session: SupabaseUserSession,
        path: String,
        method: String,
        body: String? = null,
        contentType: String = "application/json; charset=utf-8",
        prefer: String? = null,
    ): String {
        val connection = openConnection(session, path, method).apply {
            doOutput = body != null
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("Accept", "application/json")
            if (!prefer.isNullOrBlank()) setRequestProperty("Prefer", prefer)
        }
        return try {
            body?.let { payload ->
                connection.outputStream.use { output ->
                    output.write(payload.toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val text = if (status in 200..299) {
                connection.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            } else {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
            if (status !in 200..299) throw IOException(translateProfileError(text, status))
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun requestBinary(
        session: SupabaseUserSession,
        path: String,
        method: String,
        uploadFile: File? = null,
        downloadFile: File? = null,
        contentType: String? = null,
        upsert: Boolean = false,
        ignoreMissing: Boolean = false,
    ) {
        val connection = openConnection(session, path, method).apply {
            doOutput = uploadFile != null
            if (!contentType.isNullOrBlank()) setRequestProperty("Content-Type", contentType)
            if (upsert) setRequestProperty("x-upsert", "true")
            uploadFile?.let { file -> fixedLengthStreamingMode(file.length()) }
        }
        try {
            uploadFile?.inputStream()?.use { input ->
                connection.outputStream.use { output -> input.copyTo(output) }
            }
            val status = connection.responseCode
            if (status !in 200..299 && !(ignoreMissing && status == 404)) {
                val text = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                throw IOException(translateProfileError(text, status))
            }
            if (downloadFile != null && status in 200..299) {
                downloadFile.parentFile?.mkdirs()
                connection.inputStream.use { input ->
                    FileOutputStream(downloadFile).use { output -> input.copyTo(output) }
                }
            } else {
                connection.inputStream?.close()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(
        session: SupabaseUserSession,
        path: String,
        method: String,
    ): HttpURLConnection {
        val base = supabaseUrl.trim().trimEnd('/')
        if (base.isBlank() || publishableKey.isBlank()) {
            throw IOException("Supabase 尚未配置完整。")
        }
        return (URL("$base$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = USER_PROFILE_CONNECT_TIMEOUT_MS
            readTimeout = USER_PROFILE_READ_TIMEOUT_MS
            doInput = true
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
        }
    }

    private fun translateProfileError(raw: String, status: Int): String {
        val json = runCatching { JSONObject(raw) }.getOrNull()
        val code = json?.optString("code").orEmpty()
        val message = json?.let { value ->
            value.optString("message")
                .ifBlank { value.optString("hint") }
                .ifBlank { value.optString("details") }
                .ifBlank { value.optString("error") }
        }.orEmpty().ifBlank { raw.trim() }
        return when {
            code == "42P01" || code == "PGRST205" ||
                message.contains(USER_PROFILE_TABLE, ignoreCase = true) &&
                message.contains("schema cache", ignoreCase = true) ->
                "Supabase 个人资料表尚未建立，请执行仓库中的资料初始化 SQL。"
            status == 400 && message.contains("Bucket not found", ignoreCase = true) ->
                "Supabase 头像存储桶尚未建立，请执行仓库中的资料初始化 SQL。"
            status == 401 -> "登录状态已失效，请重新登录。"
            status == 403 || message.contains("row-level security", true) ||
                message.contains("policy", true) ->
                "Supabase 个人资料权限尚未配置，请检查 RLS 和 Storage Policy。"
            status == 413 -> "头像文件过大，请换一张图片。"
            message.isNotBlank() -> message
            else -> "个人资料同步失败：HTTP $status"
        }
    }
}

private fun prepareAvatarFile(context: Context, uri: Uri): File {
    val decoded = decodeAvatarBitmap(context, uri)
        ?: throw IOException("无法读取这张图片，请换一张重试。")
    val output = Bitmap.createBitmap(
        USER_AVATAR_EDGE_PX,
        USER_AVATAR_EDGE_PX,
        Bitmap.Config.ARGB_8888,
    )
    try {
        val sourceWidth = decoded.width.coerceAtLeast(1)
        val sourceHeight = decoded.height.coerceAtLeast(1)
        val cropEdge = minOf(sourceWidth, sourceHeight)
        val left = (sourceWidth - cropEdge) / 2
        val top = (sourceHeight - cropEdge) / 2
        Canvas(output).drawBitmap(
            decoded,
            Rect(left, top, left + cropEdge, top + cropEdge),
            Rect(0, 0, USER_AVATAR_EDGE_PX, USER_AVATAR_EDGE_PX),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )

        val file = File.createTempFile("profile_avatar_", ".webp", context.cacheDir)
        FileOutputStream(file).use { stream ->
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            if (!output.compress(format, 88, stream)) {
                throw IOException("头像压缩失败，请换一张图片重试。")
            }
        }
        return file
    } finally {
        if (!decoded.isRecycled) decoded.recycle()
        if (!output.isRecycled) output.recycle()
    }
}

private fun decodeAvatarBitmap(context: Context, uri: Uri): Bitmap? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width.coerceAtLeast(1)
            val height = info.size.height.coerceAtLeast(1)
            val maxEdge = max(width, height)
            if (maxEdge > USER_AVATAR_MAX_DECODE_EDGE_PX) {
                val scale = USER_AVATAR_MAX_DECODE_EDGE_PX.toFloat() / maxEdge.toFloat()
                decoder.setTargetSize(
                    (width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val maxEdge = max(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (maxEdge / (sampleSize * 2) >= USER_AVATAR_MAX_DECODE_EDGE_PX) {
            sampleSize *= 2
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(
                input,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        }
    }
}

internal fun normalizeDisplayName(value: String): String {
    return value.trim()
        .replace(Regex("\\s+"), " ")
        .take(USER_PROFILE_MAX_NAME_LENGTH)
}

internal fun defaultDisplayName(email: String): String {
    val localPart = email.substringBefore('@').trim()
    if (localPart.isBlank()) return "AI Ledger 用户"
    return localPart
        .replace(Regex("[._-]+"), " ")
        .trim()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .joinToString(" ") { part ->
            part.replaceFirstChar { character ->
                if (character.isLowerCase()) character.titlecase() else character.toString()
            }
        }
        .take(USER_PROFILE_MAX_NAME_LENGTH)
        .ifBlank { "AI Ledger 用户" }
}

private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

private fun String.storagePathEncode(): String {
    return split('/').joinToString("/") { segment -> segment.urlEncode().replace("+", "%20") }
}

private fun Throwable.friendlyProfileMessage(): String {
    val raw = message.orEmpty().trim()
    return raw.ifBlank { "个人资料暂时无法同步，请稍后再试。" }
}

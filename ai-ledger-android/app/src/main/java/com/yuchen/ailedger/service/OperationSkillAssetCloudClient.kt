package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.OperationSkillAssetEnvelope
import com.yuchen.ailedger.model.OperationSkillAssetVersionEnvelope
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.model.WorkflowExecutionMode
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

private const val SKILL_ASSET_TABLE = "operation_skill_assets_v1"
private const val SKILL_ASSET_VERSION_TABLE = "operation_skill_asset_versions_v1"
private const val SKILL_ASSET_CONNECT_TIMEOUT_MS = 8_000
private const val SKILL_ASSET_READ_TIMEOUT_MS = 14_000
private const val SKILL_ASSET_FETCH_LIMIT = 100

/**
 * 视觉 Skill 资产直接通过 Supabase PostgREST 同步。
 *
 * 这里只传 Skill 语义、工作流安全边界和用户审核快照；不上传原始演示截图、节点树、坐标、
 * Resource ID 或 Replay 运行时输入值。认证沿用当前 Supabase 会话，数据隔离交给 RLS。
 */
internal class OperationSkillAssetCloudClient(
    private val supabaseUrl: String = SupabaseAuthClient.DEFAULT_SUPABASE_URL,
    private val publishableKey: String = SupabaseAuthClient.DEFAULT_SUPABASE_PUBLISHABLE_KEY,
) {
    @Throws(IOException::class)
    fun upsertAsset(
        session: SupabaseUserSession,
        deviceId: String,
        asset: OperationSkillAssetEnvelope,
    ) {
        val body = JSONArray()
            .put(asset.toSupabaseJson(session.userId, cleanDeviceId(deviceId)))
            .toString()
        requestText(
            session = session,
            method = "POST",
            path = "/rest/v1/$SKILL_ASSET_TABLE?on_conflict=user_id,workflow_id",
            body = body,
            prefer = "resolution=merge-duplicates,return=minimal",
        )
    }

    @Throws(IOException::class)
    fun upsertApprovedVersion(
        session: SupabaseUserSession,
        deviceId: String,
        version: OperationSkillAssetVersionEnvelope,
    ) {
        val body = JSONArray()
            .put(version.toSupabaseJson(session.userId, cleanDeviceId(deviceId)))
            .toString()
        requestText(
            session = session,
            method = "POST",
            path = "/rest/v1/$SKILL_ASSET_VERSION_TABLE?on_conflict=user_id,workflow_id,version_number",
            body = body,
            prefer = "resolution=merge-duplicates,return=minimal",
        )
    }

    @Throws(IOException::class)
    fun fetchAssets(
        session: SupabaseUserSession,
        updatedAfterMillis: Long,
    ): List<OperationSkillAssetEnvelope> {
        val path = buildString {
            append("/rest/v1/").append(SKILL_ASSET_TABLE)
            append("?select=*")
            append("&local_updated_at_millis=gt.").append(updatedAfterMillis.coerceAtLeast(0L))
            append("&order=local_updated_at_millis.asc")
            append("&limit=").append(SKILL_ASSET_FETCH_LIMIT)
        }
        val response = requestText(
            session = session,
            method = "GET",
            path = path,
        )
        return response.toAssetList()
    }

    @Throws(IOException::class)
    fun markDeleted(
        session: SupabaseUserSession,
        deviceId: String,
        workflowId: String,
        deletedAtMillis: Long,
    ) {
        val cleanWorkflowId = workflowId.trim().take(120)
        if (cleanWorkflowId.isBlank()) throw IOException("Skill ID 无效，无法同步删除状态。")
        val body = JSONObject()
            .put("owner_device_id", cleanDeviceId(deviceId))
            .put("status", WorkflowDraftStatus.Archived.name)
            .put("deleted_at_millis", deletedAtMillis.coerceAtLeast(0L))
            .put("local_updated_at_millis", deletedAtMillis.coerceAtLeast(0L))
            .toString()
        requestText(
            session = session,
            method = "PATCH",
            path = "/rest/v1/$SKILL_ASSET_TABLE?workflow_id=eq.${cleanWorkflowId.urlEncode()}",
            body = body,
            prefer = "return=minimal",
        )
    }

    private fun requestText(
        session: SupabaseUserSession,
        method: String,
        path: String,
        body: String? = null,
        prefer: String? = null,
    ): String {
        val cleanBase = supabaseUrl.trim().trimEnd('/')
        if (cleanBase.isBlank() || publishableKey.isBlank()) {
            throw IOException("Supabase 尚未配置完整。")
        }
        val bytes = body?.toByteArray(Charsets.UTF_8)
        val connection = (URL("$cleanBase$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = SKILL_ASSET_CONNECT_TIMEOUT_MS
            readTimeout = SKILL_ASSET_READ_TIMEOUT_MS
            doInput = true
            doOutput = bytes != null
            useCaches = false
            if (bytes != null) setFixedLengthStreamingMode(bytes.size)
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (!prefer.isNullOrBlank()) setRequestProperty("Prefer", prefer)
        }
        return try {
            if (bytes != null) {
                connection.outputStream.use { output -> output.write(bytes) }
            }
            val status = connection.responseCode
            val text = if (status in 200..299) {
                connection.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            } else {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
            if (status !in 200..299) throw IOException(translateSkillAssetError(text, status))
            text
        } catch (error: IOException) {
            throw error
        } catch (error: Throwable) {
            throw IOException("视觉 Skill 云端资产响应无效。", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun cleanDeviceId(deviceId: String): String {
        val clean = deviceId.trim().take(120)
        if (clean.isBlank()) throw IOException("无法识别当前设备，Skill 同步已暂停。")
        return clean
    }

    private fun OperationSkillAssetEnvelope.toSupabaseJson(
        userId: String,
        deviceId: String,
    ): JSONObject = JSONObject().apply {
        put("user_id", userId)
        put("workflow_id", workflowId.take(120))
        put("owner_device_id", deviceId)
        put("title", title.take(MAX_TEXT_CHARS))
        put("description", description.take(MAX_LONG_TEXT_CHARS))
        put("status", status.name)
        put("execution_mode", executionMode.name)
        put("app_packages", JSONArray(appPackages.map { it.take(160) }))
        put("skill_json", JSONObject(skillJson))
        put("workflow_json", JSONObject(workflowJson))
        put("approved_snapshot_json", approvedSnapshotJson?.let(::JSONObject) ?: JSONObject.NULL)
        put("current_version_number", currentVersionNumber ?: JSONObject.NULL)
        put("source_demonstration_id", sourceDemonstrationId ?: JSONObject.NULL)
        put("content_digest", contentDigest.take(128))
        put("learned_at_millis", learnedAtMillis.coerceAtLeast(0L))
        put("local_updated_at_millis", localUpdatedAtMillis.coerceAtLeast(0L))
        put("approved_at_millis", approvedAtMillis ?: JSONObject.NULL)
        put("deleted_at_millis", deletedAtMillis ?: JSONObject.NULL)
    }

    private fun OperationSkillAssetVersionEnvelope.toSupabaseJson(
        userId: String,
        deviceId: String,
    ): JSONObject = JSONObject().apply {
        put("user_id", userId)
        put("workflow_id", workflowId.take(120))
        put("version_id", versionId.take(160))
        put("version_number", versionNumber.coerceAtLeast(1))
        put("owner_device_id", deviceId)
        put("snapshot_json", JSONObject(snapshotJson))
        put("skill_json", JSONObject(skillJson))
        put("content_digest", contentDigest.take(128))
        put("approved_at_millis", approvedAtMillis.coerceAtLeast(0L))
    }

    private fun String.toAssetList(): List<OperationSkillAssetEnvelope> {
        val rows = JSONArray(ifBlank { "[]" })
        return buildList(rows.length()) {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val workflowId = row.optString("workflow_id").trim()
                val skillJson = row.optJSONObject("skill_json")?.toString().orEmpty()
                val workflowJson = row.optJSONObject("workflow_json")?.toString().orEmpty()
                val digest = row.optString("content_digest").trim()
                if (workflowId.isBlank() || skillJson.isBlank() || workflowJson.isBlank() || digest.isBlank()) continue
                add(
                    OperationSkillAssetEnvelope(
                        workflowId = workflowId,
                        title = row.optString("title"),
                        description = row.optString("description"),
                        status = enumValueOrDefault(row.optString("status"), WorkflowDraftStatus.ReadyForReview),
                        executionMode = enumValueOrDefault(row.optString("execution_mode"), WorkflowExecutionMode.CloudVisual),
                        appPackages = row.optJSONArray("app_packages").toStringList(),
                        workflowJson = workflowJson,
                        skillJson = skillJson,
                        approvedSnapshotJson = row.optJSONObject("approved_snapshot_json")?.toString(),
                        currentVersionNumber = row.optIntOrNull("current_version_number"),
                        sourceDemonstrationId = row.optString("source_demonstration_id").takeIf(String::isNotBlank),
                        contentDigest = digest,
                        learnedAtMillis = row.optLong("learned_at_millis", 0L).coerceAtLeast(0L),
                        localUpdatedAtMillis = row.optLong("local_updated_at_millis", 0L).coerceAtLeast(0L),
                        approvedAtMillis = row.optLongOrNull("approved_at_millis"),
                        deletedAtMillis = row.optLongOrNull("deleted_at_millis"),
                    ),
                )
            }
        }
    }

    private fun JSONArray?.toStringList(): List<String> = buildList {
        val array = this@toStringList ?: return@buildList
        for (index in 0 until array.length()) {
            array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
        }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)

    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key)) null else optLong(key)

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        value: String,
        fallback: T,
    ): T = runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)

    private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun translateSkillAssetError(raw: String, status: Int): String {
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
                message.contains(SKILL_ASSET_TABLE, ignoreCase = true) &&
                message.contains("schema cache", ignoreCase = true) ->
                "云端 Skill 资产表尚未建立，请执行 supabase-operation-skill-assets-v1.sql。"

            status == 401 -> "登录状态已失效，请重新登录。"
            status == 403 || message.contains("row-level security", ignoreCase = true) ||
                message.contains("policy", ignoreCase = true) ->
                "云端 Skill 资产权限尚未配置，请检查 Skill 表 RLS。"

            message.isNotBlank() -> message.take(180)
            else -> "视觉 Skill 云端同步失败：HTTP $status"
        }
    }

    private companion object {
        private const val MAX_TEXT_CHARS = 240
        private const val MAX_LONG_TEXT_CHARS = 2_000
    }
}

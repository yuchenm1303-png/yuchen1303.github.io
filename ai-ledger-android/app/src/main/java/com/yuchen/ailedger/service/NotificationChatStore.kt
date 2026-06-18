package com.yuchen.ailedger.service

import android.content.Context
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val NOTIFICATION_CHAT_PREFS = "notification_chat_store"
private const val NOTIFICATION_CHAT_STATE = "state"
private const val MAX_STORED_MESSAGES = 24
private const val MAX_STORED_TEXT_LENGTH = 6_000
private const val MAX_PROMPT_LENGTH = 2_000

data class NotificationChatSnapshot(
    val messages: List<ChatMessage> = emptyList(),
    val pendingCount: Int = 0,
    val lastError: String? = null,
    val failedPrompt: String? = null
) {
    val isProcessing: Boolean
        get() = pendingCount > 0
}

data class NotificationChatRequest(
    val requestId: String,
    val userMessageId: String,
    val pendingMessageId: String,
    val prompt: String
)

object NotificationChatStore {
    @Synchronized
    fun load(context: Context): NotificationChatSnapshot {
        val raw = preferences(context).getString(NOTIFICATION_CHAT_STATE, null)
            ?: return NotificationChatSnapshot()
        return runCatching { decode(raw) }.getOrDefault(NotificationChatSnapshot())
    }

    @Synchronized
    fun mergeAppMessages(context: Context, appMessages: List<ChatMessage>): NotificationChatSnapshot {
        val current = load(context)
        if (appMessages.isEmpty()) return current

        val merged = LinkedHashMap<String, ChatMessage>()
        current.messages.forEach { merged[it.id] = it }
        appMessages.forEach { message ->
            merged[message.id] = message.toStoredMessage()
        }

        return save(
            context,
            current.copy(messages = merged.values.sortedBy { it.createdAt }.takeLast(MAX_STORED_MESSAGES))
        )
    }

    @Synchronized
    fun enqueuePrompt(context: Context, prompt: String): NotificationChatRequest? {
        val cleanPrompt = prompt.trim().take(MAX_PROMPT_LENGTH)
        if (cleanPrompt.isBlank()) return null

        val now = System.currentTimeMillis()
        val requestId = UUID.randomUUID().toString()
        val userMessageId = "notification-user-$requestId"
        val pendingMessageId = "notification-assistant-$requestId"
        val current = load(context)
        val nextMessages = current.messages +
            ChatMessage(
                id = userMessageId,
                text = cleanPrompt,
                role = MessageRole.User,
                source = "notification_chat",
                createdAt = now
            ) +
            pendingMessage(pendingMessageId, now + 1L)

        save(
            context,
            current.copy(
                messages = nextMessages.takeLast(MAX_STORED_MESSAGES),
                pendingCount = current.pendingCount + 1,
                lastError = null,
                failedPrompt = null
            )
        )

        return NotificationChatRequest(requestId, userMessageId, pendingMessageId, cleanPrompt)
    }

    @Synchronized
    fun retryFailed(context: Context): NotificationChatRequest? {
        val current = load(context)
        val prompt = current.failedPrompt?.trim()?.take(MAX_PROMPT_LENGTH)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val failedIndex = current.messages.indexOfLast { message ->
            message.status == MessageStatus.Failed && message.source == "notification_chat_failed"
        }
        if (failedIndex < 0) return null

        val userIndex = (failedIndex - 1 downTo 0).firstOrNull { index ->
            val message = current.messages[index]
            message.role == MessageRole.User && message.text.trim() == prompt
        } ?: return null

        val requestId = UUID.randomUUID().toString()
        val pendingMessageId = "notification-assistant-$requestId"
        val messages = current.messages.toMutableList().apply {
            this[failedIndex] = pendingMessage(
                id = pendingMessageId,
                createdAt = maxOf(System.currentTimeMillis(), this[failedIndex].createdAt)
            )
        }
        save(
            context,
            current.copy(
                messages = messages.takeLast(MAX_STORED_MESSAGES),
                pendingCount = current.pendingCount + 1,
                lastError = null,
                failedPrompt = null
            )
        )

        return NotificationChatRequest(
            requestId = requestId,
            userMessageId = current.messages[userIndex].id,
            pendingMessageId = pendingMessageId,
            prompt = prompt
        )
    }

    @Synchronized
    fun requestMessages(context: Context, userMessageId: String): List<ChatMessage> {
        val messages = load(context).messages
        val targetIndex = messages.indexOfFirst { it.id == userMessageId }
        if (targetIndex < 0) return emptyList()
        return messages
            .take(targetIndex + 1)
            .filterNot { it.status == MessageStatus.Failed || it.status == MessageStatus.Sending }
            .filter { it.text.isNotBlank() }
    }

    @Synchronized
    fun complete(
        context: Context,
        pendingMessageId: String,
        reply: String,
        source: String?,
        model: String?,
        modelLabel: String?
    ): NotificationChatSnapshot {
        val current = load(context)
        if (current.messages.none { it.id == pendingMessageId && it.status == MessageStatus.Sending }) {
            return current
        }
        val cleanReply = reply.trim()
            .ifBlank { "AI 已完成处理，但没有返回可显示的文字。" }
            .take(MAX_STORED_TEXT_LENGTH)
        val messages = current.messages.map { message ->
            if (message.id != pendingMessageId) {
                message
            } else {
                message.copy(
                    text = cleanReply,
                    status = MessageStatus.Sent,
                    source = source ?: "notification_chat",
                    model = model,
                    modelLabel = modelLabel ?: "自动选择",
                    errorText = null
                )
            }
        }
        return save(
            context,
            current.copy(
                messages = messages.takeLast(MAX_STORED_MESSAGES),
                pendingCount = (current.pendingCount - 1).coerceAtLeast(0),
                lastError = null,
                failedPrompt = null
            )
        )
    }

    @Synchronized
    fun fail(
        context: Context,
        pendingMessageId: String,
        prompt: String,
        errorMessage: String
    ): NotificationChatSnapshot {
        val current = load(context)
        if (current.messages.none { it.id == pendingMessageId && it.status == MessageStatus.Sending }) {
            return current
        }
        val friendly = errorMessage.trim().ifBlank { "AI 请求失败，请稍后重试。" }.take(300)
        val messages = current.messages.map { message ->
            if (message.id != pendingMessageId) {
                message
            } else {
                message.copy(
                    text = friendly,
                    status = MessageStatus.Failed,
                    source = "notification_chat_failed",
                    modelLabel = "请求失败",
                    errorText = friendly
                )
            }
        }
        return save(
            context,
            current.copy(
                messages = messages.takeLast(MAX_STORED_MESSAGES),
                pendingCount = (current.pendingCount - 1).coerceAtLeast(0),
                lastError = friendly,
                failedPrompt = prompt.trim().take(MAX_PROMPT_LENGTH)
            )
        )
    }

    @Synchronized
    fun stopPending(context: Context): NotificationChatSnapshot {
        val current = load(context)
        val messages = current.messages.map { message ->
            if (message.status != MessageStatus.Sending || message.source != "notification_chat") {
                message
            } else {
                message.copy(
                    text = "已停止通知栏请求。",
                    status = MessageStatus.Sent,
                    source = "notification_chat",
                    modelLabel = "已停止",
                    errorText = null
                )
            }
        }
        return save(
            context,
            current.copy(
                messages = messages,
                pendingCount = 0,
                lastError = null,
                failedPrompt = null
            )
        )
    }

    @Synchronized
    fun clear(context: Context): NotificationChatSnapshot {
        return save(context, NotificationChatSnapshot())
    }

    private fun pendingMessage(id: String, createdAt: Long): ChatMessage {
        return ChatMessage(
            id = id,
            text = "正在思考…",
            role = MessageRole.Assistant,
            status = MessageStatus.Sending,
            source = "notification_chat",
            modelLabel = "自动选择",
            createdAt = createdAt
        )
    }

    private fun save(context: Context, snapshot: NotificationChatSnapshot): NotificationChatSnapshot {
        preferences(context)
            .edit()
            .putString(NOTIFICATION_CHAT_STATE, encode(snapshot))
            .commit()
        return snapshot
    }

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(
        NOTIFICATION_CHAT_PREFS,
        Context.MODE_PRIVATE
    )

    private fun encode(snapshot: NotificationChatSnapshot): String {
        return JSONObject().apply {
            put("pendingCount", snapshot.pendingCount)
            put("lastError", snapshot.lastError ?: JSONObject.NULL)
            put("failedPrompt", snapshot.failedPrompt ?: JSONObject.NULL)
            put("messages", JSONArray().apply {
                snapshot.messages.takeLast(MAX_STORED_MESSAGES).forEach { message ->
                    put(JSONObject().apply {
                        put("id", message.id)
                        put("text", message.text.take(MAX_STORED_TEXT_LENGTH))
                        put("role", message.role.name)
                        put("status", message.status.name)
                        put("source", message.source ?: JSONObject.NULL)
                        put("model", message.model ?: JSONObject.NULL)
                        put("modelLabel", message.modelLabel ?: JSONObject.NULL)
                        put("errorText", message.errorText ?: JSONObject.NULL)
                        put("createdAt", message.createdAt)
                    })
                }
            })
        }.toString()
    }

    private fun decode(raw: String): NotificationChatSnapshot {
        val root = JSONObject(raw)
        val array = root.optJSONArray("messages") ?: JSONArray()
        val messages = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    ChatMessage(
                        id = item.optString("id").ifBlank { "notification-restored-$index" },
                        text = item.optString("text").take(MAX_STORED_TEXT_LENGTH),
                        role = enumValueOrDefault(item.optString("role"), MessageRole.Assistant),
                        status = enumValueOrDefault(item.optString("status"), MessageStatus.Sent),
                        source = item.optNullableString("source"),
                        model = item.optNullableString("model"),
                        modelLabel = item.optNullableString("modelLabel"),
                        errorText = item.optNullableString("errorText"),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }
        return NotificationChatSnapshot(
            messages = messages.takeLast(MAX_STORED_MESSAGES),
            pendingCount = root.optInt("pendingCount", 0).coerceAtLeast(0),
            lastError = root.optNullableString("lastError"),
            failedPrompt = root.optNullableString("failedPrompt")?.take(MAX_PROMPT_LENGTH)
        )
    }

    private fun ChatMessage.toStoredMessage(): ChatMessage {
        return copy(
            text = text.take(MAX_STORED_TEXT_LENGTH),
            attachments = emptyList(),
            webSources = emptyList(),
            structuredData = null
        )
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T {
        return enumValues<T>().firstOrNull { it.name == value } ?: default
    }
}

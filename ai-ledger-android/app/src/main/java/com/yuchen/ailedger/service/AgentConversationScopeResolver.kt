package com.yuchen.ailedger.service

import android.content.Context
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.MessageRole
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

private const val AGENT_CONVERSATION_SCOPE_PREFS = "agent-conversation-scope-v1"
private const val AGENT_CONVERSATION_SCOPE_STATE = "state"
private const val AGENT_CONVERSATION_SCOPE_SCHEMA = "ai_ledger_agent_conversation_scope_v1"
private const val AGENT_CONVERSATION_SCOPE_MAX_MESSAGE_IDS = 96

/**
 * Resolves one stable conversation id from the user-message ids already present in a chat.
 *
 * Every user message is mapped to the same conversation id as the existing messages around it.
 * This keeps the scope stable when persisted chat history is truncated, while a newly cleared chat
 * naturally receives a new scope because its first user-message id has never been seen before.
 */
internal object AgentConversationScopeResolver {
    private data class Entry(
        val conversationId: String,
        val updatedAt: Long,
    )

    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>(32, 0.75f, true)
    private var loaded = false

    fun resolve(context: Context?, messages: List<ChatMessage>): String {
        val userMessageIds = messages.asSequence()
            .filter { message -> message.role == MessageRole.User && message.id.isNotBlank() }
            .map { message -> message.id.trim().take(180) }
            .filter(String::isNotBlank)
            .distinct()
            .toList()
        if (userMessageIds.isEmpty()) return ""

        return synchronized(lock) {
            ensureLoadedLocked(context)
            val now = System.currentTimeMillis()
            val mappedConversation = userMessageIds.asReversed()
                .mapNotNull { messageId -> entries[messageId] }
                .maxByOrNull(Entry::updatedAt)
                ?.conversationId
            val conversationId = mappedConversation ?: createConversationId(userMessageIds.first())
            var changed = false
            userMessageIds.forEach { messageId ->
                val previous = entries[messageId]
                if (previous?.conversationId != conversationId) changed = true
                entries[messageId] = Entry(conversationId = conversationId, updatedAt = now)
            }
            if (trimLocked()) changed = true
            if (changed) persistLocked(context)
            conversationId
        }
    }

    internal fun clearForTest() = synchronized(lock) {
        entries.clear()
        loaded = false
    }

    private fun ensureLoadedLocked(context: Context?) {
        if (loaded) return
        loaded = true
        val raw = context?.applicationContext
            ?.getSharedPreferences(AGENT_CONVERSATION_SCOPE_PREFS, Context.MODE_PRIVATE)
            ?.getString(AGENT_CONVERSATION_SCOPE_STATE, null)
            ?: return
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return
        if (root.optString("schema") != AGENT_CONVERSATION_SCOPE_SCHEMA) return
        val records = root.optJSONArray("entries") ?: return
        for (index in 0 until records.length()) {
            val item = records.optJSONObject(index) ?: continue
            val messageId = item.optString("messageId").trim().take(180)
            val conversationId = item.optString("conversationId").trim().take(180)
            if (messageId.isBlank() || conversationId.isBlank()) continue
            entries[messageId] = Entry(
                conversationId = conversationId,
                updatedAt = item.optLong("updatedAt", 0L),
            )
        }
        trimLocked()
    }

    private fun persistLocked(context: Context?) {
        val appContext = context?.applicationContext ?: return
        val root = JSONObject().apply {
            put("schema", AGENT_CONVERSATION_SCOPE_SCHEMA)
            put("entries", JSONArray().apply {
                entries.entries
                    .sortedBy { (_, value) -> value.updatedAt }
                    .forEach { (messageId, entry) ->
                        put(JSONObject().apply {
                            put("messageId", messageId)
                            put("conversationId", entry.conversationId)
                            put("updatedAt", entry.updatedAt)
                        })
                    }
            })
        }
        appContext.getSharedPreferences(AGENT_CONVERSATION_SCOPE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(AGENT_CONVERSATION_SCOPE_STATE, root.toString())
            .apply()
    }

    private fun trimLocked(): Boolean {
        var changed = false
        while (entries.size > AGENT_CONVERSATION_SCOPE_MAX_MESSAGE_IDS) {
            val oldestKey = entries.entries.minByOrNull { it.value.updatedAt }?.key ?: break
            entries.remove(oldestKey)
            changed = true
        }
        return changed
    }

    private fun createConversationId(anchorMessageId: String): String {
        val safeAnchor = anchorMessageId
            .filter { char -> char.isLetterOrDigit() || char == '-' || char == '_' || char == '.' }
            .take(96)
        if (safeAnchor.isNotBlank()) return "chat_$safeAnchor"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(anchorMessageId.toByteArray(Charsets.UTF_8))
            .take(16)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return "chat_$digest"
    }
}

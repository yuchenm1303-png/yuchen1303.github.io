package com.yuchen.ailedger.service

import android.content.Context
import java.security.MessageDigest
import org.json.JSONObject

private const val PROJECT_SESSION_PREFS = "project-workspace-session-v2"
private const val PROJECT_SESSION_ACTIVE_ID_PREFIX = "active-project-id-"
private const val PROJECT_SESSION_LEGACY_SCOPE = "legacy"
private const val PROJECT_SESSION_MAX_SCOPES = 24

/**
 * Keeps the explicitly used project visible to later turns in the same chat thread.
 *
 * Project source remains in [ProjectWorkspaceStore]. This bridge stores only a project id per
 * conversation scope plus a bounded in-memory summary cache. It never falls back to the newest
 * project and never exposes one chat thread's active project to another thread.
 */
internal object ProjectWorkspaceSessionContext {
    private val lock = Any()
    private val activeProjects = LinkedHashMap<String, JSONObject>(16, 0.75f, true)

    /** Legacy compatibility for callers that do not yet carry a conversation id. */
    fun update(project: JSONObject?) {
        update(context = null, conversationId = PROJECT_SESSION_LEGACY_SCOPE, project = project)
    }

    /** Legacy compatibility for callers that do not yet carry a conversation id. */
    fun update(context: Context?, project: JSONObject?) {
        update(context = context, conversationId = PROJECT_SESSION_LEGACY_SCOPE, project = project)
    }

    fun update(context: Context?, conversationId: String?, project: JSONObject?) {
        val projectId = project?.optString("projectId")?.trim().orEmpty()
        if (projectId.isBlank()) return
        val scope = normalizeScope(conversationId)
        synchronized(lock) {
            activeProjects[scope] = JSONObject(project.toString())
            trimLocked()
        }
        context?.applicationContext
            ?.getSharedPreferences(PROJECT_SESSION_PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(preferenceKey(scope), projectId)
            ?.apply()
    }

    /** Legacy compatibility for callers that do not yet carry a conversation id. */
    fun current(context: Context?): JSONObject? = current(context, PROJECT_SESSION_LEGACY_SCOPE)

    fun current(context: Context?, conversationId: String?): JSONObject? {
        val scope = normalizeScope(conversationId)
        synchronized(lock) {
            activeProjects[scope]?.let { return JSONObject(it.toString()) }
        }
        val appContext = context?.applicationContext ?: return null
        val projectId = appContext
            .getSharedPreferences(PROJECT_SESSION_PREFS, Context.MODE_PRIVATE)
            .getString(preferenceKey(scope), null)
            ?.trim()
            .orEmpty()
        if (projectId.isBlank()) return null
        val project = runCatching { ProjectWorkspaceStore(appContext).getProject(projectId) }.getOrNull()
        if (project == null) {
            appContext.getSharedPreferences(PROJECT_SESSION_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(preferenceKey(scope))
                .apply()
            return null
        }
        return project.toJson().also { update(appContext, scope, it) }
    }

    /** Legacy compatibility for callers that do not yet carry a conversation id. */
    fun clear(context: Context? = null) {
        clear(context, PROJECT_SESSION_LEGACY_SCOPE)
    }

    fun clear(context: Context?, conversationId: String?) {
        val scope = normalizeScope(conversationId)
        synchronized(lock) { activeProjects.remove(scope) }
        context?.applicationContext
            ?.getSharedPreferences(PROJECT_SESSION_PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.remove(preferenceKey(scope))
            ?.apply()
    }

    private fun normalizeScope(value: String?): String = value
        ?.trim()
        ?.take(180)
        ?.takeIf(String::isNotBlank)
        ?: PROJECT_SESSION_LEGACY_SCOPE

    private fun preferenceKey(scope: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(scope.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return PROJECT_SESSION_ACTIVE_ID_PREFIX + digest
    }

    private fun trimLocked() {
        while (activeProjects.size > PROJECT_SESSION_MAX_SCOPES) {
            val iterator = activeProjects.entries.iterator()
            if (!iterator.hasNext()) return
            iterator.next()
            iterator.remove()
        }
    }
}

package com.yuchen.ailedger.service

import android.content.Context
import org.json.JSONObject

private const val PROJECT_SESSION_PREFS = "project-workspace-session-v1"
private const val PROJECT_SESSION_ACTIVE_ID = "active-project-id"

/**
 * Keeps the explicitly used project visible to later chat turns.
 *
 * The project source remains in [ProjectWorkspaceStore]. This bridge stores only the active
 * project id and a compact in-memory summary, so phrases such as “继续修改这个网页” can be
 * grounded after the previous Agent Workspace or app process has completed. It never guesses
 * that the newest project is active when no explicit selection has been recorded.
 */
internal object ProjectWorkspaceSessionContext {
    private val lock = Any()
    private var activeProject: JSONObject? = null

    fun update(project: JSONObject?) {
        update(context = null, project = project)
    }

    fun update(context: Context?, project: JSONObject?) {
        val projectId = project?.optString("projectId")?.trim().orEmpty()
        if (projectId.isBlank()) return
        synchronized(lock) {
            activeProject = JSONObject(project.toString())
        }
        context?.applicationContext
            ?.getSharedPreferences(PROJECT_SESSION_PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(PROJECT_SESSION_ACTIVE_ID, projectId)
            ?.apply()
    }

    fun current(context: Context?): JSONObject? {
        synchronized(lock) {
            activeProject?.let { return JSONObject(it.toString()) }
        }
        val appContext = context?.applicationContext ?: return null
        val projectId = appContext
            .getSharedPreferences(PROJECT_SESSION_PREFS, Context.MODE_PRIVATE)
            .getString(PROJECT_SESSION_ACTIVE_ID, null)
            ?.trim()
            .orEmpty()
        if (projectId.isBlank()) return null
        val project = runCatching { ProjectWorkspaceStore(appContext).getProject(projectId) }.getOrNull()
        if (project == null) {
            appContext.getSharedPreferences(PROJECT_SESSION_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(PROJECT_SESSION_ACTIVE_ID)
                .apply()
            return null
        }
        return project.toJson().also { update(appContext, it) }
    }

    fun clear(context: Context? = null) {
        synchronized(lock) { activeProject = null }
        context?.applicationContext
            ?.getSharedPreferences(PROJECT_SESSION_PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.remove(PROJECT_SESSION_ACTIVE_ID)
            ?.apply()
    }
}

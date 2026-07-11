package com.yuchen.ailedger.service

import android.content.Context
import org.json.JSONObject

/**
 * Keeps the most recently used project visible to later chat turns.
 *
 * The project source remains in [ProjectWorkspaceStore]. This bridge only exposes a compact,
 * non-secret project identity so phrases such as “继续修改这个网页” can be grounded after the
 * previous Agent Workspace has completed.
 */
internal object ProjectWorkspaceSessionContext {
    private val lock = Any()
    private var activeProject: JSONObject? = null

    fun update(project: JSONObject?) {
        val projectId = project?.optString("projectId")?.trim().orEmpty()
        if (projectId.isBlank()) return
        synchronized(lock) {
            activeProject = JSONObject(project.toString())
        }
    }

    fun current(context: Context?): JSONObject? {
        synchronized(lock) {
            activeProject?.let { return JSONObject(it.toString()) }
        }
        val latest = context
            ?.let { appContext -> runCatching { ProjectWorkspaceStore(appContext).listProjects(limit = 1).firstOrNull() }.getOrNull() }
            ?: return null
        return latest.toJson().also(::update)
    }

    fun clear() {
        synchronized(lock) { activeProject = null }
    }
}

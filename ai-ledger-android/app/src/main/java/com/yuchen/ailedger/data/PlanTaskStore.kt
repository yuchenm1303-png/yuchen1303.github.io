package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.model.PlanRepeatMode
import com.yuchen.ailedger.model.PlanTask
import com.yuchen.ailedger.model.PlanTaskType
import org.json.JSONArray
import org.json.JSONObject

class PlanTaskStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun loadTasks(): List<PlanTask> = synchronized(lock) {
        loadTasksLocked()
    }

    fun saveTasks(tasks: List<PlanTask>) = synchronized(lock) {
        saveTasksLocked(tasks)
    }

    fun updateTask(id: String, transform: (PlanTask) -> PlanTask): PlanTask? = synchronized(lock) {
        val tasks = loadTasksLocked().toMutableList()
        val index = tasks.indexOfFirst { it.id == id }
        if (index < 0) return@synchronized null
        val updated = transform(tasks[index])
        tasks[index] = updated
        saveTasksLocked(tasks)
        updated
    }

    private fun loadTasksLocked(): List<PlanTask> {
        val raw = preferences.getString(KEY_TASKS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toPlanTaskOrNull()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveTasksLocked(tasks: List<PlanTask>) {
        val array = JSONArray()
        tasks.forEach { task -> array.put(task.toJson()) }
        preferences.edit().putString(KEY_TASKS, array.toString()).commit()
    }

    private fun PlanTask.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("note", note)
        put("type", type.name)
        put("repeatMode", repeatMode.name)
        put("scheduledAtMillis", scheduledAtMillis)
        put("nextRunAtMillis", nextRunAtMillis ?: JSONObject.NULL)
        put("enabled", enabled)
        put("createdAtMillis", createdAtMillis)
        put("lastRunAtMillis", lastRunAtMillis ?: JSONObject.NULL)
        put("lastResult", lastResult ?: JSONObject.NULL)
    }

    private fun JSONObject.toPlanTaskOrNull(): PlanTask? {
        val id = optString("id").trim()
        val title = optString("title").trim()
        val scheduledAtMillis = optLong("scheduledAtMillis", 0L)
        if (id.isBlank() || title.isBlank() || scheduledAtMillis <= 0L) return null
        val type = runCatching {
            PlanTaskType.valueOf(optString("type", PlanTaskType.Reminder.name))
        }.getOrDefault(PlanTaskType.Reminder)
        val repeatMode = runCatching {
            PlanRepeatMode.valueOf(optString("repeatMode", PlanRepeatMode.Once.name))
        }.getOrDefault(PlanRepeatMode.Once)
        return PlanTask(
            id = id,
            title = title,
            note = optString("note"),
            type = type,
            repeatMode = repeatMode,
            scheduledAtMillis = scheduledAtMillis,
            nextRunAtMillis = optNullableLong("nextRunAtMillis"),
            enabled = optBoolean("enabled", true),
            createdAtMillis = optLong("createdAtMillis", scheduledAtMillis),
            lastRunAtMillis = optNullableLong("lastRunAtMillis"),
            lastResult = optNullableString("lastResult"),
        )
    }

    private fun JSONObject.optNullableLong(name: String): Long? {
        if (!has(name) || isNull(name)) return null
        return runCatching { getLong(name) }.getOrNull()
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).trim().takeIf { it.isNotBlank() }
    }

    private companion object {
        const val PREFERENCES_NAME = "ai_ledger_plan_tasks"
        const val KEY_TASKS = "tasks_v1"
        val lock = Any()
    }
}

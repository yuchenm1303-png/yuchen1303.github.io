package com.yuchen.ailedger.data

import org.json.JSONObject

data class AssistantLocalMemorySnapshot(
    val navigationHomeAddress: String = "",
    val navigationSchoolAddress: String = "",
    val navigationCompanyAddress: String = "",
    val navigationDormAddress: String = "",
    val updatedAtMs: Long = 0L,
) {
    val hasNavigationMemory: Boolean
        get() = navigationHomeAddress.isNotBlank() ||
            navigationSchoolAddress.isNotBlank() ||
            navigationCompanyAddress.isNotBlank() ||
            navigationDormAddress.isNotBlank()

    fun toJson(): JSONObject = JSONObject().apply {
        put("schema", "assistant_local_memory_v1")
        put("updatedAt", updatedAtMs)
        put("navigation", JSONObject().apply {
            put("home", navigationHomeAddress)
            put("school", navigationSchoolAddress)
            put("company", navigationCompanyAddress)
            put("dorm", navigationDormAddress)
            put("hasAny", hasNavigationMemory)
        })
        put("policy", JSONObject().apply {
            put("writeMode", "explicit_user_intent_only")
            put("description", "Only user-explicitly saved local preferences are exposed here. Do not invent or overwrite memory without explicit user intent.")
        })
    }

    fun summaryLines(): List<String> = buildList {
        if (navigationHomeAddress.isNotBlank()) add("家：$navigationHomeAddress")
        if (navigationSchoolAddress.isNotBlank()) add("学校：$navigationSchoolAddress")
        if (navigationCompanyAddress.isNotBlank()) add("公司：$navigationCompanyAddress")
        if (navigationDormAddress.isNotBlank()) add("宿舍：$navigationDormAddress")
    }
}

object AssistantLocalMemoryRuntime {
    @Volatile
    private var latestSnapshot: AssistantLocalMemorySnapshot = AssistantLocalMemorySnapshot()

    fun update(preferences: AssistantPreferences) {
        latestSnapshot = AssistantLocalMemorySnapshot(
            navigationHomeAddress = preferences.navigationHomeAddress.trim().take(MAX_MEMORY_VALUE_CHARS),
            navigationSchoolAddress = preferences.navigationSchoolAddress.trim().take(MAX_MEMORY_VALUE_CHARS),
            navigationCompanyAddress = preferences.navigationCompanyAddress.trim().take(MAX_MEMORY_VALUE_CHARS),
            navigationDormAddress = preferences.navigationDormAddress.trim().take(MAX_MEMORY_VALUE_CHARS),
            updatedAtMs = System.currentTimeMillis(),
        )
    }

    fun current(): AssistantLocalMemorySnapshot = latestSnapshot

    fun currentSummary(): String {
        val lines = latestSnapshot.summaryLines()
        return if (lines.isEmpty()) "" else lines.joinToString("\n")
    }

    private const val MAX_MEMORY_VALUE_CHARS = 80
}

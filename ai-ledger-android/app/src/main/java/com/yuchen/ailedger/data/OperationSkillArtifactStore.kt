package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.model.LearnedVisualSkill
import com.yuchen.ailedger.model.VisualSkillInput
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** 保存云端生成的 Skill 语义，不保存固定点击路线、节点选择器或坐标脚本。 */
class OperationSkillArtifactStore(context: Context) {
    private val directory = File(context.applicationContext.noBackupFilesDir, DIRECTORY).apply { mkdirs() }

    fun save(skill: LearnedVisualSkill) {
        val target = fileFor(skill.workflowId)
        val temporary = File(directory, "${target.name}.tmp")
        temporary.writeText(skill.toJson().toString(), Charsets.UTF_8)
        if (!temporary.renameTo(target)) {
            target.writeText(temporary.readText(Charsets.UTF_8), Charsets.UTF_8)
            temporary.delete()
        }
    }

    fun load(workflowId: String): LearnedVisualSkill? {
        val file = fileFor(workflowId)
        if (!file.isFile) return null
        return runCatching { JSONObject(file.readText(Charsets.UTF_8)).toSkill() }.getOrNull()
    }

    fun delete(workflowId: String) {
        runCatching { fileFor(workflowId).delete() }
    }

    private fun fileFor(workflowId: String): File {
        val safeId = workflowId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
        require(safeId.isNotBlank()) { "invalid workflow id" }
        return File(directory, "$safeId.json")
    }

    private fun LearnedVisualSkill.toJson(): JSONObject = JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("workflowId", workflowId)
        put("name", name)
        put("description", description)
        put("triggerExamples", JSONArray(triggerExamples))
        put("inputs", JSONArray().apply {
            inputs.forEach { input ->
                put(JSONObject().apply {
                    put("key", input.key)
                    put("label", input.label)
                    put("description", input.description)
                    put("required", input.required)
                    put("sensitive", input.sensitive)
                })
            }
        })
        put("operatingPrinciples", JSONArray(operatingPrinciples))
        put("successCriteria", JSONArray(successCriteria))
        put("safetyRules", JSONArray(safetyRules))
        put("cloudSummary", cloudSummary)
        put("confidence", confidence.toDouble())
        put("learnedAtMillis", learnedAtMillis)
    }

    private fun JSONObject.toSkill(): LearnedVisualSkill = LearnedVisualSkill(
        schemaVersion = optString("schemaVersion", LearnedVisualSkill.SCHEMA_VERSION),
        workflowId = getString("workflowId"),
        name = optString("name"),
        description = optString("description"),
        triggerExamples = optJSONArray("triggerExamples").toStringList(),
        inputs = buildList {
            val array = optJSONArray("inputs") ?: return@buildList
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val key = item.optString("key").trim()
                val label = item.optString("label").trim()
                if (key.isBlank() || label.isBlank()) continue
                add(
                    VisualSkillInput(
                        key = key,
                        label = label,
                        description = item.optString("description"),
                        required = item.optBoolean("required", true),
                        sensitive = item.optBoolean("sensitive", false),
                    ),
                )
            }
        },
        operatingPrinciples = optJSONArray("operatingPrinciples").toStringList(),
        successCriteria = optJSONArray("successCriteria").toStringList(),
        safetyRules = optJSONArray("safetyRules").toStringList(),
        cloudSummary = optString("cloudSummary"),
        confidence = optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f),
        learnedAtMillis = optLong("learnedAtMillis"),
    )

    private fun JSONArray?.toStringList(): List<String> = buildList {
        val array = this@toStringList ?: return@buildList
        for (index in 0 until array.length()) {
            array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
        }
    }

    companion object {
        private const val DIRECTORY = "operation-skills"
    }
}

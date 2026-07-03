package com.yuchen.ailedger.data

import com.yuchen.ailedger.model.LearnedVisualSkill
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.VisualSkillInput
import org.json.JSONArray
import org.json.JSONObject

/** 视觉 Skill 的唯一 JSON 协议，供本机存储和不可变审核版本共同使用。 */
object OperationSkillJsonCodec {
    fun encode(skill: LearnedVisualSkill): String = skill.toJson().toString()

    fun decode(raw: String): LearnedVisualSkill = JSONObject(raw).toSkill()

    fun encodeApprovedSnapshot(
        draft: LearnedWorkflowDraft,
        skill: LearnedVisualSkill,
    ): String = JSONObject().apply {
        put("schemaVersion", APPROVAL_SCHEMA_VERSION)
        put("workflow", JSONObject(OperationWorkflowJsonCodec.encode(draft)))
        put("skill", skill.toJson())
    }.toString()

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

    private const val APPROVAL_SCHEMA_VERSION = "ai_ledger_visual_skill_approval_v1"
}

package com.yuchen.ailedger.data

import com.yuchen.ailedger.model.LearnedVisualSkill
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.VisualSkillInput
import com.yuchen.ailedger.model.VisualSkillRouteStep
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
        put("routeSteps", JSONArray().apply {
            routeSteps.forEach { step ->
                put(JSONObject().apply {
                    put("order", step.order)
                    put("instruction", step.instruction)
                    put("visualAnchor", step.visualAnchor)
                    put("expectedEvidence", step.expectedEvidence)
                    put("fallback", step.fallback)
                    put("startState", step.startState)
                    put("visualAnchors", JSONArray(step.visualAnchors))
                    put("preferredAction", step.preferredAction)
                    put("expectedEvidenceList", JSONArray(step.expectedEvidenceList))
                    put("discouragedActions", JSONArray(step.discouragedActions))
                    put("fallbackPolicy", step.fallbackPolicy)
                    put("skippable", step.skippable)
                })
            }
        })
        put("successCriteria", JSONArray(successCriteria))
        put("safetyRules", JSONArray(safetyRules))
        put("cloudSummary", cloudSummary)
        put("confidence", confidence.toDouble())
        put("learnedAtMillis", learnedAtMillis)
    }

    private fun JSONObject.toSkill(): LearnedVisualSkill {
        val routeSteps = optJSONArray("routeSteps").toRouteStepList()
        val routeSummaries = routeSteps.routeCheckpointSummaries()
        val principles = optJSONArray("operatingPrinciples").toStringList()
        return LearnedVisualSkill(
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
            operatingPrinciples = (routeSummaries + principles).distinct(),
            routeSteps = routeSteps,
            successCriteria = optJSONArray("successCriteria").toStringList(),
            safetyRules = optJSONArray("safetyRules").toStringList(),
            cloudSummary = optString("cloudSummary").withRouteSummary(routeSummaries),
            confidence = optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f),
            learnedAtMillis = optLong("learnedAtMillis"),
        )
    }

    private fun JSONArray?.toRouteStepList(): List<VisualSkillRouteStep> = buildList {
        val array = this@toRouteStepList ?: return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val instruction = item.optString("instruction").trim()
                .ifBlank { item.optString("preferredAction").trim() }
            if (instruction.isBlank()) continue
            add(
                VisualSkillRouteStep(
                    order = item.optInt("order", index + 1).coerceAtLeast(1),
                    instruction = instruction,
                    visualAnchor = item.optString("visualAnchor").trim(),
                    expectedEvidence = item.optString("expectedEvidence").trim(),
                    fallback = item.optString("fallback").trim(),
                    startState = item.optString("startState").trim(),
                    visualAnchors = item.optJSONArray("visualAnchors").toStringList(),
                    preferredAction = item.optString("preferredAction").trim(),
                    expectedEvidenceList = item.optJSONArray("expectedEvidenceList").toStringList(),
                    discouragedActions = item.optJSONArray("discouragedActions").toStringList(),
                    fallbackPolicy = item.optString("fallbackPolicy").trim(),
                    skippable = item.optBoolean("skippable", false),
                ),
            )
        }
    }.sortedBy(VisualSkillRouteStep::order)

    private fun List<VisualSkillRouteStep>.routeCheckpointSummaries(): List<String> = take(4).mapIndexed { index, step ->
        val anchor = step.effectiveAnchors.firstOrNull().orEmpty()
        val evidence = step.effectiveEvidence.firstOrNull().orEmpty()
        buildString {
            append("路线检查点：").append(index + 1).append(". ").append(step.instruction)
            if (anchor.isNotBlank()) append("；锚点：").append(anchor)
            if (evidence.isNotBlank()) append("；证据：").append(evidence)
        }.take(320)
    }

    private fun String.withRouteSummary(routeSummaries: List<String>): String {
        val clean = trim()
        if (routeSummaries.isEmpty()) return clean
        val routeText = "演示路线：" + routeSummaries.joinToString("；") { it.removePrefix("路线检查点：") }
        return listOf(clean, routeText).filter(String::isNotBlank).joinToString("\n").take(1_000)
    }

    private fun JSONArray?.toStringList(): List<String> = buildList {
        val array = this@toStringList ?: return@buildList
        for (index in 0 until array.length()) {
            array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
        }
    }

    private const val APPROVAL_SCHEMA_VERSION = "ai_ledger_visual_skill_approval_v1"
}

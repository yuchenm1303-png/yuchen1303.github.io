package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject

data class VisualActionIntent(
    val purpose: String = "", val milestoneId: String = "",
    val expectedEvidence: List<String> = emptyList(), val failureEvidence: List<String> = emptyList(),
    val exploratory: Boolean = false, val reversible: Boolean = true, val confidence: Float? = null,
    val hypothesisId: String = "", val legacyMode: Boolean = true,
) {
    val hasSemanticContract get() = !legacyMode
    fun toJson() = JSONObject().apply {
        put("purpose", purpose); put("milestoneId", milestoneId)
        put("expectedEvidence", JSONArray(expectedEvidence)); put("failureEvidence", JSONArray(failureEvidence))
        put("exploratory", exploratory); put("reversible", reversible); confidence?.let { put("confidence", it) }
        put("hypothesisId", hypothesisId); put("legacyMode", legacyMode)
    }
}

data class VisualFailedHypothesis(
    val hypothesisId: String, val milestoneId: String, val pageStateId: String,
    val actionSignature: String, val actionCluster: String, val purpose: String,
    val failureReason: String, val count: Int = 1,
) {
    fun toJson() = JSONObject().apply {
        put("hypothesisId", hypothesisId); put("milestoneId", milestoneId); put("pageStateId", pageStateId)
        put("actionSignature", actionSignature); put("actionCluster", actionCluster); put("purpose", purpose)
        put("failureReason", failureReason); put("count", count)
    }
}

data class VisualBlockedAction(
    val milestoneId: String, val pageStateId: String, val actionCluster: String,
    val hypothesisId: String, val reason: String,
) {
    fun toJson() = JSONObject().apply {
        put("milestoneId", milestoneId); put("pageStateId", pageStateId); put("actionCluster", actionCluster)
        put("hypothesisId", hypothesisId); put("reason", reason)
    }
}

data class VisualPageState(val id: String, val packageName: String, val summary: String) {
    fun toJson() = JSONObject().apply { put("id", id); put("packageName", packageName); put("summary", summary) }
}

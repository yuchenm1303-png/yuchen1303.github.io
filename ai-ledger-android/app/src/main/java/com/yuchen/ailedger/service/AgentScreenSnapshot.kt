package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject

private const val SNAPSHOT_TEXT_LIMIT = 30
private const val SNAPSHOT_CLICKABLE_LIMIT = 30
private const val SNAPSHOT_INPUT_LIMIT = 10
private const val SNAPSHOT_SCROLLABLE_LIMIT = 10

data class AgentScreenNode(
    val id: String,
    val text: String,
    val className: String,
    val bounds: String,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
)

data class AgentScreenSnapshot(
    val currentApp: String,
    val packageName: String,
    val nodeCount: Int,
    val texts: List<String>,
    val clickableNodes: List<AgentScreenNode>,
    val inputNodes: List<AgentScreenNode>,
    val scrollableNodes: List<AgentScreenNode>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("currentApp", currentApp)
        put("packageName", packageName)
        put("nodeCount", nodeCount)
        put("texts", JSONArray(texts))
        put("clickableNodes", clickableNodes.toJsonArray())
        put("inputNodes", inputNodes.toJsonArray())
        put("scrollableNodes", scrollableNodes.toJsonArray())
    }
}

fun ScreenObservation.toAgentScreenSnapshot(): AgentScreenSnapshot {
    val appPackage = packageName.ifBlank { "unknown" }
    return AgentScreenSnapshot(
        currentApp = appPackage,
        packageName = appPackage,
        nodeCount = nodeCount,
        texts = textItems
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(SNAPSHOT_TEXT_LIMIT),
        clickableNodes = clickableItems.toAgentNodes(SNAPSHOT_CLICKABLE_LIMIT),
        inputNodes = inputItems.toAgentNodes(SNAPSHOT_INPUT_LIMIT),
        scrollableNodes = scrollableItems.toAgentNodes(SNAPSHOT_SCROLLABLE_LIMIT),
    )
}

private fun List<ObservedScreenNode>.toAgentNodes(limit: Int): List<AgentScreenNode> {
    return map {
        AgentScreenNode(
            id = it.id,
            text = it.text.trim().take(80),
            className = it.className.take(48),
            bounds = it.bounds,
            clickable = it.clickable,
            editable = it.editable,
            scrollable = it.scrollable,
        )
    }.take(limit)
}

private fun List<AgentScreenNode>.toJsonArray(): JSONArray {
    return JSONArray().apply {
        forEach { node ->
            put(JSONObject().apply {
                put("id", node.id)
                put("text", node.text)
                put("className", node.className)
                put("bounds", node.bounds)
                put("clickable", node.clickable)
                put("editable", node.editable)
                put("scrollable", node.scrollable)
            })
        }
    }
}

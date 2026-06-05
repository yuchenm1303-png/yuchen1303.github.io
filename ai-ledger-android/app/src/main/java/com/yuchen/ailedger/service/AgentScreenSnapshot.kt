package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject

private const val SNAPSHOT_TEXT_LIMIT = 48
private const val SNAPSHOT_ALL_NODE_LIMIT = 96
private const val SNAPSHOT_CLICKABLE_LIMIT = 80
private const val SNAPSHOT_INPUT_LIMIT = 16
private const val SNAPSHOT_SCROLLABLE_LIMIT = 24

data class AgentScreenNode(
    val id: String,
    val text: String,
    val className: String,
    val bounds: String,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
)

data class AgentScreenVisual(
    val available: Boolean,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val displayWidth: Int,
    val displayHeight: Int,
    val base64Jpeg: String,
    val source: String,
    val reason: String,
) {
    val hasImage: Boolean
        get() = available && base64Jpeg.isNotBlank() && width > 0 && height > 0

    fun toJson(): JSONObject = JSONObject().apply {
        put("available", available)
        put("mimeType", mimeType)
        put("width", width)
        put("height", height)
        put("displayWidth", displayWidth)
        put("displayHeight", displayHeight)
        put("source", source)
        put("reason", reason)
        if (hasImage) put("base64Jpeg", base64Jpeg)
    }
}

data class AgentScreenSnapshot(
    val currentApp: String,
    val packageName: String,
    val nodeCount: Int,
    val capturedNodeCount: Int,
    val texts: List<String>,
    val allNodes: List<AgentScreenNode>,
    val clickableNodes: List<AgentScreenNode>,
    val inputNodes: List<AgentScreenNode>,
    val scrollableNodes: List<AgentScreenNode>,
    val visual: AgentScreenVisual? = null,
) {
    val hasVisualImage: Boolean
        get() = visual?.hasImage == true

    fun toJson(includeImage: Boolean = true): JSONObject = JSONObject().apply {
        put("currentApp", currentApp)
        put("packageName", packageName)
        put("nodeCount", nodeCount)
        put("capturedNodeCount", capturedNodeCount)
        put("texts", JSONArray(texts))
        put("allNodes", allNodes.toJsonArray())
        put("clickableNodes", clickableNodes.toJsonArray())
        put("inputNodes", inputNodes.toJsonArray())
        put("scrollableNodes", scrollableNodes.toJsonArray())
        visual?.let { item ->
            put("visual", if (includeImage) item.toJson() else item.copy(base64Jpeg = "").toJson())
        }
        put("confidence", JSONObject().apply {
            put("hasUsefulNodes", clickableNodes.isNotEmpty() || inputNodes.isNotEmpty() || scrollableNodes.isNotEmpty())
            put("hasAnyCapturedNode", allNodes.isNotEmpty())
            put("needsVisualFallback", needsVisualFallback())
            put("hasVisualImage", hasVisualImage)
        })
    }

    fun needsVisualFallback(): Boolean {
        return !hasVisualImage && (clickableNodes.isEmpty() || texts.isEmpty() || capturedNodeCount <= 3)
    }
}

fun ScreenObservation.toAgentScreenSnapshot(): AgentScreenSnapshot {
    val appPackage = packageName.ifBlank { "unknown" }
    val normalizedAllItems = if (allItems.isNotEmpty()) allItems else (clickableItems + inputItems + scrollableItems)
        .distinctBy { it.bounds + it.text + it.className }
    return AgentScreenSnapshot(
        currentApp = appPackage,
        packageName = appPackage,
        nodeCount = nodeCount,
        capturedNodeCount = capturedNodeCount.takeIf { it > 0 } ?: normalizedAllItems.size,
        texts = textItems
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(SNAPSHOT_TEXT_LIMIT),
        allNodes = normalizedAllItems.toAgentNodes(SNAPSHOT_ALL_NODE_LIMIT),
        clickableNodes = clickableItems.toAgentNodes(SNAPSHOT_CLICKABLE_LIMIT),
        inputNodes = inputItems.toAgentNodes(SNAPSHOT_INPUT_LIMIT),
        scrollableNodes = scrollableItems.toAgentNodes(SNAPSHOT_SCROLLABLE_LIMIT),
        visual = visual?.toAgentVisual(),
    )
}

private fun ScreenVisualObservation.toAgentVisual(): AgentScreenVisual {
    return AgentScreenVisual(
        available = available,
        mimeType = mimeType,
        width = width,
        height = height,
        displayWidth = displayWidth,
        displayHeight = displayHeight,
        base64Jpeg = base64Jpeg,
        source = source,
        reason = reason,
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

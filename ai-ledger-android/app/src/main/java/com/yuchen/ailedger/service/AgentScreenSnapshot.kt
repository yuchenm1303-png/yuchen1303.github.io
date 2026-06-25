package com.yuchen.ailedger.service

import com.yuchen.ailedger.AiLedgerApplication
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
    val observedPackage = packageName.ifBlank { "unknown" }
    val expectedPackage = ForegroundTargetBinding.current()
    val foregroundResolution = AiLedgerApplication.contextOrNull()?.let { context ->
        ForegroundPackageResolver.resolve(
            context = context,
            observedPackage = observedPackage,
            expectedPackage = expectedPackage,
        )
    } ?: ForegroundPackageResolutionPolicy.resolve(
        observedPackage = observedPackage,
        expectedPackage = expectedPackage,
        foregroundProcessPackages = emptySet(),
        shellForegroundPackages = emptySet(),
    )
    val appPackage = foregroundResolution.packageName.ifBlank { observedPackage }
    val packageWasSubstituted = appPackage != observedPackage

    // A fallback package may be verified by foreground process/dumpsys evidence while the selected
    // accessibility root still belongs to the overlay or a transient system surface. In that case
    // keep the screenshot, but never attach another window's semantic nodes to the verified target.
    val sourceAllItems = if (packageWasSubstituted) {
        emptyList()
    } else if (allItems.isNotEmpty()) {
        allItems
    } else {
        (clickableItems + inputItems + scrollableItems)
            .distinctBy { it.bounds + it.text + it.className }
    }
    val sourceTexts = if (packageWasSubstituted) emptyList() else textItems
    val sourceClickable = if (packageWasSubstituted) emptyList() else clickableItems
    val sourceInputs = if (packageWasSubstituted) emptyList() else inputItems
    val sourceScrollable = if (packageWasSubstituted) emptyList() else scrollableItems

    return AgentScreenSnapshot(
        currentApp = appPackage,
        packageName = appPackage,
        nodeCount = if (packageWasSubstituted) 0 else nodeCount,
        capturedNodeCount = if (packageWasSubstituted) 0 else capturedNodeCount.takeIf { it > 0 } ?: sourceAllItems.size,
        texts = sourceTexts
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(SNAPSHOT_TEXT_LIMIT)
            .toList(),
        allNodes = sourceAllItems.toAgentNodes(SNAPSHOT_ALL_NODE_LIMIT),
        clickableNodes = sourceClickable.toAgentNodes(SNAPSHOT_CLICKABLE_LIMIT),
        inputNodes = sourceInputs.toAgentNodes(SNAPSHOT_INPUT_LIMIT),
        scrollableNodes = sourceScrollable.toAgentNodes(SNAPSHOT_SCROLLABLE_LIMIT),
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
    return asSequence()
        .take(limit)
        .map {
            AgentScreenNode(
                id = it.id,
                text = it.text.trim().take(80),
                className = it.className.take(48),
                bounds = it.bounds,
                clickable = it.clickable,
                editable = it.editable,
                scrollable = it.scrollable,
            )
        }
        .toList()
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

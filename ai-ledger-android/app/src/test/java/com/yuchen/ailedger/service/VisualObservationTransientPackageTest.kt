package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualObservationTransientPackageTest {
    @Test
    fun transientBlankPackageKeepsVisualActionOnlyWithStableObjectiveStructure() {
        val nodes = listOf(
            node("n1", "消息", "[0,120][300,220]", clickable = true),
            node("n2", "联系人", "[300,120][600,220]", clickable = true),
        )
        val observed = snapshot("com.tencent.mobileqq", nodes)
        val current = snapshot("", nodes)

        val result = VisualObservationProtocol.evaluateActionContextFreshness(
            step = CloudAgentStep(type = "tap_xy", x = 0.2f, y = 0.1f),
            observedSnapshot = observed,
            currentSnapshot = current,
        )

        assertTrue(result.reason, result.fresh)
        assertEquals("visual_action_structure_verified_with_transient_package", result.reason)
    }

    @Test
    fun transientBlankPackageWithoutObjectiveEvidenceRequiresReobservation() {
        val observed = snapshot("com.tencent.mobileqq", emptyList())
        val current = snapshot("", emptyList())

        val result = VisualObservationProtocol.evaluateActionContextFreshness(
            step = CloudAgentStep(type = "tap_xy", x = 0.2f, y = 0.1f),
            observedSnapshot = observed,
            currentSnapshot = current,
        )

        assertFalse(result.fresh)
        assertEquals("foreground_package_unresolved", result.reason)
    }

    @Test
    fun explicitNodeTargetCanSurviveTransientBlankPackage() {
        val observed = snapshot(
            "com.tencent.mobileqq",
            listOf(node("old", "设置", "[40,120][260,220]", clickable = true)),
        )
        val current = snapshot(
            "",
            listOf(node("new", "设置", "[42,122][262,222]", clickable = true)),
        )

        val result = VisualObservationProtocol.evaluateActionContextFreshness(
            step = CloudAgentStep(type = "tap_node", targetText = "设置"),
            observedSnapshot = observed,
            currentSnapshot = current,
        )

        assertTrue(result.reason, result.fresh)
        assertEquals("node_target_verified_with_transient_package", result.reason)
    }

    @Test
    fun realNonBlankPackageChangeStillRejectsOldVisualAction() {
        val nodes = listOf(node("n1", "设置", "[40,120][260,220]", clickable = true))
        val observed = snapshot("com.tencent.mobileqq", nodes)
        val current = snapshot("com.huawei.android.launcher", nodes)

        val result = VisualObservationProtocol.evaluateActionContextFreshness(
            step = CloudAgentStep(type = "tap_xy", x = 0.2f, y = 0.1f),
            observedSnapshot = observed,
            currentSnapshot = current,
        )

        assertFalse(result.fresh)
        assertEquals("foreground_package_changed", result.reason)
    }

    private fun snapshot(
        packageName: String,
        nodes: List<AgentScreenNode>,
    ): AgentScreenSnapshot = AgentScreenSnapshot(
        currentApp = packageName,
        packageName = packageName,
        nodeCount = nodes.size,
        capturedNodeCount = nodes.size,
        texts = nodes.mapNotNull { it.text.takeIf(String::isNotBlank) },
        allNodes = nodes,
        clickableNodes = nodes.filter(AgentScreenNode::clickable),
        inputNodes = nodes.filter(AgentScreenNode::editable),
        scrollableNodes = nodes.filter(AgentScreenNode::scrollable),
    )

    private fun node(
        id: String,
        text: String,
        bounds: String,
        clickable: Boolean = false,
        editable: Boolean = false,
        scrollable: Boolean = false,
    ): AgentScreenNode = AgentScreenNode(
        id = id,
        text = text,
        className = when {
            editable -> "android.widget.EditText"
            scrollable -> "android.widget.ScrollView"
            else -> "android.widget.Button"
        },
        bounds = bounds,
        clickable = clickable,
        editable = editable,
        scrollable = scrollable,
    )
}

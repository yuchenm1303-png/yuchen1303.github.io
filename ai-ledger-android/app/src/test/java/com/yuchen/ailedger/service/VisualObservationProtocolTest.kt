package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualObservationProtocolTest {
    @Test
    fun dynamicTextAndNodeChangesCannotVetoVisualNavigation() {
        val observed = snapshot(
            nodes = listOf(
                node("n1", "余额 100.00", "[0,0][300,80]", clickable = true),
                node("n2", "转账", "[0,100][300,180]", clickable = true),
            ),
        )
        val current = snapshot(
            nodes = listOf(
                node("n9", "确认", "[80,700][220,780]", clickable = true),
                node("n10", "输入框", "[40,500][400,620]", editable = true),
            ),
        )

        val result = VisualObservationProtocol.evaluateActionContextFreshness(
            step = CloudAgentStep(type = "back"),
            observedSnapshot = observed,
            currentSnapshot = current,
        )

        assertTrue(result.fresh)
        assertEquals("visual_action_package_verified", result.reason)
    }

    @Test
    fun disappearingSemanticTargetIsRejectedOnlyForExplicitNodeExecution() {
        val observed = snapshot(
            nodes = listOf(
                node("n1", "提交", "[100,300][300,400]", clickable = true),
                node("n2", "取消", "[100,420][300,520]", clickable = true),
            ),
        )
        val current = snapshot(
            nodes = listOf(
                node("n1", "继续", "[100,300][300,400]", clickable = true),
                node("n2", "返回", "[100,420][300,520]", clickable = true),
            ),
        )

        val result = VisualObservationProtocol.evaluateActionContextFreshness(
            step = CloudAgentStep(type = "tap_node", targetText = "提交"),
            observedSnapshot = observed,
            currentSnapshot = current,
        )

        assertFalse(result.fresh)
        assertEquals("action_target_missing", result.reason)
    }

    @Test
    fun visualCoordinateIgnoresAccessibilityReplacementAtTheSamePoint() {
        val observed = snapshot(
            nodes = listOf(node("n1", "播放", "[0,0][200,200]", clickable = true)),
        )
        val current = snapshot(
            nodes = listOf(node("n9", "确认删除", "[0,0][200,200]", clickable = true)),
        )

        val result = VisualObservationProtocol.evaluateActionContextFreshness(
            step = CloudAgentStep(type = "tap_xy", x = 0.5f, y = 0.5f),
            observedSnapshot = observed,
            currentSnapshot = current,
        )

        assertTrue(result.fresh)
        assertEquals("visual_action_package_verified", result.reason)
    }

    @Test
    fun visualOnlyObservationIsNotVetoedByLaterFullScreenRootNode() {
        val observed = snapshot(nodes = emptyList())
        val current = snapshot(
            nodes = listOf(
                node(
                    id = "root",
                    text = "首页",
                    bounds = "[0,0][1224,2700]",
                    className = "android.view.View",
                ),
            ),
        )

        val result = VisualObservationProtocol.evaluateActionContextFreshness(
            step = CloudAgentStep(type = "tap_xy", x = 0.25f, y = 0.96f),
            observedSnapshot = observed,
            currentSnapshot = current,
        )

        assertTrue(result.fresh)
        assertEquals("visual_action_package_verified", result.reason)
    }

    @Test
    fun visualSwipeDoesNotUseInteractionSurfaceSimilarityAsVeto() {
        val observed = snapshot(
            nodes = listOf(
                node("n1", "首页", "[0,0][300,80]", clickable = true),
                node("n2", "行情", "[0,2500][300,2700]", clickable = true),
            ),
        )
        val current = snapshot(
            nodes = listOf(node("root", "详情", "[0,0][1224,2700]", className = "android.view.View")),
        )

        val result = VisualObservationProtocol.evaluateActionContextFreshness(
            step = CloudAgentStep(type = "swipe", direction = "up"),
            observedSnapshot = observed,
            currentSnapshot = current,
        )

        assertTrue(result.fresh)
        assertEquals("visual_action_package_verified", result.reason)
    }

    @Test
    fun focusedDirectInputDoesNotRequireAccessibilityTarget() {
        val observed = snapshot(nodes = emptyList())
        val current = snapshot(nodes = listOf(node("root", "页面", "[0,0][1224,2700]")))

        val result = VisualObservationProtocol.evaluateActionContextFreshness(
            step = CloudAgentStep(
                type = "input_text",
                text = "贵州茅台",
                inputMode = "focused_direct",
                requiresInputNode = false,
                useFocusedInput = true,
            ),
            observedSnapshot = observed,
            currentSnapshot = current,
        )

        assertTrue(result.fresh)
        assertEquals("visual_action_package_verified", result.reason)
    }

    @Test
    fun stableLabelAndBoundsKeepExplicitNodeActionFresh() {
        val observed = snapshot(
            nodes = listOf(
                node(
                    id = "n1",
                    text = "继续",
                    bounds = "[100,300][300,400]",
                    clickable = true,
                    className = "android.widget.Button",
                ),
            ),
        )
        val current = snapshot(
            nodes = listOf(
                node(
                    id = "n9",
                    text = "继续",
                    bounds = "[102,302][302,402]",
                    clickable = true,
                    className = "android.view.View",
                ),
            ),
        )

        val result = VisualObservationProtocol.evaluateActionContextFreshness(
            step = CloudAgentStep(type = "tap_node", targetText = "继续"),
            observedSnapshot = observed,
            currentSnapshot = current,
        )

        assertTrue(result.fresh)
        assertEquals("node_target_verified", result.reason)
    }

    @Test
    fun packageChangeIsAlwaysRejected() {
        val observed = snapshot(packageName = "com.example.target")
        val current = snapshot(packageName = "com.example.other")

        val result = VisualObservationProtocol.evaluateActionContextFreshness(
            step = CloudAgentStep(type = "tap_xy", x = 0.5f, y = 0.5f),
            observedSnapshot = observed,
            currentSnapshot = current,
        )

        assertFalse(result.fresh)
        assertEquals("foreground_package_changed", result.reason)
    }

    @Test
    fun compatibilityFreshnessChecksPackageOnly() {
        val observed = snapshot(nodes = listOf(node("a", "首页", "[0,0][100,100]", clickable = true)))
        val current = snapshot(nodes = listOf(node("b", "完全不同", "[500,900][900,1200]", clickable = true)))

        assertTrue(VisualObservationProtocol.isActionContextFresh(observed, current))
    }

    private fun snapshot(
        packageName: String = "com.example.target",
        nodes: List<AgentScreenNode> = emptyList(),
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
        className: String? = null,
    ): AgentScreenNode = AgentScreenNode(
        id = id,
        text = text,
        className = className ?: when {
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

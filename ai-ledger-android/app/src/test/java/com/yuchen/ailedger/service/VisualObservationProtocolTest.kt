package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualObservationProtocolTest {
    @Test
    fun dynamicTextDoesNotInvalidateStableInteractionSurface() {
        val observed = snapshot(
            nodes = listOf(
                node("n1", "余额 100.00", "[0,0][300,80]", clickable = true),
                node("n2", "转账", "[0,100][300,180]", clickable = true),
                node("n3", "明细", "[0,200][300,280]", clickable = true),
                node("n4", "账户", "[0,300][300,380]", clickable = true),
            ),
        )
        val current = snapshot(
            nodes = listOf(
                node("n1", "余额 102.35", "[2,1][302,81]", clickable = true),
                node("n2", "转账", "[1,101][301,181]", clickable = true),
                node("n3", "明细", "[0,201][300,281]", clickable = true),
                node("n4", "账户", "[1,301][301,381]", clickable = true),
            ),
        )

        val result = VisualObservationProtocol.evaluateActionContextFreshness(
            step = CloudAgentStep(type = "back"),
            observedSnapshot = observed,
            currentSnapshot = current,
        )

        assertTrue(result.fresh)
        assertTrue(result.surfaceSimilarity >= 0.58f)
    }

    @Test
    fun samePackageWithDifferentInteractionSurfaceIsRejected() {
        val observed = snapshot(
            nodes = listOf(
                node("n1", "首页", "[0,0][300,80]", clickable = true),
                node("n2", "搜索", "[0,100][300,180]", clickable = true),
                node("n3", "消息", "[0,200][300,280]", clickable = true),
                node("n4", "我的", "[0,300][300,380]", clickable = true),
            ),
        )
        val current = snapshot(
            nodes = listOf(
                node("n1", "确认", "[80,700][220,780]", clickable = true),
                node("n2", "取消", "[240,700][380,780]", clickable = true),
                node("n3", "输入框", "[40,500][400,620]", editable = true),
                node("n4", "列表", "[0,900][480,1500]", scrollable = true),
            ),
        )

        val result = VisualObservationProtocol.evaluateActionContextFreshness(
            step = CloudAgentStep(type = "back"),
            observedSnapshot = observed,
            currentSnapshot = current,
        )

        assertFalse(result.fresh)
        assertEquals("interaction_surface_changed", result.reason)
    }

    @Test
    fun disappearingSemanticTargetIsRejectedBeforeExecution() {
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
    fun coordinateTargetChangingUnderTheSamePointIsRejected() {
        val observed = snapshot(
            nodes = listOf(node("n1", "播放", "[0,0][200,200]", clickable = true)),
        )
        val current = snapshot(
            nodes = listOf(node("n9", "确认删除", "[20,20][100,100]", clickable = true)),
        )

        val result = VisualObservationProtocol.evaluateActionContextFreshness(
            step = CloudAgentStep(type = "tap_xy", x = 50f, y = 50f),
            observedSnapshot = observed,
            currentSnapshot = current,
        )

        assertFalse(result.fresh)
        assertEquals("coordinate_target_changed", result.reason)
    }

    @Test
    fun packageChangeIsAlwaysRejected() {
        val observed = snapshot(packageName = "com.example.target")
        val current = snapshot(packageName = "com.example.other")

        val result = VisualObservationProtocol.evaluateActionContextFreshness(
            step = CloudAgentStep(type = "swipe", direction = "up"),
            observedSnapshot = observed,
            currentSnapshot = current,
        )

        assertFalse(result.fresh)
        assertEquals("foreground_package_changed", result.reason)
    }

    @Test
    fun visualOnlySurfaceKeepsPackageVerifiedFallbackWithoutExtraScreenshot() {
        val observed = snapshot()
        val current = snapshot()

        val result = VisualObservationProtocol.evaluateActionContextFreshness(
            step = CloudAgentStep(type = "swipe", direction = "up"),
            observedSnapshot = observed,
            currentSnapshot = current,
        )

        assertTrue(result.fresh)
        assertEquals("visual_only_package_verified", result.reason)
    }

    private fun snapshot(
        packageName: String = "com.example.target",
        nodes: List<AgentScreenNode> = emptyList(),
    ): AgentScreenSnapshot {
        return AgentScreenSnapshot(
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
    }

    private fun node(
        id: String,
        text: String,
        bounds: String,
        clickable: Boolean = false,
        editable: Boolean = false,
        scrollable: Boolean = false,
    ): AgentScreenNode {
        return AgentScreenNode(
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
}

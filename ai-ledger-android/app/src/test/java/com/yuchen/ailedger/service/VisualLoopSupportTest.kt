package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualLoopSupportTest {
    @Test
    fun uniqueDeclaredButtonGroundsBottomEdgeMissIntoTarget() {
        val snapshot = snapshot(
            nodes = listOf(
                node("立即支付", "40,2200,1040,2520"),
            ),
        )
        val step = CloudAgentStep(
            type = "tap_xy",
            targetText = "点击屏幕底部附近的橙色“立即支付”按钮",
            reason = "完成当前商品选择",
            x = 0.915f,
            y = 0.998f,
        )

        val grounded = VisualLoopSupport.materializeTap(step, snapshot)

        assertTrue(grounded.x!! in 40f..1040f)
        assertTrue(grounded.y!! in 2200f..2520f)
        assertTrue(grounded.y!! < 2520f)
        assertTrue(grounded.reason.orEmpty().contains("校准到可点击区域"))
        assertTrue(grounded.toolArgs!!.optBoolean("__androidGroundingApplied"))
        assertEquals(0.915, grounded.toolArgs!!.optDouble("__androidModelX"), 0.0001)
        assertEquals(0.998, grounded.toolArgs!!.optDouble("__androidModelY"), 0.0001)
        assertEquals("hybrid", grounded.toolArgs!!.optString("__androidVisualSurfaceMode"))
    }

    @Test
    fun coordinateAlreadyInsideDeclaredTargetIsNotMoved() {
        val snapshot = snapshot(
            nodes = listOf(node("立即支付", "40,2200,1040,2520")),
        )
        val step = CloudAgentStep(
            type = "tap_xy",
            targetText = "立即支付",
            x = 0.5f,
            y = 0.9f,
        )

        val grounded = VisualLoopSupport.materializeTap(step, snapshot)

        assertEquals(540f, grounded.x!!, 0.01f)
        assertEquals(2322f, grounded.y!!, 0.01f)
        assertEquals(step.reason, grounded.reason)
        assertFalse(grounded.toolArgs!!.optBoolean("__androidGroundingApplied"))
    }

    @Test
    fun visualOnlyPageKeepsOriginalMaterializedCoordinate() {
        val snapshot = snapshot(nodes = emptyList())
        val step = CloudAgentStep(
            type = "tap_xy",
            targetText = "立即支付",
            x = 0.915f,
            y = 0.998f,
        )

        val grounded = VisualLoopSupport.materializeTap(step, snapshot)

        assertEquals(988.2f, grounded.x!!, 0.01f)
        assertEquals(2574.84f, grounded.y!!, 0.02f)
        assertEquals("visual_only", grounded.toolArgs!!.optString("__androidVisualSurfaceMode"))
        assertFalse(grounded.toolArgs!!.optBoolean("__androidGroundingApplied"))
    }

    @Test
    fun repeatedLabelFarFromBothTargetsIsNotBlindlySnapped() {
        val snapshot = snapshot(
            nodes = listOf(
                node("确定", "40,100,240,200"),
                node("确定", "840,100,1040,200"),
            ),
        )
        val step = CloudAgentStep(
            type = "tap_xy",
            targetText = "确定",
            x = 0.5f,
            y = 0.9f,
        )

        val grounded = VisualLoopSupport.materializeTap(step, snapshot)

        assertEquals(540f, grounded.x!!, 0.01f)
        assertEquals(2322f, grounded.y!!, 0.01f)
        assertFalse(grounded.toolArgs!!.optBoolean("__androidGroundingApplied"))
    }

    @Test
    fun resultSummaryCarriesModelMaterializedAndExecutedCoordinates() {
        val snapshot = snapshot(nodes = listOf(node("立即支付", "40,2200,1040,2520")))
        val materialized = VisualLoopSupport.materializeTap(
            CloudAgentStep(
                type = "tap_xy",
                targetText = "立即支付",
                x = 0.915f,
                y = 0.998f,
            ),
            snapshot,
        )

        val summary = VisualLoopSupport.resultSummary(
            step = materialized,
            signature = "tap@988,2494",
            result = AgentExecutionResult(
                ok = true,
                message = "视觉坐标 988,2494 · 实际落点 986,2492（边界保护）",
            ),
        )

        assertTrue(summary.contains("surface=hybrid"))
        assertTrue(summary.contains("modelNorm=0.915,0.998"))
        assertTrue(summary.contains("modelPx=988.200,2574.840"))
        assertTrue(summary.contains("executedPx=986.000,2492.000"))
        assertTrue(summary.contains("groundingApplied=true"))
        assertTrue(summary.contains("boundaryAdjusted=true"))
    }

    private fun snapshot(nodes: List<AgentScreenNode>): AgentScreenSnapshot {
        return AgentScreenSnapshot(
            currentApp = "com.example.shop",
            packageName = "com.example.shop",
            nodeCount = nodes.size,
            capturedNodeCount = nodes.size,
            texts = nodes.map(AgentScreenNode::text),
            allNodes = nodes,
            clickableNodes = nodes,
            inputNodes = emptyList(),
            scrollableNodes = emptyList(),
            visual = AgentScreenVisual(
                available = true,
                mimeType = "image/jpeg",
                width = 720,
                height = 1720,
                displayWidth = 1080,
                displayHeight = 2580,
                base64Jpeg = "YWJj",
                source = "test",
                reason = "test",
            ),
        )
    }

    private fun node(text: String, bounds: String): AgentScreenNode {
        return AgentScreenNode(
            id = text,
            text = text,
            className = "android.widget.Button",
            bounds = bounds,
            clickable = true,
            editable = false,
            scrollable = false,
        )
    }
}

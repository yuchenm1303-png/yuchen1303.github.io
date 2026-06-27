package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualLoopSupportTest {
    @Test
    fun declaredTargetTextNeverMovesGuiPlusCoordinate() {
        val snapshot = snapshot(
            nodes = listOf(node("立即支付", "40,2200,1040,2520")),
        )
        val step = permittedTap(
            targetText = "点击屏幕底部附近的橙色“立即支付”按钮",
            reason = "完成当前商品选择",
            x = 0.915f,
            y = 0.998f,
        )

        val materialized = VisualLoopSupport.materializeTap(step, snapshot)

        assertEquals("tap_xy", materialized.type)
        assertEquals(988.2f, materialized.x!!, 0.01f)
        assertEquals(2574.84f, materialized.y!!, 0.02f)
        assertEquals(step.reason, materialized.reason)
        assertFalse(materialized.toolArgs!!.optBoolean("__androidGroundingApplied"))
        assertEquals(0.915, materialized.toolArgs!!.optDouble("__androidModelX"), 0.0001)
        assertEquals(0.998, materialized.toolArgs!!.optDouble("__androidModelY"), 0.0001)
        assertEquals("hybrid", materialized.toolArgs!!.optString("__androidVisualSurfaceMode"))
    }

    @Test
    fun missingExecutionPermitConvertsCoordinateIntoReobserveWait() {
        val step = CloudAgentStep(type = "tap_xy", x = 0.5f, y = 0.5f)

        val guarded = VisualLoopSupport.materializeTap(step, snapshot(emptyList()))

        assertEquals("wait", guarded.type)
        assertNull(guarded.x)
        assertNull(guarded.y)
        assertEquals(220L, guarded.durationMs)
        assertTrue(guarded.toolArgs!!.optBoolean("__androidExecutionPermitRejected"))
        assertEquals("tap_xy", guarded.toolArgs!!.optString("__androidRejectedAction"))
    }

    @Test
    fun unknownExecutionPermitKindIsRejectedWithoutClicking() {
        val step = CloudAgentStep(
            type = "tap_xy",
            x = 0.5f,
            y = 0.5f,
            toolArgs = permitArgs(kind = "untrusted_local_grounding"),
        )

        val guarded = VisualLoopSupport.materializeTap(step, snapshot(emptyList()))

        assertEquals("wait", guarded.type)
        assertNull(guarded.x)
        assertNull(guarded.y)
    }

    @Test
    fun repeatedNodeLabelsCannotInfluencePermittedCoordinate() {
        val snapshot = snapshot(
            nodes = listOf(
                node("确定", "40,100,240,200"),
                node("确定", "840,100,1040,200"),
            ),
        )
        val step = permittedTap(
            targetText = "确定",
            x = 0.5f,
            y = 0.9f,
        )

        val materialized = VisualLoopSupport.materializeTap(step, snapshot)

        assertEquals(540f, materialized.x!!, 0.01f)
        assertEquals(2322f, materialized.y!!, 0.01f)
        assertFalse(materialized.toolArgs!!.optBoolean("__androidGroundingApplied"))
    }

    @Test
    fun visualOnlyPageKeepsVerifiedCoordinate() {
        val materialized = VisualLoopSupport.materializeTap(
            permittedTap(
                targetText = "立即支付",
                x = 0.915f,
                y = 0.998f,
                permitKind = "independent_gui_visual_grounding",
            ),
            snapshot(nodes = emptyList()),
        )

        assertEquals(988.2f, materialized.x!!, 0.01f)
        assertEquals(2574.84f, materialized.y!!, 0.02f)
        assertEquals("visual_only", materialized.toolArgs!!.optString("__androidVisualSurfaceMode"))
        assertFalse(materialized.toolArgs!!.optBoolean("__androidGroundingApplied"))
    }

    @Test
    fun cloudExecutionPermitSurvivesCoordinateMaterialization() {
        val step = permittedTap(
            x = 0.5f,
            y = 0.5f,
            permitKind = "independent_gui_visual_grounding",
        )

        val materialized = VisualLoopSupport.materializeTap(step, snapshot(emptyList()))

        assertEquals("permit-123", materialized.toolArgs!!.optString("executionPermitId"))
        assertEquals(
            "independent_gui_visual_grounding",
            materialized.toolArgs!!.optString("executionPermitKind"),
        )
        assertEquals("observation-456", materialized.toolArgs!!.optString("executionPermitObservationId"))
    }

    @Test
    fun resultSummaryCarriesUnmodifiedAndExecutedCoordinates() {
        val snapshot = snapshot(nodes = listOf(node("立即支付", "40,2200,1040,2520")))
        val materialized = VisualLoopSupport.materializeTap(
            permittedTap(
                targetText = "立即支付",
                x = 0.915f,
                y = 0.998f,
            ),
            snapshot,
        )

        val summary = VisualLoopSupport.resultSummary(
            step = materialized,
            signature = "tap@988,2574",
            result = AgentExecutionResult(
                ok = true,
                message = "视觉坐标 988,2574 · 实际落点 986,2492（边界保护）",
            ),
        )

        assertTrue(summary.contains("surface=hybrid"))
        assertTrue(summary.contains("modelNorm=0.915,0.998"))
        assertTrue(summary.contains("modelPx=988.200,2574.840"))
        assertTrue(summary.contains("materializedPx=988.200,2574.840"))
        assertTrue(summary.contains("executedPx=986.000,2492.000"))
        assertTrue(summary.contains("groundingApplied=false"))
        assertTrue(summary.contains("boundaryAdjusted=true"))
    }

    private fun permittedTap(
        targetText: String? = null,
        reason: String? = null,
        x: Float,
        y: Float,
        permitKind: String = "android_structural_clickable_anchor",
    ): CloudAgentStep = CloudAgentStep(
        type = "tap_xy",
        targetText = targetText,
        reason = reason,
        x = x,
        y = y,
        toolArgs = permitArgs(permitKind),
    )

    private fun permitArgs(kind: String): JSONObject = JSONObject().apply {
        put("executionPermitId", "permit-123")
        put("executionPermitKind", kind)
        put("executionPermitObservationId", "observation-456")
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

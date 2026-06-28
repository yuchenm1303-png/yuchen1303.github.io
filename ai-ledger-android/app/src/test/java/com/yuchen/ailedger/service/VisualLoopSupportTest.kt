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
        val snapshot = snapshot(listOf(node("立即支付", "40,2200,1040,2520")))
        val step = permittedTap(
            targetText = "点击屏幕底部附近的橙色“立即支付”按钮",
            reason = "完成当前商品选择",
            x = 0.915f,
            y = 0.998f,
        )

        val materialized = VisualLoopSupport.materializeTap(step, snapshot)

        assertEquals("tap_xy", materialized.type)
        assertEquals(987.285f, materialized.x!!, 0.01f)
        assertEquals(2573.842f, materialized.y!!, 0.02f)
        assertEquals(step.reason, materialized.reason)
        assertFalse(materialized.toolArgs!!.optBoolean("__androidGroundingApplied"))
        assertEquals(0.915, materialized.toolArgs!!.optDouble("__androidModelX"), 0.0001)
        assertEquals(0.998, materialized.toolArgs!!.optDouble("__androidModelY"), 0.0001)
        assertEquals("hybrid", materialized.toolArgs!!.optString("__androidVisualSurfaceMode"))
        assertEquals(
            VisualCoordinateProtocol.pixelMappingProtocol,
            materialized.toolArgs!!.optString("__androidPixelMappingProtocol"),
        )
    }

    @Test
    fun normalizedBottomRightMapsToLastPhysicalPixel() {
        val materialized = VisualLoopSupport.materializeTap(
            permittedTap(x = 1f, y = 1f),
            snapshot(emptyList()),
        )

        assertEquals(1079f, materialized.x!!, 0.001f)
        assertEquals(2579f, materialized.y!!, 0.001f)
        assertEquals(1080, materialized.toolArgs!!.optInt("__androidDisplayWidth"))
        assertEquals(2580, materialized.toolArgs!!.optInt("__androidDisplayHeight"))
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
        assertEquals("missing_permit_args", guarded.toolArgs!!.optString("__androidExecutionPermitRejectReason"))
    }

    @Test
    fun observationOrSessionMismatchNeverClicks() {
        val step = permittedTap(x = 0.5f, y = 0.5f)
        step.toolArgs!!.put("responseObservationId", "stale-observation")

        val guarded = VisualLoopSupport.materializeTap(step, snapshot(emptyList()))

        assertEquals("wait", guarded.type)
        assertNull(guarded.x)
        assertNull(guarded.y)
        assertEquals(
            "permit_observation_mismatch",
            guarded.toolArgs!!.optString("__androidExecutionPermitRejectReason"),
        )
    }

    @Test
    fun repeatedNodeLabelsCannotInfluencePermittedCoordinate() {
        val materialized = VisualLoopSupport.materializeTap(
            permittedTap(targetText = "确定", x = 0.5f, y = 0.9f),
            snapshot(
                listOf(
                    node("确定", "40,100,240,200"),
                    node("确定", "840,100,1040,200"),
                ),
            ),
        )

        assertEquals(539.5f, materialized.x!!, 0.01f)
        assertEquals(2321.1f, materialized.y!!, 0.01f)
        assertFalse(materialized.toolArgs!!.optBoolean("__androidGroundingApplied"))
    }

    @Test
    fun visualOnlyPageKeepsIndependentlyVerifiedCoordinate() {
        val materialized = VisualLoopSupport.materializeTap(
            permittedTap(
                targetText = "立即支付",
                x = 0.915f,
                y = 0.998f,
                permitKind = "independent_gui_visual_grounding",
            ),
            snapshot(emptyList()),
        )

        assertEquals(987.285f, materialized.x!!, 0.01f)
        assertEquals(2573.842f, materialized.y!!, 0.02f)
        assertEquals("visual_only", materialized.toolArgs!!.optString("__androidVisualSurfaceMode"))
    }

    @Test
    fun permitAndCoordinateTraceSurviveMaterialization() {
        val materialized = VisualLoopSupport.materializeTap(
            permittedTap(x = 0.915f, y = 0.998f),
            snapshot(listOf(node("按钮", "40,2200,1040,2520"))),
        )
        val summary = VisualLoopSupport.resultSummary(
            materialized,
            "tap@987,2574",
            AgentExecutionResult(true, "视觉坐标 987.285,2573.842 · 实际落点 987.285,2573.842"),
        )

        assertTrue(materialized.toolArgs!!.optString("executionPermitId").startsWith("permit_"))
        assertTrue(summary.contains("permit=android_structural_clickable_anchor"))
        assertTrue(summary.contains("mapping=full_display_last_pixel_v2"))
        assertTrue(summary.contains("sourceFrame=1080x2580"))
        assertTrue(summary.contains("modelNorm=0.915,0.998"))
        assertTrue(summary.contains("modelPx=987.285,2573.842"))
        assertTrue(summary.contains("executedPx=987.285,2573.842"))
        assertTrue(summary.contains("boundaryAdjusted=false"))
    }

    private fun permittedTap(
        targetText: String? = null,
        reason: String? = null,
        x: Float,
        y: Float,
        permitKind: String = "android_structural_clickable_anchor",
    ): CloudAgentStep {
        val sessionId = "visual-session-123"
        val observationId = "observation-456"
        val hash = VisualExecutionPermitPolicy.tapPermitHash(
            sessionId,
            observationId,
            x,
            y,
            permitKind,
        )
        return CloudAgentStep(
            type = "tap_xy",
            targetText = targetText,
            reason = reason,
            x = x,
            y = y,
            toolArgs = JSONObject().apply {
                put("responseObservationId", observationId)
                put("responseSessionId", sessionId)
                put("executionPermitId", "permit_$hash")
                put("executionPermitKind", permitKind)
                put("executionPermitObservationId", observationId)
                put("executionPermitSessionId", sessionId)
                put("executionPermitActionType", "tap_xy")
                put("executionPermitX", x)
                put("executionPermitY", y)
                put("executionPermitActionHash", hash)
            },
        )
    }

    private fun snapshot(nodes: List<AgentScreenNode>): AgentScreenSnapshot = AgentScreenSnapshot(
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

    private fun node(text: String, bounds: String): AgentScreenNode = AgentScreenNode(
        id = text,
        text = text,
        className = "android.widget.Button",
        bounds = bounds,
        clickable = true,
        editable = false,
        scrollable = false,
    )
}

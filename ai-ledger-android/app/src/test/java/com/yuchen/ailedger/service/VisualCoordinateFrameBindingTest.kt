package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualCoordinateFrameBindingTest {
    @Test
    fun missingVisualFrameNeverUsesCurrentDisplayToGuessCoordinate() {
        val guarded = VisualLoopSupport.materializeTap(
            permittedTap(0.5f, 0.5f),
            snapshot(visual = null),
        )

        assertEquals("wait", guarded.type)
        assertNull(guarded.x)
        assertNull(guarded.y)
        assertEquals(220L, guarded.durationMs)
        assertTrue(guarded.toolArgs!!.optBoolean("__androidCoordinateMaterializationRejected"))
        assertEquals(
            "missing_visual_frame",
            guarded.toolArgs!!.optString("__androidCoordinateMaterializationRejectReason"),
        )
        assertEquals(
            "rejected:missing_visual_frame",
            guarded.toolArgs!!.optString("__androidPixelMappingProtocol"),
        )
    }

    @Test
    fun encodedImageDimensionsCannotReplaceMissingPhysicalDisplayFrame() {
        val guarded = VisualLoopSupport.materializeTap(
            permittedTap(0.5f, 0.5f),
            snapshot(
                visual = AgentScreenVisual(
                    available = true,
                    mimeType = "image/jpeg",
                    width = 720,
                    height = 1720,
                    displayWidth = 0,
                    displayHeight = 0,
                    base64Jpeg = "YWJj",
                    source = "test",
                    reason = "test",
                ),
            ),
        )

        assertEquals("wait", guarded.type)
        assertNull(guarded.x)
        assertNull(guarded.y)
        assertTrue(guarded.toolArgs!!.optBoolean("__androidCoordinateMaterializationRejected"))
        assertEquals(
            "missing_source_display_frame",
            guarded.toolArgs!!.optString("__androidCoordinateMaterializationRejectReason"),
        )
        assertEquals(720, guarded.toolArgs!!.optInt("__androidImageWidth"))
        assertEquals(1720, guarded.toolArgs!!.optInt("__androidImageHeight"))
        assertFalse(guarded.toolArgs!!.has("__androidDisplayWidth"))
        assertFalse(guarded.toolArgs!!.has("__androidDisplayHeight"))
    }

    private fun permittedTap(x: Float, y: Float): CloudAgentStep {
        val sessionId = "visual-session-frame-test"
        val observationId = "observation-frame-test"
        val kind = "independent_gui_visual_grounding"
        val hash = VisualExecutionPermitPolicy.tapPermitHash(
            sessionId = sessionId,
            observationId = observationId,
            x = x,
            y = y,
            kind = kind,
        )
        return CloudAgentStep(
            type = "tap_xy",
            x = x,
            y = y,
            toolArgs = JSONObject().apply {
                put("responseObservationId", observationId)
                put("responseSessionId", sessionId)
                put("executionPermitId", "permit_$hash")
                put("executionPermitKind", kind)
                put("executionPermitObservationId", observationId)
                put("executionPermitSessionId", sessionId)
                put("executionPermitActionType", "tap_xy")
                put("executionPermitX", x)
                put("executionPermitY", y)
                put("executionPermitActionHash", hash)
            },
        )
    }

    private fun snapshot(visual: AgentScreenVisual?): AgentScreenSnapshot = AgentScreenSnapshot(
        currentApp = "com.example.target",
        packageName = "com.example.target",
        nodeCount = 0,
        capturedNodeCount = 0,
        texts = emptyList(),
        allNodes = emptyList(),
        clickableNodes = emptyList(),
        inputNodes = emptyList(),
        scrollableNodes = emptyList(),
        visual = visual,
    )
}

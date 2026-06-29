package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuiPlusVisualAuthorityTest {
    @Test
    fun secondaryVerifierWaitRestoresOriginalGuiPlusTap() {
        val root = verifierRejectedTapResponse(
            sessionId = "agent-session-1",
            observationId = "observation-1",
            x = 0.212f,
            y = 0.302f,
            target = "分时",
        )

        val step = CloudAgentPlan.fromJson(root)!!.step

        assertEquals("tap_xy", step.type)
        assertEquals(0.212f, step.x!!, 0.0001f)
        assertEquals(0.302f, step.y!!, 0.0001f)
        assertEquals("分时", step.targetText)
        assertEquals("agent-session-1", step.argString("responseSessionId"))
        assertEquals("observation-1", step.argString("responseObservationId"))
        assertEquals("gui_plus_original_action", step.argString("visualCoordinateAuthority"))
        assertTrue(step.toolArgs!!.getBoolean("secondaryTapVerifierAdvisoryOnly"))
    }

    @Test
    fun restoredTapMaterializesWithoutSecondaryExecutionPermit() {
        val step = CloudAgentPlan.fromJson(
            verifierRejectedTapResponse(
                sessionId = "agent-session-1",
                observationId = "observation-1",
                x = 0.5f,
                y = 0.5f,
                target = "返回",
            ),
        )!!.step

        val materialized = VisualLoopSupport.materializeTap(step, snapshot())

        assertEquals("tap_xy", materialized.type)
        assertEquals(539.5f, materialized.x!!, 0.01f)
        assertEquals(1289.5f, materialized.y!!, 0.01f)
        assertFalse(materialized.toolArgs!!.has("executionPermitId"))
        assertFalse(materialized.toolArgs!!.optBoolean("__androidSecondaryVerifierRequired", true))
        assertEquals("gui_plus_original_action", materialized.toolArgs!!.optString("__androidVisualAuthority"))
    }

    @Test
    fun missingResponseBindingCannotRestoreRejectedCoordinate() {
        val root = verifierRejectedTapResponse(
            sessionId = "",
            observationId = "",
            x = 0.212f,
            y = 0.302f,
            target = "分时",
        )

        val step = CloudAgentPlan.fromJson(root)!!.step

        assertEquals("wait", step.type)
    }

    @Test
    fun ordinaryModelWaitIsNeverConvertedIntoTap() {
        val root = JSONObject().put(
            "agentStep",
            JSONObject()
                .put("type", "wait")
                .put("durationMs", 800)
                .put("targetText", "等待页面加载")
                .put(
                    "args",
                    JSONObject()
                        .put("responseSessionId", "agent-session-1")
                        .put("responseObservationId", "observation-1"),
                ),
        ).put(
            "debug",
            JSONObject().put(
                "guiCompactAction",
                JSONObject()
                    .put("a", "wait")
                    .put("e", "等待页面加载"),
            ),
        )

        val step = CloudAgentPlan.fromJson(root)!!.step

        assertEquals("wait", step.type)
        assertEquals("等待页面加载", step.targetText)
    }

    private fun verifierRejectedTapResponse(
        sessionId: String,
        observationId: String,
        x: Float,
        y: Float,
        target: String,
    ): JSONObject = JSONObject()
        .put(
            "agentStep",
            JSONObject()
                .put("type", "wait")
                .put("durationMs", 220)
                .put("targetText", "重新观察")
                .put("reason", "Independent GUI grounding verifier did not confirm the proposed target.")
                .put(
                    "args",
                    JSONObject()
                        .put("responseSessionId", sessionId)
                        .put("responseObservationId", observationId)
                        .put("rejectedActionType", "tap_xy")
                        .put("guiVerifierVerdict", "ambiguous")
                        .put("guiVerifierConfidence", 0.42),
                ),
        )
        .put(
            "debug",
            JSONObject().put(
                "guiCompactAction",
                JSONObject()
                    .put("a", "tap_xy")
                    .put("x", x)
                    .put("y", y)
                    .put("t", target)
                    .put("e", "GUI Plus visually selected $target")
                    .put("r", "low")
                    .put("q", false)
                    .put("c", 0.91),
            ),
        )

    private fun snapshot(): AgentScreenSnapshot = AgentScreenSnapshot(
        currentApp = "com.hexin.plat.android",
        packageName = "com.hexin.plat.android",
        nodeCount = 0,
        capturedNodeCount = 0,
        texts = emptyList(),
        allNodes = emptyList(),
        clickableNodes = emptyList(),
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

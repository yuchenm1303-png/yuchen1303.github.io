package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTakeoverDialogueBridgeTest {
    @Test
    fun takeoverGuidanceBecomesCanonicalUserDialogueAndForcesGuiReplan() {
        val actions = AgentTakeoverDialogueBridge.encodeInteractionActions(
            listOf(
                "我已经手动返回到商品页",
                "不要继续购买，改为打开购物车检查数量",
            ),
        )

        assertEquals(3, actions.size)
        assertEquals("userInstruction:我已经手动返回到商品页", actions[0])
        assertTrue(actions[1].startsWith("userInstruction:[LATEST_USER_DIRECTIVE]"))
        assertTrue(actions[1].contains("不要继续购买，改为打开购物车检查数量"))
        assertTrue(actions[2].startsWith("visual_replan_requested:reason=user_instruction"))
        assertTrue(actions[2].contains("replanRequired=true"))

        val snapshot = AgentScreenSnapshot(
            currentApp = "com.example.shop",
            packageName = "com.example.shop",
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
                height = 1280,
                displayWidth = 1080,
                displayHeight = 2400,
                base64Jpeg = "YWJj",
                source = "test",
                reason = "test",
            ),
        )
        val runtime = VisualAgentRuntimeContext(
            surfaceState = VisualSurfaceState.WorkSurface,
            selectedTargetPackage = snapshot.packageName,
            verifiedTargetPackage = snapshot.packageName,
            currentPackage = snapshot.packageName,
            observationId = VisualObservationProtocol.observationId(snapshot, 1L, 2L),
            routeEpoch = 1L,
            surfaceEpoch = 2L,
            guiPlusEligible = true,
        )

        val payload = buildVisualAgentPayload(
            goal = "检查购物车",
            snapshot = snapshot,
            recentActions = actions,
            runtimeContext = runtime,
        )
        val history = payload.getJSONArray("interactionHistory")

        assertEquals(2, history.length())
        assertEquals("user", history.getJSONObject(0).getString("role"))
        assertEquals("我已经手动返回到商品页", history.getJSONObject(0).getString("content"))
        assertEquals("user", history.getJSONObject(1).getString("role"))
        assertTrue(history.getJSONObject(1).getString("content").contains("LATEST_USER_DIRECTIVE"))
        assertTrue(payload.getBoolean("guiPlusReplanRequested"))
        assertTrue(payload.getBoolean("visualReplanRequested"))
        assertFalse(payload.getBoolean("routeRefreshRequested"))
        assertEquals("gui_plus", payload.getString("visualDecisionOwner"))
    }

    @Test
    fun emptyGuidanceProducesNoSyntheticSignals() {
        assertTrue(AgentTakeoverDialogueBridge.encodeInteractionActions(emptyList()).isEmpty())
        assertTrue(AgentTakeoverDialogueBridge.encodeInteractionActions(listOf("   ")).isEmpty())
    }
}

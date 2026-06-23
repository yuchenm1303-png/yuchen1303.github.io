package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualExecutionSessionStateTest {
    @Test
    fun externalForegroundAppIsNotTrustedUntilCloudSelectsExactPackage() {
        val session = VisualExecutionSessionState()
        val snapshot = testSnapshot("com.jingdong.app.mall")

        val initial = session.runtimeContext(snapshot)

        assertEquals(VisualSurfaceState.Planning, initial.surfaceState)
        assertFalse(initial.guiPlusEligible)
        assertEquals("", initial.verifiedTargetPackage)
    }

    @Test
    fun cloudOpenAppSelectionBindsVerifiedWorkSurface() {
        val session = VisualExecutionSessionState()
        val snapshot = testSnapshot("com.jingdong.app.mall")

        session.beginLaunch("com.jingdong.app.mall")
        assertEquals(VisualSurfaceState.Launching, session.surfaceState)
        session.markTargetVerified("com.jingdong.app.mall")

        val runtime = session.runtimeContext(snapshot)
        assertEquals(VisualSurfaceState.WorkSurface, runtime.surfaceState)
        assertEquals("com.jingdong.app.mall", runtime.selectedTargetPackage)
        assertEquals("com.jingdong.app.mall", runtime.verifiedTargetPackage)
        assertTrue(runtime.guiPlusEligible)
    }

    @Test
    fun packageDriftKeepsGuiPlusOwnershipAfterVerifiedHandoff() {
        val session = VisualExecutionSessionState()
        session.beginLaunch("com.jingdong.app.mall")
        session.markTargetVerified("com.jingdong.app.mall")

        val transientSurface = session.runtimeContext(testSnapshot("com.android.permissioncontroller"))

        assertEquals(VisualSurfaceState.WorkSurface, transientSurface.surfaceState)
        assertTrue(transientSurface.guiPlusEligible)
        assertEquals("com.jingdong.app.mall", transientSurface.selectedTargetPackage)
        assertEquals("com.jingdong.app.mall", transientSurface.verifiedTargetPackage)
        assertEquals("com.android.permissioncontroller", transientSurface.currentPackage)
    }

    @Test
    fun structuralReplanCannotStealOwnershipAfterGuiPlusHandoff() {
        val session = VisualExecutionSessionState()
        session.beginLaunch("com.jingdong.app.mall")
        session.markTargetVerified("com.jingdong.app.mall")
        session.markStructuralReplan()
        val routeEpoch = session.routeEpoch

        val stillVisual = session.runtimeContext(testSnapshot("com.android.permissioncontroller"))
        assertEquals(VisualSurfaceState.WorkSurface, stillVisual.surfaceState)
        assertTrue(stillVisual.guiPlusEligible)
        assertEquals(routeEpoch, stillVisual.routeEpoch)
    }

    @Test
    fun structuralReplanBeforeTargetBindingStillBelongsToDeepSeek() {
        val session = VisualExecutionSessionState()
        session.markStructuralReplan()

        val replanning = session.runtimeContext(testSnapshot("com.yuchen.ailedger"))

        assertEquals(VisualSurfaceState.Replanning, replanning.surfaceState)
        assertFalse(replanning.guiPlusEligible)
        assertEquals(1L, replanning.routeEpoch)
    }

    @Test
    fun observationIdChangesAcrossSurfaceEpochOrScreenContext() {
        val first = testSnapshot("com.jingdong.app.mall", texts = listOf("首页", "搜索"))
        val changed = testSnapshot("com.jingdong.app.mall", texts = listOf("商品详情", "立即购买"))

        val firstId = VisualObservationProtocol.observationId(first, routeEpoch = 1L, surfaceEpoch = 2L)
        val changedScreenId = VisualObservationProtocol.observationId(changed, routeEpoch = 1L, surfaceEpoch = 2L)
        val changedEpochId = VisualObservationProtocol.observationId(first, routeEpoch = 1L, surfaceEpoch = 3L)

        assertNotEquals(firstId, changedScreenId)
        assertNotEquals(firstId, changedEpochId)
        assertEquals(24, firstId.length)
    }

    @Test
    fun actionFreshnessRequiresSamePackageAndSameScreenContext() {
        val observed = testSnapshot("com.jingdong.app.mall", texts = listOf("首页", "搜索"))
        val same = testSnapshot("com.jingdong.app.mall", texts = listOf("首页", "搜索"))
        val changed = testSnapshot("com.jingdong.app.mall", texts = listOf("商品详情", "立即购买"))
        val anotherPackage = testSnapshot("com.taobao.taobao", texts = listOf("首页", "搜索"))

        assertTrue(VisualObservationProtocol.isActionContextFresh(observed, same))
        assertFalse(VisualObservationProtocol.isActionContextFresh(observed, changed))
        assertFalse(VisualObservationProtocol.isActionContextFresh(observed, anotherPackage))
    }

    private fun testSnapshot(
        packageName: String,
        texts: List<String> = listOf("首页"),
    ): AgentScreenSnapshot {
        return AgentScreenSnapshot(
            currentApp = packageName,
            packageName = packageName,
            nodeCount = texts.size,
            capturedNodeCount = texts.size,
            texts = texts,
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
    }
}

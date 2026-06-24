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
    fun selectedTargetAlwaysRequestsAVisualObservationUntilTaskEnds() {
        val session = VisualExecutionSessionState()
        assertFalse(session.requiresVisualObservation())

        session.beginLaunch("com.hexin.plat.android")

        assertTrue(session.requiresVisualObservation())
        session.markStructuralReplan()
        assertTrue(session.requiresVisualObservation())
    }

    @Test
    fun realContentPackageDriftRevokesGuiPlusOwnershipAndRequestsFreshRoute() {
        val session = VisualExecutionSessionState()
        session.beginLaunch("com.jingdong.app.mall")
        session.markTargetVerified("com.jingdong.app.mall")
        val routeEpochBeforeDrift = session.routeEpoch

        val interrupted = session.runtimeContext(testSnapshot("com.tencent.mobileqq"))

        assertEquals(VisualSurfaceState.Replanning, interrupted.surfaceState)
        assertFalse(interrupted.guiPlusEligible)
        assertEquals("com.jingdong.app.mall", interrupted.selectedTargetPackage)
        assertEquals("", interrupted.verifiedTargetPackage)
        assertEquals("com.tencent.mobileqq", interrupted.currentPackage)
        assertEquals(routeEpochBeforeDrift + 1L, interrupted.routeEpoch)
    }

    @Test
    fun transientSystemOrOverlayPackageDoesNotDestroyVerifiedTargetBinding() {
        val session = VisualExecutionSessionState()
        session.beginLaunch("com.hexin.plat.android")
        session.markTargetVerified("com.hexin.plat.android")
        val routeEpochBeforeTransientSurface = session.routeEpoch

        val permissionSurface = session.runtimeContext(testSnapshot("com.android.permissioncontroller", includeVisual = false))
        val emptySurface = session.runtimeContext(testSnapshot("", includeVisual = false))
        val overlaySurface = session.runtimeContext(testSnapshot("com.yuchen.ailedger", includeVisual = false))

        assertEquals(VisualSurfaceState.WorkSurface, permissionSurface.surfaceState)
        assertEquals("com.hexin.plat.android", permissionSurface.verifiedTargetPackage)
        assertFalse(permissionSurface.guiPlusEligible)
        assertEquals(VisualSurfaceState.WorkSurface, emptySurface.surfaceState)
        assertEquals(VisualSurfaceState.WorkSurface, overlaySurface.surfaceState)
        assertEquals(routeEpochBeforeTransientSurface, session.routeEpoch)
    }

    @Test
    fun exactSelectedPackageRecoversLaunchHandoffWithoutSemanticScreenGuessing() {
        val session = VisualExecutionSessionState()
        session.beginLaunch("com.hexin.plat.android")
        session.markStructuralReplan()

        val recovered = session.runtimeContext(
            testSnapshot("com.hexin.plat.android", includeVisual = false),
        )

        assertEquals(VisualSurfaceState.WorkSurface, recovered.surfaceState)
        assertEquals("com.hexin.plat.android", recovered.selectedTargetPackage)
        assertEquals("com.hexin.plat.android", recovered.verifiedTargetPackage)
        assertTrue(recovered.guiPlusEligible)
    }

    @Test
    fun structuralReplanAfterVerifiedHandoffRevokesGuiPlusOwnershipUntilTargetReturns() {
        val session = VisualExecutionSessionState()
        session.beginLaunch("com.jingdong.app.mall")
        session.markTargetVerified("com.jingdong.app.mall")
        session.markStructuralReplan()

        val replanning = session.runtimeContext(testSnapshot("com.yuchen.ailedger", includeVisual = false))

        assertEquals(VisualSurfaceState.Replanning, replanning.surfaceState)
        assertFalse(replanning.guiPlusEligible)
        assertEquals("com.jingdong.app.mall", replanning.selectedTargetPackage)
        assertEquals("", replanning.verifiedTargetPackage)
        assertEquals(1L, replanning.routeEpoch)
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
    fun observationIdChangesAcrossSurfaceEpochScreenContextOrVisualFrame() {
        val first = testSnapshot("com.jingdong.app.mall", texts = listOf("首页", "搜索"), visualBase64 = "YWJj")
        val changed = testSnapshot("com.jingdong.app.mall", texts = listOf("商品详情", "立即购买"), visualBase64 = "YWJj")
        val changedVisual = testSnapshot("com.jingdong.app.mall", texts = listOf("首页", "搜索"), visualBase64 = "ZGVm")

        val firstId = VisualObservationProtocol.observationId(first, routeEpoch = 1L, surfaceEpoch = 2L)
        val changedScreenId = VisualObservationProtocol.observationId(changed, routeEpoch = 1L, surfaceEpoch = 2L)
        val changedVisualId = VisualObservationProtocol.observationId(changedVisual, routeEpoch = 1L, surfaceEpoch = 2L)
        val changedEpochId = VisualObservationProtocol.observationId(first, routeEpoch = 1L, surfaceEpoch = 3L)

        assertNotEquals(firstId, changedScreenId)
        assertNotEquals(firstId, changedVisualId)
        assertNotEquals(firstId, changedEpochId)
        assertEquals(24, firstId.length)
    }

    @Test
    fun actionFreshnessIgnoresDynamicPixelsAndNodeTextInsideSamePackage() {
        val observed = testSnapshot(
            "com.hexin.plat.android",
            texts = listOf("亨通光电", "10.52", "电量80%"),
            visualBase64 = "ZnJhbWUtMQ==",
        )
        val marketAndStatusChanged = testSnapshot(
            "com.hexin.plat.android",
            texts = listOf("亨通光电", "10.53", "电量79%"),
            visualBase64 = "ZnJhbWUtMg==",
        )
        val lightweightPreExecutionCapture = testSnapshot(
            "com.hexin.plat.android",
            texts = listOf("亨通光电", "10.54"),
            includeVisual = false,
        )
        val anotherPackage = testSnapshot("com.android.permissioncontroller")
        val unknownPackage = testSnapshot("")

        assertTrue(VisualObservationProtocol.isActionContextFresh(observed, marketAndStatusChanged))
        assertTrue(VisualObservationProtocol.isActionContextFresh(observed, lightweightPreExecutionCapture))
        assertFalse(VisualObservationProtocol.isActionContextFresh(observed, anotherPackage))
        assertFalse(VisualObservationProtocol.isActionContextFresh(observed, unknownPackage))
    }

    private fun testSnapshot(
        packageName: String,
        texts: List<String> = listOf("首页"),
        visualBase64: String = "YWJj",
        includeVisual: Boolean = true,
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
            visual = if (includeVisual) {
                AgentScreenVisual(
                    available = true,
                    mimeType = "image/jpeg",
                    width = 720,
                    height = 1280,
                    displayWidth = 1080,
                    displayHeight = 2400,
                    base64Jpeg = visualBase64,
                    source = "test",
                    reason = "test",
                )
            } else {
                null
            },
        )
    }
}

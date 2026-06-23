package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualAppIdentityTest {
    @Test
    fun topLevelAppRefIsParsedAsPackageIdentity() {
        val step = CloudAgentStep.fromJson(JSONObject().apply {
            put("type", "open_app")
            put("appRef", "com.hexin.plat.android")
            put("appName", "同花顺")
        })

        assertNotNull(step)
        assertEquals("com.hexin.plat.android", step?.packageName)
        assertEquals("同花顺", step?.appName)
    }

    @Test
    fun argumentAppRefIsParsedAsPackageIdentityWithoutDisplayName() {
        val step = CloudAgentStep.fromJson(JSONObject().apply {
            put("type", "open_app")
            put("arguments", JSONObject().apply {
                put("appRef", "com.tencent.mobileqq")
            })
        })

        assertNotNull(step)
        assertEquals("com.tencent.mobileqq", step?.packageName)
        assertTrue(step?.appName.isNullOrBlank())
    }

    @Test
    fun visualOpenAppValidationRequiresPackageButNotDisplayName() {
        val snapshot = testSnapshot(currentPackage = "com.yuchen.ailedger")
        val valid = VisualActionValidator.validate(
            CloudAgentStep(
                type = "open_app",
                packageName = "com.hexin.plat.android",
                appName = null,
            ),
            snapshot,
        )
        val invalid = VisualActionValidator.validate(
            CloudAgentStep(
                type = "open_app",
                packageName = null,
                appName = "同花顺",
            ),
            snapshot,
        )

        assertTrue(valid.ok)
        assertFalse(invalid.ok)
        assertTrue(invalid.message.contains("packageName"))
    }

    @Test
    fun duplicatePackageOpenReachesCloudReplanningEvenWhenDisplayNameDiffers() {
        val snapshot = testSnapshot(currentPackage = "com.hexin.plat.android")
        val validation = VisualActionValidator.validate(
            CloudAgentStep(
                type = "open_app",
                packageName = "com.hexin.plat.android",
                appName = "同花顺",
            ),
            snapshot,
        )

        assertTrue(validation.ok)
    }

    private fun testSnapshot(currentPackage: String): AgentScreenSnapshot {
        return AgentScreenSnapshot(
            currentApp = currentPackage,
            packageName = currentPackage,
            nodeCount = 0,
            capturedNodeCount = 0,
            texts = emptyList(),
            allNodes = emptyList(),
            clickableNodes = emptyList(),
            inputNodes = emptyList(),
            scrollableNodes = emptyList(),
            visual = null,
        )
    }
}

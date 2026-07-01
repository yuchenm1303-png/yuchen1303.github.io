package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppManagementControllerTest {
    private val userApp = ManagedAppSummary(
        label = "示例应用",
        packageName = "com.example.app",
        uid = 10123,
        isSystemApp = false,
        isEnabled = true,
        isLaunchable = true,
        isProtected = false,
        protectionReason = "",
        apkBytes = 1024L,
    )

    @Test
    fun factoryBuildsCanonicalPackageAndSettingsArguments() {
        val step = AppManagementActionFactory.create(ManagedAppAction.NotificationSettings, userApp)

        assertEquals("open_app_settings", step.type)
        assertEquals("com.example.app", step.packageName)
        assertEquals("com.example.app", step.toolArgs?.optString("packageName"))
        assertEquals("notification", step.toolArgs?.optString("page"))
        assertTrue(DeviceControlSpecs.validate(step).ok)
    }

    @Test
    fun criticalActionsRetainSpecConfirmationBoundary() {
        val clearData = AppManagementActionFactory.create(ManagedAppAction.ClearData, userApp)
        val uninstall = AppManagementActionFactory.create(ManagedAppAction.Uninstall, userApp)

        assertTrue(clearData.requiresConfirmation)
        assertTrue(uninstall.requiresConfirmation)
        assertEquals("critical", clearData.riskLevel)
        assertEquals("critical", uninstall.riskLevel)
    }

    @Test
    fun protectedPackagesCannotBeStoppedClearedDisabledOrUninstalled() {
        val protected = userApp.copy(
            packageName = "com.android.systemui",
            isSystemApp = true,
            isProtected = true,
            protectionReason = "Android 核心系统组件",
        )

        listOf(
            ManagedAppAction.ForceStop,
            ManagedAppAction.ClearData,
            ManagedAppAction.Disable,
            ManagedAppAction.Uninstall,
        ).forEach { action ->
            val availability = AppManagementActionPolicy.availability(action, protected)
            assertFalse(action.title, availability.enabled)
            assertTrue(availability.reason.isNotBlank())
        }
    }

    @Test
    fun systemAppsCannotUseIrreversibleDataOrUninstallActions() {
        val system = userApp.copy(isSystemApp = true)

        assertFalse(AppManagementActionPolicy.availability(ManagedAppAction.ClearData, system).enabled)
        assertFalse(AppManagementActionPolicy.availability(ManagedAppAction.Uninstall, system).enabled)
        assertTrue(AppManagementActionPolicy.availability(ManagedAppAction.Disable, system).enabled)
    }

    @Test
    fun enablingDisabledProtectedAppRemainsAvailableAsRecoveryPath() {
        val disabledProtected = userApp.copy(
            isEnabled = false,
            isProtected = true,
            protectionReason = "默认输入法",
        )

        assertTrue(AppManagementActionPolicy.availability(ManagedAppAction.Enable, disabledProtected).enabled)
    }
}

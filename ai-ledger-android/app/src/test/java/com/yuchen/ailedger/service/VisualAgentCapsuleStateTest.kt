package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualAgentCapsuleStateTest {
    @Test
    fun runningPositionedTapTargetUsesMovePhase() {
        val now = 10_000L
        val progress = AgentOverlayProgress(
            taskId = 7L,
            enabled = true,
            running = true,
            status = "执行中",
            currentAction = "点击设置按钮",
        )
        val target = VisualAgentHudTarget(
            taskId = 7L,
            positioned = true,
            actionType = "tap_xy",
            plannedAt = now - 120L,
        )

        val state = VisualAgentCapsuleStateResolver.resolve(progress, target, now)

        assertTrue(state.active)
        assertEquals(VisualAgentCapsuleMode.Running, state.mode)
        assertEquals(VisualAgentCapsulePhase.Move, state.phase)
        assertEquals("Step 3 / 5", state.meta)
    }

    @Test
    fun resultLogUsesVerifyPhase() {
        val progress = AgentOverlayProgress(
            taskId = 3L,
            enabled = true,
            running = true,
            logs = listOf("结果：已进入目标页面"),
        )

        val state = VisualAgentCapsuleStateResolver.resolve(progress, null, 1_000L)

        assertEquals(VisualAgentCapsulePhase.Verify, state.phase)
        assertEquals("正在验证结果", state.title)
    }

    @Test
    fun pendingInputOverridesRuntimeAndRequestsExpansion() {
        val input = AgentPendingUserInput(
            id = 88L,
            title = "需要你选择",
            actionText = "选择登录方式",
            message = "请选择短信登录或密码登录",
        )
        val progress = AgentOverlayProgress(
            taskId = 4L,
            enabled = true,
            running = true,
            pendingUserInput = input,
        )

        val state = VisualAgentCapsuleStateResolver.resolve(progress, null, 1_000L)

        assertEquals(VisualAgentCapsuleMode.PendingInput, state.mode)
        assertEquals("需要你选择", state.title)
        assertEquals("input:88", state.autoExpandKey)
        assertTrue(state.showConversationInput)
        assertFalse(state.canPauseOrResume)
    }

    @Test
    fun sensitiveInputNeverShowsEditableField() {
        val input = AgentPendingUserInput(
            id = 91L,
            actionText = "输入验证码",
            message = "请完成验证码",
            sensitive = true,
        )
        val progress = AgentOverlayProgress(
            taskId = 5L,
            enabled = true,
            running = true,
            pendingUserInput = input,
        )

        val state = VisualAgentCapsuleStateResolver.resolve(progress, null, 1_000L)

        assertTrue(state.showSensitiveCompletion)
        assertFalse(state.showConversationInput)
        assertEquals("隐私接管", state.meta)
    }

    @Test
    fun takeoverPauseHasStableAutoExpandKey() {
        val progress = AgentOverlayProgress(
            taskId = 9L,
            enabled = true,
            running = true,
            userTakeoverPaused = true,
            logs = listOf("目标：打开设置", "接管：用户接管中"),
        )

        val state = VisualAgentCapsuleStateResolver.resolve(progress, null, 1_000L)

        assertEquals(VisualAgentCapsuleMode.UserTakeover, state.mode)
        assertTrue(state.paused)
        assertEquals("pause:9:2", state.autoExpandKey)
        assertTrue(state.showConversationInput)
        assertTrue(state.canPauseOrResume)
    }

    @Test
    fun confirmationShowsDedicatedMode() {
        val confirmation = AgentPendingConfirmation(
            id = 73L,
            actionText = "发送消息",
            message = "即将发送消息",
        )
        val progress = AgentOverlayProgress(
            taskId = 11L,
            enabled = true,
            running = true,
            pendingConfirmation = confirmation,
        )

        val state = VisualAgentCapsuleStateResolver.resolve(progress, null, 1_000L)

        assertEquals(VisualAgentCapsuleMode.PendingConfirmation, state.mode)
        assertEquals("confirm:73", state.autoExpandKey)
        assertTrue(state.showConfirmation)
        assertFalse(state.canPauseOrResume)
    }
}

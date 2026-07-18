package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientToolExecutionResultCodecTest {
    @Test
    fun preservesVerifiedResultDiagnosticsAndUndoStepAcrossReplay() {
        val call = CloudClientToolCall(
            schema = AI_WORKER_CLIENT_TOOL_CALL_SCHEMA,
            id = "call-device-1",
            name = "device_control",
            arguments = JSONObject()
                .put("action", "disable_app")
                .put("args", JSONObject().put("packageName", "com.example.app")),
        )
        val step = CloudAgentStep(
            type = "disable_app",
            packageName = "com.example.app",
            riskLevel = "high",
            requiresConfirmation = true,
            toolArgs = JSONObject().put("packageName", "com.example.app"),
        )
        val undo = CloudAgentStep(
            type = "enable_app",
            packageName = "com.example.app",
            reason = "撤销禁用应用",
            requiresConfirmation = true,
            toolArgs = JSONObject().put("packageName", "com.example.app"),
        )
        val original = AgentExecutionResult(
            ok = true,
            message = "已执行并核验：禁用应用。",
            shouldContinue = false,
            undoStep = undo,
            diagnostics = "internal_control_verified:disable_app",
        )

        val receipt = ClientToolExecutionResultCodec.encode(
            call = call,
            step = step,
            result = original,
            executionOwner = "android_structured_device_tool",
        )
        val replay = ClientToolExecutionResultCodec.decode(receipt, "fallback")

        assertTrue(replay.ok)
        assertFalse(replay.shouldContinue)
        assertEquals(original.message, replay.message)
        assertTrue(replay.diagnostics.contains("internal_control_verified:disable_app"))
        assertTrue(replay.diagnostics.contains("idempotent_replay"))
        assertNotNull(replay.undoStep)
        assertEquals("enable_app", replay.undoStep?.type)
        assertEquals("com.example.app", replay.undoStep?.packageName)
        assertEquals("com.example.app", replay.undoStep?.argString("packageName"))
        assertTrue(replay.undoStep?.requiresConfirmation == true)
        assertEquals("android_structured_device_tool", receipt.getString("executionOwner"))
        assertEquals("device_control", receipt.getString("toolName"))
        assertEquals("disable_app", receipt.getString("stepType"))
    }
}

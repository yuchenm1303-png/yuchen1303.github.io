package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentOrchestratorTest {
    @Test
    fun visualModesEnterTheSingleVisualLoopDirectly() {
        assertEquals(AgentOrchestratorRoute.VisualLoop, AgentOrchestrator.routeFor(AgentExecutionMode.VisualForce))
        assertEquals(AgentOrchestratorRoute.VisualLoop, AgentOrchestrator.routeFor(AgentExecutionMode.ExplicitAgent))
    }

    @Test
    fun normalChatDeviceToolKeepsTheNonVisualRuntime() {
        assertEquals(AgentOrchestratorRoute.LegacyRunner, AgentOrchestrator.routeFor(AgentExecutionMode.NormalChatDeviceTool))
    }

    @Test
    fun onlyVisualForceDependsOnTheHomeAgentSwitch() {
        assertTrue(VisualLoopRunner.requiresAgentSwitch(AgentExecutionMode.VisualForce))
        assertFalse(VisualLoopRunner.requiresAgentSwitch(AgentExecutionMode.ExplicitAgent))
        assertFalse(VisualLoopRunner.requiresAgentSwitch(AgentExecutionMode.NormalChatDeviceTool))
    }
}

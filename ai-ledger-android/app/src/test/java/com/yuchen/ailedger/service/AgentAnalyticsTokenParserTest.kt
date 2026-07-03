package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.AgentTokenAccuracy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentAnalyticsTokenParserTest {
    @Test
    fun parsesOpenAiUsageWithoutDoubleCountingReasoningTokens() {
        val response = JSONObject(
            """
            {
              "model": "gpt-test",
              "usage": {
                "prompt_tokens": 120,
                "completion_tokens": 80,
                "reasoning_tokens": 50,
                "total_tokens": 200
              }
            }
            """.trimIndent(),
        )

        val usage = requireNotNull(AgentAnalyticsTokenParser.parseProviderUsage(response))

        assertEquals(AgentTokenAccuracy.Provider, usage.accuracy)
        assertEquals(120L, usage.inputTokens)
        assertEquals(80L, usage.outputTokens)
        assertEquals(50L, usage.reasoningTokens)
        assertEquals(200L, usage.normalizedTotal)
    }

    @Test
    fun parsesGeminiUsageMetadata() {
        val response = JSONObject(
            """
            {
              "modelName": "gemini-test",
              "usageMetadata": {
                "promptTokenCount": 90,
                "candidatesTokenCount": 25,
                "thoughtsTokenCount": 15,
                "totalTokenCount": 115
              }
            }
            """.trimIndent(),
        )

        val usage = requireNotNull(AgentAnalyticsTokenParser.parseProviderUsage(response))

        assertEquals(90L, usage.inputTokens)
        assertEquals(25L, usage.outputTokens)
        assertEquals(15L, usage.reasoningTokens)
        assertEquals(115L, usage.normalizedTotal)
    }

    @Test
    fun estimatesImagePayloadWithoutCountingBase64Characters() {
        val payload = JSONObject().apply {
            put("prompt", "请分析图片")
            put("base64Data", "A".repeat(200_000))
        }

        val usage = AgentAnalyticsTokenParser.estimateUsage(payload, JSONObject().put("reply", "完成"))

        assertEquals(AgentTokenAccuracy.Estimated, usage.accuracy)
        assertTrue(usage.inputTokens in 1_024L..1_100L)
        assertTrue(usage.outputTokens > 0L)
    }

    @Test
    fun extractsModelToolsAndSearchActivity() {
        val response = JSONObject(
            """
            {
              "data": {
                "model": "deepseek-test",
                "searchUsed": true,
                "agentAction": {
                  "capability": "run_agent_task",
                  "title": "智能体任务"
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("deepseek-test", AgentAnalyticsTokenParser.modelId(null, response))
        assertTrue(AgentAnalyticsTokenParser.webSearchUsed(response))
        assertEquals(listOf("run_agent_task" to "智能体任务"), AgentAnalyticsTokenParser.toolKeys(response))
    }
}

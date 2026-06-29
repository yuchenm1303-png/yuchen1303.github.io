package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualReasoningPolicyTest {
    @Test
    fun cleanGroundedPathUsesFastReasoning() {
        val context = VisualReasoningPolicy.evaluate(baseMemory())

        assertEquals(VisualReasoningDepth.Fast, context.depth)
        assertEquals(0, context.selfCheckPasses)
        assertEquals(1, context.candidateHypothesisLimit)
        assertTrue(context.directExecutionAllowed)
        assertFalse(context.deepThinkingRequested)
    }

    @Test
    fun firstNoProgressUsesNormalReasoning() {
        val context = VisualReasoningPolicy.evaluate(
            baseMemory(),
            listOf(
                "tap_xy|0.4|0.6:ok:result=已点击",
                "visual_execution_observed:action=tap_xy|screenChanged=false|replanRequired=false|reason=screen_unchanged_unjudged",
            ),
        )

        assertEquals(VisualReasoningDepth.Normal, context.depth)
        assertEquals(1, context.noProgressCount)
        assertEquals(1, context.selfCheckPasses)
        assertTrue(VisualReasoningTrigger.FirstNoProgress in context.triggers)
    }

    @Test
    fun repeatedNoProgressAndSameActionUseDeepReasoning() {
        val context = VisualReasoningPolicy.evaluate(
            baseMemory(),
            listOf(
                "tap_xy|0.4|0.6:ok:result=已点击",
                "visual_execution_observed:action=tap_xy|screenChanged=false|replanRequired=false|reason=screen_unchanged_unjudged",
                "tap_xy|0.4|0.6:ok:result=已点击",
                "visual_execution_observed:action=tap_xy|screenChanged=false|replanRequired=true|reason=repeated_hypothesis_failure",
            ),
        )

        assertEquals(VisualReasoningDepth.Deep, context.depth)
        assertEquals(2, context.noProgressCount)
        assertEquals(2, context.sameActionCount)
        assertEquals(2, context.selfCheckPasses)
        assertFalse(context.directExecutionAllowed)
        assertTrue(VisualReasoningTrigger.RepeatedNoProgress in context.triggers)
    }

    @Test
    fun confirmedScreenProgressClearsOldDeepPressure() {
        val actions = mutableListOf(
            "tap_xy|0.4|0.6:ok:result=已点击",
            "visual_execution_observed:action=tap_xy|screenChanged=false|replanRequired=false|reason=screen_unchanged_unjudged",
            "tap_xy|0.4|0.6:ok:result=已点击",
            "visual_execution_observed:action=tap_xy|screenChanged=false|replanRequired=true|reason=repeated_hypothesis_failure",
        )
        assertEquals(VisualReasoningDepth.Deep, VisualReasoningPolicy.evaluate(baseMemory(), actions).depth)

        actions += "visual_execution_observed:action=tap_xy|screenChanged=true|replanRequired=false|reason=screen_changed_unjudged"
        val context = VisualReasoningPolicy.evaluate(baseMemory(), actions)

        assertEquals(VisualReasoningDepth.Fast, context.depth)
        assertEquals(0, context.noProgressCount)
        assertEquals(0, context.sameActionCount)
    }

    @Test
    fun userCorrectionUsesDeepButSupplementUsesNormal() {
        val correction = VisualReasoningPolicy.evaluate(
            baseMemory().copy(
                taskRevision = 2,
                taskRevisionPending = true,
                currentMilestoneInvalidated = true,
                latestUserUpdate = VisualUserTaskUpdate(
                    revision = 2,
                    kind = VisualUserTaskUpdateKind.GoalRevision,
                    content = "目标改为查看自选股",
                    invalidatesCurrentMilestone = true,
                ),
            ),
        )
        val supplement = VisualReasoningPolicy.evaluate(
            baseMemory().copy(
                taskRevision = 3,
                taskRevisionPending = true,
                latestUserUpdate = VisualUserTaskUpdate(
                    revision = 3,
                    kind = VisualUserTaskUpdateKind.Supplement,
                    content = "只看今天的数据",
                ),
            ),
        )

        assertEquals(VisualReasoningDepth.Deep, correction.depth)
        assertTrue(VisualReasoningTrigger.UserCorrection in correction.triggers)
        assertEquals(VisualReasoningDepth.Normal, supplement.depth)
        assertTrue(VisualReasoningTrigger.UserSupplement in supplement.triggers)
    }

    @Test
    fun completionCandidateAndBudgetExhaustionRequireDeepReasoning() {
        val completion = VisualReasoningPolicy.evaluate(
            baseMemory(),
            listOf("finish_verification_pending:observationId=observation-2"),
        )
        val exhausted = VisualReasoningPolicy.evaluate(
            baseMemory().copy(remainingExplorationBudget = 0),
        )

        assertEquals(VisualReasoningDepth.Deep, completion.depth)
        assertTrue(completion.completionEvidenceStrict)
        assertTrue(completion.freshObservationRequired)
        assertEquals(VisualReasoningDepth.Deep, exhausted.depth)
        assertTrue(VisualReasoningTrigger.ExplorationBudgetPressure in exhausted.triggers)
    }

    @Test
    fun reasoningSignalContainsNoUserReplyContent() {
        val secretReply = "目标改为查看我的私密账户"
        val context = VisualReasoningPolicy.evaluate(
            baseMemory().copy(
                taskRevision = 4,
                taskRevisionPending = true,
                currentMilestoneInvalidated = true,
                latestUserUpdate = VisualUserTaskUpdate(
                    revision = 4,
                    kind = VisualUserTaskUpdateKind.Correction,
                    content = secretReply,
                    invalidatesCurrentMilestone = true,
                ),
            ),
        )

        val promptLine = context.toPromptLine()
        val replanLine = VisualReasoningPolicy.deepReplanLine(context).orEmpty()

        assertFalse(promptLine.contains(secretReply))
        assertFalse(replanLine.contains(secretReply))
        assertTrue(promptLine.startsWith(VisualReasoningContext.PROMPT_PREFIX))
        assertTrue(replanLine.startsWith(VisualReasoningPolicy.DEEP_REPLAN_PREFIX))
    }

    @Test
    fun loopMemoryReplacesDeepSignalAfterProgress() {
        val actions = mutableListOf(
            "tap_xy|0.4|0.6:ok:result=已点击",
            "visual_execution_observed:action=tap_xy|screenChanged=false|replanRequired=false|reason=screen_unchanged_unjudged",
            "tap_xy|0.4|0.6:ok:result=已点击",
            "visual_execution_observed:action=tap_xy|screenChanged=false|replanRequired=true|reason=repeated_hypothesis_failure",
        )
        VisualLoopMemorySupport.replaceMemoryLine(actions, baseMemory())
        assertTrue(actions.any { it.startsWith(VisualReasoningPolicy.DEEP_REPLAN_PREFIX) })
        assertTrue(actions.any { it.startsWith(VisualReasoningContext.PROMPT_PREFIX) && it.contains("depth=deep") })

        actions += "visual_execution_observed:action=tap_xy|screenChanged=true|replanRequired=false|reason=screen_changed_unjudged"
        VisualLoopMemorySupport.replaceMemoryLine(actions, baseMemory())

        assertFalse(actions.any { it.startsWith(VisualReasoningPolicy.DEEP_REPLAN_PREFIX) })
        assertTrue(actions.any { it.startsWith(VisualReasoningContext.PROMPT_PREFIX) && it.contains("depth=fast") })
    }

    @Test
    fun taskMemoryJsonCarriesAdaptiveReasoningContext() {
        val json = baseMemory().copy(
            progressStatus = "ambiguous",
        ).toJson()

        assertEquals("visual_task_memory_v3_adaptive_reasoning", json.getString("schema"))
        assertEquals("normal", json.getString("reasoningDepth"))
        assertEquals("normal", json.getJSONObject("reasoningContext").getString("depth"))
        assertTrue(json.getJSONArray("reasoningTriggers").toString().contains("semantic_ambiguity"))
    }

    private fun baseMemory(): VisualTaskMemory = VisualTaskMemory(
        originalGoal = "测试任务",
        currentMilestoneId = "m1",
        remainingExplorationBudget = 2,
        progressStatus = "surface_verified",
        legacyMode = false,
        taskContract = VisualTaskContract(
            originalGoal = "测试任务",
            currentMilestoneId = "m1",
            milestones = listOf(VisualTaskMilestone(id = "m1")),
            explorationBudgetPerMilestone = 2,
        ),
    )
}

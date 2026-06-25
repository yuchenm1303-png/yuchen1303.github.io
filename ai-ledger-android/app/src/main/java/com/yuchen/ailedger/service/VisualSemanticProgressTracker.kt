package com.yuchen.ailedger.service

import java.text.Normalizer
import java.util.ArrayDeque
import org.json.JSONArray
import org.json.JSONObject

enum class VisualSemanticProgressStatus(val wireValue: String) {
    Advanced("advanced"),
    Stalled("stalled"),
    Regressed("regressed"),
    Ambiguous("ambiguous"),
}

data class VisualSemanticProgressResult(
    val status: VisualSemanticProgressStatus,
    val actionSignature: String,
    val milestoneId: String,
    val pageChanged: Boolean,
    val packageChanged: Boolean,
    val expectedEvidenceMatched: List<String>,
    val failureEvidenceMatched: List<String>,
    val newEvidence: List<String>,
    val failedHypothesisCount: Int,
    val explorationBudgetRemaining: Int,
    val requiresStrategyChange: Boolean,
    val shouldPauseForUser: Boolean,
    val structuralRegression: Boolean,
    val reason: String,
) {
    fun toFeedbackLine(step: CloudAgentStep): String {
        val expected = expectedEvidenceMatched.joinToString(",").take(180)
        val failed = failureEvidenceMatched.joinToString(",").take(180)
        val evidence = newEvidence.joinToString(",").take(220)
        val fields = buildString {
            append("|semanticStatus=").append(status.wireValue)
            append("|milestone=").append(milestoneId.take(80))
            append("|pageChanged=").append(pageChanged)
            append("|packageChanged=").append(packageChanged)
            append("|expectedMatched=").append(expected)
            append("|failureMatched=").append(failed)
            append("|newEvidence=").append(evidence)
            append("|failedHypotheses=").append(failedHypothesisCount)
            append("|explorationBudgetRemaining=").append(explorationBudgetRemaining)
            append("|requiresStrategyChange=").append(requiresStrategyChange)
            append("|reason=").append(reason.take(220))
        }
        return when (status) {
            VisualSemanticProgressStatus.Advanced ->
                "visual_screen_changed:$actionSignature:screen=changed$fields"

            VisualSemanticProgressStatus.Regressed -> if (structuralRegression) {
                "visual_action_rejected:type=${step.type}|failureClass=structural_route|action=$actionSignature$fields|replanRequired=true"
            } else {
                "visual_local_retry:action=$actionSignature:count=$failedHypothesisCount$fields"
            }

            VisualSemanticProgressStatus.Stalled,
            VisualSemanticProgressStatus.Ambiguous ->
                "visual_local_retry:action=$actionSignature:count=$failedHypothesisCount$fields"
        }
    }
}

/**
 * Evidence-driven runtime harness for the GUI Plus visual loop.
 *
 * It never reads the user's goal, selects an app, chooses a target, or rewrites an action. It only
 * compares factual before/after observations against cloud-provided action intent and prevents an
 * already-failed hypothesis from being repeated on the same page.
 */
class VisualSemanticProgressTracker(
    private val maxFailedHypothesesBeforePause: Int = 3,
    private val maxConsecutiveAmbiguousTransitions: Int = 2,
) {
    private val failedHypotheses = linkedSetOf<String>()
    private val confirmedFingerprints = ArrayDeque<String>()
    private var currentMilestoneId: String = DEFAULT_MILESTONE_ID
    private var consecutiveAmbiguousTransitions: Int = 0
    private var consecutiveRegressions: Int = 0

    fun onVerifiedSurface(snapshot: AgentScreenSnapshot) {
        currentMilestoneId = DEFAULT_MILESTONE_ID
        clearTransientState()
        rememberConfirmed(snapshot)
    }

    fun resetAfterUserTakeover(snapshot: AgentScreenSnapshot? = null) {
        currentMilestoneId = DEFAULT_MILESTONE_ID
        clearTransientState()
        snapshot?.let(::rememberConfirmed)
    }

    fun blockedHypothesisReason(step: CloudAgentStep, snapshot: AgentScreenSnapshot): String? {
        val intent = actionIntent(step)
        ensureMilestone(intent.milestoneId)
        val key = hypothesisKey(step, snapshot, intent)
        if (key !in failedHypotheses) return null
        return "The same action hypothesis already failed on this page. GUI Plus must choose a materially different action or request user help."
    }

    fun evaluate(
        step: CloudAgentStep,
        before: AgentScreenSnapshot,
        after: AgentScreenSnapshot,
        verifiedTargetPackage: String,
    ): VisualSemanticProgressResult {
        val intent = actionIntent(step)
        ensureMilestone(intent.milestoneId)

        val actionSignature = VisualActionValidator.actionSignature(step)
        val hypothesisKey = hypothesisKey(step, before, intent)
        val beforeFingerprint = VisualActionValidator.snapshotFingerprint(before)
        val afterFingerprint = VisualActionValidator.snapshotFingerprint(after)
        val pageChanged = beforeFingerprint != afterFingerprint
        val packageChanged = before.packageName != after.packageName
        val structuralRegression = verifiedTargetPackage.isNotBlank() &&
            VisualSurfacePackagePolicy.isConfidentForeignPackage(
                currentPackage = after.packageName,
                expectedPackage = verifiedTargetPackage,
            )

        val beforeEvidence = screenEvidence(before)
        val afterEvidence = screenEvidence(after)
        val expectedBefore = matchEvidence(intent.expectedEvidence, beforeEvidence)
        val expectedAfter = matchEvidence(intent.expectedEvidence, afterEvidence)
        val newlyMatchedExpected = expectedAfter.filterNot(expectedBefore::contains)
        val failureMatched = matchEvidence(intent.failureEvidence, afterEvidence)
        val newEvidence = afterEvidence.values
            .filterNot { it.normalized in beforeEvidence.normalizedValues }
            .map { it.original }
            .distinct()
            .take(MAX_FEEDBACK_EVIDENCE)

        val inputTextVisible = step.type == "input_text" &&
            step.text?.takeIf { it.isNotBlank() }?.let(afterEvidence::contains) == true &&
            step.text?.takeIf { it.isNotBlank() }?.let(beforeEvidence::contains) != true
        val inputSurfaceChanged = step.type == "input_text" &&
            inputNodeFingerprint(before) != inputNodeFingerprint(after)
        val targetTransitionObserved = step.targetText
            ?.takeIf { it.isNotBlank() }
            ?.let { target ->
                beforeEvidence.contains(target) && pageChanged &&
                    (!afterEvidence.contains(target) || newEvidence.isNotEmpty())
            } == true
        val returnedToConfirmedPage = step.type == "back" &&
            confirmedFingerprints.contains(afterFingerprint)

        val status = when {
            structuralRegression -> VisualSemanticProgressStatus.Regressed
            failureMatched.isNotEmpty() -> VisualSemanticProgressStatus.Regressed
            newlyMatchedExpected.isNotEmpty() -> VisualSemanticProgressStatus.Advanced
            !pageChanged -> VisualSemanticProgressStatus.Stalled
            returnedToConfirmedPage -> VisualSemanticProgressStatus.Advanced
            inputTextVisible || inputSurfaceChanged -> VisualSemanticProgressStatus.Advanced
            targetTransitionObserved -> VisualSemanticProgressStatus.Advanced
            else -> VisualSemanticProgressStatus.Ambiguous
        }

        updateState(status, hypothesisKey, intent.exploratory, after)

        val shouldPauseForUser = failedHypotheses.size >= maxFailedHypothesesBeforePause ||
            consecutiveRegressions >= MAX_CONSECUTIVE_REGRESSIONS ||
            (status == VisualSemanticProgressStatus.Regressed && !intent.reversible)
        val reason = buildReason(
            status = status,
            intent = intent,
            pageChanged = pageChanged,
            expectedMatched = newlyMatchedExpected,
            failureMatched = failureMatched,
            newEvidence = newEvidence,
            structuralRegression = structuralRegression,
        )

        return VisualSemanticProgressResult(
            status = status,
            actionSignature = actionSignature,
            milestoneId = currentMilestoneId,
            pageChanged = pageChanged,
            packageChanged = packageChanged,
            expectedEvidenceMatched = newlyMatchedExpected,
            failureEvidenceMatched = failureMatched,
            newEvidence = newEvidence,
            failedHypothesisCount = failedHypotheses.size,
            explorationBudgetRemaining =
                (maxFailedHypothesesBeforePause - failedHypotheses.size).coerceAtLeast(0),
            requiresStrategyChange = status != VisualSemanticProgressStatus.Advanced,
            shouldPauseForUser = shouldPauseForUser,
            structuralRegression = structuralRegression,
            reason = reason,
        )
    }

    private fun updateState(
        status: VisualSemanticProgressStatus,
        hypothesisKey: String,
        exploratory: Boolean,
        after: AgentScreenSnapshot,
    ) {
        when (status) {
            VisualSemanticProgressStatus.Advanced -> {
                failedHypotheses.clear()
                consecutiveAmbiguousTransitions = 0
                consecutiveRegressions = 0
                rememberConfirmed(after)
            }

            VisualSemanticProgressStatus.Stalled -> {
                failedHypotheses += hypothesisKey
                consecutiveAmbiguousTransitions = 0
                consecutiveRegressions = 0
            }

            VisualSemanticProgressStatus.Regressed -> {
                failedHypotheses += hypothesisKey
                consecutiveAmbiguousTransitions = 0
                consecutiveRegressions += 1
            }

            VisualSemanticProgressStatus.Ambiguous -> {
                consecutiveAmbiguousTransitions += 1
                if (
                    exploratory ||
                    consecutiveAmbiguousTransitions >= maxConsecutiveAmbiguousTransitions
                ) {
                    failedHypotheses += hypothesisKey
                }
            }
        }
    }

    private fun ensureMilestone(milestoneId: String) {
        val cleanMilestone = milestoneId.trim().ifBlank { DEFAULT_MILESTONE_ID }
        if (cleanMilestone == currentMilestoneId) return
        currentMilestoneId = cleanMilestone
        clearTransientState()
    }

    private fun clearTransientState() {
        failedHypotheses.clear()
        confirmedFingerprints.clear()
        consecutiveAmbiguousTransitions = 0
        consecutiveRegressions = 0
    }

    private fun rememberConfirmed(snapshot: AgentScreenSnapshot) {
        val fingerprint = VisualActionValidator.snapshotFingerprint(snapshot)
        if (fingerprint.isBlank()) return
        confirmedFingerprints.remove(fingerprint)
        confirmedFingerprints.addLast(fingerprint)
        while (confirmedFingerprints.size > MAX_CONFIRMED_FINGERPRINTS) {
            confirmedFingerprints.removeFirst()
        }
    }

    private fun hypothesisKey(
        step: CloudAgentStep,
        snapshot: AgentScreenSnapshot,
        intent: VisualActionIntent,
    ): String {
        val pageKey = Integer.toHexString(
            VisualActionValidator.completionFingerprint(snapshot).hashCode(),
        )
        val actionCluster = VisualActionValidator.actionClusterSignature(step)
        val purposeKey = normalizeVisualEvidenceText(intent.purpose).take(80)
        return listOf(currentMilestoneId, pageKey, actionCluster, purposeKey).joinToString("::")
    }

    private fun buildReason(
        status: VisualSemanticProgressStatus,
        intent: VisualActionIntent,
        pageChanged: Boolean,
        expectedMatched: List<String>,
        failureMatched: List<String>,
        newEvidence: List<String>,
        structuralRegression: Boolean,
    ): String {
        return when (status) {
            VisualSemanticProgressStatus.Advanced -> when {
                expectedMatched.isNotEmpty() ->
                    "Expected evidence appeared: ${expectedMatched.joinToString(", ")}"
                intent.purpose.isNotBlank() ->
                    "The action produced observable progress for: ${intent.purpose}"
                else -> "The action produced action-specific observable progress."
            }

            VisualSemanticProgressStatus.Stalled ->
                "The screen did not produce observable progress. Do not repeat the same hypothesis."

            VisualSemanticProgressStatus.Regressed -> when {
                structuralRegression ->
                    "The verified target package was lost. The visual work surface must be rebound."
                !intent.reversible ->
                    "The action produced failure evidence and is not declared reversible. User review is required."
                failureMatched.isNotEmpty() ->
                    "Failure evidence appeared: ${failureMatched.joinToString(", ")}"
                else -> "The action moved away from the expected visual state."
            }

            VisualSemanticProgressStatus.Ambiguous -> buildString {
                append("The screen changed, but the action's expected evidence was not proven")
                if (intent.expectedEvidence.isEmpty()) append(" because no expectedEvidence was supplied")
                if (newEvidence.isNotEmpty()) append(". New visible evidence: ${newEvidence.joinToString(", ")}")
                if (!pageChanged) append(". The page remained unchanged")
                append(". GUI Plus must verify the current subgoal before further exploration.")
            }
        }
    }

    private fun actionIntent(step: CloudAgentStep): VisualActionIntent {
        val args = step.toolArgs
        val nested = args?.optJSONObject("actionIntent")
            ?: args?.optJSONObject("progressContract")
            ?: args?.optJSONObject("semanticIntent")
        val milestoneId = firstNonBlank(
            nested?.optString("milestoneId"),
            nested?.optString("milestone"),
            args?.optString("milestoneId"),
            args?.optString("milestone"),
        ) ?: DEFAULT_MILESTONE_ID
        val purpose = firstNonBlank(
            nested?.optString("purpose"),
            nested?.optString("goal"),
            args?.optString("purpose"),
            args?.optString("subgoal"),
            step.reason,
            step.targetText,
        ).orEmpty()
        val expectedEvidence = mergeEvidenceLists(
            nested.stringList("expectedEvidence", "successEvidence", "expected"),
            args.stringList("expectedEvidence", "successEvidence"),
        )
        val failureEvidence = mergeEvidenceLists(
            nested.stringList("failureEvidence", "wrongEvidence", "negativeEvidence"),
            args.stringList("failureEvidence", "wrongEvidence", "negativeEvidence"),
        )
        val exploratory = nested.flexibleBoolean("exploratory")
            ?: args.flexibleBoolean("exploratory")
            ?: (step.type in setOf("swipe", "scroll", "wait") && expectedEvidence.isEmpty())
        val reversible = nested.flexibleBoolean("reversible")
            ?: args.flexibleBoolean("reversible")
            ?: (step.type != "input_text")
        return VisualActionIntent(
            milestoneId = milestoneId,
            purpose = purpose,
            expectedEvidence = expectedEvidence,
            failureEvidence = failureEvidence,
            exploratory = exploratory,
            reversible = reversible,
        )
    }

    private data class VisualActionIntent(
        val milestoneId: String,
        val purpose: String,
        val expectedEvidence: List<String>,
        val failureEvidence: List<String>,
        val exploratory: Boolean,
        val reversible: Boolean,
    )

    private data class ScreenEvidence(
        val values: List<EvidenceValue>,
    ) {
        val normalizedValues: Set<String> = values.mapTo(linkedSetOf()) { it.normalized }

        fun contains(value: String): Boolean {
            val target = normalizeVisualEvidenceText(value)
            if (target.isBlank()) return false
            return normalizedValues.any { current ->
                current.contains(target) || target.contains(current)
            }
        }
    }

    private data class EvidenceValue(
        val original: String,
        val normalized: String,
    )

    private fun screenEvidence(snapshot: AgentScreenSnapshot): ScreenEvidence {
        val values = buildList {
            addAll(snapshot.texts)
            addAll(snapshot.clickableNodes.map { it.text })
            addAll(snapshot.inputNodes.map { it.text })
            addAll(snapshot.scrollableNodes.map { it.text })
        }
            .asSequence()
            .map { it.trim().take(MAX_EVIDENCE_TEXT_CHARS) }
            .filter { it.isNotBlank() }
            .distinct()
            .map { EvidenceValue(original = it, normalized = normalizeVisualEvidenceText(it)) }
            .filter { it.normalized.isNotBlank() }
            .take(MAX_SCREEN_EVIDENCE)
            .toList()
        return ScreenEvidence(values)
    }

    private fun inputNodeFingerprint(snapshot: AgentScreenSnapshot): String {
        return snapshot.inputNodes.joinToString("|") { node ->
            "${node.text.take(48)}#${node.bounds}"
        }
    }

    private fun matchEvidence(
        candidates: List<String>,
        screen: ScreenEvidence,
    ): List<String> {
        return candidates.filter(screen::contains).distinct().take(MAX_FEEDBACK_EVIDENCE)
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    private fun mergeEvidenceLists(vararg lists: List<String>): List<String> {
        return lists.flatMap { it }
            .map { it.trim().take(MAX_EVIDENCE_TEXT_CHARS) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_ACTION_EVIDENCE)
    }

    private fun JSONObject?.stringList(vararg keys: String): List<String> {
        val source = this ?: return emptyList()
        for (key in keys) {
            if (!source.has(key) || source.isNull(key)) continue
            return when (val raw = source.opt(key)) {
                is JSONArray -> buildList {
                    for (index in 0 until raw.length()) {
                        raw.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
                is String -> raw.split('|', ';', '；', '\n')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                else -> emptyList()
            }
        }
        return emptyList()
    }

    private fun JSONObject?.flexibleBoolean(key: String): Boolean? {
        val source = this ?: return null
        if (!source.has(key) || source.isNull(key)) return null
        return when (val raw = source.opt(key)) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> when (raw.trim().lowercase()) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> null
            }
            else -> null
        }
    }

    companion object {
        private const val DEFAULT_MILESTONE_ID = "work_surface"
        private const val MAX_CONSECUTIVE_REGRESSIONS = 2
        private const val MAX_CONFIRMED_FINGERPRINTS = 6
        private const val MAX_ACTION_EVIDENCE = 8
        private const val MAX_SCREEN_EVIDENCE = 80
        private const val MAX_FEEDBACK_EVIDENCE = 6
        private const val MAX_EVIDENCE_TEXT_CHARS = 120
    }
}

private fun normalizeVisualEvidenceText(value: String): String {
    return Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFKC)
        .replace(Regex("[\\s\\p{P}\\p{S}]+"), "")
}

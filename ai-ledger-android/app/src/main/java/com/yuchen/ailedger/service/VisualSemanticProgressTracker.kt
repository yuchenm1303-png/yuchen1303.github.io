package com.yuchen.ailedger.service

import java.text.Normalizer
import java.util.ArrayDeque
import kotlin.math.roundToInt

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
    val requiresReplan: Boolean,
    val reobserveRecommended: Boolean,
    val shouldPauseForUser: Boolean,
    val structuralRegression: Boolean,
    val reason: String,
    val taskMemory: VisualTaskMemory,
) {
    fun toFeedbackLine(step: CloudAgentStep): String {
        val intent = step.actionIntent
        val failedIds = taskMemory.failedHypotheses.takeLast(4).joinToString(",") { it.hypothesisId }
        val blocked = taskMemory.blockedActions.takeLast(4).joinToString(",") { it.actionCluster }
        val fields = buildString {
            append("|semanticStatus=").append(status.wireValue)
            append("|milestone=").append(milestoneId.take(80))
            append("|purpose=").append(intent.purpose.take(160))
            append("|hypothesisId=").append(intent.hypothesisId.take(100))
            append("|pageChanged=").append(pageChanged)
            append("|packageChanged=").append(packageChanged)
            append("|expectedMatched=").append(expectedEvidenceMatched.joinToString(",").take(180))
            append("|failureMatched=").append(failureEvidenceMatched.joinToString(",").take(180))
            append("|newEvidence=").append(newEvidence.joinToString(",").take(180))
            append("|failedHypotheses=").append(failedIds.take(220))
            append("|blockedActions=").append(blocked.take(220))
            append("|explorationBudgetRemaining=").append(explorationBudgetRemaining)
            append("|requiresStrategyChange=").append(requiresStrategyChange)
            append("|replanRequired=").append(requiresReplan)
            append("|reobserveRecommended=").append(reobserveRecommended)
            append("|lastConfirmedPage=").append(taskMemory.lastConfirmedPage?.id.orEmpty())
            append("|reason=").append(reason.take(220))
        }
        return when (status) {
            VisualSemanticProgressStatus.Advanced ->
                "visual_screen_changed:$actionSignature:screen=changed$fields"
            VisualSemanticProgressStatus.Regressed -> if (structuralRegression) {
                "visual_action_rejected:type=${step.type}|failureClass=structural_route|action=$actionSignature$fields"
            } else {
                "visual_local_retry:action=$actionSignature:count=$failedHypothesisCount$fields"
            }
            VisualSemanticProgressStatus.Stalled,
            VisualSemanticProgressStatus.Ambiguous ->
                "visual_local_retry:action=$actionSignature:count=$failedHypothesisCount$fields"
        }.take(MAX_FEEDBACK_LINE_CHARS)
    }

    companion object {
        private const val MAX_FEEDBACK_LINE_CHARS = 1_200
    }
}

/**
 * Evidence-driven execution harness. It never chooses an app or invents task semantics; it only
 * stores cloud intent, compares factual observations, enforces exploration budgets and reports
 * structured memory back to the cloud.
 */
class VisualSemanticProgressTracker(
    private val originalGoal: String = "",
    private val defaultExplorationBudgetPerMilestone: Int = VisualTaskContract.DEFAULT_EXPLORATION_BUDGET,
    private val legacyExplorationBudgetPerMilestone: Int = 1,
    private val maxConsecutiveAmbiguousTransitions: Int = 2,
) {
    private var taskContract: VisualTaskContract? = null
    private var currentMilestoneId: String = DEFAULT_MILESTONE_ID
    private val completedMilestoneIds = linkedSetOf<String>()
    private val failedHypotheses = linkedMapOf<String, VisualFailedHypothesis>()
    private val blockedActions = linkedMapOf<String, VisualBlockedAction>()
    private val explorationAttemptsByMilestone = mutableMapOf<String, Int>()
    private val explorationFailuresByMilestone = mutableMapOf<String, Int>()
    private val confirmedFacts = linkedSetOf<String>()
    private val confirmedFingerprints = ArrayDeque<String>()
    private var currentPage: VisualPageState? = null
    private var lastConfirmedPage: VisualPageState? = null
    private var lastProgressStatus: VisualSemanticProgressStatus? = null
    private var replanRequested: Boolean = false
    private var consecutiveAmbiguousTransitions: Int = 0
    private var consecutiveRegressions: Int = 0

    // One tracker processes observations sequentially. Retaining only the last immutable snapshot
    // analysis avoids duplicate fingerprint/evidence traversal without holding visual history.
    private var cachedSnapshot: AgentScreenSnapshot? = null
    private var cachedAnalysis: SnapshotAnalysis? = null

    fun updateTaskContract(contract: VisualTaskContract?, fallbackGoal: String = originalGoal) {
        if (contract == null) return
        val normalized = contract.copy(
            originalGoal = contract.originalGoal.ifBlank { fallbackGoal }.take(240),
            currentMilestoneId = contract.currentMilestoneId.ifBlank {
                contract.milestones.firstOrNull { !it.completed }?.id ?: currentMilestoneId
            },
            explorationBudgetPerMilestone = contract.explorationBudgetPerMilestone.coerceIn(1, 4),
        )
        taskContract = normalized
        completedMilestoneIds += normalized.completedMilestoneIds
        completedMilestoneIds += normalized.milestones.filter { it.completed }.map { it.id }
        ensureMilestone(normalized.currentMilestoneId)
    }

    fun onVerifiedSurface(snapshot: AgentScreenSnapshot) {
        currentMilestoneId = taskContract?.currentMilestoneId?.ifBlank { DEFAULT_MILESTONE_ID }
            ?: DEFAULT_MILESTONE_ID
        clearTransientTransitionState()
        val analysis = analyze(snapshot)
        currentPage = analysis.pageState
        lastConfirmedPage = analysis.pageState
        rememberConfirmed(analysis)
    }

    fun resetAfterUserTakeover(snapshot: AgentScreenSnapshot? = null) {
        clearTransientTransitionState()
        replanRequested = false
        snapshot?.let {
            val analysis = analyze(it)
            currentPage = analysis.pageState
            lastConfirmedPage = analysis.pageState
            rememberConfirmed(analysis)
        }
    }

    /** Returns a protocol/budget/hypothesis block reason before Android executes the action. */
    fun blockedHypothesisReason(step: CloudAgentStep, snapshot: AgentScreenSnapshot): String? {
        val intent = resolvedIntent(step)
        ensureMilestone(intent.milestoneId)
        val analysis = analyze(snapshot)
        currentPage = analysis.pageState
        val protocolReason = explorationProtocolViolation(step, intent)
        if (protocolReason != null) {
            rememberBlocked(step, analysis.pageState, intent, protocolReason)
            replanRequested = true
            return protocolReason
        }
        if (intent.exploratory && remainingExplorationBudget(intent) <= 0) {
            val reason = "The exploration budget for this milestone is exhausted. Request replanning instead of another exploratory action."
            rememberBlocked(step, analysis.pageState, intent, reason)
            replanRequested = true
            return reason
        }
        val key = hypothesisKey(step, analysis.pageState, intent)
        if (key in failedHypotheses) {
            val reason = "The same hypothesis already failed on this page and milestone. A nearby coordinate with the same purpose is the same blocked action cluster."
            rememberBlocked(step, analysis.pageState, intent, reason)
            replanRequested = true
            return reason
        }
        return null
    }

    fun evaluate(
        step: CloudAgentStep,
        before: AgentScreenSnapshot,
        after: AgentScreenSnapshot,
        verifiedTargetPackage: String,
    ): VisualSemanticProgressResult {
        val intent = resolvedIntent(step)
        ensureMilestone(intent.milestoneId)
        val actionSignature = VisualActionValidator.actionSignature(step)
        val beforeAnalysis = analyze(before)
        val afterAnalysis = analyze(after)
        val hypothesisKey = hypothesisKey(step, beforeAnalysis.pageState, intent)
        val pageChanged = beforeAnalysis.snapshotFingerprint != afterAnalysis.snapshotFingerprint
        val packageChanged = before.packageName != after.packageName
        val transientSurfaceChange = packageChanged && isTransientSystemSurface(after.packageName)
        val structuralRegression = !transientSurfaceChange && verifiedTargetPackage.isNotBlank() &&
            VisualSurfacePackagePolicy.isConfidentForeignPackage(after.packageName, verifiedTargetPackage)

        val beforeEvidence = beforeAnalysis.evidence
        val afterEvidence = afterAnalysis.evidence
        val expectedBefore = matchEvidence(intent.expectedEvidence, beforeEvidence)
        val expectedAfter = matchEvidence(intent.expectedEvidence, afterEvidence)
        val newlyMatchedExpected = expectedAfter.filterNot(expectedBefore::contains)
        val failureMatched = matchEvidence(intent.failureEvidence, afterEvidence)
        val milestoneEvidence = taskContract?.milestones
            ?.firstOrNull { it.id == currentMilestoneId }
            ?.successEvidence.orEmpty()
        val milestoneEvidenceAfter = matchEvidence(milestoneEvidence, afterEvidence)
        val newEvidence = afterEvidence.values
            .filterNot { it.normalized in beforeEvidence.normalizedValues }
            .map { it.original }
            .distinct()
            .take(MAX_FEEDBACK_EVIDENCE)

        val inputTextVisible = step.type == "input_text" &&
            step.text?.takeIf(String::isNotBlank)?.let(afterEvidence::contains) == true &&
            step.text?.takeIf(String::isNotBlank)?.let(beforeEvidence::contains) != true
        val inputSurfaceChanged = step.type == "input_text" &&
            beforeAnalysis.inputNodeFingerprint != afterAnalysis.inputNodeFingerprint
        val targetTransitionObserved = !intent.legacyMode && step.targetText
            ?.takeIf(String::isNotBlank)
            ?.let { target ->
                beforeEvidence.contains(target) && pageChanged &&
                    (!afterEvidence.contains(target) || newEvidence.isNotEmpty())
            } == true
        val returnedToConfirmedPage = step.type == "back" &&
            confirmedFingerprints.contains(afterAnalysis.snapshotFingerprint)
        val systemOnlyChange = transientSurfaceChange || isKeyboardOrPermissionOnlyChange(
            beforePackage = before.packageName,
            afterPackage = after.packageName,
            beforeInputFingerprint = beforeAnalysis.inputNodeFingerprint,
            afterInputFingerprint = afterAnalysis.inputNodeFingerprint,
            newEvidence = newEvidence,
        )

        val status = when {
            structuralRegression -> VisualSemanticProgressStatus.Regressed
            failureMatched.isNotEmpty() -> VisualSemanticProgressStatus.Regressed
            newlyMatchedExpected.isNotEmpty() -> VisualSemanticProgressStatus.Advanced
            !pageChanged -> VisualSemanticProgressStatus.Stalled
            systemOnlyChange -> VisualSemanticProgressStatus.Ambiguous
            returnedToConfirmedPage && intent.purpose.isNotBlank() -> VisualSemanticProgressStatus.Advanced
            inputTextVisible || inputSurfaceChanged -> VisualSemanticProgressStatus.Advanced
            targetTransitionObserved -> VisualSemanticProgressStatus.Advanced
            else -> VisualSemanticProgressStatus.Ambiguous
        }

        if (intent.exploratory) {
            explorationAttemptsByMilestone[currentMilestoneId] =
                explorationAttemptsByMilestone.getOrDefault(currentMilestoneId, 0) + 1
        }
        val failureReason = buildReason(
            status, intent, pageChanged, newlyMatchedExpected, failureMatched, newEvidence,
            structuralRegression, systemOnlyChange,
        )
        updateState(
            status = status,
            hypothesisKey = hypothesisKey,
            step = step,
            beforePage = beforeAnalysis.pageState,
            afterAnalysis = afterAnalysis,
            intent = intent,
            failureReason = failureReason,
        )

        if (milestoneEvidence.isNotEmpty() && milestoneEvidenceAfter.size == milestoneEvidence.distinct().size) {
            completedMilestoneIds += currentMilestoneId
        }
        val explorationFailures = explorationFailuresByMilestone.getOrDefault(currentMilestoneId, 0)
        val requiresReplan = structuralRegression || status == VisualSemanticProgressStatus.Regressed ||
            explorationFailures >= failureLimit(intent) ||
            consecutiveAmbiguousTransitions >= maxConsecutiveAmbiguousTransitions
        if (requiresReplan) replanRequested = true
        val reobserveRecommended = status == VisualSemanticProgressStatus.Ambiguous &&
            consecutiveAmbiguousTransitions < maxConsecutiveAmbiguousTransitions && !requiresReplan
        val shouldPauseForUser = (status == VisualSemanticProgressStatus.Regressed && !intent.reversible) ||
            consecutiveRegressions >= MAX_CONSECUTIVE_REGRESSIONS
        currentPage = afterAnalysis.pageState
        val memory = memorySnapshot(afterAnalysis)

        return VisualSemanticProgressResult(
            status = status,
            actionSignature = actionSignature,
            milestoneId = currentMilestoneId,
            pageChanged = pageChanged,
            packageChanged = packageChanged,
            expectedEvidenceMatched = newlyMatchedExpected,
            failureEvidenceMatched = failureMatched,
            newEvidence = newEvidence,
            failedHypothesisCount = failedHypotheses.values.count { it.milestoneId == currentMilestoneId },
            explorationBudgetRemaining = remainingExplorationBudget(intent),
            requiresStrategyChange = status != VisualSemanticProgressStatus.Advanced,
            requiresReplan = requiresReplan,
            reobserveRecommended = reobserveRecommended,
            shouldPauseForUser = shouldPauseForUser,
            structuralRegression = structuralRegression,
            reason = failureReason,
            taskMemory = memory,
        )
    }

    fun memorySnapshot(snapshot: AgentScreenSnapshot? = null): VisualTaskMemory {
        return memorySnapshot(snapshot?.let(::analyze))
    }

    private fun memorySnapshot(analysis: SnapshotAnalysis?): VisualTaskMemory {
        analysis?.let { currentPage = it.pageState }
        val legacyMode = taskContract == null
        val budget = budgetFor(legacyMode)
        val attempts = explorationAttemptsByMilestone.getOrDefault(currentMilestoneId, 0)
        return VisualTaskMemory(
            originalGoal = taskContract?.originalGoal?.ifBlank { originalGoal } ?: originalGoal,
            currentMilestoneId = currentMilestoneId,
            completedMilestoneIds = completedMilestoneIds.toList().takeLast(MAX_MEMORY_ITEMS),
            currentPage = currentPage,
            confirmedFacts = confirmedFacts.toList().takeLast(MAX_MEMORY_ITEMS),
            failedHypotheses = failedHypotheses.values.toList().takeLast(MAX_MEMORY_ITEMS),
            blockedActions = blockedActions.values.toList().takeLast(MAX_MEMORY_ITEMS),
            remainingExplorationBudget = (budget - attempts).coerceAtLeast(0),
            lastConfirmedPage = lastConfirmedPage,
            progressStatus = lastProgressStatus?.wireValue ?: "unknown",
            replanRequested = replanRequested,
            recoveryMode = replanRequested || lastProgressStatus in setOf(
                VisualSemanticProgressStatus.Regressed,
                VisualSemanticProgressStatus.Ambiguous,
            ),
            legacyMode = legacyMode,
            taskContract = taskContract,
        )
    }

    private fun updateState(
        status: VisualSemanticProgressStatus,
        hypothesisKey: String,
        step: CloudAgentStep,
        beforePage: VisualPageState,
        afterAnalysis: SnapshotAnalysis,
        intent: VisualActionIntent,
        failureReason: String,
    ) {
        lastProgressStatus = status
        when (status) {
            VisualSemanticProgressStatus.Advanced -> {
                consecutiveAmbiguousTransitions = 0
                consecutiveRegressions = 0
                replanRequested = false
                rememberConfirmed(afterAnalysis)
                lastConfirmedPage = afterAnalysis.pageState
                confirmedFacts += intent.expectedEvidence.filter(afterAnalysis.evidence::contains)
                confirmedFacts += afterAnalysis.evidence.values.map { it.original }.take(3)
            }
            VisualSemanticProgressStatus.Stalled -> {
                consecutiveAmbiguousTransitions = 0
                consecutiveRegressions = 0
                rememberFailure(hypothesisKey, step, beforePage, intent, failureReason)
                if (intent.exploratory) incrementExplorationFailure(intent)
            }
            VisualSemanticProgressStatus.Regressed -> {
                consecutiveAmbiguousTransitions = 0
                consecutiveRegressions += 1
                rememberFailure(hypothesisKey, step, beforePage, intent, failureReason)
                if (intent.exploratory) incrementExplorationFailure(intent)
            }
            VisualSemanticProgressStatus.Ambiguous -> {
                consecutiveAmbiguousTransitions += 1
                if (intent.exploratory || consecutiveAmbiguousTransitions >= maxConsecutiveAmbiguousTransitions) {
                    rememberFailure(hypothesisKey, step, beforePage, intent, failureReason)
                    if (intent.exploratory) incrementExplorationFailure(intent)
                }
            }
        }
    }

    private fun rememberFailure(
        key: String,
        step: CloudAgentStep,
        page: VisualPageState,
        intent: VisualActionIntent,
        reason: String,
    ) {
        val existing = failedHypotheses[key]
        val hypothesisId = stableHypothesisId(step, page, intent)
        failedHypotheses[key] = VisualFailedHypothesis(
            hypothesisId = hypothesisId,
            milestoneId = currentMilestoneId,
            pageStateId = page.id,
            actionSignature = VisualActionValidator.actionSignature(step),
            actionCluster = semanticActionCluster(step, intent),
            purpose = intent.purpose,
            failureReason = reason.take(260),
            count = (existing?.count ?: 0) + 1,
        )
        trimMap(failedHypotheses)
    }

    private fun rememberBlocked(
        step: CloudAgentStep,
        page: VisualPageState,
        intent: VisualActionIntent,
        reason: String,
    ) {
        val cluster = semanticActionCluster(step, intent)
        val key = listOf(currentMilestoneId, page.id, cluster).joinToString("::")
        blockedActions[key] = VisualBlockedAction(
            milestoneId = currentMilestoneId,
            pageStateId = page.id,
            actionCluster = cluster,
            hypothesisId = stableHypothesisId(step, page, intent),
            reason = reason.take(260),
        )
        trimMap(blockedActions)
    }

    private fun explorationProtocolViolation(step: CloudAgentStep, intent: VisualActionIntent): String? {
        if (!intent.exploratory || intent.legacyMode) return null
        if (intent.purpose.isBlank()) return "Exploratory actions require a non-empty purpose. Replan without executing this action."
        if (step.type in setOf("swipe", "scroll") && intent.expectedEvidence.isEmpty()) {
            return "Exploratory swipe/scroll requires expectedEvidence describing what it is searching for."
        }
        if (step.type == "wait" && intent.expectedEvidence.isEmpty()) {
            return "Exploratory wait requires expectedEvidence describing the loading result to wait for."
        }
        if (step.type == "back" && !looksLikeRecoveryPurpose(intent.purpose)) {
            return "Exploratory back requires an explicit recovery purpose."
        }
        return null
    }

    private fun looksLikeRecoveryPurpose(purpose: String): Boolean {
        val value = normalizeVisualEvidenceText(purpose)
        return RECOVERY_WORDS.any(value::contains)
    }

    private fun resolvedIntent(step: CloudAgentStep): VisualActionIntent {
        val raw = step.actionIntent
        val contractMilestone = taskContract?.currentMilestoneId.orEmpty()
        val milestoneId = raw.milestoneId.ifBlank { contractMilestone.ifBlank { DEFAULT_MILESTONE_ID } }
        val milestone = taskContract?.milestones?.firstOrNull { it.id == milestoneId }
        return raw.copy(
            milestoneId = milestoneId,
            purpose = raw.purpose.ifBlank {
                if (raw.legacyMode) step.reason.orEmpty().ifBlank { step.targetText.orEmpty() } else ""
            },
            expectedEvidence = (raw.expectedEvidence + milestone?.successEvidence.orEmpty())
                .distinct().take(MAX_ACTION_EVIDENCE),
            failureEvidence = (raw.failureEvidence + milestone?.failureEvidence.orEmpty())
                .distinct().take(MAX_ACTION_EVIDENCE),
            exploratory = raw.exploratory || (raw.legacyMode && step.type in LEGACY_EXPLORATORY_TYPES),
        )
    }

    private fun ensureMilestone(milestoneId: String) {
        val clean = milestoneId.trim().ifBlank { DEFAULT_MILESTONE_ID }
        if (clean == currentMilestoneId) return
        currentMilestoneId = clean
        clearTransientTransitionState()
        replanRequested = false
    }

    private fun clearTransientTransitionState() {
        confirmedFingerprints.clear()
        consecutiveAmbiguousTransitions = 0
        consecutiveRegressions = 0
    }

    private fun rememberConfirmed(analysis: SnapshotAnalysis) {
        val fingerprint = analysis.snapshotFingerprint
        if (fingerprint.isBlank()) return
        confirmedFingerprints.remove(fingerprint)
        confirmedFingerprints.addLast(fingerprint)
        while (confirmedFingerprints.size > MAX_CONFIRMED_FINGERPRINTS) confirmedFingerprints.removeFirst()
    }

    private fun hypothesisKey(
        step: CloudAgentStep,
        page: VisualPageState,
        intent: VisualActionIntent,
    ): String = listOf(currentMilestoneId, page.id, semanticActionCluster(step, intent)).joinToString("::")

    private fun stableHypothesisId(
        step: CloudAgentStep,
        page: VisualPageState,
        intent: VisualActionIntent,
    ): String {
        if (intent.hypothesisId.isNotBlank()) return intent.hypothesisId.take(120)
        val raw = hypothesisKey(step, page, intent)
        return "android-${Integer.toHexString(raw.hashCode())}"
    }

    private fun semanticActionCluster(step: CloudAgentStep, intent: VisualActionIntent): String {
        if (intent.hypothesisId.isNotBlank()) {
            return "hypothesis:${normalizeVisualEvidenceText(intent.hypothesisId)}"
        }
        val purpose = normalizeVisualEvidenceText(intent.purpose)
        if (purpose.isNotBlank()) {
            val target = normalizeVisualEvidenceText(step.targetText.orEmpty())
            return listOf(step.type, purpose.take(100), target.take(60), step.direction.orEmpty()).joinToString("|")
        }
        if (step.type == "tap_xy") {
            val x = step.x ?: return VisualActionValidator.actionSignature(step)
            val y = step.y ?: return VisualActionValidator.actionSignature(step)
            return "tap_xy|${(x / LEGACY_TAP_CLUSTER_PX).roundToInt()}|${(y / LEGACY_TAP_CLUSTER_PX).roundToInt()}"
        }
        return VisualActionValidator.actionSignature(step)
    }

    private fun incrementExplorationFailure(intent: VisualActionIntent) {
        val value = explorationFailuresByMilestone.getOrDefault(currentMilestoneId, 0) + 1
        explorationFailuresByMilestone[currentMilestoneId] = value
        if (value >= failureLimit(intent)) replanRequested = true
    }

    private fun remainingExplorationBudget(intent: VisualActionIntent): Int {
        val budget = budgetFor(intent.legacyMode)
        return (budget - explorationAttemptsByMilestone.getOrDefault(currentMilestoneId, 0)).coerceAtLeast(0)
    }

    private fun budgetFor(legacyMode: Boolean): Int {
        if (legacyMode) return legacyExplorationBudgetPerMilestone.coerceAtLeast(1)
        return (taskContract?.explorationBudgetPerMilestone ?: defaultExplorationBudgetPerMilestone).coerceIn(1, 4)
    }

    private fun failureLimit(intent: VisualActionIntent): Int = if (intent.legacyMode) 1 else 2

    private fun analyze(snapshot: AgentScreenSnapshot): SnapshotAnalysis {
        if (cachedSnapshot === snapshot) cachedAnalysis?.let { return it }
        val snapshotFingerprint = VisualActionValidator.snapshotFingerprint(snapshot)
        val completionFingerprint = VisualActionValidator.completionFingerprint(snapshot)
        val pageSummary = buildList {
            addAll(snapshot.texts.take(8))
            addAll(snapshot.clickableNodes.map { it.text }.filter(String::isNotBlank).take(6))
        }.distinct().joinToString(" | ").take(360)
        val evidenceValues = buildList {
            addAll(snapshot.texts)
            addAll(snapshot.clickableNodes.map { it.text })
            addAll(snapshot.inputNodes.map { it.text })
            addAll(snapshot.scrollableNodes.map { it.text })
        }.asSequence()
            .map { it.trim().take(MAX_EVIDENCE_TEXT_CHARS) }
            .filter(String::isNotBlank)
            .distinct()
            .map { EvidenceValue(it, normalizeVisualEvidenceText(it)) }
            .filter { it.normalized.isNotBlank() }
            .take(MAX_SCREEN_EVIDENCE)
            .toList()
        return SnapshotAnalysis(
            snapshotFingerprint = snapshotFingerprint,
            pageState = VisualPageState(
                id = "page-${Integer.toHexString(completionFingerprint.hashCode())}",
                packageName = snapshot.packageName.take(120),
                summary = pageSummary,
            ),
            evidence = ScreenEvidence(evidenceValues),
            inputNodeFingerprint = snapshot.inputNodes.joinToString("|") {
                "${it.text.take(48)}#${it.bounds}"
            },
        ).also {
            cachedSnapshot = snapshot
            cachedAnalysis = it
        }
    }

    private fun matchEvidence(candidates: List<String>, screen: ScreenEvidence): List<String> =
        candidates.filter(screen::contains).distinct().take(MAX_FEEDBACK_EVIDENCE)

    private fun isKeyboardOrPermissionOnlyChange(
        beforePackage: String,
        afterPackage: String,
        beforeInputFingerprint: String,
        afterInputFingerprint: String,
        newEvidence: List<String>,
    ): Boolean {
        if (isTransientSystemSurface(afterPackage)) return true
        val normalizedNew = newEvidence.joinToString("|") { normalizeVisualEvidenceText(it) }
        return beforePackage == afterPackage && beforeInputFingerprint != afterInputFingerprint &&
            (KEYBOARD_PERMISSION_WORDS.any(normalizedNew::contains) || newEvidence.isEmpty())
    }

    private fun isTransientSystemSurface(packageName: String): Boolean {
        val value = packageName.lowercase()
        return value.contains("systemui") || value.contains("permissioncontroller") ||
            value.contains("inputmethod") || value.contains("keyboard") || value.contains("packageinstaller")
    }

    private fun buildReason(
        status: VisualSemanticProgressStatus,
        intent: VisualActionIntent,
        pageChanged: Boolean,
        expectedMatched: List<String>,
        failureMatched: List<String>,
        newEvidence: List<String>,
        structuralRegression: Boolean,
        systemOnlyChange: Boolean,
    ): String = when (status) {
        VisualSemanticProgressStatus.Advanced -> when {
            expectedMatched.isNotEmpty() -> "Expected evidence appeared: ${expectedMatched.joinToString(", ")}"
            intent.purpose.isNotBlank() -> "Action-specific evidence advanced the current purpose: ${intent.purpose}"
            else -> "Action-specific observable progress was confirmed."
        }
        VisualSemanticProgressStatus.Stalled ->
            "The screen produced no task evidence. The same page, milestone and hypothesis are now blocked."
        VisualSemanticProgressStatus.Regressed -> when {
            structuralRegression -> "The verified target package was lost. Request route recovery before another visual action."
            failureMatched.isNotEmpty() -> "Failure evidence appeared: ${failureMatched.joinToString(", ")}"
            !intent.reversible -> "The action regressed and was not declared reversible. User review is required."
            else -> "The action moved away from the expected milestone state."
        }
        VisualSemanticProgressStatus.Ambiguous -> buildString {
            if (systemOnlyChange) append("Only a keyboard, permission or system surface transition was observed")
            else append("The screen changed without proving the current milestone")
            if (intent.expectedEvidence.isEmpty()) append("; no expectedEvidence was supplied")
            if (newEvidence.isNotEmpty()) append("; new evidence=${newEvidence.joinToString(", ")}")
            if (!pageChanged) append("; page remained unchanged")
            append(". Reobserve once, then replan instead of executing another exploratory action.")
        }
    }

    private fun <K, V> trimMap(map: LinkedHashMap<K, V>) {
        while (map.size > MAX_MEMORY_ITEMS) map.remove(map.keys.first())
    }

    private data class SnapshotAnalysis(
        val snapshotFingerprint: String,
        val pageState: VisualPageState,
        val evidence: ScreenEvidence,
        val inputNodeFingerprint: String,
    )

    private data class ScreenEvidence(val values: List<EvidenceValue>) {
        val normalizedValues: Set<String> = values.mapTo(linkedSetOf()) { it.normalized }
        fun contains(value: String): Boolean {
            val target = normalizeVisualEvidenceText(value)
            if (target.isBlank()) return false
            return normalizedValues.any { it.contains(target) || target.contains(it) }
        }
    }

    private data class EvidenceValue(val original: String, val normalized: String)

    companion object {
        private const val DEFAULT_MILESTONE_ID = "work_surface"
        private const val MAX_CONSECUTIVE_REGRESSIONS = 2
        private const val MAX_CONFIRMED_FINGERPRINTS = 6
        private const val MAX_ACTION_EVIDENCE = 16
        private const val MAX_SCREEN_EVIDENCE = 80
        private const val MAX_FEEDBACK_EVIDENCE = 6
        private const val MAX_EVIDENCE_TEXT_CHARS = 120
        private const val MAX_MEMORY_ITEMS = 12
        private const val LEGACY_TAP_CLUSTER_PX = 160f
        private val LEGACY_EXPLORATORY_TYPES = setOf("swipe", "scroll", "wait", "back")
        private val RECOVERY_WORDS = listOf(
            "恢复", "返回", "回到", "撤销", "纠正", "recover", "restore", "return", "undo", "correct",
        )
        private val KEYBOARD_PERMISSION_WORDS = listOf(
            "允许", "权限", "键盘", "输入法", "permission", "allow", "keyboard", "ime",
        )
    }
}

private fun normalizeVisualEvidenceText(value: String): String =
    Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFKC)
        .replace(Regex("[\\s\\p{P}\\p{S}]+"), "")

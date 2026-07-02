package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.TargetSelectorBundle
import com.yuchen.ailedger.model.TargetSelectorCandidate
import com.yuchen.ailedger.model.WorkflowActionSpec
import com.yuchen.ailedger.model.WorkflowActionType
import com.yuchen.ailedger.model.WorkflowAppScope
import com.yuchen.ailedger.model.WorkflowConfirmationPolicy
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.model.WorkflowExecutionMode
import com.yuchen.ailedger.model.WorkflowMilestone
import com.yuchen.ailedger.model.WorkflowRecoveryPolicy
import com.yuchen.ailedger.model.WorkflowRetryPolicy
import com.yuchen.ailedger.model.WorkflowRiskLevel
import com.yuchen.ailedger.model.WorkflowRiskPolicy
import com.yuchen.ailedger.model.WorkflowSelectorKind
import com.yuchen.ailedger.model.WorkflowStateCheck
import com.yuchen.ailedger.model.WorkflowStateCheckType
import com.yuchen.ailedger.model.WorkflowStep
import com.yuchen.ailedger.model.WorkflowVariableDefinition
import com.yuchen.ailedger.model.WorkflowVariableType
import java.util.UUID
import org.json.JSONArray

class OperationWorkflowRepository private constructor(context: Context) {
    private val dao = OperationWorkflowDatabase.get(context).workflowDao()

    suspend fun loadDrafts(): List<LearnedWorkflowDraft> {
        return dao.loadActiveWorkflows().map { loadGraph(it) }
    }

    suspend fun loadDraft(workflowId: String): LearnedWorkflowDraft? {
        return dao.loadWorkflow(workflowId)?.let { loadGraph(it) }
    }

    suspend fun loadDemonstration(demonstrationId: String): OperationDemonstrationEntity? {
        return dao.loadDemonstration(demonstrationId)
    }

    suspend fun saveIntent(draft: LearnedWorkflowDraft) {
        val workflow = draft.toWorkflowEntity()
        val packages = draft.appScope.normalizedPackages
        val appScopes = packages.mapIndexed { index, packageName ->
            OperationWorkflowAppScopeEntity(
                workflowId = draft.id,
                packageName = packageName,
                displayName = draft.appScope.displayNames.getOrNull(index).orEmpty(),
                allowSystemSurfaces = draft.appScope.allowSystemSurfaces,
            )
        }
        dao.saveIntent(workflow, appScopes)
    }

    suspend fun saveCompiledDraft(
        draft: LearnedWorkflowDraft,
        demonstrationId: String,
    ) {
        val variables = draft.variables.map { variable ->
            OperationWorkflowVariableEntity(
                id = "${draft.id}-variable-${variable.key}",
                workflowId = draft.id,
                variableKey = variable.key,
                label = variable.label,
                type = variable.type.name,
                required = variable.required,
                sensitive = variable.sensitive,
                persistValue = variable.persistValue,
                allowedValuesJson = JSONArray(variable.allowedValues).toString(),
                description = variable.description,
            )
        }
        val milestones = draft.milestones.map { milestone ->
            OperationWorkflowMilestoneEntity(
                id = milestone.id,
                workflowId = draft.id,
                title = milestone.title,
                sortOrder = milestone.order,
            )
        }
        val steps = draft.steps.map { step ->
            OperationWorkflowStepEntity(
                id = step.id,
                workflowId = draft.id,
                milestoneId = step.milestoneId,
                sortOrder = step.order,
                title = step.title,
                actionType = step.action.type.name,
                variableKey = step.action.variableKey,
                fixedArgument = step.action.fixedArgument,
                selectorMinimumScore = step.target?.minimumScore,
                coordinateFallbackAllowed = step.target?.coordinateFallbackAllowed == true,
                retryMaxAttempts = step.retryPolicy.maxAttempts,
                retryDelayMs = step.retryPolicy.delayMs,
                riskLevel = step.riskLevel.name,
                confirmationPolicy = step.confirmationPolicy.name,
            )
        }
        val selectors = draft.steps.flatMap { step ->
            step.target?.candidates.orEmpty().mapIndexed { index, selector ->
                OperationWorkflowSelectorEntity(
                    id = "${step.id}-selector-$index",
                    stepId = step.id,
                    kind = selector.kind.name,
                    value = selector.value,
                    weight = selector.weight,
                    packageName = selector.packageName,
                    role = selector.role,
                    ancestorHint = selector.ancestorHint,
                )
            }
        }
        val checks = buildList {
            draft.completionChecks.forEach { check ->
                add(check.toEntity(draft.id, OWNER_WORKFLOW, draft.id, PHASE_COMPLETION))
            }
            draft.milestones.forEach { milestone ->
                milestone.completionChecks.forEach { check ->
                    add(check.toEntity(draft.id, OWNER_MILESTONE, milestone.id, PHASE_COMPLETION))
                }
            }
            draft.steps.forEach { step ->
                step.preconditions.forEach { check ->
                    add(check.toEntity(draft.id, OWNER_STEP, step.id, PHASE_PRE))
                }
                step.postconditions.forEach { check ->
                    add(check.toEntity(draft.id, OWNER_STEP, step.id, PHASE_POST))
                }
            }
        }

        dao.saveCompiledGraph(
            workflow = draft.toWorkflowEntity(),
            variables = variables,
            milestones = milestones,
            steps = steps,
            selectors = selectors,
            stateChecks = checks,
            demonstrationId = demonstrationId,
        )
    }

    suspend fun approveDraft(
        draft: LearnedWorkflowDraft,
        approvedAtMillis: Long = System.currentTimeMillis(),
    ): Int {
        return dao.approveWorkflow(
            workflowId = draft.id,
            versionId = UUID.randomUUID().toString(),
            snapshotJson = OperationWorkflowJsonCodec.encode(
                draft.copy(
                    status = WorkflowDraftStatus.Approved,
                    updatedAtMillis = approvedAtMillis,
                ),
            ),
            changeSummary = "由演示轨迹生成并经用户审核",
            approvedAtMillis = approvedAtMillis,
        )
    }

    suspend fun resetForNewDemonstration(
        workflowId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        dao.resetCompiledGraph(workflowId, nowMillis)
    }

    suspend fun beginDemonstration(
        demonstrationId: String,
        workflowId: String,
        encryptedTracePath: String,
        createdAtMillis: Long,
    ) {
        dao.upsertDemonstration(
            OperationDemonstrationEntity(
                id = demonstrationId,
                workflowId = workflowId,
                status = "recording",
                encryptedTracePath = encryptedTracePath,
                redactionStatus = "active",
                createdAtMillis = createdAtMillis,
                completedAtMillis = null,
            ),
        )
    }

    suspend fun finishDemonstration(
        demonstrationId: String,
        workflowId: String,
        status: String,
        redactionStatus: String,
        workflowStatus: WorkflowDraftStatus,
        completedAtMillis: Long,
    ) {
        dao.finishDemonstrationAndUpdateWorkflow(
            demonstrationId = demonstrationId,
            workflowId = workflowId,
            demonstrationStatus = status,
            redactionStatus = redactionStatus,
            workflowStatus = workflowStatus.name,
            sourceDemonstrationId = demonstrationId.takeIf { status == "captured" },
            completedAtMillis = completedAtMillis,
        )
    }

    suspend fun sealInterruptedDemonstrations(nowMillis: Long = System.currentTimeMillis()) {
        dao.sealInterruptedDemonstrations(nowMillis)
    }

    suspend fun deleteDraft(draftId: String) {
        dao.deleteWorkflow(draftId)
    }

    private suspend fun loadGraph(record: OperationWorkflowWithScopes): LearnedWorkflowDraft {
        val workflowId = record.workflow.id
        val variables = dao.loadVariables(workflowId).map { row ->
            WorkflowVariableDefinition(
                key = row.variableKey,
                label = row.label,
                type = enumValueOrDefault(row.type, WorkflowVariableType.Text),
                required = row.required,
                sensitive = row.sensitive,
                persistValue = row.persistValue,
                allowedValues = row.allowedValuesJson.toStringList(),
                description = row.description,
            )
        }
        val milestoneRows = dao.loadMilestones(workflowId)
        val stepRows = dao.loadSteps(workflowId)
        val selectorRows = dao.loadSelectors(workflowId).groupBy(OperationWorkflowSelectorEntity::stepId)
        val checkRows = dao.loadStateChecks(workflowId)
        val milestoneChecks = checkRows
            .filter { it.ownerType == OWNER_MILESTONE && it.phase == PHASE_COMPLETION }
            .groupBy(OperationWorkflowStateCheckEntity::ownerId)
        val stepPreconditions = checkRows
            .filter { it.ownerType == OWNER_STEP && it.phase == PHASE_PRE }
            .groupBy(OperationWorkflowStateCheckEntity::ownerId)
        val stepPostconditions = checkRows
            .filter { it.ownerType == OWNER_STEP && it.phase == PHASE_POST }
            .groupBy(OperationWorkflowStateCheckEntity::ownerId)
        val milestones = milestoneRows.map { row ->
            WorkflowMilestone(
                id = row.id,
                title = row.title,
                order = row.sortOrder,
                completionChecks = milestoneChecks[row.id].orEmpty().map { it.toModel() },
            )
        }
        val steps = stepRows.map { row ->
            val candidates = selectorRows[row.id].orEmpty().map { selector ->
                TargetSelectorCandidate(
                    kind = enumValueOrDefault(selector.kind, WorkflowSelectorKind.RecordedBounds),
                    value = selector.value,
                    weight = selector.weight,
                    packageName = selector.packageName,
                    role = selector.role,
                    ancestorHint = selector.ancestorHint,
                )
            }
            WorkflowStep(
                id = row.id,
                order = row.sortOrder,
                title = row.title,
                milestoneId = row.milestoneId,
                action = WorkflowActionSpec(
                    type = enumValueOrDefault(row.actionType, WorkflowActionType.RequestUserConfirmation),
                    variableKey = row.variableKey,
                    fixedArgument = row.fixedArgument,
                ),
                target = row.selectorMinimumScore?.let { minimumScore ->
                    TargetSelectorBundle(
                        candidates = candidates,
                        minimumScore = minimumScore,
                        coordinateFallbackAllowed = row.coordinateFallbackAllowed,
                    )
                },
                preconditions = stepPreconditions[row.id].orEmpty().map { it.toModel() },
                postconditions = stepPostconditions[row.id].orEmpty().map { it.toModel() },
                retryPolicy = WorkflowRetryPolicy(
                    maxAttempts = row.retryMaxAttempts,
                    delayMs = row.retryDelayMs,
                ),
                riskLevel = enumValueOrDefault(row.riskLevel, WorkflowRiskLevel.Low),
                confirmationPolicy = enumValueOrDefault(
                    row.confirmationPolicy,
                    WorkflowConfirmationPolicy.OnRisk,
                ),
            )
        }
        val maximumRisk = steps.maxByOrNull { it.riskLevel.ordinal }?.riskLevel
            ?: WorkflowRiskLevel.Medium
        val workflow = record.workflow
        return LearnedWorkflowDraft(
            id = workflow.id,
            title = workflow.title,
            goal = workflow.goal,
            appScope = WorkflowAppScope(
                packageNames = record.appScopes.map { it.packageName },
                displayNames = record.appScopes.map { it.displayName },
                allowSystemSurfaces = record.appScopes.any { it.allowSystemSurfaces },
            ),
            variables = variables,
            milestones = milestones,
            steps = steps,
            completionChecks = checkRows
                .filter { it.ownerType == OWNER_WORKFLOW && it.phase == PHASE_COMPLETION }
                .map { it.toModel() },
            riskPolicy = WorkflowRiskPolicy(maximumAllowedRisk = maximumRisk),
            recoveryPolicy = WorkflowRecoveryPolicy(),
            executionMode = enumValueOrDefault(workflow.executionMode, WorkflowExecutionMode.Deterministic),
            status = enumValueOrDefault(workflow.status, WorkflowDraftStatus.Intent),
            createdAtMillis = workflow.createdAtMillis,
            updatedAtMillis = workflow.updatedAtMillis,
            sourceDemonstrationId = workflow.sourceDemonstrationId,
        )
    }

    private fun LearnedWorkflowDraft.toWorkflowEntity(): OperationWorkflowEntity = OperationWorkflowEntity(
        id = id,
        title = title,
        goal = goal,
        executionMode = executionMode.name,
        status = status.name,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        sourceDemonstrationId = sourceDemonstrationId,
    )

    private fun WorkflowStateCheck.toEntity(
        workflowId: String,
        ownerType: String,
        ownerId: String,
        phase: String,
    ): OperationWorkflowStateCheckEntity = OperationWorkflowStateCheckEntity(
        id = listOf(
            workflowId,
            ownerType,
            ownerId,
            phase,
            id.substringAfterLast(CHECK_ID_SEPARATOR),
        ).joinToString(CHECK_ID_SEPARATOR),
        workflowId = workflowId,
        ownerType = ownerType,
        ownerId = ownerId,
        phase = phase,
        type = type.name,
        expectedValue = expectedValue,
        packageName = packageName,
        timeoutMs = timeoutMs,
        required = required,
    )

    private fun OperationWorkflowStateCheckEntity.toModel(): WorkflowStateCheck = WorkflowStateCheck(
        id = id.substringAfterLast(CHECK_ID_SEPARATOR),
        type = enumValueOrDefault(type, WorkflowStateCheckType.UserConfirmed),
        expectedValue = expectedValue,
        packageName = packageName,
        timeoutMs = timeoutMs,
        required = required,
    )

    private fun String.toStringList(): List<String> = runCatching {
        val source = JSONArray(this)
        buildList {
            for (index in 0 until source.length()) {
                source.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        value: String,
        fallback: T,
    ): T = runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)

    companion object {
        private const val OWNER_WORKFLOW = "workflow"
        private const val OWNER_MILESTONE = "milestone"
        private const val OWNER_STEP = "step"
        private const val PHASE_PRE = "pre"
        private const val PHASE_POST = "post"
        private const val PHASE_COMPLETION = "completion"
        private const val CHECK_ID_SEPARATOR = "|"

        @Volatile
        private var instance: OperationWorkflowRepository? = null

        fun get(context: Context): OperationWorkflowRepository {
            return instance ?: synchronized(this) {
                instance ?: OperationWorkflowRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

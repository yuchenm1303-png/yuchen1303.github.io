package com.yuchen.ailedger.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "operation_workflows")
data class OperationWorkflowEntity(
    @PrimaryKey val id: String,
    val title: String,
    val goal: String,
    val executionMode: String,
    val status: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val sourceDemonstrationId: String?,
    @ColumnInfo(defaultValue = "'{}'") val riskPolicyJson: String = "{}",
    @ColumnInfo(defaultValue = "'{}'") val recoveryPolicyJson: String = "{}",
)

@Entity(
    tableName = "operation_workflow_app_scopes",
    primaryKeys = ["workflowId", "packageName"],
    foreignKeys = [
        ForeignKey(
            entity = OperationWorkflowEntity::class,
            parentColumns = ["id"],
            childColumns = ["workflowId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workflowId")],
)
data class OperationWorkflowAppScopeEntity(
    val workflowId: String,
    val packageName: String,
    val displayName: String,
    val allowSystemSurfaces: Boolean,
)

@Entity(
    tableName = "operation_workflow_variables",
    foreignKeys = [
        ForeignKey(
            entity = OperationWorkflowEntity::class,
            parentColumns = ["id"],
            childColumns = ["workflowId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workflowId"), Index(value = ["workflowId", "variableKey"], unique = true)],
)
data class OperationWorkflowVariableEntity(
    @PrimaryKey val id: String,
    val workflowId: String,
    val variableKey: String,
    val label: String,
    val type: String,
    val required: Boolean,
    val sensitive: Boolean,
    val persistValue: Boolean,
    val allowedValuesJson: String,
    val description: String,
)

@Entity(
    tableName = "operation_workflow_milestones",
    foreignKeys = [
        ForeignKey(
            entity = OperationWorkflowEntity::class,
            parentColumns = ["id"],
            childColumns = ["workflowId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workflowId"), Index(value = ["workflowId", "sortOrder"], unique = true)],
)
data class OperationWorkflowMilestoneEntity(
    @PrimaryKey val id: String,
    val workflowId: String,
    val title: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "operation_workflow_steps",
    foreignKeys = [
        ForeignKey(
            entity = OperationWorkflowEntity::class,
            parentColumns = ["id"],
            childColumns = ["workflowId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = OperationWorkflowMilestoneEntity::class,
            parentColumns = ["id"],
            childColumns = ["milestoneId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("workflowId"),
        Index("milestoneId"),
        Index(value = ["workflowId", "sortOrder"], unique = true),
    ],
)
data class OperationWorkflowStepEntity(
    @PrimaryKey val id: String,
    val workflowId: String,
    val milestoneId: String,
    val sortOrder: Int,
    val title: String,
    val actionType: String,
    val variableKey: String?,
    val fixedArgument: String?,
    val selectorMinimumScore: Float?,
    val coordinateFallbackAllowed: Boolean,
    val retryMaxAttempts: Int,
    val retryDelayMs: Long,
    val riskLevel: String,
    val confirmationPolicy: String,
)

@Entity(
    tableName = "operation_workflow_selectors",
    foreignKeys = [
        ForeignKey(
            entity = OperationWorkflowStepEntity::class,
            parentColumns = ["id"],
            childColumns = ["stepId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("stepId")],
)
data class OperationWorkflowSelectorEntity(
    @PrimaryKey val id: String,
    val stepId: String,
    val kind: String,
    val value: String,
    val weight: Float,
    val packageName: String?,
    val role: String?,
    val ancestorHint: String?,
)

@Entity(
    tableName = "operation_workflow_state_checks",
    foreignKeys = [
        ForeignKey(
            entity = OperationWorkflowEntity::class,
            parentColumns = ["id"],
            childColumns = ["workflowId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workflowId"), Index(value = ["ownerType", "ownerId"])],
)
data class OperationWorkflowStateCheckEntity(
    @PrimaryKey val id: String,
    val workflowId: String,
    val ownerType: String,
    val ownerId: String,
    val phase: String,
    val type: String,
    val expectedValue: String,
    val packageName: String?,
    val timeoutMs: Long,
    val required: Boolean,
)

@Entity(
    tableName = "operation_workflow_versions",
    foreignKeys = [
        ForeignKey(
            entity = OperationWorkflowEntity::class,
            parentColumns = ["id"],
            childColumns = ["workflowId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workflowId"), Index(value = ["workflowId", "versionNumber"], unique = true)],
)
data class OperationWorkflowVersionEntity(
    @PrimaryKey val id: String,
    val workflowId: String,
    val versionNumber: Int,
    val snapshotJson: String,
    val approvedAtMillis: Long,
    val verifiedAtMillis: Long?,
    val changeSummary: String,
)

@Entity(
    tableName = "operation_demonstrations",
    foreignKeys = [
        ForeignKey(
            entity = OperationWorkflowEntity::class,
            parentColumns = ["id"],
            childColumns = ["workflowId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workflowId")],
)
data class OperationDemonstrationEntity(
    @PrimaryKey val id: String,
    val workflowId: String,
    val status: String,
    val encryptedTracePath: String?,
    val redactionStatus: String,
    val createdAtMillis: Long,
    val completedAtMillis: Long?,
)

@Entity(
    tableName = "operation_workflow_runs",
    foreignKeys = [
        ForeignKey(
            entity = OperationWorkflowEntity::class,
            parentColumns = ["id"],
            childColumns = ["workflowId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = OperationWorkflowVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["versionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("workflowId"), Index("versionId")],
)
data class OperationWorkflowRunEntity(
    @PrimaryKey val id: String,
    val workflowId: String,
    val versionId: String?,
    val status: String,
    val startedAtMillis: Long,
    val completedAtMillis: Long?,
    val resultJson: String?,
)

data class OperationWorkflowWithScopes(
    @Embedded val workflow: OperationWorkflowEntity,
    @Relation(parentColumn = "id", entityColumn = "workflowId")
    val appScopes: List<OperationWorkflowAppScopeEntity>,
)

@Dao
abstract class OperationWorkflowDao {
    @Transaction
    @Query(
        """
        SELECT * FROM operation_workflows
        WHERE status != 'Archived'
        ORDER BY updatedAtMillis DESC
        """,
    )
    abstract suspend fun loadActiveWorkflows(): List<OperationWorkflowWithScopes>

    @Transaction
    @Query("SELECT * FROM operation_workflows WHERE id = :workflowId LIMIT 1")
    abstract suspend fun loadWorkflow(workflowId: String): OperationWorkflowWithScopes?

    @Query("SELECT * FROM operation_workflow_variables WHERE workflowId = :workflowId ORDER BY variableKey")
    abstract suspend fun loadVariables(workflowId: String): List<OperationWorkflowVariableEntity>

    @Query("SELECT * FROM operation_workflow_milestones WHERE workflowId = :workflowId ORDER BY sortOrder")
    abstract suspend fun loadMilestones(workflowId: String): List<OperationWorkflowMilestoneEntity>

    @Query("SELECT * FROM operation_workflow_steps WHERE workflowId = :workflowId ORDER BY sortOrder")
    abstract suspend fun loadSteps(workflowId: String): List<OperationWorkflowStepEntity>

    @Query(
        """
        SELECT selector.* FROM operation_workflow_selectors AS selector
        INNER JOIN operation_workflow_steps AS step ON selector.stepId = step.id
        WHERE step.workflowId = :workflowId
        ORDER BY step.sortOrder, selector.weight DESC
        """,
    )
    abstract suspend fun loadSelectors(workflowId: String): List<OperationWorkflowSelectorEntity>

    @Query("SELECT * FROM operation_workflow_state_checks WHERE workflowId = :workflowId ORDER BY id")
    abstract suspend fun loadStateChecks(workflowId: String): List<OperationWorkflowStateCheckEntity>

    @Query("SELECT * FROM operation_demonstrations WHERE id = :demonstrationId LIMIT 1")
    abstract suspend fun loadDemonstration(demonstrationId: String): OperationDemonstrationEntity?

    @Query(
        """
        SELECT * FROM operation_workflow_versions
        WHERE workflowId = :workflowId
        ORDER BY versionNumber DESC
        LIMIT 1
        """,
    )
    abstract suspend fun loadLatestVersion(workflowId: String): OperationWorkflowVersionEntity?

    @Query(
        """
        SELECT * FROM operation_workflow_versions
        WHERE workflowId = :workflowId AND versionNumber = :versionNumber
        LIMIT 1
        """,
    )
    abstract suspend fun loadVersion(
        workflowId: String,
        versionNumber: Int,
    ): OperationWorkflowVersionEntity?

    @Upsert
    abstract suspend fun upsertWorkflow(entity: OperationWorkflowEntity)

    @Upsert
    abstract suspend fun upsertAppScopes(entities: List<OperationWorkflowAppScopeEntity>)

    @Upsert
    abstract suspend fun upsertVariables(entities: List<OperationWorkflowVariableEntity>)

    @Upsert
    abstract suspend fun upsertMilestones(entities: List<OperationWorkflowMilestoneEntity>)

    @Upsert
    abstract suspend fun upsertSteps(entities: List<OperationWorkflowStepEntity>)

    @Upsert
    abstract suspend fun upsertSelectors(entities: List<OperationWorkflowSelectorEntity>)

    @Upsert
    abstract suspend fun upsertStateChecks(entities: List<OperationWorkflowStateCheckEntity>)

    @Upsert
    abstract suspend fun upsertDemonstration(entity: OperationDemonstrationEntity)

    @Upsert
    abstract suspend fun upsertVersion(entity: OperationWorkflowVersionEntity)

    @Query("DELETE FROM operation_workflow_app_scopes WHERE workflowId = :workflowId")
    abstract suspend fun deleteAppScopes(workflowId: String)

    @Query("DELETE FROM operation_workflow_selectors WHERE stepId IN (SELECT id FROM operation_workflow_steps WHERE workflowId = :workflowId)")
    abstract suspend fun deleteSelectors(workflowId: String)

    @Query("DELETE FROM operation_workflow_state_checks WHERE workflowId = :workflowId")
    abstract suspend fun deleteStateChecks(workflowId: String)

    @Query("DELETE FROM operation_workflow_steps WHERE workflowId = :workflowId")
    abstract suspend fun deleteSteps(workflowId: String)

    @Query("DELETE FROM operation_workflow_milestones WHERE workflowId = :workflowId")
    abstract suspend fun deleteMilestones(workflowId: String)

    @Query("DELETE FROM operation_workflow_variables WHERE workflowId = :workflowId")
    abstract suspend fun deleteVariables(workflowId: String)

    @Query("DELETE FROM operation_workflows WHERE id = :workflowId")
    abstract suspend fun deleteWorkflow(workflowId: String)

    @Query(
        """
        UPDATE operation_demonstrations
        SET status = :status,
            redactionStatus = :redactionStatus,
            completedAtMillis = :completedAtMillis
        WHERE id = :demonstrationId
        """,
    )
    abstract suspend fun finishDemonstration(
        demonstrationId: String,
        status: String,
        redactionStatus: String,
        completedAtMillis: Long,
    )

    @Query(
        """
        UPDATE operation_demonstrations
        SET status = 'compiled',
            encryptedTracePath = NULL,
            redactionStatus = 'compiled'
        WHERE id = :demonstrationId
        """,
    )
    abstract suspend fun markDemonstrationCompiled(demonstrationId: String)

    @Query(
        """
        UPDATE operation_workflows
        SET status = :status,
            sourceDemonstrationId = :demonstrationId,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :workflowId
        """,
    )
    abstract suspend fun updateWorkflowAfterDemonstration(
        workflowId: String,
        status: String,
        demonstrationId: String?,
        updatedAtMillis: Long,
    )

    @Query(
        """
        UPDATE operation_workflows
        SET status = :status,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :workflowId
        """,
    )
    abstract suspend fun updateWorkflowStatus(
        workflowId: String,
        status: String,
        updatedAtMillis: Long,
    )

    @Query(
        """
        UPDATE operation_demonstrations
        SET status = 'interrupted',
            redactionStatus = 'sealed_after_interruption',
            completedAtMillis = :completedAtMillis
        WHERE status = 'recording'
        """,
    )
    abstract suspend fun sealInterruptedDemonstrations(completedAtMillis: Long)

    @Query("SELECT COALESCE(MAX(versionNumber), 0) FROM operation_workflow_versions WHERE workflowId = :workflowId")
    abstract suspend fun latestVersionNumber(workflowId: String): Int

    @Transaction
    open suspend fun saveIntent(
        workflow: OperationWorkflowEntity,
        appScopes: List<OperationWorkflowAppScopeEntity>,
    ) {
        upsertWorkflow(workflow)
        deleteAppScopes(workflow.id)
        if (appScopes.isNotEmpty()) upsertAppScopes(appScopes)
    }

    @Transaction
    open suspend fun finishDemonstrationAndUpdateWorkflow(
        demonstrationId: String,
        workflowId: String,
        demonstrationStatus: String,
        redactionStatus: String,
        workflowStatus: String,
        sourceDemonstrationId: String?,
        completedAtMillis: Long,
    ) {
        finishDemonstration(
            demonstrationId = demonstrationId,
            status = demonstrationStatus,
            redactionStatus = redactionStatus,
            completedAtMillis = completedAtMillis,
        )
        updateWorkflowAfterDemonstration(
            workflowId = workflowId,
            status = workflowStatus,
            demonstrationId = sourceDemonstrationId,
            updatedAtMillis = completedAtMillis,
        )
    }

    @Transaction
    open suspend fun saveCompiledGraph(
        workflow: OperationWorkflowEntity,
        variables: List<OperationWorkflowVariableEntity>,
        milestones: List<OperationWorkflowMilestoneEntity>,
        steps: List<OperationWorkflowStepEntity>,
        selectors: List<OperationWorkflowSelectorEntity>,
        stateChecks: List<OperationWorkflowStateCheckEntity>,
        demonstrationId: String,
    ) {
        deleteSelectors(workflow.id)
        deleteStateChecks(workflow.id)
        deleteSteps(workflow.id)
        deleteMilestones(workflow.id)
        deleteVariables(workflow.id)
        upsertWorkflow(workflow)
        if (variables.isNotEmpty()) upsertVariables(variables)
        if (milestones.isNotEmpty()) upsertMilestones(milestones)
        if (steps.isNotEmpty()) upsertSteps(steps)
        if (selectors.isNotEmpty()) upsertSelectors(selectors)
        if (stateChecks.isNotEmpty()) upsertStateChecks(stateChecks)
        markDemonstrationCompiled(demonstrationId)
    }

    @Transaction
    open suspend fun saveSyncedCloudVisualGraph(
        workflow: OperationWorkflowEntity,
        appScopes: List<OperationWorkflowAppScopeEntity>,
        variables: List<OperationWorkflowVariableEntity>,
    ) {
        deleteSelectors(workflow.id)
        deleteStateChecks(workflow.id)
        deleteSteps(workflow.id)
        deleteMilestones(workflow.id)
        deleteVariables(workflow.id)
        deleteAppScopes(workflow.id)
        upsertWorkflow(workflow)
        if (appScopes.isNotEmpty()) upsertAppScopes(appScopes)
        if (variables.isNotEmpty()) upsertVariables(variables)
    }

    @Transaction
    open suspend fun approveWorkflow(
        workflowId: String,
        versionId: String,
        snapshotJson: String,
        changeSummary: String,
        approvedAtMillis: Long,
    ): Int {
        val nextVersion = latestVersionNumber(workflowId) + 1
        upsertVersion(
            OperationWorkflowVersionEntity(
                id = versionId,
                workflowId = workflowId,
                versionNumber = nextVersion,
                snapshotJson = snapshotJson,
                approvedAtMillis = approvedAtMillis,
                verifiedAtMillis = null,
                changeSummary = changeSummary,
            ),
        )
        updateWorkflowStatus(workflowId, "Approved", approvedAtMillis)
        return nextVersion
    }

    @Transaction
    open suspend fun upsertSyncedVersion(
        workflowId: String,
        versionId: String,
        versionNumber: Int,
        snapshotJson: String,
        approvedAtMillis: Long,
        changeSummary: String,
    ) {
        val existing = loadVersion(workflowId, versionNumber)
        upsertVersion(
            OperationWorkflowVersionEntity(
                id = existing?.id ?: versionId,
                workflowId = workflowId,
                versionNumber = versionNumber.coerceAtLeast(1),
                snapshotJson = snapshotJson,
                approvedAtMillis = approvedAtMillis.coerceAtLeast(0L),
                verifiedAtMillis = existing?.verifiedAtMillis,
                changeSummary = changeSummary,
            ),
        )
    }

    @Transaction
    open suspend fun resetCompiledGraph(
        workflowId: String,
        updatedAtMillis: Long,
    ) {
        deleteSelectors(workflowId)
        deleteStateChecks(workflowId)
        deleteSteps(workflowId)
        deleteMilestones(workflowId)
        deleteVariables(workflowId)
        updateWorkflowAfterDemonstration(
            workflowId = workflowId,
            status = "Intent",
            demonstrationId = null,
            updatedAtMillis = updatedAtMillis,
        )
    }
}

@Database(
    entities = [
        OperationWorkflowEntity::class,
        OperationWorkflowAppScopeEntity::class,
        OperationWorkflowVariableEntity::class,
        OperationWorkflowMilestoneEntity::class,
        OperationWorkflowStepEntity::class,
        OperationWorkflowSelectorEntity::class,
        OperationWorkflowStateCheckEntity::class,
        OperationWorkflowVersionEntity::class,
        OperationDemonstrationEntity::class,
        OperationWorkflowRunEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class OperationWorkflowDatabase : RoomDatabase() {
    abstract fun workflowDao(): OperationWorkflowDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE operation_workflows ADD COLUMN riskPolicyJson TEXT NOT NULL DEFAULT '{}'",
                )
                database.execSQL(
                    "ALTER TABLE operation_workflows ADD COLUMN recoveryPolicyJson TEXT NOT NULL DEFAULT '{}'",
                )
            }
        }

        @Volatile
        private var instance: OperationWorkflowDatabase? = null

        fun get(context: Context): OperationWorkflowDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OperationWorkflowDatabase::class.java,
                    "operation_learning.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

package com.yuchen.ailedger.data

import android.content.Context
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
    indices = [
        Index("workflowId"),
        Index(value = ["workflowId", "variableKey"], unique = true),
    ],
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
    indices = [
        Index("workflowId"),
        Index(value = ["workflowId", "sortOrder"], unique = true),
    ],
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
    indices = [
        Index("workflowId"),
        Index(value = ["ownerType", "ownerId"]),
    ],
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
    indices = [
        Index("workflowId"),
        Index(value = ["workflowId", "versionNumber"], unique = true),
    ],
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
    @Relation(
        parentColumn = "id",
        entityColumn = "workflowId",
    )
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

    @Upsert
    abstract suspend fun upsertWorkflow(entity: OperationWorkflowEntity)

    @Upsert
    abstract suspend fun upsertAppScopes(entities: List<OperationWorkflowAppScopeEntity>)

    @Query("DELETE FROM operation_workflow_app_scopes WHERE workflowId = :workflowId")
    abstract suspend fun deleteAppScopes(workflowId: String)

    @Query("DELETE FROM operation_workflows WHERE id = :workflowId")
    abstract suspend fun deleteWorkflow(workflowId: String)

    @Transaction
    open suspend fun saveIntent(
        workflow: OperationWorkflowEntity,
        appScopes: List<OperationWorkflowAppScopeEntity>,
    ) {
        upsertWorkflow(workflow)
        deleteAppScopes(workflow.id)
        if (appScopes.isNotEmpty()) upsertAppScopes(appScopes)
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
    version = 1,
    exportSchema = true,
)
abstract class OperationWorkflowDatabase : RoomDatabase() {
    abstract fun workflowDao(): OperationWorkflowDao

    companion object {
        @Volatile
        private var instance: OperationWorkflowDatabase? = null

        fun get(context: Context): OperationWorkflowDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OperationWorkflowDatabase::class.java,
                    "operation_learning.db",
                ).build().also { instance = it }
            }
        }
    }
}

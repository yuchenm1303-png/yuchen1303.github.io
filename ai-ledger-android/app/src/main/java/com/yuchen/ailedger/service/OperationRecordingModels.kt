package com.yuchen.ailedger.service

import android.graphics.Rect

enum class OperationRecordingPhase(val label: String) {
    Idle("未录制"),
    Starting("准备录制"),
    Recording("正在录制"),
    Stopping("正在结束"),
    Captured("已采集"),
    Failed("录制失败"),
}

enum class OperationRecordingStopReason(val storageValue: String) {
    UserFinished("user_finished"),
    UserCancelled("user_cancelled"),
    NotificationFinished("notification_finished"),
    ServiceInterrupted("service_interrupted"),
    TaskStarted("task_started"),
    ScopeViolation("scope_violation"),
    DurationLimit("duration_limit"),
    EventLimit("event_limit"),
    InternalError("internal_error"),
}

data class OperationRecordingState(
    val phase: OperationRecordingPhase = OperationRecordingPhase.Idle,
    val workflowId: String? = null,
    val demonstrationId: String? = null,
    val workflowTitle: String = "",
    val allowedPackages: Set<String> = emptySet(),
    val startedAtMillis: Long? = null,
    val capturedEventCount: Int = 0,
    val message: String? = null,
) {
    val active: Boolean
        get() = phase == OperationRecordingPhase.Starting ||
            phase == OperationRecordingPhase.Recording ||
            phase == OperationRecordingPhase.Stopping
}

data class OperationRecordingConfig(
    val workflowId: String,
    val demonstrationId: String,
    val workflowTitle: String,
    val allowedPackages: Set<String>,
    val allowSystemSurfaces: Boolean,
    val startedAtMillis: Long,
)

data class OperationNodeEvidence(
    val viewId: String? = null,
    val className: String? = null,
    val role: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val hint: String? = null,
    val bounds: String? = null,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val clickable: Boolean = false,
    val longClickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val password: Boolean = false,
    val sensitive: Boolean = false,
    val inputLengthBucket: String? = null,
    val riskHints: Set<String> = emptySet(),
)

sealed interface OperationTraceRecord {
    val capturedAtMillis: Long
}

data class OperationAccessibilityEventRecord(
    override val capturedAtMillis: Long,
    val eventType: Int,
    val eventTypeLabel: String,
    val packageName: String,
    val className: String?,
    val windowTitle: String?,
    val contentChangeTypes: Int,
    val source: OperationNodeEvidence?,
    val eventText: String?,
    val inputLengthBucket: String?,
    val redactionApplied: Boolean,
    val scrollDeltaX: Int = 0,
    val scrollDeltaY: Int = 0,
    val scrollX: Int = 0,
    val scrollY: Int = 0,
    val maxScrollX: Int = 0,
    val maxScrollY: Int = 0,
    val fromIndex: Int = -1,
    val toIndex: Int = -1,
    val itemCount: Int = -1,
) : OperationTraceRecord

data class OperationNodeSnapshotRecord(
    override val capturedAtMillis: Long,
    val packageName: String,
    val windowTitle: String?,
    val nodes: List<OperationNodeEvidence>,
    val rawNodeCount: Int,
    val truncated: Boolean,
) : OperationTraceRecord

data class OperationRecordingMarkerRecord(
    override val capturedAtMillis: Long,
    val marker: String,
    val detail: String? = null,
) : OperationTraceRecord

internal fun Rect.toCompactBounds(): String =
    "${left},${top},${right},${bottom}"

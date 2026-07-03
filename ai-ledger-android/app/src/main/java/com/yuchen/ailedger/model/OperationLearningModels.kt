package com.yuchen.ailedger.model

/**
 * 操作学习领域协议。
 *
 * 原始演示只作为云端理解 Skill 的短期证据，不能直接执行。用户批准 Skill 后，
 * 运行时由视觉智能根据当前屏幕重新完成目标，本地只负责动作桥接与安全边界。
 * 旧的结构化步骤类型暂时保留用于历史数据兼容，不再作为新录制的主执行协议。
 */
enum class WorkflowDraftStatus(val label: String) {
    Intent("待演示"),
    Compiling("云端理解中"),
    ReadyForReview("待审核"),
    Approved("已批准"),
    Verified("已验证"),
    Paused("已暂停"),
    Archived("已归档"),
}

enum class WorkflowExecutionMode(val label: String) {
    CloudVisual("云端视觉 Skill"),
    Deterministic("历史确定性流程"),
    AssistedRepair("历史受控修复"),
}

enum class WorkflowRiskLevel(val label: String) {
    Low("低风险"),
    Medium("中风险"),
    High("高风险"),
    Prohibited("禁止执行"),
}

enum class WorkflowConfirmationPolicy(val label: String) {
    Never("无需确认"),
    OnRisk("风险时确认"),
    Always("每次确认"),
}

enum class WorkflowRecoveryMode(val label: String) {
    StopAndAsk("停止并询问"),
    RetryThenAsk("有限重试后询问"),
    AssistedRepairAfterConsent("经同意后辅助修复"),
}

enum class WorkflowVariableType(val label: String) {
    Text("文本"),
    Number("数字"),
    Date("日期"),
    Time("时间"),
    Choice("选项"),
    SecretReference("敏感信息引用"),
}

enum class WorkflowActionType(val label: String) {
    OpenApp("打开应用"),
    Tap("点击"),
    LongPress("长按"),
    InputText("输入文本"),
    ClearText("清空文本"),
    Scroll("滚动"),
    Swipe("滑动"),
    Back("返回"),
    Home("回到桌面"),
    WaitForState("等待页面"),
    RequestUserConfirmation("请求用户确认"),
}

enum class WorkflowSelectorKind(val label: String) {
    ResourceId("资源 ID"),
    ContentDescription("无障碍描述"),
    TextAndRole("文本与控件角色"),
    Hierarchy("层级关系"),
    RelativeAnchor("相对锚点"),
    VisualAnchor("视觉锚点"),
    RecordedBounds("录制区域"),
}

enum class WorkflowStateCheckType(val label: String) {
    PackageVisible("应用处于前台"),
    ActivityVisible("页面处于前台"),
    TextVisible("文本可见"),
    TextHidden("文本消失"),
    NodeExists("控件存在"),
    NodeMissing("控件消失"),
    VisualRegionMatches("视觉区域匹配"),
    UserConfirmed("用户已确认"),
}

data class WorkflowAppScope(
    val packageNames: List<String> = emptyList(),
    val displayNames: List<String> = emptyList(),
    val allowSystemSurfaces: Boolean = false,
) {
    val normalizedPackages: List<String>
        get() = packageNames.map(String::trim).filter(String::isNotBlank).distinct()
}

data class WorkflowVariableDefinition(
    val key: String,
    val label: String,
    val type: WorkflowVariableType = WorkflowVariableType.Text,
    val required: Boolean = true,
    val sensitive: Boolean = false,
    val persistValue: Boolean = false,
    val allowedValues: List<String> = emptyList(),
    val description: String = "",
)

data class TargetSelectorCandidate(
    val kind: WorkflowSelectorKind,
    val value: String,
    val weight: Float,
    val packageName: String? = null,
    val role: String? = null,
    val ancestorHint: String? = null,
)

data class TargetSelectorBundle(
    val candidates: List<TargetSelectorCandidate> = emptyList(),
    val minimumScore: Float = 0.72f,
    val coordinateFallbackAllowed: Boolean = false,
) {
    val hasStableCandidate: Boolean
        get() = candidates.any {
            it.kind != WorkflowSelectorKind.RecordedBounds && it.value.isNotBlank()
        }
}

data class WorkflowStateCheck(
    val id: String,
    val type: WorkflowStateCheckType,
    val expectedValue: String,
    val packageName: String? = null,
    val timeoutMs: Long = 8_000L,
    val required: Boolean = true,
)

data class WorkflowActionSpec(
    val type: WorkflowActionType,
    val variableKey: String? = null,
    val fixedArgument: String? = null,
)

data class WorkflowRetryPolicy(
    val maxAttempts: Int = 1,
    val delayMs: Long = 600L,
)

data class WorkflowStep(
    val id: String,
    val order: Int,
    val title: String,
    val milestoneId: String,
    val action: WorkflowActionSpec,
    val target: TargetSelectorBundle? = null,
    val preconditions: List<WorkflowStateCheck> = emptyList(),
    val postconditions: List<WorkflowStateCheck> = emptyList(),
    val retryPolicy: WorkflowRetryPolicy = WorkflowRetryPolicy(),
    val riskLevel: WorkflowRiskLevel = WorkflowRiskLevel.Low,
    val confirmationPolicy: WorkflowConfirmationPolicy = WorkflowConfirmationPolicy.OnRisk,
)

data class WorkflowMilestone(
    val id: String,
    val title: String,
    val order: Int,
    val completionChecks: List<WorkflowStateCheck> = emptyList(),
)

data class WorkflowRiskPolicy(
    val maximumAllowedRisk: WorkflowRiskLevel = WorkflowRiskLevel.Medium,
    val requireConfirmationForHighRisk: Boolean = true,
    val blockPasswordCapture: Boolean = true,
    val blockOtpCapture: Boolean = true,
    val blockPaymentConfirmation: Boolean = true,
)

data class WorkflowRecoveryPolicy(
    val mode: WorkflowRecoveryMode = WorkflowRecoveryMode.StopAndAsk,
    val maximumAutomaticRetries: Int = 1,
    val allowRouteMutation: Boolean = false,
)

data class LearnedWorkflowDraft(
    val id: String,
    val title: String,
    val goal: String,
    val appScope: WorkflowAppScope,
    val variables: List<WorkflowVariableDefinition> = emptyList(),
    val milestones: List<WorkflowMilestone> = emptyList(),
    val steps: List<WorkflowStep> = emptyList(),
    val completionChecks: List<WorkflowStateCheck> = emptyList(),
    val riskPolicy: WorkflowRiskPolicy = WorkflowRiskPolicy(),
    val recoveryPolicy: WorkflowRecoveryPolicy = WorkflowRecoveryPolicy(),
    val executionMode: WorkflowExecutionMode = WorkflowExecutionMode.CloudVisual,
    val status: WorkflowDraftStatus = WorkflowDraftStatus.Intent,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val sourceDemonstrationId: String? = null,
)

data class LearnedWorkflowVersion(
    val id: String,
    val workflowId: String,
    val versionNumber: Int,
    val snapshot: LearnedWorkflowDraft,
    val approvedAtMillis: Long,
    val verifiedAtMillis: Long? = null,
    val changeSummary: String = "",
)

data class DemonstrationIntent(
    val id: String,
    val workflowDraftId: String,
    val statedGoal: String,
    val appScope: WorkflowAppScope,
    val variableHints: List<WorkflowVariableDefinition> = emptyList(),
    val createdAtMillis: Long,
)

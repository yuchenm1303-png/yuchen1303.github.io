#!/usr/bin/env python3
"""Fail CI when protected low-load, chat rendering, routing, or UI contracts are weakened."""

from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def require_text(path: Path, required: list[str], forbidden: list[str] | None = None) -> list[str]:
    errors: list[str] = []
    text = path.read_text(encoding="utf-8")
    for token in required:
        if token not in text:
            errors.append(f"{path.relative_to(ROOT)} missing required contract: {token}")
    for token in forbidden or []:
        if token in text:
            errors.append(f"{path.relative_to(ROOT)} contains forbidden contract: {token}")
    return errors


def require_absent(paths: list[Path]) -> list[str]:
    errors: list[str] = []
    for path in paths:
        if path.exists():
            errors.append(f"{path.relative_to(ROOT)} must remain absent")
    return errors


def forbid_tokens_in_tree(root: Path, forbidden: list[str]) -> list[str]:
    errors: list[str] = []
    for path in root.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        for token in forbidden:
            if token in text:
                errors.append(f"{path.relative_to(ROOT)} contains globally forbidden contract: {token}")
    return errors


def main() -> int:
    errors: list[str] = []
    accessibility = ROOT / "app/src/main/res/xml/ai_agent_accessibility_service.xml"
    errors += require_text(
        accessibility,
        required=[
            'android:accessibilityFeedbackType="feedbackGeneric"',
            'android:canRetrieveWindowContent="true"',
            'android:canPerformGestures="true"',
            'android:canTakeScreenshot="true"',
            'android:description="@string/ai_agent_accessibility_description"',
            'android:notificationTimeout="1000"',
        ],
        forbidden=[
            "android:accessibilityEventTypes",
            "android:accessibilityFlags",
            "typeWindowStateChanged",
            "typeWindowsChanged",
            "typeAllMask",
            "flagDefault",
            "flagReportViewIds",
            "flagRetrieveInteractiveWindows",
            "flagIncludeNotImportantViews",
        ],
    )

    accessibility_service = ROOT / "app/src/main/java/com/yuchen/ailedger/service/AiAgentAccessibilityService.kt"
    errors += require_text(
        accessibility_service,
        required=[
            "private const val IDLE_EVENT_TYPES = 0",
            "private const val IDLE_ACCESSIBILITY_FLAGS = 0",
            "configureIdleServiceInfo(force = true)",
            "if (workingSessionDepth == 0) restorePassiveServiceInfo(force = true)",
            "withWorkingAccessibilityMode",
        ],
    )
    accessibility_service_text = accessibility_service.read_text(encoding="utf-8")
    begin_task_marker = "private fun beginTaskWorkingSession()"
    end_task_marker = "private fun endTaskWorkingSession()"
    if begin_task_marker in accessibility_service_text and end_task_marker in accessibility_service_text:
        begin_task_block = accessibility_service_text.split(begin_task_marker, 1)[1].split(end_task_marker, 1)[0]
        if "configureIdleServiceInfo(force = true)" not in begin_task_block:
            errors.append("AiAgentAccessibilityService task session must remain Idle outside short working scopes")
        if "configureWorkingServiceInfo()" in begin_task_block:
            errors.append("AiAgentAccessibilityService task session must not enter persistent Working mode")

    home = ROOT / "app/src/main/java/com/yuchen/ailedger/ui/AssistantHomePolished.kt"
    errors += require_text(
        home,
        required=[
            "FixedHeightOverflowSlot",
            "modelPanelVisualHeight",
            "modelExpandDelta",
            "LocalOpenGLGlassSurfaceAnchor",
            "LocalOpenGLGlassSurfaceAnchor provides shellAnchor",
            "ChatPanelV2(",
            "viewportTopInset = modelExpandDelta",
            "AnimatedMessageBubbleV2",
            "revealedMessageIds",
            "rememberRevealTextStateV2",
            "GeneratingMessageContentV2",
            "StreamingAssistantContentV2",
            "SweepingProgressTextV2",
            "TypewriterTrailV2",
            "LongReplyToggleV2",
            "ThinkingDotsV2",
            "ChatBubbleMaterialLayerHost",
            "ChatBubbleMaterialLayer(",
            "thinkingSweepStrength",
            "RichMessageContent",
            "MessageActionsV2",
            "MessageAttachmentListV2",
            "MessageBadgeV2",
            "MessageDataCards",
        ],
    )

    coordinates = ROOT / "app/src/main/java/com/yuchen/ailedger/ui/BackdropCoordinates.kt"
    errors += require_text(
        coordinates,
        required=[
            "internal object OpenGLFrameFinalizer",
            "private val preDrawListener",
            "private val finalDispatchAction",
            "observer.addOnPreDrawListener(preDrawListener)",
            "OpenGLFrameFinalizer.dispatch(finalDispatchAction)",
        ],
        forbidden=[
            "activeTickers.toTypedArray()",
            "OpenGLPresentationFence",
            "awaitPendingSwaps",
            "Thread.sleep",
        ],
    )

    cached_page = ROOT / "app/src/main/java/com/yuchen/ailedger/ui/CachedTabPageLayer.kt"
    errors += require_text(
        cached_page,
        required=[
            "OpenGLFrameFinalizer.bindHostView(hostView)",
            "OpenGLFrameFinalizer.requestActiveTickerFrame()",
            "NonOpenGLGlassBatchHost(",
        ],
        forbidden=[
            "OpenGLPageShellCompositor",
            "OpenGLPageWebShellLayer",
            "OpenGLPageBatchShellLayer",
            "LocalPageLegacyOpenGLShellState",
            "LocalPageWebOpenGLShellState",
            "LocalPageOpenGLShellBatchState",
        ],
    )

    removed_page_hosts = [
        ROOT / "app/src/main/java/com/yuchen/ailedger/ui/gl/OpenGLPageShellCompositor.kt",
        ROOT / "app/src/main/java/com/yuchen/ailedger/ui/gl/OpenGLPageWebShellLayer.kt",
        ROOT / "app/src/main/java/com/yuchen/ailedger/ui/gl/OpenGLPageBatchShellLayer.kt",
        ROOT / "app/src/main/java/com/yuchen/ailedger/ui/OpenGLPresentationFence.kt",
    ]
    errors += require_absent(removed_page_hosts)
    errors += forbid_tokens_in_tree(
        ROOT / "app/src/main/java",
        forbidden=[
            "OpenGLPresentationFence",
            "awaitPendingSwaps",
            "OpenGLPageShellCompositor",
            "OpenGLPageWebShellLayer",
            "OpenGLPageBatchShellLayer",
        ],
    )

    glass = ROOT / "app/src/main/java/com/yuchen/ailedger/ui/Glass.kt"
    errors += require_text(
        glass,
        required=[
            "role: GlassRole = GlassRole.Card",
            "if (role != GlassRole.Shell)",
            "NewOpenGLGlassCardLayer(",
            "role = GlassRole.Shell",
        ],
    )

    shell_route = ROOT / "app/src/main/java/com/yuchen/ailedger/ui/OpenGlShellGlass.kt"
    errors += require_text(
        shell_route,
        required=[
            "val wantsOpenGlShell = mood == OpenGlShellMood.Hero || forceOpenGl",
            "role = GlassRole.Shell",
            "role = GlassRole.Card",
        ],
    )

    app_route = ROOT / "app/src/main/java/com/yuchen/ailedger/ui/App.kt"
    errors += require_text(
        app_route,
        required=[
            "StockFirstToolsHomeScreen(",
            "SettingsPolishedScreenOptimized(",
        ],
    )

    settings_route = ROOT / "app/src/main/java/com/yuchen/ailedger/ui/SettingsPolishedDetails.kt"
    errors += require_text(
        settings_route,
        required=[
            "SettingsDetailSection.Assistant -> VisualAgentHudSettingsContent(state)",
            "SettingsDetailSection.Memory -> AccountMemorySettingsContent(state)",
        ],
    )

    tools_page = ROOT / "app/src/main/java/com/yuchen/ailedger/ui/StockFirstToolsHomeScreen.kt"
    errors += require_text(
        tools_page,
        required=[
            "private fun PendingToolScreen(",
            '"功能正在建设"',
        ],
        forbidden=[
            "Text(destination.icon",
        ],
    )

    operation_learning = ROOT / "app/src/main/java/com/yuchen/ailedger/ui/OperationLearningScreen.kt"
    errors += require_text(
        operation_learning,
        required=[
            "private fun RecordingStatusCard(",
            "private fun CreateIntentCard(",
            "private fun LearningFlowCard()",
            "private fun CloudAuthorityCard()",
            "private fun SafetyBoundaryCard()",
            "云端理解你的方法并生成 Skill",
        ],
        forbidden=[
            'text = if (active) "录" else "✓"',
            'text = "意"',
            "text = step.index",
            'Text("＋"',
            'text = if (thisRecording) "录" else "草"',
            'Text("盾"',
            "Resource ID 和固定选择器",
        ],
    )

    trace_store = ROOT / "app/src/main/java/com/yuchen/ailedger/data/OperationTraceStore.kt"
    errors += require_text(
        trace_store,
        required=[
            "private val writerFailure = AtomicReference<Throwable?>(null)",
            "private val records = Channel<OperationTraceRecord>(capacity = MAX_BUFFERED_RECORDS)",
            "private fun reserveBytes(bytes: Long, limit: Long): Boolean",
            "writerFailure.get()?.let { throw it }",
            "private const val FINAL_MARKER_RESERVE_BYTES",
        ],
        forbidden=[
            "BufferOverflow.DROP_OLDEST",
            "onBufferOverflow = BufferOverflow.DROP_OLDEST",
        ],
    )

    workflow_database = ROOT / "app/src/main/java/com/yuchen/ailedger/data/OperationWorkflowDatabase.kt"
    errors += require_text(
        workflow_database,
        required=[
            "@ColumnInfo(defaultValue = \"'{}'\") val riskPolicyJson",
            "@ColumnInfo(defaultValue = \"'{}'\") val recoveryPolicyJson",
            "private val MIGRATION_1_2 = object : Migration(1, 2)",
            ".addMigrations(MIGRATION_1_2)",
            "version = 2",
        ],
    )

    workflow_repository = ROOT / "app/src/main/java/com/yuchen/ailedger/data/OperationWorkflowRepository.kt"
    errors += require_text(
        workflow_repository,
        required=[
            "riskPolicy = workflow.riskPolicyJson.toRiskPolicy()",
            "recoveryPolicy = workflow.recoveryPolicyJson.toRecoveryPolicy()",
            "riskPolicyJson = riskPolicy.toJson()",
            "recoveryPolicyJson = recoveryPolicy.toJson()",
        ],
    )

    recording_coordinator = ROOT / "app/src/main/java/com/yuchen/ailedger/service/OperationLearningRecordingCoordinator.kt"
    errors += require_text(
        recording_coordinator,
        required=[
            "VisualDemonstrationRecorder(",
            "OperationSkillLearningCoordinator.learn(",
            "本地不会扫描或编译控件节点",
            "fun append(@Suppress(\"UNUSED_PARAMETER\") record: OperationTraceRecord): Boolean = false",
        ],
        forbidden=[
            "OperationWorkflowCompilationCoordinator.compile(",
            "OperationTraceWriter",
            "beginOperationRecording(",
        ],
    )

    visual_recorder = ROOT / "app/src/main/java/com/yuchen/ailedger/service/VisualDemonstrationRecorder.kt"
    errors += require_text(
        visual_recorder,
        required=[
            "captureFreshSnapshot(forceVisual = true)",
            "packageName !in allowedPackages",
            "session.appendFrame(",
        ],
        forbidden=[
            "OperationTraceRedactor",
            "OperationNodeSnapshotRecord",
            "viewIdResourceName",
        ],
    )

    skill_cloud = ROOT / "app/src/main/java/com/yuchen/ailedger/service/OperationSkillCloudClient.kt"
    errors += require_text(
        skill_cloud,
        required=[
            "不得输出固定坐标、Resource ID、无障碍节点、选择器、页面指纹或机械点击脚本",
            "AiWorkerClient",
            "LearnedVisualSkill(",
        ],
    )

    visual_store = ROOT / "app/src/main/java/com/yuchen/ailedger/data/VisualDemonstrationStore.kt"
    errors += require_text(
        visual_store,
        required=[
            "AES/GCM/NoPadding",
            "AndroidKeyStore",
            "cleanupExpired",
            "MAX_FRAMES = 24",
        ],
    )

    plan_components = ROOT / "app/src/main/java/com/yuchen/ailedger/ui/PlanCenterComponents.kt"
    errors += require_text(
        plan_components,
        required=[
            "internal fun PlanQuickComposer(",
            "internal fun PlanInfoBanner(",
            "internal fun PlanEmptyCard(",
            "internal fun PlanTaskCard(",
        ],
        forbidden=[
            'Text("+",',
            'Text("→",',
            'Text("准",',
            'Text("计",',
            "task.type.shortLabel",
        ],
    )

    plan_editor = ROOT / "app/src/main/java/com/yuchen/ailedger/ui/PlanCenterEditor.kt"
    errors += require_text(
        plan_editor,
        required=[
            "internal fun PlanEditorPage(",
            "internal fun PlanDeletePage(",
        ],
        forbidden=[
            'Text(\n                                "删",',
        ],
    )

    memory_page = ROOT / "app/src/main/java/com/yuchen/ailedger/ui/AssistantMemorySettingsContent.kt"
    errors += require_text(
        memory_page,
        required=[
            "private fun MemoryCenteredCard(title: String, description: String)",
        ],
        forbidden=[
            "MemoryCenteredCard(icon:",
            'icon = "锁"',
            'icon = "令"',
            'icon = "忆"',
        ],
    )

    visual_diagnostics = ROOT / "app/src/main/java/com/yuchen/ailedger/ui/VisualIntelligenceDiagnosticsSettingsContent.kt"
    errors += require_text(
        visual_diagnostics,
        required=[
            "private fun DiagnosticEmptyState()",
        ],
        forbidden=[
            'Text(\n                "诊",',
        ],
    )

    build_gradle = ROOT / "app/build.gradle"
    build_text = build_gradle.read_text(encoding="utf-8")
    if build_text.count("shrinkResources true") < 2:
        errors.append("app/build.gradle must keep resource shrinking enabled for release and performance")
    if "androidx.exifinterface:exifinterface" in build_text:
        errors.append("app/build.gradle reintroduced the unused AndroidX ExifInterface dependency")

    errors += require_text(
        ROOT / "app/src/main/java/com/yuchen/ailedger/service/NormalChatDeviceIntentPolicy.kt",
        required=["shouldProbe", "shouldIncludeInstalledApps"],
    )
    errors += require_text(
        ROOT / "app/src/main/java/com/yuchen/ailedger/service/StreamingDeltaCoalescer.kt",
        required=["first fragment", "fun drain()"],
    )

    if errors:
        print("Lightweight contract verification failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print("Lightweight contracts verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

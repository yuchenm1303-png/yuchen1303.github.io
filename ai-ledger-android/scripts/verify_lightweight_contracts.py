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

    home = ROOT / "app/src/main/java/com/yuchen/ailedger/ui/AssistantHomePolished.kt"
    errors += require_text(
        home,
        required=[
            "FixedHeightOverflowSlot",
            "modelPanelVisualHeight",
            "modelExpandDelta",
            "LocalOpenGLGlassSurfaceAnchor",
            "ChatPanelV2(",
            "viewportTopInset = modelExpandDelta",
            "AnimatedMessageBubbleV2",
            "rememberRevealTextStateV2",
            "GeneratingMessageContentV2",
            "StreamingAssistantContentV2",
            "SweepingProgressTextV2",
            "TypewriterTrailV2",
            "LongReplyToggleV2",
            "ThinkingDotsV2",
            "MessageActionsV2",
            "MessageAttachmentListV2",
            "MessageBadgeV2",
            "MessageDataCards",
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
            'Text("功能正在建设"',
            "private fun StockToolEntryContent(destination: ToolDestination)",
        ],
        forbidden=[
            "Text(destination.icon",
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

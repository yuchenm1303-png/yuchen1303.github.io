package com.yuchen.ailedger.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ObservedScreenNode(
    val id: String,
    val text: String,
    val className: String,
    val bounds: String,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
)

data class ScreenVisualObservation(
    val available: Boolean = false,
    val mimeType: String = "image/jpeg",
    val width: Int = 0,
    val height: Int = 0,
    val displayWidth: Int = 0,
    val displayHeight: Int = 0,
    val base64Jpeg: String = "",
    val source: String = "none",
    val reason: String = "",
    val capturedAt: Long = 0L,
) {
    val hasImage: Boolean
        get() = available && base64Jpeg.isNotBlank() && width > 0 && height > 0

    val hasReferenceFrame: Boolean
        get() = width > 0 && height > 0 && displayWidth > 0 && displayHeight > 0
}

data class ScreenObservation(
    val enabled: Boolean = false,
    val serviceConnected: Boolean = false,
    val packageName: String = "",
    val windowTitle: String = "",
    val updatedAt: Long = 0L,
    val textItems: List<String> = emptyList(),
    val allItems: List<ObservedScreenNode> = emptyList(),
    val clickableItems: List<ObservedScreenNode> = emptyList(),
    val inputItems: List<ObservedScreenNode> = emptyList(),
    val scrollableItems: List<ObservedScreenNode> = emptyList(),
    val nodeCount: Int = 0,
    val capturedNodeCount: Int = 0,
    val visual: ScreenVisualObservation? = null,
)

data class AccessibilityWindowPackageHint(
    val packageName: String,
    val windowTitle: String,
    val observedAt: Long,
)

object ScreenObservationStore {
    private val mutableObservation = MutableStateFlow(ScreenObservation())
    val observation: StateFlow<ScreenObservation> = mutableObservation.asStateFlow()

    @Volatile private var latestWindowPackageHint: AccessibilityWindowPackageHint? = null

    fun update(observation: ScreenObservation) {
        mutableObservation.value = sanitizeVisualObservation(observation)
    }

    fun markConnectedWaitingForWindow() {
        latestWindowPackageHint = null
        val current = mutableObservation.value
        mutableObservation.value = current.copy(
            enabled = true,
            serviceConnected = true,
            packageName = "",
            windowTitle = "",
            textItems = emptyList(),
            allItems = emptyList(),
            clickableItems = emptyList(),
            inputItems = emptyList(),
            scrollableItems = emptyList(),
            nodeCount = 0,
            capturedNodeCount = 0,
            visual = current.visual?.takeIf { it.hasReferenceFrame },
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun updateWindowHint(packageName: String, windowTitle: String = "") {
        val cleanPackage = packageName.trim()
        val cleanTitle = windowTitle.trim()
        if (cleanPackage.isBlank() || isOwnVisualHudText(cleanTitle)) return
        val now = System.currentTimeMillis()
        latestWindowPackageHint = AccessibilityWindowPackageHint(
            packageName = cleanPackage,
            windowTitle = cleanTitle,
            observedAt = now,
        )
        val current = mutableObservation.value
        mutableObservation.value = current.copy(
            enabled = true,
            serviceConnected = true,
            packageName = cleanPackage,
            windowTitle = cleanTitle.ifBlank { current.windowTitle },
            textItems = emptyList(),
            allItems = emptyList(),
            clickableItems = emptyList(),
            inputItems = emptyList(),
            scrollableItems = emptyList(),
            nodeCount = 0,
            capturedNodeCount = 0,
            visual = current.visual?.takeIf { it.hasReferenceFrame },
            updatedAt = now,
        )
    }

    fun recentWindowPackageHint(
        maxAgeMs: Long = WINDOW_PACKAGE_HINT_MAX_AGE_MS,
        nowMs: Long = System.currentTimeMillis(),
    ): AccessibilityWindowPackageHint? {
        val hint = latestWindowPackageHint ?: return null
        val latestCaptureStartedAt = mutableObservation.value.updatedAt
        val belongsToLatestCapture = latestCaptureStartedAt > 0L && hint.observedAt >= latestCaptureStartedAt
        val recentByAge = nowMs >= hint.observedAt &&
            nowMs - hint.observedAt <= maxAgeMs.coerceAtLeast(0L)
        return hint.takeIf { belongsToLatestCapture || recentByAge }
    }

    fun markDisabled() {
        latestWindowPackageHint = null
        mutableObservation.value = ScreenObservation(updatedAt = System.currentTimeMillis())
    }

    private fun sanitizeVisualObservation(observation: ScreenObservation): ScreenObservation {
        val visual = observation.visual
        if (visual?.hasImage != true) return observation

        val now = System.currentTimeMillis()
        val hint = latestWindowPackageHint
            ?.takeIf { now >= it.observedAt && now - it.observedAt <= WINDOW_PACKAGE_HINT_MAX_AGE_MS }
            ?.takeIf { it.packageName.isNotBlank() && !isOwnVisualHudText(it.windowTitle) }

        val hasOwnHudNodes = observation.textItems.any(::isOwnVisualHudText) ||
            observation.allItems.any { isOwnVisualHudText(it.text) } ||
            observation.clickableItems.any { isOwnVisualHudText(it.text) } ||
            observation.inputItems.any { isOwnVisualHudText(it.text) } ||
            observation.scrollableItems.any { isOwnVisualHudText(it.text) }

        val cleanPackageName = hint?.packageName ?: observation.packageName
        val cleanTitle = buildString {
            val hintTitle = hint?.windowTitle.orEmpty()
            if (hintTitle.isNotBlank()) {
                append(hintTitle)
                append(" · ")
            }
            append("视觉截图权威")
            if (hasOwnHudNodes) append(" · HUD节点已隔离")
        }.take(120)

        // 视觉智能 / GUI Plus 主链以 clean screenshot 为页面权威。带截图观察不再向后端暴露
        // accessibility 节点树，避免 TYPE_ACCESSIBILITY_OVERLAY HUD/胶囊被误当作 work surface。
        return observation.copy(
            packageName = cleanPackageName,
            windowTitle = cleanTitle,
            textItems = emptyList(),
            allItems = emptyList(),
            clickableItems = emptyList(),
            inputItems = emptyList(),
            scrollableItems = emptyList(),
            nodeCount = 0,
            capturedNodeCount = 0,
        )
    }

    private fun isOwnVisualHudText(value: String): Boolean {
        val text = value.trim()
        if (text.isBlank()) return false
        return text == "视觉智能体灵动胶囊" ||
            text == "展开或收起 GUI Plus 对话" ||
            text == "暂停智能体" ||
            text == "恢复智能体执行" ||
            text == "当前正在等待用户处理" ||
            text == "与 GUI Plus 沟通" ||
            text == "GUI Plus 正在准备下一步操作" ||
            text == "等待 GUI Plus 发来消息…" ||
            text.startsWith("Step ") ||
            text.contains("GUI Plus 正在根据页面证据") ||
            text.contains("视觉智能体")
    }

    private const val WINDOW_PACKAGE_HINT_MAX_AGE_MS = 600L
}

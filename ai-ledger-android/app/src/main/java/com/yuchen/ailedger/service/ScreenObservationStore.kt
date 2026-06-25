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
        mutableObservation.value = observation
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
        if (cleanPackage.isBlank()) return
        val now = System.currentTimeMillis()
        latestWindowPackageHint = AccessibilityWindowPackageHint(
            packageName = cleanPackage,
            windowTitle = windowTitle.trim(),
            observedAt = now,
        )
        val current = mutableObservation.value
        mutableObservation.value = current.copy(
            enabled = true,
            serviceConnected = true,
            packageName = cleanPackage,
            windowTitle = windowTitle.ifBlank { current.windowTitle },
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
        return hint.takeIf {
            nowMs >= it.observedAt && nowMs - it.observedAt <= maxAgeMs.coerceAtLeast(0L)
        }
    }

    fun markDisabled() {
        latestWindowPackageHint = null
        mutableObservation.value = ScreenObservation(updatedAt = System.currentTimeMillis())
    }

    private const val WINDOW_PACKAGE_HINT_MAX_AGE_MS = 600L
}

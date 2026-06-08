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

object ScreenObservationStore {
    private val mutableObservation = MutableStateFlow(ScreenObservation())
    val observation: StateFlow<ScreenObservation> = mutableObservation.asStateFlow()

    fun update(observation: ScreenObservation) {
        mutableObservation.value = observation
    }

    fun markConnectedWaitingForWindow() {
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
        val current = mutableObservation.value
        mutableObservation.value = current.copy(
            enabled = true,
            serviceConnected = true,
            packageName = packageName.ifBlank { current.packageName },
            windowTitle = windowTitle.ifBlank { current.windowTitle },
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

    fun markDisabled() {
        mutableObservation.value = ScreenObservation(updatedAt = System.currentTimeMillis())
    }
}

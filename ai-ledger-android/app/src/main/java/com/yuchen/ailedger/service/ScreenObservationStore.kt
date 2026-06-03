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

data class ScreenObservation(
    val enabled: Boolean = false,
    val serviceConnected: Boolean = false,
    val packageName: String = "",
    val windowTitle: String = "",
    val updatedAt: Long = 0L,
    val textItems: List<String> = emptyList(),
    val clickableItems: List<ObservedScreenNode> = emptyList(),
    val inputItems: List<ObservedScreenNode> = emptyList(),
    val scrollableItems: List<ObservedScreenNode> = emptyList(),
    val nodeCount: Int = 0,
)

object ScreenObservationStore {
    private val mutableObservation = MutableStateFlow(ScreenObservation())
    val observation: StateFlow<ScreenObservation> = mutableObservation.asStateFlow()

    fun update(observation: ScreenObservation) {
        mutableObservation.value = observation
    }

    fun markConnectedWaitingForWindow() {
        mutableObservation.value = mutableObservation.value.copy(
            enabled = true,
            serviceConnected = true,
            packageName = "",
            windowTitle = "",
            textItems = emptyList(),
            clickableItems = emptyList(),
            inputItems = emptyList(),
            scrollableItems = emptyList(),
            nodeCount = 0,
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
            clickableItems = emptyList(),
            inputItems = emptyList(),
            scrollableItems = emptyList(),
            nodeCount = 0,
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun markDisabled() {
        mutableObservation.value = ScreenObservation(updatedAt = System.currentTimeMillis())
    }
}

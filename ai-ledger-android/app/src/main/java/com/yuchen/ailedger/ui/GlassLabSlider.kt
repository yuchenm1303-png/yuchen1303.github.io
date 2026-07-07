package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yuchen.ailedger.model.AssistantUiState

@Composable
internal fun GlassLabFoldout(
    title: String,
    subtitle: String,
    initiallyExpanded: Boolean,
    state: AssistantUiState,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(title)
        if (initiallyExpanded) content()
    }
}

@Composable
internal fun Group(
    title: String,
    subtitle: String,
    state: AssistantUiState,
    initiallyExpanded: Boolean = true,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(title)
        if (initiallyExpanded) content()
    }
}

@Composable
internal fun LabSlider(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Text(title)
}

@Composable
internal fun LabActionButton(
    title: String,
    subtitle: String,
    state: AssistantUiState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Text(title, modifier = modifier)
}

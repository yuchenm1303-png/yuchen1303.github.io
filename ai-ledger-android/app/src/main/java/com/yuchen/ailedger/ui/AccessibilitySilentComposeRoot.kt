package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * Keeps the app visually and interactively identical while preventing Android accessibility from
 * traversing the very large Compose virtual semantics tree when our lightweight agent service is on.
 *
 * The AI agent observes external apps through AccessibilityService on demand, so it does not need
 * the host app itself to expose a full accessibility semantics tree during normal use.
 */
@Composable
fun AccessibilitySilentComposeRoot(
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clearAndSetSemantics { },
    ) {
        content()
    }
}

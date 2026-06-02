package com.yuchen.ailedger.ui

import androidx.compose.runtime.compositionLocalOf

val LocalAgentObservationAction = compositionLocalOf<(String) -> Unit> { {} }

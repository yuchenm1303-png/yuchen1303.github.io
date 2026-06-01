package com.yuchen.ailedger.ui

import androidx.compose.runtime.staticCompositionLocalOf

val LocalMobileCommandQuickReply = staticCompositionLocalOf<((String) -> Unit)?> { null }

package com.yuchen.ailedger.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates

class BackdropCoordinateSource {
    var coordinates: LayoutCoordinates? = null

    fun rootOffset(): Offset {
        val current = coordinates
        return if (current != null && current.isAttached) {
            current.localToRoot(Offset.Zero)
        } else {
            Offset.Zero
        }
    }
}

class GlassCoordinateSource {
    var coordinates: LayoutCoordinates? = null

    fun offsetRelativeTo(backdrop: BackdropCoordinateSource?): Offset {
        val current = coordinates
        return if (current != null && current.isAttached) {
            current.localToRoot(Offset.Zero) - (backdrop?.rootOffset() ?: Offset.Zero)
        } else {
            Offset.Zero
        }
    }
}

class BackdropFrameTicker {
    var frameNanos by mutableLongStateOf(0L)
}

val LocalBackdropOrigin = compositionLocalOf<BackdropCoordinateSource?> { null }
val LocalBackdropFrameTicker = compositionLocalOf<BackdropFrameTicker?> { null }

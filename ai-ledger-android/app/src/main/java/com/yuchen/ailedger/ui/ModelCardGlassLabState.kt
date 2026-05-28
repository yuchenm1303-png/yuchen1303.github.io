package com.yuchen.ailedger.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yuchen.ailedger.model.ModelCardGlassStyle

object ModelCardGlassLabState {
    var style by mutableStateOf(ModelCardGlassStyle())
        private set

    fun update(next: ModelCardGlassStyle) {
        style = next
    }

    fun reset() {
        style = ModelCardGlassStyle()
    }
}

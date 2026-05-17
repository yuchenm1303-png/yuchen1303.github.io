package com.yuchen.ailedger

enum class GlassMode {
    Basic,
    Blur,
    Liquid,
    Safe;

    companion object {
        fun from(value: String?): GlassMode = when (value?.lowercase()) {
            "blur" -> Blur
            "liquid" -> Liquid
            "safe" -> Safe
            else -> Basic
        }
    }
}

package com.yuchen.ailedger.model

enum class AppTab(val title: String, val icon: String) {
    Assistant("AI助手", "AI"),
    Tools("功能", "✦"),
    Settings("设置", "⚙")
}

enum class RenderQuality(
    val title: String,
    val desc: String,
    val starCount: Int,
    val mistCount: Int,
    val enableMotion: Boolean,
    val glassAlpha: Float
) {
    Smooth("流畅", "降低动态和玻璃层数，适合日常长期使用", 38, 1, false, 0.20f),
    Balanced("均衡", "保留轻微星空与云雾，兼顾质感和帧率", 72, 2, true, 0.16f),
    Experimental("高画质测试", "更多星星、云雾和高光，适合测试质感", 118, 3, true, 0.13f);

    val storageValue: String
        get() = name

    companion object {
        fun fromStorage(value: String): RenderQuality {
            return entries.firstOrNull { it.name == value } ?: Balanced
        }
    }
}

enum class GlassPreset(val label: String, val glassIntensity: Float, val motionIntensity: Float) {
    Basic("Basic", 0.78f, 0.35f),
    Blur("Blur", 0.95f, 0.7f),
    Liquid("Liquid", 1.18f, 1.18f),
    Safe("Safe", 0.88f, 0.45f);

    val storageValue: String
        get() = name

    companion object {
        fun fromStorage(value: String): GlassPreset {
            return entries.firstOrNull { it.name == value } ?: Liquid
        }
    }
}

enum class BackgroundTheme(val label: String, val storageValue: String) {
    Aurora("极光", "aurora"),
    Jade("翡翠海雾", "jade"),
    Sunset("暮色流光", "sunset"),
    Dawn("晨曦珍珠", "dawn");

    companion object {
        fun fromStorage(value: String): BackgroundTheme {
            return entries.firstOrNull { it.storageValue == value || it.name == value } ?: Aurora
        }
    }
}

enum class MessageRole {
    Assistant,
    User
}

data class ChatMessage(
    val id: String,
    val text: String,
    val role: MessageRole
)

data class StatSummary(
    val title: String,
    val value: String
)

data class ToolEntry(
    val title: String,
    val subtitle: String,
    val icon: String = "✦"
)

data class AssistantUiState(
    val currentTab: AppTab = AppTab.Assistant,
    val quality: RenderQuality = RenderQuality.Balanced,
    val showPreviewConversation: Boolean = true,
    val glassPreset: GlassPreset = GlassPreset.Liquid,
    val backgroundTheme: BackgroundTheme = BackgroundTheme.Aurora,
    val glassIntensity: Float = 1f,
    val motionIntensity: Float = 1f,
    val stats: List<StatSummary> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val tools: List<ToolEntry> = emptyList()
)
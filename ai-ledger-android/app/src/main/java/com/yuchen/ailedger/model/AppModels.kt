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

data class BackdropDebugParams(
    val scale: Float = 0.42f,
    val radius: Float = 7f,
    val iterations: Float = 3f,
    val brightness: Float = 1.16f,
    val contrast: Float = 1.08f,
    val saturation: Float = 1.08f,
    val cloudAlpha: Float = 1.18f,
    val cloudSoftness: Float = 1.35f,
    val cloudStretchX: Float = 2.20f,
    val cloudStretchY: Float = 0.72f,
    val cloudHighlightAlpha: Float = 0.24f,
    val moonScale: Float = 1.00f,
    val moonHaloAlpha: Float = 0.18f,
    val moonRimAlpha: Float = 0.42f
)

data class GlassBorderStyle(
    val outerStrokeAlpha: Float = 0.40f,
    val innerStrokeAlpha: Float = 0f,
    val topHighlightAlpha: Float = 1.28f,
    val bottomShadowAlpha: Float = 0.35f,
    val cornerGlintAlpha: Float = 0f,
    val ringWidthDp: Float = 10f,
    val edgePullDp: Float = -205.94f,
    val edgeAlpha: Float = 0f,
    val edgeBlurDp: Float = 24f,
    val edgeContrast: Float = 1.00f,
    val edgeSaturation: Float = 1.00f,
    val edgeBrightness: Float = 1.03f,
    val bodyAlpha: Float = 0f,
    val openGlDebugLineAlpha: Float = 0f,
    val openGlVisibility: Float = 20f,
    val openGlMaxAlpha: Float = 1.00f,
    val openGlEdgeWidthScale: Float = 0f,
    val openGlPullScale: Float = 83.21f,
    val openGlCompressionScale: Float = -10f,
    val openGlCornerScale: Float = 0f,
    val openGlDarkScale: Float = -1.63f,
    val openGlSpecularScale: Float = 0f,
    val openGlChromaticScale: Float = 0f,
    val openGlSampleRadiusScale: Float = 0f
)

enum class MessageRole {
    Assistant,
    User
}

enum class MessageStatus {
    Sending,
    Sent,
    Failed
}

enum class ChatModel(val id: String, val label: String, val shortLabel: String) {
    Auto("auto", "自动选择", "自动"),
    Gemini("gemini", "Gemini 2.5 Flash", "Gemini"),
    Kimi("kimi", "Kimi K2.6", "Kimi"),
    Mistral("mistral", "Mistral Medium 3.5", "Mistral"),
    Workers("workers", "Workers AI", "Workers");

    companion object {
        fun fromId(value: String): ChatModel {
            val clean = value.lowercase().trim().replace("workers_ai", "workers")
            return entries.firstOrNull { it.id == clean || it.name.lowercase() == clean } ?: Auto
        }
    }
}

enum class LedgerRecordType(val label: String) {
    Expense("支出"),
    Income("收入")
}

data class ChatMessage(
    val id: String,
    val text: String,
    val role: MessageRole,
    val status: MessageStatus = MessageStatus.Sent,
    val source: String? = null,
    val model: String? = null,
    val modelLabel: String? = null,
    val version: String? = null,
    val errorText: String? = null,
    val createdAt: Long = System.currentTimeMillis()
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

data class LedgerRecord(
    val id: String,
    val title: String,
    val amount: Float,
    val type: LedgerRecordType,
    val category: String,
    val dateLabel: String
)

data class AssistantUiState(
    val currentTab: AppTab = AppTab.Assistant,
    val quality: RenderQuality = RenderQuality.Balanced,
    val showPreviewConversation: Boolean = true,
    val glassPreset: GlassPreset = GlassPreset.Liquid,
    val backgroundTheme: BackgroundTheme = BackgroundTheme.Aurora,
    val customBackgroundPath: String? = null,
    val glassIntensity: Float = 1f,
    val motionIntensity: Float = 1f,
    val backdropParams: BackdropDebugParams = BackdropDebugParams(),
    val glassBorderStyle: GlassBorderStyle = GlassBorderStyle(),
    val stats: List<StatSummary> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val tools: List<ToolEntry> = emptyList(),
    val composerText: String = "",
    val selectedModel: ChatModel = ChatModel.Auto,
    val selectedModelLabel: String = ChatModel.Auto.label,
    val onlineEnabled: Boolean = false,
    val isSending: Boolean = false,
    val selectedToolTitle: String? = null,
    val ledgerRecords: List<LedgerRecord> = emptyList(),
    val ledgerBudgetText: String = "1500",
    val ledgerDraftTitle: String = "",
    val ledgerDraftAmount: String = "",
    val ledgerDraftType: LedgerRecordType = LedgerRecordType.Expense,
    val ledgerDraftCategory: String = "餐饮"
)

package com.yuchen.ailedger.model

import androidx.compose.runtime.Immutable

const val BUILTIN_THEME_BACKGROUND_PATH = "__builtin_theme_background__"

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

@Immutable
data class BackdropDebugParams(
    val scale: Float = 2.00f,
    val radius: Float = 7f,
    val iterations: Float = 12f,
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

@Immutable
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
    val openGlPullScale: Float = 83.21f,
    val openGlCompressionScale: Float = -10f,
    val openGlCornerScale: Float = 0f,
    val openGlDarkScale: Float = -1.63f,
    val openGlSampleRadiusScale: Float = 0f,
    val newOpenGlBodyWidth: Float = 1.31f,
    val newOpenGlBodyCurve: Float = 2.23f,
    val newOpenGlBodyGain: Float = 509f,
    val newOpenGlBodyBandPos: Float = 0.77f,
    val newOpenGlBodyBandWidth: Float = 0.24f,
    val newOpenGlBodyBandGain: Float = 0f,
    val newOpenGlOuterRimWidthPx: Float = 2.2f,
    val newOpenGlOuterRimCompression: Float = 3.0f,
    val newOpenGlOuterRimReachPx: Float = 32f,
    val newOpenGlOuterRimGain: Float = 2.5f,
    val newOpenGlInnerWallOffsetPx: Float = 5.4f,
    val newOpenGlInnerWallWidthPx: Float = 34f,
    val newOpenGlInnerWallGain: Float = 45f,
    val newOpenGlInnerWallFalloff: Float = 2.38f,
    val newOpenGlInnerWallReachPx: Float = 0f,
    val newOpenGlDarkExtract: Float = 0.62f,
    val newOpenGlEdgeShoulderWidthPx: Float = 18f,
    val newOpenGlEdgeTangentSmear: Float = 42f,
    val newOpenGlClarity: Float = 1.00f,
    val newOpenGlTangentSmear: Float = 0.18f,
    val newOpenGlBrightness: Float = 1.00f
)

@Immutable
data class ModelCardGlassStyle(
    val bodyAlpha: Float = 0.35f,
    val innerMist: Float = 2.50f,
    val topHairline: Float = 0.09f,
    val outerRim: Float = 0.24f,
    val innerDepth: Float = 1.43f,
    val bottomShadow: Float = 0.81f,
    val selectedRainbowRim: Float = 7.30f,
    val selectedOuterHalo: Float = 4.99f,
    val selectedAura: Float = 8.00f,
    val edgeGlint: Float = 5.56f,
    val edgeGlintRadius: Float = 0.88f,
    val edgeGlintCenterX: Float = 0.38f,
    val edgeGlintCenterY: Float = 0.53f,
    val dotGlow: Float = 4.42f,
    val unselectedEnergy: Float = 4.28f,
    val radiusScale: Float = 0.72f
)

enum class MessageRole { Assistant, User }

enum class MessageStatus { Sending, Sent, Failed }

enum class ChatModel(val id: String, val label: String, val shortLabel: String) {
    Auto("auto", "自动选择", "自动"),
    Gemini("gemini", "Gemini 2.5 Flash", "Gemini"),
    Kimi("qwen", "Qwen Max", "Qwen"),
    Mistral("mistral", "Mistral Medium 3.5", "Mistral"),
    Workers("workers", "Workers AI", "Workers"),
    DeepSeekV4("deepseek_v4", "DeepSeek V4 Pro", "DeepSeek"),
    GptOss("gpt_oss", "GPT OSS 120B", "GPT OSS");

    companion object {
        fun fromId(value: String): ChatModel {
            val clean = value.lowercase().trim().replace("workers_ai", "workers")
            return when {
                clean == "qwen" || clean == "qwen_max" || clean == "qwen-max" || clean == "qwen_plus" || clean == "qwen-plus" || clean == "qwen_vision" || clean == "qwen-vision" || clean.contains("omni") || clean == "kimi" || clean.startsWith("qwen") -> Kimi
                clean == "deepseek" || clean == "deepseek_v4" || clean == "deepseek-v4" || clean.contains("deepseek-v4-pro") -> DeepSeekV4
                clean == "gptoss" || clean == "gpt_oss" || clean == "gpt-oss" || clean.contains("gpt-oss-120b") -> GptOss
                else -> entries.firstOrNull { it.id == clean || it.name.lowercase() == clean } ?: Auto
            }
        }
    }
}

enum class LedgerRecordType(val label: String) { Expense("支出"), Income("收入") }

@Immutable
data class WebSource(
    val title: String = "",
    val url: String = "",
    val domain: String = "",
    val snippet: String = "",
    val publishedAt: String? = null
)

@Immutable
data class StructuredMetric(
    val label: String = "",
    val value: String = "",
    val unit: String? = null,
    val detail: String? = null
) {
    val displayLabel: String
        get() = label.trim().ifBlank { "指标" }

    val displayValue: String
        get() {
            val cleanValue = value.trim().ifBlank { "--" }
            val cleanUnit = unit?.trim().orEmpty()
            return cleanValue + (cleanUnit.takeIf { it.isNotBlank() && !cleanValue.contains(it) }?.let { " $it" } ?: "")
        }

    val displayDetail: String?
        get() = detail?.trim()?.takeIf { it.isNotBlank() }
}

@Immutable
data class StructuredDataCard(
    val type: String = "realtime",
    val title: String = "实时数据",
    val subtitle: String? = null,
    val timestamp: String? = null,
    val metrics: List<StructuredMetric> = emptyList(),
    val rawText: String? = null
)

@Immutable
data class ChatAttachment(
    val id: String,
    val mimeType: String = "image/jpeg",
    val base64Data: String,
    val fileName: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val sizeBytes: Int? = null,
    val previewUri: String? = null
)

enum class ComposerAttachmentStatus {
    Preparing,
    Ready,
    Uploading,
    Failed
}

@Immutable
data class ComposerAttachment(
    val id: String,
    val localUri: String,
    val mimeType: String = "image/jpeg",
    val fileName: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val sizeBytes: Int? = null,
    val base64Data: String? = null,
    val previewUri: String? = null,
    val progress: Float = 0f,
    val status: ComposerAttachmentStatus = ComposerAttachmentStatus.Preparing,
    val errorText: String? = null
) {
    val isReady: Boolean
        get() = status == ComposerAttachmentStatus.Ready && !base64Data.isNullOrBlank()

    fun toChatAttachment(): ChatAttachment? {
        val data = base64Data?.takeIf { it.isNotBlank() } ?: return null
        return ChatAttachment(
            id = id,
            mimeType = mimeType,
            base64Data = data,
            fileName = fileName,
            width = width,
            height = height,
            sizeBytes = sizeBytes,
            previewUri = previewUri
        )
    }
}

@Immutable
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
    val webSources: List<WebSource> = emptyList(),
    val structuredData: StructuredDataCard? = null,
    val searchUsed: Boolean = false,
    val searchProvider: String? = null,
    val attachments: List<ChatAttachment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val hasImageAttachments: Boolean
        get() = attachments.any { it.mimeType.startsWith("image/") && it.base64Data.isNotBlank() }
}

@Immutable
data class StatSummary(val title: String, val value: String)

@Immutable
data class ToolEntry(val title: String, val subtitle: String, val icon: String = "✦")

@Immutable
data class LedgerRecord(
    val id: String,
    val title: String,
    val amount: Float,
    val type: LedgerRecordType,
    val category: String,
    val dateLabel: String
)

@Immutable
data class AssistantUiState(
    val currentTab: AppTab = AppTab.Assistant,
    val quality: RenderQuality = RenderQuality.Balanced,
    val showPreviewConversation: Boolean = true,
    val glassPreset: GlassPreset = GlassPreset.Liquid,
    val backgroundTheme: BackgroundTheme = BackgroundTheme.Aurora,
    val customBackgroundPath: String? = null,
    val glassIntensity: Float = 1f,
    val motionIntensity: Float = 1f,
    val rainbowPrismStyle: RainbowPrismStyle = RainbowPrismStyle(),
    val modelCardGlassStyle: ModelCardGlassStyle = ModelCardGlassStyle(),
    val backdropParams: BackdropDebugParams = BackdropDebugParams(),
    val glassBorderStyle: GlassBorderStyle = GlassBorderStyle(),
    val navigationHomeAddress: String = "",
    val navigationSchoolAddress: String = "",
    val navigationCompanyAddress: String = "",
    val navigationDormAddress: String = "",
    val stats: List<StatSummary> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val tools: List<ToolEntry> = emptyList(),
    val composerText: String = "",
    val composerAttachments: List<ComposerAttachment> = emptyList(),
    val selectedModel: ChatModel = ChatModel.Auto,
    val selectedModelLabel: String = ChatModel.Auto.label,
    val onlineEnabled: Boolean = false,
    val agentEnabled: Boolean = true,
    val isSending: Boolean = false,
    val selectedToolTitle: String? = null,
    val ledgerRecords: List<LedgerRecord> = emptyList(),
    val ledgerBudgetText: String = "1500",
    val ledgerDraftTitle: String = "",
    val ledgerDraftAmount: String = "",
    val ledgerDraftType: LedgerRecordType = LedgerRecordType.Expense,
    val ledgerDraftCategory: String = "餐饮"
)

@Immutable
data class RainbowPrismStyle(
    val overall: Float = 1.00f,
    val edgeHighlight: Float = 1.00f,
    val sweepMin: Float = 0.15f,
    val sweepMax: Float = 0.65f,
    val rainbowHalo: Float = 0.80f
)

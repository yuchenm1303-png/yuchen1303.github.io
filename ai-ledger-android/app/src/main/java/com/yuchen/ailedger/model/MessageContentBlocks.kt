package com.yuchen.ailedger.model

import androidx.compose.runtime.Immutable

/**
 * Ordered, typed content rendered below the primary rich-text reply.
 *
 * The legacy [ChatMessage.text], [ChatMessage.structuredData] and
 * [ChatMessage.webSources] fields remain valid. This protocol is additive so
 * older workers and persisted conversations continue to render unchanged.
 */
@Immutable
sealed interface MessageContentBlock {
    val id: String
}

@Immutable
data class RichTextContentBlock(
    override val id: String,
    val text: String,
) : MessageContentBlock

@Immutable
data class CodeContentBlock(
    override val id: String,
    val code: String,
    val language: String? = null,
    val fileName: String? = null,
    val caption: String? = null,
) : MessageContentBlock

@Immutable
data class TableContentBlock(
    override val id: String,
    val columns: List<String>,
    val rows: List<List<String>>,
    val title: String? = null,
    val footnote: String? = null,
) : MessageContentBlock

enum class MessageChartType {
    Line,
    Bar,
    Pie,
    Scatter,
}

@Immutable
data class MessageChartPoint(
    val label: String = "",
    val x: Double? = null,
    val y: Double,
)

@Immutable
data class MessageChartSeries(
    val name: String = "",
    val points: List<MessageChartPoint>,
)

@Immutable
data class ChartContentBlock(
    override val id: String,
    val type: MessageChartType,
    val series: List<MessageChartSeries>,
    val title: String? = null,
    val subtitle: String? = null,
    val xAxisLabel: String? = null,
    val yAxisLabel: String? = null,
) : MessageContentBlock

@Immutable
data class MessageImageItem(
    val id: String,
    val source: String,
    val alt: String = "图片",
    val caption: String? = null,
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Immutable
data class ImageContentBlock(
    override val id: String,
    val image: MessageImageItem,
) : MessageContentBlock

@Immutable
data class ImageGalleryContentBlock(
    override val id: String,
    val images: List<MessageImageItem>,
    val title: String? = null,
) : MessageContentBlock

@Immutable
data class MessageKeyValue(
    val label: String,
    val value: String,
    val detail: String? = null,
)

@Immutable
data class KeyValueContentBlock(
    override val id: String,
    val items: List<MessageKeyValue>,
    val title: String? = null,
) : MessageContentBlock

enum class MessageCalloutTone {
    Info,
    Success,
    Warning,
    Error,
}

@Immutable
data class CalloutContentBlock(
    override val id: String,
    val text: String,
    val title: String? = null,
    val tone: MessageCalloutTone = MessageCalloutTone.Info,
) : MessageContentBlock

enum class MessageActionType {
    OpenUrl,
    CopyText,
}

@Immutable
data class MessageActionItem(
    val id: String,
    val label: String,
    val type: MessageActionType,
    val value: String,
    val emphasis: Boolean = false,
)

@Immutable
data class ActionGroupContentBlock(
    override val id: String,
    val actions: List<MessageActionItem>,
    val title: String? = null,
) : MessageContentBlock

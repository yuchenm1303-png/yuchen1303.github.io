package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ActionGroupContentBlock
import com.yuchen.ailedger.model.CalloutContentBlock
import com.yuchen.ailedger.model.ChartContentBlock
import com.yuchen.ailedger.model.CodeContentBlock
import com.yuchen.ailedger.model.ImageContentBlock
import com.yuchen.ailedger.model.ImageGalleryContentBlock
import com.yuchen.ailedger.model.KeyValueContentBlock
import com.yuchen.ailedger.model.MessageActionItem
import com.yuchen.ailedger.model.MessageActionType
import com.yuchen.ailedger.model.MessageCalloutTone
import com.yuchen.ailedger.model.MessageChartPoint
import com.yuchen.ailedger.model.MessageChartSeries
import com.yuchen.ailedger.model.MessageChartType
import com.yuchen.ailedger.model.MessageContentBlock
import com.yuchen.ailedger.model.MessageImageItem
import com.yuchen.ailedger.model.MessageKeyValue
import com.yuchen.ailedger.model.RichTextContentBlock
import com.yuchen.ailedger.model.TableContentBlock
import org.json.JSONArray
import org.json.JSONObject

private const val MAX_MESSAGE_CONTENT_BLOCKS = 24
private const val MAX_RICH_TEXT_CHARS = 20_000
private const val MAX_CODE_CHARS = 40_000
private const val MAX_TABLE_COLUMNS = 12
private const val MAX_TABLE_ROWS = 80
private const val MAX_CHART_SERIES = 8
private const val MAX_CHART_POINTS_PER_SERIES = 160
private const val MAX_IMAGES_PER_GALLERY = 12
private const val MAX_KEY_VALUES = 24
private const val MAX_MESSAGE_ACTIONS = 8

internal object MessageContentBlockParser {
    fun parse(data: JSONObject?): List<MessageContentBlock> {
        val array = contentBlockArray(data) ?: return emptyList()
        return buildList {
            for (index in 0 until minOf(array.length(), MAX_MESSAGE_CONTENT_BLOCKS)) {
                val item = array.optJSONObject(index) ?: continue
                parseBlock(item, index)?.let(::add)
            }
        }
    }

    private fun contentBlockArray(data: JSONObject?): JSONArray? {
        if (data == null) return null
        return data.optJSONArray("contentBlocks")
            ?: data.optJSONArray("content_blocks")
            ?: data.optJSONArray("blocks")
            ?: data.optJSONObject("data")?.optJSONArray("contentBlocks")
            ?: data.optJSONObject("data")?.optJSONArray("content_blocks")
            ?: data.optJSONObject("result")?.optJSONArray("contentBlocks")
            ?: data.optJSONObject("result")?.optJSONArray("content_blocks")
    }

    private fun parseBlock(item: JSONObject, index: Int): MessageContentBlock? {
        val type = item.string("type", "kind", "blockType", "block_type")
            ?.lowercase()
            ?.replace('-', '_')
            ?: return null
        val id = item.string("id", "blockId", "block_id")
            ?.take(100)
            ?: "content-block-$index-$type"
        return when (type) {
            "text", "rich_text", "richtext", "markdown" -> parseRichText(item, id)
            "code", "code_block", "codeblock" -> parseCode(item, id)
            "table", "data_table", "datatable" -> parseTable(item, id)
            "chart", "line_chart", "bar_chart", "pie_chart", "scatter_chart" -> parseChart(item, id, type)
            "image", "photo" -> parseImageBlock(item, id)
            "image_gallery", "gallery", "images" -> parseGallery(item, id)
            "key_value", "key_values", "metrics", "metric_grid", "info" -> parseKeyValue(item, id)
            "callout", "notice", "alert", "tip" -> parseCallout(item, id)
            "actions", "action_group", "buttons" -> parseActionGroup(item, id)
            else -> null
        }
    }

    private fun parseRichText(item: JSONObject, id: String): MessageContentBlock? {
        val text = item.string("text", "content", "markdown", "value")
            ?.trim()
            ?.take(MAX_RICH_TEXT_CHARS)
            ?.takeIf(String::isNotBlank)
            ?: return null
        return RichTextContentBlock(id = id, text = text)
    }

    private fun parseCode(item: JSONObject, id: String): MessageContentBlock? {
        val code = item.string("code", "content", "text", "value")
            ?.take(MAX_CODE_CHARS)
            ?.takeIf(String::isNotBlank)
            ?: return null
        return CodeContentBlock(
            id = id,
            code = code,
            language = item.string("language", "lang")?.trim()?.take(30),
            fileName = item.string("fileName", "file_name", "filename")?.trim()?.take(100),
            caption = item.string("caption", "description")?.trim()?.take(240),
        )
    }

    private fun parseTable(item: JSONObject, id: String): MessageContentBlock? {
        val columns = parseStringArray(
            item.optJSONArray("columns")
                ?: item.optJSONArray("headers")
                ?: item.optJSONArray("fields"),
            MAX_TABLE_COLUMNS,
            100,
        )
        val rowsArray = item.optJSONArray("rows")
            ?: item.optJSONArray("data")
            ?: item.optJSONArray("values")
            ?: return null
        val rows = buildList {
            for (rowIndex in 0 until minOf(rowsArray.length(), MAX_TABLE_ROWS)) {
                val raw = rowsArray.opt(rowIndex)
                val row = when (raw) {
                    is JSONArray -> parseAnyArray(raw, MAX_TABLE_COLUMNS, 500)
                    is JSONObject -> {
                        if (columns.isNotEmpty()) {
                            columns.map { column -> raw.opt(column).toDisplayString(500) }
                        } else {
                            raw.keys().asSequence().take(MAX_TABLE_COLUMNS).map { key -> raw.opt(key).toDisplayString(500) }.toList()
                        }
                    }
                    else -> listOf(raw.toDisplayString(500))
                }
                if (row.any(String::isNotBlank)) add(row)
            }
        }
        if (rows.isEmpty()) return null
        val resolvedColumns = if (columns.isNotEmpty()) {
            columns
        } else {
            List(rows.maxOfOrNull(List<String>::size)?.coerceAtMost(MAX_TABLE_COLUMNS) ?: 1) { columnIndex ->
                "列 ${columnIndex + 1}"
            }
        }
        return TableContentBlock(
            id = id,
            columns = resolvedColumns,
            rows = rows.map { row -> row.take(MAX_TABLE_COLUMNS) },
            title = item.string("title", "name")?.trim()?.take(120),
            footnote = item.string("footnote", "note", "caption")?.trim()?.take(500),
        )
    }

    private fun parseChart(item: JSONObject, id: String, declaredType: String): MessageContentBlock? {
        val chartType = parseChartType(item.string("chartType", "chart_type", "variant") ?: declaredType)
        val labels = parseStringArray(item.optJSONArray("labels"), MAX_CHART_POINTS_PER_SERIES, 60)
        val explicitSeries = item.optJSONArray("series")
        val series = if (explicitSeries != null) {
            buildList {
                for (seriesIndex in 0 until minOf(explicitSeries.length(), MAX_CHART_SERIES)) {
                    val rawSeries = explicitSeries.opt(seriesIndex)
                    parseChartSeries(rawSeries, seriesIndex, labels)?.let(::add)
                }
            }
        } else {
            parseSingleChartSeries(item, labels)?.let(::listOf).orEmpty()
        }
        if (series.isEmpty() || series.all { it.points.isEmpty() }) return null
        return ChartContentBlock(
            id = id,
            type = chartType,
            series = series,
            title = item.string("title", "name")?.trim()?.take(120),
            subtitle = item.string("subtitle", "description", "caption")?.trim()?.take(240),
            xAxisLabel = item.string("xAxisLabel", "x_axis_label", "xLabel")?.trim()?.take(60),
            yAxisLabel = item.string("yAxisLabel", "y_axis_label", "yLabel")?.trim()?.take(60),
        )
    }

    private fun parseChartSeries(raw: Any?, index: Int, labels: List<String>): MessageChartSeries? {
        return when (raw) {
            is JSONObject -> {
                val pointsArray = raw.optJSONArray("points")
                    ?: raw.optJSONArray("data")
                    ?: raw.optJSONArray("values")
                    ?: return null
                val points = parseChartPoints(pointsArray, labels)
                if (points.isEmpty()) null else MessageChartSeries(
                    name = raw.string("name", "label", "title")?.take(80).orEmpty(),
                    points = points,
                )
            }
            is JSONArray -> {
                val points = parseChartPoints(raw, labels)
                if (points.isEmpty()) null else MessageChartSeries(name = "系列 ${index + 1}", points = points)
            }
            else -> null
        }
    }

    private fun parseSingleChartSeries(item: JSONObject, labels: List<String>): MessageChartSeries? {
        val pointsArray = item.optJSONArray("points")
            ?: item.optJSONArray("data")
            ?: item.optJSONArray("values")
            ?: return null
        val points = parseChartPoints(pointsArray, labels)
        if (points.isEmpty()) return null
        return MessageChartSeries(
            name = item.string("seriesName", "series_name", "name")?.take(80).orEmpty(),
            points = points,
        )
    }

    private fun parseChartPoints(array: JSONArray, labels: List<String>): List<MessageChartPoint> = buildList {
        for (pointIndex in 0 until minOf(array.length(), MAX_CHART_POINTS_PER_SERIES)) {
            val raw = array.opt(pointIndex)
            val point = when (raw) {
                is Number -> MessageChartPoint(
                    label = labels.getOrNull(pointIndex).orEmpty(),
                    x = pointIndex.toDouble(),
                    y = raw.toDouble(),
                )
                is String -> raw.toDoubleOrNull()?.let { value ->
                    MessageChartPoint(labels.getOrNull(pointIndex).orEmpty(), pointIndex.toDouble(), value)
                }
                is JSONObject -> {
                    val y = raw.number("y", "value", "amount") ?: continue
                    MessageChartPoint(
                        label = raw.string("label", "name", "xLabel", "x_label")?.take(60)
                            ?: labels.getOrNull(pointIndex).orEmpty(),
                        x = raw.number("x", "index") ?: pointIndex.toDouble(),
                        y = y,
                    )
                }
                is JSONArray -> parseArrayChartPoint(raw, pointIndex, labels)
                else -> null
            }
            if (point != null && point.y.isFinite() && point.x?.isFinite() != false) add(point)
        }
    }

    private fun parseArrayChartPoint(
        array: JSONArray,
        index: Int,
        labels: List<String>,
    ): MessageChartPoint? {
        if (array.length() == 0) return null
        if (array.length() == 1) {
            val y = array.opt(0).toFiniteDouble() ?: return null
            return MessageChartPoint(labels.getOrNull(index).orEmpty(), index.toDouble(), y)
        }
        val first = array.opt(0)
        val second = array.opt(1).toFiniteDouble() ?: return null
        return when (first) {
            is Number -> MessageChartPoint(labels.getOrNull(index).orEmpty(), first.toDouble(), second)
            is String -> first.toDoubleOrNull()?.let { x -> MessageChartPoint(labels.getOrNull(index).orEmpty(), x, second) }
                ?: MessageChartPoint(first.take(60), index.toDouble(), second)
            else -> MessageChartPoint(labels.getOrNull(index).orEmpty(), index.toDouble(), second)
        }
    }

    private fun parseImageBlock(item: JSONObject, id: String): MessageContentBlock? {
        val imageObject = item.optJSONObject("image") ?: item
        val image = parseImageItem(imageObject, "$id-image") ?: return null
        return ImageContentBlock(id = id, image = image)
    }

    private fun parseGallery(item: JSONObject, id: String): MessageContentBlock? {
        val array = item.optJSONArray("images")
            ?: item.optJSONArray("items")
            ?: item.optJSONArray("data")
            ?: return null
        val images = buildList {
            for (imageIndex in 0 until minOf(array.length(), MAX_IMAGES_PER_GALLERY)) {
                val raw = array.opt(imageIndex)
                val image = when (raw) {
                    is JSONObject -> parseImageItem(raw, "$id-image-$imageIndex")
                    is String -> MessageImageItem(id = "$id-image-$imageIndex", source = raw.take(2_000_000))
                    else -> null
                }
                if (image != null) add(image)
            }
        }
        if (images.isEmpty()) return null
        return ImageGalleryContentBlock(
            id = id,
            images = images,
            title = item.string("title", "name")?.trim()?.take(120),
        )
    }

    private fun parseImageItem(item: JSONObject, fallbackId: String): MessageImageItem? {
        val mimeType = item.string("mimeType", "mime_type", "format")?.take(60)
        val rawBase64 = item.string("base64Data", "base64_data", "base64")
        val source = item.string("source", "url", "uri", "src", "data")
            ?: rawBase64?.let { encoded -> "data:${mimeType ?: "image/png"};base64,$encoded" }
            ?: return null
        if (source.isBlank() || source.length > 24_000_000) return null
        return MessageImageItem(
            id = item.string("id", "imageId", "image_id")?.take(100) ?: fallbackId,
            source = source,
            alt = item.string("alt", "title", "name")?.trim()?.take(120) ?: "图片",
            caption = item.string("caption", "description")?.trim()?.take(500),
            mimeType = mimeType,
            width = item.positiveInt("width"),
            height = item.positiveInt("height"),
        )
    }

    private fun parseKeyValue(item: JSONObject, id: String): MessageContentBlock? {
        val array = item.optJSONArray("items")
            ?: item.optJSONArray("metrics")
            ?: item.optJSONArray("values")
        val values = if (array != null) {
            buildList {
                for (valueIndex in 0 until minOf(array.length(), MAX_KEY_VALUES)) {
                    val raw = array.optJSONObject(valueIndex) ?: continue
                    val label = raw.string("label", "name", "key")?.trim()?.take(100) ?: continue
                    val value = raw.opt("value").toDisplayString(300).ifBlank {
                        raw.opt("text").toDisplayString(300)
                    }
                    if (value.isBlank()) continue
                    add(
                        MessageKeyValue(
                            label = label,
                            value = value,
                            detail = raw.string("detail", "description", "unit")?.trim()?.take(240),
                        )
                    )
                }
            }
        } else {
            val excluded = setOf("id", "type", "kind", "title", "name", "subtitle")
            item.keys().asSequence()
                .filterNot { it in excluded }
                .take(MAX_KEY_VALUES)
                .mapNotNull { key ->
                    item.opt(key).toDisplayString(300).takeIf(String::isNotBlank)?.let { value ->
                        MessageKeyValue(label = key.take(100), value = value)
                    }
                }
                .toList()
        }
        if (values.isEmpty()) return null
        return KeyValueContentBlock(
            id = id,
            items = values,
            title = item.string("title", "name")?.trim()?.take(120),
        )
    }

    private fun parseCallout(item: JSONObject, id: String): MessageContentBlock? {
        val text = item.string("text", "content", "message", "description")
            ?.trim()
            ?.take(4_000)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val tone = when (item.string("tone", "level", "variant", "severity")?.lowercase()) {
            "success", "positive", "ok" -> MessageCalloutTone.Success
            "warning", "warn", "caution" -> MessageCalloutTone.Warning
            "error", "danger", "critical", "negative" -> MessageCalloutTone.Error
            else -> MessageCalloutTone.Info
        }
        return CalloutContentBlock(
            id = id,
            text = text,
            title = item.string("title", "name")?.trim()?.take(120),
            tone = tone,
        )
    }

    private fun parseActionGroup(item: JSONObject, id: String): MessageContentBlock? {
        val array = item.optJSONArray("actions")
            ?: item.optJSONArray("items")
            ?: item.optJSONArray("buttons")
            ?: return null
        val actions = buildList {
            for (actionIndex in 0 until minOf(array.length(), MAX_MESSAGE_ACTIONS)) {
                val raw = array.optJSONObject(actionIndex) ?: continue
                val rawType = raw.string("type", "action", "kind")
                    ?.lowercase()
                    ?.replace('-', '_')
                    ?: continue
                val type = when (rawType) {
                    "open_url", "url", "link", "open_link" -> MessageActionType.OpenUrl
                    "copy", "copy_text", "clipboard" -> MessageActionType.CopyText
                    else -> continue
                }
                val value = when (type) {
                    MessageActionType.OpenUrl -> raw.string("url", "value", "href")
                        ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                    MessageActionType.CopyText -> raw.string("text", "value", "content")?.take(20_000)
                } ?: continue
                val label = raw.string("label", "title", "text")?.trim()?.take(80) ?: when (type) {
                    MessageActionType.OpenUrl -> "打开链接"
                    MessageActionType.CopyText -> "复制"
                }
                add(
                    MessageActionItem(
                        id = raw.string("id", "actionId", "action_id")?.take(100) ?: "$id-action-$actionIndex",
                        label = label,
                        type = type,
                        value = value,
                        emphasis = raw.optBoolean("emphasis", raw.optBoolean("primary", false)),
                    )
                )
            }
        }
        if (actions.isEmpty()) return null
        return ActionGroupContentBlock(
            id = id,
            actions = actions,
            title = item.string("title", "name")?.trim()?.take(120),
        )
    }

    private fun parseChartType(value: String): MessageChartType = when (
        value.lowercase().replace('-', '_')
    ) {
        "bar", "bar_chart", "column", "column_chart" -> MessageChartType.Bar
        "pie", "pie_chart", "donut", "doughnut" -> MessageChartType.Pie
        "scatter", "scatter_chart", "points" -> MessageChartType.Scatter
        else -> MessageChartType.Line
    }

    private fun parseStringArray(array: JSONArray?, maxItems: Int, maxChars: Int): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until minOf(array.length(), maxItems)) {
                val value = array.opt(index).toDisplayString(maxChars)
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun parseAnyArray(array: JSONArray, maxItems: Int, maxChars: Int): List<String> = buildList {
        for (index in 0 until minOf(array.length(), maxItems)) {
            add(array.opt(index).toDisplayString(maxChars))
        }
    }

    private fun JSONObject.string(vararg keys: String): String? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val value = opt(key)
            if (value is String && value.isNotBlank()) return value
            if (value is Number || value is Boolean) return value.toString()
        }
        return null
    }

    private fun JSONObject.number(vararg keys: String): Double? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            opt(key).toFiniteDouble()?.let { return it }
        }
        return null
    }

    private fun JSONObject.positiveInt(key: String): Int? {
        val value = opt(key).toFiniteDouble() ?: return null
        if (value <= 0.0 || value > Int.MAX_VALUE) return null
        return value.toInt()
    }

    private fun Any?.toFiniteDouble(): Double? {
        val value = when (this) {
            is Number -> toDouble()
            is String -> toDoubleOrNull()
            else -> null
        } ?: return null
        return value.takeIf(Double::isFinite)
    }

    private fun Any?.toDisplayString(maxChars: Int): String = when (this) {
        null, JSONObject.NULL -> ""
        is String -> this
        is Number, is Boolean -> toString()
        else -> toString()
    }.trim().take(maxChars)
}

package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.CodeContentBlock
import com.yuchen.ailedger.model.ImageContentBlock
import com.yuchen.ailedger.model.MessageContentBlock
import com.yuchen.ailedger.model.MessageImageItem
import com.yuchen.ailedger.model.TableContentBlock
import org.json.JSONArray
import org.json.JSONObject

private const val MARKDOWN_EXTRACTOR_MAX_BLOCKS = 24
private const val MARKDOWN_EXTRACTOR_MAX_CODE_CHARS = 40_000
private const val MARKDOWN_EXTRACTOR_MAX_TABLE_ROWS = 80
private const val MARKDOWN_EXTRACTOR_MAX_TABLE_COLUMNS = 12
private const val MARKDOWN_EXTRACTOR_MAX_IMAGES = 12

internal data class MessageContentMarkdownExtraction(
    val reply: String,
    val blocks: List<MessageContentBlock>,
)

private data class PositionedMessageContentBlock(
    val lineIndex: Int,
    val characterIndex: Int,
    val insertionOrder: Int,
    val block: MessageContentBlock,
)

/**
 * Upgrades ordinary model Markdown into typed supplementary blocks without
 * requiring a backend migration. Explicit server contentBlocks always win;
 * this extractor is only the backwards-compatible fallback.
 */
internal object MessageContentBlockMarkdownExtractor {
    private val fenceStartRegex = Regex("""^\s*```([^\s`]*)\s*(.*?)\s*$""")
    private val fenceEndRegex = Regex("""^\s*```\s*$""")
    private val tableDividerRegex = Regex("""^\s*\|?\s*:?-{3,}:?\s*(?:\|\s*:?-{3,}:?\s*)+\|?\s*$""")
    private val markdownImageRegex = Regex(
        """!\[([^\]]*)]\((https?://[^\s)]+)(?:\s+[\"']([^\"']*)[\"'])?\)""",
        RegexOption.IGNORE_CASE,
    )

    fun extract(reply: String): MessageContentMarkdownExtraction {
        if (reply.isBlank()) return MessageContentMarkdownExtraction(reply, emptyList())
        val normalized = reply.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.lines().toMutableList()
        val positionedBlocks = mutableListOf<PositionedMessageContentBlock>()

        extractFencedBlocks(lines, positionedBlocks)
        extractTables(lines, positionedBlocks)
        extractImages(lines, positionedBlocks)

        val cleaned = lines
            .joinToString("\n")
            .replace(Regex("""[ \t]+\n"""), "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
        val orderedBlocks = positionedBlocks
            .sortedWith(
                compareBy<PositionedMessageContentBlock> { it.lineIndex }
                    .thenBy { it.characterIndex }
                    .thenBy { it.insertionOrder },
            )
            .map { it.block }
            .take(MARKDOWN_EXTRACTOR_MAX_BLOCKS)

        return MessageContentMarkdownExtraction(
            reply = if (orderedBlocks.isEmpty()) reply else cleaned,
            blocks = orderedBlocks,
        )
    }

    private fun extractFencedBlocks(
        lines: MutableList<String>,
        blocks: MutableList<PositionedMessageContentBlock>,
    ) {
        var index = 0
        while (index < lines.size && blocks.size < MARKDOWN_EXTRACTOR_MAX_BLOCKS) {
            val start = fenceStartRegex.matchEntire(lines[index])
            if (start == null) {
                index += 1
                continue
            }
            var end = index + 1
            while (end < lines.size && !fenceEndRegex.matches(lines[end])) end += 1
            if (end >= lines.size) {
                index += 1
                continue
            }

            val language = start.groupValues[1].trim().lowercase().replace('_', '-').take(40)
            val label = start.groupValues[2].trim().take(120).takeIf(String::isNotBlank)
            val content = lines.subList(index + 1, end).joinToString("\n").take(MARKDOWN_EXTRACTOR_MAX_CODE_CHARS)
            val extracted = when (language) {
                "ai-blocks", "ai-ledger-blocks", "content-blocks", "contentblocks" -> parseProtocolBlock(content)
                "ai-chart", "chart-json", "chart" -> parseChartProtocolBlock(content)
                "ai-table", "table-json" -> parseTableProtocolBlock(content)
                else -> listOfNotNull(
                    content.takeIf(String::isNotBlank)?.let {
                        CodeContentBlock(
                            id = "markdown-code-${blocks.size}-$index",
                            code = it,
                            language = language.takeIf(String::isNotBlank),
                            fileName = label,
                        )
                    },
                )
            }
            appendPositionedBlocks(
                destination = blocks,
                lineIndex = index,
                characterIndex = 0,
                extracted = extracted,
            )
            if (extracted.isNotEmpty()) {
                for (lineIndex in index..end) lines[lineIndex] = ""
            }
            index = end + 1
        }
    }

    private fun extractTables(
        lines: MutableList<String>,
        blocks: MutableList<PositionedMessageContentBlock>,
    ) {
        var index = 0
        while (index + 1 < lines.size && blocks.size < MARKDOWN_EXTRACTOR_MAX_BLOCKS) {
            if (!looksLikeTableRow(lines[index]) || !tableDividerRegex.matches(lines[index + 1])) {
                index += 1
                continue
            }
            val columns = splitTableRow(lines[index])
            if (columns.size < 2) {
                index += 1
                continue
            }
            var end = index + 2
            val rows = mutableListOf<List<String>>()
            while (
                end < lines.size &&
                looksLikeTableRow(lines[end]) &&
                rows.size < MARKDOWN_EXTRACTOR_MAX_TABLE_ROWS
            ) {
                val row = splitTableRow(lines[end])
                if (row.isNotEmpty()) rows += row.take(MARKDOWN_EXTRACTOR_MAX_TABLE_COLUMNS)
                end += 1
            }
            if (rows.isEmpty()) {
                index += 1
                continue
            }
            val block = TableContentBlock(
                id = "markdown-table-${blocks.size}-$index",
                columns = columns.take(MARKDOWN_EXTRACTOR_MAX_TABLE_COLUMNS),
                rows = rows,
            )
            appendPositionedBlocks(
                destination = blocks,
                lineIndex = index,
                characterIndex = 0,
                extracted = listOf(block),
            )
            for (lineIndex in index until end) lines[lineIndex] = ""
            index = end
        }
    }

    private fun extractImages(
        lines: MutableList<String>,
        blocks: MutableList<PositionedMessageContentBlock>,
    ) {
        var imageCount = 0
        for (lineIndex in lines.indices) {
            if (
                imageCount >= MARKDOWN_EXTRACTOR_MAX_IMAGES ||
                blocks.size >= MARKDOWN_EXTRACTOR_MAX_BLOCKS
            ) break
            lines[lineIndex] = markdownImageRegex.replace(lines[lineIndex]) { match ->
                if (
                    imageCount >= MARKDOWN_EXTRACTOR_MAX_IMAGES ||
                    blocks.size >= MARKDOWN_EXTRACTOR_MAX_BLOCKS
                ) {
                    return@replace match.value
                }
                val alt = match.groupValues[1].trim().take(120).ifBlank { "图片" }
                val url = match.groupValues[2].trim().take(2_000)
                val title = match.groupValues[3].trim().take(240).takeIf(String::isNotBlank)
                val block = ImageContentBlock(
                    id = "markdown-image-${blocks.size}-$imageCount",
                    image = MessageImageItem(
                        id = "markdown-image-item-${blocks.size}-$imageCount",
                        source = url,
                        alt = alt,
                        caption = title ?: alt.takeIf { it != "图片" },
                    ),
                )
                appendPositionedBlocks(
                    destination = blocks,
                    lineIndex = lineIndex,
                    characterIndex = match.range.first,
                    extracted = listOf(block),
                )
                imageCount += 1
                ""
            }
        }
    }

    private fun appendPositionedBlocks(
        destination: MutableList<PositionedMessageContentBlock>,
        lineIndex: Int,
        characterIndex: Int,
        extracted: List<MessageContentBlock>,
    ) {
        val remaining = MARKDOWN_EXTRACTOR_MAX_BLOCKS - destination.size
        if (remaining <= 0) return
        extracted.take(remaining).forEach { block ->
            destination += PositionedMessageContentBlock(
                lineIndex = lineIndex,
                characterIndex = characterIndex,
                insertionOrder = destination.size,
                block = block,
            )
        }
    }

    private fun parseProtocolBlock(content: String): List<MessageContentBlock> {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return emptyList()
        return runCatching {
            val root = when {
                trimmed.startsWith("[") -> JSONObject().put("contentBlocks", JSONArray(trimmed))
                else -> {
                    val objectValue = JSONObject(trimmed)
                    when {
                        objectValue.has("contentBlocks") || objectValue.has("content_blocks") || objectValue.has("blocks") -> objectValue
                        else -> JSONObject().put("contentBlocks", JSONArray().put(objectValue))
                    }
                }
            }
            MessageContentBlockParser.parse(root)
        }.getOrDefault(emptyList())
    }

    private fun parseChartProtocolBlock(content: String): List<MessageContentBlock> {
        return runCatching {
            val chart = JSONObject(content.trim())
            val declaredType = chart.optString("type").trim()
            if (declaredType.isNotBlank() && declaredType !in setOf("chart", "line_chart", "bar_chart", "pie_chart", "scatter_chart")) {
                chart.put("chartType", declaredType)
            }
            chart.put("type", "chart")
            MessageContentBlockParser.parse(
                JSONObject().put("contentBlocks", JSONArray().put(chart)),
            )
        }.getOrDefault(emptyList())
    }

    private fun parseTableProtocolBlock(content: String): List<MessageContentBlock> {
        return runCatching {
            val table = JSONObject(content.trim()).put("type", "table")
            MessageContentBlockParser.parse(
                JSONObject().put("contentBlocks", JSONArray().put(table)),
            )
        }.getOrDefault(emptyList())
    }

    private fun looksLikeTableRow(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.count { it == '|' } >= 2 && !tableDividerRegex.matches(trimmed)
    }

    private fun splitTableRow(line: String): List<String> {
        val trimmed = line.trim().removePrefix("|").removeSuffix("|")
        return trimmed
            .split('|')
            .map { it.trim().replace("\\|", "|").take(500) }
            .take(MARKDOWN_EXTRACTOR_MAX_TABLE_COLUMNS)
    }
}

package com.yuchen.ailedger.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val INLINE_STICKER_MAX_PER_MESSAGE = 2
private const val INLINE_STICKER_SIZE_DP = 60
private const val INLINE_STICKER_BASELINE_SHIFT_DP = -9
private const val INLINE_STICKER_LINE_HEIGHT_MULTIPLIER = 1.2f
private const val INLINE_STICKER_TAG_START = 0xE0001
private const val INLINE_STICKER_TAG_CANCEL = 0xE007F
private const val INLINE_STICKER_TAG_BASE = 0xE0000
private const val INLINE_STICKER_PAYLOAD_PREFIX = "ai_sticker:"
private const val INLINE_STICKER_COMPACT_PREFIX = "s"
private val visibleInlineStickerRegex =
    Regex("""\[\[AI_LEDGER_INLINE_STICKER:([a-z0-9_]{2,48})]]""", RegexOption.IGNORE_CASE)

private data class CitationInlineToken(
    val id: String,
    val number: String
)

private data class StickerInlineToken(
    val id: String,
    val assetKey: String,
    val alt: String
)

private data class CitationInlineRender(
    val annotated: AnnotatedString,
    val citationTokens: List<CitationInlineToken>,
    val stickerTokens: List<StickerInlineToken>
)

private data class InlineStickerMarker(
    val start: Int,
    val endExclusive: Int,
    val assetKey: String
)

private data class InlineStickerAsset(
    val alt: String
)

private val inlineStickerCatalog: Map<String, InlineStickerAsset> = mapOf(
    "joy_burst" to InlineStickerAsset("开心庆祝"),
    "affection_hug" to InlineStickerAsset("贴贴拥抱"),
    "health_check" to InlineStickerAsset("关心健康"),
    "thinking_soft" to InlineStickerAsset("认真思考"),
    "cheer_power" to InlineStickerAsset("加油打气"),
    "pout_no" to InlineStickerAsset("委屈拒绝"),
    "comfort_friend" to InlineStickerAsset("安慰陪伴"),
    "red_packet_congrats" to InlineStickerAsset("恭喜祝贺"),
    "gift_for_you" to InlineStickerAsset("送上礼物"),
    "sparkle_excited" to InlineStickerAsset("兴奋闪亮"),
    "soft_smile" to InlineStickerAsset("温柔微笑"),
    "got_it_point" to InlineStickerAsset("明白了"),
    "heart_thanks" to InlineStickerAsset("比心感谢"),
    "confident_ready" to InlineStickerAsset("自信准备"),
    "playful_wink" to InlineStickerAsset("俏皮眨眼"),
    "confused_study" to InlineStickerAsset("学习困惑"),
    "confirm_yes" to InlineStickerAsset("确认赞同"),
    "idea_drawing" to InlineStickerAsset("灵感记录"),
    "reject_no" to InlineStickerAsset("不同意")
)

private val inlineStickerKeyByCode: Map<String, String> = mapOf(
    "0" to "joy_burst",
    "1" to "affection_hug",
    "2" to "health_check",
    "3" to "thinking_soft",
    "4" to "cheer_power",
    "5" to "pout_no",
    "6" to "comfort_friend",
    "7" to "red_packet_congrats",
    "8" to "gift_for_you",
    "9" to "sparkle_excited",
    "a" to "soft_smile",
    "b" to "got_it_point",
    "c" to "heart_thanks",
    "d" to "confident_ready",
    "e" to "playful_wink",
    "f" to "confused_study",
    "g" to "confirm_yes",
    "h" to "idea_drawing",
    "i" to "reject_no"
)

@Composable
fun CitationInlineRichText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    val render = remember(text) { buildCitationInlineRender(text) }

    if (render.citationTokens.isEmpty() && render.stickerTokens.isEmpty()) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            modifier = modifier
        )
        return
    }

    val density = LocalDensity.current
    val stickerSizeTextUnit = remember(density) {
        with(density) { INLINE_STICKER_SIZE_DP.dp.toSp() }
    }
    val stickerSlotHeightTextUnit = remember(density) {
        val reservedHeightDp = INLINE_STICKER_SIZE_DP + -INLINE_STICKER_BASELINE_SHIFT_DP
        with(density) { reservedHeightDp.dp.toSp() }
    }
    val inlineContent = remember(
        render.citationTokens,
        render.stickerTokens,
        stickerSizeTextUnit,
        stickerSlotHeightTextUnit
    ) {
        buildMap {
            render.citationTokens.forEach { token ->
                val chipWidth = if (token.number.length >= 2) 25.sp else 19.sp
                put(
                    token.id,
                    InlineTextContent(
                        Placeholder(
                            width = chipWidth,
                            height = 16.sp,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                        )
                    ) {
                        CitationBubble(number = token.number)
                    }
                )
            }
            render.stickerTokens.forEach { token ->
                put(
                    token.id,
                    InlineTextContent(
                        Placeholder(
                            width = stickerSizeTextUnit,
                            height = stickerSlotHeightTextUnit,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline
                        )
                    ) {
                        val image = InlineStickerAssets.rememberImageBitmap(token.assetKey)
                        if (image != null) {
                            Image(
                                bitmap = image,
                                contentDescription = token.alt,
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.TopCenter,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                )
            }
        }
    }

    Text(
        text = render.annotated,
        inlineContent = inlineContent,
        color = color,
        fontSize = fontSize,
        lineHeight = if (render.stickerTokens.isNotEmpty()) {
            fontSize * INLINE_STICKER_LINE_HEIGHT_MULTIPLIER
        } else {
            lineHeight
        },
        fontWeight = fontWeight,
        modifier = modifier
    )
}

private fun buildCitationInlineRender(text: String): CitationInlineRender {
    val citationRegex = Regex("""\[(\d{1,2})]""")
    val citationTokens = mutableListOf<CitationInlineToken>()
    val stickerTokens = mutableListOf<StickerInlineToken>()
    val stickerMarkers = findInlineStickerMarkers(text)
    var citationIndex = 0
    var stickerIndex = 0

    val annotated = buildAnnotatedString {
        fun appendTextWithCitations(value: String) {
            var lastIndex = 0
            citationRegex.findAll(value).forEach { match ->
                val start = match.range.first
                val end = match.range.last + 1
                if (start > lastIndex) append(value.substring(lastIndex, start))

                val number = match.groupValues[1]
                val id = "citation_${citationIndex}_${number}"
                citationTokens += CitationInlineToken(id = id, number = number)
                appendInlineContent(id, "[$number]")
                lastIndex = end
                citationIndex += 1
            }
            if (lastIndex < value.length) append(value.substring(lastIndex))
        }

        var cursor = 0
        stickerMarkers.forEach { marker ->
            if (marker.start > cursor) {
                appendTextWithCitations(text.substring(cursor, marker.start))
            }
            val asset = inlineStickerCatalog[marker.assetKey]
            if (asset != null && stickerTokens.size < INLINE_STICKER_MAX_PER_MESSAGE) {
                val id = "inline_sticker_${stickerIndex}_${marker.assetKey}"
                stickerTokens += StickerInlineToken(
                    id = id,
                    assetKey = marker.assetKey,
                    alt = asset.alt
                )
                appendInlineContent(id, asset.alt)
                stickerIndex += 1
            }
            cursor = marker.endExclusive
        }
        if (cursor < text.length) {
            appendTextWithCitations(text.substring(cursor))
        }
    }

    return CitationInlineRender(
        annotated = annotated,
        citationTokens = citationTokens,
        stickerTokens = stickerTokens
    )
}

private fun findInlineStickerMarkers(text: String): List<InlineStickerMarker> {
    if (text.isBlank()) return emptyList()

    val markers = mutableListOf<InlineStickerMarker>()
    visibleInlineStickerRegex.findAll(text).forEach { match ->
        val key = match.groupValues[1].lowercase()
        if (key in inlineStickerCatalog) {
            markers += InlineStickerMarker(match.range.first, match.range.last + 1, key)
        }
    }

    var index = 0
    while (index < text.length && markers.size < INLINE_STICKER_MAX_PER_MESSAGE * 2) {
        val codePoint = Character.codePointAt(text, index)
        if (codePoint != INLINE_STICKER_TAG_START) {
            index += Character.charCount(codePoint)
            continue
        }

        val markerStart = index
        index += Character.charCount(codePoint)
        val payload = StringBuilder()
        var completed = false
        while (index < text.length) {
            val taggedCodePoint = Character.codePointAt(text, index)
            index += Character.charCount(taggedCodePoint)
            if (taggedCodePoint == INLINE_STICKER_TAG_CANCEL) {
                completed = true
                break
            }
            if (taggedCodePoint !in 0xE0020..0xE007E) {
                payload.clear()
                break
            }
            payload.append((taggedCodePoint - INLINE_STICKER_TAG_BASE).toChar())
        }

        if (completed) {
            val decoded = payload.toString().lowercase()
            val key = when {
                decoded.startsWith(INLINE_STICKER_PAYLOAD_PREFIX) -> {
                    decoded.removePrefix(INLINE_STICKER_PAYLOAD_PREFIX).takeIf { it in inlineStickerCatalog }
                }
                decoded.startsWith(INLINE_STICKER_COMPACT_PREFIX) -> {
                    inlineStickerKeyByCode[decoded.removePrefix(INLINE_STICKER_COMPACT_PREFIX)]
                }
                else -> null
            }
            if (key != null) {
                markers += InlineStickerMarker(markerStart, index, key)
            }
        }
    }

    return markers
        .sortedBy { it.start }
        .fold(mutableListOf<InlineStickerMarker>()) { accepted, marker ->
            if (accepted.size < INLINE_STICKER_MAX_PER_MESSAGE &&
                accepted.none { marker.start < it.endExclusive && marker.endExclusive > it.start }
            ) {
                accepted += marker
            }
            accepted
        }
}

@Composable
private fun CitationBubble(number: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFF8DF9EA).copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number,
            color = Color(0xFF8DF9EA).copy(alpha = 0.92f),
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

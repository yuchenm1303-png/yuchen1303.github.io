// AI_LEDGER_SOURCE_SEGMENT_1_BEGIN
// AI_LEDGER_SOURCE_SEGMENT_1_CONTENT
// AI_LEDGER_SOURCE_SEGMENT_1_END

// AI_LEDGER_SOURCE_SEGMENT_2_BEGIN
// AI_LEDGER_SOURCE_SEGMENT_2_CONTENT
// AI_LEDGER_SOURCE_SEGMENT_2_END

// AI_LEDGER_SOURCE_SEGMENT_3_BEGIN
                OptimizedRichMessageContent(
                    text = displayText,
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                StreamingLivePlainTextV2(
                    text = displayText,
                    revealHead = revealHead,
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    motionClock = motionClock,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SweepingProgressTextV2(
                    text = progressLabel,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    motionClock = motionClock
                )
                ThinkingDotsV2(size = 4, color = Color.White.copy(alpha = 0.62f), motionClock = motionClock)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SweepingProgressTextV2(
                    text = progressLabel,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    motionClock = motionClock
                )
                ThinkingDotsV2(size = 7, color = Color.White.copy(alpha = 0.66f), motionClock = motionClock)
            }
        }
    }
}

@Immutable
private data class FluidStreamingTextVisualStateV2(
    val text: String,
    val revealHead: Float,
    val finished: Boolean
)

@Composable
private fun StreamingLivePlainTextV2(
    text: String,
    revealHead: Float? = null,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    motionClock: AssistantHomeMotionClock,
    modifier: Modifier = Modifier
) {
    val phase = motionClock.phase(1480L)
    val head = remember(text, revealHead) {
        (revealHead ?: text.length.toFloat()).coerceIn(0f, text.length.toFloat())
    }
    val annotated = remember(text, color, head, phase) {
        buildStreamingDevelopingTextV2(
            text = text,
            revealHead = head,
            color = color,
            phase = phase
        )
    }
    Text(
        text = annotated,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        modifier = modifier
    )
}

@Composable
private fun rememberFluidStreamingTextStateV2(
    messageId: String,
    targetText: String,
    enabled: Boolean
): FluidStreamingTextVisualStateV2 {
    var revealHead by remember(messageId) { mutableStateOf(if (enabled) 0f else targetText.length.toFloat()) }

    LaunchedEffect(messageId, targetText, enabled) {
        if (!enabled) {
            revealHead = targetText.length.toFloat()
            return@LaunchedEffect
        }
        if (targetText.isBlank()) {
            revealHead = 0f
            return@LaunchedEffect
        }
        if (revealHead > targetText.length || (revealHead > 1f && !targetText.startsWith(targetText.take(revealHead.toInt().coerceAtMost(targetText.length))))) {
            revealHead = revealHead.coerceIn(0f, targetText.length.toFloat())
        }

        var lastFrameNanos = 0L
        while (revealHead < targetText.length - 0.01f) {
            val frameNanos = withFrameNanos { it }
            val dt = if (lastFrameNanos == 0L) {
                1f / 60f
            } else {
                ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.006f, 0.048f)
            }
            lastFrameNanos = frameNanos

            val backlog = targetText.length - revealHead
            val speed = fluidRevealCharsPerSecondV2(backlog)
            val pauseFactor = fluidPauseFactorV2(targetText, revealHead)
            val next = revealHead + speed * pauseFactor * dt
            revealHead = minOf(targetText.length.toFloat(), next)

            if (targetText.length - revealHead < 0.35f) {
                revealHead = targetText.length.toFloat()
            }
        }
    }

    val head = revealHead.coerceIn(0f, targetText.length.toFloat())
    val previewEnd = fluidPreviewEndV2(targetText, head)
    val textForLayout = remember(targetText, previewEnd) { targetText.take(previewEnd) }
    return FluidStreamingTextVisualStateV2(
        text = textForLayout,
        revealHead = head.coerceAtMost(textForLayout.length.toFloat()),
        finished = head >= targetText.length - 0.01f
    )
}

private fun fluidRevealCharsPerSecondV2(backlog: Float): Float = when {
    backlog >= 220f -> 190f
    backlog >= 120f -> 150f
    backlog >= 56f -> 118f
    backlog >= 20f -> 92f
    else -> 68f
}

private fun fluidPauseFactorV2(text: String, revealHead: Float): Float {
    val index = revealHead.toInt().coerceIn(0, text.length)
    if (index <= 0 || index > text.length) return 1f
    val last = text[index - 1]
    val local = revealHead - index.toFloat()
    val justCrossed = local in 0f..0.38f
    if (!justCrossed) return 1f
    return when (last) {
        '\n' -> 0.36f
        '。', '！', '？', '.', '!', '?' -> 0.48f
        '，', ',', '；', ';', '：', ':' -> 0.68f
        else -> 1f
    }
}

private fun fluidPreviewEndV2(text: String, revealHead: Float): Int {
    if (text.isBlank()) return 0
    val preview = when {
        text.length >= 1200 -> 20
        text.length >= 520 -> 18
        else -> 14
    }
    val base = revealHead.toInt() + if (revealHead > revealHead.toInt().toFloat()) 1 else 0
    return safeStreamingEndV2(text, (base + preview).coerceIn(1, text.length))
}

private fun buildStreamingDevelopingTextV2(
    text: String,
    revealHead: Float,
    color: Color,
    phase: Float
) = buildAnnotatedString {
    if (text.isEmpty()) return@buildAnnotatedString

    val head = revealHead.coerceIn(0f, text.length.toFloat())
    val stableEnd = safeStreamingEndV2(text, (head - 22f).toInt().coerceAtLeast(0))
    if (stableEnd > 0) {
        withStyle(SpanStyle(color = color.copy(alpha = 0.86f))) {
            append(text.substring(0, stableEnd))
        }
    }

    val softStart = stableEnd
    val softEnd = text.length
    val shimmerCenter = 0.34f + 0.32f * sin((phase * 2f * PI).toFloat())
    for (index in softStart until softEnd) {
        val distance = head - index.toFloat()
        val alpha = fluidGlyphAlphaV2(distance)
        if (alpha <= 0.006f) continue

        val glow = if (distance in -2f..10f) {
            (1f - kotlin.math.abs(distance - shimmerCenter * 8f) / 12f).coerceIn(0f, 1f)
        } else {
            0f
        }
        val glyphColor = blendStreamingGlyphColorV2(color, alpha, glow)
        withStyle(SpanStyle(color = glyphColor)) {
            append(text[index])
        }
    }
}

private fun fluidGlyphAlphaV2(distanceFromHead: Float): Float {
    return when {
        distanceFromHead >= 14f -> 0.86f
        distanceFromHead >= 8f -> 0.76f + (distanceFromHead - 8f) / 6f * 0.10f
        distanceFromHead >= 0f -> 0.18f + distanceFromHead / 8f * 0.58f
        distanceFromHead >= -6f -> 0.055f + (distanceFromHead + 6f) / 6f * 0.125f
        distanceFromHead >= -14f -> 0.018f + (distanceFromHead + 14f) / 8f * 0.037f
        else -> 0f
    }.coerceIn(0f, 0.92f)
}

private fun blendStreamingGlyphColorV2(base: Color, alpha: Float, glow: Float): Color {
    val glowAlpha = (0.08f * glow).coerceIn(0f, 0.10f)
    return Color(
        red = (base.red * (1f - glowAlpha) + 1f * glowAlpha).coerceIn(0f, 1f),
        green = (base.green * (1f - glowAlpha) + 1f * glowAlpha).coerceIn(0f, 1f),
        blue = (base.blue * (1f - glowAlpha) + 1f * glowAlpha).coerceIn(0f, 1f),
        alpha = alpha
    )
}

private fun safeStreamingEndV2(text: String, end: Int): Int {
    val safe = end.coerceIn(0, text.length)
    if (safe in 1 until text.length && Character.isHighSurrogate(text[safe - 1]) && Character.isLowSurrogate(text[safe])) {
        return safe + 1
    }
    return safe
}

private fun shouldUseFullRichStreamingV2(text: String): Boolean {
    if (text.length > 360) return false
    val clean = text.trim()
    if (clean.contains("$$") || clean.contains("\\(") || clean.contains("\\[") || clean.contains("```")) return true
    val lines = clean.lines()
    return lines.any { line ->
        val trimmed = line.trim()
        trimmed.length >= 5 && trimmed.startsWith("|") && trimmed.endsWith("|")
    }
}

@Composable
private fun SweepingProgressTextV2(
    text: String,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    motionClock: AssistantHomeMotionClock
) {
    val phase = motionClock.phase(1880L)
    val startX = phase * 420f - 260f
    val brush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.88f),
            Color.White.copy(alpha = 0.78f),
            Color.White.copy(alpha = 0.42f),
            Color.White.copy(alpha = 0.78f),
            Color.White.copy(alpha = 0.88f)
        ),
        start = Offset(startX, 0f),
        end = Offset(startX + 260f, 0f)
    )
    Text(
        text = text,
        style = TextStyle(
            brush = brush,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun rememberCloudProgressLabelV2(messageId: String, hasLiveText: Boolean): String {
    var stage by remember(messageId) { mutableStateOf(0) }
    LaunchedEffect(messageId, hasLiveText) {
        if (hasLiveText) {
            stage = 3
            return@LaunchedEffect
        }
        stage = 0
        delay(760)
        stage = 1
        delay(1320)
        stage = 2
    }
    return if (hasLiveText) {
        "正在接收"
    } else {
        when (stage) {
            0 -> "正在连接云端"
            1 -> "云端处理中"
            else -> "正在整理回复"
        }
    }
}

@Composable
private fun rememberRevealTextStateV2(messageId: String, text: String, enabled: Boolean): Pair<String, Boolean> {
    var visibleCount by remember(messageId, text) { mutableStateOf(if (enabled) 0 else text.length) }
    LaunchedEffect(messageId, text, enabled) {
        if (!enabled) {
            visibleCount = text.length
            return@LaunchedEffect
        }
        visibleCount = 0
        delay(120)
        while (visibleCount < text.length) {
            visibleCount = nextRevealBoundaryV2(text, visibleCount)
            delay(revealDelayV2(text.length, visibleCount))
        }
    }
    val safeCount = visibleCount.coerceIn(0, text.length)
    return text.take(safeCount) to (safeCount >= text.length)
}

private fun nextRevealBoundaryV2(text: String, current: Int): Int {
    if (current >= text.length) return text.length
    val baseStep = when {
        text.length > 1600 -> 180
        text.length > 900 -> 140
        text.length > 420 -> 96
        else -> 68
    }
    val softTarget = (current + baseStep).coerceAtMost(text.length)
    val punctuationWindowEnd = (softTarget + 44).coerceAtMost(text.length)
    val paragraph = text.indexOf('\n', startIndex = softTarget).takeIf { it in softTarget until punctuationWindowEnd }
    if (paragraph != null) return (paragraph + 1).coerceAtMost(text.length)
    val punctuation = findFirstRevealBreakV2(text, softTarget, punctuationWindowEnd)
    if (punctuation > 0) return punctuation.coerceAtMost(text.length)
    return softTarget
}

private fun findFirstRevealBreakV2(text: String, start: Int, end: Int): Int {
    val breaks = setOf('。', '！', '？', '；', '.', '!', '?', ';', '，', ',')
    for (index in start until end) {
        if (text[index] in breaks) return index + 1
    }
    return -1
}

private fun revealDelayV2(total: Int, index: Int): Long = when {
    total > 1600 -> 230L
    total > 900 -> 210L
    total > 420 -> 190L
    index < 120 -> 180L
    else -> 200L
}

@Composable
private fun GeneratingMessageContentV2(
    text: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    active: Boolean,
    motionClock: AssistantHomeMotionClock,
    modifier: Modifier = Modifier
) {
    if (!active) {
        OptimizedRichMessageContent(
            text = text,
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            modifier = modifier
        )
        return
    }
    val phase = motionClock.phase(1450L)
    val tailSize = 150.coerceAtMost(text.length)
    val stableText = text.dropLast(tailSize)
    val fadingText = text.takeLast(tailSize)
    val diagonalStart = phase * 360f - 140f
    val tailBrush = Brush.linearGradient(
        colors = listOf(
            color.copy(alpha = 0.18f),
            color.copy(alpha = 0.38f),
            color.copy(alpha = 0.78f),
            Color.White.copy(alpha = 0.90f),
            color.copy(alpha = 0.64f),
            color.copy(alpha = 0.34f)
        ),
        start = Offset(diagonalStart, 44f),
        end = Offset(diagonalStart + 260f, -20f)
    )
    val annotated = buildAnnotatedString {
        append(stableText)
        withStyle(SpanStyle(brush = tailBrush)) {
            append(fadingText)
        }
    }
    Text(
        text = annotated,
        color = color.copy(alpha = 0.84f),
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        modifier = modifier
    )
}

@Composable
private fun TypewriterTrailV2(motionClock: AssistantHomeMotionClock) {
    val breath = 0.50f + FastOutSlowInEasing.transform(motionClock.pingPong(1280L)) * 0.50f
    Row(
        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = 0.20f + breath * 0.12f },
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "正在生成",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 8.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
        Spacer(Modifier.size(5.dp))
        ThinkingDotsV2(size = 3, color = Color.White.copy(alpha = 0.42f), motionClock = motionClock)
    }
}

@Composable
private fun LongReplyToggleV2(expanded: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (expanded) "收起长回复 ︿" else "展开全文 ﹀",
            color = Color.White.copy(alpha = 0.56f),
            fontSize = 9.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.055f))
                .clickable(onClick = onToggle)
                .padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

private fun hasStreamingLiveTextV2(text: String): Boolean {
    return text.isNotBlank() && !isThinkingPlaceholderV2(text)
}

private fun isThinkingPlaceholderV2(text: String): Boolean {
    val clean = text.trim()
    return clean == "正在思考…" || clean == "正在重新生成…" || clean == "正在思考" || clean == "正在重新生成"
}

private fun isThinkingSweepPlaceholderV2(text: String): Boolean {
    val clean = text.trim()
    return isThinkingPlaceholderV2(clean) ||
        clean == "正在理解视觉附件…" ||
        clean == "正在理解视觉附件" ||
        clean == "正在执行手机智能体任务…" ||
        clean == "正在执行手机智能体任务"
}

@Composable
private fun MessageAttachmentListV2(attachments: List<ChatAttachment>) {
    val visibleAttachments = remember(attachments) { attachments.take(3) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        visibleAttachments.forEach { attachment ->
            val attachmentMeta = remember(attachment.width, attachment.height, attachment.sizeBytes) {
                val dimensions = if (attachment.width != null && attachment.height != null) "${attachment.width}×${attachment.height}" else "图片"
                val size = attachment.sizeBytes?.let { "${max(1, it / 1024)} KB" } ?: "已压缩"
                "$dimensions · $size"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(27.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFF8DF9EA).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("IMG", color = Color(0xFF8DF9EA).copy(alpha = 0.88f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("视觉附件", color = Color.White.copy(alpha = 0.90f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    Text(attachmentMeta, color = Color.White.copy(alpha = 0.52f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun MessageBadgeV2(message: ChatMessage) {
    val text = remember(message.id, message.status, message.modelLabel, message.source, message.version) {
        messageBadgeTextV2(message)
    } ?: return
    val badgeColor = remember(message.id, message.status, message.source, message.model, message.modelLabel, message.hasImageAttachments) {
        badgeColorV2(message)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
// AI_LEDGER_SOURCE_SEGMENT_3_END

// AI_LEDGER_SOURCE_SEGMENT_4_BEGIN
// AI_LEDGER_SOURCE_SEGMENT_4_CONTENT
// AI_LEDGER_SOURCE_SEGMENT_4_END


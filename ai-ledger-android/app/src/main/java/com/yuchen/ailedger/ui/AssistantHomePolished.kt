@Composable
private fun StreamingAssistantContentV2(
    message: ChatMessage,
    motionClock: AssistantHomeMotionClock,
    smoothState: FluidStreamingTextVisualStateV2? = null,
    modifier: Modifier = Modifier
) {
    val targetText = remember(message.id, message.text, message.status, message.errorText) { messageText(message) }
    val hasLiveText = remember(targetText) { hasStreamingLiveTextV2(targetText) }
    val displayText = smoothState?.text?.takeIf { it.isNotBlank() } ?: targetText
    val revealHead = smoothState?.revealHead ?: displayText.length.toFloat()
    val useFullRichStreaming = remember(targetText) { shouldUseFullRichStreamingV2(targetText) }
    val progressLabel = rememberCloudProgressLabelV2(message.id, hasLiveText)
    val agentProgress = remember(displayText, targetText) {
        parseAgentProgressStatusV2(displayText.ifBlank { targetText })
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (agentProgress != null) {
            AgentProgressLoadingV2(
                state = agentProgress,
                motionClock = motionClock,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (hasLiveText) {
            if (useFullRichStreaming) {
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
private data class AgentProgressStatusUiV2(
    val currentTitle: String,
    val currentMeta: String?,
    val currentIcon: String,
    val history: List<String>
)

@Composable
private fun AgentProgressLoadingV2(
    state: AgentProgressStatusUiV2,
    motionClock: AssistantHomeMotionClock,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SweepingProgressTextV2(
                text = "AI 正在工作",
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Black,
                motionClock = motionClock
            )
            ThinkingDotsV2(size = 6, color = Color.White.copy(alpha = 0.66f), motionClock = motionClock)
        }

        state.history.takeLast(3).forEach { line ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = agentProgressIconFromLineV2(line),
                    color = Color(0xFF8DF9EA).copy(alpha = 0.50f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Text(
                    text = agentProgressTitleFromLineV2(line),
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF8DF9EA).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.currentIcon,
                    color = Color(0xFF8DF9EA).copy(alpha = 0.92f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SweepingProgressTextV2(
                    text = state.currentTitle,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    motionClock = motionClock
                )
                state.currentMeta?.takeIf { it.isNotBlank() }?.let { meta ->
                    Text(
                        text = meta,
                        color = Color(0xFF8DF9EA).copy(alpha = 0.62f),
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF8DF9EA).copy(alpha = 0.08f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private fun parseAgentProgressStatusV2(text: String): AgentProgressStatusUiV2? {
    val lines = text.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (lines.none { it.contains("AI 正在工作") }) return null

    val progressLines = lines
        .filterNot { it == "AI 正在工作…" || it == "AI 正在工作..." || it == "AI 正在工作" }
        .distinct()

    val currentRaw = progressLines.lastOrNull() ?: "◐ 正在分析下一步"
    val currentTitle = agentProgressTitleFromLineV2(currentRaw)
    val currentMeta = agentProgressMetaFromLineV2(currentRaw)
    val currentIcon = agentProgressIconFromLineV2(currentRaw)
    val history = progressLines.dropLast(1)

    return AgentProgressStatusUiV2(
        currentTitle = currentTitle,
        currentMeta = currentMeta,
        currentIcon = currentIcon,
        history = history
    )
}

private fun agentProgressTitleFromLineV2(line: String): String {
    val clean = line.trim()
    val withoutIcon = clean
        .removePrefix("▣")
        .removePrefix("◈")
        .removePrefix("◐")
        .removePrefix("◑")
        .removePrefix("⋯")
        .removePrefix("↻")
        .removePrefix("✓")
        .removePrefix("•")
        .trim()
    val splitIndex = withoutIcon.indexOf("    ")
    return if (splitIndex >= 0) {
        withoutIcon.substring(0, splitIndex).trim()
    } else {
        withoutIcon
    }.ifBlank { "正在处理任务" }
}

private fun agentProgressMetaFromLineV2(line: String): String? {
    val clean = line.trim()
    val splitIndex = clean.indexOf("    ")
    if (splitIndex < 0) return null
    return clean.substring(splitIndex).trim().takeIf { it.isNotBlank() }
}

private fun agentProgressIconFromLineV2(line: String): String {
    val clean = line.trim()
    return when {
        clean.startsWith("▣") -> "▣"
        clean.startsWith("◈") -> "◈"
        clean.startsWith("◐") -> "◐"
        clean.startsWith("◑") -> "◑"
        clean.startsWith("⋯") -> "⋯"
        clean.startsWith("↻") -> "↻"
        clean.startsWith("✓") -> "✓"
        else -> "•"
    }
}

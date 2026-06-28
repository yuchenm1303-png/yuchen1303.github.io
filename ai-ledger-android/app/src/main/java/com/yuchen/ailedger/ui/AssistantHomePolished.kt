// AI_LEDGER_SOURCE_SEGMENT_1_BEGIN
// AI_LEDGER_SOURCE_SEGMENT_1_CONTENT
// AI_LEDGER_SOURCE_SEGMENT_1_END

// AI_LEDGER_SOURCE_SEGMENT_2_BEGIN
// AI_LEDGER_SOURCE_SEGMENT_2_CONTENT
// AI_LEDGER_SOURCE_SEGMENT_2_END

// AI_LEDGER_SOURCE_SEGMENT_3_BEGIN
// AI_LEDGER_SOURCE_SEGMENT_3_CONTENT
// AI_LEDGER_SOURCE_SEGMENT_3_END

// AI_LEDGER_SOURCE_SEGMENT_4_BEGIN
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(999.dp)).background(badgeColor.copy(alpha = 0.82f)))
        Text(text, color = badgeColor.copy(alpha = 0.70f), fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MessageUserAttachmentBadgeV2(message: ChatMessage) {
    val attachment = message.attachments.firstOrNull()
    val attachmentMeta = remember(attachment?.width, attachment?.height, attachment?.sizeBytes) {
        val dimensions = if (attachment?.width != null && attachment.height != null) "${attachment.width}×${attachment.height}" else "图片"
        val size = attachment?.sizeBytes?.let { "${max(1, it / 1024)} KB" } ?: "已发送"
        "$dimensions · $size"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xFF8DF9EA).copy(alpha = 0.82f)))
        Text("视觉附件 · $attachmentMeta", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MessageActionsV2(
    message: ChatMessage,
    copyText: String,
    onCopyMessage: (String) -> Unit,
    onRetryMessage: (String) -> Unit
) {
    val retryColor = remember(message.status) {
        if (message.status == MessageStatus.Failed) Color(0xFFFFB4B4) else Color.White.copy(alpha = 0.50f)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        TextActionV2("复制") { onCopyMessage(copyText) }
        Spacer(Modifier.size(12.dp))
        TextActionV2(
            text = "重试",
            color = retryColor
        ) { onRetryMessage(message.id) }
    }
}

@Composable
private fun TextActionV2(text: String, color: Color = Color.White.copy(alpha = 0.50f), onClick: () -> Unit) {
    Text(
        text = text,
        color = color,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun isActionableCloudAssistantMessageV2(message: ChatMessage): Boolean {
    if (message.role != MessageRole.Assistant) return false
    if (message.status == MessageStatus.Sending) return false
    return when (message.source) {
        null, "", "local", "local_ledger", "local_mobile" -> false
        else -> true
    }
}

@Composable
private fun ComposerBarV2(
    state: ComposerBarUiState,
    onComposerChange: (String) -> Unit,
    onSend: () -> Unit,
    onStopGenerating: () -> Unit,
    onPickImage: () -> Unit,
    onComposerFocusChange: (Boolean) -> Unit
) {
    val view = LocalView.current
    val inputMethodManager = remember(view) {
        view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
    }

    val canSubmit = state.canSubmit
    val textIsNotBlank = state.text.isNotBlank()
    val keyboardSendAction = remember(canSubmit, textIsNotBlank, inputMethodManager, view, onSend) {
        if (!canSubmit) {
            {}
        } else {
            {
                if (textIsNotBlank) {
                    inputMethodManager?.hideSoftInputFromWindow(view.windowToken, 0)
                }
                onSend()
            }
        }
    }
    val buttonAction = remember(state.isSending, onStopGenerating, keyboardSendAction) {
        if (state.isSending) onStopGenerating else keyboardSendAction
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        RoundIconButtonV2(
            text = "+",
            quality = state.quality,
            glassIntensity = state.glassIntensity,
            motionIntensity = state.motionIntensity,
            size = 48,
            onClick = onPickImage
        )
        ComposerInputV2(
            state = state,
            onTextChange = onComposerChange,
            onSend = keyboardSendAction,
            onFocusChange = onComposerFocusChange,
            modifier = Modifier.weight(1f)
        )
        SendButtonV2(
            state = state,
            enabled = state.isSending || state.canSubmit,
            onClick = buttonAction
        )
    }
}

@Composable
private fun ComposerInputV2(
    state: ComposerBarUiState,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier
) {
    val attachment = state.attachment
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 999, modifier.height(48.dp), GlassRole.Card) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (attachment != null) {
                InlineComposerAttachmentChipV2(
                    attachment = attachment,
                    modifier = Modifier.weight(0.95f)
                )
            }
            Box(
                modifier = Modifier
                    .weight(if (attachment == null) 1f else 0.82f)
                    .height(40.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = state.text,
                    onValueChange = { if (!state.isSending) onTextChange(it) },
                    singleLine = true,
                    enabled = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(Color.White.copy(alpha = 0.86f)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { onFocusChange(it.isFocused) }
                )
                androidx.compose.animation.AnimatedVisibility(visible = state.text.isBlank(), enter = fadeIn(tween(160)), exit = fadeOut(tween(100))) {
                    Text(state.placeholder, color = Color.White.copy(alpha = 0.42f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun InlineComposerAttachmentChipV2(
    attachment: ComposerAttachment,
    modifier: Modifier = Modifier
) {
    val progress = attachment.progress.coerceIn(0f, 1f)
    val statusText = remember(attachment.status) {
        when (attachment.status) {
            ComposerAttachmentStatus.Preparing -> "准备中"
            ComposerAttachmentStatus.Uploading -> "上传中"
            ComposerAttachmentStatus.Ready -> "已就绪"
            ComposerAttachmentStatus.Failed -> "失败"
        }
    }
    val attachmentMeta = remember(attachment.width, attachment.height, attachment.sizeBytes, progress) {
        val dimensions = if (attachment.width != null && attachment.height != null) {
            "${attachment.width}×${attachment.height}"
        } else {
            "图片"
        }
        val sizeText = attachment.sizeBytes?.let { "${max(1, it / 1024)}KB" } ?: "${(progress * 100).toInt()}%"
        "$dimensions · $sizeText"
    }
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (attachment.status == ComposerAttachmentStatus.Failed) {
                    Color(0xFFFFB4B4).copy(alpha = 0.14f)
                } else {
                    Color(0xFF8DF9EA).copy(alpha = 0.13f)
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "IMG",
                    color = if (attachment.status == ComposerAttachmentStatus.Failed) Color(0xFFFFB4B4) else Color(0xFF8DF9EA),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Text(
                    text = "$statusText · $attachmentMeta",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.10f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceAtLeast(if (attachment.status == ComposerAttachmentStatus.Failed) 1f else 0.08f))
                        .height(2.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (attachment.status == ComposerAttachmentStatus.Failed) {
                                Color(0xFFFFB4B4)
                            } else {
                                Color(0xFF8DF9EA).copy(alpha = 0.84f)
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun SendButtonV2(state: ComposerBarUiState, enabled: Boolean, onClick: () -> Unit) {
    PressableGlass(
        state.quality,
        state.glassIntensity * if (enabled) 1.02f else 0.82f,
        state.motionIntensity,
        999,
        Modifier.size(48.dp),
        if (enabled) GlassRole.Floating else GlassRole.Chip,
        onClick = { if (enabled) onClick() }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (state.isSending) "Ⅱ" else "↑",
                color = Color.White.copy(alpha = if (enabled) 1f else 0.38f),
                fontSize = if (state.isSending) 19.sp else 22.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun RoundIconButtonV2(
    text: String,
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    size: Int = 40,
    onClick: () -> Unit
) {
    PressableGlass(quality, glassIntensity * 0.96f, motionIntensity, 999, Modifier.size(size.dp), GlassRole.Floating, onClick = onClick) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = 0.92f), fontSize = if (text == "+") 25.sp else 15.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ClearChatButtonV2(enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 0.64f else 0.26f
    Text(
        text = "清空",
        color = Color.White.copy(alpha = alpha),
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.075f else 0.035f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        maxLines = 1
    )
}

@Composable
private fun UserMessageActionsV2(copyText: String, onCopyMessage: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        TextActionV2("复制") { onCopyMessage(copyText) }
    }
}

@Composable
private fun ChatStatusV2(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, color = Color.White.copy(alpha = 0.38f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ThinkingDotsV2(size: Int, color: Color, motionClock: AssistantHomeMotionClock) {
    val phase = motionClock.phase(1060L)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val wave = ((sin(phase * 2f * PI.toFloat() + index * 1.34f) + 1f) / 2f).coerceIn(0f, 1f)
            val pulse = ((sin(phase * 2f * PI.toFloat() + index * 1.34f - 0.74f) + 1f) / 2f).coerceIn(0f, 1f)
            Box(
                Modifier
                    .size(size.dp)
                    .graphicsLayer {
                        translationY = -5.6f * wave
                        alpha = 0.54f + 0.46f * wave
                        scaleX = 0.76f + 0.42f * wave
                        scaleY = 0.72f + 0.30f * pulse
                    }
                    .assistantHomeThinkingPearlSurface(color = color, wave = wave, index = index)
            )
        }
    }
}

private fun Modifier.assistantHomeThinkingPearlSurface(color: Color, wave: Float, index: Int): Modifier {
    val highlightAlpha = (0.22f + wave * 0.26f).coerceIn(0f, 0.56f)
    val bodyAlpha = (0.48f + wave * 0.30f).coerceIn(0f, 0.88f)
    val shadowAlpha = (0.18f + index * 0.025f).coerceIn(0f, 0.30f)
    return clip(RoundedCornerShape(999.dp))
        .background(
            Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = highlightAlpha),
                    color.copy(alpha = bodyAlpha),
                    color.copy(alpha = shadowAlpha)
                ),
                center = Offset(3.8f + index * 1.2f, 3.2f),
                radius = 18f + wave * 8f
            )
        )
}

@Composable
private fun PulseDotV2(active: Boolean, color: Color, motionClock: AssistantHomeMotionClock) {
    if (!active) {
        Box(Modifier.size(8.dp).graphicsLayer { alpha = 0.68f }.clip(RoundedCornerShape(999.dp)).background(color))
        return
    }
    val pulse = 0.76f + FastOutSlowInEasing.transform(motionClock.pingPong(860L)) * 0.46f
    Box(Modifier.size(8.dp).graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = 0.96f }.clip(RoundedCornerShape(999.dp)).background(color))
}

private fun messageText(message: ChatMessage): String = when (message.status) {
    MessageStatus.Sending -> message.text.ifBlank { "正在思考…" }
    MessageStatus.Failed -> message.errorText ?: message.text.ifBlank { "云端请求失败，请稍后再试。" }
    MessageStatus.Sent -> stripInlineSourceTextV2(message.text)
}

private fun stripInlineSourceTextV2(text: String): String {
    val sourceBlockIndex = text.indexOf("\n\n联网来源")
    if (sourceBlockIndex >= 0) return text.substring(0, sourceBlockIndex).trimEnd()
    val sourceLineIndex = text.indexOf("\n联网来源")
    if (sourceLineIndex >= 0) return text.substring(0, sourceLineIndex).trimEnd()
    return text
}

private fun messageTextColor(message: ChatMessage, fromUser: Boolean): Color = when (message.status) {
    MessageStatus.Failed -> Color(0xFFFFB4B4)
    MessageStatus.Sending -> Color.White.copy(alpha = 0.78f)
    MessageStatus.Sent -> Color.White.copy(alpha = if (fromUser) 0.96f else 0.86f)
}

private fun messageBadgeTextV2(message: ChatMessage): String? {
    val status = when (message.status) {
        MessageStatus.Sending -> "生成中"
        MessageStatus.Failed -> "请求失败"
        MessageStatus.Sent -> null
    }
    val main = message.modelLabel?.takeIf { it.isNotBlank() } ?: sourceReadableLabelV2(message.source) ?: status
    val source = sourceReadableLabelV2(message.source)
    val version = message.version?.takeIf { it.isNotBlank() }?.removePrefix("2026-")?.removePrefix("android-")?.take(18)
    return listOfNotNull(status, main, source, version).distinct().joinToString(" · ").takeIf { it.isNotBlank() }
}

private fun sourceReadableLabelV2(source: String?): String? = when (source) {
    null, "" -> null
    "cloud_ai" -> "云端 AI"
    "workers_ai", "workers_ai_text_fallback" -> "Workers AI"
    "gemini_ai", "gemini_chat", "gemini_text_fallback" -> "Gemini"
    "qwen", "qwen_chat", "qwen_ai", "dashscope_qwen", "qwen_vision" -> "Qwen"
    "kimi", "nvidia_chat" -> "Qwen"
    "deepseek", "deepseek_chat", "deepseek_v4" -> "DeepSeek"
    "gpt_oss", "nvidia_gpt_oss" -> "GPT OSS"
    "mistral" -> "Mistral"
    "web_search_tool", "tavily_web_search", "tavily_ai_summary" -> "联网搜索"
    "cloud_fetch_failed" -> "云端连接失败"
    "cloud_error_normalized" -> "云端错误"
    "local" -> "本地"
    "local_ledger" -> "本地记账"
    "local_mobile" -> "手机动作"
    else -> source.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun badgeColorV2(message: ChatMessage): Color = when (message.status) {
    MessageStatus.Failed -> Color(0xFFFFB4B4)
    MessageStatus.Sending -> Color(0xFF8DF9EA)
    MessageStatus.Sent -> {
        val signal = modelSignalV2(message)
        when {
            message.source in listOf("web_search_tool", "tavily_web_search", "tavily_ai_summary") -> Color(0xFF8DF9EA)
            message.source in listOf("cloud_fetch_failed", "cloud_error_normalized") -> Color(0xFFFFB4B4)
            message.hasImageAttachments -> Color(0xFF8DF9EA)
            signal.contains("qwen") || signal.contains("kimi") || signal.contains("千问") -> Color(0xFF8DF9EA)
            signal.contains("deepseek") -> Color(0xFF22D3EE)
            signal.contains("gemini") -> Color(0xFF6AE4FF)
            signal.contains("mistral") -> Color(0xFFFFC247)
            signal.contains("worker") || signal.contains("llama") -> Color(0xFFFF7B5C)
            signal.contains("gpt") || signal.contains("oss") -> Color(0xFF34D399)
            else -> Color.White
        }
    }
}

private fun modelSignalV2(message: ChatMessage): String {
    return listOfNotNull(message.model, message.modelLabel, message.source)
        .joinToString(" ")
        .lowercase()
}
// AI_LEDGER_SOURCE_SEGMENT_4_END


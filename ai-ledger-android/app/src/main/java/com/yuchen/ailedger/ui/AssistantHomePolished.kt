// __AI_LEDGER_SOURCE_CHUNK_1__
        if (revealedMessageIds.any { it !in activeMessageIds }) {
            revealedMessageIds = revealedMessageIds.intersect(activeMessageIds)
        }
        if (streamedMessageIds.any { it !in activeMessageIds }) {
            streamedMessageIds = streamedMessageIds.intersect(activeMessageIds)
        }
        if (streamRevealCompletedMessageIds.any { it !in activeMessageIds }) {
            streamRevealCompletedMessageIds = streamRevealCompletedMessageIds.intersect(activeMessageIds)
        }
        if (collapsedLongReplyMessageIds.any { it !in activeMessageIds }) {
            collapsedLongReplyMessageIds = collapsedLongReplyMessageIds.intersect(activeMessageIds)
        }
    }
    val liveStreamingMessageIds = remember(messages) {
        messages
            .filter { message ->
                message.role == MessageRole.Assistant &&
                    message.status == MessageStatus.Sending &&
                    hasStreamingLiveTextV2(messageText(message))
            }
            .map { it.id }
            .toSet()
    }
    LaunchedEffect(liveStreamingMessageIds) {
        if (liveStreamingMessageIds.isNotEmpty()) {
            streamedMessageIds = streamedMessageIds + liveStreamingMessageIds
        }
    }

    val lastMessage = messages.lastOrNull()
    val lastMessageId = lastMessage?.id
    val lastMessageStatus = lastMessage?.status
    val lastMessageIndex = messages.lastIndex
    val lastActionableMessage = remember(messages) {
        messages.lastOrNull { isActionableCloudAssistantMessageV2(it) }
    }
    val lastActionableMessageId = lastActionableMessage?.id
    val hasSendingAssistantMessage = remember(messages) {
        messages.any { it.role == MessageRole.Assistant && it.status == MessageStatus.Sending }
    }
    val pendingStreamMotion = streamedMessageIds.any { it !in streamRevealCompletedMessageIds }
    val pendingRevealMotion = lastActionableMessage?.let { message ->
        message.id !in revealedMessageIds &&
            message.id !in streamedMessageIds &&
            message.status == MessageStatus.Sent &&
            messageText(message).length > 24
    } == true
    val motionClockActive = state.motionIntensity > 0.02f && (
        state.isSending ||
            hasSendingAssistantMessage ||
            pendingStreamMotion ||
            pendingRevealMotion
        )
    val motionClock = rememberAssistantHomeMotionClock(
        motionIntensity = state.motionIntensity,
        active = motionClockActive
    )
    SideEffect { PerformanceRuntimeMetrics.recordAssistantComposition() }

    LaunchedEffect(lastMessageId, state.isSending) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (state.isSending || lastMessageStatus == MessageStatus.Sending) {
            listState.scrollToItem(lastMessageIndex)
        } else {
            listState.animateScrollToItem(lastMessageIndex)
        }
    }

    GlassPanel(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 30,
        modifier = modifier.fillMaxWidth(),
        role = GlassRole.Shell,
        viewportTopInset = viewportTopInset
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(30.dp))
                .clipToBounds()
        ) {
            RainbowChatGlassOverlay(
                quality = state.quality,
                motionIntensity = state.motionIntensity,
                modifier = Modifier.matchParentSize()
            )
            Column(Modifier.fillMaxSize().padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("对话", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    ClearChatButtonV2(
                        enabled = sourceMessages.isNotEmpty(),
                        onClick = onClearMessages
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                ) {
                    ChatBubbleMaterialLayerHost(
                        layerState = bubbleLayerState,
                        listState = listState,
                        messages = messages,
                        motionClock = motionClock,
                        motionIntensity = state.motionIntensity,
                        modifier = Modifier.matchParentSize()
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 0.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            AnimatedMessageBubbleV2(
                                message = message,
                                bubbleLayerState = bubbleLayerState,
                                motionClock = motionClock,
                                showActions = message.id == lastActionableMessageId,
                                revealAlreadyPlayed = message.id in revealedMessageIds || message.id in streamedMessageIds,
                                wasStreamed = message.id in streamedMessageIds,
                                streamRevealAlreadyCompleted = message.id in streamRevealCompletedMessageIds,
                                longReplyExpanded = message.id !in collapsedLongReplyMessageIds,
                                onRevealCompleted = { id -> revealedMessageIds = revealedMessageIds + id },
                                onStreamRevealCompleted = { id -> streamRevealCompletedMessageIds = streamRevealCompletedMessageIds + id },
                                onLongReplyExpandedChange = { id, expanded ->
                                    collapsedLongReplyMessageIds = if (expanded) {
                                        collapsedLongReplyMessageIds - id
                                    } else {
                                        collapsedLongReplyMessageIds + id
                                    }
                                },
                                onCopyMessage = onCopyMessage,
                                onRetryMessage = onRetryMessage
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleMaterialLayerHost(
    layerState: ChatBubbleLayerState,
    listState: LazyListState,
    messages: List<ChatMessage>,
    motionClock: AssistantHomeMotionClock,
    motionIntensity: Float,
    modifier: Modifier = Modifier
) {
    ChatBubbleMaterialLayer(
        layerState = layerState,
        listState = listState,
        messages = messages,
        motionClock = motionClock,
        motionIntensity = motionIntensity,
        modifier = modifier
    )
}

private fun phaseOffsetForMessage(id: String): Float = ((id.hashCode() ushr 1) % 997) / 997f

private fun phaseSpeedForMessage(id: String): Float {
    val bucket = (id.hashCode() ushr 2) % 7
    return 0.82f + bucket * 0.055f
}

@Composable
private fun AnimatedMessageBubbleV2(
    message: ChatMessage,
    bubbleLayerState: ChatBubbleLayerState,
    motionClock: AssistantHomeMotionClock,
    showActions: Boolean,
    revealAlreadyPlayed: Boolean,
    wasStreamed: Boolean,
    streamRevealAlreadyCompleted: Boolean,
    longReplyExpanded: Boolean,
    onRevealCompleted: (String) -> Unit,
    onStreamRevealCompleted: (String) -> Unit,
    onLongReplyExpandedChange: (String, Boolean) -> Unit,
    onCopyMessage: (String) -> Unit,
    onRetryMessage: (String) -> Unit
) {
    val fromUser = message.role == MessageRole.User
    var visible by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(message.id) { visible = true }
    val appear by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.48f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "message-light-bubble-q-appear"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
        MessageBubbleV2(
            message = message,
            bubbleLayerState = bubbleLayerState,
            motionClock = motionClock,
            appear = appear,
            showActions = showActions,
            revealAlreadyPlayed = revealAlreadyPlayed,
            wasStreamed = wasStreamed,
            streamRevealAlreadyCompleted = streamRevealAlreadyCompleted,
            longReplyExpanded = longReplyExpanded,
            onRevealCompleted = onRevealCompleted,
            onStreamRevealCompleted = onStreamRevealCompleted,
            onLongReplyExpandedChange = onLongReplyExpandedChange,
            onCopyMessage = onCopyMessage,
            onRetryMessage = onRetryMessage
        )
    }
}

@Composable
private fun MessageBubbleV2(
    message: ChatMessage,
    bubbleLayerState: ChatBubbleLayerState,
    motionClock: AssistantHomeMotionClock,
    appear: Float = 1f,
    showActions: Boolean,
    revealAlreadyPlayed: Boolean,
    wasStreamed: Boolean,
    streamRevealAlreadyCompleted: Boolean,
    longReplyExpanded: Boolean,
    onRevealCompleted: (String) -> Unit,
    onStreamRevealCompleted: (String) -> Unit,
    onLongReplyExpandedChange: (String, Boolean) -> Unit,
    onCopyMessage: (String) -> Unit,
    onRetryMessage: (String) -> Unit
) {
    val fromUser = message.role == MessageRole.User
    val sending = message.status == MessageStatus.Sending && !fromUser
    val bubbleRadius = if (fromUser) 26 else 28
    val phaseOffset = remember(message.id) { phaseOffsetForMessage(message.id) }
    val speedFactor = remember(message.id) { phaseSpeedForMessage(message.id) }
    val visual = chatBubbleVisualTransform(appear, fromUser)

    val rawText = remember(message.id, message.text, message.status, message.errorText) { messageText(message) }
    val textColor = remember(message.id, message.status, fromUser) { messageTextColor(message, fromUser) }
    val hasLiveStreamingText = remember(rawText) { hasStreamingLiveTextV2(rawText) }
    val streamRevealShouldAnimate = !fromUser && hasLiveStreamingText && !streamRevealAlreadyCompleted && (sending || wasStreamed)
    val smoothStreamingState = rememberFluidStreamingTextStateV2(
        messageId = message.id,
        targetText = rawText,
        enabled = streamRevealShouldAnimate
    )
    val smoothStreamingFinished = streamRevealAlreadyCompleted || smoothStreamingState.finished
    val smoothStreamingActive = !fromUser && hasLiveStreamingText && (streamRevealShouldAnimate || streamRevealAlreadyCompleted || wasStreamed)
    val shouldReveal = !fromUser &&
        !sending &&
        !revealAlreadyPlayed &&
        showActions &&
        message.status == MessageStatus.Sent &&
        rawText.length > 24
    val revealState = rememberRevealTextStateV2(message.id, rawText, shouldReveal)
    val revealedText = revealState.first
    val baseRevealFinished = revealState.second
    val revealFinished = if (smoothStreamingActive) smoothStreamingFinished else baseRevealFinished
    val revealActive = shouldReveal && !baseRevealFinished

    LaunchedEffect(message.id, shouldReveal, baseRevealFinished) {
        if (shouldReveal && baseRevealFinished) onRevealCompleted(message.id)
    }
    LaunchedEffect(message.id, hasLiveStreamingText, streamRevealShouldAnimate, smoothStreamingState.finished) {
        if (hasLiveStreamingText && streamRevealShouldAnimate && smoothStreamingState.finished) {
            onStreamRevealCompleted(message.id)
        }
    }

    val longReply = !fromUser && !sending && rawText.length >= 520
    val expanded = !longReply || longReplyExpanded
    val displayBaseText = when {
        streamRevealShouldAnimate -> smoothStreamingState.text
        streamRevealAlreadyCompleted && hasLiveStreamingText -> rawText
        sending -> rawText
        else -> revealedText
    }
    val displayText = remember(displayBaseText, longReply, expanded) {
        if (longReply && !expanded) displayBaseText.take(420).trimEnd() + "…" else displayBaseText
    }
    val contentAlpha by animateFloatAsState(
        targetValue = if (sending) 0.88f else 1f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "message-content-state-alpha"
    )
    val thinkingSweepActive = sending && !fromUser && isThinkingSweepPlaceholderV2(rawText)
    val thinkingSweepStrength by animateFloatAsState(
        targetValue = if (thinkingSweepActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (sending) 680 else 300,
            easing = FastOutSlowInEasing
        ),
        label = "message-thinking-sweep-strength"
    )

    SideEffect {
        PerformanceRuntimeMetrics.recordMessageBubbleComposition()
        bubbleLayerState.updateBubbleVisual(
            id = message.id,
            fromUser = fromUser,
            status = message.status,
            appear = appear,
            phaseOffset = phaseOffset,
            speedFactor = speedFactor,
            radiusDp = bubbleRadius,
            thinkingSweepStrength = thinkingSweepStrength
        )
    }
    DisposableEffect(message.id) {
        onDispose { bubbleLayerState.removeBubble(message.id) }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (fromUser) 0.76f else 0.90f)
                .graphicsLayer {
                    alpha = visual.alpha
                    transformOrigin = TransformOrigin(visual.originX, visual.originY)
                    scaleX = visual.scaleX
                    scaleY = visual.scaleY
                    translationX = visual.translationX
                    translationY = visual.translationY
                }
                .clip(RoundedCornerShape(bubbleRadius.dp))
        ) {
            Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .animateContentSize(animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (sending) {
                StreamingAssistantContentV2(
                    message = message,
                    motionClock = motionClock,
                    smoothState = if (streamRevealShouldAnimate) smoothStreamingState else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = contentAlpha }
                )
            } else {
                if (streamRevealShouldAnimate && !smoothStreamingFinished) {
                    StreamingLivePlainTextV2(
                        text = displayText,
                        revealHead = smoothStreamingState.revealHead,
                        color = textColor,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = if (fromUser) FontWeight.Bold else FontWeight.Medium,
                        motionClock = motionClock,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = contentAlpha }
                    )
                } else {
                    GeneratingMessageContentV2(
                        text = displayText,
                        color = textColor,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = if (fromUser) FontWeight.Bold else FontWeight.Medium,
                        active = revealActive,
                        motionClock = motionClock,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = contentAlpha }
                    )
                }
                if (revealActive) {
                    TypewriterTrailV2(motionClock)
                }
                if (longReply && revealFinished) {
                    LongReplyToggleV2(expanded = expanded) {
                        onLongReplyExpandedChange(message.id, !expanded)
                    }
                }
            }

            if (message.attachments.isNotEmpty()) {
                MessageAttachmentListV2(message.attachments)
            }

            if (!fromUser) {
                MessageBadgeV2(message)
            } else if (message.hasImageAttachments) {
                MessageUserAttachmentBadgeV2(message)
            }

            if (fromUser && rawText.isNotBlank()) {
                UserMessageActionsV2(copyText = rawText, onCopyMessage = onCopyMessage)
            }

            if (!fromUser && !sending && revealFinished && message.status == MessageStatus.Sent) {
                MessageDataCards(message)
            }

            if (showActions && !fromUser && !sending && revealFinished) {
                MessageActionsV2(message, copyText = rawText, onCopyMessage = onCopyMessage, onRetryMessage = onRetryMessage)
            }
        }
    }
    }
}

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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (hasLiveText) {
            if (useFullRichStreaming) {
// __AI_LEDGER_SOURCE_CHUNK_3__
// __AI_LEDGER_SOURCE_CHUNK_4__

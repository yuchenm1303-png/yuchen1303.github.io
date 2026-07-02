package com.yuchen.ailedger.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

object OperationTraceRedactor {
    private val passwordKeywords = listOf(
        "password", "passcode", "passwd", "pwd", "pin", "密码", "口令", "支付密码", "登录密码",
    )
    private val otpKeywords = listOf(
        "otp", "verification code", "security code", "验证码", "动态码", "短信码", "校验码",
    )
    private val paymentKeywords = listOf(
        "cvv", "cvc", "card number", "bank card", "credit card", "银行卡", "信用卡", "付款", "支付确认",
    )
    private val phonePattern = Regex("(?<!\\d)(?:\\+?86[- ]?)?1[3-9]\\d{9}(?!\\d)")
    private val emailPattern = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val longNumberPattern = Regex("(?<!\\d)\\d{8,19}(?!\\d)")
    private val otpPattern = Regex("(?<!\\d)\\d{4,8}(?!\\d)")

    fun fromEvent(
        event: AccessibilityEvent,
        screenWidth: Int,
        screenHeight: Int,
    ): OperationAccessibilityEventRecord {
        val source = runCatching { event.source }.getOrNull()
        val sourceEvidence = source?.let {
            fromNode(
                node = it,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                forceInputRedaction = event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            )
        }
        val rawEventText = event.text.orEmpty()
            .map(CharSequence::toString)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .take(240)
        val eventEditable = sourceEvidence?.editable == true ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        val sensitiveInput = sourceEvidence?.sensitive == true
        val redactedEventText = when {
            rawEventText.isBlank() -> null
            eventEditable || sensitiveInput -> REDACTED_INPUT
            else -> redactVisibleText(rawEventText)
        }
        val title = event.contentDescription?.toString()
            ?.takeIf(String::isNotBlank)
            ?.let(::redactVisibleText)
            ?: event.className?.toString()?.takeIf(String::isNotBlank)

        return OperationAccessibilityEventRecord(
            capturedAtMillis = System.currentTimeMillis(),
            eventType = event.eventType,
            eventTypeLabel = eventTypeLabel(event.eventType),
            packageName = event.packageName?.toString().orEmpty(),
            className = event.className?.toString()?.takeIf(String::isNotBlank),
            windowTitle = title,
            contentChangeTypes = event.contentChangeTypes,
            source = sourceEvidence,
            eventText = redactedEventText,
            inputLengthBucket = if (eventEditable) lengthBucket(rawEventText.length) else null,
            redactionApplied = eventEditable || sensitiveInput || redactedEventText != rawEventText,
            scrollDeltaX = event.scrollDeltaX,
            scrollDeltaY = event.scrollDeltaY,
            scrollX = event.scrollX,
            scrollY = event.scrollY,
            maxScrollX = event.maxScrollX,
            maxScrollY = event.maxScrollY,
            fromIndex = event.fromIndex,
            toIndex = event.toIndex,
            itemCount = event.itemCount,
        )
    }

    fun fromNode(
        node: AccessibilityNodeInfo,
        screenWidth: Int,
        screenHeight: Int,
        forceInputRedaction: Boolean = false,
    ): OperationNodeEvidence {
        val rawText = node.text?.toString().orEmpty()
        val rawDescription = node.contentDescription?.toString().orEmpty()
        val rawHint = node.hintText?.toString().orEmpty()
        val combined = listOf(rawText, rawDescription, rawHint, node.className?.toString().orEmpty())
            .joinToString(" ")
        val editable = node.isEditable || forceInputRedaction
        val password = runCatching { node.isPassword }.getOrDefault(false)
        val passwordHint = containsAnyKeyword(combined, passwordKeywords)
        val otpHint = containsAnyKeyword(combined, otpKeywords)
        val paymentHint = containsAnyKeyword(combined, paymentKeywords)
        val riskHints = buildSet {
            if (password || passwordHint) add("password")
            if (otpHint) add("otp")
            if (paymentHint) add("payment")
        }
        val looksLikeOtpValue = editable && otpHint && otpPattern.containsMatchIn(rawText.trim())
        val sensitive = password || (editable && (passwordHint || otpHint || paymentHint || looksLikeOtpValue))
        val bounds = Rect().also { rect -> runCatching { node.getBoundsInScreen(rect) } }

        return OperationNodeEvidence(
            viewId = runCatching { node.viewIdResourceName }.getOrNull()?.takeIf(String::isNotBlank),
            className = node.className?.toString()?.takeIf(String::isNotBlank),
            role = inferRole(node),
            text = sanitizeNodeField(rawText, editable || sensitive),
            contentDescription = sanitizeNodeField(rawDescription, sensitive),
            hint = sanitizeNodeField(rawHint, sensitive),
            bounds = bounds.toCompactBounds(),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            clickable = node.isClickable,
            longClickable = node.isLongClickable,
            editable = editable,
            scrollable = node.isScrollable,
            password = password,
            sensitive = sensitive,
            inputLengthBucket = if (editable) lengthBucket(rawText.length) else null,
            riskHints = riskHints,
        )
    }

    fun redactVisibleText(value: String): String {
        return value
            .replace(emailPattern, "[email-redacted]")
            .replace(phonePattern, "[phone-redacted]")
            .replace(longNumberPattern, "[number-redacted]")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_VISIBLE_TEXT_LENGTH)
    }

    fun containsCredentialHint(value: String): Boolean {
        return containsAnyKeyword(value, passwordKeywords) || containsAnyKeyword(value, otpKeywords)
    }

    fun containsPaymentHint(value: String): Boolean = containsAnyKeyword(value, paymentKeywords)

    private fun sanitizeNodeField(value: String, redact: Boolean): String? {
        if (value.isBlank()) return null
        return if (redact) REDACTED_INPUT else redactVisibleText(value)
    }

    private fun containsAnyKeyword(value: String, keywords: List<String>): Boolean {
        return keywords.any { keyword -> containsKeyword(value, keyword) }
    }

    private fun containsKeyword(value: String, keyword: String): Boolean {
        val normalizedValue = value.lowercase()
        val normalizedKeyword = keyword.lowercase()
        return if (normalizedKeyword.all { it.isLetterOrDigit() && it.code < 128 }) {
            Regex("(?<![a-z0-9])${Regex.escape(normalizedKeyword)}(?![a-z0-9])")
                .containsMatchIn(normalizedValue)
        } else {
            normalizedValue.contains(normalizedKeyword)
        }
    }

    private fun inferRole(node: AccessibilityNodeInfo): String? {
        val className = node.className?.toString().orEmpty()
        return when {
            node.isEditable || className.contains("EditText", ignoreCase = true) -> "TextField"
            className.contains("CheckBox", ignoreCase = true) -> "CheckBox"
            className.contains("Switch", ignoreCase = true) -> "Switch"
            className.contains("Button", ignoreCase = true) || node.isClickable -> "Button"
            node.isScrollable -> "Scrollable"
            className.isNotBlank() -> className.substringAfterLast('.')
            else -> null
        }
    }

    private fun eventTypeLabel(type: Int): String = when (type) {
        AccessibilityEvent.TYPE_VIEW_CLICKED -> "view_clicked"
        AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "view_long_clicked"
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "view_text_changed"
        AccessibilityEvent.TYPE_VIEW_SCROLLED -> "view_scrolled"
        AccessibilityEvent.TYPE_VIEW_FOCUSED -> "view_focused"
        AccessibilityEvent.TYPE_VIEW_SELECTED -> "view_selected"
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "window_state_changed"
        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "windows_changed"
        else -> "event_$type"
    }

    private fun lengthBucket(length: Int): String = when {
        length <= 0 -> "0"
        length <= 4 -> "1-4"
        length <= 8 -> "5-8"
        length <= 16 -> "9-16"
        length <= 32 -> "17-32"
        else -> "33+"
    }

    private const val REDACTED_INPUT = "[input-redacted]"
    private const val MAX_VISIBLE_TEXT_LENGTH = 160
}

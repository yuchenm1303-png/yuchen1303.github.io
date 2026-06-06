package com.yuchen.ailedger.ui

internal fun String?.trim(): String {
    val source = this ?: return ""
    var start = 0
    var end = source.length
    while (start < end && source[start].isWhitespace()) start++
    while (end > start && source[end - 1].isWhitespace()) end--
    return source.substring(start, end)
}

internal fun String?.contains(other: CharSequence, ignoreCase: Boolean = false): Boolean {
    val source = this ?: return false
    return source.indexOf(other.toString(), startIndex = 0, ignoreCase = ignoreCase) >= 0
}

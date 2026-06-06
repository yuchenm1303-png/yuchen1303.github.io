package com.yuchen.ailedger.ui

internal fun String?.trim(): String = this?.let { value -> kotlin.text.trim(value).toString() }.orEmpty()

internal fun String?.contains(other: CharSequence, ignoreCase: Boolean = false): Boolean {
    return this?.contains(other, ignoreCase = ignoreCase) == true
}

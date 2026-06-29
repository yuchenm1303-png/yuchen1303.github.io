package com.yuchen.ailedger.service

/** Small allocation-free indexed count used by the execution transaction watchdog. */
internal inline fun <T> Iterable<T>.countIndexed(predicate: (index: Int, value: T) -> Boolean): Int {
    var index = 0
    var count = 0
    for (value in this) {
        if (predicate(index, value)) count += 1
        index += 1
    }
    return count
}

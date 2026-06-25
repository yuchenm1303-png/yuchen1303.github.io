package com.yuchen.ailedger.ui

/**
 * 普通 Compose 玻璃父绘制热路径的无分配签名重载。
 *
 * 原有 vararg 入口继续保留给低频调用；当前几条固定参数热路径会优先解析到这些重载，
 * 避免每次校验 Path / Brush 缓存时临时创建 IntArray。
 */
private const val OrdinaryParentSignatureSeed = 1125899906842597L

private inline fun appendOrdinaryParentSignature(signature: Long, value: Int): Long =
    signature * 31L + value.toLong()

internal fun ordinaryParentSignatureOf(
    value1: Int,
    value2: Int,
    value3: Int,
    value4: Int,
    value5: Int
): Long {
    var result = OrdinaryParentSignatureSeed
    result = appendOrdinaryParentSignature(result, value1)
    result = appendOrdinaryParentSignature(result, value2)
    result = appendOrdinaryParentSignature(result, value3)
    result = appendOrdinaryParentSignature(result, value4)
    return appendOrdinaryParentSignature(result, value5)
}

internal fun ordinaryParentSignatureOf(
    value1: Int,
    value2: Int,
    value3: Int,
    value4: Int,
    value5: Int,
    value6: Int,
    value7: Int,
    value8: Int,
    value9: Int
): Long {
    var result = OrdinaryParentSignatureSeed
    result = appendOrdinaryParentSignature(result, value1)
    result = appendOrdinaryParentSignature(result, value2)
    result = appendOrdinaryParentSignature(result, value3)
    result = appendOrdinaryParentSignature(result, value4)
    result = appendOrdinaryParentSignature(result, value5)
    result = appendOrdinaryParentSignature(result, value6)
    result = appendOrdinaryParentSignature(result, value7)
    result = appendOrdinaryParentSignature(result, value8)
    return appendOrdinaryParentSignature(result, value9)
}

internal fun ordinaryParentSignatureOf(
    value1: Int,
    value2: Int,
    value3: Int,
    value4: Int,
    value5: Int,
    value6: Int,
    value7: Int,
    value8: Int,
    value9: Int,
    value10: Int,
    value11: Int,
    value12: Int
): Long {
    var result = OrdinaryParentSignatureSeed
    result = appendOrdinaryParentSignature(result, value1)
    result = appendOrdinaryParentSignature(result, value2)
    result = appendOrdinaryParentSignature(result, value3)
    result = appendOrdinaryParentSignature(result, value4)
    result = appendOrdinaryParentSignature(result, value5)
    result = appendOrdinaryParentSignature(result, value6)
    result = appendOrdinaryParentSignature(result, value7)
    result = appendOrdinaryParentSignature(result, value8)
    result = appendOrdinaryParentSignature(result, value9)
    result = appendOrdinaryParentSignature(result, value10)
    result = appendOrdinaryParentSignature(result, value11)
    return appendOrdinaryParentSignature(result, value12)
}

package com.yuchen.ailedger.ui

private const val OrdinaryGlassSignatureSeed = 1125899906842597L

private fun mixOrdinaryGlassSignature(current: Long, value: Int): Long =
    current * 31L + value.toLong()

/** 固定参数重载避免缓存命中路径因 vararg 生成临时 IntArray。 */
internal fun ordinaryParentSignatureOf(
    value1: Int,
    value2: Int,
    value3: Int,
    value4: Int,
    value5: Int
): Long {
    var result = OrdinaryGlassSignatureSeed
    result = mixOrdinaryGlassSignature(result, value1)
    result = mixOrdinaryGlassSignature(result, value2)
    result = mixOrdinaryGlassSignature(result, value3)
    result = mixOrdinaryGlassSignature(result, value4)
    result = mixOrdinaryGlassSignature(result, value5)
    return result
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
    var result = OrdinaryGlassSignatureSeed
    result = mixOrdinaryGlassSignature(result, value1)
    result = mixOrdinaryGlassSignature(result, value2)
    result = mixOrdinaryGlassSignature(result, value3)
    result = mixOrdinaryGlassSignature(result, value4)
    result = mixOrdinaryGlassSignature(result, value5)
    result = mixOrdinaryGlassSignature(result, value6)
    result = mixOrdinaryGlassSignature(result, value7)
    result = mixOrdinaryGlassSignature(result, value8)
    result = mixOrdinaryGlassSignature(result, value9)
    return result
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
    var result = OrdinaryGlassSignatureSeed
    result = mixOrdinaryGlassSignature(result, value1)
    result = mixOrdinaryGlassSignature(result, value2)
    result = mixOrdinaryGlassSignature(result, value3)
    result = mixOrdinaryGlassSignature(result, value4)
    result = mixOrdinaryGlassSignature(result, value5)
    result = mixOrdinaryGlassSignature(result, value6)
    result = mixOrdinaryGlassSignature(result, value7)
    result = mixOrdinaryGlassSignature(result, value8)
    result = mixOrdinaryGlassSignature(result, value9)
    result = mixOrdinaryGlassSignature(result, value10)
    result = mixOrdinaryGlassSignature(result, value11)
    result = mixOrdinaryGlassSignature(result, value12)
    return result
}

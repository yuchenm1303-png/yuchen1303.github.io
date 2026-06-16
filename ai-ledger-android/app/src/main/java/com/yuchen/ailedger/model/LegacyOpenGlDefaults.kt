package com.yuchen.ailedger.model

/**
 * 玻璃实验室“原版 OpenGL”样本使用的固定光学参数。
 * 设置页顶部状态卡片也复用这一份，避免同为旧版 Renderer 却因参数源不同而产生两套材质。
 */
fun legacyOpenGlReferenceStyle(): GlassBorderStyle = GlassBorderStyle(
    ringWidthDp = 8.295f,
    edgePullDp = -199.078f,
    edgeBrightness = 1.083f,
    openGlVisibility = 19.954f,
    openGlMaxAlpha = 1f,
    openGlPullScale = -5.53f,
    openGlCompressionScale = -10f,
    openGlCornerScale = 54.378f,
    openGlDarkScale = -2.21f,
    openGlSampleRadiusScale = 66.359f
)

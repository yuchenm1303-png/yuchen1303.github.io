package com.yuchen.ailedger.model

/**
 * 新版 OpenGL 玻璃当前定型的色散默认值。
 * 只覆盖色散，不改主体折射、圆肩映射或其他玻璃参数。
 */
fun latestOpenGlDefaultBorderStyle(): GlassBorderStyle = GlassBorderStyle(
    newOpenGlDispersionStrength = 1.5f,
    newOpenGlDispersionDistanceDp = 3.272f,
    newOpenGlDispersionEdgeWidthDp = 54.324f,
    newOpenGlDispersionConcentration = 3.33f
)

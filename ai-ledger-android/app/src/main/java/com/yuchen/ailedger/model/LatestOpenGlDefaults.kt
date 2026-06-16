package com.yuchen.ailedger.model

/**
 * 新版 OpenGL 玻璃当前定型默认值。
 * 统一用于所有新版 OpenGL Shell 的初始主体折射、圆肩与色散参数。
 */
fun latestOpenGlDefaultBorderStyle(): GlassBorderStyle = GlassBorderStyle(
    newOpenGlBodyLensBasePull = 0f,
    newOpenGlBodyLensPullDp = 0f,
    newOpenGlShoulderMaterialStrength = 1.5f,
    newOpenGlDispersionStrength = 1.5f,
    newOpenGlDispersionDistanceDp = 3.272f,
    newOpenGlDispersionEdgeWidthDp = 54.324f,
    newOpenGlDispersionConcentration = 3.33f
)

package com.yuchen.ailedger.ui.gl

/**
 * Host 侧已经把几何、采样与按压合并为同一份 VSync 快照。
 * Renderer 继续复用现有 dirty-mask setter，确保第一阶段不触碰任何 Shader 或视觉公式。
 */
internal fun WebOpenGLGlassRenderer.setFrameState(
    width: Float,
    height: Float,
    rectOffsetY: Float,
    radius: Float,
    intensity: Float,
    originX: Float,
    originY: Float,
    rootWidth: Float,
    rootHeight: Float,
    pressProgress: Float,
    pressCenterX: Float,
    pressCenterY: Float,
    geometryDirty: Boolean,
    samplingDirty: Boolean,
    pressDirty: Boolean,
) {
    if (geometryDirty) setGlassSpec(width, height, rectOffsetY, radius, intensity)
    if (samplingDirty) setSamplingSpec(originX, originY, rootWidth, rootHeight)
    if (pressDirty) setPressSpec(pressProgress, pressCenterX, pressCenterY)
}

internal fun LegacyOpenGLGlassRenderer.setFrameState(
    width: Float,
    height: Float,
    rectOffsetY: Float,
    radius: Float,
    originX: Float,
    originY: Float,
    rootWidth: Float,
    rootHeight: Float,
    pressProgress: Float,
    pressCenterX: Float,
    pressCenterY: Float,
    geometryDirty: Boolean,
    samplingDirty: Boolean,
    pressDirty: Boolean,
) {
    if (geometryDirty) setGlassSpec(width, height, rectOffsetY, radius)
    if (samplingDirty) setSamplingSpec(originX, originY, rootWidth, rootHeight)
    if (pressDirty) setPressSpec(pressProgress, pressCenterX, pressCenterY)
}

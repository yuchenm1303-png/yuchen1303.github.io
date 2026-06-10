package com.yuchen.ailedger.ui.gl

/**
 * OpenGL glass cleanup closure marker.
 *
 * Final closure state:
 * - The glass lab OpenGL preview uses GlassRole.Shell through the normal glass entry path.
 * - The OpenGL lab foldout is collapsed by default, so the Shell preview is created only after expansion.
 * - openGlEdgeWidthScale, openGlSpecularScale and openGlChromaticScale were removed from GlassBorderStyle.
 * - GlassBorderStyle.bodyAlpha remains as a compatibility field for the legacy unified backdrop and viewport chains.
 * - The OpenGL lab does not expose bodyAlpha as a tunable OpenGL parameter.
 * - ModelCardGlassStyle.bodyAlpha is unrelated and remains available for model-card glass tuning.
 */
internal object OpenGLGlassCleanupNotes {
    const val VERSION: Int = 2
}

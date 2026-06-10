package com.yuchen.ailedger.ui.gl

/**
 * OpenGL glass cleanup marker.
 *
 * Current closure state:
 * - The glass lab OpenGL preview uses GlassRole.Shell through the normal glass entry path.
 * - openGlEdgeWidthScale, openGlSpecularScale and openGlChromaticScale were removed from GlassBorderStyle.
 * - GlassBorderStyle.bodyAlpha remains as a compatibility field for the legacy unified backdrop and viewport chains.
 * - The OpenGL lab does not expose bodyAlpha as a tunable OpenGL parameter.
 */
internal object OpenGLGlassCleanupNotes {
    const val VERSION: Int = 1
}

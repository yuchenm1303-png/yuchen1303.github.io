package com.yuchen.ailedger.ui

/**
 * First locked-in Frost defaults for ordinary Compose glass.
 *
 * The lab exposes useful knobs only. Removed/low-value knobs stay fixed here:
 * frost, tint, edgeDepth, innerBevel, sideBevel, shadow.
 */
object ComposeGlassRuntimeDefaults {
    const val backdrop = 1.22f
    const val backdropBlur = 0.92f
    const val backdropDim = 0.38f
    const val backdropMilk = 0.86f
    const val backdropHighlight = 0.72f

    const val frost = 0.15f
    const val tint = 0.00f
    const val quiet = 1.40f
    const val bodyAbsorption = 0.46f
    const val lowerBodyMass = 0.34f
    const val innerTransition = 0.24f

    const val topLight = 1.21f
    const val topWidthDp = 0.05f
    const val topVariation = 0.00f
    const val bottomLight = 1.27f
    const val bottomWidthDp = 0.05f

    const val edgeDepthDp = 0.00f
    const val innerBevel = 0.00f
    const val outerRim = 0.26f
    const val bottomMass = 0.54f
    const val sideBevel = 0.13f
    const val sideLight = 0.00f

    const val radius = 37.15f
    const val shadow = 0.27f
    const val ribbon = 0.00f
}

package com.yuchen.ailedger.ui

/**
 * Frost 普通 Compose 玻璃的生产默认值。
 *
 * 背景纹理已经使用高斯近似预模糊，材质层不再依靠厚重白罩伪造模糊。这里保留足够的
 * 体积吸收和上下边缘质量，同时降低白底网页上最容易产生“奶白塑料板”的 Milk/Highlight。
 * 固定项仍然不进入 OpenGL，也不触发任何 OpenGL registry 或 geometry sync。
 */
object ComposeGlassRuntimeDefaults {
    const val backdrop = 1.16f
    const val backdropBlur = 1.02f
    const val backdropDim = 0.31f
    const val backdropMilk = 0.62f
    const val backdropHighlight = 0.58f

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

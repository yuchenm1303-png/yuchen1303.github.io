package com.yuchen.ailedger.ui.gl

internal data class LegacyOpenGLGlassCacheFrame(
    val viewportWidth: Int,
    val viewportHeight: Int,
    val rectWidth: Float,
    val rectHeight: Float,
    val rectOffsetY: Float,
    val radius: Float,
    val originX: Float,
    val originY: Float,
    val rootWidth: Float,
    val rootHeight: Float,
    val pressProgress: Float,
    val pressCenterX: Float,
    val pressCenterY: Float,
    val materialVisibility: Float,
    val materialMaxAlpha: Float,
    val materialEdgeBrightness: Float,
    val refractionPullScale: Float,
    val refractionEdgePullDp: Float,
    val refractionCompressionScale: Float,
    val refractionCornerScale: Float,
    val opticsSampleRadius: Float,
    val opticsRingWidth: Float,
    val opticsDebugAlpha: Float,
    val opticsDarkScale: Float,
    val texturesReady: Boolean,
    val blurTextureId: Int,
    val lensTextureId: Int,
    val scissorLeft: Int,
    val scissorTop: Int,
    val scissorRight: Int,
    val scissorBottom: Int,
)

/**
 * 旧版 OpenGL 玻璃始终使用原始 Shader 直绘。
 *
 * half-float 几何 FBO 在部分设备的静态小型 Shell 上能够成功创建，但合成结果会
 * 变为全透明；动态聊天 Shell 因几何持续变化而经常绕过缓存，所以问题只在设置页
 * 稳定后暴露。这里彻底关闭该不可靠路径，避免依赖设备驱动对 GLES2 half-float
 * color attachment 的非一致行为。
 *
 * Renderer 的单次状态锁、dirty mask、VSync 合并、纹理复用和 Scissor 复用仍然保留。
 * 原 Shader、分辨率、采样次数和所有视觉参数均不改变。
 */
internal class LegacyOpenGLGlassGeometryCache {
    fun onSurfaceCreated() = Unit

    @Suppress("UNUSED_PARAMETER")
    fun onSurfaceChanged(width: Int, height: Int) = Unit

    fun invalidate() = Unit

    @Suppress("UNUSED_PARAMETER")
    fun drawFrame(
        frame: LegacyOpenGLGlassCacheFrame,
        quadBufferId: Int,
        geometryInvalidatedThisFrame: Boolean,
    ): Boolean = false

    fun onRelease() = Unit
}

package com.yuchen.ailedger.ui.gl

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val GL_HALF_FLOAT_OES = 0x8D61
private const val GEOMETRY_TEXTURE_UNIT = GLES20.GL_TEXTURE2

internal class LegacyOpenGLGlassCacheFrame {
    var viewportWidth: Int = 1
    var viewportHeight: Int = 1
    var rectWidth: Float = 1f
    var rectHeight: Float = 1f
    var rectOffsetY: Float = 0f
    var radius: Float = 1f
    var originX: Float = 0f
    var originY: Float = 0f
    var rootWidth: Float = 1f
    var rootHeight: Float = 1f
    var pressProgress: Float = 0f
    var pressCenterX: Float = 0.5f
    var pressCenterY: Float = 0.5f
    var materialVisibility: Float = 0f
    var materialMaxAlpha: Float = 0f
    var materialEdgeBrightness: Float = 0f
    var refractionPullScale: Float = 0f
    var refractionEdgePullDp: Float = 0f
    var refractionCompressionScale: Float = 0f
    var refractionCornerScale: Float = 0f
    var opticsSampleRadius: Float = 0f
    var opticsRingWidth: Float = 0f
    var opticsDebugAlpha: Float = 0f
    var opticsDarkScale: Float = 0f
    var texturesReady: Boolean = false
    var blurTextureId: Int = 0
    var lensTextureId: Int = 0
    var scissorLeft: Int = 0
    var scissorTop: Int = 0
    var scissorRight: Int = 0
    var scissorBottom: Int = 0
}

/**
 * 旧版玻璃的无损几何缓存。
 *
 * 只在支持可渲染 RGBA half-float 的 GLES2 设备上启用。缓存内容为 mask、厚度梯度
 * 和内部距离，保持原 Shader 的几何公式；背景采样、按压和材质合成仍使用原精度逐帧
 * 执行。任何扩展、FBO 或 Shader 初始化失败都会永久回退到原直绘路径。
 */
internal class LegacyOpenGLGlassGeometryCache {
    private var supported = false
    private var permanentlyDisabled = false
    private var cacheValid = false
    private var geometryValidationPending = true
    private var compositeValidationPending = true
    private var compositeUniformsInitialized = false

    private var geometryProgram = 0
    private var compositeProgram = 0
    private var geometryPositionHandle = -1
    private var geometryResolutionHandle = -1
    private var geometryRectHandle = -1
    private var geometryRadiusHandle = -1
    private var geometryOpticsHandle = -1

    private var compositePositionHandle = -1
    private var compositeResolutionHandle = -1
    private var compositeCardOriginHandle = -1
    private var compositeRootResolutionHandle = -1
    private var compositeRectHandle = -1
    private var compositeRadiusHandle = -1
    private var compositePressHandle = -1
    private var compositeTextureReadyHandle = -1
    private var compositeBlurTextureHandle = -1
    private var compositeLensTextureHandle = -1
    private var compositeGeometryTextureHandle = -1
    private var compositeMaterialHandle = -1
    private var compositeRefractionHandle = -1
    private var compositeOpticsHandle = -1

    private var framebufferId = 0
    private var geometryTextureId = 0
    private var textureWidth = 0
    private var textureHeight = 0

    private var lastCompositeViewportWidth = 0
    private var lastCompositeViewportHeight = 0
    private var lastCompositeOriginX = 0f
    private var lastCompositeOriginY = 0f
    private var lastCompositeRootWidth = 0f
    private var lastCompositeRootHeight = 0f
    private var lastCompositeRectWidth = 0f
    private var lastCompositeRectHeight = 0f
    private var lastCompositeRectOffsetY = 0f
    private var lastCompositeRadius = 0f
    private var lastCompositePressProgress = 0f
    private var lastCompositePressCenterX = 0f
    private var lastCompositePressCenterY = 0f
    private var lastCompositeTexturesReady = false
    private var lastCompositeMaterialVisibility = 0f
    private var lastCompositeMaterialMaxAlpha = 0f
    private var lastCompositeMaterialEdgeBrightness = 0f
    private var lastCompositeRefractionPullScale = 0f
    private var lastCompositeRefractionEdgePullDp = 0f
    private var lastCompositeRefractionCompressionScale = 0f
    private var lastCompositeRefractionCornerScale = 0f
    private var lastCompositeOpticsSampleRadius = 0f
    private var lastCompositeOpticsRingWidth = 0f
    private var lastCompositeOpticsDebugAlpha = 0f
    private var lastCompositeOpticsDarkScale = 0f

    fun onSurfaceCreated() {
        releaseProgramsAndFramebuffer()
        permanentlyDisabled = false
        cacheValid = false
        geometryValidationPending = true
        compositeValidationPending = true
        compositeUniformsInitialized = false

        val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS).orEmpty()
        val supportsHalfTexture = extensions.contains("GL_OES_texture_half_float")
        val supportsHalfColorBuffer =
            extensions.contains("GL_EXT_color_buffer_half_float") ||
                extensions.contains("GL_EXT_color_buffer_float")
        if (!supportsHalfTexture || !supportsHalfColorBuffer) {
            permanentlyDisabled = true
            return
        }

        try {
            geometryProgram = buildCacheProgram(CACHE_VERTEX_SHADER, GEOMETRY_FRAGMENT_SHADER)
            compositeProgram = buildCacheProgram(CACHE_VERTEX_SHADER, COMPOSITE_FRAGMENT_SHADER)
            geometryPositionHandle = GLES20.glGetAttribLocation(geometryProgram, "aPosition")
            geometryResolutionHandle = GLES20.glGetUniformLocation(geometryProgram, "uResolution")
            geometryRectHandle = GLES20.glGetUniformLocation(geometryProgram, "uRect")
            geometryRadiusHandle = GLES20.glGetUniformLocation(geometryProgram, "uRadius")
            geometryOpticsHandle = GLES20.glGetUniformLocation(geometryProgram, "uOptics")

            compositePositionHandle = GLES20.glGetAttribLocation(compositeProgram, "aPosition")
            compositeResolutionHandle = GLES20.glGetUniformLocation(compositeProgram, "uResolution")
            compositeCardOriginHandle = GLES20.glGetUniformLocation(compositeProgram, "uCardOrigin")
            compositeRootResolutionHandle = GLES20.glGetUniformLocation(compositeProgram, "uRootResolution")
            compositeRectHandle = GLES20.glGetUniformLocation(compositeProgram, "uRect")
            compositeRadiusHandle = GLES20.glGetUniformLocation(compositeProgram, "uRadius")
            compositePressHandle = GLES20.glGetUniformLocation(compositeProgram, "uPress")
            compositeTextureReadyHandle = GLES20.glGetUniformLocation(compositeProgram, "uTextureReady")
            compositeBlurTextureHandle = GLES20.glGetUniformLocation(compositeProgram, "uBlurTexture")
            compositeLensTextureHandle = GLES20.glGetUniformLocation(compositeProgram, "uLensTexture")
            compositeGeometryTextureHandle = GLES20.glGetUniformLocation(compositeProgram, "uGeometryTexture")
            compositeMaterialHandle = GLES20.glGetUniformLocation(compositeProgram, "uMaterial")
            compositeRefractionHandle = GLES20.glGetUniformLocation(compositeProgram, "uRefraction")
            compositeOpticsHandle = GLES20.glGetUniformLocation(compositeProgram, "uOptics")

            GLES20.glUseProgram(compositeProgram)
            GLES20.glUniform1i(compositeBlurTextureHandle, 0)
            GLES20.glUniform1i(compositeLensTextureHandle, 1)
            GLES20.glUniform1i(compositeGeometryTextureHandle, 2)
            supported = true
        } catch (_: Throwable) {
            permanentlyDisabled = true
            supported = false
            releaseProgramsAndFramebuffer()
        }
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        cacheValid = false
        if (!supported || permanentlyDisabled) return
        if (width != textureWidth || height != textureHeight) {
            releaseFramebuffer()
        }
    }

    fun invalidate() {
        cacheValid = false
    }

    fun drawFrame(
        frame: LegacyOpenGLGlassCacheFrame,
        quadBufferId: Int,
        geometryInvalidatedThisFrame: Boolean,
    ): Boolean {
        if (!supported || permanentlyDisabled || frame.opticsDebugAlpha > 0.0001f) return false
        if (geometryInvalidatedThisFrame) {
            cacheValid = false
            return false
        }
        if (!ensureFramebuffer(frame.viewportWidth, frame.viewportHeight)) return false

        if (!cacheValid && !renderGeometry(frame, quadBufferId)) {
            disablePermanently()
            return false
        }
        if (!renderComposite(frame, quadBufferId)) {
            disablePermanently()
            return false
        }
        return true
    }

    fun onRelease() {
        releaseProgramsAndFramebuffer()
        supported = false
        permanentlyDisabled = false
        cacheValid = false
        geometryValidationPending = true
        compositeValidationPending = true
        compositeUniformsInitialized = false
    }

    private fun ensureFramebuffer(width: Int, height: Int): Boolean {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        if (
            framebufferId != 0 &&
            geometryTextureId != 0 &&
            textureWidth == safeWidth &&
            textureHeight == safeHeight
        ) return true

        releaseFramebuffer()
        val textures = IntArray(1)
        val framebuffers = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        geometryTextureId = textures[0]
        if (geometryTextureId == 0) return false

        GLES20.glActiveTexture(GEOMETRY_TEXTURE_UNIT)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, geometryTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            safeWidth,
            safeHeight,
            0,
            GLES20.GL_RGBA,
            GL_HALF_FLOAT_OES,
            null,
        )

        GLES20.glGenFramebuffers(1, framebuffers, 0)
        framebufferId = framebuffers[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferId)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            geometryTextureId,
            0,
        )
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE || GLES20.glGetError() != GLES20.GL_NO_ERROR) {
            releaseFramebuffer()
            return false
        }
        textureWidth = safeWidth
        textureHeight = safeHeight
        cacheValid = false
        geometryValidationPending = true
        compositeValidationPending = true
        compositeUniformsInitialized = false
        return true
    }

    private fun renderGeometry(frame: LegacyOpenGLGlassCacheFrame, quadBufferId: Int): Boolean {
        val validate = geometryValidationPending
        if (validate) clearGlErrors()
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferId)
        GLES20.glViewport(0, 0, frame.viewportWidth, frame.viewportHeight)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(geometryProgram)
        bindQuad(quadBufferId, geometryPositionHandle)
        GLES20.glUniform2f(
            geometryResolutionHandle,
            frame.viewportWidth.toFloat(),
            frame.viewportHeight.toFloat(),
        )
        GLES20.glUniform4f(
            geometryRectHandle,
            0f,
            frame.rectOffsetY,
            frame.rectWidth,
            frame.rectHeight,
        )
        GLES20.glUniform1f(geometryRadiusHandle, frame.radius)
        GLES20.glUniform4f(
            geometryOpticsHandle,
            frame.opticsSampleRadius,
            frame.opticsRingWidth,
            frame.opticsDebugAlpha,
            frame.opticsDarkScale,
        )
        applyScissor(frame)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        val success = !validate || GLES20.glGetError() == GLES20.GL_NO_ERROR
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, frame.viewportWidth, frame.viewportHeight)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        cacheValid = success
        if (success) geometryValidationPending = false
        return success
    }

    private fun renderComposite(frame: LegacyOpenGLGlassCacheFrame, quadBufferId: Int): Boolean {
        val validate = compositeValidationPending
        if (validate) clearGlErrors()
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, frame.viewportWidth, frame.viewportHeight)
        GLES20.glUseProgram(compositeProgram)
        bindQuad(quadBufferId, compositePositionHandle)
        uploadCompositeUniforms(frame)
        bindTexture(GLES20.GL_TEXTURE0, frame.blurTextureId)
        bindTexture(GLES20.GL_TEXTURE1, frame.lensTextureId)
        bindTexture(GEOMETRY_TEXTURE_UNIT, geometryTextureId)
        applyScissor(frame)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        val success = !validate || GLES20.glGetError() == GLES20.GL_NO_ERROR
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        if (success) compositeValidationPending = false
        return success
    }

    private fun uploadCompositeUniforms(frame: LegacyOpenGLGlassCacheFrame) {
        val force = !compositeUniformsInitialized
        if (
            force ||
            lastCompositeViewportWidth != frame.viewportWidth ||
            lastCompositeViewportHeight != frame.viewportHeight
        ) {
            GLES20.glUniform2f(
                compositeResolutionHandle,
                frame.viewportWidth.toFloat(),
                frame.viewportHeight.toFloat(),
            )
            lastCompositeViewportWidth = frame.viewportWidth
            lastCompositeViewportHeight = frame.viewportHeight
        }
        if (force || lastCompositeOriginX != frame.originX || lastCompositeOriginY != frame.originY) {
            GLES20.glUniform2f(compositeCardOriginHandle, frame.originX, frame.originY)
            lastCompositeOriginX = frame.originX
            lastCompositeOriginY = frame.originY
        }
        if (force || lastCompositeRootWidth != frame.rootWidth || lastCompositeRootHeight != frame.rootHeight) {
            GLES20.glUniform2f(compositeRootResolutionHandle, frame.rootWidth, frame.rootHeight)
            lastCompositeRootWidth = frame.rootWidth
            lastCompositeRootHeight = frame.rootHeight
        }
        if (
            force ||
            lastCompositeRectWidth != frame.rectWidth ||
            lastCompositeRectHeight != frame.rectHeight ||
            lastCompositeRectOffsetY != frame.rectOffsetY
        ) {
            GLES20.glUniform4f(
                compositeRectHandle,
                0f,
                frame.rectOffsetY,
                frame.rectWidth,
                frame.rectHeight,
            )
            lastCompositeRectWidth = frame.rectWidth
            lastCompositeRectHeight = frame.rectHeight
            lastCompositeRectOffsetY = frame.rectOffsetY
        }
        if (force || lastCompositeRadius != frame.radius) {
            GLES20.glUniform1f(compositeRadiusHandle, frame.radius)
            lastCompositeRadius = frame.radius
        }
        if (
            force ||
            lastCompositePressProgress != frame.pressProgress ||
            lastCompositePressCenterX != frame.pressCenterX ||
            lastCompositePressCenterY != frame.pressCenterY
        ) {
            GLES20.glUniform4f(
                compositePressHandle,
                frame.pressProgress,
                frame.pressCenterX,
                frame.pressCenterY,
                0f,
            )
            lastCompositePressProgress = frame.pressProgress
            lastCompositePressCenterX = frame.pressCenterX
            lastCompositePressCenterY = frame.pressCenterY
        }
        if (force || lastCompositeTexturesReady != frame.texturesReady) {
            GLES20.glUniform1f(compositeTextureReadyHandle, if (frame.texturesReady) 1f else 0f)
            lastCompositeTexturesReady = frame.texturesReady
        }
        if (
            force ||
            lastCompositeMaterialVisibility != frame.materialVisibility ||
            lastCompositeMaterialMaxAlpha != frame.materialMaxAlpha ||
            lastCompositeMaterialEdgeBrightness != frame.materialEdgeBrightness
        ) {
            GLES20.glUniform4f(
                compositeMaterialHandle,
                frame.materialVisibility,
                frame.materialMaxAlpha,
                frame.materialEdgeBrightness,
                0f,
            )
            lastCompositeMaterialVisibility = frame.materialVisibility
            lastCompositeMaterialMaxAlpha = frame.materialMaxAlpha
            lastCompositeMaterialEdgeBrightness = frame.materialEdgeBrightness
        }
        if (
            force ||
            lastCompositeRefractionPullScale != frame.refractionPullScale ||
            lastCompositeRefractionEdgePullDp != frame.refractionEdgePullDp ||
            lastCompositeRefractionCompressionScale != frame.refractionCompressionScale ||
            lastCompositeRefractionCornerScale != frame.refractionCornerScale
        ) {
            GLES20.glUniform4f(
                compositeRefractionHandle,
                frame.refractionPullScale,
                frame.refractionEdgePullDp,
                frame.refractionCompressionScale,
                frame.refractionCornerScale,
            )
            lastCompositeRefractionPullScale = frame.refractionPullScale
            lastCompositeRefractionEdgePullDp = frame.refractionEdgePullDp
            lastCompositeRefractionCompressionScale = frame.refractionCompressionScale
            lastCompositeRefractionCornerScale = frame.refractionCornerScale
        }
        if (
            force ||
            lastCompositeOpticsSampleRadius != frame.opticsSampleRadius ||
            lastCompositeOpticsRingWidth != frame.opticsRingWidth ||
            lastCompositeOpticsDebugAlpha != frame.opticsDebugAlpha ||
            lastCompositeOpticsDarkScale != frame.opticsDarkScale
        ) {
            GLES20.glUniform4f(
                compositeOpticsHandle,
                frame.opticsSampleRadius,
                frame.opticsRingWidth,
                frame.opticsDebugAlpha,
                frame.opticsDarkScale,
            )
            lastCompositeOpticsSampleRadius = frame.opticsSampleRadius
            lastCompositeOpticsRingWidth = frame.opticsRingWidth
            lastCompositeOpticsDebugAlpha = frame.opticsDebugAlpha
            lastCompositeOpticsDarkScale = frame.opticsDarkScale
        }
        compositeUniformsInitialized = true
    }

    private fun applyScissor(frame: LegacyOpenGLGlassCacheFrame) {
        val width = (frame.scissorRight - frame.scissorLeft).coerceAtLeast(0)
        val height = (frame.scissorBottom - frame.scissorTop).coerceAtLeast(0)
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glScissor(
            frame.scissorLeft,
            frame.viewportHeight - frame.scissorBottom,
            width,
            height,
        )
    }

    private fun bindQuad(bufferId: Int, positionHandle: Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bufferId)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, 0)
    }

    private fun bindTexture(textureUnit: Int, textureId: Int) {
        GLES20.glActiveTexture(textureUnit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
    }

    private fun disablePermanently() {
        permanentlyDisabled = true
        supported = false
        cacheValid = false
        releaseProgramsAndFramebuffer()
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }

    private fun releaseProgramsAndFramebuffer() {
        releaseFramebuffer()
        if (geometryProgram != 0) GLES20.glDeleteProgram(geometryProgram)
        if (compositeProgram != 0) GLES20.glDeleteProgram(compositeProgram)
        geometryProgram = 0
        compositeProgram = 0
        compositeUniformsInitialized = false
    }

    private fun releaseFramebuffer() {
        if (framebufferId != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(framebufferId), 0)
        if (geometryTextureId != 0) GLES20.glDeleteTextures(1, intArrayOf(geometryTextureId), 0)
        framebufferId = 0
        geometryTextureId = 0
        textureWidth = 0
        textureHeight = 0
        cacheValid = false
    }

    private fun clearGlErrors() {
        while (GLES20.glGetError() != GLES20.GL_NO_ERROR) Unit
    }
}

private fun buildCacheProgram(vertex: String, fragment: String): Int {
    fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) { GLES20.glGetShaderInfoLog(shader) }
        return shader
    }

    val vertexShader = compile(GLES20.GL_VERTEX_SHADER, vertex)
    val fragmentShader = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
    val program = GLES20.glCreateProgram()
    GLES20.glAttachShader(program, vertexShader)
    GLES20.glAttachShader(program, fragmentShader)
    GLES20.glLinkProgram(program)
    val status = IntArray(1)
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
    GLES20.glDeleteShader(vertexShader)
    GLES20.glDeleteShader(fragmentShader)
    check(status[0] != 0) { GLES20.glGetProgramInfoLog(program) }
    return program
}

private const val CACHE_VERTEX_SHADER = """
    attribute vec2 aPosition;
    void main(){ gl_Position=vec4(aPosition,0.0,1.0); }
"""

private const val GEOMETRY_FRAGMENT_SHADER = """
    precision highp float;
    uniform vec2 uResolution;
    uniform vec4 uRect;
    uniform float uRadius;
    uniform vec4 uOptics;

    float sat(float x){return clamp(x,0.0,1.0);}
    float roundedBoxSdfPrepared(vec2 coord,vec2 halfSize,vec2 core,float radius){
        vec2 q=abs(coord-halfSize)-core;
        return length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius;
    }
    float rimWideFromInside(float inside,float edgeWidth){return 1.0-smoothstep(0.0,edgeWidth,inside);}
    float rimCoreFromInside(float inside,float coreWidth){return 1.0-smoothstep(0.0,coreWidth,inside);}
    float bodyDomeAt(vec2 coord,vec2 rectInv,float domeAspect){
        vec2 local=clamp(coord*rectInv,0.0,1.0);
        vec2 p=local*2.0-1.0;
        p.x*=domeAspect;
        float d=length(p);
        return pow(sat(1.0-d*0.74),1.65);
    }
    float thicknessAt(vec2 coord,vec2 halfSize,vec2 core,vec2 rectInv,float radius,float edgeWidth,float coreWidth,float domeAspect){
        float sd=roundedBoxSdfPrepared(coord,halfSize,core,radius);
        float inside=max(-sd,0.0);
        float maskGuard=1.0-smoothstep(1.5,16.0,sd);
        float rimWide=rimWideFromInside(inside,edgeWidth);
        float rimCore=rimCoreFromInside(inside,coreWidth);
        float dome=bodyDomeAt(coord,rectInv,domeAspect);
        return (dome*0.22+rimWide*0.46+rimCore*0.34)*maskGuard;
    }
    void main(){
        vec2 coord=vec2(gl_FragCoord.x,uResolution.y-gl_FragCoord.y);
        vec2 rectSize=max(uRect.zw,vec2(1.0));
        vec2 rectInv=1.0/rectSize;
        vec2 halfSize=rectSize*0.5;
        vec2 visualCoord=coord-uRect.xy;
        float minSize=min(rectSize.x,rectSize.y);
        float radius=min(uRadius,minSize*0.5);
        vec2 coreGeometry=max(halfSize-vec2(radius),vec2(0.0));
        float sd=roundedBoxSdfPrepared(visualCoord,halfSize,coreGeometry,radius);
        float mask=1.0-smoothstep(0.0,1.35,sd);
        if(mask<=0.001)discard;
        float inside=max(-sd,0.0);
        float edgeWidth=clamp(uOptics.y,6.0,minSize*0.34);
        float coreWidth=max(edgeWidth*0.28,3.0);
        float rectAspect=rectSize.x/rectSize.y;
        float domeAspect=min(rectAspect,2.4)*0.38;
        float stepPx=2.0;
        float tL=thicknessAt(visualCoord-vec2(stepPx,0.0),halfSize,coreGeometry,rectInv,radius,edgeWidth,coreWidth,domeAspect);
        float tR=thicknessAt(visualCoord+vec2(stepPx,0.0),halfSize,coreGeometry,rectInv,radius,edgeWidth,coreWidth,domeAspect);
        float tU=thicknessAt(visualCoord-vec2(0.0,stepPx),halfSize,coreGeometry,rectInv,radius,edgeWidth,coreWidth,domeAspect);
        float tD=thicknessAt(visualCoord+vec2(0.0,stepPx),halfSize,coreGeometry,rectInv,radius,edgeWidth,coreWidth,domeAspect);
        gl_FragColor=vec4(mask,tR-tL,tD-tU,inside);
    }
"""

private const val COMPOSITE_FRAGMENT_SHADER = """
    precision mediump float;
    uniform vec2 uResolution;
    uniform vec2 uCardOrigin;
    uniform vec2 uRootResolution;
    uniform vec4 uRect;
    uniform float uRadius;
    uniform vec4 uPress;
    uniform float uTextureReady;
    uniform vec4 uMaterial;
    uniform vec4 uRefraction;
    uniform vec4 uOptics;
    uniform sampler2D uBlurTexture;
    uniform sampler2D uLensTexture;
    uniform sampler2D uGeometryTexture;

    float sat(float x){return clamp(x,0.0,1.0);}
    vec2 perimeterNormalPrepared(vec2 coord,vec2 halfSize,vec2 core){
        vec2 local=coord-halfSize;
        vec2 nearest=clamp(local,-core,core);
        vec2 radial=local-nearest;
        float radialLength=length(radial);
        if(radialLength>0.0001)return radial/radialLength;
        vec2 safeCore=max(core,vec2(1.0));
        vec2 sideRatio=abs(local)/safeCore;
        if(sideRatio.x>sideRatio.y)return vec2(local.x<0.0?-1.0:1.0,0.0);
        return vec2(0.0,local.y<0.0?-1.0:1.0);
    }
    vec2 globalUvAt(vec2 visualCoord,vec2 rootInv){return clamp((uCardOrigin+visualCoord)*rootInv,0.0,1.0);}
    vec3 fallbackBackdrop(vec2 uv){float h=smoothstep(0.0,1.0,uv.y);return mix(vec3(0.12,0.22,0.38),vec3(0.36,0.50,0.72),h);}
    vec3 sourceBlurBackdrop(vec2 uv){vec2 safeUv=clamp(uv,0.0,1.0);if(uTextureReady<0.5)return fallbackBackdrop(safeUv);return texture2D(uBlurTexture,safeUv).rgb;}
    vec3 sourceLensBackdrop(vec2 uv){vec2 safeUv=clamp(uv,0.0,1.0);if(uTextureReady<0.5)return fallbackBackdrop(safeUv);return texture2D(uLensTexture,safeUv).rgb;}
    vec3 blurBackdrop(vec2 uv,float edgeWeight,vec2 rootInv){
        float sampleRadius=uOptics.x;
        if(sampleRadius<=0.50)return sourceBlurBackdrop(uv);
        float blurBoost=1.0+edgeWeight*0.38;
        vec2 px=vec2(sampleRadius*blurBoost)*rootInv;
        vec3 c=sourceBlurBackdrop(uv)*0.200;
        c+=sourceBlurBackdrop(uv+vec2(px.x,0.0))*0.110;
        c+=sourceBlurBackdrop(uv-vec2(px.x,0.0))*0.110;
        c+=sourceBlurBackdrop(uv+vec2(0.0,px.y))*0.110;
        c+=sourceBlurBackdrop(uv-vec2(0.0,px.y))*0.110;
        c+=sourceBlurBackdrop(uv+px)*0.090;
        c+=sourceBlurBackdrop(uv+vec2(-px.x,px.y))*0.090;
        c+=sourceBlurBackdrop(uv+vec2(px.x,-px.y))*0.090;
        c+=sourceBlurBackdrop(uv-px)*0.090;
        return c;
    }
    float rimWideFromInside(float inside,float edgeWidth){return 1.0-smoothstep(0.0,edgeWidth,inside);}
    float rimCoreFromInside(float inside,float coreWidth){return 1.0-smoothstep(0.0,coreWidth,inside);}
    float dragBandFromInside(float inside,float dragWidth){return pow(1.0-smoothstep(0.0,dragWidth,inside),1.35);}
    float pressFieldAt(vec2 coord,vec2 rectInv,vec2 center,float aspect,float press){
        vec2 delta=clamp(coord*rectInv,0.0,1.0)-center;
        delta.x*=aspect;
        float d=length(delta);
        return pow(sat(1.0-d*0.92),1.45)*press;
    }
    vec2 softLimitPx(vec2 v,float limitPx){float len=length(v);float softLen=len/(1.0+len/max(limitPx,1.0));return v*(softLen/max(len,0.0001));}
    float colorSignal(vec3 c){float luma=dot(c,vec3(0.299,0.587,0.114));float chroma=length(c-vec3(luma));return sat((luma-0.20)*1.25+chroma*1.55);}
    vec3 edgeColorDrag(vec2 coord,vec2 halfSize,vec2 coreGeometry,float band,float core,float edgeWidth,vec2 rootInv){
        vec2 n=perimeterNormalPrepared(coord,halfSize,coreGeometry);
        vec2 t=vec2(-n.y,n.x);
        float pull=clamp(8.0+abs(uRefraction.y)*0.030,8.0,42.0);
        float smear=clamp(4.0+edgeWidth*0.55,4.0,22.0);
        vec2 baseIn=coord-n*pull;
        vec2 baseFar=coord-n*(pull*1.85);
        vec2 baseOut=coord+n*(pull*0.45);
        vec2 smearNear=t*smear;
        vec2 smearFar=smearNear*1.85;
        vec3 c=sourceLensBackdrop(globalUvAt(baseIn,rootInv))*0.28;
        c+=sourceLensBackdrop(globalUvAt(baseFar,rootInv))*0.18;
        c+=sourceLensBackdrop(globalUvAt(baseOut,rootInv))*0.12;
        c+=sourceLensBackdrop(globalUvAt(baseIn+smearNear,rootInv))*0.14;
        c+=sourceLensBackdrop(globalUvAt(baseIn-smearNear,rootInv))*0.14;
        c+=sourceLensBackdrop(globalUvAt(baseIn+smearFar,rootInv))*0.07;
        c+=sourceLensBackdrop(globalUvAt(baseIn-smearFar,rootInv))*0.07;
        vec3 soft=blurBackdrop(globalUvAt(baseIn,rootInv),band,rootInv)*0.45+c*0.55;
        float signal=colorSignal(c);
        float dragAlpha=band*(0.035+sat(max(uRefraction.z,0.0))*0.105+core*0.030)*signal;
        return mix(vec3(0.0),soft,sat(dragAlpha));
    }
    void main(){
        vec4 geometry=texture2D(uGeometryTexture,gl_FragCoord.xy/max(uResolution,vec2(1.0)));
        float mask=geometry.r;
        if(mask<=0.001)discard;
        vec2 grad=geometry.gb;
        float inside=geometry.a;
        vec2 coord=vec2(gl_FragCoord.x,uResolution.y-gl_FragCoord.y);
        vec2 rectSize=max(uRect.zw,vec2(1.0));
        vec2 rectInv=1.0/rectSize;
        vec2 halfSize=rectSize*0.5;
        vec2 visualCoord=coord-uRect.xy;
        float minSize=min(rectSize.x,rectSize.y);
        float radius=min(uRadius,minSize*0.5);
        vec2 coreGeometry=max(halfSize-vec2(radius),vec2(0.0));
        vec2 rootInv=1.0/max(uRootResolution,vec2(1.0));
        float edgeWidth=clamp(uOptics.y,6.0,minSize*0.34);
        float coreWidth=max(edgeWidth*0.28,3.0);
        float dragWidth=max(edgeWidth*1.45,8.0);
        float rectAspect=rectSize.x/rectSize.y;
        float aspect=min(rectAspect,2.2);
        float press=uPress.x;
        vec2 pressCenter=uPress.yz;
        vec2 pressCenterPx=pressCenter*rectSize;
        float pressField=0.0;
        float pressWide=0.0;
        vec2 inwardPx=vec2(0.0);
        vec2 pressDimplePx=vec2(0.0);
        if(press>0.0){
            pressField=pressFieldAt(visualCoord,rectInv,pressCenter,aspect,press);
            pressWide=press*pow(sat(1.0-length((visualCoord*rectInv-pressCenter)*vec2(aspect,1.0))*0.58),1.25);
            inwardPx=softLimitPx((pressCenterPx-visualCoord)*(0.028*press+0.070*pressField),24.0+press*18.0);
            vec2 pressDelta=visualCoord-pressCenterPx;
            vec2 pressDir=pressDelta/max(length(pressDelta),0.001);
            pressDimplePx=-pressDir*pressField*(8.0+press*10.0);
        }
        vec2 pressedCoord=visualCoord+inwardPx;
        vec2 bgUv=globalUvAt(pressedCoord,rootInv);
        float rimWide=rimWideFromInside(inside,edgeWidth);
        float rimCore=rimCoreFromInside(inside,coreWidth);
        float dragBand=dragBandFromInside(inside,dragWidth);
        float gLen=length(grad);
        float gradGate=smoothstep(0.0004,0.012,gLen);
        float gradScale=gradGate*min(1.0,0.22/max(gLen,0.0001));
        grad*=gradScale;
        float gradEnergy=sat(gLen*gradScale*uRefraction.w);
        vec2 rawRefractPx=grad*(uRefraction.x+uRefraction.y*rimWide+press*(26.0+52.0*pressField))*max(uMaterial.x,0.0);
        rawRefractPx+=pressDimplePx;
        rawRefractPx+=inwardPx*(0.76+0.46*rimWide);
        float limitPx=mix(18.0,62.0,rimWide)+sat(abs(uRefraction.y)/600.0)*16.0+press*20.0;
        vec2 refractPx=softLimitPx(rawRefractPx,limitPx);
        vec2 refractedUv=bgUv+refractPx*rootInv;
        vec3 color=blurBackdrop(refractedUv,rimWide+pressField*0.85+pressWide*0.22,rootInv);
        vec3 lensColor=sourceLensBackdrop(refractedUv);
        float lensMix=sat(rimCore*max(uRefraction.z,0.0)*0.42+pressField*0.220+pressWide*0.075);
        color=mix(color,lensColor,lensMix);
        float dragAmount=dragBand+press*rimWide*0.32+pressField*0.18;
        if(dragAmount>0.002){
            vec3 dragColor=edgeColorDrag(visualCoord+inwardPx*0.72,halfSize,coreGeometry,dragAmount,rimCore,edgeWidth,rootInv);
            float dragMix=sat(max(max(dragColor.r,dragColor.g),dragColor.b));
            color=mix(color,dragColor,dragMix);
        }
        float rimOpticalBoost=rimCore*0.16+gradEnergy*0.045+press*rimCore*0.080+pressField*0.040;
        color*=uMaterial.z*(1.0+rimOpticalBoost);
        color*=1.0-pressField*0.070-pressWide*0.025;
        color+=vec3(0.018,0.035,0.046)*pressField*0.38;
        color-=vec3(0.06,0.07,0.09)*uOptics.w*rimWide;
        color=clamp(color,0.0,1.0);
        float finalAlpha=clamp(uMaterial.y*uMaterial.x,0.0,1.0)*mask;
        gl_FragColor=vec4(color,finalAlpha);
    }
"""

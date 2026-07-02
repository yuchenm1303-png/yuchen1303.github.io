package com.yuchen.ailedger.ui.gl

/**
 * 设置仪表盘批绘制只替换参数传递方式，不复制或降级玻璃算法。
 *
 * 每张卡的矩形、采样原点、圆角、强度和按压状态由顶点属性传入；Fragment 主体继续直接
 * 拼接股票 Hero 使用的公共函数、圆肩、色散和最终合成代码。八张卡因此可以在完整刷新时
 * 合并为一次 draw call，同时保持每张卡独立的折射坐标与动态状态。
 */
internal object WebOpenGLGlassBatchShaders {
    const val VERTEX_SHADER = """
        precision highp float;
        attribute vec2 aPosition;
        attribute vec4 aRect;
        attribute vec4 aCard;
        attribute vec4 aPress;
        uniform vec2 uResolution;
        varying vec4 vRect;
        varying vec4 vCard;
        varying vec4 vPress;

        void main(){
            vec2 safeResolution=max(uResolution,vec2(1.0));
            vec2 ndc=vec2(
                aPosition.x/safeResolution.x*2.0-1.0,
                1.0-aPosition.y/safeResolution.y*2.0
            );
            gl_Position=vec4(ndc,0.0,1.0);
            vRect=aRect;
            vCard=aCard;
            vPress=aPress;
        }
    """

    private const val FRAGMENT_HEADER = """
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
        uniform vec2 uResolution;
        uniform vec2 uRootResolution;
        uniform sampler2D uClearTexture;
        uniform sampler2D uBlurLowTexture;
        uniform sampler2D uBlurMediumTexture;
        uniform sampler2D uBlurHighTexture;
        uniform vec4 uMaterial;
        uniform vec4 uBodyLensA;
        uniform vec4 uBodyLensB;
        uniform vec4 uBody;
        uniform vec4 uShoulder;
        uniform vec2 uShoulderFlow;
        uniform vec4 uDispersion;
        uniform float uTextureReady;
        uniform float uBlurAmount;
        varying vec4 vRect;
        varying vec4 vCard;
        varying vec4 vPress;
        #define uRect vRect
        #define uCardOrigin (vCard.xy)
        #define uRadius (vCard.z)
        #define uIntensity (vCard.w)
        #define uPress vPress
    """

    const val FRAGMENT_SHADER =
        FRAGMENT_HEADER +
            WebOpenGLGlassShaderPrelude.FUNCTIONS +
            WebOpenGLPressOpticsShader.FUNCTIONS +
            WebOpenGLOuterPeakShoulderShader.SOURCE +
            WebOpenGLGlassMainShader.BODY_PREFIX +
            WebOpenGLGlassMainShader.BODY_SUFFIX
}

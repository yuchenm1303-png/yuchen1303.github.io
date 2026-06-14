package com.yuchen.ailedger.ui.gl

/**
 * V25.3 主体折射 + V29.8 整圈统一映射精确切向校正圆肩 + 原版动态按压光学。
 *
 * 着色器按职责拆分为公共主体、按压场、圆肩和最终合成四段；运行时仍是
 * 一个 fragment shader、一次 draw call，不会增加 OpenGL 层或渲染通道。
 */
internal object WebOpenGLGlassShaders {
    const val FRAGMENT_SHADER =
        WebOpenGLGlassShaderPrelude.HEADER +
            WebOpenGLPressOpticsShader.UNIFORMS +
            WebOpenGLGlassShaderPrelude.FUNCTIONS +
            WebOpenGLPressOpticsShader.FUNCTIONS +
            WebOpenGLOuterPeakShoulderShader.SOURCE +
            WebOpenGLGlassMainShader.BODY_PREFIX +
            WebOpenGLGlassMainShader.BODY_SUFFIX
}

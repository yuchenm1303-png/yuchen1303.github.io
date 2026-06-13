package com.yuchen.ailedger.ui.gl

/**
 * V25.3 主体折射 + 9a6e4ac 原版边缘折射 + 80e9e0e 原版动态按压光学。
 *
 * 着色器按职责拆分为公共主体、按压场、旧边缘和最终合成四段；运行时仍是
 * 一个 fragment shader、一次 draw call，不会增加 OpenGL 层或渲染通道。
 */
internal object WebOpenGLGlassShaders {
    const val FRAGMENT_SHADER =
        WebOpenGLGlassShaderPrelude.HEADER +
            WebOpenGLPressOpticsShader.UNIFORMS +
            WebOpenGLGlassShaderPrelude.FUNCTIONS +
            WebOpenGLPressOpticsShader.FUNCTIONS +
            WebOpenGLLegacyEdgeCommonShader.SOURCE +
            WebOpenGLLegacyEdgePressShader.SOURCE +
            WebOpenGLGlassMainShader.BODY_PREFIX +
            WebOpenGLGlassMainShader.BODY_SUFFIX
}

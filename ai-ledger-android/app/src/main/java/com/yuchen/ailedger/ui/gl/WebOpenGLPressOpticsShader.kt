package com.yuchen.ailedger.ui.gl

internal object WebOpenGLPressOpticsShader {
    const val UNIFORMS = """
        uniform vec4 uPress;
    """

    const val FUNCTIONS = """
        float pressFieldAt(
            vec2 coord,
            vec2 rectSize,
            vec2 center,
            float aspect,
            float press
        ){
            vec2 delta=clamp(coord/rectSize,0.0,1.0)-center;
            delta.x*=aspect;
            float d=length(delta);
            return pow(sat(1.0-d*0.92),1.45)*press;
        }
    """
}

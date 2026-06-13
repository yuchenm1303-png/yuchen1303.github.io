package com.yuchen.ailedger.ui.gl

internal object WebOpenGLPressOpticsShader {
    const val UNIFORMS = """
        uniform vec4 uPress;
    """

    const val FUNCTIONS = """
        float pressFieldAt(vec2 coord,vec2 rectSize,vec2 center,float press){
            vec2 local=clamp(coord/rectSize,0.0,1.0);
            vec2 delta=local-center;
            delta.x*=min(rectSize.x/max(rectSize.y,1.0),2.2);
            float d=length(delta);
            float field=pow(sat(1.0-d*0.92),1.45);
            return field*press;
        }
    """
}

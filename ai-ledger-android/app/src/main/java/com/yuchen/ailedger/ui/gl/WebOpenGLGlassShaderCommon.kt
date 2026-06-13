package com.yuchen.ailedger.ui.gl

/** Shared uniforms, sampling helpers and scalar utilities for the app shell glass shader. */
internal object WebOpenGLGlassShaderCommon {
    const val SOURCE = """
        precision highp float;
        uniform vec2 uResolution;
        uniform vec2 uCardOrigin;
        uniform vec2 uRootResolution;
        uniform vec4 uRect;
        uniform sampler2D uTexture;
        uniform sampler2D uFlow;
        uniform vec4 uMaterial;
        uniform vec4 uOldA;
        uniform vec4 uOldB;
        uniform vec4 uBody;
        uniform vec4 uBodyBand;
        uniform float uRadius;
        uniform float uIntensity;
        uniform float uTextureReady;
        uniform float uFlowDepth;

        float sat(float x){return clamp(x,0.0,1.0);}
        float boxSdf(vec2 p,vec2 z,float r){
            vec2 q=abs(p-z*0.5)-max(z*0.5-vec2(r),vec2(0.0));
            return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;
        }
        float insideFromSdf(float sdf){return max(-sdf,0.0);}
        vec2 globalUv(vec2 p){
            vec2 root=max(uRootResolution,vec2(1.0));
            vec2 texel=0.5/root;
            return clamp((uCardOrigin+p)/root,texel,1.0-texel);
        }
        vec3 fallback(vec2 uv){
            float h=smoothstep(0.0,1.0,uv.y);
            return mix(vec3(0.04,0.10,0.27),vec3(0.24,0.58,0.76),h);
        }
        vec3 sampleBg(vec2 uv){
            return mix(fallback(uv),texture2D(uTexture,clamp(uv,0.0,1.0)).rgb,sat(uTextureReady));
        }
        float gauss(float x,float m,float w){
            float q=(x-m)/max(w,0.0001);
            return exp(-q*q);
        }
        vec2 softLimit(vec2 v,float lim){
            float n=length(v);
            float m=n/(1.0+n/max(lim,1.0));
            return v*(m/max(n,0.0001));
        }
    """
}

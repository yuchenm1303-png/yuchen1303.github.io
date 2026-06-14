package com.yuchen.ailedger.ui.gl

internal object WebOpenGLGlassShaderPrelude {
    const val HEADER = """
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
        uniform vec2 uResolution;
        uniform vec2 uCardOrigin;
        uniform vec2 uRootResolution;
        uniform vec4 uRect;
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
        uniform float uRadius;
        uniform float uIntensity;
        uniform float uTextureReady;
        uniform float uBlurAmount;
    """

    const val FUNCTIONS = """
        float sat(float x){return clamp(x,0.0,1.0);}
        float roundedBoxSdf(vec2 p,vec2 z,float r){
            vec2 q=abs(p-z*0.5)-max(z*0.5-vec2(r),vec2(0.0));
            return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;
        }
        float insideFromSdf(float sdf){return max(-sdf,0.0);}
        vec2 globalUv(vec2 p){
            vec2 root=max(uRootResolution,vec2(1.0));
            vec2 texel=0.5/root;
            return clamp((uCardOrigin+p)/root,texel,1.0-texel);
        }
        vec3 fallbackBackdrop(vec2 uv){
            float h=smoothstep(0.0,1.0,uv.y);
            return mix(vec3(0.12,0.22,0.38),vec3(0.36,0.50,0.72),h);
        }
        vec3 clearBackdrop(vec2 uv){
            vec2 safeUv=clamp(uv,0.0,1.0);
            vec3 realColor=texture2D(uClearTexture,safeUv).rgb;
            return mix(fallbackBackdrop(safeUv),realColor,sat(uTextureReady));
        }
        vec3 blurPyramidBackdropAt(vec2 uv,float requestedAmount){
            vec2 safeUv=clamp(uv,0.0,1.0);
            float amount=clamp(requestedAmount,0.0,4.0);
            vec3 result;
            if(amount<=0.001){
                result=texture2D(uClearTexture,safeUv).rgb;
            }else if(amount<1.0){
                vec3 clearColor=texture2D(uClearTexture,safeUv).rgb;
                vec3 lowColor=texture2D(uBlurLowTexture,safeUv).rgb;
                result=mix(clearColor,lowColor,amount);
            }else if(amount<2.0){
                vec3 lowColor=texture2D(uBlurLowTexture,safeUv).rgb;
                vec3 mediumColor=texture2D(uBlurMediumTexture,safeUv).rgb;
                result=mix(lowColor,mediumColor,amount-1.0);
            }else{
                vec3 mediumColor=texture2D(uBlurMediumTexture,safeUv).rgb;
                vec3 highColor=texture2D(uBlurHighTexture,safeUv).rgb;
                result=mix(mediumColor,highColor,(amount-2.0)*0.5);
            }
            return mix(fallbackBackdrop(safeUv),result,sat(uTextureReady));
        }
        vec3 blurPyramidBackdrop(vec2 uv){
            return blurPyramidBackdropAt(uv,uBlurAmount);
        }
        vec2 softLimit(vec2 v,float lim){
            float n=length(v);
            float m=n/(1.0+n/max(lim,1.0));
            return v*(m/max(n,0.0001));
        }
        vec2 softLimitPx(vec2 v,float limitPx){
            float len=length(v);
            float softLen=len/(1.0+len/max(limitPx,1.0));
            return v*(softLen/max(len,0.0001));
        }
        vec2 perimeterNormalAt(vec2 p,vec2 z,float r){
            vec2 local=p-z*0.5;
            vec2 core=max(z*0.5-vec2(r),vec2(0.0));
            vec2 nearest=clamp(local,-core,core);
            vec2 radial=local-nearest;
            float radialLength=length(radial);
            if(radialLength>0.0001){
                return radial/radialLength;
            }
            vec2 safeCore=max(core,vec2(1.0));
            vec2 sideRatio=abs(local)/safeCore;
            if(sideRatio.x>sideRatio.y){
                return vec2(local.x<0.0?-1.0:1.0,0.0);
            }
            return vec2(0.0,local.y<0.0?-1.0:1.0);
        }
        float bodyLensReach(vec2 z,float r){
            float requested=max(uBodyLensB.y,8.0);
            float curvatureSafe=max(r*0.96,8.0);
            return min(requested,min(curvatureSafe,min(z.x,z.y)*0.46));
        }
        float bodyLensWeight(float depth,vec2 z,float r){
            float reach=bodyLensReach(z,r);
            float x=sat(depth/max(reach,1.0));
            float smooth=x*x*(3.0-2.0*x);
            float concentration=mix(0.58,1.82,sat((uBodyLensA.z+10.0)/20.0));
            return pow(1.0-smooth,concentration);
        }
        vec2 bodyRefractionFlow(vec2 p,vec2 z,float r,float depth,float weight){
            vec2 n=perimeterNormalAt(p,z,r);
            float rawPull=abs(uBodyLensA.y)*0.052+abs(uBodyLensA.x)*0.20+max(uBodyLensB.x,0.0)*0.12;
            float core=pow(weight,1.28);
            float reach=bodyLensReach(z,r);
            float remaining=max(reach-depth,0.0);
            float displacement=remaining*(1.0-exp(-(rawPull*core)/max(remaining,1.0)))*0.96;
            return -n*displacement;
        }
        float centerEnvelope(vec2 u){
            float width=sat((uBody.x-0.18)/(1.5-0.18));
            vec2 span=vec2(mix(0.72,1.16,width),mix(0.66,1.08,width));
            vec2 q=abs(u)/max(span,vec2(0.001));
            return exp(-(pow(q.x,4.0)+pow(q.y,4.0)));
        }
        vec2 polynomialTransport(vec2 u){
            float curve=sat((uBody.y-0.2)/3.0);
            float ky=mix(0.10,0.34,curve);
            float kx=mix(0.08,0.30,curve);
            float ay=mix(0.24,0.52,curve);
            float yRelax=mix(0.18,0.36,curve);
            float xBoost=mix(0.10,0.24,curve);
            vec2 transport=vec2(
                u.x*(1.0-ky*u.y*u.y),
                -ay*u.y*(1.0-kx*u.x*u.x)
            );
            transport.x+=u.x*xBoost*(1.0-0.58*u.y*u.y);
            transport.y+=u.y*yRelax*(1.0-0.66*u.x*u.x);
            transport+=vec2(-u.y,u.x)*mix(0.004,0.020,curve);
            return transport;
        }
        vec2 centerTransport(vec2 p,vec2 z){
            vec2 u=(p-z*0.5)/max(z*0.5,vec2(1.0));
            float gain=sat(uBody.z/900.0);
            float curve=sat((uBody.y-0.2)/3.0);
            float amplitude=min(z.x,z.y)*0.5*gain*mix(0.18,0.46,curve);
            vec2 flow=polynomialTransport(u)*amplitude*centerEnvelope(u);
            return softLimit(flow,mix(52.0,118.0,gain));
        }
    """
}

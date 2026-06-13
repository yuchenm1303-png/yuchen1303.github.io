package com.yuchen.ailedger.ui.gl

/**
 * Direct GLES port of the final web preview glass.
 *
 * The shader keeps the web architecture intact:
 * - precomputed harmonic ring Flow Map for the outer body transport;
 * - non-normalized polynomial center transport with a broad fourth-power envelope;
 * - the original rounded-rectangle SDF edge thickness/refraction chain.
 */
internal object WebOpenGLGlassShaders {
    const val FRAGMENT_SHADER = """
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

        float centerEnvelope(vec2 u){
            float width=sat((uBody.x-0.18)/(1.5-0.18));
            vec2 span=vec2(mix(0.64,0.99,width),mix(0.56,0.90,width));
            vec2 q=abs(u)/max(span,vec2(0.001));
            return exp(-(pow(q.x,4.0)+pow(q.y,4.0)));
        }
        vec2 polynomialTransport(vec2 u){
            float curve=sat((uBody.y-0.2)/3.0);
            float ky=mix(0.14,0.48,curve);
            float kx=mix(0.10,0.42,curve);
            float ay=mix(0.44,0.76,curve);
            float yRelax=mix(0.24,0.42,curve);
            float xBoost=mix(0.08,0.20,curve);
            vec2 transport=vec2(
                u.x*(1.0-ky*u.y*u.y),
                -ay*u.y*(1.0-kx*u.x*u.x)
            );
            transport.x+=u.x*xBoost*(1.0-0.65*u.y*u.y);
            transport.y+=u.y*yRelax*(1.0-0.75*u.x*u.x);
            vec2 tangent=vec2(-u.y,u.x);
            transport+=tangent*mix(0.006,0.026,curve);
            return transport;
        }
        float centerLimitPx(vec2 z){
            float width=sat((uBody.x-0.18)/(1.5-0.18));
            float gain=sat(uBody.z/900.0);
            return mix(38.0,86.0,width)*mix(0.55,1.0,gain);
        }

        float ringBandWidthPx(vec2 z){
            return max(uBodyBand.y*min(z.x,z.y)*0.18,1.0);
        }
        float ringBandCenterPx(vec2 z,float width){
            float halfMin=min(z.x,z.y)*0.5;
            float raw=(1.0-uBodyBand.x)*halfMin;
            return clamp(raw,0.0,max(uFlowDepth-width*1.20,0.0));
        }
        float ringShell(float depth,vec2 z){
            float width=ringBandWidthPx(z);
            float center=ringBandCenterPx(z,width);
            return gauss(depth,center,width)+gauss(depth,center+width*0.42,width*1.75)*0.78;
        }
        float ringSafeLimit(vec2 z,float ringSafe){
            float width=ringBandWidthPx(z);
            return max(4.0,min(width*4.20,uFlowDepth*0.46))*pow(sat(ringSafe),0.64);
        }
        vec2 roundedSdfNormal(vec2 p,vec2 z,float r){
            float d=1.4;
            vec2 n=vec2(
                boxSdf(p+vec2(d,0.0),z,r)-boxSdf(p-vec2(d,0.0),z,r),
                boxSdf(p+vec2(0.0,d),z,r)-boxSdf(p-vec2(0.0,d),z,r)
            );
            return n/max(length(n),0.0001);
        }

        float edgeWidth(vec2 z){return clamp(uOldB.y,2.0,min(z.x,z.y)*0.44);}
        float rimWideFromDepth(float depth,vec2 z){return 1.0-smoothstep(0.0,edgeWidth(z),depth);}
        float rimCoreFromDepth(float depth,vec2 z){return 1.0-smoothstep(0.0,max(edgeWidth(z)*0.28,2.0),depth);}
        float rimBandFromDepth(float depth,vec2 z){return pow(1.0-smoothstep(0.0,max(edgeWidth(z)*1.45,6.0),depth),1.35);}
        float dome(vec2 p,vec2 z){
            vec2 q=clamp(p/z,0.0,1.0)*2.0-1.0;
            q.x*=min(z.x/max(z.y,1.0),2.4)*0.38;
            return pow(sat(1.0-length(q)*0.74),1.65);
        }
        float thickFromSdf(vec2 p,vec2 z,float sdf){
            float depth=insideFromSdf(sdf);
            return (dome(p,z)*0.22+rimWideFromDepth(depth,z)*0.46+rimCoreFromDepth(depth,z)*0.34)
                *(1.0-smoothstep(1.5,16.0,sdf));
        }

        void main(){
            vec2 coord=vec2(gl_FragCoord.x,uResolution.y-gl_FragCoord.y);
            vec2 z=max(uRect.zw,vec2(1.0));
            vec2 p=coord-uRect.xy;
            float r=min(uRadius,min(z.x,z.y)*0.5);
            float sd=boxSdf(p,z,r);
            float mask=1.0-smoothstep(0.0,1.35,sd);
            if(mask<=0.001)discard;
            float st=2.0;
            float edgeDepth=insideFromSdf(sd);

            vec4 ringData=texture2D(uFlow,clamp(p/z,0.0,1.0));
            float ringSafe=ringData.a;
            vec2 ringFlow=vec2(0.0);
            if(ringSafe>0.001){
                float ringDepth=ringData.r*uFlowDepth;
                vec2 ringN=ringData.gb*2.0-1.0;
                ringN/=max(length(ringN),0.0001);
                float width=ringBandWidthPx(z);
                float shell=ringShell(ringDepth,z);
                float shellIn=ringShell(min(ringDepth+1.0,uFlowDepth),z);
                float shellOut=ringShell(max(ringDepth-1.0,0.0),z);
                float slope=(shellIn-shellOut)*0.5;
                float strength=uBodyBand.z*1.90;
                ringFlow=-ringN*(slope*width*strength+shell*uBodyBand.z*1.24)*ringSafe;
                ringFlow=softLimit(ringFlow,ringSafeLimit(z,ringSafe));
            }

            vec2 u=(p-z*0.5)/max(z*0.5,vec2(1.0));
            float centerGain=sat(uBody.z/900.0);
            float centerCurve=sat((uBody.y-0.2)/3.0);
            float centerAmplitude=min(z.x,z.y)*0.5*centerGain*mix(0.10,0.24,centerCurve);
            vec2 centerFlow=polynomialTransport(u)*centerAmplitude*centerEnvelope(u);
            centerFlow=softLimit(centerFlow,centerLimitPx(z));
            vec2 bodyFlow=ringFlow+centerFlow;

            float edgeSdfL=boxSdf(p-vec2(st,0.0),z,r);
            float edgeSdfR=boxSdf(p+vec2(st,0.0),z,r);
            float edgeSdfU=boxSdf(p-vec2(0.0,st),z,r);
            float edgeSdfD=boxSdf(p+vec2(0.0,st),z,r);
            float tL=thickFromSdf(p-vec2(st,0.0),z,edgeSdfL);
            float tR=thickFromSdf(p+vec2(st,0.0),z,edgeSdfR);
            float tU=thickFromSdf(p-vec2(0.0,st),z,edgeSdfU);
            float tD=thickFromSdf(p+vec2(0.0,st),z,edgeSdfD);
            vec2 edgeGrad=vec2(tR-tL,tD-tU);
            float rw=rimWideFromDepth(edgeDepth,z);
            float rc=rimCoreFromDepth(edgeDepth,z);
            float rb=rimBandFromDepth(edgeDepth,z);
            float glen=length(edgeGrad);
            edgeGrad*=smoothstep(0.00035,0.011,glen)*min(1.0,0.26/max(glen,0.0001));
            float perimeterAssist=pow(rc,1.65)*uOldA.w*0.0022;
            float pull=uOldA.x+uOldA.y*rw+uOldA.z*8.0*rb+perimeterAssist;
            vec2 edgeFlow=softLimit(edgeGrad*pull*uMaterial.x,24.0+rw*62.0+sat(abs(uOldA.y)/600.0)*24.0);

            vec3 color=sampleBg(globalUv(p+bodyFlow+edgeFlow));
            float edgeMix=sat(rb*(0.16+rc*0.60+sat(uOldA.z*0.07)));
            if(edgeMix>0.0){
                vec2 edgeN=roundedSdfNormal(p,z,r);
                float sampleRadius=max(uOldB.x,0.0);
                vec3 nearColor=sampleBg(globalUv(p-edgeN*(8.0+sampleRadius*0.20+abs(uOldA.y)*0.03)));
                vec3 farColor=sampleBg(globalUv(p-edgeN*(16.0+sampleRadius*0.36+abs(uOldA.y)*0.055)));
                vec3 outerColor=sampleBg(globalUv(p+edgeN*(6.0+sampleRadius*0.12)));
                color=mix(color,nearColor*0.50+farColor*0.36+outerColor*0.14,edgeMix);
            }
            float boost=1.0+rc*0.16+rw*max(uMaterial.z,0.0)*0.10;
            color*=uBody.w*uMaterial.z*boost;
            color-=vec3(0.06,0.07,0.09)*uOldB.z*rw;
            color=mix(color,vec3(1.0,0.45,0.0),smoothstep(-1.6,0.0,sd)*mask*uOldB.w);
            gl_FragColor=vec4(clamp(color,0.0,1.0),mask*uMaterial.y*sat(uMaterial.x/20.0)*uIntensity);
        }
    """
}

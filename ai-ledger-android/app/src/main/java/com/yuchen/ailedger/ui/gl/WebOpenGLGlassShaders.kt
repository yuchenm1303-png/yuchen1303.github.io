package com.yuchen.ailedger.ui.gl

/**
 * App 主玻璃的组合着色器。
 *
 * 主体折射保持当前网页版最终结构不变：
 * - 调和环形 Flow Map；
 * - 非归一化多项式中心运输场；
 * - 四次方宽包络。
 *
 * 边缘折射则完整恢复到提交 9a6e4ac7605da3859ff9accd4a33fe0bab7a9ddc
 * 中 OpenGLGlassCardLayer 的纯净边缘算法，仅在采样坐标中叠加现有主体位移。
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

        // 当前已经调好的主体折射：保持不变。
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

        // 以下边缘结构恢复自 9a6e4ac 的 OpenGLGlassCardLayer。
        vec3 sourceBlurBackdrop(vec2 uv){
            return sampleBg(uv);
        }
        vec3 sourceLensBackdrop(vec2 uv){
            return sampleBg(uv);
        }
        vec3 blurBackdrop(vec2 uv,float edgeWeight){
            float blurBoost=1.0+edgeWeight*0.38;
            vec2 px=vec2(max(uOldB.x,0.0)*blurBoost)/max(uRootResolution,vec2(1.0));
            vec3 c=sourceBlurBackdrop(uv)*0.200;
            c+=sourceBlurBackdrop(uv+vec2(px.x,0.0))*0.110;
            c+=sourceBlurBackdrop(uv-vec2(px.x,0.0))*0.110;
            c+=sourceBlurBackdrop(uv+vec2(0.0,px.y))*0.110;
            c+=sourceBlurBackdrop(uv-vec2(0.0,px.y))*0.110;
            c+=sourceBlurBackdrop(uv+vec2(px.x,px.y))*0.090;
            c+=sourceBlurBackdrop(uv+vec2(-px.x,px.y))*0.090;
            c+=sourceBlurBackdrop(uv+vec2(px.x,-px.y))*0.090;
            c+=sourceBlurBackdrop(uv+vec2(-px.x,-px.y))*0.090;
            return c;
        }
        float effectiveEdgeWidth(vec2 rectSize){
            float maxSafe=min(rectSize.x,rectSize.y)*0.34;
            return clamp(uOldB.y,6.0,maxSafe);
        }
        float insideDistanceAt(vec2 coord,vec2 rectSize,float radius){
            return max(-boxSdf(coord,rectSize,radius),0.0);
        }
        float rimWideAt(vec2 coord,vec2 rectSize,float radius){
            float inside=insideDistanceAt(coord,rectSize,radius);
            float w=effectiveEdgeWidth(rectSize);
            return 1.0-smoothstep(0.0,w,inside);
        }
        float rimCoreAt(vec2 coord,vec2 rectSize,float radius){
            float inside=insideDistanceAt(coord,rectSize,radius);
            float w=max(effectiveEdgeWidth(rectSize)*0.28,3.0);
            return 1.0-smoothstep(0.0,w,inside);
        }
        float edgeDragBandAt(vec2 coord,vec2 rectSize,float radius){
            float inside=insideDistanceAt(coord,rectSize,radius);
            float w=max(effectiveEdgeWidth(rectSize)*1.45,8.0);
            return pow(1.0-smoothstep(0.0,w,inside),1.35);
        }
        vec2 sdfNormalAt(vec2 coord,vec2 rectSize,float radius){
            float d=1.25;
            float l=boxSdf(coord-vec2(d,0.0),rectSize,radius);
            float r=boxSdf(coord+vec2(d,0.0),rectSize,radius);
            float u=boxSdf(coord-vec2(0.0,d),rectSize,radius);
            float b=boxSdf(coord+vec2(0.0,d),rectSize,radius);
            vec2 n=vec2(r-l,b-u);
            return n/max(length(n),0.001);
        }
        float colorSignal(vec3 c){
            float luma=dot(c,vec3(0.299,0.587,0.114));
            float chroma=length(c-vec3(luma));
            return sat((luma-0.20)*1.25+chroma*1.55);
        }
        vec3 edgeColorDrag(
            vec2 coord,
            vec2 bodyOffset,
            vec2 rectSize,
            float radius,
            float band,
            float core
        ){
            vec2 n=sdfNormalAt(coord,rectSize,radius);
            vec2 t=vec2(-n.y,n.x);
            float pull=clamp(8.0+abs(uOldA.y)*0.030,8.0,42.0);
            float smear=clamp(4.0+effectiveEdgeWidth(rectSize)*0.55,4.0,22.0);

            vec2 transported=coord+bodyOffset;
            vec2 baseIn=transported-n*pull;
            vec2 baseFar=transported-n*(pull*1.85);
            vec2 baseOut=transported+n*(pull*0.45);

            vec3 c=sourceLensBackdrop(globalUv(baseIn))*0.28;
            c+=sourceLensBackdrop(globalUv(baseFar))*0.18;
            c+=sourceLensBackdrop(globalUv(baseOut))*0.12;
            c+=sourceLensBackdrop(globalUv(baseIn+t*smear))*0.14;
            c+=sourceLensBackdrop(globalUv(baseIn-t*smear))*0.14;
            c+=sourceLensBackdrop(globalUv(baseIn+t*smear*1.85))*0.07;
            c+=sourceLensBackdrop(globalUv(baseIn-t*smear*1.85))*0.07;

            vec3 soft=blurBackdrop(globalUv(baseIn),band)*0.45+c*0.55;
            float signal=colorSignal(c);
            float dragAlpha=band*(0.035+sat(max(uOldA.z,0.0))*0.105+core*0.030)*signal;
            return mix(vec3(0.0),soft,sat(dragAlpha));
        }
        float bodyDomeAt(vec2 coord,vec2 rectSize){
            vec2 local=clamp(coord/rectSize,0.0,1.0);
            vec2 p=local*2.0-1.0;
            p.x*=min(rectSize.x/max(rectSize.y,1.0),2.4)*0.38;
            float d=length(p);
            return pow(sat(1.0-d*0.74),1.65);
        }
        float thicknessAt(vec2 coord,vec2 rectSize,float radius){
            float sd=boxSdf(coord,rectSize,radius);
            float maskGuard=1.0-smoothstep(1.5,16.0,sd);
            float rimWide=rimWideAt(coord,rectSize,radius);
            float rimCore=rimCoreAt(coord,rectSize,radius);
            float dome=bodyDomeAt(coord,rectSize);
            float thickness=dome*0.22+rimWide*0.46+rimCore*0.34;
            return thickness*maskGuard;
        }
        vec2 softLimitPx(vec2 v,float limitPx){
            float len=length(v);
            float softLen=len/(1.0+len/max(limitPx,1.0));
            return v*(softLen/max(len,0.0001));
        }

        void main(){
            vec2 coord=vec2(gl_FragCoord.x,uResolution.y-gl_FragCoord.y);
            vec2 z=max(uRect.zw,vec2(1.0));
            vec2 p=coord-uRect.xy;
            float r=min(uRadius,min(z.x,z.y)*0.5);
            float sd=boxSdf(p,z,r);
            float mask=1.0-smoothstep(0.0,1.35,sd);
            if(mask<=0.001)discard;

            // 当前主体折射计算保持不变。
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

            // 9a6e4ac 纯净边缘折射计算。
            float stepPx=2.0;
            float tL=thicknessAt(p-vec2(stepPx,0.0),z,r);
            float tR=thicknessAt(p+vec2(stepPx,0.0),z,r);
            float tU=thicknessAt(p-vec2(0.0,stepPx),z,r);
            float tD=thicknessAt(p+vec2(0.0,stepPx),z,r);
            vec2 grad=vec2(tR-tL,tD-tU);

            float rimWide=rimWideAt(p,z,r);
            float rimCore=rimCoreAt(p,z,r);
            float dragBand=edgeDragBandAt(p,z,r);
            float gLen=length(grad);
            float gradGate=smoothstep(0.0004,0.012,gLen);
            grad*=gradGate*min(1.0,0.22/max(gLen,0.0001));
            float gradEnergy=sat(length(grad)*max(uOldA.w,0.0));

            vec2 rawRefractPx=grad*(uOldA.x+uOldA.y*rimWide)*max(uMaterial.x,0.0);
            float limitPx=mix(18.0,62.0,rimWide)+sat(abs(uOldA.y)/600.0)*16.0;
            vec2 refractPx=softLimitPx(rawRefractPx,limitPx);
            vec2 refractedUv=globalUv(p+bodyFlow)+refractPx/max(uRootResolution,vec2(1.0));

            vec3 color=blurBackdrop(refractedUv,rimWide);
            vec3 lensColor=sourceLensBackdrop(refractedUv);
            float lensMix=sat(rimCore*max(uOldA.z,0.0)*0.42);
            color=mix(color,lensColor,lensMix);

            vec3 dragColor=edgeColorDrag(p,bodyFlow,z,r,dragBand,rimCore);
            float dragMix=sat(max(max(dragColor.r,dragColor.g),dragColor.b));
            color=mix(color,dragColor,dragMix);

            float rimOpticalBoost=rimCore*0.16+gradEnergy*0.045;
            color*=uBody.w*uMaterial.z*(1.0+rimOpticalBoost);

            float debugEdge=smoothstep(-1.65,0.0,sd)*mask;
            color=mix(color,vec3(1.0,0.45,0.0),debugEdge*uOldB.w);
            color-=vec3(0.06,0.07,0.09)*uOldB.z*rimWide;
            color=clamp(color,0.0,1.0);

            gl_FragColor=vec4(
                color,
                mask*uMaterial.y*sat(uMaterial.x/20.0)*uIntensity
            );
        }
    """
}

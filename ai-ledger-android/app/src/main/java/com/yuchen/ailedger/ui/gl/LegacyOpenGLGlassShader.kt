package com.yuchen.ailedger.ui.gl

/**
 * 旧版玻璃视觉公式的轻量化实现。
 *
 * 保留原有厚度场、四点有限差分、边缘拖拽、按压和材质参数；只在每个像素内复用
 * 半尺寸、圆角核心区、尺寸倒数及根尺寸倒数。实验室入口仍将九点背景采样半径固定为 0。
 */
internal object LegacyOpenGLGlassShader {
    const val FRAGMENT_SHADER = """
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

        float sat(float x){return clamp(x,0.0,1.0);}

        float roundedBoxSdfPrepared(
            vec2 coord,
            vec2 halfSize,
            vec2 core,
            float radius
        ){
            vec2 q=abs(coord-halfSize)-core;
            return length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius;
        }

        vec2 perimeterNormalPrepared(
            vec2 coord,
            vec2 halfSize,
            vec2 core
        ){
            vec2 local=coord-halfSize;
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

        vec2 globalUvAt(vec2 visualCoord,vec2 rootInv){
            return clamp((uCardOrigin+visualCoord)*rootInv,0.0,1.0);
        }

        vec3 fallbackBackdrop(vec2 uv){
            float h=smoothstep(0.0,1.0,uv.y);
            return mix(vec3(0.12,0.22,0.38),vec3(0.36,0.50,0.72),h);
        }

        vec3 sourceBlurBackdrop(vec2 uv){
            vec2 safeUv=clamp(uv,0.0,1.0);
            if(uTextureReady<0.5){
                return fallbackBackdrop(safeUv);
            }
            return texture2D(uBlurTexture,safeUv).rgb;
        }

        vec3 sourceLensBackdrop(vec2 uv){
            vec2 safeUv=clamp(uv,0.0,1.0);
            if(uTextureReady<0.5){
                return fallbackBackdrop(safeUv);
            }
            return texture2D(uLensTexture,safeUv).rgb;
        }

        vec3 blurBackdrop(
            vec2 uv,
            float edgeWeight,
            vec2 rootInv
        ){
            float sampleRadius=uOptics.x;
            if(sampleRadius<=0.50){
                return sourceBlurBackdrop(uv);
            }
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

        float rimWideFromInside(float inside,float edgeWidth){
            return 1.0-smoothstep(0.0,edgeWidth,inside);
        }

        float rimCoreFromInside(float inside,float coreWidth){
            return 1.0-smoothstep(0.0,coreWidth,inside);
        }

        float dragBandFromInside(float inside,float dragWidth){
            return pow(
                1.0-smoothstep(0.0,dragWidth,inside),
                1.35
            );
        }

        float bodyDomeAt(vec2 coord,vec2 rectInv,float domeAspect){
            vec2 local=clamp(coord*rectInv,0.0,1.0);
            vec2 p=local*2.0-1.0;
            p.x*=domeAspect;
            float d=length(p);
            return pow(sat(1.0-d*0.74),1.65);
        }

        float thicknessAt(
            vec2 coord,
            vec2 halfSize,
            vec2 core,
            vec2 rectInv,
            float radius,
            float edgeWidth,
            float coreWidth,
            float domeAspect
        ){
            float sd=roundedBoxSdfPrepared(
                coord,halfSize,core,radius
            );
            float inside=max(-sd,0.0);
            float maskGuard=1.0-smoothstep(1.5,16.0,sd);
            float rimWide=rimWideFromInside(inside,edgeWidth);
            float rimCore=rimCoreFromInside(inside,coreWidth);
            float dome=bodyDomeAt(coord,rectInv,domeAspect);
            return (dome*0.22+rimWide*0.46+rimCore*0.34)*maskGuard;
        }

        float pressFieldAt(
            vec2 coord,
            vec2 rectInv,
            vec2 center,
            float aspect,
            float press
        ){
            vec2 delta=clamp(coord*rectInv,0.0,1.0)-center;
            delta.x*=aspect;
            float d=length(delta);
            return pow(sat(1.0-d*0.92),1.45)*press;
        }

        vec2 softLimitPx(vec2 v,float limitPx){
            float len=length(v);
            float softLen=len/(1.0+len/max(limitPx,1.0));
            return v*(softLen/max(len,0.0001));
        }

        float colorSignal(vec3 c){
            float luma=dot(c,vec3(0.299,0.587,0.114));
            float chroma=length(c-vec3(luma));
            return sat((luma-0.20)*1.25+chroma*1.55);
        }

        vec3 edgeColorDrag(
            vec2 coord,
            vec2 halfSize,
            vec2 coreGeometry,
            float band,
            float core,
            float edgeWidth,
            vec2 rootInv
        ){
            vec2 n=perimeterNormalPrepared(
                coord,halfSize,coreGeometry
            );
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

            vec3 soft=blurBackdrop(
                globalUvAt(baseIn,rootInv),
                band,
                rootInv
            )*0.45+c*0.55;
            float signal=colorSignal(c);
            float dragAlpha=band*(
                0.035+
                sat(max(uRefraction.z,0.0))*0.105+
                core*0.030
            )*signal;
            return mix(vec3(0.0),soft,sat(dragAlpha));
        }

        void main(){
            vec2 coord=vec2(gl_FragCoord.x,uResolution.y-gl_FragCoord.y);
            vec2 rectSize=max(uRect.zw,vec2(1.0));
            vec2 rectInv=1.0/rectSize;
            vec2 halfSize=rectSize*0.5;
            vec2 visualCoord=coord-uRect.xy;
            float minSize=min(rectSize.x,rectSize.y);
            float radius=min(uRadius,minSize*0.5);
            vec2 coreGeometry=max(
                halfSize-vec2(radius),vec2(0.0)
            );
            float sd=roundedBoxSdfPrepared(
                visualCoord,halfSize,coreGeometry,radius
            );
            float mask=1.0-smoothstep(0.0,1.35,sd);
            if(mask<=0.001)discard;

            vec2 rootInv=1.0/max(uRootResolution,vec2(1.0));
            float edgeWidth=clamp(uOptics.y,6.0,minSize*0.34);
            float coreWidth=max(edgeWidth*0.28,3.0);
            float dragWidth=max(edgeWidth*1.45,8.0);
            float rectAspect=rectSize.x/rectSize.y;
            float aspect=min(rectAspect,2.2);
            float domeAspect=min(rectAspect,2.4)*0.38;

            float press=uPress.x;
            vec2 pressCenter=uPress.yz;
            vec2 pressCenterPx=pressCenter*rectSize;
            float pressField=0.0;
            float pressWide=0.0;
            vec2 inwardPx=vec2(0.0);
            vec2 pressDimplePx=vec2(0.0);
            if(press>0.0){
                pressField=pressFieldAt(
                    visualCoord,
                    rectInv,
                    pressCenter,
                    aspect,
                    press
                );
                pressWide=press*pow(
                    sat(
                        1.0-length(
                            (visualCoord*rectInv-pressCenter)
                                *vec2(aspect,1.0)
                        )*0.58
                    ),
                    1.25
                );
                inwardPx=softLimitPx(
                    (pressCenterPx-visualCoord)*(
                        0.028*press+0.070*pressField
                    ),
                    24.0+press*18.0
                );
                vec2 pressDelta=visualCoord-pressCenterPx;
                vec2 pressDir=pressDelta/max(length(pressDelta),0.001);
                pressDimplePx=-pressDir*pressField*(8.0+press*10.0);
            }

            vec2 pressedCoord=visualCoord+inwardPx;
            vec2 bgUv=globalUvAt(pressedCoord,rootInv);

            float stepPx=2.0;
            float tL=thicknessAt(
                visualCoord-vec2(stepPx,0.0),
                halfSize,
                coreGeometry,
                rectInv,
                radius,
                edgeWidth,
                coreWidth,
                domeAspect
            );
            float tR=thicknessAt(
                visualCoord+vec2(stepPx,0.0),
                halfSize,
                coreGeometry,
                rectInv,
                radius,
                edgeWidth,
                coreWidth,
                domeAspect
            );
            float tU=thicknessAt(
                visualCoord-vec2(0.0,stepPx),
                halfSize,
                coreGeometry,
                rectInv,
                radius,
                edgeWidth,
                coreWidth,
                domeAspect
            );
            float tD=thicknessAt(
                visualCoord+vec2(0.0,stepPx),
                halfSize,
                coreGeometry,
                rectInv,
                radius,
                edgeWidth,
                coreWidth,
                domeAspect
            );
            vec2 grad=vec2(tR-tL,tD-tU);

            float inside=max(-sd,0.0);
            float rimWide=rimWideFromInside(inside,edgeWidth);
            float rimCore=rimCoreFromInside(inside,coreWidth);
            float dragBand=dragBandFromInside(inside,dragWidth);

            float gLen=length(grad);
            float gradGate=smoothstep(0.0004,0.012,gLen);
            float gradScale=gradGate*min(1.0,0.22/max(gLen,0.0001));
            grad*=gradScale;
            float gradEnergy=sat(gLen*gradScale*uRefraction.w);

            vec2 rawRefractPx=grad*(
                uRefraction.x+
                uRefraction.y*rimWide+
                press*(26.0+52.0*pressField)
            )*max(uMaterial.x,0.0);
            rawRefractPx+=pressDimplePx;
            rawRefractPx+=inwardPx*(0.76+0.46*rimWide);
            float limitPx=
                mix(18.0,62.0,rimWide)+
                sat(abs(uRefraction.y)/600.0)*16.0+
                press*20.0;
            vec2 refractPx=softLimitPx(rawRefractPx,limitPx);
            vec2 refractedUv=bgUv+refractPx*rootInv;

            vec3 color=blurBackdrop(
                refractedUv,
                rimWide+pressField*0.85+pressWide*0.22,
                rootInv
            );
            vec3 lensColor=sourceLensBackdrop(refractedUv);
            float lensMix=sat(
                rimCore*max(uRefraction.z,0.0)*0.42+
                pressField*0.220+
                pressWide*0.075
            );
            color=mix(color,lensColor,lensMix);

            float dragAmount=
                dragBand+
                press*rimWide*0.32+
                pressField*0.18;
            if(dragAmount>0.002){
                vec3 dragColor=edgeColorDrag(
                    visualCoord+inwardPx*0.72,
                    halfSize,
                    coreGeometry,
                    dragAmount,
                    rimCore,
                    edgeWidth,
                    rootInv
                );
                float dragMix=sat(max(max(dragColor.r,dragColor.g),dragColor.b));
                color=mix(color,dragColor,dragMix);
            }

            float rimOpticalBoost=
                rimCore*0.16+
                gradEnergy*0.045+
                press*rimCore*0.080+
                pressField*0.040;
            color*=uMaterial.z*(1.0+rimOpticalBoost);
            color*=1.0-pressField*0.070-pressWide*0.025;
            color+=vec3(0.018,0.035,0.046)*pressField*0.38;

            if(uOptics.z>0.0){
                float debugEdge=smoothstep(-1.65,0.0,sd)*mask;
                color=mix(
                    color,
                    vec3(1.0,0.45,0.0),
                    debugEdge*uOptics.z
                );
            }
            color-=vec3(0.06,0.07,0.09)*uOptics.w*rimWide;
            color=clamp(color,0.0,1.0);

            float finalAlpha=clamp(
                uMaterial.y*uMaterial.x,
                0.0,
                1.0
            )*mask;
            gl_FragColor=vec4(color,finalAlpha);
        }
    """
}

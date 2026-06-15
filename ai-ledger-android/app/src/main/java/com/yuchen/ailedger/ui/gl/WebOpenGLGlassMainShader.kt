package com.yuchen.ailedger.ui.gl

internal object WebOpenGLGlassMainShader {
    const val BODY_PREFIX = """
        void main(){
            vec2 coord=vec2(gl_FragCoord.x,uResolution.y-gl_FragCoord.y);
            vec2 z=max(uRect.zw,vec2(1.0));
            vec2 p=coord-uRect.xy;
            float r=min(uRadius,min(z.x,z.y)*0.5);
            float sd=roundedBoxSdf(p,z,r);
            // 以几何边界为中心做对称像素覆盖，避免圆弧转直边处出现外扩台阶。
            float mask=1.0-smoothstep(-0.75,0.75,sd);
            if(mask<=0.001)discard;

            float press=sat(uPress.x);
            vec2 pressCenter=clamp(uPress.yz,vec2(0.0),vec2(1.0));
            float pressField=0.0;
            float pressWide=0.0;

            float depth=insideFromSdf(sd);
            vec2 normal=perimeterNormalAt(p,z,r);
            float bodyWeight=bodyLensWeight(depth,z,r);
            vec2 pressBodyFlow=vec2(0.0);
            if(press>0.0){
                vec2 pressCenterPx=pressCenter*z;
                pressField=pressFieldAt(p,z,pressCenter,press);
                float aspect=min(z.x/max(z.y,1.0),2.2);
                pressWide=press*pow(
                    sat(1.0-length((p/z-pressCenter)*vec2(aspect,1.0))*0.58),
                    1.25
                );
                vec2 inwardPx=softLimitPx(
                    (pressCenterPx-p)*(0.028*press+0.070*pressField),
                    24.0+press*18.0
                );
                vec2 pressDelta=p-pressCenterPx;
                vec2 pressDir=pressDelta/max(length(pressDelta),0.001);
                vec2 pressDimplePx=-pressDir*pressField*(8.0+press*10.0);
                pressBodyFlow=pressDimplePx+inwardPx*(1.76+0.46*bodyWeight);
            }
            vec2 mainBodyFlow=bodyRefractionFlow(normal,z,r,depth,bodyWeight);
            vec2 centerFlow=centerTransport(p,z);
            vec2 bodyOpticalCoord=p+mainBodyFlow+centerFlow+pressBodyFlow;
            float materialWeight=bodyWeight;
            float shoulder=0.0;
            float shoulderFresnel=0.0;
            float shoulderActive=0.0;

            float width=shoulderWidth(z);
            if(depth<width){
                vec4 shoulderData=evaluateShoulderSource(
                    p,normal,z,r,depth
                );
                vec2 sourcePoint=shoulderData.xy;
                shoulder=shoulderData.z;
                shoulderFresnel=shoulderData.w;
                float sourceDepth=max(
                    -roundedBoxSdf(sourcePoint,z,r),0.0
                );
                materialWeight=bodyLensWeight(sourceDepth,z,r);
                bodyOpticalCoord=evaluateBodyOpticalCoordAt(
                    sourcePoint,z,r,pressCenter,press
                );
                shoulderActive=1.0;
            }
            vec2 bodyUv=globalUv(bodyOpticalCoord);
    """

    const val BODY_SUFFIX = """
            float bodyBlurAmount=clamp(
                uBlurAmount+pressField*0.42+pressWide*0.12,
                0.0,
                4.0
            );
            vec3 bodyColor=blurPyramidBackdropAt(bodyUv,bodyBlurAmount);
            if(press>0.001){
                vec3 pressLensColor=clearBackdrop(bodyUv);
                float pressLensMix=sat(pressField*0.150+pressWide*0.045);
                bodyColor=mix(bodyColor,pressLensColor,pressLensMix);
            }

            float dispersionStrength=clamp(uDispersion.x,0.0,1.5);
            float dispersionDistance=max(uDispersion.y,0.0);
            if(dispersionStrength>0.001&&dispersionDistance>0.001){
                float dispersionWidth=max(uDispersion.z,1.0);
                float dispersionConcentration=max(uDispersion.w,0.25);
                float edgeEnvelope=1.0-smoothstep(
                    0.0,
                    dispersionWidth,
                    depth
                );
                float cornerAmount=1.0-max(
                    abs(normal.x),
                    abs(normal.y)
                );
                float dispersionMask=pow(
                    sat(edgeEnvelope),
                    dispersionConcentration
                )*dispersionStrength*(1.0+cornerAmount*0.72);
                if(dispersionMask>0.001){
                    vec2 splitPx=normal*dispersionDistance
                        *(0.72+0.28*edgeEnvelope);
                    vec3 redSample=clearBackdrop(
                        globalUv(bodyOpticalCoord+splitPx)
                    );
                    vec3 blueSample=clearBackdrop(
                        globalUv(bodyOpticalCoord-splitPx)
                    );
                    vec3 prismColor=vec3(
                        redSample.r,
                        (redSample.g+blueSample.g)*0.5,
                        blueSample.b
                    );
                    bodyColor=mix(
                        bodyColor,
                        prismColor,
                        sat(dispersionMask)
                    );
                }
            }

            float opticalBoost=1.0+materialWeight*0.24;
            bodyColor*=uBody.w*uMaterial.z*opticalBoost;
            bodyColor-=vec3(0.055,0.065,0.085)
                *uBodyLensB.z*materialWeight;
            bodyColor*=1.0-pressField*0.070-pressWide*0.025;
            bodyColor+=vec3(0.018,0.035,0.046)*pressField*0.38;
            float bodyDebug=smoothstep(-1.6,0.0,sd)*mask;
            bodyColor=mix(
                bodyColor,
                vec3(1.0,0.45,0.0),
                bodyDebug*uBodyLensB.w
            );

            vec3 color=bodyColor;
            if(shoulderActive>0.5){
                float strength=clamp(uShoulder.w,0.0,4.0);
                float fill=shoulderMaterialFill(depth,z);
                float outerRim=pow(shoulder,2.8);
                vec2 lightDirection=normalize(vec2(-0.62,-0.78));
                float lightFacing=pow(
                    sat(dot(normal,lightDirection)),2.7
                );

                float volumeShadow=0.014*strength*fill
                    *(0.30+0.70*(1.0-lightFacing));
                color*=1.0-volumeShadow;

                float fillSheen=sat(
                    0.072*strength*fill
                    *(0.40+0.60*lightFacing)
                );
                vec3 filledColor=mix(
                    color,
                    vec3(0.88,0.96,1.0),
                    0.36
                );
                color=mix(color,filledColor,fillSheen);

                float reflection=sat(
                    0.21*strength*shoulderFresnel*outerRim
                    *(0.18+0.82*lightFacing)
                );
                vec3 reflectionColor=mix(
                    color,
                    vec3(0.93,0.98,1.0),
                    0.72
                );
                color=mix(color,reflectionColor,reflection);
            }

            float bodyAlpha=uMaterial.y
                *sat(uMaterial.x/20.0)
                *uIntensity;
            float finalAlpha=sat(mask*bodyAlpha);
            vec3 finalColor=clamp(color,0.0,1.0);
            // TextureView 透明 Surface 采用预乘 alpha；边缘 RGB 必须同步衰减。
            gl_FragColor=vec4(finalColor*finalAlpha,finalAlpha);
        }
    """
}

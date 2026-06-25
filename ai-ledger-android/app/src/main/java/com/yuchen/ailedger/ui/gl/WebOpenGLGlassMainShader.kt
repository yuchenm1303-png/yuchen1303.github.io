package com.yuchen.ailedger.ui.gl

internal object WebOpenGLGlassMainShader {
    const val BODY_PREFIX = """
        void main(){
            vec2 coord=vec2(gl_FragCoord.x,uResolution.y-gl_FragCoord.y);
            vec2 z=max(uRect.zw,vec2(1.0));
            vec2 rectInv=1.0/z;
            vec2 p=coord-uRect.xy;
            vec2 center=z*0.5;
            vec2 safeHalfSize=max(center,vec2(1.0));
            vec2 invSafeCenter=1.0/safeHalfSize;
            float minSize=min(z.x,z.y);
            float r=min(uRadius,minSize*0.5);
            vec2 sdfCore=max(center-vec2(r),vec2(0.0));
            float sd=roundedBoxSdfPrepared(p,center,sdfCore,r);
            // 以几何边界为中心做对称像素覆盖，避免圆弧转直边处出现外扩台阶。
            float mask=1.0-smoothstep(-0.75,0.75,sd);
            if(mask<=0.001)discard;

            // uPress 已在 Kotlin 侧限幅；这里只复用同一份按压几何。
            float press=uPress.x;
            vec2 pressCenter=uPress.yz;
            float pressAspect=1.0;
            float pressField=0.0;
            float pressWide=0.0;

            float depth=insideFromSdf(sd);
            vec2 normal=perimeterNormalPrepared(p,center,sdfCore);

            // 这些量只依赖本次 draw 的 uniform/几何，主体与圆肩来源点共用一次结果。
            float bodyReach=bodyLensReach(minSize,r);
            float bodyConcentration=mix(
                0.58,
                1.82,
                sat((uBodyLensA.z+10.0)/20.0)
            );
            float bodyRawPull=
                abs(uBodyLensA.y)*0.052
                +abs(uBodyLensA.x)*0.20
                +uBodyLensB.x*0.12;
            vec3 lensParams=vec3(
                bodyReach,
                bodyConcentration,
                bodyRawPull
            );
            vec3 transportParams=vec3(
                sat((uBody.x-0.18)/(1.5-0.18)),
                sat((uBody.y-0.2)/3.0),
                sat(uBody.z/900.0)
            );

            float bodyWeight=bodyLensWeightAtReach(depth,lensParams);
            vec2 pressBodyFlow=vec2(0.0);
            if(press>0.0){
                pressAspect=min(z.x/max(z.y,1.0),2.2);
                vec2 pressCenterPx=pressCenter*z;
                pressField=pressFieldAt(
                    p,rectInv,pressCenter,pressAspect,press
                );
                pressWide=press*pow(
                    sat(1.0-length(
                        (p*rectInv-pressCenter)*vec2(pressAspect,1.0)
                    )*0.58),
                    1.25
                );
                vec2 inwardPx=softLimit(
                    (pressCenterPx-p)*(0.028*press+0.070*pressField),
                    24.0+press*18.0
                );
                vec2 pressDelta=p-pressCenterPx;
                vec2 pressDir=pressDelta/max(length(pressDelta),0.001);
                vec2 pressDimplePx=-pressDir*pressField*(8.0+press*10.0);
                pressBodyFlow=pressDimplePx
                    +inwardPx*(1.76+0.46*bodyWeight);
            }
            vec4 pressOptics=vec4(pressCenter,pressAspect,press);

            vec2 mainBodyFlow=bodyRefractionFlow(
                normal,depth,bodyWeight,lensParams
            );
            vec2 centerFlow=centerTransport(
                p,
                center,
                invSafeCenter,
                minSize,
                transportParams
            );
            vec2 bodyOpticalCoord=p+mainBodyFlow+centerFlow+pressBodyFlow;
            float materialWeight=bodyWeight;
            vec2 shoulderOptics=vec2(0.0);
            float shoulderXValue=0.0;
            bool shoulderActive=false;

            float width=shoulderWidth(minSize);
            if(depth<width){
                shoulderXValue=sat(depth/max(width,1.0));
                vec3 shoulderGeometry=vec3(
                    shoulderXValue,
                    width,
                    minSize
                );
                float sourceDepth;
                vec2 sourceNormal;
                vec4 shoulderData=evaluateShoulderSource(
                    p,
                    normal,
                    center,
                    safeHalfSize,
                    sdfCore,
                    invSafeCenter,
                    r,
                    depth,
                    shoulderGeometry,
                    transportParams.y,
                    sourceDepth,
                    sourceNormal
                );
                vec2 sourcePoint=shoulderData.xy;
                shoulderOptics=shoulderData.zw;
                // 深层来源点只控制折射坐标，当前位置材质亮度继续沿用 bodyWeight。
                // 避免固定取样深度把整圈圆肩的材质权重压到接近 0，形成宽黑环。
                float sourceWeight=bodyLensWeightAtReach(
                    sourceDepth,lensParams
                );
                bodyOpticalCoord=evaluateBodyOpticalCoordAt(
                    sourcePoint,
                    sourceDepth,
                    sourceNormal,
                    z,
                    rectInv,
                    center,
                    invSafeCenter,
                    minSize,
                    lensParams,
                    sourceWeight,
                    transportParams,
                    pressOptics
                );
                shoulderActive=true;
            }
            vec2 uvRoot=max(uRootResolution,vec2(1.0));
            vec2 rootInv=1.0/uvRoot;
            vec2 uvTexel=0.5*rootInv;
            vec2 bodyUv=globalUvAt(bodyOpticalCoord,rootInv,uvTexel);
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

            // 色散参数已在 Kotlin 侧限幅；保留原最低 1px 作用宽度。
            float dispersionStrength=uDispersion.x;
            float dispersionDistance=uDispersion.y;
            if(dispersionStrength>0.001&&dispersionDistance>0.001){
                float dispersionWidth=max(uDispersion.z,1.0);
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
                    edgeEnvelope,
                    uDispersion.w
                )*dispersionStrength*(1.0+cornerAmount*0.72);
                if(dispersionMask>0.001){
                    vec2 splitPx=normal*dispersionDistance
                        *(0.72+0.28*edgeEnvelope);
                    vec3 redSample=clearBackdrop(
                        globalUvAt(
                            bodyOpticalCoord+splitPx,
                            rootInv,
                            uvTexel
                        )
                    );
                    vec3 blueSample=clearBackdrop(
                        globalUvAt(
                            bodyOpticalCoord-splitPx,
                            rootInv,
                            uvTexel
                        )
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
            if(uBodyLensB.w>0.0){
                float bodyDebug=smoothstep(-1.6,0.0,sd)*mask;
                bodyColor=mix(
                    bodyColor,
                    vec3(1.0,0.45,0.0),
                    bodyDebug*uBodyLensB.w
                );
            }

            vec3 color=bodyColor;
            if(shoulderActive){
                float strength=uShoulder.w;
                float fill=shoulderMaterialFillAtX(shoulderXValue);
                float outerRim=pow(shoulderOptics.x,2.8);
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
                    0.21*strength*shoulderOptics.y*outerRim
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
                *(uMaterial.x/20.0)
                *uIntensity;
            float finalAlpha=sat(mask*bodyAlpha);
            vec3 finalColor=clamp(color,0.0,1.0);
            // TextureView 透明 Surface 采用预乘 alpha；边缘 RGB 必须同步衰减。
            gl_FragColor=vec4(finalColor*finalAlpha,finalAlpha);
        }
    """
}

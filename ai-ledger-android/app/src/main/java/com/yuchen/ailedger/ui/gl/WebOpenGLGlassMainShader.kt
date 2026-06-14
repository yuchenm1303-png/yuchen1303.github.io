package com.yuchen.ailedger.ui.gl

internal object WebOpenGLGlassMainShader {
    const val BODY_PREFIX = """
        void main(){
            vec2 coord=vec2(gl_FragCoord.x,uResolution.y-gl_FragCoord.y);
            vec2 z=max(uRect.zw,vec2(1.0));
            vec2 p=coord-uRect.xy;
            float r=min(uRadius,min(z.x,z.y)*0.5);
            float sd=roundedBoxSdf(p,z,r);
            float mask=1.0-smoothstep(0.0,1.35,sd);
            if(mask<=0.001)discard;

            float press=sat(uPress.x);
            vec2 pressCenter=clamp(uPress.yz,vec2(0.0),vec2(1.0));
            vec2 pressCenterPx=pressCenter*z;
            float pressField=pressFieldAt(p,z,pressCenter,press);
            float aspect=min(z.x/max(z.y,1.0),2.2);
            float pressWide=press*pow(
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

            float depth=insideFromSdf(sd);
            vec2 normal=perimeterNormalAt(p,z,r);
            float bodyWeight=bodyLensWeight(depth,z,r);
            vec2 mainBodyFlow=bodyRefractionFlow(p,z,r,depth,bodyWeight);
            vec2 centerFlow=centerTransport(p,z);
            vec2 pressBodyFlow=pressDimplePx+inwardPx*(1.76+0.46*bodyWeight);
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
            gl_FragColor=vec4(
                clamp(color,0.0,1.0),
                mask*bodyAlpha
            );
        }
    """
}

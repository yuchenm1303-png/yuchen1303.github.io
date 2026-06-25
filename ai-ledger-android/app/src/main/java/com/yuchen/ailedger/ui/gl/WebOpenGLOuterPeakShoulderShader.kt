package com.yuchen.ailedger.ui.gl

/**
 * 圆肩折射使用 fc725b 的 V29.5 整圈统一内部轮廓映射。
 * 仅复用同一像素内已经得到的圆角几何常量，不改变来源点、材质或色散视觉。
 */
internal object WebOpenGLOuterPeakShoulderShader {
    const val DEFAULT_VISIBLE_WIDTH_DP = 21.716216f
    const val DEFAULT_CAPTURE_WIDTH_DP = 96f
    const val DEFAULT_MAX_ANGLE_DEG = 89.5f
    const val DEFAULT_FALLOFF_ROUNDNESS = 0f
    const val DEFAULT_MATERIAL_STRENGTH = 4f
    const val DEFAULT_TANGENTIAL_FLOW_STRENGTH = 0f

    const val SOURCE = """
        float shoulderWidth(float minSize){
            return min(max(uShoulder.x,1.0),minSize*0.46);
        }
        float shoulderCaptureWidth(float visibleWidth,float minSize){
            return min(max(uShoulderFlow.x,visibleWidth),minSize*0.46);
        }
        float shoulderOuterEnvelopeAtX(float x){
            float exponent=mix(2.0,4.8,uShoulder.z);
            return pow(max(1.0-x,0.0),exponent);
        }
        float shoulderMaterialFillAtX(float x){
            float exponent=mix(1.20,1.85,uShoulder.z);
            return pow(max(1.0-x,0.0),exponent);
        }
        vec2 unifiedInnerContourPoint(
            vec2 boundaryPoint,
            vec2 center,
            vec2 safeHalfSize,
            vec2 invSafeCenter,
            float captureWidth
        ){
            vec2 innerHalf=max(safeHalfSize-vec2(captureWidth),vec2(1.0));
            vec2 normalized=(boundaryPoint-center)*invSafeCenter;
            return center+normalized*innerHalf;
        }
        float shoulderTangentialSignal(
            vec2 p,
            vec2 tangent,
            vec2 center,
            vec2 invSafeCenter,
            float bodyCurve
        ){
            vec2 u=(p-center)*invSafeCenter;
            vec2 contourVector=vec2(-u.y,u.x);
            float contourLength=length(contourVector);
            float contourSignal=0.0;
            if(contourLength>0.0001){
                contourSignal=dot(contourVector/contourLength,tangent);
            }
            float bodySignal=dot(polynomialTransport(u,bodyCurve),tangent);
            float mixed=0.48*contourSignal+0.52*bodySignal;
            return mixed/(0.65+abs(mixed));
        }
        float shoulderTangentialTravel(
            vec2 p,
            vec2 tangent,
            vec2 center,
            vec2 invSafeCenter,
            float captureWidth,
            float envelope,
            float bodyCurve
        ){
            float flowStrength=uShoulderFlow.y;
            if(flowStrength<=0.0001){
                return 0.0;
            }
            float amplitude=captureWidth*0.30*(flowStrength/2.4);
            return amplitude
                *shoulderTangentialSignal(
                    p,tangent,center,invSafeCenter,bodyCurve
                )
                *pow(envelope,0.82);
        }
        vec4 evaluateShoulderSource(
            vec2 p,
            vec2 edgeNormal,
            vec2 center,
            vec2 safeHalfSize,
            vec2 sdfCore,
            vec2 invSafeCenter,
            float r,
            float depth,
            vec3 shoulderGeometry,
            float bodyCurve,
            out float sourceDepth,
            out vec2 sourceNormal
        ){
            float envelope=shoulderOuterEnvelopeAtX(shoulderGeometry.x);
            float theta=uShoulder.y*0.01745329252*envelope;
            float captureWidth=shoulderCaptureWidth(
                shoulderGeometry.y,
                shoulderGeometry.z
            );
            vec2 tangent=vec2(-edgeNormal.y,edgeNormal.x);
            float tangentTravel=shoulderTangentialTravel(
                p,
                tangent,
                center,
                invSafeCenter,
                captureWidth,
                envelope,
                bodyCurve
            );
            vec2 boundaryPoint=p+edgeNormal*depth;
            vec2 innerContourPoint=unifiedInnerContourPoint(
                boundaryPoint,
                center,
                safeHalfSize,
                invSafeCenter,
                captureWidth
            );
            vec2 sourcePoint=
                mix(p,innerContourPoint,envelope)
                +tangent*tangentTravel;
            float sourceSd=roundedBoxSdfPrepared(
                sourcePoint,center,sdfCore,r
            );
            if(sourceSd>-0.5){
                vec2 correctionNormal=perimeterNormalPrepared(
                    sourcePoint,center,sdfCore
                );
                sourcePoint-=correctionNormal*(sourceSd+0.5);
                sourceSd=roundedBoxSdfPrepared(
                    sourcePoint,center,sdfCore,r
                );
            }
            sourceDepth=max(-sourceSd,0.0);
            sourceNormal=perimeterNormalPrepared(
                sourcePoint,center,sdfCore
            );
            float fresnelBase=1.0-cos(theta);
            float fresnel2=fresnelBase*fresnelBase;
            float fresnel=0.04+0.96*fresnel2*fresnel2*fresnelBase;
            return vec4(sourcePoint,envelope,fresnel);
        }
        vec2 evaluateBodyOpticalCoordAt(
            vec2 point,
            float pointDepth,
            vec2 pointNormal,
            vec2 z,
            vec2 center,
            vec2 invSafeCenter,
            float minSize,
            vec3 lensParams,
            float pointWeight,
            vec3 transportParams,
            vec4 pressOptics
        ){
            vec2 pressFlow=vec2(0.0);
            if(pressOptics.w>0.0){
                float pointPressField=pressFieldAt(
                    point,
                    z,
                    pressOptics.xy,
                    pressOptics.z,
                    pressOptics.w
                );
                vec2 pressCenterPx=pressOptics.xy*z;
                vec2 inwardPx=softLimit(
                    (pressCenterPx-point)
                        *(0.028*pressOptics.w+0.070*pointPressField),
                    24.0+pressOptics.w*18.0
                );
                vec2 pressDelta=point-pressCenterPx;
                vec2 pressDir=pressDelta/max(length(pressDelta),0.001);
                vec2 pressDimplePx=-pressDir*pointPressField
                    *(8.0+pressOptics.w*10.0);
                pressFlow=pressDimplePx
                    +inwardPx*(1.76+0.46*pointWeight);
            }
            return point
                +bodyRefractionFlow(
                    pointNormal,pointDepth,pointWeight,lensParams
                )
                +centerTransport(
                    point,
                    center,
                    invSafeCenter,
                    minSize,
                    transportParams
                )
                +pressFlow;
        }
    """
}

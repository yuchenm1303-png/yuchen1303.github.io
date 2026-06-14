package com.yuchen.ailedger.ui.gl

/**
 * V29.8 整圈统一映射 + 精确切向校正圆肩。
 *
 * 网页调试值以 CSS px 表示，Android 侧按 dp 处理并在 Renderer 中只乘一次 density。
 * 保持单 fragment shader、单最终纹理坐标和单 draw call；默认切向揉开为 0 时直接走零开销快路。
 */
internal object WebOpenGLOuterPeakShoulderShader {
    const val DEFAULT_VISIBLE_WIDTH_DP = 21.716216f
    const val DEFAULT_CAPTURE_WIDTH_DP = 96f
    const val DEFAULT_MAX_ANGLE_DEG = 89.5f
    const val DEFAULT_FALLOFF_ROUNDNESS = 0f
    const val DEFAULT_MATERIAL_STRENGTH = 4f
    const val DEFAULT_TANGENTIAL_FLOW_STRENGTH = 0f
    const val DEFAULT_TANGENTIAL_CORRECTION = 0.45f

    const val SOURCE = """
        float shoulderWidth(vec2 z){
            return min(
                max(uShoulder.x,1.0),
                min(z.x,z.y)*0.46
            );
        }
        float shoulderCaptureWidth(vec2 z){
            float visible=shoulderWidth(z);
            float requested=max(uShoulderFlow.x,visible);
            return min(requested,min(z.x,z.y)*0.46);
        }
        float shoulderX(float depth,vec2 z){
            return sat(depth/max(shoulderWidth(z),1.0));
        }
        float shoulderOuterEnvelope(float depth,vec2 z){
            float x=shoulderX(depth,z);
            float exponent=mix(2.0,4.8,sat(uShoulder.z));
            return pow(max(1.0-x,0.0),exponent);
        }
        float shoulderMaterialFill(float depth,vec2 z){
            float x=shoulderX(depth,z);
            float exponent=mix(1.20,1.85,sat(uShoulder.z));
            return pow(max(1.0-x,0.0),exponent);
        }
        float shoulderMaxAngle(){
            return clamp(uShoulder.y,0.0,89.5)*0.01745329252;
        }
        vec2 unifiedInnerContourPoint(
            vec2 boundaryPoint,
            vec2 z
        ){
            vec2 center=z*0.5;
            vec2 halfSize=max(z*0.5,vec2(1.0));
            float captureWidth=shoulderCaptureWidth(z);
            vec2 innerHalf=max(
                halfSize-vec2(captureWidth),
                vec2(1.0)
            );
            vec2 normalized=(boundaryPoint-center)/halfSize;
            return center+normalized*innerHalf;
        }
        vec2 tangentCorrectedUnifiedSource(
            vec2 p,
            vec2 boundaryPoint,
            vec2 edgeNormal,
            vec2 z,
            float envelope
        ){
            vec2 innerContourPoint=unifiedInnerContourPoint(
                boundaryPoint,z
            );
            vec2 source=mix(p,innerContourPoint,envelope);
            vec2 tangent=vec2(-edgeNormal.y,edgeNormal.x);
            float tangentDrift=dot(source-p,tangent);
            float axisAlignment=max(
                abs(edgeNormal.x),
                abs(edgeNormal.y)
            );
            float straightWeight=mix(
                0.35,
                1.0,
                smoothstep(0.72,1.0,axisAlignment)
            );
            float correction=sat(uShoulderFlow.z)*straightWeight;
            return source-tangent*tangentDrift*correction;
        }
        float shoulderTangentialSignal(
            vec2 p,
            vec2 edgeNormal,
            vec2 z
        ){
            vec2 u=(p-z*0.5)/max(z*0.5,vec2(1.0));
            vec2 tangent=vec2(-edgeNormal.y,edgeNormal.x);
            vec2 contourVector=vec2(-u.y,u.x);
            float contourLength=length(contourVector);
            float contourSignal=0.0;
            if(contourLength>0.0001){
                contourSignal=dot(
                    contourVector/contourLength,
                    tangent
                );
            }
            float bodySignal=dot(
                polynomialTransport(u),
                tangent
            );
            float mixed=0.48*contourSignal+0.52*bodySignal;
            return mixed/(0.65+abs(mixed));
        }
        float shoulderTangentialTravel(
            vec2 p,
            vec2 edgeNormal,
            vec2 z,
            float depth
        ){
            float flowStrength=clamp(uShoulderFlow.y,0.0,2.4);
            if(flowStrength<=0.0001){
                return 0.0;
            }
            float captureWidth=shoulderCaptureWidth(z);
            float amplitude=captureWidth*0.30*sat(flowStrength/2.4);
            float envelope=pow(
                shoulderOuterEnvelope(depth,z),
                0.82
            );
            return amplitude
                *shoulderTangentialSignal(p,edgeNormal,z)
                *envelope;
        }
        vec4 evaluateShoulderSource(
            vec2 p,
            vec2 edgeNormal,
            vec2 z,
            float r,
            float depth
        ){
            float envelope=shoulderOuterEnvelope(depth,z);
            float theta=shoulderMaxAngle()*envelope;
            vec2 boundaryPoint=p+edgeNormal*depth;
            vec2 tangent=vec2(-edgeNormal.y,edgeNormal.x);
            vec2 sourcePoint=tangentCorrectedUnifiedSource(
                p,boundaryPoint,edgeNormal,z,envelope
            );
            sourcePoint+=tangent*shoulderTangentialTravel(
                p,edgeNormal,z,depth
            );
            float sourceSd=roundedBoxSdf(sourcePoint,z,r);
            if(sourceSd>-0.5){
                vec2 sourceNormal=perimeterNormalAt(sourcePoint,z,r);
                sourcePoint-=sourceNormal*(sourceSd+0.5);
            }
            float f0=0.04;
            float cosIncidence=cos(theta);
            float fresnel=f0+(1.0-f0)
                *pow(1.0-sat(cosIncidence),5.0);
            return vec4(sourcePoint,envelope,fresnel);
        }
        vec2 evaluateBodyOpticalCoordAt(
            vec2 point,
            vec2 z,
            float r,
            vec2 pressCenter,
            float press
        ){
            float pointSd=roundedBoxSdf(point,z,r);
            float pointDepth=max(-pointSd,0.0);
            vec2 pointNormal=perimeterNormalAt(point,z,r);
            float pointWeight=bodyLensWeight(pointDepth,z,r);
            vec2 pressFlow=vec2(0.0);
            if(press>0.0){
                float pointPressField=pressFieldAt(
                    point,z,pressCenter,press
                );
                vec2 pressCenterPx=pressCenter*z;
                vec2 inwardPx=softLimitPx(
                    (pressCenterPx-point)
                        *(0.028*press+0.070*pointPressField),
                    24.0+press*18.0
                );
                vec2 pressDelta=point-pressCenterPx;
                vec2 pressDir=pressDelta/max(length(pressDelta),0.001);
                vec2 pressDimplePx=-pressDir*pointPressField
                    *(8.0+press*10.0);
                pressFlow=pressDimplePx
                    +inwardPx*(1.76+0.46*pointWeight);
            }
            return point
                +bodyRefractionFlow(
                    pointNormal,z,r,pointDepth,pointWeight
                )
                +centerTransport(point,z)
                +pressFlow;
        }
    """
}

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
            float bodyWeight=bodyLensWeight(depth,z,r);
            vec2 mainBodyFlow=bodyRefractionFlow(p,z,r,depth,bodyWeight);
            vec2 centerFlow=centerTransport(p,z);
            vec2 pressBodyFlow=pressDimplePx+inwardPx*(1.76+0.46*bodyWeight);
            vec2 totalFlow=mainBodyFlow+centerFlow+pressBodyFlow;
            vec2 bodyUv=globalUv(p+totalFlow);
    """

    const val BODY_SUFFIX = """
            vec3 bodyColor;
            if(press>0.001){
                bodyColor=blurBackdrop(bodyUv,pressField*0.85+pressWide*0.22);
                vec3 pressLensColor=clearBackdrop(bodyUv);
                float pressLensMix=sat(pressField*0.220+pressWide*0.075);
                bodyColor=mix(bodyColor,pressLensColor,pressLensMix);
            }else{
                bodyColor=blurPyramidBackdrop(bodyUv);
            }

            float opticalBoost=1.0+bodyWeight*0.24;
            bodyColor*=uBody.w*uMaterial.z*opticalBoost;
            bodyColor-=vec3(0.055,0.065,0.085)*uBodyLensB.z*bodyWeight;
            bodyColor*=1.0-pressField*0.070-pressWide*0.025;
            bodyColor+=vec3(0.018,0.035,0.046)*pressField*0.38;
            float bodyDebug=smoothstep(-1.6,0.0,sd)*mask;
            bodyColor=mix(bodyColor,vec3(1.0,0.45,0.0),bodyDebug*uBodyLensB.w);

            vec3 color=bodyColor;
            float bodyAlpha=uMaterial.y*sat(uMaterial.x/20.0)*uIntensity;
            float alpha=bodyAlpha;
            float legacyEdgeBand=edgeDragBandAt(p,z,r);
            if(legacyEdgeBand>0.001){
                vec3 legacyColor=legacyOpticalColor(p,z,r,sd,mask,legacyEdgeBand);
                color=mix(bodyColor,legacyColor,legacyEdgeBand);
                float legacyAlpha=clamp(uLegacyMaterial.y*uLegacyMaterial.x,0.0,1.0);
                alpha=max(bodyAlpha,legacyAlpha*legacyEdgeBand);
            }
            gl_FragColor=vec4(clamp(color,0.0,1.0),mask*alpha);
        }
    """
}

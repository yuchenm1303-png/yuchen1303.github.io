package com.yuchen.ailedger.ui.gl

internal object WebOpenGLLegacyEdgePressShader {
    const val SOURCE = """
        vec3 legacyOpticalColor(
            vec2 coord,
            vec2 rectSize,
            float radius,
            float sd,
            float mask,
            float dragBand
        ){
            float press=sat(uPress.x);
            vec2 pressCenter=clamp(uPress.yz,vec2(0.0),vec2(1.0));
            vec2 pressCenterPx=pressCenter*rectSize;
            float pressField=pressFieldAt(coord,rectSize,pressCenter,press);
            float aspect=min(rectSize.x/max(rectSize.y,1.0),2.2);
            float pressWide=press*pow(
                sat(1.0-length((coord/rectSize-pressCenter)*vec2(aspect,1.0))*0.58),
                1.25
            );
            vec2 inwardPx=softLimitPx(
                (pressCenterPx-coord)*(0.028*press+0.070*pressField),
                24.0+press*18.0
            );
            vec2 pressedCoord=coord+inwardPx;
            vec2 bgUv=legacyGlobalUv(pressedCoord);

            float stepPx=2.0;
            float tL=thicknessAt(coord-vec2(stepPx,0.0),rectSize,radius);
            float tR=thicknessAt(coord+vec2(stepPx,0.0),rectSize,radius);
            float tU=thicknessAt(coord-vec2(0.0,stepPx),rectSize,radius);
            float tD=thicknessAt(coord+vec2(0.0,stepPx),rectSize,radius);
            vec2 grad=vec2(tR-tL,tD-tU);
            float rimWide=rimWideAt(coord,rectSize,radius);
            float rimCore=rimCoreAt(coord,rectSize,radius);
            float gLen=length(grad);
            float gradGate=smoothstep(0.0004,0.012,gLen);
            grad*=gradGate*min(1.0,0.22/max(gLen,0.0001));
            float gradEnergy=sat(length(grad)*max(uLegacyRefraction.w,0.0));

            vec2 pressDelta=coord-pressCenterPx;
            vec2 pressDir=pressDelta/max(length(pressDelta),0.001);
            vec2 pressDimplePx=-pressDir*pressField*(8.0+press*10.0);
            vec2 rawRefractPx=grad*(
                uLegacyRefraction.x+
                uLegacyRefraction.y*rimWide+
                press*(26.0+52.0*pressField)
            )*max(uLegacyMaterial.x,0.0);
            rawRefractPx+=pressDimplePx;
            rawRefractPx+=inwardPx*(0.76+0.46*rimWide);
            float limitPx=mix(18.0,62.0,rimWide)+
                sat(abs(uLegacyRefraction.y)/600.0)*16.0+
                press*20.0;
            vec2 refractPx=softLimitPx(rawRefractPx,limitPx);
            vec2 refractedUv=bgUv+refractPx/max(uRootResolution,vec2(1.0));

            vec3 color=blurBackdrop(
                refractedUv,
                rimWide+pressField*0.85+pressWide*0.22
            );
            vec3 lensColor=sourceLensBackdrop(refractedUv);
            float lensMix=sat(
                rimCore*max(uLegacyRefraction.z,0.0)*0.42+
                pressField*0.220+
                pressWide*0.075
            );
            color=mix(color,lensColor,lensMix);

            vec3 dragColor=edgeColorDrag(
                coord+inwardPx*0.72,
                rectSize,
                radius,
                dragBand+press*rimWide*0.32+pressField*0.18,
                rimCore
            );
            float dragMix=sat(max(max(dragColor.r,dragColor.g),dragColor.b));
            color=mix(color,dragColor,dragMix);

            float rimOpticalBoost=rimCore*0.16+
                gradEnergy*0.045+
                press*rimCore*0.080+
                pressField*0.040;
            color*=uLegacyMaterial.z*(1.0+rimOpticalBoost);
            color*=1.0-pressField*0.070-pressWide*0.025;
            color+=vec3(0.018,0.035,0.046)*pressField*0.38;

            float debugEdge=smoothstep(-1.65,0.0,sd)*mask;
            color=mix(color,vec3(1.0,0.45,0.0),debugEdge*uLegacyOptics.z);
            color-=vec3(0.06,0.07,0.09)*uLegacyOptics.w*rimWide;
            return clamp(color,0.0,1.0);
        }
    """
}

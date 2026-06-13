package com.yuchen.ailedger.ui.gl

/**
 * Final app shell shader assembled from isolated optical stages.
 *
 * The tuned web body refraction owns the glass interior. The legacy 9a6e4ac analytic edge owns the
 * outer rim. A continuous ownership mask cross-fades the two fields so the low-resolution Flow Map
 * never participates in the outer rounded corner, while the body field remains unchanged inside.
 */
internal object WebOpenGLGlassShaders {
    val FRAGMENT_SHADER: String = buildString {
        append(WebOpenGLGlassShaderCommon.SOURCE)
        append(WebOpenGLGlassBodyShader.SOURCE)
        append(LegacyEdgeRefractionShader.SOURCE)
        append(MAIN_SOURCE)
    }

    private const val MAIN_SOURCE = """
        void main(){
            vec2 coord=vec2(gl_FragCoord.x,uResolution.y-gl_FragCoord.y);
            vec2 z=max(uRect.zw,vec2(1.0));
            vec2 p=coord-uRect.xy;
            float r=min(uRadius,min(z.x,z.y)*0.5);
            float sd=boxSdf(p,z,r);
            float mask=1.0-smoothstep(0.0,1.35,sd);
            if(mask<=0.001)discard;

            // Tuned body refraction: formulas and constants remain unchanged.
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

            // Legacy analytic edge field.
            float stepPx=2.0;
            float tL=thicknessAt(p-vec2(stepPx,0.0),z,r);
            float tR=thicknessAt(p+vec2(stepPx,0.0),z,r);
            float tU=thicknessAt(p-vec2(0.0,stepPx),z,r);
            float tD=thicknessAt(p+vec2(0.0,stepPx),z,r);
            vec2 grad=vec2(tR-tL,tD-tU);

            float rimWide=rimWideAt(p,z,r);
            float rimCore=rimCoreAt(p,z,r);
            float dragBand=edgeDragBandAt(p,z,r);
            float edgeOwnership=smoothstep(0.02,0.98,dragBand);
            float bodyOwnership=1.0-edgeOwnership;

            float gLen=length(grad);
            float gradGate=smoothstep(0.0004,0.012,gLen);
            grad*=gradGate*min(1.0,0.22/max(gLen,0.0001));
            float gradEnergy=sat(length(grad)*max(uOldA.w,0.0));

            vec2 rawRefractPx=grad*(uOldA.x+uOldA.y*rimWide)*max(uMaterial.x,0.0);
            float limitPx=mix(18.0,62.0,rimWide)+sat(abs(uOldA.y)/600.0)*16.0;
            vec2 refractPx=softLimitPx(rawRefractPx,limitPx);

            // The body Flow Map is progressively removed before entering the outer edge band.
            vec2 bodyUv=globalUv(p+bodyFlow*bodyOwnership);
            vec3 bodyColor=sourceBlurBackdrop(bodyUv);

            // The old edge is sampled from its own analytic SDF coordinates, never from bodyFlow.
            vec2 edgeUv=globalUv(p)+refractPx/max(uRootResolution,vec2(1.0));
            vec3 edgeColor=blurBackdrop(edgeUv,rimWide);
            vec3 lensColor=sourceLensBackdrop(edgeUv);
            float lensMix=sat(rimCore*max(uOldA.z,0.0)*0.42);
            edgeColor=mix(edgeColor,lensColor,lensMix);

            vec3 dragColor=edgeColorDrag(p,z,r,dragBand,rimCore);
            float dragMix=sat(max(max(dragColor.r,dragColor.g),dragColor.b));
            edgeColor=mix(edgeColor,dragColor,dragMix);

            // Continuous ownership keeps one visually continuous glass without letting the two
            // normal fields compete in the same rounded-corner pixels.
            vec3 color=mix(bodyColor,edgeColor,edgeOwnership);

            float rimOpticalBoost=rimCore*0.16+gradEnergy*0.045;
            color*=uBody.w*uMaterial.z*(1.0+rimOpticalBoost*edgeOwnership);

            float debugEdge=smoothstep(-1.65,0.0,sd)*mask;
            color=mix(color,vec3(1.0,0.45,0.0),debugEdge*uOldB.w);
            color-=vec3(0.06,0.07,0.09)*uOldB.z*rimWide*edgeOwnership;
            color=clamp(color,0.0,1.0);

            gl_FragColor=vec4(
                color,
                mask*uMaterial.y*sat(uMaterial.x/20.0)*uIntensity
            );
        }
    """
}

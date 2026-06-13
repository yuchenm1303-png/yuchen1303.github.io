package com.yuchen.ailedger.ui.gl

/**
 * Pure analytic edge refraction restored from commit 9a6e4ac7605da3859ff9accd4a33fe0bab7a9ddc.
 *
 * The app supplies one globally blurred backdrop texture. The edge stage therefore keeps the old
 * rounded-rectangle SDF, thickness field, normal field, drag weights and pull geometry, while using
 * that shared texture directly instead of building another full-card blur stack.
 */
internal object LegacyEdgeRefractionShader {
    const val SOURCE = """
        vec3 sourceBlurBackdrop(vec2 uv){
            return sampleBg(uv);
        }
        vec3 sourceLensBackdrop(vec2 uv){
            return sampleBg(uv);
        }
        vec3 blurBackdrop(vec2 uv,float edgeWeight){
            return sourceBlurBackdrop(uv);
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
            vec2 rectSize,
            float radius,
            float band,
            float core
        ){
            if(band<=0.001)return vec3(0.0);

            vec2 n=sdfNormalAt(coord,rectSize,radius);
            vec2 t=vec2(-n.y,n.x);
            float pull=clamp(8.0+abs(uOldA.y)*0.030,8.0,42.0);
            float smear=clamp(4.0+effectiveEdgeWidth(rectSize)*0.55,4.0,22.0);

            vec2 baseIn=coord-n*pull;
            vec2 baseFar=coord-n*(pull*1.85);
            vec2 baseOut=coord+n*(pull*0.45);

            vec3 c=sourceLensBackdrop(globalUv(baseIn))*0.28;
            c+=sourceLensBackdrop(globalUv(baseFar))*0.18;
            c+=sourceLensBackdrop(globalUv(baseOut))*0.12;
            c+=sourceLensBackdrop(globalUv(baseIn+t*smear))*0.14;
            c+=sourceLensBackdrop(globalUv(baseIn-t*smear))*0.14;
            c+=sourceLensBackdrop(globalUv(baseIn+t*smear*1.85))*0.07;
            c+=sourceLensBackdrop(globalUv(baseIn-t*smear*1.85))*0.07;

            vec3 soft=sourceBlurBackdrop(globalUv(baseIn))*0.45+c*0.55;
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
    """
}

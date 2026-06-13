package com.yuchen.ailedger.ui.gl

internal object WebOpenGLLegacyEdgeCommonShader {
    const val SOURCE = """
        float legacyRoundedBoxSdfAt(vec2 coord,vec2 rectSize,float radius){
            return roundedBoxSdf(coord,rectSize,radius);
        }
        vec3 sourceBlurBackdrop(vec2 uv){return blurPyramidBackdrop(uv);}
        vec3 sourceLensBackdrop(vec2 uv){return clearBackdrop(uv);}
        vec3 blurBackdrop(vec2 uv,float edgeWeight){
            float blurBoost=1.0+edgeWeight*0.38;
            vec2 px=vec2(max(uLegacyOptics.x,0.0)*blurBoost)/max(uRootResolution,vec2(1.0));
            vec3 c=sourceBlurBackdrop(uv)*0.200;
            c+=sourceBlurBackdrop(uv+vec2(px.x,0.0))*0.110;
            c+=sourceBlurBackdrop(uv-vec2(px.x,0.0))*0.110;
            c+=sourceBlurBackdrop(uv+vec2(0.0,px.y))*0.110;
            c+=sourceBlurBackdrop(uv-vec2(0.0,px.y))*0.110;
            c+=sourceBlurBackdrop(uv+vec2(px.x,px.y))*0.090;
            c+=sourceBlurBackdrop(uv+vec2(-px.x,px.y))*0.090;
            c+=sourceBlurBackdrop(uv+vec2(px.x,-px.y))*0.090;
            c+=sourceBlurBackdrop(uv+vec2(-px.x,-px.y))*0.090;
            return c;
        }
        float effectiveEdgeWidth(vec2 rectSize){
            float maxSafe=min(rectSize.x,rectSize.y)*0.34;
            return clamp(uLegacyOptics.y,6.0,maxSafe);
        }
        float insideDistanceAt(vec2 coord,vec2 rectSize,float radius){
            return max(-legacyRoundedBoxSdfAt(coord,rectSize,radius),0.0);
        }
        float rimWideAt(vec2 coord,vec2 rectSize,float radius){
            float inside=insideDistanceAt(coord,rectSize,radius);
            return 1.0-smoothstep(0.0,effectiveEdgeWidth(rectSize),inside);
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
            float l=legacyRoundedBoxSdfAt(coord-vec2(d,0.0),rectSize,radius);
            float rr=legacyRoundedBoxSdfAt(coord+vec2(d,0.0),rectSize,radius);
            float u=legacyRoundedBoxSdfAt(coord-vec2(0.0,d),rectSize,radius);
            float b=legacyRoundedBoxSdfAt(coord+vec2(0.0,d),rectSize,radius);
            vec2 n=vec2(rr-l,b-u);
            return n/max(length(n),0.001);
        }
        float colorSignal(vec3 c){
            float luma=dot(c,vec3(0.299,0.587,0.114));
            float chroma=length(c-vec3(luma));
            return sat((luma-0.20)*1.25+chroma*1.55);
        }
        vec3 edgeColorDrag(vec2 coord,vec2 rectSize,float radius,float band,float core){
            vec2 n=sdfNormalAt(coord,rectSize,radius);
            vec2 t=vec2(-n.y,n.x);
            float pull=clamp(8.0+abs(uLegacyRefraction.y)*0.030,8.0,42.0);
            float smear=clamp(4.0+effectiveEdgeWidth(rectSize)*0.55,4.0,22.0);
            vec2 baseIn=coord-n*pull;
            vec2 baseFar=coord-n*(pull*1.85);
            vec2 baseOut=coord+n*(pull*0.45);
            vec3 c=sourceLensBackdrop(legacyGlobalUv(baseIn))*0.28;
            c+=sourceLensBackdrop(legacyGlobalUv(baseFar))*0.18;
            c+=sourceLensBackdrop(legacyGlobalUv(baseOut))*0.12;
            c+=sourceLensBackdrop(legacyGlobalUv(baseIn+t*smear))*0.14;
            c+=sourceLensBackdrop(legacyGlobalUv(baseIn-t*smear))*0.14;
            c+=sourceLensBackdrop(legacyGlobalUv(baseIn+t*smear*1.85))*0.07;
            c+=sourceLensBackdrop(legacyGlobalUv(baseIn-t*smear*1.85))*0.07;
            vec3 soft=blurBackdrop(legacyGlobalUv(baseIn),band)*0.45+c*0.55;
            float signal=colorSignal(c);
            float dragAlpha=band*(0.035+sat(max(uLegacyRefraction.z,0.0))*0.105+core*0.030)*signal;
            return mix(vec3(0.0),soft,sat(dragAlpha));
        }
        float bodyDomeAt(vec2 coord,vec2 rectSize){
            vec2 local=clamp(coord/rectSize,0.0,1.0);
            vec2 p=local*2.0-1.0;
            p.x*=min(rectSize.x/max(rectSize.y,1.0),2.4)*0.38;
            return pow(sat(1.0-length(p)*0.74),1.65);
        }
        float thicknessAt(vec2 coord,vec2 rectSize,float radius){
            float sd=legacyRoundedBoxSdfAt(coord,rectSize,radius);
            float maskGuard=1.0-smoothstep(1.5,16.0,sd);
            float rimWide=rimWideAt(coord,rectSize,radius);
            float rimCore=rimCoreAt(coord,rectSize,radius);
            float t=bodyDomeAt(coord,rectSize)*0.22+rimWide*0.46+rimCore*0.34;
            return t*maskGuard;
        }
        vec2 softLimitPx(vec2 v,float limitPx){
            float len=length(v);
            float softLen=len/(1.0+len/max(limitPx,1.0));
            return v*(softLen/max(len,0.0001));
        }
    """
}

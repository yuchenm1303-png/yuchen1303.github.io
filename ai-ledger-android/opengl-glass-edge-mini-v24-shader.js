'use strict';
window.OpenGLV24Shaders={
  vs:'attribute vec2 a;void main(){gl_Position=vec4(a,0.,1.);}',
  fs:`precision highp float;
uniform vec2 uRes,uOrigin,uRoot,uShapeOffset,uShapeSize;
uniform sampler2D uBlurTexture,uLensTexture;
uniform vec4 uMat,uBodyLensA,uBodyLensB,uBody;
uniform vec4 uLegacyMaterial,uLegacyRefraction,uLegacyOptics;
uniform vec4 uInnerA,uInnerB;
uniform vec4 uRimA,uRimB,uRimC;
uniform vec4 uMode;
uniform float uRadius,uIntensity,uTextureReady;

float sat(float x){return clamp(x,0.0,1.0);}
float boxSdf(vec2 p,vec2 z,float r){
  vec2 q=abs(p-z*.5)-max(z*.5-vec2(r),vec2(0.0));
  return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;
}
float insideFromSdf(float sdf){return max(-sdf,0.0);}
vec2 globalUv(vec2 p){
  vec2 root=max(uRoot,vec2(1.0));
  vec2 texel=.5/root;
  return clamp((uOrigin+p)/root,texel,1.0-texel);
}
vec3 bodyBackdrop(vec2 uv){return texture2D(uBlurTexture,clamp(uv,0.0,1.0)).rgb;}
vec2 softLimit(vec2 v,float lim){
  float n=length(v);
  float m=n/(1.0+n/max(lim,1.0));
  return v*(m/max(n,.0001));
}

/* V25.3 主体几何与折射：保持稳定基准公式。 */
vec2 perimeterNormalAt(vec2 p,vec2 z,float r){
  vec2 local=p-z*.5;
  vec2 core=max(z*.5-vec2(r),vec2(0.0));
  vec2 nearest=clamp(local,-core,core);
  vec2 radial=local-nearest;
  float radialLength=length(radial);
  if(radialLength>.0001){return radial/radialLength;}
  vec2 safeCore=max(core,vec2(1.0));
  vec2 sideRatio=abs(local)/safeCore;
  if(sideRatio.x>sideRatio.y){return vec2(local.x<0.0?-1.0:1.0,0.0);}
  return vec2(0.0,local.y<0.0?-1.0:1.0);
}
float cornerFactorAt(vec2 n){return sat(abs(n.x*n.y)*2.0);}
float bodyLensReach(vec2 z,float r){
  float requested=max(uBodyLensB.y,8.0);
  float curvatureSafe=max(r*.96,8.0);
  return min(requested,min(curvatureSafe,min(z.x,z.y)*.46));
}
float bodyLensWeight(float depth,vec2 z,float r){
  float reach=bodyLensReach(z,r);
  float x=sat(depth/max(reach,1.0));
  float smooth=x*x*(3.0-2.0*x);
  float concentration=mix(.58,1.82,sat((uBodyLensA.z+10.0)/20.0));
  return pow(1.0-smooth,concentration);
}
vec2 bodyRefractionFlow(vec2 p,vec2 z,float r,float depth,float weight){
  vec2 n=perimeterNormalAt(p,z,r);
  float rawPull=abs(uBodyLensA.y)*.052+abs(uBodyLensA.x)*.20+max(uBodyLensB.x,0.0)*.12;
  float core=pow(weight,1.28);
  float reach=bodyLensReach(z,r);
  float remaining=max(reach-depth,0.0);
  float displacement=remaining*(1.0-exp(-(rawPull*core)/max(remaining,1.0)))*.96;
  return -n*displacement;
}
float centerEnvelope(vec2 u){
  float width=sat((uBody.x-.18)/(1.5-.18));
  vec2 span=vec2(mix(.72,1.16,width),mix(.66,1.08,width));
  vec2 q=abs(u)/max(span,vec2(.001));
  return exp(-(pow(q.x,4.0)+pow(q.y,4.0)));
}
vec2 polynomialTransport(vec2 u){
  float curve=sat((uBody.y-.2)/3.0);
  float ky=mix(.10,.34,curve);
  float kx=mix(.08,.30,curve);
  float ay=mix(.24,.52,curve);
  float yRelax=mix(.18,.36,curve);
  float xBoost=mix(.10,.24,curve);
  vec2 transport=vec2(u.x*(1.0-ky*u.y*u.y),-ay*u.y*(1.0-kx*u.x*u.x));
  transport.x+=u.x*xBoost*(1.0-.58*u.y*u.y);
  transport.y+=u.y*yRelax*(1.0-.66*u.x*u.x);
  transport+=vec2(-u.y,u.x)*mix(.004,.020,curve);
  return transport;
}
vec2 centerTransport(vec2 p,vec2 z){
  vec2 u=(p-z*.5)/max(z*.5,vec2(1.0));
  float gain=sat(uBody.z/900.0);
  float curve=sat((uBody.y-.2)/3.0);
  float amplitude=min(z.x,z.y)*.5*gain*mix(.18,.46,curve);
  vec2 flow=polynomialTransport(u)*amplitude*centerEnvelope(u);
  return softLimit(flow,mix(52.0,118.0,gain));
}
vec3 sampleBodyMaterial(vec2 uv,float bodyWeight){
  vec3 color=bodyBackdrop(uv);
  float opticalBoost=1.0+bodyWeight*.24;
  color*=uBody.w*uMat.z*opticalBoost;
  color-=vec3(.055,.065,.085)*uBodyLensB.z*bodyWeight;
  return color;
}

/* 9a6e4ac 原版边缘链：只允许旧边缘和左右对比调用。 */
float legacyRoundedBoxSdfAt(vec2 coord,vec2 rectSize,float radius){
  vec2 p=coord-rectSize*.5;
  vec2 halfSize=rectSize*.5;
  vec2 q=abs(p)-max(halfSize-vec2(radius),vec2(0.0));
  return length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius;
}
vec2 legacyTexUv(vec2 uv){return clamp(uv,0.0,1.0);}
vec3 fallbackBackdrop(vec2 uv){float h=smoothstep(0.0,1.0,uv.y);return mix(vec3(.12,.22,.38),vec3(.36,.50,.72),h);}
vec3 sourceBlurBackdrop(vec2 uv){vec3 fallback=fallbackBackdrop(uv);vec3 realColor=texture2D(uBlurTexture,legacyTexUv(uv)).rgb;return mix(fallback,realColor,sat(uTextureReady));}
vec3 sourceLensBackdrop(vec2 uv){vec3 fallback=fallbackBackdrop(uv);vec3 realColor=texture2D(uLensTexture,legacyTexUv(uv)).rgb;return mix(fallback,realColor,sat(uTextureReady));}
vec3 blurBackdrop(vec2 uv,float edgeWeight){
  float blurBoost=1.0+edgeWeight*.38;
  vec2 px=vec2(max(uLegacyOptics.x,0.0)*blurBoost)/max(uRoot,vec2(1.0));
  vec3 c=sourceBlurBackdrop(uv)*.200;
  c+=sourceBlurBackdrop(uv+vec2(px.x,0.0))*.110;
  c+=sourceBlurBackdrop(uv-vec2(px.x,0.0))*.110;
  c+=sourceBlurBackdrop(uv+vec2(0.0,px.y))*.110;
  c+=sourceBlurBackdrop(uv-vec2(0.0,px.y))*.110;
  c+=sourceBlurBackdrop(uv+vec2(px.x,px.y))*.090;
  c+=sourceBlurBackdrop(uv+vec2(-px.x,px.y))*.090;
  c+=sourceBlurBackdrop(uv+vec2(px.x,-px.y))*.090;
  c+=sourceBlurBackdrop(uv+vec2(-px.x,-px.y))*.090;
  return c;
}
float effectiveEdgeWidth(vec2 rectSize){float maxSafe=min(rectSize.x,rectSize.y)*.34;return clamp(uLegacyOptics.y,6.0,maxSafe);}
float insideDistanceAt(vec2 coord,vec2 rectSize,float radius){return max(-legacyRoundedBoxSdfAt(coord,rectSize,radius),0.0);}
float rimWideAt(vec2 coord,vec2 rectSize,float radius){float inside=insideDistanceAt(coord,rectSize,radius);float w=effectiveEdgeWidth(rectSize);return 1.0-smoothstep(0.0,w,inside);}
float rimCoreAt(vec2 coord,vec2 rectSize,float radius){float inside=insideDistanceAt(coord,rectSize,radius);float w=max(effectiveEdgeWidth(rectSize)*.28,3.0);return 1.0-smoothstep(0.0,w,inside);}
float edgeDragBandAt(vec2 coord,vec2 rectSize,float radius){float inside=insideDistanceAt(coord,rectSize,radius);float w=max(effectiveEdgeWidth(rectSize)*1.45,8.0);return pow(1.0-smoothstep(0.0,w,inside),1.35);}
vec2 sdfNormalAt(vec2 coord,vec2 rectSize,float radius){
  float d=1.25;
  float l=legacyRoundedBoxSdfAt(coord-vec2(d,0.0),rectSize,radius);
  float rr=legacyRoundedBoxSdfAt(coord+vec2(d,0.0),rectSize,radius);
  float u=legacyRoundedBoxSdfAt(coord-vec2(0.0,d),rectSize,radius);
  float b=legacyRoundedBoxSdfAt(coord+vec2(0.0,d),rectSize,radius);
  vec2 n=vec2(rr-l,b-u);
  return n/max(length(n),.001);
}
float colorSignal(vec3 c){float luma=dot(c,vec3(.299,.587,.114));float chroma=length(c-vec3(luma));return sat((luma-.20)*1.25+chroma*1.55);}
vec3 edgeColorDrag(vec2 coord,vec2 rectSize,float radius,float band,float core){
  vec2 n=sdfNormalAt(coord,rectSize,radius);
  vec2 t=vec2(-n.y,n.x);
  float pull=clamp(8.0+abs(uLegacyRefraction.y)*.030,8.0,42.0);
  float smear=clamp(4.0+effectiveEdgeWidth(rectSize)*.55,4.0,22.0);
  vec2 baseIn=coord-n*pull;
  vec2 baseFar=coord-n*(pull*1.85);
  vec2 baseOut=coord+n*(pull*.45);
  vec3 c=sourceLensBackdrop(globalUv(baseIn))*.28;
  c+=sourceLensBackdrop(globalUv(baseFar))*.18;
  c+=sourceLensBackdrop(globalUv(baseOut))*.12;
  c+=sourceLensBackdrop(globalUv(baseIn+t*smear))*.14;
  c+=sourceLensBackdrop(globalUv(baseIn-t*smear))*.14;
  c+=sourceLensBackdrop(globalUv(baseIn+t*smear*1.85))*.07;
  c+=sourceLensBackdrop(globalUv(baseIn-t*smear*1.85))*.07;
  vec3 soft=blurBackdrop(globalUv(baseIn),band)*.45+c*.55;
  float signal=colorSignal(c);
  float dragAlpha=band*(.035+sat(max(uLegacyRefraction.z,0.0))*.105+core*.030)*signal;
  return mix(vec3(0.0),soft,sat(dragAlpha));
}
float bodyDomeAt(vec2 coord,vec2 rectSize){vec2 local=clamp(coord/rectSize,0.0,1.0);vec2 pp=local*2.0-1.0;pp.x*=min(rectSize.x/max(rectSize.y,1.0),2.4)*.38;float d=length(pp);return pow(sat(1.0-d*.74),1.65);}
float thicknessAt(vec2 coord,vec2 rectSize,float radius){
  float sd=legacyRoundedBoxSdfAt(coord,rectSize,radius);
  float maskGuard=1.0-smoothstep(1.5,16.0,sd);
  float rimWide=rimWideAt(coord,rectSize,radius);
  float rimCore=rimCoreAt(coord,rectSize,radius);
  float dome=bodyDomeAt(coord,rectSize);
  float tt=dome*.22+rimWide*.46+rimCore*.34;
  return tt*maskGuard;
}
vec2 softLimitPx(vec2 v,float limitPx){float len=length(v);float softLen=len/(1.0+len/max(limitPx,1.0));return v*(softLen/max(len,.0001));}
vec3 legacyEdgeColor(vec2 coord,vec2 rectSize,float radius,float sd,out float dragBand){
  vec2 bgUv=globalUv(coord);
  float stepPx=2.0;
  float tL=thicknessAt(coord-vec2(stepPx,0.0),rectSize,radius);
  float tR=thicknessAt(coord+vec2(stepPx,0.0),rectSize,radius);
  float tU=thicknessAt(coord-vec2(0.0,stepPx),rectSize,radius);
  float tD=thicknessAt(coord+vec2(0.0,stepPx),rectSize,radius);
  vec2 grad=vec2(tR-tL,tD-tU);
  float rimWide=rimWideAt(coord,rectSize,radius);
  float rimCore=rimCoreAt(coord,rectSize,radius);
  dragBand=edgeDragBandAt(coord,rectSize,radius);
  float gLen=length(grad);
  float gradGate=smoothstep(.0004,.012,gLen);
  grad*=gradGate*min(1.0,.22/max(gLen,.0001));
  float gradEnergy=sat(length(grad)*max(uLegacyRefraction.w,0.0));
  vec2 rawRefractPx=grad*(uLegacyRefraction.x+uLegacyRefraction.y*rimWide)*max(uLegacyMaterial.x,0.0);
  float limitPx=mix(18.0,62.0,rimWide)+sat(abs(uLegacyRefraction.y)/600.0)*16.0;
  vec2 refractPx=softLimitPx(rawRefractPx,limitPx);
  vec2 refractedUv=bgUv+refractPx/max(uRoot,vec2(1.0));
  vec3 color=blurBackdrop(refractedUv,rimWide);
  vec3 lensColor=sourceLensBackdrop(refractedUv);
  float lensMix=sat(rimCore*max(uLegacyRefraction.z,0.0)*.42);
  color=mix(color,lensColor,lensMix);
  vec3 dragColor=edgeColorDrag(coord,rectSize,radius,dragBand,rimCore);
  float dragMix=sat(max(max(dragColor.r,dragColor.g),dragColor.b));
  color=mix(color,dragColor,dragMix);
  float rimOpticalBoost=rimCore*.16+gradEnergy*.045;
  color*=uLegacyMaterial.z*(1.0+rimOpticalBoost);
  float debugEdge=smoothstep(-1.65,0.0,sd);
  color=mix(color,vec3(1.0,.45,0.0),debugEdge*uLegacyOptics.z);
  color-=vec3(.06,.07,.09)*uLegacyOptics.w*rimWide;
  return clamp(color,0.0,1.0);
}

/* 新双层一：基于主体材质的三点切线扩散。 */
float innerBandMask(float sd){
  float depth=max(-sd,0.0);
  float startPx=max(uInnerA.x,0.0);
  float widthPx=max(uInnerA.y,1.0);
  float endPx=startPx+widthPx;
  float feather=min(max(widthPx*.22,.75),2.75);
  float enter=smoothstep(startPx,startPx+feather,depth);
  float leave=1.0-smoothstep(endPx-feather,endPx,depth);
  return step(sd,0.0)*enter*leave;
}
vec3 innerDiffusionColor(vec2 bodyCoord,vec2 normal,vec2 tangent,float corner,float band,float bodyWeight){
  float cornerScale=1.0+corner*sat(uInnerB.y)*.30;
  float spread=max(uInnerA.z,0.0)*cornerScale;
  float normalPull=max(uInnerB.x,0.0)*band*(1.0+corner*sat(uInnerB.y)*.12);
  vec2 baseCoord=bodyCoord-normal*normalPull;
  vec2 centerUv=globalUv(baseCoord);
  vec2 tangentUv=tangent*spread/max(uRoot,vec2(1.0));
  vec3 centerColor=sampleBodyMaterial(centerUv,bodyWeight);
  vec3 plusColor=sampleBodyMaterial(centerUv+tangentUv,bodyWeight);
  vec3 minusColor=sampleBodyMaterial(centerUv-tangentUv,bodyWeight);
  vec3 diffusion=centerColor*.50+plusColor*.25+minusColor*.25;
  return mix(centerColor,diffusion,sat(uInnerA.w));
}

/* 新双层二：完全位于边界内侧的透明实体 Rim。 */
float rimMaskAt(float sd){
  float depth=max(-sd,0.0);
  float widthPx=max(uRimA.x,1.0);
  float feather=min(max(widthPx*.18,.75),1.75);
  return step(sd,0.0)*(1.0-smoothstep(widthPx-feather,widthPx,depth));
}
vec3 rimMaterialColor(vec3 rimBase,vec2 p,vec2 z,vec2 normal,float sd,float corner,out float rimCoverage){
  float depth=max(-sd,0.0);
  float widthPx=max(uRimA.x,1.0);
  float rimMask=rimMaskAt(sd);
  float opacity=sat(uRimA.y);
  float cornerGain=1.0+corner*sat(uRimC.y)*.16;
  rimCoverage=sat(rimMask*opacity*cornerGain);

  float transmission=clamp(uRimA.z,0.0,1.0);
  float tintWeight=sat(uRimA.w)*.14;
  float luma=dot(rimBase,vec3(.299,.587,.114));
  vec3 environmentTint=mix(vec3(luma),rimBase,.55)*vec3(.985,1.0,1.015);
  vec3 transmitted=rimBase*transmission;
  vec3 rimColor=mix(transmitted,environmentTint,tintWeight);

  float sectionWidth=max(min(widthPx*.16,2.25),.80);
  float innerCenter=max(widthPx-sectionWidth*1.10,sectionWidth);
  float innerLine=1.0-smoothstep(sectionWidth,sectionWidth*1.90,abs(depth-innerCenter));
  rimColor*=1.0-innerLine*sat(uRimB.x)*.18;

  float outerLine=1.0-smoothstep(0.0,sectionWidth,depth);
  vec2 local=(p-z*.5)/max(z*.5,vec2(1.0));
  vec2 lightDirection=normalize(vec2(uRimB.z,uRimB.w)+vec2(.0001));
  vec2 lightAnchor=lightDirection*1.65;
  vec2 toLight=normalize(lightAnchor-local+vec2(.0001));
  float facing=sat(dot(normal,toLight));
  float highlightPower=max(uRimC.x,1.0);
  float directional=pow(facing,highlightPower);
  float highlightAmount=outerLine*directional*sat(uRimB.y)*.42*(1.0+corner*sat(uRimC.y)*.12);
  vec3 highlightColor=mix(rimColor,vec3(1.0,.99,.965),.72);
  rimColor=mix(rimColor,highlightColor,sat(highlightAmount));

  float peak=max(max(rimColor.r,rimColor.g),rimColor.b);
  float excess=max(peak-1.0,0.0);
  rimColor/=1.0+excess*.32;
  return rimColor;
}

void main(){
  vec2 canvasP=vec2(gl_FragCoord.x,uRes.y-gl_FragCoord.y);
  vec2 p=canvasP-uShapeOffset;
  vec2 z=uShapeSize;
  float r=min(uRadius,min(z.x,z.y)*.5);
  float sd=boxSdf(p,z,r);
  float bodyMask=1.0-smoothstep(0.0,1.35,sd);
  if(bodyMask<=.001&&sd>0.0)discard;

  float depth=insideFromSdf(sd);
  vec2 normal=perimeterNormalAt(p,z,r);
  vec2 tangent=vec2(-normal.y,normal.x);
  float corner=cornerFactorAt(normal);

  float bodyWeight=bodyLensWeight(depth,z,r);
  vec2 mainBodyFlow=bodyRefractionFlow(p,z,r,depth,bodyWeight);
  vec2 centerFlow=centerTransport(p,z);
  vec2 bodyCoord=p+mainBodyFlow+centerFlow;
  vec2 bodyUv=globalUv(bodyCoord);
  vec3 bodyColor=sampleBodyMaterial(bodyUv,bodyWeight);
  float bodyDebug=smoothstep(-1.6,0.0,sd)*bodyMask;
  bodyColor=mix(bodyColor,vec3(1.0,.45,0.0),bodyDebug*uBodyLensB.w);

  float baseAlpha=sat(uMat.y*sat(uMat.x/20.0)*uIntensity);
  float bodyAlpha=bodyMask*baseAlpha;
  float debugView=uMode.y;
  float edgeMode=uMode.x;

  float innerMask=innerBandMask(sd);
  float rimMask=rimMaskAt(sd);
  bool needDual=edgeMode>1.5||debugView>1.5;
  vec3 innerColor=bodyColor;
  if(needDual&&innerMask>.001){innerColor=innerDiffusionColor(bodyCoord,normal,tangent,corner,innerMask,bodyWeight);}

  vec3 dualColor=bodyColor;
  dualColor=mix(dualColor,innerColor,innerMask*sat(uInnerB.z));
  float rimCoverage=0.0;
  vec3 rimColor=dualColor;
  if(needDual&&rimMask>.001){rimColor=rimMaterialColor(dualColor,p,z,normal,sd,corner,rimCoverage);}
  dualColor=mix(dualColor,rimColor,rimCoverage);
  float dualAlpha=bodyAlpha+(1.0-bodyAlpha)*rimCoverage*sat(uRimC.z);

  bool useLegacy=edgeMode>.5&&edgeMode<1.5;
  if(edgeMode>2.5){useLegacy=p.x<z.x*sat(uMode.z);}
  vec3 legacyColor=bodyColor;
  float legacyAlpha=bodyAlpha;
  if(useLegacy&&bodyMask>.001){
    float legacyBand=0.0;
    vec3 edgeColor=legacyEdgeColor(p,z,r,sd,legacyBand);
    legacyColor=mix(bodyColor,edgeColor,legacyBand);
    legacyAlpha=bodyMask*max(baseAlpha,clamp(uLegacyMaterial.y*uLegacyMaterial.x,0.0,1.0)*legacyBand);
  }

  vec3 color=bodyColor;
  float alpha=bodyAlpha;
  if(edgeMode>.5&&edgeMode<1.5){color=legacyColor;alpha=legacyAlpha;}
  else if(edgeMode>1.5&&edgeMode<2.5){color=dualColor;alpha=dualAlpha;}
  else if(edgeMode>2.5){color=useLegacy?legacyColor:dualColor;alpha=useLegacy?legacyAlpha:dualAlpha;}

  if(debugView>.5&&debugView<1.5){color=bodyColor;alpha=bodyAlpha;}
  else if(debugView>1.5&&debugView<2.5){color=vec3(innerMask);alpha=bodyMask*innerMask;}
  else if(debugView>2.5&&debugView<3.5){color=innerColor;alpha=bodyMask*innerMask;}
  else if(debugView>3.5&&debugView<4.5){color=vec3(rimMask);alpha=bodyMask*rimMask;}
  else if(debugView>4.5&&debugView<5.5){color=rimColor;alpha=bodyMask*rimCoverage;}
  else if(debugView>5.5&&debugView<6.5){color=dualColor;alpha=dualAlpha;}
  else if(debugView>6.5&&debugView<7.5){
    float scale=16.0;
    float insideTone=sat(-sd/scale);
    float boundary=1.0-smoothstep(0.0,1.25,abs(sd));
    color=vec3(0.0,boundary,insideTone);
    alpha=bodyMask;
  }
  else if(debugView>7.5&&debugView<8.5){color=vec3(normal*.5+.5,.5);alpha=bodyMask;}
  else if(debugView>8.5){color=vec3(tangent*.5+.5,.5);alpha=bodyMask;}

  if(alpha<=.001)discard;
  gl_FragColor=vec4(clamp(color,0.0,1.0),sat(alpha));
}`
};

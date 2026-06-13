'use strict';
window.OpenGLV24Shaders={
  vs:'attribute vec2 a;void main(){gl_Position=vec4(a,0.,1.);}',
  fs:`precision highp float;
uniform vec2 uRes,uOrigin,uRoot,uShapeOffset,uShapeSize;
uniform sampler2D uBlurTexture,uLensTexture;
uniform vec4 uMat,uBodyLensA,uBodyLensB,uBody;
uniform vec4 uLegacyMaterial,uLegacyRefraction,uLegacyOptics;
uniform vec4 uNewInnerA,uNewInnerB;
uniform vec4 uNewRimA,uNewRimB,uNewRimC;
uniform vec4 uNewSpecA,uNewSpecB;
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

/* 9a6e4ac 原版边缘链，保留用于旧模式和对比。 */
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

float innerBandMask(float sd){
  float depth=max(-sd,0.0);
  float start=max(uNewInnerA.x,0.0);
  float width=max(uNewInnerA.y,1.0);
  float nearMask=smoothstep(max(start*.20,.35),max(start,1.0),depth);
  float farMask=1.0-smoothstep(start+width*.72,start+width,depth);
  return step(sd,0.0)*nearMask*farMask;
}
vec3 innerRefractionColor(vec2 bodyCoord,vec2 normal,vec2 tangent,float corner){
  float spread=max(uNewInnerA.z,0.0)*(1.0+corner*max(uNewInnerB.y,0.0));
  float normalPull=max(uNewInnerB.x,0.0)*(1.0+corner*max(uNewInnerB.y,0.0)*.35);
  vec2 baseCoord=bodyCoord-normal*normalPull;
  vec2 root=max(uRoot,vec2(1.0));
  vec2 uv=globalUv(baseCoord);
  vec2 t1=tangent*spread/root;
  vec2 t2=tangent*(spread*1.85)/root;
  vec3 center=bodyBackdrop(uv);
  vec3 smear=center*.32;
  smear+=bodyBackdrop(uv+t1)*.23;
  smear+=bodyBackdrop(uv-t1)*.23;
  smear+=bodyBackdrop(uv+t2)*.11;
  smear+=bodyBackdrop(uv-t2)*.11;
  float strength=sat(uNewInnerA.w);
  return mix(center,smear,strength)*max(uNewInnerB.z,0.0);
}
float rimMaterialMask(float sd){
  float aa=1.15;
  float outside=step(0.0,sd);
  float outerWidth=max(uNewRimA.y,.25);
  float innerWidth=max(uNewRimA.z,.25);
  float outerMask=1.0-smoothstep(outerWidth,outerWidth+aa,max(sd,0.0));
  float innerMask=1.0-smoothstep(innerWidth,innerWidth+aa,max(-sd,0.0));
  return mix(innerMask,outerMask,outside);
}
vec4 rimMaterial(vec2 bodyUv,float sd,float corner,float rimMask){
  float outerWidth=max(uNewRimA.y,.25);
  float innerWidth=max(uNewRimA.z,.25);
  float coreWidth=max(uNewRimA.x,.5);
  float section=sat((sd+innerWidth)/max(innerWidth+outerWidth,.5));
  float sectionSigned=section*2.0-1.0;
  float materialCore=exp(-abs(sd)/coreWidth);
  float outerSurface=1.0-smoothstep(0.0,max(outerWidth,1.0),abs(sd));
  float innerCenter=-innerWidth*.72;
  float innerLine=1.0-smoothstep(.0,max(1.1,innerWidth*.18),abs(sd-innerCenter));
  vec3 blurred=bodyBackdrop(bodyUv);
  vec3 clear=sourceLensBackdrop(bodyUv);
  vec3 refracted=mix(blurred,clear,sat(uNewRimC.y));
  float luma=dot(refracted,vec3(.299,.587,.114));
  vec3 chroma=refracted-vec3(luma);
  vec3 environmentTint=chroma*uNewRimB.x*.42+vec3(.025,.045,.075)*uNewRimB.x;
  float edgeFresnel=pow(abs(sectionSigned),2.2);
  vec3 rimColor=refracted*sat(uNewRimA.w)+environmentTint;
  rimColor+=refracted*(uNewRimB.z*outerSurface*(.36+.64*edgeFresnel));
  rimColor-=vec3(.055,.065,.085)*uNewRimB.y*innerLine;
  float cornerThickness=1.0+corner*max(uNewRimB.w,0.0);
  float alpha=rimMask*sat(uNewRimC.x)*sat((.28+.72*materialCore)*cornerThickness);
  return vec4(clamp(rimColor,0.0,1.5),alpha);
}
float specularMaskAt(float sd){
  float width=max(uNewSpecA.x,.2);
  return 1.0-smoothstep(width,width+1.1,abs(sd));
}
vec4 specularRim(vec2 normal,float sd,float corner){
  float mask=specularMaskAt(sd);
  vec2 light=normalize(vec2(uNewSpecA.z,uNewSpecA.w)+vec2(.0001));
  float facing=sat(dot(normal,light));
  float directional=pow(facing,1.35);
  float width=max(uNewSpecA.x,.2);
  float section=sat(abs(sd)/width);
  float fresnel=pow(1.0-section,max(uNewSpecB.x,.2));
  float cornerGain=1.0+corner*max(uNewSpecB.y,0.0);
  float warm=sat((uNewSpecB.z+1.0)*.5);
  vec3 coolColor=vec3(.78,.90,1.0);
  vec3 warmColor=vec3(1.0,.91,.74);
  vec3 highlightColor=mix(coolColor,warmColor,warm);
  float alpha=mask*directional*fresnel*cornerGain*max(uNewSpecA.y,0.0)*sat(uNewSpecB.w);
  return vec4(highlightColor*alpha,alpha);
}
vec3 compositeTransparentRim(vec3 base,vec3 rimColor,float alpha){return base*(1.0-alpha)+rimColor*alpha;}

void main(){
  vec2 canvasP=vec2(gl_FragCoord.x,uRes.y-gl_FragCoord.y);
  vec2 p=canvasP-uShapeOffset;
  vec2 z=uShapeSize;
  float r=min(uRadius,min(z.x,z.y)*.5);
  float sd=boxSdf(p,z,r);
  float bodyMask=1.0-smoothstep(0.0,1.35,sd);
  float depth=insideFromSdf(sd);
  vec2 normal=perimeterNormalAt(p,z,r);
  vec2 tangent=vec2(-normal.y,normal.x);
  float corner=cornerFactorAt(normal);

  float bodyWeight=bodyLensWeight(depth,z,r);
  vec2 mainBodyFlow=bodyRefractionFlow(p,z,r,depth,bodyWeight);
  vec2 centerFlow=centerTransport(p,z);
  vec2 bodyCoord=p+mainBodyFlow+centerFlow;
  vec2 bodyUv=globalUv(bodyCoord);
  vec3 bodyColor=bodyBackdrop(bodyUv);
  float opticalBoost=1.0+bodyWeight*.24;
  bodyColor*=uBody.w*uMat.z*opticalBoost;
  bodyColor-=vec3(.055,.065,.085)*uBodyLensB.z*bodyWeight;
  float bodyDebug=smoothstep(-1.6,0.0,sd)*bodyMask;
  bodyColor=mix(bodyColor,vec3(1.0,.45,0.0),bodyDebug*uBodyLensB.w);

  float innerMask=innerBandMask(sd);
  float rimMask=rimMaterialMask(sd);
  float specMask=specularMaskAt(sd);
  vec3 innerColor=bodyColor;
  vec4 rim=vec4(0.0);
  vec4 spec=vec4(0.0);
  float debugView=uMode.y;
  bool needNew=uMode.x>.5||debugView>1.5;
  if(needNew&&innerMask>.001){innerColor=innerRefractionColor(bodyCoord,normal,tangent,corner);}
  if(needNew&&rimMask>.001){rim=rimMaterial(bodyUv,sd,corner,rimMask);}
  if(needNew&&specMask>.001){spec=specularRim(normal,sd,corner);}

  vec3 newColor=bodyColor;
  newColor=mix(newColor,innerColor,innerMask*sat(uNewInnerB.w));
  newColor=compositeTransparentRim(newColor,rim.rgb,rim.a);
  newColor+=spec.rgb;
  float bodyAlpha=sat(uMat.y*sat(uMat.x/20.0)*uIntensity);
  float newAlpha=max(bodyMask*bodyAlpha,max(rim.a,spec.a));

  float splitPx=z.x*sat(uMode.z);
  bool legacySide=uMode.x<.5||(uMode.x>1.5&&p.x<splitPx);
  vec3 legacyColor=bodyColor;
  float legacyAlpha=bodyMask*bodyAlpha;
  if(legacySide&&bodyMask>.001){
    float legacyBand=0.0;
    vec3 edgeColor=legacyEdgeColor(p,z,r,sd,legacyBand);
    legacyColor=mix(bodyColor,edgeColor,legacyBand);
    legacyAlpha=bodyMask*max(bodyAlpha,clamp(uLegacyMaterial.y*uLegacyMaterial.x,0.0,1.0)*legacyBand);
  }

  vec3 color=legacySide?legacyColor:newColor;
  float alpha=legacySide?legacyAlpha:newAlpha;

  if(debugView>.5&&debugView<1.5){color=bodyColor;alpha=bodyMask*bodyAlpha;}
  else if(debugView>1.5&&debugView<2.5){color=innerColor*innerMask;alpha=max(bodyMask,innerMask);}
  else if(debugView>2.5&&debugView<3.5){color=rim.rgb*rimMask;alpha=max(rimMask*.9,rim.a);}
  else if(debugView>3.5&&debugView<4.5){color=spec.rgb;alpha=max(specMask*.35,spec.a);}
  else if(debugView>4.5&&debugView<5.5){color=newColor;alpha=newAlpha;}
  else if(debugView>5.5&&debugView<6.5){
    float scale=18.0;
    float insideTone=sat(-sd/scale);
    float outsideTone=sat(sd/scale);
    float boundary=1.0-smoothstep(0.0,1.25,abs(sd));
    color=vec3(outsideTone,boundary,insideTone);
    alpha=max(bodyMask,rimMask);
  }
  else if(debugView>6.5&&debugView<7.5){color=vec3(normal*.5+.5,.5);alpha=max(bodyMask,rimMask);}
  else if(debugView>7.5&&debugView<8.5){color=vec3(tangent*.5+.5,.5);alpha=max(bodyMask,rimMask);}
  else if(debugView>8.5&&debugView<9.5){color=vec3(innerMask);alpha=max(bodyMask,innerMask);}
  else if(debugView>9.5&&debugView<10.5){color=vec3(rimMask);alpha=max(bodyMask,rimMask);}
  else if(debugView>10.5&&debugView<11.5){color=vec3(specMask);alpha=max(bodyMask,specMask);}
  else if(debugView>11.5){color=vec3(innerMask,rimMask,specMask);alpha=max(bodyMask,max(rimMask,specMask));}

  if(alpha<=.001)discard;
  gl_FragColor=vec4(clamp(color,0.0,1.0),sat(alpha));
}`
};

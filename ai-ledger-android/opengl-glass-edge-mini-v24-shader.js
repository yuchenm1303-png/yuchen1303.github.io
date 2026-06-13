'use strict';
window.OpenGLV24Shaders={
  vs:'attribute vec2 a;void main(){gl_Position=vec4(a,0.,1.);}',
  fs:`precision highp float;
uniform vec2 uRes,uOrigin,uRoot;
uniform sampler2D uTex,uFlow;
uniform vec4 uMat,uOldA,uOldB,uBody,uBand;
uniform float uRadius,uIntensity,uFlowDepth;
float sat(float x){return clamp(x,0.0,1.0);}
float boxSdf(vec2 p,vec2 z,float r){vec2 q=abs(p-z*.5)-max(z*.5-vec2(r),vec2(0.0));return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;}
float insideFromSdf(float sdf){return max(-sdf,0.0);}
vec2 globalUv(vec2 p){vec2 t=.5/max(uRoot,vec2(1.0));return clamp((uOrigin+p)/max(uRoot,vec2(1.0)),t,1.0-t);}
vec3 sampleBg(vec2 uv){return texture2D(uTex,clamp(uv,0.0,1.0)).rgb;}
float gauss(float x,float m,float w){float q=(x-m)/max(w,.0001);return exp(-q*q);}
vec2 softLimit(vec2 v,float lim){float n=length(v),m=n/(1.0+n/max(lim,1.0));return v*(m/max(n,.0001));}

/* 当前 App 已调好的主体折射：公式与参数保持一致。 */
float centerEnvelope(vec2 u){
  float width=sat((uBody.x-.18)/(1.5-.18));
  vec2 span=vec2(mix(.64,.99,width),mix(.56,.90,width));
  vec2 q=abs(u)/max(span,vec2(.001));
  return exp(-(pow(q.x,4.0)+pow(q.y,4.0)));
}
vec2 polynomialTransport(vec2 u){
  float curve=sat((uBody.y-.2)/3.0);
  float ky=mix(.14,.48,curve);
  float kx=mix(.10,.42,curve);
  float ay=mix(.44,.76,curve);
  float yRelax=mix(.24,.42,curve);
  float xBoost=mix(.08,.20,curve);
  vec2 transport=vec2(
    u.x*(1.0-ky*u.y*u.y),
    -ay*u.y*(1.0-kx*u.x*u.x)
  );
  transport.x+=u.x*xBoost*(1.0-.65*u.y*u.y);
  transport.y+=u.y*yRelax*(1.0-.75*u.x*u.x);
  vec2 tangent=vec2(-u.y,u.x);
  transport+=tangent*mix(.006,.026,curve);
  return transport;
}
float centerLimitPx(vec2 z){
  float width=sat((uBody.x-.18)/(1.5-.18));
  float gain=sat(uBody.z/900.0);
  return mix(38.0,86.0,width)*mix(.55,1.0,gain);
}
float ringBandWidthPx(vec2 z){return max(uBand.y*min(z.x,z.y)*.18,1.0);}
float ringBandCenterPx(vec2 z,float width){
  float halfMin=min(z.x,z.y)*.5;
  float raw=(1.0-uBand.x)*halfMin;
  return clamp(raw,0.0,max(uFlowDepth-width*1.20,0.0));
}
/* 主体只保留一个宽而连续的运输丘，不再生成贴边外框或双高斯同心框。 */
float bodyTransportStartPx(vec2 z){
  float width=ringBandWidthPx(z);
  float pos=sat((uBand.x-.55)/(.98-.55));
  return mix(max(width*2.40,12.0),max(width*1.20,8.0),pos);
}
float bodyTransportEndPx(vec2 z,float start){
  float width=ringBandWidthPx(z);
  float widthNorm=sat((uBand.y-.015)/(.8-.015));
  float reach=mix(uFlowDepth*.66,uFlowDepth*.88,widthNorm);
  return clamp(max(reach,start+width*4.8),start+24.0,uFlowDepth*.92);
}
float bodyTransportProfile(float depth,vec2 z){
  float start=bodyTransportStartPx(z);
  float end=bodyTransportEndPx(z,start);
  float x=sat((depth-start)/max(end-start,1.0));
  float oneMinus=1.0-x;
  return 16.0*x*x*oneMinus*oneMinus;
}
float ringSafeLimit(vec2 z,float ringSafe){
  float width=ringBandWidthPx(z);
  return max(4.0,min(width*4.20,uFlowDepth*.46))*pow(sat(ringSafe),.64);
}

/* 9a6e4ac 解析边缘折射：只读取同一张全局模糊背景。 */
vec3 sourceBlurBackdrop(vec2 uv){return sampleBg(uv);}
vec3 sourceLensBackdrop(vec2 uv){return sampleBg(uv);}
vec3 blurBackdrop(vec2 uv,float edgeWeight){return sourceBlurBackdrop(uv);}
float effectiveEdgeWidth(vec2 rectSize){
  float maxSafe=min(rectSize.x,rectSize.y)*.34;
  return clamp(uOldB.y,6.0,maxSafe);
}
float insideDistanceAt(vec2 coord,vec2 rectSize,float radius){return max(-boxSdf(coord,rectSize,radius),0.0);}
float rimWideAt(vec2 coord,vec2 rectSize,float radius){
  float inside=insideDistanceAt(coord,rectSize,radius);
  float w=effectiveEdgeWidth(rectSize);
  return 1.0-smoothstep(0.0,w,inside);
}
float rimCoreAt(vec2 coord,vec2 rectSize,float radius){
  float inside=insideDistanceAt(coord,rectSize,radius);
  float w=max(effectiveEdgeWidth(rectSize)*.28,3.0);
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
  return n/max(length(n),.001);
}
float colorSignal(vec3 c){
  float luma=dot(c,vec3(.299,.587,.114));
  float chroma=length(c-vec3(luma));
  return sat((luma-.20)*1.25+chroma*1.55);
}
vec3 edgeColorDrag(vec2 coord,vec2 rectSize,float radius,float band,float core){
  if(band<=.001)return vec3(0.0);
  vec2 n=sdfNormalAt(coord,rectSize,radius);
  vec2 t=vec2(-n.y,n.x);
  float pull=clamp(8.0+abs(uOldA.y)*.030,8.0,42.0);
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
  vec3 soft=sourceBlurBackdrop(globalUv(baseIn))*.45+c*.55;
  float signal=colorSignal(c);
  float dragAlpha=band*(.035+sat(max(uOldA.z,0.0))*.105+core*.030)*signal;
  return mix(vec3(0.0),soft,sat(dragAlpha));
}
float bodyDomeAt(vec2 coord,vec2 rectSize){
  vec2 local=clamp(coord/rectSize,0.0,1.0);
  vec2 p=local*2.0-1.0;
  p.x*=min(rectSize.x/max(rectSize.y,1.0),2.4)*.38;
  return pow(sat(1.0-length(p)*.74),1.65);
}
float thicknessAt(vec2 coord,vec2 rectSize,float radius){
  float sd=boxSdf(coord,rectSize,radius);
  float maskGuard=1.0-smoothstep(1.5,16.0,sd);
  float rimWide=rimWideAt(coord,rectSize,radius);
  float rimCore=rimCoreAt(coord,rectSize,radius);
  return (bodyDomeAt(coord,rectSize)*.22+rimWide*.46+rimCore*.34)*maskGuard;
}
vec2 softLimitPx(vec2 v,float limitPx){
  float len=length(v);
  float softLen=len/(1.0+len/max(limitPx,1.0));
  return v*(softLen/max(len,.0001));
}

void main(){
  vec2 z=uRes,p=vec2(gl_FragCoord.x,uRes.y-gl_FragCoord.y);
  float r=min(uRadius,min(z.x,z.y)*.5);
  float sd=boxSdf(p,z,r);
  float mask=1.0-smoothstep(0.0,1.35,sd);
  if(mask<=.001)discard;

  /* 网页实验：移除主体外框，仅保留单个宽运输丘；App 暂不修改。 */
  vec4 ringData=texture2D(uFlow,clamp(p/max(z,vec2(1.0)),0.0,1.0));
  float ringSafe=ringData.a;
  vec2 ringFlow=vec2(0.0);
  if(ringSafe>.001){
    float ringDepth=ringData.r*uFlowDepth;
    vec2 ringN=ringData.gb*2.0-1.0;
    ringN/=max(length(ringN),.0001);
    float profile=bodyTransportProfile(ringDepth,z);
    float strength=uBand.z*.78;
    ringFlow=-ringN*(profile*strength)*ringSafe;
    ringFlow=softLimit(ringFlow,ringSafeLimit(z,ringSafe));
  }
  vec2 u=(p-z*.5)/max(z*.5,vec2(1.0));
  float centerGain=sat(uBody.z/900.0);
  float centerCurve=sat((uBody.y-.2)/3.0);
  float centerAmplitude=min(z.x,z.y)*.5*centerGain*mix(.10,.24,centerCurve);
  vec2 centerFlow=polynomialTransport(u)*centerAmplitude*centerEnvelope(u);
  centerFlow=softLimit(centerFlow,centerLimitPx(z));
  vec2 bodyFlow=ringFlow+centerFlow;

  /* 旧版解析边缘独占最外圈。 */
  float stepPx=2.0;
  float tL=thicknessAt(p-vec2(stepPx,0.0),z,r);
  float tR=thicknessAt(p+vec2(stepPx,0.0),z,r);
  float tU=thicknessAt(p-vec2(0.0,stepPx),z,r);
  float tD=thicknessAt(p+vec2(0.0,stepPx),z,r);
  vec2 grad=vec2(tR-tL,tD-tU);
  float rimWide=rimWideAt(p,z,r);
  float rimCore=rimCoreAt(p,z,r);
  float dragBand=edgeDragBandAt(p,z,r);
  float edgeOwnership=smoothstep(.02,.98,dragBand);
  float bodyOwnership=1.0-edgeOwnership;
  float gLen=length(grad);
  float gradGate=smoothstep(.0004,.012,gLen);
  grad*=gradGate*min(1.0,.22/max(gLen,.0001));
  float gradEnergy=sat(length(grad)*max(uOldA.w,0.0));
  vec2 rawRefractPx=grad*(uOldA.x+uOldA.y*rimWide)*max(uMat.x,0.0);
  float limitPx=mix(18.0,62.0,rimWide)+sat(abs(uOldA.y)/600.0)*16.0;
  vec2 refractPx=softLimitPx(rawRefractPx,limitPx);

  vec2 bodyUv=globalUv(p+bodyFlow*bodyOwnership);
  vec3 bodyColor=sourceBlurBackdrop(bodyUv);
  vec2 edgeUv=globalUv(p)+refractPx/max(uRoot,vec2(1.0));
  vec3 edgeColor=blurBackdrop(edgeUv,rimWide);
  vec3 lensColor=sourceLensBackdrop(edgeUv);
  float lensMix=sat(rimCore*max(uOldA.z,0.0)*.42);
  edgeColor=mix(edgeColor,lensColor,lensMix);
  vec3 dragColor=edgeColorDrag(p,z,r,dragBand,rimCore);
  float dragMix=sat(max(max(dragColor.r,dragColor.g),dragColor.b));
  edgeColor=mix(edgeColor,dragColor,dragMix);

  vec3 color=mix(bodyColor,edgeColor,edgeOwnership);
  float rimOpticalBoost=rimCore*.16+gradEnergy*.045;
  color*=uBody.w*uMat.z*(1.0+rimOpticalBoost*edgeOwnership);
  float debugEdge=smoothstep(-1.65,0.0,sd)*mask;
  color=mix(color,vec3(1.0,.45,0.0),debugEdge*uOldB.w);
  color-=vec3(.06,.07,.09)*uOldB.z*rimWide*edgeOwnership;
  color=clamp(color,0.0,1.0);
  gl_FragColor=vec4(color,mask*uMat.y*sat(uMat.x/20.0)*uIntensity);
}`
};

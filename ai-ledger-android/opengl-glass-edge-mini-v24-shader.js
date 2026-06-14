'use strict';
window.OpenGLV24Shaders={
  vs:'attribute vec2 a;void main(){gl_Position=vec4(a,0.,1.);}',
  fs:`precision highp float;
uniform vec2 uRes,uOrigin,uRoot;
uniform sampler2D uBlurTexture;
uniform vec4 uMat,uBodyLensA,uBodyLensB,uBody,uBodyEdge;
uniform float uRadius,uIntensity,uEdgeMode;

float sat(float x){return clamp(x,0.0,1.0);}
float smoother01(float x){
  x=sat(x);
  return x*x*x*(x*(x*6.0-15.0)+10.0);
}
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
vec3 bodyBackdrop(vec2 uv){
  return texture2D(uBlurTexture,clamp(uv,0.0,1.0)).rgb;
}
vec2 softLimit(vec2 v,float lim){
  float n=length(v);
  float m=n/(1.0+n/max(lim,1.0));
  return v*(m/max(n,.0001));
}

/*
 * V26.6 Aggressive Monotonic Capsule Lens
 * 胶囊边缘先生成唯一来源点，再进入原始 V25.3 主体折射场。
 * 映射导数始终为正，同时允许内侧有限回弹放大。
 */
vec2 perimeterNormalAt(vec2 p,vec2 z,float r){
  vec2 local=p-z*.5;
  vec2 core=max(z*.5-vec2(r),vec2(0.0));
  vec2 nearest=clamp(local,-core,core);
  vec2 radial=local-nearest;
  float radialLength=length(radial);
  if(radialLength>.0001){return radial/radialLength;}
  vec2 safeCore=max(core,vec2(1.0));
  vec2 sideRatio=abs(local)/safeCore;
  if(sideRatio.x>sideRatio.y){
    return vec2(local.x<0.0?-1.0:1.0,0.0);
  }
  return vec2(0.0,local.y<0.0?-1.0:1.0);
}

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

float capsuleWidth(vec2 z){
  return min(
      max(uBodyEdge.x,1.0),
      min(z.x,z.y)*.44
  );
}

float capsuleStrength(){
  return sat((uBodyEdge.w-.20)/2.30);
}

float capsuleDrive(vec2 z){
  float width=capsuleWidth(z);
  float opticalResponse=1.0-exp(
      -max(uBodyEdge.y,0.0)/max(width*.78,1.0)
  );
  float strength=capsuleStrength();
  return sat(opticalResponse*(.66+.54*strength));
}

/* 外沿映射斜率：越小，外沿压缩越强。 */
float capsuleOuterSlope(vec2 z){
  float drive=capsuleDrive(z);
  float curveNorm=sat((uBodyEdge.z-.35)/(2.40-.35));
  float slope=mix(.30,.16,drive);
  slope*=mix(.88,1.12,curveNorm);
  return clamp(slope,.14,.34);
}

/*
 * 零积分回弹项的幅度。
 * 它只重新分配压缩率，不改变两个端点，因此不会造成接缝。
 */
float capsuleRebound(vec2 z){
  float drive=capsuleDrive(z);
  float curveNorm=sat((uBodyEdge.z-.35)/(2.40-.35));
  float rebound=mix(.22,.50,drive);
  rebound*=mix(1.08,.90,curveNorm);
  return clamp(rebound,.18,.52);
}

/*
 * 正导数轮廓：
 * 外沿强压缩 -> 中后段有限回弹 -> 内沿导数精确回到 1。
 */
float capsuleSlopeProfile(float x,vec2 z){
  float m0=capsuleOuterSlope(z);
  float rebound=capsuleRebound(z);
  float smoothRise=3.0*x*x-2.0*x*x*x;
  float zeroAreaShape=4.0*x*(1.0-x)*(2.0*x-1.0);
  float slope=
      m0
      +(1.0-m0)*smoothRise
      +rebound*zeroAreaShape;
  return clamp(slope,.012,1.24);
}

/*
 * 对正导数轮廓进行解析积分。
 * y(0)=(1-m0)/2，y(1)=1，且整个映射严格向内单调。
 */
float capsuleMappedDepth(float depth,vec2 z,float r){
  float width=capsuleWidth(z);
  if(uEdgeMode<.5||depth>=width){return depth;}

  float x=sat(depth/max(width,1.0));
  float x2=x*x;
  float x3=x2*x;
  float x4=x2*x2;
  float m0=capsuleOuterSlope(z);
  float rebound=capsuleRebound(z);

  float outerSource=.5*(1.0-m0);
  float baseIntegral=
      m0*x
      +(1.0-m0)*(x3-.5*x4);
  float zeroAreaIntegral=
      -2.0*x2*(1.0-x)*(1.0-x);

  float mapped=
      outerSource
      +baseIntegral
      +rebound*zeroAreaIntegral;

  return width*clamp(mapped,0.0,1.0);
}

float capsuleMappedSlope(float depth,vec2 z,float r){
  float width=capsuleWidth(z);
  if(uEdgeMode<.5||depth>=width){return 1.0;}
  float x=sat(depth/max(width,1.0));
  return capsuleSlopeProfile(x,z);
}

float capsuleShoulderAt(float depth,vec2 z,float r){
  float width=capsuleWidth(z);
  if(uEdgeMode<.5||depth>=width){return 0.0;}
  return 1.0-smoother01(depth/max(width,1.0));
}

/* 原始 V25.3 主体法线折射，不包含胶囊映射。 */
vec2 baseBodyRefractionFlow(
  vec2 n,
  vec2 z,
  float r,
  float depth,
  float weight
){
  float rawPull=
      abs(uBodyLensA.y)*.052
      +abs(uBodyLensA.x)*.20
      +max(uBodyLensB.x,0.0)*.12;
  float core=pow(weight,1.28);
  float reach=bodyLensReach(z,r);
  float remaining=max(reach-depth,0.0);
  float displacement=
      remaining
      *(1.0-exp(-(rawPull*core)/max(remaining,1.0)))
      *.96;
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
  vec2 transport=vec2(
      u.x*(1.0-ky*u.y*u.y),
      -ay*u.y*(1.0-kx*u.x*u.x)
  );
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

float evaluateBodyWeightAt(vec2 point,vec2 z,float r){
  float pointSd=boxSdf(point,z,r);
  return bodyLensWeight(max(-pointSd,0.0),z,r);
}

vec2 evaluateBodyOpticalCoordAt(
  vec2 point,
  vec2 z,
  float r,
  float pointWeight
){
  float pointSd=boxSdf(point,z,r);
  float pointDepth=max(-pointSd,0.0);
  vec2 pointNormal=perimeterNormalAt(point,z,r);
  return point
      +baseBodyRefractionFlow(
          pointNormal,z,r,pointDepth,pointWeight
      )
      +centerTransport(point,z);
}

vec3 sampleBodyMaterial(vec2 uv,float bodyWeight){
  vec3 color=bodyBackdrop(uv);
  float opticalBoost=1.0+bodyWeight*.24;
  color*=uBody.w*uMat.z*opticalBoost;
  color-=vec3(.055,.065,.085)*uBodyLensB.z*bodyWeight;
  return color;
}

void main(){
  vec2 p=vec2(gl_FragCoord.x,uRes.y-gl_FragCoord.y);
  vec2 z=uRes;
  float r=min(uRadius,min(z.x,z.y)*.5);
  float sd=boxSdf(p,z,r);
  float bodyMask=1.0-smoothstep(0.0,1.35,sd);
  if(bodyMask<=.001)discard;

  float depth=insideFromSdf(sd);
  float bodyWeight=bodyLensWeight(depth,z,r);
  vec2 normal=perimeterNormalAt(p,z,r);

  /*
   * 胶囊映射先决定来源点；来源点再进入同一套 V25.3 主体折射。
   */
  float mappedDepth=capsuleMappedDepth(depth,z,r);
  float capsuleOffset=max(mappedDepth-depth,0.0);
  vec2 opticalSourcePoint=p-normal*capsuleOffset;
  float sourceWeight=evaluateBodyWeightAt(
      opticalSourcePoint,z,r
  );
  vec2 bodyOpticalCoord=evaluateBodyOpticalCoordAt(
      opticalSourcePoint,z,r,sourceWeight
  );
  vec3 color=sampleBodyMaterial(
      globalUv(bodyOpticalCoord),sourceWeight
  );

  /*
   * 强观察版材质：压缩区强调体积，回弹区形成有限内侧聚光。
   * 所有明暗都来自同一映射导数，不生成独立边框。
   */
  float shoulder=capsuleShoulderAt(depth,z,r);
  float mappedSlope=capsuleMappedSlope(depth,z,r);
  float compression=sat((1.0-mappedSlope)/.86);
  float rebound=sat((mappedSlope-1.0)/.24);
  float outerVolume=pow(compression,.62)*shoulder;
  float innerCaustic=pow(rebound,.78)*(1.0-shoulder*.32);

  vec2 lightDirection=normalize(vec2(-.62,-.78));
  float lightFacing=pow(sat(dot(normal,lightDirection)),2.65);
  float transmission=
      1.0
      -.040*outerVolume
      -.010*innerCaustic;
  float directionalLift=
      .115*outerVolume*lightFacing;
  float innerLift=.085*innerCaustic;
  color*=transmission+directionalLift+innerLift;

  float bodyDebug=smoothstep(-1.6,0.0,sd)*bodyMask;
  color=mix(
      color,
      vec3(1.0,.45,0.0),
      bodyDebug*uBodyLensB.w
  );

  float alpha=bodyMask*sat(
      uMat.y*sat(uMat.x/20.0)*uIntensity
  );
  if(alpha<=.001)discard;
  gl_FragColor=vec4(clamp(color,0.0,1.0),sat(alpha));
}`
};

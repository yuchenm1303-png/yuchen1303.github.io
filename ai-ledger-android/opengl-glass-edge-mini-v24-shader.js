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
 * V26.5 Monotonic Capsule Depth Mapping
 * 胶囊边缘是主体折射场最外侧的单调深度重映射。
 * 不存在独立 rim 坐标、边框图像或额外纹理采样。
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

/*
 * 外沿对应的内部来源深度比例。
 * 光学厚度和折射强度只改变比例，但始终限制在圆肩宽度以内。
 */
float capsuleOuterSourceRatio(vec2 z){
  float width=capsuleWidth(z);
  float opticalResponse=1.0-exp(
      -max(uBodyEdge.y,0.0)/max(width*1.15,1.0)
  );
  float strength=capsuleStrength();
  float drive=opticalResponse*(.55+.45*strength);
  float ratio=mix(.18,.46,sat(drive));

  float curveNorm=sat((uBodyEdge.z-.35)/(2.40-.35));
  ratio*=mix(.94,1.06,curveNorm);
  return clamp(ratio,.16,.48);
}

/* 外沿局部压缩率，始终保持为正值。 */
float capsuleOuterSlope(){
  float strength=capsuleStrength();
  float curveNorm=sat((uBodyEdge.z-.35)/(2.40-.35));
  float slope=mix(.36,.14,strength);
  slope*=mix(1.10,.82,curveNorm);
  return clamp(slope,.10,.42);
}

/*
 * 三次 Hermite 单调映射：
 * depth=0 时从圆肩内部取样；
 * depth=width 时 sourceDepth=depth 且导数精确等于 1；
 * 因此进入主体时位置与变化率都连续。
 */
float capsuleMappedDepth(float depth,vec2 z,float r){
  float width=capsuleWidth(z);
  if(uEdgeMode<.5||depth>=width){return depth;}

  float x=sat(depth/max(width,1.0));
  float x2=x*x;
  float x3=x2*x;

  float h00=2.0*x3-3.0*x2+1.0;
  float h10=x3-2.0*x2+x;
  float h01=-2.0*x3+3.0*x2;
  float h11=x3-x2;

  float outerRatio=capsuleOuterSourceRatio(z);
  float outerSlope=capsuleOuterSlope();
  float normalizedDepth=
      h00*outerRatio
      +h10*outerSlope
      +h01
      +h11;

  return width*normalizedDepth;
}

/* 解析导数用于材质体积感，不需要额外纹理采样。 */
float capsuleMappedSlope(float depth,vec2 z,float r){
  float width=capsuleWidth(z);
  if(uEdgeMode<.5||depth>=width){return 1.0;}

  float x=sat(depth/max(width,1.0));
  float x2=x*x;

  float dh00=6.0*x2-6.0*x;
  float dh10=3.0*x2-4.0*x+1.0;
  float dh01=-6.0*x2+6.0*x;
  float dh11=3.0*x2-2.0*x;

  float outerRatio=capsuleOuterSourceRatio(z);
  float outerSlope=capsuleOuterSlope();
  float slope=
      dh00*outerRatio
      +dh10*outerSlope
      +dh01
      +dh11;

  return clamp(slope,.08,1.0);
}

float capsuleShoulderAt(float depth,vec2 z,float r){
  float width=capsuleWidth(z);
  if(uEdgeMode<.5||depth>=width){return 0.0;}
  return 1.0-smoother01(depth/max(width,1.0));
}

vec2 bodyRefractionFlow(
  vec2 p,
  vec2 n,
  vec2 z,
  float r,
  float depth,
  float weight
){
  /* 原始 V25.3 主体法线折射保持不变。 */
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

  /*
   * 胶囊附加位移直接来自严格单调的 sourceDepth-depth。
   * 不再使用 refract 位移上限或切向运输。
   */
  float mappedDepth=capsuleMappedDepth(depth,z,r);
  float capsuleOffset=max(mappedDepth-depth,0.0);

  return -n*(displacement+capsuleOffset);
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

  /* 始终只有这一套主体坐标和一次背景纹理采样。 */
  vec2 mainBodyFlow=bodyRefractionFlow(
      p,normal,z,r,depth,bodyWeight
  );
  vec2 centerFlow=centerTransport(p,z);
  vec2 bodyOpticalCoord=p+mainBodyFlow+centerFlow;
  vec3 color=sampleBodyMaterial(
      globalUv(bodyOpticalCoord),bodyWeight
  );

  /*
   * 厚度明暗来自同一深度映射的压缩率。
   * 不生成独立白边，也不把透明圆肩压成黑色实体。
   */
  float shoulder=capsuleShoulderAt(depth,z,r);
  float mappedSlope=capsuleMappedSlope(depth,z,r);
  float compression=sat(1.0-mappedSlope);
  float volume=pow(compression,.78)*shoulder;

  vec2 lightDirection=normalize(vec2(-.62,-.78));
  float lightFacing=pow(sat(dot(normal,lightDirection)),3.0);
  float transmission=1.0-.018*volume;
  float directionalLift=.052*volume*lightFacing;
  color*=transmission+directionalLift;

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

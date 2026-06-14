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
 * V26.3 Single Body Optical Field
 * 以 V26.1 稳定主体场为基准。
 * 沿边界流动仅作用于最外侧窄带，不改写主体内部坐标。
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

/* 主过渡带与长尾共同把边缘压缩连续融入主体。 */
float integratedBodyEdgeProfile(float depth,vec2 z,float r){
  float requestedWidth=max(uBodyEdge.x,1.0);
  float width=min(requestedWidth,min(z.x,z.y)*.44);

  float primaryX=sat(depth/max(width,1.0));
  float primary=1.0-smoother01(primaryX);

  float tailWidth=min(
      width*1.85,
      min(z.x,z.y)*.47
  );
  float tailX=sat(depth/max(tailWidth,1.0));
  float tail=1.0-smoother01(tailX);

  float curve=clamp(uBodyEdge.z,.25,3.0);
  float primaryCurve=mix(.82,1.28,sat(curve/3.0));
  float tailCurve=mix(.72,1.08,sat(curve/3.0));

  primary=pow(max(primary,0.0),primaryCurve);
  tail=pow(max(tail,0.0),tailCurve);

  return mix(primary,tail,.50)*sat(uEdgeMode);
}

/* 仅最外侧约 40% 的边缘宽度参与弧向运输。 */
float outerArcMask(float depth,vec2 z){
  float width=min(
      max(uBodyEdge.x,1.0),
      min(z.x,z.y)*.44
  );
  float arcWidth=max(width*.42,3.0);
  return (1.0-smoother01(depth/arcWidth))*sat(uEdgeMode);
}

float roundedCornernessAt(vec2 p,vec2 z,float r){
  vec2 local=abs(p-z*.5);
  vec2 core=max(z*.5-vec2(r),vec2(0.0));
  vec2 cornerOffset=max(local-core,vec2(0.0));
  float bothAxes=min(cornerOffset.x,cornerOffset.y);
  return smoother01(sat(bothAxes/max(r*.42,1.0)));
}

/*
 * 在稳定法向来源点上增加一个小幅、全圈同向的弧向位移。
 * 使用中点切线修正圆角方向，不使用 sign()，因此中线不会翻转成 X。
 */
vec2 capsuleArcSource(
  vec2 basePoint,
  vec2 originalPoint,
  vec2 z,
  float r,
  float depth,
  float normalDisplacement
){
  float arcMask=outerArcMask(depth,z);
  float edgeWidth=min(
      max(uBodyEdge.x,1.0),
      min(z.x,z.y)*.44
  );
  float cornerness=roundedCornernessAt(originalPoint,z,r);
  float flowStrength=max(uBodyEdge.w,0.0);

  vec2 local=(originalPoint-z*.5)/max(z*.5,vec2(1.0));
  float modulation=.82+.18*sin((local.x-local.y)*3.14159265);
  float arcTravel=edgeWidth
      *.18
      *flowStrength
      *arcMask
      *modulation
      *mix(.88,1.36,cornerness);
  arcTravel=min(arcTravel,edgeWidth*.24);

  vec2 n0=perimeterNormalAt(basePoint,z,r);
  vec2 t0=vec2(-n0.y,n0.x);
  vec2 midpoint=basePoint+t0*(arcTravel*.5);
  vec2 nm=perimeterNormalAt(midpoint,z,r);
  vec2 tm=vec2(-nm.y,nm.x);
  vec2 direction=t0+tm;
  direction/=max(length(direction),.0001);

  vec2 point=basePoint+direction*arcTravel;

  /* 只做一次柔和等深度校正，避免抵消圆角流动。 */
  float desiredDepth=max(-boxSdf(basePoint,z,r),0.0);
  float pointDepth=max(-boxSdf(point,z,r),0.0);
  vec2 pointNormal=perimeterNormalAt(point,z,r);
  point-=pointNormal*(desiredDepth-pointDepth)*.38;

  vec2 arcOffset=point-basePoint;
  arcOffset=softLimit(
      arcOffset,
      min(edgeWidth*.26,normalDisplacement*.38+edgeWidth*.12+1.0)
  );
  return basePoint+arcOffset;
}

vec2 bodyRefractionFlow(
  vec2 p,
  vec2 n,
  vec2 z,
  float r,
  float depth,
  float weight
){
  /* 原始 V25.3 主体法线折射。 */
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

  /* V26.1 的稳定边缘压缩和软上限。 */
  float edgeProfile=integratedBodyEdgeProfile(depth,z,r);
  float edgeWidth=min(
      max(uBodyEdge.x,1.0),
      min(z.x,z.y)*.44
  );
  float requestedEdgePull=max(uBodyEdge.y,0.0);
  float safeEdgePull=edgeWidth
      *(1.0-exp(-requestedEdgePull/max(edgeWidth,1.0)));
  float edgePull=safeEdgePull*edgeProfile;
  float normalDisplacement=
      displacement
      +edgePull*(.60+.22*weight);

  vec2 basePoint=p-n*normalDisplacement;
  vec2 sourcePoint=capsuleArcSource(
      basePoint,p,z,r,depth,normalDisplacement
  );

  return sourcePoint-p;
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

  /* 始终只有这一套主体坐标和一次背景采样。 */
  vec2 mainBodyFlow=bodyRefractionFlow(
      p,normal,z,r,depth,bodyWeight
  );
  vec2 centerFlow=centerTransport(p,z);
  vec2 bodyOpticalCoord=p+mainBodyFlow+centerFlow;
  vec3 color=sampleBodyMaterial(
      globalUv(bodyOpticalCoord),bodyWeight
  );

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

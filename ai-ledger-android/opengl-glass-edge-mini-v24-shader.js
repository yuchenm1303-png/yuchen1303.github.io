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
 * V26.4 Single Body Capsule Lens Field
 * 胶囊边缘不是独立 rim，而是主体折射场最外侧的连续圆肩透镜。
 * 整块玻璃始终只有一个 bodyOpticalCoord 和一次背景纹理采样。
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

/*
 * 单侧圆肩截面：外沿坡度最大，向内平滑躺平。
 * 末端保留极弱长尾，只负责消除与主体场的接缝。
 */
float capsuleShoulderAt(float depth,vec2 z,float r){
  float width=min(
      max(uBodyEdge.x,1.0),
      min(z.x,z.y)*.44
  );
  float curve=clamp(uBodyEdge.z,.35,2.4);

  float primary=1.0-smoother01(depth/max(width,1.0));
  primary=pow(max(primary,0.0),curve);

  float tailWidth=min(width*1.38,min(z.x,z.y)*.47);
  float tail=1.0-smoother01(depth/max(tailWidth,1.0));
  tail=pow(max(tail,0.0),1.18);

  return mix(primary,tail,.12)*sat(uEdgeMode);
}

/*
 * 用圆肩表面法线和折射率求光线斜率，但最终位移始终锁定到玻璃内法线。
 * 位移上限小于圆肩宽度的一半，保证来源深度单调，避免折返和 X 型割裂。
 */
float capsuleRefractionTravel(
  vec2 edgeNormal,
  float depth,
  vec2 z,
  float r
){
  float shoulder=capsuleShoulderAt(depth,z,r);
  if(shoulder<=.0001){return 0.0;}

  float width=min(
      max(uBodyEdge.x,1.0),
      min(z.x,z.y)*.44
  );
  float strength=sat(max(uBodyEdge.w,0.0)/2.5);
  float maxAngle=mix(40.0,68.0,strength)*.01745329252;
  float theta=maxAngle*pow(shoulder,.82);

  vec3 surfaceNormal=normalize(vec3(
      edgeNormal*sin(theta),
      cos(theta)
  ));
  vec3 viewRay=vec3(0.0,0.0,-1.0);
  float ior=mix(1.10,1.58,strength);
  vec3 refractedRay=refract(
      viewRay,
      surfaceNormal,
      1.0/ior
  );

  float raySlope=length(refractedRay.xy)
      /max(-refractedRay.z,.30);
  float rawTravel=max(uBodyEdge.y,0.0)*raySlope;

  float safeMax=width*mix(.31,.42,strength);
  float travel=safeMax
      *(1.0-exp(-rawTravel/max(safeMax,1.0)));

  return travel;
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

  /* 胶囊圆肩是同一法向折射场的外沿增强，不再做切向平移。 */
  float capsuleTravel=capsuleRefractionTravel(
      n,depth,z,r
  );
  return -n*(displacement+capsuleTravel);
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

  /* 始终只有这一套主体坐标。 */
  vec2 mainBodyFlow=bodyRefractionFlow(
      p,normal,z,r,depth,bodyWeight
  );
  vec2 centerFlow=centerTransport(p,z);
  vec2 bodyOpticalCoord=p+mainBodyFlow+centerFlow;
  vec3 color=sampleBodyMaterial(
      globalUv(bodyOpticalCoord),bodyWeight
  );

  /* 同一材质中的轻微方向透射变化，只强调圆肩体积，不生成白色独立描边。 */
  float capsuleWeight=capsuleShoulderAt(depth,z,r);
  vec2 lightDirection=normalize(vec2(-.62,-.78));
  float lightFacing=pow(sat(dot(normal,lightDirection)),3.2);
  float shoulderMid=pow(
      sat(4.0*capsuleWeight*(1.0-capsuleWeight)),
      1.35
  );
  color*=1.0
      -.022*shoulderMid
      -.030*capsuleWeight
      +.095*capsuleWeight*lightFacing;

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

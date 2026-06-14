'use strict';
window.OpenGLV24Shaders={
  vs:'attribute vec2 a;void main(){gl_Position=vec4(a,0.,1.);}',
  fs:`precision highp float;
uniform vec2 uRes,uOrigin,uRoot;
uniform sampler2D uBlurTexture;
uniform vec4 uMat,uBodyLensA,uBodyLensB,uBody;
uniform vec4 uRimA,uRimB,uRimC;
uniform float uRadius,uIntensity,uRimMode;

float sat(float x){return clamp(x,0.0,1.0);}
float smoother01(float x){
  x=sat(x);
  return x*x*x*(x*(x*6.0-15.0)+10.0);
}
float luminanceOf(vec3 color){return dot(color,vec3(.299,.587,.114));}
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

/* V25.3 主体折射稳定基准。 */
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
vec2 bodyRefractionFlow(vec2 n,vec2 z,float r,float depth,float weight){
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
float evaluateBodyWeightAt(vec2 point,vec2 z,float r){
  float pointSd=boxSdf(point,z,r);
  return bodyLensWeight(max(-pointSd,0.0),z,r);
}
vec2 evaluateBodyOpticalCoordAt(vec2 point,vec2 z,float r,float pointWeight){
  float pointSd=boxSdf(point,z,r);
  float pointDepth=max(-pointSd,0.0);
  vec2 pointNormal=perimeterNormalAt(point,z,r);
  return point
      +bodyRefractionFlow(pointNormal,z,r,pointDepth,pointWeight)
      +centerTransport(point,z);
}

/*
 * V25.5 Wide Source-Band Liquid Shoulder
 * 边缘仍然只有一个 finalOpticalCoord：
 * 1. Snell 折射给出真实入射方向；
 * 2. 把一段更宽的主体内部来源带压缩进窄圆肩；
 * 3. 来源点继续进入完整 V25.3 主体折射场；
 * 4. 内沿 shoulder=0 时 sourcePoint=p，严格回到 bodyOpticalCoord；
 * 5. 三点切线采样仅扩散同一最终坐标的颜色足迹。
 */
float roundedShoulder(float t,float curve){
  float retreat=1.0-smoother01(sat(t));
  return pow(max(retreat,0.0),clamp(curve,.35,3.0));
}

vec3 renderUnifiedRoundedRim(
  vec2 p,
  vec2 z,
  float r,
  float sd,
  vec2 edgeNormal,
  vec2 bodyOpticalCoord,
  float bodyWeight
){
  float rimWidth=max(uRimA.x,1.0);
  float depth=max(-sd,0.0);
  float t=sat(depth/rimWidth);
  float shoulder=roundedShoulder(t,uRimA.w);

  float maxAngle=clamp(uRimA.z,0.0,78.0)*.01745329252;
  float theta=maxAngle*shoulder;
  vec3 surfaceNormal=normalize(vec3(
      edgeNormal*sin(theta),
      cos(theta)
  ));

  vec3 viewRay=vec3(0.0,0.0,-1.0);
  float ior=clamp(uRimB.x,1.01,1.85);
  vec3 refractedRay=refract(viewRay,surfaceNormal,1.0/ior);
  float rayZ=max(-refractedRay.z,.22);
  vec2 rawRayOffset=refractedRay.xy/rayZ*max(uRimA.y,0.0);

  /* 只保留向玻璃内部的 Snell 位移。 */
  float snellTravel=max(dot(rawRayOffset,-edgeNormal),0.0);

  /*
   * rimAnchor 把外沿先锚到圆肩内侧，lensGain 再展开光学厚度。
   * 默认参数下，约 50~70px 的内部来源带会被压入 14px 左右的圆肩，
   * 因而能够产生明确的压缩、放大和折返，而不是仅改变亮度。
   */
  float rimAnchor=max(rimWidth-depth,0.0)*pow(max(shoulder,0.0),.56);
  float lensGain=mix(1.20,2.05,pow(max(shoulder,0.0),.72));
  float inwardTravel=rimAnchor+snellTravel*lensGain;
  inwardTravel=min(inwardTravel,min(z.x,z.y)*.44);

  vec2 sourcePoint=p-edgeNormal*inwardTravel;
  float sourceWeight=evaluateBodyWeightAt(sourcePoint,z,r);
  vec2 finalOpticalCoord=evaluateBodyOpticalCoordAt(
      sourcePoint,z,r,sourceWeight
  );

  /*
   * 放大来源点与当前主体坐标之间的法线视差，形成可见的液态折叠。
   * shoulder 在内沿归零，因此这里不会破坏主体接缝。
   */
  float normalSeparation=dot(
      finalOpticalCoord-bodyOpticalCoord,
      -edgeNormal
  );
  finalOpticalCoord-=edgeNormal
      *normalSeparation
      *.34
      *pow(max(shoulder,0.0),.78);

  /* 复用 V25.3 的切向运输，让直边流线在圆角处连续转向并轻微拉长。 */
  vec2 tangent=vec2(-edgeNormal.y,edgeNormal.x);
  float tangentFlow=dot(finalOpticalCoord-sourcePoint,tangent);
  finalOpticalCoord+=tangent
      *tangentFlow
      *.52
      *pow(max(shoulder,0.0),.62);

  /* 距离与权重分开衰减，足迹覆盖圆肩但不产生三张清晰副本。 */
  float footprintStrength=clamp(uRimB.z,0.0,1.0);
  float spreadShape=pow(max(shoulder,0.0),.36);
  float blendShape=pow(max(shoulder,0.0),.72);
  float spread=max(uRimB.y,0.0)
      *spreadShape
      *mix(1.0,1.62,shoulder);

  vec3 centerColor=sampleBodyMaterial(
      globalUv(finalOpticalCoord),sourceWeight
  );
  vec3 plusColor=sampleBodyMaterial(
      globalUv(finalOpticalCoord+tangent*spread),sourceWeight
  );
  vec3 minusColor=sampleBodyMaterial(
      globalUv(finalOpticalCoord-tangent*spread),sourceWeight
  );

  float plusLuma=luminanceOf(plusColor);
  float minusLuma=luminanceOf(minusColor);
  float sideWeight=.20*footprintStrength*blendShape;
  float centerWeight=1.0-sideWeight*2.0;
  vec3 transmitted=
      centerColor*centerWeight
      +plusColor*sideWeight
      +minusColor*sideWeight;

  /* 深层亮色仅改变同一折射足迹的光能。 */
  float centerLuma=luminanceOf(centerColor);
  float peakLuma=max(centerLuma,max(plusLuma,minusLuma));
  float capture=sat((peakLuma-centerLuma)*max(uRimB.w,0.0));
  transmitted*=1.0+capture*.22*pow(max(shoulder,0.0),.66);
  transmitted*=mix(1.0,clamp(uRimC.x,.45,1.15),shoulder);

  /* Fresnel 使用同一来源带中的环境颜色，不建立独立白色高光层。 */
  float cosIncidence=sat(dot(-viewRay,surfaceNormal));
  float f0=(ior-1.0)/(ior+1.0);
  f0*=f0;
  float fresnel=f0+(1.0-f0)*pow(1.0-cosIncidence,5.0);

  vec2 local=(p-z*.5)/max(z*.5,vec2(1.0));
  vec2 lightAnchor=vec2(-1.18,-1.58);
  vec2 toLight=lightAnchor-local;
  float lightDistance2=max(dot(toLight,toLight),.001);
  vec2 lightDirection=toLight*inversesqrt(lightDistance2);
  float facing=sat(dot(edgeNormal,lightDirection));
  float directional=pow(facing,max(uRimC.w,1.0));
  float falloff=1.0/(1.0+lightDistance2*.40);
  float reflectionFacing=.05+.95*directional*falloff;

  float reflectionAmount=sat(
      fresnel
      *max(uRimC.y,0.0)
      *pow(max(shoulder,0.0),1.32)
      *reflectionFacing
  );
  vec3 footprintHighlight=plusLuma>minusLuma?plusColor:minusColor;
  vec3 reflectionColor=mix(transmitted,footprintHighlight,.56);
  reflectionColor=mix(
      reflectionColor,
      vec3(1.0,.995,.98),
      .05+.17*directional
  );
  vec3 color=mix(transmitted,reflectionColor,reflectionAmount);

  /* 内侧焦散保持很弱，避免生成平顶平台或独立白线。 */
  float innerLine=1.0-smoothstep(.055,.18,abs(t-.73));
  float causticAmount=innerLine*max(uRimC.z,0.0)*.12*(1.0-t);
  vec3 causticColor=mix(color,vec3(1.0,.985,.95),.08);
  color=mix(color,causticColor,sat(causticAmount));

  float peak=max(max(color.r,color.g),color.b);
  color/=1.0+max(peak-1.0,0.0)*.22;
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
  vec2 mainBodyFlow=bodyRefractionFlow(normal,z,r,depth,bodyWeight);
  vec2 centerFlow=centerTransport(p,z);
  vec2 bodyOpticalCoord=p+mainBodyFlow+centerFlow;
  vec3 color=sampleBodyMaterial(globalUv(bodyOpticalCoord),bodyWeight);

  float bodyDebug=smoothstep(-1.6,0.0,sd)*bodyMask;
  color=mix(color,vec3(1.0,.45,0.0),bodyDebug*uBodyLensB.w);

  if(uRimMode>.5&&depth<uRimA.x){
    color=renderUnifiedRoundedRim(
        p,z,r,sd,normal,bodyOpticalCoord,bodyWeight
    );
  }

  float alpha=bodyMask*sat(uMat.y*sat(uMat.x/20.0)*uIntensity);
  if(alpha<=.001)discard;
  gl_FragColor=vec4(clamp(color,0.0,1.0),sat(alpha));
}`
};

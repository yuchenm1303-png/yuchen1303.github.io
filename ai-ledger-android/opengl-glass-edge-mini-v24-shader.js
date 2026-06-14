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
float smootherRange(float a,float b,float x){
  return smoother01((x-a)/max(b-a,.0001));
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
 * Capsule Deep-Lens Rim
 * 用圆截面而不是平顶遮罩：外沿、中央冠部、内沿属于同一条胶囊曲线。
 * uRimA = rimWidthPx, sourceDepthPx, refractionStrength, capsuleRoundness
 * uRimB = brightCapture, transportStrength, transmission, innerJoinSoftness
 * uRimC = outerReflection, innerCaustic, highlightPower, reserved
 */
void capsuleCrossSection(
  float t,
  float roundness,
  float innerSoftness,
  out float height,
  out float slopeLobe
){
  float x=sat(t)*2.0-1.0;
  float circle=max(1.0-x*x,0.0);
  float base=sqrt(circle);
  float rounded=pow(base,clamp(roundness,.35,2.4));
  float outerGate=smootherRange(0.0,.035,t);
  float innerEdge=mix(.965,.84,sat(innerSoftness));
  float innerGate=1.0-smootherRange(innerEdge,1.0,t);
  height=rounded*outerGate*innerGate;

  /* 两侧斜面方向相反，两端和冠部都回到 0。 */
  slopeLobe=-x*pow(circle,.36)*outerGate*innerGate;
}

vec3 sampleCapsuleInteriorLens(
  vec2 p,
  vec2 z,
  float r,
  vec2 normal,
  float t,
  float capsuleHeight,
  float capsuleSlope,
  vec2 bodyOpticalCoord,
  float bodyWeight
){
  float rimWidth=max(uRimA.x,1.0);
  float maxSafeDepth=max(rimWidth*1.1,min(z.x,z.y)*.42);
  float centerDepth=clamp(uRimA.y,rimWidth*1.1,maxSafeDepth);
  float strength=clamp(uRimA.z,0.0,1.35);

  /* 中央冠部深入玻璃，两侧斜面产生方向相反的少量折射偏置。 */
  float depthFromThickness=centerDepth*capsuleHeight;
  float slopeOffset=rimWidth*.82*strength*capsuleSlope;
  float sourceDepth=clamp(
      depthFromThickness+slopeOffset,
      0.0,
      maxSafeDepth
  );

  vec2 sourcePoint=p-normal*sourceDepth;
  float sourceWeight=evaluateBodyWeightAt(sourcePoint,z,r);
  vec2 sourceOpticalCoord=evaluateBodyOpticalCoordAt(sourcePoint,z,r,sourceWeight);

  float coordinateAmount=sat(capsuleHeight*strength*sat(uRimB.y));
  vec2 lensOpticalCoord=mix(bodyOpticalCoord,sourceOpticalCoord,coordinateAmount);
  float lensWeight=mix(bodyWeight,sourceWeight,coordinateAmount);
  vec3 refractedColor=sampleBodyMaterial(globalUv(lensOpticalCoord),lensWeight);

  /* 中层锚点只提供亮色浓缩，不再引入第二套几何形状。 */
  float anchorDepth=centerDepth*capsuleHeight;
  vec2 anchorPoint=p-normal*anchorDepth;
  float anchorWeight=evaluateBodyWeightAt(anchorPoint,z,r);
  vec2 anchorOpticalCoord=evaluateBodyOpticalCoordAt(anchorPoint,z,r,anchorWeight);
  vec3 anchorColor=sampleBodyMaterial(globalUv(anchorOpticalCoord),anchorWeight);

  float capture=clamp(uRimB.x,0.0,4.0);
  float refractedLuma=luminanceOf(refractedColor);
  float anchorLuma=luminanceOf(anchorColor);
  float captureAmount=
      sat(max(anchorLuma-refractedLuma,0.0)*capture*.58)
      *coordinateAmount;
  refractedColor*=1.0+captureAmount*.30;
  return refractedColor;
}

vec3 projectCapsuleInteriorLens(
  vec2 p,
  vec2 z,
  float r,
  float sd,
  vec2 normal,
  vec2 bodyOpticalCoord,
  float bodyWeight,
  vec3 bodyColor
){
  float rimWidth=max(uRimA.x,1.0);
  float depth=max(-sd,0.0);
  float t=sat(depth/rimWidth);

  float capsuleHeight=0.0;
  float capsuleSlope=0.0;
  capsuleCrossSection(
      t,uRimA.w,uRimB.w,capsuleHeight,capsuleSlope
  );

  vec3 lensColor=sampleCapsuleInteriorLens(
      p,z,r,normal,t,capsuleHeight,capsuleSlope,
      bodyOpticalCoord,bodyWeight
  );

  float transmission=mix(
      1.0,
      clamp(uRimB.z,.45,1.15),
      capsuleHeight
  );
  vec3 transmitted=lensColor*transmission;
  float materialCoverage=sat(capsuleHeight*sat(uRimB.y));
  vec3 color=mix(bodyColor,transmitted,materialCoverage);

  vec2 local=(p-z*.5)/max(z*.5,vec2(1.0));
  vec2 lightAnchor=vec2(-1.18,-1.58);
  vec2 toLight=lightAnchor-local;
  float lightDistance2=max(dot(toLight,toLight),.001);
  vec2 lightDirection=toLight*inversesqrt(lightDistance2);
  float facing=sat(dot(normal,lightDirection));
  float directional=pow(facing,max(uRimC.z,1.0));
  float falloff=1.0/(1.0+lightDistance2*.40);

  float outerLineWidth=max(1.0,min(1.7,rimWidth*.14));
  float outerLine=1.0-smoothstep(0.0,outerLineWidth,depth);
  float outerAmount=outerLine*(.024+directional*falloff*max(uRimC.x,0.0)*.40);
  vec3 reflectedColor=mix(color,lensColor,.74);
  reflectedColor=mix(reflectedColor,vec3(1.0,.995,.98),.13*directional);
  color=mix(color,reflectedColor,sat(outerAmount));

  /* 内沿焦散放在胶囊内侧斜面，而不是平台末端。 */
  float innerShoulder=pow(sat(-capsuleSlope),1.35);
  float causticAmount=innerShoulder*max(uRimC.y,0.0)*.18;
  vec3 causticColor=mix(lensColor,vec3(1.0,.985,.95),.10);
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
  vec2 opticalCoord=p+mainBodyFlow+centerFlow;
  vec3 color=sampleBodyMaterial(globalUv(opticalCoord),bodyWeight);

  float bodyDebug=smoothstep(-1.6,0.0,sd)*bodyMask;
  color=mix(color,vec3(1.0,.45,0.0),bodyDebug*uBodyLensB.w);

  if(uRimMode>.5&&depth<uRimA.x){
    color=projectCapsuleInteriorLens(
        p,z,r,sd,normal,opticalCoord,bodyWeight,color
    );
  }

  float alpha=bodyMask*sat(uMat.y*sat(uMat.x/20.0)*uIntensity);
  if(alpha<=.001)discard;
  gl_FragColor=vec4(clamp(color,0.0,1.0),sat(alpha));
}`
};

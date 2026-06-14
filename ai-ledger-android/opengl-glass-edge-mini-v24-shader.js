'use strict';
window.OpenGLV24Shaders={
  vs:'attribute vec2 a;void main(){gl_Position=vec4(a,0.,1.);}',
  fs:`precision highp float;
uniform vec2 uRes,uOrigin,uRoot;
uniform sampler2D uBlurTexture;
uniform vec4 uMat,uBodyLensA,uBodyLensB,uBody;
uniform vec4 uRimA,uRimB;
uniform float uRadius,uIntensity,uRimMode,uRimJoinWidth;

float sat(float x){return clamp(x,0.0,1.0);}
float smoother01(float x){
  x=sat(x);
  return x*x*x*(x*(x*6.0-15.0)+10.0);
}
float smootherRange(float a,float b,float x){
  return smoother01((x-a)/max(b-a,.0001));
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

/*
 * 边框折射不再二次采样。
 * 它直接生成 rimFlow，与主体流场一起组成唯一 opticalCoord。
 * 两端使用五次平滑接入，保证边界和主体连接处的位移与变化率都回到 0。
 */
vec2 continuousRimFlow(float sd,vec2 normal){
  float rimWidth=max(uRimA.x,1.0);
  float depth=max(-sd,0.0);
  float t=sat(depth/rimWidth);
  float joinWidth=clamp(uRimJoinWidth,.02,.45);
  float outerJoin=smootherRange(0.0,joinWidth,t);
  float innerJoin=1.0-smootherRange(1.0-joinWidth,1.0,t);
  float joinWindow=outerJoin*innerJoin*step(sd,0.0);

  float curvature=max(uRimA.y,.01);
  float rawSlope=cos(3.14159265*t);
  float slopeInput=rawSlope*curvature*2.4;
  float slope=slopeInput/(1.0+abs(slopeInput));
  float flowStrength=sat(uRimA.w)*sat(uRimB.w);
  float refractPx=max(uRimA.z,0.0)*slope*joinWindow*flowStrength;
  return -normal*refractPx;
}

/* 高光和吸收只处理统一采样后的颜色，不再改变折射坐标。 */
vec3 applyContinuousRimMaterial(
  vec2 p,
  vec2 z,
  float sd,
  vec2 normal,
  vec3 color
){
  float rimWidth=max(uRimA.x,1.0);
  float depth=max(-sd,0.0);
  float t=sat(depth/rimWidth);
  float materialStrength=sat(uRimB.w);
  float profile=sin(3.14159265*t);

  float absorptionShape=profile*(.42+.58*t);
  color*=exp(-max(uRimB.x,0.0)*absorptionShape*.42*materialStrength);

  vec2 local=(p-z*.5)/max(z*.5,vec2(1.0));
  vec2 lightAnchor=vec2(-1.15,-1.65);
  vec2 toLight=lightAnchor-local;
  float lightDistance2=max(dot(toLight,toLight),.001);
  vec2 lightDirection=toLight*inversesqrt(lightDistance2);
  float facing=sat(dot(normal,lightDirection));
  float directional=pow(facing,max(uRimB.z,1.0));
  float falloff=1.0/(1.0+lightDistance2*.42);
  float outerSurface=pow(1.0-t,2.8);
  float rimLuma=dot(color,vec3(.299,.587,.114));
  float baseReflection=pow(1.0-t,1.6)*(.120+.080*sat(rimLuma));
  float directionalReflection=directional*falloff*outerSurface*max(uRimB.y,0.0);
  float surfaceReflection=sat((baseReflection+directionalReflection)*materialStrength);
  vec3 highlightColor=mix(color,vec3(1.0,.995,.980),.68);
  color=mix(color,highlightColor,surfaceReflection);

  float peak=max(max(color.r,color.g),color.b);
  color/=1.0+max(peak-1.0,0.0)*.28;
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
  vec2 rimFlow=vec2(0.0);
  if(uRimMode>.5&&depth<uRimA.x){
    rimFlow=continuousRimFlow(sd,normal);
  }

  vec2 totalFlow=mainBodyFlow+centerFlow+rimFlow;
  vec2 opticalCoord=p+totalFlow;
  vec3 color=sampleBodyMaterial(globalUv(opticalCoord),bodyWeight);

  float bodyDebug=smoothstep(-1.6,0.0,sd)*bodyMask;
  color=mix(color,vec3(1.0,.45,0.0),bodyDebug*uBodyLensB.w);

  if(uRimMode>.5&&depth<uRimA.x){
    color=applyContinuousRimMaterial(p,z,sd,normal,color);
  }

  float alpha=bodyMask*sat(uMat.y*sat(uMat.x/20.0)*uIntensity);
  if(alpha<=.001)discard;
  gl_FragColor=vec4(clamp(color,0.0,1.0),sat(alpha));
}`
};

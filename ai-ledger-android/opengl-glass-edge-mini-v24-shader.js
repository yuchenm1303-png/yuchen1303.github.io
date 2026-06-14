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
 * 圆润玻璃倒角折射带。
 * 高度截面 h(t)=sin(pi*t)^power；power>1 保证两端高度和坡度同时归零。
 * 截面坡度自然形成外侧斜面、中央冠部和内侧斜面，不再使用边界突变的 cos(pi*t)。
 *
 * uRimA = widthPx, profilePower, refractiveIndex, opticalThickness
 * uRimB = innerBevelRatio, crownPullRatio, absorption, overallStrength
 * uRimC = outerHighlight, innerCaustic, highlightPower, crownClarity
 */
void rimProfile(float t,out float profile,out float signedSlope){
  float angle=3.14159265*sat(t);
  float s=max(sin(angle),0.0);
  float c=cos(angle);
  float power=max(uRimA.y,1.15);
  profile=pow(s,power);
  float edgeGate=smoother01(sat(s/.085));
  signedSlope=power*3.14159265*pow(max(s,.0001),power-1.0)*c*edgeGate;
}
vec2 roundedBevelRimFlow(float sd,vec2 normal){
  float rimWidth=max(uRimA.x,1.0);
  float depth=max(-sd,0.0);
  float t=sat(depth/rimWidth);
  float profile=0.0;
  float signedSlope=0.0;
  rimProfile(t,profile,signedSlope);

  float innerRatio=sat(uRimB.x);
  signedSlope*=mix(innerRatio,1.0,step(0.0,signedSlope));
  float slopeScale=signedSlope*.34;
  float boundedSlope=slopeScale/(1.0+abs(slopeScale));

  float ior=clamp(uRimA.z,1.01,1.85);
  float snellGain=(1.0-1.0/ior)*2.15;
  float bevelShift=rimWidth*max(uRimA.w,0.0)*snellGain*boundedSlope;
  float crownShift=rimWidth*max(uRimB.y,0.0)*profile;
  float rawShift=(bevelShift+crownShift)*sat(uRimB.w);

  float shiftLimit=rimWidth*.42;
  float shiftPx=rawShift/(1.0+abs(rawShift)/max(shiftLimit,.001));
  return -normal*shiftPx*step(sd,0.0);
}

/* 折射后的材质：冠部颜色凝聚、厚度吸收、窄外沿反射、窄内沿焦散。 */
vec3 applyRoundedBevelMaterial(
  vec2 p,
  vec2 z,
  float sd,
  vec2 normal,
  vec3 color
){
  float rimWidth=max(uRimA.x,1.0);
  float depth=max(-sd,0.0);
  float t=sat(depth/rimWidth);
  float profile=0.0;
  float signedSlope=0.0;
  rimProfile(t,profile,signedSlope);
  float strength=sat(uRimB.w);

  float luma=dot(color,vec3(.299,.587,.114));
  float clarity=profile*sat(uRimC.w)*strength;
  vec3 concentrated=vec3(luma)+(color-vec3(luma))*(1.0+clarity*.58);
  concentrated=(concentrated-.5)*(1.0+clarity*.16)+.5;
  color=mix(color,concentrated,clarity*.55);

  float absorption=profile*max(uRimB.z,0.0)*.34*strength;
  color*=exp(-absorption);

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
  float outerReflection=outerLine*(.045+directional*falloff*max(uRimC.x,0.0)*.52)*strength;
  vec3 outerColor=mix(color,vec3(1.0,.995,.978),.72);
  color=mix(color,outerColor,sat(outerReflection));

  float innerCenter=rimWidth*.82;
  float innerLineWidth=max(.70,rimWidth*.065);
  float innerLine=1.0-smoothstep(innerLineWidth,innerLineWidth*2.15,abs(depth-innerCenter));
  float innerResponse=(.035+.145*max(uRimC.y,0.0))*innerLine*strength;
  vec3 causticColor=mix(color,vec3(1.0,.985,.94),.38);
  color=mix(color,causticColor,sat(innerResponse));

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
    rimFlow=roundedBevelRimFlow(sd,normal);
  }

  vec2 totalFlow=mainBodyFlow+centerFlow+rimFlow;
  vec2 opticalCoord=p+totalFlow;
  vec3 color=sampleBodyMaterial(globalUv(opticalCoord),bodyWeight);

  float bodyDebug=smoothstep(-1.6,0.0,sd)*bodyMask;
  color=mix(color,vec3(1.0,.45,0.0),bodyDebug*uBodyLensB.w);

  if(uRimMode>.5&&depth<uRimA.x){
    color=applyRoundedBevelMaterial(p,z,sd,normal,color);
  }

  float alpha=bodyMask*sat(uMat.y*sat(uMat.x/20.0)*uIntensity);
  if(alpha<=.001)discard;
  gl_FragColor=vec4(clamp(color,0.0,1.0),sat(alpha));
}`
};

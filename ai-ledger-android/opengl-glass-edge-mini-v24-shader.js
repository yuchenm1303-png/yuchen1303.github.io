'use strict';
window.OpenGLV24Shaders={
  vs:'attribute vec2 a;void main(){gl_Position=vec4(a,0.,1.);}',
  fs:`precision highp float;
uniform vec2 uRes,uOrigin,uRoot;
uniform sampler2D uBlurTexture;
uniform vec4 uMat,uBodyLensA,uBodyLensB,uBody;
uniform vec4 uShoulder;
uniform vec2 uShoulderFlow;
uniform float uShoulderEnabled,uRadius,uIntensity;

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
 * V29.3 Fixed-Capture Compression Shoulder
 * uShoulder = visibleWidthPx, maxAngleDeg, falloffRoundness, materialStrength
 * uShoulderFlow = fixedCaptureWidthPx, tangentialFlowStrength
 *
 * 可见圆肩宽度只决定显示压缩宽度。
 * 背景来源始终使用固定最大宽度的深层取样域，
 * 圆肩缩窄时把同一深层图像压缩进边缘，而不是退回边缘重新取样。
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

vec2 bodyRefractionFlow(
  vec2 p,
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

vec2 evaluateBodyOpticalCoordAt(vec2 point,vec2 z,float r){
  float pointSd=boxSdf(point,z,r);
  float pointDepth=max(-pointSd,0.0);
  vec2 pointNormal=perimeterNormalAt(point,z,r);
  float pointWeight=bodyLensWeight(pointDepth,z,r);
  return point
      +bodyRefractionFlow(
          point,pointNormal,z,r,pointDepth,pointWeight
      )
      +centerTransport(point,z);
}

float shoulderWidth(vec2 z){
  return min(
      max(uShoulder.x,1.0),
      min(z.x,z.y)*.46
  );
}

float shoulderCaptureWidth(vec2 z){
  float visible=shoulderWidth(z);
  float requested=max(uShoulderFlow.x,visible);
  return min(requested,min(z.x,z.y)*.46);
}

float shoulderProfile(float depth,vec2 z){
  float width=shoulderWidth(z);
  float x=sat(depth/max(width,1.0));
  float cubic=x*x*(3.0-2.0*x);
  float quintic=smoother01(x);
  float roundness=sat(uShoulder.z);
  return 1.0-mix(cubic,quintic,roundness);
}

float shoulderMaterialFill(float depth,vec2 z){
  float x=sat(depth/max(shoulderWidth(z),1.0));
  float tail=sat((x-.72)/.28);
  return 1.0-smoother01(tail);
}

float shoulderOpticalBlend(float depth,vec2 z){
  float x=sat(depth/max(shoulderWidth(z),1.0));
  float tail=sat((x-.55)/.45);
  return 1.0-smoother01(tail);
}

float shoulderMaxAngle(){
  return clamp(uShoulder.y,0.0,89.5)*.01745329252;
}

float shoulderTheta(float depth,vec2 z){
  return shoulderMaxAngle()*shoulderProfile(depth,z);
}

float smoothstepIntegral(float x){
  x=sat(x);
  return x*x*x-.5*x*x*x*x;
}

/*
 * 固定最大宽度下的深层来源曲线。
 * x 始终来自可见圆肩的归一化深度，
 * 但最终来源深度始终乘固定 captureWidth。
 */
float shoulderCaptureTravelRatio(float x,float outerRatio){
  float ramp=.15;
  float slope=outerRatio/(1.0-ramp);

  if(x<ramp){
    float t=x/ramp;
    return outerRatio
        -slope*ramp*smoothstepIntegral(t);
  }

  if(x>1.0-ramp){
    float t=(1.0-x)/ramp;
    return slope*ramp*smoothstepIntegral(t);
  }

  return slope*((1.0-ramp)-x)
      +slope*ramp*.5;
}

float shoulderCaptureDepth(float depth,vec2 z){
  float visibleWidth=shoulderWidth(z);
  float captureWidth=shoulderCaptureWidth(z);
  float x=sat(depth/max(visibleWidth,1.0));
  float angleResponse=pow(max(sin(shoulderMaxAngle()),0.0),.90);
  float outerRatio=.78*angleResponse;
  float extraRatio=shoulderCaptureTravelRatio(x,outerRatio);
  return captureWidth*(x+extraRatio);
}

float shoulderTangentialSignal(
  vec2 p,
  vec2 edgeNormal,
  vec2 z
){
  vec2 u=(p-z*.5)/max(z*.5,vec2(1.0));
  vec2 tangent=vec2(-edgeNormal.y,edgeNormal.x);

  vec2 contourVector=vec2(-u.y,u.x);
  float contourLength=length(contourVector);
  float contourSignal=0.0;
  if(contourLength>.0001){
    contourSignal=dot(
        contourVector/contourLength,
        tangent
    );
  }

  float bodySignal=dot(
      polynomialTransport(u),
      tangent
  );
  float mixed=.48*contourSignal+.52*bodySignal;
  return mixed/(.65+abs(mixed));
}

float shoulderTangentialTravel(
  vec2 p,
  vec2 edgeNormal,
  vec2 z,
  float depth
){
  float captureWidth=shoulderCaptureWidth(z);
  float flowStrength=clamp(uShoulderFlow.y,0.0,2.4);
  float amplitude=captureWidth*.30*sat(flowStrength/2.4);
  float envelope=
      shoulderMaterialFill(depth,z)
      *shoulderOpticalBlend(depth,z);
  return amplitude
      *shoulderTangentialSignal(p,edgeNormal,z)
      *envelope;
}

vec4 evaluateShoulderSource(
  vec2 p,
  vec2 edgeNormal,
  vec2 z,
  float r,
  float depth
){
  float visibleWidth=shoulderWidth(z);
  if(uShoulderEnabled<.5||depth>=visibleWidth){
    return vec4(p,0.0,0.0);
  }

  float profile=shoulderProfile(depth,z);
  float theta=shoulderTheta(depth,z);
  float sourceDepth=shoulderCaptureDepth(depth,z);
  float tangentTravel=shoulderTangentialTravel(
      p,edgeNormal,z,depth
  );
  vec2 tangent=vec2(-edgeNormal.y,edgeNormal.x);

  /*
   * 先回到外轮廓，再按固定最大取样深度进入玻璃。
   * 因而 visibleWidth 缩小时只是压缩显示，不改变来源区域。
   */
  vec2 boundaryPoint=p+edgeNormal*depth;
  vec2 sourcePoint=
      boundaryPoint
      -edgeNormal*sourceDepth
      +tangent*tangentTravel;

  float sourceSd=boxSdf(sourcePoint,z,r);
  if(sourceSd>-.5){
    vec2 sourceNormal=perimeterNormalAt(
        sourcePoint,z,r
    );
    sourcePoint-=sourceNormal*(sourceSd+.5);
  }

  float f0=.04;
  float cosIncidence=cos(theta);
  float fresnel=f0+(1.0-f0)
      *pow(1.0-sat(cosIncidence),5.0);

  return vec4(sourcePoint,profile,fresnel);
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
  vec2 normal=perimeterNormalAt(p,z,r);
  float bodyWeight=bodyLensWeight(depth,z,r);

  vec2 pureBodyCoord=p
      +bodyRefractionFlow(
          p,normal,z,r,depth,bodyWeight
      )
      +centerTransport(p,z);

  vec2 bodyOpticalCoord=pureBodyCoord;
  float materialWeight=bodyWeight;
  float shoulder=0.0;
  float shoulderFresnel=0.0;
  float shoulderBlend=0.0;

  float width=shoulderWidth(z);
  if(uShoulderEnabled>.5&&depth<width){
    vec4 shoulderData=evaluateShoulderSource(
        p,normal,z,r,depth
    );
    vec2 sourcePoint=shoulderData.xy;
    shoulder=shoulderData.z;
    shoulderFresnel=shoulderData.w;
    shoulderBlend=shoulderOpticalBlend(depth,z);

    float sourceDepth=max(-boxSdf(sourcePoint,z,r),0.0);
    float sourceWeight=bodyLensWeight(sourceDepth,z,r);
    vec2 shoulderCoord=evaluateBodyOpticalCoordAt(
        sourcePoint,z,r
    );

    bodyOpticalCoord=mix(
        pureBodyCoord,
        shoulderCoord,
        shoulderBlend
    );
    materialWeight=mix(
        bodyWeight,
        sourceWeight,
        shoulderBlend
    );
  }

  vec3 color=sampleBodyMaterial(
      globalUv(bodyOpticalCoord),materialWeight
  );

  float strength=clamp(uShoulder.w,0.0,4.0);
  float fill=shoulderMaterialFill(depth,z);
  float outerRim=pow(shoulder,3.6);

  vec2 lightDirection=normalize(vec2(-.62,-.78));
  float lightFacing=pow(sat(dot(normal,lightDirection)),2.7);

  float volumeShadow=.014*strength*fill
      *(.30+.70*(1.0-lightFacing));
  color*=1.0-volumeShadow;

  float fillSheen=sat(
      .072*strength*fill
      *(.40+.60*lightFacing)
  );
  vec3 filledColor=mix(
      color,
      vec3(.88,.96,1.0),
      .36
  );
  color=mix(color,filledColor,fillSheen);

  float reflection=sat(
      .21*strength*shoulderFresnel*outerRim
      *(.18+.82*lightFacing)
  );
  vec3 reflectionColor=mix(
      color,
      vec3(.93,.98,1.0),
      .72
  );
  color=mix(color,reflectionColor,reflection);

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

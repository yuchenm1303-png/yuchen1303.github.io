'use strict';
window.OpenGLV24Shaders={
  vs:'attribute vec2 a;void main(){gl_Position=vec4(a,0.,1.);}',
  fs:`precision highp float;
uniform vec2 uRes,uOrigin,uRoot;
uniform sampler2D uBlurTexture;
uniform vec4 uMat,uBodyLensA,uBodyLensB,uBody;
uniform vec4 uShoulder;
uniform vec2 uShoulderFlow;
uniform float uShoulderCorrection;
uniform float uShoulderEnabled,uRadius,uIntensity;

float sat(float x){return clamp(x,0.0,1.0);}
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
 * V29.8 Affine Unified Mapping with Exact Tangential Correction
 * uShoulderEnabled:
 *   0 = pure V25.3 body
 *   1 = original V29.4 local-normal capture
 *   2 = V29.5 affine unified full-perimeter capture
 *   3 = V29.6 normal-locked blended mapping
 *   4 = V29.7 curvature-safe perimeter normal mapping
 *   5 = V29.8 affine unified mapping + exact tangential correction
 *
 * 模式 5 完整保留模式 2 的统一内轮廓和无重影结构，只精确测量最终来源点
 * 相对当前像素产生的切向漂移，再按 uShoulderCorrection 比例抵消。
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

float shoulderX(float depth,vec2 z){
  return sat(depth/max(shoulderWidth(z),1.0));
}

float shoulderOuterEnvelope(float depth,vec2 z){
  float x=shoulderX(depth,z);
  float exponent=mix(2.0,4.8,sat(uShoulder.z));
  return pow(max(1.0-x,0.0),exponent);
}

float shoulderMaterialFill(float depth,vec2 z){
  float x=shoulderX(depth,z);
  float exponent=mix(1.20,1.85,sat(uShoulder.z));
  return pow(max(1.0-x,0.0),exponent);
}

float shoulderMaxAngle(){
  return clamp(uShoulder.y,0.0,89.5)*.01745329252;
}

float shoulderTheta(float depth,vec2 z){
  return shoulderMaxAngle()*shoulderOuterEnvelope(depth,z);
}

float shoulderCaptureDepth(float depth,vec2 z){
  float captureWidth=shoulderCaptureWidth(z);
  float envelope=shoulderOuterEnvelope(depth,z);
  return depth+(captureWidth-depth)*envelope;
}

vec2 unifiedInnerContourPoint(vec2 boundaryPoint,vec2 z){
  vec2 center=z*.5;
  vec2 halfSize=max(z*.5,vec2(1.0));
  float captureWidth=shoulderCaptureWidth(z);
  vec2 innerHalf=max(
      halfSize-vec2(captureWidth),
      vec2(1.0)
  );
  vec2 normalized=(boundaryPoint-center)/halfSize;
  return center+normalized*innerHalf;
}

float unifiedCornerBlend(
  vec2 boundaryPoint,
  vec2 z,
  float r
){
  vec2 local=abs(boundaryPoint-z*.5);
  vec2 core=max(z*.5-vec2(r),vec2(0.0));
  vec2 cornerOffset=max(local-core,vec2(0.0));
  float cornerDepth=min(cornerOffset.x,cornerOffset.y);
  float feather=max(r*.22,1.0);
  return smoothstep(0.0,feather,cornerDepth);
}

vec2 normalLockedUnifiedInnerPoint(
  vec2 boundaryPoint,
  vec2 edgeNormal,
  vec2 z,
  float r
){
  vec2 unifiedPoint=unifiedInnerContourPoint(boundaryPoint,z);
  vec2 delta=unifiedPoint-boundaryPoint;
  vec2 tangent=vec2(-edgeNormal.y,edgeNormal.x);
  float normalShift=dot(delta,edgeNormal);
  float tangentShift=dot(delta,tangent);
  float cornerBlend=unifiedCornerBlend(boundaryPoint,z,r);
  return boundaryPoint
      +edgeNormal*normalShift
      +tangent*tangentShift*cornerBlend;
}

float curvatureSafeCaptureWidth(
  vec2 boundaryPoint,
  vec2 edgeNormal,
  vec2 z,
  float r
){
  float fullCapture=shoulderCaptureWidth(z);
  float curvatureLimit=max(r-1.0,1.0);
  float cornerCapture=min(
      fullCapture,
      min(max(r*.78,1.0),curvatureLimit)
  );

  vec2 local=abs(boundaryPoint-z*.5);
  vec2 core=max(z*.5-vec2(r),vec2(0.0));
  vec2 cornerOffset=max(local-core,vec2(0.0));
  float onArc=step(.001,cornerOffset.x)
      *step(.001,cornerOffset.y);

  float horizontalSide=step(
      abs(edgeNormal.x),
      abs(edgeNormal.y)
  );
  float distanceToCorner=mix(
      max(core.y-local.y,0.0),
      max(core.x-local.x,0.0),
      horizontalSide
  );
  float feather=max(
      r*1.55,
      fullCapture-cornerCapture
  );
  float straightBlend=smoothstep(
      0.0,
      max(feather,1.0),
      distanceToCorner
  );
  float straightCapture=mix(
      cornerCapture,
      fullCapture,
      straightBlend
  );
  return mix(straightCapture,cornerCapture,onArc);
}

vec2 curvatureSafePerimeterInnerPoint(
  vec2 boundaryPoint,
  vec2 edgeNormal,
  vec2 z,
  float r
){
  float capture=curvatureSafeCaptureWidth(
      boundaryPoint,edgeNormal,z,r
  );
  return boundaryPoint-edgeNormal*capture;
}

vec2 tangentCorrectedUnifiedSource(
  vec2 p,
  vec2 boundaryPoint,
  vec2 edgeNormal,
  vec2 z,
  float envelope
){
  vec2 innerContourPoint=unifiedInnerContourPoint(
      boundaryPoint,z
  );
  vec2 source=mix(p,innerContourPoint,envelope);
  vec2 tangent=vec2(-edgeNormal.y,edgeNormal.x);
  float tangentDrift=dot(source-p,tangent);
  float axisAlignment=max(
      abs(edgeNormal.x),
      abs(edgeNormal.y)
  );
  float straightWeight=mix(
      .35,
      1.0,
      smoothstep(.72,1.0,axisAlignment)
  );
  float correction=sat(uShoulderCorrection)*straightWeight;
  return source-tangent*tangentDrift*correction;
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
  float envelope=pow(
      shoulderOuterEnvelope(depth,z),
      .82
  );
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

  float envelope=shoulderOuterEnvelope(depth,z);
  float theta=shoulderTheta(depth,z);
  float tangentTravel=shoulderTangentialTravel(
      p,edgeNormal,z,depth
  );
  vec2 tangent=vec2(-edgeNormal.y,edgeNormal.x);
  vec2 boundaryPoint=p+edgeNormal*depth;
  vec2 sourcePoint;

  if(uShoulderEnabled<1.5){
    float sourceDepth=shoulderCaptureDepth(depth,z);
    sourcePoint=
        boundaryPoint
        -edgeNormal*sourceDepth
        +tangent*tangentTravel;
  }else if(uShoulderEnabled<2.5){
    vec2 innerContourPoint=unifiedInnerContourPoint(
        boundaryPoint,z
    );
    sourcePoint=
        mix(p,innerContourPoint,envelope)
        +tangent*tangentTravel;
  }else if(uShoulderEnabled<3.5){
    vec2 innerContourPoint=normalLockedUnifiedInnerPoint(
        boundaryPoint,edgeNormal,z,r
    );
    sourcePoint=
        mix(p,innerContourPoint,envelope)
        +tangent*tangentTravel;
  }else if(uShoulderEnabled<4.5){
    vec2 innerContourPoint=curvatureSafePerimeterInnerPoint(
        boundaryPoint,edgeNormal,z,r
    );
    sourcePoint=
        mix(p,innerContourPoint,envelope)
        +tangent*tangentTravel;
  }else{
    sourcePoint=tangentCorrectedUnifiedSource(
        p,boundaryPoint,edgeNormal,z,envelope
    )+tangent*tangentTravel;
  }

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

  return vec4(sourcePoint,envelope,fresnel);
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

  float width=shoulderWidth(z);
  if(uShoulderEnabled>.5&&depth<width){
    vec4 shoulderData=evaluateShoulderSource(
        p,normal,z,r,depth
    );
    vec2 sourcePoint=shoulderData.xy;
    shoulder=shoulderData.z;
    shoulderFresnel=shoulderData.w;

    float sourceDepth=max(-boxSdf(sourcePoint,z,r),0.0);
    materialWeight=bodyLensWeight(sourceDepth,z,r);

    bodyOpticalCoord=evaluateBodyOpticalCoordAt(
        sourcePoint,z,r
    );
  }

  vec3 color=sampleBodyMaterial(
      globalUv(bodyOpticalCoord),materialWeight
  );

  float strength=clamp(uShoulder.w,0.0,4.0);
  float fill=shoulderMaterialFill(depth,z);
  float outerRim=pow(shoulder,2.8);

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

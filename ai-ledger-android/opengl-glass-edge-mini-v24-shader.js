'use strict';
window.OpenGLV24Shaders={
  vs:'attribute vec2 a;void main(){gl_Position=vec4(a,0.,1.);}',
  fs:`precision highp float;
uniform vec2 uRes,uOrigin,uRoot;
uniform sampler2D uTex;
uniform vec4 uMat,uOldA,uOldB,uBody;
uniform float uRadius,uIntensity;
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
vec3 sampleBg(vec2 uv){return texture2D(uTex,clamp(uv,0.0,1.0)).rgb;}
vec2 softLimit(vec2 v,float lim){
  float n=length(v);
  float m=n/(1.0+n/max(lim,1.0));
  return v*(m/max(n,.0001));
}
vec2 sdfNormalAt(vec2 p,vec2 z,float r){
  float d=1.25;
  vec2 n=vec2(
    boxSdf(p+vec2(d,0.0),z,r)-boxSdf(p-vec2(d,0.0),z,r),
    boxSdf(p+vec2(0.0,d),z,r)-boxSdf(p-vec2(0.0,d),z,r)
  );
  return n/max(length(n),.0001);
}

/* 单一解析边缘透镜：边界最强，向内部单调衰减，不再形成内外双框。 */
float edgeLensWeight(float depth,vec2 z){
  float reach=clamp(uOldB.y,8.0,min(z.x,z.y)*.46);
  float x=sat(depth/max(reach,1.0));
  float smooth=x*x*(3.0-2.0*x);
  float concentration=mix(.58,1.82,sat((uOldA.z+10.0)/20.0));
  return pow(1.0-smooth,concentration);
}
float cornerFactor(vec2 p,vec2 z){
  vec2 q=abs((p-z*.5)/max(z*.5,vec2(1.0)));
  return smoothstep(.52,.94,min(q.x,q.y));
}
vec2 analyticEdgeFlow(vec2 p,vec2 z,float r,float depth,float weight){
  vec2 n=sdfNormalAt(p,z,r);
  float pull=abs(uOldA.y)*.052+abs(uOldA.x)*.20+max(uOldB.x,0.0)*.12;
  float cornerBoost=1.0+cornerFactor(p,z)*sat(uOldA.w/200.0)*.52;
  float core=pow(weight,1.28);
  vec2 flow=-n*pull*core*cornerBoost;
  return softLimit(flow,96.0+max(uOldB.x,0.0)*.16);
}

/* 极宽、无闭合轮廓的内部低频运输，只负责轻微材质流动。 */
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

void main(){
  vec2 z=uRes;
  vec2 p=vec2(gl_FragCoord.x,uRes.y-gl_FragCoord.y);
  float r=min(uRadius,min(z.x,z.y)*.5);
  float sd=boxSdf(p,z,r);
  float mask=1.0-smoothstep(0.0,1.35,sd);
  if(mask<=.001)discard;

  float depth=insideFromSdf(sd);
  float edgeWeight=edgeLensWeight(depth,z);
  vec2 edgeFlow=analyticEdgeFlow(p,z,r,depth,edgeWeight);
  vec2 centerFlow=centerTransport(p,z);
  vec2 totalFlow=edgeFlow+centerFlow;

  vec3 color=sampleBg(globalUv(p+totalFlow));
  float opticalBoost=1.0+edgeWeight*.24+cornerFactor(p,z)*edgeWeight*.10;
  color*=uBody.w*uMat.z*opticalBoost;
  color-=vec3(.055,.065,.085)*uOldB.z*edgeWeight;
  float debugEdge=smoothstep(-1.6,0.0,sd)*mask;
  color=mix(color,vec3(1.0,.45,0.0),debugEdge*uOldB.w);
  gl_FragColor=vec4(clamp(color,0.0,1.0),mask*uMat.y*sat(uMat.x/20.0)*uIntensity);
}`
};

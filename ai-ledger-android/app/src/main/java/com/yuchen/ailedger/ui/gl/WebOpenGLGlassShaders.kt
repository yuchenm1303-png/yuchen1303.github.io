package com.yuchen.ailedger.ui.gl

internal object WebOpenGLGlassShaders {
    const val FRAGMENT_SHADER = """
        precision highp float;
        uniform vec2 uResolution;
        uniform vec2 uCardOrigin;
        uniform vec2 uRootResolution;
        uniform vec4 uRect;
        uniform sampler2D uTexture;
        uniform vec4 uMaterial;
        uniform vec4 uOldA;
        uniform vec4 uOldB;
        uniform vec4 uBody;
        uniform vec4 uBodyBand;
        uniform float uRadius;
        uniform float uIntensity;
        uniform float uTextureReady;
        float sat(float x){return clamp(x,0.0,1.0);} 
        float boxSdf(vec2 p,vec2 z,float r){vec2 q=abs(p-z*0.5)-max(z*0.5-vec2(r),vec2(0.0));return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;}
        vec2 globalUv(vec2 p){return clamp((uCardOrigin+p)/max(uRootResolution,vec2(1.0)),0.0,1.0);} 
        vec3 fallback(vec2 uv){float h=smoothstep(0.0,1.0,uv.y);return mix(vec3(0.04,0.10,0.27),vec3(0.24,0.58,0.76),h);} 
        vec3 src(vec2 uv){return mix(fallback(uv),texture2D(uTexture,clamp(uv,0.0,1.0)).rgb,sat(uTextureReady));}
        float insideDistance(vec2 p,vec2 z,float r){return max(-boxSdf(p,z,r),0.0);} 
        float edgeWidth(vec2 z){return clamp(uOldB.y,2.0,min(z.x,z.y)*0.44);} 
        float rimWide(vec2 p,vec2 z,float r){return 1.0-smoothstep(0.0,edgeWidth(z),insideDistance(p,z,r));}
        float rimCore(vec2 p,vec2 z,float r){return 1.0-smoothstep(0.0,max(edgeWidth(z)*0.28,2.0),insideDistance(p,z,r));}
        float rimBand(vec2 p,vec2 z,float r){return pow(1.0-smoothstep(0.0,max(edgeWidth(z)*1.45,6.0),insideDistance(p,z,r)),1.35);} 
        float dome(vec2 p,vec2 z){vec2 q=clamp(p/z,0.0,1.0)*2.0-1.0;q.x*=min(z.x/max(z.y,1.0),2.4)*0.38;return pow(sat(1.0-length(q)*0.74),1.65);} 
        float thickness(vec2 p,vec2 z,float r){float sd=boxSdf(p,z,r);return (dome(p,z)*0.22+rimWide(p,z,r)*0.46+rimCore(p,z,r)*0.34)*(1.0-smoothstep(1.5,16.0,sd));}
        float rhoAt(vec2 p,vec2 z){vec2 u=clamp(p/z,0.0,1.0);vec2 q=abs(u*2.0-1.0);float n=5.8;return pow(pow(q.x,n)+pow(q.y,n),1.0/n);} 
        float gauss(float x,float m,float w){float q=(x-m)/max(w,0.0001);return exp(-q*q);} 
        float bodyHeight(vec2 p,vec2 z,float r){float sd=boxSdf(p,z,r);float shape=1.0-smoothstep(0.0,4.0,sd);float rho=rhoAt(p,z);float body=pow(sat(1.0-rho),max(uBody.y,0.20));body=pow(body,max(0.28,2.0-uBody.y*0.55));body*=mix(0.72,1.9,sat(uBody.x));float shell=gauss(rho,uBodyBand.x,uBodyBand.y);float shoulder=gauss(rho,uBodyBand.x-0.025,uBodyBand.y*1.8);return (pow(sat(body),0.72)+shell*1.55+shoulder*0.85)*shape;}
        vec2 normalAt(vec2 p,vec2 z,float r){float d=1.4;vec2 n=vec2(boxSdf(p+vec2(d,0.0),z,r)-boxSdf(p-vec2(d,0.0),z,r),boxSdf(p+vec2(0.0,d),z,r)-boxSdf(p-vec2(0.0,d),z,r));return n/max(length(n),0.0001);} 
        vec2 softLimit(vec2 v,float lim){float n=length(v);float m=n/(1.0+n/max(lim,1.0));return v*(m/max(n,0.0001));}
        void main(){
            vec2 coord=vec2(gl_FragCoord.x,uResolution.y-gl_FragCoord.y);vec2 z=max(uRect.zw,vec2(1.0));vec2 p=coord-uRect.xy;float r=min(uRadius,min(z.x,z.y)*0.5);float sd=boxSdf(p,z,r);float mask=1.0-smoothstep(0.0,1.35,sd);if(mask<=0.001)discard;
            float st=2.0;float hL=bodyHeight(p-vec2(st,0.0),z,r);float hR=bodyHeight(p+vec2(st,0.0),z,r);float hU=bodyHeight(p-vec2(0.0,st),z,r);float hD=bodyHeight(p+vec2(0.0,st),z,r);float hC=bodyHeight(p,z,r);vec2 grad=vec2(hR-hL,hD-hU)*0.5;float slope=length(grad);vec2 uvLocal=p/z;float rho=rhoAt(p,z);
            float bodyPresence=sat(pow(sat(1.0-rho),0.42)*(0.38+uBody.x*0.72));float shell=gauss(rho,uBodyBand.x,uBodyBand.y)+gauss(rho,uBodyBand.x-0.028,uBodyBand.y*1.9)*0.55;float side=abs(uvLocal.x*2.0-1.0);float sideVertical=smoothstep(0.055,0.30,uvLocal.y)*smoothstep(0.055,0.30,1.0-uvLocal.y);float sideBand=(gauss(side,uBodyBand.x,max(uBodyBand.y*0.72,0.014))+gauss(side,uBodyBand.x-0.060,max(uBodyBand.y*1.65,0.026))*0.52)*sideVertical*sat(0.42+bodyPresence*0.38+shell*0.40);
            float energy=pow(sat((slope*11.0+hC*0.22)*max(uBodyBand.z,1.0)),max(0.32,max(uBodyBand.z,1.0)*0.55));vec2 bodyFlow=grad*(uBody.z*bodyPresence*(0.35+hC*0.55+energy*0.35)+uBodyBand.z*shell*(0.36+energy*0.70+slope*2.4));bodyFlow.x*=mix(1.0,1.72,sideBand);bodyFlow+=(vec2(0.5)-uvLocal)*vec2(1.0,0.72)*(bodyPresence*uBody.z*0.08);bodyFlow+=vec2(sign(0.5-uvLocal.x),0.0)*((uBodyBand.z*0.185+uBody.z*0.105)*sideBand*(0.72+hC*0.42+bodyPresence*0.25));bodyFlow.y+=(0.5-uvLocal.y)*sideBand*(uBodyBand.z*0.030+uBody.z*0.022)*(0.45+hC*0.35);
            float tL=thickness(p-vec2(st,0.0),z,r);float tR=thickness(p+vec2(st,0.0),z,r);float tU=thickness(p-vec2(0.0,st),z,r);float tD=thickness(p+vec2(0.0,st),z,r);vec2 edgeGrad=vec2(tR-tL,tD-tU);float rw=rimWide(p,z,r);float rc=rimCore(p,z,r);float rb=rimBand(p,z,r);float glen=length(edgeGrad);edgeGrad*=smoothstep(0.0004,0.012,glen)*min(1.0,0.22/max(glen,0.0001));vec2 n=normalAt(p,z,r);
            float corner=pow(rc,1.8)*sat(length(abs(uvLocal*2.0-1.0))*0.55)*uOldA.w*0.012;float pull=uOldA.x+uOldA.y*rw+uOldA.z*8.0*rb+corner;vec2 edgeFlow=softLimit(edgeGrad*pull*uMaterial.x,18.0+rw*48.0+sat(abs(uOldA.y)/600.0)*16.0);float sample=max(uOldB.x,0.0);vec2 uv=globalUv(p+bodyFlow+edgeFlow);vec3 color=src(uv);vec3 nearColor=src(globalUv(p-n*(8.0+sample*0.20+abs(uOldA.y)*0.03)));vec3 farColor=src(globalUv(p-n*(16.0+sample*0.36+abs(uOldA.y)*0.055)));vec3 outColor=src(globalUv(p+n*(6.0+sample*0.12)));vec3 drag=nearColor*0.48+farColor*0.34+outColor*0.18;color=mix(color,drag,sat(rb*(0.10+rc*0.42+sat(uOldA.z*0.05))));float boost=1.0+rc*0.16+rw*max(uMaterial.z,0.0)*0.10;color*=uBody.w*uMaterial.z*boost;color-=vec3(0.06,0.07,0.09)*uOldB.z*rw;color=mix(color,vec3(1.0,0.45,0.0),smoothstep(-1.6,0.0,sd)*mask*uOldB.w);gl_FragColor=vec4(clamp(color,0.0,1.0),mask*uMaterial.y*sat(uMaterial.x/20.0)*uIntensity);
        }
    """
}

'use strict';
function clamp01(v){return Math.max(0,Math.min(1,v))}
function smoothstepJs(a,b,x){const t=clamp01((x-a)/Math.max(b-a,1e-6));return t*t*(3-2*t)}
function roundedRectSdfJs(x,y,w,h,r){const rr=Math.max(0,Math.min(r,Math.min(w,h)*.5));const qx=Math.abs(x-w*.5)-Math.max(w*.5-rr,0);const qy=Math.abs(y-h*.5)-Math.max(h*.5-rr,0);return Math.hypot(Math.max(qx,0),Math.max(qy,0))+Math.min(Math.max(qx,qy),0)-rr}
function rebuildFlowTexture(){
  const fullW=Math.max(cv.width,1),fullH=Math.max(cv.height,1),aspect=fullW/fullH;
  const fw=Math.max(256,Math.min(448,Math.round(fullW/3.5)));
  const fh=Math.max(64,Math.min(160,Math.round(fw/Math.max(aspect,.25))));
  const sx=fullW/fw,sy=fullH/fh,cell=Math.max(sx,sy);
  const outerR=Math.min(46*stageDpr(),fullH*.5);
  flowDepthPx=Math.min(fullH*.44,Math.max(outerR*1.7,fullH*.32));
  const innerW=Math.max(fullW-2*flowDepthPx,fullW*.12),innerH=Math.max(fullH-2*flowDepthPx,fullH*.16);
  const innerR=Math.min(Math.max(outerR-flowDepthPx*.45,4),Math.min(innerW,innerH)*.45);
  const count=fw*fh,fieldA=new Float32Array(count),fieldB=new Float32Array(count),fixed=new Uint8Array(count),ring=new Uint8Array(count);
  const boundary=cell*1.35;
  for(let y=0;y<fh;y++)for(let x=0;x<fw;x++){
    const i=y*fw+x,px=(x+.5)*sx,py=(y+.5)*sy;
    const outer=roundedRectSdfJs(px,py,fullW,fullH,outerR);
    const inner=roundedRectSdfJs(px-flowDepthPx,py-flowDepthPx,innerW,innerH,innerR);
    if(outer>0){fixed[i]=1;fieldA[i]=fieldB[i]=0;continue}
    if(inner<=0){fixed[i]=1;fieldA[i]=fieldB[i]=1;continue}
    ring[i]=1;
    const outerDepth=Math.max(-outer,0),innerDistance=Math.max(inner,0),estimate=outerDepth/Math.max(outerDepth+innerDistance,1e-5);
    fieldA[i]=fieldB[i]=clamp01(estimate);
    if(outerDepth<=boundary){fixed[i]=1;fieldA[i]=fieldB[i]=0}
    else if(innerDistance<=boundary){fixed[i]=1;fieldA[i]=fieldB[i]=1}
  }
  let current=fieldA,next=fieldB;
  const diagonal=.70710678;
  for(let iter=0;iter<180;iter++){
    for(let y=1;y<fh-1;y++)for(let x=1;x<fw-1;x++){
      const i=y*fw+x;if(fixed[i])continue;
      const card=current[i-1]+current[i+1]+current[i-fw]+current[i+fw];
      const diag=current[i-fw-1]+current[i-fw+1]+current[i+fw-1]+current[i+fw+1];
      next[i]=(card+diagonal*diag)/(4+4*diagonal);
    }
    const swap=current;current=next;next=swap;
  }
  const pixels=new Uint8Array(count*4);
  for(let y=0;y<fh;y++)for(let x=0;x<fw;x++){
    const i=y*fw+x,o=i*4,t=clamp01(current[i]);
    let nx=0,ny=0,safe=0;
    if(ring[i]&&x>0&&x<fw-1&&y>0&&y<fh-1){
      const gx=(current[i+1]-current[i-1])/(2*sx),gy=(current[i+fw]-current[i-fw])/(2*sy),g=Math.hypot(gx,gy);
      if(g>1e-6){nx=-gx/g;ny=-gy/g}
      else{
        const e=Math.max(1,cell*.5),dx=roundedRectSdfJs((x+.5)*sx+e,(y+.5)*sy,fullW,fullH,outerR)-roundedRectSdfJs((x+.5)*sx-e,(y+.5)*sy,fullW,fullH,outerR),dy=roundedRectSdfJs((x+.5)*sx,(y+.5)*sy+e,fullW,fullH,outerR)-roundedRectSdfJs((x+.5)*sx,(y+.5)*sy-e,fullW,fullH,outerR),d=Math.hypot(dx,dy)||1;nx=dx/d;ny=dy/d
      }
      const innerFade=1-smoothstepJs(.78,.995,t),quality=clamp01(g*flowDepthPx*.9);
      safe=innerFade*(.55+.45*quality);
    }
    pixels[o]=Math.round(t*255);pixels[o+1]=Math.round((nx*.5+.5)*255);pixels[o+2]=Math.round((ny*.5+.5)*255);pixels[o+3]=Math.round(clamp01(safe)*255);
  }
  gl.activeTexture(gl.TEXTURE1);gl.bindTexture(gl.TEXTURE_2D,flowTex);gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,false);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,fw,fh,0,gl.RGBA,gl.UNSIGNED_BYTE,pixels);gl.activeTexture(gl.TEXTURE0);
}

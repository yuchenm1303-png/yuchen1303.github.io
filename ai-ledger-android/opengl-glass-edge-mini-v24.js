'use strict';

const SHOULDER_CAPTURE_WIDTH_PX=96;

const APP_RAW={
  radius:.230414746543779,iterations:12,brightness:1.14239631336406,contrast:1.0241935483871,saturation:1.112,
  moonScale:1,moonHaloAlpha:.18,moonRimAlpha:.42,
  bodyVisibility:20,bodyMaxAlpha:1,bodyOutputBrightness:1.81152073732719,
  bodyLensBasePull:300,bodyLensPullDp:600,bodyLensConcentration:10,bodyLensCornerBoost:0,
  bodyLensExtraDistance:200,bodyLensReachDp:180,bodyLensDark:.23041474654378,bodyLensDebug:0,
  bodyLowFrequencyWidth:1.25059907834101,bodyLowFrequencyCurve:.2,bodyLowFrequencyGain:12.4423963133641,
  bodyBrightness:.545161290322581,glassIntensity:1.35,
  edgeMode:2,
  shoulderWidthPx:21.7162162162162,
  shoulderMaxAngleDeg:89.5,
  shoulderFalloffRoundness:0,
  shoulderMaterialStrength:4,
  shoulderTangentialFlowStrength:0
};

let p={...APP_RAW},bgMode='flow',blurMoonVisible=false,customBgImage=null,customBgUrl=null;

const groups=[
  {title:'背景模糊层 BackdropDebugParams',items:[
    ['radius','背景模糊半径',0,4],['iterations','模糊迭代次数',1,12],['brightness','背景层亮度',.4,2.2],['contrast','背景层对比',.5,1.8],['saturation','背景层饱和',.3,1.8]
  ]},
  {title:'主体折射 Body Refraction',items:[
    ['bodyVisibility','主体可见强度',0,20],['bodyMaxAlpha','主体最大透明',0,1],['bodyOutputBrightness','主体折射亮度',.2,2.8],['bodyLensBasePull','主体基础拉力',-300,300],['bodyLensPullDp','主体主拉力 dp',-600,600],['bodyLensConcentration','主体向内衰减集中度',-10,10],['bodyLensCornerBoost','主体圆角增强',0,200],['bodyLensExtraDistance','主体额外折射距离',0,200],['bodyLensReachDp','主体作用深度',8,180],['bodyLensDark','主体暗部强度',-10,10],['bodyLensDebug','主体调试线',0,1]
  ]},
  {title:'主体低频运输 Body Low-Frequency Transport',items:[
    ['glassIntensity','样本玻璃强度',.35,1.35],['bodyBrightness','内部输出亮度',.4,2.2],['bodyLowFrequencyWidth','内部运输宽度',.18,1.5],['bodyLowFrequencyCurve','内部运输曲率',.2,3.2],['bodyLowFrequencyGain','内部运输强度',0,900]
  ]},
  {title:'外沿尖峰固定取样 Outer-Peak Fixed Capture',items:[
    ['shoulderWidthPx','圆肩可见宽度',4,96],['shoulderMaxAngleDeg','外沿最大坡度',0,89.5],['shoulderFalloffRoundness','外沿集中与内沿圆润度',0,1],['shoulderMaterialStrength','圆肩整体材质填充',0,4],['shoulderTangentialFlowStrength','固定取样切向揉开',0,2.4]
  ]}
];

const backdropKeys=new Set(['radius','iterations','brightness','contrast','saturation']);
const $=id=>document.getElementById(id);
const stage=$('stage'),scroll=$('scroll'),glassEl=$('glass'),bg=$('bg'),gb=$('gb'),cv=$('gl'),out=$('out'),errorEl=$('error'),moonBtn=$('moonBtn'),bgUpload=$('bgUpload'),uploadBgBtn=$('uploadBgBtn'),clearBgBtn=$('clearBgBtn'),bgStatus=$('bgStatus'),resetBodyBtn=$('resetBodyBtn');
const ctx=bg.getContext('2d'),gbCtx=gb.getContext('2d'),sourceCanvas=document.createElement('canvas'),sourceCtx=sourceCanvas.getContext('2d'),colorCanvas=document.createElement('canvas'),colorCtx=colorCanvas.getContext('2d'),blurA=document.createElement('canvas'),blurACtx=blurA.getContext('2d'),blurB=document.createElement('canvas'),blurBCtx=blurB.getContext('2d'),blurCanvas=document.createElement('canvas'),blurCtx=blurCanvas.getContext('2d');
let gl,program,buffer,L,blurTex,backdropFrame=0,backdropRevision=0;

function initGl(){
  gl=cv.getContext('webgl',{alpha:true,premultipliedAlpha:false});
  if(!gl)throw new Error('当前浏览器未启用 WebGL');
  const {vs,fs}=window.OpenGLV24Shaders;
  const compile=(type,source)=>{const shader=gl.createShader(type);gl.shaderSource(shader,source);gl.compileShader(shader);if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(shader));return shader};
  program=gl.createProgram();gl.attachShader(program,compile(gl.VERTEX_SHADER,vs));gl.attachShader(program,compile(gl.FRAGMENT_SHADER,fs));gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(program));
  buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buffer);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,1,1]),gl.STATIC_DRAW);
  const names=['a','uRes','uOrigin','uRoot','uBlurTexture','uMat','uBodyLensA','uBodyLensB','uBody','uShoulder','uShoulderFlow','uShoulderEnabled','uRadius','uIntensity'];
  L={};for(const name of names)L[name]=name==='a'?gl.getAttribLocation(program,name):gl.getUniformLocation(program,name);
  const missing=names.filter(name=>name==='a'?L[name]<0:L[name]===null);if(missing.length)throw new Error('Shader 参数未绑定：'+missing.join(', '));
  blurTex=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,blurTex);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
}

function cover(c,img,w,h){const iw=Math.max(img.naturalWidth||img.width||1,1),ih=Math.max(img.naturalHeight||img.height||1,1),s=Math.max(w/iw,h/ih),dw=iw*s,dh=ih*s;c.drawImage(img,(w-dw)/2,(h-dh)/2,dw,dh)}

function drawBackdrop(c,w,h,withText){
  c.save();c.setTransform(1,0,0,1,0,0);c.clearRect(0,0,w,h);
  if(customBgImage){cover(c,customBgImage,w,h);c.restore();return}
  if(bgMode==='stripes'){for(let x=-w;x<w*2;x+=42){c.fillStyle=Math.floor(x/42)%2?'#07142f':'#eef6ff';c.fillRect(x,0,22,h)}c.restore();return}
  const g=c.createLinearGradient(0,0,0,h);g.addColorStop(0,'#0a3a80');g.addColorStop(.42,'#4fd5ff');g.addColorStop(.72,'#173066');g.addColorStop(1,'#d06c9e');c.fillStyle=g;c.fillRect(0,0,w,h);
  if(bgMode==='grid'){c.strokeStyle='rgba(255,255,255,.65)';c.lineWidth=Math.max(1,w/1200);for(let x=0;x<w;x+=30){c.beginPath();c.moveTo(x,0);c.lineTo(x,h);c.stroke()}for(let y=0;y<h;y+=30){c.beginPath();c.moveTo(0,y);c.lineTo(w,y);c.stroke()}c.restore();return}
  c.globalAlpha=.55;for(let i=0;i<11;i++){c.strokeStyle=i%2?'#fff':'#68e7ff';c.lineWidth=3+i*.45;c.beginPath();c.moveTo(-80,130+i*60);c.bezierCurveTo(260,20+i*10,520,330-i*20,1280,130+i*30);c.stroke()}
  c.globalAlpha=.25;for(let y=70;y<h;y+=160)for(let x=70;x<w;x+=140){c.fillStyle='#fff';c.fillRect(x,y,80,14)}
  c.globalAlpha=1;if(withText||blurMoonVisible){const r=Math.min(w,h)*.055*p.moonScale,mx=w*.73,my=h*.23,halo=c.createRadialGradient(mx,my,0,mx,my,r*2.25);halo.addColorStop(0,`rgba(255,244,218,${p.moonHaloAlpha})`);halo.addColorStop(1,'rgba(255,244,218,0)');c.fillStyle=halo;c.beginPath();c.arc(mx,my,r*2.25,0,Math.PI*2);c.fill();c.fillStyle='rgba(255,242,210,.92)';c.beginPath();c.arc(mx,my,r,0,Math.PI*2);c.fill()}
  if(withText){c.fillStyle='rgba(255,255,255,.22)';c.font=`900 ${Math.max(18,w*.038)}px system-ui`;c.fillText('BACKGROUND LAYER',w*.08,h*.42)}c.restore();
}

const dpr=()=>Math.min(window.devicePixelRatio||1,2);
function glassRect(){const a=stage.getBoundingClientRect(),b=glassEl.getBoundingClientRect();return{x:b.left-a.left,y:b.top-a.top,w:b.width,h:b.height}}
function smoothCtx(c){c.setTransform(1,0,0,1,0,0);c.imageSmoothingEnabled=true;try{c.imageSmoothingQuality='high'}catch(_){}c.globalAlpha=1;c.globalCompositeOperation='source-over'}
function size(canvas,w,h){w=Math.max(1,Math.round(w));h=Math.max(1,Math.round(h));if(canvas.width!==w)canvas.width=w;if(canvas.height!==h)canvas.height=h}
function drawGlassBackdrop(){const d=dpr(),q=glassRect(),sx=q.x*d,sy=q.y*d,sw=q.w*d,sh=q.h*d;size(gb,sw,sh);smoothCtx(gbCtx);gbCtx.clearRect(0,0,gb.width,gb.height);gbCtx.drawImage(blurCanvas,sx,sy,sw,sh,0,0,gb.width,gb.height)}
const effectiveBlurPx=d=>Math.max(0,p.radius*d*Math.pow(Math.max(1,p.iterations),.55));
function shift(dst,src,w,h,step,horizontal){smoothCtx(dst);dst.clearRect(0,0,w,h);dst.save();dst.globalCompositeOperation='lighter';dst.globalAlpha=.2;for(let i=-2;i<=2;i++)dst.drawImage(src,horizontal?i*step:0,horizontal?0:i*step,w,h);dst.restore();dst.save();dst.globalCompositeOperation='destination-over';dst.drawImage(src,0,0,w,h);dst.restore()}
function blur(source,w,h,radius){smoothCtx(blurCtx);blurCtx.clearRect(0,0,w,h);if(radius<=.025){blurCtx.drawImage(source,0,0,w,h);return}size(blurA,w,h);size(blurB,w,h);const passes=Math.max(1,Math.min(3,Math.ceil(p.iterations/4))),step=Math.max(.25,radius/Math.sqrt(2*passes));let current=source;for(let i=0;i<passes;i++){shift(blurACtx,current,w,h,step,true);shift(blurBCtx,blurA,w,h,step,false);current=blurB}blurCtx.drawImage(current,0,0,w,h)}

function rebuildBackdrop(){
  const d=dpr(),r=stage.getBoundingClientRect(),w=Math.max(1,Math.round(r.width*d)),h=Math.max(1,Math.round(r.height*d));
  size(bg,w,h);size(sourceCanvas,w,h);size(colorCanvas,w,h);size(blurCanvas,w,h);drawBackdrop(ctx,w,h,true);drawBackdrop(sourceCtx,w,h,false);
  smoothCtx(colorCtx);colorCtx.clearRect(0,0,w,h);colorCtx.save();colorCtx.filter=`brightness(${p.brightness}) contrast(${p.contrast}) saturate(${p.saturation})`;colorCtx.drawImage(sourceCanvas,0,0,w,h);colorCtx.restore();
  blur(colorCanvas,w,h,effectiveBlurPx(d));drawGlassBackdrop();gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,blurTex);gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,false);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,blurCanvas);gl.flush();backdropRevision++;
}

function render(){
  drawGlassBackdrop();gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT);gl.useProgram(program);gl.bindBuffer(gl.ARRAY_BUFFER,buffer);gl.enableVertexAttribArray(L.a);gl.vertexAttribPointer(L.a,2,gl.FLOAT,false,0,0);
  const d=dpr(),q=glassRect();gl.uniform2f(L.uRes,cv.width,cv.height);gl.uniform2f(L.uOrigin,q.x*d,q.y*d);gl.uniform2f(L.uRoot,bg.width,bg.height);gl.uniform1f(L.uRadius,46*d);gl.uniform1f(L.uIntensity,p.glassIntensity);
  gl.uniform4f(L.uMat,p.bodyVisibility,p.bodyMaxAlpha,p.bodyOutputBrightness,0);
  gl.uniform4f(L.uBodyLensA,p.bodyLensBasePull*d,p.bodyLensPullDp*d,p.bodyLensConcentration,p.bodyLensCornerBoost);
  gl.uniform4f(L.uBodyLensB,p.bodyLensExtraDistance*d,p.bodyLensReachDp*d,p.bodyLensDark,p.bodyLensDebug);
  gl.uniform4f(L.uBody,p.bodyLowFrequencyWidth,p.bodyLowFrequencyCurve,p.bodyLowFrequencyGain,p.bodyBrightness);
  gl.uniform4f(L.uShoulder,p.shoulderWidthPx*d,p.shoulderMaxAngleDeg,p.shoulderFalloffRoundness,p.shoulderMaterialStrength);
  gl.uniform2f(L.uShoulderFlow,SHOULDER_CAPTURE_WIDTH_PX*d,p.shoulderTangentialFlowStrength);
  gl.uniform1f(L.uShoulderEnabled,p.edgeMode);
  gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,blurTex);gl.uniform1i(L.uBlurTexture,0);gl.drawArrays(gl.TRIANGLE_STRIP,0,4);
}

function format(v){return String(Math.round(v*1000)/1000)}
function edgeModeName(mode){return mode===0?'pureV25_3Body':mode===1?'originalV29_4LocalNormalCapture':'unifiedPerimeterContourMapping'}
function updateUi(){for(const [key] of groups.flatMap(group=>group.items)){const label=$('v-'+key),input=$('i-'+key);if(label)label.textContent=format(p[key]);if(input&&Math.abs(Number(input.value)-p[key])>1e-9)input.value=p[key]}document.querySelectorAll('[data-edge-mode]').forEach(button=>button.classList.toggle('on',Number(button.dataset.edgeMode)===p.edgeMode));out.textContent=JSON.stringify({mode:'v29_5UnifiedPerimeterMappingTest',edgeMode:edgeModeName(p.edgeMode),peakLocation:'outerBoundary',visibleShoulderWidthPx:p.shoulderWidthPx,fixedCaptureWidthPx:SHOULDER_CAPTURE_WIDTH_PX,captureBehavior:p.edgeMode===2?'sharedInnerContourC1Convergence':'fixedDeepDomainWithSourcePointC1Convergence',shoulderBodyTransition:'sourcePointC1ConvergenceNoCrossfade',tangentialFlowBasis:'fixedCaptureWidthOuterEnvelope',innerRim:false,oneFinalOpticalCoord:true,extraEdgeSamples:0,blurBackend:'fullResolutionShiftAverage',backdropRevision,effectiveBlurPx:effectiveBlurPx(dpr()),...p},null,2)}
function refresh(){if(backdropFrame)cancelAnimationFrame(backdropFrame);backdropFrame=0;rebuildBackdrop();updateUi();render()}
function schedule(){if(backdropFrame)cancelAnimationFrame(backdropFrame);backdropFrame=requestAnimationFrame(()=>{backdropFrame=0;rebuildBackdrop();updateUi();render()})}

function buildControls(){
  const root=$('controls');root.innerHTML='';
  for(const group of groups){const title=document.createElement('div');title.className='groupTitle';title.textContent=group.title;root.appendChild(title);for(const [key,name,min,max] of group.items){const row=document.createElement('div');row.className='c';row.innerHTML=`<div class="h"><strong>${name}</strong><small id="v-${key}"></small></div><input id="i-${key}" type="range" min="${min}" max="${max}" step="any" value="${p[key]}">`;root.appendChild(row);const input=$('i-'+key),apply=event=>{p[key]=Number(event.target.value);if(backdropKeys.has(key))schedule();else{updateUi();render()}};input.addEventListener('input',apply);input.addEventListener('change',event=>{p[key]=Number(event.target.value);if(backdropKeys.has(key))refresh();else{updateUi();render()}})}}updateUi();
}

function clearCustom(redraw=true){if(customBgUrl)URL.revokeObjectURL(customBgUrl);customBgUrl=null;customBgImage=null;bgUpload.value='';uploadBgBtn.textContent='上传自定义背景';clearBgBtn.disabled=true;bgStatus.textContent='当前背景：'+(bgMode==='flow'?'默认流线背景':bgMode==='stripes'?'黑白条纹':'细网格');if(redraw)refresh()}
uploadBgBtn.addEventListener('click',()=>bgUpload.click());clearBgBtn.addEventListener('click',()=>clearCustom(true));
bgUpload.addEventListener('change',event=>{const file=event.target.files&&event.target.files[0];if(!file)return;const url=URL.createObjectURL(file),image=new Image();image.onload=()=>{if(customBgUrl)URL.revokeObjectURL(customBgUrl);customBgUrl=url;customBgImage=image;uploadBgBtn.textContent='更换自定义背景';clearBgBtn.disabled=false;bgStatus.textContent='当前背景：'+file.name;refresh()};image.onerror=()=>{URL.revokeObjectURL(url);alert('图片读取失败')};image.src=url});
document.querySelectorAll('[data-bg]').forEach(button=>button.addEventListener('click',()=>{clearCustom(false);bgMode=button.dataset.bg;bgStatus.textContent='当前背景：'+(bgMode==='flow'?'默认流线背景':bgMode==='stripes'?'黑白条纹':'细网格');refresh()}));
document.querySelectorAll('[data-edge-mode]').forEach(button=>button.addEventListener('click',()=>{p.edgeMode=Number(button.dataset.edgeMode);updateUi();render()}));
resetBodyBtn.addEventListener('click',()=>{p={...APP_RAW};refresh()});
moonBtn.addEventListener('click',()=>{blurMoonVisible=!blurMoonVisible;moonBtn.textContent=blurMoonVisible?'隐藏模糊层月亮':'显示模糊层月亮';moonBtn.classList.toggle('on',blurMoonVisible);refresh()});
function resize(){const d=dpr(),r=cv.getBoundingClientRect();cv.width=Math.max(1,Math.round(r.width*d));cv.height=Math.max(1,Math.round(r.height*d));gl.viewport(0,0,cv.width,cv.height);refresh()}
window.addEventListener('resize',resize);scroll.addEventListener('scroll',render,{passive:true});window.addEventListener('beforeunload',()=>{if(customBgUrl)URL.revokeObjectURL(customBgUrl)});

try{document.title='OpenGL V29.5 · Unified Perimeter Mapping Test';initGl();buildControls();requestAnimationFrame(()=>{resize();scroll.scrollTop=500;render();window.__glassDebug={gl,program,getParams:()=>({...p}),getBackdropRevision:()=>backdropRevision,refreshBackdrop:refresh,render}})}catch(error){errorEl.textContent=String(error&&error.stack||error);console.error(error)}

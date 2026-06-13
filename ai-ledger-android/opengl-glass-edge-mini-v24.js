'use strict';
const APP_RAW={
  radius:.230414746543779,
  iterations:12,
  brightness:1.14239631336406,
  contrast:1.0241935483871,
  saturation:1.112,
  moonScale:1,
  moonHaloAlpha:.18,
  moonRimAlpha:.42,

  bodyVisibility:20,
  bodyMaxAlpha:1,
  bodyOutputBrightness:1.81152073732719,
  bodyLensBasePull:300,
  bodyLensPullDp:600,
  bodyLensConcentration:10,
  bodyLensCornerBoost:0,
  bodyLensExtraDistance:200,
  bodyLensReachDp:180,
  bodyLensDark:.23041474654378,
  bodyLensDebug:0,
  bodyLowFrequencyWidth:1.25059907834101,
  bodyLowFrequencyCurve:.2,
  bodyLowFrequencyGain:12.4423963133641,
  bodyBrightness:.545161290322581,
  glassIntensity:1.35,

  legacyVisibility:20,
  legacyMaxAlpha:1,
  legacyEdgeBrightness:1.15207373271889,
  legacyPullScale:-300,
  legacyEdgePullDp:-600,
  legacyCompressionScale:-10,
  legacyCornerScale:200,
  legacySampleRadiusScale:14.2857142857143,
  legacyEdgeWidthDp:8.29493087557604,
  legacyDarkScale:-2.58064516129032,
  legacyDebugLineAlpha:0,

  edgeMode:2,
  debugView:0,
  compareSplit:.5,

  innerStartPx:1.5,
  innerWidthPx:10,
  innerTangentSpreadPx:4.8,
  innerTangentStrength:.42,
  innerNormalPullPx:1.25,
  innerCornerBoost:.18,
  innerOpacity:.50,

  rimWidthPx:7,
  rimOpacity:.38,
  rimTransmission:.92,
  rimTintStrength:.18,
  rimInnerAbsorption:.22,
  rimOuterHighlight:.38,
  rimLightX:-.72,
  rimLightY:-.68,
  rimHighlightPower:3.4,
  rimCornerBoost:.18,
  rimAlphaContribution:.18
};
const LEGACY_FIXED={
  legacyEdgeBrightness:1.03,
  legacyPullScale:83.21,
  legacyEdgePullDp:-205.94,
  legacyCompressionScale:-10,
  legacyCornerScale:0,
  legacySampleRadiusScale:0,
  legacyEdgeWidthDp:10,
  legacyDarkScale:-1.63,
  legacyDebugLineAlpha:0
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
  {title:'新双层 · Inner Refraction Band',items:[
    ['innerStartPx','内侧起始深度',0,5],['innerWidthPx','内侧带宽度',4,18],['innerTangentSpreadPx','切线扩散距离',0,12],['innerTangentStrength','切线扩散强度',0,1],['innerNormalPullPx','法线拉入量',0,4],['innerCornerBoost','圆角扩散增强',0,1],['innerOpacity','内侧带透明度',0,1]
  ]},
  {title:'新双层 · Rim Material',items:[
    ['rimWidthPx','实体边框宽度',3,12],['rimOpacity','材质覆盖强度',0,1],['rimTransmission','透射率',.5,1],['rimTintStrength','背景染色强度',0,.6],['rimInnerAbsorption','内沿吸收',0,.8],['rimOuterHighlight','外沿方向亮面',0,1],['rimLightX','光源方向 X',-1,1],['rimLightY','光源方向 Y',-1,1],['rimHighlightPower','亮面方向曲线',1,8],['rimCornerBoost','圆角厚度增强',0,1],['rimAlphaContribution','边框 Alpha 贡献',0,.5]
  ]},
  {title:'对比与旧边缘 Legacy Edge',items:[
    ['compareSplit','左右对比分割',.1,.9],['legacyVisibility','旧边缘可见强度',0,20],['legacyMaxAlpha','旧边缘最大透明',0,1],['legacyEdgeBrightness','旧边缘亮度',-5,5],['legacyPullScale','旧边缘基础拉力',-300,300],['legacyEdgePullDp','旧边缘拉力 dp',-600,600],['legacyCompressionScale','旧边缘拖影强度',-10,10],['legacyCornerScale','旧边缘梯度增益',0,200],['legacySampleRadiusScale','旧边缘柔化半径',0,200],['legacyEdgeWidthDp','旧边缘宽度',0,300],['legacyDarkScale','旧边缘暗部强度',-10,10],['legacyDebugLineAlpha','旧边缘调试线',0,1]
  ]}
];
const backdropParamKeys=new Set(['radius','iterations','brightness','contrast','saturation']);
const $=id=>document.getElementById(id),stage=$('stage'),scroll=$('scroll'),glassEl=$('glass'),bg=$('bg'),gb=$('gb'),cv=$('gl'),out=$('out'),errorEl=$('error'),moonBtn=$('moonBtn'),bgUpload=$('bgUpload'),uploadBgBtn=$('uploadBgBtn'),clearBgBtn=$('clearBgBtn'),bgStatus=$('bgStatus'),legacyDomRim=$('legacyDomRim'),debugView=$('debugView');
let compareDivider=null;
const ctx=bg.getContext('2d'),gbCtx=gb.getContext('2d'),sourceCanvas=document.createElement('canvas'),sourceCtx=sourceCanvas.getContext('2d'),blurCanvas=document.createElement('canvas'),blurCtx=blurCanvas.getContext('2d');

function prepareShellUi(){
  document.title='OpenGL V25.3 · Dual-Layer Edge Material';
  const heading=document.querySelector('.panel h2');if(heading)heading.textContent='V25.3 · 新双层边缘实验';
  const subtitle=document.querySelector('.ui p');if(subtitle)subtitle.textContent='v25.3 body · inner refraction band + transparent rim material';
  const appPreset=document.querySelector('[data-preset="app"]');if(appPreset)appPreset.textContent='双层默认';
  const strongPreset=document.querySelector('[data-preset="strong"]');if(strongPreset)strongPreset.remove();
  const modeRow=document.querySelector('.modeRow');if(modeRow)modeRow.innerHTML='<button data-edge-mode="0">仅主体</button><button data-edge-mode="1">旧边缘</button><button data-edge-mode="2">新双层边缘</button><button data-edge-mode="3">左右对比</button>';
  debugView.innerHTML='<option value="0">模式最终输出</option><option value="1">仅主体</option><option value="2">仅 Inner Band 遮罩</option><option value="3">仅 Inner Band 颜色</option><option value="4">仅 Rim 遮罩</option><option value="5">仅 Rim 材质颜色</option><option value="6">最终双层合成</option><option value="7">SDF 距离</option><option value="8">法线方向</option><option value="9">切线方向</option>';
  compareDivider=document.getElementById('compareDivider');
  if(!compareDivider){compareDivider=document.createElement('div');compareDivider.id='compareDivider';compareDivider.className='compareDivider';compareDivider.innerHTML='<span>左：旧边缘｜右：新双层</span>';glassEl.appendChild(compareDivider);}
  if(!document.getElementById('dualEdgeShellStyle')){const style=document.createElement('style');style.id='dualEdgeShellStyle';style.textContent='.compareDivider{position:absolute;top:0;bottom:0;left:50%;width:2px;transform:translateX(-1px);background:rgba(255,255,255,.94);box-shadow:0 0 0 1px rgba(11,29,67,.48),0 0 12px rgba(255,255,255,.45);z-index:5;pointer-events:none;display:none}.compareDivider.on{display:block}.compareDivider span{position:absolute;top:10px;left:50%;transform:translateX(-50%);white-space:nowrap;padding:5px 9px;border-radius:999px;background:rgba(8,20,50,.82);border:1px solid rgba(255,255,255,.28);font-size:10px;font-weight:900;letter-spacing:.04em}';document.head.appendChild(style);}
}

let gl,program,buffer,L,blurTex,lensTex;
function initGl(){
  gl=cv.getContext('webgl',{alpha:true,premultipliedAlpha:false});
  if(!gl)throw new Error('当前浏览器未启用 WebGL');
  const {vs,fs}=window.OpenGLV24Shaders;
  function compile(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(s));return s}
  program=gl.createProgram();gl.attachShader(program,compile(gl.VERTEX_SHADER,vs));gl.attachShader(program,compile(gl.FRAGMENT_SHADER,fs));gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(program));
  buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buffer);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,1,1]),gl.STATIC_DRAW);
  const names=['a','uRes','uOrigin','uRoot','uShapeOffset','uShapeSize','uBlurTexture','uLensTexture','uMat','uBodyLensA','uBodyLensB','uBody','uLegacyMaterial','uLegacyRefraction','uLegacyOptics','uInnerA','uInnerB','uRimA','uRimB','uRimC','uMode','uRadius','uIntensity','uTextureReady'];
  L={};for(const name of names)L[name]=name==='a'?gl.getAttribLocation(program,name):gl.getUniformLocation(program,name);
  const missing=names.filter(name=>name==='a'?L[name]<0:L[name]===null);if(missing.length)throw new Error('Shader 参数未绑定：'+missing.join(', '));
  function initTexture(texture){gl.bindTexture(gl.TEXTURE_2D,texture);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE)}
  blurTex=gl.createTexture();lensTex=gl.createTexture();initTexture(blurTex);initTexture(lensTex);
}
function drawCover(c,img,w,h){const iw=Math.max(img.naturalWidth||img.width||1,1),ih=Math.max(img.naturalHeight||img.height||1,1),s=Math.max(w/iw,h/ih),dw=iw*s,dh=ih*s;c.drawImage(img,(w-dw)/2,(h-dh)/2,dw,dh)}
function drawBackdrop(c,w,h,withText){
  c.save();c.setTransform(1,0,0,1,0,0);c.clearRect(0,0,w,h);
  if(customBgImage){drawCover(c,customBgImage,w,h);c.restore();return}
  if(bgMode==='stripes'){for(let x=-w;x<w*2;x+=42){c.fillStyle=Math.floor(x/42)%2?'#07142f':'#eef6ff';c.fillRect(x,0,22,h)}c.restore();return}
  const g=c.createLinearGradient(0,0,0,h);g.addColorStop(0,'#0a3a80');g.addColorStop(.42,'#4fd5ff');g.addColorStop(.72,'#173066');g.addColorStop(1,'#d06c9e');c.fillStyle=g;c.fillRect(0,0,w,h);
  if(bgMode==='grid'){c.strokeStyle='rgba(255,255,255,.65)';c.lineWidth=Math.max(1,w/1200);for(let x=0;x<w;x+=30){c.beginPath();c.moveTo(x,0);c.lineTo(x,h);c.stroke()}for(let y=0;y<h;y+=30){c.beginPath();c.moveTo(0,y);c.lineTo(w,y);c.stroke()}c.restore();return}
  c.globalAlpha=.55;for(let i=0;i<11;i++){c.strokeStyle=i%2?'#fff':'#68e7ff';c.lineWidth=3+i*.45;c.beginPath();c.moveTo(-80,130+i*60);c.bezierCurveTo(260,20+i*10,520,330-i*20,1280,130+i*30);c.stroke()}
  c.globalAlpha=.25;for(let y=70;y<h;y+=160)for(let x=70;x<w;x+=140){c.fillStyle='#fff';c.fillRect(x,y,80,14)}
  c.globalAlpha=1;if(withText||blurMoonVisible){const r=Math.min(w,h)*.055*p.moonScale,mx=w*.73,my=h*.23,halo=c.createRadialGradient(mx,my,0,mx,my,r*2.25);halo.addColorStop(0,`rgba(255,244,218,${p.moonHaloAlpha})`);halo.addColorStop(1,'rgba(255,244,218,0)');c.fillStyle=halo;c.beginPath();c.arc(mx,my,r*2.25,0,Math.PI*2);c.fill();c.fillStyle='rgba(255,242,210,.92)';c.beginPath();c.arc(mx,my,r,0,Math.PI*2);c.fill()}
  if(withText){c.fillStyle='rgba(255,255,255,.22)';c.font=`900 ${Math.max(18,w*.038)}px system-ui`;c.fillText('BACKGROUND LAYER',w*.08,h*.42)}c.restore()
}
function stageDpr(){return Math.min(window.devicePixelRatio||1,2)}
function glassRect(){const a=stage.getBoundingClientRect(),b=glassEl.getBoundingClientRect();return{x:b.left-a.left,y:b.top-a.top,w:b.width,h:b.height}}
function drawGlassBackdrop(){const d=stageDpr(),q=glassRect(),sx=q.x*d,sy=q.y*d,sw=q.w*d,sh=q.h*d;gb.width=Math.max(1,Math.round(sw));gb.height=Math.max(1,Math.round(sh));gbCtx.clearRect(0,0,gb.width,gb.height);gbCtx.drawImage(blurCanvas,sx,sy,sw,sh,0,0,gb.width,gb.height)}
function uploadBackdropTextures(){gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,blurTex);gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,false);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,blurCanvas);gl.activeTexture(gl.TEXTURE1);gl.bindTexture(gl.TEXTURE_2D,lensTex);gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,false);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,sourceCanvas);gl.activeTexture(gl.TEXTURE0)}
function rebuildBackdrop(){const d=stageDpr(),r=stage.getBoundingClientRect(),w=Math.max(1,Math.round(r.width*d)),h=Math.max(1,Math.round(r.height*d));bg.width=w;bg.height=h;sourceCanvas.width=w;sourceCanvas.height=h;blurCanvas.width=w;blurCanvas.height=h;drawBackdrop(ctx,w,h,true);drawBackdrop(sourceCtx,w,h,false);blurCtx.save();blurCtx.clearRect(0,0,w,h);const blurPx=Math.max(0,p.radius*d*Math.pow(Math.max(1,p.iterations),.55));blurCtx.filter=`blur(${blurPx}px) brightness(${p.brightness}) contrast(${p.contrast}) saturate(${p.saturation})`;blurCtx.drawImage(sourceCanvas,0,0);blurCtx.restore();drawGlassBackdrop();uploadBackdropTextures()}
function render(){
  drawGlassBackdrop();gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT);gl.useProgram(program);gl.bindBuffer(gl.ARRAY_BUFFER,buffer);gl.enableVertexAttribArray(L.a);gl.vertexAttribPointer(L.a,2,gl.FLOAT,false,0,0);
  const d=stageDpr(),q=glassRect(),stageRect=stage.getBoundingClientRect(),canvasRect=cv.getBoundingClientRect(),shapeOffsetX=(q.x-(canvasRect.left-stageRect.left))*d,shapeOffsetY=(q.y-(canvasRect.top-stageRect.top))*d;
  gl.uniform2f(L.uRes,cv.width,cv.height);gl.uniform2f(L.uOrigin,q.x*d,q.y*d);gl.uniform2f(L.uRoot,bg.width,bg.height);gl.uniform2f(L.uShapeOffset,shapeOffsetX,shapeOffsetY);gl.uniform2f(L.uShapeSize,q.w*d,q.h*d);
  gl.uniform1f(L.uRadius,46*d);gl.uniform1f(L.uIntensity,p.glassIntensity);gl.uniform1f(L.uTextureReady,1);
  gl.uniform4f(L.uMat,p.bodyVisibility,p.bodyMaxAlpha,p.bodyOutputBrightness,0);gl.uniform4f(L.uBodyLensA,p.bodyLensBasePull*d,p.bodyLensPullDp*d,p.bodyLensConcentration,p.bodyLensCornerBoost);gl.uniform4f(L.uBodyLensB,p.bodyLensExtraDistance*d,p.bodyLensReachDp*d,p.bodyLensDark,p.bodyLensDebug);gl.uniform4f(L.uBody,p.bodyLowFrequencyWidth,p.bodyLowFrequencyCurve,p.bodyLowFrequencyGain,p.bodyBrightness);
  gl.uniform4f(L.uLegacyMaterial,p.legacyVisibility,p.legacyMaxAlpha,p.legacyEdgeBrightness,0);gl.uniform4f(L.uLegacyRefraction,p.legacyPullScale*d,p.legacyEdgePullDp*d,p.legacyCompressionScale,p.legacyCornerScale);gl.uniform4f(L.uLegacyOptics,p.legacySampleRadiusScale*d,p.legacyEdgeWidthDp*d,p.legacyDebugLineAlpha,p.legacyDarkScale);
  gl.uniform4f(L.uInnerA,p.innerStartPx*d,p.innerWidthPx*d,p.innerTangentSpreadPx*d,p.innerTangentStrength);gl.uniform4f(L.uInnerB,p.innerNormalPullPx*d,p.innerCornerBoost,p.innerOpacity,0);
  gl.uniform4f(L.uRimA,p.rimWidthPx*d,p.rimOpacity,p.rimTransmission,p.rimTintStrength);gl.uniform4f(L.uRimB,p.rimInnerAbsorption,p.rimOuterHighlight,p.rimLightX,p.rimLightY);gl.uniform4f(L.uRimC,p.rimHighlightPower,p.rimCornerBoost,p.rimAlphaContribution,0);gl.uniform4f(L.uMode,p.edgeMode,p.debugView,p.compareSplit,0);
  gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,blurTex);gl.uniform1i(L.uBlurTexture,0);gl.activeTexture(gl.TEXTURE1);gl.bindTexture(gl.TEXTURE_2D,lensTex);gl.uniform1i(L.uLensTexture,1);gl.drawArrays(gl.TRIANGLE_STRIP,0,4);gl.activeTexture(gl.TEXTURE0)
}
function resize(){const d=stageDpr(),r=cv.getBoundingClientRect();cv.width=Math.max(1,Math.round(r.width*d));cv.height=Math.max(1,Math.round(r.height*d));gl.viewport(0,0,cv.width,cv.height);rebuildBackdrop();render()}
function fmt(v){return String(Math.round(v*1000)/1000)}
function syncModeUi(){
  document.querySelectorAll('[data-edge-mode]').forEach(btn=>btn.classList.toggle('on',Number(btn.dataset.edgeMode)===p.edgeMode));debugView.value=String(p.debugView);
  const normalOutput=p.debugView===0,showLegacyDom=normalOutput&&(p.edgeMode===1||p.edgeMode===3),showCompare=normalOutput&&p.edgeMode===3;
  legacyDomRim.classList.toggle('off',!showLegacyDom);legacyDomRim.classList.toggle('compare',showCompare);legacyDomRim.style.setProperty('--legacy-split',`${p.compareSplit*100}%`);
  compareDivider.classList.toggle('on',showCompare);compareDivider.style.left=`${p.compareSplit*100}%`;
}
function updateUi(){groups.flatMap(g=>g.items).forEach(([k])=>{const label=$('v-'+k),input=$('i-'+k);if(label)label.textContent=fmt(p[k]);if(input&&Math.abs(Number(input.value)-p[k])>1e-9)input.value=p[k]});syncModeUi();const modes=['body','legacy','dual','compare'];out.textContent=JSON.stringify({mode:'v25BodyWithDualLayerEdge',edgeMode:modes[p.edgeMode]||'unknown',debugView:p.debugView,legacySourceCommit:'9a6e4ac7605da3859ff9accd4a33fe0bab7a9ddc',...p},null,2)}
function buildControls(){const root=$('controls');root.innerHTML='';for(const group of groups){const title=document.createElement('div');title.className='groupTitle';title.textContent=group.title;root.appendChild(title);for(const [k,name,min,max] of group.items){const row=document.createElement('div');row.className='c';row.innerHTML=`<div class="h"><strong>${name}</strong><small id="v-${k}"></small></div><input id="i-${k}" type="range" min="${min}" max="${max}" step="any" value="${p[k]}">`;root.appendChild(row);$('i-'+k).addEventListener('input',e=>{p[k]=Number(e.target.value);if(backdropParamKeys.has(k))rebuildBackdrop();updateUi();render()})}}updateUi()}
function clearCustomBackground(redraw=true){if(customBgUrl)URL.revokeObjectURL(customBgUrl);customBgUrl=null;customBgImage=null;bgUpload.value='';uploadBgBtn.textContent='上传自定义背景';clearBgBtn.disabled=true;bgStatus.textContent='当前背景：'+(bgMode==='flow'?'默认流线背景':bgMode==='stripes'?'黑白条纹':'细网格');if(redraw){rebuildBackdrop();render()}}
prepareShellUi();
uploadBgBtn.addEventListener('click',()=>bgUpload.click());clearBgBtn.addEventListener('click',()=>clearCustomBackground(true));
bgUpload.addEventListener('change',e=>{const file=e.target.files&&e.target.files[0];if(!file)return;const url=URL.createObjectURL(file),img=new Image();img.onload=()=>{if(customBgUrl)URL.revokeObjectURL(customBgUrl);customBgUrl=url;customBgImage=img;uploadBgBtn.textContent='更换自定义背景';clearBgBtn.disabled=false;bgStatus.textContent='当前背景：'+file.name;rebuildBackdrop();render()};img.onerror=()=>{URL.revokeObjectURL(url);alert('图片读取失败')};img.src=url});
document.querySelectorAll('[data-bg]').forEach(btn=>btn.addEventListener('click',()=>{clearCustomBackground(false);bgMode=btn.dataset.bg;bgStatus.textContent='当前背景：'+(bgMode==='flow'?'默认流线背景':bgMode==='stripes'?'黑白条纹':'细网格');rebuildBackdrop();render()}));
document.querySelectorAll('[data-edge-mode]').forEach(btn=>btn.addEventListener('click',()=>{p.edgeMode=Number(btn.dataset.edgeMode);p.debugView=0;updateUi();render()}));
debugView.addEventListener('change',()=>{p.debugView=Number(debugView.value);updateUi();render()});
document.querySelectorAll('[data-preset]').forEach(btn=>btn.addEventListener('click',()=>{if(btn.dataset.preset==='legacyFixed')p={...APP_RAW,...LEGACY_FIXED,edgeMode:1,debugView:0};else p={...APP_RAW};updateUi();rebuildBackdrop();render()}));
moonBtn.addEventListener('click',()=>{blurMoonVisible=!blurMoonVisible;moonBtn.textContent=blurMoonVisible?'隐藏模糊层月亮':'显示模糊层月亮';moonBtn.classList.toggle('on',blurMoonVisible);rebuildBackdrop();render()});
window.addEventListener('resize',resize);scroll.addEventListener('scroll',render,{passive:true});window.addEventListener('beforeunload',()=>{if(customBgUrl)URL.revokeObjectURL(customBgUrl)});
try{initGl();buildControls();requestAnimationFrame(()=>{resize();scroll.scrollTop=500;render();window.__glassDebug={gl,program,getParams:()=>({...p}),render}})}catch(err){errorEl.textContent=String(err&&err.stack||err);console.error(err)}

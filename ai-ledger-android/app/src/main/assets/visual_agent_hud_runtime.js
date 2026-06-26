(() => {
  const app=document.getElementById('app');
  const cursor=document.getElementById('cursor');
  const bubbleTitle=document.getElementById('bubbleTitle');
  const thought=document.getElementById('thought');
  const confidence=document.getElementById('confidence');
  const coords=document.getElementById('coords');
  const actionSource=document.getElementById('actionSource');
  const topTitle=document.getElementById('topTitle');
  const topMeta=document.getElementById('topMeta');
  const debugStep=document.getElementById('debugStep');
  const debugPoint=document.getElementById('debugPoint');
  const debugLatency=document.getElementById('debugLatency');
  const shape=document.getElementById('mouseCursorShape');
  const cyanLayer=document.getElementById('cyanLayer');
  const whiteLayer=document.getElementById('whiteLayer');
  const pinkLayer=document.getElementById('pinkLayer');
  const outerRim=document.getElementById('outerRim');
  const innerRim=document.getElementById('innerRim');
  const glowBlurNode=document.getElementById('cursorGlowBlurNode');
  const glowMatrix=document.getElementById('cursorGlowMatrix');
  const cyanGradient=document.getElementById('mouseCursorCyan');
  const whiteGradient=document.getElementById('mouseCursorWhite');
  const pinkGradient=document.getElementById('mouseCursorPink');
  const innerGlow=document.getElementById('innerGlow');
  const innerBlurNode=document.getElementById('innerBlurNode');
  const root=document.documentElement;
  const phaseNames=[
    ['正在观察页面','Step 1 / 5','OBSERVE'],
    ['正在分析目标','Step 2 / 5','ANALYZE'],
    ['正在移动光标','Step 3 / 5','MOVE_POINTER'],
    ['正在执行点击','Step 4 / 5','TAP'],
    ['正在验证结果','Step 5 / 5','VERIFY']
  ];
  let lastClickRevision=-1;
  let phaseTimer=0;

  function number(value,fallback){
    const parsed=Number(value);
    return Number.isFinite(parsed)?parsed:fallback;
  }

  function point(parameters,index){
    return {
      x:number(parameters[`p${index}x`],0),
      y:number(parameters[`p${index}y`],0)
    };
  }

  function cursorPath(parameters){
    const points=Array.from({length:7},(_,index)=>point(parameters,index));
    const tension=number(parameters.tension,.91);
    const factor=tension/6;
    const fmt=value=>Number(value.toFixed(3));
    let path=`M ${fmt(points[0].x)} ${fmt(points[0].y)}`;
    for(let index=0;index<points.length;index+=1){
      const previous=points[(index-1+points.length)%points.length];
      const current=points[index];
      const next=points[(index+1)%points.length];
      const after=points[(index+2)%points.length];
      const c1x=current.x+(next.x-previous.x)*factor;
      const c1y=current.y+(next.y-previous.y)*factor;
      const c2x=next.x-(after.x-current.x)*factor;
      const c2y=next.y-(after.y-current.y)*factor;
      path+=` C ${fmt(c1x)} ${fmt(c1y)}, ${fmt(c2x)} ${fmt(c2y)}, ${fmt(next.x)} ${fmt(next.y)}`;
    }
    return `${path} Z`;
  }

  function setCss(name,value,unit=''){
    root.style.setProperty(name,`${number(value,0)}${unit}`);
  }

  function applyParameters(parameters){
    if(!parameters||typeof parameters!=='object')return;

    shape.setAttribute('d',cursorPath(parameters));
    setCss('--lab-cursor-size',parameters.size,'px');
    setCss('--lab-scale-x',parameters.scaleX);
    setCss('--lab-scale-y',parameters.scaleY);
    setCss('--lab-rotation',parameters.rotation,'deg');
    setCss('--lab-offset-x',parameters.offsetX,'px');
    setCss('--lab-offset-y',parameters.offsetY,'px');
    setCss('--hotspot-x',parameters.hotspotX,'px');
    setCss('--hotspot-y',parameters.hotspotY,'px');
    setCss('--lab-aura-size',parameters.auraSize,'px');
    setCss('--lab-aura-blur',parameters.auraBlur,'px');
    setCss('--lab-aura-opacity',parameters.auraOpacity);

    cyanLayer.setAttribute('opacity',number(parameters.cyanOpacity,.92));
    whiteLayer.setAttribute('opacity',number(parameters.whiteOpacity,.86));
    pinkLayer.setAttribute('opacity',number(parameters.pinkOpacity,.72));
    outerRim.setAttribute('stroke-width',number(parameters.outerRimWidth,1.02));
    innerRim.setAttribute('stroke-width',number(parameters.innerRimWidth,.42));
    outerRim.setAttribute('opacity',number(parameters.rimOpacity,.95));
    innerRim.setAttribute('opacity',number(parameters.rimOpacity,.95));
    glowBlurNode.setAttribute('stdDeviation',number(parameters.glowBlur,1.45));
    glowMatrix.setAttribute('values',`0 0 0 0 0.20 0 0 0 0 0.82 0 0 0 0 1 0 0 0 ${number(parameters.glowOpacity,.18)} 0`);

    cyanGradient.setAttribute('gradientTransform',`translate(${number(parameters.cyanX,33.7)} ${number(parameters.cyanY,16)}) rotate(33) scale(${Math.max(.01,number(parameters.cyanSizeX,23))} ${Math.max(.01,number(parameters.cyanSizeY,18))})`);
    whiteGradient.setAttribute('gradientTransform',`translate(${number(parameters.whiteX,29)} ${number(parameters.whiteY,28)}) rotate(32) scale(${Math.max(.01,number(parameters.whiteSizeX,18))} ${Math.max(.01,number(parameters.whiteSizeY,14))})`);
    pinkGradient.setAttribute('gradientTransform',`translate(${number(parameters.pinkX,30)} ${number(parameters.pinkY,50)}) rotate(-70) scale(${Math.max(.01,number(parameters.pinkSizeX,18))} ${Math.max(.01,number(parameters.pinkSizeY,18))})`);

    innerGlow.setAttribute('cx',number(parameters.innerGlowX,28.2));
    innerGlow.setAttribute('cy',number(parameters.innerGlowY,28.1));
    innerGlow.setAttribute('rx',Math.max(.01,number(parameters.innerGlowRx,11.8)));
    innerGlow.setAttribute('ry',Math.max(.01,number(parameters.innerGlowRy,8.2)));
    innerGlow.setAttribute('opacity',number(parameters.innerGlowOpacity,.09));
    innerBlurNode.setAttribute('stdDeviation',number(parameters.innerGlowBlur,2.5));

    setCss('--edge-inset',parameters.edgeInset,'px');
    setCss('--edge-radius',parameters.edgeRadius,'px');
    setCss('--edge-halo-width',parameters.edgeHaloWidth,'px');
    setCss('--edge-halo-blur',parameters.edgeHaloBlur,'px');
    setCss('--edge-halo-opacity',parameters.edgeHaloOpacity);
    setCss('--edge-cast-depth',parameters.edgeCastDepth,'px');
    setCss('--edge-cast-blur',parameters.edgeCastBlur,'px');
    setCss('--edge-cast-opacity',parameters.edgeCastOpacity);
    setCss('--edge-flow-speed',Math.max(.01,number(parameters.edgeFlowDuration,7.5)),'s');
    setCss('--edge-breath-speed',Math.max(.01,number(parameters.edgeBreathDuration,1.5)),'s');
    setCss('--edge-breath-strength',parameters.edgeBreathStrength);
  }

  function setPoint(xNorm,yNorm){
    const x=Math.max(0,Math.min(1,number(xNorm,0)))*innerWidth;
    const y=Math.max(0,Math.min(1,number(yNorm,0)))*innerHeight;
    root.style.setProperty('--cursor-x',x+'px');
    root.style.setProperty('--cursor-y',y+'px');
    const cx=Math.round(x),cy=Math.round(y);
    coords.textContent=`${cx}, ${cy}`;
    debugPoint.textContent=`screen_point: (${cx}, ${cy})`;
  }

  function setPhase(index){
    const i=Math.max(0,Math.min(4,number(index,0)));
    const n=phaseNames[i];
    topTitle.textContent=n[0];
    topMeta.textContent=n[1];
    debugStep.textContent=`step_id: 0${i+1} / ${n[2]}`;
    document.querySelectorAll('.phase').forEach((phase,k)=>{
      phase.classList.toggle('done',k<i);
      phase.classList.toggle('active',k===i);
    });
  }

  function clickPulse(){
    cursor.classList.remove('clicking');
    void cursor.offsetWidth;
    cursor.classList.add('clicking');
  }

  window.VisualHud={
    update(payload){
      const data=typeof payload==='string'?JSON.parse(payload):payload;
      clearTimeout(phaseTimer);
      applyParameters(data.parameters);
      app.classList.toggle('hud-live',!!data.visible);
      if(!data.visible)return;
      setPoint(data.xNorm,data.yNorm);
      setPhase(data.phase);
      if(data.title)topTitle.textContent=data.title;
      if(data.meta)topMeta.textContent=data.meta;
      bubbleTitle.textContent=data.bubbleTitle||data.currentAction||'正在执行视觉任务';
      thought.textContent=data.thought||data.result||'正在根据页面证据选择下一步操作。';
      confidence.textContent=data.confidence||'—';
      actionSource.textContent=data.actionSource||'视觉识别';
      debugLatency.textContent=data.debugLatency||'latency_total: —';
      if(number(data.autoClickAfterMs,0)>0){
        phaseTimer=setTimeout(()=>setPhase(3),number(data.autoClickAfterMs,0));
      }
      if(number(data.clickRevision,0)>lastClickRevision){
        lastClickRevision=number(data.clickRevision,0);
        if(lastClickRevision>0)clickPulse();
      }
    },
    hide(){
      clearTimeout(phaseTimer);
      app.classList.remove('hud-live');
    }
  };
})();

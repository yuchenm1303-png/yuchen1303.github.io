(() => {
  const app=document.getElementById('app');
  const cursor=document.getElementById('cursor');
  const bubble=document.getElementById('bubble');
  const bubbleContent=document.getElementById('bubbleContent');
  const bubbleTitle=document.getElementById('bubbleTitle');
  const thought=document.getElementById('thought');
  const confidence=document.getElementById('confidence');
  const coords=document.getElementById('coords');
  const actionSource=document.getElementById('actionSource');
  const topTitle=document.getElementById('topTitle');
  const topMeta=document.getElementById('topMeta');
  const timeline=document.getElementById('timeline');
  const hudTop=document.querySelector('.hud-top');
  const debugPanel=document.querySelector('.debug-panel');
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
  const captureSafeElements=[hudTop,cursor,bubble,timeline,debugPanel].filter(Boolean);
  const reduceMotion=window.matchMedia&&window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const phaseNames=[
    ['正在观察页面','Step 1 / 5','OBSERVE'],
    ['正在分析目标','Step 2 / 5','ANALYZE'],
    ['正在移动光标','Step 3 / 5','MOVE_POINTER'],
    ['正在执行点击','Step 4 / 5','TAP'],
    ['正在验证结果','Step 5 / 5','VERIFY']
  ];

  let lastClickRevision=-1;
  let phaseTimer=0;
  let contentRevision=0;
  let lastContentSignature='';
  let bubblePositionFrame=0;
  let currentPoint={x:innerWidth*.5,y:innerHeight*.5};
  let currentCursorSize=36.1;
  let currentScaleX=1;
  let currentScaleY=.95;
  let currentAuraSize=72;
  let currentBubbleScale=.65;

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

    currentCursorSize=Math.max(1,number(parameters.size,36.1));
    currentScaleX=number(parameters.scaleX,1);
    currentScaleY=number(parameters.scaleY,.95);
    currentAuraSize=Math.max(0,number(parameters.auraSize,72));
    currentBubbleScale=Math.max(.1,number(parameters.infoBubbleScale,.65));

    shape.setAttribute('d',cursorPath(parameters));
    setCss('--lab-cursor-size',currentCursorSize,'px');
    setCss('--lab-scale-x',currentScaleX);
    setCss('--lab-scale-y',currentScaleY);
    setCss('--lab-rotation',parameters.rotation,'deg');
    setCss('--lab-offset-x',parameters.offsetX,'px');
    setCss('--lab-offset-y',parameters.offsetY,'px');
    setCss('--hotspot-x',parameters.hotspotX,'px');
    setCss('--hotspot-y',parameters.hotspotY,'px');
    setCss('--lab-aura-size',currentAuraSize,'px');
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

    setCss('--info-bubble-width',parameters.infoBubbleWidth,'px');
    setCss('--info-bubble-scale',currentBubbleScale);

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
    scheduleBubblePosition();
  }

  function setPoint(xNorm,yNorm){
    const x=Math.max(0,Math.min(1,number(xNorm,0)))*innerWidth;
    const y=Math.max(0,Math.min(1,number(yNorm,0)))*innerHeight;
    currentPoint={x,y};
    root.style.setProperty('--cursor-x',x+'px');
    root.style.setProperty('--cursor-y',y+'px');
    const cx=Math.round(x),cy=Math.round(y);
    coords.textContent=`${cx}, ${cy}`;
    debugPoint.textContent=`screen_point: (${cx}, ${cy})`;
    scheduleBubblePosition();
  }

  function overlapArea(a,b){
    const width=Math.max(0,Math.min(a.right,b.right)-Math.max(a.left,b.left));
    const height=Math.max(0,Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top));
    return width*height;
  }

  function scheduleBubblePosition(){
    if(bubblePositionFrame)return;
    bubblePositionFrame=requestAnimationFrame(()=>{
      bubblePositionFrame=0;
      positionBubble();
    });
  }

  function positionBubble(){
    if(!bubble||!bubble.offsetWidth||!bubble.offsetHeight)return;

    const visualWidth=bubble.offsetWidth*currentBubbleScale;
    const visualHeight=bubble.offsetHeight*currentBubbleScale;
    const screenWidth=Math.max(1,innerWidth);
    const screenHeight=Math.max(1,innerHeight);
    const safeLeft=12;
    const safeRight=12;
    const safeTop=12;
    const safeBottom=12;
    const cursorRadius=Math.max(
      18,
      currentCursorSize*Math.max(Math.abs(currentScaleX),Math.abs(currentScaleY))*.62,
      currentAuraSize*.18
    );
    const gap=Math.max(10,currentCursorSize*.22);
    const x=currentPoint.x;
    const y=currentPoint.y;
    const cursorRect={
      left:x-cursorRadius,
      top:y-cursorRadius,
      right:x+cursorRadius,
      bottom:y+cursorRadius
    };
    const candidates=[
      {name:'right-bottom',x:x+cursorRadius+gap,y:y+gap,priority:0},
      {name:'left-bottom',x:x-cursorRadius-gap-visualWidth,y:y+gap,priority:1},
      {name:'right-top',x:x+cursorRadius+gap,y:y-gap-visualHeight,priority:2},
      {name:'left-top',x:x-cursorRadius-gap-visualWidth,y:y-gap-visualHeight,priority:3},
      {name:'bottom',x:x-visualWidth*.5,y:y+cursorRadius+gap,priority:4},
      {name:'top',x:x-visualWidth*.5,y:y-cursorRadius-gap-visualHeight,priority:5}
    ];

    const maxX=Math.max(safeLeft,screenWidth-safeRight-visualWidth);
    const maxY=Math.max(safeTop,screenHeight-safeBottom-visualHeight);
    let best=null;
    for(const candidate of candidates){
      const overflow=
        Math.max(0,safeLeft-candidate.x)+
        Math.max(0,candidate.x+visualWidth-(screenWidth-safeRight))+
        Math.max(0,safeTop-candidate.y)+
        Math.max(0,candidate.y+visualHeight-(screenHeight-safeBottom));
      const left=Math.min(maxX,Math.max(safeLeft,candidate.x));
      const top=Math.min(maxY,Math.max(safeTop,candidate.y));
      const rect={left,top,right:left+visualWidth,bottom:top+visualHeight};
      const overlap=overlapArea(rect,cursorRect);
      const centerDistance=Math.hypot(left+visualWidth*.5-x,top+visualHeight*.5-y);
      const score=overflow*900+overlap*120+centerDistance*.035+candidate.priority*2;
      if(!best||score<best.score)best={...candidate,left,top,score};
    }
    if(!best)return;

    const previousPlacement=bubble.dataset.placement||'';
    const placementChanged=previousPlacement&&previousPlacement!==best.name;
    if(placementChanged){
      bubble.style.transition='none';
    }
    bubble.style.left=`${best.left}px`;
    bubble.style.top=`${best.top}px`;
    bubble.dataset.placement=best.name;
    if(placementChanged){
      void bubble.offsetWidth;
      bubble.style.transition='';
      if(!reduceMotion&&bubble.animate){
        bubble.animate(
          [
            {opacity:.58,filter:'blur(1.4px) saturate(.9)'},
            {opacity:1,filter:'blur(0) saturate(1)'}
          ],
          {duration:180,easing:'cubic-bezier(.2,.8,.2,1)'}
        );
      }
    }
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

  function revealText(element,text,revision,speed,delayMs=0){
    const value=String(text||'');
    if(reduceMotion||value.length<2){
      element.textContent=value;
      scheduleBubblePosition();
      return;
    }
    const characters=Array.from(value);
    element.textContent='';
    let startedAt=0;
    let rendered=0;
    function frame(now){
      if(revision!==contentRevision)return;
      if(!startedAt)startedAt=now+delayMs;
      if(now<startedAt){
        requestAnimationFrame(frame);
        return;
      }
      const next=Math.min(characters.length,Math.floor((now-startedAt)/speed)+1);
      if(next!==rendered){
        rendered=next;
        element.textContent=characters.slice(0,rendered).join('');
        scheduleBubblePosition();
      }
      if(rendered<characters.length)requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
  }

  function updateBubbleContent(data){
    const titleText=data.bubbleTitle||data.currentAction||'正在执行视觉任务';
    const thoughtText=data.thought||data.result||'正在根据页面证据选择下一步操作。';
    const confidenceText=data.confidence||'—';
    const sourceText=data.actionSource||'视觉识别';
    const signature=JSON.stringify([titleText,thoughtText,confidenceText,sourceText]);
    if(signature===lastContentSignature){
      confidence.textContent=confidenceText;
      actionSource.textContent=sourceText;
      return;
    }

    lastContentSignature=signature;
    contentRevision+=1;
    const revision=contentRevision;
    confidence.textContent=confidenceText;
    actionSource.textContent=sourceText;

    if(!reduceMotion&&bubble.animate&&bubbleContent.animate){
      bubble.animate(
        [
          {opacity:.76,filter:'brightness(.92) saturate(.9)'},
          {opacity:1,filter:'brightness(1) saturate(1)'}
        ],
        {duration:300,easing:'cubic-bezier(.2,.8,.2,1)'}
      );
      bubbleContent.animate(
        [
          {opacity:.15,transform:'translateY(6px) scale(.985)',filter:'blur(2px)'},
          {opacity:1,transform:'translateY(0) scale(1)',filter:'blur(0)'}
        ],
        {duration:280,easing:'cubic-bezier(.16,.84,.24,1)'}
      );
    }

    revealText(bubbleTitle,titleText,revision,16,0);
    revealText(thought,thoughtText,revision,10,72);
    scheduleBubblePosition();
  }

  function clickPulse(){
    cursor.classList.remove('clicking');
    void cursor.offsetWidth;
    cursor.classList.add('clicking');
  }

  function setCaptureSafe(active){
    const enabled=!!active;
    app.classList.toggle('capture-safe',enabled);
    captureSafeElements.forEach(element=>{
      element.style.opacity=enabled?'0':'';
      element.style.visibility=enabled?'hidden':'';
    });
    if(enabled){
      clearTimeout(phaseTimer);
      cursor.classList.remove('clicking');
    }
  }

  if(window.ResizeObserver){
    new ResizeObserver(scheduleBubblePosition).observe(bubble);
  }
  window.addEventListener('resize',scheduleBubblePosition,{passive:true});

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
      updateBubbleContent(data);
      debugLatency.textContent=data.debugLatency||'latency_total: —';
      if(number(data.autoClickAfterMs,0)>0){
        phaseTimer=setTimeout(()=>{
          setPhase(3);
          clickPulse();
        },number(data.autoClickAfterMs,0));
      }
      const clickRevision=number(data.clickRevision,0);
      if(clickRevision>0&&clickRevision!==lastClickRevision){
        lastClickRevision=clickRevision;
        clickPulse();
      }
    },
    setCaptureSafe,
    hide(){
      clearTimeout(phaseTimer);
      setCaptureSafe(false);
      app.classList.remove('hud-live');
    }
  };
})();

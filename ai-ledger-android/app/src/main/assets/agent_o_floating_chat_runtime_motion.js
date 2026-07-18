/*
 * Agent O 原生生产态合成过渡控制器。
 *
 * 稳定珠态继续使用原 WebGL 光场，稳定展开态继续使用真实 glass-shell、backdrop-filter、
 * 边缘光和完整聊天 DOM。展开/收回期间由固定尺寸的轻量代理层只做 transform、opacity
 * 与代理自身圆角变化，真实复杂玻璃在动画首尾各切换一次，不再逐帧修改宽高和重绘聊天树。
 */
(function installAgentOCompositeTransition(){
  if(!nativeProduction||!window.GuiPlusFloatingChat)return;
  if(root.dataset.compositeController==='1')return;
  const proxy=root.querySelector('.composite-transition-shell');
  if(!proxy)return;

  root.dataset.compositeController='1';
  root.dataset.compositeActive='false';

  const baseFloatingChat=window.GuiPlusFloatingChat;
  const baseEnsureAnimationLoop=ensureAnimationLoop;
  const baseSuspend=baseFloatingChat.suspend;
  const baseResume=baseFloatingChat.resume;

  let compositeActive=false;
  let compositeTargetForm=0;
  let compositeAnimation=null;
  let proxyFadeAnimation=null;
  let transitionSerial=0;

  ensureAnimationLoop=function(){
    if(compositeActive)return;
    baseEnsureAnimationLoop();
  };

  const px=value=>Math.round(value*1000)/1000;
  const transformFor=(x,y,scaleX,scaleY)=>
    `translate(-50%, -50%) translate3d(${px(x)}px, ${px(y)}px, 0) scale3d(${px(scaleX)}, ${px(scaleY)}, 1)`;

  function cancelProxyAnimations(){
    if(compositeAnimation){
      compositeAnimation.onfinish=null;
      compositeAnimation.oncancel=null;
    }
    if(proxyFadeAnimation){
      proxyFadeAnimation.onfinish=null;
      proxyFadeAnimation.oncancel=null;
    }
    if(typeof proxy.getAnimations==='function'){
      proxy.getAnimations().forEach(animation=>animation.cancel());
    }else{
      if(compositeAnimation)compositeAnimation.cancel();
      if(proxyFadeAnimation)proxyFadeAnimation.cancel();
    }
    compositeAnimation=null;
    proxyFadeAnimation=null;
  }

  function commitCompositeAnimation(finalTransform,finalRadius){
    // 先把终点写入内联样式，再取消 fill 动画；旧版 WebView 无 commitStyles 也不会跳回起点。
    proxy.style.transform=finalTransform;
    proxy.style.borderRadius=finalRadius;
    const animation=compositeAnimation;
    compositeAnimation=null;
    if(!animation)return;
    animation.onfinish=null;
    animation.oncancel=null;
    animation.cancel();
  }

  function configureProxy(panel){
    proxy.hidden=false;
    proxy.style.width=`${panel.width}px`;
    proxy.style.height=`${panel.height}px`;
    proxy.style.top=`calc(50% + ${panel.anchorY}px)`;
    proxy.style.opacity='1';
  }

  function resetProxyStyles(){
    proxy.hidden=true;
    proxy.style.removeProperty('transform');
    proxy.style.removeProperty('border-radius');
    proxy.style.removeProperty('opacity');
    proxy.style.removeProperty('width');
    proxy.style.removeProperty('height');
    proxy.style.removeProperty('top');
  }

  function revealStableLayer(onComplete){
    root.dataset.compositeActive='false';
    if(typeof proxy.animate!=='function'){
      resetProxyStyles();
      if(onComplete)onComplete();
      return;
    }
    proxyFadeAnimation=proxy.animate(
      [{opacity:1},{opacity:0}],
      {duration:72,easing:'linear',fill:'forwards'}
    );
    proxyFadeAnimation.onfinish=()=>{
      const animation=proxyFadeAnimation;
      proxyFadeAnimation=null;
      if(animation){
        animation.onfinish=null;
        animation.oncancel=null;
        animation.cancel();
      }
      resetProxyStyles();
      if(onComplete)onComplete();
    };
    proxyFadeAnimation.oncancel=()=>{
      proxyFadeAnimation=null;
      resetProxyStyles();
      if(onComplete)onComplete();
    };
  }

  function snapStableState(targetForm){
    form=targetForm;
    geometryForm=targetForm;
    root.dataset.form=String(targetForm);
    root.dataset.flight='0';
    offsetX=0;
    offsetY=0;
    offsetDirty=true;
    applyOffset();
    snapMorphGeometry(targetForm);
    updateSelection();
    if(targetForm===2){
      root.dataset.content='2';
      root.dataset.orbOptics='0';
      opticalForm=1;
      opticalState=0;
      morphState='expanded';
    }else{
      root.dataset.content='0';
      root.dataset.orbOptics='1';
      opticalForm=0;
      opticalState=0;
      morphState='collapsed';
    }
    root.dataset.phase='idle';
  }

  function finishExpansion(serial){
    if(serial!==transitionSerial||compositeTargetForm!==2)return;
    const panel=updateDesiredGeometry(2,{});
    const finalTransform=transformFor(0,0,1,1);
    const finalRadius=`${panel.topRadius}px ${panel.topRadius}px ${panel.bottomRadius}px ${panel.bottomRadius}px`;
    commitCompositeAnimation(finalTransform,finalRadius);
    snapStableState(2);
    compositeActive=false;
    notifyMorphState('expanded',true);
    revealStableLayer();
  }

  function finishCollapse(serial){
    if(serial!==transitionSerial||compositeTargetForm!==0)return;
    const bead=updateDesiredGeometry(0,{});
    const panel=updateDesiredGeometry(2,{});
    const finalTransform=transformFor(0,-panel.anchorY,bead.width/panel.width,bead.height/panel.height);
    commitCompositeAnimation(finalTransform,'50%');
    snapStableState(0);
    compositeActive=false;
    notifyMorphState('collapsed',false);
    revealStableLayer(()=>baseEnsureAnimationLoop());
  }

  function beginExpansion(){
    const serial=++transitionSerial;
    compositeTargetForm=2;
    compositeActive=true;
    clearTransitionTimers();
    pauseAnimationLoop();
    cancelProxyAnimations();

    morphRevision+=1;
    morphStartedAt=performance.now();
    morphMilestone=0;
    morphState='expanding';
    root.dataset.phase='composite-expand';
    root.dataset.content='0';
    root.dataset.orbOptics='0';
    root.dataset.compositeActive='true';
    root.dataset.compositeDirection='expand';
    notifyMorphState('expanding',false);

    const bead=updateDesiredGeometry(0,{});
    const panel=updateDesiredGeometry(2,{});
    const startX=offsetX;
    const startY=offsetY-panel.anchorY;
    const beadScaleX=bead.width/panel.width;
    const beadScaleY=bead.height/panel.height;
    const startTransform=transformFor(startX,startY,beadScaleX,beadScaleY);
    const finalRadius=`${panel.topRadius}px ${panel.topRadius}px ${panel.bottomRadius}px ${panel.bottomRadius}px`;

    configureProxy(panel);
    proxy.style.transform=startTransform;
    proxy.style.borderRadius='50%';
    proxy.getBoundingClientRect();

    // 真实面板一次性准备到最终几何，但在代理层下完全隐藏，不参与中间帧布局。
    form=2;
    geometryForm=2;
    root.dataset.form='2';
    offsetX=0;
    offsetY=0;
    offsetDirty=true;
    applyOffset();
    snapMorphGeometry(2);
    updateSelection();
    opticalForm=1;
    opticalState=0;

    if(typeof proxy.animate!=='function'){
      finishExpansion(serial);
      return;
    }

    const duration=Math.max(300,Math.min(420,morphDuration+42));
    compositeAnimation=proxy.animate([
      {
        offset:0,
        transform:startTransform,
        borderRadius:'50%',
        easing:'cubic-bezier(.32,0,.55,1)'
      },
      {
        offset:.16,
        transform:transformFor(startX,startY-10,beadScaleX*.86,beadScaleY*.86),
        borderRadius:'50%',
        easing:'cubic-bezier(.18,.78,.22,1)'
      },
      {
        offset:.66,
        transform:transformFor(startX*.16,startY*.18-5,1.035,.97),
        borderRadius:'36px',
        easing:'cubic-bezier(.18,.82,.24,1)'
      },
      {
        offset:.84,
        transform:transformFor(0,-2,1.012,.99),
        borderRadius:`${panel.topRadius+2}px ${panel.topRadius+2}px ${panel.bottomRadius+2}px ${panel.bottomRadius+2}px`,
        easing:'cubic-bezier(.2,.75,.2,1)'
      },
      {
        offset:1,
        transform:transformFor(0,0,1,1),
        borderRadius:finalRadius
      }
    ],{
      duration,
      easing:'linear',
      fill:'forwards'
    });
    compositeAnimation.onfinish=()=>finishExpansion(serial);
    compositeAnimation.oncancel=()=>{compositeAnimation=null;};
  }

  function beginCollapse(){
    const serial=++transitionSerial;
    compositeTargetForm=0;
    compositeActive=true;
    clearTransitionTimers();
    pauseAnimationLoop();
    cancelProxyAnimations();

    morphRevision+=1;
    morphStartedAt=performance.now();
    morphMilestone=0;
    morphState='collapsing';
    root.dataset.phase='composite-collapse';
    root.dataset.content='0';
    root.dataset.orbOptics='0';
    root.dataset.compositeActive='true';
    root.dataset.compositeDirection='collapse';
    notifyMorphState('collapsing',false);

    const bead=updateDesiredGeometry(0,{});
    const panel=updateDesiredGeometry(2,{});
    const beadScaleX=bead.width/panel.width;
    const beadScaleY=bead.height/panel.height;
    const endX=0;
    const endY=-panel.anchorY;
    const finalPanelRadius=`${panel.topRadius}px ${panel.topRadius}px ${panel.bottomRadius}px ${panel.bottomRadius}px`;
    const endTransform=transformFor(endX,endY,beadScaleX,beadScaleY);

    configureProxy(panel);
    proxy.style.transform=transformFor(0,0,1,1);
    proxy.style.borderRadius=finalPanelRadius;
    proxy.getBoundingClientRect();

    // 真实玻璃一次性切到珠态并保持隐藏，代理层完成收拢后再无缝交还 WebGL 珠态。
    form=0;
    geometryForm=0;
    root.dataset.form='0';
    offsetX=0;
    offsetY=0;
    offsetDirty=true;
    applyOffset();
    snapMorphGeometry(0);
    updateSelection();
    opticalForm=0;
    opticalState=0;

    if(typeof proxy.animate!=='function'){
      finishCollapse(serial);
      return;
    }

    const duration=Math.max(280,Math.min(390,morphDuration+18));
    compositeAnimation=proxy.animate([
      {
        offset:0,
        transform:transformFor(0,0,1,1),
        borderRadius:finalPanelRadius,
        easing:'cubic-bezier(.32,0,.55,1)'
      },
      {
        offset:.18,
        transform:transformFor(0,-4,.985,.99),
        borderRadius:`${panel.topRadius+3}px ${panel.topRadius+3}px ${panel.bottomRadius+3}px ${panel.bottomRadius+3}px`,
        easing:'cubic-bezier(.35,0,.65,1)'
      },
      {
        offset:.72,
        transform:transformFor(endX*.72,endY*.70,beadScaleX*1.16,beadScaleY*1.16),
        borderRadius:'50%',
        easing:'cubic-bezier(.28,.02,.4,1)'
      },
      {
        offset:1,
        transform:endTransform,
        borderRadius:'50%'
      }
    ],{
      duration,
      easing:'linear',
      fill:'forwards'
    });
    compositeAnimation.onfinish=()=>finishCollapse(serial);
    compositeAnimation.oncancel=()=>{compositeAnimation=null;};
  }

  setForm=function(value){
    const targetForm=value===2?2:0;
    if(targetForm===2&&(morphState==='expanding'||morphState==='expanded'))return;
    if(targetForm===0&&(morphState==='collapsing'||morphState==='collapsed'))return;
    if(targetForm===2)beginExpansion();
    else beginCollapse();
  };

  function settleInterruptedTransition(){
    if(!compositeActive)return;
    const target=compositeTargetForm;
    ++transitionSerial;
    cancelProxyAnimations();
    snapStableState(target);
    compositeActive=false;
    root.dataset.compositeActive='false';
    resetProxyStyles();
    if(target===2)notifyMorphState('expanded',true);
    else{
      notifyMorphState('collapsed',false);
      baseEnsureAnimationLoop();
    }
  }

  window.GuiPlusFloatingChat=Object.freeze({
    ...baseFloatingChat,
    expand:()=>setForm(2),
    collapse:()=>setForm(0),
    suspend:()=>{
      baseSuspend();
      if(compositeAnimation&&compositeAnimation.playState==='running')compositeAnimation.pause();
      if(proxyFadeAnimation&&proxyFadeAnimation.playState==='running')proxyFadeAnimation.pause();
    },
    resume:()=>{
      baseResume();
      if(compositeAnimation&&compositeAnimation.playState==='paused')compositeAnimation.play();
      if(proxyFadeAnimation&&proxyFadeAnimation.playState==='paused')proxyFadeAnimation.play();
    }
  });

  document.addEventListener('visibilitychange',()=>{
    if(document.hidden)settleInterruptedTransition();
  },{passive:true});
})();

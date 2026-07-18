/*
 * Agent O 原生运动协调器。
 *
 * 保持 V8.4 固定 Surface、玻璃参数与弹簧手感不变，只负责：
 * - 珠态以当前位置为展开原点，靠边时仅移动到最近安全落点；
 * - 展开结束后把网页位移交还给 WindowManager，收回仍停在同一位置；
 * - 展开态拖动只保留原生侧一个逐帧合并器；
 * - 过渡和拖动期间暂停独立循环色相与边缘流，释放帧预算。
 */
(function installAgentOMotionCoordinator(){
  if(!nativeProduction||!window.GuiPlusFloatingChat)return;
  if(root.dataset.nativeMotionCoordinator==='1')return;
  root.dataset.nativeMotionCoordinator='1';

  const budgetStyle=document.createElement('style');
  budgetStyle.textContent=`
    #glass-blur-motion-lab-v2 .glass-shell,
    #glass-blur-motion-lab-v2 .bead-aura{
      will-change:width,height,border-radius,transform,opacity;
      backface-visibility:hidden;
    }
    #glass-blur-motion-lab-v2[data-motion-budget="paused"] .blur-stage,
    #glass-blur-motion-lab-v2[data-motion-budget="paused"] .bead-aura,
    #glass-blur-motion-lab-v2[data-motion-budget="paused"] .glass-shell::before{
      animation-play-state:paused!important;
    }
  `;
  document.head.appendChild(budgetStyle);

  const setMotionBudgetPaused=paused=>{
    root.dataset.motionBudget=paused?'paused':'active';
  };
  const dispatchNative=(action,payload={})=>{
    try{
      if(window.GuiPlusNative&&typeof window.GuiPlusNative.dispatch==='function'){
        window.GuiPlusNative.dispatch(action,JSON.stringify(payload));
        return true;
      }
    }catch(error){
      showChatToast('原生拖动桥暂时不可用');
    }
    return false;
  };
  const clamp=(value,min,max)=>Math.max(min,Math.min(max,value));

  /*
   * safeX/safeY 在原生侧把固定 viewport 居中到可用显示区。这里用 screen 与 viewport 的差值
   * 还原中心两侧的额外空间。纵向预留 96 CSS px 给状态栏、导航栏和厂商手势区；最终位置仍会
   * 再经过原生真实边界 coerce，因此不会越过系统安全区。
   */
  function expansionSafeRange(){
    const scale=Math.max(.1,agentONativeStageScale||1);
    const screenWidth=Math.max(window.innerWidth,Number(window.screen&&window.screen.availWidth)||Number(window.screen&&window.screen.width)||window.innerWidth);
    const screenHeight=Math.max(window.innerHeight,Number(window.screen&&window.screen.availHeight)||Number(window.screen&&window.screen.height)||window.innerHeight);
    const extraX=Math.max(0,(screenWidth-window.innerWidth-16)/(2*scale));
    const extraY=Math.max(0,(screenHeight-window.innerHeight-96)/(2*scale));
    return {
      minX:-60-extraX,
      maxX:60+extraX,
      minY:-71-extraY,
      maxY:59+extraY,
    };
  }

  const baseSetForm=setForm;
  const baseAdvanceMorphTimeline=advanceMorphTimeline;
  const baseFinishMorphTransitionIfReady=finishMorphTransitionIfReady;
  const baseFloatingChat=window.GuiPlusFloatingChat;
  const baseNativeOrbDown=baseFloatingChat.nativeOrbDown;
  const baseNativeOrbMove=baseFloatingChat.nativeOrbMove;
  const baseNativeOrbUp=baseFloatingChat.nativeOrbUp;
  const baseNativeOrbCancel=baseFloatingChat.nativeOrbCancel;

  let anchoredExpansion=false;
  let expansionTargetX=0;
  let expansionTargetY=0;
  let savedImpactDelay=P.impactDelay;

  setForm=function(value){
    const targetForm=value===2?2:0;
    if(targetForm===2&&(morphState==='expanding'||morphState==='expanded'))return;
    if(targetForm===0&&(morphState==='collapsing'||morphState==='collapsed'))return;
    if(targetForm===2){
      setMotionBudgetPaused(true);
      if(anchoredExpansion){
        savedImpactDelay=P.impactDelay;
        P.impactDelay=Math.min(P.impactDelay,P.launchDelay+16);
      }
      baseSetForm(value);
      return;
    }
    if(anchoredExpansion){
      P.impactDelay=savedImpactDelay;
      anchoredExpansion=false;
    }
    setMotionBudgetPaused(true);
    baseSetForm(value);
  };

  advanceMorphTimeline=function(now){
    if(morphState!=='expanding'||!anchoredExpansion){
      baseAdvanceMorphTimeline(now);
      return;
    }
    const launchX=P.launchX;
    const launchY=P.launchY;
    const impactX=P.impactX;
    P.launchX=launchX+expansionTargetX;
    P.launchY=launchY+expansionTargetY;
    P.impactX=impactX+expansionTargetX;
    baseAdvanceMorphTimeline(now);
    P.launchX=launchX;
    P.launchY=launchY;
    P.impactX=impactX;

    if(morphState==='expanding'&&morphMilestone>=3){
      poseTarget.x=expansionTargetX;
      poseTarget.y=expansionTargetY;
    }else if(morphState==='expanding'&&morphMilestone>=2){
      poseTarget.y=expansionTargetY;
    }
  };

  function handoffAnchoredPositionToNative(){
    const x=expansionTargetX;
    const y=expansionTargetY;
    P.impactDelay=savedImpactDelay;
    anchoredExpansion=false;
    if(Math.abs(x)<.01&&Math.abs(y)<.01){
      offsetX=0;
      offsetY=0;
      offsetDirty=true;
      applyOffset();
      setMotionBudgetPaused(false);
      return;
    }
    const scale=Math.max(.1,agentONativeStageScale||1);
    dispatchNative('window.dragStart',{expanded:true});
    dispatchNative('window.drag',{dx:x*scale,dy:y*scale,expanded:true});
    // dragEnd 在原生主线程立即提交 pending 坐标，避免再等待第二层 RAF。
    dispatchNative('window.dragEnd',{expanded:true});
    requestAnimationFrame(()=>requestAnimationFrame(()=>{
      offsetX=0;
      offsetY=0;
      offsetDirty=true;
      applyOffset();
      setMotionBudgetPaused(false);
    }));
  }

  finishMorphTransitionIfReady=function(now,perceptuallySettled){
    const stateBefore=morphState;
    let willFinishAnchoredExpansion=false;
    if(stateBefore==='expanding'&&anchoredExpansion){
      const elapsed=now-morphStartedAt;
      const earliest=Math.max(P.contentDelay,P.settleDelay)+45;
      const deadline=Math.max(720,morphDuration*2.45);
      willFinishAnchoredExpansion=elapsed>=earliest&&(perceptuallySettled||elapsed>=deadline);
      if(willFinishAnchoredExpansion){
        // 同一帧内把 pose 位移换成 offset，随后原 snap 清零 pose，屏幕位置不跳变。
        offsetX=expansionTargetX;
        offsetY=expansionTargetY;
        offsetDirty=true;
        applyOffset();
      }
    }

    baseFinishMorphTransitionIfReady(now,perceptuallySettled);

    if(willFinishAnchoredExpansion&&morphState==='expanded'){
      handoffAnchoredPositionToNative();
    }else if(stateBefore==='collapsing'&&morphState==='collapsed'){
      setMotionBudgetPaused(false);
    }else if(stateBefore==='expanding'&&morphState==='expanded'&&!anchoredExpansion){
      setMotionBudgetPaused(false);
    }
  };

  function coordinatedNativeOrbDown(){
    setMotionBudgetPaused(true);
    baseNativeOrbDown();
  }

  function coordinatedNativeOrbMove(velocity){
    baseNativeOrbMove(velocity);
  }

  function coordinatedNativeOrbUp(wasMoved){
    baseNativeOrbUp(wasMoved);
    if(wasMoved)setMotionBudgetPaused(false);
  }

  function coordinatedNativeOrbCancel(){
    baseNativeOrbCancel();
    setMotionBudgetPaused(false);
  }

  function anchoredNativeOrbTap(rebaseX,rebaseY){
    const x=Number(rebaseX);
    const y=Number(rebaseY);
    const originX=Number.isFinite(x)?x:0;
    const originY=Number.isFinite(y)?y:0;
    const safe=expansionSafeRange();
    expansionTargetX=clamp(originX,safe.minX,safe.maxX);
    expansionTargetY=clamp(originY,safe.minY,safe.maxY);
    anchoredExpansion=true;
    setMotionBudgetPaused(true);
    offsetX=originX;
    offsetY=originY;
    offsetDirty=true;
    applyOffset();
    nativeOrbUp(false);
  }

  window.GuiPlusFloatingChat=Object.freeze({
    ...baseFloatingChat,
    expand:()=>setForm(2),
    collapse:()=>setForm(0),
    nativeOrbDown:coordinatedNativeOrbDown,
    nativeOrbMove:coordinatedNativeOrbMove,
    nativeOrbUp:coordinatedNativeOrbUp,
    nativeOrbCancel:coordinatedNativeOrbCancel,
    nativeOrbTap:anchoredNativeOrbTap,
  });

  let panelDragActive=false;
  let panelDragPointer=-1;
  let panelDragStartX=0;
  let panelDragStartY=0;
  let panelDragLastX=0;
  let panelDragLastY=0;
  let panelDragToolbar=null;

  // document capture 先于旧 toolbar target listener，阻止旧的 JS RAF 链继续执行。
  document.addEventListener('pointerdown',event=>{
    const toolbar=event.target&&event.target.closest?event.target.closest('.chat-toolbar'):null;
    if(!toolbar||form!==2||morphState!=='expanded'||event.button!==0)return;
    if(event.target.closest('button,input,textarea,a,[role="button"]'))return;
    panelDragActive=true;
    panelDragPointer=event.pointerId;
    panelDragStartX=event.screenX;
    panelDragStartY=event.screenY;
    panelDragLastX=0;
    panelDragLastY=0;
    panelDragToolbar=toolbar;
    toolbar.dataset.dragging='true';
    if(toolbar.setPointerCapture)toolbar.setPointerCapture(panelDragPointer);
    setMotionBudgetPaused(true);
    dispatchNative('window.dragStart',{pointerId:panelDragPointer,expanded:true});
    event.preventDefault();
    event.stopPropagation();
  },true);

  document.addEventListener('pointermove',event=>{
    if(!panelDragActive||event.pointerId!==panelDragPointer)return;
    panelDragLastX=event.screenX-panelDragStartX;
    panelDragLastY=event.screenY-panelDragStartY;
    // 直接送最新坐标；原生 scheduleDragFrame 负责每显示帧最多一次 WindowManager 提交。
    dispatchNative('window.drag',{dx:panelDragLastX,dy:panelDragLastY,expanded:true});
    event.preventDefault();
    event.stopPropagation();
  },true);

  const finishPanelDrag=event=>{
    if(!panelDragActive||event.pointerId!==panelDragPointer)return;
    dispatchNative('window.drag',{dx:panelDragLastX,dy:panelDragLastY,expanded:true});
    dispatchNative('window.dragEnd',{expanded:true});
    if(panelDragToolbar&&panelDragToolbar.hasPointerCapture&&panelDragToolbar.hasPointerCapture(panelDragPointer)){
      panelDragToolbar.releasePointerCapture(panelDragPointer);
    }
    if(panelDragToolbar)panelDragToolbar.dataset.dragging='false';
    panelDragActive=false;
    panelDragPointer=-1;
    panelDragToolbar=null;
    setMotionBudgetPaused(false);
    event.preventDefault();
    event.stopPropagation();
  };
  document.addEventListener('pointerup',finishPanelDrag,true);
  document.addEventListener('pointercancel',finishPanelDrag,true);
})();

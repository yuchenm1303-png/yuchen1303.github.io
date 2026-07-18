/*
 * Agent O Android 生产控制器。
 *
 * 生产态只保留一个真实 glass-shell：尺寸在形态切换点修改一次，运动帧全部交给
 * motion-shell 的 transform 合成。稳定珠态继续绘制原 WebGL，稳定展开态继续使用完整玻璃。
 */
window.__agentONativeWindowDrag=Boolean(
  window.GuiPlusNative&&
  window.GuiPlusNative.usesNativeWindowDrag&&
  window.GuiPlusNative.usesNativeWindowDrag()
);

let agentONativeStageScale=-1;
let agentONativeStageResizeFrame=0;

function updateAgentONativeStageScale(){
  agentONativeStageResizeFrame=0;
  if(!nativeProduction)return;
  const scale=Math.min(window.innerWidth/620,window.innerHeight/490,1);
  const safeScale=Math.max(.1,scale);
  if(Math.abs(safeScale-agentONativeStageScale)<.0005)return;
  agentONativeStageScale=safeScale;
  root.style.setProperty('--native-stage-scale',String(safeScale));
  root.style.setProperty('--native-stage-offset-y',`${30*safeScale}px`);
  opticalSurfaceDirty=true;
  ensureAnimationLoop();
}

function scheduleAgentONativeStageScale(){
  if(agentONativeStageResizeFrame)return;
  agentONativeStageResizeFrame=requestAnimationFrame(updateAgentONativeStageScale);
}

updateAgentONativeStageScale();
window.addEventListener('resize',scheduleAgentONativeStageScale,{passive:true});

if(nativeProduction){
  const agentOMotionShell=root.querySelector('.motion-shell');
  const agentOIdentity='translate3d(0px,0px,0) scale3d(1,1,1)';
  const agentORetreatY=-10;
  const agentOPreScale=.78;
  const agentOState={
    token:0,
    animation:null,
    preparedX:0,
    preparedY:0,
    suspended:false,
  };

  function agentOTransform(x,y,scaleX=1,scaleY=scaleX,skew=0){
    return `translate3d(${x.toFixed(2)}px,${y.toFixed(2)}px,0) skewX(${skew.toFixed(2)}deg) scale3d(${scaleX.toFixed(5)},${scaleY.toFixed(5)},1)`;
  }

  function runCatchingCancel(animation){
    try{animation.cancel();}catch(error){/* 已结束的动画无需处理。 */}
  }

  function agentOCancelMotion(){
    const animation=agentOState.animation;
    agentOState.animation=null;
    if(animation)runCatchingCancel(animation);
  }

  function agentOSetMotionTransform(transform){
    agentOMotionShell.style.transform=transform;
  }

  function agentOAnimate(keyframes,options,token){
    agentOCancelMotion();
    const finalTransform=keyframes[keyframes.length-1].transform;
    if(typeof agentOMotionShell.animate!=='function'){
      agentOSetMotionTransform(finalTransform);
      return new Promise(resolve=>setTimeout(()=>resolve(token===agentOState.token),Number(options.duration)||0));
    }
    return new Promise(resolve=>{
      const animation=agentOMotionShell.animate(keyframes,{...options,fill:'forwards'});
      agentOState.animation=animation;
      if(agentOState.suspended)animation.pause();
      animation.onfinish=()=>{
        animation.oncancel=null;
        if(agentOState.animation===animation)agentOState.animation=null;
        agentOSetMotionTransform(finalTransform);
        runCatchingCancel(animation);
        resolve(token===agentOState.token);
      };
      animation.oncancel=()=>resolve(false);
    });
  }

  function agentOResetPhysics(){
    shellScale=1;
    shellScaleVelocity=0;
    targetScale=1;
    Object.assign(pose,{x:0,y:0,skew:0,stretchX:1,stretchY:1});
    Object.assign(poseTarget,{x:0,y:0,skew:0,stretchX:1,stretchY:1});
    Object.assign(poseVelocity,{x:0,y:0,skew:0,stretchX:0,stretchY:0});
  }

  function agentOSetGeometry(targetForm){
    const target=updateDesiredGeometry(targetForm);
    geometryForm=targetForm;
    for(const key of geometryKeys){
      geometry[key]=target[key];
      velocity[key]=0;
    }
    agentOResetPhysics();
    const widthPx=`${Math.max(1,target.width)}px`;
    const heightPx=`${Math.max(1,target.height)}px`;
    const radiusPx=`${Math.max(0,target.topRadius)}px ${Math.max(0,target.topRadius)}px ${Math.max(0,target.bottomRadius)}px ${Math.max(0,target.bottomRadius)}px`;
    shell.style.width=widthPx;
    shell.style.height=heightPx;
    shell.style.borderRadius=radiusPx;
    if(beadAura){
      beadAura.style.width=widthPx;
      beadAura.style.height=heightPx;
      beadAura.style.borderRadius=radiusPx;
    }
    lastWidthPx=widthPx;
    lastHeightPx=heightPx;
    lastRadiusPx=radiusPx;
    setCachedRootVariable('--anchor-y',`${target.anchorY}px`);
    setCachedRootVariable('--bubble-breath','1');
    return target;
  }

  function agentOClearOptical(){
    opticalForm=1;
    opticalState=0;
    if(gl&&opticalWasVisible){
      gl.clear(gl.COLOR_BUFFER_BIT);
      opticalWasVisible=false;
    }
  }

  function agentOFinishExpanded(){
    form=2;
    geometryForm=2;
    morphState='expanded';
    root.dataset.form='2';
    root.dataset.content='2';
    root.dataset.phase='idle';
    root.dataset.flight='0';
    root.dataset.orbOptics='0';
    root.dataset.transitioning='false';
    agentOSetMotionTransform(agentOIdentity);
    updateSelection();
    notifyMorphState('expanded',true);
  }

  function agentOFinishCollapsed(){
    form=0;
    geometryForm=0;
    morphState='collapsed';
    opticalForm=0;
    opticalState=0;
    root.dataset.form='0';
    root.dataset.content='0';
    root.dataset.phase='idle';
    root.dataset.flight='0';
    root.dataset.orbOptics='1';
    root.dataset.transitioning='false';
    agentOSetMotionTransform(agentOIdentity);
    updateSelection();
    notifyMorphState('collapsed',false);
    ensureAnimationLoop();
  }

  async function agentORunExpand(){
    if(morphState==='expanding'||morphState==='expanded')return;
    const token=++agentOState.token;
    morphRevision+=1;
    morphState='expanding';
    root.dataset.content='0';
    root.dataset.transitioning='true';
    root.dataset.phase='shrink';
    root.dataset.flight='0';
    root.dataset.orbOptics='1';
    notifyMorphState('expanding',false);

    const rebaseX=agentOState.preparedX;
    const rebaseY=agentOState.preparedY;
    agentOState.preparedX=0;
    agentOState.preparedY=0;
    const orbStart=agentOTransform(rebaseX,rebaseY,1,1);
    const orbRetreat=agentOTransform(rebaseX,rebaseY+agentORetreatY,agentOPreScale,agentOPreScale);
    agentOSetMotionTransform(orbStart);
    ensureAnimationLoop();
    const shrunk=await agentOAnimate([
      {transform:orbStart,offset:0},
      {transform:orbRetreat,offset:1},
    ],{duration:78,easing:'cubic-bezier(.42,0,.58,1)'},token);
    if(!shrunk)return;

    pauseAnimationLoop();
    root.dataset.orbOptics='0';
    agentOClearOptical();
    const panel=agentOSetGeometry(2);
    root.dataset.form='2';
    root.dataset.phase='flight';
    const scaleX=(ORB_MAX*agentOPreScale)/Math.max(1,panel.width);
    const scaleY=(ORB_MAX*agentOPreScale)/Math.max(1,panel.height);
    const startY=rebaseY+agentORetreatY-panel.anchorY;
    const panelStart=agentOTransform(rebaseX,startY,scaleX,scaleY);
    const panelOvershoot=agentOTransform(0,-3,1.018,.986);
    agentOSetMotionTransform(panelStart);
    void agentOMotionShell.offsetWidth;
    notifyMorphState('expanding',true);

    const expanded=await agentOAnimate([
      {transform:panelStart,offset:0},
      {transform:agentOTransform(rebaseX*.28,startY*.22,.66,.70),offset:.46},
      {transform:panelOvershoot,offset:.84},
      {transform:agentOIdentity,offset:1},
    ],{duration:258,easing:'cubic-bezier(.18,.72,.2,1)'},token);
    if(!expanded)return;
    agentOFinishExpanded();
  }

  async function agentORunCollapse(){
    if(morphState==='collapsing'||morphState==='collapsed')return;
    const token=++agentOState.token;
    morphRevision+=1;
    morphState='collapsing';
    root.dataset.content='0';
    root.dataset.transitioning='true';
    root.dataset.phase='settle';
    notifyMorphState('collapsing',false);

    const panel=updateDesiredGeometry(2);
    const scaleX=(ORB_MAX*agentOPreScale)/Math.max(1,panel.width);
    const scaleY=(ORB_MAX*agentOPreScale)/Math.max(1,panel.height);
    const panelEnd=agentOTransform(0,agentORetreatY-panel.anchorY,scaleX,scaleY);
    const collapsedPanel=await agentOAnimate([
      {transform:agentOIdentity,offset:0},
      {transform:agentOTransform(0,3,.985,1.012),offset:.16},
      {transform:panelEnd,offset:1},
    ],{duration:222,easing:'cubic-bezier(.42,0,.72,.28)'},token);
    if(!collapsedPanel)return;

    agentOSetGeometry(0);
    root.dataset.form='0';
    root.dataset.orbOptics='1';
    opticalForm=0;
    opticalState=0;
    const orbStart=agentOTransform(0,agentORetreatY,agentOPreScale,agentOPreScale);
    agentOSetMotionTransform(orbStart);
    void agentOMotionShell.offsetWidth;
    ensureAnimationLoop();
    const restored=await agentOAnimate([
      {transform:orbStart,offset:0},
      {transform:agentOTransform(0,1,1.025,1.025),offset:.72},
      {transform:agentOIdentity,offset:1},
    ],{duration:104,easing:'cubic-bezier(.2,.8,.2,1)'},token);
    if(!restored)return;
    agentOFinishCollapsed();
  }

  function agentOSnapForOppositeTransition(targetForm){
    agentOCancelMotion();
    if(targetForm===0){
      agentOSetGeometry(2);
      root.dataset.form='2';
      agentOSetMotionTransform(agentOIdentity);
      morphState='expanded';
      form=2;
    }else{
      agentOSetGeometry(0);
      root.dataset.form='0';
      root.dataset.orbOptics='1';
      agentOSetMotionTransform(agentOIdentity);
      morphState='collapsed';
      form=0;
    }
  }

  setForm=function(value){
    const targetForm=value===2?2:0;
    if(targetForm===2){
      if(morphState==='collapsing')agentOSnapForOppositeTransition(2);
      void agentORunExpand();
    }else{
      if(morphState==='expanding')agentOSnapForOppositeTransition(0);
      void agentORunCollapse();
    }
  };

  advanceMorphTimeline=function(){};
  finishMorphTransitionIfReady=function(){};
  snapMorphGeometry=agentOSetGeometry;

  pauseAnimationLoop();
  renderGeometry=function(now){
    animationFrameId=0;
    const delta=Math.min(.05,(now-previousFrame)/1000||.016);
    previousFrame=now;
    const orbVisible=root.dataset.form==='0'&&root.dataset.orbOptics==='1';
    if(!orbVisible||!pageActive||!stageInView)return;
    const livingWave=
      Math.sin(now*.00069)*.58+
      Math.sin(now*.00031+1.7)*.27+
      Math.sin(now*.00017+4.2)*.15;
    const livingAmplitude=.002+.00886*Math.min(2,O.idleBreath/54);
    setCachedRootVariable('--bubble-breath',String(1+livingWave*livingAmplitude));
    drawOptical(now,delta);
    animationFrameId=requestAnimationFrame(renderGeometry);
  };

  /* 生产态只经过唯一原生桥。 */
  postChatAction=function(action,payload={}){
    let delivered=false;
    try{
      if(chatBridgeAdapter){
        const envelope={source:CHAT_BRIDGE_SOURCE,action,payload};
        if(typeof chatBridgeAdapter==='function')chatBridgeAdapter(envelope);
        else if(typeof chatBridgeAdapter.postMessage==='function')chatBridgeAdapter.postMessage(envelope);
        else if(typeof chatBridgeAdapter.dispatch==='function')chatBridgeAdapter.dispatch(envelope);
        delivered=true;
      }else if(window.GuiPlusNative&&typeof window.GuiPlusNative.dispatch==='function'){
        window.GuiPlusNative.dispatch(action,JSON.stringify(payload));
        delivered=true;
      }else if(window.GuiPlusNative&&typeof window.GuiPlusNative.postMessage==='function'){
        window.GuiPlusNative.postMessage(JSON.stringify({source:CHAT_BRIDGE_SOURCE,action,payload}));
        delivered=true;
      }
    }catch(error){
      showChatToast('原生桥暂时不可用');
    }
    if(delivered!==chatState.bridgeConnected){chatState.bridgeConnected=delivered;renderBridgeStatus();}
    return delivered;
  };

  function agentONativeOrbDown(){
    if(morphState!=='collapsed')return;
    dragging=true;
    root.dataset.dragging='true';
    root.dataset.phase='drag';
    agentOCancelMotion();
    agentOSetMotionTransform(agentOTransform(0,0,.965,.965));
    ensureAnimationLoop();
  }

  function agentONativeOrbMove(velocity){
    if(!dragging||morphState!=='collapsed')return;
    const safeVelocity=Number.isFinite(Number(velocity))?Number(velocity):0;
    const skew=Math.max(-3.2,Math.min(3.2,safeVelocity*2.4));
    const stretch=Math.min(.026,Math.abs(safeVelocity)*.018);
    agentOSetMotionTransform(agentOTransform(0,0,1+stretch,1-stretch,skew));
  }

  function agentORestoreOrbTransform(){
    const from=agentOMotionShell.style.transform||agentOIdentity;
    const token=++agentOState.token;
    void agentOAnimate([
      {transform:from,offset:0},
      {transform:agentOIdentity,offset:1},
    ],{duration:118,easing:'cubic-bezier(.2,.8,.2,1)'},token);
  }

  function agentONativeOrbUp(wasMoved){
    dragging=false;
    root.dataset.dragging='false';
    root.dataset.phase='idle';
    if(wasMoved)agentORestoreOrbTransform();
    else void agentORunExpand();
  }

  function agentONativeOrbCancel(){
    dragging=false;
    root.dataset.dragging='false';
    root.dataset.phase='idle';
    agentORestoreOrbTransform();
  }

  function agentONativePrepareExpand(rebaseX,rebaseY){
    if(morphState!=='collapsed')return false;
    const x=Number(rebaseX);
    const y=Number(rebaseY);
    agentOState.preparedX=Number.isFinite(x)?x:0;
    agentOState.preparedY=Number.isFinite(y)?y:0;
    agentOSetMotionTransform(agentOTransform(agentOState.preparedX,agentOState.preparedY,1,1));
    return true;
  }

  function agentONativeCommitExpand(){
    if(morphState!=='collapsed')return false;
    void agentORunExpand();
    return true;
  }

  function agentONativeOrbTap(rebaseX,rebaseY){
    agentONativePrepareExpand(rebaseX,rebaseY);
    return agentONativeCommitExpand();
  }

  const agentOBaseFloatingChat=window.GuiPlusFloatingChat;
  window.GuiPlusFloatingChat=Object.freeze({
    ...agentOBaseFloatingChat,
    expand:()=>setForm(2),
    collapse:()=>setForm(0),
    nativeOrbDown:agentONativeOrbDown,
    nativeOrbMove:agentONativeOrbMove,
    nativeOrbUp:agentONativeOrbUp,
    nativeOrbCancel:agentONativeOrbCancel,
    nativeOrbTap:agentONativeOrbTap,
    nativePrepareExpand:agentONativePrepareExpand,
    nativeCommitExpand:agentONativeCommitExpand,
    suspend:()=>{
      pageActive=false;
      pauseAnimationLoop();
      agentOState.suspended=true;
      if(agentOState.animation)agentOState.animation.pause();
      if(agentOTailFrame){cancelAnimationFrame(agentOTailFrame);agentOTailFrame=0;}
    },
    resume:()=>{
      pageActive=!document.hidden;
      agentOState.suspended=false;
      if(agentOState.animation)agentOState.animation.play();
      if(pageActive)ensureAnimationLoop();
    },
  });

  document.addEventListener('visibilitychange',()=>{
    agentOState.suspended=document.hidden;
    if(agentOState.animation){
      if(document.hidden)agentOState.animation.pause();
      else agentOState.animation.play();
    }
  },{passive:true});

  agentOSetGeometry(0);
  root.dataset.form='0';
  root.dataset.content='0';
  root.dataset.orbOptics='1';
  root.dataset.transitioning='false';
  agentOSetMotionTransform(agentOIdentity);
  ensureAnimationLoop();
}
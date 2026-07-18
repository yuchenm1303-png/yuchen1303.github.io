/*
 * V8.4 Android 生产接入层。
 *
 * 固定 620×490 逻辑窗口与原始 560×720 舞台，展开/折叠不修改 viewport，避免 WebGL
 * Surface 重建。生产态只保留原生桥所需路径，并在稳定珠态跳过已经收敛的弹簧与几何写入；
 * WebGL 光场、呼吸、色相和全部视觉参数保持原值。
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

/* 继续直接使用 V8.4 原始几何，不改变面板、圆角、锚点和弹簧路径。 */
updateDesiredGeometry=function(value,target=geometryTarget){
  const bead=nativeProduction
    ? Math.min(ORB_MAX,Math.max(1,stageSize.width-18),Math.max(1,stageSize.height-18))
    : Math.min(190,stageSize.width*.46,stageSize.height*.38);
  const inputHeight=P.capsuleHeight;
  const inputWidth=Math.min(P.capsuleWidth,stageSize.width-34);
  const panelWidth=Math.min(500,stageSize.width-28);
  const panelHeight=Math.min(P.panelHeight,stageSize.height-30);
  if(value===0){
    target.width=bead;target.height=bead;target.topRadius=bead*.5;target.bottomRadius=bead*.5;target.anchorY=0;
  }else if(value===1){
    target.width=inputWidth;target.height=inputHeight;target.topRadius=inputHeight*.5;target.bottomRadius=inputHeight*.5;target.anchorY=0;
  }else{
    target.width=panelWidth;target.height=panelHeight;
    target.topRadius=P.panelTopRadius;target.bottomRadius=P.panelBottomRadius;
    target.anchorY=-(panelHeight-inputHeight)*.5;
  }
  return target;
};

const agentOPx=value=>`${Math.round(value*10)/10}px`;
const agentODeg=value=>`${Math.round(value*100)/100}deg`;
const agentONumber=value=>String(Math.round(value*10000)/10000);

/*
 * 稳定珠态仍逐帧绘制完全相同的 WebGL 活体光场，但不再重复积分已经收敛的 11 个弹簧量。
 * 过渡态使用最多三个固定物理子步追赶真实帧间隔，并把不可见的亚像素抖动量化到 0.1px，
 * 避免掉帧后弹簧变慢和同一视觉像素被反复触发布局、重绘。
 */
renderGeometry=function(now){
  animationFrameId=0;
  const elapsed=Math.min(.05,(now-previousFrame)/1000||.016);
  previousFrame=now;
  advanceMorphTimeline(now);

  const target=updateDesiredGeometry(geometryForm);
  const settledBefore=geometryPerceptuallySettled(target)&&
    posePerceptuallySettled()&&
    Math.abs(shellScale-targetScale)<.0005&&Math.abs(shellScaleVelocity)<.004;
  const stableCollapsed=morphState==='collapsed'&&settledBefore;

  if(!stableCollapsed){
    const speed=360/Math.max(160,morphDuration);
    const collapsingToOrb=morphState==='collapsing';
    const geometrySpeed=speed*(collapsingToOrb?.90:1);
    const collapseDamping=collapsingToOrb?.26:0;
    const stepCount=Math.max(1,Math.min(3,Math.ceil(elapsed/.016667)));
    const delta=elapsed/stepCount;
    for(let step=0;step<stepCount;step+=1){
      springProperty(geometry,velocity,'width',target.width,P.widthFrequency*geometrySpeed,P.widthDamping+collapseDamping,delta);
      springProperty(geometry,velocity,'height',target.height,P.heightFrequency*geometrySpeed,P.heightDamping+collapseDamping,delta);
      springProperty(geometry,velocity,'topRadius',target.topRadius,P.topRadiusFrequency*geometrySpeed,P.topRadiusDamping+collapseDamping,delta);
      springProperty(geometry,velocity,'bottomRadius',target.bottomRadius,P.bottomRadiusFrequency*geometrySpeed,P.bottomRadiusDamping+collapseDamping,delta);
      springProperty(geometry,velocity,'anchorY',target.anchorY,P.anchorFrequency*geometrySpeed,P.anchorDamping+collapseDamping,delta);
      shellScaleVelocity+=(P.scaleFrequency*P.scaleFrequency*speed*speed*(targetScale-shellScale)-2*P.scaleDamping*P.scaleFrequency*speed*shellScaleVelocity)*delta;
      shellScale+=shellScaleVelocity*delta;
      for(const key of poseKeys){
        springProperty(pose,poseVelocity,key,poseTarget[key],P.poseFrequency*speed,P.poseDamping,delta);
      }
    }

    const widthPx=agentOPx(Math.max(1,geometry.width));
    const heightPx=agentOPx(Math.max(1,geometry.height));
    const topRadius=agentOPx(Math.max(0,geometry.topRadius));
    const bottomRadius=agentOPx(Math.max(0,geometry.bottomRadius));
    const radiusPx=`${topRadius} ${topRadius} ${bottomRadius} ${bottomRadius}`;
    if(widthPx!==lastWidthPx){shell.style.width=widthPx;if(beadAura)beadAura.style.width=widthPx;lastWidthPx=widthPx;}
    if(heightPx!==lastHeightPx){shell.style.height=heightPx;if(beadAura)beadAura.style.height=heightPx;lastHeightPx=heightPx;}
    if(radiusPx!==lastRadiusPx){shell.style.borderRadius=radiusPx;if(beadAura)beadAura.style.borderRadius=radiusPx;lastRadiusPx=radiusPx;}
  }

  const livingWave=
    Math.sin(now*.00069)*.58+
    Math.sin(now*.00031+1.7)*.27+
    Math.sin(now*.00017+4.2)*.15;
  const livingAmplitude=.002+.00886*Math.min(2,O.idleBreath/54);
  const bubbleBreath=geometryForm===0?1+livingWave*livingAmplitude:1;
  applyOffset();
  setCachedRootVariable('--anchor-y',agentOPx(geometry.anchorY));
  setCachedRootVariable('--shell-scale',agentONumber(shellScale));
  setCachedRootVariable('--bubble-breath',agentONumber(bubbleBreath));
  setCachedRootVariable('--choreo-x',agentOPx(pose.x));
  setCachedRootVariable('--choreo-y',agentOPx(pose.y));
  setCachedRootVariable('--shell-skew',agentODeg(pose.skew));
  setCachedRootVariable('--stretch-x',agentONumber(pose.stretchX));
  setCachedRootVariable('--stretch-y',agentONumber(pose.stretchY));
  drawOptical(now,elapsed);

  const geometrySettled=stableCollapsed||geometryPerceptuallySettled(target);
  const poseSettled=stableCollapsed||posePerceptuallySettled();
  const scaleSettled=stableCollapsed||Math.abs(shellScale-targetScale)<.0005&&Math.abs(shellScaleVelocity)<.004;
  finishMorphTransitionIfReady(now,geometrySettled&&poseSettled&&scaleSettled);

  const stableExpanded=morphState==='expanded'&&geometrySettled&&poseSettled&&scaleSettled&&opticalForm===1&&opticalState===0;
  if(!stableExpanded&&pageActive&&stageInView)animationFrameId=requestAnimationFrame(renderGeometry);
};

/* Android 生产态不广播 iframe / CustomEvent 预览副本，用户意图只经过唯一原生桥。 */
if(nativeProduction){
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
}

updateAgentONativeStageScale();
window.addEventListener('resize',scheduleAgentONativeStageScale,{passive:true});
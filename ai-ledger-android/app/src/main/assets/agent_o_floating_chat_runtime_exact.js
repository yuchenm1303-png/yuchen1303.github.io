/*
 * V8.4 原版接入适配层：Android 只提供紧边界 620×490 窗口，网页仍使用原始 560×720
 * 设计舞台和 500×360 面板。舞台只整体等比缩放，不修改任何内部视觉参数。
 */
window.__agentONativeWindowDrag=Boolean(
  window.GuiPlusNative&&
  window.GuiPlusNative.usesNativeWindowDrag&&
  window.GuiPlusNative.usesNativeWindowDrag()
);

function updateAgentONativeStageScale(){
  if(!nativeProduction)return;
  const scale=Math.min(window.innerWidth/620,window.innerHeight/490,1);
  const safeScale=Math.max(.1,scale);
  root.style.setProperty('--native-stage-scale',String(safeScale));
  root.style.setProperty('--native-stage-offset-y',`${30*safeScale}px`);
}

/*
 * 继续直接使用 V8.4 原始几何：面板 500×360、圆角、负锚点与弹簧路径均不改。
 */
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

updateAgentONativeStageScale();
window.addEventListener('resize',updateAgentONativeStageScale,{passive:true});

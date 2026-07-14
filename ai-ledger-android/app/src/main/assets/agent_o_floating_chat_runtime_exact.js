/*
 * V8.4 原版接入适配层：只处理 Android 舞台缩放与宿主能力，不修改面板视觉参数。
 */
window.__agentONativeWindowDrag=Boolean(
  window.GuiPlusNative&&
  window.GuiPlusNative.usesNativeWindowDrag&&
  window.GuiPlusNative.usesNativeWindowDrag()
);

function updateAgentONativeStageScale(){
  if(!nativeProduction)return;
  const scale=Math.min(window.innerWidth/560,window.innerHeight/720,1);
  root.style.setProperty('--native-stage-scale',String(Math.max(.1,scale)));
}

/*
 * 珠态继续使用紧尺寸窗口；展开态严格恢复原网页 500×360 面板、圆角和负锚点。
 * 原生窗口使用 560×720 同比例安全区，因此不再触发手机窄屏的高瘦重排。
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

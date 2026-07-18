/*
 * Agent O 运动帧预算协调器。
 *
 * 原地展开、安全落点和单帧拖动由 AgentOFloatingChatHost 与现有 runtime_5 共同负责。
 * 本模块不改变任何窗口坐标、玻璃参数、WebGL 分辨率或弹簧路径，只在用户正在拖动，
 * 或展开/收回的几百毫秒内暂停独立循环的色相与边缘流，把合成预算让给主运动。
 */
(function installAgentOMotionBudgetCoordinator(){
  if(!nativeProduction||!window.GuiPlusFloatingChat)return;
  if(root.dataset.nativeMotionCoordinator==='1')return;
  root.dataset.nativeMotionCoordinator='1';
  root.dataset.motionBudget='active';

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

  let pauseDepth=0;
  const pauseMotionBudget=()=>{
    pauseDepth+=1;
    if(pauseDepth===1)root.dataset.motionBudget='paused';
  };
  const resumeMotionBudget=()=>{
    pauseDepth=Math.max(0,pauseDepth-1);
    if(pauseDepth===0)root.dataset.motionBudget='active';
  };
  const resetMotionBudget=()=>{
    pauseDepth=0;
    root.dataset.motionBudget='active';
  };

  const baseSetForm=setForm;
  const baseFinishMorphTransitionIfReady=finishMorphTransitionIfReady;
  const basePostChatAction=postChatAction;
  const baseFloatingChat=window.GuiPlusFloatingChat;
  const baseNativeOrbDown=baseFloatingChat.nativeOrbDown;
  const baseNativeOrbMove=baseFloatingChat.nativeOrbMove;
  const baseNativeOrbUp=baseFloatingChat.nativeOrbUp;
  const baseNativeOrbCancel=baseFloatingChat.nativeOrbCancel;

  let transitionBudgetHeld=false;
  let panelDragBudgetHeld=false;
  let orbDragBudgetHeld=false;

  setForm=function(value){
    const targetForm=value===2?2:0;
    if(targetForm===2&&(morphState==='expanding'||morphState==='expanded'))return;
    if(targetForm===0&&(morphState==='collapsing'||morphState==='collapsed'))return;
    if(!transitionBudgetHeld){
      transitionBudgetHeld=true;
      pauseMotionBudget();
    }
    baseSetForm(value);
  };

  finishMorphTransitionIfReady=function(now,perceptuallySettled){
    const before=morphState;
    baseFinishMorphTransitionIfReady(now,perceptuallySettled);
    const finished=
      (before==='expanding'&&morphState==='expanded')||
      (before==='collapsing'&&morphState==='collapsed');
    if(finished&&transitionBudgetHeld){
      transitionBudgetHeld=false;
      resumeMotionBudget();
    }
  };

  postChatAction=function(action,payload={}){
    if(action==='window.dragStart'&&!panelDragBudgetHeld){
      panelDragBudgetHeld=true;
      pauseMotionBudget();
    }
    const delivered=basePostChatAction(action,payload);
    if(action==='window.dragEnd'&&panelDragBudgetHeld){
      panelDragBudgetHeld=false;
      resumeMotionBudget();
    }
    return delivered;
  };

  function coordinatedNativeOrbDown(){
    if(!orbDragBudgetHeld){
      orbDragBudgetHeld=true;
      pauseMotionBudget();
    }
    baseNativeOrbDown();
  }

  function coordinatedNativeOrbMove(velocity){
    baseNativeOrbMove(velocity);
  }

  function coordinatedNativeOrbUp(wasMoved){
    baseNativeOrbUp(wasMoved);
    if(wasMoved&&orbDragBudgetHeld){
      orbDragBudgetHeld=false;
      resumeMotionBudget();
    }
    // 点击展开时 setForm 已接管同一份预算；这里只释放珠态按压占用。
    if(!wasMoved&&orbDragBudgetHeld){
      orbDragBudgetHeld=false;
      resumeMotionBudget();
    }
  }

  function coordinatedNativeOrbCancel(){
    baseNativeOrbCancel();
    if(orbDragBudgetHeld){
      orbDragBudgetHeld=false;
      resumeMotionBudget();
    }
  }

  window.GuiPlusFloatingChat=Object.freeze({
    ...baseFloatingChat,
    expand:()=>setForm(2),
    collapse:()=>setForm(0),
    nativeOrbDown:coordinatedNativeOrbDown,
    nativeOrbMove:coordinatedNativeOrbMove,
    nativeOrbUp:coordinatedNativeOrbUp,
    nativeOrbCancel:coordinatedNativeOrbCancel,
  });

  document.addEventListener('visibilitychange',()=>{
    if(document.hidden)resetMotionBudget();
  },{passive:true});
})();

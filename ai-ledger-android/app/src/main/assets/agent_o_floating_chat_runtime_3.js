

  function clearTransitionTimers(){
    for(const timerId of transitionTimers)clearTimeout(timerId);
    transitionTimers.clear();
  }

  let morphState='collapsed';
  let morphRevision=0;
  let morphStartedAt=0;
  let morphMilestone=0;

  function notifyMorphState(state,panelVisible){
    postChatAction('window.transition',{
      revision:morphRevision,
      state,
      panelVisible:Boolean(panelVisible)
    });
  }

  function resetPoseTarget(){
    Object.assign(poseTarget,{x:0,y:0,skew:0,stretchX:1,stretchY:1});
  }

  function setForm(value){
    const targetForm=value===2?2:0;
    if(targetForm===2&&(morphState==='expanding'||morphState==='expanded'))return;
    if(targetForm===0&&(morphState==='collapsing'||morphState==='collapsed'))return;

    clearTransitionTimers();
    ensureAnimationLoop();
    morphRevision+=1;
    morphStartedAt=performance.now();
    morphMilestone=0;
    root.dataset.content='0';
    root.dataset.orbOptics='0';

    if(targetForm===2){
      morphState='expanding';
      form=2;
      if(offsetX||offsetY){
        pose.x+=offsetX;
        pose.y+=offsetY;
        offsetX=0;
        offsetY=0;
        offsetDirty=true;
        applyOffset();
      }
      root.dataset.flight='1';
      root.dataset.phase='shrink';
      targetScale=P.shrinkScale;
      Object.assign(poseTarget,{
        x:pose.x+P.retreatX,
        y:pose.y+P.retreatY,
        skew:P.retreatSkew,
        stretchX:P.retreatSX,
        stretchY:P.retreatSY
      });
      updateSelection();
      notifyMorphState('expanding',false);
      return;
    }

    morphState='collapsing';
    form=0;
    geometryForm=0;
    root.dataset.form='0';
    root.dataset.flight='0';
    root.dataset.phase='settle';
    targetScale=.99;
    Object.assign(poseTarget,{
      x:-3,
      y:-3,
      skew:-.8,
      stretchX:1.012,
      stretchY:.994
    });
    updateSelection();
    notifyMorphState('collapsing',false);
  }

  function advanceMorphTimeline(now){
    const elapsed=now-morphStartedAt;
    if(morphState==='expanding'){
      if(morphMilestone<1&&elapsed>=P.launchDelay){
        morphMilestone=1;
        root.dataset.phase='flight';
        targetScale=P.launchScale;
        Object.assign(poseTarget,{
          x:P.launchX,
          y:P.launchY,
          skew:P.launchSkew,
          stretchX:P.launchSX,
          stretchY:P.launchSY
        });
      }
      if(morphMilestone<2&&elapsed>=P.impactDelay){
        morphMilestone=2;
        root.dataset.phase='impact';
        geometryForm=2;
        root.dataset.form='2';
        targetScale=P.impactScale;
        Object.assign(poseTarget,{
          x:P.impactX,
          y:0,
          skew:P.impactSkew,
          stretchX:P.impactSX,
          stretchY:P.impactSY
        });
        notifyMorphState('expanding',true);
      }
      if(morphMilestone<3&&elapsed>=P.settleDelay){
        morphMilestone=3;
        root.dataset.flight='0';
        root.dataset.phase='settle';
        targetScale=1;
        resetPoseTarget();
      }
      if(morphMilestone<4&&elapsed>=P.contentDelay){
        morphMilestone=4;
        root.dataset.content='2';
      }
      return;
    }

    if(morphState==='collapsing'&&morphMilestone<1&&elapsed>=Math.min(110,morphDuration*.34)){
      morphMilestone=1;
      root.dataset.phase='idle';
      targetScale=1;
      resetPoseTarget();
    }
  }

  function snapMorphGeometry(targetForm){
    const target=updateDesiredGeometry(targetForm);
    for(const key of geometryKeys){
      geometry[key]=target[key];
      velocity[key]=0;
    }
    shellScale=1;
    shellScaleVelocity=0;
    targetScale=1;
    Object.assign(pose,{x:0,y:0,skew:0,stretchX:1,stretchY:1});
    Object.assign(poseVelocity,{x:0,y:0,skew:0,stretchX:0,stretchY:0});
    resetPoseTarget();

    const widthPx=`${Math.max(1,geometry.width)}px`;
    const heightPx=`${Math.max(1,geometry.height)}px`;
    const radiusPx=`${Math.max(0,geometry.topRadius)}px ${Math.max(0,geometry.topRadius)}px ${Math.max(0,geometry.bottomRadius)}px ${Math.max(0,geometry.bottomRadius)}px`;
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
    setCachedRootVariable('--anchor-y',`${geometry.anchorY}px`);
    setCachedRootVariable('--shell-scale','1');
    setCachedRootVariable('--bubble-breath','1');
    setCachedRootVariable('--choreo-x','0px');
    setCachedRootVariable('--choreo-y','0px');
    setCachedRootVariable('--shell-skew','0deg');
    setCachedRootVariable('--stretch-x','1');
    setCachedRootVariable('--stretch-y','1');
  }

  function finishMorphTransitionIfReady(now,perceptuallySettled){
    const elapsed=now-morphStartedAt;
    if(morphState==='expanding'){
      const earliest=Math.max(P.contentDelay,P.settleDelay)+45;
      const deadline=Math.max(720,morphDuration*2.45);
      if(elapsed<earliest||(!perceptuallySettled&&elapsed<deadline))return;
      geometryForm=2;
      form=2;
      snapMorphGeometry(2);
      root.dataset.form='2';
      root.dataset.content='2';
      root.dataset.flight='0';
      root.dataset.phase='idle';
      opticalForm=1;
      opticalState=0;
      morphState='expanded';
      notifyMorphState('expanded',true);
      return;
    }

    if(morphState==='collapsing'){
      const earliest=Math.max(160,morphDuration*.52);
      const deadline=Math.max(620,morphDuration*2.15);
      if(elapsed<earliest||(!perceptuallySettled&&elapsed<deadline))return;
      geometryForm=0;
      form=0;
      snapMorphGeometry(0);
      root.dataset.form='0';
      root.dataset.content='0';
      root.dataset.flight='0';
      root.dataset.phase='idle';
      root.dataset.orbOptics='1';
      opticalForm=0;
      opticalState=0;
      morphState='collapsed';
      notifyMorphState('collapsed',false);
    }
  }

  /*
   * Floating chat integration surface.
   * Android can inject state with GuiPlusFloatingChat.hydrate(snapshot) and receive every
   * user intent through GuiPlusNative.postMessage(JSON) or a custom bridge registered with
   * GuiPlusFloatingChat.connect(adapter). This file deliberately performs no network request.
   */
  const CHAT_BRIDGE_VERSION='1.1.0';
  const CHAT_BRIDGE_SOURCE='gui-plus-floating-chat';
  const chatCopy=root.querySelector('.chat-copy');
  const chatMessageViewport=chatCopy.querySelector('.chat-message-viewport');
  const chatMessageList=chatCopy.querySelector('.chat-message-list');
  const composerForm=chatCopy.querySelector('.chat-composer');
  const composerInput=chatCopy.querySelector('.composer-input');
  const composerSend=chatCopy.querySelector('.composer-send-button');
  const scrollLatestButton=chatCopy.querySelector('.scroll-latest-button');
  const chatToast=chatCopy.querySelector('.chat-toast');
  const memoryPanel=chatCopy.querySelector('.memory-quick-panel');
  const skillPanel=chatCopy.querySelector('.skill-quick-panel');
  let chatBridgeAdapter=null;
  let mockReplyTimer=0;
  let toastTimer=0;
  let composerDispatchTimer=0;
  let actionSequence=0;
  const chatActionNames=Object.freeze([
    'workspace.toggle','agent.toggle','online.toggle','memory.open','memory.refresh','memory.manage',
    'skill.open','skill.refresh','skill.manage','skill.run','attachment.pick','attachment.remove','composer.change',
    'chat.send','chat.stop','chat.copy','chat.retry','chat.clear','panel.collapse',
    'window.ready','window.transition','window.dragStart','window.drag','window.dragEnd','composer.focus','composer.blur'
  ]);
  const chatState={
    schemaVersion:CHAT_BRIDGE_VERSION,
    bridgeConnected:false,
    workspaceEnabled:false,
    agentEnabled:true,
    onlineEnabled:false,
    isSending:false,
    selectedModelLabel:'自动选择',
    composerText:'',
    attachment:null,
    messages:[],
    memory:{loading:false,items:[]},
    skills:{loading:false,items:[]}
  };

  function safeClone(value){
    if(typeof structuredClone==='function')return structuredClone(value);
    return JSON.parse(JSON.stringify(value));
  }
  function normalizeMessage(message,index){
    const role=String(message&&message.role||'assistant').toLowerCase()==='user'?'user':'assistant';
    return {
      id:String(message&&message.id||`${role}-${Date.now()}-${index}`),
      role,
      text:String(message&&message.text||''),
      status:String(message&&message.status||'sent').toLowerCase(),
      source:message&&message.source?String(message.source):'',
      modelLabel:message&&message.modelLabel?String(message.modelLabel):'',
      createdAt:Number(message&&message.createdAt||Date.now()),
      errorText:message&&message.errorText?String(message.errorText):'',
      attachments:Array.isArray(message&&message.attachments)?message.attachments.map(item=>({
        id:String(item&&item.id||''),fileName:String(item&&item.fileName||item&&item.name||'视觉附件'),mimeType:String(item&&item.mimeType||'')
      })):[],
      structuredData:message&&message.structuredData&&typeof message.structuredData==='object'?safeClone(message.structuredData):null,
      webSources:Array.isArray(message&&message.webSources)?safeClone(message.webSources):[]
    };
  }
  function bridgeIsAvailable(){
    return Boolean(chatBridgeAdapter||
      (window.GuiPlusNative&&(typeof window.GuiPlusNative.postMessage==='function'||typeof window.GuiPlusNative.dispatch==='function'))||
      (window.ReactNativeWebView&&typeof window.ReactNativeWebView.postMessage==='function'));
  }
  function postChatAction(action,payload={}){
    const envelope={
      source:CHAT_BRIDGE_SOURCE,
      version:CHAT_BRIDGE_VERSION,
      type:'action',
      id:`web-${Date.now()}-${++actionSequence}`,
      action,
      payload,
      timestamp:Date.now()
    };
    let delivered=false;
    try{
      if(chatBridgeAdapter){
        if(typeof chatBridgeAdapter==='function')chatBridgeAdapter(envelope);
        else if(typeof chatBridgeAdapter.postMessage==='function')chatBridgeAdapter.postMessage(envelope);
        else if(typeof chatBridgeAdapter.dispatch==='function')chatBridgeAdapter.dispatch(envelope);
        delivered=true;
      }else if(window.GuiPlusNative&&typeof window.GuiPlusNative.postMessage==='function'){
        window.GuiPlusNative.postMessage(JSON.stringify(envelope));delivered=true;
      }else if(window.GuiPlusNative&&typeof window.GuiPlusNative.dispatch==='function'){
        window.GuiPlusNative.dispatch(action,JSON.stringify(payload));delivered=true;
      }else if(window.ReactNativeWebView&&typeof window.ReactNativeWebView.postMessage==='function'){
        window.ReactNativeWebView.postMessage(JSON.stringify(envelope));delivered=true;
      }
      if(window.parent!==window)window.parent.postMessage(envelope,'*');
    }catch(error){
      showChatToast('原生桥暂时不可用');
    }
    chatCopy.dispatchEvent(new CustomEvent('gui-plus-action',{detail:envelope,bubbles:true}));
    if(delivered!==chatState.bridgeConnected){chatState.bridgeConnected=delivered;renderBridgeStatus();}
    return delivered;
  }
  function showChatToast(message){
    clearTimeout(toastTimer);
    chatToast.textContent=message;
    chatToast.dataset.visible='true';
    toastTimer=setTimeout(()=>{delete chatToast.dataset.visible;},1250);
  }
  function setQuickPanel(kind,visible){
    const nextPanel=kind==='memory'?memoryPanel:skillPanel;
    const otherPanel=kind==='memory'?skillPanel:memoryPanel;
    nextPanel.hidden=!visible;
    otherPanel.hidden=true;
    chatCopy.querySelector('.memory-button').setAttribute('aria-expanded',String(!memoryPanel.hidden));
    chatCopy.querySelector('.skill-button').setAttribute('aria-expanded',String(!skillPanel.hidden));
  }
  function closeQuickPanels(){
    memoryPanel.hidden=true;skillPanel.hidden=true;
    chatCopy.querySelector('.memory-button').setAttribute('aria-expanded','false');
    chatCopy.querySelector('.skill-button').setAttribute('aria-expanded','false');
  }

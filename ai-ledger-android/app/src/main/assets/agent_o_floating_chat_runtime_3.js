

  function clearTransitionTimers(){
    for(const timerId of transitionTimers)clearTimeout(timerId);
    transitionTimers.clear();
  }

  function scheduleTransition(callback,delay){
    const timerId=setTimeout(()=>{
      transitionTimers.delete(timerId);
      callback();
    },Math.max(0,delay));
    transitionTimers.add(timerId);
    return timerId;
  }

  function setForm(value){
    if(value===form)return;
    clearTransitionTimers();
    ensureAnimationLoop();
    const previous=form;
    form=value;
    root.dataset.orbOptics='0';
    const transitionStretch=E.stretch/100;
    const token=++transitionToken;
    root.dataset.content='0';
    if(form>0&&previous===0){
      pose.x+=offsetX;pose.y+=offsetY;
      offsetX=0;offsetY=0;offsetDirty=true;applyOffset();
    }
    updateSelection();
    if(previous===0&&form>0){
      const destinationForm=form;
      root.dataset.flight='1';
      root.dataset.phase='shrink';
      targetScale=P.shrinkScale;
      Object.assign(poseTarget,{x:pose.x+P.retreatX,y:pose.y+P.retreatY,skew:P.retreatSkew,stretchX:P.retreatSX,stretchY:P.retreatSY});
      scheduleTransition(()=>{
        if(token!==transitionToken)return;
        root.dataset.phase='flight';
        targetScale=P.launchScale;
        Object.assign(poseTarget,{x:P.launchX,y:P.launchY,skew:P.launchSkew,stretchX:P.launchSX,stretchY:P.launchSY});
      },P.launchDelay);
      scheduleTransition(()=>{
        if(token!==transitionToken)return;
        root.dataset.phase='impact';
        postChatAction('window.form',{form:destinationForm});
        geometryForm=destinationForm;
        root.dataset.form=String(destinationForm);
        targetScale=P.impactScale;
        Object.assign(poseTarget,{x:P.impactX,y:0,skew:P.impactSkew,stretchX:P.impactSX,stretchY:P.impactSY});
      },P.impactDelay);
      scheduleTransition(()=>{
        if(token!==transitionToken)return;
        root.dataset.flight='0';
        root.dataset.phase='settle';
        targetScale=1;
        Object.assign(poseTarget,{x:0,y:0,skew:0,stretchX:1,stretchY:1});
      },P.settleDelay);
      scheduleTransition(()=>{if(token===transitionToken){root.dataset.content=String(destinationForm);root.dataset.phase='idle';}},P.contentDelay);
      return;
    }
    const opening=form>geometryForm;
    const anticipation=Math.min(105,morphDuration*.22);
    targetScale=.975;
    if(previous===0&&form===1){
      Object.assign(poseTarget,{x:-7,y:1,skew:-1.8,stretchX:.94,stretchY:1.035});
    }else if(previous===1&&form===2){
      root.dataset.phase='panel-press';
      Object.assign(poseTarget,{x:0,y:P.panelPrepressY,skew:0,stretchX:1+.018*transitionStretch,stretchY:1-.06*transitionStretch});
    }else if(form<previous){
      Object.assign(poseTarget,{x:0,y:-3,skew:0,stretchX:1-.025*transitionStretch,stretchY:1+.025*transitionStretch});
    }
    scheduleTransition(()=>{
      if(token!==transitionToken)return;
      geometryForm=form;
      root.dataset.form=String(form);
      if(form>0)postChatAction('window.form',{form});
      targetScale=opening?1.012:.99;
      if(previous===0&&form===1){
        Object.assign(poseTarget,{x:9,y:0,skew:1.7,stretchX:1.026,stretchY:.985});
      }else if(previous===1&&form===2){
        root.dataset.phase='panel-lift';
        Object.assign(poseTarget,{x:0,y:P.panelLiftY,skew:0,stretchX:1-.008*transitionStretch,stretchY:1+.018*transitionStretch});
      }else{
        Object.assign(poseTarget,{x:form===0?-3:0,y:0,skew:form===0?-.8:0,stretchX:1+.012*transitionStretch,stretchY:1-.006*transitionStretch});
      }
      if(form===0){
        scheduleTransition(()=>{
          if(token===transitionToken){root.dataset.orbOptics='1';postChatAction('window.form',{form:0});}
        },Math.max(140,morphDuration*.55));
      }
      scheduleTransition(()=>{
        if(token!==transitionToken)return;
        root.dataset.phase='idle';
        targetScale=1;
        Object.assign(poseTarget,{x:0,y:0,skew:0,stretchX:1,stretchY:1});
      },Math.min(165,morphDuration*.34));
      if(form>0){
        scheduleTransition(()=>{
          if(token===transitionToken)root.dataset.content=String(form);
        },Math.max(175,morphDuration*.64));
      }
    },opening?anticipation:0);
  }

  /*
   * Floating chat integration surface.
   * Android can inject state with GuiPlusFloatingChat.hydrate(snapshot) and receive every
   * user intent through GuiPlusNative.postMessage(JSON) or a custom bridge registered with
   * GuiPlusFloatingChat.connect(adapter). This file deliberately performs no network request.
   */
  const CHAT_BRIDGE_VERSION='1.0.0';
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
    'window.ready','window.form','window.dragStart','window.drag','window.dragEnd','composer.focus','composer.blur'
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

let agentOMessageIndex=new Map();
let agentOMessageRows=new Map();
let agentOTailFrame=0;

function rebuildAgentOMessageCache(){
  agentOMessageIndex.clear();
  chatState.messages.forEach((message,index)=>agentOMessageIndex.set(String(message.id),index));
  agentOMessageRows.clear();
  chatMessageList.querySelectorAll('.message-row').forEach(row=>{
    const id=row.dataset.messageId;
    if(id)agentOMessageRows.set(id,row);
  });
}

function scheduleAgentOTailUpdate(forceBottom=true){
  if(agentOTailFrame)return;
  agentOTailFrame=requestAnimationFrame(()=>{
    agentOTailFrame=0;
    if(forceBottom)scrollMessagesToBottom(false);
    updateScrollLatestButton();
  });
}

function sameAgentOAttachment(raw,current){
  if(!raw&&!current)return true;
  if(!raw||!current)return false;
  return String(raw.id||'')===String(current.id||'')&&
    String(raw.fileName||raw.name||'视觉附件')===String(current.fileName||'视觉附件')&&
    String(raw.mimeType||'')===String(current.mimeType||'')&&
    String(raw.status||'')===String(current.status||'')&&
    String(raw.statusLabel||'')===String(current.statusLabel||'');
}

function sameAgentOStructuredData(raw,current){
  if(!raw&&!current)return true;
  if(!raw||!current)return false;
  const rawMetrics=Array.isArray(raw.metrics)?raw.metrics:[];
  const currentMetrics=Array.isArray(current.metrics)?current.metrics:[];
  if(String(raw.title||'')!==String(current.title||'')||
    String(raw.subtitle||'')!==String(current.subtitle||'')||
    rawMetrics.length!==currentMetrics.length)return false;
  for(let index=0;index<rawMetrics.length;index+=1){
    const left=rawMetrics[index]||{};
    const right=currentMetrics[index]||{};
    if(String(left.label||'')!==String(right.label||'')||
      String(left.value||'')!==String(right.value||'')||
      String(left.unit||'')!==String(right.unit||''))return false;
  }
  return true;
}

function sameAgentOSources(raw,current){
  const left=Array.isArray(raw)?raw:[];
  const right=Array.isArray(current)?current:[];
  if(left.length!==right.length)return false;
  for(let index=0;index<left.length;index+=1){
    const a=left[index]||{};
    const b=right[index]||{};
    if(String(a.title||'')!==String(b.title||'')||
      String(a.domain||'')!==String(b.domain||'')||
      String(a.url||'')!==String(b.url||''))return false;
  }
  return true;
}

function sameAgentOMessages(rawMessages){
  if(!Array.isArray(rawMessages)||rawMessages.length!==chatState.messages.length)return false;
  for(let index=0;index<rawMessages.length;index+=1){
    const raw=rawMessages[index]||{};
    const current=chatState.messages[index];
    if(!current||
      String(raw.id||'')!==String(current.id||'')||
      String(raw.role||'assistant').toLowerCase()!==current.role||
      String(raw.text||'')!==current.text||
      String(raw.status||'sent').toLowerCase()!==current.status||
      String(raw.source||'')!==current.source||
      String(raw.modelLabel||'')!==current.modelLabel||
      Number(raw.createdAt||0)!==Number(current.createdAt||0)||
      String(raw.errorText||'')!==current.errorText)return false;
    const rawAttachments=Array.isArray(raw.attachments)?raw.attachments:[];
    if(rawAttachments.length!==current.attachments.length)return false;
    for(let attachmentIndex=0;attachmentIndex<rawAttachments.length;attachmentIndex+=1){
      if(!sameAgentOAttachment(rawAttachments[attachmentIndex],current.attachments[attachmentIndex]))return false;
    }
    if(!sameAgentOStructuredData(raw.structuredData,current.structuredData)||
      !sameAgentOSources(raw.webSources,current.webSources))return false;
  }
  return true;
}

function handleChatAction(action,payload={}){
  if(!chatActionNames.includes(action))return;
  if(action==='workspace.toggle'){chatState.workspaceEnabled=!chatState.workspaceEnabled;renderToolbar();postChatAction(action,{enabled:chatState.workspaceEnabled});return;}
  if(action==='agent.toggle'){chatState.agentEnabled=!chatState.agentEnabled;renderToolbar();postChatAction(action,{enabled:chatState.agentEnabled});return;}
  if(action==='online.toggle'){chatState.onlineEnabled=!chatState.onlineEnabled;renderToolbar();postChatAction(action,{enabled:chatState.onlineEnabled});return;}
  if(action==='memory.open'||action==='skill.open'){postChatAction(action);return;}
  if(action==='panel.collapse'){closeQuickPanels();postChatAction(action);setForm(0);return;}
  if(action==='chat.clear'){
    stopMockReply();clearTimeout(composerDispatchTimer);chatState.messages=[];chatState.isSending=false;
    chatState.composerText='';chatState.attachment=null;renderFloatingChat(true);rebuildAgentOMessageCache();postChatAction(action);showChatToast('对话已清空');return;
  }
  if(action==='chat.copy'){
    const index=agentOMessageIndex.get(String(payload.messageId));
    const message=index===undefined?null:chatState.messages[index];
    if(message)copyChatText(message.text||message.errorText||'');
    postChatAction(action,payload);return;
  }
  if(action==='chat.retry'){
    const index=agentOMessageIndex.get(String(payload.messageId));
    let previous=null;
    if(index!==undefined){
      for(let cursor=index-1;cursor>=0;cursor-=1){
        if(chatState.messages[cursor].role==='user'){previous=chatState.messages[cursor];break;}
      }
    }
    postChatAction(action,payload);
    if(!bridgeIsAvailable()&&previous)showChatToast('原生聊天桥未连接');
    return;
  }
  if(action==='chat.stop'){
    stopMockReply();chatState.isSending=false;
    chatState.messages=chatState.messages.map(item=>item.status==='sending'?{...item,text:item.text==='正在思考…'?'已停止生成。':item.text,status:'stopped'}:item);
    renderFloatingChat(true);rebuildAgentOMessageCache();postChatAction(action);return;
  }
  if(action==='chat.send'){
    const text=String(payload.text||chatState.composerText||'').trim();
    if(!text&&!chatState.attachment)return;
    clearTimeout(composerDispatchTimer);
    const delivered=postChatAction(action,{text,attachment:chatState.attachment});
    if(delivered){chatState.composerText='';chatState.isSending=true;renderComposer();}
    else showChatToast('原生聊天桥未连接');
    return;
  }
  if(action==='attachment.remove'){chatState.attachment=null;renderComposer();postChatAction(action,payload);return;}
  postChatAction(action,payload);
  if(action==='attachment.pick'&&!bridgeIsAvailable())showChatToast('接入原生后可选择图片');
  if(action==='skill.run'&&!bridgeIsAvailable())showChatToast('已发送 Skill 运行请求');
}

function hydrateFloatingChat(snapshot,options={}){
  try{if(typeof snapshot==='string')snapshot=JSON.parse(snapshot);}catch(error){showChatToast('状态数据格式错误');return false;}
  if(!snapshot||typeof snapshot!=='object')return false;

  let toolbarDirty=false;
  let composerDirty=false;
  let messagesDirty=false;
  let quickPanelsDirty=false;
  const assignBooleanOrText=(key,group)=>{
    if(!Object.prototype.hasOwnProperty.call(snapshot,key)||chatState[key]===snapshot[key])return;
    chatState[key]=snapshot[key];
    if(group==='toolbar')toolbarDirty=true;
    else composerDirty=true;
  };
  assignBooleanOrText('workspaceEnabled','toolbar');
  assignBooleanOrText('agentEnabled','toolbar');
  assignBooleanOrText('onlineEnabled','toolbar');
  const previousModelLabel=chatState.selectedModelLabel;
  assignBooleanOrText('selectedModelLabel','toolbar');
  if(previousModelLabel!==chatState.selectedModelLabel&&!chatState.messages.length)messagesDirty=true;
  assignBooleanOrText('isSending','composer');
  assignBooleanOrText('composerText','composer');

  if(Object.prototype.hasOwnProperty.call(snapshot,'attachment')&&!sameAgentOAttachment(snapshot.attachment,chatState.attachment)){
    chatState.attachment=snapshot.attachment?{...snapshot.attachment}:null;
    composerDirty=true;
  }
  if(Array.isArray(snapshot.messages)&&!sameAgentOMessages(snapshot.messages)){
    chatState.messages=snapshot.messages.map(normalizeMessage);
    messagesDirty=true;
    toolbarDirty=true;
  }
  if(snapshot.memory&&typeof snapshot.memory==='object'){
    const next={...chatState.memory,...snapshot.memory};
    if(JSON.stringify(next)!==JSON.stringify(chatState.memory)){chatState.memory=next;quickPanelsDirty=true;}
  }
  if(snapshot.skills&&typeof snapshot.skills==='object'){
    const next={...chatState.skills,...snapshot.skills};
    if(JSON.stringify(next)!==JSON.stringify(chatState.skills)){chatState.skills=next;quickPanelsDirty=true;}
  }
  if(options.connected!==false&&!chatState.bridgeConnected){chatState.bridgeConnected=true;toolbarDirty=true;}

  if(toolbarDirty)renderToolbar();
  if(messagesDirty){renderMessages(Boolean(options.forceBottom));rebuildAgentOMessageCache();}
  else if(options.forceBottom)scheduleAgentOTailUpdate(true);
  if(quickPanelsDirty)renderQuickPanels();
  if(composerDirty)renderComposer();
  if(!nativeProduction){
    chatCopy.dispatchEvent(new CustomEvent('gui-plus-state-applied',{detail:{version:CHAT_BRIDGE_VERSION,state:safeClone(chatState)}}));
  }
  return true;
}

function patchFloatingMessage(id,text,status){
  const messageId=String(id);
  let index=agentOMessageIndex.get(messageId);
  if(index===undefined){rebuildAgentOMessageCache();index=agentOMessageIndex.get(messageId);}
  if(index===undefined)return false;
  const current=chatState.messages[index];
  const nextText=String(text||'');
  const nextStatus=status||current.status;
  if(current.text===nextText&&current.status===nextStatus)return true;
  chatState.messages[index]={...current,text:nextText,status:nextStatus};
  let row=agentOMessageRows.get(messageId);
  if(!row||!row.isConnected){rebuildAgentOMessageCache();row=agentOMessageRows.get(messageId);}
  if(row&&row.dataset.status===nextStatus){
    const textElement=row.querySelector('.message-text');
    if(textElement&&textElement.textContent!==nextText)textElement.textContent=nextText;
    scheduleAgentOTailUpdate(true);
    return true;
  }
  renderMessages(true);
  rebuildAgentOMessageCache();
  return true;
}

function connectFloatingChat(adapter){
  chatBridgeAdapter=adapter||null;
  chatState.bridgeConnected=bridgeIsAvailable();
  renderBridgeStatus();
  return window.GuiPlusFloatingChat;
}

function nativeOrbDown(){
  ensureAnimationLoop();
  dragging=true;
  moved=false;
  root.dataset.dragging='true';
  root.dataset.phase='drag';
  targetScale=P.dragPressScale;
}

function nativeOrbMove(velocity){
  if(!dragging)return;
  ensureAnimationLoop();
  const safeVelocity=Number.isFinite(Number(velocity))?Number(velocity):0;
  poseTarget.skew=Math.max(-P.dragSkewMax,Math.min(P.dragSkewMax,safeVelocity*P.dragSkewGain));
  poseTarget.stretchX=1+Math.min(P.dragStretchMax,Math.abs(safeVelocity)*.018);
  poseTarget.stretchY=2-poseTarget.stretchX;
}

function nativeOrbUp(wasMoved){
  dragging=false;
  targetScale=1;
  resetPoseTarget();
  root.dataset.dragging='false';
  root.dataset.phase='idle';
  ensureAnimationLoop();
  if(!wasMoved)setForm(2);
}

function nativeOrbCancel(){
  dragging=false;
  targetScale=1;
  resetPoseTarget();
  root.dataset.dragging='false';
  root.dataset.phase='idle';
  ensureAnimationLoop();
}

function nativeOrbTap(rebaseX,rebaseY){
  const x=Number(rebaseX);
  const y=Number(rebaseY);
  if(Number.isFinite(x)&&Number.isFinite(y)){
    offsetX=x;
    offsetY=y;
    offsetDirty=true;
    applyOffset();
  }
  nativeOrbUp(false);
}

window.GuiPlusFloatingChat=Object.freeze({
  version:CHAT_BRIDGE_VERSION,
  source:CHAT_BRIDGE_SOURCE,
  actions:chatActionNames,
  connect:connectFloatingChat,
  disconnect:()=>connectFloatingChat(null),
  hydrate:hydrateFloatingChat,
  patchMessage:patchFloatingMessage,
  getState:()=>safeClone(chatState),
  dispatch:handleChatAction,
  expand:()=>setForm(2),
  collapse:()=>setForm(0),
  nativeOrbDown,
  nativeOrbMove,
  nativeOrbUp,
  nativeOrbCancel,
  nativeOrbTap,
  suspend:()=>{pageActive=false;pauseAnimationLoop();if(agentOTailFrame){cancelAnimationFrame(agentOTailFrame);agentOTailFrame=0;}},
  resume:()=>{pageActive=!document.hidden;if(pageActive)ensureAnimationLoop();}
});

chatCopy.addEventListener('pointerdown',event=>event.stopPropagation());
chatCopy.addEventListener('pointerup',event=>event.stopPropagation());
chatCopy.addEventListener('click',event=>{
  const quickToggle=event.target.closest('[data-chat-action="memory.toggle"],[data-chat-action="skill.toggle"]');
  if(!quickToggle&&!event.target.closest('.quick-panel'))closeQuickPanels();
  const messageAction=event.target.closest('[data-message-action]');
  if(messageAction){handleChatAction(`chat.${messageAction.dataset.messageAction}`,{messageId:messageAction.dataset.messageId});return;}
  const trigger=event.target.closest('[data-chat-action]');if(!trigger)return;
  const action=trigger.dataset.chatAction;
  if(action==='memory.toggle'){
    const visible=memoryPanel.hidden;setQuickPanel('memory',visible);if(visible)handleChatAction('memory.open');return;
  }
  if(action==='skill.toggle'){
    const visible=skillPanel.hidden;setQuickPanel('skill',visible);if(visible)handleChatAction('skill.open');return;
  }
  if(action==='skill.run'||action==='memory.manage'||action==='skill.manage')closeQuickPanels();
  handleChatAction(action,{skillId:trigger.dataset.skillId||undefined});
});

composerInput.addEventListener('pointerdown',()=>postChatAction('composer.focus'));
composerInput.addEventListener('focus',()=>postChatAction('composer.focus'));
composerInput.addEventListener('blur',()=>postChatAction('composer.blur'));
composerInput.addEventListener('input',()=>{
  chatState.composerText=composerInput.value;renderComposer();
  clearTimeout(composerDispatchTimer);
  composerDispatchTimer=setTimeout(()=>postChatAction('composer.change',{text:chatState.composerText}),48);
});
composerInput.addEventListener('keydown',event=>{
  if(event.key==='Enter'&&!event.shiftKey&&!event.isComposing){event.preventDefault();composerForm.requestSubmit();}
});
composerForm.addEventListener('submit',event=>{
  event.preventDefault();handleChatAction(chatState.isSending?'chat.stop':'chat.send',{text:composerInput.value});
});
chatMessageViewport.addEventListener('scroll',updateScrollLatestButton,{passive:true});
scrollLatestButton.addEventListener('click',()=>scrollMessagesToBottom(true));
document.addEventListener('pointerdown',event=>{if(!chatCopy.contains(event.target))closeQuickPanels();},{passive:true});
if(!nativeProduction){
  window.addEventListener('message',event=>{
    const data=event.data;
    if(!data||typeof data!=='object'||data.source!=='gui-plus-native')return;
    if(data.type==='state')hydrateFloatingChat(data.payload,{connected:true,forceBottom:Boolean(data.forceBottom)});
  });
  window.addEventListener('gui-plus-state',event=>hydrateFloatingChat(event.detail,{connected:true}));
}
window.addEventListener('keydown',event=>{
  if(event.key==='Escape'&&form===2){
    if(!memoryPanel.hidden||!skillPanel.hidden)closeQuickPanels();
    else handleChatAction('panel.collapse');
  }
});

/* 展开态仅使用 V8.4 原工具栏空白处拖动；跨桥消息每个显示帧最多一次。 */
const chatToolbar=chatCopy.querySelector('.chat-toolbar');
let panelDragPointer=-1;
let panelDragStartX=0;
let panelDragStartY=0;
let panelDragging=false;
let panelDragFrame=0;
let pendingPanelDragX=0;
let pendingPanelDragY=0;

function dispatchPendingPanelDrag(){
  panelDragFrame=0;
  if(!panelDragging)return;
  postChatAction('window.drag',{dx:pendingPanelDragX,dy:pendingPanelDragY,expanded:true});
}

function finishPanelDrag(event){
  if(!panelDragging||event.pointerId!==panelDragPointer)return;
  if(panelDragFrame){
    cancelAnimationFrame(panelDragFrame);
    panelDragFrame=0;
    postChatAction('window.drag',{dx:pendingPanelDragX,dy:pendingPanelDragY,expanded:true});
  }
  if(chatToolbar.hasPointerCapture(panelDragPointer))chatToolbar.releasePointerCapture(panelDragPointer);
  postChatAction('window.dragEnd',{expanded:true});
  panelDragging=false;
  panelDragPointer=-1;
  chatToolbar.dataset.dragging='false';
  event.preventDefault();
  event.stopPropagation();
}
chatToolbar.addEventListener('pointerdown',event=>{
  if(form!==2||morphState!=='expanded'||!bridgeIsAvailable()||event.button!==0)return;
  if(event.target.closest('button,input,textarea,a,[role="button"]'))return;
  panelDragging=true;
  panelDragPointer=event.pointerId;
  panelDragStartX=event.screenX;
  panelDragStartY=event.screenY;
  pendingPanelDragX=0;
  pendingPanelDragY=0;
  chatToolbar.dataset.dragging='true';
  chatToolbar.setPointerCapture(panelDragPointer);
  postChatAction('window.dragStart',{pointerId:panelDragPointer,expanded:true});
  event.preventDefault();
  event.stopPropagation();
},true);
chatToolbar.addEventListener('pointermove',event=>{
  if(!panelDragging||event.pointerId!==panelDragPointer)return;
  pendingPanelDragX=event.screenX-panelDragStartX;
  pendingPanelDragY=event.screenY-panelDragStartY;
  if(!panelDragFrame)panelDragFrame=requestAnimationFrame(dispatchPendingPanelDrag);
  event.preventDefault();
  event.stopPropagation();
},true);
chatToolbar.addEventListener('pointerup',finishPanelDrag,true);
chatToolbar.addEventListener('pointercancel',finishPanelDrag,true);

if(!nativeProduction){
  root.querySelectorAll('.form-btn').forEach(button=>button.addEventListener('click',()=>setForm(Number(button.dataset.form))));
}

/* 网页预览仍使用原始 DOM 拖动；App 珠态由独立原生紧尺寸触摸窗驱动。 */
const nativeWindowDrag=Boolean(window.__agentONativeWindowDrag);
if(!nativeWindowDrag){
  shell.addEventListener('pointerdown',event=>{
    ensureAnimationLoop();
    pointerId=event.pointerId;
    moved=false;
    startClientX=event.clientX;
    startClientY=event.clientY;
    startOffsetX=offsetX;
    startOffsetY=offsetY;
    lastPointerX=startClientX;
    lastPointerTime=performance.now();
    dragging=form===0;
    if(dragging)root.dataset.phase='drag';
    targetScale=form===0?P.dragPressScale:.985;
    if(dragging){
      const stageRect=stage.getBoundingClientRect();
      const shellRect=shell.getBoundingClientRect();
      dragMaxX=Math.max(0,(stageRect.width-shellRect.width)/2-8);
      dragMaxY=Math.max(0,(stageRect.height-shellRect.height)/2-8);
      root.dataset.dragging='true';
      shell.setPointerCapture(pointerId);
    }
  });

  shell.addEventListener('pointermove',event=>{
    if(!dragging||event.pointerId!==pointerId)return;
    const dx=event.clientX-startClientX;
    const dy=event.clientY-startClientY;
    if(Math.hypot(dx,dy)>4)moved=true;
    offsetX=Math.max(-dragMaxX,Math.min(dragMaxX,startOffsetX+dx));
    offsetY=Math.max(-dragMaxY,Math.min(dragMaxY,startOffsetY+dy));
    const now=performance.now();
    const pointerVelocity=(event.clientX-lastPointerX)/Math.max(8,now-lastPointerTime);
    poseTarget.skew=Math.max(-P.dragSkewMax,Math.min(P.dragSkewMax,pointerVelocity*P.dragSkewGain));
    poseTarget.stretchX=1+Math.min(P.dragStretchMax,Math.abs(pointerVelocity)*.018);
    poseTarget.stretchY=2-poseTarget.stretchX;
    lastPointerX=event.clientX;
    lastPointerTime=now;
    offsetDirty=true;
  });

  shell.addEventListener('pointerup',event=>{
    const shouldAdvance=!dragging||!moved;
    if(dragging&&shell.hasPointerCapture(event.pointerId))shell.releasePointerCapture(event.pointerId);
    dragging=false;
    targetScale=1;
    resetPoseTarget();
    root.dataset.dragging='false';
    root.dataset.phase='idle';
    if(shouldAdvance)setForm(form===0?2:0);
  });

  shell.addEventListener('pointercancel',event=>{
    if(shell.hasPointerCapture(event.pointerId))shell.releasePointerCapture(event.pointerId);
    nativeOrbCancel();
  });
}

renderFloatingChat(true);
rebuildAgentOMessageCache();
applyEasyParameters();
createOpticalProgram();
updateDesiredGeometry(0,geometry);
ensureAnimationLoop();
postChatAction('window.ready',{form});

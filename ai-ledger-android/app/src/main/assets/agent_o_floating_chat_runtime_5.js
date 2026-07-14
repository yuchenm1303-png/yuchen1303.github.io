function handleChatAction(action,payload={}){
  if(!chatActionNames.includes(action))return;
  if(action==='workspace.toggle'){chatState.workspaceEnabled=!chatState.workspaceEnabled;renderToolbar();postChatAction(action,{enabled:chatState.workspaceEnabled});return;}
  if(action==='agent.toggle'){chatState.agentEnabled=!chatState.agentEnabled;renderToolbar();postChatAction(action,{enabled:chatState.agentEnabled});return;}
  if(action==='online.toggle'){chatState.onlineEnabled=!chatState.onlineEnabled;renderToolbar();postChatAction(action,{enabled:chatState.onlineEnabled});return;}
  if(action==='memory.open'||action==='skill.open'){postChatAction(action);return;}
  if(action==='panel.collapse'){closeQuickPanels();postChatAction(action);setForm(0);return;}
  if(action==='chat.clear'){
    stopMockReply();clearTimeout(composerDispatchTimer);chatState.messages=[];chatState.isSending=false;
    chatState.composerText='';chatState.attachment=null;renderFloatingChat(true);postChatAction(action);showChatToast('对话已清空');return;
  }
  if(action==='chat.copy'){
    const message=chatState.messages.find(item=>item.id===payload.messageId);
    if(message)copyChatText(message.text||message.errorText||'');
    postChatAction(action,payload);return;
  }
  if(action==='chat.retry'){
    const index=chatState.messages.findIndex(item=>item.id===payload.messageId);
    const previous=[...chatState.messages.slice(0,index)].reverse().find(item=>item.role==='user');
    postChatAction(action,payload);
    if(!bridgeIsAvailable()&&previous)showChatToast('原生聊天桥未连接');
    return;
  }
  if(action==='chat.stop'){
    stopMockReply();chatState.isSending=false;
    chatState.messages=chatState.messages.map(item=>item.status==='sending'?{...item,text:item.text==='正在思考…'?'已停止生成。':item.text,status:'stopped'}:item);
    renderFloatingChat(true);postChatAction(action);return;
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
  const keys=['workspaceEnabled','agentEnabled','onlineEnabled','isSending','selectedModelLabel','composerText','attachment'];
  keys.forEach(key=>{if(Object.prototype.hasOwnProperty.call(snapshot,key))chatState[key]=snapshot[key];});
  if(Array.isArray(snapshot.messages))chatState.messages=snapshot.messages.map(normalizeMessage);
  if(snapshot.memory&&typeof snapshot.memory==='object')chatState.memory={...chatState.memory,...snapshot.memory};
  if(snapshot.skills&&typeof snapshot.skills==='object')chatState.skills={...chatState.skills,...snapshot.skills};
  if(options.connected!==false)chatState.bridgeConnected=true;
  renderFloatingChat(Boolean(options.forceBottom));
  chatCopy.dispatchEvent(new CustomEvent('gui-plus-state-applied',{detail:{version:CHAT_BRIDGE_VERSION,state:safeClone(chatState)}}));
  return true;
}

function patchFloatingMessage(id,text,status){
  const index=chatState.messages.findIndex(item=>item.id===id);
  if(index<0)return false;
  const current=chatState.messages[index];
  const nextStatus=status||current.status;
  chatState.messages[index]={...current,text:String(text||''),status:nextStatus};
  const row=[...chatMessageList.querySelectorAll('.message-row')].find(item=>item.dataset.messageId===String(id));
  if(row&&row.dataset.status===nextStatus){
    const textElement=row.querySelector('.message-text');
    if(textElement&&textElement.textContent!==String(text||''))textElement.textContent=String(text||'');
    requestAnimationFrame(()=>{scrollMessagesToBottom(false);updateScrollLatestButton();});
    return true;
  }
  renderMessages(true);
  return true;
}

function connectFloatingChat(adapter){
  chatBridgeAdapter=adapter||null;
  chatState.bridgeConnected=bridgeIsAvailable();
  renderBridgeStatus();
  return window.GuiPlusFloatingChat;
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
  suspend:()=>{pageActive=false;pauseAnimationLoop();},
  resume:()=>{pageActive=!document.hidden;if(pageActive)ensureAnimationLoop();}
});

chatCopy.addEventListener('pointerdown',event=>event.stopPropagation());
chatCopy.addEventListener('pointerup',event=>event.stopPropagation());
chatCopy.addEventListener('click',event=>{
  const quickToggle=event.target.closest('[data-chat-action="memory.toggle"],[data-chat-action="skill.toggle"]');
  if(!quickToggle&&!event.target.closest('.quick-panel'))closeQuickPanels();
  const messageAction=event.target.closest('[data-message-action]');
  if(messageAction){handleChatAction(`chat.${messageAction.dataset.messageAction}`,{messageId:messageAction.dataset.messageId});return;}
  const trigger=event.target.closest('[data-chat-action]');
  if(!trigger)return;
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
window.addEventListener('message',event=>{
  const data=event.data;
  if(!data||typeof data!=='object'||data.source!=='gui-plus-native')return;
  if(data.type==='state')hydrateFloatingChat(data.payload,{connected:true,forceBottom:Boolean(data.forceBottom)});
});
window.addEventListener('gui-plus-state',event=>hydrateFloatingChat(event.detail,{connected:true}));
window.addEventListener('keydown',event=>{
  if(event.key==='Escape'&&form===2){
    if(!memoryPanel.hidden||!skillPanel.hidden)closeQuickPanels();
    else handleChatAction('panel.collapse');
  }
});

/* 展开态仅使用 V8.4 原工具栏空白处拖动，screenX/Y 不受窗口自身移动影响。 */
const chatToolbar=chatCopy.querySelector('.chat-toolbar');
let panelDragPointer=-1;
let panelDragStartX=0;
let panelDragStartY=0;
let panelDragging=false;
function finishPanelDrag(event){
  if(!panelDragging||event.pointerId!==panelDragPointer)return;
  if(chatToolbar.hasPointerCapture(panelDragPointer))chatToolbar.releasePointerCapture(panelDragPointer);
  postChatAction('window.dragEnd',{expanded:true});
  panelDragging=false;
  panelDragPointer=-1;
  chatToolbar.dataset.dragging='false';
  event.preventDefault();
  event.stopPropagation();
}
chatToolbar.addEventListener('pointerdown',event=>{
  if(form!==2||!bridgeIsAvailable()||event.button!==0)return;
  if(event.target.closest('button,input,textarea,a,[role="button"]'))return;
  panelDragging=true;
  panelDragPointer=event.pointerId;
  panelDragStartX=event.screenX;
  panelDragStartY=event.screenY;
  chatToolbar.dataset.dragging='true';
  chatToolbar.setPointerCapture(panelDragPointer);
  postChatAction('window.dragStart',{pointerId:panelDragPointer,expanded:true});
  event.preventDefault();
  event.stopPropagation();
},true);
chatToolbar.addEventListener('pointermove',event=>{
  if(!panelDragging||event.pointerId!==panelDragPointer)return;
  postChatAction('window.drag',{
    dx:event.screenX-panelDragStartX,
    dy:event.screenY-panelDragStartY,
    expanded:true
  });
  event.preventDefault();
  event.stopPropagation();
},true);
chatToolbar.addEventListener('pointerup',finishPanelDrag,true);
chatToolbar.addEventListener('pointercancel',finishPanelDrag,true);

root.querySelectorAll('.form-btn').forEach(button=>button.addEventListener('click',()=>setForm(Number(button.dataset.form))));

/*
 * 珠态窗口由 Android raw MotionEvent 直接移动。JS 只计算原版形变，不再每帧跨桥更新窗口，
 * 从根源上消除窗口移动后 clientX 反向变化造成的来回振荡。
 */
const nativeWindowDrag=Boolean(window.__agentONativeWindowDrag);
shell.addEventListener('pointerdown',event=>{
  ensureAnimationLoop();
  pointerId=event.pointerId;
  moved=false;
  startClientX=nativeWindowDrag?event.screenX:event.clientX;
  startClientY=nativeWindowDrag?event.screenY:event.clientY;
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
  const pointerX=nativeWindowDrag?event.screenX:event.clientX;
  const pointerY=nativeWindowDrag?event.screenY:event.clientY;
  const dx=pointerX-startClientX;
  const dy=pointerY-startClientY;
  if(Math.hypot(dx,dy)>4)moved=true;
  if(nativeWindowDrag){
    offsetX=0;offsetY=0;
  }else{
    offsetX=Math.max(-dragMaxX,Math.min(dragMaxX,startOffsetX+dx));
    offsetY=Math.max(-dragMaxY,Math.min(dragMaxY,startOffsetY+dy));
  }
  const now=performance.now();
  const pointerVelocity=(pointerX-lastPointerX)/Math.max(8,now-lastPointerTime);
  poseTarget.skew=Math.max(-P.dragSkewMax,Math.min(P.dragSkewMax,pointerVelocity*P.dragSkewGain));
  poseTarget.stretchX=1+Math.min(P.dragStretchMax,Math.abs(pointerVelocity)*.018);
  poseTarget.stretchY=2-poseTarget.stretchX;
  lastPointerX=pointerX;
  lastPointerTime=now;
  offsetDirty=true;
});

shell.addEventListener('pointerup',event=>{
  const shouldAdvance=!dragging||!moved;
  if(dragging&&shell.hasPointerCapture(event.pointerId))shell.releasePointerCapture(event.pointerId);
  dragging=false;
  targetScale=1;
  Object.assign(poseTarget,{x:0,y:0,skew:0,stretchX:1,stretchY:1});
  root.dataset.dragging='false';
  root.dataset.phase='idle';
  if(shouldAdvance)setForm(form===0?2:0);
});

shell.addEventListener('pointercancel',event=>{
  if(shell.hasPointerCapture(event.pointerId))shell.releasePointerCapture(event.pointerId);
  dragging=false;
  targetScale=1;
  Object.assign(poseTarget,{x:0,y:0,skew:0,stretchX:1,stretchY:1});
  root.dataset.dragging='false';
  root.dataset.phase='idle';
});

renderFloatingChat(true);
applyEasyParameters();
createOpticalProgram();
updateDesiredGeometry(0,geometry);
ensureAnimationLoop();
postChatAction('window.ready',{form});

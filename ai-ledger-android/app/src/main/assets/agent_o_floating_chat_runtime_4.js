
  function renderBridgeStatus(){
    const connected=chatState.bridgeConnected||bridgeIsAvailable();
    const status=chatCopy.querySelector('.bridge-status');
    status.dataset.state=connected?'connected':'preview';
    status.title=connected?'已连接原生聊天状态':'当前使用网页预览数据';
    status.querySelector('span').textContent=connected?'已连接':'预览';
  }
  function renderToolbar(){
    const workspace=chatCopy.querySelector('.workspace-button');
    const agent=chatCopy.querySelector('.agent-button');
    const online=chatCopy.querySelector('.online-button');
    workspace.setAttribute('aria-pressed',String(Boolean(chatState.workspaceEnabled)));
    agent.setAttribute('aria-pressed',String(Boolean(chatState.agentEnabled)));
    online.setAttribute('aria-pressed',String(Boolean(chatState.onlineEnabled)));
    online.querySelector('.toggle-label').textContent=chatState.onlineEnabled?'开':'关';
    chatCopy.querySelector('.clear-chat-button').disabled=!chatState.messages.length;
    renderBridgeStatus();
  }
  function appendTextElement(parent,tag,className,text){
    const element=document.createElement(tag);element.className=className;element.textContent=text;parent.appendChild(element);return element;
  }
  function renderMessages(forceBottom=false){
    const wasNearBottom=chatMessageViewport.scrollHeight-chatMessageViewport.scrollTop-chatMessageViewport.clientHeight<42;
    const messages=chatState.messages.length?chatState.messages:[{
      id:'welcome-empty',role:'assistant',text:'新的对话已经准备好。你可以让我整理页面信息、回答问题，或继续完成当前操作。',status:'sent',source:'GUI Plus',modelLabel:chatState.selectedModelLabel,virtual:true
    }];
    const renderedRows=[...chatMessageList.querySelectorAll('.message-row')];
    const canPatchTextOnly=renderedRows.length===messages.length&&messages.every((message,index)=>{
      const row=renderedRows[index];
      return row.dataset.messageId===message.id&&row.dataset.status===(message.status||'sent');
    });
    if(canPatchTextOnly){
      messages.forEach((message,index)=>{
        const textElement=renderedRows[index].querySelector('.message-text');
        const nextText=message.text||message.errorText||'';
        if(textElement&&textElement.textContent!==nextText)textElement.textContent=nextText;
      });
      requestAnimationFrame(()=>{if(forceBottom||wasNearBottom)scrollMessagesToBottom(false);updateScrollLatestButton();});
      return;
    }
    chatMessageList.replaceChildren();
    const lastAssistant=[...messages].reverse().find(item=>item.role==='assistant'&&item.status!=='sending');
    messages.forEach(message=>{
      const row=document.createElement('article');row.className='message-row';row.dataset.role=message.role;row.dataset.messageId=message.id;row.dataset.status=message.status||'sent';
      const bubble=document.createElement('div');bubble.className='message-bubble';
      const textElement=appendTextElement(bubble,'p','message-text',message.text||message.errorText||'');
      if(message.role==='user')textElement.style.paddingRight='30px';
      if(Array.isArray(message.attachments)&&message.attachments.length){
        const attachmentList=document.createElement('div');attachmentList.className='message-attachments';
        message.attachments.forEach(item=>appendTextElement(attachmentList,'span','message-attachment',item.fileName||'视觉附件'));
        bubble.appendChild(attachmentList);
      }
      if(message.structuredData){
        const card=document.createElement('div');card.className='message-data-card';
        appendTextElement(card,'strong','',String(message.structuredData.title||'结构化结果'));
        if(message.structuredData.subtitle)appendTextElement(card,'span','',String(message.structuredData.subtitle));
        const metrics=Array.isArray(message.structuredData.metrics)?message.structuredData.metrics.slice(0,4):[];
        if(metrics.length){const metricRow=document.createElement('div');metricRow.className='message-metrics';metrics.forEach(metric=>appendTextElement(metricRow,'em','',`${metric.label||''} ${metric.value||''}${metric.unit?` ${metric.unit}`:''}`.trim()));card.appendChild(metricRow);}
        bubble.appendChild(card);
      }
      if(Array.isArray(message.webSources)&&message.webSources.length){
        const sources=document.createElement('div');sources.className='message-sources';
        message.webSources.slice(0,3).forEach((source,index)=>appendTextElement(sources,'span','',String(source.title||source.domain||`来源 ${index+1}`)));
        bubble.appendChild(sources);
      }
      const actions=document.createElement('div');actions.className='message-actions';
      if(!message.virtual){
        const copy=document.createElement('button');copy.type='button';copy.textContent='复制';copy.dataset.messageAction='copy';copy.dataset.messageId=message.id;actions.appendChild(copy);
      }
      if(!message.virtual&&message.role==='assistant'&&lastAssistant&&lastAssistant.id===message.id&&message.status!=='sending'){
        const retry=document.createElement('button');retry.type='button';retry.textContent='重试';retry.dataset.messageAction='retry';retry.dataset.messageId=message.id;actions.appendChild(retry);
      }
      bubble.appendChild(actions);
      if(message.role==='assistant'){
        const meta=document.createElement('div');meta.className='message-meta';
        const dot=document.createElement('i');meta.appendChild(dot);
        const label=[message.source||'自动选择',message.modelLabel||chatState.selectedModelLabel].filter(Boolean).join(' · ');
        meta.appendChild(document.createTextNode(label));
        if(message.status&&message.status!=='sent'){
          const status=document.createElement('span');status.className='message-status';status.dataset.status=message.status;
          status.textContent=message.status==='sending'?'生成中':message.status==='failed'?'失败':'已停止';meta.appendChild(status);
        }
        bubble.appendChild(meta);
      }
      row.appendChild(bubble);chatMessageList.appendChild(row);
    });
    requestAnimationFrame(()=>{
      if(forceBottom||wasNearBottom)scrollMessagesToBottom(false);
      updateScrollLatestButton();
    });
  }
  function renderQuickPanels(){
    const memoryList=chatCopy.querySelector('.memory-list');memoryList.replaceChildren();
    const memories=chatState.memory&&Array.isArray(chatState.memory.items)?chatState.memory.items:[];
    chatCopy.querySelector('.memory-summary').textContent=chatState.memory&&chatState.memory.loading?'正在同步':memories.length?`本轮生效 ${memories.filter(item=>item.active!==false).length} 项`:'暂无记忆';
    memories.forEach(item=>{
      const row=document.createElement('div');row.className='quick-panel-item';row.style.setProperty('--item-accent',item.accent||'#8dfff4');
      row.innerHTML='<i aria-hidden="true"></i>';
      const copy=document.createElement('div');appendTextElement(copy,'strong','',String(item.title||'记忆'));appendTextElement(copy,'span','',String(item.content||''));row.appendChild(copy);appendTextElement(row,'em','',item.active===false?'停用':'生效');memoryList.appendChild(row);
    });
    if(!memories.length)appendTextElement(memoryList,'div','chat-empty','还没有可显示的记忆');
    const skillList=chatCopy.querySelector('.skill-list');skillList.replaceChildren();
    const skills=chatState.skills&&Array.isArray(chatState.skills.items)?chatState.skills.items:[];
    chatCopy.querySelector('.skill-summary').textContent=chatState.skills&&chatState.skills.loading?'正在刷新':skills.length?`可运行 ${skills.filter(item=>item.enabled!==false).length} 个`:'暂无可运行 Skill';
    skills.forEach(item=>{
      const row=document.createElement('button');row.type='button';row.className='quick-panel-item';row.dataset.chatAction='skill.run';row.dataset.skillId=String(item.id||'');row.disabled=item.enabled===false;row.style.setProperty('--item-accent','#ffd66e');row.innerHTML='<i aria-hidden="true"></i>';
      const copy=document.createElement('div');appendTextElement(copy,'strong','',String(item.title||'Skill'));appendTextElement(copy,'span','',String(item.description||''));row.appendChild(copy);appendTextElement(row,'em','',item.enabled===false?'待批准':'运行');skillList.appendChild(row);
    });
    if(!skills.length)appendTextElement(skillList,'div','chat-empty','还没有可运行的 Skill');
  }
  function resizeComposer(){
    composerInput.style.height='auto';
    composerInput.style.height=`${Math.min(76,Math.max(29,composerInput.scrollHeight))}px`;
  }
  function renderComposer(){
    if(composerInput.value!==chatState.composerText)composerInput.value=chatState.composerText||'';
    composerForm.dataset.hasText=String(Boolean((chatState.composerText||'').trim()));
    composerForm.dataset.sending=String(Boolean(chatState.isSending));
    composerInput.disabled=Boolean(chatState.isSending);
    composerSend.disabled=!chatState.isSending&&!(chatState.composerText||'').trim()&&!chatState.attachment;
    composerSend.setAttribute('aria-label',chatState.isSending?'停止生成':'发送消息');
    const attachmentView=chatCopy.querySelector('.composer-attachment');
    attachmentView.hidden=!chatState.attachment;
    if(chatState.attachment){
      attachmentView.querySelector('.attachment-name').textContent=String(chatState.attachment.fileName||chatState.attachment.name||'视觉附件');
      attachmentView.querySelector('.attachment-status').textContent=String(chatState.attachment.statusLabel||chatState.attachment.status||'已准备');
    }
    resizeComposer();
  }
  function renderFloatingChat(forceBottom=false){renderToolbar();renderMessages(forceBottom);renderQuickPanels();renderComposer();}
  function scrollMessagesToBottom(smooth=true){chatMessageViewport.scrollTo({top:chatMessageViewport.scrollHeight,behavior:smooth?'smooth':'auto'});}
  function updateScrollLatestButton(){
    const distance=chatMessageViewport.scrollHeight-chatMessageViewport.scrollTop-chatMessageViewport.clientHeight;
    scrollLatestButton.hidden=distance<46;
  }
  async function copyChatText(text){
    try{
      if(navigator.clipboard&&window.isSecureContext)await navigator.clipboard.writeText(text);
      else{
        const helper=document.createElement('textarea');helper.value=text;helper.style.cssText='position:fixed;opacity:0;pointer-events:none';document.body.appendChild(helper);helper.select();document.execCommand('copy');helper.remove();
      }
      showChatToast('已复制');
    }catch(error){showChatToast('复制失败');}
  }
  function stopMockReply(){clearTimeout(mockReplyTimer);mockReplyTimer=0;}
  function runMockSend(){ showChatToast('原生聊天桥未连接'); }

(() => {
  const app=document.getElementById('app');
  const cursor=document.getElementById('cursor');
  const bubbleTitle=document.getElementById('bubbleTitle');
  const thought=document.getElementById('thought');
  const confidence=document.getElementById('confidence');
  const coords=document.getElementById('coords');
  const actionSource=document.getElementById('actionSource');
  const topTitle=document.getElementById('topTitle');
  const topMeta=document.getElementById('topMeta');
  const debugStep=document.getElementById('debugStep');
  const debugPoint=document.getElementById('debugPoint');
  const debugLatency=document.getElementById('debugLatency');
  const phaseNames=[
    ['正在观察页面','Step 1 / 5','OBSERVE'],
    ['正在分析目标','Step 2 / 5','ANALYZE'],
    ['正在移动光标','Step 3 / 5','MOVE_POINTER'],
    ['正在执行点击','Step 4 / 5','TAP'],
    ['正在验证结果','Step 5 / 5','VERIFY']
  ];
  let lastClickRevision=-1;
  let phaseTimer=0;

  function setPoint(xNorm,yNorm){
    const x=Math.max(0,Math.min(1,Number(xNorm)||0))*innerWidth;
    const y=Math.max(0,Math.min(1,Number(yNorm)||0))*innerHeight;
    document.documentElement.style.setProperty('--cursor-x',x+'px');
    document.documentElement.style.setProperty('--cursor-y',y+'px');
    const cx=Math.round(x),cy=Math.round(y);
    coords.textContent=`${cx}, ${cy}`;
    debugPoint.textContent=`screen_point: (${cx}, ${cy})`;
  }

  function setPhase(index){
    const i=Math.max(0,Math.min(4,Number(index)||0));
    const n=phaseNames[i];
    topTitle.textContent=n[0];
    topMeta.textContent=n[1];
    debugStep.textContent=`step_id: 0${i+1} / ${n[2]}`;
    document.querySelectorAll('.phase').forEach((p,k)=>{
      p.classList.toggle('done',k<i);
      p.classList.toggle('active',k===i);
    });
  }

  function clickPulse(){
    cursor.classList.remove('clicking');
    void cursor.offsetWidth;
    cursor.classList.add('clicking');
  }

  window.VisualHud={
    update(payload){
      const p=typeof payload==='string'?JSON.parse(payload):payload;
      clearTimeout(phaseTimer);
      app.classList.toggle('hud-live',!!p.visible);
      if(!p.visible)return;
      setPoint(p.xNorm,p.yNorm);
      setPhase(p.phase);
      if(p.title)topTitle.textContent=p.title;
      if(p.meta)topMeta.textContent=p.meta;
      bubbleTitle.textContent=p.bubbleTitle||p.currentAction||'正在执行视觉任务';
      thought.textContent=p.thought||p.result||'正在根据页面证据选择下一步操作。';
      confidence.textContent=p.confidence||'—';
      actionSource.textContent=p.actionSource||'视觉识别';
      debugLatency.textContent=p.debugLatency||'latency_total: —';
      if(Number(p.autoClickAfterMs)>0){
        phaseTimer=setTimeout(()=>setPhase(3),Number(p.autoClickAfterMs));
      }
      if(Number(p.clickRevision)>lastClickRevision){
        lastClickRevision=Number(p.clickRevision);
        if(lastClickRevision>0)clickPulse();
      }
    },
    hide(){
      clearTimeout(phaseTimer);
      app.classList.remove('hud-live');
    }
  };
})();

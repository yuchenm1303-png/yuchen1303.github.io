function movingAverage(candles,window){
  const out=Array(candles.length).fill(null);let sum=0;
  candles.forEach((c,index)=>{sum+=c.close;if(index>=window)sum-=candles[index-window].close;if(index>=window-1)out[index]=sum/window});return out;
}
function klineWindow(candles){
  if(!candles.length)return {start:0,end:0,visible:[]};
  const base=Math.min(state.kBaseCount,candles.length),minimum=Math.min(12,candles.length),count=Math.max(minimum,Math.min(candles.length,Math.round(base/state.kZoom))),maxPan=Math.max(0,candles.length-count);
  state.kPan=Math.max(0,Math.min(maxPan,state.kPan));const end=Math.max(count,Math.min(candles.length,candles.length-Math.round(state.kPan))),start=Math.max(0,end-count);return {start,end,visible:candles.slice(start,end),count,maxPan};
}
function drawKlineChart(){
  const {ctx,width,height}=canvasContext();ctx.clearRect(0,0,width,height);
  const candles=currentKlines(),windowData=klineWindow(candles),visible=windowData.visible,volumeHeight=height*state.volumeFraction,gap=8,chartHeight=height-volumeHeight-gap,volumeTop=chartHeight+gap;
  drawGrid(ctx,width,chartHeight,5);ctx.strokeStyle='rgba(255,255,255,.14)';ctx.beginPath();ctx.moveTo(0,volumeTop);ctx.lineTo(width,volumeTop);ctx.stroke();
  if(visible.length<2){ctx.fillStyle='rgba(255,255,255,.42)';ctx.font='12px system-ui';ctx.textAlign='center';ctx.fillText(`等待真实${state.selectedTab}数据`,width/2,chartHeight/2);setAxis([{label:'--',x:0},{label:'--',x:.5},{label:'--',x:1}]);renderCaption(['MA5 / MA10','成交量','滚轮缩放 · 拖拽']);return}
  const rawMin=Math.min(...visible.map(c=>c.low)),rawMax=Math.max(...visible.map(c=>c.high)),pad=Math.max((rawMax-rawMin)*.06,rawMax*.0015,.01),bottom=rawMin-pad,top=rawMax+pad,range=Math.max(top-bottom,.0001);
  const step=width/visible.length,bodyWidth=Math.max(2.5,Math.min(14,step*.58)),maxVolume=Math.max(...visible.map(c=>c.volume),1),x=i=>i*step+step/2,y=v=>chartHeight-(v-bottom)/range*chartHeight;
  visible.forEach((c,index)=>{const cx=x(index),rising=c.close>=c.open,color=rising?COLORS.rise:COLORS.fall,highY=y(c.high),lowY=y(c.low),openY=y(c.open),closeY=y(c.close),bodyTop=Math.min(openY,closeY),bodyBottom=Math.max(openY,closeY);ctx.strokeStyle=color;ctx.lineWidth=1.2;ctx.beginPath();ctx.moveTo(cx,highY);ctx.lineTo(cx,lowY);ctx.stroke();ctx.strokeStyle=color;ctx.lineWidth=Math.max(1.6,bodyWidth);ctx.beginPath();ctx.moveTo(cx,bodyTop);ctx.lineTo(cx,Math.max(bodyTop+1,bodyBottom));ctx.stroke();const vTop=volumeTop+volumeHeight*(1-c.volume/maxVolume);ctx.strokeStyle=rising?'rgba(255,143,143,.38)':'rgba(128,247,180,.38)';ctx.lineWidth=bodyWidth*.88;ctx.beginPath();ctx.moveTo(cx,height);ctx.lineTo(cx,vTop);ctx.stroke()});
  const ma5=movingAverage(candles,5),ma10=movingAverage(candles,10);
  function averageLine(series,color,lineWidth){let started=false;ctx.strokeStyle=color;ctx.lineWidth=lineWidth;ctx.lineCap='round';ctx.lineJoin='round';ctx.beginPath();visible.forEach((_,localIndex)=>{const value=series[windowData.start+localIndex];if(value==null)return;const px=x(localIndex),py=y(value);if(!started){ctx.moveTo(px,py);started=true}else ctx.lineTo(px,py)});if(started)ctx.stroke()}
  averageLine(ma5,COLORS.yellow,1.35);averageLine(ma10,COLORS.blue,1.2);
  const activeGlobal=state.kHoverIndex>=0?state.kHoverIndex:state.kSelectedIndex,activeLocal=activeGlobal-windowData.start;
  if(activeLocal>=0&&activeLocal<visible.length){const c=visible[activeLocal],cx=x(activeLocal),cy=y(c.close);ctx.strokeStyle='rgba(141,249,234,.58)';ctx.lineWidth=1;ctx.beginPath();ctx.moveTo(cx,0);ctx.lineTo(cx,chartHeight);ctx.stroke();ctx.strokeStyle='rgba(141,249,234,.38)';ctx.beginPath();ctx.moveTo(0,cy);ctx.lineTo(width,cy);ctx.stroke();$('#chartOverlay').textContent=`${c.date}  开 ${fmt(c.open)}  高 ${fmt(c.high)}  低 ${fmt(c.low)}  收 ${fmt(c.close)}  涨跌 ${c.changePercent}  量 ${formatVolume(c.volume)}`}
  else{$('#chartOverlay').textContent=`${visible.length} 根 · 滚轮缩放 · 拖拽平移 · 双击复位`}
  ctx.font='8px system-ui';ctx.textAlign='right';ctx.textBaseline='middle';ctx.fillStyle='rgba(255,255,255,.48)';ctx.fillText(fmt(top),width-4,9);ctx.fillText(fmt((top+bottom)/2),width-4,chartHeight/2);ctx.fillText(fmt(bottom),width-4,chartHeight-8);
  const mid=visible[Math.floor(visible.length/2)];setAxis([{label:dateLabel(visible[0].date),x:0},{label:dateLabel(mid.date),x:.5},{label:dateLabel(visible.at(-1).date),x:1}]);renderCaption([`MA5 ${fmt(ma5.at(-1))}`,`MA10 ${fmt(ma10.at(-1))}`,`${visible.length}根 · 缩放${state.kZoom.toFixed(2)}x`]);
}
function dateLabel(raw){const text=String(raw??'--');const m=text.match(/(\d{4})[-/.]?(\d{2})[-/.]?(\d{2})/);return m?`${m[2]}-${m[3]}`:text.slice(-5)}

function klineIndexFromPointer(clientX){
  if(isMinuteTab(state.selectedTab))return -1;const candles=currentKlines(),windowData=klineWindow(candles);if(!windowData.visible.length)return -1;const rect=$('#chart').getBoundingClientRect(),local=Math.max(0,Math.min(rect.width-1,clientX-rect.left)),index=Math.floor(local/rect.width*windowData.visible.length);return windowData.start+Math.max(0,Math.min(windowData.visible.length-1,index));
}
function resetKlineInteraction(){state.kZoom=1;state.kPan=0;state.kSelectedIndex=-1;state.kHoverIndex=-1;drawSelectedChart()}
function installChartInteractions(){
  const wrap=$('#chartWrap');
  wrap.addEventListener('wheel',event=>{if(isMinuteTab(state.selectedTab))return;event.preventDefault();const next=state.kZoom*(event.deltaY<0?1.16:.86);state.kZoom=Math.max(1,Math.min(5,next));drawSelectedChart()},{passive:false});
  wrap.addEventListener('pointerdown',event=>{if(isMinuteTab(state.selectedTab))return;wrap.setPointerCapture(event.pointerId);state.dragStartX=event.clientX;state.dragStartPan=state.kPan});
  wrap.addEventListener('pointermove',event=>{if(isMinuteTab(state.selectedTab))return;if(state.dragStartX!=null){const candles=currentKlines(),windowData=klineWindow(candles),step=Math.max(1,wrap.clientWidth/Math.max(windowData.visible.length,1));state.kPan=state.dragStartPan+(event.clientX-state.dragStartX)/step;drawSelectedChart()}else{state.kHoverIndex=klineIndexFromPointer(event.clientX);drawSelectedChart()}});
  wrap.addEventListener('pointerup',event=>{if(isMinuteTab(state.selectedTab))return;state.dragStartX=null;state.kSelectedIndex=klineIndexFromPointer(event.clientX);drawSelectedChart()});
  wrap.addEventListener('pointercancel',()=>{state.dragStartX=null});
  wrap.addEventListener('pointerleave',()=>{if(state.dragStartX==null){state.kHoverIndex=-1;drawSelectedChart()}});
  wrap.addEventListener('dblclick',event=>{if(isMinuteTab(state.selectedTab))return;event.preventDefault();resetKlineInteraction()});
}

function refreshPlan(){
  const parts=Object.fromEntries(new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',weekday:'short',hour:'2-digit',minute:'2-digit',hour12:false}).formatToParts(new Date()).filter(x=>x.type!=='literal').map(x=>[x.type,x.value]));
  const minute=Number(parts.hour)*60+Number(parts.minute),weekday=!['Sat','Sun'].includes(parts.weekday),auction=weekday&&((minute>=554&&minute<=566)||(minute>=896&&minute<=901)),trading=weekday&&minute>=555&&minute<=905;
  return auction?{delay:1000,force:true,label:'集合竞价每秒刷新'}:trading?{delay:5000,force:false,label:'交易时段每5秒刷新'}:{delay:30000,force:false,label:'休市每30秒检查'};
}
function scheduleRealtime(){clearTimeout(state.timer);if(!state.running)return;const plan=refreshPlan();state.timer=setTimeout(()=>loadRealtime(true),document.hidden?Math.max(plan.delay,15000):plan.delay)}
async function fetchJson(url,timeoutMs=18000){
  const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),timeoutMs);
  try{const response=await fetch(url,{signal:controller.signal,cache:'no-store',headers:{'Cache-Control':'no-cache'}});if(!response.ok)throw new Error(`HTTP ${response.status}`);return await response.json()}finally{clearTimeout(timer)}
}
async function loadRealtime(silent=false){
  if(state.loadingRealtime){scheduleRealtime();return}
  state.loadingRealtime=true;const query=currentQuery(),days=isMinuteTab(state.selectedTab)?minuteDaysForTab(state.selectedTab):1,plan=refreshPlan();
  if(!silent)$('#dataStatus').innerHTML='<strong>连接中</strong>：正在读取真实行情…';
  try{
    const root=await fetchJson(`${REALTIME_API}?query=${encodeURIComponent(query)}&ndays=${days}&forceAuction=${plan.force}&_=${Date.now()}`);
    const frame=normalizeRealtimePayload(root);applyRealtimeFrame(frame,days);state.requestCount++;state.lastRealtimeError='';renderAll();
    const time=new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(new Date());
    $('#dataStatus').innerHTML=`<strong>真实后端 · ${plan.label}</strong>\n${state.quote.name} ${state.quote.code} · ${time}\n当前页签 ${state.selectedTab} · 分时点 ${frame.minutePoints.length}\n盘口 ${frame.sellLevels.length}/${frame.buyLevels.length} · 逐笔 ${frame.ticks.length}\n数据源 ${frame.sourceLabel}\n请求 ${state.requestCount} 次`;
  }catch(error){state.lastRealtimeError=error.name==='AbortError'?'请求超时':error.message;$('#dataStatus').innerHTML=`<strong>实时刷新失败</strong>：${state.lastRealtimeError}\n保留上一份真实成功数据。`}
  finally{state.loadingRealtime=false;scheduleRealtime()}
}
async function loadKline(tab=state.selectedTab,force=false){
  if(isMinuteTab(tab))return;const query=currentQuery(),period=periodForTab(tab),key=`${query}:${period}`;
  if(!force&&state.klineCache.has(key)){state.activeCode=query;renderAll();return}
  if(state.loadingKline)return;state.loadingKline=true;$('#auctionStatus').textContent=`正在加载真实${tab}`;
  try{
    let root;
    try{root=await fetchJson(`${KLINE_API}?query=${encodeURIComponent(query)}&period=${period}&limit=160&_=${Date.now()}`,20000)}
    catch(primaryError){root=await fetchJson(`${KLINE_FALLBACK_API}?query=${encodeURIComponent(query)}&period=${period}&limit=160&_=${Date.now()}`,20000)}
    const candles=normalizeKlinePayload(root);if(candles.length<2)throw new Error(`${tab}接口返回数据不足`);
    const codeKey=state.quote.code||query;state.klineCache.set(key,candles);state.klineCache.set(`${codeKey}:${period}`,candles);state.activeCode=codeKey;state.kZoom=1;state.kPan=0;state.kSelectedIndex=-1;state.kHoverIndex=-1;renderAll();
    $('#dataStatus').innerHTML+=`\n${tab}加载完成：${candles.length} 根真实K线`;
  }catch(error){$('#auctionStatus').textContent=`${tab}加载失败`;$('#dataStatus').innerHTML+=`\n${tab}加载失败：${error.name==='AbortError'?'请求超时':error.message}`;drawSelectedChart()}
  finally{state.loadingKline=false}
}
async function selectTab(tab){
  if(!['分时','日K','周K','月K','五日'].includes(tab))return;
  state.selectedTab=tab;state.kZoom=1;state.kPan=0;state.kSelectedIndex=-1;state.kHoverIndex=-1;renderAll();
  if(isMinuteTab(tab)){await loadRealtime(false)}else{await Promise.all([loadRealtime(true),loadKline(tab)])}
}
async function startAll(){
  state.running=true;state.activeCode=currentQuery();state.minuteCache.clear();state.klineCache.clear();await loadRealtime(false);if(!isMinuteTab(state.selectedTab))await loadKline(state.selectedTab,true)
}

$$('.pill').forEach(button=>button.addEventListener('click',()=>selectTab(button.dataset.tab)));
$('#start').addEventListener('click',startAll);
$('#stop').addEventListener('click',()=>{state.running=false;clearTimeout(state.timer);$('#dataStatus').innerHTML+='\n已停止自动刷新。'});
$('#query').addEventListener('keydown',event=>{if(event.key==='Enter')startAll()});
document.addEventListener('visibilitychange',()=>{if(!document.hidden&&state.running)loadRealtime(true);else scheduleRealtime()});
window.addEventListener('resize',()=>requestAnimationFrame(drawSelectedChart));
new ResizeObserver(()=>drawSelectedChart()).observe($('#chartWrap'));
$('#mobileToggle').addEventListener('click',()=>document.body.classList.toggle('controls-open'));
$('#chartHeight').addEventListener('input',event=>{rootStyle.setProperty('--chart-h',`${event.target.value}px`);$('#chartHeightText').textContent=`${event.target.value}px`;drawSelectedChart()});
$('#orderWidth').addEventListener('input',event=>{rootStyle.setProperty('--order-w',`${event.target.value}px`);$('#orderWidthText').textContent=`${event.target.value}px`;drawSelectedChart()});
$('#volumeHeight').addEventListener('input',event=>{state.volumeFraction=Number(event.target.value)/100;$('#volumeHeightText').textContent=`${event.target.value}%`;drawSelectedChart()});
$('#kBaseCount').addEventListener('input',event=>{state.kBaseCount=Number(event.target.value);$('#kBaseCountText').textContent=`${event.target.value}根`;state.kPan=0;drawSelectedChart()});
installChartInteractions();renderAll();setTimeout(startAll,250);

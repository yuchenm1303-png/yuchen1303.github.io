'use strict';

state.selectedIndicator = state.selectedIndicator || 'MACD';
COLORS.pink = 'rgba(255,114,210,.92)';
COLORS.green = 'rgba(72,201,149,.90)';
COLORS.orange = 'rgba(255,144,96,.92)';

function normalizeKlinePayloadEnhanced(root){
  const rows=firstArray(root,['kLinePoints','klinePoints','klines','kLines']);
  return rows.map(item=>{
    const open=number(item?.open??item?.o),close=number(item?.close??item?.c??item?.price);
    if(open==null||close==null)return null;
    return{
      date:String(item?.date??item?.day??item?.time??''),open,close,
      high:number(item?.high??item?.h)??close,low:number(item?.low??item?.l)??close,
      volume:number(item?.volume??item?.vol)??0,amount:number(item?.amount)??0,
      amplitude:String(item?.amplitude??item?.amp??'--'),changePercent:String(item?.changePercent??item?.pct??'--'),
      changeAmount:String(item?.changeAmount??item?.change??'--'),turnoverRate:String(item?.turnoverRate??item?.turnover??'--')
    };
  }).filter(Boolean);
}
function klineWindow(candles){
  if(!candles.length)return{start:0,end:0,visible:[]};
  const base=Math.min(state.kBaseCount,candles.length),minimum=Math.min(12,candles.length),count=Math.max(minimum,Math.min(candles.length,Math.round(base/state.kZoom))),maxPan=Math.max(0,candles.length-count);
  state.kPan=Math.max(0,Math.min(maxPan,state.kPan));
  const end=Math.max(count,Math.min(candles.length,candles.length-Math.round(state.kPan))),start=Math.max(0,end-count);
  return{start,end,visible:candles.slice(start,end),count,maxPan};
}
function displayWire(value,suffix=''){
  const text=String(value??'--').trim();
  if(!text||text==='--')return'--';
  return suffix&&!text.includes(suffix)?`${text}${suffix}`:text;
}
function formatMoney(value){
  const n=number(value);if(n==null)return'--';
  if(Math.abs(n)>=1e8)return`${(n/1e8).toFixed(2)}亿`;
  if(Math.abs(n)>=1e4)return`${(n/1e4).toFixed(2)}万`;
  return`${Math.round(n)}`;
}
function clamp(value,min,max){return Math.max(min,Math.min(max,value))}
function computeKlinePanelLayout(height){
  const gap=clamp(height*.012,4,8);
  const usable=Math.max(1,height-gap*2);
  const volumeRatio=clamp(state.volumeFraction,.16,.34);
  const indicatorRatio=.24;
  const mainRatio=Math.max(.40,1-volumeRatio-indicatorRatio);
  const ratioTotal=mainRatio+volumeRatio+indicatorRatio;
  const mainHeight=usable*mainRatio/ratioTotal;
  const volumeHeight=usable*volumeRatio/ratioTotal;
  const indicatorHeight=Math.max(1,usable-mainHeight-volumeHeight);
  return{
    gap,
    mainHeight,
    volumeHeight,
    indicatorHeight,
    volumeTop:mainHeight+gap,
    indicatorTop:mainHeight+gap+volumeHeight+gap
  };
}
function renderTechnicalChrome(){
  const isKline=!isMinuteTab(state.selectedTab),card=$('#chartCard'),toolbar=$('#klineTools');
  card?.classList.toggle('kline-active',isKline);
  if(!isKline){if(toolbar)toolbar.innerHTML='';return}
  const candles=currentKlines(),ma5=movingAverage(candles,5),ma10=movingAverage(candles,10),ma20=movingAverage(candles,20),ma30=movingAverage(candles,30);
  if(toolbar)toolbar.innerHTML=`<span class="tool-label">均线</span><span class="yellow">MA5 ${fmt(latestFinite(ma5))}</span><span class="ma-blue">MA10 ${fmt(latestFinite(ma10))}</span><span class="ma-pink">MA20 ${fmt(latestFinite(ma20))}</span><span class="ma-green">MA30 ${fmt(latestFinite(ma30))}</span><span class="tool-spacer"></span><span class="tool-muted">前复权</span><span class="tool-muted">${candles.length}根</span>`;
  $$('.indicator-button').forEach(button=>button.classList.toggle('active',button.dataset.indicator===state.selectedIndicator));
}
function renderAll(){renderTabs();renderQuote();renderOrderFlow();renderLegend();renderTechnicalChrome();requestAnimationFrame(drawSelectedChart)}

function drawKlineChart(){
  const{ctx,width,height}=canvasContext();ctx.clearRect(0,0,width,height);
  const candles=currentKlines(),windowData=klineWindow(candles),visible=windowData.visible;
  const layout=computeKlinePanelLayout(height),{gap,mainHeight,volumeHeight,indicatorHeight,volumeTop,indicatorTop}=layout;
  rootStyle.setProperty('--indicator-switch-top',`${Math.round(indicatorTop+3)}px`);

  drawPanelGrid(ctx,width,0,mainHeight,4,5);
  drawPanelGrid(ctx,width,volumeTop,volumeHeight,2,5);
  drawPanelGrid(ctx,width,indicatorTop,indicatorHeight,2,5);
  ctx.strokeStyle='rgba(255,255,255,.16)';ctx.beginPath();ctx.moveTo(0,volumeTop-gap/2);ctx.lineTo(width,volumeTop-gap/2);ctx.moveTo(0,indicatorTop-gap/2);ctx.lineTo(width,indicatorTop-gap/2);ctx.stroke();

  if(visible.length<2){
    ctx.fillStyle='rgba(255,255,255,.42)';ctx.font='12px system-ui';ctx.textAlign='center';ctx.fillText(`等待真实${state.selectedTab}数据`,width/2,mainHeight/2);
    setAxis([{label:'--',x:0},{label:'--',x:.5},{label:'--',x:1}]);renderCaption(['MA5/10/20/30','成交量M5/M10',state.selectedIndicator]);return;
  }

  const ma5=movingAverage(candles,5),ma10=movingAverage(candles,10),ma20=movingAverage(candles,20),ma30=movingAverage(candles,30),boll=calculateBoll(candles);
  const rangeValues=visible.flatMap(c=>[c.low,c.high]);
  if(state.selectedIndicator==='BOLL'){
    for(let index=windowData.start;index<windowData.end;index++){
      if(Number.isFinite(boll.upper[index]))rangeValues.push(boll.upper[index]);
      if(Number.isFinite(boll.lower[index]))rangeValues.push(boll.lower[index]);
    }
  }
  const rawMin=Math.min(...rangeValues),rawMax=Math.max(...rangeValues),padding=Math.max((rawMax-rawMin)*.06,rawMax*.0015,.01),bottom=rawMin-padding,top=rawMax+padding,range=Math.max(top-bottom,.0001);
  const step=width/visible.length,bodyWidth=Math.max(2.2,Math.min(12,step*.58)),x=index=>index*step+step/2,y=value=>mainHeight-(value-bottom)/range*mainHeight;

  ctx.save();clipRect(ctx,0,0,width,mainHeight);
  visible.forEach((c,index)=>{
    const cx=x(index),rising=c.close>=c.open,color=rising?COLORS.rise:COLORS.fall,highY=y(c.high),lowY=y(c.low),openY=y(c.open),closeY=y(c.close),bodyTop=Math.min(openY,closeY),bodyBottom=Math.max(openY,closeY);
    ctx.strokeStyle=color;ctx.lineWidth=1.1;ctx.beginPath();ctx.moveTo(cx,highY);ctx.lineTo(cx,lowY);ctx.stroke();
    ctx.strokeStyle=color;ctx.lineWidth=Math.max(1.6,bodyWidth);ctx.beginPath();ctx.moveTo(cx,bodyTop);ctx.lineTo(cx,Math.max(bodyTop+1,bodyBottom));ctx.stroke();
  });
  drawSeries(ctx,ma5,windowData,x,y,COLORS.yellow,1.45);
  drawSeries(ctx,ma10,windowData,x,y,COLORS.blue,1.25);
  drawSeries(ctx,ma20,windowData,x,y,COLORS.pink,1.15);
  drawSeries(ctx,ma30,windowData,x,y,COLORS.green,1.15);
  if(state.selectedIndicator==='BOLL'){
    drawSeries(ctx,boll.upper,windowData,x,y,COLORS.orange,1.05);
    drawSeries(ctx,boll.mid,windowData,x,y,'rgba(255,255,255,.78)',1.05);
    drawSeries(ctx,boll.lower,windowData,x,y,COLORS.orange,1.05);
  }
  ctx.restore();

  const volumes=numericSeries(candles,'volume'),vma5=simpleMovingAverage(volumes,5),vma10=simpleMovingAverage(volumes,10),maxVolume=Math.max(...visible.map(c=>c.volume),1),volumeY=value=>volumeTop+volumeHeight-(value/maxVolume)*volumeHeight*.76;
  ctx.save();clipRect(ctx,0,volumeTop,width,volumeHeight);
  visible.forEach((c,index)=>{
    const cx=x(index),rising=c.close>=c.open;
    ctx.strokeStyle=rising?'rgba(255,143,143,.62)':'rgba(128,247,180,.62)';ctx.lineWidth=Math.max(1.3,bodyWidth*.82);ctx.beginPath();ctx.moveTo(cx,volumeTop+volumeHeight);ctx.lineTo(cx,volumeY(c.volume));ctx.stroke();
  });
  drawSeries(ctx,vma5,windowData,x,volumeY,'rgba(255,255,255,.90)',1.1);
  drawSeries(ctx,vma10,windowData,x,volumeY,COLORS.orange,1.1);
  ctx.restore();

  const latest=visible.at(-1),latestGlobal=windowData.end-1;
  ctx.font='7.5px system-ui';ctx.textBaseline='top';ctx.textAlign='left';ctx.fillStyle='rgba(255,255,255,.86)';ctx.fillText('成交量',5,volumeTop+4);
  ctx.fillStyle=latest.close>=latest.open?COLORS.rise:COLORS.fall;ctx.fillText(`量 ${formatVolume(latest.volume)}`,43,volumeTop+4);
  ctx.fillStyle='rgba(255,255,255,.78)';ctx.fillText(`M5 ${formatVolume(vma5[latestGlobal])}`,Math.min(width*.29,118),volumeTop+4);
  ctx.fillStyle=COLORS.orange;ctx.fillText(`M10 ${formatVolume(vma10[latestGlobal])}`,Math.min(width*.51,205),volumeTop+4);
  ctx.textAlign='right';ctx.fillStyle=COLORS.blue;ctx.fillText(`换手 ${displayWire(latest.turnoverRate,'%')}`,width-5,volumeTop+4);

  const snapshot=indicatorSnapshot(candles,state.selectedIndicator);
  drawIndicatorPanel(ctx,width,indicatorTop,indicatorHeight,windowData,x,snapshot);

  const activeGlobal=state.kHoverIndex>=0?state.kHoverIndex:state.kSelectedIndex,activeLocal=activeGlobal-windowData.start;
  if(activeLocal>=0&&activeLocal<visible.length){
    const c=visible[activeLocal],cx=x(activeLocal),cy=y(c.close);
    ctx.strokeStyle='rgba(141,249,234,.58)';ctx.lineWidth=1;ctx.beginPath();ctx.moveTo(cx,0);ctx.lineTo(cx,height-2);ctx.stroke();
    ctx.strokeStyle='rgba(141,249,234,.38)';ctx.beginPath();ctx.moveTo(0,cy);ctx.lineTo(width,cy);ctx.stroke();
    $('#chartOverlay').textContent=`${c.date}  开${fmt(c.open)} 高${fmt(c.high)} 低${fmt(c.low)} 收${fmt(c.close)} 涨跌${displayWire(c.changePercent,'%')} 振幅${displayWire(c.amplitude,'%')} 换手${displayWire(c.turnoverRate,'%')} 量${formatVolume(c.volume)} 额${formatMoney(c.amount)}`;
  }else{$('#chartOverlay').textContent=`${visible.length}根 · 主图${Math.round(mainHeight)}px · 成交量${Math.round(volumeHeight)}px · 指标${Math.round(indicatorHeight)}px`}

  ctx.font='8px system-ui';ctx.textAlign='right';ctx.textBaseline='middle';ctx.fillStyle='rgba(255,255,255,.48)';ctx.fillText(fmt(top),width-4,9);ctx.fillText(fmt((top+bottom)/2),width-4,mainHeight/2);ctx.fillText(fmt(bottom),width-4,mainHeight-8);
  const mid=visible[Math.floor(visible.length/2)];setAxis([{label:dateLabel(visible[0].date),x:0},{label:dateLabel(mid.date),x:.5},{label:dateLabel(visible.at(-1).date),x:1}]);
  renderCaption([`MA20 ${fmt(latestFinite(ma20))} · MA30 ${fmt(latestFinite(ma30))}`,`量M5 ${formatVolume(latestFinite(vma5))} · M10 ${formatVolume(latestFinite(vma10))}`,`${snapshot.label} · 缩放${state.kZoom.toFixed(2)}x`]);
}
function clipRect(ctx,x,y,width,height){ctx.beginPath();ctx.rect(x,y,width,Math.max(1,height));ctx.clip()}
function drawPanelGrid(ctx,width,top,height,horizontal,vertical){
  for(let index=1;index<=horizontal;index++){const yy=top+height*index/(horizontal+1);ctx.strokeStyle='rgba(255,255,255,.085)';ctx.lineWidth=1;ctx.beginPath();ctx.moveTo(0,yy);ctx.lineTo(width,yy);ctx.stroke()}
  for(let index=1;index<=vertical;index++){const xx=width*index/(vertical+1);ctx.strokeStyle='rgba(255,255,255,.05)';ctx.beginPath();ctx.moveTo(xx,top);ctx.lineTo(xx,top+height);ctx.stroke()}
}
function drawSeries(ctx,series,windowData,x,y,color,lineWidth){
  let hasPoint=false,penDown=false;ctx.strokeStyle=color;ctx.lineWidth=lineWidth;ctx.lineCap='round';ctx.lineJoin='round';ctx.beginPath();
  windowData.visible.forEach((_,localIndex)=>{
    const value=series[windowData.start+localIndex];
    if(!Number.isFinite(value)){penDown=false;return}
    const px=x(localIndex),py=y(value);
    if(!penDown){ctx.moveTo(px,py);penDown=true}else ctx.lineTo(px,py);
    hasPoint=true;
  });
  if(hasPoint)ctx.stroke();
}
function drawIndicatorPanel(ctx,width,top,height,windowData,x,snapshot){
  const valuesText=snapshot.values.map(([name,value])=>`${name}:${fmt(value,3)}`).join('  ');
  drawFittedText(ctx,`${snapshot.label}  ${valuesText}`,6,top+25,width-12,8,6.2);
  const plotTop=top+41,plotHeight=Math.max(24,height-47),start=windowData.start,end=windowData.end;
  const line=(series,min,max,color,lineWidth=1.15)=>{
    const map=value=>clamp(plotTop+plotHeight-(value-min)/(max-min||1)*plotHeight,plotTop+1,plotTop+plotHeight-1);
    drawSeries(ctx,series,windowData,x,map,color,lineWidth);
  };
  ctx.save();clipRect(ctx,0,plotTop,width,plotHeight);
  if(state.selectedIndicator==='KDJ'){
    const all=[...snapshot.data.k.slice(start,end),...snapshot.data.d.slice(start,end),...snapshot.data.j.slice(start,end)].filter(Number.isFinite),rawMin=Math.min(0,...all),rawMax=Math.max(100,...all),padding=Math.max((rawMax-rawMin)*.06,3),min=rawMin-padding,max=rawMax+padding;
    drawThreshold(ctx,width,plotTop,plotHeight,min,max,20);drawThreshold(ctx,width,plotTop,plotHeight,min,max,80);
    line(snapshot.data.k,min,max,COLORS.yellow);line(snapshot.data.d,min,max,COLORS.blue);line(snapshot.data.j,min,max,COLORS.pink);ctx.restore();return;
  }
  if(state.selectedIndicator==='RSI'){
    drawThreshold(ctx,width,plotTop,plotHeight,0,100,30);drawThreshold(ctx,width,plotTop,plotHeight,0,100,70);
    line(snapshot.data.rsi6,0,100,COLORS.yellow);line(snapshot.data.rsi12,0,100,COLORS.blue);line(snapshot.data.rsi24,0,100,COLORS.pink);ctx.restore();return;
  }
  if(state.selectedIndicator==='BOLL'){
    const visibleValues=snapshot.data.percentB.slice(start,end).filter(Number.isFinite),rawMin=Math.min(-.1,...visibleValues),rawMax=Math.max(1.1,...visibleValues),padding=Math.max((rawMax-rawMin)*.08,.08),min=rawMin-padding,max=rawMax+padding;
    drawThreshold(ctx,width,plotTop,plotHeight,min,max,0);drawThreshold(ctx,width,plotTop,plotHeight,min,max,1);
    line(snapshot.data.percentB,min,max,COLORS.yellow,1.35);ctx.restore();return;
  }
  const data=snapshot.data,all=[...data.dif.slice(start,end),...data.dea.slice(start,end),...data.histogram.slice(start,end)].filter(Number.isFinite),maxAbs=Math.max(.0001,...all.map(Math.abs)),zeroY=plotTop+plotHeight/2;
  ctx.strokeStyle='rgba(255,255,255,.18)';ctx.beginPath();ctx.moveTo(0,zeroY);ctx.lineTo(width,zeroY);ctx.stroke();
  windowData.visible.forEach((_,localIndex)=>{
    const value=data.histogram[start+localIndex];if(!Number.isFinite(value))return;
    const px=x(localIndex),py=clamp(zeroY-value/maxAbs*(plotHeight/2*.88),plotTop+1,plotTop+plotHeight-1);
    ctx.strokeStyle=value>=0?COLORS.rise:COLORS.fall;ctx.lineWidth=Math.max(1,width/windowData.visible.length*.52);ctx.beginPath();ctx.moveTo(px,zeroY);ctx.lineTo(px,py);ctx.stroke();
  });
  line(data.dif,-maxAbs,maxAbs,COLORS.yellow);line(data.dea,-maxAbs,maxAbs,COLORS.blue);ctx.restore();
}
function drawFittedText(ctx,text,x,y,maxWidth,maxSize=8,minSize=6.2){
  let size=maxSize;ctx.textBaseline='top';ctx.textAlign='left';ctx.fillStyle='rgba(255,255,255,.88)';ctx.font=`${size}px system-ui`;
  while(size>minSize&&ctx.measureText(text).width>maxWidth){size-=.3;ctx.font=`${size}px system-ui`}
  if(ctx.measureText(text).width<=maxWidth){ctx.fillText(text,x,y);return}
  let clipped=text;while(clipped.length>3&&ctx.measureText(`${clipped}…`).width>maxWidth)clipped=clipped.slice(0,-1);ctx.fillText(`${clipped}…`,x,y);
}
function drawThreshold(ctx,width,top,height,min,max,value){
  if(value<min||value>max)return;
  const y=top+height-(value-min)/(max-min||1)*height;ctx.save();ctx.setLineDash([4,4]);ctx.strokeStyle='rgba(255,255,255,.14)';ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(width,y);ctx.stroke();ctx.restore();
}
function dateLabel(raw){const text=String(raw??'--'),match=text.match(/(\d{4})[-/.]?(\d{2})[-/.]?(\d{2})/);return match?`${match[2]}-${match[3]}`:text.slice(-5)}

function klineIndexFromPointer(clientX){
  if(isMinuteTab(state.selectedTab))return-1;
  const candles=currentKlines(),windowData=klineWindow(candles);if(!windowData.visible.length)return-1;
  const rect=$('#chart').getBoundingClientRect(),local=Math.max(0,Math.min(rect.width-1,clientX-rect.left)),index=Math.floor(local/rect.width*windowData.visible.length);
  return windowData.start+Math.max(0,Math.min(windowData.visible.length-1,index));
}
function resetKlineInteraction(){state.kZoom=1;state.kPan=0;state.kSelectedIndex=-1;state.kHoverIndex=-1;drawSelectedChart()}
function installChartInteractions(){
  const wrap=$('#chartWrap');
  wrap.addEventListener('wheel',event=>{if(isMinuteTab(state.selectedTab))return;event.preventDefault();state.kZoom=Math.max(1,Math.min(5,state.kZoom*(event.deltaY<0?1.16:.86)));drawSelectedChart()},{passive:false});
  wrap.addEventListener('pointerdown',event=>{if(isMinuteTab(state.selectedTab)||event.target.closest?.('.indicator-switch'))return;wrap.setPointerCapture(event.pointerId);state.dragStartX=event.clientX;state.dragStartPan=state.kPan});
  wrap.addEventListener('pointermove',event=>{if(isMinuteTab(state.selectedTab))return;if(state.dragStartX!=null){const windowData=klineWindow(currentKlines()),step=Math.max(1,wrap.clientWidth/Math.max(windowData.visible.length,1));state.kPan=state.dragStartPan+(event.clientX-state.dragStartX)/step;drawSelectedChart()}else if(!event.target.closest?.('.indicator-switch')){state.kHoverIndex=klineIndexFromPointer(event.clientX);drawSelectedChart()}});
  wrap.addEventListener('pointerup',event=>{if(isMinuteTab(state.selectedTab)||state.dragStartX==null)return;state.dragStartX=null;state.kSelectedIndex=klineIndexFromPointer(event.clientX);drawSelectedChart()});
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
    const root=await fetchJson(`${REALTIME_API}?query=${encodeURIComponent(query)}&ndays=${days}&forceAuction=${plan.force}&_=${Date.now()}`),frame=normalizeRealtimePayload(root);
    applyRealtimeFrame(frame,days);state.requestCount++;state.lastRealtimeError='';renderAll();
    const time=new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(new Date());
    $('#dataStatus').innerHTML=`<strong>真实后端 · ${plan.label}</strong>\n${state.quote.name} ${state.quote.code} · ${time}\n当前页签 ${state.selectedTab} · 分时点 ${frame.minutePoints.length}\n盘口 ${frame.sellLevels.length}/${frame.buyLevels.length} · 逐笔 ${frame.ticks.length}\n数据源 ${frame.sourceLabel}\n请求 ${state.requestCount} 次`;
  }catch(error){state.lastRealtimeError=error.name==='AbortError'?'请求超时':error.message;$('#dataStatus').innerHTML=`<strong>实时刷新失败</strong>：${state.lastRealtimeError}\n保留上一份真实成功数据。`}
  finally{state.loadingRealtime=false;scheduleRealtime()}
}
async function loadKline(tab=state.selectedTab,force=false){
  if(isMinuteTab(tab))return;
  const query=currentQuery(),period=periodForTab(tab),key=`${query}:${period}`;
  if(!force&&state.klineCache.has(key)){state.activeCode=query;renderAll();return}
  if(state.loadingKline)return;
  state.loadingKline=true;$('#auctionStatus').textContent=`正在加载真实${tab}`;
  try{
    let root;
    try{root=await fetchJson(`${KLINE_API}?query=${encodeURIComponent(query)}&period=${period}&limit=160&_=${Date.now()}`,20000)}
    catch(primaryError){root=await fetchJson(`${KLINE_FALLBACK_API}?query=${encodeURIComponent(query)}&period=${period}&limit=160&_=${Date.now()}`,20000)}
    const candles=normalizeKlinePayloadEnhanced(root);if(candles.length<2)throw new Error(`${tab}接口返回数据不足`);
    const codeKey=state.quote.code||query;state.klineCache.set(key,candles);state.klineCache.set(`${codeKey}:${period}`,candles);state.activeCode=codeKey;state.kZoom=1;state.kPan=0;state.kSelectedIndex=-1;state.kHoverIndex=-1;renderAll();
    $('#dataStatus').innerHTML+=`\n${tab}加载完成：${candles.length}根K线，已计算MA、量均线、MACD、KDJ、RSI、BOLL`;
  }catch(error){$('#auctionStatus').textContent=`${tab}加载失败`;$('#dataStatus').innerHTML+=`\n${tab}加载失败：${error.name==='AbortError'?'请求超时':error.message}`;drawSelectedChart()}
  finally{state.loadingKline=false}
}
async function selectTab(tab){
  if(!['分时','日K','周K','月K','五日'].includes(tab))return;
  state.selectedTab=tab;state.kZoom=1;state.kPan=0;state.kSelectedIndex=-1;state.kHoverIndex=-1;renderAll();
  if(isMinuteTab(tab))await loadRealtime(false);else await Promise.all([loadRealtime(true),loadKline(tab)]);
}
async function startAll(){state.running=true;state.activeCode=currentQuery();state.minuteCache.clear();state.klineCache.clear();await loadRealtime(false);if(!isMinuteTab(state.selectedTab))await loadKline(state.selectedTab,true)}

$$('.pill').forEach(button=>button.addEventListener('click',()=>selectTab(button.dataset.tab)));
$$('.indicator-button').forEach(button=>{
  button.addEventListener('pointerdown',event=>event.stopPropagation());
  button.addEventListener('click',event=>{event.stopPropagation();state.selectedIndicator=button.dataset.indicator||'MACD';renderTechnicalChrome();drawSelectedChart()});
});
$('#start').addEventListener('click',startAll);
$('#stop').addEventListener('click',()=>{state.running=false;clearTimeout(state.timer);$('#dataStatus').innerHTML+='\n已停止自动刷新。'});
$('#query').addEventListener('keydown',event=>{if(event.key==='Enter')startAll()});
document.addEventListener('visibilitychange',()=>{if(!document.hidden&&state.running)loadRealtime(true);else scheduleRealtime()});
window.addEventListener('resize',()=>requestAnimationFrame(drawSelectedChart));
new ResizeObserver(()=>drawSelectedChart()).observe($('#chartWrap'));
$('#mobileToggle').addEventListener('click',()=>document.body.classList.toggle('controls-open'));
$('#chartHeight').addEventListener('input',event=>{if(event.target.disabled)return;rootStyle.setProperty('--chart-h',`${event.target.value}px`);$('#chartHeightText').textContent=`${event.target.value}px`;drawSelectedChart()});
$('#orderWidth').addEventListener('input',event=>{rootStyle.setProperty('--order-w',`${event.target.value}px`);$('#orderWidthText').textContent=`${event.target.value}px`;drawSelectedChart()});
$('#volumeHeight').addEventListener('input',event=>{state.volumeFraction=Number(event.target.value)/100;$('#volumeHeightText').textContent=`${event.target.value}%`;drawSelectedChart()});
$('#kBaseCount').addEventListener('input',event=>{state.kBaseCount=Number(event.target.value);$('#kBaseCountText').textContent=`${event.target.value}根`;state.kPan=0;drawSelectedChart()});
installChartInteractions();renderAll();setTimeout(startAll,250);

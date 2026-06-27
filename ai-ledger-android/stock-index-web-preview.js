'use strict';

const API_BASE = 'https://ai-ledger-stock-proxy.onrender.com';
const INDEX_DETAIL_API = `${API_BASE}/api/stock/a-share/index/detail`;
const VALID_INDEX_CODES = new Set(['000001','399001','399006','000300','000688','000510','000016','000905','000852','899050']);
const $ = selector => document.querySelector(selector);
const state = {
  code: normalizeCode(new URLSearchParams(location.search).get('query')),
  tab: 'minute',
  payload: null,
  loading: false,
  error: '',
  lastSuccessAt: 0,
  requestCount: 0,
  autoRefresh: true,
  timer: null
};

function normalizeCode(value){
  const digits = String(value || '').replace(/\D/g,'');
  return VALID_INDEX_CODES.has(digits) ? digits : '000001';
}
function text(value,fallback='--'){const result=String(value??'').trim();return result&&result!=='null'&&result!=='NaN'?result:fallback}
function number(value){if(typeof value==='number')return Number.isFinite(value)?value:null;const parsed=Number(String(value??'').replace(/[,%，]/g,''));return Number.isFinite(parsed)?parsed:null}
function escapeHtml(value){return String(value??'').replace(/[&<>'"]/g,char=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[char]))}
function isRising(value){return !String(value??'').trim().startsWith('-')}
function toneClass(value){return isRising(value)?'rise-text':'fall-text'}
function formatPercent(value){const parsed=number(value);return parsed==null?'--':`${parsed.toFixed(2)}%`}
function formatTemperature(value){const parsed=number(value);return parsed==null?'--':parsed.toFixed(0)}
function safePoints(value){return Array.isArray(value)?value:[]}

async function fetchJson(url,timeoutMs=35000){
  const controller=new AbortController();
  const timer=setTimeout(()=>controller.abort(),timeoutMs);
  try{
    const response=await fetch(url,{signal:controller.signal,cache:'no-store',headers:{'Cache-Control':'no-cache'}});
    const body=await response.json().catch(()=>({}));
    if(!response.ok)throw new Error(body?.detail||`HTTP ${response.status}`);
    return body;
  }finally{clearTimeout(timer)}
}

function updateClock(){
  $('#clock').textContent=new Intl.DateTimeFormat('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date());
}

function renderHeader(){
  const refresh=$('#refreshButton');
  refresh.classList.toggle('index-loading',state.loading);
  const payload=state.payload,quote=payload?.quote||{};
  $('#indexName').textContent=text(quote.name,payload?.name||'--');
  $('#indexCode').textContent=text(quote.code,state.code);
  $('#indexSource').textContent=state.loading?'正在同步真实指数行情':text(payload?.dataSourceLabel,state.error||'等待真实指数数据');
  $('#indexPrice').textContent=text(quote.price,'--');
  $('#indexPrice').className=`index-price ${toneClass(quote.changePercent)}`;
  $('#indexChange').textContent=`${text(quote.changeAmount,'--')}  ${text(quote.changePercent,'--')}`;
  $('#indexChange').className=`index-change ${toneClass(quote.changePercent)}`;
  const updated=state.lastSuccessAt?new Intl.DateTimeFormat('zh-CN',{hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(new Date(state.lastSuccessAt)):'等待更新';
  $('#indexUpdated').textContent=state.error?`刷新失败 · ${state.error}`:`更新 ${updated}`;
}

function metric(label,value,tone='neutral-text'){
  return `<article class="index-metric"><span>${escapeHtml(label)}</span><strong class="${tone}">${escapeHtml(text(value,'--'))}</strong></article>`;
}
function renderMetrics(){
  const quote=state.payload?.quote||{};
  const previousClose=number(quote.previousClose);
  $('#indexMetrics').innerHTML=[
    metric('今开',quote.open),
    metric('最高',quote.high,'rise-text'),
    metric('最低',quote.low,'fall-text'),
    metric('昨收',previousClose==null?'--':previousClose.toFixed(2)),
    metric('成交额',quote.amount),
    metric('成交量',quote.volume)
  ].join('');
}

function contextMetric(label,value,tone='neutral-text'){
  return `<article class="context-card"><span>${escapeHtml(label)}</span><strong class="${tone}">${escapeHtml(text(value,'--'))}</strong></article>`;
}
function renderContext(){
  const breadth=state.payload?.marketBreadth||{};
  const sentiment=state.payload?.sentiment||{};
  $('#contextGrid').innerHTML=[
    contextMetric('上涨',breadth.upCount,'rise-text'),
    contextMetric('下跌',breadth.downCount,'fall-text'),
    contextMetric('红盘率',formatPercent(breadth.redRate),'neutral-text'),
    contextMetric('情绪温度',formatTemperature(sentiment.sentimentTemperature??sentiment.temperature),'aqua-text')
  ].join('');
  $('#marketAmount').textContent=text(breadth.marketAmount,'--');
  const breadthStatus=text(state.payload?.marketBreadthMeta?.status,'unavailable');
  $('#contextStatus').textContent=`市场宽度 ${breadthStatus} · ${text(state.payload?.marketBreadthMeta?.source,'公开真实市场数据')}`;
}

function renderRelated(){
  const items=safePoints(state.payload?.relatedIndices).slice(0,8);
  const root=$('#relatedGrid');
  if(!items.length){root.innerHTML='<div class="empty-line">其他指数数据暂不可用</div>';return}
  root.innerHTML=items.map(item=>`<button type="button" class="related-index" data-code="${escapeHtml(item.code)}"><strong>${escapeHtml(item.name)}</strong><small>${escapeHtml(item.code)}</small><b class="${toneClass(item.changePercent)}">${escapeHtml(item.changePercent)}</b></button>`).join('');
  root.querySelectorAll('[data-code]').forEach(button=>button.addEventListener('click',()=>switchIndex(button.dataset.code,true)));
}

function renderTabs(){
  document.querySelectorAll('.index-tab').forEach(button=>button.classList.toggle('active',button.dataset.tab===state.tab));
}

function resizeCanvas(){
  const canvas=$('#indexChart');
  const rect=canvas.getBoundingClientRect();
  const dpr=Math.max(1,window.devicePixelRatio||1);
  const width=Math.max(1,Math.floor(rect.width));
  const height=Math.max(1,Math.floor(rect.height));
  if(canvas.width!==Math.floor(width*dpr)||canvas.height!==Math.floor(height*dpr)){
    canvas.width=Math.floor(width*dpr);canvas.height=Math.floor(height*dpr);
  }
  const ctx=canvas.getContext('2d');
  ctx.setTransform(dpr,0,0,dpr,0,0);
  ctx.clearRect(0,0,width,height);
  return {ctx,width,height};
}

function showChartEmpty(message){
  const empty=$('#chartEmpty');
  empty.textContent=message;
  empty.style.display='grid';
  resizeCanvas();
  $('#indexAxis').innerHTML='';
}

function drawGrid(ctx,left,top,right,bottom,columns=4){
  ctx.save();ctx.strokeStyle='rgba(255,255,255,.075)';ctx.lineWidth=1;
  for(let i=0;i<=4;i++){const y=top+(bottom-top)*i/4;ctx.beginPath();ctx.moveTo(left,y);ctx.lineTo(right,y);ctx.stroke()}
  for(let i=0;i<=columns;i++){const x=left+(right-left)*i/columns;ctx.beginPath();ctx.moveTo(x,top);ctx.lineTo(x,bottom);ctx.stroke()}
  ctx.restore();
}

function drawMinute(points){
  const data=safePoints(points).filter(point=>number(point.price)>0);
  if(!data.length){showChartEmpty('当日真实分时数据暂不可用');return}
  $('#chartEmpty').style.display='none';
  const {ctx,width,height}=resizeCanvas();
  const left=42,right=width-10,top=12,priceBottom=Math.max(top+60,height-72),volumeTop=priceBottom+12,volumeBottom=height-12;
  const previous=number(state.payload?.quote?.previousClose);
  const prices=data.map(point=>number(point.price)).filter(Number.isFinite);
  const averages=data.map(point=>number(point.average)).filter(Number.isFinite);
  const rangeValues=previous?[...prices,previous]:prices;
  let min=Math.min(...rangeValues),max=Math.max(...rangeValues);
  const padding=Math.max((max-min)*.12,max*.0025,1);
  min-=padding;max+=padding;
  const x=index=>left+(right-left)*(data.length===1?0:index/(data.length-1));
  const y=value=>top+(max-value)/(max-min)*(priceBottom-top);
  drawGrid(ctx,left,top,right,priceBottom);

  if(previous){ctx.save();ctx.setLineDash([4,4]);ctx.strokeStyle='rgba(255,255,255,.26)';ctx.beginPath();ctx.moveTo(left,y(previous));ctx.lineTo(right,y(previous));ctx.stroke();ctx.restore()}

  const maxVolume=Math.max(...data.map(point=>number(point.volume)||0),1);
  const barWidth=Math.max(1,(right-left)/data.length*.72);
  data.forEach((point,index)=>{
    const volume=number(point.volume)||0;
    const barHeight=(volume/maxVolume)*(volumeBottom-volumeTop);
    const current=number(point.price)||0;
    const prior=index?number(data[index-1].price):previous;
    ctx.fillStyle=current>=(prior??current)?'rgba(255,112,127,.74)':'rgba(82,233,163,.72)';
    ctx.fillRect(x(index)-barWidth/2,volumeBottom-barHeight,barWidth,Math.max(1,barHeight));
  });

  ctx.save();ctx.strokeStyle='#70d8ff';ctx.lineWidth=1.8;ctx.beginPath();
  data.forEach((point,index)=>{const px=x(index),py=y(number(point.price));if(index===0)ctx.moveTo(px,py);else ctx.lineTo(px,py)});ctx.stroke();ctx.restore();

  if(averages.length){ctx.save();ctx.strokeStyle='#ffd86b';ctx.lineWidth=1.2;ctx.beginPath();data.forEach((point,index)=>{const value=number(point.average);if(value==null)return;const px=x(index),py=y(value);if(index===0)ctx.moveTo(px,py);else ctx.lineTo(px,py)});ctx.stroke();ctx.restore()}

  ctx.save();ctx.fillStyle='rgba(255,255,255,.42)';ctx.font='8px system-ui';ctx.textAlign='right';ctx.fillText(max.toFixed(2),left-5,top+4);ctx.fillText(((max+min)/2).toFixed(2),left-5,(top+priceBottom)/2+3);ctx.fillText(min.toFixed(2),left-5,priceBottom);ctx.restore();

  const latest=data[data.length-1],latestPrice=number(latest.price),average=number(latest.average);
  $('#chartLatest').textContent=`最新 ${latestPrice?.toFixed(2)??'--'}`;
  $('#chartAverage').textContent=`均价 ${average?.toFixed(2)??'--'}`;
  $('#chartRange').textContent=`${min.toFixed(2)} - ${max.toFixed(2)}`;
  $('#chartTitle').textContent='分时走势';
  $('#indexAxis').innerHTML='<span>09:30</span><span>11:30 / 13:00</span><span>15:00</span>';
  $('#indexCaption').textContent='当日指数分时、均价线与分钟成交量。';
}

function groupFiveDayPoints(points){
  const groups=new Map();
  safePoints(points).filter(point=>number(point.price)>0&&text(point.date,'')).forEach(point=>{
    const date=text(point.date,'');
    if(!groups.has(date))groups.set(date,[]);
    groups.get(date).push(point);
  });
  return [...groups.entries()]
    .sort(([left],[right])=>left.localeCompare(right))
    .slice(-5)
    .map(([date,items])=>({date,items:items.sort((a,b)=>(number(a.timestamp)||0)-(number(b.timestamp)||0))}));
}

function drawFiveDay(points){
  const days=groupFiveDayPoints(points);
  const meta=state.payload?.fiveDayMeta||{};
  if(days.length<2){
    showChartEmpty('真实五日分钟数据尚未返回\n不会再用单日分时冒充五日走势');
    $('#chartTitle').textContent='五日走势';
    $('#chartAverage').textContent='真实多日数据';
    $('#chartLatest').textContent='最新 --';
    $('#chartRange').textContent='--';
    $('#indexCaption').textContent=`五日数据源 ${text(meta.source,'暂不可用')}。`;
    return;
  }
  $('#chartEmpty').style.display='none';
  const {ctx,width,height}=resizeCanvas();
  const left=42,right=width-10,top=12,priceBottom=Math.max(top+60,height-72),volumeTop=priceBottom+12,volumeBottom=height-12;
  const all=days.flatMap(day=>day.items);
  const prices=all.map(point=>number(point.price)).filter(Number.isFinite);
  let min=Math.min(...prices),max=Math.max(...prices);
  const padding=Math.max((max-min)*.10,max*.0025,1);
  min-=padding;max+=padding;
  const y=value=>top+(max-value)/(max-min)*(priceBottom-top);
  const dayWidth=(right-left)/days.length;
  const x=(dayIndex,pointIndex,count)=>{
    const innerLeft=left+dayWidth*dayIndex+2;
    const innerRight=left+dayWidth*(dayIndex+1)-2;
    return count<=1?(innerLeft+innerRight)/2:innerLeft+(innerRight-innerLeft)*pointIndex/(count-1);
  };
  drawGrid(ctx,left,top,right,priceBottom,days.length);

  const maxVolume=Math.max(...all.map(point=>number(point.volume)||0),1);
  const maxDayPoints=Math.max(...days.map(day=>day.items.length),1);
  const barWidth=Math.max(.7,Math.min(2.2,dayWidth/maxDayPoints*.72));
  let previousDayClose=null;
  days.forEach((day,dayIndex)=>{
    day.items.forEach((point,pointIndex)=>{
      const px=x(dayIndex,pointIndex,day.items.length);
      const volume=number(point.volume)||0;
      const barHeight=(volume/maxVolume)*(volumeBottom-volumeTop);
      const current=number(point.price)||0;
      const prior=pointIndex?number(day.items[pointIndex-1].price):previousDayClose;
      ctx.fillStyle=current>=(prior??current)?'rgba(255,112,127,.72)':'rgba(82,233,163,.70)';
      ctx.fillRect(px-barWidth/2,volumeBottom-barHeight,barWidth,Math.max(1,barHeight));
    });
    ctx.save();ctx.strokeStyle='#70d8ff';ctx.lineWidth=1.55;ctx.beginPath();
    day.items.forEach((point,pointIndex)=>{
      const px=x(dayIndex,pointIndex,day.items.length),py=y(number(point.price));
      if(pointIndex===0)ctx.moveTo(px,py);else ctx.lineTo(px,py);
    });
    ctx.stroke();ctx.restore();
    previousDayClose=number(day.items[day.items.length-1]?.price);
  });

  ctx.save();ctx.fillStyle='rgba(255,255,255,.42)';ctx.font='8px system-ui';ctx.textAlign='right';ctx.fillText(max.toFixed(2),left-5,top+4);ctx.fillText(((max+min)/2).toFixed(2),left-5,(top+priceBottom)/2+3);ctx.fillText(min.toFixed(2),left-5,priceBottom);ctx.restore();

  const latest=all[all.length-1],latestPrice=number(latest.price);
  $('#chartLatest').textContent=`最新 ${latestPrice?.toFixed(2)??'--'}`;
  $('#chartAverage').textContent=`${days.length}个交易日`;
  $('#chartRange').textContent=`${min.toFixed(2)} - ${max.toFixed(2)}`;
  $('#chartTitle').textContent='五日走势';
  $('#indexAxis').innerHTML=days.map(day=>`<span>${escapeHtml(day.date.slice(5))}</span>`).join('');
  $('#indexCaption').textContent=`真实五日分钟走势 · ${text(meta.source,'多日行情源')} · 每个交易日独立绘制。`;
}

function drawDaily(points){
  const data=safePoints(points).filter(row=>number(row.open)>0&&number(row.close)>0).slice(-60);
  if(!data.length){showChartEmpty('指数日K数据暂不可用');return}
  $('#chartEmpty').style.display='none';
  const {ctx,width,height}=resizeCanvas();
  const left=42,right=width-10,top=12,priceBottom=Math.max(top+60,height-72),volumeTop=priceBottom+12,volumeBottom=height-12;
  const highs=data.map(row=>number(row.high)||0),lows=data.map(row=>number(row.low)||0);
  let min=Math.min(...lows),max=Math.max(...highs);const padding=Math.max((max-min)*.08,max*.002,1);min-=padding;max+=padding;
  const slot=(right-left)/data.length;const candle=Math.max(2,Math.min(8,slot*.56));const x=index=>left+slot*(index+.5);const y=value=>top+(max-value)/(max-min)*(priceBottom-top);
  drawGrid(ctx,left,top,right,priceBottom);
  const maxVolume=Math.max(...data.map(row=>number(row.volume)||0),1);
  data.forEach((row,index)=>{
    const open=number(row.open),close=number(row.close),high=number(row.high),low=number(row.low),rise=close>=open,color=rise?'#ff7180':'#52e9a3',px=x(index);
    ctx.strokeStyle=color;ctx.fillStyle=color;ctx.lineWidth=1;ctx.beginPath();ctx.moveTo(px,y(high));ctx.lineTo(px,y(low));ctx.stroke();
    const bodyTop=Math.min(y(open),y(close)),bodyHeight=Math.max(1,Math.abs(y(open)-y(close)));ctx.fillRect(px-candle/2,bodyTop,candle,bodyHeight);
    const volume=number(row.volume)||0,barHeight=(volume/maxVolume)*(volumeBottom-volumeTop);ctx.globalAlpha=.62;ctx.fillRect(px-candle/2,volumeBottom-barHeight,candle,Math.max(1,barHeight));ctx.globalAlpha=1;
  });
  ctx.save();ctx.fillStyle='rgba(255,255,255,.42)';ctx.font='8px system-ui';ctx.textAlign='right';ctx.fillText(max.toFixed(2),left-5,top+4);ctx.fillText(((max+min)/2).toFixed(2),left-5,(top+priceBottom)/2+3);ctx.fillText(min.toFixed(2),left-5,priceBottom);ctx.restore();
  const latest=data[data.length-1];
  $('#chartTitle').textContent='日K走势';
  $('#chartAverage').textContent=`开 ${number(latest.open)?.toFixed(2)??'--'}`;
  $('#chartLatest').textContent=`收 ${number(latest.close)?.toFixed(2)??'--'}`;
  $('#chartRange').textContent=`高 ${number(latest.high)?.toFixed(2)??'--'} · 低 ${number(latest.low)?.toFixed(2)??'--'}`;
  const first=data[0],middle=data[Math.floor(data.length/2)];
  $('#indexAxis').innerHTML=`<span>${escapeHtml(text(first.date,''))}</span><span>${escapeHtml(text(middle.date,''))}</span><span>${escapeHtml(text(latest.date,''))}</span>`;
  $('#indexCaption').textContent='最近 60 个交易日日K与成交量。';
}

function renderChart(){
  renderTabs();
  if(state.tab==='fiveDay')drawFiveDay(state.payload?.fiveDayPoints);
  else if(state.tab==='daily')drawDaily(state.payload?.kLinePoints);
  else drawMinute(state.payload?.minutePoints);
}

function renderAll(){renderHeader();renderMetrics();renderContext();renderRelated();renderChart();updateDebugStatus()}

function updateDebugStatus(){
  const root=$('#dataStatus');
  if(state.loading){root.innerHTML='<strong>正在连接</strong>：并行加载指数报价、分时、真实五日、日K和市场宽度。';return}
  if(state.error){root.innerHTML=`<strong>刷新失败</strong>：${escapeHtml(state.error)}<br>保留上一份真实成功数据。`;return}
  const payload=state.payload;
  const fiveDayCount=number(payload?.fiveDayMeta?.tradingDayCount)||0;
  root.innerHTML=payload?`<strong>指数接口已连接</strong><br>${escapeHtml(payload.name)} ${escapeHtml(payload.code)}<br>分时 ${safePoints(payload.minutePoints).length} · 五日 ${fiveDayCount}个交易日/${safePoints(payload.fiveDayPoints).length}点 · 日K ${safePoints(payload.kLinePoints).length}<br>后端 ${escapeHtml(payload.totalLatencyMs??'--')}ms · 请求 ${state.requestCount} 次`:'<strong>等待数据</strong>：尚未收到指数详情。';
}

async function loadIndex(silent=false){
  if(state.loading)return;
  state.loading=true;state.error='';renderHeader();if(!silent)updateDebugStatus();
  try{
    const payload=await fetchJson(`${INDEX_DETAIL_API}?query=${encodeURIComponent(state.code)}&_=${Date.now()}`);
    if(!payload?.quote)throw new Error('指数详情缺少报价');
    state.payload=payload;state.lastSuccessAt=Date.now();state.requestCount++;
  }catch(error){state.error=error?.name==='AbortError'?'请求超时':error?.message||String(error)}
  finally{state.loading=false;renderAll();scheduleRefresh()}
}

function scheduleRefresh(){clearTimeout(state.timer);if(!state.autoRefresh)return;state.timer=setTimeout(()=>loadIndex(true),20000)}
function switchIndex(code,pushHistory){
  const normalized=normalizeCode(code);if(normalized===state.code&&state.payload)return;
  state.code=normalized;state.payload=null;state.error='';
  if(pushHistory)history.pushState({code:normalized},'',`?query=${encodeURIComponent(normalized)}`);
  renderAll();loadIndex(false);
}

$('#backButton').addEventListener('click',()=>{location.href='./stock-home-web-preview.html'});
$('#refreshButton').addEventListener('click',()=>loadIndex(false));
$('#indexTabs').addEventListener('click',event=>{const button=event.target.closest('[data-tab]');if(!button)return;state.tab=button.dataset.tab;renderChart()});
document.querySelectorAll('[data-index]').forEach(button=>button.addEventListener('click',()=>switchIndex(button.dataset.index,true)));
$('#autoRefresh').addEventListener('change',event=>{state.autoRefresh=event.target.checked;scheduleRefresh()});
$('#glassRange').addEventListener('input',event=>{document.documentElement.style.setProperty('--glass',Number(event.target.value)/100);$('#glassText').textContent=`${event.target.value}%`});
$('#phoneWidth').addEventListener('input',event=>{document.documentElement.style.setProperty('--phone-w',`${event.target.value}px`);$('#phoneWidthText').textContent=`${event.target.value}px`;requestAnimationFrame(renderChart)});
$('#radiusRange').addEventListener('input',event=>{document.documentElement.style.setProperty('--radius',`${event.target.value}px`);$('#radiusText').textContent=`${event.target.value}px`});
$('#mobileToggle').addEventListener('click',()=>document.body.classList.toggle('controls-open'));
window.addEventListener('resize',()=>requestAnimationFrame(renderChart));
window.addEventListener('popstate',()=>switchIndex(new URLSearchParams(location.search).get('query'),false));
document.addEventListener('visibilitychange',()=>{if(!document.hidden&&state.autoRefresh)loadIndex(true)});

updateClock();setInterval(updateClock,30000);renderAll();setTimeout(()=>loadIndex(false),180);

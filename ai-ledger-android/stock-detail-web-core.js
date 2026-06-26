'use strict';

const API_BASE = 'https://ai-ledger-stock-proxy.onrender.com';
const REALTIME_API = `${API_BASE}/api/stock/a-share/realtime`;
const KLINE_API = `${API_BASE}/api/stock/a-share/kline`;
const KLINE_FALLBACK_API = `${API_BASE}/api/stock/crawl/a-share/kline`;
const $ = selector => document.querySelector(selector);
const $$ = selector => [...document.querySelectorAll(selector)];
const rootStyle = document.documentElement.style;

const COLORS = {
  rise: 'rgba(255,143,143,.98)',
  riseSoft: 'rgba(255,90,90,.42)',
  fall: 'rgba(128,247,180,.98)',
  fallSoft: 'rgba(80,210,120,.42)',
  aqua: 'rgba(141,249,234,.72)',
  yellow: 'rgba(255,211,110,.90)',
  blue: 'rgba(157,203,255,.82)',
  white: 'rgba(255,255,255,.88)',
  grid: 'rgba(255,255,255,.10)'
};

const state = {
  selectedTab: '分时',
  quote: emptyQuote(),
  minuteCache: new Map(),
  klineCache: new Map(),
  sellLevels: [],
  buyLevels: [],
  ticks: [],
  auction: null,
  depthStatus: '--',
  dataSourceLabel: '等待真实行情',
  running: false,
  loadingRealtime: false,
  loadingKline: false,
  timer: null,
  requestCount: 0,
  openWidth: .14,
  closeWidth: .05,
  volumeFraction: .24,
  kBaseCount: 72,
  kZoom: 1,
  kPan: 0,
  kSelectedIndex: -1,
  kHoverIndex: -1,
  dragStartX: null,
  dragStartPan: 0,
  activeCode: '600667',
  lastRealtimeError: ''
};

function emptyQuote(){
  return {
    name:'--',code:'------',market:'--',price:null,changeAmount:null,changePercent:null,
    high:null,low:null,open:null,previousClose:null,amount:'--',turnoverRate:'--',
    volumeRatio:'--',totalMarketValue:'--',circulatingMarketValue:'--',peTtm:'--',source:'等待真实后端行情'
  };
}

function number(value){
  if(typeof value === 'number') return Number.isFinite(value) ? value : null;
  if(value == null) return null;
  const normalized = String(value).replace(/[,，%亿万手元]/g,'').trim();
  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? parsed : null;
}
function fmt(value, digits=2){return value == null || !Number.isFinite(Number(value)) ? '--' : Number(value).toFixed(digits)}
function signed(value,suffix=''){return value == null || !Number.isFinite(Number(value)) ? '--' : `${Number(value)>=0?'+':''}${Number(value).toFixed(2)}${suffix}`}
function formatVolume(value){
  const n = number(value); if(n == null) return '--';
  if(n >= 1e8) return `${(n/1e8).toFixed(2)}亿`;
  if(n >= 1e4) return `${(n/1e4).toFixed(2)}万`;
  return `${Math.round(n)}`;
}
function priceTone(price, zero=state.quote.previousClose){
  const p=number(price),z=number(zero); if(p==null||z==null) return 'flat';
  return p>z?'rise':p<z?'fall':'flat';
}
function quoteTone(){return (number(state.quote.changePercent)??0)>=0?'rise':'fall'}
function periodForTab(tab){return tab==='周K'?'weekly':tab==='月K'?'monthly':'daily'}
function isMinuteTab(tab){return tab==='分时'||tab==='五日'}
function minuteDaysForTab(tab){return tab==='五日'?5:1}
function currentQuery(){return ($('#query').value.trim() || state.activeCode || '600667')}
function unwrapPayload(root){return root?.data??root?.payload??root?.result??root??{}}
function firstArray(root,keys){
  if(!root||typeof root!=='object') return [];
  for(const key of keys){if(Array.isArray(root[key])) return root[key]}
  for(const container of ['data','payload','result','snapshot']){
    const nested=root[container]; if(nested&&typeof nested==='object'){
      const found=firstArray(nested,keys); if(found.length) return found;
    }
  }
  return [];
}
function extractDate(raw){
  const m=String(raw??'').match(/(\d{4}-\d{2}-\d{2})/); return m?.[1]??'';
}
function timeParts(raw){
  if(raw==null) return null;
  const text=String(raw).trim();
  if(/^\d{10,13}$/.test(text)){
    const stamp=Number(text.length===10?`${text}000`:text);
    const parts=Object.fromEntries(new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).formatToParts(new Date(stamp)).filter(i=>i.type!=='literal').map(i=>[i.type,i.value]));
    return {date:`${parts.year}-${parts.month}-${parts.day}`,minute:`${parts.hour}:${parts.minute}`,full:`${parts.hour}:${parts.minute}:${parts.second}`,minuteOfDay:Number(parts.hour)*60+Number(parts.minute),secondOfDay:(Number(parts.hour)*60+Number(parts.minute))*60+Number(parts.second)};
  }
  const m=text.match(/(\d{1,2}):(\d{2})(?::(\d{2}))?/); if(!m) return null;
  const h=Number(m[1]),mi=Number(m[2]),s=Number(m[3]??0); if(h>23||mi>59||s>59) return null;
  const hh=String(h).padStart(2,'0'),mm=String(mi).padStart(2,'0'),ss=String(s).padStart(2,'0');
  return {date:extractDate(text),minute:`${hh}:${mm}`,full:m[3]==null?`${hh}:${mm}`:`${hh}:${mm}:${ss}`,minuteOfDay:h*60+mi,secondOfDay:(h*60+mi)*60+s};
}
function phaseFrom(item,t){
  const raw=String(item?.sessionPhase??item?.phase??'').toLowerCase();
  if(raw.includes('open')) return 'open';
  if(raw.includes('close')) return 'close';
  if(t.minuteOfDay>=555&&t.minuteOfDay<=565) return 'open';
  if(t.minuteOfDay>=897&&t.minuteOfDay<=900) return 'close';
  return 'continuous';
}
function normalizeMinutePoint(item){
  const rawTime=item?.time??item?.datetime??item?.dateTime??item?.timestamp;
  const t=timeParts(rawTime); const price=number(item?.price??item?.close??item?.current);
  if(price==null||!t||t.minuteOfDay<555||t.minuteOfDay>900) return null;
  return {
    date:item?.date??item?.tradeDate??t.date??'',time:t.minute,fullTime:t.full,
    minuteOfDay:t.minuteOfDay,secondOfDay:t.secondOfDay,timestamp:number(item?.timestamp)??0,
    price,average:number(item?.average??item?.avgPrice??item?.averagePrice)??price,
    volume:number(item?.volume??item?.vol)??0,volumeRatio:number(item?.volumeRatio??item?.ratio),
    matchedVolume:number(item?.matchedVolume),unmatchedVolume:number(item?.unmatchedVolume),
    unmatchedDirection:String(item?.unmatchedDirection??'unavailable').toLowerCase(),phase:phaseFrom(item,t)
  };
}
function minuteKey(point){return point.timestamp>0?`t:${point.timestamp}`:`${point.date}|${point.fullTime}`}
function normalizeLevel(item,index,prefix){return {label:item?.label??item?.name??`${prefix}${index+1}`,price:String(item?.price??item?.p??'--'),volume:String(item?.volume??item?.qty??item?.vol??item?.amount??'--')}}
function normalizeRealtimePayload(root){
  const payload=unwrapPayload(root); const q0=payload.quote??payload.stock??{}; const auction=payload.auction??null;
  const quote={
    name:q0.name??q0.stockName??'--',code:q0.code??q0.stockCode??'------',market:q0.market??q0.marketName??'--',
    price:number(q0.price??q0.latestPrice??q0.current),changeAmount:number(q0.changeAmount??q0.change),changePercent:number(q0.changePercent??q0.changePct),
    high:number(q0.high),low:number(q0.low),open:number(q0.open),previousClose:number(q0.previousClose??q0.preClose),
    amount:q0.amount??q0.turnoverAmount??'--',turnoverRate:q0.turnoverRate??'--',volumeRatio:q0.volumeRatio??'--',
    totalMarketValue:q0.totalMarketValue??q0.marketValue??q0.marketCap??'--',
    circulatingMarketValue:q0.circulatingMarketValue??q0.floatMarketValue??q0.circulatingMarketCap??q0.floatMarketCap??q0.marketValue??'--',
    peTtm:q0.peTtm??q0.peTTM??q0.priceEarningsTtm??q0.peDynamic??q0.pe??'--',
    source:payload.dataSourceLabel??root?.dataSourceLabel??'A股真实统一行情'
  };
  const raw=[...firstArray(payload,['minutePoints','minutes','minute','trends']),...(auction?.open?.points??[]),...(auction?.close?.points??[])];
  const map=new Map(); raw.forEach(item=>{const point=normalizeMinutePoint(item);if(point)map.set(minuteKey(point),point)});
  const minutePoints=[...map.values()].sort((a,b)=>String(a.date).localeCompare(String(b.date))||a.secondOfDay-b.secondOfDay||a.timestamp-b.timestamp);
  const sellRaw=payload.sellLevels??payload.sell??payload.asks??payload.depth?.sellLevels??[];
  const buyRaw=payload.buyLevels??payload.buy??payload.bids??payload.depth?.buyLevels??[];
  const tickRaw=payload.tradeTicks??payload.ticks??payload.details??[];
  const ticks=(Array.isArray(tickRaw)?tickRaw:[]).slice(-120).map(item=>{const t=timeParts(item?.time??item?.timestamp);return {time:t?.full??'--',price:String(item?.price??'--'),volume:String(item?.volume??item?.amount??item?.qty??'--'),buy:Boolean(item?.isBuy??String(item?.direction??'').includes('买'))}});
  return {
    quote,minutePoints,sellLevels:(Array.isArray(sellRaw)?sellRaw:[]).slice(0,10).map((x,i)=>normalizeLevel(x,i,'卖')),
    buyLevels:(Array.isArray(buyRaw)?buyRaw:[]).slice(0,10).map((x,i)=>normalizeLevel(x,i,'买')),
    ticks,auction,depthStatus:payload.depthStatus??payload.depthState?.status??((sellRaw.length||buyRaw.length)?'ok':'unavailable'),
    sourceLabel:quote.source
  };
}
function normalizeKlinePayload(root){
  const rows=firstArray(root,['kLinePoints','klinePoints','klines','kLines']);
  return rows.map(item=>{
    const open=number(item?.open??item?.o),close=number(item?.close??item?.c??item?.price);
    if(open==null||close==null) return null;
    return {date:String(item?.date??item?.day??item?.time??''),open,close,high:number(item?.high??item?.h)??close,low:number(item?.low??item?.l)??close,volume:number(item?.volume??item?.vol)??0,amount:number(item?.amount)??0,changePercent:String(item?.changePercent??item?.pct??'--')};
  }).filter(Boolean);
}

function applyRealtimeFrame(frame,days){
  state.quote=frame.quote; state.activeCode=frame.quote.code||currentQuery();
  state.sellLevels=frame.sellLevels; state.buyLevels=frame.buyLevels; state.ticks=frame.ticks;
  state.auction=frame.auction; state.depthStatus=frame.depthStatus; state.dataSourceLabel=frame.sourceLabel;
  if(frame.minutePoints.length) state.minuteCache.set(`${state.activeCode}:${days}`,frame.minutePoints);
}
function currentMinutePoints(){return state.minuteCache.get(`${state.activeCode}:${minuteDaysForTab(state.selectedTab)}`)??[]}
function currentKlines(){const period=periodForTab(state.selectedTab);return state.klineCache.get(`${state.activeCode}:${period}`)??state.klineCache.get(`${currentQuery()}:${period}`)??[]}

function renderQuote(){
  const q=state.quote,t=quoteTone();
  $('#name').textContent=q.name; $('#code').textContent=q.code; $('#market').textContent=q.market; $('#source').textContent=q.source;
  $('#price').textContent=fmt(q.price); $('#price').className=`price ${t}`;
  $('#change').textContent=`${signed(q.changeAmount)}  ${signed(q.changePercent,'%')}`; $('#change').className=`change ${t}`;
  $('#previousCloseText').textContent=fmt(q.previousClose);
  const volumeTone=(number(q.volumeRatio)??0)>=1?'rise':'fall';
  const rows=[['高',fmt(q.high),priceTone(q.high)],['市值',q.totalMarketValue,''],['量比',q.volumeRatio,volumeTone],['低',fmt(q.low),priceTone(q.low)],['流通',q.circulatingMarketValue,''],['换',q.turnoverRate,''],['开',fmt(q.open),priceTone(q.open)],['市盈TTM',q.peTtm,''],['额',q.amount,'']];
  $('#metrics').innerHTML=rows.map(([label,value,tone])=>`<div class="metric"><span>${label}</span><b class="${tone}">${value}</b></div>`).join('');
}
function renderOrderFlow(){
  const zero=state.quote.previousClose;
  $('#depthState').textContent=['ok','partial','stale'].includes(String(state.depthStatus).toLowerCase())?'实时':'不可用';
  const sell=state.sellLevels.slice(0,5),buy=state.buyLevels.slice(0,5);
  $('#depth').innerHTML=!sell.length&&!buy.length?'<div class="empty">真实五档<br>暂不可用</div>':[
    ...sell.map(x=>({...x,side:'ask'})),{spread:true},...buy.map(x=>({...x,side:'bid'}))
  ].map(row=>row.spread?'<div class="spread"></div>':`<div class="depth-row"><span class="label">${row.label}</span><span class="depth-price ${priceTone(row.price,zero)}">${row.price}</span><span class="volume">${row.volume}</span></div>`).join('');
  const ticks=state.ticks.slice(-8);
  $('#ticks').innerHTML=ticks.length?ticks.map(item=>`<div class="tick-row"><span class="tick-time">${item.time}</span><span class="tick-price ${priceTone(item.price,zero)}">${item.price}</span><span class="volume">${item.volume}</span></div>`).join(''):'<div class="empty">真实逐笔<br>暂不可用</div>';
}
function renderTabs(){$$('.pill').forEach(button=>button.classList.toggle('active',button.dataset.tab===state.selectedTab));$('#terminal').classList.toggle('kline-mode',!isMinuteTab(state.selectedTab))}
function setAxis(items){
  const axis=$('#axis'); axis.innerHTML='';
  items.forEach((item,index)=>{const span=document.createElement('span');span.textContent=item.label;span.style.left=`${item.x*100}%`;if(index===0){span.style.left='0';span.style.transform='none'}else if(index===items.length-1){span.style.left='auto';span.style.right='0';span.style.transform='none'}axis.appendChild(span)});
}
function renderCaption(items){$('#caption').innerHTML=items.map((text,index)=>`<span class="${index===0?'yellow':index===1?'ma-blue':''}">${text}</span>`).join('')}
function renderLegend(){
  const q=state.quote,t=quoteTone(); $('#legendChange').textContent=signed(q.changePercent,'%');$('#legendChange').className=t;
  if(state.selectedTab==='分时'||state.selectedTab==='五日'){
    const points=currentMinutePoints(),latest=points.at(-1),continuous=points.filter(p=>p.phase==='continuous');
    $('#legendTitle').textContent=state.selectedTab==='五日'?'五日分时':'分时 · 首尾集合竞价';
    $('#legendSecondary').innerHTML=`均价 <b id="average">${fmt(continuous.at(-1)?.average??latest?.average??q.price)}</b>`;
    $('#legendLatest').innerHTML=`最新 <b id="latest" class="${t}">${fmt(q.price)}</b>`;
    if(state.selectedTab==='分时'){
      const open=state.auction?.open??{},close=state.auction?.close??{};
      const matched=(open.matchedVolumePointCount??0)+(close.matchedVolumePointCount??0),unmatched=(open.unmatchedVolumePointCount??0)+(close.unmatchedVolumePointCount??0);
      $('#auctionStatus').textContent=state.auction?`匹配 ${matched} · 未匹配 ${unmatched}`:'竞价数据不可用';
    }else{$('#auctionStatus').textContent=`${fiveDayDates(points).filter(Boolean).length} 个交易日`}
  }else{
    const candles=currentKlines(),ma5=movingAverage(candles,5),ma10=movingAverage(candles,10);
    $('#legendTitle').textContent=`${state.selectedTab}历史行情`;
    $('#legendSecondary').innerHTML=`MA5 <b>${fmt(ma5.at(-1))}</b>`;
    $('#legendLatest').innerHTML=`MA10 <b class="ma-blue">${fmt(ma10.at(-1))}</b>`;
    $('#auctionStatus').textContent=candles.length?`${candles.length} 根真实K线`:'等待真实K线';
  }
}
function renderAll(){renderTabs();renderQuote();renderOrderFlow();renderLegend();requestAnimationFrame(drawSelectedChart)}

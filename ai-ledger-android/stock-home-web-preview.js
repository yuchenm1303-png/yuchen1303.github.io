'use strict';

const API_BASE = 'https://ai-ledger-stock-proxy.onrender.com';
const MARKET_HOME_API = `${API_BASE}/api/stock/a-share/market/home`;
const ACTIONS = ['自选','热榜','板块','资金','异动','新闻','研报','预警'];
const BOARD_DEFINITIONS = [
  ['gainers','涨幅榜','真实涨幅排序'],
  ['losers','跌幅榜','真实跌幅排序'],
  ['amountRanking','成交额榜','真实成交额排序'],
  ['turnoverRanking','换手率榜','真实换手率排序'],
  ['volumeRatioRanking','量比榜','真实量比排序'],
  ['speedRanking','涨速榜','真实涨速排序'],
  ['mainInflowRanking','主力净流入榜','真实主力净流入排序'],
  ['mainOutflowRanking','主力净流出榜','真实主力净流出排序']
];
const STATUS_TEXT = {ok:'实时',partial:'部分数据',empty:'暂无数据',stale:'缓存数据',unavailable:'数据源暂不可用'};
const $ = selector => document.querySelector(selector);
const state = {
  loading:false,
  selectedAction:'热榜',
  snapshot:emptySnapshot(),
  lastSuccessAt:0,
  lastError:'',
  requestCount:0,
  timer:null,
  autoRefresh:true
};

function emptyMeta(){return {status:'unavailable',source:'',updatedAt:'',cacheAgeMs:0,isDerived:false,warnings:[]}}
function emptySnapshot(){return {indices:[],indicesMeta:emptyMeta(),breadth:{meta:emptyMeta()},sentiment:{meta:emptyMeta()},boards:[],sectors:[],marketNews:[],marketNewsMeta:emptyMeta(),popularityMeta:emptyMeta(),limitUpMeta:emptyMeta(),updatedAt:'',warnings:[]}}
function escapeHtml(value){return String(value??'').replace(/[&<>'"]/g,char=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[char]))}
function first(...values){for(const value of values){if(value!==undefined&&value!==null&&String(value).trim()!=='')return value}return null}
function number(value){if(typeof value==='number')return Number.isFinite(value)?value:null;if(value==null)return null;const parsed=Number(String(value).replace(/[,%，]/g,'').trim());return Number.isFinite(parsed)?parsed:null}
function integer(value){const parsed=number(value);return parsed==null?null:Math.trunc(parsed)}
function text(value,fallback='--'){const result=String(value??'').trim();return result&&result!=='null'&&result!=='NaN'?result:fallback}
function percent(value){const parsed=number(value);return parsed==null?'--':`${parsed.toFixed(2)}%`}
function temperature(value){const parsed=number(value);return parsed==null?'--':parsed.toFixed(0)}
function normalizedStatus(value){const status=String(value??'').trim().toLowerCase();return ['ok','partial','empty','stale'].includes(status)?status:'unavailable'}
function statusClass(status){if(status==='ok')return'aqua-text';if(status==='partial'||status==='stale')return'warning-text';return'neutral-muted'}
function hasRealData(meta){return ['ok','partial','stale'].includes(meta?.status)}
function unwrap(root){return root?.data??root?.payload??root?.result??root??{}}
function moduleObject(payload,key){const value=payload?.[key];return value&&typeof value==='object'&&!Array.isArray(value)?value:null}
function itemsArray(module){if(!module||typeof module!=='object')return[];for(const key of ['items','data','result']){if(Array.isArray(module[key]))return module[key]}for(const key of ['data','result','payload']){const nested=module[key];if(nested&&typeof nested==='object'&&!Array.isArray(nested)){for(const itemKey of ['items','data','result'])if(Array.isArray(nested[itemKey]))return nested[itemKey]}}return[]}
function itemsObject(module){if(!module||typeof module!=='object')return{};if(module.items&&typeof module.items==='object'&&!Array.isArray(module.items))return module.items;for(const key of ['data','result']){const nested=module[key];if(nested&&typeof nested==='object'&&!Array.isArray(nested))return nested.items&&typeof nested.items==='object'&&!Array.isArray(nested.items)?nested.items:nested}return{}}
function parseMeta(module){if(!module||typeof module!=='object')return emptyMeta();return {status:normalizedStatus(module.status),source:text(module.source,''),sourceUrlType:text(module.sourceUrlType,''),updatedAt:text(module.updatedAt,''),cacheAgeMs:number(module.cacheAgeMs)??0,isDerived:Boolean(module.isDerived),warnings:Array.isArray(module.warnings)?module.warnings.map(String):[]}}
function parseIndex(item){const code=text(first(item?.code,item?.symbol),'');const name=text(first(item?.name,item?.indexName),code);const value=text(first(item?.price,item?.value),'');if(!code||!name||!value||value==='--')return null;const changePercent=text(first(item?.changePercent,item?.pct),'--');return{code,name,value,changePercent,isRising:!changePercent.startsWith('-'),open:text(item?.open,''),high:text(item?.high,''),low:text(item?.low,''),amount:text(item?.amount,'')}}
function parseRankItem(item){const code=text(first(item?.code,item?.symbol),'');const name=text(first(item?.name,item?.stockName),code);if(!code&&!name)return null;const changePercent=text(first(item?.changePercent,item?.pct),'--');return{name,code,value:rankingValue(item),changePercent,isRising:!changePercent.startsWith('-')}}
function rankingValue(item){for(const key of ['mainInflow','amount','turnoverRate','volumeRatio','changeSpeed','price','value']){const value=text(item?.[key],'');if(value&&value!=='--')return value}return'--'}
function parseSector(item){const code=text(first(item?.sectorCode,item?.code),'');const name=text(first(item?.sectorName,item?.name),code);if(!code&&!name)return null;return{code,name,type:text(item?.type,''),changePercent:text(first(item?.changePercent,item?.pct),'--'),upCount:integer(item?.upCount),downCount:integer(item?.downCount),flatCount:integer(item?.flatCount),leaderName:text(item?.leaderName,''),leaderChangePercent:text(item?.leaderChangePercent,''),amount:text(item?.amount,''),turnoverRate:text(item?.turnoverRate,''),mainInflow:text(item?.mainInflow,''),heatRank:integer(item?.heatRank)}}
function parseInformation(item){const title=text(first(item?.title,item?.name),'');if(!title)return null;return{id:text(first(item?.id,item?.reportId),''),title,summary:text(first(item?.summary,item?.description),''),publishTime:text(first(item?.publishTime,item?.time,item?.updatedAt),''),source:text(first(item?.source,item?.institution),''),url:text(first(item?.url,item?.attachmentUrl),'')}}

function parseMarketHome(root){
  const payload=unwrap(root);
  const indicesModule=moduleObject(payload,'indices');
  const breadthModule=moduleObject(payload,'marketBreadth');
  const sentimentModule=moduleObject(payload,'sentiment');
  const sectorModule=moduleObject(payload,'sectorHotRanking');
  const newsModule=moduleObject(payload,'marketNews');
  const popularityModule=moduleObject(payload,'popularityRanking');
  const limitUpModule=moduleObject(payload,'limitUpSummary');
  const breadth=itemsObject(breadthModule),sentiment=itemsObject(sentimentModule);
  const boards=[];
  for(const [key,title,subtitle] of BOARD_DEFINITIONS){
    const module=moduleObject(payload,key),meta=parseMeta(module),items=itemsArray(module).map(parseRankItem).filter(Boolean);
    if(items.length&&hasRealData(meta))boards.push({key,title,subtitle,meta,items});
  }
  return {
    indices:itemsArray(indicesModule).map(parseIndex).filter(Boolean),indicesMeta:parseMeta(indicesModule),
    breadth:{upCount:integer(breadth.upCount),downCount:integer(breadth.downCount),flatCount:integer(breadth.flatCount),limitUpCount:integer(breadth.limitUpCount),limitDownCount:integer(breadth.limitDownCount),brokenBoardCount:integer(breadth.brokenBoardCount),brokenBoardRate:number(breadth.brokenBoardRate),maxConsecutiveBoards:integer(breadth.maxConsecutiveBoards),redRate:number(breadth.redRate),medianChangePercent:number(breadth.medianChangePercent),marketAmount:text(breadth.marketAmount,'--'),shszAmount:text(breadth.shszAmount,'--'),bjAmount:text(breadth.bjAmount,'--'),moneyMakingEffect:number(breadth.moneyMakingEffect),updatedAt:text(breadth.updatedAt,''),meta:parseMeta(breadthModule)},
    sentiment:{temperature:number(first(sentiment.sentimentTemperature,sentiment.temperature)),level:text(first(sentiment.sentimentLevel,sentiment.level),''),formula:text(sentiment.formula,''),redRate:number(sentiment.redRate),limitUpCount:integer(sentiment.limitUpCount),moneyMakingEffect:number(sentiment.moneyMakingEffect),meta:parseMeta(sentimentModule)},
    boards,
    sectors:itemsArray(sectorModule).map(parseSector).filter(Boolean),
    marketNews:itemsArray(newsModule).map(parseInformation).filter(Boolean),marketNewsMeta:parseMeta(newsModule),
    popularityMeta:parseMeta(popularityModule),limitUpMeta:parseMeta(limitUpModule),updatedAt:text(payload.updatedAt,''),warnings:Array.isArray(payload.warnings)?payload.warnings.map(String):[]
  };
}

function toneClass(rising){return rising?'rise-text':'fall-text'}
function sentimentTone(value){const n=number(value);if(n==null)return'neutral-text';if(n>=60)return'rise-text';if(n<35)return'fall-text';return'warning-text'}
function flowTone(value){const raw=text(value,'');if(!raw||raw==='--')return'neutral-text';return raw.includes('-')?'fall-text':'rise-text'}
function statusText(meta){return STATUS_TEXT[meta?.status]??STATUS_TEXT.unavailable}
function statusMarkup(meta){return `<div class="module-status"><strong class="${statusClass(meta?.status)}">${escapeHtml(statusText(meta))}</strong><span>${escapeHtml(meta?.source||'未接稳定真实数据源')}</span></div>`}
function sectionHeading(title,subtitle){return `<div class="section-title-block"><h2>${escapeHtml(title)}</h2><p>${escapeHtml(subtitle)}</p></div>`}

function renderHeader(){
  const status=$('#homeStatus'),refresh=$('#refreshButton');
  refresh.classList.toggle('loading',state.loading);
  if(state.loading){status.textContent='正在同步指数、宽度、榜单与板块';status.classList.remove('warning')}
  else if(state.lastError){status.textContent='市场数据刷新失败 · 保留上一份真实数据';status.classList.add('warning')}
  else{status.textContent='真实市场数据 · 20 秒刷新';status.classList.remove('warning')}
}
function renderIndices(){
  const root=$('#indices'),snapshot=state.snapshot;
  if(!snapshot.indices.length){root.innerHTML=statusMarkup(state.loading?{status:'partial',source:'正在加载真实指数'}:snapshot.indicesMeta);return}
  root.innerHTML=snapshot.indices.map(item=>`<button class="index-card ${item.isRising?'rise':'fall'}" data-index-code="${escapeHtml(item.code)}" aria-label="查看${escapeHtml(item.name)}详情"><div class="index-head"><span class="index-name">${escapeHtml(item.name)}</span><span class="index-arrow ${toneClass(item.isRising)}">${item.isRising?'↑':'↓'}</span></div><div class="index-value">${escapeHtml(item.value)}</div><div class="index-change ${toneClass(item.isRising)}">${escapeHtml(item.changePercent)}</div></button>`).join('');
  root.querySelectorAll('[data-index-code]').forEach(card=>{card.style.cursor='pointer';card.addEventListener('click',()=>openIndexDetail(card.dataset.indexCode))});
}
function metricCard(label,value,tone,prominent=false){return `<article class="breadth-card${prominent?' prominent':''}"><span class="breadth-label">${escapeHtml(label)}</span><strong class="breadth-value ${tone}">${escapeHtml(value)}</strong></article>`}
function renderBreadth(){
  const breadth=state.snapshot.breadth,sentiment=state.snapshot.sentiment;
  $('#breadthPrimary').innerHTML=[
    metricCard('上涨',breadth.upCount??'--','rise-text',true),metricCard('下跌',breadth.downCount??'--','fall-text',true),metricCard('涨停',breadth.limitUpCount??'--','rise-text',true),metricCard('跌停',breadth.limitDownCount??'--','fall-text',true)
  ].join('');
  $('#breadthSecondary').innerHTML=[
    metricCard('红盘率',percent(breadth.redRate),'neutral-text'),metricCard('赚钱效应',percent(breadth.moneyMakingEffect),'aqua-text'),metricCard('情绪温度',temperature(sentiment.temperature),sentimentTone(sentiment.temperature))
  ].join('');
  $('#marketAmount').textContent=breadth.marketAmount||'--';
}
function renderQuickGrid(){
  $('#quickGrid').innerHTML=ACTIONS.map(action=>`<button class="quick-button${state.selectedAction===action?' active':''}" data-action="${action}">${action}</button>`).join('');
  document.querySelectorAll('.quick-button').forEach(button=>button.addEventListener('click',()=>{state.selectedAction=button.dataset.action;renderQuickGrid();renderToolContent()}));
}
function rankRow(item,index){return `<button class="rank-row" data-code="${escapeHtml(item.code)}"><span class="rank-number">${index+1}</span><span class="rank-name"><strong>${escapeHtml(item.name)}</strong><span>${escapeHtml(item.code)}</span></span><span class="rank-value">${escapeHtml(item.value)}</span><span class="rank-change ${toneClass(item.isRising)}">${escapeHtml(item.changePercent)}</span></button>`}
function renderBoards(boards,title,meta){
  if(!boards.length)return sectionHeading(title,'不同榜单使用各自真实排序字段')+statusMarkup(meta);
  return sectionHeading(title,'不同榜单使用各自真实排序字段')+boards.map((board,boardIndex)=>`<div class="content-section-title">${escapeHtml(board.title)}</div>${board.items.slice(0,boards.length===1?8:3).map(rankRow).join('')}${boardIndex<boards.length-1?'<div class="inner-divider"></div>':''}`).join('');
}
function renderSectors(){
  const sectors=state.snapshot.sectors;
  if(!sectors.length)return sectionHeading('行业板块','真实行业涨幅、涨跌家数、资金与领涨股')+'<div class="empty-line">板块数据暂不可用</div>';
  return sectionHeading('行业板块','真实行业涨幅、涨跌家数、资金与领涨股')+sectors.slice(0,8).map(sector=>`<div class="sector-row"><div class="sector-copy"><strong>${escapeHtml(sector.name)}</strong><span>涨 ${escapeHtml(sector.upCount??'--')} · 跌 ${escapeHtml(sector.downCount??'--')}${sector.leaderName?` · 领涨 ${escapeHtml(sector.leaderName)}`:''}</span></div><span class="sector-flow ${flowTone(sector.mainInflow)}">${escapeHtml(sector.mainInflow||sector.amount||'--')}</span><span class="sector-change ${toneClass(!sector.changePercent.startsWith('-'))}">${escapeHtml(sector.changePercent)}</span></div>`).join('');
}
function renderNews(){
  const items=state.snapshot.marketNews;
  if(!items.length)return sectionHeading('市场新闻','只展示后端确认的真实内容')+statusMarkup(state.snapshot.marketNewsMeta);
  return sectionHeading('市场新闻','只展示后端确认的真实内容')+items.slice(0,8).map(item=>`<article class="info-row"><strong>${escapeHtml(item.title)}</strong><span>${escapeHtml([item.source,item.publishTime].filter(Boolean).join(' · '))}</span></article>`).join('');
}
function renderToolContent(){
  const root=$('#toolContent');root.classList.toggle('scrollable',['板块','新闻'].includes(state.selectedAction));
  const snapshot=state.snapshot;
  switch(state.selectedAction){
    case '自选':root.innerHTML=sectionHeading('我的自选','当前仅保存本次页面状态，不注入固定股票')+'<div class="empty-line">尚未添加自选股</div>';break;
    case '热榜':root.innerHTML=renderBoards(snapshot.boards.filter(board=>!board.title.includes('主力')).slice(0,3),'真实行情榜单',snapshot.popularityMeta);break;
    case '板块':root.innerHTML=renderSectors();break;
    case '资金':root.innerHTML=renderBoards(snapshot.boards.filter(board=>board.title.includes('主力')),'主力资金排序',{status:snapshot.boards.some(board=>board.title.includes('主力'))?'ok':'unavailable',source:'公开真实资金数据'});break;
    case '异动':root.innerHTML=sectionHeading('交易异动','没有稳定真实数据源时不会生成模板数据')+statusMarkup(snapshot.limitUpMeta);break;
    case '新闻':root.innerHTML=renderNews();break;
    case '研报':root.innerHTML=sectionHeading('机构研报','没有稳定真实数据源时不会生成模板数据')+statusMarkup({status:'unavailable',source:'未接稳定真实数据源'});break;
    case '预警':root.innerHTML='<div class="empty-line">价格预警属于本地功能，当前尚未配置预警条件</div>';break;
  }
  root.querySelectorAll('.rank-row[data-code]').forEach(row=>row.addEventListener('click',()=>openDetail(row.dataset.code)));
}
function renderStatus(){
  const snapshot=state.snapshot;
  const entries=[['指数',snapshot.indicesMeta],['宽度',snapshot.breadth.meta],['情绪',snapshot.sentiment.meta],['新闻',snapshot.marketNewsMeta]];
  $('#statusGrid').innerHTML=entries.map(([label,meta])=>`<div class="status-metric"><span>${label}</span><strong class="${statusClass(meta.status)}">${escapeHtml(statusText(meta))}</strong></div>`).join('');
  const breadth=snapshot.breadth,sentiment=snapshot.sentiment,leading=snapshot.indices[0];
  $('#aiSummary').textContent=leading?`${leading.name} ${leading.value}，涨跌幅 ${leading.changePercent}；全市场上涨 ${breadth.upCount??'--'} 家、下跌 ${breadth.downCount??'--'} 家，情绪温度 ${temperature(sentiment.temperature)}。`:'真实市场数据暂未完整返回。';
}
function renderAll(){renderHeader();renderIndices();renderBreadth();renderQuickGrid();renderToolContent();renderStatus()}

function updateDebugStatus(){
  const status=$('#dataStatus');
  if(state.loading){status.innerHTML='<strong>正在连接</strong>：读取真实指数、宽度、榜单、板块与新闻。';return}
  if(state.lastError){status.innerHTML=`<strong>刷新失败</strong>：${escapeHtml(state.lastError)}\n保留上一份真实成功数据。`;return}
  const time=state.lastSuccessAt?new Intl.DateTimeFormat('zh-CN',{hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(new Date(state.lastSuccessAt)):'--';
  status.innerHTML=`<strong>真实后端已连接</strong>\n更新时间 ${time}\n指数 ${state.snapshot.indices.length} · 榜单 ${state.snapshot.boards.length} · 板块 ${state.snapshot.sectors.length}\n请求 ${state.requestCount} 次`;
}
async function fetchJson(url,timeoutMs=35000){
  const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),timeoutMs);
  try{const response=await fetch(url,{signal:controller.signal,cache:'no-store',headers:{'Cache-Control':'no-cache'}});if(!response.ok)throw new Error(`HTTP ${response.status}`);return await response.json()}finally{clearTimeout(timer)}
}
async function loadMarketHome(silent=false){
  if(state.loading)return;
  state.loading=true;state.lastError='';renderHeader();if(!silent)updateDebugStatus();
  try{
    const root=await fetchJson(`${MARKET_HOME_API}?_=${Date.now()}`);
    const snapshot=parseMarketHome(root);
    if(!snapshot.indices.length&&!hasRealData(snapshot.breadth.meta)&&!snapshot.boards.length)throw new Error('市场首页接口未返回可展示数据');
    state.snapshot=snapshot;state.lastSuccessAt=Date.now();state.requestCount++;
  }catch(error){state.lastError=error?.name==='AbortError'?'请求超时':error?.message||String(error)}
  finally{state.loading=false;renderAll();updateDebugStatus();scheduleRefresh()}
}
function scheduleRefresh(){clearTimeout(state.timer);if(!state.autoRefresh)return;state.timer=setTimeout(()=>loadMarketHome(true),20000)}
function openDetail(code){const query=text(code,$('#query').value.trim()||'600396');location.href=`./stock-detail-web-preview.html?query=${encodeURIComponent(query)}`}
function openIndexDetail(code){const query=text(code,'000001');location.href=`./stock-index-web-preview.html?query=${encodeURIComponent(query)}`}
function installClock(){const update=()=>{$('#clock').textContent=new Intl.DateTimeFormat('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date())};update();setInterval(update,30000)}

$('#searchForm').addEventListener('submit',event=>{event.preventDefault();openDetail($('#query').value.trim())});
$('#refreshButton').addEventListener('click',()=>loadMarketHome(false));
$('#manualRefresh').addEventListener('click',()=>loadMarketHome(false));
$('#openDetail').addEventListener('click',()=>openDetail($('#query').value.trim()));
$('#backButton').addEventListener('click',()=>{if(history.length>1)history.back();else $('#marketScroll').scrollTo({top:0,behavior:'smooth'})});
$('#aiWatchButton').addEventListener('click',()=>$('#dataStatus').innerHTML+='<br>AI 看盘入口在网页调试版中保留交互占位。');
$('#autoRefresh').addEventListener('change',event=>{state.autoRefresh=event.target.checked;scheduleRefresh()});
$('#glassRange').addEventListener('input',event=>{document.documentElement.style.setProperty('--glass',Number(event.target.value)/100);$('#glassText').textContent=`${event.target.value}%`});
$('#phoneWidth').addEventListener('input',event=>{document.documentElement.style.setProperty('--phone-w',`${event.target.value}px`);$('#phoneWidthText').textContent=`${event.target.value}px`});
$('#radiusRange').addEventListener('input',event=>{document.documentElement.style.setProperty('--radius',`${event.target.value}px`);$('#radiusText').textContent=`${event.target.value}px`});
$('#mobileToggle').addEventListener('click',()=>document.body.classList.toggle('controls-open'));
document.addEventListener('visibilitychange',()=>{if(!document.hidden&&state.autoRefresh)loadMarketHome(true)});

installClock();renderAll();updateDebugStatus();setTimeout(()=>loadMarketHome(false),180);

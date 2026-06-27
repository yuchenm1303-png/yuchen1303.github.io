'use strict';

const INDEX_HISTORY_API='https://ai-ledger-stock-proxy.onrender.com/api/stock/a-share/kline';
const INDEX_HISTORY_LIMIT=600;
state.indexHistoryCache=state.indexHistoryCache||new Map();
state.indexHistoryLoading=false;
state.indexKBaseCount=72;
state.indexKZoom=1;
state.indexKPan=0;
state.indexKDragStartX=null;
state.indexKDragStartPan=0;

function indexHistoryWindow(candles){
  if(!candles.length)return{start:0,end:0,visible:[]};
  const base=Math.min(state.indexKBaseCount,candles.length);
  const minimum=Math.min(12,candles.length);
  const count=Math.max(minimum,Math.min(candles.length,Math.round(base/state.indexKZoom)));
  const maxPan=Math.max(0,candles.length-count);
  state.indexKPan=Math.max(0,Math.min(maxPan,state.indexKPan));
  const end=Math.max(count,Math.min(candles.length,candles.length-Math.round(state.indexKPan)));
  const start=Math.max(0,end-count);
  return{start,end,visible:candles.slice(start,end),count,maxPan};
}

function currentIndexHistory(fallback){
  const cached=state.indexHistoryCache.get(state.code);
  return cached?.length?cached:safePoints(fallback);
}

async function loadExtendedIndexHistory(force=false){
  if(state.indexHistoryLoading)return;
  if(!force&&state.indexHistoryCache.has(state.code)){renderChart();return}
  state.indexHistoryLoading=true;
  if(state.tab==='daily')showChartEmpty('正在加载 600 根真实指数日K…');
  try{
    const payload=await fetchJson(`${INDEX_HISTORY_API}?query=${encodeURIComponent(state.code)}&instrument=index&period=daily&limit=${INDEX_HISTORY_LIMIT}&_=${Date.now()}`,30000);
    const rows=safePoints(payload?.kLinePoints).filter(row=>number(row.open)>0&&number(row.close)>0);
    if(rows.length<2)throw new Error('指数扩展日K数据不足');
    state.indexHistoryCache.set(state.code,rows);
    state.indexKZoom=1;
    state.indexKPan=0;
  }catch(error){
    state.error=error?.name==='AbortError'?'指数日K请求超时':error?.message||String(error);
  }finally{
    state.indexHistoryLoading=false;
    if(state.tab==='daily')renderChart();
    updateDebugStatus();
  }
}

drawDaily=function(points){
  const candles=currentIndexHistory(points).filter(row=>number(row.open)>0&&number(row.close)>0);
  const windowData=indexHistoryWindow(candles),data=windowData.visible;
  if(data.length<2){showChartEmpty(state.indexHistoryLoading?'正在加载真实指数日K':'指数日K数据暂不可用');return}
  $('#chartEmpty').style.display='none';
  const{ctx,width,height}=resizeCanvas();
  const left=42,right=width-10,top=12,priceBottom=Math.max(top+60,height-72),volumeTop=priceBottom+12,volumeBottom=height-12;
  const highs=data.map(row=>number(row.high)||0),lows=data.map(row=>number(row.low)||0);
  let min=Math.min(...lows),max=Math.max(...highs);
  const padding=Math.max((max-min)*.08,max*.002,1);min-=padding;max+=padding;
  const slot=(right-left)/data.length,candle=Math.max(1.5,Math.min(8,slot*.56));
  const x=index=>left+slot*(index+.5),y=value=>top+(max-value)/(max-min)*(priceBottom-top);
  drawGrid(ctx,left,top,right,priceBottom);
  const maxVolume=Math.max(...data.map(row=>number(row.volume)||0),1);
  data.forEach((row,index)=>{
    const open=number(row.open),close=number(row.close),high=number(row.high),low=number(row.low);
    const rise=close>=open,color=rise?'#ff7180':'#52e9a3',px=x(index);
    ctx.strokeStyle=color;ctx.fillStyle=color;ctx.lineWidth=1;
    ctx.beginPath();ctx.moveTo(px,y(high));ctx.lineTo(px,y(low));ctx.stroke();
    const bodyTop=Math.min(y(open),y(close)),bodyHeight=Math.max(1,Math.abs(y(open)-y(close)));
    ctx.fillRect(px-candle/2,bodyTop,candle,bodyHeight);
    const volume=number(row.volume)||0,barHeight=volume/maxVolume*(volumeBottom-volumeTop);
    ctx.globalAlpha=.62;ctx.fillRect(px-candle/2,volumeBottom-barHeight,candle,Math.max(1,barHeight));ctx.globalAlpha=1;
  });
  ctx.save();ctx.fillStyle='rgba(255,255,255,.42)';ctx.font='8px system-ui';ctx.textAlign='right';
  ctx.fillText(max.toFixed(2),left-5,top+4);ctx.fillText(((max+min)/2).toFixed(2),left-5,(top+priceBottom)/2+3);ctx.fillText(min.toFixed(2),left-5,priceBottom);ctx.restore();
  const latest=data.at(-1),middle=data[Math.floor(data.length/2)];
  $('#chartTitle').textContent='日K走势';
  $('#chartAverage').textContent=`开 ${number(latest.open)?.toFixed(2)??'--'}`;
  $('#chartLatest').textContent=`收 ${number(latest.close)?.toFixed(2)??'--'}`;
  $('#chartRange').textContent=`高 ${number(latest.high)?.toFixed(2)??'--'} · 低 ${number(latest.low)?.toFixed(2)??'--'}`;
  $('#indexAxis').innerHTML=`<span>${escapeHtml(text(data[0].date,''))}</span><span>${escapeHtml(text(middle.date,''))}</span><span>${escapeHtml(text(latest.date,''))}</span>`;
  $('#indexCaption').textContent=`共 ${candles.length} 根真实指数日K · 当前显示 ${data.length} 根 · 滚轮缩放、拖动查看历史。`;
};

const baseSwitchIndex=window.switchIndex||switchIndex;
switchIndex=function(code,pushHistory){
  state.indexKZoom=1;state.indexKPan=0;state.indexKDragStartX=null;
  baseSwitchIndex(code,pushHistory);
  if(state.tab==='daily')setTimeout(()=>loadExtendedIndexHistory(false),0);
};

function installIndexHistoryInteractions(){
  const wrap=$('#chartWrap');
  wrap.addEventListener('wheel',event=>{
    if(state.tab!=='daily')return;
    event.preventDefault();
    state.indexKZoom=Math.max(1,Math.min(6,state.indexKZoom*(event.deltaY<0?1.16:.86)));
    renderChart();
  },{passive:false});
  wrap.addEventListener('pointerdown',event=>{
    if(state.tab!=='daily')return;
    wrap.setPointerCapture(event.pointerId);
    state.indexKDragStartX=event.clientX;
    state.indexKDragStartPan=state.indexKPan;
  });
  wrap.addEventListener('pointermove',event=>{
    if(state.tab!=='daily'||state.indexKDragStartX==null)return;
    const windowData=indexHistoryWindow(currentIndexHistory(state.payload?.kLinePoints));
    const step=Math.max(1,wrap.clientWidth/Math.max(windowData.visible.length,1));
    state.indexKPan=state.indexKDragStartPan+(event.clientX-state.indexKDragStartX)/step;
    renderChart();
  });
  wrap.addEventListener('pointerup',()=>{state.indexKDragStartX=null});
  wrap.addEventListener('pointercancel',()=>{state.indexKDragStartX=null});
  wrap.addEventListener('dblclick',event=>{
    if(state.tab!=='daily')return;
    event.preventDefault();state.indexKZoom=1;state.indexKPan=0;renderChart();
  });
}

$('#indexTabs').addEventListener('click',event=>{
  const button=event.target.closest('[data-tab]');
  if(!button)return;
  if(button.dataset.tab==='daily')setTimeout(()=>loadExtendedIndexHistory(false),0);
});
installIndexHistoryInteractions();

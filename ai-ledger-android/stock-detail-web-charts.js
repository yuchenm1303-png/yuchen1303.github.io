function canvasContext(){
  const canvas=$('#chart'),rect=canvas.getBoundingClientRect(),dpr=Math.min(devicePixelRatio||1,2);
  canvas.width=Math.max(1,Math.round(rect.width*dpr)); canvas.height=Math.max(1,Math.round(rect.height*dpr));
  const ctx=canvas.getContext('2d');ctx.setTransform(dpr,0,0,dpr,0,0);return {ctx,width:rect.width,height:rect.height};
}
function drawGrid(ctx,width,chartHeight,verticals=5){
  for(let i=1;i<=4;i++){const y=chartHeight*i/5;ctx.strokeStyle=COLORS.grid;ctx.lineWidth=1;ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(width,y);ctx.stroke()}
  for(let i=1;i<=verticals;i++){const x=width*i/(verticals+1);ctx.strokeStyle='rgba(255,255,255,.06)';ctx.beginPath();ctx.moveTo(x,0);ctx.lineTo(x,chartHeight);ctx.stroke()}
}
function sessionFraction(point){
  const minute=point.minuteOfDay,second=(point.secondOfDay%60)/60;if(!Number.isFinite(minute)||minute<555||minute>900)return null;
  const t=minute+second,ow=state.openWidth,cw=state.closeWidth,ce=1-cw,split=ow+(ce-ow)*.5;
  if(t<=565)return Math.max(0,(t-555)/10*ow);
  if(t<570)return null;
  if(t<=690)return ow+(t-570)/120*(split-ow);
  if(t<780)return null;
  if(t<897)return split+(t-780)/117*(ce-split);
  return ce+(t-897)/3*cw;
}
function fiveDayDates(points){return [...new Set(points.map(p=>p.date).filter(Boolean))].sort().slice(-5)}
function positionMinutePoints(points,isFiveDay){
  if(!isFiveDay)return points.map(point=>({point,x:sessionFraction(point),slot:0})).filter(x=>x.x!=null).sort((a,b)=>a.x-b.x);
  const dates=fiveDayDates(points),firstSlot=Math.max(0,5-dates.length),map=new Map(dates.map((date,index)=>[date,firstSlot+index]));
  return points.map(point=>{const slot=map.get(point.date)??4,f=sessionFraction(point);return f==null?null:{point,x:(slot+f)/5,slot}}).filter(Boolean).sort((a,b)=>a.x-b.x);
}
function drawSelectedChart(){
  $('#chartOverlay').textContent='';
  if(isMinuteTab(state.selectedTab))drawTimeShareChart(state.selectedTab==='五日');else drawKlineChart();
}
function drawTimeShareChart(isFiveDay){
  const {ctx,width,height}=canvasContext();ctx.clearRect(0,0,width,height);
  const volumeHeight=height*state.volumeFraction,gap=8,chartHeight=height-volumeHeight-gap,volumeTop=chartHeight+gap;
  const source=currentMinutePoints(),positioned=positionMinutePoints(source,isFiveDay),points=positioned.map(x=>x.point);
  drawGrid(ctx,width,chartHeight,isFiveDay?4:2);
  if(isFiveDay){for(let i=1;i<5;i++){const x=width*i/5;ctx.strokeStyle='rgba(255,255,255,.10)';ctx.beginPath();ctx.moveTo(x,0);ctx.lineTo(x,height);ctx.stroke()}}
  else{
    const openX=width*state.openWidth,closeX=width*(1-state.closeWidth),lunchX=width*(state.openWidth+(1-state.closeWidth-state.openWidth)*.5);
    ctx.fillStyle='rgba(245,247,255,.045)';ctx.fillRect(0,0,openX,chartHeight);ctx.fillStyle='rgba(141,249,234,.045)';ctx.fillRect(closeX,0,width-closeX,chartHeight);
    [openX,lunchX,closeX].forEach(x=>{ctx.strokeStyle='rgba(255,255,255,.13)';ctx.beginPath();ctx.moveTo(x,0);ctx.lineTo(x,height);ctx.stroke()});
  }
  ctx.strokeStyle='rgba(255,255,255,.13)';ctx.strokeRect(.5,volumeTop-.5,width-1,volumeHeight);
  if(positioned.length<2){ctx.fillStyle='rgba(255,255,255,.42)';ctx.font='12px system-ui';ctx.textAlign='center';ctx.fillText(isFiveDay?'暂无真实五日分时数据':'暂无真实分时数据',width/2,chartHeight/2);setAxis(isFiveDay?Array.from({length:5},(_,i)=>({label:'--',x:i/4})):[{label:'09:15',x:0},{label:'09:30',x:.14},{label:'11:30/13:00',x:.545},{label:'14:57',x:.95},{label:'15:00',x:1}]);renderCaption(['均价线','成交量','等待数据']);return}
  const q=state.quote,previous=number(q.previousClose)??points[0].price;
  const values=points.flatMap(p=>p.phase==='continuous'?[p.price,p.average]:[p.price]).filter(Number.isFinite);const rawMin=Math.min(...values),rawMax=Math.max(...values);
  let min,max;
  if(isFiveDay){const pad=Math.max((rawMax-rawMin)*.08,rawMax*.002,.01);min=rawMin-pad;max=rawMax+pad}else{const limitRatio=/ST/i.test(q.name)?.05:.10,observed=Math.max(...values.map(v=>Math.abs(v-previous))),half=Math.max(previous*limitRatio,observed,.01);min=previous-half;max=previous+half}
  const range=Math.max(max-min,.0001),y=value=>chartHeight-(value-min)/range*chartHeight,x=item=>item.x*width;
  if(!isFiveDay){ctx.save();ctx.setLineDash([5,5]);ctx.strokeStyle='rgba(255,255,255,.22)';ctx.beginPath();ctx.moveTo(0,y(previous));ctx.lineTo(width,y(previous));ctx.stroke();ctx.restore();if(q.price!=null){ctx.save();ctx.setLineDash([3,4]);ctx.strokeStyle='rgba(255,202,92,.55)';ctx.beginPath();ctx.moveTo(width*state.openWidth,y(q.price));ctx.lineTo(width,y(q.price));ctx.stroke();ctx.restore()}}
  const maxVolume=Math.max(...points.map(p=>p.volume||0),1);
  positioned.forEach(item=>{const top=height-(item.point.volume/maxVolume)*volumeHeight*.88;ctx.strokeStyle=item.point.price>=previous?COLORS.riseSoft:COLORS.fallSoft;ctx.lineWidth=isFiveDay?1:1.1;ctx.beginPath();ctx.moveTo(x(item),height);ctx.lineTo(x(item),Math.max(volumeTop,top));ctx.stroke()});
  if(!isFiveDay)drawAuctionVolumes(ctx,positioned,width,height,volumeTop,volumeHeight);
  function line(selector,color,lineWidth,include=()=>true){
    let started=false,lastSlot=-1;ctx.strokeStyle=color;ctx.lineWidth=lineWidth;ctx.lineCap='round';ctx.lineJoin='round';ctx.beginPath();
    positioned.forEach(item=>{if(!include(item.point)){started=false;lastSlot=item.slot;return}const py=y(selector(item.point));if(!started||item.slot!==lastSlot){ctx.moveTo(x(item),py);started=true}else ctx.lineTo(x(item),py);lastSlot=item.slot});ctx.stroke();
  }
  line(p=>p.average,COLORS.yellow,1.6,p=>p.phase==='continuous');line(p=>p.price,quoteTone()==='rise'?COLORS.rise:COLORS.fall,2.4);
  if(!isFiveDay){const limitRatio=/ST/i.test(q.name)?.05:.10;ctx.font='8px system-ui';ctx.textBaseline='middle';ctx.textAlign='left';ctx.fillStyle=COLORS.rise;ctx.fillText(fmt(previous*(1+limitRatio)),width*state.openWidth+5,10);ctx.fillStyle='rgba(255,255,255,.54)';ctx.fillText(fmt(previous),width*state.openWidth+5,y(previous)-5);ctx.fillStyle=COLORS.fall;ctx.fillText(fmt(previous*(1-limitRatio)),width*state.openWidth+5,chartHeight-8);ctx.textAlign='right';ctx.fillStyle=COLORS.rise;ctx.fillText(`+${(limitRatio*100).toFixed(2)}%`,width-4,10);ctx.fillStyle='rgba(255,255,255,.54)';ctx.fillText('0.00%',width-4,y(previous)-5);ctx.fillStyle=COLORS.fall;ctx.fillText(`-${(limitRatio*100).toFixed(2)}%`,width-4,chartHeight-8)}
  if(isFiveDay){const dates=fiveDayDates(points),labels=Array.from({length:5},()=> '--'),first=Math.max(0,5-dates.length);dates.forEach((date,i)=>labels[first+i]=date.slice(5));setAxis(labels.map((label,i)=>({label,x:i/4})));renderCaption(['五日均价','五日成交量',`${dates.length}日真实数据`])}
  else{setAxis([{label:'09:15',x:0},{label:'09:30',x:.14},{label:'11:30/13:00',x:.545},{label:'14:57',x:.95},{label:'15:00',x:1}]);renderCaption(['红/绿未匹配量：上沿向下','白色匹配量：下沿向上','首尾集合竞价'])}
}
function drawAuctionVolumes(ctx,positioned,width,height,volumeTop,volumeHeight){
  const buckets=new Map();
  positioned.filter(item=>item.point.phase!=='continuous').forEach(item=>{const p=item.point;if(!(p.matchedVolume>0)&&!(p.unmatchedVolume>0))return;const x=Math.round(item.x*width),key=`${p.phase}:${x}`,old=buckets.get(key)??{x,phase:p.phase,matched:0,unmatched:0,direction:'unavailable'};if(p.matchedVolume>old.matched)old.matched=p.matchedVolume;if(p.unmatchedVolume>old.unmatched){old.unmatched=p.unmatchedVolume;old.direction=p.unmatchedDirection}buckets.set(key,old)});
  const bars=[...buckets.values()].sort((a,b)=>a.x-b.x),unmatchedMax=Math.max(...bars.map(b=>b.unmatched),0),matchedMax=Math.max(...bars.map(b=>b.matched),0);
  const segments=(items,keyFn)=>{const result=[];let active=[],last=null;items.forEach(item=>{const key=keyFn(item);if(!active.length||(last&&key===last.key&&item.phase===last.phase&&item.x-last.x<=8))active.push(item);else{result.push(active);active=[item]}last={key,phase:item.phase,x:item.x}});if(active.length)result.push(active);return result};
  if(unmatchedMax>0)segments(bars.filter(b=>b.unmatched>0),b=>b.direction).forEach(segment=>{ctx.beginPath();ctx.moveTo(segment[0].x,volumeTop);segment.forEach(b=>ctx.lineTo(b.x,volumeTop+b.unmatched/unmatchedMax*volumeHeight*.46));ctx.lineTo(segment.at(-1).x,volumeTop);ctx.closePath();ctx.fillStyle=segment[0].direction==='sell'?'rgba(42,194,82,.88)':'rgba(255,57,52,.90)';ctx.fill()});
  if(matchedMax>0)segments(bars.filter(b=>b.matched>0),()=> 'matched').forEach(segment=>{ctx.beginPath();ctx.moveTo(segment[0].x,height);segment.forEach(b=>ctx.lineTo(b.x,height-b.matched/matchedMax*volumeHeight*.46));ctx.lineTo(segment.at(-1).x,height);ctx.closePath();ctx.fillStyle='rgba(243,245,255,.68)';ctx.fill();ctx.beginPath();segment.forEach((b,i)=>i?ctx.lineTo(b.x,height-b.matched/matchedMax*volumeHeight*.46):ctx.moveTo(b.x,height-b.matched/matchedMax*volumeHeight*.46));ctx.strokeStyle='rgba(255,255,255,.80)';ctx.lineWidth=.75;ctx.stroke()});
}

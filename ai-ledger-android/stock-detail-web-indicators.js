'use strict';

function numericSeries(candles,key){return candles.map(item=>number(item?.[key]))}
function latestFinite(series){for(let index=series.length-1;index>=0;index--){if(Number.isFinite(series[index]))return series[index]}return null}
function simpleMovingAverage(values,period){
  const result=Array(values.length).fill(null);let sum=0,valid=0;
  values.forEach((raw,index)=>{const value=number(raw);if(value!=null){sum+=value;valid++}if(index>=period){const removed=number(values[index-period]);if(removed!=null){sum-=removed;valid--}}if(index>=period-1&&valid===period)result[index]=sum/period});return result;
}
function movingAverage(candles,period){return simpleMovingAverage(numericSeries(candles,'close'),period)}
function exponentialMovingAverage(values,period){
  const result=Array(values.length).fill(null),alpha=2/(period+1);let previous=null;
  values.forEach((raw,index)=>{const value=number(raw);if(value==null)return;previous=previous==null?value:previous+alpha*(value-previous);result[index]=previous});return result;
}
function calculateMacd(candles){
  const close=numericSeries(candles,'close'),ema12=exponentialMovingAverage(close,12),ema26=exponentialMovingAverage(close,26);
  const dif=close.map((_,index)=>Number.isFinite(ema12[index])&&Number.isFinite(ema26[index])?ema12[index]-ema26[index]:null);
  const dea=exponentialMovingAverage(dif,9);const histogram=dif.map((value,index)=>Number.isFinite(value)&&Number.isFinite(dea[index])?(value-dea[index])*2:null);
  return{dif,dea,histogram};
}
function calculateKdj(candles,period=9){
  const k=Array(candles.length).fill(null),d=Array(candles.length).fill(null),j=Array(candles.length).fill(null);let previousK=50,previousD=50;
  candles.forEach((candle,index)=>{const start=Math.max(0,index-period+1),window=candles.slice(start,index+1),highest=Math.max(...window.map(item=>item.high)),lowest=Math.min(...window.map(item=>item.low)),range=highest-lowest,rsv=range>0?(candle.close-lowest)/range*100:50;previousK=previousK*2/3+rsv/3;previousD=previousD*2/3+previousK/3;k[index]=previousK;d[index]=previousD;j[index]=3*previousK-2*previousD});return{k,d,j};
}
function calculateRsi(candles,period){
  const result=Array(candles.length).fill(null);if(candles.length<2)return result;let avgGain=0,avgLoss=0;
  for(let index=1;index<candles.length;index++){
    const change=candles[index].close-candles[index-1].close,gain=Math.max(change,0),loss=Math.max(-change,0);
    if(index<=period){avgGain+=gain;avgLoss+=loss;if(index===period){avgGain/=period;avgLoss/=period;result[index]=avgLoss===0?100:100-100/(1+avgGain/avgLoss)}}else{avgGain=(avgGain*(period-1)+gain)/period;avgLoss=(avgLoss*(period-1)+loss)/period;result[index]=avgLoss===0?100:100-100/(1+avgGain/avgLoss)}
  }
  return result;
}
function calculateBoll(candles,period=20,multiplier=2){
  const close=numericSeries(candles,'close'),mid=simpleMovingAverage(close,period),upper=Array(candles.length).fill(null),lower=Array(candles.length).fill(null),bandwidth=Array(candles.length).fill(null),percentB=Array(candles.length).fill(null);
  for(let index=period-1;index<candles.length;index++){
    const mean=mid[index];if(!Number.isFinite(mean))continue;const window=close.slice(index-period+1,index+1);if(window.some(value=>!Number.isFinite(value)))continue;const variance=window.reduce((sum,value)=>sum+(value-mean)**2,0)/period,std=Math.sqrt(variance);upper[index]=mean+multiplier*std;lower[index]=mean-multiplier*std;bandwidth[index]=mean!==0?(upper[index]-lower[index])/mean*100:null;percentB[index]=upper[index]!==lower[index]?(close[index]-lower[index])/(upper[index]-lower[index]):.5;
  }
  return{mid,upper,lower,bandwidth,percentB};
}
function indicatorSnapshot(candles,type){
  if(!candles.length)return{label:type,values:[]};
  if(type==='KDJ'){const data=calculateKdj(candles);return{label:'KDJ(9,3,3)',values:[['K',latestFinite(data.k)],['D',latestFinite(data.d)],['J',latestFinite(data.j)]],data}}
  if(type==='RSI'){const rsi6=calculateRsi(candles,6),rsi12=calculateRsi(candles,12),rsi24=calculateRsi(candles,24);return{label:'RSI',values:[['R6',latestFinite(rsi6)],['R12',latestFinite(rsi12)],['R24',latestFinite(rsi24)]],data:{rsi6,rsi12,rsi24}}}
  if(type==='BOLL'){const data=calculateBoll(candles);return{label:'BOLL(20,2)',values:[['UP',latestFinite(data.upper)],['MID',latestFinite(data.mid)],['LOW',latestFinite(data.lower)],['BW',latestFinite(data.bandwidth)]],data}}
  const data=calculateMacd(candles);return{label:'MACD(12,26,9)',values:[['DIF',latestFinite(data.dif)],['DEA',latestFinite(data.dea)],['MACD',latestFinite(data.histogram)]],data};
}

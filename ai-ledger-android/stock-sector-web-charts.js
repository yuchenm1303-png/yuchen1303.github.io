'use strict';

function resizeCanvas() {
  const canvas = $('#sectorChart');
  const rect = canvas.getBoundingClientRect();
  const dpr = Math.max(1, window.devicePixelRatio || 1);
  const width = Math.max(1, Math.floor(rect.width));
  const height = Math.max(1, Math.floor(rect.height));
  if (canvas.width !== Math.floor(width * dpr) || canvas.height !== Math.floor(height * dpr)) {
    canvas.width = Math.floor(width * dpr);
    canvas.height = Math.floor(height * dpr);
  }
  const ctx = canvas.getContext('2d');
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, width, height);
  return { ctx, width, height };
}

function showChartEmpty(message) {
  $('#chartEmpty').textContent = message;
  $('#chartEmpty').style.display = 'grid';
  resizeCanvas();
  $('#sectorAxis').innerHTML = '';
}

function drawGrid(ctx, left, top, right, bottom, columns = 4) {
  ctx.save();
  ctx.strokeStyle = 'rgba(255,255,255,.075)';
  ctx.lineWidth = 1;
  for (let index = 0; index <= 4; index++) {
    const y = top + (bottom - top) * index / 4;
    ctx.beginPath(); ctx.moveTo(left, y); ctx.lineTo(right, y); ctx.stroke();
  }
  for (let index = 0; index <= columns; index++) {
    const x = left + (right - left) * index / columns;
    ctx.beginPath(); ctx.moveTo(x, top); ctx.lineTo(x, bottom); ctx.stroke();
  }
  ctx.restore();
}

function drawMinute() {
  const data = safeArray(state.payload?.minutePoints).filter(point => number(point.price) > 0);
  if (!data.length) { showChartEmpty('板块真实分时数据暂不可用'); return; }
  $('#chartEmpty').style.display = 'none';
  const { ctx, width, height } = resizeCanvas();
  const left = 42, right = width - 10, top = 12;
  const priceBottom = Math.max(top + 60, height - 72);
  const volumeTop = priceBottom + 12, volumeBottom = height - 12;
  const previous = number(state.payload?.quote?.previousClose);
  const prices = data.map(point => number(point.price)).filter(Number.isFinite);
  const rangeValues = previous ? [...prices, previous] : prices;
  let min = Math.min(...rangeValues), max = Math.max(...rangeValues);
  const padding = Math.max((max - min) * .12, max * .0025, 1);
  min -= padding; max += padding;
  const x = index => left + (right - left) * (data.length === 1 ? 0 : index / (data.length - 1));
  const y = value => top + (max - value) / (max - min) * (priceBottom - top);
  drawGrid(ctx, left, top, right, priceBottom);
  if (previous) {
    ctx.save(); ctx.setLineDash([4, 4]); ctx.strokeStyle = 'rgba(255,255,255,.26)';
    ctx.beginPath(); ctx.moveTo(left, y(previous)); ctx.lineTo(right, y(previous)); ctx.stroke(); ctx.restore();
  }
  const maxVolume = Math.max(...data.map(point => number(point.volume) || 0), 1);
  const barWidth = Math.max(1, (right - left) / data.length * .72);
  data.forEach((point, index) => {
    const volume = number(point.volume) || 0;
    const barHeight = volume / maxVolume * (volumeBottom - volumeTop);
    const current = number(point.price) || 0;
    const prior = index ? number(data[index - 1].price) : previous;
    ctx.fillStyle = current >= (prior ?? current) ? 'rgba(255,112,127,.72)' : 'rgba(82,233,163,.70)';
    ctx.fillRect(x(index) - barWidth / 2, volumeBottom - barHeight, barWidth, Math.max(1, barHeight));
  });
  ctx.save(); ctx.strokeStyle = '#70d8ff'; ctx.lineWidth = 1.8; ctx.beginPath();
  data.forEach((point, index) => index ? ctx.lineTo(x(index), y(number(point.price))) : ctx.moveTo(x(index), y(number(point.price))));
  ctx.stroke(); ctx.restore();
  ctx.save(); ctx.strokeStyle = '#ffd86b'; ctx.lineWidth = 1.2; ctx.beginPath();
  let started = false;
  data.forEach((point, index) => {
    const value = number(point.average);
    if (value == null) return;
    started ? ctx.lineTo(x(index), y(value)) : ctx.moveTo(x(index), y(value));
    started = true;
  });
  if (started) ctx.stroke();
  ctx.restore();
  ctx.save(); ctx.fillStyle = 'rgba(255,255,255,.42)'; ctx.font = '8px system-ui'; ctx.textAlign = 'right';
  ctx.fillText(max.toFixed(2), left - 5, top + 4);
  ctx.fillText(((max + min) / 2).toFixed(2), left - 5, (top + priceBottom) / 2 + 3);
  ctx.fillText(min.toFixed(2), left - 5, priceBottom); ctx.restore();
  const latest = data.at(-1);
  $('#chartTitle').textContent = '板块分时';
  $('#chartSecondary').textContent = `均价 ${formatPrice(latest.average)}`;
  $('#chartLatest').textContent = `最新 ${formatPrice(latest.price)}`;
  $('#chartRange').textContent = `${min.toFixed(2)} - ${max.toFixed(2)}`;
  $('#sectorAxis').innerHTML = '<span>09:30</span><span>11:30 / 13:00</span><span>15:00</span>';
  $('#sectorCaption').textContent = '真实板块分时、均价线与分钟成交量。';
}

function currentKlines() { return state.klineCache.get(`${state.code}:${state.tab}`) || []; }

function klineWindow(candles) {
  if (!candles.length) return { start: 0, end: 0, visible: [] };
  const base = Math.min(state.kBaseCount, candles.length);
  const minimum = Math.min(12, candles.length);
  const count = Math.max(minimum, Math.min(candles.length, Math.round(base / state.kZoom)));
  const maxPan = Math.max(0, candles.length - count);
  state.kPan = Math.max(0, Math.min(maxPan, state.kPan));
  const end = Math.max(count, Math.min(candles.length, candles.length - Math.round(state.kPan)));
  const start = Math.max(0, end - count);
  return { start, end, visible: candles.slice(start, end), count, maxPan };
}

function drawKline() {
  const candles = currentKlines();
  const data = klineWindow(candles).visible;
  const label = state.tab === 'daily' ? '日K' : state.tab === 'weekly' ? '周K' : '月K';
  if (data.length < 2) { showChartEmpty(state.loadingKline ? `正在加载真实${label}` : '历史K线暂不可用'); return; }
  $('#chartEmpty').style.display = 'none';
  const { ctx, width, height } = resizeCanvas();
  const left = 42, right = width - 10, top = 12;
  const priceBottom = Math.max(top + 60, height - 72);
  const volumeTop = priceBottom + 12, volumeBottom = height - 12;
  const highs = data.map(row => number(row.high) || 0), lows = data.map(row => number(row.low) || 0);
  let min = Math.min(...lows), max = Math.max(...highs);
  const padding = Math.max((max - min) * .08, max * .002, 1);
  min -= padding; max += padding;
  const slot = (right - left) / data.length;
  const candle = Math.max(1.5, Math.min(8, slot * .56));
  const x = index => left + slot * (index + .5);
  const y = value => top + (max - value) / (max - min) * (priceBottom - top);
  drawGrid(ctx, left, top, right, priceBottom);
  const maxVolume = Math.max(...data.map(row => number(row.volume) || 0), 1);
  data.forEach((row, index) => {
    const open = number(row.open), close = number(row.close), high = number(row.high), low = number(row.low);
    const rising = close >= open, color = rising ? '#ff7180' : '#52e9a3', px = x(index);
    ctx.strokeStyle = color; ctx.fillStyle = color; ctx.lineWidth = 1;
    ctx.beginPath(); ctx.moveTo(px, y(high)); ctx.lineTo(px, y(low)); ctx.stroke();
    const bodyTop = Math.min(y(open), y(close));
    ctx.fillRect(px - candle / 2, bodyTop, candle, Math.max(1, Math.abs(y(open) - y(close))));
    const barHeight = (number(row.volume) || 0) / maxVolume * (volumeBottom - volumeTop);
    ctx.globalAlpha = .62; ctx.fillRect(px - candle / 2, volumeBottom - barHeight, candle, Math.max(1, barHeight)); ctx.globalAlpha = 1;
  });
  ctx.save(); ctx.fillStyle = 'rgba(255,255,255,.42)'; ctx.font = '8px system-ui'; ctx.textAlign = 'right';
  ctx.fillText(max.toFixed(2), left - 5, top + 4);
  ctx.fillText(((max + min) / 2).toFixed(2), left - 5, (top + priceBottom) / 2 + 3);
  ctx.fillText(min.toFixed(2), left - 5, priceBottom); ctx.restore();
  const latest = data.at(-1), middle = data[Math.floor(data.length / 2)];
  $('#chartTitle').textContent = `板块${label}`;
  $('#chartSecondary').textContent = `开 ${formatPrice(latest.open)}`;
  $('#chartLatest').textContent = `收 ${formatPrice(latest.close)}`;
  $('#chartRange').textContent = `高 ${formatPrice(latest.high)} · 低 ${formatPrice(latest.low)}`;
  $('#sectorAxis').innerHTML = `<span>${escapeHtml(formatDate(data[0].date))}</span><span>${escapeHtml(formatDate(middle.date))}</span><span>${escapeHtml(formatDate(latest.date))}</span>`;
  $('#sectorCaption').textContent = `共 ${candles.length} 根真实${label} · 当前显示 ${data.length} 根 · 滚轮缩放、拖动查看历史。`;
}

function renderChart() {
  renderTabs();
  state.tab === 'minute' ? drawMinute() : drawKline();
}

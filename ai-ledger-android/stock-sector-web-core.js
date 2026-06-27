'use strict';

const API_BASE = 'https://ai-ledger-stock-proxy.onrender.com';
const DETAIL_API = `${API_BASE}/api/stock/a-share/sector/detail`;
const CONSTITUENTS_API = `${API_BASE}/api/stock/a-share/sector/constituents`;
const KLINE_API = `${API_BASE}/api/stock/a-share/kline`;
const KLINE_LIMITS = { daily: 600, weekly: 320, monthly: 180 };
const $ = selector => document.querySelector(selector);

const state = {
  code: normalizeSectorCode(new URLSearchParams(location.search).get('query')),
  tab: 'minute',
  payload: null,
  constituents: [],
  constituentPage: 0,
  constituentTotal: 0,
  hasMore: false,
  klineCache: new Map(),
  loading: false,
  loadingConstituents: false,
  loadingKline: false,
  error: '',
  lastSuccessAt: 0,
  requestCount: 0,
  autoRefresh: true,
  timer: null,
  kBaseCount: 72,
  kZoom: 1,
  kPan: 0,
  dragStartX: null,
  dragStartPan: 0
};

function normalizeSectorCode(value) {
  const normalized = String(value || '').trim().toUpperCase();
  return /^BK\d+$/.test(normalized) ? normalized : 'BK0428';
}

function text(value, fallback = '--') {
  const result = String(value ?? '').trim();
  return result && result !== 'null' && result !== 'NaN' ? result : fallback;
}

function number(value) {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null;
  const parsed = Number(String(value ?? '').replace(/[,，%亿万手元]/g, '').trim());
  return Number.isFinite(parsed) ? parsed : null;
}

function safeArray(value) { return Array.isArray(value) ? value : []; }
function escapeHtml(value) { return String(value ?? '').replace(/[&<>'"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[char])); }
function toneClass(value) { return String(value ?? '').trim().startsWith('-') ? 'fall-text' : 'rise-text'; }
function formatPercent(value) { const parsed = number(value); return parsed == null ? '--' : `${parsed.toFixed(2)}%`; }
function formatPrice(value) { const parsed = number(value); return parsed == null ? '--' : parsed.toFixed(2); }
function formatDate(value) { const raw = text(value, ''); return raw.length >= 10 ? raw.slice(5, 10) : raw; }

async function fetchJson(url, timeoutMs = 25000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, {
      signal: controller.signal,
      cache: 'no-store',
      headers: { 'Cache-Control': 'no-cache' }
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body?.detail || `HTTP ${response.status}`);
    return body;
  } finally {
    clearTimeout(timer);
  }
}

function updateClock() {
  $('#clock').textContent = new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit', minute: '2-digit', hour12: false
  }).format(new Date());
}

function metric(label, value, tone = 'neutral-text') {
  return `<article class="sector-metric"><span>${escapeHtml(label)}</span><strong class="${tone}">${escapeHtml(text(value, '--'))}</strong></article>`;
}

function contextCard(label, value, tone = 'neutral-text') {
  return `<article class="sector-context-card"><span>${escapeHtml(label)}</span><strong class="${tone}">${escapeHtml(text(value, '--'))}</strong></article>`;
}

function renderHeader() {
  $('#refreshButton').classList.toggle('sector-loading', state.loading);
  const payload = state.payload || {};
  const quote = payload.quote || {};
  $('#sectorName').textContent = text(quote.name, payload.name || '--');
  $('#sectorCode').textContent = text(payload.code, state.code);
  $('#sectorType').textContent = payload.type === 'concept' ? '概念板块' : payload.type === 'region' ? '地域板块' : '行业板块';
  $('#sectorSource').textContent = state.loading ? '正在同步真实板块行情' : text(payload.dataSourceLabel, state.error || '等待真实板块数据');
  $('#sectorPrice').textContent = text(quote.price, '--');
  $('#sectorPrice').className = `sector-price ${toneClass(quote.changePercent)}`;
  $('#sectorChange').textContent = `${text(quote.changeAmount, '--')}  ${text(quote.changePercent, '--')}`;
  $('#sectorChange').className = `sector-change ${toneClass(quote.changePercent)}`;
  const updated = state.lastSuccessAt ? new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
  }).format(new Date(state.lastSuccessAt)) : '等待更新';
  $('#sectorUpdated').textContent = state.error ? `刷新失败 · ${state.error}` : `更新 ${updated}`;
  $('#sectorMetrics').innerHTML = [
    metric('今开', quote.open),
    metric('最高', quote.high, 'rise-text'),
    metric('最低', quote.low, 'fall-text'),
    metric('昨收', formatPrice(quote.previousClose)),
    metric('成交额', quote.amount),
    metric('成交量', quote.volume)
  ].join('');
}

function renderBreadth() {
  const breadth = state.payload?.breadth || {};
  $('#sectorContextGrid').innerHTML = [
    contextCard('上涨', breadth.upCount, 'rise-text'),
    contextCard('下跌', breadth.downCount, 'fall-text'),
    contextCard('平盘', breadth.flatCount),
    contextCard('红盘率', formatPercent(breadth.redRate), 'aqua-text')
  ].join('');
  $('#leaderName').textContent = text(breadth.leaderName, '--');
  $('#leaderChange').textContent = text(breadth.leaderChangePercent, '--');
  $('#leaderChange').className = toneClass(breadth.leaderChangePercent);
  $('#mainInflow').textContent = text(breadth.mainInflow, '--');
  $('#mainInflow').className = String(breadth.mainInflow || '').includes('-') ? 'fall-text' : 'rise-text';
}

function renderRelated() {
  const items = safeArray(state.payload?.relatedSectors);
  const root = $('#relatedSectorGrid');
  if (!items.length) {
    root.innerHTML = '<div class="empty-line">其他板块暂不可用</div>';
    return;
  }
  root.innerHTML = items.map(item => `<button type="button" class="related-sector" data-sector="${escapeHtml(item.code)}"><strong>${escapeHtml(item.name)}</strong><small>${escapeHtml(item.code)}</small><b class="${toneClass(item.changePercent)}">${escapeHtml(text(item.changePercent, '--'))}</b></button>`).join('');
  root.querySelectorAll('[data-sector]').forEach(button => button.addEventListener('click', () => switchSector(button.dataset.sector, true)));
}

function renderConstituents() {
  const root = $('#constituentList');
  if (!state.constituents.length) {
    root.innerHTML = `<div class="empty-line">${state.loadingConstituents ? '正在加载真实成分股' : '成分股暂不可用'}</div>`;
  } else {
    root.innerHTML = state.constituents.map(item => `<button type="button" class="constituent-row" data-code="${escapeHtml(item.code)}"><span class="constituent-rank">${escapeHtml(item.rank)}</span><span class="constituent-copy"><strong>${escapeHtml(item.name)}</strong><span>${escapeHtml(item.code)} · 额 ${escapeHtml(text(item.amount, '--'))}</span></span><span class="constituent-price">${escapeHtml(text(item.price, '--'))}</span><span class="constituent-change ${toneClass(item.changePercent)}">${escapeHtml(text(item.changePercent, '--'))}</span></button>`).join('');
    root.querySelectorAll('[data-code]').forEach(row => row.addEventListener('click', () => {
      location.href = `./stock-detail-web-preview.html?query=${encodeURIComponent(row.dataset.code)}`;
    }));
  }
  $('#constituentSummary').textContent = state.constituentTotal
    ? `已加载 ${state.constituents.length}/${state.constituentTotal} 只真实成分股`
    : '按真实涨跌幅排序';
  const more = $('#loadMoreButton');
  more.disabled = state.loadingConstituents || !state.hasMore;
  more.textContent = state.loadingConstituents ? '加载中' : state.hasMore ? '加载更多' : '已全部加载';
}

function renderTabs() {
  document.querySelectorAll('.sector-tab').forEach(button => {
    button.classList.toggle('active', button.dataset.tab === state.tab);
  });
}

function renderStatus() {
  const kline = currentKlines();
  $('#dataStatus').innerHTML = state.loading
    ? '<strong>正在连接</strong>：读取板块报价、分时与市场宽度。'
    : state.error
      ? `<strong>刷新失败</strong>：${escapeHtml(state.error)}\n保留上一份真实成功数据。`
      : state.payload
        ? `<strong>板块接口已连接</strong>\n${escapeHtml(state.payload.name)} ${escapeHtml(state.payload.code)}\n分时 ${safeArray(state.payload.minutePoints).length}点 · 成分股 ${state.constituents.length}/${state.constituentTotal || '--'} · 当前K线 ${kline.length}根\n后端 ${escapeHtml(state.payload.totalLatencyMs ?? '--')}ms · 请求 ${state.requestCount} 次`
        : '<strong>等待数据</strong>：尚未收到板块详情。';
}

function renderAll() {
  renderHeader();
  renderBreadth();
  renderRelated();
  renderConstituents();
  renderChart();
  renderStatus();
}

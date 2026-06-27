'use strict';

const API_BASE = 'https://ai-ledger-stock-proxy.onrender.com';
const HOT_API = `${API_BASE}/api/stock/a-share/hot/ranking`;
const DEFAULT_SOURCE_URL = 'https://guba.eastmoney.com/rank/';
const $ = selector => document.querySelector(selector);
const TYPE_CONFIG = {
  popularity: {
    title: '个股人气榜',
    shortTitle: '人气榜',
    subtitle: '东方财富站内真实行为形成的市场关注度排行',
    description: '当前人气排名与实时行情批量合并展示',
    metricHeader: '当前排名'
  },
  surge: {
    title: '人气飙升榜',
    shortTitle: '飙升榜',
    subtitle: '较昨日人气排名提升幅度最大的股票',
    description: '按较昨日排名变化排序，并显示当前人气名次',
    metricHeader: '较昨日'
  }
};
const state = {
  type: normalizeType(new URLSearchParams(location.search).get('type')),
  payload: null,
  cache: new Map(),
  loading: false,
  error: '',
  requestCount: 0,
  autoRefresh: true,
  timer: null,
  sourceUrl: DEFAULT_SOURCE_URL
};

function normalizeType(value) { return TYPE_CONFIG[value] ? value : 'popularity'; }
function safeArray(value) { return Array.isArray(value) ? value : []; }
function text(value, fallback = '--') { const result = String(value ?? '').trim(); return result && result !== 'null' && result !== 'NaN' ? result : fallback; }
function number(value) { const parsed = Number(String(value ?? '').replace(/[,，%]/g, '').trim()); return Number.isFinite(parsed) ? parsed : null; }
function escapeHtml(value) { return String(value ?? '').replace(/[&<>'"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[char])); }
function toneClass(value) { return String(value ?? '').trim().startsWith('-') ? 'fall-text' : 'rise-text'; }
function currentConfig() { return TYPE_CONFIG[state.type]; }

async function fetchJson(url, timeoutMs = 30000) {
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

function formatTime(value) {
  const date = value ? new Date(value) : null;
  if (!date || Number.isNaN(date.getTime())) return '--';
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
  }).format(date);
}

function summaryCard(label, value, tone = 'neutral-text') {
  return `<article class="hot-summary-card"><span>${escapeHtml(label)}</span><strong class="${tone}">${escapeHtml(text(value, '--'))}</strong></article>`;
}

function renderHeader() {
  const config = currentConfig();
  const payload = state.payload || {};
  $('#hotTitle').textContent = config.title;
  $('#hotSubtitle').textContent = config.subtitle;
  $('#hotSource').textContent = state.loading
    ? `正在同步真实${config.shortTitle}`
    : state.error
      ? `刷新失败 · ${state.error}`
      : text(payload.dataSourceLabel, `东方财富个股${config.shortTitle} · 约10分钟更新`);
  $('#refreshButton').classList.toggle('loading', state.loading);
  state.sourceUrl = text(payload.sourcePageUrl, DEFAULT_SOURCE_URL);
  $('#sourceButton').disabled = !state.sourceUrl;
}

function renderSummary() {
  const payload = state.payload || {};
  const summary = payload.summary || {};
  const count = safeArray(payload.items).length;
  $('#hotSummary').innerHTML = [
    summaryCard('榜单股票', count ? `${count}只` : '--', 'aqua-text'),
    summaryCard('上涨', summary.risingCount, 'rise-text'),
    summaryCard('下跌', summary.fallingCount, 'fall-text')
  ].join('');
}

function rankChangeView(item) {
  const currentRank = number(item.currentRank) ?? number(item.rank);
  if (state.type === 'popularity') {
    return {
      primary: currentRank == null ? '--' : `#${currentRank}`,
      secondary: text(item.market, 'A股'),
      className: 'rank-flat'
    };
  }
  const change = number(item.rankChange);
  if (change == null) {
    return {
      primary: '--',
      secondary: currentRank == null ? '当前排名 --' : `当前 #${currentRank}`,
      className: 'rank-flat'
    };
  }
  return {
    primary: change > 0 ? `↑ ${change}` : change < 0 ? `↓ ${Math.abs(change)}` : '—',
    secondary: currentRank == null ? '当前排名 --' : `当前 #${currentRank}`,
    className: change > 0 ? 'rank-up' : change < 0 ? 'rank-down' : 'rank-flat'
  };
}

function renderList() {
  const payload = state.payload || {};
  const items = safeArray(payload.items);
  const config = currentConfig();
  $('#listTitle').textContent = `${config.shortTitle} TOP ${items.length || 100}`;
  $('#listDescription').textContent = config.description;
  $('#hotMetricHeader').textContent = config.metricHeader;
  $('#listUpdated').textContent = payload.updatedAt ? `更新 ${formatTime(payload.updatedAt)}` : '--';
  const root = $('#hotList');
  const empty = $('#hotEmpty');
  if (!items.length) {
    root.innerHTML = '';
    empty.style.display = 'grid';
    empty.textContent = state.loading
      ? `正在加载真实${config.shortTitle}`
      : state.error
        ? `热点榜暂不可用\n${state.error}`
        : '热点榜暂无可展示股票';
    return;
  }
  empty.style.display = 'none';
  root.innerHTML = items.map((item, index) => {
    const rank = number(item.rank) ?? index + 1;
    const metric = rankChangeView(item);
    const change = text(item.changePercent, '--');
    const subtitle = [text(item.code, ''), text(item.industry, ''), text(item.market, '')].filter(value => value && value !== '--').join(' · ');
    return `<button type="button" class="hot-row" data-code="${escapeHtml(item.code)}" aria-label="查看${escapeHtml(item.name)}详情"><span class="hot-rank${rank <= 3 ? ' top' : ''}">${rank}</span><span class="hot-stock"><strong>${escapeHtml(item.name)}</strong><span>${escapeHtml(subtitle || item.code)}</span></span><span class="hot-price"><strong>${escapeHtml(text(item.price, '--'))}</strong><span class="${toneClass(change)}">${escapeHtml(change)}</span></span><span class="hot-metric"><strong class="${metric.className}">${escapeHtml(metric.primary)}</strong><span>${escapeHtml(metric.secondary)}</span></span></button>`;
  }).join('');
  root.querySelectorAll('[data-code]').forEach(row => row.addEventListener('click', () => {
    location.href = `./stock-detail-web-preview.html?query=${encodeURIComponent(row.dataset.code)}`;
  }));
}

function renderTabs() {
  document.querySelectorAll('[data-type]').forEach(button => {
    button.classList.toggle('active', button.dataset.type === state.type);
  });
  document.querySelectorAll('[data-control-type]').forEach(button => {
    button.classList.toggle('primary', button.dataset.controlType === state.type);
  });
}

function renderStatus() {
  const payload = state.payload || {};
  const items = safeArray(payload.items);
  $('#dataStatus').innerHTML = state.loading
    ? `<strong>正在连接</strong>：读取东方财富${escapeHtml(currentConfig().shortTitle)}与批量行情。`
    : state.error
      ? `<strong>刷新失败</strong>：${escapeHtml(state.error)}\n保留上一份真实成功数据。`
      : items.length
        ? `<strong>真实热点接口已连接</strong>\n${escapeHtml(currentConfig().shortTitle)} ${items.length}只 · 行情匹配 ${escapeHtml(payload.summary?.quoteMatchCount ?? '--')}只\n后端 ${escapeHtml(payload.totalLatencyMs ?? '--')}ms · 缓存 ${payload.cacheHit ? '命中' : '更新'} · 请求 ${state.requestCount}次\n数据源规则：人气排名约10分钟更新一次`
        : '<strong>等待数据</strong>：尚未收到实时热点榜。';
}

function renderAll() {
  renderHeader();
  renderSummary();
  renderTabs();
  renderList();
  renderStatus();
}

async function loadHot(force = false) {
  if (state.loading) return;
  const cached = state.cache.get(state.type);
  if (!force && cached && Date.now() - cached.fetchedAt < 120000) {
    state.payload = cached.payload;
    state.error = '';
    renderAll();
    scheduleRefresh();
    return;
  }
  state.loading = true;
  state.error = '';
  renderAll();
  try {
    const payload = await fetchJson(`${HOT_API}?type=${encodeURIComponent(state.type)}&limit=100&_=${Date.now()}`);
    const items = safeArray(payload.items).filter(item => text(item?.code, '') && text(item?.name, ''));
    if (!items.length) throw new Error('热点接口未返回可展示股票');
    const normalized = { ...payload, items };
    state.payload = normalized;
    state.cache.set(state.type, { payload: normalized, fetchedAt: Date.now() });
    state.requestCount++;
  } catch (error) {
    state.error = error?.name === 'AbortError' ? '请求超时' : error?.message || String(error);
    if (cached) state.payload = cached.payload;
  } finally {
    state.loading = false;
    renderAll();
    scheduleRefresh();
  }
}

function switchType(type, pushHistory) {
  const normalized = normalizeType(type);
  if (normalized === state.type && state.payload) return;
  state.type = normalized;
  state.payload = state.cache.get(normalized)?.payload || null;
  state.error = '';
  if (pushHistory) history.pushState({ type: normalized }, '', `?type=${encodeURIComponent(normalized)}`);
  renderAll();
  loadHot(false);
}

function scheduleRefresh() {
  clearTimeout(state.timer);
  if (!state.autoRefresh) return;
  state.timer = setTimeout(() => loadHot(true), 120000);
}

$('#backButton').addEventListener('click', () => {
  if (history.length > 1) history.back();
  else location.href = './stock-home-web-preview.html';
});
$('#refreshButton').addEventListener('click', () => loadHot(true));
$('#manualRefresh').addEventListener('click', () => loadHot(true));
$('#sourceButton').addEventListener('click', () => {
  if (state.sourceUrl) window.open(state.sourceUrl, '_blank', 'noopener,noreferrer');
});
$('#hotTabs').addEventListener('click', event => {
  const button = event.target.closest('[data-type]');
  if (button) switchType(button.dataset.type, true);
});
document.querySelectorAll('[data-control-type]').forEach(button => button.addEventListener('click', () => switchType(button.dataset.controlType, true)));
$('#autoRefresh').addEventListener('change', event => { state.autoRefresh = event.target.checked; scheduleRefresh(); });
$('#phoneWidth').addEventListener('input', event => {
  document.documentElement.style.setProperty('--phone-w', `${event.target.value}px`);
  $('#phoneWidthText').textContent = `${event.target.value}px`;
});
$('#rowHeight').addEventListener('input', event => {
  document.documentElement.style.setProperty('--hot-row-height', `${event.target.value}px`);
  $('#rowHeightText').textContent = `${event.target.value}px`;
});
$('#mobileToggle').addEventListener('click', () => document.body.classList.toggle('controls-open'));
window.addEventListener('popstate', () => switchType(new URLSearchParams(location.search).get('type'), false));
document.addEventListener('visibilitychange', () => {
  if (!document.hidden && state.autoRefresh) loadHot(false);
});

updateClock();
setInterval(updateClock, 30000);
renderAll();
setTimeout(() => loadHot(false), 180);

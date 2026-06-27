'use strict';

const API_BASE = 'https://ai-ledger-stock-proxy.onrender.com';
const RANKING_API = `${API_BASE}/api/stock/a-share/rankings`;
const $ = selector => document.querySelector(selector);
const RANKINGS = {
  gainers: { title: '涨幅榜', subtitle: '按实时涨跌幅由高到低排序', metricLabel: '成交额', metricKey: 'amount', changeKey: 'changePercent' },
  losers: { title: '跌幅榜', subtitle: '按实时涨跌幅由低到高排序', metricLabel: '成交额', metricKey: 'amount', changeKey: 'changePercent' },
  amount: { title: '成交额榜', subtitle: '按实时成交额由高到低排序', metricLabel: '成交额', metricKey: 'amount', changeKey: 'changePercent' },
  turnover: { title: '换手率榜', subtitle: '按实时换手率由高到低排序', metricLabel: '换手率', metricKey: 'turnoverRate', changeKey: 'changePercent' },
  volume_ratio: { title: '量比榜', subtitle: '按实时量比由高到低排序', metricLabel: '量比', metricKey: 'volumeRatio', changeKey: 'changePercent' },
  speed: { title: '涨速榜', subtitle: '按实时涨速由高到低排序', metricLabel: '成交额', metricKey: 'amount', changeKey: 'changeSpeed' },
  main_inflow: { title: '主力净流入榜', subtitle: '按主力资金净流入由高到低排序', metricLabel: '主力净流入', metricKey: 'mainInflow', changeKey: 'changePercent' },
  main_outflow: { title: '主力净流出榜', subtitle: '按主力资金净流出由高到低排序', metricLabel: '主力净流出', metricKey: 'mainInflow', changeKey: 'changePercent' }
};
const state = {
  type: normalizeType(new URLSearchParams(location.search).get('type')),
  items: [],
  loading: false,
  error: '',
  lastSuccessAt: 0,
  requestCount: 0,
  autoRefresh: true,
  timer: null
};

function normalizeType(value) { return RANKINGS[value] ? value : 'gainers'; }
function escapeHtml(value) { return String(value ?? '').replace(/[&<>'"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[char])); }
function text(value, fallback = '--') { const result = String(value ?? '').trim(); return result && result !== 'null' && result !== 'NaN' ? result : fallback; }
function safeArray(value) { return Array.isArray(value) ? value : []; }
function toneClass(value) { return String(value ?? '').trim().startsWith('-') ? 'fall-text' : 'rise-text'; }

async function fetchJson(url, timeoutMs = 30000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, { signal: controller.signal, cache: 'no-store', headers: { 'Cache-Control': 'no-cache' } });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body?.detail || `HTTP ${response.status}`);
    return body;
  } finally {
    clearTimeout(timer);
  }
}

function updateClock() {
  $('#clock').textContent = new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date());
}

function currentConfig() { return RANKINGS[state.type]; }
function metricValue(item) { return text(item?.[currentConfig().metricKey], '--'); }
function changeValue(item) { return text(item?.[currentConfig().changeKey], '--'); }

function renderSwitcher() {
  $('#rankingSwitcher').innerHTML = Object.entries(RANKINGS).map(([type, config]) => `<button type="button" class="ranking-chip${state.type === type ? ' active' : ''}" data-ranking-type="${type}">${escapeHtml(config.title)}</button>`).join('');
  $('#rankingSwitcher').querySelectorAll('[data-ranking-type]').forEach(button => button.addEventListener('click', () => switchRanking(button.dataset.rankingType, true)));
  $('#rankingSelect').value = state.type;
}

function renderHeader() {
  const config = currentConfig();
  $('#rankingTitle').textContent = config.title;
  $('#rankingSubtitle').textContent = config.subtitle;
  $('#metricHeader').textContent = config.metricLabel;
  $('#rankingCount').textContent = state.items.length || '--';
  $('#refreshButton').classList.toggle('loading', state.loading);
  const time = state.lastSuccessAt ? new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(new Date(state.lastSuccessAt)) : '--';
  $('#rankingStatus').textContent = state.loading ? `正在刷新${config.title}` : state.error ? `刷新失败 · ${state.error}` : state.lastSuccessAt ? `真实榜单数据 · 更新 ${time} · 20秒刷新` : '正在连接真实榜单数据';
}

function renderList() {
  const root = $('#rankingList');
  const empty = $('#rankingEmpty');
  if (!state.items.length) {
    root.innerHTML = '';
    empty.style.display = 'grid';
    empty.textContent = state.loading ? `正在加载${currentConfig().title}` : state.error ? '榜单暂时加载失败，正在保留上一次成功数据' : '榜单暂无可展示股票';
    return;
  }
  empty.style.display = 'none';
  root.innerHTML = state.items.map((item, index) => {
    const rank = Number(item.rank) || index + 1;
    const change = changeValue(item);
    return `<button type="button" class="ranking-row-detail" data-code="${escapeHtml(item.code)}" aria-label="查看${escapeHtml(item.name)}详情"><span class="ranking-number${rank <= 3 ? ' top-three' : ''}">${rank}</span><span class="ranking-stock"><strong>${escapeHtml(item.name)}</strong><span>${escapeHtml(item.code)}${item.industry ? ` · ${escapeHtml(item.industry)}` : ''}</span></span><span class="ranking-metric">${escapeHtml(metricValue(item))}</span><span class="ranking-change ${toneClass(change)}">${escapeHtml(change)}</span></button>`;
  }).join('');
  root.querySelectorAll('[data-code]').forEach(row => row.addEventListener('click', () => {
    location.href = `./stock-detail-web-preview.html?query=${encodeURIComponent(row.dataset.code)}`;
  }));
}

function renderStatus() {
  $('#dataStatus').innerHTML = state.loading
    ? `<strong>正在连接</strong>：读取${escapeHtml(currentConfig().title)}真实排序。`
    : state.error
      ? `<strong>刷新失败</strong>：${escapeHtml(state.error)}\n保留上一份真实成功数据。`
      : state.lastSuccessAt
        ? `<strong>榜单接口已连接</strong>\n${escapeHtml(currentConfig().title)} · ${state.items.length} 只股票\n请求 ${state.requestCount} 次 · 页面未加载分时或K线`
        : '<strong>等待数据</strong>：尚未收到榜单接口结果。';
}

function renderAll() { renderHeader(); renderSwitcher(); renderList(); renderStatus(); }

async function loadRanking(silent = false) {
  if (state.loading) return;
  state.loading = true;
  state.error = '';
  renderHeader();
  if (!silent) renderStatus();
  try {
    const payload = await fetchJson(`${RANKING_API}?type=${encodeURIComponent(state.type)}&limit=100&_=${Date.now()}`);
    const items = safeArray(payload.items).filter(item => text(item?.code, '') && text(item?.name, ''));
    if (!items.length) throw new Error('榜单接口未返回可展示股票');
    state.items = items;
    state.lastSuccessAt = Date.now();
    state.requestCount++;
  } catch (error) {
    state.error = error?.name === 'AbortError' ? '请求超时' : error?.message || String(error);
  } finally {
    state.loading = false;
    renderAll();
    scheduleRefresh();
  }
}

function scheduleRefresh() {
  clearTimeout(state.timer);
  if (!state.autoRefresh) return;
  state.timer = setTimeout(() => loadRanking(true), 20000);
}

function switchRanking(type, pushHistory) {
  const normalized = normalizeType(type);
  if (normalized === state.type && state.items.length) return;
  state.type = normalized;
  state.items = [];
  state.error = '';
  if (pushHistory) history.pushState({ type: normalized }, '', `?type=${encodeURIComponent(normalized)}`);
  renderAll();
  loadRanking(false);
}

Object.entries(RANKINGS).forEach(([type, config]) => {
  const option = document.createElement('option');
  option.value = type;
  option.textContent = config.title;
  $('#rankingSelect').append(option);
});

$('#backButton').addEventListener('click', () => { if (history.length > 1) history.back(); else location.href = './stock-home-web-preview.html'; });
$('#refreshButton').addEventListener('click', () => loadRanking(false));
$('#manualRefresh').addEventListener('click', () => loadRanking(false));
$('#openRankingButton').addEventListener('click', () => switchRanking($('#rankingSelect').value, true));
$('#rankingSelect').addEventListener('change', event => switchRanking(event.target.value, true));
$('#autoRefresh').addEventListener('change', event => { state.autoRefresh = event.target.checked; scheduleRefresh(); });
$('#phoneWidth').addEventListener('input', event => { document.documentElement.style.setProperty('--phone-w', `${event.target.value}px`); $('#phoneWidthText').textContent = `${event.target.value}px`; });
$('#rowHeight').addEventListener('input', event => { document.documentElement.style.setProperty('--ranking-row-height', `${event.target.value}px`); $('#rowHeightText').textContent = `${event.target.value}px`; });
$('#mobileToggle').addEventListener('click', () => document.body.classList.toggle('controls-open'));
window.addEventListener('popstate', () => switchRanking(new URLSearchParams(location.search).get('type'), false));
document.addEventListener('visibilitychange', () => { if (!document.hidden && state.autoRefresh) loadRanking(true); });

updateClock();
setInterval(updateClock, 30000);
renderAll();
setTimeout(() => loadRanking(false), 180);

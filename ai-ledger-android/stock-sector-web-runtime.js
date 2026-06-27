'use strict';

async function loadDetail(silent = false) {
  if (state.loading) return;
  state.loading = true;
  state.error = '';
  renderHeader();
  if (!silent) renderStatus();
  try {
    const payload = await fetchJson(`${DETAIL_API}?query=${encodeURIComponent(state.code)}&_=${Date.now()}`);
    if (!payload?.quote) throw new Error('板块详情缺少报价');
    state.payload = payload;
    state.code = normalizeSectorCode(payload.code || state.code);
    state.lastSuccessAt = Date.now();
    state.requestCount++;
    $('#sectorQuery').value = state.code;
    if (!state.constituents.length) await loadConstituents(true, true);
  } catch (error) {
    state.error = error?.name === 'AbortError' ? '请求超时' : error?.message || String(error);
  } finally {
    state.loading = false;
    renderAll();
    scheduleRefresh();
  }
}

async function loadConstituents(reset = false, silent = false) {
  if (state.loadingConstituents) return;
  if (reset) {
    state.constituents = [];
    state.constituentPage = 0;
    state.constituentTotal = 0;
    state.hasMore = false;
  }
  state.loadingConstituents = true;
  if (!silent) renderConstituents();
  const page = state.constituentPage + 1;
  try {
    const payload = await fetchJson(`${CONSTITUENTS_API}?query=${encodeURIComponent(state.code)}&page=${page}&pageSize=20&_=${Date.now()}`);
    const items = safeArray(payload.items);
    state.constituents = reset ? items : [...state.constituents, ...items];
    state.constituentPage = page;
    state.constituentTotal = number(payload.total) || state.constituents.length;
    state.hasMore = Boolean(payload.hasMore);
  } catch (error) {
    state.error = error?.message || String(error);
  } finally {
    state.loadingConstituents = false;
    renderConstituents();
    renderStatus();
  }
}

async function loadKline(force = false) {
  if (state.tab === 'minute' || state.loadingKline) return;
  const key = `${state.code}:${state.tab}`;
  if (!force && state.klineCache.has(key)) { renderChart(); return; }
  state.loadingKline = true;
  renderChart();
  try {
    const limit = KLINE_LIMITS[state.tab];
    const payload = await fetchJson(`${KLINE_API}?query=${encodeURIComponent(state.code)}&instrument=sector&period=${state.tab}&limit=${limit}&_=${Date.now()}`, 30000);
    const rows = safeArray(payload.kLinePoints).filter(row => number(row.open) > 0 && number(row.close) > 0);
    if (rows.length < 2) throw new Error('板块K线数据不足');
    state.klineCache.set(key, rows);
    state.kZoom = 1;
    state.kPan = 0;
  } catch (error) {
    state.error = error?.name === 'AbortError' ? 'K线请求超时' : error?.message || String(error);
  } finally {
    state.loadingKline = false;
    renderAll();
  }
}

function scheduleRefresh() {
  clearTimeout(state.timer);
  if (!state.autoRefresh) return;
  state.timer = setTimeout(() => loadDetail(true), 20000);
}

function switchSector(code, pushHistory) {
  const normalized = normalizeSectorCode(code);
  state.code = normalized;
  state.payload = null;
  state.constituents = [];
  state.constituentPage = 0;
  state.constituentTotal = 0;
  state.hasMore = false;
  state.klineCache.clear();
  state.kZoom = 1;
  state.kPan = 0;
  state.error = '';
  if (pushHistory) history.pushState({ code: normalized }, '', `?query=${encodeURIComponent(normalized)}`);
  renderAll();
  loadDetail(false);
}

function installChartInteractions() {
  const wrap = $('#chartWrap');
  wrap.addEventListener('wheel', event => {
    if (state.tab === 'minute') return;
    event.preventDefault();
    state.kZoom = Math.max(1, Math.min(6, state.kZoom * (event.deltaY < 0 ? 1.16 : .86)));
    renderChart();
  }, { passive: false });
  wrap.addEventListener('pointerdown', event => {
    if (state.tab === 'minute') return;
    wrap.setPointerCapture(event.pointerId);
    state.dragStartX = event.clientX;
    state.dragStartPan = state.kPan;
  });
  wrap.addEventListener('pointermove', event => {
    if (state.tab === 'minute' || state.dragStartX == null) return;
    const visible = klineWindow(currentKlines()).visible;
    const step = Math.max(1, wrap.clientWidth / Math.max(visible.length, 1));
    state.kPan = state.dragStartPan + (event.clientX - state.dragStartX) / step;
    renderChart();
  });
  wrap.addEventListener('pointerup', () => { state.dragStartX = null; });
  wrap.addEventListener('pointercancel', () => { state.dragStartX = null; });
  wrap.addEventListener('dblclick', event => {
    if (state.tab === 'minute') return;
    event.preventDefault();
    state.kZoom = 1;
    state.kPan = 0;
    renderChart();
  });
}

$('#backButton').addEventListener('click', () => { location.href = './stock-home-web-preview.html'; });
$('#refreshButton').addEventListener('click', () => loadDetail(false));
$('#manualRefresh').addEventListener('click', () => loadDetail(false));
$('#openSectorButton').addEventListener('click', () => switchSector($('#sectorQuery').value, true));
$('#sectorQuery').addEventListener('keydown', event => { if (event.key === 'Enter') switchSector(event.target.value, true); });
$('#sectorTabs').addEventListener('click', event => {
  const button = event.target.closest('[data-tab]');
  if (!button) return;
  state.tab = button.dataset.tab;
  state.kZoom = 1;
  state.kPan = 0;
  renderChart();
  if (state.tab !== 'minute') loadKline(false);
});
$('#loadMoreButton').addEventListener('click', () => loadConstituents(false, false));
$('#autoRefresh').addEventListener('change', event => { state.autoRefresh = event.target.checked; scheduleRefresh(); });
$('#kBaseCount').addEventListener('input', event => {
  state.kBaseCount = Number(event.target.value);
  $('#kBaseCountText').textContent = `${event.target.value}根`;
  state.kPan = 0;
  renderChart();
});
$('#phoneWidth').addEventListener('input', event => {
  document.documentElement.style.setProperty('--phone-w', `${event.target.value}px`);
  $('#phoneWidthText').textContent = `${event.target.value}px`;
  requestAnimationFrame(renderChart);
});
$('#mobileToggle').addEventListener('click', () => document.body.classList.toggle('controls-open'));
window.addEventListener('resize', () => requestAnimationFrame(renderChart));
window.addEventListener('popstate', () => switchSector(new URLSearchParams(location.search).get('query'), false));
document.addEventListener('visibilitychange', () => { if (!document.hidden && state.autoRefresh) loadDetail(true); });

updateClock();
setInterval(updateClock, 30000);
installChartInteractions();
renderAll();
setTimeout(() => loadDetail(false), 180);

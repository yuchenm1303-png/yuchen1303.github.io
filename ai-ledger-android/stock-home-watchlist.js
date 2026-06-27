'use strict';

const HOME_WATCHLIST_STORAGE_KEY = 'ai-ledger-stock-watchlist-v1';
const originalRenderToolContent = renderToolContent;

function readHomeWatchlist() {
  try {
    const parsed = JSON.parse(localStorage.getItem(HOME_WATCHLIST_STORAGE_KEY) || '[]');
    return Array.isArray(parsed) ? parsed.filter(item => /^\d{6}$/.test(String(item?.code || ''))) : [];
  } catch (_) {
    return [];
  }
}

function writeHomeWatchlist(items) {
  try {
    localStorage.setItem(HOME_WATCHLIST_STORAGE_KEY, JSON.stringify(items.slice(0, 300)));
    return true;
  } catch (_) {
    return false;
  }
}

function renderHomeWatchlist() {
  const root = document.querySelector('#toolContent');
  if (!root) return;
  const items = readHomeWatchlist();
  root.classList.remove('scrollable');
  root.innerHTML = `<div class="watchlist-panel-head"><div class="section-title-block"><h2>我的自选</h2><p>从个股详情页加入，保存在当前浏览器</p></div><span>${items.length} 只</span></div>${items.length ? `<div class="watchlist-list">${items.map(item => `<div class="watchlist-row"><button type="button" class="watchlist-main" data-watch-code="${escapeHtml(item.code)}"><span class="watchlist-star">★</span><span class="watchlist-copy"><strong>${escapeHtml(item.name || item.code)}</strong><span>${escapeHtml(item.code)} · ${escapeHtml(item.market || 'A股')}</span></span><span class="watchlist-open">›</span></button><button type="button" class="watchlist-remove" data-remove-watch="${escapeHtml(item.code)}" aria-label="移除${escapeHtml(item.name || item.code)}">×</button></div>`).join('')}</div>` : '<div class="watchlist-empty">还没有自选股<br>进入任意个股详情，点击“加自选”即可加入</div>'}`;
  root.querySelectorAll('[data-watch-code]').forEach(button => button.addEventListener('click', () => openDetail(button.dataset.watchCode)));
  root.querySelectorAll('[data-remove-watch]').forEach(button => button.addEventListener('click', event => {
    event.stopPropagation();
    const code = button.dataset.removeWatch;
    writeHomeWatchlist(readHomeWatchlist().filter(item => item.code !== code));
    window.dispatchEvent(new CustomEvent('ai-ledger-watchlist-change', { detail: { code } }));
    renderHomeWatchlist();
  }));
}

renderToolContent = function renderToolContentWithWatchlist() {
  if (state.selectedAction === '自选') {
    renderHomeWatchlist();
    return;
  }
  originalRenderToolContent();
};

window.addEventListener('storage', event => {
  if (event.key === HOME_WATCHLIST_STORAGE_KEY && state.selectedAction === '自选') renderHomeWatchlist();
});
window.addEventListener('ai-ledger-watchlist-change', () => {
  if (state.selectedAction === '自选') renderHomeWatchlist();
});

if (state.selectedAction === '自选') renderHomeWatchlist();

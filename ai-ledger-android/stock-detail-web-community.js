'use strict';

const DISCUSSION_LIST_API = `${API_BASE}/api/stock/a-share/discussions`;
const WATCHLIST_STORAGE_KEY = 'ai-ledger-stock-watchlist-v1';
const communityState = {
  mode: 'market',
  code: '',
  posts: [],
  page: 0,
  hasMore: false,
  loading: false,
  error: '',
  sourceUrl: '',
  requestCount: 0,
  requestSerial: 0,
  sort: 'latest',
  pageCache: new Map()
};
let discussionToastTimer = null;

function communityEscape(value) {
  return String(value ?? '').replace(/[&<>'"]/g, char => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
  }[char]));
}

function communityText(value, fallback = '--') {
  const result = String(value ?? '').trim();
  return result && result !== 'null' && result !== 'NaN' ? result : fallback;
}

function communityNumber(value) {
  const parsed = Number(String(value ?? '').replace(/[,，]/g, '').trim());
  return Number.isFinite(parsed) ? parsed : null;
}

function discussionCode() {
  const live = String(state.quote?.code || '').trim();
  if (/^\d{6}$/.test(live)) return live;
  const input = String(currentQuery() || '').trim();
  const digits = input.replace(/\D/g, '');
  return /^\d{6}$/.test(digits) ? digits : '';
}

function discussionName() {
  const name = String(state.quote?.name || '').trim();
  return name && name !== '--' ? name : discussionCode() || '当前股票';
}

async function communityFetchJson(url, timeoutMs = 30000) {
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

function readWatchlist() {
  try {
    const parsed = JSON.parse(localStorage.getItem(WATCHLIST_STORAGE_KEY) || '[]');
    return Array.isArray(parsed)
      ? parsed.filter(item => /^\d{6}$/.test(String(item?.code || '')))
      : [];
  } catch (_) {
    return [];
  }
}

function writeWatchlist(items) {
  try {
    localStorage.setItem(WATCHLIST_STORAGE_KEY, JSON.stringify(items.slice(0, 300)));
    return true;
  } catch (_) {
    return false;
  }
}

function isCurrentStockWatched() {
  const code = discussionCode();
  return Boolean(code && readWatchlist().some(item => item.code === code));
}

function renderWatchlistButton() {
  const button = document.getElementById('watchlistButton');
  if (!button) return;
  const code = discussionCode();
  const watched = isCurrentStockWatched();
  button.disabled = !code;
  button.classList.toggle('active', watched);
  button.setAttribute('aria-pressed', watched ? 'true' : 'false');
  const icon = button.querySelector('span');
  const label = button.querySelector('b');
  if (icon) icon.textContent = watched ? '★' : '☆';
  if (label) label.textContent = code ? (watched ? '已自选' : '加自选') : '等待行情';
}

function toggleCurrentWatchlist() {
  const code = discussionCode();
  if (!code) return;
  const items = readWatchlist();
  const exists = items.some(item => item.code === code);
  const next = exists
    ? items.filter(item => item.code !== code)
    : [{
        code,
        name: discussionName(),
        market: communityText(state.quote?.market, 'A股'),
        addedAt: new Date().toISOString()
      }, ...items];
  if (!writeWatchlist(next)) showDiscussionToast('浏览器未允许保存自选状态');
  renderWatchlistButton();
  window.dispatchEvent(new CustomEvent('ai-ledger-watchlist-change', { detail: { code } }));
}

function discussionKindLabel(kind) {
  return ({
    announcement: '公告',
    research: '研报',
    news: '资讯',
    qa: '问董秘',
    discussion: '讨论'
  })[kind] || '讨论';
}

function formatDiscussionCount(value) {
  const amount = communityNumber(value);
  if (amount == null) return '--';
  if (amount >= 10000) return `${(amount / 10000).toFixed(amount >= 100000 ? 0 : 1)}万`;
  return String(amount);
}

function feedAuthorHue(author) {
  let hash = 0;
  for (const char of String(author || '股吧用户')) {
    hash = (hash * 31 + char.charCodeAt(0)) % 360;
  }
  return hash;
}

function feedAuthorInitial(author) {
  const value = String(author || '股吧用户').trim();
  return value ? [...value][0] : '股';
}

function feedPostText(post) {
  const original = String(post?.title || '').trim();
  if (!original) return '点击查看该条讨论';
  return original
    .replace(/^\$[^$]{1,48}\$\s*/, '')
    .replace(/^#[^#]{1,48}#\s*/, '')
    .trim() || original;
}

function sortedDiscussionPosts() {
  const posts = [...communityState.posts];
  if (communityState.sort !== 'hot') return posts;
  return posts.sort((left, right) => {
    const leftScore = (communityNumber(left.commentCount) || 0) * 500
      + (communityNumber(left.readCount) || 0);
    const rightScore = (communityNumber(right.commentCount) || 0) * 500
      + (communityNumber(right.readCount) || 0);
    return rightScore - leftScore;
  });
}

function resetDiscussionForCode(code) {
  communityState.requestSerial += 1;
  communityState.code = code;
  communityState.posts = [];
  communityState.page = 0;
  communityState.hasMore = false;
  communityState.loading = false;
  communityState.error = '';
  communityState.sourceUrl = '';
}

function openStandaloneDiscussion(postId) {
  const code = discussionCode();
  if (!code || !postId) return;
  location.href = `./stock-discussion-web-preview.html?query=${encodeURIComponent(code)}&postId=${encodeURIComponent(postId)}`;
}

function renderDiscussionHeader() {
  const title = document.getElementById('discussionTitle');
  const subtitle = document.getElementById('discussionSubtitle');
  if (title) title.textContent = '社区';
  if (subtitle) {
    subtitle.textContent = `${discussionName()}（${discussionCode() || '------'}）· 东方财富股吧只读社区`;
  }
}

function renderDiscussionSort() {
  document.querySelectorAll('[data-discussion-sort]').forEach(button => {
    button.classList.toggle('active', button.dataset.discussionSort === communityState.sort);
  });
}

function renderDiscussionList() {
  const view = document.getElementById('discussionListView');
  const list = document.getElementById('discussionList');
  const more = document.getElementById('discussionMore');
  const count = document.getElementById('discussionCount');
  if (!view || !list || !more || !count) return;

  view.hidden = false;
  renderDiscussionSort();
  count.textContent = communityState.posts.length
    ? `${communityState.posts.length} 条`
    : communityState.loading ? '加载中' : '等待数据';

  if (communityState.loading && !communityState.posts.length) {
    list.innerHTML = '<div class="discussion-loading">正在读取真实股吧社区…</div>';
  } else if (communityState.error && !communityState.posts.length) {
    list.innerHTML = `<div class="discussion-error"><div>${communityEscape(communityState.error)}<br><button type="button" data-discussion-retry>重新加载</button></div></div>`;
  } else if (!communityState.posts.length) {
    list.innerHTML = '<div class="discussion-empty">当前股票暂未返回可展示的社区帖子</div>';
  } else {
    const stockTag = `$${discussionName()}(${discussionCode()})$`;
    list.innerHTML = sortedDiscussionPosts().map(post => {
      const commentCount = communityNumber(post.commentCount) || 0;
      const readCount = communityNumber(post.readCount) || 0;
      const author = post.author || '股吧用户';
      const kindChip = post.kind && post.kind !== 'discussion'
        ? `<span class="feed-kind-chip">${communityEscape(discussionKindLabel(post.kind))}</span>`
        : '';
      const preview = commentCount > 0
        ? `<strong>网友讨论：</strong>已有 ${communityEscape(formatDiscussionCount(commentCount))} 条评论，进入详情后按需加载`
        : '点击进入独立帖子详情页查看正文';
      return `<article class="discussion-feed-card" data-post-id="${communityEscape(post.postId)}" tabindex="0" role="button" aria-label="查看${communityEscape(post.title)}"><div class="feed-author-row"><span class="feed-avatar" style="--avatar-hue:${feedAuthorHue(author)}">${communityEscape(feedAuthorInitial(author))}</span><span class="feed-author-copy"><strong>${communityEscape(author)}</strong><span>${communityEscape(post.updatedAt || '时间未知')}</span></span><span class="feed-more">•••</span></div><div class="feed-content">${kindChip}<span class="feed-stock-tag">${communityEscape(stockTag)}</span>${communityEscape(feedPostText(post))}</div><div class="feed-actions"><span class="feed-action"><span class="feed-action-icon">↗</span>分享</span><span class="feed-action"><span class="feed-action-icon">◯</span>${communityEscape(formatDiscussionCount(commentCount))}</span><span class="feed-action"><span class="feed-action-icon">♡</span>阅读 ${communityEscape(formatDiscussionCount(readCount))}</span></div><div class="feed-comment-preview">${preview}</div></article>`;
    }).join('');
  }

  more.disabled = communityState.loading || !communityState.hasMore;
  more.textContent = communityState.loading && communityState.posts.length
    ? '加载中…'
    : communityState.hasMore ? '加载更多社区帖子' : '已加载当前社区内容';

  list.querySelectorAll('[data-post-id]').forEach(card => {
    const open = () => openStandaloneDiscussion(card.dataset.postId);
    card.addEventListener('click', open);
    card.addEventListener('keydown', event => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        open();
      }
    });
  });
  list.querySelector('[data-discussion-retry]')?.addEventListener('click', () => loadDiscussionPage(true));
}

function renderDiscussionStatus() {
  const status = document.getElementById('discussionStatus');
  if (!status) return;
  if (communityState.loading) {
    status.textContent = communityState.posts.length
      ? `正在加载更多社区帖子 · 已有 ${communityState.posts.length} 条`
      : '正在连接东方财富股吧，只读抓取不会参与行情刷新。';
  } else if (communityState.error) {
    status.textContent = communityState.error;
  } else if (communityState.posts.length) {
    status.textContent = `讨论 ${communityState.posts.length} 条 · 社区请求 ${communityState.requestCount} 次 · 45秒列表缓存`;
  } else {
    status.textContent = '社区首次打开时才加载，不增加个股行情首屏开销。';
  }
}

function renderDiscussion() {
  renderDiscussionHeader();
  renderDiscussionList();
  renderDiscussionStatus();
}

async function loadDiscussionPage(force = false) {
  const code = discussionCode();
  if (!code || communityState.loading) return;
  if (communityState.code !== code) resetDiscussionForCode(code);

  const targetPage = force && !communityState.posts.length
    ? 1
    : communityState.page + 1;
  const cacheKey = `${code}:${targetPage}`;
  const cached = communityState.pageCache.get(cacheKey);
  if (!force && cached) {
    communityState.posts = targetPage === 1
      ? cached.posts
      : mergeDiscussionPosts(communityState.posts, cached.posts);
    communityState.page = targetPage;
    communityState.hasMore = Boolean(cached.hasMore);
    communityState.sourceUrl = cached.sourcePageUrl || communityState.sourceUrl;
    renderDiscussion();
    return;
  }

  const requestSerial = ++communityState.requestSerial;
  communityState.loading = true;
  communityState.error = '';
  renderDiscussion();

  try {
    const payload = await communityFetchJson(
      `${DISCUSSION_LIST_API}?query=${encodeURIComponent(code)}&page=${targetPage}&pageSize=20&_=${Date.now()}`
    );
    if (requestSerial !== communityState.requestSerial || code !== discussionCode()) return;
    const posts = Array.isArray(payload.posts)
      ? payload.posts.filter(post => post?.postId && post?.title)
      : [];
    if (!posts.length) throw new Error('讨论接口未返回可展示帖子');
    communityState.pageCache.set(cacheKey, { ...payload, posts });
    communityState.posts = targetPage === 1
      ? posts
      : mergeDiscussionPosts(communityState.posts, posts);
    communityState.page = targetPage;
    communityState.hasMore = Boolean(payload.hasMore);
    communityState.sourceUrl = payload.sourcePageUrl || communityState.sourceUrl;
    communityState.requestCount += 1;
  } catch (error) {
    if (requestSerial !== communityState.requestSerial) return;
    communityState.error = error?.name === 'AbortError'
      ? '社区请求超时'
      : error?.message || String(error);
  } finally {
    if (requestSerial === communityState.requestSerial) {
      communityState.loading = false;
      renderDiscussion();
    }
  }
}

function mergeDiscussionPosts(existing, incoming) {
  const seen = new Set(existing.map(post => String(post.postId)));
  return [
    ...existing,
    ...incoming.filter(post => !seen.has(String(post.postId)))
  ];
}

function setDiscussionSort(sort) {
  communityState.sort = sort === 'hot' ? 'hot' : 'latest';
  renderDiscussion();
  document.getElementById('discussionList')?.scrollTo({ top: 0, behavior: 'smooth' });
}

function showDiscussionToast(message) {
  const toast = document.getElementById('discussionToast');
  if (!toast) return;
  toast.textContent = message;
  toast.classList.add('show');
  clearTimeout(discussionToastTimer);
  discussionToastTimer = setTimeout(() => toast.classList.remove('show'), 2200);
}

function setDetailMode(mode) {
  communityState.mode = mode === 'discussion' ? 'discussion' : 'market';
  const chart = document.getElementById('chartCard');
  const discussion = document.getElementById('discussionCard');
  document.body.classList.toggle('discussion-mode', communityState.mode === 'discussion');
  if (chart) chart.hidden = communityState.mode === 'discussion';
  if (discussion) discussion.hidden = communityState.mode !== 'discussion';
  document.querySelectorAll('[data-detail-mode]').forEach(button => {
    button.classList.toggle('active', button.dataset.detailMode === communityState.mode);
  });

  if (communityState.mode === 'discussion') {
    const code = discussionCode();
    if (code && communityState.code !== code) resetDiscussionForCode(code);
    renderDiscussion();
    if (!communityState.posts.length && !communityState.loading) loadDiscussionPage(false);
  } else {
    requestAnimationFrame(drawSelectedChart);
  }
}

function syncCommunityStock() {
  const code = discussionCode();
  renderWatchlistButton();
  if (code && communityState.code && communityState.code !== code) {
    resetDiscussionForCode(code);
    if (communityState.mode === 'discussion') {
      renderDiscussion();
      loadDiscussionPage(false);
    }
  }
  renderDiscussionHeader();
}

document.getElementById('watchlistButton')?.addEventListener('click', toggleCurrentWatchlist);
document.querySelectorAll('[data-detail-mode]').forEach(button => {
  button.addEventListener('click', () => setDetailMode(button.dataset.detailMode));
});
document.querySelectorAll('[data-discussion-sort]').forEach(button => {
  button.addEventListener('click', () => setDiscussionSort(button.dataset.discussionSort));
});
document.getElementById('discussionMore')?.addEventListener('click', () => loadDiscussionPage(false));
document.getElementById('discussionSourceButton')?.addEventListener('click', () => {
  const code = discussionCode();
  const url = communityState.sourceUrl || (code ? `https://guba.eastmoney.com/list,${code}.html` : '');
  if (url) window.open(url, '_blank', 'noopener,noreferrer');
});
document.getElementById('discussionCompose')?.addEventListener('click', () => {
  showDiscussionToast('当前为只读社区，暂不支持登录、发帖或回复');
});
window.addEventListener('storage', event => {
  if (event.key === WATCHLIST_STORAGE_KEY) renderWatchlistButton();
});
window.addEventListener('ai-ledger-watchlist-change', renderWatchlistButton);

const communityCodeObserver = new MutationObserver(syncCommunityStock);
const observedCode = document.getElementById('code');
const observedName = document.getElementById('name');
if (observedCode) {
  communityCodeObserver.observe(observedCode, {
    childList: true,
    characterData: true,
    subtree: true
  });
}
if (observedName) {
  communityCodeObserver.observe(observedName, {
    childList: true,
    characterData: true,
    subtree: true
  });
}

renderWatchlistButton();
renderDiscussion();
setDetailMode('market');

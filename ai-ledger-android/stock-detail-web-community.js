'use strict';

const DISCUSSION_LIST_API = `${API_BASE}/api/stock/a-share/discussions`;
const DISCUSSION_DETAIL_API = `${API_BASE}/api/stock/a-share/discussion/detail`;
const WATCHLIST_STORAGE_KEY = 'ai-ledger-stock-watchlist-v1';
const communityState = {
  mode: 'market',
  code: '',
  posts: [],
  page: 0,
  hasMore: false,
  loadingList: false,
  listError: '',
  listSourceUrl: '',
  activePostId: '',
  activePost: null,
  comments: [],
  commentPage: 0,
  hasMoreComments: false,
  loadingPost: false,
  postError: '',
  requestCount: 0,
  listCache: new Map(),
  postCache: new Map()
};

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
    return Array.isArray(parsed) ? parsed.filter(item => /^\d{6}$/.test(String(item?.code || ''))) : [];
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
  const existing = items.findIndex(item => item.code === code);
  let next;
  if (existing >= 0) {
    next = items.filter(item => item.code !== code);
  } else {
    next = [{
      code,
      name: discussionName(),
      market: communityText(state.quote?.market, 'A股'),
      addedAt: new Date().toISOString()
    }, ...items];
  }
  if (!writeWatchlist(next)) {
    const status = document.getElementById('discussionStatus');
    if (status) status.textContent = '浏览器未允许保存自选状态';
  }
  renderWatchlistButton();
  window.dispatchEvent(new CustomEvent('ai-ledger-watchlist-change', { detail: { code } }));
}

function discussionKindLabel(kind) {
  return ({
    announcement: '公告', research: '研报', news: '资讯', qa: '问董秘', discussion: '讨论'
  })[kind] || '讨论';
}

function formatDiscussionCount(value) {
  const amount = communityNumber(value);
  if (amount == null) return '--';
  if (amount >= 10000) return `${(amount / 10000).toFixed(amount >= 100000 ? 0 : 1)}万`;
  return String(amount);
}

function resetDiscussionForCode(code) {
  communityState.code = code;
  communityState.posts = [];
  communityState.page = 0;
  communityState.hasMore = false;
  communityState.loadingList = false;
  communityState.listError = '';
  communityState.listSourceUrl = '';
  communityState.activePostId = '';
  communityState.activePost = null;
  communityState.comments = [];
  communityState.commentPage = 0;
  communityState.hasMoreComments = false;
  communityState.loadingPost = false;
  communityState.postError = '';
}

function renderDiscussionHeader() {
  const title = document.getElementById('discussionTitle');
  const subtitle = document.getElementById('discussionSubtitle');
  if (title) title.textContent = `${discussionName()}讨论`;
  if (subtitle) subtitle.textContent = communityState.activePost
    ? '帖子正文与公开评论只读展示'
    : '东方财富股吧最新讨论 · 点击帖子查看评论';
}

function renderDiscussionList() {
  const view = document.getElementById('discussionListView');
  const postView = document.getElementById('discussionPostView');
  const list = document.getElementById('discussionList');
  const more = document.getElementById('discussionMore');
  const count = document.getElementById('discussionCount');
  if (!view || !postView || !list || !more || !count) return;
  view.hidden = false;
  postView.hidden = true;
  count.textContent = communityState.posts.length ? `已加载 ${communityState.posts.length} 条` : '等待讨论数据';

  if (communityState.loadingList && !communityState.posts.length) {
    list.innerHTML = '<div class="discussion-loading">正在读取真实股吧帖子…</div>';
  } else if (communityState.listError && !communityState.posts.length) {
    list.innerHTML = `<div class="discussion-error"><div>${communityEscape(communityState.listError)}<br><button type="button" data-discussion-retry>重新加载</button></div></div>`;
  } else if (!communityState.posts.length) {
    list.innerHTML = '<div class="discussion-empty">当前股票暂未返回可展示的股吧帖子</div>';
  } else {
    list.innerHTML = communityState.posts.map((post, index) => `<button type="button" class="discussion-row" data-post-id="${communityEscape(post.postId)}"><span class="discussion-rank">${index + 1}</span><span class="discussion-row-copy"><strong>${communityEscape(post.title)}</strong><span>${communityEscape(discussionKindLabel(post.kind))} · ${communityEscape(post.author)} · ${communityEscape(post.updatedAt || '时间未知')}</span></span><span class="discussion-counts"><b>评 ${communityEscape(formatDiscussionCount(post.commentCount))}</b><span>阅 ${communityEscape(formatDiscussionCount(post.readCount))}</span></span></button>`).join('');
  }

  more.disabled = communityState.loadingList || !communityState.hasMore;
  more.textContent = communityState.loadingList && communityState.posts.length
    ? '加载中…'
    : communityState.hasMore ? '加载更多讨论' : '已加载当前页讨论';

  list.querySelectorAll('[data-post-id]').forEach(button => button.addEventListener('click', () => openDiscussionPost(button.dataset.postId)));
  list.querySelectorAll('[data-discussion-retry]').forEach(button => button.addEventListener('click', () => loadDiscussionPage(true)));
}

function renderNestedReplies(replies) {
  if (!Array.isArray(replies) || !replies.length) return '';
  return `<div class="nested-replies">${replies.slice(0, 6).map(reply => `<div class="nested-reply"><strong>${communityEscape(reply.author || '股吧用户')}</strong><p>${communityEscape(reply.content || '')}</p></div>`).join('')}</div>`;
}

function renderDiscussionPost() {
  const listView = document.getElementById('discussionListView');
  const view = document.getElementById('discussionPostView');
  const body = document.getElementById('discussionPostScroll');
  const postStatus = document.getElementById('discussionPostStatus');
  if (!listView || !view || !body || !postStatus) return;
  listView.hidden = true;
  view.hidden = false;
  const detail = communityState.activePost;
  const post = detail?.post || null;
  postStatus.textContent = communityState.loadingPost
    ? '正在读取帖子正文与评论'
    : communityState.postError
      ? communityState.postError
      : post ? `${communityState.comments.length} 条公开评论已加载` : '等待帖子正文';

  if (communityState.loadingPost && !post) {
    body.innerHTML = '<div class="discussion-loading">正在加载真实帖子与评论…</div>';
    return;
  }
  if (communityState.postError && !post) {
    body.innerHTML = `<div class="discussion-error"><div>${communityEscape(communityState.postError)}<br><button type="button" data-post-retry>重新加载</button></div></div>`;
    body.querySelector('[data-post-retry]')?.addEventListener('click', () => loadDiscussionPost(communityState.activePostId, true));
    return;
  }
  if (!post) {
    body.innerHTML = '<div class="discussion-empty">帖子正文暂不可用</div>';
    return;
  }

  const comments = communityState.comments;
  body.innerHTML = `<article class="discussion-article"><h3>${communityEscape(post.title)}</h3><div class="discussion-article-meta">${communityEscape(post.author || '股吧用户')} · ${communityEscape(post.publishedAt || '时间未知')} · 赞 ${communityEscape(formatDiscussionCount(post.likeCount))}</div><div class="discussion-article-body">${communityEscape(post.content || '该帖子未返回纯文本正文。')}</div><div class="discussion-risk">股吧内容来自公开社区，仅代表发布者个人观点，不构成投资建议。页面仅做只读展示。</div></article><div class="comment-section-title"><strong>网友评论</strong><span>${communityEscape(String(detail.commentTotalParsed ?? comments.length))} 条已解析</span></div>${comments.length ? comments.map(comment => `<article class="comment-row"><div class="comment-head"><span class="comment-author">${communityEscape(comment.author || '股吧用户')}</span><span class="comment-time">${communityEscape(comment.publishedAt || '')}</span></div><div class="comment-content">${communityEscape(comment.content || '')}</div><div class="comment-meta"><span>赞 ${communityEscape(formatDiscussionCount(comment.likeCount))}</span><span>回复 ${communityEscape(formatDiscussionCount(comment.replyCount))}</span></div>${renderNestedReplies(comment.replies)}</article>`).join('') : '<div class="discussion-empty">正文已加载，但当前页面没有返回公开评论。</div>'}${communityState.hasMoreComments ? '<button type="button" class="comment-more" id="commentMore">加载更多评论</button>' : ''}<button type="button" class="comment-more" data-open-post-source>查看东方财富原帖 ↗</button>`;
  body.querySelector('#commentMore')?.addEventListener('click', () => loadMoreDiscussionComments());
  body.querySelector('[data-open-post-source]')?.addEventListener('click', () => {
    const url = post.sourceUrl || detail.sourcePageUrl;
    if (url) window.open(url, '_blank', 'noopener,noreferrer');
  });
}

function renderDiscussionStatus() {
  const status = document.getElementById('discussionStatus');
  if (!status) return;
  if (communityState.loadingList || communityState.loadingPost) {
    status.textContent = '正在连接东方财富股吧，只读抓取不会参与行情刷新。';
  } else if (communityState.listError || communityState.postError) {
    status.textContent = communityState.postError || communityState.listError;
  } else if (communityState.posts.length) {
    status.textContent = `讨论 ${communityState.posts.length} 条 · 社区请求 ${communityState.requestCount} 次 · 45秒列表缓存 / 120秒帖子缓存`;
  } else {
    status.textContent = '讨论页首次打开时才加载，不增加个股行情首屏开销。';
  }
}

function renderDiscussion() {
  renderDiscussionHeader();
  if (communityState.activePostId) renderDiscussionPost();
  else renderDiscussionList();
  renderDiscussionStatus();
}

async function loadDiscussionPage(force = false) {
  const code = discussionCode();
  if (!code || communityState.loadingList) return;
  if (communityState.code !== code) resetDiscussionForCode(code);
  const targetPage = force && !communityState.posts.length ? 1 : communityState.page + 1;
  const cacheKey = `${code}:${targetPage}`;
  const cached = communityState.listCache.get(cacheKey);
  if (!force && cached) {
    communityState.posts = targetPage === 1 ? cached.posts : [...communityState.posts, ...cached.posts];
    communityState.page = targetPage;
    communityState.hasMore = Boolean(cached.hasMore);
    communityState.listSourceUrl = cached.sourcePageUrl || communityState.listSourceUrl;
    renderDiscussion();
    return;
  }
  communityState.loadingList = true;
  communityState.listError = '';
  renderDiscussion();
  try {
    const payload = await communityFetchJson(`${DISCUSSION_LIST_API}?query=${encodeURIComponent(code)}&page=${targetPage}&pageSize=20&_=${Date.now()}`);
    const posts = Array.isArray(payload.posts) ? payload.posts.filter(post => post?.postId && post?.title) : [];
    if (!posts.length) throw new Error('讨论接口未返回可展示帖子');
    communityState.listCache.set(cacheKey, { ...payload, posts });
    communityState.posts = targetPage === 1 ? posts : [...communityState.posts, ...posts.filter(post => !communityState.posts.some(existing => existing.postId === post.postId))];
    communityState.page = targetPage;
    communityState.hasMore = Boolean(payload.hasMore);
    communityState.listSourceUrl = payload.sourcePageUrl || communityState.listSourceUrl;
    communityState.requestCount++;
  } catch (error) {
    communityState.listError = error?.name === 'AbortError' ? '讨论请求超时' : error?.message || String(error);
  } finally {
    communityState.loadingList = false;
    renderDiscussion();
  }
}

async function loadDiscussionPost(postId, force = false, page = 1) {
  const code = discussionCode();
  if (!code || !postId || communityState.loadingPost) return;
  const cacheKey = `${code}:${postId}:${page}`;
  const cached = communityState.postCache.get(cacheKey);
  if (!force && cached) {
    communityState.activePost = cached;
    communityState.comments = page === 1 ? cached.comments || [] : [...communityState.comments, ...(cached.comments || [])];
    communityState.commentPage = page;
    communityState.hasMoreComments = Boolean(cached.hasMoreComments);
    renderDiscussion();
    return;
  }
  communityState.loadingPost = true;
  communityState.postError = '';
  renderDiscussion();
  try {
    const payload = await communityFetchJson(`${DISCUSSION_DETAIL_API}?query=${encodeURIComponent(code)}&postId=${encodeURIComponent(postId)}&page=${page}&pageSize=20&_=${Date.now()}`, 35000);
    communityState.postCache.set(cacheKey, payload);
    communityState.activePost = { ...payload, comments: undefined };
    communityState.comments = page === 1 ? (payload.comments || []) : [...communityState.comments, ...(payload.comments || [])];
    communityState.commentPage = page;
    communityState.hasMoreComments = Boolean(payload.hasMoreComments);
    communityState.requestCount++;
  } catch (error) {
    communityState.postError = error?.name === 'AbortError' ? '帖子请求超时' : error?.message || String(error);
  } finally {
    communityState.loadingPost = false;
    renderDiscussion();
  }
}

function openDiscussionPost(postId) {
  communityState.activePostId = String(postId || '');
  communityState.activePost = null;
  communityState.comments = [];
  communityState.commentPage = 0;
  communityState.hasMoreComments = false;
  communityState.postError = '';
  renderDiscussion();
  loadDiscussionPost(communityState.activePostId, false, 1);
}

function closeDiscussionPost() {
  communityState.activePostId = '';
  communityState.activePost = null;
  communityState.comments = [];
  communityState.commentPage = 0;
  communityState.hasMoreComments = false;
  communityState.postError = '';
  renderDiscussion();
}

function loadMoreDiscussionComments() {
  if (!communityState.activePostId || !communityState.hasMoreComments) return;
  loadDiscussionPost(communityState.activePostId, false, communityState.commentPage + 1);
}

function setDetailMode(mode) {
  communityState.mode = mode === 'discussion' ? 'discussion' : 'market';
  const chart = document.getElementById('chartCard');
  const discussion = document.getElementById('discussionCard');
  document.body.classList.toggle('discussion-mode', communityState.mode === 'discussion');
  if (chart) chart.hidden = communityState.mode === 'discussion';
  if (discussion) discussion.hidden = communityState.mode !== 'discussion';
  document.querySelectorAll('[data-detail-mode]').forEach(button => button.classList.toggle('active', button.dataset.detailMode === communityState.mode));
  if (communityState.mode === 'discussion') {
    const code = discussionCode();
    if (code && communityState.code !== code) resetDiscussionForCode(code);
    renderDiscussion();
    if (!communityState.posts.length && !communityState.loadingList) loadDiscussionPage(false);
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
document.querySelectorAll('[data-detail-mode]').forEach(button => button.addEventListener('click', () => setDetailMode(button.dataset.detailMode)));
document.getElementById('discussionMore')?.addEventListener('click', () => loadDiscussionPage(false));
document.getElementById('discussionPostBack')?.addEventListener('click', closeDiscussionPost);
document.getElementById('discussionSourceButton')?.addEventListener('click', () => {
  const url = communityState.listSourceUrl || (discussionCode() ? `https://guba.eastmoney.com/list,${discussionCode()}.html` : '');
  if (url) window.open(url, '_blank', 'noopener,noreferrer');
});
window.addEventListener('storage', event => {
  if (event.key === WATCHLIST_STORAGE_KEY) renderWatchlistButton();
});
window.addEventListener('ai-ledger-watchlist-change', renderWatchlistButton);

const communityCodeObserver = new MutationObserver(syncCommunityStock);
const observedCode = document.getElementById('code');
const observedName = document.getElementById('name');
if (observedCode) communityCodeObserver.observe(observedCode, { childList: true, characterData: true, subtree: true });
if (observedName) communityCodeObserver.observe(observedName, { childList: true, characterData: true, subtree: true });

renderWatchlistButton();
renderDiscussion();
setDetailMode('market');

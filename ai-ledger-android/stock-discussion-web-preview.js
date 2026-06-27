'use strict';

const API_BASE = 'https://ai-ledger-stock-proxy.onrender.com';
const POST_API = `${API_BASE}/api/stock/a-share/discussion/post`;
const COMMENTS_API = `${API_BASE}/api/stock/a-share/discussion/detail`;
const params = new URLSearchParams(location.search);
const state = {
  query: String(params.get('query') || '').trim(),
  postId: String(params.get('postId') || '').trim(),
  postPayload: null,
  comments: [],
  commentPage: 0,
  hasMoreComments: false,
  loadingPost: false,
  loadingComments: false,
  postError: '',
  commentsError: '',
  commentsLoaded: false,
  sourceUrl: '',
  requestCount: 0
};
const $ = selector => document.querySelector(selector);

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>'"]/g, char => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
  }[char]));
}

function text(value, fallback = '--') {
  const result = String(value ?? '').trim();
  return result && result !== 'null' && result !== 'NaN' ? result : fallback;
}

function number(value) {
  const parsed = Number(String(value ?? '').replace(/[,，]/g, '').trim());
  return Number.isFinite(parsed) ? parsed : null;
}

function formatCount(value) {
  const amount = number(value);
  if (amount == null) return '--';
  if (amount >= 10000) return `${(amount / 10000).toFixed(amount >= 100000 ? 0 : 1)}万`;
  return String(amount);
}

function authorHue(author) {
  let hash = 0;
  for (const char of String(author || '股吧用户')) hash = (hash * 31 + char.charCodeAt(0)) % 360;
  return hash;
}

function authorInitial(author) {
  const value = String(author || '股吧用户').trim();
  return value ? [...value][0] : '股';
}

async function fetchJson(url, timeoutMs = 35000) {
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

function renderPost() {
  const loading = $('#postLoading');
  const content = $('#postContent');
  if (state.loadingPost && !state.postPayload) {
    loading.hidden = false;
    loading.textContent = '正在加载帖子正文…';
    content.hidden = true;
    return;
  }
  if (state.postError && !state.postPayload) {
    loading.hidden = false;
    loading.innerHTML = `${escapeHtml(state.postError)}<br><button type="button" id="retryPost">重新加载</button>`;
    content.hidden = true;
    loading.querySelector('#retryPost')?.addEventListener('click', () => loadPost(true));
    return;
  }
  const payload = state.postPayload;
  const post = payload?.post;
  if (!post) {
    loading.hidden = false;
    loading.textContent = '帖子正文暂不可用';
    content.hidden = true;
    return;
  }
  loading.hidden = true;
  content.hidden = false;
  const author = text(post.author, '股吧用户');
  $('#postAvatar').textContent = authorInitial(author);
  $('#postAvatar').style.setProperty('--avatar-hue', authorHue(author));
  $('#postAuthor').textContent = author;
  $('#postTime').textContent = text(post.publishedAt, '时间未知');
  $('#postStockTag').textContent = `$${text(payload.name, state.query)}(${text(payload.code, state.query)})$`;
  $('#postTitle').textContent = text(post.title, '股吧讨论');
  $('#postBody').textContent = text(post.content, '该帖子没有返回可展示的纯文本正文。');
  $('#postLikeCount').textContent = formatCount(post.likeCount);
  state.sourceUrl = text(post.sourceUrl || payload.sourcePageUrl, '');
  $('#sourceButton').disabled = !state.sourceUrl;
}

function renderNestedReplies(replies) {
  if (!Array.isArray(replies) || !replies.length) return '';
  return `<div class="nested-replies">${replies.slice(0, 8).map(reply => `<div class="nested-reply"><strong>${escapeHtml(reply.author || '股吧用户')}</strong><p>${escapeHtml(reply.content || '')}</p></div>`).join('')}</div>`;
}

function renderComments() {
  const gate = $('#commentsGate');
  const list = $('#commentsList');
  const more = $('#commentsMore');
  const label = $('#commentCountLabel');

  if (!state.commentsLoaded && !state.loadingComments) {
    gate.hidden = false;
    list.hidden = true;
    more.hidden = true;
    label.textContent = '未加载';
    return;
  }

  gate.hidden = true;
  list.hidden = false;
  label.textContent = state.loadingComments && !state.comments.length
    ? '加载中'
    : `${state.comments.length} 条`;

  if (state.loadingComments && !state.comments.length) {
    list.innerHTML = '<div class="comments-loading">正在单独加载网友评论…</div>';
  } else if (state.commentsError && !state.comments.length) {
    list.innerHTML = `<div class="comments-error"><div>${escapeHtml(state.commentsError)}<br><button type="button" id="retryComments">重新加载</button></div></div>`;
    list.querySelector('#retryComments')?.addEventListener('click', () => loadComments(true, 1));
  } else if (!state.comments.length) {
    list.innerHTML = '<div class="comments-empty">帖子正文已加载，但当前页面没有返回公开评论。</div>';
  } else {
    list.innerHTML = state.comments.map(comment => `<article class="comment-row"><div class="comment-head"><span class="comment-author">${escapeHtml(comment.author || '股吧用户')}</span><span class="comment-time">${escapeHtml(comment.publishedAt || '')}</span></div><div class="comment-content">${escapeHtml(comment.content || '')}</div><div class="comment-meta"><span>赞 ${escapeHtml(formatCount(comment.likeCount))}</span><span>回复 ${escapeHtml(formatCount(comment.replyCount))}</span></div>${renderNestedReplies(comment.replies)}</article>`).join('');
  }

  more.hidden = !state.hasMoreComments || state.loadingComments;
  more.disabled = state.loadingComments;
  more.textContent = state.loadingComments ? '加载中…' : '加载更多评论';
}

function renderStatus() {
  const status = $('#dataStatus');
  if (state.loadingPost) {
    status.innerHTML = '<strong>正在加载正文</strong>：评论接口尚未请求。';
    return;
  }
  if (state.postError) {
    status.innerHTML = `<strong>正文加载失败</strong>：${escapeHtml(state.postError)}`;
    return;
  }
  if (state.loadingComments) {
    status.innerHTML = '<strong>正在加载评论</strong>：这是用户点击后的独立请求。';
    return;
  }
  if (state.commentsError) {
    status.innerHTML = `<strong>评论加载失败</strong>：${escapeHtml(state.commentsError)}\n帖子正文仍可正常阅读。`;
    return;
  }
  status.innerHTML = state.commentsLoaded
    ? `<strong>正文与评论已加载</strong>\n评论 ${state.comments.length} 条 · 请求 ${state.requestCount} 次`
    : `<strong>帖子正文已加载</strong>\n评论尚未请求 · 请求 ${state.requestCount} 次`;
}

function renderAll() {
  renderPost();
  renderComments();
  renderStatus();
}

async function loadPost(force = false) {
  if (state.loadingPost || (!force && state.postPayload)) return;
  if (!state.query || !/^\d+$/.test(state.postId)) {
    state.postError = '缺少有效的股票代码或帖子编号';
    renderAll();
    return;
  }
  state.loadingPost = true;
  state.postError = '';
  renderAll();
  try {
    const payload = await fetchJson(`${POST_API}?query=${encodeURIComponent(state.query)}&postId=${encodeURIComponent(state.postId)}&_=${Date.now()}`);
    if (!payload?.post) throw new Error('帖子正文接口未返回内容');
    state.postPayload = payload;
    state.sourceUrl = text(payload.post?.sourceUrl || payload.sourcePageUrl, '');
    state.requestCount++;
  } catch (error) {
    state.postError = error?.name === 'AbortError' ? '帖子正文请求超时' : error?.message || String(error);
  } finally {
    state.loadingPost = false;
    renderAll();
  }
}

async function loadComments(force = false, page = 1) {
  if (state.loadingComments) return;
  if (!force && state.commentsLoaded && page === 1) return;
  state.commentsLoaded = true;
  state.loadingComments = true;
  state.commentsError = '';
  renderAll();
  try {
    const payload = await fetchJson(`${COMMENTS_API}?query=${encodeURIComponent(state.query)}&postId=${encodeURIComponent(state.postId)}&page=${page}&pageSize=20&_=${Date.now()}`);
    const incoming = Array.isArray(payload.comments) ? payload.comments : [];
    state.comments = page === 1
      ? incoming
      : [...state.comments, ...incoming.filter(item => !state.comments.some(existing => existing.commentId === item.commentId))];
    state.commentPage = page;
    state.hasMoreComments = Boolean(payload.hasMoreComments);
    if (!state.postPayload && payload.post) state.postPayload = payload;
    state.requestCount++;
  } catch (error) {
    state.commentsError = error?.name === 'AbortError' ? '评论请求超时' : error?.message || String(error);
  } finally {
    state.loadingComments = false;
    renderAll();
  }
}

function openComments() {
  $('#commentsCard').scrollIntoView({ behavior: 'smooth', block: 'start' });
  if (!state.commentsLoaded) loadComments(false, 1);
}

$('#backButton').addEventListener('click', () => {
  if (history.length > 1) history.back();
  else location.href = `./stock-detail-web-preview.html?query=${encodeURIComponent(state.query)}`;
});
$('#sourceButton').addEventListener('click', () => {
  if (state.sourceUrl) window.open(state.sourceUrl, '_blank', 'noopener,noreferrer');
});
$('#commentAction').addEventListener('click', openComments);
$('#loadCommentsButton').addEventListener('click', () => loadComments(false, 1));
$('#commentsMore').addEventListener('click', () => loadComments(false, state.commentPage + 1));
$('#reloadPost').addEventListener('click', () => loadPost(true));
$('#reloadComments').addEventListener('click', () => loadComments(true, 1));
$('#phoneWidth').addEventListener('input', event => {
  document.documentElement.style.setProperty('--phone-w', `${event.target.value}px`);
  $('#phoneWidthText').textContent = `${event.target.value}px`;
});
$('#mobileToggle').addEventListener('click', () => document.body.classList.toggle('controls-open'));

document.addEventListener('visibilitychange', () => {
  if (!document.hidden && !state.postPayload && !state.loadingPost) loadPost(false);
});

updateClock();
setInterval(updateClock, 30000);
renderAll();
setTimeout(() => loadPost(false), 160);

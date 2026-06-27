'use strict';

communityState.sort = 'latest';
let discussionToastTimer = null;

function feedAuthorHue(author) {
  let hash = 0;
  for (const char of String(author || '股吧用户')) hash = (hash * 31 + char.charCodeAt(0)) % 360;
  return hash;
}

function feedAuthorInitial(author) {
  const text = String(author || '股吧用户').trim();
  return text ? [...text][0] : '股';
}

function feedPostText(post) {
  const original = String(post?.title || '').trim();
  if (!original) return '点击查看该条讨论';
  const withoutDollarTag = original.replace(/^\$[^$]{1,48}\$\s*/, '').trim();
  const withoutHashTag = withoutDollarTag.replace(/^#[^#]{1,48}#\s*/, '').trim();
  return withoutHashTag || original;
}

function feedSortedPosts() {
  const posts = [...communityState.posts];
  if (communityState.sort !== 'hot') return posts;
  return posts.sort((left, right) => {
    const leftScore = (communityNumber(left.commentCount) || 0) * 500 + (communityNumber(left.readCount) || 0);
    const rightScore = (communityNumber(right.commentCount) || 0) * 500 + (communityNumber(right.readCount) || 0);
    return rightScore - leftScore;
  });
}

function renderDiscussionSortChrome() {
  document.querySelectorAll('[data-discussion-sort]').forEach(button => {
    button.classList.toggle('active', button.dataset.discussionSort === communityState.sort);
  });
}

function openStandaloneDiscussion(postId) {
  const code = discussionCode();
  if (!code || !postId) return;
  location.href = `./stock-discussion-web-preview.html?query=${encodeURIComponent(code)}&postId=${encodeURIComponent(postId)}`;
}

renderDiscussionHeader = function renderCommunityHeader() {
  const title = document.getElementById('discussionTitle');
  const subtitle = document.getElementById('discussionSubtitle');
  if (title) title.textContent = '社区';
  if (subtitle) subtitle.textContent = `${discussionName()}（${discussionCode() || '------'}）· 东方财富股吧只读社区`;
};

renderDiscussionList = function renderCommunityFeed() {
  const view = document.getElementById('discussionListView');
  const list = document.getElementById('discussionList');
  const more = document.getElementById('discussionMore');
  const count = document.getElementById('discussionCount');
  if (!view || !list || !more || !count) return;

  view.hidden = false;
  renderDiscussionSortChrome();
  count.textContent = communityState.posts.length ? `${communityState.posts.length} 条` : '等待数据';

  if (communityState.loadingList && !communityState.posts.length) {
    list.innerHTML = '<div class="discussion-loading">正在读取真实股吧社区…</div>';
  } else if (communityState.listError && !communityState.posts.length) {
    list.innerHTML = `<div class="discussion-error"><div>${communityEscape(communityState.listError)}<br><button type="button" data-discussion-retry>重新加载</button></div></div>`;
  } else if (!communityState.posts.length) {
    list.innerHTML = '<div class="discussion-empty">当前股票暂未返回可展示的社区帖子</div>';
  } else {
    const stockTag = `$${discussionName()}(${discussionCode()})$`;
    list.innerHTML = feedSortedPosts().map(post => {
      const commentCount = communityNumber(post.commentCount) || 0;
      const readCount = communityNumber(post.readCount) || 0;
      const author = post.author || '股吧用户';
      const kind = discussionKindLabel(post.kind);
      const kindChip = post.kind && post.kind !== 'discussion'
        ? `<span class="feed-kind-chip">${communityEscape(kind)}</span>`
        : '';
      const commentPreview = commentCount > 0
        ? `<strong>${communityEscape(author)}的讨论：</strong>已有 ${communityEscape(formatDiscussionCount(commentCount))} 条网友评论，点击进入帖子详情后按需加载`
        : '点击进入独立帖子详情页查看正文';
      return `<article class="discussion-feed-card" data-post-id="${communityEscape(post.postId)}" tabindex="0" role="button" aria-label="查看${communityEscape(post.title)}"><div class="feed-author-row"><span class="feed-avatar" style="--avatar-hue:${feedAuthorHue(author)}">${communityEscape(feedAuthorInitial(author))}</span><span class="feed-author-copy"><strong>${communityEscape(author)}</strong><span>${communityEscape(post.updatedAt || '时间未知')}</span></span><span class="feed-more">•••</span></div><div class="feed-content">${kindChip}<span class="feed-stock-tag">${communityEscape(stockTag)}</span>${communityEscape(feedPostText(post))}</div><div class="feed-actions"><span class="feed-action"><span class="feed-action-icon">↗</span>分享</span><span class="feed-action"><span class="feed-action-icon">◯</span>${communityEscape(formatDiscussionCount(commentCount))}</span><span class="feed-action"><span class="feed-action-icon">♡</span>阅读 ${communityEscape(formatDiscussionCount(readCount))}</span></div><div class="feed-comment-preview">${commentPreview}</div></article>`;
    }).join('');
  }

  more.disabled = communityState.loadingList || !communityState.hasMore;
  more.textContent = communityState.loadingList && communityState.posts.length
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
  list.querySelectorAll('[data-discussion-retry]').forEach(button => button.addEventListener('click', () => loadDiscussionPage(true)));
};

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

document.querySelectorAll('[data-discussion-sort]').forEach(button => {
  button.addEventListener('click', () => setDiscussionSort(button.dataset.discussionSort));
});

document.getElementById('discussionCompose')?.addEventListener('click', () => {
  showDiscussionToast('当前为只读社区，暂不支持登录、发帖或回复');
});

renderDiscussionSortChrome();
renderDiscussion();

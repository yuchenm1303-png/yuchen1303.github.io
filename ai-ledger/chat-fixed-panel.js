(() => {
  'use strict';

  const CHAT_KEY = 'ai-ledger-chat-v2';
  const BOOT_FLAG = 'chatFixedPanelReady';

  function readMessages() {
    try {
      const parsed = JSON.parse(localStorage.getItem(CHAT_KEY) || '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  function clearConversation() {
    const initial = [{
      id: 'welcome',
      role: 'assistant',
      content: '你好，我是你的 AI 助手。你可以让我记账、查账单、查天气、读网页、设置提醒、打开应用，也可以直接和我聊天。',
      action: 'chat',
      records: [],
      draftState: 'none',
      source: 'builtin_profile',
    }];

    try {
      localStorage.setItem(CHAT_KEY, JSON.stringify(initial));
      window.chatMessages = initial;
    } catch {}

    if (window.AiAssistantRuntime?.setChatWindowLimit) window.AiAssistantRuntime.setChatWindowLimit(60);
    if (typeof window.renderAll === 'function') window.renderAll();
    else if (typeof window.renderChat === 'function') window.renderChat();
    else window.location.reload();

    if (window.ChatSourceBadges?.refresh) window.setTimeout(window.ChatSourceBadges.refresh, 80);
    updateProgress();
    showToast('已清空对话');
  }

  function showToast(text) {
    const toast = document.querySelector('#toast');
    if (!toast) return;
    toast.textContent = text;
    toast.classList.add('show');
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => toast.classList.remove('show'), 1800);
  }

  function ensureCountCard(strip) {
    let card = strip.querySelector('#chatCountCard');
    if (card) return card;
    card = document.createElement('div');
    card.id = 'chatCountCard';
    card.className = 'chat-count-card';
    card.innerHTML = '<div class="chat-count-top"><span>对话进度</span><strong id="chatCountText">0 条</strong></div><div class="chat-count-track"><span id="chatCountFill" class="chat-count-fill"></span></div>';
    strip.appendChild(card);
    return card;
  }

  function updateProgress() {
    const messages = readMessages();
    const count = messages.filter((item) => item?.id !== 'welcome' && (item?.role === 'user' || item?.role === 'assistant')).length;
    const percent = Math.max(4, Math.min(100, Math.round((count / 40) * 100)));
    const card = document.querySelector('#chatCountCard');
    const text = document.querySelector('#chatCountText');
    const fill = document.querySelector('#chatCountFill');
    if (card) card.style.setProperty('--chat-progress', `${percent}%`);
    if (text) text.textContent = `${count} 条`;
    if (fill) fill.style.width = `${percent}%`;
  }

  function arrangeControls() {
    const strip = document.querySelector('.chat-summary-strip');
    const model = document.querySelector('#chatModelPickerBtn');
    const badge = document.querySelector('#aiModeBadge');
    const clear = document.querySelector('#clearChatInlineBtn');
    if (!strip || !model) return;

    strip.classList.add('model-picker-hero-strip');
    strip.querySelectorAll('.summary-chip').forEach((node) => node.remove());

    if (model.parentElement !== strip) strip.appendChild(model);
    model.classList.add('hero-model-picker-btn');

    if (badge && badge.parentElement !== strip) strip.appendChild(badge);
    if (clear && clear.parentElement !== strip) strip.appendChild(clear);
    if (clear && clear.dataset.fixedPanelBound !== 'true') {
      clear.dataset.fixedPanelBound = 'true';
      clear.textContent = '清空';
      clear.addEventListener('click', (event) => {
        event.preventDefault();
        clearConversation();
      });
    }

    ensureCountCard(strip);
    updateProgress();
  }

  function observeMessages() {
    const host = document.querySelector('#chatMessages');
    if (!host || host.dataset.fixedPanelProgressReady === 'true') return;
    host.dataset.fixedPanelProgressReady = 'true';
    const observer = new MutationObserver(() => updateProgress());
    observer.observe(host, { childList: true, subtree: true });
  }

  function boot() {
    document.body.classList.add('chat-panel-fixed');
    arrangeControls();
    observeMessages();
    updateProgress();
  }

  if (document.documentElement.dataset[BOOT_FLAG] !== 'true') {
    document.documentElement.dataset[BOOT_FLAG] = 'true';
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
    else boot();
    [120, 360, 900, 1500].forEach((delay) => window.setTimeout(boot, delay));
    window.setInterval(() => { arrangeControls(); updateProgress(); }, 1200);
  }

  window.ChatFixedPanel = { boot, updateProgress, clearConversation };
})();
(() => {
  'use strict';

  function isQuickAiEntry() {
    try {
      const params = new URLSearchParams(window.location.search || '');
      return params.get('mode') === 'quick_ai'
        || params.get('quick') === 'ai'
        || window.location.hash === '#ai-chat'
        || window.location.hash === '#quick-ai';
    } catch {
      return false;
    }
  }

  if (!isQuickAiEntry()) return;

  document.documentElement.dataset.entry = 'quick-ai';

  const params = new URLSearchParams(window.location.search || '');
  const bridgeNames = ['AndroidQuickAi', 'AiLedgerAndroid', 'QuickAiBridge'];

  function getBridge() {
    return bridgeNames.map((name) => window[name]).find(Boolean) || null;
  }

  function notifyNative(type, payload = {}) {
    const bridge = getBridge();
    if (!bridge) return false;
    const message = JSON.stringify({ type, payload, at: Date.now() });
    try {
      if (typeof bridge.postMessage === 'function') {
        bridge.postMessage(message);
        return true;
      }
      if (type === 'close' && typeof bridge.closeQuickAi === 'function') {
        bridge.closeQuickAi();
        return true;
      }
      if (type === 'expand' && typeof bridge.openFullApp === 'function') {
        bridge.openFullApp();
        return true;
      }
    } catch (error) {
      console.warn('[quick-ai] native bridge failed:', error);
    }
    return false;
  }

  function closeQuickAi() {
    if (notifyNative('close')) return;
    if (window.history.length > 1) window.history.back();
    else window.close();
  }

  function openFullApp() {
    if (notifyNative('expand')) return;
    window.location.href = './index.html';
  }

  function focusComposer(delay = 180) {
    window.setTimeout(() => {
      const input = document.querySelector('#aiInput');
      if (!input) return;
      input.focus({ preventScroll: true });
      input.scrollIntoView({ block: 'nearest' });
    }, delay);
  }

  function applyHeaderCopy() {
    const eyebrow = document.querySelector('#view-ai .eyebrow');
    const title = document.querySelector('#view-ai h1');
    const subtext = document.querySelector('#view-ai .subtext');
    const input = document.querySelector('#aiInput');
    const hint = document.querySelector('#aiModeHint');

    if (eyebrow) eyebrow.textContent = '通知中心快捷入口';
    if (title) title.textContent = 'AI 小窗';
    if (subtext) subtext.textContent = '下滑通知栏，一点即聊';
    if (input) input.placeholder = '直接说：设闹钟、导航、记账、查一下…';
    if (hint) hint.textContent = '这是快速对话模式，适合从安卓快捷设置磁贴唤起。';
  }

  function installWindowActions() {
    const actions = document.querySelector('.chat-header-actions');
    if (!actions || document.querySelector('#quickAiWindowActions')) return;

    const group = document.createElement('div');
    group.id = 'quickAiWindowActions';
    group.className = 'quick-ai-window-actions';
    group.innerHTML = [
      '<button class="quick-ai-window-btn" type="button" data-quick-ai-action="expand">完整应用</button>',
      '<button class="quick-ai-window-btn close" type="button" data-quick-ai-action="close" aria-label="关闭 AI 小窗">关闭</button>',
    ].join('');
    actions.appendChild(group);
  }

  function lockToAiView() {
    document.body.classList.add('quick-ai-entry');
    window.AiAssistantViews?.open?.('ai');
    document.querySelectorAll('#view-tools, #view-stats, #view-list, #view-settings').forEach((view) => {
      view.setAttribute('aria-hidden', 'true');
    });
  }

  async function handlePrefill() {
    const text = params.get('q') || params.get('text') || '';
    if (!text) return;
    const input = document.querySelector('#aiInput');
    if (input) {
      input.value = text;
      input.dispatchEvent(new Event('input', { bubbles: true }));
    }
    if (params.get('autosend') === '1' && window.AiAssistantRuntime?.ask) {
      if (input) input.value = '';
      await window.AiAssistantRuntime.ask(text);
    }
  }

  function bindQuickActions() {
    document.addEventListener('click', (event) => {
      const button = event.target.closest('[data-quick-ai-action]');
      if (!button) return;
      const action = button.dataset.quickAiAction;
      if (action === 'close') closeQuickAi();
      if (action === 'expand') openFullApp();
    });

    window.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') closeQuickAi();
    });
  }

  function boot() {
    lockToAiView();
    applyHeaderCopy();
    installWindowActions();
    bindQuickActions();
    handlePrefill();
    focusComposer();
    window.dispatchEvent(new CustomEvent('quick-ai-entry-ready'));
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot, { once: true });
  } else {
    boot();
  }

  window.AiLedgerQuickEntry = {
    active: true,
    close: closeQuickAi,
    openFullApp,
    focus: focusComposer,
  };
})();

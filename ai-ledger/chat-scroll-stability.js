(() => {
  'use strict';

  const STYLE_ID = 'chat-scroll-stability-style';
  let lastPinnedAt = 0;

  function installStyle() {
    if (document.getElementById(STYLE_ID)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      #chatMessages{scroll-padding-bottom:34px!important;padding-bottom:16px!important;overflow-y:auto!important;}
      .chat-row,.chat-response,.chat-bubble{overflow:visible!important;}
    `;
    document.head.appendChild(style);
  }

  function pinBottom(reason = '') {
    const host = document.querySelector('#chatMessages');
    if (!host) return;
    lastPinnedAt = Date.now();
    const run = () => {
      try { host.scrollTop = host.scrollHeight + 100; } catch {}
    };
    requestAnimationFrame(run);
    [24, 80, 180, 360, 700].forEach((delay) => window.setTimeout(run, delay));
  }

  function shouldPinAfterMutation(mutations) {
    return mutations.some((mutation) => [...mutation.addedNodes, ...mutation.removedNodes].some((node) => {
      if (!(node instanceof HTMLElement)) return false;
      return node.id === 'typingRow'
        || node.classList?.contains('chat-row')
        || node.classList?.contains('chat-source-badge-row')
        || node.querySelector?.('.chat-row,.chat-source-badge-row');
    }));
  }

  function installObserver() {
    const host = document.querySelector('#chatMessages');
    if (!host || host.dataset.scrollStabilityObserver) return;
    host.dataset.scrollStabilityObserver = '1';
    const observer = new MutationObserver((mutations) => {
      if (shouldPinAfterMutation(mutations)) pinBottom('mutation');
    });
    observer.observe(host, { childList: true, subtree: true });
    pinBottom('boot');
  }

  function watchdog() {
    const host = document.querySelector('#chatMessages');
    if (!host) return;
    const bottomGap = host.scrollHeight - host.clientHeight - host.scrollTop;
    if (document.querySelector('#typingRow') || (Date.now() - lastPinnedAt < 1800 && bottomGap > 6)) pinBottom('watchdog');
  }

  function boot() {
    installStyle();
    installObserver();
    window.setInterval(watchdog, 900);
  }

  window.ChatScrollStability = { pinBottom, installObserver };
  if (window.AiLedgerChatShared) window.AiLedgerChatShared.pinChatBottom = pinBottom;

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();

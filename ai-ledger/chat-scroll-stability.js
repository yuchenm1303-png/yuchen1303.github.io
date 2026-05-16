(() => {
  'use strict';

  const STYLE_ID = 'chat-scroll-stability-style';
  let lastPinnedAt = 0;
  let lockUntil = 0;

  function installStyle() {
    const old = document.getElementById(STYLE_ID);
    if (old) old.remove();
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      #chatMessages{scroll-padding-bottom:42px!important;padding-bottom:22px!important;overflow-y:auto!important;overflow-anchor:none!important;}
      .chat-row,.chat-response,.chat-bubble{overflow:visible!important;}
      body.chat-render-lock #chatMessages{scroll-behavior:auto!important;}
    `;
    document.head.appendChild(style);
  }

  function rawPin() {
    const host = document.querySelector('#chatMessages');
    if (!host) return;
    try {
      host.scrollTop = host.scrollHeight + 160;
    } catch {}
  }

  function pinBottom(reason = '') {
    lastPinnedAt = Date.now();
    lockUntil = Date.now() + 1200;
    document.body.classList.add('chat-render-lock');
    rawPin();
    requestAnimationFrame(rawPin);
    [0, 16, 32, 64, 120, 220, 420, 760, 1100].forEach((delay) => window.setTimeout(rawPin, delay));
    window.setTimeout(() => {
      if (Date.now() > lockUntil) document.body.classList.remove('chat-render-lock');
    }, 1300);
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
      if (!shouldPinAfterMutation(mutations)) return;
      rawPin();
      pinBottom('mutation');
    });
    observer.observe(host, { childList: true, subtree: true });
    pinBottom('boot');
  }

  function watchdog() {
    const host = document.querySelector('#chatMessages');
    if (!host) return;
    const bottomGap = host.scrollHeight - host.clientHeight - host.scrollTop;
    if (document.querySelector('#typingRow') || Date.now() < lockUntil || (Date.now() - lastPinnedAt < 2200 && bottomGap > 4)) {
      rawPin();
    }
  }

  function boot() {
    installStyle();
    installObserver();
    window.setInterval(watchdog, 180);
  }

  window.ChatScrollStability = { pinBottom, installObserver, rawPin };
  if (window.AiLedgerChatShared) window.AiLedgerChatShared.pinChatBottom = pinBottom;

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();

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
    try { host.scrollTop = host.scrollHeight + 120; } catch {}
  }

  function pinBottom(reason = '') {
    lastPinnedAt = Date.now();
    lockUntil = Date.now() + 700;
    document.body.classList.add('chat-render-lock');
    rawPin();
    requestAnimationFrame(rawPin);
    [0, 16, 48, 120, 260, 520].forEach((delay) => window.setTimeout(rawPin, delay));
    window.setTimeout(() => {
      if (Date.now() > lockUntil) document.body.classList.remove('chat-render-lock');
    }, 760);
  }

  function isMessageRowMutation(node) {
    if (!(node instanceof HTMLElement)) return false;
    return node.classList?.contains('chat-row') || Boolean(node.querySelector?.(':scope > .chat-row'));
  }

  function shouldPinAfterMutation(mutations) {
    // 只在真正新增/移除整条消息时贴底。生成中 typing、标签、复制/重试按钮变化不再反复强制下拉。
    return mutations.some((mutation) => [...mutation.addedNodes, ...mutation.removedNodes].some(isMessageRowMutation));
  }

  function installObserver() {
    const host = document.querySelector('#chatMessages');
    if (!host || host.dataset.scrollStabilityObserver) return;
    host.dataset.scrollStabilityObserver = '1';
    const observer = new MutationObserver((mutations) => {
      if (!shouldPinAfterMutation(mutations)) return;
      pinBottom('message-row');
    });
    observer.observe(host, { childList: true, subtree: false });
    pinBottom('boot');
  }

  function watchdog() {
    const host = document.querySelector('#chatMessages');
    if (!host) return;
    const bottomGap = host.scrollHeight - host.clientHeight - host.scrollTop;
    if (Date.now() < lockUntil || (Date.now() - lastPinnedAt < 900 && bottomGap > 4)) rawPin();
  }

  function boot() {
    installStyle();
    installObserver();
    window.setInterval(watchdog, 240);
  }

  window.ChatScrollStability = { pinBottom, installObserver, rawPin };
  if (window.AiLedgerChatShared) window.AiLedgerChatShared.pinChatBottom = pinBottom;

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();

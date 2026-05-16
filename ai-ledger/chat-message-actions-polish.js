(() => {
  'use strict';

  const STYLE_ID = 'chat-message-actions-polish-style';
  const CHAT_KEY = 'ai-ledger-chat-v2';

  function installStyle() {
    const old = document.getElementById(STYLE_ID);
    if (old) old.remove();
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      .chat-row.assistant{flex-direction:column!important;align-items:flex-start!important;}
      .chat-row.user{flex-direction:column!important;align-items:flex-end!important;}
      .chat-row.assistant > .chat-bubble,.chat-row.assistant > .chat-response{max-width:92%!important;}
      .chat-row.user > .chat-bubble,.chat-row.user > .chat-response{max-width:86%!important;}
      .chat-message-actions{display:flex!important;align-items:center!important;justify-content:flex-start!important;gap:8px!important;margin:7px 0 2px 8px!important;min-height:28px!important;width:auto!important;max-width:92%!important;position:relative!important;z-index:14!important;}
      .chat-row.user .chat-message-actions{justify-content:flex-end!important;margin-left:auto!important;margin-right:8px!important;max-width:86%!important;}
      .chat-action-btn{appearance:none!important;border:1px solid rgba(255,255,255,.26)!important;border-radius:999px!important;background:linear-gradient(135deg,rgba(255,255,255,.20),rgba(255,255,255,.065))!important;min-width:48px!important;min-height:29px!important;padding:0 12px!important;font-size:11px!important;font-weight:900!important;line-height:1!important;letter-spacing:.02em!important;backdrop-filter:blur(14px) saturate(155%)!important;-webkit-backdrop-filter:blur(14px) saturate(155%)!important;box-shadow:inset 0 1px 0 rgba(255,255,255,.26),0 8px 18px rgba(0,0,0,.12)!important;}
      .chat-action-btn.retry{color:#d8caff!important;border-color:rgba(174,150,255,.36)!important;background:linear-gradient(135deg,rgba(126,87,255,.22),rgba(255,255,255,.07))!important;}
      .chat-action-btn.copy{color:#9ff8d4!important;border-color:rgba(42,218,150,.34)!important;background:linear-gradient(135deg,rgba(22,190,121,.20),rgba(255,255,255,.07))!important;}
      .chat-action-btn:active{transform:scale(.96)!important;filter:brightness(1.08)!important;}
      .chat-bubble[data-badge-text]::after,.chat-response[data-badge-text]::after{content:'● ' attr(data-badge-text)!important;position:static!important;display:block!important;width:fit-content!important;max-width:100%!important;box-sizing:border-box!important;margin-top:14px!important;border-radius:999px!important;padding:7px 12px!important;font-size:11px!important;font-weight:900!important;letter-spacing:.012em!important;line-height:1.18!important;white-space:normal!important;word-break:break-word!important;z-index:12!important;opacity:1!important;visibility:visible!important;box-shadow:inset 0 1px 0 rgba(255,255,255,.30),0 10px 22px rgba(0,0,0,.11)!important;backdrop-filter:blur(14px) saturate(150%)!important;-webkit-backdrop-filter:blur(14px) saturate(150%)!important;}
      .chat-bubble[data-badge-text]::before,.chat-response[data-badge-text]::before{display:none!important;content:none!important;}
      body.assistant-compact .chat-message-actions{margin-top:6px!important;gap:6px!important;}
      body.assistant-compact .chat-action-btn{min-width:42px!important;min-height:25px!important;padding:0 10px!important;font-size:10px!important;}
    `;
    document.head.appendChild(style);
  }

  function readMessages() {
    const out = [];
    const seen = new Set();
    const push = (message) => {
      if (!message?.id || seen.has(String(message.id))) return;
      seen.add(String(message.id));
      out.push(message);
    };
    try {
      const local = JSON.parse(localStorage.getItem(CHAT_KEY) || '[]');
      if (Array.isArray(local)) local.forEach(push);
    } catch {}
    if (Array.isArray(window.chatMessages)) window.chatMessages.forEach(push);
    return out;
  }

  function findMessage(id) {
    return readMessages().find((message) => String(message.id) === String(id));
  }

  function copyWithSelection(text) {
    const area = document.createElement('textarea');
    area.value = text;
    area.setAttribute('readonly', 'readonly');
    area.style.position = 'fixed';
    area.style.top = '0';
    area.style.left = '0';
    area.style.width = '1px';
    area.style.height = '1px';
    area.style.opacity = '0.01';
    area.style.pointerEvents = 'none';
    area.style.zIndex = '-1';
    document.body.appendChild(area);
    area.focus({ preventScroll: true });
    area.select();
    area.setSelectionRange(0, area.value.length);
    let ok = false;
    try { ok = document.execCommand('copy'); } catch { ok = false; }
    area.remove();
    if (!ok) throw new Error('execCommand copy failed');
  }

  async function copyText(text) {
    const value = String(text || '').trim();
    if (!value) throw new Error('empty text');
    try {
      if (navigator.clipboard?.writeText && window.isSecureContext !== false) {
        await navigator.clipboard.writeText(value);
        return true;
      }
    } catch {}
    copyWithSelection(value);
    return true;
  }

  function flashButton(button, text) {
    const old = button.textContent;
    button.textContent = text;
    button.disabled = true;
    setTimeout(() => {
      button.textContent = old;
      button.disabled = false;
    }, 1100);
  }

  async function handleCopy(event, button) {
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation?.();
    const message = findMessage(button.dataset.messageId);
    const text = String(message?.content || '').trim();
    if (!text) return flashButton(button, '无内容');
    try {
      await copyText(text);
      flashButton(button, '已复制');
    } catch {
      const input = document.querySelector('#aiInput');
      if (input) {
        input.value = text;
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.focus?.();
        flashButton(button, '已填入');
      } else {
        flashButton(button, '复制失败');
      }
    }
  }

  function ensureUserCopyActions() {
    const messages = readMessages();
    const byId = new Map(messages.map((message) => [String(message.id), message]));
    let changed = false;
    document.querySelectorAll('.chat-row.user[data-message-id]').forEach((row) => {
      const id = row.dataset.messageId;
      const message = byId.get(String(id));
      if (!message || !String(message.content || '').trim()) return;
      const existing = row.querySelector(':scope .chat-message-actions[data-user-copy="1"]');
      if (existing) return;
      row.querySelectorAll(':scope .chat-message-actions').forEach((node) => node.remove());
      const bubble = row.querySelector('.chat-response,.chat-bubble');
      if (!bubble) return;
      bubble.insertAdjacentHTML('afterend', `<div class="chat-message-actions" data-user-copy="1" data-for-message="${String(id).replaceAll('"', '&quot;')}"><button class="chat-action-btn copy" type="button" data-chat-action="copy" data-message-id="${String(id).replaceAll('"', '&quot;')}">复制</button></div>`);
      changed = true;
    });
    if (changed) window.ChatScrollStability?.pinBottom?.('user-copy-actions');
  }

  function installCopyCapture() {
    if (document.body.dataset.chatCopyPolishBound === '1') return;
    document.body.dataset.chatCopyPolishBound = '1';
    document.addEventListener('click', (event) => {
      const button = event.target.closest('[data-chat-action="copy"]');
      if (!button) return;
      handleCopy(event, button);
    }, true);
  }

  function installObserver() {
    const host = document.querySelector('#chatMessages');
    if (!host || host.dataset.userCopyObserver === '1') return;
    host.dataset.userCopyObserver = '1';
    const observer = new MutationObserver(() => {
      ensureUserCopyActions();
      requestAnimationFrame(ensureUserCopyActions);
    });
    observer.observe(host, { childList: true, subtree: true });
    ensureUserCopyActions();
    setInterval(ensureUserCopyActions, 700);
  }

  function boot() {
    installStyle();
    installCopyCapture();
    installObserver();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();

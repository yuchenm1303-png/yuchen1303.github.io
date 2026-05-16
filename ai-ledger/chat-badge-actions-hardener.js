(() => {
  'use strict';

  const shared = window.AiLedgerChatShared;
  if (!shared || window.__chatBadgeActionsHardenerInstalled) return;
  window.__chatBadgeActionsHardenerInstalled = true;

  const STYLE_ID = 'chat-badge-actions-hardener-style';
  const CHAT_KEY = shared.CHAT_KEY || 'ai-ledger-chat-v2';
  const { SOURCE_LABELS = {}, escapeHtml = (v) => String(v || '') } = shared;
  const animatedIds = new Set();
  let repaintTimer = 0;
  let repaintFlip = false;

  function readMessages() {
    const out = [];
    const seen = new Set();
    const push = (message) => {
      if (!message?.id) return;
      const id = String(message.id);
      if (seen.has(id)) return;
      seen.add(id);
      out.push(message);
    };
    try {
      const saved = JSON.parse(localStorage.getItem(CHAT_KEY) || '[]');
      if (Array.isArray(saved)) saved.forEach(push);
    } catch {}
    if (Array.isArray(window.chatMessages)) window.chatMessages.forEach(push);
    return out;
  }

  function messageMap() {
    return new Map(readMessages().map((message) => [String(message.id), message]));
  }

  function compactVersion(version) {
    return String(version || '')
      .replace(/^ai-ledger-/i, '')
      .replace(/worker-/i, '')
      .replace(/orchestrator-/i, 'orch-')
      .replace(/attachment-gateway-/i, 'attach-')
      .replace(/command-protocol-/i, 'cmd-')
      .replace(/cloud-error-normalizer-/i, 'err-')
      .slice(0, 90);
  }

  function inferSource(message) {
    if (!message || message.role !== 'assistant') return '';
    const version = String(message.version || '');
    if (message.source) return message.source;
    if (/tavily/i.test(version)) return 'tavily_web_search';
    if (/weather|open-meteo/i.test(version)) return 'weather_tool';
    if (/gemini/i.test(version)) return 'gemini_chat';
    if (/workers|llama/i.test(version)) return 'workers_ai';
    if (/kimi|nvidia|mistral|qwen|deepseek/i.test(version)) return 'nvidia_chat';
    if (message.mobileCommand) return 'local_mobile';
    if (Array.isArray(message.records) && message.records.length) return 'local';
    if (message.id === 'welcome') return 'builtin_profile';
    return 'cloud_ai';
  }

  function nvidiaLabel(message) {
    const text = `${message?.modelLabel || ''} ${message?.model || ''} ${message?.version || ''}`.toLowerCase();
    if (text.includes('kimi')) return 'Kimi 对话';
    if (text.includes('mistral')) return 'Mistral 对话';
    if (text.includes('qwen')) return 'Qwen 对话';
    if (text.includes('deepseek')) return 'DeepSeek 对话';
    return 'NVIDIA NIM';
  }

  function sourceMeta(source, message) {
    if (source === 'nvidia_chat') return { label: nvidiaLabel(message), tone: 'cloud' };
    if (source === 'nvidia_vision') return { label: nvidiaLabel(message).replace('对话', '识图'), tone: 'vision' };
    if (SOURCE_LABELS[source]) return SOURCE_LABELS[source];
    if (/gemini/i.test(source)) return { label: 'Gemini AI', tone: 'gemini' };
    if (/worker|llama/i.test(source)) return { label: 'Workers AI', tone: 'cloud' };
    if (/search|tavily/i.test(source)) return { label: '联网搜索', tone: 'online' };
    if (/weather/i.test(source)) return { label: '实时天气', tone: 'online' };
    if (/error|failed|timeout/i.test(source)) return { label: '云端错误', tone: 'error' };
    return { label: '云端 AI', tone: 'cloud' };
  }

  function modelText(message) {
    const parts = [];
    if (message?.modelLabel) parts.push(message.modelLabel);
    else if (message?.model) parts.push(message.model);
    const version = compactVersion(message?.version);
    if (version && !parts.some((part) => version.includes(part))) parts.push(version);
    return parts.filter(Boolean).join(' · ');
  }

  function attachmentBadge(message) {
    const list = Array.isArray(message?.attachments) ? message.attachments : [];
    if (!list.length) return null;
    const imageCount = list.filter((item) => String(item.mimeType || '').startsWith('image/')).length;
    const pdfCount = list.filter((item) => /pdf/i.test(String(item.mimeType || ''))).length;
    const fileCount = list.length - imageCount - pdfCount;
    const labels = [];
    if (imageCount) labels.push(imageCount === 1 ? '本图' : `${imageCount}张图片`);
    if (pdfCount) labels.push(pdfCount === 1 ? 'PDF' : `${pdfCount}个PDF`);
    if (fileCount) labels.push(fileCount === 1 ? '文件' : `${fileCount}个文件`);
    return { text: `已附带 ${labels.join('+') || '附件'}`, tone: 'attachment' };
  }

  function badgeFor(row, message) {
    if (!message) return null;
    if (row.classList.contains('user')) return attachmentBadge(message);
    if (message.role !== 'assistant') return null;
    const source = inferSource(message);
    const meta = sourceMeta(source, message);
    const detail = modelText(message);
    return { text: detail ? `${meta.label} · ${detail}` : meta.label, tone: meta.tone || 'cloud' };
  }

  function installStyle() {
    document.getElementById(STYLE_ID)?.remove();
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      .chat-row{overflow:visible!important;}
      .chat-row.assistant{flex-direction:column!important;align-items:flex-start!important;}
      .chat-row.user{flex-direction:column!important;align-items:flex-end!important;}
      .chat-row[data-entered="1"] .chat-bubble,.chat-row[data-entered="1"] .chat-response{animation:chatBubbleIn .34s cubic-bezier(.2,.9,.18,1) both;transform-origin:center bottom;}
      .chat-row.user[data-entered="1"] .chat-bubble,.chat-row.user[data-entered="1"] .chat-response{animation-name:chatBubbleInUser;transform-origin:right bottom;}
      .chat-row.assistant[data-entered="1"] .chat-bubble,.chat-row.assistant[data-entered="1"] .chat-response{transform-origin:left bottom;}
      .chat-row[data-entered="1"] .chat-hard-badge{animation:chatBadgeIn .38s cubic-bezier(.2,.9,.18,1) .05s both;}
      .chat-row[data-entered="1"] .chat-hard-actions{animation:chatActionsIn .34s cubic-bezier(.2,.9,.18,1) .08s both;}
      @keyframes chatBubbleIn{0%{opacity:0;transform:translateY(12px) scale(.965);filter:blur(4px)}68%{opacity:1;transform:translateY(-1px) scale(1.006);filter:blur(0)}100%{opacity:1;transform:translateY(0) scale(1);filter:blur(0)}}
      @keyframes chatBubbleInUser{0%{opacity:0;transform:translate(10px,12px) scale(.965);filter:blur(4px)}68%{opacity:1;transform:translate(-1px,-1px) scale(1.006);filter:blur(0)}100%{opacity:1;transform:translate(0,0) scale(1);filter:blur(0)}}
      @keyframes chatBadgeIn{0%{opacity:0;transform:translateY(-4px) scale(.94)}100%{opacity:1;transform:translateY(0) scale(1)}}
      @keyframes chatActionsIn{0%{opacity:0;transform:translateY(-3px) scale(.96)}100%{opacity:1;transform:translateY(0) scale(1)}}
      #chatMessages[data-badge-repaint="1"] .chat-hard-badge,#chatMessages[data-badge-repaint="1"] .chat-hard-actions{transform:translateZ(.01px)!important;}
      #chatMessages[data-badge-repaint="2"] .chat-hard-badge,#chatMessages[data-badge-repaint="2"] .chat-hard-actions{transform:translateZ(.02px)!important;}
      .chat-hard-badge{display:inline-flex!important;align-items:center!important;gap:6px!important;width:fit-content!important;max-width:100%!important;box-sizing:border-box!important;margin:14px 0 0 0!important;padding:7px 12px!important;border-radius:999px!important;font-size:11px!important;font-weight:900!important;line-height:1.18!important;letter-spacing:.01em!important;white-space:normal!important;word-break:break-word!important;opacity:1!important;visibility:visible!important;position:relative!important;z-index:30!important;pointer-events:none!important;color:rgba(238,250,255,.86)!important;background:rgba(255,255,255,.135)!important;border:1px solid rgba(255,255,255,.24)!important;box-shadow:inset 0 1px 0 rgba(255,255,255,.25),0 8px 18px rgba(0,0,0,.10)!important;transform:translateZ(0)!important;will-change:transform,opacity!important;contain:paint!important;}
      .chat-row.user .chat-hard-badge{margin-left:auto!important;}
      .chat-hard-badge::before{content:'';display:inline-block;width:8px;height:8px;border-radius:999px;background:currentColor;opacity:.92;box-shadow:0 0 12px currentColor;flex:0 0 auto;}
      .chat-hard-badge[data-tone="cloud"]{color:#8cf7ff!important;border-color:rgba(132,221,255,.38)!important;background:rgba(33,197,255,.16)!important;}
      .chat-hard-badge[data-tone="gemini"]{color:#d6c8ff!important;border-color:rgba(173,145,255,.42)!important;background:rgba(126,87,255,.17)!important;}
      .chat-hard-badge[data-tone="vision"]{color:#ffd1fb!important;border-color:rgba(236,72,153,.38)!important;background:rgba(236,72,153,.15)!important;}
      .chat-hard-badge[data-tone="online"]{color:#9af8cc!important;border-color:rgba(22,190,121,.38)!important;background:rgba(22,190,121,.15)!important;}
      .chat-hard-badge[data-tone="attachment"]{color:#e7edff!important;border-color:rgba(181,190,255,.38)!important;background:rgba(148,163,255,.16)!important;}
      .chat-hard-badge[data-tone="error"]{color:#ffc1c1!important;border-color:rgba(255,91,91,.38)!important;background:rgba(255,91,91,.16)!important;}
      .chat-source-badge-row,.chat-message-actions:not(.chat-hard-actions){display:none!important;}
      .chat-bubble[data-badge-text]::before,.chat-response[data-badge-text]::before,.chat-bubble[data-badge-text]::after,.chat-response[data-badge-text]::after{display:none!important;content:none!important;}
      .chat-hard-actions{display:flex!important;align-items:center!important;gap:8px!important;min-height:30px!important;margin:8px 0 3px 8px!important;position:relative!important;z-index:31!important;transform:translateZ(0)!important;will-change:transform,opacity!important;}
      .chat-row.user .chat-hard-actions{justify-content:flex-end!important;margin-left:auto!important;margin-right:8px!important;}
      .chat-hard-actions .chat-action-btn{appearance:none!important;border-radius:999px!important;min-width:48px!important;min-height:29px!important;padding:0 12px!important;font-size:11px!important;font-weight:900!important;line-height:1!important;}
      .chat-hard-actions .retry{color:#d8caff!important;border:1px solid rgba(174,150,255,.36)!important;background:rgba(126,87,255,.20)!important;}
      .chat-hard-actions .copy{color:#9ff8d4!important;border:1px solid rgba(42,218,150,.34)!important;background:rgba(22,190,121,.18)!important;}
      @media (prefers-reduced-motion:reduce){.chat-row[data-entered="1"] .chat-bubble,.chat-row[data-entered="1"] .chat-response,.chat-row[data-entered="1"] .chat-hard-badge,.chat-row[data-entered="1"] .chat-hard-actions{animation:none!important;}}
    `;
    document.head.appendChild(style);
  }

  function purgeLegacyNodes(row) {
    row.querySelectorAll('.chat-source-badge-row,.chat-message-actions:not(.chat-hard-actions)').forEach((node) => node.remove());
    const bubble = row.querySelector('.chat-response,.chat-bubble');
    if (bubble) {
      bubble.removeAttribute('data-badge-text');
      bubble.removeAttribute('data-badge-tone');
      bubble.removeAttribute('data-badge-key');
    }
  }

  function nudgeRepaint() {
    const host = document.querySelector('#chatMessages');
    if (!host || repaintTimer) return;
    repaintTimer = window.setTimeout(() => {
      repaintTimer = 0;
      repaintFlip = !repaintFlip;
      host.dataset.badgeRepaint = repaintFlip ? '1' : '2';
      // 读一下布局，强制 Android WebView 把这一层重新合成。不要改滚动位置。
      void host.offsetHeight;
    }, 24);
  }

  function ensureEntrance(row, id) {
    if (!id || animatedIds.has(id)) return;
    animatedIds.add(id);
    row.dataset.entered = '1';
    setTimeout(() => {
      if (row?.dataset?.entered === '1') row.dataset.entered = 'done';
    }, 680);
  }

  function ensureRow(row, byId) {
    if (!row || row.id === 'typingRow') return false;
    const id = String(row.dataset.messageId || '');
    if (!id) return false;
    const message = byId.get(id);
    const bubble = row.querySelector('.chat-response,.chat-bubble');
    if (!bubble) return false;
    purgeLegacyNodes(row);
    ensureEntrance(row, id);

    let changed = false;
    const badge = badgeFor(row, message);
    const existingBadge = bubble.querySelector(':scope > .chat-hard-badge');
    row.querySelectorAll(':scope > .chat-hard-badge').forEach((node) => node.remove());
    if (badge?.text) {
      const key = `${badge.tone}|${badge.text}`;
      if (!existingBadge || existingBadge.dataset.key !== key) {
        existingBadge?.remove();
        bubble.insertAdjacentHTML('beforeend', `<div class="chat-hard-badge" data-tone="${escapeHtml(badge.tone)}" data-key="${escapeHtml(key)}">${escapeHtml(badge.text)}</div>`);
        changed = true;
      }
    } else if (existingBadge) {
      existingBadge.remove();
      changed = true;
    }

    const shouldHaveActions = message?.role === 'assistant' && message.id !== 'welcome';
    const existingActions = Array.from(row.children).find((child) => child.classList?.contains('chat-hard-actions'));
    if (shouldHaveActions) {
      if (!existingActions || existingActions.dataset.forMessage !== id) {
        existingActions?.remove();
        bubble.insertAdjacentHTML('afterend', `<div class="chat-hard-actions" data-for-message="${escapeHtml(id)}"><button class="chat-action-btn retry" type="button" data-chat-action="retry" data-message-id="${escapeHtml(id)}">重试</button><button class="chat-action-btn copy" type="button" data-chat-action="copy" data-message-id="${escapeHtml(id)}">复制</button></div>`);
        changed = true;
      }
    } else if (existingActions) {
      existingActions.remove();
      changed = true;
    }

    return changed;
  }

  function refresh() {
    const host = document.querySelector('#chatMessages');
    if (!host) return;
    const byId = messageMap();
    let changed = false;
    host.querySelectorAll('.chat-row[data-message-id]').forEach((row) => {
      if (ensureRow(row, byId)) changed = true;
    });
    nudgeRepaint();
    if (changed) setTimeout(nudgeRepaint, 90);
  }

  function flashButton(button, label) {
    if (!button) return;
    const old = button.textContent;
    button.textContent = label;
    button.disabled = true;
    window.setTimeout(() => { button.textContent = old; button.disabled = false; }, 1100);
  }

  function findMessageIndex(id) {
    const list = readMessages();
    const index = list.findIndex((message) => String(message.id) === String(id));
    return { list, index };
  }

  async function copyText(text) {
    const value = String(text || '').trim();
    if (!value) throw new Error('empty text');
    try {
      if (navigator.clipboard?.writeText && window.isSecureContext !== false) {
        await navigator.clipboard.writeText(value);
        return;
      }
    } catch {}
    const area = document.createElement('textarea');
    area.value = value;
    area.setAttribute('readonly', 'readonly');
    area.style.position = 'fixed';
    area.style.left = '-9999px';
    area.style.top = '0';
    document.body.appendChild(area);
    area.focus({ preventScroll: true });
    area.select();
    area.setSelectionRange(0, value.length);
    const ok = document.execCommand('copy');
    area.remove();
    if (!ok) throw new Error('copy failed');
  }

  async function handleCopy(event, button) {
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation?.();
    const { list, index } = findMessageIndex(button.dataset.messageId);
    const message = index >= 0 ? list[index] : null;
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
        input.focus?.({ preventScroll: true });
        flashButton(button, '已填入');
      } else {
        flashButton(button, '复制失败');
      }
    }
  }

  function handleRetry(event, button) {
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation?.();
    const { list, index } = findMessageIndex(button.dataset.messageId);
    if (index < 0) return flashButton(button, '未找到');
    const prevUser = list.slice(0, index).reverse().find((message) => message.role === 'user' && String(message.content || '').trim());
    if (!prevUser) return flashButton(button, '无上文');
    const input = document.querySelector('#aiInput');
    const form = document.querySelector('#chatForm');
    if (!input || !form) return flashButton(button, '不可重试');
    input.value = prevUser.content;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    flashButton(button, '重试中');
    window.ChatScrollStability?.pinBottom?.('retry-submit');
    if (form.requestSubmit) form.requestSubmit();
    else form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  }

  function installActionEvents() {
    if (document.body.dataset.chatHardActionBound === '1') return;
    document.body.dataset.chatHardActionBound = '1';
    document.addEventListener('click', (event) => {
      const button = event.target.closest('[data-chat-action]');
      if (!button) return;
      if (button.dataset.chatAction === 'copy') handleCopy(event, button);
      if (button.dataset.chatAction === 'retry') handleRetry(event, button);
    }, true);
  }

  function installObserver() {
    const host = document.querySelector('#chatMessages');
    if (!host || host.dataset.hardBadgeActionsObserver === '1') return;
    host.dataset.hardBadgeActionsObserver = '1';
    const observer = new MutationObserver(() => {
      requestAnimationFrame(refresh);
      setTimeout(refresh, 80);
      setTimeout(refresh, 280);
    });
    observer.observe(host, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'style', 'data-message-id'] });
  }

  function installRepaintHooks() {
    const host = document.querySelector('#chatMessages');
    if (host && host.dataset.hardBadgeRepaintHooks !== '1') {
      host.dataset.hardBadgeRepaintHooks = '1';
      ['scroll', 'touchend', 'pointerup'].forEach((eventName) => host.addEventListener(eventName, () => {
        refresh();
        nudgeRepaint();
      }, { passive: true }));
    }
    ['touchend', 'pointerup', 'visibilitychange', 'focus', 'resize'].forEach((eventName) => window.addEventListener(eventName, () => {
      refresh();
      nudgeRepaint();
    }, { passive: true }));
  }

  function boot() {
    installStyle();
    installActionEvents();
    installObserver();
    installRepaintHooks();
    refresh();
    setInterval(() => { refresh(); nudgeRepaint(); }, 600);
    window.addEventListener('ai-ledger-model-change', refresh);
    window.addEventListener('focus', refresh);
  }

  const oldRefresh = window.ChatSourceBadges?.refresh;
  window.ChatBadgeActionsHardener = { refresh, repaint: nudgeRepaint, version: '20260516-hardener-4' };
  if (window.ChatSourceBadges) {
    window.ChatSourceBadges.refresh = () => {
      try { oldRefresh?.(); } catch {}
      refresh();
    };
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();

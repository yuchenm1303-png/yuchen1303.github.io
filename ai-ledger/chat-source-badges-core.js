(() => {
  'use strict';

  const shared = window.AiLedgerChatShared;
  if (!shared) return;

  const STYLE_ID = 'chat-source-badges-core-style';
  const { SOURCE_LABELS, readMessages, readModelPreference, escapeHtml, pinChatBottom } = shared;

  function windowMessages() {
    return Array.isArray(window.chatMessages) ? window.chatMessages : [];
  }

  function allMessages() {
    const seen = new Set();
    const list = [];
    [...readMessages(), ...windowMessages()].filter(Boolean).forEach((message) => {
      if (!message?.id || seen.has(String(message.id))) return;
      seen.add(String(message.id));
      list.push(message);
    });
    return list;
  }

  function mergedMessages() {
    const map = new Map();
    allMessages().forEach((message) => {
      if (message?.id) map.set(String(message.id), message);
    });
    return map;
  }

  function selectedFallbackMessage(row) {
    const pref = String(readModelPreference?.() || 'auto').toLowerCase();
    const map = {
      kimi: { source: 'nvidia_chat', provider: 'NVIDIA NIM', model: 'moonshotai/kimi-k2.6', modelLabel: 'moonshotai/kimi-k2.6 · via NVIDIA NIM' },
      mistral: { source: 'nvidia_chat', provider: 'NVIDIA NIM', model: 'mistralai/mistral-medium-3.5-128b', modelLabel: 'Mistral Medium 3.5 128B · via NVIDIA NIM' },
      gemini: { source: 'gemini_chat', provider: 'Gemini', model: 'gemini-2.5-flash', modelLabel: 'Gemini 2.5 Flash' },
      workers: { source: 'workers_ai', provider: 'Cloudflare Workers AI', model: '@cf/meta/llama-3.1-8b-instruct', modelLabel: 'Workers AI' },
      auto: { source: 'cloud_ai', provider: 'Auto', model: 'auto', modelLabel: '自动模型池' },
    };
    const fallback = map[pref] || map.auto;
    return {
      id: row?.dataset?.messageId || '',
      role: row?.classList?.contains('user') ? 'user' : 'assistant',
      ...fallback,
      version: '',
      __fallbackBadge: true,
    };
  }

  function getMessage(row, byId) {
    const id = row?.dataset?.messageId;
    if (id && byId.has(String(id))) return byId.get(String(id));
    return selectedFallbackMessage(row);
  }

  function inferSource(message) {
    if (!message || message.role !== 'assistant') return null;
    const version = String(message.version || '');
    if (message.source) return message.source;
    if (/command-protocol|worker-command|cloud-command/i.test(version)) return 'command_protocol';
    if (/tavily/i.test(version)) return 'tavily_web_search';
    if (/open-meteo/i.test(version)) return 'weather_tool';
    if (/gemini/i.test(version)) return 'gemini_chat';
    if (/kimi|nvidia|mistral|qwen|deepseek/i.test(version)) return 'nvidia_chat';
    if (message.mobileCommand) return 'local_mobile';
    if (Array.isArray(message.records) && message.records.length) return 'local';
    if (message.id === 'welcome') return 'builtin_profile';
    return 'cloud_ai';
  }

  function nvidiaLabelFromMessage(message) {
    const text = `${message?.modelLabel || ''} ${message?.model || ''} ${message?.version || ''}`.toLowerCase();
    if (text.includes('kimi')) return 'Kimi 对话';
    if (text.includes('mistral')) return 'Mistral 对话';
    if (text.includes('qwen')) return 'Qwen 对话';
    if (text.includes('deepseek')) return 'DeepSeek 对话';
    return 'NVIDIA NIM';
  }

  function sourceMeta(source, message) {
    if (source === 'nvidia_chat') return { label: nvidiaLabelFromMessage(message), tone: 'cloud' };
    if (source === 'nvidia_vision' || source === 'nvidia_vision_fallback') return { label: nvidiaLabelFromMessage(message).replace('对话', '识图'), tone: 'vision' };
    if (SOURCE_LABELS[source]) return SOURCE_LABELS[source];
    if (/vision|image|attachment/i.test(source || '')) return { label: '识图', tone: 'vision' };
    if (/nvidia|nim|kimi|qwen|mistral|deepseek/i.test(source || '')) return { label: 'NVIDIA NIM', tone: 'cloud' };
    if (/gemini/i.test(source || '')) return { label: 'Gemini AI', tone: 'gemini' };
    if (/tavily|search/i.test(source || '')) return { label: '联网搜索', tone: 'online' };
    if (/weather/i.test(source || '')) return { label: '实时天气', tone: 'online' };
    if (/worker/i.test(source || '')) return { label: 'Workers AI', tone: 'cloud' };
    return { label: '云端 AI', tone: 'cloud' };
  }

  function compactVersion(version) {
    return String(version || '')
      .replace(/^ai-ledger-/i, '')
      .replace(/worker-/i, '')
      .replace(/orchestrator-/i, 'orch-')
      .replace(/attachment-gateway-/i, 'attach-')
      .replace(/command-protocol-/i, 'cmd-')
      .slice(0, 90);
  }

  function modelText(message) {
    const parts = [];
    if (message?.modelLabel) parts.push(message.modelLabel);
    else if (message?.model) parts.push(message.model);
    else if (message?.provider && message?.model) parts.push(`${message.provider} ${message.model}`);
    const version = compactVersion(message?.version);
    if (version && !parts.some((part) => version.includes(part))) parts.push(version);
    return parts.filter(Boolean).join(' · ');
  }

  function attachmentMeta(message) {
    const list = Array.isArray(message?.attachments) ? message.attachments : [];
    if (!list.length) return null;
    const imageCount = list.filter((item) => String(item.mimeType || '').startsWith('image/')).length;
    const pdfCount = list.filter((item) => /pdf/i.test(String(item.mimeType || ''))).length;
    const fileCount = list.length - imageCount - pdfCount;
    const labels = [];
    if (imageCount) labels.push(imageCount === 1 ? '本图' : `${imageCount}张图片`);
    if (pdfCount) labels.push(pdfCount === 1 ? 'PDF' : `${pdfCount}个PDF`);
    if (fileCount) labels.push(fileCount === 1 ? '文件' : `${fileCount}个文件`);
    const names = list.map((item) => item.name).filter(Boolean).slice(0, 2).join('、');
    return { label: `已附带 ${labels.join('+') || '附件'}`, detail: names };
  }

  function installStyle() {
    const old = document.getElementById(STYLE_ID);
    if (old) old.remove();
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      #chatMessages{scroll-padding-bottom:50px!important;padding-bottom:28px!important;}
      .chat-row,.chat-response,.chat-bubble{overflow:visible!important;}
      .chat-bubble[data-badge-text],.chat-response[data-badge-text]{position:relative!important;}
      .chat-bubble[data-badge-text]::before,.chat-response[data-badge-text]::before{display:none!important;content:none!important;}
      .chat-bubble[data-badge-text]::after,.chat-response[data-badge-text]::after{content:'● ' attr(data-badge-text);position:static!important;display:block;width:fit-content;max-width:100%;box-sizing:border-box;margin-top:14px;border-radius:999px;padding:7px 12px;font-size:11px;font-weight:900;letter-spacing:.01em;line-height:1.18;white-space:normal;word-break:break-word;z-index:12;opacity:1!important;visibility:visible!important;color:rgba(238,250,255,.82);background:linear-gradient(135deg,rgba(255,255,255,.22),rgba(255,255,255,.075));border:1px solid rgba(255,255,255,.30);box-shadow:inset 0 1px 0 rgba(255,255,255,.30),0 10px 22px rgba(0,0,0,.11);backdrop-filter:blur(14px) saturate(150%);-webkit-backdrop-filter:blur(14px) saturate(150%);}
      .chat-row.user .chat-bubble[data-badge-text]::after,.chat-row.user .chat-response[data-badge-text]::after{margin-left:auto;}
      .chat-bubble[data-badge-tone="cloud"]::after,.chat-response[data-badge-tone="cloud"]::after{color:#8cf7ff;background:linear-gradient(135deg,rgba(33,197,255,.22),rgba(88,130,255,.10));border-color:rgba(132,221,255,.38);}
      .chat-bubble[data-badge-tone="gemini"]::after,.chat-response[data-badge-tone="gemini"]::after{color:#d6c8ff;background:linear-gradient(135deg,rgba(126,87,255,.24),rgba(236,72,153,.10));border-color:rgba(173,145,255,.42);}
      .chat-bubble[data-badge-tone="vision"]::after,.chat-response[data-badge-tone="vision"]::after{color:#ffd1fb;background:linear-gradient(135deg,rgba(236,72,153,.20),rgba(126,87,255,.10));border-color:rgba(236,72,153,.38);}
      .chat-bubble[data-badge-tone="attachment"]::after,.chat-response[data-badge-tone="attachment"]::after{color:#e7edff;background:linear-gradient(135deg,rgba(148,163,255,.22),rgba(255,255,255,.08));border-color:rgba(181,190,255,.38);}
      .chat-bubble[data-badge-tone="online"]::after,.chat-response[data-badge-tone="online"]::after{color:#9af8cc;background:linear-gradient(135deg,rgba(22,190,121,.20),rgba(33,197,255,.08));border-color:rgba(22,190,121,.38);}
      .chat-bubble[data-badge-tone="utility"]::after,.chat-response[data-badge-tone="utility"]::after{color:#ffe69a;background:linear-gradient(135deg,rgba(240,180,50,.20),rgba(255,255,255,.08));border-color:rgba(240,180,50,.36);}
      .chat-bubble[data-badge-tone="cloud-fallback"]::after,.chat-response[data-badge-tone="cloud-fallback"]::after{color:#ffd79b;background:linear-gradient(135deg,rgba(255,189,91,.20),rgba(255,255,255,.075));border-color:rgba(255,189,91,.36);}
      .chat-bubble[data-badge-tone="cloud-rule"]::after,.chat-response[data-badge-tone="cloud-rule"]::after{color:#a7f3d0;background:linear-gradient(135deg,rgba(16,185,129,.18),rgba(255,255,255,.075));border-color:rgba(16,185,129,.32);}
      .chat-bubble[data-badge-tone="builtin"]::after,.chat-response[data-badge-tone="builtin"]::after{color:#e7ddff;background:linear-gradient(135deg,rgba(161,117,255,.18),rgba(255,255,255,.075));border-color:rgba(161,117,255,.34);}
      .chat-bubble[data-badge-tone="local"]::after,.chat-response[data-badge-tone="local"]::after{color:#d3dbe9;background:linear-gradient(135deg,rgba(148,163,184,.18),rgba(255,255,255,.075));border-color:rgba(148,163,184,.30);}
      .chat-bubble[data-badge-tone="mobile"]::after,.chat-response[data-badge-tone="mobile"]::after{color:#8df4ed;background:linear-gradient(135deg,rgba(11,143,139,.20),rgba(33,197,255,.08));border-color:rgba(11,143,139,.36);}
      .chat-bubble[data-badge-tone="error"]::after,.chat-response[data-badge-tone="error"]::after{color:#ffc1c1;background:linear-gradient(135deg,rgba(255,91,91,.22),rgba(236,72,153,.08));border-color:rgba(255,91,91,.38);}
      .chat-source-badge-row{display:none!important;}
      .chat-message-actions{display:flex;align-items:center;gap:8px;margin:8px 0 2px 4px;min-height:30px;position:relative;z-index:10;}
      .chat-row.user .chat-message-actions{justify-content:flex-end;margin-right:4px;}
      .chat-action-btn{appearance:none;border:1px solid rgba(255,255,255,.24);border-radius:999px;background:linear-gradient(135deg,rgba(255,255,255,.18),rgba(255,255,255,.06));color:rgba(235,243,255,.78);min-height:28px;padding:0 12px;font-size:11px;font-weight:850;letter-spacing:.01em;backdrop-filter:blur(12px) saturate(145%);-webkit-backdrop-filter:blur(12px) saturate(145%);box-shadow:inset 0 1px 0 rgba(255,255,255,.22),0 8px 18px rgba(0,0,0,.10);}
      .chat-action-btn:active{transform:scale(.96);}
      .chat-action-btn.retry{color:#c7b7ff;border-color:rgba(173,145,255,.32);}
      .chat-action-btn.copy{color:#9af8cc;border-color:rgba(22,190,121,.30);}
      body.assistant-compact .chat-bubble[data-badge-text]::after,body.assistant-compact .chat-response[data-badge-text]::after{font-size:10px;padding:6px 10px;margin-top:10px;}
      body.assistant-compact .chat-message-actions{margin-top:6px;gap:6px;}
      body.assistant-compact .chat-action-btn{min-height:25px;padding:0 10px;font-size:10px;}
    `;
    document.head.appendChild(style);
  }

  function badgeParts(message) {
    if (message.role === 'user') {
      const att = attachmentMeta(message);
      if (!att) return null;
      const detail = att.detail ? ` · ${att.detail}` : '';
      return { text: `${att.label}${detail}`, tone: 'attachment', key: 'user-attachment' };
    }
    const source = inferSource(message);
    const meta = sourceMeta(source, message);
    const detail = modelText(message);
    const detailText = detail ? ` · ${detail}` : '';
    const key = `${source || 'cloud_ai'}|${message.modelLabel || message.model || ''}|${message.version || ''}|${message.__fallbackBadge ? 'fallback' : 'real'}`;
    return { text: `${meta.label}${detailText}`, tone: meta.tone, key };
  }

  function clearRowAttrs(row) {
    row.removeAttribute('data-badge-text');
    row.removeAttribute('data-badge-tone');
    row.removeAttribute('data-badge-key');
    row.style.paddingBottom = '';
  }

  function ensureBadge(row, byId) {
    if (!row || row.id === 'typingRow') return false;
    clearRowAttrs(row);
    row.querySelectorAll(':scope .chat-source-badge-row').forEach((node) => node.remove());
    const bubble = row.querySelector('.chat-response,.chat-bubble');
    if (!bubble) return false;
    const message = getMessage(row, byId);
    const parts = badgeParts(message);
    if (!parts?.text) {
      const had = bubble.hasAttribute('data-badge-text');
      bubble.removeAttribute('data-badge-text');
      bubble.removeAttribute('data-badge-tone');
      bubble.removeAttribute('data-badge-key');
      return had;
    }
    const currentKey = bubble.dataset.badgeKey || '';
    const shouldKeepReal = currentKey && !currentKey.includes('fallback') && parts.key.includes('fallback');
    if (shouldKeepReal) return false;
    const changed = bubble.dataset.badgeText !== parts.text || bubble.dataset.badgeTone !== parts.tone || bubble.dataset.badgeKey !== parts.key;
    if (!changed) return false;
    bubble.dataset.badgeText = parts.text;
    bubble.dataset.badgeTone = parts.tone || 'cloud';
    bubble.dataset.badgeKey = parts.key;
    row.dataset.sourceBadgeReady = 'ready';
    return true;
  }

  function ensureActions(row, byId) {
    if (!row || row.id === 'typingRow' || row.classList.contains('user')) return false;
    const id = row.dataset.messageId || '';
    const message = id ? byId.get(String(id)) : null;
    if (!message || message.role !== 'assistant' || message.id === 'welcome') return false;
    const bubble = row.querySelector('.chat-response,.chat-bubble');
    if (!bubble) return false;
    const existing = row.querySelector(':scope .chat-message-actions');
    if (existing && existing.dataset.forMessage === id) return false;
    existing?.remove();
    bubble.insertAdjacentHTML('afterend', `<div class="chat-message-actions" data-for-message="${escapeHtml(id)}"><button class="chat-action-btn retry" type="button" data-chat-action="retry" data-message-id="${escapeHtml(id)}">重试</button><button class="chat-action-btn copy" type="button" data-chat-action="copy" data-message-id="${escapeHtml(id)}">复制</button></div>`);
    return true;
  }

  function addBadges() {
    const byId = mergedMessages();
    let changed = false;
    document.querySelectorAll('.chat-row').forEach((row) => {
      if (ensureBadge(row, byId)) changed = true;
      if (ensureActions(row, byId)) changed = true;
    });
    if (changed) pinChatBottom('badge-actions');
  }

  function findMessageIndex(id) {
    const list = allMessages();
    const index = list.findIndex((message) => String(message.id) === String(id));
    return { list, index };
  }

  function cleanCopyText(message) {
    return String(message?.content || '').trim();
  }

  async function copyText(text) {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return;
    }
    const area = document.createElement('textarea');
    area.value = text;
    area.style.position = 'fixed';
    area.style.left = '-9999px';
    document.body.appendChild(area);
    area.focus();
    area.select();
    document.execCommand('copy');
    area.remove();
  }

  function flashButton(button, label) {
    if (!button) return;
    const old = button.textContent;
    button.textContent = label;
    window.setTimeout(() => { button.textContent = old; }, 1200);
  }

  async function handleCopy(button, id) {
    const { list, index } = findMessageIndex(id);
    const message = index >= 0 ? list[index] : null;
    const text = cleanCopyText(message);
    if (!text) return flashButton(button, '无内容');
    try {
      await copyText(text);
      flashButton(button, '已复制');
    } catch {
      flashButton(button, '复制失败');
    }
  }

  function handleRetry(button, id) {
    const { list, index } = findMessageIndex(id);
    if (index < 0) return flashButton(button, '未找到');
    const prevUser = list.slice(0, index).reverse().find((message) => message.role === 'user' && String(message.content || '').trim());
    if (!prevUser) return flashButton(button, '无上文');
    const input = document.querySelector('#aiInput');
    const form = document.querySelector('#chatForm');
    if (!input || !form) return flashButton(button, '不可重试');
    input.value = prevUser.content;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    flashButton(button, '重试中');
    if (form.requestSubmit) form.requestSubmit();
    else form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  }

  function installActionEvents() {
    if (document.body.dataset.chatMessageActionsBound === '1') return;
    document.body.dataset.chatMessageActionsBound = '1';
    document.addEventListener('click', (event) => {
      const button = event.target.closest('[data-chat-action]');
      if (!button) return;
      const action = button.dataset.chatAction;
      const id = button.dataset.messageId;
      if (action === 'copy') handleCopy(button, id);
      if (action === 'retry') handleRetry(button, id);
    });
  }

  function installObserver() {
    const target = document.querySelector('#chatMessages');
    if (!target || target.dataset.sourceBadgeObserver === 'ready') return;
    target.dataset.sourceBadgeObserver = 'ready';
    const observer = new MutationObserver(() => {
      addBadges();
      requestAnimationFrame(addBadges);
      setTimeout(addBadges, 80);
      setTimeout(addBadges, 260);
    });
    observer.observe(target, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'data-message-id'] });
    addBadges();
  }

  function boot() {
    installStyle();
    installActionEvents();
    installObserver();
    window.setInterval(addBadges, 300);
    window.addEventListener('ai-ledger-model-change', addBadges);
  }

  window.ChatSourceBadges = { refresh: addBadges, labels: SOURCE_LABELS, pinBottom: pinChatBottom };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();

(() => {
  'use strict';

  const shared = window.AiLedgerChatShared;
  if (!shared) return;

  const STYLE_ID = 'chat-source-badges-core-style';
  const { SOURCE_LABELS, readMessages, readModelPreference, escapeHtml, pinChatBottom } = shared;

  function windowMessages() {
    return Array.isArray(window.chatMessages) ? window.chatMessages : [];
  }

  function mergedMessages() {
    const map = new Map();
    [...readMessages(), ...windowMessages()].filter(Boolean).forEach((message) => {
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
      #chatMessages{scroll-padding-bottom:44px!important;padding-bottom:24px!important;}
      .chat-row,.chat-response,.chat-bubble{overflow:visible!important;}
      .chat-bubble[data-badge-text]{position:relative!important;padding-bottom:48px!important;}
      .chat-bubble[data-badge-text]::after{content:attr(data-badge-text);position:absolute;left:28px;right:auto;bottom:12px;display:inline-flex;max-width:calc(100% - 46px);box-sizing:border-box;border-radius:999px;padding:6px 10px 6px 22px;font-size:11px;font-weight:850;line-height:1.12;white-space:normal;word-break:break-word;z-index:12;opacity:1!important;visibility:visible!important;color:rgba(238,250,255,.78);background:rgba(255,255,255,.18);border:1px solid rgba(255,255,255,.28);box-shadow:inset 0 1px 0 rgba(255,255,255,.18),0 8px 20px rgba(0,0,0,.10);backdrop-filter:blur(12px);-webkit-backdrop-filter:blur(12px);}
      .chat-bubble[data-badge-text]::before{content:'';position:absolute;left:39px;bottom:22px;width:6px;height:6px;border-radius:999px;z-index:13;background:currentColor;opacity:.88;color:rgba(238,250,255,.78);}
      .chat-row.user .chat-bubble[data-badge-text]::after{left:auto;right:28px;}
      .chat-row.user .chat-bubble[data-badge-text]::before{left:auto;right:40px;}
      .chat-bubble[data-badge-tone="cloud"]::after{color:#83f7ff;background:rgba(33,197,255,.14);border-color:rgba(33,197,255,.28);}
      .chat-bubble[data-badge-tone="cloud"]::before{color:#83f7ff;}
      .chat-bubble[data-badge-tone="gemini"]::after{color:#c7b7ff;background:rgba(126,87,255,.18);border-color:rgba(126,87,255,.35);}
      .chat-bubble[data-badge-tone="gemini"]::before{color:#c7b7ff;}
      .chat-bubble[data-badge-tone="vision"]::after{color:#ffd1fb;background:rgba(236,72,153,.16);border-color:rgba(236,72,153,.34);}
      .chat-bubble[data-badge-tone="vision"]::before{color:#ffd1fb;}
      .chat-bubble[data-badge-tone="attachment"]::after{color:#e5edff;background:rgba(148,163,255,.18);border-color:rgba(181,190,255,.34);}
      .chat-bubble[data-badge-tone="attachment"]::before{color:#e5edff;}
      .chat-bubble[data-badge-tone="online"]::after{color:#8ff7c4;background:rgba(22,190,121,.16);border-color:rgba(22,190,121,.34);}
      .chat-bubble[data-badge-tone="online"]::before{color:#8ff7c4;}
      .chat-bubble[data-badge-tone="utility"]::after{color:#ffe38f;background:rgba(240,180,50,.16);border-color:rgba(240,180,50,.32);}
      .chat-bubble[data-badge-tone="utility"]::before{color:#ffe38f;}
      .chat-bubble[data-badge-tone="cloud-fallback"]::after{color:#ffd28a;background:rgba(255,189,91,.14);border-color:rgba(255,189,91,.32);}
      .chat-bubble[data-badge-tone="cloud-fallback"]::before{color:#ffd28a;}
      .chat-bubble[data-badge-tone="cloud-rule"]::after{color:#a7f3d0;background:rgba(16,185,129,.14);border-color:rgba(16,185,129,.28);}
      .chat-bubble[data-badge-tone="cloud-rule"]::before{color:#a7f3d0;}
      .chat-bubble[data-badge-tone="builtin"]::after{color:#e7ddff;background:rgba(161,117,255,.16);border-color:rgba(161,117,255,.30);}
      .chat-bubble[data-badge-tone="builtin"]::before{color:#e7ddff;}
      .chat-bubble[data-badge-tone="local"]::after{color:#cbd5e1;background:rgba(148,163,184,.16);border-color:rgba(148,163,184,.28);}
      .chat-bubble[data-badge-tone="local"]::before{color:#cbd5e1;}
      .chat-bubble[data-badge-tone="mobile"]::after{color:#86ece2;background:rgba(11,143,139,.18);border-color:rgba(11,143,139,.32);}
      .chat-bubble[data-badge-tone="mobile"]::before{color:#86ece2;}
      .chat-bubble[data-badge-tone="error"]::after{color:#ffb4b4;background:rgba(255,91,91,.15);border-color:rgba(255,91,91,.30);}
      .chat-bubble[data-badge-tone="error"]::before{color:#ffb4b4;}
      .chat-source-badge-row{display:none!important;}
      body.assistant-compact .chat-bubble[data-badge-text]{padding-bottom:42px!important;}
      body.assistant-compact .chat-bubble[data-badge-text]::after{font-size:10px;padding:5px 9px 5px 20px;}
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

  function addBadges() {
    const byId = mergedMessages();
    let changed = false;
    document.querySelectorAll('.chat-row').forEach((row) => {
      if (ensureBadge(row, byId)) changed = true;
    });
    if (changed) pinChatBottom('badge-bubble');
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
    installObserver();
    window.setInterval(addBadges, 300);
    window.addEventListener('ai-ledger-model-change', addBadges);
  }

  window.ChatSourceBadges = { refresh: addBadges, labels: SOURCE_LABELS, pinBottom: pinChatBottom };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();

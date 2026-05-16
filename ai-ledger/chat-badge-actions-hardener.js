(() => {
  'use strict';

  const shared = window.AiLedgerChatShared;
  if (!shared || window.__chatBadgeActionsHardenerInstalled) return;
  window.__chatBadgeActionsHardenerInstalled = true;

  const STYLE_ID = 'chat-badge-actions-hardener-style';
  const CHAT_KEY = shared.CHAT_KEY || 'ai-ledger-chat-v2';
  const { SOURCE_LABELS = {}, escapeHtml = (v) => String(v || '') } = shared;

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
      .chat-hard-badge{display:inline-flex!important;align-items:center!important;gap:6px!important;width:fit-content!important;max-width:min(92%,620px)!important;box-sizing:border-box!important;margin:9px 0 2px 8px!important;padding:7px 12px!important;border-radius:999px!important;font-size:11px!important;font-weight:900!important;line-height:1.18!important;letter-spacing:.01em!important;white-space:normal!important;word-break:break-word!important;opacity:1!important;visibility:visible!important;position:relative!important;z-index:30!important;pointer-events:none!important;color:rgba(238,250,255,.84)!important;background:linear-gradient(135deg,rgba(255,255,255,.22),rgba(255,255,255,.075))!important;border:1px solid rgba(255,255,255,.30)!important;box-shadow:inset 0 1px 0 rgba(255,255,255,.30),0 10px 22px rgba(0,0,0,.11)!important;backdrop-filter:blur(14px) saturate(150%)!important;-webkit-backdrop-filter:blur(14px) saturate(150%)!important;}
      .chat-row.user .chat-hard-badge{margin-left:auto!important;margin-right:8px!important;}
      .chat-hard-badge::before{content:'';display:inline-block;width:8px;height:8px;border-radius:999px;background:currentColor;opacity:.92;box-shadow:0 0 12px currentColor;flex:0 0 auto;}
      .chat-hard-badge[data-tone="cloud"]{color:#8cf7ff!important;border-color:rgba(132,221,255,.38)!important;background:linear-gradient(135deg,rgba(33,197,255,.22),rgba(88,130,255,.10))!important;}
      .chat-hard-badge[data-tone="gemini"]{color:#d6c8ff!important;border-color:rgba(173,145,255,.42)!important;background:linear-gradient(135deg,rgba(126,87,255,.24),rgba(236,72,153,.10))!important;}
      .chat-hard-badge[data-tone="vision"]{color:#ffd1fb!important;border-color:rgba(236,72,153,.38)!important;background:linear-gradient(135deg,rgba(236,72,153,.20),rgba(126,87,255,.10))!important;}
      .chat-hard-badge[data-tone="online"]{color:#9af8cc!important;border-color:rgba(22,190,121,.38)!important;background:linear-gradient(135deg,rgba(22,190,121,.20),rgba(33,197,255,.08))!important;}
      .chat-hard-badge[data-tone="attachment"]{color:#e7edff!important;border-color:rgba(181,190,255,.38)!important;background:linear-gradient(135deg,rgba(148,163,255,.22),rgba(255,255,255,.08))!important;}
      .chat-hard-badge[data-tone="error"]{color:#ffc1c1!important;border-color:rgba(255,91,91,.38)!important;background:linear-gradient(135deg,rgba(255,91,91,.22),rgba(236,72,153,.08))!important;}
      .chat-hard-actions{display:flex!important;align-items:center!important;gap:8px!important;min-height:30px!important;margin:8px 0 3px 8px!important;position:relative!important;z-index:31!important;}
      .chat-row.user .chat-hard-actions{justify-content:flex-end!important;margin-left:auto!important;margin-right:8px!important;}
      .chat-hard-actions .chat-action-btn{appearance:none!important;border-radius:999px!important;min-width:48px!important;min-height:29px!important;padding:0 12px!important;font-size:11px!important;font-weight:900!important;line-height:1!important;backdrop-filter:blur(14px) saturate(155%)!important;-webkit-backdrop-filter:blur(14px) saturate(155%)!important;}
      .chat-hard-actions .retry{color:#d8caff!important;border:1px solid rgba(174,150,255,.36)!important;background:linear-gradient(135deg,rgba(126,87,255,.22),rgba(255,255,255,.07))!important;}
      .chat-hard-actions .copy{color:#9ff8d4!important;border:1px solid rgba(42,218,150,.34)!important;background:linear-gradient(135deg,rgba(22,190,121,.20),rgba(255,255,255,.07))!important;}
    `;
    document.head.appendChild(style);
  }

  function removeOldHardNodes(row) {
    Array.from(row.children).forEach((child) => {
      if (child.classList?.contains('chat-hard-badge') || child.classList?.contains('chat-hard-actions')) child.remove();
    });
  }

  function ensureRow(row, byId) {
    if (!row || row.id === 'typingRow') return false;
    const id = String(row.dataset.messageId || '');
    if (!id) return false;
    const message = byId.get(id);
    const bubble = row.querySelector('.chat-response,.chat-bubble');
    if (!bubble) return false;

    let changed = false;
    const badge = badgeFor(row, message);
    const existingBadge = Array.from(row.children).find((child) => child.classList?.contains('chat-hard-badge'));
    if (badge?.text) {
      const key = `${badge.tone}|${badge.text}`;
      if (!existingBadge || existingBadge.dataset.key !== key) {
        existingBadge?.remove();
        bubble.insertAdjacentHTML('afterend', `<div class="chat-hard-badge" data-tone="${escapeHtml(badge.tone)}" data-key="${escapeHtml(key)}">${escapeHtml(badge.text)}</div>`);
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
        const anchor = Array.from(row.children).find((child) => child.classList?.contains('chat-hard-badge')) || bubble;
        anchor.insertAdjacentHTML('afterend', `<div class="chat-hard-actions" data-for-message="${escapeHtml(id)}"><button class="chat-action-btn retry" type="button" data-chat-action="retry" data-message-id="${escapeHtml(id)}">重试</button><button class="chat-action-btn copy" type="button" data-chat-action="copy" data-message-id="${escapeHtml(id)}">复制</button></div>`);
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
    if (changed) window.ChatScrollStability?.pinBottom?.('hard-badge-actions');
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
    observer.observe(host, { childList: true, subtree: true });
  }

  function boot() {
    installStyle();
    installObserver();
    refresh();
    setInterval(refresh, 500);
    window.addEventListener('ai-ledger-model-change', refresh);
    window.addEventListener('focus', refresh);
  }

  const oldRefresh = window.ChatSourceBadges?.refresh;
  window.ChatBadgeActionsHardener = { refresh, version: '20260516-hardener-1' };
  if (window.ChatSourceBadges) {
    window.ChatSourceBadges.refresh = () => {
      try { oldRefresh?.(); } catch {}
      refresh();
    };
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();

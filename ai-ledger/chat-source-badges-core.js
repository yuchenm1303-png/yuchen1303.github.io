(() => {
  'use strict';

  const shared = window.AiLedgerChatShared;
  if (!shared) return;

  const STYLE_ID = 'chat-source-badges-core-style';
  const { SOURCE_LABELS, readMessages, escapeHtml, pinChatBottom } = shared;

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
    if (document.getElementById(STYLE_ID)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      #chatMessages{scroll-padding-bottom:28px!important;padding-bottom:12px!important;}
      .chat-row,.chat-response,.chat-bubble{overflow:visible!important;}
      .chat-source-badge-row{display:flex;justify-content:flex-start;margin:8px 0 2px 4px;gap:6px;flex-wrap:wrap;min-height:22px;position:relative;z-index:3;}
      .chat-row.user .chat-source-badge-row{justify-content:flex-end;margin:7px 4px 2px 0;}
      .chat-source-badge{display:inline-flex;align-items:center;gap:5px;border-radius:999px;padding:5px 9px;font-size:11px;font-weight:800;line-height:1.12;background:rgba(255,255,255,.18);border:1px solid rgba(255,255,255,.28);color:rgba(238,250,255,.78);backdrop-filter:blur(12px);-webkit-backdrop-filter:blur(12px);max-width:100%;word-break:break-word;box-sizing:border-box;}
      .chat-source-badge::before{content:"";width:6px;height:6px;min-width:6px;border-radius:999px;background:currentColor;opacity:.85;}
      .chat-source-badge.cloud{color:#83f7ff;background:rgba(33,197,255,.14);border-color:rgba(33,197,255,.28);}
      .chat-source-badge.gemini{color:#c7b7ff;background:rgba(126,87,255,.18);border-color:rgba(126,87,255,.35);}
      .chat-source-badge.vision{color:#ffd1fb;background:rgba(236,72,153,.16);border-color:rgba(236,72,153,.34);}
      .chat-source-badge.attachment{color:#e5edff;background:rgba(148,163,255,.18);border-color:rgba(181,190,255,.34);}
      .chat-source-badge.online{color:#8ff7c4;background:rgba(22,190,121,.16);border-color:rgba(22,190,121,.34);}
      .chat-source-badge.utility{color:#ffe38f;background:rgba(240,180,50,.16);border-color:rgba(240,180,50,.32);}
      .chat-source-badge.cloud-fallback{color:#ffd28a;background:rgba(255,189,91,.14);border-color:rgba(255,189,91,.32);}
      .chat-source-badge.cloud-rule{color:#a7f3d0;background:rgba(16,185,129,.14);border-color:rgba(16,185,129,.28);}
      .chat-source-badge.builtin{color:#e7ddff;background:rgba(161,117,255,.16);border-color:rgba(161,117,255,.30);}
      .chat-source-badge.local{color:#cbd5e1;background:rgba(148,163,184,.16);border-color:rgba(148,163,184,.28);}
      .chat-source-badge.mobile{color:#86ece2;background:rgba(11,143,139,.18);border-color:rgba(11,143,139,.32);}
      .chat-source-badge.error{color:#ffb4b4;background:rgba(255,91,91,.15);border-color:rgba(255,91,91,.30);}
      body.assistant-compact .chat-source-badge-row{margin-top:5px;}
      body.assistant-compact .chat-source-badge{font-size:10px;padding:4px 8px;}
    `;
    document.head.appendChild(style);
  }

  function removeDuplicateBadges(row) {
    const badges = row.querySelectorAll(':scope .chat-source-badge-row');
    badges.forEach((badge, index) => { if (index > 0) badge.remove(); });
  }

  function addBadges() {
    let inserted = false;
    const messages = readMessages();
    const byId = new Map(messages.map((message) => [String(message.id), message]));
    document.querySelectorAll('.chat-row[data-message-id]').forEach((row) => {
      const id = row.dataset.messageId;
      if (!id) return;
      removeDuplicateBadges(row);
      if (row.querySelector(':scope .chat-source-badge-row')) {
        row.dataset.sourceBadgeReady = 'ready';
        return;
      }
      const message = byId.get(String(id));
      if (!message) return;
      const response = row.querySelector('.chat-response,.chat-bubble');
      if (!response) return;

      if (message.role === 'user') {
        const att = attachmentMeta(message);
        if (att) {
          const detail = att.detail ? ` · ${escapeHtml(att.detail)}` : '';
          response.insertAdjacentHTML('beforeend', `<div class="chat-source-badge-row"><span class="chat-source-badge attachment">${escapeHtml(att.label)}${detail}</span></div>`);
          inserted = true;
        }
        row.dataset.sourceBadgeReady = 'ready';
        return;
      }

      if (message.role === 'assistant') {
        const source = inferSource(message);
        const meta = sourceMeta(source, message);
        const detail = modelText(message);
        const detailText = detail ? ` · ${escapeHtml(detail)}` : '';
        response.insertAdjacentHTML('beforeend', `<div class="chat-source-badge-row"><span class="chat-source-badge ${escapeHtml(meta.tone)}">${escapeHtml(meta.label)}${detailText}</span></div>`);
        row.dataset.sourceBadgeReady = 'ready';
        inserted = true;
      }
    });
    if (inserted) pinChatBottom('badge-insert');
  }

  function installObserver() {
    const target = document.querySelector('#chatMessages');
    if (!target || target.dataset.sourceBadgeObserver === 'ready') return;
    target.dataset.sourceBadgeObserver = 'ready';
    const observer = new MutationObserver(() => {
      addBadges();
      pinChatBottom('badge-mutation');
    });
    observer.observe(target, { childList: true, subtree: true });
    addBadges();
  }

  function boot() {
    installStyle();
    installObserver();
    window.setInterval(addBadges, 900);
  }

  window.ChatSourceBadges = { refresh: addBadges, labels: SOURCE_LABELS, pinBottom: pinChatBottom };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();

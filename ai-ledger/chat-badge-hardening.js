(() => {
  'use strict';

  const CHAT_KEY = 'ai-ledger-chat-v2';
  const PREF_KEY = 'ai-ledger-model-preference-v1';
  const STYLE_ID = 'chat-badge-hardening-style';

  function safeJson(value, fallback) {
    try { return JSON.parse(value); } catch { return fallback; }
  }

  function escapeHtml(value) {
    return String(value || '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function readMessages() {
    const fromWindow = Array.isArray(window.chatMessages) ? window.chatMessages : [];
    const fromStorage = safeJson(localStorage.getItem(CHAT_KEY) || '[]', []);
    const merged = [...fromStorage, ...fromWindow].filter(Boolean);
    const map = new Map();
    merged.forEach((item) => {
      if (item?.id) map.set(String(item.id), item);
    });
    return map;
  }

  function readModelPreference() {
    const parsed = safeJson(localStorage.getItem(PREF_KEY) || '{}', {});
    return String(parsed.model || 'auto').toLowerCase();
  }

  function selectedModelFallback() {
    const model = readModelPreference();
    if (model === 'kimi') return { source: 'nvidia_chat', modelLabel: 'moonshotai/kimi-k2.6 · via NVIDIA NIM' };
    if (model === 'mistral') return { source: 'nvidia_chat', modelLabel: 'Mistral Medium 3.5 128B · via NVIDIA NIM' };
    if (model === 'gemini') return { source: 'gemini_chat', modelLabel: 'Gemini 2.5 Flash' };
    if (model === 'workers') return { source: 'workers_ai', modelLabel: 'Workers AI' };
    return { source: 'cloud_ai', modelLabel: '自动模型池' };
  }

  function sourceLabel(source, modelLabel = '', version = '') {
    const text = `${source} ${modelLabel} ${version}`.toLowerCase();
    if (source === 'selected_model_failed') return { label: '所选模型失败', tone: 'error' };
    if (text.includes('weather')) return { label: '实时天气', tone: 'online' };
    if (text.includes('tavily') || text.includes('search')) return { label: '联网总结', tone: 'online' };
    if (text.includes('vision') || text.includes('识图') || text.includes('attachment')) {
      if (text.includes('kimi')) return { label: 'Kimi 识图', tone: 'vision' };
      if (text.includes('mistral')) return { label: 'Mistral 识图', tone: 'vision' };
      if (text.includes('gemini')) return { label: 'Gemini 识图', tone: 'vision' };
      if (text.includes('worker')) return { label: 'Workers AI 识图', tone: 'vision' };
      return { label: '识图', tone: 'vision' };
    }
    if (text.includes('kimi')) return { label: 'Kimi 对话', tone: 'cloud' };
    if (text.includes('mistral')) return { label: 'Mistral 对话', tone: 'cloud' };
    if (text.includes('gemini')) return { label: 'Gemini 对话', tone: 'gemini' };
    if (text.includes('worker')) return { label: 'Workers AI', tone: 'cloud-fallback' };
    if (text.includes('local') || text.includes('builtin')) return { label: '内置回复', tone: 'builtin' };
    return { label: '云端 AI', tone: 'cloud' };
  }

  function compactVersion(version) {
    return String(version || '')
      .replace(/^ai-ledger-/i, '')
      .replace(/orchestrator-/i, 'orch-')
      .replace(/attachment-gateway-/i, 'attach-')
      .replace(/command-protocol-/i, 'cmd-')
      .slice(0, 72);
  }

  function getMessageForRow(row, messages) {
    const id = row?.dataset?.messageId;
    if (id && messages.has(String(id))) return messages.get(String(id));
    const fallback = selectedModelFallback();
    return {
      id: id || '',
      role: row?.classList?.contains('user') ? 'user' : 'assistant',
      source: row?.dataset?.source || fallback.source,
      modelLabel: fallback.modelLabel,
      version: '',
    };
  }

  function hasOwnBadge(row) {
    return Boolean(row && row.querySelector('.chat-source-badge-row'));
  }

  function removeExtraBadges(row) {
    const badges = Array.from(row.querySelectorAll('.chat-source-badge-row'));
    badges.slice(1).forEach((node) => node.remove());
  }

  function buildAssistantBadge(message) {
    const meta = sourceLabel(message.source || '', message.modelLabel || message.model || '', message.version || '');
    const details = [message.modelLabel || message.model || '', compactVersion(message.version)]
      .filter(Boolean)
      .filter((value, index, arr) => arr.indexOf(value) === index)
      .join(' · ');
    return `<div class="chat-source-badge-row" data-badge-hardening="1"><span class="chat-source-badge ${escapeHtml(meta.tone)}">${escapeHtml(meta.label)}${details ? ` · ${escapeHtml(details)}` : ''}</span></div>`;
  }

  function buildUserAttachmentBadge(message) {
    const list = Array.isArray(message?.attachments) ? message.attachments : [];
    if (!list.length) return '';
    const imageCount = list.filter((item) => String(item.mimeType || '').startsWith('image/')).length;
    const names = list.map((item) => item.name).filter(Boolean).slice(0, 2).join('、');
    const label = imageCount ? (imageCount === 1 ? '已附带 本图' : `已附带 ${imageCount}张图片`) : `已附带 ${list.length}个附件`;
    return `<div class="chat-source-badge-row" data-badge-hardening="1"><span class="chat-source-badge attachment">${escapeHtml(label)}${names ? ` · ${escapeHtml(names)}` : ''}</span></div>`;
  }

  function ensureBadge(row, messages) {
    if (!row || row.id === 'typingRow') return false;
    removeExtraBadges(row);
    if (hasOwnBadge(row)) return false;
    const bubble = row.querySelector('.chat-response, .chat-bubble');
    if (!bubble) return false;
    const message = getMessageForRow(row, messages);
    let html = '';
    if (row.classList.contains('user')) html = buildUserAttachmentBadge(message);
    else html = buildAssistantBadge(message);
    if (!html) return false;
    bubble.insertAdjacentHTML('beforeend', html);
    return true;
  }

  function pinBottom() {
    const host = document.querySelector('#chatMessages');
    if (!host) return;
    const run = () => { host.scrollTop = host.scrollHeight + 120; };
    requestAnimationFrame(run);
    setTimeout(run, 80);
    setTimeout(run, 240);
  }

  function hardenBadges() {
    const host = document.querySelector('#chatMessages');
    if (!host) return;
    const messages = readMessages();
    let changed = false;
    host.querySelectorAll('.chat-row').forEach((row) => {
      if (ensureBadge(row, messages)) changed = true;
    });
    if (changed) pinBottom();
  }

  function installStyle() {
    if (document.getElementById(STYLE_ID)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      #chatMessages{padding-bottom:18px!important;scroll-padding-bottom:42px!important;overflow-y:auto!important;}
      .chat-row,.chat-bubble,.chat-response{overflow:visible!important;}
      .chat-source-badge-row{display:flex!important;min-height:24px!important;margin-top:8px!important;margin-bottom:3px!important;position:relative!important;z-index:8!important;}
      .chat-source-badge{box-sizing:border-box!important;max-width:100%!important;white-space:normal!important;line-height:1.15!important;}
    `;
    document.head.appendChild(style);
  }

  function boot() {
    installStyle();
    hardenBadges();
    const host = document.querySelector('#chatMessages');
    if (host && !host.dataset.badgeHardeningObserver) {
      host.dataset.badgeHardeningObserver = '1';
      new MutationObserver(() => {
        setTimeout(hardenBadges, 0);
        setTimeout(hardenBadges, 120);
      }).observe(host, { childList: true, subtree: true });
    }
    setInterval(hardenBadges, 500);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();

  window.ChatBadgeHardening = { refresh: hardenBadges, pinBottom };
})();

(() => {
  'use strict';

  const CHAT_KEY = 'ai-ledger-chat-v2';
  const STYLE_ID = 'chat-source-badges-style';
  const PREF_KEY = 'ai-ledger-model-preference-v1';
  const FETCH_PATCH_FLAG = '__aiLedgerModelPreferenceFetchPatched';

  const MODELS = [
    { id: 'auto', label: '自动', short: '自动', hint: '按额度和可用性自动切换' },
    { id: 'kimi', label: 'Kimi K2.6', short: 'Kimi', hint: '只使用 Kimi，不自动切到其他模型' },
    { id: 'mistral', label: 'Mistral Medium 3.5', short: 'Mistral', hint: '只使用 Mistral，不自动切到其他模型' },
    { id: 'gemini', label: 'Gemini 2.5 Flash', short: 'Gemini', hint: '只使用 Gemini，不自动切到其他模型' },
    { id: 'workers', label: 'Workers AI', short: 'Workers', hint: '只使用 Workers AI 兜底模型' },
  ];

  const SOURCE_LABELS = {
    builtin_profile: { label: '内置回复', tone: 'builtin' },
    local: { label: '本地规则', tone: 'local' },
    local_ledger: { label: '本地记账', tone: 'local' },
    local_mobile: { label: '手机动作', tone: 'mobile' },
    cloud_ai: { label: '云端 AI', tone: 'cloud' },
    nvidia_chat: { label: 'NVIDIA NIM', tone: 'cloud' },
    gemini_chat: { label: 'Gemini 对话', tone: 'gemini' },
    workers_ai: { label: 'Workers AI', tone: 'cloud' },
    web_search_tool: { label: '联网搜索', tone: 'online' },
    tavily_web_search: { label: '联网搜索', tone: 'online' },
    weather_tool: { label: '实时天气', tone: 'online' },
    calculator_tool: { label: '计算器', tone: 'utility' },
    selected_model_failed: { label: '所选模型失败', tone: 'error' },
    provider_pool_failed: { label: '模型池失败', tone: 'error' },
    cloud_fetch_failed: { label: '云端连接失败', tone: 'error' },
  };

  function escapeHtml(value) {
    return String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#039;');
  }

  function readMessages() {
    try {
      const parsed = JSON.parse(localStorage.getItem(CHAT_KEY) || '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  function writeMessages(messages) {
    localStorage.setItem(CHAT_KEY, JSON.stringify(messages));
    window.chatMessages = messages;
  }

  function initialMessage() {
    return [{
      id: 'welcome',
      role: 'assistant',
      content: '你好，我是你的 AI 助手。你可以让我记账、查账单、查天气、读网页、设置提醒、打开应用，也可以直接和我聊天。',
      action: 'chat',
      records: [],
      draftState: 'none',
      source: 'builtin_profile',
    }];
  }

  function readModelPreference() {
    try {
      const value = String(JSON.parse(localStorage.getItem(PREF_KEY) || '{}').model || 'auto');
      return MODELS.some((item) => item.id === value) ? value : 'auto';
    } catch {
      return 'auto';
    }
  }

  function writeModelPreference(model) {
    const value = MODELS.some((item) => item.id === model) ? model : 'auto';
    localStorage.setItem(PREF_KEY, JSON.stringify({ model: value, updatedAt: Date.now() }));
    updateModelButton();
  }

  function currentModel() {
    return MODELS.find((item) => item.id === readModelPreference()) || MODELS[0];
  }

  function inferSource(message) {
    if (!message || message.role !== 'assistant') return null;
    if (message.source) return message.source;
    if (message.mobileCommand) return 'local_mobile';
    if (Array.isArray(message.records) && message.records.length) return 'local_ledger';
    if (message.id === 'welcome') return 'builtin_profile';
    return 'cloud_ai';
  }

  function sourceMeta(source, message) {
    if (source === 'nvidia_chat') {
      const text = `${message?.modelLabel || ''} ${message?.model || ''} ${message?.version || ''}`.toLowerCase();
      if (text.includes('kimi')) return { label: 'Kimi 对话', tone: 'cloud' };
      if (text.includes('mistral')) return { label: 'Mistral 对话', tone: 'cloud' };
      return { label: 'NVIDIA NIM', tone: 'cloud' };
    }
    if (SOURCE_LABELS[source]) return SOURCE_LABELS[source];
    if (/search|tavily|web/i.test(source || '')) return { label: '联网搜索', tone: 'online' };
    if (/gemini/i.test(source || '')) return { label: 'Gemini AI', tone: 'gemini' };
    if (/worker/i.test(source || '')) return { label: 'Workers AI', tone: 'cloud' };
    if (/error|failed/i.test(source || '')) return { label: '调用失败', tone: 'error' };
    return { label: '云端 AI', tone: 'cloud' };
  }

  function compactModelText(message) {
    const raw = message?.modelLabel || message?.model || message?.version || '';
    return String(raw).replace(/^ai-ledger-/i, '').replace(/orchestrator-/i, 'orch-').slice(0, 56);
  }

  function installStyle() {
    document.getElementById(STYLE_ID)?.remove();
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      .chat-source-badge-row{display:flex;justify-content:flex-start;margin:8px 0 2px 4px;gap:6px;flex-wrap:wrap;position:relative;z-index:3}.chat-row.user .chat-source-badge-row{justify-content:flex-end;margin:7px 4px 2px 0}.chat-source-badge{display:inline-flex;align-items:center;gap:5px;border-radius:999px;padding:5px 9px;font-size:11px;font-weight:800;line-height:1.12;background:rgba(255,255,255,.14);border:1px solid rgba(255,255,255,.22);color:rgba(238,250,255,.78);max-width:100%;box-sizing:border-box}.chat-source-badge::before{content:'';width:6px;height:6px;min-width:6px;border-radius:999px;background:currentColor;opacity:.85}.chat-source-badge.cloud{color:#83f7ff;background:rgba(33,197,255,.12);border-color:rgba(33,197,255,.24)}.chat-source-badge.gemini{color:#c7b7ff;background:rgba(126,87,255,.16);border-color:rgba(126,87,255,.30)}.chat-source-badge.online{color:#8ff7c4;background:rgba(22,190,121,.14);border-color:rgba(22,190,121,.30)}.chat-source-badge.utility{color:#ffe38f;background:rgba(240,180,50,.14);border-color:rgba(240,180,50,.28)}.chat-source-badge.builtin{color:#e7ddff;background:rgba(161,117,255,.14);border-color:rgba(161,117,255,.26)}.chat-source-badge.local{color:#cbd5e1;background:rgba(148,163,184,.14);border-color:rgba(148,163,184,.24)}.chat-source-badge.mobile{color:#86ece2;background:rgba(11,143,139,.16);border-color:rgba(11,143,139,.28)}.chat-source-badge.error{color:#ffb4b4;background:rgba(255,91,91,.14);border-color:rgba(255,91,91,.26)}
      .model-picker-sheet-mask{position:fixed;inset:0;z-index:1300;display:none;background:rgba(4,8,20,.30);backdrop-filter:blur(8px);-webkit-backdrop-filter:blur(8px)}.model-picker-sheet-mask.open{display:grid;place-items:end center}.model-picker-sheet{width:min(94vw,520px);margin:0 0 max(14px,env(safe-area-inset-bottom));border-radius:30px;padding:16px;background:linear-gradient(145deg,rgba(246,250,255,.22),rgba(255,255,255,.10) 58%,rgba(255,255,255,.06)),rgba(40,48,84,.60);border:1px solid rgba(255,255,255,.28);box-shadow:0 28px 80px rgba(0,0,0,.34),inset 0 1px 0 rgba(255,255,255,.35);color:rgba(255,255,255,.94);backdrop-filter:blur(24px) saturate(160%);-webkit-backdrop-filter:blur(24px) saturate(160%)}.model-picker-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:12px}.model-picker-head strong{display:block;font-size:18px;letter-spacing:-.03em}.model-picker-head span{display:block;margin-top:4px;font-size:12px;opacity:.68}.model-picker-close{width:34px;height:34px;border:0;border-radius:999px;background:rgba(255,255,255,.16);color:inherit;font-size:22px;line-height:1}.model-picker-list{display:grid;gap:9px}.model-choice{display:flex;align-items:center;gap:10px;width:100%;padding:12px;border-radius:20px;border:1px solid rgba(255,255,255,.18);background:rgba(255,255,255,.10);color:inherit;text-align:left}.model-choice.active{background:linear-gradient(135deg,rgba(99,226,255,.24),rgba(145,106,255,.20));border-color:rgba(132,221,255,.42);box-shadow:inset 0 1px 0 rgba(255,255,255,.28)}.model-choice-dot{width:11px;height:11px;border-radius:999px;border:2px solid rgba(255,255,255,.52);box-shadow:0 0 0 4px rgba(255,255,255,.05)}.model-choice.active .model-choice-dot{background:#8bf7ff;border-color:#8bf7ff;box-shadow:0 0 18px rgba(139,247,255,.55)}.model-choice-text{display:grid;gap:3px}.model-choice-text strong{font-size:14px}.model-choice-text em{font-size:12px;font-style:normal;opacity:.66;line-height:1.35}
    `;
    document.head.appendChild(style);
  }

  function addBadges() {
    const messages = readMessages();
    const byId = new Map(messages.map((message) => [String(message.id), message]));
    document.querySelectorAll('.chat-row[data-message-id]').forEach((row) => {
      row.querySelectorAll(':scope .chat-source-badge-row').forEach((node, index) => { if (index) node.remove(); });
      if (row.querySelector(':scope .chat-source-badge-row')) return;
      const message = byId.get(String(row.dataset.messageId));
      const response = row.querySelector('.chat-response');
      if (!message || !response || message.role !== 'assistant') return;
      const meta = sourceMeta(inferSource(message), message);
      const detail = compactModelText(message);
      response.insertAdjacentHTML('beforeend', `<div class="chat-source-badge-row"><span class="chat-source-badge ${escapeHtml(meta.tone)}">${escapeHtml(meta.label)}${detail ? ` · ${escapeHtml(detail)}` : ''}</span></div>`);
    });
  }

  function updateProgress() {
    const count = readMessages().filter((item) => item?.id !== 'welcome' && (item?.role === 'user' || item?.role === 'assistant')).length;
    const percent = Math.max(4, Math.min(100, Math.round(count / 40 * 100)));
    const text = document.querySelector('#chatCountText');
    const fill = document.querySelector('#chatCountFill');
    const card = document.querySelector('#chatCountCard');
    if (text) text.textContent = `${count} 条`;
    if (fill) fill.style.width = `${percent}%`;
    if (card) card.style.setProperty('--chat-progress', `${percent}%`);
  }

  function updateModelButton() {
    const btn = document.querySelector('#chatModelPickerBtn');
    if (btn) btn.textContent = currentModel().short;
  }

  function normalizeTopControls() {
    const strip = document.querySelector('.chat-summary-strip');
    const model = document.querySelector('#chatModelPickerBtn');
    const badge = document.querySelector('#aiModeBadge');
    const clear = document.querySelector('#clearChatInlineBtn');
    const count = document.querySelector('#chatCountCard');
    if (!strip) return;
    strip.classList.add('model-picker-hero-strip');
    [model, badge, clear, count].forEach((node) => { if (node && node.parentElement !== strip) strip.appendChild(node); });
    if (badge) badge.textContent = '🌐 联网搜索';
    if (clear) clear.textContent = '清空';
    updateModelButton();
    updateProgress();
  }

  function renderModelSheet() {
    let mask = document.querySelector('#modelPickerSheetMask');
    if (!mask) {
      mask = document.createElement('div');
      mask.id = 'modelPickerSheetMask';
      mask.className = 'model-picker-sheet-mask';
      document.body.appendChild(mask);
    }
    const selected = readModelPreference();
    mask.innerHTML = `<section class="model-picker-sheet" role="dialog" aria-modal="true" aria-label="选择云端模型"><div class="model-picker-head"><div><strong>选择云端模型</strong><span>自动模式会按可用性切换；手动选择时会严格使用指定模型。</span></div><button class="model-picker-close" type="button" data-model-picker-close>×</button></div><div class="model-picker-list">${MODELS.map((item) => `<button type="button" class="model-choice ${item.id === selected ? 'active' : ''}" data-model-choice="${escapeHtml(item.id)}"><span class="model-choice-dot"></span><span class="model-choice-text"><strong>${escapeHtml(item.label)}</strong><em>${escapeHtml(item.hint)}</em></span></button>`).join('')}</div></section>`;
    return mask;
  }

  function openModelSheet() {
    renderModelSheet().classList.add('open');
  }

  function clearConversation() {
    writeMessages(initialMessage());
    if (window.AiAssistantRuntime?.setChatWindowLimit) window.AiAssistantRuntime.setChatWindowLimit(60);
    if (typeof window.renderChat === 'function') window.renderChat();
    else if (typeof window.renderAll === 'function') window.renderAll();
    updateProgress();
  }

  function installControls() {
    normalizeTopControls();
    const btn = document.querySelector('#chatModelPickerBtn');
    if (btn && btn.dataset.modelPickerBound !== 'true') {
      btn.dataset.modelPickerBound = 'true';
      btn.addEventListener('click', (event) => { event.preventDefault(); openModelSheet(); });
    }
    const clear = document.querySelector('#clearChatInlineBtn');
    if (clear && clear.dataset.clearChatBound !== 'true') {
      clear.dataset.clearChatBound = 'true';
      clear.addEventListener('click', (event) => { event.preventDefault(); clearConversation(); });
    }
  }

  function patchFetch() {
    if (window[FETCH_PATCH_FLAG]) return;
    window[FETCH_PATCH_FLAG] = true;
    const nativeFetch = window.fetch.bind(window);
    window.fetch = async (input, init = {}) => {
      try {
        const method = String(init?.method || (input instanceof Request ? input.method : 'GET')).toUpperCase();
        const body = init?.body;
        if (method === 'POST' && typeof body === 'string' && (body.includes('messages') || body.includes('attachments'))) {
          const data = JSON.parse(body);
          if (data && !data.modelPreference) {
            data.modelPreference = readModelPreference();
            data.aiModelPreference = readModelPreference();
            init = { ...init, body: JSON.stringify(data) };
          }
        }
      } catch {}
      return nativeFetch(input, init);
    };
  }

  function installClicks() {
    if (document.documentElement.dataset.modelPickerSheetClicks === 'true') return;
    document.documentElement.dataset.modelPickerSheetClicks = 'true';
    document.addEventListener('click', (event) => {
      const mask = document.querySelector('#modelPickerSheetMask');
      if (!mask?.classList.contains('open')) return;
      if (event.target === mask || event.target.closest('[data-model-picker-close]')) {
        mask.classList.remove('open');
        return;
      }
      const choice = event.target.closest('[data-model-choice]');
      if (!choice) return;
      writeModelPreference(choice.dataset.modelChoice);
      mask.classList.remove('open');
    }, true);
  }

  function boot() {
    installStyle();
    patchFetch();
    installClicks();
    installControls();
    addBadges();
    updateProgress();
  }

  window.ChatSourceBadges = { refresh: addBadges, labels: SOURCE_LABELS, pinBottom() {}, updateProgress };
  window.AiLedgerModelPicker = { current: readModelPreference, set: writeModelPreference, models: MODELS };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();

  window.setInterval(() => {
    installControls();
    addBadges();
    updateProgress();
  }, 700);
})();
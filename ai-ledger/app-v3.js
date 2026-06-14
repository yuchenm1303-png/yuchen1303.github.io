(() => {
  'use strict';

  const STORAGE_KEY = 'ai-ledger-records-v3';
  const CHAT_KEY = 'ai-ledger-chat-v2';
  const AI_CONFIG_KEY = 'ai-ledger-ai-config-v1';
  const DEFAULT_AI_CONFIG = window.AI_LEDGER_CONFIG || {};
  const DEFAULT_AI_ENDPOINT = DEFAULT_AI_CONFIG.aiEndpoint || '';
  const DEFAULT_AI_TIMEOUT_MS = Number(DEFAULT_AI_CONFIG.aiTimeoutMs) || 12000;
  const CHAT_RENDER_LIMIT = 60;
  const CHAT_RENDER_STEP = 40;
  const RECORD_RENDER_LIMIT = 80;
  const RECORD_RENDER_STEP = 80;
  const CHAT_KEYBOARD_GAP = 110;

  const categories = ['餐饮', '交通', '购物', '居住', '饮品', '工资', '礼物', '其他'];
  const typeMap = { expense: '支出', income: '收入' };

  const initialChat = [{
    id: 'welcome',
    role: 'assistant',
    content: '你好，我是你的 AI 助手。你可以让我记账、查账单、查天气、读网页、设置提醒、打开应用，也可以直接和我聊天。',
    action: 'chat',
    records: [],
    draftState: 'none',
    source: 'builtin_profile',
  }];

  const els = {
    navBtns: document.querySelectorAll('.nav-btn'),
    views: {
      ai: document.querySelector('#view-ai'),
      tools: document.querySelector('#view-tools'),
      stats: document.querySelector('#view-stats'),
      list: document.querySelector('#view-list'),
      settings: document.querySelector('#view-settings'),
    },
    chatMessages: document.querySelector('#chatMessages'),
    chatForm: document.querySelector('#chatForm'),
    aiInput: document.querySelector('#aiInput'),
    sendBtn: document.querySelector('#aiAddBtn') || document.querySelector('#sendBtn'),
    sampleBtns: document.querySelectorAll('.sample-btn'),
    aiModeBadge: document.querySelector('#aiModeBadge'),
    aiModeHint: document.querySelector('#aiModeHint'),
    aiTodayExpense: document.querySelector('#aiTodayExpense'),
    aiMonthBalance: document.querySelector('#aiMonthBalance'),
    todaySpend: document.querySelector('#todaySpend'),
    monthIncome: document.querySelector('#monthIncome'),
    monthExpense: document.querySelector('#monthExpense'),
    monthBalance: document.querySelector('#monthBalance'),
    recordList: document.querySelector('#recordList'),
    recordCount: document.querySelector('#recordCount'),
    addManualBtn: document.querySelector('#addManualBtn'),
    addSheet: document.querySelector('#addSheet'),
    closeSheetBtn: document.querySelector('#closeSheetBtn'),
    saveManualBtn: document.querySelector('#saveManualBtn'),
    manualTitle: document.querySelector('#manualTitle'),
    manualAmount: document.querySelector('#manualAmount'),
    manualType: document.querySelector('#manualType'),
    manualCategory: document.querySelector('#manualCategory'),
    exportBtn: document.querySelector('#exportBtn'),
    clearChatBtn: document.querySelector('#clearChatBtn'),
    toast: document.querySelector('#toast'),
    budgetInput: document.querySelector('#budgetInput'),
    aiEndpointInput: document.querySelector('#aiEndpointInput'),
    saveAiEndpointBtn: document.querySelector('#saveAiEndpointBtn'),
    resetAiEndpointBtn: document.querySelector('#resetAiEndpointBtn'),
    testAiEndpointBtn: document.querySelector('#testAiEndpointBtn'),
    aiEndpointStatus: document.querySelector('#aiEndpointStatus'),
  };

  let records = loadRecords();
  let chatMessages = loadChatMessages();
  let currentView = 'ai';
  let aiEndpoint = loadAiConfig().endpoint;
  let aiBusy = false;
  let visibleChatLimit = CHAT_RENDER_LIMIT;
  let visibleRecordLimit = RECORD_RENDER_LIMIT;
  let lastChatRenderKey = '';
  let lastRecordRenderKey = '';
  let viewportFrame = 0;
  let resizeSettleTimer = 0;
  let stableVisualHeight = Math.round(window.visualViewport?.height || window.innerHeight || 0);

  function loadRecords() {
    try {
      const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  function saveRecords(next = records) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  }

  function loadChatMessages() {
    try {
      const parsed = JSON.parse(localStorage.getItem(CHAT_KEY) || '[]');
      return Array.isArray(parsed) && parsed.length ? parsed : [...initialChat];
    } catch {
      return [...initialChat];
    }
  }

  function saveChatMessages() {
    localStorage.setItem(CHAT_KEY, JSON.stringify(chatMessages));
  }

  function createId(prefix = 'id') {
    if (crypto.randomUUID) return `${prefix}-${crypto.randomUUID()}`;
    return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  function todayISO() { return new Date().toISOString().slice(0, 10); }
  function shiftDate(iso, days) { const d = new Date(`${iso}T00:00:00Z`); d.setUTCDate(d.getUTCDate() + days); return d.toISOString().slice(0, 10); }
  function currentMonthPrefix() { return todayISO().slice(0, 7); }
  function formatCurrency(value) { return `¥${Number(value || 0).toFixed(2)}`; }
  function escapeHtml(value) { return String(value ?? '').replaceAll('&', '&').replaceAll('<', '<').replaceAll('>', '>').replaceAll('"', '"').replaceAll("'", '&#039;'); }

  function showToast(message) {
    if (!els.toast) return;
    els.toast.textContent = message;
    els.toast.classList.add('show');
    clearTimeout(showToast.timer);
    showToast.timer = setTimeout(() => els.toast.classList.remove('show'), 2200);
  }

  function normalizeEndpoint(value) { return String(value || '').trim().replace(/\/+$/g, ''); }
  function loadAiConfig() { try { const parsed = JSON.parse(localStorage.getItem(AI_CONFIG_KEY) || '{}'); return { endpoint: normalizeEndpoint(parsed.endpoint || DEFAULT_AI_ENDPOINT) }; } catch { return { endpoint: normalizeEndpoint(DEFAULT_AI_ENDPOINT) }; } }
  function saveAiConfig(config) { localStorage.setItem(AI_CONFIG_KEY, JSON.stringify({ endpoint: normalizeEndpoint(config.endpoint) })); }

  function setAiStatus(message, mode = 'normal') {
    if (els.aiEndpointStatus) {
      els.aiEndpointStatus.textContent = message;
      els.aiEndpointStatus.dataset.mode = mode;
    }
  }

  function updateAiModeUI() {
    if (els.aiEndpointInput) els.aiEndpointInput.value = aiEndpoint || '';
    setAiStatus(aiEndpoint ? '云端 AI 接口已配置' : '未配置云端 AI，当前使用本地识别', aiEndpoint ? 'success' : 'normal');
    if (els.aiModeBadge) els.aiModeBadge.textContent = aiEndpoint ? '✨云端' : '✨本地';
    if (els.aiModeHint) {
      els.aiModeHint.textContent = aiEndpoint
        ? '已连接云端 AI。本地动作会优先快速识别，复杂问题再交给云端。'
        : '当前使用本地识别：可记账、设置提醒、打开应用和导航；复杂聊天需要配置云端 AI。';
    }
  }

  function setAiLoading(isLoading) {
    aiBusy = isLoading;
    if (els.sendBtn) els.sendBtn.disabled = isLoading;
    if (els.aiInput) els.aiInput.disabled = isLoading;
  }

  function scrollChatToBottom(smooth = false) {
    if (!els.chatMessages) return;
    const host = els.chatMessages;
    const behavior = smooth && !document.body.classList.contains('keyboard-open') ? 'smooth' : 'auto';
    requestAnimationFrame(() => host.scrollTo({ top: host.scrollHeight, behavior }));
  }

  function renderTyping() {
    if (!els.chatMessages || document.querySelector('#typingRow')) return;
    els.chatMessages.insertAdjacentHTML('beforeend', '<div class="chat-row assistant" id="typingRow"><div class="chat-bubble"><span class="typing-dot"></span><span class="typing-dot"></span><span class="typing-dot"></span></div></div>');
    scrollChatToBottom(false);
  }

  function removeTyping() { document.querySelector('#typingRow')?.remove(); }
  function isMathOrOnlineQuery(text) { return /(天气|下雨|气温|温度|风速|降雨|上网|联网|搜索|查一下|搜一下|最新|新闻|http|www\.|计算|算一下|等于|[0-9]\s*[+\-×÷*/^]\s*[0-9])/u.test(String(text || '')); }

  function normalizeRecord(record) {
    return {
      id: record.id || createId('record'),
      title: String(record.title || '未命名账单').slice(0, 30),
      amount: Number(record.amount) || 0,
      type: record.type === 'income' ? 'income' : 'expense',
      category: categories.includes(record.category) ? record.category : '其他',
      date: /^\d{4}-\d{2}-\d{2}$/.test(record.date || '') ? record.date : todayISO(),
    };
  }

  function normalizeRecords(list) { return Array.isArray(list) ? list.map(normalizeRecord).filter((item) => item.amount > 0) : []; }
  function normalizeMobileCommand(command) { if (!command || typeof command !== 'object') return null; if (!['set_alarm', 'navigate'].includes(command.type)) return null; return command; }

  function mobileClientTools() {
    return (window.MobileCommandActions?.tools || []).filter((tool) => tool?.commandType !== 'open_app');
  }

  function cleanTitle(text) { return String(text || '').replace(/今天|昨天|前天|花了|花费|消费|支出|收入|进账|收到|元|块钱|块/gu, '').replace(/[0-9.]/gu, '').replace(/[，,。；;、]/gu, '').trim() || '未命名账单'; }
  function inferCategory(text) { if (/(饭|早餐|午餐|晚餐|外卖|面|米线|火锅|烧烤|餐)/u.test(text)) return '餐饮'; if (/(奶茶|咖啡|饮料|可乐|茶)/u.test(text)) return '饮品'; if (/(打车|出租|公交|地铁|高铁|火车|机票|加油)/u.test(text)) return '交通'; if (/(淘宝|京东|拼多多|买|衣服|鞋|超市|购物)/u.test(text)) return '购物'; if (/(房租|水电|物业|宿舍|宽带)/u.test(text)) return '居住'; if (/(工资|兼职|奖金|补贴|报销|收入)/u.test(text)) return '工资'; if (/(礼物|红包)/u.test(text)) return '礼物'; return '其他'; }
  function inferType(text) { return /(收入|工资|兼职|奖金|报销|收到|进账)/u.test(text) ? 'income' : 'expense'; }

  function parseNaturalLanguage(text) {
    if (isMathOrOnlineQuery(text)) return [];
    if (!/(花|买|消费|支出|收入|收到|工资|报销|元|块|奶茶|午饭|晚饭|早餐|打车|地铁|公交)/u.test(text)) return [];
    if (/(我付了|自己花|垫付|平摊|AA)/u.test(text)) return [];
    const parts = String(text).split(/[，,。；;、\n]/).map((item) => item.trim()).filter(Boolean);
    const parsed = parts.map((part) => {
      const amountMatch = part.match(/(\d+(?:\.\d+)?)/u);
      if (!amountMatch) return null;
      const amount = Number(amountMatch[1]);
      if (!Number.isFinite(amount) || amount <= 0) return null;
      return { id: createId('draft'), title: cleanTitle(part), amount, type: inferType(part), category: inferCategory(part), date: /昨天/u.test(part) ? shiftDate(todayISO(), -1) : todayISO() };
    }).filter(Boolean);
    return parsed.length === parts.length ? parsed : [];
  }

  function getPendingMessage() { return chatMessages.find((item) => item.role === 'assistant' && item.action === 'draft' && item.draftState === 'pending'); }

  function getLedgerContext() {
    const month = currentMonthPrefix();
    const monthRecords = records.filter((record) => record.date?.startsWith(month));
    const monthIncome = monthRecords.filter((r) => r.type === 'income').reduce((s, r) => s + r.amount, 0);
    const monthExpense = monthRecords.filter((r) => r.type === 'expense').reduce((s, r) => s + r.amount, 0);
    return { summary: { monthIncome, monthExpense, monthBalance: monthIncome - monthExpense }, recentRecords: records.slice(0, 60) };
  }

  function buildRecordCard(recordsList) {
    const rows = recordsList.map((r) => `<div class="draft-record"><div><strong>${escapeHtml(r.title)}</strong><span>${escapeHtml(r.date)} · ${escapeHtml(r.category)}</span></div><em>${r.type === 'income' ? '+' : '-'}${formatCurrency(r.amount)}</em></div>`).join('');
    return `<div class="draft-card"><div class="draft-head"><strong>待确认账单</strong><span>${recordsList.length} 笔</span></div>${rows}<button class="confirm-draft" data-action="confirm-draft">确认记账</button><button class="cancel-draft" data-action="cancel-draft">先不保存</button></div>`;
  }

  function renderMobileCommandCard(command) { return window.MobileCommandActions?.renderCard?.(command) || ''; }

  function buildChatRow(message) {
    const isUser = message.role === 'user';
    const source = message.source ? ` data-source="${escapeHtml(message.source)}"` : '';
    const draft = message.action === 'draft' && message.records?.length ? buildRecordCard(message.records) : '';
    const mobile = message.action === 'mobile_command' && message.mobileCommand ? renderMobileCommandCard(message.mobileCommand) : '';
    return `<div class="chat-row ${isUser ? 'user' : 'assistant'}" data-message-id="${escapeHtml(message.id)}"${source}><div class="chat-bubble chat-response">${escapeHtml(message.content).replace(/\n/g, '<br>')}${draft}${mobile}</div></div>`;
  }

  function getChatRenderKey(start, visible) { return `${start}:${chatMessages.length}:${visibleChatLimit}:` + visible.map((message) => `${message.id}|${message.action || ''}|${message.draftState || ''}|${message.mobileCommand?.id || ''}|${message.records?.length || 0}`).join('~'); }

  function renderChat({ force = false, preserveScroll = false } = {}) {
    const host = els.chatMessages;
    if (!host) return;
    visibleChatLimit = Math.max(CHAT_RENDER_LIMIT, Math.min(visibleChatLimit, Math.max(chatMessages.length, CHAT_RENDER_LIMIT)));
    const total = chatMessages.length;
    const start = Math.max(0, total - visibleChatLimit);
    const visible = chatMessages.slice(start);
    const key = getChatRenderKey(start, visible);
    const hasTyping = Boolean(document.querySelector('#typingRow'));
    if (!force && !hasTyping && key === lastChatRenderKey) return;
    const previousBottom = host.scrollHeight - host.scrollTop - host.clientHeight;
    const wasNearBottom = previousBottom < 96;
    const olderButton = start > 0 ? `<div class="chat-history-gate"><button type="button" data-action="load-older-chat">查看更早 ${start} 条消息</button></div>` : '';
    host.innerHTML = olderButton + visible.map(buildChatRow).join('');
    lastChatRenderKey = key;
    if (preserveScroll && !wasNearBottom) requestAnimationFrame(() => { host.scrollTop = Math.max(0, host.scrollHeight - host.clientHeight - previousBottom); });
    else scrollChatToBottom(false);
  }

  function resetChatWindowToBottom() { visibleChatLimit = CHAT_RENDER_LIMIT; lastChatRenderKey = ''; }
  function loadOlderChat() { const host = els.chatMessages; const previousHeight = host?.scrollHeight || 0; const previousTop = host?.scrollTop || 0; visibleChatLimit = Math.min(chatMessages.length, visibleChatLimit + CHAT_RENDER_STEP); renderChat({ force: true, preserveScroll: true }); requestAnimationFrame(() => { if (!host) return; host.scrollTop = previousTop + Math.max(0, host.scrollHeight - previousHeight); }); }

  function renderStats() {
    const today = todayISO();
    const month = currentMonthPrefix();
    const todaySpend = records.filter((r) => r.type === 'expense' && r.date === today).reduce((s, r) => s + r.amount, 0);
    const monthRecords = records.filter((r) => r.date?.startsWith(month));
    const monthIncome = monthRecords.filter((r) => r.type === 'income').reduce((s, r) => s + r.amount, 0);
    const monthExpense = monthRecords.filter((r) => r.type === 'expense').reduce((s, r) => s + r.amount, 0);
    const balance = monthIncome - monthExpense;
    if (els.aiTodayExpense) els.aiTodayExpense.textContent = formatCurrency(todaySpend);
    if (els.aiMonthBalance) els.aiMonthBalance.textContent = formatCurrency(balance);
    if (els.todaySpend) els.todaySpend.textContent = formatCurrency(todaySpend);
    if (els.monthIncome) els.monthIncome.textContent = formatCurrency(monthIncome);
    if (els.monthExpense) els.monthExpense.textContent = formatCurrency(monthExpense);
    if (els.monthBalance) els.monthBalance.textContent = formatCurrency(balance);
  }

  function buildRecordRow(r) {
    return `<article class="record-item"><div><strong>${escapeHtml(r.title)}</strong><span>${escapeHtml(r.date)} · ${escapeHtml(r.category)} · ${escapeHtml(typeMap[r.type])}</span></div><em>${r.type === 'income' ? '+' : '-'}${formatCurrency(r.amount)}</em><button data-remove="${escapeHtml(r.id)}">删除</button></article>`;
  }

  function renderList({ force = false } = {}) {
    if (!els.recordList) return;
    const total = records.length;
    visibleRecordLimit = Math.max(RECORD_RENDER_LIMIT, Math.min(visibleRecordLimit, Math.max(total, RECORD_RENDER_LIMIT)));
    const visible = records.slice(0, visibleRecordLimit);
    const key = `${total}:${visibleRecordLimit}:` + visible.map((r) => `${r.id}|${r.title}|${r.amount}|${r.type}|${r.category}|${r.date}`).join('~');
    if (!force && key === lastRecordRenderKey) return;
    if (els.recordCount) els.recordCount.textContent = `${total} 条${total > visible.length ? ` · 已显示 ${visible.length} 条` : ''}`;
    if (!total) {
      els.recordList.innerHTML = '<p class="empty-state">还没有账单。</p>';
    } else {
      const more = total > visible.length
        ? `<div class="record-history-gate"><button type="button" data-action="load-more-records">再显示 ${Math.min(RECORD_RENDER_STEP, total - visible.length)} 条</button></div>`
        : '';
      els.recordList.innerHTML = visible.map(buildRecordRow).join('') + more;
    }
    lastRecordRenderKey = key;
  }

  function resetRecordWindow() { visibleRecordLimit = RECORD_RENDER_LIMIT; lastRecordRenderKey = ''; }
  function loadMoreRecords() { visibleRecordLimit = Math.min(records.length, visibleRecordLimit + RECORD_RENDER_STEP); renderList({ force: true }); }

  function renderAll() { renderChat(); renderStats(); renderList(); updateAiModeUI(); }
  function getTopLevelView(name) { return name === 'stats' || name === 'list' ? 'tools' : name; }
  function switchView(name) { if (!els.views[name]) name = 'ai'; currentView = name; Object.entries(els.views).forEach(([key, el]) => el?.classList.toggle('active', key === name)); const top = getTopLevelView(name); els.navBtns.forEach((btn) => btn.classList.toggle('active', btn.dataset.view === top)); if (name === 'settings') updateAiModeUI(); if (name === 'tools') window.dispatchEvent(new CustomEvent('ai-tools-home')); if (name === 'list') renderList({ force: true }); }

  function conversationPayload() { return chatMessages.filter((m) => m.role === 'user' || m.role === 'assistant').slice(-16).map((m) => ({ role: m.role, content: m.content })); }
  async function fetchJsonWithTimeout(url, options, timeoutMs = DEFAULT_AI_TIMEOUT_MS) { const controller = new AbortController(); const timer = setTimeout(() => controller.abort(), timeoutMs); try { const response = await fetch(url, { ...options, signal: controller.signal }); const text = await response.text(); let data = null; try { data = text ? JSON.parse(text) : null; } catch {} return { response, data, text }; } finally { clearTimeout(timer); } }
  function formatCloudError(result) { const data = result?.data || {}; if (data.providerStatus) return `云端 AI 调用失败：${data.providerCode || data.code || `HTTP ${data.providerStatus}`}`; if (data.code) return `云端 AI 调用失败：${data.code}`; return '云端 AI 暂时不可用'; }

  async function askCloudAI() {
    if (!aiEndpoint) return null;
    const pending = getPendingMessage();
    const payload = { messages: conversationPayload(), pendingDraft: pending?.records || [], ledgerContext: getLedgerContext(), clientTools: mobileClientTools(), now: todayISO() };
    const result = await fetchJsonWithTimeout(aiEndpoint, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(payload) }, DEFAULT_AI_TIMEOUT_MS);
    if (!result.response.ok) { const error = new Error(formatCloudError(result)); error.cloudResult = result; throw error; }
    const mobileCommand = normalizeMobileCommand(result.data?.mobileCommand);
    const action = result.data?.action === 'mobile_command' && !mobileCommand ? 'chat' : result.data?.action || 'chat';
    return { reply: String(result.data?.reply || result.data?.response || result.data?.text || '').trim(), action, records: normalizeRecords(result.data?.records), mobileCommand, source: result.data?.source || 'cloud_ai', version: result.data?.version };
  }

  function makeMobileResult(command, source = 'local_mobile') { return { reply: window.MobileCommandActions?.createReply?.(command) || '我整理好了这个手机动作，确认后我再执行。', action: 'mobile_command', records: [], mobileCommand: command, source }; }

  function immediateLocalResult(text) {
    const pending = getPendingMessage();
    if (pending && /^(好|好的|对|确认|保存|记上|就这样)$/u.test(text)) return { reply: '好的，已帮你记上。', action: 'confirm_pending', records: [], source: 'local_confirm' };
    if (pending && /^(算了|不用了|先别记|取消)$/u.test(text)) return { reply: '好的，这次先不保存。', action: 'cancel_pending', records: [], source: 'local_confirm' };
    const routed = window.AICommandRouter?.toAssistantResult?.(text);
    if (routed) return routed;
    if (!isMathOrOnlineQuery(text)) { const mobileCommand = normalizeMobileCommand(window.MobileCommandActions?.parse?.(text)); if (mobileCommand) return makeMobileResult(mobileCommand, 'local_mobile'); }
    return null;
  }

  function localFallbackResult(text) {
    const pending = getPendingMessage();
    if (pending && /^(好|好的|对|确认|保存|记上|就这样)$/u.test(text)) return { reply: '好的，已帮你记上。', action: 'confirm_pending', records: [], source: 'local_confirm' };
    if (pending && /^(算了|不用了|先别记|取消)$/u.test(text)) return { reply: '好的，这次先不保存。', action: 'cancel_pending', records: [], source: 'local_confirm' };
    const routed = window.AICommandRouter?.toAssistantResult?.(text);
    if (routed) return routed;
    const mobileCommand = normalizeMobileCommand(window.MobileCommandActions?.parse?.(text));
    if (mobileCommand) return makeMobileResult(mobileCommand, 'local_mobile');
    if (isMathOrOnlineQuery(text)) return { reply: '这个问题需要云端工具处理，但当前云端没有成功返回。请检查 Worker 是否部署成功，或到设置里测试连接。', action: 'chat', records: [], source: 'local' };
    const parsed = parseNaturalLanguage(text);
    if (parsed.length) return { reply: `我先整理出 ${parsed.length} 笔待确认账单，你回复“好”我就帮你保存。`, action: 'draft', records: parsed, source: 'local_ledger' };
    return { reply: '我还没听清。你可以换个说法，或者先配置云端 AI，我就能处理更复杂的聊天。', action: 'chat', records: [], source: 'local' };
  }

  function applyAssistantResult(result) {
    const pending = getPendingMessage();
    if (result.action === 'confirm_pending' && pending) confirmDraft(pending.id, false, false);
    else if (result.action === 'cancel_pending' && pending) pending.draftState = 'cancelled';
    else if (result.action === 'draft' && pending) pending.draftState = 'superseded';
    chatMessages.push({ id: createId('assistant'), role: 'assistant', content: result.reply || '我在。', action: result.action || 'chat', records: result.action === 'draft' ? normalizeRecords(result.records) : [], draftState: result.action === 'draft' && result.records?.length ? 'pending' : 'none', mobileCommand: result.action === 'mobile_command' ? normalizeMobileCommand(result.mobileCommand) : null, source: result.source || 'assistant_runtime', version: result.version, router: result.router });
    saveChatMessages();
    renderAll();
  }

  async function askAssistant(text) {
    if (aiBusy) return;
    const clean = String(text || '').trim();
    if (!clean) return showToast('先说一句吧');
    resetChatWindowToBottom();
    chatMessages.push({ id: createId('user'), role: 'user', content: clean });
    saveChatMessages();
    renderChat({ force: true });
    const instant = immediateLocalResult(clean);
    if (instant) { applyAssistantResult(instant); return; }
    let result = null;
    if (aiEndpoint) {
      setAiLoading(true);
      renderTyping();
      try { result = await askCloudAI(); setAiStatus(`云端 AI 已连接${result.version ? ` · ${result.version}` : ''}`, 'success'); }
      catch (error) { console.warn('Cloud AI failed:', error); setAiStatus(error.message || '云端 AI 暂时不可用', 'error'); showToast(error.message || '云端 AI 暂时不可用'); }
      finally { removeTyping(); setAiLoading(false); }
    }
    applyAssistantResult(result || localFallbackResult(clean));
  }

  function confirmDraft(messageId, announce = true, shouldRender = true) {
    const message = chatMessages.find((item) => item.id === messageId);
    if (!message || message.draftState !== 'pending' || !message.records?.length) return;
    records = [...message.records.map((record) => ({ ...record, id: createId('record') })), ...records];
    resetRecordWindow();
    message.draftState = 'confirmed';
    saveRecords(records); saveChatMessages(); lastChatRenderKey = '';
    if (announce) showToast(`已保存 ${message.records.length} 条账单`);
    if (shouldRender) renderAll();
  }

  function cancelDraft(messageId) { const message = chatMessages.find((item) => item.id === messageId); if (!message || message.draftState !== 'pending') return; message.draftState = 'cancelled'; saveChatMessages(); lastChatRenderKey = ''; renderChat({ force: true }); showToast('已取消这次记账'); }
  function clearChat() { chatMessages = [...initialChat]; window.chatMessages = chatMessages; saveChatMessages(); resetChatWindowToBottom(); renderChat({ force: true }); showToast('已清空聊天记录'); }
  function openSheet() { if (!els.addSheet) return; document.body.classList.add('sheet-open'); els.addSheet.setAttribute('aria-hidden', 'false'); }
  function closeSheet() { if (!els.addSheet) return; document.body.classList.remove('sheet-open'); els.addSheet.setAttribute('aria-hidden', 'true'); }

  function addManualRecord() {
    const title = els.manualTitle?.value.trim();
    const amount = Number(els.manualAmount?.value);
    if (!title || !Number.isFinite(amount) || amount <= 0) return showToast('标题和金额要填完整哦');
    records = [{ id: createId('record'), title, amount, type: els.manualType?.value || 'expense', category: els.manualCategory?.value || '其他', date: todayISO() }, ...records];
    resetRecordWindow();
    saveRecords(records); renderStats(); renderList({ force: true }); closeSheet(); showToast('已添加一条账单');
  }

  function exportRecords() { const blob = new Blob([JSON.stringify(records, null, 2)], { type: 'application/json' }); const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = 'ai-ledger-records.json'; link.click(); URL.revokeObjectURL(url); }

  async function testAiEndpoint() {
    const endpoint = normalizeEndpoint(els.aiEndpointInput?.value);
    if (!endpoint) return showToast('请先填写 AI 接口地址');
    if (els.testAiEndpointBtn) { els.testAiEndpointBtn.disabled = true; els.testAiEndpointBtn.textContent = '测试中...'; }
    setAiStatus('正在检测云端 AI…', 'normal');
    try { const health = await fetchJsonWithTimeout(`${endpoint}/health`, { method: 'GET' }, 8000); if (!health.response.ok || !health.data?.ok) throw new Error(`Worker 健康检查失败：HTTP ${health.response.status}`); setAiStatus(`AI 接口连接成功 · ${health.data.version || '已连通'}`, 'success'); showToast('AI 接口连接成功'); }
    catch (error) { setAiStatus(error.message || 'AI 接口连接失败', 'error'); showToast(error.message || 'AI 接口连接失败'); }
    finally { if (els.testAiEndpointBtn) { els.testAiEndpointBtn.disabled = false; els.testAiEndpointBtn.textContent = '测试连接'; } }
  }

  function syncViewportMetrics() {
    const viewport = window.visualViewport;
    const visualHeight = Math.round(viewport?.height || window.innerHeight || 0);
    const offsetTop = Math.round(viewport?.offsetTop || 0);
    if (!visualHeight) return;

    stableVisualHeight = Math.max(stableVisualHeight || visualHeight, visualHeight);
    const keyboardGap = Math.max(0, stableVisualHeight - visualHeight - offsetTop);
    
    const isQuickAi = document.body.classList.contains('quick-ai-entry');
    const keyboardOpen = document.activeElement === els.aiInput && keyboardGap > CHAT_KEYBOARD_GAP;

    document.documentElement.style.setProperty('--app-visual-vh', `${visualHeight}px`);
    document.documentElement.style.setProperty('--app-stable-vh', `${stableVisualHeight}px`);
    document.documentElement.style.setProperty('--keyboard-gap', `${keyboardOpen ? keyboardGap : 0}px`);

    document.body.classList.toggle('keyboard-open', keyboardOpen);
    document.body.classList.toggle('chat-input-focused', document.activeElement === els.aiInput);

    // ==================== Quick AI Spacer 处理 ====================
    const spacer = document.getElementById('keyboardSpacer');
    if (spacer) {
        if (isQuickAi && keyboardOpen) {
            spacer.style.height = `${Math.max(58, keyboardGap - 20)}px`;
            spacer.style.background = 'rgba(255,255,255,0.06)';
            spacer.style.borderRadius = '20px 20px 0 0';
        } else {
            spacer.style.height = '0px';
            spacer.style.background = 'transparent';
        }
    }
    // ============================================================

    if (keyboardOpen) scrollChatToBottom(false);

    clearTimeout(resizeSettleTimer);
    resizeSettleTimer = setTimeout(() => {
        document.body.classList.remove('viewport-resizing');
        if (document.activeElement === els.aiInput) scrollChatToBottom(false);
    }, 180);
  }

  function scheduleViewportSync() { document.body.classList.add('viewport-resizing'); cancelAnimationFrame(viewportFrame); viewportFrame = requestAnimationFrame(syncViewportMetrics); }

  function installViewportStability() {
    syncViewportMetrics();
    window.visualViewport?.addEventListener('resize', scheduleViewportSync, { passive: true });
    window.visualViewport?.addEventListener('scroll', scheduleViewportSync, { passive: true });
    window.addEventListener('resize', scheduleViewportSync, { passive: true });
    window.addEventListener('orientationchange', () => { stableVisualHeight = 0; setTimeout(scheduleViewportSync, 240); }, { passive: true });
    els.aiInput?.addEventListener('focus', () => { document.body.classList.add('chat-input-focused'); scheduleViewportSync(); setTimeout(() => scrollChatToBottom(false), 120); });
    els.aiInput?.addEventListener('blur', () => { setTimeout(() => { document.body.classList.remove('keyboard-open', 'chat-input-focused', 'viewport-resizing'); document.documentElement.style.setProperty('--keyboard-gap', '0px'); scheduleViewportSync(); }, 180); });
  }

  function bindEvents() {
    els.navBtns.forEach((button) => button.addEventListener('click', () => switchView(button.dataset.view)));
    document.addEventListener('click', (event) => {
      const viewBtn = event.target.closest('[data-open-view]'); if (viewBtn) return switchView(viewBtn.dataset.openView);
      const olderBtn = event.target.closest('[data-action="load-older-chat"]'); if (olderBtn) return loadOlderChat();
      const moreRecordsBtn = event.target.closest('[data-action="load-more-records"]'); if (moreRecordsBtn) return loadMoreRecords();
      const confirmBtn = event.target.closest('[data-action="confirm-draft"]'); if (confirmBtn) return confirmDraft(confirmBtn.closest('.chat-row')?.dataset.messageId);
      const cancelBtn = event.target.closest('[data-action="cancel-draft"]'); if (cancelBtn) return cancelDraft(cancelBtn.closest('.chat-row')?.dataset.messageId);
      const remove = event.target.closest('[data-remove]');
      if (remove) { records = records.filter((r) => r.id !== remove.dataset.remove); saveRecords(records); lastRecordRenderKey = ''; renderStats(); renderList({ force: true }); }
    });
    els.sampleBtns.forEach((button) => button.addEventListener('click', () => { if (!els.aiInput) return; els.aiInput.value = button.dataset.sample || ''; els.aiInput.focus(); scheduleViewportSync(); }));
    els.chatForm?.addEventListener('submit', async (event) => { event.preventDefault(); if (!els.aiInput) return; const text = els.aiInput.value.trim(); if (!text) return showToast('先说一句吧'); els.aiInput.value = ''; els.aiInput.style.height = 'auto'; await askAssistant(text); });
    els.aiInput?.addEventListener('input', () => { els.aiInput.style.height = 'auto'; els.aiInput.style.height = `${Math.min(140, els.aiInput.scrollHeight)}px`; scheduleViewportSync(); });
    els.addManualBtn?.addEventListener('click', openSheet); els.closeSheetBtn?.addEventListener('click', closeSheet); els.saveManualBtn?.addEventListener('click', addManualRecord); els.exportBtn?.addEventListener('click', exportRecords); els.clearChatBtn?.addEventListener('click', clearChat);
    els.saveAiEndpointBtn?.addEventListener('click', () => { aiEndpoint = normalizeEndpoint(els.aiEndpointInput?.value); saveAiConfig({ endpoint: aiEndpoint }); updateAiModeUI(); showToast('AI 接口已保存'); });
    els.resetAiEndpointBtn?.addEventListener('click', () => { aiEndpoint = normalizeEndpoint(DEFAULT_AI_ENDPOINT); saveAiConfig({ endpoint: aiEndpoint }); updateAiModeUI(); showToast('已恢复默认 AI 接口'); });
    els.testAiEndpointBtn?.addEventListener('click', testAiEndpoint);
  }

  function exposeDebugApi() {
    window.chatMessages = chatMessages;
    window.createId = createId;
    window.saveChatMessages = saveChatMessages;
    window.renderAll = renderAll;
    window.renderChat = () => renderChat({ force: true });
    window.getPendingMessage = getPendingMessage;
    window.localMobileCommandResult = (text) => { const command = normalizeMobileCommand(window.MobileCommandActions?.parse?.(text)); return command ? makeMobileResult(command, 'local_mobile') : null; };
    window.AiAssistantViews = { open: switchView, current: () => currentView };
    window.AiAssistantRuntime = { version: '20260516-7', ask: askAssistant, immediateLocalResult, localFallbackResult, getLedgerContext, isBusy: () => aiBusy, getEndpoint: () => aiEndpoint, setChatWindowLimit(limit = CHAT_RENDER_LIMIT) { visibleChatLimit = Math.max(CHAT_RENDER_LIMIT, Number(limit) || CHAT_RENDER_LIMIT); renderChat({ force: true }); }, setRecordWindowLimit(limit = RECORD_RENDER_LIMIT) { visibleRecordLimit = Math.max(RECORD_RENDER_LIMIT, Number(limit) || RECORD_RENDER_LIMIT); renderList({ force: true }); } };
  }

  bindEvents();
  installViewportStability();
  exposeDebugApi();
  renderAll();
})();

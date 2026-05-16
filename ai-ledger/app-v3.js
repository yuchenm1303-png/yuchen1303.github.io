(() => {
  'use strict';

  /*
   * Ownership target:
   * This is the core app runtime file. It owns local records, chat state, rendering,
   * simple view routing, AI endpoint config, and chart/list updates.
   *
   * Refactor note:
   * Do not add visual redesign CSS or Android navigation compatibility here.
   * Later, this large file can be split into smaller modules such as:
   * records-store, chat-runtime, chart-renderer, and view-router.
   * For now, keep behavior unchanged and only mark boundaries.
   */

  const STORAGE_KEY = 'ai-ledger-records-v3';
  const CHAT_KEY = 'ai-ledger-chat-v2';
  const AI_CONFIG_KEY = 'ai-ledger-ai-config-v1';
  const DEFAULT_AI_CONFIG = window.AI_LEDGER_CONFIG || {};
  const DEFAULT_AI_ENDPOINT = DEFAULT_AI_CONFIG.aiEndpoint || '';
  const DEFAULT_AI_TIMEOUT_MS = Number(DEFAULT_AI_CONFIG.aiTimeoutMs) || 12000;
  const CHAT_RENDER_LIMIT = 60;
  const CHAT_RENDER_STEP = 40;
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
    clearChatInlineBtn: document.querySelector('#clearChatInlineBtn'),
    resetBtn: document.querySelector('#resetBtn'),
    budgetInput: document.querySelector('#budgetInput'),
    rangeBtns: document.querySelectorAll('.range-chip'),
    rangeText: document.querySelector('#rangeText'),
    budgetBadge: document.querySelector('#budgetBadge'),
    budgetProgress: document.querySelector('#budgetProgress'),
    budgetText: document.querySelector('#budgetText'),
    summaryIncome: document.querySelector('#summaryIncome'),
    summaryExpense: document.querySelector('#summaryExpense'),
    summaryBalance: document.querySelector('#summaryBalance'),
    metricIncome: document.querySelector('#metricIncome'),
    metricExpense: document.querySelector('#metricExpense'),
    trendChart: document.querySelector('#trendChart'),
    categoryChart: document.querySelector('#categoryChart'),
    toast: document.querySelector('#toast'),
    aiEndpointInput: document.querySelector('#aiEndpointInput'),
    saveAiEndpointBtn: document.querySelector('#saveAiEndpointBtn'),
    testAiEndpointBtn: document.querySelector('#testAiEndpointBtn'),
    resetAiEndpointBtn: document.querySelector('#resetAiEndpointBtn'),
    aiEndpointStatus: document.querySelector('#aiEndpointStatus'),
  };

  let records = loadRecords();
  let chatMessages = loadChatMessages();
  let budget = Number(localStorage.getItem('ai-ledger-budget') || 3000);
  let currentRange = 'month';
  let trendChart = null;
  let categoryChart = null;
  let aiEndpoint = loadAiEndpoint();
  let chatRenderLimit = Math.max(CHAT_RENDER_LIMIT, Number(localStorage.getItem('ai-ledger-chat-render-limit') || CHAT_RENDER_LIMIT));
  let keyboardBaselineHeight = window.visualViewport?.height || window.innerHeight;

  function money(value) {
    const amount = Number(value || 0);
    return `¥${amount.toFixed(2)}`;
  }

  function showToast(text) {
    if (!els.toast) return;
    els.toast.textContent = text;
    els.toast.classList.add('show');
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => els.toast.classList.remove('show'), 1800);
  }

  function loadRecords() {
    try {
      const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  function saveRecords() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(records));
    window.dispatchEvent(new CustomEvent('ai-ledger-records-changed', { detail: { records } }));
  }

  function loadChatMessages() {
    try {
      const parsed = JSON.parse(localStorage.getItem(CHAT_KEY) || '[]');
      return Array.isArray(parsed) && parsed.length ? parsed : initialChat;
    } catch {
      return initialChat;
    }
  }

  function saveChatMessages() {
    localStorage.setItem(CHAT_KEY, JSON.stringify(chatMessages));
    window.chatMessages = chatMessages;
  }

  function loadAiEndpoint() {
    try {
      const saved = JSON.parse(localStorage.getItem(AI_CONFIG_KEY) || '{}');
      return saved.endpoint || DEFAULT_AI_ENDPOINT;
    } catch {
      return DEFAULT_AI_ENDPOINT;
    }
  }

  function saveAiEndpoint(value) {
    aiEndpoint = String(value || '').trim();
    localStorage.setItem(AI_CONFIG_KEY, JSON.stringify({ endpoint: aiEndpoint, updatedAt: Date.now() }));
    updateAiEndpointStatus();
  }

  function resetAiEndpoint() {
    aiEndpoint = DEFAULT_AI_ENDPOINT;
    localStorage.removeItem(AI_CONFIG_KEY);
    if (els.aiEndpointInput) els.aiEndpointInput.value = aiEndpoint;
    updateAiEndpointStatus();
    showToast('已恢复默认接口');
  }

  function updateAiEndpointStatus(text) {
    if (els.aiEndpointInput && document.activeElement !== els.aiEndpointInput) {
      els.aiEndpointInput.value = aiEndpoint;
    }
    if (!els.aiEndpointStatus) return;
    if (text) {
      els.aiEndpointStatus.textContent = text;
      return;
    }
    if (aiEndpoint) {
      const source = aiEndpoint === DEFAULT_AI_ENDPOINT ? '默认接口' : '自定义接口';
      els.aiEndpointStatus.textContent = `${source}已配置：${aiEndpoint}`;
    } else {
      els.aiEndpointStatus.textContent = '未配置云端 AI，当前只使用本地识别。';
    }
  }

  function normalizeNumber(value) {
    const num = Number(String(value).replace(/[¥,，\s]/g, ''));
    return Number.isFinite(num) ? num : 0;
  }

  function parseNaturalRecord(text) {
    const raw = String(text || '').trim();
    const amountMatch = raw.match(/(?:¥|￥)?\s*(\d+(?:\.\d+)?)/);
    if (!amountMatch) return null;
    const amount = normalizeNumber(amountMatch[1]);
    if (!amount) return null;
    const lowered = raw.toLowerCase();
    const incomeKeywords = ['收入', '工资', '奖金', '报销', '转入', '收款', '赚', '到账'];
    const type = incomeKeywords.some((keyword) => lowered.includes(keyword)) ? 'income' : 'expense';
    const category = guessCategory(raw, type);
    const title = raw.replace(amountMatch[0], '').replace(/今天|刚刚|记一笔|记账|花了|消费|支出|收入/g, '').trim() || category;
    return { title, amount, type, category };
  }

  function guessCategory(text, type = 'expense') {
    const keywordMap = {
      餐饮: ['饭', '餐', '外卖', '早餐', '午饭', '晚饭', '夜宵', '面', '咖啡', '奶茶', '吃'],
      交通: ['地铁', '公交', '打车', '出租', '高铁', '车票', '油费', '停车'],
      购物: ['买', '购物', '衣服', '鞋', '淘宝', '京东', '拼多多'],
      居住: ['房租', '水电', '物业', '宽带', '燃气'],
      饮品: ['奶茶', '咖啡', '饮料', '可乐'],
      工资: ['工资', '奖金', '报销'],
      礼物: ['红包', '礼物', '转账'],
    };
    for (const [category, words] of Object.entries(keywordMap)) {
      if (words.some((word) => text.includes(word))) return category;
    }
    return type === 'income' ? '工资' : '其他';
  }

  function addRecord(record) {
    const finalRecord = {
      id: crypto.randomUUID ? crypto.randomUUID() : String(Date.now()),
      title: record.title || record.category || '未命名',
      amount: Number(record.amount || 0),
      type: record.type === 'income' ? 'income' : 'expense',
      category: record.category || guessCategory(record.title || '', record.type),
      createdAt: record.createdAt || new Date().toISOString(),
    };
    records.unshift(finalRecord);
    saveRecords();
    return finalRecord;
  }

  function periodFilter(range) {
    const now = new Date();
    const start = new Date(now);
    const end = new Date(now);
    if (range === 'month') {
      start.setDate(1); start.setHours(0, 0, 0, 0);
      end.setMonth(end.getMonth() + 1, 1); end.setHours(0, 0, 0, 0);
    } else if (range === 'lastMonth') {
      start.setMonth(start.getMonth() - 1, 1); start.setHours(0, 0, 0, 0);
      end.setDate(1); end.setHours(0, 0, 0, 0);
    } else if (range === '30days') {
      start.setDate(start.getDate() - 29); start.setHours(0, 0, 0, 0);
      end.setDate(end.getDate() + 1); end.setHours(0, 0, 0, 0);
    } else if (range === 'year') {
      start.setMonth(0, 1); start.setHours(0, 0, 0, 0);
      end.setFullYear(end.getFullYear() + 1, 0, 1); end.setHours(0, 0, 0, 0);
    }
    return records.filter((record) => {
      const date = new Date(record.createdAt);
      return date >= start && date < end;
    });
  }

  function summarize(list) {
    return list.reduce((acc, record) => {
      if (record.type === 'income') acc.income += record.amount;
      else acc.expense += record.amount;
      return acc;
    }, { income: 0, expense: 0 });
  }

  function updateStats() {
    const list = periodFilter(currentRange);
    const summary = summarize(list);
    const balance = summary.income - summary.expense;
    const today = records.filter((record) => new Date(record.createdAt).toDateString() === new Date().toDateString() && record.type === 'expense')
      .reduce((sum, record) => sum + record.amount, 0);

    if (els.todaySpend) els.todaySpend.textContent = money(today);
    if (els.monthIncome) els.monthIncome.textContent = money(summary.income);
    if (els.monthExpense) els.monthExpense.textContent = money(summary.expense);
    if (els.monthBalance) els.monthBalance.textContent = money(balance);
    if (els.aiTodayExpense) els.aiTodayExpense.textContent = money(today);
    if (els.aiMonthBalance) els.aiMonthBalance.textContent = money(balance);
    if (els.summaryIncome) els.summaryIncome.textContent = `+${money(summary.income)}`;
    if (els.summaryExpense) els.summaryExpense.textContent = `-${money(summary.expense)}`;
    if (els.summaryBalance) els.summaryBalance.textContent = money(balance);
    if (els.metricIncome) els.metricIncome.textContent = money(summary.income);
    if (els.metricExpense) els.metricExpense.textContent = money(summary.expense);
    if (els.budgetBadge) els.budgetBadge.textContent = money(budget);
    const used = budget ? Math.min(100, Math.round((summary.expense / budget) * 100)) : 0;
    if (els.budgetProgress) els.budgetProgress.style.width = `${used}%`;
    if (els.budgetText) els.budgetText.textContent = `预算已使用 ${used}%`;
    if (els.rangeText) {
      const labels = { month: '本月', lastMonth: '上月', '30days': '近30天', year: '本年' };
      els.rangeText.textContent = labels[currentRange] || '本月';
    }
    updateCharts(list);
  }

  function renderRecords() {
    if (!els.recordList) return;
    if (els.recordCount) els.recordCount.textContent = `${records.length} 条`;
    if (!records.length) {
      els.recordList.innerHTML = '<div class="empty-state">还没有记录。试试对我说“今天午饭28”。</div>';
      return;
    }
    els.recordList.innerHTML = records.map((record) => `
      <article class="record-item">
        <div class="record-main">
          <strong class="record-title">${escapeHtml(record.title)}</strong>
          <span>${escapeHtml(record.category)} · ${new Date(record.createdAt).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}</span>
        </div>
        <div class="record-side ${record.type}">${record.type === 'income' ? '+' : '-'}${money(record.amount)}</div>
        <button class="delete-btn" data-delete="${record.id}" aria-label="删除记录">×</button>
      </article>
    `).join('');
  }

  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[char]));
  }

  function updateCharts(list) {
    if (!window.Chart) return;
    const days = Array.from({ length: 7 }, (_, index) => {
      const date = new Date();
      date.setDate(date.getDate() - (6 - index));
      date.setHours(0, 0, 0, 0);
      return date;
    });
    const labels = days.map((date) => `${date.getMonth() + 1}/${date.getDate()}`);
    const incomeData = days.map((date) => sumByDay(list, date, 'income'));
    const expenseData = days.map((date) => sumByDay(list, date, 'expense'));

    if (els.trendChart) {
      const ctx = els.trendChart.getContext('2d');
      if (trendChart) trendChart.destroy();
      trendChart = new Chart(ctx, {
        type: 'line',
        data: {
          labels,
          datasets: [
            { label: '收入', data: incomeData, borderColor: '#73e7ff', backgroundColor: 'rgba(115,231,255,.12)', tension: .42, fill: true },
            { label: '支出', data: expenseData, borderColor: '#ffb5c7', backgroundColor: 'rgba(255,181,199,.12)', tension: .42, fill: true },
          ],
        },
        options: chartOptions(),
      });
    }

    if (els.categoryChart) {
      const expenses = list.filter((record) => record.type === 'expense');
      const categoryTotals = categories.map((category) => expenses.filter((record) => record.category === category).reduce((sum, record) => sum + record.amount, 0));
      const ctx = els.categoryChart.getContext('2d');
      if (categoryChart) categoryChart.destroy();
      categoryChart = new Chart(ctx, {
        type: 'doughnut',
        data: {
          labels: categories,
          datasets: [{ data: categoryTotals, backgroundColor: ['#73e7ff', '#8debd7', '#ffd166', '#ff8fab', '#cdb4db', '#a0c4ff', '#b9fbc0', '#d7dce8'], borderWidth: 0 }],
        },
        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { labels: { color: 'rgba(255,255,255,.72)' } } } },
      });
    }
  }

  function sumByDay(list, date, type) {
    return list.filter((record) => {
      const d = new Date(record.createdAt);
      return d.toDateString() === date.toDateString() && record.type === type;
    }).reduce((sum, record) => sum + record.amount, 0);
  }

  function chartOptions() {
    return {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { labels: { color: 'rgba(255,255,255,.70)' } } },
      scales: {
        x: { ticks: { color: 'rgba(255,255,255,.58)' }, grid: { color: 'rgba(255,255,255,.06)' } },
        y: { ticks: { color: 'rgba(255,255,255,.58)' }, grid: { color: 'rgba(255,255,255,.06)' } },
      },
    };
  }

  function makeId(prefix = 'msg') {
    if (crypto.randomUUID) return crypto.randomUUID();
    return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  function appendMessage(message) {
    chatMessages.push({ id: makeId('chat'), ...message });
    saveChatMessages();
    renderChat();
  }

  function ensureInitialChatShape() {
    if (!Array.isArray(chatMessages) || !chatMessages.length) chatMessages = initialChat;
    chatMessages = chatMessages.map((message, index) => ({
      id: message.id || makeId(`msg-${index}`),
      role: message.role || 'assistant',
      content: message.content || '',
      action: message.action || 'chat',
      records: Array.isArray(message.records) ? message.records : [],
      draftState: message.draftState || 'none',
      ...message,
    }));
    saveChatMessages();
  }

  function renderChat() {
    if (!els.chatMessages) return;
    ensureInitialChatShape();
    const hiddenCount = Math.max(0, chatMessages.length - chatRenderLimit);
    const visibleMessages = chatMessages.slice(hiddenCount);
    els.chatMessages.innerHTML = `${hiddenCount ? `<button class="load-more-chat" type="button" data-load-more-chat>显示更早的 ${hiddenCount} 条对话</button>` : ''}${visibleMessages.map((message) => {
      const actionText = message.action && message.action !== 'chat' ? `<span class="chat-action">${escapeHtml(actionLabel(message.action))}</span>` : '';
      const content = formatMessageContent(message.content);
      const commandCard = message.mobileCommand ? renderMobileCommandCard(message.mobileCommand) : '';
      const attachments = renderMessageAttachments(message.attachments || []);
      const pendingClass = message.pending ? ' pending' : '';
      const classes = ['chat-row', message.role === 'user' ? 'user' : 'assistant', pendingClass].join(' ');
      return `<div class="${classes}" data-message-id="${escapeHtml(message.id)}"><div class="chat-bubble"><div class="chat-response">${actionText}${content}${attachments}${commandCard}</div></div></div>`;
    }).join('')}`;
    bindMobileCommandButtons();
    bindLoadMoreChat();
    window.setTimeout(() => {
      els.chatMessages.scrollTop = els.chatMessages.scrollHeight;
      window.ChatSourceBadges?.refresh?.();
      window.ChatSourceBadges?.pinBottom?.('render-chat');
    }, 0);
  }

  function bindLoadMoreChat() {
    els.chatMessages?.querySelector('[data-load-more-chat]')?.addEventListener('click', () => {
      chatRenderLimit += CHAT_RENDER_STEP;
      localStorage.setItem('ai-ledger-chat-render-limit', String(chatRenderLimit));
      renderChat();
      showToast('已加载更早对话');
    });
  }

  function formatMessageContent(content) {
    return escapeHtml(content).replace(/\n/g, '<br>');
  }

  function renderMessageAttachments(attachments) {
    if (!Array.isArray(attachments) || !attachments.length) return '';
    return `<div class="message-attachments">${attachments.map((item) => {
      const isImage = String(item.mimeType || '').startsWith('image/');
      if (isImage && item.dataUrl) return `<div class="message-attachment image"><img src="${escapeHtml(item.dataUrl)}" alt="${escapeHtml(item.name || '图片')}"/><span>${escapeHtml(item.name || '图片')}</span></div>`;
      return `<div class="message-attachment"><span>📎</span><span>${escapeHtml(item.name || '附件')}</span></div>`;
    }).join('')}</div>`;
  }

  function actionLabel(action) {
    const map = { add: '已记账', query: '账单查询', suggest: 'AI建议', command: '手机任务', chat: '对话' };
    return map[action] || action;
  }

  function renderMobileCommandCard(command) {
    if (!command) return '';
    const safe = (value) => escapeHtml(value ?? '');
    const params = command.params || {};
    const titleMap = { alarm: '设置闹钟', reminder: '创建提醒', open_app: '打开应用', navigate: '地图导航', call: '拨打电话' };
    const details = [];
    if (params.timeText) details.push(`时间：${safe(params.timeText)}`);
    if (params.label) details.push(`标题：${safe(params.label)}`);
    if (params.appName) details.push(`应用：${safe(params.appName)}`);
    if (params.destination) details.push(`目的地：${safe(params.destination)}`);
    if (params.phone) details.push(`号码：${safe(params.phone)}`);
    const isPending = command.status === 'pending';
    const isPreference = command.commandKind === 'navigation_preference' || params.intent === 'navigation_preference' || Boolean(params.updates);
    const statusText = command.status === 'done' ? '已执行' : command.status === 'failed' ? '执行失败' : command.status === 'cancelled' ? '已取消' : isPreference ? '已保存' : '待确认';
    const actions = isPending && !isPreference ? `<div class="mobile-command-actions"><button data-mobile-run="${safe(command.id)}" type="button">执行</button><button data-mobile-cancel="${safe(command.id)}" type="button">取消</button></div>` : '';
    return `<article class="mobile-command-card" data-mobile-card="${safe(command.id)}"><div class="mobile-command-top"><strong>${safe(titleMap[command.type] || '手机任务')}</strong><span class="mobile-command-status ${safe(command.status || 'pending')}">${statusText}</span></div>${details.length ? `<div class="mobile-command-details">${details.join('<br>')}</div>` : ''}${actions}</article>`;
  }

  function findMobileCommand(commandId) {
    const message = chatMessages.find((item) => item.mobileCommand?.id === commandId);
    return message?.mobileCommand;
  }

  function updateMobileCommand(commandId, updates) {
    chatMessages = chatMessages.map((message) => message.mobileCommand?.id === commandId ? { ...message, mobileCommand: { ...message.mobileCommand, ...updates } } : message);
    saveChatMessages();
    renderChat();
  }

  async function runMobileCommand(commandId) {
    const command = findMobileCommand(commandId);
    if (!command) return;
    updateMobileCommand(commandId, { status: 'running' });
    try {
      if (!window.MobileCommandExecutor?.execute) throw new Error('当前设备未接入手机执行插件。');
      const result = await window.MobileCommandExecutor.execute(command);
      updateMobileCommand(commandId, { status: result?.ok ? 'done' : 'failed', result });
      showToast(result?.message || (result?.ok ? '已执行' : '执行失败'));
    } catch (error) {
      updateMobileCommand(commandId, { status: 'failed', error: String(error?.message || error) });
      showToast(String(error?.message || error));
    }
  }

  function bindMobileCommandButtons() {
    els.chatMessages?.querySelectorAll('[data-mobile-run]').forEach((button) => {
      button.addEventListener('click', () => runMobileCommand(button.dataset.mobileRun));
    });
    els.chatMessages?.querySelectorAll('[data-mobile-cancel]').forEach((button) => {
      button.addEventListener('click', () => updateMobileCommand(button.dataset.mobileCancel, { status: 'cancelled' }));
    });
  }

  function localAssistant(text) {
    const parsed = parseNaturalRecord(text);
    if (parsed) {
      const record = addRecord(parsed);
      const sign = record.type === 'income' ? '收入' : '支出';
      return { content: `已记录${sign}：${record.title} ${money(record.amount)}，分类为「${record.category}」。`, action: 'add', records: [record] };
    }

    if (/账单|花了多少|消费|统计|结余|收入|支出/.test(text)) {
      const monthSummary = summarize(periodFilter('month'));
      const balance = monthSummary.income - monthSummary.expense;
      return { content: `本月收入 ${money(monthSummary.income)}，支出 ${money(monthSummary.expense)}，当前结余 ${money(balance)}。`, action: 'query' };
    }

    if (/预算|省钱|建议|分析/.test(text)) {
      const monthSummary = summarize(periodFilter('month'));
      const used = budget ? Math.round((monthSummary.expense / budget) * 100) : 0;
      const content = used > 80
        ? `本月预算已使用 ${used}%，建议接下来优先控制餐饮和购物类支出。`
        : `本月预算已使用 ${used}%，整体还比较稳。可以继续保持记录习惯。`;
      return { content, action: 'suggest' };
    }

    const mobileCommand = window.MobileCommandParser?.parse?.(text);
    if (mobileCommand) {
      return {
        content: mobileCommand.previewText || '我理解为一个手机任务，请确认后执行。',
        action: 'command',
        mobileCommand,
        source: mobileCommand.commandKind === 'navigation_preference' ? 'navigation_preferences' : 'local_mobile',
      };
    }

    return null;
  }

  async function askCloud(text, attachments = []) {
    if (!aiEndpoint) return null;
    const controller = new AbortController();
    const timer = window.setTimeout(() => controller.abort(), DEFAULT_AI_TIMEOUT_MS);
    try {
      const response = await fetch(aiEndpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: text, messages: chatMessages.slice(-8), records: records.slice(0, 80), attachments }),
        signal: controller.signal,
      });
      window.clearTimeout(timer);
      if (!response.ok) throw new Error(`接口返回 ${response.status}`);
      const data = await response.json();
      if (!data) return null;
      let mobileCommand = data.mobileCommand || data.command || null;
      if (!mobileCommand && data.intent && window.MobileCommandParser?.fromCloudIntent) {
        mobileCommand = window.MobileCommandParser.fromCloudIntent(data.intent, data.params || data.arguments || {});
      }
      const normalizedCommand = window.MobileCommandParser?.normalize?.(mobileCommand) || mobileCommand;
      if (Array.isArray(data.records)) data.records.forEach((item) => addRecord(item));
      return {
        content: data.reply || data.content || data.message || '云端已处理完成。',
        action: normalizedCommand ? 'command' : (data.action || 'chat'),
        records: Array.isArray(data.records) ? data.records : [],
        mobileCommand: normalizedCommand,
        source: data.source,
        model: data.model,
        modelLabel: data.modelLabel,
        provider: data.provider,
        version: data.version,
      };
    } catch (error) {
      window.clearTimeout(timer);
      console.warn('[AI Ledger] Cloud AI failed:', error);
      return null;
    }
  }

  async function handleChatSubmit(event) {
    event.preventDefault();
    const text = els.aiInput?.value?.trim();
    const attachments = window.ChatAttachments?.consume?.() || [];
    if (!text && !attachments.length) return;
    if (els.aiInput) {
      els.aiInput.value = '';
      resizeTextarea();
    }

    const userText = text || (attachments.length ? '帮我看看这个附件。' : '');
    appendMessage({ role: 'user', content: userText, action: 'chat', attachments });
    setSending(true);
    const thinkingId = makeId('thinking');
    chatMessages.push({ id: thinkingId, role: 'assistant', content: '正在思考…', action: 'chat', pending: true, source: 'local' });
    saveChatMessages();
    renderChat();

    let result = localAssistant(userText);
    if (!result || attachments.length) {
      const cloudResult = await askCloud(userText, attachments);
      if (cloudResult) result = cloudResult;
    }
    if (!result) result = { content: '我已经收到啦。现在你可以让我记账、查账单、做预算建议，或者让我帮你设置提醒、打开应用、导航。', action: 'chat', source: 'builtin_profile' };

    chatMessages = chatMessages.filter((item) => item.id !== thinkingId);
    chatMessages.push({ id: makeId('assistant'), role: 'assistant', draftState: 'none', ...result });
    saveChatMessages();
    setSending(false);
    renderAll();
  }

  function setSending(isSending) {
    if (els.sendBtn) els.sendBtn.disabled = isSending;
    els.chatForm?.classList.toggle('is-sending', isSending);
  }

  function resizeTextarea() {
    if (!els.aiInput) return;
    els.aiInput.style.height = 'auto';
    els.aiInput.style.height = `${Math.min(160, Math.max(44, els.aiInput.scrollHeight))}px`;
  }

  async function testAiEndpoint() {
    if (!els.aiEndpointInput) return;
    const candidate = els.aiEndpointInput.value.trim();
    if (!candidate) {
      updateAiEndpointStatus('请先填写接口地址。');
      return;
    }
    updateAiEndpointStatus('正在测试连接…');
    const previous = aiEndpoint;
    aiEndpoint = candidate;
    const result = await askCloud('请用一句话回复：连接正常。');
    aiEndpoint = previous;
    if (result?.content) updateAiEndpointStatus(`连接成功：${result.content.slice(0, 40)}`);
    else updateAiEndpointStatus('连接失败，请检查 Worker 地址或网络。');
  }

  function switchView(viewName) {
    Object.entries(els.views).forEach(([name, view]) => {
      view?.classList.toggle('active', name === viewName);
    });
    els.navBtns.forEach((button) => button.classList.toggle('active', button.dataset.view === viewName));
    window.AiAssistantViews?.open?.(viewName);
    if (viewName === 'stats') updateStats();
    if (viewName === 'list') renderRecords();
  }

  function bindEvents() {
    els.navBtns.forEach((button) => {
      button.addEventListener('click', () => switchView(button.dataset.view));
    });
    document.querySelectorAll('[data-open-view]').forEach((button) => {
      button.addEventListener('click', () => switchView(button.dataset.openView));
    });
    els.sampleBtns.forEach((button) => button.addEventListener('click', () => {
      if (els.aiInput) {
        els.aiInput.value = button.dataset.sample || '';
        resizeTextarea();
        els.aiInput.focus();
      }
    }));
    els.chatForm?.addEventListener('submit', handleChatSubmit);
    els.aiInput?.addEventListener('input', resizeTextarea);

    els.addManualBtn?.addEventListener('click', () => els.addSheet?.classList.add('open'));
    els.closeSheetBtn?.addEventListener('click', () => els.addSheet?.classList.remove('open'));
    els.saveManualBtn?.addEventListener('click', () => {
      const amount = normalizeNumber(els.manualAmount?.value || 0);
      if (!amount) { showToast('请输入金额'); return; }
      addRecord({ title: els.manualTitle?.value || els.manualCategory?.value || '手动记录', amount, type: els.manualType?.value || 'expense', category: els.manualCategory?.value || '其他' });
      els.addSheet?.classList.remove('open');
      if (els.manualTitle) els.manualTitle.value = '';
      if (els.manualAmount) els.manualAmount.value = '';
      renderAll();
      showToast('已添加记录');
    });

    els.recordList?.addEventListener('click', (event) => {
      const id = event.target.closest('[data-delete]')?.dataset.delete;
      if (!id) return;
      records = records.filter((record) => record.id !== id);
      saveRecords();
      renderAll();
      showToast('已删除');
    });

    els.exportBtn?.addEventListener('click', () => {
      const blob = new Blob([JSON.stringify({ records, exportedAt: new Date().toISOString() }, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `ai-ledger-${new Date().toISOString().slice(0, 10)}.json`;
      link.click();
      URL.revokeObjectURL(url);
    });

    els.clearChatBtn?.addEventListener('click', () => {
      chatMessages = initialChat;
      chatRenderLimit = CHAT_RENDER_LIMIT;
      localStorage.setItem('ai-ledger-chat-render-limit', String(chatRenderLimit));
      saveChatMessages();
      renderChat();
      showToast('已清空聊天');
    });
    els.clearChatInlineBtn?.addEventListener('click', () => els.clearChatBtn?.click());

    els.resetBtn?.addEventListener('click', () => {
      if (!confirm('确定清空所有本地数据吗？')) return;
      records = [];
      chatMessages = initialChat;
      saveRecords();
      saveChatMessages();
      renderAll();
      showToast('已重置');
    });

    els.budgetInput?.addEventListener('change', () => {
      budget = normalizeNumber(els.budgetInput.value || 0);
      localStorage.setItem('ai-ledger-budget', String(budget));
      updateStats();
    });

    els.rangeBtns.forEach((button) => button.addEventListener('click', () => {
      currentRange = button.dataset.range;
      els.rangeBtns.forEach((item) => item.classList.toggle('active', item === button));
      updateStats();
    }));

    els.saveAiEndpointBtn?.addEventListener('click', () => {
      saveAiEndpoint(els.aiEndpointInput?.value || '');
      showToast('AI 接口已保存');
    });
    els.testAiEndpointBtn?.addEventListener('click', testAiEndpoint);
    els.resetAiEndpointBtn?.addEventListener('click', resetAiEndpoint);

    window.addEventListener('resize', handleViewportChange);
    window.visualViewport?.addEventListener('resize', handleViewportChange);
    window.visualViewport?.addEventListener('scroll', handleViewportChange);
  }

  function handleViewportChange() {
    const currentHeight = window.visualViewport?.height || window.innerHeight;
    const keyboardOpen = keyboardBaselineHeight - currentHeight > CHAT_KEYBOARD_GAP;
    document.body.classList.toggle('keyboard-open', keyboardOpen);
    document.documentElement.style.setProperty('--app-visual-vh', `${currentHeight}px`);
    if (!keyboardOpen) keyboardBaselineHeight = Math.max(keyboardBaselineHeight, currentHeight);
    window.setTimeout(() => {
      if (els.chatMessages) els.chatMessages.scrollTop = els.chatMessages.scrollHeight;
    }, 80);
  }

  function renderAll() {
    updateStats();
    renderRecords();
    renderChat();
    if (els.budgetInput) els.budgetInput.value = budget;
    updateAiEndpointStatus();
  }

  function exposeRuntime() {
    window.chatMessages = chatMessages;
    window.AiAssistantRuntime = {
      ask: async (text, options = {}) => {
        if (els.aiInput && !options.silentInput) els.aiInput.value = '';
        const fakeEvent = { preventDefault() {} };
        if (els.aiInput) els.aiInput.value = text;
        await handleChatSubmit(fakeEvent);
      },
      getRecords: () => records.slice(),
      getChatMessages: () => chatMessages.slice(),
      openView: switchView,
      addRecord,
      renderAll,
      setChatWindowLimit: (limit) => {
        chatRenderLimit = Math.max(CHAT_RENDER_LIMIT, Number(limit) || CHAT_RENDER_LIMIT);
        localStorage.setItem('ai-ledger-chat-render-limit', String(chatRenderLimit));
        renderChat();
      },
    };
    window.AiAssistantViews = { open: switchView };
  }

  function boot() {
    ensureInitialChatShape();
    exposeRuntime();
    bindEvents();
    handleViewportChange();
    renderAll();
  }

  boot();
})();
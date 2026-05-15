const STORAGE_KEY = "ai-ledger-records-v1";
const BUDGET_KEY = "ai-ledger-budget-v1";
const AI_ENDPOINT_KEY = "ai-ledger-ai-endpoint-v1";
const CHAT_KEY = "ai-ledger-chat-v2";
const DEFAULT_AI_CONFIG = window.AI_LEDGER_CONFIG || {};
const ALLOWED_CATEGORIES = ["餐饮", "交通", "购物", "居住", "饮品", "工资", "礼物", "其他"];

function todayISO() {
  return new Date().toISOString().slice(0, 10);
}

function daysAgoISO(days) {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}

function money(n) {
  return `¥${Number(n || 0).toFixed(2)}`;
}

function sameMonth(dateStr) {
  const now = new Date();
  const d = new Date(dateStr);
  return now.getFullYear() === d.getFullYear() && now.getMonth() === d.getMonth();
}

function formatDate(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}.${m}.${d}`;
}

function createId() {
  return crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

const seedRecords = [
  { id: "1", title: "午饭", amount: 28, type: "expense", category: "餐饮", date: todayISO() },
  { id: "2", title: "奶茶", amount: 16, type: "expense", category: "饮品", date: todayISO() },
  { id: "3", title: "公交", amount: 3, type: "expense", category: "交通", date: todayISO() },
  { id: "4", title: "兼职", amount: 180, type: "income", category: "工资", date: daysAgoISO(1) },
  { id: "5", title: "超市", amount: 46.5, type: "expense", category: "购物", date: daysAgoISO(2) },
  { id: "6", title: "晚饭", amount: 34, type: "expense", category: "餐饮", date: daysAgoISO(3) },
];

const initialChat = [
  {
    id: "welcome",
    role: "assistant",
    content: "你好，我是你的 AI 助手。你可以让我记账、查账单、设置提醒、打开应用，也可以直接和我聊天。",
    action: "chat",
    records: [],
    draftState: "none",
  },
];

function inferCategory(text) {
  const t = text.toLowerCase();
  if (/(饭|早餐|午餐|晚餐|外卖|面|米线|火锅|烧烤|餐)/.test(t)) return "餐饮";
  if (/(奶茶|咖啡|饮料|可乐|茶)/.test(t)) return "饮品";
  if (/(打车|出租|公交|地铁|高铁|火车|机票|加油)/.test(t)) return "交通";
  if (/(淘宝|京东|拼多多|买|衣服|鞋|超市|购物)/.test(t)) return "购物";
  if (/(房租|水电|物业|宿舍|宽带)/.test(t)) return "居住";
  if (/(工资|兼职|奖金|补贴|报销|收入)/.test(t)) return "工资";
  if (/(礼物|红包)/.test(t)) return "礼物";
  return "其他";
}

function inferType(text) {
  return /(收入|工资|兼职|奖金|报销|收到|进账)/.test(text) ? "income" : "expense";
}

function cleanTitle(text) {
  return text
    .replace(/今天|昨天|前天|花了|花费|消费|支出|收入|进账|收到|元|块钱|块/g, "")
    .replace(/[0-9.]/g, "")
    .replace(/[，,。；;、]/g, "")
    .trim() || "未命名账单";
}

function parseNaturalLanguage(input) {
  return input
    .split(/[，,。；;、\n]/)
    .map((s) => s.trim())
    .filter(Boolean)
    .map((part) => {
      const amountMatch = part.match(/(\d+(?:\.\d+)?)/);
      if (!amountMatch) return null;
      const amount = Number(amountMatch[1]);
      if (!Number.isFinite(amount) || amount <= 0) return null;
      return {
        id: createId(),
        title: cleanTitle(part),
        amount,
        type: inferType(part),
        category: inferCategory(part),
        date: /昨天/.test(part) ? daysAgoISO(1) : /前天/.test(part) ? daysAgoISO(2) : todayISO(),
      };
    })
    .filter(Boolean);
}

function normalizeRecord(record) {
  const amount = Number(record?.amount);
  if (!Number.isFinite(amount) || amount <= 0) return null;
  return {
    id: record?.id || createId(),
    title: String(record?.title || "未命名账单").trim().slice(0, 30) || "未命名账单",
    amount,
    type: record?.type === "income" ? "income" : "expense",
    category: ALLOWED_CATEGORIES.includes(record?.category) ? record.category : "其他",
    date: /^\d{4}-\d{2}-\d{2}$/.test(String(record?.date || "")) ? record.date : todayISO(),
  };
}

function normalizeRecords(list) {
  return (Array.isArray(list) ? list : []).map(normalizeRecord).filter(Boolean);
}

function normalizeMobileCommand(command) {
  if (!command || typeof command !== "object") return null;
  const type = String(command.type || "").trim();
  const params = command.params && typeof command.params === "object" ? command.params : {};

  if (type === "set_alarm") {
    const hour = Number(params.hour);
    const minute = Number(params.minute || 0);
    if (!Number.isInteger(hour) || !Number.isInteger(minute) || hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
    const label = String(params.label || "提醒").trim() || "提醒";
    return {
      id: command.id || createId(),
      type,
      title: "设置闹钟",
      summary: command.summary || `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`,
      params: {
        date: /^\d{4}-\d{2}-\d{2}$/.test(String(params.date || "")) ? params.date : todayISO(),
        hour,
        minute,
        label,
      },
    };
  }

  if (type === "open_app") {
    const appName = String(params.appName || command.summary || "").trim();
    if (!appName) return null;
    return {
      id: command.id || createId(),
      type,
      title: "打开应用",
      summary: appName,
      params: { appName, packageName: String(params.packageName || "").trim() },
    };
  }

  if (type === "navigate") {
    const destination = String(params.destination || command.summary || "").replace(/^到\s*/, "").trim();
    if (!destination) return null;
    const mode = ["driving", "walking", "riding"].includes(params.mode) ? params.mode : "driving";
    return {
      id: command.id || createId(),
      type,
      title: "百度地图导航",
      summary: `到 ${destination}`,
      params: { appName: "百度地图", destination, mode },
    };
  }

  return null;
}

function getRecords() {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return seedRecords;
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.map(normalizeRecord).filter(Boolean) : seedRecords;
  } catch {
    return seedRecords;
  }
}

function saveRecords(list) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
}

function getBudget() {
  const raw = localStorage.getItem(BUDGET_KEY);
  return raw ? Number(raw) : 3000;
}

function saveBudget(value) {
  localStorage.setItem(BUDGET_KEY, String(value));
}

function getChatMessages() {
  const raw = localStorage.getItem(CHAT_KEY);
  if (!raw) return [...initialChat];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) && parsed.length ? parsed : [...initialChat];
  } catch {
    return [...initialChat];
  }
}

function saveChatMessages() {
  localStorage.setItem(CHAT_KEY, JSON.stringify(chatMessages.slice(-60)));
}

function normalizeEndpoint(value) {
  return String(value || "").trim().replace(/\/$/, "");
}

function getAiEndpoint() {
  const saved = normalizeEndpoint(localStorage.getItem(AI_ENDPOINT_KEY));
  return saved || normalizeEndpoint(DEFAULT_AI_CONFIG.aiEndpoint);
}

function saveAiEndpoint(value) {
  const endpoint = normalizeEndpoint(value);
  if (endpoint) localStorage.setItem(AI_ENDPOINT_KEY, endpoint);
  else localStorage.removeItem(AI_ENDPOINT_KEY);
  return endpoint;
}

let records = getRecords();
let budget = getBudget();
let chatMessages = getChatMessages();
let renderedChatIds = new Set(chatMessages.map((message) => message.id));
let currentView = "ai";
let currentRange = "month";
let aiEndpoint = getAiEndpoint();
let trendChart;
let categoryChart;

const els = {
  views: {
    ai: document.querySelector("#view-ai"),
    tools: document.querySelector("#view-tools"),
    stats: document.querySelector("#view-stats"),
    list: document.querySelector("#view-list"),
    settings: document.querySelector("#view-settings"),
  },
  navBtns: document.querySelectorAll(".nav-btn"),
  rangeChips: document.querySelectorAll(".range-chip"),
  rangeText: document.querySelector("#rangeText"),
  chatMessages: document.querySelector("#chatMessages"),
  chatForm: document.querySelector("#chatForm"),
  aiInput: document.querySelector("#aiInput"),
  aiAddBtn: document.querySelector("#aiAddBtn"),
  aiModeBadge: document.querySelector("#aiModeBadge"),
  aiModeHint: document.querySelector("#aiModeHint"),
  sampleBtns: document.querySelectorAll(".sample-btn"),
  aiTodayExpense: document.querySelector("#aiTodayExpense"),
  aiMonthBalance: document.querySelector("#aiMonthBalance"),
  summaryBalance: document.querySelector("#summaryBalance"),
  summaryIncome: document.querySelector("#summaryIncome"),
  summaryExpense: document.querySelector("#summaryExpense"),
  metricIncome: document.querySelector("#metricIncome"),
  metricExpense: document.querySelector("#metricExpense"),
  budgetBadge: document.querySelector("#budgetBadge"),
  budgetProgress: document.querySelector("#budgetProgress"),
  budgetText: document.querySelector("#budgetText"),
  recordCount: document.querySelector("#recordCount"),
  recordList: document.querySelector("#recordList"),
  budgetInput: document.querySelector("#budgetInput"),
  aiEndpointInput: document.querySelector("#aiEndpointInput"),
  aiEndpointStatus: document.querySelector("#aiEndpointStatus"),
  saveAiEndpointBtn: document.querySelector("#saveAiEndpointBtn"),
  testAiEndpointBtn: document.querySelector("#testAiEndpointBtn"),
  clearChatBtn: document.querySelector("#clearChatBtn"),
  exportBtn: document.querySelector("#exportBtn"),
  resetBtn: document.querySelector("#resetBtn"),
  fabAdd: document.querySelector("#fabAdd"),
  sheetMask: document.querySelector("#sheetMask"),
  addSheet: document.querySelector("#addSheet"),
  closeSheetBtn: document.querySelector("#closeSheetBtn"),
  manualTitle: document.querySelector("#manualTitle"),
  manualAmount: document.querySelector("#manualAmount"),
  manualType: document.querySelector("#manualType"),
  manualCategory: document.querySelector("#manualCategory"),
  manualAddBtn: document.querySelector("#manualAddBtn"),
  toast: document.querySelector("#toast"),
};

function showToast(message) {
  els.toast.textContent = message;
  els.toast.classList.add("show");
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => els.toast.classList.remove("show"), 2800);
}

function setAiLoading(isLoading) {
  els.aiAddBtn.disabled = isLoading;
  els.aiAddBtn.classList.toggle("loading", isLoading);
  els.aiAddBtn.textContent = isLoading ? "…" : "➤";
}

function setAiStatus(message, tone = "normal") {
  els.aiEndpointStatus.textContent = message;
  els.aiEndpointStatus.dataset.tone = tone;
}

function updateAiModeUI() {
  aiEndpoint = getAiEndpoint();
  els.aiEndpointInput.value = aiEndpoint;
  const cloudEnabled = Boolean(aiEndpoint);
  els.aiModeBadge.textContent = cloudEnabled ? "☁️ 云端 AI 识别" : "✨ 本地智能识别";
  els.aiModeHint.textContent = cloudEnabled
    ? "现在可以像聊天一样交流、补充、修改，也可以直接问我账本。"
    : "未配置云端 AI 时，会自动使用本地规则识别。";
  setAiStatus(cloudEnabled ? `当前接口：${aiEndpoint}` : "未配置时，App 会自动使用本地规则识别。", "normal");
}

function getMonthlyStats() {
  const monthRecords = records.filter((r) => sameMonth(r.date));
  const monthExpense = monthRecords.filter((r) => r.type === "expense").reduce((s, r) => s + r.amount, 0);
  const monthIncome = monthRecords.filter((r) => r.type === "income").reduce((s, r) => s + r.amount, 0);
  const todayExpense = records.filter((r) => r.date === todayISO() && r.type === "expense").reduce((s, r) => s + r.amount, 0);
  return { monthExpense, monthIncome, todayExpense, monthBalance: monthIncome - monthExpense };
}

function getLedgerContext() {
  const stats = getMonthlyStats();
  return {
    summary: stats,
    recentRecords: [...records].sort((a, b) => b.date.localeCompare(a.date)).slice(0, 60),
  };
}

function getRangeBounds() {
  const now = new Date();
  let start;
  let end;
  if (currentRange === "lastMonth") {
    start = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    end = new Date(now.getFullYear(), now.getMonth(), 0);
  } else if (currentRange === "30days") {
    start = new Date(now);
    start.setDate(now.getDate() - 29);
    end = new Date(now);
  } else if (currentRange === "year") {
    start = new Date(now.getFullYear(), 0, 1);
    end = new Date(now.getFullYear(), 11, 31);
  } else {
    start = new Date(now.getFullYear(), now.getMonth(), 1);
    end = new Date(now.getFullYear(), now.getMonth() + 1, 0);
  }
  return { start, end };
}

function getFilteredRecords() {
  const { start, end } = getRangeBounds();
  return records.filter((record) => {
    const d = new Date(record.date);
    return d >= start && d <= end;
  });
}

function summarize(list) {
  const income = list.filter((r) => r.type === "income").reduce((s, r) => s + r.amount, 0);
  const expense = list.filter((r) => r.type === "expense").reduce((s, r) => s + r.amount, 0);
  return { income, expense, balance: income - expense };
}

function buildTrendData() {
  const labels = [];
  const expenseData = [];
  const incomeData = [];
  for (let i = 6; i >= 0; i -= 1) {
    const date = daysAgoISO(i);
    labels.push(date.slice(5));
    expenseData.push(records.filter((r) => r.date === date && r.type === "expense").reduce((s, r) => s + r.amount, 0));
    incomeData.push(records.filter((r) => r.date === date && r.type === "income").reduce((s, r) => s + r.amount, 0));
  }
  return { labels, expenseData, incomeData };
}

function buildCategoryData(list) {
  const map = {};
  list.filter((r) => r.type === "expense").forEach((r) => {
    map[r.category] = (map[r.category] || 0) + r.amount;
  });
  return { labels: Object.keys(map), data: Object.values(map) };
}

function recordMarkup(record) {
  return `
    <article class="record-item">
      <div class="record-main">
        <div class="record-title">${escapeHtml(record.title)}</div>
        <div class="record-meta">${record.date} · ${record.category}</div>
      </div>
      <div class="record-side">
        <span class="record-amount ${record.type}">${record.type === "income" ? "+" : "-"}${money(record.amount)}</span>
        <button class="delete-btn" data-id="${record.id}" aria-label="删除">×</button>
      </div>
    </article>
  `;
}

function getPendingMessage() {
  return [...chatMessages].reverse().find((message) => message.role === "assistant" && message.draftState === "pending");
}

function draftMarkup(message) {
  if (!message.records?.length) return "";
  const title = message.draftState === "confirmed"
    ? "已保存账单"
    : message.draftState === "cancelled"
      ? "已取消"
      : message.draftState === "superseded"
        ? "已被新草稿替代"
        : "待确认账单";
  const items = message.records.map((record) => `
    <div class="draft-item">
      <div class="draft-item-main">
        <div class="draft-item-title">${escapeHtml(record.title)}</div>
        <div class="draft-item-meta">${record.date} · ${record.category}</div>
      </div>
      <div class="draft-item-amount ${record.type}">${record.type === "income" ? "+" : "-"}${money(record.amount)}</div>
    </div>
  `).join("");
  const actions = message.draftState === "pending"
    ? `
      <div class="draft-actions">
        <button class="confirm-btn" data-chat-action="confirm" data-message-id="${message.id}">确认记账</button>
        <button class="cancel-btn" data-chat-action="cancel" data-message-id="${message.id}">先不保存</button>
      </div>
    `
    : "";
  return `
    <div class="draft-card ${message.draftState || ""}">
      <div class="draft-head">
        <span class="draft-title">${title}</span>
        <span class="draft-state">${message.records.length} 笔</span>
      </div>
      <div class="draft-list">${items}</div>
      ${actions}
    </div>
  `;
}

function mobileCommandMarkup(message) {
  if (!message.mobileCommand || !window.MobileCommandActions?.renderCard) return "";
  return window.MobileCommandActions.renderCard(message.mobileCommand);
}

function renderChat() {
  els.chatMessages.innerHTML = chatMessages.map((message) => {
    const enterClass = renderedChatIds.has(message.id) ? "" : " new-message";
    if (message.role === "user") {
      return `<div class="chat-row user${enterClass}" data-message-id="${escapeHtml(message.id)}"><div class="chat-bubble">${escapeHtml(message.content)}</div></div>`;
    }
    return `
      <div class="chat-row assistant${enterClass}" data-message-id="${escapeHtml(message.id)}">
        <div class="chat-response">
          <div class="chat-bubble">${escapeHtml(message.content)}</div>
          ${draftMarkup(message)}
          ${mobileCommandMarkup(message)}
        </div>
      </div>
    `;
  }).join("");
  chatMessages.forEach((message) => renderedChatIds.add(message.id));
  els.chatMessages.scrollTop = els.chatMessages.scrollHeight;
}

function renderTyping() {
  els.chatMessages.insertAdjacentHTML("beforeend", `<div id="typingRow" class="chat-row assistant typing-row new-message"><div class="chat-bubble typing"><span></span><span></span><span></span></div></div>`);
  els.chatMessages.scrollTop = els.chatMessages.scrollHeight;
}

function removeTyping() {
  document.querySelector("#typingRow")?.remove();
}

function renderAI() {
  const stats = getMonthlyStats();
  els.aiTodayExpense.textContent = money(stats.todayExpense);
  els.aiMonthBalance.textContent = money(stats.monthBalance);
  renderChat();
}

function renderStats() {
  const filtered = getFilteredRecords();
  const stats = summarize(filtered);
  const { start, end } = getRangeBounds();
  const rate = budget > 0 ? Math.min((stats.expense / budget) * 100, 100) : 0;
  els.rangeText.textContent = `${formatDate(start)} - ${formatDate(end)}`;
  els.summaryBalance.textContent = money(stats.balance);
  els.summaryIncome.textContent = `+${money(stats.income)}`;
  els.summaryExpense.textContent = `-${money(stats.expense)}`;
  els.metricIncome.textContent = money(stats.income);
  els.metricExpense.textContent = money(stats.expense);
  els.budgetBadge.textContent = money(budget);
  els.budgetProgress.style.width = `${rate}%`;
  els.budgetText.textContent = `预算已使用 ${rate.toFixed(0)}%`;
  els.budgetInput.value = budget;
}

function renderList() {
  const sorted = [...records].sort((a, b) => b.date.localeCompare(a.date));
  els.recordCount.textContent = `${sorted.length} 条`;
  els.recordList.innerHTML = sorted.map(recordMarkup).join("") || `<p class="subtext">还没有账单。</p>`;
}

function renderCharts() {
  const trend = buildTrendData();
  const category = buildCategoryData(getFilteredRecords());
  if (trendChart) trendChart.destroy();
  if (categoryChart) categoryChart.destroy();

  trendChart = new Chart(document.querySelector("#trendChart"), {
    type: "line",
    data: {
      labels: trend.labels,
      datasets: [
        {
          label: "支出",
          data: trend.expenseData,
          borderColor: "#0b8f8b",
          backgroundColor: "rgba(11,143,139,.18)",
          tension: .35,
          fill: true,
          pointRadius: 2,
        },
        {
          label: "收入",
          data: trend.incomeData,
          borderColor: "#86ece2",
          backgroundColor: "rgba(134,236,226,.20)",
          tension: .35,
          fill: true,
          pointRadius: 2,
        },
      ],
    },
    options: {
      animation: false,
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { labels: { color: "#607083" } } },
      scales: {
        y: { beginAtZero: true, ticks: { color: "#607083" }, grid: { color: "rgba(16,32,50,.08)" } },
        x: { ticks: { color: "#607083" }, grid: { display: false } },
      },
    },
  });

  categoryChart = new Chart(document.querySelector("#categoryChart"), {
    type: "doughnut",
    data: {
      labels: category.labels,
      datasets: [{
        data: category.data,
        backgroundColor: ["#086a73", "#0b8f8b", "#86ece2", "#6ec7f4", "#a9efe7", "#53b9b3", "#b8e5ff"],
        borderWidth: 0,
      }],
    },
    options: {
      animation: false,
      responsive: true,
      maintainAspectRatio: false,
      cutout: "64%",
      plugins: { legend: { position: "bottom", labels: { color: "#607083", boxWidth: 12, padding: 16 } } },
    },
  });
}

function renderAll() {
  renderAI();
  renderStats();
  renderList();
  updateAiModeUI();
  if (currentView === "stats") renderCharts();
}

function getTopLevelView(name) {
  return name === "stats" || name === "list" ? "tools" : name;
}

function switchView(name) {
  if (!els.views[name]) name = "ai";
  currentView = name;
  Object.entries(els.views).forEach(([key, el]) => el.classList.toggle("active", key === name));
  const topLevelView = getTopLevelView(name);
  els.navBtns.forEach((btn) => btn.classList.toggle("active", btn.dataset.view === topLevelView));
  if (name === "stats") {
    renderStats();
    requestAnimationFrame(renderCharts);
  }
  if (name === "ai") renderAI();
  if (name === "list") renderList();
  if (name === "settings") updateAiModeUI();
  if (name === "tools") window.dispatchEvent(new CustomEvent("ai-tools-home"));
}

window.AiAssistantViews = {
  open: switchView,
  current: () => currentView,
};

function openSheet() {
  if (!els.addSheet) return;
  document.body.classList.add("sheet-open");
  els.addSheet.setAttribute("aria-hidden", "false");
}

function closeSheet() {
  if (!els.addSheet) return;
  document.body.classList.remove("sheet-open");
  els.addSheet.setAttribute("aria-hidden", "true");
}

function addManualRecord() {
  if (!els.manualTitle || !els.manualAmount || !els.manualType || !els.manualCategory) return;
  const title = els.manualTitle.value.trim();
  const amount = Number(els.manualAmount.value);
  if (!title || !Number.isFinite(amount) || amount <= 0) {
    showToast("标题和金额要填完整哦");
    return;
  }
  records = [{
    id: createId(),
    title,
    amount,
    type: els.manualType.value,
    category: els.manualCategory.value,
    date: todayISO(),
  }, ...records];
  saveRecords(records);
  els.manualTitle.value = "";
  els.manualAmount.value = "";
  closeSheet();
  renderAll();
  showToast("已添加一条账单");
}

function removeRecord(id) {
  records = records.filter((record) => record.id !== id);
  saveRecords(records);
  renderAll();
  showToast("已删除该账单");
}

function exportRecords() {
  const blob = new Blob([JSON.stringify(records, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "ai-ledger-records.json";
  link.click();
  URL.revokeObjectURL(url);
}

function conversationPayload() {
  return chatMessages
    .filter((message) => message.role === "user" || message.role === "assistant")
    .slice(-16)
    .map((message) => ({ role: message.role, content: message.content }));
}

async function fetchJsonWithTimeout(url, options, timeoutMs = 12000) {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, { ...options, signal: controller.signal });
    const text = await response.text();
    let data = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      data = null;
    }
    return { response, data, text };
  } finally {
    window.clearTimeout(timer);
  }
}

function formatCloudError(result) {
  const data = result?.data || {};
  if (data.providerStatus) {
    return `云端 AI 调用失败：${data.providerCode || data.code || `HTTP ${data.providerStatus}`}`;
  }
  if (data.code) return `云端 AI 调用失败：${data.code}`;
  return "云端 AI 暂时不可用";
}

async function askCloudAI() {
  if (!aiEndpoint) return null;
  const pending = getPendingMessage();
  const result = await fetchJsonWithTimeout(aiEndpoint, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      messages: conversationPayload(),
      pendingDraft: pending?.records || [],
      ledgerContext: getLedgerContext(),
      clientTools: window.MobileCommandActions?.tools || [],
      now: todayISO(),
    }),
  }, Number(DEFAULT_AI_CONFIG.aiTimeoutMs) || 12000);

  if (!result.response.ok) {
    const error = new Error(formatCloudError(result));
    error.cloudResult = result;
    throw error;
  }

  const mobileCommand = normalizeMobileCommand(result.data?.mobileCommand);
  const action = result.data?.action === "mobile_command" && !mobileCommand
    ? "chat"
    : result.data?.action || "chat";

  return {
    reply: String(result.data?.reply || "").trim(),
    action,
    records: normalizeRecords(result.data?.records),
    mobileCommand,
    source: "cloud_ai",
    version: result.data?.version,
  };
}

function localChatFallback(text) {
  const pending = getPendingMessage();
  if (pending && /^(好|好的|对|确认|保存|记上|就这样)$/u.test(text)) {
    return { reply: "好的，已帮你记上。", action: "confirm_pending", records: [], source: "local" };
  }
  if (pending && /^(算了|不用了|先别记|取消)$/u.test(text)) {
    return { reply: "好的，这次先不保存。", action: "cancel_pending", records: [], source: "local" };
  }
  const mobileCommand = normalizeMobileCommand(window.MobileCommandActions?.parse?.(text));
  if (mobileCommand) {
    return {
      reply: window.MobileCommandActions?.createReply?.(mobileCommand) || "我整理好了这个手机动作，确认后我再执行。",
      action: "mobile_command",
      records: [],
      mobileCommand,
      source: "local_mobile",
    };
  }
  const parsed = parseNaturalLanguage(text);
  if (parsed.length) {
    return { reply: `我先整理出 ${parsed.length} 笔待确认账单，你回复“好”我就帮你保存。`, action: "draft", records: parsed, source: "local" };
  }
  if (/(帮我记|记一笔|记账)/.test(text) && /(饭|早餐|午餐|晚餐|外卖|火锅|奶茶|咖啡|地铁|公交|打车|工资|兼职)/.test(text)) {
    const item = cleanTitle(text);
    return { reply: `${item}花了多少钱？`, action: "chat", records: [], source: "local" };
  }
  return { reply: "我还没听清要记什么。你可以继续说，我会跟着问。", action: "chat", records: [], source: "local" };
}

async function askAssistant(text) {
  chatMessages.push({ id: createId(), role: "user", content: text });
  saveChatMessages();
  renderChat();
  renderTyping();
  setAiLoading(true);

  let result;
  if (aiEndpoint) {
    try {
      result = await askCloudAI();
      setAiStatus(`云端 AI 已连接${result.version ? ` · ${result.version}` : ""}`, "success");
    } catch (error) {
      console.warn("Cloud AI failed, falling back to local chat:", error);
      setAiStatus(error.message || "云端 AI 暂时不可用，已自动改用本地识别。", "error");
      showToast(error.message || "云端 AI 暂时不可用，已自动改用本地识别");
    }
  }
  if (!result) result = localChatFallback(text);

  removeTyping();
  setAiLoading(false);

  const pending = getPendingMessage();
  if (result.action === "confirm_pending" && pending) {
    confirmDraft(pending.id, false);
  } else if (result.action === "cancel_pending" && pending) {
    pending.draftState = "cancelled";
  } else if (result.action === "draft" && pending) {
    pending.draftState = "superseded";
  }

  chatMessages.push({
    id: createId(),
    role: "assistant",
    content: result.reply || "我在。",
    action: result.action,
    records: result.action === "draft" ? result.records : [],
    draftState: result.action === "draft" && result.records.length ? "pending" : "none",
    mobileCommand: result.action === "mobile_command" ? result.mobileCommand : null,
    source: result.source,
  });
  saveChatMessages();
  renderAll();
}

function confirmDraft(messageId, announce = true) {
  const message = chatMessages.find((item) => item.id === messageId);
  if (!message || message.draftState !== "pending" || !message.records?.length) return;
  records = [...message.records.map((record) => ({ ...record, id: createId() })), ...records];
  message.draftState = "confirmed";
  saveRecords(records);
  saveChatMessages();
  if (announce) showToast(`已保存 ${message.records.length} 条账单`);
}

function cancelDraft(messageId) {
  const message = chatMessages.find((item) => item.id === messageId);
  if (!message || message.draftState !== "pending") return;
  message.draftState = "cancelled";
  saveChatMessages();
  renderChat();
  showToast("已取消这次记账");
}

function clearChat() {
  chatMessages = [...initialChat];
  saveChatMessages();
  renderChat();
  showToast("已清空聊天记录");
}

async function testAiEndpoint() {
  const endpoint = normalizeEndpoint(els.aiEndpointInput.value);
  if (!endpoint) {
    showToast("请先填写 AI 接口地址");
    return;
  }

  els.testAiEndpointBtn.disabled = true;
  els.testAiEndpointBtn.textContent = "测试中...";
  setAiStatus("正在检测云端 AI…", "normal");

  try {
    const health = await fetchJsonWithTimeout(`${endpoint}/health`, { method: "GET" }, 8000);
    if (!health.response.ok || !health.data?.ok) {
      throw new Error(`Worker 健康检查失败：HTTP ${health.response.status}`);
    }

    const chat = await fetchJsonWithTimeout(endpoint, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ messages: [{ role: "user", content: "你好" }], now: todayISO() }),
    }, 12000);
    if (!chat.response.ok) throw new Error(formatCloudError(chat));

    const label = `AI 接口连接成功 · ${health.data.version || chat.data?.version || "已连通"}`;
    setAiStatus(label, "success");
    showToast("AI 接口连接成功");
  } catch (error) {
    setAiStatus(error.message || "AI 接口连接失败", "error");
    showToast(error.message || "AI 接口连接失败");
  } finally {
    els.testAiEndpointBtn.disabled = false;
    els.testAiEndpointBtn.textContent = "测试连接";
  }
}

els.navBtns.forEach((button) => button.addEventListener("click", () => switchView(button.dataset.view)));

document.addEventListener("click", (event) => {
  const button = event.target.closest("[data-open-view]");
  if (!button) return;
  switchView(button.dataset.openView);
});

els.rangeChips.forEach((button) => button.addEventListener("click", () => {
  currentRange = button.dataset.range;
  els.rangeChips.forEach((item) => item.classList.toggle("active", item === button));
  renderStats();
  renderCharts();
}));

els.sampleBtns.forEach((button) => button.addEventListener("click", () => {
  els.aiInput.value = button.dataset.sample;
  els.aiInput.focus();
}));

els.chatForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const text = els.aiInput.value.trim();
  if (!text) {
    showToast("先说一句吧");
    return;
  }
  els.aiInput.value = "";
  els.aiInput.style.height = "auto";
  await askAssistant(text);
});

els.aiInput.addEventListener("input", () => {
  els.aiInput.style.height = "auto";
  els.aiInput.style.height = `${Math.min(els.aiInput.scrollHeight, 120)}px`;
});

els.chatMessages.addEventListener("click", (event) => {
  const actionButton = event.target.closest("[data-chat-action]");
  if (!actionButton) return;
  if (actionButton.dataset.chatAction === "confirm") {
    confirmDraft(actionButton.dataset.messageId);
    renderAll();
  }
  if (actionButton.dataset.chatAction === "cancel") {
    cancelDraft(actionButton.dataset.messageId);
  }
});

els.fabAdd?.addEventListener("click", openSheet);
els.closeSheetBtn?.addEventListener("click", closeSheet);
els.sheetMask?.addEventListener("click", closeSheet);
els.manualAddBtn?.addEventListener("click", addManualRecord);

els.recordList.addEventListener("click", (event) => {
  const button = event.target.closest("[data-id]");
  if (!button) return;
  removeRecord(button.dataset.id);
});

els.budgetInput.addEventListener("input", () => {
  budget = Math.max(Number(els.budgetInput.value) || 0, 0);
  saveBudget(budget);
  renderStats();
});

els.saveAiEndpointBtn.addEventListener("click", () => {
  aiEndpoint = saveAiEndpoint(els.aiEndpointInput.value);
  updateAiModeUI();
  showToast(aiEndpoint ? "已保存 AI 接口" : "已关闭云端 AI");
});

els.testAiEndpointBtn.addEventListener("click", testAiEndpoint);
els.clearChatBtn.addEventListener("click", clearChat);
els.exportBtn.addEventListener("click", exportRecords);
els.resetBtn.addEventListener("click", () => {
  if (!window.confirm("确定要清空全部账单吗？")) return;
  records = [];
  saveRecords(records);
  renderAll();
  showToast("已清空全部账单");
});

if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => navigator.serviceWorker.register("./sw.js"));
}

renderAll();
switchView("ai");

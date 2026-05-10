const STORAGE_KEY = "ai-ledger-records-v1";
const BUDGET_KEY = "ai-ledger-budget-v1";
const AI_ENDPOINT_KEY = "ai-ledger-ai-endpoint-v1";
const DEFAULT_AI_CONFIG = window.AI_LEDGER_CONFIG || {};

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

const seedRecords = [
  { id: "1", title: "午饭", amount: 28, type: "expense", category: "餐饮", date: todayISO() },
  { id: "2", title: "奶茶", amount: 16, type: "expense", category: "饮品", date: todayISO() },
  { id: "3", title: "公交", amount: 3, type: "expense", category: "交通", date: todayISO() },
  { id: "4", title: "兼职", amount: 180, type: "income", category: "工资", date: daysAgoISO(1) },
  { id: "5", title: "超市", amount: 46.5, type: "expense", category: "购物", date: daysAgoISO(2) },
  { id: "6", title: "晚饭", amount: 34, type: "expense", category: "餐饮", date: daysAgoISO(3) },
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

function getRecords() {
  const raw = localStorage.getItem(STORAGE_KEY);
  return raw ? JSON.parse(raw) : seedRecords;
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

function normalizeEndpoint(value) {
  return String(value || "").trim().replace(/\/$/, "");
}

function getAiEndpoint() {
  const saved = normalizeEndpoint(localStorage.getItem(AI_ENDPOINT_KEY));
  return saved || normalizeEndpoint(DEFAULT_AI_CONFIG.aiEndpoint);
}

function saveAiEndpoint(value) {
  const endpoint = normalizeEndpoint(value);
  if (endpoint) {
    localStorage.setItem(AI_ENDPOINT_KEY, endpoint);
  } else {
    localStorage.removeItem(AI_ENDPOINT_KEY);
  }
  return endpoint;
}

function normalizeCloudRecord(record) {
  const amount = Number(record?.amount);
  if (!Number.isFinite(amount) || amount <= 0) return null;
  return {
    id: createId(),
    title: String(record?.title || "未命名账单").trim().slice(0, 30) || "未命名账单",
    amount,
    type: record?.type === "income" ? "income" : "expense",
    category: ["餐饮", "交通", "购物", "居住", "饮品", "工资", "礼物", "其他"].includes(record?.category)
      ? record.category
      : "其他",
    date: /^\d{4}-\d{2}-\d{2}$/.test(String(record?.date || "")) ? record.date : todayISO(),
  };
}

function addRecordIds(list) {
  return list.map(normalizeCloudRecord).filter(Boolean);
}

let records = getRecords();
let budget = getBudget();
let currentView = "stats";
let currentRange = "month";
let aiEndpoint = getAiEndpoint();
let trendChart;
let categoryChart;

const els = {
  views: {
    ai: document.querySelector("#view-ai"),
    stats: document.querySelector("#view-stats"),
    list: document.querySelector("#view-list"),
    settings: document.querySelector("#view-settings"),
  },
  navBtns: document.querySelectorAll(".nav-btn"),
  rangeChips: document.querySelectorAll(".range-chip"),
  rangeText: document.querySelector("#rangeText"),
  aiInput: document.querySelector("#aiInput"),
  aiAddBtn: document.querySelector("#aiAddBtn"),
  aiModeBadge: document.querySelector("#aiModeBadge"),
  aiModeHint: document.querySelector("#aiModeHint"),
  sampleBtns: document.querySelectorAll(".sample-btn"),
  aiTodayExpense: document.querySelector("#aiTodayExpense"),
  aiMonthBalance: document.querySelector("#aiMonthBalance"),
  aiRecentList: document.querySelector("#aiRecentList"),
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
  showToast.timer = window.setTimeout(() => els.toast.classList.remove("show"), 2200);
}

function setAiButtonLoading(isLoading) {
  els.aiAddBtn.disabled = isLoading;
  els.aiAddBtn.classList.toggle("loading", isLoading);
  els.aiAddBtn.textContent = isLoading ? "AI 识别中..." : "智能识别并添加";
}

function updateAiModeUI() {
  aiEndpoint = getAiEndpoint();
  els.aiEndpointInput.value = aiEndpoint;
  const cloudEnabled = Boolean(aiEndpoint);
  els.aiModeBadge.textContent = cloudEnabled ? "☁️ 云端 AI 识别" : "✨ 本地智能识别";
  els.aiModeHint.textContent = cloudEnabled
    ? "复杂语句将优先交给云端 AI 解析；连接失败时自动回退到本地识别。"
    : "未配置云端 AI 时，会自动使用本地规则识别。";
  els.aiEndpointStatus.textContent = cloudEnabled
    ? `当前接口：${aiEndpoint}`
    : "未配置时，App 会自动使用本地规则识别。";
}

function getMonthlyStats() {
  const monthRecords = records.filter((r) => sameMonth(r.date));
  const monthExpense = monthRecords.filter((r) => r.type === "expense").reduce((s, r) => s + r.amount, 0);
  const monthIncome = monthRecords.filter((r) => r.type === "income").reduce((s, r) => s + r.amount, 0);
  const todayExpense = records.filter((r) => r.date === todayISO() && r.type === "expense").reduce((s, r) => s + r.amount, 0);
  return { monthExpense, monthIncome, todayExpense, monthBalance: monthIncome - monthExpense };
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
        <div class="record-title">${record.title}</div>
        <div class="record-meta">${record.date} · ${record.category}</div>
      </div>
      <div class="record-side">
        <span class="record-amount ${record.type}">${record.type === "income" ? "+" : "-"}${money(record.amount)}</span>
        <button class="delete-btn" data-id="${record.id}" aria-label="删除">×</button>
      </div>
    </article>
  `;
}

function renderAI() {
  const stats = getMonthlyStats();
  els.aiTodayExpense.textContent = money(stats.todayExpense);
  els.aiMonthBalance.textContent = money(stats.monthBalance);
  const recent = [...records].sort((a, b) => b.date.localeCompare(a.date)).slice(0, 4);
  els.aiRecentList.innerHTML = recent.map(recordMarkup).join("") || `<p class="subtext">还没有账单，先记一笔吧。</p>`;
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
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          labels: { color: "#607083" },
        },
      },
      scales: {
        y: {
          beginAtZero: true,
          ticks: { color: "#607083" },
          grid: { color: "rgba(16,32,50,.08)" },
        },
        x: {
          ticks: { color: "#607083" },
          grid: { display: false },
        },
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
      responsive: true,
      maintainAspectRatio: false,
      cutout: "64%",
      plugins: {
        legend: {
          position: "bottom",
          labels: { color: "#607083", boxWidth: 12, padding: 16 },
        },
      },
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

function switchView(name) {
  currentView = name;
  Object.entries(els.views).forEach(([key, el]) => el.classList.toggle("active", key === name));
  els.navBtns.forEach((btn) => btn.classList.toggle("active", btn.dataset.view === name));
  if (name === "stats") {
    renderStats();
    requestAnimationFrame(renderCharts);
  }
  if (name === "ai") renderAI();
  if (name === "list") renderList();
  if (name === "settings") updateAiModeUI();
}

function openSheet() {
  document.body.classList.add("sheet-open");
  els.addSheet.setAttribute("aria-hidden", "false");
}

function closeSheet() {
  document.body.classList.remove("sheet-open");
  els.addSheet.setAttribute("aria-hidden", "true");
}

function addManualRecord() {
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

async function parseWithCloudAI(text) {
  if (!aiEndpoint) return null;

  const controller = new AbortController();
  const timeoutMs = Number(DEFAULT_AI_CONFIG.aiTimeoutMs) || 12000;
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(aiEndpoint, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ text, now: todayISO() }),
      signal: controller.signal,
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    const data = await response.json();
    return addRecordIds(Array.isArray(data.records) ? data.records : []);
  } finally {
    window.clearTimeout(timer);
  }
}

async function parseSmartRecords(text) {
  if (aiEndpoint) {
    try {
      const cloudRecords = await parseWithCloudAI(text);
      if (cloudRecords?.length) {
        return { records: cloudRecords, source: "cloud_ai" };
      }
    } catch (error) {
      console.warn("Cloud AI failed, falling back to local parser:", error);
      showToast("云端 AI 暂时不可用，已自动改用本地识别");
    }
  }

  return { records: parseNaturalLanguage(text), source: "local" };
}

async function testAiEndpoint() {
  const endpoint = normalizeEndpoint(els.aiEndpointInput.value);
  if (!endpoint) {
    showToast("请先填写 AI 接口地址");
    return;
  }

  const previousEndpoint = aiEndpoint;
  aiEndpoint = endpoint;
  els.testAiEndpointBtn.disabled = true;
  els.testAiEndpointBtn.textContent = "测试中...";

  try {
    const result = await parseWithCloudAI("今天测试消费1元");
    if (result && result.length) {
      showToast("AI 接口连接成功");
    } else {
      showToast("接口可达，但没有返回账单");
    }
  } catch (error) {
    showToast("AI 接口连接失败");
  } finally {
    aiEndpoint = previousEndpoint;
    els.testAiEndpointBtn.disabled = false;
    els.testAiEndpointBtn.textContent = "测试连接";
  }
}

els.navBtns.forEach((button) => button.addEventListener("click", () => switchView(button.dataset.view)));

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

els.aiAddBtn.addEventListener("click", async () => {
  const text = els.aiInput.value.trim();
  if (!text) {
    showToast("先输入一条账单内容吧");
    return;
  }

  setAiButtonLoading(true);
  const result = await parseSmartRecords(text);
  setAiButtonLoading(false);

  if (!result.records.length) {
    showToast("我没识别到账单金额，试试：今天午饭28，打车12");
    return;
  }

  records = [...result.records, ...records];
  saveRecords(records);
  els.aiInput.value = "";
  renderAll();
  showToast(result.source === "cloud_ai"
    ? `AI 已识别并添加 ${result.records.length} 条账单`
    : `已本地识别并添加 ${result.records.length} 条账单`);
});

els.fabAdd.addEventListener("click", openSheet);
els.closeSheetBtn.addEventListener("click", closeSheet);
els.sheetMask.addEventListener("click", closeSheet);
els.manualAddBtn.addEventListener("click", addManualRecord);

[els.recordList, els.aiRecentList].forEach((list) => list.addEventListener("click", (event) => {
  const button = event.target.closest("[data-id]");
  if (!button) return;
  removeRecord(button.dataset.id);
}));

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
switchView("stats");

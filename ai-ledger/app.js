const STORAGE_KEY = "ai-ledger-records-v1";
const BUDGET_KEY = "ai-ledger-budget-v1";

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
        id: crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`,
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

function saveRecords(records) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(records));
}

function getBudget() {
  const raw = localStorage.getItem(BUDGET_KEY);
  return raw ? Number(raw) : 1200;
}

function saveBudget(budget) {
  localStorage.setItem(BUDGET_KEY, String(budget));
}

let records = getRecords();
let budget = getBudget();
let trendChart;
let categoryChart;

const els = {
  monthBalance: document.querySelector("#monthBalance"),
  todayExpense: document.querySelector("#todayExpense"),
  monthExpense: document.querySelector("#monthExpense"),
  monthIncome: document.querySelector("#monthIncome"),
  budgetValue: document.querySelector("#budgetValue"),
  aiInput: document.querySelector("#aiInput"),
  aiAddBtn: document.querySelector("#aiAddBtn"),
  budgetInput: document.querySelector("#budgetInput"),
  budgetProgress: document.querySelector("#budgetProgress"),
  budgetText: document.querySelector("#budgetText"),
  manualTitle: document.querySelector("#manualTitle"),
  manualAmount: document.querySelector("#manualAmount"),
  manualType: document.querySelector("#manualType"),
  manualCategory: document.querySelector("#manualCategory"),
  manualAddBtn: document.querySelector("#manualAddBtn"),
  recordList: document.querySelector("#recordList"),
  toast: document.querySelector("#toast"),
};

function showToast(message) {
  els.toast.textContent = message;
  els.toast.classList.add("show");
  window.setTimeout(() => els.toast.classList.remove("show"), 2200);
}

function getMonthlyStats() {
  const monthRecords = records.filter((r) => sameMonth(r.date));
  const monthExpense = monthRecords.filter((r) => r.type === "expense").reduce((s, r) => s + r.amount, 0);
  const monthIncome = monthRecords.filter((r) => r.type === "income").reduce((s, r) => s + r.amount, 0);
  const todayExpense = records.filter((r) => r.date === todayISO() && r.type === "expense").reduce((s, r) => s + r.amount, 0);
  return { monthExpense, monthIncome, todayExpense, monthBalance: monthIncome - monthExpense };
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

function buildCategoryData() {
  const map = {};
  records.filter((r) => r.type === "expense" && sameMonth(r.date)).forEach((r) => {
    map[r.category] = (map[r.category] || 0) + r.amount;
  });
  return { labels: Object.keys(map), data: Object.values(map) };
}

function renderStats() {
  const stats = getMonthlyStats();
  els.monthBalance.textContent = money(stats.monthBalance);
  els.todayExpense.textContent = money(stats.todayExpense);
  els.monthExpense.textContent = money(stats.monthExpense);
  els.monthIncome.textContent = money(stats.monthIncome);
  els.budgetValue.textContent = money(budget);
  els.budgetInput.value = budget;
  const rate = budget > 0 ? Math.min((stats.monthExpense / budget) * 100, 100) : 0;
  els.budgetProgress.style.width = `${rate}%`;
  els.budgetText.textContent = `已使用 ${rate.toFixed(1)}% · 剩余 ${money(Math.max(budget - stats.monthExpense, 0))}`;
}

function renderRecords() {
  const recent = [...records].sort((a, b) => b.date.localeCompare(a.date)).slice(0, 8);
  els.recordList.innerHTML = recent.map((record) => `
    <div class="record-item">
      <div class="record-main">
        <div class="record-title">${record.title}</div>
        <div class="record-meta">${record.date} · ${record.category}</div>
      </div>
      <div class="record-side">
        <span class="amount ${record.type === "income" ? "income" : ""}">${record.type === "income" ? "+" : "-"}${money(record.amount)}</span>
        <button class="delete-btn" data-id="${record.id}" aria-label="删除">×</button>
      </div>
    </div>
  `).join("");
}

function renderCharts() {
  const trend = buildTrendData();
  const category = buildCategoryData();

  if (trendChart) trendChart.destroy();
  if (categoryChart) categoryChart.destroy();

  trendChart = new Chart(document.querySelector("#trendChart"), {
    type: "line",
    data: {
      labels: trend.labels,
      datasets: [
        { label: "支出", data: trend.expenseData, borderColor: "#111827", backgroundColor: "rgba(17,24,39,.12)", tension: .35, fill: true },
        { label: "收入", data: trend.incomeData, borderColor: "#9ca3af", backgroundColor: "rgba(156,163,175,.12)", tension: .35, fill: true },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { display: true } },
      scales: { y: { beginAtZero: true } },
    },
  });

  categoryChart = new Chart(document.querySelector("#categoryChart"), {
    type: "doughnut",
    data: {
      labels: category.labels,
      datasets: [{
        data: category.data,
        backgroundColor: ["#111827", "#374151", "#6b7280", "#9ca3af", "#d1d5db", "#4b5563", "#737373"],
      }],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { position: "bottom" } },
    },
  });
}

function render() {
  renderStats();
  renderRecords();
  renderCharts();
}

els.aiAddBtn.addEventListener("click", () => {
  const parsed = parseNaturalLanguage(els.aiInput.value);
  if (!parsed.length) {
    showToast("我没识别到账单金额，试试：今天午饭28，打车12");
    return;
  }
  records = [...parsed, ...records];
  saveRecords(records);
  els.aiInput.value = "";
  render();
  showToast(`已识别并添加 ${parsed.length} 条账单`);
});

els.aiInput.addEventListener("keydown", (event) => {
  if (event.key === "Enter") els.aiAddBtn.click();
});

els.budgetInput.addEventListener("input", () => {
  budget = Math.max(Number(els.budgetInput.value) || 0, 0);
  saveBudget(budget);
  renderStats();
});

els.manualAddBtn.addEventListener("click", () => {
  const title = els.manualTitle.value.trim();
  const amount = Number(els.manualAmount.value);
  if (!title || !Number.isFinite(amount) || amount <= 0) {
    showToast("标题和金额要填完整哦");
    return;
  }
  records = [{
    id: crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`,
    title,
    amount,
    type: els.manualType.value,
    category: els.manualCategory.value,
    date: todayISO(),
  }, ...records];
  saveRecords(records);
  els.manualTitle.value = "";
  els.manualAmount.value = "";
  render();
  showToast("已添加一条账单");
});

els.recordList.addEventListener("click", (event) => {
  const button = event.target.closest("[data-id]");
  if (!button) return;
  records = records.filter((record) => record.id !== button.dataset.id);
  saveRecords(records);
  render();
});

if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => navigator.serviceWorker.register("./sw.js"));
}

render();

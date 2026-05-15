const STORAGE_KEY = "ai-ledger-records-v3";
const CHAT_KEY = "ai-ledger-chat-v2";
const BACKGROUND_KEY = "ai-ledger-background-v1";
const CUSTOM_BG_KEY = "ai-ledger-custom-bg-v1";
const AI_CONFIG_KEY = "ai-ledger-ai-config-v1";
const DEFAULT_AI_CONFIG = window.AI_LEDGER_CONFIG || {};
const DEFAULT_AI_ENDPOINT = DEFAULT_AI_CONFIG.aiEndpoint || "";
const DEFAULT_AI_TIMEOUT_MS = DEFAULT_AI_CONFIG.aiTimeoutMs || 12000;
const DEFAULT_BG = "linear-gradient(180deg, #dff8f5 0%, #f6fbff 46%, #e9f5ff 100%)";

const categories = ["餐饮", "交通", "购物", "居住", "饮品", "工资", "礼物", "其他"];
const typeMap = { expense: "支出", income: "收入" };

const els = {
  body: document.body,
  navBtns: document.querySelectorAll(".nav-btn"),
  views: {
    ai: document.querySelector("#view-ai"),
    tools: document.querySelector("#view-tools"),
    stats: document.querySelector("#view-stats"),
    list: document.querySelector("#view-list"),
    settings: document.querySelector("#view-settings"),
  },
  chatMessages: document.querySelector("#chatMessages"),
  typingIndicator: document.querySelector("#typingIndicator"),
  chatForm: document.querySelector("#chatForm"),
  aiInput: document.querySelector("#aiInput"),
  sendBtn: document.querySelector("#sendBtn"),
  sampleBtns: document.querySelectorAll(".sample-btn"),
  todaySpend: document.querySelector("#todaySpend"),
  monthIncome: document.querySelector("#monthIncome"),
  monthExpense: document.querySelector("#monthExpense"),
  monthBalance: document.querySelector("#monthBalance"),
  recordList: document.querySelector("#recordList"),
  rangeChips: document.querySelectorAll(".range-chip"),
  addManualBtn: document.querySelector("#addManualBtn"),
  addSheet: document.querySelector("#addSheet"),
  closeSheetBtn: document.querySelector("#closeSheetBtn"),
  saveManualBtn: document.querySelector("#saveManualBtn"),
  manualTitle: document.querySelector("#manualTitle"),
  manualAmount: document.querySelector("#manualAmount"),
  manualType: document.querySelector("#manualType"),
  manualCategory: document.querySelector("#manualCategory"),
  exportBtn: document.querySelector("#exportBtn"),
  clearChatBtn: document.querySelector("#clearChatBtn"),
  toast: document.querySelector("#toast"),
  trendChart: document.querySelector("#trendChart"),
  categoryChart: document.querySelector("#categoryChart"),
  budgetInput: document.querySelector("#budgetInput"),
  backgroundPicker: document.querySelector("#backgroundPicker"),
  customBgInput: document.querySelector("#customBgInput"),
  clearBgBtn: document.querySelector("#clearBgBtn"),
  aiEndpointInput: document.querySelector("#aiEndpointInput"),
  saveAiEndpointBtn: document.querySelector("#saveAiEndpointBtn"),
  resetAiEndpointBtn: document.querySelector("#resetAiEndpointBtn"),
  testAiEndpointBtn: document.querySelector("#testAiEndpointBtn"),
  aiEndpointStatus: document.querySelector("#aiEndpointStatus"),
};

const initialChat = [
  { id: "welcome", role: "assistant", content: "你好，我是你的 AI 助手。你可以让我记账、查账单、查天气、读网页、设置提醒、打开应用，也可以直接和我聊天。", action: "chat", records: [], source: "builtin_profile" },
];

let records = loadRecords();
let chatMessages = loadChatMessages();
let currentView = "ai";
let currentRange = "month";
let trendChart = null;
let categoryChart = null;
let aiEndpoint = loadAiConfig().endpoint;

function loadRecords() {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) || "[]");
    return Array.isArray(parsed) ? parsed : [];
  } catch { return []; }
}
function saveRecords(next = records) { localStorage.setItem(STORAGE_KEY, JSON.stringify(next)); }
function loadChatMessages() {
  try {
    const parsed = JSON.parse(localStorage.getItem(CHAT_KEY) || "[]");
    return Array.isArray(parsed) && parsed.length ? parsed : [...initialChat];
  } catch { return [...initialChat]; }
}
function saveChatMessages() { localStorage.setItem(CHAT_KEY, JSON.stringify(chatMessages)); }
function createId() { return crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`; }
function todayISO() { return new Date().toISOString().slice(0, 10); }
function currentMonthPrefix() { return todayISO().slice(0, 7); }
function formatCurrency(value) { return `¥${Number(value || 0).toFixed(2)}`; }
function showToast(message) { if (!els.toast) return; els.toast.textContent = message; els.toast.classList.add("show"); clearTimeout(showToast.timer); showToast.timer = setTimeout(() => els.toast.classList.remove("show"), 2200); }
function setAiLoading(isLoading) { if (!els.sendBtn || !els.aiInput) return; els.sendBtn.disabled = isLoading; els.aiInput.disabled = isLoading; }
function renderTyping() { if (!els.chatMessages) return; els.chatMessages.insertAdjacentHTML("beforeend", `<div class="chat-row assistant" id="typingRow"><div class="chat-bubble"><span class="typing-dot"></span><span class="typing-dot"></span><span class="typing-dot"></span></div></div>`); els.chatMessages.scrollTop = els.chatMessages.scrollHeight; }
function removeTyping() { document.querySelector("#typingRow")?.remove(); }
function escapeHtml(value) { return String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;"); }

function loadAiConfig() {
  try {
    const parsed = JSON.parse(localStorage.getItem(AI_CONFIG_KEY) || "{}");
    return { endpoint: normalizeEndpoint(parsed.endpoint || DEFAULT_AI_ENDPOINT) };
  } catch { return { endpoint: normalizeEndpoint(DEFAULT_AI_ENDPOINT) }; }
}
function saveAiConfig(config) { localStorage.setItem(AI_CONFIG_KEY, JSON.stringify({ endpoint: normalizeEndpoint(config.endpoint) })); }
function normalizeEndpoint(value) { return String(value || "").trim().replace(/\/+$/g, ""); }
function setAiStatus(message, mode = "normal") { if (!els.aiEndpointStatus) return; els.aiEndpointStatus.textContent = message; els.aiEndpointStatus.dataset.mode = mode; }
function updateAiModeUI() { if (els.aiEndpointInput) els.aiEndpointInput.value = aiEndpoint || ""; setAiStatus(aiEndpoint ? "云端 AI 接口已配置" : "未配置云端 AI，当前使用本地识别", aiEndpoint ? "success" : "normal"); }

function isMathOrOnlineQuery(text) { return /(天气|下雨|气温|温度|风速|降雨|上网|联网|搜索|查一下|搜一下|最新|新闻|http|www\.|计算|算一下|等于|[0-9]\s*[+\-×÷*/^]\s*[0-9])/u.test(String(text || "")); }

function normalizeRecord(record) {
  return {
    id: record.id || createId(),
    title: String(record.title || "未命名账单").slice(0, 30),
    amount: Number(record.amount) || 0,
    type: record.type === "income" ? "income" : "expense",
    category: categories.includes(record.category) ? record.category : "其他",
    date: /^\d{4}-\d{2}-\d{2}$/.test(record.date || "") ? record.date : todayISO(),
  };
}
function normalizeRecords(list) { return Array.isArray(list) ? list.map(normalizeRecord).filter((item) => item.amount > 0) : []; }
function normalizeMobileCommand(command) {
  if (!command || typeof command !== "object") return null;
  if (!["set_alarm", "open_app", "navigate"].includes(command.type)) return null;
  return command;
}

function cleanTitle(text) { return String(text || "").replace(/今天|昨天|前天|花了|花费|消费|支出|收入|进账|收到|元|块钱|块/gu, "").replace(/[0-9.]/gu, "").replace(/[，,。；;、]/gu, "").trim() || "未命名账单"; }
function inferCategory(text) { if (/(饭|早餐|午餐|晚餐|外卖|面|米线|火锅|烧烤|餐)/u.test(text)) return "餐饮"; if (/(奶茶|咖啡|饮料|可乐|茶)/u.test(text)) return "饮品"; if (/(打车|出租|公交|地铁|高铁|火车|机票|加油)/u.test(text)) return "交通"; if (/(淘宝|京东|拼多多|买|衣服|鞋|超市|购物)/u.test(text)) return "购物"; if (/(房租|水电|物业|宿舍|宽带)/u.test(text)) return "居住"; if (/(工资|兼职|奖金|补贴|报销|收入)/u.test(text)) return "工资"; if (/(礼物|红包)/u.test(text)) return "礼物"; return "其他"; }
function inferType(text) { return /(收入|工资|兼职|奖金|报销|收到|进账)/u.test(text) ? "income" : "expense"; }
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
    return { id: createId(), title: cleanTitle(part), amount, type: inferType(part), category: inferCategory(part), date: /昨天/u.test(part) ? shiftDate(todayISO(), -1) : todayISO() };
  }).filter(Boolean);
  return parsed.length === parts.length ? parsed : [];
}
function shiftDate(iso, days) { const d = new Date(`${iso}T00:00:00Z`); d.setUTCDate(d.getUTCDate() + days); return d.toISOString().slice(0, 10); }
function getPendingMessage() { return chatMessages.find((item) => item.role === "assistant" && item.action === "draft" && item.draftState === "pending"); }
function getLedgerContext() {
  const month = currentMonthPrefix();
  const monthRecords = records.filter((record) => record.date?.startsWith(month));
  const monthIncome = monthRecords.filter((r) => r.type === "income").reduce((s, r) => s + r.amount, 0);
  const monthExpense = monthRecords.filter((r) => r.type === "expense").reduce((s, r) => s + r.amount, 0);
  return { summary: { monthIncome, monthExpense, monthBalance: monthIncome - monthExpense }, recentRecords: records.slice(0, 60) };
}
function buildRecordCard(recordsList, state = "pending") { const rows = recordsList.map((r) => `<div class="draft-record"><div><strong>${escapeHtml(r.title)}</strong><span>${escapeHtml(r.date)} · ${escapeHtml(r.category)}</span></div><em>${r.type === "income" ? "+" : "-"}${formatCurrency(r.amount)}</em></div>`).join(""); return `<div class="draft-card"><div class="draft-head"><strong>待确认账单</strong><span>${recordsList.length} 笔</span></div>${rows}<button class="confirm-draft" data-action="confirm-draft">确认记账</button><button class="cancel-draft" data-action="cancel-draft">先不保存</button></div>`; }
function renderMobileCommandCard(command) { return window.MobileCommandActions?.renderCard?.(command) || ""; }
function renderAI() { renderChat(); }
function renderChat() {
  if (!els.chatMessages) return;
  els.chatMessages.innerHTML = chatMessages.map((message) => {
    const isUser = message.role === "user";
    const source = message.source ? ` data-source="${escapeHtml(message.source)}"` : "";
    const draft = message.action === "draft" && message.records?.length ? buildRecordCard(message.records, message.draftState) : "";
    const mobile = message.action === "mobile_command" && message.mobileCommand ? renderMobileCommandCard(message.mobileCommand) : "";
    return `<div class="chat-row ${isUser ? "user" : "assistant"}" data-message-id="${escapeHtml(message.id)}"${source}><div class="chat-bubble chat-response">${escapeHtml(message.content).replace(/\n/g, "<br>")}${draft}${mobile}</div></div>`;
  }).join("");
  els.chatMessages.scrollTop = els.chatMessages.scrollHeight;
}
function renderStats() {
  const today = todayISO();
  const month = currentMonthPrefix();
  const todaySpend = records.filter((r) => r.type === "expense" && r.date === today).reduce((s, r) => s + r.amount, 0);
  const monthRecords = records.filter((r) => r.date?.startsWith(month));
  const monthIncome = monthRecords.filter((r) => r.type === "income").reduce((s, r) => s + r.amount, 0);
  const monthExpense = monthRecords.filter((r) => r.type === "expense").reduce((s, r) => s + r.amount, 0);
  if (els.todaySpend) els.todaySpend.textContent = formatCurrency(todaySpend);
  if (els.monthIncome) els.monthIncome.textContent = formatCurrency(monthIncome);
  if (els.monthExpense) els.monthExpense.textContent = formatCurrency(monthExpense);
  if (els.monthBalance) els.monthBalance.textContent = formatCurrency(monthIncome - monthExpense);
}
function getFilteredRecords() { return records; }
function renderList() { if (!els.recordList) return; els.recordList.innerHTML = records.length ? records.map((r) => `<article class="record-item"><div><strong>${escapeHtml(r.title)}</strong><span>${escapeHtml(r.date)} · ${escapeHtml(r.category)} · ${escapeHtml(typeMap[r.type])}</span></div><em>${r.type === "income" ? "+" : "-"}${formatCurrency(r.amount)}</em><button data-remove="${escapeHtml(r.id)}">删除</button></article>`).join("") : `<p class="empty-state">还没有账单。</p>`; }
function buildTrendData() { return { labels: [], expenseData: [], incomeData: [] }; }
function buildCategoryData() { return { labels: categories, data: categories.map((c) => records.filter((r) => r.category === c && r.type === "expense").reduce((s, r) => s + r.amount, 0)) }; }
function renderCharts() {}
function renderAll() { renderAI(); renderStats(); renderList(); updateAiModeUI(); }
function getTopLevelView(name) { return name === "stats" || name === "list" ? "tools" : name; }
function switchView(name) { if (!els.views[name]) name = "ai"; currentView = name; Object.entries(els.views).forEach(([key, el]) => el?.classList.toggle("active", key === name)); const top = getTopLevelView(name); els.navBtns.forEach((btn) => btn.classList.toggle("active", btn.dataset.view === top)); if (name === "settings") updateAiModeUI(); if (name === "tools") window.dispatchEvent(new CustomEvent("ai-tools-home")); }
window.AiAssistantViews = { open: switchView, current: () => currentView };
function openSheet() { if (!els.addSheet) return; document.body.classList.add("sheet-open"); els.addSheet.setAttribute("aria-hidden", "false"); }
function closeSheet() { if (!els.addSheet) return; document.body.classList.remove("sheet-open"); els.addSheet.setAttribute("aria-hidden", "true"); }
function addManualRecord() { const title = els.manualTitle?.value.trim(); const amount = Number(els.manualAmount?.value); if (!title || !Number.isFinite(amount) || amount <= 0) return showToast("标题和金额要填完整哦"); records = [{ id: createId(), title, amount, type: els.manualType.value, category: els.manualCategory.value, date: todayISO() }, ...records]; saveRecords(records); renderAll(); closeSheet(); showToast("已添加一条账单"); }
function exportRecords() { const blob = new Blob([JSON.stringify(records, null, 2)], { type: "application/json" }); const url = URL.createObjectURL(blob); const link = document.createElement("a"); link.href = url; link.download = "ai-ledger-records.json"; link.click(); URL.revokeObjectURL(url); }
function conversationPayload() { return chatMessages.filter((m) => m.role === "user" || m.role === "assistant").slice(-16).map((m) => ({ role: m.role, content: m.content })); }
async function fetchJsonWithTimeout(url, options, timeoutMs = 12000) { const controller = new AbortController(); const timer = setTimeout(() => controller.abort(), timeoutMs); try { const response = await fetch(url, { ...options, signal: controller.signal }); const text = await response.text(); let data = null; try { data = text ? JSON.parse(text) : null; } catch {} return { response, data, text }; } finally { clearTimeout(timer); } }
function formatCloudError(result) { const data = result?.data || {}; if (data.providerStatus) return `云端 AI 调用失败：${data.providerCode || data.code || `HTTP ${data.providerStatus}`}`; if (data.code) return `云端 AI 调用失败：${data.code}`; return "云端 AI 暂时不可用"; }
async function askCloudAI() {
  if (!aiEndpoint) return null;
  const pending = getPendingMessage();
  const result = await fetchJsonWithTimeout(aiEndpoint, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ messages: conversationPayload(), pendingDraft: pending?.records || [], ledgerContext: getLedgerContext(), clientTools: window.MobileCommandActions?.tools || [], now: todayISO() }) }, Number(DEFAULT_AI_TIMEOUT_MS) || 12000);
  if (!result.response.ok) { const error = new Error(formatCloudError(result)); error.cloudResult = result; throw error; }
  const mobileCommand = normalizeMobileCommand(result.data?.mobileCommand);
  const action = result.data?.action === "mobile_command" && !mobileCommand ? "chat" : result.data?.action || "chat";
  return { reply: String(result.data?.reply || "").trim(), action, records: normalizeRecords(result.data?.records), mobileCommand, source: result.data?.source || "cloud_ai", version: result.data?.version };
}
function localChatFallback(text) {
  const pending = getPendingMessage();
  if (pending && /^(好|好的|对|确认|保存|记上|就这样)$/u.test(text)) return { reply: "好的，已帮你记上。", action: "confirm_pending", records: [], source: "local" };
  if (pending && /^(算了|不用了|先别记|取消)$/u.test(text)) return { reply: "好的，这次先不保存。", action: "cancel_pending", records: [], source: "local" };
  const mobileCommand = normalizeMobileCommand(window.MobileCommandActions?.parse?.(text));
  if (mobileCommand) return { reply: window.MobileCommandActions?.createReply?.(mobileCommand) || "我整理好了这个手机动作，确认后我再执行。", action: "mobile_command", records: [], mobileCommand, source: "local_mobile" };
  if (isMathOrOnlineQuery(text)) return { reply: "这个问题需要云端工具处理，但当前云端没有成功返回。请检查 Worker 是否部署成功，或到设置里测试连接。", action: "chat", records: [], source: "local" };
  const parsed = parseNaturalLanguage(text);
  if (parsed.length) return { reply: `我先整理出 ${parsed.length} 笔待确认账单，你回复“好”我就帮你保存。`, action: "draft", records: parsed, source: "local" };
  return { reply: "我还没听清。你可以继续说，我会跟着问。", action: "chat", records: [], source: "local" };
}
function localMobileCommandResult(text) { if (getPendingMessage() || isMathOrOnlineQuery(text)) return null; const mobileCommand = normalizeMobileCommand(window.MobileCommandActions?.parse?.(text)); if (!mobileCommand) return null; return { reply: window.MobileCommandActions?.createReply?.(mobileCommand) || "我整理好了这个手机动作，确认后我再执行。", action: "mobile_command", records: [], mobileCommand, source: "local_mobile" }; }
async function askAssistant(text) {
  chatMessages.push({ id: createId(), role: "user", content: text }); saveChatMessages(); renderChat(); renderTyping(); setAiLoading(true);
  let result; const localMobile = localMobileCommandResult(text);
  if (localMobile) result = localMobile; else if (aiEndpoint) { try { result = await askCloudAI(); setAiStatus(`云端 AI 已连接${result.version ? ` · ${result.version}` : ""}`, "success"); } catch (error) { console.warn("Cloud AI failed:", error); setAiStatus(error.message || "云端 AI 暂时不可用", "error"); showToast(error.message || "云端 AI 暂时不可用"); } }
  if (!result) result = localChatFallback(text);
  removeTyping(); setAiLoading(false);
  const pending = getPendingMessage();
  if (result.action === "confirm_pending" && pending) confirmDraft(pending.id, false); else if (result.action === "cancel_pending" && pending) pending.draftState = "cancelled"; else if (result.action === "draft" && pending) pending.draftState = "superseded";
  chatMessages.push({ id: createId(), role: "assistant", content: result.reply || "我在。", action: result.action, records: result.action === "draft" ? result.records : [], draftState: result.action === "draft" && result.records.length ? "pending" : "none", mobileCommand: result.action === "mobile_command" ? result.mobileCommand : null, source: result.source, version: result.version });
  saveChatMessages(); renderAll();
}
function confirmDraft(messageId, announce = true) { const message = chatMessages.find((item) => item.id === messageId); if (!message || message.draftState !== "pending" || !message.records?.length) return; records = [...message.records.map((record) => ({ ...record, id: createId() })), ...records]; message.draftState = "confirmed"; saveRecords(records); saveChatMessages(); if (announce) showToast(`已保存 ${message.records.length} 条账单`); renderAll(); }
function cancelDraft(messageId) { const message = chatMessages.find((item) => item.id === messageId); if (!message || message.draftState !== "pending") return; message.draftState = "cancelled"; saveChatMessages(); renderChat(); showToast("已取消这次记账"); }
function clearChat() { chatMessages = [...initialChat]; saveChatMessages(); renderChat(); showToast("已清空聊天记录"); }
async function testAiEndpoint() { const endpoint = normalizeEndpoint(els.aiEndpointInput.value); if (!endpoint) return showToast("请先填写 AI 接口地址"); els.testAiEndpointBtn.disabled = true; els.testAiEndpointBtn.textContent = "测试中..."; setAiStatus("正在检测云端 AI…", "normal"); try { const health = await fetchJsonWithTimeout(`${endpoint}/health`, { method: "GET" }, 8000); if (!health.response.ok || !health.data?.ok) throw new Error(`Worker 健康检查失败：HTTP ${health.response.status}`); const label = `AI 接口连接成功 · ${health.data.version || "已连通"}`; setAiStatus(label, "success"); showToast("AI 接口连接成功"); } catch (error) { setAiStatus(error.message || "AI 接口连接失败", "error"); showToast(error.message || "AI 接口连接失败"); } finally { els.testAiEndpointBtn.disabled = false; els.testAiEndpointBtn.textContent = "测试连接"; } }
els.navBtns.forEach((button) => button.addEventListener("click", () => switchView(button.dataset.view)));
document.addEventListener("click", (event) => { const viewBtn = event.target.closest("[data-open-view]"); if (viewBtn) return switchView(viewBtn.dataset.openView); const confirmBtn = event.target.closest("[data-action='confirm-draft']"); if (confirmBtn) return confirmDraft(confirmBtn.closest(".chat-row")?.dataset.messageId); const cancelBtn = event.target.closest("[data-action='cancel-draft']"); if (cancelBtn) return cancelDraft(cancelBtn.closest(".chat-row")?.dataset.messageId); const remove = event.target.closest("[data-remove]"); if (remove) { records = records.filter((r) => r.id !== remove.dataset.remove); saveRecords(records); renderAll(); } });
els.sampleBtns.forEach((button) => button.addEventListener("click", () => { els.aiInput.value = button.dataset.sample; els.aiInput.focus(); }));
els.chatForm?.addEventListener("submit", async (event) => { event.preventDefault(); const text = els.aiInput.value.trim(); if (!text) return showToast("先说一句吧"); els.aiInput.value = ""; els.aiInput.style.height = "auto"; await askAssistant(text); });
els.aiInput?.addEventListener("input", () => { els.aiInput.style.height = "auto"; els.aiInput.style.height = `${Math.min(160, els.aiInput.scrollHeight)}px`; });
els.addManualBtn?.addEventListener("click", openSheet); els.closeSheetBtn?.addEventListener("click", closeSheet); els.saveManualBtn?.addEventListener("click", addManualRecord); els.exportBtn?.addEventListener("click", exportRecords); els.clearChatBtn?.addEventListener("click", clearChat);
els.saveAiEndpointBtn?.addEventListener("click", () => { aiEndpoint = normalizeEndpoint(els.aiEndpointInput.value); saveAiConfig({ endpoint: aiEndpoint }); updateAiModeUI(); showToast("AI 接口已保存"); });
els.resetAiEndpointBtn?.addEventListener("click", () => { aiEndpoint = normalizeEndpoint(DEFAULT_AI_ENDPOINT); saveAiConfig({ endpoint: aiEndpoint }); updateAiModeUI(); showToast("已恢复默认 AI 接口"); });
els.testAiEndpointBtn?.addEventListener("click", testAiEndpoint);
renderAll();
window.chatMessages = chatMessages; window.createId = createId; window.saveChatMessages = saveChatMessages; window.renderAll = renderAll; window.getPendingMessage = getPendingMessage; window.localMobileCommandResult = localMobileCommandResult;

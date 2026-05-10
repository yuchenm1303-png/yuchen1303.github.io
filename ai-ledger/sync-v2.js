const RECORDS_KEY = "ai-ledger-records-v1";
const BUDGET_KEY = "ai-ledger-budget-v1";
const SYNC_TABLE = "records";
const SETTINGS_TABLE = "user_settings";
const SYNC_POLL_MS = 3500;

const syncEls = {
  status: document.querySelector("#syncStatusText"),
  button: document.querySelector("#syncNowBtn"),
};

let syncClient = null;
let syncUser = null;
let lastRecordSnapshot = localStorage.getItem(RECORDS_KEY) || "[]";
let lastBudgetSnapshot = localStorage.getItem(BUDGET_KEY) || "3000";
let syncTimer = null;
let syncing = false;
let hydratedUserId = null;

function setSyncStatus(message, tone = "normal") {
  if (!syncEls.status) return;
  syncEls.status.textContent = message;
  syncEls.status.dataset.tone = tone;
}

function normalizeLocalRecord(record) {
  const amount = Number(record?.amount);
  if (!record?.id || !Number.isFinite(amount) || amount <= 0) return null;
  return {
    id: String(record.id),
    title: String(record.title || "未命名账单").trim() || "未命名账单",
    amount,
    type: record.type === "income" ? "income" : "expense",
    category: String(record.category || "其他"),
    date: /^\d{4}-\d{2}-\d{2}$/.test(String(record.date || "")) ? record.date : new Date().toISOString().slice(0, 10),
  };
}

function readLocalRecords() {
  try {
    const parsed = JSON.parse(localStorage.getItem(RECORDS_KEY) || "[]");
    return Array.isArray(parsed) ? parsed.map(normalizeLocalRecord).filter(Boolean) : [];
  } catch {
    return [];
  }
}

function writeLocalRecords(records) {
  const safe = records.map(normalizeLocalRecord).filter(Boolean);
  localStorage.setItem(RECORDS_KEY, JSON.stringify(safe));
  lastRecordSnapshot = localStorage.getItem(RECORDS_KEY) || "[]";
}

function readLocalBudget() {
  return Math.max(Number(localStorage.getItem(BUDGET_KEY) || 3000) || 0, 0);
}

function writeLocalBudget(value) {
  localStorage.setItem(BUDGET_KEY, String(Math.max(Number(value) || 0, 0)));
  lastBudgetSnapshot = localStorage.getItem(BUDGET_KEY) || "3000";
}

function recordsToRows(records, userId) {
  return records.map((record) => ({
    id: record.id,
    user_id: userId,
    title: record.title,
    amount: record.amount,
    type: record.type,
    category: record.category,
    date: record.date,
  }));
}

function rowsToRecords(rows) {
  return (Array.isArray(rows) ? rows : [])
    .map((row) => normalizeLocalRecord({
      id: row.id,
      title: row.title,
      amount: row.amount,
      type: row.type,
      category: row.category,
      date: row.date,
    }))
    .filter(Boolean);
}

function sortRecords(records) {
  return [...records].sort((a, b) => b.date.localeCompare(a.date));
}

function mergeById(localRecords, remoteRecords) {
  const map = new Map();
  remoteRecords.forEach((record) => map.set(record.id, record));
  localRecords.forEach((record) => map.set(record.id, record));
  return sortRecords([...map.values()]);
}

function snapshot(value) {
  return JSON.stringify(value);
}

async function getSessionUser() {
  if (!syncClient) return null;
  const { data } = await syncClient.auth.getSession();
  return data.session?.user || null;
}

async function fetchRemoteRecords() {
  const { data, error } = await syncClient
    .from(SYNC_TABLE)
    .select("id,title,amount,type,category,date")
    .order("date", { ascending: false });
  if (error) throw error;
  return rowsToRecords(data);
}

async function upsertRecords(records) {
  if (!records.length) return;
  const { error } = await syncClient
    .from(SYNC_TABLE)
    .upsert(recordsToRows(records, syncUser.id), { onConflict: "id" });
  if (error) throw error;
}

async function deleteRemoteRecords(ids) {
  if (!ids.length) return;
  const { error } = await syncClient
    .from(SYNC_TABLE)
    .delete()
    .in("id", ids);
  if (error) throw error;
}

async function fetchRemoteBudget() {
  const { data, error } = await syncClient
    .from(SETTINGS_TABLE)
    .select("monthly_budget")
    .eq("user_id", syncUser.id)
    .maybeSingle();
  if (error) throw error;
  return data?.monthly_budget;
}

async function upsertBudget(value) {
  const { error } = await syncClient
    .from(SETTINGS_TABLE)
    .upsert({ user_id: syncUser.id, monthly_budget: value }, { onConflict: "user_id" });
  if (error) throw error;
}

function maybeReloadOnceAfterFirstHydration(userId, changed) {
  if (!changed) return;
  const key = `ai-ledger-hydrated-once-${userId}`;
  if (sessionStorage.getItem(key)) return;
  sessionStorage.setItem(key, "1");
  window.setTimeout(() => window.location.reload(), 180);
}

async function hydrateFromCloud() {
  if (!syncUser || hydratedUserId === syncUser.id) return;
  syncing = true;
  setSyncStatus("正在读取云端数据…");
  try {
    const localRecords = readLocalRecords();
    const remoteRecords = await fetchRemoteRecords();
    const merged = remoteRecords.length ? mergeById(localRecords, remoteRecords) : localRecords;

    if (!remoteRecords.length && localRecords.length) {
      await upsertRecords(localRecords);
    } else if (merged.length) {
      const localOnly = merged.filter((record) => !remoteRecords.some((remote) => remote.id === record.id));
      if (localOnly.length) await upsertRecords(localOnly);
    }

    const beforeRecords = localStorage.getItem(RECORDS_KEY) || "[]";
    const beforeBudget = localStorage.getItem(BUDGET_KEY) || "3000";

    writeLocalRecords(merged);

    const remoteBudget = await fetchRemoteBudget();
    if (remoteBudget == null) {
      await upsertBudget(readLocalBudget());
    } else {
      writeLocalBudget(remoteBudget);
    }

    const changed = beforeRecords !== (localStorage.getItem(RECORDS_KEY) || "[]")
      || beforeBudget !== (localStorage.getItem(BUDGET_KEY) || "3000");

    hydratedUserId = syncUser.id;
    setSyncStatus("云端同步已开启", "success");
    maybeReloadOnceAfterFirstHydration(syncUser.id, changed);
  } catch (error) {
    console.error("Hydrate cloud sync failed:", error);
    setSyncStatus(`同步失败：${error.message || "请稍后重试"}`, "error");
  } finally {
    syncing = false;
  }
}

async function syncLocalChanges() {
  if (!syncUser || syncing) return;
  const recordSnapshot = localStorage.getItem(RECORDS_KEY) || "[]";
  const budgetSnapshot = localStorage.getItem(BUDGET_KEY) || "3000";
  if (recordSnapshot === lastRecordSnapshot && budgetSnapshot === lastBudgetSnapshot) return;

  syncing = true;
  setSyncStatus("正在同步…");
  try {
    const previousRecords = JSON.parse(lastRecordSnapshot || "[]").map(normalizeLocalRecord).filter(Boolean);
    const currentRecords = readLocalRecords();
    const previousIds = new Set(previousRecords.map((record) => record.id));
    const currentIds = new Set(currentRecords.map((record) => record.id));
    const deletedIds = [...previousIds].filter((id) => !currentIds.has(id));

    if (currentRecords.length) await upsertRecords(currentRecords);
    if (deletedIds.length) await deleteRemoteRecords(deletedIds);
    if (budgetSnapshot !== lastBudgetSnapshot) await upsertBudget(readLocalBudget());

    lastRecordSnapshot = recordSnapshot;
    lastBudgetSnapshot = budgetSnapshot;
    setSyncStatus("已同步到云端", "success");
  } catch (error) {
    console.error("Cloud sync failed:", error);
    setSyncStatus(`同步失败：${error.message || "请稍后重试"}`, "error");
  } finally {
    syncing = false;
  }
}

async function syncNow() {
  syncUser = await getSessionUser();
  if (!syncUser) {
    setSyncStatus("先登录，才能开启云同步。", "normal");
    return;
  }

  const remoteRecords = await fetchRemoteRecords();
  const localRecords = readLocalRecords();
  const merged = mergeById(localRecords, remoteRecords);
  writeLocalRecords(merged);
  await upsertRecords(merged);
  await upsertBudget(readLocalBudget());
  lastRecordSnapshot = localStorage.getItem(RECORDS_KEY) || "[]";
  lastBudgetSnapshot = localStorage.getItem(BUDGET_KEY) || "3000";
  setSyncStatus("已同步到云端", "success");
}

async function initSync() {
  syncClient = window.aiLedgerAuth?.client || null;
  if (!syncClient) {
    setSyncStatus("登录服务尚未准备好。", "error");
    return;
  }

  syncUser = await getSessionUser();
  if (!syncUser) {
    setSyncStatus("登录后自动开启云同步。", "normal");
  } else {
    await hydrateFromCloud();
  }

  syncClient.auth.onAuthStateChange(async (_event, session) => {
    syncUser = session?.user || null;
    hydratedUserId = null;
    if (!syncUser) {
      setSyncStatus("已退出，当前仅保存在本机。", "normal");
      return;
    }
    await hydrateFromCloud();
  });

  if (syncTimer) window.clearInterval(syncTimer);
  syncTimer = window.setInterval(syncLocalChanges, SYNC_POLL_MS);
}

syncEls.button?.addEventListener("click", syncNow);
window.addEventListener("load", initSync);
window.aiLedgerSync = { syncNow };

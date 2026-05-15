(() => {
  const CHAT_KEY = "ai-ledger-chat-v2";
  const STYLE_ID = "chat-source-badges-style";

  const SOURCE_LABELS = {
    cloud_ai: { label: "云端 AI", tone: "cloud" },
    workers_ai: { label: "云端 AI", tone: "cloud" },
    workers_ai_text_fallback: { label: "云端兜底", tone: "cloud-fallback" },
    gemini_ai: { label: "Gemini AI", tone: "gemini" },
    gemini_text_fallback: { label: "Gemini 兜底", tone: "cloud-fallback" },
    gemini_error: { label: "Gemini 错误", tone: "error" },
    hybrid_rules: { label: "云端规则", tone: "cloud-rule" },
    weather_tool: { label: "实时天气", tone: "online" },
    wiki_tool: { label: "百科摘要", tone: "online" },
    webpage_tool: { label: "网页读取", tone: "online" },
    web_search_tool: { label: "联网搜索", tone: "online" },
    search_not_configured: { label: "搜索未配置", tone: "cloud-fallback" },
    calculator_tool: { label: "计算器", tone: "utility" },
    datetime_tool: { label: "日期时间", tone: "utility" },
    builtin_profile: { label: "内置回复", tone: "builtin" },
    local: { label: "本地规则", tone: "local" },
    local_mobile: { label: "手机动作", tone: "mobile" },
    navigation_preferences: { label: "导航偏好", tone: "mobile" },
    missing_workers_ai_binding: { label: "云端未绑定", tone: "error" },
    missing_cloud_ai: { label: "云端未配置", tone: "error" },
  };

  function escapeHtml(value) {
    return String(value || "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function readMessages() {
    try {
      const parsed = JSON.parse(localStorage.getItem(CHAT_KEY) || "[]");
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  function inferSource(message) {
    if (!message || message.role !== "assistant") return null;
    if (message.source) return message.source;
    if (message.mobileCommand) return "local_mobile";
    if (Array.isArray(message.records) && message.records.length) return "local";
    if (message.id === "welcome") return "builtin_profile";
    return "unknown";
  }

  function sourceMeta(source) {
    if (SOURCE_LABELS[source]) return SOURCE_LABELS[source];
    return { label: "来源未知", tone: "unknown" };
  }

  function installStyle() {
    if (document.querySelector(`#${STYLE_ID}`)) return;
    const style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = `
      .chat-source-badge-row{display:flex;justify-content:flex-start;margin:7px 0 0 4px;gap:6px;flex-wrap:wrap}
      .chat-source-badge{display:inline-flex;align-items:center;gap:5px;border-radius:999px;padding:4px 8px;font-size:11px;font-weight:800;line-height:1;background:rgba(255,255,255,.18);border:1px solid rgba(255,255,255,.28);color:rgba(238,250,255,.78);backdrop-filter:blur(12px)}
      .chat-source-badge::before{content:"";width:6px;height:6px;border-radius:999px;background:currentColor;opacity:.85}
      .chat-source-badge.cloud{color:#83f7ff;background:rgba(33,197,255,.14);border-color:rgba(33,197,255,.28)}
      .chat-source-badge.gemini{color:#c7b7ff;background:rgba(126,87,255,.18);border-color:rgba(126,87,255,.35)}
      .chat-source-badge.online{color:#8ff7c4;background:rgba(22,190,121,.16);border-color:rgba(22,190,121,.34)}
      .chat-source-badge.utility{color:#ffe38f;background:rgba(240,180,50,.16);border-color:rgba(240,180,50,.32)}
      .chat-source-badge.cloud-fallback{color:#ffd28a;background:rgba(255,189,91,.14);border-color:rgba(255,189,91,.32)}
      .chat-source-badge.cloud-rule{color:#a7f3d0;background:rgba(16,185,129,.14);border-color:rgba(16,185,129,.28)}
      .chat-source-badge.builtin{color:#e7ddff;background:rgba(161,117,255,.16);border-color:rgba(161,117,255,.30)}
      .chat-source-badge.local{color:#cbd5e1;background:rgba(148,163,184,.16);border-color:rgba(148,163,184,.28)}
      .chat-source-badge.mobile{color:#86ece2;background:rgba(11,143,139,.18);border-color:rgba(11,143,139,.32)}
      .chat-source-badge.error{color:#ffb4b4;background:rgba(255,91,91,.15);border-color:rgba(255,91,91,.30)}
      .chat-source-badge.unknown{color:#d8dce8;background:rgba(255,255,255,.12)}
      body.assistant-compact .chat-source-badge-row{margin-top:5px}
      body.assistant-compact .chat-source-badge{font-size:10px;padding:3px 7px}
    `;
    document.head.appendChild(style);
  }

  function addBadges() {
    const messages = readMessages();
    const byId = new Map(messages.map((message) => [String(message.id), message]));
    document.querySelectorAll(".chat-row.assistant[data-message-id]").forEach((row) => {
      const id = row.dataset.messageId;
      if (!id || row.dataset.sourceBadgeReady === "ready") return;
      const message = byId.get(String(id));
      if (!message) return;
      const source = inferSource(message);
      const meta = sourceMeta(source);
      const response = row.querySelector(".chat-response");
      if (!response) return;
      const version = message.version ? ` · ${escapeHtml(message.version)}` : "";
      response.insertAdjacentHTML("beforeend", `<div class="chat-source-badge-row"><span class="chat-source-badge ${escapeHtml(meta.tone)}">${escapeHtml(meta.label)}${version}</span></div>`);
      row.dataset.sourceBadgeReady = "ready";
    });
  }

  function installObserver() {
    const target = document.querySelector("#chatMessages");
    if (!target || target.dataset.sourceBadgeObserver === "ready") return;
    target.dataset.sourceBadgeObserver = "ready";
    const observer = new MutationObserver(() => addBadges());
    observer.observe(target, { childList: true, subtree: true });
    addBadges();
  }

  window.ChatSourceBadges = { refresh: addBadges, labels: SOURCE_LABELS };

  window.addEventListener("DOMContentLoaded", () => {
    installStyle();
    window.setTimeout(installObserver, 0);
    window.setTimeout(installObserver, 300);
    window.setInterval(addBadges, 1800);
  });
})();

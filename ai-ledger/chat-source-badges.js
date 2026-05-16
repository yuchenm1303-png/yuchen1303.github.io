(() => {
  const CHAT_KEY = "ai-ledger-chat-v2";
  const STYLE_ID = "chat-source-badges-style";

  const SOURCE_LABELS = {
    cloud_ai: { label: "云端 AI", tone: "cloud" },
    workers_ai: { label: "Workers AI", tone: "cloud" },
    workers_ai_text_fallback: { label: "Workers AI 兜底", tone: "cloud-fallback" },
    workers_ai_vision: { label: "Workers AI 识图", tone: "vision" },
    workers_ai_vision_fallback: { label: "Workers AI 识图兜底", tone: "vision" },
    gemini_ai: { label: "Gemini AI", tone: "gemini" },
    gemini_chat: { label: "Gemini 对话", tone: "gemini" },
    gemini_chat_error: { label: "Gemini 错误", tone: "error" },
    gemini_missing_key: { label: "Gemini 未配置", tone: "error" },
    gemini_vision: { label: "Gemini 识图", tone: "vision" },
    gemini_vision_error: { label: "识图错误", tone: "error" },
    vision_quota_exceeded: { label: "识图配额不足", tone: "error" },
    attachment_ai_missing_key: { label: "识图未配置", tone: "error" },
    gemini_text_fallback: { label: "Gemini 兜底", tone: "cloud-fallback" },
    gemini_error: { label: "Gemini 错误", tone: "error" },
    hybrid_rules: { label: "云端规则", tone: "cloud-rule" },
    cloud_command_bridge: { label: "云端指令", tone: "mobile" },
    command_protocol: { label: "云端指令", tone: "mobile" },
    gemini_structured: { label: "Gemini 指令", tone: "mobile" },
    weather_tool: { label: "实时天气", tone: "online" },
    weather_tool_memory: { label: "实时天气 · 记忆城市", tone: "online" },
    weather_need_location: { label: "天气待补充", tone: "cloud-fallback" },
    weather_error: { label: "天气错误", tone: "error" },
    wiki_tool: { label: "百科摘要", tone: "online" },
    webpage_tool: { label: "网页读取", tone: "online" },
    web_search_tool: { label: "联网搜索", tone: "online" },
    tavily_web_search: { label: "Tavily 搜索", tone: "online" },
    web_search_error: { label: "搜索错误", tone: "error" },
    search_not_configured: { label: "搜索未配置", tone: "cloud-fallback" },
    calculator_tool: { label: "计算器", tone: "utility" },
    datetime_tool: { label: "日期时间", tone: "utility" },
    builtin_profile: { label: "内置回复", tone: "builtin" },
    local: { label: "本地规则", tone: "local" },
    local_ledger: { label: "本地记账", tone: "local" },
    local_mobile: { label: "手机动作", tone: "mobile" },
    navigation_preferences: { label: "导航偏好", tone: "mobile" },
    ai_command_router_v3: { label: "本地指令路由", tone: "mobile" },
    missing_workers_ai_binding: { label: "云端未绑定", tone: "error" },
    missing_cloud_ai: { label: "云端未配置", tone: "error" },
    cloud_error_normalized: { label: "云端错误", tone: "error" },
    cloud_fetch_failed: { label: "云端连接失败", tone: "error" },
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
    const version = String(message.version || "");
    if (message.source) return message.source;
    if (/command-protocol|worker-command|cloud-command/i.test(version)) return "command_protocol";
    if (/tavily/i.test(version)) return "tavily_web_search";
    if (/open-meteo/i.test(version)) return "weather_tool";
    if (/gemini/i.test(version)) return "gemini_chat";
    if (message.mobileCommand) return "local_mobile";
    if (Array.isArray(message.records) && message.records.length) return "local";
    if (message.id === "welcome") return "builtin_profile";
    return "cloud_ai";
  }

  function sourceMeta(source) {
    if (SOURCE_LABELS[source]) return SOURCE_LABELS[source];
    if (/gemini/i.test(source || "")) return { label: "Gemini AI", tone: "gemini" };
    if (/tavily|search/i.test(source || "")) return { label: "联网搜索", tone: "online" };
    if (/weather/i.test(source || "")) return { label: "实时天气", tone: "online" };
    if (/worker/i.test(source || "")) return { label: "Workers AI", tone: "cloud" };
    return { label: "云端 AI", tone: "cloud" };
  }

  function compactVersion(version) {
    return String(version || "")
      .replace(/^ai-ledger-/i, "")
      .replace(/worker-/i, "")
      .replace(/orchestrator-/i, "orch-")
      .replace(/attachment-gateway-/i, "attach-")
      .replace(/command-protocol-/i, "cmd-")
      .slice(0, 80);
  }

  function modelText(message) {
    const parts = [];
    if (message?.modelLabel) parts.push(message.modelLabel);
    else if (message?.model) parts.push(message.model);
    else if (message?.provider && message?.model) parts.push(`${message.provider} ${message.model}`);

    const version = compactVersion(message?.version);
    if (version && !parts.some((part) => version.includes(part))) parts.push(version);
    return parts.filter(Boolean).join(" · ");
  }

  function installStyle() {
    const old = document.querySelector(`#${STYLE_ID}`);
    if (old) old.remove();
    const style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = `
      .chat-source-badge-row{display:flex;justify-content:flex-start;margin:7px 0 0 4px;gap:6px;flex-wrap:wrap}
      .chat-source-badge{display:inline-flex;align-items:center;gap:5px;border-radius:999px;padding:4px 8px;font-size:11px;font-weight:800;line-height:1;background:rgba(255,255,255,.18);border:1px solid rgba(255,255,255,.28);color:rgba(238,250,255,.78);backdrop-filter:blur(12px);-webkit-backdrop-filter:blur(12px);max-width:100%;word-break:break-word}
      .chat-source-badge::before{content:"";width:6px;height:6px;min-width:6px;border-radius:999px;background:currentColor;opacity:.85}
      .chat-source-badge.cloud{color:#83f7ff;background:rgba(33,197,255,.14);border-color:rgba(33,197,255,.28)}
      .chat-source-badge.gemini{color:#c7b7ff;background:rgba(126,87,255,.18);border-color:rgba(126,87,255,.35)}
      .chat-source-badge.vision{color:#ffd1fb;background:rgba(236,72,153,.16);border-color:rgba(236,72,153,.34)}
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

  function clearOldBadges() {
    document.querySelectorAll(".chat-source-badge-row").forEach((el) => el.remove());
    document.querySelectorAll(".chat-row.assistant[data-message-id]").forEach((row) => delete row.dataset.sourceBadgeReady);
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
      const detail = modelText(message);
      const detailText = detail ? ` · ${escapeHtml(detail)}` : "";
      response.insertAdjacentHTML("beforeend", `<div class="chat-source-badge-row"><span class="chat-source-badge ${escapeHtml(meta.tone)}">${escapeHtml(meta.label)}${detailText}</span></div>`);
      row.dataset.sourceBadgeReady = "ready";
    });
  }

  function installObserver() {
    const target = document.querySelector("#chatMessages");
    if (!target || target.dataset.sourceBadgeObserver === "ready") return;
    target.dataset.sourceBadgeObserver = "ready";
    const observer = new MutationObserver(() => addBadges());
    observer.observe(target, { childList: true, subtree: true });
    clearOldBadges();
    addBadges();
  }

  window.ChatSourceBadges = { refresh: () => { clearOldBadges(); addBadges(); }, labels: SOURCE_LABELS };

  window.addEventListener("DOMContentLoaded", () => {
    installStyle();
    window.setTimeout(installObserver, 0);
    window.setTimeout(installObserver, 300);
    window.setInterval(addBadges, 1800);
  });
})();
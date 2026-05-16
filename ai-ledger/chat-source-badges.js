(() => {
  /*
   * Ownership target:
   * This file currently owns chat source badges and cloud model picker behavior.
   *
   * Refactor note:
   * Source badge rendering can stay here.
   * Model picker state/sheet and fetch model-preference injection may later move to `model-picker.js`.
   * Keep all behavior unchanged until the model picker split is tested in an APK.
   */

  const CHAT_KEY = "ai-ledger-chat-v2";
  const STYLE_ID = "chat-source-badges-style";

  // model-picker ownership: later move model preference constants/state to model-picker.js.
  const PREF_KEY = "ai-ledger-model-preference-v1";
  const FETCH_PATCH_FLAG = "__aiLedgerModelPreferenceFetchPatched";

  // model-picker ownership: model registry used by the top capsule and model sheet.
  const MODELS = [
    { id: "auto", label: "自动", short: "自动", hint: "按额度和可用性自动切换" },
    { id: "kimi", label: "Kimi K2.6", short: "Kimi", hint: "只使用 Kimi，不自动切到其他模型" },
    { id: "mistral", label: "Mistral Medium 3.5", short: "Mistral", hint: "只使用 Mistral，不自动切到 Kimi/Gemini" },
    { id: "gemini", label: "Gemini 2.5 Flash", short: "Gemini", hint: "只使用 Gemini，不自动切到其他模型" },
    { id: "workers", label: "Workers AI", short: "Workers", hint: "只使用 Workers AI 兜底模型" },
  ];

  // source-badge ownership: maps assistant source ids to visible badge labels.
  const SOURCE_LABELS = {
    cloud_ai: { label: "云端 AI", tone: "cloud" },
    nvidia_chat: { label: "NVIDIA NIM", tone: "cloud" },
    tavily_ai_summary: { label: "联网总结", tone: "online" },
    workers_ai: { label: "Workers AI", tone: "cloud" },
    workers_ai_text_fallback: { label: "Workers AI", tone: "cloud-fallback" },
    workers_ai_vision: { label: "Workers AI 识图", tone: "vision" },
    workers_ai_vision_fallback: { label: "Workers AI 识图兜底", tone: "vision" },
    nvidia_vision: { label: "NVIDIA 识图", tone: "vision" },
    nvidia_vision_fallback: { label: "NVIDIA 识图兜底", tone: "vision" },
    selected_model_failed: { label: "所选模型失败", tone: "error" },
    nvidia_chat_fallback: { label: "NVIDIA 兜底", tone: "cloud-fallback" },
    gemini_ai: { label: "Gemini AI", tone: "gemini" },
    gemini_chat: { label: "Gemini 对话", tone: "gemini" },
    gemini_chat_error: { label: "Gemini 错误", tone: "error" },
    gemini_missing_key: { label: "Gemini 未配置", tone: "error" },
    gemini_vision: { label: "Gemini 识图", tone: "vision" },
    gemini_vision_fallback: { label: "Gemini 识图兜底", tone: "vision" },
    gemini_vision_error: { label: "识图错误", tone: "error" },
    vision_all_failed: { label: "识图失败", tone: "error" },
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
    cloud_error_normalized: { label: "云端错误", tone: "error" },
    cloud_fetch_failed: { label: "云端连接失败", tone: "error" },
    provider_pool_failed: { label: "模型池失败", tone: "error" },
  };

  let lastPinnedAt = 0;

  function escapeHtml(value) {
    return String(value || "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");
  }

  function readMessages() {
    try {
      const parsed = JSON.parse(localStorage.getItem(CHAT_KEY) || "[]");
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  // model-picker ownership starts here.
  function readModelPreference() {
    try {
      const parsed = JSON.parse(localStorage.getItem(PREF_KEY) || "{}");
      const value = String(parsed.model || "auto");
      return MODELS.some((item) => item.id === value) ? value : "auto";
    } catch {
      return "auto";
    }
  }

  function writeModelPreference(model) {
    const value = MODELS.some((item) => item.id === model) ? model : "auto";
    localStorage.setItem(PREF_KEY, JSON.stringify({ model: value, updatedAt: Date.now() }));
    updateModelButton();
  }

  function modelShort() {
    return (MODELS.find((item) => item.id === readModelPreference()) || MODELS[0]).short;
  }
  // model-picker ownership ends here for preference helpers.

  // source-badge ownership starts here.
  function inferSource(message) {
    if (!message || message.role !== "assistant") return null;
    const version = String(message.version || "");
    if (message.source) return message.source;
    if (/command-protocol|worker-command|cloud-command/i.test(version)) return "command_protocol";
    if (/tavily/i.test(version)) return "tavily_web_search";
    if (/open-meteo/i.test(version)) return "weather_tool";
    if (/gemini/i.test(version)) return "gemini_chat";
    if (/kimi|nvidia|mistral|qwen|deepseek/i.test(version)) return "nvidia_chat";
    if (message.mobileCommand) return "local_mobile";
    if (Array.isArray(message.records) && message.records.length) return "local";
    if (message.id === "welcome") return "builtin_profile";
    return "cloud_ai";
  }

  function nvidiaLabelFromMessage(message) {
    const text = `${message?.modelLabel || ""} ${message?.model || ""} ${message?.version || ""}`.toLowerCase();
    if (text.includes("kimi")) return "Kimi 对话";
    if (text.includes("mistral")) return "Mistral 对话";
    if (text.includes("qwen")) return "Qwen 对话";
    if (text.includes("deepseek")) return "DeepSeek 对话";
    return "NVIDIA NIM";
  }

  function sourceMeta(source, message) {
    if (source === "nvidia_chat") return { label: nvidiaLabelFromMessage(message), tone: "cloud" };
    if (source === "nvidia_vision" || source === "nvidia_vision_fallback") return { label: nvidiaLabelFromMessage(message).replace("对话", "识图"), tone: "vision" };
    if (SOURCE_LABELS[source]) return SOURCE_LABELS[source];
    if (/vision|image|attachment/i.test(source || "")) return { label: "识图", tone: "vision" };
    if (/nvidia|nim|kimi|qwen|mistral|deepseek/i.test(source || "")) return { label: "NVIDIA NIM", tone: "cloud" };
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
      .slice(0, 90);
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

  function attachmentMeta(message) {
    const list = Array.isArray(message?.attachments) ? message.attachments : [];
    if (!list.length) return null;
    const imageCount = list.filter((item) => String(item.mimeType || "").startsWith("image/")).length;
    const pdfCount = list.filter((item) => /pdf/i.test(String(item.mimeType || ""))).length;
    const fileCount = list.length - imageCount - pdfCount;
    const labels = [];
    if (imageCount) labels.push(imageCount === 1 ? "本图" : `${imageCount}张图片`);
    if (pdfCount) labels.push(pdfCount === 1 ? "PDF" : `${pdfCount}个PDF`);
    if (fileCount) labels.push(fileCount === 1 ? "文件" : `${fileCount}个文件`);
    const names = list.map((item) => item.name).filter(Boolean).slice(0, 2).join("、");
    return { label: `已附带 ${labels.join("+") || "附件"}`, detail: names };
  }
  // source-badge ownership ends here for metadata helpers.

  // mixed ownership: style injection includes source badges, model picker, and typing indicator styles.
  // Later split into chat-badges.css, model-picker.css, and typing-indicator.css.
  function installStyle() {
    const old = document.querySelector(`#${STYLE_ID}`);
    if (old) old.remove();
    const style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = `
      #chatMessages{scroll-padding-bottom:28px!important;padding-bottom:12px!important}
      .chat-row,.chat-response,.chat-bubble{overflow:visible!important}
      .chat-source-badge-row{display:flex;justify-content:flex-start;margin:8px 0 2px 4px;gap:6px;flex-wrap:wrap;min-height:22px;position:relative;z-index:3}
      .chat-row.user .chat-source-badge-row{justify-content:flex-end;margin:7px 4px 2px 0}
      .chat-source-badge{display:inline-flex;align-items:center;gap:5px;border-radius:999px;padding:5px 9px;font-size:11px;font-weight:800;line-height:1.12;background:rgba(255,255,255,.18);border:1px solid rgba(255,255,255,.28);color:rgba(238,250,255,.78);backdrop-filter:blur(12px);-webkit-backdrop-filter:blur(12px);max-width:100%;word-break:break-word;box-sizing:border-box}
      .chat-source-badge::before{content:"";width:6px;height:6px;min-width:6px;border-radius:999px;background:currentColor;opacity:.85}
      .chat-source-badge.cloud{color:#83f7ff;background:rgba(33,197,255,.14);border-color:rgba(33,197,255,.28)}
      .chat-source-badge.gemini{color:#c7b7ff;background:rgba(126,87,255,.18);border-color:rgba(126,87,255,.35)}
      .chat-source-badge.vision{color:#ffd1fb;background:rgba(236,72,153,.16);border-color:rgba(236,72,153,.34)}
      .chat-source-badge.attachment{color:#e5edff;background:rgba(148,163,255,.18);border-color:rgba(181,190,255,.34)}
      .chat-source-badge.online{color:#8ff7c4;background:rgba(22,190,121,.16);border-color:rgba(22,190,121,.34)}
      .chat-source-badge.utility{color:#ffe38f;background:rgba(240,180,50,.16);border-color:rgba(240,180,50,.32)}
      .chat-source-badge.cloud-fallback{color:#ffd28a;background:rgba(255,189,91,.14);border-color:rgba(255,189,91,.32)}
      .chat-source-badge.cloud-rule{color:#a7f3d0;background:rgba(16,185,129,.14);border-color:rgba(16,185,129,.28)}
      .chat-source-badge.builtin{color:#e7ddff;background:rgba(161,117,255,.16);border-color:rgba(161,117,255,.30)}
      .chat-source-badge.local{color:#cbd5e1;background:rgba(148,163,184,.16);border-color:rgba(148,163,184,.28)}
      .chat-source-badge.mobile{color:#86ece2;background:rgba(11,143,139,.18);border-color:rgba(11,143,139,.32)}
      .chat-source-badge.error{color:#ffb4b4;background:rgba(255,91,91,.15);border-color:rgba(255,91,91,.30)}
      .model-picker-btn{width:48px;height:48px;min-width:48px;border-radius:20px;border:1px solid rgba(255,255,255,.30);background:linear-gradient(145deg,rgba(255,255,255,.20),rgba(255,255,255,.08));color:rgba(255,255,255,.92);font-size:10px;font-weight:950;display:grid;place-items:center;text-align:center;line-height:1.05;backdrop-filter:blur(16px) saturate(150%);-webkit-backdrop-filter:blur(16px) saturate(150%);box-shadow:inset 0 1px 0 rgba(255,255,255,.28),0 10px 24px rgba(0,0,0,.12)}
      .model-picker-btn::before{content:'AI';font-size:10px;opacity:.7;margin-bottom:1px;display:block}.model-picker-btn:active{transform:scale(.96)}
      .model-picker-sheet-mask{position:fixed;inset:0;z-index:1300;display:none;background:rgba(4,8,20,.30);backdrop-filter:blur(8px);-webkit-backdrop-filter:blur(8px)}.model-picker-sheet-mask.open{display:grid;place-items:end center}
      .model-picker-sheet{width:min(94vw,520px);margin:0 0 max(14px,env(safe-area-inset-bottom));border-radius:30px;padding:16px;background:linear-gradient(145deg,rgba(246,250,255,.24),rgba(255,255,255,.10) 58%,rgba(255,255,255,.06)),rgba(40,48,84,.56);border:1px solid rgba(255,255,255,.28);box-shadow:0 28px 80px rgba(0,0,0,.34),inset 0 1px 0 rgba(255,255,255,.35);color:rgba(255,255,255,.94);backdrop-filter:blur(26px) saturate(170%);-webkit-backdrop-filter:blur(26px) saturate(170%);animation:modelSheetIn .22s cubic-bezier(.2,.9,.2,1)}
      @keyframes modelSheetIn{from{transform:translateY(18px) scale(.98);opacity:.3}to{transform:none;opacity:1}}
      .model-picker-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:12px}.model-picker-head strong{display:block;font-size:18px;letter-spacing:-.03em}.model-picker-head span{display:block;margin-top:4px;font-size:12px;opacity:.68}.model-picker-close{width:34px;height:34px;border:0;border-radius:999px;background:rgba(255,255,255,.16);color:inherit;font-size:22px;line-height:1}.model-picker-list{display:grid;gap:9px}.model-choice{display:flex;align-items:center;gap:10px;width:100%;padding:12px;border-radius:20px;border:1px solid rgba(255,255,255,.18);background:rgba(255,255,255,.10);color:inherit;text-align:left}.model-choice.active{background:linear-gradient(135deg,rgba(99,226,255,.24),rgba(145,106,255,.20));border-color:rgba(132,221,255,.42);box-shadow:inset 0 1px 0 rgba(255,255,255,.28)}.model-choice-dot{width:11px;height:11px;border-radius:999px;border:2px solid rgba(255,255,255,.52);box-shadow:0 0 0 4px rgba(255,255,255,.05)}.model-choice.active .model-choice-dot{background:#8bf7ff;border-color:#8bf7ff;box-shadow:0 0 18px rgba(139,247,255,.55)}.model-choice-text{display:grid;gap:3px}.model-choice-text strong{font-size:14px}.model-choice-text em{font-size:12px;font-style:normal;opacity:.66;line-height:1.35}
      #typingRow .chat-bubble{min-width:168px;min-height:44px;display:inline-flex;align-items:center;justify-content:flex-start;gap:8px;padding:12px 16px;border-radius:24px;background:linear-gradient(135deg,rgba(255,255,255,.19),rgba(255,255,255,.08)),rgba(138,118,255,.08);position:relative;overflow:hidden!important;border:1px solid rgba(255,255,255,.20);box-shadow:inset 0 1px 0 rgba(255,255,255,.28),0 12px 28px rgba(25,20,60,.18)}
      #typingRow .chat-bubble::before{content:'正在生成';position:relative;z-index:2;font-size:13px;font-weight:850;color:rgba(255,255,255,.86);letter-spacing:.02em;text-shadow:0 1px 8px rgba(255,255,255,.20)}
      #typingRow .chat-bubble::after{content:'';position:absolute;inset:0;background:linear-gradient(105deg,transparent 0%,rgba(139,247,255,.00) 24%,rgba(139,247,255,.22) 44%,rgba(188,160,255,.28) 52%,rgba(139,247,255,.00) 70%,transparent 100%);transform:translateX(-120%);animation:aiLiquidSweep 1.9s ease-in-out infinite;pointer-events:none}
      #typingRow .typing-dot{position:relative;z-index:2;display:inline-block!important;width:6px;height:6px;border-radius:999px;background:rgba(210,235,255,.90);box-shadow:0 0 10px rgba(149,217,255,.55);animation:aiSoftDot 1.15s ease-in-out infinite}.typing-dot:nth-child(2){animation-delay:.16s}.typing-dot:nth-child(3){animation-delay:.32s}
      @keyframes aiLiquidSweep{0%{transform:translateX(-120%);opacity:.25}45%{opacity:.95}100%{transform:translateX(120%);opacity:.25}}@keyframes aiSoftDot{0%,100%{transform:translateY(2px) scale(.72);opacity:.42}50%{transform:translateY(-1px) scale(1.06);opacity:1}}
      body.assistant-compact .chat-source-badge-row{margin-top:5px}body.assistant-compact .chat-source-badge{font-size:10px;padding:4px 8px}body.assistant-compact .model-picker-btn{width:44px;height:44px;min-width:44px;border-radius:18px;font-size:9px}
    `;
    document.head.appendChild(style);
  }

  // source-badge ownership: remove repeated badge rows from chat bubbles.
  function removeDuplicateBadges(row) {
    const badges = row.querySelectorAll(":scope .chat-source-badge-row");
    badges.forEach((badge, index) => { if (index > 0) badge.remove(); });
  }

  // source-badge ownership: insert visible source/model/file badges into rendered chat rows.
  function addBadges() {
    let inserted = false;
    const messages = readMessages();
    const byId = new Map(messages.map((message) => [String(message.id), message]));
    document.querySelectorAll(".chat-row[data-message-id]").forEach((row) => {
      const id = row.dataset.messageId;
      if (!id) return;
      removeDuplicateBadges(row);
      if (row.querySelector(":scope .chat-source-badge-row")) {
        row.dataset.sourceBadgeReady = "ready";
        return;
      }
      const message = byId.get(String(id));
      if (!message) return;
      const response = row.querySelector(".chat-response");
      if (!response) return;
      if (message.role === "user") {
        const att = attachmentMeta(message);
        if (att) {
          const detail = att.detail ? ` · ${escapeHtml(att.detail)}` : "";
          response.insertAdjacentHTML("beforeend", `<div class="chat-source-badge-row"><span class="chat-source-badge attachment">${escapeHtml(att.label)}${detail}</span></div>`);
          inserted = true;
        }
        row.dataset.sourceBadgeReady = "ready";
        return;
      }
      if (message.role === "assistant") {
        const source = inferSource(message);
        const meta = sourceMeta(source, message);
        const detail = modelText(message);
        const detailText = detail ? ` · ${escapeHtml(detail)}` : "";
        response.insertAdjacentHTML("beforeend", `<div class="chat-source-badge-row"><span class="chat-source-badge ${escapeHtml(meta.tone)}">${escapeHtml(meta.label)}${detailText}</span></div>`);
        row.dataset.sourceBadgeReady = "ready";
        inserted = true;
      }
    });
    if (inserted) pinChatBottom("badge-insert");
  }

  // source-badge ownership: after inserting badges, keep the latest message visible.
  function pinChatBottom(reason = "") {
    const host = document.querySelector("#chatMessages");
    if (!host) return;
    lastPinnedAt = Date.now();
    const run = () => {
      try { host.scrollTop = host.scrollHeight + 80; } catch {}
    };
    requestAnimationFrame(run);
    [24, 80, 180, 360, 700].forEach((delay) => window.setTimeout(run, delay));
  }

  function shouldPinAfterMutation(mutations) {
    return mutations.some((mutation) => [...mutation.addedNodes, ...mutation.removedNodes].some((node) => {
      if (!(node instanceof HTMLElement)) return false;
      return node.id === "typingRow" || node.classList?.contains("chat-row") || node.querySelector?.(".chat-row") || node.classList?.contains("chat-source-badge-row");
    }));
  }

  // source-badge ownership: watch chat DOM changes and add badges to newly rendered rows.
  function installObserver() {
    const target = document.querySelector("#chatMessages");
    if (!target || target.dataset.sourceBadgeObserver === "ready") return;
    target.dataset.sourceBadgeObserver = "ready";
    const observer = new MutationObserver((mutations) => {
      addBadges();
      if (shouldPinAfterMutation(mutations)) pinChatBottom("message-mutation");
    });
    observer.observe(target, { childList: true, subtree: true });
    addBadges();
    pinChatBottom("boot");
  }

  // model-picker ownership: create the small model picker button near the composer.
  function installModelButton() {
    const form = document.querySelector("#chatForm");
    const input = document.querySelector("#aiInput");
    if (!form || !input || document.querySelector("#chatModelPickerBtn")) return;
    const btn = document.createElement("button");
    btn.id = "chatModelPickerBtn";
    btn.className = "model-picker-btn";
    btn.type = "button";
    btn.setAttribute("aria-label", "选择云端模型");
    btn.textContent = modelShort();
    const attachBtn = document.querySelector("#chatAttachBtn");
    if (attachBtn?.parentNode === form) form.insertBefore(btn, attachBtn.nextSibling);
    else form.insertBefore(btn, input);
    btn.addEventListener("click", openModelSheet);
  }

  // model-picker ownership: render the model picker bottom sheet.
  function renderModelSheet() {
    let mask = document.querySelector("#modelPickerSheetMask");
    if (!mask) {
      mask = document.createElement("div");
      mask.id = "modelPickerSheetMask";
      mask.className = "model-picker-sheet-mask";
      document.body.appendChild(mask);
      mask.addEventListener("click", (event) => {
        if (event.target === mask || event.target.closest("[data-model-picker-close]")) closeModelSheet();
        const choice = event.target.closest("[data-model-choice]");
        if (!choice) return;
        writeModelPreference(choice.dataset.modelChoice);
        renderModelSheet();
        closeModelSheet();
      });
    }
    const selected = readModelPreference();
    mask.innerHTML = `<section class="model-picker-sheet" role="dialog" aria-modal="true" aria-label="选择云端模型"><div class="model-picker-head"><div><strong>选择云端模型</strong><span>自动模式才会切换模型；手动选 Kimi / Mistral / Gemini / Workers 时会严格使用所选模型。</span></div><button class="model-picker-close" type="button" data-model-picker-close>×</button></div><div class="model-picker-list">${MODELS.map((item) => `<button type="button" class="model-choice ${item.id === selected ? "active" : ""}" data-model-choice="${escapeHtml(item.id)}"><span class="model-choice-dot"></span><span class="model-choice-text"><strong>${escapeHtml(item.label)}</strong><em>${escapeHtml(item.hint)}</em></span></button>`).join("")}</div></section>`;
  }

  function openModelSheet() { renderModelSheet(); document.querySelector("#modelPickerSheetMask")?.classList.add("open"); }
  function closeModelSheet() { document.querySelector("#modelPickerSheetMask")?.classList.remove("open"); }
  function updateModelButton() { const btn = document.querySelector("#chatModelPickerBtn"); if (btn) btn.textContent = modelShort(); }

  // model-picker ownership: inject selected model preference into cloud/chat requests.
  function patchFetch() {
    if (window[FETCH_PATCH_FLAG]) return;
    window[FETCH_PATCH_FLAG] = true;
    const nativeFetch = window.fetch.bind(window);
    window.fetch = async (input, init = {}) => {
      try {
        const method = String(init?.method || (input instanceof Request ? input.method : "GET")).toUpperCase();
        const body = init?.body;
        if (method === "POST" && typeof body === "string" && (body.includes("messages") || body.includes("attachments"))) {
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

  function badgeWatchdog() {
    addBadges();
    const host = document.querySelector("#chatMessages");
    if (!host) return;
    const bottomGap = host.scrollHeight - host.clientHeight - host.scrollTop;
    if (document.querySelector("#typingRow") || (Date.now() - lastPinnedAt < 1800 && bottomGap > 6)) pinChatBottom("watchdog");
  }

  window.ChatSourceBadges = { refresh: addBadges, labels: SOURCE_LABELS, pinBottom: pinChatBottom };
  window.AiLedgerModelPicker = { current: readModelPreference, set: writeModelPreference, models: MODELS };

  window.addEventListener("DOMContentLoaded", () => {
    installStyle();
    patchFetch();
    installModelButton();
    window.setTimeout(installObserver, 0);
    window.setTimeout(installObserver, 300);
    window.setInterval(() => { addBadges(); installModelButton(); updateModelButton(); badgeWatchdog(); }, 700);
  });
})();
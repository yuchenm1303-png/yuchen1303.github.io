(() => {
  const CHAT_KEY = "ai-ledger-chat-v2";
  const MOBILE_STYLE_ID = "mobile-command-style";
  const initialChat = [
    {
      id: "welcome",
      role: "assistant",
      content: "你好，我是你的 AI 记账助手。你可以直接和我说：今天午饭28；也可以问我：这个月餐饮花了多少。",
      action: "chat",
      records: [],
      draftState: "none",
    },
  ];

  function clearConversation() {
    const ok = window.confirm("确定清空当前对话吗？账单记录不会被删除。");
    if (!ok) return;
    localStorage.setItem(CHAT_KEY, JSON.stringify(initialChat));
    window.location.reload();
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

  function readChatMessages() {
    try {
      const parsed = JSON.parse(localStorage.getItem(CHAT_KEY) || "[]");
      return Array.isArray(parsed) && parsed.length ? parsed : [...initialChat];
    } catch {
      return [...initialChat];
    }
  }

  function saveChatMessages(messages) {
    localStorage.setItem(CHAT_KEY, JSON.stringify(messages.slice(-60)));
  }

  function addDays(date, days) {
    const next = new Date(date);
    next.setDate(next.getDate() + days);
    return next;
  }

  function pad2(value) {
    return String(value).padStart(2, "0");
  }

  function toIsoDate(date) {
    return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
  }

  function formatDisplayDate(date) {
    return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`;
  }

  function normalizeMeridiem(hour, text) {
    if (/下午|晚上|傍晚|今晚/u.test(text) && hour < 12) return hour + 12;
    if (/中午/u.test(text) && hour < 11) return hour + 12;
    if (/凌晨|早上|上午|明早|明天早上/u.test(text) && hour === 12) return 0;
    return hour;
  }

  function parseAlarmCommand(text) {
    if (!/(闹钟|叫我|提醒我|提醒一下|叫醒|起床)/u.test(text)) return null;
    const timeMatch = text.match(/(\d{1,2})(?:[:：点时](\d{1,2})?分?)?/u);
    if (!timeMatch) return null;

    let hour = Number(timeMatch[1]);
    const minute = Number(timeMatch[2] || 0);
    if (!Number.isFinite(hour) || !Number.isFinite(minute) || minute < 0 || minute > 59) return null;
    hour = normalizeMeridiem(hour, text);
    if (hour < 0 || hour > 23) return null;

    const now = new Date();
    let target = new Date(now);
    if (/后天/u.test(text)) target = addDays(now, 2);
    else if (/明天|明早|明晚/u.test(text)) target = addDays(now, 1);
    else if (/今天|今晚/u.test(text)) target = new Date(now);
    else {
      const candidate = new Date(now);
      candidate.setHours(hour, minute, 0, 0);
      if (candidate <= now) target = addDays(now, 1);
    }
    target.setHours(hour, minute, 0, 0);

    const labelMatch = text.match(/(?:提醒我|叫我|叫醒我|闹钟)(.*)$/u);
    const rawLabel = labelMatch?.[1] || "";
    const label = rawLabel
      .replace(/(明天|后天|今天|今晚|明早|明晚|上午|下午|晚上|早上|凌晨|中午)/gu, "")
      .replace(/\d{1,2}(?:[:：点时]\d{0,2})?分?/gu, "")
      .replace(/^(去|要|一下|起床)/u, "")
      .trim() || (/起床|叫醒/u.test(text) ? "起床" : "提醒");

    return {
      type: "set_alarm",
      title: "设置闹钟",
      summary: `${formatDisplayDate(target)} ${pad2(hour)}:${pad2(minute)}`,
      params: {
        date: toIsoDate(target),
        hour,
        minute,
        label,
      },
    };
  }

  function parseOpenAppCommand(text) {
    const match = text.match(/(?:打开|启动|帮我打开)\s*([\u4e00-\u9fa5A-Za-z0-9]+)$/u);
    if (!match) return null;
    const appName = match[1].trim();
    if (!appName || /(闹钟|提醒|记账)/u.test(appName)) return null;
    return {
      type: "open_app",
      title: "打开应用",
      summary: appName,
      params: { appName },
    };
  }

  function parseMobileCommand(text) {
    return parseAlarmCommand(text) || parseOpenAppCommand(text);
  }

  function getStatusText(state) {
    if (state === "done") return "已执行";
    if (state === "cancelled") return "已取消";
    if (state === "failed") return "执行失败";
    return "待确认";
  }

  function getActionRows(command) {
    if (command.type === "set_alarm") {
      return [
        ["动作", "设置闹钟"],
        ["时间", command.summary],
        ["标签", command.params.label],
      ];
    }
    if (command.type === "open_app") {
      return [
        ["动作", "打开应用"],
        ["应用", command.params.appName],
      ];
    }
    return [["动作", command.title || command.type]];
  }

  function renderMobileCard(command, state = "pending", message = "") {
    const rows = getActionRows(command)
      .map(([key, value]) => `<div class="mobile-command-row"><span>${escapeHtml(key)}</span><strong>${escapeHtml(value)}</strong></div>`)
      .join("");
    const buttons = state === "pending"
      ? `<div class="mobile-command-actions">
          <button class="mobile-command-confirm" type="button" data-mobile-run="${escapeHtml(command.id)}">确认执行</button>
          <button class="mobile-command-cancel" type="button" data-mobile-cancel="${escapeHtml(command.id)}">取消</button>
        </div>`
      : "";
    const note = message ? `<div class="mobile-command-message">${escapeHtml(message)}</div>` : "";
    return `<div class="mobile-command-card" data-mobile-card="${escapeHtml(command.id)}">
      <div class="mobile-command-head">
        <span class="mobile-command-title">${escapeHtml(command.title)}</span>
        <span class="mobile-command-status ${escapeHtml(state)}">${getStatusText(state)}</span>
      </div>
      <div class="mobile-command-detail">${rows}</div>
      ${buttons}
      ${note}
    </div>`;
  }

  function appendChatRow(role, content, command) {
    const chat = document.querySelector("#chatMessages");
    if (!chat) return;
    if (role === "user") {
      chat.insertAdjacentHTML("beforeend", `<div class="chat-row user"><div class="chat-bubble">${escapeHtml(content)}</div></div>`);
    } else {
      const card = command ? renderMobileCard(command) : "";
      chat.insertAdjacentHTML("beforeend", `<div class="chat-row assistant mobile-command-extra"><div class="chat-response"><div class="chat-bubble">${escapeHtml(content)}</div>${card}</div></div>`);
    }
    chat.scrollTop = chat.scrollHeight;
  }

  function pushMobileConversation(userText, assistantText, command) {
    const messages = readChatMessages();
    messages.push({ id: createId(), role: "user", content: userText });
    messages.push({
      id: createId(),
      role: "assistant",
      content: assistantText,
      action: "mobile_command",
      records: [],
      draftState: "none",
      mobileCommand: command,
    });
    saveChatMessages(messages);
  }

  function getCapacitorPlugin() {
    return window.Capacitor?.Plugins?.MobileAssistant || window.Capacitor?.Plugins?.MobileTools || null;
  }

  async function executeCommand(command) {
    const plugin = getCapacitorPlugin();
    if (!plugin) {
      return {
        ok: false,
        message: "当前网页端只能生成动作指令；打包到 Android 后，需要接入原生插件才能真正执行。",
      };
    }
    if (command.type === "set_alarm" && typeof plugin.setAlarm === "function") {
      return await plugin.setAlarm(command.params);
    }
    if (command.type === "open_app" && typeof plugin.openApp === "function") {
      return await plugin.openApp(command.params);
    }
    return { ok: false, message: "Android 插件还没有实现这个动作。" };
  }

  function updateCard(commandId, state, message) {
    const card = document.querySelector(`[data-mobile-card="${CSS.escape(commandId)}"]`);
    if (!card) return;
    const status = card.querySelector(".mobile-command-status");
    if (status) {
      status.className = `mobile-command-status ${state}`;
      status.textContent = getStatusText(state);
    }
    card.querySelector(".mobile-command-actions")?.remove();
    card.querySelector(".mobile-command-message")?.remove();
    if (message) card.insertAdjacentHTML("beforeend", `<div class="mobile-command-message">${escapeHtml(message)}</div>`);
  }

  function installMobileStyles() {
    if (document.querySelector(`#${MOBILE_STYLE_ID}`)) return;
    const style = document.createElement("style");
    style.id = MOBILE_STYLE_ID;
    style.textContent = `
      .mobile-command-extra .chat-response{width:min(100%,520px)}
      .mobile-command-card{margin-top:10px;padding:14px;border:1px solid rgba(255,255,255,.42);border-radius:20px;background:rgba(255,255,255,.62);box-shadow:0 18px 45px rgba(9,35,66,.12);backdrop-filter:blur(18px)}
      .mobile-command-head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:10px}
      .mobile-command-title{font-size:15px;font-weight:800;color:#102033}
      .mobile-command-status{flex:0 0 auto;padding:4px 9px;border-radius:999px;font-size:12px;font-weight:700;background:rgba(11,143,139,.12);color:#0b6e69}
      .mobile-command-status.done{background:rgba(17,125,83,.12);color:#117d53}
      .mobile-command-status.cancelled,.mobile-command-status.failed{background:rgba(197,69,69,.12);color:#a13a3a}
      .mobile-command-detail{display:grid;gap:8px;margin:10px 0 12px}
      .mobile-command-row{display:flex;justify-content:space-between;gap:18px;color:#607083;font-size:13px}
      .mobile-command-row strong{color:#172033;font-weight:800;text-align:right}
      .mobile-command-actions{display:flex;gap:10px;flex-wrap:wrap}
      .mobile-command-actions button{border:0;border-radius:999px;padding:9px 14px;font-weight:800;cursor:pointer}
      .mobile-command-confirm{color:white;background:linear-gradient(135deg,#0b8f8b,#086a73)}
      .mobile-command-cancel{color:#607083;background:rgba(255,255,255,.72)}
      .mobile-command-message{margin-top:10px;color:#607083;font-size:13px;line-height:1.5}
    `;
    document.head.appendChild(style);
  }

  function interceptMobileSubmit() {
    const form = document.querySelector("#chatForm");
    const input = document.querySelector("#aiInput");
    if (!form || !input) return;

    form.addEventListener("submit", (event) => {
      const text = input.value.trim();
      const parsed = parseMobileCommand(text);
      if (!parsed) return;

      event.preventDefault();
      event.stopImmediatePropagation();

      const command = { id: createId(), ...parsed };
      const reply = command.type === "set_alarm"
        ? `我理解为要${command.summary}设置“${command.params.label}”闹钟，确认后我再执行。`
        : `我理解为要打开“${command.params.appName}”，确认后我再执行。`;

      input.value = "";
      appendChatRow("user", text);
      appendChatRow("assistant", reply, command);
      pushMobileConversation(text, reply, command);
    }, true);
  }

  function installMobileCardHandlers() {
    document.addEventListener("click", async (event) => {
      const runBtn = event.target.closest("[data-mobile-run]");
      const cancelBtn = event.target.closest("[data-mobile-cancel]");
      if (!runBtn && !cancelBtn) return;

      const commandId = runBtn?.dataset.mobileRun || cancelBtn?.dataset.mobileCancel;
      const messages = readChatMessages();
      const message = [...messages].reverse().find((item) => item.mobileCommand?.id === commandId);
      const command = message?.mobileCommand;
      if (!command) return;

      if (cancelBtn) {
        updateCard(commandId, "cancelled", "已取消执行。账单和其他数据不会受影响。");
        return;
      }

      updateCard(commandId, "pending", "正在调用 Android 能力……");
      try {
        const result = await executeCommand(command);
        if (result?.ok) {
          updateCard(commandId, "done", result.message || "已执行。");
        } else {
          updateCard(commandId, "failed", result?.message || "暂时无法执行这个动作。");
        }
      } catch (error) {
        updateCard(commandId, "failed", String(error?.message || error || "执行失败"));
      }
    });
  }

  window.addEventListener("DOMContentLoaded", () => {
    document.querySelector("#clearChatInlineBtn")?.addEventListener("click", clearConversation);
    installMobileStyles();
    interceptMobileSubmit();
    installMobileCardHandlers();
  });
})();

(() => {
  const MAX_FILES = 3;
  const MAX_FILE_BYTES = 4 * 1024 * 1024;
  const ACCEPT = "image/*,.pdf,.txt,.md,.csv,.json,.html,.htm,.js,.css,.py,.java,.c,.cpp,.h";
  const CHAT_KEY = "ai-ledger-chat-v2";
  const AI_CONFIG_KEY = "ai-ledger-ai-config-v1";
  let pendingAttachments = [];

  function escapeHtml(value) {
    return String(value || "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function toast(message) {
    const el = document.querySelector("#toast");
    if (!el) return;
    el.textContent = message;
    el.classList.add("show");
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => el.classList.remove("show"), 2200);
  }

  function createId(prefix = "att") {
    if (crypto.randomUUID) return `${prefix}-${crypto.randomUUID()}`;
    return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  function readAiEndpoint() {
    try {
      const saved = JSON.parse(localStorage.getItem(AI_CONFIG_KEY) || "{}");
      return String(saved.endpoint || window.AI_LEDGER_CONFIG?.aiEndpoint || "").replace(/\/+$/g, "");
    } catch {
      return String(window.AI_LEDGER_CONFIG?.aiEndpoint || "").replace(/\/+$/g, "");
    }
  }

  function readChatMessages() {
    if (Array.isArray(window.chatMessages)) return window.chatMessages;
    try {
      const parsed = JSON.parse(localStorage.getItem(CHAT_KEY) || "[]");
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  function saveChatMessages(messages) {
    try {
      localStorage.setItem(CHAT_KEY, JSON.stringify(messages));
      if (Array.isArray(window.chatMessages)) {
        window.chatMessages.length = 0;
        messages.forEach((message) => window.chatMessages.push(message));
      }
      if (typeof window.saveChatMessages === "function") window.saveChatMessages();
      if (typeof window.renderAll === "function") window.renderAll();
      else window.ChatSourceBadges?.refresh?.();
    } catch {}
  }

  function todayISO() {
    return new Date().toISOString().slice(0, 10);
  }

  function conversationPayload(messages) {
    return messages
      .filter((message) => message.role === "user" || message.role === "assistant")
      .slice(-16)
      .map((message) => ({ role: message.role, content: message.content }));
  }

  function ledgerContext() {
    return { summary: {}, recentRecords: [] };
  }

  function fileToAttachment(file) {
    return new Promise((resolve, reject) => {
      if (!file) return reject(new Error("文件无效"));
      if (file.size > MAX_FILE_BYTES) return reject(new Error(`${file.name} 超过 4MB，先压缩或换小一点的文件`));
      const reader = new FileReader();
      reader.onload = () => {
        const dataUrl = String(reader.result || "");
        const comma = dataUrl.indexOf(",");
        const base64 = comma >= 0 ? dataUrl.slice(comma + 1) : dataUrl;
        resolve({ id: createId("file"), name: file.name || "未命名文件", mimeType: file.type || guessMime(file.name), size: file.size, data: base64 });
      };
      reader.onerror = () => reject(new Error(`${file.name} 读取失败`));
      reader.readAsDataURL(file);
    });
  }

  function guessMime(name) {
    const lower = String(name || "").toLowerCase();
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
    if (lower.endsWith(".png")) return "image/png";
    if (lower.endsWith(".webp")) return "image/webp";
    if (lower.endsWith(".pdf")) return "application/pdf";
    if (lower.endsWith(".txt") || lower.endsWith(".md")) return "text/plain";
    if (lower.endsWith(".csv")) return "text/csv";
    if (lower.endsWith(".json")) return "application/json";
    if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
    if (lower.endsWith(".js")) return "text/javascript";
    if (lower.endsWith(".css")) return "text/css";
    return "application/octet-stream";
  }

  function iconFor(att) {
    const type = att.mimeType || "";
    if (type.startsWith("image/")) return "🖼";
    if (type.includes("pdf")) return "📄";
    if (type.startsWith("text/") || /json|csv|javascript|css/.test(type)) return "📝";
    return "📎";
  }

  function renderTray() {
    const tray = document.querySelector("#attachmentTray");
    if (!tray) return;
    if (!pendingAttachments.length) {
      tray.innerHTML = "";
      tray.classList.remove("show");
      document.body.classList.remove("has-chat-attachments");
      return;
    }
    document.body.classList.add("has-chat-attachments");
    tray.classList.add("show");
    tray.innerHTML = pendingAttachments.map((att) => `
      <div class="attachment-pill" data-attachment-id="${escapeHtml(att.id)}">
        <span>${iconFor(att)}</span>
        <strong>${escapeHtml(att.name)}</strong>
        <em>${Math.max(1, Math.round(att.size / 1024))}KB</em>
        <button type="button" data-remove-attachment="${escapeHtml(att.id)}">×</button>
      </div>
    `).join("");
  }

  async function handleFiles(files) {
    const room = MAX_FILES - pendingAttachments.length;
    if (room <= 0) return toast(`最多同时上传 ${MAX_FILES} 个附件`);
    const list = Array.from(files || []).slice(0, room);
    if (!list.length) return;
    try {
      for (const file of list) pendingAttachments.push(await fileToAttachment(file));
      renderTray();
      toast(`已添加 ${list.length} 个附件`);
    } catch (error) {
      toast(error.message || "附件读取失败");
    }
  }

  function installStyle() {
    if (document.querySelector("#chat-attachments-style")) return;
    const style = document.createElement("style");
    style.id = "chat-attachments-style";
    style.textContent = `
      .chat-composer{position:relative;gap:10px;align-items:flex-end;z-index:5}
      .attach-btn{width:48px;height:48px;min-width:48px;border-radius:20px;border:1px solid rgba(255,255,255,.30);background:rgba(255,255,255,.14);color:rgba(255,255,255,.92);font-size:24px;font-weight:900;display:grid;place-items:center;backdrop-filter:blur(16px);-webkit-backdrop-filter:blur(16px);box-shadow:inset 0 1px 0 rgba(255,255,255,.24)}
      .attach-btn:active{transform:scale(.96)}
      .attachment-tray{display:none;position:relative;z-index:6;gap:8px;flex-wrap:wrap;margin:10px 4px 10px;max-width:100%}
      .attachment-tray.show{display:flex}
      .attachment-pill{display:inline-flex;align-items:center;gap:6px;max-width:100%;border-radius:999px;padding:7px 9px;background:rgba(255,255,255,.16);border:1px solid rgba(255,255,255,.26);color:rgba(255,255,255,.88);font-size:12px;backdrop-filter:blur(14px);-webkit-backdrop-filter:blur(14px)}
      .attachment-pill strong{max-width:170px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-weight:850}
      .attachment-pill em{font-style:normal;opacity:.66}
      .attachment-pill button{border:0;background:rgba(255,255,255,.20);color:inherit;border-radius:999px;width:22px;height:22px;font-weight:900;line-height:1}
    `;
    document.head.appendChild(style);
  }

  function setLoading(isLoading) {
    const input = document.querySelector("#aiInput");
    const send = document.querySelector("#aiAddBtn") || document.querySelector("#sendBtn");
    if (input) input.disabled = isLoading;
    if (send) send.disabled = isLoading;
  }

  function addTyping() {
    const box = document.querySelector("#chatMessages");
    if (!box || document.querySelector("#attachmentTypingRow")) return;
    box.insertAdjacentHTML("beforeend", `<div class="chat-row assistant" id="attachmentTypingRow"><div class="chat-bubble"><span class="typing-dot"></span><span class="typing-dot"></span><span class="typing-dot"></span></div></div>`);
    box.scrollTop = box.scrollHeight;
  }

  function removeTyping() {
    document.querySelector("#attachmentTypingRow")?.remove();
  }

  async function sendAttachmentChat(text) {
    const endpoint = readAiEndpoint();
    if (!endpoint) {
      toast("请先在设置里配置 AI 接口");
      return;
    }
    const attachments = pendingAttachments.slice();
    if (!attachments.length) return;
    pendingAttachments = [];
    renderTray();

    const messages = readChatMessages();
    const userText = text.trim() || "请分析这个附件";
    messages.push({ id: createId("user"), role: "user", content: userText, attachments: attachments.map(({ name, mimeType, size }) => ({ name, mimeType, size })) });
    saveChatMessages(messages);
    addTyping();
    setLoading(true);

    try {
      const response = await fetch(endpoint, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          messages: conversationPayload(messages),
          text: userText,
          attachments: attachments.map(({ id, name, mimeType, size, data }) => ({ id, name, mimeType, size, data })),
          pendingDraft: [],
          ledgerContext: ledgerContext(),
          clientTools: window.MobileCommandActions?.tools || [],
          now: todayISO(),
        }),
      });
      const data = await response.json().catch(() => null);
      if (!response.ok) throw new Error(data?.error || data?.reply || `HTTP ${response.status}`);
      messages.push({
        id: createId("assistant"),
        role: "assistant",
        content: String(data?.reply || "我看到了附件，但没有提取到明确内容。"),
        action: data?.action || "chat",
        records: Array.isArray(data?.records) ? data.records : [],
        draftState: "none",
        mobileCommand: data?.mobileCommand || null,
        source: data?.source || "gemini_vision",
        version: data?.version,
      });
      saveChatMessages(messages);
      toast("附件分析完成");
    } catch (error) {
      messages.push({ id: createId("assistant"), role: "assistant", content: `附件分析失败：${String(error?.message || error).slice(0, 160)}`, action: "chat", records: [], draftState: "none", mobileCommand: null, source: "gemini_vision_error" });
      saveChatMessages(messages);
      toast("附件分析失败");
    } finally {
      removeTyping();
      setLoading(false);
      window.ChatSourceBadges?.refresh?.();
    }
  }

  function installUI() {
    const form = document.querySelector("#chatForm");
    const input = document.querySelector("#aiInput");
    if (!form || !input || form.dataset.attachmentsReady === "ready") return;
    form.dataset.attachmentsReady = "ready";
    installStyle();

    const picker = document.createElement("input");
    picker.id = "chatAttachmentInput";
    picker.type = "file";
    picker.accept = ACCEPT;
    picker.multiple = true;
    picker.hidden = true;

    const btn = document.createElement("button");
    btn.id = "chatAttachBtn";
    btn.className = "attach-btn";
    btn.type = "button";
    btn.setAttribute("aria-label", "上传图片或文件");
    btn.textContent = "+";

    const tray = document.createElement("div");
    tray.id = "attachmentTray";
    tray.className = "attachment-tray";

    form.parentNode.insertBefore(tray, form);
    form.insertBefore(btn, input);
    form.appendChild(picker);

    btn.addEventListener("click", () => picker.click());
    picker.addEventListener("change", async () => {
      await handleFiles(picker.files);
      picker.value = "";
    });

    form.addEventListener("submit", (event) => {
      if (!pendingAttachments.length) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      const text = input.value.trim() || "请分析这个附件";
      input.value = "";
      input.style.height = "auto";
      sendAttachmentChat(text);
    }, true);

    tray.addEventListener("click", (event) => {
      const remove = event.target.closest("[data-remove-attachment]");
      if (!remove) return;
      pendingAttachments = pendingAttachments.filter((att) => att.id !== remove.dataset.removeAttachment);
      renderTray();
    });
  }

  window.ChatAttachments = {
    take: () => { const current = pendingAttachments; pendingAttachments = []; renderTray(); return current; },
    peek: () => pendingAttachments.slice(),
    has: () => pendingAttachments.length > 0,
    version: "2026-05-16-attachments-direct-1",
  };

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", installUI);
  else installUI();
  setTimeout(installUI, 300);
})();

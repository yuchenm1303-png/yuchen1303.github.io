(() => {
  const MAX_FILES = 3;
  const MAX_FILE_BYTES = 4 * 1024 * 1024;
  const ACCEPT = "image/*,.pdf,.txt,.md,.csv,.json,.html,.htm,.js,.css,.py,.java,.c,.cpp,.h,.doc,.docx";
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

  function fileToAttachment(file) {
    return new Promise((resolve, reject) => {
      if (!file) return reject(new Error("文件无效"));
      if (file.size > MAX_FILE_BYTES) return reject(new Error(`${file.name} 超过 4MB，先压缩或换小一点的文件`));
      const reader = new FileReader();
      reader.onload = () => {
        const dataUrl = String(reader.result || "");
        const comma = dataUrl.indexOf(",");
        const base64 = comma >= 0 ? dataUrl.slice(comma + 1) : dataUrl;
        resolve({
          id: crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`,
          name: file.name || "未命名文件",
          mimeType: file.type || guessMime(file.name),
          size: file.size,
          data: base64,
        });
      };
      reader.onerror = () => reject(new Error(`${file.name} 读取失败`));
      reader.readAsDataURL(file);
    });
  }

  function guessMime(name) {
    const lower = String(name || "").toLowerCase();
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
      return;
    }
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
    const list = Array.from(files || []).slice(0, MAX_FILES - pendingAttachments.length);
    if (!list.length) return;
    try {
      for (const file of list) {
        const att = await fileToAttachment(file);
        pendingAttachments.push(att);
      }
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
      .chat-composer{position:relative;gap:10px;align-items:flex-end}
      .attach-btn{width:48px;height:48px;min-width:48px;border-radius:20px;border:1px solid rgba(255,255,255,.28);background:rgba(255,255,255,.12);color:rgba(255,255,255,.9);font-size:24px;font-weight:800;display:grid;place-items:center;backdrop-filter:blur(16px);box-shadow:inset 0 1px 0 rgba(255,255,255,.22)}
      .attach-btn:active{transform:scale(.96)}
      .attachment-tray{display:none;gap:8px;flex-wrap:wrap;margin:10px 4px 8px}
      .attachment-tray.show{display:flex}
      .attachment-pill{display:inline-flex;align-items:center;gap:6px;max-width:100%;border-radius:999px;padding:7px 9px;background:rgba(255,255,255,.14);border:1px solid rgba(255,255,255,.22);color:rgba(255,255,255,.86);font-size:12px;backdrop-filter:blur(14px)}
      .attachment-pill strong{max-width:150px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-weight:800}
      .attachment-pill em{font-style:normal;opacity:.62}
      .attachment-pill button{border:0;background:rgba(255,255,255,.18);color:inherit;border-radius:999px;width:20px;height:20px;font-weight:900}
      .attachment-preview{margin-top:8px;border-radius:18px;border:1px solid rgba(255,255,255,.18);background:rgba(255,255,255,.08);padding:8px;display:flex;gap:8px;flex-wrap:wrap}
      .attachment-preview span{font-size:12px;opacity:.74}
    `;
    document.head.appendChild(style);
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

    tray.addEventListener("click", (event) => {
      const remove = event.target.closest("[data-remove-attachment]");
      if (!remove) return;
      pendingAttachments = pendingAttachments.filter((att) => att.id !== remove.dataset.removeAttachment);
      renderTray();
    });
  }

  function takeAttachments() {
    const current = pendingAttachments;
    pendingAttachments = [];
    renderTray();
    return current;
  }

  function peekAttachments() {
    return pendingAttachments.slice();
  }

  window.ChatAttachments = {
    take: takeAttachments,
    peek: peekAttachments,
    has: () => pendingAttachments.length > 0,
    version: "2026-05-15-attachments-1",
  };

  window.addEventListener("DOMContentLoaded", () => {
    installUI();
    setTimeout(installUI, 300);
  });
})();

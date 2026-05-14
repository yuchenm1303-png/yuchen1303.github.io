(() => {
  const STYLE_ID = "tools-center-style";

  function $(selector) {
    return document.querySelector(selector);
  }

  function openView(name) {
    if (window.AiAssistantViews?.open) {
      window.AiAssistantViews.open(name);
      return;
    }
    $(`.nav-btn[data-view="${name}"]`)?.click();
  }

  function installStyles() {
    if ($(`#${STYLE_ID}`)) return;
    const style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = `
      .tools-home{display:grid;gap:16px;padding-bottom:110px}
      .tools-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}
      .tool-card{min-height:132px;padding:16px;text-align:left;color:#f2ffff;cursor:pointer}
      .tool-card:active{transform:scale(.985)}
      .tool-icon{width:40px;height:40px;border-radius:16px;display:grid;place-items:center;margin-bottom:12px;background:rgba(255,255,255,.16);font-size:21px}
      .tool-card h3{margin:0 0 7px;font-size:18px;color:#f5ffff}
      .tool-card p{margin:0;color:rgba(235,252,255,.68);font-size:13px;line-height:1.45}
      .tools-panel{display:none;gap:14px;padding-bottom:110px}
      .tools-panel.open{display:grid}
      .tools-back{width:max-content;margin:0 0 14px;border:1px solid rgba(255,255,255,.34);border-radius:999px;padding:9px 14px;background:rgba(255,255,255,.14);color:#efffff;font-weight:800}
      .tools-panel-card{padding:18px;border-radius:28px;border:1px solid rgba(255,255,255,.32);background:rgba(255,255,255,.14);box-shadow:0 20px 46px rgba(0,40,50,.15);backdrop-filter:blur(18px);color:#f4ffff}
      .tools-panel-card h2{margin:0 0 8px;font-size:24px}
      .tools-panel-card p{margin:0;color:rgba(235,252,255,.70);line-height:1.6}
      .tools-chip-row{display:flex;gap:10px;flex-wrap:wrap;margin-top:14px}
      .tools-chip-row button{border:1px solid rgba(255,255,255,.36);border-radius:999px;padding:10px 14px;background:rgba(255,255,255,.14);color:#efffff;font-weight:800}
      @media(max-width:420px){.tools-grid{gap:10px}.tool-card{min-height:120px;padding:14px}}
    `;
    document.head.appendChild(style);
  }

  function showToolsHome() {
    const home = $("#toolsHome");
    const panel = $("#toolsPanel");
    if (home) home.style.removeProperty("display");
    if (panel) {
      panel.classList.remove("open");
      panel.innerHTML = "";
    }
  }

  function showPanel(kind) {
    const home = $("#toolsHome");
    const panel = $("#toolsPanel");
    if (!panel) return;
    if (home) home.style.display = "none";

    const panels = {
      alarm: {
        title: "提醒闹钟",
        text: "通过 AI 对话生成提醒或系统闹钟动作卡片，Android 版确认后会调用原生能力。",
        actions: [
          ["明早 8 点叫我起床", "明天早上8点叫我起床"],
          ["今晚 9 点提醒复习", "今晚9点提醒我复习"],
        ],
      },
      apps: {
        title: "应用控制",
        text: "通过 AI 对话生成打开应用动作卡片，Android 版确认后会调用原生插件打开对应 App。",
        actions: [
          ["打开微信", "打开微信"],
          ["打开支付宝", "打开支付宝"],
        ],
      },
      shortcuts: {
        title: "快捷指令",
        text: "这里会沉淀高频任务。当前可以先用下方指令快速进入 AI 对话。",
        actions: [
          ["记一笔午饭", "今天午饭28"],
          ["查餐饮开支", "我这个月餐饮花了多少"],
        ],
      },
      tasks: {
        title: "任务记录",
        text: "手机动作卡片会保存在对话中。后续这里会集中展示提醒、打开应用等执行记录。",
        actions: [
          ["查看对话", ""],
          ["打开支付宝", "打开支付宝"],
        ],
      },
    };

    const config = panels[kind] || panels.shortcuts;
    const buttons = config.actions
      .map(([label, prompt]) => prompt
        ? `<button type="button" data-send-ai="${prompt}">${label}</button>`
        : `<button type="button" data-open-view="ai">${label}</button>`)
      .join("");

    panel.innerHTML = `
      <button class="tools-back" type="button" data-tools-home>← 功能中心</button>
      <article class="tools-panel-card">
        <h2>${config.title}</h2>
        <p>${config.text}</p>
        <div class="tools-chip-row">${buttons}</div>
      </article>
    `;
    panel.classList.add("open");
  }

  function sendToAi(text) {
    openView("ai");
    window.setTimeout(() => {
      const input = $("#aiInput");
      const form = $("#chatForm");
      if (!input || !form) return;
      input.value = text;
      form.requestSubmit ? form.requestSubmit() : form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    }, 120);
  }

  function installHandlers() {
    document.addEventListener("click", (event) => {
      const tool = event.target.closest("[data-tool]")?.dataset.tool;
      if (tool === "ledger") return openView("list");
      if (tool === "stats") return openView("stats");
      if (tool === "alarm" || tool === "apps" || tool === "shortcuts" || tool === "tasks") return showPanel(tool);
      if (event.target.closest("[data-tools-home]")) return showToolsHome();

      const send = event.target.closest("[data-send-ai]")?.dataset.sendAi;
      if (send) return sendToAi(send);
    });

    window.addEventListener("ai-tools-home", showToolsHome);
  }

  window.addEventListener("DOMContentLoaded", () => {
    installStyles();
    installHandlers();
  });
})();

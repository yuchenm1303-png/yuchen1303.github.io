(() => {
  const CHAT_KEY = "ai-ledger-chat-v2";

  function escapeHtml(value) {
    return String(value)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function createId() {
    return crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`;
  }

  function readMessages() {
    try {
      const parsed = JSON.parse(localStorage.getItem(CHAT_KEY) || "[]");
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  function saveAssistantExchange(userText, reply) {
    const messages = readMessages();
    messages.push({ id: createId(), role: "user", content: userText });
    messages.push({ id: createId(), role: "assistant", content: reply, action: "chat", records: [], draftState: "none" });
    localStorage.setItem(CHAT_KEY, JSON.stringify(messages.slice(-80)));
  }

  function appendBubble(role, content) {
    const chat = document.querySelector("#chatMessages");
    if (!chat) return;
    const cls = role === "user" ? "user" : "assistant";
    chat.insertAdjacentHTML("beforeend", `<div class="chat-row ${cls}"><div class="chat-bubble">${escapeHtml(content)}</div></div>`);
    chat.scrollTop = chat.scrollHeight;
  }

  function getBuiltInReply(text) {
    const value = text.trim();
    if (/^(你好|您好|嗨|哈喽|hello|hi|在吗)$/iu.test(value)) {
      return "我在。你可以直接说任务，比如“明天早上8点叫我起床”“打开微信”“今天午饭28”，也可以随便和我聊。";
    }
    if (/(你是谁|你能做什么|有什么功能|会干什么|怎么用)/u.test(value)) {
      return "我是你的多功能 AI 助手。现在可以聊天、记账、查账单、生成手机任务卡片；打包到 Android 后，还能通过原生插件执行闹钟和打开应用。";
    }
    if (/(天气|下雨|气温)/u.test(value)) {
      return "天气能力还没有正式接入。我已经把它放进后续能力清单，接入天气接口后就能直接回答今天会不会下雨。";
    }
    if (/(谢谢|谢了|thank)/iu.test(value)) {
      return "不客气。你继续说任务就行，我会尽量把它整理成可以执行的动作。";
    }
    return null;
  }

  function updateWelcomeMessage() {
    const messages = readMessages();
    if (!messages.length) return;
    const welcome = messages.find((item) => item.id === "welcome");
    if (!welcome || !/记账助手/.test(welcome.content)) return;
    welcome.content = "你好，我是你的 AI 助手。你可以让我记账、查账单、设置提醒、打开应用，也可以直接和我聊天。";
    localStorage.setItem(CHAT_KEY, JSON.stringify(messages));
  }

  function installBuiltInReplies() {
    const form = document.querySelector("#chatForm");
    const input = document.querySelector("#aiInput");
    if (!form || !input) return;

    form.addEventListener("submit", (event) => {
      const text = input.value.trim();
      const reply = getBuiltInReply(text);
      if (!reply) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      input.value = "";
      appendBubble("user", text);
      appendBubble("assistant", reply);
      saveAssistantExchange(text, reply);
    }, true);
  }

  window.addEventListener("DOMContentLoaded", () => {
    updateWelcomeMessage();
    installBuiltInReplies();
  });
})();

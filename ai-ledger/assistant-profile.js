(() => {
  const CHAT_KEY = "ai-ledger-chat-v2";

  function readMessages() {
    try {
      const parsed = JSON.parse(localStorage.getItem(CHAT_KEY) || "[]");
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  function getBuiltInReply(text) {
    const value = String(text || "").trim();
    if (!value) return null;

    if (/^(你好|您好|嗨|哈喽|hello|hi|在吗)$/iu.test(value)) {
      return "我在。你可以直接说任务，比如“明天早上8点叫我起床”“导航回家”“打开微信”“今天午饭28”，也可以随便和我聊。";
    }

    if (/(你是谁|你能做什么|有什么功能|会干什么|怎么用)/u.test(value)) {
      return "我是你的多功能 AI 助手。现在可以聊天、记账、查账单、生成手机任务卡片；打包到 Android 后，还能通过原生插件执行闹钟、打开应用和百度地图导航。";
    }

    if (/(天气|下雨|气温|温度)/u.test(value)) {
      return "天气能力还没有正式接入。现在我可以先帮你记账、设提醒、打开应用和导航；后面接入天气接口后，就能直接回答今天会不会下雨。";
    }

    if (/(谢谢|谢了|thank)/iu.test(value)) {
      return "不客气。你继续说任务就行，我会尽量把它整理成可以确认执行的动作。";
    }

    if (/(你会不会|可以不可以|能不能).*(打开|导航|闹钟|提醒)/u.test(value)) {
      return "可以。你直接说“打开微信”“明早8点叫我起床”或“导航回家”，我会先生成动作卡片，等你确认后再执行。";
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

  function createChatId() {
    return typeof createId === "function"
      ? createId()
      : (crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`);
  }

  function hasPendingDraft() {
    return typeof getPendingMessage === "function" && Boolean(getPendingMessage());
  }

  function hasMobileCommand(text) {
    return typeof localMobileCommandResult === "function" && Boolean(localMobileCommandResult(text));
  }

  function addBuiltInExchange(userText, reply) {
    if (!Array.isArray(chatMessages)) return false;
    chatMessages.push({ id: createChatId(), role: "user", content: userText });
    chatMessages.push({
      id: createChatId(),
      role: "assistant",
      content: reply,
      action: "chat",
      records: [],
      draftState: "none",
      mobileCommand: null,
      source: "builtin_profile",
    });
    if (typeof saveChatMessages === "function") saveChatMessages();
    if (typeof renderAll === "function") renderAll();
    return true;
  }

  function installMainFlowHook() {
    const form = document.querySelector("#chatForm");
    const input = document.querySelector("#aiInput");
    if (!form || !input || form.dataset.builtinReplyHook === "ready") return;
    form.dataset.builtinReplyHook = "ready";

    form.addEventListener("submit", (event) => {
      const text = input.value.trim();
      const reply = getBuiltInReply(text);
      if (!reply || hasPendingDraft() || hasMobileCommand(text)) return;

      event.preventDefault();
      event.stopImmediatePropagation();
      input.value = "";
      input.style.height = "auto";
      addBuiltInExchange(text, reply);
    }, true);
  }

  window.BuiltInAssistantProfile = {
    getReply: getBuiltInReply,
    updateWelcomeMessage,
    installMainFlowHook,
    version: "2026-05-15-shared-state-2",
  };

  window.addEventListener("DOMContentLoaded", () => {
    updateWelcomeMessage();
    window.setTimeout(installMainFlowHook, 0);
    window.setTimeout(installMainFlowHook, 300);
  });
})();

(() => {
  const CHAT_KEY = 'ai-ledger-chat-v2';

  function setText(selector, value) {
    const el = document.querySelector(selector);
    if (el) el.textContent = value;
  }

  function escapeHtml(value) {
    return String(value)
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function createId() {
    return crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`;
  }

  function readMessages() {
    try {
      const parsed = JSON.parse(localStorage.getItem(CHAT_KEY) || '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  function saveAssistantExchange(userText, reply) {
    const messages = readMessages();
    messages.push({ id: createId(), role: 'user', content: userText });
    messages.push({ id: createId(), role: 'assistant', content: reply, action: 'chat', records: [], draftState: 'none' });
    localStorage.setItem(CHAT_KEY, JSON.stringify(messages.slice(-80)));
  }

  function appendBubble(role, content) {
    const chat = document.querySelector('#chatMessages');
    if (!chat) return;
    const cls = role === 'user' ? 'user' : 'assistant';
    chat.insertAdjacentHTML('beforeend', `<div class="chat-row ${cls}"><div class="chat-bubble">${escapeHtml(content)}</div></div>`);
    chat.scrollTop = chat.scrollHeight;
  }

  function getBuiltInReply(text) {
    const value = text.trim();
    if (/^(你好|您好|嗨|哈喽|hello|hi|在吗)$/iu.test(value)) {
      return '我在。你可以直接说任务，比如“明天早上8点叫我起床”“打开微信”“今天午饭28”，也可以随便和我聊。';
    }
    if (/(你是谁|你能做什么|有什么功能|会干什么|怎么用)/u.test(value)) {
      return '我是你的多功能 AI 助手。现在可以聊天、记账、查账单、生成手机任务卡片；后面接入 Android 原生插件后，就能真正执行闹钟、打开应用、提醒等手机操作。';
    }
    if (/(天气|下雨|气温)/u.test(value)) {
      return '天气能力还没有正式接入。我已经把它放进后续能力清单，接入天气接口后就能直接回答今天会不会下雨。';
    }
    if (/(谢谢|谢了|thank)/iu.test(value)) {
      return '不客气。你继续说任务就行，我会尽量把它整理成可以执行的动作。';
    }
    return null;
  }

  function updateSampleButtons() {
    const samples = [...document.querySelectorAll('.sample-btn')];
    const configs = [
      ['设提醒', '明天早上8点叫我起床'],
      ['打开微信', '打开微信'],
      ['记一笔', '今天午饭28'],
    ];
    samples.forEach((btn, index) => {
      const config = configs[index];
      if (!config) return;
      btn.textContent = config[0];
      btn.dataset.sample = config[1];
    });
  }

  function updateWelcomeMessage() {
    const messages = readMessages();
    if (!messages.length) return;
    const welcome = messages.find((item) => item.id === 'welcome');
    if (!welcome || !/记账助手/.test(welcome.content)) return;
    welcome.content = '你好，我是你的 AI 助手。你可以让我记账、查消费、设置提醒、打开应用，也可以直接和我聊天。';
    localStorage.setItem(CHAT_KEY, JSON.stringify(messages));
  }

  function applyAssistantProfile() {
    document.title = 'AI助手';
    setText('#view-ai .eyebrow', 'AI 多功能助手');
    setText('#view-ai h1', '对话中枢');
    setText('#view-ai .subtext', '聊天、记账、提醒与手机任务');
    setText('#aiModeBadge', '✨ 本地助手模式');
    setText('#aiModeHint', '可以像聊天一样交流，也可以让我记账、设置提醒、打开应用。');
    setText(".bottom-nav .nav-btn[data-view='ai'] em", 'AI助手');

    const input = document.querySelector('#aiInput');
    if (input) input.placeholder = '和我说点什么，例如：明早8点叫我起床';
    updateSampleButtons();
    updateWelcomeMessage();
  }

  function installBuiltInReplies() {
    const form = document.querySelector('#chatForm');
    const input = document.querySelector('#aiInput');
    if (!form || !input) return;

    form.addEventListener('submit', (event) => {
      const text = input.value.trim();
      const reply = getBuiltInReply(text);
      if (!reply) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      input.value = '';
      appendBubble('user', text);
      appendBubble('assistant', reply);
      saveAssistantExchange(text, reply);
    }, true);
  }

  window.addEventListener('DOMContentLoaded', () => {
    applyAssistantProfile();
    installBuiltInReplies();
    window.setTimeout(applyAssistantProfile, 120);
    window.setTimeout(applyAssistantProfile, 500);
  });
})();

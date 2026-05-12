(() => {
  function setText(selector, value) {
    const el = document.querySelector(selector);
    if (el) el.textContent = value;
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
    const raw = localStorage.getItem('ai-ledger-chat-v2');
    if (!raw) return;
    try {
      const messages = JSON.parse(raw);
      if (!Array.isArray(messages)) return;
      const welcome = messages.find((item) => item.id === 'welcome');
      if (!welcome) return;
      if (!/记账助手/.test(welcome.content)) return;
      welcome.content = '你好，我是你的 AI 助手。你可以让我记账、查消费、设置提醒、打开应用，也可以直接和我聊天。';
      localStorage.setItem('ai-ledger-chat-v2', JSON.stringify(messages));
    } catch {}
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

  window.addEventListener('DOMContentLoaded', () => {
    applyAssistantProfile();
    window.setTimeout(applyAssistantProfile, 120);
    window.setTimeout(applyAssistantProfile, 500);
  });
})();

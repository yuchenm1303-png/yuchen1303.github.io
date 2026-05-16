(() => {
  'use strict';

  const CHAT_KEY = 'ai-ledger-chat-v2';
  const PREF_KEY = 'ai-ledger-model-preference-v1';
  const BASE = (() => {
    try {
      const script = document.currentScript;
      if (!script?.src) return './';
      return script.src.slice(0, script.src.lastIndexOf('/') + 1);
    } catch {
      return './';
    }
  })();

  const MODULE_VERSION = '20260516-11';

  const MODELS = [
    { id: 'auto', label: '自动', short: '自动', hint: '按额度和可用性自动切换' },
    { id: 'kimi', label: 'Kimi K2.6', short: 'Kimi', hint: '只使用 Kimi，不自动切到其他模型' },
    { id: 'mistral', label: 'Mistral Medium 3.5', short: 'Mistral', hint: '只使用 Mistral，不自动切到 Kimi/Gemini' },
    { id: 'gemini', label: 'Gemini 2.5 Flash', short: 'Gemini', hint: '只使用 Gemini，不自动切到其他模型' },
    { id: 'workers', label: 'Workers AI', short: 'Workers', hint: '只使用 Workers AI 兜底模型' },
  ];

  const SOURCE_LABELS = {
    cloud_ai: { label: '云端 AI', tone: 'cloud' },
    nvidia_chat: { label: 'NVIDIA NIM', tone: 'cloud' },
    tavily_ai_summary: { label: '联网总结', tone: 'online' },
    workers_ai: { label: 'Workers AI', tone: 'cloud' },
    workers_ai_text_fallback: { label: 'Workers AI', tone: 'cloud-fallback' },
    workers_ai_vision: { label: 'Workers AI 识图', tone: 'vision' },
    workers_ai_vision_fallback: { label: 'Workers AI 识图兜底', tone: 'vision' },
    nvidia_vision: { label: 'NVIDIA 识图', tone: 'vision' },
    nvidia_vision_fallback: { label: 'NVIDIA 识图兜底', tone: 'vision' },
    selected_model_failed: { label: '所选模型失败', tone: 'error' },
    nvidia_chat_fallback: { label: 'NVIDIA 兜底', tone: 'cloud-fallback' },
    gemini_ai: { label: 'Gemini AI', tone: 'gemini' },
    gemini_chat: { label: 'Gemini 对话', tone: 'gemini' },
    gemini_chat_error: { label: 'Gemini 错误', tone: 'error' },
    gemini_missing_key: { label: 'Gemini 未配置', tone: 'error' },
    gemini_vision: { label: 'Gemini 识图', tone: 'vision' },
    gemini_vision_fallback: { label: 'Gemini 识图兜底', tone: 'vision' },
    gemini_vision_error: { label: '识图错误', tone: 'error' },
    vision_all_failed: { label: '识图失败', tone: 'error' },
    vision_quota_exceeded: { label: '识图配额不足', tone: 'error' },
    attachment_ai_missing_key: { label: '识图未配置', tone: 'error' },
    gemini_text_fallback: { label: 'Gemini 兜底', tone: 'cloud-fallback' },
    gemini_error: { label: 'Gemini 错误', tone: 'error' },
    hybrid_rules: { label: '云端规则', tone: 'cloud-rule' },
    cloud_command_bridge: { label: '云端指令', tone: 'mobile' },
    command_protocol: { label: '云端指令', tone: 'mobile' },
    gemini_structured: { label: 'Gemini 指令', tone: 'mobile' },
    weather_tool: { label: '实时天气', tone: 'online' },
    weather_tool_memory: { label: '实时天气 · 记忆城市', tone: 'online' },
    weather_need_location: { label: '天气待补充', tone: 'cloud-fallback' },
    weather_error: { label: '天气错误', tone: 'error' },
    wiki_tool: { label: '百科摘要', tone: 'online' },
    webpage_tool: { label: '网页读取', tone: 'online' },
    web_search_tool: { label: '联网搜索', tone: 'online' },
    tavily_web_search: { label: 'Tavily 搜索', tone: 'online' },
    web_search_error: { label: '搜索错误', tone: 'error' },
    search_not_configured: { label: '搜索未配置', tone: 'cloud-fallback' },
    calculator_tool: { label: '计算器', tone: 'utility' },
    datetime_tool: { label: '日期时间', tone: 'utility' },
    builtin_profile: { label: '内置回复', tone: 'builtin' },
    local: { label: '本地规则', tone: 'local' },
    local_ledger: { label: '本地记账', tone: 'local' },
    local_mobile: { label: '手机动作', tone: 'mobile' },
    navigation_preferences: { label: '导航偏好', tone: 'mobile' },
    ai_command_router_v3: { label: '本地指令路由', tone: 'mobile' },
    cloud_error_normalized: { label: '云端错误', tone: 'error' },
    cloud_fetch_failed: { label: '云端连接失败', tone: 'error' },
    provider_pool_failed: { label: '模型池失败', tone: 'error' },
  };

  function escapeHtml(value) {
    return String(value || '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function readMessages() {
    try {
      const parsed = JSON.parse(localStorage.getItem(CHAT_KEY) || '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  function readModelPreference() {
    try {
      const parsed = JSON.parse(localStorage.getItem(PREF_KEY) || '{}');
      const value = String(parsed.model || 'auto');
      return MODELS.some((item) => item.id === value) ? value : 'auto';
    } catch {
      return 'auto';
    }
  }

  function writeModelPreference(model) {
    const value = MODELS.some((item) => item.id === model) ? model : 'auto';
    localStorage.setItem(PREF_KEY, JSON.stringify({ model: value, updatedAt: Date.now() }));
  }

  function modelShort() {
    return (MODELS.find((item) => item.id === readModelPreference()) || MODELS[0]).short;
  }

  function pinChatBottom(reason = '') {
    if (window.ChatScrollStability?.pinBottom) {
      window.ChatScrollStability.pinBottom(reason);
      return;
    }
    const host = document.querySelector('#chatMessages');
    if (!host) return;
    const run = () => { try { host.scrollTop = host.scrollHeight + 80; } catch {} };
    requestAnimationFrame(run);
    [80, 220, 520].forEach((delay) => setTimeout(run, delay));
  }

  function loadScript(file) {
    return new Promise((resolve, reject) => {
      if (document.querySelector(`script[data-chat-module="${file}"]`)) {
        resolve();
        return;
      }
      const script = document.createElement('script');
      script.src = `${BASE}${file}?v=${MODULE_VERSION}`;
      script.async = false;
      script.dataset.chatModule = file;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error(`Failed to load ${file}`));
      document.head.appendChild(script);
    });
  }

  async function boot() {
    const modules = [
      'chat-request-patcher.js',
      'chat-scroll-stability.js',
      'chat-typing-indicator.js',
      'chat-model-picker.js',
      'chat-source-badges-core.js',
      'chat-message-actions-polish.js',
    ];
    for (const module of modules) {
      try { await loadScript(module); }
      catch (error) { console.warn('[chat-modules]', error); }
    }
  }

  window.AiLedgerChatShared = {
    CHAT_KEY,
    PREF_KEY,
    MODELS,
    SOURCE_LABELS,
    escapeHtml,
    readMessages,
    readModelPreference,
    writeModelPreference,
    modelShort,
    pinChatBottom,
  };

  boot();
})();

(() => {
  'use strict';

  const shared = window.AiLedgerChatShared;
  if (!shared) return;

  const FETCH_PATCH_FLAG = '__aiLedgerChatRequestPatcherInstalled';
  if (window[FETCH_PATCH_FLAG]) return;
  window[FETCH_PATCH_FLAG] = true;

  const { readModelPreference } = shared;
  const nativeFetch = window.fetch.bind(window);

  function isAiPost(method, body) {
    return method === 'POST' && typeof body === 'string' && (body.includes('messages') || body.includes('attachments'));
  }

  function timeoutForModel(model) {
    const value = String(model || 'auto').toLowerCase();
    if (value === 'kimi') return 36000;
    if (value === 'mistral') return 32000;
    if (value === 'gemini') return 22000;
    if (value === 'workers') return 22000;
    return 26000;
  }

  window.fetch = async (input, init = {}) => {
    let model = '';
    let shouldExtendTimeout = false;
    try {
      const method = String(init?.method || (input instanceof Request ? input.method : 'GET')).toUpperCase();
      const body = init?.body;
      if (isAiPost(method, body)) {
        const data = JSON.parse(body);
        if (data) {
          model = String(data.modelPreference || data.aiModelPreference || readModelPreference() || 'auto').toLowerCase();
          if (!data.modelPreference) data.modelPreference = model;
          if (!data.aiModelPreference) data.aiModelPreference = model;
          init = { ...init, body: JSON.stringify(data) };
          shouldExtendTimeout = ['kimi', 'mistral', 'gemini', 'workers', 'auto'].includes(model);
        }
      }
    } catch {}

    if (!shouldExtendTimeout) return nativeFetch(input, init);

    // app-v3 默认 12 秒超时；Kimi / Mistral 这类 NVIDIA NIM 首次冷启动经常会超过 12 秒。
    // 这里仅对 AI POST 请求替换为更长的局部超时，不影响普通资源加载。
    const controller = new AbortController();
    const timer = window.setTimeout(() => controller.abort(), timeoutForModel(model));
    const nextInit = { ...init, signal: controller.signal };
    try {
      return await nativeFetch(input, nextInit);
    } finally {
      window.clearTimeout(timer);
    }
  };
})();

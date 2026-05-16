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

  function normalizePreference(value) {
    const model = String(value || 'auto').toLowerCase().trim();
    if (['auto', 'gemini', 'kimi', 'mistral', 'workers', 'workers_ai'].includes(model)) {
      return model === 'workers_ai' ? 'workers' : model;
    }
    return 'auto';
  }

  function effectivePreference(selected) {
    // 当前 Gemini 免费层容易 429，NVIDIA NIM 的 Kimi/Mistral 免费端点容易排队超时。
    // 因此“自动”先走稳定的 Workers AI，手动选择仍保持严格模式，不偷偷回退。
    return normalizePreference(selected) === 'auto' ? 'workers' : normalizePreference(selected);
  }

  function timeoutForModel(model) {
    const value = normalizePreference(model);
    if (value === 'kimi') return 36000;
    if (value === 'mistral') return 32000;
    if (value === 'gemini') return 24000;
    if (value === 'workers') return 24000;
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
          const selected = normalizePreference(data.modelPreference || data.aiModelPreference || readModelPreference() || 'auto');
          model = effectivePreference(selected);
          data.requestedModelPreference = selected;
          data.modelPreference = model;
          data.aiModelPreference = model;
          data.modelRoutingReason = selected === 'auto' && model === 'workers' ? 'auto_stability_workers_first' : 'manual_strict';
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

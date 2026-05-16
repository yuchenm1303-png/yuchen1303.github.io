(() => {
  'use strict';

  const shared = window.AiLedgerChatShared;
  if (!shared) return;

  const FETCH_PATCH_FLAG = '__aiLedgerChatRequestPatcherInstalled';
  if (window[FETCH_PATCH_FLAG]) return;
  window[FETCH_PATCH_FLAG] = true;

  const { readModelPreference } = shared;
  const nativeFetch = window.fetch.bind(window);

  window.fetch = async (input, init = {}) => {
    try {
      const method = String(init?.method || (input instanceof Request ? input.method : 'GET')).toUpperCase();
      const body = init?.body;
      if (method === 'POST' && typeof body === 'string' && (body.includes('messages') || body.includes('attachments'))) {
        const data = JSON.parse(body);
        if (data && !data.modelPreference) {
          const model = readModelPreference();
          data.modelPreference = model;
          data.aiModelPreference = model;
          init = { ...init, body: JSON.stringify(data) };
        }
      }
    } catch {}
    return nativeFetch(input, init);
  };
})();

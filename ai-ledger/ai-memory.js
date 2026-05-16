(() => {
  'use strict';

  const MEMORY_KEY = 'ai-ledger-user-memory-v1';
  const MAX_FACTS = 36;
  const PATCH_FLAG = '__aiLedgerMemoryFetchPatched';

  function nowIso() {
    return new Date().toISOString();
  }

  function cleanText(value, max = 180) {
    return String(value || '')
      .replace(/\s+/g, ' ')
      .replace(/^[，。！？、,.!?:：；;\s]+|[，。！？、,.!?:：；;\s]+$/g, '')
      .trim()
      .slice(0, max);
  }

  function cleanCity(value) {
    const text = cleanText(value, 32)
      .replace(/^(在|到|是|就是|位于|定位在|这里是|这边是|城市是|当前城市是)/u, '')
      .replace(/(这边|这里|本地|当地|附近|天气|新闻|搜索|查一下|嘛|呀|啊|呢|吧|了|啦|哈|噻)$/u, '')
      .replace(/[，。！？、,.!?:：；;\s]/g, '')
      .trim();
    if (!text || /^(这里|这边|本地|当地|当前位置|当前城市|所在城市|附近)$/u.test(text)) return '';
    if (/(新闻|天气|搜索|定义|什么|怎么|如何|为什么)/u.test(text)) return '';
    return text.slice(0, 24);
  }

  function normalizeMemory(input = {}) {
    const facts = Array.isArray(input.facts) ? input.facts : [];
    const seen = new Set();
    return {
      version: 1,
      currentCity: cleanCity(input.currentCity),
      currentLocation: cleanText(input.currentLocation, 80),
      name: cleanText(input.name, 40),
      hometown: cleanText(input.hometown, 40),
      identity: cleanText(input.identity, 80),
      preferences: input.preferences && typeof input.preferences === 'object' ? input.preferences : {},
      facts: facts
        .map((item) => ({
          key: cleanText(item?.key || 'fact', 32),
          value: cleanText(item?.value || item?.text || '', 180),
          source: cleanText(item?.source || 'local', 24),
          updatedAt: item?.updatedAt || nowIso(),
        }))
        .filter((item) => item.value)
        .filter((item) => {
          const key = `${item.key}:${item.value}`.toLowerCase();
          if (seen.has(key)) return false;
          seen.add(key);
          return true;
        })
        .slice(-MAX_FACTS),
      updatedAt: input.updatedAt || nowIso(),
    };
  }

  function readMemory() {
    try {
      return normalizeMemory(JSON.parse(localStorage.getItem(MEMORY_KEY) || '{}'));
    } catch {
      return normalizeMemory({});
    }
  }

  function writeMemory(memory) {
    const normalized = normalizeMemory({ ...memory, updatedAt: nowIso() });
    localStorage.setItem(MEMORY_KEY, JSON.stringify(normalized));
    window.dispatchEvent(new CustomEvent('ai-memory-updated', { detail: normalized }));
    return normalized;
  }

  function addFact(memory, key, value, source = 'user_message') {
    const fact = { key: cleanText(key, 32), value: cleanText(value, 180), source, updatedAt: nowIso() };
    if (!fact.key || !fact.value) return memory;
    const rest = (memory.facts || []).filter((item) => `${item.key}:${item.value}`.toLowerCase() !== `${fact.key}:${fact.value}`.toLowerCase());
    return { ...memory, facts: [...rest, fact].slice(-MAX_FACTS) };
  }

  function extractMemoriesFromText(text) {
    const raw = cleanText(text, 260);
    if (!raw) return [];
    const items = [];

    const cityPatterns = [
      /(?:我(?:现在|目前|刚刚)?(?:就)?在|我这边(?:是|在)?|我这里(?:是|在)?|当前位置(?:是|在)|当前城市(?:是|在)|定位(?:是|在))\s*([\u4e00-\u9fa5A-Za-z· .-]{2,24})/u,
      /(?:我(?:住|居住|生活|工作|上学|读书)在)\s*([\u4e00-\u9fa5A-Za-z· .-]{2,24})/u,
      /(?:不是说了|刚刚说了|我说了)\s*我在\s*([\u4e00-\u9fa5A-Za-z· .-]{2,24})/u,
    ];
    for (const pattern of cityPatterns) {
      const match = raw.match(pattern);
      const city = cleanCity(match?.[1]);
      if (city) items.push({ type: 'currentCity', value: city });
    }

    const nameMatch = raw.match(/(?:我叫|我的名字(?:是|叫)|你可以叫我)\s*([\u4e00-\u9fa5A-Za-z0-9_·.-]{2,24})/u);
    if (nameMatch) items.push({ type: 'name', value: cleanText(nameMatch[1], 40) });

    const hometownMatch = raw.match(/(?:我是|我来自)\s*([\u4e00-\u9fa5]{2,16})(?:人)?$/u);
    if (hometownMatch && !/(学生|老师|工程师|程序员|开发者|用户)/u.test(hometownMatch[1])) {
      items.push({ type: 'hometown', value: cleanText(hometownMatch[1], 40) });
    }

    const identityMatch = raw.match(/我是\s*([^，。！？,.!?]{2,40}(?:学生|老师|工程师|程序员|开发者|用户|设计师|运营|产品经理))/u);
    if (identityMatch) items.push({ type: 'identity', value: cleanText(identityMatch[1], 80) });

    const rememberMatch = raw.match(/(?:记住|你要记住|以后记得|帮我记住)[:：]?\s*([^。！？!?]{2,120})/u);
    if (rememberMatch) items.push({ type: 'fact', key: 'explicit_memory', value: cleanText(rememberMatch[1], 120) });

    return items;
  }

  function rememberFromText(text) {
    const extracted = extractMemoriesFromText(text);
    if (!extracted.length) return readMemory();
    let memory = readMemory();
    extracted.forEach((item) => {
      if (item.type === 'currentCity') {
        memory.currentCity = item.value;
        memory.currentLocation = item.value;
        memory = addFact(memory, 'current_city', `用户当前城市：${item.value}`);
      } else if (item.type === 'name') {
        memory.name = item.value;
        memory = addFact(memory, 'name', `用户名字：${item.value}`);
      } else if (item.type === 'hometown') {
        memory.hometown = item.value;
        memory = addFact(memory, 'hometown', `用户来自：${item.value}`);
      } else if (item.type === 'identity') {
        memory.identity = item.value;
        memory = addFact(memory, 'identity', `用户身份：${item.value}`);
      } else if (item.type === 'fact') {
        memory = addFact(memory, item.key || 'fact', item.value, 'explicit_memory');
      }
    });
    return writeMemory(memory);
  }

  function getContext() {
    const memory = readMemory();
    return {
      version: 'ai-memory-v1',
      currentCity: memory.currentCity || '',
      currentLocation: memory.currentLocation || memory.currentCity || '',
      name: memory.name || '',
      hometown: memory.hometown || '',
      identity: memory.identity || '',
      preferences: memory.preferences || {},
      facts: (memory.facts || []).slice(-18),
      updatedAt: memory.updatedAt || '',
    };
  }

  function lastUserText(body) {
    const messages = Array.isArray(body?.messages) ? body.messages : [];
    const msg = [...messages].reverse().find((item) => item?.role === 'user' && cleanText(item?.content));
    return cleanText(msg?.content || body?.text || '', 260);
  }

  function isAiPayload(body) {
    return body && typeof body === 'object' && Array.isArray(body.messages);
  }

  function patchFetch() {
    if (window[PATCH_FLAG]) return;
    window[PATCH_FLAG] = true;
    const originalFetch = window.fetch.bind(window);
    window.fetch = async (input, init = {}) => {
      let patchedInit = init;
      try {
        const method = String(init?.method || 'GET').toUpperCase();
        const bodyText = typeof init?.body === 'string' ? init.body : '';
        if (method === 'POST' && bodyText) {
          const body = JSON.parse(bodyText);
          if (isAiPayload(body)) {
            const text = lastUserText(body);
            if (text) rememberFromText(text);
            patchedInit = {
              ...init,
              body: JSON.stringify({
                ...body,
                memoryContext: getContext(),
              }),
            };
          }
        }
      } catch {}
      return originalFetch(input, patchedInit);
    };
  }

  window.AIMemory = {
    version: '20260516-1',
    read: readMemory,
    clear() {
      localStorage.removeItem(MEMORY_KEY);
      window.dispatchEvent(new CustomEvent('ai-memory-updated', { detail: readMemory() }));
      return readMemory();
    },
    remember(text) { return rememberFromText(text); },
    getContext,
    extract: extractMemoriesFromText,
  };

  patchFetch();
})();
(() => {
  const CHAT_KEY = 'ai-ledger-chat-v2';
  const NAV_PREF_KEY = 'ai-assistant-navigation-preferences-v2';
  const HOME_ADDRESS_KEY = 'ai-assistant-home-address-v1';
  const MAP_PROVIDER_KEY = 'ai-assistant-map-provider-v1';
  const PATCH_FLAG = '__navigationLocalFirstPatched';

  const MODE_LABELS = {
    driving: '驾车',
    walking: '步行',
    riding: '骑行',
    transit: '公交/地铁',
  };

  const MAP_LABELS = {
    baidu: '百度地图',
    amap: '高德地图',
  };

  const ROUTE_OPTION_LABELS = {
    avoidHighway: '避开高速',
    avoidTolls: '少收费',
    preferSubway: '地铁优先',
    preferLessWalk: '少步行',
    useRealtimeTraffic: '参考实时路况',
  };

  const PLACE_LABELS = {
    home: '家',
    school: '学校',
    work: '公司',
    dorm: '宿舍',
  };

  const DEFAULT_PREFS = {
    places: { home: '', school: '', work: '', dorm: '' },
    customPlaces: [],
    mapProvider: 'baidu',
    defaultMode: 'driving',
    routeOptions: {
      avoidHighway: false,
      avoidTolls: false,
      preferSubway: false,
      preferLessWalk: false,
      useRealtimeTraffic: true,
    },
  };

  function createId(prefix = 'nav') {
    if (crypto.randomUUID) return `${prefix}-${crypto.randomUUID()}`;
    return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function cleanText(value, max = 120) {
    return String(value || '')
      .trim()
      .replace(/^[，。；;：:\s]+|[，。；;：:\s]+$/g, '')
      .replace(/\s+/g, ' ')
      .slice(0, max);
  }

  function clone(value) {
    return JSON.parse(JSON.stringify(value));
  }

  function normalizeProvider(value) {
    return value === 'amap' ? 'amap' : 'baidu';
  }

  function normalizeMode(value) {
    return ['driving', 'walking', 'riding', 'transit'].includes(value) ? value : 'driving';
  }

  function normalizePrefs(input = {}) {
    const prefs = clone(DEFAULT_PREFS);
    const legacyHome = localStorage.getItem(HOME_ADDRESS_KEY) || '';
    const legacyMap = localStorage.getItem(MAP_PROVIDER_KEY) || '';
    prefs.places.home = cleanText(input.places?.home || input.homeAddress || legacyHome, 120);
    prefs.places.school = cleanText(input.places?.school, 120);
    prefs.places.work = cleanText(input.places?.work, 120);
    prefs.places.dorm = cleanText(input.places?.dorm, 120);
    prefs.customPlaces = Array.isArray(input.customPlaces)
      ? input.customPlaces.map((item) => ({ name: cleanText(item?.name, 16), address: cleanText(item?.address, 120) })).filter((item) => item.name && item.address)
      : [];
    prefs.mapProvider = normalizeProvider(input.mapProvider || legacyMap);
    prefs.defaultMode = normalizeMode(input.defaultMode);
    prefs.routeOptions = { ...prefs.routeOptions, ...(input.routeOptions || {}) };
    Object.keys(prefs.routeOptions).forEach((key) => { prefs.routeOptions[key] = Boolean(prefs.routeOptions[key]); });
    return prefs;
  }

  function readPrefs() {
    try {
      return normalizePrefs(JSON.parse(localStorage.getItem(NAV_PREF_KEY) || '{}'));
    } catch {
      return normalizePrefs({});
    }
  }

  function writePrefs(nextPrefs) {
    const prefs = normalizePrefs(nextPrefs);
    const payload = { ...prefs, version: 2, lastUpdated: new Date().toISOString() };
    localStorage.setItem(NAV_PREF_KEY, JSON.stringify(payload));
    if (prefs.places.home) localStorage.setItem(HOME_ADDRESS_KEY, prefs.places.home);
    else localStorage.removeItem(HOME_ADDRESS_KEY);
    localStorage.setItem(MAP_PROVIDER_KEY, prefs.mapProvider);
    window.dispatchEvent(new CustomEvent('assistant-preferences-changed', { detail: payload }));
    return payload;
  }

  function normalizePlaceKey(alias) {
    const text = cleanText(alias, 24).replace(/^(我的|我|去|到|回|导航到|导航去)/u, '');
    if (/^(家|我家|家里|家庭|住处|住的地方|回家|到家)$/u.test(text)) return 'home';
    if (/^(学校|校区|大学|学院|上课地方)$/u.test(text)) return 'school';
    if (/^(公司|单位|办公室|上班地方|实习单位)$/u.test(text)) return 'work';
    if (/^(宿舍|寝室|公寓)$/u.test(text)) return 'dorm';
    return '';
  }

  function inferProvider(text) {
    if (/高德|amap/i.test(text)) return 'amap';
    if (/百度|baidu/i.test(text)) return 'baidu';
    return '';
  }

  function inferMode(text, fallback = '') {
    if (/公交|地铁|轻轨|轨道|公共交通|换乘|坐车/u.test(text)) return 'transit';
    if (/步行|走路/u.test(text)) return 'walking';
    if (/骑行|骑车|自行车|电动车|单车/u.test(text)) return 'riding';
    if (/驾车|开车|自驾|打车|出租车|网约车/u.test(text)) return 'driving';
    return fallback || '';
  }

  function inferRouteOptions(text) {
    const options = {};
    if (/避开高速|不走高速|不要高速|少走高速/u.test(text)) options.avoidHighway = true;
    if (/高速优先|走高速/u.test(text) && !/不走高速|不要高速/u.test(text)) options.avoidHighway = false;
    if (/少收费|少花钱|避免收费|避开收费|不走收费/u.test(text)) options.avoidTolls = true;
    if (/地铁优先|优先地铁|多坐地铁/u.test(text)) options.preferSubway = true;
    if (/少步行|少走路|不要走太多|步行少一点/u.test(text)) options.preferLessWalk = true;
    if (/实时路况|躲拥堵|避开拥堵|避堵/u.test(text)) options.useRealtimeTraffic = true;
    return options;
  }

  function routeOptionText(options = {}) {
    return Object.entries(options)
      .filter(([, value]) => Boolean(value))
      .map(([key]) => ROUTE_OPTION_LABELS[key] || key)
      .join('、') || '按默认路线';
  }

  function normalizeAddress(value) {
    return cleanText(value, 120)
      .replace(/^(就是|是|在|为|到|去|设为|设置为|改成|定为|保存为|记为)/u, '')
      .replace(/(然后|之后|以后|现在|马上|立刻|导航|地图|默认|吧|呀|哦)$/u, '')
      .trim();
  }

  function mergeCustomPlaces(base = [], updates = []) {
    const map = new Map();
    base.forEach((item) => {
      if (item?.name && item?.address) map.set(item.name.toLowerCase(), { name: item.name, address: item.address });
    });
    updates.forEach((item) => {
      const name = cleanText(item?.name, 16);
      const address = cleanText(item?.address, 120);
      if (name && address) map.set(name.toLowerCase(), { name, address });
    });
    return [...map.values()].slice(0, 10);
  }

  function applyUpdates(updates = {}) {
    const prefs = readPrefs();
    let count = 0;
    if (updates.mapProvider) {
      prefs.mapProvider = normalizeProvider(updates.mapProvider);
      count += 1;
    }
    if (updates.defaultMode) {
      prefs.defaultMode = normalizeMode(updates.defaultMode);
      count += 1;
    }
    if (updates.places && typeof updates.places === 'object') {
      Object.entries(updates.places).forEach(([key, value]) => {
        const placeKey = normalizePlaceKey(key) || key;
        const address = cleanText(value, 120);
        if (Object.prototype.hasOwnProperty.call(prefs.places, placeKey) && address) {
          prefs.places[placeKey] = address;
          count += 1;
        }
      });
    }
    if (Array.isArray(updates.customPlaces)) {
      prefs.customPlaces = mergeCustomPlaces(prefs.customPlaces, updates.customPlaces);
      if (updates.customPlaces.length) count += 1;
    }
    if (updates.routeOptions && typeof updates.routeOptions === 'object') {
      Object.entries(updates.routeOptions).forEach(([key, value]) => {
        if (Object.prototype.hasOwnProperty.call(prefs.routeOptions, key)) {
          prefs.routeOptions[key] = Boolean(value);
          count += 1;
        }
      });
    }
    writePrefs(prefs);
    return { ok: true, count, message: count ? `已保存 ${count} 项导航偏好。` : '导航偏好没有变化。' };
  }

  function addPlaceUpdate(updates, rows, alias, address) {
    const cleanAlias = cleanText(alias, 16).replace(/^(我的|我)/u, '');
    const cleanAddress = normalizeAddress(address);
    if (!cleanAlias || !cleanAddress) return;
    const key = normalizePlaceKey(cleanAlias);
    if (key) {
      updates.places ||= {};
      updates.places[key] = cleanAddress;
      rows.push([`常用地址 · ${PLACE_LABELS[key] || cleanAlias}`, cleanAddress]);
      return;
    }
    updates.customPlaces ||= [];
    updates.customPlaces.push({ name: cleanAlias, address: cleanAddress });
    rows.push([`自定义地点 · ${cleanAlias}`, cleanAddress]);
  }

  function parseMemoryCommand(text) {
    const raw = cleanText(text, 180);
    const hasIntent = /(默认|以后|偏好|习惯|地址|位置|设为|设置为|保存为|改成|定为|记住|就是|我家|家里|少步行|避开高速|少收费|地铁优先)/u.test(raw);
    if (!hasIntent) return null;

    const updates = {};
    const rows = [];
    const patterns = [
      /(?:把|将)?(?:我的|我)?(家|我家|家里|学校|校区|公司|单位|办公室|宿舍|寝室|公寓)(?:的)?(?:地址|位置)?\s*(?:设为|设置为|改成|定为|保存为|记为|就是|是|在|=)\s*([^，。；;\n]+)/gu,
      /(?:以后|以后再|之后)(?:回|去|到)(家|学校|公司|单位|宿舍|寝室|公寓)\s*(?:就是|是|去|到)?\s*([^，。；;\n]+)/gu,
      /([^，。；;\n]{1,12})(?:地址|位置)\s*(?:设为|设置为|改成|定为|保存为|记为|就是|是|在|=)\s*([^，。；;\n]+)/gu,
    ];
    patterns.forEach((pattern) => {
      for (const match of raw.matchAll(pattern)) addPlaceUpdate(updates, rows, match[1], match[2]);
    });

    const provider = inferProvider(raw);
    if (provider && /(默认|以后|偏好|习惯|地图|导航)/u.test(raw)) {
      updates.mapProvider = provider;
      rows.push(['默认地图', MAP_LABELS[provider]]);
    }
    const mode = inferMode(raw);
    if (mode && /(默认|以后|偏好|习惯|导航|出行方式|路线|通勤)/u.test(raw)) {
      updates.defaultMode = mode;
      rows.push(['默认方式', MODE_LABELS[mode]]);
    }
    const routeOptions = inferRouteOptions(raw);
    if (Object.keys(routeOptions).length) {
      updates.routeOptions = routeOptions;
      rows.push(['路线习惯', routeOptionText(routeOptions)]);
    }
    if (!Object.keys(updates).length) return null;
    return {
      id: createId('nav-pref'),
      type: 'navigate',
      commandKind: 'navigation_preference',
      title: '保存导航偏好',
      summary: rows.map(([key, value]) => `${key}：${value}`).join('；') || '更新导航偏好',
      params: { intent: 'navigation_preference', updates, rows },
    };
  }

  function findCustomPlace(alias, prefs) {
    const text = cleanText(alias, 24).toLowerCase();
    return prefs.customPlaces.find((item) => item.name.toLowerCase() === text) || null;
  }

  function resolveDestination(alias, prefs) {
    const raw = cleanText(alias, 120);
    const key = normalizePlaceKey(raw);
    if (key) {
      const address = prefs.places[key] || '';
      return { destination: address || raw, alias: raw, matchedPlaceKey: key, matchedPlaceLabel: PLACE_LABELS[key], placeAddressMissing: !address };
    }
    const custom = findCustomPlace(raw, prefs);
    if (custom) return { destination: custom.address, alias: raw, matchedPlaceKey: `custom:${custom.name}`, matchedPlaceLabel: custom.name, placeAddressMissing: false };
    return { destination: raw, alias: raw, matchedPlaceKey: '', matchedPlaceLabel: '', placeAddressMissing: false };
  }

  function cleanDestination(value) {
    return cleanText(value, 120)
      .replace(/^(百度地图|高德地图|地图|帮我|请|给我|用百度|用高德|打开地图|开车|驾车|步行|走路|骑行|公交|地铁|坐公交|坐地铁)/u, '')
      .replace(/(怎么走|怎么去|路线|导航|导航一下|带路)$/u, '')
      .replace(/^(到|去|回)/u, '')
      .trim();
  }

  function parseNavigationCommand(text) {
    const raw = cleanText(text, 180);
    if (!/(导航|路线|带我去|回家|到家|怎么走|怎么去|去学校|去公司|去宿舍|去寝室|去家里)/u.test(raw)) return null;

    const prefs = readPrefs();
    const destinationMatch = raw.match(/(?:导航(?:到|去)?|路线到|带我去|怎么去|怎么到)\s*([^，。；;\n]+)/u)
      || raw.match(/去\s*([^，。；;\n]+?)(?:怎么走|怎么去|路线|导航)?$/u)
      || raw.match(/(?:回|到)(家|学校|公司|宿舍|寝室)$/u);
    let alias = destinationMatch?.[1]?.trim() || '';

    if (/回家|到家|去家|家里|我家/u.test(raw)) alias = '家';
    if (/去学校|到学校|回学校/u.test(raw)) alias = '学校';
    if (/去公司|到公司|回公司|去单位|到单位/u.test(raw)) alias = '公司';
    if (/去宿舍|回宿舍|到宿舍|去寝室|回寝室/u.test(raw)) alias = '宿舍';

    alias = cleanDestination(alias);
    if (!alias || /^(打开|启动)?(百度地图|高德地图|地图)$/u.test(alias)) return null;

    const provider = normalizeProvider(inferProvider(raw) || prefs.mapProvider);
    const mode = normalizeMode(inferMode(raw, prefs.defaultMode));
    const routeOptions = { ...prefs.routeOptions, ...inferRouteOptions(raw) };
    const resolved = resolveDestination(alias, prefs);
    const appName = MAP_LABELS[provider] || '地图';

    return {
      id: createId('nav'),
      type: 'navigate',
      title: `${appName}导航`,
      summary: resolved.placeAddressMissing ? `${resolved.alias}（未填写地址）` : `到 ${resolved.destination}`,
      params: {
        appName,
        mapProvider: provider,
        mode,
        routeOptions,
        destination: resolved.destination,
        destinationAlias: resolved.alias,
        matchedPlaceKey: resolved.matchedPlaceKey,
        matchedPlaceLabel: resolved.matchedPlaceLabel,
        placeAddressMissing: resolved.placeAddressMissing,
        homeAddressMissing: resolved.matchedPlaceKey === 'home' && resolved.placeAddressMissing,
      },
    };
  }

  function parseLocalNavigation(text) {
    return parseMemoryCommand(text) || parseNavigationCommand(text);
  }

  function isMemoryCommand(command) {
    return command?.commandKind === 'navigation_preference'
      || command?.params?.intent === 'navigation_preference'
      || Boolean(command?.params?.updates);
  }

  function getActionRows(command) {
    if (isMemoryCommand(command)) {
      return [['动作', '保存导航偏好'], ...(command.params?.rows?.length ? command.params.rows : [['偏好', command.summary || '更新导航偏好']])];
    }
    const rows = [
      ['动作', `${command.params?.appName || '地图'}导航`],
      ['目的地', command.params?.placeAddressMissing ? `${command.params.destinationAlias || command.params.destination}（未填写地址）` : command.params?.destination],
      ['方式', MODE_LABELS[command.params?.mode] || '驾车'],
    ];
    const options = routeOptionText(command.params?.routeOptions || {});
    if (options !== '按默认路线') rows.push(['路线偏好', options]);
    return rows;
  }

  function renderCard(command, state = 'pending', message = '') {
    const rows = getActionRows(command).map(([key, value]) => `<div class="mobile-command-row"><span>${escapeHtml(key)}</span><strong>${escapeHtml(value)}</strong></div>`).join('');
    const isMemory = isMemoryCommand(command);
    const statusText = state === 'done' ? (isMemory ? '已保存' : '已执行') : state === 'cancelled' ? '已取消' : state === 'failed' ? (isMemory ? '保存失败' : '执行失败') : '待确认';
    const buttons = state === 'pending'
      ? `<div class="mobile-command-actions">
          <button class="mobile-command-confirm" type="button" data-mobile-run="${escapeHtml(command.id)}">${isMemory ? '确认保存' : '确认执行'}</button>
          <button class="mobile-command-cancel" type="button" data-mobile-cancel="${escapeHtml(command.id)}">取消</button>
        </div>`
      : '';
    const note = message ? `<div class="mobile-command-message">${escapeHtml(message)}</div>` : '';
    return `<div class="mobile-command-card" data-navigation-local-first="true" data-mobile-card="${escapeHtml(command.id)}">
      <div class="mobile-command-head">
        <span class="mobile-command-title">${escapeHtml(command.title || (isMemory ? '保存导航偏好' : '地图导航'))}</span>
        <span class="mobile-command-status ${escapeHtml(state)}">${escapeHtml(statusText)}</span>
      </div>
      <div class="mobile-command-detail">${rows}</div>
      ${buttons}
      ${note}
    </div>`;
  }

  function replyFor(command) {
    if (isMemoryCommand(command)) return `我整理好了导航偏好：${command.summary || '更新导航习惯'}。确认后我会保存到手机偏好里。`;
    const map = command.params?.appName || '地图';
    const mode = MODE_LABELS[command.params?.mode] || '驾车';
    if (command.params?.placeAddressMissing) {
      return `我知道你想去“${command.params.destinationAlias || command.params.destination}”，但这个常用地址还没填写。你可以说“家就是重庆大学”这类话先保存地址。`;
    }
    return `我理解为要用${map}${mode}导航到“${command.params.destination}”，确认后我再执行。`;
  }

  function loadChat() {
    if (Array.isArray(window.chatMessages)) return window.chatMessages;
    try {
      const parsed = JSON.parse(localStorage.getItem(CHAT_KEY) || '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  function saveChat(messages) {
    localStorage.setItem(CHAT_KEY, JSON.stringify(messages));
    if (Array.isArray(window.chatMessages) && window.chatMessages !== messages) {
      window.chatMessages.length = 0;
      messages.forEach((item) => window.chatMessages.push(item));
    }
    window.saveChatMessages?.();
    window.renderAll?.();
  }

  function appendLocalCommand(text, command) {
    const messages = loadChat();
    messages.push({ id: createId('msg-user'), role: 'user', content: text });
    messages.push({
      id: createId('msg-assistant'),
      role: 'assistant',
      content: replyFor(command),
      action: 'mobile_command',
      records: [],
      draftState: 'none',
      mobileCommand: command,
      source: 'local_navigation_first',
    });
    saveChat(messages);
  }

  function toast(message) {
    const el = document.querySelector('#toast');
    if (!el) return;
    el.textContent = message;
    el.classList.add('show');
    window.clearTimeout(toast.timer);
    toast.timer = window.setTimeout(() => el.classList.remove('show'), 2200);
  }

  function updateCard(commandId, state, message) {
    const card = document.querySelector(`[data-mobile-card="${CSS.escape(commandId)}"]`);
    if (!card) return;
    const isMemory = Boolean(card.querySelector('[data-mobile-run]')?.textContent?.includes('保存')) || card.dataset.navigationLocalFirst === 'true';
    const status = card.querySelector('.mobile-command-status');
    if (status) {
      status.className = `mobile-command-status ${state}`;
      status.textContent = state === 'done' ? (isMemory ? '已保存' : '已执行') : state === 'cancelled' ? '已取消' : state === 'failed' ? (isMemory ? '保存失败' : '执行失败') : '待确认';
    }
    card.querySelector('.mobile-command-actions')?.remove();
    card.querySelector('.mobile-command-message')?.remove();
    if (message) card.insertAdjacentHTML('beforeend', `<div class="mobile-command-message">${escapeHtml(message)}</div>`);
  }

  function findCommand(commandId) {
    const messages = loadChat();
    return [...messages].reverse().find((item) => item.mobileCommand?.id === commandId)?.mobileCommand || null;
  }

  function installSubmitCapture() {
    const form = document.querySelector('#chatForm');
    const input = document.querySelector('#aiInput');
    if (!form || !input || form.dataset.navigationLocalFirst === 'true') return;
    form.dataset.navigationLocalFirst = 'true';
    form.addEventListener('submit', (event) => {
      const text = input.value.trim();
      const command = parseLocalNavigation(text);
      if (!text || !command) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      input.value = '';
      input.style.height = 'auto';
      appendLocalCommand(text, command);
    }, true);
  }

  function installClickCapture() {
    if (document.body.dataset.navigationLocalFirstClick === 'true') return;
    document.body.dataset.navigationLocalFirstClick = 'true';
    document.addEventListener('click', async (event) => {
      const runBtn = event.target.closest?.('[data-mobile-run]');
      const cancelBtn = event.target.closest?.('[data-mobile-cancel]');
      if (!runBtn && !cancelBtn) return;
      const commandId = runBtn?.dataset.mobileRun || cancelBtn?.dataset.mobileCancel;
      const command = findCommand(commandId);
      if (!isMemoryCommand(command)) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      if (cancelBtn) {
        updateCard(commandId, 'cancelled', '已取消保存导航偏好。');
        return;
      }
      updateCard(commandId, 'pending', '正在保存导航偏好……');
      try {
        const result = applyUpdates(command.params?.updates || {});
        updateCard(commandId, 'done', result.message || '已保存导航偏好。');
        toast('已保存导航偏好');
      } catch (error) {
        updateCard(commandId, 'failed', String(error?.message || error || '保存失败'));
      }
    }, true);
  }

  function patchMobileActions() {
    const actions = window.MobileCommandActions;
    if (!actions || actions[PATCH_FLAG]) return;
    actions[PATCH_FLAG] = true;
    const baseParse = actions.parse;
    const baseRender = actions.renderCard;
    const baseReply = actions.createReply;
    actions.parse = (text) => parseLocalNavigation(text) || baseParse?.(text) || null;
    actions.renderCard = (command, state, message) => {
      if (command?.source === 'local_navigation_first' || isMemoryCommand(command) || command?.type === 'navigate') return renderCard(command, state, message);
      return baseRender?.(command, state, message) || '';
    };
    actions.createReply = (command) => {
      if (isMemoryCommand(command) || command?.type === 'navigate') return replyFor(command);
      return baseReply?.(command) || '我整理好了这个手机动作，确认后我再执行。';
    };
  }

  function patchAssistantPreferences() {
    const api = window.AssistantPreferences;
    if (!api || api.__localFirstPatched) return;
    api.__localFirstPatched = true;
    api.getPreferences = readPrefs;
    api.getHomeAddress = () => readPrefs().places.home || '';
    api.getMapProvider = () => readPrefs().mapProvider;
    api.applyPreferenceUpdate = applyUpdates;
    api.resolveDestination = (destination) => {
      const prefs = readPrefs();
      const resolved = resolveDestination(destination, prefs);
      return { destination: resolved.destination, alias: resolved.alias, matchedPlace: resolved.matchedPlaceKey ? { key: resolved.matchedPlaceKey, label: resolved.matchedPlaceLabel, address: resolved.destination } : null, missingAddress: resolved.placeAddressMissing };
    };
    api.decorateNavigationParams = (params = {}, options = {}) => {
      const prefs = readPrefs();
      const sourceText = String(options.sourceText || '');
      const provider = normalizeProvider(params.mapProvider || inferProvider(sourceText) || prefs.mapProvider);
      const alias = cleanText(params.destinationAlias || params.destination || '', 120);
      const resolved = resolveDestination(alias, prefs);
      const mode = normalizeMode(params.mode || inferMode(sourceText, prefs.defaultMode));
      return {
        ...params,
        appName: MAP_LABELS[provider],
        mapProvider: provider,
        mode,
        routeOptions: { ...prefs.routeOptions, ...(params.routeOptions || {}) },
        destination: resolved.destination,
        destinationAlias: resolved.alias,
        matchedPlaceKey: resolved.matchedPlaceKey,
        matchedPlaceLabel: resolved.matchedPlaceLabel,
        placeAddressMissing: resolved.placeAddressMissing,
        homeAddressMissing: resolved.matchedPlaceKey === 'home' && resolved.placeAddressMissing,
      };
    };
  }

  function boot() {
    patchAssistantPreferences();
    patchMobileActions();
    installSubmitCapture();
    installClickCapture();
    window.setTimeout(() => { patchAssistantPreferences(); patchMobileActions(); installSubmitCapture(); }, 300);
    window.setTimeout(() => { patchAssistantPreferences(); patchMobileActions(); installSubmitCapture(); }, 1200);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();

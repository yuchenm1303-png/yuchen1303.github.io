(() => {
  'use strict';

  const NAV_PREF_KEY = 'ai-assistant-navigation-preferences-v2';
  const HOME_ADDRESS_KEY = 'ai-assistant-home-address-v1';
  const MAP_PROVIDER_KEY = 'ai-assistant-map-provider-v1';
  const ROUTER_FLAG = '__aiCommandRouterV3Installed';

  const MAP_LABELS = { baidu: '百度地图', amap: '高德地图' };
  const MODE_LABELS = { driving: '驾车', walking: '步行', riding: '骑行', transit: '公交/地铁' };
  const PLACE_LABELS = { home: '家', school: '学校', work: '公司', dorm: '宿舍' };
  const ROUTE_OPTION_LABELS = {
    avoidHighway: '避开高速',
    avoidTolls: '少收费',
    preferSubway: '地铁优先',
    preferLessWalk: '少步行',
    useRealtimeTraffic: '参考实时路况',
  };

  const DEFAULT_NAV_PREFS = Object.freeze({
    version: 3,
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
    lastUpdated: '',
  });

  let cachedPrefs = null;
  let patchRetryFrame = 0;
  let settingsSyncFrame = 0;

  function createId(prefix = 'cmd') {
    if (crypto.randomUUID) return `${prefix}-${crypto.randomUUID()}`;
    return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  function cleanText(value, max = 180) {
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

  function normalizeMode(value, fallback = 'driving') {
    return ['driving', 'walking', 'riding', 'transit'].includes(value) ? value : fallback;
  }

  function normalizeCustomPlaces(list) {
    if (!Array.isArray(list)) return [];
    const seen = new Set();
    return list
      .map((item) => ({ name: cleanText(item?.name, 16), address: cleanText(item?.address, 120) }))
      .filter((item) => item.name && item.address)
      .filter((item) => {
        const key = item.name.toLowerCase();
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      })
      .slice(0, 10);
  }

  function normalizeNavPrefs(input = {}) {
    const prefs = clone(DEFAULT_NAV_PREFS);
    const legacyHome = localStorage.getItem(HOME_ADDRESS_KEY) || '';
    const legacyMap = localStorage.getItem(MAP_PROVIDER_KEY) || '';
    prefs.places.home = cleanText(input.places?.home || input.homeAddress || legacyHome, 120);
    prefs.places.school = cleanText(input.places?.school, 120);
    prefs.places.work = cleanText(input.places?.work, 120);
    prefs.places.dorm = cleanText(input.places?.dorm, 120);
    prefs.customPlaces = normalizeCustomPlaces(input.customPlaces);
    prefs.mapProvider = normalizeProvider(input.mapProvider || legacyMap);
    prefs.defaultMode = normalizeMode(input.defaultMode, 'driving');
    prefs.routeOptions = { ...prefs.routeOptions, ...(input.routeOptions || {}) };
    Object.keys(prefs.routeOptions).forEach((key) => { prefs.routeOptions[key] = Boolean(prefs.routeOptions[key]); });
    prefs.lastUpdated = input.lastUpdated || '';
    return prefs;
  }

  function readNavPrefs({ fresh = false } = {}) {
    if (cachedPrefs && !fresh) return cachedPrefs;
    try {
      cachedPrefs = normalizeNavPrefs(JSON.parse(localStorage.getItem(NAV_PREF_KEY) || '{}'));
    } catch {
      cachedPrefs = normalizeNavPrefs({});
    }
    return cachedPrefs;
  }

  function writeNavPrefs(nextPrefs) {
    const prefs = normalizeNavPrefs({ ...nextPrefs, lastUpdated: new Date().toISOString() });
    cachedPrefs = prefs;
    localStorage.setItem(NAV_PREF_KEY, JSON.stringify(prefs));
    if (prefs.places.home) localStorage.setItem(HOME_ADDRESS_KEY, prefs.places.home);
    else localStorage.removeItem(HOME_ADDRESS_KEY);
    localStorage.setItem(MAP_PROVIDER_KEY, prefs.mapProvider);
    window.dispatchEvent(new CustomEvent('assistant-preferences-changed', { detail: prefs }));
    return prefs;
  }

  function normalizePlaceKey(alias) {
    const text = cleanText(alias, 24).replace(/^(我的|我|去|到|回|导航到|导航去)/u, '');
    if (/^(家|我家|家里|家庭|住处|住的地方|回家|到家)$/u.test(text)) return 'home';
    if (/^(学校|校区|大学|学院|上课地方)$/u.test(text)) return 'school';
    if (/^(公司|单位|办公室|上班地方|实习单位)$/u.test(text)) return 'work';
    if (/^(宿舍|寝室|公寓|住处)$/u.test(text)) return 'dorm';
    return '';
  }

  function inferProvider(text) {
    if (/高德|amap/i.test(text)) return 'amap';
    if (/百度|baidu/i.test(text)) return 'baidu';
    return '';
  }

  function inferMode(text, fallback = '') {
    if (/公交|地铁|轻轨|轨道|公共交通|换乘|坐车|乘车/u.test(text)) return 'transit';
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

  function mergeCustomPlaces(base = [], updates = []) {
    return normalizeCustomPlaces([...normalizeCustomPlaces(base), ...normalizeCustomPlaces(updates)]);
  }

  function applyNavigationPreferenceUpdate(updates = {}) {
    const prefs = clone(readNavPrefs());
    let count = 0;
    if (updates.mapProvider) {
      prefs.mapProvider = normalizeProvider(updates.mapProvider);
      count += 1;
    }
    if (updates.defaultMode) {
      prefs.defaultMode = normalizeMode(updates.defaultMode, prefs.defaultMode);
      count += 1;
    }
    if (updates.places && typeof updates.places === 'object') {
      Object.entries(updates.places).forEach(([key, value]) => {
        const placeKey = normalizePlaceKey(key) || key;
        const address = cleanText(value, 120);
        if (Object.prototype.hasOwnProperty.call(prefs.places, placeKey) && address) {
          prefs.places[placeKey] = address;
          count += 1;
        } else if (cleanText(key, 16) && address) {
          prefs.customPlaces = mergeCustomPlaces(prefs.customPlaces, [{ name: key, address }]);
          count += 1;
        }
      });
    }
    if (Array.isArray(updates.customPlaces) && updates.customPlaces.length) {
      prefs.customPlaces = mergeCustomPlaces(prefs.customPlaces, updates.customPlaces);
      count += 1;
    }
    if (updates.routeOptions && typeof updates.routeOptions === 'object') {
      Object.entries(updates.routeOptions).forEach(([key, value]) => {
        if (Object.prototype.hasOwnProperty.call(prefs.routeOptions, key)) {
          prefs.routeOptions[key] = Boolean(value);
          count += 1;
        }
      });
    }
    writeNavPrefs(prefs);
    return { ok: true, count, message: count ? `已保存 ${count} 项导航偏好。` : '导航偏好没有变化。' };
  }

  function findCustomPlace(alias, prefs = readNavPrefs()) {
    const text = cleanText(alias, 24).toLowerCase();
    if (!text) return null;
    return prefs.customPlaces.find((item) => item.name.toLowerCase() === text) || null;
  }

  function resolveDestination(alias, prefs = readNavPrefs()) {
    const raw = cleanText(alias, 120);
    const key = normalizePlaceKey(raw);
    if (key) {
      const address = prefs.places[key] || '';
      return {
        destination: address || raw,
        alias: raw,
        matchedPlaceKey: key,
        matchedPlaceLabel: PLACE_LABELS[key],
        placeAddressMissing: !address,
      };
    }
    const custom = findCustomPlace(raw, prefs);
    if (custom) {
      return {
        destination: custom.address,
        alias: raw,
        matchedPlaceKey: `custom:${custom.name}`,
        matchedPlaceLabel: custom.name,
        placeAddressMissing: false,
      };
    }
    return { destination: raw, alias: raw, matchedPlaceKey: '', matchedPlaceLabel: '', placeAddressMissing: false };
  }

  function cleanDestination(value) {
    return cleanText(value, 120)
      .replace(/^(百度地图|高德地图|地图|帮我|请|给我|用百度|用高德|打开地图|开车|驾车|步行|走路|骑行|公交|地铁|坐公交|坐地铁)/u, '')
      .replace(/(怎么走|怎么去|路线|导航|导航一下|带路)$/u, '')
      .replace(/^(到|去|回)/u, '')
      .trim();
  }

  function parseNavigationPreference(text) {
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
      kind: 'local_command',
      intent: 'navigation.preference.set',
      confidence: 0.96,
      source: 'local_rule',
      command: {
        id: createId('nav-pref'),
        type: 'navigate',
        commandKind: 'navigation_preference',
        title: '保存导航偏好',
        summary: rows.map(([key, value]) => `${key}：${value}`).join('；') || '更新导航偏好',
        params: { intent: 'navigation_preference', updates, rows },
      },
    };
  }

  function buildNavigationCommand(alias, rawText, overrides = {}) {
    const prefs = readNavPrefs();
    const cleanAlias = cleanDestination(alias);
    if (!cleanAlias || /^(打开|启动)?(百度地图|高德地图|地图)$/u.test(cleanAlias)) return null;

    const provider = normalizeProvider(overrides.mapProvider || inferProvider(rawText) || prefs.mapProvider);
    const mode = normalizeMode(overrides.mode || inferMode(rawText, prefs.defaultMode), prefs.defaultMode);
    const routeOptions = { ...prefs.routeOptions, ...inferRouteOptions(rawText), ...(overrides.routeOptions || {}) };
    const resolved = resolveDestination(cleanAlias, prefs);
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
        travelMode: mode,
        navigationMode: mode,
        transportMode: mode,
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

  function parseNavigationStart(text) {
    const raw = cleanText(text, 180);
    const directNavIntent = /(导航|带我去|打开地图去|路线到|回家|到家|去学校|去公司|去宿舍|去寝室|去家里)/u.test(raw);
    const routeQueryIntent = /(怎么去|怎么走|附近|最近|查|搜索|搜一下|路况|堵不堵|多久|多远)/u.test(raw);
    if (!directNavIntent || (routeQueryIntent && !/导航|带我去|打开地图去|回家|到家/u.test(raw))) return null;

    const destinationMatch = raw.match(/(?:导航(?:到|去)?|路线到|带我去|打开地图去)\s*([^，。；;\n]+)/u)
      || raw.match(/去\s*([^，。；;\n]+?)(?:导航|路线)?$/u)
      || raw.match(/(?:回|到)(家|学校|公司|宿舍|寝室)$/u);
    let alias = destinationMatch?.[1]?.trim() || '';

    if (/回家|到家|去家|家里|我家/u.test(raw)) alias = '家';
    if (/去学校|到学校|回学校/u.test(raw)) alias = '学校';
    if (/去公司|到公司|回公司|去单位|到单位/u.test(raw)) alias = '公司';
    if (/去宿舍|回宿舍|到宿舍|去寝室|回寝室/u.test(raw)) alias = '宿舍';

    const command = buildNavigationCommand(alias, raw);
    if (!command) return null;
    return {
      kind: command.params.placeAddressMissing ? 'need_user_info' : 'local_command',
      intent: 'navigation.start',
      confidence: 0.95,
      source: 'local_rule',
      missing: command.params.placeAddressMissing ? `${command.params.matchedPlaceLabel || command.params.destinationAlias}地址` : '',
      command,
    };
  }

  function loadChatMessages() {
    if (Array.isArray(window.chatMessages)) return window.chatMessages;
    try {
      const parsed = JSON.parse(localStorage.getItem('ai-ledger-chat-v2') || '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  function isPreferenceCommand(command) {
    return command?.commandKind === 'navigation_preference'
      || command?.params?.intent === 'navigation_preference'
      || Boolean(command?.params?.updates);
  }

  function getLastNavigationCommand() {
    const messages = loadChatMessages();
    for (let i = messages.length - 1; i >= 0; i -= 1) {
      const command = messages[i]?.mobileCommand;
      if (command?.type === 'navigate' && !isPreferenceCommand(command)) return command;
    }
    return null;
  }

  function patchNavigationCommand(baseCommand, text) {
    if (!baseCommand?.params) return null;
    const raw = cleanText(text, 180);
    const next = clone(baseCommand);
    next.id = createId('nav-edit');
    next.params = { ...next.params };

    const provider = inferProvider(raw);
    if (provider) {
      next.params.mapProvider = provider;
      next.params.appName = MAP_LABELS[provider];
      next.title = `${MAP_LABELS[provider]}导航`;
    }

    const mode = inferMode(raw);
    if (mode) {
      next.params.mode = normalizeMode(mode, next.params.mode);
      next.params.travelMode = next.params.mode;
      next.params.navigationMode = next.params.mode;
      next.params.transportMode = next.params.mode;
    }

    const routeOptions = inferRouteOptions(raw);
    next.params.routeOptions = { ...(next.params.routeOptions || {}), ...routeOptions };

    let alias = '';
    const destinationMatch = raw.match(/(?:目的地|地址|终点|导航到|改到|改成去|换到|去)\s*([^，。；;\n]+)/u);
    if (destinationMatch) alias = destinationMatch[1];
    if (/回家|回去|到家|去家|家里|我家/u.test(raw)) alias = '家';
    if (/去学校|到学校|回学校/u.test(raw)) alias = '学校';
    if (/去公司|到公司|回公司|去单位|到单位/u.test(raw)) alias = '公司';
    if (/去宿舍|回宿舍|到宿舍|去寝室|回寝室/u.test(raw)) alias = '宿舍';

    if (alias) {
      const rebuilt = buildNavigationCommand(alias, raw, {
        mapProvider: next.params.mapProvider,
        mode: next.params.mode,
        routeOptions: next.params.routeOptions,
      });
      if (rebuilt) return rebuilt;
    }

    next.params.mapProvider = normalizeProvider(next.params.mapProvider);
    next.params.appName = MAP_LABELS[next.params.mapProvider];
    next.title = `${next.params.appName}导航`;
    next.summary = next.params.placeAddressMissing ? `${next.params.destinationAlias || next.params.destination}（未填写地址）` : `到 ${next.params.destination}`;
    return next;
  }

  function parseNavigationEdit(text) {
    const raw = cleanText(text, 180);
    const hasEditIntent = /(改为|改成|换成|换为|改一下|重新|还是|用|坐公交|公交|地铁|步行|走路|骑车|骑行|开车|驾车|高德|百度|少步行|避开高速|少收费|回去|回家)/u.test(raw);
    if (!hasEditIntent) return null;
    const hasNavigationWord = /(导航|路线|地图|回去|回家|到家|去|目的地|终点|公交|地铁|步行|骑行|开车|驾车)/u.test(raw);
    if (!hasNavigationWord) return null;

    const base = getLastNavigationCommand();
    if (!base) return null;
    const command = patchNavigationCommand(base, raw);
    if (!command) return null;
    return {
      kind: command.params.placeAddressMissing ? 'need_user_info' : 'local_command',
      intent: 'navigation.modify',
      confidence: 0.93,
      source: 'local_context',
      missing: command.params.placeAddressMissing ? `${command.params.matchedPlaceLabel || command.params.destinationAlias}地址` : '',
      command,
    };
  }

  function routeText(text) {
    const raw = cleanText(text, 220);
    if (!raw) return { kind: 'empty', confidence: 1, source: 'router' };
    const local = parseNavigationPreference(raw) || parseNavigationEdit(raw) || parseNavigationStart(raw);
    if (local) return local;
    if (/(导航|路线|地图|附近|怎么去|怎么走|路况)/u.test(raw)) {
      return { kind: 'cloud_fallback', intent: 'navigation.unknown', confidence: 0.45, source: 'router' };
    }
    return { kind: 'pass', intent: 'chat_or_other', confidence: 0.2, source: 'router' };
  }

  function replyForDecision(decision) {
    const command = decision?.command;
    if (!command) return '';
    if (isPreferenceCommand(command)) {
      return `我整理好了导航偏好：${command.summary || '更新导航习惯'}。确认后我会保存到手机偏好里。`;
    }
    const map = command.params?.appName || '地图';
    const mode = MODE_LABELS[command.params?.mode] || '驾车';
    if (command.params?.placeAddressMissing) {
      return `我知道你想去“${command.params.destinationAlias || command.params.destination}”，但还缺少${decision.missing || '常用地址'}。你可以说“家就是重庆大学”这类话先保存地址。`;
    }
    if (decision.intent === 'navigation.modify') {
      return `已把上一条导航改为：用${map}${mode}导航到“${command.params.destination}”，确认后我再执行。`;
    }
    return `我理解为要用${map}${mode}导航到“${command.params.destination}”，确认后我再执行。`;
  }

  function toAssistantResult(text) {
    const decision = routeText(text);
    if (decision.kind !== 'local_command' && decision.kind !== 'need_user_info') return null;
    return {
      reply: replyForDecision(decision),
      action: 'mobile_command',
      records: [],
      mobileCommand: decision.command,
      source: 'ai_command_router_v3',
      router: { intent: decision.intent, confidence: decision.confidence, source: decision.source },
    };
  }

  function isUserEditingPreferencePanel() {
    const active = document.activeElement;
    return Boolean(active?.closest?.('#assistantPreferencePanel')) && ['INPUT', 'TEXTAREA', 'SELECT'].includes(active.tagName);
  }

  function formatCustomPlaces(list = []) {
    return normalizeCustomPlaces(list).map((item) => `${item.name}：${item.address}`).join('\n');
  }

  function scheduleSettingsPanelUpdate(force = false) {
    cancelAnimationFrame(settingsSyncFrame);
    settingsSyncFrame = requestAnimationFrame(() => updateSettingsPanel({ force }));
  }

  function updateSettingsPanel({ force = false } = {}) {
    const panel = document.querySelector('#assistantPreferencePanel');
    if (!panel || (!force && isUserEditingPreferencePanel())) return;
    const prefs = readNavPrefs();
    const setValue = (selector, value) => { const el = document.querySelector(selector); if (el && el.value !== value) el.value = value; };
    const setChecked = (selector, checked) => { const el = document.querySelector(selector); if (el && el.checked !== checked) el.checked = checked; };
    setValue('#assistantHomeAddressInput', prefs.places.home || '');
    setValue('#assistantSchoolAddressInput', prefs.places.school || '');
    setValue('#assistantWorkAddressInput', prefs.places.work || '');
    setValue('#assistantDormAddressInput', prefs.places.dorm || '');
    setValue('#assistantCustomPlacesInput', formatCustomPlaces(prefs.customPlaces));
    setValue('#assistantMapProviderSelect', prefs.mapProvider || 'baidu');
    setValue('#assistantDefaultModeSelect', prefs.defaultMode || 'driving');
    setChecked('#assistantAvoidHighwayInput', Boolean(prefs.routeOptions.avoidHighway));
    setChecked('#assistantAvoidTollsInput', Boolean(prefs.routeOptions.avoidTolls));
    setChecked('#assistantPreferSubwayInput', Boolean(prefs.routeOptions.preferSubway));
    setChecked('#assistantPreferLessWalkInput', Boolean(prefs.routeOptions.preferLessWalk));
    setChecked('#assistantRealtimeTrafficInput', Boolean(prefs.routeOptions.useRealtimeTraffic));
  }

  function patchAssistantPreferencesApi() {
    const api = window.AssistantPreferences;
    if (!api || api.__commandRouterV3Patched) return Boolean(api);
    api.__commandRouterV3Patched = true;
    api.getPreferences = readNavPrefs;
    api.getHomeAddress = () => readNavPrefs().places.home || '';
    api.getMapProvider = () => readNavPrefs().mapProvider;
    api.getMapLabel = (provider = readNavPrefs().mapProvider) => MAP_LABELS[normalizeProvider(provider)] || MAP_LABELS.baidu;
    api.getModeLabel = (mode = readNavPrefs().defaultMode) => MODE_LABELS[normalizeMode(mode)] || MODE_LABELS.driving;
    api.normalizePlaceKey = normalizePlaceKey;
    api.applyPreferenceUpdate = (updates = {}) => {
      const result = applyNavigationPreferenceUpdate(updates);
      scheduleSettingsPanelUpdate(true);
      return result;
    };
    api.resolveDestination = (destination) => {
      const resolved = resolveDestination(destination, readNavPrefs());
      return {
        destination: resolved.destination,
        alias: resolved.alias,
        matchedPlace: resolved.matchedPlaceKey ? { key: resolved.matchedPlaceKey, label: resolved.matchedPlaceLabel, address: resolved.placeAddressMissing ? '' : resolved.destination } : null,
        missingAddress: resolved.placeAddressMissing,
      };
    };
    api.decorateNavigationParams = (params = {}, options = {}) => {
      const prefs = readNavPrefs();
      const sourceText = String(options.sourceText || '');
      const provider = normalizeProvider(params.mapProvider || inferProvider(sourceText) || prefs.mapProvider);
      const alias = cleanText(params.destinationAlias || params.destination || '', 120);
      const resolved = resolveDestination(alias, prefs);
      const mode = normalizeMode(params.mode || inferMode(sourceText, prefs.defaultMode), prefs.defaultMode);
      return {
        ...params,
        appName: MAP_LABELS[provider],
        mapProvider: provider,
        mode,
        travelMode: mode,
        navigationMode: mode,
        transportMode: mode,
        routeOptions: { ...prefs.routeOptions, ...(params.routeOptions || {}) },
        destination: resolved.destination,
        destinationAlias: resolved.alias,
        matchedPlaceKey: resolved.matchedPlaceKey,
        matchedPlaceLabel: resolved.matchedPlaceLabel,
        placeAddressMissing: resolved.placeAddressMissing,
        homeAddressMissing: resolved.matchedPlaceKey === 'home' && resolved.placeAddressMissing,
      };
    };
    return true;
  }

  function patchMobileActions() {
    const actions = window.MobileCommandActions;
    if (!actions || actions.__commandRouterV3Patched) return Boolean(actions);
    actions.__commandRouterV3Patched = true;
    const baseParse = actions.parse;
    actions.parse = (text) => {
      const decision = routeText(text);
      if (decision.kind === 'local_command' || decision.kind === 'need_user_info') return decision.command;
      return baseParse?.(text) || null;
    };
    return true;
  }

  function installDebugApi() {
    window.AICommandRouter = {
      version: '20260516-3',
      route: routeText,
      toAssistantResult,
      replyForDecision,
      readNavigationPreferences: readNavPrefs,
      writeNavigationPreferences: writeNavPrefs,
      applyNavigationPreferenceUpdate,
      getLastNavigationCommand,
      resolveDestination,
      buildNavigationCommand,
      normalizeMode,
      normalizeProvider,
    };
  }

  function patchDependenciesWithLightRetry() {
    cancelAnimationFrame(patchRetryFrame);
    patchRetryFrame = requestAnimationFrame(() => {
      const prefsReady = patchAssistantPreferencesApi();
      const actionsReady = patchMobileActions();
      if (!prefsReady || !actionsReady) {
        window.setTimeout(() => {
          patchAssistantPreferencesApi();
          patchMobileActions();
          installDebugApi();
          scheduleSettingsPanelUpdate(false);
        }, 360);
      }
    });
  }

  function boot() {
    if (window[ROUTER_FLAG]) return;
    window[ROUTER_FLAG] = true;
    readNavPrefs({ fresh: true });
    installDebugApi();
    patchDependenciesWithLightRetry();
    window.addEventListener('assistant-preferences-changed', () => scheduleSettingsPanelUpdate(true));
    window.addEventListener('storage', (event) => {
      if ([NAV_PREF_KEY, HOME_ADDRESS_KEY, MAP_PROVIDER_KEY].includes(event.key)) {
        cachedPrefs = null;
        scheduleSettingsPanelUpdate(true);
      }
    });
    document.addEventListener('click', (event) => {
      if (event.target.closest?.('[data-settings-group="phone"], .nav-btn[data-view="settings"]')) {
        window.setTimeout(() => scheduleSettingsPanelUpdate(false), 120);
      }
    }, true);
    scheduleSettingsPanelUpdate(false);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();
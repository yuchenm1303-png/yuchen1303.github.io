(() => {
  'use strict';

  // Cloud command bridge
  // 负责：
  // 1. 给云端请求注入联网开关、commandProtocol、navigationContext。
  // 2. 把云端返回的 command / mobileCommand 标准化成本地动作卡片。
  // 3. 管理“自动联网 / 强制联网”按钮状态。
  //
  // 维护边界：
  // - 本文件不负责天气、新闻、闲聊回答本身，避免和 Orchestrator 抢活。
  // - 本文件不直接执行手机动作，只负责补全 mobileCommand，执行仍需用户确认。
  // - 本文件只处理高置信结构化指令转换；低置信语义判断留给云端 Orchestrator。

  const FORCE_SEARCH_KEY = 'ai-assistant-force-web-search-v1';
  const CHAT_KEY = 'ai-ledger-chat-v2';
  const AI_CONFIG_KEY = 'ai-ledger-ai-config-v1';
  const NAV_PREF_KEY = 'ai-assistant-navigation-preferences-v2';
  const STYLE_ID = 'cloud-command-bridge-style';

  const MAP_LABELS = { baidu: '百度地图', amap: '高德地图' };
  const MODE_LABELS = { driving: '驾车', walking: '步行', riding: '骑行', transit: '公交/地铁' };
  const PLACE_LABELS = { home: '家', school: '学校', work: '公司', dorm: '宿舍' };

  let forceWebSearch = localStorage.getItem(FORCE_SEARCH_KEY) === 'true';
  let fetchPatched = false;

  // ---------------------------------------------------------------------------
  // Basic helpers
  // ---------------------------------------------------------------------------

  function cleanText(value, max = 160) {
    return String(value || '').trim().replace(/\s+/g, ' ').slice(0, max);
  }

  function clone(value) {
    return JSON.parse(JSON.stringify(value));
  }

  function createId(prefix = 'cloud-cmd') {
    if (crypto.randomUUID) return `${prefix}-${crypto.randomUUID()}`;
    return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  // ---------------------------------------------------------------------------
  // Navigation normalization
  // ---------------------------------------------------------------------------

  function normalizeProvider(value) {
    const text = String(value || '').toLowerCase();
    if (text === 'amap' || /高德|gaode|amap/.test(String(value || ''))) return 'amap';
    return 'baidu';
  }

  function normalizeMode(value, fallback = 'driving') {
    const raw = String(value || '').trim();
    const text = raw.toLowerCase();
    if (['transit', 'bus', 'subway', 'metro', 'public_transport', 'public-transport'].includes(text) || /公交|地铁|公共交通|轻轨|轨道|巴士|乘车|坐车/.test(raw)) return 'transit';
    if (['walking', 'walk', 'foot'].includes(text) || /步行|走路|步走/.test(raw)) return 'walking';
    if (['riding', 'bike', 'bicycle', 'cycling', 'ride'].includes(text) || /骑行|骑车|自行车|单车|电动车/.test(raw)) return 'riding';
    if (['driving', 'drive', 'car', 'taxi'].includes(text) || /驾车|开车|自驾|打车|出租车|网约车/.test(raw)) return 'driving';
    return fallback || 'driving';
  }

  function addModeAliases(params = {}) {
    // 兼容 navigation-execution-compat.js。地图 Deep Link 会读取多个 mode 字段，
    // 所以这里统一补齐，避免卡片显示公交/地铁但执行时默认驾车。
    const mode = normalizeMode(params.mode || params.travelMode || params.navigationMode || params.transportMode);
    return {
      ...params,
      mode,
      travelMode: mode,
      navigationMode: mode,
      transportMode: mode,
    };
  }

  function readNavPrefs() {
    if (window.AICommandRouter?.readNavigationPreferences) {
      try { return window.AICommandRouter.readNavigationPreferences(); } catch {}
    }
    try {
      const parsed = JSON.parse(localStorage.getItem(NAV_PREF_KEY) || '{}');
      return {
        places: {
          home: cleanText(parsed.places?.home || localStorage.getItem('ai-assistant-home-address-v1') || '', 120),
          school: cleanText(parsed.places?.school || '', 120),
          work: cleanText(parsed.places?.work || '', 120),
          dorm: cleanText(parsed.places?.dorm || '', 120),
        },
        customPlaces: Array.isArray(parsed.customPlaces) ? parsed.customPlaces : [],
        mapProvider: normalizeProvider(parsed.mapProvider || localStorage.getItem('ai-assistant-map-provider-v1')),
        defaultMode: normalizeMode(parsed.defaultMode),
        routeOptions: {
          avoidHighway: Boolean(parsed.routeOptions?.avoidHighway),
          avoidTolls: Boolean(parsed.routeOptions?.avoidTolls),
          preferSubway: Boolean(parsed.routeOptions?.preferSubway),
          preferLessWalk: Boolean(parsed.routeOptions?.preferLessWalk),
          useRealtimeTraffic: parsed.routeOptions?.useRealtimeTraffic !== false,
        },
      };
    } catch {
      return {
        places: { home: '', school: '', work: '', dorm: '' },
        customPlaces: [],
        mapProvider: 'baidu',
        defaultMode: 'driving',
        routeOptions: { useRealtimeTraffic: true },
      };
    }
  }

  function normalizePlaceKey(alias) {
    const text = cleanText(alias, 24).replace(/^(我的|我|去|到|回|导航到|导航去)/u, '');
    if (/^(家|我家|家里|家庭|回家|到家)$/u.test(text)) return 'home';
    if (/^(学校|校区|大学|学院)$/u.test(text)) return 'school';
    if (/^(公司|单位|办公室|上班地方|实习单位)$/u.test(text)) return 'work';
    if (/^(宿舍|寝室|公寓|住处)$/u.test(text)) return 'dorm';
    return '';
  }

  function resolveDestination(alias, prefs = readNavPrefs()) {
    const raw = cleanText(alias, 120);
    const key = normalizePlaceKey(raw);
    if (key) {
      const address = prefs.places?.[key] || '';
      return {
        destination: address || raw,
        destinationAlias: raw,
        matchedPlaceKey: key,
        matchedPlaceLabel: PLACE_LABELS[key],
        placeAddressMissing: !address,
      };
    }
    const custom = (prefs.customPlaces || []).find((item) => item?.name && item.name.toLowerCase() === raw.toLowerCase());
    if (custom) {
      return {
        destination: custom.address,
        destinationAlias: raw,
        matchedPlaceKey: `custom:${custom.name}`,
        matchedPlaceLabel: custom.name,
        placeAddressMissing: false,
      };
    }
    return { destination: raw, destinationAlias: raw, matchedPlaceKey: '', matchedPlaceLabel: '', placeAddressMissing: false };
  }

  function loadChatMessages() {
    if (Array.isArray(window.chatMessages)) return window.chatMessages;
    try {
      const parsed = JSON.parse(localStorage.getItem(CHAT_KEY) || '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch { return []; }
  }

  function getLastNavigationCommand() {
    if (window.AICommandRouter?.getLastNavigationCommand) {
      try {
        const command = window.AICommandRouter.getLastNavigationCommand();
        if (command) return command;
      } catch {}
    }
    const messages = loadChatMessages();
    for (let i = messages.length - 1; i >= 0; i -= 1) {
      const cmd = messages[i]?.mobileCommand;
      if (cmd?.type === 'navigate' && cmd?.params?.intent !== 'navigation_preference' && !cmd?.params?.updates) return cmd;
    }
    return null;
  }

  function normalizeRouteOptions(options = {}) {
    return {
      avoidHighway: Boolean(options.avoidHighway),
      avoidTolls: Boolean(options.avoidTolls),
      preferSubway: Boolean(options.preferSubway),
      preferLessWalk: Boolean(options.preferLessWalk),
      useRealtimeTraffic: options.useRealtimeTraffic !== false,
    };
  }

  // ---------------------------------------------------------------------------
  // Cloud command materialization
  // ---------------------------------------------------------------------------

  function buildNavigateCommand(payload = {}, baseCommand = null) {
    const prefs = readNavPrefs();
    const base = baseCommand ? clone(baseCommand) : null;
    const params = payload.params || payload.updates || payload;
    const mapProvider = normalizeProvider(params.mapProvider || base?.params?.mapProvider || prefs.mapProvider);
    const mode = normalizeMode(params.mode || params.travelMode || params.navigationMode || params.transportMode || base?.params?.mode || prefs.defaultMode);
    const destinationAlias = cleanText(
      params.destinationAlias || params.destination || params.to || base?.params?.destinationAlias || base?.params?.destination || '',
      120
    );
    const resolved = resolveDestination(destinationAlias, prefs);
    const routeOptions = {
      ...normalizeRouteOptions(prefs.routeOptions),
      ...normalizeRouteOptions(base?.params?.routeOptions || {}),
      ...normalizeRouteOptions(params.routeOptions || {}),
    };

    return {
      id: createId(base ? 'cloud-nav-edit' : 'cloud-nav'),
      type: 'navigate',
      title: `${MAP_LABELS[mapProvider]}导航`,
      summary: resolved.placeAddressMissing ? `${resolved.destinationAlias}（未填写地址）` : `到 ${resolved.destination}`,
      params: addModeAliases({
        appName: MAP_LABELS[mapProvider],
        mapProvider,
        mode,
        routeOptions,
        destination: resolved.destination,
        destinationAlias: resolved.destinationAlias,
        matchedPlaceKey: resolved.matchedPlaceKey,
        matchedPlaceLabel: resolved.matchedPlaceLabel,
        placeAddressMissing: resolved.placeAddressMissing,
        homeAddressMissing: resolved.matchedPlaceKey === 'home' && resolved.placeAddressMissing,
      }),
    };
  }

  function buildPreferenceCommand(command = {}) {
    const updates = command.updates || command.params?.updates || command.params || {};
    const rows = command.rows || command.params?.rows || [];
    return {
      id: createId('cloud-nav-pref'),
      type: 'navigate',
      commandKind: 'navigation_preference',
      title: '保存导航偏好',
      summary: command.summary || rows.map((row) => Array.isArray(row) ? `${row[0]}：${row[1]}` : '').filter(Boolean).join('；') || '更新导航偏好',
      params: { intent: 'navigation_preference', updates, rows },
    };
  }

  function materializeCloudCommand(rawCommand) {
    if (!rawCommand || typeof rawCommand !== 'object') return null;
    if (rawCommand.type && ['set_alarm', 'open_app'].includes(rawCommand.type)) return rawCommand;
    if (rawCommand.type === 'navigate' && rawCommand.params?.intent !== 'navigation_preference') {
      return buildNavigateCommand(rawCommand.params || rawCommand);
    }

    const intent = rawCommand.intent || rawCommand.action || rawCommand.name || rawCommand.type || '';
    if (/navigation\.preference\.set|navigation_preference|preference\.navigation/i.test(intent)) return buildPreferenceCommand(rawCommand);
    if (/navigation\.modify|navigation_modify/i.test(intent)) return buildNavigateCommand(rawCommand.updates || rawCommand.params || rawCommand, getLastNavigationCommand());
    if (/navigation\.start|navigate|navigation_start/i.test(intent)) return buildNavigateCommand(rawCommand.params || rawCommand);
    return null;
  }

  function normalizeCloudResponse(data) {
    if (!data || typeof data !== 'object') return data;
    const next = { ...data };
    const rawCommand = next.mobileCommand || next.command || next.localCommand || (Array.isArray(next.commands) ? next.commands[0] : null);
    const mobileCommand = materializeCloudCommand(rawCommand);
    if (mobileCommand) {
      next.mobileCommand = mobileCommand;
      next.action = 'mobile_command';
      if (!next.reply) next.reply = next.response || next.text || '我整理好了这个动作，确认后再执行。';
      next.source = next.source || 'cloud_command_bridge';
    }
    if (next.webSources && !next.citations) next.citations = next.webSources;
    return next;
  }

  // ---------------------------------------------------------------------------
  // Request injection
  // ---------------------------------------------------------------------------

  function getAiConfig() {
    try { return JSON.parse(localStorage.getItem(AI_CONFIG_KEY) || '{}'); }
    catch { return {}; }
  }

  function shouldPatchAiRequest(body) {
    return Boolean(body && Array.isArray(body.messages) && ('ledgerContext' in body || 'clientTools' in body || 'pendingDraft' in body));
  }

  function getWebSearchPayload() {
    return {
      forceWebSearch,
      webSearchMode: forceWebSearch ? 'force' : 'auto',
      searchMode: forceWebSearch ? 'force' : 'auto',
      webSearch: {
        mode: forceWebSearch ? 'force' : 'auto',
        force: forceWebSearch,
        keepAutoSearchWhenOff: true,
        requireCitationsWhenForced: true,
      },
    };
  }

  function getBridgePayload() {
    return {
      version: 'cloud-command-bridge-20260516-2',
      webSearch: getWebSearchPayload().webSearch,
      commandProtocol: {
        enabled: true,
        requireStructuredCommandWhenActionable: true,
        allowedIntents: [
          'navigation.start',
          'navigation.modify',
          'navigation.preference.set',
          'alarm.set',
          'app.open',
          'ledger.create',
        ],
        commandShape: {
          intent: 'string',
          target: 'last_navigation | current_app | none',
          params: 'object',
          updates: 'object',
          reply: 'short user-facing text',
        },
        instruction: '如果用户要求执行、修改或保存本地动作，不要只给自然语言回复；必须同时返回 command 或 mobileCommand JSON。本地会验证、补全并弹确认卡片。',
      },
      navigationContext: {
        preferences: readNavPrefs(),
        lastNavigation: getLastNavigationCommand(),
      },
    };
  }

  function patchFetch() {
    if (fetchPatched || window.__cloudCommandBridgeFetchPatched) return;
    fetchPatched = true;
    window.__cloudCommandBridgeFetchPatched = true;
    const originalFetch = window.fetch.bind(window);

    window.fetch = async (input, init = {}) => {
      let patchedInit = init;
      let shouldNormalizeResponse = false;
      try {
        const method = String(init?.method || 'GET').toUpperCase();
        const bodyText = typeof init?.body === 'string' ? init.body : '';
        if (method === 'POST' && bodyText) {
          const body = JSON.parse(bodyText);
          if (shouldPatchAiRequest(body)) {
            const bridge = getBridgePayload();
            const patchedBody = {
              ...body,
              ...getWebSearchPayload(),
              commandProtocol: bridge.commandProtocol,
              navigationContext: bridge.navigationContext,
              clientTools: [...(Array.isArray(body.clientTools) ? body.clientTools : []), {
                name: 'mobile.command_bridge',
                action: 'mobile_command',
                commandType: 'cloud_structured_command',
                title: '云端结构化本地指令',
              }],
            };
            patchedInit = { ...init, body: JSON.stringify(patchedBody) };
            shouldNormalizeResponse = true;
          }
        }
      } catch {}

      const response = await originalFetch(input, patchedInit);
      if (!shouldNormalizeResponse) return response;

      try {
        const text = await response.clone().text();
        const data = text ? JSON.parse(text) : null;
        const normalized = normalizeCloudResponse(data);
        return new Response(JSON.stringify(normalized), {
          status: response.status,
          statusText: response.statusText,
          headers: response.headers,
        });
      } catch {
        return response;
      }
    };
  }

  // ---------------------------------------------------------------------------
  // Web search toggle UI
  // ---------------------------------------------------------------------------

  function installStyle() {
    if (document.querySelector(`#${STYLE_ID}`)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      .web-search-toggle{
        display:inline-flex;align-items:center;gap:6px;border:1px solid rgba(255,255,255,.34);
        border-radius:999px;padding:8px 11px;background:rgba(255,255,255,.16);color:rgba(244,250,255,.88);
        font-size:13px;font-weight:800;box-shadow:inset 0 1px 0 rgba(255,255,255,.24);backdrop-filter:blur(14px);-webkit-backdrop-filter:blur(14px);
        transition:transform .18s ease, background .18s ease, border-color .18s ease, color .18s ease;
      }
      .web-search-toggle:active{transform:scale(.96)}
      .web-search-toggle[data-force="true"]{
        background:linear-gradient(135deg,rgba(0,196,180,.38),rgba(92,106,255,.34));
        border-color:rgba(119,255,235,.52);color:#fff;box-shadow:0 10px 24px rgba(0,150,180,.20),inset 0 1px 0 rgba(255,255,255,.36);
      }
      .web-search-toggle .web-search-dot{width:7px;height:7px;border-radius:999px;background:rgba(255,255,255,.45)}
      .web-search-toggle[data-force="true"] .web-search-dot{background:#70ffe9;box-shadow:0 0 12px rgba(112,255,233,.9)}
      .chat-header-actions .web-search-toggle{min-height:34px}
    `;
    document.head.appendChild(style);
  }

  function updateToggle(button) {
    if (!button) return;
    button.dataset.force = String(forceWebSearch);
    button.setAttribute('aria-pressed', String(forceWebSearch));
    button.title = forceWebSearch ? '联网搜索：强制搜索后回答' : '联网搜索：自动判断是否搜索';
    button.innerHTML = `<span>🌐</span><span>${forceWebSearch ? '强制联网' : '自动联网'}</span><i class="web-search-dot"></i>`;
  }

  function installToggle() {
    if (document.querySelector('#webSearchToggleBtn')) return;
    const host = document.querySelector('.chat-header-actions') || document.querySelector('.chat-tags') || document.querySelector('.chat-composer');
    if (!host) return;
    const button = document.createElement('button');
    button.id = 'webSearchToggleBtn';
    button.className = 'web-search-toggle';
    button.type = 'button';
    button.setAttribute('aria-label', '切换联网搜索模式');
    button.addEventListener('click', () => {
      forceWebSearch = !forceWebSearch;
      localStorage.setItem(FORCE_SEARCH_KEY, String(forceWebSearch));
      updateToggle(button);
      const toast = document.querySelector('#toast');
      if (toast) {
        toast.textContent = forceWebSearch ? '联网搜索已设为强制模式' : '联网搜索已恢复自动模式';
        toast.classList.add('show');
        window.clearTimeout(installToggle.toastTimer);
        installToggle.toastTimer = window.setTimeout(() => toast.classList.remove('show'), 2200);
      }
    });
    updateToggle(button);
    host.appendChild(button);
  }

  // ---------------------------------------------------------------------------
  // Public debug API and boot
  // ---------------------------------------------------------------------------

  function installDebugApi() {
    window.CloudCommandBridge = {
      version: '20260516-2',
      isForceWebSearch: () => forceWebSearch,
      getWebSearchPayload,
      setForceWebSearch(value) {
        forceWebSearch = Boolean(value);
        localStorage.setItem(FORCE_SEARCH_KEY, String(forceWebSearch));
        updateToggle(document.querySelector('#webSearchToggleBtn'));
      },
      normalizeCloudResponse,
      materializeCloudCommand,
      getBridgePayload,
      normalizeMode,
    };
  }

  function boot() {
    installStyle();
    installToggle();
    installDebugApi();
    patchFetch();
    window.setTimeout(() => { installToggle(); patchFetch(); installDebugApi(); }, 400);
    window.setTimeout(() => { installToggle(); patchFetch(); installDebugApi(); }, 1200);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();

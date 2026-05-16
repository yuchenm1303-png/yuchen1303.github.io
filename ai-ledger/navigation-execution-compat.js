(() => {
  const PATCH_FLAG = '__navigationExecutionCompatPatched';
  const HANDLER_FLAG = '__navigationExecutionCompatClickHandler';
  const CHAT_KEY = 'ai-ledger-chat-v2';

  function normalizeMode(value) {
    const raw = String(value || '').trim();
    const text = raw.toLowerCase();
    if (['transit', 'bus', 'subway', 'metro', 'public_transport', 'public-transport'].includes(text) || /公交|地铁|公共交通|轻轨|轨道|巴士|乘车|坐车/.test(raw)) return 'transit';
    if (['walking', 'walk', 'foot'].includes(text) || /步行|走路|步走/.test(raw)) return 'walking';
    if (['riding', 'bike', 'bicycle', 'cycling', 'ride'].includes(text) || /骑行|骑车|自行车|单车|电动车/.test(raw)) return 'riding';
    if (['driving', 'drive', 'car', 'taxi'].includes(text) || /驾车|开车|自驾|打车|出租车|网约车/.test(raw)) return 'driving';
    return 'driving';
  }

  function modeAliases(mode) {
    const normalized = normalizeMode(mode);
    const amapRouteType = { driving: '0', transit: '1', walking: '2', riding: '3' }[normalized] || '0';
    const baiduMode = { driving: 'driving', transit: 'transit', walking: 'walking', riding: 'riding' }[normalized] || 'driving';
    return {
      mode: normalized,
      travelMode: normalized,
      navigationMode: normalized,
      transportMode: normalized,
      routeMode: normalized,
      baiduMode,
      amapMode: normalized,
      amapRouteType,
      modeCode: amapRouteType,
    };
  }

  function normalizeNavigateParams(params = {}) {
    const normalizedMode = normalizeMode(params.mode || params.travelMode || params.navigationMode || params.transportMode || params.routeMode);
    return {
      ...params,
      ...modeAliases(normalizedMode),
      routeOptions: {
        useRealtimeTraffic: true,
        ...(params.routeOptions || {}),
      },
    };
  }

  function patchPlugin(plugin, name) {
    if (!plugin || typeof plugin.navigate !== 'function' || plugin[PATCH_FLAG]) return false;
    const originalNavigate = plugin.navigate.bind(plugin);
    plugin.navigate = async (params = {}) => {
      const normalized = normalizeNavigateParams(params);
      console.info('[NavigationCompat] plugin navigate params', normalized);
      return originalNavigate(normalized);
    };
    plugin[PATCH_FLAG] = true;
    plugin.__normalizeNavigateParams = normalizeNavigateParams;
    console.info(`[NavigationCompat] patched ${name}.navigate`);
    return true;
  }

  function patchAll() {
    const plugins = window.Capacitor?.Plugins;
    if (!plugins) return;
    patchPlugin(plugins.MobileAssistant, 'MobileAssistant');
    patchPlugin(plugins.MobileTools, 'MobileTools');
  }

  function readChatMessages() {
    if (Array.isArray(window.chatMessages)) return window.chatMessages;
    try {
      const parsed = JSON.parse(localStorage.getItem(CHAT_KEY) || '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  function findCommand(commandId) {
    const messages = readChatMessages();
    const message = [...messages].reverse().find((item) => item?.mobileCommand?.id === commandId);
    return message?.mobileCommand || null;
  }

  function isPreferenceCommand(command) {
    return command?.commandKind === 'navigation_preference' || command?.params?.intent === 'navigation_preference' || Boolean(command?.params?.updates);
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function updateCard(commandId, state, message) {
    const card = document.querySelector(`[data-mobile-card="${CSS.escape(commandId)}"]`);
    if (!card) return;
    const status = card.querySelector('.mobile-command-status');
    if (status) {
      status.className = `mobile-command-status ${state}`;
      status.textContent = state === 'done' ? '已执行' : state === 'failed' ? '执行失败' : state === 'cancelled' ? '已取消' : '待确认';
    }
    card.querySelector('.mobile-command-actions')?.remove();
    card.querySelector('.mobile-command-message')?.remove();
    if (message) card.insertAdjacentHTML('beforeend', `<div class="mobile-command-message">${escapeHtml(message)}</div>`);
  }

  function buildBaiduUri(params) {
    const p = normalizeNavigateParams(params);
    const destination = encodeURIComponent(p.destination || p.destinationAlias || '');
    const mode = encodeURIComponent(p.baiduMode || p.mode || 'driving');
    const src = encodeURIComponent('ai-ledger');
    const coordType = encodeURIComponent('bd09ll');
    return `baidumap://map/direction?destination=${destination}&mode=${mode}&coord_type=${coordType}&src=${src}`;
  }

  function buildAmapUri(params) {
    const p = normalizeNavigateParams(params);
    const dname = encodeURIComponent(p.destination || p.destinationAlias || '');
    const sourceApplication = encodeURIComponent('AI助手');
    const type = encodeURIComponent(p.amapRouteType || '0');
    return `androidamap://route?sourceApplication=${sourceApplication}&dname=${dname}&dev=0&t=${type}`;
  }

  function buildWebFallback(params) {
    const p = normalizeNavigateParams(params);
    const destination = encodeURIComponent(p.destination || p.destinationAlias || '');
    if (p.mapProvider === 'amap') return `https://uri.amap.com/navigation?to=,,${destination}&mode=${p.mode}&policy=1&src=ai-ledger&coordinate=gaode&callnative=1`;
    return `https://api.map.baidu.com/direction?destination=${destination}&mode=${p.baiduMode || p.mode}&region=中国&output=html&src=ai-ledger`;
  }

  function buildNavigationUri(params = {}) {
    const p = normalizeNavigateParams(params);
    if (p.mapProvider === 'amap') return buildAmapUri(p);
    return buildBaiduUri(p);
  }

  function launchByUri(params = {}) {
    const normalized = normalizeNavigateParams(params);
    const uri = buildNavigationUri(normalized);
    const fallback = buildWebFallback(normalized);
    console.info('[NavigationCompat] launch uri', uri, normalized);

    // Give native URL scheme the first chance. It carries explicit transit/walking/riding mode.
    window.location.href = uri;

    // If Android blocks the scheme, the page remains foreground. Open web/native fallback shortly after.
    window.setTimeout(() => {
      if (document.visibilityState === 'visible') window.location.href = fallback;
    }, 900);

    const label = { driving: '驾车', transit: '公交/地铁', walking: '步行', riding: '骑行' }[normalized.mode] || '驾车';
    const map = normalized.appName || (normalized.mapProvider === 'amap' ? '高德地图' : '百度地图');
    return { ok: true, message: `已尝试用${map}${label}导航到“${normalized.destination || normalized.destinationAlias || '目的地'}”。` };
  }

  function installNavigateClickHandler() {
    if (document[HANDLER_FLAG]) return;
    document[HANDLER_FLAG] = true;
    document.addEventListener('click', (event) => {
      const runBtn = event.target.closest?.('[data-mobile-run]');
      if (!runBtn) return;
      const command = findCommand(runBtn.dataset.mobileRun);
      if (!command || command.type !== 'navigate' || isPreferenceCommand(command)) return;

      event.preventDefault();
      event.stopImmediatePropagation();
      try {
        updateCard(command.id, 'pending', '正在打开地图……');
        const result = launchByUri(command.params || {});
        updateCard(command.id, 'done', result.message);
      } catch (error) {
        const plugin = window.Capacitor?.Plugins?.MobileAssistant || window.Capacitor?.Plugins?.MobileTools;
        if (plugin?.navigate) {
          plugin.navigate(normalizeNavigateParams(command.params || {}))
            .then((result) => updateCard(command.id, result?.ok ? 'done' : 'failed', result?.message || '已尝试打开地图。'))
            .catch((err) => updateCard(command.id, 'failed', String(err?.message || err || '导航失败')));
        } else {
          updateCard(command.id, 'failed', String(error?.message || error || '导航失败'));
        }
      }
    }, true);
  }

  window.NavigationExecutionCompat = {
    version: '2026-05-16-2-deeplink',
    normalizeMode,
    normalizeNavigateParams,
    buildNavigationUri,
    launchByUri,
    patchAll,
  };

  patchAll();
  installNavigateClickHandler();
  window.addEventListener('DOMContentLoaded', () => { patchAll(); installNavigateClickHandler(); });
  window.setTimeout(() => { patchAll(); installNavigateClickHandler(); }, 300);
  window.setTimeout(() => { patchAll(); installNavigateClickHandler(); }, 1200);
})();
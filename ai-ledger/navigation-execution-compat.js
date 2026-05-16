(() => {
  'use strict';

  const PATCH_FLAG = '__navigationExecutionCompatPatched';

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
    plugin.navigate = async (params = {}) => originalNavigate(normalizeNavigateParams(params));
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

  function cleanupOldInjectedUi() {
    document.getElementById('visual-design-director-style')?.remove();
    document.getElementById('model-picker-hero-polish-style')?.remove();
    document.body?.classList.remove('visual-design-v1');
  }

  window.NavigationExecutionCompat = {
    version: '2026-05-16-10-clean-navigation-only',
    normalizeMode,
    normalizeNavigateParams,
    patchAll,
    cleanupOldInjectedUi,
  };

  cleanupOldInjectedUi();
  patchAll();
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => { cleanupOldInjectedUi(); patchAll(); }, { once: true });
  }
  window.setTimeout(() => { cleanupOldInjectedUi(); patchAll(); }, 300);
})();
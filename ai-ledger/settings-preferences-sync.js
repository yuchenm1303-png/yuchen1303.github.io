(() => {
  const STYLE_ID = 'settings-preferences-sync-style';
  const NAV_PREF_KEY = 'ai-assistant-navigation-preferences-v2';
  const HOME_ADDRESS_KEY = 'ai-assistant-home-address-v1';
  const MAP_PROVIDER_KEY = 'ai-assistant-map-provider-v1';

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

  const MAP_LABEL = { baidu: '百度地图', amap: '高德地图' };
  const MODE_LABEL = { driving: '驾车', walking: '步行', riding: '骑行', transit: '公交/地铁' };

  let lastSavedSignature = '';

  function clone(value) {
    return JSON.parse(JSON.stringify(value));
  }

  function readPrefs() {
    const fromModule = window.AssistantPreferences?.getPreferences?.();
    if (fromModule?.places) return normalizePrefs(fromModule);
    try {
      return normalizePrefs(JSON.parse(localStorage.getItem(NAV_PREF_KEY) || '{}'));
    } catch {
      return normalizePrefs({});
    }
  }

  function normalizePrefs(input = {}) {
    const prefs = clone(DEFAULT_PREFS);
    const oldHome = localStorage.getItem(HOME_ADDRESS_KEY) || '';
    const oldMap = localStorage.getItem(MAP_PROVIDER_KEY) || '';
    prefs.places.home = String(input.places?.home || input.homeAddress || oldHome || '').trim();
    prefs.places.school = String(input.places?.school || '').trim();
    prefs.places.work = String(input.places?.work || '').trim();
    prefs.places.dorm = String(input.places?.dorm || '').trim();
    prefs.customPlaces = Array.isArray(input.customPlaces) ? input.customPlaces.filter((item) => item?.name && item?.address) : [];
    prefs.mapProvider = input.mapProvider === 'amap' || oldMap === 'amap' ? 'amap' : 'baidu';
    prefs.defaultMode = ['driving', 'walking', 'riding', 'transit'].includes(input.defaultMode) ? input.defaultMode : 'driving';
    prefs.routeOptions = { ...prefs.routeOptions, ...(input.routeOptions || {}) };
    Object.keys(prefs.routeOptions).forEach((key) => { prefs.routeOptions[key] = Boolean(prefs.routeOptions[key]); });
    return prefs;
  }

  function isUserEditingPreferencePanel() {
    const active = document.activeElement;
    return Boolean(active?.closest?.('#assistantPreferencePanel'))
      && ['INPUT', 'TEXTAREA', 'SELECT'].includes(active.tagName);
  }

  function setValue(selector, value) {
    const el = document.querySelector(selector);
    if (!el) return false;
    el.value = value;
    return true;
  }

  function setChecked(selector, checked) {
    const el = document.querySelector(selector);
    if (!el) return false;
    el.checked = checked;
    return true;
  }

  function formatCustomPlaces(list = []) {
    return list.map((item) => `${item.name}：${item.address}`).join('\n');
  }

  function optionText(options = {}) {
    const rows = [];
    if (options.avoidHighway) rows.push('避开高速');
    if (options.avoidTolls) rows.push('少收费');
    if (options.preferSubway) rows.push('地铁优先');
    if (options.preferLessWalk) rows.push('少步行');
    if (options.useRealtimeTraffic) rows.push('参考实时路况');
    return rows.length ? rows.join('、') : '无特殊偏好';
  }

  function escapeHtml(value) {
    return String(value || '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function refreshPreviewFromPrefs(prefs) {
    const preview = document.querySelector('#assistantPrefPreview');
    if (!preview) return;
    const places = [
      prefs.places.home ? `家：${prefs.places.home}` : '',
      prefs.places.school ? `学校：${prefs.places.school}` : '',
      prefs.places.work ? `公司：${prefs.places.work}` : '',
      prefs.places.dorm ? `宿舍：${prefs.places.dorm}` : '',
    ].filter(Boolean).slice(0, 3).join('；');
    const map = MAP_LABEL[prefs.mapProvider] || '百度地图';
    const mode = MODE_LABEL[prefs.defaultMode] || '驾车';
    preview.innerHTML = places
      ? `以后可直接说 <strong>导航回家 / 去学校 / 去公司</strong>。当前默认：<strong>${escapeHtml(map)}</strong> · <strong>${escapeHtml(mode)}</strong>。<br>${escapeHtml(places)}<br>路线习惯：<strong>${escapeHtml(optionText(prefs.routeOptions))}</strong>`
      : `还没填写常用地址。填写后，AI 可以把“导航回家、去学校、去公司”自动替换为具体地址。当前默认：<strong>${escapeHtml(map)}</strong> · <strong>${escapeHtml(mode)}</strong>。`;
  }

  function syncPanel({ force = false, allowWhileEditing = false, flash = false } = {}) {
    const panel = document.querySelector('#assistantPreferencePanel');
    if (!panel) return;
    if (!allowWhileEditing && isUserEditingPreferencePanel()) return;

    const prefs = readPrefs();
    const signature = JSON.stringify(prefs);
    if (!force && signature === lastSavedSignature) return;
    lastSavedSignature = signature;

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
    refreshPreviewFromPrefs(prefs);
    if (flash) flashPanel();
  }

  function installStyle() {
    if (document.querySelector(`#${STYLE_ID}`)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      #assistantPreferencePanel[data-sync-flash="true"]{animation:navPrefSyncFlash 680ms ease both}
      @keyframes navPrefSyncFlash{
        0%{box-shadow:0 0 0 rgba(11,143,139,0)}
        38%{box-shadow:0 0 0 4px rgba(11,143,139,.16),0 18px 45px rgba(8,106,115,.14)}
        100%{box-shadow:var(--shadow,0 18px 45px rgba(8,106,115,.14))}
      }
    `;
    document.head.appendChild(style);
  }

  function flashPanel() {
    const panel = document.querySelector('#assistantPreferencePanel');
    if (!panel) return;
    panel.dataset.syncFlash = 'false';
    void panel.offsetWidth;
    panel.dataset.syncFlash = 'true';
    window.setTimeout(() => { panel.dataset.syncFlash = 'false'; }, 700);
  }

  function scheduleSync(options = {}) {
    window.setTimeout(() => syncPanel(options), 0);
    window.setTimeout(() => syncPanel(options), 160);
    window.setTimeout(() => syncPanel(options), 520);
  }

  function patchApplyPreferenceUpdate() {
    const prefsApi = window.AssistantPreferences;
    if (!prefsApi || prefsApi.__safeSyncPatched || typeof prefsApi.applyPreferenceUpdate !== 'function') return;
    const original = prefsApi.applyPreferenceUpdate.bind(prefsApi);
    prefsApi.applyPreferenceUpdate = (updates = {}) => {
      const result = original(updates);
      scheduleSync({ force: true, allowWhileEditing: true, flash: true });
      return result;
    };
    prefsApi.__safeSyncPatched = true;
  }

  function boot() {
    installStyle();
    patchApplyPreferenceUpdate();
    scheduleSync({ force: true });

    window.addEventListener('assistant-preferences-changed', () => {
      scheduleSync({ force: true, allowWhileEditing: true, flash: true });
    });

    document.addEventListener('click', (event) => {
      if (event.target.closest?.('[data-settings-group="phone"], .nav-btn[data-view="settings"]')) {
        scheduleSync({ force: true });
      }
    }, true);

    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) scheduleSync({ force: true });
    });

    window.setTimeout(() => { patchApplyPreferenceUpdate(); scheduleSync({ force: true }); }, 300);
    window.setTimeout(() => { patchApplyPreferenceUpdate(); scheduleSync({ force: true }); }, 1200);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();

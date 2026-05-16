(() => {
  'use strict';

  const PATCH_FLAG = '__navigationExecutionCompatPatched';
  const MODEL_POLISH_STYLE_ID = 'model-picker-hero-polish-style';

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

  function removeUnstableVisualLayer() {
    document.getElementById('visual-design-director-style')?.remove();
    document.body?.classList.remove('visual-design-v1');
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function installModelPickerPolishStyle() {
    const old = document.getElementById(MODEL_POLISH_STYLE_ID);
    if (old) old.remove();
    const style = document.createElement('style');
    style.id = MODEL_POLISH_STYLE_ID;
    style.textContent = `
      .chat-summary-strip.model-picker-hero-strip { display:grid!important; grid-template-columns:minmax(0,1fr)!important; gap:0!important; margin-bottom:2px!important; }
      .chat-summary-strip.model-picker-hero-strip .summary-chip { display:none!important; }
      .model-picker-btn.hero-model-picker-btn { width:100%!important; min-width:0!important; height:62px!important; min-height:62px!important; padding:10px 12px!important; display:grid!important; grid-template-columns:38px minmax(0,1fr) auto!important; align-items:center!important; gap:10px!important; border-radius:22px!important; text-align:left!important; font-size:22px!important; line-height:1!important; letter-spacing:-.035em!important; font-weight:900!important; color:rgba(246,250,255,.95)!important; background:radial-gradient(ellipse at 14% 0%,rgba(139,247,255,.14),transparent 34%),radial-gradient(ellipse at 98% 100%,rgba(151,110,255,.13),transparent 42%),linear-gradient(145deg,rgba(255,255,255,.115),rgba(255,255,255,.038) 58%,rgba(255,255,255,.022)),rgba(126,146,205,.115)!important; border:1px solid rgba(238,246,255,.24)!important; box-shadow:0 10px 22px rgba(0,0,0,.10),inset 0 .8px 0 rgba(255,255,255,.30),inset 0 -.8px 0 rgba(4,8,22,.08)!important; backdrop-filter:blur(18px) saturate(136%) contrast(1.02) brightness(1.04)!important; -webkit-backdrop-filter:blur(18px) saturate(136%) contrast(1.02) brightness(1.04)!important; overflow:hidden!important; transition:transform .18s cubic-bezier(.18,.86,.2,1),filter .18s ease,box-shadow .18s ease!important; }
      .model-picker-btn.hero-model-picker-btn::before { content:'AI'!important; width:38px!important; height:38px!important; display:grid!important; place-items:center!important; margin:0!important; border-radius:15px!important; font-size:14px!important; letter-spacing:-.035em!important; font-weight:950!important; color:rgba(255,255,255,.96)!important; background:radial-gradient(circle at 28% 18%,rgba(255,255,255,.44),transparent 44%),linear-gradient(135deg,rgba(121,235,255,.42),rgba(142,105,255,.44))!important; box-shadow:inset 0 .8px 0 rgba(255,255,255,.28),0 7px 14px rgba(0,0,0,.10)!important; opacity:1!important; }
      .model-picker-btn.hero-model-picker-btn::after { content:'模型'!important; justify-self:end!important; padding:7px 10px!important; border-radius:999px!important; font-size:11px!important; line-height:1!important; letter-spacing:.04em!important; font-weight:900!important; color:rgba(224,242,255,.72)!important; background:rgba(255,255,255,.075)!important; border:1px solid rgba(255,255,255,.13)!important; }
      .model-picker-btn.hero-model-picker-btn:active,.model-picker-btn.hero-model-picker-btn.liquid-pressed { transform:scale(.992)!important; filter:brightness(1.035) saturate(1.025)!important; }
      .model-picker-sheet-mask.open { display:grid!important; place-items:end center!important; background:rgba(4,8,20,.36)!important; backdrop-filter:blur(14px) saturate(112%)!important; -webkit-backdrop-filter:blur(14px) saturate(112%)!important; animation:modelMaskFadeIn .16s ease both!important; }
      .model-picker-sheet { width:min(94vw,500px)!important; margin:0 0 max(14px,env(safe-area-inset-bottom))!important; padding:15px!important; border-radius:30px!important; color:rgba(248,252,255,.98)!important; background:radial-gradient(ellipse at 18% 0%,rgba(139,247,255,.16),transparent 34%),radial-gradient(ellipse at 90% 96%,rgba(154,126,255,.20),transparent 40%),linear-gradient(145deg,rgba(255,255,255,.20),rgba(255,255,255,.075) 58%,rgba(255,255,255,.048)),rgba(40,48,84,.68)!important; border:1px solid rgba(255,255,255,.28)!important; box-shadow:0 28px 78px rgba(0,0,0,.40),inset 0 1px 0 rgba(255,255,255,.34)!important; backdrop-filter:blur(26px) saturate(160%) contrast(1.03)!important; -webkit-backdrop-filter:blur(26px) saturate(160%) contrast(1.03)!important; animation:modelSheetPopIn .25s cubic-bezier(.18,1.04,.24,1) both!important; }
      .model-picker-head strong{font-size:19px!important;letter-spacing:-.04em!important}.model-picker-head span{max-width:310px!important;line-height:1.42!important}.model-picker-list{gap:9px!important}.model-choice{min-height:64px!important;padding:12px!important;border-radius:20px!important;background:rgba(255,255,255,.085)!important;border:1px solid rgba(255,255,255,.16)!important;box-shadow:inset 0 .7px 0 rgba(255,255,255,.16)!important;transition:transform .18s cubic-bezier(.18,.86,.2,1),background .18s ease,border-color .18s ease,box-shadow .18s ease!important}.model-choice.active{background:radial-gradient(ellipse at 18% 0%,rgba(139,247,255,.20),transparent 38%),linear-gradient(135deg,rgba(99,226,255,.20),rgba(145,106,255,.18))!important;border-color:rgba(139,247,255,.38)!important;box-shadow:inset 0 .8px 0 rgba(255,255,255,.28),0 10px 22px rgba(65,88,188,.13)!important}.model-choice.is-selecting{animation:modelChoiceSelect .24s cubic-bezier(.18,1.06,.2,1) both!important}.model-choice-dot{width:12px!important;height:12px!important;border-width:2px!important;transition:transform .18s ease,background .18s ease,box-shadow .18s ease!important}.model-choice.active .model-choice-dot,.model-choice.is-selecting .model-choice-dot{transform:scale(1.14)!important;background:#8bf7ff!important;border-color:#8bf7ff!important;box-shadow:0 0 0 4px rgba(139,247,255,.12),0 0 20px rgba(139,247,255,.54)!important}
      @keyframes modelMaskFadeIn{from{opacity:0}to{opacity:1}} @keyframes modelSheetPopIn{from{transform:translateY(20px) scale(.98);opacity:.38}to{transform:none;opacity:1}} @keyframes modelChoiceSelect{0%{transform:scale(.988)}55%{transform:scale(1.012)}100%{transform:scale(1)}}
      @media (pointer:coarse),(max-width:768px){.model-picker-btn.hero-model-picker-btn{backdrop-filter:blur(15px) saturate(130%) contrast(1.02) brightness(1.03)!important;-webkit-backdrop-filter:blur(15px) saturate(130%) contrast(1.02) brightness(1.03)!important}}
    `;
    document.head.appendChild(style);
  }

  function syncModelHeroLabel() {
    const picker = window.AiLedgerModelPicker;
    const btn = document.querySelector('#chatModelPickerBtn');
    if (!picker || !btn) return;
    const current = picker.current?.() || 'auto';
    const model = (picker.models || []).find((item) => item.id === current);
    btn.dataset.modelId = current;
    btn.dataset.modelLabel = model?.label || btn.textContent || '自动';
    btn.dataset.modelHint = model?.hint || '';
    btn.textContent = model?.short || btn.textContent || '自动';
  }

  function renderFallbackModelSheet() {
    const picker = window.AiLedgerModelPicker;
    if (!picker?.models?.length) return null;
    let mask = document.querySelector('#modelPickerSheetMask');
    if (!mask) {
      mask = document.createElement('div');
      mask.id = 'modelPickerSheetMask';
      mask.className = 'model-picker-sheet-mask';
      document.body.appendChild(mask);
    }
    const selected = picker.current?.() || 'auto';
    mask.innerHTML = `<section class="model-picker-sheet" role="dialog" aria-modal="true" aria-label="选择云端模型"><div class="model-picker-head"><div><strong>选择云端模型</strong><span>自动模式会按可用性切换；手动选择时会优先使用指定模型。</span></div><button class="model-picker-close" type="button" data-model-picker-close>×</button></div><div class="model-picker-list">${picker.models.map((item) => `<button type="button" class="model-choice ${item.id === selected ? 'active' : ''}" data-model-choice="${escapeHtml(item.id)}"><span class="model-choice-dot"></span><span class="model-choice-text"><strong>${escapeHtml(item.label)}</strong><em>${escapeHtml(item.hint)}</em></span></button>`).join('')}</div></section>`;
    return mask;
  }

  function openModelPickerSheet() {
    const mask = renderFallbackModelSheet();
    if (!mask) return;
    mask.classList.add('open');
  }

  function installModelButtonFallback() {
    if (document.documentElement.dataset.staticModelButtonReady === 'true') return;
    document.documentElement.dataset.staticModelButtonReady = 'true';
    document.addEventListener('click', (event) => {
      const btn = event.target.closest?.('#chatModelPickerBtn');
      if (!btn) return;
      event.preventDefault();
      openModelPickerSheet();
    }, false);
  }

  function installModelHero() {
    installModelPickerPolishStyle();
    const strip = document.querySelector('.chat-summary-strip');
    const btn = document.querySelector('#chatModelPickerBtn');
    if (!strip || !btn) return;
    strip.classList.add('model-picker-hero-strip');
    strip.querySelectorAll('.summary-chip').forEach((node) => node.remove());
    btn.classList.add('hero-model-picker-btn');
    if (btn.parentElement !== strip) strip.appendChild(btn);
    syncModelHeroLabel();
  }

  function installModelChoiceAnimation() {
    if (document.documentElement.dataset.modelChoiceAnimationReady === 'true') return;
    document.documentElement.dataset.modelChoiceAnimationReady = 'true';
    document.addEventListener('click', (event) => {
      const mask = document.querySelector('#modelPickerSheetMask');
      if (event.target === mask || event.target.closest?.('#modelPickerSheetMask [data-model-picker-close]')) {
        mask?.classList.remove('open');
        return;
      }
      const choice = event.target.closest?.('#modelPickerSheetMask .model-choice[data-model-choice]');
      if (!choice) return;
      event.preventDefault();
      event.stopPropagation();
      event.stopImmediatePropagation();
      mask?.querySelectorAll('.model-choice').forEach((item) => item.classList.remove('active', 'is-selecting'));
      choice.classList.add('active', 'is-selecting');
      window.AiLedgerModelPicker?.set?.(choice.dataset.modelChoice);
      window.setTimeout(() => {
        syncModelHeroLabel();
        mask?.classList.remove('open');
      }, 220);
    }, true);
  }

  function bootModelPolish() {
    installModelHero();
    installModelButtonFallback();
    installModelChoiceAnimation();
  }

  window.NavigationExecutionCompat = {
    version: '2026-05-16-7-static-model-picker-no-flash',
    normalizeMode,
    normalizeNavigateParams,
    patchAll,
    removeUnstableVisualLayer,
    bootModelPolish,
  };

  removeUnstableVisualLayer();
  patchAll();
  bootModelPolish();
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => { removeUnstableVisualLayer(); patchAll(); bootModelPolish(); }, { once: true });
  } else {
    removeUnstableVisualLayer();
    bootModelPolish();
  }
  window.setTimeout(() => { removeUnstableVisualLayer(); patchAll(); bootModelPolish(); }, 300);
  window.setTimeout(() => { removeUnstableVisualLayer(); patchAll(); bootModelPolish(); }, 1200);
  window.setInterval(() => { removeUnstableVisualLayer(); bootModelPolish(); }, 900);
})();
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

  function installModelPickerPolishStyle() {
    if (document.getElementById(MODEL_POLISH_STYLE_ID)) return;
    const style = document.createElement('style');
    style.id = MODEL_POLISH_STYLE_ID;
    style.textContent = `
      .chat-summary-strip.model-picker-hero-strip {
        grid-template-columns: minmax(0, 1fr) !important;
        gap: 0 !important;
      }

      .chat-summary-strip.model-picker-hero-strip .summary-chip {
        display: none !important;
      }

      .model-picker-btn.hero-model-picker-btn {
        width: 100% !important;
        min-width: 0 !important;
        height: 96px !important;
        min-height: 96px !important;
        padding: 15px 18px !important;
        display: grid !important;
        grid-template-columns: 54px minmax(0, 1fr) auto !important;
        grid-template-rows: 1fr !important;
        align-items: center !important;
        gap: 14px !important;
        border-radius: 28px !important;
        text-align: left !important;
        font-size: 28px !important;
        line-height: 1 !important;
        letter-spacing: -.045em !important;
        font-weight: 950 !important;
        color: rgba(248, 252, 255, .98) !important;
        background:
          radial-gradient(ellipse at 16% 0%, rgba(139, 247, 255, .25), transparent 36%),
          radial-gradient(ellipse at 92% 100%, rgba(151, 110, 255, .22), transparent 42%),
          linear-gradient(145deg, rgba(255, 255, 255, .18), rgba(255, 255, 255, .060) 58%, rgba(255, 255, 255, .030)),
          rgba(126, 146, 205, .20) !important;
        border: 1px solid rgba(238, 246, 255, .32) !important;
        box-shadow:
          0 18px 38px rgba(0, 0, 0, .16),
          inset 0 1px 0 rgba(255, 255, 255, .42),
          inset 0 -1px 0 rgba(4, 8, 22, .10) !important;
        backdrop-filter: blur(22px) saturate(148%) contrast(1.03) brightness(1.06) !important;
        -webkit-backdrop-filter: blur(22px) saturate(148%) contrast(1.03) brightness(1.06) !important;
        overflow: hidden !important;
        isolation: isolate;
        transition: transform .20s cubic-bezier(.18,.86,.2,1), filter .18s ease, box-shadow .18s ease !important;
      }

      .model-picker-btn.hero-model-picker-btn::before {
        content: 'AI' !important;
        width: 54px !important;
        height: 54px !important;
        display: grid !important;
        place-items: center !important;
        margin: 0 !important;
        border-radius: 20px !important;
        font-size: 18px !important;
        letter-spacing: -.04em !important;
        font-weight: 950 !important;
        color: rgba(255, 255, 255, .98) !important;
        background:
          radial-gradient(circle at 28% 18%, rgba(255,255,255,.56), transparent 44%),
          linear-gradient(135deg, rgba(121,235,255,.62), rgba(142,105,255,.54)) !important;
        box-shadow: inset 0 .8px 0 rgba(255,255,255,.40), 0 10px 22px rgba(0,0,0,.16) !important;
        opacity: 1 !important;
      }

      .model-picker-btn.hero-model-picker-btn::after {
        content: '点击切换' !important;
        justify-self: end !important;
        padding: 8px 12px !important;
        border-radius: 999px !important;
        font-size: 12px !important;
        line-height: 1 !important;
        letter-spacing: .02em !important;
        font-weight: 900 !important;
        color: rgba(230, 247, 255, .88) !important;
        background: rgba(255, 255, 255, .12) !important;
        border: 1px solid rgba(255, 255, 255, .18) !important;
      }

      .model-picker-btn.hero-model-picker-btn:active,
      .model-picker-btn.hero-model-picker-btn.liquid-pressed {
        transform: scale(.988) !important;
        filter: brightness(1.04) saturate(1.04) !important;
      }

      .model-picker-sheet-mask.open {
        display: grid !important;
        place-items: end center !important;
        background: rgba(4, 8, 20, .40) !important;
        backdrop-filter: blur(18px) saturate(118%) !important;
        -webkit-backdrop-filter: blur(18px) saturate(118%) !important;
        animation: modelMaskFadeIn .18s ease both !important;
      }

      .model-picker-sheet {
        width: min(94vw, 500px) !important;
        margin: 0 0 max(14px, env(safe-area-inset-bottom)) !important;
        padding: 15px !important;
        border-radius: 32px !important;
        color: rgba(248, 252, 255, .98) !important;
        background:
          radial-gradient(ellipse at 18% 0%, rgba(139, 247, 255, .20), transparent 34%),
          radial-gradient(ellipse at 90% 96%, rgba(154, 126, 255, .24), transparent 40%),
          linear-gradient(145deg, rgba(255,255,255,.22), rgba(255,255,255,.082) 58%, rgba(255,255,255,.052)),
          rgba(40, 48, 84, .68) !important;
        border: 1px solid rgba(255,255,255,.30) !important;
        box-shadow: 0 30px 88px rgba(0,0,0,.42), inset 0 1px 0 rgba(255,255,255,.36) !important;
        backdrop-filter: blur(28px) saturate(170%) contrast(1.04) !important;
        -webkit-backdrop-filter: blur(28px) saturate(170%) contrast(1.04) !important;
        animation: modelSheetPopIn .28s cubic-bezier(.18,1.08,.24,1) both !important;
      }

      .model-picker-head strong {
        font-size: 20px !important;
        letter-spacing: -.04em !important;
      }

      .model-picker-head span {
        max-width: 310px !important;
        line-height: 1.42 !important;
      }

      .model-picker-list {
        gap: 10px !important;
      }

      .model-choice {
        min-height: 68px !important;
        padding: 13px !important;
        border-radius: 22px !important;
        background: rgba(255,255,255,.090) !important;
        border: 1px solid rgba(255,255,255,.18) !important;
        box-shadow: inset 0 .7px 0 rgba(255,255,255,.18) !important;
        transition: transform .18s cubic-bezier(.18,.86,.2,1), background .18s ease, border-color .18s ease, box-shadow .18s ease !important;
      }

      .model-choice.active {
        background:
          radial-gradient(ellipse at 18% 0%, rgba(139, 247, 255, .25), transparent 38%),
          linear-gradient(135deg, rgba(99,226,255,.24), rgba(145,106,255,.22)) !important;
        border-color: rgba(139, 247, 255, .46) !important;
        box-shadow: inset 0 .8px 0 rgba(255,255,255,.32), 0 12px 28px rgba(65, 88, 188, .16) !important;
      }

      .model-choice.is-selecting {
        animation: modelChoiceSelect .28s cubic-bezier(.18,1.10,.2,1) both !important;
      }

      .model-choice-dot {
        width: 13px !important;
        height: 13px !important;
        border-width: 2px !important;
        transition: transform .18s ease, background .18s ease, box-shadow .18s ease !important;
      }

      .model-choice.active .model-choice-dot,
      .model-choice.is-selecting .model-choice-dot {
        transform: scale(1.18) !important;
        background: #8bf7ff !important;
        border-color: #8bf7ff !important;
        box-shadow: 0 0 0 5px rgba(139,247,255,.14), 0 0 24px rgba(139,247,255,.62) !important;
      }

      @keyframes modelMaskFadeIn { from { opacity: 0; } to { opacity: 1; } }
      @keyframes modelSheetPopIn { from { transform: translateY(24px) scale(.975); opacity: .36; } to { transform: none; opacity: 1; } }
      @keyframes modelChoiceSelect { 0% { transform: scale(.985); } 55% { transform: scale(1.018); } 100% { transform: scale(1); } }

      @media (pointer: coarse), (max-width: 768px) {
        .model-picker-btn.hero-model-picker-btn {
          backdrop-filter: blur(18px) saturate(138%) contrast(1.02) brightness(1.04) !important;
          -webkit-backdrop-filter: blur(18px) saturate(138%) contrast(1.02) brightness(1.04) !important;
        }
      }
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
      const choice = event.target.closest?.('#modelPickerSheetMask .model-choice[data-model-choice]');
      if (!choice) return;
      event.preventDefault();
      event.stopPropagation();
      event.stopImmediatePropagation();
      const mask = document.querySelector('#modelPickerSheetMask');
      mask?.querySelectorAll('.model-choice').forEach((item) => item.classList.remove('active', 'is-selecting'));
      choice.classList.add('active', 'is-selecting');
      window.AiLedgerModelPicker?.set?.(choice.dataset.modelChoice);
      window.setTimeout(() => {
        syncModelHeroLabel();
        mask?.classList.remove('open');
      }, 240);
    }, true);
  }

  function bootModelPolish() {
    installModelHero();
    installModelChoiceAnimation();
  }

  window.NavigationExecutionCompat = {
    version: '2026-05-16-5-model-picker-polish',
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
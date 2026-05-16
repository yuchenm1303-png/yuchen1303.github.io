(() => {
  const PATCH_FLAG = '__navigationExecutionCompatPatched';
  const HANDLER_FLAG = '__navigationExecutionCompatClickHandler';
  const CHAT_KEY = 'ai-ledger-chat-v2';
  const VISUAL_STYLE_ID = 'visual-design-director-style';

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

  function installVisualDesignLayer() {
    if (document.getElementById(VISUAL_STYLE_ID)) return;

    const style = document.createElement('style');
    style.id = VISUAL_STYLE_ID;
    style.textContent = `
      :root {
        --designer-shell-w: 492px;
        --designer-gap: 12px;
        --designer-card-radius: 26px;
        --designer-control-radius: 18px;
        --designer-glass-fill: rgba(248, 252, 255, .036);
        --designer-glass-fill-soft: rgba(248, 252, 255, .026);
        --designer-glass-line: rgba(248, 252, 255, .22);
        --designer-glass-edge: rgba(255, 255, 255, .42);
        --designer-shadow: 0 18px 42px rgba(0, 0, 0, .20), inset 0 .8px 0 rgba(255, 255, 255, .30), inset 0 -.8px 0 rgba(5, 10, 24, .10);
        --designer-shadow-soft: 0 10px 26px rgba(0, 0, 0, .14), inset 0 .7px 0 rgba(255, 255, 255, .24);
        --designer-accent-a: rgba(115, 231, 255, .92);
        --designer-accent-b: rgba(154, 126, 255, .84);
        --designer-ease: cubic-bezier(.18, .86, .22, 1);
      }

      body.visual-design-v1 {
        background-color: #070a18 !important;
        letter-spacing: -.01em;
      }

      body.visual-design-v1 .scene-backdrop {
        transform: scale(1.035) translateZ(0) !important;
        background:
          linear-gradient(180deg, rgba(7, 10, 24, .04), rgba(7, 10, 24, .22) 68%, rgba(7, 10, 24, .36)),
          radial-gradient(ellipse at 18% 8%, rgba(128, 184, 255, .20), transparent 30%),
          radial-gradient(ellipse at 78% 10%, rgba(181, 125, 255, .16), transparent 28%),
          radial-gradient(ellipse at 72% 84%, rgba(85, 228, 255, .13), transparent 32%),
          var(--scene-overlay),
          var(--scene-image),
          var(--scene-base) !important;
      }

      body.visual-design-v1[data-bg="jade"] .scene-backdrop {
        background:
          linear-gradient(180deg, rgba(2, 16, 24, .03), rgba(2, 16, 24, .24) 72%),
          radial-gradient(ellipse at 16% 10%, rgba(122, 255, 228, .15), transparent 30%),
          radial-gradient(ellipse at 84% 70%, rgba(58, 184, 195, .14), transparent 34%),
          var(--scene-overlay), var(--scene-image), var(--scene-base) !important;
      }

      body.visual-design-v1[data-bg="sunset"] .scene-backdrop {
        background:
          linear-gradient(180deg, rgba(18, 5, 22, .04), rgba(18, 5, 22, .28) 72%),
          radial-gradient(ellipse at 20% 8%, rgba(255, 202, 151, .15), transparent 30%),
          radial-gradient(ellipse at 86% 70%, rgba(255, 104, 160, .13), transparent 34%),
          var(--scene-overlay), var(--scene-image), var(--scene-base) !important;
      }

      body.visual-design-v1[data-bg="dawn"] {
        --designer-glass-fill: rgba(255, 255, 255, .34);
        --designer-glass-fill-soft: rgba(255, 255, 255, .24);
        --designer-glass-line: rgba(255, 255, 255, .54);
        --designer-glass-edge: rgba(255, 255, 255, .76);
        --designer-shadow: 0 18px 42px rgba(67, 90, 122, .11), inset 0 .8px 0 rgba(255, 255, 255, .76), inset 0 -.8px 0 rgba(116, 136, 162, .11);
        --designer-shadow-soft: 0 10px 24px rgba(67, 90, 122, .08), inset 0 .7px 0 rgba(255, 255, 255, .66);
      }

      body.visual-design-v1:not(.quick-ai-entry) .app-shell {
        width: min(100%, var(--designer-shell-w)) !important;
        max-width: var(--designer-shell-w) !important;
        padding: max(18px, env(safe-area-inset-top)) 14px calc(150px + env(safe-area-inset-bottom)) !important;
      }

      body.visual-design-v1 .view.active {
        display: grid !important;
        gap: var(--designer-gap) !important;
        align-content: start !important;
      }

      body.visual-design-v1 .page-header {
        margin: 0 0 2px !important;
        padding: 2px 3px 0 !important;
      }

      body.visual-design-v1 .page-header h1 {
        font-size: clamp(31px, 8vw, 38px) !important;
        line-height: .98 !important;
        letter-spacing: -.065em !important;
        font-weight: 920 !important;
        text-wrap: balance;
      }

      body.visual-design-v1 .eyebrow {
        margin-bottom: 7px !important;
        font-size: 12px !important;
        letter-spacing: .10em !important;
        text-transform: uppercase;
        color: rgba(143, 232, 255, .92) !important;
      }

      body.visual-design-v1 .subtext,
      body.visual-design-v1 .helper-text,
      body.visual-design-v1 .soft-label,
      body.visual-design-v1 .field-label {
        color: var(--muted) !important;
        font-size: 13px !important;
        line-height: 1.48 !important;
      }

      body.visual-design-v1 .glass-card,
      body.visual-design-v1 .chat-shell,
      body.visual-design-v1 .summary-card,
      body.visual-design-v1 .metric-card,
      body.visual-design-v1 .chart-card,
      body.visual-design-v1 .tool-card,
      body.visual-design-v1 .auth-sheet,
      body.visual-design-v1 .add-sheet,
      body.visual-design-v1 .settings-group-sheet,
      body.visual-design-v1 .appearance-detail-panel,
      body.visual-design-v1 .detail-panel,
      body.visual-design-v1 .tools-panel-card {
        position: relative !important;
        overflow: hidden !important;
        margin-bottom: 0 !important;
        border-radius: var(--designer-card-radius) !important;
        border: 1px solid var(--designer-glass-line) !important;
        background:
          linear-gradient(145deg, rgba(255, 255, 255, .096), rgba(255, 255, 255, .018) 45%, rgba(255, 255, 255, .026) 75%, rgba(0, 0, 0, .018)),
          var(--designer-glass-fill) !important;
        box-shadow: var(--designer-shadow) !important;
        backdrop-filter: blur(26px) saturate(142%) contrast(1.04) brightness(1.07) !important;
        -webkit-backdrop-filter: blur(26px) saturate(142%) contrast(1.04) brightness(1.07) !important;
        transform: translateZ(0);
      }

      body.visual-design-v1 .glass-card::before,
      body.visual-design-v1 .chat-shell::before,
      body.visual-design-v1 .summary-card::before,
      body.visual-design-v1 .tool-card::before,
      body.visual-design-v1 .bottom-nav::before,
      body.visual-design-v1 .auth-sheet::before,
      body.visual-design-v1 .add-sheet::before,
      body.visual-design-v1 .settings-group-sheet::before,
      body.visual-design-v1 .appearance-detail-panel::before,
      body.visual-design-v1 .detail-panel::before,
      body.visual-design-v1 .tools-panel-card::before {
        content: "" !important;
        position: absolute !important;
        inset: 0 !important;
        pointer-events: none !important;
        border-radius: inherit !important;
        background:
          radial-gradient(ellipse at 16% -10%, rgba(255, 255, 255, .26), transparent 34%),
          radial-gradient(ellipse at 86% 110%, rgba(115, 231, 255, .080), transparent 38%),
          linear-gradient(180deg, rgba(255, 255, 255, .135), transparent 32%),
          linear-gradient(90deg, rgba(255,255,255,.080), transparent 22%, transparent 78%, rgba(190, 222, 255, .060)) !important;
        opacity: .62 !important;
      }

      body.visual-design-v1 .glass-card::after,
      body.visual-design-v1 .chat-shell::after,
      body.visual-design-v1 .summary-card::after,
      body.visual-design-v1 .tool-card::after,
      body.visual-design-v1 .bottom-nav::after,
      body.visual-design-v1 .auth-sheet::after,
      body.visual-design-v1 .add-sheet::after,
      body.visual-design-v1 .settings-group-sheet::after,
      body.visual-design-v1 .appearance-detail-panel::after,
      body.visual-design-v1 .detail-panel::after,
      body.visual-design-v1 .tools-panel-card::after {
        content: "" !important;
        position: absolute !important;
        inset: .75px !important;
        border-radius: inherit !important;
        border: 1px solid rgba(255, 255, 255, .060) !important;
        pointer-events: none !important;
      }

      body.visual-design-v1 .glass-card > *,
      body.visual-design-v1 .chat-shell > *,
      body.visual-design-v1 .summary-card > *,
      body.visual-design-v1 .tool-card > *,
      body.visual-design-v1 .bottom-nav > *,
      body.visual-design-v1 .auth-sheet > *,
      body.visual-design-v1 .add-sheet > *,
      body.visual-design-v1 .settings-group-sheet > *,
      body.visual-design-v1 .appearance-detail-panel > *,
      body.visual-design-v1 .detail-panel > *,
      body.visual-design-v1 .tools-panel-card > * {
        position: relative;
        z-index: 1;
      }

      body.visual-design-v1 #view-ai .chat-header {
        display: grid !important;
        grid-template-columns: minmax(0, 1fr) auto !important;
        align-items: end !important;
        gap: 12px !important;
      }

      body.visual-design-v1 .chat-header-actions {
        justify-items: end !important;
        gap: 7px !important;
      }

      body.visual-design-v1 .ai-badge,
      body.visual-design-v1 .mini-ghost-btn,
      body.visual-design-v1 .tag-btn,
      body.visual-design-v1 .range-chip,
      body.visual-design-v1 .ghost-btn,
      body.visual-design-v1 .primary-btn,
      body.visual-design-v1 .danger-btn,
      body.visual-design-v1 .icon-btn,
      body.visual-design-v1 .delete-btn,
      body.visual-design-v1 .tools-back,
      body.visual-design-v1 .summary-chip,
      body.visual-design-v1 .record-item,
      body.visual-design-v1 .draft-card,
      body.visual-design-v1 .draft-item,
      body.visual-design-v1 .account-row,
      body.visual-design-v1 .summary-box,
      body.visual-design-v1 .budget-pill,
      body.visual-design-v1 textarea,
      body.visual-design-v1 input,
      body.visual-design-v1 select {
        border: 1px solid rgba(248, 252, 255, .18) !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.060), rgba(255,255,255,.010) 56%, rgba(0,0,0,.010)),
          var(--designer-glass-fill-soft) !important;
        box-shadow: var(--designer-shadow-soft) !important;
        backdrop-filter: blur(16px) saturate(136%) contrast(1.03) brightness(1.06) !important;
        -webkit-backdrop-filter: blur(16px) saturate(136%) contrast(1.03) brightness(1.06) !important;
      }

      body.visual-design-v1 .chat-shell {
        grid-template-rows: auto minmax(275px, 1fr) auto auto auto !important;
        gap: 11px !important;
        min-height: min(660px, calc(var(--app-visual-vh, 100vh) - 150px)) !important;
        padding: 14px !important;
        overflow: hidden !important;
      }

      body.visual-design-v1 .chat-summary-strip {
        gap: 10px !important;
      }

      body.visual-design-v1 .summary-chip {
        min-height: 68px !important;
        padding: 12px 13px !important;
        border-radius: 20px !important;
      }

      body.visual-design-v1 .summary-chip span {
        font-size: 11px !important;
        letter-spacing: .02em !important;
      }

      body.visual-design-v1 .summary-chip strong {
        font-size: 19px !important;
        letter-spacing: -.025em !important;
      }

      body.visual-design-v1 .chat-messages {
        min-height: 275px !important;
        gap: 11px !important;
        padding: 2px 2px 8px !important;
        mask-image: linear-gradient(to bottom, transparent 0, #000 12px, #000 calc(100% - 12px), transparent 100%);
        -webkit-mask-image: linear-gradient(to bottom, transparent 0, #000 12px, #000 calc(100% - 12px), transparent 100%);
      }

      body.visual-design-v1 .chat-bubble {
        max-width: 88% !important;
        padding: 11px 13px !important;
        border-radius: 20px !important;
        font-size: 14px !important;
        line-height: 1.52 !important;
      }

      body.visual-design-v1 .chat-row.user .chat-bubble {
        border-bottom-right-radius: 8px !important;
        background:
          linear-gradient(135deg, rgba(70, 122, 255, .88), rgba(133, 82, 255, .82)) !important;
        box-shadow: 0 12px 24px rgba(55, 62, 170, .26), inset 0 .7px 0 rgba(255,255,255,.28) !important;
      }

      body.visual-design-v1 .chat-row.assistant .chat-bubble {
        border-bottom-left-radius: 8px !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.072), rgba(255,255,255,.012) 56%, rgba(255,255,255,.018)),
          rgba(248, 252, 255, .026) !important;
      }

      body.visual-design-v1 .quick-tags.chat-tags {
        gap: 8px !important;
        overflow-x: auto !important;
        flex-wrap: nowrap !important;
        padding-bottom: 2px !important;
        scrollbar-width: none;
      }

      body.visual-design-v1 .quick-tags.chat-tags::-webkit-scrollbar {
        display: none;
      }

      body.visual-design-v1 .tag-btn,
      body.visual-design-v1 .range-chip {
        min-height: 36px !important;
        padding: 0 13px !important;
        border-radius: 999px !important;
        font-size: 13px !important;
      }

      body.visual-design-v1 .chat-composer {
        min-height: 62px !important;
        padding: 8px !important;
        border-radius: 28px !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.090), rgba(255,255,255,.020) 58%, rgba(0,0,0,.012)),
          rgba(248, 252, 255, .030) !important;
        border: 1px solid rgba(248, 252, 255, .20) !important;
        box-shadow: var(--designer-shadow-soft) !important;
      }

      body.visual-design-v1 .chat-composer textarea,
      body.visual-design-v1 #aiInput {
        min-height: 44px !important;
        padding: 11px 12px !important;
        background: transparent !important;
        border: 0 !important;
        box-shadow: none !important;
      }

      body.visual-design-v1 .send-btn,
      body.visual-design-v1 #aiAddBtn {
        width: 46px !important;
        height: 46px !important;
        min-width: 46px !important;
        min-height: 46px !important;
        border-radius: 19px !important;
        background:
          radial-gradient(circle at 25% 10%, rgba(255,255,255,.38), transparent 45%),
          linear-gradient(135deg, var(--designer-accent-a), var(--designer-accent-b)) !important;
        color: #fff !important;
        box-shadow: 0 12px 22px rgba(66, 88, 185, .30), inset 0 .8px 0 rgba(255,255,255,.40) !important;
      }

      body.visual-design-v1 .tools-grid {
        display: grid !important;
        grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
        gap: 11px !important;
      }

      body.visual-design-v1 .tool-card {
        display: grid !important;
        align-content: space-between !important;
        min-height: 128px !important;
        padding: 14px !important;
        text-align: left !important;
      }

      body.visual-design-v1 .tool-icon,
      body.visual-design-v1 .settings-group-icon {
        width: 42px !important;
        height: 42px !important;
        border-radius: 17px !important;
        display: grid !important;
        place-items: center !important;
        color: #fff !important;
        background:
          radial-gradient(circle at 24% 16%, rgba(255,255,255,.42), transparent 44%),
          linear-gradient(135deg, rgba(115,231,255,.58), rgba(154,126,255,.50)) !important;
        box-shadow: inset 0 .8px 0 rgba(255,255,255,.34), 0 10px 20px rgba(0,0,0,.12) !important;
      }

      body.visual-design-v1 .tool-card h3 {
        margin: 10px 0 4px !important;
        font-size: 16px !important;
        line-height: 1.2 !important;
        letter-spacing: -.02em !important;
      }

      body.visual-design-v1 .tool-card p {
        margin: 0 !important;
        color: var(--muted) !important;
        font-size: 12px !important;
        line-height: 1.42 !important;
      }

      body.visual-design-v1 .summary-card {
        padding: 16px !important;
        background:
          radial-gradient(ellipse at 14% 0%, rgba(115, 231, 255, .16), transparent 36%),
          radial-gradient(ellipse at 86% 86%, rgba(154, 126, 255, .14), transparent 40%),
          linear-gradient(145deg, rgba(255,255,255,.082), rgba(255,255,255,.016) 56%, rgba(0,0,0,.018)),
          rgba(248, 252, 255, .032) !important;
      }

      body.visual-design-v1 .big-money {
        font-size: clamp(34px, 9vw, 42px) !important;
        letter-spacing: -.065em !important;
      }

      body.visual-design-v1 .summary-box,
      body.visual-design-v1 .metric-card {
        border-radius: 21px !important;
      }

      body.visual-design-v1 .metric-grid,
      body.visual-design-v1 .summary-grid,
      body.visual-design-v1 .form-grid,
      body.visual-design-v1 .mini-grid {
        gap: 11px !important;
      }

      body.visual-design-v1 .chart-wrap {
        height: 200px !important;
      }

      body.visual-design-v1 .donut-wrap {
        height: 236px !important;
      }

      body.visual-design-v1 .background-options {
        grid-template-columns: repeat(4, minmax(0, 1fr)) !important;
        gap: 9px !important;
      }

      body.visual-design-v1 .bg-preview {
        border-radius: 18px !important;
        box-shadow: 0 12px 24px rgba(0,0,0,.16), inset 0 .8px 0 rgba(255,255,255,.36) !important;
      }

      body.visual-design-v1 .bottom-nav {
        width: min(88vw, 430px) !important;
        min-height: 72px !important;
        padding: 7px !important;
        border-radius: 28px !important;
        bottom: calc(12px + env(safe-area-inset-bottom)) !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.104), rgba(255,255,255,.020) 54%, rgba(0,0,0,.018)),
          rgba(248,252,255,.036) !important;
        border: 1px solid rgba(248,252,255,.26) !important;
        box-shadow: 0 18px 42px rgba(0,0,0,.24), inset 0 .8px 0 rgba(255,255,255,.36), inset 0 -.8px 0 rgba(5,10,24,.12) !important;
        backdrop-filter: blur(26px) saturate(142%) contrast(1.04) brightness(1.07) !important;
        -webkit-backdrop-filter: blur(26px) saturate(142%) contrast(1.04) brightness(1.07) !important;
      }

      body.visual-design-v1 .bottom-nav .nav-btn {
        min-height: 58px !important;
        height: 58px !important;
        border-radius: 22px !important;
        color: rgba(230, 238, 255, .64) !important;
        background: transparent !important;
      }

      body.visual-design-v1 .bottom-nav .nav-btn.active {
        color: #fff !important;
        background: transparent !important;
        border-color: transparent !important;
        box-shadow: none !important;
      }

      body.visual-design-v1 .liquid-nav-indicator {
        border-radius: 22px !important;
        background:
          radial-gradient(circle at 24% 12%, rgba(255,255,255,.58), rgba(255,255,255,.18) 34%, transparent 66%),
          linear-gradient(135deg, rgba(255,255,255,.20), rgba(255,255,255,.046) 48%, rgba(126,189,255,.11)) !important;
        box-shadow: inset 0 .8px 0 rgba(255,255,255,.52), inset 0 -.8px 0 rgba(0,0,0,.08), 0 10px 22px rgba(0,0,0,.13) !important;
      }

      body.visual-design-v1 .record-item {
        border-radius: 19px !important;
        padding: 12px 13px !important;
      }

      body.visual-design-v1 .auth-overlay,
      body.visual-design-v1 .detail-overlay,
      body.visual-design-v1 .settings-group-detail,
      body.visual-design-v1 .appearance-detail-overlay {
        background: rgba(4, 7, 20, .42) !important;
        backdrop-filter: blur(22px) saturate(112%) !important;
        -webkit-backdrop-filter: blur(22px) saturate(112%) !important;
      }

      body.visual-design-v1.quick-ai-entry .app-shell {
        width: min(100%, 520px) !important;
        padding: 0 !important;
      }

      body.visual-design-v1.quick-ai-entry .page-header.chat-header {
        padding: 13px 14px 0 !important;
      }

      body.visual-design-v1.quick-ai-entry .chat-shell {
        min-height: 0 !important;
        height: 100% !important;
        border-radius: 30px !important;
      }

      @media (max-width: 430px) {
        body.visual-design-v1:not(.quick-ai-entry) .app-shell {
          padding-left: 12px !important;
          padding-right: 12px !important;
        }

        body.visual-design-v1 .page-header h1 {
          font-size: 33px !important;
        }

        body.visual-design-v1 .chat-shell {
          min-height: min(630px, calc(var(--app-visual-vh, 100vh) - 150px)) !important;
        }

        body.visual-design-v1 .tools-grid {
          gap: 10px !important;
        }

        body.visual-design-v1 .tool-card {
          min-height: 122px !important;
          padding: 13px !important;
        }
      }

      @media (max-width: 390px) {
        body.visual-design-v1 .background-options {
          grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
        }

        body.visual-design-v1 .bottom-nav {
          width: min(91vw, 410px) !important;
        }
      }

      @media (pointer: coarse), (max-width: 768px) {
        body.visual-design-v1 .summary-chip,
        body.visual-design-v1 .record-item,
        body.visual-design-v1 .draft-card,
        body.visual-design-v1 .draft-item,
        body.visual-design-v1 .account-row,
        body.visual-design-v1 .summary-box,
        body.visual-design-v1 .budget-pill,
        body.visual-design-v1 textarea,
        body.visual-design-v1 input,
        body.visual-design-v1 select,
        body.visual-design-v1 .tag-btn,
        body.visual-design-v1 .range-chip,
        body.visual-design-v1 .ghost-btn,
        body.visual-design-v1 .mini-ghost-btn,
        body.visual-design-v1 .ai-badge {
          backdrop-filter: none !important;
          -webkit-backdrop-filter: none !important;
        }
      }
    `;
    document.head.appendChild(style);
    document.body?.classList.add('visual-design-v1');
  }

  function bootVisualDesignLayer() {
    installVisualDesignLayer();
    document.body?.classList.add('visual-design-v1');
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
    version: '2026-05-16-3-visual-design',
    normalizeMode,
    normalizeNavigateParams,
    buildNavigationUri,
    launchByUri,
    patchAll,
    installVisualDesignLayer: bootVisualDesignLayer,
  };

  bootVisualDesignLayer();
  patchAll();
  installNavigateClickHandler();
  window.addEventListener('DOMContentLoaded', () => { bootVisualDesignLayer(); patchAll(); installNavigateClickHandler(); });
  window.setTimeout(() => { bootVisualDesignLayer(); patchAll(); installNavigateClickHandler(); }, 300);
  window.setTimeout(() => { bootVisualDesignLayer(); patchAll(); installNavigateClickHandler(); }, 1200);
})();
(() => {
  'use strict';

  const STYLE_ID = 'glass-stability-style';
  const PERF_KEY = 'ai-assistant-performance-mode-v1';
  const PERF_VERSION_KEY = 'ai-assistant-performance-mode-version-v1';
  const PERF_VERSION = '2';
  const VALID_MODES = ['auto', 'lite', 'balanced', 'quality', 'full'];
  let interactionTimer = 0;
  let scrollTimer = 0;
  let resizeTimer = 0;

  function readSavedMode() {
    try {
      const savedVersion = localStorage.getItem(PERF_VERSION_KEY);
      let mode = localStorage.getItem(PERF_KEY) || 'auto';
      mode = VALID_MODES.includes(mode) ? mode : 'auto';
      if (savedVersion !== PERF_VERSION) {
        if (mode === 'full') mode = 'balanced';
        localStorage.setItem(PERF_KEY, mode);
        localStorage.setItem(PERF_VERSION_KEY, PERF_VERSION);
      }
      return mode;
    } catch {
      return 'auto';
    }
  }

  function deviceInfo() {
    const ua = navigator.userAgent || '';
    const isAndroid = /Android/i.test(ua);
    const isIOS = /iPad|iPhone|iPod/i.test(ua) || (/Macintosh/i.test(ua) && navigator.maxTouchPoints > 1);
    const memory = Number(navigator.deviceMemory || 0);
    const cores = Number(navigator.hardwareConcurrency || 0);
    const minWidth = Math.min(window.innerWidth || 999, window.innerHeight || 999);
    const touch = window.matchMedia?.('(pointer: coarse)')?.matches || false;
    const webView = isAndroid && /; wv\)|Version\/\d+\.\d+ Chrome\//i.test(ua);
    return { ua, isAndroid, isIOS, webView, memory, cores, minWidth, touch };
  }

  function lowPowerDevice(info = deviceInfo()) {
    return info.isAndroid && (
      info.webView ||
      (info.memory && info.memory <= 4) ||
      (info.cores && info.cores <= 4) ||
      info.minWidth <= 480 ||
      info.touch
    );
  }

  function resolveMode(selected = readSavedMode()) {
    if (selected === 'lite' || selected === 'balanced' || selected === 'quality' || selected === 'full') return selected;
    return lowPowerDevice() ? 'lite' : 'balanced';
  }

  function installStyle() {
    document.getElementById(STYLE_ID)?.remove();
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      :root {
        --assistant-glass-panel-alpha: .016;
        --assistant-glass-control-alpha: .024;
        --assistant-glass-nav-alpha: .028;
        --assistant-glass-selected-alpha: .048;
        --assistant-glass-preview-alpha: .030;
        --assistant-glass-panel-blur: 12px;
        --assistant-glass-control-blur: 0px;
        --assistant-glass-nav-blur: 10px;
        --assistant-glass-edge: rgba(255,255,255,.18);
        --assistant-glass-edge-strong: rgba(255,255,255,.34);
        --assistant-glass-edge-cool: rgba(170,216,255,.10);
        --assistant-glass-shadow: 0 18px 42px rgba(0,0,0,.18), inset 0 .55px 0 rgba(255,255,255,.28), inset 0 -.7px 0 rgba(0,0,0,.10);
      }

      body.stable-glass-rendering {
        text-rendering: optimizeLegibility;
        -webkit-font-smoothing: antialiased;
      }

      body.stable-glass-rendering .reveal {
        opacity: 1 !important;
        visibility: visible !important;
        animation-duration: 260ms !important;
      }

      body.stable-glass-rendering :where(.chat-row,.record-item,.draft-card,.draft-item,.tool-card,.settings-group-card,.tools-panel-card,.appearance-plus-card,.summary-card,.metric-card,.chart-card) {
        content-visibility: visible !important;
        contain-intrinsic-size: auto !important;
        backface-visibility: hidden;
      }

      body.stable-glass-rendering :where(.chat-messages,.record-list,.tools-panel,.settings-group-content) {
        -webkit-overflow-scrolling: touch;
        overscroll-behavior: contain;
      }

      body.stable-glass-rendering :where(.glass-card,.chat-shell,.summary-card,.metric-card,.chart-card,.tool-card,.auth-sheet,.mobile-command-card,.tools-panel-card,.account-row,.appearance-detail-panel,.settings-group-sheet,.settings-group-card) {
        position: relative;
        overflow: hidden;
        isolation: isolate;
        border: 1px solid var(--assistant-glass-edge) !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.040), rgba(255,255,255,.004) 46%, rgba(0,0,0,.014)),
          rgba(255,255,255,var(--assistant-glass-panel-alpha)) !important;
        box-shadow: var(--assistant-glass-shadow) !important;
        backdrop-filter: blur(var(--assistant-glass-panel-blur)) saturate(132%) brightness(1.055) contrast(1.025) !important;
        -webkit-backdrop-filter: blur(var(--assistant-glass-panel-blur)) saturate(132%) brightness(1.055) contrast(1.025) !important;
      }

      body.stable-glass-rendering :where(.summary-chip,.record-item,.draft-card,.draft-item,.chat-composer,textarea,input,select,.tag-btn,.range-chip,.ghost-btn,.mini-ghost-btn,.summary-box,.budget-pill,.auth-tab,.icon-btn,.delete-btn,.chat-row.assistant .chat-bubble,.tools-back,.account-pill,.appearance-preview,.performance-mode-option) {
        position: relative;
        overflow: hidden;
        border-color: rgba(255,255,255,.15) !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.052), rgba(255,255,255,.007) 52%, rgba(0,0,0,.010)),
          rgba(255,255,255,var(--assistant-glass-control-alpha)) !important;
        box-shadow: inset 0 .55px 0 rgba(255,255,255,.22), inset 0 -.55px 0 rgba(0,0,0,.075) !important;
        backdrop-filter: blur(var(--assistant-glass-control-blur)) saturate(116%) brightness(1.04) !important;
        -webkit-backdrop-filter: blur(var(--assistant-glass-control-blur)) saturate(116%) brightness(1.04) !important;
      }

      body.stable-glass-rendering .bottom-nav {
        border: 1px solid rgba(255,255,255,.18) !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.058), rgba(255,255,255,.010) 50%, rgba(0,0,0,.026)),
          rgba(255,255,255,var(--assistant-glass-nav-alpha)) !important;
        box-shadow: 0 16px 36px rgba(0,0,0,.20), inset 0 .6px 0 rgba(255,255,255,.30), inset 0 -.7px 0 rgba(0,0,0,.10) !important;
        backdrop-filter: blur(var(--assistant-glass-nav-blur)) saturate(126%) brightness(1.052) contrast(1.02) !important;
        -webkit-backdrop-filter: blur(var(--assistant-glass-nav-blur)) saturate(126%) brightness(1.052) contrast(1.02) !important;
      }

      body.stable-glass-rendering :where(.primary-btn,.send-btn,.confirm-btn,.range-chip.active,.auth-tab.active) {
        border: 1px solid rgba(255,255,255,.17) !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.070), rgba(255,255,255,.012) 54%, rgba(220,234,255,.028)),
          rgba(255,255,255,var(--assistant-glass-selected-alpha)) !important;
        box-shadow: inset 0 .65px 0 rgba(255,255,255,.25), inset 0 -.55px 0 rgba(0,0,0,.08), 0 10px 22px rgba(0,0,0,.12) !important;
        backdrop-filter: blur(var(--assistant-glass-control-blur)) saturate(116%) brightness(1.045) !important;
        -webkit-backdrop-filter: blur(var(--assistant-glass-control-blur)) saturate(116%) brightness(1.045) !important;
      }

      body.stable-glass-rendering .bottom-nav .nav-btn.active {
        background: transparent !important;
        border-color: transparent !important;
        box-shadow: none !important;
      }

      body.stable-glass-rendering .auth-overlay {
        background: rgba(4,8,20,.22) !important;
        backdrop-filter: none !important;
        -webkit-backdrop-filter: none !important;
        transition: opacity 140ms ease, visibility 140ms ease !important;
      }

      body.stable-glass-rendering .auth-overlay .auth-sheet {
        transform: translate3d(0,14px,0) scale(.992) !important;
        opacity: .001 !important;
        transition: transform 170ms cubic-bezier(.18,.86,.22,1), opacity 120ms ease !important;
        will-change: transform, opacity;
      }

      body.stable-glass-rendering .auth-overlay.open .auth-sheet {
        transform: translate3d(0,0,0) scale(1) !important;
        opacity: 1 !important;
      }

      body.stable-glass-rendering :where(.glass-card,.chat-shell,.summary-card,.metric-card,.chart-card,.tool-card,.auth-sheet,.mobile-command-card,.tools-panel-card,.account-row,.settings-group-sheet,.settings-group-card,.bottom-nav)::before {
        background:
          linear-gradient(180deg, rgba(255,255,255,.13), transparent 34%),
          linear-gradient(90deg, rgba(255,255,255,.055), transparent 18%, transparent 82%, rgba(190,222,255,.050)),
          radial-gradient(ellipse at 16% -8%, rgba(255,255,255,.14), transparent 38%),
          radial-gradient(ellipse at 92% 108%, rgba(150,202,255,.045), transparent 34%) !important;
        opacity: .34 !important;
      }

      body.stable-glass-rendering :where(.glass-card,.summary-card,.metric-card,.chart-card,.tool-card,.auth-sheet,.mobile-command-card,.tools-panel-card,.account-row,.settings-group-sheet,.settings-group-card,.bottom-nav)::after {
        inset: .75px !important;
        border: 1px solid rgba(255,255,255,.045) !important;
        box-shadow: inset 0 .45px 0 rgba(255,255,255,.13), inset 0 -.5px 0 rgba(0,0,0,.07), inset .45px 0 0 var(--assistant-glass-edge-cool) !important;
      }

      body.assistant-balanced-performance :where(.glass-card,.chat-shell,.summary-card,.metric-card,.chart-card,.tool-card,.auth-sheet,.mobile-command-card,.tools-panel-card,.account-row,.settings-group-sheet,.settings-group-card) {
        backdrop-filter: blur(8px) saturate(112%) brightness(1.035) !important;
        -webkit-backdrop-filter: blur(8px) saturate(112%) brightness(1.035) !important;
      }

      body.assistant-balanced-performance .bottom-nav {
        backdrop-filter: blur(8px) saturate(112%) brightness(1.035) !important;
        -webkit-backdrop-filter: blur(8px) saturate(112%) brightness(1.035) !important;
      }

      body.assistant-balanced-performance :where(.summary-chip,.record-item,.draft-card,.draft-item,.chat-composer,textarea,input,select,.tag-btn,.range-chip,.ghost-btn,.mini-ghost-btn,.summary-box,.budget-pill,.auth-tab,.icon-btn,.delete-btn,.chat-row.assistant .chat-bubble,.tools-back,.account-pill,.appearance-preview,.performance-mode-option),
      body.assistant-quality-performance :where(.summary-chip,.record-item,.draft-card,.draft-item,.chat-composer,textarea,input,select,.tag-btn,.range-chip,.ghost-btn,.mini-ghost-btn,.summary-box,.budget-pill,.auth-tab,.icon-btn,.delete-btn,.chat-row.assistant .chat-bubble,.tools-back,.account-pill,.appearance-preview,.performance-mode-option) {
        backdrop-filter: none !important;
        -webkit-backdrop-filter: none !important;
      }

      body.assistant-balanced-performance .scene-backdrop,
      body.assistant-balanced-performance .scene-backdrop::before,
      body.assistant-balanced-performance .scene-backdrop::after {
        animation: none !important;
      }

      body.assistant-quality-performance :where(.glass-card,.chat-shell,.summary-card,.metric-card,.chart-card,.tool-card,.auth-sheet,.mobile-command-card,.tools-panel-card,.account-row,.settings-group-sheet,.settings-group-card) {
        backdrop-filter: blur(12px) saturate(122%) brightness(1.045) contrast(1.012) !important;
        -webkit-backdrop-filter: blur(12px) saturate(122%) brightness(1.045) contrast(1.012) !important;
      }

      body.assistant-quality-performance .bottom-nav {
        backdrop-filter: blur(10px) saturate(120%) brightness(1.045) !important;
        -webkit-backdrop-filter: blur(10px) saturate(120%) brightness(1.045) !important;
      }

      body.assistant-full-glass :where(.glass-card,.chat-shell,.summary-card,.metric-card,.chart-card,.tool-card,.auth-sheet,.mobile-command-card,.tools-panel-card,.account-row,.settings-group-sheet,.settings-group-card) {
        backdrop-filter: blur(calc(var(--assistant-glass-panel-blur) + 4px)) saturate(138%) brightness(1.065) contrast(1.03) !important;
        -webkit-backdrop-filter: blur(calc(var(--assistant-glass-panel-blur) + 4px)) saturate(138%) brightness(1.065) contrast(1.03) !important;
      }

      body.assistant-lite-motion :where(.glass-card,.chat-shell,.summary-card,.metric-card,.chart-card,.tool-card,.auth-sheet,.mobile-command-card,.tools-panel-card,.account-row,.settings-group-sheet,.settings-group-card,.bottom-nav),
      body.assistant-scrolling:not(.assistant-full-glass) :where(.glass-card,.chat-shell,.summary-card,.metric-card,.chart-card,.tool-card,.auth-sheet,.mobile-command-card,.tools-panel-card,.account-row,.settings-group-sheet,.settings-group-card,.bottom-nav),
      body.assistant-scrolling :where(.summary-chip,.record-item,.draft-card,.draft-item,.tag-btn,.range-chip,.ghost-btn,.mini-ghost-btn,.summary-box,.auth-tab,.icon-btn,.delete-btn,.chat-row.assistant .chat-bubble,.tools-back),
      body.assistant-interacting :where(.summary-chip,.record-item,.draft-card,.draft-item,.tag-btn,.range-chip,.ghost-btn,.mini-ghost-btn,.summary-box,.auth-tab,.icon-btn,.delete-btn,.chat-row.assistant .chat-bubble,.tools-back),
      body.keyboard-open :where(.glass-card,.chat-shell,.summary-card,.metric-card,.chart-card,.tool-card,.auth-sheet,.mobile-command-card,.tools-panel-card,.account-row,.settings-group-sheet,.settings-group-card),
      body.viewport-resizing :where(.glass-card,.chat-shell,.summary-card,.metric-card,.chart-card,.tool-card,.auth-sheet,.mobile-command-card,.tools-panel-card,.account-row,.settings-group-sheet,.settings-group-card) {
        backdrop-filter: none !important;
        -webkit-backdrop-filter: none !important;
      }

      body.assistant-lite-motion :where(.glass-card,.chat-shell,.summary-card,.metric-card,.chart-card,.tool-card,.auth-sheet,.mobile-command-card,.tools-panel-card,.account-row,.settings-group-sheet,.settings-group-card,.bottom-nav) {
        background:
          linear-gradient(145deg, rgba(255,255,255,.072), rgba(255,255,255,.014) 52%, rgba(0,0,0,.016)),
          rgba(255,255,255,.044) !important;
      }

      body.assistant-scrolling .scene-backdrop,
      body.assistant-scrolling .scene-backdrop::before,
      body.assistant-scrolling .scene-backdrop::after,
      body.assistant-interacting .scene-backdrop,
      body.assistant-interacting .scene-backdrop::before,
      body.assistant-interacting .scene-backdrop::after,
      body.viewport-resizing .scene-backdrop,
      body.viewport-resizing .scene-backdrop::before,
      body.viewport-resizing .scene-backdrop::after {
        animation-play-state: paused !important;
      }

      body.assistant-scrolling :where(.liquid-motion-target.liquid-pressed,.liquid-motion-target.liquid-release,.is-pressed,.is-releasing) {
        animation: none !important;
        transform: translate3d(0,0,0) scale(1) !important;
        transition-duration: .001ms !important;
      }

      body.assistant-lite-motion .scene-backdrop::before,
      body.assistant-lite-motion .scene-backdrop::after,
      body.assistant-lite-motion .ambient,
      body.assistant-lite-motion .liquid-nav-indicator {
        display: none !important;
      }

      body.assistant-motion-off *,
      body.assistant-motion-off *::before,
      body.assistant-motion-off *::after {
        animation-duration: .001ms !important;
        animation-iteration-count: 1 !important;
        transition-duration: .001ms !important;
        scroll-behavior: auto !important;
      }

      @media (pointer: coarse), (max-width: 760px) {
        body.stable-glass-rendering :where(.summary-chip,.record-item,.draft-card,.draft-item,textarea,input,select,.tag-btn,.range-chip,.ghost-btn,.mini-ghost-btn,.summary-box,.budget-pill,.auth-tab,.icon-btn,.delete-btn,.chat-row.assistant .chat-bubble,.tools-back,.account-pill,.appearance-preview,.performance-mode-option) {
          backdrop-filter: none !important;
          -webkit-backdrop-filter: none !important;
        }

        body.assistant-balanced-performance :where(.glass-card,.chat-shell,.summary-card,.metric-card,.chart-card,.tool-card,.auth-sheet,.mobile-command-card,.tools-panel-card,.account-row,.settings-group-sheet,.settings-group-card,.bottom-nav) {
          backdrop-filter: blur(7px) saturate(110%) brightness(1.035) !important;
          -webkit-backdrop-filter: blur(7px) saturate(110%) brightness(1.035) !important;
        }

        body.assistant-quality-performance :where(.glass-card,.chat-shell,.summary-card,.metric-card,.chart-card,.tool-card,.auth-sheet,.mobile-command-card,.tools-panel-card,.account-row,.settings-group-sheet,.settings-group-card,.bottom-nav) {
          backdrop-filter: blur(10px) saturate(116%) brightness(1.04) !important;
          -webkit-backdrop-filter: blur(10px) saturate(116%) brightness(1.04) !important;
        }
      }
    `;
    document.head.appendChild(style);
    document.body?.classList.add('stable-glass-rendering');
  }

  function applyPerformanceMode() {
    const selected = readSavedMode();
    const resolved = resolveMode(selected);
    const info = deviceInfo();
    document.body?.classList.toggle('assistant-lite-motion', resolved === 'lite');
    document.body?.classList.toggle('assistant-balanced-performance', resolved === 'balanced');
    document.body?.classList.toggle('assistant-quality-performance', resolved === 'quality');
    document.body?.classList.toggle('assistant-full-glass', resolved === 'full');
    document.body?.classList.toggle('assistant-android-glass', info.isAndroid);
    document.body?.classList.toggle('assistant-ios-glass', info.isIOS);
    document.body?.classList.toggle('assistant-low-power-device', lowPowerDevice(info));
    document.documentElement.dataset.performanceMode = resolved;
    document.documentElement.dataset.performanceModeSelected = selected;
    window.dispatchEvent(new CustomEvent('assistant-performance-change', {
      detail: { selected, resolved, device: info },
    }));
  }

  function markTimed(className, timeout, timerRef) {
    document.body?.classList.add(className);
    clearTimeout(timerRef.value);
    timerRef.value = setTimeout(() => document.body?.classList.remove(className), timeout);
  }

  function markInteraction() {
    markTimed('assistant-interacting', 180, { get value() { return interactionTimer; }, set value(v) { interactionTimer = v; } });
  }

  function markScroll() {
    markTimed('assistant-scrolling', 180, { get value() { return scrollTimer; }, set value(v) { scrollTimer = v; } });
  }

  function markResize() {
    document.body?.classList.add('viewport-resizing');
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => {
      document.body?.classList.remove('viewport-resizing');
      applyPerformanceMode();
    }, 260);
  }

  function syncKeyboardState() {
    const viewport = window.visualViewport;
    if (!viewport) return;
    const keyboardOpen = viewport.height < window.innerHeight * 0.78;
    document.body?.classList.toggle('keyboard-open', keyboardOpen);
  }

  function bindEvents() {
    window.addEventListener('storage', (event) => {
      if (event.key === PERF_KEY) applyPerformanceMode();
    });
    window.addEventListener('resize', markResize, { passive: true });
    window.addEventListener('orientationchange', markResize, { passive: true });
    window.visualViewport?.addEventListener('resize', () => {
      syncKeyboardState();
      markResize();
    }, { passive: true });
    document.addEventListener('pointerdown', markInteraction, { passive: true, capture: true });
    document.addEventListener('pointermove', markInteraction, { passive: true, capture: true });
    document.addEventListener('scroll', markScroll, { passive: true, capture: true });
  }

  function boot() {
    document.documentElement.dataset.glassStabilityReady = 'true';
    installStyle();
    applyPerformanceMode();
    syncKeyboardState();
    bindEvents();
    window.AssistantPerformance = {
      key: PERF_KEY,
      modes: VALID_MODES,
      getMode: readSavedMode,
      getSelectedMode: readSavedMode,
      getResolvedMode: () => resolveMode(readSavedMode()),
      getDeviceInfo: deviceInfo,
      isLowPower: lowPowerDevice,
      refresh: applyPerformanceMode,
      setMode(mode = 'auto') {
        const next = VALID_MODES.includes(mode) ? mode : 'auto';
        try {
          localStorage.setItem(PERF_KEY, next);
          localStorage.setItem(PERF_VERSION_KEY, PERF_VERSION);
        } catch {}
        applyPerformanceMode();
      },
    };
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();

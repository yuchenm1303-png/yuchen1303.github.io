(() => {
  'use strict';

  const STYLE_ID = 'glass-stability-style';
  const PERF_KEY = 'ai-assistant-performance-mode-v1';
  let motionTimer = 0;

  function getSavedMode() {
    try { return localStorage.getItem(PERF_KEY) || 'auto'; }
    catch { return 'auto'; }
  }

  function detectLowPowerDevice() {
    const ua = navigator.userAgent || '';
    const isAndroid = /Android/i.test(ua);
    const memory = Number(navigator.deviceMemory || 0);
    const cores = Number(navigator.hardwareConcurrency || 0);
    const narrow = Math.min(window.innerWidth || 999, window.innerHeight || 999) <= 390;
    return isAndroid && ((memory && memory <= 4) || (cores && cores <= 4) || narrow);
  }

  function applyPerformanceMode() {
    const mode = getSavedMode();
    const lowPower = mode === 'lite' || (mode === 'auto' && detectLowPowerDevice());
    const balanced = mode !== 'full';
    document.body?.classList.toggle('assistant-lite-motion', lowPower);
    document.body?.classList.toggle('assistant-balanced-performance', balanced && !lowPower);
    document.body?.classList.toggle('assistant-full-glass', mode === 'full');
    document.documentElement.dataset.performanceMode = lowPower ? 'lite' : (balanced ? 'balanced' : 'full');
  }

  function markInteractiveMotion() {
    document.body?.classList.add('assistant-interacting');
    clearTimeout(motionTimer);
    motionTimer = setTimeout(() => document.body?.classList.remove('assistant-interacting'), 180);
  }

  function installStableGlassStyle() {
    const old = document.getElementById(STYLE_ID);
    if (old) old.remove();

    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      html,
      body {
        background-attachment: scroll !important;
      }

      .stable-glass-rendering .view .reveal,
      .stable-glass-rendering .view.active .reveal,
      .stable-glass-rendering .appearance-plus-card {
        opacity: 1 !important;
        transform: none !important;
        animation: none !important;
        visibility: visible !important;
      }

      .stable-glass-rendering .view {
        content-visibility: visible !important;
      }

      .stable-glass-rendering .chat-messages,
      .stable-glass-rendering .record-list,
      .stable-glass-rendering .tools-panel,
      .stable-glass-rendering .tools-grid,
      .stable-glass-rendering .settings-group-list {
        contain: layout paint;
      }

      .stable-glass-rendering .chart-wrap,
      .stable-glass-rendering canvas {
        contain: layout paint size;
      }

      .stable-glass-rendering .ambient,
      .stable-glass-rendering body::before,
      .stable-glass-rendering body::after {
        will-change: auto !important;
      }

      @media (pointer: coarse), (max-width: 760px) {
        body::before,
        body::after,
        .scene-backdrop::before,
        .scene-backdrop::after,
        .ambient {
          animation: none !important;
        }

        .glass-card,
        .chat-shell,
        .summary-card,
        .metric-card,
        .chart-card,
        .tool-card,
        .auth-sheet,
        .mobile-command-card,
        .tools-panel-card,
        .account-row,
        .appearance-detail-panel,
        .settings-group-sheet,
        .settings-group-card,
        .bottom-nav {
          will-change: auto !important;
          isolation: isolate;
        }

        .summary-chip,
        .record-item,
        .draft-card,
        .draft-item,
        textarea,
        input,
        select,
        .tag-btn,
        .range-chip,
        .ghost-btn,
        .mini-ghost-btn,
        .summary-box,
        .budget-pill,
        .auth-tab,
        .icon-btn,
        .delete-btn,
        .chat-row.assistant .chat-bubble,
        .tools-back,
        .account-pill,
        .appearance-preview {
          backdrop-filter: none !important;
          -webkit-backdrop-filter: none !important;
        }
      }

      body.assistant-balanced-performance .glass-card,
      body.assistant-balanced-performance .chat-shell,
      body.assistant-balanced-performance .summary-card,
      body.assistant-balanced-performance .metric-card,
      body.assistant-balanced-performance .chart-card,
      body.assistant-balanced-performance .tool-card,
      body.assistant-balanced-performance .auth-sheet,
      body.assistant-balanced-performance .mobile-command-card,
      body.assistant-balanced-performance .tools-panel-card,
      body.assistant-balanced-performance .account-row,
      body.assistant-balanced-performance .appearance-detail-panel,
      body.assistant-balanced-performance .settings-group-sheet {
        backdrop-filter: blur(10px) saturate(118%) brightness(1.035) !important;
        -webkit-backdrop-filter: blur(10px) saturate(118%) brightness(1.035) !important;
      }

      body.assistant-balanced-performance .bottom-nav {
        backdrop-filter: blur(12px) saturate(120%) brightness(1.04) !important;
        -webkit-backdrop-filter: blur(12px) saturate(120%) brightness(1.04) !important;
      }

      body.assistant-balanced-performance.keyboard-open .glass-card,
      body.assistant-balanced-performance.keyboard-open .chat-shell,
      body.assistant-balanced-performance.settings-group-open .glass-card,
      body.assistant-balanced-performance.settings-group-open .settings-group-sheet,
      body.assistant-balanced-performance.viewport-resizing .glass-card,
      body.assistant-balanced-performance.assistant-interacting .mobile-command-card,
      body.assistant-balanced-performance.assistant-interacting .settings-group-card {
        backdrop-filter: none !important;
        -webkit-backdrop-filter: none !important;
      }

      body.assistant-android-glass .glass-card,
      body.assistant-android-glass .chat-shell,
      body.assistant-android-glass .summary-card,
      body.assistant-android-glass .metric-card,
      body.assistant-android-glass .chart-card,
      body.assistant-android-glass .tool-card,
      body.assistant-android-glass .auth-sheet,
      body.assistant-android-glass .mobile-command-card,
      body.assistant-android-glass .tools-panel-card,
      body.assistant-android-glass .account-row,
      body.assistant-android-glass .appearance-detail-panel,
      body.assistant-android-glass .settings-group-sheet {
        transform: translateZ(0);
      }

      body.assistant-lite-motion .glass-card,
      body.assistant-lite-motion .chat-shell,
      body.assistant-lite-motion .summary-card,
      body.assistant-lite-motion .metric-card,
      body.assistant-lite-motion .chart-card,
      body.assistant-lite-motion .tool-card,
      body.assistant-lite-motion .auth-sheet,
      body.assistant-lite-motion .mobile-command-card,
      body.assistant-lite-motion .tools-panel-card,
      body.assistant-lite-motion .account-row,
      body.assistant-lite-motion .appearance-detail-panel,
      body.assistant-lite-motion .settings-group-sheet,
      body.assistant-lite-motion .settings-group-card,
      body.assistant-lite-motion .bottom-nav {
        backdrop-filter: none !important;
        -webkit-backdrop-filter: none !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.090), rgba(255,255,255,.020) 52%, rgba(0,0,0,.018)),
          rgba(255,255,255,var(--assistant-glass-panel-alpha,.050)) !important;
      }

      body.assistant-lite-motion .ambient,
      body.assistant-lite-motion .scene-backdrop::before,
      body.assistant-lite-motion .scene-backdrop::after {
        display: none !important;
      }

      body.assistant-lite-motion .glass-card::before,
      body.assistant-lite-motion .glass-card::after,
      body.assistant-lite-motion .bottom-nav::before,
      body.assistant-lite-motion .bottom-nav::after,
      body.assistant-lite-motion .summary-chip::before,
      body.assistant-lite-motion .summary-chip::after,
      body.assistant-lite-motion .record-item::before,
      body.assistant-lite-motion .record-item::after,
      body.assistant-lite-motion .draft-card::before,
      body.assistant-lite-motion .draft-card::after,
      body.assistant-lite-motion .draft-item::before,
      body.assistant-lite-motion .draft-item::after,
      body.assistant-lite-motion .tag-btn::before,
      body.assistant-lite-motion .tag-btn::after,
      body.assistant-lite-motion .range-chip::before,
      body.assistant-lite-motion .range-chip::after,
      body.assistant-lite-motion .ghost-btn::before,
      body.assistant-lite-motion .ghost-btn::after,
      body.assistant-lite-motion .mini-ghost-btn::before,
      body.assistant-lite-motion .mini-ghost-btn::after,
      body.assistant-lite-motion .summary-box::before,
      body.assistant-lite-motion .summary-box::after,
      body.assistant-lite-motion .auth-tab::before,
      body.assistant-lite-motion .auth-tab::after,
      body.assistant-lite-motion .icon-btn::before,
      body.assistant-lite-motion .icon-btn::after,
      body.assistant-lite-motion .delete-btn::before,
      body.assistant-lite-motion .delete-btn::after,
      body.assistant-lite-motion .tools-panel-card::before,
      body.assistant-lite-motion .tools-panel-card::after,
      body.assistant-lite-motion .tools-back::before,
      body.assistant-lite-motion .tools-back::after {
        animation: none !important;
        filter: none !important;
        opacity: .16 !important;
      }

      body.detail-open .app-shell,
      body.detail-open .bottom-nav,
      body.detail-open .fab {
        opacity: .96 !important;
      }

      body.assistant-motion-off *,
      body.assistant-motion-off *::before,
      body.assistant-motion-off *::after {
        animation-duration: .001ms !important;
        animation-iteration-count: 1 !important;
        transition-duration: .001ms !important;
        scroll-behavior: auto !important;
      }
    `;
    document.head.appendChild(style);
    document.body?.classList.add('stable-glass-rendering');
  }

  function boot() {
    document.documentElement.dataset.glassStabilityReady = 'true';
    installStableGlassStyle();
    applyPerformanceMode();
    window.addEventListener('storage', (event) => {
      if (event.key === PERF_KEY) applyPerformanceMode();
    });
    window.addEventListener('resize', applyPerformanceMode, { passive: true });
    document.addEventListener('pointerdown', markInteractiveMotion, { passive: true, capture: true });
    document.addEventListener('touchstart', markInteractiveMotion, { passive: true, capture: true });
    window.AssistantPerformance = {
      getMode: getSavedMode,
      setMode(mode = 'auto') {
        const next = ['auto', 'lite', 'full'].includes(mode) ? mode : 'auto';
        localStorage.setItem(PERF_KEY, next);
        applyPerformanceMode();
      },
      isLowPower: detectLowPowerDevice,
      refresh: applyPerformanceMode,
    };
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot, { once: true });
  } else {
    boot();
  }
})();

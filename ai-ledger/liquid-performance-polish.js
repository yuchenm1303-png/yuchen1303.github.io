(() => {
  'use strict';

  const STYLE_ID = 'liquid-performance-polish-style';
  const PERF_KEY = 'ai-assistant-performance-mode-v1';
  const DECORATED = new WeakSet();
  let interactionTimer = 0;
  let resizeTimer = 0;
  let observer = null;

  const glassSurfaces = ':where(.glass-card,.chat-shell,.summary-card,.metric-card,.chart-card,.tool-card,.auth-sheet,.mobile-command-card,.tools-panel-card,.account-row,.appearance-detail-panel,.settings-group-sheet,.settings-group-card,.bottom-nav)';
  const heavyEffects = ':where(.ambient,.scene-backdrop::before,.scene-backdrop::after,body::before,body::after)';
  const listItems = ':where(.chat-row,.record-item,.draft-card,.draft-item,.tool-card,.settings-group-card,.tools-panel-card,.appearance-plus-card)';

  function safeMatch(query) {
    try { return window.matchMedia?.(query)?.matches || false; }
    catch { return false; }
  }

  function getSavedMode() {
    try { return localStorage.getItem(PERF_KEY) || 'auto'; }
    catch { return 'auto'; }
  }

  function getDeviceHint() {
    const ua = navigator.userAgent || '';
    const memory = Number(navigator.deviceMemory || 0);
    const cores = Number(navigator.hardwareConcurrency || 0);
    const minSide = Math.min(window.innerWidth || 999, window.innerHeight || 999);
    return {
      android: /Android/i.test(ua),
      ios: /iPad|iPhone|iPod/i.test(ua) || (/Macintosh/i.test(ua) && navigator.maxTouchPoints > 1),
      lowPower: /Android/i.test(ua) && ((memory && memory <= 4) || (cores && cores <= 4) || minSide <= 390),
      reduced: safeMatch('(prefers-reduced-motion: reduce)'),
    };
  }

  function setClass(el, name, on) {
    if (el && el.classList.contains(name) !== on) el.classList.toggle(name, on);
  }

  function resolveMode() {
    const selected = getSavedMode();
    if (selected === 'lite' || selected === 'balanced' || selected === 'full') return selected;
    return getDeviceHint().lowPower ? 'lite' : 'balanced';
  }

  function installStyle() {
    if (document.getElementById(STYLE_ID)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      :root {
        --assistant-press-fast: 76ms;
        --assistant-panel-contain-size: 180px;
      }

      ${listItems} {
        content-visibility: auto;
        contain-intrinsic-size: var(--assistant-panel-contain-size);
      }

      .chat-messages,
      .record-list,
      .tools-grid,
      .tools-panel,
      .settings-group-content {
        contain: layout paint style;
      }

      .performance-decorated {
        backface-visibility: hidden;
        transform-style: flat;
      }

      body.performance-interacting ${glassSurfaces},
      body.performance-resizing ${glassSurfaces},
      body.keyboard-open ${glassSurfaces} {
        will-change: auto !important;
      }

      body.performance-interacting :where(.summary-chip,.record-item,.draft-card,.draft-item,.tag-btn,.range-chip,.ghost-btn,.mini-ghost-btn,.summary-box,.auth-tab,.icon-btn,.delete-btn,.tools-back,.chat-row.assistant .chat-bubble),
      body.performance-resizing :where(.summary-chip,.record-item,.draft-card,.draft-item,.tag-btn,.range-chip,.ghost-btn,.mini-ghost-btn,.summary-box,.auth-tab,.icon-btn,.delete-btn,.tools-back,.chat-row.assistant .chat-bubble) {
        backdrop-filter: none !important;
        -webkit-backdrop-filter: none !important;
      }

      html[data-performance-mode="lite"] ${heavyEffects},
      html[data-performance-mode="lite"] .liquid-nav-indicator::before,
      html[data-performance-mode="lite"] .liquid-nav-indicator::after {
        animation: none !important;
        filter: none !important;
      }

      html[data-performance-mode="lite"] ${glassSurfaces} {
        box-shadow: 0 8px 20px rgba(0,0,0,.16), inset 0 .7px 0 rgba(255,255,255,.20) !important;
      }

      html[data-performance-mode="balanced"] body.performance-interacting ${glassSurfaces},
      html[data-performance-mode="balanced"] body.performance-resizing ${glassSurfaces} {
        backdrop-filter: none !important;
        -webkit-backdrop-filter: none !important;
      }

      html[data-performance-mode="full"] body:not(.performance-resizing):not(.keyboard-open) .bottom-nav,
      html[data-performance-mode="full"] body:not(.performance-resizing):not(.keyboard-open) .chat-shell {
        will-change: transform;
      }

      body.assistant-motion-off ${listItems},
      body.assistant-motion-off .liquid-motion-target,
      body.assistant-motion-off .liquid-nav-indicator {
        content-visibility: visible;
        transform: none !important;
      }

      @media (prefers-reduced-motion: reduce) {
        *, *::before, *::after {
          animation-duration: .001ms !important;
          animation-iteration-count: 1 !important;
          transition-duration: .001ms !important;
          scroll-behavior: auto !important;
        }
      }
    `;
    document.head.appendChild(style);
  }

  function syncPerformanceFlags() {
    const hint = getDeviceHint();
    const mode = resolveMode();
    document.documentElement.dataset.performanceMode = mode;
    document.documentElement.dataset.performanceDevice = hint.lowPower ? 'low' : 'normal';
    setClass(document.body, 'assistant-motion-off', hint.reduced);
    setClass(document.body, 'assistant-lite-motion', mode === 'lite');
    setClass(document.body, 'assistant-balanced-performance', mode === 'balanced');
    setClass(document.body, 'assistant-full-glass', mode === 'full');
    setClass(document.body, 'assistant-android-glass', hint.android);
    setClass(document.body, 'assistant-ios-glass', hint.ios);
  }

  function markInteracting() {
    if (!document.body) return;
    document.body.classList.add('performance-interacting');
    clearTimeout(interactionTimer);
    interactionTimer = setTimeout(() => document.body.classList.remove('performance-interacting'), 180);
  }

  function markResizing() {
    if (!document.body) return;
    document.body.classList.add('performance-resizing');
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => {
      document.body.classList.remove('performance-resizing');
      syncPerformanceFlags();
      window.AssistantLiquidMotion?.updateNav?.();
    }, 220);
  }

  function decorateElement(el) {
    if (!el || DECORATED.has(el)) return;
    if (!el.matches?.('.record-item,.chat-row,.draft-card,.draft-item,.tool-card,.settings-group-card,.tools-panel-card,.appearance-plus-card')) return;
    el.classList.add('performance-decorated');
    DECORATED.add(el);
  }

  function decorateRoot(root = document) {
    if (root.nodeType === Node.ELEMENT_NODE) decorateElement(root);
    root.querySelectorAll?.('.record-item,.chat-row,.draft-card,.draft-item,.tool-card,.settings-group-card,.tools-panel-card,.appearance-plus-card').forEach(decorateElement);
  }

  function watchDom() {
    observer?.disconnect();
    observer = new MutationObserver((mutations) => {
      let shouldRefreshMotion = false;
      mutations.forEach((mutation) => {
        mutation.addedNodes.forEach((node) => {
          if (node.nodeType !== Node.ELEMENT_NODE) return;
          if (node.matches?.('.record-item,.chat-row,.draft-card,.draft-item,.tool-card,.settings-group-card,.tools-panel-card,.appearance-plus-card') || node.querySelector?.('.record-item,.chat-row,.draft-card,.draft-item,.tool-card,.settings-group-card,.tools-panel-card,.appearance-plus-card')) {
            decorateRoot(node);
          }
          if (node.matches?.('.nav-btn,.settings-group-card,.tool-card,button') || node.querySelector?.('.nav-btn,.settings-group-card,.tool-card,button')) shouldRefreshMotion = true;
        });
      });
      if (shouldRefreshMotion) window.AssistantLiquidMotion?.refresh?.();
    });
    observer.observe(document.body, { childList: true, subtree: true });
  }

  function bindEvents() {
    const pressEvent = window.PointerEvent ? 'pointerdown' : 'touchstart';
    document.addEventListener(pressEvent, markInteracting, { passive: true, capture: true });
    window.addEventListener('resize', markResizing, { passive: true });
    window.visualViewport?.addEventListener('resize', markResizing, { passive: true });
    window.addEventListener('orientationchange', () => setTimeout(markResizing, 160), { passive: true });
    window.addEventListener('storage', (event) => { if (event.key === PERF_KEY) syncPerformanceFlags(); });
    window.addEventListener('assistant-performance-change', syncPerformanceFlags, { passive: true });
    window.addEventListener('pagehide', () => observer?.disconnect(), { passive: true });
  }

  function boot() {
    if (document.documentElement.dataset.liquidPerformancePolishReady === 'true') return;
    document.documentElement.dataset.liquidPerformancePolishReady = 'true';
    installStyle();
    syncPerformanceFlags();
    decorateRoot(document);
    bindEvents();
    watchDom();
    window.AssistantPerformancePolish = {
      refresh: () => { syncPerformanceFlags(); decorateRoot(document); },
      markInteracting,
      markResizing,
      mode: resolveMode,
    };
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();

(() => {
  const STYLE_ID = 'ios-glass-motion-style';
  const PRESSED_CLASS = 'liquid-pressed';
  const RELEASE_CLASS = 'liquid-release';
  const TARGET_SELECTOR = [
    '.tool-card',
    '.nav-btn',
    '.tag-btn',
    '.range-chip',
    '.ghost-btn',
    '.mini-ghost-btn',
    '.primary-btn',
    '.danger-btn',
    '.icon-btn',
    '.delete-btn',
    '.tools-back',
    '.bg-option',
    '.appearance-toggle',
    '.send-btn',
    '.confirm-btn',
    '.confirm-draft',
    '.cancel-draft',
    '.settings-group-card'
  ].join(',');

  const IGNORE_SELECTOR = [
    'input',
    'textarea',
    'select',
    '[type="range"]',
    '[disabled]',
    '[aria-disabled="true"]'
  ].join(',');

  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const coarsePointer = window.matchMedia('(pointer: coarse)');
  const raf = window.requestAnimationFrame.bind(window);
  const caf = window.cancelAnimationFrame.bind(window);

  let activePress = null;
  let navFrame = 0;
  let navSettleTimer = 0;
  let targetRefreshFrame = 0;
  let lastVibrationAt = 0;
  let observer = null;

  function installStyle() {
    document.querySelector(`#${STYLE_ID}`)?.remove();
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      :root {
        --liquid-motion-fast: 150ms;
        --liquid-motion-mid: 320ms;
        --liquid-motion-slow: 560ms;
        --liquid-ease-out: cubic-bezier(.22,1,.36,1);
        --liquid-ease-spring: cubic-bezier(.18,1.24,.26,1);
        --liquid-ease-nav: cubic-bezier(.18,.86,.18,1);
      }

      body.assistant-liquid-motion {
        text-rendering: optimizeLegibility;
      }

      .liquid-touch-glow { display: none !important; content: none !important; }

      .liquid-motion-target,
      .liquid-motion-target > *,
      .settings-group-card,
      .settings-group-card > *,
      .settings-group-title,
      .settings-group-desc,
      .settings-group-arrow,
      .settings-group-icon {
        writing-mode: horizontal-tb !important;
        text-orientation: mixed !important;
      }

      .liquid-motion-target {
        position: relative !important;
        transform: translate3d(0,0,0) scale(1);
        transform-origin: center center !important;
        transition:
          transform var(--liquid-motion-mid) var(--liquid-ease-spring),
          filter 180ms ease,
          opacity 160ms ease !important;
        -webkit-tap-highlight-color: transparent;
        touch-action: manipulation;
        backface-visibility: hidden;
        contain: paint;
      }

      .liquid-motion-target.is-pressed,
      .liquid-motion-target.is-releasing {
        animation: none !important;
      }

      .liquid-motion-target::before {
        transition: opacity 180ms ease, background 180ms ease !important;
      }

      .liquid-motion-target.liquid-pressed {
        transition-duration: 86ms !important;
        transition-timing-function: cubic-bezier(.2,0,.2,1) !important;
        filter: brightness(1.045) saturate(1.025);
      }

      .tool-card.liquid-pressed,
      .settings-group-card.liquid-pressed {
        transform: translate3d(var(--liquid-shift-x,0px), var(--liquid-shift-y,.7px), 0) scale(.986) !important;
      }

      .nav-btn.liquid-pressed,
      .icon-btn.liquid-pressed,
      .delete-btn.liquid-pressed,
      .send-btn.liquid-pressed {
        transform: translate3d(var(--liquid-shift-x,0px), var(--liquid-shift-y,.3px), 0) scale(.950) !important;
      }

      .tag-btn.liquid-pressed,
      .range-chip.liquid-pressed,
      .ghost-btn.liquid-pressed,
      .mini-ghost-btn.liquid-pressed,
      .primary-btn.liquid-pressed,
      .danger-btn.liquid-pressed,
      .tools-back.liquid-pressed,
      .bg-option.liquid-pressed,
      .appearance-toggle.liquid-pressed,
      .confirm-btn.liquid-pressed,
      .confirm-draft.liquid-pressed,
      .cancel-draft.liquid-pressed {
        transform: translate3d(var(--liquid-shift-x,0px), var(--liquid-shift-y,.7px), 0) scale(.972) !important;
      }

      .liquid-motion-target.liquid-pressed::before {
        background:
          radial-gradient(circle at var(--liquid-touch-x,50%) var(--liquid-touch-y,50%), rgba(255,255,255,.30), transparent 30%),
          linear-gradient(135deg, rgba(255,255,255,.18), rgba(255,255,255,0) 42%, rgba(185,220,255,.08)) !important;
        opacity: .74 !important;
      }

      .liquid-release {
        animation: liquidRelease 420ms var(--liquid-ease-spring) both;
      }

      @keyframes liquidRelease {
        0% { transform: translate3d(var(--liquid-shift-x,0px), var(--liquid-shift-y,.7px), 0) scale(.982); }
        44% { transform: translate3d(0,-.35px,0) scale(1.012); }
        74% { transform: translate3d(0,0,0) scale(.997); }
        100% { transform: translate3d(0,0,0) scale(1); }
      }

      .bottom-nav {
        overflow: hidden !important;
        will-change: auto !important;
        contain: layout paint;
        transform: translateX(-50%) translateZ(0) !important;
      }

      .bottom-nav .nav-btn {
        position: relative !important;
        z-index: 3 !important;
        background: transparent !important;
      }

      .liquid-nav-indicator {
        position: absolute;
        z-index: 1;
        left: 0;
        top: 0;
        width: var(--nav-indicator-w, 0px);
        height: var(--nav-indicator-h, 0px);
        border-radius: 22px;
        pointer-events: none;
        opacity: .88;
        transform:
          translate3d(var(--nav-indicator-x, 0px), var(--nav-indicator-y, 0px), 0)
          scale(var(--nav-indicator-scale, 1));
        background:
          radial-gradient(circle at 24% 14%, rgba(255,255,255,.52), rgba(255,255,255,.16) 34%, transparent 68%),
          linear-gradient(135deg, rgba(255,255,255,.18), rgba(255,255,255,.040) 46%, rgba(126,189,255,.10));
        box-shadow:
          inset 0 .8px 0 rgba(255,255,255,.48),
          inset 0 -.8px 0 rgba(0,0,0,.09),
          0 10px 22px rgba(0,0,0,.14);
        transition:
          transform 560ms var(--liquid-ease-nav),
          width 560ms var(--liquid-ease-nav),
          height 560ms var(--liquid-ease-nav),
          opacity 180ms ease,
          filter 180ms ease;
        will-change: transform;
        backface-visibility: hidden;
      }

      .bottom-nav.liquid-nav-moving .liquid-nav-indicator {
        --nav-indicator-scale: 1.035;
        opacity: .96;
        filter: brightness(1.07) saturate(1.04);
      }

      .nav-btn.liquid-nav-pop { animation: refinedNavContent 420ms var(--liquid-ease-spring) both; }
      .bottom-nav.liquid-nav-wobble { animation: refinedNavBody 520ms var(--liquid-ease-spring) both; }
      .tool-card.liquid-card-pop,
      .settings-group-card.liquid-card-pop {
        animation: refinedEntryBloom 430ms var(--liquid-ease-spring) both;
        animation-delay: var(--tool-pop-delay, 0ms);
      }
      .tools-grid.liquid-grid-pop { animation: refinedGridFloat 360ms var(--liquid-ease-out) both; }

      .view.active.liquid-view-pop > .page-header,
      .view.active.liquid-view-pop > .glass-card,
      .view.active.liquid-view-pop .tools-grid,
      .view.active.liquid-view-pop .chip-row {
        animation: liquidViewIn 360ms var(--liquid-ease-out) both;
      }

      @keyframes refinedNavContent {
        0% { transform: translate3d(0,0,0) scale(.952); }
        48% { transform: translate3d(0,-.7px,0) scale(1.028); }
        76% { transform: translate3d(0,0,0) scale(.996); }
        100% { transform: translate3d(0,0,0) scale(1); }
      }

      @keyframes refinedNavBody {
        0% { transform: translateX(-50%) translateZ(0) scale(1); }
        42% { transform: translateX(-50%) translateZ(0) scale(1.008); }
        80% { transform: translateX(-50%) translateZ(0) scale(.999); }
        100% { transform: translateX(-50%) translateZ(0) scale(1); }
      }

      @keyframes refinedEntryBloom {
        0% { opacity: .92; transform: translate3d(0,7px,0) scale(.985); }
        50% { opacity: 1; transform: translate3d(0,-1px,0) scale(1.008); }
        100% { opacity: 1; transform: translate3d(0,0,0) scale(1); }
      }

      @keyframes refinedGridFloat {
        0% { transform: translate3d(0,5px,0); }
        100% { transform: translate3d(0,0,0); }
      }

      @keyframes liquidViewIn {
        0% { opacity: .01; transform: translate3d(0,10px,0) scale(.992); }
        100% { opacity: 1; transform: translate3d(0,0,0) scale(1); }
      }

      .settings-group-detail.open,
      .appearance-detail-overlay.open,
      .auth-overlay.open,
      .detail-overlay.open {
        overscroll-behavior: contain;
      }

      @media (hover:hover) and (pointer:fine) {
        .liquid-motion-target:hover {
          transform: translate3d(0,-1.5px,0) scale(1.004);
          filter: brightness(1.035);
        }
        .nav-btn.liquid-motion-target:hover {
          transform: translate3d(0,-.6px,0) scale(1.010);
        }
      }

      @media (pointer: coarse), (max-width: 760px) {
        .liquid-motion-target { contain: paint; }
        .bottom-nav { contain: layout paint; }
        .chat-messages { contain: content; }
      }

      body.assistant-motion-off .liquid-motion-target,
      body.assistant-motion-off .nav-btn.liquid-nav-pop,
      body.assistant-motion-off .bottom-nav.liquid-nav-wobble,
      body.assistant-motion-off .bottom-nav.liquid-nav-moving,
      body.assistant-motion-off .tool-card.liquid-card-pop,
      body.assistant-motion-off .settings-group-card.liquid-card-pop,
      body.assistant-motion-off .tools-grid.liquid-grid-pop,
      body.assistant-motion-off .view.active.liquid-view-pop > * {
        animation: none !important;
        transition: none !important;
        transform: none !important;
      }

      @media (prefers-reduced-motion: reduce) {
        .liquid-motion-target,
        .nav-btn.liquid-nav-pop,
        .bottom-nav.liquid-nav-wobble,
        .bottom-nav.liquid-nav-moving,
        .tool-card.liquid-card-pop,
        .settings-group-card.liquid-card-pop,
        .tools-grid.liquid-grid-pop,
        .view.active.liquid-view-pop > * {
          animation: none !important;
          transition: none !important;
          transform: none !important;
        }
      }
    `;
    document.head.appendChild(style);
  }

  function classifyDevice() {
    const ua = navigator.userAgent || '';
    const isIOS = /iPad|iPhone|iPod/i.test(ua) || (/Macintosh/i.test(ua) && navigator.maxTouchPoints > 1);
    const isAndroid = /Android/i.test(ua);
    const memory = Number(navigator.deviceMemory || 4);
    const cores = Number(navigator.hardwareConcurrency || 4);
    document.body.classList.add('assistant-liquid-motion');
    document.body.classList.toggle('assistant-ios-glass', isIOS);
    document.body.classList.toggle('assistant-android-glass', isAndroid);
    document.body.classList.toggle('assistant-lite-motion', isAndroid && (memory <= 4 || cores <= 4));
  }

  function cleanupOldStates() {
    document.querySelectorAll('.liquid-touch-glow').forEach((node) => node.remove());
    document.querySelectorAll('.is-liquid-pressed,.is-liquid-releasing').forEach((node) => {
      node.classList.remove('is-liquid-pressed', 'is-liquid-releasing');
    });
  }

  function isMotionDisabled() {
    return document.body.classList.contains('assistant-motion-off') || reduceMotion.matches;
  }

  function isIgnored(target) {
    return !target || Boolean(target.closest?.(IGNORE_SELECTOR));
  }

  function targetFrom(eventTarget) {
    if (isIgnored(eventTarget)) return null;
    const target = eventTarget.closest?.(TARGET_SELECTOR);
    if (!target || isIgnored(target)) return null;
    return target;
  }

  function setTouchPoint(target, event) {
    if (!target || typeof event?.clientX !== 'number') return;
    const rect = target.getBoundingClientRect();
    if (!rect.width || !rect.height) return;
    const x = Math.max(0, Math.min(100, ((event.clientX - rect.left) / rect.width) * 100));
    const y = Math.max(0, Math.min(100, ((event.clientY - rect.top) / rect.height) * 100));
    const shiftX = ((x - 50) / 50) * 1.15;
    const shiftY = Math.max(.3, ((y - 38) / 62) * 1.1);
    target.style.setProperty('--liquid-touch-x', `${x.toFixed(1)}%`);
    target.style.setProperty('--liquid-touch-y', `${y.toFixed(1)}%`);
    target.style.setProperty('--liquid-shift-x', `${shiftX.toFixed(2)}px`);
    target.style.setProperty('--liquid-shift-y', `${shiftY.toFixed(2)}px`);
  }

  function clearTouchPoint(target) {
    if (!target) return;
    window.setTimeout(() => {
      if (target.classList.contains(PRESSED_CLASS)) return;
      target.style.removeProperty('--liquid-shift-x');
      target.style.removeProperty('--liquid-shift-y');
    }, 460);
  }

  function softVibrate() {
    if (!coarsePointer.matches || isMotionDisabled() || !navigator.vibrate) return;
    const now = Date.now();
    if (now - lastVibrationAt < 900) return;
    lastVibrationAt = now;
    try { navigator.vibrate(3); } catch {}
  }

  function popClass(element, className, timeout = 520) {
    if (!element || isMotionDisabled()) return;
    element.classList.remove(className);
    raf(() => {
      element.classList.add(className);
      window.setTimeout(() => element.classList.remove(className), timeout);
    });
  }

  function prepareTargets(root = document) {
    root.querySelectorAll?.(TARGET_SELECTOR).forEach((target) => {
      if (isIgnored(target)) return;
      target.classList.add('liquid-motion-target');
      target.dataset.liquidMotionManaged = 'true';
    });
    ensureNavIndicator();
    scheduleNavIndicator(false);
  }

  function schedulePrepareTargets(root = document) {
    caf(targetRefreshFrame);
    targetRefreshFrame = raf(() => prepareTargets(root));
  }

  function beginPress(target, event) {
    if (!target || isMotionDisabled()) return;
    if (activePress?.target && activePress.target !== target) {
      activePress.target.classList.remove(PRESSED_CLASS);
    }
    setTouchPoint(target, event);
    target.classList.remove(RELEASE_CLASS);
    target.classList.add(PRESSED_CLASS);
    activePress = {
      target,
      pointerId: event.pointerId,
      startX: event.clientX || 0,
      startY: event.clientY || 0,
      canceled: false,
    };
    if (target.classList.contains('nav-btn')) moveNavIndicatorTo(target, true);
    softVibrate();
  }

  function cancelPress(animate = false) {
    if (!activePress?.target) return;
    const target = activePress.target;
    activePress = null;
    target.classList.remove(PRESSED_CLASS);
    if (animate && !isMotionDisabled()) popClass(target, RELEASE_CLASS, 430);
    clearTouchPoint(target);
  }

  function finishPress() {
    const target = activePress?.target;
    if (!target) return;
    cancelPress(true);

    if (target.classList.contains('nav-btn')) {
      moveNavIndicatorTo(target, true);
      popClass(target, 'liquid-nav-pop', 440);
      popClass(document.querySelector('.bottom-nav'), 'liquid-nav-wobble', 540);
      if (target.dataset.view === 'tools') window.setTimeout(popToolCards, 140);
    } else if (target.classList.contains('tool-card') || target.classList.contains('settings-group-card')) {
      popClass(target, 'liquid-card-pop', 500);
    }
  }

  function handlePointerDown(event) {
    if (event.button !== undefined && event.button > 0) return;
    const target = targetFrom(event.target);
    if (!target) return;
    beginPress(target, event);
  }

  function handlePointerMove(event) {
    if (!activePress?.target) return;
    if (activePress.pointerId !== undefined && event.pointerId !== activePress.pointerId) return;
    const dx = event.clientX - activePress.startX;
    const dy = event.clientY - activePress.startY;
    const limit = coarsePointer.matches ? 10 : 14;
    if (Math.hypot(dx, dy) > limit) {
      activePress.canceled = true;
      cancelPress(false);
      return;
    }
    setTouchPoint(activePress.target, event);
  }

  function handlePointerUp() {
    if (activePress?.canceled) return cancelPress(false);
    finishPress();
  }

  function ensureNavIndicator() {
    const nav = document.querySelector('.bottom-nav');
    if (!nav || nav.querySelector('.liquid-nav-indicator')) return;
    const indicator = document.createElement('span');
    indicator.className = 'liquid-nav-indicator';
    indicator.setAttribute('aria-hidden', 'true');
    nav.prepend(indicator);
  }

  function scheduleNavIndicator(animated = true) {
    caf(navFrame);
    navFrame = raf(() => updateNavIndicator(animated));
  }

  function setNavIndicator(nav, button, animated = true) {
    if (!nav || !button) return;
    const navRect = nav.getBoundingClientRect();
    const activeRect = button.getBoundingClientRect();
    nav.style.setProperty('--nav-indicator-w', `${Math.round(activeRect.width)}px`);
    nav.style.setProperty('--nav-indicator-h', `${Math.round(activeRect.height)}px`);
    nav.style.setProperty('--nav-indicator-x', `${Math.round(activeRect.left - navRect.left)}px`);
    nav.style.setProperty('--nav-indicator-y', `${Math.round(activeRect.top - navRect.top)}px`);
    if (!animated || isMotionDisabled()) return;
    nav.classList.add('liquid-nav-moving');
    window.clearTimeout(navSettleTimer);
    navSettleTimer = window.setTimeout(() => nav.classList.remove('liquid-nav-moving'), 560);
  }

  function moveNavIndicatorTo(button, animated = true) {
    const nav = document.querySelector('.bottom-nav');
    const indicator = nav?.querySelector('.liquid-nav-indicator');
    if (!nav || !indicator || !button) return;
    setNavIndicator(nav, button, animated);
  }

  function updateNavIndicator(animated = true) {
    const nav = document.querySelector('.bottom-nav');
    const active = nav?.querySelector('.nav-btn.active') || nav?.querySelector('.nav-btn');
    moveNavIndicatorTo(active, animated);
  }

  function popToolCards() {
    if (isMotionDisabled()) return;
    const grid = document.querySelector('#toolsHome .tools-grid');
    const cards = [...document.querySelectorAll('#toolsHome .tool-card')];
    if (!grid || !cards.length) return;
    popClass(grid, 'liquid-grid-pop', 420);
    cards.forEach((card, index) => {
      card.style.setProperty('--tool-pop-delay', `${Math.min(index * 26, 130)}ms`);
      popClass(card, 'liquid-card-pop', 520 + index * 26);
    });
  }

  function popActiveView() {
    if (isMotionDisabled()) return;
    const view = document.querySelector('.view.active');
    if (!view) return;
    view.classList.remove('liquid-view-pop');
    raf(() => {
      view.classList.add('liquid-view-pop');
      window.setTimeout(() => view.classList.remove('liquid-view-pop'), 460);
    });
  }

  function bindEvents() {
    document.addEventListener('pointerdown', handlePointerDown, { passive: true });
    document.addEventListener('pointermove', handlePointerMove, { passive: true });
    document.addEventListener('pointerup', handlePointerUp, { passive: true });
    document.addEventListener('pointercancel', () => cancelPress(false), { passive: true });
    document.addEventListener('scroll', () => cancelPress(false), { passive: true, capture: true });

    document.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      beginPress(targetFrom(event.target), event);
    });
    document.addEventListener('keyup', (event) => {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      finishPress();
    });

    document.addEventListener('click', (event) => {
      const nav = event.target.closest?.('.nav-btn');
      if (nav) window.setTimeout(() => moveNavIndicatorTo(nav, true), 34);
    }, { passive: true });

    window.addEventListener('assistant-nav-polished', () => {
      scheduleNavIndicator(true);
      popActiveView();
    }, { passive: true });
    window.addEventListener('ai-tools-home', () => window.setTimeout(popToolCards, 80), { passive: true });
    window.addEventListener('resize', () => scheduleNavIndicator(false), { passive: true });
    window.addEventListener('orientationchange', () => window.setTimeout(() => scheduleNavIndicator(false), 180), { passive: true });
  }

  function watchDom() {
    observer?.disconnect();
    observer = new MutationObserver((mutations) => {
      if (!mutations.some((mutation) => mutation.addedNodes.length)) return;
      schedulePrepareTargets(document);
    });
    observer.observe(document.body, { childList: true, subtree: true });
  }

  function boot() {
    installStyle();
    classifyDevice();
    cleanupOldStates();
    prepareTargets();
    bindEvents();
    watchDom();
    window.setTimeout(() => schedulePrepareTargets(document), 260);
    window.setTimeout(() => scheduleNavIndicator(false), 720);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();

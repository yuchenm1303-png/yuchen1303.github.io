(() => {
  const STYLE_ID = 'ios-glass-motion-style';
  const PRESSED_CLASS = 'liquid-pressed';
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
    '.send-btn'
  ].join(',');

  const IGNORE_SELECTOR = [
    'input',
    'textarea',
    'select',
    '[type="range"]',
    '[disabled]'
  ].join(',');

  let activeTarget = null;
  let clearTimer = 0;
  let lastVibrationAt = 0;
  let navFrame = 0;
  let toolPopTimer = 0;

  function installStyle() {
    const old = document.querySelector(`#${STYLE_ID}`);
    if (old) old.remove();

    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
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
        transform-origin: center center !important;
        transition: transform 180ms cubic-bezier(.22,1,.36,1), opacity 160ms ease !important;
        -webkit-tap-highlight-color: transparent;
        touch-action: manipulation;
        backface-visibility: hidden;
      }

      .tool-card.liquid-pressed,
      .tool-card:active {
        transform: translate3d(0,.8px,0) scale(.990) !important;
      }

      .nav-btn.liquid-pressed,
      .nav-btn:active,
      .icon-btn.liquid-pressed,
      .icon-btn:active,
      .delete-btn.liquid-pressed,
      .delete-btn:active,
      .send-btn.liquid-pressed,
      .send-btn:active {
        transform: translate3d(0,0,0) scale(.958) !important;
      }

      .tag-btn.liquid-pressed,
      .tag-btn:active,
      .range-chip.liquid-pressed,
      .range-chip:active,
      .ghost-btn.liquid-pressed,
      .ghost-btn:active,
      .mini-ghost-btn.liquid-pressed,
      .mini-ghost-btn:active,
      .primary-btn.liquid-pressed,
      .primary-btn:active,
      .danger-btn.liquid-pressed,
      .danger-btn:active,
      .tools-back.liquid-pressed,
      .tools-back:active,
      .bg-option.liquid-pressed,
      .bg-option:active,
      .appearance-toggle.liquid-pressed,
      .appearance-toggle:active {
        transform: translate3d(0,.8px,0) scale(.978) !important;
      }

      .bottom-nav {
        overflow: hidden !important;
        will-change: auto !important;
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
        border-radius: 18px;
        pointer-events: none;
        opacity: .86;
        transform: translate3d(var(--nav-indicator-x, 0px), var(--nav-indicator-y, 0px), 0) scale(1);
        background:
          radial-gradient(circle at 24% 18%, rgba(255,255,255,.34), rgba(255,255,255,.12) 34%, transparent 68%),
          linear-gradient(135deg, rgba(255,255,255,.22), rgba(255,255,255,.055) 48%, rgba(145,210,255,.09));
        box-shadow:
          inset 0 1px 0 rgba(255,255,255,.38),
          inset 0 -1px 0 rgba(0,0,0,.10),
          0 8px 18px rgba(0,0,0,.12);
        transition: transform 440ms cubic-bezier(.22,1,.36,1), opacity 180ms ease;
        will-change: transform;
        backface-visibility: hidden;
      }

      .bottom-nav.liquid-nav-travel .liquid-nav-indicator {
        animation: indicatorSoftBreathe 480ms cubic-bezier(.22,1,.36,1) both;
      }

      .nav-btn.liquid-nav-pop { animation: refinedNavContent 360ms cubic-bezier(.22,1,.36,1) both; will-change: transform; }
      .bottom-nav.liquid-nav-wobble { animation: refinedNavBody 420ms cubic-bezier(.22,1,.36,1) both; will-change: transform; }
      .tool-card.liquid-card-pop { animation: refinedEntryBloom 400ms cubic-bezier(.22,1,.36,1) both; animation-delay: var(--tool-pop-delay, 0ms); will-change: transform; }
      .tools-grid.liquid-grid-pop { animation: refinedGridFloat 360ms cubic-bezier(.22,1,.36,1) both; will-change: transform; }

      @keyframes indicatorSoftBreathe {
        0% { opacity: .80; transform: translate3d(var(--nav-indicator-x,0px), var(--nav-indicator-y,0px), 0) scale(.990); }
        48% { opacity: .92; transform: translate3d(var(--nav-indicator-x,0px), var(--nav-indicator-y,0px), 0) scale(1.010); }
        100% { opacity: .86; transform: translate3d(var(--nav-indicator-x,0px), var(--nav-indicator-y,0px), 0) scale(1); }
      }

      @keyframes refinedNavContent {
        0% { transform: translate3d(0,0,0) scale(.958); }
        46% { transform: translate3d(0,-.4px,0) scale(1.026); }
        74% { transform: translate3d(0,0,0) scale(.994); }
        100% { transform: translate3d(0,0,0) scale(1); }
      }

      @keyframes refinedNavBody {
        0% { transform: translateX(-50%) scale(1); }
        46% { transform: translateX(-50%) scale(1.006); }
        80% { transform: translateX(-50%) scale(.999); }
        100% { transform: translateX(-50%) scale(1); }
      }

      @keyframes refinedEntryBloom {
        0% { transform: translate3d(0,5px,0) scale(.988); }
        50% { transform: translate3d(0,-1px,0) scale(1.006); }
        100% { transform: translate3d(0,0,0) scale(1); }
      }

      @keyframes refinedGridFloat {
        0% { transform: translate3d(0,4px,0); }
        100% { transform: translate3d(0,0,0); }
      }

      .settings-group-detail.open,
      .appearance-detail-overlay.open,
      .auth-overlay.open,
      .settings-group-detail.open .settings-group-sheet,
      .appearance-detail-overlay.open .appearance-detail-panel,
      .auth-overlay.open .auth-sheet {
        animation: none !important;
      }

      body.assistant-motion-off .liquid-motion-target,
      body.assistant-motion-off .nav-btn.liquid-nav-pop,
      body.assistant-motion-off .bottom-nav.liquid-nav-wobble,
      body.assistant-motion-off .bottom-nav.liquid-nav-travel,
      body.assistant-motion-off .tool-card.liquid-card-pop,
      body.assistant-motion-off .tools-grid.liquid-grid-pop {
        animation: none !important; transition: none !important; transform: none !important;
      }

      @media (prefers-reduced-motion: reduce) {
        .liquid-motion-target,
        .nav-btn.liquid-nav-pop,
        .bottom-nav.liquid-nav-wobble,
        .bottom-nav.liquid-nav-travel,
        .tool-card.liquid-card-pop,
        .tools-grid.liquid-grid-pop {
          animation: none !important; transition: none !important; transform: none !important;
        }
      }
    `;
    document.head.appendChild(style);
  }

  function cleanupOldGlow() {
    document.querySelectorAll('.liquid-touch-glow').forEach((node) => node.remove());
    document.querySelectorAll('.is-liquid-pressed,.is-liquid-releasing').forEach((node) => {
      node.classList.remove('is-liquid-pressed', 'is-liquid-releasing');
    });
  }

  function isMotionDisabled() {
    return document.body.classList.contains('assistant-motion-off') || window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  function targetFrom(eventTarget) {
    if (!eventTarget || eventTarget.closest?.(IGNORE_SELECTOR)) return null;
    const target = eventTarget.closest?.(TARGET_SELECTOR);
    if (!target || target.closest?.(IGNORE_SELECTOR)) return null;
    return target;
  }

  function prepareTargets() {
    document.querySelectorAll(TARGET_SELECTOR).forEach((target) => {
      if (!target.closest(IGNORE_SELECTOR)) target.classList.add('liquid-motion-target');
    });
    ensureNavIndicator();
    scheduleNavIndicator(false);
  }

  function softVibrate() {
    const now = Date.now();
    if (now - lastVibrationAt < 1200) return;
    lastVibrationAt = now;
    try { navigator.vibrate?.(3); } catch {}
  }

  function press(target) {
    if (!target || isMotionDisabled()) return;
    window.clearTimeout(clearTimer);
    if (activeTarget && activeTarget !== target) activeTarget.classList.remove(PRESSED_CLASS);
    activeTarget = target;
    target.classList.add(PRESSED_CLASS);
    if (target.classList.contains('nav-btn')) {
      moveNavIndicatorTo(target, true);
    }
    softVibrate();
  }

  function popClass(el, className, timeout = 520) {
    if (!el || isMotionDisabled()) return;
    el.classList.remove(className);
    window.requestAnimationFrame(() => {
      window.requestAnimationFrame(() => {
        el.classList.add(className);
        window.setTimeout(() => el.classList.remove(className), timeout);
      });
    });
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
    window.cancelAnimationFrame(navFrame);
    navFrame = window.requestAnimationFrame(() => updateNavIndicator(animated));
  }

  function setNavIndicator(nav, button, animated = true) {
    const navRect = nav.getBoundingClientRect();
    const activeRect = button.getBoundingClientRect();
    const x = activeRect.left - navRect.left;
    const y = activeRect.top - navRect.top;
    nav.style.setProperty('--nav-indicator-w', `${activeRect.width}px`);
    nav.style.setProperty('--nav-indicator-h', `${activeRect.height}px`);
    nav.style.setProperty('--nav-indicator-x', `${x}px`);
    nav.style.setProperty('--nav-indicator-y', `${y}px`);
    if (animated) popClass(nav, 'liquid-nav-travel', 520);
  }

  function moveNavIndicatorTo(button, animated = true) {
    const nav = document.querySelector('.bottom-nav');
    const indicator = nav?.querySelector('.liquid-nav-indicator');
    if (!nav || !indicator || !button) return;
    setNavIndicator(nav, button, animated);
  }

  function updateNavIndicator(animated = true) {
    const nav = document.querySelector('.bottom-nav');
    const indicator = nav?.querySelector('.liquid-nav-indicator');
    const active = nav?.querySelector('.nav-btn.active') || nav?.querySelector('.nav-btn');
    if (!nav || !indicator || !active) return;
    setNavIndicator(nav, active, animated);
  }

  function popToolCards() {
    if (isMotionDisabled()) return;
    window.clearTimeout(toolPopTimer);
    toolPopTimer = window.setTimeout(() => {
      const grid = document.querySelector('#toolsHome .tools-grid');
      const cards = [...document.querySelectorAll('#toolsHome .tool-card')];
      if (!grid || !cards.length) return;
      popClass(grid, 'liquid-grid-pop', 420);
      cards.forEach((card, index) => {
        card.style.setProperty('--tool-pop-delay', `${Math.min(index * 28, 140)}ms`);
        popClass(card, 'liquid-card-pop', 560 + index * 28);
      });
    }, 0);
  }

  function release() {
    if (!activeTarget) return;
    const target = activeTarget;
    activeTarget = null;
    clearTimer = window.setTimeout(() => target.classList.remove(PRESSED_CLASS), 60);

    if (target.classList.contains('nav-btn')) {
      moveNavIndicatorTo(target, true);
      popClass(target, 'liquid-nav-pop', 460);
      popClass(document.querySelector('.bottom-nav'), 'liquid-nav-wobble', 520);
      window.setTimeout(() => scheduleNavIndicator(true), 180);
      if (target.dataset.view === 'tools') window.setTimeout(popToolCards, 140);
    } else if (target.classList.contains('tool-card')) {
      popClass(target, 'liquid-card-pop', 480);
    }
  }

  function boot() {
    installStyle();
    cleanupOldGlow();
    prepareTargets();

    document.addEventListener('pointerdown', (event) => press(targetFrom(event.target)), { passive: true });
    document.addEventListener('pointerup', release, { passive: true });
    document.addEventListener('pointercancel', release, { passive: true });
    document.addEventListener('scroll', release, { passive: true, capture: true });

    document.addEventListener('click', (event) => {
      const nav = event.target.closest?.('.nav-btn');
      if (nav) moveNavIndicatorTo(nav, true);
    }, { passive: true });

    window.addEventListener('assistant-nav-polished', () => scheduleNavIndicator(true), { passive: true });

    document.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      press(targetFrom(event.target));
    });
    document.addEventListener('keyup', (event) => {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      release();
    });

    window.addEventListener('resize', () => scheduleNavIndicator(false), { passive: true });
    window.setTimeout(() => { cleanupOldGlow(); prepareTargets(); }, 300);
    window.setTimeout(() => { cleanupOldGlow(); prepareTargets(); }, 1200);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
(() => {
  const STYLE_ID = 'ios-glass-motion-style';
  const PRESSED_CLASS = 'liquid-pressed';
  const TARGET_SELECTOR = [
    '.settings-group-card',
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
        transition:
          transform 210ms cubic-bezier(.2,.8,.22,1),
          filter 210ms ease,
          box-shadow 260ms ease,
          opacity 210ms ease !important;
        -webkit-tap-highlight-color: transparent;
        touch-action: manipulation;
      }

      .liquid-motion-target.liquid-pressed,
      .liquid-motion-target:active {
        filter: brightness(1.07) saturate(1.04) !important;
      }

      .tool-card.liquid-pressed,
      .tool-card:active,
      .settings-group-card.liquid-pressed,
      .settings-group-card:active {
        transform: translateY(2px) scale(.976) !important;
        box-shadow:
          0 9px 20px rgba(0,0,0,.15),
          inset 0 1px 0 rgba(255,255,255,.38),
          inset 0 -1px 0 rgba(0,0,0,.10),
          0 0 0 1px rgba(255,255,255,.12) !important;
      }

      .nav-btn.liquid-pressed,
      .nav-btn:active,
      .icon-btn.liquid-pressed,
      .icon-btn:active,
      .delete-btn.liquid-pressed,
      .delete-btn:active,
      .send-btn.liquid-pressed,
      .send-btn:active {
        transform: scale(.925) !important;
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
        transform: translateY(1.5px) scale(.966) !important;
      }

      .bottom-nav {
        overflow: hidden !important;
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
        width: 0;
        height: 0;
        border-radius: 18px;
        pointer-events: none;
        opacity: .88;
        transform: translate3d(0,0,0) scale(1);
        background:
          radial-gradient(circle at 22% 18%, rgba(255,255,255,.42), rgba(255,255,255,.12) 34%, transparent 68%),
          linear-gradient(135deg, rgba(255,255,255,.24), rgba(255,255,255,.050) 48%, rgba(145,210,255,.10));
        box-shadow:
          inset 0 1px 0 rgba(255,255,255,.40),
          inset 0 -1px 0 rgba(0,0,0,.10),
          0 10px 24px rgba(0,0,0,.14);
        transition:
          transform 650ms cubic-bezier(.17,1.05,.24,1),
          width 650ms cubic-bezier(.17,1.05,.24,1),
          height 650ms cubic-bezier(.17,1.05,.24,1),
          border-radius 650ms cubic-bezier(.17,1.05,.24,1),
          opacity 260ms ease;
      }

      .bottom-nav.liquid-nav-travel .liquid-nav-indicator {
        animation: indicatorBreath 720ms cubic-bezier(.17,1.05,.24,1) both;
      }

      .nav-btn.liquid-nav-pop { animation: elasticNavContent 560ms cubic-bezier(.18,.95,.22,1) both; }
      .bottom-nav.liquid-nav-wobble { animation: elasticNavBody 680ms cubic-bezier(.18,.95,.22,1) both; }
      .tool-card.liquid-card-pop { animation: liquidCardBloom 640ms cubic-bezier(.18,.95,.22,1) both; animation-delay: var(--tool-pop-delay, 0ms); }
      .tools-grid.liquid-grid-pop { animation: liquidGridFloat 540ms ease both; }
      .settings-group-card.liquid-card-pop { animation: liquidEntryBloom 520ms cubic-bezier(.18,.95,.22,1) both; }

      .tool-card.liquid-card-pop::after,
      .nav-btn.liquid-nav-pop::after {
        content: '' !important;
        position: absolute !important;
        inset: 0 !important;
        border-radius: inherit !important;
        pointer-events: none !important;
        z-index: 2 !important;
        background: radial-gradient(circle at 32% 18%, rgba(255,255,255,.30), transparent 48%) !important;
        animation: softGlassGlint 620ms ease-out both !important;
      }

      .nav-btn > *,
      .tool-card > *,
      .settings-group-card > * {
        position: relative;
        z-index: 4;
      }

      @keyframes indicatorBreath {
        0% { opacity: .72; filter: brightness(1); }
        34% { opacity: .98; filter: brightness(1.16); }
        100% { opacity: .88; filter: brightness(1); }
      }

      @keyframes elasticNavContent {
        0% { transform: scale(.925); }
        42% { transform: scale(1.082); }
        68% { transform: scale(.982); }
        100% { transform: scale(1); }
      }

      @keyframes elasticNavBody {
        0% { transform: translateX(-50%) scale(1); }
        36% { transform: translateX(-50%) scale(1.022); }
        68% { transform: translateX(-50%) scale(.992); }
        100% { transform: translateX(-50%) scale(1); }
      }

      @keyframes liquidCardBloom {
        0% { opacity: .62; transform: translateY(24px) scale(.935); filter: blur(4px) brightness(.96); }
        42% { opacity: 1; transform: translateY(-5px) scale(1.030); filter: blur(0) brightness(1.06); }
        68% { opacity: 1; transform: translateY(1px) scale(.994); filter: blur(0) brightness(1.01); }
        100% { opacity: 1; transform: translateY(0) scale(1); filter: blur(0) brightness(1); }
      }

      @keyframes liquidEntryBloom {
        0% { transform: translateY(2px) scale(.976); }
        46% { transform: translateY(-2px) scale(1.018); }
        100% { transform: translateY(0) scale(1); }
      }

      @keyframes liquidGridFloat {
        0% { transform: translateY(10px); opacity: .86; }
        100% { transform: translateY(0); opacity: 1; }
      }

      @keyframes softGlassGlint {
        0% { opacity: 0; transform: scale(.92); }
        32% { opacity: .75; transform: scale(1); }
        100% { opacity: 0; transform: scale(1.18); }
      }

      .settings-group-detail.open,
      .appearance-detail-overlay.open,
      .auth-overlay.open {
        animation: liquidOverlaySoftIn 260ms ease both !important;
      }

      .settings-group-detail.open .settings-group-sheet,
      .appearance-detail-overlay.open .appearance-detail-panel,
      .auth-overlay.open .auth-sheet {
        animation: liquidSheetSoftExpand 520ms cubic-bezier(.17,1.02,.23,1) both !important;
        transform-origin: 50% 100%;
      }

      @keyframes liquidOverlaySoftIn {
        from { opacity: 0; }
        to { opacity: 1; }
      }

      @keyframes liquidSheetSoftExpand {
        0% { opacity: 0; transform: translateY(28px) scale(.955); }
        48% { opacity: 1; transform: translateY(-3px) scale(1.012); }
        78% { opacity: 1; transform: translateY(1px) scale(.997); }
        100% { opacity: 1; transform: translateY(0) scale(1); }
      }

      body.assistant-motion-off .liquid-motion-target,
      body.assistant-motion-off .nav-btn.liquid-nav-pop,
      body.assistant-motion-off .bottom-nav.liquid-nav-wobble,
      body.assistant-motion-off .bottom-nav.liquid-nav-travel,
      body.assistant-motion-off .tool-card.liquid-card-pop,
      body.assistant-motion-off .tools-grid.liquid-grid-pop,
      body.assistant-motion-off .settings-group-card.liquid-card-pop,
      body.assistant-motion-off .settings-group-detail.open,
      body.assistant-motion-off .settings-group-detail.open .settings-group-sheet,
      body.assistant-motion-off .appearance-detail-overlay.open,
      body.assistant-motion-off .appearance-detail-overlay.open .appearance-detail-panel,
      body.assistant-motion-off .auth-overlay.open,
      body.assistant-motion-off .auth-overlay.open .auth-sheet {
        animation: none !important; transition: none !important; transform: none !important; filter: none !important;
      }

      @media (prefers-reduced-motion: reduce) {
        .liquid-motion-target,
        .nav-btn.liquid-nav-pop,
        .bottom-nav.liquid-nav-wobble,
        .bottom-nav.liquid-nav-travel,
        .tool-card.liquid-card-pop,
        .tools-grid.liquid-grid-pop,
        .settings-group-card.liquid-card-pop,
        .settings-group-detail.open,
        .settings-group-detail.open .settings-group-sheet,
        .appearance-detail-overlay.open,
        .appearance-detail-overlay.open .appearance-detail-panel,
        .auth-overlay.open,
        .auth-overlay.open .auth-sheet {
          animation: none !important; transition: none !important; transform: none !important; filter: none !important;
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
    updateNavIndicator(false);
  }

  function softVibrate() {
    const now = Date.now();
    if (now - lastVibrationAt < 900) return;
    lastVibrationAt = now;
    try { navigator.vibrate?.(5); } catch {}
  }

  function press(target) {
    if (!target || isMotionDisabled()) return;
    window.clearTimeout(clearTimer);
    if (activeTarget && activeTarget !== target) activeTarget.classList.remove(PRESSED_CLASS);
    activeTarget = target;
    target.classList.add(PRESSED_CLASS);
    softVibrate();
  }

  function popClass(el, className, timeout = 720) {
    if (!el || isMotionDisabled()) return;
    el.classList.remove(className);
    void el.offsetWidth;
    el.classList.add(className);
    window.setTimeout(() => el.classList.remove(className), timeout);
  }

  function ensureNavIndicator() {
    const nav = document.querySelector('.bottom-nav');
    if (!nav || nav.querySelector('.liquid-nav-indicator')) return;
    const indicator = document.createElement('span');
    indicator.className = 'liquid-nav-indicator';
    indicator.setAttribute('aria-hidden', 'true');
    nav.prepend(indicator);
  }

  function updateNavIndicator(animated = true) {
    const nav = document.querySelector('.bottom-nav');
    const indicator = nav?.querySelector('.liquid-nav-indicator');
    const active = nav?.querySelector('.nav-btn.active') || nav?.querySelector('.nav-btn');
    if (!nav || !indicator || !active) return;
    const navRect = nav.getBoundingClientRect();
    const activeRect = active.getBoundingClientRect();
    const x = activeRect.left - navRect.left;
    const y = activeRect.top - navRect.top;
    indicator.style.width = `${activeRect.width}px`;
    indicator.style.height = `${activeRect.height}px`;
    indicator.style.transform = `translate3d(${x}px, ${y}px, 0)`;
    if (animated) popClass(nav, 'liquid-nav-travel', 760);
  }

  function popToolCards() {
    if (isMotionDisabled()) return;
    const grid = document.querySelector('#toolsHome .tools-grid');
    const cards = [...document.querySelectorAll('#toolsHome .tool-card')];
    if (!grid || !cards.length) return;
    popClass(grid, 'liquid-grid-pop', 620);
    cards.forEach((card, index) => {
      card.style.setProperty('--tool-pop-delay', `${Math.min(index * 42, 210)}ms`);
      popClass(card, 'liquid-card-pop', 860 + index * 42);
    });
  }

  function release() {
    if (!activeTarget) return;
    const target = activeTarget;
    activeTarget = null;
    clearTimer = window.setTimeout(() => target.classList.remove(PRESSED_CLASS), 90);

    if (target.classList.contains('nav-btn')) {
      popClass(target, 'liquid-nav-pop', 680);
      popClass(document.querySelector('.bottom-nav'), 'liquid-nav-wobble', 760);
      window.setTimeout(() => updateNavIndicator(true), 40);
      if (target.dataset.view === 'tools') window.setTimeout(popToolCards, 180);
    } else if (target.classList.contains('tool-card') || target.classList.contains('settings-group-card')) {
      popClass(target, 'liquid-card-pop', 720);
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
      if (nav) window.setTimeout(() => updateNavIndicator(true), 80);
      if (nav?.dataset.view === 'tools') window.setTimeout(popToolCards, 220);
    }, { passive: true });

    document.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      press(targetFrom(event.target));
    });
    document.addEventListener('keyup', (event) => {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      release();
    });

    window.addEventListener('resize', () => updateNavIndicator(false), { passive: true });
    window.setTimeout(() => { cleanupOldGlow(); prepareTargets(); }, 300);
    window.setTimeout(() => { cleanupOldGlow(); prepareTargets(); }, 1200);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();

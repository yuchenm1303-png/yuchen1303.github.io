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
        transform-origin: center center !important;
        transition:
          transform 185ms cubic-bezier(.18,.82,.22,1),
          filter 180ms ease,
          box-shadow 220ms ease,
          opacity 180ms ease !important;
        -webkit-tap-highlight-color: transparent;
        touch-action: manipulation;
      }

      .liquid-motion-target.liquid-pressed,
      .liquid-motion-target:active { filter: brightness(1.055) saturate(1.025) !important; }

      .settings-group-card.liquid-pressed,
      .settings-group-card:active,
      .tool-card.liquid-pressed,
      .tool-card:active {
        transform: translateY(.6px) scale(.994) !important;
        box-shadow:
          0 11px 24px rgba(0,0,0,.145),
          inset 0 1px 0 rgba(255,255,255,.32),
          inset 0 -1px 0 rgba(0,0,0,.08),
          0 0 0 1px rgba(255,255,255,.08) !important;
      }

      .nav-btn.liquid-pressed,
      .nav-btn:active,
      .icon-btn.liquid-pressed,
      .icon-btn:active,
      .delete-btn.liquid-pressed,
      .delete-btn:active,
      .send-btn.liquid-pressed,
      .send-btn:active { transform: scale(.965) !important; }

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
      .appearance-toggle:active { transform: translateY(.6px) scale(.985) !important; }

      .nav-btn.liquid-nav-pop { animation: refinedNavButtonPop 460ms cubic-bezier(.16,.86,.25,1) both; }
      .bottom-nav.liquid-nav-wobble { animation: refinedBottomNavSettle 520ms cubic-bezier(.16,.86,.25,1) both; }
      .tool-card.liquid-card-pop { animation: refinedToolCardSettle 520ms cubic-bezier(.16,.86,.25,1) both; animation-delay: var(--tool-pop-delay, 0ms); }
      .tools-grid.liquid-grid-pop { animation: refinedGridSettle 420ms ease both; }

      @keyframes refinedNavButtonPop {
        0% { transform: scale(.965); }
        38% { transform: scale(1.045); }
        70% { transform: scale(.992); }
        100% { transform: scale(1); }
      }

      @keyframes refinedBottomNavSettle {
        0% { transform: translateX(-50%) scale(1); }
        38% { transform: translateX(-50%) scale(1.008); }
        68% { transform: translateX(-50%) scale(.998); }
        100% { transform: translateX(-50%) scale(1); }
      }

      @keyframes refinedToolCardSettle {
        0% { opacity: .94; transform: translateY(6px) scale(.992); }
        48% { opacity: 1; transform: translateY(-1px) scale(1.008); }
        100% { opacity: 1; transform: translateY(0) scale(1); }
      }

      @keyframes refinedGridSettle {
        0% { transform: translateY(3px); }
        100% { transform: translateY(0); }
      }

      .settings-group-detail.open,
      .appearance-detail-overlay.open,
      .auth-overlay.open { opacity: 1 !important; animation: none !important; }

      .settings-group-detail.open .settings-group-sheet,
      .appearance-detail-overlay.open .appearance-detail-panel,
      .auth-overlay.open .auth-sheet { opacity: 1 !important; transform: none !important; animation: none !important; }

      body.assistant-motion-off .liquid-motion-target,
      body.assistant-motion-off .nav-btn.liquid-nav-pop,
      body.assistant-motion-off .bottom-nav.liquid-nav-wobble,
      body.assistant-motion-off .tool-card.liquid-card-pop,
      body.assistant-motion-off .tools-grid.liquid-grid-pop {
        animation: none !important; transition: none !important; transform: none !important; filter: none !important;
      }

      @media (prefers-reduced-motion: reduce) {
        .liquid-motion-target,
        .nav-btn.liquid-nav-pop,
        .bottom-nav.liquid-nav-wobble,
        .tool-card.liquid-card-pop,
        .tools-grid.liquid-grid-pop {
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
  }

  function softVibrate() {
    const now = Date.now();
    if (now - lastVibrationAt < 1100) return;
    lastVibrationAt = now;
    try { navigator.vibrate?.(3); } catch {}
  }

  function press(target) {
    if (!target || isMotionDisabled()) return;
    window.clearTimeout(clearTimer);
    if (activeTarget && activeTarget !== target) activeTarget.classList.remove(PRESSED_CLASS);
    activeTarget = target;
    target.classList.add(PRESSED_CLASS);
    softVibrate();
  }

  function popClass(el, className, timeout = 620) {
    if (!el || isMotionDisabled()) return;
    el.classList.remove(className);
    void el.offsetWidth;
    el.classList.add(className);
    window.setTimeout(() => el.classList.remove(className), timeout);
  }

  function popToolCards() {
    if (isMotionDisabled()) return;
    const grid = document.querySelector('#toolsHome .tools-grid');
    const cards = [...document.querySelectorAll('#toolsHome .tool-card')];
    if (!grid || !cards.length) return;
    popClass(grid, 'liquid-grid-pop', 520);
    cards.forEach((card, index) => {
      card.style.setProperty('--tool-pop-delay', `${Math.min(index * 34, 170)}ms`);
      popClass(card, 'liquid-card-pop', 700 + index * 34);
    });
  }

  function release() {
    if (!activeTarget) return;
    const target = activeTarget;
    activeTarget = null;
    clearTimer = window.setTimeout(() => target.classList.remove(PRESSED_CLASS), 90);

    if (target.classList.contains('nav-btn')) {
      popClass(target, 'liquid-nav-pop', 560);
      popClass(document.querySelector('.bottom-nav'), 'liquid-nav-wobble', 640);
      if (target.dataset.view === 'tools') window.setTimeout(popToolCards, 140);
    } else if (target.classList.contains('tool-card') || target.classList.contains('settings-group-card')) {
      popClass(target, 'liquid-card-pop', 620);
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
      if (nav?.dataset.view === 'tools') window.setTimeout(popToolCards, 160);
    }, { passive: true });

    document.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      press(targetFrom(event.target));
    });
    document.addEventListener('keyup', (event) => {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      release();
    });

    window.setTimeout(() => { cleanupOldGlow(); prepareTargets(); }, 300);
    window.setTimeout(() => { cleanupOldGlow(); prepareTargets(); }, 1200);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();

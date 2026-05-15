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
      .liquid-touch-glow {
        display: none !important;
        content: none !important;
      }

      .liquid-motion-target,
      .liquid-motion-target > * {
        writing-mode: horizontal-tb !important;
        text-orientation: mixed !important;
      }

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
          transform 120ms cubic-bezier(.2,.8,.2,1),
          filter 120ms ease,
          box-shadow 150ms ease,
          opacity 120ms ease !important;
        -webkit-tap-highlight-color: transparent;
        touch-action: manipulation;
      }

      .liquid-motion-target.liquid-pressed,
      .liquid-motion-target:active {
        filter: brightness(1.08) saturate(1.04) !important;
      }

      .settings-group-card.liquid-pressed,
      .settings-group-card:active,
      .tool-card.liquid-pressed,
      .tool-card:active {
        transform: translateY(1px) scale(.992) !important;
        box-shadow:
          0 10px 22px rgba(0,0,0,.14),
          inset 0 1px 0 rgba(255,255,255,.34),
          inset 0 -1px 0 rgba(0,0,0,.08),
          0 0 0 1px rgba(255,255,255,.10) !important;
      }

      .nav-btn.liquid-pressed,
      .nav-btn:active,
      .icon-btn.liquid-pressed,
      .icon-btn:active,
      .delete-btn.liquid-pressed,
      .delete-btn:active,
      .send-btn.liquid-pressed,
      .send-btn:active {
        transform: scale(.965) !important;
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
        transform: translateY(1px) scale(.975) !important;
      }

      .settings-group-card.liquid-pressed::after,
      .settings-group-card:active::after,
      .tool-card.liquid-pressed::after,
      .tool-card:active::after,
      .nav-btn.liquid-pressed::after,
      .nav-btn:active::after {
        opacity: .22 !important;
      }

      .settings-group-detail.open,
      .appearance-detail-overlay.open,
      .auth-overlay.open {
        animation: safeOverlayFadeIn 140ms ease both;
      }

      .settings-group-detail.open .settings-group-sheet,
      .appearance-detail-overlay.open .appearance-detail-panel,
      .auth-overlay.open .auth-sheet {
        animation: safeSheetRise 180ms cubic-bezier(.2,.8,.2,1) both;
        transform-origin: 50% 100%;
      }

      @keyframes safeOverlayFadeIn {
        from { opacity: 0; }
        to { opacity: 1; }
      }

      @keyframes safeSheetRise {
        from { opacity: 0; transform: translateY(12px) scale(.985); }
        to { opacity: 1; transform: translateY(0) scale(1); }
      }

      body.assistant-motion-off .liquid-motion-target,
      body.assistant-motion-off .settings-group-detail.open,
      body.assistant-motion-off .settings-group-detail.open .settings-group-sheet,
      body.assistant-motion-off .appearance-detail-overlay.open,
      body.assistant-motion-off .appearance-detail-overlay.open .appearance-detail-panel,
      body.assistant-motion-off .auth-overlay.open,
      body.assistant-motion-off .auth-overlay.open .auth-sheet {
        animation: none !important;
        transition: none !important;
        transform: none !important;
        filter: none !important;
      }

      @media (prefers-reduced-motion: reduce) {
        .liquid-motion-target,
        .settings-group-detail.open,
        .settings-group-detail.open .settings-group-sheet,
        .appearance-detail-overlay.open,
        .appearance-detail-overlay.open .appearance-detail-panel,
        .auth-overlay.open,
        .auth-overlay.open .auth-sheet {
          animation: none !important;
          transition: none !important;
          transform: none !important;
          filter: none !important;
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
    if (now - lastVibrationAt < 900) return;
    lastVibrationAt = now;
    try { navigator.vibrate?.(4); } catch {}
  }

  function press(target) {
    if (!target || isMotionDisabled()) return;
    window.clearTimeout(clearTimer);
    if (activeTarget && activeTarget !== target) activeTarget.classList.remove(PRESSED_CLASS);
    activeTarget = target;
    target.classList.add(PRESSED_CLASS);
    softVibrate();
  }

  function release() {
    if (!activeTarget) return;
    const target = activeTarget;
    activeTarget = null;
    clearTimer = window.setTimeout(() => target.classList.remove(PRESSED_CLASS), 70);
  }

  function boot() {
    installStyle();
    cleanupOldGlow();
    prepareTargets();

    document.addEventListener('pointerdown', (event) => press(targetFrom(event.target)), { passive: true });
    document.addEventListener('pointerup', release, { passive: true });
    document.addEventListener('pointercancel', release, { passive: true });
    document.addEventListener('scroll', release, { passive: true, capture: true });

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

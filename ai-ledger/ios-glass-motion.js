(() => {
  const STYLE_ID = 'ios-glass-motion-style';
  const TARGET_SELECTOR = [
    'button',
    '[role="button"]',
    '.settings-group-card',
    '.tool-card',
    '.record-item',
    '.summary-chip',
    '.range-chip',
    '.tag-btn',
    '.ghost-btn',
    '.mini-ghost-btn',
    '.primary-btn',
    '.danger-btn',
    '.nav-btn',
    '.icon-btn',
    '.delete-btn',
    '.tools-back',
    '.bg-option',
    '.appearance-toggle'
  ].join(',');

  const IGNORE_SELECTOR = [
    'input',
    'textarea',
    'select',
    '[type="range"]',
    '[disabled]',
    '.appearance-select-wrap',
    '.settings-group-detail',
    '.auth-overlay'
  ].join(',');

  let activeTarget = null;
  let pointerMoveFrame = 0;
  let lastVibrationAt = 0;

  function installStyle() {
    const old = document.querySelector(`#${STYLE_ID}`);
    if (old) old.remove();

    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      :root {
        --liquid-motion-duration: 220ms;
        --liquid-motion-ease: cubic-bezier(.2,.8,.2,1);
        --liquid-spring-ease: cubic-bezier(.18, .86, .22, 1.12);
      }

      .liquid-motion-target {
        position: relative !important;
        overflow: hidden !important;
        transform-origin: center center !important;
        transition:
          transform 150ms var(--liquid-motion-ease),
          box-shadow 180ms var(--liquid-motion-ease),
          opacity 160ms ease,
          background 180ms ease !important;
        touch-action: manipulation;
      }

      .liquid-motion-target > *:not(.liquid-touch-glow) {
        position: relative;
        z-index: 3;
      }

      .liquid-touch-glow {
        position: absolute;
        z-index: 2;
        inset: -1px;
        border-radius: inherit;
        pointer-events: none;
        opacity: 0;
        transform: scale(.86);
        background:
          radial-gradient(circle at var(--press-x,50%) var(--press-y,50%), rgba(255,255,255,.56) 0%, rgba(255,255,255,.28) 16%, rgba(143,225,255,.10) 38%, transparent 66%),
          linear-gradient(120deg, transparent 0%, rgba(255,255,255,.16) 38%, transparent 64%);
        mix-blend-mode: screen;
        transition:
          opacity 150ms ease,
          transform 240ms var(--liquid-spring-ease),
          background-position 200ms ease;
      }

      .liquid-motion-target.is-liquid-pressed {
        transform: translateY(1px) scale(.982) !important;
      }

      .liquid-motion-target.is-liquid-pressed .liquid-touch-glow {
        opacity: .92;
        transform: scale(1);
      }

      .liquid-motion-target.is-liquid-releasing {
        animation: liquidReleasePop 300ms var(--liquid-spring-ease) both;
      }

      .liquid-motion-target.is-liquid-releasing .liquid-touch-glow {
        animation: liquidGlowRelease 360ms ease-out both;
      }

      .settings-group-card.is-liquid-pressed,
      .tool-card.is-liquid-pressed,
      .record-item.is-liquid-pressed {
        transform: translateY(1px) scale(.986) !important;
      }

      .nav-btn.is-liquid-pressed,
      .icon-btn.is-liquid-pressed,
      .delete-btn.is-liquid-pressed {
        transform: scale(.955) !important;
      }

      .bottom-nav .nav-btn.active .liquid-touch-glow {
        opacity: .28;
        transform: scale(1);
      }

      .bottom-nav .nav-btn.active.is-liquid-pressed .liquid-touch-glow {
        opacity: .88;
      }

      .settings-group-detail.open,
      .appearance-detail-overlay.open,
      .auth-overlay.open {
        animation: liquidOverlayIn 180ms ease both;
      }

      .settings-group-detail.open .settings-group-sheet,
      .appearance-detail-overlay.open .appearance-detail-panel,
      .auth-overlay.open .auth-sheet {
        animation: liquidSheetRise 280ms var(--liquid-spring-ease) both;
        transform-origin: 50% 100%;
      }

      .bottom-nav {
        transition:
          transform 220ms var(--liquid-spring-ease),
          opacity 160ms ease,
          box-shadow 180ms ease !important;
      }

      .bottom-nav:has(.nav-btn.is-liquid-pressed) {
        box-shadow:
          0 16px 32px rgba(0,0,0,.20),
          inset 0 .8px 0 rgba(255,255,255,.34),
          inset 0 -.8px 0 rgba(0,0,0,.10) !important;
      }

      @keyframes liquidReleasePop {
        0% { transform: translateY(1px) scale(.982); }
        56% { transform: translateY(-.5px) scale(1.010); }
        100% { transform: translateY(0) scale(1); }
      }

      @keyframes liquidGlowRelease {
        0% { opacity: .85; transform: scale(1); }
        70% { opacity: .22; transform: scale(1.16); }
        100% { opacity: 0; transform: scale(1.22); }
      }

      @keyframes liquidOverlayIn {
        from { opacity: 0; }
        to { opacity: 1; }
      }

      @keyframes liquidSheetRise {
        0% { opacity: 0; transform: translateY(22px) scale(.965); }
        58% { opacity: 1; transform: translateY(-2px) scale(1.006); }
        100% { opacity: 1; transform: translateY(0) scale(1); }
      }

      body.assistant-motion-off .liquid-motion-target,
      body.assistant-motion-off .liquid-touch-glow,
      body.assistant-motion-off .settings-group-detail.open,
      body.assistant-motion-off .settings-group-detail.open .settings-group-sheet,
      body.assistant-motion-off .appearance-detail-overlay.open,
      body.assistant-motion-off .appearance-detail-overlay.open .appearance-detail-panel,
      body.assistant-motion-off .auth-overlay.open,
      body.assistant-motion-off .auth-overlay.open .auth-sheet {
        animation: none !important;
        transition: none !important;
        transform: none !important;
      }

      body.assistant-motion-off .liquid-touch-glow {
        opacity: 0 !important;
      }

      @media (prefers-reduced-motion: reduce) {
        .liquid-motion-target,
        .liquid-touch-glow,
        .settings-group-detail.open,
        .settings-group-detail.open .settings-group-sheet,
        .appearance-detail-overlay.open,
        .appearance-detail-overlay.open .appearance-detail-panel,
        .auth-overlay.open,
        .auth-overlay.open .auth-sheet {
          animation: none !important;
          transition: none !important;
          transform: none !important;
        }

        .liquid-touch-glow {
          opacity: 0 !important;
        }
      }
    `;
    document.head.appendChild(style);
  }

  function isMotionDisabled() {
    return document.body.classList.contains('assistant-motion-off') || window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  function getTarget(eventTarget) {
    if (!eventTarget || eventTarget.closest?.(IGNORE_SELECTOR)) return null;
    const target = eventTarget.closest?.(TARGET_SELECTOR);
    if (!target || target.closest?.(IGNORE_SELECTOR)) return null;
    return target;
  }

  function ensureGlow(target) {
    let glow = target.querySelector(':scope > .liquid-touch-glow');
    if (!glow) {
      glow = document.createElement('span');
      glow.className = 'liquid-touch-glow';
      glow.setAttribute('aria-hidden', 'true');
      target.prepend(glow);
    }
    target.classList.add('liquid-motion-target');
    return glow;
  }

  function setPressPoint(target, event) {
    const rect = target.getBoundingClientRect();
    const clientX = event.clientX ?? (event.touches?.[0]?.clientX) ?? rect.left + rect.width / 2;
    const clientY = event.clientY ?? (event.touches?.[0]?.clientY) ?? rect.top + rect.height / 2;
    const x = Math.max(0, Math.min(100, ((clientX - rect.left) / rect.width) * 100));
    const y = Math.max(0, Math.min(100, ((clientY - rect.top) / rect.height) * 100));
    target.style.setProperty('--press-x', `${x.toFixed(1)}%`);
    target.style.setProperty('--press-y', `${y.toFixed(1)}%`);
  }

  function softVibrate() {
    const now = Date.now();
    if (now - lastVibrationAt < 650) return;
    lastVibrationAt = now;
    try { navigator.vibrate?.(6); } catch {}
  }

  function press(target, event) {
    if (!target || isMotionDisabled()) return;
    activeTarget = target;
    ensureGlow(target);
    setPressPoint(target, event);
    target.classList.remove('is-liquid-releasing');
    target.classList.add('is-liquid-pressed');
    softVibrate();
  }

  function move(event) {
    if (!activeTarget || isMotionDisabled()) return;
    window.cancelAnimationFrame(pointerMoveFrame);
    pointerMoveFrame = window.requestAnimationFrame(() => setPressPoint(activeTarget, event));
  }

  function release(target = activeTarget) {
    if (!target) return;
    target.classList.remove('is-liquid-pressed');
    if (!isMotionDisabled()) {
      target.classList.remove('is-liquid-releasing');
      void target.offsetWidth;
      target.classList.add('is-liquid-releasing');
      window.setTimeout(() => target.classList.remove('is-liquid-releasing'), 380);
    }
    if (target === activeTarget) activeTarget = null;
  }

  function attachInitialTargets() {
    document.querySelectorAll(TARGET_SELECTOR).forEach((target) => {
      if (!target.closest(IGNORE_SELECTOR)) {
        target.classList.add('liquid-motion-target');
        ensureGlow(target);
      }
    });
  }

  function boot() {
    installStyle();
    attachInitialTargets();

    document.addEventListener('pointerdown', (event) => press(getTarget(event.target), event), { passive: true });
    document.addEventListener('pointermove', move, { passive: true });
    document.addEventListener('pointerup', () => release(), { passive: true });
    document.addEventListener('pointercancel', () => release(), { passive: true });
    document.addEventListener('mouseleave', () => release(), { passive: true });

    document.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      press(getTarget(event.target), event);
    });
    document.addEventListener('keyup', (event) => {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      release(getTarget(event.target));
    });

    const observer = new MutationObserver(() => attachInitialTargets());
    observer.observe(document.body, { childList: true, subtree: true });

    window.setTimeout(attachInitialTargets, 300);
    window.setTimeout(attachInitialTargets, 1200);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();

(() => {
  'use strict';

  const STYLE_ID = 'navigation-polish-style';
  const NAV_SYNC_EVENT = 'assistant-nav-polished';
  let navObserver = null;
  let syncFrame = 0;

  function installStyle() {
    if (document.getElementById(STYLE_ID)) return;

    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      .bottom-nav {
        grid-template-columns: repeat(3, minmax(0, 1fr)) !important;
        isolation: isolate !important;
      }

      .bottom-nav::after {
        content: "";
        position: absolute;
        inset: 1px;
        z-index: 0;
        border-radius: inherit;
        pointer-events: none;
        background:
          linear-gradient(180deg, rgba(255,255,255,.24), rgba(255,255,255,.038) 52%, rgba(0,0,0,.025)),
          radial-gradient(circle at 50% -20%, rgba(255,255,255,.22), transparent 54%);
        opacity: .62;
      }

      .bottom-nav .nav-btn {
        min-width: 0 !important;
        user-select: none;
        -webkit-user-select: none;
      }

      .bottom-nav .nav-btn span,
      .bottom-nav .nav-btn em {
        position: relative;
        z-index: 4;
        transition: none !important;
        transform: none !important;
      }

      .bottom-nav .nav-btn.active {
        color: rgba(247,253,255,.98) !important;
        background: transparent !important;
        text-shadow: 0 1px 8px rgba(31,123,148,.16);
      }

      .bottom-nav .nav-btn.active span {
        transform: none !important;
      }

      .bottom-nav .nav-btn:not(.active) em {
        opacity: .72;
      }

      .bottom-nav .nav-btn:focus-visible {
        outline: 2px solid rgba(134,236,226,.72) !important;
        outline-offset: 3px;
      }

      .bottom-nav .liquid-nav-indicator {
        border-radius: 18px !important;
        background:
          radial-gradient(circle at 26% 14%, rgba(255,255,255,.48), rgba(255,255,255,.15) 34%, transparent 68%),
          linear-gradient(135deg, rgba(92,164,196,.38), rgba(98,128,190,.26) 52%, rgba(255,255,255,.10)) !important;
        box-shadow:
          inset 0 1px 0 rgba(255,255,255,.44),
          inset 0 -1px 0 rgba(0,0,0,.08),
          0 8px 18px rgba(14,101,128,.13) !important;
      }

      @media (hover:hover) {
        .bottom-nav .nav-btn:not(.active):hover {
          background: rgba(255,255,255,.10) !important;
        }
      }

      @media (max-width: 360px) {
        .bottom-nav {
          width: min(92vw, 400px) !important;
        }
        .bottom-nav .nav-btn em {
          font-size: 10px !important;
        }
      }
    `;
    document.head.appendChild(style);
  }

  function getNav() {
    return document.querySelector('.bottom-nav');
  }

  function getActiveButton() {
    const nav = getNav();
    return nav?.querySelector('.nav-btn.active') || null;
  }

  function scheduleSyncNavState() {
    cancelAnimationFrame(syncFrame);
    syncFrame = requestAnimationFrame(syncNavState);
  }

  function syncNavState() {
    const nav = getNav();
    if (!nav) return;
    const buttons = [...nav.querySelectorAll('.nav-btn')];
    const active = getActiveButton();
    const nextView = active?.dataset.view || '';
    const changed = nav.dataset.activeView !== nextView;

    buttons.forEach((button) => {
      const isActive = button === active;
      if (isActive) button.setAttribute('aria-current', 'page');
      else button.removeAttribute('aria-current');

      const label = button.querySelector('em')?.textContent?.trim() || button.dataset.view || '页面';
      const nextLabel = isActive ? `${label}，当前页` : `切换到${label}`;
      if (button.getAttribute('aria-label') !== nextLabel) button.setAttribute('aria-label', nextLabel);
    });

    nav.dataset.activeView = nextView;
    if (changed) window.dispatchEvent(new CustomEvent(NAV_SYNC_EVENT, { detail: { view: nextView } }));
  }

  function watchNav() {
    const nav = getNav();
    if (!nav || nav.dataset.navPolishObserved === 'true') return Boolean(nav);
    nav.dataset.navPolishObserved = 'true';
    navObserver?.disconnect();
    navObserver = new MutationObserver(scheduleSyncNavState);
    nav.querySelectorAll('.nav-btn').forEach((button) => {
      navObserver.observe(button, { attributes: true, attributeFilter: ['class'] });
    });
    nav.addEventListener('click', scheduleSyncNavState, { passive: true });
    scheduleSyncNavState();
    return true;
  }

  function boot() {
    if (document.documentElement.dataset.navigationPolishReady === 'true') return;
    document.documentElement.dataset.navigationPolishReady = 'true';
    installStyle();
    if (!watchNav()) {
      requestAnimationFrame(watchNav);
    }
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();
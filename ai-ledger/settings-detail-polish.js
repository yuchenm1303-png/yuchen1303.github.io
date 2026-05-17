(() => {
  'use strict';

  const STYLE_ID = 'settings-detail-polish-style';
  const DETAIL_ID = 'settingsGroupDetail';
  const PERF_SCRIPT_ID = 'settings-performance-polish-loader';
  let bodyObserver = null;
  let detailObserver = null;
  let syncFrame = 0;

  function scheduleSyncOpenState() {
    cancelAnimationFrame(syncFrame);
    syncFrame = requestAnimationFrame(syncOpenState);
  }

  function syncOpenState() {
    const isOpen = !!document.querySelector(`#${DETAIL_ID}.open`);
    document.body?.classList.toggle('settings-group-open', isOpen);
  }

  function loadPerformancePolish() {
    if (document.getElementById(PERF_SCRIPT_ID) || window.__settingsPerformancePolishLoaded) return;
    window.__settingsPerformancePolishLoaded = true;
    const script = document.createElement('script');
    script.id = PERF_SCRIPT_ID;
    script.src = './settings-performance-polish.js?v=20260517-1';
    script.defer = true;
    script.onerror = () => console.warn('[settings-detail-polish] settings-performance-polish.js failed to load');
    document.head.appendChild(script);
  }

  function installStyle() {
    const old = document.getElementById(STYLE_ID);
    if (old) old.remove();

    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      #settingsGroupList .settings-group-card {
        position: relative !important;
        overflow: hidden !important;
      }

      #settingsGroupList .settings-group-card::before,
      #settingsGroupList .settings-group-card::after {
        display: none !important;
        content: none !important;
      }

      #settingsGroupList .settings-group-card > *,
      #settingsGroupList .settings-group-icon,
      #settingsGroupList .settings-group-title,
      #settingsGroupList .settings-group-desc,
      #settingsGroupList .settings-group-arrow {
        position: relative !important;
        z-index: 3 !important;
        opacity: 1 !important;
        visibility: visible !important;
        -webkit-text-fill-color: currentColor !important;
      }

      #settingsGroupDetail {
        backdrop-filter: none !important;
        -webkit-backdrop-filter: none !important;
        contain: layout paint style !important;
      }

      #settingsGroupDetail .settings-group-sheet {
        position: relative !important;
        z-index: 96 !important;
        opacity: 1;
        visibility: visible !important;
        backdrop-filter: none !important;
        -webkit-backdrop-filter: none !important;
        contain: layout paint style !important;
        backface-visibility: hidden;
      }

      #settingsGroupDetail .settings-group-content {
        contain: layout paint style !important;
      }

      #settingsGroupDetail .settings-group-content > .glass-card,
      #settingsGroupDetail .settings-group-content .glass-card {
        backdrop-filter: none !important;
        -webkit-backdrop-filter: none !important;
        animation: none !important;
        transform: none !important;
      }

      #settingsGroupDetail .settings-group-content > .glass-card {
        background:
          linear-gradient(145deg, rgba(255,255,255,.080), rgba(255,255,255,.026) 48%, rgba(0,0,0,.026)),
          rgba(255,255,255,.040) !important;
        box-shadow:
          inset 0 1px 0 rgba(255,255,255,.18),
          0 8px 18px rgba(0,0,0,.12) !important;
      }

      #settingsGroupDetail .settings-group-content input,
      #settingsGroupDetail .settings-group-content select,
      #settingsGroupDetail .settings-group-content textarea,
      #settingsGroupDetail .settings-group-content button,
      #settingsGroupDetail .settings-group-content .account-row {
        backdrop-filter: none !important;
        -webkit-backdrop-filter: none !important;
      }

      #settingsGroupDetail .settings-group-sheet *,
      #settingsGroupDetail .settings-group-content * {
        visibility: visible;
      }

      body.settings-group-opening .app-shell,
      body.settings-group-open .app-shell {
        pointer-events: none !important;
      }

      body.settings-group-opening .bottom-nav,
      body.settings-group-open .bottom-nav,
      body.settings-group-opening .fab,
      body.settings-group-open .fab {
        opacity: 0 !important;
        visibility: hidden !important;
        pointer-events: none !important;
        transition: opacity 80ms ease !important;
      }

      body.settings-group-open .settings-group-detail,
      body.settings-group-open #settingsGroupDetail {
        opacity: 1 !important;
        visibility: visible !important;
      }

      body.settings-group-open .settings-group-detail .glass-card,
      body.settings-group-open #settingsGroupDetail .glass-card {
        opacity: 1 !important;
        visibility: visible !important;
      }

      @media (pointer: coarse), (max-width: 760px) {
        #settingsGroupDetail {
          backdrop-filter: none !important;
          -webkit-backdrop-filter: none !important;
        }

        #settingsGroupDetail .settings-group-sheet {
          max-height: 82vh !important;
          background:
            linear-gradient(145deg, rgba(255,255,255,.090), rgba(255,255,255,.034) 45%, rgba(0,0,0,.042)),
            rgba(17,28,54,.97) !important;
          backdrop-filter: none !important;
          -webkit-backdrop-filter: none !important;
        }
      }
    `;
    document.head.appendChild(style);
  }

  function observeDetail() {
    const detail = document.getElementById(DETAIL_ID);
    if (!detail || detail.dataset.polishObserved === '1') return Boolean(detail);
    detail.dataset.polishObserved = '1';
    detailObserver?.disconnect();
    detailObserver = new MutationObserver(scheduleSyncOpenState);
    detailObserver.observe(detail, { attributes: true, attributeFilter: ['class'] });
    scheduleSyncOpenState();
    return true;
  }

  function observeBodyUntilDetailExists() {
    if (observeDetail() || bodyObserver) return;
    bodyObserver = new MutationObserver(() => {
      if (observeDetail()) {
        bodyObserver.disconnect();
        bodyObserver = null;
      }
    });
    bodyObserver.observe(document.body, { childList: true, subtree: true });
  }

  function boot() {
    document.documentElement.dataset.settingsDetailPolishReady = 'true';
    installStyle();
    loadPerformancePolish();
    observeBodyUntilDetailExists();
    scheduleSyncOpenState();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();

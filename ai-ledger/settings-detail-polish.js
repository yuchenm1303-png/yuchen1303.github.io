(() => {
  'use strict';

  const STYLE_ID = 'settings-detail-polish-style';
  const DETAIL_ID = 'settingsGroupDetail';
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

  function installStyle() {
    if (document.getElementById(STYLE_ID)) return;

    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      #settingsGroupList .settings-group-card {
        position: relative !important;
        overflow: hidden !important;
      }

      #settingsGroupList .settings-group-card::before,
      #settingsGroupList .settings-group-card::after {
        pointer-events: none !important;
        z-index: 0 !important;
      }

      #settingsGroupList .settings-group-entry {
        position: relative !important;
        z-index: 3 !important;
        background: transparent !important;
        color: rgba(248,250,255,.96) !important;
        opacity: 1 !important;
        visibility: visible !important;
      }

      #settingsGroupList .settings-group-entry > *,
      #settingsGroupList .settings-group-icon,
      #settingsGroupList .settings-group-title,
      #settingsGroupList .settings-group-desc,
      #settingsGroupList .settings-group-arrow {
        position: relative !important;
        z-index: 4 !important;
        opacity: 1 !important;
        visibility: visible !important;
        -webkit-text-fill-color: currentColor !important;
      }

      #settingsGroupList .settings-group-title {
        color: rgba(248,250,255,.98) !important;
        text-shadow: 0 1px 2px rgba(0,0,0,.18);
      }

      #settingsGroupList .settings-group-desc {
        color: rgba(220,230,250,.76) !important;
      }

      #settingsGroupList .settings-group-icon {
        background: rgba(255,255,255,.072) !important;
      }

      #settingsGroupDetail {
        background: rgba(4, 8, 20, .86) !important;
        backdrop-filter: none !important;
        -webkit-backdrop-filter: none !important;
        contain: layout paint style !important;
      }

      #settingsGroupDetail.open {
        display: grid !important;
        place-items: end center !important;
        pointer-events: auto !important;
      }

      body.settings-group-open #settingsGroupDetail.open {
        animation: settingsOverlayStaticFade 90ms ease-out both !important;
      }

      #settingsGroupDetail .settings-group-sheet {
        position: relative !important;
        z-index: 96 !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.108), rgba(255,255,255,.040) 45%, rgba(0,0,0,.040)),
          rgba(17,28,54,.96) !important;
        border-color: rgba(255,255,255,.22) !important;
        box-shadow:
          0 18px 38px rgba(0,0,0,.34),
          inset 0 1px 0 rgba(255,255,255,.22),
          inset 0 -1px 0 rgba(0,0,0,.12) !important;
        opacity: 1 !important;
        visibility: visible !important;
        backdrop-filter: none !important;
        -webkit-backdrop-filter: none !important;
        contain: layout paint style !important;
        transform: translate3d(0,0,0);
        backface-visibility: hidden;
      }

      body.settings-group-open #settingsGroupDetail.open .settings-group-sheet {
        animation: settingsSheetStableSlide 200ms cubic-bezier(.18,.72,.26,1) both !important;
      }

      #settingsGroupDetail .settings-group-content {
        contain: layout paint style !important;
      }

      #settingsGroupDetail .settings-group-content > .glass-card,
      #settingsGroupDetail .settings-group-content .glass-card,
      #settingsGroupDetail .account-row,
      #settingsGroupDetail input,
      #settingsGroupDetail select,
      #settingsGroupDetail textarea,
      #settingsGroupDetail button {
        backdrop-filter: none !important;
        -webkit-backdrop-filter: none !important;
      }

      #settingsGroupDetail .settings-group-content > .glass-card {
        background:
          linear-gradient(145deg, rgba(255,255,255,.080), rgba(255,255,255,.026) 48%, rgba(0,0,0,.026)),
          rgba(255,255,255,.040) !important;
        box-shadow:
          inset 0 1px 0 rgba(255,255,255,.18),
          0 10px 22px rgba(0,0,0,.14) !important;
        animation: none !important;
      }

      #settingsGroupDetail .settings-group-sheet *,
      #settingsGroupDetail .settings-group-content * {
        visibility: visible;
      }

      body.settings-group-opening .app-shell,
      body.settings-group-open .app-shell {
        opacity: 0 !important;
        visibility: hidden !important;
        filter: none !important;
        pointer-events: none !important;
        transform: none !important;
        transition: none !important;
        animation: none !important;
      }

      body.settings-group-opening .bottom-nav,
      body.settings-group-open .bottom-nav,
      body.settings-group-opening .fab,
      body.settings-group-open .fab {
        opacity: 0 !important;
        visibility: hidden !important;
        pointer-events: none !important;
        transform: translateX(-50%) !important;
        transition: none !important;
        animation: none !important;
      }

      body.settings-group-opening .app-shell *,
      body.settings-group-open .app-shell * {
        backdrop-filter: none !important;
        -webkit-backdrop-filter: none !important;
        animation: none !important;
        transition: none !important;
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

      @keyframes settingsOverlayStaticFade {
        from { opacity: 0; }
        to { opacity: 1; }
      }

      @keyframes settingsSheetStableSlide {
        0% { opacity: .98; transform: translate3d(0, 6px, 0) scale(.997); }
        100% { opacity: 1; transform: translate3d(0, 0, 0) scale(1); }
      }

      @media (pointer: coarse), (max-width: 760px) {
        #settingsGroupDetail {
          background: rgba(4,8,20,.88) !important;
          backdrop-filter: none !important;
          -webkit-backdrop-filter: none !important;
        }

        #settingsGroupDetail .settings-group-sheet {
          max-height: 84vh !important;
          background:
            linear-gradient(145deg, rgba(255,255,255,.096), rgba(255,255,255,.036) 45%, rgba(0,0,0,.044)),
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
    if (document.documentElement.dataset.settingsDetailPolishReady === 'true') return;
    document.documentElement.dataset.settingsDetailPolishReady = 'true';
    installStyle();
    observeBodyUntilDetailExists();
    scheduleSyncOpenState();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();
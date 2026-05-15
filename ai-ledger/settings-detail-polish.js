(() => {
  const STYLE_ID = 'settings-detail-polish-style';
  const DETAIL_ID = 'settingsGroupDetail';

  function syncOpenState() {
    const isOpen = !!document.querySelector(`#${DETAIL_ID}.open`);
    document.body.classList.toggle('settings-group-open', isOpen);
  }

  function installStyle() {
    const old = document.querySelector(`#${STYLE_ID}`);
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
        background:
          radial-gradient(circle at 50% 15%, rgba(255,255,255,.08), transparent 38%),
          rgba(3,7,18,.72) !important;
        backdrop-filter: blur(6px) saturate(1.08) !important;
        -webkit-backdrop-filter: blur(6px) saturate(1.08) !important;
      }

      #settingsGroupDetail.open {
        display: grid !important;
        place-items: end center !important;
        pointer-events: auto !important;
      }

      #settingsGroupDetail .settings-group-sheet {
        position: relative !important;
        z-index: 96 !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.115), rgba(255,255,255,.045) 45%, rgba(0,0,0,.035)),
          rgba(20,31,58,.88) !important;
        border-color: rgba(255,255,255,.24) !important;
        box-shadow:
          0 24px 56px rgba(0,0,0,.42),
          inset 0 1px 0 rgba(255,255,255,.26),
          inset 0 -1px 0 rgba(0,0,0,.12) !important;
        opacity: 1 !important;
        visibility: visible !important;
      }

      #settingsGroupDetail .settings-group-sheet *,
      #settingsGroupDetail .settings-group-content * {
        opacity: 1;
        visibility: visible;
      }

      body.settings-group-open .app-shell {
        opacity: .34 !important;
        filter: blur(1.5px) saturate(.9) brightness(.82) !important;
        pointer-events: none !important;
        transform: none !important;
      }

      body.settings-group-open .bottom-nav,
      body.settings-group-open .fab {
        opacity: 0 !important;
        visibility: hidden !important;
        pointer-events: none !important;
        transform: translateX(-50%) !important;
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
          background: rgba(4,8,20,.78) !important;
          backdrop-filter: blur(3px) saturate(1.04) !important;
          -webkit-backdrop-filter: blur(3px) saturate(1.04) !important;
        }

        #settingsGroupDetail .settings-group-sheet {
          max-height: 84vh !important;
          background:
            linear-gradient(145deg, rgba(255,255,255,.105), rgba(255,255,255,.040) 45%, rgba(0,0,0,.040)),
            rgba(17,28,54,.92) !important;
          backdrop-filter: none !important;
          -webkit-backdrop-filter: none !important;
        }

        body.settings-group-open .app-shell {
          opacity: .22 !important;
          filter: none !important;
        }
      }
    `;
    document.head.appendChild(style);
  }

  function observeDetail() {
    const detail = document.querySelector(`#${DETAIL_ID}`);
    if (!detail || detail.dataset.polishObserved === '1') return;
    detail.dataset.polishObserved = '1';
    const observer = new MutationObserver(syncOpenState);
    observer.observe(detail, { attributes: true, attributeFilter: ['class'] });
    syncOpenState();
  }

  function boot() {
    installStyle();
    observeDetail();
    setTimeout(() => { installStyle(); observeDetail(); syncOpenState(); }, 300);
    setTimeout(() => { installStyle(); observeDetail(); syncOpenState(); }, 1200);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();

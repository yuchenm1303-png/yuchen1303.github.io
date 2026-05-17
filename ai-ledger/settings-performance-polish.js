(() => {
  'use strict';

  const STYLE_ID = 'settings-performance-polish-style';
  const PERF_CLASS = 'settings-perf-mode';
  const DETAIL_ID = 'settingsGroupDetail';
  let detailObserver = null;
  let waitObserver = null;

  function installStyle() {
    document.getElementById(STYLE_ID)?.remove();
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      :root{
        --settings-open-duration: 165ms;
        --settings-close-duration: 120ms;
        --settings-ease-out: cubic-bezier(.18,.86,.22,1);
        --settings-ease-in: cubic-bezier(.28,.02,.36,1);
      }

      #view-settings{
        content-visibility:visible;
        contain-intrinsic-size:auto;
      }

      .settings-group-list{
        transform:translateZ(0);
        will-change:auto;
      }

      .settings-group-card{
        transform:translate3d(0,0,0);
        backface-visibility:hidden;
        will-change:auto;
        contain:layout paint style;
      }

      .settings-group-card .settings-group-icon,
      .settings-group-card .settings-group-arrow{
        transform:translateZ(0);
      }

      .settings-group-detail{
        isolation:isolate;
        touch-action:pan-y;
        contain:layout paint style;
        will-change:auto;
      }

      .settings-group-detail.open{
        animation:settingsOverlayIn var(--settings-open-duration) ease both;
      }

      .settings-group-detail:not(.open){
        animation:settingsOverlayOut var(--settings-close-duration) ease both;
      }

      .settings-group-sheet{
        transform:translate3d(0,18px,0) scale(.985)!important;
        opacity:.001!important;
        will-change:auto;
        contain:layout paint style;
        content-visibility:visible;
        contain-intrinsic-size:auto;
        transform-origin:50% 100%;
        overscroll-behavior:contain;
      }

      .settings-group-detail.open.ready .settings-group-sheet,
      .settings-group-detail.open.content-ready .settings-group-sheet{
        transform:translate3d(0,0,0) scale(1)!important;
        opacity:1!important;
        will-change:transform,opacity;
        transition:transform var(--settings-open-duration) var(--settings-ease-out), opacity 115ms ease!important;
      }

      .settings-group-detail:not(.open) .settings-group-sheet{
        transform:translate3d(0,14px,0) scale(.992)!important;
        opacity:0!important;
        transition:transform var(--settings-close-duration) var(--settings-ease-in), opacity 90ms ease!important;
      }

      .settings-group-content{
        contain:layout paint style;
        transform:translateZ(0);
      }

      .settings-group-content > .glass-card,
      .settings-group-content > section{
        contain:layout paint style;
        transform:translateZ(0);
        backface-visibility:hidden;
      }

      body.settings-group-opening .glass-card,
      body.settings-group-opening .summary-chip,
      body.settings-group-opening .bottom-nav,
      body.settings-group-open .bottom-nav{
        will-change:transform,opacity;
      }

      body.settings-group-open #view-settings .settings-group-list{
        pointer-events:none;
      }

      body.settings-group-open .bottom-nav{
        transform:translate3d(0,6px,0) scale(.992);
        opacity:.72;
        transition:transform 140ms ease, opacity 140ms ease;
      }

      body.settings-perf-low-glass .settings-group-content .glass-card,
      body.settings-perf-low-glass .settings-group-sheet{
        -webkit-backdrop-filter:none!important;
        backdrop-filter:none!important;
      }

      body.settings-perf-low-glass .settings-group-content .glass-card{
        box-shadow:0 8px 18px rgba(0,0,0,.16), inset 0 1px 0 rgba(255,255,255,.16)!important;
      }

      @keyframes settingsOverlayIn{
        from{opacity:0;background:rgba(4,8,20,0)}
        to{opacity:1;background:rgba(4,8,20,.58)}
      }

      @keyframes settingsOverlayOut{
        from{opacity:1;background:rgba(4,8,20,.58)}
        to{opacity:0;background:rgba(4,8,20,0)}
      }

      @media(max-width:720px){
        .settings-group-card{
          min-height:82px!important;
          padding:12px 14px!important;
          grid-template-columns:42px 1fr 18px!important;
          gap:12px!important;
        }
        .settings-group-icon{width:42px!important;height:42px!important;border-radius:15px!important}
        .settings-group-title{font-size:17px!important}
        .settings-group-desc{font-size:12.5px!important}
        .settings-group-sheet{
          max-height:min(80vh,720px)!important;
          padding:13px!important;
        }
      }

      @media(prefers-reduced-motion:reduce){
        .settings-group-detail,
        .settings-group-sheet,
        .settings-group-card,
        .bottom-nav{
          animation:none!important;
          transition:none!important;
        }
      }
    `;
    document.head.appendChild(style);
  }

  function isLowEndAndroidWebView() {
    const ua = navigator.userAgent || '';
    const memory = navigator.deviceMemory || 4;
    const cores = navigator.hardwareConcurrency || 4;
    return /Android/i.test(ua) && (memory <= 4 || cores <= 4 || /wv\)/i.test(ua));
  }

  function setPerfMode(open) {
    document.body.classList.toggle(PERF_CLASS, open);
    if (open && isLowEndAndroidWebView()) document.body.classList.add('settings-perf-low-glass');
    else if (!open) window.setTimeout(() => document.body.classList.remove('settings-perf-low-glass'), 160);
  }

  function syncFromDetail(detail = document.getElementById(DETAIL_ID)) {
    setPerfMode(Boolean(detail?.classList.contains('open')));
  }

  function observeDetail(detail) {
    if (!detail || detail.__settingsPerfPatched) return false;
    detail.__settingsPerfPatched = true;
    detailObserver?.disconnect();
    detailObserver = new MutationObserver(() => syncFromDetail(detail));
    detailObserver.observe(detail, { attributes:true, attributeFilter:['class'] });
    detail.addEventListener('transitionstart', () => syncFromDetail(detail), true);
    detail.addEventListener('animationstart', () => syncFromDetail(detail), true);
    detail.addEventListener('transitionend', () => syncFromDetail(detail), true);
    syncFromDetail(detail);
    return true;
  }

  function installObserver() {
    if (observeDetail(document.getElementById(DETAIL_ID))) return;
    waitObserver?.disconnect();
    waitObserver = new MutationObserver(() => {
      if (observeDetail(document.getElementById(DETAIL_ID))) {
        waitObserver.disconnect();
        waitObserver = null;
      }
    });
    waitObserver.observe(document.body, { childList:true, subtree:false });
    window.setTimeout(() => observeDetail(document.getElementById(DETAIL_ID)), 500);
    window.setTimeout(() => observeDetail(document.getElementById(DETAIL_ID)), 1500);
  }

  function boot() {
    installStyle();
    installObserver();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once:true });
  else boot();
})();

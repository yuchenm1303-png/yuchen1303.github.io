(() => {
  'use strict';

  const STYLE_ID = 'app-startup-stability-style';
  const READY_CLASS = 'app-startup-ready';

  function installStyle() {
    const old = document.getElementById(STYLE_ID);
    if (old) old.remove();
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      html:not(.${READY_CLASS}) body{
        background:#0b1024!important;
      }
      html:not(.${READY_CLASS}) .app-shell,
      html:not(.${READY_CLASS}) .bottom-nav{
        opacity:0!important;
        transform:translate3d(0,8px,0) scale(.992)!important;
        pointer-events:none!important;
      }
      html.${READY_CLASS} .app-shell,
      html.${READY_CLASS} .bottom-nav{
        opacity:1;
        transition:opacity .22s ease, transform .22s ease;
      }
      .app-startup-curtain{
        position:fixed;
        inset:0;
        z-index:2147483000;
        pointer-events:none;
        background:
          radial-gradient(circle at 22% 10%,rgba(116,150,255,.24),transparent 34%),
          radial-gradient(circle at 78% 90%,rgba(190,135,255,.20),transparent 38%),
          linear-gradient(180deg,#101936,#13152c 58%,#241e3a);
        opacity:1;
        transition:opacity .24s ease;
      }
      html.${READY_CLASS} .app-startup-curtain{
        opacity:0;
      }
    `;
    document.head.appendChild(style);
  }

  function markReady() {
    const root = document.documentElement;
    root.classList.add(READY_CLASS);
    window.setTimeout(() => {
      document.querySelector('.app-startup-curtain')?.remove();
    }, 320);
  }

  function boot() {
    installStyle();
    if (!document.querySelector('.app-startup-curtain')) {
      const curtain = document.createElement('div');
      curtain.className = 'app-startup-curtain';
      document.body.appendChild(curtain);
    }
    const release = () => {
      requestAnimationFrame(() => requestAnimationFrame(markReady));
    };
    if (document.readyState === 'complete') release();
    else window.addEventListener('load', release, { once: true });
    window.setTimeout(markReady, 900);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();

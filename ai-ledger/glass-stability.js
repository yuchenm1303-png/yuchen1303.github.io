(() => {
  const STYLE_ID = 'glass-stability-style';

  function installStableGlassStyle() {
    const old = document.querySelector(`#${STYLE_ID}`);
    if (old) old.remove();

    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      @media (pointer: coarse), (max-width: 760px) {
        html, body {
          background-attachment: scroll !important;
          transform: none !important;
        }

        body::before,
        body::after,
        .scene-backdrop::before,
        .scene-backdrop::after,
        .ambient {
          animation: none !important;
          transform: none !important;
        }

        body.detail-open .app-shell,
        body.detail-open .bottom-nav,
        body.detail-open .fab {
          transform: none !important;
          opacity: .92 !important;
        }

        .glass-card,
        .chat-shell,
        .summary-card,
        .metric-card,
        .chart-card,
        .tool-card,
        .record-item,
        .draft-card,
        .draft-item,
        .add-sheet,
        .auth-sheet,
        .mobile-command-card,
        .tools-panel-card,
        .account-row,
        .summary-chip,
        .tag-btn,
        .range-chip,
        .ghost-btn,
        .mini-ghost-btn,
        .summary-box,
        .budget-pill,
        .auth-tab,
        .icon-btn,
        .delete-btn,
        .chat-row.assistant .chat-bubble,
        .tools-back,
        .account-pill,
        .bottom-nav,
        .appearance-detail-panel,
        .settings-group-sheet,
        .appearance-preview,
        input,
        textarea,
        select {
          backdrop-filter: none !important;
          -webkit-backdrop-filter: none !important;
          will-change: auto !important;
          contain: none !important;
          isolation: auto !important;
        }

        .glass-card,
        .chat-shell,
        .summary-card,
        .metric-card,
        .chart-card,
        .tool-card,
        .auth-sheet,
        .mobile-command-card,
        .tools-panel-card,
        .account-row,
        .appearance-detail-panel,
        .settings-group-sheet {
          background:
            linear-gradient(145deg, rgba(255,255,255,.078), rgba(255,255,255,.026) 44%, rgba(0,0,0,.026)),
            radial-gradient(circle at 20% 0%, rgba(255,255,255,.075), transparent 46%),
            rgba(255,255,255,var(--assistant-glass-panel-alpha,.044)) !important;
          border-color: rgba(255,255,255,.18) !important;
          box-shadow:
            0 14px 28px rgba(0,0,0,.16),
            inset 0 .8px 0 rgba(255,255,255,.28),
            inset 0 -.8px 0 rgba(0,0,0,.07) !important;
        }

        .summary-chip,
        .record-item,
        .draft-card,
        .draft-item,
        textarea,
        input,
        select,
        .tag-btn,
        .range-chip,
        .ghost-btn,
        .mini-ghost-btn,
        .summary-box,
        .budget-pill,
        .auth-tab,
        .icon-btn,
        .delete-btn,
        .chat-row.assistant .chat-bubble,
        .tools-back,
        .account-pill,
        .appearance-preview {
          background:
            linear-gradient(145deg, rgba(255,255,255,.090), rgba(255,255,255,.020) 52%, rgba(0,0,0,.018)),
            rgba(255,255,255,var(--assistant-glass-control-alpha,.050)) !important;
          border-color: rgba(255,255,255,.16) !important;
          box-shadow:
            0 7px 16px rgba(0,0,0,.11),
            inset 0 .7px 0 rgba(255,255,255,.26),
            inset 0 -.7px 0 rgba(0,0,0,.065) !important;
        }

        .bottom-nav {
          background:
            linear-gradient(145deg, rgba(255,255,255,.105), rgba(255,255,255,.028) 54%, rgba(0,0,0,.030)),
            rgba(255,255,255,var(--assistant-glass-nav-alpha,.050)) !important;
          border-color: rgba(255,255,255,.20) !important;
          box-shadow:
            0 14px 26px rgba(0,0,0,.18),
            inset 0 .8px 0 rgba(255,255,255,.30),
            inset 0 -.8px 0 rgba(0,0,0,.08) !important;
        }

        .glass-card::before,
        .glass-card::after,
        .bottom-nav::before,
        .bottom-nav::after,
        .summary-chip::before,
        .summary-chip::after,
        .record-item::before,
        .record-item::after,
        .draft-card::before,
        .draft-card::after,
        .draft-item::before,
        .draft-item::after,
        .tag-btn::before,
        .tag-btn::after,
        .range-chip::before,
        .range-chip::after,
        .ghost-btn::before,
        .ghost-btn::after,
        .mini-ghost-btn::before,
        .mini-ghost-btn::after,
        .summary-box::before,
        .summary-box::after,
        .auth-tab::before,
        .auth-tab::after,
        .icon-btn::before,
        .icon-btn::after,
        .delete-btn::before,
        .delete-btn::after,
        .tools-panel-card::before,
        .tools-panel-card::after,
        .tools-back::before,
        .tools-back::after {
          animation: none !important;
          filter: none !important;
          transform: none !important;
          opacity: .18 !important;
        }

        .reveal,
        .view.active .reveal,
        .appearance-plus-card {
          opacity: 1 !important;
          transform: none !important;
          animation: none !important;
          visibility: visible !important;
        }

        .interactive,
        button,
        .record-item,
        .summary-chip,
        .summary-box,
        .metric-card,
        .chart-card,
        .summary-card,
        .account-row,
        .draft-card,
        .draft-item,
        .auth-tab {
          transition: transform .10s ease, opacity .10s ease !important;
        }
      }
    `;
    document.head.appendChild(style);
    document.body?.classList.add('stable-glass-rendering');
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', installStableGlassStyle);
  } else {
    installStableGlassStyle();
  }

  window.setTimeout(installStableGlassStyle, 300);
  window.setTimeout(installStableGlassStyle, 1200);
})();

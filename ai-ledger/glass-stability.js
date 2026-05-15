(() => {
  const STYLE_ID = 'glass-stability-style';

  function installStableGlassStyle() {
    document.querySelector(`#${STYLE_ID}`)?.remove();

    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      html,
      body {
        background-attachment: scroll !important;
      }

      .stable-glass-rendering .view .reveal,
      .stable-glass-rendering .view.active .reveal,
      .stable-glass-rendering .appearance-plus-card {
        opacity: 1 !important;
        transform: none !important;
        animation: none !important;
        visibility: visible !important;
      }

      .stable-glass-rendering .view {
        content-visibility: visible !important;
      }

      .stable-glass-rendering .chat-messages,
      .stable-glass-rendering .record-list,
      .stable-glass-rendering .tools-panel,
      .stable-glass-rendering .tools-grid {
        contain: layout paint;
      }

      .stable-glass-rendering .chart-wrap,
      .stable-glass-rendering canvas {
        contain: layout paint size;
      }

      .stable-glass-rendering .ambient,
      .stable-glass-rendering body::before,
      .stable-glass-rendering body::after {
        will-change: auto !important;
      }

      @media (pointer: coarse), (max-width: 760px) {
        body::before,
        body::after,
        .scene-backdrop::before,
        .scene-backdrop::after,
        .ambient {
          animation: none !important;
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
        .settings-group-sheet,
        .bottom-nav {
          will-change: auto !important;
          isolation: isolate;
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
          backdrop-filter: none !important;
          -webkit-backdrop-filter: none !important;
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
          backdrop-filter: blur(18px) saturate(130%) brightness(1.05) !important;
          -webkit-backdrop-filter: blur(18px) saturate(130%) brightness(1.05) !important;
        }

        .bottom-nav {
          backdrop-filter: blur(20px) saturate(132%) brightness(1.06) !important;
          -webkit-backdrop-filter: blur(20px) saturate(132%) brightness(1.06) !important;
        }

        .detail-overlay,
        .auth-overlay,
        .appearance-detail-overlay,
        .settings-group-detail {
          backdrop-filter: blur(14px) saturate(112%) !important;
          -webkit-backdrop-filter: blur(14px) saturate(112%) !important;
        }
      }

      body.assistant-android-glass .glass-card,
      body.assistant-android-glass .chat-shell,
      body.assistant-android-glass .summary-card,
      body.assistant-android-glass .metric-card,
      body.assistant-android-glass .chart-card,
      body.assistant-android-glass .tool-card,
      body.assistant-android-glass .auth-sheet,
      body.assistant-android-glass .mobile-command-card,
      body.assistant-android-glass .tools-panel-card,
      body.assistant-android-glass .account-row,
      body.assistant-android-glass .appearance-detail-panel,
      body.assistant-android-glass .settings-group-sheet {
        transform: translateZ(0);
      }

      body.assistant-lite-motion .glass-card,
      body.assistant-lite-motion .chat-shell,
      body.assistant-lite-motion .summary-card,
      body.assistant-lite-motion .metric-card,
      body.assistant-lite-motion .chart-card,
      body.assistant-lite-motion .tool-card,
      body.assistant-lite-motion .auth-sheet,
      body.assistant-lite-motion .mobile-command-card,
      body.assistant-lite-motion .tools-panel-card,
      body.assistant-lite-motion .account-row,
      body.assistant-lite-motion .appearance-detail-panel,
      body.assistant-lite-motion .settings-group-sheet,
      body.assistant-lite-motion .bottom-nav {
        backdrop-filter: none !important;
        -webkit-backdrop-filter: none !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.090), rgba(255,255,255,.020) 52%, rgba(0,0,0,.018)),
          rgba(255,255,255,var(--assistant-glass-panel-alpha,.050)) !important;
      }

      body.assistant-lite-motion .glass-card::before,
      body.assistant-lite-motion .glass-card::after,
      body.assistant-lite-motion .bottom-nav::before,
      body.assistant-lite-motion .bottom-nav::after,
      body.assistant-lite-motion .summary-chip::before,
      body.assistant-lite-motion .summary-chip::after,
      body.assistant-lite-motion .record-item::before,
      body.assistant-lite-motion .record-item::after,
      body.assistant-lite-motion .draft-card::before,
      body.assistant-lite-motion .draft-card::after,
      body.assistant-lite-motion .draft-item::before,
      body.assistant-lite-motion .draft-item::after,
      body.assistant-lite-motion .tag-btn::before,
      body.assistant-lite-motion .tag-btn::after,
      body.assistant-lite-motion .range-chip::before,
      body.assistant-lite-motion .range-chip::after,
      body.assistant-lite-motion .ghost-btn::before,
      body.assistant-lite-motion .ghost-btn::after,
      body.assistant-lite-motion .mini-ghost-btn::before,
      body.assistant-lite-motion .mini-ghost-btn::after,
      body.assistant-lite-motion .summary-box::before,
      body.assistant-lite-motion .summary-box::after,
      body.assistant-lite-motion .auth-tab::before,
      body.assistant-lite-motion .auth-tab::after,
      body.assistant-lite-motion .icon-btn::before,
      body.assistant-lite-motion .icon-btn::after,
      body.assistant-lite-motion .delete-btn::before,
      body.assistant-lite-motion .delete-btn::after,
      body.assistant-lite-motion .tools-panel-card::before,
      body.assistant-lite-motion .tools-panel-card::after,
      body.assistant-lite-motion .tools-back::before,
      body.assistant-lite-motion .tools-back::after {
        animation: none !important;
        filter: none !important;
        opacity: .18 !important;
      }

      body.detail-open .app-shell,
      body.detail-open .bottom-nav,
      body.detail-open .fab {
        opacity: .92 !important;
      }

      body.assistant-motion-off *,
      body.assistant-motion-off *::before,
      body.assistant-motion-off *::after {
        animation-duration: .001ms !important;
        animation-iteration-count: 1 !important;
        transition-duration: .001ms !important;
        scroll-behavior: auto !important;
      }
    `;
    document.head.appendChild(style);
    document.body?.classList.add('stable-glass-rendering');
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', installStableGlassStyle, { once: true });
  } else {
    installStableGlassStyle();
  }

  window.setTimeout(installStableGlassStyle, 420);
})();
(() => {
  const STYLE_ID = 'layout-stability-polish-style';

  function installStyle() {
    const old = document.querySelector(`#${STYLE_ID}`);
    if (old) old.remove();

    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      html, body {
        overflow-x: hidden !important;
      }

      .app-shell,
      .view,
      .glass-card,
      .chat-shell,
      .tools-panel,
      .tools-home,
      .settings-group-sheet {
        box-sizing: border-box !important;
      }

      .view {
        min-width: 0 !important;
      }

      .glass-card,
      .settings-group-card,
      .tool-card,
      .mobile-command-card,
      .tools-panel-card,
      .chat-shell {
        max-width: 100% !important;
      }

      .chat-shell {
        grid-template-rows: auto minmax(260px, 1fr) auto auto !important;
        min-height: min(620px, calc(100vh - 168px)) !important;
        overflow: hidden !important;
      }

      .chat-messages {
        min-height: 260px !important;
        max-height: none !important;
        overflow-y: auto !important;
        overscroll-behavior: contain !important;
      }

      .chat-composer {
        grid-template-columns: minmax(0, 1fr) 48px !important;
        align-items: center !important;
      }

      .chat-composer textarea,
      #aiInput {
        width: 100% !important;
        min-width: 0 !important;
        min-height: 48px !important;
        max-height: 104px !important;
        writing-mode: horizontal-tb !important;
        text-orientation: mixed !important;
        resize: none !important;
        overflow-y: auto !important;
      }

      .send-btn,
      #aiAddBtn {
        width: 48px !important;
        height: 48px !important;
        min-width: 48px !important;
        place-self: center !important;
      }

      .chat-tags,
      .quick-tags {
        display: flex !important;
        flex-wrap: wrap !important;
        align-items: center !important;
        gap: 9px !important;
      }

      .tag-btn,
      .range-chip,
      .ghost-btn,
      .mini-ghost-btn,
      .primary-btn,
      .danger-btn,
      .bg-option,
      .appearance-toggle,
      .tools-back {
        white-space: nowrap !important;
        writing-mode: horizontal-tb !important;
        text-orientation: mixed !important;
      }

      .settings-group-card,
      .tool-card {
        contain: layout paint style !important;
      }

      .tools-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
      }

      .tool-card {
        min-width: 0 !important;
        overflow: hidden !important;
      }

      .tool-card h3,
      .tool-card p,
      .settings-group-title,
      .settings-group-desc {
        overflow-wrap: anywhere !important;
      }

      .settings-group-sheet {
        overflow-x: hidden !important;
        overscroll-behavior: contain !important;
      }

      .settings-group-content input,
      .settings-group-content select,
      .settings-group-content textarea {
        max-width: 100% !important;
        min-width: 0 !important;
      }

      body.settings-group-opening,
      body.settings-group-open {
        overflow: hidden !important;
      }

      @media (pointer: coarse), (max-width: 760px) {
        .chat-shell {
          min-height: min(600px, calc(100vh - 156px)) !important;
        }

        .chat-messages {
          min-height: 245px !important;
        }

        .tools-grid {
          gap: 10px !important;
        }
      }

      @media (max-width: 390px) {
        .tools-grid {
          grid-template-columns: 1fr !important;
        }

        .chat-shell {
          min-height: min(580px, calc(100vh - 150px)) !important;
        }
      }
    `;
    document.head.appendChild(style);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', installStyle);
  else installStyle();
})();
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
        grid-template-rows: auto minmax(300px, 1fr) auto auto auto !important;
        min-height: min(640px, calc(100vh - 168px)) !important;
        overflow: hidden !important;
      }

      .chat-messages {
        min-height: 300px !important;
        max-height: none !important;
        overflow-y: auto !important;
        overscroll-behavior: contain !important;
      }

      .chat-composer {
        display: flex !important;
        align-items: center !important;
        gap: 10px !important;
        width: 100% !important;
        min-width: 0 !important;
        padding: 8px !important;
        border: 1px solid rgba(255,255,255,.28) !important;
        border-radius: 26px !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.080), rgba(255,255,255,.026) 48%, rgba(0,0,0,.018)),
          rgba(255,255,255,.052) !important;
        box-shadow:
          inset 0 1px 0 rgba(255,255,255,.20),
          0 10px 22px rgba(0,0,0,.10) !important;
      }

      .chat-composer textarea,
      #aiInput {
        order: 2 !important;
        flex: 1 1 auto !important;
        width: auto !important;
        min-width: 0 !important;
        min-height: 44px !important;
        max-height: 96px !important;
        padding: 11px 12px !important;
        border: 0 !important;
        border-radius: 18px !important;
        background: transparent !important;
        box-shadow: none !important;
        writing-mode: horizontal-tb !important;
        text-orientation: mixed !important;
        resize: none !important;
        overflow-y: auto !important;
        white-space: pre-wrap !important;
        word-break: break-word !important;
        line-height: 1.42 !important;
      }

      .chat-composer textarea::placeholder,
      #aiInput::placeholder {
        writing-mode: horizontal-tb !important;
        text-orientation: mixed !important;
        white-space: normal !important;
      }

      .chat-composer button {
        flex: 0 0 46px !important;
        width: 46px !important;
        height: 46px !important;
        min-width: 46px !important;
        min-height: 46px !important;
        border-radius: 18px !important;
        display: grid !important;
        place-items: center !important;
        writing-mode: horizontal-tb !important;
        text-orientation: mixed !important;
      }

      .chat-composer button:not(#aiAddBtn):not(.send-btn) {
        order: 1 !important;
      }

      .send-btn,
      #aiAddBtn {
        order: 3 !important;
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
          min-height: min(620px, calc(100vh - 156px)) !important;
        }

        .chat-messages {
          min-height: 275px !important;
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
          min-height: min(590px, calc(100vh - 150px)) !important;
        }

        .chat-composer {
          gap: 8px !important;
          padding: 7px !important;
        }

        .chat-composer button {
          flex-basis: 44px !important;
          width: 44px !important;
          height: 44px !important;
          min-width: 44px !important;
          min-height: 44px !important;
        }
      }
    `;
    document.head.appendChild(style);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', installStyle);
  else installStyle();
})();
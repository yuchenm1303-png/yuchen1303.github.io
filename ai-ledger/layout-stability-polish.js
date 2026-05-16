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

      body {
        padding-bottom: calc(146px + env(safe-area-inset-bottom)) !important;
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

      .app-shell {
        padding-bottom: calc(164px + env(safe-area-inset-bottom)) !important;
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
        display: grid !important;
        grid-template-rows: auto minmax(260px, 1fr) auto auto auto !important;
        min-height: min(640px, calc(100vh - 168px)) !important;
        overflow: visible !important;
        padding-bottom: 18px !important;
      }

      .chat-messages {
        min-height: 260px !important;
        max-height: none !important;
        overflow-y: auto !important;
        overscroll-behavior: contain !important;
        padding-bottom: 10px !important;
      }

      .chat-composer {
        display: flex !important;
        align-items: center !important;
        gap: 10px !important;
        width: 100% !important;
        min-width: 0 !important;
        padding: 8px !important;
        border: 1px solid rgba(255,255,255,.30) !important;
        border-radius: 26px !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.10), rgba(255,255,255,.04) 48%, rgba(0,0,0,.018)),
          rgba(255,255,255,.06) !important;
        box-shadow:
          inset 0 1px 0 rgba(255,255,255,.22),
          0 10px 22px rgba(0,0,0,.10) !important;
        z-index: 6 !important;
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

      .attachment-tray {
        grid-row: auto !important;
        margin: 10px 0 10px !important;
      }

      body.has-chat-attachments .chat-shell {
        padding-bottom: 24px !important;
      }

      .chat-tags,
      .quick-tags {
        display: flex !important;
        flex-wrap: wrap !important;
        align-items: center !important;
        gap: 9px !important;
      }

      .bottom-nav {
        grid-template-columns: repeat(3, minmax(0, 1fr)) !important;
        width: min(90vw, 470px) !important;
        z-index: 80 !important;
        bottom: calc(12px + env(safe-area-inset-bottom)) !important;
        background: rgba(255,255,255,.16) !important;
        border-color: rgba(255,255,255,.26) !important;
        box-shadow: 0 18px 40px rgba(0,0,0,.20), inset 0 1px 0 rgba(255,255,255,.22) !important;
      }

      .bottom-nav .nav-btn {
        min-width: 0 !important;
        color: rgba(235,241,255,.64) !important;
      }

      .bottom-nav .nav-btn.active {
        color: #ffffff !important;
        background: linear-gradient(135deg, rgba(170,205,255,.38), rgba(132,95,255,.32)) !important;
        border: 1px solid rgba(255,255,255,.26) !important;
        box-shadow: inset 0 1px 0 rgba(255,255,255,.30), 0 10px 24px rgba(75,90,180,.18) !important;
      }

      .bottom-nav .nav-btn span,
      .bottom-nav .nav-btn em {
        opacity: 1 !important;
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
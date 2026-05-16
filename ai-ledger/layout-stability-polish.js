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

      .chat-row.assistant .chat-bubble.chat-response:has(.draft-card),
      .chat-row.assistant .chat-bubble.chat-response:has(.mobile-command-card),
      .chat-row.assistant .chat-bubble.chat-response.draft-bubble,
      .chat-row.assistant .chat-bubble.chat-response.mobile-command-bubble {
        width: fit-content !important;
        max-width: min(80%, 318px) !important;
        padding: 6px !important;
        gap: 6px !important;
        background: transparent !important;
        border-color: transparent !important;
        box-shadow: none !important;
        backdrop-filter: none !important;
        -webkit-backdrop-filter: none !important;
      }

      .draft-card,
      .mobile-command-card {
        width: min(100%, 226px) !important;
        inline-size: min(100%, 226px) !important;
        max-width: 226px !important;
        min-height: 0 !important;
        height: auto !important;
        aspect-ratio: auto !important;
        display: grid !important;
        align-content: start !important;
        gap: 6px !important;
        margin: 2px 0 0 !important;
        padding: 9px !important;
        border: 1px solid rgba(255,255,255,.22) !important;
        border-radius: 15px !important;
        color: rgba(248,250,255,.95) !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.116), rgba(255,255,255,.040) 58%, rgba(0,0,0,.030)),
          rgba(18,30,55,.74) !important;
        box-shadow:
          0 5px 12px rgba(0,0,0,.095),
          inset 0 .7px 0 rgba(255,255,255,.22) !important;
        backdrop-filter: none !important;
        -webkit-backdrop-filter: none !important;
        overflow: hidden !important;
      }

      .draft-card::before,
      .draft-card::after,
      .mobile-command-card::before,
      .mobile-command-card::after {
        display: none !important;
        content: none !important;
      }

      .draft-head,
      .mobile-command-head {
        display: flex !important;
        align-items: center !important;
        justify-content: space-between !important;
        gap: 8px !important;
        margin: 0 !important;
        padding: 0 0 4px !important;
        min-height: 0 !important;
      }

      .draft-head strong,
      .mobile-command-title {
        min-width: 0 !important;
        color: rgba(252,253,255,.98) !important;
        font-size: 14px !important;
        line-height: 1.16 !important;
        font-weight: 900 !important;
        overflow-wrap: anywhere !important;
      }

      .draft-head span,
      .mobile-command-status {
        flex: 0 0 auto !important;
        padding: 3px 8px !important;
        border-radius: 999px !important;
        font-size: 11px !important;
        line-height: 1.12 !important;
        font-weight: 850 !important;
        color: rgba(132,255,236,.94) !important;
        background: rgba(98,240,218,.13) !important;
      }

      .draft-record,
      .mobile-command-row {
        display: grid !important;
        grid-template-columns: minmax(0,1fr) auto !important;
        align-items: center !important;
        gap: 7px !important;
        margin: 0 !important;
        padding: 5px 0 !important;
        border-top: 1px solid rgba(255,255,255,.075) !important;
        color: rgba(224,233,250,.70) !important;
        font-size: 12px !important;
        line-height: 1.22 !important;
      }

      .mobile-command-row {
        grid-template-columns: 3.7em minmax(0,1fr) !important;
        padding: 2px 0 !important;
        border-top: 0 !important;
      }

      .draft-record strong,
      .mobile-command-row strong {
        display: block !important;
        color: rgba(250,252,255,.96) !important;
        font-size: 13px !important;
        line-height: 1.22 !important;
        font-weight: 850 !important;
        text-align: right !important;
        overflow-wrap: anywhere !important;
      }

      .draft-record div strong {
        text-align: left !important;
      }

      .draft-record span {
        display: block !important;
        margin-top: 2px !important;
        color: rgba(224,233,250,.62) !important;
        font-size: 11px !important;
        line-height: 1.18 !important;
      }

      .draft-record em {
        color: rgba(250,252,255,.96) !important;
        font-style: normal !important;
        font-size: 13px !important;
        line-height: 1.2 !important;
        font-weight: 900 !important;
      }

      .mobile-command-detail {
        display: grid !important;
        gap: 2px !important;
        margin: 2px 0 5px !important;
        padding: 0 !important;
      }

      .confirm-draft,
      .cancel-draft,
      .mobile-command-actions button {
        min-height: 28px !important;
        height: 28px !important;
        padding: 4px 10px !important;
        border-radius: 12px !important;
        border: 1px solid rgba(255,255,255,.15) !important;
        font-size: 12px !important;
        line-height: 1 !important;
        font-weight: 880 !important;
        box-shadow: inset 0 .7px 0 rgba(255,255,255,.14), 0 4px 8px rgba(0,0,0,.075) !important;
      }

      .confirm-draft,
      .mobile-command-confirm {
        color: #fff !important;
        background: linear-gradient(135deg, rgba(20,190,190,.86), rgba(45,139,220,.78)) !important;
      }

      .cancel-draft,
      .mobile-command-cancel {
        color: rgba(242,247,255,.86) !important;
        background: rgba(255,255,255,.075) !important;
      }

      .mobile-command-actions {
        display: flex !important;
        flex-wrap: wrap !important;
        gap: 6px !important;
        margin: 4px 0 0 !important;
        padding: 0 !important;
      }

      .mobile-command-message {
        margin-top: 5px !important;
        color: rgba(224,233,250,.66) !important;
        font-size: 11.5px !important;
        line-height: 1.28 !important;
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

        .chat-row.assistant .chat-bubble.chat-response:has(.draft-card),
        .chat-row.assistant .chat-bubble.chat-response:has(.mobile-command-card),
        .chat-row.assistant .chat-bubble.chat-response.draft-bubble,
        .chat-row.assistant .chat-bubble.chat-response.mobile-command-bubble {
          max-width: 78% !important;
        }

        .draft-card,
        .mobile-command-card {
          width: min(100%, 214px) !important;
          inline-size: min(100%, 214px) !important;
          max-width: 214px !important;
          padding: 8px !important;
        }
      }
    `;
    document.head.appendChild(style);
  }

  function tagChatCards(root = document) {
    root.querySelectorAll?.('.draft-card').forEach((card) => {
      card.closest('.chat-bubble.chat-response')?.classList.add('draft-bubble');
    });
    root.querySelectorAll?.('.mobile-command-card').forEach((card) => {
      card.closest('.chat-bubble.chat-response')?.classList.add('mobile-command-bubble');
    });
  }

  function installChatCardObserver() {
    const host = document.getElementById('chatMessages');
    if (!host || host.__layoutChatCardObserver) return;
    let frame = 0;
    const observer = new MutationObserver(() => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => tagChatCards(host));
    });
    observer.observe(host, { childList: true, subtree: true });
    host.__layoutChatCardObserver = observer;
    tagChatCards(host);
  }

  function boot() {
    installStyle();
    installChatCardObserver();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
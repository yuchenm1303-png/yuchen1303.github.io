(() => {
  const STYLE_ID = 'chat-card-polish-style';

  function installStyle() {
    const old = document.querySelector(`#${STYLE_ID}`);
    if (old) old.remove();

    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      .chat-row .chat-bubble.chat-response {
        overflow: visible !important;
      }

      .draft-card,
      .mobile-command-card {
        width: min(100%, 430px) !important;
        margin: 12px 0 4px !important;
        padding: 14px !important;
        border-radius: 24px !important;
        border: 1px solid rgba(255,255,255,.24) !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.102), rgba(255,255,255,.040) 46%, rgba(0,0,0,.030)),
          rgba(28,42,72,.64) !important;
        color: rgba(248,250,255,.94) !important;
        box-shadow:
          0 12px 26px rgba(0,0,0,.16),
          inset 0 1px 0 rgba(255,255,255,.22),
          inset 0 -1px 0 rgba(0,0,0,.10) !important;
        backdrop-filter: none !important;
        -webkit-backdrop-filter: none !important;
        overflow: hidden !important;
      }

      .draft-head,
      .mobile-command-head {
        display: flex !important;
        align-items: center !important;
        justify-content: space-between !important;
        gap: 10px !important;
        margin-bottom: 10px !important;
      }

      .draft-head strong,
      .mobile-command-title {
        font-size: 16px !important;
        font-weight: 900 !important;
        color: rgba(248,250,255,.96) !important;
      }

      .draft-head span,
      .mobile-command-status {
        flex: 0 0 auto !important;
        padding: 5px 10px !important;
        border-radius: 999px !important;
        font-size: 12px !important;
        font-weight: 800 !important;
        background: rgba(98,240,218,.16) !important;
        color: rgba(118,255,231,.92) !important;
      }

      .mobile-command-status.done {
        background: rgba(105,231,158,.16) !important;
        color: rgba(149,255,191,.96) !important;
      }

      .mobile-command-status.cancelled,
      .mobile-command-status.failed {
        background: rgba(255,135,135,.16) !important;
        color: rgba(255,178,178,.96) !important;
      }

      .draft-record {
        display: grid !important;
        grid-template-columns: minmax(0,1fr) auto !important;
        align-items: start !important;
        gap: 10px !important;
        padding: 6px 0 12px !important;
        border: 0 !important;
      }

      .draft-record strong,
      .draft-record em,
      .mobile-command-row strong {
        color: rgba(248,250,255,.95) !important;
        font-weight: 900 !important;
      }

      .draft-record span,
      .mobile-command-row span,
      .mobile-command-message {
        color: rgba(222,231,250,.68) !important;
      }

      .draft-record em {
        font-style: normal !important;
        text-align: right !important;
        white-space: nowrap !important;
      }

      .mobile-command-detail {
        display: grid !important;
        gap: 7px !important;
        margin: 8px 0 12px !important;
      }

      .mobile-command-row {
        display: grid !important;
        grid-template-columns: 78px minmax(0,1fr) !important;
        gap: 10px !important;
        font-size: 13px !important;
        line-height: 1.55 !important;
      }

      .mobile-command-row strong {
        text-align: right !important;
        overflow-wrap: anywhere !important;
      }

      .draft-card button,
      .mobile-command-actions button {
        border: 1px solid rgba(255,255,255,.18) !important;
        border-radius: 18px !important;
        min-height: 42px !important;
        padding: 10px 14px !important;
        font-weight: 900 !important;
        font-size: 14px !important;
        box-shadow:
          inset 0 1px 0 rgba(255,255,255,.20),
          0 8px 16px rgba(0,0,0,.12) !important;
        writing-mode: horizontal-tb !important;
        text-orientation: mixed !important;
      }

      .confirm-draft,
      .mobile-command-confirm {
        color: white !important;
        background: linear-gradient(135deg, rgba(12,190,176,.96), rgba(32,128,216,.86)) !important;
      }

      .cancel-draft,
      .mobile-command-cancel {
        color: rgba(242,247,255,.90) !important;
        background: rgba(255,255,255,.105) !important;
      }

      .draft-card .confirm-draft,
      .draft-card .cancel-draft {
        display: block !important;
        width: 100% !important;
        margin-top: 8px !important;
      }

      .mobile-command-actions {
        display: flex !important;
        gap: 9px !important;
        flex-wrap: wrap !important;
      }

      .mobile-command-message {
        margin-top: 10px !important;
        font-size: 13px !important;
        line-height: 1.55 !important;
      }

      .mobile-command-card[data-mobile-card] {
        min-height: 0 !important;
      }

      @media (pointer: coarse), (max-width: 760px) {
        .draft-card,
        .mobile-command-card {
          width: 100% !important;
          padding: 13px !important;
          border-radius: 22px !important;
        }

        .mobile-command-row {
          grid-template-columns: 64px minmax(0,1fr) !important;
          gap: 8px !important;
        }

        .draft-card button,
        .mobile-command-actions button {
          min-height: 40px !important;
          border-radius: 16px !important;
        }
      }
    `;
    document.head.appendChild(style);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', installStyle);
  else installStyle();
})();
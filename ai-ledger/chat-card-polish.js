(() => {
  const STYLE_ID = 'chat-card-polish-style';

  function installStyle() {
    document.querySelector(`#${STYLE_ID}`)?.remove();

    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      .chat-row .chat-bubble.chat-response {
        overflow: visible !important;
      }

      .chat-row.assistant .chat-bubble.chat-response {
        max-width: min(92%, 430px) !important;
        padding: 10px 12px !important;
        border-radius: 18px !important;
        border-bottom-left-radius: 7px !important;
        line-height: 1.48 !important;
        font-size: 14px !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.078), rgba(255,255,255,.020) 58%, rgba(255,255,255,.012)),
          rgba(238,246,255,.070) !important;
        border-color: rgba(255,255,255,.22) !important;
        box-shadow:
          0 7px 16px rgba(0,0,0,.07),
          inset 0 .7px 0 rgba(255,255,255,.24) !important;
        backdrop-filter: blur(10px) saturate(122%) !important;
        -webkit-backdrop-filter: blur(10px) saturate(122%) !important;
      }

      .draft-card,
      .mobile-command-card {
        width: min(100%, 350px) !important;
        margin: 8px 0 2px !important;
        padding: 11px 12px !important;
        border-radius: 17px !important;
        border: 1px solid rgba(255,255,255,.24) !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.115), rgba(255,255,255,.038) 56%, rgba(255,255,255,.020)),
          rgba(238,246,255,.085) !important;
        color: rgba(248,250,255,.94) !important;
        box-shadow:
          0 7px 16px rgba(0,0,0,.075),
          inset 0 .7px 0 rgba(255,255,255,.30),
          inset 0 -.7px 0 rgba(8,18,34,.040) !important;
        backdrop-filter: blur(9px) saturate(122%) !important;
        -webkit-backdrop-filter: blur(9px) saturate(122%) !important;
        overflow: hidden !important;
        min-height: 0 !important;
      }

      .draft-head,
      .mobile-command-head {
        display: flex !important;
        align-items: center !important;
        justify-content: space-between !important;
        gap: 8px !important;
        margin-bottom: 7px !important;
      }

      .draft-head strong,
      .mobile-command-title {
        font-size: 14px !important;
        line-height: 1.25 !important;
        font-weight: 850 !important;
        color: rgba(250,252,255,.96) !important;
      }

      .draft-head span,
      .mobile-command-status {
        flex: 0 0 auto !important;
        padding: 3px 8px !important;
        border-radius: 999px !important;
        font-size: 11px !important;
        line-height: 1.25 !important;
        font-weight: 800 !important;
        background: rgba(98,240,218,.14) !important;
        color: rgba(128,255,235,.92) !important;
      }

      .mobile-command-status.done {
        background: rgba(105,231,158,.14) !important;
        color: rgba(149,255,191,.96) !important;
      }

      .mobile-command-status.cancelled,
      .mobile-command-status.failed {
        background: rgba(255,135,135,.14) !important;
        color: rgba(255,178,178,.96) !important;
      }

      .draft-record {
        display: grid !important;
        grid-template-columns: minmax(0,1fr) auto !important;
        align-items: start !important;
        gap: 8px !important;
        padding: 4px 0 8px !important;
        border: 0 !important;
      }

      .draft-record strong,
      .draft-record em,
      .mobile-command-row strong {
        color: rgba(250,252,255,.94) !important;
        font-weight: 850 !important;
      }

      .draft-record strong,
      .draft-record em {
        font-size: 13px !important;
        line-height: 1.35 !important;
      }

      .draft-record span,
      .mobile-command-row span,
      .mobile-command-message {
        color: rgba(224,233,250,.64) !important;
      }

      .draft-record span {
        font-size: 12px !important;
        line-height: 1.35 !important;
      }

      .draft-record em {
        font-style: normal !important;
        text-align: right !important;
        white-space: nowrap !important;
      }

      .mobile-command-detail {
        display: grid !important;
        gap: 5px !important;
        margin: 7px 0 9px !important;
      }

      .mobile-command-row {
        display: grid !important;
        grid-template-columns: 54px minmax(0,1fr) !important;
        align-items: center !important;
        gap: 8px !important;
        font-size: 12px !important;
        line-height: 1.32 !important;
      }

      .mobile-command-row strong {
        text-align: right !important;
        overflow-wrap: anywhere !important;
        font-size: 12px !important;
        line-height: 1.32 !important;
      }

      .draft-card button,
      .mobile-command-actions button {
        border: 1px solid rgba(255,255,255,.16) !important;
        border-radius: 13px !important;
        min-height: 34px !important;
        padding: 7px 11px !important;
        font-weight: 850 !important;
        font-size: 13px !important;
        box-shadow:
          inset 0 .7px 0 rgba(255,255,255,.18),
          0 6px 12px rgba(0,0,0,.09) !important;
        writing-mode: horizontal-tb !important;
        text-orientation: mixed !important;
      }

      .confirm-draft,
      .mobile-command-confirm {
        color: white !important;
        background: linear-gradient(135deg, rgba(20,190,190,.92), rgba(45,139,220,.84)) !important;
      }

      .cancel-draft,
      .mobile-command-cancel {
        color: rgba(242,247,255,.88) !important;
        background: rgba(255,255,255,.090) !important;
      }

      .draft-card .confirm-draft,
      .draft-card .cancel-draft {
        display: inline-flex !important;
        align-items: center !important;
        justify-content: center !important;
        width: auto !important;
        margin-top: 6px !important;
        margin-right: 7px !important;
      }

      .mobile-command-actions {
        display: flex !important;
        gap: 7px !important;
        flex-wrap: wrap !important;
        margin-top: 5px !important;
      }

      .mobile-command-message {
        margin-top: 8px !important;
        font-size: 12px !important;
        line-height: 1.4 !important;
      }

      @media (pointer: coarse), (max-width: 760px) {
        .chat-row.assistant .chat-bubble.chat-response {
          max-width: 92% !important;
          padding: 9px 11px !important;
          font-size: 14px !important;
        }

        .draft-card,
        .mobile-command-card {
          width: min(100%, 330px) !important;
          padding: 10px 11px !important;
          border-radius: 16px !important;
        }

        .mobile-command-row {
          grid-template-columns: 50px minmax(0,1fr) !important;
          gap: 7px !important;
        }

        .draft-card button,
        .mobile-command-actions button {
          min-height: 33px !important;
          border-radius: 12px !important;
          padding: 7px 10px !important;
        }
      }
    `;
    document.head.appendChild(style);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', installStyle, { once: true });
  else installStyle();
})();
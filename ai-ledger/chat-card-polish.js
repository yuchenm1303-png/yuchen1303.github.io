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
        max-width: min(91%, 400px) !important;
        padding: 8px 10px !important;
        border-radius: 17px !important;
        border-bottom-left-radius: 7px !important;
        line-height: 1.42 !important;
        font-size: 14px !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.070), rgba(255,255,255,.018) 58%, rgba(255,255,255,.010)),
          rgba(238,246,255,.060) !important;
        border-color: rgba(255,255,255,.20) !important;
        box-shadow:
          0 6px 14px rgba(0,0,0,.060),
          inset 0 .7px 0 rgba(255,255,255,.22) !important;
        backdrop-filter: blur(8px) saturate(118%) !important;
        -webkit-backdrop-filter: blur(8px) saturate(118%) !important;
      }

      .draft-card,
      .mobile-command-card {
        width: min(100%, 318px) !important;
        height: auto !important;
        min-height: unset !important;
        max-height: none !important;
        align-self: start !important;
        display: block !important;
        margin: 6px 0 1px !important;
        padding: 8px 9px !important;
        border-radius: 14px !important;
        border: 1px solid rgba(255,255,255,.22) !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.110), rgba(255,255,255,.034) 56%, rgba(255,255,255,.018)),
          rgba(238,246,255,.070) !important;
        color: rgba(248,250,255,.94) !important;
        box-shadow:
          0 5px 12px rgba(0,0,0,.060),
          inset 0 .7px 0 rgba(255,255,255,.26),
          inset 0 -.7px 0 rgba(8,18,34,.036) !important;
        backdrop-filter: blur(8px) saturate(118%) !important;
        -webkit-backdrop-filter: blur(8px) saturate(118%) !important;
        overflow: hidden !important;
      }

      .draft-card *,
      .mobile-command-card * {
        box-sizing: border-box !important;
      }

      .draft-head,
      .mobile-command-head {
        display: flex !important;
        align-items: center !important;
        justify-content: space-between !important;
        gap: 7px !important;
        margin: 0 0 5px !important;
        padding: 0 !important;
        min-height: 0 !important;
      }

      .draft-head strong,
      .mobile-command-title {
        display: block !important;
        font-size: 13px !important;
        line-height: 1.18 !important;
        font-weight: 850 !important;
        color: rgba(250,252,255,.96) !important;
      }

      .draft-head span,
      .mobile-command-status {
        flex: 0 0 auto !important;
        padding: 2px 7px !important;
        border-radius: 999px !important;
        font-size: 10.5px !important;
        line-height: 1.2 !important;
        font-weight: 800 !important;
        background: rgba(98,240,218,.13) !important;
        color: rgba(128,255,235,.92) !important;
      }

      .mobile-command-status.done {
        background: rgba(105,231,158,.13) !important;
        color: rgba(149,255,191,.96) !important;
      }

      .mobile-command-status.cancelled,
      .mobile-command-status.failed {
        background: rgba(255,135,135,.13) !important;
        color: rgba(255,178,178,.96) !important;
      }

      .draft-record {
        display: grid !important;
        grid-template-columns: minmax(0,1fr) auto !important;
        align-items: start !important;
        gap: 7px !important;
        padding: 2px 0 5px !important;
        margin: 0 !important;
        border: 0 !important;
        min-height: 0 !important;
      }

      .draft-record strong,
      .draft-record em,
      .mobile-command-row strong {
        color: rgba(250,252,255,.94) !important;
        font-weight: 850 !important;
      }

      .draft-record strong,
      .draft-record em {
        font-size: 12px !important;
        line-height: 1.25 !important;
      }

      .draft-record span,
      .mobile-command-row span,
      .mobile-command-message {
        color: rgba(224,233,250,.63) !important;
      }

      .draft-record span {
        font-size: 11px !important;
        line-height: 1.25 !important;
      }

      .draft-record em {
        font-style: normal !important;
        text-align: right !important;
        white-space: nowrap !important;
      }

      .mobile-command-detail {
        display: grid !important;
        grid-auto-rows: min-content !important;
        gap: 3px !important;
        margin: 5px 0 7px !important;
        padding: 0 !important;
        min-height: 0 !important;
      }

      .mobile-command-row {
        display: grid !important;
        grid-template-columns: 46px minmax(0,1fr) !important;
        align-items: center !important;
        gap: 7px !important;
        margin: 0 !important;
        padding: 0 !important;
        font-size: 11.5px !important;
        line-height: 1.25 !important;
        min-height: 0 !important;
      }

      .mobile-command-row strong {
        text-align: right !important;
        overflow-wrap: anywhere !important;
        font-size: 11.5px !important;
        line-height: 1.25 !important;
      }

      .draft-card button,
      .mobile-command-actions button {
        border: 1px solid rgba(255,255,255,.15) !important;
        border-radius: 11px !important;
        min-height: 29px !important;
        height: 29px !important;
        padding: 5px 9px !important;
        font-weight: 850 !important;
        font-size: 12px !important;
        line-height: 1 !important;
        box-shadow:
          inset 0 .7px 0 rgba(255,255,255,.16),
          0 5px 10px rgba(0,0,0,.075) !important;
        writing-mode: horizontal-tb !important;
        text-orientation: mixed !important;
      }

      .confirm-draft,
      .mobile-command-confirm {
        color: white !important;
        background: linear-gradient(135deg, rgba(20,190,190,.90), rgba(45,139,220,.82)) !important;
      }

      .cancel-draft,
      .mobile-command-cancel {
        color: rgba(242,247,255,.88) !important;
        background: rgba(255,255,255,.080) !important;
      }

      .draft-card .confirm-draft,
      .draft-card .cancel-draft {
        display: inline-flex !important;
        align-items: center !important;
        justify-content: center !important;
        width: auto !important;
        margin-top: 4px !important;
        margin-right: 6px !important;
      }

      .mobile-command-actions {
        display: flex !important;
        align-items: center !important;
        gap: 6px !important;
        flex-wrap: wrap !important;
        margin: 4px 0 0 !important;
        padding: 0 !important;
        min-height: 0 !important;
      }

      .mobile-command-message {
        margin-top: 6px !important;
        font-size: 11px !important;
        line-height: 1.3 !important;
      }

      @media (pointer: coarse), (max-width: 760px) {
        .chat-row.assistant .chat-bubble.chat-response {
          max-width: 91% !important;
          padding: 8px 10px !important;
          font-size: 13.5px !important;
        }

        .draft-card,
        .mobile-command-card {
          width: min(100%, 306px) !important;
          padding: 8px 9px !important;
          border-radius: 14px !important;
        }

        .mobile-command-row {
          grid-template-columns: 42px minmax(0,1fr) !important;
          gap: 6px !important;
          font-size: 11px !important;
        }

        .mobile-command-row strong {
          font-size: 11px !important;
        }

        .draft-card button,
        .mobile-command-actions button {
          min-height: 28px !important;
          height: 28px !important;
          border-radius: 10px !important;
          padding: 5px 8px !important;
        }
      }
    `;
    document.head.appendChild(style);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', installStyle, { once: true });
  else installStyle();
})();
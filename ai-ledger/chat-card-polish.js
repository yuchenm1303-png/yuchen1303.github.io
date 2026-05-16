(() => {
  'use strict';

  const STYLE_ID = 'chat-card-polish-style';
  const COMPACT_ATTR = 'data-compact-command-card';
  let observer = null;
  let compactFrame = 0;

  function installStyle() {
    const old = document.getElementById(STYLE_ID);
    if (old) old.remove();

    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      .chat-row .chat-bubble.chat-response {
        overflow: visible !important;
      }

      .chat-row.assistant .chat-bubble.chat-response {
        max-width: min(88%, 380px) !important;
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

      .chat-row.assistant .chat-bubble.chat-response:has(.mobile-command-card),
      .chat-row.assistant .chat-bubble.chat-response:has(.draft-card) {
        max-width: min(82%, 330px) !important;
        width: fit-content !important;
        height: auto !important;
        min-height: 0 !important;
        padding: 6px !important;
        display: block !important;
        align-items: start !important;
      }

      .draft-card,
      .mobile-command-card,
      [data-mobile-card] {
        width: min(100%, 252px) !important;
        height: auto !important;
        min-height: 0 !important;
        max-height: max-content !important;
        aspect-ratio: auto !important;
        block-size: auto !important;
        min-block-size: 0 !important;
        inline-size: min(100%, 252px) !important;
        align-self: flex-start !important;
        justify-self: start !important;
        place-self: start !important;
        display: inline-grid !important;
        grid-template-rows: auto auto auto auto !important;
        grid-auto-rows: min-content !important;
        align-content: start !important;
        justify-content: stretch !important;
        gap: 0 !important;
        margin: 4px 0 0 !important;
        padding: 9px 10px 10px !important;
        border-radius: 15px !important;
        border: 1px solid rgba(255,255,255,.23) !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.118), rgba(255,255,255,.038) 56%, rgba(255,255,255,.020)),
          rgba(238,246,255,.078) !important;
        color: rgba(248,250,255,.94) !important;
        box-shadow:
          0 5px 12px rgba(0,0,0,.060),
          inset 0 .7px 0 rgba(255,255,255,.25),
          inset 0 -.7px 0 rgba(8,18,34,.036) !important;
        backdrop-filter: blur(7px) saturate(116%) !important;
        -webkit-backdrop-filter: blur(7px) saturate(116%) !important;
        overflow: hidden !important;
      }

      .draft-card::before,
      .draft-card::after,
      .mobile-command-card::before,
      .mobile-command-card::after,
      [data-mobile-card]::before,
      [data-mobile-card]::after {
        display: none !important;
        content: none !important;
      }

      .draft-card *,
      .mobile-command-card *,
      [data-mobile-card] * { box-sizing: border-box !important; }

      .draft-head,
      .mobile-command-head,
      [data-mobile-card] .mobile-command-head {
        display: flex !important;
        align-items: center !important;
        justify-content: space-between !important;
        gap: 8px !important;
        margin: 0 0 6px !important;
        padding: 0 !important;
        height: auto !important;
        min-height: 0 !important;
        max-height: none !important;
      }

      .draft-head strong,
      .mobile-command-title,
      [data-mobile-card] .mobile-command-title {
        display: block !important;
        min-width: 0 !important;
        font-size: 14.5px !important;
        line-height: 1.16 !important;
        font-weight: 880 !important;
        letter-spacing: -.1px !important;
        color: rgba(250,252,255,.97) !important;
        overflow-wrap: anywhere !important;
      }

      .draft-head span,
      .mobile-command-status,
      [data-mobile-card] .mobile-command-status {
        flex: 0 0 auto !important;
        padding: 3px 8px !important;
        border-radius: 999px !important;
        font-size: 11.5px !important;
        line-height: 1.16 !important;
        font-weight: 850 !important;
        background: rgba(98,240,218,.14) !important;
        color: rgba(128,255,235,.94) !important;
      }

      .mobile-command-detail,
      [data-mobile-card] .mobile-command-detail {
        display: grid !important;
        grid-template-rows: repeat(auto-fit, min-content) !important;
        grid-auto-rows: min-content !important;
        gap: 4px !important;
        margin: 6px 0 8px !important;
        padding: 0 !important;
        height: auto !important;
        min-height: 0 !important;
        max-height: none !important;
        align-content: start !important;
      }

      .mobile-command-row,
      [data-mobile-card] .mobile-command-row {
        display: grid !important;
        grid-template-columns: 4.1em minmax(0,1fr) !important;
        align-items: baseline !important;
        gap: 7px !important;
        margin: 0 !important;
        padding: 0 !important;
        height: auto !important;
        min-height: 0 !important;
        max-height: none !important;
        font-size: 12.5px !important;
        line-height: 1.24 !important;
      }

      .mobile-command-row span,
      .mobile-command-message,
      [data-mobile-card] .mobile-command-row span,
      [data-mobile-card] .mobile-command-message {
        color: rgba(224,233,250,.66) !important;
      }

      .mobile-command-row strong,
      [data-mobile-card] .mobile-command-row strong {
        color: rgba(250,252,255,.96) !important;
        font-weight: 850 !important;
        text-align: right !important;
        overflow-wrap: anywhere !important;
        word-break: break-word !important;
        font-size: 13.5px !important;
        line-height: 1.24 !important;
      }

      .mobile-command-actions,
      [data-mobile-card] .mobile-command-actions {
        display: flex !important;
        align-items: center !important;
        gap: 7px !important;
        flex-wrap: wrap !important;
        margin: 6px 0 0 !important;
        padding: 0 !important;
        height: auto !important;
        min-height: 0 !important;
        max-height: none !important;
      }

      .mobile-command-actions button,
      [data-mobile-card] .mobile-command-actions button,
      .draft-card button {
        border: 1px solid rgba(255,255,255,.15) !important;
        border-radius: 12px !important;
        min-height: 31px !important;
        height: 31px !important;
        padding: 5px 10px !important;
        font-weight: 880 !important;
        font-size: 13px !important;
        line-height: 1 !important;
        box-shadow:
          inset 0 .7px 0 rgba(255,255,255,.16),
          0 5px 10px rgba(0,0,0,.075) !important;
      }

      .mobile-command-confirm,
      [data-mobile-card] .mobile-command-confirm,
      .confirm-draft {
        color: white !important;
        background: linear-gradient(135deg, rgba(20,190,190,.90), rgba(45,139,220,.82)) !important;
      }

      .mobile-command-cancel,
      [data-mobile-card] .mobile-command-cancel,
      .cancel-draft {
        color: rgba(242,247,255,.88) !important;
        background: rgba(255,255,255,.080) !important;
      }

      .mobile-command-message,
      [data-mobile-card] .mobile-command-message {
        margin-top: 6px !important;
        font-size: 12px !important;
        line-height: 1.3 !important;
      }

      @media (pointer: coarse), (max-width: 760px) {
        .chat-row.assistant .chat-bubble.chat-response:has(.mobile-command-card),
        .chat-row.assistant .chat-bubble.chat-response:has(.draft-card) {
          max-width: min(80%, 318px) !important;
          padding: 5px !important;
        }

        .draft-card,
        .mobile-command-card,
        [data-mobile-card] {
          width: min(100%, 244px) !important;
          inline-size: min(100%, 244px) !important;
          padding: 8px 9px 9px !important;
          border-radius: 15px !important;
        }

        .mobile-command-detail,
        [data-mobile-card] .mobile-command-detail {
          gap: 3px !important;
          margin: 5px 0 7px !important;
        }

        .mobile-command-row,
        [data-mobile-card] .mobile-command-row {
          grid-template-columns: 3.9em minmax(0,1fr) !important;
          gap: 6px !important;
          font-size: 12px !important;
          line-height: 1.22 !important;
        }

        .mobile-command-row strong,
        [data-mobile-card] .mobile-command-row strong { font-size: 13px !important; line-height: 1.22 !important; }
      }

      @media (max-width: 390px) {
        .chat-row.assistant .chat-bubble.chat-response:has(.mobile-command-card),
        .chat-row.assistant .chat-bubble.chat-response:has(.draft-card) {
          max-width: 76% !important;
        }

        .draft-card,
        .mobile-command-card,
        [data-mobile-card] {
          width: min(100%, 230px) !important;
          inline-size: min(100%, 230px) !important;
          padding: 8px !important;
        }

        .mobile-command-title,
        [data-mobile-card] .mobile-command-title { font-size: 14px !important; }
        .mobile-command-status,
        [data-mobile-card] .mobile-command-status { font-size: 11px !important; }
        .mobile-command-row,
        [data-mobile-card] .mobile-command-row { grid-template-columns: 3.8em minmax(0,1fr) !important; }
        .mobile-command-actions button,
        [data-mobile-card] .mobile-command-actions button,
        .draft-card button { min-height: 30px !important; height: 30px !important; font-size: 12.5px !important; }
      }
    `;
    document.head.appendChild(style);
  }

  function applyCompactCard(card) {
    if (!card) return;
    card.setAttribute(COMPACT_ATTR, 'true');
    card.style.setProperty('width', 'min(100%, 244px)', 'important');
    card.style.setProperty('inline-size', 'min(100%, 244px)', 'important');
    card.style.setProperty('height', 'auto', 'important');
    card.style.setProperty('block-size', 'auto', 'important');
    card.style.setProperty('min-height', '0', 'important');
    card.style.setProperty('min-block-size', '0', 'important');
    card.style.setProperty('max-height', 'max-content', 'important');
    card.style.setProperty('aspect-ratio', 'auto', 'important');
    card.style.setProperty('display', 'inline-grid', 'important');
    card.style.setProperty('grid-template-rows', 'auto auto auto auto', 'important');
    card.style.setProperty('grid-auto-rows', 'min-content', 'important');
    card.style.setProperty('align-content', 'start', 'important');
    card.style.setProperty('align-self', 'start', 'important');
    card.style.setProperty('justify-self', 'start', 'important');
    card.style.setProperty('place-self', 'start', 'important');
    card.style.setProperty('gap', '0', 'important');
    card.style.setProperty('margin', '4px 0 0', 'important');
    card.style.setProperty('padding', '8px 9px 9px', 'important');
    card.style.setProperty('border-radius', '15px', 'important');
    card.style.setProperty('overflow', 'hidden', 'important');

    card.querySelectorAll('.mobile-command-head,.draft-head').forEach((el) => {
      el.style.setProperty('margin', '0 0 6px', 'important');
      el.style.setProperty('padding', '0', 'important');
      el.style.setProperty('height', 'auto', 'important');
      el.style.setProperty('min-height', '0', 'important');
      el.style.setProperty('max-height', 'none', 'important');
    });
    card.querySelectorAll('.mobile-command-detail').forEach((el) => {
      el.style.setProperty('gap', '3px', 'important');
      el.style.setProperty('margin', '5px 0 7px', 'important');
      el.style.setProperty('padding', '0', 'important');
      el.style.setProperty('height', 'auto', 'important');
      el.style.setProperty('min-height', '0', 'important');
      el.style.setProperty('max-height', 'none', 'important');
      el.style.setProperty('align-content', 'start', 'important');
    });
    card.querySelectorAll('.mobile-command-row').forEach((el) => {
      el.style.setProperty('grid-template-columns', '3.9em minmax(0,1fr)', 'important');
      el.style.setProperty('gap', '6px', 'important');
      el.style.setProperty('font-size', '12px', 'important');
      el.style.setProperty('line-height', '1.22', 'important');
      el.style.setProperty('margin', '0', 'important');
      el.style.setProperty('padding', '0', 'important');
      el.style.setProperty('height', 'auto', 'important');
      el.style.setProperty('min-height', '0', 'important');
      el.style.setProperty('max-height', 'none', 'important');
    });
    card.querySelectorAll('.mobile-command-row strong').forEach((el) => {
      el.style.setProperty('font-size', '13px', 'important');
      el.style.setProperty('line-height', '1.22', 'important');
      el.style.setProperty('text-align', 'right', 'important');
      el.style.setProperty('overflow-wrap', 'anywhere', 'important');
    });
    card.querySelectorAll('.mobile-command-title').forEach((el) => {
      el.style.setProperty('font-size', '14px', 'important');
      el.style.setProperty('line-height', '1.16', 'important');
    });
    card.querySelectorAll('.mobile-command-status').forEach((el) => {
      el.style.setProperty('font-size', '11px', 'important');
      el.style.setProperty('padding', '3px 8px', 'important');
    });
    card.querySelectorAll('.mobile-command-actions').forEach((el) => {
      el.style.setProperty('gap', '7px', 'important');
      el.style.setProperty('margin', '6px 0 0', 'important');
      el.style.setProperty('padding', '0', 'important');
      el.style.setProperty('height', 'auto', 'important');
      el.style.setProperty('min-height', '0', 'important');
      el.style.setProperty('max-height', 'none', 'important');
    });
    card.querySelectorAll('button').forEach((el) => {
      el.style.setProperty('height', '30px', 'important');
      el.style.setProperty('min-height', '30px', 'important');
      el.style.setProperty('padding', '5px 10px', 'important');
      el.style.setProperty('border-radius', '12px', 'important');
      el.style.setProperty('font-size', '12.5px', 'important');
      el.style.setProperty('line-height', '1', 'important');
    });
  }

  function compactAllCards() {
    document.querySelectorAll('.mobile-command-card,[data-mobile-card],.draft-card').forEach(applyCompactCard);
  }

  function scheduleCompactAllCards() {
    cancelAnimationFrame(compactFrame);
    compactFrame = requestAnimationFrame(compactAllCards);
  }

  function watchCards() {
    compactAllCards();
    const host = document.querySelector('#chatMessages') || document.body;
    if (!host || host.dataset.compactCommandObserver === 'true') return;
    host.dataset.compactCommandObserver = 'true';
    observer?.disconnect();
    observer = new MutationObserver(scheduleCompactAllCards);
    observer.observe(host, { childList: true, subtree: true, attributes: true, attributeFilter: ['style', 'class'] });
  }

  function boot() {
    document.documentElement.dataset.chatCardPolishReady = 'true';
    installStyle();
    watchCards();
    window.setTimeout(compactAllCards, 60);
    window.setTimeout(compactAllCards, 360);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();
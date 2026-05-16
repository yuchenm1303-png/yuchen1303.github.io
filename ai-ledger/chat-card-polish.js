(() => {
  'use strict';

  const STYLE_ID = 'chat-card-polish-style';
  const COMPACT_ATTR = 'data-compact-command-card';
  let observer = null;
  let compactFrame = 0;

  function installStyle() {
    if (document.getElementById(STYLE_ID)) return;

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
        max-width: min(82%, 342px) !important;
        width: fit-content !important;
        padding: 7px !important;
      }

      .draft-card,
      .mobile-command-card,
      [data-mobile-card] {
        width: min(100%, 258px) !important;
        height: auto !important;
        min-height: 0 !important;
        max-height: none !important;
        align-self: start !important;
        display: block !important;
        margin: 4px 0 0 !important;
        padding: 10px 11px !important;
        border-radius: 16px !important;
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
        margin: 0 0 7px !important;
        padding: 0 !important;
        min-height: 0 !important;
      }

      .draft-head strong,
      .mobile-command-title,
      [data-mobile-card] .mobile-command-title {
        display: block !important;
        min-width: 0 !important;
        font-size: 15px !important;
        line-height: 1.18 !important;
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
        font-size: 12px !important;
        line-height: 1.18 !important;
        font-weight: 850 !important;
        background: rgba(98,240,218,.14) !important;
        color: rgba(128,255,235,.94) !important;
      }

      .mobile-command-detail,
      [data-mobile-card] .mobile-command-detail {
        display: grid !important;
        grid-auto-rows: min-content !important;
        gap: 5px !important;
        margin: 7px 0 9px !important;
        padding: 0 !important;
        min-height: 0 !important;
      }

      .mobile-command-row,
      [data-mobile-card] .mobile-command-row {
        display: grid !important;
        grid-template-columns: 4.4em minmax(0,1fr) !important;
        align-items: baseline !important;
        gap: 8px !important;
        margin: 0 !important;
        padding: 0 !important;
        font-size: 13px !important;
        line-height: 1.28 !important;
        min-height: 0 !important;
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
        font-size: 14px !important;
        line-height: 1.28 !important;
      }

      .mobile-command-actions,
      [data-mobile-card] .mobile-command-actions {
        display: flex !important;
        align-items: center !important;
        gap: 7px !important;
        flex-wrap: wrap !important;
        margin: 7px 0 0 !important;
        padding: 0 !important;
        min-height: 0 !important;
      }

      .mobile-command-actions button,
      [data-mobile-card] .mobile-command-actions button,
      .draft-card button {
        border: 1px solid rgba(255,255,255,.15) !important;
        border-radius: 12px !important;
        min-height: 33px !important;
        height: 33px !important;
        padding: 6px 11px !important;
        font-weight: 880 !important;
        font-size: 13.5px !important;
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
        margin-top: 7px !important;
        font-size: 12.5px !important;
        line-height: 1.35 !important;
      }

      @media (pointer: coarse), (max-width: 760px) {
        .chat-row.assistant .chat-bubble.chat-response:has(.mobile-command-card),
        .chat-row.assistant .chat-bubble.chat-response:has(.draft-card) {
          max-width: min(80%, 326px) !important;
          padding: 6px !important;
        }

        .draft-card,
        .mobile-command-card,
        [data-mobile-card] {
          width: min(100%, 252px) !important;
          padding: 9px 10px !important;
          border-radius: 15px !important;
        }

        .mobile-command-row,
        [data-mobile-card] .mobile-command-row {
          grid-template-columns: 4.2em minmax(0,1fr) !important;
          gap: 7px !important;
          font-size: 12.5px !important;
        }

        .mobile-command-row strong,
        [data-mobile-card] .mobile-command-row strong { font-size: 13.5px !important; }
      }

      @media (max-width: 390px) {
        .chat-row.assistant .chat-bubble.chat-response:has(.mobile-command-card),
        .chat-row.assistant .chat-bubble.chat-response:has(.draft-card) {
          max-width: 78% !important;
        }

        .draft-card,
        .mobile-command-card,
        [data-mobile-card] {
          width: min(100%, 238px) !important;
          padding: 9px !important;
        }

        .mobile-command-title,
        [data-mobile-card] .mobile-command-title { font-size: 14.5px !important; }
        .mobile-command-status,
        [data-mobile-card] .mobile-command-status { font-size: 11.5px !important; }
        .mobile-command-row,
        [data-mobile-card] .mobile-command-row { grid-template-columns: 4em minmax(0,1fr) !important; }
        .mobile-command-actions button,
        [data-mobile-card] .mobile-command-actions button,
        .draft-card button { min-height: 32px !important; height: 32px !important; font-size: 13px !important; }
      }
    `;
    document.head.appendChild(style);
  }

  function applyCompactCard(card) {
    if (!card || card.getAttribute(COMPACT_ATTR) === 'true') return;
    card.setAttribute(COMPACT_ATTR, 'true');
    card.style.setProperty('width', 'min(100%, 252px)', 'important');
    card.style.setProperty('height', 'auto', 'important');
    card.style.setProperty('min-height', '0', 'important');
    card.style.setProperty('max-height', 'none', 'important');
    card.style.setProperty('display', 'block', 'important');
    card.style.setProperty('align-self', 'start', 'important');
    card.style.setProperty('margin', '4px 0 0', 'important');
    card.style.setProperty('padding', '9px 10px', 'important');
    card.style.setProperty('border-radius', '15px', 'important');
    card.style.setProperty('overflow', 'hidden', 'important');

    card.querySelectorAll('.mobile-command-head,.draft-head').forEach((el) => {
      el.style.setProperty('margin', '0 0 7px', 'important');
      el.style.setProperty('padding', '0', 'important');
      el.style.setProperty('min-height', '0', 'important');
    });
    card.querySelectorAll('.mobile-command-detail').forEach((el) => {
      el.style.setProperty('gap', '5px', 'important');
      el.style.setProperty('margin', '7px 0 9px', 'important');
      el.style.setProperty('padding', '0', 'important');
      el.style.setProperty('min-height', '0', 'important');
    });
    card.querySelectorAll('.mobile-command-row').forEach((el) => {
      el.style.setProperty('grid-template-columns', '4.2em minmax(0,1fr)', 'important');
      el.style.setProperty('gap', '7px', 'important');
      el.style.setProperty('font-size', '12.5px', 'important');
      el.style.setProperty('line-height', '1.28', 'important');
      el.style.setProperty('margin', '0', 'important');
      el.style.setProperty('padding', '0', 'important');
      el.style.setProperty('min-height', '0', 'important');
    });
    card.querySelectorAll('.mobile-command-row strong').forEach((el) => {
      el.style.setProperty('font-size', '13.5px', 'important');
      el.style.setProperty('line-height', '1.28', 'important');
      el.style.setProperty('text-align', 'right', 'important');
      el.style.setProperty('overflow-wrap', 'anywhere', 'important');
    });
    card.querySelectorAll('.mobile-command-title').forEach((el) => {
      el.style.setProperty('font-size', '14.5px', 'important');
      el.style.setProperty('line-height', '1.18', 'important');
    });
    card.querySelectorAll('.mobile-command-status').forEach((el) => {
      el.style.setProperty('font-size', '11.5px', 'important');
      el.style.setProperty('padding', '3px 8px', 'important');
    });
    card.querySelectorAll('.mobile-command-actions').forEach((el) => {
      el.style.setProperty('gap', '7px', 'important');
      el.style.setProperty('margin', '7px 0 0', 'important');
      el.style.setProperty('padding', '0', 'important');
      el.style.setProperty('min-height', '0', 'important');
    });
    card.querySelectorAll('button').forEach((el) => {
      el.style.setProperty('height', '32px', 'important');
      el.style.setProperty('min-height', '32px', 'important');
      el.style.setProperty('padding', '6px 11px', 'important');
      el.style.setProperty('border-radius', '12px', 'important');
      el.style.setProperty('font-size', '13px', 'important');
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
    observer.observe(host, { childList: true, subtree: true });
  }

  function boot() {
    if (document.documentElement.dataset.chatCardPolishReady === 'true') return;
    document.documentElement.dataset.chatCardPolishReady = 'true';
    installStyle();
    watchCards();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();
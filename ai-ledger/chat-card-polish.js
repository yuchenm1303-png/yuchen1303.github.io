(() => {
  const STYLE_ID = 'chat-card-polish-style';
  const COMPACT_ATTR = 'data-compact-command-card';

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
      .mobile-command-card,
      [data-mobile-card] {
        width: min(100%, 306px) !important;
        height: auto !important;
        min-height: 0 !important;
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
      .mobile-command-card *,
      [data-mobile-card] * { box-sizing: border-box !important; }

      .draft-head,
      .mobile-command-head,
      [data-mobile-card] .mobile-command-head {
        display: flex !important;
        align-items: center !important;
        justify-content: space-between !important;
        gap: 7px !important;
        margin: 0 0 5px !important;
        padding: 0 !important;
        min-height: 0 !important;
      }

      .draft-head strong,
      .mobile-command-title,
      [data-mobile-card] .mobile-command-title {
        display: block !important;
        font-size: 13px !important;
        line-height: 1.18 !important;
        font-weight: 850 !important;
        color: rgba(250,252,255,.96) !important;
      }

      .draft-head span,
      .mobile-command-status,
      [data-mobile-card] .mobile-command-status {
        flex: 0 0 auto !important;
        padding: 2px 7px !important;
        border-radius: 999px !important;
        font-size: 10.5px !important;
        line-height: 1.2 !important;
        font-weight: 800 !important;
        background: rgba(98,240,218,.13) !important;
        color: rgba(128,255,235,.92) !important;
      }

      .mobile-command-detail,
      [data-mobile-card] .mobile-command-detail {
        display: grid !important;
        grid-auto-rows: min-content !important;
        gap: 3px !important;
        margin: 5px 0 7px !important;
        padding: 0 !important;
        min-height: 0 !important;
      }

      .mobile-command-row,
      [data-mobile-card] .mobile-command-row {
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

      .mobile-command-row span,
      .mobile-command-message,
      [data-mobile-card] .mobile-command-row span,
      [data-mobile-card] .mobile-command-message {
        color: rgba(224,233,250,.63) !important;
      }

      .mobile-command-row strong,
      [data-mobile-card] .mobile-command-row strong {
        color: rgba(250,252,255,.94) !important;
        font-weight: 850 !important;
        text-align: right !important;
        overflow-wrap: anywhere !important;
        font-size: 11.5px !important;
        line-height: 1.25 !important;
      }

      .mobile-command-actions,
      [data-mobile-card] .mobile-command-actions {
        display: flex !important;
        align-items: center !important;
        gap: 6px !important;
        flex-wrap: wrap !important;
        margin: 4px 0 0 !important;
        padding: 0 !important;
        min-height: 0 !important;
      }

      .mobile-command-actions button,
      [data-mobile-card] .mobile-command-actions button,
      .draft-card button {
        border: 1px solid rgba(255,255,255,.15) !important;
        border-radius: 10px !important;
        min-height: 27px !important;
        height: 27px !important;
        padding: 4px 8px !important;
        font-weight: 850 !important;
        font-size: 12px !important;
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
        font-size: 11px !important;
        line-height: 1.3 !important;
      }

      @media (pointer: coarse), (max-width: 760px) {
        .draft-card,
        .mobile-command-card,
        [data-mobile-card] {
          width: min(100%, 292px) !important;
          padding: 7px 8px !important;
          border-radius: 13px !important;
        }

        .mobile-command-row,
        [data-mobile-card] .mobile-command-row {
          grid-template-columns: 42px minmax(0,1fr) !important;
          gap: 6px !important;
          font-size: 11px !important;
        }

        .mobile-command-row strong,
        [data-mobile-card] .mobile-command-row strong { font-size: 11px !important; }
      }
    `;
    document.head.appendChild(style);
  }

  function applyCompactCard(card) {
    if (!card || card.getAttribute(COMPACT_ATTR) === 'true') return;
    card.setAttribute(COMPACT_ATTR, 'true');
    card.style.setProperty('width', 'min(100%, 292px)', 'important');
    card.style.setProperty('height', 'auto', 'important');
    card.style.setProperty('min-height', '0', 'important');
    card.style.setProperty('max-height', 'none', 'important');
    card.style.setProperty('display', 'block', 'important');
    card.style.setProperty('align-self', 'start', 'important');
    card.style.setProperty('margin', '6px 0 1px', 'important');
    card.style.setProperty('padding', '7px 8px', 'important');
    card.style.setProperty('border-radius', '13px', 'important');
    card.style.setProperty('overflow', 'hidden', 'important');

    card.querySelectorAll('.mobile-command-head,.draft-head').forEach((el) => {
      el.style.setProperty('margin', '0 0 5px', 'important');
      el.style.setProperty('padding', '0', 'important');
      el.style.setProperty('min-height', '0', 'important');
    });
    card.querySelectorAll('.mobile-command-detail').forEach((el) => {
      el.style.setProperty('gap', '3px', 'important');
      el.style.setProperty('margin', '5px 0 7px', 'important');
      el.style.setProperty('padding', '0', 'important');
      el.style.setProperty('min-height', '0', 'important');
    });
    card.querySelectorAll('.mobile-command-row').forEach((el) => {
      el.style.setProperty('grid-template-columns', '42px minmax(0,1fr)', 'important');
      el.style.setProperty('gap', '6px', 'important');
      el.style.setProperty('font-size', '11px', 'important');
      el.style.setProperty('line-height', '1.25', 'important');
      el.style.setProperty('margin', '0', 'important');
      el.style.setProperty('padding', '0', 'important');
      el.style.setProperty('min-height', '0', 'important');
    });
    card.querySelectorAll('.mobile-command-actions').forEach((el) => {
      el.style.setProperty('gap', '6px', 'important');
      el.style.setProperty('margin', '4px 0 0', 'important');
      el.style.setProperty('padding', '0', 'important');
      el.style.setProperty('min-height', '0', 'important');
    });
    card.querySelectorAll('button').forEach((el) => {
      el.style.setProperty('height', '27px', 'important');
      el.style.setProperty('min-height', '27px', 'important');
      el.style.setProperty('padding', '4px 8px', 'important');
      el.style.setProperty('border-radius', '10px', 'important');
      el.style.setProperty('font-size', '12px', 'important');
      el.style.setProperty('line-height', '1', 'important');
    });
  }

  function compactAllCards() {
    document.querySelectorAll('.mobile-command-card,[data-mobile-card],.draft-card').forEach(applyCompactCard);
  }

  function watchCards() {
    compactAllCards();
    const host = document.querySelector('#chatMessages') || document.body;
    if (!host || host.dataset.compactCommandObserver === 'true') return;
    host.dataset.compactCommandObserver = 'true';
    const observer = new MutationObserver(() => window.requestAnimationFrame(compactAllCards));
    observer.observe(host, { childList: true, subtree: true });
  }

  function boot() {
    installStyle();
    watchCards();
    window.setTimeout(watchCards, 80);
    window.setTimeout(watchCards, 420);
    window.setTimeout(compactAllCards, 1200);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();
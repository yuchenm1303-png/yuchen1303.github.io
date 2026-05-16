(() => {
  'use strict';

  const STYLE_ID = 'chat-card-polish-style';
  const OBSERVER_KEY = '__chatCardPolishObserver';

  function installStyle() {
    document.getElementById(STYLE_ID)?.remove();
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      .chat-row.assistant .chat-bubble.chat-response:has(.draft-card),
      .chat-row.assistant .chat-bubble.chat-response:has(.mobile-command-card),
      .chat-row.assistant .chat-bubble.chat-response.draft-bubble,
      .chat-row.assistant .chat-bubble.chat-response.mobile-command-bubble{
        width:fit-content!important;
        max-width:min(80%,318px)!important;
        padding:6px!important;
        gap:6px!important;
        background:transparent!important;
        border-color:transparent!important;
        box-shadow:none!important;
        backdrop-filter:none!important;
        -webkit-backdrop-filter:none!important;
      }

      .draft-card,
      .mobile-command-card{
        width:min(100%,226px)!important;
        inline-size:min(100%,226px)!important;
        max-width:226px!important;
        min-height:0!important;
        height:auto!important;
        aspect-ratio:auto!important;
        display:grid!important;
        align-content:start!important;
        gap:6px!important;
        margin:2px 0 0!important;
        padding:9px!important;
        border:1px solid rgba(255,255,255,.22)!important;
        border-radius:15px!important;
        color:rgba(248,250,255,.95)!important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.116), rgba(255,255,255,.040) 58%, rgba(0,0,0,.030)),
          rgba(18,30,55,.74)!important;
        box-shadow:
          0 5px 12px rgba(0,0,0,.095),
          inset 0 .7px 0 rgba(255,255,255,.22)!important;
        backdrop-filter:none!important;
        -webkit-backdrop-filter:none!important;
        overflow:hidden!important;
      }

      .draft-card::before,
      .draft-card::after,
      .mobile-command-card::before,
      .mobile-command-card::after{
        display:none!important;
        content:none!important;
      }

      .draft-head,
      .mobile-command-head{
        display:flex!important;
        align-items:center!important;
        justify-content:space-between!important;
        gap:8px!important;
        margin:0!important;
        padding:0 0 4px!important;
        min-height:0!important;
      }

      .draft-head strong,
      .mobile-command-title{
        min-width:0!important;
        color:rgba(252,253,255,.98)!important;
        font-size:14px!important;
        line-height:1.16!important;
        font-weight:900!important;
        overflow-wrap:anywhere!important;
      }

      .draft-head span,
      .mobile-command-status{
        flex:0 0 auto!important;
        padding:3px 8px!important;
        border-radius:999px!important;
        font-size:11px!important;
        line-height:1.12!important;
        font-weight:850!important;
        color:rgba(132,255,236,.94)!important;
        background:rgba(98,240,218,.13)!important;
      }

      .draft-record,
      .mobile-command-row{
        display:grid!important;
        grid-template-columns:minmax(0,1fr) auto!important;
        align-items:center!important;
        gap:7px!important;
        margin:0!important;
        padding:5px 0!important;
        border-top:1px solid rgba(255,255,255,.075)!important;
        color:rgba(224,233,250,.70)!important;
        font-size:12px!important;
        line-height:1.22!important;
      }

      .mobile-command-row{
        grid-template-columns:3.7em minmax(0,1fr)!important;
        padding:2px 0!important;
        border-top:0!important;
      }

      .draft-record strong,
      .mobile-command-row strong{
        display:block!important;
        color:rgba(250,252,255,.96)!important;
        font-size:13px!important;
        line-height:1.22!important;
        font-weight:850!important;
        text-align:right!important;
        overflow-wrap:anywhere!important;
      }

      .draft-record div strong{
        text-align:left!important;
      }

      .draft-record span{
        display:block!important;
        margin-top:2px!important;
        color:rgba(224,233,250,.62)!important;
        font-size:11px!important;
        line-height:1.18!important;
      }

      .draft-record em{
        color:rgba(250,252,255,.96)!important;
        font-style:normal!important;
        font-size:13px!important;
        line-height:1.2!important;
        font-weight:900!important;
      }

      .mobile-command-detail{
        display:grid!important;
        gap:2px!important;
        margin:2px 0 5px!important;
        padding:0!important;
      }

      .confirm-draft,
      .cancel-draft,
      .mobile-command-actions button{
        min-height:28px!important;
        height:28px!important;
        padding:4px 10px!important;
        border-radius:12px!important;
        border:1px solid rgba(255,255,255,.15)!important;
        font-size:12px!important;
        line-height:1!important;
        font-weight:880!important;
        box-shadow:inset 0 .7px 0 rgba(255,255,255,.14), 0 4px 8px rgba(0,0,0,.075)!important;
      }

      .confirm-draft,
      .mobile-command-confirm{
        color:#fff!important;
        background:linear-gradient(135deg, rgba(20,190,190,.86), rgba(45,139,220,.78))!important;
      }

      .cancel-draft,
      .mobile-command-cancel{
        color:rgba(242,247,255,.86)!important;
        background:rgba(255,255,255,.075)!important;
      }

      .mobile-command-actions{
        display:flex!important;
        flex-wrap:wrap!important;
        gap:6px!important;
        margin:4px 0 0!important;
        padding:0!important;
      }

      .mobile-command-message{
        margin-top:5px!important;
        color:rgba(224,233,250,.66)!important;
        font-size:11.5px!important;
        line-height:1.28!important;
      }

      @media(max-width:390px){
        .chat-row.assistant .chat-bubble.chat-response:has(.draft-card),
        .chat-row.assistant .chat-bubble.chat-response:has(.mobile-command-card),
        .chat-row.assistant .chat-bubble.chat-response.draft-bubble,
        .chat-row.assistant .chat-bubble.chat-response.mobile-command-bubble{
          max-width:78%!important;
        }
        .draft-card,
        .mobile-command-card{
          width:min(100%,214px)!important;
          inline-size:min(100%,214px)!important;
          max-width:214px!important;
          padding:8px!important;
        }
      }
    `;
    document.head.appendChild(style);
  }

  function tagCards(root = document) {
    root.querySelectorAll?.('.draft-card').forEach((card) => {
      card.closest('.chat-bubble.chat-response')?.classList.add('draft-bubble');
    });
    root.querySelectorAll?.('.mobile-command-card').forEach((card) => {
      card.closest('.chat-bubble.chat-response')?.classList.add('mobile-command-bubble');
    });
  }

  function installObserver() {
    const host = document.getElementById('chatMessages');
    if (!host || host[OBSERVER_KEY]) return;
    let frame = 0;
    const observer = new MutationObserver(() => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => tagCards(host));
    });
    observer.observe(host, { childList: true, subtree: true });
    host[OBSERVER_KEY] = observer;
    tagCards(host);
  }

  function boot() {
    installStyle();
    installObserver();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();
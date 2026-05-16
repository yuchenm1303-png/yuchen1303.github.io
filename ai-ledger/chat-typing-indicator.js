(() => {
  'use strict';

  const STYLE_ID = 'chat-typing-indicator-style';

  function installStyle() {
    if (document.getElementById(STYLE_ID)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      #typingRow .chat-bubble{min-width:168px;min-height:44px;display:inline-flex;align-items:center;justify-content:flex-start;gap:8px;padding:12px 16px;border-radius:24px;background:linear-gradient(135deg,rgba(255,255,255,.19),rgba(255,255,255,.08)),rgba(138,118,255,.08);position:relative;overflow:hidden!important;border:1px solid rgba(255,255,255,.20);box-shadow:inset 0 1px 0 rgba(255,255,255,.28),0 12px 28px rgba(25,20,60,.18);}
      #typingRow .chat-bubble::before{content:'正在生成';position:relative;z-index:2;font-size:13px;font-weight:850;color:rgba(255,255,255,.86);letter-spacing:.02em;text-shadow:0 1px 8px rgba(255,255,255,.20);}
      #typingRow .chat-bubble::after{content:'';position:absolute;inset:0;background:linear-gradient(105deg,transparent 0%,rgba(139,247,255,.00) 24%,rgba(139,247,255,.22) 44%,rgba(188,160,255,.28) 52%,rgba(139,247,255,.00) 70%,transparent 100%);transform:translateX(-120%);animation:aiLiquidSweep 1.9s ease-in-out infinite;pointer-events:none;}
      #typingRow .typing-dot{position:relative;z-index:2;display:inline-block!important;width:6px;height:6px;border-radius:999px;background:rgba(210,235,255,.90);box-shadow:0 0 10px rgba(149,217,255,.55);animation:aiSoftDot 1.15s ease-in-out infinite;}
      #typingRow .typing-dot:nth-child(2){animation-delay:.16s;}
      #typingRow .typing-dot:nth-child(3){animation-delay:.32s;}
      @keyframes aiLiquidSweep{0%{transform:translateX(-120%);opacity:.25}45%{opacity:.95}100%{transform:translateX(120%);opacity:.25}}
      @keyframes aiSoftDot{0%,100%{transform:translateY(2px) scale(.72);opacity:.42}50%{transform:translateY(-1px) scale(1.06);opacity:1}}
    `;
    document.head.appendChild(style);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', installStyle, { once: true });
  else installStyle();
})();

(() => {
  const STYLE_ID = 'assistant-splash-style';
  const SPLASH_ID = 'assistantSplash';

  function installStyle() {
    if (document.querySelector(`#${STYLE_ID}`)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      html.assistant-boot, html.assistant-boot body{background:#071827!important;overflow:hidden}
      .assistant-splash{position:fixed;inset:0;z-index:99999;display:grid;place-items:center;background:radial-gradient(circle at 30% 18%,rgba(72,245,230,.36),transparent 34%),radial-gradient(circle at 72% 72%,rgba(124,82,255,.36),transparent 38%),linear-gradient(145deg,#071827,#0b2140 45%,#1b1450);color:#efffff;transition:opacity .55s ease,visibility .55s ease;overflow:hidden}
      .assistant-splash::before{content:'';position:absolute;inset:-20%;background-image:radial-gradient(circle,rgba(255,255,255,.62) 0 1px,transparent 2px);background-size:76px 76px;opacity:.25;animation:splashStars 12s linear infinite}
      .assistant-splash::after{content:'';position:absolute;width:520px;height:520px;border-radius:50%;background:radial-gradient(circle,rgba(100,255,246,.24),transparent 62%);filter:blur(12px);animation:splashBreathe 2.4s ease-in-out infinite}
      .assistant-splash.hide{opacity:0;visibility:hidden;pointer-events:none}
      .assistant-splash-core{position:relative;z-index:1;display:grid;place-items:center;text-align:center;gap:18px;transform:translateY(-12px)}
      .assistant-splash-orb{position:relative;width:132px;height:132px;border-radius:38px;background:linear-gradient(135deg,#42f5e8,#6477ff 52%,#8a3ffc);box-shadow:0 0 34px rgba(84,245,235,.62),0 32px 80px rgba(12,8,70,.45);display:grid;place-items:center;animation:splashFloat 2.2s ease-in-out infinite}
      .assistant-splash-orb::before{content:'';position:absolute;inset:-24px;border:1px solid rgba(210,255,255,.42);border-radius:50%;animation:splashRing 1.8s ease-out infinite}
      .assistant-splash-orb::after{content:'';position:absolute;inset:-48px;border:1px solid rgba(210,255,255,.18);border-radius:50%;animation:splashRing 2.2s ease-out infinite .2s}
      .assistant-splash-star{position:relative;width:72px;height:72px;filter:drop-shadow(0 0 16px rgba(255,255,255,.88))}
      .assistant-splash-star::before{content:'';position:absolute;inset:0;background:#fff;clip-path:polygon(50% 0,61% 36%,100% 50%,61% 64%,50% 100%,39% 64%,0 50%,39% 36%)}
      .assistant-splash-star::after{content:'';position:absolute;inset:22px;background:#55f5ec;clip-path:polygon(50% 0,61% 36%,100% 50%,61% 64%,50% 100%,39% 64%,0 50%,39% 36%)}
      .assistant-splash-title{margin:10px 0 0;font-size:34px;font-weight:900;letter-spacing:.06em}
      .assistant-splash-sub{margin:0;color:rgba(235,252,255,.72);font-size:15px;letter-spacing:.08em}
      .assistant-splash-line{width:170px;height:3px;border-radius:999px;background:linear-gradient(90deg,transparent,#6ff7ef,#8d7aff,transparent);animation:splashScan 1.25s ease-in-out infinite}
      @keyframes splashFloat{0%,100%{transform:translateY(0) scale(1)}50%{transform:translateY(-8px) scale(1.025)}}
      @keyframes splashRing{0%{transform:scale(.72);opacity:.75}100%{transform:scale(1.35);opacity:0}}
      @keyframes splashBreathe{0%,100%{transform:scale(.92);opacity:.72}50%{transform:scale(1.05);opacity:1}}
      @keyframes splashStars{from{transform:translate3d(0,0,0)}to{transform:translate3d(76px,76px,0)}}
      @keyframes splashScan{0%,100%{opacity:.35;transform:scaleX(.72)}50%{opacity:1;transform:scaleX(1)}}
    `;
    document.head.appendChild(style);
  }

  function showSplash() {
    if (document.querySelector(`#${SPLASH_ID}`)) return;
    document.documentElement.classList.add('assistant-boot');
    const splash = document.createElement('div');
    splash.id = SPLASH_ID;
    splash.className = 'assistant-splash';
    splash.innerHTML = `
      <div class="assistant-splash-core">
        <div class="assistant-splash-orb"><div class="assistant-splash-star"></div></div>
        <div>
          <h1 class="assistant-splash-title">AI助手</h1>
          <p class="assistant-splash-sub">正在唤醒个人智能核心</p>
        </div>
        <div class="assistant-splash-line"></div>
      </div>
    `;
    document.body.prepend(splash);
    window.setTimeout(() => {
      splash.classList.add('hide');
      document.documentElement.classList.remove('assistant-boot');
      window.setTimeout(() => splash.remove(), 650);
    }, 1750);
  }

  installStyle();
  if (document.body) showSplash();
  else window.addEventListener('DOMContentLoaded', showSplash, { once: true });
})();

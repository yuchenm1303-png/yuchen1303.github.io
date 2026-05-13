(() => {
  const STYLE_ID = 'assistant-splash-style';
  const SPLASH_ID = 'assistantSplash';

  function installStyle() {
    if (document.querySelector(`#${STYLE_ID}`)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      html.assistant-boot, html.assistant-boot body{background:#08111c!important;overflow:hidden}
      .assistant-splash{position:fixed;inset:0;z-index:99999;display:grid;place-items:center;background:linear-gradient(145deg,#f7f9fb 0%,#edf1f5 44%,#dde5ec 100%);color:#111827;transition:opacity .62s cubic-bezier(.22,1,.36,1),visibility .62s cubic-bezier(.22,1,.36,1);overflow:hidden}
      .assistant-splash::before{content:'';position:absolute;inset:0;background:radial-gradient(circle at 50% 42%,rgba(255,255,255,.96),transparent 31%),linear-gradient(180deg,rgba(255,255,255,.7),rgba(255,255,255,0));opacity:.86}
      .assistant-splash::after{content:'';position:absolute;left:50%;top:50%;width:460px;height:460px;transform:translate(-50%,-50%);border-radius:50%;border:1px solid rgba(17,24,39,.08);box-shadow:0 0 0 88px rgba(255,255,255,.18);opacity:.72;animation:compassHalo 1.9s cubic-bezier(.22,1,.36,1) both}
      .assistant-splash.hide{opacity:0;visibility:hidden;pointer-events:none}
      .assistant-splash-core{position:relative;z-index:1;display:grid;place-items:center;text-align:center;gap:18px;transform:translateY(-10px)}
      .assistant-splash-mark{position:relative;width:150px;height:150px;display:grid;place-items:center;filter:drop-shadow(0 24px 30px rgba(15,23,42,.20));animation:markEnter 1.15s cubic-bezier(.22,1,.36,1) both}
      .assistant-splash-mark::before{content:'';position:absolute;inset:0;border-radius:50%;background:radial-gradient(circle,rgba(15,23,42,.08),transparent 62%);animation:softPulse 1.8s ease-in-out infinite}
      .assistant-compass{position:relative;width:132px;height:132px;background:linear-gradient(145deg,#f8fbff,#b9c3ce 58%,#7f8d9d);clip-path:polygon(50% 0%,57% 42%,100% 16%,64% 50%,100% 84%,57% 58%,50% 100%,43% 58%,0% 84%,36% 50%,0% 16%,43% 42%)}
      .assistant-compass::before{content:'';position:absolute;inset:21px;background:linear-gradient(145deg,rgba(10,18,30,.24),rgba(255,255,255,.06));clip-path:polygon(50% 0%,57% 42%,100% 16%,64% 50%,100% 84%,57% 58%,50% 100%,43% 58%,0% 84%,36% 50%,0% 16%,43% 42%)}
      .assistant-compass::after{content:'';position:absolute;left:50%;top:50%;width:16px;height:16px;border-radius:50%;background:#f8fbff;transform:translate(-50%,-50%);box-shadow:0 0 0 1px rgba(15,23,42,.18)}
      .assistant-splash-title{margin:12px 0 0;font-size:32px;font-weight:800;letter-spacing:.08em;color:#111827}
      .assistant-splash-sub{margin:0;color:rgba(17,24,39,.54);font-size:14px;letter-spacing:.16em;font-weight:600}
      .assistant-splash-line{width:150px;height:1px;border-radius:999px;background:linear-gradient(90deg,transparent,rgba(17,24,39,.45),transparent);animation:lineScan 1.35s cubic-bezier(.22,1,.36,1) infinite}
      @keyframes markEnter{0%{opacity:0;transform:scale(.78) rotate(-18deg)}100%{opacity:1;transform:scale(1) rotate(0deg)}}
      @keyframes compassHalo{0%{opacity:0;transform:translate(-50%,-50%) scale(.72)}100%{opacity:.72;transform:translate(-50%,-50%) scale(1)}}
      @keyframes softPulse{0%,100%{transform:scale(.92);opacity:.44}50%{transform:scale(1.08);opacity:.82}}
      @keyframes lineScan{0%,100%{opacity:.28;transform:scaleX(.55)}50%{opacity:1;transform:scaleX(1)}}
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
        <div class="assistant-splash-mark"><div class="assistant-compass"></div></div>
        <div>
          <h1 class="assistant-splash-title">AI助手</h1>
          <p class="assistant-splash-sub">PERSONAL INTELLIGENCE</p>
        </div>
        <div class="assistant-splash-line"></div>
      </div>
    `;
    document.body.prepend(splash);
    window.setTimeout(() => {
      splash.classList.add('hide');
      document.documentElement.classList.remove('assistant-boot');
      window.setTimeout(() => splash.remove(), 700);
    }, 1650);
  }

  installStyle();
  if (document.body) showSplash();
  else window.addEventListener('DOMContentLoaded', showSplash, { once: true });
})();

(() => {
  function $(s) { return document.querySelector(s); }
  function setText(s, v) { const el = $(s); if (el) el.textContent = v; }

  function installStyle() {
    if ($('#tools-center-style')) return;
    const style = document.createElement('style');
    style.id = 'tools-center-style';
    style.textContent = [
      ".nav-btn[data-view='list']{display:none!important}",
      ".bottom-nav{grid-template-columns:repeat(3,1fr)!important}",
      ".tools-home{display:grid;gap:16px;padding-bottom:110px}",
      ".tools-hero{padding:22px;border-radius:30px;border:1px solid rgba(255,255,255,.32);background:rgba(255,255,255,.14);box-shadow:0 22px 55px rgba(8,35,45,.18);backdrop-filter:blur(18px)}",
      ".tools-hero .eyebrow{margin:0 0 8px;color:#86ece2;font-size:14px;font-weight:900;letter-spacing:.08em}",
      ".tools-hero h1{margin:0;color:#eefcff;font-size:34px;line-height:1.05}",
      ".tools-hero p{margin:10px 0 0;color:rgba(235,252,255,.72);font-size:15px;line-height:1.5}",
      ".tools-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}",
      ".tool-card{min-height:132px;padding:16px;text-align:left;color:#f2ffff;cursor:pointer;border:1px solid rgba(255,255,255,.35);border-radius:26px;background:rgba(255,255,255,.16);box-shadow:0 18px 42px rgba(0,40,50,.16);backdrop-filter:blur(18px)}",
      ".tool-icon{width:40px;height:40px;border-radius:16px;display:grid;place-items:center;margin-bottom:12px;background:rgba(255,255,255,.16);font-size:21px}",
      ".tool-card h3{margin:0 0 7px;font-size:18px;color:#f5ffff}",
      ".tool-card p{margin:0;color:rgba(235,252,255,.68);font-size:13px;line-height:1.45}",
      ".tools-panel{display:none;gap:14px;padding-bottom:110px}",
      ".tools-panel.open{display:grid}",
      ".tools-back{width:max-content;margin:0 0 14px;border:1px solid rgba(255,255,255,.34);border-radius:999px;padding:9px 14px;background:rgba(255,255,255,.14);color:#efffff;font-weight:800}",
      ".tools-panel-card{padding:18px;border-radius:28px;border:1px solid rgba(255,255,255,.32);background:rgba(255,255,255,.14);box-shadow:0 20px 46px rgba(0,40,50,.15);backdrop-filter:blur(18px);color:#f4ffff}",
      ".tools-chip-row{display:flex;gap:10px;flex-wrap:wrap;margin-top:14px}.tools-chip-row button{border:1px solid rgba(255,255,255,.36);border-radius:999px;padding:10px 14px;background:rgba(255,255,255,.14);color:#efffff;font-weight:800}"
    ].join('\n');
    document.head.appendChild(style);
  }

  function makeHome() {
    const view = $('#view-stats');
    if (!view || $('#toolsHome')) return;
    view.innerHTML = '<section id="toolsHome" class="tools-home">' +
      '<header class="tools-hero reveal"><p class="eyebrow">功能中心</p><h1>工具与能力</h1><p>账单、统计、提醒闹钟、应用控制、快捷指令和任务记录都收在这里。</p></header>' +
      '<section class="tools-grid reveal delay-1">' +
      card('ledger','▤','账单中心','查看流水、新增账单、导出账单数据。') +
      card('stats','▣','数据统计','查看收支趋势、分类占比和预算。') +
      card('alarm','⏰','提醒闹钟','生成提醒任务，Android 版可调用系统闹钟。') +
      card('apps','◎','应用控制','打开微信、支付宝等常用 App。') +
      card('shortcuts','✦','快捷指令','沉淀高频任务，一句话执行组合动作。') +
      card('tasks','✓','任务记录','查看提醒、打开应用等执行记录。') +
      '</section></section><section id="toolsPanel" class="tools-panel"></section>';
  }

  function card(key, icon, title, text) {
    return '<button class="tool-card" type="button" data-tool="' + key + '"><div class="tool-icon">' + icon + '</div><h3>' + title + '</h3><p>' + text + '</p></button>';
  }

  function openAi(text) {
    $(".bottom-nav .nav-btn[data-view='ai']")?.click();
    setTimeout(() => {
      const input = $('#aiInput');
      const form = $('#chatForm');
      if (!input || !form) return;
      input.value = text;
      if (form.requestSubmit) form.requestSubmit();
      else form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    }, 120);
  }

  function showHome() {
    const home = $('#toolsHome');
    const panel = $('#toolsPanel');
    if (home) home.style.display = '';
    if (panel) { panel.classList.remove('open'); panel.innerHTML = ''; }
  }

  function showPanel(title, text, a, b) {
    const home = $('#toolsHome');
    const panel = $('#toolsPanel');
    if (!panel) return;
    if (home) home.style.display = 'none';
    panel.innerHTML = '<button class="tools-back" type="button" data-back-tools>← 功能中心</button><article class="tools-panel-card"><h2>' + title + '</h2><p>' + text + '</p><div class="tools-chip-row"><button type="button" data-prompt="' + a[1] + '">' + a[0] + '</button><button type="button" data-prompt="' + b[1] + '">' + b[0] + '</button></div></article>';
    panel.classList.add('open');
  }

  function handleTool(tool) {
    if (tool === 'ledger') return $(".bottom-nav .nav-btn[data-view='list']")?.click();
    if (tool === 'stats') return alert('数据统计已收进功能中心，后续会做成子页面。');
    if (tool === 'alarm') return showPanel('提醒闹钟','通过 AI 生成提醒或系统闹钟动作卡片。',['明早8点叫我起床','明天早上8点叫我起床'],['今晚9点提醒复习','今晚9点提醒我复习']);
    if (tool === 'apps') return showPanel('应用控制','通过 AI 生成打开应用动作卡片。',['打开微信','打开微信'],['打开支付宝','打开支付宝']);
    if (tool === 'shortcuts') return showPanel('快捷指令','沉淀高频任务，一句话执行组合动作。',['记一笔午饭','今天午饭28'],['查餐饮开支','我这个月餐饮花了多少']);
    if (tool === 'tasks') return showPanel('任务记录','后续这里会集中展示手机动作执行记录。',['查看对话','你好'],['打开支付宝','打开支付宝']);
  }

  document.addEventListener('click', (e) => {
    const tool = e.target.closest('[data-tool]')?.dataset.tool;
    if (tool) return handleTool(tool);
    if (e.target.closest('[data-back-tools]')) return showHome();
    const prompt = e.target.closest('[data-prompt]')?.dataset.prompt;
    if (prompt) return openAi(prompt);
  });

  window.addEventListener('DOMContentLoaded', () => {
    installStyle();
    setText(".bottom-nav .nav-btn[data-view='stats'] em", '功能');
    setText(".bottom-nav .nav-btn[data-view='ai'] em", 'AI助手');
    makeHome();
    setTimeout(makeHome, 300);
  });
})();

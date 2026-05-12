(() => {
  const STYLE_ID = 'tools-center-style';

  function $(selector) {
    return document.querySelector(selector);
  }

  function setText(selector, value) {
    const el = $(selector);
    if (el) el.textContent = value;
  }

  function installStyles() {
    if ($(`#${STYLE_ID}`)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      .nav-btn[data-view='list']{display:none!important}
      .bottom-nav{grid-template-columns:repeat(3,1fr)!important}
      .tools-home{display:grid;gap:16px;padding-bottom:110px}
      .tools-hero{padding:22px;border-radius:30px;border:1px solid rgba(255,255,255,.32);background:rgba(255,255,255,.14);box-shadow:0 22px 55px rgba(8,35,45,.18);backdrop-filter:blur(18px)}
      .tools-hero .eyebrow{margin:0 0 8px;color:#86ece2;font-size:14px;font-weight:900;letter-spacing:.08em}
      .tools-hero h1{margin:0;color:#eefcff;font-size:34px;line-height:1.05}
      .tools-hero p{margin:10px 0 0;color:rgba(235,252,255,.72);font-size:15px;line-height:1.5}
      .tools-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}
      .tool-card{min-height:132px;padding:16px;border:1px solid rgba(255,255,255,.35);border-radius:26px;background:rgba(255,255,255,.16);box-shadow:0 18px 42px rgba(0,40,50,.16);backdrop-filter:blur(18px);color:#f2ffff;text-align:left;cursor:pointer}
      .tool-card:active{transform:scale(.985)}
      .tool-icon{width:40px;height:40px;border-radius:16px;display:grid;place-items:center;margin-bottom:12px;background:rgba(255,255,255,.18);font-size:21px}
      .tool-card h3{margin:0 0 7px;font-size:18px;color:#f5ffff}
      .tool-card p{margin:0;color:rgba(235,252,255,.66);font-size:13px;line-height:1.45}
      .tools-panel{display:none;gap:14px;padding-bottom:110px}
      .tools-panel.open{display:grid}
      .tools-back{width:max-content;border:1px solid rgba(255,255,255,.34);border-radius:999px;padding:9px 14px;background:rgba(255,255,255,.14);color:#efffff;font-weight:800}
      .tools-panel-card{padding:18px;border-radius:28px;border:1px solid rgba(255,255,255,.32);background:rgba(255,255,255,.14);box-shadow:0 20px 46px rgba(0,40,50,.15);backdrop-filter:blur(18px);color:#f4ffff}
      .tools-panel-card h2{margin:0 0 8px;font-size:24px}.tools-panel-card p{margin:0;color:rgba(235,252,255,.70);line-height:1.6}
      .tools-chip-row{display:flex;gap:10px;flex-wrap:wrap;margin-top:14px}.tools-chip-row button{border:1px solid rgba(255,255,255,.36);border-radius:999px;padding:10px 14px;background:rgba(255,255,255,.14);color:#efffff;font-weight:800}
      .tools-detail-shell{display:none}.tools-detail-shell.open{display:block}.tools-detail-shell .tools-back{margin-bottom:14px}
      #view-list .tools-back{margin:0 0 14px 0}
      @media(max-width:420px){.tools-grid{gap:10px}.tool-card{min-height:120px;padding:14px}.tools-hero h1{font-size:30px}}
    `;
    document.head.appendChild(style);
  }

  function updateNav() {
    const statsBtn = $(".bottom-nav .nav-btn[data-view='stats']");
    const listBtn = $(".bottom-nav .nav-btn[data-view='list']");
    if (statsBtn) {
      const icon = statsBtn.querySelector('span');
      const label = statsBtn.querySelector('em');
      if (icon) icon.textContent = '▦';
      if (label) label.textContent = '功能';
      statsBtn.setAttribute('aria-label', '功能');
    }
    if (listBtn) listBtn.setAttribute('aria-hidden', 'true');
  }

  function moveStatsIntoDetail() {
    const view = $('#view-stats');
    if (!view || $('#toolsHome')) return;
    const detail = document.createElement('div');
    detail.id = 'statsDetailShell';
    detail.className = 'tools-detail-shell';
    detail.innerHTML = '<button class="tools-back" type="button" data-tools-home>← 功能中心</button>';
    const body = document.createElement('div');
    body.id = 'statsDetailBody';
    [...view.childNodes].forEach((node) => body.appendChild(node));
    detail.appendChild(body);

    const home = document.createElement('section');
    home.id = 'toolsHome';
    home.className = 'tools-home';
    home.innerHTML = `
      <header class="tools-hero reveal">
        <p class="eyebrow">功能中心</p>
        <h1>工具与能力</h1>
        <p>账单、统计、提醒闹钟和应用控制都收在这里。底部只保留 AI 助手、功能、设置，整体更像一个多功能 AI。</p>
      </header>
      <section class="tools-grid reveal delay-1">
        <button class="tool-card" type="button" data-tool="ledger"><div class="tool-icon">▤</div><h3>账单中心</h3><p>查看流水、新增账单、导出账单数据。</p></button>
        <button class="tool-card" type="button" data-tool="stats"><div class="tool-icon">▣</div><h3>数据统计</h3><p>查看本月收支、趋势图、分类占比和预算。</p></button>
        <button class="tool-card" type="button" data-tool="alarm"><div class="tool-icon">⏰</div><h3>提醒闹钟</h3><p>创建提醒任务，后续接入系统闹钟执行。</p></button>
        <button class="tool-card" type="button" data-tool="apps"><div class="tool-icon">◎</div><h3>应用控制</h3><p>打开常用 App，管理手机任务卡片。</p></button>
      </section>
    `;

    const panel = document.createElement('section');
    panel.id = 'toolsPanel';
    panel.className = 'tools-panel';

    view.appendChild(home);
    view.appendChild(detail);
    view.appendChild(panel);
  }

  function showToolsHome() {
    $('#toolsHome')?.style.removeProperty('display');
    $('#toolsPanel')?.classList.remove('open');
    $('#statsDetailShell')?.classList.remove('open');
    $('#view-stats')?.classList.add('active');
  }

  function showStatsDetail() {
    const home = $('#toolsHome');
    if (home) home.style.display = 'none';
    $('#toolsPanel')?.classList.remove('open');
    $('#statsDetailShell')?.classList.add('open');
  }

  function showSimplePanel(kind) {
    const home = $('#toolsHome');
    const panel = $('#toolsPanel');
    if (!panel) return;
    if (home) home.style.display = 'none';
    $('#statsDetailShell')?.classList.remove('open');

    const isAlarm = kind === 'alarm';
    panel.innerHTML = `
      <button class="tools-back" type="button" data-tools-home>← 功能中心</button>
      <article class="tools-panel-card">
        <h2>${isAlarm ? '提醒闹钟' : '应用控制'}</h2>
        <p>${isAlarm ? '这里后续会显示提醒列表、闹钟任务和执行记录。现在可以先通过 AI 对话生成任务卡片。' : '这里后续会显示常用应用快捷入口和手机控制记录。现在可以先通过 AI 对话生成打开应用卡片。'}</p>
        <div class="tools-chip-row">
          ${isAlarm
            ? '<button type="button" data-send-ai="明天早上8点叫我起床">明早 8 点叫我起床</button><button type="button" data-send-ai="今晚9点提醒我复习">今晚 9 点提醒复习</button>'
            : '<button type="button" data-send-ai="打开微信">打开微信</button><button type="button" data-send-ai="打开支付宝">打开支付宝</button>'}
        </div>
      </article>
    `;
    panel.classList.add('open');
  }

  function openListDetail() {
    const listBtn = $(".bottom-nav .nav-btn[data-view='list']");
    listBtn?.click();
    window.setTimeout(() => {
      const view = $('#view-list');
      if (!view || view.querySelector('[data-list-back]')) return;
      const back = document.createElement('button');
      back.className = 'tools-back';
      back.type = 'button';
      back.dataset.listBack = '1';
      back.textContent = '← 功能中心';
      back.addEventListener('click', () => $(".bottom-nav .nav-btn[data-view='stats']")?.click());
      view.insertBefore(back, view.firstChild);
      setText('#view-list .eyebrow', '账单中心');
      setText('#view-list h1', '账单流水');
      setText('#view-list .subtext', '查看和管理最近的收入、支出明细。');
    }, 80);
  }

  function sendToAi(text) {
    const aiBtn = $(".bottom-nav .nav-btn[data-view='ai']");
    aiBtn?.click();
    window.setTimeout(() => {
      const input = $('#aiInput');
      const form = $('#chatForm');
      if (!input || !form) return;
      input.value = text;
      form.requestSubmit ? form.requestSubmit() : form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    }, 120);
  }

  function installHandlers() {
    document.addEventListener('click', (event) => {
      const tool = event.target.closest('[data-tool]')?.dataset.tool;
      if (tool === 'ledger') return openListDetail();
      if (tool === 'stats') return showStatsDetail();
      if (tool === 'alarm' || tool === 'apps') return showSimplePanel(tool);
      if (event.target.closest('[data-tools-home]')) return showToolsHome();
      const send = event.target.closest('[data-send-ai]')?.dataset.sendAi;
      if (send) return sendToAi(send);
    });

    const statsBtn = $(".bottom-nav .nav-btn[data-view='stats']");
    statsBtn?.addEventListener('click', () => window.setTimeout(showToolsHome, 80));
  }

  function init() {
    installStyles();
    updateNav();
    moveStatsIntoDetail();
    showToolsHome();
    installHandlers();
  }

  window.addEventListener('DOMContentLoaded', () => {
    window.setTimeout(init, 180);
    window.setTimeout(() => { updateNav(); showToolsHome(); }, 600);
  });
})();

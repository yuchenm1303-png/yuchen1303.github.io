(() => {
  'use strict';

  const STYLE_ID = 'settings-groups-style';
  const DETAIL_ID = 'settingsGroupDetail';
  let returnAnchor = null;
  let movedNodes = [];
  let settingsObserver = null;

  function installStyle() {
    if (document.getElementById(STYLE_ID)) return;

    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      .settings-group-list{
        display:grid;
        gap:14px;
        margin-bottom:16px;
      }

      .settings-group-card,
      .settings-group-card:hover,
      .settings-group-card:active,
      .settings-group-card:focus{
        position:relative!important;
        z-index:2!important;
        width:100%;
        min-height:108px;
        display:grid;
        grid-template-columns:auto 1fr auto;
        align-items:center;
        gap:14px;
        padding:18px!important;
        border:1px solid rgba(255,255,255,.18)!important;
        border-radius:30px!important;
        color:rgba(248,250,255,.96)!important;
        text-align:left;
        cursor:pointer;
        outline:none!important;
        overflow:hidden!important;
        opacity:1!important;
        visibility:visible!important;
        pointer-events:auto!important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.088), rgba(255,255,255,.026) 48%, rgba(0,0,0,.020)),
          radial-gradient(circle at 20% 0%, rgba(255,255,255,.070), transparent 48%),
          rgba(255,255,255,var(--assistant-glass-panel-alpha,.044))!important;
        box-shadow:
          0 12px 26px rgba(0,0,0,.15),
          inset 0 .8px 0 rgba(255,255,255,.28),
          inset 0 -.8px 0 rgba(0,0,0,.07)!important;
        -webkit-tap-highlight-color:transparent;
      }

      .settings-group-card::before,
      .settings-group-card::after{
        display:none!important;
        content:none!important;
      }

      .settings-group-card > *,
      .settings-group-icon,
      .settings-group-title,
      .settings-group-desc,
      .settings-group-arrow{
        position:relative!important;
        z-index:3!important;
        opacity:1!important;
        visibility:visible!important;
        -webkit-text-fill-color:currentColor!important;
      }

      .settings-group-icon{
        width:46px;
        height:46px;
        border-radius:17px;
        display:grid;
        place-items:center;
        background:rgba(255,255,255,.072)!important;
        box-shadow:inset 0 .8px 0 rgba(255,255,255,.26), 0 6px 14px rgba(0,0,0,.10);
        font-size:20px;
        font-weight:900;
        color:rgba(248,250,255,.95)!important;
      }

      .settings-group-title{
        display:block;
        font-size:18px;
        line-height:1.22;
        font-weight:900;
        color:rgba(248,250,255,.98)!important;
        text-shadow:0 1px 2px rgba(0,0,0,.16);
      }

      .settings-group-desc{
        display:block;
        margin-top:5px;
        font-size:13px;
        line-height:1.45;
        color:rgba(220,230,250,.76)!important;
      }

      .settings-group-arrow{
        font-size:30px;
        line-height:1;
        color:rgba(248,250,255,.76)!important;
        font-weight:500;
      }

      .settings-group-hidden{display:none!important}

      .settings-group-detail{
        position:fixed;
        z-index:92;
        inset:0;
        display:none;
        padding:18px;
        background:rgba(3,7,18,.72);
        pointer-events:none;
      }

      .settings-group-detail.open{
        display:grid;
        place-items:end center;
        pointer-events:auto;
      }

      .settings-group-detail.preparing .settings-group-content{
        opacity:0!important;
      }

      .settings-group-detail.ready .settings-group-content{
        opacity:1!important;
        transition:opacity 90ms ease!important;
      }

      .settings-group-sheet{
        width:min(100%,520px);
        max-height:min(86vh,820px);
        overflow:auto;
        padding:18px;
        border-radius:30px;
        border:1px solid rgba(255,255,255,.22);
        background:
          linear-gradient(145deg,rgba(255,255,255,.105),rgba(255,255,255,.040) 45%,rgba(0,0,0,.040)),
          rgba(17,28,54,.94);
        box-shadow:0 24px 56px rgba(0,0,0,.42), inset 0 1px 0 rgba(255,255,255,.22);
      }

      .settings-group-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:14px}
      .settings-group-head h2{margin:0;font-size:22px;font-weight:900;color:rgba(248,250,255,.98)}
      .settings-group-head p{margin:6px 0 0;font-size:13px;line-height:1.55;color:rgba(214,224,246,.72)}
      .settings-group-close{width:40px;height:40px;border-radius:15px;border:1px solid rgba(255,255,255,.16);background:rgba(255,255,255,var(--assistant-glass-control-alpha,.052));color:rgba(248,250,255,.92);font-size:24px}
      .settings-group-content>.glass-card{display:block!important;margin-bottom:14px;opacity:1!important;visibility:visible!important}
      .settings-group-content>.glass-card:last-child{margin-bottom:0}

      @media(max-width:720px){
        .settings-group-detail{padding:10px;background:rgba(4,8,20,.78)}
        .settings-group-sheet{border-radius:28px 28px 0 0;max-height:84vh}
        .settings-group-card{min-height:104px;border-radius:28px!important}
      }
    `;
    document.head.appendChild(style);
  }

  function makeEntry({ id, icon, title, desc }) {
    const section = document.createElement('section');
    section.className = 'settings-group-card';
    section.dataset.settingsGroup = id;
    section.setAttribute('role', 'button');
    section.setAttribute('tabindex', '0');
    section.innerHTML = `
      <span class="settings-group-icon">${icon}</span>
      <span><span class="settings-group-title">${title}</span><span class="settings-group-desc">${desc}</span></span>
      <span class="settings-group-arrow">›</span>
    `;
    return section;
  }

  function ensureDetail() {
    let detail = document.getElementById(DETAIL_ID);
    if (detail) return detail;
    detail = document.createElement('div');
    detail.id = DETAIL_ID;
    detail.className = 'settings-group-detail';
    detail.innerHTML = `
      <section class="settings-group-sheet" role="dialog" aria-modal="true">
        <div class="settings-group-head">
          <div><h2 id="settingsGroupTitle">设置</h2><p id="settingsGroupDesc">调整相关选项。</p></div>
          <button class="settings-group-close" type="button" aria-label="关闭">×</button>
        </div>
        <div id="settingsGroupContent" class="settings-group-content"></div>
      </section>
    `;
    document.body.appendChild(detail);
    detail.addEventListener('click', (event) => {
      if (event.target === detail || event.target.closest('.settings-group-close')) closeDetail();
    });
    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && detail.classList.contains('open')) closeDetail();
    });
    return detail;
  }

  function restoreMovedNodes() {
    if (!returnAnchor) return;
    movedNodes.forEach((node) => {
      node.classList.add('settings-group-hidden');
      returnAnchor.before(node);
    });
    returnAnchor.remove();
    returnAnchor = null;
    movedNodes = [];
  }

  function closeDetail() {
    const detail = document.getElementById(DETAIL_ID);
    if (detail) detail.classList.remove('open', 'preparing', 'ready');
    document.body.classList.remove('detail-open', 'settings-group-open', 'settings-group-opening');
    restoreMovedNodes();
    const content = document.getElementById('settingsGroupContent');
    if (content) content.textContent = '';
  }

  const GROUPS = {
    display: { title: '显示与语言', desc: '语言、字体、玻璃透明度、模糊强度和动效。' },
    account: { title: '账号与同步', desc: '登录、注册、本地模式和云同步。' },
    phone: { title: '手机偏好', desc: '家庭地址、默认地图等手机任务偏好。' },
    data: { title: '数据与预算', desc: '预算、聊天记录、导出账单和清空数据。' },
    appearance: { title: '背景外观', desc: '选择天气星空、翡翠海雾等内置背景。' },
  };

  function openGroup(id) {
    closeDetail();
    const group = GROUPS[id];
    const nodes = [...document.querySelectorAll(`#view-settings > section[data-settings-group-target="${id}"]`)];
    if (!group || !nodes.length) return;

    const detail = ensureDetail();
    const content = document.getElementById('settingsGroupContent');
    const title = document.getElementById('settingsGroupTitle');
    const desc = document.getElementById('settingsGroupDesc');
    if (!content || !title || !desc) return;

    document.body.classList.add('settings-group-opening');
    detail.classList.add('open', 'preparing');
    title.textContent = group.title;
    desc.textContent = group.desc;
    content.textContent = '';

    requestAnimationFrame(() => {
      returnAnchor = document.createElement('span');
      returnAnchor.hidden = true;
      returnAnchor.dataset.settingsReturnAnchor = id;
      nodes[0].before(returnAnchor);
      movedNodes = nodes;

      const fragment = document.createDocumentFragment();
      nodes.forEach((node) => {
        node.classList.remove('settings-group-hidden');
        fragment.appendChild(node);
      });
      content.appendChild(fragment);

      detail.classList.remove('preparing');
      detail.classList.add('ready');
      document.body.classList.remove('settings-group-opening');
      document.body.classList.add('detail-open', 'settings-group-open');
    });
  }

  function tagSection(section) {
    if (!section || section.dataset.settingsGroupTarget || section.classList.contains('settings-group-card')) return false;
    const title = section.querySelector('.section-head h2')?.textContent?.trim() || '';
    let target = '';
    if (section.id === 'appearancePlusPanel' || title.includes('显示') || title.includes('语言')) target = 'display';
    else if (title.includes('账号') || title.includes('同步')) target = 'account';
    else if (section.id === 'assistantPreferencePanel' || title.includes('手机偏好')) target = 'phone';
    else if (section.querySelector('#budgetInput') || title.includes('预算') || title.includes('数据工具')) target = 'data';
    else if (section.querySelector('#backgroundPicker') || title.includes('外观')) target = 'appearance';
    if (!target) return false;
    section.dataset.settingsGroupTarget = target;
    section.classList.add('settings-group-hidden');
    return true;
  }

  function tagOriginalSections(settingsView) {
    if (!settingsView) return;
    [...settingsView.querySelectorAll(':scope > section')].forEach(tagSection);
  }

  function installGroups() {
    const settingsView = document.getElementById('view-settings');
    if (!settingsView) return false;

    tagOriginalSections(settingsView);
    if (!document.getElementById('settingsGroupList')) {
      const header = settingsView.querySelector('.page-header');
      const list = document.createElement('div');
      list.id = 'settingsGroupList';
      list.className = 'settings-group-list';
      list.append(
        makeEntry({ id: 'account', icon: '☁', title: '账号与同步', desc: '登录、注册、本地模式和云同步。' }),
        makeEntry({ id: 'display', icon: 'Aa', title: '显示与语言', desc: '语言、字体、玻璃透明度、模糊强度和动效。' }),
        makeEntry({ id: 'phone', icon: '⌖', title: '手机偏好', desc: '家庭地址、默认地图等手机任务偏好。' }),
        makeEntry({ id: 'appearance', icon: '✦', title: '背景外观', desc: '切换内置背景风格。' }),
        makeEntry({ id: 'data', icon: '▤', title: '数据与预算', desc: '预算、导出、清空记录等数据工具。' })
      );
      header?.after(list);
      list.addEventListener('click', (event) => {
        const card = event.target.closest('[data-settings-group]');
        if (card) openGroup(card.dataset.settingsGroup);
      });
      list.addEventListener('keydown', (event) => {
        if (event.key !== 'Enter' && event.key !== ' ') return;
        const card = event.target.closest('[data-settings-group]');
        if (!card) return;
        event.preventDefault();
        openGroup(card.dataset.settingsGroup);
      });
    }

    if (!settingsObserver) {
      settingsObserver = new MutationObserver((mutations) => {
        let shouldRetag = false;
        mutations.forEach((mutation) => {
          mutation.addedNodes.forEach((node) => {
            if (node.nodeType !== Node.ELEMENT_NODE) return;
            if (node.matches?.('section') || node.querySelector?.('section')) shouldRetag = true;
          });
        });
        if (shouldRetag) requestAnimationFrame(() => tagOriginalSections(settingsView));
      });
      settingsObserver.observe(settingsView, { childList: true });
    }

    return true;
  }

  function boot() {
    if (document.documentElement.dataset.settingsGroupsReady === 'true') return;
    document.documentElement.dataset.settingsGroupsReady = 'true';
    installStyle();
    ensureDetail();
    if (!installGroups()) {
      const waitForSettings = new MutationObserver(() => {
        if (installGroups()) waitForSettings.disconnect();
      });
      waitForSettings.observe(document.body, { childList: true, subtree: true });
    }
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();
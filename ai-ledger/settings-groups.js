(() => {
  const STYLE_ID = 'settings-groups-style';
  const DETAIL_ID = 'settingsGroupDetail';

  function installStyle() {
    if (document.querySelector(`#${STYLE_ID}`)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      .settings-group-list{display:grid;gap:14px;margin-bottom:16px}
      .settings-group-entry{width:100%;display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:14px;padding:18px;border:0;text-align:left;color:inherit;cursor:pointer}
      .settings-group-icon{width:44px;height:44px;border-radius:16px;display:grid;place-items:center;background:rgba(255,255,255,var(--assistant-glass-control-alpha,.042));box-shadow:inset 0 .7px 0 rgba(255,255,255,.28);font-size:20px;font-weight:900;color:rgba(248,250,255,.95)}
      .settings-group-title{display:block;font-size:18px;line-height:1.2;font-weight:900;color:rgba(248,250,255,.98)}
      .settings-group-desc{display:block;margin-top:5px;font-size:13px;line-height:1.45;color:rgba(214,224,246,.68)}
      .settings-group-arrow{font-size:28px;line-height:1;color:rgba(248,250,255,.74);font-weight:500}
      .settings-group-hidden{display:none!important}
      .settings-group-detail{position:fixed;z-index:92;inset:0;display:none;padding:18px;background:rgba(3,7,18,.30)}
      .settings-group-detail.open{display:grid;place-items:end center}
      .settings-group-sheet{width:min(100%,520px);max-height:min(86vh,820px);overflow:auto;padding:18px;border-radius:30px;border:1px solid rgba(255,255,255,.18);background:linear-gradient(145deg,rgba(255,255,255,.058),rgba(255,255,255,.010) 46%,rgba(0,0,0,.018)),rgba(255,255,255,var(--assistant-glass-panel-alpha,.034));box-shadow:0 18px 42px rgba(0,0,0,.28);backdrop-filter:blur(var(--assistant-glass-panel-blur,12px)) saturate(1.14);-webkit-backdrop-filter:blur(var(--assistant-glass-panel-blur,12px)) saturate(1.14)}
      .settings-group-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:14px}
      .settings-group-head h2{margin:0;font-size:22px;font-weight:900;color:rgba(248,250,255,.98)}
      .settings-group-head p{margin:6px 0 0;font-size:13px;line-height:1.55;color:rgba(214,224,246,.68)}
      .settings-group-close{width:40px;height:40px;border-radius:15px;border:1px solid rgba(255,255,255,.16);background:rgba(255,255,255,var(--assistant-glass-control-alpha,.042));color:rgba(248,250,255,.92);font-size:24px}
      .settings-group-content>.glass-card{display:block!important;margin-bottom:14px}
      .settings-group-content>.glass-card:last-child{margin-bottom:0}
      @media(max-width:720px){.settings-group-detail{padding:10px}.settings-group-sheet{border-radius:28px 28px 0 0}}
    `;
    document.head.appendChild(style);
  }

  function makeEntry({ id, icon, title, desc }) {
    const section = document.createElement('section');
    section.className = 'glass-card settings-group-card';
    section.innerHTML = `
      <button class="settings-group-entry" type="button" data-settings-group="${id}">
        <span class="settings-group-icon">${icon}</span>
        <span><span class="settings-group-title">${title}</span><span class="settings-group-desc">${desc}</span></span>
        <span class="settings-group-arrow">›</span>
      </button>
    `;
    return section;
  }

  function ensureDetail() {
    let detail = document.querySelector(`#${DETAIL_ID}`);
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
      if (event.key === 'Escape') closeDetail();
    });
    return detail;
  }

  function closeDetail() {
    const detail = document.querySelector(`#${DETAIL_ID}`);
    if (!detail) return;
    detail.classList.remove('open');
    document.body.classList.remove('detail-open');
    const content = document.querySelector('#settingsGroupContent');
    const placeholder = content?.querySelector('[data-return-before]');
    if (content && placeholder) {
      [...content.children].forEach((node) => {
        if (node !== placeholder && node.nodeType === 1) placeholder.before(node);
      });
      placeholder.remove();
    }
  }

  const GROUPS = {
    display: { title: '显示与语言', desc: '语言、字体、玻璃透明度、模糊强度和动效。' },
    account: { title: '账号与同步', desc: '登录、注册、本地模式和云同步。' },
    phone: { title: '手机偏好', desc: '家庭地址、默认地图等手机任务偏好。' },
    data: { title: '数据与预算', desc: '预算、聊天记录、导出账单和清空数据。' },
    appearance: { title: '背景外观', desc: '选择天气星空、翡翠海雾等内置背景。' },
  };

  function openGroup(id) {
    const group = GROUPS[id];
    const nodes = [...document.querySelectorAll(`[data-settings-group-target="${id}"]`)];
    if (!group || !nodes.length) return;
    const detail = ensureDetail();
    document.querySelector('#settingsGroupTitle').textContent = group.title;
    document.querySelector('#settingsGroupDesc').textContent = group.desc;
    const content = document.querySelector('#settingsGroupContent');
    content.innerHTML = '';
    const placeholder = document.createElement('span');
    placeholder.hidden = true;
    placeholder.dataset.returnBefore = '1';
    nodes[0].before(placeholder);
    content.appendChild(placeholder);
    nodes.forEach((node) => {
      node.classList.remove('settings-group-hidden');
      content.appendChild(node);
    });
    detail.classList.add('open');
    document.body.classList.add('detail-open');
  }

  function tagOriginalSections(settingsView) {
    const sections = [...settingsView.querySelectorAll(':scope > section')].filter((s) => !s.classList.contains('settings-group-card'));
    sections.forEach((section) => {
      const title = section.querySelector('.section-head h2')?.textContent?.trim() || '';
      if (section.id === 'appearancePlusPanel' || title.includes('显示') || title.includes('语言')) section.dataset.settingsGroupTarget = 'display';
      else if (title.includes('账号') || title.includes('同步')) section.dataset.settingsGroupTarget = 'account';
      else if (section.id === 'assistantPreferencePanel' || title.includes('手机偏好')) section.dataset.settingsGroupTarget = 'phone';
      else if (section.querySelector('#budgetInput') || title.includes('预算') || title.includes('数据工具')) section.dataset.settingsGroupTarget = 'data';
      else if (section.querySelector('#backgroundPicker') || title.includes('外观')) section.dataset.settingsGroupTarget = 'appearance';
      else return;
      section.classList.add('settings-group-hidden');
    });
  }

  function installGroups() {
    const settingsView = document.querySelector('#view-settings');
    if (!settingsView || document.querySelector('#settingsGroupList')) return;
    tagOriginalSections(settingsView);
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
      const btn = event.target.closest('[data-settings-group]');
      if (btn) openGroup(btn.dataset.settingsGroup);
    });
  }

  function boot() {
    installStyle();
    ensureDetail();
    setTimeout(installGroups, 80);
    setTimeout(() => { tagOriginalSections(document.querySelector('#view-settings')); }, 500);
    setTimeout(() => { tagOriginalSections(document.querySelector('#view-settings')); }, 1200);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();

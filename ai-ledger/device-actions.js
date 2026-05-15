(() => {
  const ACTION_KEY = 'ai-ledger-device-actions-v1';
  const ANDROID_BRIDGE_NAMES = ['AndroidBridge', 'AiLedgerAndroid', 'DeviceBridge'];
  const MAX_BROWSER_TIMER_MS = 24 * 60 * 60 * 1000;

  const APP_MAP = {
    '微信': { packageName: 'com.tencent.mm', scheme: 'weixin://', aliases: ['微信', 'wechat'] },
    '支付宝': { packageName: 'com.eg.android.AlipayGphone', scheme: 'alipays://platformapi/startapp', aliases: ['支付宝', 'alipay'] },
    '淘宝': { packageName: 'com.taobao.taobao', scheme: 'taobao://', aliases: ['淘宝'] },
    '京东': { packageName: 'com.jingdong.app.mall', scheme: 'openapp.jdmobile://', aliases: ['京东'] },
    'QQ': { packageName: 'com.tencent.mobileqq', scheme: 'mqq://', aliases: ['QQ', 'qq'] },
    '抖音': { packageName: 'com.ss.android.ugc.aweme', scheme: 'snssdk1128://', aliases: ['抖音'] },
    '设置': { packageName: 'android.settings.SETTINGS', scheme: 'intent:#Intent;action=android.settings.SETTINGS;end', aliases: ['设置', '系统设置'] },
    '相机': { packageName: 'android.media.action.IMAGE_CAPTURE', scheme: 'intent:#Intent;action=android.media.action.IMAGE_CAPTURE;end', aliases: ['相机', '拍照'] },
    '电话': { packageName: 'android.intent.action.DIAL', scheme: 'tel:', aliases: ['电话', '拨号'] }
  };

  function $(selector) {
    return document.querySelector(selector);
  }

  function nowId(prefix = 'act') {
    return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function loadActions() {
    try {
      const parsed = JSON.parse(localStorage.getItem(ACTION_KEY) || '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  function saveActions(actions) {
    localStorage.setItem(ACTION_KEY, JSON.stringify(actions.slice(-80)));
  }

  function upsertAction(action) {
    const actions = loadActions();
    const index = actions.findIndex((item) => item.id === action.id);
    if (index >= 0) actions[index] = action;
    else actions.push(action);
    saveActions(actions);
    return action;
  }

  function getBridge() {
    for (const name of ANDROID_BRIDGE_NAMES) {
      if (window[name]) return window[name];
    }
    return null;
  }

  function callBridge(methodNames, ...args) {
    const bridge = getBridge();
    if (!bridge) return false;
    for (const method of methodNames) {
      if (typeof bridge[method] === 'function') {
        bridge[method](...args);
        return true;
      }
    }
    return false;
  }

  function showToast(message) {
    const toast = $('#toast');
    if (!toast) return;
    toast.textContent = message;
    toast.classList.add('show');
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => toast.classList.remove('show'), 2600);
  }

  function formatLocalTime(date) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    const hh = String(date.getHours()).padStart(2, '0');
    const mm = String(date.getMinutes()).padStart(2, '0');
    return `${y}-${m}-${d} ${hh}:${mm}`;
  }

  function parseHourMinute(text) {
    const colon = text.match(/(\d{1,2})\s*[:：]\s*(\d{1,2})/);
    if (colon) return { hour: Number(colon[1]), minute: Number(colon[2]) };

    const cn = text.match(/(\d{1,2})\s*点\s*(半|\d{1,2}\s*分?)?/);
    if (!cn) return null;
    const hour = Number(cn[1]);
    let minute = 0;
    if (cn[2]) {
      minute = cn[2].includes('半') ? 30 : Number(cn[2].replace(/\D/g, '') || 0);
    }
    return { hour, minute };
  }

  function parseReminderTime(text) {
    const now = new Date();
    const minuteLater = text.match(/(\d{1,3})\s*分钟后/);
    if (minuteLater) {
      const target = new Date(now.getTime() + Number(minuteLater[1]) * 60 * 1000);
      return target;
    }

    const hourLater = text.match(/(\d{1,2})\s*小时后/);
    if (hourLater) {
      const target = new Date(now.getTime() + Number(hourLater[1]) * 60 * 60 * 1000);
      return target;
    }

    const hm = parseHourMinute(text);
    if (!hm) return null;

    let dayOffset = 0;
    if (/后天/.test(text)) dayOffset = 2;
    else if (/明天|明早|明晚|明日/.test(text)) dayOffset = 1;

    let hour = hm.hour;
    const isPm = /下午|晚上|今晚|明晚|夜里/.test(text);
    const isNoon = /中午/.test(text);
    if ((isPm || isNoon) && hour < 12) hour += 12;
    if (/凌晨/.test(text) && hour === 12) hour = 0;

    const target = new Date(now);
    target.setDate(now.getDate() + dayOffset);
    target.setHours(hour, hm.minute, 0, 0);

    if (dayOffset === 0 && target.getTime() <= now.getTime()) {
      target.setDate(target.getDate() + 1);
    }
    return target;
  }

  function cleanReminderTitle(text) {
    return text
      .replace(/(帮我|给我|请|麻烦你|你)/g, '')
      .replace(/(设置|定|创建)?(一个)?(提醒|闹钟)/g, '')
      .replace(/(提醒我|叫我|叫醒我|通知我)/g, '')
      .replace(/(今天|明天|后天|今晚|明早|明晚|上午|下午|晚上|凌晨|中午|早上|早晨)/g, '')
      .replace(/\d{1,2}\s*[:：]\s*\d{1,2}/g, '')
      .replace(/\d{1,2}\s*点\s*(半|\d{1,2}\s*分?)?/g, '')
      .replace(/\d+\s*(分钟后|小时后)/g, '')
      .replace(/[，,。；;、]/g, '')
      .trim() || '提醒事项';
  }

  function parseOpenApp(text) {
    const openMatch = text.match(/(?:打开|开启|启动|进入)\s*([\u4e00-\u9fa5A-Za-z]{1,12})/);
    if (!openMatch) return null;
    const target = openMatch[1].trim();
    const app = Object.entries(APP_MAP).find(([name, config]) => {
      return name === target || config.aliases.some((alias) => alias.toLowerCase() === target.toLowerCase());
    });
    if (!app) return null;
    const [name, config] = app;
    return {
      id: nowId('open'),
      type: 'open_app',
      title: `打开${name}`,
      appName: name,
      packageName: config.packageName,
      scheme: config.scheme,
      createdAt: new Date().toISOString(),
      status: 'pending'
    };
  }

  function parseReminder(text) {
    if (!/(提醒|闹钟|叫我|叫醒|通知|起床|复习|开会|上课)/.test(text)) return null;
    const targetTime = parseReminderTime(text);
    if (!targetTime) return null;
    const title = cleanReminderTitle(text);
    return {
      id: nowId('reminder'),
      type: 'reminder',
      title,
      targetTime: targetTime.toISOString(),
      createdAt: new Date().toISOString(),
      status: 'scheduled'
    };
  }

  function parseDeviceCommand(text) {
    return parseOpenApp(text) || parseReminder(text);
  }

  function scheduleBrowserNotification(action) {
    const target = new Date(action.targetTime);
    const delay = target.getTime() - Date.now();
    if (delay < 0 || delay > MAX_BROWSER_TIMER_MS) return false;

    const fire = () => {
      const title = 'AI助手提醒';
      const body = action.title;
      if ('Notification' in window && Notification.permission === 'granted') {
        new Notification(title, { body });
      } else {
        alert(`${title}：${body}`);
      }
      action.status = 'done';
      upsertAction(action);
      renderActionStack();
    };

    window.setTimeout(fire, delay);
    return true;
  }

  async function executeReminder(action) {
    const iso = action.targetTime;
    const title = action.title;
    const bridgeOK = callBridge(['setAlarm', 'createAlarm', 'createReminder'], iso, title, action.id);
    if (bridgeOK) {
      action.status = 'sent_to_android';
      upsertAction(action);
      showToast('已交给安卓系统设置提醒');
      renderActionStack();
      return { ok: true, mode: 'android' };
    }

    if ('Notification' in window && Notification.permission === 'default') {
      try { await Notification.requestPermission(); } catch {}
    }
    const browserOK = scheduleBrowserNotification(action);
    action.status = browserOK ? 'scheduled_in_browser' : 'saved_only';
    upsertAction(action);
    showToast(browserOK ? '已在网页内创建提醒' : '已保存提醒；关闭网页后需要安卓端接管');
    renderActionStack();
    return { ok: browserOK, mode: browserOK ? 'browser' : 'saved_only' };
  }

  function executeOpenApp(action) {
    const bridgeOK = callBridge(['openApp', 'launchApp'], action.packageName, action.appName, action.id);
    if (bridgeOK) {
      action.status = 'sent_to_android';
      upsertAction(action);
      showToast(`正在通过安卓端打开${action.appName}`);
      renderActionStack();
      return true;
    }

    action.status = 'scheme_attempted';
    upsertAction(action);
    renderActionStack();
    showToast(`正在尝试打开${action.appName}`);
    if (action.scheme) window.location.href = action.scheme;
    return false;
  }

  async function executeAction(actionOrId) {
    const actions = loadActions();
    const action = typeof actionOrId === 'string'
      ? actions.find((item) => item.id === actionOrId)
      : actionOrId;
    if (!action) return false;
    if (action.type === 'reminder') return executeReminder(action);
    if (action.type === 'open_app') return executeOpenApp(action);
    return false;
  }

  function actionDescription(action) {
    if (action.type === 'reminder') {
      return `时间：${formatLocalTime(new Date(action.targetTime))}`;
    }
    if (action.type === 'open_app') {
      return `目标：${action.appName} · ${action.packageName}`;
    }
    return '待执行动作';
  }

  function actionPrimaryText(action) {
    if (action.type === 'reminder') return '设置提醒';
    if (action.type === 'open_app') return '立即打开';
    return '执行';
  }

  function actionCard(action, compact = false) {
    const icon = action.type === 'reminder' ? '⏰' : '◎';
    const state = action.status || 'pending';
    return `
      <article class="device-action-card ${compact ? 'compact' : ''}" data-device-action-id="${escapeHtml(action.id)}">
        <div class="device-action-icon">${icon}</div>
        <div class="device-action-main">
          <div class="device-action-title">${escapeHtml(action.title)}</div>
          <div class="device-action-desc">${escapeHtml(actionDescription(action))}</div>
          <div class="device-action-status">状态：${escapeHtml(state)}</div>
        </div>
        <button class="device-action-btn" type="button" data-execute-device-action="${escapeHtml(action.id)}">${actionPrimaryText(action)}</button>
      </article>
    `;
  }

  function appendChatBubble(role, content, action) {
    const box = $('#chatMessages');
    if (!box) return;
    const html = role === 'user'
      ? `<div class="chat-row user device-owned"><div class="chat-bubble">${escapeHtml(content)}</div></div>`
      : `<div class="chat-row assistant device-owned"><div class="chat-response"><div class="chat-bubble">${escapeHtml(content)}</div>${action ? actionCard(action) : ''}</div></div>`;
    box.insertAdjacentHTML('beforeend', html);
    box.scrollTop = box.scrollHeight;
  }

  function replyForAction(action) {
    if (action.type === 'reminder') {
      return `我已生成提醒动作：${action.title}，时间是 ${formatLocalTime(new Date(action.targetTime))}。`;
    }
    if (action.type === 'open_app') {
      return `我已生成打开应用动作：${action.appName}。`;
    }
    return '我已生成一个可执行动作。';
  }

  function handleSubmit(event) {
    const input = $('#aiInput');
    if (!input) return;
    const text = input.value.trim();
    if (!text) return;
    const action = parseDeviceCommand(text);
    if (!action) return;

    event.preventDefault();
    event.stopImmediatePropagation();

    input.value = '';
    input.style.height = 'auto';
    upsertAction(action);
    appendChatBubble('user', text);
    appendChatBubble('assistant', replyForAction(action), action);
    renderActionStack();

    if (action.type === 'open_app') {
      executeOpenApp(action);
    }
  }

  function installStyle() {
    if ($('#device-actions-style')) return;
    const style = document.createElement('style');
    style.id = 'device-actions-style';
    style.textContent = `
      .device-action-card{display:grid;grid-template-columns:auto 1fr auto;gap:12px;align-items:center;margin-top:10px;padding:13px;border-radius:22px;border:1px solid rgba(255,255,255,.34);background:rgba(255,255,255,.15);box-shadow:0 14px 34px rgba(0,30,45,.14);backdrop-filter:blur(16px);color:#f4ffff}
      .device-action-card.compact{grid-template-columns:auto 1fr;position:relative;padding-right:96px;margin:10px 0}
      .device-action-card.compact .device-action-btn{position:absolute;right:12px;top:50%;transform:translateY(-50%)}
      .device-action-icon{width:38px;height:38px;border-radius:16px;display:grid;place-items:center;background:rgba(255,255,255,.18);font-size:20px}
      .device-action-title{font-weight:900;color:#f5ffff;font-size:15px;line-height:1.35}
      .device-action-desc,.device-action-status{font-size:12px;color:rgba(235,252,255,.70);line-height:1.45;margin-top:3px}
      .device-action-btn{border:0;border-radius:999px;padding:9px 12px;background:#86ece2;color:#062f35;font-weight:900;white-space:nowrap;box-shadow:0 8px 18px rgba(2,220,200,.20)}
      .device-action-stack{margin-top:14px;padding:12px;border-radius:24px;background:rgba(255,255,255,.10);border:1px solid rgba(255,255,255,.24)}
      .device-action-stack-title{display:flex;align-items:center;justify-content:space-between;color:#f5ffff;font-weight:900;font-size:14px;margin-bottom:8px}
      .device-empty{color:rgba(235,252,255,.68);font-size:13px;line-height:1.7;margin:0}
      .tools-panel .device-action-card{background:rgba(255,255,255,.14)}
      @media(max-width:720px){.device-action-card{grid-template-columns:auto 1fr}.device-action-btn{grid-column:1/-1;width:100%}.device-action-card.compact{padding-right:13px}.device-action-card.compact .device-action-btn{position:static;transform:none}}
    `;
    document.head.appendChild(style);
  }

  function renderActionStack() {
    const box = $('#chatMessages');
    if (!box) return;
    let stack = $('#deviceActionStack');
    const actions = loadActions().slice(-3).reverse();
    const body = actions.length
      ? actions.map((action) => actionCard(action, true)).join('')
      : '<p class="device-empty">还没有手机动作。你可以说“明早8点叫我起床”或“打开微信”。</p>';

    if (!stack) {
      stack = document.createElement('div');
      stack.id = 'deviceActionStack';
      stack.className = 'device-action-stack';
      box.appendChild(stack);
    }
    stack.innerHTML = `<div class="device-action-stack-title"><span>最近手机动作</span><span>${actions.length} 条</span></div>${body}`;
  }

  function renderTasksPanel() {
    const panel = $('#toolsPanel');
    if (!panel) return;
    const actions = loadActions().slice().reverse();
    const body = actions.length
      ? actions.map((action) => actionCard(action)).join('')
      : '<p class="device-empty">暂无任务记录。回到 AI 助手，说“明早8点提醒我复习”试一下。</p>';
    panel.innerHTML = '<button class="tools-back" type="button" data-back-tools>← 功能中心</button>' +
      '<article class="tools-panel-card"><h2>任务记录</h2><p>这里会保存提醒、闹钟和打开应用等执行记录。接入安卓 WebView 后，同一套动作会交给系统执行。</p>' + body + '</article>';
    panel.classList.add('open');
    const home = $('#toolsHome');
    if (home) home.style.display = 'none';
  }

  function replayShortTimers() {
    loadActions().forEach((action) => {
      if (action.type === 'reminder' && ['scheduled', 'scheduled_in_browser', 'saved_only'].includes(action.status)) {
        scheduleBrowserNotification(action);
      }
    });
  }

  function bind() {
    installStyle();
    $('#chatForm')?.addEventListener('submit', handleSubmit, true);

    document.addEventListener('click', (event) => {
      const button = event.target.closest('[data-execute-device-action]');
      if (button) {
        executeAction(button.dataset.executeDeviceAction);
        return;
      }
      if (event.target.closest('[data-tool="tasks"]')) {
        window.setTimeout(renderTasksPanel, 0);
      }
    }, true);

    window.setTimeout(renderActionStack, 500);
    replayShortTimers();
  }

  window.AiLedgerDeviceActions = {
    parseDeviceCommand,
    executeAction,
    executeReminder,
    executeOpenApp,
    loadActions,
    saveActions,
    renderActionStack,
    renderTasksPanel,
    bridgeNames: ANDROID_BRIDGE_NAMES
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bind);
  } else {
    bind();
  }
})();

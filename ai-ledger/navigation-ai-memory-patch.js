(() => {
  const STYLE_ID = 'navigation-ai-memory-patch-style';
  const PATCH_FLAG = '__navigationMemoryPatched';

  const MODE_LABELS = {
    driving: '驾车',
    walking: '步行',
    riding: '骑行',
    transit: '公交/地铁',
  };

  const ROUTE_OPTION_LABELS = {
    avoidHighway: '避开高速',
    avoidTolls: '少收费',
    preferSubway: '地铁优先',
    preferLessWalk: '少步行',
    useRealtimeTraffic: '参考实时路况',
  };

  const PLACE_KEY_LABELS = {
    home: '家',
    school: '学校',
    work: '公司',
    dorm: '宿舍',
  };

  function installStyle() {
    if (document.querySelector(`#${STYLE_ID}`)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      .mobile-command-card[data-nav-memory-card="true"]{
        background:linear-gradient(145deg,rgba(255,255,255,.68),rgba(255,255,255,.46));
      }
      .mobile-command-card[data-nav-memory-card="true"] .mobile-command-title::before{
        content:"◇ ";
        color:#0b8f8b;
      }
    `;
    document.head.appendChild(style);
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function cleanText(value, max = 120) {
    return String(value || '')
      .trim()
      .replace(/^[，。；;：:\s]+|[，。；;：:\s]+$/g, '')
      .replace(/\s+/g, ' ')
      .slice(0, max);
  }

  function createId(prefix = 'nav-memory') {
    if (crypto.randomUUID) return `${prefix}-${crypto.randomUUID()}`;
    return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  function isMemoryCommand(command) {
    return command?.commandKind === 'navigation_preference'
      || command?.params?.intent === 'navigation_preference'
      || Boolean(command?.params?.updates);
  }

  function normalizePlaceKey(alias) {
    const text = cleanText(alias, 20);
    if (/^(家|我家|家里|家庭|住处|住的地方|回家|到家)$/u.test(text)) return 'home';
    if (/^(学校|校区|大学|学院|上课地方)$/u.test(text)) return 'school';
    if (/^(公司|单位|办公室|上班地方|实习单位)$/u.test(text)) return 'work';
    if (/^(宿舍|寝室|公寓)$/u.test(text)) return 'dorm';
    return '';
  }

  function inferProvider(text) {
    if (/高德|amap/i.test(text)) return 'amap';
    if (/百度|baidu/i.test(text)) return 'baidu';
    return '';
  }

  function inferMode(text) {
    if (/公交|地铁|轻轨|轨道|公共交通|换乘/u.test(text)) return 'transit';
    if (/步行|走路/u.test(text)) return 'walking';
    if (/骑行|骑车|自行车|电动车|单车/u.test(text)) return 'riding';
    if (/驾车|开车|自驾|打车|出租车|网约车/u.test(text)) return 'driving';
    return '';
  }

  function inferRouteOptions(text) {
    const options = {};
    if (/避开高速|不走高速|不要高速|少走高速/u.test(text)) options.avoidHighway = true;
    if (/少收费|少花钱|避免收费|避开收费|不走收费/u.test(text)) options.avoidTolls = true;
    if (/地铁优先|优先地铁|多坐地铁/u.test(text)) options.preferSubway = true;
    if (/少步行|少走路|不要走太多|步行少一点/u.test(text)) options.preferLessWalk = true;
    if (/实时路况|躲拥堵|避开拥堵|避堵/u.test(text)) options.useRealtimeTraffic = true;
    return options;
  }

  function routeOptionText(options = {}) {
    return Object.entries(options)
      .filter(([, value]) => Boolean(value))
      .map(([key]) => ROUTE_OPTION_LABELS[key] || key)
      .join('、');
  }

  function normalizeAddress(value) {
    return cleanText(value, 120)
      .replace(/^(就是|是|在|为|到|去|设为|设置为|改成|定为|保存为|记为)/u, '')
      .replace(/(然后|之后|以后|现在|马上|立刻|导航|地图|默认|吧|呀|哦)$/u, '')
      .trim();
  }

  function addPlaceUpdate(updates, rows, alias, address) {
    const cleanAlias = cleanText(alias, 16).replace(/^(我的|我)/u, '');
    const cleanAddress = normalizeAddress(address);
    if (!cleanAlias || !cleanAddress) return;
    const key = normalizePlaceKey(cleanAlias);
    if (key) {
      updates.places ||= {};
      updates.places[key] = cleanAddress;
      rows.push([`常用地址 · ${PLACE_KEY_LABELS[key] || cleanAlias}`, cleanAddress]);
      return;
    }
    updates.customPlaces ||= [];
    updates.customPlaces.push({ name: cleanAlias, address: cleanAddress });
    rows.push([`自定义地点 · ${cleanAlias}`, cleanAddress]);
  }

  function parseAddressUpdates(text, updates, rows) {
    const patterns = [
      /(?:把|将)?(?:我的|我)?(家|我家|家里|学校|校区|公司|单位|办公室|宿舍|寝室|公寓)(?:的)?(?:地址|位置)?\s*(?:设为|设置为|改成|定为|保存为|记为|就是|是|在|=)\s*([^，。；;\n]+)/gu,
      /(?:以后|以后再|之后)(?:回|去|到)(家|学校|公司|单位|宿舍|寝室|公寓)\s*(?:就是|是|去|到)?\s*([^，。；;\n]+)/gu,
      /([^，。；;\n]{1,12})(?:地址|位置)\s*(?:设为|设置为|改成|定为|保存为|记为|就是|是|在|=)\s*([^，。；;\n]+)/gu,
    ];

    patterns.forEach((pattern) => {
      for (const match of text.matchAll(pattern)) {
        addPlaceUpdate(updates, rows, match[1], match[2]);
      }
    });
  }

  function parseMemoryCommand(text) {
    const raw = String(text || '').trim();
    if (!raw) return null;

    const hasMemoryIntent = /(默认|以后|偏好|习惯|地址|位置|设为|设置为|保存为|改成|定为|记住|就是|我家|家里|少步行|避开高速|少收费|地铁优先)/u.test(raw);
    if (!hasMemoryIntent) return null;

    const updates = {};
    const rows = [];

    parseAddressUpdates(raw, updates, rows);

    const provider = inferProvider(raw);
    if (provider && /(默认|以后|偏好|习惯|地图|导航)/u.test(raw)) {
      updates.mapProvider = provider;
      rows.push(['默认地图', provider === 'amap' ? '高德地图' : '百度地图']);
    }

    const mode = inferMode(raw);
    if (mode && /(默认|以后|偏好|习惯|导航|出行方式|路线|通勤)/u.test(raw)) {
      updates.defaultMode = mode;
      rows.push(['默认方式', MODE_LABELS[mode] || mode]);
    }

    const routeOptions = inferRouteOptions(raw);
    if (Object.keys(routeOptions).length) {
      updates.routeOptions = routeOptions;
      rows.push(['路线习惯', routeOptionText(routeOptions)]);
    }

    if (!Object.keys(updates).length) return null;

    const summary = rows.map(([key, value]) => `${key}：${value}`).join('；') || '更新导航偏好';
    return {
      id: createId(),
      type: 'navigate',
      commandKind: 'navigation_preference',
      title: '保存导航偏好',
      summary,
      params: {
        intent: 'navigation_preference',
        updates,
        rows,
      },
    };
  }

  function renderMemoryCard(command, state = 'pending', message = '') {
    const rows = (command.params?.rows?.length ? command.params.rows : [['偏好', command.summary || '更新导航偏好']])
      .map(([key, value]) => `<div class="mobile-command-row"><span>${escapeHtml(key)}</span><strong>${escapeHtml(value)}</strong></div>`)
      .join('');
    const buttons = state === 'pending'
      ? `<div class="mobile-command-actions">
          <button class="mobile-command-confirm" type="button" data-mobile-run="${escapeHtml(command.id)}">确认保存</button>
          <button class="mobile-command-cancel" type="button" data-mobile-cancel="${escapeHtml(command.id)}">取消</button>
        </div>`
      : '';
    const note = message ? `<div class="mobile-command-message">${escapeHtml(message)}</div>` : '';
    return `<div class="mobile-command-card" data-nav-memory-card="true" data-mobile-card="${escapeHtml(command.id)}">
      <div class="mobile-command-head">
        <span class="mobile-command-title">${escapeHtml(command.title || '保存导航偏好')}</span>
        <span class="mobile-command-status ${escapeHtml(state)}">${state === 'done' ? '已保存' : state === 'cancelled' ? '已取消' : state === 'failed' ? '保存失败' : '待确认'}</span>
      </div>
      <div class="mobile-command-detail">${rows}</div>
      ${buttons}
      ${note}
    </div>`;
  }

  function updateCard(commandId, state, message) {
    const card = document.querySelector(`[data-mobile-card="${CSS.escape(commandId)}"]`);
    if (!card) return;
    const status = card.querySelector('.mobile-command-status');
    if (status) {
      status.className = `mobile-command-status ${state}`;
      status.textContent = state === 'done' ? '已保存' : state === 'cancelled' ? '已取消' : state === 'failed' ? '保存失败' : '待确认';
    }
    card.querySelector('.mobile-command-actions')?.remove();
    card.querySelector('.mobile-command-message')?.remove();
    if (message) card.insertAdjacentHTML('beforeend', `<div class="mobile-command-message">${escapeHtml(message)}</div>`);
  }

  function readCommandFromChat(commandId) {
    try {
      const messages = JSON.parse(localStorage.getItem('ai-ledger-chat-v2') || '[]');
      return [...messages].reverse().find((item) => item.mobileCommand?.id === commandId)?.mobileCommand || null;
    } catch {
      return null;
    }
  }

  function toast(message) {
    const el = document.querySelector('#toast');
    if (!el) return;
    el.textContent = message;
    el.classList.add('show');
    window.clearTimeout(toast.timer);
    toast.timer = window.setTimeout(() => el.classList.remove('show'), 2200);
  }

  async function saveMemoryCommand(command) {
    if (!window.AssistantPreferences?.applyPreferenceUpdate) {
      return { ok: false, message: '导航偏好模块还没有加载完成，请刷新后再试。' };
    }
    return window.AssistantPreferences.applyPreferenceUpdate(command.params?.updates || {});
  }

  function installClickCapture() {
    if (document.body.dataset.navMemoryClickCapture === 'true') return;
    document.body.dataset.navMemoryClickCapture = 'true';
    document.addEventListener('click', async (event) => {
      const runBtn = event.target.closest?.('[data-mobile-run]');
      const cancelBtn = event.target.closest?.('[data-mobile-cancel]');
      if (!runBtn && !cancelBtn) return;

      const commandId = runBtn?.dataset.mobileRun || cancelBtn?.dataset.mobileCancel;
      const command = readCommandFromChat(commandId);
      if (!isMemoryCommand(command)) return;

      event.preventDefault();
      event.stopImmediatePropagation();

      if (cancelBtn) {
        updateCard(commandId, 'cancelled', '已取消保存导航偏好。');
        return;
      }

      updateCard(commandId, 'pending', '正在保存导航偏好……');
      try {
        const result = await saveMemoryCommand(command);
        if (result?.ok) {
          updateCard(commandId, 'done', result.message || '已保存导航偏好。');
          toast('已保存导航偏好');
        } else {
          updateCard(commandId, 'failed', result?.message || '保存失败。');
        }
      } catch (error) {
        updateCard(commandId, 'failed', String(error?.message || error || '保存失败'));
      }
    }, true);
  }

  function patchMobileActions() {
    const actions = window.MobileCommandActions;
    if (!actions || actions[PATCH_FLAG]) return;
    actions[PATCH_FLAG] = true;

    const baseParse = actions.parse;
    actions.parse = (text) => parseMemoryCommand(text) || baseParse?.(text) || null;

    const baseRenderCard = actions.renderCard;
    actions.renderCard = (command, state, message) => {
      if (isMemoryCommand(command)) return renderMemoryCard(command, state, message);
      return baseRenderCard?.(command, state, message) || '';
    };

    const baseCreateReply = actions.createReply;
    actions.createReply = (command) => {
      if (isMemoryCommand(command)) {
        return `我整理好了导航偏好：${command.summary || '更新导航习惯'}。确认后我会保存到手机偏好里。`;
      }
      return baseCreateReply?.(command) || '我整理好了这个手机动作，确认后我再执行。';
    };
  }

  function boot() {
    installStyle();
    installClickCapture();
    patchMobileActions();
    window.setTimeout(patchMobileActions, 300);
    window.setTimeout(patchMobileActions, 1200);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();

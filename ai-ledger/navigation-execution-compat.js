(() => {
  'use strict';

  const PATCH_FLAG = '__navigationExecutionCompatPatched';
  const CHAT_KEY = 'ai-ledger-chat-v2';
  const MODEL_STYLE_ID = 'model-picker-hero-polish-style';
  const MODEL_PREF_KEY = 'ai-ledger-model-preference-v1';

  const FALLBACK_MODELS = [
    { id: 'auto', label: '自动', short: '自动', hint: '按可用性自动切换' },
    { id: 'kimi', label: 'Kimi K2.6', short: 'Kimi', hint: '严格使用 Kimi' },
    { id: 'mistral', label: 'Mistral Medium 3.5', short: 'Mistral', hint: '严格使用 Mistral' },
    { id: 'gemini', label: 'Gemini 2.5 Flash', short: 'Gemini', hint: '严格使用 Gemini' },
    { id: 'workers', label: 'Workers AI', short: 'Workers', hint: '使用 Workers AI 兜底' },
  ];

  function normalizeMode(value) {
    const raw = String(value || '').trim();
    const text = raw.toLowerCase();
    if (['transit', 'bus', 'subway', 'metro', 'public_transport', 'public-transport'].includes(text) || /公交|地铁|公共交通|轻轨|轨道|巴士|乘车|坐车/.test(raw)) return 'transit';
    if (['walking', 'walk', 'foot'].includes(text) || /步行|走路|步走/.test(raw)) return 'walking';
    if (['riding', 'bike', 'bicycle', 'cycling', 'ride'].includes(text) || /骑行|骑车|自行车|单车|电动车/.test(raw)) return 'riding';
    if (['driving', 'drive', 'car', 'taxi'].includes(text) || /驾车|开车|自驾|打车|出租车|网约车/.test(raw)) return 'driving';
    return 'driving';
  }

  function modeAliases(mode) {
    const normalized = normalizeMode(mode);
    const amapRouteType = { driving: '0', transit: '1', walking: '2', riding: '3' }[normalized] || '0';
    const baiduMode = { driving: 'driving', transit: 'transit', walking: 'walking', riding: 'riding' }[normalized] || 'driving';
    return { mode: normalized, travelMode: normalized, navigationMode: normalized, transportMode: normalized, routeMode: normalized, baiduMode, amapMode: normalized, amapRouteType, modeCode: amapRouteType };
  }

  function normalizeNavigateParams(params = {}) {
    const normalizedMode = normalizeMode(params.mode || params.travelMode || params.navigationMode || params.transportMode || params.routeMode);
    return { ...params, ...modeAliases(normalizedMode), routeOptions: { useRealtimeTraffic: true, ...(params.routeOptions || {}) } };
  }

  function patchPlugin(plugin, name) {
    if (!plugin || typeof plugin.navigate !== 'function' || plugin[PATCH_FLAG]) return false;
    const originalNavigate = plugin.navigate.bind(plugin);
    plugin.navigate = async (params = {}) => originalNavigate(normalizeNavigateParams(params));
    plugin[PATCH_FLAG] = true;
    plugin.__normalizeNavigateParams = normalizeNavigateParams;
    console.info(`[NavigationCompat] patched ${name}.navigate`);
    return true;
  }

  function patchAll() {
    const plugins = window.Capacitor?.Plugins;
    if (!plugins) return;
    patchPlugin(plugins.MobileAssistant, 'MobileAssistant');
    patchPlugin(plugins.MobileTools, 'MobileTools');
  }

  function removeUnstableVisualLayer() {
    document.getElementById('visual-design-director-style')?.remove();
    document.body?.classList.remove('visual-design-v1');
  }

  function escapeHtml(value) {
    return String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#039;');
  }

  function readMessages() {
    try {
      const parsed = JSON.parse(localStorage.getItem(CHAT_KEY) || '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch { return []; }
  }

  function models() {
    return window.AiLedgerModelPicker?.models || FALLBACK_MODELS;
  }

  function currentModelId() {
    if (window.AiLedgerModelPicker?.current) return window.AiLedgerModelPicker.current();
    try {
      const value = JSON.parse(localStorage.getItem(MODEL_PREF_KEY) || '{}').model || 'auto';
      return models().some((item) => item.id === value) ? value : 'auto';
    } catch { return 'auto'; }
  }

  function setModel(id) {
    if (window.AiLedgerModelPicker?.set) window.AiLedgerModelPicker.set(id);
    else localStorage.setItem(MODEL_PREF_KEY, JSON.stringify({ model: id, updatedAt: Date.now() }));
    syncModelLabel();
  }

  function currentModel() {
    const id = currentModelId();
    return models().find((item) => item.id === id) || models()[0] || FALLBACK_MODELS[0];
  }

  function showToast(text) {
    const toast = document.querySelector('#toast');
    if (!toast) return;
    toast.textContent = text;
    toast.classList.add('show');
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => toast.classList.remove('show'), 1800);
  }

  function clearConversation() {
    const initial = [{ id: 'welcome', role: 'assistant', content: '你好，我是你的 AI 助手。你可以让我记账、查账单、查天气、读网页、设置提醒、打开应用，也可以直接和我聊天。', action: 'chat', records: [], draftState: 'none', source: 'builtin_profile' }];
    try {
      localStorage.setItem(CHAT_KEY, JSON.stringify(initial));
      window.chatMessages = initial;
    } catch {}
    if (window.AiAssistantRuntime?.setChatWindowLimit) window.AiAssistantRuntime.setChatWindowLimit(60);
    if (typeof window.renderAll === 'function') window.renderAll();
    else if (typeof window.renderChat === 'function') window.renderChat();
    else window.location.reload();
    window.setTimeout(() => window.ChatSourceBadges?.refresh?.(), 80);
    updateProgress();
    showToast('已清空对话');
  }

  function installStyle() {
    document.getElementById(MODEL_STYLE_ID)?.remove();
    const style = document.createElement('style');
    style.id = MODEL_STYLE_ID;
    style.textContent = `
      .chat-summary-strip.model-picker-hero-strip{display:grid!important;grid-template-columns:auto auto auto minmax(96px,1fr)!important;align-items:center!important;gap:8px!important;min-height:46px!important;margin-bottom:5px!important}.chat-summary-strip.model-picker-hero-strip .summary-chip{display:none!important}.chat-summary-strip.model-picker-hero-strip::after{display:none!important}.model-picker-btn.hero-model-picker-btn,#view-ai #aiModeBadge,#view-ai #clearChatInlineBtn{height:40px!important;min-height:40px!important;border-radius:999px!important;border:1px solid rgba(238,246,255,.22)!important;background:radial-gradient(circle at 20% 8%,rgba(139,247,255,.14),transparent 42%),linear-gradient(145deg,rgba(255,255,255,.12),rgba(255,255,255,.038) 62%,rgba(255,255,255,.022)),rgba(126,146,205,.10)!important;box-shadow:0 7px 14px rgba(0,0,0,.09),inset 0 .7px 0 rgba(255,255,255,.24)!important;backdrop-filter:blur(12px) saturate(124%) contrast(1.02) brightness(1.03)!important;-webkit-backdrop-filter:blur(12px) saturate(124%) contrast(1.02) brightness(1.03)!important}.model-picker-btn.hero-model-picker-btn{width:auto!important;min-width:104px!important;max-width:132px!important;padding:0 12px!important;display:inline-grid!important;grid-template-columns:22px auto!important;align-items:center!important;gap:7px!important;font-size:14px!important;font-weight:920!important;color:rgba(246,250,255,.94)!important}.model-picker-btn.hero-model-picker-btn::before{content:'AI'!important;width:22px!important;height:22px!important;display:grid!important;place-items:center!important;border-radius:9px!important;font-size:9px!important;font-weight:950!important;background:linear-gradient(135deg,rgba(121,235,255,.42),rgba(142,105,255,.44))!important}.model-picker-btn.hero-model-picker-btn::after{display:none!important}#view-ai #aiModeBadge{display:inline-flex!important;align-items:center!important;justify-content:center!important;min-width:92px!important;padding:0 12px!important;font-size:13px!important;font-weight:900!important;color:rgba(224,246,255,.88)!important;white-space:nowrap!important}#view-ai #clearChatInlineBtn{display:inline-flex!important;align-items:center!important;justify-content:center!important;min-width:74px!important;padding:0 11px!important;font-size:13px!important;font-weight:900!important;color:rgba(246,250,255,.86)!important;white-space:nowrap!important}.chat-count-card{min-width:0!important;height:40px!important;padding:7px 11px!important;display:grid!important;align-content:center!important;gap:5px!important;border-radius:999px!important;color:rgba(224,242,255,.72)!important;background:radial-gradient(ellipse at 80% 0%,rgba(139,247,255,.12),transparent 40%),linear-gradient(145deg,rgba(255,255,255,.075),rgba(255,255,255,.020) 62%,rgba(255,255,255,.012)),rgba(255,255,255,.026)!important;border:1px solid rgba(255,255,255,.12)!important;box-shadow:inset 0 .6px 0 rgba(255,255,255,.18)!important;overflow:hidden!important}.chat-count-top{display:flex!important;justify-content:space-between!important;align-items:center!important;gap:6px!important;font-size:10px!important;line-height:1!important;font-weight:850!important;white-space:nowrap!important}.chat-count-top strong{color:rgba(246,250,255,.88)!important;font-size:11px!important}.chat-count-track{height:4px!important;border-radius:999px!important;overflow:hidden!important;background:rgba(255,255,255,.105)!important}.chat-count-fill{display:block!important;width:var(--chat-progress,0%)!important;height:100%!important;border-radius:inherit!important;background:linear-gradient(90deg,rgba(139,247,255,.82),rgba(154,126,255,.82))!important;box-shadow:0 0 12px rgba(139,247,255,.35)!important;transition:width .24s ease!important}.model-picker-sheet-mask.open{display:grid!important;place-items:end center!important;background:rgba(4,8,20,.34)!important;backdrop-filter:blur(14px) saturate(112%)!important;-webkit-backdrop-filter:blur(14px) saturate(112%)!important}.model-picker-sheet{width:min(94vw,500px)!important;margin:0 0 max(14px,env(safe-area-inset-bottom))!important;padding:15px!important;border-radius:30px!important;color:rgba(248,252,255,.98)!important;background:linear-gradient(145deg,rgba(255,255,255,.20),rgba(255,255,255,.075)),rgba(40,48,84,.72)!important;border:1px solid rgba(255,255,255,.28)!important;box-shadow:0 28px 78px rgba(0,0,0,.40),inset 0 1px 0 rgba(255,255,255,.34)!important;backdrop-filter:blur(26px) saturate(160%) contrast(1.03)!important;-webkit-backdrop-filter:blur(26px) saturate(160%) contrast(1.03)!important}.model-picker-head{display:flex;justify-content:space-between;gap:12px;margin-bottom:12px}.model-picker-head strong{display:block;font-size:19px!important}.model-picker-head span{display:block;margin-top:4px;font-size:12px;opacity:.68}.model-picker-close{width:34px;height:34px;border:0;border-radius:999px;background:rgba(255,255,255,.16);color:inherit;font-size:22px}.model-picker-list{display:grid;gap:9px}.model-choice{display:flex;align-items:center;gap:10px;width:100%;min-height:64px!important;padding:12px!important;border-radius:20px!important;background:rgba(255,255,255,.085)!important;border:1px solid rgba(255,255,255,.16)!important;color:inherit;text-align:left}.model-choice.active{background:linear-gradient(135deg,rgba(99,226,255,.20),rgba(145,106,255,.18))!important;border-color:rgba(139,247,255,.38)!important}.model-choice-dot{width:12px;height:12px;border-radius:999px;border:2px solid rgba(255,255,255,.52)}.model-choice.active .model-choice-dot{background:#8bf7ff;border-color:#8bf7ff;box-shadow:0 0 20px rgba(139,247,255,.54)}.model-choice-text{display:grid;gap:3px}.model-choice-text strong{font-size:14px}.model-choice-text em{font-size:12px;font-style:normal;opacity:.66;line-height:1.35}@media(max-width:390px){.chat-summary-strip.model-picker-hero-strip{grid-template-columns:auto auto auto minmax(78px,1fr)!important;gap:6px!important}.model-picker-btn.hero-model-picker-btn{min-width:92px!important;max-width:110px!important;padding:0 9px!important;font-size:13px!important}#view-ai #aiModeBadge{min-width:72px!important;padding:0 9px!important;font-size:12px!important}#view-ai #clearChatInlineBtn{min-width:58px!important;padding:0 8px!important;font-size:12px!important}.chat-count-top span{display:none!important}}
    `;
    document.head.appendChild(style);
  }

  function syncModelLabel() {
    const btn = document.querySelector('#chatModelPickerBtn');
    if (!btn) return;
    const model = currentModel();
    btn.textContent = model.short || model.label || '自动';
  }

  function renderModelSheet() {
    let mask = document.querySelector('#modelPickerSheetMask');
    if (!mask) {
      mask = document.createElement('div');
      mask.id = 'modelPickerSheetMask';
      mask.className = 'model-picker-sheet-mask';
      document.body.appendChild(mask);
    }
    const selected = currentModelId();
    mask.innerHTML = `<section class="model-picker-sheet" role="dialog" aria-modal="true" aria-label="选择云端模型"><div class="model-picker-head"><div><strong>选择云端模型</strong><span>自动模式会按可用性切换；手动选择时会优先使用指定模型。</span></div><button class="model-picker-close" type="button" data-model-picker-close>×</button></div><div class="model-picker-list">${models().map((item) => `<button type="button" class="model-choice ${item.id === selected ? 'active' : ''}" data-model-choice="${escapeHtml(item.id)}"><span class="model-choice-dot"></span><span class="model-choice-text"><strong>${escapeHtml(item.label)}</strong><em>${escapeHtml(item.hint || '')}</em></span></button>`).join('')}</div></section>`;
    return mask;
  }

  function openModelSheet() {
    renderModelSheet().classList.add('open');
  }

  function ensureModelButton(strip) {
    let btn = document.querySelector('#chatModelPickerBtn');
    if (!btn) {
      btn = document.createElement('button');
      btn.id = 'chatModelPickerBtn';
      btn.className = 'model-picker-btn';
      btn.type = 'button';
      btn.setAttribute('aria-label', '选择云端模型');
    }
    btn.classList.add('hero-model-picker-btn');
    if (btn.parentElement !== strip) strip.appendChild(btn);
    syncModelLabel();
    return btn;
  }

  function ensureCountCard(strip) {
    let card = strip.querySelector('#chatCountCard');
    if (card) return card;
    card = document.createElement('div');
    card.id = 'chatCountCard';
    card.className = 'chat-count-card';
    card.innerHTML = '<div class="chat-count-top"><span>对话进度</span><strong id="chatCountText">0 条</strong></div><div class="chat-count-track"><span id="chatCountFill" class="chat-count-fill"></span></div>';
    strip.appendChild(card);
    return card;
  }

  function updateProgress() {
    const count = readMessages().filter((item) => item?.id !== 'welcome' && (item?.role === 'user' || item?.role === 'assistant')).length;
    const percent = Math.max(4, Math.min(100, Math.round((count / 40) * 100)));
    const card = document.querySelector('#chatCountCard');
    const text = document.querySelector('#chatCountText');
    const fill = document.querySelector('#chatCountFill');
    card?.style.setProperty('--chat-progress', `${percent}%`);
    if (text) text.textContent = `${count} 条`;
    if (fill) fill.style.width = `${percent}%`;
  }

  function arrangeControls() {
    installStyle();
    document.body.classList.add('chat-panel-fixed');
    const strip = document.querySelector('.chat-summary-strip');
    if (!strip) return;
    strip.classList.add('model-picker-hero-strip');
    strip.querySelectorAll('.summary-chip').forEach((node) => node.remove());

    const modelBtn = ensureModelButton(strip);
    const badge = document.querySelector('#aiModeBadge');
    const clear = document.querySelector('#clearChatInlineBtn');
    if (badge && badge.parentElement !== strip) strip.appendChild(badge);
    if (clear && clear.parentElement !== strip) strip.appendChild(clear);
    if (clear) {
      clear.textContent = '清空';
      if (clear.dataset.fixedPanelBound !== 'true') {
        clear.dataset.fixedPanelBound = 'true';
        clear.addEventListener('click', (event) => { event.preventDefault(); clearConversation(); });
      }
    }
    ensureCountCard(strip);
    updateProgress();
    return modelBtn;
  }

  function installClicks() {
    if (document.documentElement.dataset.fixedControlClicksReady === 'true') return;
    document.documentElement.dataset.fixedControlClicksReady = 'true';
    document.addEventListener('click', (event) => {
      const modelBtn = event.target.closest?.('#chatModelPickerBtn');
      if (modelBtn) { event.preventDefault(); openModelSheet(); return; }
      const mask = document.querySelector('#modelPickerSheetMask');
      if (event.target === mask || event.target.closest?.('[data-model-picker-close]')) { mask?.classList.remove('open'); return; }
      const choice = event.target.closest?.('[data-model-choice]');
      if (choice && choice.closest('#modelPickerSheetMask')) {
        event.preventDefault();
        setModel(choice.dataset.modelChoice || 'auto');
        mask?.querySelectorAll('.model-choice').forEach((node) => node.classList.toggle('active', node === choice));
        window.setTimeout(() => mask?.classList.remove('open'), 180);
      }
    }, true);
  }

  function observeMessages() {
    const host = document.querySelector('#chatMessages');
    if (!host || host.dataset.fixedPanelProgressReady === 'true') return;
    host.dataset.fixedPanelProgressReady = 'true';
    new MutationObserver(updateProgress).observe(host, { childList: true, subtree: true });
  }

  function boot() {
    removeUnstableVisualLayer();
    patchAll();
    arrangeControls();
    installClicks();
    observeMessages();
    updateProgress();
  }

  window.NavigationExecutionCompat = { version: '2026-05-16-9-fixed-chat-control-bar', normalizeMode, normalizeNavigateParams, patchAll, removeUnstableVisualLayer, bootModelPolish: boot, bootFixedPanel: boot };

  boot();
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  [160, 360, 900, 1500].forEach((delay) => window.setTimeout(boot, delay));
  window.setInterval(boot, 1200);
})();
(() => {
  const PREF_KEY = 'ai-ledger-model-preference-v1';
  const STYLE_ID = 'chat-model-picker-style';
  const FETCH_PATCH_FLAG = '__aiLedgerModelPickerFetchPatched';

  const MODELS = [
    { id: 'auto', label: '自动', short: '自动', hint: '自动选择可用模型' },
    { id: 'kimi', label: 'Kimi K2.6', short: 'Kimi', hint: 'NVIDIA NIM · 中文闲聊/总结/多模态兜底' },
    { id: 'gemini', label: 'Gemini 2.5 Flash', short: 'Gemini', hint: 'Google Gemini · 普通聊天/知识问答' },
    { id: 'workers', label: 'Workers AI', short: 'Workers', hint: 'Cloudflare Workers AI · 兜底模型' },
  ];

  function readPreference() {
    try {
      const saved = JSON.parse(localStorage.getItem(PREF_KEY) || '{}');
      const value = String(saved.model || 'auto');
      return MODELS.some((item) => item.id === value) ? value : 'auto';
    } catch {
      return 'auto';
    }
  }

  function writePreference(model) {
    const value = MODELS.some((item) => item.id === model) ? model : 'auto';
    localStorage.setItem(PREF_KEY, JSON.stringify({ model: value, updatedAt: Date.now() }));
    window.dispatchEvent(new CustomEvent('ai-ledger-model-preference-change', { detail: { model: value } }));
  }

  function currentModel() {
    return readPreference();
  }

  function currentLabel() {
    const model = MODELS.find((item) => item.id === currentModel()) || MODELS[0];
    return model.short;
  }

  function escapeHtml(value) {
    return String(value || '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function installStyle() {
    if (document.querySelector(`#${STYLE_ID}`)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      .model-picker-btn{
        width:48px;height:48px;min-width:48px;border-radius:20px;border:1px solid rgba(255,255,255,.30);
        background:linear-gradient(145deg,rgba(255,255,255,.20),rgba(255,255,255,.08));color:rgba(255,255,255,.92);
        font-size:11px;font-weight:950;letter-spacing:-.02em;display:grid;place-items:center;text-align:center;line-height:1.05;
        backdrop-filter:blur(16px) saturate(150%);-webkit-backdrop-filter:blur(16px) saturate(150%);
        box-shadow:inset 0 1px 0 rgba(255,255,255,.28),0 10px 24px rgba(0,0,0,.12);overflow:hidden;
      }
      .model-picker-btn::before{content:'AI';font-size:10px;opacity:.7;margin-bottom:1px;display:block}
      .model-picker-btn:active{transform:scale(.96)}
      .model-picker-sheet-mask{position:fixed;inset:0;z-index:1300;display:none;background:rgba(4,8,20,.30);backdrop-filter:blur(8px);-webkit-backdrop-filter:blur(8px)}
      .model-picker-sheet-mask.open{display:grid;place-items:end center}
      .model-picker-sheet{
        width:min(94vw,520px);margin:0 0 max(14px,env(safe-area-inset-bottom));border-radius:30px;padding:16px;
        background:linear-gradient(145deg,rgba(246,250,255,.24),rgba(255,255,255,.10) 58%,rgba(255,255,255,.06)),rgba(40,48,84,.56);
        border:1px solid rgba(255,255,255,.28);box-shadow:0 28px 80px rgba(0,0,0,.34),inset 0 1px 0 rgba(255,255,255,.35);
        color:rgba(255,255,255,.94);backdrop-filter:blur(26px) saturate(170%);-webkit-backdrop-filter:blur(26px) saturate(170%);
        animation:modelSheetIn .22s cubic-bezier(.2,.9,.2,1);
      }
      @keyframes modelSheetIn{from{transform:translateY(18px) scale(.98);opacity:.3}to{transform:none;opacity:1}}
      .model-picker-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:12px}
      .model-picker-head strong{display:block;font-size:18px;letter-spacing:-.03em}.model-picker-head span{display:block;margin-top:4px;font-size:12px;opacity:.68}
      .model-picker-close{width:34px;height:34px;border:0;border-radius:999px;background:rgba(255,255,255,.16);color:inherit;font-size:22px;line-height:1}
      .model-picker-list{display:grid;gap:9px}.model-choice{display:flex;align-items:center;gap:10px;width:100%;padding:12px;border-radius:20px;border:1px solid rgba(255,255,255,.18);background:rgba(255,255,255,.10);color:inherit;text-align:left}
      .model-choice.active{background:linear-gradient(135deg,rgba(99,226,255,.24),rgba(145,106,255,.20));border-color:rgba(132,221,255,.42);box-shadow:inset 0 1px 0 rgba(255,255,255,.28)}
      .model-choice-dot{width:11px;height:11px;border-radius:999px;border:2px solid rgba(255,255,255,.52);box-shadow:0 0 0 4px rgba(255,255,255,.05)}
      .model-choice.active .model-choice-dot{background:#8bf7ff;border-color:#8bf7ff;box-shadow:0 0 18px rgba(139,247,255,.55)}
      .model-choice-text{display:grid;gap:3px}.model-choice-text strong{font-size:14px}.model-choice-text em{font-size:12px;font-style:normal;opacity:.66;line-height:1.35}
      #typingRow .chat-bubble{min-width:132px;display:flex;align-items:center;gap:10px;padding:12px 15px;border-radius:24px;background:linear-gradient(135deg,rgba(255,255,255,.18),rgba(255,255,255,.08));position:relative;overflow:hidden}
      #typingRow .chat-bubble::before{content:'';width:28px;height:28px;border-radius:50%;background:conic-gradient(from 0deg,rgba(140,245,255,.15),rgba(145,106,255,.85),rgba(140,245,255,.15));filter:blur(.1px);animation:aiThinkingSpin 1.15s linear infinite;box-shadow:0 0 22px rgba(145,106,255,.42)}
      #typingRow .chat-bubble::after{content:'正在生成';font-size:13px;font-weight:850;color:rgba(255,255,255,.84);letter-spacing:.02em;animation:aiThinkingPulse 1.4s ease-in-out infinite}
      #typingRow .typing-dot{display:none!important}
      @keyframes aiThinkingSpin{to{transform:rotate(360deg)}}
      @keyframes aiThinkingPulse{0%,100%{opacity:.62}50%{opacity:1}}
      body.assistant-compact .model-picker-btn{width:44px;height:44px;min-width:44px;border-radius:18px;font-size:10px}
    `;
    document.head.appendChild(style);
  }

  function installModelButton() {
    const form = document.querySelector('#chatForm');
    const input = document.querySelector('#aiInput');
    if (!form || !input || document.querySelector('#chatModelPickerBtn')) return;
    const btn = document.createElement('button');
    btn.id = 'chatModelPickerBtn';
    btn.className = 'model-picker-btn';
    btn.type = 'button';
    btn.setAttribute('aria-label', '选择云端模型');
    btn.textContent = currentLabel();
    const attachBtn = document.querySelector('#chatAttachBtn');
    if (attachBtn?.parentNode === form) form.insertBefore(btn, attachBtn.nextSibling);
    else form.insertBefore(btn, input);
    btn.addEventListener('click', openSheet);
  }

  function renderSheet() {
    let mask = document.querySelector('#modelPickerSheetMask');
    if (!mask) {
      mask = document.createElement('div');
      mask.id = 'modelPickerSheetMask';
      mask.className = 'model-picker-sheet-mask';
      document.body.appendChild(mask);
      mask.addEventListener('click', (event) => {
        if (event.target === mask || event.target.closest('[data-model-picker-close]')) closeSheet();
        const choice = event.target.closest('[data-model-choice]');
        if (!choice) return;
        writePreference(choice.dataset.modelChoice);
        updateButton();
        renderSheet();
        closeSheet();
      });
    }
    const selected = currentModel();
    mask.innerHTML = `<section class="model-picker-sheet" role="dialog" aria-modal="true" aria-label="选择云端模型">
      <div class="model-picker-head"><div><strong>选择云端模型</strong><span>自动模式会按额度和可用性在 Kimi、Gemini、Workers AI 之间切换。</span></div><button class="model-picker-close" type="button" data-model-picker-close>×</button></div>
      <div class="model-picker-list">
        ${MODELS.map((item) => `<button type="button" class="model-choice ${item.id === selected ? 'active' : ''}" data-model-choice="${escapeHtml(item.id)}"><span class="model-choice-dot"></span><span class="model-choice-text"><strong>${escapeHtml(item.label)}</strong><em>${escapeHtml(item.hint)}</em></span></button>`).join('')}
      </div>
    </section>`;
  }

  function openSheet() {
    renderSheet();
    document.querySelector('#modelPickerSheetMask')?.classList.add('open');
  }

  function closeSheet() {
    document.querySelector('#modelPickerSheetMask')?.classList.remove('open');
  }

  function updateButton() {
    const btn = document.querySelector('#chatModelPickerBtn');
    if (btn) btn.textContent = currentLabel();
  }

  function patchFetch() {
    if (window[FETCH_PATCH_FLAG]) return;
    window[FETCH_PATCH_FLAG] = true;
    const nativeFetch = window.fetch.bind(window);
    window.fetch = async (input, init = {}) => {
      try {
        const method = String(init?.method || (input instanceof Request ? input.method : 'GET')).toUpperCase();
        const body = init?.body;
        if (method === 'POST' && typeof body === 'string' && body.includes('messages')) {
          const data = JSON.parse(body);
          if (data && Array.isArray(data.messages) && !data.modelPreference) {
            data.modelPreference = currentModel();
            data.aiModelPreference = currentModel();
            init = { ...init, body: JSON.stringify(data) };
          }
        }
      } catch {}
      return nativeFetch(input, init);
    };
  }

  function badgeWatchdog() {
    const rows = [...document.querySelectorAll('.chat-row.assistant[data-message-id]')].filter((row) => row.id !== 'typingRow');
    const missing = rows.some((row) => !row.querySelector('.chat-source-badge-row'));
    if (missing) window.ChatSourceBadges?.refresh?.();
  }

  function boot() {
    installStyle();
    patchFetch();
    installModelButton();
    updateButton();
    setInterval(() => {
      installModelButton();
      updateButton();
      badgeWatchdog();
    }, 1200);
  }

  window.AiLedgerModelPicker = { current: currentModel, set: writePreference, models: MODELS };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();

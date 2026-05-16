(() => {
  'use strict';

  const shared = window.AiLedgerChatShared;
  if (!shared) return;

  const STYLE_ID = 'chat-model-picker-style';
  const { MODELS, readModelPreference, writeModelPreference, modelShort, escapeHtml } = shared;

  function installStyle() {
    if (document.getElementById(STYLE_ID)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      .model-picker-hero-strip{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 12px!important;}
      .model-picker-hero-strip::after{content:'✦ 轻量待命';display:inline-flex;align-items:center;justify-content:center;min-height:42px;flex:1;border-radius:999px;border:1px solid rgba(255,255,255,.18);background:rgba(255,255,255,.06);color:rgba(235,242,255,.66);font-size:13px;font-weight:850;letter-spacing:.01em;}
      .model-picker-btn{width:auto;min-width:132px;height:58px;border-radius:28px;border:1px solid rgba(255,255,255,.30);background:linear-gradient(145deg,rgba(255,255,255,.24),rgba(255,255,255,.08));color:rgba(255,255,255,.94);font-size:18px;font-weight:950;display:inline-flex;align-items:center;justify-content:center;gap:10px;text-align:center;line-height:1.05;backdrop-filter:blur(16px) saturate(150%);-webkit-backdrop-filter:blur(16px) saturate(150%);box-shadow:inset 0 1px 0 rgba(255,255,255,.28),0 10px 24px rgba(0,0,0,.12);}
      .model-picker-btn::before{content:'AI';width:36px;height:36px;display:grid;place-items:center;border-radius:14px;background:linear-gradient(135deg,rgba(179,225,255,.58),rgba(139,105,255,.48));font-size:15px;font-weight:950;color:#fff;box-shadow:inset 0 1px 0 rgba(255,255,255,.45),0 8px 18px rgba(96,115,255,.22);}
      .model-picker-btn:active{transform:scale(.975);}
      .model-picker-sheet-mask{position:fixed;inset:0;z-index:1300;display:none;background:rgba(4,8,20,.30);backdrop-filter:blur(8px);-webkit-backdrop-filter:blur(8px);}
      .model-picker-sheet-mask.open{display:grid;place-items:end center;}
      .model-picker-sheet{width:min(94vw,520px);margin:0 0 max(14px,env(safe-area-inset-bottom));border-radius:30px;padding:16px;background:linear-gradient(145deg,rgba(246,250,255,.24),rgba(255,255,255,.10) 58%,rgba(255,255,255,.06)),rgba(40,48,84,.56);border:1px solid rgba(255,255,255,.28);box-shadow:0 28px 80px rgba(0,0,0,.34),inset 0 1px 0 rgba(255,255,255,.35);color:rgba(255,255,255,.94);backdrop-filter:blur(26px) saturate(170%);-webkit-backdrop-filter:blur(26px) saturate(170%);animation:modelSheetIn .22s cubic-bezier(.2,.9,.2,1);}
      @keyframes modelSheetIn{from{transform:translateY(18px) scale(.98);opacity:.3}to{transform:none;opacity:1}}
      .model-picker-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:12px;}
      .model-picker-head strong{display:block;font-size:18px;letter-spacing:-.03em;}
      .model-picker-head span{display:block;margin-top:4px;font-size:12px;opacity:.68;line-height:1.35;}
      .model-picker-close{width:34px;height:34px;border:0;border-radius:999px;background:rgba(255,255,255,.16);color:inherit;font-size:22px;line-height:1;}
      .model-picker-list{display:grid;gap:9px;}
      .model-choice{display:flex;align-items:center;gap:10px;width:100%;padding:12px;border-radius:20px;border:1px solid rgba(255,255,255,.18);background:rgba(255,255,255,.10);color:inherit;text-align:left;}
      .model-choice.active{background:linear-gradient(135deg,rgba(99,226,255,.24),rgba(145,106,255,.20));border-color:rgba(132,221,255,.42);box-shadow:inset 0 1px 0 rgba(255,255,255,.28);}
      .model-choice-dot{width:11px;height:11px;border-radius:999px;border:2px solid rgba(255,255,255,.52);box-shadow:0 0 0 4px rgba(255,255,255,.05);}
      .model-choice.active .model-choice-dot{background:#8bf7ff;border-color:#8bf7ff;box-shadow:0 0 18px rgba(139,247,255,.55);}
      .model-choice-text{display:grid;gap:3px;}
      .model-choice-text strong{font-size:14px;}
      .model-choice-text em{font-size:12px;font-style:normal;opacity:.66;line-height:1.35;}
      body.assistant-compact .model-picker-btn{min-width:112px;height:50px;border-radius:24px;font-size:15px;}
      body.assistant-compact .model-picker-btn::before{width:30px;height:30px;border-radius:12px;font-size:12px;}
    `;
    document.head.appendChild(style);
  }

  function updateModelButton() {
    const btn = document.querySelector('#chatModelPickerBtn');
    if (!btn) return;
    btn.textContent = modelShort();
    btn.setAttribute('data-model', readModelPreference());
  }

  function ensureButton() {
    const existing = document.querySelector('#chatModelPickerBtn');
    if (existing) {
      existing.type = 'button';
      existing.classList.add('model-picker-btn');
      if (!existing.dataset.modelPickerBound) {
        existing.dataset.modelPickerBound = '1';
        existing.addEventListener('click', openModelSheet);
      }
      updateModelButton();
      return;
    }

    const form = document.querySelector('#chatForm');
    const input = document.querySelector('#aiInput');
    if (!form || !input) return;
    const btn = document.createElement('button');
    btn.id = 'chatModelPickerBtn';
    btn.className = 'model-picker-btn';
    btn.type = 'button';
    btn.setAttribute('aria-label', '选择云端模型');
    btn.addEventListener('click', openModelSheet);
    const attachBtn = document.querySelector('#chatAttachBtn');
    if (attachBtn?.parentNode === form) form.insertBefore(btn, attachBtn.nextSibling);
    else form.insertBefore(btn, input);
    updateModelButton();
  }

  function renderModelSheet() {
    let mask = document.querySelector('#modelPickerSheetMask');
    if (!mask) {
      mask = document.createElement('div');
      mask.id = 'modelPickerSheetMask';
      mask.className = 'model-picker-sheet-mask';
      document.body.appendChild(mask);
      mask.addEventListener('click', (event) => {
        if (event.target === mask || event.target.closest('[data-model-picker-close]')) closeModelSheet();
        const choice = event.target.closest('[data-model-choice]');
        if (!choice) return;
        writeModelPreference(choice.dataset.modelChoice);
        updateModelButton();
        window.dispatchEvent(new CustomEvent('ai-ledger-model-change', { detail: { model: readModelPreference() } }));
        renderModelSheet();
        closeModelSheet();
      });
    }
    const selected = readModelPreference();
    mask.innerHTML = `<section class="model-picker-sheet" role="dialog" aria-modal="true" aria-label="选择云端模型"><div class="model-picker-head"><div><strong>选择云端模型</strong><span>自动模式才会切换模型；手动选 Kimi / Mistral / Gemini / Workers 时会严格使用所选模型。</span></div><button class="model-picker-close" type="button" data-model-picker-close>×</button></div><div class="model-picker-list">${MODELS.map((item) => `<button type="button" class="model-choice ${item.id === selected ? 'active' : ''}" data-model-choice="${escapeHtml(item.id)}"><span class="model-choice-dot"></span><span class="model-choice-text"><strong>${escapeHtml(item.label)}</strong><em>${escapeHtml(item.hint)}</em></span></button>`).join('')}</div></section>`;
  }

  function openModelSheet() {
    renderModelSheet();
    document.querySelector('#modelPickerSheetMask')?.classList.add('open');
  }

  function closeModelSheet() {
    document.querySelector('#modelPickerSheetMask')?.classList.remove('open');
  }

  function boot() {
    installStyle();
    ensureButton();
    window.setInterval(() => { ensureButton(); updateModelButton(); }, 1200);
  }

  window.AiLedgerModelPicker = { current: readModelPreference, set: writeModelPreference, models: MODELS, refresh: updateModelButton };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();

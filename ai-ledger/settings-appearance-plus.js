(() => {
  const SETTINGS_KEY = "ai-assistant-appearance-plus-v1";
  const STYLE_ID = "appearance-plus-style";
  const PANEL_ID = "appearancePlusPanel";
  const SETTINGS_VERSION = 4;

  const DEFAULTS = {
    version: SETTINGS_VERSION,
    language: "zh-CN",
    fontScale: "normal",
    glassOpacity: 0,
    glassBlur: 12,
    motion: "on",
    compact: "off",
  };

  const FONT_SCALE = { small: 0.92, normal: 1, large: 1.1, xlarge: 1.2 };
  const BASE_GLASS = { panel: 0.034, control: 0.042, nav: 0.032, selected: 0.052, preview: 0.042 };

  const TEXT = {
    "zh-CN": {
      appTitle: "AI助手",
      aiEyebrow: "AI多功能助手",
      aiTitle: "对话",
      aiSubtext: "聊天、记账、提醒与任务",
      aiPlaceholder: "和我说点什么",
      navAi: "AI助手",
      navTools: "功能",
      navSettings: "设置",
      settingsEyebrow: "设置中心",
      settingsTitle: "应用设置",
      settingsSubtext: "接入登录、云端同步与个性化外观",
      panelTitle: "显示与语言",
      panelDesc: "调整语言、字体、玻璃质感和动效。透明度 0 为默认液态玻璃样式，向左更通透，向右更厚实。滑动时只更新数值，松手后再应用效果，避免界面闪烁。",
      language: "语言",
      fontSize: "字体大小",
      glassOpacity: "玻璃透明度偏移",
      glassBlur: "玻璃模糊强度",
      motion: "动画效果",
      compact: "紧凑模式",
      save: "保存显示设置",
      reset: "恢复默认",
      preview: "预览：0 就是默认液态玻璃效果。负值更透明，正值更偏白、更厚。",
      saved: "已保存显示设置",
      resetDone: "已恢复默认显示设置",
      on: "开启",
      off: "关闭",
      small: "小",
      normal: "标准",
      large: "大",
      xlarge: "超大",
    },
    en: {
      appTitle: "AI Assistant",
      aiEyebrow: "AI multifunction assistant",
      aiTitle: "Chat",
      aiSubtext: "Chat, ledger, reminders and tasks",
      aiPlaceholder: "Say something",
      navAi: "Assistant",
      navTools: "Tools",
      navSettings: "Settings",
      settingsEyebrow: "Settings",
      settingsTitle: "App Settings",
      settingsSubtext: "Account, sync and personalized appearance",
      panelTitle: "Display & Language",
      panelDesc: "Opacity 0 keeps the default liquid glass look. Slider values update while dragging; the glass effect applies after release to avoid flicker.",
      language: "Language",
      fontSize: "Font size",
      glassOpacity: "Glass opacity offset",
      glassBlur: "Glass blur",
      motion: "Motion",
      compact: "Compact mode",
      save: "Save display settings",
      reset: "Reset",
      preview: "Preview: 0 is the default liquid glass effect. Negative is clearer, positive is thicker.",
      saved: "Display settings saved",
      resetDone: "Display settings reset",
      on: "On",
      off: "Off",
      small: "Small",
      normal: "Normal",
      large: "Large",
      xlarge: "Extra large",
    },
    ja: {
      appTitle: "AIアシスタント",
      aiEyebrow: "AI多機能アシスタント",
      aiTitle: "チャット",
      aiSubtext: "会話・家計簿・リマインダー・タスク",
      aiPlaceholder: "何か話してください",
      navAi: "AI助手",
      navTools: "機能",
      navSettings: "設定",
      settingsEyebrow: "設定センター",
      settingsTitle: "アプリ設定",
      settingsSubtext: "ログイン、同期、外観を設定します",
      panelTitle: "表示と言語",
      panelDesc: "透明度 0 は標準の液体ガラス効果です。スライダーは指を離した後に反映され、ちらつきを防ぎます。",
      language: "言語",
      fontSize: "文字サイズ",
      glassOpacity: "ガラス透明度オフセット",
      glassBlur: "ガラスぼかし",
      motion: "アニメーション",
      compact: "コンパクト表示",
      save: "表示設定を保存",
      reset: "初期設定に戻す",
      preview: "プレビュー：0 が標準の液体ガラス効果です。",
      saved: "表示設定を保存しました",
      resetDone: "表示設定を初期化しました",
      on: "オン",
      off: "オフ",
      small: "小",
      normal: "標準",
      large: "大",
      xlarge: "特大",
    },
  };

  function t(key, settings = readSettings()) {
    return (TEXT[settings.language] || TEXT[DEFAULTS.language])[key] || TEXT[DEFAULTS.language][key] || key;
  }

  function escapeHtml(value) {
    return String(value || "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function clamp(value, min, max, fallback) {
    if (!Number.isFinite(value)) return fallback;
    return Math.max(min, Math.min(max, Math.round(value)));
  }

  function normalizeSettings(settings = {}) {
    return {
      version: SETTINGS_VERSION,
      language: TEXT[settings.language] ? settings.language : DEFAULTS.language,
      fontScale: FONT_SCALE[settings.fontScale] ? settings.fontScale : DEFAULTS.fontScale,
      glassOpacity: clamp(Number(settings.glassOpacity), -100, 100, DEFAULTS.glassOpacity),
      glassBlur: clamp(Number(settings.glassBlur), 0, 30, DEFAULTS.glassBlur),
      motion: settings.motion === "off" ? "off" : "on",
      compact: settings.compact === "on" ? "on" : "off",
    };
  }

  function readSettings() {
    try {
      const parsed = JSON.parse(localStorage.getItem(SETTINGS_KEY) || "{}");
      const merged = { ...DEFAULTS, ...parsed };
      if (parsed.version !== SETTINGS_VERSION) {
        merged.glassOpacity = DEFAULTS.glassOpacity;
        merged.glassBlur = DEFAULTS.glassBlur;
      }
      return normalizeSettings(merged);
    } catch {
      return { ...DEFAULTS };
    }
  }

  function alpha(base, offset) {
    return Math.max(0.004, Math.min(0.22, base + offset * 0.002)).toFixed(3);
  }

  function px(value) {
    const n = Math.max(0, Math.min(30, Number(value) || 0));
    return `${Math.round(n)}px`;
  }

  function signed(value) {
    const n = Number(value) || 0;
    return n > 0 ? `+${n}` : String(n);
  }

  function notify(message) {
    const toast = document.querySelector("#toast");
    if (!toast) return;
    toast.textContent = message;
    toast.classList.add("show");
    window.clearTimeout(notify.timer);
    notify.timer = window.setTimeout(() => toast.classList.remove("show"), 2200);
  }

  function installStyle() {
    if (document.querySelector(`#${STYLE_ID}`)) return;
    const style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = `
      :root{
        --assistant-font-scale:1;
        --assistant-glass-panel-alpha:.034;
        --assistant-glass-control-alpha:.042;
        --assistant-glass-nav-alpha:.032;
        --assistant-glass-selected-alpha:.052;
        --assistant-glass-preview-alpha:.042;
        --assistant-glass-panel-blur:12px;
        --assistant-glass-control-blur:7px;
        --assistant-glass-nav-blur:9px;
      }
      body{font-size:calc(16px * var(--assistant-font-scale));}
      .glass-card,.chat-shell,.summary-card,.metric-card,.chart-card,.tool-card,.auth-sheet,.mobile-command-card{
        background:linear-gradient(145deg,rgba(255,255,255,.058),rgba(255,255,255,.010) 46%,rgba(0,0,0,.018)),rgba(255,255,255,var(--assistant-glass-panel-alpha)) !important;
        backdrop-filter:blur(var(--assistant-glass-panel-blur)) saturate(1.16) brightness(1.05) contrast(1.02) !important;
        -webkit-backdrop-filter:blur(var(--assistant-glass-panel-blur)) saturate(1.16) brightness(1.05) contrast(1.02) !important;
      }
      .summary-chip,.record-item,.draft-card,.draft-item,textarea,input,select,.tag-btn,.range-chip,.ghost-btn,.mini-ghost-btn,.summary-box,.budget-pill,.auth-tab,.icon-btn,.delete-btn,.chat-row.assistant .chat-bubble,.tools-back,.account-row,.account-pill{
        background:linear-gradient(145deg,rgba(255,255,255,.070),rgba(255,255,255,.012) 52%,rgba(0,0,0,.012)),rgba(255,255,255,var(--assistant-glass-control-alpha)) !important;
        backdrop-filter:blur(var(--assistant-glass-control-blur)) saturate(1.12) brightness(1.04) contrast(1.01) !important;
        -webkit-backdrop-filter:blur(var(--assistant-glass-control-blur)) saturate(1.12) brightness(1.04) contrast(1.01) !important;
      }
      .bottom-nav{
        background:linear-gradient(145deg,rgba(255,255,255,.080),rgba(255,255,255,.018) 54%,rgba(0,0,0,.026)),rgba(255,255,255,var(--assistant-glass-nav-alpha)) !important;
        backdrop-filter:blur(var(--assistant-glass-nav-blur)) saturate(1.14) brightness(1.05) contrast(1.02) !important;
        -webkit-backdrop-filter:blur(var(--assistant-glass-nav-blur)) saturate(1.14) brightness(1.05) contrast(1.02) !important;
      }
      .nav-btn.active,.primary-btn,.send-btn,.confirm-btn,.range-chip.active,.auth-tab.active{
        background:linear-gradient(145deg,rgba(255,255,255,.150),rgba(255,255,255,.030) 56%,rgba(132,178,255,.030)),rgba(255,255,255,var(--assistant-glass-selected-alpha)) !important;
      }
      body.assistant-motion-off *,body.assistant-motion-off *::before,body.assistant-motion-off *::after{animation:none !important;transition:none !important;scroll-behavior:auto !important;}
      body.assistant-motion-off .reveal,body.assistant-motion-off .view.active .reveal,body.assistant-motion-off .appearance-plus-card{opacity:1 !important;transform:none !important;animation:none !important;visibility:visible !important;}
      body.assistant-compact .glass-card,body.assistant-compact .chat-shell,body.assistant-compact .tool-card{padding:14px !important;border-radius:20px !important;}
      body.assistant-compact .view{gap:12px !important;}
      body.assistant-compact .page-header{margin-bottom:10px !important;}
      body.assistant-compact .chat-messages{gap:8px !important;}
      .appearance-plus-card{display:grid;gap:14px;opacity:1 !important;transform:none !important;animation:none !important;}
      .appearance-plus-desc{margin:0;color:rgba(214,224,246,.68);font-size:13px;line-height:1.65}
      .appearance-plus-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}
      .appearance-plus-field{display:grid;gap:7px}
      .appearance-plus-field span{font-size:13px;font-weight:850;color:rgba(230,238,255,.80)}
      .appearance-select-wrap{position:relative;display:block}
      .appearance-select-wrap select{width:100%;height:56px;border:1px solid rgba(255,255,255,.16);border-radius:18px;padding:0 58px 0 14px;background:rgba(255,255,255,var(--assistant-glass-control-alpha));color:rgba(248,250,255,.96);font:inherit;outline:none;appearance:none;-webkit-appearance:none;}
      .appearance-select-wrap b{position:absolute;right:18px;top:50%;transform:translateY(-50%);font-size:26px;line-height:1;color:rgba(248,250,255,.78);pointer-events:none;font-weight:500}
      .appearance-plus-field input[type="range"]{width:100%;accent-color:#73e7ff}
      .appearance-range-row{display:flex;align-items:center;gap:12px;padding:10px 12px;border-radius:18px;background:rgba(255,255,255,var(--assistant-glass-control-alpha));border:1px solid rgba(255,255,255,.14)}
      .appearance-range-value{min-width:58px;text-align:right;color:rgba(248,250,255,.96);font-weight:850;font-size:13px}
      .appearance-toggle-row{display:flex;gap:10px;flex-wrap:wrap}
      .appearance-toggle{border:1px solid rgba(255,255,255,.14);background:rgba(255,255,255,var(--assistant-glass-control-alpha));color:rgba(230,238,255,.80);border-radius:999px;padding:10px 13px;font-weight:850;cursor:pointer}
      .appearance-toggle.active{background:rgba(255,255,255,var(--assistant-glass-selected-alpha));color:white;border-color:rgba(255,255,255,.22);box-shadow:0 8px 18px rgba(0,0,0,.12),inset 0 .7px 0 rgba(255,255,255,.34)}
      .appearance-preview{padding:14px;border-radius:20px;border:1px solid rgba(255,255,255,.16);background:rgba(255,255,255,var(--assistant-glass-preview-alpha));backdrop-filter:blur(var(--assistant-glass-control-blur));-webkit-backdrop-filter:blur(var(--assistant-glass-control-blur));color:rgba(214,224,246,.74);line-height:1.6}
      .appearance-preview strong{color:rgba(248,250,255,.98)}
      @media(max-width:720px){.appearance-plus-grid{grid-template-columns:1fr}}
    `;
    document.head.appendChild(style);
  }

  function updateGlassVars(settings) {
    const normalized = normalizeSettings(settings);
    const root = document.documentElement;
    const panelBlur = normalized.glassBlur;
    const controlBlur = Math.max(0, Math.round(panelBlur * 0.62));
    const navBlur = Math.max(0, Math.round(panelBlur * 0.78));
    root.style.setProperty("--assistant-glass-panel-alpha", alpha(BASE_GLASS.panel, normalized.glassOpacity));
    root.style.setProperty("--assistant-glass-control-alpha", alpha(BASE_GLASS.control, normalized.glassOpacity));
    root.style.setProperty("--assistant-glass-nav-alpha", alpha(BASE_GLASS.nav, normalized.glassOpacity));
    root.style.setProperty("--assistant-glass-selected-alpha", alpha(BASE_GLASS.selected, normalized.glassOpacity));
    root.style.setProperty("--assistant-glass-preview-alpha", alpha(BASE_GLASS.preview, normalized.glassOpacity));
    root.style.setProperty("--assistant-glass-panel-blur", px(panelBlur));
    root.style.setProperty("--assistant-glass-control-blur", px(controlBlur));
    root.style.setProperty("--assistant-glass-nav-blur", px(navBlur));
  }

  function applySettings(settings = readSettings()) {
    const normalized = normalizeSettings(settings);
    const root = document.documentElement;
    root.style.setProperty("--assistant-font-scale", FONT_SCALE[normalized.fontScale]);
    updateGlassVars(normalized);
    document.body.classList.toggle("assistant-motion-off", normalized.motion === "off");
    document.body.classList.toggle("assistant-compact", normalized.compact === "on");
    document.documentElement.lang = normalized.language;
    translateCoreUI(normalized);
  }

  function saveSettings(settings) {
    const normalized = normalizeSettings(settings);
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(normalized));
    applySettings(normalized);
    renderPanelValues(normalized);
    return normalized;
  }

  function setSelectValue(id, value) { const el = document.querySelector(`#${id}`); if (el) el.value = value; }
  function setRangeValue(id, value, suffix = "") {
    const el = document.querySelector(`#${id}`);
    const label = document.querySelector(`[data-range-value="${id}"]`);
    if (el) el.value = value;
    if (label) label.textContent = id === "appearanceGlassOpacity" ? signed(value) : `${value}${suffix}`;
  }
  function setToggleGroup(name, value) {
    document.querySelectorAll(`[data-appearance-toggle="${name}"]`).forEach((button) => button.classList.toggle("active", button.dataset.value === value));
  }

  function renderPanelValues(settings = readSettings()) {
    setSelectValue("appearanceLanguageSelect", settings.language);
    setSelectValue("appearanceFontSelect", settings.fontScale);
    setRangeValue("appearanceGlassOpacity", settings.glassOpacity);
    setRangeValue("appearanceGlassBlur", settings.glassBlur, "px");
    setToggleGroup("motion", settings.motion);
    setToggleGroup("compact", settings.compact);
    refreshPanelText(settings);
  }

  function refreshPanelText(settings = readSettings()) {
    const panel = document.querySelector(`#${PANEL_ID}`);
    if (!panel) return;
    ["panelTitle","panelDesc","language","fontSize","glassOpacity","glassBlur","motion","compact","save","reset"].forEach((key) => {
      const node = panel.querySelector(`[data-i18n='${key}']`);
      if (node) node.textContent = t(key, settings);
    });
    const preview = panel.querySelector("[data-i18n='preview']");
    if (preview) preview.innerHTML = `<strong>${escapeHtml(t("panelTitle", settings))}</strong> · ${escapeHtml(t("preview", settings))}`;
    panel.querySelectorAll("[data-toggle-label='on']").forEach((el) => el.textContent = t("on", settings));
    panel.querySelectorAll("[data-toggle-label='off']").forEach((el) => el.textContent = t("off", settings));
  }

  function translateCoreUI(settings = readSettings()) {
    document.title = t("appTitle", settings);
    const aiHeader = document.querySelector("#view-ai .page-header");
    if (aiHeader) {
      const eyebrow = aiHeader.querySelector(".eyebrow");
      const title = aiHeader.querySelector("h1");
      const subtext = aiHeader.querySelector(".subtext");
      if (eyebrow) eyebrow.textContent = t("aiEyebrow", settings);
      if (title) title.textContent = t("aiTitle", settings);
      if (subtext) subtext.textContent = t("aiSubtext", settings);
    }
    const input = document.querySelector("#aiInput");
    if (input) input.placeholder = t("aiPlaceholder", settings);
    const navAi = document.querySelector(".nav-btn[data-view='ai'] em");
    const navTools = document.querySelector(".nav-btn[data-view='tools'] em");
    const navSettings = document.querySelector(".nav-btn[data-view='settings'] em");
    if (navAi) navAi.textContent = t("navAi", settings);
    if (navTools) navTools.textContent = t("navTools", settings);
    if (navSettings) navSettings.textContent = t("navSettings", settings);
    const settingsHeader = document.querySelector("#view-settings .page-header");
    if (settingsHeader) {
      const eyebrow = settingsHeader.querySelector(".eyebrow");
      const title = settingsHeader.querySelector("h1");
      const subtext = settingsHeader.querySelector(".subtext");
      if (eyebrow) eyebrow.textContent = t("settingsEyebrow", settings);
      if (title) title.textContent = t("settingsTitle", settings);
      if (subtext) subtext.textContent = t("settingsSubtext", settings);
    }
  }

  function currentFormSettings() {
    return normalizeSettings({
      language: document.querySelector("#appearanceLanguageSelect")?.value || DEFAULTS.language,
      fontScale: document.querySelector("#appearanceFontSelect")?.value || DEFAULTS.fontScale,
      glassOpacity: document.querySelector("#appearanceGlassOpacity")?.value || DEFAULTS.glassOpacity,
      glassBlur: document.querySelector("#appearanceGlassBlur")?.value || DEFAULTS.glassBlur,
      motion: document.querySelector("[data-appearance-toggle='motion'].active")?.dataset.value || DEFAULTS.motion,
      compact: document.querySelector("[data-appearance-toggle='compact'].active")?.dataset.value || DEFAULTS.compact,
    });
  }

  function commitRangePreview() {
    window.clearTimeout(commitRangePreview.timer);
    commitRangePreview.timer = window.setTimeout(() => {
      applySettings(currentFormSettings());
    }, 90);
  }

  function installPanel() {
    const settingsView = document.querySelector("#view-settings");
    if (!settingsView || document.querySelector(`#${PANEL_ID}`)) return;
    const appearanceSection = document.querySelector("#backgroundPicker")?.closest("section");
    const settings = readSettings();
    const panel = document.createElement("section");
    panel.id = PANEL_ID;
    panel.className = "glass-card appearance-plus-card";
    panel.innerHTML = `
      <div class="section-head"><h2 data-i18n="panelTitle">${escapeHtml(t("panelTitle", settings))}</h2></div>
      <p class="appearance-plus-desc" data-i18n="panelDesc">${escapeHtml(t("panelDesc", settings))}</p>
      <div class="appearance-plus-grid">
        <label class="appearance-plus-field"><span data-i18n="language">${escapeHtml(t("language", settings))}</span><div class="appearance-select-wrap"><select id="appearanceLanguageSelect"><option value="zh-CN">简体中文</option><option value="en">English</option><option value="ja">日本語</option></select><b>⌄</b></div></label>
        <label class="appearance-plus-field"><span data-i18n="fontSize">${escapeHtml(t("fontSize", settings))}</span><div class="appearance-select-wrap"><select id="appearanceFontSelect"><option value="small">${escapeHtml(t("small", settings))}</option><option value="normal">${escapeHtml(t("normal", settings))}</option><option value="large">${escapeHtml(t("large", settings))}</option><option value="xlarge">${escapeHtml(t("xlarge", settings))}</option></select><b>⌄</b></div></label>
        <label class="appearance-plus-field"><span data-i18n="glassOpacity">${escapeHtml(t("glassOpacity", settings))}</span><div class="appearance-range-row"><input id="appearanceGlassOpacity" type="range" min="-100" max="100" step="1" /><strong class="appearance-range-value" data-range-value="appearanceGlassOpacity"></strong></div></label>
        <label class="appearance-plus-field"><span data-i18n="glassBlur">${escapeHtml(t("glassBlur", settings))}</span><div class="appearance-range-row"><input id="appearanceGlassBlur" type="range" min="0" max="30" step="1" /><strong class="appearance-range-value" data-range-value="appearanceGlassBlur"></strong></div></label>
        <div class="appearance-plus-field"><span data-i18n="motion">${escapeHtml(t("motion", settings))}</span><div class="appearance-toggle-row"><button class="appearance-toggle" type="button" data-appearance-toggle="motion" data-value="on"><span data-toggle-label="on">${escapeHtml(t("on", settings))}</span></button><button class="appearance-toggle" type="button" data-appearance-toggle="motion" data-value="off"><span data-toggle-label="off">${escapeHtml(t("off", settings))}</span></button></div></div>
        <div class="appearance-plus-field"><span data-i18n="compact">${escapeHtml(t("compact", settings))}</span><div class="appearance-toggle-row"><button class="appearance-toggle" type="button" data-appearance-toggle="compact" data-value="off"><span data-toggle-label="off">${escapeHtml(t("off", settings))}</span></button><button class="appearance-toggle" type="button" data-appearance-toggle="compact" data-value="on"><span data-toggle-label="on">${escapeHtml(t("on", settings))}</span></button></div></div>
      </div>
      <div class="appearance-preview" data-i18n="preview"><strong>${escapeHtml(t("panelTitle", settings))}</strong> · ${escapeHtml(t("preview", settings))}</div>
      <div class="settings-actions inline-actions"><button id="saveAppearancePlusBtn" class="ghost-btn" type="button" data-i18n="save">${escapeHtml(t("save", settings))}</button><button id="resetAppearancePlusBtn" class="ghost-btn" type="button" data-i18n="reset">${escapeHtml(t("reset", settings))}</button></div>
    `;
    if (appearanceSection) settingsView.insertBefore(panel, appearanceSection);
    else settingsView.appendChild(panel);
    renderPanelValues(settings);
    bindPanelEvents();
  }

  function bindPanelEvents() {
    document.querySelector("#appearanceLanguageSelect")?.addEventListener("change", () => {
      const next = currentFormSettings();
      applySettings(next);
      refreshPanelText(next);
    });
    document.querySelector("#appearanceFontSelect")?.addEventListener("change", () => applySettings(currentFormSettings()));

    ["appearanceGlassOpacity", "appearanceGlassBlur"].forEach((id) => {
      const slider = document.querySelector(`#${id}`);
      if (!slider) return;
      const suffix = id === "appearanceGlassBlur" ? "px" : "";
      slider.addEventListener("input", (event) => {
        setRangeValue(id, event.target.value, suffix);
      }, { passive: true });
      slider.addEventListener("change", commitRangePreview);
      slider.addEventListener("pointerup", commitRangePreview, { passive: true });
      slider.addEventListener("touchend", commitRangePreview, { passive: true });
      slider.addEventListener("keyup", (event) => {
        if (["ArrowLeft", "ArrowRight", "Home", "End", "PageUp", "PageDown"].includes(event.key)) commitRangePreview();
      });
    });

    document.querySelectorAll("[data-appearance-toggle]").forEach((button) => {
      button.addEventListener("click", () => {
        setToggleGroup(button.dataset.appearanceToggle, button.dataset.value);
        applySettings(currentFormSettings());
      });
    });
    document.querySelector("#saveAppearancePlusBtn")?.addEventListener("click", () => {
      const saved = saveSettings(currentFormSettings());
      notify(t("saved", saved));
    });
    document.querySelector("#resetAppearancePlusBtn")?.addEventListener("click", () => {
      const saved = saveSettings({ ...DEFAULTS });
      notify(t("resetDone", saved));
    });
  }

  window.AppearancePlusSettings = { read: readSettings, save: saveSettings, apply: applySettings, getText: t, defaults: DEFAULTS };
  installStyle();
  applySettings(readSettings());
  window.addEventListener("DOMContentLoaded", () => {
    installStyle();
    applySettings(readSettings());
    window.setTimeout(installPanel, 0);
    window.setTimeout(() => { installPanel(); applySettings(readSettings()); }, 300);
  });
})();

(() => {
  const SETTINGS_KEY = "ai-assistant-appearance-plus-v1";
  const STYLE_ID = "appearance-plus-style";
  const PANEL_ID = "appearancePlusPanel";

  const DEFAULTS = {
    language: "zh-CN",
    fontScale: "normal",
    glassOpacity: 68,
    glassBlur: 18,
    motion: "on",
    compact: "off",
  };

  const FONT_SCALE = {
    small: 0.92,
    normal: 1,
    large: 1.1,
    xlarge: 1.2,
  };

  const I18N = {
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
      panelDesc: "调整语言、字体、玻璃质感和动效。设置会保存在本机。",
      language: "语言",
      fontSize: "字体大小",
      glassOpacity: "玻璃透明度",
      glassBlur: "玻璃模糊强度",
      motion: "动画效果",
      compact: "紧凑模式",
      save: "保存显示设置",
      reset: "恢复默认",
      preview: "预览：这是一张玻璃卡片。调节透明度和模糊强度后，界面会立即变化。",
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
      panelDesc: "Adjust language, font size, glass effect and motion. Saved locally on this device.",
      language: "Language",
      fontSize: "Font size",
      glassOpacity: "Glass opacity",
      glassBlur: "Glass blur",
      motion: "Motion",
      compact: "Compact mode",
      save: "Save display settings",
      reset: "Reset",
      preview: "Preview: this is a glass card. Opacity and blur changes apply instantly.",
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
      panelDesc: "言語、文字サイズ、ガラス効果、アニメーションを調整します。設定は端末内に保存されます。",
      language: "言語",
      fontSize: "文字サイズ",
      glassOpacity: "ガラス透明度",
      glassBlur: "ガラスぼかし",
      motion: "アニメーション",
      compact: "コンパクト表示",
      save: "表示設定を保存",
      reset: "初期設定に戻す",
      preview: "プレビュー：これはガラスカードです。透明度とぼかしはすぐに反映されます。",
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

  function escapeHtml(value) {
    return String(value || "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function readSettings() {
    try {
      const parsed = JSON.parse(localStorage.getItem(SETTINGS_KEY) || "{}");
      return normalizeSettings({ ...DEFAULTS, ...parsed });
    } catch {
      return { ...DEFAULTS };
    }
  }

  function normalizeSettings(settings) {
    return {
      language: I18N[settings.language] ? settings.language : DEFAULTS.language,
      fontScale: FONT_SCALE[settings.fontScale] ? settings.fontScale : DEFAULTS.fontScale,
      glassOpacity: clamp(Number(settings.glassOpacity), 35, 92, DEFAULTS.glassOpacity),
      glassBlur: clamp(Number(settings.glassBlur), 4, 32, DEFAULTS.glassBlur),
      motion: settings.motion === "off" ? "off" : "on",
      compact: settings.compact === "on" ? "on" : "off",
    };
  }

  function clamp(value, min, max, fallback) {
    if (!Number.isFinite(value)) return fallback;
    return Math.max(min, Math.min(max, Math.round(value)));
  }

  function saveSettings(settings) {
    const normalized = normalizeSettings(settings);
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(normalized));
    applySettings(normalized);
    renderPanelValues(normalized);
    return normalized;
  }

  function getText(key, settings = readSettings()) {
    return (I18N[settings.language] || I18N[DEFAULTS.language])[key] || I18N[DEFAULTS.language][key] || key;
  }

  function notify(message) {
    const toast = document.querySelector("#toast");
    if (!toast) return;
    toast.textContent = message;
    toast.classList.add("show");
    window.clearTimeout(notify.timer);
    notify.timer = window.setTimeout(() => toast.classList.remove("show"), 2400);
  }

  function installStyle() {
    if (document.querySelector(`#${STYLE_ID}`)) return;
    const style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = `
      :root{
        --assistant-font-scale:1;
        --assistant-glass-alpha:.68;
        --assistant-glass-blur:18px;
        --assistant-panel-spacing:18px;
      }
      body{font-size:calc(16px * var(--assistant-font-scale));}
      .glass-card,.chat-shell,.summary-card,.metric-card,.chart-card,.tool-card,.record-item,.auth-sheet,.mobile-command-card{
        background:rgba(255,255,255,var(--assistant-glass-alpha)) !important;
        backdrop-filter:blur(var(--assistant-glass-blur)) saturate(1.25) !important;
        -webkit-backdrop-filter:blur(var(--assistant-glass-blur)) saturate(1.25) !important;
      }
      body.assistant-motion-off *,body.assistant-motion-off *::before,body.assistant-motion-off *::after{
        animation:none !important;
        transition:none !important;
        scroll-behavior:auto !important;
      }
      body.assistant-compact .glass-card,body.assistant-compact .chat-shell,body.assistant-compact .tool-card{padding:14px !important;border-radius:20px !important;}
      body.assistant-compact .view{gap:12px !important;}
      body.assistant-compact .page-header{margin-bottom:10px !important;}
      body.assistant-compact .chat-messages{gap:8px !important;}
      .appearance-plus-card{display:grid;gap:14px}
      .appearance-plus-desc{margin:0;color:#607083;font-size:13px;line-height:1.6}
      .appearance-plus-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}
      .appearance-plus-field{display:grid;gap:7px}
      .appearance-plus-field span{font-size:13px;font-weight:850;color:#425466}
      .appearance-plus-field select,.appearance-plus-field input[type="range"]{width:100%}
      .appearance-plus-field select{border:1px solid rgba(88,112,135,.22);border-radius:16px;padding:12px 13px;background:rgba(255,255,255,.70);color:#132033;font:inherit;outline:none}
      .appearance-range-row{display:flex;align-items:center;gap:12px;padding:10px 12px;border-radius:16px;background:rgba(255,255,255,.42);border:1px solid rgba(88,112,135,.16)}
      .appearance-range-row input{accent-color:#0b8f8b;flex:1}
      .appearance-range-value{min-width:54px;text-align:right;color:#102033;font-weight:850;font-size:13px}
      .appearance-toggle-row{display:flex;gap:10px;flex-wrap:wrap}
      .appearance-toggle{border:1px solid rgba(88,112,135,.18);background:rgba(255,255,255,.58);color:#425466;border-radius:999px;padding:10px 13px;font-weight:850;cursor:pointer}
      .appearance-toggle.active{background:linear-gradient(135deg,#0b8f8b,#086a73);color:white;border-color:transparent;box-shadow:0 10px 22px rgba(11,143,139,.22)}
      .appearance-preview{padding:14px;border-radius:20px;border:1px solid rgba(255,255,255,.42);background:rgba(255,255,255,var(--assistant-glass-alpha));backdrop-filter:blur(var(--assistant-glass-blur));color:#607083;line-height:1.6}
      .appearance-preview strong{color:#102033}
      @media(max-width:720px){.appearance-plus-grid{grid-template-columns:1fr}}
    `;
    document.head.appendChild(style);
  }

  function setSelectValue(id, value) {
    const el = document.querySelector(`#${id}`);
    if (el) el.value = value;
  }

  function setRangeValue(id, value, suffix = "") {
    const el = document.querySelector(`#${id}`);
    const label = document.querySelector(`[data-range-value="${id}"]`);
    if (el) el.value = value;
    if (label) label.textContent = `${value}${suffix}`;
  }

  function setToggleGroup(name, value) {
    document.querySelectorAll(`[data-appearance-toggle="${name}"]`).forEach((button) => {
      button.classList.toggle("active", button.dataset.value === value);
    });
  }

  function renderPanelValues(settings = readSettings()) {
    setSelectValue("appearanceLanguageSelect", settings.language);
    setSelectValue("appearanceFontSelect", settings.fontScale);
    setRangeValue("appearanceGlassOpacity", settings.glassOpacity, "%");
    setRangeValue("appearanceGlassBlur", settings.glassBlur, "px");
    setToggleGroup("motion", settings.motion);
    setToggleGroup("compact", settings.compact);
    refreshPanelText(settings);
  }

  function refreshPanelText(settings = readSettings()) {
    const t = (key) => getText(key, settings);
    const panel = document.querySelector(`#${PANEL_ID}`);
    if (!panel) return;
    panel.querySelector("[data-i18n='panelTitle']").textContent = t("panelTitle");
    panel.querySelector("[data-i18n='panelDesc']").textContent = t("panelDesc");
    panel.querySelector("[data-i18n='language']").textContent = t("language");
    panel.querySelector("[data-i18n='fontSize']").textContent = t("fontSize");
    panel.querySelector("[data-i18n='glassOpacity']").textContent = t("glassOpacity");
    panel.querySelector("[data-i18n='glassBlur']").textContent = t("glassBlur");
    panel.querySelector("[data-i18n='motion']").textContent = t("motion");
    panel.querySelector("[data-i18n='compact']").textContent = t("compact");
    panel.querySelector("[data-i18n='save']").textContent = t("save");
    panel.querySelector("[data-i18n='reset']").textContent = t("reset");
    panel.querySelector("[data-i18n='preview']").innerHTML = `<strong>${escapeHtml(t("panelTitle"))}</strong> · ${escapeHtml(t("preview"))}`;
    panel.querySelectorAll("[data-toggle-label='on']").forEach((el) => el.textContent = t("on"));
    panel.querySelectorAll("[data-toggle-label='off']").forEach((el) => el.textContent = t("off"));
  }

  function applySettings(settings = readSettings()) {
    const normalized = normalizeSettings(settings);
    const root = document.documentElement;
    root.style.setProperty("--assistant-font-scale", FONT_SCALE[normalized.fontScale]);
    root.style.setProperty("--assistant-glass-alpha", (normalized.glassOpacity / 100).toFixed(2));
    root.style.setProperty("--assistant-glass-blur", `${normalized.glassBlur}px`);
    document.body.classList.toggle("assistant-motion-off", normalized.motion === "off");
    document.body.classList.toggle("assistant-compact", normalized.compact === "on");
    document.documentElement.lang = normalized.language;
    translateCoreUI(normalized);
  }

  function translateCoreUI(settings = readSettings()) {
    const t = (key) => getText(key, settings);
    document.title = t("appTitle");
    const aiHeader = document.querySelector("#view-ai .page-header");
    if (aiHeader) {
      const eyebrow = aiHeader.querySelector(".eyebrow");
      const title = aiHeader.querySelector("h1");
      const subtext = aiHeader.querySelector(".subtext");
      if (eyebrow) eyebrow.textContent = t("aiEyebrow");
      if (title) title.textContent = t("aiTitle");
      if (subtext) subtext.textContent = t("aiSubtext");
    }
    const input = document.querySelector("#aiInput");
    if (input) input.placeholder = t("aiPlaceholder");
    const navAi = document.querySelector(".nav-btn[data-view='ai'] em");
    const navTools = document.querySelector(".nav-btn[data-view='tools'] em");
    const navSettings = document.querySelector(".nav-btn[data-view='settings'] em");
    if (navAi) navAi.textContent = t("navAi");
    if (navTools) navTools.textContent = t("navTools");
    if (navSettings) navSettings.textContent = t("navSettings");

    const settingsHeader = document.querySelector("#view-settings .page-header");
    if (settingsHeader) {
      const eyebrow = settingsHeader.querySelector(".eyebrow");
      const title = settingsHeader.querySelector("h1");
      const subtext = settingsHeader.querySelector(".subtext");
      if (eyebrow) eyebrow.textContent = t("settingsEyebrow");
      if (title) title.textContent = t("settingsTitle");
      if (subtext) subtext.textContent = t("settingsSubtext");
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

  function installPanel() {
    const settingsView = document.querySelector("#view-settings");
    if (!settingsView || document.querySelector(`#${PANEL_ID}`)) return;
    const appearanceSection = document.querySelector("#backgroundPicker")?.closest("section");
    const settings = readSettings();
    const t = (key) => getText(key, settings);

    const panel = document.createElement("section");
    panel.id = PANEL_ID;
    panel.className = "glass-card reveal delay-2 appearance-plus-card";
    panel.innerHTML = `
      <div class="section-head"><h2 data-i18n="panelTitle">${escapeHtml(t("panelTitle"))}</h2></div>
      <p class="appearance-plus-desc" data-i18n="panelDesc">${escapeHtml(t("panelDesc"))}</p>
      <div class="appearance-plus-grid">
        <label class="appearance-plus-field">
          <span data-i18n="language">${escapeHtml(t("language"))}</span>
          <select id="appearanceLanguageSelect">
            <option value="zh-CN">简体中文</option>
            <option value="en">English</option>
            <option value="ja">日本語</option>
          </select>
        </label>
        <label class="appearance-plus-field">
          <span data-i18n="fontSize">${escapeHtml(t("fontSize"))}</span>
          <select id="appearanceFontSelect">
            <option value="small">${escapeHtml(t("small"))}</option>
            <option value="normal">${escapeHtml(t("normal"))}</option>
            <option value="large">${escapeHtml(t("large"))}</option>
            <option value="xlarge">${escapeHtml(t("xlarge"))}</option>
          </select>
        </label>
        <label class="appearance-plus-field">
          <span data-i18n="glassOpacity">${escapeHtml(t("glassOpacity"))}</span>
          <div class="appearance-range-row">
            <input id="appearanceGlassOpacity" type="range" min="35" max="92" step="1" />
            <strong class="appearance-range-value" data-range-value="appearanceGlassOpacity"></strong>
          </div>
        </label>
        <label class="appearance-plus-field">
          <span data-i18n="glassBlur">${escapeHtml(t("glassBlur"))}</span>
          <div class="appearance-range-row">
            <input id="appearanceGlassBlur" type="range" min="4" max="32" step="1" />
            <strong class="appearance-range-value" data-range-value="appearanceGlassBlur"></strong>
          </div>
        </label>
        <div class="appearance-plus-field">
          <span data-i18n="motion">${escapeHtml(t("motion"))}</span>
          <div class="appearance-toggle-row">
            <button class="appearance-toggle" type="button" data-appearance-toggle="motion" data-value="on"><span data-toggle-label="on">${escapeHtml(t("on"))}</span></button>
            <button class="appearance-toggle" type="button" data-appearance-toggle="motion" data-value="off"><span data-toggle-label="off">${escapeHtml(t("off"))}</span></button>
          </div>
        </div>
        <div class="appearance-plus-field">
          <span data-i18n="compact">${escapeHtml(t("compact"))}</span>
          <div class="appearance-toggle-row">
            <button class="appearance-toggle" type="button" data-appearance-toggle="compact" data-value="off"><span data-toggle-label="off">${escapeHtml(t("off"))}</span></button>
            <button class="appearance-toggle" type="button" data-appearance-toggle="compact" data-value="on"><span data-toggle-label="on">${escapeHtml(t("on"))}</span></button>
          </div>
        </div>
      </div>
      <div class="appearance-preview" data-i18n="preview"><strong>${escapeHtml(t("panelTitle"))}</strong> · ${escapeHtml(t("preview"))}</div>
      <div class="settings-actions inline-actions">
        <button id="saveAppearancePlusBtn" class="ghost-btn" type="button" data-i18n="save">${escapeHtml(t("save"))}</button>
        <button id="resetAppearancePlusBtn" class="ghost-btn" type="button" data-i18n="reset">${escapeHtml(t("reset"))}</button>
      </div>
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
    document.querySelector("#appearanceGlassOpacity")?.addEventListener("input", (event) => {
      setRangeValue("appearanceGlassOpacity", event.target.value, "%");
      applySettings(currentFormSettings());
    });
    document.querySelector("#appearanceGlassBlur")?.addEventListener("input", (event) => {
      setRangeValue("appearanceGlassBlur", event.target.value, "px");
      applySettings(currentFormSettings());
    });
    document.querySelectorAll("[data-appearance-toggle]").forEach((button) => {
      button.addEventListener("click", () => {
        setToggleGroup(button.dataset.appearanceToggle, button.dataset.value);
        applySettings(currentFormSettings());
      });
    });
    document.querySelector("#saveAppearancePlusBtn")?.addEventListener("click", () => {
      const saved = saveSettings(currentFormSettings());
      notify(getText("saved", saved));
    });
    document.querySelector("#resetAppearancePlusBtn")?.addEventListener("click", () => {
      const saved = saveSettings({ ...DEFAULTS });
      notify(getText("resetDone", saved));
    });
  }

  window.AppearancePlusSettings = {
    read: readSettings,
    save: saveSettings,
    apply: applySettings,
    getText,
    defaults: DEFAULTS,
  };

  installStyle();
  applySettings(readSettings());

  window.addEventListener("DOMContentLoaded", () => {
    installStyle();
    applySettings(readSettings());
    window.setTimeout(installPanel, 0);
    window.setTimeout(() => {
      installPanel();
      applySettings(readSettings());
    }, 300);
  });
})();

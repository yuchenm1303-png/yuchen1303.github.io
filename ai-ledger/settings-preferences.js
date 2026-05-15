(() => {
  const HOME_ADDRESS_KEY = "ai-assistant-home-address-v1";
  const MAP_PROVIDER_KEY = "ai-assistant-map-provider-v1";
  const STYLE_ID = "assistant-preferences-style";

  const MAPS = {
    baidu: "百度地图",
    amap: "高德地图",
  };

  function escapeHtml(value) {
    return String(value || "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function getHomeAddress() {
    return localStorage.getItem(HOME_ADDRESS_KEY) || "";
  }

  function getMapProvider() {
    const saved = localStorage.getItem(MAP_PROVIDER_KEY);
    return saved === "amap" ? "amap" : "baidu";
  }

  function getMapLabel(provider = getMapProvider()) {
    return MAPS[provider] || MAPS.baidu;
  }

  function savePreferences(homeAddress, mapProvider) {
    const address = String(homeAddress || "").trim();
    if (address) localStorage.setItem(HOME_ADDRESS_KEY, address);
    else localStorage.removeItem(HOME_ADDRESS_KEY);
    localStorage.setItem(MAP_PROVIDER_KEY, mapProvider === "amap" ? "amap" : "baidu");
    window.dispatchEvent(new CustomEvent("assistant-preferences-changed", {
      detail: getPreferences(),
    }));
  }

  function getPreferences() {
    const mapProvider = getMapProvider();
    return {
      homeAddress: getHomeAddress(),
      mapProvider,
      mapLabel: getMapLabel(mapProvider),
    };
  }

  function isHomeDestination(destination) {
    return /^(家|回家|我家|家里|到家)$/u.test(String(destination || "").trim());
  }

  function resolveDestination(destination) {
    const text = String(destination || "").trim();
    if (isHomeDestination(text)) {
      const home = getHomeAddress().trim();
      return home || "家";
    }
    return text;
  }

  function decorateNavigationParams(params = {}) {
    const provider = getMapProvider();
    const rawDestination = String(params.destination || params.destinationAlias || "").trim();
    const destination = resolveDestination(rawDestination);
    return {
      ...params,
      appName: getMapLabel(provider),
      destination,
      destinationAlias: rawDestination,
      mapProvider: provider,
      mode: ["driving", "walking", "riding"].includes(params.mode) ? params.mode : "driving",
    };
  }

  function installStyle() {
    if (document.querySelector(`#${STYLE_ID}`)) return;
    const style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = `
      .assistant-pref-card{display:grid;gap:14px}
      .assistant-pref-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}
      .assistant-pref-field{display:grid;gap:7px}
      .assistant-pref-field span{font-size:13px;font-weight:800;color:#425466}
      .assistant-pref-field input,.assistant-pref-field select{width:100%;border:1px solid rgba(88,112,135,.22);border-radius:16px;padding:12px 13px;background:rgba(255,255,255,.70);color:#132033;font:inherit;outline:none}
      .assistant-pref-field input:focus,.assistant-pref-field select:focus{border-color:rgba(11,143,139,.55);box-shadow:0 0 0 4px rgba(11,143,139,.10)}
      .assistant-pref-preview{padding:12px 13px;border-radius:16px;background:rgba(11,143,139,.08);color:#607083;font-size:13px;line-height:1.55}
      .assistant-pref-preview strong{color:#102033}
      @media(max-width:720px){.assistant-pref-grid{grid-template-columns:1fr}}
    `;
    document.head.appendChild(style);
  }

  function renderPreview() {
    const preview = document.querySelector("#assistantPrefPreview");
    const homeInput = document.querySelector("#assistantHomeAddressInput");
    const mapSelect = document.querySelector("#assistantMapProviderSelect");
    if (!preview || !homeInput || !mapSelect) return;
    const home = homeInput.value.trim();
    const provider = mapSelect.value === "amap" ? "amap" : "baidu";
    preview.innerHTML = home
      ? `以后你说 <strong>导航回家</strong>，会用 <strong>${escapeHtml(getMapLabel(provider))}</strong> 导航到：<strong>${escapeHtml(home)}</strong>`
      : `还没填写家庭地址。填写后，“导航回家”会自动替换为具体地址。当前默认地图：<strong>${escapeHtml(getMapLabel(provider))}</strong>`;
  }

  function installSettingsPanel() {
    const settingsView = document.querySelector("#view-settings");
    const budgetSection = document.querySelector("#budgetInput")?.closest("section");
    if (!settingsView || document.querySelector("#assistantPreferencePanel")) return;

    const prefs = getPreferences();
    const panel = document.createElement("section");
    panel.id = "assistantPreferencePanel";
    panel.className = "glass-card reveal delay-3 assistant-pref-card";
    panel.innerHTML = `
      <div class="section-head"><h2>手机偏好设置</h2></div>
      <div class="assistant-pref-grid">
        <label class="assistant-pref-field">
          <span>家庭地址</span>
          <input id="assistantHomeAddressInput" value="${escapeHtml(prefs.homeAddress)}" placeholder="例如：重庆大学虎溪校区 / 某小区" />
        </label>
        <label class="assistant-pref-field">
          <span>默认地图</span>
          <select id="assistantMapProviderSelect">
            <option value="baidu" ${prefs.mapProvider === "baidu" ? "selected" : ""}>百度地图</option>
            <option value="amap" ${prefs.mapProvider === "amap" ? "selected" : ""}>高德地图</option>
          </select>
        </label>
      </div>
      <div id="assistantPrefPreview" class="assistant-pref-preview"></div>
      <div class="settings-actions inline-actions">
        <button id="saveAssistantPrefsBtn" class="ghost-btn" type="button">保存手机偏好</button>
      </div>
    `;

    if (budgetSection) settingsView.insertBefore(panel, budgetSection);
    else settingsView.appendChild(panel);

    const homeInput = document.querySelector("#assistantHomeAddressInput");
    const mapSelect = document.querySelector("#assistantMapProviderSelect");
    const saveBtn = document.querySelector("#saveAssistantPrefsBtn");

    homeInput?.addEventListener("input", renderPreview);
    mapSelect?.addEventListener("change", renderPreview);
    saveBtn?.addEventListener("click", () => {
      savePreferences(homeInput?.value || "", mapSelect?.value || "baidu");
      const toast = document.querySelector("#toast");
      if (toast) {
        toast.textContent = "已保存手机偏好";
        toast.classList.add("show");
        window.clearTimeout(savePreferences.toastTimer);
        savePreferences.toastTimer = window.setTimeout(() => toast.classList.remove("show"), 2400);
      }
      renderPreview();
    });
    renderPreview();
  }

  window.AssistantPreferences = {
    getHomeAddress,
    getMapProvider,
    getMapLabel,
    getPreferences,
    savePreferences,
    isHomeDestination,
    resolveDestination,
    decorateNavigationParams,
  };

  window.addEventListener("DOMContentLoaded", () => {
    installStyle();
    window.setTimeout(installSettingsPanel, 0);
    window.setTimeout(installSettingsPanel, 300);
  });
})();

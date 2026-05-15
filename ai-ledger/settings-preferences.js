(() => {
  const HOME_ADDRESS_KEY = "ai-assistant-home-address-v1";
  const MAP_PROVIDER_KEY = "ai-assistant-map-provider-v1";
  const NAV_PREF_KEY = "ai-assistant-navigation-preferences-v2";
  const STYLE_ID = "assistant-preferences-style";

  const MAPS = {
    baidu: "百度地图",
    amap: "高德地图",
  };

  const MODES = {
    driving: "驾车",
    walking: "步行",
    riding: "骑行",
    transit: "公交/地铁",
  };

  const PLACE_LABELS = {
    home: "家",
    school: "学校",
    work: "公司",
    dorm: "宿舍",
  };

  const DEFAULT_PREFS = {
    version: 2,
    places: {
      home: "",
      school: "",
      work: "",
      dorm: "",
    },
    customPlaces: [],
    mapProvider: "baidu",
    defaultMode: "driving",
    routeOptions: {
      avoidHighway: false,
      avoidTolls: false,
      preferSubway: false,
      preferLessWalk: false,
      useRealtimeTraffic: true,
    },
    lastUpdated: "",
  };

  function escapeHtml(value) {
    return String(value || "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function clone(value) {
    return JSON.parse(JSON.stringify(value));
  }

  function cleanText(value, max = 80) {
    return String(value || "").trim().replace(/\s+/g, " ").slice(0, max);
  }

  function normalizeProvider(value) {
    return value === "amap" ? "amap" : "baidu";
  }

  function normalizeMode(value) {
    return Object.prototype.hasOwnProperty.call(MODES, value) ? value : "driving";
  }

  function normalizeCustomPlaces(list) {
    if (!Array.isArray(list)) return [];
    const seen = new Set();
    return list
      .map((item) => ({
        name: cleanText(item?.name, 16),
        address: cleanText(item?.address, 120),
      }))
      .filter((item) => item.name && item.address)
      .filter((item) => {
        const key = item.name.toLowerCase();
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      })
      .slice(0, 10);
  }

  function normalizePrefs(raw = {}) {
    const oldHome = localStorage.getItem(HOME_ADDRESS_KEY) || "";
    const oldMap = localStorage.getItem(MAP_PROVIDER_KEY) || "";
    const prefs = clone(DEFAULT_PREFS);
    const input = raw && typeof raw === "object" ? raw : {};

    prefs.places.home = cleanText(input.places?.home || input.homeAddress || oldHome, 120);
    prefs.places.school = cleanText(input.places?.school, 120);
    prefs.places.work = cleanText(input.places?.work, 120);
    prefs.places.dorm = cleanText(input.places?.dorm, 120);
    prefs.customPlaces = normalizeCustomPlaces(input.customPlaces);
    prefs.mapProvider = normalizeProvider(input.mapProvider || oldMap);
    prefs.defaultMode = normalizeMode(input.defaultMode);
    prefs.routeOptions = {
      ...prefs.routeOptions,
      ...(input.routeOptions && typeof input.routeOptions === "object" ? input.routeOptions : {}),
    };
    Object.keys(prefs.routeOptions).forEach((key) => {
      prefs.routeOptions[key] = Boolean(prefs.routeOptions[key]);
    });
    prefs.lastUpdated = input.lastUpdated || "";
    return prefs;
  }

  function readPrefs() {
    try {
      return normalizePrefs(JSON.parse(localStorage.getItem(NAV_PREF_KEY) || "{}"));
    } catch {
      return normalizePrefs({});
    }
  }

  function writePrefs(nextPrefs) {
    const prefs = normalizePrefs({ ...nextPrefs, lastUpdated: new Date().toISOString() });
    localStorage.setItem(NAV_PREF_KEY, JSON.stringify(prefs));

    if (prefs.places.home) localStorage.setItem(HOME_ADDRESS_KEY, prefs.places.home);
    else localStorage.removeItem(HOME_ADDRESS_KEY);
    localStorage.setItem(MAP_PROVIDER_KEY, prefs.mapProvider);

    window.dispatchEvent(new CustomEvent("assistant-preferences-changed", {
      detail: getPreferences(),
    }));
    return prefs;
  }

  function getPreferences() {
    return readPrefs();
  }

  function getHomeAddress() {
    return readPrefs().places.home || "";
  }

  function getMapProvider() {
    return readPrefs().mapProvider;
  }

  function getMapLabel(provider = getMapProvider()) {
    return MAPS[normalizeProvider(provider)] || MAPS.baidu;
  }

  function getModeLabel(mode = readPrefs().defaultMode) {
    return MODES[normalizeMode(mode)] || MODES.driving;
  }

  function savePreferences(homeAddress, mapProvider) {
    const prefs = readPrefs();
    prefs.places.home = cleanText(homeAddress, 120);
    prefs.mapProvider = normalizeProvider(mapProvider);
    return writePrefs(prefs);
  }

  function savePreferencesObject(nextPrefs) {
    return writePrefs(normalizePrefs(nextPrefs));
  }

  function normalizePlaceKey(alias) {
    const text = cleanText(alias, 24).replace(/^(去|到|回|导航到|导航去)/u, "");
    if (/^(家|我家|家里|回家|到家|家庭)$/u.test(text)) return "home";
    if (/^(学校|校区|大学|学院|上课地方)$/u.test(text)) return "school";
    if (/^(公司|单位|上班地方|实习单位|办公室)$/u.test(text)) return "work";
    if (/^(宿舍|寝室|住处|住的地方)$/u.test(text)) return "dorm";
    return "";
  }

  function isHomeDestination(destination) {
    return normalizePlaceKey(destination) === "home";
  }

  function findCustomPlace(name, prefs = readPrefs()) {
    const text = cleanText(name, 24).toLowerCase();
    if (!text) return null;
    return prefs.customPlaces.find((item) => item.name.toLowerCase() === text) || null;
  }

  function getPlaceAddress(alias, prefs = readPrefs()) {
    const key = normalizePlaceKey(alias);
    if (key) {
      return {
        key,
        label: PLACE_LABELS[key],
        address: prefs.places[key] || "",
      };
    }
    const custom = findCustomPlace(alias, prefs);
    if (custom) {
      return {
        key: `custom:${custom.name}`,
        label: custom.name,
        address: custom.address,
      };
    }
    return null;
  }

  function resolveDestination(destination) {
    const prefs = readPrefs();
    const raw = cleanText(destination, 120);
    const place = getPlaceAddress(raw, prefs);
    if (!place) return { destination: raw, alias: raw, matchedPlace: null, missingAddress: false };
    return {
      destination: place.address || raw,
      alias: raw,
      matchedPlace: place,
      missingAddress: !place.address,
    };
  }

  function decorateNavigationParams(params = {}, options = {}) {
    const prefs = readPrefs();
    const sourceText = String(options.sourceText || "");
    const explicitProvider = /高德|amap/i.test(sourceText) ? "amap" : /百度|baidu/i.test(sourceText) ? "baidu" : "";
    const mapProvider = normalizeProvider(params.mapProvider || explicitProvider || prefs.mapProvider);
    const rawDestination = cleanText(params.destinationAlias || params.destination || "", 120);
    const resolved = resolveDestination(rawDestination);
    const mode = normalizeMode(params.mode || prefs.defaultMode);

    return {
      ...params,
      appName: getMapLabel(mapProvider),
      mapProvider,
      mode,
      routeOptions: {
        ...prefs.routeOptions,
        ...(params.routeOptions && typeof params.routeOptions === "object" ? params.routeOptions : {}),
      },
      destination: resolved.destination,
      destinationAlias: resolved.alias,
      matchedPlaceKey: resolved.matchedPlace?.key || "",
      matchedPlaceLabel: resolved.matchedPlace?.label || "",
      placeAddressMissing: resolved.missingAddress,
      homeAddressMissing: resolved.matchedPlace?.key === "home" && resolved.missingAddress,
    };
  }

  function mergeCustomPlaces(baseList, updates = []) {
    const map = new Map(normalizeCustomPlaces(baseList).map((item) => [item.name.toLowerCase(), item]));
    normalizeCustomPlaces(updates).forEach((item) => map.set(item.name.toLowerCase(), item));
    return [...map.values()].slice(0, 10);
  }

  function applyPreferenceUpdate(updates = {}) {
    const prefs = readPrefs();
    let count = 0;

    if (updates.mapProvider) {
      prefs.mapProvider = normalizeProvider(updates.mapProvider);
      count += 1;
    }
    if (updates.defaultMode) {
      prefs.defaultMode = normalizeMode(updates.defaultMode);
      count += 1;
    }
    if (updates.places && typeof updates.places === "object") {
      Object.entries(updates.places).forEach(([key, value]) => {
        const placeKey = normalizePlaceKey(key) || key;
        if (Object.prototype.hasOwnProperty.call(prefs.places, placeKey)) {
          prefs.places[placeKey] = cleanText(value, 120);
          count += 1;
        } else if (cleanText(key, 16) && cleanText(value, 120)) {
          prefs.customPlaces = mergeCustomPlaces(prefs.customPlaces, [{ name: key, address: value }]);
          count += 1;
        }
      });
    }
    if (Array.isArray(updates.customPlaces)) {
      const before = prefs.customPlaces.length;
      prefs.customPlaces = mergeCustomPlaces(prefs.customPlaces, updates.customPlaces);
      if (prefs.customPlaces.length !== before || updates.customPlaces.length) count += 1;
    }
    if (updates.routeOptions && typeof updates.routeOptions === "object") {
      Object.entries(updates.routeOptions).forEach(([key, value]) => {
        if (Object.prototype.hasOwnProperty.call(prefs.routeOptions, key)) {
          prefs.routeOptions[key] = Boolean(value);
          count += 1;
        }
      });
    }

    const saved = writePrefs(prefs);
    return {
      ok: true,
      message: count ? `已保存 ${count} 项导航偏好。` : "导航偏好没有变化。",
      preferences: saved,
      count,
    };
  }

  function parseCustomPlacesText(text) {
    return String(text || "")
      .split(/\n+/)
      .map((line) => line.trim())
      .filter(Boolean)
      .map((line) => {
        const parts = line.split(/[：:，,]/);
        return {
          name: cleanText(parts.shift(), 16),
          address: cleanText(parts.join(" "), 120),
        };
      })
      .filter((item) => item.name && item.address);
  }

  function formatCustomPlaces(list) {
    return normalizeCustomPlaces(list).map((item) => `${item.name}：${item.address}`).join("\n");
  }

  function optionText(options) {
    const rows = [];
    if (options.avoidHighway) rows.push("避开高速");
    if (options.avoidTolls) rows.push("少收费");
    if (options.preferSubway) rows.push("地铁优先");
    if (options.preferLessWalk) rows.push("少步行");
    if (options.useRealtimeTraffic) rows.push("参考实时路况");
    return rows.length ? rows.join("、") : "无特殊偏好";
  }

  function installStyle() {
    const old = document.querySelector(`#${STYLE_ID}`);
    if (old) old.remove();
    const style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = `
      .assistant-pref-card{display:grid;gap:14px}
      .assistant-pref-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}
      .assistant-pref-field{display:grid;gap:7px}
      .assistant-pref-field.full{grid-column:1/-1}
      .assistant-pref-field span{font-size:13px;font-weight:800;color:#425466}
      .assistant-pref-field input,.assistant-pref-field select,.assistant-pref-field textarea{width:100%;border:1px solid rgba(88,112,135,.22);border-radius:16px;padding:12px 13px;background:rgba(255,255,255,.70);color:#132033;font:inherit;outline:none}
      .assistant-pref-field textarea{min-height:86px;resize:vertical;line-height:1.5}
      .assistant-pref-field input:focus,.assistant-pref-field select:focus,.assistant-pref-field textarea:focus{border-color:rgba(11,143,139,.55);box-shadow:0 0 0 4px rgba(11,143,139,.10)}
      .assistant-route-options{grid-column:1/-1;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}
      .assistant-route-options label{display:flex;align-items:center;gap:8px;min-height:38px;padding:9px 11px;border-radius:14px;background:rgba(255,255,255,.52);color:#425466;font-size:13px;font-weight:750}
      .assistant-route-options input{width:16px;height:16px;accent-color:#0b8f8b}
      .assistant-pref-preview{padding:12px 13px;border-radius:16px;background:rgba(11,143,139,.08);color:#607083;font-size:13px;line-height:1.6}
      .assistant-pref-preview strong{color:#102033}
      @media(max-width:720px){.assistant-pref-grid,.assistant-route-options{grid-template-columns:1fr}}
    `;
    document.head.appendChild(style);
  }

  function renderPreview() {
    const preview = document.querySelector("#assistantPrefPreview");
    const homeInput = document.querySelector("#assistantHomeAddressInput");
    const schoolInput = document.querySelector("#assistantSchoolAddressInput");
    const workInput = document.querySelector("#assistantWorkAddressInput");
    const dormInput = document.querySelector("#assistantDormAddressInput");
    const mapSelect = document.querySelector("#assistantMapProviderSelect");
    const modeSelect = document.querySelector("#assistantDefaultModeSelect");
    if (!preview || !homeInput || !mapSelect || !modeSelect) return;

    const routeOptions = {
      avoidHighway: Boolean(document.querySelector("#assistantAvoidHighwayInput")?.checked),
      avoidTolls: Boolean(document.querySelector("#assistantAvoidTollsInput")?.checked),
      preferSubway: Boolean(document.querySelector("#assistantPreferSubwayInput")?.checked),
      preferLessWalk: Boolean(document.querySelector("#assistantPreferLessWalkInput")?.checked),
      useRealtimeTraffic: Boolean(document.querySelector("#assistantRealtimeTrafficInput")?.checked),
    };

    const places = [
      homeInput.value.trim() ? `家：${homeInput.value.trim()}` : "",
      schoolInput?.value.trim() ? `学校：${schoolInput.value.trim()}` : "",
      workInput?.value.trim() ? `公司：${workInput.value.trim()}` : "",
      dormInput?.value.trim() ? `宿舍：${dormInput.value.trim()}` : "",
    ].filter(Boolean).slice(0, 3).join("；");

    preview.innerHTML = places
      ? `以后可直接说 <strong>导航回家 / 去学校 / 去公司</strong>。当前默认：<strong>${escapeHtml(getMapLabel(mapSelect.value))}</strong> · <strong>${escapeHtml(getModeLabel(modeSelect.value))}</strong>。<br>${escapeHtml(places)}<br>路线习惯：<strong>${escapeHtml(optionText(routeOptions))}</strong>`
      : `还没填写常用地址。填写后，AI 可以把“导航回家、去学校、去公司”自动替换为具体地址。当前默认：<strong>${escapeHtml(getMapLabel(mapSelect.value))}</strong> · <strong>${escapeHtml(getModeLabel(modeSelect.value))}</strong>。`;
  }

  function installSettingsPanel() {
    const settingsView = document.querySelector("#view-settings");
    const budgetSection = document.querySelector("#budgetInput")?.closest("section");
    if (!settingsView || document.querySelector("#assistantPreferencePanel")) return;

    const prefs = readPrefs();
    const panel = document.createElement("section");
    panel.id = "assistantPreferencePanel";
    panel.className = "glass-card reveal delay-3 assistant-pref-card";
    panel.innerHTML = `
      <div class="section-head"><h2>手机偏好设置</h2></div>
      <div class="assistant-pref-grid">
        <label class="assistant-pref-field">
          <span>家庭地址</span>
          <input id="assistantHomeAddressInput" value="${escapeHtml(prefs.places.home)}" placeholder="例如：重庆大学虎溪校区 / 某小区" />
        </label>
        <label class="assistant-pref-field">
          <span>学校 / 校区</span>
          <input id="assistantSchoolAddressInput" value="${escapeHtml(prefs.places.school)}" placeholder="例如：温州大学北校区" />
        </label>
        <label class="assistant-pref-field">
          <span>公司 / 实习单位</span>
          <input id="assistantWorkAddressInput" value="${escapeHtml(prefs.places.work)}" placeholder="例如：某某大厦" />
        </label>
        <label class="assistant-pref-field">
          <span>宿舍 / 住处</span>
          <input id="assistantDormAddressInput" value="${escapeHtml(prefs.places.dorm)}" placeholder="例如：学生公寓一区" />
        </label>
        <label class="assistant-pref-field">
          <span>默认地图</span>
          <select id="assistantMapProviderSelect">
            <option value="baidu" ${prefs.mapProvider === "baidu" ? "selected" : ""}>百度地图</option>
            <option value="amap" ${prefs.mapProvider === "amap" ? "selected" : ""}>高德地图</option>
          </select>
        </label>
        <label class="assistant-pref-field">
          <span>默认出行方式</span>
          <select id="assistantDefaultModeSelect">
            <option value="driving" ${prefs.defaultMode === "driving" ? "selected" : ""}>驾车</option>
            <option value="walking" ${prefs.defaultMode === "walking" ? "selected" : ""}>步行</option>
            <option value="riding" ${prefs.defaultMode === "riding" ? "selected" : ""}>骑行</option>
            <option value="transit" ${prefs.defaultMode === "transit" ? "selected" : ""}>公交/地铁</option>
          </select>
        </label>
        <label class="assistant-pref-field full">
          <span>其他常用地点</span>
          <textarea id="assistantCustomPlacesInput" placeholder="每行一个，例如：\n健身房：某某健身中心\n高铁站：温州南站">${escapeHtml(formatCustomPlaces(prefs.customPlaces))}</textarea>
        </label>
        <div class="assistant-route-options" aria-label="路线习惯">
          <label><input id="assistantAvoidHighwayInput" type="checkbox" ${prefs.routeOptions.avoidHighway ? "checked" : ""}>避开高速</label>
          <label><input id="assistantAvoidTollsInput" type="checkbox" ${prefs.routeOptions.avoidTolls ? "checked" : ""}>少收费</label>
          <label><input id="assistantPreferSubwayInput" type="checkbox" ${prefs.routeOptions.preferSubway ? "checked" : ""}>地铁优先</label>
          <label><input id="assistantPreferLessWalkInput" type="checkbox" ${prefs.routeOptions.preferLessWalk ? "checked" : ""}>少步行</label>
          <label><input id="assistantRealtimeTrafficInput" type="checkbox" ${prefs.routeOptions.useRealtimeTraffic ? "checked" : ""}>参考实时路况</label>
        </div>
      </div>
      <div id="assistantPrefPreview" class="assistant-pref-preview"></div>
      <div class="settings-actions inline-actions">
        <button id="saveAssistantPrefsBtn" class="ghost-btn" type="button">保存导航偏好</button>
      </div>
    `;

    if (budgetSection) settingsView.insertBefore(panel, budgetSection);
    else settingsView.appendChild(panel);

    const ids = [
      "#assistantHomeAddressInput",
      "#assistantSchoolAddressInput",
      "#assistantWorkAddressInput",
      "#assistantDormAddressInput",
      "#assistantMapProviderSelect",
      "#assistantDefaultModeSelect",
      "#assistantCustomPlacesInput",
      "#assistantAvoidHighwayInput",
      "#assistantAvoidTollsInput",
      "#assistantPreferSubwayInput",
      "#assistantPreferLessWalkInput",
      "#assistantRealtimeTrafficInput",
    ];
    ids.forEach((id) => {
      const el = document.querySelector(id);
      el?.addEventListener(el.type === "checkbox" || el.tagName === "SELECT" ? "change" : "input", renderPreview);
    });

    document.querySelector("#saveAssistantPrefsBtn")?.addEventListener("click", () => {
      const saved = writePrefs({
        places: {
          home: document.querySelector("#assistantHomeAddressInput")?.value || "",
          school: document.querySelector("#assistantSchoolAddressInput")?.value || "",
          work: document.querySelector("#assistantWorkAddressInput")?.value || "",
          dorm: document.querySelector("#assistantDormAddressInput")?.value || "",
        },
        customPlaces: parseCustomPlacesText(document.querySelector("#assistantCustomPlacesInput")?.value || ""),
        mapProvider: document.querySelector("#assistantMapProviderSelect")?.value || "baidu",
        defaultMode: document.querySelector("#assistantDefaultModeSelect")?.value || "driving",
        routeOptions: {
          avoidHighway: Boolean(document.querySelector("#assistantAvoidHighwayInput")?.checked),
          avoidTolls: Boolean(document.querySelector("#assistantAvoidTollsInput")?.checked),
          preferSubway: Boolean(document.querySelector("#assistantPreferSubwayInput")?.checked),
          preferLessWalk: Boolean(document.querySelector("#assistantPreferLessWalkInput")?.checked),
          useRealtimeTraffic: Boolean(document.querySelector("#assistantRealtimeTrafficInput")?.checked),
        },
      });
      const toast = document.querySelector("#toast");
      if (toast) {
        toast.textContent = "已保存导航偏好";
        toast.classList.add("show");
        window.clearTimeout(writePrefs.toastTimer);
        writePrefs.toastTimer = window.setTimeout(() => toast.classList.remove("show"), 2400);
      }
      document.querySelector("#assistantCustomPlacesInput").value = formatCustomPlaces(saved.customPlaces);
      renderPreview();
    });
    renderPreview();
  }

  window.AssistantPreferences = {
    MAPS,
    MODES,
    PLACE_LABELS,
    getHomeAddress,
    getMapProvider,
    getMapLabel,
    getModeLabel,
    getPreferences,
    savePreferences,
    savePreferencesObject,
    applyPreferenceUpdate,
    isHomeDestination,
    normalizePlaceKey,
    getPlaceAddress,
    resolveDestination,
    decorateNavigationParams,
  };

  window.addEventListener("DOMContentLoaded", () => {
    installStyle();
    window.setTimeout(installSettingsPanel, 0);
    window.setTimeout(installSettingsPanel, 300);
  });
})();

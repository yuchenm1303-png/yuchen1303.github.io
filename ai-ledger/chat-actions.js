(() => {
  const CHAT_KEY = "ai-ledger-chat-v2";
  const MOBILE_STYLE_ID = "mobile-command-style";
  const MOBILE_TOOLS = [
    {
      name: "mobile.set_alarm",
      action: "mobile_command",
      commandType: "set_alarm",
      title: "设置系统闹钟",
    },
    {
      name: "mobile.open_app",
      action: "mobile_command",
      commandType: "open_app",
      title: "打开手机应用",
    },
    {
      name: "mobile.navigate",
      action: "mobile_command",
      commandType: "navigate",
      title: "地图导航",
      params: {
        mapProvider: "baidu | amap",
        destination: "目的地或常用地点别名",
        mode: "driving | walking | riding | transit",
        routeOptions: "避开高速、少收费、地铁优先、少步行、实时路况等偏好",
      },
    },
    {
      name: "mobile.navigation_preferences",
      action: "mobile_command",
      commandType: "navigate",
      intent: "navigation_preference",
      title: "保存导航偏好",
      params: {
        updates: "家、学校、公司、宿舍、默认地图、默认出行方式和路线习惯",
      },
    },
  ];
  const initialChat = [
    {
      id: "welcome",
      role: "assistant",
      content: "你好，我是你的 AI 助手。你可以让我记账、查账单、设置提醒、打开应用，也可以直接和我聊天。",
      action: "chat",
      records: [],
      draftState: "none",
    },
  ];

  const MODE_LABELS = {
    driving: "驾车",
    walking: "步行",
    riding: "骑行",
    transit: "公交/地铁",
  };

  const ROUTE_OPTION_LABELS = {
    avoidHighway: "避开高速",
    avoidTolls: "少收费",
    preferSubway: "地铁优先",
    preferLessWalk: "少步行",
    useRealtimeTraffic: "参考实时路况",
  };

  function clearConversation() {
    const ok = window.confirm("确定清空当前对话吗？账单记录不会被删除。");
    if (!ok) return;
    localStorage.setItem(CHAT_KEY, JSON.stringify(initialChat));
    window.location.reload();
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function readChatMessages() {
    try {
      const parsed = JSON.parse(localStorage.getItem(CHAT_KEY) || "[]");
      return Array.isArray(parsed) && parsed.length ? parsed : [...initialChat];
    } catch {
      return [...initialChat];
    }
  }

  function makeCommandId(prefix = "cmd") {
    if (crypto.randomUUID) return `${prefix}-${crypto.randomUUID()}`;
    return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  function isNavigationPreferenceCommand(command) {
    return command?.commandKind === "navigation_preference" || command?.params?.intent === "navigation_preference" || command?.params?.updates;
  }

  function addDays(date, days) {
    const next = new Date(date);
    next.setDate(next.getDate() + days);
    return next;
  }

  function pad2(value) {
    return String(value).padStart(2, "0");
  }

  function toIsoDate(date) {
    return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
  }

  function formatDisplayDate(date) {
    return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`;
  }

  function cleanText(value, max = 120) {
    return String(value || "").trim().replace(/\s+/g, " ").slice(0, max);
  }

  function normalizeMeridiem(hour, text) {
    if (/下午|晚上|傍晚|今晚/u.test(text) && hour < 12) return hour + 12;
    if (/中午/u.test(text) && hour < 11) return hour + 12;
    if (/凌晨|早上|上午|明早|明天早上/u.test(text) && hour === 12) return 0;
    return hour;
  }

  function parseAlarmCommand(text) {
    if (!/(闹钟|叫我|提醒我|提醒一下|叫醒|起床)/u.test(text)) return null;
    const timeMatch = text.match(/(\d{1,2})(?:[:：点时](\d{1,2})?分?)?/u);
    if (!timeMatch) return null;

    let hour = Number(timeMatch[1]);
    const minute = Number(timeMatch[2] || 0);
    if (!Number.isFinite(hour) || !Number.isFinite(minute) || minute < 0 || minute > 59) return null;
    hour = normalizeMeridiem(hour, text);
    if (hour < 0 || hour > 23) return null;

    const now = new Date();
    let target = new Date(now);
    if (/后天/u.test(text)) target = addDays(now, 2);
    else if (/明天|明早|明晚/u.test(text)) target = addDays(now, 1);
    else if (/今天|今晚/u.test(text)) target = new Date(now);
    else {
      const candidate = new Date(now);
      candidate.setHours(hour, minute, 0, 0);
      if (candidate <= now) target = addDays(now, 1);
    }
    target.setHours(hour, minute, 0, 0);

    const labelMatch = text.match(/(?:提醒我|叫我|叫醒我|闹钟)(.*)$/u);
    const rawLabel = labelMatch?.[1] || "";
    const label = rawLabel
      .replace(/(明天|后天|今天|今晚|明早|明晚|上午|下午|晚上|早上|凌晨|中午)/gu, "")
      .replace(/\d{1,2}(?:[:：点时]\d{0,2})?分?/gu, "")
      .replace(/^(去|要|一下|起床)/u, "")
      .trim() || (/起床|叫醒/u.test(text) ? "起床" : "提醒");

    return {
      id: makeCommandId("alarm"),
      type: "set_alarm",
      title: "设置闹钟",
      summary: `${formatDisplayDate(target)} ${pad2(hour)}:${pad2(minute)}`,
      params: {
        date: toIsoDate(target),
        hour,
        minute,
        label,
      },
    };
  }

  function parseOpenAppCommand(text) {
    const match = text.match(/(?:打开|启动|帮我打开)\s*([\u4e00-\u9fa5A-Za-z0-9]+)$/u);
    if (!match) return null;
    const appName = match[1].trim();
    if (!appName || /(闹钟|提醒|记账)/u.test(appName)) return null;
    return {
      id: makeCommandId("app"),
      type: "open_app",
      title: "打开应用",
      summary: appName,
      params: { appName },
    };
  }

  function inferMapProvider(text) {
    if (/高德|amap/i.test(text)) return "amap";
    if (/百度|baidu/i.test(text)) return "baidu";
    return "";
  }

  function inferTravelMode(text, fallback = "") {
    if (/公交|地铁|轨道|轻轨|换乘|坐车|公共交通/u.test(text)) return "transit";
    if (/步行|走路|步走/u.test(text)) return "walking";
    if (/骑行|骑车|单车|自行车|电动车/u.test(text)) return "riding";
    if (/驾车|开车|自驾|打车|出租车|网约车/u.test(text)) return "driving";
    return fallback || window.AssistantPreferences?.getPreferences?.().defaultMode || "driving";
  }

  function inferRouteOptions(text) {
    const options = {};
    if (/避开高速|不走高速|不要高速|少走高速/u.test(text)) options.avoidHighway = true;
    if (/高速优先|走高速/u.test(text) && !/不走高速|不要高速/u.test(text)) options.avoidHighway = false;
    if (/少收费|少花钱|避免收费|不走收费|避开收费/u.test(text)) options.avoidTolls = true;
    if (/地铁优先|优先地铁|多坐地铁/u.test(text)) options.preferSubway = true;
    if (/少步行|少走路|不要走太多|步行少一点/u.test(text)) options.preferLessWalk = true;
    if (/实时路况|躲拥堵|避开拥堵|避堵/u.test(text)) options.useRealtimeTraffic = true;
    return options;
  }

  function routeOptionText(options = {}) {
    return Object.entries(options)
      .filter(([, value]) => Boolean(value))
      .map(([key]) => ROUTE_OPTION_LABELS[key] || key)
      .join("、") || "按默认路线";
  }

  function normalizePlaceKey(alias) {
    return window.AssistantPreferences?.normalizePlaceKey?.(alias) || (() => {
      const text = cleanText(alias, 24);
      if (/^(家|我家|家里|回家|到家)$/u.test(text)) return "home";
      if (/^(学校|校区|大学|学院)$/u.test(text)) return "school";
      if (/^(公司|单位|办公室)$/u.test(text)) return "work";
      if (/^(宿舍|寝室|住处)$/u.test(text)) return "dorm";
      return "";
    })();
  }

  function parseNavigationPreferenceCommand(text) {
    if (!/(默认|以后|偏好|习惯|地址|设为|设置为|保存为|改成|定为|记住|少步行|避开高速|少收费|地铁优先)/u.test(text)) return null;
    const updates = { places: {}, customPlaces: [], routeOptions: {} };
    const summaryRows = [];

    const provider = inferMapProvider(text);
    if (provider && /(默认|以后|导航|地图|偏好|习惯)/u.test(text)) {
      updates.mapProvider = provider;
      summaryRows.push(["默认地图", provider === "amap" ? "高德地图" : "百度地图"]);
    }

    const mode = inferTravelMode(text, "");
    if (mode && /(默认|以后|导航|出行方式|路线|通勤|偏好|习惯)/u.test(text)) {
      updates.defaultMode = mode;
      summaryRows.push(["默认方式", MODE_LABELS[mode] || "驾车"]);
    }

    const routeOptions = inferRouteOptions(text);
    if (Object.keys(routeOptions).length) {
      updates.routeOptions = { ...updates.routeOptions, ...routeOptions };
      summaryRows.push(["路线习惯", routeOptionText(routeOptions)]);
    }

    const placePatterns = [
      /(?:把|将)?(家|我家|家里|学校|校区|公司|单位|办公室|宿舍|寝室|住处)(?:的)?(?:地址|位置)?(?:设为|设置为|改成|定为|保存为|记为|是)\s*([^，。；;\n]+)/u,
      /([^，。；;\n]{1,12})(?:地址|位置)(?:设为|设置为|改成|定为|保存为|记为|是)\s*([^，。；;\n]+)/u,
      /以后(?:去|回|到)([^，。；;\n]{1,12})(?:就是|去|到)?\s*([^，。；;\n]+)/u,
    ];

    placePatterns.forEach((pattern) => {
      const match = text.match(pattern);
      if (!match) return;
      const alias = cleanText(match[1], 16);
      const address = cleanText(match[2], 120)
        .replace(/^(在|是|为|到|去)/u, "")
        .replace(/(默认|以后|导航|地图)$/u, "")
        .trim();
      if (!alias || !address) return;
      const key = normalizePlaceKey(alias);
      if (key) updates.places[key] = address;
      else updates.customPlaces.push({ name: alias, address });
      summaryRows.push([key ? `常用地址 · ${alias}` : `自定义地点 · ${alias}`, address]);
    });

    updates.customPlaces = updates.customPlaces.filter((item) => item.name && item.address);
    if (!Object.keys(updates.places).length) delete updates.places;
    if (!updates.customPlaces.length) delete updates.customPlaces;
    if (!Object.keys(updates.routeOptions).length) delete updates.routeOptions;

    const hasUpdates = Boolean(
      updates.mapProvider || updates.defaultMode || updates.places || updates.customPlaces || updates.routeOptions
    );
    if (!hasUpdates) return null;

    return {
      id: makeCommandId("nav-pref"),
      type: "navigate",
      commandKind: "navigation_preference",
      title: "保存导航偏好",
      summary: summaryRows.map(([key, value]) => `${key}：${value}`).join("；") || "更新导航偏好",
      params: {
        intent: "navigation_preference",
        updates,
        rows: summaryRows,
      },
    };
  }

  function cleanDestination(text) {
    return cleanText(text, 120)
      .replace(/^(百度地图|高德地图|地图|帮我|请|给我|用百度|用高德|打开地图|开车|驾车|步行|走路|骑行|公交|地铁|坐公交|坐地铁)/u, "")
      .replace(/(怎么走|怎么去|路线|导航|导航一下|带路)$/u, "")
      .replace(/^(到|去|回)/u, "")
      .trim();
  }

  function parseNavigationCommand(text) {
    if (!/(导航|路线|带我去|回家|到家|怎么走|怎么去|去学校|去公司|去宿舍|去寝室|去家里)/u.test(text)) return null;

    const destinationMatch = text.match(/(?:导航(?:到|去)?|路线到|带我去|怎么去|怎么到)\s*([^，。；;\n]+)/u)
      || text.match(/去\s*([^，。；;\n]+?)(?:怎么走|怎么去|路线|导航)?$/u)
      || text.match(/(?:回|到)(家|学校|公司|宿舍|寝室)$/u);
    let destination = destinationMatch?.[1]?.trim() || "";

    if (/回家|到家|去家|家里|我家/u.test(text)) destination = "家";
    if (/去学校|到学校|回学校/u.test(text)) destination = "学校";
    if (/去公司|到公司|回公司|去单位|到单位/u.test(text)) destination = "公司";
    if (/去宿舍|回宿舍|到宿舍|去寝室|回寝室/u.test(text)) destination = "宿舍";

    destination = cleanDestination(destination);
    if (!destination || /^(打开|启动)?(百度地图|高德地图|地图)$/u.test(destination)) return null;

    const mode = inferTravelMode(text);
    const routeOptions = inferRouteOptions(text);
    const provider = inferMapProvider(text);
    const decorated = window.AssistantPreferences?.decorateNavigationParams?.({
      mapProvider: provider || undefined,
      destination,
      destinationAlias: destination,
      mode,
      routeOptions,
    }, { sourceText: text }) || {
      appName: provider === "amap" ? "高德地图" : "百度地图",
      mapProvider: provider || "baidu",
      destination,
      destinationAlias: destination,
      mode,
      routeOptions,
    };

    return {
      id: makeCommandId("nav"),
      type: "navigate",
      title: `${decorated.appName || "地图"}导航`,
      summary: decorated.placeAddressMissing ? `${decorated.destinationAlias || destination}（未填写地址）` : `到 ${decorated.destination}`,
      params: decorated,
    };
  }

  function parseMobileCommand(text) {
    return parseAlarmCommand(text) || parseNavigationPreferenceCommand(text) || parseNavigationCommand(text) || parseOpenAppCommand(text);
  }

  function getStatusText(state) {
    if (state === "done") return "已执行";
    if (state === "cancelled") return "已取消";
    if (state === "failed") return "执行失败";
    return "待确认";
  }

  function getActionRows(command) {
    if (command.type === "set_alarm") {
      return [
        ["动作", "设置闹钟"],
        ["时间", command.summary],
        ["标签", command.params.label],
      ];
    }
    if (command.type === "open_app") {
      return [
        ["动作", "打开应用"],
        ["应用", command.params.appName],
      ];
    }
    if (isNavigationPreferenceCommand(command)) {
      const rows = command.params?.rows?.length ? command.params.rows : [["偏好", command.summary || "更新导航偏好"]];
      return [["动作", "保存导航偏好"], ...rows];
    }
    if (command.type === "navigate") {
      const modeLabel = MODE_LABELS[command.params.mode] || "驾车";
      const rows = [
        ["动作", `${command.params.appName || "地图"}导航`],
        ["目的地", command.params.placeAddressMissing ? `${command.params.destinationAlias || command.params.destination}（未填写地址）` : command.params.destination],
        ["方式", modeLabel],
      ];
      const options = routeOptionText(command.params.routeOptions || {});
      if (options !== "按默认路线") rows.push(["路线偏好", options]);
      return rows;
    }
    return [["动作", command.title || command.type]];
  }

  function renderMobileCard(command, state = "pending", message = "") {
    command.id = command.id || makeCommandId(command.type || "cmd");
    const rows = getActionRows(command)
      .map(([key, value]) => `<div class="mobile-command-row"><span>${escapeHtml(key)}</span><strong>${escapeHtml(value)}</strong></div>`)
      .join("");
    const confirmText = isNavigationPreferenceCommand(command) ? "确认保存" : "确认执行";
    const buttons = state === "pending"
      ? `<div class="mobile-command-actions">
          <button class="mobile-command-confirm" type="button" data-mobile-run="${escapeHtml(command.id)}">${confirmText}</button>
          <button class="mobile-command-cancel" type="button" data-mobile-cancel="${escapeHtml(command.id)}">取消</button>
        </div>`
      : "";
    const note = message ? `<div class="mobile-command-message">${escapeHtml(message)}</div>` : "";
    return `<div class="mobile-command-card" data-mobile-card="${escapeHtml(command.id)}">
      <div class="mobile-command-head">
        <span class="mobile-command-title">${escapeHtml(command.title)}</span>
        <span class="mobile-command-status ${escapeHtml(state)}">${getStatusText(state)}</span>
      </div>
      <div class="mobile-command-detail">${rows}</div>
      ${buttons}
      ${note}
    </div>`;
  }

  function createMobileReply(command) {
    if (command.type === "set_alarm") {
      return `我理解为要${command.summary}设置“${command.params.label}”闹钟，确认后我再执行。`;
    }
    if (isNavigationPreferenceCommand(command)) {
      return `我整理好了导航偏好：${command.summary || "更新导航习惯"}。确认后我会保存到手机偏好里。`;
    }
    if (command.type === "navigate") {
      const map = command.params?.appName || "地图";
      const mode = MODE_LABELS[command.params?.mode] || "驾车";
      if (command.params?.placeAddressMissing) {
        return `我知道你想去“${command.params.destinationAlias || command.params.destination}”，但这个常用地址还没填写。你可以先确认尝试打开${map}，也可以说“把${command.params.destinationAlias}设为具体地址”。`;
      }
      return `我理解为要用${map}${mode}导航到“${command.params.destination}”，确认后我再执行。`;
    }
    return `我理解为要打开“${command.params.appName}”，确认后我再执行。`;
  }

  function getCapacitorPlugin() {
    return window.Capacitor?.Plugins?.MobileAssistant || window.Capacitor?.Plugins?.MobileTools || null;
  }

  async function executeCommand(command) {
    if (isNavigationPreferenceCommand(command)) {
      if (!window.AssistantPreferences?.applyPreferenceUpdate) {
        return { ok: false, message: "导航偏好模块还没有加载完成。" };
      }
      return window.AssistantPreferences.applyPreferenceUpdate(command.params?.updates || {});
    }

    const plugin = getCapacitorPlugin();
    if (!plugin) {
      return {
        ok: false,
        message: "当前网页端只能生成动作指令；打包到 Android 后，需要接入原生插件才能真正执行。",
      };
    }
    if (command.type === "set_alarm" && typeof plugin.setAlarm === "function") {
      return await plugin.setAlarm(command.params);
    }
    if (command.type === "open_app" && typeof plugin.openApp === "function") {
      return await plugin.openApp(command.params);
    }
    if (command.type === "navigate" && typeof plugin.navigate === "function") {
      return await plugin.navigate(command.params);
    }
    return { ok: false, message: "Android 插件还没有实现这个动作。" };
  }

  function updateCard(commandId, state, message) {
    const card = document.querySelector(`[data-mobile-card="${CSS.escape(commandId)}"]`);
    if (!card) return;
    const status = card.querySelector(".mobile-command-status");
    if (status) {
      status.className = `mobile-command-status ${state}`;
      status.textContent = getStatusText(state);
    }
    card.querySelector(".mobile-command-actions")?.remove();
    card.querySelector(".mobile-command-message")?.remove();
    if (message) card.insertAdjacentHTML("beforeend", `<div class="mobile-command-message">${escapeHtml(message)}</div>`);
  }

  function installMobileStyles() {
    if (document.getElementById(MOBILE_STYLE_ID)) return;
    const style = document.createElement("style");
    style.id = MOBILE_STYLE_ID;
    style.textContent = `
      .chat-row.assistant .chat-bubble.chat-response:has(.mobile-command-card) {
        max-width: min(82%, 330px) !important;
        width: fit-content !important;
        height: auto !important;
        min-height: 0 !important;
        padding: 6px !important;
        display: block !important;
      }

      .mobile-command-extra .chat-response {
        width: fit-content !important;
        max-width: min(82%, 330px) !important;
      }

      .mobile-command-card {
        width: min(100%, 244px) !important;
        inline-size: min(100%, 244px) !important;
        height: auto !important;
        min-height: 0 !important;
        max-height: max-content !important;
        aspect-ratio: auto !important;
        display: inline-grid !important;
        grid-template-rows: auto auto auto auto !important;
        grid-auto-rows: min-content !important;
        align-content: start !important;
        justify-self: start !important;
        gap: 0 !important;
        margin: 4px 0 0 !important;
        padding: 8px 9px 9px !important;
        border: 1px solid rgba(255,255,255,.23) !important;
        border-radius: 15px !important;
        background:
          linear-gradient(145deg, rgba(255,255,255,.118), rgba(255,255,255,.038) 56%, rgba(255,255,255,.020)),
          rgba(238,246,255,.078) !important;
        color: rgba(248,250,255,.94) !important;
        box-shadow:
          0 5px 12px rgba(0,0,0,.060),
          inset 0 .7px 0 rgba(255,255,255,.25),
          inset 0 -.7px 0 rgba(8,18,34,.036) !important;
        backdrop-filter: blur(7px) saturate(116%);
        -webkit-backdrop-filter: blur(7px) saturate(116%);
        overflow: hidden !important;
      }

      .mobile-command-card::before,
      .mobile-command-card::after {
        display: none !important;
        content: none !important;
      }

      .mobile-command-card * {
        box-sizing: border-box !important;
      }

      .mobile-command-head {
        display: flex !important;
        align-items: center !important;
        justify-content: space-between !important;
        gap: 8px !important;
        margin: 0 0 6px !important;
        padding: 0 !important;
        height: auto !important;
        min-height: 0 !important;
      }

      .mobile-command-title {
        display: block !important;
        min-width: 0 !important;
        font-size: 14px !important;
        line-height: 1.16 !important;
        font-weight: 880 !important;
        letter-spacing: -.1px !important;
        color: rgba(250,252,255,.97) !important;
        overflow-wrap: anywhere !important;
      }

      .mobile-command-status {
        flex: 0 0 auto !important;
        padding: 3px 8px !important;
        border-radius: 999px !important;
        font-size: 11px !important;
        line-height: 1.16 !important;
        font-weight: 850 !important;
        background: rgba(98,240,218,.14) !important;
        color: rgba(128,255,235,.94) !important;
      }

      .mobile-command-status.done {
        background: rgba(116,235,175,.14) !important;
        color: rgba(160,255,205,.94) !important;
      }

      .mobile-command-status.cancelled,
      .mobile-command-status.failed {
        background: rgba(255,116,132,.14) !important;
        color: rgba(255,170,184,.95) !important;
      }

      .mobile-command-detail {
        display: grid !important;
        grid-auto-rows: min-content !important;
        gap: 3px !important;
        margin: 5px 0 7px !important;
        padding: 0 !important;
        height: auto !important;
        min-height: 0 !important;
        align-content: start !important;
      }

      .mobile-command-row {
        display: grid !important;
        grid-template-columns: 3.9em minmax(0,1fr) !important;
        align-items: baseline !important;
        gap: 6px !important;
        margin: 0 !important;
        padding: 0 !important;
        height: auto !important;
        min-height: 0 !important;
        color: rgba(224,233,250,.66) !important;
        font-size: 12px !important;
        line-height: 1.22 !important;
      }

      .mobile-command-row strong {
        color: rgba(250,252,255,.96) !important;
        font-weight: 850 !important;
        text-align: right !important;
        overflow-wrap: anywhere !important;
        word-break: break-word !important;
        font-size: 13px !important;
        line-height: 1.22 !important;
      }

      .mobile-command-actions {
        display: flex !important;
        align-items: center !important;
        gap: 7px !important;
        flex-wrap: wrap !important;
        margin: 6px 0 0 !important;
        padding: 0 !important;
        height: auto !important;
        min-height: 0 !important;
      }

      .mobile-command-actions button {
        border: 1px solid rgba(255,255,255,.15) !important;
        border-radius: 12px !important;
        height: 30px !important;
        min-height: 30px !important;
        padding: 5px 10px !important;
        font-size: 12.5px !important;
        line-height: 1 !important;
        font-weight: 880 !important;
        cursor: pointer;
        box-shadow:
          inset 0 .7px 0 rgba(255,255,255,.16),
          0 5px 10px rgba(0,0,0,.075) !important;
      }

      .mobile-command-confirm {
        color: white !important;
        background: linear-gradient(135deg, rgba(20,190,190,.90), rgba(45,139,220,.82)) !important;
      }

      .mobile-command-cancel {
        color: rgba(242,247,255,.88) !important;
        background: rgba(255,255,255,.080) !important;
      }

      .mobile-command-message {
        margin-top: 6px !important;
        color: rgba(224,233,250,.66) !important;
        font-size: 12px !important;
        line-height: 1.3 !important;
      }

      @media (max-width:390px) {
        .chat-row.assistant .chat-bubble.chat-response:has(.mobile-command-card) {
          max-width: 76% !important;
          padding: 5px !important;
        }
        .mobile-command-card {
          width: min(100%, 230px) !important;
          inline-size: min(100%, 230px) !important;
          padding: 8px !important;
        }
      }
    `;
    document.head.appendChild(style);
  }

  function installMobileCardHandlers() {
    document.addEventListener("click", async (event) => {
      const runBtn = event.target.closest("[data-mobile-run]");
      const cancelBtn = event.target.closest("[data-mobile-cancel]");
      if (!runBtn && !cancelBtn) return;

      const commandId = runBtn?.dataset.mobileRun || cancelBtn?.dataset.mobileCancel;
      const messages = readChatMessages();
      const message = [...messages].reverse().find((item) => item.mobileCommand?.id === commandId);
      const command = message?.mobileCommand;
      if (!command) return;

      if (cancelBtn) {
        updateCard(commandId, "cancelled", "已取消执行。账单和其他数据不会受影响。");
        return;
      }

      updateCard(commandId, "pending", isNavigationPreferenceCommand(command) ? "正在保存导航偏好……" : "正在调用 Android 能力……");
      try {
        const result = await executeCommand(command);
        if (result?.ok) {
          updateCard(commandId, "done", result.message || "已执行。");
        } else {
          updateCard(commandId, "failed", result?.message || "暂时无法执行这个动作。");
        }
      } catch (error) {
        updateCard(commandId, "failed", String(error?.message || error || "执行失败"));
      }
    });
  }

  window.MobileCommandActions = {
    tools: MOBILE_TOOLS,
    parse: parseMobileCommand,
    renderCard: renderMobileCard,
    createReply: createMobileReply,
  };

  window.addEventListener("DOMContentLoaded", () => {
    document.querySelector("#clearChatInlineBtn")?.addEventListener("click", clearConversation);
    installMobileStyles();
    installMobileCardHandlers();
  });
})();
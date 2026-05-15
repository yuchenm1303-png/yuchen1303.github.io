(() => {
  const NAV_STYLE_ID = "navigation-preference-style";

  function createChatId() {
    return typeof createId === "function"
      ? createId()
      : (crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`);
  }

  function escapeHtml(value) {
    return String(value || "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function getPrefs() {
    return window.AssistantPreferences?.getPreferences?.() || {
      homeAddress: "",
      mapProvider: "baidu",
      mapLabel: "百度地图",
    };
  }

  function isNavigationText(text) {
    return /(导航|路线|带我去|回家|到家|怎么走|怎么去|怎么到)/u.test(String(text || ""));
  }

  function isHomeText(text) {
    return /(回家|到家|去家|家里|我家)/u.test(String(text || ""));
  }

  function parseDestination(text) {
    const value = String(text || "").trim();
    if (isHomeText(value)) return "家";
    const match = value.match(/(?:导航(?:到|去)?|路线到|带我去|怎么去|怎么到)\s*([\u4e00-\u9fa5A-Za-z0-9·.\- ]+)$/u)
      || value.match(/去\s*([\u4e00-\u9fa5A-Za-z0-9·.\- ]+?)(?:怎么走|路线|导航)$/u);
    return (match?.[1] || "")
      .replace(/^(百度地图|高德地图|地图|帮我|请|给我)/u, "")
      .replace(/(?:怎么走|路线|导航)$/u, "")
      .trim();
  }

  function parseMode(text) {
    if (/步行|走路/u.test(text)) return "walking";
    if (/骑行|骑车|单车/u.test(text)) return "riding";
    return "driving";
  }

  function normalizeMapProviderFromText(text, fallback) {
    if (/高德地图|高德/u.test(text)) return "amap";
    if (/百度地图|百度/u.test(text)) return "baidu";
    return fallback === "amap" ? "amap" : "baidu";
  }

  function mapLabel(provider) {
    return provider === "amap" ? "高德地图" : "百度地图";
  }

  function buildNavigationCommand(text) {
    if (!isNavigationText(text)) return null;
    const rawDestination = parseDestination(text);
    if (!rawDestination || /^(打开|启动)?(百度地图|高德地图|地图)$/u.test(rawDestination)) return null;

    const prefs = getPrefs();
    const isHome = rawDestination === "家";
    if (isHome && !prefs.homeAddress) {
      return {
        missingHomeAddress: true,
        reply: "还没有设置家庭地址。你可以先到设置中心填写“家庭地址”，之后说“导航回家”我就能直接生成导航动作。",
      };
    }

    const provider = normalizeMapProviderFromText(text, prefs.mapProvider);
    const destination = isHome ? prefs.homeAddress : rawDestination;
    const mode = parseMode(text);
    return {
      type: "navigate",
      title: `${mapLabel(provider)}导航`,
      summary: `到 ${destination}`,
      params: {
        appName: mapLabel(provider),
        mapProvider: provider,
        destination,
        destinationAlias: rawDestination,
        mode,
      },
    };
  }

  function createNavigationReply(command) {
    const modeLabel = {
      driving: "驾车",
      walking: "步行",
      riding: "骑行",
    }[command.params.mode] || "驾车";
    const aliasText = command.params.destinationAlias === "家" ? "（已替换为你的家庭地址）" : "";
    return `我理解为要用${command.params.appName}${modeLabel}导航到“${command.params.destination}”${aliasText}，确认后我再执行。`;
  }

  function savePlainReply(userText, reply) {
    if (!Array.isArray(chatMessages)) return false;
    chatMessages.push({ id: createChatId(), role: "user", content: userText });
    chatMessages.push({
      id: createChatId(),
      role: "assistant",
      content: reply,
      action: "chat",
      records: [],
      draftState: "none",
      mobileCommand: null,
      source: "navigation_preferences",
    });
    if (typeof saveChatMessages === "function") saveChatMessages();
    if (typeof renderAll === "function") renderAll();
    return true;
  }

  function saveNavigationCommand(userText, command) {
    if (!Array.isArray(chatMessages)) return false;
    const commandWithId = { ...command, id: command.id || createChatId() };
    chatMessages.push({ id: createChatId(), role: "user", content: userText });
    chatMessages.push({
      id: createChatId(),
      role: "assistant",
      content: createNavigationReply(commandWithId),
      action: "mobile_command",
      records: [],
      draftState: "none",
      mobileCommand: commandWithId,
      source: "navigation_preferences",
    });
    if (typeof saveChatMessages === "function") saveChatMessages();
    if (typeof renderAll === "function") renderAll();
    return true;
  }

  function installNavigationSubmitHook() {
    const form = document.querySelector("#chatForm");
    const input = document.querySelector("#aiInput");
    if (!form || !input || form.dataset.navigationPreferenceHook === "ready") return;
    form.dataset.navigationPreferenceHook = "ready";

    form.addEventListener("submit", (event) => {
      const text = input.value.trim();
      const result = buildNavigationCommand(text);
      if (!result) return;

      event.preventDefault();
      event.stopImmediatePropagation();
      input.value = "";
      input.style.height = "auto";

      if (result.missingHomeAddress) savePlainReply(text, result.reply);
      else saveNavigationCommand(text, result);
    }, true);
  }

  function installMobileCommandDecorator() {
    if (!window.MobileCommandActions || window.MobileCommandActions.__NAV_PREF_DECORATED__) return;
    const originalParse = window.MobileCommandActions.parse;
    const originalReply = window.MobileCommandActions.createReply;
    const originalRender = window.MobileCommandActions.renderCard;

    window.MobileCommandActions.parse = (text) => {
      const nav = buildNavigationCommand(text);
      if (nav && !nav.missingHomeAddress) return nav;
      return originalParse?.(text) || null;
    };

    window.MobileCommandActions.createReply = (command) => {
      if (command?.type === "navigate" && command.params?.mapProvider) return createNavigationReply(command);
      return originalReply?.(command) || "我整理好了这个手机动作，确认后我再执行。";
    };

    window.MobileCommandActions.renderCard = (command, state, message) => {
      if (command?.type !== "navigate" || !command.params?.mapProvider || !originalRender) {
        return originalRender?.(command, state, message) || "";
      }
      return originalRender({
        ...command,
        title: `${command.params.appName || mapLabel(command.params.mapProvider)}导航`,
      }, state, message);
    };

    window.MobileCommandActions.__NAV_PREF_DECORATED__ = true;
  }

  function installStyle() {
    if (document.querySelector(`#${NAV_STYLE_ID}`)) return;
    const style = document.createElement("style");
    style.id = NAV_STYLE_ID;
    style.textContent = `.mobile-command-card strong{word-break:break-word}`;
    document.head.appendChild(style);
  }

  window.NavigationPreferences = {
    buildNavigationCommand,
    createNavigationReply,
    installNavigationSubmitHook,
    installMobileCommandDecorator,
  };

  window.addEventListener("DOMContentLoaded", () => {
    installStyle();
    window.setTimeout(installMobileCommandDecorator, 0);
    window.setTimeout(installMobileCommandDecorator, 300);
    window.setTimeout(installNavigationSubmitHook, 0);
    window.setTimeout(installNavigationSubmitHook, 300);
  });
})();

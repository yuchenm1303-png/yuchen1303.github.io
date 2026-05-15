const WORKER_VERSION = "ai-ledger-worker-command-protocol-v1";
const DEFAULT_GEMINI_MODEL = "gemini-1.5-flash";

const ALLOWED_ACTIONS = ["chat", "draft", "mobile_command"];
const ALLOWED_INTENTS = [
  "navigation.start",
  "navigation.modify",
  "navigation.preference.set",
  "alarm.set",
  "app.open",
  "ledger.create",
];
const NAV_MODES = ["driving", "walking", "riding", "transit"];
const MAP_PROVIDERS = ["baidu", "amap"];
const ALLOWED_CATEGORIES = ["餐饮", "交通", "购物", "居住", "饮品", "工资", "礼物", "其他"];

const jsonHeaders = {
  "content-type": "application/json; charset=utf-8",
};

export default {
  async fetch(request, env) {
    const corsHeaders = getCorsHeaders(request, env);
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders });
    }

    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, version: WORKER_VERSION }, 200, corsHeaders);
    }

    if (request.method !== "POST") {
      return json({ error: "Method not allowed", code: "method_not_allowed", version: WORKER_VERSION }, 405, corsHeaders);
    }

    let body;
    try {
      body = await request.json();
    } catch {
      return json({ error: "Invalid JSON body", code: "invalid_json_body", version: WORKER_VERSION }, 400, corsHeaders);
    }

    const now = normalizeIsoDate(body?.now) || new Date().toISOString().slice(0, 10);
    const messages = normalizeMessages(body?.messages);
    const text = String(body?.text || "").trim();
    const lastUserText = getLastUserText(messages, text);
    const conversation = messages.length ? buildConversation(messages) : lastUserText;
    const pendingDraft = sanitizeRecords(body?.pendingDraft, now);
    const ledgerContext = sanitizeLedgerContext(body?.ledgerContext, now);
    const clientTools = sanitizeClientTools(body?.clientTools);
    const commandProtocol = normalizeCommandProtocol(body?.commandProtocol);
    const navigationContext = normalizeNavigationContext(body?.navigationContext);
    const webSearch = normalizeWebSearch(body);

    if (!conversation) {
      return json({ error: "messages or text is required", code: "missing_conversation", version: WORKER_VERSION }, 400, corsHeaders);
    }

    if (conversation.length > 8000) {
      return json({ error: "conversation is too long", code: "conversation_too_long", version: WORKER_VERSION }, 400, corsHeaders);
    }

    const deterministic = tryDeterministicReply({
      lastUserText,
      pendingDraft,
      ledgerContext,
      now,
      commandProtocol,
    });
    if (deterministic) {
      return json(normalizeWorkerResponse(deterministic, now, "gemini_structured"), 200, corsHeaders);
    }

    if (webSearch.mode === "force") {
      return json(createMissingSearchResponse(webSearch.mode), 200, corsHeaders);
    }

    if (!env.GEMINI_API_KEY) {
      return json(normalizeWorkerResponse({
        reply: "当前云端还未配置 GEMINI_API_KEY，因此只能处理本地确定性命令，暂时无法调用 Gemini。",
        action: "chat",
        records: [],
        mobileCommand: null,
      }, now, "gemini_fallback"), 200, corsHeaders);
    }

    const systemInstruction = buildSystemInstruction({ now, commandProtocol, webSearch });
    const userContext = buildUserContext({
      now,
      conversation,
      ledgerContext,
      pendingDraft,
      clientTools,
      navigationContext,
      commandProtocol,
      webSearch,
    });

    let raw;
    try {
      raw = await callGemini(env, systemInstruction, userContext);
    } catch (error) {
      return json({
        reply: "Gemini 暂时不可用，请稍后再试。",
        action: "chat",
        records: [],
        mobileCommand: null,
        source: "gemini_fallback",
        error: String(error?.message || error).slice(0, 300),
        version: WORKER_VERSION,
      }, 502, corsHeaders);
    }

    const parsed = parseJsonObject(raw);
    if (!parsed) {
      return json({
        reply: String(raw || "").trim().slice(0, 1200) || "Gemini 没有返回可读内容。",
        action: "chat",
        records: [],
        mobileCommand: null,
        source: "gemini_fallback",
        version: WORKER_VERSION,
      }, 200, corsHeaders);
    }

    return json(normalizeWorkerResponse(parsed, now, "gemini_structured"), 200, corsHeaders);
  },
};

function buildSystemInstruction({ now, commandProtocol, webSearch }) {
  return [
    "你是一个 AI 助手。你的回答必须优先判断用户是否在请求本地动作。",
    "如果用户请求执行、修改或保存本地动作，不能只给自然语言回复，必须返回结构化 JSON。",
    "返回格式必须是 JSON object，不要 Markdown，不要代码块，不要纯字符串。",
    "",
    "标准返回格式：",
    JSON.stringify({
      reply: "给用户看的简短回复",
      action: "chat | draft | mobile_command",
      records: [],
      mobileCommand: {
        intent: "navigation.start | navigation.modify | navigation.preference.set | alarm.set | app.open | ledger.create",
        target: "last_navigation | none",
        params: {},
        updates: {},
      },
      source: "gemini_structured",
    }, null, 2),
    "",
    `今天日期是 ${now}。date 必须使用 YYYY-MM-DD。`,
    `允许的 action: ${ALLOWED_ACTIONS.join(", ")}。`,
    `允许的 intent: ${commandProtocol.allowedIntents.join(", ")}。`,
    "",
    "导航相关规则：",
    "1. 用户说“导航回家 / 去学校 / 去公司 / 去宿舍”，返回 action=mobile_command，mobileCommand.intent=navigation.start，params.destinationAlias=家/学校/公司/宿舍，params.mode=driving，reply 提醒确认后再执行。",
    "2. mode 只能是 driving、walking、riding、transit。公交、地铁、公共交通都用 transit；步行用 walking；骑行、骑车用 riding；默认 driving。",
    "3. mapProvider 只能是 baidu、amap。高德用 amap，百度用 baidu。",
    "4. 用户说“改成公交 / 换成步行 / 改为坐公交回去 / 换高德 / 换成百度 / 避开高速 / 少步行”，这是修改上一条导航，返回 intent=navigation.modify，target=last_navigation，不要只回复文字。",
    "5. 修改导航时，公交或地铁写 updates.mode=transit；步行写 walking；骑行写 riding；开车写 driving；换高德写 updates.mapProvider=amap；换百度写 updates.mapProvider=baidu；避开高速写 updates.routeOptions.avoidHighway=true；少步行写 updates.routeOptions.preferLessWalk=true。",
    "6. 用户说“家就是重庆大学 / 把家设为重庆大学 / 以后回家就是重庆大学”，返回 intent=navigation.preference.set，updates.places.home=重庆大学。学校、公司、宿舍同理，对应 places.school、places.work、places.dorm。",
    "7. 用户说“以后默认用高德地图”，返回 updates.mapProvider=amap；用户说“以后默认公交 / 以后导航默认坐地铁”，返回 updates.defaultMode=transit。",
    "",
    "其他本地动作：",
    "1. 设置闹钟、提醒某个具体时间，返回 intent=alarm.set，params 包含 date、hour、minute、label。",
    "2. 打开 App，返回 intent=app.open，params.appName 为应用名。",
    "3. 真实收支记账返回 action=draft，records 包含 title、amount、type、category、date，mobileCommand=null。不要直接保存。",
    "4. 普通聊天返回 action=chat，records=[]，mobileCommand=null。",
    "",
    "联网搜索规则：",
    "1. webSearch.mode=force 时必须联网搜索或调用可用搜索能力后回答。",
    "2. 当前 Worker 没有接入真实搜索源；如果需要强制搜索，必须明确说明“当前云端未接入真实搜索源”，不能假装搜索过。",
    "3. webSearch.mode=auto 时，天气、新闻、最新资料、近期事件、价格、政策、比赛赛程等时效性问题可以搜索；普通聊天、本地动作、记账、导航不要强制搜索。",
    `当前 webSearch.mode=${webSearch.mode}。`,
    "",
    "最终只返回合法 JSON object。",
  ].join("\n");
}

function buildUserContext({ now, conversation, ledgerContext, pendingDraft, clientTools, navigationContext, commandProtocol, webSearch }) {
  return [
    `今天日期：${now}`,
    `待确认账单：${pendingDraft.length ? JSON.stringify(pendingDraft) : "无"}`,
    `账本上下文：${JSON.stringify(ledgerContext)}`,
    `客户端工具：${clientTools.length ? JSON.stringify(clientTools) : "未上报"}`,
    `导航上下文：${JSON.stringify(navigationContext)}`,
    `命令协议：${JSON.stringify(commandProtocol)}`,
    `联网搜索设置：${JSON.stringify(webSearch)}`,
    `对话历史：\n${conversation}`,
    "请根据最后一条用户消息返回标准 JSON。",
  ].join("\n\n");
}

async function callGemini(env, systemInstruction, userContext) {
  const model = String(env.GEMINI_MODEL || DEFAULT_GEMINI_MODEL).replace(/^models\//, "");
  const endpoint = `${env.GEMINI_API_BASE || "https://generativelanguage.googleapis.com/v1beta/models"}/${model}:generateContent?key=${encodeURIComponent(env.GEMINI_API_KEY)}`;
  const response = await fetch(endpoint, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      systemInstruction: {
        parts: [{ text: systemInstruction }],
      },
      contents: [
        {
          role: "user",
          parts: [{ text: userContext }],
        },
      ],
      generationConfig: {
        temperature: 0.1,
        maxOutputTokens: 1200,
        responseMimeType: "application/json",
      },
    }),
  });

  const data = await response.json().catch(() => null);
  if (!response.ok) {
    const message = data?.error?.message || `Gemini HTTP ${response.status}`;
    throw new Error(message);
  }

  return data?.candidates?.[0]?.content?.parts
    ?.map((part) => part.text || "")
    .join("")
    .trim() || "";
}

function tryDeterministicReply({ lastUserText, pendingDraft, ledgerContext, now, commandProtocol }) {
  const text = String(lastUserText || "").trim();
  if (!text) return null;

  const preferenceCommand = parseNavigationPreferenceCommand(text, commandProtocol);
  if (preferenceCommand) return preferenceCommand;

  const navigationModifyCommand = parseNavigationModifyCommand(text, commandProtocol);
  if (navigationModifyCommand) return navigationModifyCommand;

  const navigationStartCommand = parseNavigationStartCommand(text, commandProtocol);
  if (navigationStartCommand) return navigationStartCommand;

  const alarmCommand = parseAlarmCommand(text, now, commandProtocol);
  if (alarmCommand) return alarmCommand;

  const appCommand = parseOpenAppCommand(text, commandProtocol);
  if (appCommand) return appCommand;

  if (pendingDraft.length && /^(好|好的|对|确认|记上|保存|就这样)$/u.test(text)) {
    return { reply: "好的，已确认这笔待保存账单。", action: "chat", records: [], mobileCommand: null };
  }

  if (pendingDraft.length && /^(算了|不用了|先别记|取消)$/u.test(text)) {
    return { reply: "好的，这次先不保存。", action: "chat", records: [], mobileCommand: null };
  }

  const records = parseSimpleRecords(text, now);
  if (records.length) {
    return {
      reply: `我整理出 ${records.length} 笔待确认账单，你确认后我再保存。`,
      action: "draft",
      records,
      mobileCommand: null,
    };
  }

  const categoryQuery = extractCategoryQuery(text);
  if (categoryQuery) {
    const total = sumCategoryThisMonth(ledgerContext.recentRecords, categoryQuery, now);
    return {
      reply: `你这个月${categoryQuery}一共花了 ¥${total.toFixed(2)}。`,
      action: "chat",
      records: [],
      mobileCommand: null,
    };
  }

  return null;
}

function parseNavigationStartCommand(text, commandProtocol) {
  if (!isIntentAllowed(commandProtocol, "navigation.start")) return null;
  const alias = extractDestinationAlias(text);
  if (!alias) return null;
  if (!/(导航|去|到|回|带我|路线|怎么走)/u.test(text)) return null;

  const params = {
    destinationAlias: alias,
    mode: inferNavigationMode(text),
  };
  const mapProvider = inferMapProvider(text);
  if (mapProvider) params.mapProvider = mapProvider;

  return {
    reply: "我理解为要导航到对应常用地点，确认后再执行。",
    action: "mobile_command",
    records: [],
    mobileCommand: {
      intent: "navigation.start",
      target: "none",
      params,
      updates: {},
    },
  };
}

function parseNavigationModifyCommand(text, commandProtocol) {
  if (!isIntentAllowed(commandProtocol, "navigation.modify")) return null;
  const updates = {};
  const routeOptions = {};

  if (/(公交|地铁|公共交通)/u.test(text) && /(改|换|变|坐|路线|回去)/u.test(text)) updates.mode = "transit";
  else if (/(步行|走路)/u.test(text) && /(改|换|变|路线|回去)/u.test(text)) updates.mode = "walking";
  else if (/(骑行|骑车|单车)/u.test(text) && /(改|换|变|路线|回去)/u.test(text)) updates.mode = "riding";
  else if (/(开车|驾车|自驾)/u.test(text) && /(改|换|变|路线|回去)/u.test(text)) updates.mode = "driving";

  if (/(换|改|用).*(高德|高德地图)|^(高德|换高德|换成高德)$/u.test(text)) updates.mapProvider = "amap";
  if (/(换|改|用).*(百度|百度地图)|^(百度|换百度|换成百度)$/u.test(text)) updates.mapProvider = "baidu";
  if (/避开高速|不走高速|躲开高速/u.test(text)) routeOptions.avoidHighway = true;
  if (/少步行|少走路|步行少/u.test(text)) routeOptions.preferLessWalk = true;

  if (Object.keys(routeOptions).length) updates.routeOptions = routeOptions;
  if (!Object.keys(updates).length) return null;

  const reply = updates.mapProvider === "amap"
    ? "我理解为把上一条导航换成高德地图，确认后再执行。"
    : updates.mapProvider === "baidu"
      ? "我理解为把上一条导航换成百度地图，确认后再执行。"
      : updates.mode === "transit"
        ? "我理解为把上一条导航改成公交/地铁路线，确认后再执行。"
        : "我理解为修改上一条导航设置，确认后再执行。";

  return {
    reply,
    action: "mobile_command",
    records: [],
    mobileCommand: {
      intent: "navigation.modify",
      target: "last_navigation",
      params: {},
      updates,
    },
  };
}

function parseNavigationPreferenceCommand(text, commandProtocol) {
  if (!isIntentAllowed(commandProtocol, "navigation.preference.set")) return null;

  const place = parsePlacePreference(text);
  if (place) {
    return {
      reply: place.key === "home" ? "我整理好了家庭地址，确认后保存到手机偏好。" : "我整理好了常用地点，确认后保存到手机偏好。",
      action: "mobile_command",
      records: [],
      mobileCommand: {
        intent: "navigation.preference.set",
        target: "none",
        params: {},
        updates: { places: { [place.key]: place.value } },
      },
    };
  }

  if (/以后.*默认.*(高德|高德地图)|默认.*用.*(高德|高德地图)/u.test(text)) {
    return createPreferenceCommand("我整理好了默认地图偏好，确认后保存。", { mapProvider: "amap" });
  }
  if (/以后.*默认.*(百度|百度地图)|默认.*用.*(百度|百度地图)/u.test(text)) {
    return createPreferenceCommand("我整理好了默认地图偏好，确认后保存。", { mapProvider: "baidu" });
  }

  const defaultMode = parseDefaultModePreference(text);
  if (defaultMode) {
    return createPreferenceCommand("我整理好了默认出行偏好，确认后保存。", { defaultMode });
  }

  return null;
}

function parsePlacePreference(text) {
  const aliasPattern = "(家|学校|公司|宿舍)";
  const direct = text.match(new RegExp(`^${aliasPattern}\\s*(?:就是|是|设为|设置为|改成|改为)\\s*(.+)$`, "u"));
  const withBa = text.match(new RegExp(`^把\\s*${aliasPattern}\\s*(?:设为|设置为|改成|改为)\\s*(.+)$`, "u"));
  const futureHome = text.match(/^以后\s*回家\s*(?:就是|是|去)\s*(.+)$/u);
  const match = direct || withBa || (futureHome ? ["", "家", futureHome[1]] : null);
  if (!match) return null;

  const key = placeAliasToKey(match[1]);
  const value = cleanPreferenceValue(match[2]);
  if (!key || !value) return null;
  return { key, value };
}

function parseDefaultModePreference(text) {
  if (!/以后|默认/u.test(text)) return null;
  if (/公交|地铁|公共交通/u.test(text)) return "transit";
  if (/步行|走路/u.test(text)) return "walking";
  if (/骑行|骑车|单车/u.test(text)) return "riding";
  if (/开车|驾车|自驾/u.test(text)) return "driving";
  return null;
}

function createPreferenceCommand(reply, updates) {
  return {
    reply,
    action: "mobile_command",
    records: [],
    mobileCommand: {
      intent: "navigation.preference.set",
      target: "none",
      params: {},
      updates,
    },
  };
}

function parseAlarmCommand(text, now, commandProtocol) {
  if (!isIntentAllowed(commandProtocol, "alarm.set")) return null;
  if (!/(闹钟|叫我|提醒我|提醒一下|叫醒|起床)/u.test(text)) return null;

  const timeMatch = text.match(/(\d{1,2})(?:[:：点时](\d{1,2})?分?)?/u);
  if (!timeMatch) return null;

  let hour = Number(timeMatch[1]);
  const minute = Number(timeMatch[2] || 0);
  if (!Number.isInteger(hour) || !Number.isInteger(minute) || minute < 0 || minute > 59) return null;
  hour = normalizeMeridiem(hour, text);
  if (hour < 0 || hour > 23) return null;

  const date = /后天/u.test(text) ? shiftDate(now, 2) : /明天|明早|明晚/u.test(text) ? shiftDate(now, 1) : now;
  const label = text
    .replace(/(今天|明天|后天|今晚|明早|明晚|上午|下午|晚上|早上|凌晨|中午)/gu, "")
    .replace(/\d{1,2}(?:[:：点时]\d{0,2})?分?/gu, "")
    .replace(/^(帮我|给我|设|设置|一个|闹钟|提醒我|叫我|叫醒)/gu, "")
    .trim() || (/起床|叫醒/u.test(text) ? "起床" : "提醒");

  return {
    reply: `我理解为要设置“${label}”闹钟，确认后再执行。`,
    action: "mobile_command",
    records: [],
    mobileCommand: {
      intent: "alarm.set",
      target: "none",
      params: { date, hour, minute, label },
      updates: {},
    },
  };
}

function parseOpenAppCommand(text, commandProtocol) {
  if (!isIntentAllowed(commandProtocol, "app.open")) return null;
  const match = text.match(/(?:打开|启动|帮我打开)\s*([\u4e00-\u9fa5A-Za-z0-9]+)$/u);
  if (!match) return null;
  const appName = match[1].trim();
  if (!appName || /(闹钟|提醒|记账)/u.test(appName)) return null;
  return {
    reply: `我理解为要打开“${appName}”，确认后再执行。`,
    action: "mobile_command",
    records: [],
    mobileCommand: {
      intent: "app.open",
      target: "none",
      params: { appName },
      updates: {},
    },
  };
}

function parseSimpleRecords(text, now) {
  if (/(导航|闹钟|打开|提醒我|叫我|默认|设为|就是)/u.test(text)) return [];
  if (/^\d+(?:\.\d+)?$/u.test(text)) return [];

  const parts = text.split(/[，,。；;\n]/u).map((item) => item.trim()).filter(Boolean);
  if (!parts.length || parts.length > 10) return [];

  const records = parts.map((part) => {
    const amountMatch = part.match(/(\d+(?:\.\d+)?)/u);
    if (!amountMatch) return null;
    const amount = Number(amountMatch[1]);
    if (!Number.isFinite(amount) || amount <= 0) return null;
    return {
      title: cleanTitle(part),
      amount,
      type: inferType(part),
      category: inferCategory(part),
      date: /前天/u.test(part) ? shiftDate(now, -2) : /昨天/u.test(part) ? shiftDate(now, -1) : now,
    };
  }).filter(Boolean);

  return records.length === parts.length ? records : [];
}

function normalizeWorkerResponse(payload, fallbackDate, source) {
  const records = sanitizeRecords(payload?.records, fallbackDate);
  const mobileCommand = sanitizeMobileCommand(payload?.mobileCommand || payload?.command, fallbackDate);
  const action = sanitizeAction(payload?.action, records, mobileCommand);
  return {
    reply: sanitizeReply(payload?.reply, action, records, mobileCommand),
    action,
    records: action === "draft" ? records : [],
    mobileCommand: action === "mobile_command" ? mobileCommand : null,
    source: source || payload?.source || "gemini_structured",
    version: WORKER_VERSION,
  };
}

function sanitizeAction(action, records, mobileCommand) {
  if (action === "mobile_command" && mobileCommand) return "mobile_command";
  if (action === "draft" && records.length) return "draft";
  if (ALLOWED_ACTIONS.includes(action)) return action === "mobile_command" ? "chat" : action;
  if (mobileCommand) return "mobile_command";
  if (records.length) return "draft";
  return "chat";
}

function sanitizeReply(reply, action, records, mobileCommand) {
  const text = String(reply || "").trim();
  if (text) return text.slice(0, 500);
  if (action === "mobile_command" && mobileCommand) return createMobileReply(mobileCommand);
  if (action === "draft" && records.length) return `我整理出 ${records.length} 笔待确认账单，你确认后我再保存。`;
  return "我在，直接和我说就行。";
}

function sanitizeMobileCommand(command, fallbackDate) {
  if (!command || typeof command !== "object") return null;
  const legacyIntent = mapLegacyIntent(command.type || command.commandType);
  const intent = String(command.intent || legacyIntent || "").trim();
  if (!ALLOWED_INTENTS.includes(intent)) return null;

  const params = plainObject(command.params);
  const updates = plainObject(command.updates);
  const target = String(command.target || (intent === "navigation.modify" ? "last_navigation" : "none")).trim() || "none";

  if (intent === "navigation.start") {
    const destinationAlias = cleanText(params.destinationAlias || params.destination || command.destinationAlias, 40);
    const destination = cleanText(params.destination, 120);
    if (!destinationAlias && !destination) return null;
    const cleanParams = {
      ...(destinationAlias ? { destinationAlias } : {}),
      ...(destination ? { destination } : {}),
      mode: NAV_MODES.includes(params.mode) ? params.mode : "driving",
    };
    if (MAP_PROVIDERS.includes(params.mapProvider)) cleanParams.mapProvider = params.mapProvider;
    return { intent, target: "none", params: cleanParams, updates: {} };
  }

  if (intent === "navigation.modify") {
    const cleanUpdates = sanitizeNavigationUpdates(updates);
    if (!Object.keys(cleanUpdates).length) return null;
    return { intent, target: "last_navigation", params: {}, updates: cleanUpdates };
  }

  if (intent === "navigation.preference.set") {
    const cleanUpdates = sanitizePreferenceUpdates(updates);
    if (!Object.keys(cleanUpdates).length) return null;
    return { intent, target: "none", params: {}, updates: cleanUpdates };
  }

  if (intent === "alarm.set") {
    const hour = Number(params.hour);
    const minute = Number(params.minute || 0);
    if (!Number.isInteger(hour) || !Number.isInteger(minute) || hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
    return {
      intent,
      target: "none",
      params: {
        date: normalizeIsoDate(params.date) || fallbackDate,
        hour,
        minute,
        label: cleanText(params.label || "提醒", 40) || "提醒",
      },
      updates: {},
    };
  }

  if (intent === "app.open") {
    const appName = cleanText(params.appName || command.summary, 40);
    if (!appName) return null;
    const cleanParams = { appName };
    const packageName = cleanText(params.packageName, 100);
    if (packageName) cleanParams.packageName = packageName;
    return { intent, target: "none", params: cleanParams, updates: {} };
  }

  if (intent === "ledger.create") {
    return { intent, target, params, updates };
  }

  return null;
}

function sanitizeNavigationUpdates(updates) {
  const clean = {};
  if (NAV_MODES.includes(updates.mode)) clean.mode = updates.mode;
  if (MAP_PROVIDERS.includes(updates.mapProvider)) clean.mapProvider = updates.mapProvider;
  const routeOptions = plainObject(updates.routeOptions);
  const cleanRouteOptions = {};
  if (typeof routeOptions.avoidHighway === "boolean") cleanRouteOptions.avoidHighway = routeOptions.avoidHighway;
  if (typeof routeOptions.preferLessWalk === "boolean") cleanRouteOptions.preferLessWalk = routeOptions.preferLessWalk;
  if (Object.keys(cleanRouteOptions).length) clean.routeOptions = cleanRouteOptions;
  return clean;
}

function sanitizePreferenceUpdates(updates) {
  const clean = {};
  if (MAP_PROVIDERS.includes(updates.mapProvider)) clean.mapProvider = updates.mapProvider;
  if (NAV_MODES.includes(updates.defaultMode)) clean.defaultMode = updates.defaultMode;

  const places = plainObject(updates.places);
  const cleanPlaces = {};
  for (const key of ["home", "school", "work", "dorm"]) {
    const value = cleanText(places[key], 120);
    if (value) cleanPlaces[key] = value;
  }
  if (Object.keys(cleanPlaces).length) clean.places = cleanPlaces;
  return clean;
}

function createMobileReply(command) {
  if (command.intent === "navigation.start") {
    const destination = command.params.destinationAlias || command.params.destination || "目的地";
    return `我理解为要导航到${destination}，确认后再执行。`;
  }
  if (command.intent === "navigation.modify") return "我理解为修改上一条导航设置，确认后再执行。";
  if (command.intent === "navigation.preference.set") return "我整理好了导航偏好，确认后保存。";
  if (command.intent === "alarm.set") return `我理解为要设置“${command.params.label}”闹钟，确认后再执行。`;
  if (command.intent === "app.open") return `我理解为要打开“${command.params.appName}”，确认后再执行。`;
  return "我整理好了这个本地动作，确认后再执行。";
}

function createMissingSearchResponse(mode) {
  return {
    webSearchUsed: false,
    webSearchMode: mode,
    sources: [],
    citations: [],
    reply: "当前云端还未接入真实搜索源，因此无法完成强制联网搜索。",
    action: "chat",
    records: [],
    mobileCommand: null,
    source: "gemini_structured",
    version: WORKER_VERSION,
  };
}

function parseJsonObject(raw) {
  const text = String(raw || "").trim();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    // Continue with extraction below.
  }

  const stripped = text
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/\s*```$/i, "")
    .trim();
  try {
    return JSON.parse(stripped);
  } catch {
    // Continue with balanced object extraction.
  }

  const jsonText = extractBalancedJsonObject(stripped);
  if (!jsonText) return null;
  try {
    return JSON.parse(jsonText);
  } catch {
    return null;
  }
}

function extractBalancedJsonObject(text) {
  const start = text.indexOf("{");
  if (start < 0) return "";

  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let i = start; i < text.length; i += 1) {
    const char = text[i];
    if (inString) {
      if (escaped) escaped = false;
      else if (char === "\\") escaped = true;
      else if (char === "\"") inString = false;
      continue;
    }

    if (char === "\"") inString = true;
    else if (char === "{") depth += 1;
    else if (char === "}") {
      depth -= 1;
      if (depth === 0) return text.slice(start, i + 1);
    }
  }
  return "";
}

function normalizeCommandProtocol(protocol) {
  const safe = protocol && typeof protocol === "object" ? protocol : {};
  const allowed = Array.isArray(safe.allowedIntents)
    ? safe.allowedIntents.filter((intent) => ALLOWED_INTENTS.includes(intent))
    : ALLOWED_INTENTS;
  return {
    enabled: safe.enabled !== false,
    requireStructuredCommandWhenActionable: safe.requireStructuredCommandWhenActionable !== false,
    allowedIntents: allowed.length ? allowed : ALLOWED_INTENTS,
  };
}

function normalizeNavigationContext(context) {
  const safe = context && typeof context === "object" ? context : {};
  return {
    preferences: plainObject(safe.preferences),
    lastNavigation: plainObject(safe.lastNavigation),
  };
}

function normalizeWebSearch(body) {
  const safe = body?.webSearch && typeof body.webSearch === "object" ? body.webSearch : {};
  const mode = body?.forceWebSearch === true
    || safe.force === true
    || safe.mode === "force"
    || body?.webSearchMode === "force"
    || body?.searchMode === "force"
    ? "force"
    : "auto";
  return {
    mode,
    force: mode === "force",
    keepAutoSearchWhenOff: safe.keepAutoSearchWhenOff !== false,
    requireCitationsWhenForced: safe.requireCitationsWhenForced !== false,
  };
}

function isIntentAllowed(commandProtocol, intent) {
  return commandProtocol.enabled !== false && commandProtocol.allowedIntents.includes(intent);
}

function normalizeMessages(messages) {
  if (!Array.isArray(messages)) return [];
  return messages
    .slice(-12)
    .map((message) => ({
      role: message?.role === "assistant" ? "assistant" : "user",
      content: String(message?.content || "").trim().slice(0, 800),
    }))
    .filter((message) => message.content);
}

function buildConversation(messages) {
  return messages
    .map((message) => `${message.role === "assistant" ? "助手" : "用户"}：${message.content}`)
    .join("\n");
}

function getLastUserText(messages, fallbackText) {
  const last = [...messages].reverse().find((message) => message.role === "user");
  return String(last?.content || fallbackText || "").trim();
}

function sanitizeRecords(records, fallbackDate) {
  if (!Array.isArray(records)) return [];
  return records
    .slice(0, 10)
    .map((record) => ({
      title: cleanText(record?.title || "未命名账单", 30) || "未命名账单",
      amount: Number(record?.amount),
      type: record?.type === "income" ? "income" : "expense",
      category: ALLOWED_CATEGORIES.includes(record?.category) ? record.category : "其他",
      date: normalizeIsoDate(record?.date) || fallbackDate,
    }))
    .filter((record) => Number.isFinite(record.amount) && record.amount > 0);
}

function sanitizeLedgerContext(context, fallbackDate) {
  const safe = context && typeof context === "object" ? context : {};
  const summary = safe.summary && typeof safe.summary === "object" ? safe.summary : {};
  return {
    today: fallbackDate,
    summary: {
      todayExpense: Number(summary.todayExpense) || 0,
      monthIncome: Number(summary.monthIncome) || 0,
      monthExpense: Number(summary.monthExpense) || 0,
      monthBalance: Number(summary.monthBalance) || 0,
    },
    recentRecords: sanitizeRecords(safe.recentRecords, fallbackDate).slice(0, 80),
  };
}

function sanitizeClientTools(tools) {
  if (!Array.isArray(tools)) return [];
  return tools
    .slice(0, 20)
    .map((tool) => ({
      name: cleanText(tool?.name, 60),
      action: cleanText(tool?.action, 40),
      commandType: cleanText(tool?.commandType, 40),
      title: cleanText(tool?.title, 60),
    }))
    .filter((tool) => tool.name || tool.action || tool.commandType);
}

function normalizeIsoDate(value) {
  const text = String(value || "");
  return /^\d{4}-\d{2}-\d{2}$/.test(text) ? text : null;
}

function getCorsHeaders(request, env) {
  const origin = request.headers.get("Origin") || "";
  const allowed = String(env.ALLOWED_ORIGINS || "*")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);

  const allowOrigin = allowed.includes("*") || allowed.includes(origin)
    ? origin || "*"
    : allowed[0] || "*";

  return {
    ...jsonHeaders,
    "access-control-allow-origin": allowOrigin,
    "access-control-allow-methods": "GET, POST, OPTIONS",
    "access-control-allow-headers": "content-type",
    "vary": "Origin",
  };
}

function json(payload, status = 200, corsHeaders = {}) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { ...jsonHeaders, ...corsHeaders },
  });
}

function extractDestinationAlias(text) {
  if (/回家|到家|去家|导航回家|家里|我家/u.test(text)) return "家";
  if (/去学校|到学校|回学校|导航.*学校/u.test(text)) return "学校";
  if (/去公司|到公司|回公司|导航.*公司|去上班/u.test(text)) return "公司";
  if (/去宿舍|到宿舍|回宿舍|导航.*宿舍/u.test(text)) return "宿舍";
  return "";
}

function inferNavigationMode(text) {
  if (/公交|地铁|公共交通/u.test(text)) return "transit";
  if (/步行|走路/u.test(text)) return "walking";
  if (/骑行|骑车|单车/u.test(text)) return "riding";
  return "driving";
}

function inferMapProvider(text) {
  if (/高德|高德地图/u.test(text)) return "amap";
  if (/百度|百度地图/u.test(text)) return "baidu";
  return "";
}

function placeAliasToKey(alias) {
  return {
    家: "home",
    学校: "school",
    公司: "work",
    宿舍: "dorm",
  }[alias] || "";
}

function cleanPreferenceValue(value) {
  return cleanText(value, 120)
    .replace(/^(就是|是|为|成)/u, "")
    .replace(/[。！!，,]$/u, "")
    .trim();
}

function normalizeMeridiem(hour, text) {
  if (/下午|晚上|傍晚|今晚/u.test(text) && hour < 12) return hour + 12;
  if (/中午/u.test(text) && hour < 11) return hour + 12;
  if (/凌晨|早上|上午|明早|明天早上/u.test(text) && hour === 12) return 0;
  return hour;
}

function mapLegacyIntent(type) {
  return {
    navigate: "navigation.start",
    set_alarm: "alarm.set",
    open_app: "app.open",
  }[String(type || "")] || "";
}

function plainObject(value) {
  return value && typeof value === "object" && !Array.isArray(value) ? value : {};
}

function cleanText(value, maxLength) {
  return String(value || "").trim().slice(0, maxLength);
}

function shiftDate(isoDate, offsetDays) {
  const date = new Date(`${isoDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + offsetDays);
  return date.toISOString().slice(0, 10);
}

function inferCategory(text) {
  const t = String(text || "").toLowerCase();
  if (/(餐|早饭|早餐|午饭|午餐|晚饭|晚餐|外卖|面|米线|火锅|烧烤|饭)/u.test(t)) return "餐饮";
  if (/(奶茶|咖啡|饮料|可乐|茶)/u.test(t)) return "饮品";
  if (/(打车|出租|公交|地铁|高铁|火车|机票|加油)/u.test(t)) return "交通";
  if (/(淘宝|京东|拼多多|买|衣服|鞋|超市|购物)/u.test(t)) return "购物";
  if (/(房租|水电|物业|宿舍|宽带)/u.test(t)) return "居住";
  if (/(工资|兼职|奖金|补贴|报销|收入)/u.test(t)) return "工资";
  if (/(礼物|红包)/u.test(t)) return "礼物";
  return "其他";
}

function inferType(text) {
  return /(收入|工资|兼职|奖金|补贴|报销|收到|进账)/u.test(String(text || "")) ? "income" : "expense";
}

function cleanTitle(text) {
  return String(text || "")
    .replace(/今天|昨天|前天|花了|花费|消费|支出|收入|进账|收到|元|块钱|块/gu, "")
    .replace(/[0-9.]/gu, "")
    .replace(/[，,。；;、]/gu, "")
    .trim() || "未命名账单";
}

function extractCategoryQuery(text) {
  const match = text.match(/这个月(餐饮|交通|购物|居住|饮品|工资|礼物|其他)(?:花了多少|支出多少|多少钱)/u);
  return match?.[1] || null;
}

function sumCategoryThisMonth(records, category, now) {
  const prefix = String(now).slice(0, 7);
  return (Array.isArray(records) ? records : [])
    .filter((record) => record.type === "expense" && record.category === category && String(record.date).startsWith(prefix))
    .reduce((sum, record) => sum + Number(record.amount || 0), 0);
}

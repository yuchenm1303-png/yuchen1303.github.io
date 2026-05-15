const ALLOWED_CATEGORIES = ["餐饮", "交通", "购物", "居住", "饮品", "工资", "礼物", "其他"];
const ALLOWED_ACTIONS = ["chat", "draft", "confirm_pending", "cancel_pending", "mobile_command"];
const ALLOWED_MOBILE_ACTIONS = ["set_alarm", "open_app", "navigate"];
const WORKER_VERSION = "2026-05-15-json-repair-2";

const TOOL_REGISTRY = [
  {
    name: "ledger.draft_records",
    action: "draft",
    title: "整理待确认账单",
    description: "当用户表达真实收支时，把内容整理成待确认账单；缺金额等关键信息时先追问。",
  },
  {
    name: "mobile.set_alarm",
    action: "mobile_command",
    commandType: "set_alarm",
    title: "设置系统闹钟",
    description: "当用户要求叫醒、闹钟、提醒某个具体时间时使用。",
  },
  {
    name: "mobile.open_app",
    action: "mobile_command",
    commandType: "open_app",
    title: "打开手机应用",
    description: "当用户要求打开微信、支付宝、淘宝、QQ、百度地图等应用时使用。",
  },
  {
    name: "mobile.navigate",
    action: "mobile_command",
    commandType: "navigate",
    title: "地图导航",
    description: "当用户要求导航、回家、去某地怎么走、带我去某地时使用。",
  },
];

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
      return json({
        ok: true,
        worker: "ai-ledger-parser",
        version: WORKER_VERSION,
        provider: "workers_ai",
        mode: "hybrid_rules_plus_ai_json_repair",
        agentMode: "general_chat_plus_tool_registry",
        tools: TOOL_REGISTRY,
        model: env.AI_MODEL || "@cf/meta/llama-3.1-8b-instruct",
        hasAiBinding: Boolean(env.AI),
      }, 200, corsHeaders);
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
    const pendingDraft = sanitizeRecords(body?.pendingDraft, now);
    const ledgerContext = sanitizeLedgerContext(body?.ledgerContext, now);
    const clientTools = sanitizeClientTools(body?.clientTools);
    const lastUserText = getLastUserText(messages, text);
    const conversation = messages.length ? buildConversation(messages) : lastUserText;

    if (!conversation) {
      return json({ error: "messages or text is required", code: "missing_conversation", version: WORKER_VERSION }, 400, corsHeaders);
    }

    if (conversation.length > 5000) {
      return json({ error: "conversation is too long", code: "conversation_too_long", version: WORKER_VERSION }, 400, corsHeaders);
    }

    const deterministic = tryDeterministicReply({ lastUserText, pendingDraft, ledgerContext, now });
    if (deterministic) {
      return json({ ...deterministic, source: "hybrid_rules", version: WORKER_VERSION }, 200, corsHeaders);
    }

    if (!env.AI) {
      return json({
        reply: "云端 AI 还没有绑定成功。我现在只能处理记账、闹钟、打开应用和导航等本地规则任务。",
        action: "chat",
        records: [],
        mobileCommand: null,
        source: "missing_workers_ai_binding",
        version: WORKER_VERSION,
      }, 200, corsHeaders);
    }

    const schema = buildResponseSchema();
    const instructions = buildInstructions(now);
    const context = [
      `今天日期：${now}`,
      `待确认账单：${pendingDraft.length ? JSON.stringify(pendingDraft) : "无"}`,
      `账本上下文：${JSON.stringify(ledgerContext)}`,
      `客户端可执行工具：${clientTools.length ? JSON.stringify(clientTools) : "未上报，按默认 Tool Registry 处理"}`,
      `对话历史：\n${conversation}`,
      "请根据最后一条用户消息作答。",
    ].join("\n\n");

    let aiResult;
    try {
      aiResult = await env.AI.run(env.AI_MODEL || "@cf/meta/llama-3.1-8b-instruct", {
        messages: [
          { role: "system", content: instructions },
          { role: "user", content: context },
        ],
        temperature: 0.15,
        max_tokens: 700,
        response_format: {
          type: "json_schema",
          json_schema: schema,
        },
      });
    } catch (error) {
      return json({
        error: "Workers AI provider error",
        code: "workers_ai_error",
        providerMessage: String(error?.message || error),
        version: WORKER_VERSION,
      }, 502, corsHeaders);
    }

    const raw = extractWorkersAiText(aiResult);
    const parsed = parseAiJson(raw);

    if (!parsed) {
      const textReply = cleanRawAiText(raw) || createSafeFallbackReply(lastUserText);
      return json({
        reply: textReply,
        action: "chat",
        records: [],
        mobileCommand: null,
        source: "workers_ai_text_fallback",
        version: WORKER_VERSION,
        repaired: true,
      }, 200, corsHeaders);
    }

    const records = sanitizeRecords(parsed.records, now);
    const mobileCommand = sanitizeMobileCommand(parsed.mobileCommand, now);
    const action = sanitizeAction(parsed.action, records, pendingDraft, mobileCommand);
    const reply = sanitizeReply(parsed.reply, action, records, mobileCommand);

    return json({
      reply,
      action,
      records,
      mobileCommand,
      source: "workers_ai",
      version: WORKER_VERSION,
    }, 200, corsHeaders);
  },
};

function getCorsHeaders(request, env) {
  const origin = request.headers.get("Origin") || "";
  const allowed = String(env.ALLOWED_ORIGINS || "*")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
  const allowOrigin = allowed.includes("*") || allowed.includes(origin) ? origin || "*" : allowed[0] || "*";
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

function buildResponseSchema() {
  return {
    type: "object",
    additionalProperties: false,
    properties: {
      reply: { type: "string" },
      action: { type: "string", enum: ALLOWED_ACTIONS },
      mobileCommand: {
        type: "object",
        additionalProperties: false,
        properties: {
          type: { type: "string", enum: ALLOWED_MOBILE_ACTIONS },
          title: { type: "string" },
          summary: { type: "string" },
          params: {
            type: "object",
            additionalProperties: false,
            properties: {
              date: { type: "string" },
              hour: { type: "number" },
              minute: { type: "number" },
              label: { type: "string" },
              appName: { type: "string" },
              packageName: { type: "string" },
              destination: { type: "string" },
              mode: { type: "string", enum: ["driving", "walking", "riding"] },
            },
          },
        },
        required: ["type", "params"],
      },
      records: {
        type: "array",
        minItems: 0,
        maxItems: 10,
        items: {
          type: "object",
          additionalProperties: false,
          properties: {
            title: { type: "string" },
            amount: { type: "number" },
            type: { type: "string", enum: ["expense", "income"] },
            category: { type: "string", enum: ALLOWED_CATEGORIES },
            date: { type: "string" },
          },
          required: ["title", "amount", "type", "category", "date"],
        },
      },
    },
    required: ["reply", "action", "records"],
  };
}

function buildInstructions(now) {
  return [
    "你是一个中文通用手机 AI 智能体。你能自然聊天、理解上下文，并在合适时调用工具。",
    "普通聊天就正常回答，不要强行记账或调用手机工具。复杂问题也要尽量回答，但保持简洁。",
    "如果用户明显要记账，使用 action=draft 并返回 records；缺金额时先追问。",
    "如果用户明显要设置闹钟、打开应用或导航，使用 action=mobile_command 并返回 mobileCommand。所有手机动作都只是生成确认卡片，不能声称已经执行。",
    "如果用户对待确认账单表示同意，使用 confirm_pending；表示取消，使用 cancel_pending。",
    "账本问答只能依据 ledgerContext 中提供的数据回答；上下文不足时要坦率说明。",
    "只记录用户本人真实承担或真实收到的金额。垫付、AA、总额和个人份额同时出现时，只记录个人实际承担金额。",
    `category 只能从 ${ALLOWED_CATEGORIES.join("、")} 中选择。今天日期是 ${now}。date 必须是 YYYY-MM-DD。`,
    "输出协议必须尽量返回 JSON：{ reply, action, records, mobileCommand }。不要输出 Markdown。",
    "如果你无法严格组织 JSON，也至少直接给出自然语言回答；系统会自动兜底。",
    `Tool Registry: ${JSON.stringify(TOOL_REGISTRY)}`,
  ].join("\n");
}

function getLastUserText(messages, fallbackText) {
  const last = [...messages].reverse().find((message) => message.role === "user");
  return String(last?.content || fallbackText || "").trim();
}

function tryDeterministicReply({ lastUserText, pendingDraft, ledgerContext, now }) {
  const text = String(lastUserText || "").trim();
  if (!text) return null;

  if (pendingDraft.length && /^(好|好的|对|确认|保存|记上|就这样)$/u.test(text)) {
    return { reply: "好的，已帮你记上。", action: "confirm_pending", records: [], mobileCommand: null };
  }

  if (pendingDraft.length && /^(算了|不用了|先别记|取消)$/u.test(text)) {
    return { reply: "好的，这次先不保存。", action: "cancel_pending", records: [], mobileCommand: null };
  }

  if (/^(你好|您好|嗨|哈喽|在吗|hello|hi)$/iu.test(text)) {
    return { reply: "我在。你可以直接说任务，比如记账、设置闹钟、打开应用、导航，也可以正常聊天。", action: "chat", records: [], mobileCommand: null };
  }

  if (/(你有|你会|有哪些).*(功能|能做什么|会干什么)/u.test(text)) {
    return { reply: "我可以聊天、记账、查账本，也能生成设置闹钟、打开应用、地图导航这些手机动作卡片。", action: "chat", records: [], mobileCommand: null };
  }

  const mobileCommand = parseMobileCommand(text, now);
  if (mobileCommand) {
    return { reply: createMobileReply(mobileCommand), action: "mobile_command", records: [], mobileCommand };
  }

  const simpleRecords = parseSimpleRecords(text, now);
  if (simpleRecords.length) {
    return { reply: `我先整理出 ${simpleRecords.length} 笔待确认账单，你回复“好”我就帮你保存。`, action: "draft", records: simpleRecords, mobileCommand: null };
  }

  const requestedItem = extractIncompleteItem(text) || extractStandaloneItem(text);
  if (requestedItem) {
    return { reply: `${requestedItem}花了多少钱？`, action: "chat", records: [], mobileCommand: null };
  }

  const categoryQuery = extractCategoryQuery(text);
  if (categoryQuery) {
    const total = sumCategoryThisMonth(ledgerContext.recentRecords, categoryQuery, now);
    return { reply: `你这个月${categoryQuery}一共花了 ¥${total.toFixed(2)}。`, action: "chat", records: [], mobileCommand: null };
  }

  return null;
}

function parseMobileCommand(text, now) {
  return parseAlarmCommand(text, now) || parseNavigationCommand(text) || parseOpenAppCommand(text);
}

function parseAlarmCommand(text, now) {
  if (!/(闹钟|叫我|提醒我|提醒一下|叫醒|起床)/u.test(text)) return null;
  const timeMatch = text.match(/(\d{1,2})(?:[:：点时](\d{1,2})?分?)?/u);
  if (!timeMatch) return null;

  let hour = Number(timeMatch[1]);
  const minute = Number(timeMatch[2] || 0);
  if (!Number.isInteger(hour) || !Number.isInteger(minute) || minute < 0 || minute > 59) return null;
  hour = normalizeMeridiem(hour, text);
  if (hour < 0 || hour > 23) return null;

  const date = /后天/u.test(text) ? shiftDate(now, 2) : /明天|明早|明晚/u.test(text) ? shiftDate(now, 1) : now;
  const labelMatch = text.match(/(?:提醒我|叫我|叫醒我|闹钟)(.*)$/u);
  const rawLabel = labelMatch?.[1] || "";
  const label = rawLabel
    .replace(/(明天|后天|今天|今晚|明早|明晚|上午|下午|晚上|早上|凌晨|中午)/gu, "")
    .replace(/\d{1,2}(?:[:：点时]\d{0,2})?分?/gu, "")
    .replace(/^(去|要|一下|起床)/u, "")
    .trim() || (/起床|叫醒/u.test(text) ? "起床" : "提醒");

  return {
    type: "set_alarm",
    title: "设置闹钟",
    summary: `${date} ${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`,
    params: { date, hour, minute, label },
  };
}

function normalizeMeridiem(hour, text) {
  if (/下午|晚上|傍晚|今晚/u.test(text) && hour < 12) return hour + 12;
  if (/中午/u.test(text) && hour < 11) return hour + 12;
  if (/凌晨|早上|上午|明早|明天早上/u.test(text) && hour === 12) return 0;
  return hour;
}

function parseOpenAppCommand(text) {
  const match = text.match(/(?:打开|启动|帮我打开)\s*([\u4e00-\u9fa5A-Za-z0-9]+)$/u);
  if (!match) return null;
  const appName = match[1].trim();
  if (!appName || /(闹钟|提醒|记账)/u.test(appName)) return null;
  return { type: "open_app", title: "打开应用", summary: appName, params: { appName } };
}

function parseNavigationCommand(text) {
  if (!/(导航|路线|带我去|回家|到家|怎么走|我想回家)/u.test(text)) return null;
  const destinationMatch = text.match(/(?:导航(?:到|去)?|路线到|带我去|怎么去|怎么到)\s*([\u4e00-\u9fa5A-Za-z0-9·.\- ]+)$/u)
    || text.match(/去\s*([\u4e00-\u9fa5A-Za-z0-9·.\- ]+?)(?:怎么走|路线|导航)$/u);
  let destination = destinationMatch?.[1]?.trim() || "";
  if (/回家|到家|去家|家里|我家|我想回家/u.test(text)) destination = "家";
  destination = destination
    .replace(/^(百度地图|高德地图|地图|帮我|请|给我)/u, "")
    .replace(/(?:怎么走|路线|导航)$/u, "")
    .trim();
  if (!destination || /^(打开|启动)?(百度地图|高德地图|地图)$/u.test(destination)) return null;
  const mode = /步行|走路/u.test(text) ? "walking" : /骑行|骑车|单车/u.test(text) ? "riding" : "driving";
  return { type: "navigate", title: "地图导航", summary: `到 ${destination}`, params: { appName: "地图", destination, mode } };
}

function createMobileReply(command) {
  if (command.type === "set_alarm") return `我理解为要${command.summary}设置“${command.params.label}”闹钟，确认后我再执行。`;
  if (command.type === "navigate") return `我理解为要导航到“${command.params.destination}”，确认后我再执行。`;
  return `我理解为要打开“${command.params.appName}”，确认后我再执行。`;
}

function parseSimpleRecords(text, now) {
  if (/(我付了|自己花|垫付|平摊|AA)/u.test(text)) return [];
  const parts = text.split(/[，,。；;、\n]/).map((item) => item.trim()).filter(Boolean);
  if (!parts.length) return [];
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
      date: /昨天/u.test(part) ? shiftDate(now, -1) : /前天/u.test(part) ? shiftDate(now, -2) : now,
    };
  }).filter(Boolean);
  return records.length === parts.length ? records : [];
}

function extractIncompleteItem(text) {
  if (/\d/u.test(text)) return null;
  const match = text.match(/(?:帮我)?(?:记|记一笔|记个)(?:一下)?\s*([\u4e00-\u9fa5A-Za-z]+)$/u);
  if (!match) return null;
  const item = normalizeItem(match[1]);
  if (!item || /^(点账|账)$/u.test(item)) return null;
  return item;
}

function extractStandaloneItem(text) {
  if (/\d/u.test(text)) return null;
  if (!/^(早餐|午饭|午餐|晚饭|晚餐|奶茶|咖啡|地铁|公交|打车|火锅|外卖)$/u.test(text)) return null;
  return normalizeItem(text);
}

function normalizeItem(text) {
  return String(text || "").replace(/^(一笔|一个|一下)/u, "").replace(/金额$/u, "").trim();
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

function inferCategory(text) {
  const t = String(text || "").toLowerCase();
  if (/(饭|早餐|午餐|晚餐|外卖|面|米线|火锅|烧烤|餐)/u.test(t)) return "餐饮";
  if (/(奶茶|咖啡|饮料|可乐|茶)/u.test(t)) return "饮品";
  if (/(打车|出租|公交|地铁|高铁|火车|机票|加油)/u.test(t)) return "交通";
  if (/(淘宝|京东|拼多多|买|衣服|鞋|超市|购物)/u.test(t)) return "购物";
  if (/(房租|水电|物业|宿舍|宽带)/u.test(t)) return "居住";
  if (/(工资|兼职|奖金|补贴|报销|收入)/u.test(t)) return "工资";
  if (/(礼物|红包)/u.test(t)) return "礼物";
  return "其他";
}

function inferType(text) {
  return /(收入|工资|兼职|奖金|报销|收到|进账)/u.test(String(text || "")) ? "income" : "expense";
}

function cleanTitle(text) {
  return String(text || "")
    .replace(/今天|昨天|前天|花了|花费|消费|支出|收入|进账|收到|元|块钱|块/gu, "")
    .replace(/[0-9.]/gu, "")
    .replace(/[，,。；;、]/gu, "")
    .trim() || "未命名账单";
}

function shiftDate(isoDate, offsetDays) {
  const date = new Date(`${isoDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + offsetDays);
  return date.toISOString().slice(0, 10);
}

function extractWorkersAiText(result) {
  if (typeof result === "string") return result;
  if (typeof result?.response === "string") return result.response;
  if (typeof result?.result?.response === "string") return result.result.response;
  if (typeof result?.output_text === "string") return result.output_text;
  if (Array.isArray(result?.content)) {
    return result.content.map((item) => item?.text || item).join("\n");
  }
  return JSON.stringify(result);
}

function parseAiJson(raw) {
  const text = String(raw || "").trim();
  if (!text) return null;

  const direct = tryJson(text);
  if (direct) return direct;

  const unfenced = text
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/```$/i, "")
    .trim();
  const fromFence = tryJson(unfenced);
  if (fromFence) return fromFence;

  const balanced = extractFirstBalancedObject(unfenced);
  if (balanced) {
    const parsed = tryJson(balanced);
    if (parsed) return parsed;
  }

  return null;
}

function tryJson(text) {
  try {
    const parsed = JSON.parse(text);
    if (parsed && typeof parsed === "object") return parsed;
  } catch {}
  return null;
}

function extractFirstBalancedObject(text) {
  const start = text.indexOf("{");
  if (start < 0) return null;
  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let i = start; i < text.length; i += 1) {
    const ch = text[i];
    if (inString) {
      if (escaped) escaped = false;
      else if (ch === "\\") escaped = true;
      else if (ch === '"') inString = false;
      continue;
    }
    if (ch === '"') inString = true;
    else if (ch === "{") depth += 1;
    else if (ch === "}") {
      depth -= 1;
      if (depth === 0) return text.slice(start, i + 1);
    }
  }
  return null;
}

function cleanRawAiText(raw) {
  const text = String(raw || "")
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/```$/i, "")
    .trim();
  if (!text) return "";
  if (text.startsWith("{") && text.endsWith("}")) return "";
  return text.slice(0, 520);
}

function createSafeFallbackReply(lastUserText) {
  const text = String(lastUserText || "").trim();
  if (!text) return "我在，你可以继续说。";
  return "这个问题我可以继续聊，但刚才云端模型返回格式不稳定。我先按普通聊天处理：你可以换一种说法，或者把问题拆成更短的一句。";
}

function normalizeIsoDate(value) {
  const text = String(value || "");
  return /^\d{4}-\d{2}-\d{2}$/.test(text) ? text : null;
}

function normalizeMessages(messages) {
  if (!Array.isArray(messages)) return [];
  return messages
    .slice(-10)
    .map((message) => ({
      role: message?.role === "assistant" ? "assistant" : "user",
      content: String(message?.content || "").trim().slice(0, 400),
    }))
    .filter((message) => message.content);
}

function buildConversation(messages) {
  return messages.map((message) => `${message.role === "assistant" ? "助手" : "用户"}：${message.content}`).join("\n");
}

function sanitizeRecords(records, fallbackDate) {
  if (!Array.isArray(records)) return [];
  return records
    .slice(0, 10)
    .map((record) => ({
      title: String(record?.title || "未命名账单").trim().slice(0, 30) || "未命名账单",
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
    recentRecords: sanitizeRecords(safe.recentRecords, fallbackDate).slice(0, 60),
  };
}

function sanitizeClientTools(tools) {
  if (!Array.isArray(tools)) return [];
  return tools
    .slice(0, 12)
    .map((tool) => ({
      name: String(tool?.name || "").trim().slice(0, 50),
      action: String(tool?.action || "").trim().slice(0, 40),
      commandType: String(tool?.commandType || "").trim().slice(0, 40),
      title: String(tool?.title || "").trim().slice(0, 40),
    }))
    .filter((tool) => tool.name && tool.action);
}

function sanitizeMobileCommand(command, fallbackDate) {
  if (!command || typeof command !== "object") return null;
  const type = String(command.type || "").trim();
  const params = command.params && typeof command.params === "object" ? command.params : {};

  if (type === "set_alarm") {
    const hour = Number(params.hour);
    const minute = Number(params.minute || 0);
    if (!Number.isInteger(hour) || !Number.isInteger(minute) || hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
    const date = normalizeIsoDate(params.date) || fallbackDate;
    const label = String(params.label || "提醒").trim().slice(0, 30) || "提醒";
    return { type, title: "设置闹钟", summary: `${date} ${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`, params: { date, hour, minute, label } };
  }

  if (type === "open_app") {
    const appName = String(params.appName || command.summary || "").trim().slice(0, 30);
    if (!appName) return null;
    return { type, title: "打开应用", summary: appName, params: { appName, packageName: String(params.packageName || "").trim().slice(0, 80) } };
  }

  if (type === "navigate") {
    const destination = String(params.destination || command.summary || "").replace(/^到\s*/, "").trim().slice(0, 80);
    if (!destination) return null;
    const mode = ["driving", "walking", "riding"].includes(params.mode) ? params.mode : "driving";
    return { type, title: "地图导航", summary: `到 ${destination}`, params: { appName: String(params.appName || "地图"), destination, mode } };
  }

  return null;
}

function sanitizeAction(action, records, pendingDraft, mobileCommand) {
  if (!ALLOWED_ACTIONS.includes(action)) {
    if (mobileCommand) return "mobile_command";
    return records.length ? "draft" : "chat";
  }
  if (action === "draft") return records.length ? "draft" : "chat";
  if (action === "mobile_command") return mobileCommand ? "mobile_command" : "chat";
  if ((action === "confirm_pending" || action === "cancel_pending") && !pendingDraft.length) return "chat";
  return action;
}

function sanitizeReply(reply, action, records, mobileCommand) {
  const text = String(reply || "").trim();
  if (text) return text.slice(0, 520);
  if (action === "mobile_command" && mobileCommand) return createMobileReply(mobileCommand);
  if (action === "draft" && records.length) return `我整理出 ${records.length} 笔待确认账单，你回复“好”我就帮你保存。`;
  if (action === "confirm_pending") return "好的，已帮你记上。";
  if (action === "cancel_pending") return "好的，这次先不保存。";
  return "我在，直接和我说就行。";
}

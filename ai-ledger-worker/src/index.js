const ALLOWED_CATEGORIES = ["餐饮", "交通", "购物", "居住", "饮品", "工资", "礼物", "其他"];
const TOOL_REGISTRY = [
  {
    name: "ledger.draft_records",
    action: "draft",
    title: "整理待确认账单",
    description: "当用户表达真实收支时，把内容整理成待确认账单；缺金额等关键信息时先追问。",
    params: ["records[].title", "records[].amount", "records[].type", "records[].category", "records[].date"],
    safety: "不直接保存，必须等待用户确认。",
  },
  {
    name: "mobile.set_alarm",
    action: "mobile_command",
    commandType: "set_alarm",
    title: "设置系统闹钟",
    description: "当用户要求叫醒、闹钟、提醒某个具体时间时使用。",
    params: ["date", "hour", "minute", "label"],
    safety: "生成动作卡片，用户确认后才调用 Android 原生能力。",
  },
  {
    name: "mobile.open_app",
    action: "mobile_command",
    commandType: "open_app",
    title: "打开手机应用",
    description: "当用户要求打开微信、支付宝、淘宝、QQ、百度地图等应用时使用。",
    params: ["appName", "packageName?"],
    safety: "生成动作卡片，用户确认后才调用 Android 原生能力。",
  },
  {
    name: "mobile.navigate",
    action: "mobile_command",
    commandType: "navigate",
    title: "百度地图导航",
    description: "当用户要求导航、回家、去某地怎么走、带我去某地时使用。",
    params: ["destination", "mode"],
    safety: "默认用百度地图；用户确认后才调用 Android 原生能力。",
  },
];
const ALLOWED_ACTIONS = ["chat", "draft", "confirm_pending", "cancel_pending", "mobile_command"];
const ALLOWED_MOBILE_ACTIONS = TOOL_REGISTRY.map((tool) => tool.commandType).filter(Boolean);
const WORKER_VERSION = "2026-05-15-agent-tools-1";

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
        mode: "hybrid_rules_plus_ai",
        agentMode: "general_chat_plus_tool_registry",
        tools: TOOL_REGISTRY.map(({ name, action, commandType, title }) => ({ name, action, commandType, title })),
        model: env.AI_MODEL || "@cf/meta/llama-3.1-8b-instruct",
        hasAiBinding: Boolean(env.AI),
      }, 200, corsHeaders);
    }

    if (request.method !== "POST") {
      return json({ error: "Method not allowed", code: "method_not_allowed", version: WORKER_VERSION }, 405, corsHeaders);
    }

    if (!env.AI) {
      return json({
        error: "Server is missing Workers AI binding",
        code: "missing_workers_ai_binding",
        version: WORKER_VERSION,
      }, 500, corsHeaders);
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
    const conversation = messages.length ? buildConversation(messages) : text;
    const pendingDraft = sanitizeRecords(body?.pendingDraft, now);
    const ledgerContext = sanitizeLedgerContext(body?.ledgerContext, now);
    const clientTools = sanitizeClientTools(body?.clientTools);
    const lastUserText = getLastUserText(messages, text);

    if (!conversation) {
      return json({ error: "messages or text is required", code: "missing_conversation", version: WORKER_VERSION }, 400, corsHeaders);
    }

    if (conversation.length > 5000) {
      return json({ error: "conversation is too long", code: "conversation_too_long", version: WORKER_VERSION }, 400, corsHeaders);
    }

    const deterministic = tryDeterministicReply({ lastUserText, messages, pendingDraft, ledgerContext, now });
    if (deterministic) {
      return json({ ...deterministic, source: "hybrid_rules", version: WORKER_VERSION }, 200, corsHeaders);
    }

    const schema = {
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
                mode: { type: "string", enum: ["driving", "walking", "riding"] }
              }
            }
          },
          required: ["type", "params"]
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
              date: { type: "string" }
            },
            required: ["title", "amount", "type", "category", "date"]
          }
        }
      },
      required: ["reply", "action", "records"]
    };

    const instructions = [
      "你是一个中文通用手机 AI 智能体。你不是单纯的字段解析器，而是能自然聊天、理解上下文，并在合适时调用工具的个人助手。",
      "你的核心能力：1. 像正常助手一样随意聊天；2. 根据账本上下文回答收支问题；3. 用账单工具整理待确认账单；4. 用手机工具生成可确认执行的动作卡片。",
      "你必须先理解用户意图，再决定是否需要工具。普通聊天不要强行调用工具；工具只在用户明显需要记账或控制手机时使用。",
      "下面是 Tool Registry。只能使用注册表里的工具，不要发明工具名、动作类型或参数。",
      JSON.stringify(TOOL_REGISTRY),
      "输出协议：chat=普通回复或继续追问；draft=使用 ledger.draft_records；confirm_pending=用户同意保存当前待确认账单；cancel_pending=用户取消当前待确认账单；mobile_command=使用 mobile.* 工具生成待确认手机动作。",
      "如果 action=mobile_command，records 必须为空数组，mobileCommand 必须完整。mobileCommand.type 只能是 set_alarm、open_app、navigate。",
      "如果 action=draft，mobileCommand 不要返回有效内容，records 必须包含完整待确认账单。",
      "如果 action=chat、confirm_pending、cancel_pending，records 必须为空数组，除非你在继续追问或普通聊天。",
      "手机工具安全规则：所有 mobile_command 都只是生成动作卡片，不能声称已经执行。回复必须提示用户确认后再执行。",
      "导航规则：用户说回家、到家、我想回家了、带我回家，destination 返回“家”。用户说步行/走路用 mode=walking，骑行/骑车用 mode=riding，其他默认 driving。",
      "如果用户对已有待确认账单说‘好’‘对’‘确认’‘记上’‘保存’‘就这样’等同意语，且存在 pendingDraft，action 必须为 confirm_pending，records 为空数组。",
      "如果用户对已有待确认账单说‘算了’‘不用了’‘先别记’‘取消’等拒绝语，且存在 pendingDraft，action 必须为 cancel_pending，records 为空数组。",
      "如果存在 pendingDraft，用户说‘改成58’‘分类改成交通’‘日期改成昨天’等，action 必须为 draft，并返回更新后的完整待确认账单，不要只返回被修改的一部分。",
      "如果缺少关键信息，例如没有金额，先自然追问最关键的一项，不要猜测，不要创建账单。",
      "账本问答只能依据 ledgerContext 中提供的数据回答；如果上下文不足，要坦率说明。",
      "只记录用户本人真实承担或真实收到的金额，不要把同一件事中的多个金额拆成重复账单。",
      "如果用户同时说了总额和自己的份额，例如‘昨天和室友吃火锅我付了126，我自己花63’，只记录一笔‘火锅’支出63，不得再额外记录126。",
      "如果用户说‘我先垫付126，最后自己承担63’，同样只记录63；除非用户明确要求记录代付或应收款，否则不要记录垫付总额。",
      "如果一句话里有多个互相独立的事件，例如‘午饭28，奶茶16，兼职收入180’，可以返回多条账单。",
      "若同一事件中出现多个金额且无法确定哪个才是用户实际承担金额，优先追问，不要猜测。",
      "type 只能是 expense 或 income。",
      `category 只能从 ${ALLOWED_CATEGORIES.join("、")} 中选择。`,
      `今天日期是 ${now}。请正确解析今天、昨天、前天等相对日期。`,
      "date 必须返回 YYYY-MM-DD 格式。",
      "title 保持简短，优先使用事项本身，如‘火锅’‘地铁’‘兼职’；不要把整句话当标题。",
      "不要虚构金额，不要补充用户未表达的账单。",
      "回复要自然、简短、像人在聊天，不要每次都机械地重复固定模板。",
      "只返回合法 JSON，不要输出 Markdown，不要输出解释文字。",
      "示例1：用户：你好。输出 action=chat，reply 可以是自然问候，records=[]。",
      "示例2：用户：帮我记一笔午饭。因为缺少金额，输出 action=chat，reply 追问‘午饭花了多少钱？’，records=[]。",
      "示例3：用户：昨天和室友吃火锅我付了126，我自己花63。输出 action=draft，records 只包含火锅63元。",
      "示例4：已有 pendingDraft 后，用户：好。输出 action=confirm_pending。",
      "示例5：已有 pendingDraft 后，用户：把这笔改成交通。输出 action=draft，records 返回修改后的完整草稿。",
      "示例6：用户：我想回家了。输出 action=mobile_command，mobileCommand.type=navigate，params.destination=家，params.mode=driving。",
      "示例7：用户：打开微信。输出 action=mobile_command，mobileCommand.type=open_app，params.appName=微信。",
      "示例8：用户：明天早上8点叫我起床。输出 action=mobile_command，mobileCommand.type=set_alarm，params.date=明天日期，hour=8，minute=0，label=起床。"
    ].join("\n");

    const context = [
      `今天日期：${now}`,
      `待确认账单：${pendingDraft.length ? JSON.stringify(pendingDraft) : "无"}`,
      `账本上下文：${JSON.stringify(ledgerContext)}`,
      `客户端可执行工具：${clientTools.length ? JSON.stringify(clientTools) : "未上报，按默认 Tool Registry 处理"}`,
      `对话历史：\n${conversation}`,
      "请根据最后一条用户消息作答。"
    ].join("\n\n");

    let aiResult;
    try {
      aiResult = await env.AI.run(env.AI_MODEL || "@cf/meta/llama-3.1-8b-instruct", {
        messages: [
          { role: "system", content: instructions },
          { role: "user", content: context },
        ],
        temperature: 0.1,
        max_tokens: 500,
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

    let parsed;
    try {
      parsed = JSON.parse(raw);
    } catch {
      return json({
        error: "AI returned invalid JSON",
        code: "invalid_ai_json",
        raw: String(raw || "").slice(0, 500),
        version: WORKER_VERSION,
      }, 502, corsHeaders);
    }

    const records = sanitizeRecords(parsed.records, now);
    const mobileCommand = sanitizeMobileCommand(parsed.mobileCommand, now);
    const action = sanitizeAction(parsed.action, records, pendingDraft, mobileCommand);
    const reply = sanitizeReply(parsed.reply, action, records, mobileCommand);
    return json({ reply, action, records, mobileCommand, source: "workers_ai", version: WORKER_VERSION }, 200, corsHeaders);
  },
};

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

function getLastUserText(messages, fallbackText) {
  const last = [...messages].reverse().find((message) => message.role === "user");
  return String(last?.content || fallbackText || "").trim();
}

function tryDeterministicReply({ lastUserText, messages, pendingDraft, ledgerContext, now }) {
  const text = String(lastUserText || "").trim();
  if (!text) return null;

  if (pendingDraft.length && /^(好|好的|对|确认|保存|记上|就这样)$/u.test(text)) {
    return { reply: "好的，已帮你记上。", action: "confirm_pending", records: [] };
  }

  if (pendingDraft.length && /^(算了|不用了|先别记|取消)$/u.test(text)) {
    return { reply: "好的，这次先不保存。", action: "cancel_pending", records: [] };
  }

  if (/^(你好|您好|嗨|哈喽|在吗|hello|hi)$/iu.test(text)) {
    return { reply: "你好呀。想记一笔，还是查一下最近的账？", action: "chat", records: [] };
  }

  if (/^(想记点账|想记账|我要记账|我想记账|记点账|记账)$/u.test(text)) {
    return { reply: "好呀，想记什么？", action: "chat", records: [] };
  }

  if (/(你有|你会|有哪些).*(功能|能做什么)/u.test(text)) {
    return {
      reply: "我可以帮你记账、查收支，也能生成打开应用、设置闹钟、百度地图导航这些手机动作。",
      action: "chat",
      records: [],
    };
  }

  const mobileCommand = parseMobileCommand(text, now);
  if (mobileCommand) {
    return {
      reply: createMobileReply(mobileCommand),
      action: "mobile_command",
      records: [],
      mobileCommand,
    };
  }

  const simpleRecords = parseSimpleRecords(text, now);
  if (simpleRecords.length) {
    return {
      reply: `我先整理出 ${simpleRecords.length} 笔待确认账单，你回复“好”我就帮你保存。`,
      action: "draft",
      records: simpleRecords,
    };
  }

  const requestedItem = extractIncompleteItem(text) || extractStandaloneItem(text);
  if (requestedItem) {
    return { reply: `${requestedItem}花了多少钱？`, action: "chat", records: [] };
  }

  const categoryQuery = extractCategoryQuery(text);
  if (categoryQuery) {
    const total = sumCategoryThisMonth(ledgerContext.recentRecords, categoryQuery, now);
    return {
      reply: `你这个月${categoryQuery}一共花了 ¥${total.toFixed(2)}。`,
      action: "chat",
      records: [],
    };
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
  return {
    type: "open_app",
    title: "打开应用",
    summary: appName,
    params: { appName },
  };
}

function parseNavigationCommand(text) {
  if (!/(导航|路线|带我去|回家|到家|怎么走|我想回家)/u.test(text)) return null;
  const destinationMatch = text.match(/(?:导航(?:到|去)?|路线到|带我去|怎么去|怎么到)\s*([\u4e00-\u9fa5A-Za-z0-9·.\- ]+)$/u)
    || text.match(/去\s*([\u4e00-\u9fa5A-Za-z0-9·.\- ]+?)(?:怎么走|路线|导航)$/u);
  let destination = destinationMatch?.[1]?.trim() || "";

  if (/回家|到家|去家|家里|我家|我想回家/u.test(text)) destination = "家";
  destination = destination
    .replace(/^(百度地图|地图|帮我|请|给我)/u, "")
    .replace(/(?:怎么走|路线|导航)$/u, "")
    .trim();

  if (!destination || /^(打开|启动)?(百度地图|地图)$/u.test(destination)) return null;
  const mode = /步行|走路/u.test(text) ? "walking" : /骑行|骑车|单车/u.test(text) ? "riding" : "driving";
  return {
    type: "navigate",
    title: "百度地图导航",
    summary: `到 ${destination}`,
    params: { appName: "百度地图", destination, mode },
  };
}

function createMobileReply(command) {
  if (command.type === "set_alarm") {
    return `我理解为要${command.summary}设置“${command.params.label}”闹钟，确认后我再执行。`;
  }
  if (command.type === "navigate") {
    return `我理解为要用百度地图导航到“${command.params.destination}”，确认后我再执行。`;
  }
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
  return String(text || "")
    .replace(/^(一笔|一个|一下)/u, "")
    .replace(/金额$/u, "")
    .trim();
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
  return JSON.stringify(result);
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
  return messages
    .map((message) => `${message.role === "assistant" ? "助手" : "用户"}：${message.content}`)
    .join("\n");
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
    return {
      type,
      title: "设置闹钟",
      summary: `${date} ${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`,
      params: { date, hour, minute, label },
    };
  }

  if (type === "open_app") {
    const appName = String(params.appName || command.summary || "").trim().slice(0, 30);
    if (!appName) return null;
    return {
      type,
      title: "打开应用",
      summary: appName,
      params: { appName, packageName: String(params.packageName || "").trim().slice(0, 80) },
    };
  }

  if (type === "navigate") {
    const destination = String(params.destination || command.summary || "").replace(/^到\s*/, "").trim().slice(0, 80);
    if (!destination) return null;
    const mode = ["driving", "walking", "riding"].includes(params.mode) ? params.mode : "driving";
    return {
      type,
      title: "百度地图导航",
      summary: `到 ${destination}`,
      params: { appName: "百度地图", destination, mode },
    };
  }

  return null;
}

function sanitizeAction(action, records, pendingDraft, mobileCommand) {
  if (!ALLOWED_ACTIONS.includes(action)) {
    if (mobileCommand) return "mobile_command";
    return records.length ? "draft" : "chat";
  }
  if (action === "draft") {
    return records.length ? "draft" : "chat";
  }
  if (action === "mobile_command") {
    return mobileCommand ? "mobile_command" : "chat";
  }
  if ((action === "confirm_pending" || action === "cancel_pending") && !pendingDraft.length) {
    return "chat";
  }
  return action;
}

function sanitizeReply(reply, action, records, mobileCommand) {
  const text = String(reply || "").trim();
  if (text) return text.slice(0, 320);
  if (action === "mobile_command" && mobileCommand) {
    return createMobileReply(mobileCommand);
  }
  if (action === "draft" && records.length) {
    return `我整理出 ${records.length} 笔待确认账单，你回复“好”我就帮你保存。`;
  }
  if (action === "confirm_pending") {
    return "好的，已帮你记上。";
  }
  if (action === "cancel_pending") {
    return "好的，这次先不保存。";
  }
  return "我在，直接和我说就行。";
}

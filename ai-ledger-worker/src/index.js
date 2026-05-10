const ALLOWED_CATEGORIES = ["餐饮", "交通", "购物", "居住", "饮品", "工资", "礼物", "其他"];
const ALLOWED_ACTIONS = ["chat", "draft", "confirm_pending", "cancel_pending"];
const WORKER_VERSION = "2026-05-10-hybrid-ai-1";

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
      "你是一个中文 AI 记账助手，不是单纯的字段解析器。你要像真实聊天一样自然回复用户，同时在需要时协助记账。",
      "你可以做三类事：1. 正常聊天；2. 根据账本上下文回答收支问题；3. 整理待确认账单。",
      "回复要自然、简短、像人在聊天，不要每次都机械地重复固定模板。",
      "action 说明：chat=普通回复或继续追问；draft=产生或更新待确认账单；confirm_pending=用户明确同意保存当前待确认账单；cancel_pending=用户明确表示不要保存当前待确认账单。",
      "如果用户只是打招呼、闲聊、提问、或信息还不完整，action 必须为 chat，records 必须为空数组。",
      "如果用户提供的信息足够形成一笔或多笔账单，action 必须为 draft，records 返回待确认账单，reply 要自然地说明你理解到了什么，并提示用户可以回复‘好’或点确认保存。",
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
      "只返回合法 JSON，不要输出 Markdown，不要输出解释文字。",
      "示例1：用户：你好。输出 action=chat，reply 可以是自然问候，records=[]。",
      "示例2：用户：帮我记一笔午饭。因为缺少金额，输出 action=chat，reply 追问‘午饭花了多少钱？’，records=[]。",
      "示例3：用户：昨天和室友吃火锅我付了126，我自己花63。输出 action=draft，records 只包含火锅63元。",
      "示例4：已有 pendingDraft 后，用户：好。输出 action=confirm_pending。",
      "示例5：已有 pendingDraft 后，用户：把这笔改成交通。输出 action=draft，records 返回修改后的完整草稿。"
    ].join("\n");

    const context = [
      `今天日期：${now}`,
      `待确认账单：${pendingDraft.length ? JSON.stringify(pendingDraft) : "无"}`,
      `账本上下文：${JSON.stringify(ledgerContext)}`,
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
    const action = sanitizeAction(parsed.action, records, pendingDraft);
    const reply = sanitizeReply(parsed.reply, action, records);
    return json({ reply, action, records, source: "workers_ai", version: WORKER_VERSION }, 200, corsHeaders);
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

  if (/(你有|你会|有哪些).*(功能|能做什么)/u.test(text)) {
    return {
      reply: "我可以帮你记账、补问金额、确认或取消账单，也能查本月收支和分类花费。",
      action: "chat",
      records: [],
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
  return normalizeItem(match[1]);
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

function sanitizeAction(action, records, pendingDraft) {
  if (!ALLOWED_ACTIONS.includes(action)) {
    return records.length ? "draft" : "chat";
  }
  if (action === "draft") {
    return records.length ? "draft" : "chat";
  }
  if ((action === "confirm_pending" || action === "cancel_pending") && !pendingDraft.length) {
    return "chat";
  }
  return action;
}

function sanitizeReply(reply, action, records) {
  const text = String(reply || "").trim();
  if (text) return text.slice(0, 320);
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

const ALLOWED_CATEGORIES = ["餐饮", "交通", "购物", "居住", "饮品", "工资", "礼物", "其他"];
const ALLOWED_ACTIONS = ["chat", "draft", "confirm_pending", "cancel_pending"];
const WORKER_VERSION = "2026-05-10-diagnostics-1";

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
        model: env.OPENAI_MODEL || "gpt-4.1-mini",
        hasOpenAIKey: Boolean(env.OPENAI_API_KEY),
      }, 200, corsHeaders);
    }

    if (request.method !== "POST") {
      return json({ error: "Method not allowed", code: "method_not_allowed", version: WORKER_VERSION }, 405, corsHeaders);
    }

    if (!env.OPENAI_API_KEY) {
      return json({
        error: "Server is missing OPENAI_API_KEY",
        code: "missing_openai_api_key",
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

    if (!conversation) {
      return json({ error: "messages or text is required", code: "missing_conversation", version: WORKER_VERSION }, 400, corsHeaders);
    }

    if (conversation.length > 5000) {
      return json({ error: "conversation is too long", code: "conversation_too_long", version: WORKER_VERSION }, 400, corsHeaders);
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

    let upstream;
    try {
      upstream = await fetch("https://api.openai.com/v1/responses", {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "authorization": `Bearer ${env.OPENAI_API_KEY}`,
        },
        body: JSON.stringify({
          model: env.OPENAI_MODEL || "gpt-4.1-mini",
          instructions,
          input: context,
          temperature: 0.2,
          max_output_tokens: 800,
          text: {
            format: {
              type: "json_schema",
              name: "ledger_conversation_reply",
              strict: true,
              schema,
            },
          },
        }),
      });
    } catch (error) {
      return json({
        error: "Failed to reach AI provider",
        code: "provider_unreachable",
        detail: String(error),
        version: WORKER_VERSION,
      }, 502, corsHeaders);
    }

    if (!upstream.ok) {
      const provider = await readProviderError(upstream);
      return json({
        error: "AI provider error",
        code: "provider_error",
        providerStatus: upstream.status,
        providerCode: provider.code,
        providerMessage: provider.message,
        version: WORKER_VERSION,
      }, 502, corsHeaders);
    }

    const data = await upstream.json();
    const raw = extractOutputText(data);

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
    return json({ reply, action, records, source: "cloud_ai", version: WORKER_VERSION }, 200, corsHeaders);
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

async function readProviderError(response) {
  try {
    const payload = await response.json();
    const err = payload?.error || {};
    return {
      code: err.code || err.type || "unknown_provider_error",
      message: err.message || `Provider returned HTTP ${response.status}`,
    };
  } catch {
    return {
      code: "unknown_provider_error",
      message: `Provider returned HTTP ${response.status}`,
    };
  }
}

function extractOutputText(data) {
  if (typeof data.output_text === "string") return data.output_text;

  const chunks = [];
  for (const item of data.output || []) {
    for (const content of item.content || []) {
      if (content.type === "output_text" && typeof content.text === "string") {
        chunks.push(content.text);
      }
    }
  }
  return chunks.join("");
}

function normalizeIsoDate(value) {
  const text = String(value || "");
  return /^\d{4}-\d{2}-\d{2}$/.test(text) ? text : null;
}

function normalizeMessages(messages) {
  if (!Array.isArray(messages)) return [];
  return messages
    .slice(-16)
    .map((message) => ({
      role: message?.role === "assistant" ? "assistant" : "user",
      content: String(message?.content || "").trim().slice(0, 600),
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

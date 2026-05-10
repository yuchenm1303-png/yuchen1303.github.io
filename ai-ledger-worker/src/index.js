const ALLOWED_CATEGORIES = ["餐饮", "交通", "购物", "居住", "饮品", "工资", "礼物", "其他"];
const ALLOWED_STATUS = ["draft", "clarify", "none"];

const jsonHeaders = {
  "content-type": "application/json; charset=utf-8",
};

export default {
  async fetch(request, env) {
    const corsHeaders = getCorsHeaders(request, env);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders });
    }

    if (request.method !== "POST") {
      return json({ error: "Method not allowed" }, 405, corsHeaders);
    }

    if (!env.OPENAI_API_KEY) {
      return json({ error: "Server is missing OPENAI_API_KEY" }, 500, corsHeaders);
    }

    let body;
    try {
      body = await request.json();
    } catch {
      return json({ error: "Invalid JSON body" }, 400, corsHeaders);
    }

    const now = normalizeIsoDate(body?.now) || new Date().toISOString().slice(0, 10);
    const messages = normalizeMessages(body?.messages);
    const text = String(body?.text || "").trim();
    const conversation = messages.length ? buildConversation(messages) : text;

    if (!conversation) {
      return json({ error: "messages or text is required" }, 400, corsHeaders);
    }

    if (conversation.length > 4000) {
      return json({ error: "conversation is too long" }, 400, corsHeaders);
    }

    const schema = {
      type: "object",
      additionalProperties: false,
      properties: {
        reply: { type: "string" },
        status: { type: "string", enum: ALLOWED_STATUS },
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
              date: { type: "string", pattern: "^\\d{4}-\\d{2}-\\d{2}$" }
            },
            required: ["title", "amount", "type", "category", "date"]
          }
        }
      },
      required: ["reply", "status", "records"]
    };

    const instructions = [
      "你是一个中文 AI 记账助手，负责与用户对话并整理待确认账单。",
      "目标：先理解，再给出自然、简短的中文回复；不要直接假装已经保存，除非前端明确告诉你已经保存。",
      "当用户提供的信息足够形成一笔或多笔账单时，status 必须为 draft，records 返回待确认账单，reply 要用一句话概括并询问是否保存。",
      "当关键信息缺失（例如没有金额、没有说明收入还是支出）时，status 必须为 clarify，records 返回空数组，reply 只追问最关键的一项。",
      "当用户并未提供可记账内容时，status 为 none，records 返回空数组，reply 正常回应即可。",
      "只记录用户本人真实承担或真实收到的金额，不要把同一件事中的多个金额拆成重复账单。",
      "如果用户同时说了总额和自己的份额，例如‘昨天和室友吃火锅我付了126，我自己花63’，只记录一笔‘火锅’支出 63，不得再额外记录 126。",
      "如果用户说‘我先垫付126，最后自己承担63’，同样只记录 63；除非用户明确要求记录代付或应收款，否则不要记录垫付总额。",
      "如果一句话里有多个互相独立的事件，例如‘午饭28，奶茶16，兼职收入180’，可以返回多条账单。",
      "若同一事件中出现多个金额且无法确定哪个才是用户实际承担金额，优先追问，不要猜测。",
      "type 只能是 expense 或 income。",
      `category 只能从 ${ALLOWED_CATEGORIES.join("、")} 中选择。`,
      `今天日期是 ${now}。请正确解析今天、昨天、前天等相对日期。`,
      "title 保持简短，优先使用事项本身，如‘火锅’‘地铁’‘兼职’；不要把整句话当标题。",
      "不要虚构金额，不要补充用户未表达的账单。",
      "示例：用户：昨天和室友吃火锅我付了126，我自己花63。输出应只包含一条：火锅，63，expense，餐饮，昨天日期。",
      "示例：用户：帮我记一笔午饭。由于缺少金额，应追问金额，records 为空。"
    ].join("\n");

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
          input: `以下是对话历史，请根据最后一条用户消息给出回复和待确认账单：\n\n${conversation}`,
          temperature: 0,
          max_output_tokens: 700,
          text: {
            format: {
              type: "json_schema",
              name: "ledger_chat_reply",
              strict: true,
              schema,
            },
          },
        }),
      });
    } catch (error) {
      return json({ error: "Failed to reach AI provider", detail: String(error) }, 502, corsHeaders);
    }

    if (!upstream.ok) {
      const detail = await upstream.text();
      return json({ error: "AI provider error", detail }, 502, corsHeaders);
    }

    const data = await upstream.json();
    const raw = extractOutputText(data);

    let parsed;
    try {
      parsed = JSON.parse(raw);
    } catch {
      return json({ error: "AI returned invalid JSON", raw }, 502, corsHeaders);
    }

    const records = sanitizeRecords(parsed.records, now);
    const status = sanitizeStatus(parsed.status, records);
    const reply = sanitizeReply(parsed.reply, status, records);
    return json({ reply, status, records, source: "cloud_ai" }, 200, corsHeaders);
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
    "access-control-allow-methods": "POST, OPTIONS",
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
    .slice(-12)
    .map((message) => ({
      role: message?.role === "assistant" ? "assistant" : "user",
      content: String(message?.content || "").trim().slice(0, 500),
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

function sanitizeStatus(status, records) {
  if (ALLOWED_STATUS.includes(status)) {
    return records.length ? "draft" : status;
  }
  return records.length ? "draft" : "clarify";
}

function sanitizeReply(reply, status, records) {
  const text = String(reply || "").trim();
  if (text) return text.slice(0, 240);
  if (status === "draft" && records.length) {
    return `我整理出 ${records.length} 笔待确认账单，确认后再帮你保存。`;
  }
  if (status === "clarify") {
    return "我还差一点信息才能记账，你可以再补充一下金额或用途。";
  }
  return "我在，直接告诉我一笔消费或收入就行。";
}

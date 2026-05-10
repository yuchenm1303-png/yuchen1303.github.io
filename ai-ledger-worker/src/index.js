const ALLOWED_CATEGORIES = ["餐饮", "交通", "购物", "居住", "饮品", "工资", "礼物", "其他"];

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

    const text = String(body?.text || "").trim();
    const now = normalizeIsoDate(body?.now) || new Date().toISOString().slice(0, 10);

    if (!text) {
      return json({ error: "text is required" }, 400, corsHeaders);
    }

    if (text.length > 500) {
      return json({ error: "text is too long" }, 400, corsHeaders);
    }

    const schema = {
      type: "object",
      additionalProperties: false,
      properties: {
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
      required: ["records"]
    };

    const instructions = [
      "你是一个中文记账解析器。",
      "把用户输入拆分成一条或多条账单记录。",
      "只提取用户自己实际承担的金额；如果用户说‘我付了126，我自己花63’，amount 应为 63。",
      "type 只能是 expense 或 income。",
      `category 只能从 ${ALLOWED_CATEGORIES.join("、")} 中选择。`,
      `今天日期是 ${now}。请正确解析今天、昨天、前天等相对日期。`,
      "title 保持简短，优先使用消费事项，如‘火锅’‘地铁’‘兼职’。",
      "如果一句话无法形成有效账单，返回 records 空数组。",
      "不要虚构金额，不要补充用户未表达的账单。"
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
          input: text,
          temperature: 0,
          max_output_tokens: 500,
          text: {
            format: {
              type: "json_schema",
              name: "ledger_records",
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
    return json({ records, source: "cloud_ai" }, 200, corsHeaders);
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

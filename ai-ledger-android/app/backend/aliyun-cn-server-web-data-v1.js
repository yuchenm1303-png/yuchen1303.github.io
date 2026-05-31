const http = require("http");

const PORT = Number(process.env.PORT || process.env.FC_SERVER_PORT || 9000);
const REQUEST_TIMEOUT_MS = Number(process.env.PROVIDER_TIMEOUT_MS || 30000);

function sendJson(res, status, data) {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "content-type, authorization, x-client",
  });
  res.end(JSON.stringify(data));
}

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    let raw = "";
    req.on("data", (chunk) => {
      raw += chunk;
      if (raw.length > 2 * 1024 * 1024) {
        reject(new Error("body_too_large"));
        req.destroy();
      }
    });
    req.on("end", () => {
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch (e) {
        reject(new Error("invalid_json"));
      }
    });
    req.on("error", reject);
  });
}

function latestUserText(messages) {
  if (!Array.isArray(messages)) return "";
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    const item = messages[i] || {};
    const role = String(item.role || "").toLowerCase();
    const content = typeof item.content === "string" ? item.content : item.text;
    if (role === "user" && content && String(content).trim()) {
      return String(content).trim();
    }
  }
  return "";
}

function normalizeMessages(input, fallbackText) {
  const source = Array.isArray(input) ? input : [];
  const clean = [];

  for (const item of source.slice(-18)) {
    const roleRaw = String(item?.role || "").toLowerCase().trim();
    const role = roleRaw === "assistant" ? "assistant" : roleRaw === "user" ? "user" : "";
    const content =
      typeof item?.content === "string"
        ? item.content
        : typeof item?.text === "string"
          ? item.text
          : "";
    const text = content.trim();
    if (!role || !text) continue;
    clean.push({ role, content: text });
  }

  while (clean.length && clean[0].role !== "user") clean.shift();

  const fallback = String(fallbackText || "").trim();
  if (!clean.length && fallback) clean.push({ role: "user", content: fallback });

  return clean.slice(-16);
}

function hasAny(text, keywords) {
  return keywords.some((keyword) => text.includes(keyword));
}

function routeAuto(prompt) {
  const text = String(prompt || "").toLowerCase();

  if (
    hasAny(text, [
      "推理", "证明", "数学", "计算", "建模", "电路", "方案", "架构",
      "为什么", "分析", "优化", "极限", "偏导", "积分", "仿真"
    ])
  ) {
    return "deepseek_v4";
  }

  return "qwen";
}

function resolveModel(modelPref, prompt) {
  const pref = String(modelPref || "auto").toLowerCase().trim();

  if (pref === "auto") return routeAuto(prompt);
  if (["qwen", "qwen_max", "qwen-max", "qwen_plus", "qwen-plus", "kimi"].includes(pref) || pref.startsWith("qwen")) return "qwen";
  if (["deepseek", "deepseek_v4", "deepseek-v4", "deepseek_v4_pro", "deepseek-v4-pro"].includes(pref)) return "deepseek_v4";

  return "unsupported";
}

function isForceWebSearch(body) {
  const mode = String(body.webSearchMode || body.searchMode || body.webSearch?.mode || "").toLowerCase();
  return Boolean(body.onlineEnabled || body.searchEnabled || body.forceWebSearch || body.webSearch?.force || mode === "force");
}

async function fetchWithTimeout(url, options = {}, timeoutMs = REQUEST_TIMEOUT_MS) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

async function callOpenAICompatible(base, key, model, messages, name) {
  if (!key) throw new Error(`${name} key missing`);
  if (!base) throw new Error(`${name} base url missing`);
  if (!model) throw new Error(`${name} model missing`);

  const endpoint = `${String(base).replace(/\/+$/g, "")}/chat/completions`;

  const r = await fetchWithTimeout(endpoint, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${key}`,
    },
    body: JSON.stringify({
      model,
      messages,
      temperature: 0.35,
      max_tokens: 1200,
      stream: false,
    }),
  });

  const t = await r.text();

  if (!r.ok) {
    throw new Error(`${name} ${r.status} ${t.slice(0, 240)}`);
  }

  let data;
  try {
    data = JSON.parse(t);
  } catch (e) {
    throw new Error(`${name} invalid_json_response ${t.slice(0, 120)}`);
  }

  const reply =
    data?.choices?.[0]?.message?.content ||
    data?.choices?.[0]?.text ||
    data?.reply ||
    "";

  if (!String(reply).trim()) {
    throw new Error(`${name} empty`);
  }

  return String(reply).trim();
}

function detectStructuredIntent(prompt) {
  const text = String(prompt || "").trim();
  const lower = text.toLowerCase();

  if (/(天气|气温|温度|下雨|降雨|weather)/i.test(text)) {
    return { type: "weather", query: extractBeforeKeyword(text, ["天气", "气温", "温度", "weather"]) || "杭州" };
  }

  if (/(汇率|兑换|兑|exchange rate|currency)/i.test(text)) {
    const pair = extractCurrencyPair(text);
    return { type: "exchange_rate", from: pair.from, to: pair.to };
  }

  if (/(股价|股票|行情|stock|price|nasdaq|nyse|a股|港股)/i.test(text)) {
    return { type: "stock", symbol: extractStockSymbol(text) };
  }

  return null;
}

function extractBeforeKeyword(text, keywords) {
  for (const keyword of keywords) {
    const index = text.toLowerCase().indexOf(String(keyword).toLowerCase());
    if (index > 0) {
      return text.slice(Math.max(0, index - 12), index).replace(/[，。！？?\s]/g, "").trim();
    }
  }
  return "";
}

function extractCurrencyPair(text) {
  const upper = String(text || "").toUpperCase();
  const codes = upper.match(/\b[A-Z]{3}\b/g) || [];
  const cnMap = [
    ["美元", "USD"], ["人民币", "CNY"], ["日元", "JPY"], ["欧元", "EUR"],
    ["英镑", "GBP"], ["港币", "HKD"], ["新币", "SGD"], ["新加坡元", "SGD"]
  ];
  const found = [];
  for (const [cn, code] of cnMap) {
    if (text.includes(cn)) found.push(code);
  }
  const merged = [...codes, ...found];
  return { from: merged[0] || "USD", to: merged[1] || "CNY" };
}

function extractStockSymbol(text) {
  const upper = String(text || "").toUpperCase();
  const explicit = upper.match(/\b[A-Z]{1,6}(?:\.[A-Z]{1,4})?\b/);
  if (explicit) return explicit[0];
  const cnCode = text.match(/\b\d{6}\b/);
  return cnCode ? cnCode[0] : "";
}

async function getWeatherData(location) {
  const query = encodeURIComponent(location || "杭州");
  const geoUrl = `https://geocoding-api.open-meteo.com/v1/search?name=${query}&count=1&language=zh&format=json`;
  const geoRes = await fetchWithTimeout(geoUrl, { method: "GET" }, 12000);
  if (!geoRes.ok) throw new Error(`weather geocode ${geoRes.status}`);
  const geo = await geoRes.json();
  const place = geo?.results?.[0];
  if (!place) throw new Error(`weather location not found: ${location}`);

  const weatherUrl = `https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto`;
  const weatherRes = await fetchWithTimeout(weatherUrl, { method: "GET" }, 12000);
  if (!weatherRes.ok) throw new Error(`weather api ${weatherRes.status}`);
  const weather = await weatherRes.json();
  const current = weather.current || {};

  return {
    type: "weather",
    title: `${place.name || location}天气`,
    subtitle: [place.admin1, place.country].filter(Boolean).join(" · "),
    timestamp: current.time || new Date().toISOString(),
    metrics: [
      { label: "温度", value: String(current.temperature_2m ?? "--"), unit: weather.current_units?.temperature_2m || "°C" },
      { label: "湿度", value: String(current.relative_humidity_2m ?? "--"), unit: weather.current_units?.relative_humidity_2m || "%" },
      { label: "风速", value: String(current.wind_speed_10m ?? "--"), unit: weather.current_units?.wind_speed_10m || "km/h" },
      { label: "天气代码", value: String(current.weather_code ?? "--") },
    ],
    rawText: "天气数据来自实时天气接口，天气代码后续可在 App 端映射为晴、阴、雨等中文状态。",
  };
}

async function getExchangeRateData(from, to) {
  const base = encodeURIComponent(from || "USD");
  const target = String(to || "CNY").toUpperCase();
  const url = `https://open.er-api.com/v6/latest/${base}`;
  const res = await fetchWithTimeout(url, { method: "GET" }, 12000);
  if (!res.ok) throw new Error(`exchange api ${res.status}`);
  const data = await res.json();
  const rate = data?.rates?.[target];
  if (!rate) throw new Error(`exchange rate not found: ${base}/${target}`);

  return {
    type: "exchange_rate",
    title: `${base.toUpperCase()} / ${target} 汇率`,
    subtitle: data.provider || "实时汇率",
    timestamp: data.time_last_update_utc || new Date().toISOString(),
    metrics: [
      { label: "来源币种", value: base.toUpperCase() },
      { label: "目标币种", value: target },
      { label: "汇率", value: String(rate) },
    ],
  };
}

async function getStructuredData(intent) {
  if (!intent) return null;

  if (intent.type === "weather") {
    return await getWeatherData(intent.query);
  }

  if (intent.type === "exchange_rate") {
    return await getExchangeRateData(intent.from, intent.to);
  }

  if (intent.type === "stock") {
    return {
      type: "stock",
      title: "股票行情",
      subtitle: intent.symbol || "未识别代码",
      timestamp: new Date().toISOString(),
      metrics: [
        { label: "代码", value: intent.symbol || "未识别" },
        { label: "状态", value: "股票接口待配置" },
      ],
      rawText: "当前示例后端尚未绑定稳定股票行情源。可后续接入富途、AkShare 服务、腾讯行情或券商 API。",
    };
  }

  return null;
}

async function tavilySearch(query) {
  const key = process.env.TAVILY_API_KEY;
  if (!key) return { sources: [], provider: null };

  const res = await fetchWithTimeout("https://api.tavily.com/search", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      api_key: key,
      query,
      search_depth: "basic",
      include_answer: false,
      include_raw_content: false,
      max_results: 5,
    }),
  }, 15000);

  const text = await res.text();
  if (!res.ok) throw new Error(`tavily ${res.status} ${text.slice(0, 160)}`);
  const data = JSON.parse(text);
  const sources = (Array.isArray(data.results) ? data.results : []).map((item) => {
    const url = String(item.url || "");
    return {
      title: String(item.title || url || "搜索来源"),
      url,
      domain: url.replace(/^https?:\/\//, "").split("/")[0],
      snippet: String(item.content || item.snippet || "").slice(0, 360),
      publishedAt: item.published_date || item.publishedAt || "",
    };
  });

  return { sources, provider: "tavily" };
}

function buildSourceContext(sources) {
  if (!sources.length) return "";
  return sources.map((source, index) => {
    return `[${index + 1}] ${source.title}\n${source.url}\n${source.snippet}`;
  }).join("\n\n");
}

function buildMessages(bodyMessages, prompt, structuredData, sources) {
  const messages = normalizeMessages(bodyMessages, prompt);
  const contextBlocks = [];

  if (structuredData) {
    contextBlocks.push(`结构化实时数据：\n${JSON.stringify(structuredData, null, 2)}`);
  }

  const sourceContext = buildSourceContext(sources);
  if (sourceContext) {
    contextBlocks.push(`联网搜索资料：\n${sourceContext}`);
  }

  const system = [
    "你是可靠、清晰、简洁的中文助手。",
    "如果提供了结构化实时数据或联网搜索资料，必须优先基于这些资料回答。",
    "不要编造来源；无法确认时要说明不确定。",
  ];

  if (contextBlocks.length) {
    system.push(contextBlocks.join("\n\n"));
  }

  return [
    { role: "system", content: system.join("\n") },
    ...messages,
  ];
}

const server = http.createServer(async (req, res) => {
  try {
    if (req.method === "OPTIONS") return sendJson(res, 204, {});

    if (req.method === "GET") {
      return sendJson(res, 200, {
        ok: true,
        mode: "aliyun-fc-custom-runtime",
        version: "qwen-deepseek-cn-web-data-v1",
        features: ["qwen", "deepseek", "web_search_sources", "structured_realtime"],
      });
    }

    if (req.method !== "POST") {
      return sendJson(res, 405, { ok: false, error: "method_not_allowed" });
    }

    const body = await readJsonBody(req);
    const prompt =
      body.message ||
      body.prompt ||
      body.text ||
      body.content ||
      latestUserText(body.messages);

    if (!prompt || !String(prompt).trim()) {
      return sendJson(res, 400, { ok: false, error: "empty_prompt" });
    }

    const modelPref = String(
      body.modelPreference ||
      body.aiModelPreference ||
      body.requestedModelPreference ||
      body.modelId ||
      body.model ||
      "auto"
    ).toLowerCase().trim();

    const resolved = resolveModel(modelPref, prompt);
    if (resolved === "unsupported") {
      return sendJson(res, 200, {
        ok: false,
        unsupportedModel: true,
        shouldFallback: true,
        code: "model_not_available",
        error: `CN gateway does not support model: ${modelPref}`,
        model: modelPref,
        version: "qwen-deepseek-cn-web-data-v1",
      });
    }

    const forceSearch = isForceWebSearch(body);
    const structuredIntent = forceSearch ? detectStructuredIntent(prompt) : null;

    let structuredData = null;
    let structuredError = null;
    if (structuredIntent) {
      try {
        structuredData = await getStructuredData(structuredIntent);
      } catch (e) {
        structuredError = String(e.message || e);
      }
    }

    let sources = [];
    let searchProvider = null;
    let searchError = null;
    if (forceSearch) {
      try {
        const search = await tavilySearch(prompt);
        sources = search.sources;
        searchProvider = search.provider;
      } catch (e) {
        searchError = String(e.message || e);
      }
    }

    const messages = buildMessages(body.messages, prompt, structuredData, sources);

    const reply =
      resolved === "deepseek_v4"
        ? await callOpenAICompatible(
            process.env.DEEPSEEK_BASE_URL,
            process.env.DEEPSEEK_API_KEY,
            process.env.DEEPSEEK_MODEL,
            messages,
            "DeepSeek"
          )
        : await callOpenAICompatible(
            process.env.QWEN_BASE_URL,
            process.env.QWEN_API_KEY,
            process.env.QWEN_MODEL,
            messages,
            "Qwen"
          );

    return sendJson(res, 200, {
      ok: true,
      reply,
      source: resolved === "deepseek_v4" ? "deepseek" : "qwen",
      model: resolved,
      modelId: resolved,
      modelLabel: resolved === "deepseek_v4" ? "DeepSeek V4 Pro" : "Qwen Max",
      searchUsed: Boolean(forceSearch && sources.length),
      searchProvider,
      searchError,
      sources,
      structuredData,
      structuredError,
      version: "qwen-deepseek-cn-web-data-v1",
    });
  } catch (e) {
    return sendJson(res, 502, {
      ok: false,
      error: String(e.message || e),
      code: "provider_call_failed",
    });
  }
});

server.listen(PORT, () => {
  console.log(`AI Ledger CN web-data server listening on ${PORT}`);
});

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

  if (/(天气|气温|温度|下雨|降雨|weather)/i.test(text)) {
    return { type: "weather", query: extractLocation(text) || "杭州" };
  }

  if (/(汇率|兑换|兑|exchange rate|currency)/i.test(text)) {
    const pair = extractCurrencyPair(text);
    return { type: "exchange_rate", from: pair.from, to: pair.to };
  }

  if (/(股价|股票|行情|stock|price|nasdaq|nyse|a股|港股|美股)/i.test(text)) {
    return { type: "stock", symbol: normalizeStockSymbol(extractStockSymbol(text), text) };
  }

  return null;
}

function extractLocation(text) {
  const cleaned = String(text || "")
    .replace(/今天|现在|实时|当前|查询|一下|请问|帮我看看|怎么样|多少/g, "")
    .replace(/天气|气温|温度|下雨|降雨|weather/gi, "")
    .replace(/[，。！？?\s]/g, "")
    .trim();
  return cleaned.slice(0, 24);
}

function extractCurrencyPair(text) {
  const upper = String(text || "").toUpperCase();
  const codes = upper.match(/\b[A-Z]{3}\b/g) || [];
  const cnMap = [
    ["美元", "USD"], ["人民币", "CNY"], ["日元", "JPY"], ["欧元", "EUR"],
    ["英镑", "GBP"], ["港币", "HKD"], ["港元", "HKD"], ["新币", "SGD"], ["新加坡元", "SGD"]
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
  const known = [
    ["苹果", "AAPL"], ["特斯拉", "TSLA"], ["英伟达", "NVDA"], ["微软", "MSFT"],
    ["谷歌", "GOOGL"], ["亚马逊", "AMZN"], ["腾讯", "0700.HK"], ["阿里", "BABA"],
    ["贵州茅台", "600519.SS"], ["宁德时代", "300750.SZ"], ["比亚迪", "002594.SZ"]
  ];
  for (const [name, symbol] of known) {
    if (text.includes(name)) return symbol;
  }
  const hk = upper.match(/\b0?\d{3,4}\.HK\b/) || upper.match(/港股\s*(\d{3,5})/);
  if (hk) return String(hk[1] || hk[0]).replace(/^港股\s*/i, "");
  const explicit = upper.match(/\b[A-Z]{1,6}(?:\.[A-Z]{1,4})?\b/);
  if (explicit) return explicit[0];
  const cnCode = text.match(/\b\d{6}\b/);
  return cnCode ? cnCode[0] : "";
}

function normalizeStockSymbol(symbol, rawText) {
  const clean = String(symbol || "").toUpperCase().trim();
  if (!clean) return "";
  if (clean.includes(".")) return clean;
  if (/^\d{3,5}$/.test(clean) && /港股|港股行情|港股股价|HK/i.test(rawText)) {
    return clean.padStart(4, "0") + ".HK";
  }
  if (/^\d{6}$/.test(clean)) {
    if (/^(60|68|90)/.test(clean)) return `${clean}.SS`;
    if (/^(00|30|20)/.test(clean)) return `${clean}.SZ`;
    if (/^(83|87|43)/.test(clean)) return `${clean}.BJ`;
  }
  return clean;
}

function weatherCodeLabel(code) {
  const n = Number(code);
  if ([0].includes(n)) return "晴";
  if ([1, 2, 3].includes(n)) return "多云";
  if ([45, 48].includes(n)) return "雾";
  if ([51, 53, 55, 56, 57].includes(n)) return "毛毛雨";
  if ([61, 63, 65, 66, 67, 80, 81, 82].includes(n)) return "雨";
  if ([71, 73, 75, 77, 85, 86].includes(n)) return "雪";
  if ([95, 96, 99].includes(n)) return "雷暴";
  return String(code ?? "--");
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
    data: {
      type: "weather",
      title: `${place.name || location}天气`,
      subtitle: [place.admin1, place.country].filter(Boolean).join(" · "),
      timestamp: current.time || new Date().toISOString(),
      metrics: [
        { label: "温度", value: String(current.temperature_2m ?? "--"), unit: weather.current_units?.temperature_2m || "°C" },
        { label: "天气", value: weatherCodeLabel(current.weather_code) },
        { label: "湿度", value: String(current.relative_humidity_2m ?? "--"), unit: weather.current_units?.relative_humidity_2m || "%" },
        { label: "风速", value: String(current.wind_speed_10m ?? "--"), unit: weather.current_units?.wind_speed_10m || "km/h" },
      ],
      rawText: "天气数据来自 Open-Meteo 实时接口。",
    },
    source: {
      title: "Open-Meteo Weather Forecast API",
      url: "https://open-meteo.com/",
      domain: "open-meteo.com",
      snippet: "实时天气、地理编码和气象预报数据来源。",
    },
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
    data: {
      type: "exchange_rate",
      title: `${base.toUpperCase()} / ${target} 汇率`,
      subtitle: data.provider || "实时汇率",
      timestamp: data.time_last_update_utc || new Date().toISOString(),
      metrics: [
        { label: "来源币种", value: base.toUpperCase() },
        { label: "目标币种", value: target },
        { label: "汇率", value: String(rate) },
      ],
    },
    source: {
      title: "ExchangeRate-API Open Endpoint",
      url: "https://open.er-api.com/",
      domain: "open.er-api.com",
      snippet: "实时外汇汇率数据来源。",
    },
  };
}

async function getStockData(symbol) {
  const cleanSymbol = String(symbol || "").trim().toUpperCase();
  if (!cleanSymbol) throw new Error("stock symbol missing");
  const url = `https://query1.finance.yahoo.com/v8/finance/chart/${encodeURIComponent(cleanSymbol)}?range=1d&interval=1m`;
  const res = await fetchWithTimeout(url, { method: "GET" }, 12000);
  if (!res.ok) throw new Error(`stock api ${res.status}`);
  const json = await res.json();
  const result = json?.chart?.result?.[0];
  const meta = result?.meta || {};
  const quote = result?.indicators?.quote?.[0] || {};
  const closes = Array.isArray(quote.close) ? quote.close.filter((v) => typeof v === "number") : [];
  const latest = closes.length ? closes[closes.length - 1] : meta.regularMarketPrice;
  const previous = meta.chartPreviousClose || meta.previousClose;
  const change = typeof latest === "number" && typeof previous === "number" ? latest - previous : null;
  const changePercent = change !== null && previous ? (change / previous) * 100 : null;

  return {
    data: {
      type: "stock",
      title: `${cleanSymbol} 股票行情`,
      subtitle: meta.exchangeName || meta.fullExchangeName || "Yahoo Finance",
      timestamp: new Date().toISOString(),
      metrics: [
        { label: "代码", value: cleanSymbol },
        { label: "价格", value: latest !== undefined ? String(Number(latest).toFixed(3)) : "--", unit: meta.currency || "" },
        { label: "涨跌", value: change !== null ? String(change.toFixed(3)) : "--" },
        { label: "涨跌幅", value: changePercent !== null ? `${changePercent.toFixed(2)}%` : "--" },
      ],
      rawText: "股票数据来自 Yahoo Finance chart 接口。行情可能有延迟，仅供信息参考。",
    },
    source: {
      title: "Yahoo Finance Chart API",
      url: `https://finance.yahoo.com/quote/${encodeURIComponent(cleanSymbol)}`,
      domain: "finance.yahoo.com",
      snippet: "股票、ETF、指数等市场行情来源。",
    },
  };
}

async function getStructuredDataWithSource(intent) {
  if (!intent) return { structuredData: null, structuredSource: null };

  if (intent.type === "weather") {
    const result = await getWeatherData(intent.query);
    return { structuredData: result.data, structuredSource: result.source };
  }

  if (intent.type === "exchange_rate") {
    const result = await getExchangeRateData(intent.from, intent.to);
    return { structuredData: result.data, structuredSource: result.source };
  }

  if (intent.type === "stock") {
    const result = await getStockData(intent.symbol);
    return { structuredData: result.data, structuredSource: result.source };
  }

  return { structuredData: null, structuredSource: null };
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

function dedupeSources(sources) {
  const seen = new Set();
  const clean = [];
  for (const source of sources) {
    const url = String(source?.url || "");
    const key = url || String(source?.title || "");
    if (!key || seen.has(key)) continue;
    seen.add(key);
    clean.push(source);
  }
  return clean.slice(0, 6);
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
        version: "qwen-deepseek-cn-web-data-v2",
        features: ["qwen", "deepseek", "web_search_sources", "weather", "exchange_rate", "stock"],
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
        version: "qwen-deepseek-cn-web-data-v2",
      });
    }

    const forceSearch = isForceWebSearch(body);
    const structuredIntent = forceSearch ? detectStructuredIntent(prompt) : null;

    let structuredData = null;
    let structuredSource = null;
    let structuredError = null;
    if (structuredIntent) {
      try {
        const structured = await getStructuredDataWithSource(structuredIntent);
        structuredData = structured.structuredData;
        structuredSource = structured.structuredSource;
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

    if (structuredSource) {
      sources = [structuredSource, ...sources];
      searchProvider = searchProvider || structuredSource.domain;
    }
    sources = dedupeSources(sources);

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
      structuredUsed: Boolean(structuredData),
      searchProvider,
      searchError,
      sources,
      structuredData,
      structuredError,
      version: "qwen-deepseek-cn-web-data-v2",
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

const http = require("http");
const fs = require("fs");

const PORT = Number(process.env.PORT || process.env.FC_SERVER_PORT || 9000);
const REQUEST_TIMEOUT_MS = Number(process.env.PROVIDER_TIMEOUT_MS || 30000);
const WORKER_VERSION = "qwen-deepseek-cn-web-data-v3-aliyun-gui-plus";
const MAX_BODY_BYTES = Number(process.env.MAX_BODY_BYTES || 16 * 1024 * 1024);

const AGENT_GUI_PROVIDER = normalizeProviderName(process.env.AGENT_GUI_PROVIDER || process.env.GUI_PROVIDER || "qwen_omni");
const AGENT_GUI_PROVIDER_FALLBACK_TO_QWEN = String(process.env.AGENT_GUI_PROVIDER_FALLBACK_TO_QWEN || "true").toLowerCase() !== "false";
const ALIYUN_GUI_BASE_URL = String(process.env.ALIYUN_GUI_BASE_URL || "https://dashscope.aliyuncs.com/compatible-mode/v1").trim();
const ALIYUN_GUI_MODEL = String(process.env.ALIYUN_GUI_MODEL || "gui-plus-2026-02-26").trim();
const ALIYUN_GUI_TIMEOUT_MS = Number(process.env.ALIYUN_GUI_TIMEOUT_MS || 15000);
const ALIYUN_GUI_MAX_TOKENS = Number(process.env.ALIYUN_GUI_MAX_TOKENS || 512);
const AGENT_ACTION_BATCH_MAX = Number(process.env.AGENT_ACTION_BATCH_MAX || 1);

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
      if (raw.length > MAX_BODY_BYTES) {
        reject(new Error("body_too_large"));
        req.destroy();
      }
    });
    req.on("end", () => {
      try { resolve(raw ? JSON.parse(raw) : {}); } catch { reject(new Error("invalid_json")); }
    });
    req.on("error", reject);
  });
}

async function fetchWithTimeout(url, options = {}, timeoutMs = REQUEST_TIMEOUT_MS) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try { return await fetch(url, { ...options, signal: controller.signal }); }
  finally { clearTimeout(timer); }
}

function latestUserText(messages) {
  if (!Array.isArray(messages)) return "";
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    const item = messages[i] || {};
    const role = String(item.role || "").toLowerCase();
    const content = typeof item.content === "string" ? item.content : item.text;
    if (role === "user" && content && String(content).trim()) return String(content).trim();
  }
  return "";
}

function normalizeMessages(input, fallbackText) {
  const clean = [];
  for (const item of (Array.isArray(input) ? input : []).slice(-18)) {
    const roleRaw = String(item?.role || "").toLowerCase().trim();
    const role = roleRaw === "assistant" ? "assistant" : roleRaw === "user" ? "user" : "";
    const content = typeof item?.content === "string" ? item.content : typeof item?.text === "string" ? item.text : "";
    const text = content.trim();
    if (role && text) clean.push({ role, content: text });
  }
  while (clean.length && clean[0].role !== "user") clean.shift();
  const fallback = String(fallbackText || "").trim();
  if (!clean.length && fallback) clean.push({ role: "user", content: fallback });
  return clean.slice(-16);
}

function hasAny(text, keywords) { return keywords.some((keyword) => text.includes(keyword)); }

function routeAuto(prompt) {
  const text = String(prompt || "").toLowerCase();
  if (hasAny(text, ["推理", "证明", "数学", "计算", "建模", "电路", "方案", "架构", "为什么", "分析", "优化", "极限", "偏导", "积分", "仿真"])) return "deepseek_v4";
  return "qwen";
}

function resolveModel(modelPref, prompt) {
  const pref = String(modelPref || "auto").toLowerCase().trim();
  if (pref === "auto") return routeAuto(prompt);
  if (["qwen", "qwen_max", "qwen-max", "qwen_plus", "qwen-plus", "kimi"].includes(pref) || pref.startsWith("qwen")) return "qwen";
  if (["deepseek", "deepseek_v4", "deepseek-v4", "deepseek_v4_pro", "deepseek-v4-pro"].includes(pref)) return "deepseek_v4";
  return "unsupported";
}

async function callOpenAICompatible(base, key, model, messages, name, options = {}) {
  if (!key) throw new Error(`${name} key missing`);
  if (!base) throw new Error(`${name} base url missing`);
  if (!model) throw new Error(`${name} model missing`);
  const endpoint = `${String(base).replace(/\/+$/g, "")}/chat/completions`;
  const r = await fetchWithTimeout(endpoint, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${key}` },
    body: JSON.stringify({
      model,
      messages,
      temperature: options.temperature ?? 0.1,
      max_tokens: options.maxTokens ?? 1200,
      stream: false,
      response_format: options.responseFormat,
    }),
  }, options.timeoutMs || REQUEST_TIMEOUT_MS);
  const t = await r.text();
  let data = null;
  try { data = t ? JSON.parse(t) : null; } catch {}
  if (!r.ok) {
    const message = data?.error?.message || data?.message || t.slice(0, 240) || `${name} HTTP ${r.status}`;
    throw new Error(`${name} HTTP ${r.status}: ${sanitizeProviderError(message)}`);
  }
  const reply = data?.choices?.[0]?.message?.content || data?.choices?.[0]?.text || data?.reply || "";
  if (!String(reply).trim()) throw new Error(`${name} empty response`);
  return String(reply).trim();
}

function isForceWebSearch(body) {
  const mode = String(body.webSearchMode || body.searchMode || body.webSearch?.mode || "").toLowerCase();
  return Boolean(body.onlineEnabled || body.searchEnabled || body.forceWebSearch || body.webSearch?.force || mode === "force");
}

function detectStructuredIntent(prompt) {
  const text = String(prompt || "").trim();
  if (/(天气|气温|温度|下雨|降雨|weather)/i.test(text)) return { type: "weather", query: extractLocation(text) || "杭州" };
  if (/(汇率|兑换|兑|exchange rate|currency)/i.test(text)) { const pair = extractCurrencyPair(text); return { type: "exchange_rate", from: pair.from, to: pair.to }; }
  if (/(股价|股票|行情|stock|price|nasdaq|nyse|a股|港股|美股)/i.test(text)) return { type: "stock", symbol: normalizeStockSymbol(extractStockSymbol(text), text) };
  return null;
}

function extractLocation(text) {
  return String(text || "").replace(/今天|现在|实时|当前|查询|一下|请问|帮我看看|怎么样|多少/g, "").replace(/天气|气温|温度|下雨|降雨|weather/gi, "").replace(/[，。！？?\s]/g, "").trim().slice(0, 24);
}

function extractCurrencyPair(text) {
  const upper = String(text || "").toUpperCase();
  const codes = upper.match(/\b[A-Z]{3}\b/g) || [];
  const cnMap = [["美元", "USD"], ["人民币", "CNY"], ["日元", "JPY"], ["欧元", "EUR"], ["英镑", "GBP"], ["港币", "HKD"], ["港元", "HKD"], ["新币", "SGD"], ["新加坡元", "SGD"]];
  const found = [];
  for (const [cn, code] of cnMap) if (text.includes(cn)) found.push(code);
  const merged = [...codes, ...found];
  return { from: merged[0] || "USD", to: merged[1] || "CNY" };
}

function extractStockSymbol(text) {
  const upper = String(text || "").toUpperCase();
  const known = [["苹果", "AAPL"], ["特斯拉", "TSLA"], ["英伟达", "NVDA"], ["微软", "MSFT"], ["谷歌", "GOOGL"], ["亚马逊", "AMZN"], ["腾讯", "0700.HK"], ["阿里", "BABA"], ["贵州茅台", "600519.SS"], ["宁德时代", "300750.SZ"], ["比亚迪", "002594.SZ"]];
  for (const [name, symbol] of known) if (text.includes(name)) return symbol;
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
  if (/^\d{3,5}$/.test(clean) && /港股|港股行情|港股股价|HK/i.test(rawText)) return clean.padStart(4, "0") + ".HK";
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
  const geoRes = await fetchWithTimeout(`https://geocoding-api.open-meteo.com/v1/search?name=${query}&count=1&language=zh&format=json`, { method: "GET" }, 12000);
  if (!geoRes.ok) throw new Error(`weather geocode ${geoRes.status}`);
  const geo = await geoRes.json();
  const place = geo?.results?.[0];
  if (!place) throw new Error(`weather location not found: ${location}`);
  const weatherRes = await fetchWithTimeout(`https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto`, { method: "GET" }, 12000);
  if (!weatherRes.ok) throw new Error(`weather api ${weatherRes.status}`);
  const weather = await weatherRes.json();
  const current = weather.current || {};
  return {
    data: { type: "weather", title: `${place.name || location}天气`, subtitle: [place.admin1, place.country].filter(Boolean).join(" · "), timestamp: current.time || new Date().toISOString(), metrics: [
      { label: "温度", value: String(current.temperature_2m ?? "--"), unit: weather.current_units?.temperature_2m || "°C" },
      { label: "天气", value: weatherCodeLabel(current.weather_code) },
      { label: "湿度", value: String(current.relative_humidity_2m ?? "--"), unit: weather.current_units?.relative_humidity_2m || "%" },
      { label: "风速", value: String(current.wind_speed_10m ?? "--"), unit: weather.current_units?.wind_speed_10m || "km/h" },
    ], rawText: "天气数据来自 Open-Meteo 实时接口。" },
    source: { title: "Open-Meteo Weather Forecast API", url: "https://open-meteo.com/", domain: "open-meteo.com", snippet: "实时天气、地理编码和气象预报数据来源。" },
  };
}

async function getExchangeRateData(from, to) {
  const base = encodeURIComponent(from || "USD");
  const target = String(to || "CNY").toUpperCase();
  const res = await fetchWithTimeout(`https://open.er-api.com/v6/latest/${base}`, { method: "GET" }, 12000);
  if (!res.ok) throw new Error(`exchange api ${res.status}`);
  const data = await res.json();
  const rate = data?.rates?.[target];
  if (!rate) throw new Error(`exchange rate not found: ${base}/${target}`);
  return {
    data: { type: "exchange_rate", title: `${base.toUpperCase()} / ${target} 汇率`, subtitle: data.provider || "实时汇率", timestamp: data.time_last_update_utc || new Date().toISOString(), metrics: [
      { label: "来源币种", value: base.toUpperCase() }, { label: "目标币种", value: target }, { label: "汇率", value: String(rate) },
    ] },
    source: { title: "ExchangeRate-API Open Endpoint", url: "https://open.er-api.com/", domain: "open.er-api.com", snippet: "实时外汇汇率数据来源。" },
  };
}

async function getStockData(symbol) {
  const cleanSymbol = String(symbol || "").trim().toUpperCase();
  if (!cleanSymbol) throw new Error("stock symbol missing");
  const res = await fetchWithTimeout(`https://query1.finance.yahoo.com/v8/finance/chart/${encodeURIComponent(cleanSymbol)}?range=1d&interval=1m`, { method: "GET" }, 12000);
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
    data: { type: "stock", title: `${cleanSymbol} 股票行情`, subtitle: meta.exchangeName || meta.fullExchangeName || "Yahoo Finance", timestamp: new Date().toISOString(), metrics: [
      { label: "代码", value: cleanSymbol },
      { label: "价格", value: latest !== undefined ? String(Number(latest).toFixed(3)) : "--", unit: meta.currency || "" },
      { label: "涨跌", value: change !== null ? String(change.toFixed(3)) : "--" },
      { label: "涨跌幅", value: changePercent !== null ? `${changePercent.toFixed(2)}%` : "--" },
    ], rawText: "股票数据来自 Yahoo Finance chart 接口。行情可能有延迟，仅供信息参考。" },
    source: { title: "Yahoo Finance Chart API", url: `https://finance.yahoo.com/quote/${encodeURIComponent(cleanSymbol)}`, domain: "finance.yahoo.com", snippet: "股票、ETF、指数等市场行情来源。" },
  };
}

async function getStructuredDataWithSource(intent) {
  if (!intent) return { structuredData: null, structuredSource: null };
  if (intent.type === "weather") { const result = await getWeatherData(intent.query); return { structuredData: result.data, structuredSource: result.source }; }
  if (intent.type === "exchange_rate") { const result = await getExchangeRateData(intent.from, intent.to); return { structuredData: result.data, structuredSource: result.source }; }
  if (intent.type === "stock") { const result = await getStockData(intent.symbol); return { structuredData: result.data, structuredSource: result.source }; }
  return { structuredData: null, structuredSource: null };
}

async function tavilySearch(query) {
  const key = process.env.TAVILY_API_KEY;
  if (!key) return { sources: [], provider: null };
  const res = await fetchWithTimeout("https://api.tavily.com/search", {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ api_key: key, query, search_depth: "basic", include_answer: false, include_raw_content: false, max_results: 5 }),
  }, 15000);
  const text = await res.text();
  if (!res.ok) throw new Error(`tavily ${res.status} ${text.slice(0, 160)}`);
  const data = JSON.parse(text);
  return { sources: (Array.isArray(data.results) ? data.results : []).map((item) => {
    const url = String(item.url || "");
    return { title: String(item.title || url || "搜索来源"), url, domain: url.replace(/^https?:\/\//, "").split("/")[0], snippet: String(item.content || item.snippet || "").slice(0, 360), publishedAt: item.published_date || item.publishedAt || "" };
  }), provider: "tavily" };
}

function dedupeSources(sources) {
  const seen = new Set();
  const clean = [];
  for (const source of sources) { const key = String(source?.url || source?.title || ""); if (key && !seen.has(key)) { seen.add(key); clean.push(source); } }
  return clean.slice(0, 6);
}

function buildMessages(bodyMessages, prompt, structuredData, sources) {
  const messages = normalizeMessages(bodyMessages, prompt);
  const contextBlocks = [];
  if (structuredData) contextBlocks.push(`结构化实时数据：\n${JSON.stringify(structuredData, null, 2)}`);
  if (sources.length) contextBlocks.push(`联网搜索资料：\n${sources.map((s, i) => `[${i + 1}] ${s.title}\n${s.url}\n${s.snippet}`).join("\n\n")}`);
  const system = ["你是可靠、清晰、简洁的中文助手。", "如果提供了结构化实时数据或联网搜索资料，必须优先基于这些资料回答。", "不要编造来源；无法确认时要说明不确定。"];
  if (contextBlocks.length) system.push(contextBlocks.join("\n\n"));
  return [{ role: "system", content: system.join("\n") }, ...messages];
}

function normalizeProviderName(value) {
  const raw = String(value || "qwen_omni").trim().toLowerCase().replace(/[-\s]+/g, "_");
  if (["aliyun_gui_plus", "gui_plus", "bailian_gui_plus"].includes(raw)) return "aliyun_gui_plus";
  if (["qwen", "qwen_omni", "qwen_vision", "omni", "default"].includes(raw)) return "qwen_omni";
  if (["showui", "show_ui"].includes(raw)) return "showui";
  if (["external_http", "http", "custom"].includes(raw)) return "external_http";
  return raw;
}

function isAgentModeRequest(body) {
  const intent = String(body?.intent || body?.action || body?.type || "").toLowerCase().replace(/-/g, "_");
  return Boolean(intent === "agent_step" || body?.agentMode === true || body?.computerUseMode === true || body?.screenSnapshot);
}

function supportedAgentStepsFromBody(body) {
  const raw = Array.isArray(body?.supportedAgentSteps) ? body.supportedAgentSteps : [];
  const clean = raw.map((item) => String(item || "").toLowerCase().replace(/-/g, "_")).filter(Boolean);
  return clean.length ? clean : ["tap_xy", "need_user_help"];
}

function normalizeAgentScreenshot(body) {
  const top = body?.screenshot && typeof body.screenshot === "object" ? body.screenshot : {};
  const visual = body?.screenSnapshot?.visual && typeof body.screenSnapshot.visual === "object" ? body.screenSnapshot.visual : {};
  const base64Raw = top.base64 || top.base64Data || top.imageBase64 || top.data || visual.base64Jpeg || visual.base64 || visual.base64Data || "";
  const base64 = String(base64Raw || "").replace(/^data:image\/[a-zA-Z0-9.+-]+;base64,/, "").trim();
  if (!base64) return { hasImage: false, mimeType: "image/jpeg", base64: "", width: 0, height: 0, displayWidth: 0, displayHeight: 0 };
  const width = Number(top.width || visual.width) || 0;
  const height = Number(top.height || visual.height) || 0;
  const displayWidth = Number(top.displayWidth || top.originalWidth || top.screenWidth || visual.displayWidth || visual.originalWidth || visual.screenWidth) || width;
  const displayHeight = Number(top.displayHeight || top.originalHeight || top.screenHeight || visual.displayHeight || visual.originalHeight || visual.screenHeight) || height;
  return { hasImage: true, mimeType: String(top.mimeType || visual.mimeType || "image/jpeg"), base64, width, height, displayWidth, displayHeight };
}

function compactNodesForPrompt(snapshot) {
  const nodes = [
    ...(Array.isArray(snapshot?.clickableNodes) ? snapshot.clickableNodes : []),
    ...(Array.isArray(snapshot?.inputNodes) ? snapshot.inputNodes : []),
    ...(Array.isArray(snapshot?.scrollableNodes) ? snapshot.scrollableNodes : []),
  ];
  return nodes.slice(0, 28).map((node) => ({
    id: safeText(node?.id, 32), text: safeText(node?.text || node?.label, 80), bounds: safeText(node?.bounds, 48),
    clickable: Boolean(node?.clickable), editable: Boolean(node?.editable), scrollable: Boolean(node?.scrollable),
  })).filter((node) => node.text || node.bounds);
}

function buildGuiPlusPrompt(goal, snapshot, screenshotInfo, supportedSteps) {
  const nodes = compactNodesForPrompt(snapshot);
  return [
    "你是一个手机 GUI 操作定位模型。请根据截图和任务目标，返回下一步最安全的 GUI 操作。",
    "必须只输出 JSON，不要输出 Markdown，不要解释。",
    "坐标使用相对整张截图的 0-1 归一化坐标，左上角为 (0,0)，右下角为 (1,1)。",
    "第一阶段只允许动作：tap_xy、need_user_help。不要返回 swipe/input/back/home。",
    "输出格式：{\"action\":\"tap_xy\",\"x\":0.0,\"y\":0.0,\"confidence\":0.0,\"reason\":\"简短原因\"} 或 {\"action\":\"need_user_help\",\"confidence\":0.0,\"reason\":\"原因\"}",
    "不要点击悬浮窗、不要点击状态栏、不要点击不确定位置。如果目标按钮被遮挡或截图中不存在，返回 need_user_help。",
    `任务目标：${safeText(goal, 240)}`,
    `截图信息：image=${screenshotInfo.width}x${screenshotInfo.height}, display=${screenshotInfo.displayWidth}x${screenshotInfo.displayHeight}`,
    `后端支持动作：${supportedSteps.join(",")}`,
    `可选节点信息：${JSON.stringify(nodes)}`,
  ].join("\n");
}

async function callAliyunGuiPlus(goal, snapshot, screenshotInfo, supportedSteps) {
  const key = String(process.env.ALIYUN_GUI_API_KEY || process.env.QWEN_API_KEY || "").trim();
  if (!key) throw new Error("ALIYUN_GUI_API_KEY missing and QWEN_API_KEY fallback missing");
  if (!screenshotInfo.hasImage) throw new Error("screenshot missing");

  // 阿里云百炼 GUI Plus 这里使用 OpenAI-compatible /chat/completions 调用格式：
  // POST {ALIYUN_GUI_BASE_URL}/chat/completions, model=gui-plus-2026-02-26, content=[text,image_url]。
  // 如果控制台后续给出 DashScope 原生专用接口，只需要替换本函数，不影响下游 compact action 协议。
  const prompt = buildGuiPlusPrompt(goal, snapshot, screenshotInfo, supportedSteps);
  const startedAt = Date.now();
  const raw = await callOpenAICompatible(ALIYUN_GUI_BASE_URL, key, ALIYUN_GUI_MODEL, [
    { role: "user", content: [
      { type: "text", text: prompt },
      { type: "image_url", image_url: { url: `data:${screenshotInfo.mimeType};base64,${screenshotInfo.base64}` } },
    ] },
  ], "Aliyun GUI Plus", { temperature: 0, maxTokens: ALIYUN_GUI_MAX_TOKENS, timeoutMs: ALIYUN_GUI_TIMEOUT_MS });
  const compact = normalizeGuiProviderOutput(raw, screenshotInfo, "aliyun_gui_plus");
  logGuiProviderCall({ provider: "aliyun_gui_plus", model: ALIYUN_GUI_MODEL, screenshotInfo, elapsedMs: Date.now() - startedAt, compact });
  return compact;
}

async function callQwenVisionGuiFallback(goal, snapshot, screenshotInfo, supportedSteps, reasonTag = "fallback") {
  const key = String(process.env.QWEN_API_KEY || "").trim();
  const base = String(process.env.QWEN_BASE_URL || "https://dashscope.aliyuncs.com/compatible-mode/v1").trim();
  const model = String(process.env.QWEN_VISION_MODEL || "qwen3.5-omni-plus-2026-03-15").trim();
  if (!key) throw new Error("QWEN_API_KEY missing for fallback");
  if (!screenshotInfo.hasImage) throw new Error("screenshot missing");
  const prompt = buildGuiPlusPrompt(goal, snapshot, screenshotInfo, supportedSteps);
  const startedAt = Date.now();
  const raw = await callOpenAICompatible(base, key, model, [
    { role: "user", content: [
      { type: "text", text: `${prompt}\n你是兜底视觉模型，请严格返回同样 JSON。` },
      { type: "image_url", image_url: { url: `data:${screenshotInfo.mimeType};base64,${screenshotInfo.base64}` } },
    ] },
  ], "Qwen Vision GUI Fallback", { temperature: 0, maxTokens: Math.min(ALIYUN_GUI_MAX_TOKENS, 360), timeoutMs: Math.min(ALIYUN_GUI_TIMEOUT_MS, 12000) });
  const compact = normalizeGuiProviderOutput(raw, screenshotInfo, `qwen_vision_${reasonTag}`);
  logGuiProviderCall({ provider: "qwen_vision_fallback", model, screenshotInfo, elapsedMs: Date.now() - startedAt, compact });
  return compact;
}

async function resolveGuiProviderPlan(goal, snapshot, screenshotInfo, supportedSteps) {
  if (AGENT_GUI_PROVIDER === "aliyun_gui_plus") {
    try { return await callAliyunGuiPlus(goal, snapshot, screenshotInfo, supportedSteps); }
    catch (error) {
      const message = readableGuiError(error);
      console.warn(`[agent-gui] provider=aliyun_gui_plus error=${message}`);
      if (AGENT_GUI_PROVIDER_FALLBACK_TO_QWEN) {
        try { return await callQwenVisionGuiFallback(goal, snapshot, screenshotInfo, supportedSteps, "after_gui_plus_error"); }
        catch (fallbackError) { return compactNeedUserHelp(goal, `GUI Plus 失败且 Qwen 兜底也失败：${readableGuiError(fallbackError)}`, "qwen_fallback_failed", String(error?.message || "")); }
      }
      return compactNeedUserHelp(goal, `GUI Plus 调用失败：${message}`, "aliyun_gui_plus_failed", String(error?.message || ""));
    }
  }

  try { return await callQwenVisionGuiFallback(goal, snapshot, screenshotInfo, supportedSteps, "default_provider"); }
  catch (error) { return compactNeedUserHelp(goal, `GUI provider 不可用：${readableGuiError(error)}`, "provider_unavailable", String(error?.message || "")); }
}

function extractJsonCandidate(text) {
  const raw = String(text || "").trim();
  if (!raw) return null;
  const fenced = raw.match(/```(?:json)?\s*([\s\S]*?)```/i);
  const candidate = fenced ? fenced[1].trim() : raw;
  try { return JSON.parse(candidate); } catch {}
  const objectMatch = candidate.match(/\{[\s\S]*\}/);
  if (objectMatch) { try { return JSON.parse(objectMatch[0]); } catch {} }
  const arrayMatch = candidate.match(/\[\s*-?\d+(?:\.\d+)?\s*,\s*-?\d+(?:\.\d+)?\s*\]/);
  if (arrayMatch) { try { return JSON.parse(arrayMatch[0]); } catch {} }
  return null;
}

function normalizeGuiProviderOutput(rawOutput, screenshotInfo, provider) {
  const parsed = extractJsonCandidate(rawOutput);
  let action = "";
  let xRaw;
  let yRaw;
  let targetText = "点击目标";
  let confidence = 0;
  let reason = "";

  if (Array.isArray(parsed) && parsed.length >= 2) {
    action = "tap_xy"; xRaw = parsed[0]; yRaw = parsed[1]; confidence = 0.55; reason = "模型返回坐标数组。";
  } else if (parsed && typeof parsed === "object") {
    const nested = parsed.agentStep || parsed.step || parsed.actionStep || parsed.result || parsed;
    const coordinate = nested.coordinate || nested.coordinates || nested.point || nested.position || nested.xy || nested.center;
    action = normalizeActionType(nested.action || nested.type || nested.a || nested.operation || parsed.action || parsed.type);
    if (Array.isArray(coordinate) && coordinate.length >= 2) { xRaw = coordinate[0]; yRaw = coordinate[1]; }
    else if (coordinate && typeof coordinate === "object") { xRaw = coordinate.x ?? coordinate[0]; yRaw = coordinate.y ?? coordinate[1]; }
    xRaw = xRaw ?? nested.x ?? nested.targetX ?? nested.cx ?? nested.centerX ?? parsed.x ?? parsed.targetX;
    yRaw = yRaw ?? nested.y ?? nested.targetY ?? nested.cy ?? nested.centerY ?? parsed.y ?? parsed.targetY;
    targetText = safeText(nested.targetText || nested.label || nested.t || nested.text || parsed.t || "点击目标", 80);
    confidence = clamp01(nested.confidence ?? nested.score ?? nested.c ?? parsed.confidence ?? parsed.c ?? 0);
    reason = safeText(nested.reason || nested.evidence || nested.e || parsed.reason || parsed.e || "GUI Plus predicted clickable coordinate.", 180);
  } else {
    const numbers = String(rawOutput || "").match(/-?\d+(?:\.\d+)?/g)?.map(Number).filter(Number.isFinite) || [];
    const xyText = String(rawOutput || "").match(/[xy][:=：]\s*(-?\d+(?:\.\d+)?)/gi) || [];
    if (xyText.length >= 2 || numbers.length >= 2) { action = "tap_xy"; xRaw = numbers[0]; yRaw = numbers[1]; confidence = 0.35; reason = "从非 JSON 文本中提取到坐标，置信度较低。"; }
  }

  if (!action) action = "need_user_help";
  if (action !== "tap_xy") return compactNeedUserHelp(targetText, `第一阶段不自动执行 ${action}，已保守暂停。${reason || ""}`, provider, String(rawOutput || "").slice(0, 1200));

  const point = normalizePoint(xRaw, yRaw, screenshotInfo);
  if (!point) return compactNeedUserHelp(targetText, "模型没有给出可靠坐标，禁止猜测点击。", provider, String(rawOutput || "").slice(0, 1200));
  const safeConfidence = confidence > 0 ? confidence : 0.6;
  if (safeConfidence < 0.25) return compactNeedUserHelp(targetText, `坐标置信度过低：${safeConfidence.toFixed(2)}`, provider, String(rawOutput || "").slice(0, 1200));
  return { s: "p", a: "tap_xy", x: point.x, y: point.y, t: targetText || "点击目标", c: safeConfidence, e: reason || "GUI Plus predicted clickable coordinate.", raw: String(rawOutput || "").slice(0, 1200), provider };
}

function normalizeActionType(value) {
  const raw = String(value || "").toLowerCase().trim().replace(/[-\s]+/g, "_");
  if (["tap", "click", "press", "point", "coordinate_click", "coordinate_tap", "tap_point", "tap_xy"].includes(raw)) return "tap_xy";
  if (["input", "type", "enter_text", "input_text"].includes(raw)) return "input_text";
  if (["swipe", "scroll", "back", "home", "wait", "finish", "need_user_help"].includes(raw)) return raw;
  if (["none", "unknown", "uncertain", "ask_user", "need_help"].includes(raw)) return "need_user_help";
  return raw || "need_user_help";
}

function normalizePoint(rawX, rawY, screenshotInfo) {
  let x = Number(rawX);
  let y = Number(rawY);
  if (!Number.isFinite(x) || !Number.isFinite(y)) return null;
  const w = Number(screenshotInfo?.width) || Number(screenshotInfo?.displayWidth) || 0;
  const h = Number(screenshotInfo?.height) || Number(screenshotInfo?.displayHeight) || 0;
  const dw = Number(screenshotInfo?.displayWidth) || w;
  const dh = Number(screenshotInfo?.displayHeight) || h;
  if (x >= 0 && x <= 1 && y >= 0 && y <= 1) return { x: clamp01(x), y: clamp01(y), source: "normalized" };
  if (x >= 0 && x <= 100 && y >= 0 && y <= 100) return { x: clamp01(x / 100), y: clamp01(y / 100), source: "percent" };
  if (w > 1 && h > 1 && x >= 0 && x <= w + 24 && y >= 0 && y <= h + 24) return { x: clamp01(x / w), y: clamp01(y / h), source: "image_pixel" };
  if (dw > 1 && dh > 1 && x >= 0 && x <= dw + 24 && y >= 0 && y <= dh + 24) return { x: clamp01(x / dw), y: clamp01(y / dh), source: "display_pixel" };
  return null;
}

function compactNeedUserHelp(target, reason, provider = "unknown", raw = "") {
  return { s: "u", a: "need_user_help", x: null, y: null, t: safeText(target || "目标", 80), c: 0, e: safeText(reason || "无法可靠判断，需要用户帮助。", 220), raw: String(raw || "").slice(0, 1200), provider };
}

function compactToAgentResponse(compact, meta = {}) {
  const isTap = compact.a === "tap_xy" && Number.isFinite(compact.x) && Number.isFinite(compact.y);
  const agentStep = isTap
    ? { type: "tap_xy", x: compact.x, y: compact.y, targetText: compact.t, reason: compact.e, riskLevel: "low", requiresConfirmation: false }
    : { type: "need_user_help", targetText: compact.t, reason: compact.e, riskLevel: "low", requiresConfirmation: false };
  const agentState = { isComplete: false, expectedProgress: isTap, isWrong: false, confidence: Number(compact.c) || 0, reason: compact.e || "", nextHint: isTap ? "执行点击后重新截图复核。" : "需要用户协助或重新观察。" };
  return { ok: true, reply: isTap ? "已由 GUI Provider 规划下一步点击。" : "GUI Provider 无法可靠规划，已暂停。", agentStep, agentState, agentSteps: AGENT_ACTION_BATCH_MAX > 1 && isTap ? [agentStep] : [], compactAction: compact, source: "agent_step_gui_provider", model: meta.model || compact.provider || AGENT_GUI_PROVIDER, modelId: meta.model || compact.provider || AGENT_GUI_PROVIDER, modelLabel: meta.modelLabel || (compact.provider === "aliyun_gui_plus" ? "阿里云 GUI Plus" : "GUI Provider"), version: WORKER_VERSION, debug: meta.debug || {} };
}

async function handleAgentStepRequest(body, prompt) {
  const goal = safeText(body.agentGoal || body.goal || prompt, 240);
  const snapshot = body.screenSnapshot && typeof body.screenSnapshot === "object" ? body.screenSnapshot : {};
  const screenshotInfo = normalizeAgentScreenshot(body);
  const supportedSteps = supportedAgentStepsFromBody(body);
  if (!goal) return compactToAgentResponse(compactNeedUserHelp("目标", "empty_agent_goal", "local"), { debug: { reason: "empty_goal" } });
  if (!screenshotInfo.hasImage) return compactToAgentResponse(compactNeedUserHelp(goal, "Android 端没有提供截图，第一阶段不进行盲目 GUI 操作。", "local"), { debug: { hasScreenshot: false } });
  const compact = await resolveGuiProviderPlan(goal, snapshot, screenshotInfo, supportedSteps);
  return compactToAgentResponse(compact, {
    model: compact.provider === "aliyun_gui_plus" ? ALIYUN_GUI_MODEL : compact.provider,
    modelLabel: compact.provider === "aliyun_gui_plus" ? "阿里云 GUI Plus" : "Qwen 视觉兜底",
    debug: { provider: compact.provider, requestedProvider: AGENT_GUI_PROVIDER, image: `${screenshotInfo.width}x${screenshotInfo.height}`, display: `${screenshotInfo.displayWidth}x${screenshotInfo.displayHeight}`, action: compact.a, x: compact.x, y: compact.y },
  });
}

function logGuiProviderCall({ provider, model, screenshotInfo, elapsedMs, compact }) {
  const x = Number.isFinite(compact?.x) ? compact.x.toFixed(4) : "null";
  const y = Number.isFinite(compact?.y) ? compact.y.toFixed(4) : "null";
  console.log(`[agent-gui] provider=${provider} model=${model} image=${screenshotInfo.width}x${screenshotInfo.height} elapsedMs=${elapsedMs} action=${compact?.a || "unknown"} x=${x} y=${y}`);
}

function sanitizeProviderError(value) { return String(value || "").replace(/Bearer\s+[A-Za-z0-9_\-.]+/gi, "Bearer ***").slice(0, 300); }
function readableGuiError(error) {
  const text = sanitizeProviderError(error?.message || error || "unknown");
  if (/401|403|unauthorized|forbidden|无权限|权限/i.test(text)) return `API Key 或模型权限异常：${text}`;
  if (/timeout|aborted|超时/i.test(text)) return `timeout：${text}`;
  if (/content.?filter|safety|blocked|安全/i.test(text)) return `content filter：${text}`;
  return text;
}
function safeText(value, max = 120) { return String(value ?? "").replace(/\s+/g, " ").trim().slice(0, max); }
function clamp01(value) { const n = Number(value); if (!Number.isFinite(n)) return 0; return Math.max(0, Math.min(1, n)); }

const server = http.createServer(async (req, res) => {
  try {
    if (req.method === "OPTIONS") return sendJson(res, 204, {});
    if (req.method === "GET") return sendJson(res, 200, { ok: true, mode: "aliyun-fc-custom-runtime", version: WORKER_VERSION, features: ["qwen", "deepseek", "web_search_sources", "weather", "exchange_rate", "stock", "agent_step", "aliyun_gui_plus"], agentGuiProvider: AGENT_GUI_PROVIDER, aliyunGuiModel: ALIYUN_GUI_MODEL });
    if (req.method !== "POST") return sendJson(res, 405, { ok: false, error: "method_not_allowed" });

    const body = await readJsonBody(req);
    const prompt = body.message || body.prompt || body.text || body.content || latestUserText(body.messages) || body.agentGoal || body.goal || "";
    if (isAgentModeRequest(body)) return sendJson(res, 200, await handleAgentStepRequest(body, prompt));
    if (!prompt || !String(prompt).trim()) return sendJson(res, 400, { ok: false, error: "empty_prompt" });

    const modelPref = String(body.modelPreference || body.aiModelPreference || body.requestedModelPreference || body.modelId || body.model || "auto").toLowerCase().trim();
    const resolved = resolveModel(modelPref, prompt);
    if (resolved === "unsupported") return sendJson(res, 200, { ok: false, unsupportedModel: true, shouldFallback: true, code: "model_not_available", error: `CN gateway does not support model: ${modelPref}`, model: modelPref, version: WORKER_VERSION });

    const forceSearch = isForceWebSearch(body);
    const structuredIntent = forceSearch ? detectStructuredIntent(prompt) : null;
    let structuredData = null, structuredSource = null, structuredError = null;
    if (structuredIntent) {
      try { const structured = await getStructuredDataWithSource(structuredIntent); structuredData = structured.structuredData; structuredSource = structured.structuredSource; }
      catch (e) { structuredError = String(e.message || e); }
    }
    let sources = [], searchProvider = null, searchError = null;
    if (forceSearch) {
      try { const search = await tavilySearch(prompt); sources = search.sources; searchProvider = search.provider; }
      catch (e) { searchError = String(e.message || e); }
    }
    if (structuredSource) { sources = [structuredSource, ...sources]; searchProvider = searchProvider || structuredSource.domain; }
    sources = dedupeSources(sources);
    const messages = buildMessages(body.messages, prompt, structuredData, sources);
    const reply = resolved === "deepseek_v4"
      ? await callOpenAICompatible(process.env.DEEPSEEK_BASE_URL, process.env.DEEPSEEK_API_KEY, process.env.DEEPSEEK_MODEL, messages, "DeepSeek")
      : await callOpenAICompatible(process.env.QWEN_BASE_URL, process.env.QWEN_API_KEY, process.env.QWEN_MODEL, messages, "Qwen");
    return sendJson(res, 200, { ok: true, reply, source: resolved === "deepseek_v4" ? "deepseek" : "qwen", model: resolved, modelId: resolved, modelLabel: resolved === "deepseek_v4" ? "DeepSeek V4 Pro" : "Qwen Max", searchUsed: Boolean(forceSearch && sources.length), structuredUsed: Boolean(structuredData), searchProvider, searchError, sources, structuredData, structuredError, version: WORKER_VERSION });
  } catch (e) {
    return sendJson(res, 502, { ok: false, error: sanitizeProviderError(e.message || e), code: "provider_call_failed", version: WORKER_VERSION });
  }
});

if (require.main === module) {
  server.listen(PORT, () => console.log(`AI Ledger CN web-data server listening on ${PORT}`));
}

module.exports = { normalizeGuiProviderOutput, normalizePoint, compactToAgentResponse, compactNeedUserHelp, handleAgentStepRequest };

const ALLOWED_CATEGORIES = ["餐饮", "交通", "购物", "居住", "饮品", "工资", "礼物", "其他"];
const ALLOWED_ACTIONS = ["chat", "draft", "confirm_pending", "cancel_pending", "mobile_command"];
const ALLOWED_MOBILE_ACTIONS = ["set_alarm", "open_app", "navigate"];
const WORKER_VERSION = "2026-05-15-online-tools-1";

const TOOL_REGISTRY = [
  { name: "ledger.draft_records", title: "记账草稿" },
  { name: "mobile.set_alarm", title: "设置闹钟" },
  { name: "mobile.open_app", title: "打开应用" },
  { name: "mobile.navigate", title: "地图导航" },
  { name: "online.weather", title: "实时天气，无需 Key" },
  { name: "online.wikipedia", title: "百科摘要，无需 Key" },
  { name: "online.webpage", title: "网页链接读取，无需 Key" },
  { name: "online.search", title: "联网搜索，需要 Tavily / Brave / Serper Key" },
  { name: "utility.calculator", title: "计算器" },
  { name: "utility.datetime", title: "日期时间" },
];

const jsonHeaders = { "content-type": "application/json; charset=utf-8" };

export default {
  async fetch(request, env) {
    const corsHeaders = getCorsHeaders(request, env);
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders });

    if (request.method === "GET" && url.pathname === "/health") {
      const provider = env.GEMINI_API_KEY ? "gemini" : "workers_ai";
      const model = env.GEMINI_API_KEY ? (env.GEMINI_MODEL || "gemini-2.5-flash") : (env.AI_MODEL || "@cf/meta/llama-3.1-8b-instruct");
      return json({
        ok: true,
        worker: "ai-ledger-parser",
        version: WORKER_VERSION,
        provider,
        model,
        mode: "online_tools_plus_gemini_first",
        tools: TOOL_REGISTRY,
        hasGeminiKey: Boolean(env.GEMINI_API_KEY),
        hasAiBinding: Boolean(env.AI),
        searchProviders: {
          tavily: Boolean(env.TAVILY_API_KEY),
          brave: Boolean(env.BRAVE_SEARCH_API_KEY),
          serper: Boolean(env.SERPER_API_KEY),
        },
      }, 200, corsHeaders);
    }

    if (request.method !== "POST") return json({ error: "Method not allowed", code: "method_not_allowed", version: WORKER_VERSION }, 405, corsHeaders);

    let body;
    try { body = await request.json(); }
    catch { return json({ error: "Invalid JSON body", code: "invalid_json_body", version: WORKER_VERSION }, 400, corsHeaders); }

    const now = normalizeIsoDate(body?.now) || todayInTimeZone("Asia/Shanghai");
    const messages = normalizeMessages(body?.messages);
    const text = String(body?.text || "").trim();
    const pendingDraft = sanitizeRecords(body?.pendingDraft, now);
    const ledgerContext = sanitizeLedgerContext(body?.ledgerContext, now);
    const clientTools = sanitizeClientTools(body?.clientTools);
    const lastUserText = getLastUserText(messages, text);
    const conversation = messages.length ? buildConversation(messages) : lastUserText;

    if (!conversation) return json({ error: "messages or text is required", code: "missing_conversation", version: WORKER_VERSION }, 400, corsHeaders);
    if (conversation.length > 7000) return json({ error: "conversation is too long", code: "conversation_too_long", version: WORKER_VERSION }, 400, corsHeaders);

    const onlineReply = await tryOnlineToolReply({ text: lastUserText, env, now });
    if (onlineReply) return json({ ...onlineReply, version: WORKER_VERSION }, 200, corsHeaders);

    const deterministic = tryDeterministicReply({ lastUserText, pendingDraft, ledgerContext, now });
    if (deterministic) return json({ ...deterministic, source: "hybrid_rules", version: WORKER_VERSION }, 200, corsHeaders);

    const instructions = buildInstructions(now);
    const context = [
      `今天日期：${now}`,
      `待确认账单：${pendingDraft.length ? JSON.stringify(pendingDraft) : "无"}`,
      `账本上下文：${JSON.stringify(ledgerContext)}`,
      `客户端可执行工具：${clientTools.length ? JSON.stringify(clientTools) : "未上报"}`,
      `可用在线工具：天气、百科、网页读取、计算器、日期；联网搜索${hasSearchProvider(env) ? "已配置" : "未配置"}。`,
      `对话历史：\n${conversation}`,
      "请根据最后一条用户消息作答。",
    ].join("\n\n");

    let provider = "none";
    let raw = "";
    let providerError = null;

    if (env.GEMINI_API_KEY) {
      provider = "gemini";
      try { raw = await runGemini({ env, instructions, context }); }
      catch (error) { providerError = String(error?.message || error); }
    }

    if (!raw && env.AI) {
      provider = "workers_ai";
      try { raw = await runWorkersAI({ env, instructions, context }); }
      catch (error) { providerError = String(error?.message || error); }
    }

    if (!raw) {
      return json({
        reply: providerError ? `云端 AI 暂时不可用：${providerError.slice(0, 160)}` : "云端 AI 还没有配置成功。我现在仍可处理天气、百科、网页读取、计算、记账、闹钟、打开应用和导航。",
        action: "chat",
        records: [],
        mobileCommand: null,
        source: provider === "gemini" ? "gemini_error" : "missing_cloud_ai",
        version: WORKER_VERSION,
      }, 200, corsHeaders);
    }

    const parsed = parseAiJson(raw);
    if (!parsed) {
      return json({
        reply: cleanRawAiText(raw) || createSafeFallbackReply(lastUserText),
        action: "chat",
        records: [],
        mobileCommand: null,
        source: provider === "gemini" ? "gemini_text_fallback" : "workers_ai_text_fallback",
        version: WORKER_VERSION,
        repaired: true,
      }, 200, corsHeaders);
    }

    const records = sanitizeRecords(parsed.records, now);
    const mobileCommand = sanitizeMobileCommand(parsed.mobileCommand, now);
    const action = sanitizeAction(parsed.action, records, pendingDraft, mobileCommand);
    const reply = sanitizeReply(parsed.reply, action, records, mobileCommand);
    return json({ reply, action, records, mobileCommand, source: provider === "gemini" ? "gemini_ai" : "workers_ai", provider, version: WORKER_VERSION }, 200, corsHeaders);
  },
};

async function tryOnlineToolReply({ text, env, now }) {
  const value = String(text || "").trim();
  if (!value) return null;
  const url = extractUrl(value);
  if (url && /(总结|读取|看看|分析|网页|链接|这个)/u.test(value)) return readWebpageTool(url);
  const calc = tryCalculator(value);
  if (calc) return calc;
  const dateReply = tryDateTimeReply(value, now);
  if (dateReply) return dateReply;
  if (/(天气|下雨|气温|温度|风速|降雨|穿什么)/u.test(value)) return weatherTool(value);
  if (/(搜索|查一下|搜一下|最新|新闻|价格|官网|资料|联网查)/u.test(value)) return searchTool(value, env);
  if (/(百科|维基|介绍一下|是什么|是谁)/u.test(value) && value.length <= 60 && !/(你是谁|你叫什么|你是什么|你能做什么)/u.test(value)) {
    const topic = extractWikiTopic(value);
    if (topic) return wikiTool(topic);
  }
  return null;
}

async function weatherTool(text) {
  const location = extractLocation(text);
  if (!location) return { reply: "你想查哪里的天气？可以这样问：重庆今天会下雨吗、东京明天天气怎么样。", action: "chat", records: [], mobileCommand: null, source: "weather_tool" };
  try {
    const geo = await fetchJson(`https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(location)}&count=1&language=zh&format=json`);
    const place = geo?.results?.[0];
    if (!place) return { reply: `我没有找到“${location}”的天气位置。`, action: "chat", records: [], mobileCommand: null, source: "weather_tool" };
    const forecast = await fetchJson(`https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}&current=temperature_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=3&timezone=auto`);
    const c = forecast.current || {};
    const d = forecast.daily || {};
    const rain = d.precipitation_probability_max?.[0];
    const reply = `${place.name}${place.admin1 ? `（${place.admin1}）` : ""}当前约 ${round(c.temperature_2m)}℃，体感 ${round(c.apparent_temperature)}℃，${weatherCodeText(c.weather_code)}，风速约 ${round(c.wind_speed_10m)} km/h。今天气温大约 ${round(d.temperature_2m_min?.[0])}–${round(d.temperature_2m_max?.[0])}℃，最高降水概率约 ${rain ?? "未知"}%。${Number(rain) >= 50 || Number(c.precipitation) > 0 ? "出门建议带伞。" : "目前看降雨风险不算高。"}`;
    return { reply, action: "chat", records: [], mobileCommand: null, source: "weather_tool" };
  } catch (error) {
    return { reply: `天气查询失败：${String(error?.message || error).slice(0, 120)}`, action: "chat", records: [], mobileCommand: null, source: "weather_tool" };
  }
}

async function wikiTool(topic) {
  try {
    const search = await fetchJson(`https://zh.wikipedia.org/w/rest.php/v1/search/page?q=${encodeURIComponent(topic)}&limit=1`);
    const key = search?.pages?.[0]?.key;
    if (!key) return { reply: `没有找到“${topic}”的百科摘要。`, action: "chat", records: [], mobileCommand: null, source: "wiki_tool" };
    const summary = await fetchJson(`https://zh.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(key)}`);
    const extract = String(summary?.extract || "").trim();
    return { reply: extract ? `${summary.title || topic}：${extract.slice(0, 520)}` : `找到“${topic}”，但没有可用摘要。`, action: "chat", records: [], mobileCommand: null, source: "wiki_tool" };
  } catch (error) {
    return { reply: `百科查询失败：${String(error?.message || error).slice(0, 120)}`, action: "chat", records: [], mobileCommand: null, source: "wiki_tool" };
  }
}

async function readWebpageTool(url) {
  try {
    const response = await fetch(url, { headers: { "user-agent": "AI-Assistant-Worker/1.0" } });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const type = response.headers.get("content-type") || "";
    if (!/text\/html|text\/plain|application\/json/i.test(type)) throw new Error("暂不支持读取这种文件类型");
    const html = (await response.text()).slice(0, 120000);
    const title = extractTitle(html) || url;
    const text = stripHtml(html).replace(/\s+/g, " ").trim().slice(0, 1200);
    return { reply: text ? `我读取到了网页《${title}》。主要内容摘要：${text.slice(0, 520)}${text.length > 520 ? "……" : ""}` : `我打开了网页《${title}》，但没有提取到足够正文。`, action: "chat", records: [], mobileCommand: null, source: "webpage_tool" };
  } catch (error) {
    return { reply: `网页读取失败：${String(error?.message || error).slice(0, 120)}`, action: "chat", records: [], mobileCommand: null, source: "webpage_tool" };
  }
}

async function searchTool(text, env) {
  const query = cleanSearchQuery(text);
  if (!query) return null;
  try {
    if (env.TAVILY_API_KEY) return await tavilySearch(query, env.TAVILY_API_KEY);
    if (env.BRAVE_SEARCH_API_KEY) return await braveSearch(query, env.BRAVE_SEARCH_API_KEY);
    if (env.SERPER_API_KEY) return await serperSearch(query, env.SERPER_API_KEY);
    return { reply: "联网搜索工具还没配置 Key。现在我已经能查天气、百科和读取网页链接；如果要通用网页搜索，可以在 GitHub Secrets 里添加 TAVILY_API_KEY、BRAVE_SEARCH_API_KEY 或 SERPER_API_KEY。", action: "chat", records: [], mobileCommand: null, source: "search_not_configured" };
  } catch (error) {
    return { reply: `联网搜索失败：${String(error?.message || error).slice(0, 150)}`, action: "chat", records: [], mobileCommand: null, source: "web_search_tool" };
  }
}

async function tavilySearch(query, key) {
  const data = await fetchJson("https://api.tavily.com/search", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ api_key: key, query, search_depth: "basic", max_results: 5, include_answer: true }) });
  const results = (data?.results || []).slice(0, 5).map((r, i) => `${i + 1}. ${r.title || "结果"}：${r.content || r.url || ""}`).join("\n");
  return { reply: `${data?.answer ? `${data.answer}\n\n` : ""}搜索结果：\n${results || "没有找到结果。"}`, action: "chat", records: [], mobileCommand: null, source: "web_search_tool" };
}
async function braveSearch(query, key) {
  const data = await fetchJson(`https://api.search.brave.com/res/v1/web/search?q=${encodeURIComponent(query)}&count=5`, { headers: { accept: "application/json", "x-subscription-token": key } });
  const results = (data?.web?.results || []).slice(0, 5).map((r, i) => `${i + 1}. ${r.title || "结果"}：${r.description || r.url || ""}`).join("\n");
  return { reply: `搜索结果：\n${results || "没有找到结果。"}`, action: "chat", records: [], mobileCommand: null, source: "web_search_tool" };
}
async function serperSearch(query, key) {
  const data = await fetchJson("https://google.serper.dev/search", { method: "POST", headers: { "content-type": "application/json", "X-API-KEY": key }, body: JSON.stringify({ q: query, num: 5 }) });
  const results = (data?.organic || []).slice(0, 5).map((r, i) => `${i + 1}. ${r.title || "结果"}：${r.snippet || r.link || ""}`).join("\n");
  return { reply: `搜索结果：\n${results || "没有找到结果。"}`, action: "chat", records: [], mobileCommand: null, source: "web_search_tool" };
}

function tryCalculator(text) {
  if (!/(计算|算一下|等于|\+|\-|\*|×|\/|÷|\^)/u.test(text)) return null;
  const expr = String(text).replace(/计算|算一下|等于|是多少|等于多少|？|\?/gu, "").replace(/×/g, "*").replace(/÷/g, "/").replace(/\^/g, "**").trim();
  if (!/^[0-9+\-*/().\s*%]+$/.test(expr) || expr.length > 80) return null;
  try { const result = Function(`"use strict"; return (${expr})`)(); if (!Number.isFinite(result)) return null; return { reply: `${expr} = ${Number(result.toFixed(10))}`, action: "chat", records: [], mobileCommand: null, source: "calculator_tool" }; } catch { return null; }
}
function tryDateTimeReply(text, now) {
  if (!/(今天几号|今天星期几|现在日期|今天日期|今天是什么日子)/u.test(text)) return null;
  const d = new Date(`${now}T00:00:00+08:00`);
  const week = ["星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"][d.getDay()];
  return { reply: `今天是 ${now}，${week}。`, action: "chat", records: [], mobileCommand: null, source: "datetime_tool" };
}

async function runGemini({ env, instructions, context }) {
  const model = env.GEMINI_MODEL || "gemini-2.5-flash";
  const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(env.GEMINI_API_KEY)}`;
  const response = await fetch(endpoint, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ systemInstruction: { parts: [{ text: instructions }] }, contents: [{ role: "user", parts: [{ text: context }] }], generationConfig: { temperature: 0.18, maxOutputTokens: 900, responseMimeType: "application/json" } }) });
  const data = await response.json().catch(() => null);
  if (!response.ok) throw new Error(data?.error?.message || `Gemini HTTP ${response.status}`);
  return extractGeminiText(data);
}
async function runWorkersAI({ env, instructions, context }) {
  const result = await env.AI.run(env.AI_MODEL || "@cf/meta/llama-3.1-8b-instruct", { messages: [{ role: "system", content: instructions }, { role: "user", content: context }], temperature: 0.15, max_tokens: 700, response_format: { type: "json_schema", json_schema: buildResponseSchema() } });
  return extractWorkersAiText(result);
}
function extractGeminiText(data) { const parts = data?.candidates?.[0]?.content?.parts || []; const text = parts.map((part) => part?.text || "").join("\n").trim(); return text || JSON.stringify(data || {}); }
function getCorsHeaders(request, env) { const origin = request.headers.get("Origin") || ""; const allowed = String(env.ALLOWED_ORIGINS || "*").split(",").map((i) => i.trim()).filter(Boolean); const allowOrigin = allowed.includes("*") || allowed.includes(origin) ? origin || "*" : allowed[0] || "*"; return { ...jsonHeaders, "access-control-allow-origin": allowOrigin, "access-control-allow-methods": "GET, POST, OPTIONS", "access-control-allow-headers": "content-type", "vary": "Origin" }; }
function json(payload, status = 200, corsHeaders = {}) { return new Response(JSON.stringify(payload), { status, headers: { ...jsonHeaders, ...corsHeaders } }); }
async function fetchJson(url, options = {}, timeoutMs = 9000) { const controller = new AbortController(); const timer = setTimeout(() => controller.abort(), timeoutMs); try { const res = await fetch(url, { ...options, signal: controller.signal }); const data = await res.json().catch(() => null); if (!res.ok) throw new Error(data?.error?.message || `HTTP ${res.status}`); return data; } finally { clearTimeout(timer); } }
function buildResponseSchema() { return { type: "object", additionalProperties: false, properties: { reply: { type: "string" }, action: { type: "string", enum: ALLOWED_ACTIONS }, mobileCommand: { type: "object" }, records: { type: "array" } }, required: ["reply", "action", "records"] }; }
function buildInstructions(now) { return ["你是一个中文通用手机 AI 智能体。你能自然聊天、理解上下文，并在合适时调用工具。", "普通聊天就正常回答，不要强行记账或调用手机工具。复杂问题也要尽量回答，但保持简洁。", "如果用户需要实时信息，优先使用已提供的天气、百科、网页或搜索结果。", "如果用户明显要记账，使用 action=draft 并返回 records；缺金额时先追问。", "如果用户明显要设置闹钟、打开应用或导航，使用 action=mobile_command 并返回 mobileCommand。", "输出协议必须返回 JSON：{ reply, action, records, mobileCommand }。不要输出 Markdown，不要用代码块。", `今天日期是 ${now}。Tool Registry: ${JSON.stringify(TOOL_REGISTRY)}`].join("\n"); }
function getLastUserText(messages, fallbackText) { const last = [...messages].reverse().find((m) => m.role === "user"); return String(last?.content || fallbackText || "").trim(); }
function tryDeterministicReply({ lastUserText, pendingDraft, ledgerContext, now }) { const text = String(lastUserText || "").trim(); if (!text) return null; if (pendingDraft.length && /^(好|好的|对|确认|保存|记上|就这样)$/u.test(text)) return { reply: "好的，已帮你记上。", action: "confirm_pending", records: [], mobileCommand: null }; if (pendingDraft.length && /^(算了|不用了|先别记|取消)$/u.test(text)) return { reply: "好的，这次先不保存。", action: "cancel_pending", records: [], mobileCommand: null }; if (/^(你好|您好|嗨|哈喽|在吗|hello|hi)$/iu.test(text)) return { reply: "我在。你可以直接说任务，比如记账、设闹钟、打开应用、导航、查天气、读网页，也可以正常聊天。", action: "chat", records: [], mobileCommand: null }; const mobileCommand = parseMobileCommand(text, now); if (mobileCommand) return { reply: createMobileReply(mobileCommand), action: "mobile_command", records: [], mobileCommand }; const simpleRecords = parseSimpleRecords(text, now); if (simpleRecords.length) return { reply: `我先整理出 ${simpleRecords.length} 笔待确认账单，你回复“好”我就帮你保存。`, action: "draft", records: simpleRecords, mobileCommand: null }; const categoryQuery = extractCategoryQuery(text); if (categoryQuery) { const total = sumCategoryThisMonth(ledgerContext.recentRecords, categoryQuery, now); return { reply: `你这个月${categoryQuery}一共花了 ¥${total.toFixed(2)}。`, action: "chat", records: [], mobileCommand: null }; } return null; }
function parseMobileCommand(text, now) { return parseAlarmCommand(text, now) || parseNavigationCommand(text) || parseOpenAppCommand(text); }
function parseAlarmCommand(text, now) { if (!/(闹钟|叫我|提醒我|提醒一下|叫醒|起床)/u.test(text)) return null; const m = text.match(/(\d{1,2})(?:[:：点时](\d{1,2})?分?)?/u); if (!m) return null; let hour = Number(m[1]); const minute = Number(m[2] || 0); if (!Number.isInteger(hour) || !Number.isInteger(minute) || minute < 0 || minute > 59) return null; hour = normalizeMeridiem(hour, text); if (hour < 0 || hour > 23) return null; const date = /后天/u.test(text) ? shiftDate(now, 2) : /明天|明早|明晚/u.test(text) ? shiftDate(now, 1) : now; const label = (/起床|叫醒/u.test(text) ? "起床" : "提醒"); return { type: "set_alarm", title: "设置闹钟", summary: `${date} ${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`, params: { date, hour, minute, label } }; }
function normalizeMeridiem(hour, text) { if (/下午|晚上|傍晚|今晚/u.test(text) && hour < 12) return hour + 12; if (/中午/u.test(text) && hour < 11) return hour + 12; if (/凌晨|早上|上午|明早|明天早上/u.test(text) && hour === 12) return 0; return hour; }
function parseOpenAppCommand(text) { const m = text.match(/(?:打开|启动|帮我打开)\s*([\u4e00-\u9fa5A-Za-z0-9]+)$/u); if (!m) return null; const appName = m[1].trim(); if (!appName || /(闹钟|提醒|记账)/u.test(appName)) return null; return { type: "open_app", title: "打开应用", summary: appName, params: { appName } }; }
function parseNavigationCommand(text) { if (!/(导航|路线|带我去|回家|到家|怎么走|我想回家)/u.test(text)) return null; const m = text.match(/(?:导航(?:到|去)?|路线到|带我去|怎么去|怎么到)\s*([\u4e00-\u9fa5A-Za-z0-9·.\- ]+)$/u); let destination = m?.[1]?.trim() || ""; if (/回家|到家|去家|家里|我家|我想回家/u.test(text)) destination = "家"; destination = destination.replace(/^(百度地图|高德地图|地图|帮我|请|给我)/u, "").trim(); if (!destination) return null; const mode = /步行|走路/u.test(text) ? "walking" : /骑行|骑车|单车/u.test(text) ? "riding" : "driving"; return { type: "navigate", title: "地图导航", summary: `到 ${destination}`, params: { appName: "地图", destination, mode } }; }
function createMobileReply(command) { if (command.type === "set_alarm") return `我理解为要${command.summary}设置“${command.params.label}”闹钟，确认后我再执行。`; if (command.type === "navigate") return `我理解为要导航到“${command.params.destination}”，确认后我再执行。`; return `我理解为要打开“${command.params.appName}”，确认后我再执行。`; }
function parseSimpleRecords(text, now) { if (/(我付了|自己花|垫付|平摊|AA)/u.test(text)) return []; const parts = text.split(/[，,。；;、\n]/).map((i) => i.trim()).filter(Boolean); const records = parts.map((part) => { const m = part.match(/(\d+(?:\.\d+)?)/u); if (!m) return null; const amount = Number(m[1]); if (!Number.isFinite(amount) || amount <= 0) return null; return { title: cleanTitle(part), amount, type: inferType(part), category: inferCategory(part), date: /昨天/u.test(part) ? shiftDate(now, -1) : /前天/u.test(part) ? shiftDate(now, -2) : now }; }).filter(Boolean); return records.length === parts.length ? records : []; }
function extractCategoryQuery(text) { return text.match(/这个月(餐饮|交通|购物|居住|饮品|工资|礼物|其他)(?:花了多少|支出多少|多少钱)/u)?.[1] || null; }
function sumCategoryThisMonth(records, category, now) { const prefix = String(now).slice(0, 7); return (Array.isArray(records) ? records : []).filter((r) => r.type === "expense" && r.category === category && String(r.date).startsWith(prefix)).reduce((s, r) => s + Number(r.amount || 0), 0); }
function inferCategory(text) { const t = String(text || "").toLowerCase(); if (/(饭|早餐|午餐|晚餐|外卖|面|米线|火锅|烧烤|餐)/u.test(t)) return "餐饮"; if (/(奶茶|咖啡|饮料|可乐|茶)/u.test(t)) return "饮品"; if (/(打车|出租|公交|地铁|高铁|火车|机票|加油)/u.test(t)) return "交通"; if (/(淘宝|京东|拼多多|买|衣服|鞋|超市|购物)/u.test(t)) return "购物"; if (/(房租|水电|物业|宿舍|宽带)/u.test(t)) return "居住"; if (/(工资|兼职|奖金|补贴|报销|收入)/u.test(t)) return "工资"; if (/(礼物|红包)/u.test(t)) return "礼物"; return "其他"; }
function inferType(text) { return /(收入|工资|兼职|奖金|报销|收到|进账)/u.test(String(text || "")) ? "income" : "expense"; }
function cleanTitle(text) { return String(text || "").replace(/今天|昨天|前天|花了|花费|消费|支出|收入|进账|收到|元|块钱|块/gu, "").replace(/[0-9.]/gu, "").replace(/[，,。；;、]/gu, "").trim() || "未命名账单"; }
function shiftDate(isoDate, offsetDays) { const d = new Date(`${isoDate}T00:00:00Z`); d.setUTCDate(d.getUTCDate() + offsetDays); return d.toISOString().slice(0, 10); }
function extractWorkersAiText(result) { if (typeof result === "string") return result; if (typeof result?.response === "string") return result.response; if (typeof result?.result?.response === "string") return result.result.response; if (typeof result?.output_text === "string") return result.output_text; if (Array.isArray(result?.content)) return result.content.map((i) => i?.text || i).join("\n"); return JSON.stringify(result); }
function parseAiJson(raw) { const text = String(raw || "").trim(); if (!text) return null; const direct = tryJson(text); if (direct) return direct; const unfenced = text.replace(/^```(?:json)?\s*/i, "").replace(/```$/i, "").trim(); const fromFence = tryJson(unfenced); if (fromFence) return fromFence; const balanced = extractFirstBalancedObject(unfenced); return balanced ? tryJson(balanced) : null; }
function tryJson(text) { try { const parsed = JSON.parse(text); if (parsed && typeof parsed === "object") return parsed; } catch {} return null; }
function extractFirstBalancedObject(text) { const start = text.indexOf("{"); if (start < 0) return null; let depth = 0, inString = false, escaped = false; for (let i = start; i < text.length; i += 1) { const ch = text[i]; if (inString) { if (escaped) escaped = false; else if (ch === "\\") escaped = true; else if (ch === '"') inString = false; continue; } if (ch === '"') inString = true; else if (ch === "{") depth += 1; else if (ch === "}") { depth -= 1; if (depth === 0) return text.slice(start, i + 1); } } return null; }
function cleanRawAiText(raw) { const text = String(raw || "").replace(/^```(?:json)?\s*/i, "").replace(/```$/i, "").trim(); if (!text || (text.startsWith("{") && text.endsWith("}"))) return ""; return text.slice(0, 900); }
function createSafeFallbackReply(lastUserText) { return lastUserText ? "这个问题我可以继续聊，但刚才云端模型返回格式不稳定。我先按普通聊天处理：你可以换一种说法，或者把问题拆成更短的一句。" : "我在，你可以继续说。"; }
function normalizeIsoDate(value) { const text = String(value || ""); return /^\d{4}-\d{2}-\d{2}$/.test(text) ? text : null; }
function normalizeMessages(messages) { if (!Array.isArray(messages)) return []; return messages.slice(-10).map((m) => ({ role: m?.role === "assistant" ? "assistant" : "user", content: String(m?.content || "").trim().slice(0, 500) })).filter((m) => m.content); }
function buildConversation(messages) { return messages.map((m) => `${m.role === "assistant" ? "助手" : "用户"}：${m.content}`).join("\n"); }
function sanitizeRecords(records, fallbackDate) { if (!Array.isArray(records)) return []; return records.slice(0, 10).map((r) => ({ title: String(r?.title || "未命名账单").trim().slice(0, 30) || "未命名账单", amount: Number(r?.amount), type: r?.type === "income" ? "income" : "expense", category: ALLOWED_CATEGORIES.includes(r?.category) ? r.category : "其他", date: normalizeIsoDate(r?.date) || fallbackDate })).filter((r) => Number.isFinite(r.amount) && r.amount > 0); }
function sanitizeLedgerContext(context, fallbackDate) { const safe = context && typeof context === "object" ? context : {}; const summary = safe.summary && typeof safe.summary === "object" ? safe.summary : {}; return { today: fallbackDate, summary: { todayExpense: Number(summary.todayExpense) || 0, monthIncome: Number(summary.monthIncome) || 0, monthExpense: Number(summary.monthExpense) || 0, monthBalance: Number(summary.monthBalance) || 0 }, recentRecords: sanitizeRecords(safe.recentRecords, fallbackDate).slice(0, 60) }; }
function sanitizeClientTools(tools) { if (!Array.isArray(tools)) return []; return tools.slice(0, 12).map((t) => ({ name: String(t?.name || "").trim().slice(0, 50), action: String(t?.action || "").trim().slice(0, 40), commandType: String(t?.commandType || "").trim().slice(0, 40), title: String(t?.title || "").trim().slice(0, 40) })).filter((t) => t.name && t.action); }
function sanitizeMobileCommand(command, fallbackDate) { if (!command || typeof command !== "object") return null; const type = String(command.type || "").trim(); const params = command.params && typeof command.params === "object" ? command.params : {}; if (type === "set_alarm") { const hour = Number(params.hour); const minute = Number(params.minute || 0); if (!Number.isInteger(hour) || !Number.isInteger(minute) || hour < 0 || hour > 23 || minute < 0 || minute > 59) return null; const date = normalizeIsoDate(params.date) || fallbackDate; const label = String(params.label || "提醒").trim().slice(0, 30) || "提醒"; return { type, title: "设置闹钟", summary: `${date} ${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`, params: { date, hour, minute, label } }; } if (type === "open_app") { const appName = String(params.appName || command.summary || "").trim().slice(0, 30); if (!appName) return null; return { type, title: "打开应用", summary: appName, params: { appName, packageName: String(params.packageName || "").trim().slice(0, 80) } }; } if (type === "navigate") { const destination = String(params.destination || command.summary || "").replace(/^到\s*/, "").trim().slice(0, 80); if (!destination) return null; const mode = ["driving", "walking", "riding"].includes(params.mode) ? params.mode : "driving"; return { type, title: "地图导航", summary: `到 ${destination}`, params: { appName: String(params.appName || "地图"), destination, mode } }; } return null; }
function sanitizeAction(action, records, pendingDraft, mobileCommand) { if (!ALLOWED_ACTIONS.includes(action)) { if (mobileCommand) return "mobile_command"; return records.length ? "draft" : "chat"; } if (action === "draft") return records.length ? "draft" : "chat"; if (action === "mobile_command") return mobileCommand ? "mobile_command" : "chat"; if ((action === "confirm_pending" || action === "cancel_pending") && !pendingDraft.length) return "chat"; return action; }
function sanitizeReply(reply, action, records, mobileCommand) { const text = String(reply || "").trim(); if (text) return text.slice(0, 900); if (action === "mobile_command" && mobileCommand) return createMobileReply(mobileCommand); if (action === "draft" && records.length) return `我整理出 ${records.length} 笔待确认账单，你回复“好”我就帮你保存。`; if (action === "confirm_pending") return "好的，已帮你记上。"; if (action === "cancel_pending") return "好的，这次先不保存。"; return "我在，直接和我说就行。"; }
function hasSearchProvider(env) { return Boolean(env.TAVILY_API_KEY || env.BRAVE_SEARCH_API_KEY || env.SERPER_API_KEY); }
function extractUrl(text) { return String(text).match(/https?:\/\/[^\s]+/i)?.[0] || ""; }
function extractLocation(text) { return String(text).replace(/今天|明天|后天|天气|下雨|气温|温度|怎么样|如何|会不会|查询|查一下|请问|现在|的/gu, "").replace(/[，。！？?\s]/g, "").trim().slice(0, 24); }
function extractWikiTopic(text) { return String(text).replace(/请|帮我|百科|维基|介绍一下|是什么|是谁|查一下|搜索/gu, "").replace(/[？?。！!]/g, "").trim().slice(0, 40); }
function cleanSearchQuery(text) { return String(text).replace(/请|帮我|联网|搜索|搜一下|查一下|最新|新闻/gu, "").replace(/[？?。！!]/g, "").trim().slice(0, 120); }
function round(v) { return Number.isFinite(Number(v)) ? Math.round(Number(v)) : "未知"; }
function weatherCodeText(code) { const map = { 0: "晴", 1: "大致晴朗", 2: "局部多云", 3: "阴天", 45: "有雾", 48: "雾凇", 51: "小毛毛雨", 53: "毛毛雨", 55: "较强毛毛雨", 61: "小雨", 63: "中雨", 65: "大雨", 71: "小雪", 73: "中雪", 75: "大雪", 80: "阵雨", 81: "较强阵雨", 82: "强阵雨", 95: "雷暴" }; return map[Number(code)] || "天气状况未知"; }
function extractTitle(html) { return String(html).match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1]?.replace(/\s+/g, " ").trim() || ""; }
function stripHtml(html) { return String(html).replace(/<script[\s\S]*?<\/script>/gi, " ").replace(/<style[\s\S]*?<\/style>/gi, " ").replace(/<[^>]+>/g, " ").replace(/&nbsp;/g, " ").replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">"); }
function todayInTimeZone(timeZone) { return new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date()); }

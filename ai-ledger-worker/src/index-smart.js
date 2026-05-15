const VERSION = "2026-05-15-smart-router-1";
const JSON_HEADERS = { "content-type": "application/json; charset=utf-8" };
const CATEGORIES = ["餐饮", "交通", "购物", "居住", "饮品", "工资", "礼物", "其他"];
const ACTIONS = ["chat", "draft", "confirm_pending", "cancel_pending", "mobile_command"];
const WIKI_HEADERS = {
  "accept": "application/json",
  "user-agent": "AI-Ledger-Assistant/1.0 (https://github.com/yuchenm1303-png/yuchen1303.github.io)",
  "api-user-agent": "AI-Ledger-Assistant/1.0 (https://github.com/yuchenm1303-png/yuchen1303.github.io)",
};

export default {
  async fetch(request, env) {
    const corsHeaders = cors(request, env);
    const url = new URL(request.url);

    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders });

    if (request.method === "GET" && url.pathname === "/health") {
      return json({
        ok: true,
        worker: "ai-ledger-parser",
        version: VERSION,
        mode: "smart_router_tools_plus_gemini",
        provider: env.GEMINI_API_KEY ? "gemini" : "workers_ai",
        model: env.GEMINI_API_KEY ? (env.GEMINI_MODEL || "gemini-2.5-flash") : (env.AI_MODEL || "@cf/meta/llama-3.1-8b-instruct"),
        hasGeminiKey: Boolean(env.GEMINI_API_KEY),
        hasAiBinding: Boolean(env.AI),
        searchProviders: {
          tavily: Boolean(env.TAVILY_API_KEY),
          brave: Boolean(env.BRAVE_SEARCH_API_KEY),
          serper: Boolean(env.SERPER_API_KEY),
        },
        tools: ["weather", "calculator", "datetime", "webpage", "wikipedia", "optional_search", "ledger", "mobile_actions"],
      }, 200, corsHeaders);
    }

    if (request.method !== "POST") return json({ error: "Method not allowed", code: "method_not_allowed", version: VERSION }, 405, corsHeaders);

    let body;
    try { body = await request.json(); }
    catch { return json({ error: "Invalid JSON body", code: "invalid_json_body", version: VERSION }, 400, corsHeaders); }

    const now = normalizedDate(body?.now) || todayCN();
    const messages = normalizeMessages(body?.messages);
    const text = String(body?.text || lastUser(messages) || "").trim();
    const pendingDraft = normalizeRecords(body?.pendingDraft, now);
    const ledgerContext = normalizeLedgerContext(body?.ledgerContext, now);
    const conversation = messages.length ? messages.map((m) => `${m.role === "assistant" ? "助手" : "用户"}：${m.content}`).join("\n") : text;

    if (!text && !conversation) return json({ error: "messages or text is required", code: "missing_conversation", version: VERSION }, 400, corsHeaders);

    const tool = await tryToolReply(text, env, now);
    if (tool) return json({ ...tool, version: VERSION }, 200, corsHeaders);

    const rule = tryRuleReply(text, pendingDraft, ledgerContext, now);
    if (rule) return json({ ...rule, source: "hybrid_rules", version: VERSION }, 200, corsHeaders);

    const prompt = makePrompt({ now, pendingDraft, ledgerContext, conversation, env });
    const cloud = await runCloud(env, prompt);
    if (!cloud.raw) {
      return json({
        reply: cloud.error ? `云端 AI 暂时不可用：${cloud.error.slice(0, 160)}` : "云端 AI 还没配置成功，但我仍可以查天气、读网页、计算、记账、设置闹钟、打开应用和导航。",
        action: "chat",
        records: [],
        mobileCommand: null,
        source: cloud.provider === "gemini" ? "gemini_error" : "missing_cloud_ai",
        version: VERSION,
      }, 200, corsHeaders);
    }

    const parsed = parseJsonLoose(cloud.raw);
    if (!parsed) {
      return json({
        reply: cleanCloudText(cloud.raw) || "我可以继续聊。",
        action: "chat",
        records: [],
        mobileCommand: null,
        source: cloud.provider === "gemini" ? "gemini_text_fallback" : "workers_ai_text_fallback",
        version: VERSION,
      }, 200, corsHeaders);
    }

    const outRecords = normalizeRecords(parsed.records, now);
    const mobileCommand = normalizeMobileCommand(parsed.mobileCommand, now);
    const action = normalizeAction(parsed.action, outRecords, pendingDraft, mobileCommand);
    const reply = String(parsed.reply || "").trim().slice(0, 1200) || fallbackReply(action, outRecords, mobileCommand);

    return json({
      reply,
      action,
      records: outRecords,
      mobileCommand,
      provider: cloud.provider,
      source: cloud.provider === "gemini" ? "gemini_ai" : "workers_ai",
      version: VERSION,
    }, 200, corsHeaders);
  },
};

async function tryToolReply(text, env, now) {
  const value = String(text || "").trim();
  if (!value) return null;

  const url = value.match(/https?:\/\/[^\s]+/i)?.[0] || "";
  if (url && /(总结|读取|看看|分析|网页|链接|这个)/u.test(value)) return readWebpage(url);

  const calc = calculator(value);
  if (calc) return calc;

  if (/(今天几号|今天星期几|今天日期|现在日期|今天是什么日子)/u.test(value)) return datetime(now);

  if (/(天气|下雨|气温|温度|风速|降雨|穿什么|预报)/u.test(value)) return weather(value);

  if (/(搜索|查一下|搜一下|最新|新闻|价格|官网|联网查)/u.test(value)) return webSearch(value, env);

  // “xxx是什么意思 / xxx meaning” belongs to the language model, not Wikipedia.
  if (/(是什么意思|什么意思|啥意思|mean\??|meaning\??)$/iu.test(value)) return null;

  if (/(百科|维基|介绍一下|是什么|是谁)/u.test(value) && value.length <= 80 && !/(你是谁|你叫什么|你是什么|你能做什么)/u.test(value)) {
    const topic = value.replace(/请|帮我|百科|维基|介绍一下|是什么|是谁|查一下|搜索/gu, "").replace(/[？?。！!]/g, "").trim();
    if (topic) return wiki(topic);
  }

  return null;
}

async function weather(text) {
  const candidates = weatherCandidates(text);
  if (!candidates.length) return pack("你想查哪里的天气？例如：重庆今天会下雨吗。", "weather_tool");

  try {
    const found = await geocode(candidates);
    if (!found) return pack(`我没有找到“${candidates[0]}”的天气位置。可以换成更明确的城市名，比如“重庆市天气”或“北京海淀天气”。`, "weather_tool");

    const p = found.place;
    const f = await fetchJson(`https://api.open-meteo.com/v1/forecast?latitude=${p.latitude}&longitude=${p.longitude}&current=temperature_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=3&timezone=auto`);
    const c = f.current || {};
    const d = f.daily || {};
    const rain = d.precipitation_probability_max?.[0];
    return pack(`${p.name}${p.admin1 ? `（${p.admin1}）` : ""}当前约 ${round(c.temperature_2m)}℃，体感 ${round(c.apparent_temperature)}℃，${weatherText(c.weather_code)}，风速约 ${round(c.wind_speed_10m)} km/h。今天气温约 ${round(d.temperature_2m_min?.[0])}–${round(d.temperature_2m_max?.[0])}℃，最高降水概率约 ${rain ?? "未知"}%。${Number(rain) >= 50 || Number(c.precipitation) > 0 ? "建议带伞。" : "降雨风险不算高。"}`, "weather_tool");
  } catch (error) {
    return pack(`天气查询失败：${String(error?.message || error).slice(0, 120)}`, "weather_tool");
  }
}

function weatherCandidates(text) {
  const raw = String(text || "").trim();
  const patterns = [
    /(?:查一下|查询|看看|帮我查|帮我看看|请问)?\s*([\u4e00-\u9fa5A-Za-z .·-]{2,32}?)(?:今天|今日|现在|当前|明天|后天)?(?:的)?(?:天气|气温|温度|预报)/u,
    /(?:查一下|查询|看看|帮我查|帮我看看|请问)?\s*([\u4e00-\u9fa5A-Za-z .·-]{2,32}?)(?:今天|今日|明天|后天)?(?:会不会|会)?(?:下雨|降雨)/u,
    /(?:今天|今日|现在|当前|明天|后天)?\s*([\u4e00-\u9fa5A-Za-z .·-]{2,32}?)(?:会不会|会)?(?:下雨|降雨|天气|气温|温度)/u,
  ];
  let loc = "";
  for (const p of patterns) {
    const match = raw.match(p);
    if (match?.[1]) { loc = match[1]; break; }
  }
  const base = cleanLocation(loc || raw);
  if (!base) return [];
  const list = [base];
  if (base.endsWith("市") || base.endsWith("区") || base.endsWith("县")) list.push(base.slice(0, -1));
  else if (/^[\u4e00-\u9fa5]{2,6}$/u.test(base)) list.push(`${base}市`);
  const aliases = { 重庆: ["重庆市", "Chongqing"], 北京: ["北京市", "Beijing"], 上海: ["上海市", "Shanghai"], 天津: ["天津市", "Tianjin"], 广州: ["广州市", "Guangzhou"], 深圳: ["深圳市", "Shenzhen"] };
  if (aliases[base]) list.push(...aliases[base]);
  return [...new Set(list.filter(Boolean))].slice(0, 7);
}

function cleanLocation(value) {
  return String(value || "")
    .replace(/https?:\/\/\S+/gi, "")
    .replace(/请问|请|帮我|帮忙|给我|麻烦|上网|联网|搜索|搜一下|查一下|查询|看看|看一下|一下|今天|今日|现在|当前|明天|后天|天气|气温|温度|预报|下雨|降雨|会不会|会|不会|怎么样|如何|多少|几度|穿什么|适合|出门|带伞|的|吗|呢|啊|呀/gu, "")
    .replace(/[，。！？?、,.!！\s]/g, "")
    .trim()
    .slice(0, 32);
}

async function geocode(candidates) {
  for (const name of candidates) {
    const geo = await fetchJson(`https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(name)}&count=5&language=zh&format=json`);
    const results = Array.isArray(geo?.results) ? geo.results : [];
    const cn = results.find((x) => x.country_code === "CN") || results[0];
    if (cn?.latitude && cn?.longitude) return { query: name, place: cn };
  }
  return null;
}

async function wiki(topic) {
  try {
    const search = await fetchJson(`https://zh.wikipedia.org/w/rest.php/v1/search/page?q=${encodeURIComponent(topic)}&limit=1`, { headers: WIKI_HEADERS });
    const key = search?.pages?.[0]?.key;
    if (!key) return pack(`没有找到“${topic}”的百科摘要。`, "wiki_tool");
    const summary = await fetchJson(`https://zh.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(key)}`, { headers: WIKI_HEADERS });
    return pack(summary?.extract ? `${summary.title || topic}：${String(summary.extract).slice(0, 520)}` : `找到“${topic}”，但没有可用摘要。`, "wiki_tool");
  } catch (error) {
    return pack(`百科查询失败：${String(error?.message || error).slice(0, 120)}`, "wiki_tool");
  }
}

async function readWebpage(url) {
  try {
    const res = await fetch(url, { headers: { "user-agent": "AI-Ledger-Assistant/1.0" } });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const type = res.headers.get("content-type") || "";
    if (!/text\/html|text\/plain|application\/json/i.test(type)) throw new Error("暂不支持读取这种文件类型");
    const html = (await res.text()).slice(0, 120000);
    const title = html.match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1]?.replace(/\s+/g, " ").trim() || url;
    const content = html.replace(/<script[\s\S]*?<\/script>/gi, " ").replace(/<style[\s\S]*?<\/style>/gi, " ").replace(/<[^>]+>/g, " ").replace(/&nbsp;/g, " ").replace(/&amp;/g, "&").replace(/\s+/g, " ").trim();
    return pack(content ? `我读取到了网页《${title}》。主要内容摘要：${content.slice(0, 520)}${content.length > 520 ? "……" : ""}` : `我打开了网页《${title}》，但没有提取到足够正文。`, "webpage_tool");
  } catch (error) {
    return pack(`网页读取失败：${String(error?.message || error).slice(0, 120)}`, "webpage_tool");
  }
}

async function webSearch(text, env) {
  const query = text.replace(/请|帮我|联网|搜索|搜一下|查一下|最新|新闻/gu, "").replace(/[？?。！!]/g, "").trim().slice(0, 120);
  if (!query) return null;
  try {
    if (env.TAVILY_API_KEY) {
      const data = await fetchJson("https://api.tavily.com/search", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ api_key: env.TAVILY_API_KEY, query, search_depth: "basic", max_results: 5, include_answer: true }) });
      const rows = (data?.results || []).slice(0, 5).map((x, i) => `${i + 1}. ${x.title || "结果"}：${x.content || x.url || ""}`).join("\n");
      return pack(`${data?.answer ? `${data.answer}\n\n` : ""}搜索结果：\n${rows || "没有找到结果。"}`, "web_search_tool");
    }
    return pack("联网搜索工具还没配置 Key。现在我已经能查天气、百科和读取网页链接；要通用网页搜索，可以加 TAVILY_API_KEY、BRAVE_SEARCH_API_KEY 或 SERPER_API_KEY。", "search_not_configured");
  } catch (error) {
    return pack(`联网搜索失败：${String(error?.message || error).slice(0, 150)}`, "web_search_tool");
  }
}

function calculator(text) {
  const hasOp = ["+", "-", "*", "/", "×", "÷", "^"].some((op) => text.includes(op));
  if (!hasOp && !/(计算|算一下|等于)/u.test(text)) return null;
  const expr = text.replace(/计算|算一下|等于|是多少|等于多少|？|\?/gu, "").replace(/×/g, "*").replace(/÷/g, "/").replace(/\^/g, "**").trim();
  if (!/^[0-9+*/().\s%*-]+$/.test(expr) || expr.length > 80) return null;
  try {
    const result = Function(`"use strict"; return (${expr})`)();
    if (!Number.isFinite(result)) return null;
    return pack(`${expr} = ${Number(result.toFixed(10))}`, "calculator_tool");
  } catch { return null; }
}

function datetime(now) {
  const d = new Date(`${now}T00:00:00+08:00`);
  const week = ["星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"][d.getDay()];
  return pack(`今天是 ${now}，${week}。`, "datetime_tool");
}

async function runCloud(env, prompt) {
  if (env.GEMINI_API_KEY) {
    try { return { provider: "gemini", raw: await gemini(env, prompt), error: "" }; }
    catch (error) {
      if (!env.AI) return { provider: "gemini", raw: "", error: String(error?.message || error) };
    }
  }
  if (env.AI) {
    try { return { provider: "workers_ai", raw: await workersAI(env, prompt), error: "" }; }
    catch (error) { return { provider: "workers_ai", raw: "", error: String(error?.message || error) }; }
  }
  return { provider: "none", raw: "", error: "" };
}

async function gemini(env, prompt) {
  const model = env.GEMINI_MODEL || "gemini-2.5-flash";
  const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(env.GEMINI_API_KEY)}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ systemInstruction: { parts: [{ text: prompt.system }] }, contents: [{ role: "user", parts: [{ text: prompt.user }] }], generationConfig: { temperature: 0.2, maxOutputTokens: 1000, responseMimeType: "application/json" } }),
  });
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new Error(data?.error?.message || `Gemini HTTP ${res.status}`);
  return (data?.candidates?.[0]?.content?.parts || []).map((p) => p?.text || "").join("\n") || JSON.stringify(data || {});
}

async function workersAI(env, prompt) {
  const result = await env.AI.run(env.AI_MODEL || "@cf/meta/llama-3.1-8b-instruct", { messages: [{ role: "system", content: prompt.system }, { role: "user", content: prompt.user }], temperature: 0.15, max_tokens: 700, response_format: { type: "json_schema", json_schema: responseSchema() } });
  if (typeof result === "string") return result;
  return result?.response || result?.result?.response || result?.output_text || JSON.stringify(result || {});
}

function makePrompt({ now, pendingDraft, ledgerContext, conversation, env }) {
  return {
    system: [
      "你是中文手机 AI 助手。普通聊天要自然回答；词语解释、英文单词含义、代码概念解释都要直接解释清楚。",
      "记账、闹钟、打开应用、导航需要整理成 JSON。",
      "输出必须是 JSON：{ reply, action, records, mobileCommand }。不要 Markdown，不要代码块。",
      "action 只能是 chat、draft、confirm_pending、cancel_pending、mobile_command。",
      `今天日期：${now}。`,
    ].join("\n"),
    user: [`待确认账单：${pendingDraft.length ? JSON.stringify(pendingDraft) : "无"}`, `账本上下文：${JSON.stringify(ledgerContext)}`, `在线搜索：${env.TAVILY_API_KEY || env.BRAVE_SEARCH_API_KEY || env.SERPER_API_KEY ? "可用" : "未配置"}`, `对话：\n${conversation}`, "请回答最后一条用户消息。"].join("\n\n"),
  };
}

function tryRuleReply(text, pendingDraft, ledgerContext, now) {
  if (pendingDraft.length && /^(好|好的|对|确认|保存|记上|就这样)$/u.test(text)) return { reply: "好的，已帮你记上。", action: "confirm_pending", records: [], mobileCommand: null };
  if (pendingDraft.length && /^(算了|不用了|先别记|取消)$/u.test(text)) return { reply: "好的，这次先不保存。", action: "cancel_pending", records: [], mobileCommand: null };
  if (/^(你好|您好|嗨|哈喽|在吗|hello|hi)$/iu.test(text)) return { reply: "我在。你可以让我记账、查天气、读网页、设置闹钟、打开应用和导航，也可以直接聊天。", action: "chat", records: [], mobileCommand: null };
  const cmd = mobileCommandFromText(text, now);
  if (cmd) return { reply: mobileReply(cmd), action: "mobile_command", records: [], mobileCommand: cmd };
  const rs = simpleRecords(text, now);
  if (rs.length) return { reply: `我整理出 ${rs.length} 笔待确认账单，你回复“好”我就帮你保存。`, action: "draft", records: rs, mobileCommand: null };
  const cat = text.match(/这个月(餐饮|交通|购物|居住|饮品|工资|礼物|其他)(?:花了多少|支出多少|多少钱)/u)?.[1];
  if (cat) {
    const prefix = now.slice(0, 7);
    const total = (ledgerContext.recentRecords || []).filter((r) => r.type === "expense" && r.category === cat && String(r.date).startsWith(prefix)).reduce((s, r) => s + Number(r.amount || 0), 0);
    return { reply: `你这个月${cat}一共花了 ¥${total.toFixed(2)}。`, action: "chat", records: [], mobileCommand: null };
  }
  return null;
}

function mobileCommandFromText(text, now) {
  if (/(闹钟|叫我|提醒我|叫醒|起床)/u.test(text)) {
    const m = text.match(/(\d{1,2})(?:[:：点时](\d{1,2})?分?)?/u);
    if (!m) return null;
    let hour = Number(m[1]);
    const minute = Number(m[2] || 0);
    if (/下午|晚上|今晚/u.test(text) && hour < 12) hour += 12;
    if (!Number.isInteger(hour) || hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
    const date = /后天/u.test(text) ? shiftDate(todayCN(), 2) : /明天|明早|明晚/u.test(text) ? shiftDate(todayCN(), 1) : now;
    return { type: "set_alarm", title: "设置闹钟", summary: `${date} ${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`, params: { date, hour, minute, label: /起床|叫醒/u.test(text) ? "起床" : "提醒" } };
  }
  const app = text.match(/(?:打开|启动|帮我打开)\s*([\u4e00-\u9fa5A-Za-z0-9]+)$/u)?.[1];
  if (app && !/(闹钟|提醒|记账)/u.test(app)) return { type: "open_app", title: "打开应用", summary: app, params: { appName: app } };
  if (/(导航|路线|带我去|回家|到家|怎么走)/u.test(text)) {
    const m = text.match(/(?:导航(?:到|去)?|路线到|带我去|怎么去|怎么到)\s*([\u4e00-\u9fa5A-Za-z0-9·.\- ]+)$/u);
    let destination = m?.[1]?.trim() || "";
    if (/回家|到家|我家/u.test(text)) destination = "家";
    if (destination) return { type: "navigate", title: "地图导航", summary: `到 ${destination}`, params: { appName: "地图", destination, mode: /步行|走路/u.test(text) ? "walking" : "driving" } };
  }
  return null;
}

function simpleRecords(text, now) {
  if (/(我付了|垫付|平摊|AA)/u.test(text)) return [];
  if (/(是什么意思|什么意思|[0-9]\s*[+\-×÷*/^]\s*[0-9])/u.test(text)) return [];
  if (!/(花|买|消费|支出|收入|收到|工资|报销|元|块|奶茶|午饭|晚饭|早餐|打车|地铁|公交)/u.test(text)) return [];
  const parts = text.split(/[，,。；;、\n]/).map((x) => x.trim()).filter(Boolean);
  const rs = parts.map((p) => {
    const m = p.match(/(\d+(?:\.\d+)?)/u);
    if (!m) return null;
    const amount = Number(m[1]);
    if (!Number.isFinite(amount) || amount <= 0) return null;
    return { title: p.replace(/[0-9.元块钱]/gu, "").trim() || "未命名账单", amount, type: /(收入|工资|兼职|奖金|报销|收到|进账)/u.test(p) ? "income" : "expense", category: inferCategory(p), date: /昨天/u.test(p) ? shiftDate(now, -1) : now };
  }).filter(Boolean);
  return rs.length === parts.length ? rs : [];
}

function inferCategory(t) { if (/(饭|早餐|午餐|晚餐|外卖|餐)/u.test(t)) return "餐饮"; if (/(奶茶|咖啡|饮料|茶)/u.test(t)) return "饮品"; if (/(打车|公交|地铁|高铁|火车|机票)/u.test(t)) return "交通"; if (/(买|淘宝|京东|购物|超市)/u.test(t)) return "购物"; if (/(房租|水电|物业)/u.test(t)) return "居住"; if (/(工资|兼职|奖金|补贴|报销)/u.test(t)) return "工资"; return "其他"; }
function mobileReply(c) { if (c.type === "set_alarm") return `我理解为要${c.summary}设置“${c.params.label}”闹钟，确认后我再执行。`; if (c.type === "navigate") return `我理解为要导航到“${c.params.destination}”，确认后我再执行。`; return `我理解为要打开“${c.params.appName}”，确认后我再执行。`; }
function pack(reply, source) { return { reply, action: "chat", records: [], mobileCommand: null, source }; }
function json(payload, status = 200, headers = {}) { return new Response(JSON.stringify(payload), { status, headers: { ...JSON_HEADERS, ...headers } }); }
function cors(request, env) { const origin = request.headers.get("Origin") || ""; const allowed = String(env.ALLOWED_ORIGINS || "*").split(",").map((x) => x.trim()).filter(Boolean); const allow = allowed.includes("*") || allowed.includes(origin) ? origin || "*" : allowed[0] || "*"; return { ...JSON_HEADERS, "access-control-allow-origin": allow, "access-control-allow-methods": "GET, POST, OPTIONS", "access-control-allow-headers": "content-type", vary: "Origin" }; }
async function fetchJson(url, options = {}, timeout = 9000) { const c = new AbortController(); const timer = setTimeout(() => c.abort(), timeout); try { const res = await fetch(url, { ...options, signal: c.signal }); const data = await res.json().catch(() => null); if (!res.ok) throw new Error(data?.error?.message || `HTTP ${res.status}`); return data; } finally { clearTimeout(timer); } }
function parseJsonLoose(raw) { const text = String(raw || "").trim(); try { return JSON.parse(text); } catch {} const unfenced = text.replace(/^```(?:json)?\s*/i, "").replace(/```$/i, "").trim(); try { return JSON.parse(unfenced); } catch {} const obj = balancedObject(unfenced); if (!obj) return null; try { return JSON.parse(obj); } catch { return null; } }
function balancedObject(text) { const start = text.indexOf("{"); if (start < 0) return null; let depth = 0, str = false, esc = false; for (let i = start; i < text.length; i++) { const ch = text[i]; if (str) { if (esc) esc = false; else if (ch === "\\") esc = true; else if (ch === '"') str = false; continue; } if (ch === '"') str = true; else if (ch === "{") depth++; else if (ch === "}") { depth--; if (depth === 0) return text.slice(start, i + 1); } } return null; }
function responseSchema() { return { type: "object", properties: { reply: { type: "string" }, action: { type: "string" }, records: { type: "array" }, mobileCommand: { type: "object" } }, required: ["reply", "action", "records"] }; }
function normalizeRecords(list, date) { if (!Array.isArray(list)) return []; return list.slice(0, 10).map((r) => ({ title: String(r?.title || "未命名账单").slice(0, 30), amount: Number(r?.amount), type: r?.type === "income" ? "income" : "expense", category: CATEGORIES.includes(r?.category) ? r.category : "其他", date: normalizedDate(r?.date) || date })).filter((r) => Number.isFinite(r.amount) && r.amount > 0); }
function normalizeMobileCommand(cmd) { if (!cmd || typeof cmd !== "object") return null; return ["set_alarm", "open_app", "navigate"].includes(cmd.type) ? cmd : null; }
function normalizeAction(action, rs, pending, cmd) { if (!ACTIONS.includes(action)) return cmd ? "mobile_command" : rs.length ? "draft" : "chat"; if (action === "draft" && !rs.length) return "chat"; if (action === "mobile_command" && !cmd) return "chat"; if ((action === "confirm_pending" || action === "cancel_pending") && !pending.length) return "chat"; return action; }
function fallbackReply(action, rs, cmd) { if (action === "mobile_command" && cmd) return mobileReply(cmd); if (action === "draft" && rs.length) return `我整理出 ${rs.length} 笔待确认账单，你回复“好”我就帮你保存。`; return "我在，直接和我说就行。"; }
function normalizeLedgerContext(x, date) { const s = x && typeof x === "object" ? x : {}; return { today: date, summary: s.summary || {}, recentRecords: normalizeRecords(s.recentRecords, date).slice(0, 60) }; }
function normalizeMessages(messages) { if (!Array.isArray(messages)) return []; return messages.slice(-10).map((m) => ({ role: m?.role === "assistant" ? "assistant" : "user", content: String(m?.content || "").trim().slice(0, 500) })).filter((m) => m.content); }
function lastUser(messages) { return [...messages].reverse().find((m) => m.role === "user")?.content || ""; }
function normalizedDate(v) { const t = String(v || ""); return /^\d{4}-\d{2}-\d{2}$/.test(t) ? t : null; }
function todayCN() { return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Shanghai", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date()); }
function shiftDate(iso, days) { const d = new Date(`${iso}T00:00:00Z`); d.setUTCDate(d.getUTCDate() + days); return d.toISOString().slice(0, 10); }
function round(v) { return Number.isFinite(Number(v)) ? Math.round(Number(v)) : "未知"; }
function weatherText(code) { return ({ 0: "晴", 1: "大致晴朗", 2: "局部多云", 3: "阴天", 45: "有雾", 51: "小毛毛雨", 61: "小雨", 63: "中雨", 65: "大雨", 71: "小雪", 80: "阵雨", 95: "雷暴" })[Number(code)] || "天气状况未知"; }
function cleanCloudText(raw) { const text = String(raw || "").replace(/^```(?:json)?\s*/i, "").replace(/```$/i, "").trim(); if (!text || (text.startsWith("{") && text.endsWith("}"))) return ""; return text.slice(0, 1000); }

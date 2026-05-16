import commandWorker from "./index.js";
import attachmentGateway from "./index-attachments-gateway.js";

const ORCHESTRATOR_VERSION = "ai-ledger-orchestrator-v4-model-tags";
const JSON_HEADERS = { "content-type": "application/json; charset=utf-8" };
const ACTION_INTENTS = new Set([
  "navigation.start",
  "navigation.modify",
  "navigation.preference.set",
  "alarm.set",
  "app.open",
  "ledger.create",
]);

export default {
  async fetch(request, env, ctx) {
    const corsHeaders = cors(request, env);
    const url = new URL(request.url);

    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders });

    if (request.method === "GET" && url.pathname === "/health") {
      const commandHealth = await safeHealth(commandWorker, request, env, ctx);
      const attachmentHealth = await safeHealth(attachmentGateway, request, env, ctx);
      return json({
        ok: true,
        version: ORCHESTRATOR_VERSION,
        mode: "cloud_brain_local_executor_orchestrator_memory_model_tags",
        hasGeminiKey: Boolean(env.GEMINI_API_KEY),
        hasTavilyKey: Boolean(env.TAVILY_API_KEY),
        hasWorkersAI: Boolean(env.AI),
        defaultGeminiModel: geminiModel(env),
        commandWorker: commandHealth,
        attachmentGateway: attachmentHealth,
        intents: [
          "attachment.analyze",
          "weather.query",
          "web.search",
          "news.query",
          "navigation.start",
          "navigation.modify",
          "navigation.preference.set",
          "alarm.set",
          "app.open",
          "ledger.create",
          "chat",
        ],
      }, 200, corsHeaders);
    }

    if (request.method !== "POST") return commandWorker.fetch(request, env, ctx);

    const originalForDelegate = request.clone();
    let body;
    try {
      body = await request.json();
    } catch {
      return commandWorker.fetch(originalForDelegate, env, ctx);
    }

    const text = lastUserText(body?.messages, body?.text);
    const memory = normalizeMemoryContext(body?.memoryContext);
    const hasAttachments = Array.isArray(body?.attachments) && body.attachments.length > 0;

    if (hasAttachments) {
      return attachmentGateway.fetch(cloneRequestWithJson(request, { ...body, memoryContext: memory }), env, ctx);
    }

    const forceSearch = isForcedWebSearch(body);
    const explicitSearch = isExplicitSearchQuery(text) && !isWeatherLikeText(text);

    // Hard guard: explicit search/news requests must not be mistaken as weather locations.
    if (explicitSearch) {
      const search = await searchWeb(env, text);
      if (search.ok) return json(search, 200, corsHeaders);
      return json(searchError(search.error), 200, corsHeaders);
    }

    let intent = await classifyIntent(env, body, text, memory);

    // Guard against Gemini accidentally classifying concept questions as weather.
    if (intent.intent === "weather.query" && !isWeatherLikeText(text) && !isWeatherLocationFollowup(body, text)) {
      intent = { intent: "chat", confidence: 0.72, toolInput: {}, reason: "weather_false_positive_guard" };
    }

    if (intent.intent === "weather.query") {
      const weatherInput = cleanWeatherLocation(intent.toolInput?.location)
        || memory.currentCity
        || memory.currentLocation
        || text;
      return json(await weather(weatherInput, memory), 200, corsHeaders);
    }

    if (intent.intent === "web.search" || intent.intent === "news.query" || (forceSearch && !ACTION_INTENTS.has(intent.intent))) {
      const query = intent.toolInput?.query || text;
      const search = await searchWeb(env, query);
      if (search.ok) return json(search, 200, corsHeaders);
      return json(searchError(search.error), 200, corsHeaders);
    }

    if (intent.intent === "chat") {
      return json(await chatWithGemini(env, body, text, memory), 200, corsHeaders);
    }

    // Local actions and ledger drafts are delegated to the command protocol worker.
    // The cloud only returns structured JSON; the local app still verifies and executes after confirmation.
    return delegateCommandWorker(request, env, ctx, {
      ...body,
      memoryContext: memory,
      orchestrator: {
        version: ORCHESTRATOR_VERSION,
        intent: intent.intent,
        confidence: intent.confidence,
        toolInput: intent.toolInput || {},
        instruction: "For actionable local tasks, return structured command JSON. For normal chat, return action=chat.",
      },
    });
  },
};

async function delegateCommandWorker(originalRequest, env, ctx, body) {
  const response = await commandWorker.fetch(cloneRequestWithJson(originalRequest, body), env, ctx);
  const text = await response.clone().text().catch(() => "");
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch {}
  if (!data || typeof data !== "object") return response;
  const meta = modelMeta("Gemini", geminiModel(env), "Command Planner");
  return json({
    ...data,
    ...meta,
    version: appendRunLabel(data.version || ORCHESTRATOR_VERSION, meta.modelLabel),
  }, response.status, response.headers);
}

async function safeHealth(worker, originalRequest, env, ctx) {
  try {
    const url = new URL(originalRequest.url);
    const res = await worker.fetch(new Request(`${url.origin}/health`, { method: "GET", headers: originalRequest.headers }), env, ctx);
    return await res.json().catch(() => ({ ok: res.ok, status: res.status }));
  } catch (error) {
    return { ok: false, error: String(error?.message || error).slice(0, 120) };
  }
}

function cloneRequestWithJson(originalRequest, body) {
  const headers = new Headers(originalRequest.headers);
  headers.set("content-type", "application/json; charset=utf-8");
  headers.delete("content-length");
  return new Request(originalRequest.url, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });
}

async function classifyIntent(env, body, text, memory = {}) {
  const fallback = heuristicIntent(body, text);

  // Keep ultra-high-confidence local actions deterministic to avoid latency and accidental tool calls.
  if (fallback.confidence >= 0.9 && ACTION_INTENTS.has(fallback.intent)) return fallback;

  if (!env.GEMINI_API_KEY) return fallback;

  try {
    const model = geminiModel(env);
    const endpoint = `${env.GEMINI_API_BASE || "https://generativelanguage.googleapis.com/v1beta/models"}/${model}:generateContent?key=${encodeURIComponent(env.GEMINI_API_KEY)}`;
    const messages = Array.isArray(body?.messages) ? body.messages.slice(-10) : [];
    const prompt = [
      "你是 AI 助手的意图编排器。只判断最后一句用户话应该归到哪一层，不要回答正文。",
      "必须只返回 JSON object，不要 markdown。",
      "允许 intent：attachment.analyze, weather.query, web.search, news.query, navigation.start, navigation.modify, navigation.preference.set, alarm.set, app.open, ledger.create, chat。",
      "硬规则：",
      "1. 用户明确说“搜一下/搜索/查一下/联网查/上网查”，且没有天气、气温、下雨等天气词 → web.search 或 news.query，不能判成 weather.query。",
      "2. 用户说新闻、大新闻、热点、今日新闻、最新消息 → news.query。不要把“新闻”当作城市。",
      "3. 天气、气温、下雨、穿衣建议 → weather.query。toolInput.location 有城市就填，没有则空；不要把“这里/本地/当前位置/新闻/定义/生命”等当城市。",
      "4. 如果上一条助手问城市天气，用户只回“重庆吧/北京/温州”，才判为 weather.query，并把 location 写成这个城市。",
      "5. 导航、修改导航、保存家/学校/公司/宿舍地址、默认地图/默认出行方式 → 对应 navigation intent。",
      "6. 设置提醒/闹钟 → alarm.set。打开 App → app.open。真实收支 → ledger.create。",
      "7. 普通解释、定义、学习问题，例如“生命的定义是什么/人工智能是什么” → chat。",
      "返回格式：",
      JSON.stringify({ intent: "chat", confidence: 0.8, toolInput: {}, reason: "short" }),
      "本地记忆：",
      JSON.stringify(memory || {}),
      "对话历史：",
      JSON.stringify(messages),
      "最后一句：",
      text,
    ].join("\n");

    const res = await fetch(endpoint, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        contents: [{ role: "user", parts: [{ text: prompt }] }],
        generationConfig: { temperature: 0, maxOutputTokens: 320, responseMimeType: "application/json" },
      }),
    });
    const data = await res.json().catch(() => null);
    if (!res.ok) return fallback;
    const raw = data?.candidates?.[0]?.content?.parts?.map((p) => p.text || "").join("").trim() || "";
    const parsed = JSON.parse(raw);
    return normalizeIntent(parsed, fallback);
  } catch {
    return fallback;
  }
}

function normalizeIntent(input, fallback) {
  const allowed = new Set(["attachment.analyze", "weather.query", "web.search", "news.query", "navigation.start", "navigation.modify", "navigation.preference.set", "alarm.set", "app.open", "ledger.create", "chat"]);
  const intent = allowed.has(input?.intent) ? input.intent : fallback.intent;
  const confidence = Number(input?.confidence);
  return {
    intent,
    confidence: Number.isFinite(confidence) ? Math.max(0, Math.min(1, confidence)) : fallback.confidence,
    toolInput: input?.toolInput && typeof input.toolInput === "object" ? input.toolInput : fallback.toolInput || {},
    reason: String(input?.reason || fallback.reason || "").slice(0, 120),
  };
}

function heuristicIntent(body, text) {
  const raw = String(text || "").trim();
  const clean = raw.replace(/[？?。！!\s]/g, "");
  const prevAssistant = previousAssistantText(body);

  if (isExplicitSearchQuery(raw) && !isWeatherLikeText(raw)) {
    return { intent: /新闻|热点|消息/u.test(raw) ? "news.query" : "web.search", confidence: 0.9, toolInput: { query: cleanSearchQuery(raw) }, reason: "explicit search" };
  }
  if (isWeatherLikeText(raw)) {
    return { intent: "weather.query", confidence: 0.82, toolInput: { location: extractWeatherLocation(raw) }, reason: "weather keywords" };
  }
  if (/哪个城市.*天气|想查.*天气|城市.*天气/u.test(prevAssistant) && /^[\u4e00-\u9fa5A-Za-z .·-]{2,16}(吧|呗|呀|呢)?$/u.test(clean)) {
    return { intent: "weather.query", confidence: 0.86, toolInput: { location: cleanWeatherLocation(raw) }, reason: "weather location follow-up" };
  }
  if (/(新闻|大新闻|热点|今日热点|最新|最近|搜索|搜一下|查一下|查查|上网查|联网查|资料|官网|价格|榜单|实时|现在发生)/u.test(raw)
    && !/(导航|记账|提醒|闹钟|打开|保存|设置家|回家)/u.test(raw)) {
    return { intent: /新闻|热点/u.test(raw) ? "news.query" : "web.search", confidence: 0.82, toolInput: { query: cleanSearchQuery(raw) }, reason: "web search keywords" };
  }
  if (/(导航|路线|怎么走|去|到|回家|去学校|去公司|去宿舍)/u.test(raw)) return { intent: "navigation.start", confidence: 0.72, toolInput: {}, reason: "navigation-like" };
  if (/(改成|换成|少步行|避开高速|换高德|换百度)/u.test(raw)) return { intent: "navigation.modify", confidence: 0.76, toolInput: {}, reason: "navigation modify-like" };
  if (/(家就是|把家设为|学校就是|公司就是|宿舍就是|默认用|默认坐|默认公交|默认地铁)/u.test(raw)) return { intent: "navigation.preference.set", confidence: 0.9, toolInput: {}, reason: "navigation preference" };
  if (/(闹钟|叫我|提醒我|提醒一下|叫醒|起床)/u.test(raw)) return { intent: "alarm.set", confidence: 0.9, toolInput: {}, reason: "alarm" };
  if (/^(打开|启动|帮我打开)\s*[\u4e00-\u9fa5A-Za-z0-9]+$/u.test(raw)) return { intent: "app.open", confidence: 0.9, toolInput: {}, reason: "open app" };
  if (/(花了|消费|支出|收入|收到|工资|报销|午饭|晚饭|早餐|奶茶|打车).*(\d+(?:\.\d+)?)/u.test(raw)) return { intent: "ledger.create", confidence: 0.78, toolInput: {}, reason: "ledger" };

  return { intent: "chat", confidence: 0.55, toolInput: {}, reason: "default" };
}

function previousAssistantText(body) {
  const messages = Array.isArray(body?.messages) ? body.messages : [];
  const msg = [...messages].reverse().find((m) => m?.role === "assistant" && String(m?.content || "").trim());
  return String(msg?.content || "");
}

async function weather(locationOrText, memory = {}) {
  const location = cleanWeatherLocation(extractWeatherLocation(locationOrText)) || cleanWeatherLocation(memory.currentCity || memory.currentLocation);
  if (!location) {
    return baseResponse("你想查哪个城市的天气？比如可以说：重庆今天会下雨吗。", "weather_need_location", modelMeta("Open-Meteo", "weather-forecast", "Open-Meteo"));
  }
  try {
    const place = await geocode(location);
    if (!place) return baseResponse(`我没有找到“${location}”的天气位置。可以换成更明确的城市名，比如“重庆市天气”。`, "weather_tool", modelMeta("Open-Meteo", "geocoding+forecast", "Open-Meteo"));
    const f = await fetchJson(`https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}&current=temperature_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=3&timezone=auto`);
    const c = f.current || {};
    const d = f.daily || {};
    const rain = d.precipitation_probability_max?.[0];
    return baseResponse(`${place.name}${place.admin1 ? `（${place.admin1}）` : ""}当前约 ${round(c.temperature_2m)}℃，体感 ${round(c.apparent_temperature)}℃，${weatherText(c.weather_code)}，风速约 ${round(c.wind_speed_10m)} km/h。今天气温约 ${round(d.temperature_2m_min?.[0])}–${round(d.temperature_2m_max?.[0])}℃，最高降水概率约 ${rain ?? "未知"}%。${Number(rain) >= 50 || Number(c.precipitation) > 0 ? "建议带伞。" : "降雨风险不算高。"}`, memory.currentCity ? "weather_tool_memory" : "weather_tool", modelMeta("Open-Meteo", "forecast-v1", "Open-Meteo"));
  } catch {
    return baseResponse("天气接口暂时请求失败。你可以稍后再试，或换一个更明确的城市名。", "weather_error", modelMeta("Open-Meteo", "forecast-v1", "Open-Meteo"));
  }
}

async function searchWeb(env, rawQuery) {
  const query = cleanSearchQuery(rawQuery) || "今日新闻";
  if (env.TAVILY_API_KEY) {
    try {
      const res = await fetch("https://api.tavily.com/search", {
        method: "POST",
        headers: { "content-type": "application/json", authorization: `Bearer ${env.TAVILY_API_KEY}` },
        body: JSON.stringify({ query, search_depth: "advanced", topic: "general", max_results: 6, include_answer: true, include_raw_content: false, include_images: false }),
      });
      const data = await res.json().catch(() => null);
      if (!res.ok) throw new Error(data?.error || data?.message || `Tavily HTTP ${res.status}`);
      const results = Array.isArray(data?.results) ? data.results.slice(0, 6) : [];
      if (!results.length && !data?.answer) throw new Error("Tavily empty");
      const lines = results.map((r, i) => `${i + 1}. ${r.title || r.url}${r.content ? `：${trim(r.content, 96)}` : ""}`);
      const meta = modelMeta("Tavily", "tavily-search", "Tavily Search");
      return {
        ok: true,
        reply: `${data?.answer ? `${data.answer}\n\n` : ""}已通过 Tavily 联网搜索“${query}”。${lines.length ? `\n${lines.join("\n")}` : ""}`,
        action: "chat",
        records: [],
        mobileCommand: null,
        webSearchUsed: true,
        webSearchMode: "orchestrator",
        sources: results.map((r) => ({ title: r.title || r.url, url: r.url, score: r.score, content: r.content })),
        citations: results.map((r) => r.url).filter(Boolean),
        source: "tavily_web_search",
        ...meta,
        version: versionWithMeta(meta.modelLabel),
      };
    } catch (error) {
      return { ok: false, error: String(error?.message || error) };
    }
  }
  return { ok: false, error: "missing_tavily_key" };
}

async function chatWithGemini(env, body, text, memory = {}) {
  const model = geminiModel(env);
  const meta = modelMeta("Gemini", model, geminiModelLabel(model));
  if (!env.GEMINI_API_KEY) {
    return baseResponse("云端 AI 暂时不可用：Worker 没有配置 GEMINI_API_KEY，所以普通聊天和知识问答无法调用 Gemini。", "gemini_missing_key", meta);
  }
  try {
    const endpoint = `${env.GEMINI_API_BASE || "https://generativelanguage.googleapis.com/v1beta/models"}/${model}:generateContent?key=${encodeURIComponent(env.GEMINI_API_KEY)}`;
    const messages = Array.isArray(body?.messages) ? body.messages.slice(-12) : [];
    const conversation = messages.map((m) => `${m?.role === "assistant" ? "助手" : "用户"}：${String(m?.content || "").slice(0, 1200)}`).join("\n");
    const prompt = [
      "你是手机端 AI 助手的普通聊天层。",
      "只回答普通聊天、学习解释、概念说明和非本地执行类问题。",
      "可以参考本地记忆理解上下文，但不要无故复述隐私记忆。",
      "不要生成 command/mobileCommand，不要声称已经执行手机动作。",
      "回答用中文，简洁自然；必要时分点，但不要太啰嗦。",
      "本地记忆：",
      JSON.stringify(memory || {}),
      "对话历史：",
      conversation || `用户：${text}`,
    ].join("\n");

    const res = await fetch(endpoint, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        contents: [{ role: "user", parts: [{ text: prompt }] }],
        generationConfig: { temperature: 0.35, maxOutputTokens: 1200 },
      }),
    });
    const data = await res.json().catch(() => null);
    if (!res.ok) throw new Error(data?.error?.message || `Gemini HTTP ${res.status}`);
    const reply = data?.candidates?.[0]?.content?.parts?.map((p) => p.text || "").join("\n").trim();
    return baseResponse(reply || "Gemini 没有返回可读内容。", "gemini_chat", meta);
  } catch (error) {
    return baseResponse(friendlyGeminiError(String(error?.message || error)), "gemini_chat_error", meta);
  }
}

function friendlyGeminiError(message) {
  const raw = String(message || "");
  if (/quota|rate.?limit|billing|exceeded|429/i.test(raw)) return "云端 AI 当前配额不足或被限流了。可以稍后再试，或更换/升级 Gemini Key。";
  if (/GEMINI_API_KEY|api key|permission|unauthorized|403|401/i.test(raw)) return "云端 AI 暂时不可用：Gemini Key 可能缺失、无权限或配置错误。";
  if (/abort|timeout|network|fetch|502|503|504|temporarily/i.test(raw)) return "云端 AI 请求超时或服务暂时不可用，请稍后再试。";
  return `云端 AI 暂时不可用：${raw.slice(0, 180) || "未知错误"}`;
}

function searchError(error) {
  const meta = modelMeta("Tavily", "tavily-search", "Tavily Search");
  return {
    reply: String(error || "").includes("missing_tavily_key")
      ? "当前还没有配置 TAVILY_API_KEY，所以无法进行稳定联网搜索。"
      : "联网搜索已经触发，但搜索源暂时请求失败。请检查 TAVILY_API_KEY 是否有效，或稍后再试。",
    action: "chat",
    records: [],
    mobileCommand: null,
    webSearchUsed: false,
    source: "web_search_error",
    ...meta,
    version: versionWithMeta(meta.modelLabel),
  };
}

function baseResponse(reply, source, meta = {}) {
  return {
    reply,
    action: "chat",
    records: [],
    mobileCommand: null,
    source,
    ...meta,
    version: versionWithMeta(meta.modelLabel),
  };
}

async function geocode(location) {
  const candidates = weatherLocationCandidates(location);
  for (const name of candidates) {
    const geo = await fetchJson(`https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(name)}&count=5&language=zh&format=json`);
    const results = Array.isArray(geo?.results) ? geo.results : [];
    const china = results.find((x) => x.country_code === "CN") || results[0];
    if (china?.latitude && china?.longitude) return china;
  }
  return null;
}

function weatherLocationCandidates(value) {
  const base = cleanWeatherLocation(value);
  if (!base) return [];
  const items = [base];
  if (base.endsWith("市") || base.endsWith("区") || base.endsWith("县")) items.push(base.slice(0, -1));
  else if (/^[\u4e00-\u9fa5]{2,6}$/u.test(base)) items.push(`${base}市`);
  const aliases = { 重庆: ["重庆市", "Chongqing"], 北京: ["北京市", "Beijing"], 上海: ["上海市", "Shanghai"], 天津: ["天津市", "Tianjin"], 广州: ["广州市", "Guangzhou"], 深圳: ["深圳市", "Shenzhen"], 成都: ["成都市", "Chengdu"], 温州: ["温州市", "Wenzhou"] };
  if (aliases[base]) items.push(...aliases[base]);
  return [...new Set(items)].slice(0, 7);
}

function extractWeatherLocation(text) {
  const raw = String(text || "").trim();
  if (/^(今天|今日|现在|当前)?(天气|气温|温度|下雨|会下雨吗|天气如何|天气怎么样)$/u.test(raw.replace(/[？?。！!\s]/g, ""))) return "";
  const patterns = [
    /(?:查一下|查询|看看|帮我查|帮我看看|请问)?\s*([\u4e00-\u9fa5A-Za-z .·-]{2,32}?)(?:今天|今日|现在|当前|明天|后天)?(?:的)?(?:天气|气温|温度|预报)/u,
    /(?:查一下|查询|看看|帮我查|帮我看看|请问)?\s*([\u4e00-\u9fa5A-Za-z .·-]{2,32}?)(?:今天|今日|明天|后天)?(?:会不会|会)?(?:下雨|降雨)/u,
    /(?:今天|今日|现在|当前|明天|后天)?\s*([\u4e00-\u9fa5A-Za-z .·-]{2,32}?)(?:会不会|会)?(?:下雨|降雨|天气|气温|温度)/u,
  ];
  for (const p of patterns) {
    const m = raw.match(p);
    if (m?.[1]) return cleanWeatherLocation(m[1]);
  }
  return cleanWeatherLocation(raw);
}

function cleanWeatherLocation(value) {
  const cleaned = String(value || "")
    .replace(/https?:\/\/\S+/gi, "")
    .replace(/请问|请|帮我|帮忙|给我|麻烦|上网|联网|搜索|搜一下|查一下|查询|看看|看一下|一下|今天|今日|现在|当前|明天|后天|天气|气温|温度|预报|下雨|降雨|会不会|会|不会|怎么样|如何|多少|几度|穿什么|适合|出门|带伞|的|吗|呢|啊|呀|吧|呗|哈|噻/gu, "")
    .replace(/[，。！？?、,.!！\s]/g, "")
    .trim()
    .slice(0, 32);
  if (/^(这里|这边|本地|当地|当前位置|当前城市|所在城市|我这里|我这边|附近|新闻|定义|生命|人工智能)$/u.test(cleaned)) return "";
  return cleaned;
}

async function fetchJson(url, options = {}, timeout = 9000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  try {
    const res = await fetch(url, { ...options, signal: controller.signal });
    const data = await res.json().catch(() => null);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return data;
  } finally {
    clearTimeout(timer);
  }
}

function cleanSearchQuery(text) {
  return String(text || "")
    .replace(/搜索一下|搜一下|查一下|查查|联网|上网查|联网查|今天的|今天|今日|有什么|吗|呢|吧|大新闻|新闻|热点|最新|最近/gu, " ")
    .replace(/\s+/g, " ")
    .trim() || "今日新闻";
}

function normalizeMemoryContext(input = {}) {
  if (!input || typeof input !== "object") return {};
  return {
    version: String(input.version || "").slice(0, 40),
    currentCity: cleanWeatherLocation(input.currentCity || ""),
    currentLocation: cleanWeatherLocation(input.currentLocation || ""),
    name: String(input.name || "").slice(0, 40),
    hometown: String(input.hometown || "").slice(0, 40),
    identity: String(input.identity || "").slice(0, 100),
    preferences: input.preferences && typeof input.preferences === "object" ? input.preferences : {},
    facts: Array.isArray(input.facts) ? input.facts.slice(-18).map((x) => ({ key: String(x?.key || "").slice(0, 40), value: String(x?.value || "").slice(0, 180) })).filter((x) => x.value) : [],
  };
}

function isExplicitSearchQuery(text) {
  return /(搜一下|搜索|查一下|查查|联网查|上网查|帮我查|看一下.*最新|今天.*新闻|今日.*新闻|大新闻|热点|最新消息|新闻)/u.test(String(text || ""));
}

function isWeatherLikeText(text) {
  return /(天气|下雨|气温|温度|风速|降雨|穿什么|预报|几度|带伞)/u.test(String(text || ""));
}

function isWeatherLocationFollowup(body, text) {
  const cleaned = cleanWeatherLocation(text);
  if (!cleaned || cleaned.length > 12 || /(新闻|最新|搜索|查|导航|记账|提醒|定义|什么|怎么|如何)/u.test(String(text || ""))) return false;
  const prevAssistant = previousAssistantText(body);
  return /哪个城市.*天气|想查.*天气|城市.*天气/u.test(prevAssistant);
}

function isForcedWebSearch(body) {
  const mode = String(body?.webSearchMode || body?.searchMode || body?.webSearch?.mode || "").toLowerCase();
  return body?.forceWebSearch === true || body?.webSearch?.force === true || mode === "force";
}

function geminiModel(env) {
  return String(env.GEMINI_CHAT_MODEL || env.GEMINI_MODEL || "gemini-2.5-flash").replace(/^models\//, "");
}

function geminiModelLabel(model) {
  const value = String(model || "");
  if (/2\.5.*flash/i.test(value)) return "Gemini 2.5 Flash";
  if (/2\.5.*pro/i.test(value)) return "Gemini 2.5 Pro";
  if (/2\.0.*flash/i.test(value)) return "Gemini 2.0 Flash";
  if (/1\.5.*flash/i.test(value)) return "Gemini 1.5 Flash";
  if (/1\.5.*pro/i.test(value)) return "Gemini 1.5 Pro";
  return value || "Gemini";
}

function modelMeta(provider, model, label) {
  const cleanProvider = String(provider || "").trim();
  const cleanModel = String(model || "").trim();
  const cleanLabel = String(label || cleanModel || cleanProvider || "Cloud Model").trim();
  return {
    provider: cleanProvider,
    model: cleanModel,
    modelLabel: cleanLabel,
  };
}

function versionWithMeta(label) {
  return appendRunLabel(ORCHESTRATOR_VERSION, label);
}

function appendRunLabel(version, label) {
  const cleanLabel = String(label || "").trim();
  if (!cleanLabel) return version;
  if (String(version || "").includes(cleanLabel)) return version;
  return `${version} · ${cleanLabel}`;
}

function trim(text, max = 120) {
  const value = String(text || "").replace(/\s+/g, " ").trim();
  return value.length > max ? `${value.slice(0, max)}…` : value;
}

function round(value) { return Number.isFinite(Number(value)) ? Math.round(Number(value)) : "未知"; }
function weatherText(code) { return ({ 0: "晴", 1: "大致晴朗", 2: "局部多云", 3: "阴天", 45: "有雾", 51: "小毛毛雨", 61: "小雨", 63: "中雨", 65: "大雨", 71: "小雪", 80: "阵雨", 95: "雷暴" })[Number(code)] || "天气状况未知"; }
function lastUserText(messages, fallback) { const m = Array.isArray(messages) ? [...messages].reverse().find((x) => x?.role === "user" && String(x?.content || "").trim()) : null; return String(m?.content || fallback || "").trim(); }
function cors(request, env) { const origin = request.headers.get("Origin") || ""; const allowed = String(env.ALLOWED_ORIGINS || "*").split(",").map((x) => x.trim()).filter(Boolean); const allow = allowed.includes("*") || allowed.includes(origin) ? origin || "*" : allowed[0] || "*"; return { ...JSON_HEADERS, "access-control-allow-origin": allow, "access-control-allow-methods": "GET, POST, OPTIONS", "access-control-allow-headers": "content-type", vary: "Origin" }; }
function json(payload, status = 200, headers = {}) { return new Response(JSON.stringify(payload), { status, headers: { ...JSON_HEADERS, ...headers } }); }

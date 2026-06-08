const http = require("http");
const crypto = require("crypto");

const PORT = Number(process.env.PORT || process.env.FC_SERVER_PORT || 9000);
const REQUEST_TIMEOUT_MS = Number(process.env.PROVIDER_TIMEOUT_MS || 30000);
const TOOL_ROUTER_TIMEOUT_MS = Number(process.env.TOOL_ROUTER_TIMEOUT_MS || 18000);
const STRUCTURED_ROUTER_TIMEOUT_MS = Number(process.env.STRUCTURED_ROUTER_TIMEOUT_MS || 2800);
const SEARCH_TIMEOUT_MS = Number(process.env.SEARCH_TIMEOUT_MS || 6000);
const DEVICE_ROUTER_TIMEOUT_MS = Number(process.env.DEVICE_ROUTER_TIMEOUT_MS || 2800);
const ENABLE_DEVICE_MODEL_ROUTER = String(process.env.ENABLE_DEVICE_MODEL_ROUTER || "false").toLowerCase() === "true";
const ENABLE_AUTO_WEB_SEARCH_ON_ONLINE = String(process.env.ENABLE_AUTO_WEB_SEARCH_ON_ONLINE || "false").toLowerCase() === "true";
const AGENT_PLANNER_TIMEOUT_MS = Number(process.env.AGENT_PLANNER_TIMEOUT_MS || 7000);
const AGENT_STEP_TOTAL_BUDGET_MS = Number(process.env.AGENT_STEP_TOTAL_BUDGET_MS || 18000);
const AGENT_STEP_VISION_TIMEOUT_MS = Number(process.env.AGENT_STEP_VISION_TIMEOUT_MS || process.env.AGENT_REALTIME_VISION_TIMEOUT_MS || 15000);
const AGENT_FAST_VISION_MAX_TOKENS = Number(process.env.AGENT_FAST_VISION_MAX_TOKENS || 180);
const AGENT_TEXT_PLANNER_TIMEOUT_MS = Number(process.env.AGENT_TEXT_PLANNER_TIMEOUT_MS || 7000);
const AGENT_STEP_TEXT_PLANNER_TIMEOUT_MS = Number(process.env.AGENT_STEP_TEXT_PLANNER_TIMEOUT_MS || 1000);
const AGENT_STEP_FALLBACK_MIN_BUDGET_MS = Number(process.env.AGENT_STEP_FALLBACK_MIN_BUDGET_MS || 900);
const AGENT_ROUTE_PLANNER_TIMEOUT_MS = Number(process.env.AGENT_ROUTE_PLANNER_TIMEOUT_MS || 1800);
const AGENT_ROUTE_PLANNER_MAX_TOKENS = Number(process.env.AGENT_ROUTE_PLANNER_MAX_TOKENS || 360);
const AGENT_RESPONSE_SAFETY_MARGIN_MS = Number(process.env.AGENT_RESPONSE_SAFETY_MARGIN_MS || 350);
const AGENT_VISION_MAX_TOKENS = Number(process.env.AGENT_VISION_MAX_TOKENS || 360);
const AGENT_TEXT_MAX_TOKENS = Number(process.env.AGENT_TEXT_MAX_TOKENS || 300);
const MAX_BODY_BYTES = Number(process.env.MAX_BODY_BYTES || 16 * 1024 * 1024);
const AGENT_SESSION_TTL_MS = Number(process.env.AGENT_SESSION_TTL_MS || 8 * 60 * 1000);
const AGENT_SESSION_MAX = Number(process.env.AGENT_SESSION_MAX || 128);
const AGENT_VISUAL_CACHE_MIN_CONFIDENCE = Number(process.env.AGENT_VISUAL_CACHE_MIN_CONFIDENCE || 0.50);
const AGENT_VISUAL_FRAME_MIN_SIGNAL_CONFIDENCE = Number(process.env.AGENT_VISUAL_FRAME_MIN_SIGNAL_CONFIDENCE || 0.45);
const AGENT_SESSIONS = new Map();

const AGENT_GUI_PROVIDER = normalizeAgentGuiProviderName(process.env.AGENT_GUI_PROVIDER || process.env.GUI_PROVIDER || "qwen_omni");
const AGENT_GUI_PROVIDER_URL = String(process.env.AGENT_GUI_PROVIDER_URL || process.env.GUI_PROVIDER_URL || "").trim();
const AGENT_GUI_PROVIDER_BASE_URL = String(process.env.AGENT_GUI_PROVIDER_BASE_URL || process.env.GUI_PROVIDER_BASE_URL || "").trim();
const AGENT_GUI_PROVIDER_API_KEY = String(process.env.AGENT_GUI_PROVIDER_API_KEY || process.env.GUI_PROVIDER_API_KEY || "").trim();
const AGENT_GUI_PROVIDER_MODEL = String(process.env.AGENT_GUI_PROVIDER_MODEL || process.env.GUI_PROVIDER_MODEL || "").trim();
const AGENT_GUI_PROVIDER_TIMEOUT_MS = Number(process.env.AGENT_GUI_PROVIDER_TIMEOUT_MS || process.env.GUI_PROVIDER_TIMEOUT_MS || AGENT_STEP_VISION_TIMEOUT_MS);
const AGENT_GUI_PROVIDER_MAX_TOKENS = Number(process.env.AGENT_GUI_PROVIDER_MAX_TOKENS || process.env.GUI_PROVIDER_MAX_TOKENS || AGENT_FAST_VISION_MAX_TOKENS);
const AGENT_GUI_PROVIDER_FALLBACK_TO_QWEN = String(process.env.AGENT_GUI_PROVIDER_FALLBACK_TO_QWEN || "true").toLowerCase() !== "false";
const ALIYUN_GUI_API_KEY = String(process.env.ALIYUN_GUI_API_KEY || process.env.QWEN_API_KEY || "").trim();
const ALIYUN_GUI_BASE_URL = String(process.env.ALIYUN_GUI_BASE_URL || "https://dashscope.aliyuncs.com/compatible-mode/v1").trim();
const ALIYUN_GUI_MODEL = String(process.env.ALIYUN_GUI_MODEL || "gui-plus-2026-02-26").trim();
const ALIYUN_GUI_TIMEOUT_MS = Number(process.env.ALIYUN_GUI_TIMEOUT_MS || 15000);
const ALIYUN_GUI_MAX_TOKENS = Number(process.env.ALIYUN_GUI_MAX_TOKENS || 512);
const ALIYUN_GUI_API_MODE = String(process.env.ALIYUN_GUI_API_MODE || "openai_compatible").trim().toLowerCase();
const ALIYUN_GUI_HIGH_RESOLUTION_IMAGES = String(process.env.ALIYUN_GUI_HIGH_RESOLUTION_IMAGES || "true").toLowerCase() !== "false";
const ALIYUN_GUI_ENABLE_THINKING = String(process.env.ALIYUN_GUI_ENABLE_THINKING || "false").toLowerCase() === "true";
const AGENT_GUI_HISTORY_N = Math.max(0, Math.min(6, Number(process.env.AGENT_GUI_HISTORY_N || 4)));
const AGENT_GUI_SESSION_MAX = Math.max(4, Math.min(64, Number(process.env.AGENT_GUI_SESSION_MAX || 24)));

// v34 架构保护开关：普通聊天、显式智能体、agent_step、联网工具彻底分流。
// 默认关闭历史“普通聊天里用关键词/模型路由触发手机动作”的行为。
const ENABLE_LEGACY_CHAT_DEVICE_ROUTER = String(process.env.ENABLE_LEGACY_CHAT_DEVICE_ROUTER || "false").toLowerCase() === "true";
const ENABLE_MODEL_COMMANDS_IN_NORMAL_CHAT = String(process.env.ENABLE_MODEL_COMMANDS_IN_NORMAL_CHAT || "false").toLowerCase() === "true";
const ENABLE_AGENT_SUGGESTION_CARD = String(process.env.ENABLE_AGENT_SUGGESTION_CARD || "false").toLowerCase() === "true";


const WORKER_VERSION = "qwen-deepseek-cn-web-data-v40-aliyun-gui-plus-official-loop";
const EMBEDDED_COMMAND_PREFIX = "[[AI_LEDGER_COMMAND:";
const EMBEDDED_COMMAND_SUFFIX = "]]";

function sendJson(res, status, data) {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "content-type, authorization, x-client, x-ai-ledger-stream",
  });
  res.end(JSON.stringify(data));
}

function wantsSseStream(req, body = {}) {
  const accept = String(req?.headers?.accept || "").toLowerCase();
  const streamHeader = String(req?.headers?.["x-ai-ledger-stream"] || "").toLowerCase();
  const responseMode = String(body.responseMode || body.streamMode || "").toLowerCase();
  const streamFormat = String(body.streamFormat || body.format || "").toLowerCase();

  return Boolean(
    body.stream === true ||
      body.streaming === true ||
      responseMode === "stream" ||
      responseMode === "sse" ||
      streamFormat === "sse" ||
      streamHeader === "sse" ||
      accept.includes("text/event-stream")
  );
}

function sendSseHeaders(res) {
  if (res.headersSent) return;
  res.writeHead(200, {
    "Content-Type": "text/event-stream; charset=utf-8",
    "Cache-Control": "no-cache, no-transform",
    "Connection": "keep-alive",
    "X-Accel-Buffering": "no",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "content-type, authorization, x-client, x-ai-ledger-stream",
  });
  if (typeof res.flushHeaders === "function") res.flushHeaders();
}

function writeSse(res, data) {
  res.write(`data: ${JSON.stringify(data)}\n\n`);
}

function writeSseDone(res) {
  res.write("data: [DONE]\n\n");
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
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch (e) {
        reject(new Error("invalid_json"));
      }
    });

    req.on("error", reject);
  });
}

function extractTextFromContent(content) {
  if (typeof content === "string") return content;
  if (!Array.isArray(content)) return "";
  return content
    .map((part) => {
      if (!part || typeof part !== "object") return "";
      if (typeof part.text === "string") return part.text;
      if (part.type === "text" && typeof part.content === "string") return part.content;
      return "";
    })
    .join("\n")
    .trim();
}

function latestUserText(messages) {
  if (!Array.isArray(messages)) return "";

  for (let i = messages.length - 1; i >= 0; i -= 1) {
    const item = messages[i] || {};
    const role = String(item.role || "").toLowerCase();
    const content = item.content !== undefined ? item.content : item.text;
    const text = extractTextFromContent(content);

    if (role === "user" && text.trim()) {
      return text.trim();
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
    const content = item?.content !== undefined ? item.content : item?.text;
    const text = extractTextFromContent(content).trim();

    if (!role || !text) continue;
    clean.push({ role, content: text });
  }

  while (clean.length && clean[0].role !== "user") clean.shift();

  const fallback = String(fallbackText || "").trim();
  if (!clean.length && fallback) clean.push({ role: "user", content: fallback });

  return clean.slice(-16);
}

function normalizeImages(body) {
  const raw = [
    ...(Array.isArray(body?.images) ? body.images : []),
    ...(Array.isArray(body?.attachments) ? body.attachments : []),
  ];

  const clean = [];
  const seen = new Set();

  for (const item of raw) {
    if (!item || typeof item !== "object") continue;

    const mimeType = String(item.mimeType || item.mediaType || "image/jpeg").trim() || "image/jpeg";
    const base64Data = String(item.base64Data || item.imageBase64 || item.data || "").trim();

    if (!base64Data) continue;

    const key = `${mimeType}:${base64Data.slice(0, 64)}:${base64Data.length}`;
    if (seen.has(key)) continue;
    seen.add(key);

    clean.push({
      mimeType: mimeType.startsWith("image/") ? mimeType : "image/jpeg",
      base64Data,
      width: Number(item.width) || undefined,
      height: Number(item.height) || undefined,
      sizeBytes: Number(item.sizeBytes) || undefined,
      fileName: String(item.fileName || "").trim().slice(0, 80),
    });

    if (clean.length >= 4) break;
  }

  return clean;
}

function buildVisionMessages(prompt, images, body = {}) {
  const userPrompt = String(prompt || "请识别这张图片，说明图中内容，并回答我可能关心的问题。").trim();

  const systemBlocks = [
    "你是一个中文图片理解助手，服务于 Android Compose AI 助手。",
    "你需要认真识别用户上传的图片，包括截图、题目、电路图、表格、页面、手写内容、文字、图标、坐标图和细节。",
    "如果图片是题目或学习资料，请先说明关键信息，再给出解题思路和答案。",
    "如果图片是 App 页面或手机界面，只能分析页面内容，不要声称你已经操作了手机。",
    "回答要清晰、可靠、自然。无法确认的细节要说明不确定，不要编造。",
  ];

  const commandInstruction = String(body.commandProtocolInstruction || body.systemPrompt || "").trim();
  if (shouldAllowModelCommandsInChat(body) && commandInstruction) {
    systemBlocks.push(commandInstruction);
  }

  return [
    {
      role: "system",
      content: systemBlocks.join("\n"),
    },
    {
      role: "user",
      content: [
        { type: "text", text: userPrompt },
        ...images.map((image) => ({
          type: "image_url",
          image_url: {
            url: `data:${image.mimeType};base64,${image.base64Data}`,
          },
        })),
      ],
    },
  ];
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
  if (["qwen_vision", "qwen-vision", "qwen_omni", "qwen-omni"].includes(pref) || pref.includes("omni")) return "qwen_vision";
  if (["qwen", "qwen_max", "qwen-max", "qwen_plus", "qwen-plus", "kimi"].includes(pref) || pref.startsWith("qwen")) return "qwen";
  if (["deepseek", "deepseek_v4", "deepseek-v4", "deepseek_v4_pro", "deepseek-v4-pro"].includes(pref)) return "deepseek_v4";

  return "unsupported";
}

function hasFreshSearchSignal(prompt) {
  const text = String(prompt || "").toLowerCase();
  return hasAny(text, [
    "联网", "搜索", "查一下", "帮我查", "搜一下", "网上", "资料", "引用", "来源",
    "最新", "现在", "今天", "当前", "实时", "新闻", "价格", "股价", "行情", "天气", "汇率",
    "today", "latest", "current", "real-time", "news", "price", "weather", "stock"
  ]);
}

function isForceWebSearch(body, prompt = "") {
  const mode = String(body.webSearchMode || body.searchMode || body.webSearch?.mode || "").toLowerCase();
  if (body.forceWebSearch || body.webSearch?.force || mode === "force") return true;

  // 关键保护：onlineEnabled/searchEnabled 在 App 里可能是常驻开关，不能把每次普通问答都变成联网/工具路由。
  // 只有显式打开 ENABLE_AUTO_WEB_SEARCH_ON_ONLINE 且文本确实像实时检索时，才自动联网。
  if (ENABLE_AUTO_WEB_SEARCH_ON_ONLINE && (body.onlineEnabled || body.searchEnabled || mode === "auto")) {
    return hasFreshSearchSignal(prompt);
  }

  return false;
}

async function fetchWithTimeout(url, options = {}, timeoutMs = REQUEST_TIMEOUT_MS) {
  if (typeof fetch !== "function") {
    throw new Error("Node runtime does not support fetch. Please use Node.js 18 or Node.js 20 in Aliyun FC.");
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

function agentRemainingBudgetMs(startedAt) {
  const elapsed = Date.now() - Number(startedAt || Date.now());
  return Math.max(0, AGENT_STEP_TOTAL_BUDGET_MS - elapsed);
}

function boundedAgentTimeoutMs(preferredMs, remainingMs, fallbackMs = 1200) {
  const preferred = Number(preferredMs);
  const fallback = Number(fallbackMs);
  const base = Number.isFinite(preferred) && preferred > 0 ? preferred : fallback;
  const room = Number.isFinite(remainingMs) && remainingMs > 0
    ? Math.max(300, remainingMs - AGENT_RESPONSE_SAFETY_MARGIN_MS)
    : base;
  return Math.max(300, Math.min(base, room));
}

function isAgentBudgetNearlyExhausted(startedAt, minMs = AGENT_STEP_FALLBACK_MIN_BUDGET_MS) {
  return agentRemainingBudgetMs(startedAt) < minMs;
}

function isTimeoutLikeError(error) {
  const name = String(error?.name || "").toLowerCase();
  const message = String(error?.message || error || "").toLowerCase();
  return (
    name.includes("abort") ||
    name.includes("timeout") ||
    message.includes("abort") ||
    message.includes("timeout") ||
    message.includes("timed out") ||
    message.includes("etimedout") ||
    message.includes("exceeded")
  );
}

function sanitizeProviderError(error, max = 160) {
  return String(error?.message || error || "provider_failed")
    .replace(/Bearer\s+[A-Za-z0-9_\-\.]+/gi, "Bearer ***")
    .slice(0, max);
}


async function callOpenAICompatible(base, key, model, messages, name, options = {}) {
  if (!key) throw new Error(`${name} key missing`);
  if (!base) throw new Error(`${name} base url missing`);
  if (!model) throw new Error(`${name} model missing`);

  const endpoint = `${String(base).replace(/\/+$/g, "")}/chat/completions`;

  const payload = {
    model,
    messages,
    temperature: options.temperature ?? 0.35,
    max_tokens: options.max_tokens ?? 1200,
    stream: false,
  };

  if (options.response_format) {
    payload.response_format = options.response_format;
  }
  if (options.extraBody && typeof options.extraBody === "object") {
    Object.assign(payload, options.extraBody);
  }

  const extraHeaders = options.headers && typeof options.headers === "object" ? options.headers : {};
  const r = await fetchWithTimeout(endpoint, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${key}`,
      ...extraHeaders,
    },
    body: JSON.stringify(payload),
  }, options.timeoutMs || REQUEST_TIMEOUT_MS);

  const t = await r.text();

  if (!r.ok) {
    throw new Error(`${name} ${r.status} ${t.slice(0, 300)}`);
  }

  let data;
  try {
    data = JSON.parse(t);
  } catch (e) {
    throw new Error(`${name} invalid_json_response ${t.slice(0, 160)}`);
  }

  const choice = data?.choices?.[0] || {};
  const message = choice.message || {};
  const contentReply = message.content || choice.text || data?.reply || "";
  const toolCalls = Array.isArray(message.tool_calls) ? message.tool_calls : [];
  const functionCall = message.function_call || null;
  const nativeToolPayload = toolCalls.length
    ? toolCalls.map((item) => ({
        name: item?.function?.name || item?.name || "",
        arguments: item?.function?.arguments || item?.arguments || item?.args || {},
      }))
    : functionCall
      ? [{ name: functionCall.name || "", arguments: functionCall.arguments || {} }]
      : [];

  const reply = String(contentReply || "").trim() || (nativeToolPayload.length ? JSON.stringify({ tool_calls: nativeToolPayload }) : "");

  if (!String(reply).trim()) {
    throw new Error(`${name} empty`);
  }

  return String(reply).trim();
}


function openAiStreamPayloadText(payloadText) {
  const clean = String(payloadText || "").trim();
  if (!clean || clean === "[DONE]") return { text: "", done: clean === "[DONE]" };

  let data;
  try {
    data = JSON.parse(clean);
  } catch (_) {
    return { text: "", done: false };
  }

  const choice = data?.choices?.[0] || {};
  const delta = choice.delta || {};
  const message = choice.message || {};
  const directText = data.delta ?? data.text ?? data.content ?? data.reply;
  let text = "";

  if (typeof delta.content === "string") text = delta.content;
  else if (Array.isArray(delta.content)) {
    text = delta.content
      .map((part) => typeof part === "string" ? part : typeof part?.text === "string" ? part.text : "")
      .join("");
  } else if (typeof message.content === "string") {
    text = message.content;
  } else if (typeof directText === "string") {
    text = directText;
  }

  return {
    text,
    done: Boolean(choice.finish_reason || data.done === true || data.type === "done"),
  };
}

async function callOpenAICompatibleStream(base, key, model, messages, name, options = {}) {
  if (!key) throw new Error(`${name} key missing`);
  if (!base) throw new Error(`${name} base url missing`);
  if (!model) throw new Error(`${name} model missing`);

  const endpoint = `${String(base).replace(/\/+$/g, "")}/chat/completions`;

  const payload = {
    model,
    messages,
    temperature: options.temperature ?? 0.35,
    max_tokens: options.max_tokens ?? 1200,
    stream: true,
  };

  const r = await fetchWithTimeout(endpoint, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "accept": "text/event-stream",
      authorization: `Bearer ${key}`,
    },
    body: JSON.stringify(payload),
  }, options.timeoutMs || REQUEST_TIMEOUT_MS);

  if (!r.ok) {
    const t = await r.text();
    throw new Error(`${name} stream ${r.status} ${t.slice(0, 300)}`);
  }

  if (!r.body || typeof r.body.getReader !== "function") {
    throw new Error(`${name} stream body unavailable`);
  }

  if (typeof options.onStreamStart === "function") options.onStreamStart();

  const reader = r.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let reply = "";
  let finished = false;

  function consumeEvent(eventText) {
    const dataLines = String(eventText || "")
      .split(/\r?\n/g)
      .map((line) => line.trimEnd())
      .filter((line) => line.startsWith("data:"))
      .map((line) => line.slice(5).trimStart());

    if (!dataLines.length) return;
    const payloadText = dataLines.join("\n").trim();
    const parsed = openAiStreamPayloadText(payloadText);

    if (parsed.text) {
      reply += parsed.text;
      if (typeof options.onDelta === "function") options.onDelta(parsed.text);
    }

    if (parsed.done) finished = true;
  }

  while (!finished) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });

    let boundary = buffer.indexOf("\n\n");
    while (boundary >= 0) {
      const eventText = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      consumeEvent(eventText);
      boundary = buffer.indexOf("\n\n");
    }
  }

  buffer += decoder.decode();
  if (buffer.trim()) consumeEvent(buffer);

  const cleanReply = String(reply || "").trim();
  if (!cleanReply) {
    throw new Error(`${name} stream empty`);
  }

  return cleanReply;
}

async function callOpenAICompatibleJsonFirst(base, key, model, messages, name, options = {}) {
  const jsonOptions = { ...options, response_format: options.response_format || { type: "json_object" } };

  try {
    return await callOpenAICompatible(base, key, model, messages, name, jsonOptions);
  } catch (error) {
    const message = String(error?.message || error || "");
    const mayBeJsonModeUnsupported = /response_format|json_object|invalid_parameter|unsupported|not support|不支持/i.test(message);

    if (!mayBeJsonModeUnsupported) throw error;

    return await callOpenAICompatible(
      base,
      key,
      model,
      messages,
      `${name} FallbackNoJsonMode`,
      { ...options, response_format: undefined }
    );
  }
}

function extractJsonText(text) {
  const raw = String(text || "").trim();
  if (raw.startsWith("{") && raw.endsWith("}")) return raw;

  const fenced = raw.match(/```(?:json)?\s*([\s\S]*?)```/i);
  if (fenced && fenced[1]) {
    const inner = fenced[1].trim();
    if (inner.startsWith("{") && inner.endsWith("}")) return inner;
  }

  const match = raw.match(/\{[\s\S]*\}/);
  if (match) return match[0];

  return raw;
}

function extractEmbeddedCommand(reply) {
  const raw = String(reply || "");
  const start = raw.indexOf(EMBEDDED_COMMAND_PREFIX);
  if (start < 0) return null;

  const jsonStart = start + EMBEDDED_COMMAND_PREFIX.length;
  const end = raw.indexOf(EMBEDDED_COMMAND_SUFFIX, jsonStart);
  if (end < 0) return null;

  const payload = raw.slice(jsonStart, end).trim();
  if (!payload.startsWith("{") || !payload.endsWith("}")) return null;

  try {
    return JSON.parse(payload);
  } catch (e) {
    return null;
  }
}

function stripEmbeddedCommand(reply) {
  const raw = String(reply || "");
  const start = raw.indexOf(EMBEDDED_COMMAND_PREFIX);
  if (start < 0) return raw.trim();

  const jsonStart = start + EMBEDDED_COMMAND_PREFIX.length;
  const end = raw.indexOf(EMBEDDED_COMMAND_SUFFIX, jsonStart);
  if (end < 0) return raw.trim();

  return (raw.slice(0, start) + raw.slice(end + EMBEDDED_COMMAND_SUFFIX.length)).trim();
}

function normalizeAgentAction(value) {
  if (!value || typeof value !== "object") return null;
  const capability = String(value.capability || value.type || value.action || "")
    .toLowerCase()
    .trim()
    .replace(/-/g, "_");

  if (!["observe_screen", "run_agent_task"].includes(capability)) return null;

  const isRunTask = capability === "run_agent_task";
  const goal = String(value.goal || value.task || value.instruction || value.query || value.prompt || "").trim();

  if (isRunTask && !goal) return null;

  return {
    capability,
    title: String(value.title || (isRunTask ? "手机智能体任务" : "观察当前屏幕")).trim().slice(0, 40) || (isRunTask ? "手机智能体任务" : "观察当前屏幕"),
    goal: isRunTask ? goal.slice(0, 240) : undefined,
    requiresConfirmation: Boolean(value.requiresConfirmation),
    reason: String(value.reason || (isRunTask ? "用户明确要求操作手机完成任务" : "用户希望手机智能体读取当前界面")).trim().slice(0, 160),
  };
}

function normalizeMobileAction(value) {
  if (!value || typeof value !== "object") return null;
  const type = String(value.type || value.action || "").toLowerCase().trim().replace(/-/g, "_");
  if (!["set_alarm", "open_app", "navigate"].includes(type)) return null;

  const action = { type };

  if (type === "navigate") {
    const destination = String(value.destination || value.target || "").trim();
    if (!destination) return null;
    action.destination = destination.slice(0, 80);
  }

  if (type === "open_app") {
    const appName = String(value.appName || value.app || value.title || "").trim();
    if (!appName) return null;
    action.appName = appName.slice(0, 40);
    const packageName = String(value.packageName || value.package || "").trim();
    if (packageName) action.packageName = packageName.slice(0, 80);
  }

  if (type === "set_alarm") {
    const hour = Number(value.hour);
    const minute = Number(value.minute ?? 0);
    if (!Number.isInteger(hour) || hour < 0 || hour > 23) return null;
    if (!Number.isInteger(minute) || minute < 0 || minute > 59) return null;
    action.hour = hour;
    action.minute = minute;
    action.label = String(value.label || value.message || "AI 助手提醒").trim().slice(0, 40) || "AI 助手提醒";
  }

  return action;
}

function normalizePreferenceUpdate(value) {
  if (!value || typeof value !== "object") return null;
  const type = String(value.type || "").toLowerCase().trim().replace(/-/g, "_");
  if (type !== "navigation_address") return null;

  const slot = String(value.slot || "").toLowerCase().trim().replace(/-/g, "_");
  if (!["home", "school", "company", "dorm"].includes(slot)) return null;

  const rawValue = String(value.value || value.address || value.destination || "").trim();
  if (!rawValue) return null;

  const label = String(value.label || ({ home: "家", school: "学校", company: "公司", dorm: "宿舍" })[slot] || slot).trim();

  return {
    type: "navigation_address",
    slot,
    label: label.slice(0, 12),
    value: rawValue.slice(0, 80),
  };
}

function extractCommandPayload(reply) {
  const embedded = extractEmbeddedCommand(reply);
  if (!embedded || typeof embedded !== "object") {
    return { agentAction: null, mobileAction: null, preferenceUpdate: null };
  }

  return {
    agentAction: normalizeAgentAction(embedded.agentAction),
    mobileAction: normalizeMobileAction(embedded.mobileAction),
    preferenceUpdate: normalizePreferenceUpdate(embedded.preferenceUpdate),
  };
}

function isCommandProtocolEnabled(body) {
  return Boolean(
    body?.commandProtocol?.enabled ||
      body?.responseFormat?.includeAgentAction ||
      body?.responseFormat?.includeMobileAction ||
      body?.responseFormat?.includePreferenceUpdate ||
      body?.commandProtocolInstruction
  );
}

function mightNeedDeviceRouter(prompt) {
  const text = String(prompt || "").toLowerCase();
  return hasAny(text, [
    "手机", "屏幕", "界面", "页面", "按钮", "输入框", "智能体", "无障碍", "观察", "读取", "识别", "看一下", "看看",
    "打开", "启动", "进入", "导航", "闹钟", "提醒", "设置为", "回家", "去学校", "app", "应用", "wechat", "bilibili", "同花顺", "热榜", "联系人", "朋友圈"
  ]);
}

function shouldAllowModelCommandsInChat(body) {
  return Boolean(
    ENABLE_MODEL_COMMANDS_IN_NORMAL_CHAT ||
      body?.intent === "command_chat" ||
      body?.commandProtocol?.allowModelCommands === true ||
      body?.responseFormat?.allowModelCommands === true
  );
}

function normalizeIntentName(value) {
  return String(value || "").toLowerCase().trim().replace(/[\s\-]+/g, "_");
}

function normalizeExplicitDeviceIntent(body, prompt = "") {
  const intent = normalizeIntentName(body?.intent || body?.action || body?.type || body?.requestType);
  const agentActionFromBody = normalizeAgentAction(body?.agentAction || body?.commandProtocol?.agentAction);
  const mobileActionFromBody = normalizeMobileAction(body?.mobileAction || body?.commandProtocol?.mobileAction);
  const preferenceUpdateFromBody = normalizePreferenceUpdate(body?.preferenceUpdate || body?.commandProtocol?.preferenceUpdate);

  if (agentActionFromBody) {
    return {
      agentAction: agentActionFromBody,
      mobileAction: null,
      preferenceUpdate: null,
      reason: "explicit_agent_action_payload",
      source: "explicit_payload",
    };
  }

  if (mobileActionFromBody || preferenceUpdateFromBody) {
    return {
      agentAction: null,
      mobileAction: mobileActionFromBody,
      preferenceUpdate: preferenceUpdateFromBody,
      reason: "explicit_mobile_or_preference_payload",
      source: "explicit_payload",
    };
  }

  const wantsAgentStart = Boolean(
    intent === "agent_start" ||
      intent === "run_agent_task" ||
      intent === "mobile_agent_task" ||
      body?.agentStart === true ||
      body?.runAgentTask === true ||
      body?.commandProtocol?.intent === "agent_start" ||
      body?.commandProtocol?.mode === "agent_start"
  );

  if (wantsAgentStart) {
    const goal = safeText(body?.agentGoal || body?.goal || body?.task || prompt, 240);
    if (!goal) {
      return { agentAction: null, mobileAction: null, preferenceUpdate: null, reason: "empty_explicit_agent_goal", source: "explicit_payload" };
    }
    const compact = goal.replace(/\s+/g, "");
    return {
      agentAction: {
        capability: "run_agent_task",
        title: safeText(body?.title || "手机智能体任务", 40),
        goal,
        requiresConfirmation: HIGH_RISK_AGENT_WORDS.some((word) => compact.includes(word)),
        reason: "显式 agent_start 请求，进入手机智能体专用链路。",
      },
      mobileAction: null,
      preferenceUpdate: null,
      reason: "explicit_agent_start",
      source: "explicit_payload",
    };
  }

  const wantsObserve = Boolean(
    intent === "observe_screen" ||
      intent === "screen_observation" ||
      body?.observeScreen === true ||
      body?.commandProtocol?.intent === "observe_screen" ||
      body?.commandProtocol?.mode === "observe_screen"
  );

  if (wantsObserve) {
    return {
      agentAction: {
        capability: "observe_screen",
        title: safeText(body?.title || "观察当前屏幕", 40),
        requiresConfirmation: false,
        reason: "显式 observe_screen 请求，进入屏幕观察链路。",
      },
      mobileAction: null,
      preferenceUpdate: null,
      reason: "explicit_observe_screen",
      source: "explicit_payload",
    };
  }

  return { agentAction: null, mobileAction: null, preferenceUpdate: null, reason: "no_explicit_device_intent", source: "none" };
}

function buildDirectAgentAction(prompt, body = {}) {
  const explicit = normalizeExplicitDeviceIntent(body, prompt);
  return explicit.agentAction || null;
}

function normalizeDeviceRouterPayload(value) {
  if (!value || typeof value !== "object") return { agentAction: null, mobileAction: null, preferenceUpdate: null, reason: "" };
  return {
    agentAction: normalizeAgentAction(value.agentAction),
    mobileAction: normalizeMobileAction(value.mobileAction),
    preferenceUpdate: normalizePreferenceUpdate(value.preferenceUpdate),
    reason: String(value.reason || "").trim().slice(0, 160),
  };
}

async function detectDeviceIntentByModel(prompt, body) {
  const explicit = normalizeExplicitDeviceIntent(body, prompt);
  if (explicit.agentAction || explicit.mobileAction || explicit.preferenceUpdate) {
    return explicit;
  }

  // v34 默认普通聊天绝对不走设备模型路由，避免关键词和模型路由污染问答链路。
  if (!ENABLE_LEGACY_CHAT_DEVICE_ROUTER) {
    return { agentAction: null, mobileAction: null, preferenceUpdate: null, reason: "skip_legacy_device_router_clean_architecture", source: "skip" };
  }

  const explicitDeviceRouter = Boolean(
    body?.intent === "device_route" ||
      body?.intent === "device_action" ||
      body?.commandProtocol?.forceDeviceRouter ||
      body?.responseFormat?.forceDeviceRouter ||
      String(body?.deviceRouterMode || "").toLowerCase() === "force"
  );
  if (!explicitDeviceRouter && !(ENABLE_DEVICE_MODEL_ROUTER && isCommandProtocolEnabled(body) && mightNeedDeviceRouter(prompt))) {
    return { agentAction: null, mobileAction: null, preferenceUpdate: null, reason: "skip_device_model_router_normal_chat", source: "skip" };
  }

  if (!mightNeedDeviceRouter(prompt)) {
    return { agentAction: null, mobileAction: null, preferenceUpdate: null, reason: "skip_device_model_router_no_signal", source: "skip" };
  }

  const messages = [
    {
      role: "system",
      content: [
        "你是 Android 手机 AI 助手的设备能力路由器，只能输出严格 JSON，不能输出解释、Markdown 或代码块。",
        "你只判断用户是不是在请求手机本地能力；不要回答问题本身。",
        "普通问答、写作、翻译、代码、项目讨论，必须全部返回 null。",
        "只有明确要求操作手机或打开手机中的 App 时才触发。",
        "输出格式必须是单个 JSON 对象：",
        "{\"agentAction\":null|{\"capability\":\"observe_screen|run_agent_task\",\"title\":\"\",\"goal\":\"完整任务，仅 run_agent_task 需要\",\"requiresConfirmation\":false,\"reason\":\"\"},\"mobileAction\":null|{\"type\":\"open_app|navigate|set_alarm\",\"appName\":\"\",\"packageName\":\"\",\"destination\":\"\",\"hour\":8,\"minute\":0,\"label\":\"\"},\"preferenceUpdate\":null|{\"type\":\"navigation_address\",\"slot\":\"home|school|company|dorm\",\"label\":\"\",\"value\":\"\"},\"reason\":\"\"}",
      ].join("\n"),
    },
    { role: "user", content: String(prompt || "") },
  ];

  const raw = await callOpenAICompatible(
    process.env.QWEN_BASE_URL,
    process.env.QWEN_API_KEY,
    process.env.QWEN_MODEL,
    messages,
    "Qwen Device Router",
    {
      temperature: 0,
      max_tokens: Number(process.env.DEVICE_ROUTER_MAX_TOKENS || 220),
      timeoutMs: DEVICE_ROUTER_TIMEOUT_MS,
      response_format: { type: "json_object" },
    }
  );

  try {
    const parsed = JSON.parse(extractJsonText(raw));
    return { ...normalizeDeviceRouterPayload(parsed), source: "legacy_model_router", raw };
  } catch (e) {
    return { agentAction: null, mobileAction: null, preferenceUpdate: null, reason: `device_router_json_parse_failed: ${String(raw).slice(0, 120)}`, source: "legacy_model_router_error", raw };
  }
}

function buildDeviceActionReply(deviceIntent) {
  if (deviceIntent?.agentAction?.capability === "run_agent_task") return "我已识别到手机智能体任务，将交给本地智能体执行。";
  if (deviceIntent?.agentAction?.capability === "observe_screen") return "我已识别到手机智能体观察请求，将在本地读取当前屏幕结构。";
  if (deviceIntent?.mobileAction) return "我已识别到手机动作，请在本地确认后执行。";
  if (deviceIntent?.preferenceUpdate) return "我已识别到一项本地偏好更新。";
  return "我已识别到本地能力请求。";
}


const SUPPORTED_AGENT_STEP_TYPES = ["open_app", "home", "back", "recents", "notifications", "quick_settings", "tap_node", "tap_xy", "input_text", "scroll", "swipe", "wait", "finish", "need_user_help"];
const AGENT_ACTION_BATCH_MAX = Math.max(1, Math.min(3, Number(process.env.AGENT_ACTION_BATCH_MAX || 1)));
const HIGH_RISK_AGENT_WORDS = [
  "支付", "付款", "转账", "红包", "下单", "购买", "删除", "卸载", "授权", "同意",
  "发送", "发给", "提交", "发布", "评论", "私信", "验证码", "密码", "登录", "确认付款",
  "pay", "transfer", "delete", "send", "submit", "publish", "password", "login", "otp"
];

function safeText(value, max = 160) {
  return String(value ?? "").replace(/\s+/g, " ").trim().slice(0, max);
}

function normalizeAgentStepType(value) {
  const type = String(value || "").toLowerCase().trim().replace(/-/g, "_");
  if (SUPPORTED_AGENT_STEP_TYPES.includes(type)) return type;
  if (["tap_xy", "tapx_y", "coordinate_click", "coordinate_tap", "click_xy", "tap_point", "point_click"].includes(type)) return "tap_xy";
  if (["tap", "click", "press"].includes(type)) return "tap_xy";
  if (["input", "type", "enter_text"].includes(type)) return "input_text";
  if (["done", "complete", "completed"].includes(type)) return "finish";
  if (["ask_user", "need_help", "clarify"].includes(type)) return "need_user_help";
  return "need_user_help";
}

function normalizeAgentDirection(value) {
  const direction = String(value || "").toLowerCase().trim();
  if (["up", "down", "left", "right"].includes(direction)) return direction;
  if (["上", "向上", "上滑"].includes(direction)) return "up";
  if (["下", "向下", "下滑"].includes(direction)) return "down";
  if (["左", "向左", "左滑"].includes(direction)) return "left";
  if (["右", "向右", "右滑"].includes(direction)) return "right";
  return "";
}

function normalizeRiskLevel(value, joinedText) {
  const raw = String(value || "").toLowerCase().trim().replace(/-/g, "_");
  const inferredHigh = HIGH_RISK_AGENT_WORDS.some((word) => joinedText.includes(word.toLowerCase()) || joinedText.includes(word));
  if (inferredHigh) return "high";
  if (["high", "medium", "low"].includes(raw)) return raw;
  return "low";
}

function compactAgentNode(node, index) {
  if (!node || typeof node !== "object") return null;
  return {
    id: safeText(node.id || `n${index}`, 32),
    text: safeText(node.text || node.label || node.contentDescription || "", 80),
    className: safeText(node.className || node.class || "", 48),
    bounds: safeText(node.bounds || "", 48),
    clickable: Boolean(node.clickable),
    editable: Boolean(node.editable),
    scrollable: Boolean(node.scrollable),
  };
}

function compactScreenVisual(value) {
  const raw = value && typeof value === "object" ? value : {};
  const width = Number(raw.width) || 0;
  const height = Number(raw.height) || 0;
  const displayWidth = Number(raw.displayWidth || raw.originalWidth || raw.screenWidth || raw.rawWidth) || width;
  const displayHeight = Number(raw.displayHeight || raw.originalHeight || raw.screenHeight || raw.rawHeight) || height;

  return {
    available: Boolean(raw.available),
    mimeType: safeText(raw.mimeType || "image/jpeg", 40),
    width,
    height,
    displayWidth,
    displayHeight,
    source: safeText(raw.source || "", 80),
    reason: safeText(raw.reason || "", 160),
  };
}

function compactScreenSnapshot(snapshot) {
  const raw = snapshot && typeof snapshot === "object" ? snapshot : {};
  const texts = Array.isArray(raw.texts) ? raw.texts.map((item) => safeText(item, 80)).filter(Boolean).slice(0, 30) : [];
  const clickableNodes = (Array.isArray(raw.clickableNodes) ? raw.clickableNodes : [])
    .map(compactAgentNode)
    .filter(Boolean)
    .slice(0, 30);
  const inputNodes = (Array.isArray(raw.inputNodes) ? raw.inputNodes : [])
    .map(compactAgentNode)
    .filter(Boolean)
    .slice(0, 10);
  const scrollableNodes = (Array.isArray(raw.scrollableNodes) ? raw.scrollableNodes : [])
    .map(compactAgentNode)
    .filter(Boolean)
    .slice(0, 10);
  const visual = compactScreenVisual(raw.visual || {});
  const confidence = raw.confidence && typeof raw.confidence === "object"
    ? {
        hasUsefulNodes: Boolean(raw.confidence.hasUsefulNodes),
        needsVisualFallback: Boolean(raw.confidence.needsVisualFallback),
        hasVisualImage: Boolean(raw.confidence.hasVisualImage || visual.available),
      }
    : {
        hasUsefulNodes: clickableNodes.length > 0 || inputNodes.length > 0 || scrollableNodes.length > 0,
        needsVisualFallback: (Number(raw.nodeCount) || 0) <= 8 || texts.length === 0 || clickableNodes.length === 0,
        hasVisualImage: Boolean(visual.available),
      };

  return {
    currentApp: safeText(raw.currentApp || raw.packageName || "", 100),
    packageName: safeText(raw.packageName || raw.currentApp || "", 100),
    nodeCount: Number(raw.nodeCount) || clickableNodes.length + inputNodes.length + scrollableNodes.length + texts.length,
    texts,
    clickableNodes,
    inputNodes,
    scrollableNodes,
    confidence,
    visual,
  };
}

function normalizeAgentScreenshot(body) {
  const top = body?.screenshot && typeof body.screenshot === "object" ? body.screenshot : {};
  const visual = body?.screenSnapshot?.visual && typeof body.screenSnapshot.visual === "object" ? body.screenSnapshot.visual : {};
  const base64Raw =
    top.base64 ||
    top.base64Data ||
    top.imageBase64 ||
    top.data ||
    visual.base64Jpeg ||
    visual.base64 ||
    visual.base64Data ||
    "";

  const base64 = String(base64Raw || "").replace(/^data:image\/[a-zA-Z0-9.+-]+;base64,/, "").trim();
  if (!base64) {
    return {
      hasImage: false,
      mimeType: "image/jpeg",
      base64: "",
      width: 0,
      height: 0,
      displayWidth: 0,
      displayHeight: 0,
    };
  }

  const width = Number(top.width || visual.width) || 0;
  const height = Number(top.height || visual.height) || 0;
  const displayWidth = Number(top.displayWidth || top.originalWidth || top.screenWidth || visual.displayWidth || visual.originalWidth || visual.screenWidth) || width;
  const displayHeight = Number(top.displayHeight || top.originalHeight || top.screenHeight || visual.displayHeight || visual.originalHeight || visual.screenHeight) || height;
  const mimeType = String(top.mimeType || visual.mimeType || "image/jpeg").trim() || "image/jpeg";

  return {
    hasImage: true,
    mimeType: mimeType.startsWith("image/") ? mimeType : "image/jpeg",
    base64,
    width,
    height,
    displayWidth,
    displayHeight,
    source: safeText(top.source || visual.source || "", 80),
    reason: safeText(top.reason || visual.reason || "", 160),
  };
}

function clamp01(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return undefined;
  return Math.max(0, Math.min(1, numeric));
}

function normalizeAgentCoordinate(value, imageSize, displaySize) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return undefined;
  if (numeric >= 0 && numeric <= 1) return clamp01(numeric);
  if (imageSize > 1 && numeric >= 0 && numeric <= imageSize + 24) return clamp01(numeric / imageSize);
  if (displaySize > 1 && numeric >= 0 && numeric <= displaySize + 24) return clamp01(numeric / displaySize);
  return undefined;
}

function normalizeAgentTapCoordinates(rawX, rawY, screenshotInfo = null) {
  const imageWidth = Number(screenshotInfo?.width) || 0;
  const imageHeight = Number(screenshotInfo?.height) || 0;
  const displayWidth = Number(screenshotInfo?.displayWidth) || imageWidth;
  const displayHeight = Number(screenshotInfo?.displayHeight) || imageHeight;
  const x = normalizeAgentCoordinate(rawX, imageWidth, displayWidth);
  const y = normalizeAgentCoordinate(rawY, imageHeight, displayHeight);
  if (!Number.isFinite(x) || !Number.isFinite(y)) return { x: undefined, y: undefined };
  return { x, y };
}


function allSnapshotNodes(snapshot) {
  return [
    ...(snapshot.clickableNodes || []),
    ...(snapshot.inputNodes || []),
    ...(snapshot.scrollableNodes || []),
  ];
}

function findAgentNode(snapshot, nodeId) {
  const id = safeText(nodeId, 32);
  if (!id) return null;
  return allSnapshotNodes(snapshot).find((node) => node.id === id) || null;
}

function isAgentModeRequest(body) {
  return Boolean(
    body?.agentMode === true ||
      body?.intent === "agent_step" ||
      body?.responseFormat?.includeAgentStep ||
      body?.screenSnapshot
  );
}

function isAgentOutcomeVerificationRequest(body) {
  const intent = String(body?.intent || body?.action || body?.type || "").toLowerCase().trim().replace(/-/g, "_");
  return Boolean(
    intent === "agent_outcome_verification" ||
      intent === "agent_verification" ||
      intent === "agent_verify" ||
      body?.responseFormat?.includeAgentOutcomeVerification ||
      body?.responseFormat?.includeAgentVerification
  );
}

function normalizeForMatch(value) {
  return String(value || "").toLowerCase().replace(/[\s\u3000，。,.、:：/\\\-]+/g, "");
}

function escapeRegExp(value) {
  return String(value || "").replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function snapshotTextForOutcome(snapshot) {
  const nodes = [
    ...(Array.isArray(snapshot?.texts) ? snapshot.texts : []),
    ...(Array.isArray(snapshot?.clickableNodes) ? snapshot.clickableNodes.map((n) => n?.text || "") : []),
    ...(Array.isArray(snapshot?.inputNodes) ? snapshot.inputNodes.map((n) => n?.text || "") : []),
    ...(Array.isArray(snapshot?.scrollableNodes) ? snapshot.scrollableNodes.map((n) => n?.text || "") : []),
  ];
  return normalizeForMatch(nodes.join(" "));
}

function quickVerifyAgentOutcome(goal, action, snapshot) {
  const goalText = normalizeForMatch(goal);
  const actionText = normalizeForMatch([action?.type, action?.targetText, action?.reason, action?.text].join(" "));
  const visibleText = snapshotTextForOutcome(snapshot);
  if (!goalText || !visibleText) return null;

  // 本地快速验证只保留安全兜底，不能用关键词判断“已完成”或“有效进展”。
  // 例如“打开 QQ 联系人界面”时，底部 Tab 上出现“联系人”只是入口可见，
  // 不是页面主体已经进入联系人界面。是否完成必须交给云端视觉模型判断。
  const highRiskVisible = HIGH_RISK_AGENT_WORDS.some((word) => visibleText.includes(normalizeForMatch(word)));
  const highRiskRequested = HIGH_RISK_AGENT_WORDS.some((word) => goalText.includes(normalizeForMatch(word)) || actionText.includes(normalizeForMatch(word)));
  if (highRiskVisible && !highRiskRequested) {
    return {
      isExpected: false,
      expectedProgress: false,
      isWrong: true,
      confidence: 0.72,
      reason: "当前页面出现支付、发送、授权、删除等高风险状态，且用户目标没有明确要求，应视为走错或需要退出。",
      nextHint: "返回上一层后重新观察。",
      result: "wrong",
      source: "generic_local_safety_rule",
    };
  }

  return null;
}

function normalizeAgentOutcome(value) {
  const raw = value && typeof value === "object" ? value : {};
  const nested = raw.agentOutcome || raw.outcome || raw.verification || raw.result || raw;
  const expectedRaw = nested.isExpected ?? nested.expected ?? nested.success ?? nested.reachedTarget ?? nested.onExpectedPage;
  const wrongRaw = nested.isWrong ?? nested.wrong ?? nested.failed ?? nested.wrongPage ?? nested.offTarget;
  const confidenceRaw = Number(nested.confidence ?? nested.score ?? 0);
  const isExpected = Boolean(expectedRaw === true || expectedRaw === "true" || expectedRaw === "expected");
  const isWrong = Boolean(wrongRaw === true || wrongRaw === "true" || wrongRaw === "wrong");
  const confidence = Number.isFinite(confidenceRaw) ? Math.max(0, Math.min(1, confidenceRaw)) : (isExpected || isWrong ? 0.75 : 0.35);
  return {
    isExpected,
    isWrong: isExpected ? false : isWrong,
    confidence,
    reason: safeText(nested.reason || nested.explanation || nested.rationale || (isExpected ? "页面已符合预期。" : isWrong ? "页面与预期不符。" : "无法确认页面结果。"), 220),
  };
}

function normalizeAgentState(value, agentStep = null) {
  const raw = value && typeof value === "object" ? value : {};
  const nested = raw.agentState || raw.state || raw.statusState || raw.verification || raw.outcomeVerification || {};

  const resultRaw = String(nested.result ?? nested.status ?? nested.outcome ?? "").toLowerCase().trim();
  const isCompleteRaw = nested.isComplete ?? nested.complete ?? nested.completed ?? nested.isExpected ?? nested.expected ?? nested.success;
  const progressRaw = nested.expectedProgress ?? nested.progress ?? nested.isProgress ?? nested.closerToGoal ?? nested.onCorrectPath;
  const wrongRaw = nested.isWrong ?? nested.wrong ?? nested.failed ?? nested.wrongPage ?? nested.offTarget;

  const isComplete = Boolean(
    isCompleteRaw === true ||
      isCompleteRaw === "true" ||
      isCompleteRaw === "complete" ||
      isCompleteRaw === "completed" ||
      isCompleteRaw === "expected" ||
      resultRaw === "complete" ||
      resultRaw === "completed" ||
      resultRaw === "expected" ||
      resultRaw === "success" ||
      agentStep?.type === "finish"
  );

  const expectedProgress = Boolean(
    isComplete ||
      progressRaw === true ||
      progressRaw === "true" ||
      progressRaw === "progress" ||
      progressRaw === "expected_progress" ||
      resultRaw === "progress" ||
      resultRaw === "expected_progress"
  );

  const isWrongRaw = Boolean(
    wrongRaw === true ||
      wrongRaw === "true" ||
      wrongRaw === "wrong" ||
      resultRaw === "wrong" ||
      resultRaw === "failed" ||
      resultRaw === "failure" ||
      resultRaw === "incorrect" ||
      resultRaw === "mismatch"
  );

  const isWrong = isComplete || expectedProgress ? false : isWrongRaw;
  const confidenceRaw = Number(nested.confidence ?? nested.score ?? 0);
  const confidence = Number.isFinite(confidenceRaw)
    ? Math.max(0, Math.min(1, confidenceRaw))
    : (isComplete || isWrong || expectedProgress ? 0.72 : 0.35);

  return {
    isComplete,
    expectedProgress,
    isWrong,
    confidence,
    reason: safeText(
      nested.reason ||
        nested.explanation ||
        nested.rationale ||
        agentStep?.reason ||
        (isComplete ? "云端判断目标已经完成。" : expectedProgress ? "云端判断当前页面仍在正确路径上。" : isWrong ? "云端判断当前页面明显偏离目标。" : "云端无法确认当前状态。"),
      220
    ),
    nextHint: safeText(nested.nextHint || nested.next_hint || nested.hint || "", 160),
    result: isComplete ? "complete" : expectedProgress ? "progress" : isWrong ? "wrong" : "uncertain",
  };
}

function buildAgentVerifierSystemPrompt() {
  return [
    "你是 Android 手机 Computer Use 智能体的通用结果验证器，只能输出严格 JSON。",
    "你需要根据用户原始目标、刚执行的动作、当前 screenSnapshot 和可选 screenshot，判断动作后的页面状态。",
    "截图是主输入；无障碍节点可能缺失、滞后或只暴露入口文字。不要因为节点少就直接判失败或不确定。",
    "你不是下一步规划器，不要返回点击坐标，也不要执行任务。",
    "",
    "你只判断四件事：",
    "1. 当前页面是否已经完成用户目标。",
    "2. 当前页面是否比动作前更接近目标，或者已经进入正确的中间页面。",
    "3. 当前页面是否明显走错，例如进入无关页面、高风险提交/支付/发送/授权页面、错误 App、广告弹窗等。",
    "4. 是否应该继续下一轮观察规划，而不是返回。",
    "",
    "判定规则：",
    "- isExpected=true：已经到达目标页面，目标 Tab 已选中，页面主体内容已切换，或目标内容明确展开。",
    "- expectedProgress=true：尚未完成最终目标，但页面进入了正确路径或有效中间页，应继续下一步，不应自动返回。",
    "- 入口可见不等于目标完成。仅看到目标入口、底部 Tab 文字、菜单项或按钮时，通常应 expectedProgress=true，而不是 isExpected=true。",
    "- 例如目标是进入联系人界面，仅看到底部“联系人”Tab 但主体仍是消息列表，不算完成；应继续点击联系人。",
    "- 例如目标是进入朋友圈，仅看到发现页里的“朋友圈”入口，不算完成；应继续点击朋友圈。",
    "- isWrong=true：只有在明显偏离目标或进入无关/高风险页面时才设为 true。",
    "- uncertain：证据不足时不要设 isWrong=true；保持三者 false，并说明需要继续观察。",
    "- 如果 isExpected=true 或 expectedProgress=true，则 isWrong 必须为 false。",
    "- 不要依赖某个 App 或固定脚本；根据用户目标、动作意图、截图和节点综合判断。",
    "",
    "输出格式只能是：",
    "{\"outcomeVerification\":{\"result\":\"expected|progress|wrong|uncertain\",\"isExpected\":false,\"expectedProgress\":false,\"isWrong\":false,\"confidence\":0.0,\"reason\":\"简短原因\",\"nextHint\":\"下一轮规划提示，可为空\"}}",
  ].join("\n");
}

function buildAgentVerifierMessages(goal, action, snapshot, screenshotInfo = null) {
  const textPayload = {
    agentGoal: goal,
    lastAction: action || null,
    screenSnapshot: snapshot,
    screenshot: screenshotInfo?.hasImage
      ? {
          mimeType: screenshotInfo.mimeType,
          width: screenshotInfo.width,
          height: screenshotInfo.height,
          displayWidth: screenshotInfo.displayWidth,
          displayHeight: screenshotInfo.displayHeight,
          source: screenshotInfo.source || "",
          reason: screenshotInfo.reason || "",
        }
      : null,
    task: "判断 lastAction 执行后是否到达用户目标或目标路径。只返回 agentOutcome JSON。",
  };
  const content = [{ type: "text", text: JSON.stringify(textPayload) }];
  if (screenshotInfo?.hasImage) {
    content.push({
      type: "image_url",
      image_url: { url: `data:${screenshotInfo.mimeType};base64,${screenshotInfo.base64}` },
    });
  }
  return [
    { role: "system", content: buildAgentVerifierSystemPrompt() },
    { role: "user", content },
  ];
}

function supportedAgentStepsFromBody(body) {
  const raw = Array.isArray(body?.supportedAgentSteps) ? body.supportedAgentSteps : SUPPORTED_AGENT_STEP_TYPES;
  const clean = raw
    .map(normalizeAgentStepType)
    .filter((item, index, arr) => SUPPORTED_AGENT_STEP_TYPES.includes(item) && arr.indexOf(item) === index);
  return clean.length ? clean : SUPPORTED_AGENT_STEP_TYPES;
}

function normalizeAgentStep(value, snapshot, supportedSteps, goal, screenshotInfo = null, deviceContext = null) {
  const raw = value && typeof value === "object" ? value : {};
  const nested = raw.agentStep || raw.step || raw.actionStep || (Array.isArray(raw.agentSteps) ? raw.agentSteps[0] : null) || (Array.isArray(raw.actionBatch) ? raw.actionBatch[0] : null) || raw.result || raw;
  const safeSupported = Array.isArray(supportedSteps) && supportedSteps.length ? supportedSteps : SUPPORTED_AGENT_STEP_TYPES;
  const type = normalizeAgentStepType(nested.type || nested.action);
  let finalType = safeSupported.includes(type) ? type : "need_user_help";

  const targetNodeId = safeText(nested.targetNodeId || nested.nodeId || nested.targetId || "", 32);
  const targetText = safeText(nested.targetText || nested.label || nested.title || nested.target || "", 80);
  const inputText = safeText(nested.text || nested.inputText || nested.value || "", 180);
  const direction = normalizeAgentDirection(nested.direction);
  const reason = safeText(nested.reason || nested.rationale || nested.explanation || "根据当前屏幕和用户目标规划下一步。", 220);
  let appName = safeText(nested.appName || nested.app || nested.application || "", 40);
  let packageName = safeText(nested.packageName || nested.package || "", 100);
  const rawX = Number(nested.x ?? nested.centerX ?? nested.tapX);
  const rawY = Number(nested.y ?? nested.centerY ?? nested.tapY);
  const durationMsRaw = Number(nested.durationMs ?? nested.waitMs ?? nested.delayMs);
  const durationMs = Number.isFinite(durationMsRaw) ? Math.max(120, Math.min(2000, Math.round(durationMsRaw))) : undefined;

  let finalReason = reason;
  let finalTargetNodeId = targetNodeId;
  let finalDirection = direction;
  let finalX = rawX;
  let finalY = rawY;

  if (finalType === "tap_node") {
    const hasNode = Boolean(targetNodeId && findAgentNode(snapshot, targetNodeId));
    if (!hasNode && !targetText) {
      finalType = "need_user_help";
      finalReason = "当前屏幕快照中没有可靠的可点击目标，不能伪造点击。";
    }
  }

  if (finalType === "tap_xy") {
    const normalizedTap = normalizeAgentTapCoordinates(rawX, rawY, screenshotInfo);
    if (!Number.isFinite(normalizedTap.x) || !Number.isFinite(normalizedTap.y)) {
      finalType = "need_user_help";
      finalReason = "tap_xy 缺少可靠归一化坐标，不能执行坐标点击。";
    } else {
      finalX = normalizedTap.x;
      finalY = normalizedTap.y;
      finalReason = `${finalReason} 坐标已统一为归一化屏幕坐标。`;
    }
  }

  if (finalType === "input_text") {
    const hasInput = snapshot.inputNodes && snapshot.inputNodes.length > 0;
    if (!hasInput) {
      finalType = "need_user_help";
      finalReason = "当前屏幕没有可识别输入框，不能输入文字。";
    }
  }

  if (finalType === "scroll") {
    const hasScrollable = snapshot.scrollableNodes && snapshot.scrollableNodes.length > 0;
    if (!hasScrollable) {
      if (safeSupported.includes("swipe")) {
        finalType = "swipe";
        finalTargetNodeId = "";
        finalDirection = direction || "up";
        finalReason = `${finalReason} 当前快照未标记可滚动区域，改用普通滑动手势兜底。`;
      } else {
        finalDirection = direction || "down";
        finalReason = `${finalReason} 当前快照未标记可滚动区域，交给 Android 端用手势兜底。`;
      }
    }
  }

  if (finalType === "swipe") {
    finalDirection = direction || "up";
  }

  if (finalType === "open_app") {
    const matchedApp = findInstalledAppForOpenApp(appName, packageName, deviceContext);
    const hasDeviceApps = installedAppsFromDeviceContext(deviceContext).length > 0;
    if (matchedApp) {
      appName = matchedApp.label;
      packageName = matchedApp.packageName;
      finalReason = `${finalReason} 已根据 deviceContext.installedApps 校准为真实可启动应用：${matchedApp.label}(${matchedApp.packageName})。`;
    } else if (hasDeviceApps) {
      finalType = "need_user_help";
      finalReason = `open_app 目标不在 deviceContext.installedApps 中，不能猜包名或点击桌面文件夹。原始 appName=${appName || "空"} packageName=${packageName || "空"}。`;
      appName = "";
      packageName = "";
    } else if (!appName && !packageName) {
      finalType = "need_user_help";
      finalReason = "open_app 缺少 appName 或 packageName，且未提供 deviceContext.installedApps，不能打开应用。";
    }
  }

  if (finalType === "wait" && !durationMs) {
    finalReason = finalReason || "等待页面加载后重新观察。";
  }

  const joined = [goal, targetText, inputText, appName, packageName, finalReason].join(" ").toLowerCase();
  const riskLevel = normalizeRiskLevel(nested.riskLevel, joined);
  const requiresConfirmation = Boolean(nested.requiresConfirmation) || riskLevel !== "low";

  return {
    type: finalType,
    appName: finalType === "open_app" ? appName || undefined : appName || undefined,
    packageName: finalType === "open_app" ? packageName || undefined : packageName || undefined,
    targetNodeId: finalTargetNodeId || undefined,
    targetText: targetText || undefined,
    text: finalType === "input_text" ? inputText : inputText || undefined,
    direction: ["scroll", "swipe"].includes(finalType) ? finalDirection || "down" : finalDirection || undefined,
    x: finalType === "tap_xy" && Number.isFinite(finalX) ? finalX : undefined,
    y: finalType === "tap_xy" && Number.isFinite(finalY) ? finalY : undefined,
    durationMs: finalType === "wait" ? durationMs || 700 : durationMs,
    reason: finalReason,
    riskLevel,
    requiresConfirmation,
  };
}



function agentBatchCandidatesFromParsed(value) {
  const raw = value && typeof value === "object" ? value : {};
  const containers = [raw, raw.plan, raw.data, raw.result, raw.agentPlan].filter((item) => item && typeof item === "object");
  const keys = ["agentSteps", "steps", "actionBatch", "actions"];
  const candidates = [];
  for (const container of containers) {
    for (const key of keys) {
      if (Array.isArray(container[key])) candidates.push(...container[key]);
    }
  }
  return candidates;
}

function agentStepBatchKey(step) {
  return [
    step?.type || "",
    step?.targetNodeId || "",
    step?.targetText || "",
    step?.text || "",
    Number.isFinite(step?.x) ? Number(step.x).toFixed(3) : "",
    Number.isFinite(step?.y) ? Number(step.y).toFixed(3) : "",
  ].join("|");
}

function normalizeAgentStepBatch(parsed, fallbackStep, snapshot, supportedSteps, goal, screenshotInfo = null, deviceContext = null) {
  const out = [];
  const seen = new Set();
  const candidates = agentBatchCandidatesFromParsed(parsed);
  if (!candidates.length && fallbackStep) candidates.push(fallbackStep);

  for (const candidate of candidates) {
    const step = normalizeAgentStep(candidate, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    if (!step || ["finish", "need_user_help"].includes(step.type)) continue;
    if (step.riskLevel !== "low" || step.requiresConfirmation) continue;
    const key = agentStepBatchKey(step);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(step);
    if (out.length >= AGENT_ACTION_BATCH_MAX) break;
  }
  return out;
}

function normalizeAgentStopConditions(value) {
  const raw = value && typeof value === "object" ? value : {};
  const containers = [raw, raw.plan, raw.data, raw.result, raw.agentPlan].filter((item) => item && typeof item === "object");
  const keys = ["stopConditions", "batchStopConditions", "replanOn"];
  const out = new Set();
  for (const container of containers) {
    for (const key of keys) {
      const item = container[key];
      const list = Array.isArray(item) ? item : typeof item === "string" ? item.split(/[,;|]/g) : [];
      for (const entry of list) {
        const normalized = String(entry || "").trim().toLowerCase().replace(/-/g, "_");
        if (normalized) out.add(normalized);
      }
    }
  }
  if (!out.size) {
    out.add("visual_after_input");
    out.add("visual_after_system_action");
  }
  return Array.from(out).slice(0, 8);
}

function normalizeAppMatchText(value) {
  return String(value || "")
    .toLowerCase()
    .normalize("NFKC")
    .replace(/[\s　·・.。_\-]+/g, "")
    .replace(/app$/i, "")
    .replace(/应用$/u, "");
}

function installedAppsFromDeviceContext(deviceContext) {
  const ctx = deviceContext && typeof deviceContext === "object" ? deviceContext : {};
  const raw = Array.isArray(ctx.installedApps) ? ctx.installedApps : [];
  return raw
    .map((item) => {
      if (!item || typeof item !== "object") return null;
      const label = safeText(item.label || item.name || item.appName || "", 80);
      const packageName = safeText(item.packageName || item.package || "", 120);
      if (!label || !packageName) return null;
      const aliases = Array.isArray(item.aliases)
        ? item.aliases.map((alias) => safeText(alias, 80)).filter(Boolean).slice(0, 8)
        : [];
      return { label, packageName, launchable: item.launchable !== false, aliases };
    })
    .filter(Boolean)
    .filter((item) => item.launchable)
    .slice(0, 160);
}

function appCandidatesForPrompt(deviceContext, max = 120) {
  const apps = installedAppsFromDeviceContext(deviceContext);
  return apps.slice(0, max).map((app) => `${app.label}(${app.packageName})`).join(" / ");
}

function findInstalledAppForOpenApp(appName, packageName, deviceContext) {
  const apps = installedAppsFromDeviceContext(deviceContext);
  if (!apps.length) return null;
  const pkg = safeText(packageName || "", 120);
  if (pkg) {
    const exactPkg = apps.find((app) => app.packageName === pkg);
    if (exactPkg) return { ...exactPkg, match: "packageName" };
  }
  const q = normalizeAppMatchText(appName);
  if (!q) return null;

  let best = null;
  let bestScore = 0;
  for (const app of apps) {
    const names = [app.label, ...(app.aliases || [])].map(normalizeAppMatchText).filter(Boolean);
    for (const name of names) {
      let score = 0;
      if (name === q) score = 1200 + name.length;
      else if (name.startsWith(q) || q.startsWith(name)) score = 1000 + Math.min(name.length, q.length);
      else if (name.includes(q) || q.includes(name)) score = 850 + Math.min(name.length, q.length);
      if (score > bestScore) {
        bestScore = score;
        best = app;
      }
    }
  }
  return bestScore >= 850 && best ? { ...best, match: "appName" } : null;
}

function deviceContextSummaryForPrompt(deviceContext) {
  const ctx = deviceContext && typeof deviceContext === "object" ? deviceContext : {};
  const device = ctx.device || {};
  const screen = ctx.screen || {};
  const currentApp = ctx.currentApp || {};
  const apps = installedAppsFromDeviceContext(ctx);
  return {
    schema: ctx.schema || "android_device_context",
    device: {
      brand: device.brand || device.manufacturer || "",
      model: device.model || "",
      androidSdk: device.androidSdk,
      androidRelease: device.androidRelease || "",
      locale: device.locale || "",
    },
    screen: {
      widthPx: screen.widthPx,
      heightPx: screen.heightPx,
      density: screen.density,
      coordinateProtocol: screen.coordinateProtocol || "normalized_screen_0_1",
    },
    currentApp: {
      packageName: currentApp.packageName || "",
      isLauncherOrSystemSurface: Boolean(currentApp.isLauncherOrSystemSurface),
    },
    installedAppCount: apps.length,
    installedApps: apps.slice(0, 120),
    toolRules: Array.isArray(ctx.toolRules) ? ctx.toolRules.slice(0, 8) : [],
  };
}

function compactAgentMemoryForPrompt(agentMemory, recentActions) {
  const memory = agentMemory && typeof agentMemory === "object" ? agentMemory : {};
  return {
    schema: memory.schema || "agent_loop_memory",
    recentActions: Array.isArray(memory.recentActions) ? memory.recentActions.slice(-8) : Array.isArray(recentActions) ? recentActions.slice(-8) : [],
    failedActions: Array.isArray(memory.failedActions) ? memory.failedActions.slice(-8) : [],
    blockedActions: Array.isArray(memory.blockedActions) ? memory.blockedActions.slice(-8) : [],
    loopSignals: memory.loopSignals && typeof memory.loopSignals === "object" ? memory.loopSignals : {},
    policyHints: Array.isArray(memory.policyHints) ? memory.policyHints.slice(0, 8) : [],
  };
}



function routeEvidenceText(snapshot, visualFrame = null) {
  return evidenceTextForCompletion(snapshot, visualFrame);
}

function routeTextContainsAny(text, words) {
  const normalized = normalizeForMatch(text);
  return words.some((word) => normalized.includes(normalizeForMatch(word)));
}

function normalizeAgentRoutePlan(value, fallbackGoal = "") {
  const raw = value && typeof value === "object" ? value : {};
  const nested = raw.routePlan || raw.plan || raw.result || raw;
  const preferredAction = normalizeAgentStepType(nested.preferredAction || nested.action || nested.nextAction || "tap_xy");
  const subgoal = safeText(nested.subgoal || nested.groundingGoal || nested.nextTarget || fallbackGoal, 220);
  const expectedEvidence = Array.isArray(nested.expectedEvidence) ? nested.expectedEvidence.map((item) => safeText(item, 40)).filter(Boolean).slice(0, 8) : [];
  const avoidEvidence = Array.isArray(nested.avoidEvidence) ? nested.avoidEvidence.map((item) => safeText(item, 40)).filter(Boolean).slice(0, 8) : [];
  const confidenceRaw = Number(nested.confidence ?? nested.score ?? 0);
  const confidence = Number.isFinite(confidenceRaw) ? Math.max(0, Math.min(1, confidenceRaw)) : 0;
  const allowScrollRaw = nested.allowScroll;
  const allowScroll = allowScrollRaw === undefined || allowScrollRaw === null
    ? !["back", "finish", "need_user_help"].includes(preferredAction)
    : Boolean(allowScrollRaw);
  const directAction = ["back", "home", "wait", "finish", "need_user_help"].includes(preferredAction) ? preferredAction : "";
  return {
    stage: safeText(nested.stage || nested.phase || "route", 40),
    currentAssessment: safeText(nested.currentAssessment || nested.assessment || nested.pageAssessment || "", 180),
    subgoal: subgoal || safeText(fallbackGoal, 220),
    groundingGoal: safeText(nested.groundingGoal || subgoal || fallbackGoal, 240),
    preferredAction,
    directAction,
    allowScroll,
    expectedEvidence,
    avoidEvidence,
    routeState: safeText(nested.routeState || nested.status || "", 40),
    reason: safeText(nested.reason || nested.rationale || nested.currentAssessment || "路线规划器生成当前子目标。", 220),
    confidence,
    source: safeText(nested.source || "model", 40),
  };
}

function buildLocalRoutePlan(goal, snapshot, visualFrame = null, recentActions = [], deviceContext = null) {
  const normalizedGoal = normalizeForMatch(goal);
  const evidence = routeEvidenceText(snapshot, visualFrame);
  const recent = normalizeForMatch(Array.isArray(recentActions) ? recentActions.join(" ") : "");
  const wantsSettings = ["设置", "settings", "setting", "选项", "账号管理", "隐私", "通用"].some((word) => normalizedGoal.includes(normalizeForMatch(word)));
  const wantsContacts = ["联系人", "通讯录", "contacts"].some((word) => normalizedGoal.includes(normalizeForMatch(word)));
  const wantsBack = ["返回", "上一页", "back"].some((word) => normalizedGoal.includes(normalizeForMatch(word)));

  if (wantsBack) {
    return normalizeAgentRoutePlan({
      preferredAction: "back",
      subgoal: "返回上一页",
      groundingGoal: "返回上一页",
      allowScroll: false,
      expectedEvidence: ["上一页"],
      reason: "用户目标本身是返回，直接使用系统 back，避免坐标误点。",
      confidence: 0.86,
      source: "local_rule",
    }, goal);
  }

  if (wantsSettings) {
    const looksLikeChannelProfile = routeTextContainsAny(evidence, ["个人中心", "我的频道", "我关注的作者", "创建频道", "我发表的帖子", "频道钱包", "签约中心", "频道"]);
    const settingVisible = routeTextContainsAny(evidence, ["设置", "账号管理", "隐私", "通用", "辅助功能"]);
    const repeatedSwipe = (recent.match(/swipe|scroll|滑动|滚动/g) || []).length >= 1;
    if (looksLikeChannelProfile && !settingVisible) {
      return normalizeAgentRoutePlan({
        preferredAction: "back",
        subgoal: "返回上一页；当前个人中心频道页不是设置路径，不要继续在此页滑动找设置",
        groundingGoal: "点击左上角返回箭头或执行返回，离开当前个人中心频道页",
        allowScroll: false,
        expectedEvidence: ["QQ 主页面", "侧边菜单", "设置入口"],
        avoidEvidence: ["我的频道", "创建频道", "帖子", "频道钱包", "签约中心"],
        reason: "当前页面是个人中心/频道资料页，未出现设置入口，继续滑动属于盲找，应返回换路径。",
        confidence: repeatedSwipe ? 0.92 : 0.86,
        source: "local_rule",
      }, goal);
    }
    if (settingVisible) {
      return normalizeAgentRoutePlan({
        preferredAction: "tap_xy",
        subgoal: "点击屏幕中可见的“设置”入口",
        groundingGoal: "点击可见的“设置”入口",
        allowScroll: false,
        expectedEvidence: ["设置页", "账号管理", "隐私", "通用"],
        reason: "设置入口已经可见，交给 GUI Plus 精确定位点击。",
        confidence: 0.82,
        source: "local_rule",
      }, goal);
    }
    if (repeatedSwipe) {
      return normalizeAgentRoutePlan({
        preferredAction: "back",
        subgoal: "停止在当前页继续滑动，返回上一层重新寻找设置入口",
        groundingGoal: "点击左上角返回箭头或执行返回",
        allowScroll: false,
        expectedEvidence: ["QQ 主页面", "菜单", "设置"],
        reason: "近期已经滑动但没有找到设置，路线价值低，应返回重选入口。",
        confidence: 0.78,
        source: "local_rule",
      }, goal);
    }
    return normalizeAgentRoutePlan({
      preferredAction: "tap_xy",
      subgoal: "寻找并点击明确的设置入口；如果当前页没有设置入口，不要盲滑，优先返回上一层",
      groundingGoal: "点击当前屏幕可见的设置入口；若不可见则返回上一层",
      allowScroll: false,
      expectedEvidence: ["设置", "账号管理", "隐私", "通用"],
      avoidEvidence: ["频道页", "帖子列表", "钱包", "签约中心"],
      reason: "目标是设置，需要先找到明确设置入口，禁止在无关个人中心页盲目滚动。",
      confidence: 0.58,
      source: "local_rule",
    }, goal);
  }

  if (wantsContacts) {
    return normalizeAgentRoutePlan({
      preferredAction: "tap_xy",
      subgoal: "点击当前屏幕中可见的“联系人”入口或底部联系人标签",
      groundingGoal: "点击“联系人”入口",
      allowScroll: false,
      expectedEvidence: ["联系人页", "好友", "群聊"],
      reason: "联系人属于低风险导航入口，交给 GUI Plus 定位可见标签。",
      confidence: 0.72,
      source: "local_rule",
    }, goal);
  }

  return normalizeAgentRoutePlan({
    preferredAction: "tap_xy",
    subgoal: safeText(goal, 220),
    groundingGoal: safeText(goal, 240),
    allowScroll: true,
    expectedEvidence: [],
    avoidEvidence: [],
    reason: "没有命中特定路线规则，使用用户目标作为当前子目标。",
    confidence: 0.42,
    source: "local_fallback",
  }, goal);
}

function buildAgentRoutePlannerSystemPrompt() {
  return [
    "你是 Android GUI Agent 的路线规划器，不负责点击坐标，只负责把用户大目标拆成当前屏幕的一步子目标。",
    "你必须只输出严格 JSON，不能输出 Markdown、自然语言解释或代码块。",
    "GUI Plus 后续只负责定位坐标，所以你输出的 groundingGoal 必须是明确可定位的当前一步目标，例如“点击左上角返回箭头”“点击底部联系人 Tab”“点击可见的设置入口”。",
    "不要让 GUI Plus 自己猜整条路线；不要输出宽泛目标如“找到设置”。",
    "如果当前页面明显不是目标路线，例如进入了个人中心频道页、帖子页、钱包页、广告页，而目标是设置/联系人/搜索等，应 preferredAction=back，allowScroll=false。",
    "只有当前页面本身可滚动且目标可能就在同一列表中时，才 allowScroll=true。",
    "如果已经看到目标入口，preferredAction=tap_xy，groundingGoal 指向该入口。",
    "如果目标已完成，preferredAction=finish。",
    "输出格式：",
    "{\"routePlan\":{\"stage\":\"route\",\"currentAssessment\":\"当前页判断\",\"subgoal\":\"当前一步子目标\",\"groundingGoal\":\"给 GUI Plus 的具体定位目标\",\"preferredAction\":\"tap_xy|back|swipe|scroll|wait|finish|need_user_help\",\"allowScroll\":false,\"expectedEvidence\":[\"\"],\"avoidEvidence\":[\"\"],\"routeState\":\"on_route|off_route|done|uncertain\",\"reason\":\"简短原因\",\"confidence\":0.0}}"
  ].join("\\n");
}

function buildAgentRoutePlannerMessages(goal, snapshot, visualFrame, recentActions, deviceContext, agentMemory) {
  const payload = {
    goal,
    currentApp: snapshot.currentApp || snapshot.packageName || "",
    currentEvidence: {
      visualFrame: visualFrame ? {
        pageTitle: visualFrame.pageTitle || "",
        pageType: visualFrame.pageType || "",
        summary: visualFrame.summary || "",
        targetText: visualFrame.targetText || "",
        confidence: visualFrame.confidence || 0,
      } : null,
      texts: (snapshot.texts || []).slice(0, 24),
      clickableTexts: (snapshot.clickableNodes || []).map((node) => node.text).filter(Boolean).slice(0, 24),
      nodeCount: snapshot.nodeCount,
    },
    recentActions: Array.isArray(recentActions) ? recentActions.slice(-8) : [],
    memory: compactAgentMemoryForPrompt(agentMemory, recentActions),
    device: deviceContextSummaryForPrompt(deviceContext),
    task: "输出当前路线判断和给 GUI Plus 的具体 groundingGoal。不要输出坐标。",
  };
  return [
    { role: "system", content: buildAgentRoutePlannerSystemPrompt() },
    { role: "user", content: JSON.stringify(payload) },
  ];
}

async function callAgentRoutePlanner(goal, snapshot, visualFrame, recentActions, deviceContext, agentMemory, timeoutMs = AGENT_ROUTE_PLANNER_TIMEOUT_MS) {
  const local = buildLocalRoutePlan(goal, snapshot, visualFrame, recentActions, deviceContext);
  if (local.confidence >= 0.78 || local.directAction) return local;
  try {
    const raw = await callOpenAICompatible(
      process.env.QWEN_BASE_URL,
      process.env.QWEN_API_KEY,
      process.env.QWEN_MODEL,
      buildAgentRoutePlannerMessages(goal, snapshot, visualFrame, recentActions, deviceContext, agentMemory),
      "Qwen Agent Route Planner",
      {
        temperature: 0,
        max_tokens: AGENT_ROUTE_PLANNER_MAX_TOKENS,
        timeoutMs: Math.max(300, Number(timeoutMs || AGENT_ROUTE_PLANNER_TIMEOUT_MS)),
        response_format: { type: "json_object" },
      }
    );
    let parsed = {};
    try { parsed = JSON.parse(extractJsonText(raw)); } catch (_) { parsed = {}; }
    const modelPlan = normalizeAgentRoutePlan(parsed, goal);
    if (modelPlan.confidence >= 0.45 && modelPlan.groundingGoal) return { ...modelPlan, source: "model" };
  } catch (error) {
    return { ...local, reason: `${local.reason} 路线模型暂不可用：${sanitizeProviderError(error, 80)}`.slice(0, 220), source: `${local.source}_model_error` };
  }
  return local;
}

function routePlanToDirectAgentPlan(routePlan, snapshot, supportedSteps, goal, screenshotInfo, deviceContext) {
  if (!routePlan || !routePlan.directAction) return null;
  const action = routePlan.directAction;
  if (!["back", "home", "wait", "finish", "need_user_help"].includes(action)) return null;
  const step = normalizeAgentStep({
    agentStep: {
      type: action,
      durationMs: action === "wait" ? 700 : undefined,
      reason: routePlan.reason || routePlan.subgoal || "路线规划器给出直接动作。",
      riskLevel: "low",
      requiresConfirmation: false,
    },
  }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
  const state = normalizeAgentState({
    agentState: {
      isComplete: action === "finish",
      expectedProgress: ["back", "home", "wait", "finish"].includes(action),
      isWrong: action === "back" && routePlan.routeState === "off_route",
      confidence: Math.max(0.45, Number(routePlan.confidence || 0)),
      reason: routePlan.reason || routePlan.currentAssessment || "",
      nextHint: routePlan.subgoal || "",
    },
  }, step);
  return { agentStep: step, agentState: state, source: `route_planner_direct_${action}` };
}

function applyRoutePlanGuard(routePlan, agentStep, agentState, snapshot, supportedSteps, goal, screenshotInfo, deviceContext) {
  if (!routePlan || !agentStep) return { agentStep, agentState, guarded: false, reason: "" };
  if (routePlan.allowScroll === false && ["scroll", "swipe"].includes(agentStep.type)) {
    const fallbackAction = routePlan.preferredAction === "back" || routePlan.directAction === "back" ? "back" : "need_user_help";
    const reason = routePlan.reason || "路线规划禁止在当前页继续盲目滑动。";
    const guardedStep = normalizeAgentStep({
      agentStep: {
        type: fallbackAction,
        reason: `${reason} 已拦截 GUI Plus 的 ${agentStep.type}。`,
        riskLevel: "low",
        requiresConfirmation: false,
      },
    }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    const guardedState = normalizeAgentState({
      agentState: {
        isComplete: false,
        expectedProgress: fallbackAction === "back",
        isWrong: fallbackAction === "back",
        confidence: Math.max(0.55, Number(routePlan.confidence || 0)),
        reason: guardedStep.reason,
        nextHint: routePlan.subgoal || "",
      },
    }, guardedStep);
    return { agentStep: guardedStep, agentState: guardedState, guarded: true, reason: guardedStep.reason };
  }
  if (routePlan.preferredAction === "tap_xy" && ["wait", "need_user_help"].includes(agentStep.type) && routePlan.confidence >= 0.7) {
    const reason = routePlan.reason || "路线规划已经给出明确可点击子目标，但 GUI Plus 未返回点击。";
    const guardedStep = normalizeAgentStep({
      agentStep: {
        type: "need_user_help",
        reason: `${reason} 需要重新截图或人工确认，禁止盲目等待。`,
        riskLevel: "low",
        requiresConfirmation: false,
      },
    }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    const guardedState = normalizeAgentState({
      agentState: {
        isComplete: false,
        expectedProgress: false,
        isWrong: false,
        confidence: Math.max(0.4, Number(routePlan.confidence || 0)),
        reason: guardedStep.reason,
        nextHint: routePlan.subgoal || "",
      },
    }, guardedStep);
    return { agentStep: guardedStep, agentState: guardedState, guarded: true, reason: guardedStep.reason };
  }
  return { agentStep, agentState, guarded: false, reason: "" };
}



function buildAgentPlannerSystemPrompt(supportedSteps, hasScreenshot = false) {
  return [
    "你是 Android 手机 Computer Use 智能体的快速规划器，只能输出严格 JSON。",
    "你的任务不是聊天，而是根据用户目标、截图、节点提示、设备上下文和短期记忆，返回当前状态 agentState 与下一步 agentStep。",
    "",
    `允许动作：${supportedSteps.join(", ")}`,
    "",
    "输出必须是单个 JSON 对象，不能有 Markdown、解释文字或代码块：",
    "{\"agentState\":{\"isComplete\":false,\"expectedProgress\":false,\"isWrong\":false,\"confidence\":0.0,\"reason\":\"\",\"nextHint\":\"\"},\"agentStep\":{\"type\":\"open_app|tap_xy|tap_node|input_text|scroll|swipe|back|home|wait|finish|need_user_help\",\"appName\":\"\",\"packageName\":\"\",\"targetNodeId\":\"\",\"targetText\":\"\",\"text\":\"\",\"direction\":\"up|down|left|right\",\"x\":0,\"y\":0,\"durationMs\":700,\"reason\":\"\",\"riskLevel\":\"low|medium|high\",\"requiresConfirmation\":false}}",
    "",
    "核心规则：",
    "- 有 screenshot 时，截图是主观察源；screenSnapshot 只作为可点击、可输入、可滚动提示。",
    "- 没有 screenshot 时，只能依赖 open_app、节点、设备上下文或保守动作。",
    "- 当前不在目标 App 且目标 App 在 installedApps 中时，优先 open_app。",
    "- open_app 必须从 deviceContext.installedApps 选择真实 appName/packageName；不要猜包名，不要在桌面找图标。",
    "- 入口可见不等于完成。进入页面/Tab/栏目/列表类目标，必须看到目标页已选中且主体内容切换后才能 finish。",
    "- 如果只是更接近目标，设置 expectedProgress=true 并继续下一步，不要 finish。",
    "- 明显走错时 isWrong=true，通常下一步 back。",
    "- tap_xy 的 x/y 必须是 0 到 1 的归一化屏幕坐标；不要输出像素。",
    "- 图片中能看见目标但节点没有时，用 tap_xy；节点明确对应目标时可用 tap_node。",
    "- 页面加载、动画或空白时 wait；能探索时 scroll/swipe；不要因为节点少就直接失败。",
    "- 涉及支付、转账、下单、删除、发送、发布、授权、登录、验证码、密码时，riskLevel=high 且 requiresConfirmation=true。",
    hasScreenshot ? "- 当前请求含截图，应优先看图规划，返回一步最小动作。" : "- 当前请求无截图，不能猜复杂页面是否完成。",
  ].join("\n");
}


function targetAppCandidatesFromDeviceContext(deviceContext) {
  const ctx = deviceContext && typeof deviceContext === "object" ? deviceContext : {};
  const raw = Array.isArray(ctx.targetAppCandidates) ? ctx.targetAppCandidates : [];
  return raw
    .map((item) => {
      if (!item || typeof item !== "object") return null;
      const label = safeText(item.label || item.name || item.appName || "", 80);
      const packageName = safeText(item.packageName || item.package || "", 120);
      if (!label || !packageName) return null;
      const aliases = Array.isArray(item.aliases)
        ? item.aliases.map((alias) => safeText(alias, 80)).filter(Boolean).slice(0, 6)
        : [];
      return { label, packageName, launchable: item.launchable !== false, aliases };
    })
    .filter(Boolean)
    .filter((item) => item.launchable)
    .slice(0, 12);
}

function plannerAppsForPrompt(deviceContext, goal, hasScreenshot) {
  const candidates = targetAppCandidatesFromDeviceContext(deviceContext);
  if (candidates.length) return candidates.slice(0, 12);

  const apps = installedAppsFromDeviceContext(deviceContext);
  if (!apps.length) return [];

  const cleanGoal = normalizeAppMatchText(goal);
  const matched = apps.filter((app) => {
    const names = [app.label, ...(app.aliases || [])].map(normalizeAppMatchText).filter(Boolean);
    return names.some((name) => name && cleanGoal && (cleanGoal.includes(name) || name.includes(cleanGoal)));
  });

  if (matched.length) return matched.slice(0, 12);
  return hasScreenshot ? [] : apps.slice(0, 24);
}

function buildAgentPlannerMessages(goal, snapshot, supportedSteps, screenshotInfo = null, recentActions = [], deviceContext = null, agentMemory = null) {
  const compactDeviceContext = deviceContextSummaryForPrompt(deviceContext);
  const compactMemory = compactAgentMemoryForPrompt(agentMemory, recentActions);
  const hasScreenshot = Boolean(screenshotInfo?.hasImage);
  const plannerApps = plannerAppsForPrompt(deviceContext, goal, hasScreenshot);
  const payload = {
    goal,
    currentApp: snapshot.currentApp || snapshot.packageName || "",
    supportedSteps,
    deviceContext: {
      currentApp: compactDeviceContext.currentApp,
      screen: compactDeviceContext.screen,
      installedApps: plannerApps,
      installedAppCount: compactDeviceContext.installedAppCount,
    },
    memory: compactMemory,
    snapshot: {
      packageName: snapshot.packageName,
      nodeCount: snapshot.nodeCount,
      texts: (snapshot.texts || []).slice(0, 16),
      clickableNodes: (snapshot.clickableNodes || []).slice(0, 18),
      inputNodes: (snapshot.inputNodes || []).slice(0, 4),
      scrollableNodes: (snapshot.scrollableNodes || []).slice(0, 4),
      confidence: snapshot.confidence,
    },
    screenshot: hasScreenshot
      ? {
          width: screenshotInfo.width,
          height: screenshotInfo.height,
          displayWidth: screenshotInfo.displayWidth,
          displayHeight: screenshotInfo.displayHeight,
        }
      : null,
    rules: [
      "只输出 JSON",
      "截图主导，节点辅助",
      "每次只返回一步",
      "入口可见不等于完成",
      "tap_xy 返回 0-1 归一化坐标",
      "高风险动作 requiresConfirmation=true"
    ],
  };

  const content = [{ type: "text", text: JSON.stringify(payload) }];

  if (hasScreenshot) {
    content.push({
      type: "image_url",
      image_url: {
        url: `data:${screenshotInfo.mimeType};base64,${screenshotInfo.base64}`,
      },
    });
  }

  return [
    { role: "system", content: buildAgentPlannerSystemPrompt(supportedSteps, hasScreenshot) },
    { role: "user", content },
  ];
}

async function handleAgentOutcomeVerificationRequest(body, prompt, resolvedModel) {
  const goal = safeText(body.agentGoal || body.goal || prompt, 240);
  const action = body.action || body.agentStep || body.lastAction || {};
  const snapshot = compactScreenSnapshot(body.screenSnapshot || {});
  const screenshotInfo = normalizeAgentScreenshot(body);
  const quick = quickVerifyAgentOutcome(goal, action, snapshot);
  const plannerModel = screenshotInfo.hasImage ? "qwen_vision" : resolvedModel === "deepseek_v4" ? "deepseek_v4" : "qwen";
  const providerModel = screenshotInfo.hasImage
    ? String(process.env.QWEN_VISION_MODEL || "qwen-vl-plus").trim()
    : plannerModel === "deepseek_v4"
      ? process.env.DEEPSEEK_MODEL
      : process.env.QWEN_MODEL;

  const baseMeta = {
    source: quick ? "agent_outcome_local_rule" : "agent_outcome_verifier",
    sourceDetail: quick ? "local_text_rule" : plannerModel,
    model: quick ? "local_text_rule" : plannerModel,
    modelId: quick ? "local_text_rule" : plannerModel,
    modelLabel: quick ? "本地结果规则" : plannerModel === "deepseek_v4" ? "DeepSeek V4 Pro" : plannerModel === "qwen_vision" ? "Qwen 识图" : "Qwen Max",
    providerModel: quick ? "local" : providerModel,
    version: WORKER_VERSION,
  };

  if (quick) {
    return {
      ok: true,
      reply: "已完成点击结果验证。",
      agentOutcome: quick,
      isExpected: quick.isExpected,
      isWrong: quick.isWrong,
      confidence: quick.confidence,
      reason: quick.reason,
      ...baseMeta,
    };
  }

  const officialGuiPlusLoop = Boolean(
    screenshotInfo.hasImage &&
      guiProviderConfig.provider === "aliyun_gui_plus" &&
      guiProviderConfig.mode === "aliyun_openai_compatible"
  );

  if (!goal) {
    return { ok: false, error: "empty_agent_goal", code: "empty_agent_goal", version: WORKER_VERSION };
  }

  if (!screenshotInfo.hasImage && !snapshot.packageName && !snapshot.currentApp && snapshot.nodeCount === 0) {
    const outcome = { isExpected: false, isWrong: false, confidence: 0.2, reason: "没有可用屏幕快照，无法验证结果。" };
    return { ok: true, reply: "当前没有可用屏幕快照。", agentOutcome: outcome, ...outcome, ...baseMeta };
  }

  let raw = "";
  try {
    const messages = buildAgentVerifierMessages(goal, action, snapshot, screenshotInfo);
    if (plannerModel === "deepseek_v4") {
      raw = await callOpenAICompatible(process.env.DEEPSEEK_BASE_URL, process.env.DEEPSEEK_API_KEY, process.env.DEEPSEEK_MODEL, messages, "DeepSeek Agent Verifier", { temperature: 0.02, max_tokens: 360, timeoutMs: TOOL_ROUTER_TIMEOUT_MS, response_format: { type: "json_object" } });
    } else {
      raw = await callOpenAICompatible(process.env.QWEN_BASE_URL, process.env.QWEN_API_KEY, providerModel, messages, screenshotInfo.hasImage ? "Qwen Vision Agent Verifier" : "Qwen Agent Verifier", { temperature: 0.02, max_tokens: 360, timeoutMs: Number(process.env.QWEN_VISION_TIMEOUT_MS || TOOL_ROUTER_TIMEOUT_MS), response_format: { type: "json_object" } });
    }
  } catch (error) {
    const outcome = { isExpected: false, isWrong: false, confidence: 0.25, reason: `验证器暂时不可用：${String(error?.message || error).replace(/Bearer\s+[A-Za-z0-9_\-\.]+/gi, "Bearer ***").slice(0, 140)}` };
    return { ok: true, reply: "智能体结果验证器暂时不可用。", agentOutcome: outcome, ...outcome, ...baseMeta };
  }

  let parsed;
  try { parsed = JSON.parse(extractJsonText(raw)); } catch (_) { parsed = {}; }
  const outcome = normalizeAgentOutcome(parsed);
  return {
    ok: true,
    reply: "已完成点击结果验证。",
    agentOutcome: outcome,
    isExpected: outcome.isExpected,
    isWrong: outcome.isWrong,
    confidence: outcome.confidence,
    reason: outcome.reason,
    ...baseMeta,
  };
}

function cleanupAgentSessions() {
  const now = Date.now();
  for (const [key, value] of AGENT_SESSIONS.entries()) {
    if (!value || now - Number(value.updatedAt || 0) > AGENT_SESSION_TTL_MS) AGENT_SESSIONS.delete(key);
  }
  const maxSessions = Math.min(AGENT_SESSION_MAX, AGENT_GUI_SESSION_MAX);
  if (AGENT_SESSIONS.size <= maxSessions) return;
  const entries = [...AGENT_SESSIONS.entries()].sort((a, b) => Number(a[1]?.updatedAt || 0) - Number(b[1]?.updatedAt || 0));
  for (const [key] of entries.slice(0, Math.max(0, AGENT_SESSIONS.size - maxSessions))) AGENT_SESSIONS.delete(key);
}

function normalizeAgentSessionId(body, goal) {
  const raw = safeText(body?.agentSessionId || body?.sessionId || body?.agentMemory?.loopSignals?.agentSessionId || "", 100);
  if (raw) return raw;
  const fallback = normalizeForMatch(goal).slice(0, 40) || "anonymous";
  return `agent_${fallback}`;
}

function getAgentSession(body, goal) {
  cleanupAgentSessions();
  const id = normalizeAgentSessionId(body, goal);
  const existing = AGENT_SESSIONS.get(id);
  if (existing) {
    existing.updatedAt = Date.now();
    existing.goal = goal || existing.goal || "";
    return existing;
  }
  const created = {
    id,
    guiSessionId: newAgentGuiSessionId(),
    goal: goal || "",
    createdAt: Date.now(),
    updatedAt: Date.now(),
    step: 0,
    guiHistory: [],
    visualFrame: null,
    lastFingerprint: "",
    lastStableScreenKey: "",
    lastActionKey: "",
    lastObservationReason: "",
    failedVisualCount: 0,
  };
  AGENT_SESSIONS.set(id, created);
  return created;
}


function newAgentGuiSessionId() {
  try {
    if (crypto && typeof crypto.randomUUID === "function") return crypto.randomUUID();
  } catch (_) {}
  return `gui-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function trimAgentGuiHistory(session) {
  if (!session || !Array.isArray(session.guiHistory)) return;
  while (session.guiHistory.length > AGENT_GUI_HISTORY_N) session.guiHistory.shift();
}

function rememberAgentGuiTurn(session, screenshotInfo, rawOutput, compactAction = null) {
  if (!session || !screenshotInfo?.hasImage || !rawOutput) return;
  if (!Array.isArray(session.guiHistory)) session.guiHistory = [];
  session.guiHistory.push({
    image: {
      mimeType: screenshotInfo.mimeType || "image/jpeg",
      base64: screenshotInfo.base64 || "",
      width: Number(screenshotInfo.width) || 0,
      height: Number(screenshotInfo.height) || 0,
      displayWidth: Number(screenshotInfo.displayWidth) || Number(screenshotInfo.width) || 0,
      displayHeight: Number(screenshotInfo.displayHeight) || Number(screenshotInfo.height) || 0,
    },
    output: String(rawOutput || "").slice(0, 6000),
    compactAction: compactAction || null,
    createdAt: Date.now(),
  });
  trimAgentGuiHistory(session);
}

function aliyunGuiDateInfo() {
  try {
    const parts = new Intl.DateTimeFormat("zh-CN", {
      timeZone: "Asia/Singapore",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      weekday: "long",
    }).formatToParts(new Date());
    const get = (type) => parts.find((p) => p.type === type)?.value || "";
    return `今天的日期是:${get("year")}年${get("month")}月${get("day")}日 ${get("weekday")}。`;
  } catch (_) {
    return "";
  }
}

function extractMobileUseActionSummary(output) {
  const raw = String(output || "").trim();
  const actionLine = (raw.match(/^Action:\s*(.*)$/im) || [])[1];
  if (actionLine) return safeText(actionLine, 120);
  const args = extractAliyunMobileUseToolCall(raw);
  if (args) {
    const action = safeText(args.action || "", 24);
    const text = safeText(args.text || args.button || "", 80);
    const coordinate = Array.isArray(args.coordinate) ? ` @${args.coordinate.slice(0, 2).join(",")}` : "";
    return safeText([action, text, coordinate].filter(Boolean).join(" "), 120);
  }
  return safeText(raw.replace(/<tool_call>[\s\S]*?<\/tool_call>/gi, ""), 120);
}


function screenFingerprint(snapshot, screenshotInfo) {
  const texts = [
    ...(Array.isArray(snapshot?.texts) ? snapshot.texts : []),
    ...(Array.isArray(snapshot?.clickableNodes) ? snapshot.clickableNodes.map((node) => node?.text || "") : []),
  ].map((item) => safeText(item, 40)).filter(Boolean).slice(0, 12).join("|");
  const base = [
    snapshot?.packageName || snapshot?.currentApp || "",
    snapshot?.nodeCount || 0,
    screenshotInfo?.hasImage ? `${screenshotInfo.width}x${screenshotInfo.height}` : "noimg",
    texts,
  ].join("||");
  let hash = 0;
  for (let i = 0; i < base.length; i += 1) hash = ((hash << 5) - hash + base.charCodeAt(i)) | 0;
  return `${Math.abs(hash)}:${base.slice(0, 120)}`;
}

function cleanStableScreenText(value) {
  const raw = safeText(value, 80);
  if (!raw) return "";
  const compact = raw
    .replace(/[+\-−]?\d+(?:\.\d+)?\s*%/g, "")
    .replace(/[+\-−]?\d+(?:\.\d+)?\s*(?:亿|万|元|股|手|次|日|月|年|KB|MB|GB|k|m|b)?/gi, "")
    .replace(/[￥$¥]/g, "")
    .replace(/\s+/g, "")
    .trim();
  const normalized = normalizeForMatch(compact);
  if (!normalized || normalized.length < 2) return "";
  if (/^[a-z0-9_.-]{4,}$/i.test(normalized)) return "";
  if (/^(涨|跌|涨幅|跌幅|价格|现价|成交额|热度|广告|查看|更多|搜索)$/.test(normalized)) return "";
  if (compact.length > 18 && !/[首页行情自选交易资讯理财推荐热榜榜单联系人设置搜索]/.test(compact)) return "";
  return safeText(compact, 30);
}

function stableScreenKey(snapshot, screenshotInfo) {
  const app = safeText(snapshot?.packageName || snapshot?.currentApp || "", 100);
  const rawTexts = [
    ...(Array.isArray(snapshot?.texts) ? snapshot.texts : []),
    ...(Array.isArray(snapshot?.clickableNodes) ? snapshot.clickableNodes.map((node) => node?.text || "") : []),
  ];
  const stableTexts = [];
  const seen = new Set();
  for (const item of rawTexts) {
    const cleaned = cleanStableScreenText(item);
    if (!cleaned) continue;
    const key = normalizeForMatch(cleaned);
    if (!key || seen.has(key)) continue;
    seen.add(key);
    stableTexts.push(cleaned);
    if (stableTexts.length >= 10) break;
  }
  const title = stableTexts.find((text) => text.length >= 2 && text.length <= 12) || stableTexts[0] || "";
  const navWords = ["首页", "行情", "自选", "交易", "资讯", "理财", "推荐", "热榜", "关注", "咨询", "搜索", "设置", "联系人", "发现", "我的"];
  const nav = navWords.filter((word) => rawTexts.some((text) => normalizeForMatch(text).includes(normalizeForMatch(word)))).join("|");
  const visualSize = screenshotInfo?.hasImage ? `${screenshotInfo.displayWidth || screenshotInfo.width}x${screenshotInfo.displayHeight || screenshotInfo.height}` : "noimg";
  return [app, visualSize, title, nav].filter(Boolean).join("||").slice(0, 180);
}

function visualFrameHasUsefulSignal(visualFrame) {
  if (!visualFrame || typeof visualFrame !== "object") return false;
  const hasPage = Boolean(safeText(visualFrame.pageTitle || visualFrame.pageType || visualFrame.summary || "", 4));
  const hasTarget = Boolean(visualFrame.targetVisible && (safeText(visualFrame.targetText || "", 2) || (Number.isFinite(Number(visualFrame.targetX)) && Number.isFinite(Number(visualFrame.targetY)))));
  const hasDecision = Boolean(visualFrame.isComplete || visualFrame.isWrong);
  const suggested = visualFrame.suggestedAction || {};
  const hasAction = Boolean(normalizeAgentStepType(suggested.type || "") !== "need_user_help" && (safeText(suggested.targetText || "", 2) || Number.isFinite(Number(suggested.x)) || suggested.direction));
  return hasPage || hasTarget || hasDecision || hasAction;
}

function isVisualFrameCacheable(visualFrame) {
  if (!visualFrameHasUsefulSignal(visualFrame)) return false;
  const confidence = Number(visualFrame?.confidence || 0);
  if (visualFrame?.isComplete || visualFrame?.isWrong) return confidence >= AGENT_VISUAL_FRAME_MIN_SIGNAL_CONFIDENCE;
  return confidence >= AGENT_VISUAL_CACHE_MIN_CONFIDENCE;
}

function latestActionKeyFromRecent(recentActions) {
  if (!Array.isArray(recentActions) || !recentActions.length) return "";
  return safeText(recentActions[recentActions.length - 1] || "", 160);
}

function normalizeVisualFrame(value, fallback = {}) {
  const raw = value && typeof value === "object" ? value : {};
  const nested = raw.visualFrame || raw.frame || raw.observation || raw.result || raw;
  const target = nested.target || nested.targetLocation || nested.targetCoordinate || {};
  const action = nested.suggestedAction || nested.nextVisualAction || {};
  const xRaw = nested.targetX ?? nested.x ?? target.x ?? action.x ?? action.targetX;
  const yRaw = nested.targetY ?? nested.y ?? target.y ?? action.y ?? action.targetY;
  const sxRaw = action.x ?? action.targetX ?? nested.suggestedX ?? nested.actionX ?? xRaw;
  const syRaw = action.y ?? action.targetY ?? nested.suggestedY ?? nested.actionY ?? yRaw;
  const confidenceRaw = Number(nested.confidence ?? nested.score ?? fallback.confidence ?? 0);
  const confidence = Number.isFinite(confidenceRaw) ? Math.max(0, Math.min(1, confidenceRaw)) : 0;
  const targetX = clamp01(xRaw);
  const targetY = clamp01(yRaw);
  const suggestedX = clamp01(sxRaw);
  const suggestedY = clamp01(syRaw);
  const actionType = normalizeAgentStepType(action.type || nested.suggestedActionType || nested.actionType || "");
  const isComplete = Boolean(nested.isComplete === true || nested.complete === true || nested.completed === true || String(nested.result || "").toLowerCase() === "complete");
  const isWrong = Boolean(nested.isWrong === true || nested.wrong === true || String(nested.result || "").toLowerCase() === "wrong");
  const targetVisible = Boolean(nested.targetVisible === true || nested.visible === true || (Number.isFinite(targetX) && Number.isFinite(targetY)));
  return {
    pageTitle: safeText(nested.pageTitle || nested.title || fallback.pageTitle || "", 80),
    pageType: safeText(nested.pageType || nested.screenType || fallback.pageType || "", 80),
    summary: safeText(nested.summary || nested.description || fallback.summary || "", 240),
    isComplete,
    isWrong: isComplete ? false : isWrong,
    targetVisible,
    targetText: safeText(nested.targetText || nested.visibleTarget || action.targetText || "", 80),
    targetX: Number.isFinite(targetX) ? targetX : undefined,
    targetY: Number.isFinite(targetY) ? targetY : undefined,
    suggestedAction: {
      type: SUPPORTED_AGENT_STEP_TYPES.includes(actionType) ? actionType : "",
      targetText: safeText(action.targetText || nested.suggestedActionTarget || nested.targetText || "", 80),
      x: Number.isFinite(suggestedX) ? suggestedX : undefined,
      y: Number.isFinite(suggestedY) ? suggestedY : undefined,
      direction: normalizeAgentDirection(action.direction || nested.direction || ""),
      reason: safeText(action.reason || nested.actionReason || "", 160),
    },
    completionEvidence: safeText(nested.completionEvidence || nested.evidence || nested.reason || "", 180),
    reason: safeText(nested.reason || nested.rationale || nested.explanation || "", 220),
    confidence,
    capturedAt: Date.now(),
  };
}

function buildAgentVisualObserverSystemPrompt() {
  return [
    "你是 Android Computer Use 的视觉状态观察器，只负责看图确权，不负责完整规划。",
    "你必须以 screenshot 为主输入，节点文字只作为辅助线索。",
    "输出严格 JSON，不要 Markdown，不要解释。",
    "判断当前页面是什么、用户目标是否已经完成、目标入口/目标区域是否可见。",
    "如果目标入口可见，返回 0-1 归一化坐标；如果已经到达目标页面或目标功能的默认子页面，isComplete=true。",
    "除非截图明显是加载、空白或动画过渡，否则不要建议 wait。",
    "不要因为节点里有某个词就判完成；完成必须来自截图中的页面标题、已选中状态或主体内容。",
    "输出格式：",
    "{\"visualFrame\":{\"pageTitle\":\"\",\"pageType\":\"\",\"summary\":\"\",\"isComplete\":false,\"isWrong\":false,\"targetVisible\":false,\"targetText\":\"\",\"targetX\":0.0,\"targetY\":0.0,\"suggestedAction\":{\"type\":\"tap_xy|scroll|swipe|back|wait|none\",\"targetText\":\"\",\"x\":0.0,\"y\":0.0,\"direction\":\"up|down|left|right\",\"reason\":\"\"},\"completionEvidence\":\"\",\"confidence\":0.0,\"reason\":\"\"}}"
  ].join("\n");
}

function buildAgentVisualObserverMessages(goal, snapshot, screenshotInfo, session, recentActions = []) {
  const previous = session?.visualFrame ? {
    pageTitle: session.visualFrame.pageTitle,
    pageType: session.visualFrame.pageType,
    summary: session.visualFrame.summary,
    isComplete: session.visualFrame.isComplete,
    confidence: session.visualFrame.confidence,
  } : null;
  const payload = {
    goal,
    previousVisualFrame: previous,
    lastAction: latestActionKeyFromRecent(recentActions),
    currentApp: snapshot.currentApp || snapshot.packageName || "",
    snapshotHints: {
      packageName: snapshot.packageName,
      nodeCount: snapshot.nodeCount,
      texts: (snapshot.texts || []).slice(0, 10),
      clickableTexts: (snapshot.clickableNodes || []).map((node) => node.text).filter(Boolean).slice(0, 12),
    },
    task: "只观察当前截图和目标关系，输出 visualFrame。",
  };
  return [
    { role: "system", content: buildAgentVisualObserverSystemPrompt() },
    {
      role: "user",
      content: [
        { type: "text", text: JSON.stringify(payload) },
        { type: "image_url", image_url: { url: `data:${screenshotInfo.mimeType};base64,${screenshotInfo.base64}` } },
      ],
    },
  ];
}


function isLatestActionLikelyConsumedVisualSuggestion(visualFrame, latestActionKey) {
  if (!visualFrame || typeof visualFrame !== "object") return false;
  const key = safeText(latestActionKey || "", 220).toLowerCase();
  if (!key) return false;
  const suggested = visualFrame.suggestedAction || {};
  const actionType = normalizeAgentStepType(suggested.type || "");
  const targetText = normalizeForMatch(suggested.targetText || visualFrame.targetText || "");

  if ((key.includes("tap_xy") || key.includes("tap_node") || key.includes("点击")) && (actionType === "tap_xy" || visualFrame.targetVisible)) {
    if (!targetText) return true;
    return key.includes(targetText) || normalizeForMatch(key).includes(targetText);
  }
  if (key.includes("wait") || key.includes("等待")) return actionType === "wait";
  if (key.includes("swipe") || key.includes("滑动")) return actionType === "swipe" || actionType === "scroll";
  if (key.includes("scroll") || key.includes("滚动")) return actionType === "scroll" || actionType === "swipe";
  return false;
}


function countRecentActionKind(recentActions, kind) {
  if (!Array.isArray(recentActions) || !kind) return 0;
  const k = normalizeForMatch(kind);
  return recentActions.filter((line) => normalizeForMatch(line).includes(k)).length;
}

function evidenceTextForCompletion(snapshot, visualFrame) {
  const parts = [];
  if (visualFrame && typeof visualFrame === "object") {
    parts.push(visualFrame.pageTitle, visualFrame.pageType, visualFrame.summary, visualFrame.targetText, visualFrame.completionEvidence, visualFrame.reason);
    if (visualFrame.suggestedAction) parts.push(visualFrame.suggestedAction.targetText, visualFrame.suggestedAction.reason);
  }
  const texts = Array.isArray(snapshot?.texts) ? snapshot.texts : [];
  const clickableTexts = Array.isArray(snapshot?.clickableNodes) ? snapshot.clickableNodes.map((node) => node?.text || "") : [];
  parts.push(...texts.slice(0, 24), ...clickableTexts.slice(0, 20));
  return normalizeForMatch(parts.map((item) => safeText(item, 120)).filter(Boolean).join(" "));
}

function goalCoreForCompletion(goal, deviceContext) {
  let core = safeText(goal || "", 120);
  const candidates = targetAppCandidatesFromDeviceContext(deviceContext || {});
  for (const app of candidates) {
    const names = [app.label, ...(app.aliases || [])].map((name) => safeText(name, 80)).filter(Boolean);
    for (const name of names) core = core.replace(new RegExp(escapeRegExp(name), "gi"), "");
  }
  core = core
    .replace(/帮我|请|麻烦|一下|这个|那个|软件|应用|app|页面|界面|功能/g, "")
    .replace(/打开|开启|启动|进入|找到|查找|查看|看一下|看|去到|跳到|前往/g, "")
    .replace(/[\s，。,.、:：/\\\-_]+/g, "");
  return normalizeForMatch(core);
}

function goalAppearsCompleteFromEvidence(goal, snapshot, visualFrame, deviceContext) {
  const rawGoal = safeText(goal || "", 160);
  const normalizedGoal = normalizeForMatch(rawGoal);
  const core = goalCoreForCompletion(rawGoal, deviceContext);
  const evidence = evidenceTextForCompletion(snapshot, visualFrame);
  if (!evidence) return null;
  if (core && core.length >= 2 && evidence.includes(core)) {
    return { confidence: 0.82, reason: `当前截图/视觉状态已经出现目标栏目：${core}` };
  }
  // 榜单/热榜类功能经常进入默认子栏目后标题不再完整显示“热榜”，例如热股/热度/热门/大家都在看。
  // 这里不是用节点单独判定完成，而是作为视觉状态的完成证据补强，避免在已到达目标页后反复 wait。
  if (normalizedGoal.includes("热榜") || normalizedGoal.includes("榜单")) {
    const hotEvidence = ["热股", "热度", "热门", "大家都在看", "关注的股票", "排行", "榜"].some((word) => evidence.includes(normalizeForMatch(word)));
    if (hotEvidence) return { confidence: 0.8, reason: "当前页面已显示榜单/热榜类主体内容。" };
  }
  if (normalizedGoal.includes("自选") && evidence.includes("自选")) {
    return { confidence: 0.78, reason: "当前页面已显示自选相关主体内容。" };
  }
  return null;
}

function isLikelyLoadingOrTransition(snapshot, visualFrame) {
  const evidence = evidenceTextForCompletion(snapshot, visualFrame);
  if (!evidence) return true;
  const nodeCount = Number(snapshot?.nodeCount || 0);
  const textCount = Array.isArray(snapshot?.texts) ? snapshot.texts.length : 0;
  if (nodeCount <= 3 && textCount <= 2) return true;
  return ["加载", "正在", "刷新", "loading", "空白", "跳转", "过渡"].some((word) => evidence.includes(normalizeForMatch(word)));
}

function visualFrameToDirectAgentPlan(visualFrame, snapshot, supportedSteps, goal, screenshotInfo, deviceContext, recentActions = [], fromCache = false) {
  if (!visualFrame || typeof visualFrame !== "object") return null;
  const completionEvidence = goalAppearsCompleteFromEvidence(goal, snapshot, visualFrame, deviceContext);
  if (completionEvidence && visualFrameHasUsefulSignal(visualFrame) && Number(visualFrame.confidence || 0) >= 0.42) {
    const confidence = Math.max(completionEvidence.confidence, Number(visualFrame.confidence || 0));
    const step = normalizeAgentStep({ agentStep: { type: "finish", reason: completionEvidence.reason, riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    const state = normalizeAgentState({ agentState: { isComplete: true, expectedProgress: true, isWrong: false, confidence, reason: completionEvidence.reason } }, step);
    return { agentStep: step, agentState: state, source: "visual_frame_completion_evidence" };
  }
  if (visualFrame.isComplete && visualFrame.confidence >= 0.58) {
    const step = normalizeAgentStep({ agentStep: { type: "finish", reason: visualFrame.completionEvidence || visualFrame.reason || "视觉状态已确认目标完成。", riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    const state = normalizeAgentState({ agentState: { isComplete: true, expectedProgress: true, isWrong: false, confidence: visualFrame.confidence, reason: visualFrame.completionEvidence || visualFrame.reason || "视觉状态已确认目标完成。" } }, step);
    return { agentStep: step, agentState: state, source: "visual_frame_complete" };
  }
  if (visualFrame.isWrong && visualFrame.confidence >= 0.82) {
    const step = normalizeAgentStep({ agentStep: { type: "back", reason: visualFrame.reason || "视觉状态判断当前页面偏离目标，返回上一层。", riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    const state = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: false, isWrong: true, confidence: visualFrame.confidence, reason: visualFrame.reason || "视觉状态判断当前页面偏离目标。" } }, step);
    return { agentStep: step, agentState: state, source: "visual_frame_wrong" };
  }
  const suggested = visualFrame.suggestedAction || {};
  const actionType = normalizeAgentStepType(suggested.type || "");
  const latestActionKey = latestActionKeyFromRecent(recentActions);
  const visualSuggestionConsumed = fromCache && isLatestActionLikelyConsumedVisualSuggestion(visualFrame, latestActionKey);
  const x = Number(suggested.x ?? visualFrame.targetX);
  const y = Number(suggested.y ?? visualFrame.targetY);
  if (!visualSuggestionConsumed && (actionType === "tap_xy" || (!actionType && visualFrame.targetVisible)) && Number.isFinite(x) && Number.isFinite(y) && visualFrame.confidence >= 0.38) {
    const step = normalizeAgentStep({ agentStep: { type: "tap_xy", x, y, targetText: suggested.targetText || visualFrame.targetText, reason: suggested.reason || visualFrame.reason || "视觉状态发现目标入口，执行坐标点击。", riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    const state = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: true, isWrong: false, confidence: Math.max(0.55, visualFrame.confidence), reason: visualFrame.reason || "视觉状态发现目标入口，继续执行。" } }, step);
    return { agentStep: step, agentState: state, source: "visual_frame_target" };
  }
  if (!visualSuggestionConsumed && ["scroll", "swipe", "back"].includes(actionType) && visualFrame.confidence >= 0.45) {
    const step = normalizeAgentStep({ agentStep: { type: actionType, direction: suggested.direction || "up", reason: suggested.reason || visualFrame.reason || "根据视觉状态继续探索。", riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    const state = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: true, isWrong: false, confidence: visualFrame.confidence, reason: visualFrame.reason || "视觉状态建议继续探索。" } }, step);
    return { agentStep: step, agentState: state, source: "visual_frame_action" };
  }
  if (!visualSuggestionConsumed && actionType === "wait" && visualFrame.confidence >= 0.45 && isLikelyLoadingOrTransition(snapshot, visualFrame) && countRecentActionKind(recentActions, "wait") === 0) {
    const step = normalizeAgentStep({ agentStep: { type: "wait", durationMs: 700, reason: suggested.reason || visualFrame.reason || "页面仍在加载或过渡，短暂等待。", riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    const state = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: true, isWrong: false, confidence: visualFrame.confidence, reason: visualFrame.reason || "视觉状态建议等待加载。" } }, step);
    return { agentStep: step, agentState: state, source: "visual_frame_wait_loading" };
  }
  return null;
}

function buildAgentTextPlannerMessages(goal, snapshot, supportedSteps, visualFrame, session, recentActions = [], deviceContext = null, agentMemory = null) {
  const compactMemory = compactAgentMemoryForPrompt(agentMemory, recentActions);
  const compactDeviceContext = deviceContextSummaryForPrompt(deviceContext);
  const plannerApps = plannerAppsForPrompt(deviceContext, goal, false);
  const latestAction = latestActionKeyFromRecent(recentActions);
  const visualSuggestionConsumed = isLatestActionLikelyConsumedVisualSuggestion(visualFrame, latestAction);
  const payload = {
    goal,
    visualFrame: visualFrame || null,
    visualSuggestionConsumed,
    latestAction,
    session: {
      id: session?.id || "",
      step: session?.step || 0,
      lastObservationReason: session?.lastObservationReason || "",
    },
    currentApp: snapshot.currentApp || snapshot.packageName || "",
    supportedSteps,
    deviceContext: {
      currentApp: compactDeviceContext.currentApp,
      screen: compactDeviceContext.screen,
      installedApps: plannerApps,
    },
    memory: compactMemory,
    actionBatch: {
      enabled: true,
      maxSteps: AGENT_ACTION_BATCH_MAX,
      contract: "Return agentStep as the first action. If the next 2-3 actions are low-risk and safe without a fresh screenshot, also return agentSteps/actionBatch with those actions in order.",
      stopConditions: ["visual_after_input", "visual_after_system_action", "visual_after_uncertain_progress"],
    },
    snapshotHints: {
      packageName: snapshot.packageName,
      nodeCount: snapshot.nodeCount,
      texts: (snapshot.texts || []).slice(0, 12),
      clickableNodes: (snapshot.clickableNodes || []).slice(0, 14),
      inputNodes: (snapshot.inputNodes || []).slice(0, 3),
      scrollableNodes: (snapshot.scrollableNodes || []).slice(0, 3),
    },
    rules: [
      "When safe, return agentSteps/actionBatch with up to 3 low-risk local actions. Keep agentStep equal to the first item.",
      "Do not batch input_text, open_app, back/home/system actions, or any action requiring confirmation unless it is the final single action.",
      "visualFrame 是视觉确权结果，优先级高于节点。",
      "节点只作为 affordance，不能单独证明任务完成。",
      "每次只返回一步 agentStep。",
      "如果 visualSuggestionConsumed=true 且当前页面没有明显变化，不要重复同一个视觉建议动作。",
      "除非页面明显加载/空白/动画过渡，否则不要返回 wait；连续 wait 一次后仍无变化，应 finish、换路径、back 或 need_user_help。",
      "如果当前视觉状态或主体文本已经证明进入目标栏目/目标功能默认子页，应直接 finish。",
      "高风险动作 requiresConfirmation=true。"
    ],
  };
  return [
    { role: "system", content: buildAgentPlannerSystemPrompt(supportedSteps, false) },
    { role: "user", content: JSON.stringify(payload) },
  ];
}

async function callAgentVisualObserver(goal, snapshot, screenshotInfo, session, recentAgentActions, providerModel, timeoutMs = AGENT_STEP_VISION_TIMEOUT_MS) {
  const messages = buildAgentVisualObserverMessages(goal, snapshot, screenshotInfo, session, recentAgentActions);
  const raw = await callOpenAICompatible(
    process.env.QWEN_BASE_URL,
    process.env.QWEN_API_KEY,
    providerModel,
    messages,
    "Qwen Vision Agent Observer",
    {
      temperature: 0,
      max_tokens: Math.min(AGENT_VISION_MAX_TOKENS, 220),
      timeoutMs: Math.max(300, Number(timeoutMs || AGENT_STEP_VISION_TIMEOUT_MS)),
    }
  );
  let parsed;
  try { parsed = JSON.parse(extractJsonText(raw)); } catch (_) { parsed = {}; }
  return normalizeVisualFrame(parsed);
}

async function callAgentTextPlanner(goal, snapshot, supportedSteps, visualFrame, session, recentAgentActions, deviceContext, agentMemory, timeoutMs = AGENT_STEP_TEXT_PLANNER_TIMEOUT_MS) {
  const messages = buildAgentTextPlannerMessages(goal, snapshot, supportedSteps, visualFrame, session, recentAgentActions, deviceContext, agentMemory);
  const raw = await callOpenAICompatible(
    process.env.QWEN_BASE_URL,
    process.env.QWEN_API_KEY,
    process.env.QWEN_MODEL,
    messages,
    "Qwen Agent Text Planner",
    {
      temperature: 0,
      max_tokens: Math.min(AGENT_TEXT_MAX_TOKENS, 220),
      timeoutMs: Math.max(300, Math.min(Number(timeoutMs || AGENT_STEP_TEXT_PLANNER_TIMEOUT_MS), AGENT_TEXT_PLANNER_TIMEOUT_MS)),
      response_format: { type: "json_object" },
    }
  );
  let parsed;
  try { parsed = JSON.parse(extractJsonText(raw)); } catch (_) { parsed = {}; }
  return parsed;
}




function buildAgentVisionPlannerSystemPrompt(supportedSteps) {
  const allowed = supportedSteps.join(", ");
  return [
    "你是 Android 手机 Computer Use 的云端 GUI 操作员，只能输出严格 JSON。",
    "目标：用一轮低延迟视觉决策完成“感知→规划→决策→反思”闭环。不要聊天，不要解释过程，只输出最终动作对象。",
    "",
    `允许动作：${allowed}`,
    "",
    "输出短 JSON，字段越少越好，但必须足够执行：",
    "{\"s\":\"p|d|w|u\",\"phase\":\"感知/规划/决策/反思简词\",\"page\":\"当前界面\",\"a\":\"tap_xy|tap_node|scroll|swipe|back|wait|finish|need_user_help\",\"x\":0.5,\"y\":0.5,\"b\":[0.1,0.2,0.3,0.4],\"t\":\"目标控件\",\"n\":\"节点id\",\"d\":\"up|down|left|right\",\"v\":\"输入内容\",\"r\":\"low|medium|high\",\"q\":false,\"c\":0.78,\"e\":\"截图证据\"}",
    "",
    "分层规则：",
    "1. 感知层：先识别当前 App/页面/主要 Tab/可点击控件，只相信整张截图；节点文字和 bounds 只作为辅助落点证据。",
    "2. 规划层：判断目标是否已经完成、是否更接近目标、是否走错。完成才 finish，走错才 back。",
    "3. 决策层：低风险入口可见就直接输出点击动作；不要因为不确定而反复观察。",
    "4. 反思层：结合 last/recent/prev 判断上一步是否已经点过且页面未变化。若同一坐标刚点过且无变化，不要重复同点，改选另一个入口、scroll 或 back。",
    "",
    "坐标和 bbox：",
    "- x/y 与 b 必须相对于整张手机截图归一化 0-1，包括状态栏、顶部栏和底部导航栏。",
    "- b 为目标控件框 [left,top,right,bottom]，x/y 必须是 b 的中心。能给 b 就给 b，但不要为了列候选拖慢。",
    "- 如果使用节点辅助，n/t 应对应节点；如果节点与视觉不一致，以截图视觉为准。",
    "",
    "动作策略：",
    "- 低风险导航点击要果断：设置、首页、行情、联系人、搜索、热榜、榜单、资讯、Tab、列表入口、返回、展开菜单等。",
    "- 目标控件可见且未完成：s=p，a=tap_xy 或 tap_node，给 x/y 或 n。",
    "- 目标页面已经打开且主体内容切换：s=d，a=finish。",
    "- 入口不在当前屏但页面可滚动：a=scroll 或 swipe。",
    "- 当前页面明显偏离目标：s=w，a=back。",
    "- 只有截图明显加载、空白、刷新、跳转动画时才 a=wait；普通静止页面禁止 wait。",
    "- 除非没有截图、没有任何候选、也无法探索，否则不要 need_user_help。",
    "- 支付、转账、下单、删除、发送、发布、授权、登录、验证码、密码：r=high 且 q=true。",
    "",
    "禁止事项：",
    "- 不要定位调试浮窗、状态栏、广告、无关头像或无关搜索框。",
    "- 不要输出 Markdown、自然语言、agentState、visualFrame、agentSteps 或 stopConditions。",
  ].join("\n");
}

function buildAgentVisionPlannerMessages(goal, snapshot, screenshotInfo, session, recentActions = [], supportedSteps = SUPPORTED_AGENT_STEP_TYPES, deviceContext = null, agentMemory = null) {
  const previous = session?.visualFrame ? {
    title: safeText(session.visualFrame.pageTitle || session.visualFrame.pageType || "", 32),
    target: safeText(session.visualFrame.targetText || "", 32),
    complete: Boolean(session.visualFrame.isComplete),
    confidence: Number(session.visualFrame.confidence || 0),
  } : null;
  const compactDeviceContext = deviceContextSummaryForPrompt(deviceContext);
  const targetCore = goalCoreForCompletion(goal, deviceContext || {});
  const targetApps = targetAppCandidatesFromDeviceContext(deviceContext || {});
  const nodeHints = [
    ...(Array.isArray(snapshot.clickableNodes) ? snapshot.clickableNodes : []),
    ...(Array.isArray(snapshot.inputNodes) ? snapshot.inputNodes : []),
  ].map((node) => ({
    id: safeText(node.id || "", 24),
    text: safeText(node.text || "", 48),
    bounds: safeText(node.bounds || "", 48),
    clickable: Boolean(node.clickable),
    editable: Boolean(node.editable),
  })).filter((node) => node.text || node.bounds).slice(0, 18);

  const compactMemory = compactAgentMemoryForPrompt(agentMemory, recentActions);
  const payload = {
    g: goal,
    target: targetCore || goal,
    targetApps: targetApps.slice(0, 4).map((app) => ({ label: app.label, packageName: app.packageName })),
    app: snapshot.currentApp || snapshot.packageName || "",
    last: latestActionKeyFromRecent(recentActions),
    recent: Array.isArray(recentActions) ? recentActions.slice(-5) : [],
    prev: previous,
    loop: compactMemory.loopSignals || {},
    screen: {
      w: screenshotInfo.width,
      h: screenshotInfo.height,
      dw: screenshotInfo.displayWidth,
      dh: screenshotInfo.displayHeight,
      coord: "normalized_full_screenshot_0_1",
    },
    device: {
      currentApp: compactDeviceContext.currentApp,
      screen: compactDeviceContext.screen,
    },
    hints: {
      pkg: snapshot.packageName,
      nodes: snapshot.nodeCount,
      txt: (snapshot.texts || []).slice(0, 12),
      nodeBoxes: nodeHints,
      scroll: (snapshot.scrollableNodes || []).length,
    },
    task: "按感知→规划→决策→反思给出本轮最优低风险动作。不要强制列候选；普通静止页面不要 wait；低风险入口可见就直接点。",
  };
  return [
    { role: "system", content: buildAgentVisionPlannerSystemPrompt(supportedSteps) },
    {
      role: "user",
      content: [
        { type: "text", text: JSON.stringify(payload) },
        { type: "image_url", image_url: { url: `data:${screenshotInfo.mimeType};base64,${screenshotInfo.base64}` } },
      ],
    },
  ];
}

function isCompactVisionPlannerOutput(value) {
  if (!value || typeof value !== "object") return false;
  if (value.agentStep || value.visualFrame || value.agentState || value.step || value.actionStep) return false;
  return ["s", "a", "x", "y", "t", "n", "c", "e"].some((key) => Object.prototype.hasOwnProperty.call(value, key));
}

function normalizeCompactVisionStatus(value, actionType) {
  const raw = String(value || "").toLowerCase().trim();
  if (["d", "done", "complete", "completed", "finish", "success", "expected"].includes(raw) || actionType === "finish") return "complete";
  if (["w", "wrong", "bad", "off", "mismatch", "failed"].includes(raw)) return "wrong";
  if (["p", "progress", "next", "continue", "ok", "found", "visible", "target"].includes(raw)) return "progress";
  return "uncertain";
}

function normalizeCompactRisk(value) {
  const raw = String(value || "low").toLowerCase().trim();
  if (["h", "high", "danger"].includes(raw)) return "high";
  if (["m", "medium", "mid"].includes(raw)) return "medium";
  return "low";
}

function normalizeCompactVisionAction(value, status) {
  const raw = String(value || "").toLowerCase().trim().replace(/-/g, "_");
  if (["tap", "click", "press", "point"].includes(raw)) return "tap_xy";
  if (["done", "complete", "completed"].includes(raw)) return "finish";
  if (["none", "noop", "unknown", "uncertain"].includes(raw)) return status === "complete" ? "finish" : "need_user_help";
  const normalized = normalizeAgentStepType(raw);
  if (normalized === "need_user_help" && status === "complete") return "finish";
  return normalized;
}

function normalizeCompactGroundingBox(parsed) {
  const rawBox = parsed.b ?? parsed.box ?? parsed.bbox ?? parsed.rect ?? parsed.bounds;
  let list = Array.isArray(rawBox) ? rawBox : [];
  if (!list.length && rawBox && typeof rawBox === "object") {
    list = [rawBox.l ?? rawBox.left ?? rawBox.x1, rawBox.t ?? rawBox.top ?? rawBox.y1, rawBox.r ?? rawBox.right ?? rawBox.x2, rawBox.b ?? rawBox.bottom ?? rawBox.y2];
  }
  if (list.length < 4) return null;
  const left = clamp01(list[0]);
  const top = clamp01(list[1]);
  const right = clamp01(list[2]);
  const bottom = clamp01(list[3]);
  if (![left, top, right, bottom].every(Number.isFinite)) return null;
  if (right <= left || bottom <= top) return null;
  return { left, top, right, bottom, centerX: (left + right) / 2, centerY: (top + bottom) / 2 };
}

function normalizeCompactFound(value, hasCoordinate, hasBox, status) {
  if (value === true || value === "true" || value === 1 || value === "1" || value === "yes" || value === "found") return true;
  if (value === false || value === "false" || value === 0 || value === "0" || value === "no" || value === "missing") return false;
  if (status === "complete") return true;
  return Boolean(hasCoordinate || hasBox);
}


function normalizeCompactGroundingCandidates(parsed) {
  const raw = parsed.k ?? parsed.candidates ?? parsed.options ?? parsed.targets ?? [];
  const list = Array.isArray(raw) ? raw : [];
  return list
    .map((item) => {
      if (!item || typeof item !== "object") return null;
      const box = normalizeCompactGroundingBox(item);
      const confidenceRaw = Number(item.c ?? item.confidence ?? item.score ?? 0);
      const confidence = Number.isFinite(confidenceRaw) ? Math.max(0, Math.min(1, confidenceRaw)) : 0;
      return {
        text: safeText(item.t ?? item.text ?? item.label ?? item.targetText ?? "", 60),
        box: box ? { left: box.left, top: box.top, right: box.right, bottom: box.bottom } : undefined,
        centerX: box ? box.centerX : undefined,
        centerY: box ? box.centerY : undefined,
        confidence,
        evidence: safeText(item.e ?? item.evidence ?? item.reason ?? "", 80),
      };
    })
    .filter((item) => item && (item.text || item.box || item.evidence))
    .slice(0, 3);
}

function adaptCompactVisionPlan(parsed) {
  if (!isCompactVisionPlannerOutput(parsed)) return parsed && typeof parsed === "object" ? parsed : {};
  const box = normalizeCompactGroundingBox(parsed);
  const candidates = normalizeCompactGroundingCandidates(parsed);
  const bestCandidate = candidates
    .filter((item) => Number.isFinite(item.centerX) && Number.isFinite(item.centerY))
    .sort((a, b) => Number(b.confidence || 0) - Number(a.confidence || 0))[0] || null;
  let x = clamp01(parsed.x ?? parsed.cx ?? parsed.centerX ?? parsed.targetX);
  let y = clamp01(parsed.y ?? parsed.cy ?? parsed.centerY ?? parsed.targetY);
  if ((!Number.isFinite(x) || !Number.isFinite(y)) && box) {
    x = box.centerX;
    y = box.centerY;
  }
  if ((!Number.isFinite(x) || !Number.isFinite(y)) && bestCandidate) {
    x = bestCandidate.centerX;
    y = bestCandidate.centerY;
  }
  const confidenceRaw = Number(parsed.c ?? parsed.confidence ?? parsed.score ?? bestCandidate?.confidence ?? 0.55);
  const confidence = Number.isFinite(confidenceRaw) ? Math.max(0, Math.min(1, confidenceRaw)) : 0.55;
  const status = normalizeCompactVisionStatus(parsed.s ?? parsed.status, parsed.a);
  let actionType = normalizeCompactVisionAction(parsed.a ?? parsed.action ?? parsed.type, status);
  const hasCoordinate = Number.isFinite(x) && Number.isFinite(y);
  const found = normalizeCompactFound(parsed.found ?? parsed.f ?? parsed.visible ?? parsed.targetVisible, hasCoordinate, Boolean(box), status);
  const riskLevel = normalizeCompactRisk(parsed.r ?? parsed.risk ?? parsed.riskLevel);
  const requiresConfirmation = Boolean(parsed.q ?? parsed.confirm ?? parsed.requiresConfirmation) || riskLevel !== "low";
  const targetText = safeText(parsed.t ?? parsed.targetText ?? parsed.label ?? parsed.textTarget ?? bestCandidate?.text ?? "", 80);
  const targetNodeId = safeText(parsed.n ?? parsed.nodeId ?? parsed.targetNodeId ?? "", 32);
  const appName = safeText(parsed.appName ?? parsed.app ?? parsed.packageLabel ?? (actionType === "open_app" ? targetText : ""), 80);
  const packageName = safeText(parsed.packageName ?? parsed.package ?? "", 120);
  const inputText = safeText(parsed.v ?? parsed.text ?? parsed.inputText ?? "", 180);
  const direction = normalizeAgentDirection(parsed.d ?? parsed.direction ?? "");
  let reason = safeText(parsed.e ?? parsed.evidence ?? parsed.reason ?? parsed.rationale ?? bestCandidate?.evidence ?? "视觉定位规划。", 160);
  const isComplete = status === "complete";
  const isWrong = !isComplete && status === "wrong";

  if (actionType === "need_user_help" && status !== "complete" && bestCandidate && !requiresConfirmation) {
    actionType = "tap_xy";
    reason = reason || "候选目标已经定位，执行低风险点击。";
  }
  if (actionType === "tap_xy" && (!found || !hasCoordinate)) {
    actionType = "need_user_help";
    reason = found ? "视觉定位未给出可靠中心坐标。" : "视觉未可靠定位目标控件，禁止猜坐标。";
  }

  const expectedProgress = isComplete || status === "progress" || ["tap_xy", "tap_node", "scroll", "swipe", "wait", "back"].includes(actionType);
  const targetVisible = found && (hasCoordinate || Boolean(targetText));
  return {
    agentState: {
      isComplete,
      expectedProgress: isWrong ? false : expectedProgress,
      isWrong,
      confidence,
      reason,
      nextHint: "",
    },
    visualFrame: {
      pageTitle: safeText(parsed.p ?? parsed.pageTitle ?? "", 60),
      pageType: safeText(parsed.pt ?? parsed.pageType ?? parsed.phase ?? "", 60),
      summary: safeText(parsed.m ?? parsed.summary ?? parsed.page ?? parsed.phase ?? reason, 160),
      isComplete,
      isWrong,
      targetVisible,
      targetText,
      targetX: targetVisible && hasCoordinate ? x : undefined,
      targetY: targetVisible && hasCoordinate ? y : undefined,
      targetBox: box ? { left: box.left, top: box.top, right: box.right, bottom: box.bottom } : bestCandidate?.box,
      suggestedAction: {
        type: actionType === "finish" ? "" : actionType,
        targetText,
        x: actionType === "tap_xy" && hasCoordinate ? x : undefined,
        y: actionType === "tap_xy" && hasCoordinate ? y : undefined,
        direction,
        reason,
      },
      completionEvidence: isComplete ? reason : "",
      reason,
      confidence,
      groundingFound: found,
      candidates,
    },
    agentStep: {
      type: actionType,
      appName: actionType === "open_app" ? (appName || targetText || undefined) : undefined,
      packageName: actionType === "open_app" ? (packageName || undefined) : undefined,
      targetNodeId: targetNodeId || undefined,
      targetText: targetText || undefined,
      text: actionType === "input_text" ? inputText : inputText || undefined,
      direction: ["scroll", "swipe"].includes(actionType) ? direction || "up" : direction || undefined,
      x: actionType === "tap_xy" && hasCoordinate ? x : undefined,
      y: actionType === "tap_xy" && hasCoordinate ? y : undefined,
      x2: actionType === "swipe" ? parsed.x2 : undefined,
      y2: actionType === "swipe" ? parsed.y2 : undefined,
      durationMs: actionType === "wait" ? Number(parsed.ms || parsed.durationMs || 500) : undefined,
      reason,
      riskLevel,
      requiresConfirmation,
    },
    stopConditions: ["visual_after_input", "visual_after_system_action", "visual_after_uncertain_progress"],
    compactVision: true,
    guiGrounding: true,
  };
}

async function callAgentVisionPlanner(goal, snapshot, screenshotInfo, session, recentAgentActions, supportedSteps, deviceContext, agentMemory, providerModel, timeoutMs = AGENT_STEP_VISION_TIMEOUT_MS) {
  const messages = buildAgentVisionPlannerMessages(goal, snapshot, screenshotInfo, session, recentAgentActions, supportedSteps, deviceContext, agentMemory);
  const raw = await callOpenAICompatible(
    process.env.QWEN_BASE_URL,
    process.env.QWEN_API_KEY,
    providerModel,
    messages,
    "Qwen Vision Agent GUI Grounding",
    {
      temperature: 0,
      max_tokens: Math.min(AGENT_FAST_VISION_MAX_TOKENS, AGENT_VISION_MAX_TOKENS, 220),
      timeoutMs: Math.max(300, Number(timeoutMs || AGENT_STEP_VISION_TIMEOUT_MS)),
    }
  );
  let parsed;
  try { parsed = JSON.parse(extractJsonText(raw)); } catch (_) { parsed = {}; }
  return adaptCompactVisionPlan(parsed);
}

function normalizeAgentGuiProviderName(value) {
  const raw = String(value || "qwen_omni").trim().toLowerCase().replace(/[-\s]+/g, "_");
  if (!raw || ["qwen", "qwen_omni", "qwen_vision", "omni", "default"].includes(raw)) return "qwen_omni";
  if (["aliyun_gui_plus", "gui_plus", "bailian_gui_plus", "dashscope_gui_plus"].includes(raw)) return "aliyun_gui_plus";
  if (["ui_tars", "uitars", "ui_tars_2"].includes(raw)) return "ui_tars";
  if (["ui_venus", "uivenus", "ui_venus_15", "ui_venus_1_5"].includes(raw)) return "ui_venus";
  if (["showui", "show_ui"].includes(raw)) return "showui";
  if (["claude", "claude_computer_use", "anthropic"].includes(raw)) return "claude_computer_use";
  if (["openai_compatible", "openai", "vllm", "sglang"].includes(raw)) return "openai_compatible";
  if (["http", "http_json", "custom", "custom_http", "external", "external_http"].includes(raw)) return "external_http";
  return raw;
}

function resolveAgentGuiProviderConfig(screenshotInfo, qwenVisionModel) {
  if (!screenshotInfo?.hasImage) {
    return {
      requestedProvider: AGENT_GUI_PROVIDER,
      provider: "text_only",
      mode: "text_only",
      providerModel: process.env.QWEN_MODEL || "",
      modelLabel: "文本规划",
      externalUrlConfigured: false,
      fallbackToQwen: false,
      fallbackReason: "",
    };
  }

  const requested = normalizeAgentGuiProviderName(AGENT_GUI_PROVIDER);
  if (requested === "aliyun_gui_plus") {
    const configured = Boolean(ALIYUN_GUI_API_KEY && ALIYUN_GUI_BASE_URL && ALIYUN_GUI_MODEL);
    if (configured) {
      return {
        requestedProvider: requested,
        provider: "aliyun_gui_plus",
        mode: "aliyun_openai_compatible",
        providerModel: ALIYUN_GUI_MODEL,
        modelLabel: "阿里云 GUI Plus",
        externalUrlConfigured: true,
        fallbackToQwen: false,
        runtimeFallbackToQwen: AGENT_GUI_PROVIDER_FALLBACK_TO_QWEN,
        fallbackReason: "",
      };
    }
    if (AGENT_GUI_PROVIDER_FALLBACK_TO_QWEN) {
      return {
        requestedProvider: requested,
        provider: "qwen_omni",
        mode: "qwen_openai_compatible",
        providerModel: qwenVisionModel,
        modelLabel: "Qwen Omni GUI Provider",
        externalUrlConfigured: Boolean(ALIYUN_GUI_BASE_URL && ALIYUN_GUI_MODEL),
        fallbackToQwen: true,
        runtimeFallbackToQwen: false,
        fallbackReason: "阿里云 GUI Plus 未配置 ALIYUN_GUI_API_KEY/QWEN_API_KEY、ALIYUN_GUI_BASE_URL 或 ALIYUN_GUI_MODEL，已回退 Qwen。",
      };
    }
    return {
      requestedProvider: requested,
      provider: "aliyun_gui_plus",
      mode: "unconfigured",
      providerModel: ALIYUN_GUI_MODEL || requested,
      modelLabel: "阿里云 GUI Plus · unconfigured",
      externalUrlConfigured: Boolean(ALIYUN_GUI_BASE_URL && ALIYUN_GUI_MODEL),
      fallbackToQwen: false,
      runtimeFallbackToQwen: false,
      fallbackReason: "阿里云 GUI Plus 未配置 ALIYUN_GUI_API_KEY/QWEN_API_KEY、ALIYUN_GUI_BASE_URL 或 ALIYUN_GUI_MODEL。",
    };
  }

  if (requested === "qwen_omni") {
    return {
      requestedProvider: requested,
      provider: "qwen_omni",
      mode: "qwen_openai_compatible",
      providerModel: qwenVisionModel,
      modelLabel: "Qwen Omni GUI Provider",
      externalUrlConfigured: false,
      fallbackToQwen: false,
      fallbackReason: "",
    };
  }

  const hasOpenAiCompatibleConfig = Boolean(AGENT_GUI_PROVIDER_BASE_URL && AGENT_GUI_PROVIDER_MODEL);
  const hasExternalHttpConfig = Boolean(AGENT_GUI_PROVIDER_URL);

  if (["openai_compatible", "ui_tars", "ui_venus", "showui"].includes(requested) && hasOpenAiCompatibleConfig) {
    return {
      requestedProvider: requested,
      provider: requested,
      mode: "openai_compatible",
      providerModel: AGENT_GUI_PROVIDER_MODEL,
      modelLabel: `${requested} · OpenAI-compatible GUI Provider`,
      externalUrlConfigured: true,
      fallbackToQwen: false,
      fallbackReason: "",
    };
  }

  if (["external_http", "claude_computer_use", "ui_tars", "ui_venus", "showui"].includes(requested) && hasExternalHttpConfig) {
    return {
      requestedProvider: requested,
      provider: requested,
      mode: "external_http_json",
      providerModel: AGENT_GUI_PROVIDER_MODEL || requested,
      modelLabel: `${requested} · external GUI Provider`,
      externalUrlConfigured: true,
      fallbackToQwen: false,
      fallbackReason: "",
    };
  }

  if (AGENT_GUI_PROVIDER_FALLBACK_TO_QWEN) {
    return {
      requestedProvider: requested,
      provider: "qwen_omni",
      mode: "qwen_openai_compatible",
      providerModel: qwenVisionModel,
      modelLabel: "Qwen Omni GUI Provider",
      externalUrlConfigured: hasOpenAiCompatibleConfig || hasExternalHttpConfig,
      fallbackToQwen: true,
      fallbackReason: `GUI provider ${requested} 未配置可用 URL/BaseURL/Model，已回退 Qwen。`,
    };
  }

  return {
    requestedProvider: requested,
    provider: requested,
    mode: "unconfigured",
    providerModel: AGENT_GUI_PROVIDER_MODEL || requested,
    modelLabel: `${requested} · unconfigured`,
    externalUrlConfigured: hasOpenAiCompatibleConfig || hasExternalHttpConfig,
    fallbackToQwen: false,
    fallbackReason: `GUI provider ${requested} 未配置可用 URL/BaseURL/Model。`,
  };
}

function buildExternalGuiProviderPayload(goal, snapshot, screenshotInfo, session, recentActions, supportedSteps, deviceContext, agentMemory, providerConfig) {
  const nodeBoxes = [
    ...(Array.isArray(snapshot.clickableNodes) ? snapshot.clickableNodes : []),
    ...(Array.isArray(snapshot.inputNodes) ? snapshot.inputNodes : []),
  ].map((node) => ({
    id: safeText(node.id || "", 24),
    text: safeText(node.text || "", 48),
    bounds: safeText(node.bounds || "", 48),
    clickable: Boolean(node.clickable),
    editable: Boolean(node.editable),
  })).filter((node) => node.text || node.bounds).slice(0, 24);

  return {
    schema: "ai_ledger_gui_provider_v1",
    provider: providerConfig?.provider || AGENT_GUI_PROVIDER,
    mode: providerConfig?.mode || "external_http_json",
    goal,
    supportedSteps,
    coordinateSystem: "normalized_full_screenshot_0_1",
    outputSchema: {
      description: "返回单个 JSON。按感知→规划→决策→反思给出本轮最优动作；低风险入口可见时直接给动作，不要泛泛 wait。",
      compact: "{\"s\":\"p|d|w|u\",\"phase\":\"感知/规划/决策/反思简词\",\"a\":\"tap_xy|tap_node|scroll|swipe|back|finish|need_user_help\",\"x\":0.5,\"y\":0.5,\"b\":[0.4,0.4,0.6,0.6],\"t\":\"目标文字\",\"c\":0.8,\"e\":\"短证据\"}",
      fields: {
        s: "p=继续/进展, d=完成, w=走错页, u=不确定",
        a: "动作类型",
        x: "归一化中心 x，相对整张手机截图",
        y: "归一化中心 y，相对整张手机截图",
        b: "目标框 [left,top,right,bottom]，归一化坐标",
        t: "目标控件文字或目标名",
        c: "0-1 置信度",
        phase: "可选：感知/规划/决策/反思的极短标签",
        e: "不超过 40 字的可见证据",
      },
    },
    screen: {
      currentApp: snapshot.currentApp || snapshot.packageName || "",
      packageName: snapshot.packageName || "",
      texts: (snapshot.texts || []).slice(0, 18),
      nodeCount: snapshot.nodeCount || 0,
      capturedNodeCount: snapshot.capturedNodeCount || 0,
      clickableNodes: nodeBoxes,
      screenshot: {
        mimeType: screenshotInfo.mimeType || "image/jpeg",
        width: screenshotInfo.width || 0,
        height: screenshotInfo.height || 0,
        displayWidth: screenshotInfo.displayWidth || screenshotInfo.width || 0,
        displayHeight: screenshotInfo.displayHeight || screenshotInfo.height || 0,
        base64: screenshotInfo.base64 || "",
      },
    },
    memory: {
      recentActions: Array.isArray(recentActions) ? recentActions.slice(-8) : [],
      loopSignals: agentMemory?.loopSignals || {},
      previousVisualFrame: session?.visualFrame ? {
        pageTitle: safeText(session.visualFrame.pageTitle || "", 60),
        pageType: safeText(session.visualFrame.pageType || "", 60),
        targetText: safeText(session.visualFrame.targetText || "", 60),
        confidence: Number(session.visualFrame.confidence || 0),
      } : null,
    },
    deviceContext: deviceContext || {},
  };
}

function extractGuiProviderPayload(data) {
  if (!data || typeof data !== "object") return {};
  if (data.agentStep || data.agentState || data.visualFrame || data.s || data.a || data.x || data.y) return data;
  if (data.result && typeof data.result === "object") return data.result;
  if (data.plan && typeof data.plan === "object") return data.plan;
  if (data.data && typeof data.data === "object") return data.data;
  if (data.output && typeof data.output === "object") return data.output;
  if (data.action && typeof data.action === "object") return data.action;

  const content =
    data.choices?.[0]?.message?.content ||
    data.choices?.[0]?.text ||
    data.message?.content ||
    data.text ||
    data.reply ||
    data.content ||
    "";
  if (content) {
    try {
      return JSON.parse(extractJsonText(content));
    } catch (_) {
      return {};
    }
  }
  return {};
}


function normalizeGuiPlusActionName(value) {
  const raw = String(value || "").toLowerCase().trim().replace(/[-\s]+/g, "_");
  if (["tap", "click", "press", "point", "coordinate_click", "coordinate_tap", "tap_point", "tap_xy"].includes(raw)) return "tap_xy";
  if (["input", "type", "enter_text", "input_text"].includes(raw)) return "input_text";
  if (["swipe", "scroll", "back", "home", "wait", "finish", "done", "complete", "completed"].includes(raw)) return raw === "done" || raw === "complete" || raw === "completed" ? "finish" : raw;
  if (["none", "unknown", "uncertain", "ask_user", "need_help", "need_user_help"].includes(raw)) return "need_user_help";
  return raw || "need_user_help";
}

function extractGuiPlusJsonOrArray(text) {
  const raw = String(text || "").trim();
  if (!raw) return null;
  try { return JSON.parse(raw); } catch (_) {}
  const fenced = raw.match(/```(?:json)?\s*([\s\S]*?)```/i);
  if (fenced && fenced[1]) {
    const inner = fenced[1].trim();
    try { return JSON.parse(inner); } catch (_) {}
  }
  const objectMatch = raw.match(/\{[\s\S]*\}/);
  if (objectMatch) {
    try { return JSON.parse(objectMatch[0]); } catch (_) {}
  }
  const arrayMatch = raw.match(/\[\s*-?\d+(?:\.\d+)?\s*,\s*-?\d+(?:\.\d+)?\s*\]/);
  if (arrayMatch) {
    try { return JSON.parse(arrayMatch[0]); } catch (_) {}
  }
  return null;
}

function normalizeGuiPlusCoordinate(value, imageSize, displaySize) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return undefined;
  if (numeric >= 0 && numeric <= 1) return clamp01(numeric);
  if (numeric >= 0 && numeric <= 100) return clamp01(numeric / 100);
  if (imageSize > 1 && numeric >= 0 && numeric <= imageSize + 24) return clamp01(numeric / imageSize);
  if (displaySize > 1 && numeric >= 0 && numeric <= displaySize + 24) return clamp01(numeric / displaySize);
  return undefined;
}

function normalizeGuiPlusPoint(rawX, rawY, screenshotInfo) {
  const imageWidth = Number(screenshotInfo?.width) || 0;
  const imageHeight = Number(screenshotInfo?.height) || 0;
  const displayWidth = Number(screenshotInfo?.displayWidth) || imageWidth;
  const displayHeight = Number(screenshotInfo?.displayHeight) || imageHeight;
  const x = normalizeGuiPlusCoordinate(rawX, imageWidth, displayWidth);
  const y = normalizeGuiPlusCoordinate(rawY, imageHeight, displayHeight);
  if (!Number.isFinite(x) || !Number.isFinite(y)) return null;
  return { x, y };
}

function guiPlusNeedUserHelp(targetText, reason, raw = "") {
  return {
    s: "u",
    a: "need_user_help",
    x: null,
    y: null,
    t: safeText(targetText || "目标", 80),
    c: 0,
    e: safeText(reason || "无法可靠判断，需要用户帮助。", 220),
    raw: String(raw || "").slice(0, 1200),
  };
}

function normalizeGuiPlusRawToCompact(rawOutput, screenshotInfo) {
  const rawText = String(rawOutput || "");
  const parsed = extractGuiPlusJsonOrArray(rawText);
  let action = "";
  let xRaw;
  let yRaw;
  let targetText = "点击目标";
  let confidence = 0;
  let reason = "";

  if (Array.isArray(parsed) && parsed.length >= 2) {
    action = "tap_xy";
    xRaw = parsed[0];
    yRaw = parsed[1];
    confidence = 0.55;
    reason = "GUI Plus 返回坐标数组。";
  } else if (parsed && typeof parsed === "object") {
    const nested = parsed.agentStep || parsed.step || parsed.actionStep || parsed.result || parsed.action || parsed;
    const coordinate = nested.coordinate || nested.coordinates || nested.point || nested.position || nested.xy || nested.center || nested.coord;
    action = normalizeGuiPlusActionName(nested.action || nested.type || nested.a || nested.operation || parsed.action || parsed.type);
    if (Array.isArray(coordinate) && coordinate.length >= 2) {
      xRaw = coordinate[0];
      yRaw = coordinate[1];
    } else if (coordinate && typeof coordinate === "object") {
      xRaw = coordinate.x ?? coordinate[0] ?? coordinate.cx ?? coordinate.centerX;
      yRaw = coordinate.y ?? coordinate[1] ?? coordinate.cy ?? coordinate.centerY;
    }
    xRaw = xRaw ?? nested.x ?? nested.targetX ?? nested.cx ?? nested.centerX ?? nested.tapX ?? parsed.x ?? parsed.targetX;
    yRaw = yRaw ?? nested.y ?? nested.targetY ?? nested.cy ?? nested.centerY ?? nested.tapY ?? parsed.y ?? parsed.targetY;
    targetText = safeText(nested.targetText || nested.label || nested.t || nested.text || parsed.t || parsed.targetText || "点击目标", 80);
    confidence = clamp01(nested.confidence ?? nested.score ?? nested.c ?? parsed.confidence ?? parsed.c ?? 0);
    reason = safeText(nested.reason || nested.evidence || nested.e || parsed.reason || parsed.e || "GUI Plus predicted clickable coordinate.", 180);
  } else {
    const xyPairs = rawText.match(/[xy][:=：]\s*(-?\d+(?:\.\d+)?)/gi) || [];
    const numbers = rawText.match(/-?\d+(?:\.\d+)?/g)?.map(Number).filter(Number.isFinite) || [];
    if (xyPairs.length >= 2 || numbers.length >= 2) {
      action = "tap_xy";
      xRaw = numbers[0];
      yRaw = numbers[1];
      confidence = 0.35;
      reason = "从 GUI Plus 非 JSON 文本中提取到坐标，置信度较低。";
    }
  }

  if (!action) action = "need_user_help";
  if (action !== "tap_xy") {
    return guiPlusNeedUserHelp(targetText, `第一阶段不自动执行 ${action}，已保守暂停。${reason || ""}`, rawText);
  }

  const point = normalizeGuiPlusPoint(xRaw, yRaw, screenshotInfo);
  if (!point) return guiPlusNeedUserHelp(targetText, "GUI Plus 没有给出可靠坐标，禁止猜测点击。", rawText);

  const safeConfidence = confidence > 0 ? confidence : 0.6;
  if (safeConfidence < 0.25) {
    return guiPlusNeedUserHelp(targetText, `GUI Plus 坐标置信度过低：${safeConfidence.toFixed(2)}`, rawText);
  }

  return {
    s: "p",
    a: "tap_xy",
    x: point.x,
    y: point.y,
    t: targetText || "点击目标",
    c: safeConfidence,
    e: reason || "GUI Plus predicted clickable coordinate.",
    raw: rawText.slice(0, 1200),
  };
}

function compactNodesForGuiPlusPrompt(snapshot) {
  const nodes = [
    ...(Array.isArray(snapshot?.clickableNodes) ? snapshot.clickableNodes : []),
    ...(Array.isArray(snapshot?.inputNodes) ? snapshot.inputNodes : []),
    ...(Array.isArray(snapshot?.scrollableNodes) ? snapshot.scrollableNodes : []),
  ];
  return nodes
    .map((node) => ({
      id: safeText(node?.id || "", 24),
      text: safeText(node?.text || node?.label || node?.contentDescription || "", 60),
      bounds: safeText(node?.bounds || "", 48),
      clickable: Boolean(node?.clickable),
      editable: Boolean(node?.editable),
      scrollable: Boolean(node?.scrollable),
    }))
    .filter((node) => node.text || node.bounds)
    .slice(0, 28);
}

function buildAliyunMobileUseToolProtocolPrompt() {
  return [
    "# Tools",
    "You may call one or more functions to assist with the user query.",
    "",
    "You are provided with function signatures within <tools></tools> XML tags:",
    "<tools>",
    "{\"type\":\"function\",\"function\":{\"name_for_human\":\"mobile_use\",\"name\":\"mobile_use\",\"description\":\"Use a touchscreen to interact with a mobile device, and take screenshots.\\n* This is an interface to a mobile device with touchscreen. You can perform actions like clicking, typing, swiping, etc.\\n* Some applications may take time to start or process actions, so you may need to wait and take successive screenshots to see the results of your actions.\\n* The screen's resolution is 1000x1000.\\n* Make sure to click any buttons, links, icons, etc with the cursor tip in the center of the element. Don't click boxes on their edges unless asked.\",\"parameters\":{\"properties\":{\"action\":{\"description\":\"The action to perform. The available actions are: key, click, long_press, swipe, type, system_button, open, wait, answer, interact, terminate.\",\"enum\":[\"key\",\"click\",\"long_press\",\"swipe\",\"type\",\"system_button\",\"open\",\"wait\",\"answer\",\"interact\",\"terminate\"],\"type\":\"string\"},\"coordinate\":{\"description\":\"(x, y): coordinates in the 1000x1000 mobile_use screen coordinate system. Required only by action=click, action=long_press, and action=swipe.\",\"type\":\"array\"},\"coordinate2\":{\"description\":\"(x, y): end coordinates for action=swipe in the 1000x1000 mobile_use coordinate system.\",\"type\":\"array\"},\"text\":{\"description\":\"Required only by action=key, type, open, answer, and interact.\",\"type\":\"string\"},\"time\":{\"description\":\"Seconds to wait or long-press.\",\"type\":\"number\"},\"button\":{\"description\":\"System button.\",\"enum\":[\"Back\",\"Home\",\"Menu\",\"Enter\"],\"type\":\"string\"},\"status\":{\"description\":\"Task status for terminate.\",\"type\":\"string\",\"enum\":[\"success\",\"failure\"]}},\"required\":[\"action\"],\"type\":\"object\"},\"args_format\":\"Format the arguments as a JSON object.\"}}",
    "</tools>",
    "",
    "For each function call, return a json object with function name and arguments within <tool_call></tool_call> XML tags:",
    "<tool_call>",
    "{\"name\": <function-name>, \"arguments\": <args-json-object>}",
    "</tool_call>",
    "",
    "# Response format",
    "Response format for every step:",
    "1) Action: a short imperative describing what to do in the UI.",
    "2) A single <tool_call>...</tool_call> block containing only the JSON: {\"name\": <function-name>, \"arguments\": <args-json-object>}",
    "",
    "Rules:",
    "- Output exactly in the order: Action, <tool_call>.",
    "- Be brief: one for Action.",
    "- Do not output anything else outside those two parts.",
    "- The mobile_use screen resolution is 1000x1000, regardless of the source screenshot resolution.",
    "- If the requested low-risk target is clearly visible, call mobile_use with action=click and coordinate at the target center.",
    "- Use action=wait only when the visible app page is genuinely loading or transitioning.",
    "- Use action=terminate only when the task is truly complete or impossible from the current screen.",
  ].join("\n");
}

function buildAliyunGuiPlusMessages(goal, snapshot, screenshotInfo, recentActions = [], supportedSteps = SUPPORTED_AGENT_STEP_TYPES, deviceContext = null, agentMemory = null, session = null) {
  const history = Array.isArray(session?.guiHistory) ? session.guiHistory.slice(-AGENT_GUI_HISTORY_N) : [];
  const previousActions = history
    .map((item, index) => `Step ${index + 1}: ${extractMobileUseActionSummary(item.output)}`)
    .filter(Boolean)
    .join("\n") || "None";

  const instructionPrompt = [
    "Please generate the next move according to the UI screenshot, instruction and previous actions.",
    "",
    `Instruction: ${aliyunGuiDateInfo()}${safeText(goal, 240)}`,
    "",
    "Previous actions:",
    previousActions,
  ].join("\n");

  const messages = [
    {
      role: "system",
      content: buildAliyunMobileUseToolProtocolPrompt(),
    },
  ];

  if (history.length > 0) {
    history.forEach((item, historyIndex) => {
      const content = [];
      if (historyIndex === 0) content.push({ type: "text", text: instructionPrompt });
      content.push({
        type: "image_url",
        image_url: {
          url: `data:${item.image?.mimeType || "image/jpeg"};base64,${item.image?.base64 || ""}`,
        },
      });
      messages.push({ role: "user", content });
      messages.push({ role: "assistant", content: String(item.output || "") });
    });
    messages.push({
      role: "user",
      content: [
        {
          type: "image_url",
          image_url: { url: `data:${screenshotInfo.mimeType};base64,${screenshotInfo.base64}` },
        },
      ],
    });
  } else {
    messages.push({
      role: "user",
      content: [
        { type: "text", text: instructionPrompt },
        {
          type: "image_url",
          image_url: { url: `data:${screenshotInfo.mimeType};base64,${screenshotInfo.base64}` },
        },
      ],
    });
  }

  return messages;
}

function extractAliyunMobileUseToolCall(rawOutput) {
  const raw = String(rawOutput || "").trim();
  if (!raw) return null;
  const candidates = [];
  const xmlRegex = /<tool_call>\s*([\s\S]*?)\s*<\/tool_call>/gi;
  let match;
  while ((match = xmlRegex.exec(raw)) !== null) {
    if (match[1]) candidates.push(match[1].trim());
  }
  const fenced = raw.match(/```(?:json)?\s*([\s\S]*?)```/i);
  if (fenced && fenced[1]) candidates.push(fenced[1].trim());
  const objectMatch = raw.match(/\{[\s\S]*\}/);
  if (objectMatch) candidates.push(objectMatch[0]);

  for (const candidate of candidates) {
    try {
      const parsed = JSON.parse(candidate);
      const normalized = normalizeAliyunMobileUseToolCallObject(parsed);
      if (normalized) return normalized;
    } catch (_) {}
  }
  return null;
}

function normalizeAliyunMobileUseToolCallObject(value) {
  if (!value || typeof value !== "object") return null;
  if (Array.isArray(value.tool_calls) && value.tool_calls.length) {
    for (const item of value.tool_calls) {
      const normalized = normalizeAliyunMobileUseToolCallObject(item);
      if (normalized) return normalized;
    }
  }
  if (value.tool_call && typeof value.tool_call === "object") {
    const normalized = normalizeAliyunMobileUseToolCallObject(value.tool_call);
    if (normalized) return normalized;
  }
  if (value.function && typeof value.function === "object") {
    const name = safeText(value.function.name || value.name || "", 64);
    let args = value.function.arguments;
    if (typeof args === "string") {
      try { args = JSON.parse(args); } catch (_) { args = {}; }
    }
    if (name === "mobile_use" && args && typeof args === "object") return args;
  }
  const name = safeText(value.name || value.functionName || value.toolName || "", 64);
  const args = value.arguments || value.args || value.parameters || value.input;
  if (name === "mobile_use" && args && typeof args === "object") return args;
  if (value.action && typeof value.action === "string") return value;
  return null;
}

function normalizeMobileUseCoordinatePair(coordinate, screenshotInfo) {
  if (!Array.isArray(coordinate) || coordinate.length < 2) return null;
  const rawX = Number(coordinate[0]);
  const rawY = Number(coordinate[1]);
  if (!Number.isFinite(rawX) || !Number.isFinite(rawY)) return null;
  if (rawX >= 0 && rawX <= 1 && rawY >= 0 && rawY <= 1) return { x: clamp01(rawX), y: clamp01(rawY), source: "normalized" };
  if (rawX >= 0 && rawX <= 1000 && rawY >= 0 && rawY <= 1000) return { x: clamp01(rawX / 1000), y: clamp01(rawY / 1000), source: "mobile_use_1000" };
  const fallback = normalizeGuiPlusPoint(rawX, rawY, screenshotInfo);
  return fallback ? { ...fallback, source: "screenshot_or_display_pixel" } : null;
}

function mobileUseSwipeDirection(args) {
  const a = Array.isArray(args?.coordinate) ? args.coordinate : [];
  const b = Array.isArray(args?.coordinate2) ? args.coordinate2 : [];
  if (a.length < 2 || b.length < 2) return normalizeAgentDirection(args?.direction || "") || "up";
  const dx = Number(b[0]) - Number(a[0]);
  const dy = Number(b[1]) - Number(a[1]);
  if (!Number.isFinite(dx) || !Number.isFinite(dy)) return "up";
  if (Math.abs(dx) > Math.abs(dy)) return dx > 0 ? "right" : "left";
  return dy > 0 ? "down" : "up";
}

function normalizeAliyunMobileUseRawToCompact(rawOutput, screenshotInfo, goal = "") {
  const rawText = String(rawOutput || "");
  const args = extractAliyunMobileUseToolCall(rawText);
  if (!args) return normalizeGuiPlusRawToCompact(rawText, screenshotInfo);

  const action = String(args.action || "").toLowerCase().trim();
  const actionLine = safeText((rawText.match(/^Action:\s*(.*)$/im) || [])[1] || "", 120);
  const text = safeText(args.text || args.button || args.status || actionLine || goal || "目标", 120);
  const confidence = 0.82;

  if (action === "click") {
    const point = normalizeMobileUseCoordinatePair(args.coordinate, screenshotInfo);
    if (!point) return guiPlusNeedUserHelp(text, "GUI Plus mobile_use click 缺少可靠坐标。", rawText);
    return { s: "p", a: "tap_xy", x: point.x, y: point.y, t: text || "点击目标", c: confidence, e: `GUI Plus mobile_use click (${point.source}).`, raw: rawText.slice(0, 1200) };
  }

  if (action === "wait") {
    const seconds = Number(args.time);
    const ms = Number.isFinite(seconds) ? Math.max(300, Math.min(2000, Math.round(seconds * 1000))) : 700;
    return { s: "p", a: "wait", ms, t: text || "等待", c: 0.68, e: "GUI Plus mobile_use wait：页面加载/过渡/处理等待。", raw: rawText.slice(0, 1200) };
  }

  if (action === "system_button") {
    const button = String(args.button || args.text || "").toLowerCase();
    if (button === "back") return { s: "p", a: "back", t: "返回", c: 0.76, e: "GUI Plus mobile_use system_button Back.", raw: rawText.slice(0, 1200) };
    if (button === "home") return { s: "p", a: "home", t: "主页", c: 0.76, e: "GUI Plus mobile_use system_button Home.", raw: rawText.slice(0, 1200) };
    if (button === "menu") return { s: "p", a: "recents", t: "多任务", c: 0.7, e: "GUI Plus mobile_use system_button Menu.", raw: rawText.slice(0, 1200) };
    return guiPlusNeedUserHelp(text, `第一阶段不自动执行 system_button=${safeText(args.button || args.text || "", 24)}。`, rawText);
  }

  if (action === "swipe") {
    const p1 = normalizeMobileUseCoordinatePair(args.coordinate, screenshotInfo);
    const p2 = normalizeMobileUseCoordinatePair(args.coordinate2, screenshotInfo);
    return {
      s: "p",
      a: "swipe",
      d: mobileUseSwipeDirection(args),
      x: p1?.x,
      y: p1?.y,
      x2: p2?.x,
      y2: p2?.y,
      t: text || "滑动",
      c: 0.72,
      e: "GUI Plus mobile_use swipe.",
      raw: rawText.slice(0, 1200),
    };
  }

  if (action === "type") {
    return { s: "p", a: "input_text", v: safeText(args.text || "", 180), t: "输入文字", c: 0.72, e: "GUI Plus mobile_use type.", raw: rawText.slice(0, 1200) };
  }

  if (action === "key") {
    const key = String(args.text || "").toLowerCase();
    if (key.includes("back")) return { s: "p", a: "back", t: "返回", c: 0.72, e: "GUI Plus mobile_use key back.", raw: rawText.slice(0, 1200) };
    if (key.includes("home")) return { s: "p", a: "home", t: "主页", c: 0.72, e: "GUI Plus mobile_use key home.", raw: rawText.slice(0, 1200) };
    return guiPlusNeedUserHelp(text, `第一阶段不自动执行 key=${safeText(args.text || "", 32)}。`, rawText);
  }

  if (action === "terminate") {
    const status = String(args.status || "").toLowerCase();
    if (status === "success") return { s: "d", a: "finish", t: text || "完成", c: 0.82, e: safeText(args.text || actionLine || "GUI Plus mobile_use terminate success.", 160), raw: rawText.slice(0, 1200) };
    return guiPlusNeedUserHelp(text || goal, safeText(args.text || actionLine || "GUI Plus mobile_use terminate failure.", 220), rawText);
  }

  if (action === "answer" || action === "interact") {
    return guiPlusNeedUserHelp(text || goal, safeText(args.text || actionLine || `GUI Plus mobile_use ${action}.`, 220), rawText);
  }

  if (action === "open") {
    return {
      s: "p",
      a: "open_app",
      appName: text || goal,
      t: text || goal || "打开应用",
      c: 0.72,
      e: "GUI Plus mobile_use open.",
      raw: rawText.slice(0, 1200),
    };
  }

  if (action === "long_press") {
    return guiPlusNeedUserHelp(text || goal, "第一阶段不自动执行 mobile_use long_press，已保守暂停。", rawText);
  }

  return guiPlusNeedUserHelp(text || goal, `未知 mobile_use action=${safeText(action, 32)}。`, rawText);
}

function logGuiProviderCall(provider, model, screenshotInfo, elapsedMs, compact, extra = "") {
  const x = Number.isFinite(compact?.x) ? Number(compact.x).toFixed(4) : "null";
  const y = Number.isFinite(compact?.y) ? Number(compact.y).toFixed(4) : "null";
  console.log(`[agent-gui] provider=${provider} model=${model} image=${screenshotInfo?.width || 0}x${screenshotInfo?.height || 0} elapsedMs=${elapsedMs} action=${compact?.a || "unknown"} x=${x} y=${y}${extra ? ` ${extra}` : ""}`);
}

async function callAliyunGuiPlusProvider(goal, snapshot, screenshotInfo, session, recentActions, supportedSteps, deviceContext, agentMemory, providerConfig, timeoutMs) {
  if (!ALIYUN_GUI_API_KEY) throw new Error("Aliyun GUI Plus key missing: set ALIYUN_GUI_API_KEY or QWEN_API_KEY");
  if (!ALIYUN_GUI_BASE_URL) throw new Error("Aliyun GUI Plus base url missing: set ALIYUN_GUI_BASE_URL");
  if (!ALIYUN_GUI_MODEL) throw new Error("Aliyun GUI Plus model missing: set ALIYUN_GUI_MODEL");
  if (!screenshotInfo?.hasImage) throw new Error("Aliyun GUI Plus requires screenshot");

  // 阿里云百炼 GUI Plus 当前按 OpenAI-compatible /chat/completions 调用：
  // POST {ALIYUN_GUI_BASE_URL}/chat/completions
  // model=gui-plus-2026-02-26
  // messages=[{role:"user", content:[{type:"text"}, {type:"image_url"}]}]
  // 如果后续官方文档要求 DashScope 原生专用接口，只替换本函数即可；下游仍保持 compact action JSON。
  const startedAt = Date.now();
  const raw = await callOpenAICompatible(
    ALIYUN_GUI_BASE_URL,
    ALIYUN_GUI_API_KEY,
    ALIYUN_GUI_MODEL,
    buildAliyunGuiPlusMessages(goal, snapshot, screenshotInfo, recentActions, supportedSteps, deviceContext, agentMemory, session),
    "Aliyun GUI Plus",
    {
      temperature: 0,
      max_tokens: Math.min(ALIYUN_GUI_MAX_TOKENS, AGENT_VISION_MAX_TOKENS, 512),
      headers: {
        "x-dashscope-gui-session-id": session?.guiSessionId || newAgentGuiSessionId(),
      },
      extraBody: {
        vl_high_resolution_images: ALIYUN_GUI_HIGH_RESOLUTION_IMAGES,
        enable_thinking: ALIYUN_GUI_ENABLE_THINKING,
      },
      timeoutMs: Math.max(
        300,
        Math.min(
          Math.max(Number(timeoutMs || 0), Number(ALIYUN_GUI_TIMEOUT_MS || 15000)),
          Math.max(300, Number(AGENT_STEP_TOTAL_BUDGET_MS || 18000) - AGENT_RESPONSE_SAFETY_MARGIN_MS)
        )
      ),
    }
  );
  const compact = normalizeAliyunMobileUseRawToCompact(raw, screenshotInfo, goal);
  rememberAgentGuiTurn(session, screenshotInfo, raw, compact);
  logGuiProviderCall("aliyun_gui_plus", ALIYUN_GUI_MODEL, screenshotInfo, Date.now() - startedAt, compact, `rawLen=${raw.length} history=${session?.guiHistory?.length || 0}`);
  return { ...adaptCompactVisionPlan(compact), guiPlusRawOutput: raw, guiPlusCompact: compact };
}

async function callExternalHttpGuiProvider(goal, snapshot, screenshotInfo, session, recentActions, supportedSteps, deviceContext, agentMemory, providerConfig, timeoutMs) {
  const payload = buildExternalGuiProviderPayload(goal, snapshot, screenshotInfo, session, recentActions, supportedSteps, deviceContext, agentMemory, providerConfig);
  const headers = {
    "content-type": "application/json",
    "x-ai-ledger-provider": providerConfig.provider,
  };
  if (AGENT_GUI_PROVIDER_API_KEY) {
    headers.authorization = `Bearer ${AGENT_GUI_PROVIDER_API_KEY}`;
  }
  const r = await fetchWithTimeout(AGENT_GUI_PROVIDER_URL, {
    method: "POST",
    headers,
    body: JSON.stringify(payload),
  }, Math.max(300, Number(timeoutMs || AGENT_GUI_PROVIDER_TIMEOUT_MS || AGENT_STEP_VISION_TIMEOUT_MS)));
  const t = await r.text();
  if (!r.ok) {
    throw new Error(`GUI provider ${providerConfig.provider} ${r.status} ${t.slice(0, 260)}`);
  }
  let data;
  try {
    data = JSON.parse(t);
  } catch (_) {
    data = { text: t };
  }
  const parsed = extractGuiProviderPayload(data);
  return adaptCompactVisionPlan(parsed);
}

async function callOpenAICompatibleGuiProvider(goal, snapshot, screenshotInfo, session, recentActions, supportedSteps, deviceContext, agentMemory, providerConfig, timeoutMs) {
  const messages = buildAgentVisionPlannerMessages(goal, snapshot, screenshotInfo, session, recentActions, supportedSteps, deviceContext, agentMemory);
  const raw = await callOpenAICompatible(
    AGENT_GUI_PROVIDER_BASE_URL,
    AGENT_GUI_PROVIDER_API_KEY || process.env.QWEN_API_KEY,
    providerConfig.providerModel,
    messages,
    `${providerConfig.provider} GUI Provider`,
    {
      temperature: 0,
      max_tokens: Math.min(AGENT_GUI_PROVIDER_MAX_TOKENS, AGENT_VISION_MAX_TOKENS, 260),
      timeoutMs: Math.max(300, Number(timeoutMs || AGENT_GUI_PROVIDER_TIMEOUT_MS || AGENT_STEP_VISION_TIMEOUT_MS)),
    }
  );
  let parsed;
  try { parsed = JSON.parse(extractJsonText(raw)); } catch (_) { parsed = {}; }
  return adaptCompactVisionPlan(parsed);
}

async function callAgentGuiProvider(goal, snapshot, screenshotInfo, session, recentAgentActions, supportedSteps, deviceContext, agentMemory, providerConfig, qwenProviderModel, timeoutMs) {
  if (!providerConfig || providerConfig.provider === "qwen_omni") {
    return callAgentVisionPlanner(goal, snapshot, screenshotInfo, session, recentAgentActions, supportedSteps, deviceContext, agentMemory, qwenProviderModel, timeoutMs);
  }
  if (providerConfig.mode === "aliyun_openai_compatible") {
    try {
      return await callAliyunGuiPlusProvider(goal, snapshot, screenshotInfo, session, recentAgentActions, supportedSteps, deviceContext, agentMemory, providerConfig, timeoutMs);
    } catch (error) {
      const message = sanitizeProviderError(error, 180);
      console.warn(`[agent-gui] provider=aliyun_gui_plus model=${ALIYUN_GUI_MODEL} error=${message}`);
      if (providerConfig.runtimeFallbackToQwen || AGENT_GUI_PROVIDER_FALLBACK_TO_QWEN) {
        console.warn("[agent-gui] provider=aliyun_gui_plus fallback=qwen_omni");
        return callAgentVisionPlanner(goal, snapshot, screenshotInfo, session, recentAgentActions, supportedSteps, deviceContext, agentMemory, qwenProviderModel, timeoutMs);
      }
      throw error;
    }
  }
  if (providerConfig.mode === "openai_compatible") {
    return callOpenAICompatibleGuiProvider(goal, snapshot, screenshotInfo, session, recentAgentActions, supportedSteps, deviceContext, agentMemory, providerConfig, timeoutMs);
  }
  if (providerConfig.mode === "external_http_json") {
    return callExternalHttpGuiProvider(goal, snapshot, screenshotInfo, session, recentAgentActions, supportedSteps, deviceContext, agentMemory, providerConfig, timeoutMs);
  }
  throw new Error(providerConfig.fallbackReason || `GUI provider ${providerConfig.provider} unavailable`);
}


function parsedVisionPlanHasUsableStep(parsed) {
  if (!parsed || typeof parsed !== "object") return false;
  const rawStep = parsed.agentStep || parsed.step || parsed.actionStep || agentBatchCandidatesFromParsed(parsed)[0] || parsed.result || {};
  const type = normalizeAgentStepType(rawStep.type || rawStep.action);
  if (!SUPPORTED_AGENT_STEP_TYPES.includes(type)) return false;
  if (type === "need_user_help") return false;
  return true;
}

function cachedFrameNeedsTextPlanner(visualFrame, recentActions) {
  if (!visualFrame || typeof visualFrame !== "object") return true;
  if (visualFrame.isComplete || visualFrame.isWrong) return false;
  const suggested = visualFrame.suggestedAction || {};
  const actionType = normalizeAgentStepType(suggested.type || "");
  const latestAction = latestActionKeyFromRecent(recentActions);
  if (isLatestActionLikelyConsumedVisualSuggestion(visualFrame, latestAction)) return true;
  if (["tap_xy", "scroll", "swipe", "back", "wait"].includes(actionType)) return false;
  return true;
}

function findGoalMatchedClickableNode(goal, snapshot) {
  const core = goalCoreForCompletion(goal, {});
  const normalizedGoal = normalizeForMatch(goal);
  const nodes = Array.isArray(snapshot?.clickableNodes) ? snapshot.clickableNodes : [];
  let best = null;
  let bestScore = 0;
  for (const node of nodes) {
    const text = safeText(node?.text || "", 80);
    const normalizedText = normalizeForMatch(text);
    if (!normalizedText || normalizedText.length < 2) continue;
    let score = 0;
    if (core && normalizedText === core) score = 1000;
    else if (core && (normalizedText.includes(core) || core.includes(normalizedText))) score = 880 + Math.min(core.length, normalizedText.length);
    else if (normalizedGoal && normalizedGoal.includes(normalizedText)) score = 650 + normalizedText.length;
    if (score > bestScore) {
      bestScore = score;
      best = node;
    }
  }
  return bestScore >= 650 ? best : null;
}

function buildLocalAgentFallbackPlan(goal, snapshot, supportedSteps, visualFrame, screenshotInfo, deviceContext, recentAgentActions, reasonTag = "local_fallback") {
  const direct = visualFrameToDirectAgentPlan(visualFrame, snapshot, supportedSteps, goal, screenshotInfo, deviceContext, recentAgentActions, true);
  if (direct) {
    return {
      agentStep: direct.agentStep,
      agentState: direct.agentState,
      parsed: null,
      source: `${direct.source || "visual_frame"}_${reasonTag}`,
      reason: direct.agentStep?.reason || direct.agentState?.reason || "",
    };
  }

  const completionEvidence = goalAppearsCompleteFromEvidence(goal, snapshot, visualFrame, deviceContext);
  if (completionEvidence) {
    const step = normalizeAgentStep({ agentStep: { type: "finish", reason: completionEvidence.reason, riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    const state = normalizeAgentState({ agentState: { isComplete: true, expectedProgress: true, isWrong: false, confidence: completionEvidence.confidence, reason: completionEvidence.reason } }, step);
    return { agentStep: step, agentState: state, parsed: null, source: `completion_evidence_${reasonTag}`, reason: completionEvidence.reason };
  }

  const currentPackage = safeText(snapshot?.packageName || snapshot?.currentApp || "", 120);
  const targetApps = targetAppCandidatesFromDeviceContext(deviceContext || {});
  const firstTargetApp = targetApps[0];
  if (firstTargetApp && supportedSteps.includes("open_app") && firstTargetApp.packageName && firstTargetApp.packageName !== currentPackage) {
    const step = normalizeAgentStep({
      agentStep: {
        type: "open_app",
        appName: firstTargetApp.label,
        packageName: firstTargetApp.packageName,
        reason: `视觉云端超时，先用 deviceContext 打开目标应用：${firstTargetApp.label}。`,
        riskLevel: "low",
        requiresConfirmation: false,
      },
    }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    const state = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: true, isWrong: false, confidence: 0.62, reason: "本地设备上下文命中目标应用，先打开目标 App。" } }, step);
    return { agentStep: step, agentState: state, parsed: null, source: `open_app_${reasonTag}`, reason: step.reason };
  }

  const highRiskRequested = HIGH_RISK_AGENT_WORDS.some((word) => normalizeForMatch(goal).includes(normalizeForMatch(word)));
  const matchedNode = highRiskRequested ? null : findGoalMatchedClickableNode(goal, snapshot);
  if (matchedNode && supportedSteps.includes("tap_node")) {
    const step = normalizeAgentStep({
      agentStep: {
        type: "tap_node",
        targetNodeId: matchedNode.id,
        targetText: matchedNode.text,
        reason: `视觉云端超时，节点中已出现与目标匹配的低风险入口：${matchedNode.text}。`,
        riskLevel: "low",
        requiresConfirmation: false,
      },
    }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    const state = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: true, isWrong: false, confidence: 0.58, reason: "使用节点 affordance 进行低风险兜底点击。" } }, step);
    return { agentStep: step, agentState: state, parsed: null, source: `tap_node_${reasonTag}`, reason: step.reason };
  }

  if ((snapshot?.scrollableNodes || []).length > 0 && supportedSteps.includes("scroll") && countRecentActionKind(recentAgentActions, "scroll") === 0 && countRecentActionKind(recentAgentActions, "swipe") === 0) {
    const step = normalizeAgentStep({
      agentStep: {
        type: "scroll",
        direction: "down",
        reason: "视觉云端超时，当前页面可滚动，先执行一次低风险滚动探索。",
        riskLevel: "low",
        requiresConfirmation: false,
      },
    }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    const state = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: true, isWrong: false, confidence: 0.46, reason: "低风险滚动探索后继续视觉复核。" } }, step);
    return { agentStep: step, agentState: state, parsed: null, source: `scroll_${reasonTag}`, reason: step.reason };
  }

  if (screenshotInfo?.hasImage && countRecentActionKind(recentAgentActions, "wait") === 0 && isLikelyLoadingOrTransition(snapshot, visualFrame)) {
    const step = normalizeAgentStep({
      agentStep: {
        type: "wait",
        durationMs: 500,
        reason: "视觉云端超时且页面可能仍在加载，短暂等待后重新观察。",
        riskLevel: "low",
        requiresConfirmation: false,
      },
    }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    const state = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: true, isWrong: false, confidence: 0.42, reason: "页面可能处于加载或过渡状态。" } }, step);
    return { agentStep: step, agentState: state, parsed: null, source: `wait_${reasonTag}`, reason: step.reason };
  }

  const reason = "视觉云端超时且本地没有足够可靠的低风险兜底动作，已暂停避免盲目操作。";
  const step = normalizeAgentStep({ agentStep: { type: "need_user_help", reason, riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
  const state = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: false, isWrong: false, confidence: 0.28, reason } }, step);
  return { agentStep: step, agentState: state, parsed: null, source: `need_help_${reasonTag}`, reason };
}

async function tryAgentTextPlannerFallback(context) {
  const {
    startedAt,
    goal,
    snapshot,
    supportedSteps,
    visualFrame,
    session,
    recentAgentActions,
    deviceContext,
    agentMemory,
    screenshotInfo,
    reasonTag,
  } = context;
  if (isAgentBudgetNearlyExhausted(startedAt)) {
    return buildLocalAgentFallbackPlan(goal, snapshot, supportedSteps, visualFrame, screenshotInfo, deviceContext, recentAgentActions, `${reasonTag}_budget_exhausted`);
  }

  const timeoutMs = boundedAgentTimeoutMs(AGENT_STEP_TEXT_PLANNER_TIMEOUT_MS, agentRemainingBudgetMs(startedAt), 1200);
  const plannerStartedAt = Date.now();
  try {
    const parsed = await callAgentTextPlanner(goal, snapshot, supportedSteps, visualFrame, session, recentAgentActions, deviceContext, agentMemory, timeoutMs);
    const textPlannerMs = Date.now() - plannerStartedAt;
    let agentStep = normalizeAgentStep(parsed, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    let agentState = normalizeAgentState(parsed, agentStep);

    const completionEvidence = goalAppearsCompleteFromEvidence(goal, snapshot, visualFrame, deviceContext);
    if (completionEvidence && ["wait", "scroll", "swipe", "need_user_help"].includes(agentStep.type)) {
      agentStep = normalizeAgentStep({ agentStep: { type: "finish", reason: completionEvidence.reason, riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
      agentState = normalizeAgentState({ agentState: { isComplete: true, expectedProgress: true, isWrong: false, confidence: completionEvidence.confidence, reason: completionEvidence.reason } }, agentStep);
    } else if (!officialGuiPlusLoop && agentStep.type === "wait" && countRecentActionKind(recentAgentActions, "wait") >= 1 && !isLikelyLoadingOrTransition(snapshot, visualFrame)) {
      const local = buildLocalAgentFallbackPlan(goal, snapshot, supportedSteps, visualFrame, screenshotInfo, deviceContext, recentAgentActions, `${reasonTag}_text_wait_guard`);
      return { ...local, textPlannerMs, textPlannerError: "" };
    }

    if (agentStep.type === "need_user_help") {
      const local = buildLocalAgentFallbackPlan(goal, snapshot, supportedSteps, visualFrame, screenshotInfo, deviceContext, recentAgentActions, `${reasonTag}_text_no_action`);
      if (local.agentStep?.type !== "need_user_help") return { ...local, textPlannerMs, textPlannerError: "" };
    }

    return {
      parsed,
      agentStep,
      agentState,
      textPlannerMs,
      textPlannerError: "",
      source: `text_fallback_${reasonTag}`,
      reason: agentStep.reason || agentState.reason || "",
    };
  } catch (error) {
    const local = buildLocalAgentFallbackPlan(goal, snapshot, supportedSteps, visualFrame, screenshotInfo, deviceContext, recentAgentActions, `${reasonTag}_text_error`);
    return {
      ...local,
      textPlannerMs: Date.now() - plannerStartedAt,
      textPlannerError: sanitizeProviderError(error, 140),
    };
  }
}

async function handleAgentStepRequest(body, prompt, resolvedModel) {
  const startedAt = Date.now();
  const goal = safeText(body.agentGoal || body.goal || prompt, 240);
  const snapshot = compactScreenSnapshot(body.screenSnapshot || {});
  const supportedSteps = supportedAgentStepsFromBody(body);
  const screenshotInfo = normalizeAgentScreenshot(body);
  const deviceContext = body.deviceContext && typeof body.deviceContext === "object" ? body.deviceContext : {};
  const agentMemory = body.agentMemory && typeof body.agentMemory === "object" ? body.agentMemory : {};
  const recentAgentActions = Array.isArray(body.recentAgentActions) ? body.recentAgentActions.slice(-8) : [];
  const requestBytes = Number(body.__debugRequestBytes || 0) || 0;
  const readBodyMs = Number(body.__debugReadBodyMs || 0) || 0;
  const session = getAgentSession(body, goal);
  session.step += 1;

  const qwenProviderModel = String(process.env.QWEN_VISION_MODEL || "qwen-vl-plus").trim();
  const guiProviderConfig = resolveAgentGuiProviderConfig(screenshotInfo, qwenProviderModel);
  const baseMeta = {
    source: "agent_step_single_model_loop",
    sourceDetail: screenshotInfo.hasImage ? "gui_provider_planner_or_cache" : "text_planner_only",
    model: screenshotInfo.hasImage ? guiProviderConfig.provider : "qwen",
    modelId: screenshotInfo.hasImage ? guiProviderConfig.provider : "qwen",
    modelLabel: screenshotInfo.hasImage ? guiProviderConfig.modelLabel : "Qwen 文本规划",
    providerModel: screenshotInfo.hasImage ? guiProviderConfig.providerModel : process.env.QWEN_MODEL,
    guiProvider: guiProviderConfig.provider,
    requestedGuiProvider: guiProviderConfig.requestedProvider,
    guiProviderMode: guiProviderConfig.mode,
    guiProviderFallbackToQwen: Boolean(guiProviderConfig.fallbackToQwen),
    guiProviderFallbackReason: guiProviderConfig.fallbackReason || "",
    version: WORKER_VERSION,
  };

  if (!goal) {
    return { ok: false, error: "empty_agent_goal", code: "empty_agent_goal", version: WORKER_VERSION };
  }

  if (!snapshot.packageName && !snapshot.currentApp && snapshot.nodeCount === 0 && !screenshotInfo.hasImage) {
    return {
      ok: true,
      reply: "当前没有可用屏幕快照。",
      agentStep: { type: "need_user_help", reason: "Android 端没有提供可用 screenSnapshot 或 screenshot，无法规划下一步。", riskLevel: "low", requiresConfirmation: false },
      ...baseMeta,
    };
  }

  const fingerprint = screenFingerprint(snapshot, screenshotInfo);
  const screenKey = stableScreenKey(snapshot, screenshotInfo);
  const lastActionKey = latestActionKeyFromRecent(recentAgentActions);
  const androidRequestedVisual = Boolean(agentMemory?.loopSignals?.forceNextVisual);
  const hardForceVisual = Boolean(body.forceVisual === true || body.mustObserveVisual === true);
  const cachedFrameUseful = isVisualFrameCacheable(session.visualFrame);
  const stableScreenMatched = Boolean(session.lastStableScreenKey && session.lastStableScreenKey === screenKey);
  const legacyFingerprintMatched = Boolean(!session.lastStableScreenKey && session.lastFingerprint && session.lastFingerprint === fingerprint);
  const hasCachedFrame = Boolean(session.visualFrame && cachedFrameUseful && (stableScreenMatched || legacyFingerprintMatched));
  const shouldCallVisual = Boolean(screenshotInfo.hasImage && (hardForceVisual || androidRequestedVisual || !hasCachedFrame));

  let visualFrame = hasCachedFrame ? session.visualFrame : null;
  let visualCalled = false;
  let visualCacheHit = Boolean(visualFrame && !shouldCallVisual);
  let providerMs = 0;
  let textPlannerMs = 0;
  let visualError = "";
  let parsed = null;
  let agentStep = null;
  let agentState = null;
  let planSource = "";
  let routePlan = null;
  let routePlannerMs = 0;
  let routePlannerError = "";
  let routeGuarded = false;
  let routeGuardReason = "";
  let groundingGoal = goal;

  if (!officialGuiPlusLoop) {
    const routeStartedAt = Date.now();
    try {
      const routeTimeoutMs = boundedAgentTimeoutMs(AGENT_ROUTE_PLANNER_TIMEOUT_MS, agentRemainingBudgetMs(startedAt), AGENT_ROUTE_PLANNER_TIMEOUT_MS);
      routePlan = await callAgentRoutePlanner(goal, snapshot, visualFrame, recentAgentActions, deviceContext, agentMemory, routeTimeoutMs);
      routePlannerMs = Date.now() - routeStartedAt;
      groundingGoal = routePlan?.groundingGoal || routePlan?.subgoal || goal;
    } catch (error) {
      routePlannerMs = Date.now() - routeStartedAt;
      routePlannerError = sanitizeProviderError(error, 120);
      routePlan = buildLocalRoutePlan(goal, snapshot, visualFrame, recentAgentActions, deviceContext);
      groundingGoal = routePlan?.groundingGoal || routePlan?.subgoal || goal;
    }
  } else {
    routePlan = null;
    routePlannerMs = 0;
    routePlannerError = "";
    groundingGoal = goal;
  }

  const directRoutePlan = routePlanToDirectAgentPlan(routePlan, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
  if (directRoutePlan) {
    agentStep = directRoutePlan.agentStep;
    agentState = directRoutePlan.agentState;
    planSource = directRoutePlan.source || "route_planner_direct";
  } else if (shouldCallVisual) {
    const providerStartedAt = Date.now();
    try {
      const visionTimeoutMs = boundedAgentTimeoutMs(AGENT_STEP_VISION_TIMEOUT_MS, agentRemainingBudgetMs(startedAt), AGENT_PLANNER_TIMEOUT_MS);
      parsed = await callAgentGuiProvider(groundingGoal, snapshot, screenshotInfo, session, recentAgentActions, supportedSteps, deviceContext, agentMemory, guiProviderConfig, qwenProviderModel, visionTimeoutMs);
      providerMs = Date.now() - providerStartedAt;
      visualCalled = true;
      visualFrame = normalizeVisualFrame(parsed);
      if (isVisualFrameCacheable(visualFrame)) {
        session.visualFrame = visualFrame;
        session.lastFingerprint = fingerprint;
        session.lastStableScreenKey = screenKey;
      }
      session.lastActionKey = lastActionKey;
      session.lastObservationReason = visualFrame.reason || visualFrame.summary || "";
      session.failedVisualCount = 0;

      const direct = visualFrameToDirectAgentPlan(visualFrame, snapshot, supportedSteps, goal, screenshotInfo, deviceContext, recentAgentActions, false);
      if (direct && (direct.agentState?.isComplete || direct.agentState?.isWrong)) {
        agentStep = direct.agentStep;
        agentState = direct.agentState;
        planSource = direct.source || "vision_direct_state";
      } else if (parsedVisionPlanHasUsableStep(parsed)) {
        agentStep = normalizeAgentStep(parsed, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
        agentState = normalizeAgentState(parsed, agentStep);
        planSource = "vision_planner";
      } else if (direct) {
        agentStep = direct.agentStep;
        agentState = direct.agentState;
        planSource = direct.source || "vision_frame_direct";
      } else {
        agentStep = normalizeAgentStep({ agentStep: { type: "need_user_help", reason: "视觉单步规划器没有返回可靠动作，已暂停避免盲目操作。", riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
        agentState = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: false, isWrong: false, confidence: Math.max(0.25, Number(visualFrame?.confidence || 0)), reason: visualFrame?.reason || "视觉单步规划器没有返回可靠动作。" } }, agentStep);
        planSource = "vision_planner_no_action";
      }

      const routeGuard = applyRoutePlanGuard(routePlan, agentStep, agentState, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
      if (routeGuard.guarded) {
        agentStep = routeGuard.agentStep;
        agentState = routeGuard.agentState;
        routeGuarded = true;
        routeGuardReason = routeGuard.reason;
        planSource = `${planSource}_route_guard`;
      }

      const completionEvidence = goalAppearsCompleteFromEvidence(goal, snapshot, visualFrame, deviceContext);
      if (completionEvidence && ["wait", "scroll", "swipe", "need_user_help"].includes(agentStep.type)) {
        agentStep = normalizeAgentStep({ agentStep: { type: "finish", reason: completionEvidence.reason, riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
        agentState = normalizeAgentState({ agentState: { isComplete: true, expectedProgress: true, isWrong: false, confidence: completionEvidence.confidence, reason: completionEvidence.reason } }, agentStep);
        planSource = "vision_completion_guard";
      } else if (!officialGuiPlusLoop && agentStep.type === "wait" && countRecentActionKind(recentAgentActions, "wait") >= 1 && !isLikelyLoadingOrTransition(snapshot, visualFrame)) {
        agentStep = normalizeAgentStep({ agentStep: { type: "need_user_help", reason: "页面内容稳定但视觉规划器连续等待，为避免卡住已暂停。", riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
        agentState = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: false, isWrong: false, confidence: 0.45, reason: "连续 wait 且页面非加载态。" } }, agentStep);
        planSource = "vision_wait_guard";
      }
    } catch (error) {
      providerMs = Date.now() - providerStartedAt;
      visualCalled = true;
      visualError = `${isTimeoutLikeError(error) ? "timeout: " : ""}${sanitizeProviderError(error, 150)}`;
      session.failedVisualCount = Number(session.failedVisualCount || 0) + 1;
      if (isVisualFrameCacheable(session.visualFrame)) {
        visualFrame = session.visualFrame;
        visualCacheHit = true;
        const direct = visualFrameToDirectAgentPlan(visualFrame, snapshot, supportedSteps, goal, screenshotInfo, deviceContext, recentAgentActions, true);
        if (direct) {
          agentStep = direct.agentStep;
          agentState = direct.agentState;
          planSource = `${direct.source || "visual_cache"}_after_visual_error`;
        }
      }
      if (!agentStep || !agentState) {
        const reason = `视觉规划器暂时不可用：${visualError}`;
        agentStep = { type: "need_user_help", reason, riskLevel: "low", requiresConfirmation: false };
        agentState = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: false, isWrong: false, confidence: 0.25, reason } }, agentStep);
        planSource = "vision_planner_failed";
      }
    }
  } else {
    const direct = visualFrameToDirectAgentPlan(visualFrame, snapshot, supportedSteps, goal, screenshotInfo, deviceContext, recentAgentActions, visualCacheHit);
    if (direct && (!cachedFrameNeedsTextPlanner(visualFrame, recentAgentActions) || direct.agentState?.isComplete || direct.agentState?.isWrong)) {
      agentStep = direct.agentStep;
      agentState = direct.agentState;
      planSource = direct.source || "visual_cache_direct";
    } else {
      try {
        const plannerStartedAt = Date.now();
        parsed = await callAgentTextPlanner(goal, snapshot, supportedSteps, visualFrame, session, recentAgentActions, deviceContext, agentMemory);
        textPlannerMs = Date.now() - plannerStartedAt;
        agentStep = normalizeAgentStep(parsed, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
        agentState = normalizeAgentState(parsed, agentStep);
        const routeGuard = applyRoutePlanGuard(routePlan, agentStep, agentState, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
        if (routeGuard.guarded) {
          agentStep = routeGuard.agentStep;
          agentState = routeGuard.agentState;
          routeGuarded = true;
          routeGuardReason = routeGuard.reason;
          planSource = `${planSource || "text_planner"}_route_guard`;
        }

        const completionEvidence = goalAppearsCompleteFromEvidence(goal, snapshot, visualFrame, deviceContext);
        if (completionEvidence && ["wait", "scroll", "swipe"].includes(agentStep.type)) {
          agentStep = normalizeAgentStep({ agentStep: { type: "finish", reason: completionEvidence.reason, riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
          agentState = normalizeAgentState({ agentState: { isComplete: true, expectedProgress: true, isWrong: false, confidence: completionEvidence.confidence, reason: completionEvidence.reason } }, agentStep);
          planSource = "cache_text_completion_guard";
        } else if (!officialGuiPlusLoop && agentStep.type === "wait" && countRecentActionKind(recentAgentActions, "wait") >= 1 && !isLikelyLoadingOrTransition(snapshot, visualFrame)) {
          agentStep = normalizeAgentStep({ agentStep: { type: "need_user_help", reason: "页面内容已经稳定但规划器连续等待，为避免卡住已暂停。", riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
          agentState = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: false, isWrong: false, confidence: 0.45, reason: "连续 wait 且页面非加载态。" } }, agentStep);
          planSource = "cache_text_wait_guard";
        } else {
          planSource = visualCacheHit ? "cache_text_planner" : "text_planner";
        }
      } catch (error) {
        const reason = `文本规划器暂时不可用：${String(error?.message || error).replace(/Bearer\s+[A-Za-z0-9_\-\.]+/gi, "Bearer ***").slice(0, 140)}`;
        agentStep = { type: "need_user_help", reason, riskLevel: "low", requiresConfirmation: false };
        agentState = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: false, isWrong: false, confidence: 0.25, reason } }, agentStep);
        planSource = "text_planner_failed";
      }
    }
  }

  const shouldTryRealtimeFallback = Boolean(
    agentStep?.type === "need_user_help" &&
      (
        planSource === "vision_planner_failed" ||
        planSource === "vision_planner_no_action" ||
        planSource === "vision_wait_guard"
      )
  );
  if (shouldTryRealtimeFallback) {
    const fallback = await tryAgentTextPlannerFallback({
      startedAt,
      goal,
      snapshot,
      supportedSteps,
      visualFrame,
      session,
      recentAgentActions,
      deviceContext,
      agentMemory,
      screenshotInfo,
      reasonTag: planSource,
    });
    if (fallback) {
      if (fallback.parsed) parsed = fallback.parsed;
      agentStep = fallback.agentStep;
      agentState = fallback.agentState;
      textPlannerMs += Number(fallback.textPlannerMs || 0);
      if (fallback.textPlannerError) visualError = [visualError, `textFallback=${fallback.textPlannerError}`].filter(Boolean).join(" | ").slice(0, 220);
      planSource = fallback.source || `${planSource}_fallback`;
    }
  }

  const screenshotBytesApprox = screenshotInfo.hasImage ? Math.round((screenshotInfo.base64.length * 3) / 4) : 0;
  const promptChars = JSON.stringify({ visualFrame, snapshotHints: { texts: (snapshot.texts || []).slice(0, 12), clickableNodes: (snapshot.clickableNodes || []).slice(0, 12) } }).length;
  const totalMs = Date.now() - startedAt;
  const agentSteps = normalizeAgentStepBatch(parsed, agentStep, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
  const actionBatch = agentSteps.slice();
  const stopConditions = normalizeAgentStopConditions(parsed);

  return {
    ok: true,
    reply: agentState?.isComplete ? "视觉状态已确认任务完成。" : "已根据当前屏幕规划下一步。",
    agentState,
    isComplete: agentState.isComplete,
    expectedProgress: agentState.expectedProgress,
    isWrong: agentState.isWrong,
    confidence: agentState.confidence,
    nextHint: agentState.nextHint,
    agentStep,
    agentSteps,
    steps: agentSteps,
    actionBatch,
    stopConditions,
    ...baseMeta,
    sourceDetail: planSource || baseMeta.sourceDetail,
    model: visualCalled ? guiProviderConfig.provider : textPlannerMs > 0 ? "qwen" : "cache_rule",
    modelId: visualCalled ? guiProviderConfig.provider : textPlannerMs > 0 ? "qwen" : "cache_rule",
    modelLabel: visualCalled ? guiProviderConfig.modelLabel : textPlannerMs > 0 ? "Qwen 文本规划" : "视觉缓存规则",
    providerModel: visualCalled ? (guiProviderConfig.providerModel || qwenProviderModel || process.env.QWEN_VISION_MODEL || "qwen-vl-plus") : textPlannerMs > 0 ? process.env.QWEN_MODEL : "cache",
    searchUsed: false,
    structuredUsed: false,
    searchProvider: null,
    toolUsed: "agent_step",
    toolReason: agentStep.reason,
    sources: [],
    structuredData: null,
    toolIntent: null,
    intentError: null,
    deviceIntent: null,
    deviceIntentError: null,
    visualFrame,
    routePlan,
    debug: {
      currentApp: snapshot.currentApp,
      packageName: snapshot.packageName,
      nodeCount: snapshot.nodeCount,
      hasScreenshot: screenshotInfo.hasImage,
      screenshotSize: screenshotInfo.hasImage ? `${screenshotInfo.width}x${screenshotInfo.height}` : "",
      displaySize: screenshotInfo.hasImage ? `${screenshotInfo.displayWidth}x${screenshotInfo.displayHeight}` : "",
      supportedAgentSteps: supportedSteps,
      actionBatchMax: AGENT_ACTION_BATCH_MAX,
      actionBatchCount: agentSteps.length,
      stopConditions,
      installedAppCount: installedAppsFromDeviceContext(deviceContext).length,
      plannerAppCount: plannerAppsForPrompt(deviceContext, goal, screenshotInfo.hasImage).length,
      memorySignals: compactAgentMemoryForPrompt(agentMemory, recentAgentActions).loopSignals,
      requestBytes,
      readBodyMs,
      promptChars,
      screenshotBytesApprox,
      buildMessagesMs: 0,
      providerMs,
      textPlannerMs,
      routePlannerMs,
      routePlannerError,
      routeGuarded,
      routeGuardReason,
      groundingGoal,
      routePlan,
      parseMs: 0,
      totalMs,
      agentStepTotalBudgetMs: AGENT_STEP_TOTAL_BUDGET_MS,
      agentStepVisionTimeoutMs: AGENT_STEP_VISION_TIMEOUT_MS,
      agentStepTextFallbackTimeoutMs: AGENT_STEP_TEXT_PLANNER_TIMEOUT_MS,
      remainingBudgetMs: agentRemainingBudgetMs(startedAt),
      singleModelLoop: true,
      blockedVisionThenText: Boolean(visualCalled && textPlannerMs === 0),
      visionJsonMode: false,
      compactVisionMode: true,
      guiGroundingMode: true,
      guiOperatorMode: true,
      aliyunOfficialGuiLoop: officialGuiPlusLoop,
      guiSessionId: session.guiSessionId || "",
      guiHistoryCount: Array.isArray(session.guiHistory) ? session.guiHistory.length : 0,
      guiApiMode: ALIYUN_GUI_API_MODE,
      guiHighResolutionImages: ALIYUN_GUI_HIGH_RESOLUTION_IMAGES,
      guiEnableThinking: ALIYUN_GUI_ENABLE_THINKING,
      pluggableGuiProvider: true,
      layeredAgentRuntime: true,
      agentArchitecture: officialGuiPlusLoop ? "aliyun_gui_plus_official_mobile_use_loop" : "route_planner_gui_grounder_verifier_memory",
      guiProvider: guiProviderConfig.provider,
      requestedGuiProvider: guiProviderConfig.requestedProvider,
      guiProviderMode: guiProviderConfig.mode,
      guiProviderExternalUrlConfigured: Boolean(guiProviderConfig.externalUrlConfigured),
      guiProviderFallbackToQwen: Boolean(guiProviderConfig.fallbackToQwen),
      guiProviderFallbackReason: guiProviderConfig.fallbackReason || "",
      fastVisionMaxTokens: Math.min(
        guiProviderConfig.provider === "qwen_omni" ? AGENT_FAST_VISION_MAX_TOKENS : AGENT_GUI_PROVIDER_MAX_TOKENS,
        AGENT_VISION_MAX_TOKENS,
        260
      ),
      fastVisionPrompt: officialGuiPlusLoop ? "aliyun_official_mobile_use_loop_v1" : guiProviderConfig.provider === "aliyun_gui_plus" ? "aliyun_mobile_use_tool_protocol_v1" : guiProviderConfig.provider === "qwen_omni" ? "layered_gui_operator_v1" : "pluggable_layered_gui_provider_v1",
      visualCalled,
      visualCacheHit,
      androidRequestedVisual,
      hardForceVisual,
      visualError,
      realtimeFallbackUsed: shouldTryRealtimeFallback,
      visualFrameConfidence: visualFrame?.confidence || 0,
      guiRawOutputLen: parsed?.guiPlusRawOutput ? String(parsed.guiPlusRawOutput).length : 0,
      visualFrameCacheable: isVisualFrameCacheable(visualFrame),
      cacheEligible: hasCachedFrame,
      stableScreenMatched,
      stableScreenKey: screenKey.slice(0, 120),
      sessionId: session.id,
      sessionStep: session.step,
      screenFingerprint: fingerprint.slice(0, 80),
    },
    version: WORKER_VERSION,
  };
}

function normalizeToolName(value) {
  const tool = String(value || "none").toLowerCase().trim();
  if (["weather", "exchange_rate", "stock", "none"].includes(tool)) return tool;
  if (["currency", "rate", "fx", "forex"].includes(tool)) return "exchange_rate";
  return "none";
}

async function detectIntentByModel(prompt) {
  const messages = [
    {
      role: "system",
      content: [
        "你是一个工具调用路由器，只能输出严格 JSON，不能输出解释、Markdown 或代码块。",
        "你要判断用户是否需要实时结构化接口。",
        "支持的工具：",
        "1. weather：天气、气温、是否下雨、是否带伞、冷不冷、热不热、适不适合出门、穿衣建议、风速、湿度。",
        "2. exchange_rate：汇率、货币兑换、外汇价格。",
        "3. stock：股票、股价、证券行情、指数或 ETF 行情。",
        "4. none：不需要实时结构化接口。",
        "",
        "字段规则：",
        "- tool 必须是 weather、exchange_rate、stock、none 之一。",
        "- weather 时 location 填城市或地区名，例如 杭州、新加坡、北京；不能把“如何、怎么样、今天、天气”等问法词放进 location。",
        "- exchange_rate 时 from/to 填 ISO 货币代码，例如 USD、CNY、JPY、EUR；不知道时按语义补全。",
        "- stock 时 symbol 填股票代码。已知中文公司名要转成常见代码，例如 英伟达=NVDA，苹果=AAPL，特斯拉=TSLA，腾讯=0700.HK，贵州茅台=600519.SS。",
        "- none 时其他字段留空。",
        "",
        "输出格式必须是单个 JSON 对象：",
        "{\"tool\":\"weather | exchange_rate | stock | none\",\"location\":\"\",\"from\":\"\",\"to\":\"\",\"symbol\":\"\",\"reason\":\"\"}",
        "",
        "示例：",
        "用户：杭州今天天气如何",
        "{\"tool\":\"weather\",\"location\":\"杭州\",\"from\":\"\",\"to\":\"\",\"symbol\":\"\",\"reason\":\"用户询问杭州天气\"}",
        "用户：杭州今天要不要带伞",
        "{\"tool\":\"weather\",\"location\":\"杭州\",\"from\":\"\",\"to\":\"\",\"symbol\":\"\",\"reason\":\"用户询问杭州降雨情况\"}",
        "用户：美元兑人民币汇率是多少",
        "{\"tool\":\"exchange_rate\",\"location\":\"\",\"from\":\"USD\",\"to\":\"CNY\",\"symbol\":\"\",\"reason\":\"用户询问汇率\"}",
        "用户：英伟达股价是多少",
        "{\"tool\":\"stock\",\"location\":\"\",\"from\":\"\",\"to\":\"\",\"symbol\":\"NVDA\",\"reason\":\"用户询问股票行情\"}",
        "用户：辛亥革命的历史意义",
        "{\"tool\":\"none\",\"location\":\"\",\"from\":\"\",\"to\":\"\",\"symbol\":\"\",\"reason\":\"历史知识问题，不需要实时接口\"}"
      ].join("\n")
    },
    { role: "user", content: String(prompt || "") }
  ];

  const raw = await callOpenAICompatible(
    process.env.QWEN_BASE_URL,
    process.env.QWEN_API_KEY,
    process.env.QWEN_MODEL,
    messages,
    "Qwen Intent Router",
    {
      temperature: 0,
      max_tokens: 260,
      timeoutMs: STRUCTURED_ROUTER_TIMEOUT_MS,
      response_format: { type: "json_object" }
    }
  );

  try {
    const parsed = JSON.parse(extractJsonText(raw));
    const tool = normalizeToolName(parsed.tool);

    return {
      tool,
      location: String(parsed.location || "").trim(),
      from: String(parsed.from || "").trim().toUpperCase(),
      to: String(parsed.to || "").trim().toUpperCase(),
      symbol: String(parsed.symbol || "").trim().toUpperCase(),
      reason: String(parsed.reason || "").trim(),
      raw,
    };
  } catch (e) {
    return {
      tool: "none",
      location: "",
      from: "",
      to: "",
      symbol: "",
      reason: `intent_json_parse_failed: ${String(raw).slice(0, 120)}`,
      raw,
    };
  }
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

function mightNeedStructuredTool(prompt) {
  const text = String(prompt || "").toLowerCase();
  return hasAny(text, [
    "天气", "气温", "下雨", "带伞", "冷不冷", "热不热", "湿度", "风速",
    "汇率", "兑换", "外汇", "美元", "人民币", "日元", "欧元",
    "股票", "股价", "行情", "涨跌", "指数", "etf", "a股", "港股", "美股",
    "weather", "exchange rate", "stock", "price", "ticker"
  ]);
}

async function detectStructuredIntent(prompt, forceSearch) {
  if (!forceSearch || !mightNeedStructuredTool(prompt)) {
    return { intent: null, toolIntent: null, intentError: null };
  }

  try {
    const toolIntent = await detectIntentByModel(prompt);

    if (toolIntent.tool === "weather") {
      return {
        intent: {
          type: "weather",
          query: toolIntent.location || "杭州",
          reason: toolIntent.reason || "",
        },
        toolIntent,
        intentError: null,
      };
    }

    if (toolIntent.tool === "exchange_rate") {
      return {
        intent: {
          type: "exchange_rate",
          from: toolIntent.from || "USD",
          to: toolIntent.to || "CNY",
          reason: toolIntent.reason || "",
        },
        toolIntent,
        intentError: null,
      };
    }

    if (toolIntent.tool === "stock") {
      return {
        intent: {
          type: "stock",
          symbol: normalizeStockSymbol(toolIntent.symbol, prompt),
          reason: toolIntent.reason || "",
        },
        toolIntent,
        intentError: null,
      };
    }

    return { intent: null, toolIntent, intentError: null };
  } catch (e) {
    return {
      intent: null,
      toolIntent: null,
      intentError: String(e.message || e),
    };
  }
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

  if (!geoRes.ok) {
    throw new Error(`weather geocode ${geoRes.status}`);
  }

  const geo = await geoRes.json();
  const place = geo?.results?.[0];

  if (!place) {
    throw new Error(`weather location not found: ${location}`);
  }

  const weatherUrl = `https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto`;
  const weatherRes = await fetchWithTimeout(weatherUrl, { method: "GET" }, 12000);

  if (!weatherRes.ok) {
    throw new Error(`weather api ${weatherRes.status}`);
  }

  const weather = await weatherRes.json();
  const current = weather.current || {};

  return {
    data: {
      type: "weather",
      title: `${place.name || location}天气`,
      subtitle: [place.admin1, place.country].filter(Boolean).join(" · "),
      timestamp: current.time || new Date().toISOString(),
      metrics: [
        {
          label: "温度",
          value: String(current.temperature_2m ?? "--"),
          unit: weather.current_units?.temperature_2m || "°C",
        },
        {
          label: "天气",
          value: weatherCodeLabel(current.weather_code),
        },
        {
          label: "湿度",
          value: String(current.relative_humidity_2m ?? "--"),
          unit: weather.current_units?.relative_humidity_2m || "%",
        },
        {
          label: "风速",
          value: String(current.wind_speed_10m ?? "--"),
          unit: weather.current_units?.wind_speed_10m || "km/h",
        },
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

  if (!res.ok) {
    throw new Error(`exchange api ${res.status}`);
  }

  const data = await res.json();
  const rate = data?.rates?.[target];

  if (!rate) {
    throw new Error(`exchange rate not found: ${base}/${target}`);
  }

  return {
    data: {
      type: "exchange_rate",
      title: `${base.toUpperCase()} / ${target} 汇率`,
      subtitle: data.provider || "实时汇率",
      timestamp: data.time_last_update_utc || new Date().toISOString(),
      metrics: [
        {
          label: "来源币种",
          value: base.toUpperCase(),
        },
        {
          label: "目标币种",
          value: target,
        },
        {
          label: "汇率",
          value: String(rate),
        },
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

  if (!cleanSymbol) {
    throw new Error("stock symbol missing");
  }

  const url = `https://query1.finance.yahoo.com/v8/finance/chart/${encodeURIComponent(cleanSymbol)}?range=1d&interval=1m`;
  const res = await fetchWithTimeout(url, { method: "GET" }, 12000);

  if (!res.ok) {
    throw new Error(`stock api ${res.status}`);
  }

  const json = await res.json();
  const result = json?.chart?.result?.[0];
  const meta = result?.meta || {};
  const quote = result?.indicators?.quote?.[0] || {};
  const closes = Array.isArray(quote.close)
    ? quote.close.filter((v) => typeof v === "number")
    : [];

  const latest = closes.length ? closes[closes.length - 1] : meta.regularMarketPrice;
  const previous = meta.chartPreviousClose || meta.previousClose;
  const change = typeof latest === "number" && typeof previous === "number"
    ? latest - previous
    : null;
  const changePercent = change !== null && previous
    ? (change / previous) * 100
    : null;

  return {
    data: {
      type: "stock",
      title: `${cleanSymbol} 股票行情`,
      subtitle: meta.exchangeName || meta.fullExchangeName || "Yahoo Finance",
      timestamp: new Date().toISOString(),
      metrics: [
        {
          label: "代码",
          value: cleanSymbol,
        },
        {
          label: "价格",
          value: latest !== undefined ? String(Number(latest).toFixed(3)) : "--",
          unit: meta.currency || "",
        },
        {
          label: "涨跌",
          value: change !== null ? String(change.toFixed(3)) : "--",
        },
        {
          label: "涨跌幅",
          value: changePercent !== null ? `${changePercent.toFixed(2)}%` : "--",
        },
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
  if (!intent) {
    return {
      structuredData: null,
      structuredSource: null,
    };
  }

  if (intent.type === "weather") {
    const result = await getWeatherData(intent.query);
    return {
      structuredData: result.data,
      structuredSource: result.source,
    };
  }

  if (intent.type === "exchange_rate") {
    const result = await getExchangeRateData(intent.from, intent.to);
    return {
      structuredData: result.data,
      structuredSource: result.source,
    };
  }

  if (intent.type === "stock") {
    const result = await getStockData(intent.symbol);
    return {
      structuredData: result.data,
      structuredSource: result.source,
    };
  }

  return {
    structuredData: null,
    structuredSource: null,
  };
}

async function tavilySearch(query) {
  const key = process.env.TAVILY_API_KEY;

  if (!key) {
    return {
      sources: [],
      provider: null,
    };
  }

  const res = await fetchWithTimeout("https://api.tavily.com/search", {
    method: "POST",
    headers: {
      "content-type": "application/json",
    },
    body: JSON.stringify({
      api_key: key,
      query,
      search_depth: "basic",
      include_answer: false,
      include_raw_content: false,
      max_results: 5,
    }),
  }, SEARCH_TIMEOUT_MS);

  const text = await res.text();

  if (!res.ok) {
    throw new Error(`tavily ${res.status} ${text.slice(0, 160)}`);
  }

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

  return {
    sources,
    provider: "tavily",
  };
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

function buildMessages(bodyMessages, prompt, structuredData, sources, toolIntent, body = {}, options = {}) {
  const messages = normalizeMessages(bodyMessages, prompt);
  const contextBlocks = [];
  const includeCommandProtocol = Boolean(options.includeCommandProtocol);

  if (toolIntent) {
    contextBlocks.push(`工具路由判断：\n${JSON.stringify(toolIntent, null, 2)}`);
  }

  if (structuredData) {
    contextBlocks.push(`结构化实时数据：\n${JSON.stringify(structuredData, null, 2)}`);
  }

  const sourceContext = buildSourceContext(sources);

  if (sourceContext) {
    contextBlocks.push(`联网搜索资料：\n${sourceContext}`);
  }

  const commandInstruction = String(body.commandProtocolInstruction || body.systemPrompt || "").trim();

  const system = [
    "你是可靠、清晰、简洁的中文助手。",
    "如果提供了结构化实时数据或联网搜索资料，必须优先基于这些资料回答。",
    "不要编造来源；无法确认时要说明不确定。",
    "普通聊天路径只负责回答用户问题，不启动手机动作、不输出机器指令、不模拟已经操作手机。",
  ];

  if (includeCommandProtocol) {
    system.push(
      "仅当本次请求显式允许模型命令时，才可以输出 AI_LEDGER_COMMAND 机器标记；否则不要输出任何内部 JSON 或机器标记。",
      "agentAction 只允许 observe_screen 或 run_agent_task；具体点击、输入、滑动动作必须交给 agent_step 智能体规划接口。"
    );
    if (commandInstruction) system.push(commandInstruction);
  }

  if (contextBlocks.length) {
    system.push(contextBlocks.join("\n\n"));
  }

  return [
    {
      role: "system",
      content: system.join("\n"),
    },
    ...messages,
  ];
}


const server = http.createServer(async (req, res) => {
  try {
    if (req.method === "OPTIONS") {
      return sendJson(res, 204, {});
    }

    if (req.method === "GET") {
      return sendJson(res, 200, {
        ok: true,
        mode: "aliyun-fc-custom-runtime",
        version: WORKER_VERSION,
        features: [
          "qwen",
          "qwen_vision",
          "qwen_omni",
          "image_understanding",
          "multimodal_chat",
          "deepseek",
          "model_tool_router",
          "web_search_sources",
          "weather",
          "exchange_rate",
          "stock",
          "command_protocol",
          "agent_action",
          "run_agent_task",
          "screen_observation",
          "screenshot_visual_fallback",
          "computer_use_vision",
          "agent_step_planner",
          "pluggable_gui_provider",
          "aliyun_gui_plus",
          "clean_route_architecture",
          "explicit_agent_intent",
          "normal_chat_isolation",
          "mobile_action",
          "preference_update",
        ],
        limits: {
          maxBodyBytes: MAX_BODY_BYTES,
          maxImages: 4,
        },
      });
    }

    if (req.method !== "POST") {
      return sendJson(res, 405, {
        ok: false,
        error: "method_not_allowed",
      });
    }

    const readBodyStartedAt = Date.now();
    const body = await readJsonBody(req);
    const wantsStream = wantsSseStream(req, body);
    const readBodyMs = Date.now() - readBodyStartedAt;
    body.__debugRequestBytes = Number(req.headers["content-length"] || 0) || JSON.stringify(body).length;
    body.__debugReadBodyMs = readBodyMs;
    const images = normalizeImages(body);

    const prompt =
      body.message ||
      body.prompt ||
      body.text ||
      body.content ||
      body.agentGoal ||
      body.goal ||
      body.task ||
      latestUserText(body.messages) ||
      (images.length ? "请识别这张图片，说明图中内容，并回答我可能关心的问题。" : "");

    if (!prompt || !String(prompt).trim()) {
      return sendJson(res, 400, {
        ok: false,
        error: "empty_prompt",
      });
    }

    const modelPref = String(
      body.modelPreference ||
      body.aiModelPreference ||
      body.requestedModelPreference ||
      body.modelId ||
      body.model ||
      "auto"
    ).toLowerCase().trim();

    const useVision = images.length > 0 || modelPref === "qwen_vision" || modelPref === "qwen-vision";
    const resolved = useVision ? "qwen_vision" : resolveModel(modelPref, prompt);

    if (resolved === "unsupported") {
      return sendJson(res, 200, {
        ok: false,
        unsupportedModel: true,
        shouldFallback: true,
        code: "model_not_available",
        error: `CN gateway does not support model: ${modelPref}`,
        model: modelPref,
        version: WORKER_VERSION,
      });
    }

    if (isAgentOutcomeVerificationRequest(body)) {
      const verifyResult = await handleAgentOutcomeVerificationRequest(body, prompt, resolved === "qwen_vision" ? "qwen" : resolved);
      return sendJson(res, verifyResult.ok === false ? 400 : 200, verifyResult);
    }

    if (isAgentModeRequest(body)) {
      const agentResult = await handleAgentStepRequest(body, prompt, resolved === "qwen_vision" ? "qwen" : resolved);
      return sendJson(res, agentResult.ok === false ? 400 : 200, agentResult);
    }

    if (useVision) {
      if (!images.length) {
        return sendJson(res, 400, {
          ok: false,
          error: "vision_model_requires_image",
          code: "empty_images",
          model: "qwen_vision",
          version: WORKER_VERSION,
        });
      }

      const visionMessages = buildVisionMessages(prompt, images, body);
      const visionModel = process.env.QWEN_VISION_MODEL;
      const allowModelCommands = shouldAllowModelCommandsInChat(body);

      if (wantsStream && !allowModelCommands) {
        let sseStarted = false;
        try {
          const reply = await callOpenAICompatibleStream(
            process.env.QWEN_BASE_URL,
            process.env.QWEN_API_KEY,
            visionModel,
            visionMessages,
            "Qwen Vision",
            {
              temperature: 0.25,
              max_tokens: Number(process.env.QWEN_VISION_MAX_TOKENS || 1800),
              timeoutMs: Number(process.env.QWEN_VISION_TIMEOUT_MS || REQUEST_TIMEOUT_MS),
              onStreamStart: () => {
                sendSseHeaders(res);
                sseStarted = true;
              },
              onDelta: (delta) => {
                if (!sseStarted) {
                  sendSseHeaders(res);
                  sseStarted = true;
                }
                writeSse(res, { type: "delta", delta });
              },
            }
          );

          if (!sseStarted) {
            sendSseHeaders(res);
            sseStarted = true;
          }

          writeSse(res, {
            type: "done",
            ok: true,
            reply,
            agentAction: null,
            mobileAction: null,
            preferenceUpdate: null,
            source: "qwen_vision",
            sourceDetail: "qwen_vision",
            model: "qwen_vision",
            modelId: "qwen_vision",
            providerModel: visionModel,
            modelLabel: "Qwen 识图 · Omni Plus",
            imageCount: images.length,
            searchUsed: false,
            structuredUsed: false,
            searchProvider: null,
            toolUsed: "vision",
            toolReason: "",
            searchError: null,
            sources: [],
            structuredData: null,
            structuredError: null,
            toolIntent: null,
            intentError: null,
            deviceIntent: null,
            deviceIntentError: null,
            version: WORKER_VERSION,
          });
          writeSseDone(res);
          return res.end();
        } catch (streamError) {
          if (sseStarted) {
            const errorText = sanitizeProviderError(streamError, 180);
            writeSse(res, { type: "error", ok: false, error: errorText, code: "provider_stream_failed", version: WORKER_VERSION });
            writeSse(res, { type: "done", ok: false, error: errorText, code: "provider_stream_failed", version: WORKER_VERSION });
            writeSseDone(res);
            return res.end();
          }
          // Provider 不支持或流式握手失败时，回退到原来的 JSON 一次性返回，避免聊天中断。
        }
      }

      const reply = await callOpenAICompatible(
        process.env.QWEN_BASE_URL,
        process.env.QWEN_API_KEY,
        visionModel,
        visionMessages,
        "Qwen Vision",
        {
          temperature: 0.25,
          max_tokens: Number(process.env.QWEN_VISION_MAX_TOKENS || 1800),
          timeoutMs: Number(process.env.QWEN_VISION_TIMEOUT_MS || REQUEST_TIMEOUT_MS),
        }
      );

      const commandPayload = allowModelCommands ? extractCommandPayload(reply) : { agentAction: null, mobileAction: null, preferenceUpdate: null };
      const cleanReply = allowModelCommands ? (stripEmbeddedCommand(reply) || buildDeviceActionReply(commandPayload)) : reply;

      return sendJson(res, 200, {
        ok: true,
        reply: cleanReply,
        agentAction: commandPayload.agentAction,
        mobileAction: commandPayload.mobileAction,
        preferenceUpdate: commandPayload.preferenceUpdate,
        source: "qwen_vision",
        sourceDetail: "qwen_vision",
        model: "qwen_vision",
        modelId: "qwen_vision",
        providerModel: visionModel,
        modelLabel: "Qwen 识图 · Omni Plus",
        imageCount: images.length,
        searchUsed: false,
        structuredUsed: false,
        searchProvider: null,
        toolUsed: commandPayload.agentAction ? "agent_action" : "vision",
        toolReason: commandPayload.agentAction?.reason || "",
        searchError: null,
        sources: [],
        structuredData: null,
        structuredError: null,
        toolIntent: null,
        intentError: null,
        deviceIntent: null,
        deviceIntentError: null,
        version: WORKER_VERSION,
      });
    }

    let deviceIntent = { agentAction: null, mobileAction: null, preferenceUpdate: null, reason: "", source: "none" };
    let deviceIntentError = null;

    try {
      deviceIntent = await detectDeviceIntentByModel(prompt, body);
    } catch (e) {
      deviceIntentError = String(e.message || e);
    }

    if (deviceIntent?.agentAction || deviceIntent?.mobileAction || deviceIntent?.preferenceUpdate) {
      return sendJson(res, 200, {
        ok: true,
        reply: buildDeviceActionReply(deviceIntent),
        agentAction: deviceIntent.agentAction || null,
        mobileAction: deviceIntent.mobileAction || null,
        preferenceUpdate: deviceIntent.preferenceUpdate || null,
        source: "device_action_router",
        sourceDetail: deviceIntent.source || "device_router",
        model: resolved,
        modelId: resolved,
        modelLabel: resolved === "deepseek_v4" ? "DeepSeek V4 Pro" : "Qwen Max",
        searchUsed: false,
        structuredUsed: false,
        searchProvider: null,
        toolUsed: "device_action",
        toolReason: deviceIntent.reason || "",
        sources: [],
        structuredData: null,
        toolIntent: null,
        intentError: null,
        deviceIntent,
        deviceIntentError,
        version: WORKER_VERSION,
      });
    }

    const forceSearch = isForceWebSearch(body, prompt);
    const routed = await detectStructuredIntent(prompt, forceSearch);
    const structuredIntent = routed.intent;
    const toolIntent = routed.toolIntent;
    const intentError = routed.intentError;

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

    if (forceSearch && !structuredData) {
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

    const allowModelCommands = shouldAllowModelCommandsInChat(body);
    const messages = buildMessages(body.messages, prompt, structuredData, sources, toolIntent, body, { includeCommandProtocol: allowModelCommands });

    if (wantsStream && !allowModelCommands) {
      let sseStarted = false;
      try {
        const streamOptions = {
          onStreamStart: () => {
            sendSseHeaders(res);
            sseStarted = true;
          },
          onDelta: (delta) => {
            if (!sseStarted) {
              sendSseHeaders(res);
              sseStarted = true;
            }
            writeSse(res, { type: "delta", delta });
          },
        };

        const reply =
          resolved === "deepseek_v4"
            ? await callOpenAICompatibleStream(
                process.env.DEEPSEEK_BASE_URL,
                process.env.DEEPSEEK_API_KEY,
                process.env.DEEPSEEK_MODEL,
                messages,
                "DeepSeek",
                streamOptions
              )
            : await callOpenAICompatibleStream(
                process.env.QWEN_BASE_URL,
                process.env.QWEN_API_KEY,
                process.env.QWEN_MODEL,
                messages,
                "Qwen",
                streamOptions
              );

        if (!sseStarted) {
          sendSseHeaders(res);
          sseStarted = true;
        }

        writeSse(res, {
          type: "done",
          ok: true,
          reply,
          agentAction: null,
          mobileAction: null,
          preferenceUpdate: null,
          source: sources.length || structuredData ? "web_search_tool" : resolved === "deepseek_v4" ? "deepseek" : "qwen",
          sourceDetail: resolved === "deepseek_v4" ? "deepseek" : "qwen",
          model: resolved,
          modelId: resolved,
          modelLabel: resolved === "deepseek_v4" ? "DeepSeek V4 Pro" : "Qwen Max",
          searchUsed: Boolean(forceSearch && sources.length),
          structuredUsed: Boolean(structuredData),
          searchProvider,
          toolUsed: toolIntent?.tool || "none",
          toolReason: toolIntent?.reason || "",
          searchError,
          sources,
          structuredData,
          structuredError,
          toolIntent,
          intentError,
          deviceIntent,
          deviceIntentError,
          version: WORKER_VERSION,
        });
        writeSseDone(res);
        return res.end();
      } catch (streamError) {
        if (sseStarted) {
          const errorText = sanitizeProviderError(streamError, 180);
          writeSse(res, { type: "error", ok: false, error: errorText, code: "provider_stream_failed", version: WORKER_VERSION });
          writeSse(res, { type: "done", ok: false, error: errorText, code: "provider_stream_failed", version: WORKER_VERSION });
          writeSseDone(res);
          return res.end();
        }
        // Provider 不支持或流式握手失败时，回退到原来的 JSON 一次性返回，避免聊天中断。
      }
    }

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

    const commandPayload = allowModelCommands ? extractCommandPayload(reply) : { agentAction: null, mobileAction: null, preferenceUpdate: null };
    const cleanReply = allowModelCommands ? (stripEmbeddedCommand(reply) || buildDeviceActionReply(commandPayload)) : reply;

    return sendJson(res, 200, {
      ok: true,
      reply: cleanReply,
      agentAction: commandPayload.agentAction,
      mobileAction: commandPayload.mobileAction,
      preferenceUpdate: commandPayload.preferenceUpdate,
      source: sources.length || structuredData ? "web_search_tool" : resolved === "deepseek_v4" ? "deepseek" : "qwen",
      sourceDetail: resolved === "deepseek_v4" ? "deepseek" : "qwen",
      model: resolved,
      modelId: resolved,
      modelLabel: resolved === "deepseek_v4" ? "DeepSeek V4 Pro" : "Qwen Max",
      searchUsed: Boolean(forceSearch && sources.length),
      structuredUsed: Boolean(structuredData),
      searchProvider,
      toolUsed: commandPayload.agentAction ? "agent_action" : toolIntent?.tool || "none",
      toolReason: commandPayload.agentAction?.reason || toolIntent?.reason || "",
      searchError,
      sources,
      structuredData,
      structuredError,
      toolIntent,
      intentError,
      deviceIntent,
      deviceIntentError,
      version: WORKER_VERSION,
    });
  } catch (e) {
    return sendJson(res, 502, {
      ok: false,
      error: String(e.message || e),
      code: "provider_call_failed",
      version: WORKER_VERSION,
    });
  }
});

server.listen(PORT, () => {
  console.log(`AI Ledger CN web-data qwen-vision model-router server listening on ${PORT}`);
});

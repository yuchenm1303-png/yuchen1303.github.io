const http = require("http");
const crypto = require("crypto");
const { AsyncLocalStorage } = require("async_hooks");

const PORT = Number(process.env.PORT || process.env.FC_SERVER_PORT || 9000);
const REQUEST_TIMEOUT_MS = Number(process.env.PROVIDER_TIMEOUT_MS || 30000);
const TOOL_ROUTER_TIMEOUT_MS = Number(process.env.TOOL_ROUTER_TIMEOUT_MS || 18000);
const STRUCTURED_ROUTER_TIMEOUT_MS = Number(process.env.STRUCTURED_ROUTER_TIMEOUT_MS || 2800);
const SEARCH_TIMEOUT_MS = Number(process.env.SEARCH_TIMEOUT_MS || 6000);
const DEVICE_ROUTER_TIMEOUT_MS = Number(process.env.DEVICE_ROUTER_TIMEOUT_MS || 2800);
const ENABLE_DEVICE_MODEL_ROUTER = String(process.env.ENABLE_DEVICE_MODEL_ROUTER || "false").toLowerCase() === "true";
const ENABLE_AUTO_WEB_SEARCH_ON_ONLINE = String(process.env.ENABLE_AUTO_WEB_SEARCH_ON_ONLINE || "false").toLowerCase() === "true";
const AGENT_PLANNER_TIMEOUT_MS = Number(process.env.AGENT_PLANNER_TIMEOUT_MS || 7000);
const AGENT_STEP_TOTAL_BUDGET_MS = Number(process.env.AGENT_STEP_TOTAL_BUDGET_MS || 22000);
const AGENT_STEP_VISION_TIMEOUT_MS = Number(process.env.AGENT_STEP_VISION_TIMEOUT_MS || process.env.AGENT_REALTIME_VISION_TIMEOUT_MS || 15000);
const AGENT_FAST_VISION_MAX_TOKENS = Number(process.env.AGENT_FAST_VISION_MAX_TOKENS || 180);
const AGENT_TEXT_PLANNER_TIMEOUT_MS = Number(process.env.AGENT_TEXT_PLANNER_TIMEOUT_MS || 7000);
const AGENT_STEP_TEXT_PLANNER_TIMEOUT_MS = Number(process.env.AGENT_STEP_TEXT_PLANNER_TIMEOUT_MS || 1000);
const AGENT_STEP_FALLBACK_MIN_BUDGET_MS = Number(process.env.AGENT_STEP_FALLBACK_MIN_BUDGET_MS || 900);
const AGENT_ROUTE_PLANNER_TIMEOUT_MS = Number(process.env.AGENT_ROUTE_PLANNER_TIMEOUT_MS || 1800);
const AGENT_ROUTE_PLANNER_MAX_TOKENS = Number(process.env.AGENT_ROUTE_PLANNER_MAX_TOKENS || 360);
const AGENT_BRAIN_ROUTE_TIMEOUT_MS = Number(process.env.AGENT_BRAIN_ROUTE_TIMEOUT_MS || 15000);
const AGENT_BRAIN_ROUTE_MAX_TOKENS = Number(process.env.AGENT_BRAIN_ROUTE_MAX_TOKENS || 900);
const AGENT_BRAIN_ROUTE_CACHE_TTL_MS = Number(process.env.AGENT_BRAIN_ROUTE_CACHE_TTL_MS || 45 * 1000);
const AGENT_BRAIN_ROUTE_CACHE_MAX = Math.max(16, Math.min(256, Number(process.env.AGENT_BRAIN_ROUTE_CACHE_MAX || 96)));
const AGENT_BRAIN_ROUTE_APP_CANDIDATES_MAX = Math.max(16, Math.min(160, Number(process.env.AGENT_BRAIN_ROUTE_APP_CANDIDATES_MAX || 160)));
const AGENT_RESPONSE_SAFETY_MARGIN_MS = Number(process.env.AGENT_RESPONSE_SAFETY_MARGIN_MS || 900);
const AGENT_VISION_MAX_TOKENS = Number(process.env.AGENT_VISION_MAX_TOKENS || 360);
const AGENT_TEXT_MAX_TOKENS = Number(process.env.AGENT_TEXT_MAX_TOKENS || 300);
const MAX_BODY_BYTES = Number(process.env.MAX_BODY_BYTES || 16 * 1024 * 1024);
const REQUEST_BODY_TIMEOUT_MS = Math.max(1000, Number(process.env.REQUEST_BODY_TIMEOUT_MS || 15000));
const MAX_IMAGE_COUNT = Math.max(1, Math.min(8, Number(process.env.MAX_IMAGE_COUNT || 4)));
const MAX_IMAGE_BYTES = Math.max(256 * 1024, Number(process.env.MAX_IMAGE_BYTES || 4 * 1024 * 1024));
const MAX_TOTAL_IMAGE_BYTES = Math.max(MAX_IMAGE_BYTES, Number(process.env.MAX_TOTAL_IMAGE_BYTES || 10 * 1024 * 1024));
const MAX_AGENT_SCREENSHOT_BYTES = Math.max(256 * 1024, Number(process.env.MAX_AGENT_SCREENSHOT_BYTES || 4 * 1024 * 1024));
const PROVIDER_STREAM_HEADER_TIMEOUT_MS = Math.max(1000, Number(process.env.PROVIDER_STREAM_HEADER_TIMEOUT_MS || REQUEST_TIMEOUT_MS));
const PROVIDER_STREAM_IDLE_TIMEOUT_MS = Math.max(1000, Number(process.env.PROVIDER_STREAM_IDLE_TIMEOUT_MS || 20000));
const PROVIDER_STREAM_ABSOLUTE_TIMEOUT_MS = Math.max(PROVIDER_STREAM_HEADER_TIMEOUT_MS, Number(process.env.PROVIDER_STREAM_ABSOLUTE_TIMEOUT_MS || 90000));
const AI_LEDGER_CLIENT_TOKEN = String(process.env.AI_LEDGER_CLIENT_TOKEN || process.env.CLIENT_AUTH_TOKEN || "").trim();
const REQUIRE_CLIENT_AUTH = String(process.env.REQUIRE_CLIENT_AUTH || "false").toLowerCase() === "true";
const RATE_LIMIT_WINDOW_MS = Math.max(1000, Number(process.env.RATE_LIMIT_WINDOW_MS || 60 * 1000));
const RATE_LIMIT_MAX_REQUESTS = Math.max(1, Number(process.env.RATE_LIMIT_MAX_REQUESTS || 90));
const AGENT_RATE_LIMIT_MAX_REQUESTS = Math.max(1, Number(process.env.AGENT_RATE_LIMIT_MAX_REQUESTS || 30));
const AUTH_FAILURE_RATE_LIMIT_WINDOW_MS = Math.max(1000, Number(process.env.AUTH_FAILURE_RATE_LIMIT_WINDOW_MS || 60 * 1000));
const AUTH_FAILURE_RATE_LIMIT_MAX_REQUESTS = Math.max(1, Number(process.env.AUTH_FAILURE_RATE_LIMIT_MAX_REQUESTS || 12));
const RATE_LIMIT_BUCKET_MAX = Math.max(256, Math.min(16384, Number(process.env.RATE_LIMIT_BUCKET_MAX || 4096)));
const MAX_CONCURRENT_REQUESTS_PER_CLIENT = Math.max(1, Number(process.env.MAX_CONCURRENT_REQUESTS_PER_CLIENT || 4));
const AGENT_SESSION_TTL_MS = Number(process.env.AGENT_SESSION_TTL_MS || 8 * 60 * 1000);
const AGENT_SESSION_MAX = Number(process.env.AGENT_SESSION_MAX || 128);
const AGENT_SESSION_MAX_BYTES = Math.max(512 * 1024, Number(process.env.AGENT_SESSION_MAX_BYTES || 8 * 1024 * 1024));
const AGENT_HISTORY_FRAME_MAX_BYTES = Math.max(256 * 1024, Number(process.env.AGENT_HISTORY_FRAME_MAX_BYTES || 3 * 1024 * 1024));
const AGENT_VISUAL_CACHE_MIN_CONFIDENCE = Number(process.env.AGENT_VISUAL_CACHE_MIN_CONFIDENCE || 0.50);
const AGENT_VISUAL_FRAME_MIN_SIGNAL_CONFIDENCE = Number(process.env.AGENT_VISUAL_FRAME_MIN_SIGNAL_CONFIDENCE || 0.45);
const AGENT_SESSIONS = new Map();
const AGENT_BRAIN_ROUTE_CACHE = new Map();
const REQUEST_CONTEXT = new AsyncLocalStorage();
const RATE_LIMIT_BUCKETS = new Map();
const ACTIVE_REQUESTS_BY_CLIENT = new Map();

const AGENT_GUI_PROVIDER = normalizeAgentGuiProviderName(process.env.AGENT_GUI_PROVIDER || process.env.GUI_PROVIDER || "aliyun_gui_plus");
const AGENT_GUI_PROVIDER_URL = String(process.env.AGENT_GUI_PROVIDER_URL || process.env.GUI_PROVIDER_URL || "").trim();
const AGENT_GUI_PROVIDER_BASE_URL = String(process.env.AGENT_GUI_PROVIDER_BASE_URL || process.env.GUI_PROVIDER_BASE_URL || "").trim();
const AGENT_GUI_PROVIDER_API_KEY = String(process.env.AGENT_GUI_PROVIDER_API_KEY || process.env.GUI_PROVIDER_API_KEY || "").trim();
const AGENT_GUI_PROVIDER_MODEL = String(process.env.AGENT_GUI_PROVIDER_MODEL || process.env.GUI_PROVIDER_MODEL || "").trim();
const AGENT_GUI_PROVIDER_TIMEOUT_MS = Number(process.env.AGENT_GUI_PROVIDER_TIMEOUT_MS || process.env.GUI_PROVIDER_TIMEOUT_MS || AGENT_STEP_VISION_TIMEOUT_MS);
const AGENT_GUI_PROVIDER_MAX_TOKENS = Number(process.env.AGENT_GUI_PROVIDER_MAX_TOKENS || process.env.GUI_PROVIDER_MAX_TOKENS || 220);
const AGENT_GUI_PROVIDER_FALLBACK_TO_QWEN = String(process.env.AGENT_GUI_PROVIDER_FALLBACK_TO_QWEN || "false").toLowerCase() === "true";
const AGENT_GUI_STRICT_OFFICIAL_LOOP = String(process.env.AGENT_GUI_STRICT_OFFICIAL_LOOP || "true").toLowerCase() !== "false";
const ALIYUN_GUI_API_KEY = String(process.env.ALIYUN_GUI_API_KEY || process.env.QWEN_API_KEY || "").trim();
const ALIYUN_GUI_BASE_URL = String(process.env.ALIYUN_GUI_BASE_URL || "https://dashscope.aliyuncs.com/compatible-mode/v1").trim();
const ALIYUN_GUI_MODEL = String(process.env.ALIYUN_GUI_MODEL || "gui-plus-2026-02-26").trim();
const CLOUD_DECISION_OWNERSHIP = "normal_chat_models_and_gui_plus_exclusive_visual"; // Visual sessions explicitly owned by GUI Plus bypass AgentBrain and local semantic routing.
const ALIYUN_GUI_TIMEOUT_MS = Number(process.env.ALIYUN_GUI_TIMEOUT_MS || 15000);
const ALIYUN_GUI_MAX_TOKENS = Number(process.env.ALIYUN_GUI_MAX_TOKENS || 220);
const ALIYUN_GUI_API_MODE = String(process.env.ALIYUN_GUI_API_MODE || "dashscope_native").trim().toLowerCase();
const ALIYUN_GUI_HIGH_RESOLUTION_IMAGES = String(process.env.ALIYUN_GUI_HIGH_RESOLUTION_IMAGES || "true").toLowerCase() !== "false";
const ALIYUN_GUI_ENABLE_THINKING = String(process.env.ALIYUN_GUI_ENABLE_THINKING || "false").toLowerCase() === "true";
const AGENT_OFFICIAL_GUI_PLUS_MAX_TIMEOUT_MS = Math.max(4000, Math.min(12000, Number(process.env.AGENT_OFFICIAL_GUI_PLUS_MAX_TIMEOUT_MS || 10500)));
const AGENT_GUI_INTERACTION_REVIEW_TIMEOUT_MS = Math.max(800, Math.min(5000, Number(process.env.AGENT_GUI_INTERACTION_REVIEW_TIMEOUT_MS || 3200)));
const AGENT_GUI_DEEP_THINKING_MODE = String(process.env.AGENT_GUI_DEEP_THINKING_MODE || process.env.AGENT_DEEP_THINKING_MODE || "adaptive").trim().toLowerCase();
const AGENT_GUI_DEEP_THINKING_MIN_NO_PROGRESS = Math.max(2, Math.min(8, Number(process.env.AGENT_GUI_DEEP_THINKING_MIN_NO_PROGRESS || 2)));
const AGENT_GUI_DEEP_THINKING_TIMEOUT_EXTRA_MS = Math.max(0, Math.min(12000, Number(process.env.AGENT_GUI_DEEP_THINKING_TIMEOUT_EXTRA_MS || 3500)));
const AGENT_GUI_DEEP_THINKING_REASON_MAX = Math.max(2, Math.min(10, Number(process.env.AGENT_GUI_DEEP_THINKING_REASON_MAX || 6)));
const AGENT_GUI_HISTORY_N = Math.max(1, Math.min(4, Number(process.env.AGENT_GUI_HISTORY_N || 4)));
const AGENT_GUI_SESSION_MAX = Math.max(4, Math.min(64, Number(process.env.AGENT_GUI_SESSION_MAX || 24)));
const AGENT_TASK_CONTRACT_JUDGE_MODE = String(process.env.AGENT_TASK_CONTRACT_JUDGE_MODE || "adaptive").trim().toLowerCase();
const AGENT_TASK_CONTRACT_JUDGE_TIMEOUT_MS = Math.max(600, Math.min(6000, Number(process.env.AGENT_TASK_CONTRACT_JUDGE_TIMEOUT_MS || 1800)));
const AGENT_TASK_CONTRACT_JUDGE_MAX_TOKENS = Math.max(260, Math.min(1100, Number(process.env.AGENT_TASK_CONTRACT_JUDGE_MAX_TOKENS || 620)));

// v34 架构保护开关：普通聊天、显式智能体、agent_step、联网工具彻底分流。
// 默认关闭历史“普通聊天里用关键词/模型路由触发手机动作”的行为。
const ENABLE_LEGACY_CHAT_DEVICE_ROUTER = String(process.env.ENABLE_LEGACY_CHAT_DEVICE_ROUTER || "false").toLowerCase() === "true";
const ENABLE_MODEL_COMMANDS_IN_NORMAL_CHAT = String(process.env.ENABLE_MODEL_COMMANDS_IN_NORMAL_CHAT || "false").toLowerCase() === "true";
const ENABLE_AGENT_SUGGESTION_CARD = String(process.env.ENABLE_AGENT_SUGGESTION_CARD || "false").toLowerCase() === "true";
const ANDROID_COMMAND_PROTOCOL_VERSION = 6;
const NORMAL_CHAT_SERVER_BLOCKED_TOOL_TYPES = new Set([
  "request_shizuku_permission",
  "set_animation_scale",
  "force_stop_app",
  "clear_app_data",
  "uninstall_app",
  "disable_app",
  "enable_app",
]);


const WORKER_VERSION = "qwen-deepseek-cn-web-data-v107-route-execution-boundary";
const ANDROID_CLOUD_ROUTE_VISUAL_PROTOCOL = "android_visual_agent_v13_cloud_route_visual_loop";
const GUI_PLUS_CONTROLLER_PLACEHOLDER_IMAGE = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAAKElEQVR42u3NQQEAAAQEMPTvfErw2wqsk9SnqWcCgUAgEAgEAoHgygLH8QM9BsqtpQAAAABJRU5ErkJggg==";
const RUNTIME_BOOT_AT = Date.now();
let RUNTIME_REQUEST_COUNT = 0;
const RUNTIME_FATAL_EVENTS = [];
function recordRuntimeFatal(kind, error) {
  const message = sanitizeProviderError ? sanitizeProviderError(error, 260) : String(error?.message || error || kind).slice(0, 260);
  RUNTIME_FATAL_EVENTS.push({ kind, message, at: Date.now() });
  while (RUNTIME_FATAL_EVENTS.length > 12) RUNTIME_FATAL_EVENTS.shift();
  try { console.error(`[AI_LEDGER_${kind}]`, message); } catch (_) {}
}
process.on("unhandledRejection", (reason) => recordRuntimeFatal("UNHANDLED_REJECTION", reason));
process.on("uncaughtException", (error) => recordRuntimeFatal("UNCAUGHT_EXCEPTION", error));

const EMBEDDED_COMMAND_PREFIX = "[[AI_LEDGER_COMMAND:";
const EMBEDDED_COMMAND_SUFFIX = "]]";

function responseWritable(res) {
  return Boolean(res && !res.destroyed && !res.writableEnded);
}

function commonCorsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "content-type, authorization, x-client, x-client-id, x-device-id, x-ai-ledger-token, x-ai-ledger-stream",
  };
}

function sendJson(res, status, data) {
  if (!responseWritable(res)) return false;
  try {
    const context = currentRequestContext();
    const serializeStartedAt = Date.now();
    const payload = status === 204 ? undefined : JSON.stringify(data);
    const serializeMs = Date.now() - serializeStartedAt;
    const requestTotalMs = context.requestStartedAt ? Date.now() - context.requestStartedAt : 0;
    const coldStart = context.coldStart ? 1 : 0;
    if (!res.headersSent) {
      res.writeHead(status, {
        "Content-Type": "application/json; charset=utf-8",
        "Server-Timing": `serialize;dur=${serializeMs}, total;dur=${requestTotalMs}, cold;desc="${coldStart}"`,
        "X-AI-Ledger-Cold-Start": String(coldStart),
        "X-AI-Ledger-Serialize-Ms": String(serializeMs),
        "X-AI-Ledger-Request-Total-Ms": String(requestTotalMs),
        ...commonCorsHeaders(),
      });
    }
    res.end(payload);
    return true;
  } catch (_) {
    return false;
  }
}

function safeEndResponse(res) {
  if (!responseWritable(res)) return false;
  try {
    res.end();
    return true;
  } catch (_) {
    return false;
  }
}

function createHttpError(message, statusCode = 400, code = message) {
  const error = new Error(message);
  error.statusCode = statusCode;
  error.code = code;
  return error;
}

function currentRequestContext() {
  return REQUEST_CONTEXT.getStore() || {};
}

function currentRequestSignal() {
  return currentRequestContext().signal || null;
}

function linkAbortSignals(signals = []) {
  const controller = new AbortController();
  const cleanups = [];
  const abortFrom = (signal) => {
    if (controller.signal.aborted) return;
    const reason = signal?.reason instanceof Error ? signal.reason : new Error(String(signal?.reason || "request_aborted"));
    controller.abort(reason);
  };
  for (const signal of signals.filter(Boolean)) {
    if (signal.aborted) {
      abortFrom(signal);
      break;
    }
    const listener = () => abortFrom(signal);
    signal.addEventListener("abort", listener, { once: true });
    cleanups.push(() => signal.removeEventListener("abort", listener));
  }
  return { controller, cleanup: () => cleanups.splice(0).forEach((fn) => fn()) };
}

function normalizeNetworkIdentity(value, max = 180) {
  return String(value || "")
    .trim()
    .replace(/[^0-9a-fA-F:.\-_%]/g, "")
    .slice(0, max) || "unknown";
}

function requestIp(req) {
  const realIp = normalizeNetworkIdentity(req?.headers?.["x-real-ip"] || "");
  if (realIp !== "unknown") return realIp;
  const forwardedValues = String(req?.headers?.["x-forwarded-for"] || "")
    .split(",")
    .map((item) => normalizeNetworkIdentity(item))
    .filter((item) => item !== "unknown");
  if (forwardedValues.length) return forwardedValues[0];
  return normalizeNetworkIdentity(req?.socket?.remoteAddress || "unknown");
}

function clientAuthCandidates(req) {
  const authorization = String(req?.headers?.authorization || "").trim();
  const bearer = authorization.match(/^Bearer\s+(.+)$/i)?.[1]?.trim() || "";
  const headerToken = String(req?.headers?.["x-ai-ledger-token"] || "").trim();
  return {
    bearer: bearer.slice(0, 1024),
    headerToken: headerToken.slice(0, 1024),
    hadOversizedToken: bearer.length > 1024 || headerToken.length > 1024,
  };
}

function extractClientToken(req) {
  const candidates = clientAuthCandidates(req);
  return candidates.headerToken || candidates.bearer || "";
}

function requestClientKey(req) {
  // 限流身份不能只相信客户端可任意伪造的 X-Client-Id/X-Device-Id。
  // 使用认证令牌摘要（若有）与网络来源共同生成稳定配额键。
  const token = extractClientToken(req);
  const authPart = token
    ? `auth:${crypto.createHash("sha256").update(token).digest("hex").slice(0, 24)}`
    : "auth:anonymous";
  const identity = `${authPart}|ip:${requestIp(req)}`;
  return crypto.createHash("sha256").update(identity).digest("hex").slice(0, 32);
}

function requestAuthFailureKey(req) {
  return crypto.createHash("sha256").update(`auth-failure|ip:${requestIp(req)}`).digest("hex").slice(0, 32);
}

function timingSafeEqualText(a, b) {
  const left = Buffer.from(String(a || ""));
  const right = Buffer.from(String(b || ""));
  if (left.length !== right.length || left.length === 0) return false;
  return crypto.timingSafeEqual(left, right);
}

function validateClientAuth(req) {
  if (!AI_LEDGER_CLIENT_TOKEN) {
    if (REQUIRE_CLIENT_AUTH) return { ok: false, status: 503, error: "client_auth_not_configured" };
    return { ok: true, mode: "optional_unconfigured" };
  }
  const candidates = clientAuthCandidates(req);
  if (candidates.hadOversizedToken) return { ok: false, status: 401, error: "unauthorized_client" };
  if (candidates.headerToken && candidates.bearer && !timingSafeEqualText(candidates.headerToken, candidates.bearer)) {
    return { ok: false, status: 401, error: "conflicting_client_credentials" };
  }
  const provided = candidates.headerToken || candidates.bearer;
  if (!timingSafeEqualText(provided, AI_LEDGER_CLIENT_TOKEN)) return { ok: false, status: 401, error: "unauthorized_client" };
  return { ok: true, mode: "token" };
}

function consumeRateLimit(key, limit, scope = "general", windowMs = RATE_LIMIT_WINDOW_MS) {
  const now = Date.now();
  const safeWindowMs = Math.max(1000, Number(windowMs || RATE_LIMIT_WINDOW_MS));
  const safeKey = safeText(key, 96) || "unknown";
  const bucketKey = `${safeText(scope, 48) || "general"}:${safeKey}`;
  const existing = RATE_LIMIT_BUCKETS.get(bucketKey);
  const bucket = !existing || now - Number(existing.startedAt || 0) >= safeWindowMs
    ? { startedAt: now, count: 0, windowMs: safeWindowMs }
    : existing;
  bucket.count += 1;
  bucket.windowMs = safeWindowMs;
  RATE_LIMIT_BUCKETS.set(bucketKey, bucket);
  if (RATE_LIMIT_BUCKETS.size > RATE_LIMIT_BUCKET_MAX) {
    for (const [entryKey, entry] of RATE_LIMIT_BUCKETS.entries()) {
      const entryWindowMs = Math.max(1000, Number(entry?.windowMs || RATE_LIMIT_WINDOW_MS));
      if (!entry || now - Number(entry.startedAt || 0) >= entryWindowMs * 2 || RATE_LIMIT_BUCKETS.size > RATE_LIMIT_BUCKET_MAX) {
        RATE_LIMIT_BUCKETS.delete(entryKey);
      }
      if (RATE_LIMIT_BUCKETS.size <= RATE_LIMIT_BUCKET_MAX) break;
    }
  }
  return {
    allowed: bucket.count <= limit,
    remaining: Math.max(0, limit - bucket.count),
    retryAfterSeconds: Math.max(1, Math.ceil((safeWindowMs - (now - bucket.startedAt)) / 1000)),
  };
}

function acquireClientConcurrency(key) {
  const current = Number(ACTIVE_REQUESTS_BY_CLIENT.get(key) || 0);
  if (current >= MAX_CONCURRENT_REQUESTS_PER_CLIENT) return null;
  ACTIVE_REQUESTS_BY_CLIENT.set(key, current + 1);
  let released = false;
  return () => {
    if (released) return;
    released = true;
    const next = Math.max(0, Number(ACTIVE_REQUESTS_BY_CLIENT.get(key) || 1) - 1);
    if (next === 0) ACTIVE_REQUESTS_BY_CLIENT.delete(key);
    else ACTIVE_REQUESTS_BY_CLIENT.set(key, next);
  };
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
  if (!responseWritable(res)) return false;
  if (res.headersSent) return true;
  try {
    res.writeHead(200, {
      "Content-Type": "text/event-stream; charset=utf-8",
      "Cache-Control": "no-cache, no-transform",
      "Connection": "keep-alive",
      "X-Accel-Buffering": "no",
      ...commonCorsHeaders(),
    });
    if (typeof res.flushHeaders === "function") res.flushHeaders();
    return true;
  } catch (_) {
    return false;
  }
}

function writeSse(res, data) {
  if (!responseWritable(res)) return false;
  try { return res.write(`data: ${JSON.stringify(data)}\n\n`); } catch (_) { return false; }
}

function writeSseDone(res) {
  if (!responseWritable(res)) return false;
  try { return res.write("data: [DONE]\n\n"); } catch (_) { return false; }
}

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let totalBytes = 0;
    let settled = false;
    const timer = setTimeout(() => fail(createHttpError("request_body_timeout", 408, "request_body_timeout")), REQUEST_BODY_TIMEOUT_MS);

    function cleanup() {
      clearTimeout(timer);
      req.removeListener("data", onData);
      req.removeListener("end", onEnd);
      req.removeListener("error", onError);
      req.removeListener("aborted", onAborted);
    }
    function finish(value) { if (!settled) { settled = true; cleanup(); resolve(value); } }
    function fail(error) { if (!settled) { settled = true; cleanup(); reject(error); } }
    function onData(chunk) {
      const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
      totalBytes += buffer.length;
      if (totalBytes > MAX_BODY_BYTES) {
        fail(createHttpError("body_too_large", 413, "body_too_large"));
        req.resume();
        return;
      }
      chunks.push(buffer);
    }
    function onEnd() {
      const raw = chunks.length ? Buffer.concat(chunks, totalBytes).toString("utf8") : "";
      try { finish(raw ? JSON.parse(raw) : {}); }
      catch (_) { fail(createHttpError("invalid_json", 400, "invalid_json")); }
    }
    function onError(error) { fail(error instanceof Error ? error : createHttpError("request_read_failed", 400, "request_read_failed")); }
    function onAborted() { fail(createHttpError("request_aborted", 499, "request_aborted")); }

    req.on("data", onData);
    req.once("end", onEnd);
    req.once("error", onError);
    req.once("aborted", onAborted);
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

function normalizeBase64Payload(value) {
  return String(value || "").replace(/^data:[^;]+;base64,/i, "").replace(/\s+/g, "").trim();
}

function decodedBase64Bytes(value) {
  const base64 = normalizeBase64Payload(value);
  if (!base64) return 0;
  if (!/^[A-Za-z0-9+/]*={0,2}$/.test(base64) || base64.length % 4 === 1) return -1;
  const padding = base64.endsWith("==") ? 2 : base64.endsWith("=") ? 1 : 0;
  return Math.max(0, Math.floor((base64.length * 3) / 4) - padding);
}

function normalizeImages(body) {
  const raw = [
    ...(Array.isArray(body?.images) ? body.images : []),
    ...(Array.isArray(body?.attachments) ? body.attachments : []),
  ];
  const clean = [];
  const seen = new Set();
  let totalDecodedBytes = 0;

  for (const item of raw) {
    if (!item || typeof item !== "object") continue;
    const mimeType = String(item.mimeType || item.mediaType || "image/jpeg").trim() || "image/jpeg";
    const base64Data = normalizeBase64Payload(item.base64Data || item.imageBase64 || item.data || "");
    if (!base64Data) continue;
    const decodedBytes = decodedBase64Bytes(base64Data);
    if (decodedBytes < 0) throw createHttpError("invalid_image_base64", 400, "invalid_image_base64");
    if (decodedBytes > MAX_IMAGE_BYTES) throw createHttpError("image_too_large", 413, "image_too_large");
    const key = `${mimeType}:${base64Data.slice(0, 64)}:${base64Data.length}`;
    if (seen.has(key)) continue;
    seen.add(key);
    if (clean.length >= MAX_IMAGE_COUNT) throw createHttpError("too_many_images", 413, "too_many_images");
    totalDecodedBytes += decodedBytes;
    if (totalDecodedBytes > MAX_TOTAL_IMAGE_BYTES) throw createHttpError("images_total_too_large", 413, "images_total_too_large");
    clean.push({
      mimeType: mimeType.startsWith("image/") ? mimeType : "image/jpeg",
      base64Data,
      width: Number(item.width) || undefined,
      height: Number(item.height) || undefined,
      sizeBytes: decodedBytes,
      fileName: String(item.fileName || "").trim().slice(0, 80),
    });
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

async function fetchTextWithTimeout(url, options = {}, timeoutMs = REQUEST_TIMEOUT_MS) {
  if (typeof fetch !== "function") throw new Error("Node runtime does not support fetch. Please use Node.js 18 or Node.js 20 in Aliyun FC.");
  const ms = Math.max(300, Number(timeoutMs || REQUEST_TIMEOUT_MS));
  const linked = linkAbortSignals([options.signal, currentRequestSignal()]);
  const timer = setTimeout(() => linked.controller.abort(new Error("provider_body_timeout")), ms);
  try {
    const response = await fetch(url, { ...options, signal: linked.controller.signal });
    const bodyText = await response.text();
    return { response, text: bodyText };
  } catch (error) {
    if (linked.controller.signal.aborted && linked.controller.signal.reason instanceof Error) throw linked.controller.signal.reason;
    throw error;
  } finally { clearTimeout(timer); linked.cleanup(); }
}

async function fetchJsonWithTimeout(url, options = {}, timeoutMs = REQUEST_TIMEOUT_MS, name = "fetch") {
  const { response, text: bodyText } = await fetchTextWithTimeout(url, options, timeoutMs);
  try { return { response, data: bodyText ? JSON.parse(bodyText) : {}, text: bodyText }; }
  catch (_) { throw new Error(`${name} invalid_json_response ${String(bodyText || "").slice(0, 160)}`); }
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
  const { response: r, text: t } = await fetchTextWithTimeout(endpoint, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${key}`,
      ...extraHeaders,
    },
    body: JSON.stringify(payload),
  }, options.timeoutMs || REQUEST_TIMEOUT_MS);

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
  if (typeof fetch !== "function") throw new Error("Node runtime does not support fetch. Please use Node.js 18 or Node.js 20 in Aliyun FC.");

  const endpoint = `${String(base).replace(/\/+$/g, "")}/chat/completions`;
  const payload = { model, messages, temperature: options.temperature ?? 0.35, max_tokens: options.max_tokens ?? 1200, stream: true };
  const headerTimeoutMs = Math.max(500, Number(options.headerTimeoutMs || options.timeoutMs || PROVIDER_STREAM_HEADER_TIMEOUT_MS));
  const absoluteTimeoutMs = Math.max(headerTimeoutMs, Number(options.absoluteTimeoutMs || PROVIDER_STREAM_ABSOLUTE_TIMEOUT_MS));
  const idleTimeoutMs = Math.max(500, Math.min(absoluteTimeoutMs, Number(options.idleTimeoutMs || PROVIDER_STREAM_IDLE_TIMEOUT_MS)));
  const linked = linkAbortSignals([options.signal, currentRequestSignal()]);
  let headerTimer;
  let absoluteTimer;
  let idleTimer;
  let reader;
  const abortWith = (message) => { if (!linked.controller.signal.aborted) linked.controller.abort(new Error(message)); };
  const resetIdle = () => { clearTimeout(idleTimer); idleTimer = setTimeout(() => abortWith("provider_stream_idle_timeout"), idleTimeoutMs); };

  try {
    headerTimer = setTimeout(() => abortWith("provider_stream_header_timeout"), headerTimeoutMs);
    absoluteTimer = setTimeout(() => abortWith("provider_stream_absolute_timeout"), absoluteTimeoutMs);
    const r = await fetch(endpoint, {
      method: "POST",
      headers: { "content-type": "application/json", accept: "text/event-stream", authorization: `Bearer ${key}` },
      body: JSON.stringify(payload),
      signal: linked.controller.signal,
    });
    clearTimeout(headerTimer);
    headerTimer = null;
    if (!r.ok) {
      let t = "";
      try { t = await r.text(); } catch (_) {}
      throw new Error(`${name} stream ${r.status} ${t.slice(0, 300)}`);
    }
    if (!r.body || typeof r.body.getReader !== "function") throw new Error(`${name} stream body unavailable`);
    if (typeof options.onStreamStart === "function") options.onStreamStart();

    reader = r.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    let reply = "";
    let finished = false;

    function consumeEvent(eventText) {
      const dataLines = String(eventText || "").split(/\r?\n/g).map((line) => line.trimEnd()).filter((line) => line.startsWith("data:")).map((line) => line.slice(5).trimStart());
      if (!dataLines.length) return;
      const parsed = openAiStreamPayloadText(dataLines.join("\n").trim());
      if (parsed.text) {
        reply += parsed.text;
        if (typeof options.onDelta === "function") options.onDelta(parsed.text);
      }
      if (parsed.done) finished = true;
    }
    function consumeBufferedEvents() {
      while (true) {
        const match = buffer.match(/\r?\n\r?\n/);
        if (!match || match.index === undefined) break;
        const eventText = buffer.slice(0, match.index);
        buffer = buffer.slice(match.index + match[0].length);
        consumeEvent(eventText);
      }
    }

    resetIdle();
    while (!finished) {
      const { done, value } = await reader.read();
      if (done) break;
      resetIdle();
      buffer += decoder.decode(value, { stream: true });
      consumeBufferedEvents();
    }
    clearTimeout(idleTimer);
    idleTimer = null;
    buffer += decoder.decode();
    consumeBufferedEvents();
    if (buffer.trim()) consumeEvent(buffer);
    const cleanReply = String(reply || "").trim();
    if (!cleanReply) throw new Error(`${name} stream empty`);
    return cleanReply;
  } catch (error) {
    if (linked.controller.signal.aborted && linked.controller.signal.reason instanceof Error) throw linked.controller.signal.reason;
    throw error;
  } finally {
    clearTimeout(headerTimer);
    clearTimeout(absoluteTimer);
    clearTimeout(idleTimer);
    if (reader) { try { await reader.cancel(); } catch (_) {} }
    linked.cleanup();
  }
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

function normalizeDeviceControlAction(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const rawCapability = String(value.capability || value.tool || value.type || value.action || value.name || "")
    .toLowerCase()
    .trim()
    .replace(/[.\s\-]+/g, "_");
  const capabilityAliases = {
    app_open: "open_app",
    app_launch: "open_app",
    app_settings: "open_app_settings",
    settings_open: "open_system_settings",
    system_settings_open: "open_system_settings",
    network_wifi_set: "set_wifi_enabled",
    wifi_set: "set_wifi_enabled",
    network_bluetooth_set: "set_bluetooth_enabled",
    bluetooth_set: "set_bluetooth_enabled",
    network_mobile_data_set: "set_mobile_data_enabled",
    mobile_data_set: "set_mobile_data_enabled",
    system_dark_mode_set: "set_dark_mode",
    dark_mode_set: "set_dark_mode",
    system_brightness_set: "set_brightness",
    brightness_set: "set_brightness",
    system_screen_timeout_set: "set_screen_timeout",
    screen_timeout_set: "set_screen_timeout",
    system_auto_rotate_set: "set_auto_rotate",
    auto_rotate_set: "set_auto_rotate",
    system_media_volume_set: "set_media_volume",
    media_volume_set: "set_media_volume",
    device_health: "device_status",
    shizuku_permission_request: "request_shizuku_permission",
    shell_probe: "shizuku_status",
    system_animation_scale_set: "set_animation_scale",
    animation_scale_set: "set_animation_scale",
    app_force_stop: "force_stop_app",
    app_clear_data: "clear_app_data",
    app_uninstall: "uninstall_app",
    app_disable: "disable_app",
    app_enable: "enable_app",
  };
  const tool = normalizeAgentStepType(capabilityAliases[rawCapability] || rawCapability);
  if (!INTERNAL_TOOL_AGENT_STEP_TYPES.includes(tool)) return null;
  const rawArgs = value.arguments && typeof value.arguments === "object"
    ? value.arguments
    : (value.args && typeof value.args === "object"
        ? value.args
        : (value.params && typeof value.params === "object" ? value.params : {}));
  const directArgs = { ...rawArgs };
  for (const key of ["appName", "packageName", "page", "kind", "enabled", "mode", "percent", "deltaPercent", "timeoutMs", "seconds", "minutes", "scale"]) {
    if (value[key] !== undefined && directArgs[key] === undefined) directArgs[key] = value[key];
  }
  const args = normalizeInternalDeviceToolArgsForAndroid(tool, directArgs);
  const riskLevel = normalizeRiskLevel(value.riskLevel || value.risk || "");
  return {
    capability: tool,
    tool,
    args,
    arguments: args,
    riskLevel,
    requiresConfirmation: Boolean(value.requiresConfirmation || value.requireConfirmation || ["high", "critical"].includes(riskLevel)),
    reason: safeText(value.reason || value.explanation || "结构化内部设备控制动作。", 160),
  };
}

function normalizeAgentAction(value) {
  if (!value || typeof value !== "object") return null;
  const capability = String(value.capability || value.type || value.action || "")
    .toLowerCase()
    .trim()
    .replace(/-/g, "_");

  const normalizedCapability = capability === "device_control" || capability === "run_internal_device_control" ? "run_device_control" : capability;
  if (!["observe_screen", "run_agent_task", "run_device_control"].includes(normalizedCapability)) return null;

  const isRunTask = normalizedCapability === "run_agent_task";
  const isDeviceControl = normalizedCapability === "run_device_control";
  const goal = String(value.goal || value.task || value.instruction || value.query || value.prompt || "").trim();

  if ((isRunTask || isDeviceControl) && !goal) return null;

  const deviceControlAction = isDeviceControl
    ? normalizeDeviceControlAction(value.deviceControlAction || value.device_control_action || value.agentStep || value.step || value)
    : null;
  if (isDeviceControl && !deviceControlAction) return null;

  return {
    capability: normalizedCapability,
    title: String(value.title || (isDeviceControl ? "内部设备控制" : (isRunTask ? "手机智能体任务" : "观察当前屏幕"))).trim().slice(0, 40) || (isDeviceControl ? "内部设备控制" : (isRunTask ? "手机智能体任务" : "观察当前屏幕")),
    goal: (isRunTask || isDeviceControl) ? goal.slice(0, 240) : undefined,
    deviceControlAction: deviceControlAction || undefined,
    requiresConfirmation: Boolean(value.requiresConfirmation),
    reason: String(value.reason || (isDeviceControl ? "用户明确要求执行本地内部设备控制" : (isRunTask ? "用户明确要求操作手机完成任务" : "用户希望手机智能体读取当前界面"))).trim().slice(0, 160),
  };
}

function normalizeMobileAction(value) {
  if (!value || typeof value !== "object") return null;
  const type = String(value.type || value.action || "").toLowerCase().trim().replace(/-/g, "_");
  if (!["set_alarm", "navigate"].includes(type)) return null;

  const action = { type };

  if (type === "navigate") {
    const destination = String(value.destination || value.target || "").trim();
    if (!destination) return null;
    action.destination = destination.slice(0, 80);
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

function extractCommandPayload(reply, body = {}) {
  const embedded = extractEmbeddedCommand(reply);
  if (!embedded || typeof embedded !== "object") {
    return { agentAction: null, mobileAction: null, preferenceUpdate: null, rejectedReason: "" };
  }
  return enforceClientCommandPolicy(
    {
      agentAction: embedded.agentAction,
      mobileAction: embedded.mobileAction,
      preferenceUpdate: embedded.preferenceUpdate,
    },
    body
  );
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
    "打开", "关闭", "启动", "进入", "导航", "闹钟", "提醒", "设置为", "调到", "调高", "调低", "增加", "降低", "回家", "去学校",
    "wifi", "wi-fi", "无线网络", "蓝牙", "移动数据", "蜂窝数据", "流量", "深色模式", "暗色模式", "亮度", "音量", "静音",
    "屏幕超时", "自动旋转", "系统设置", "应用设置", "设备状态", "shizuku", "app", "应用", "wechat", "bilibili", "同花顺", "热榜", "联系人", "朋友圈"
  ]);
}

function shouldAllowModelCommandsInChat(body) {
  return Boolean(
    ENABLE_MODEL_COMMANDS_IN_NORMAL_CHAT ||
      body?.intent === "command_chat" ||
      body?.allowModelCommands === true ||
      body?.commandProtocol?.allowModelCommands === true ||
      body?.responseFormat?.allowModelCommands === true
  );
}

function isAndroidStructuredCommandProtocol(body) {
  const version = Number(body?.commandProtocol?.version || 0);
  return Boolean(
    shouldAllowModelCommandsInChat(body) &&
      body?.commandProtocol?.enabled === true &&
      body?.commandProtocol?.structuredCommandsOnly === true &&
      version >= ANDROID_COMMAND_PROTOCOL_VERSION
  );
}

function normalizedStringArray(value, max = 128) {
  if (!Array.isArray(value)) return [];
  return value
    .map((item) => String(item || "").trim())
    .filter(Boolean)
    .filter((item, index, array) => array.indexOf(item) === index)
    .slice(0, Math.max(1, Math.min(256, Number(max || 128))));
}

function clientSupportedAgentActions(body) {
  const hasDeclaration = Array.isArray(body?.commandProtocol?.supportedAgentActions);
  const declared = normalizedStringArray(body?.commandProtocol?.supportedAgentActions, 16)
    .map((item) => normalizeIntentName(item));
  return new Set(hasDeclaration ? declared : ["observe_screen", "run_agent_task", "run_device_control"]);
}

function clientSupportedDeviceToolTypes(body) {
  const hasDeclaration = Array.isArray(body?.commandProtocol?.supportedDeviceToolSteps) ||
    Array.isArray(body?.commandProtocol?.supportedDeviceControlActions);
  const declaredSteps = normalizedStringArray(body?.commandProtocol?.supportedDeviceToolSteps, 128)
    .map((item) => normalizeAgentStepType(item))
    .filter((item) => INTERNAL_TOOL_AGENT_STEP_TYPES.includes(item));
  const declaredCapabilities = normalizedStringArray(body?.commandProtocol?.supportedDeviceControlActions, 128)
    .map((capability) => normalizeDeviceControlAction({ capability, arguments: {} })?.tool || "")
    .filter((item) => INTERNAL_TOOL_AGENT_STEP_TYPES.includes(item));
  const combined = [...new Set([...declaredSteps, ...declaredCapabilities])];
  const fallback = DEVICE_TOOL_AGENT_STEP_TYPES.filter((item) => !NORMAL_CHAT_SERVER_BLOCKED_TOOL_TYPES.has(item));
  const allowed = hasDeclaration ? combined : fallback;
  return new Set(allowed.filter((item) => !NORMAL_CHAT_SERVER_BLOCKED_TOOL_TYPES.has(item)));
}

function clientSupportedMobileActions(body) {
  const hasDeclaration = Array.isArray(body?.commandProtocol?.supportedMobileActions);
  const declared = normalizedStringArray(body?.commandProtocol?.supportedMobileActions, 24)
    .map((item) => normalizeIntentName(item));
  return new Set(hasDeclaration ? declared : ["set_alarm", "navigate"]);
}

function clientSupportedPreferenceUpdates(body) {
  const hasDeclaration = Array.isArray(body?.commandProtocol?.supportedPreferenceUpdates);
  const declared = normalizedStringArray(body?.commandProtocol?.supportedPreferenceUpdates, 24)
    .map((item) => normalizeIntentName(item));
  return new Set(hasDeclaration ? declared : ["navigation_address"]);
}

function enforceClientCommandPolicy(payload, body) {
  const source = payload && typeof payload === "object" ? payload : {};
  const supportedAgentActions = clientSupportedAgentActions(body);
  const supportedDeviceTools = clientSupportedDeviceToolTypes(body);
  const supportedMobileActions = clientSupportedMobileActions(body);
  const supportedPreferenceUpdates = clientSupportedPreferenceUpdates(body);
  let rejectedReason = "";

  let agentAction = normalizeAgentAction(source.agentAction);
  if (agentAction && !supportedAgentActions.has(normalizeIntentName(agentAction.capability))) {
    rejectedReason = `unsupported_agent_action:${agentAction.capability}`;
    agentAction = null;
  }
  if (agentAction?.capability === "run_device_control") {
    const tool = normalizeAgentStepType(agentAction.deviceControlAction?.tool || agentAction.deviceControlAction?.capability || "");
    if (!tool || !supportedDeviceTools.has(tool) || NORMAL_CHAT_SERVER_BLOCKED_TOOL_TYPES.has(tool)) {
      rejectedReason = `unsupported_device_tool:${tool || "unknown"}`;
      agentAction = null;
    }
  }

  let mobileAction = normalizeMobileAction(source.mobileAction);
  if (mobileAction && !supportedMobileActions.has(normalizeIntentName(mobileAction.type))) {
    rejectedReason = `unsupported_mobile_action:${mobileAction.type}`;
    mobileAction = null;
  }

  let preferenceUpdate = normalizePreferenceUpdate(source.preferenceUpdate);
  if (preferenceUpdate && !supportedPreferenceUpdates.has(normalizeIntentName(preferenceUpdate.type))) {
    rejectedReason = `unsupported_preference_update:${preferenceUpdate.type}`;
    preferenceUpdate = null;
  }

  const actionCount = [agentAction, mobileAction, preferenceUpdate].filter(Boolean).length;
  if (actionCount > 1) {
    rejectedReason = "multiple_device_actions_not_allowed";
    mobileAction = null;
    preferenceUpdate = null;
  }

  return { agentAction, mobileAction, preferenceUpdate, rejectedReason };
}

function shouldEmbedCommandsInAnswer(body) {
  return shouldAllowModelCommandsInChat(body) && !isAndroidStructuredCommandProtocol(body);
}

function normalizeIntentName(value) {
  return String(value || "").toLowerCase().trim().replace(/[\s\-]+/g, "_");
}

function isVisualAgentStepRequest(body) {
  const intent = normalizeIntentName(body?.intent || body?.action || body?.type || body?.requestType);
  return Boolean(
    intent === "visual_agent_step" ||
      intent === "visual_agent_direct" ||
      body?.visualAgentDirect === true ||
      body?.forceVisualAgent === true
  );
}

function visualDecisionOwnerFromPayload(value) {
  const source = value && typeof value === "object" ? value : {};
  const ownership = source.visualOwnership && typeof source.visualOwnership === "object"
    ? source.visualOwnership
    : {};
  return normalizeIntentName(
    source.visualDecisionOwner ||
      source.decisionOwner ||
      ownership.owner ||
      ownership.decisionOwner ||
      ""
  );
}

function isExclusiveGuiPlusVisualRequest(body) {
  const source = body && typeof body === "object" ? body : {};
  const ownership = source.visualOwnership && typeof source.visualOwnership === "object"
    ? source.visualOwnership
    : {};
  const owner = visualDecisionOwnerFromPayload(source);
  const exclusive = Boolean(
    source.exclusiveVisualSession === true ||
      ownership.exclusive === true
  );
  const agentBrainDisabled = Boolean(
    source.allowAgentBrain === false ||
      ownership.allowAgentBrain === false
  );
  return Boolean(
    isVisualAgentStepRequest(source) &&
      owner === "gui_plus" &&
      exclusive &&
      agentBrainDisabled
  );
}

function isExclusiveGuiPlusVisualMemory(agentMemory) {
  const memory = agentMemory && typeof agentMemory === "object" ? agentMemory : {};
  const ownership = memory.visualOwnership && typeof memory.visualOwnership === "object"
    ? memory.visualOwnership
    : {};
  const owner = visualDecisionOwnerFromPayload(memory);
  const exclusive = Boolean(
    memory.exclusiveVisualSession === true ||
      ownership.exclusive === true
  );
  const agentBrainDisabled = Boolean(
    memory.allowAgentBrain === false ||
      ownership.allowAgentBrain === false
  );
  return Boolean(owner === "gui_plus" && exclusive && agentBrainDisabled);
}

function visualSurfaceContextFromPayload(body = null, deviceContext = null, agentMemory = null) {
  const request = body && typeof body === "object" ? body : {};
  const device = deviceContext && typeof deviceContext === "object" ? deviceContext : {};
  const memory = agentMemory && typeof agentMemory === "object" ? agentMemory : {};
  const memorySurface = memory.surfaceContext && typeof memory.surfaceContext === "object"
    ? memory.surfaceContext
    : {};
  const deviceSurface = device.surfaceContext && typeof device.surfaceContext === "object"
    ? device.surfaceContext
    : {};
  const requestDeviceSurface = request.deviceContext?.surfaceContext && typeof request.deviceContext.surfaceContext === "object"
    ? request.deviceContext.surfaceContext
    : {};
  const requestHandoff = request.controllerHandoff && typeof request.controllerHandoff === "object"
    ? request.controllerHandoff
    : {};
  return {
    ...memorySurface,
    ...deviceSurface,
    ...requestDeviceSurface,
    ...requestHandoff,
    role: safeText(
      request.surfaceRole ||
        requestHandoff.role ||
        requestDeviceSurface.role ||
        deviceSurface.role ||
        memorySurface.role ||
        "",
      40
    ),
  };
}

function isControllerHandoffSurface(body = null, snapshot = null, deviceContext = null, agentMemory = null) {
  const request = body && typeof body === "object" ? body : {};
  const memory = agentMemory && typeof agentMemory === "object" ? agentMemory : {};
  const surface = visualSurfaceContextFromPayload(request, deviceContext, memory);
  const role = normalizeIntentName(surface.role || request.surfaceRole || "");
  const currentPackage = safeText(
    snapshot?.packageName ||
      snapshot?.currentApp ||
      deviceContext?.currentApp?.packageName ||
      request.currentPackage ||
      "",
    120
  );
  const loopSignals = memory.loopSignals && typeof memory.loopSignals === "object"
    ? memory.loopSignals
    : {};
  const executedStepCount = Math.max(0, Number(loopSignals.executedStepCount || 0) || 0);
  const firstTurn = surface.isFirstVisualTurn !== false && executedStepCount === 0;
  const assistantHost = Boolean(
    surface.isAssistantHost === true ||
      role === "controller" ||
      isAssistantHostAppPackage(currentPackage) ||
      snapshotLooksLikeAssistantChat(snapshot)
  );
  // Controller handoff is a transport state, never a semantic inference from the
  // foreground package or UI text. The Android client must declare it explicitly.
  const handoffDeclared = Boolean(
    surface.controllerHandoffActive === true ||
      request.controllerHandoffActive === true
  );
  return Boolean(handoffDeclared && firstTurn && assistantHost);
}

function verifiedVisualSurfaceProtocol(body = null, snapshot = null, deviceContext = null, agentMemory = null) {
  const request = body && typeof body === "object" ? body : {};
  const device = deviceContext && typeof deviceContext === "object" ? deviceContext : {};
  const memory = agentMemory && typeof agentMemory === "object" ? agentMemory : {};
  const candidates = [
    request.runtimeExecutionContext,
    request.deviceContext?.runtimeExecutionContext,
    device.runtimeExecutionContext,
    memory.runtimeExecutionContext,
  ].filter((item) => item && typeof item === "object");
  const runtime = candidates[0] || {};
  const currentPackage = safeText(
    snapshot?.packageName || snapshot?.currentApp || runtime.currentPackage || request.currentPackage || "",
    120
  );
  const selectedTargetPackage = safeText(
    runtime.selectedTargetPackage || request.selectedTargetPackage || "",
    120
  );
  const verifiedTargetPackage = safeText(
    runtime.verifiedTargetPackage || request.verifiedTargetPackage || "",
    120
  );
  const surfaceState = normalizeIntentName(
    runtime.surfaceState || request.surfaceState || "planning"
  ) || "planning";
  const observationId = safeText(
    runtime.observationId || request.observationId || request.expectedActionObservationId || "",
    120
  );
  const packageMatches = Boolean(
    selectedTargetPackage &&
    verifiedTargetPackage &&
    currentPackage &&
    selectedTargetPackage === verifiedTargetPackage &&
    verifiedTargetPackage === currentPackage
  );
  const explicitlyEligible = runtime.guiPlusEligible === true;
  const guiPlusEligible = Boolean(
    explicitlyEligible &&
      selectedTargetPackage &&
      selectedTargetPackage === verifiedTargetPackage &&
      surfaceState === "work_surface"
  );
  return {
    schema: "verified_visual_surface_protocol_v1",
    surfaceState,
    selectedTargetPackage,
    verifiedTargetPackage,
    currentPackage,
    observationId,
    routeEpoch: Math.max(0, Number(runtime.routeEpoch || 0) || 0),
    surfaceEpoch: Math.max(0, Number(runtime.surfaceEpoch || 0) || 0),
    guiPlusEligible,
    packageMatches,
    routeRefreshRequested: Boolean(
      request.routeRefreshRequested === true ||
      request.invalidateCachedAgentBrainRoute === true ||
      surfaceState === "replanning" ||
      surfaceState === "interrupted"
    ),
  };
}

function createVisualAgentStepRoute({ handleAgentStepRequest }) {
  if (typeof handleAgentStepRequest !== "function") {
    throw new TypeError("handleAgentStepRequest dependency is required");
  }
  return async function handleVisualAgentStepRoute(body, prompt, resolvedModel) {
    return handleAgentStepRequest(body, prompt, resolvedModel);
  };
}

function normalizeExplicitDeviceIntent(body, prompt = "") {
  const intent = normalizeIntentName(body?.intent || body?.action || body?.type || body?.requestType);
  const agentActionFromBody = normalizeAgentAction(body?.agentAction || body?.commandProtocol?.agentAction);
  const mobileActionFromBody = normalizeMobileAction(body?.mobileAction || body?.commandProtocol?.mobileAction);
  const preferenceUpdateFromBody = normalizePreferenceUpdate(body?.preferenceUpdate || body?.commandProtocol?.preferenceUpdate);
  const deviceControlActionFromBody = normalizeDeviceControlAction(
    body?.deviceControlAction ||
      body?.device_control_action ||
      body?.commandProtocol?.deviceControlAction ||
      body?.commandProtocol?.device_control_action
  );

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

  const wantsDeviceControl = Boolean(
    intent === "run_device_control" ||
      intent === "device_control" ||
      intent === "internal_device_control" ||
      body?.runDeviceControl === true ||
      body?.commandProtocol?.intent === "run_device_control" ||
      body?.commandProtocol?.mode === "run_device_control"
  );

  if (wantsDeviceControl && deviceControlActionFromBody) {
    const goal = safeText(body?.agentGoal || body?.goal || body?.task || prompt || deviceControlActionFromBody.reason, 240);
    return {
      agentAction: {
        capability: "run_device_control",
        title: safeText(body?.title || "内部设备控制", 40),
        goal: goal || deviceControlActionFromBody.reason || deviceControlActionFromBody.tool,
        deviceControlAction: deviceControlActionFromBody,
        requiresConfirmation: Boolean(deviceControlActionFromBody.requiresConfirmation),
        reason: "显式内部设备控制请求，进入本地受控工具链路。",
      },
      mobileAction: null,
      preferenceUpdate: null,
      reason: "explicit_device_control",
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
    return {
      agentAction: {
        capability: "run_agent_task",
        title: safeText(body?.title || "手机智能体任务", 40),
        goal,
        requiresConfirmation: booleanFromValue(body?.requiresConfirmation ?? body?.commandProtocol?.requiresConfirmation, false),
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
    return {
      ...enforceClientCommandPolicy(explicit, body),
      reason: explicit.reason,
      source: explicit.source,
    };
  }

  const structuredProtocol = isAndroidStructuredCommandProtocol(body);
  if (!structuredProtocol && !ENABLE_LEGACY_CHAT_DEVICE_ROUTER) {
    return { agentAction: null, mobileAction: null, preferenceUpdate: null, reason: "skip_legacy_device_router_clean_architecture", source: "skip" };
  }

  const explicitDeviceRouter = Boolean(
    body?.intent === "device_route" ||
      body?.intent === "device_action" ||
      body?.commandProtocol?.forceDeviceRouter ||
      body?.responseFormat?.forceDeviceRouter ||
      String(body?.deviceRouterMode || "").toLowerCase() === "force"
  );
  const shouldRoute = structuredProtocol
    ? mightNeedDeviceRouter(prompt)
    : explicitDeviceRouter || (ENABLE_DEVICE_MODEL_ROUTER && isCommandProtocolEnabled(body) && mightNeedDeviceRouter(prompt));

  if (!shouldRoute) {
    return {
      agentAction: null,
      mobileAction: null,
      preferenceUpdate: null,
      reason: structuredProtocol ? "structured_command_protocol_no_device_signal" : "skip_device_model_router_normal_chat",
      source: "skip",
    };
  }

  const allowedAgentActions = [...clientSupportedAgentActions(body)];
  const allowedTools = [...clientSupportedDeviceToolTypes(body)];
  const allowedMobileActions = [...clientSupportedMobileActions(body)];
  const allowedPreferenceUpdates = [...clientSupportedPreferenceUpdates(body)];

  const messages = [
    {
      role: "system",
      content: [
        "你是 Android 手机 AI 助手的结构化设备意图路由器，只能输出严格 JSON，不能输出解释、Markdown 或代码块。",
        "你只判断用户是否明确要求执行手机本地能力；不要回答问题本身。",
        "普通问答、写作、翻译、代码、项目讨论、举例和假设场景必须全部返回 null。",
        "只有用户明确要求立即操作手机、系统设置、应用、导航、闹钟或视觉智能体任务时才触发。",
        "单纯打开 App 或调整系统参数优先 run_device_control；打开 App 后继续进入页面、搜索、点击、输入或滑动应使用 run_agent_task。",
        "不得输出支付、验证码、密码、授权、自由 shell、卸载、清除数据、强停、停用应用、动画比例修改或权限申请。",
        `允许的 agentAction：${allowedAgentActions.join("|") || "none"}。`,
        `允许的 deviceControlAction.tool：${allowedTools.join("|") || "none"}。`,
        `允许的 mobileAction.type：${allowedMobileActions.join("|") || "none"}。`,
        `允许的 preferenceUpdate.type：${allowedPreferenceUpdates.join("|") || "none"}。`,
        "输出格式必须是单个 JSON 对象：",
        '{"agentAction":null|{"capability":"observe_screen|run_agent_task|run_device_control","title":"","goal":"","deviceControlAction":{"tool":"","args":{},"riskLevel":"low|medium","requiresConfirmation":false,"reason":""},"requiresConfirmation":false,"reason":""},"mobileAction":null|{"type":"navigate|set_alarm","destination":"","hour":8,"minute":0,"label":""},"preferenceUpdate":null|{"type":"navigation_address","slot":"home|school|company|dorm","label":"","value":""},"reason":""}',
        "一次最多返回一种动作；无明确动作请求时三个动作字段都必须为 null。",
      ].join("\n"),
    },
    { role: "user", content: String(prompt || "") },
  ];

  const raw = await callOpenAICompatibleJsonFirst(
    process.env.QWEN_BASE_URL,
    process.env.QWEN_API_KEY,
    process.env.QWEN_MODEL,
    messages,
    structuredProtocol ? "Qwen Android Command V6 Router" : "Qwen Legacy Device Router",
    {
      temperature: 0,
      max_tokens: Number(process.env.DEVICE_ROUTER_MAX_TOKENS || 260),
      timeoutMs: DEVICE_ROUTER_TIMEOUT_MS,
      response_format: { type: "json_object" },
    }
  );

  try {
    const parsed = JSON.parse(extractJsonText(raw));
    const filtered = enforceClientCommandPolicy(parsed, body);
    return {
      ...filtered,
      reason: safeText(parsed.reason || filtered.rejectedReason || "", 160),
      source: structuredProtocol ? "android_command_v6_router" : "legacy_model_router",
      raw,
    };
  } catch (e) {
    return {
      agentAction: null,
      mobileAction: null,
      preferenceUpdate: null,
      rejectedReason: "device_router_json_parse_failed",
      reason: `device_router_json_parse_failed: ${String(raw).slice(0, 120)}`,
      source: structuredProtocol ? "android_command_v6_router_error" : "legacy_model_router_error",
      raw,
    };
  }
}

function buildDeviceActionReply(deviceIntent) {
  if (deviceIntent?.agentAction?.capability === "run_device_control") return "我已识别到内部设备控制任务，将交给本地受控工具执行。";
  if (deviceIntent?.agentAction?.capability === "run_agent_task") return "我已识别到手机智能体任务，将交给本地智能体执行。";
  if (deviceIntent?.agentAction?.capability === "observe_screen") return "我已识别到手机智能体观察请求，将在本地读取当前屏幕结构。";
  if (deviceIntent?.mobileAction) return "我已识别到手机动作，请在本地确认后执行。";
  if (deviceIntent?.preferenceUpdate) return "我已识别到一项本地偏好更新。";
  return "我已识别到本地能力请求。";
}


const DEVICE_TOOL_AGENT_STEP_TYPES = [
  "open_app",
  "open_system_settings",
  "open_app_settings",
  "set_brightness",
  "set_screen_timeout",
  "set_auto_rotate",
  "set_media_volume",
  "set_wifi_enabled",
  "set_bluetooth_enabled",
  "set_mobile_data_enabled",
  "set_dark_mode",
  "device_status",
  "shizuku_status",
  "request_shizuku_permission",
  "set_animation_scale",
  "force_stop_app",
  "clear_app_data",
  "uninstall_app",
  "disable_app",
  "enable_app",
];

const LEDGER_TOOL_AGENT_STEP_TYPES = [
  "ledger_add_record",
  "ledger_set_budget",
  "ledger_query_summary",
  "ledger_list_records",
];

const INTERNAL_TOOL_AGENT_STEP_TYPES = [
  ...DEVICE_TOOL_AGENT_STEP_TYPES,
  ...LEDGER_TOOL_AGENT_STEP_TYPES,
];

const SUPPORTED_AGENT_STEP_TYPES = [
  "open_app",
  "home",
  "back",
  "recents",
  "notifications",
  "quick_settings",
  "tap_node",
  "tap_xy",
  "long_press",
  "key",
  "input_text",
  "scroll",
  "swipe",
  "wait",
  "finish",
  "need_user_help",
  ...INTERNAL_TOOL_AGENT_STEP_TYPES,
];
const AGENT_ACTION_BATCH_MAX = Math.max(1, Math.min(3, Number(process.env.AGENT_ACTION_BATCH_MAX || 1)));

function safeText(value, max = 160) {
  return String(value ?? "").replace(/\s+/g, " ").trim().slice(0, max);
}

function normalizeAgentStepType(value) {
  const type = String(value || "").toLowerCase().trim().replace(/[\s\-]+/g, "_");
  if (SUPPORTED_AGENT_STEP_TYPES.includes(type)) return type;
  const mapped = {
    open: "open_app",
    launch: "open_app",
    launch_app: "open_app",
    open_application: "open_app",
    app_open: "open_app",
    tap: "tap_xy",
    click: "tap_xy",
    press: "tap_xy",
    longpress: "long_press",
    press_and_hold: "long_press",
    tap_xy: "tap_xy",
    tapx_y: "tap_xy",
    coordinate_click: "tap_xy",
    coordinate_tap: "tap_xy",
    click_xy: "tap_xy",
    tap_point: "tap_xy",
    point_click: "tap_xy",
    input: "input_text",
    type: "input_text",
    enter_text: "input_text",
    text: "input_text",
    done: "finish",
    complete: "finish",
    completed: "finish",
    ask_user: "need_user_help",
    need_help: "need_user_help",
    clarify: "need_user_help",
    settings: "open_system_settings",
    open_settings: "open_system_settings",
    system_settings: "open_system_settings",
    open_system_setting: "open_system_settings",
    app_settings: "open_app_settings",
    app_info: "open_app_settings",
    open_app_detail: "open_app_settings",
    brightness: "set_brightness",
    screen_brightness: "set_brightness",
    screen_timeout: "set_screen_timeout",
    sleep_timeout: "set_screen_timeout",
    auto_rotate: "set_auto_rotate",
    rotation: "set_auto_rotate",
    accelerometer_rotation: "set_auto_rotate",
    media_volume: "set_media_volume",
    volume: "set_media_volume",
    set_volume: "set_media_volume",
    music_volume: "set_media_volume",
    wifi: "set_wifi_enabled",
    wi_fi: "set_wifi_enabled",
    set_wifi: "set_wifi_enabled",
    wifi_enabled: "set_wifi_enabled",
    bluetooth: "set_bluetooth_enabled",
    set_bluetooth: "set_bluetooth_enabled",
    bluetooth_enabled: "set_bluetooth_enabled",
    mobile_data: "set_mobile_data_enabled",
    cellular_data: "set_mobile_data_enabled",
    data_enabled: "set_mobile_data_enabled",
    set_data: "set_mobile_data_enabled",
    dark_mode: "set_dark_mode",
    night_mode: "set_dark_mode",
    ui_mode: "set_dark_mode",
    health: "device_status",
    device_health: "device_status",
    shell_status: "shizuku_status",
    enhanced_status: "shizuku_status",
    shizuku: "shizuku_status",
    shizuku_permission: "request_shizuku_permission",
    request_shizuku: "request_shizuku_permission",
    animation_scale: "set_animation_scale",
    force_stop: "force_stop_app",
    force_stop_application: "force_stop_app",
    clear_data: "clear_app_data",
    uninstall: "uninstall_app",
    disable: "disable_app",
    enable: "enable_app",
    add_ledger_record: "ledger_add_record",
    create_ledger_record: "ledger_add_record",
    ledger_record_add: "ledger_add_record",
    ledger_add: "ledger_add_record",
    set_ledger_budget: "ledger_set_budget",
    ledger_budget_set: "ledger_set_budget",
    budget_set: "ledger_set_budget",
    query_ledger_summary: "ledger_query_summary",
    ledger_summary: "ledger_query_summary",
    ledger_query: "ledger_query_summary",
    list_ledger_records: "ledger_list_records",
    ledger_records: "ledger_list_records",
    ledger_list: "ledger_list_records",
  };
  return mapped[type] || "need_user_help";
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

function normalizeRiskLevel(value) {
  const raw = String(value || "").toLowerCase().trim().replace(/-/g, "_");
  if (["user_input", "credential_input"].includes(raw)) return "user_input";
  if (["high", "critical"].includes(raw)) return "high";
  if (["medium", "mid"].includes(raw)) return "medium";
  if (["low", "safe", "navigation", "navigate", ""].includes(raw)) return "low";
  return "low";
}

function normalizeInputMode(value) {
  const raw = String(value || "")
    .toLowerCase()
    .trim()
    .replace(/[\s\-]+/g, "_");
  if (["focused_direct", "focus_direct", "focused", "direct", "ime", "keyboard", "mobile_use_type"].includes(raw)) return "focused_direct";
  if (["node", "accessibility_node", "editable_node", "input_node", "a11y_node"].includes(raw)) return "node";
  if (["clipboard", "paste", "clip_paste"].includes(raw)) return "clipboard";
  return "";
}

function booleanFromValue(value, fallback = false) {
  if (value === true || value === "true" || value === 1 || value === "1" || value === "yes") return true;
  if (value === false || value === "false" || value === 0 || value === "0" || value === "no") return false;
  return Boolean(fallback);
}

function normalizeAgentStepArgs(value) {
  const raw = value && typeof value === "object" ? value : {};
  const nestedArgs = raw.args && typeof raw.args === "object"
    ? raw.args
    : raw.arguments && typeof raw.arguments === "object"
      ? raw.arguments
      : {};
  const args = { ...nestedArgs };
  const passthroughKeys = [
    "appName", "app", "application", "label", "name", "packageName", "package", "pkg",
    "targetText", "target", "title", "page", "kind", "setting",
    "percent", "brightness", "value", "seconds", "second", "sec", "minutes", "minute", "min",
    "timeoutMs", "screenTimeoutMs", "scale", "durationMs", "delayMs", "waitMs",
    "enabled", "enable", "on", "state", "mode", "operation", "delta", "deltaPercent", "changePercent", "adjustBy",
    "volume", "autoRotate", "darkMode", "text", "inputText", "query", "content", "reason", "risk", "riskLevel", "direction", "inputMode",
    "amount", "budget", "recordType", "transactionType", "entryType", "category", "date", "dateLabel",
    "range", "period", "timeRange", "month", "startDate", "endDate", "limit", "count", "description",
  ];
  for (const key of passthroughKeys) {
    if (args[key] === undefined && raw[key] !== undefined) args[key] = raw[key];
  }
  return Object.fromEntries(Object.entries(args).filter(([, v]) => v !== undefined && v !== null && String(v).trim() !== ""));
}

function hasAgentStepArgs(args) {
  return Boolean(args && typeof args === "object" && Object.keys(args).length > 0);
}

function agentBrainStepArgs(step) {
  const raw = step && typeof step === "object" ? step : {};
  return normalizeAgentStepArgs(raw);
}

function isDeviceToolAgentStepType(type) {
  return DEVICE_TOOL_AGENT_STEP_TYPES.includes(normalizeAgentStepType(type));
}

function isLedgerToolAgentStepType(type) {
  return LEDGER_TOOL_AGENT_STEP_TYPES.includes(normalizeAgentStepType(type));
}

function isInternalToolAgentStepType(type) {
  return INTERNAL_TOOL_AGENT_STEP_TYPES.includes(normalizeAgentStepType(type));
}


// TEST MODE: backend action-safety judging and action rewriting are intentionally disabled.
// GUI Plus / DeepSeek cloud decisions pass through unchanged except for protocol normalization.

function ensurePrimaryStepInBatch(agentSteps, agentStep) {
  const out = Array.isArray(agentSteps) ? agentSteps.slice() : [];
  if (!agentStep || ["finish", "need_user_help"].includes(agentStep.type)) return out;
  if (agentStep.riskLevel !== "low" || agentStep.requiresConfirmation) return out;
  const key = agentStepBatchKey(agentStep);
  if (!out.some((step) => agentStepBatchKey(step) === key)) out.unshift(agentStep);
  return out.slice(0, AGENT_ACTION_BATCH_MAX);
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

  const rawTexts = Array.isArray(raw.texts)
    ? raw.texts
    : Array.isArray(raw.topTexts)
      ? raw.topTexts
      : Array.isArray(raw.visibleTexts)
        ? raw.visibleTexts
        : [];
  const texts = rawTexts.map((item) => safeText(item, 80)).filter(Boolean).slice(0, 30);

  const clickableSource = Array.isArray(raw.clickableNodes)
    ? raw.clickableNodes
    : Array.isArray(raw.clickableTexts)
      ? raw.clickableTexts.map((text, index) => ({ id: `ct${index}`, text, clickable: true }))
      : [];
  const inputSource = Array.isArray(raw.inputNodes)
    ? raw.inputNodes
    : Array.isArray(raw.inputTexts)
      ? raw.inputTexts.map((text, index) => ({ id: `it${index}`, text, editable: true }))
      : [];
  const scrollableSource = Array.isArray(raw.scrollableNodes) ? raw.scrollableNodes : [];

  const clickableNodes = clickableSource
    .map(compactAgentNode)
    .filter(Boolean)
    .slice(0, 30);
  const inputNodes = inputSource
    .map(compactAgentNode)
    .filter(Boolean)
    .slice(0, 10);
  const scrollableNodes = scrollableSource
    .map(compactAgentNode)
    .filter(Boolean)
    .slice(0, 10);

  const visual = compactScreenVisual(raw.visual || {});
  const rawHasVisualImage = raw.hasVisualImage === true || raw.hasVisualImage === "true";
  const confidence = raw.confidence && typeof raw.confidence === "object"
    ? {
        hasUsefulNodes: Boolean(raw.confidence.hasUsefulNodes),
        needsVisualFallback: Boolean(raw.confidence.needsVisualFallback),
        hasVisualImage: Boolean(raw.confidence.hasVisualImage || rawHasVisualImage || visual.available),
      }
    : {
        hasUsefulNodes: clickableNodes.length > 0 || inputNodes.length > 0 || scrollableNodes.length > 0 || texts.length > 0,
        needsVisualFallback: (Number(raw.nodeCount) || 0) <= 8 || texts.length === 0 || clickableNodes.length === 0,
        hasVisualImage: Boolean(rawHasVisualImage || visual.available),
      };

  return {
    currentApp: safeText(raw.currentApp || raw.packageName || raw.app || "", 100),
    packageName: safeText(raw.packageName || raw.currentApp || raw.app || "", 100),
    nodeCount: Number(raw.nodeCount) || clickableNodes.length + inputNodes.length + scrollableNodes.length + texts.length,
    capturedNodeCount: Number(raw.capturedNodeCount) || undefined,
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

  const base64 = normalizeBase64Payload(base64Raw);
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

  const decodedBytes = decodedBase64Bytes(base64);
  if (decodedBytes < 0) throw createHttpError("invalid_agent_screenshot_base64", 400, "invalid_agent_screenshot_base64");
  if (decodedBytes > MAX_AGENT_SCREENSHOT_BYTES) throw createHttpError("agent_screenshot_too_large", 413, "agent_screenshot_too_large");

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
    sizeBytes: decodedBytes,
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
  // GUI Plus / mobile_use 官方点击坐标是 1000x1000。
  // 必须优先兼容，否则旧 Android 前端只认 x/y 时会显示“缺少坐标”或点击偏移。
  if (numeric >= 0 && numeric <= 1000) return clamp01(numeric / 1000);
  if (imageSize > 1 && numeric >= 0 && numeric <= imageSize + 24) return clamp01(numeric / imageSize);
  if (displaySize > 1 && numeric >= 0 && numeric <= displaySize + 24) return clamp01(numeric / displaySize);
  return undefined;
}

function firstFiniteNumber(...values) {
  for (const value of values) {
    const numeric = Number(value);
    if (Number.isFinite(numeric)) return numeric;
  }
  return undefined;
}

function coordinatePairFromValue(value) {
  if (Array.isArray(value) && value.length >= 2) {
    return { x: firstFiniteNumber(value[0]), y: firstFiniteNumber(value[1]) };
  }
  if (value && typeof value === "object") {
    return {
      x: firstFiniteNumber(value.x, value.cx, value.centerX, value.tapX, value[0]),
      y: firstFiniteNumber(value.y, value.cy, value.centerY, value.tapY, value[1]),
    };
  }
  return { x: undefined, y: undefined };
}

function extractRawAgentTapCoordinates(nested) {
  const raw = nested && typeof nested === "object" ? nested : {};
  const pair = coordinatePairFromValue(
    raw.coordinate ?? raw.coordinates ?? raw.coord ?? raw.coords ?? raw.point ?? raw.position ?? raw.center ?? raw.tapPoint ?? raw.xy
  );
  return {
    rawX: firstFiniteNumber(raw.x, raw.centerX, raw.tapX, raw.targetX, raw.cx, pair.x),
    rawY: firstFiniteNumber(raw.y, raw.centerY, raw.tapY, raw.targetY, raw.cy, pair.y),
  };
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
  const intent = normalizeIntentName(body?.intent || body?.action || body?.type || body?.requestType);
  return Boolean(
    body?.agentMode === true ||
      body?.agentStepRequest === true ||
      intent === "agent_step" ||
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

function normalizedContainsAny(value, candidates) {
  const haystack = normalizeForMatch(value);
  const list = Array.isArray(candidates) ? candidates : [candidates];
  if (!haystack || list.length === 0) return false;
  return list.some((candidate) => {
    const needle = normalizeForMatch(candidate);
    return Boolean(needle && haystack.includes(needle));
  });
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
  // v12F：本地快速验证不再因为屏幕出现某些词就判错/高风险。
  // 屏幕词只是证据，完成/走错必须由视觉验证器或 GUI Plus 判断。
  // 这里只保留空值返回，避免关键词护栏压过主脑。
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
    "3. 当前页面是否明显走错，例如进入无关页面、外部承诺页面、权限变更页面、错误 App、广告弹窗等。",
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
  const declared = Array.isArray(body?.supportedAgentSteps);
  const raw = declared ? body.supportedAgentSteps : [
    "open_app",
    "tap_xy",
    "input_text",
    "swipe",
    "back",
    "home",
    "wait",
    "finish",
    "need_user_help",
  ];
  const clean = raw
    .map(normalizeAgentStepType)
    .filter((item, index, arr) => SUPPORTED_AGENT_STEP_TYPES.includes(item) && arr.indexOf(item) === index);
  if (clean.length) return clean;
  return declared ? ["need_user_help"] : ["open_app", "tap_xy", "input_text", "swipe", "back", "home", "wait", "finish", "need_user_help"];
}

function normalizeAgentStep(value, snapshot, supportedSteps, goal, screenshotInfo = null, deviceContext = null) {
  const raw = value && typeof value === "object" ? value : {};
  const nested = raw.agentStep || raw.step || raw.actionStep || (Array.isArray(raw.agentSteps) ? raw.agentSteps[0] : null) || (Array.isArray(raw.actionBatch) ? raw.actionBatch[0] : null) || raw.result || raw;
  const safeSupported = Array.isArray(supportedSteps) && supportedSteps.length ? supportedSteps : SUPPORTED_AGENT_STEP_TYPES;
  const type = normalizeAgentStepType(nested.type || nested.action || nested.tool || nested.name);
  let finalType = safeSupported.includes(type) ? type : "need_user_help";

  const args = normalizeAgentStepArgs(nested);
  const argText = (...keys) => {
    for (const key of keys) {
      const value = args[key];
      const text = safeText(value, 180);
      if (text) return text;
    }
    return "";
  };

  const targetNodeId = safeText(nested.targetNodeId || nested.nodeId || nested.targetId || argText("targetNodeId", "nodeId", "targetId"), 32);
  const targetText = safeText(nested.targetText || nested.label || nested.title || nested.target || argText("targetText", "label", "title", "target", "page", "kind"), 80);
  const inputText = safeText(nested.text || nested.inputText || nested.value || argText("text", "inputText", "value", "query", "content"), 180);
  const rawInputMode = normalizeInputMode(nested.inputMode || nested.input_mode || nested.inputStrategy || nested.inputAction || nested.inputMethod || argText("inputMode", "input_mode", "inputStrategy", "input_strategy"));
  const inputMode = finalType === "input_text" ? (rawInputMode || "focused_direct") : rawInputMode;
  const requiresInputNode = finalType === "input_text"
    ? booleanFromValue(nested.requiresInputNode ?? nested.requires_input_node ?? nested.inputNodeRequired, inputMode === "node")
    : false;
  const direction = normalizeAgentDirection(nested.direction || argText("direction"));
  const reason = safeText(nested.reason || nested.rationale || nested.explanation || argText("reason", "rationale") || "根据当前屏幕和用户目标规划下一步。", 220);
  let appName = safeText(nested.appName || nested.app || nested.application || argText("appName", "app", "application", "label", "name"), 80);
  let packageName = safeText(nested.packageName || nested.package || nested.pkg || argText("packageName", "package", "pkg"), 120);
  const { rawX, rawY } = extractRawAgentTapCoordinates(nested);
  const durationMsRaw = Number(nested.durationMs ?? nested.waitMs ?? nested.delayMs ?? args.durationMs ?? args.waitMs ?? args.delayMs);
  const durationMs = Number.isFinite(durationMsRaw) ? Math.max(120, Math.min(2000, Math.round(durationMsRaw))) : undefined;

  let finalReason = reason;
  let finalTargetNodeId = targetNodeId;
  let finalDirection = direction;
  let finalX = rawX;
  let finalY = rawY;

  if (isInternalToolAgentStepType(finalType)) {
    finalReason = safeText(finalReason || "AgentBrain 选择结构化内部工具，由 Android 原生执行器校验并执行。", 260);
  }

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
    const canUseFocusedDirectInput = inputMode === "focused_direct" || inputMode === "clipboard" || booleanFromValue(nested.requiresInputNode ?? nested.requires_input_node ?? nested.inputNodeRequired, false) === false;
    if (!inputText) {
      finalType = "need_user_help";
      finalReason = "input_text 缺少要输入的文字，不能执行空输入。";
    } else if (!hasInput && requiresInputNode && !canUseFocusedDirectInput) {
      finalType = "need_user_help";
      finalReason = "当前屏幕没有可识别输入框，且该输入动作要求无障碍输入节点，不能输入文字。";
    } else if (!hasInput && canUseFocusedDirectInput) {
      finalReason = `${finalReason} 未发现无障碍 inputNodes，按 focused_direct 模式交给 Android 端向当前焦点/IME 直接输入。`;
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
    } else if (hasDeviceApps && appName) {
      // v41：open_app 是低风险恢复动作。deviceContext 可能因为权限/别名不全而没有命中，
      // 这里保留 appName 交给 Android InstalledAppIndex 做最终校验；失败会回传给下一轮，禁止退化成乱点当前页。
      packageName = "";
      finalReason = `${finalReason} 未在 deviceContext.installedApps 中精确命中，但保留 appName=${appName} 由 Android 本机应用索引最终校验打开。`;
    } else if (!appName && !packageName) {
      finalType = "need_user_help";
      finalReason = "open_app 缺少 appName 或 packageName，且未提供 deviceContext.installedApps，不能打开应用。";
    }
  }

  if (finalType === "wait" && !durationMs) {
    finalReason = finalReason || "等待页面加载后重新观察。";
  }

  // TEST MODE: do not rewrite cloud actions based on risk metadata.
  const riskLevel = "low";
  const requiresConfirmation = false;

  return {
    type: finalType,
    appName: finalType === "open_app" ? appName || undefined : appName || undefined,
    packageName: finalType === "open_app" ? packageName || undefined : packageName || undefined,
    targetNodeId: finalTargetNodeId || undefined,
    targetText: targetText || undefined,
    text: finalType === "input_text" ? inputText : inputText || undefined,
    inputMode: finalType === "input_text" ? inputMode || "focused_direct" : inputMode || undefined,
    requiresInputNode: finalType === "input_text" ? requiresInputNode : undefined,
    expectsFocusedInput: finalType === "input_text" ? (inputMode !== "node") : undefined,
    useFocusedInput: finalType === "input_text" ? (inputMode !== "node") : undefined,
    direction: ["scroll", "swipe"].includes(finalType) ? finalDirection || "down" : finalDirection || undefined,
    x: finalType === "tap_xy" && Number.isFinite(finalX) ? finalX : undefined,
    y: finalType === "tap_xy" && Number.isFinite(finalY) ? finalY : undefined,
    durationMs: finalType === "wait" ? durationMs || 700 : durationMs,
    reason: finalReason,
    riskLevel,
    requiresConfirmation,
    args: hasAgentStepArgs(args) ? args : undefined,
    arguments: hasAgentStepArgs(args) ? args : undefined,
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
    step?.appName || "",
    step?.packageName || "",
    Number.isFinite(step?.x) ? Number(step.x).toFixed(3) : "",
    Number.isFinite(step?.y) ? Number(step.y).toFixed(3) : "",
    step?.args && typeof step.args === "object" ? JSON.stringify(step.args) : "",
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
      const profileRaw = item.capabilityProfile && typeof item.capabilityProfile === "object"
        ? item.capabilityProfile
        : {};
      const categories = Array.isArray(profileRaw.categories)
        ? profileRaw.categories.map((value) => normalizeAppCapability(value)).filter(Boolean).slice(0, 16)
        : [];
      const capabilities = Array.isArray(profileRaw.capabilities)
        ? profileRaw.capabilities.map((value) => normalizeAppCapability(value)).filter(Boolean).slice(0, 24)
        : [];
      const evidence = Array.isArray(profileRaw.evidence)
        ? profileRaw.evidence.map((value) => safeText(value, 80)).filter(Boolean).slice(0, 12)
        : [];
      const confidenceRaw = Number(profileRaw.confidence || 0);
      const confidence = Number.isFinite(confidenceRaw)
        ? Math.max(0, Math.min(1, confidenceRaw))
        : 0;
      return {
        label,
        packageName,
        launchable: item.launchable !== false,
        aliases,
        capabilityProfile: {
          categories,
          capabilities,
          confidence,
          evidence,
        },
      };
    })
    .filter(Boolean)
    .filter((item) => item.launchable)
    .slice(0, 160);
}

function visualAppContextFromRequestBody(body, max = 256) {
  const raw = Array.isArray(body?.appContext)
    ? body.appContext
    : Array.isArray(body?.installedAppContext)
      ? body.installedAppContext
      : Array.isArray(body?.deviceContext?.visualAppContext)
        ? body.deviceContext.visualAppContext
        : Array.isArray(body?.deviceContext?.appContext)
          ? body.deviceContext.appContext
          : [];
  const seen = new Set();
  const out = [];
  for (const item of raw) {
    if (!item || typeof item !== "object") continue;
    const label = safeText(item.label || item.appName || item.name || "", 80);
    const packageName = safeText(item.packageName || item.package || "", 120);
    if (!label || !packageName) continue;
    const key = packageName;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push({ label, packageName, launchable: true });
    if (out.length >= Math.max(1, Math.min(256, Number(max || 256)))) break;
  }
  return out;
}

function mergeVisualAppContextIntoDeviceContext(body, deviceContext) {
  const visualApps = visualAppContextFromRequestBody(body);
  if (!visualApps.length) return deviceContext;
  deviceContext.visualAppContext = visualApps.map(({ label, packageName }) => ({ label, packageName }));
  deviceContext.appContext = visualApps.map(({ label, packageName }) => ({ label, packageName }));
  if (!Array.isArray(deviceContext.installedApps) || !deviceContext.installedApps.length) {
    deviceContext.installedApps = visualApps;
  }
  if (!Number(deviceContext.installedAppCount || 0)) deviceContext.installedAppCount = visualApps.length;
  if (!Number(deviceContext.uploadedAppCount || 0)) deviceContext.uploadedAppCount = visualApps.length;
  return deviceContext;
}

function normalizeAppCapability(value) {
  const raw = String(value || "").trim().toLowerCase().replace(/[\s\-]+/g, "_");
  const aliases = {
    stock: "stock_quote",
    stock_detail: "stock_quote",
    stock_quotes: "stock_quote",
    quote: "stock_quote",
    stock_lookup: "stock_search",
    search_stock: "stock_search",
    security_search: "stock_search",
    securities: "securities_trade",
    stock_trade: "securities_trade",
    security_trade: "securities_trade",
    brokerage_trade: "securities_trade",
    stock_order: "order_entry",
    stock_order_entry: "order_entry",
    securities_order_entry: "order_entry",
    trade_order_entry: "order_entry",
    maps: "navigation",
    map: "navigation",
    route: "navigation",
    directions: "navigation",
    ecommerce: "shopping",
    commerce: "shopping",
    chat: "messaging",
    im: "messaging",
    pay: "payment",
    wallet: "payment",
  };
  return aliases[raw] || raw;
}

function normalizeRequiredCapabilities(value) {
  const raw = Array.isArray(value)
    ? value
    : typeof value === "string"
      ? value.split(/[,，;；|]/g)
      : [];
  return Array.from(new Set(
    raw.map((item) => normalizeAppCapability(item)).filter(Boolean)
  )).slice(0, 16);
}

function appSupportsRequiredCapabilities(app, requiredCapabilities) {
  const required = normalizeRequiredCapabilities(requiredCapabilities);
  if (!required.length) return true;
  const actual = new Set(
    normalizeRequiredCapabilities(app?.capabilityProfile?.capabilities || [])
  );
  return required.every((capability) => actual.has(capability));
}

function defaultRequiredCapabilitiesForTask(domain, targetAction = "") {
  const action = String(targetAction || "").trim().toLowerCase().replace(/[\s\-]+/g, "_");
  if (
    domain === "stock_trade" ||
    ["open_order_entry", "order_entry", "place_order", "buy", "sell"].includes(action)
  ) {
    return ["stock_search", "securities_trade", "order_entry"];
  }
  if (domain === "stock_detail" || domain === "stock_index") {
    return ["stock_quote", "stock_search"];
  }
  if (domain === "navigation") return ["navigation"];
  if (domain === "video") return ["video"];
  if (domain === "chat" || domain === "social") return ["messaging"];
  return [];
}

function semanticRequiredCapabilities(contract) {
  const explicit = normalizeRequiredCapabilities(
    contract?.requiredCapabilities ||
      contract?.requiredAppCapabilities ||
      contract?.capabilities ||
      []
  );
  if (explicit.length) return explicit;
  return defaultRequiredCapabilitiesForTask(
    safeText(contract?.domain || "general", 60),
    safeText(contract?.targetAction || contract?.desiredAction || "", 80)
  );
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


function explicitAppMentionKnownApps() {
  return [
    "QQ", "微信", "支付宝", "淘宝", "京东", "拼多多", "微博", "抖音", "快手", "哔哩哔哩", "B站", "小红书",
    "同花顺", "东方财富", "雪球", "大智慧", "通达信", "腾讯自选股",
    "高德地图", "百度地图", "腾讯地图",
    "网易云音乐", "QQ音乐", "酷狗音乐", "酷我音乐",
    "携程旅行", "去哪儿旅行", "飞猪", "美团", "大众点评"
  ];
}

function isAsciiAppToken(value) {
  return /^[a-z0-9][a-z0-9._+\-]{1,24}$/i.test(String(value || ""));
}

function compactGoalForExplicitApp(value) {
  return String(value || "")
    .normalize("NFKC")
    .toLowerCase()
    .replace(/\s+/g, "");
}

function explicitAppMentionMatchedInGoal(goal, appName) {
  const app = normalizeAppMatchText(appName);
  if (!app || app.length < 2) return false;
  const raw = String(goal || "").normalize("NFKC").toLowerCase();
  const compact = compactGoalForExplicitApp(goal);
  if (!compact.includes(app)) return false;

  if (isAsciiAppToken(app)) {
    const token = escapeRegExp(app);
    const regex = new RegExp(`(^|[^a-z0-9])${token}([^a-z0-9]|$)`, "i");
    return regex.test(raw);
  }

  let index = compact.indexOf(app);
  while (index >= 0) {
    const before = compact.slice(Math.max(0, index - 4), index);
    const after = compact.slice(index + app.length, index + app.length + 6);
    const prefixOk = index === 0 || /(打开|启动|进入|前往|去到|切到|跳到|用|在|到)$/.test(before);
    const suffixOk = after === "" || /^(app|应用|客户端|里面|里|内|首页|主页|设置|设置页|页面|界面|搜索|消息|联系人|通讯录|公众号|小程序|钱包|我的|个人中心|频道|空间|好友|群|热榜|榜单)/.test(after);
    if (prefixOk && suffixOk) return true;
    index = compact.indexOf(app, index + 1);
  }
  return false;
}

function findExplicitAppMentionForPreflight(goal, deviceContext) {
  const installed = installedAppsFromDeviceContext(deviceContext);
  const candidates = [];
  const pushCandidate = (label, packageName = "", aliases = [], source = "known") => {
    const appLabel = safeText(label, 80);
    const normalized = normalizeAppMatchText(appLabel);
    if (!appLabel || normalized.length < 2) return;
    if (["股票", "证券", "行情", "地图", "音乐", "视频", "酒店", "旅行", "新闻", "资讯", "设置", "应用"].includes(appLabel)) return;
    candidates.push({ label: appLabel, packageName: safeText(packageName, 120), aliases, source });
  };

  installed.forEach((app) => {
    pushCandidate(app.label, app.packageName, app.aliases || [], "installed");
    (Array.isArray(app.aliases) ? app.aliases : []).forEach((alias) => pushCandidate(alias, app.packageName, [app.label, ...(app.aliases || [])], "installed_alias"));
  });
  explicitAppMentionKnownApps().forEach((name) => pushCandidate(name, "", [], "known_app_name"));

  let best = null;
  for (const candidate of candidates) {
    const names = [candidate.label, ...(Array.isArray(candidate.aliases) ? candidate.aliases : [])].filter(Boolean);
    for (const name of names) {
      if (!explicitAppMentionMatchedInGoal(goal, name)) continue;
      const installedMatch = findInstalledAppForOpenApp(candidate.label || name, candidate.packageName, deviceContext);
      const normalized = normalizeAppMatchText(name);
      const score = (candidate.source.startsWith("installed") ? 2000 : 1000) + normalized.length * 10 + (installedMatch ? 400 : 0);
      if (!best || score > best.score) {
        best = {
          label: installedMatch?.label || candidate.label || name,
          packageName: installedMatch?.packageName || candidate.packageName || "",
          aliases: installedMatch?.aliases || candidate.aliases || [],
          explicitName: name,
          source: candidate.source,
          confidence: candidate.source.startsWith("installed") ? 0.94 : 0.78,
          score,
        };
      }
    }
  }
  return best;
}

function explicitAppPreflightShouldOpen(goal, snapshot, deviceContext, explicitApp) {
  if (!explicitApp || (!explicitApp.label && !explicitApp.packageName)) return false;
  const currentPackage = safeText(snapshot?.packageName || snapshot?.currentApp || deviceContext?.currentApp?.packageName || "", 120);
  if (explicitApp.packageName && currentPackage && explicitApp.packageName === currentPackage) return false;
  const currentLabel = safeText(deviceContext?.currentApp?.label || deviceContext?.currentApp?.name || snapshot?.currentAppLabel || "", 80);
  const currentApp = { label: currentLabel || currentPackage, packageName: currentPackage, aliases: [] };
  if (appMatchesTaskContractApp(currentApp, explicitApp)) return false;

  const assistantHost = isAssistantHostAppPackage(currentPackage) || snapshotLooksLikeAssistantChat(snapshot);
  const cleanGoal = normalizeForMatch(goal);
  const openLike = normalizedContainsAny(cleanGoal, ["打开", "启动", "进入", "前往", "去到", "切到", "跳到", "设置页", "页面", "界面"]);
  return Boolean(assistantHost || !currentPackage || openLike);
}

function buildExplicitAppOpenPreflightPlan(goal, snapshot, supportedSteps, screenshotInfo, deviceContext, agentMemory, recentActions, source = "explicit_app_preflight") {
  if (!Array.isArray(supportedSteps) || !supportedSteps.includes("open_app")) return null;
  const explicitApp = findExplicitAppMentionForPreflight(goal, deviceContext);
  if (!explicitAppPreflightShouldOpen(goal, snapshot, deviceContext, explicitApp)) return null;
  const appName = explicitApp.label || explicitApp.explicitName || explicitApp.packageName || "";
  if (!appName && !explicitApp.packageName) return null;
  const reason = [
    `显式 App 预检：用户目标中明确出现应用“${appName}”，当前页不是该应用。`,
    "先打开目标 App，再由 GUI Plus 在 App 内完成页面导航；不要在 AI 助手聊天页直接结束或输入整句目标。"
  ].join(" ");
  const step = normalizeAgentStep({
    agentStep: {
      type: "open_app",
      appName,
      packageName: explicitApp.packageName || undefined,
      targetText: appName,
      reason,
      riskLevel: "low",
      requiresConfirmation: false,
    },
  }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
  if (!step || step.type !== "open_app") return null;
  const state = normalizeAgentState({
    agentState: {
      isComplete: false,
      expectedProgress: true,
      isWrong: false,
      confidence: explicitApp.confidence || 0.9,
      reason,
      nextHint: `打开 ${appName} 后继续完成：${safeText(goal, 120)}`,
    },
  }, step);
  return {
    agentStep: step,
    agentState: state,
    source,
    explicitAppPreflight: true,
    explicitApp,
  };
}



function deviceContextSummaryForPrompt(deviceContext) {
  const ctx = deviceContext && typeof deviceContext === "object" ? deviceContext : {};
  const device = ctx.device || {};
  const screen = ctx.screen || {};
  const currentApp = ctx.currentApp || {};
  const inventory = ctx.inventory && typeof ctx.inventory === "object" ? ctx.inventory : {};
  const apps = installedAppsFromDeviceContext(ctx);
  const resolution = normalizeTargetAppResolution(
    ctx.targetAppResolution,
    ctx
  );
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
      isAssistantHost: Boolean(currentApp.isAssistantHost),
      isLauncherOrSystemSurface: Boolean(currentApp.isLauncherOrSystemSurface),
    },
    appInventory: {
      schema: inventory.schema || "",
      inventoryHash: safeText(ctx.appInventoryHash || inventory.inventoryHash || "", 120),
      installedAppCount: Number(ctx.installedAppCount || inventory.installedAppCount || apps.length),
      uploadedAppCount: Number(ctx.uploadedAppCount || inventory.uploadedAppCount || apps.length),
      truncated: Boolean(ctx.installedAppsTruncated || inventory.truncated),
    },
    installedApps: apps.slice(0, 120),
    targetAppResolution: resolution,
    taskExecutionContract: ctx.taskExecutionContract && typeof ctx.taskExecutionContract === "object"
      ? ctx.taskExecutionContract
      : null,
    toolRules: Array.isArray(ctx.toolRules) ? ctx.toolRules.slice(0, 10) : [],
  };
}

const AGENT_DOMAIN_APP_KEYWORDS = {
  stock: ["同花顺", "东方财富", "雪球", "大智慧", "通达信", "自选股", "证券", "股票", "涨乐", "富途", "老虎", "华泰", "国泰君安", "招商证券", "广发证券", "中信证券", "银河证券", "平安证券"],
  stock_trade: ["证券", "券商", "交易", "委托", "下单", "涨乐", "蜻蜓点金", "金太阳", "君弘", "海通财", "财富通", "同花顺", "东方财富"],
  index: ["同花顺", "东方财富", "雪球", "大智慧", "通达信", "证券", "股票", "自选股"],
  finance_news: ["同花顺", "东方财富", "雪球", "财联社", "新浪财经", "腾讯自选股", "证券", "股票", "今日头条", "百度", "浏览器"],
  navigation: ["高德", "百度地图", "腾讯地图", "地图"],
  music: ["网易云", "QQ音乐", "酷狗", "酷我", "音乐"],
  video: ["哔哩", "bilibili", "B站", "抖音", "快手", "腾讯视频", "爱奇艺", "优酷", "视频"],
  travel: ["携程", "去哪儿", "飞猪", "同程", "美团", "大众点评", "酒店", "旅行"],
};

function agentDomainAppKeywords(domain) {
  if (domain === "stock_trade") return AGENT_DOMAIN_APP_KEYWORDS.stock_trade;
  if (domain === "stock_detail") return AGENT_DOMAIN_APP_KEYWORDS.stock;
  if (domain === "stock_index") return AGENT_DOMAIN_APP_KEYWORDS.index;
  if (domain === "finance_news") return AGENT_DOMAIN_APP_KEYWORDS.finance_news;
  if (domain === "navigation") return AGENT_DOMAIN_APP_KEYWORDS.navigation;
  if (domain === "music") return AGENT_DOMAIN_APP_KEYWORDS.music;
  if (domain === "video") return AGENT_DOMAIN_APP_KEYWORDS.video;
  if (domain === "travel") return AGENT_DOMAIN_APP_KEYWORDS.travel;
  return [];
}

function appMatchesAnyKeyword(app, keywords) {
  const names = [app?.label, app?.packageName, ...(Array.isArray(app?.aliases) ? app.aliases : [])]
    .map((item) => normalizeAppMatchText(item))
    .filter(Boolean);
  return names.some((name) => keywords.some((keyword) => {
    const k = normalizeAppMatchText(keyword);
    return k && (name.includes(k) || k.includes(name));
  }));
}


function rankDomainAppCandidates(deviceContext, domain, max = 10) {
  const apps = installedAppsFromDeviceContext(deviceContext);
  if (!apps.length) return [];
  const requiredCapabilities = defaultRequiredCapabilitiesForTask(domain, "");
  const keywords = agentDomainAppKeywords(domain);
  const ranked = [];
  for (const app of apps) {
    let score = 0;
    if (requiredCapabilities.length && appSupportsRequiredCapabilities(app, requiredCapabilities)) {
      score = Math.max(
        score,
        1800 + Math.round(Number(app.capabilityProfile?.confidence || 0) * 500)
      );
    }
    const names = [app.label, app.packageName, ...(app.aliases || [])]
      .map(normalizeAppMatchText)
      .filter(Boolean);
    for (const keyword of keywords) {
      const k = normalizeAppMatchText(keyword);
      if (!k) continue;
      for (const name of names) {
        if (name === k) score = Math.max(score, 1200 + k.length);
        else if (name.includes(k) || k.includes(name)) score = Math.max(score, 900 + Math.min(name.length, k.length));
        else if (name.startsWith(k) || k.startsWith(name)) score = Math.max(score, 760 + Math.min(name.length, k.length));
      }
    }
    if (score > 0) ranked.push({ ...app, score });
  }
  return ranked.sort((a, b) => b.score - a.score).slice(0, max);
}


function inferAgentInstructionDomain(goal) {
  const clean = normalizeForMatch(goal);
  const stockEntityHints = [
    "光电", "信息", "锂业", "科技", "股份", "能源", "电力", "通信", "电子", "医药", "药业", "银行", "证券",
    "保险", "汽车", "材料", "半导体", "茅台", "宁德", "比亚迪", "中芯", "海康", "浪潮", "恒瑞", "隆基",
  ];
  const hasTradeWords = normalizedContainsAny(clean, [
    "下单界面", "下单页面", "交易界面", "交易页面", "委托界面", "委托页面",
    "买入界面", "买入页面", "卖出界面", "卖出页面", "证券下单", "股票下单", "挂单",
  ]);
  const hasStockWords = normalizedContainsAny(clean, ["股票", "股价", "个股", "行情", "走势", "涨跌", "K线", "k线", "分时", "证券", "自选", "详情页", "详情界面"]);
  const hasIndexWords = normalizedContainsAny(clean, ["上证指数", "创业板指", "深证成指", "沪深300", "中证", "指数行情", "大盘"]);
  const hasFinanceNewsWords = normalizedContainsAny(clean, ["头条新闻", "公司新闻", "公告", "研报", "资讯", "财报", "财经新闻"]);
  if (hasTradeWords) return "stock_trade";
  if (hasIndexWords) return "stock_index";
  if (hasStockWords || (normalizedContainsAny(clean, ["详情页", "详情界面", "打开", "查看"]) && stockEntityHints.some((word) => clean.includes(normalizeForMatch(word))))) {
    return hasFinanceNewsWords ? "finance_news" : "stock_detail";
  }
  if (hasFinanceNewsWords && stockEntityHints.some((word) => clean.includes(normalizeForMatch(word)))) return "finance_news";
  if (normalizedContainsAny(clean, ["导航", "路线", "怎么去", "带我去", "开车去", "步行去", "骑行去"])) return "navigation";
  if (normalizedContainsAny(clean, ["播放", "听歌", "歌曲", "歌单", "音乐"])) return "music";
  if (normalizedContainsAny(clean, ["视频", "番剧", "直播", "up主", "哔哩", "b站", "抖音", "快手"])) return "video";
  if (normalizedContainsAny(clean, ["酒店", "机票", "火车票", "高铁票", "旅游", "旅行", "携程", "民宿"])) return "travel";
  return "general";
}


function extractAgentTaskEntity(goal, domain) {
  let text = safeText(goal, 160);
  const removeWords = [
    "帮我", "请", "麻烦", "一下", "打开", "进入", "查看", "看一下", "看", "找到", "搜索", "搜一下", "去到", "跳到", "前往",
    "详情界面", "详情页", "详情", "下单界面", "下单页面", "交易界面", "交易页面", "委托界面", "委托页面",
    "买入界面", "买入页面", "卖出界面", "卖出页面", "页面", "界面", "行情", "走势", "股票", "股价", "证券", "个股", "资讯", "新闻", "头条",
    "播放", "导航到", "导航", "路线", "酒店", "价格",
  ];
  for (const word of removeWords) text = text.replace(new RegExp(escapeRegExp(word), "gi"), "");
  text = text.replace(/[，。,.、:：/\\\-_]+/g, " ").replace(/\s+/g, " ").trim().replace(/的$/u, "");
  if (!text) return "";
  if (["stock_trade", "stock_detail", "finance_news", "stock_index"].includes(domain)) {
    const match = text.match(/[\u4e00-\u9fa5A-Za-z0-9]{2,24}/);
    return safeText(match ? match[0] : text, 32);
  }
  return safeText(text, 48);
}

function buildDomainTaskInterpretation(goal, domain, entity, appCandidates) {
  const appNames = (appCandidates || []).map((app) => `${app.label}${app.packageName ? `(${app.packageName})` : ""}`).slice(0, 6);
  const appLine = appNames.length ? `可用候选 App：${appNames.join(" / ")}。` : "没有可靠 App 候选时，不要猜包名；先利用当前屏幕可见入口，实在不可达再 terminate failure。";
  if (domain === "stock_trade") {
    return [
      `任务理解：用户要打开“${entity || goal}”的证券下单/委托入口，但不是自动提交订单。`,
      appLine,
      "所需能力：stock_search + securities_trade + order_entry。必须优先使用设备上由 App 能力目录确认的真实交易 App；多候选时请求用户选择，禁止根据名称猜测包名。",
      "建议路线：确定性打开已解析的交易 App，验证前台包名后，再由 GUI Plus 搜索目标股票并导航到买入/卖出/委托入口。",
      "安全边界：只允许打开下单界面。任何价格、数量、买卖方向填写以及提交委托都必须重新确认；提交订单不得自动完成。",
      "完成标准：截图主体显示目标股票名称和买入/卖出/委托表单入口，但尚未提交订单时 terminate success。",
    ].join("\n");
  }
  if (domain === "stock_detail") {
    return [
      `任务理解：用户要打开“${entity || goal}”的股票/证券行情详情页。`,
      appLine,
      "建议路线：若当前不在行情/证券 App，优先使用 mobile_use open 打开候选股票行情 App；进入后寻找搜索栏，搜索目标名称，点击正确股票结果进入详情页。",
      "完成标准：截图主体显示目标名称及价格/涨跌幅/分时/K线/盘口等股票详情信息时 terminate success。",
    ].join("\n");
  }
  if (domain === "stock_index") {
    return [
      `任务理解：用户要查看“${entity || goal}”的指数/大盘行情详情页。`,
      appLine,
      "建议路线：优先打开行情/证券 App，搜索或进入指数/大盘入口，点击对应指数结果。",
      "完成标准：截图主体显示指数名称、点位、涨跌幅、分时/K线等行情信息时 terminate success。",
    ].join("\n");
  }
  if (domain === "finance_news") {
    return [
      `任务理解：用户要查看“${entity || goal}”相关财经新闻/资讯。`,
      appLine,
      "建议路线：优先打开财经/行情/新闻类候选 App，搜索目标名称，进入新闻/资讯/公告/头条结果。",
      "完成标准：截图主体显示目标相关新闻列表或新闻详情时 terminate success。",
    ].join("\n");
  }
  if (domain === "navigation") {
    return [
      `任务理解：用户要导航或查路线到“${entity || goal}”。`,
      appLine,
      "建议路线：优先打开地图 App，点击搜索框，输入目的地，选择正确地点。",
      "完成标准：显示目的地路线/地图结果页时 terminate success。",
    ].join("\n");
  }
  if (domain === "music") {
    return [
      `任务理解：用户要播放或搜索音乐“${entity || goal}”。`,
      appLine,
      "建议路线：优先打开音乐 App，搜索目标歌曲/歌手/歌单，进入结果；真正播放前如无风险可点击播放。",
      "完成标准：显示目标歌曲/播放页/搜索结果时 terminate success。",
    ].join("\n");
  }
  if (domain === "video") {
    return [
      `任务理解：用户要打开或搜索视频内容“${entity || goal}”。`,
      appLine,
      "建议路线：优先打开视频/短视频 App，搜索目标内容，点击匹配结果。",
      "完成标准：显示目标视频详情、播放页或搜索结果时 terminate success。",
    ].join("\n");
  }
  if (domain === "travel") {
    return [
      `任务理解：用户要完成旅行/酒店/票务相关查询“${entity || goal}”。`,
      appLine,
      "建议路线：优先打开旅行/酒店 App，根据页面逐步搜索城市、日期、酒店或票务关键词。",
      "完成标准：显示相关价格列表或详情页时 terminate success。",
    ].join("\n");
  }
  return "";
}


function flattenKnownExplicitAppNames(deviceContext, domain = "") {
  const installed = installedAppsFromDeviceContext(deviceContext);
  const names = [];
  const add = (value) => {
    const text = safeText(value, 80);
    const normalized = normalizeAppMatchText(text);
    if (!text || normalized.length < 2) return;
    if (["股票", "证券", "行情", "地图", "音乐", "视频", "酒店", "旅行", "新闻", "资讯", "设置"].includes(text)) return;
    names.push(text);
  };
  installed.forEach((app) => {
    add(app.label);
    (Array.isArray(app.aliases) ? app.aliases : []).forEach(add);
  });
  [
    "同花顺", "东方财富", "雪球", "大智慧", "通达信", "腾讯自选股", "富途牛牛", "老虎证券",
    "高德地图", "百度地图", "腾讯地图",
    "微信", "QQ", "支付宝", "淘宝", "京东", "拼多多",
    "抖音", "快手", "哔哩哔哩", "B站", "小红书",
    "网易云音乐", "QQ音乐", "酷狗音乐", "酷我音乐",
    "携程旅行", "去哪儿旅行", "飞猪", "美团", "大众点评",
  ].forEach(add);
  agentDomainAppKeywords(domain).forEach(add);
  return [...new Set(names)].sort((a, b) => normalizeAppMatchText(b).length - normalizeAppMatchText(a).length).slice(0, 120);
}

function findExplicitAppConstraint(goal, deviceContext, domain = "") {
  // v52：本地不再用“包含某个 App 名”来解释用户意图。
  // 这里只允许非常明确的 App 指定表达通过，例如“用京东 App”“在同花顺里”“打开微信应用”。
  // 像“京东方A”“东方电气”“腾讯控股”这类实体名里包含 App 名时，绝不能被识别为 requiredApp。
  const rawGoal = String(goal || "");
  if (!rawGoal.trim()) return null;
  const installed = installedAppsFromDeviceContext(deviceContext);
  let best = null;

  const isExplicitAppMention = (name) => {
    const label = safeText(name, 80);
    if (!label || normalizeAppMatchText(label).length < 2) return false;
    const escaped = escapeRegExp(label);
    const boundary = "(?:$|[\\s\\u3000，。,.、:：；;!?！？）)】》]|app|App|APP|应用|客户端|里|里面|中|内)";
    const prefix = "(?:^|[\\s\\u3000，。,.、:：；;!?！？（(【《])";
    const patterns = [
      new RegExp(`${prefix}(?:用|使用|通过)\\s*${escaped}\\s*(?:app|App|APP|应用|客户端|里|里面|中|内)?${boundary}`, "i"),
      new RegExp(`${prefix}在\\s*${escaped}\\s*(?:app|App|APP|应用|客户端|里|里面|中|内)${boundary}`, "i"),
      new RegExp(`${prefix}(?:打开|启动|进入|切到|切换到)\\s*${escaped}\\s*(?:app|App|APP|应用|客户端)?${boundary}`, "i"),
      new RegExp(`${prefix}${escaped}\\s*(?:app|App|APP|应用|客户端)${boundary}`, "i"),
    ];
    return patterns.some((pattern) => pattern.test(rawGoal));
  };

  const consider = (candidate, source) => {
    const label = safeText(candidate?.label || candidate?.appName || candidate?.name || "", 80);
    const packageName = safeText(candidate?.packageName || candidate?.package || "", 120);
    const aliases = Array.isArray(candidate?.aliases) ? candidate.aliases.map((item) => safeText(item, 80)).filter(Boolean) : [];
    const names = [label, ...aliases].filter(Boolean);
    for (const name of names) {
      if (!isExplicitAppMention(name)) continue;
      const n = normalizeAppMatchText(name);
      const score = (source === "installed" ? 2000 : 1000) + n.length * 10 + (label && normalizeAppMatchText(label) === n ? 8 : 0);
      if (!best || score > best.score) {
        best = { label: label || name, packageName, aliases, explicitName: name, source, score };
      }
    }
  };

  installed.forEach((app) => consider(app, "installed"));
  flattenKnownExplicitAppNames(deviceContext, domain).forEach((name) => consider({ label: name, aliases: [] }, "known_app_name"));
  if (!best) return null;
  return {
    label: best.label,
    packageName: best.packageName || "",
    aliases: best.aliases || [],
    explicitName: best.explicitName || best.label,
    source: best.source,
    confidence: best.source === "installed" ? 0.92 : 0.78,
  };
}

function removeExplicitAppWordsFromGoal(goal, explicitApp) {
  let text = safeText(goal, 220);
  const names = [
    explicitApp?.explicitName,
    explicitApp?.label,
    ...(Array.isArray(explicitApp?.aliases) ? explicitApp.aliases : []),
  ].filter(Boolean);
  for (const name of names) {
    const clean = safeText(name, 80);
    if (clean) text = text.replace(new RegExp(escapeRegExp(clean), "gi"), " ");
  }
  return text.replace(/\s+/g, " ").trim();
}

function extractContractTaskEntity(goal, domain, explicitApp) {
  const cleanedGoal = explicitApp ? removeExplicitAppWordsFromGoal(goal, explicitApp) : goal;
  return extractAgentTaskEntity(cleanedGoal, domain);
}

function appMatchesTaskContractApp(app, requiredApp) {
  if (!app || !requiredApp) return false;
  const appNames = [app.label, app.packageName, ...(Array.isArray(app.aliases) ? app.aliases : [])].map(normalizeAppMatchText).filter(Boolean);
  const requiredNames = [requiredApp.label, requiredApp.packageName, requiredApp.explicitName, ...(Array.isArray(requiredApp.aliases) ? requiredApp.aliases : [])].map(normalizeAppMatchText).filter(Boolean);
  return appNames.some((name) => requiredNames.some((required) => required && name && (name === required || name.includes(required) || required.includes(name))));
}

function currentAppSatisfiesTaskContract(contract, snapshot, deviceContext) {
  const required = contract?.requiredApp;
  if (!required?.label && !required?.packageName) return true;
  const currentPackage = safeText(snapshot?.packageName || snapshot?.currentApp || deviceContext?.currentApp?.packageName || "", 120);
  const currentLabel = safeText(deviceContext?.currentApp?.label || deviceContext?.currentApp?.name || snapshot?.currentAppLabel || "", 80);
  const currentApp = { label: currentLabel || currentPackage, packageName: currentPackage, aliases: [] };
  if (required.packageName && currentPackage && required.packageName === currentPackage) return true;
  if (appMatchesTaskContractApp(currentApp, required)) return true;
  const installed = installedAppsFromDeviceContext(deviceContext);
  const matchedInstalled = installed.find((app) => app.packageName && currentPackage && app.packageName === currentPackage);
  return Boolean(matchedInstalled && appMatchesTaskContractApp(matchedInstalled, required));
}

function rankTaskContractAppCandidates(deviceContext, domain, explicitApp, max = 10) {
  const domainCandidates = rankDomainAppCandidates(deviceContext, domain, max + 4);
  const out = [];
  const push = (app, reason) => {
    if (!app || (!app.label && !app.packageName)) return;
    const normalizedKey = normalizeAppMatchText(`${app.packageName || ""}|${app.label || ""}`);
    if (out.some((item) => normalizeAppMatchText(`${item.packageName || ""}|${item.label || ""}`) === normalizedKey)) return;
    out.push({ ...app, reason });
  };
  if (explicitApp) {
    const installed = findInstalledAppForOpenApp(explicitApp.label || explicitApp.explicitName, explicitApp.packageName, deviceContext);
    push(installed || explicitApp, "explicit_user_app_constraint");
  }
  domainCandidates.forEach((app) => push(app, "domain_candidate"));
  return out.slice(0, max);
}

function inferTaskContractTargetPage(domain, goal) {
  if (domain === "stock_trade") return "stock_order_entry_page";
  if (domain === "stock_detail") return "stock_detail_page";
  if (domain === "stock_index") return "stock_index_detail_page";
  if (domain === "finance_news") return "finance_news_or_article";
  if (domain === "navigation") return "map_route_or_place_page";
  if (domain === "music") return "music_result_or_player_page";
  if (domain === "video") return "video_result_or_player_page";
  if (domain === "travel") return "travel_result_or_detail_page";
  if (normalizedContainsAny(goal, ["详情页", "详情界面", "详情"])) return "detail_page";
  return "goal_satisfied_page";
}

function buildRuleTaskSemanticContract(goal, snapshot = null, deviceContext = null, agentMemory = null) {
  const assistantHost = isAssistantHostAppPackage(snapshot?.packageName || snapshot?.currentApp || "") || snapshotLooksLikeAssistantChat(snapshot);
  const domain = inferAgentInstructionDomain(goal);
  const explicitApp = findExplicitAppConstraint(goal, deviceContext, domain);
  const targetEntity = extractContractTaskEntity(goal, domain, explicitApp);
  const requiredCapabilities = defaultRequiredCapabilitiesForTask(domain, domain === "stock_trade" ? "open_order_entry" : "");
  const targetAction = domain === "stock_trade"
    ? "open_order_entry"
    : ["stock_detail", "stock_index"].includes(domain)
      ? "open_detail"
      : domain === "navigation"
        ? "navigate"
        : isDomainTask(domain)
          ? "open"
          : "";
  const targetPage = inferTaskContractTargetPage(domain, goal);
  const targetSubPage = domain === "stock_trade"
    ? "order_entry"
    : ["stock_detail", "stock_index"].includes(domain)
      ? "detail_main"
      : "goal_satisfied_page";
  const appCandidates = rankTaskContractAppCandidates(deviceContext, domain, explicitApp, 10);
  const domainRecognized = isDomainTask(domain);
  const completionEvidenceKeywords = [targetEntity].filter(Boolean);
  if (domain === "stock_trade") completionEvidenceKeywords.push("买入", "卖出", "委托", "价格", "数量");
  if (domain === "stock_detail" || domain === "stock_index") completionEvidenceKeywords.push("现价", "涨跌", "分时", "K线", "成交");
  if (domain === "navigation") completionEvidenceKeywords.push("路线", "导航", "驾车", "步行");

  return {
    schema: domainRecognized
      ? "agent_task_semantic_contract_v73_deterministic_capability_fallback"
      : "agent_task_semantic_contract_v73_neutral_fallback",
    sourceGoal: safeText(goal, 240),
    domain: domainRecognized ? domain : "general",
    targetEntity: domainRecognized ? targetEntity : "",
    targetKind: domainRecognized ? domain : "",
    targetPage: domainRecognized ? targetPage : "goal_satisfied_page",
    targetSubPage: domainRecognized ? targetSubPage : "goal_satisfied_page",
    targetAction: domainRecognized ? targetAction : "",
    requiredCapabilities: domainRecognized ? requiredCapabilities : [],
    requiredApp: explicitApp || null,
    explicitAppRequired: Boolean(explicitApp),
    allowAlternativeApp: !explicitApp,
    appCandidates,
    current: {
      requiredAppActive: explicitApp ? currentAppSatisfiesTaskContract({ requiredApp: explicitApp }, snapshot, deviceContext) : false,
      assistantHost,
      entityVisible: Boolean(targetEntity && normalizeForMatch(collectSnapshotEvidenceText(snapshot)).includes(normalizeForMatch(targetEntity))),
      completionLike: false,
      phase: "observe",
    },
    completionCriteria: domainRecognized
      ? buildDomainTaskInterpretation(goal, domain, targetEntity, appCandidates).split("\n").filter(Boolean).slice(-2)
      : [],
    completionEvidenceKeywords: domainRecognized ? completionEvidenceKeywords : [],
    safeNavigationActions: domainRecognized ? ["open_app", "tap_node", "tap_xy", "input_text", "scroll", "swipe", "back", "wait"] : [],
    dangerousActions: domain === "stock_trade" ? ["submit_order", "confirm_buy", "confirm_sell"] : [],
    forbiddenActions: [
      "不要把 AI 助手聊天气泡、历史消息或底部输入框当作外部任务目标。",
      "任何 packageName 必须来自设备安装清单或设备解析结果。",
    ],
    confidence: domainRecognized ? 0.76 : 0.1,
    contractSource: domainRecognized ? "deterministic_capability_fallback" : "neutral_local_fallback",
    modelReason: domainRecognized
      ? "TaskContractJudge 不可用时，使用可验证的领域、能力和设备应用清单规则维持任务链路。"
      : "无法可靠判断任务领域，保持中性合同。",
  };
}

function normalizeTaskContractSubPage(value) {
  const raw = String(value || "").trim().toLowerCase().replace(/[\s\-]+/g, "_");
  const mapped = {
    comments: "comment_community",
    comment: "comment_community",
    community: "comment_community",
    discussion: "comment_community",
    forum: "comment_community",
    stock_forum: "comment_community",
    chat: "chat_thread",
    conversation: "chat_thread",
    profile: "profile_page",
    detail: "detail_main",
    main: "detail_main",
    news: "news_tab",
    announcement: "announcement_tab",
  };
  return mapped[raw] || raw || "detail_main";
}

function normalizeTaskContractJudgeMode(value) {
  const raw = String(value || "").trim().toLowerCase().replace(/[\s\-]+/g, "_");
  if (["0", "false", "off", "none", "disable", "disabled", "rule", "rules"].includes(raw)) return "rule";
  if (["always", "force", "model", "llm", "semantic"].includes(raw)) return "always";
  return "adaptive";
}

function shouldUseTaskContractJudge() {
  // Two-brain architecture: semantic routing belongs to DeepSeek AgentBrain.
  // No keyword/regex task-contract judge is allowed in the visual execution chain.
  return false;
}

function taskContractJudgeSnapshotPayload(snapshot) {
  return {
    app: safeText(snapshot?.currentApp || snapshot?.packageName || "", 100),
    packageName: safeText(snapshot?.packageName || snapshot?.currentApp || "", 100),
    nodeCount: Number(snapshot?.nodeCount || 0),
    texts: (Array.isArray(snapshot?.texts) ? snapshot.texts : []).map((item) => safeText(item, 60)).filter(Boolean).slice(0, 18),
    clickableTexts: (Array.isArray(snapshot?.clickableNodes) ? snapshot.clickableNodes : []).map((node) => safeText(node?.text || "", 60)).filter(Boolean).slice(0, 18),
    inputTexts: (Array.isArray(snapshot?.inputNodes) ? snapshot.inputNodes : []).map((node) => safeText(node?.text || "", 60)).filter(Boolean).slice(0, 6),
  };
}


function normalizeTaskContractJudgeDecision(value, fallbackContract) {
  const raw = value && typeof value === "object" ? value : {};
  const nested = raw.taskSemanticContract || raw.semanticTaskContract || raw.taskContract || raw.contract || raw.result || raw;
  const fallback = fallbackContract && typeof fallbackContract === "object" ? fallbackContract : {};
  const requiredRaw = nested.requiredApp && typeof nested.requiredApp === "object" ? nested.requiredApp : fallback.requiredApp;
  const requiredApp = requiredRaw && (requiredRaw.label || requiredRaw.packageName || requiredRaw.explicitName)
    ? {
        label: safeText(requiredRaw.label || requiredRaw.appName || requiredRaw.name || fallback.requiredApp?.label || "", 80),
        packageName: safeText(requiredRaw.packageName || requiredRaw.package || fallback.requiredApp?.packageName || "", 120),
        explicitName: safeText(requiredRaw.explicitName || requiredRaw.label || requiredRaw.appName || fallback.requiredApp?.explicitName || "", 80),
        aliases: Array.isArray(requiredRaw.aliases) ? requiredRaw.aliases.map((item) => safeText(item, 80)).filter(Boolean).slice(0, 8) : (fallback.requiredApp?.aliases || []),
        source: safeText(requiredRaw.source || "task_contract_judge", 80),
        confidence: Math.max(0, Math.min(1, Number(requiredRaw.confidence || fallback.requiredApp?.confidence || 0.8))),
      }
    : null;
  const targetAction = safeText(
    nested.targetAction || nested.desiredAction || nested.action || fallback.targetAction || "",
    80
  ).trim().toLowerCase().replace(/[\s\-]+/g, "_");
  let domain = safeText(nested.domain || fallback.domain || "general", 60) || "general";
  if (
    domain === "stock_detail" &&
    ["open_order_entry", "order_entry", "place_order", "buy", "sell"].includes(targetAction)
  ) {
    domain = "stock_trade";
  }
  const requiredCapabilities = normalizeRequiredCapabilities(
    nested.requiredCapabilities ||
      nested.requiredAppCapabilities ||
      nested.capabilities ||
      fallback.requiredCapabilities ||
      defaultRequiredCapabilitiesForTask(domain, targetAction)
  );
  const targetPage = safeText(nested.targetPage || fallback.targetPage || inferTaskContractTargetPage(domain, fallback.sourceGoal || ""), 80);
  const targetSubPage = normalizeTaskContractSubPage(nested.targetSubPage || nested.subPage || fallback.targetSubPage || "");
  const completionCriteria = Array.isArray(nested.completionCriteria) && nested.completionCriteria.length
    ? nested.completionCriteria.map((item) => safeText(item, 120)).filter(Boolean).slice(0, 10)
    : (Array.isArray(fallback.completionCriteria) ? fallback.completionCriteria : []);
  const completionEvidenceKeywords = Array.isArray(nested.completionEvidenceKeywords)
    ? nested.completionEvidenceKeywords.map((item) => safeText(item, 40)).filter(Boolean).slice(0, 16)
    : Array.isArray(nested.completionEvidence)
      ? nested.completionEvidence.map((item) => safeText(item, 40)).filter(Boolean).slice(0, 16)
      : Array.isArray(fallback.completionEvidenceKeywords) ? fallback.completionEvidenceKeywords : [];
  const safeNavigationActions = [];
  const dangerousActions = [];
  const current = nested.current && typeof nested.current === "object" ? nested.current : fallback.current || {};
  const confidenceRaw = Number(nested.confidence ?? nested.score ?? fallback.confidence ?? 0.65);
  const confidence = Number.isFinite(confidenceRaw) ? Math.max(0, Math.min(1, confidenceRaw)) : 0.65;
  const nestedAppCandidates = Array.isArray(nested.appCandidates)
    ? nested.appCandidates
        .map((item) => {
          if (!item) return null;
          if (typeof item === "string") return { label: safeText(item, 80), packageName: "", source: "task_contract_judge" };
          if (typeof item !== "object") return null;
          const label = safeText(item.label || item.appName || item.name || item.title || "", 80);
          const packageName = safeText(item.packageName || item.package || "", 120);
          if (!label && !packageName) return null;
          return {
            label: label || packageName,
            packageName,
            aliases: Array.isArray(item.aliases) ? item.aliases.map((alias) => safeText(alias, 80)).filter(Boolean).slice(0, 8) : [],
            source: safeText(item.source || "task_contract_judge", 80),
            confidence: Math.max(0, Math.min(1, Number(item.confidence || item.score || 0.7))),
          };
        })
        .filter(Boolean)
        .slice(0, 10)
    : [];
  const fallbackAppCandidates = Array.isArray(fallback.appCandidates) ? fallback.appCandidates : [];

  return {
    schema: "agent_task_semantic_contract_v13_capabilities",
    sourceGoal: safeText(nested.sourceGoal || fallback.sourceGoal || "", 240),
    domain,
    targetEntity: safeText(nested.targetEntity || nested.entity || fallback.targetEntity || "", 80),
    targetKind: safeText(nested.targetKind || fallback.targetKind || "", 100),
    targetPage,
    targetSubPage,
    targetAction,
    requiredCapabilities,
    requiredApp,
    explicitAppRequired: Boolean(requiredApp && (fallback.explicitAppRequired || nested.explicitAppRequired !== false)),
    allowAlternativeApp: requiredApp ? false : nested.allowAlternativeApp !== false,
    appCandidates: nestedAppCandidates.length ? nestedAppCandidates : fallbackAppCandidates,
    current: {
      requiredAppActive: Boolean(current.requiredAppActive),
      assistantHost: Boolean(current.assistantHost),
      entityVisible: Boolean(current.entityVisible),
      completionLike: Boolean(current.completionLike),
      phase: safeText(current.phase || fallback.current?.phase || "observe", 80),
    },
    completionCriteria,
    completionEvidenceKeywords,
    safeNavigationActions,
    dangerousActions,
    forbiddenActions: Array.isArray(fallback.forbiddenActions) ? fallback.forbiddenActions : [],
    confidence,
    contractSource: safeText(nested.contractSource || nested.source || "task_contract_judge", 80),
    modelReason: safeText(nested.reason || nested.rationale || "", 260),
  };
}

function inferRuleTargetSubPage(goal) {
  return "detail_main";
}

function enrichRuleContractWithSubPage(contract, goal) {
  if (!contract || typeof contract !== "object") return contract;
  return {
    ...contract,
    schema: contract.schema || "agent_task_semantic_contract_v13_capabilities_rule_fallback",
    targetSubPage: contract.targetSubPage || "detail_main",
    safeNavigationActions: [],
    dangerousActions: [],
    contractSource: contract.contractSource || "rule_fallback_enriched_no_keywords",
  };
}

function buildTaskSemanticContract(goal, snapshot = null, deviceContext = null, agentMemory = null) {
  const memoryContract = agentMemory && typeof agentMemory === "object"
    ? (agentMemory.taskSemanticContract || agentMemory.semanticTaskContract)
    : null;
  if (memoryContract && typeof memoryContract === "object" && memoryContract.contractSource === "deepseek_agent_brain") {
    return memoryContract;
  }
  return {
    schema: "agent_task_context_v2_two_brain",
    sourceGoal: safeText(goal, 240),
    domain: "general",
    targetEntity: "",
    targetKind: "",
    targetPage: "goal_satisfied_page",
    targetSubPage: "goal_satisfied_page",
    targetAction: "",
    requiredCapabilities: [],
    requiredApp: null,
    explicitAppRequired: false,
    allowAlternativeApp: true,
    appCandidates: [],
    current: {
      requiredAppActive: false,
      assistantHost: isAssistantHostAppPackage(snapshot?.packageName || snapshot?.currentApp || "") || snapshotLooksLikeAssistantChat(snapshot),
      entityVisible: false,
      completionLike: false,
      phase: "observe",
    },
    completionCriteria: [],
    completionEvidenceKeywords: [],
    safeNavigationActions: [],
    dangerousActions: [],
    forbiddenActions: [
      "不要把 AI 助手聊天气泡、历史消息或底部输入框当作外部任务目标。",
      "任何 packageName 必须来自 Android 上传的 canonical installedApps。",
    ],
    confidence: 0,
    contractSource: "disabled_two_brain_architecture",
    modelReason: "DeepSeek AgentBrain owns semantic routing; GUI Plus owns visual task understanding.",
  };
}


function buildTaskContractJudgeMessages(goal, snapshot, deviceContext, fallbackContract) {
  const payload = {
    goal: safeText(goal, 240),
    currentScreen: taskContractJudgeSnapshotPayload(snapshot),
    device: deviceContextSummaryForPrompt(deviceContext),
    neutralFallback: fallbackContract || null,
    task: "Parse semantic requirements first. Declare required app capabilities; the Android device resolves the actual installed app.",
  };
  const system = [
    "你是 Android 手机智能体的 TaskContractJudge，属于内部控制主脑的一部分，只输出严格 JSON。",
    "先理解用户真实意图，再写任务契约；禁止照抄本地 fallback，禁止被股票名、公司名、人名、地点名中的 App 名子串带偏。",
    "requiredApp 只能在用户明确指定 App 时填写。用户未指定 App 时，requiredApp 必须为 null，由 requiredCapabilities 描述需要的能力。",
    "必须区分 stock_detail 与 stock_trade：查看行情/详情属于 stock_detail；打开买入、卖出、委托、下单界面属于 stock_trade。",
    "stock_trade 只表示导航到下单入口，不代表填写或提交订单。targetAction=open_order_entry；requiredCapabilities 至少包含 stock_search、securities_trade、order_entry。",
    "查看股票详情时 requiredCapabilities 使用 stock_quote、stock_search。导航任务使用 navigation。",
    "不要编造 packageName。真实目标 App 必须由 Android 上传的 targetAppResolution 或 installedApps capabilityProfile 解析。",
    "你只描述任务契约，不负责点击坐标；风险由 ActionSafetyJudge 判断候选动作后果。",
    "输出 JSON：{\"taskSemanticContract\":{\"sourceGoal\":\"\",\"domain\":\"stock_trade|stock_detail|stock_index|finance_news|chat|social|navigation|music|video|travel|system_control|general\",\"targetEntity\":\"\",\"targetKind\":\"\",\"targetPage\":\"\",\"targetSubPage\":\"detail_main|order_entry|comment_community|news_tab|chat_thread|profile_page|goal_satisfied_page\",\"targetAction\":\"open_order_entry|open_detail|navigate|open|\",\"requiredCapabilities\":[\"\"],\"requiredApp\":null,\"explicitAppRequired\":false,\"allowAlternativeApp\":true,\"appCandidates\":[],\"completionCriteria\":[\"\"],\"completionEvidenceKeywords\":[\"\"],\"confidence\":0.0,\"reason\":\"\"}}",
  ].join("\n");
  return [
    { role: "system", content: system },
    { role: "user", content: JSON.stringify(payload) },
  ];
}

async function judgeTaskSemanticContract(goal, snapshot, deviceContext, agentMemory, startedAt) {
  // Disabled by design. DeepSeek AgentBrain is the only semantic router.
  return buildTaskSemanticContract(goal, snapshot, deviceContext, agentMemory);
}


function taskSemanticContractPromptBlock(contract) {
  if (!contract || typeof contract !== "object") return "Task semantic contract: unavailable. Use original goal and current screenshot.";
  const source = String(contract.contractSource || contract.source || "").toLowerCase();
  const neutral = source.includes("neutral") || source.includes("rule_fallback");
  const required = neutral ? null : contract.requiredApp;
  const candidateLine = (contract.appCandidates || [])
    .slice(0, 6)
    .map((app, index) => `${index + 1}. ${app.label || app.appName || "未知"}${app.packageName ? `(${app.packageName})` : ""}`)
    .join(" / ") || "无";

  if (neutral && !contract.targetEntity && !required) {
    return [
      "Task semantic contract: neutral local fallback only.",
      `- Original goal: ${contract.sourceGoal || ""}`,
      "- Local backend did not interpret domain/entity/requiredApp. GUI/AgentBrain must infer from original goal + screenshot.",
      `- Candidate apps from device context: ${candidateLine}`,
      "- Do not treat this fallback as a hard route constraint.",
    ].join("\n");
  }

  return [
    "Task semantic contract (from AgentBrain/TaskContractJudge; obey unless runtime screenshot clearly contradicts it):",
    `- Original goal: ${contract.sourceGoal || ""}`,
    `- Domain/target: ${contract.domain || "general"} / ${contract.targetEntity || "未指定"} / ${contract.targetPage || "goal_satisfied_page"} / sub=${contract.targetSubPage || "goal_satisfied_page"}`,
    required
      ? `- Required app: ${required.label}${required.packageName ? `(${required.packageName})` : ""}. This is a hard user constraint only because AgentBrain judged it explicit.`
      : `- Required app: none explicit. Candidate apps: ${candidateLine}`,
    `- Current phase: ${contract.current?.phase || "observe"}; requiredAppActive=${Boolean(contract.current?.requiredAppActive)}; entityVisible=${Boolean(contract.current?.entityVisible)}; completionLike=${Boolean(contract.current?.completionLike)}.`,
    `- Completion criteria: ${(contract.completionCriteria || []).join("；") || "视觉上确认任务目标完成"}`,
    `- Completion evidence keywords from contract: ${(contract.completionEvidenceKeywords || []).join(" / ") || "由视觉模型判断"}`,
    `- Forbidden actions: ${(contract.forbiddenActions || []).join("；")}`,
    "Use this contract as semantic guidance; Android runtime feedback and current screenshot still have priority for execution validity.",
  ].join("\n");
}

function compactActionTargetsApp(compact, targetApp) {
  if (!compact || !targetApp) return false;
  const action = String(compact.a || compact.action || compact.type || "").toLowerCase().trim().replace(/-/g, "_");
  if (action !== "open_app" && action !== "open") return false;
  const candidate = {
    label: safeText(compact.appName || compact.app || compact.t || compact.targetText || "", 100),
    packageName: safeText(compact.packageName || compact.package || "", 120),
    aliases: [],
  };
  return appMatchesTaskContractApp(candidate, targetApp);
}

function buildTaskContractOpenRequiredAppPlan(contract, reason, confidence = 0.92) {
  const app = contract?.requiredApp || null;
  if (!app?.label && !app?.packageName) return null;
  const appName = app.label || app.explicitName || "目标 App";
  const fullReason = safeText(reason || `任务契约要求先打开指定 App：${appName}`, 260);
  return {
    agentState: {
      isComplete: false,
      expectedProgress: true,
      isWrong: false,
      confidence,
      reason: fullReason,
      nextHint: `打开 ${appName} 后继续执行目标 ${contract?.targetEntity || contract?.sourceGoal || ""}`,
    },
    visualFrame: {
      pageTitle: "任务契约：打开指定 App",
      pageType: "task_contract_open_required_app",
      summary: fullReason,
      isComplete: false,
      isWrong: false,
      targetVisible: false,
      targetText: appName,
      suggestedAction: { type: "open_app", targetText: appName, reason: fullReason },
      completionEvidence: "",
      reason: fullReason,
      confidence,
      taskContract: contract,
    },
    agentStep: {
      type: "open_app",
      appName,
      packageName: app.packageName || undefined,
      targetText: appName,
      reason: fullReason,
      riskLevel: "low",
      requiresConfirmation: false,
    },
    stopConditions: ["visual_after_system_action"],
    guiPlusCompact: {
      s: "p",
      a: "open_app",
      appName,
      packageName: app.packageName || undefined,
      t: appName,
      c: confidence,
      e: fullReason,
    },
    taskSemanticContract: contract,
    guardReason: "task_contract_required_app",
    sourceDetail: "guarded_task_contract_required_app",
  };
}

function taskContractCompletionSatisfied(contract, snapshot, deviceContext) {
  if (!contract || typeof contract !== "object") return false;
  const currentPackage = safeText(snapshot?.packageName || snapshot?.currentApp || deviceContext?.currentApp?.packageName || "", 120);
  if (isAssistantHostAppPackage(currentPackage) || snapshotLooksLikeAssistantChat(snapshot)) return false;
  if (contract.requiredApp && !currentAppSatisfiesTaskContract(contract, snapshot, deviceContext)) return false;

  const evidence = collectSnapshotEvidenceText(snapshot);
  const cleanEvidence = normalizeForMatch(evidence);
  if (!cleanEvidence) return false;
  const entity = normalizeForMatch(contract.targetEntity || "");
  if (entity && !cleanEvidence.includes(entity)) return false;

  const explicitEvidence = Array.isArray(contract.completionEvidenceKeywords)
    ? contract.completionEvidenceKeywords.filter(Boolean)
    : [];
  if (explicitEvidence.length && !evidenceHasAny(evidence, explicitEvidence)) return false;

  const domain = safeText(contract.domain || "general", 60);
  const action = safeText(contract.targetAction || "", 80).toLowerCase();
  if (domain === "stock_trade" || ["open_order_entry", "order_entry", "place_order", "buy", "sell"].includes(action)) {
    return evidenceHasAny(evidence, ["买入", "卖出", "委托", "下单", "价格", "数量", "可用资金", "交易"]);
  }
  if (domain === "stock_detail" || domain === "stock_index") {
    return evidenceHasAny(evidence, ["现价", "涨跌", "涨幅", "跌幅", "分时", "K线", "成交", "行情"]);
  }
  if (domain === "navigation") {
    return evidenceHasAny(evidence, ["路线", "导航", "开始导航", "驾车", "步行", "骑行"]);
  }
  if (domain === "video") return evidenceHasAny(evidence, ["播放", "暂停", "全屏", "选集"]);
  if (domain === "music") return evidenceHasAny(evidence, ["播放", "暂停", "歌词", "歌曲"]);

  const skill = agentSkillForDomain(domain);
  if (skill && Array.isArray(skill.completionWords) && skill.completionWords.length) {
    return evidenceHasAny(evidence, skill.completionWords);
  }
  return Boolean(entity && cleanEvidence.includes(entity) && evidence.length >= 4);
}

function guardTaskSemanticContractPlan(parsed, goal, snapshot, supportedSteps, screenshotInfo, deviceContext, agentMemory, recentActions) {
  const compact = parsed?.guiPlusCompact || parsed?.compactVision || parsed || {};
  const contract = buildTaskSemanticContract(goal, snapshot, deviceContext, agentMemory);
  const source = String(contract?.contractSource || contract?.source || "").toLowerCase();
  const confidence = Number(contract?.confidence || 0);
  const required = contract?.requiredApp;

  // v52：只有 AgentBrain/TaskContractJudge 高置信输出的显式 requiredApp 才能成为硬护栏。
  // 本地 neutral/rule fallback 不能强制打开某个 App，避免“京东方A -> 京东”这类子串误判。
  const trustedRequiredApp = Boolean(
    required &&
      !source.includes("neutral") &&
      !source.includes("rule_fallback") &&
      contract.explicitAppRequired !== false &&
      confidence >= 0.68
  );
  if (!trustedRequiredApp) return parsed;

  const action = String(compact.a || compact.action || compact.type || "").toLowerCase().trim().replace(/-/g, "_");
  const currentRequired = currentAppSatisfiesTaskContract(contract, snapshot, deviceContext);
  const safeSystemActions = ["back", "home", "recents", "wait", "need_user_help"];
  if ((action === "open_app" || action === "open") && !compactActionTargetsApp(compact, required)) {
    const target = safeText(compact.appName || compact.packageName || compact.t || compact.targetText || "其他 App", 80);
    return buildTaskContractOpenRequiredAppPlan(
      contract,
      `任务契约拦截：AgentBrain 判断用户明确指定使用 ${required.label}，但 GUI Plus 计划打开 ${target || "其他 App"}；必须回到指定 App。`,
      0.9
    );
  }

  if (!currentRequired && !compactActionTargetsApp(compact, required) && !safeSystemActions.includes(action)) {
    return buildTaskContractOpenRequiredAppPlan(
      contract,
      `任务契约拦截：当前不在 AgentBrain 指定 App ${required.label}，不能继续执行 ${action || "unknown"}；必须先打开指定 App。`,
      0.86
    );
  }

  if ((action === "finish" || action === "terminate" || compact.s === "d") && !taskContractCompletionSatisfied(contract, snapshot, deviceContext)) {
    const replan = buildGuardedNeedReplanPlan(
      `任务契约拦截：未满足 AgentBrain 完成标准，不能结束。必须满足：${(contract.completionCriteria || []).join("；")}`,
      0.76
    );
    replan.guardReason = "task_contract_premature_finish";
    replan.sourceDetail = "guarded_agentbrain_task_contract_premature_finish";
    replan.taskSemanticContract = contract;
    return replan;
  }

  return parsed;
}


function guiPlusVisualBrainInstalledAppsPrompt(deviceContext, max = 80) {
  const apps = installedAppsFromDeviceContext(deviceContext)
    .slice(0, max)
    .map((app, index) => `${index + 1}. ${app.label}${app.packageName ? `(${app.packageName})` : ""}${Array.isArray(app.aliases) && app.aliases.length ? ` aliases=${app.aliases.slice(0, 3).join("/")}` : ""}`);
  return apps.length ? apps.join("\n") : "No installed app list was provided by Android. If the user names an app, use mobile_use open with the app name from the instruction.";
}

function buildEnhancedGuiPlusInstruction(goal, snapshot, deviceContext, agentMemory = null) {
  const compactDevice = deviceContextSummaryForPrompt(deviceContext);
  const currentApp = safeText(snapshot?.currentApp || snapshot?.packageName || compactDevice.currentApp?.packageName || "", 100);
  const currentPackage = safeText(snapshot?.packageName || snapshot?.currentApp || compactDevice.currentApp?.packageName || "", 120);
  const loopSignals = agentMemory && typeof agentMemory === "object" ? (agentMemory.loopSignals || {}) : {};
  const runtimeHint = runtimeVerificationHintForPrompt(agentMemory);
  const deepThinking = adaptiveDeepThinkingDecision(goal, snapshot, deviceContext, agentMemory, []);
  const assistantHost = isAssistantHostAppPackage(currentPackage) || snapshotLooksLikeAssistantChat(snapshot);
  const controllerHandoffActive = isControllerHandoffSurface(null, snapshot, deviceContext, agentMemory);
  const lines = [
    `Original user instruction: ${safeText(goal, 240)}`,
    `Current foreground app/package: ${currentApp || "unknown"}`,
    "You are GUI Plus, the visual-control agent brain. You must understand the full user instruction yourself, inspect the screenshot, use the installed app list, plan the next UI action, and output exactly one mobile_use action.",
    "DeepSeek/AgentBrain is only for internal-control or cross-tool routing. For visual-control tasks, do not wait for DeepSeek or local rules to interpret the sentence for you.",
    "Local Android/backend rules are runtime/protocol guards only. They must not reinterpret or override the original instruction.",
    "If the current screen is unrelated to the instruction, you should recover by mobile_use open / Back / Home rather than clicking random UI.",
    "If the user instruction explicitly names an app, and the current foreground app is not that app, the normal first visual-agent action is mobile_use open with that app name. Example: '打开 QQ 设置页' from AI Assistant screen => open 'QQ', then continue inside QQ after the next screenshot.",
    "If the instruction is an implicit domain task such as stock detail, map route, music, video, travel, shopping page, or app setting, infer the suitable app from the instruction and installed app list. Use mobile_use open when the target app is not already foreground.",
    "Do not type the full user instruction into the current page. For input tasks, first click the visible search/input field and wait for focus; only type when the screenshot shows focused input, caret, keyboard, or an active search page ready for typing.",
    "Do not terminate success just because the user's chat bubble or assistant reply contains the goal text. Historical chat messages are not task completion evidence.",
    "Installed apps available to Android:",
    guiPlusVisualBrainInstalledAppsPrompt(deviceContext, 90),
    "Runtime state:",
    `- assistantHost=${assistantHost}; controllerHandoffActive=${controllerHandoffActive}; executedStepCount=${Number(loopSignals.executedStepCount || 0)}; loopIndex=${Number(loopSignals.loopIndex || 0)}; noProgress=${Number(loopSignals.noProgressCount || 0)}`,
    `- coordinate protocol: mobile_use 1000x1000; Android maps it to the real screen.`,
  ];
  if (runtimeHint) {
    lines.push(
      "Android runtime verification feedback:",
      runtimeHint,
      "If Android reports a no-progress/blocked action, change route, target element, or app entry. Do not repeat the exact blocked action."
    );
  }
  if (assistantHost) {
    lines.push(
      "The current screenshot appears to be the AI Assistant/chat host. Chat bubbles, assistant cards, copy/retry buttons, and the bottom composer are not the target app UI.",
      "For external visual tasks, your first action should normally be mobile_use open for the intended app or a suitable app, not typing/clicking inside the assistant."
    );
  }
  if (controllerHandoffActive) {
    lines.push(
      "Controller handoff is a routing stage, not a task page and not a loading state. Transient host text such as preparing/generating a reply must be ignored.",
      "At controller handoff, action=wait is invalid. Select the intended canonical installed app and output mobile_use open; do not click, type, swipe, Back, Home, answer or terminate on the controller surface."
    );
  }
  lines.push(
    "Completion rule: use terminate success only when the screenshot clearly shows the requested final page/state. Otherwise continue with the next UI action.",
    "Adaptive deep thinking status:",
    deepThinkingPromptBlock(deepThinking)
  );
  return lines.join("\n").slice(0, 4200);
}



function isAssistantHostAppPackage(packageName) {
  const clean = normalizeForMatch(packageName);
  return Boolean(
    clean.includes("comyuchenailedger") ||
      clean.includes("ailedger") ||
      clean.includes("aiassistant") ||
      clean.includes("chatgpt")
  );
}

function snapshotLooksLikeAssistantChat(snapshot) {
  const texts = [
    ...(Array.isArray(snapshot?.texts) ? snapshot.texts : []),
    ...(Array.isArray(snapshot?.clickableNodes) ? snapshot.clickableNodes.map((node) => node?.text || "") : []),
  ].map((item) => normalizeForMatch(item)).join("|");
  return Boolean(
    texts.includes("ai助手") ||
      texts.includes("ai智能体") ||
      texts.includes("正在整理回复") ||
      texts.includes("和我说点什么") ||
      texts.includes("停止本次任务") ||
      texts.includes("复制重试")
  );
}

function isDomainTask(domain) {
  return ["stock_trade", "stock_detail", "stock_index", "finance_news", "navigation", "music", "video", "travel"].includes(domain);
}


const AGENT_TASK_SKILL_REGISTRY = {
  stock_trade: {
    label: "证券下单入口",
    targetKind: "证券买入/卖出/委托入口",
    preferredRecovery: "resolve_trade_app_then_open_and_search",
    completionWords: ["买入", "卖出", "委托", "下单", "价格", "数量", "可用资金", "持仓"],
    searchWords: ["搜索", "股票", "代码", "名称", "交易", "买入", "卖出"],
    forbiddenOnAssistant: "禁止在 AI 助手聊天页输入交易目标；禁止自动填写价格、数量或提交委托。",
  },
  stock_detail: {
    label: "股票详情页",
    targetKind: "股票/证券行情详情页",
    preferredRecovery: "open_stock_app_then_search",
    completionWords: ["分时", "K线", "k线", "盘口", "五档", "成交", "涨跌幅", "现价", "最新价", "买入", "卖出", "自选"],
    searchWords: ["搜索", "股票", "代码", "名称", "自选", "行情"],
    forbiddenOnAssistant: "禁止点击聊天气泡里的股票名称；那只是历史指令。",
  },
  stock_index: {
    label: "指数行情页",
    targetKind: "指数/大盘行情详情页",
    preferredRecovery: "open_stock_app_then_search_index",
    completionWords: ["分时", "K线", "k线", "大盘", "指数", "点位", "涨跌幅", "成交额"],
    searchWords: ["搜索", "指数", "行情", "大盘"],
    forbiddenOnAssistant: "禁止点击聊天气泡里的指数名称；那只是历史指令。",
  },
  finance_news: {
    label: "财经资讯页",
    targetKind: "财经新闻/资讯页",
    preferredRecovery: "open_finance_app_then_search_news",
    completionWords: ["新闻", "资讯", "公告", "研报", "财报", "头条", "快讯", "要闻"],
    searchWords: ["搜索", "资讯", "新闻", "公告", "研报"],
    forbiddenOnAssistant: "禁止点击聊天气泡里的新闻标题；那只是历史指令。",
  },
  navigation: {
    label: "地图导航页",
    targetKind: "地图路线/目的地结果页",
    preferredRecovery: "open_map_app_then_search_destination",
    completionWords: ["路线", "导航", "公交", "驾车", "步行", "骑行", "到这去", "目的地"],
    searchWords: ["搜索", "去哪", "目的地", "地点", "路线"],
    forbiddenOnAssistant: "禁止点击聊天气泡里的地点名；那只是历史指令。",
  },
  music: {
    label: "音乐搜索/播放页",
    targetKind: "音乐搜索结果或播放页",
    preferredRecovery: "open_music_app_then_search",
    completionWords: ["播放", "歌曲", "歌手", "专辑", "歌单", "暂停", "歌词"],
    searchWords: ["搜索", "歌曲", "歌手", "音乐"],
    forbiddenOnAssistant: "禁止点击聊天气泡里的歌曲名；那只是历史指令。",
  },
  video: {
    label: "视频搜索/播放页",
    targetKind: "视频搜索结果或播放页",
    preferredRecovery: "open_video_app_then_search",
    completionWords: ["播放", "视频", "番剧", "直播", "关注", "弹幕", "相关推荐"],
    searchWords: ["搜索", "视频", "影视", "直播"],
    forbiddenOnAssistant: "禁止点击聊天气泡里的视频标题；那只是历史指令。",
  },
  travel: {
    label: "旅行/票务查询页",
    targetKind: "旅行酒店票务查询页",
    preferredRecovery: "open_travel_app_then_query",
    completionWords: ["酒店", "机票", "火车票", "价格", "入住", "出发", "到达", "预订", "筛选"],
    searchWords: ["搜索", "目的地", "城市", "酒店", "车票", "机票"],
    forbiddenOnAssistant: "禁止点击聊天气泡里的地点或酒店名；那只是历史指令。",
  },
};

function agentSkillForDomain(domain) {
  return AGENT_TASK_SKILL_REGISTRY[domain] || null;
}

function agentRuntimeTaskInfo(goal, snapshot = null, deviceContext = null, agentMemory = null) {
  const contract = buildTaskSemanticContract(goal, snapshot, deviceContext, agentMemory);
  const domain = safeText(contract.domain || "general", 60) || "general";
  const entity = safeText(contract.targetEntity || "", 80);
  const skill = agentSkillForDomain(domain);
  const appCandidates = Array.isArray(contract.appCandidates) ? contract.appCandidates.slice(0, 10) : [];
  const currentPackage = safeText(snapshot?.packageName || snapshot?.currentApp || "", 120);
  const assistantHost = isAssistantHostAppPackage(currentPackage) || snapshotLooksLikeAssistantChat(snapshot);
  const currentIsCandidate = contract.requiredApp
    ? currentAppSatisfiesTaskContract(contract, snapshot, deviceContext)
    : false;
  const progress = scoreAgentTaskProgress(goal, snapshot, domain, entity, deviceContext);
  const loopSignals = agentMemory && typeof agentMemory === "object" ? (agentMemory.loopSignals || {}) : {};
  return { domain, entity, skill, appCandidates, currentPackage, assistantHost, currentIsCandidate, progress, loopSignals, contract };
}

function collectSnapshotEvidenceText(snapshot) {
  const values = [];
  const add = (value) => {
    const text = safeText(value, 80);
    if (text) values.push(text);
  };
  (Array.isArray(snapshot?.texts) ? snapshot.texts : []).forEach(add);
  (Array.isArray(snapshot?.allNodes) ? snapshot.allNodes : []).forEach((node) => add(node?.text));
  (Array.isArray(snapshot?.clickableNodes) ? snapshot.clickableNodes : []).forEach((node) => add(node?.text));
  (Array.isArray(snapshot?.inputNodes) ? snapshot.inputNodes : []).forEach((node) => add(node?.text));
  (Array.isArray(snapshot?.scrollableNodes) ? snapshot.scrollableNodes : []).forEach((node) => add(node?.text));
  return values.filter(Boolean).slice(0, 80).join(" | ");
}

function evidenceHasAny(evidence, words) {
  const clean = normalizeForMatch(evidence || "");
  return (Array.isArray(words) ? words : []).some((word) => clean.includes(normalizeForMatch(word)));
}

function scoreAgentTaskProgress(goal, snapshot, domain, entity, deviceContext) {
  const skill = agentSkillForDomain(domain);
  if (!skill) return { score: 0, stage: "general", evidence: "无专用技能", entityVisible: false, completionLike: false, searchLike: false, appLike: false };
  const evidence = collectSnapshotEvidenceText(snapshot);
  const currentPackage = safeText(snapshot?.packageName || snapshot?.currentApp || "", 120);
  const assistantHost = isAssistantHostAppPackage(currentPackage) || snapshotLooksLikeAssistantChat(snapshot);
  const cleanEvidence = normalizeForMatch(evidence);
  const cleanEntity = normalizeForMatch(entity || "");
  const entityVisible = Boolean(cleanEntity && cleanEvidence.includes(cleanEntity));
  const completionLike = entityVisible && evidenceHasAny(evidence, skill.completionWords);
  const searchLike = evidenceHasAny(evidence, skill.searchWords) || (Array.isArray(snapshot?.inputNodes) && snapshot.inputNodes.length > 0);
  const appLike = isCurrentAppAlreadyDomainCandidate(snapshot, deviceContext, domain);
  let score = 0;
  let stage = "unrelated";
  if (assistantHost) {
    score = 0;
    stage = "assistant_host";
  } else if (completionLike) {
    score = 0.95;
    stage = "completion_evidence";
  } else if (entityVisible && appLike) {
    score = 0.78;
    stage = "entity_visible_in_candidate_app";
  } else if (searchLike && appLike) {
    score = 0.55;
    stage = "search_or_input_in_candidate_app";
  } else if (appLike) {
    score = 0.35;
    stage = "candidate_app_open";
  } else if (searchLike) {
    score = 0.20;
    stage = "search_like_but_wrong_app";
  }
  return {
    score,
    stage,
    evidence: safeText(evidence, 220),
    entityVisible,
    completionLike,
    searchLike,
    appLike,
    assistantHost,
  };
}

function formatCandidateAppsForPrompt(appCandidates, max = 8) {
  const list = (Array.isArray(appCandidates) ? appCandidates : []).slice(0, max).map((app, index) => {
    const pkg = app.packageName ? `(${app.packageName})` : "";
    const score = Number.isFinite(Number(app.score)) ? ` score=${Number(app.score)}` : "";
    return `${index + 1}. ${app.label || app.appName || "未知"}${pkg}${score}`;
  });
  return list.length ? list.join("\n") : "无可靠候选 App。";
}

function buildAgentRuntimeHints(goal, snapshot, deviceContext, agentMemory = null) {
  const info = agentRuntimeTaskInfo(goal, snapshot, deviceContext, agentMemory);
  const skill = info.skill;
  if (!skill) {
    return [
      "Runtime task hints: general task.",
      "If the instruction refers to an external app but current screenshot is only AI assistant chat history, do not click chat bubbles; use open/back/home or ask for help.",
    ].join("\n");
  }
  const noProgressCount = Number(info.loopSignals.noProgressCount || info.loopSignals.repeatedCloudRejects || info.loopSignals.recoverableFailures || 0);
  const lines = [
    `Runtime skill: ${info.domain} / ${skill.label}`,
    `Target entity: ${info.entity || safeText(goal, 60)}`,
    `Target kind: ${skill.targetKind}`,
    `Current page classification: ${info.assistantHost ? "AI assistant host" : (info.currentIsCandidate ? "candidate target app" : "unrelated/wrong app")}`,
    `Progress score: ${info.progress.score.toFixed(2)} (${info.progress.stage})`,
    `Candidate apps:\n${formatCandidateAppsForPrompt(info.appCandidates, 8)}`,
    `Completion evidence must include: ${[info.entity, ...(skill.completionWords || []).slice(0, 8)].filter(Boolean).join(" / ")}`,
    `Recovery policy: ${skill.preferredRecovery}. If progress score stays low, open the best candidate app, use Back/Home, or change route; do not keep clicking the same visible text.`,
    skill.forbiddenOnAssistant,
  ];
  if (noProgressCount > 0) lines.push(`No-progress signal from Android memory: ${noProgressCount}. You must change strategy now.`);
  return lines.join("\n").slice(0, 1600);
}

function shouldPreferDomainOpenPreflight(goal, snapshot, deviceContext, agentMemory, recentActions) {
  const info = agentRuntimeTaskInfo(goal, snapshot, deviceContext, agentMemory);
  if (!isDomainTask(info.domain)) return { shouldOpen: false, info, reason: "not_domain_task" };
  if (info.currentIsCandidate && info.progress.stage !== "assistant_host") return { shouldOpen: false, info, reason: "already_candidate_app" };
  const executedStepCount = Number(info.loopSignals.executedStepCount || 0);
  const noProgressCount = Number(info.loopSignals.noProgressCount || info.loopSignals.repeatedCloudRejects || info.loopSignals.recoverableFailures || 0);
  const recent = normalizeForMatch(Array.isArray(recentActions) ? recentActions.slice(-6).join(" ") : "");
  const failedOrBlocked = recent.includes("失败") || recent.includes("fail") || recent.includes("拒绝") || recent.includes("blocked");
  const shouldOpen = Boolean(
    info.assistantHost ||
      !info.currentPackage ||
      executedStepCount === 0 ||
      info.progress.score < 0.25 ||
      noProgressCount >= 1 ||
      failedOrBlocked
  );
  return { shouldOpen, info, reason: shouldOpen ? `preflight_open_${info.progress.stage}` : "no_open_needed" };
}

function defaultDomainOpenAppCandidate(domain) {
  if (domain === "stock_trade") return null;
  if (domain === "stock_detail" || domain === "stock_index" || domain === "finance_news") return { label: "同花顺", packageName: "", source: "default_stock_app" };
  if (domain === "navigation") return { label: "高德地图", packageName: "", source: "default_navigation_app" };
  if (domain === "music") return { label: "网易云音乐", packageName: "", source: "default_music_app" };
  if (domain === "video") return { label: "哔哩哔哩", packageName: "", source: "default_video_app" };
  if (domain === "travel") return { label: "携程旅行", packageName: "", source: "default_travel_app" };
  return null;
}

function bestDomainOpenAppCandidate(deviceContext, domain) {
  const ranked = rankDomainAppCandidates(deviceContext, domain, 8);
  if (ranked.length) return { ...ranked[0], source: "device_installed_apps" };
  const installed = installedAppsFromDeviceContext(deviceContext);
  if (installed.length) return null;
  return defaultDomainOpenAppCandidate(domain);
}

function isCurrentAppAlreadyDomainCandidate(snapshot, deviceContext, domain) {
  const currentPackage = safeText(snapshot?.packageName || snapshot?.currentApp || "", 120);
  if (!currentPackage) return false;
  const candidates = rankDomainAppCandidates(deviceContext, domain, 20);
  if (candidates.some((app) => app.packageName && app.packageName === currentPackage)) return true;
  const keywords = agentDomainAppKeywords(domain);
  return appMatchesAnyKeyword({ label: currentPackage, packageName: currentPackage, aliases: [] }, keywords);
}

function hasRecentlyTriedOpenCandidate(recentActions, candidate) {
  const recent = (Array.isArray(recentActions) ? recentActions : []).slice(-4).map((item) => normalizeForMatch(item)).join("|");
  const label = normalizeForMatch(candidate?.label || "");
  const pkg = normalizeForMatch(candidate?.packageName || "");
  if (!recent) return false;
  return Boolean(
    recent.includes("openapp") &&
      ((label && recent.includes(label)) || (pkg && recent.includes(pkg))) &&
      (recent.includes("失败") || recent.includes("fail") || recent.includes("没有找到"))
  );
}

function buildGuiPlusDomainOpenPreflightPlan(goal, snapshot, supportedSteps, screenshotInfo, deviceContext, agentMemory, recentActions) {
  const decision = shouldPreferDomainOpenPreflight(goal, snapshot, deviceContext, agentMemory, recentActions);
  const { info } = decision;
  const domain = info.domain;
  if (!decision.shouldOpen || !isDomainTask(domain)) return null;

  const contract = info.contract || buildTaskSemanticContract(goal, snapshot, deviceContext, agentMemory);
  const candidate = contract.requiredApp || bestDomainOpenAppCandidate(deviceContext, domain) || defaultDomainOpenAppCandidate(domain);
  if (!candidate || !candidate.label) return null;
  if (!contract.requiredApp && hasRecentlyTriedOpenCandidate(recentActions, candidate)) return null;

  const entity = contract.targetEntity || info.entity || extractAgentTaskEntity(goal, domain);
  const skill = info.skill || agentSkillForDomain(domain);
  const reason = [
    `后端 Preflight Gate：当前页分类=${info.progress.stage}，progress=${info.progress.score.toFixed(2)}，目标是${skill?.label || domain}。`,
    info.assistantHost ? `聊天气泡中的“${safeText(goal, 36)}”只是历史消息，不能点击。` : "当前页不是可靠目标页，先进入合适 App。",
    contract.requiredApp ? `用户已明确指定 App：${candidate.label}${candidate.packageName ? `(${candidate.packageName})` : ""}，这是硬约束，禁止用其他行情 App 替代。` : `先打开候选 App：${candidate.label}${candidate.packageName ? `(${candidate.packageName})` : ""}，再让 GUI Plus 在 App 内搜索/进入${entity || "目标"}。`,
  ].join(" ");

  return {
    agentState: {
      isComplete: false,
      expectedProgress: true,
      isWrong: false,
      confidence: info.assistantHost ? 0.94 : 0.82,
      reason,
      nextHint: `打开 ${candidate.label} 后继续搜索 ${entity || goal}`,
    },
    visualFrame: {
      pageTitle: info.assistantHost ? "AI 助手/聊天页" : "非目标页面",
      pageType: "preflight_open_app",
      summary: reason,
      isComplete: false,
      isWrong: false,
      targetVisible: false,
      targetText: candidate.label,
      suggestedAction: {
        type: "open_app",
        targetText: candidate.label,
        reason,
      },
      completionEvidence: "",
      reason,
      confidence: info.assistantHost ? 0.94 : 0.82,
      taskSkill: domain,
      progressScore: info.progress.score,
      progressStage: info.progress.stage,
    },
    agentStep: {
      type: "open_app",
      appName: candidate.label,
      packageName: candidate.packageName || undefined,
      targetText: candidate.label,
      reason,
      riskLevel: "low",
      requiresConfirmation: false,
    },
    stopConditions: ["visual_after_system_action"],
    guiPlusCompact: {
      s: "p",
      a: "open_app",
      appName: candidate.label,
      packageName: candidate.packageName || undefined,
      t: candidate.label,
      c: info.assistantHost ? 0.94 : 0.82,
      e: reason,
    },
    preflightDomainOpen: true,
    taskSkill: domain,
    taskEntity: entity,
    progressScore: info.progress.score,
    progressStage: info.progress.stage,
  };
}

function isTapOnAssistantChatBubble(compact, goal, snapshot) {
  if (!compact || compact.a !== "tap_xy") return false;
  if (!(isAssistantHostAppPackage(snapshot?.packageName || snapshot?.currentApp || "") || snapshotLooksLikeAssistantChat(snapshot))) return false;
  const target = normalizeForMatch(compact.t || compact.targetText || "");
  const goalText = normalizeForMatch(goal);
  if (target && goalText && (target.includes(goalText) || goalText.includes(target))) return true;
  const x = Number(compact.x);
  const y = Number(compact.y);
  // 聊天消息气泡通常位于主聊天区中部；在 AI 助手页对这种坐标点击要特别保守。
  return Number.isFinite(x) && Number.isFinite(y) && x > 0.08 && x < 0.92 && y > 0.18 && y < 0.82;
}


function isCompactActionUnsafeOnAssistant(compact, goal, snapshot) {
  if (!(isAssistantHostAppPackage(snapshot?.packageName || snapshot?.currentApp || "") || snapshotLooksLikeAssistantChat(snapshot))) return false;
  const action = String(compact?.a || "").toLowerCase();
  if (["tap_xy", "tap_node", "input_text", "wait"].includes(action)) return true;
  if (action === "finish") return !scoreAgentTaskProgress(goal, snapshot, inferAgentInstructionDomain(goal), extractAgentTaskEntity(goal, inferAgentInstructionDomain(goal))).completionLike;
  return false;
}

function isPrematureDomainFinish(compact, goal, snapshot, deviceContext) {
  const domain = inferAgentInstructionDomain(goal);
  if (!isDomainTask(domain)) return false;
  const action = String(compact?.a || "").toLowerCase();
  if (action !== "finish") return false;
  const entity = extractAgentTaskEntity(goal, domain);
  const progress = scoreAgentTaskProgress(goal, snapshot, domain, entity, deviceContext);
  return progress.score < 0.85 || !progress.completionLike;
}

function buildGuardedNeedReplanPlan(reason, confidence = 0.62) {
  const cleanReason = safeText(reason, 260);
  return {
    agentState: {
      isComplete: false,
      expectedProgress: false,
      isWrong: false,
      confidence,
      reason: cleanReason,
      nextHint: "动作被护栏拦截，需要重新规划或请求用户接管。",
    },
    visualFrame: {
      pageTitle: "动作被后端护栏拦截",
      pageType: "guarded_replan",
      summary: cleanReason,
      isComplete: false,
      isWrong: false,
      targetVisible: false,
      suggestedAction: { type: "need_user_help", targetText: "请求接管/重新规划", reason: cleanReason },
      reason: cleanReason,
      confidence,
    },
    agentStep: {
      type: "need_user_help",
      targetText: "请求接管/重新规划",
      reason: `USER_TAKEOVER_REQUIRED: ${cleanReason}`,
      riskLevel: "low",
      requiresConfirmation: false,
    },
    stopConditions: ["visual_after_uncertain_progress"],
    guiPlusCompact: { s: "p", a: "need_user_help", t: "请求接管/重新规划", c: confidence, e: cleanReason },
    guarded: true,
  };
}

function normalizeRuntimeCompactActionName(value) {
  const raw = String(value || "").toLowerCase().trim().replace(/[\s\-]+/g, "_");
  if (["click", "tap", "press", "point", "tap_point", "click_xy", "coordinate_click", "coordinate_tap"].includes(raw)) return "tap_xy";
  if (["type", "input", "enter_text", "text"].includes(raw)) return "input_text";
  if (["open", "launch", "launch_app", "open_application"].includes(raw)) return "open_app";
  if (["system_button", "button"].includes(raw)) return "system_button";
  return normalizeAgentStepType(raw);
}

function quantizedRuntimeTapSignature(x, y) {
  const nx = Number(x);
  const ny = Number(y);
  if (!Number.isFinite(nx) || !Number.isFinite(ny)) return "";
  const cx = Math.max(0, Math.min(49, Math.floor(nx * 50)));
  const cy = Math.max(0, Math.min(49, Math.floor(ny * 50)));
  return `tap@${cx},${cy}`;
}

function runtimeTapSignatureCandidates(rawX, rawY, screenshotInfo = null) {
  const out = new Set();
  const x = Number(rawX);
  const y = Number(rawY);
  if (!Number.isFinite(x) || !Number.isFinite(y)) return [];

  if (x >= 0 && x <= 1 && y >= 0 && y <= 1) {
    out.add(quantizedRuntimeTapSignature(x, y));
  }

  // 阿里 GUI Plus/mobile_use 官方坐标是 1000x1000。
  if (x >= 0 && x <= 1000 && y >= 0 && y <= 1000) {
    out.add(quantizedRuntimeTapSignature(x / 1000, y / 1000));
  }

  const imageWidth = Number(screenshotInfo?.width) || 0;
  const imageHeight = Number(screenshotInfo?.height) || 0;
  const displayWidth = Number(screenshotInfo?.displayWidth) || imageWidth;
  const displayHeight = Number(screenshotInfo?.displayHeight) || imageHeight;
  if (imageWidth > 1 && imageHeight > 1 && x >= 0 && x <= imageWidth + 24 && y >= 0 && y <= imageHeight + 24) {
    out.add(quantizedRuntimeTapSignature(x / imageWidth, y / imageHeight));
  }
  if (displayWidth > 1 && displayHeight > 1 && x >= 0 && x <= displayWidth + 24 && y >= 0 && y <= displayHeight + 24) {
    out.add(quantizedRuntimeTapSignature(x / displayWidth, y / displayHeight));
  }

  return Array.from(out).filter(Boolean);
}

function runtimeActionSignaturesFromCompact(compact, screenshotInfo = null) {
  const raw = compact?.agentStep || compact?.step || compact?.actionStep || compact?.suggestedAction || compact || {};
  const action = normalizeRuntimeCompactActionName(raw.a || raw.action || raw.type || raw.name || "");
  const out = new Set();

  if (action === "tap_xy") {
    const coordinate = Array.isArray(raw.coordinate) ? raw.coordinate : Array.isArray(raw.coord) ? raw.coord : [];
    const x = raw.x ?? raw.cx ?? raw.centerX ?? raw.targetX ?? raw.tapX ?? coordinate[0];
    const y = raw.y ?? raw.cy ?? raw.centerY ?? raw.targetY ?? raw.tapY ?? coordinate[1];
    runtimeTapSignatureCandidates(x, y, screenshotInfo).forEach((item) => out.add(item));
  } else if (action === "tap_node") {
    const key = safeText(raw.n || raw.nodeId || raw.targetNodeId || raw.t || raw.targetText || raw.text || "", 32);
    if (key) out.add(`tap_node@${key}`);
  } else if (action === "open_app") {
    const key = safeText(raw.packageName || raw.package || raw.appName || raw.app || raw.t || raw.targetText || "", 64);
    if (key) out.add(`open@${key}`);
  } else if (action === "input_text") {
    const key = safeText(raw.v || raw.text || raw.inputText || raw.value || "", 32);
    if (key) out.add(`input@${key}`);
  } else if (action === "scroll" || action === "swipe") {
    out.add(`${action}@${normalizeAgentDirection(raw.d || raw.direction || "") || "up"}`);
  } else if (["back", "home", "recents", "notifications", "quick_settings"].includes(action)) {
    out.add(action);
  } else if (action === "system_button") {
    const button = String(raw.button || raw.key || raw.t || "").toLowerCase();
    if (button.includes("back") || button.includes("返回")) out.add("back");
    if (button.includes("home") || button.includes("主页")) out.add("home");
  }

  return Array.from(out).filter(Boolean);
}

function runtimeBlockedActionSignatures(agentMemory) {
  const memory = agentMemory && typeof agentMemory === "object" ? agentMemory : {};
  const out = new Set();
  const add = (value) => {
    const text = safeText(value, 260);
    if (!text) return;
    const matches = text.match(/(?:tap@\d+,\d+|tap_node@[^\s，。；;:：]+|open@[^\s，。；;:：]+|input@[^\s，。；;:：]+|scroll@[a-z]+|swipe@[a-z]+|back|home|recents|notifications|quick_settings)/g) || [];
    matches.forEach((item) => out.add(item));
  };

  if (Array.isArray(memory.blockedActionSignatures)) memory.blockedActionSignatures.forEach(add);
  if (Array.isArray(memory.verificationEvents)) memory.verificationEvents.forEach(add);
  if (Array.isArray(memory.blockedActions)) memory.blockedActions.forEach(add);
  if (Array.isArray(memory.recentActions)) {
    memory.recentActions
      .filter((line) => /无进展|拉黑|blocked|no progress|拒绝/.test(String(line || "")))
      .forEach(add);
  }

  const loopSignals = memory.loopSignals && typeof memory.loopSignals === "object" ? memory.loopSignals : {};
  if (Number(loopSignals.noProgressCount || 0) > 0 && loopSignals.lastActionSignature) add(loopSignals.lastActionSignature);
  return Array.from(out).slice(-16);
}

function runtimeVerificationHintForPrompt(agentMemory) {
  const memory = agentMemory && typeof agentMemory === "object" ? agentMemory : {};
  const loopSignals = memory.loopSignals && typeof memory.loopSignals === "object" ? memory.loopSignals : {};
  const blockedSignatures = runtimeBlockedActionSignatures(memory);
  const verificationEvents = Array.isArray(memory.verificationEvents) ? memory.verificationEvents.slice(-5).map((item) => safeText(item, 160)).filter(Boolean) : [];
  const pending = memory.pendingVerification && typeof memory.pendingVerification === "object" ? memory.pendingVerification : null;
  const executionFeedback = memory.executionFeedback && typeof memory.executionFeedback === "object" ? memory.executionFeedback : {};
  const lastToolResponse = memory.lastToolResponse && typeof memory.lastToolResponse === "object" ? normalizeAndroidVisualToolResponse(memory.lastToolResponse) : null;
  const noProgressCount = Number(loopSignals.noProgressCount || executionFeedback.noProgressCount || 0);
  const finishVerificationRequested = agentMemoryRequestsFinishVerification(memory);
  const routeRefreshRequested = agentMemoryRequestsRouteRefresh(memory);

  const lines = [];
  if (blockedSignatures.length) {
    lines.push(`Android runtime 已判定这些动作无进展/被临时拉黑：${blockedSignatures.join(" / ")}。禁止再次输出完全相同动作签名。`);
    lines.push("如果目标入口仍然是正确的，也不要重复同一落点；应换一个更明确的落点、点击文字主体/列表行中心、重新搜索、返回再进，或等待重新截图后再判断。");
  }
  if (verificationEvents.length) lines.push(`动作后验证记录：${verificationEvents.join(" | ")}`);
  if (lastToolResponse && (lastToolResponse.result || lastToolResponse.actionSignature || lastToolResponse.verification !== "unknown")) {
    lines.push(`Android lastToolResponse: success=${lastToolResponse.success === null ? "unknown" : lastToolResponse.success}; action=${lastToolResponse.actionSignature || "unknown"}; verification=${lastToolResponse.verification}; result=${lastToolResponse.result || "none"}.`);
  }
  if (pending?.signature) lines.push(`当前仍有待验证动作：${safeText(pending.signature, 80)}，下一步必须先根据当前截图判断它是否真的生效。`);
  if (finishVerificationRequested) lines.push("Android 已请求 finish 新截图复核：本轮必须重新检查当前截图；只有目标仍明确完成时才能再次输出 terminate success，否则继续操作。");
  if (routeRefreshRequested) lines.push("Android 已请求 route refresh：忽略旧路线缓存，基于当前截图、执行结果和原始目标重新判断路线。");
  if (noProgressCount > 0) lines.push(`Android noProgressCount=${noProgressCount}：当前必须换策略，不能继续 wait 或重复同一坐标。`);
  return lines.join("\n").slice(0, 1800);
}


function normalizeAgentDeepThinkingMode(value) {
  const raw = String(value || "").trim().toLowerCase().replace(/[-\s]+/g, "_");
  if (["0", "false", "off", "none", "fast", "disable", "disabled"].includes(raw)) return "fast";
  if (["1", "true", "on", "deep", "always", "force", "full"].includes(raw)) return "deep";
  return "adaptive";
}

function adaptiveDeepThinkingDecision(goal, snapshot = null, deviceContext = null, agentMemory = null, recentActions = []) {
  const mode = normalizeAgentDeepThinkingMode(AGENT_GUI_DEEP_THINKING_MODE);
  const memory = agentMemory && typeof agentMemory === "object" ? agentMemory : {};
  const loopSignals = memory.loopSignals && typeof memory.loopSignals === "object" ? memory.loopSignals : {};
  const reasons = [];
  const add = (reason) => {
    const clean = safeText(reason, 180);
    if (clean && !reasons.includes(clean)) reasons.push(clean);
  };

  const noProgressCount = Number(loopSignals.noProgressCount || 0);
  const blockedSignatures = runtimeBlockedActionSignatures(memory);
  const verificationEvents = Array.isArray(memory.verificationEvents)
    ? memory.verificationEvents.slice(-6).map((item) => safeText(item, 180)).filter(Boolean)
    : [];
  const pending = memory.pendingVerification && typeof memory.pendingVerification === "object" ? memory.pendingVerification : null;
  const failedActions = Array.isArray(memory.failedActions) ? memory.failedActions.slice(-4).map((item) => safeText(item, 140)).filter(Boolean) : [];
  const blockedActions = Array.isArray(memory.blockedActions) ? memory.blockedActions.slice(-4).map((item) => safeText(item, 140)).filter(Boolean) : [];
  const recentText = [
    ...verificationEvents,
    ...failedActions,
    ...blockedActions,
    ...(Array.isArray(recentActions) ? recentActions.slice(-5).map((item) => safeText(item, 120)) : []),
  ].join(" | ");

  if (noProgressCount >= AGENT_GUI_DEEP_THINKING_MIN_NO_PROGRESS) add(`Android noProgressCount=${noProgressCount}`);
  if (blockedSignatures.length) add(`Android blockedActionSignatures=${blockedSignatures.slice(0, 5).join("/")}`);
  if (/无进展|未生效|没有变化|拉黑|blocked|no progress|same screen|stuck|failed verification|验证失败|重复/.test(recentText)) {
    add("recent runtime verification/action history shows stuck or repeated ineffective action");
  }
  if (pending?.signature && (noProgressCount > 0 || blockedSignatures.length)) {
    add(`pendingVerification still unresolved: ${safeText(pending.signature, 80)}`);
  }

  const contract = buildTaskSemanticContract(goal, snapshot, deviceContext, agentMemory);
  const domain = safeText(contract.domain || "general", 60);
  const entity = safeText(contract.targetEntity || "", 80);
  const currentPackage = safeText(snapshot?.packageName || snapshot?.currentApp || "", 120);
  if (isDomainTask(domain) && (isAssistantHostAppPackage(currentPackage) || snapshotLooksLikeAssistantChat(snapshot))) {
    add("external app task is still on assistant/chat host");
  }
  if (isDomainTask(domain) && entity) {
    const texts = [
      ...(Array.isArray(snapshot?.texts) ? snapshot.texts : []),
      ...(Array.isArray(snapshot?.clickableNodes) ? snapshot.clickableNodes.map((node) => node?.text || "") : []),
    ].map((item) => normalizeForMatch(item));
    const normalizedEntity = normalizeForMatch(entity);
    const matches = texts.filter((item) => normalizedEntity && item.includes(normalizedEntity));
    if (matches.length >= 3) add(`multiple similar target texts detected for ${safeText(entity, 80)}`);
  }
  if (contract.requiredApp && !currentAppSatisfiesTaskContract(contract, snapshot, deviceContext)) {
    add(`task semantic contract requires app=${contract.requiredApp.label}; current app is not satisfying contract`);
  }
  if (contract.requiredApp && /打开|open_app|open app/.test(recentText)) {
    add("explicit app constraint exists; re-check previous app-open route against task contract");
  }
  if (agentMemoryRequestsFinishVerification(memory)) {
    add("Android requested fresh-screen finish verification");
  }
  if (agentMemoryRequestsRouteRefresh(memory)) {
    add("Android requested route refresh/replan");
  }

  const forcedDeep = mode === "deep";
  const enabled = forcedDeep || (mode === "adaptive" && reasons.length > 0) || ALIYUN_GUI_ENABLE_THINKING === true;
  const level = forcedDeep ? "deep" : enabled ? "adaptive_deep" : "fast";
  const finalReasons = forcedDeep && !reasons.length ? ["AGENT_GUI_DEEP_THINKING_MODE=deep"] : reasons.slice(0, AGENT_GUI_DEEP_THINKING_REASON_MAX);
  return {
    mode,
    enabled,
    level,
    reasons: finalReasons,
    noProgressCount,
    blockedSignatures: blockedSignatures.slice(0, 8),
    timeoutExtraMs: enabled ? AGENT_GUI_DEEP_THINKING_TIMEOUT_EXTRA_MS : 0,
  };
}

function deepThinkingPromptBlock(decision) {
  const d = decision && typeof decision === "object" ? decision : { mode: "adaptive", enabled: false, reasons: [] };
  if (!d.enabled) {
    return "Deep thinking policy: adaptive-fast. 当前没有强卡死/风险信号，本轮快速决策；但仍需避免重复无效动作。";
  }
  const reasons = Array.isArray(d.reasons) && d.reasons.length ? d.reasons.join("；") : "manual/deep mode enabled";
  return [
    `Deep thinking policy: ${d.level || "adaptive_deep"} ENABLED.`,
    `Trigger reasons: ${reasons}.`,
    "Before choosing the next mobile_use action, internally re-check: current page, task goal, last failed action, Android verification feedback, and a different route that can create visible progress.",
    "Do not output chain-of-thought or analysis. Output only the next valid mobile_use action/tool call, but make it the result of this deeper replan.",
    "When blockedActionSignatures/noProgress exist, the next action must change at least one of: route, target element, tap area, app entry, search query strategy, Back/Home recovery, or wait-for-state reason.",
  ].join("\n");
}


function runtimeGuardBlockedReasonForCompact(compact, agentMemory, screenshotInfo = null) {
  const blocked = new Set(runtimeBlockedActionSignatures(agentMemory));
  if (!blocked.size) return null;
  const signatures = runtimeActionSignaturesFromCompact(compact, screenshotInfo);
  const hit = signatures.find((item) => blocked.has(item));
  if (!hit) return null;
  return `后端 v12G runtime guard：Android 已把动作 ${hit} 判定为无进展/临时拉黑；本轮禁止再次下发同一动作。请重新观察并换路线、换落点、重新搜索、返回上一层或选择其他入口。`;
}

function guardGuiPlusParsedPlan(parsed, goal, snapshot, supportedSteps, screenshotInfo, deviceContext, agentMemory, recentActions) {
  const compact = parsed?.guiPlusCompact || parsed?.compactVision || parsed || {};

  // The guard layer validates runtime safety only. It never interprets user semantics,
  // selects an App, or rewrites the GUI Plus route.
  const runtimeBlockedReason = runtimeGuardBlockedReasonForCompact(compact, agentMemory, screenshotInfo);
  if (runtimeBlockedReason) {
    const replan = buildGuardedNeedReplanPlan(runtimeBlockedReason, 0.74);
    replan.guardReason = "runtime_blocked_action_signature";
    replan.sourceDetail = "guarded_android_runtime_no_progress_signature";
    replan.runtimeBlockedSignatures = runtimeActionSignaturesFromCompact(compact, screenshotInfo);
    return replan;
  }

  return parsed;
}


function nullableBooleanFromValue(value) {
  if (value === true || value === "true" || value === 1 || value === "1" || value === "yes") return true;
  if (value === false || value === "false" || value === 0 || value === "0" || value === "no") return false;
  return null;
}

function uniqueSafeTextList(values, maxItems = 16, maxChars = 240) {
  const out = [];
  const seen = new Set();
  for (const value of Array.isArray(values) ? values : []) {
    const clean = safeText(value, maxChars);
    if (!clean || seen.has(clean)) continue;
    seen.add(clean);
    out.push(clean);
  }
  return out.slice(-Math.max(1, Math.min(64, Number(maxItems || 16))));
}

function normalizeGuiPlusInteractionHistory(value, maxItems = 16) {
  const source = Array.isArray(value) ? value : [];
  const out = [];
  for (const item of source) {
    if (!item || typeof item !== "object") continue;
    const rawRole = String(item.role || item.speaker || item.author || "").trim().toLowerCase();
    const role = ["assistant", "gui_plus", "guiplus", "model", "agent"].includes(rawRole)
      ? "assistant"
      : ["user", "human"].includes(rawRole)
        ? "user"
        : "";
    if (!role) continue;
    let content = safeText(item.content || item.text || item.message || item.value || "", 1200);
    const sensitiveRedacted = Boolean(item.sensitiveRedacted || item.sensitive_redacted || item.redacted);
    if (sensitiveRedacted && role === "user") {
      content = "[用户已在目标应用中完成敏感输入]";
    }
    if (!content) continue;
    const normalized = { role, content, sensitiveRedacted };
    const previous = out[out.length - 1];
    if (previous && previous.role === normalized.role && previous.content === normalized.content) continue;
    out.push(normalized);
  }
  return out.slice(-Math.max(1, Math.min(32, Number(maxItems || 16))));
}

function guiPlusInteractionHistoryFromRecentActions(recentActions = []) {
  const turns = [];
  for (const raw of Array.isArray(recentActions) ? recentActions : []) {
    const line = String(raw || "").trim();
    if (!line) continue;
    if (line.startsWith("guiPlusQuestion:")) {
      turns.push({ role: "assistant", content: line.slice("guiPlusQuestion:".length), sensitiveRedacted: false });
    } else if (line.startsWith("userReply:")) {
      const content = line.slice("userReply:".length);
      turns.push({
        role: "user",
        content,
        sensitiveRedacted: /敏感输入|private_step|completed_private/i.test(content),
      });
    } else if (line.startsWith("userInstruction:")) {
      turns.push({ role: "user", content: line.slice("userInstruction:".length), sensitiveRedacted: false });
    }
  }
  return normalizeGuiPlusInteractionHistory(turns, 16);
}

function mergeGuiPlusInteractionHistories(...sources) {
  const merged = [];
  for (const source of sources) {
    for (const item of normalizeGuiPlusInteractionHistory(source, 32)) {
      const previous = merged[merged.length - 1];
      if (previous && previous.role === item.role && previous.content === item.content) continue;
      const duplicateIndex = merged.findIndex((existing) => existing.role === item.role && existing.content === item.content);
      if (duplicateIndex >= 0) continue;
      merged.push(item);
    }
  }
  return merged.slice(-16);
}

function resolveGuiPlusInteractionHistory(body = null, agentMemory = null, recentActions = [], session = null) {
  const bodyHistory = body && typeof body === "object" ? body.interactionHistory : null;
  const memory = agentMemory && typeof agentMemory === "object" ? agentMemory : {};
  const sessionHistory = session && typeof session === "object" ? session.interactionHistory : null;
  return mergeGuiPlusInteractionHistories(
    sessionHistory,
    memory.interactionHistory,
    bodyHistory,
    guiPlusInteractionHistoryFromRecentActions(recentActions),
  );
}

function syncAgentGuiInteractionHistory(session, history) {
  const normalized = normalizeGuiPlusInteractionHistory(history, 16);
  if (!session || typeof session !== "object") return normalized;
  session.interactionHistory = mergeGuiPlusInteractionHistories(session.interactionHistory, normalized);
  session.updatedAt = Date.now();
  return session.interactionHistory;
}

function formatGuiPlusInteractionHistoryForPrompt(history) {
  const normalized = normalizeGuiPlusInteractionHistory(history, 16);
  if (!normalized.length) return "none";
  return normalized.map((item, index) => {
    const speaker = item.role === "assistant" ? "GUI Plus" : "User";
    return `${index + 1}. ${speaker}: ${safeText(item.content, 1200)}`;
  }).join("\n");
}

function normalizeAndroidVisualToolResponse(value) {
  const raw = value && typeof value === "object" ? value : {};
  const success = nullableBooleanFromValue(raw.success ?? raw.ok ?? raw.lastResultOk);
  return {
    type: safeText(raw.type || "tool_response", 40) || "tool_response",
    toolName: safeText(raw.toolName || raw.tool || raw.name || "mobile_use", 80) || "mobile_use",
    success,
    result: safeText(raw.result || raw.message || raw.executionResult || "", 320),
    verification: safeText(raw.verification || raw.lastVerification || "unknown", 80) || "unknown",
    actionSignature: safeText(raw.actionSignature || raw.signature || raw.lastActionSignature || "", 160),
    screenChanged: nullableBooleanFromValue(raw.screenChanged),
    finishVerificationRequested: booleanFromValue(raw.finishVerificationRequested, false),
  };
}

function normalizeAndroidVisualExecutionProtocol(body, rawAgentMemory = {}, recentActions = []) {
  const memory = rawAgentMemory && typeof rawAgentMemory === "object" ? rawAgentMemory : {};
  const exclusiveGuiPlusVisualSession = Boolean(
    isExclusiveGuiPlusVisualRequest(body) ||
      isExclusiveGuiPlusVisualMemory(memory)
  );
  const bodyFeedback = body?.executionFeedback && typeof body.executionFeedback === "object" ? body.executionFeedback : {};
  const memoryFeedback = memory.executionFeedback && typeof memory.executionFeedback === "object" ? memory.executionFeedback : {};
  const rawLoopSignals = memory.loopSignals && typeof memory.loopSignals === "object" ? memory.loopSignals : {};
  const rawToolResponse = body?.lastToolResponse || body?.toolResponse || memory.lastToolResponse || memory.toolResponse || rawLoopSignals.lastToolResponse || {};
  const lastToolResponse = normalizeAndroidVisualToolResponse(rawToolResponse);

  const verificationEvents = uniqueSafeTextList([
    ...(Array.isArray(memory.verificationEvents) ? memory.verificationEvents : []),
    ...(Array.isArray(memoryFeedback.verificationEvents) ? memoryFeedback.verificationEvents : []),
    ...(Array.isArray(bodyFeedback.verificationEvents) ? bodyFeedback.verificationEvents : []),
    bodyFeedback.latestEvent,
    memoryFeedback.latestEvent,
    lastToolResponse.result,
  ], 12, 260);
  const blockedActionSignatures = uniqueSafeTextList([
    ...(Array.isArray(memory.blockedActionSignatures) ? memory.blockedActionSignatures : []),
    ...(Array.isArray(memoryFeedback.blockedActionSignatures) ? memoryFeedback.blockedActionSignatures : []),
    ...(Array.isArray(bodyFeedback.blockedActionSignatures) ? bodyFeedback.blockedActionSignatures : []),
  ], 12, 180);

  const finishVerificationRequested = Boolean(
    body?.finishVerificationRequested === true ||
      bodyFeedback.finishVerificationRequested === true ||
      memory.finishVerificationRequested === true ||
      memoryFeedback.finishVerificationRequested === true ||
      rawLoopSignals.finishVerificationRequested === true ||
      lastToolResponse.finishVerificationRequested === true
  );
  const explicitRouteRefresh = Boolean(
    body?.routeRefreshRequested === true ||
      body?.invalidateCachedAgentBrainRoute === true ||
      bodyFeedback.routeRefreshRequested === true ||
      memory.routeRefreshRequested === true ||
      memory.invalidateCachedAgentBrainRoute === true ||
      memoryFeedback.routeRefreshRequested === true ||
      rawLoopSignals.routeRefreshRequested === true
  );
  const explicitGuiPlusReplan = Boolean(
    body?.visualReplanRequested === true ||
      body?.guiPlusReplanRequested === true ||
      bodyFeedback.visualReplanRequested === true ||
      bodyFeedback.guiPlusReplanRequested === true ||
      memory.visualReplanRequested === true ||
      memory.guiPlusReplanRequested === true ||
      memoryFeedback.visualReplanRequested === true ||
      memoryFeedback.guiPlusReplanRequested === true ||
      rawLoopSignals.visualReplanRequested === true ||
      rawLoopSignals.guiPlusReplanRequested === true
  );
  let visualReplanRequested = Boolean(explicitGuiPlusReplan || finishVerificationRequested);
  const routeRefreshRequested = exclusiveGuiPlusVisualSession
    ? false
    : Boolean(explicitRouteRefresh || finishVerificationRequested);
  const lastResultOk = nullableBooleanFromValue(
    bodyFeedback.lastResultOk ?? memoryFeedback.lastResultOk ?? lastToolResponse.success
  );
  const lastVerification = safeText(
    bodyFeedback.lastVerification || memoryFeedback.lastVerification || lastToolResponse.verification || "unknown",
    80
  ) || "unknown";
  const latestEvent = safeText(
    bodyFeedback.latestEvent || memoryFeedback.latestEvent || verificationEvents[verificationEvents.length - 1] || lastToolResponse.result || "",
    260
  );
  const lastActionSignature = safeText(
    bodyFeedback.lastActionSignature || memoryFeedback.lastActionSignature || lastToolResponse.actionSignature || rawLoopSignals.lastActionSignature || "",
    160
  );
  const noProgressCount = Math.max(0, Number(bodyFeedback.noProgressCount ?? memoryFeedback.noProgressCount ?? rawLoopSignals.noProgressCount ?? 0) || 0);
  const sameActionCount = Math.max(0, Number(bodyFeedback.sameActionCount ?? memoryFeedback.sameActionCount ?? rawLoopSignals.sameActionCount ?? 0) || 0);
  visualReplanRequested = Boolean(
    visualReplanRequested ||
      noProgressCount > 0 ||
      lastResultOk === false
  );

  const executionFeedback = {
    ...memoryFeedback,
    ...bodyFeedback,
    lastResultOk,
    lastVerification,
    noProgressCount,
    sameActionCount,
    lastActionSignature,
    blockedActionSignatures,
    verificationEvents,
    latestEvent,
    finishVerificationRequested,
    visualReplanRequested,
    guiPlusReplanRequested: visualReplanRequested,
    routeRefreshRequested,
  };
  const mergedRecentActions = uniqueSafeTextList([
    ...(Array.isArray(memory.recentActions) ? memory.recentActions : []),
    ...(Array.isArray(recentActions) ? recentActions : []),
  ], 12, 1200);
  const interactionHistory = resolveGuiPlusInteractionHistory(body, memory, mergedRecentActions, null);
  const interactionProtocol = safeText(
    body?.interactionProtocol || memory.interactionProtocol || "gui_plus_dialogue_v1",
    80
  ) || "gui_plus_dialogue_v1";
  const surfaceContext = visualSurfaceContextFromPayload(body, body?.deviceContext, memory);
  const loopSignals = {
    ...rawLoopSignals,
    executedStepCount: Math.max(0, Number(rawLoopSignals.executedStepCount || 0) || 0),
    loopIndex: Math.max(0, Number(rawLoopSignals.loopIndex || 0) || 0),
    noProgressCount,
    sameActionCount,
    lastResultOk,
    lastVerification,
    lastActionSignature,
    finishVerificationRequested,
    visualReplanRequested,
    guiPlusReplanRequested: visualReplanRequested,
    routeRefreshRequested,
    lastToolResponse,
  };

  return {
    ...memory,
    schema: safeText(memory.schema || body?.agentSessionProtocol || "android_visual_agent_loop_memory_v7_tool_response", 120),
    recentActions: mergedRecentActions,
    interactionProtocol,
    interactionHistory,
    interactionTurnCount: interactionHistory.length,
    surfaceContext,
    executionFeedback,
    lastToolResponse,
    toolResponse: lastToolResponse,
    verificationEvents,
    blockedActionSignatures,
    finishVerificationRequested,
    visualReplanRequested,
    guiPlusReplanRequested: visualReplanRequested,
    routeRefreshRequested,
    invalidateCachedAgentBrainRoute: routeRefreshRequested,
    decisionOwner: exclusiveGuiPlusVisualSession ? "gui_plus" : safeText(memory.decisionOwner || body?.decisionOwner || "", 80),
    visualDecisionOwner: exclusiveGuiPlusVisualSession ? "gui_plus" : safeText(memory.visualDecisionOwner || body?.visualDecisionOwner || "", 80),
    exclusiveVisualSession: exclusiveGuiPlusVisualSession,
    allowAgentBrain: exclusiveGuiPlusVisualSession ? false : memory.allowAgentBrain,
    allowRoutePlanner: exclusiveGuiPlusVisualSession ? false : memory.allowRoutePlanner,
    allowSemanticJudge: exclusiveGuiPlusVisualSession ? false : memory.allowSemanticJudge,
    allowTaskContractJudge: exclusiveGuiPlusVisualSession ? false : memory.allowTaskContractJudge,
    visualOwnership: exclusiveGuiPlusVisualSession
      ? {
          schema: "android_gui_plus_exclusive_ownership_v1",
          owner: "gui_plus",
          exclusive: true,
          entryRouterReleased: true,
          allowAgentBrain: false,
          allowRoutePlanner: false,
          allowSemanticJudge: false,
          allowTaskContractJudge: false,
        }
      : memory.visualOwnership,
    loopSignals,
  };
}

function agentMemoryRequestsRouteRefresh(agentMemory) {
  const memory = agentMemory && typeof agentMemory === "object" ? agentMemory : {};
  if (isExclusiveGuiPlusVisualMemory(memory)) return false;
  const feedback = memory.executionFeedback && typeof memory.executionFeedback === "object" ? memory.executionFeedback : {};
  const loopSignals = memory.loopSignals && typeof memory.loopSignals === "object" ? memory.loopSignals : {};
  return Boolean(
    memory.routeRefreshRequested === true ||
      memory.invalidateCachedAgentBrainRoute === true ||
      memory.finishVerificationRequested === true ||
      feedback.routeRefreshRequested === true ||
      feedback.finishVerificationRequested === true ||
      loopSignals.routeRefreshRequested === true ||
      loopSignals.finishVerificationRequested === true
  );
}

function agentMemoryRequestsGuiPlusReplan(agentMemory) {
  const memory = agentMemory && typeof agentMemory === "object" ? agentMemory : {};
  const feedback = memory.executionFeedback && typeof memory.executionFeedback === "object" ? memory.executionFeedback : {};
  const loopSignals = memory.loopSignals && typeof memory.loopSignals === "object" ? memory.loopSignals : {};
  return Boolean(
    memory.visualReplanRequested === true ||
      memory.guiPlusReplanRequested === true ||
      memory.finishVerificationRequested === true ||
      feedback.visualReplanRequested === true ||
      feedback.guiPlusReplanRequested === true ||
      feedback.finishVerificationRequested === true ||
      Number(feedback.noProgressCount || 0) > 0 ||
      feedback.lastResultOk === false ||
      loopSignals.visualReplanRequested === true ||
      loopSignals.guiPlusReplanRequested === true ||
      loopSignals.finishVerificationRequested === true ||
      Number(loopSignals.noProgressCount || 0) > 0 ||
      loopSignals.lastResultOk === false
  );
}

function agentMemoryRequestsFinishVerification(agentMemory) {
  const memory = agentMemory && typeof agentMemory === "object" ? agentMemory : {};
  const feedback = memory.executionFeedback && typeof memory.executionFeedback === "object" ? memory.executionFeedback : {};
  const loopSignals = memory.loopSignals && typeof memory.loopSignals === "object" ? memory.loopSignals : {};
  const toolResponse = memory.lastToolResponse && typeof memory.lastToolResponse === "object" ? memory.lastToolResponse : {};
  return Boolean(
    memory.finishVerificationRequested === true ||
      feedback.finishVerificationRequested === true ||
      loopSignals.finishVerificationRequested === true ||
      toolResponse.finishVerificationRequested === true
  );
}

function compactAgentMemoryForPrompt(agentMemory, recentActions) {
  const memory = agentMemory && typeof agentMemory === "object" ? agentMemory : {};
  const pending = memory.pendingVerification && typeof memory.pendingVerification === "object"
    ? {
        signature: safeText(memory.pendingVerification.signature || "", 80),
        actionSummary: safeText(memory.pendingVerification.actionSummary || "", 160),
        expectedEvidence: Array.isArray(memory.pendingVerification.expectedEvidence)
          ? memory.pendingVerification.expectedEvidence.map((item) => safeText(item, 40)).filter(Boolean).slice(0, 6)
          : [],
        createdAtLoop: Number(memory.pendingVerification.createdAtLoop || 0),
      }
    : null;
  const executionFeedback = memory.executionFeedback && typeof memory.executionFeedback === "object"
    ? {
        lastResultOk: nullableBooleanFromValue(memory.executionFeedback.lastResultOk),
        lastVerification: safeText(memory.executionFeedback.lastVerification || "unknown", 80),
        noProgressCount: Math.max(0, Number(memory.executionFeedback.noProgressCount || 0) || 0),
        sameActionCount: Math.max(0, Number(memory.executionFeedback.sameActionCount || 0) || 0),
        lastActionSignature: safeText(memory.executionFeedback.lastActionSignature || "", 160),
        latestEvent: safeText(memory.executionFeedback.latestEvent || "", 260),
        finishVerificationRequested: Boolean(memory.executionFeedback.finishVerificationRequested),
        routeRefreshRequested: Boolean(memory.executionFeedback.routeRefreshRequested),
      }
    : null;
  const lastToolResponse = memory.lastToolResponse && typeof memory.lastToolResponse === "object"
    ? normalizeAndroidVisualToolResponse(memory.lastToolResponse)
    : null;

  return {
    schema: memory.schema || "agent_loop_memory",
    recentActions: Array.isArray(memory.recentActions) ? memory.recentActions.slice(-8) : Array.isArray(recentActions) ? recentActions.slice(-8) : [],
    failedActions: Array.isArray(memory.failedActions) ? memory.failedActions.slice(-8) : [],
    blockedActions: Array.isArray(memory.blockedActions) ? memory.blockedActions.slice(-8) : [],
    verificationEvents: Array.isArray(memory.verificationEvents) ? memory.verificationEvents.slice(-8) : [],
    blockedActionSignatures: runtimeBlockedActionSignatures(memory),
    pendingVerification: pending,
    executionFeedback,
    lastToolResponse,
    finishVerificationRequested: agentMemoryRequestsFinishVerification(memory),
    visualReplanRequested: agentMemoryRequestsGuiPlusReplan(memory),
    guiPlusReplanRequested: agentMemoryRequestsGuiPlusReplan(memory),
    routeRefreshRequested: agentMemoryRequestsRouteRefresh(memory),
    decisionOwner: safeText(memory.decisionOwner || memory.visualDecisionOwner || "", 80),
    exclusiveVisualSession: isExclusiveGuiPlusVisualMemory(memory),
    loopSignals: memory.loopSignals && typeof memory.loopSignals === "object" ? memory.loopSignals : {},
    policyHints: Array.isArray(memory.policyHints) ? memory.policyHints.slice(0, 8) : [],
  };
}





function isAgentBrainRouteRequest(body) {
  const intent = normalizeIntentName(body?.intent || body?.action || body?.type || body?.requestType);
  return Boolean(
    intent === "agent_brain_route" ||
      body?.agentBrainRoute === true ||
      body?.responseFormat?.includeAgentBrainRoute === true
  );
}

function normalizeAgentBrainRouteName(value) {
  const raw = String(value || "").toLowerCase().trim().replace(/[-\s]+/g, "_");
  if (["device", "device_tool", "internal", "internal_tool", "system_tool"].includes(raw)) return "device_tool";
  if (["hybrid", "mixed", "mix"].includes(raw)) return "hybrid";
  if (["ask", "ask_user", "clarify", "need_user_help"].includes(raw)) return "ask_user";
  if (["refuse", "deny", "blocked", "unsafe"].includes(raw)) return "refuse";
  return "visual_agent";
}

function normalizeAgentBrainExecutorName(value, fallbackRoute = "") {
  const raw = String(value || fallbackRoute || "").toLowerCase().trim().replace(/[-\s]+/g, "_");
  if (["device", "device_tool", "internal", "internal_tool", "system_tool"].includes(raw)) return "device_tool";
  return "visual_agent";
}

function normalizeAgentBrainRisk(value) {
  const raw = String(value || "").toLowerCase().trim().replace(/[-\s]+/g, "_");
  if (["critical", "danger", "dangerous", "very_high"].includes(raw)) return "critical";
  if (raw === "high") return "high";
  if (["medium", "mid"].includes(raw)) return "medium";
  return "low";
}

function normalizeAgentBrainToolName(value, executor = "") {
  const raw = String(value || "").toLowerCase().trim().replace(/[-\s]+/g, "_");
  const mapped = {
    open_application: "open_app",
    launch_app: "open_app",
    app_open: "open_app",
    settings: "open_system_settings",
    open_settings: "open_system_settings",
    system_settings: "open_system_settings",
    app_settings: "open_app_settings",
    app_info: "open_app_settings",
    open_app_detail: "open_app_settings",
    brightness: "set_brightness",
    screen_brightness: "set_brightness",
    screen_timeout: "set_screen_timeout",
    sleep_timeout: "set_screen_timeout",
    auto_rotate: "set_auto_rotate",
    rotation: "set_auto_rotate",
    accelerometer_rotation: "set_auto_rotate",
    media_volume: "set_media_volume",
    volume: "set_media_volume",
    set_volume: "set_media_volume",
    music_volume: "set_media_volume",
    wifi: "set_wifi_enabled",
    wi_fi: "set_wifi_enabled",
    set_wifi: "set_wifi_enabled",
    wifi_enabled: "set_wifi_enabled",
    bluetooth: "set_bluetooth_enabled",
    set_bluetooth: "set_bluetooth_enabled",
    bluetooth_enabled: "set_bluetooth_enabled",
    mobile_data: "set_mobile_data_enabled",
    cellular_data: "set_mobile_data_enabled",
    data_enabled: "set_mobile_data_enabled",
    set_data: "set_mobile_data_enabled",
    dark_mode: "set_dark_mode",
    night_mode: "set_dark_mode",
    ui_mode: "set_dark_mode",
    health: "device_status",
    device_health: "device_status",
    shell_status: "shizuku_status",
    enhanced_status: "shizuku_status",
    shizuku: "shizuku_status",
    shizuku_permission: "request_shizuku_permission",
    request_shizuku: "request_shizuku_permission",
    animation_scale: "set_animation_scale",
    force_stop: "force_stop_app",
    force_stop_application: "force_stop_app",
    clear_data: "clear_app_data",
    uninstall: "uninstall_app",
    disable: "disable_app",
    enable: "enable_app",
    add_ledger_record: "ledger_add_record",
    create_ledger_record: "ledger_add_record",
    ledger_record_add: "ledger_add_record",
    ledger_add: "ledger_add_record",
    set_ledger_budget: "ledger_set_budget",
    ledger_budget_set: "ledger_set_budget",
    budget_set: "ledger_set_budget",
    query_ledger_summary: "ledger_query_summary",
    ledger_summary: "ledger_query_summary",
    ledger_query: "ledger_query_summary",
    list_ledger_records: "ledger_list_records",
    ledger_records: "ledger_list_records",
    ledger_list: "ledger_list_records",
  };
  const normalized = mapped[raw] || raw;
  const allowed = new Set([...INTERNAL_TOOL_AGENT_STEP_TYPES, "visual_agent"]);
  if (allowed.has(normalized)) return normalized;
  return executor === "device_tool" ? "device_status" : "visual_agent";
}
function normalizeAgentBrainRoutePlan(value, originalGoal = "") {
  const raw = value && typeof value === "object" ? value : {};
  const nested = raw.agentBrainRoute || raw.agentBrain || raw.routePlan || raw.result || raw.plan || raw;
  let route = normalizeAgentBrainRouteName(nested.route || nested.mode || nested.executor);
  const risk = normalizeAgentBrainRisk(nested.risk || nested.riskLevel);
  const confidenceRaw = Number(nested.confidence ?? nested.score ?? 0);
  const confidence = Number.isFinite(confidenceRaw) ? Math.max(0, Math.min(1, confidenceRaw)) : 0;
  const rawSteps = Array.isArray(nested.steps)
    ? nested.steps
    : Array.isArray(nested.actions)
      ? nested.actions
      : nested.tool || nested.action
        ? [{
            executor: nested.executor || (["device_tool", "hybrid"].includes(route) ? "device_tool" : "visual_agent"),
            tool: nested.tool || nested.action,
            args: {
              ...(nested.args && typeof nested.args === "object" ? nested.args : {}),
              ...(nested.packageName ? { packageName: nested.packageName } : {}),
              ...(nested.appName ? { appName: nested.appName } : {}),
            },
            risk: nested.risk,
            requiresConfirmation: nested.requiresConfirmation,
            reason: nested.reason,
          }]
        : [];
  const steps = [];

  for (const item of rawSteps.slice(0, 4)) {
    if (!item || typeof item !== "object") continue;
    const executor = normalizeAgentBrainExecutorName(item.executor || item.route, route);
    const tool = normalizeAgentBrainToolName(item.tool || item.action || item.name, executor);
    const stepRisk = normalizeAgentBrainRisk(item.risk || item.riskLevel || risk);
    const args = item.args && typeof item.args === "object"
      ? item.args
      : item.arguments && typeof item.arguments === "object"
        ? item.arguments
        : {};
    steps.push({
      executor,
      tool,
      args,
      goal: executor === "visual_agent"
        ? safeText(originalGoal, 240)
        : safeText(item.goal || item.subgoal || "", 240),
      risk: stepRisk,
      requiresConfirmation: Boolean(item.requiresConfirmation || item.confirm || ["high", "critical"].includes(stepRisk)),
      reason: safeText(item.reason || item.rationale || "", 220),
    });
  }

  if (["ask_user", "refuse"].includes(route)) {
    steps.length = 0;
  } else if (!steps.length) {
    // A malformed route must never be repaired with a guessed local tool.
    // Safely hand the unchanged goal to GUI Plus instead.
    route = "visual_agent";
    steps.push({
      executor: "visual_agent",
      tool: "visual_agent",
      args: {},
      goal: safeText(originalGoal, 240),
      risk: "low",
      requiresConfirmation: false,
      reason: "AgentBrain did not provide a valid deterministic tool; hand the original goal to GUI Plus.",
    });
  }

  return {
    route,
    confidence,
    risk,
    reason: safeText(nested.reason || nested.rationale || "", 260),
    question: safeText(nested.question || nested.ask || "", 180),
    refusalReason: safeText(nested.refusalReason || nested.refuseReason || "", 220),
    steps,
  };
}

function compactAgentBrainMemoryForRoute(agentMemory, recentActions) {
  const memory = agentMemory && typeof agentMemory === "object" ? agentMemory : {};
  const loopSignals = memory.loopSignals && typeof memory.loopSignals === "object" ? memory.loopSignals : {};
  const feedback = memory.executionFeedback && typeof memory.executionFeedback === "object" ? memory.executionFeedback : {};
  const lastToolResponse = memory.lastToolResponse && typeof memory.lastToolResponse === "object"
    ? normalizeAndroidVisualToolResponse(memory.lastToolResponse)
    : null;
  return {
    recentActions: Array.isArray(memory.recentActions) ? memory.recentActions.slice(-3) : Array.isArray(recentActions) ? recentActions.slice(-3) : [],
    failedActions: Array.isArray(memory.failedActions) ? memory.failedActions.slice(-2) : [],
    blockedActions: Array.isArray(memory.blockedActions) ? memory.blockedActions.slice(-2) : [],
    verificationEvents: Array.isArray(memory.verificationEvents) ? memory.verificationEvents.slice(-3) : [],
    executionFeedback: {
      lastResultOk: nullableBooleanFromValue(feedback.lastResultOk),
      lastVerification: safeText(feedback.lastVerification || "unknown", 80),
      noProgressCount: Math.max(0, Number(feedback.noProgressCount || loopSignals.noProgressCount || 0) || 0),
      lastActionSignature: safeText(feedback.lastActionSignature || loopSignals.lastActionSignature || "", 160),
      latestEvent: safeText(feedback.latestEvent || "", 220),
    },
    lastToolResponse,
    finishVerificationRequested: agentMemoryRequestsFinishVerification(memory),
    routeRefreshRequested: agentMemoryRequestsRouteRefresh(memory),
    loopSignals: {
      executedStepCount: Number(loopSignals.executedStepCount || 0),
      loopIndex: Number(loopSignals.loopIndex || 0),
      noProgressCount: Math.max(0, Number(loopSignals.noProgressCount || feedback.noProgressCount || 0) || 0),
      sameActionCount: Math.max(0, Number(loopSignals.sameActionCount || feedback.sameActionCount || 0) || 0),
      lastVerification: safeText(loopSignals.lastVerification || feedback.lastVerification || "unknown", 80),
      finishVerificationRequested: agentMemoryRequestsFinishVerification(memory),
      routeRefreshRequested: agentMemoryRequestsRouteRefresh(memory),
    },
  };
}

function agentBrainRouteScreenForPrompt(snapshot) {
  return {
    app: safeText(snapshot?.currentApp || snapshot?.packageName || "", 80),
    pkg: safeText(snapshot?.packageName || snapshot?.currentApp || "", 80),
    nodes: Number(snapshot?.nodeCount || 0),
    capturedNodes: Number(snapshot?.capturedNodeCount || 0) || undefined,
    image: Boolean(snapshot?.confidence?.hasVisualImage || snapshot?.visual?.available),
    texts: (Array.isArray(snapshot?.texts) ? snapshot.texts : []).slice(0, 8),
    controls: (Array.isArray(snapshot?.clickableNodes) ? snapshot.clickableNodes : [])
      .map((node) => safeText(node?.text || "", 36))
      .filter(Boolean)
      .slice(0, 8),
  };
}

function agentBrainRouteAppCandidates(goal, snapshot, deviceContext, max = AGENT_BRAIN_ROUTE_APP_CANDIDATES_MAX) {
  const currentPackage = safeText(snapshot?.packageName || snapshot?.currentApp || "", 120);
  return canonicalVisualAppPairsFromDeviceContext(deviceContext, 160)
    .slice(0, Math.max(1, Math.min(160, Number(max || 160))))
    .map((app) => ({
      label: app.label,
      packageName: app.packageName,
      aliases: Array.isArray(app.aliases) ? app.aliases : [],
      capabilities: Array.isArray(app.capabilities) ? app.capabilities : [],
      source: "android_canonical_inventory",
      current: Boolean(currentPackage && app.packageName === currentPackage),
    }));
}

function isCloudRouteVisualLoopRequest(body) {
  return Boolean(
    isVisualAgentStepRequest(body) &&
      safeText(body?.agentSessionProtocol, 120) === ANDROID_CLOUD_ROUTE_VISUAL_PROTOCOL
  );
}

function compactAgentBrainDeviceForRoute(goal, snapshot, deviceContext) {
  const compactDevice = deviceContextSummaryForPrompt(deviceContext);
  const installedApps = agentBrainRouteAppCandidates(goal, snapshot, deviceContext, 160)
    .map((app) => [app.label, app.packageName]);
  return {
    currentApp: compactDevice.currentApp,
    screen: compactDevice.screen,
    installedAppCount: installedApps.length,
    installedApps,
  };
}

function agentBrainRouteCacheKey(goal, snapshot, deviceContext, agentMemory = null) {
  const device = compactAgentBrainDeviceForRoute(goal, snapshot, deviceContext);
  const screen = agentBrainRouteScreenForPrompt(snapshot);
  const verifiedSurface = verifiedVisualSurfaceProtocol(null, snapshot, deviceContext, agentMemory);
  const raw = JSON.stringify({
    goal: normalizeForMatch(goal).slice(0, 160),
    app: screen.app,
    pkg: screen.pkg,
    texts: screen.texts.slice(0, 6),
    controls: screen.controls.slice(0, 6),
    inventoryHash: safeText(deviceContext?.appInventoryHash || deviceContext?.inventory?.inventoryHash || "", 120),
    apps: (device.installedApps || []).map((app) => Array.isArray(app) ? `${app[0]}:${app[1]}` : `${app.label}:${app.packageName}`),
    surfaceState: verifiedSurface.surfaceState,
    selectedTargetPackage: verifiedSurface.selectedTargetPackage,
    verifiedTargetPackage: verifiedSurface.verifiedTargetPackage,
    observationId: verifiedSurface.observationId,
    guiPlusEligible: verifiedSurface.guiPlusEligible,
  });
  return crypto.createHash("sha1").update(raw).digest("hex");
}

function getCachedAgentBrainRoute(cacheKey) {
  const item = AGENT_BRAIN_ROUTE_CACHE.get(cacheKey);
  if (!item) return null;
  if (Date.now() - Number(item.createdAt || 0) > AGENT_BRAIN_ROUTE_CACHE_TTL_MS) {
    AGENT_BRAIN_ROUTE_CACHE.delete(cacheKey);
    return null;
  }
  return item.route || null;
}

function setCachedAgentBrainRoute(cacheKey, route) {
  if (!cacheKey || !route) return;
  AGENT_BRAIN_ROUTE_CACHE.set(cacheKey, { createdAt: Date.now(), route });
  if (AGENT_BRAIN_ROUTE_CACHE.size <= AGENT_BRAIN_ROUTE_CACHE_MAX) return;
  const entries = [...AGENT_BRAIN_ROUTE_CACHE.entries()].sort((a, b) => Number(a[1]?.createdAt || 0) - Number(b[1]?.createdAt || 0));
  for (const [key] of entries.slice(0, AGENT_BRAIN_ROUTE_CACHE.size - AGENT_BRAIN_ROUTE_CACHE_MAX)) {
    AGENT_BRAIN_ROUTE_CACHE.delete(key);
  }
}

function buildAgentBrainRouteMessages(goal, snapshot, recentActions, deviceContext, agentMemory) {
  const verifiedSurface = verifiedVisualSurfaceProtocol(null, snapshot, deviceContext, agentMemory);
  const payload = {
    goal: safeText(goal, 240),
    screen: agentBrainRouteScreenForPrompt(snapshot),
    device: compactAgentBrainDeviceForRoute(goal, snapshot, deviceContext),
    memory: compactAgentBrainMemoryForRoute(agentMemory, recentActions),
    verifiedSurface,
    routes: ["device_tool", "visual_agent", "hybrid", "ask_user", "refuse"],
    tools: [...INTERNAL_TOOL_AGENT_STEP_TYPES, "visual_agent"],
    task: "Understand the full user intent and choose only the execution route. Do not output coordinates.",
  };

  const system = [
    "你是 Android 智能体唯一的语义路由主脑 DeepSeek。只输出严格 JSON，不操作坐标，不替 GUI Plus 点击页面。",
    "完整理解用户原始指令，并在 Android 确定性内部工具与 GUI Plus 视觉循环之间选择。device.installedApps 是完整的 [应用名称, packageName] 事实目录；禁止用本地关键词规则代替你的语义判断。",
    "当 verifiedSurface.guiPlusEligible=false 且任务需要操作 App 页面时，必须 route=hybrid、tool=open_app，并从 device.installedApps 原样复制唯一目标的 appName 与 packageName。即使目标包已经在前台，也返回 open_app 以完成目标绑定。此时禁止 route=visual_agent。",
    "当 verifiedSurface.guiPlusEligible=true 时，App 已打开且 Android 已验证包名；需要页面操作的任务 route=visual_agent，并把用户原始目标逐字保留给 GUI Plus。",
    "route=device_tool 仅用于无需 GUI 的确定性系统或内部工具。只有 installedApps 中确实无法唯一确定目标且缺少信息时才 route=ask_user；用户明确说 QQ 且目录存在唯一 QQ 时不得 ask_user。",
    "GUI Plus 不负责在桌面、最近任务或助手界面寻找和启动 App。Android 只执行你返回的真实 packageName，并校验安装、可启动、安全确认和执行结果，不参与语义选 App。",
    "This call only selects the executor and, when needed, one installed app. It never performs the user's transaction or irreversible action. For purchases, payments, transfers, orders, posts, deletions, or other consequential GUI tasks, select/open the uniquely identifiable app and hand the original goal to GUI Plus; confirmation belongs immediately before the final consequential action inside that visual loop. Do not return empty merely because the eventual task is high risk.",
    "高风险内部工具必须标注 risk 和 requiresConfirmation；不允许的任务 route=refuse。",
    `仅返回一个紧凑 JSON：{"route":"device_tool|visual_agent|hybrid|ask_user|refuse","tool":"${[...INTERNAL_TOOL_AGENT_STEP_TYPES, "visual_agent"].join("|")}","appName":"","packageName":"","args":{},"risk":"low|medium|high|critical","requiresConfirmation":false,"reason":"","question":""}`,
  ].join("\n");

  return [
    { role: "system", content: system },
    { role: "user", content: JSON.stringify(payload) },
  ];
}

async function handleAgentBrainRouteRequest(body, prompt, resolvedModel) {
  const goal = safeText(body.agentGoal || body.goal || body.message || body.prompt || prompt, 240);
  const snapshot = compactScreenSnapshot(body.screenSnapshot || {});
  const rawDeviceContext = body.deviceContext && typeof body.deviceContext === "object" ? body.deviceContext : {};
  const deviceContext = mergeVisualAppContextIntoDeviceContext(body, { ...rawDeviceContext });
  if (body.targetAppResolution && typeof body.targetAppResolution === "object") {
    deviceContext.targetAppResolution = body.targetAppResolution;
  }
  const recentActions = Array.isArray(body.recentAgentActions)
    ? body.recentAgentActions
    : Array.isArray(body.recentActions)
      ? body.recentActions
      : [];
  const agentMemory = normalizeAndroidVisualExecutionProtocol(
    body,
    body.agentMemory && typeof body.agentMemory === "object" ? body.agentMemory : {},
    recentActions
  );

  if (!goal) {
    return { ok: false, error: "empty_agent_goal", code: "empty_agent_goal", version: WORKER_VERSION };
  }

  let raw = "";
  let parsed = {};
  let route = null;
  let errorText = "";
  const startedAt = Date.now();
  const verifiedSurface = verifiedVisualSurfaceProtocol(body, snapshot, deviceContext, agentMemory);
  const cacheKey = agentBrainRouteCacheKey(goal, snapshot, deviceContext, agentMemory);
  const forceRefresh = Boolean(agentMemoryRequestsRouteRefresh(agentMemory) || verifiedSurface.routeRefreshRequested);
  if (forceRefresh) AGENT_BRAIN_ROUTE_CACHE.delete(cacheKey);
  const cachedRoute = forceRefresh ? null : getCachedAgentBrainRoute(cacheKey);
  if (cachedRoute) {
    return {
      ok: true,
      reply: "AgentBrain 路由已完成。",
      agentBrainRoute: cachedRoute,
      routePlan: cachedRoute,
      source: "agent_brain_route_cache",
      sourceDetail: "deepseek_v4_cached",
      model: "deepseek_v4",
      modelId: "deepseek_v4",
      modelLabel: "DeepSeek V4 Pro",
      providerModel: process.env.DEEPSEEK_MODEL || "",
      elapsedMs: Date.now() - startedAt,
      cached: true,
      verifiedSurfaceProtocol: verifiedSurface,
      version: WORKER_VERSION,
    };
  }

  try {
    raw = await callOpenAICompatibleJsonFirst(
      process.env.DEEPSEEK_BASE_URL,
      process.env.DEEPSEEK_API_KEY,
      process.env.DEEPSEEK_MODEL,
      buildAgentBrainRouteMessages(goal, snapshot, recentActions, deviceContext, agentMemory),
      "DeepSeek AgentBrain Route",
      {
        temperature: 0,
        max_tokens: AGENT_BRAIN_ROUTE_MAX_TOKENS,
        timeoutMs: AGENT_BRAIN_ROUTE_TIMEOUT_MS,
        response_format: { type: "json_object" },
      }
    );
    try { parsed = JSON.parse(extractJsonText(raw)); } catch (_) { parsed = {}; }
    route = normalizeAgentBrainRoutePlan(parsed, goal);
    setCachedAgentBrainRoute(cacheKey, route);
  } catch (error) {
    errorText = sanitizeProviderError(error, 160);
    route = normalizeAgentBrainRoutePlan({
      route: "ask_user",
      confidence: 0.25,
      risk: "low",
      reason: `DeepSeek AgentBrain 路由暂不可用；在目标工作界面验证前不会启动 GUI Plus：${errorText}`,
      question: "云端主脑暂时无法确定目标应用，请稍后重试。",
      steps: [],
    }, goal);
  }

  return {
    ok: true,
    reply: "AgentBrain 路由已完成。",
    agentBrainRoute: route,
    routePlan: route,
    source: errorText ? "agent_brain_route_fallback" : "agent_brain_route",
    sourceDetail: errorText ? "deepseek_error_safe_pause" : "deepseek_v4",
    model: "deepseek_v4",
    modelId: "deepseek_v4",
    modelLabel: "DeepSeek V4 Pro",
    providerModel: process.env.DEEPSEEK_MODEL || "",
    elapsedMs: Date.now() - startedAt,
    cached: false,
    verifiedSurfaceProtocol: verifiedSurface,
    appCandidateCount: agentBrainRouteAppCandidates(goal, snapshot, deviceContext).length,
    error: errorText || undefined,
    raw: String(raw || "").slice(0, 900),
    version: WORKER_VERSION,
  };
}


async function resolveAgentBrainRouteForStep(goal, snapshot, recentActions, deviceContext, agentMemory, startedAt, options = {}) {
  const cleanGoal = safeText(goal, 240);
  const started = Date.now();
  if (!cleanGoal) {
    return { route: null, source: "agent_brain_empty_goal", elapsedMs: Date.now() - started, error: "empty_goal", cached: false };
  }
  const verifiedSurface = verifiedVisualSurfaceProtocol(null, snapshot, deviceContext, agentMemory);
  const useCache = options?.useCache !== false;
  const cacheKey = useCache ? agentBrainRouteCacheKey(cleanGoal, snapshot, deviceContext, agentMemory) : "";
  const forceRefresh = Boolean(options?.forceRefresh || agentMemoryRequestsRouteRefresh(agentMemory) || verifiedSurface.routeRefreshRequested);
  if (useCache && forceRefresh) AGENT_BRAIN_ROUTE_CACHE.delete(cacheKey);
  const cachedRoute = useCache && !forceRefresh ? getCachedAgentBrainRoute(cacheKey) : null;
  if (cachedRoute) return { route: cachedRoute, source: "agent_brain_route_cache", elapsedMs: Date.now() - started, error: "", cached: true };

  const timeoutMs = boundedAgentTimeoutMs(AGENT_BRAIN_ROUTE_TIMEOUT_MS, agentRemainingBudgetMs(startedAt), AGENT_BRAIN_ROUTE_TIMEOUT_MS);
  if (!process.env.DEEPSEEK_API_KEY || !process.env.DEEPSEEK_BASE_URL || !process.env.DEEPSEEK_MODEL || timeoutMs < 500) {
    return {
      route: null,
      source: "agent_brain_unavailable_no_local_route",
      elapsedMs: Date.now() - started,
      error: "agent_brain_unavailable",
      cached: false,
    };
  }

  try {
    const raw = await callOpenAICompatible(
      process.env.DEEPSEEK_BASE_URL,
      process.env.DEEPSEEK_API_KEY,
      process.env.DEEPSEEK_MODEL,
      buildAgentBrainRouteMessages(cleanGoal, snapshot, recentActions, deviceContext, agentMemory),
      "DeepSeek AgentBrain Route Step",
      { temperature: 0, max_tokens: AGENT_BRAIN_ROUTE_MAX_TOKENS, timeoutMs }
    );
    let parsed = {};
    try { parsed = JSON.parse(extractJsonText(raw)); } catch (_) { parsed = {}; }
    const nested = parsed?.agentBrainRoute || parsed?.agentBrain || parsed?.routePlan || parsed?.result || parsed?.plan || parsed;
    const hasExplicitRoute = Boolean(safeText(nested?.route || nested?.mode || nested?.executor || "", 40));
    const hasExplicitSteps = Boolean(
      (Array.isArray(nested?.steps) && nested.steps.length) ||
      (Array.isArray(nested?.actions) && nested.actions.length)
    );
    if (!hasExplicitRoute && !hasExplicitSteps) {
      throw new Error("DeepSeek AgentBrain returned no explicit route decision");
    }
    const route = normalizeAgentBrainRoutePlan(parsed, cleanGoal);
    if (useCache) setCachedAgentBrainRoute(cacheKey, route);
    return { route, source: "agent_brain_route_step_deepseek", elapsedMs: Date.now() - started, error: "", cached: false, raw: safeText(raw, 800) };
  } catch (error) {
    return {
      route: null,
      source: "agent_brain_error_no_local_route",
      elapsedMs: Date.now() - started,
      error: sanitizeProviderError(error, 180),
      cached: false,
    };
  }
}

function agentBrainRoutePromptBlock(route) {
  if (!route || typeof route !== "object") {
    return "DeepSeek AgentBrain route: unavailable. GUI Plus must decide from the complete original instruction, current screenshot, installed apps and Android tool_response.";
  }
  const steps = Array.isArray(route.steps) ? route.steps.slice(0, 4) : [];
  const stepLine = steps.length ? steps.map((step, index) => {
    const args = step?.args && typeof step.args === "object" ? step.args : {};
    const target = safeText(args.appName || args.app || args.packageName || step.goal || step.reason || "", 90);
    return `${index + 1}. ${step.executor || "visual_agent"}/${step.tool || "visual_agent"}${target ? ` -> ${target}` : ""}`;
  }).join("；") : "无明确步骤";
  return [
    "DeepSeek AgentBrain cloud decision context:",
    `- route=${route.route || "visual_agent"}; confidence=${Number(route.confidence || 0).toFixed(2)}; risk=${route.risk || "low"}`,
    `- reason=${safeText(route.reason || route.refusalReason || route.question || "", 240)}`,
    `- steps=${stepLine}`,
    "This is cloud-model guidance, not a backend-generated semantic rule.",
    "The complete original user instruction remains authoritative. If a clarification request conflicts with information already explicit in the original instruction or canonical installed-app list, do not ask again; act on the explicit information.",
    "GUI Plus owns the current visual action. The backend must not invent an app, subgoal, route, completion result or user question."
  ].join("\n");
}

function firstAgentBrainStep(route, predicate) {
  const steps = Array.isArray(route?.steps) ? route.steps : [];
  return steps.find((step) => step && typeof step === "object" && (typeof predicate === "function" ? predicate(step) : true)) || null;
}

function agentBrainRouteStepToAgentStepPayload(route, routeStep, tool, goal) {
  const args = agentBrainStepArgs(routeStep);
  const risk = normalizeAgentBrainRisk(routeStep?.risk || route?.risk);
  const riskLevel = ["high", "critical"].includes(risk) ? "high" : risk === "medium" ? "medium" : "low";
  const reason = safeText(routeStep?.reason || route?.reason || `AgentBrain 选择结构化工具：${tool}`, 260);
  return {
    type: tool,
    tool,
    action: tool,
    appName: safeText(args.appName || args.app || args.label || args.name || routeStep?.appName || routeStep?.targetApp || "", 80) || undefined,
    packageName: safeText(args.packageName || args.package || args.pkg || routeStep?.packageName || "", 120) || undefined,
    targetText: safeText(args.targetText || args.target || args.label || args.name || args.page || args.kind || routeStep?.targetText || "", 80) || undefined,
    text: safeText(args.text || args.inputText || args.value || args.query || routeStep?.text || routeStep?.goal || "", 180) || undefined,
    durationMs: Number.isFinite(Number(args.durationMs || args.waitMs || args.delayMs)) ? Number(args.durationMs || args.waitMs || args.delayMs) : undefined,
    reason,
    riskLevel,
    requiresConfirmation: Boolean(routeStep?.requiresConfirmation || routeStep?.confirm || ["high", "critical"].includes(risk)),
    args,
    arguments: args,
  };
}

function shouldUseAgentBrainDirectPreflight(preflightPlan, route) {
  if (!preflightPlan?.agentStep || !route || typeof route !== "object") return false;
  const routeName = normalizeAgentBrainRouteName(route.route || "visual_agent");
  const stepType = normalizeAgentStepType(preflightPlan.agentStep.type);
  if (["ask_user", "refuse"].includes(routeName)) return true;
  if (routeName === "device_tool") return stepType === "open_app" || isInternalToolAgentStepType(stepType);
  if (routeName === "hybrid") return stepType === "open_app" || isInternalToolAgentStepType(stepType);
  return false;
}

function canonicalInstalledAppForAgentBrainStep(routeStep, deviceContext) {
  if (!routeStep || typeof routeStep !== "object") return null;
  const args = agentBrainStepArgs(routeStep);
  const requestedPackage = safeText(
    args.packageName || args.package || args.pkg || routeStep.packageName || "",
    120
  );
  const apps = canonicalVisualAppPairsFromDeviceContext(deviceContext, 160);
  if (!requestedPackage) return null;
  return apps.find((app) => app.packageName === requestedPackage) || null;
}

function agentBrainRouteToDirectAgentPlan(route, snapshot, supportedSteps, goal, screenshotInfo, deviceContext, reasonTag = "agent_brain_direct") {
  if (!route || typeof route !== "object") return null;
  const routeName = normalizeAgentBrainRouteName(route.route || "visual_agent");

  if (routeName === "ask_user") {
    const reason = safeText(route.question || route.reason || "AgentBrain 认为缺少不可推断的关键信息。", 260);
    const step = normalizeAgentStep({ agentStep: { type: "need_user_help", reason, riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    const state = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: false, isWrong: false, confidence: Math.max(0.3, Number(route.confidence || 0)), reason } }, step);
    return { agentStep: step, agentState: state, source: `${reasonTag}_ask_user` };
  }

  if (routeName === "refuse") {
    const reason = safeText(route.refusalReason || route.reason || "AgentBrain 拒绝执行该任务。", 260);
    const step = normalizeAgentStep({ agentStep: { type: "need_user_help", reason: `REFUSED_BY_AGENT_BRAIN: ${reason}`, riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    const state = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: false, isWrong: false, confidence: Math.max(0.4, Number(route.confidence || 0)), reason } }, step);
    return { agentStep: step, agentState: state, source: `${reasonTag}_refuse` };
  }

  const steps = Array.isArray(route.steps) ? route.steps : [];
  for (const routeStep of steps) {
    if (!routeStep || typeof routeStep !== "object") continue;
    const executor = normalizeAgentBrainExecutorName(routeStep.executor || routeStep.route, routeName);
    const tool = normalizeAgentBrainToolName(routeStep.tool || routeStep.action || routeStep.name, executor);
    if (executor !== "device_tool" || !isInternalToolAgentStepType(tool)) continue;

    let normalizedArgs = normalizeInternalDeviceToolArgsForAndroid(tool, agentBrainStepArgs(routeStep));
    if (tool === "open_app") {
      const canonical = canonicalInstalledAppForAgentBrainStep(routeStep, deviceContext);
      if (!canonical) {
        // Do not ask the user and do not guess. GUI Plus receives the original goal and full app inventory.
        continue;
      }
      normalizedArgs = {
        ...normalizedArgs,
        appName: canonical.label,
        packageName: canonical.packageName,
      };
    }

    const validation = validateInternalToolArgs(tool, normalizedArgs);
    if (!validation.ok) {
      const reason = safeText(validation.question || route.question || "内部工具缺少不可推断的必要参数。", 260);
      const step = normalizeAgentStep({ agentStep: { type: "need_user_help", reason, riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
      const state = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: false, isWrong: false, confidence: Math.max(0.55, Number(route.confidence || 0.7)), reason, nextHint: reason } }, step);
      return { agentStep: step, agentState: state, source: `${reasonTag}_${tool}_missing_args` };
    }

    if (!supportedSteps.includes(tool)) continue;
    const routeStepWithNormalizedArgs = { ...routeStep, args: normalizedArgs, arguments: normalizedArgs };
    const step = normalizeAgentStep(
      { agentStep: agentBrainRouteStepToAgentStepPayload(route, routeStepWithNormalizedArgs, tool, goal) },
      snapshot,
      supportedSteps,
      goal,
      screenshotInfo,
      deviceContext
    );
    const state = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: true, isWrong: false, confidence: Math.max(0.58, Number(route.confidence || 0.7)), reason: step.reason, nextHint: goal } }, step);
    return { agentStep: step, agentState: state, source: `${reasonTag}_${tool}` };
  }

  // visual_agent / hybrid after deterministic preflight: GUI Plus owns the next visual decision.
  return null;
}

function buildRootInternalDeviceToolPlan(tool, args, goal, snapshot, supportedSteps, screenshotInfo, deviceContext, reason, risk = "low") {
  const type = normalizeAgentStepType(tool);
  if (!SUPPORTED_AGENT_STEP_TYPES.includes(type) || !supportedSteps.includes(type)) return null;
  const finalRisk = normalizeRiskLevel(risk);
  const step = normalizeAgentStep({
    agentStep: {
      type,
      reason: safeText(reason || "普通聊天内部设备工具快速规划器直接命中结构化工具。", 260),
      riskLevel: finalRisk,
      requiresConfirmation: ["high", "critical"].includes(finalRisk),
      args: args && typeof args === "object" ? args : {},
      arguments: args && typeof args === "object" ? args : {},
    },
  }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
  const state = normalizeAgentState({
    agentState: {
      isComplete: false,
      expectedProgress: true,
      isWrong: false,
      confidence: 0.96,
      reason: step.reason,
      nextHint: safeText(goal, 120),
    },
  }, step);
  return {
    agentStep: step,
    agentState: state,
    source: `normal_chat_root_internal_${type}`,
  };
}

function buildAiInternalDeviceToolPlannerMessages(goal, body, deviceContext, agentMemory) {
  const payload = {
    userGoal: safeText(goal || body?.agentGoal || body?.goal || body?.message || body?.prompt || "", 240),
    executionMode: "normal_chat_internal_tool",
    device: deviceContext && typeof deviceContext === "object" ? deviceContext : {},
    memory: agentMemory && typeof agentMemory === "object" ? agentMemory : {},
    allowedTools: INTERNAL_TOOL_AGENT_STEP_TYPES,
    outputContract: {
      internalDeviceToolPlan: {
        handled: "boolean; true only when the request should be executed as an Android internal device tool now",
        tool: "one allowed tool or empty string",
        args: "object; canonical tool arguments. Device tools keep their existing contracts. ledger_add_record uses amount, recordType, title, category, date; ledger_set_budget uses amount; ledger_query_summary/ledger_list_records use range and optional recordType/category/limit",
        risk: "low|medium|high|critical",
        requiresConfirmation: "boolean; true for high/critical or destructive actions",
        reason: "short Chinese reason for execution result UI",
        confidence: "0.0-1.0"
      }
    }
  };
  const system = [
    "你是 Android App 的内部工具规划器，只输出 JSON，不输出教程、不输出自然语言回答。",
    "你负责真正理解任意用户自然语言指令，并判断它是否应当由 App 原生内部工具直接执行。",
    "禁止按固定关键词或正则硬匹配意图；必须结合整句话、上下文和用户是否在发出真实执行请求来判断。系统控制、应用启动、设备状态和原生账本能力都属于内部工具。",
    "如果用户只是在问知识、教程、比较、解释、举例、写作或闲聊，返回 handled=false。目标需要看屏幕点击 App 内 UI 时也返回 handled=false；打开 App 本身属于内部工具，App 打开后的页面导航才交给视觉智能。",
    "内部工具语义：打开/启动某个 App => open_app，args.appName 或 args.packageName 必须尽量给出；设备/手机/电量/电池/内存/存储/网络/系统健康/手机状态/当前状态 => device_status。",
    "Shizuku/增强模式/shell 状态 => shizuku_status；请求/申请 Shizuku 授权 => request_shizuku_permission。",
    "打开系统设置页 => open_system_settings，args.page/kind 必须用规范值之一：system,wifi,bluetooth,battery,display,notification,accessibility,apps,storage,sound,location,data,developer,dnd。开发者/开发人员选项一律 page=developer；WiFi/无线网一律 page=wifi；勿扰/免打扰一律 page=dnd；电池/省电/电池优化一律 page=battery。",
    "打开某 App 的应用信息/通知/权限/电池后台入口 => open_app_settings，并尽量给 args.appName 或 args.packageName，args.page 可为 details,notification,permission,battery。",
    "设置亮度 => set_brightness。用户给出明确百分比时 args.percent 为 0-100；用户说调低点/暗一点/降低亮度时 args.deltaPercent=-15；用户说调高点/亮一点/提高亮度时 args.deltaPercent=15；也可给 args.operation=decrease|increase 但优先给 deltaPercent。",
    "设置媒体音量 => set_media_volume。用户给百分比时 args.percent 为 0-100；调高/调低时给 args.deltaPercent=15 或 -15。",
    "设置息屏/自动锁屏时间 => set_screen_timeout，args.timeoutMs 为毫秒；设置自动旋转 => set_auto_rotate，args.enabled 为 true/false。",
    "Wi‑Fi 开关 => set_wifi_enabled；蓝牙开关 => set_bluetooth_enabled；移动数据开关 => set_mobile_data_enabled；深色模式 => set_dark_mode。开/打开/启用/on => args.enabled=true；关/关闭/禁用/off => args.enabled=false；深色模式自动/跟随系统可给 args.mode=auto。",
    "动画缩放 => set_animation_scale，args.scale 为数值。强停/清除数据/卸载/禁用/启用 App 必须给对应 tool 和目标 appName/packageName；清数据/卸载/禁用为 critical，强停/动画缩放为 high，启用通常 medium。",
    "账本新增 => ledger_add_record。必须从语义中可靠得到 args.amount（正数）和 args.recordType（expense 或 income）；args.title 是简短事项名，args.category 只能是餐饮、交通、购物、居住、饮品、工资、礼物、其他之一，未指定日期用 args.date=today。像“记一笔午饭18元”应理解为支出 18 元、标题午饭、分类餐饮，而不是靠本地词表套模板。",
    "设置本月预算 => ledger_set_budget，args.amount 必须是非负数。查询收支汇总 => ledger_query_summary；查询账单列表/最近明细 => ledger_list_records。查询工具 args.range 可为 current_month、last_month、last_30_days、current_year、all，recordType/category 可选，ledger_list_records 的 limit 为 1-20。",
    "账本工具均为低风险原生数据工具，requiresConfirmation=false。只有用户明确要求执行记账、改预算或读取自己的账本时 handled=true；讨论如何记账、假设示例、仅陈述价格、金额不明确或新增账单的收支方向无法可靠判断时 handled=false，让普通聊天继续追问，禁止猜金额。",
    "如果一句话既可执行又可回答，应根据完整语义判断用户是否在委托立即执行；不要使用固定命令词表决定。",
    "返回格式必须严格为：{\"internalDeviceToolPlan\":{\"handled\":true|false,\"tool\":\"...\",\"args\":{},\"risk\":\"low\",\"requiresConfirmation\":false,\"reason\":\"...\",\"confidence\":0.0}}"
  ].join("\n");
  return [
    { role: "system", content: system },
    { role: "user", content: JSON.stringify(payload) }
  ];
}
function normalizeInternalDeviceToolArgsForAndroid(tool, args) {
  const raw = args && typeof args === "object" && !Array.isArray(args) ? args : {};
  const out = { ...raw };
  const normalizeText = (value) => String(value || "").trim().toLowerCase().replace(/[\s_-]+/g, "_");
  const numberOrNull = (value) => {
    if (value === null || value === undefined || value === "") return null;
    const n = Number(String(value).replace("%", ""));
    return Number.isFinite(n) ? n : null;
  };
  const boolOrNull = (value) => {
    if (value === true || value === false) return value;
    const text = normalizeText(value);
    if (["true", "1", "yes", "on", "enable", "enabled", "open", "start", "开启", "打开", "启用", "开"].includes(text)) return true;
    if (["false", "0", "no", "off", "disable", "disabled", "close", "stop", "关闭", "关掉", "禁用", "关"].includes(text)) return false;
    return null;
  };
  const normalizePercentTool = (defaultDelta) => {
    const percent = numberOrNull(out.percent ?? out.brightness ?? out.volume ?? out.value);
    if (percent !== null) out.percent = Math.max(0, Math.min(100, percent));
    const delta = numberOrNull(out.deltaPercent ?? out.delta ?? out.brightnessDelta ?? out.volumeDelta ?? out.changePercent ?? out.adjustBy);
    if (delta !== null && percent === null) out.deltaPercent = Math.max(-100, Math.min(100, delta));
    const op = normalizeText(out.operation || out.mode || out.adjustment || out.relative || out.direction);
    if (out.deltaPercent === undefined && percent === null) {
      if (["decrease", "reduce", "lower", "down", "dim", "darker", "less", "mute", "调低", "降低", "变暗", "小一点", "减小"].includes(op)) out.deltaPercent = -defaultDelta;
      if (["increase", "raise", "higher", "up", "brighten", "brighter", "more", "调高", "提高", "变亮", "大一点", "增大"].includes(op)) out.deltaPercent = defaultDelta;
    }
  };
  const normalizeSwitchTool = () => {
    const enabled = boolOrNull(out.enabled ?? out.enable ?? out.on ?? out.state ?? out.value ?? out.mode);
    if (enabled !== null) out.enabled = enabled;
  };

  if (tool === "open_app") {
    if (!out.appName && (out.app || out.application || out.label || out.name || out.target)) out.appName = out.app || out.application || out.label || out.name || out.target;
    if (!out.packageName && (out.package || out.pkg)) out.packageName = out.package || out.pkg;
  }

  if (tool === "open_system_settings") {
    const rawPage = normalizeText(out.page || out.kind || out.target || out.setting);
    const pageAliases = {
      wi_fi: "wifi",
      wifi_settings: "wifi",
      wireless: "wifi",
      battery_settings: "battery",
      battery_optimization: "battery",
      power: "battery",
      power_saving: "battery",
      developer_options: "developer",
      dev_options: "developer",
      developer_setting: "developer",
      development: "developer",
      application_development: "developer",
      app: "apps",
      application: "apps",
      applications: "apps",
      app_management: "apps",
      mobile_data: "data",
      data_usage: "data",
      network_data: "data",
      notifications: "notification",
      do_not_disturb: "dnd",
      zen: "dnd",
      zen_mode: "dnd",
      dnd_settings: "dnd",
      accessibility_settings: "accessibility",
      storage_settings: "storage",
      display_settings: "display",
      sound_settings: "sound",
      location_settings: "location",
    };
    const canonical = pageAliases[rawPage] || rawPage;
    const allowed = new Set(["system", "wifi", "bluetooth", "battery", "display", "notification", "accessibility", "apps", "storage", "sound", "location", "data", "developer", "dnd"]);
    if (allowed.has(canonical)) {
      out.page = canonical;
      out.kind = canonical;
    }
  }

  if (tool === "open_app_settings") {
    const rawPage = normalizeText(out.page || out.kind || out.target);
    const pageAliases = {
      info: "details",
      app_info: "details",
      application_info: "details",
      notifications: "notification",
      permission: "permission",
      permissions: "permission",
      battery_optimization: "battery",
      background: "battery",
      background_restriction: "battery",
    };
    const canonical = pageAliases[rawPage] || rawPage;
    if (["details", "notification", "permission", "battery"].includes(canonical)) {
      out.page = canonical;
      out.kind = canonical;
    }
  }

  if (tool === "set_brightness") normalizePercentTool(15);
  if (tool === "set_media_volume") normalizePercentTool(15);

  if (tool === "set_screen_timeout") {
    const timeoutMs = Number(out.timeoutMs ?? out.screenTimeoutMs);
    if (Number.isFinite(timeoutMs) && timeoutMs > 0) out.timeoutMs = Math.max(5000, Math.min(30 * 60 * 1000, Math.round(timeoutMs)));
    const seconds = Number(out.seconds ?? out.second ?? out.sec);
    if (!out.timeoutMs && Number.isFinite(seconds) && seconds > 0) out.timeoutMs = Math.max(5000, Math.min(30 * 60 * 1000, Math.round(seconds * 1000)));
    const minutes = Number(out.minutes ?? out.minute ?? out.min);
    if (!out.timeoutMs && Number.isFinite(minutes) && minutes > 0) out.timeoutMs = Math.max(5000, Math.min(30 * 60 * 1000, Math.round(minutes * 60000)));
  }

  if (["set_auto_rotate", "set_wifi_enabled", "set_bluetooth_enabled", "set_mobile_data_enabled"].includes(tool)) {
    normalizeSwitchTool();
  }

  if (tool === "set_dark_mode") {
    const mode = normalizeText(out.mode || out.state || out.value);
    if (["auto", "automatic", "follow", "system", "follow_system", "自动", "跟随系统"].includes(mode)) out.mode = "auto";
    else {
      normalizeSwitchTool();
      if (out.enabled === true) out.mode = "yes";
      if (out.enabled === false) out.mode = "no";
    }
  }

  if (tool === "set_animation_scale") {
    const scale = numberOrNull(out.scale ?? out.value);
    if (scale !== null) out.scale = Math.max(0, Math.min(10, scale));
  }

  const normalizeLedgerRecordType = (value) => {
    const type = normalizeText(value);
    if (["expense", "outcome", "spending", "支出"].includes(type)) return "expense";
    if (["income", "earning", "收入"].includes(type)) return "income";
    return "";
  };
  const normalizeLedgerCategory = (value) => {
    const category = normalizeText(value);
    const aliases = {
      food: "餐饮",
      meal: "餐饮",
      dining: "餐饮",
      餐饮: "餐饮",
      transport: "交通",
      transportation: "交通",
      交通: "交通",
      shopping: "购物",
      购物: "购物",
      housing: "居住",
      home: "居住",
      居住: "居住",
      drink: "饮品",
      beverage: "饮品",
      饮品: "饮品",
      salary: "工资",
      wage: "工资",
      工资: "工资",
      gift: "礼物",
      礼物: "礼物",
      other: "其他",
      others: "其他",
      其他: "其他",
    };
    return aliases[category] || "";
  };
  const normalizeLedgerRange = (value) => {
    const range = normalizeText(value);
    const aliases = {
      current_month: "current_month",
      this_month: "current_month",
      month: "current_month",
      本月: "current_month",
      last_month: "last_month",
      previous_month: "last_month",
      上月: "last_month",
      last_30_days: "last_30_days",
      recent_30_days: "last_30_days",
      "30_days": "last_30_days",
      最近30天: "last_30_days",
      current_year: "current_year",
      this_year: "current_year",
      year: "current_year",
      本年: "current_year",
      今年: "current_year",
      all: "all",
      all_time: "all",
      全部: "all",
    };
    return aliases[range] || "current_month";
  };

  if (tool === "ledger_add_record") {
    const amount = numberOrNull(out.amount ?? out.value);
    if (amount !== null && amount > 0) out.amount = amount;
    else delete out.amount;
    const recordType = normalizeLedgerRecordType(out.recordType ?? out.transactionType ?? out.entryType);
    if (recordType) out.recordType = recordType;
    else delete out.recordType;
    const title = safeText(out.title || out.name || out.description || "", 30);
    if (title) out.title = title;
    const category = normalizeLedgerCategory(out.category);
    out.category = category || "其他";
    const date = safeText(out.date || out.dateLabel || "today", 24);
    out.date = date || "today";
  }

  if (tool === "ledger_set_budget") {
    const amount = numberOrNull(out.amount ?? out.budget ?? out.value);
    if (amount !== null && amount >= 0) out.amount = amount;
    else delete out.amount;
  }

  if (["ledger_query_summary", "ledger_list_records"].includes(tool)) {
    out.range = normalizeLedgerRange(out.range ?? out.period ?? out.timeRange);
    const recordType = normalizeLedgerRecordType(out.recordType ?? out.transactionType ?? out.entryType);
    if (recordType) out.recordType = recordType;
    else {
      delete out.recordType;
      delete out.transactionType;
      delete out.entryType;
    }
    if (out.category !== undefined) {
      const category = normalizeLedgerCategory(out.category);
      if (category) out.category = category;
      else delete out.category;
    }
    if (tool === "ledger_list_records") {
      const limit = numberOrNull(out.limit ?? out.count);
      out.limit = limit === null ? 10 : Math.max(1, Math.min(20, Math.round(limit)));
    }
  }

  return Object.fromEntries(Object.entries(out).filter(([, v]) => v !== undefined && v !== null && String(v).trim() !== ""));
}

function validateInternalToolArgs(tool, args) {
  const normalizedTool = normalizeAgentStepType(tool);
  const raw = args && typeof args === "object" && !Array.isArray(args) ? args : {};
  if (normalizedTool === "ledger_add_record") {
    const amount = Number(raw.amount);
    if (!Number.isFinite(amount) || amount <= 0) {
      return { ok: false, question: "请告诉我要记录的具体金额。" };
    }
    if (!["expense", "income"].includes(String(raw.recordType || "").toLowerCase())) {
      return { ok: false, question: "这笔账是支出还是收入？" };
    }
  }
  if (normalizedTool === "ledger_set_budget") {
    const amount = Number(raw.amount);
    if (!Number.isFinite(amount) || amount < 0) {
      return { ok: false, question: "请告诉我要设置的本月预算金额。" };
    }
  }
  return { ok: true, question: "" };
}

function parseAiInternalDeviceToolDecision(rawText) {
  try {
    const parsed = JSON.parse(extractJsonText(rawText));
    const plan = parsed?.internalDeviceToolPlan || parsed?.deviceToolPlan || parsed?.plan || parsed;
    if (!plan || typeof plan !== "object") return null;
    const handled = plan.handled === true || plan.shouldExecute === true || plan.execute === true;
    const tool = normalizeAgentStepType(plan.tool || plan.type || plan.action || plan.name || "");
    const args = plan.args && typeof plan.args === "object" ? plan.args : (plan.arguments && typeof plan.arguments === "object" ? plan.arguments : {});
    const risk = normalizeRiskLevel(plan.risk || plan.riskLevel || "low");
    const confidence = Math.max(0, Math.min(1, Number(plan.confidence || 0)));
    const normalizedArgs = normalizeInternalDeviceToolArgsForAndroid(tool, args);
    const validation = validateInternalToolArgs(tool, normalizedArgs);
    return {
      handled: handled && validation.ok,
      tool,
      args: normalizedArgs,
      risk,
      requiresConfirmation: Boolean(plan.requiresConfirmation || ["high", "critical"].includes(risk)),
      reason: safeText(
        validation.ok
          ? (plan.reason || plan.explanation || "AI 内部工具规划器返回结构化内部工具。")
          : (validation.question || plan.reason || "内部工具参数不足，交回普通聊天补充信息。"),
        260
      ),
      confidence,
      validation,
    };
  } catch (error) {
    return null;
  }
}

async function resolveNormalChatInternalDeviceToolPlanByAI(goal, body, snapshot, supportedSteps, screenshotInfo, deviceContext, agentMemory, startedAt) {
  const preferredTimeoutMs = Math.max(
    4500,
    Math.min(9000, Number(process.env.INTERNAL_DEVICE_TOOL_PLANNER_TIMEOUT_MS || process.env.AGENT_INTERNAL_DEVICE_TOOL_PLANNER_TIMEOUT_MS || 6500))
  );
  const timeoutMs = boundedAgentTimeoutMs(
    preferredTimeoutMs,
    agentRemainingBudgetMs(startedAt),
    preferredTimeoutMs
  );
  const maxTokens = Math.max(
    420,
    Math.min(900, Number(process.env.INTERNAL_DEVICE_TOOL_PLANNER_MAX_TOKENS || AGENT_BRAIN_ROUTE_MAX_TOKENS || 520))
  );
  const messages = buildAiInternalDeviceToolPlannerMessages(goal, body, deviceContext, agentMemory);
  const providers = [
    {
      enabled: Boolean(process.env.DEEPSEEK_API_KEY && process.env.DEEPSEEK_BASE_URL && process.env.DEEPSEEK_MODEL),
      base: process.env.DEEPSEEK_BASE_URL,
      key: process.env.DEEPSEEK_API_KEY,
      model: process.env.DEEPSEEK_MODEL,
      name: "DeepSeek NormalChat Internal Device Tool Planner",
      source: "deepseek_internal_device_tool_planner",
      temperature: 0,
    },
    {
      enabled: Boolean(process.env.QWEN_API_KEY && process.env.QWEN_BASE_URL && process.env.QWEN_MODEL),
      base: process.env.QWEN_BASE_URL,
      key: process.env.QWEN_API_KEY,
      model: process.env.QWEN_MODEL,
      name: "Qwen NormalChat Internal Device Tool Planner Fallback",
      source: "qwen_internal_device_tool_planner_fallback",
      temperature: 0,
    },
  ];

  const attempts = [];

  const buildAcceptedPlan = (decision, providerSource, rawPreview, warning = "") => {
    const finalRisk = normalizeRiskLevel(decision.risk || "low");
    const step = normalizeAgentStep({
      agentStep: {
        type: decision.tool,
        reason: decision.reason || "AI 已识别为可执行的 Android 原生内部工具。",
        riskLevel: finalRisk,
        requiresConfirmation: Boolean(decision.requiresConfirmation || ["high", "critical"].includes(finalRisk)),
        args: decision.args || {},
        arguments: decision.args || {},
      },
    }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    const state = normalizeAgentState({
      agentState: {
        isComplete: false,
        expectedProgress: true,
        isWrong: false,
        confidence: Math.max(0.62, Number(decision.confidence || 0.82)),
        reason: step.reason,
        nextHint: safeText(goal, 120),
      },
    }, step);
    return {
      plan: {
        agentStep: step,
        agentState: state,
        source: `normal_chat_ai_internal_${decision.tool}`,
      },
      debug: {
        planner: "ai_internal_device_tool_planner",
        plannerMode: "multi_provider_ai_semantic_planner",
        provider: providerSource,
        handled: true,
        tool: decision.tool,
        confidence: Number(decision.confidence || 0),
        risk: finalRisk,
        timeoutMs,
        maxTokens,
        warning,
        attempts,
        raw: safeText(rawPreview, 260),
      },
    };
  };

  for (const provider of providers) {
    if (!provider.enabled) {
      attempts.push({ provider: provider.source, enabled: false, error: "provider_not_configured" });
      continue;
    }

    let raw = "";
    let decision = null;
    try {
      raw = await callOpenAICompatibleJsonFirst(
        provider.base,
        provider.key,
        provider.model,
        messages,
        provider.name,
        {
          temperature: provider.temperature,
          max_tokens: maxTokens,
          timeoutMs,
          response_format: { type: "json_object" },
        }
      );
      decision = parseAiInternalDeviceToolDecision(raw);
      attempts.push({
        provider: provider.source,
        enabled: true,
        parsed: Boolean(decision),
        handled: Boolean(decision?.handled),
        tool: decision?.tool || "",
        confidence: Number(decision?.confidence || 0),
        raw: safeText(raw, 160),
      });
    } catch (error) {
      attempts.push({
        provider: provider.source,
        enabled: true,
        error: sanitizeProviderError(error, 160),
      });
      continue;
    }

    if (!decision || decision.handled !== true || !decision.tool || !isInternalToolAgentStepType(decision.tool)) {
      continue;
    }

    if (!supportedSteps.includes(decision.tool) || !SUPPORTED_AGENT_STEP_TYPES.includes(decision.tool)) {
      attempts.push({
        provider: provider.source,
        rejectedTool: decision.tool,
        error: "tool_not_supported_by_android_client",
      });
      continue;
    }

    return buildAcceptedPlan(decision, provider.source, raw);
  }

  return {
    plan: null,
    debug: {
      planner: "ai_internal_device_tool_planner",
      plannerMode: "multi_provider_ai_semantic_planner",
      handled: false,
      tool: "",
      confidence: 0,
      timeoutMs,
      maxTokens,
      error: attempts.map((item) => `${item.provider}:${item.error || item.tool || item.handled || "no_match"}`).join(" | ").slice(0, 260),
      attempts,
    },
  };
}

function normalChatNoInternalDeviceToolPlan(goal, snapshot, supportedSteps, screenshotInfo, deviceContext, startedAt, requestBytes, readBodyMs) {
  const reason = "NOT_INTERNAL_DEVICE_TOOL";
  const step = normalizeAgentStep({ agentStep: { type: "need_user_help", reason, riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
  return {
    ok: true,
    reply: "该请求不是可直接执行的内部设备工具，交回普通聊天。",
    agentStep: step,
    agentState: { isComplete: false, expectedProgress: false, isWrong: false, confidence: 0.2, reason },
    source: "normal_chat_no_device_tool_root",
    debug: { totalMs: Date.now() - startedAt, readBodyMs, requestBytes, visualCalled: false, normalChatDeviceToolMode: true, rootInternalPlanner: "no_match" },
    version: WORKER_VERSION,
  };
}


function taskContractOpenCandidatePlan(contract, snapshot, supportedSteps, goal, screenshotInfo, deviceContext, reasonTag = "task_contract_open_candidate") {
  if (!contract || typeof contract !== "object" || !supportedSteps.includes("open_app")) return null;
  const source = String(contract.contractSource || contract.source || "").toLowerCase();
  const neutral = source.includes("neutral") || source.includes("rule_fallback");
  if (neutral || Number(contract.confidence || 0) < 0.45) return null;
  const currentPackage = safeText(snapshot?.packageName || snapshot?.currentApp || deviceContext?.currentApp?.packageName || "", 120);
  const assistantHost = isAssistantHostAppPackage(currentPackage) || snapshotLooksLikeAssistantChat(snapshot);
  const required = contract.requiredApp && typeof contract.requiredApp === "object" ? contract.requiredApp : null;
  const candidate = required || (Array.isArray(contract.appCandidates) ? contract.appCandidates[0] : null);
  if (!candidate || (!candidate.label && !candidate.packageName && !candidate.appName)) return null;
  if (required && currentAppSatisfiesTaskContract(contract, snapshot, deviceContext)) return null;
  if (!required && !assistantHost && currentPackage) return null;
  const appName = safeText(candidate.label || candidate.appName || candidate.name || candidate.packageName || "目标 App", 80);
  const packageName = safeText(candidate.packageName || candidate.package || "", 120);
  const reason = safeText(required ? `AgentBrain/TaskContractJudge 要求先打开指定 App：${appName}。` : `AgentBrain/TaskContractJudge 给出候选 App：${appName}，当前仍在非目标页，先打开该 App 再交给 GUI Plus 视觉执行。`, 260);
  const step = normalizeAgentStep({ agentStep: { type: "open_app", appName, packageName: packageName || undefined, targetText: appName, reason, riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
  const state = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: true, isWrong: false, confidence: Math.max(0.55, Number(contract.confidence || 0.6)), reason, nextHint: `打开 ${appName} 后继续：${contract.targetEntity || goal}` } }, step);
  return { agentStep: step, agentState: state, source: reasonTag };
}


const AGENT_TASK_EXECUTION_CONTRACT_SCHEMA = "agent_task_execution_contract_v1";

function normalizeTaskExecutionPhase(value) {
  const raw = String(value || "").trim().toLowerCase().replace(/[\s\-]+/g, "_");
  if (["resolve_requirements", "resolving_requirements"].includes(raw)) return "resolve_requirements";
  if (["resolve_target_app", "resolve_app", "resolving_target_app"].includes(raw)) return "resolve_target_app";
  if (["open_target_app", "opening_target_app", "leave_assistant_open_target_app"].includes(raw)) return "open_target_app";
  if (["verify_target_app", "verifying_target_app"].includes(raw)) return "verify_target_app";
  if (["visual_navigation", "visual_execute", "navigate", "navigating"].includes(raw)) return "visual_navigation";
  if (["verify_result", "verifying_result", "verify_completion"].includes(raw)) return "verify_result";
  if (["completed", "complete", "done", "success"].includes(raw)) return "completed";
  if (["internal_control", "device_tool"].includes(raw)) return "internal_control";
  if (["need_user_help", "user_assistance", "blocked", "paused"].includes(raw)) return "user_assistance";
  return "resolve_target_app";
}

function taskExecutionAllowedActions(phase, mode = "visual") {
  if (mode === "internal_device_tool" || phase === "internal_control") {
    return ["open_app", "open_system_settings", "open_app_settings", "set_brightness", "set_screen_timeout", "set_auto_rotate", "set_media_volume", "set_wifi_enabled", "set_bluetooth_enabled", "set_mobile_data_enabled", "set_dark_mode", "device_status", "shizuku_status", "request_shizuku_permission", "set_animation_scale", "force_stop_app", "clear_app_data", "uninstall_app", "disable_app", "enable_app", "ledger_add_record", "ledger_set_budget", "ledger_query_summary", "ledger_list_records", "need_user_help"];
  }
  if (phase === "resolve_requirements") return ["wait", "need_user_help"];
  if (phase === "resolve_target_app") return ["open_app", "wait", "need_user_help"];
  if (phase === "open_target_app") return ["open_app", "need_user_help"];
  if (phase === "verify_target_app") return ["open_app", "wait", "need_user_help"];
  if (phase === "verify_result") return ["finish", "wait", "need_user_help"];
  if (phase === "completed") return ["finish"];
  if (phase === "user_assistance") return ["need_user_help"];
  return ["open_app", "tap_node", "tap_xy", "input_text", "scroll", "swipe", "back", "wait", "finish", "need_user_help"];
}

function taskExecutionRouteOpenStep(route) {
  if (!route || typeof route !== "object") return null;
  const candidates = [];
  if (Array.isArray(route.steps)) candidates.push(...route.steps);
  if (route.step && typeof route.step === "object") candidates.push(route.step);
  if (route.agentStep && typeof route.agentStep === "object") candidates.push(route.agentStep);
  return candidates.find((step) => normalizeAgentStepType(step?.type || step?.tool || step?.action || "") === "open_app") || null;
}


function normalizeExecutionTargetApp(candidate, deviceContext) {
  if (!candidate || typeof candidate !== "object") return null;
  const rawLabel = safeText(candidate.label || candidate.appName || candidate.name || candidate.title || candidate.targetText || "", 80);
  const rawPackage = safeText(candidate.packageName || candidate.package || candidate.pkg || "", 120);
  const installed = installedAppsFromDeviceContext(deviceContext);
  const packageMatch = rawPackage ? installed.find((app) => app.packageName === rawPackage) : null;
  if (packageMatch) {
    return {
      label: packageMatch.label || rawLabel || rawPackage,
      packageName: packageMatch.packageName,
      aliases: Array.isArray(packageMatch.aliases) ? packageMatch.aliases.slice(0, 8) : [],
      capabilityProfile: packageMatch.capabilityProfile || null,
      source: safeText(candidate.source || "installed_package_match", 80),
      confidence: Math.max(0, Math.min(1, Number(candidate.confidence || packageMatch.capabilityProfile?.confidence || 0.9))),
    };
  }
  const labelKey = normalizeForMatch(rawLabel);
  const labelMatch = labelKey ? installed.find((app) => {
    const values = [app.label, app.packageName, ...(Array.isArray(app.aliases) ? app.aliases : [])].map(normalizeForMatch);
    return values.some((value) => value && (value === labelKey || value.includes(labelKey) || labelKey.includes(value)));
  }) : null;
  if (labelMatch) {
    return {
      label: labelMatch.label || rawLabel,
      packageName: labelMatch.packageName || rawPackage,
      aliases: Array.isArray(labelMatch.aliases) ? labelMatch.aliases.slice(0, 8) : [],
      capabilityProfile: labelMatch.capabilityProfile || null,
      source: safeText(candidate.source || "installed_label_match", 80),
      confidence: Math.max(0, Math.min(1, Number(candidate.confidence || labelMatch.capabilityProfile?.confidence || 0.82))),
    };
  }
  if (!rawLabel && !rawPackage) return null;
  return {
    label: rawLabel || rawPackage,
    packageName: rawPackage,
    aliases: Array.isArray(candidate.aliases) ? candidate.aliases.map((item) => safeText(item, 80)).filter(Boolean).slice(0, 8) : [],
    capabilityProfile: candidate.capabilityProfile && typeof candidate.capabilityProfile === "object"
      ? candidate.capabilityProfile
      : null,
    source: safeText(candidate.source || "contract_candidate", 80),
    confidence: Math.max(0, Math.min(1, Number(candidate.confidence || 0.65))),
  };
}

function normalizeTargetAppResolution(value, deviceContext) {
  const raw = value && typeof value === "object" ? value : null;
  if (!raw) return null;
  const statusRaw = String(raw.status || raw.resolutionStatus || "").trim().toLowerCase().replace(/[\s\-]+/g, "_");
  const status = ["resolved", "ambiguous", "not_found", "not_required"].includes(statusRaw)
    ? statusRaw
    : "unknown";
  const requiredCapabilities = normalizeRequiredCapabilities(raw.requiredCapabilities || []);
  const selectedRaw = raw.selectedApp && typeof raw.selectedApp === "object"
    ? raw.selectedApp
    : raw.targetApp && typeof raw.targetApp === "object"
      ? raw.targetApp
      : null;
  const selectedApp = selectedRaw
    ? normalizeExecutionTargetApp(
        { ...selectedRaw, source: selectedRaw.source || "android_target_app_resolution" },
        deviceContext
      )
    : null;
  const candidateRaw = Array.isArray(raw.candidates) ? raw.candidates : [];
  const candidates = candidateRaw
    .map((candidate) => normalizeExecutionTargetApp(
      { ...candidate, source: candidate?.source || "android_target_app_candidate" },
      deviceContext
    ))
    .filter(Boolean)
    .slice(0, 12);
  return {
    schema: safeText(raw.schema || "target_app_resolution_v1", 80),
    status,
    selectedApp,
    candidates,
    requiredCapabilities,
    reason: safeText(raw.reason || "", 260),
    source: "android_device_resolution",
  };
}

function appTaskResolutionScore(app, semanticContract, requiredCapabilities) {
  if (!app) return 0;
  const required = normalizeRequiredCapabilities(requiredCapabilities);
  const domain = safeText(semanticContract?.domain || "general", 60);
  const targetAction = safeText(semanticContract?.targetAction || "", 80);
  let score = 0;
  if (required.length && appSupportsRequiredCapabilities(app, required)) {
    score += 2200 + Math.round(Number(app.capabilityProfile?.confidence || 0) * 500);
  }
  const domainKeywords = agentDomainAppKeywords(domain);
  if (appMatchesAnyKeyword(app, domainKeywords)) score += 1200;
  const names = [app.label, app.packageName, ...(Array.isArray(app.aliases) ? app.aliases : [])]
    .map(normalizeAppMatchText)
    .filter(Boolean);
  const metadataTokens = {
    stock_trade: ["证券", "券商", "交易", "委托", "财富", "佣金", "stock", "trade", "broker", "securities", "finance"],
    stock_detail: ["股票", "行情", "证券", "财富", "stock", "quote", "finance"],
    stock_index: ["股票", "行情", "证券", "财富", "stock", "quote", "finance"],
    navigation: ["地图", "导航", "map", "nav"],
    video: ["视频", "直播", "video"],
    music: ["音乐", "歌曲", "music"],
    travel: ["旅行", "旅游", "酒店", "机票", "travel"],
  }[domain] || [];
  for (const token of metadataTokens) {
    const normalized = normalizeAppMatchText(token);
    if (normalized && names.some((name) => name.includes(normalized) || normalized.includes(name))) score += 360;
  }
  const explicit = semanticContract?.requiredApp;
  if (explicit && appMatchesTaskContractApp(app, explicit)) score += 4000;
  if (["open_order_entry", "order_entry", "place_order", "buy", "sell"].includes(targetAction) && names.some((name) => /证券|交易|trade|broker|securit/.test(name))) score += 700;
  return score;
}

function selectResolvedAppCandidate(candidates, semanticContract, requiredCapabilities, deviceContext) {
  const ranked = candidates
    .map((app) => ({ app, score: appTaskResolutionScore(app, semanticContract, requiredCapabilities) }))
    .filter((item) => item.score > 0)
    .sort((a, b) => b.score - a.score);
  if (!ranked.length) return { status: "not_found", selectedApp: null, candidates: [] };
  const normalizedCandidates = ranked.slice(0, 12).map((item) => ({ ...item.app, resolutionScore: item.score }));
  const top = ranked[0];
  const second = ranked[1];
  const uniqueStrong = !second || top.score >= second.score + 300;
  if (uniqueStrong) {
    return {
      status: "resolved",
      selectedApp: normalizeExecutionTargetApp({ ...top.app, source: "server_inventory_ranked_match", confidence: Math.min(1, Math.max(0.72, top.score / 4000)) }, deviceContext),
      candidates: normalizedCandidates,
    };
  }
  return { status: "ambiguous", selectedApp: null, candidates: normalizedCandidates };
}

function deriveTargetAppResolutionFromInventory(semanticContract, deviceContext, requiredCapabilitiesOverride = []) {
  const installed = installedAppsFromDeviceContext(deviceContext);
  const explicit = semanticContract?.requiredApp && typeof semanticContract.requiredApp === "object"
    ? semanticContract.requiredApp
    : null;

  if (explicit) {
    const exact = normalizeExecutionTargetApp(explicit, deviceContext);
    if (exact && installed.some((app) => app.packageName === exact.packageName)) {
      return {
        schema: "target_app_resolution_v2_two_brain",
        status: "resolved",
        selectedApp: exact,
        candidates: [exact],
        requiredCapabilities: [],
        reason: "DeepSeek AgentBrain supplied a canonical installed App.",
        source: "agent_brain_canonical_app",
      };
    }
  }

  return {
    schema: "target_app_resolution_v2_two_brain",
    status: "not_required",
    selectedApp: null,
    candidates: [],
    requiredCapabilities: [],
    reason: "GUI Plus directly understands the original visual task and current screen.",
    source: "two_brain_visual_runtime",
  };
}

function effectiveTargetAppResolution(semanticContract, deviceContext, requiredCapabilitiesOverride = []) {
  const overrideCapabilities = normalizeRequiredCapabilities(requiredCapabilitiesOverride);
  const requiredCapabilities = overrideCapabilities.length
    ? overrideCapabilities
    : semanticRequiredCapabilities(semanticContract);
  const fromDevice = normalizeTargetAppResolution(deviceContext?.targetAppResolution, deviceContext);
  const deviceCompatible = Boolean(
    fromDevice &&
    fromDevice.status !== "unknown" &&
    !(requiredCapabilities.length && fromDevice.status === "not_required") &&
    (!requiredCapabilities.length || !fromDevice.requiredCapabilities.length || requiredCapabilities.every((item) => fromDevice.requiredCapabilities.includes(item))) &&
    (fromDevice.status !== "resolved" || fromDevice.selectedApp)
  );
  if (deviceCompatible) return fromDevice;
  return deriveTargetAppResolutionFromInventory(semanticContract, deviceContext, requiredCapabilities);
}


function resolveTaskExecutionTargetApp(semanticContract, agentBrainRoute, snapshot, deviceContext) {
  const domain = safeText(semanticContract?.domain || "general", 60) || "general";
  const resolution = effectiveTargetAppResolution(semanticContract, deviceContext);
  if (resolution?.status === "resolved" && resolution.selectedApp) {
    return normalizeExecutionTargetApp(
      { ...resolution.selectedApp, source: resolution.source || "android_target_app_resolution" },
      deviceContext
    );
  }

  const routeStep = taskExecutionRouteOpenStep(agentBrainRoute);
  const candidates = [
    semanticContract?.requiredApp,
    routeStep ? {
      label: routeStep.appName || routeStep.targetText || routeStep.text || "",
      packageName: routeStep.packageName || routeStep.package || "",
      source: "agent_brain_route",
      confidence: Number(agentBrainRoute?.confidence || 0.8),
    } : null,
  ].filter(Boolean);

  for (const candidate of candidates) {
    const normalized = normalizeExecutionTargetApp(candidate, deviceContext);
    if (normalized?.packageName || normalized?.label) return normalized;
  }

  const requiredCapabilities = semanticRequiredCapabilities(semanticContract);
  if (!requiredCapabilities.length && isDomainTask(domain)) {
    const ranked = bestDomainOpenAppCandidate(deviceContext, domain);
    if (ranked) {
      const normalized = normalizeExecutionTargetApp(
        { ...ranked, source: ranked.source || "domain_candidate", confidence: 0.8 },
        deviceContext
      );
      if (normalized?.packageName || normalized?.label) return normalized;
    }
  }

  const currentPackage = safeText(snapshot?.packageName || snapshot?.currentApp || "", 120);
  const assistantHost = isAssistantHostAppPackage(currentPackage) || snapshotLooksLikeAssistantChat(snapshot);
  if (!assistantHost && currentPackage && currentPackage !== "android" && !currentPackage.includes("systemui")) {
    const installed = installedAppsFromDeviceContext(deviceContext);
    const active = installed.find((app) => app.packageName === currentPackage);
    if (
      active &&
      (
        !requiredCapabilities.length ||
        appSupportsRequiredCapabilities(active, requiredCapabilities)
      )
    ) {
      return normalizeExecutionTargetApp(
        { ...active, source: "current_foreground_app", confidence: 0.72 },
        deviceContext
      );
    }
  }
  return null;
}

function taskExecutionTargetAppActive(contract, snapshot) {
  const target = contract?.targetApp;
  if (!target) return false;
  const currentPackage = safeText(snapshot?.packageName || snapshot?.currentApp || "", 120);
  if (target.packageName && currentPackage === target.packageName) return true;
  const currentKey = normalizeForMatch(currentPackage);
  const candidates = [target.label, target.packageName, ...(Array.isArray(target.aliases) ? target.aliases : [])]
    .map(normalizeForMatch)
    .filter(Boolean);
  return Boolean(currentKey && candidates.some((value) => currentKey === value || currentKey.includes(value) || value.includes(currentKey)));
}

function taskExecutionGoalMatches(contract, goal) {
  if (!contract || typeof contract !== "object") return false;
  const previous = normalizeForMatch(contract.sourceGoal || contract.goal || "");
  const current = normalizeForMatch(goal || "");
  return Boolean(previous && current && previous === current);
}


function reconcileTaskExecutionContract({ existing, session, goal, semanticContract, agentBrainRoute, snapshot, deviceContext }) {
  const restored = taskExecutionGoalMatches(existing, goal) ? existing : null;
  const routeName = normalizeAgentBrainRouteName(agentBrainRoute?.route || "visual_agent");
  const mode = routeName === "device_tool" ? "internal_device_tool" : "visual";
  const routeOpenStep = firstAgentBrainStep(agentBrainRoute, (step) => {
    const executor = normalizeAgentBrainExecutorName(step?.executor || step?.route, routeName);
    const tool = normalizeAgentBrainToolName(step?.tool || step?.action || step?.name, executor);
    return executor === "device_tool" && tool === "open_app";
  });
  const targetApp = routeOpenStep ? canonicalInstalledAppForAgentBrainStep(routeOpenStep, deviceContext) : null;
  const currentPackage = safeText(snapshot?.packageName || snapshot?.currentApp || "", 120);
  const targetActive = Boolean(targetApp?.packageName && currentPackage === targetApp.packageName);
  const assistantHost = isAssistantHostAppPackage(currentPackage) || snapshotLooksLikeAssistantChat(snapshot);

  let phase = "visual_navigation";
  let phaseReason = safeText(agentBrainRoute?.reason || "GUI Plus owns visual App/page understanding.", 260);
  if (mode === "internal_device_tool") {
    phase = "internal_control";
  } else if (["ask_user", "refuse"].includes(routeName)) {
    phase = "user_assistance";
  } else if (targetApp && !targetActive) {
    phase = "open_target_app";
  }

  const now = Date.now();
  return {
    schema: "agent_task_execution_contract_v2_two_brain",
    taskId: safeText(restored?.taskId || session?.id || `task-${now}`, 120),
    sourceGoal: safeText(goal, 240),
    mode,
    domain: "agent_brain_routed",
    targetEntity: "",
    targetAction: "",
    requiredCapabilities: [],
    targetPage: "goal_satisfied_page",
    targetSubPage: "goal_satisfied_page",
    targetApp: targetApp || null,
    targetAppResolution: targetApp
      ? { schema: "target_app_resolution_v2_two_brain", status: "resolved", selectedApp: targetApp, candidates: [targetApp], requiredCapabilities: [], reason: "Resolved from DeepSeek AgentBrain canonical open_app step.", source: "agent_brain_canonical_app" }
      : { schema: "target_app_resolution_v2_two_brain", status: "not_required", selectedApp: null, candidates: [], requiredCapabilities: [], reason: "GUI Plus directly understands the original visual task and current screen.", source: "two_brain_visual_runtime" },
    targetAppResolutionStatus: targetApp ? "resolved" : "not_required",
    appInventoryHash: safeText(deviceContext?.appInventoryHash || deviceContext?.inventory?.inventoryHash || "", 120),
    phase,
    previousPhase: safeText(restored?.phase || "", 80),
    phaseVersion: Math.max(1, Number(restored?.phaseVersion || 0) + (restored?.phase !== phase ? 1 : 0)),
    allowedActions: taskExecutionAllowedActions(phase, mode),
    completionCriteria: [],
    completionEvidenceKeywords: [],
    targetAppActive: targetActive,
    completionSatisfied: false,
    assistantHost,
    createdAt: Number(restored?.createdAt || now),
    updatedAt: now,
    confidence: Math.max(0, Math.min(1, Number(agentBrainRoute?.confidence || 0.55))),
    phaseReason,
  };
}


function taskExecutionContractPlan(contract, semanticContract, snapshot, supportedSteps, goal, screenshotInfo, deviceContext, reasonTag = "task_execution_contract") {
  if (!contract || typeof contract !== "object") return null;
  const phase = normalizeTaskExecutionPhase(contract.phase);
  const target = contract.targetApp;

  if (["internal_control", "visual_navigation", "resolve_target_app", "verify_target_app", "resolve_requirements"].includes(phase)) {
    // GUI Plus owns visual interpretation. These phases must never block on local App-resolution rules.
    return null;
  }

  if (phase === "completed") {
    const reason = safeText(`任务已完成：${contract.sourceGoal || goal}`, 260);
    const step = normalizeAgentStep({ agentStep: { type: "finish", reason, riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    const state = normalizeAgentState({ agentState: { isComplete: true, expectedProgress: true, isWrong: false, confidence: Math.max(0.82, Number(contract.confidence || 0.82)), reason } }, step);
    return { agentStep: step, agentState: state, source: `${reasonTag}_completed` };
  }

  if (phase === "open_target_app" && target && supportedSteps.includes("open_app")) {
    if (taskExecutionTargetAppActive(contract, snapshot)) return null;
    const appName = safeText(target.label || target.packageName || "目标 App", 80);
    const packageName = safeText(target.packageName || "", 120);
    const reason = safeText(`DeepSeek AgentBrain 已解析 canonical App，先打开 ${appName}，随后由 GUI Plus 完成页面内导航。`, 260);
    const step = normalizeAgentStep({ agentStep: { type: "open_app", appName, packageName: packageName || undefined, targetText: appName, reason, riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    const state = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: true, isWrong: false, confidence: Math.max(0.8, Number(contract.confidence || 0.8)), reason } }, step);
    return { agentStep: step, agentState: state, source: `${reasonTag}_open_target_app` };
  }

  if (phase === "user_assistance") {
    const reason = safeText(contract.phaseReason || "DeepSeek AgentBrain requires user clarification.", 300);
    const step = normalizeAgentStep({ agentStep: { type: "need_user_help", reason, riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    const state = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: false, isWrong: false, confidence: 0.86, reason } }, step);
    return { agentStep: step, agentState: state, source: `${reasonTag}_need_user_help` };
  }
  return null;
}

function enforceTaskExecutionContractPlan(contract, semanticContract, agentStep, agentState, snapshot, supportedSteps, goal, screenshotInfo, deviceContext, source = "two_brain_runtime_validation") {
  const currentContract = contract && typeof contract === "object" ? contract : null;
  if (!agentStep || typeof agentStep !== "object") {
    const reason = "视觉主脑没有返回可执行动作。";
    const step = normalizeAgentStep({ agentStep: { type: "need_user_help", reason, riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    const state = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: false, isWrong: false, confidence: 0.2, reason } }, step);
    return { agentStep: step, agentState: state, contract: currentContract, source: `${source}_missing_action` };
  }

  let step = agentStep;
  let state = agentState;
  const type = normalizeAgentStepType(step.type || "") || "need_user_help";
  if (!supportedSteps.includes(type)) {
    const reason = safeText(`Android 客户端不支持动作 ${type}。`, 220);
    step = normalizeAgentStep({ agentStep: { type: "need_user_help", reason, riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    state = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: false, isWrong: false, confidence: 0.3, reason } }, step);
    return { agentStep: step, agentState: state, contract: currentContract, source: `${source}_unsupported_action` };
  }

  if (type === "open_app") {
    const canonical = canonicalInstalledAppForAgentBrainStep({
      appName: step.appName,
      packageName: step.packageName,
      args: { appName: step.appName, packageName: step.packageName },
    }, deviceContext);
    if (!canonical) {
      const reason = safeText("open_app 未匹配到 Android 上传的 canonical 应用名称与包名，已停止避免猜测。", 220);
      step = normalizeAgentStep({ agentStep: { type: "need_user_help", reason, riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
      state = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: false, isWrong: false, confidence: 0.35, reason } }, step);
      return { agentStep: step, agentState: state, contract: currentContract, source: `${source}_invalid_open_app_identity` };
    }
    step = { ...step, appName: canonical.label, packageName: canonical.packageName, targetText: canonical.label };
  }

  const nextContract = currentContract
    ? {
        ...currentContract,
        lastPlannedAction: type,
        phase: state?.isComplete && type === "finish" ? "completed" : currentContract.phase,
        completionSatisfied: Boolean(state?.isComplete && type === "finish"),
        updatedAt: Date.now(),
      }
    : null;
  return { agentStep: step, agentState: state, contract: nextContract, source: "" };
}


async function resolveTaskOrchestrationContext({ body, goal, snapshot, deviceContext, rawAgentMemory, recentAgentActions, session, startedAt }) {
  const exclusiveGuiPlusVisualSession = Boolean(
    isExclusiveGuiPlusVisualRequest(body) ||
      isExclusiveGuiPlusVisualMemory(rawAgentMemory)
  );
  if (exclusiveGuiPlusVisualSession) {
    if (session) {
      session.agentBrainRoute = null;
      session.agentBrainSource = "disabled_by_gui_plus_exclusive_visual_session";
      session.agentBrainError = "";
      session.taskSemanticContract = null;
      session.taskExecutionContract = null;
    }
    const guiPlusReplanRequested = agentMemoryRequestsGuiPlusReplan(rawAgentMemory);
    const agentMemory = {
      ...rawAgentMemory,
      taskSemanticContract: null,
      semanticTaskContract: null,
      taskExecutionContract: null,
      agentBrainRoute: null,
      agentBrain: null,
      decisionOwner: "gui_plus",
      visualDecisionOwner: "gui_plus",
      exclusiveVisualSession: true,
      allowAgentBrain: false,
      allowRoutePlanner: false,
      allowSemanticJudge: false,
      allowTaskContractJudge: false,
      routeRefreshRequested: false,
      invalidateCachedAgentBrainRoute: false,
      visualReplanRequested: guiPlusReplanRequested,
      guiPlusReplanRequested,
      visualOwnership: {
        schema: "android_gui_plus_exclusive_ownership_v1",
        owner: "gui_plus",
        exclusive: true,
        entryRouterReleased: true,
        allowAgentBrain: false,
        allowRoutePlanner: false,
        allowSemanticJudge: false,
        allowTaskContractJudge: false,
      },
      executionFeedback: {
        ...(rawAgentMemory?.executionFeedback && typeof rawAgentMemory.executionFeedback === "object"
          ? rawAgentMemory.executionFeedback
          : {}),
        routeRefreshRequested: false,
        visualReplanRequested: guiPlusReplanRequested,
        guiPlusReplanRequested,
      },
      loopSignals: {
        ...(rawAgentMemory?.loopSignals && typeof rawAgentMemory.loopSignals === "object"
          ? rawAgentMemory.loopSignals
          : {}),
        routeRefreshRequested: false,
        visualReplanRequested: guiPlusReplanRequested,
        guiPlusReplanRequested,
      },
    };

    return {
      taskSemanticContract: null,
      taskContractJudgeMs: 0,
      taskContractSource: "disabled_gui_plus_exclusive_visual_session",
      agentBrainRoute: null,
      agentBrainMs: 0,
      agentBrainSource: "disabled_by_gui_plus_exclusive_visual_session",
      agentBrainError: "",
      appResolveMs: 0,
      taskExecutionContract: null,
      agentMemory,
    };
  }

  const routeRefreshRequested = agentMemoryRequestsRouteRefresh(rawAgentMemory);
  if (routeRefreshRequested && session) {
    session.agentBrainRoute = null;
    session.agentBrainSource = "";
    session.agentBrainError = "";
  }
  let agentBrainRoute = !routeRefreshRequested && cachedAgentBrainRouteUsable(session.agentBrainRoute) ? session.agentBrainRoute : null;
  let agentBrainMs = 0;
  let agentBrainSource = agentBrainRoute ? "agent_brain_session_cache" : "";
  let agentBrainError = "";

  if (!agentBrainRoute) {
    const started = Date.now();
    const result = await resolveAgentBrainRouteForStep(
      goal,
      snapshot,
      recentAgentActions,
      deviceContext,
      rawAgentMemory,
      startedAt,
      { forceRefresh: routeRefreshRequested }
    );
    agentBrainMs = Date.now() - started;
    agentBrainRoute = result.route;
    agentBrainSource = result.source;
    agentBrainError = result.error || "";
    session.agentBrainRoute = agentBrainRoute;
    session.agentBrainSource = agentBrainSource;
    session.agentBrainError = agentBrainError;
  } else {
    agentBrainSource = session.agentBrainSource || agentBrainSource;
    agentBrainError = session.agentBrainError || "";
  }

  session.taskSemanticContract = null;
  session.taskExecutionContract = null;
  const agentMemory = {
    ...rawAgentMemory,
    taskSemanticContract: null,
    semanticTaskContract: null,
    taskExecutionContract: null,
    agentBrainRoute,
    agentBrain: agentBrainRoute,
  };

  return {
    taskSemanticContract: null,
    taskContractJudgeMs: 0,
    taskContractSource: "disabled_pure_two_brain_runtime",
    agentBrainRoute,
    agentBrainMs,
    agentBrainSource,
    agentBrainError,
    appResolveMs: 0,
    taskExecutionContract: null,
    agentMemory,
  };
}

function hasLikelyWritableFocus(snapshot, recentActions = []) {
  const inputNodes = Array.isArray(snapshot?.inputNodes) ? snapshot.inputNodes : [];
  if (inputNodes.length > 0) return true;
  const recentText = (Array.isArray(recentActions) ? recentActions.slice(-4) : []).join(" ");
  if (/输入框|搜索框|search box|input box|edit text|EditText|已聚焦|焦点|键盘|IME|光标|点击.*搜索|点击.*输入/i.test(recentText)) return true;
  const evidence = collectSnapshotEvidenceText(snapshot);
  return /正在输入|键盘已打开|光标|输入法|IME/.test(evidence);
}

function guardUnfocusedInputStep(agentStep, agentState, route, contract, snapshot, supportedSteps, goal, screenshotInfo, deviceContext, recentActions, reasonTag = "input_phase_guard") {
  if (!agentStep || agentStep.type !== "input_text") return { guarded: false, agentStep, agentState, source: "" };
  if (hasLikelyWritableFocus(snapshot, recentActions)) return { guarded: false, agentStep, agentState, source: "" };

  const brainOpen = agentBrainRouteToDirectAgentPlan(route, snapshot, supportedSteps, goal, screenshotInfo, deviceContext, `${reasonTag}_agent_brain`);
  if (brainOpen && brainOpen.agentStep?.type === "open_app") {
    brainOpen.agentStep.reason = safeText(`输入动作校验失败：当前没有可写焦点。${brainOpen.agentStep.reason || "按 AgentBrain 路线先打开目标 App。"}`, 260);
    brainOpen.source = `${reasonTag}_to_agent_brain_open_app`;
    return { guarded: true, ...brainOpen };
  }

  const reason = safeText("输入动作校验失败：当前截图和 Android 状态都没有显示可写入的输入框、焦点或键盘。GUI Plus 必须先点击真实输入框后再输入。", 260);
  const step = normalizeAgentStep({ agentStep: { type: "need_user_help", reason, riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
  const state = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: false, isWrong: false, confidence: 0.62, reason } }, step);
  return { guarded: true, agentStep: step, agentState: state, source: `${reasonTag}_blocked_unfocused_input` };
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
  const wantsBack = ["返回", "上一页", "back"].some((word) => normalizedGoal.includes(normalizeForMatch(word)));

  // v52：本地路线规划器不再根据“设置/联系人/股票/音乐”等关键词决定路线。
  // 只保留系统级直接返回这种无语义歧义的动作；其他全部交给 AgentBrain / GUI Plus。
  if (wantsBack) {
    return normalizeAgentRoutePlan({
      preferredAction: "back",
      subgoal: "返回上一页",
      groundingGoal: "返回上一页",
      allowScroll: false,
      expectedEvidence: ["上一页"],
      reason: "用户目标本身是返回，直接使用系统 back，避免坐标误点。",
      confidence: 0.86,
      source: "local_system_action_only",
    }, goal);
  }

  return normalizeAgentRoutePlan({
    preferredAction: "tap_xy",
    subgoal: safeText(goal, 220),
    groundingGoal: safeText(goal, 240),
    allowScroll: true,
    expectedEvidence: [],
    avoidEvidence: [],
    reason: "本地不解释任务语义，仅把原始目标交给 AgentBrain/GUI Plus。",
    confidence: 0.2,
    source: "local_neutral_fallback",
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
    "- 风险字段必须来自候选动作语义后果：普通规划器不要用固定词语给动作升风险；不确定时保持 low，交给 ActionSafetyJudge 复核。",
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
      "如果 memory.blockedActionSignatures 或 memory.verificationEvents 提示某动作无进展，禁止再次输出同一动作签名；必须换路线。"
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
  const raw = safeText(body?.agentSessionId || body?.sessionId || body?.agentMemory?.loopSignals?.agentSessionId || "", 120);
  const namespace = safeText(body?.__clientNamespace || body?.clientId || body?.deviceId || "anonymous", 120) || "anonymous";
  const stableSeed = raw || `goal:${normalizeForMatch(goal).slice(0, 120) || "anonymous"}`;
  const hash = crypto.createHash("sha256").update(`${namespace}|${stableSeed}`).digest("hex").slice(0, 32);
  return `agent_${hash}`;
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
    interactionHistory: [],
    visualFrame: null,
    lastFingerprint: "",
    lastStableScreenKey: "",
    lastActionKey: "",
    lastObservationReason: "",
    failedVisualCount: 0,
    taskSemanticContract: null,
    taskExecutionContract: null,
    agentBrainRoute: null,
    agentBrainSource: "",
    agentBrainError: "",
    pendingFinishVerification: false,
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


function agentGuiHistoryBytes(session) {
  if (!session || !Array.isArray(session.guiHistory)) return 0;
  return session.guiHistory.reduce((sum, item) => {
    return sum
      + Buffer.byteLength(String(item?.output || ""), "utf8")
      + Buffer.byteLength(String(item?.toolResponse || ""), "utf8")
      + Buffer.byteLength(String(item?.imageBase64 || ""), "utf8");
  }, 0);
}

function trimAgentGuiHistory(session) {
  if (!session || !Array.isArray(session.guiHistory)) return;
  while (session.guiHistory.length > AGENT_GUI_HISTORY_N) session.guiHistory.shift();
  while (session.guiHistory.length > 1 && agentGuiHistoryBytes(session) > AGENT_SESSION_MAX_BYTES) {
    session.guiHistory.shift();
  }
}

function rememberAgentGuiTurn(session, screenshotInfo, rawOutput, compactAction = null) {
  if (!session || !screenshotInfo?.hasImage) return;
  if (!Array.isArray(session.guiHistory)) session.guiHistory = [];
  session.guiHistory.push({
    imageBase64: String(screenshotInfo.base64 || ""),
    mimeType: safeText(screenshotInfo.mimeType || "image/jpeg", 80),
    width: Number(screenshotInfo.width || 0),
    height: Number(screenshotInfo.height || 0),
    displayWidth: Number(screenshotInfo.displayWidth || screenshotInfo.width || 0),
    displayHeight: Number(screenshotInfo.displayHeight || screenshotInfo.height || 0),
    output: String(rawOutput || "").trim(),
    toolResponse: "",
    compactAction: compactAction || null,
    createdAt: Date.now(),
  });
  trimAgentGuiHistory(session);
}

function finalizeAgentSessionIfComplete(session, agentState, options = {}) {
  if (!session || !agentState?.isComplete) return;
  if (options?.awaitingFinishVerification === true) {
    session.pendingFinishVerification = true;
    session.updatedAt = Date.now();
    return;
  }
  AGENT_SESSIONS.delete(session.id);
  session.guiHistory = [];
  session.visualFrame = null;
  session.taskSemanticContract = null;
  session.taskExecutionContract = null;
  session.agentBrainRoute = null;
  session.pendingFinishVerification = false;
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
      contract: "Return agentStep as the first action. If the next 2-3 actions do not require a fresh screenshot, also return agentSteps/actionBatch with those actions in order.",
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
      "When no fresh screenshot is required, return agentSteps/actionBatch with up to 3 actions. Keep agentStep equal to the first item.",
      "Do not batch input_text, open_app, or back/home/system actions unless it is the final single action.",
      "visualFrame 是视觉确权结果，优先级高于节点。",
      "节点只作为 affordance，不能单独证明任务完成。",
      "每次只返回一步 agentStep。",
      "如果 visualSuggestionConsumed=true 且当前页面没有明显变化，不要重复同一个视觉建议动作。",
      "除非页面明显加载/空白/动画过渡，否则不要返回 wait；连续 wait 一次后仍无变化，应 finish、换路径、back 或 need_user_help。",
      "如果当前视觉状态或主体文本已经证明进入目标栏目/目标功能默认子页，应直接 finish。",
      "If memory.blockedActionSignatures or verificationEvents says an action made no progress, do not output the same action signature again; choose a different route or target point."
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
    "- 只改变可见状态、页面层级或选择状态的入口点击要果断；不要用固定词语判断风险。",
    "- 目标控件可见且未完成：s=p，a=tap_xy 或 tap_node，给 x/y 或 n。",
    "- 目标页面已经打开且主体内容切换：s=d，a=finish。",
    "- 入口不在当前屏但页面可滚动：a=scroll 或 swipe。",
    "- 当前页面明显偏离目标：s=w，a=back。",
    "- 只有截图明显加载、空白、刷新、跳转动画时才 a=wait；普通静止页面禁止 wait。",
    "- 除非没有截图、没有任何候选、也无法探索，否则不要 need_user_help。",
    "- r/q 必须来自候选动作的语义后果；不要根据目标或屏幕里的固定词语直接升风险。",
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
  const parsedInputMode = normalizeInputMode(parsed.inputMode ?? parsed.input_mode ?? parsed.im ?? parsed.inputStrategy ?? parsed.inputAction ?? "");
  const inputMode = actionType === "input_text" ? (parsedInputMode || "focused_direct") : parsedInputMode;
  const requiresInputNode = actionType === "input_text"
    ? booleanFromValue(parsed.requiresInputNode ?? parsed.requires_input_node ?? parsed.inputNodeRequired, inputMode === "node")
    : false;
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

  const expectedProgress = isComplete || status === "progress" || ["tap_xy", "tap_node", "input_text", "scroll", "swipe", "wait", "back"].includes(actionType);
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
        inputMode: actionType === "input_text" ? inputMode || "focused_direct" : undefined,
        requiresInputNode: actionType === "input_text" ? requiresInputNode : undefined,
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
      inputMode: actionType === "input_text" ? inputMode || "focused_direct" : inputMode || undefined,
      requiresInputNode: actionType === "input_text" ? requiresInputNode : undefined,
      expectsFocusedInput: actionType === "input_text" ? (inputMode !== "node") : undefined,
      useFocusedInput: actionType === "input_text" ? (inputMode !== "node") : undefined,
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
        mode: "aliyun_dashscope_native",
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
  const xRaw = Number(rawX);
  const yRaw = Number(rawY);
  if (!Number.isFinite(xRaw) || !Number.isFinite(yRaw)) return null;

  // GUI Plus 官方 mobile_use 坐标协议是 1000x1000。
  // 不能先按截图像素换算，否则 1080x2400 截图压缩到 648x1440 后，
  // 例如 y=960 会被误当作 1440 高截图像素，变成 0.667，实际点击会明显偏上。
  if (xRaw >= 0 && xRaw <= 1 && yRaw >= 0 && yRaw <= 1) {
    return { x: clamp01(xRaw), y: clamp01(yRaw), source: "normalized_0_1" };
  }
  if (xRaw >= 0 && xRaw <= 100 && yRaw >= 0 && yRaw <= 100) {
    return { x: clamp01(xRaw / 100), y: clamp01(yRaw / 100), source: "percent_0_100" };
  }
  if (xRaw >= 0 && xRaw <= 1000 && yRaw >= 0 && yRaw <= 1000) {
    return { x: clamp01(xRaw / 1000), y: clamp01(yRaw / 1000), source: "mobile_use_1000" };
  }

  const imageWidth = Number(screenshotInfo?.width) || 0;
  const imageHeight = Number(screenshotInfo?.height) || 0;
  const displayWidth = Number(screenshotInfo?.displayWidth) || imageWidth;
  const displayHeight = Number(screenshotInfo?.displayHeight) || imageHeight;
  const x = normalizeGuiPlusCoordinate(xRaw, imageWidth, displayWidth);
  const y = normalizeGuiPlusCoordinate(yRaw, imageHeight, displayHeight);
  if (!Number.isFinite(x) || !Number.isFinite(y)) return null;
  return { x, y, source: "screenshot_or_display_pixel" };
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
    raw: String(raw || "").slice(0, 240),
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
    e: `${reason || "GUI Plus predicted clickable coordinate."} 坐标来源=${point.source || "unknown"}.`,
    raw: rawText.slice(0, 240),
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
    "- If finishing, use action=terminate in the tool call.",
  ].join("\n");
}


function buildLeanAliyunMobileUseToolProtocolPrompt() {
  return buildAliyunMobileUseToolProtocolPrompt();
}

function normalizeCanonicalVisualAppLabel(value) {
  return String(value || "").normalize("NFKC").trim().toLowerCase();
}

function canonicalVisualAppPairsFromDeviceContext(deviceContext, max = 36) {
  const ctx = deviceContext && typeof deviceContext === "object" ? deviceContext : {};
  const source = Array.isArray(ctx.visualAppContext) && ctx.visualAppContext.length
    ? ctx.visualAppContext
    : Array.isArray(ctx.appContext) && ctx.appContext.length
      ? ctx.appContext
      : Array.isArray(ctx.installedApps)
        ? ctx.installedApps
        : [];
  const seen = new Set();
  const out = [];
  for (const item of source) {
    if (!item || typeof item !== "object") continue;
    const label = safeText(item.label || item.appName || item.name || "", 80);
    const packageName = safeText(item.packageName || item.package || "", 120);
    if (!label || !packageName || item.launchable === false) continue;
    const key = `${normalizeCanonicalVisualAppLabel(label)}|${packageName}`;
    if (seen.has(key)) continue;
    seen.add(key);
    const aliases = Array.isArray(item.aliases)
      ? item.aliases.map((value) => safeText(value, 80)).filter(Boolean).slice(0, 16)
      : [];
    const capabilities = Array.isArray(item.capabilities)
      ? item.capabilities.map((value) => safeText(value, 80)).filter(Boolean).slice(0, 32)
      : Array.isArray(item.capabilityProfile?.capabilities)
        ? item.capabilityProfile.capabilities.map((value) => safeText(value, 80)).filter(Boolean).slice(0, 32)
        : [];
    out.push({ label, packageName, aliases, capabilities });
    if (out.length >= Math.max(1, Math.min(160, Number(max || 36)))) break;
  }
  return out;
}

function resolveCanonicalVisualAppPair(appName, deviceContext) {
  const query = normalizeCanonicalVisualAppLabel(appName);
  if (!query) return { status: "not_found", app: null, candidates: [] };
  const candidates = canonicalVisualAppPairsFromDeviceContext(deviceContext, 160)
    .filter((app) => normalizeCanonicalVisualAppLabel(app.label) === query);
  if (candidates.length === 1) return { status: "exact", app: candidates[0], candidates };
  if (candidates.length > 1) return { status: "ambiguous", app: null, candidates };
  return { status: "not_found", app: null, candidates: [] };
}

function formatCanonicalVisualAppPairs(deviceContext, goal = "", max = 160) {
  const goalKey = normalizeForMatch(goal);
  const apps = canonicalVisualAppPairsFromDeviceContext(deviceContext, 160)
    .map((app, index) => {
      const labelKey = normalizeForMatch(app.label);
      const packageKey = normalizeForMatch(app.packageName);
      const explicitIdentityMatch = Boolean(
        goalKey && (
          (labelKey && goalKey.includes(labelKey)) ||
          (packageKey && goalKey.includes(packageKey))
        )
      );
      return { ...app, explicitIdentityMatch, index };
    })
    .sort((a, b) => Number(b.explicitIdentityMatch) - Number(a.explicitIdentityMatch) || a.index - b.index)
    .slice(0, Math.max(1, Math.min(160, Number(max || 160))));
  if (!apps.length) return "none";
  return apps.map((app) => `${app.label} (${app.packageName})`).join(" / ");
}


function buildOfficialGuiPlusInstruction(goal, recentActions = [], deviceContext = null, agentBrainRoute = null, agentMemory = null, routeState = null, snapshot = null) {
  const previousActions = (Array.isArray(recentActions) ? recentActions : [])
    .slice(-12)
    .map((item, index) => `Step ${index + 1}: ${safeText(item, 240)}`)
    .filter(Boolean)
    .join("\n") || "None";
  const exclusiveGuiPlusVisualSession = isExclusiveGuiPlusVisualMemory(agentMemory);
  const routeContext = exclusiveGuiPlusVisualSession
    ? "Exclusive visual session: GUI Plus is the sole task-understanding, routing, action and completion-decision owner. Do not request or wait for DeepSeek AgentBrain routing."
    : agentBrainRoute && typeof agentBrainRoute === "object"
      ? agentBrainRoutePromptBlock(agentBrainRoute)
      : "DeepSeek AgentBrain route: visual_agent.";
  const runtimeFeedback = runtimeVerificationHintForPrompt(agentMemory);
  const interactionHistory = resolveGuiPlusInteractionHistory(null, agentMemory, recentActions, null);
  const interactionContext = formatGuiPlusInteractionHistoryForPrompt(interactionHistory);
  const currentPackage = safeText(
    snapshot?.packageName || snapshot?.currentApp || deviceContext?.currentApp?.packageName || "",
    120
  );
  const assistantHost = isAssistantHostAppPackage(currentPackage) || snapshotLooksLikeAssistantChat(snapshot);
  const controllerHandoffActive = isControllerHandoffSurface(null, snapshot, deviceContext, agentMemory);
  const canonicalApps = formatCanonicalVisualAppPairs(deviceContext, goal, 160);
  return [
    "You are GUI Plus, the cloud visual-control decision maker. Generate exactly one next mobile_use action from the current screenshot.",
    exclusiveGuiPlusVisualSession
      ? "Decision ownership: GUI Plus exclusively owns the complete visual session, including understanding the full user instruction, choosing or opening the canonical installed app, planning the route, selecting the next UI action, interpreting Android tool_response, and deciding completion from a fresh screenshot. The backend only transports and validates protocol."
      : "Decision ownership: DeepSeek AgentBrain owns high-level cloud routing; GUI Plus owns visual understanding and the next UI action. The backend only transports messages, session history and Android tool_response. It does not create subgoals, choose apps, reinterpret intent, decide completion, or decide that the user must clarify.",
    "The original user instruction below is authoritative and must remain complete. Never shorten it or drop the named app, target object, action, range or constraint.",
    `Current foreground package: ${currentPackage || "unknown"}; assistantHost=${assistantHost}; controllerHandoffActive=${controllerHandoffActive}.`,
    `Canonical installed apps available for action=open: ${canonicalApps}.`,
    controllerHandoffActive
      ? "Controller handoff rule: Android intentionally withholds the AI Assistant screenshot because it is not a task work surface. This stage has no page state to wait for. Ignore transient host text such as preparing/generating a reply. Your only valid next action is mobile_use action=open with one exact canonical installed-app label. Do not output wait, click, type, swipe, Back, Home, interact, answer or terminate."
      : "Work-surface rule: use wait only when the actual target-app screenshot visibly shows loading, transition or processing.",
    "You have direct mobile_use capability to open installed apps and operate their normal UI. Being on the AI Assistant host is not a limitation and is never a reason to ask the user to open an app manually.",
    "If the instruction names an app and that app appears uniquely in the canonical installed-app list, do not ask which app to use and do not tell the user to open it. If it is not foreground, output action=open with the exact canonical label; if it is foreground, continue inside it.",
    "For external-app tasks while assistantHost=true, the normal recovery action is action=open for the intended canonical app. Do not answer that you cannot directly operate the app, and do not delegate ordinary opening, searching, navigation, selection, or clicking steps to the user.",
    "Ask for user help only when GUI Plus genuinely cannot infer a required fact from the complete instruction, screenshot, installed-app list, history and tool_response, or when the next step is genuinely user-only such as password, SMS code, biometric verification, identity verification, or irreversible confirmation.",
    "The GUI Plus/User interaction history below is authoritative dialogue context. Treat the latest user reply as a direct continuation of your latest interact request, preserve all stated constraints, and continue the same visual session. You may ask another interact question when further clarification is genuinely necessary.",
    "When the interaction history says the user completed a sensitive step in the target app, do not request the secret itself. Inspect the fresh screenshot and continue or ask the user to retry only if the screen still requires private input.",
    "Do not use action=interact merely because the user left subjective preferences unspecified. For requests such as '挑一个合适的', make a reasonable visible choice yourself from the available products or options and continue.",
    "Use action=interact only when the next step truly requires user-only participation, such as password, SMS code, biometric/identity verification, an explicit irreversible confirmation, or a required choice that cannot be safely inferred from the instruction and screen.",
    "Use action=answer only when the requested visual task has been completed and you are returning the final result to the user. The backend will treat answer as a completion candidate and Android will verify it on a fresh screenshot.",
    "For shopping tasks that ask to enter the order page, you may click Buy Now/立即购买 to reach the order-confirmation page, but stop before Submit Order/提交订单 or payment unless the user explicitly asks and confirms the irreversible action.",
    exclusiveGuiPlusVisualSession
      ? "When an action fails, the screen does not change, or finish verification is requested, replan inside GUI Plus using the latest screenshot and Android tool_response. Never request AgentBrain route refresh."
      : "Use the supplied AgentBrain route only as high-level context; visual decisions still come from the current screenshot.",
    "Do not treat chat bubbles or assistant overlay text as completion evidence for an external app task.",
    "",
    `Original user instruction: ${aliyunGuiDateInfo()}${safeText(goal, 240)}`,
    "",
    "GUI Plus/User interaction history:",
    interactionContext,
    "",
    routeContext,
    "",
    "Previous cloud actions and Android results:",
    previousActions,
    "",
    runtimeFeedback ? `Android execution feedback:\n${runtimeFeedback}` : "Android execution feedback: none",
    "",
    `Canonical installed apps for action=open: ${canonicalApps}`,
    "",
    "Output one mobile_use action only. After Android executes it, a fresh screenshot and tool_response will be supplied for the next cloud decision."
  ].join("\n").slice(0, 12000);
}

function buildPureOfficialGuiPlusInstruction(goal, recentActions = [], deviceContext = null, agentBrainRoute = null, agentMemory = null) {
  const previousActions = (Array.isArray(recentActions) ? recentActions : [])
    .slice(-12)
    .map((item, index) => `Step ${index + 1}: ${safeText(item, 240)}`)
    .filter(Boolean)
    .join("\n") || "None";
  const routeContext = agentBrainRoute && typeof agentBrainRoute === "object"
    ? agentBrainRoutePromptBlock(agentBrainRoute)
    : "DeepSeek AgentBrain route: visual_agent.";
  const feedback = runtimeVerificationHintForPrompt(agentMemory);
  return [
    "Please generate the next move according to the UI screenshot, instruction and previous actions.",
    `Instruction: ${aliyunGuiDateInfo()}${safeText(goal, 240)}`,
    `Installed applications available to mobile_use action=open: ${formatCanonicalVisualAppPairs(deviceContext, goal, 160)}.`,
    routeContext,
    "Previous actions:",
    previousActions,
    feedback ? `Latest Android tool response:\n${feedback}` : "Latest Android tool response: none",
  ].join("\n\n").slice(0, 6000);
}

function guiHistoryImagePart(item) {
  const base64 = String(item?.imageBase64 || "");
  if (!base64) return null;
  const mimeType = safeText(item?.mimeType || "image/jpeg", 80);
  return {
    type: "image_url",
    image_url: { url: `data:${mimeType};base64,${base64}` },
  };
}

function currentGuiImagePart(screenshotInfo) {
  return {
    type: "image_url",
    image_url: { url: `data:${screenshotInfo.mimeType};base64,${screenshotInfo.base64}` },
  };
}


function hasMeaningfulAndroidVisualToolResponse(value) {
  const response = value && typeof value === "object" ? value : {};
  return Boolean(
    response.success !== null && response.success !== undefined ||
      safeText(response.result || "", 320) ||
      safeText(response.actionSignature || "", 160) ||
      (safeText(response.verification || "", 80) && safeText(response.verification || "", 80) !== "unknown") ||
      response.screenChanged !== null && response.screenChanged !== undefined ||
      response.finishVerificationRequested === true
  );
}

function formatOfficialGuiToolResponse(agentMemory, historyItem = null) {
  const memory = agentMemory && typeof agentMemory === "object" ? agentMemory : {};
  const feedback = memory.executionFeedback && typeof memory.executionFeedback === "object" ? memory.executionFeedback : {};
  const normalized = normalizeAndroidVisualToolResponse(
    memory.lastToolResponse || memory.toolResponse || feedback.lastToolResponse || {}
  );
  if (hasMeaningfulAndroidVisualToolResponse(normalized)) {
    return JSON.stringify({
      tool_name: normalized.toolName || "mobile_use",
      success: normalized.success,
      result: normalized.result || feedback.latestEvent || "",
      verification: normalized.verification || feedback.lastVerification || "unknown",
      action_signature: normalized.actionSignature || feedback.lastActionSignature || "",
      screen_changed: normalized.screenChanged,
      finish_verification_requested: Boolean(normalized.finishVerificationRequested),
    });
  }

  const compact = historyItem?.compactAction && typeof historyItem.compactAction === "object"
    ? historyItem.compactAction
    : {};
  const action = safeText(compact.a || compact.action || compact.type || "mobile_use", 48);
  return JSON.stringify({
    tool_name: "mobile_use",
    success: null,
    result: `The previous ${action || "mobile_use"} action was dispatched, but Android did not report a structured execution result. Inspect the current screenshot before choosing the next action.`,
    verification: "unknown",
    action_signature: "",
    screen_changed: null,
    finish_verification_requested: false,
  });
}

function syncLatestAgentGuiToolResponse(session, agentMemory) {
  if (!session || !Array.isArray(session.guiHistory) || !session.guiHistory.length) return "";
  const lastIndex = session.guiHistory.length - 1;
  const latest = session.guiHistory[lastIndex];
  const toolResponse = formatOfficialGuiToolResponse(agentMemory, latest);
  session.guiHistory[lastIndex] = {
    ...latest,
    toolResponse,
    toolResponseUpdatedAt: Date.now(),
  };
  trimAgentGuiHistory(session);
  return toolResponse;
}

function officialGuiCurrentUserContent(screenshotInfo, toolResponseText, interactionHistory = []) {
  const imagePart = currentGuiImagePart(screenshotInfo);
  const interactionText = formatGuiPlusInteractionHistoryForPrompt(interactionHistory);
  const hasInteraction = interactionText && interactionText !== "none";
  const content = [];
  if (toolResponseText) {
    content.push({ type: "text", text: "<tool_response>\n" });
    content.push({ type: "text", text: String(toolResponseText) });
    content.push({ type: "text", text: "\n</tool_response>\n" });
  }
  if (hasInteraction) {
    content.push({
      type: "text",
      text: `<interaction_history protocol="gui_plus_dialogue_v1">\n${interactionText}\n</interaction_history>\n`,
    });
  }
  content.push(imagePart);
  return content;
}

function buildAliyunGuiPlusMessages(goal, snapshot, screenshotInfo, recentActions = [], supportedSteps = SUPPORTED_AGENT_STEP_TYPES, deviceContext = null, agentMemory = null, session = null, routeState = null) {
  const interactionHistory = syncAgentGuiInteractionHistory(
    session,
    resolveGuiPlusInteractionHistory(null, agentMemory, recentActions, session)
  );
  const effectiveAgentMemory = {
    ...(agentMemory && typeof agentMemory === "object" ? agentMemory : {}),
    interactionProtocol: "gui_plus_dialogue_v1",
    interactionHistory,
    interactionTurnCount: interactionHistory.length,
  };
  const messages = [
    {
      role: "system",
      content: buildLeanAliyunMobileUseToolProtocolPrompt(supportedSteps),
    },
  ];
  const history = Array.isArray(session?.guiHistory)
    ? session.guiHistory.slice(-AGENT_GUI_HISTORY_N).filter((item) => item?.imageBase64 && item?.output)
    : [];
  const instructionPrompt = buildPureOfficialGuiPlusInstruction(
    goal,
    recentActions,
    deviceContext,
    isExclusiveGuiPlusVisualMemory(effectiveAgentMemory)
      ? null
      : effectiveAgentMemory?.agentBrainRoute || effectiveAgentMemory?.agentBrain || session?.agentBrainRoute || null,
    effectiveAgentMemory,
    null,
    snapshot
  );

  if (history.length > 0) {
    const latestToolResponse = syncLatestAgentGuiToolResponse(session, effectiveAgentMemory)
      || formatOfficialGuiToolResponse(effectiveAgentMemory, history[history.length - 1]);
    const refreshedHistory = Array.isArray(session?.guiHistory)
      ? session.guiHistory.slice(-AGENT_GUI_HISTORY_N).filter((item) => item?.imageBase64 && item?.output)
      : history;

    refreshedHistory.forEach((item, index) => {
      const imagePart = guiHistoryImagePart(item);
      if (!imagePart) return;
      messages.push({
        role: "user",
        content: index === 0
          ? [{ type: "text", text: instructionPrompt }, imagePart]
          : [imagePart],
      });
      messages.push({
        role: "assistant",
        content: String(item.output || ""),
      });
    });
    messages.push({
      role: "user",
      content: officialGuiCurrentUserContent(screenshotInfo, latestToolResponse, interactionHistory),
    });
  } else {
    messages.push({
      role: "user",
      content: [
        { type: "text", text: instructionPrompt },
        currentGuiImagePart(screenshotInfo),
      ],
    });
  }
  return messages;
}

function buildAliyunGuiPlusControllerHandoffMessages(goal, snapshot, recentActions = [], supportedSteps = SUPPORTED_AGENT_STEP_TYPES, deviceContext = null, agentMemory = null) {
  const instructionPrompt = buildOfficialGuiPlusInstruction(
    goal,
    recentActions,
    deviceContext,
    null,
    agentMemory,
    null,
    snapshot
  );
  const canonicalApps = formatCanonicalVisualAppPairs(deviceContext, goal, 160);
  const controllerInstruction = [
    instructionPrompt,
    "",
    "<controller_handoff_protocol>",
    "The attached neutral white image is only a provider-compatibility placeholder. It is not the current phone screen and contains no loading or navigation evidence.",
    "Android has deliberately suppressed the AI Assistant/chat screenshot so transient text cannot distract the decision.",
    "Choose the best target app from the canonical list using the complete original instruction.",
    `Canonical app labels: ${canonicalApps}.`,
    "Return exactly one official mobile_use action=open. The text argument must exactly equal one canonical label from the list.",
    "Never return wait at controller handoff: there is no visible task page to finish loading.",
    "Do not click, type, swipe, press Back/Home, answer, interact or terminate before the target app is opened.",
    "</controller_handoff_protocol>",
  ].join("\n");
  return [
    {
      role: "system",
      content: buildLeanAliyunMobileUseToolProtocolPrompt(supportedSteps),
    },
    {
      role: "user",
      content: [
        { type: "text", text: controllerInstruction },
        { type: "image_url", image_url: { url: GUI_PLUS_CONTROLLER_PLACEHOLDER_IMAGE } },
      ],
    },
  ];
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

function normalizeMobileUseArgsPayload(args) {
  if (!args) return null;
  if (typeof args === "string") {
    const raw = args.trim();
    if (!raw) return null;
    try {
      const parsed = JSON.parse(raw);
      return parsed && typeof parsed === "object" ? parsed : null;
    } catch (_) {
      const extracted = extractGuiPlusJsonOrArray(raw);
      return extracted && typeof extracted === "object" ? extracted : null;
    }
  }
  return typeof args === "object" ? args : null;
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
    const args = normalizeMobileUseArgsPayload(value.function.arguments);
    if (name === "mobile_use" && args) return args;
  }
  const name = safeText(value.name || value.functionName || value.toolName || "", 64);
  const args = normalizeMobileUseArgsPayload(value.arguments || value.args || value.parameters || value.input);
  if (name === "mobile_use" && args) return args;
  if (value.action && typeof value.action === "string") return value;
  return null;
}

function rawMobileUseCoordinatePair(coordinate, fallback = null) {
  const candidates = [];
  if (coordinate !== undefined && coordinate !== null) candidates.push(coordinate);
  if (fallback && typeof fallback === "object") {
    candidates.push(
      fallback.coordinate,
      fallback.coordinates,
      fallback.point,
      fallback.position,
      fallback.xy,
      fallback.center,
      fallback.coord,
    );
    const x = fallback.x ?? fallback.cx ?? fallback.centerX ?? fallback.targetX ?? fallback.tapX;
    const y = fallback.y ?? fallback.cy ?? fallback.centerY ?? fallback.targetY ?? fallback.tapY;
    if (x !== undefined || y !== undefined) candidates.push({ x, y });
  }

  for (const item of candidates) {
    if (item === undefined || item === null) continue;
    let x;
    let y;
    if (Array.isArray(item) && item.length >= 2) {
      x = item[0];
      y = item[1];
    } else if (typeof item === "object") {
      x = item.x ?? item[0] ?? item.cx ?? item.centerX ?? item.targetX ?? item.tapX ?? item.left;
      y = item.y ?? item[1] ?? item.cy ?? item.centerY ?? item.targetY ?? item.tapY ?? item.top;
    } else if (typeof item === "string") {
      const nums = item.match(/-?\d+(?:\.\d+)?/g)?.map(Number).filter(Number.isFinite) || [];
      if (nums.length >= 2) {
        x = nums[0];
        y = nums[1];
      }
    }
    const rawX = Number(x);
    const rawY = Number(y);
    if (Number.isFinite(rawX) && Number.isFinite(rawY)) return { rawX, rawY };
  }

  return null;
}

function normalizeMobileUseCoordinatePair(coordinate, fallback = null) {
  const pair = rawMobileUseCoordinatePair(coordinate, fallback);
  if (!pair) return null;
  const { rawX, rawY } = pair;
  if (rawX < 0 || rawX > 1000 || rawY < 0 || rawY > 1000) return null;
  return { x: clamp01(rawX / 1000), y: clamp01(rawY / 1000), source: "mobile_use_1000" };
}

function mobileUseSwipeDirection(args) {
  const a = rawMobileUseCoordinatePair(args?.coordinate, args);
  const b = rawMobileUseCoordinatePair(args?.coordinate2 ?? args?.endCoordinate ?? args?.end ?? args?.to ?? args?.point2, {
    x: args?.x2 ?? args?.endX ?? args?.toX,
    y: args?.y2 ?? args?.endY ?? args?.toY,
  });
  if (!a || !b) return normalizeAgentDirection(args?.direction || "") || "up";
  const dx = Number(b.rawX) - Number(a.rawX);
  const dy = Number(b.rawY) - Number(a.rawY);
  if (!Number.isFinite(dx) || !Number.isFinite(dy)) return "up";
  if (Math.abs(dx) > Math.abs(dy)) return dx > 0 ? "right" : "left";
  return dy > 0 ? "down" : "up";
}

function normalizeAliyunMobileUseRawToCompact(rawOutput, screenshotInfo, goal = "", deviceContext = null) {
  const rawText = String(rawOutput || "");
  const args = extractAliyunMobileUseToolCall(rawText);
  if (!args) return guiPlusNeedUserHelp(goal || "目标", "GUI Plus 没有返回官方 mobile_use tool_call，已停止避免猜测操作。", rawText);

  const action = String(args.action || "").toLowerCase().trim();
  const actionLine = safeText((rawText.match(/^Action:\s*(.*)$/im) || [])[1] || "", 120);
  const text = safeText(args.text || args.button || args.status || actionLine || goal || "目标", 120);
  const confidence = 0.82;

  if (action === "click") {
    const point = normalizeMobileUseCoordinatePair(args.coordinate, args);
    if (!point) return guiPlusNeedUserHelp(text, "GUI Plus mobile_use click 缺少可靠坐标。", rawText);
    return { s: "p", a: "tap_xy", x: point.x, y: point.y, t: text || "点击目标", c: confidence, e: `GUI Plus mobile_use click (${point.source}).`, raw: rawText.slice(0, 240) };
  }

  if (action === "wait") {
    const seconds = Number(args.time);
    const ms = Number.isFinite(seconds) ? Math.max(100, Math.min(60000, Math.round(seconds * 1000))) : 700;
    return { s: "p", a: "wait", ms, t: text || "等待", c: 0.68, e: "GUI Plus mobile_use wait：页面加载/过渡/处理等待。", raw: rawText.slice(0, 240) };
  }

  if (action === "system_button") {
    const button = String(args.button || args.text || "").toLowerCase();
    if (button === "enter") return { s: "p", a: "key", v: "enter", t: "Enter", c: 0.76, e: "GUI Plus mobile_use system_button Enter.", raw: rawText.slice(0, 240) };
    if (button === "back") return { s: "p", a: "back", t: "返回", c: 0.76, e: "GUI Plus mobile_use system_button Back.", raw: rawText.slice(0, 240) };
    if (button === "home") return { s: "p", a: "home", t: "主页", c: 0.76, e: "GUI Plus mobile_use system_button Home.", raw: rawText.slice(0, 240) };
    if (button === "menu") return { s: "p", a: "recents", t: "多任务", c: 0.7, e: "GUI Plus mobile_use system_button Menu.", raw: rawText.slice(0, 240) };
    return guiPlusNeedUserHelp(text, `第一阶段不自动执行 system_button=${safeText(args.button || args.text || "", 24)}。`, rawText);
  }

  if (action === "swipe") {
    const p1 = normalizeMobileUseCoordinatePair(args.coordinate, args);
    const p2 = normalizeMobileUseCoordinatePair(args.coordinate2 ?? args.endCoordinate ?? args.end ?? args.to ?? args.point2, {
      x: args.x2 ?? args.endX ?? args.toX,
      y: args.y2 ?? args.endY ?? args.toY,
    });
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
      raw: rawText.slice(0, 240),
    };
  }

  if (action === "type") {
    return {
      s: "p",
      a: "input_text",
      v: safeText(args.text || "", 180),
      t: "输入文字",
      c: 0.72,
      e: "GUI Plus mobile_use type：向当前已激活/聚焦的输入框输入文字；不要求 Android 无障碍树暴露 inputNodes。",
      inputMode: "focused_direct",
      requiresInputNode: false,
      expectsFocusedInput: true,
      useFocusedInput: true,
      raw: rawText.slice(0, 240),
    };
  }

  if (action === "key") {
    const key = String(args.text || "").toLowerCase();
    return { s: "p", a: "key", v: safeText(key, 64), t: text, c: 0.72, e: `GUI Plus mobile_use key ${safeText(key, 64)}.`, raw: rawText.slice(0, 240) };
    if (key.includes("back")) return { s: "p", a: "back", t: "返回", c: 0.72, e: "GUI Plus mobile_use key back.", raw: rawText.slice(0, 240) };
    if (key.includes("home")) return { s: "p", a: "home", t: "主页", c: 0.72, e: "GUI Plus mobile_use key home.", raw: rawText.slice(0, 240) };
    return guiPlusNeedUserHelp(text, `第一阶段不自动执行 key=${safeText(args.text || "", 32)}。`, rawText);
  }

  if (action === "terminate") {
    const status = String(args.status || "").toLowerCase();
    if (status === "success") return { s: "d", a: "finish", t: text || "完成", c: 0.82, e: safeText(args.text || actionLine || "GUI Plus mobile_use terminate success.", 160), raw: rawText.slice(0, 240) };
    return guiPlusNeedUserHelp(text || goal, safeText(args.text || actionLine || "GUI Plus mobile_use terminate failure.", 220), rawText);
  }

  if (action === "answer") {
    const finalAnswer = safeText(args.text || actionLine || "GUI Plus completed the visual task.", 1200);
    return {
      s: "d",
      a: "finish",
      t: safeText(text || goal || "完成", 120),
      c: 0.82,
      e: finalAnswer,
      raw: rawText.slice(0, 240),
    };
  }

  if (action === "interact") {
    const request = safeText(args.text || actionLine || "GUI Plus requires user interaction to continue.", 1200);
    return {
      s: "u",
      a: "need_user_help",
      t: "GUI Plus 需要你补充信息",
      c: 0.82,
      e: request,
      interactionProtocol: "gui_plus_dialogue_v1",
      interactionKind: "clarification",
      raw: rawText.slice(0, 240),
    };
  }

  if (action === "open") {
    const requestedLabel = safeText(args.text || "", 80);
    const resolution = resolveCanonicalVisualAppPair(requestedLabel, deviceContext);
    if (resolution.status === "exact" && resolution.app) {
      return {
        s: "p",
        a: "open_app",
        appName: resolution.app.label,
        packageName: resolution.app.packageName,
        t: resolution.app.label,
        c: 0.82,
        e: "GUI Plus mobile_use open：已按 canonical appContext 精确映射规范名称与包名。",
        raw: rawText.slice(0, 240),
      };
    }
    return {
      s: "u",
      a: "open_app",
      appName: requestedLabel || undefined,
      packageName: undefined,
      t: requestedLabel || "未匹配应用",
      c: 0,
      e: resolution.status === "ambiguous"
        ? "MODEL_CONTRACT_ERROR: mobile_use open 的应用名称在 canonical appContext 中不唯一，必须重新选择一个规范 label。"
        : "MODEL_CONTRACT_ERROR: mobile_use open 必须原样选择 canonical appContext 中的规范 label，后端不会猜测、纠错或使用别名。",
      raw: rawText.slice(0, 240),
    };
  }

  if (action === "long_press") {
    const point = normalizeMobileUseCoordinatePair(args.coordinate, args);
    if (point) {
      const seconds = Number(args.time);
      const ms = Number.isFinite(seconds) ? Math.max(300, Math.min(10000, Math.round(seconds * 1000))) : 800;
      return { s: "p", a: "long_press", x: point.x, y: point.y, ms, t: text, c: 0.78, e: "GUI Plus mobile_use long_press.", raw: rawText.slice(0, 240) };
    }
    return guiPlusNeedUserHelp(text || goal, "第一阶段不自动执行 mobile_use long_press，已保守暂停。", rawText);
  }

  return guiPlusNeedUserHelp(text || goal, `未知 mobile_use action=${safeText(action, 32)}。`, rawText);
}

function logGuiProviderCall(provider, model, screenshotInfo, elapsedMs, compact, extra = "") {
  const x = Number.isFinite(compact?.x) ? Number(compact.x).toFixed(4) : "null";
  const y = Number.isFinite(compact?.y) ? Number(compact.y).toFixed(4) : "null";
  console.log(`[agent-gui] provider=${provider} model=${model} image=${screenshotInfo?.width || 0}x${screenshotInfo?.height || 0} elapsedMs=${elapsedMs} action=${compact?.a || "unknown"} x=${x} y=${y}${extra ? ` ${extra}` : ""}`);
}

function aliyunGuiNativeBaseUrl() {
  const raw = String(process.env.ALIYUN_GUI_NATIVE_BASE_URL || ALIYUN_GUI_BASE_URL || "https://dashscope.aliyuncs.com/api/v1").trim();
  if (raw.includes("/compatible-mode/v1")) return raw.replace(/\/compatible-mode\/v1\/?$/i, "/api/v1");
  return raw.replace(/\/+$/g, "");
}

function toDashScopeNativeMessages(messages) {
  return (Array.isArray(messages) ? messages : []).map((message) => {
    const role = String(message?.role || "user");
    const content = message?.content;
    if (typeof content === "string") return { role, content: [{ text: content }] };
    if (!Array.isArray(content)) return { role, content: [] };
    return {
      role,
      content: content.map((part) => {
        if (!part || typeof part !== "object") return null;
        if (typeof part.text === "string") return { text: part.text };
        if (part.type === "text" && typeof part.content === "string") return { text: part.content };
        if (part.type === "image_url") {
          const url = part.image_url?.url || part.url || "";
          return url ? { image: url } : null;
        }
        if (typeof part.image === "string") return { image: part.image };
        return null;
      }).filter(Boolean),
    };
  }).filter((message) => message.content.length > 0);
}

function extractDashScopeNativeMobileUseCall(message, data) {
  const candidates = [
    message,
    message?.tool_call,
    message?.tool_calls,
    message?.function_call,
    data?.output?.tool_call,
    data?.output?.tool_calls,
    data?.output?.choices?.[0]?.tool_call,
    data?.output?.choices?.[0]?.tool_calls,
  ];
  const content = Array.isArray(message?.content) ? message.content : [];
  content.forEach((part) => {
    candidates.push(part, part?.tool_call, part?.tool_calls, part?.function_call, part?.function);
  });
  for (const candidate of candidates) {
    if (!candidate) continue;
    if (Array.isArray(candidate)) {
      for (const item of candidate) {
        const normalized = normalizeAliyunMobileUseToolCallObject(item);
        if (normalized) return normalized;
      }
      continue;
    }
    const normalized = normalizeAliyunMobileUseToolCallObject(candidate);
    if (normalized) return normalized;
  }
  return null;
}

function synthesizeMobileUseToolCallText(args) {
  return [
    `Action: ${safeText(args?.action || "mobile_use", 32)}`,
    `<tool_call>${JSON.stringify({ name: "mobile_use", arguments: args })}</tool_call>`,
  ].join("\n");
}

async function callDashScopeNativeGuiPlus(model, messages, sessionId, timeoutMs, options = {}) {
  if (!ALIYUN_GUI_API_KEY) throw new Error("Aliyun GUI Plus key missing");
  const endpoint = `${aliyunGuiNativeBaseUrl()}/services/aigc/multimodal-generation/generation`;
  const enableThinking = Boolean(options?.enableThinking || ALIYUN_GUI_ENABLE_THINKING);
  const payload = {
    model,
    input: { messages: toDashScopeNativeMessages(messages) },
    parameters: {
      vl_high_resolution_images: ALIYUN_GUI_HIGH_RESOLUTION_IMAGES,
      enable_thinking: enableThinking,
      max_tokens: Math.min(ALIYUN_GUI_MAX_TOKENS, AGENT_GUI_PROVIDER_MAX_TOKENS, 256),
    },
  };
  const { response: r, text: t } = await fetchTextWithTimeout(endpoint, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${ALIYUN_GUI_API_KEY}`,
      "x-dashscope-gui-session-id": sessionId || newAgentGuiSessionId(),
    },
    body: JSON.stringify(payload),
  }, timeoutMs || ALIYUN_GUI_TIMEOUT_MS);
  if (!r.ok) throw new Error(`Aliyun GUI Plus native ${r.status} ${t.slice(0, 300)}`);
  let data;
  try { data = JSON.parse(t); } catch (_) { throw new Error(`Aliyun GUI Plus native invalid_json_response ${t.slice(0, 160)}`); }
  const message = data?.output?.choices?.[0]?.message || {};
  const structuredCall = extractDashScopeNativeMobileUseCall(message, data);
  if (structuredCall) return synthesizeMobileUseToolCallText(structuredCall);
  const content = Array.isArray(message.content) ? message.content : [];
  const text = content.map((part) => part?.text || "").join("\n").trim() || String(message.content || data?.output?.text || "").trim();
  if (!text) throw new Error(`Aliyun GUI Plus native empty ${t.slice(0, 160)}`);
  return text;
}



function shouldSelfReviewGuiPlusInteraction(compact, snapshot = null, deviceContext = null, agentMemory = null) {
  const value = compact && typeof compact === "object" ? compact : {};
  const controllerHandoffActive = isControllerHandoffSurface(null, snapshot, deviceContext, agentMemory);
  const validControllerOpen = Boolean(
    value.a === "open_app" &&
      safeText(value.appName || "", 100) &&
      safeText(value.packageName || "", 140)
  );
  if (controllerHandoffActive && !validControllerOpen) return true;
  return Boolean(
    value.a === "need_user_help" &&
      value.interactionProtocol === "gui_plus_dialogue_v1"
  );
}

function buildGuiPlusInteractionSelfReviewPrompt(goal, snapshot, deviceContext, agentMemory, compact) {
  const currentPackage = safeText(
    snapshot?.packageName || snapshot?.currentApp || deviceContext?.currentApp?.packageName || "",
    120
  );
  const assistantHost = isAssistantHostAppPackage(currentPackage) || snapshotLooksLikeAssistantChat(snapshot);
  const controllerHandoffActive = isControllerHandoffSurface(null, snapshot, deviceContext, agentMemory);
  const interactionHistory = resolveGuiPlusInteractionHistory(null, agentMemory, [], null);
  return [
    "<interaction_self_review>",
    "Review your immediately previous action=interact before it is shown to the user.",
    "This is not a new user instruction. Keep the original task and current screenshot authoritative.",
    `Original instruction: ${safeText(goal, 240)}`,
    `Current foreground package: ${currentPackage || "unknown"}; assistantHost=${assistantHost}; controllerHandoffActive=${controllerHandoffActive}.`,
    `Canonical installed apps: ${formatCanonicalVisualAppPairs(deviceContext, goal, 160)}.`,
    `Previous proposed action: ${safeText(compact?.a || "unknown", 80)}; reason=${safeText(compact?.e || compact?.reason || compact?.t || "", 1200)}.`,
    controllerHandoffActive
      ? "Controller handoff is not a visible loading page. The AI Assistant screenshot is intentionally absent or irrelevant. Replace wait/click/type/swipe/Back/Home/interact/answer/terminate with action=open using the exact canonical target-app label."
      : "Review whether the previous interact is genuinely necessary.",
    `Existing GUI Plus/User dialogue: ${formatGuiPlusInteractionHistoryForPrompt(interactionHistory)}.`,
    "You can directly use mobile_use open, click, type, swipe, Back, Home and wait.",
    "If the previous interact merely asked the user to manually open an installed app, search, navigate, select, click, or perform another ordinary UI step that mobile_use can do, replace it now with the single executable mobile_use action you should perform yourself.",
    "When assistantHost=true and the target app is present in the canonical installed-app list, use action=open with the exact canonical app label instead of claiming that you cannot operate the app.",
    "Keep action=interact only when the missing information is genuinely unavailable and necessary, or the next step is genuinely user-only: password, SMS code, biometric/identity verification, or irreversible confirmation.",
    "If interact is still correct after review, repeat action=interact with a clear question that contains exactly what the user must provide or complete.",
    "Output exactly one mobile_use action in the required Action + <tool_call> format, and nothing else.",
    "</interaction_self_review>",
  ].join("\n");
}

function buildGuiPlusInteractionSelfReviewMessages(messages, raw, goal, snapshot, deviceContext, agentMemory, compact) {
  return [
    ...(Array.isArray(messages) ? messages : []),
    { role: "assistant", content: String(raw || "") },
    {
      role: "user",
      content: [{
        type: "text",
        text: buildGuiPlusInteractionSelfReviewPrompt(goal, snapshot, deviceContext, agentMemory, compact),
      }],
    },
  ];
}

function isBackendGeneratedGuiProtocolFallback(compact) {
  const value = compact && typeof compact === "object" ? compact : {};
  const reason = safeText(value.e || value.reason || "", 320);
  if (value.s === "u" && reason.startsWith("MODEL_CONTRACT_ERROR:")) return true;
  return Boolean(
    reason.includes("缺少可靠坐标") ||
    reason.includes("未知 mobile_use action=") ||
    reason.includes("第一阶段不自动执行 system_button=") ||
    reason.includes("第一阶段不自动执行 key=") ||
    reason.includes("第一阶段不自动执行 mobile_use long_press")
  );
}

async function callAliyunGuiPlusProvider(goal, snapshot, screenshotInfo, session, recentActions, supportedSteps, deviceContext, agentMemory, providerConfig, timeoutMs) {
  if (!ALIYUN_GUI_API_KEY) throw new Error("Aliyun GUI Plus key missing: set ALIYUN_GUI_API_KEY or QWEN_API_KEY");
  if (!ALIYUN_GUI_BASE_URL) throw new Error("Aliyun GUI Plus base url missing: set ALIYUN_GUI_BASE_URL");
  if (!ALIYUN_GUI_MODEL) throw new Error("Aliyun GUI Plus model missing: set ALIYUN_GUI_MODEL");
  const controllerHandoffActive = isControllerHandoffSurface(null, snapshot, deviceContext, agentMemory);
  if (!screenshotInfo?.hasImage && !controllerHandoffActive) throw new Error("Aliyun GUI Plus requires screenshot outside controller handoff");

  const startedAt = Date.now();
  const requestedTimeoutMs = Number(timeoutMs || 0) > 0
    ? Number(timeoutMs)
    : Math.min(Number(ALIYUN_GUI_TIMEOUT_MS || 15000), AGENT_OFFICIAL_GUI_PLUS_MAX_TIMEOUT_MS);
  const deepThinking = {
    enabled: ALIYUN_GUI_ENABLE_THINKING,
    level: ALIYUN_GUI_ENABLE_THINKING ? "enabled" : "off",
    reasons: ALIYUN_GUI_ENABLE_THINKING ? ["explicit_environment_configuration"] : [],
  };
  const boundedTimeoutMs = Math.max(
    300,
    Math.min(
      requestedTimeoutMs,
      Math.max(300, Number(AGENT_STEP_TOTAL_BUDGET_MS || 18000) - AGENT_RESPONSE_SAFETY_MARGIN_MS)
    )
  );
  const messages = controllerHandoffActive
    ? buildAliyunGuiPlusControllerHandoffMessages(
        goal,
        snapshot,
        recentActions,
        supportedSteps,
        deviceContext,
        agentMemory
      )
    : buildAliyunGuiPlusMessages(
        goal,
        snapshot,
        screenshotInfo,
        recentActions,
        supportedSteps,
        deviceContext,
        agentMemory,
        session,
        null
      );
  const sessionId = session?.guiSessionId || newAgentGuiSessionId();
  let raw = await callDashScopeNativeGuiPlus(
    ALIYUN_GUI_MODEL,
    messages,
    sessionId,
    boundedTimeoutMs,
    { enableThinking: deepThinking.enabled }
  );
  let compact = normalizeAliyunMobileUseRawToCompact(raw, screenshotInfo, goal, deviceContext);
  let interactionSelfReviewed = false;
  let interactionSelfReviewChangedAction = false;
  let interactionSelfReviewError = "";

  if (false && shouldSelfReviewGuiPlusInteraction(compact, snapshot, deviceContext, agentMemory)) {
    const elapsedMs = Date.now() - startedAt;
    const remainingBudgetMs = Math.max(
      0,
      Number(AGENT_STEP_TOTAL_BUDGET_MS || 18000) - AGENT_RESPONSE_SAFETY_MARGIN_MS - elapsedMs
    );
    const reviewTimeoutMs = Math.min(AGENT_GUI_INTERACTION_REVIEW_TIMEOUT_MS, remainingBudgetMs);
    if (reviewTimeoutMs >= 800) {
      try {
        const reviewMessages = buildGuiPlusInteractionSelfReviewMessages(
          messages,
          raw,
          goal,
          snapshot,
          deviceContext,
          agentMemory,
          compact
        );
        const reviewedRaw = await callDashScopeNativeGuiPlus(
          ALIYUN_GUI_MODEL,
          reviewMessages,
          sessionId,
          reviewTimeoutMs,
          { enableThinking: deepThinking.enabled }
        );
        const reviewedCompact = normalizeAliyunMobileUseRawToCompact(reviewedRaw, screenshotInfo, goal, deviceContext);
        interactionSelfReviewed = true;
        interactionSelfReviewChangedAction = reviewedCompact?.a !== compact?.a;
        raw = reviewedRaw;
        compact = reviewedCompact;
      } catch (error) {
        interactionSelfReviewError = sanitizeProviderError(error, 160);
        console.warn(`[agent-gui] interaction self-review skipped after provider error: ${interactionSelfReviewError}`);
      }
    }
  }

  if (controllerHandoffActive && !(compact?.a === "open_app" && compact?.appName && compact?.packageName)) {
    throw new Error(`GUI Plus controller handoff must return canonical open_app, received ${safeText(compact?.a || "unknown", 80)}`);
  }
  rememberAgentGuiTurn(session, screenshotInfo, raw, compact);
  const exclusiveGuiPlusVisualSession = isExclusiveGuiPlusVisualMemory(agentMemory);
  logGuiProviderCall(
    "aliyun_gui_plus",
    ALIYUN_GUI_MODEL,
    screenshotInfo,
    Date.now() - startedAt,
    compact,
    `mode=dashscope_native controllerHandoff=${controllerHandoffActive ? "yes" : "no"} history=${Math.max(0, (session?.guiHistory?.length || 1) - 1)} messages=${messages.length} thinking=${deepThinking.enabled ? "on" : "off"} interactionReview=${interactionSelfReviewed ? "yes" : "no"} interactionChanged=${interactionSelfReviewChangedAction ? "yes" : "no"} interactionReviewError=${interactionSelfReviewError ? "yes" : "no"} decisionOwner=${exclusiveGuiPlusVisualSession ? "gui_plus" : "cloud_models"} completionOwner=gui_plus_android_fresh_screen`
  );
  return {
    guiPlusCompact: compact,
    guiPlusRawLength: raw.length,
    guiPlusMessagesCount: messages.length,
    guiPlusHistoryImagesSent: Math.max(0, Math.floor((messages.length - 2) / 2)),
    guiPlusThinkingEnabled: Boolean(deepThinking.enabled),
    guiPlusThinkingLevel: deepThinking.level || "fast",
    guiPlusThinkingReasons: Array.isArray(deepThinking.reasons) ? deepThinking.reasons : [],
    guiPlusInteractionSelfReviewed: interactionSelfReviewed,
    guiPlusInteractionSelfReviewChangedAction: interactionSelfReviewChangedAction,
    guiPlusInteractionSelfReviewError: interactionSelfReviewError || undefined,
    guiPlusControllerHandoff: controllerHandoffActive,
    guiPlusTimeoutMs: boundedTimeoutMs,
    persistentRouteState: null,
    completionGuard: { status: "delegated_to_gui_plus_and_android_fresh_screen", accepted: null, reason: "Backend performs no semantic completion decision." },
  };
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
  const { response: r, text: t } = await fetchTextWithTimeout(AGENT_GUI_PROVIDER_URL, {
    method: "POST",
    headers,
    body: JSON.stringify(payload),
  }, Math.max(300, Number(timeoutMs || AGENT_GUI_PROVIDER_TIMEOUT_MS || AGENT_STEP_VISION_TIMEOUT_MS)));
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
  if (providerConfig.provider === "aliyun_gui_plus") {
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
  return null;
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

  const matchedNode = findGoalMatchedClickableNode(goal, snapshot);
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
    } else if (agentStep.type === "wait" && countRecentActionKind(recentAgentActions, "wait") >= 1 && !isLikelyLoadingOrTransition(snapshot, visualFrame)) {
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



function pureAgentStepFromGuiPlusCompact(compact, supportedSteps, goal) {
  const raw = compact && typeof compact === "object" ? compact : {};
  const allowed = new Set(Array.isArray(supportedSteps) ? supportedSteps : []);
  const requestedType = normalizeAgentStepType(raw.a || raw.action || raw.type || "need_user_help");
  const type = allowed.has(requestedType) ? requestedType : "need_user_help";
  const reason = type === requestedType
    ? safeText(raw.e || `GUI Plus mobile_use ${requestedType}.`, 260)
    : `Android client does not support GUI Plus action: ${requestedType}`;
  const riskLevel = normalizeRiskLevel(raw.r || raw.risk || raw.riskLevel || "low");
  const requiresConfirmation = Boolean(raw.q || raw.confirm || raw.requiresConfirmation) || riskLevel === "high";
  return {
    type,
    appName: type === "open_app" ? safeText(raw.appName || "", 80) || undefined : undefined,
    packageName: type === "open_app" ? safeText(raw.packageName || "", 120) || undefined : undefined,
    targetText: safeText(raw.t || raw.targetText || "", 100) || undefined,
    text: ["input_text", "key"].includes(type) ? safeText(raw.v || raw.text || "", 240) : undefined,
    inputMode: type === "input_text" ? "focused_direct" : undefined,
    requiresInputNode: type === "input_text" ? false : undefined,
    expectsFocusedInput: type === "input_text" ? true : undefined,
    useFocusedInput: type === "input_text" ? true : undefined,
    direction: type === "swipe" ? normalizeAgentDirection(raw.d || raw.direction || "up") || "up" : undefined,
    x: ["tap_xy", "long_press", "swipe"].includes(type) && Number.isFinite(Number(raw.x)) ? clamp01(Number(raw.x)) : undefined,
    y: ["tap_xy", "long_press", "swipe"].includes(type) && Number.isFinite(Number(raw.y)) ? clamp01(Number(raw.y)) : undefined,
    x2: type === "swipe" && Number.isFinite(Number(raw.x2)) ? clamp01(Number(raw.x2)) : undefined,
    y2: type === "swipe" && Number.isFinite(Number(raw.y2)) ? clamp01(Number(raw.y2)) : undefined,
    durationMs: ["wait", "long_press"].includes(type) ? Math.max(100, Math.min(60000, Number(raw.ms || 700))) : undefined,
    reason,
    riskLevel,
    requiresConfirmation,
  };
}

function pureAgentStateFromGuiPlusStep(step, compact, agentMemory = null) {
  const type = String(step?.type || "need_user_help");
  const isComplete = type === "finish";
  const needsHelp = type === "need_user_help";
  const finishVerificationRequested = agentMemoryRequestsFinishVerification(agentMemory);
  const reason = isComplete && finishVerificationRequested
    ? safeText(`Fresh-screen finish verification confirmed by GUI Plus. ${step?.reason || compact?.e || ""}`, 260)
    : safeText(step?.reason || compact?.e || "GUI Plus next action.", 260);
  return {
    isComplete,
    expectedProgress: !isComplete && !needsHelp,
    isWrong: false,
    confidence: Number.isFinite(Number(compact?.c)) ? clamp01(Number(compact.c)) : (isComplete ? 0.9 : 0.82),
    reason,
    nextHint: safeText(step?.targetText || step?.appName || "", 120),
    finishVerificationRequested,
    finishVerificationConfirmed: Boolean(isComplete && finishVerificationRequested),
  };
}


async function handleOfficialAliyunGuiPlusLoopStep(context) {
  const {
    startedAt,
    goal,
    snapshot,
    supportedSteps,
    screenshotInfo,
    deviceContext,
    agentMemory,
    recentAgentActions,
    requestBytes,
    readBodyMs,
    session,
    guiProviderConfig,
    baseMeta,
  } = context;
  const exclusiveGuiPlusVisualSession = isExclusiveGuiPlusVisualMemory(agentMemory);

  if (!exclusiveGuiPlusVisualSession) {
    return {
      ok: false,
      error: "gui_plus_visual_ownership_required",
      code: "gui_plus_visual_ownership_required",
      message: "GUI Plus may run only after Android verifies the cloud-selected target package.",
      ...baseMeta,
      version: WORKER_VERSION,
    };
  }
  if (!screenshotInfo?.hasImage) {
    return {
      ok: false,
      error: "visual_screenshot_required",
      code: "visual_screenshot_required",
      message: "GUI Plus requires a fresh Android screenshot.",
      ...baseMeta,
      version: WORKER_VERSION,
    };
  }
  if (guiProviderConfig.provider !== "aliyun_gui_plus") {
    return {
      ok: false,
      error: "aliyun_gui_plus_unavailable",
      code: "aliyun_gui_plus_unavailable",
      message: guiProviderConfig.fallbackReason || "Aliyun GUI Plus is not configured.",
      ...baseMeta,
      version: WORKER_VERSION,
    };
  }

  const providerStartedAt = Date.now();
  let parsed;
  let providerMs = 0;
  try {
    const preCallThinking = { enabled: ALIYUN_GUI_ENABLE_THINKING, timeoutExtraMs: 0 };
    const timeoutMs = boundedAgentTimeoutMs(
      Math.min(ALIYUN_GUI_TIMEOUT_MS, AGENT_OFFICIAL_GUI_PLUS_MAX_TIMEOUT_MS) + Number(preCallThinking.timeoutExtraMs || 0),
      agentRemainingBudgetMs(startedAt),
      AGENT_OFFICIAL_GUI_PLUS_MAX_TIMEOUT_MS
    );
    parsed = await callAliyunGuiPlusProvider(
      goal,
      snapshot,
      screenshotInfo,
      session,
      recentAgentActions,
      supportedSteps,
      deviceContext,
      agentMemory,
      guiProviderConfig,
      timeoutMs
    );
    providerMs = Date.now() - providerStartedAt;
    session.failedVisualCount = 0;
  } catch (error) {
    providerMs = Date.now() - providerStartedAt;
    const visualError = `${isTimeoutLikeError(error) ? "timeout: " : ""}${sanitizeProviderError(error, 220)}`;
    session.failedVisualCount = Number(session.failedVisualCount || 0) + 1;
    return {
      ok: false,
      error: "gui_plus_cloud_decision_failed",
      code: "gui_plus_cloud_decision_failed",
      message: visualError,
      retryable: true,
      ...baseMeta,
      sourceDetail: "aliyun_gui_plus_cloud_call_failed_no_local_action",
      model: "aliyun_gui_plus",
      modelId: "aliyun_gui_plus",
      modelLabel: "阿里云 GUI Plus · 云端视觉决策",
      providerModel: ALIYUN_GUI_MODEL,
      decisionOwner: "gui_plus",
      exclusiveVisualSession: true,
      debug: {
        providerMs,
        decisionOwner: "gui_plus",
        exclusiveVisualSession: true,
        localSemanticFallbackUsed: false,
      },
      version: WORKER_VERSION,
    };
  }

  let agentStep = pureAgentStepFromGuiPlusCompact(parsed?.guiPlusCompact, supportedSteps, goal);
  let agentState = pureAgentStateFromGuiPlusStep(agentStep, parsed?.guiPlusCompact, agentMemory);

  // TEST MODE: pass the cloud action through without backend safety judging or rewriting.
  agentStep = { ...agentStep, riskLevel: "low", requiresConfirmation: false };

  const totalMs = Date.now() - startedAt;
  const agentSteps = [agentStep];
  const actionBatch = agentSteps.slice();
  const stopConditions = ["after_each_action_reobserve"];
  const finishVerificationRequested = agentMemoryRequestsFinishVerification(agentMemory);
  const awaitingFinishVerification = Boolean(agentStep?.type === "finish" && !finishVerificationRequested);
  if (agentStep?.type !== "finish") session.pendingFinishVerification = false;
  finalizeAgentSessionIfComplete(session, agentState, { awaitingFinishVerification });

  return {
    ok: true,
    reply: awaitingFinishVerification
      ? "GUI Plus produced a completion candidate; Android will provide a fresh screenshot for the next cloud verification."
      : agentState?.isComplete
        ? "GUI Plus confirmed completion on the fresh screenshot."
        : "GUI Plus produced the next mobile_use action.",
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
    sourceDetail: "aliyun_gui_plus_exclusive_visual_history_loop",
    model: "aliyun_gui_plus",
    modelId: "aliyun_gui_plus",
    modelLabel: "阿里云 GUI Plus · 云端视觉决策",
    providerModel: ALIYUN_GUI_MODEL,
    searchUsed: false,
    toolUsed: "agent_step",
    toolReason: agentStep.reason,
    sources: [],
    executionFeedbackAccepted: agentMemory?.executionFeedback || null,
    lastToolResponseAccepted: agentMemory?.lastToolResponse || null,
    finishVerificationRequested,
    awaitingFinishVerification,
    visualReplanRequested: agentMemoryRequestsGuiPlusReplan(agentMemory),
    guiPlusReplanRequested: agentMemoryRequestsGuiPlusReplan(agentMemory),
    routeRefreshRequested: false,
    decisionOwner: "gui_plus",
    exclusiveVisualSession: true,
    guiThinking: {
      enabled: Boolean(parsed?.guiPlusThinkingEnabled),
      level: parsed?.guiPlusThinkingLevel || "fast",
      reasons: Array.isArray(parsed?.guiPlusThinkingReasons) ? parsed.guiPlusThinkingReasons : [],
      timeoutMs: Number(parsed?.guiPlusTimeoutMs || 0),
    },
    debug: {
      packageName: snapshot.packageName,
      hasScreenshot: screenshotInfo.hasImage,
      requestBytes,
      readBodyMs,
      providerMs,
      totalMs,
      officialHistoryLoop: true,
      historyImagesSent: Number(parsed?.guiPlusHistoryImagesSent || 0),
      guiHistoryCount: Array.isArray(session.guiHistory) ? session.guiHistory.length : 0,
      guiApiMode: "dashscope_native_official",
      agentArchitecture: "gui_plus_exclusive_visual_session_backend_transport_only",
      guiCompactAction: parsed?.guiPlusCompact || null,
      finishVerificationRequested,
      awaitingFinishVerification,
      sessionId: session.id,
      sessionStep: session.step,
      decisionOwner: "gui_plus",
    },
    version: WORKER_VERSION,
  };
}

function refreshCachedTaskContractForScreen(contract, snapshot, deviceContext) {
  if (!contract || typeof contract !== "object") return contract;
  const requiredAppActive = currentAppSatisfiesTaskContract(contract, snapshot, deviceContext);
  const assistantHost = isAssistantHostAppPackage(snapshot?.packageName || snapshot?.currentApp || "") || snapshotLooksLikeAssistantChat(snapshot);
  return {
    ...contract,
    current: {
      ...(contract.current && typeof contract.current === "object" ? contract.current : {}),
      requiredAppActive,
      assistantHost,
      phase: requiredAppActive ? "visual_execute" : assistantHost ? "leave_assistant_open_target_app" : "visual_execute",
    },
  };
}

function cachedAgentBrainRouteUsable(route) {
  if (!route || typeof route !== "object") return false;
  const routeName = normalizeAgentBrainRouteName(route.route || "visual_agent");
  if (["refuse", "ask_user"].includes(routeName)) return true;
  const confidence = Number(route.confidence || 0);
  return confidence >= 0.18 || Array.isArray(route.steps);
}

async function handleAgentStepRequest(body, prompt, resolvedModel) {
  const startedAt = Date.now();
  const goal = safeText(body.agentGoal || body.goal || prompt, 240);
  const snapshot = compactScreenSnapshot({
    ...(body.screenSnapshot && typeof body.screenSnapshot === "object" ? body.screenSnapshot : {}),
    currentApp: body?.screenSnapshot?.currentApp || body?.screenSnapshot?.packageName || body?.currentPackage || "",
    packageName: body?.screenSnapshot?.packageName || body?.screenSnapshot?.currentApp || body?.currentPackage || "",
  });
  const supportedSteps = supportedAgentStepsFromBody(body);
  const screenshotInfo = normalizeAgentScreenshot(body);
  const rawDeviceContext = body.deviceContext && typeof body.deviceContext === "object" ? body.deviceContext : {};
  const deviceContext = mergeVisualAppContextIntoDeviceContext(body, { ...rawDeviceContext });
  if (body.targetAppResolution && typeof body.targetAppResolution === "object") {
    deviceContext.targetAppResolution = body.targetAppResolution;
  }
  const rawAgentMemoryInput = body.agentMemory && typeof body.agentMemory === "object" ? body.agentMemory : {};
  const recentAgentActions = Array.isArray(body.recentAgentActions)
    ? body.recentAgentActions.slice(-12)
    : Array.isArray(body.recentActions)
      ? body.recentActions.slice(-12)
      : [];
  const rawAgentMemory = normalizeAndroidVisualExecutionProtocol(body, rawAgentMemoryInput, recentAgentActions);
  const verifiedSurface = verifiedVisualSurfaceProtocol(body, snapshot, deviceContext, rawAgentMemory);
  const controllerHandoffActive = isControllerHandoffSurface(body, snapshot, deviceContext, rawAgentMemory);
  const requestBytes = Number(body.__debugRequestBytes || 0) || 0;
  const readBodyMs = Number(body.__debugReadBodyMs || 0) || 0;
  const session = getAgentSession(body, goal);
  syncAgentGuiInteractionHistory(session, rawAgentMemory.interactionHistory);
  session.step += 1;

  const exclusiveGuiPlusVisualSession = Boolean(
    verifiedSurface.guiPlusEligible && (
      isExclusiveGuiPlusVisualRequest(body) ||
      isExclusiveGuiPlusVisualMemory(rawAgentMemory)
    )
  );
  const qwenProviderModel = String(process.env.QWEN_VISION_MODEL || "qwen-vl-plus").trim();
  const providerResolutionScreenshotInfo = controllerHandoffActive
    ? { ...screenshotInfo, hasImage: true }
    : screenshotInfo;
  const guiProviderConfig = resolveAgentGuiProviderConfig(providerResolutionScreenshotInfo, qwenProviderModel);
  const baseMeta = {
    source: exclusiveGuiPlusVisualSession
      ? "agent_step_gui_plus_exclusive_loop"
      : "agent_step_single_model_loop",
    sourceDetail: controllerHandoffActive
      ? "gui_plus_controller_handoff_preflight"
      : screenshotInfo.hasImage
        ? "gui_provider_planner_or_cache"
        : "text_planner_only",
    model: (screenshotInfo.hasImage || controllerHandoffActive) ? guiProviderConfig.provider : "qwen",
    modelId: (screenshotInfo.hasImage || controllerHandoffActive) ? guiProviderConfig.provider : "qwen",
    modelLabel: (screenshotInfo.hasImage || controllerHandoffActive) ? guiProviderConfig.modelLabel : "Qwen 文本规划",
    providerModel: (screenshotInfo.hasImage || controllerHandoffActive) ? guiProviderConfig.providerModel : process.env.QWEN_MODEL,
    guiProvider: guiProviderConfig.provider,
    requestedGuiProvider: guiProviderConfig.requestedProvider,
    guiProviderMode: guiProviderConfig.mode,
    guiProviderFallbackToQwen: Boolean(guiProviderConfig.fallbackToQwen),
    guiProviderFallbackReason: guiProviderConfig.fallbackReason || "",
    version: WORKER_VERSION,
    architecture: exclusiveGuiPlusVisualSession
      ? "gui_plus_exclusive_visual_session_backend_transport_only"
      : "deepseek_cloud_router_gui_plus_cloud_visual_action_backend_transport_only",
    decisionOwner: exclusiveGuiPlusVisualSession ? "gui_plus" : "cloud_models",
    exclusiveVisualSession: exclusiveGuiPlusVisualSession,
    verifiedSurfaceState: verifiedSurface.surfaceState,
    verifiedTargetPackage: verifiedSurface.verifiedTargetPackage || "",
    observationId: verifiedSurface.observationId || "",
  };

  const officialGuiPlusLoop = Boolean(
    (screenshotInfo.hasImage || controllerHandoffActive) &&
      guiProviderConfig.provider === "aliyun_gui_plus"
  );

  if (!goal) {
    return { ok: false, error: "empty_agent_goal", code: "empty_agent_goal", version: WORKER_VERSION };
  }

  const executionMode = String(body.executionMode || "").toLowerCase().trim().replace(/-/g, "_");
  const normalChatDeviceToolMode = Boolean(
    body.normalChatDeviceToolMode === true ||
    executionMode === "normal_chat_device_tool" ||
    (body.allowInternalDeviceTools === true && body.forceVisualAgent !== true && body.computerUseMode !== true)
  );
  const allowInternalDeviceTools = Boolean(body.allowInternalDeviceTools === true || normalChatDeviceToolMode);
  const hasUsableSnapshot = Boolean(snapshot.packageName || snapshot.currentApp || snapshot.nodeCount > 0 || screenshotInfo.hasImage);

  if (!hasUsableSnapshot && !allowInternalDeviceTools) {
    return {
      ok: true,
      reply: "当前没有可用屏幕快照。",
      agentStep: { type: "need_user_help", reason: "Android 端没有提供可用 screenSnapshot 或 screenshot，无法规划下一步。", riskLevel: "low", requiresConfirmation: false },
      ...baseMeta,
    };
  }

  if (normalChatDeviceToolMode) {
    const aiInternalResult = await resolveNormalChatInternalDeviceToolPlanByAI(goal, body, snapshot, supportedSteps, screenshotInfo, deviceContext, rawAgentMemory, startedAt);
    const directInternalPlan = aiInternalResult?.plan || null;
    const plannerDebug = aiInternalResult?.debug || {};
    if (directInternalPlan && directInternalPlan.agentStep && isInternalToolAgentStepType(directInternalPlan.agentStep.type)) {
      return {
        ok: true,
        reply: directInternalPlan.agentStep.reason || "已识别为原生内部工具。",
        agentStep: directInternalPlan.agentStep,
        agentState: directInternalPlan.agentState,
        agentSteps: [directInternalPlan.agentStep],
        debug: {
          totalMs: Date.now() - startedAt,
          readBodyMs,
          requestBytes,
          visualCalled: false,
          normalChatDeviceToolMode: true,
          internalToolPlanner: "ai",
          planSource: directInternalPlan.source || "normal_chat_ai_internal_device_tool",
          ...plannerDebug,
        },
        ...baseMeta,
        source: directInternalPlan.source || "normal_chat_ai_internal_device_tool",
        sourceDetail: "ai_internal_device_tool_direct",
        model: "deepseek_v4",
        modelId: "deepseek_v4",
        modelLabel: "AI 原生内部工具规划器",
      };
    }
    return {
      ...normalChatNoInternalDeviceToolPlan(goal, snapshot, supportedSteps, screenshotInfo, deviceContext, startedAt, requestBytes, readBodyMs),
      ...baseMeta,
      source: "normal_chat_no_device_tool_ai",
      sourceDetail: "ai_internal_device_tool_no_match",
      model: "deepseek_v4",
      modelId: "deepseek_v4",
      modelLabel: "AI 内部设备工具规划器",
      debug: {
        totalMs: Date.now() - startedAt,
        readBodyMs,
        requestBytes,
        visualCalled: false,
        normalChatDeviceToolMode: true,
        internalToolPlanner: "ai",
        ...plannerDebug,
      },
    };
  }

  // v13 is an isolated protocol family: it must never fall through to the legacy
  // task-contract/router stack, including on the first turn without a screenshot.
  const cloudRouteVisualLoopRequest = isCloudRouteVisualLoopRequest(body);

  if (cloudRouteVisualLoopRequest) {
    const routeResult = exclusiveGuiPlusVisualSession
      ? null
      : await resolveAgentBrainRouteForStep(
          goal,
          snapshot,
          recentAgentActions,
          deviceContext,
          rawAgentMemory,
          startedAt,
          { forceRefresh: true, useCache: false }
        );
    const effectiveAgentBrainRoute = routeResult?.route || null;
    const agentBrainMs = Number(routeResult?.elapsedMs || 0);
    const agentBrainSource = routeResult?.source || (exclusiveGuiPlusVisualSession ? "gui_plus_verified_surface" : "deepseek_cloud_route");
    const agentBrainError = routeResult?.error || "";
    if (!exclusiveGuiPlusVisualSession && !effectiveAgentBrainRoute && agentBrainError) {
      return {
        ok: false,
        error: `DeepSeek 主脑路由失败：${agentBrainError}`,
        code: "agent_brain_route_failed",
        retryable: true,
        ...baseMeta,
        source: "agent_brain_route_failed",
        sourceDetail: agentBrainSource,
        model: "deepseek_v4",
        modelId: "deepseek_v4",
        modelLabel: "DeepSeek AgentBrain",
        debug: {
          totalMs: Date.now() - startedAt,
          agentBrainMs,
          agentBrainSource,
          agentBrainError,
          visualCalled: false,
          localSemanticFallbackUsed: false,
        },
        version: WORKER_VERSION,
      };
    }
    const routedPlan = exclusiveGuiPlusVisualSession
      ? null
      : agentBrainRouteToDirectAgentPlan(
          effectiveAgentBrainRoute,
          snapshot,
          supportedSteps,
          goal,
          screenshotInfo,
          deviceContext,
          "agent_brain_entry_route"
        );
    if (routedPlan?.agentStep) {
      return {
        ok: true,
        reply: routedPlan.agentStep.reason || "DeepSeek selected the next deterministic action.",
        agentStep: routedPlan.agentStep,
        agentState: routedPlan.agentState,
        agentSteps: [routedPlan.agentStep],
        steps: [routedPlan.agentStep],
        actionBatch: [routedPlan.agentStep],
        stopConditions: ["after_each_action_reobserve"],
        ...baseMeta,
        source: routedPlan.source || "agent_brain_entry_route",
        sourceDetail: "deepseek_entry_route_direct_action",
        model: "deepseek_v4",
        modelId: "deepseek_v4",
        modelLabel: "DeepSeek AgentBrain",
        agentBrainRoute: effectiveAgentBrainRoute,
        decisionOwner: "deepseek",
        exclusiveVisualSession: false,
        debug: {
          totalMs: Date.now() - startedAt,
          agentBrainMs,
          agentBrainSource,
          agentBrainError,
          visualCalled: false,
          localSemanticFallbackUsed: false,
        },
        version: WORKER_VERSION,
      };
    }
    if (!verifiedSurface.guiPlusEligible) {
      const reason = safeText(
        effectiveAgentBrainRoute?.reason ||
          "DeepSeek did not select an exact installed target package before GUI Plus handoff.",
        300
      );
      const step = {
        type: "need_user_help",
        reason,
        riskLevel: "low",
        requiresConfirmation: false,
      };
      return {
        ok: true,
        reply: reason,
        agentStep: step,
        agentState: {
          isComplete: false,
          expectedProgress: false,
          isWrong: false,
          confidence: 0.45,
          reason,
          nextHint: "DeepSeek must return open_app with an exact packageName from appContext, or ask the user to choose an app.",
        },
        agentSteps: [step],
        steps: [step],
        actionBatch: [step],
        stopConditions: ["after_each_action_reobserve"],
        ...baseMeta,
        source: "agent_brain_target_binding_required",
        sourceDetail: "deepseek_must_select_target_before_gui_plus",
        model: "deepseek_v4",
        modelId: "deepseek_v4",
        modelLabel: "DeepSeek AgentBrain",
        agentBrainRoute: effectiveAgentBrainRoute,
        decisionOwner: "deepseek",
        exclusiveVisualSession: false,
        verifiedSurfaceProtocol: verifiedSurface,
        expectedActionObservationId: verifiedSurface.observationId || "",
        debug: {
          totalMs: Date.now() - startedAt,
          agentBrainMs,
          agentBrainSource,
          agentBrainError,
          visualCalled: false,
          guiPlusBlockedUntilTargetVerified: true,
          localSemanticFallbackUsed: false,
        },
        version: WORKER_VERSION,
      };
    }
    return await handleOfficialAliyunGuiPlusLoopStep({
      startedAt,
      goal,
      snapshot,
      supportedSteps: supportedSteps.filter((type) => !INTERNAL_TOOL_AGENT_STEP_TYPES.includes(type)),
      screenshotInfo,
      deviceContext,
      agentMemory: rawAgentMemory,
      recentAgentActions,
      requestBytes,
      readBodyMs,
      session,
      guiProviderConfig,
      baseMeta,
    });
  }

  const orchestration = await resolveTaskOrchestrationContext({
    body,
    goal,
    snapshot,
    deviceContext,
    rawAgentMemory,
    recentAgentActions,
    session,
    startedAt,
  });
  let taskSemanticContract = orchestration.taskSemanticContract;
  const taskContractJudgeMs = orchestration.taskContractJudgeMs;
  const taskContractSource = orchestration.taskContractSource;
  let agentBrainRoute = orchestration.agentBrainRoute;
  const agentBrainMs = orchestration.agentBrainMs;
  const agentBrainSource = orchestration.agentBrainSource;
  const agentBrainError = orchestration.agentBrainError;
  const appResolveMs = orchestration.appResolveMs;
  let taskExecutionContract = orchestration.taskExecutionContract;
  let agentMemory = orchestration.agentMemory;

  const fingerprint = screenFingerprint(snapshot, screenshotInfo);
  const screenKey = stableScreenKey(snapshot, screenshotInfo);
  const lastActionKey = latestActionKeyFromRecent(recentAgentActions);
  const androidRequestedVisual = Boolean(agentMemory?.loopSignals?.forceNextVisual);
  const hardForceVisual = Boolean(body.forceVisual === true || body.mustObserveVisual === true);
  const cachedFrameUseful = isVisualFrameCacheable(session.visualFrame);
  const stableScreenMatched = Boolean(session.lastStableScreenKey && session.lastStableScreenKey === screenKey);
  const legacyFingerprintMatched = Boolean(!session.lastStableScreenKey && session.lastFingerprint && session.lastFingerprint === fingerprint);
  const hasCachedFrame = Boolean(session.visualFrame && cachedFrameUseful && (stableScreenMatched || legacyFingerprintMatched));
  const shouldCallVisual = Boolean(screenshotInfo.hasImage);

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
  let inputPhaseGuarded = false;
  let inputPhaseGuardReason = "";
  let semanticSafety = null;
  let semanticSafetyMs = 0;

  // Two-brain runtime: DeepSeek has already chosen the executor.
  // GUI Plus always receives the unchanged original goal; no local route planner or keyword fallback runs here.
  routePlan = null;
  routePlannerMs = 0;
  routePlannerError = "";
  groundingGoal = goal;

  const directAgentBrainPlan = exclusiveGuiPlusVisualSession
    ? null
    : agentBrainRouteToDirectAgentPlan(agentBrainRoute, snapshot, supportedSteps, goal, screenshotInfo, deviceContext, "agent_brain_preflight");
  if (directAgentBrainPlan) {
    agentStep = directAgentBrainPlan.agentStep;
    agentState = directAgentBrainPlan.agentState;
    planSource = directAgentBrainPlan.source || "agent_brain_preflight";
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

      // 连续 wait / 无进展只作为 Android tool_response 回传给云端双脑，不在后端改写为 need_user_help。
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
    const reason = "视觉任务缺少当前截图，GUI Plus 无法规划下一步。";
    agentStep = normalizeAgentStep({ agentStep: { type: "need_user_help", reason, riskLevel: "low", requiresConfirmation: false } }, snapshot, supportedSteps, goal, screenshotInfo, deviceContext);
    agentState = normalizeAgentState({ agentState: { isComplete: false, expectedProgress: false, isWrong: false, confidence: 0.2, reason } }, agentStep);
    planSource = "visual_missing_screenshot";
  }

  // 输入目标与焦点由 GUI Plus 根据截图决定；后端不再把 input_text 改写成 need_user_help。
  inputPhaseGuarded = false;
  inputPhaseGuardReason = "";

  // GUI Plus is the only visual brain. Do not invoke a third text planner to reinterpret the task.
  const shouldTryRealtimeFallback = false;

  // TEST MODE: pass the cloud action through without backend safety judging or rewriting.
  if (agentStep) agentStep = { ...agentStep, riskLevel: "low", requiresConfirmation: false };
  semanticSafety = null;
  semanticSafetyMs = 0;

  if (!exclusiveGuiPlusVisualSession) {
    const executionEnforced = enforceTaskExecutionContractPlan(
      taskExecutionContract,
      null,
      agentStep,
      agentState,
      snapshot,
      supportedSteps,
      goal,
      screenshotInfo,
      deviceContext,
      "task_execution_contract"
    );
    agentStep = executionEnforced.agentStep;
    agentState = executionEnforced.agentState;
    taskExecutionContract = executionEnforced.contract || taskExecutionContract;
    if (executionEnforced.source) planSource = executionEnforced.source;
    session.taskExecutionContract = taskExecutionContract;
  } else {
    taskSemanticContract = null;
    taskExecutionContract = null;
    session.taskSemanticContract = null;
    session.taskExecutionContract = null;
  }

  const screenshotBytesApprox = screenshotInfo.hasImage ? Math.round((screenshotInfo.base64.length * 3) / 4) : 0;
  const promptChars = JSON.stringify({ visualFrame, snapshotHints: { texts: (snapshot.texts || []).slice(0, 12), clickableNodes: (snapshot.clickableNodes || []).slice(0, 12) } }).length;
  const totalMs = Date.now() - startedAt;
  // 每个观察帧只下发一个动作；执行后必须重新截图、重新核验阶段，再规划下一步。
  const agentSteps = [agentStep];
  const actionBatch = agentSteps.slice();
  const stopConditions = normalizeAgentStopConditions(parsed);
  const finishVerificationRequested = agentMemoryRequestsFinishVerification(agentMemory);
  const awaitingFinishVerification = Boolean(agentStep?.type === "finish" && !finishVerificationRequested);
  if (agentStep?.type !== "finish") session.pendingFinishVerification = false;
  finalizeAgentSessionIfComplete(session, agentState, { awaitingFinishVerification });

  return {
    ok: true,
    reply: awaitingFinishVerification
      ? "已生成完成候选，等待 Android 新截图复核。"
      : agentState?.isComplete
        ? "视觉状态已通过新截图复核并确认任务完成。"
        : "已根据当前屏幕规划下一步。",
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
    semanticSafety,
    routePlan,
    agentBrainRoute,
    taskSemanticContract,
    taskExecutionContract,
    executionFeedbackAccepted: agentMemory?.executionFeedback || null,
    lastToolResponseAccepted: agentMemory?.lastToolResponse || null,
    finishVerificationRequested: agentMemoryRequestsFinishVerification(agentMemory),
    awaitingFinishVerification,
    visualReplanRequested: agentMemoryRequestsGuiPlusReplan(agentMemory),
    guiPlusReplanRequested: agentMemoryRequestsGuiPlusReplan(agentMemory),
    routeRefreshRequested: exclusiveGuiPlusVisualSession ? false : agentMemoryRequestsRouteRefresh(agentMemory),
    decisionOwner: exclusiveGuiPlusVisualSession ? "gui_plus" : "cloud_models",
    exclusiveVisualSession: exclusiveGuiPlusVisualSession,
    targetAppResolutionAck: exclusiveGuiPlusVisualSession
      ? null
      : taskExecutionContract?.targetAppResolution || effectiveTargetAppResolution(taskSemanticContract, deviceContext),
    deviceContextAccepted: true,
    deviceContextSchemaAccepted: safeText(deviceContext?.schema || "", 100),
    taskExecutionProtocolAccepted: AGENT_TASK_EXECUTION_CONTRACT_SCHEMA,
    appInventoryHashAccepted: safeText(deviceContext?.appInventoryHash || deviceContext?.inventory?.inventoryHash || "", 120),
    installedAppCountReceived: installedAppsFromDeviceContext(deviceContext).length,
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
      taskRuntime: agentRuntimeTaskInfo(goal, snapshot, deviceContext, agentMemory),
      taskProgress: null,
      taskContractJudgeMs,
      appResolveMs,
      agentBrainMs,
      agentBrainSource,
      agentBrainError,
      clientDeviceContextMs: Number(deviceContext?.clientPerformance?.deviceContextMs || 0),
      clientAppInventoryMs: Number(deviceContext?.clientPerformance?.appInventoryMs || 0),
      clientTargetAppResolveMs: Number(deviceContext?.clientPerformance?.targetAppResolveMs || 0),
      coldStart: currentRequestContext().coldStart ? 1 : 0,
      runtimeAgeMs: Date.now() - RUNTIME_BOOT_AT,
      runtimeRequestIndex: currentRequestContext().requestIndex || 0,
      requestBytes,
      readBodyMs,
      promptChars,
      screenshotBytesApprox,
      buildMessagesMs: 0,
      providerMs,
      semanticSafetyMs,
      semanticSafety,
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
      guiApiMode: "dashscope_native_forced",
      guiHighResolutionImages: ALIYUN_GUI_HIGH_RESOLUTION_IMAGES,
      guiEnableThinking: ALIYUN_GUI_ENABLE_THINKING,
      pluggableGuiProvider: true,
      layeredAgentRuntime: !exclusiveGuiPlusVisualSession,
      agentArchitecture: exclusiveGuiPlusVisualSession
        ? "gui_plus_exclusive_visual_session_backend_transport_only"
        : "cloud_task_contract_state_machine_with_visual_navigation",
      decisionOwner: exclusiveGuiPlusVisualSession ? "gui_plus" : "cloud_models",
      exclusiveVisualSession: exclusiveGuiPlusVisualSession,
      agentBrainBypassed: exclusiveGuiPlusVisualSession,
      taskContractBypassed: exclusiveGuiPlusVisualSession,
      routePlannerBypassed: exclusiveGuiPlusVisualSession,
      taskExecutionPhase: taskExecutionContract?.phase || "",
      taskExecutionAllowedActions: taskExecutionContract?.allowedActions || [],
      requiredCapabilities: taskExecutionContract?.requiredCapabilities || [],
      targetAppResolutionStatus: taskExecutionContract?.targetAppResolutionStatus || "",
      targetAppPackage: taskExecutionContract?.targetApp?.packageName || "",
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
      guiRawOutputLen: Number(parsed?.guiPlusRawLength || 0),
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
  const { response: geoRes, data: geo } = await fetchJsonWithTimeout(geoUrl, { method: "GET" }, 12000, "weather geocode");

  if (!geoRes.ok) {
    throw new Error(`weather geocode ${geoRes.status}`);
  }

  const place = geo?.results?.[0];

  if (!place) {
    throw new Error(`weather location not found: ${location}`);
  }

  const weatherUrl = `https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto`;
  const { response: weatherRes, data: weather } = await fetchJsonWithTimeout(weatherUrl, { method: "GET" }, 12000, "weather api");

  if (!weatherRes.ok) {
    throw new Error(`weather api ${weatherRes.status}`);
  }

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
  const { response: res, data } = await fetchJsonWithTimeout(url, { method: "GET" }, 12000, "exchange api");

  if (!res.ok) {
    throw new Error(`exchange api ${res.status}`);
  }

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
  const { response: res, data: json } = await fetchJsonWithTimeout(url, { method: "GET" }, 12000, "stock api");

  if (!res.ok) {
    throw new Error(`stock api ${res.status}`);
  }

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

  const { response: res, text } = await fetchTextWithTimeout("https://api.tavily.com/search", {
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
      "agentAction 只允许 observe_screen、run_device_control 或 run_agent_task；打开 App、系统设置、设备开关、Shizuku/shell 状态等确定性内部控制走 run_device_control；具体点击、输入、滑动动作必须交给 agent_step 智能体规划接口。"
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


const handleVisualAgentStepRoute = createVisualAgentStepRoute({
  handleAgentStepRequest,
});

const server = http.createServer((req, res) => {
  const requestStartedAt = Date.now();
  const requestIndex = ++RUNTIME_REQUEST_COUNT;
  const coldStart = requestIndex === 1;
  const requestController = new AbortController();
  const abortRequest = () => {
    if (!requestController.signal.aborted) requestController.abort(new Error("client_disconnected"));
  };
  req.once("aborted", abortRequest);
  res.once("close", () => {
    if (!res.writableEnded) abortRequest();
  });

  return REQUEST_CONTEXT.run({
    signal: requestController.signal,
    req,
    res,
    requestStartedAt,
    requestIndex,
    coldStart,
  }, async () => {
  let releaseConcurrency = null;
  try {
    if (req.method === "OPTIONS") {
      return sendJson(res, 204, {});
    }

    if (req.method === "GET") {
      return sendJson(res, 200, {
        ok: true,
        mode: "aliyun-fc-custom-runtime",
        version: WORKER_VERSION,
        runtimeFatalCount: RUNTIME_FATAL_EVENTS.length,
        runtimeLastFatal: RUNTIME_FATAL_EVENTS.length ? RUNTIME_FATAL_EVENTS[RUNTIME_FATAL_EVENTS.length - 1] : null,
        runtimeAgeMs: Date.now() - RUNTIME_BOOT_AT,
        runtimeRequestCount: RUNTIME_REQUEST_COUNT,
        features: [
          "deepseek",
          "aliyun_gui_plus",
          "internal_device_tools",
          "gui_plus_exclusive_visual_session",
          "normal_chat_isolation",
          "web_search",
        ],
        limits: {
          maxBodyBytes: MAX_BODY_BYTES,
          requestBodyTimeoutMs: REQUEST_BODY_TIMEOUT_MS,
          maxImages: MAX_IMAGE_COUNT,
          maxImageBytes: MAX_IMAGE_BYTES,
          maxTotalImageBytes: MAX_TOTAL_IMAGE_BYTES,
          maxAgentScreenshotBytes: MAX_AGENT_SCREENSHOT_BYTES,
          streamHeaderTimeoutMs: PROVIDER_STREAM_HEADER_TIMEOUT_MS,
          streamIdleTimeoutMs: PROVIDER_STREAM_IDLE_TIMEOUT_MS,
          streamAbsoluteTimeoutMs: PROVIDER_STREAM_ABSOLUTE_TIMEOUT_MS,
          maxConcurrentRequestsPerClient: MAX_CONCURRENT_REQUESTS_PER_CLIENT,
          rateLimitMaxRequests: RATE_LIMIT_MAX_REQUESTS,
          agentRateLimitMaxRequests: AGENT_RATE_LIMIT_MAX_REQUESTS,
          authFailureRateLimitMaxRequests: AUTH_FAILURE_RATE_LIMIT_MAX_REQUESTS,
          authFailureRateLimitWindowMs: AUTH_FAILURE_RATE_LIMIT_WINDOW_MS,
          clientAuthConfigured: Boolean(AI_LEDGER_CLIENT_TOKEN),
          clientAuthRequired: Boolean(REQUIRE_CLIENT_AUTH || AI_LEDGER_CLIENT_TOKEN),
        },
      });
    }

    if (req.method !== "POST") {
      return sendJson(res, 405, {
        ok: false,
        error: "method_not_allowed",
      });
    }

    const auth = validateClientAuth(req);
    if (!auth.ok) {
      const authRate = consumeRateLimit(
        requestAuthFailureKey(req),
        AUTH_FAILURE_RATE_LIMIT_MAX_REQUESTS,
        "auth_failure",
        AUTH_FAILURE_RATE_LIMIT_WINDOW_MS
      );
      if (!authRate.allowed) {
        if (responseWritable(res) && !res.headersSent) res.setHeader("Retry-After", String(authRate.retryAfterSeconds));
        return sendJson(res, 429, { ok: false, error: "auth_rate_limited", retryAfterSeconds: authRate.retryAfterSeconds, version: WORKER_VERSION });
      }
      return sendJson(res, auth.status || 401, { ok: false, error: auth.error, version: WORKER_VERSION });
    }
    const clientKey = requestClientKey(req);
    const rate = consumeRateLimit(clientKey, RATE_LIMIT_MAX_REQUESTS, "request");
    if (!rate.allowed) {
      if (responseWritable(res) && !res.headersSent) res.setHeader("Retry-After", String(rate.retryAfterSeconds));
      return sendJson(res, 429, { ok: false, error: "rate_limited", retryAfterSeconds: rate.retryAfterSeconds, version: WORKER_VERSION });
    }
    releaseConcurrency = acquireClientConcurrency(clientKey);
    if (!releaseConcurrency) return sendJson(res, 429, { ok: false, error: "too_many_concurrent_requests", version: WORKER_VERSION });
    res.once("finish", releaseConcurrency);
    res.once("close", releaseConcurrency);

    const readBodyStartedAt = Date.now();
    const body = await readJsonBody(req);
    const wantsStream = wantsSseStream(req, body);
    const readBodyMs = Date.now() - readBodyStartedAt;
    body.__debugRequestBytes = Math.max(0, Number(req.headers["content-length"] || 0));
    body.__debugReadBodyMs = readBodyMs;
    body.__clientNamespace = clientKey;
    const images = normalizeImages(body);
    if (isVisualAgentStepRequest(body) || isAgentModeRequest(body) || isAgentBrainRouteRequest(body) || isAgentOutcomeVerificationRequest(body)) {
      const agentRate = consumeRateLimit(clientKey, AGENT_RATE_LIMIT_MAX_REQUESTS, "agent");
      if (!agentRate.allowed) {
        if (responseWritable(res) && !res.headersSent) res.setHeader("Retry-After", String(agentRate.retryAfterSeconds));
        return sendJson(res, 429, { ok: false, error: "agent_rate_limited", retryAfterSeconds: agentRate.retryAfterSeconds, version: WORKER_VERSION });
      }
    }

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

    if (isAgentBrainRouteRequest(body)) {
      const routeResult = await handleAgentBrainRouteRequest(body, prompt, resolved === "qwen_vision" ? "qwen" : resolved);
      return sendJson(res, routeResult.ok === false ? 400 : 200, routeResult);
    }

    if (isAgentOutcomeVerificationRequest(body)) {
      const verifyResult = await handleAgentOutcomeVerificationRequest(body, prompt, resolved === "qwen_vision" ? "qwen" : resolved);
      return sendJson(res, verifyResult.ok === false ? 400 : 200, verifyResult);
    }

    if (isVisualAgentStepRequest(body)) {
      const visualAgentResult = await handleVisualAgentStepRoute(body, prompt, resolved === "qwen_vision" ? "qwen" : resolved);
      return sendJson(res, visualAgentResult.ok === false ? 400 : 200, visualAgentResult);
    }

    if (isAgentModeRequest(body)) {
      const agentResult = await handleAgentStepRequest(body, prompt, resolved === "qwen_vision" ? "qwen" : resolved);
      return sendJson(res, agentResult.ok === false ? 400 : 200, agentResult);﻿
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
              signal: currentRequestSignal(),
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
          return safeEndResponse(res);
        } catch (streamError) {
          if (sseStarted) {
            const errorText = sanitizeProviderError(streamError, 180);
            writeSse(res, { type: "error", ok: false, error: errorText, code: "provider_stream_failed", version: WORKER_VERSION });
            writeSse(res, { type: "done", ok: false, error: errorText, code: "provider_stream_failed", version: WORKER_VERSION });
            writeSseDone(res);
            return safeEndResponse(res);
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

      const commandPayload = allowModelCommands
        ? extractCommandPayload(reply, body)
        : { agentAction: null, mobileAction: null, preferenceUpdate: null, rejectedReason: "" };
      const cleanReply = allowModelCommands
        ? (stripEmbeddedCommand(reply) || (commandPayload.rejectedReason ? "该设备操作不在当前客户端允许的白名单中，未执行。" : buildDeviceActionReply(commandPayload)))
        : reply;

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
    const embedCommandsInAnswer = shouldEmbedCommandsInAnswer(body);
    const messages = buildMessages(body.messages, prompt, structuredData, sources, toolIntent, body, { includeCommandProtocol: embedCommandsInAnswer });

    if (wantsStream && !embedCommandsInAnswer) {
      let sseStarted = false;
      try {
        const streamOptions = {
          signal: currentRequestSignal(),
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
        return safeEndResponse(res);
      } catch (streamError) {
        if (sseStarted) {
          const errorText = sanitizeProviderError(streamError, 180);
          writeSse(res, { type: "error", ok: false, error: errorText, code: "provider_stream_failed", version: WORKER_VERSION });
          writeSse(res, { type: "done", ok: false, error: errorText, code: "provider_stream_failed", version: WORKER_VERSION });
          writeSseDone(res);
          return safeEndResponse(res);
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

    const commandPayload = embedCommandsInAnswer
      ? extractCommandPayload(reply, body)
      : { agentAction: null, mobileAction: null, preferenceUpdate: null, rejectedReason: "" };
    const cleanReply = embedCommandsInAnswer
      ? (stripEmbeddedCommand(reply) || (commandPayload.rejectedReason ? "该设备操作不在当前客户端允许的白名单中，未执行。" : buildDeviceActionReply(commandPayload)))
      : reply;

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
    if (releaseConcurrency) releaseConcurrency();
    const status = Number(e?.statusCode || 0) || (e?.code === "body_too_large" ? 413 : e?.code === "invalid_json" ? 400 : e?.code === "request_body_timeout" ? 408 : 502);
    const code = String(e?.code || (status >= 500 ? "provider_call_failed" : "request_failed"));
    if (requestController.signal.aborted && !responseWritable(res)) return false;
    return sendJson(res, status, {
      ok: false,
      error: sanitizeProviderError(e, 220),
      code,
      version: WORKER_VERSION,
    });
  } finally {
    if (releaseConcurrency) releaseConcurrency();
  }
  });
});

server.on("clientError", (error, socket) => {
  recordRuntimeFatal("CLIENT_ERROR", error);
  try { if (socket && !socket.destroyed) socket.end("HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n"); } catch (_) {}
});

server.on("error", (error) => recordRuntimeFatal("SERVER_ERROR", error));

if (require.main === module) {
  server.listen(PORT, () => {
    console.log(`AI Ledger cloud-decision gateway listening on ${PORT}; decisionOwner=${CLOUD_DECISION_OWNERSHIP}`);
  });
}

module.exports = {
  WORKER_VERSION,
  isVisualAgentStepRequest,
  isCloudRouteVisualLoopRequest,
  buildAgentBrainRouteMessages,
  normalizeAgentBrainRoutePlan,
  verifiedVisualSurfaceProtocol,
  agentBrainRouteToDirectAgentPlan,
};

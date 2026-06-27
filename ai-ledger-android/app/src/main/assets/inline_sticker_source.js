// ===== AI Ledger source module: 00-config-runtime.js =====
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
// 聊天表情包独立协议开关。v127 默认开启；可通过 ENABLE_CHAT_STICKERS=false 随时关闭。
const ENABLE_CHAT_STICKERS = String(process.env.ENABLE_CHAT_STICKERS || "true").toLowerCase() === "true";
const CHAT_STICKER_SEND_RATE = Math.max(0, Math.min(1, Number(process.env.CHAT_STICKER_SEND_RATE || 0.38)));
const CHAT_STICKER_COOLDOWN_MS = Math.max(0, Number(process.env.CHAT_STICKER_COOLDOWN_MS || 30 * 1000));
const CHAT_STICKER_CLIENT_STATE_MAX = Math.max(32, Math.min(4096, Number(process.env.CHAT_STICKER_CLIENT_STATE_MAX || 1024)));
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
const AGENT_BRAIN_ROUTE_FIRST_ATTEMPT_TIMEOUT_MS = Math.max(2500, Number(process.env.AGENT_BRAIN_ROUTE_FIRST_ATTEMPT_TIMEOUT_MS || 7000));
const AGENT_BRAIN_ROUTE_MAX_TOKENS = Math.max(900, Number(process.env.AGENT_BRAIN_ROUTE_MAX_TOKENS || 1200));
const AGENT_BRAIN_ROUTE_RETRY_MAX_TOKENS = Math.max(
  AGENT_BRAIN_ROUTE_MAX_TOKENS,
  Number(process.env.AGENT_BRAIN_ROUTE_RETRY_MAX_TOKENS || 1400)
);
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
const NORMALIZED_VISUAL_TASK_CONTRACTS = new WeakSet();
const NORMALIZED_VISUAL_TASK_MEMORIES = new WeakSet();
const VISUAL_TASK_STATE_CACHE = new WeakMap();
const REQUEST_CONTEXT = new AsyncLocalStorage();
const RATE_LIMIT_BUCKETS = new Map();
const ACTIVE_REQUESTS_BY_CLIENT = new Map();
const CHAT_STICKER_STATE_BY_CLIENT = new Map();

const AGENT_GUI_PROVIDER = normalizeAgentGuiProviderName(process.env.AGENT_GUI_PROVIDER || process.env.GUI_PROVIDER || "aliyun_gui_plus");
const AGENT_GUI_PROVIDER_URL = String(process.env.AGENT_GUI_PROVIDER_URL || process.env.GUI_PROVIDER_URL || "").trim();
const AGENT_GUI_PROVIDER_BASE_URL = String(process.env.AGENT_GUI_PROVIDER_BASE_URL || process.env.GUI_PROVIDER_BASE_URL || "").trim();
const AGENT_GUI_PROVIDER_API_KEY = String(process.env.AGENT_GUI_PROVIDER_API_KEY || process.env.GUI_PROVIDER_API_KEY || "").trim();
const AGENT_GUI_PROVIDER_MODEL = String(process.env.AGENT_GUI_PROVIDER_MODEL || process.env.GUI_PROVIDER_MODEL || "").trim();
const AGENT_GUI_PROVIDER_TIMEOUT_MS = Number(process.env.AGENT_GUI_PROVIDER_TIMEOUT_MS || process.env.GUI_PROVIDER_TIMEOUT_MS || AGENT_STEP_VISION_TIMEOUT_MS);
const AGENT_GUI_PROVIDER_MAX_TOKENS = Number(process.env.AGENT_GUI_PROVIDER_MAX_TOKENS || process.env.GUI_PROVIDER_MAX_TOKENS || 480);
const AGENT_GUI_PROVIDER_FALLBACK_TO_QWEN = String(process.env.AGENT_GUI_PROVIDER_FALLBACK_TO_QWEN || "false").toLowerCase() === "true";
const AGENT_GUI_STRICT_OFFICIAL_LOOP = String(process.env.AGENT_GUI_STRICT_OFFICIAL_LOOP || "true").toLowerCase() !== "false";
const ALIYUN_GUI_API_KEY = String(process.env.ALIYUN_GUI_API_KEY || process.env.QWEN_API_KEY || "").trim();
const ALIYUN_GUI_BASE_URL = String(process.env.ALIYUN_GUI_BASE_URL || "https://dashscope.aliyuncs.com/compatible-mode/v1").trim();
const ALIYUN_GUI_MODEL = String(process.env.ALIYUN_GUI_MODEL || "gui-plus-2026-02-26").trim();
const CLOUD_DECISION_OWNERSHIP = "normal_chat_models_and_gui_plus_exclusive_visual"; // Visual sessions explicitly owned by GUI Plus bypass AgentBrain and local semantic routing.
const ALIYUN_GUI_TIMEOUT_MS = Number(process.env.ALIYUN_GUI_TIMEOUT_MS || 15000);
const ALIYUN_GUI_MAX_TOKENS = Number(process.env.ALIYUN_GUI_MAX_TOKENS || 640);
const ALIYUN_GUI_API_MODE = String(process.env.ALIYUN_GUI_API_MODE || "dashscope_native").trim().toLowerCase();
const ALIYUN_GUI_HIGH_RESOLUTION_IMAGES = String(process.env.ALIYUN_GUI_HIGH_RESOLUTION_IMAGES || "true").toLowerCase() !== "false";
const ALIYUN_GUI_ENABLE_THINKING = String(process.env.ALIYUN_GUI_ENABLE_THINKING || "false").toLowerCase() === "true";
const AGENT_OFFICIAL_GUI_PLUS_MAX_TIMEOUT_MS = Math.max(4000, Math.min(16000, Number(process.env.AGENT_OFFICIAL_GUI_PLUS_MAX_TIMEOUT_MS || 11500)));
const AGENT_GUI_INTERACTION_REVIEW_TIMEOUT_MS = Math.max(800, Math.min(5000, Number(process.env.AGENT_GUI_INTERACTION_REVIEW_TIMEOUT_MS || 3200)));
const AGENT_GUI_DEEP_THINKING_MODE = String(process.env.AGENT_GUI_DEEP_THINKING_MODE || process.env.AGENT_DEEP_THINKING_MODE || "adaptive").trim().toLowerCase();
const AGENT_GUI_DEEP_THINKING_MIN_NO_PROGRESS = Math.max(2, Math.min(8, Number(process.env.AGENT_GUI_DEEP_THINKING_MIN_NO_PROGRESS || 2)));
const AGENT_GUI_DEEP_THINKING_TIMEOUT_EXTRA_MS = Math.max(0, Math.min(12000, Number(process.env.AGENT_GUI_DEEP_THINKING_TIMEOUT_EXTRA_MS || 3500)));
const AGENT_GUI_DEEP_THINKING_REASON_MAX = Math.max(2, Math.min(10, Number(process.env.AGENT_GUI_DEEP_THINKING_REASON_MAX || 6)));
const AGENT_GUI_EXPLORATION_PRESSURE_STEPS = Math.max(4, Math.min(16, Number(process.env.AGENT_GUI_EXPLORATION_PRESSURE_STEPS || 6)));
const AGENT_GUI_EXPLORATION_BUDGET_STEPS = Math.max(6, Math.min(24, Number(process.env.AGENT_GUI_EXPLORATION_BUDGET_STEPS || 9)));
const AGENT_GUI_HISTORY_N = Math.max(1, Math.min(4, Number(process.env.AGENT_GUI_HISTORY_N || 4)));
const AGENT_GUI_FAST_HISTORY_N = Math.max(1, Math.min(AGENT_GUI_HISTORY_N, Number(process.env.AGENT_GUI_FAST_HISTORY_N || 1)));
const AGENT_GUI_RECOVERY_HISTORY_N = Math.max(AGENT_GUI_FAST_HISTORY_N, Math.min(AGENT_GUI_HISTORY_N, Number(process.env.AGENT_GUI_RECOVERY_HISTORY_N || 2)));
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


const WORKER_VERSION = "qwen-deepseek-cn-web-data-v127-independent-sticker-protocol";
const BACKEND_ARCHITECTURE = Object.freeze({
  schema: "ai_ledger_backend_architecture_v1",
  sourceLayout: "build_time_modules_single_runtime_bundle",
  visualRequestContext: "canonical_once_per_route",
  visualDecisionOwner: "gui_plus_exclusive",
  androidProtocol: "android_visual_agent_v14_task_contract_harness",
  protectedChains: Object.freeze([
    "three_way_observation_binding",
    "task_contract_and_task_memory",
    "single_frame_single_action",
    "android_risk_confirmation",
    "fresh_screen_finish_verification",
  ]),
  sourceModules: Object.freeze([
    "config-runtime",
    "http-provider-runtime",
    "command-protocol",
    "visual-contract-runtime",
    "agent-orchestration",
    "gui-plus-runtime",
    "chat-data-tools",
    "http-server",
  ]),
});
const ANDROID_CLOUD_ROUTE_VISUAL_PROTOCOL = "android_visual_agent_v14_task_contract_harness";
const ANDROID_CLOUD_ROUTE_VISUAL_PROTOCOL_ALIASES = new Set([
  ANDROID_CLOUD_ROUTE_VISUAL_PROTOCOL,
  "android_visual_agent_v13_cloud_route_visual_loop",
]);
const AGENT_TASK_EXECUTION_CONTRACT_SCHEMA = "android_visual_task_contract_v1";
const AGENT_TASK_MEMORY_SCHEMA = "android_visual_task_memory_v1";
const AGENT_ACTION_INTENT_SCHEMA = "android_visual_action_intent_v1";
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

// ===== AI Ledger source module: 10-http-provider-runtime.js =====
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

function structuredResultHttpStatus(result) {
  if (!result || result.ok !== false) return 200;
  if (result.retryable === true) return 503;
  const explicitStatus = Number(result.httpStatus || result.statusCode || 0);
  if (explicitStatus >= 400 && explicitStatus <= 599) return explicitStatus;
  return 400;
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
  let raw = typeof value === "string" ? value : String(value || "");
  if (!raw) return "";
  raw = raw.trim();
  if (/^data:/i.test(raw)) raw = raw.replace(/^data:[^;]+;base64,/i, "");
  // Android screenshots are already compact Base64. Avoid allocating another multi-megabyte
  // string unless whitespace is actually present.
  return /\s/.test(raw) ? raw.replace(/\s+/g, "") : raw;
}

function decodedNormalizedBase64Bytes(base64) {
  if (!base64) return 0;
  if (!/^[A-Za-z0-9+/]*={0,2}$/.test(base64) || base64.length % 4 === 1) return -1;
  const padding = base64.endsWith("==") ? 2 : base64.endsWith("=") ? 1 : 0;
  return Math.max(0, Math.floor((base64.length * 3) / 4) - padding);
}

function decodedBase64Bytes(value) {
  return decodedNormalizedBase64Bytes(normalizeBase64Payload(value));
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
    const decodedBytes = decodedNormalizedBase64Bytes(base64Data);
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
    const reasoningContent = message.reasoning_content ?? message.reasoningContent ?? choice.reasoning_content ?? "";
    const diagnostics = [
      `finish_reason=${safeText(choice.finish_reason || data?.finish_reason || "none", 40) || "none"}`,
      `choices=${Array.isArray(data?.choices) ? data.choices.length : 0}`,
      `content_chars=${String(contentReply || "").length}`,
      `reasoning_chars=${String(reasoningContent || "").length}`,
      `tool_calls=${toolCalls.length + (functionCall ? 1 : 0)}`,
      `completion_tokens=${Math.max(0, Number(data?.usage?.completion_tokens || data?.usage?.output_tokens || 0) || 0)}`,
    ].join(" ");
    throw new Error(`${name} empty ${diagnostics}`);
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


// ===== AI Ledger chat sticker test runtime v1 =====
// 设计目标：只在普通、轻量聊天中附加结构化表情包负载；不改写模型回复，不进入视觉智能体、
// 设备控制、联网检索或结构化实时数据链路。选择失败时静默返回 null。
const CHAT_STICKER_STRUCTURED_TYPE = "chat_sticker_v1";
const CHAT_STICKER_CATALOG = Object.freeze({
  joy_burst: { category: "joy", alt: "开心庆祝" },
  affection_hug: { category: "affection", alt: "喜欢与抱抱" },
  health_check: { category: "care", alt: "关心与照顾" },
  thinking_soft: { category: "thinking", alt: "认真思考" },
  cheer_power: { category: "encourage", alt: "加油鼓励" },
  pout_no: { category: "displeased", alt: "委屈或轻微不满" },
  comfort_friend: { category: "comfort", alt: "温柔安慰" },
  red_packet_congrats: { category: "celebrate", alt: "祝贺与好运" },
  gift_for_you: { category: "gift", alt: "送你一份礼物" },
  sparkle_excited: { category: "excited", alt: "惊喜与期待" },
  soft_smile: { category: "friendly", alt: "友好微笑" },
  got_it_point: { category: "acknowledge", alt: "收到与明白" },
  heart_thanks: { category: "thanks", alt: "感谢与喜欢" },
  confident_ready: { category: "confident", alt: "准备好了" },
  playful_wink: { category: "playful", alt: "俏皮眨眼" },
  confused_study: { category: "confused", alt: "学习困惑" },
  confirm_yes: { category: "approval", alt: "确认正确" },
  idea_drawing: { category: "idea", alt: "有新思路了" },
  reject_no: { category: "reject", alt: "明确否定" },
});


// 初始测试包把 128×128 WebP 直接随 Worker 部署，并且只在真正选中某张表情时回传。
// 这是为了先验证端到端协议；正式版应迁移到 App 本地资源或受控 CDN，避免长期占用响应体。
const CHAT_STICKER_WEBP_BASE64 = Object.freeze({
  joy_burst: "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",
  affection_hug: "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",
  health_check: "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",
  thinking_soft: "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",
  cheer_power: "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",
  pout_no: "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",
  comfort_friend: "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",
  red_packet_congrats: "UklGRuQNAABXRUJQVlA4INgNAADQNQCdASqAAIAAPslSoUsnpKMhrxqsePAZCWQAyujRlOjjvsbcTnWNOh3nv/I4J72Xf6nwj8gvyTGZ8VvUXmT9VvP/oD3p/HPUC9obtna/9a/YC769+t/hehP119gD9XP+f6797t557AX87/wn/f9mj+y/+Xl9/Q/8z+1vwGfzv+99bv0gv2uccVy2odYo8gzsFQ3w+yGsNRDdP23X7KhihUqyn/b5XUEiEL6DA/7kKsFXPDyBMO+xciWlIZC/ut2ondCupTUqYtoAqvdJTT/QlqNVvytvxk4uRt+iCcyy9BSUndIIUb1PakAdyL9pXUnN28RtuBz662jfzFaIgGFwNBrZ3hlfocKWV3qOsKV3aP5T5fDPd3IkLUeX2W1LXegmVKZ36QjpafY+PKGr3IFVLZrAFkbKViXXRF5mhodCXeA6Vmee4l+azkWLlvv0IW/5HhEy0Ey4oHbC6OIEAnzT5vOx8VVcrT2DSQYAbaT86D30PnbO5WYxVcqT/3P9vNoYhdA6wCyBewymritddysnKJLS8K6vlNsguqBhfYGGiBsBI/sW7T5ehwM0WybAtOJ1fR+CdqAAAP78SgEYAKUcu6wWjs29rlBGMLXgQYdB/nOcgXuSS1/ZhuCDg/Qkez/NejtmSKeJN0kAsMQ4NOD8L6P1K5wG8o18vS9J/mN0gs0Gqw+2rE58WZcJk3C+0bNWBEoCNWeKrjZ6vtEEJoaw/h7CVK/K81mTkT86itWCLh+pvwZAflUhXysKmBd3wVI3Vf5TdrIqK7p1uDTHVLafDV7TUqREoLaU2y2DP6kw/NPXaP1LByXeunaleIpaP8hKjnlUo8kxjfswTBavqm5P9pFQgZj9TZZ32E0SBXlzjn3UwFS25/Xi/bZXpwxTdfIwWSp9nenPTXluq7A2d0u0Kf0Rdxmso4LaHdaA03KLGtmkScSO8eBm1NeCD8xAbfa/y/jgA0Ve4n+v0HsUD9i90uyqML1KSrX2Ib/H0NcOJ0kzdO7N/tzkB9R3AdJc4MNRrI2fgerLbDJ5khs1iyhOxObtOOcNOkw4Aavm8V059rg+7wJZb2YJWg/HAG5WpzWdzr1rW+LIm+qaq/kDCrYDoAKjrWUbB+au3tCaZpD5do2P7M7V0StL3XYN+Js3qXtt9lsrZyy+Mm+fnpFf/ipe4nwR0OpC2V2URpOHJu5xaAMJ0CkcDfjFZlwPZfzHvsJxmoG4X40KmRIi0jXes5Zc2reLSHxjV554xmt04XvsxzjXbhg7zylmhyeyCL4XYczZIynjV4eO93oEKAT5XjW71QOEMqPtGdE4kqbo+lV3/YAJSHYUZs67tW1aA5iy1yvmK4DRFr9l/AOBiFZMSYcyo0B3zmgeUclsdhRqDSMdx6CqGkGaOwRpVsSFKEMdimd70mWUYSjlWOS8WS0NRFNtf8X2hfgZFXSaiL7Tj93bTQMsx1Glv9IBk4WFxu2oAMDzpiaf8SJlFkrs2LOCSNmU5uvNrNUgo81tUcVynlkGi/8IPh9dKB+ivDOxZrwN0gM1yUV6nZj9CxuTL9IDvhZuH3odV7u+9vRmjKu93YKT98f/k/o+HJ++8rwWgch/g9Wg0VppBSIJcKXoOnch5MPIuUnV0T6xX+BE1ZM6BRJohZHkXZ5V3Johr4ycv8G1Ef0/7X9zJZzNrE/mLcF8zLMmxF7ZDom3vajDsoiNitFyls2rFZ3dMw47vhqSo1z40XQ+Ryb/0+eO/zDU/UzqJlJGB8XIXGMiFrgc2p6a6dwhuFc/FPR3bjuAK22cUC1he9+OqDrBqLjw8XXL8Rc4vyO1h985HkAyKPRz1xw+AB1RbVSFK5SL0mkOuUS3vKPm80xHpe06xNolqyXG7u4u+/5FV5CfGvl0qOcj8pT/nndmxjYuRhHZmiFe8v5pcStQId9wKbS4wbM2K4r5USTaKP69jjspS6BTl31eKKja/1on6Zy0lyvepIhDXvp/yKMNplMuJhtoSxiLCxgm/jDV3rRmNlpRZyJwKrZbvJNpDDP3ypu6ZcGYv3Rc9iHuV+u6KFIixE436RIpn6LSMxtKu3iMcF5tJc2uFeGsLFgPWXRVH8Xasr6YPboxtG3im83jK5sylmgk1E06HP18/RbNjl5JQPaSAbVYDLmQT5eNQ/eXSXt60wvVkY2lHEww1AoDl3xADpWfW4inH4TNcIZ1QG8Z0Htz5V3zGN5CSkt7m2ggqvLrvdculB56CSfLoHq3SjWxeqa3xOI+h+XJIUombNPme6EKk2+S9W128t+EoNl3Oe3I9jUKWJwOHr9KwLeBKC2JVHrv0/AFw4FE+cLcFYkPhYgByT3HZdaUxTM3AF6LhxueD0XagqO2iuHY91UcCrDwOMDsa8nFfaGC7Tp5Tz1OAx7RDjJBLqPMpP9d1OBeCROSv9owSZM7GSuyjaV4QmdIHc1r644OoOc99FZ+uQewwf034XDm+PrwPFH09U8UjURu2OJLN2fwlKyg5Yk58ia9E60avHbGp5RF+X1gqWnB8T/l8ONdP5GWP3UDPXTn4XqW3pnzmKZmarx4+9JCmXYezX2pvY3vMZ0I3HJ4AsXG+1N14ffzx8cHn/gf1U2fbpcdNf5VLjvrnQuXMqFGfDlgMpLLdUNzkr4gripKQARWi/DoBWglI4ssUXMR5D3ete7pDBPybou3QvoD7HMJKzcIjxe0mrIgQaqT9LDo7xtyz/aqHS2RMMa/FG/jgj9roDa5kSggHOvm27s81bDw4PzqFGj3y/dCRBaQbdvk3CEraYY3ErdL1eIS9h2rOFEjvQvoxXdfwVUc7zeKRZ8P3wYnB7eTIXb0b28fYsViVUPut3VZVEr+3DVTNauea23xTead4F0uKkInPI6GOO2YOkPwJmYOwjHnl0AaVlWxHXveZvLGPyUTKL7ZldcATPHq/l7kg6mF3Bkc4CU+uMCMcOEzeZQsq6OtTzUP+4cI2iUcP7x583KKjLaBVBrlE0bkDNMzBzXTSHOzWj98hMHLfM4J6JyXA1mSJ/D9/UvuXWerJUBIdeYDfQetUihCa/UVt0gdlfnqA/p3hG3Ha74Zqfwt6SyZ7fznPnHp+x/BIdx/6zHRnN2lFlzHSIqBbqcUas+0py4pbjiJqyqV6a3/FYx6LvQKzf2hF+BFRJV71g8PRX+tfSzq0Nc43/sOz8JPgwSjUEJZWouqIpUZRTW2CHDPcqQFPjypTPefq2nyRszomv6FY9LGE4NA49eSaKtGVeVA7tp+PaU1sOfB+zPmF5RcHCP6NkErJqvM1d7hEsWV/PglKiOVGQNwG8NUU2oMgll+u3cjjGuR/aD50AKOpbtfbjfWcw6oxBm3ddQ/yIdeG5Qt/0SejD66matFSO0IPMG1GtqydmgFRKk7P5tRhjYwnik38th0xRIsDMH+6GDYMA60Zt3orW8ARcuLuKtmB+oAU6Y9sJwfWTmovG9GqpjNe7jFtLB5TAde0gZ13xNqjjGmvpTFzGYGDTk+AuYhy7u8XGZTKnxufOT/UXhyjiNi6+asT90JZ+VrDzs4fCqmptxbvLc/COWv7yysXMLPCZ+rejD3FNZcii+4Xo1rs5muTzZ4x9/FOlxnWOGsBJlWwWBK1ZxFGX9HWqvsj6DGTXX0lI4YjrTuzyD/HoEWXM/GVZjvyw3gZIWWF8D+pbNFZCifPVy2YBtwrDUBruoigpQTwgwEA5Hb6iHjPdtqsUbad0HZBRS3+TJeAuJX9Dqp4qaCELqjn/LDi3JjugJuopUhzULNr9al/z19WDR6neGMv3Q9vspjYjOdeKuREIy3JANtNnMoZrTStoRx7WO+tadWigvYux7HyDz/PUEOuQSeFAArQxH/W2BxzHmw/U9jPtKboSYDatTwPJqxhJ5yVzpfxwtHVjWR/zCzJDj1lxMAJb5cE1Yl31GTQzT76BRTPTQszlKbUHYNTVZzBgE55o03uepz/KTytU+IXrbnURu99pZJq0qwowlPZbmT7maadpG3XYY5XaEvduDVDapqS755zfVj9s/we4rEUzbeAwsCKx2HcNLjZ5E0Dvb3XpOJEDPi0ARUsxiHT/6kPIIUdC5q0Ty1cvR3P9vEuui6ncbdzoi9xolqdmiCUOLhJdsoS+snYNWqjEbvTmdvDX/o1LaQgni0YR88HJGTa9+toScdmG78trhISfQP3cPgOF9fAWiY02g0ZyGYk8Q38hlPa1JkEPfLOtiUF8b7c9wwtjNdoSd7malJriHw2621bK0CDGC2Vi2lcBJvxXM45awVMymBIyEoq8WXZEki6fXnsBdZvwPy3nsstD7bbp9hv1zD4VL67I7p8BeGquCZz/2rIImkCUs7zAH9QmjUhmUWS8syoGSaLRJPqbV2p+dolAZ2qe+KOj1eFhcS+DiY6/WnUGMGHkbzFrqF2jp/2/HvNqdgf9RHxyzHR9M/h5mTWF6h28u7t5FQlnyuohIaoecbQmPaNNCBtBDdEBNCfDk/pKAxhj/q9dYbo/QZv+qPJ9HoRwOuodstGiaTvXRd02j7u28sW3DJh3I1/C3HP1/SPKm6Uh+Y2wR7W1NYgvBcLlj5ulChpsNpVGlvJsuPi1989FaArNbfMGwbUT3fWG3S+vFbieZBNjB/H+eOJQfsk4zCWF+eu9JjIjXNJsQL+QqISLO2yjIRlkt3lKitoiZmAB6z4SLvLcr6by+jzgYF+/IT5yS7UkWLboiMc5M/4vL0MbE3vVcgAAAA",
  gift_for_you: "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",
  sparkle_excited: "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",
  soft_smile: "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",
  got_it_point: "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",
  heart_thanks: "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",
  confident_ready: "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",
  playful_wink: "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",
  confused_study: "UklGRtQMAABXRUJQVlA4IMgMAABQNACdASqAAIAAPslOn0snpCKhsHi9qPAZCUAZkaAlmAdrqXpL3AHPW6cRvTtfEYxOiH2/+0fjJ7ZWYfsG1Jvln5AxuMl/kp/keoF7S83/43s8bWegX7B/Xv95xofYP2AP1k8b7wvPSfYA/RH65ex5/4/6z0H/of+b/9f+S+Av+cf2n/vdjz0gFqyZfIr9Y6XB1A2AeN7O8V7wzoe1yPVz2h5m4uxwTAlj5qc6DK6TgdoNKuvo6GUhqhhpCpuu0EwVWNsauQvVekrvv2vCOBn5s0q/Of1282V00Vm3N7HnhpYEINDhrOHZmRR/DtPImL9DsorEFzqDKAM+Rb7crgyGMBPoZqTEavrpDz/YXjyeLmncLZa1VlooqOpPAl0iwam3o1LBJiXf2apw/O7TsSosnf0aKa1+stzTpGIHqBMAcm1wmJ9RHRjob7yCeooPx/cGgX0P0bStwib0ZYlNbqrruJXwMXYVbCAjZL+xygg53VWs1a8/rY3bMz7tHCG2dg5kP0hvshxKHlomrEvs+tbe3rYDpZUcX3z5UZtSjjHn+98JjX+ZDwzv3v2AAP752qc86USSuTqE5G/0yelP1pXnbunFbg01fOS/abC+wpS1yAXk0oHD43/mF7+W0ibo2EEaLW86ywRuV2Rbs7i+/1YZgyzrzX7H2kaYF8Dl17LgE9fpx0BwfnJKyN1iIMEj8NmiQ/R4L6kMwbhfxxiLYWvvNUkfaVO7yrE7Nm8tVUBaOXJ9DIszJS/evTXuJvt9d8ZsjfPdGpgPfp0f22jMw32mIvDjEqgl8BxEZbDsl56LJzF6X0vSzQ3+/rUeVcTfgJm79I5e97pfaz31TGMbIxpoqesP9NKBnrPp30omjFtP+XIwN82Zj9y8PDjuFFKiZOgTF5yS+tlUTf1tPTL82Dd8O/pdfh47IcZyub2ZJ/phxcx3WpfCAl4B9M9PSPYWVJJBxRkqNI8jsabZCFOQMGLUd1IF1pXL8DRzW3/ZusASu4eh23SIuOQanMydr14Yof/DNTUOOPsPGJqV4vEzIDzHcGchBeTHp8cGGIsRJPk/vf8t/rDN9HAiwe+Si5crJucJJQjwPMKyVD2bpgefMvMD8tQQOlWTl93XnUc3WLcmW6esToiriiFWev96mdOkCgRyblwAfKhEkwPlR5yAl7X+KvwDFbf5GGEAwX3Sf+Cs8Sc8RcIeskThKVqLVnBYv0uN8AjKDsZLBSlDL3nvEv5tJMImZEDPWIvjaoc8ew9HJx+SMhtrsfywUUrKiIS4a7Vz12TAntEUEEhbqEyEbnxe/NCAt3aWc524EE3itwjfYCk5O4Orq+na4zBRPiO+z5yC5lugOgawwlx9HnMrmX9v25IiYRdNKRAuJMjHxFmTYp+Yei/kaVb9JU4Ij+cHYUGT2HJTcJe1KelauUAyXH6zVUVIGHg5+4NyFzGvrs9lQorALwFSJZpx4cnn96oiT4cBJpUs+zL8dkGU9a42eBuPLsP3+ciSbtj0FjOOJQc3Ym9atw0eshgv+ve7JwlMr20NV5gXPNKzZnUt1iJ1RFg++/c/7nL3fklqZd/Uf1UatIZbhXadRqZB4DlGDrNhJPrC56JjTjv6AFTRU38S8kdCXffOh0obnUTG1t81LkXI5rb7aqA9lVJ0+Yp42kdiXsatM6d2snU95OtYjmztsvylcWiR4YWiR7Tjbu9ePAr4Zx78LVfJCvxGP/reOBwdpiDqSC8mgTRhcnAi96C/KVzwXyBZMNGOarr3pVEDXVdCm4Sq79qBtFx3aG13OjjBrkvDpdUJqTtaIavmWdZRahEM2TNFA8ifJS1/NFzwXGh0t1ctapApYd24JoeK5nxP2ElgTRg6q+hQ6jmnXOaJrknpUZ1SnL4WWTENg8IVouWqTuvumNyAXNHqMQW0/2dakBE11DggMuQ4HB8P+oNNkPREdivB/6u8H9/A9/Sb4c6HOXrY5LS1GE21Zlcl6bccwOx/b+fBFHEBOvKcCnIdbVxkFFXdwrzNJjjZCb1zp+EbIU6Q3Nop4JC5ve9MjC8qiOC+9T7v5fyGCX5O7zk7dLQIH7XHbF8rWwwrCtSVYJEnItHUrX6hn1uijqw6YM6IboaWj/seRvZ+66Z7IfuTeG+BI7M8QprZm7b/6b+Yr9IaLomI9tCtr/gdsCx9rmxPRPi8oaQsbjPTOxNicRHw8/BB7XhlIk7HqlSPyOpWH9xaw/EdmrpQwL1Sm0f5vB+GV18IzqDhTyxiMqWxC7rZ0vu5kWi9+ZYnhBmr+uC2HrourAZGzBriZCXCgEaeVgH+Q0cZ3d/8fID3NuUNi419W57QZyz3gNxWKmazc0xKLEnURJH6KfN3TwiTk+Izqu+4OWbasceK0ADPLTf5HEm6D1qQFNmEYA8Pw8GoJus1Hkk0tnuA9q7N/cVKoqOJ832ptLKLK7mqXhyCUzdT9N5BC6xvdFm8osBtdMpn8hspIKEDaEk/JhDqBqfD2dlG+FoxJWuDo3/rD2S3pNVVhqHUXT5ErJb8BUp0LkhayNFc9y+YZ5Cmtb5QiFarzWZZBsbvj/PO24gkdDgyEiRBSjaF+lcaB3UdlXDlTghWNj/8z4tWtP2BdtHngiir7akBGNkSpQAGJiPB1dNOvAZA5LdmjfyozIbmPWOCMPa9WVj23l+8dHSgT6nkv/oNIKvAt82udWcIBEc/VNtdvsdQFYKem8yJzDUk80+IxmJ8oZKJ4T3wV/5wYOaYEDVTYa8tU53d3PGGhrTIcNiiRci7Ps6yIRPi+9CO+P0Hu1avwOSk1R+WVaJ0DJ9K/nOdevtCXra9IyguGoWjIGl4Xd217PvRPwuA4IgRWxes3cnl+F1jKXwbM9BEquW1uw9s44nVOqw4rkULYBbs+N4suUdqpff+ETOU4J1mlsTqQmIlHnPvOZ5jUW9F+pxl/cEBFqmjrO2tYDlinthLUFZgazJzWlvTO3hjdD+0radPwipnwQoN/wZD8qye74BxDVPtD4o+VAmgL/+RO8M6XzAE6wf102fSp+si4nBIpLSanvVlro0pfKsKNi1AtywHjsdWsS9uyz080fAo83jtkhq3oWXZOOvfFie7rXhE6zfOJUdCWNj6SjBPIY7twrOGVxrQhgdOGTRmci3m3fewbPWIM0RsEB9TCow1iClYnAv9MH68uneuU9nOWeYbS0qOjzdSwcrlkLaoGmazMyu/zM4RxvdfDkefsgR3lZuZbPvWLfqGlNxjawujWXZ5MIeDejjmnWD9khPfVhse9y5TV8gP2FF3S6X6wqICmIkzef04fqh4PXNKmtcTaUyRrjcFn+qLgQbYhzCzyaCyAc36R/T6Q/i11PX8dmjwGViKYJjz6xYCovn5IaxkYCX5KoMs+2wzbN8+vtQ2vnmJNgLoC0mBzGwHIAnQsR/MW+x3X3qsPnb9LUohqICOO5zEp0tkRK91j7sFGaKV5FuCUSztZ/Fa8m6rlSbTtJ4+Oq82pidzJFoATn/ycNKrvX6EBy1Zq6vHJ+XmXjmE4yPQ6R5n0nxPyn/2qB4SNxUQtUP4iMWNw5Gg7QfrUgwpVxvsjoSeUb40pTdSKE0mDvw+dUPq7V5t8cZ+zg2Pa77Ug9Nlag6GAaKXvS8ahjclogEz9yUveGKLYyW9kxz3MZmyRuGkysN908d73BWhRsLi7HxSKhqGKpafxMxcp/N20V5bk2E78J8W/lSbl4CLyMvVyQDfE7fjiT+MUpjPzPOFPm0LqfrrI0RDbCeKD3EpUedcd5oldnx6+0JKCYcWnku+uk8cXmd+fmp1XjC5yvg5cvH86nXcXnDSww6LdFcohtYcnoQwTxSIaLIeiGFNIBF6tT85bE5PspUUd1mCFtlexwnQ4AA8b2p/df2jv4HGo+kzKJBhUOPexG8zFTUvae1VwgT+uKyW5X4iWbwKP3wylDbScVfoNu6XtXa6N9ms7dzl3lFIBdcFvzgUJ4YA2wEKuJUCA0On9trnq7CWNpoCgAwOJZNzkYixliqkM7LUA9EoL8AGMhQhuGY0qRGzJ+647zo5SKW5+iOqWlbo/bNBvN+Aiy9LJElmewdR/x1tOh75x87k6fOqHgmF/GP9PqECaPERNol95PyYHe3yUnVW/vbDa/ulQt2aApe93eZAPHf2rV1SoX9/WfJZELLd69oXszqEnJQobBuWBFUu+PrvVOVLtg1Tt1HCDnxcZRvFFq0MduW8zBn24/14zhgrS42gzr9uKpfhiA5f9m9Jmq3OXxpKrgArxzpDuitERJvNLoYJsqq1/2ocpz5b59qxf6sMEp3S2Xdz/e/yfvXPsgVplQCQzUa5SCExrzBPvoWxDQA72nMTo4BpV5R4wh1qPRQo0ACTSBiAAA==",
  confirm_yes: "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",
  idea_drawing: "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",
  reject_no: "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"
});

const CHAT_STICKER_RULES = Object.freeze([
  { id: "affection_hug", base: 4, user: ["喜欢你", "爱你", "抱抱", "贴贴"], reply: ["抱抱", "我也很喜欢", "陪着你"] },
  { id: "comfort_friend", base: 4, user: ["难过", "伤心", "焦虑", "压力好大", "崩溃", "失败了", "想哭"], reply: ["别难过", "没关系", "慢慢来", "我陪你", "抱抱"] },
  { id: "health_check", base: 4, user: ["不舒服", "头疼", "发烧", "生病", "肚子疼", "失眠"], reply: ["休息", "身体", "不舒服", "就医"] },
  { id: "red_packet_congrats", base: 4, user: ["过年", "新年", "发财", "中奖", "录取了", "拿奖了"], reply: ["恭喜", "祝贺", "新年快乐", "好运"] },
  { id: "gift_for_you", base: 3, user: ["礼物", "送给我", "奖励"], reply: ["送给你", "小礼物", "奖励你"] },
  { id: "heart_thanks", base: 4, user: ["谢谢", "感谢", "辛苦了", "多谢"], reply: ["不客气", "很高兴帮到你", "不用谢"] },
  { id: "joy_burst", base: 3, user: ["太好了", "好耶", "成功了", "终于好了", "开心", "哈哈哈"], reply: ["太好了", "成功", "搞定了", "好耶", "哈哈"] },
  { id: "sparkle_excited", base: 3, user: ["好期待", "太棒了", "绝了", "厉害", "惊喜"], reply: ["太棒了", "很惊喜", "值得期待", "漂亮"] },
  { id: "cheer_power", base: 3, user: ["加油", "考试", "面试", "比赛", "坚持不住", "复习"], reply: ["加油", "你可以", "稳住", "继续冲", "坚持"] },
  { id: "confused_study", base: 4, user: ["看不懂", "没听懂", "不会做", "不明白", "好难", "还是不懂"], reply: ["换个简单的说法", "一步一步", "再解释", "别急"] },
  { id: "confirm_yes", base: 3, user: ["这样对吗", "是不是这样", "我算对了吗", "可以吗"], reply: ["对的", "没错", "完全正确", "这样可以", "就是这个意思"] },
  { id: "reject_no", base: 3, user: ["能不能违规", "能绕过吗", "这样肯定对吧"], reply: ["不可以", "不能这样", "这个不对", "需要纠正", "不能帮你"] },
  { id: "got_it_point", base: 3, user: ["知道了", "懂了", "明白了", "收到", "好的好的"], reply: ["收到", "明白", "好，那就"] },
  { id: "pout_no", base: 3, user: ["生气", "讨厌", "委屈", "气死了", "烦死了"], reply: ["确实有点气", "太委屈", "不开心"] },
  { id: "playful_wink", base: 2, user: ["开玩笑", "逗你的", "哈哈", "笑死"], reply: ["开个玩笑", "哈哈", "被你发现了"] },
  { id: "idea_drawing", base: 2, user: ["有什么思路", "怎么设计", "想个办法", "方案"], reply: ["有个思路", "可以这样设计", "我想到一个办法", "方案是"] },
  { id: "confident_ready", base: 2, user: ["交给你", "你来做", "准备好了吗"], reply: ["交给我", "没问题", "准备好了", "我来处理"] },
  { id: "thinking_soft", base: 1, user: ["你想想", "分析一下", "帮我看看", "为什么"], reply: ["我来看看", "先分析", "这里关键是", "想了一下"] },
]);

function stickerKeywordHits(text, keywords) {
  const clean = String(text || "").toLowerCase();
  let hits = 0;
  for (const keyword of keywords || []) {
    if (clean.includes(String(keyword).toLowerCase())) hits += 1;
  }
  return hits;
}

function stableStickerGate(clientKey, prompt, reply, rate) {
  const boundedRate = Math.max(0, Math.min(1, Number(rate || 0)));
  if (boundedRate <= 0) return false;
  if (boundedRate >= 1) return true;
  const digest = crypto
    .createHash("sha256")
    .update(`${safeText(clientKey, 64)}|${safeText(prompt, 400)}|${safeText(reply, 600)}`)
    .digest();
  return digest.readUInt32BE(0) / 0xffffffff < boundedRate;
}

function isForcedStickerTest(body, prompt) {
  const mode = String(body?.stickerMode || "").toLowerCase().trim();
  return mode === "force" || /表情包测试|测试表情包|测试表情/.test(String(prompt || ""));
}

function stickerContextBlocked({ body, prompt, reply, structuredData, sources, toolIntent }) {
  if (!ENABLE_CHAT_STICKERS) return true;
  if (String(body?.stickerMode || "").toLowerCase() === "off") return true;
  if (body?.agentStartRequested === true || normalizeIntentName(body?.intent) !== "chat") return true;

  // v127：普通联网来源和 structuredData 不再占用表情包通道。
  // 表情包通过独立顶层 sticker 返回；真正的 Agent / 设备控制仍然禁止发送。
  const activeTool = normalizeIntentName(toolIntent?.tool);
  if (["agent_action", "agent_task", "device_control", "run_device_control"].includes(activeTool)) return true;
  if (shouldAllowModelCommandsInChat(body) && shouldEmbedCommandsInAnswer(body)) return true;

  const cleanReply = String(reply || "").trim();
  if (cleanReply.length < 2 || cleanReply.length > 720) return true;
  const combined = `${String(prompt || "")}\n${cleanReply}`.toLowerCase();
  if (combined.includes("```") || combined.includes("traceback") || combined.includes("exception:")) return true;
  if (/\$\$|\\\(|\\\[|\bgradle\b|\bkotlin\b|\bpython\b|\bjavascript\b|\bapi\b/.test(combined)) return true;

  const seriousSignals = [
    "自杀", "自残", "死亡", "去世", "急救", "报警", "失踪", "家暴", "性侵",
    "验证码", "密码", "转账", "支付", "下单", "买入", "卖出", "投资建议",
    "法律责任", "起诉", "处方", "剂量", "胸痛", "呼吸困难"
  ];
  return seriousSignals.some((signal) => combined.includes(signal));
}

function trimChatStickerState(now = Date.now()) {
  if (CHAT_STICKER_STATE_BY_CLIENT.size <= CHAT_STICKER_CLIENT_STATE_MAX) return;
  for (const [key, state] of CHAT_STICKER_STATE_BY_CLIENT.entries()) {
    if (!state || now - Number(state.sentAt || 0) > Math.max(CHAT_STICKER_COOLDOWN_MS * 4, 10 * 60 * 1000)) {
      CHAT_STICKER_STATE_BY_CLIENT.delete(key);
    }
    if (CHAT_STICKER_STATE_BY_CLIENT.size <= CHAT_STICKER_CLIENT_STATE_MAX) break;
  }
}

function selectChatSticker({ clientKey, body, prompt, reply, structuredData, sources, toolIntent }) {
  try {
    if (stickerContextBlocked({ body, prompt, reply, structuredData, sources, toolIntent })) return null;
    const now = Date.now();
    const stateKey = safeText(clientKey || body?.__clientNamespace || "anonymous", 96) || "anonymous";
    const previous = CHAT_STICKER_STATE_BY_CLIENT.get(stateKey);
    const forcedTest = isForcedStickerTest(body, prompt);
    if (!forcedTest && previous && now - Number(previous.sentAt || 0) < CHAT_STICKER_COOLDOWN_MS) return null;

    const activeTool = normalizeIntentName(toolIntent?.tool);
    const hasDataContext = Boolean(
      structuredData ||
      (Array.isArray(sources) && sources.length > 0) ||
      ["weather", "exchange_rate", "stock", "sports", "web_search"].includes(activeTool)
    );

    let winner = null;
    for (const rule of CHAT_STICKER_RULES) {
      const userHits = stickerKeywordHits(prompt, rule.user);
      const replyHits = stickerKeywordHits(reply, rule.reply);
      if (!userHits && !replyHits) continue;
      const score = Number(rule.base || 0) + userHits * 3 + replyHits * 2;
      if (!winner || score > winner.score) winner = { id: rule.id, score };
    }

    // 数据卡 / 联网来源场景只在情绪信号足够明确时附加表情，避免普通行情和天气查询被随机贴图打扰。
    if (!winner) {
      if (hasDataContext && !forcedTest) return null;
      if (!forcedTest && !stableStickerGate(stateKey, prompt, reply, CHAT_STICKER_SEND_RATE * 0.18)) return null;
      winner = { id: "soft_smile", score: 1 };
    } else if (hasDataContext && !forcedTest && winner.score < 9) {
      return null;
    } else if (!forcedTest && winner.score < 7 && !stableStickerGate(stateKey, prompt, reply, CHAT_STICKER_SEND_RATE)) {
      return null;
    }

    let selectedId = winner.id;
    if (previous?.id === selectedId) {
      const alternative = selectedId === "confirm_yes" ? "got_it_point" : selectedId === "joy_burst" ? "sparkle_excited" : "soft_smile";
      if (CHAT_STICKER_CATALOG[alternative]) selectedId = alternative;
    }
    const meta = CHAT_STICKER_CATALOG[selectedId];
    if (!meta) return null;

    const sticker = {
      id: selectedId,
      pack: "chibi_reactions_v1",
      packVersion: 1,
      category: meta.category,
      placement: "after_text",
      alt: meta.alt,
    };
    CHAT_STICKER_STATE_BY_CLIENT.set(stateKey, { id: selectedId, sentAt: now });
    trimChatStickerState(now);
    return sticker;
  } catch (_) {
    return null;
  }
}

function stickerToResponsePayload(sticker) {
  if (!sticker || !CHAT_STICKER_CATALOG[sticker.id]) return null;
  return {
    schema: "chat_sticker_v2",
    id: sticker.id,
    assetKey: sticker.id,
    pack: sticker.pack || "chibi_reactions_v1",
    packVersion: Number(sticker.packVersion || 1),
    category: sticker.category || "friendly",
    placement: "separate_message",
    alt: sticker.alt || sticker.category || "AI 表情包",
    assetMode: "app_catalog",
  };
}

// 兼容当前已发布 App：没有普通 structuredData 时，继续提供旧版渲染负载。
// 新版 App 应读取顶层 sticker，并通过 assetKey 使用本地表情资源；届时可移除此兼容分支。
function stickerToLegacyStructuredData(sticker) {
  if (!sticker || !CHAT_STICKER_CATALOG[sticker.id]) return null;
  const base64Data = CHAT_STICKER_WEBP_BASE64[sticker.id];
  if (!base64Data) return null;
  return {
    type: CHAT_STICKER_STRUCTURED_TYPE,
    title: "AI 表情",
    subtitle: sticker.alt || sticker.category || "AI 表情包",
    timestamp: null,
    metrics: [],
    rawText: JSON.stringify({
      schema: "chat_sticker_payload_v1",
      id: sticker.id,
      mimeType: "image/webp",
      width: 128,
      height: 128,
      base64Data,
    }),
  };
}

// ===== AI Ledger source module: 20-command-protocol.js =====
const DEVICE_CONTROL_CAPABILITY_ALIASES = Object.freeze({
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
});

function normalizeDeviceControlAction(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const rawCapability = String(value.capability || value.tool || value.type || value.action || value.name || "")
    .toLowerCase()
    .trim()
    .replace(/[.\s\-]+/g, "_");
  const tool = normalizeAgentStepType(DEVICE_CONTROL_CAPABILITY_ALIASES[rawCapability] || rawCapability);
  if (!INTERNAL_TOOL_AGENT_STEP_TYPE_SET.has(tool)) return null;
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
    requiresConfirmation: Boolean(value.requiresConfirmation || value.requireConfirmation || riskRequiresConfirmation(riskLevel)),
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
    .filter((item) => INTERNAL_TOOL_AGENT_STEP_TYPE_SET.has(item));
  const declaredCapabilities = normalizedStringArray(body?.commandProtocol?.supportedDeviceControlActions, 128)
    .map((capability) => normalizeDeviceControlAction({ capability, arguments: {} })?.tool || "")
    .filter((item) => INTERNAL_TOOL_AGENT_STEP_TYPE_SET.has(item));
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

// ===== AI Ledger source module: 30-visual-contract-runtime.js =====
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

  // The current Android client sends the same fresh observation token through
  // runtimeExecutionContext, observationId and expectedActionObservationId.
  // GUI Plus is allowed only when all three values exist and match exactly.
  const runtimeObservationId = safeText(runtime.observationId || "", 120);
  const requestObservationId = safeText(request.observationId || "", 120);
  const expectedActionObservationId = safeText(request.expectedActionObservationId || "", 120);
  const observationIds = [runtimeObservationId, requestObservationId, expectedActionObservationId];
  const missingObservationFields = [
    !runtimeObservationId ? "runtimeExecutionContext.observationId" : "",
    !requestObservationId ? "observationId" : "",
    !expectedActionObservationId ? "expectedActionObservationId" : "",
  ].filter(Boolean);
  const distinctObservationIds = [...new Set(observationIds.filter(Boolean))];
  const observationMatchesExpected = Boolean(
    missingObservationFields.length === 0 &&
      distinctObservationIds.length === 1
  );
  const observationId = observationMatchesExpected ? expectedActionObservationId : "";
  const observationBindingError = observationMatchesExpected
    ? ""
    : missingObservationFields.length
      ? `missing:${missingObservationFields.join(",")}`
      : "conflicting_observation_ids";

  const strictPackageMatches = Boolean(
    selectedTargetPackage &&
      verifiedTargetPackage &&
      currentPackage &&
      selectedTargetPackage === verifiedTargetPackage &&
      verifiedTargetPackage === currentPackage
  );
  const hasFreshVisualFrame = Boolean(
    snapshot?.confidence?.hasVisualImage === true ||
      snapshot?.visual?.available === true ||
      request.hasScreenshot === true ||
      request.hasImage === true
  );
  const deterministicHandoffRecovery = Boolean(
    selectedTargetPackage &&
      currentPackage === selectedTargetPackage &&
      (!verifiedTargetPackage || verifiedTargetPackage === selectedTargetPackage) &&
      observationMatchesExpected &&
      hasFreshVisualFrame &&
      ["launching", "planning", "replanning", "work_surface"].includes(surfaceState)
  );
  const effectiveVerifiedTargetPackage = deterministicHandoffRecovery
    ? selectedTargetPackage
    : verifiedTargetPackage;
  const packageMatches = Boolean(strictPackageMatches || deterministicHandoffRecovery);
  const explicitlyEligible = runtime.guiPlusEligible === true;
  const guiPlusEligible = Boolean(
    observationMatchesExpected &&
      packageMatches &&
      (
        (explicitlyEligible && surfaceState === "work_surface") ||
        deterministicHandoffRecovery
      )
  );
  const verifiedSurfaceInvalidated = Boolean(
    surfaceState === "work_surface" &&
      (!packageMatches || !observationMatchesExpected)
  );

  return {
    schema: "verified_visual_surface_protocol_v4",
    surfaceState: guiPlusEligible ? "work_surface" : surfaceState,
    selectedTargetPackage,
    verifiedTargetPackage: effectiveVerifiedTargetPackage,
    currentPackage,
    observationId,
    expectedActionObservationId,
    actionObservationId: observationId,
    runtimeObservationId,
    requestObservationId,
    observationMatchesExpected,
    observationBindingError,
    missingObservationFields,
    routeEpoch: Math.max(0, Number(runtime.routeEpoch || 0) || 0),
    surfaceEpoch: Math.max(0, Number(runtime.surfaceEpoch || 0) || 0),
    guiPlusEligible,
    packageMatches,
    strictPackageMatches,
    deterministicHandoffRecovery,
    hasFreshVisualFrame,
    verifiedSurfaceInvalidated,
    routeRefreshRequested: Boolean(
      !deterministicHandoffRecovery && (
        request.routeRefreshRequested === true ||
        request.invalidateCachedAgentBrainRoute === true ||
        surfaceState === "replanning" ||
        surfaceState === "interrupted" ||
        verifiedSurfaceInvalidated
      )
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
const DEVICE_TOOL_AGENT_STEP_TYPE_SET = new Set(DEVICE_TOOL_AGENT_STEP_TYPES);
const LEDGER_TOOL_AGENT_STEP_TYPE_SET = new Set(LEDGER_TOOL_AGENT_STEP_TYPES);
const INTERNAL_TOOL_AGENT_STEP_TYPE_SET = new Set(INTERNAL_TOOL_AGENT_STEP_TYPES);

const SUPPORTED_AGENT_STEP_TYPES = Array.from(new Set([
  "open_app",
  "home",
  "back",
  "recents",
  "notifications",
  "quick_settings",
  "tap_node",
  "tap_xy",
  "input_text",
  "scroll",
  "swipe",
  "wait",
  "finish",
  "need_user_help",
  ...INTERNAL_TOOL_AGENT_STEP_TYPES,
]));
const SUPPORTED_AGENT_STEP_TYPE_SET = new Set(SUPPORTED_AGENT_STEP_TYPES);

// Static aliases are allocated once at module load instead of rebuilding a large object for every
// model/tool action. Unsupported long-press/key aliases are intentionally absent: they resolve to
// need_user_help exactly as before, while the backend no longer carries dead Android capabilities.
const AGENT_STEP_TYPE_ALIASES = Object.freeze({
  open: "open_app",
  launch: "open_app",
  launch_app: "open_app",
  open_application: "open_app",
  app_open: "open_app",
  tap: "tap_xy",
  click: "tap_xy",
  press: "tap_xy",
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
});
const AGENT_ACTION_BATCH_MAX = Math.max(1, Math.min(3, Number(process.env.AGENT_ACTION_BATCH_MAX || 1)));

function safeText(value, max = 160) {
  return String(value ?? "").replace(/\s+/g, " ").trim().slice(0, max);
}

function normalizeVisualEvidenceList(value, maxItems = 12, maxChars = 180) {
  const source = Array.isArray(value)
    ? value
    : typeof value === "string"
      ? value.split(/[\n,，;；|]/g)
      : [];
  const out = [];
  const seen = new Set();
  for (const item of source) {
    const clean = safeText(item, maxChars);
    const key = normalizeForMatch(clean);
    if (!clean || !key || seen.has(key)) continue;
    seen.add(key);
    out.push(clean);
    if (out.length >= Math.max(1, Math.min(32, Number(maxItems || 12)))) break;
  }
  return out;
}

function normalizeVisualTaskMilestone(value, index = 0) {
  if (!value || typeof value !== "object") return null;
  const id = safeText(value.id || value.milestoneId || value.key || `milestone_${index + 1}`, 80);
  if (!id) return null;
  return {
    id,
    title: safeText(value.title || value.name || value.purpose || id, 160),
    successEvidence: normalizeVisualEvidenceList(
      value.successEvidence || value.expectedEvidence || value.completionEvidence || [],
      12,
      180
    ),
    failureEvidence: normalizeVisualEvidenceList(value.failureEvidence || value.blockingEvidence || [], 12, 180),
    completed: booleanFromValue(value.completed ?? value.isCompleted, false),
  };
}

function normalizeVisualTaskContract(value, fallbackGoal = "") {
  const source = value && typeof value === "object" ? value : null;
  if (!source) return null;
  const raw = source.taskContract && typeof source.taskContract === "object"
    ? source.taskContract
    : source;
  if (
    NORMALIZED_VISUAL_TASK_CONTRACTS.has(raw) &&
    (!fallbackGoal || safeText(raw.originalGoal || "", 240))
  ) {
    return raw;
  }
  const milestones = (Array.isArray(raw.milestones) ? raw.milestones : [])
    .map(normalizeVisualTaskMilestone)
    .filter(Boolean)
    .slice(0, 24);
  const originalGoal = safeText(raw.originalGoal || raw.goal || fallbackGoal, 240);
  const currentMilestoneId = safeText(
    raw.currentMilestoneId || raw.currentMilestone?.id || raw.currentMilestone || milestones.find((item) => !item.completed)?.id || milestones[0]?.id || "",
    80
  );
  const completedMilestoneIds = normalizeVisualEvidenceList(
    raw.completedMilestoneIds || raw.completedMilestones || milestones.filter((item) => item.completed).map((item) => item.id),
    24,
    80
  );
  const explorationBudgetRaw = Number(raw.explorationBudgetPerMilestone ?? raw.explorationBudget ?? 2);
  const explorationBudgetPerMilestone = Number.isFinite(explorationBudgetRaw)
    ? Math.max(1, Math.min(4, Math.round(explorationBudgetRaw)))
    : 2;
  if (!originalGoal && !currentMilestoneId && !milestones.length) return null;
  const normalized = {
    schema: safeText(raw.schema || AGENT_TASK_EXECUTION_CONTRACT_SCHEMA, 100) || AGENT_TASK_EXECUTION_CONTRACT_SCHEMA,
    originalGoal,
    currentMilestoneId,
    milestones,
    completedMilestoneIds,
    explorationBudgetPerMilestone,
  };
  NORMALIZED_VISUAL_TASK_CONTRACTS.add(normalized);
  return normalized;
}

function normalizeVisualFailedHypothesis(value, index = 0) {
  if (!value || typeof value !== "object") return null;
  const hypothesisId = safeText(value.hypothesisId || value.id || `failed_hypothesis_${index + 1}`, 100);
  if (!hypothesisId) return null;
  return {
    hypothesisId,
    milestoneId: safeText(value.milestoneId || value.milestone || "", 80),
    pageStateId: safeText(value.pageStateId || value.pageId || "", 100),
    actionSignature: safeText(value.actionSignature || value.signature || "", 180),
    actionCluster: safeText(value.actionCluster || value.cluster || "", 180),
    purpose: safeText(value.purpose || "", 180),
    failureReason: safeText(value.failureReason || value.reason || "", 240),
  };
}

function normalizeVisualBlockedAction(value, index = 0) {
  if (!value || typeof value !== "object") return null;
  return {
    milestoneId: safeText(value.milestoneId || value.milestone || "", 80),
    pageStateId: safeText(value.pageStateId || value.pageId || "", 100),
    actionCluster: safeText(value.actionCluster || value.cluster || value.actionSignature || `blocked_${index + 1}`, 180),
    hypothesisId: safeText(value.hypothesisId || value.id || "", 100),
    reason: safeText(value.reason || value.failureReason || "", 240),
  };
}

function normalizeVisualPageState(value) {
  if (!value || typeof value !== "object") return null;
  const id = safeText(value.id || value.pageStateId || value.pageId || "", 100);
  const packageName = safeText(value.packageName || value.currentPackage || value.app || "", 120);
  const summary = safeText(value.summary || value.pageSummary || value.textSummary || "", 320);
  if (!id && !packageName && !summary) return null;
  return { id, packageName, summary };
}

function normalizeVisualTaskMemory(value, taskContract = null, fallbackGoal = "") {
  const source = value && typeof value === "object" ? value : null;
  if (!source && !taskContract) return null;
  if (
    source &&
    NORMALIZED_VISUAL_TASK_MEMORIES.has(source) &&
    (!fallbackGoal || safeText(source.originalGoal || "", 240))
  ) {
    return source;
  }
  const raw = source || {};
  const contract = normalizeVisualTaskContract(
    raw.taskContract || taskContract,
    raw.originalGoal || fallbackGoal
  ) || taskContract || null;
  const failedHypotheses = (Array.isArray(raw.failedHypotheses) ? raw.failedHypotheses : [])
    .map(normalizeVisualFailedHypothesis)
    .filter(Boolean)
    .slice(-24);
  const blockedActions = (Array.isArray(raw.blockedActions) ? raw.blockedActions : [])
    .map(normalizeVisualBlockedAction)
    .filter(Boolean)
    .slice(-24);
  const remainingRaw = Number(
    raw.remainingExplorationBudget ??
      raw.explorationBudgetRemaining ??
      raw.remainingBudget ??
      contract?.explorationBudgetPerMilestone ??
      0
  );
  const remainingExplorationBudget = Number.isFinite(remainingRaw)
    ? Math.max(0, Math.min(8, Math.round(remainingRaw)))
    : Math.max(0, Number(contract?.explorationBudgetPerMilestone || 0));
  const normalized = {
    schema: safeText(raw.schema || AGENT_TASK_MEMORY_SCHEMA, 100) || AGENT_TASK_MEMORY_SCHEMA,
    originalGoal: safeText(raw.originalGoal || contract?.originalGoal || fallbackGoal, 240),
    currentMilestoneId: safeText(raw.currentMilestoneId || contract?.currentMilestoneId || "", 80),
    completedMilestoneIds: normalizeVisualEvidenceList(
      raw.completedMilestoneIds || contract?.completedMilestoneIds || [],
      24,
      80
    ),
    failedHypotheses,
    blockedActions,
    remainingExplorationBudget,
    lastConfirmedPage: normalizeVisualPageState(raw.lastConfirmedPage),
    progressStatus: safeText(raw.progressStatus || raw.lastProgressStatus || "", 40),
    replanRequested: booleanFromValue(raw.replanRequested ?? raw.visualReplanRequested, false),
    recoveryMode: booleanFromValue(raw.recoveryMode, false),
    legacyMode: booleanFromValue(raw.legacyMode, false),
    taskContract: contract,
  };
  NORMALIZED_VISUAL_TASK_MEMORIES.add(normalized);
  return normalized;
}

function visualTaskContractFromRequest(body = null, memory = null, goal = "") {
  const request = body && typeof body === "object" ? body : {};
  const agentMemory = memory && typeof memory === "object" ? memory : {};
  return normalizeVisualTaskContract(
    request.taskContract ||
      request.visualTaskContract ||
      agentMemory.taskContract ||
      agentMemory.taskMemory?.taskContract ||
      null,
    goal
  );
}

function visualTaskMemoryFromRequest(body = null, memory = null, taskContract = null, goal = "") {
  const request = body && typeof body === "object" ? body : {};
  const agentMemory = memory && typeof memory === "object" ? memory : {};
  return normalizeVisualTaskMemory(
    request.taskMemory ||
      agentMemory.taskMemory ||
      request.agentMemory?.taskMemory ||
      null,
    taskContract,
    goal
  );
}

/**
 * Returns the one canonical task contract/memory pair carried by normalized Android memory.
 * Canonical objects are cached by identity; mutable legacy input is normalized without caching so
 * external callers cannot observe stale state after mutation.
 */
function visualTaskStateFromMemory(agentMemory = null, fallbackGoal = "") {
  const memory = agentMemory && typeof agentMemory === "object" ? agentMemory : {};
  const goal = safeText(fallbackGoal, 240);
  const cacheable = Boolean(
    (memory.taskContract && NORMALIZED_VISUAL_TASK_CONTRACTS.has(memory.taskContract)) ||
      (memory.taskMemory && NORMALIZED_VISUAL_TASK_MEMORIES.has(memory.taskMemory))
  );
  if (cacheable) {
    const cached = VISUAL_TASK_STATE_CACHE.get(memory);
    if (cached && cached.goal === goal) return cached.state;
  }
  const taskContract = normalizeVisualTaskContract(
    memory.taskContract || memory.taskMemory?.taskContract || null,
    goal
  );
  const taskMemory = normalizeVisualTaskMemory(memory.taskMemory, taskContract, goal);
  const state = Object.freeze({ taskContract, taskMemory });
  if (cacheable) VISUAL_TASK_STATE_CACHE.set(memory, { goal, state });
  return state;
}

function visualTaskContractPromptBlock(agentMemory = null, goal = "") {
  const memory = agentMemory && typeof agentMemory === "object" ? agentMemory : {};
  const { taskContract: contract, taskMemory } = visualTaskStateFromMemory(memory, goal);
  if (!contract && !taskMemory) return "Task contract: not supplied yet. Create the smallest useful current milestone in the next tool call.";
  return [
    "Android Task Contract / structured memory:",
    JSON.stringify({
      taskContract: contract,
      taskMemory: taskMemory
        ? {
            currentMilestoneId: taskMemory.currentMilestoneId,
            completedMilestoneIds: taskMemory.completedMilestoneIds,
            failedHypotheses: taskMemory.failedHypotheses.slice(-8),
            blockedActions: taskMemory.blockedActions.slice(-8),
            remainingExplorationBudget: taskMemory.remainingExplorationBudget,
            lastConfirmedPage: taskMemory.lastConfirmedPage,
            progressStatus: taskMemory.progressStatus,
            replanRequested: taskMemory.replanRequested,
            recoveryMode: taskMemory.recoveryMode,
            legacyMode: taskMemory.legacyMode,
          }
        : null,
    }),
    "Treat failedHypotheses and blockedActions as hard negative evidence on the same page and milestone.",
    "Do not evade a blocked action by moving to a nearby coordinate while keeping the same purpose.",
    "When remainingExplorationBudget is 0, do not issue another exploratory action; choose a grounded recovery/replan action or interact only if user input is genuinely required.",
  ].join("\n");
}

function normalizeGuiActionIntent(args, compact = null, goal = "", agentMemory = null) {
  const rawArgs = args && typeof args === "object" ? args : {};
  const nested = rawArgs.actionIntent && typeof rawArgs.actionIntent === "object"
    ? rawArgs.actionIntent
    : rawArgs.intent && typeof rawArgs.intent === "object"
      ? rawArgs.intent
      : rawArgs;
  const existingContract = normalizeVisualTaskContract(
    rawArgs.taskContract ||
      nested.taskContract ||
      agentMemory?.taskContract ||
      agentMemory?.taskMemory?.taskContract ||
      null,
    goal
  );
  const purpose = safeText(nested.purpose || rawArgs.purpose || "", 220);
  const explicitMilestoneId = safeText(nested.milestoneId || rawArgs.milestoneId || "", 80);
  const milestoneId = explicitMilestoneId || safeText(existingContract?.currentMilestoneId || "", 80);
  const expectedEvidence = normalizeVisualEvidenceList(
    nested.expectedEvidence || rawArgs.expectedEvidence || [],
    12,
    180
  );
  const failureEvidence = normalizeVisualEvidenceList(
    nested.failureEvidence || rawArgs.failureEvidence || [],
    12,
    180
  );
  const exploratoryRaw = nullableBooleanFromValue(nested.exploratory ?? rawArgs.exploratory);
  const reversibleRaw = nullableBooleanFromValue(nested.reversible ?? rawArgs.reversible);
  const confidenceRaw = Number(nested.confidence ?? rawArgs.confidence ?? compact?.c);
  const confidence = Number.isFinite(confidenceRaw) ? clamp01(confidenceRaw) : undefined;
  const hypothesisId = safeText(nested.hypothesisId || rawArgs.hypothesisId || "", 100);
  const hasIntent = Boolean(
    purpose ||
      explicitMilestoneId ||
      expectedEvidence.length ||
      failureEvidence.length ||
      exploratoryRaw !== null ||
      reversibleRaw !== null ||
      hypothesisId ||
      Boolean(rawArgs.taskContract || nested.taskContract)
  );
  if (!hasIntent) return null;
  return {
    schema: AGENT_ACTION_INTENT_SCHEMA,
    purpose: purpose || undefined,
    milestoneId: milestoneId || undefined,
    expectedEvidence,
    failureEvidence,
    exploratory: exploratoryRaw === null ? undefined : exploratoryRaw,
    reversible: reversibleRaw === null ? undefined : reversibleRaw,
    confidence,
    hypothesisId: hypothesisId || undefined,
    taskContract: existingContract || undefined,
  };
}

function applyGuiPlusActionIntent(compact, rawOutput, goal = "", agentMemory = null) {
  const value = compact && typeof compact === "object" ? compact : {};
  const args = extractAliyunMobileUseToolCall(String(rawOutput || "")) || {};
  const intent = normalizeGuiActionIntent(args, value, goal, agentMemory);
  if (!intent) return value;
  return {
    ...value,
    purpose: intent.purpose,
    milestoneId: intent.milestoneId,
    expectedEvidence: intent.expectedEvidence,
    failureEvidence: intent.failureEvidence,
    exploratory: intent.exploratory,
    reversible: intent.reversible,
    confidence: intent.confidence,
    hypothesisId: intent.hypothesisId,
    actionIntent: intent,
    taskContract: intent.taskContract,
  };
}

function mergeVisualTaskContractFromAction(goal, existingContract, action) {
  const rawAction = action && typeof action === "object" ? action : {};
  const explicit = normalizeVisualTaskContract(
    rawAction.taskContract || rawAction.actionIntent?.taskContract || existingContract,
    goal
  );
  const milestoneId = safeText(
    rawAction.milestoneId ||
      rawAction.actionIntent?.milestoneId ||
      explicit?.currentMilestoneId ||
      "",
    80
  );
  const purpose = safeText(rawAction.purpose || rawAction.actionIntent?.purpose || rawAction.reason || "", 160);
  const expectedEvidence = normalizeVisualEvidenceList(
    rawAction.expectedEvidence || rawAction.actionIntent?.expectedEvidence || [],
    12,
    180
  );
  const failureEvidence = normalizeVisualEvidenceList(
    rawAction.failureEvidence || rawAction.actionIntent?.failureEvidence || [],
    12,
    180
  );
  if (!explicit && !milestoneId && !purpose && !expectedEvidence.length && !failureEvidence.length) return null;
  const base = explicit || {
    schema: AGENT_TASK_EXECUTION_CONTRACT_SCHEMA,
    originalGoal: safeText(goal, 240),
    currentMilestoneId: milestoneId || "goal",
    milestones: [],
    completedMilestoneIds: [],
    explorationBudgetPerMilestone: 2,
  };
  const currentId = milestoneId || base.currentMilestoneId || "goal";
  const milestones = Array.isArray(base.milestones) ? base.milestones.slice(0, 24) : [];
  const index = milestones.findIndex((item) => item.id === currentId);
  const nextMilestone = {
    id: currentId,
    title: purpose || milestones[index]?.title || currentId,
    successEvidence: expectedEvidence.length ? expectedEvidence : (milestones[index]?.successEvidence || []),
    failureEvidence: failureEvidence.length ? failureEvidence : (milestones[index]?.failureEvidence || []),
    completed: rawAction.type === "finish" || milestones[index]?.completed === true,
  };
  if (index >= 0) milestones[index] = { ...milestones[index], ...nextMilestone };
  else milestones.push(nextMilestone);
  const completedMilestoneIds = normalizeVisualEvidenceList([
    ...(base.completedMilestoneIds || []),
    ...(rawAction.type === "finish" ? [currentId] : []),
  ], 24, 80);
  return {
    ...base,
    schema: safeText(base.schema || AGENT_TASK_EXECUTION_CONTRACT_SCHEMA, 100),
    originalGoal: safeText(base.originalGoal || goal, 240),
    currentMilestoneId: currentId,
    milestones,
    completedMilestoneIds,
  };
}

function deterministicBackendError(error) {
  const name = String(error?.name || "").toLowerCase();
  const message = String(error?.message || error || "").toLowerCase();
  return Boolean(
    name === "referenceerror" ||
      name === "syntaxerror" ||
      message.includes(" is not defined") ||
      message.includes("cannot access") ||
      message.includes("unexpected token") ||
      message.includes("backend_contract_not_defined")
  );
}

function retryableCloudError(error) {
  if (deterministicBackendError(error)) return false;
  const message = String(error?.message || error || "").toLowerCase();
  return Boolean(
    isTimeoutLikeError(error) ||
      /(?:\b429\b|\b500\b|\b502\b|\b503\b|\b504\b|econnreset|econnrefused|socket hang up|temporarily unavailable|rate limit)/i.test(message)
  );
}

function normalizeAgentStepType(value) {
  const type = String(value || "").toLowerCase().trim().replace(/[\s\-]+/g, "_");
  if (SUPPORTED_AGENT_STEP_TYPE_SET.has(type)) return type;
  const normalized = AGENT_STEP_TYPE_ALIASES[type] || "";
  return SUPPORTED_AGENT_STEP_TYPE_SET.has(normalized) ? normalized : "need_user_help";
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
  const raw = String(value || "").toLowerCase().trim().replace(/[\s-]+/g, "_");
  if (["user_input", "credential_input", "sensitive", "private"].includes(raw)) return "user_input";
  if (["financial", "financial_transaction", "trade", "transaction"].includes(raw)) return "financial_transaction";
  if (["purchase", "checkout", "order_submit"].includes(raw)) return "purchase";
  if (["consequential", "external_effect"].includes(raw)) return "consequential";
  if (["irreversible", "destructive"].includes(raw)) return "irreversible";
  if (raw === "critical") return "critical";
  if (raw === "high") return "high";
  if (["medium", "mid"].includes(raw)) return "medium";
  if (["low", "safe", "navigation", "navigate", ""].includes(raw)) return "low";
  return "low";
}

function riskRequiresConfirmation(value) {
  return [
    "high",
    "critical",
    "financial_transaction",
    "purchase",
    "consequential",
    "irreversible",
  ].includes(normalizeRiskLevel(value));
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
    "purpose", "milestoneId", "expectedEvidence", "failureEvidence", "exploratory", "reversible", "confidence", "hypothesisId",
    "actionIntent", "taskContract",
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

function isInternalToolAgentStepType(type) {
  return INTERNAL_TOOL_AGENT_STEP_TYPE_SET.has(normalizeAgentStepType(type));
}


// Backend semantic safety judging and action rewriting remain disabled.
// GUI Plus / DeepSeek own semantic decisions; the backend only preserves structured risk metadata
// and Android enforces the non-bypassable confirmation boundary.

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

  const decodedBytes = decodedNormalizedBase64Bytes(base64);
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

/**
 * Converts all accepted legacy aliases into one canonical visual request object. The clone is
 * shallow: screenshot Base64, node arrays and app inventories stay shared instead of being copied.
 * Downstream visual code therefore reads one field name per concept while old clients remain
 * compatible at this single boundary.
 */
function canonicalizeVisualAgentRequest(body) {
  const source = body && typeof body === "object" ? body : {};
  const request = { ...source };

  const goal = safeText(
    source.agentGoal || source.goal || source.message || source.prompt || "",
    240
  );
  if (goal) {
    request.agentGoal = goal;
    request.goal = goal;
  }

  const recentAgentActions = Array.isArray(source.recentAgentActions)
    ? source.recentAgentActions
    : Array.isArray(source.recentActions)
      ? source.recentActions
      : [];
  request.recentAgentActions = recentAgentActions;

  if (!request.lastToolResponse && source.toolResponse && typeof source.toolResponse === "object") {
    request.lastToolResponse = source.toolResponse;
  }
  if (!request.agentSessionId && source.sessionId) {
    request.agentSessionId = safeText(source.sessionId, 120);
  }
  if (!request.deviceId && source.clientId) {
    request.deviceId = safeText(source.clientId, 120);
  }
  if (request.hasScreenshot === undefined) {
    const screenshot = source.screenshot && typeof source.screenshot === "object"
      ? source.screenshot
      : {};
    const visual = source.screenSnapshot?.visual && typeof source.screenSnapshot.visual === "object"
      ? source.screenSnapshot.visual
      : {};
    request.hasScreenshot = Boolean(
      source.hasImage === true ||
        source.hasImages === true ||
        screenshot.base64 ||
        screenshot.base64Data ||
        screenshot.imageBase64 ||
        screenshot.data ||
        visual.available === true ||
        visual.base64Jpeg ||
        visual.base64 ||
        visual.base64Data
    );
  }

  delete request.recentActions;
  delete request.toolResponse;
  delete request.sessionId;
  delete request.clientId;
  delete request.message;
  delete request.hasImage;
  delete request.hasImages;
  return request;
}

/**
 * Performs all request-wide visual normalization exactly once. Provider planners, routing and
 * response binding reuse these immutable references instead of repeatedly traversing the same
 * screenshot metadata, app inventory and task memory.
 */
function buildVisualAgentRequestContext(body, prompt = "") {
  const request = canonicalizeVisualAgentRequest(body);
  const goal = safeText(request.agentGoal || request.goal || prompt, 240);
  const snapshot = compactScreenSnapshot({
    ...(request.screenSnapshot && typeof request.screenSnapshot === "object" ? request.screenSnapshot : {}),
    currentApp: request?.screenSnapshot?.currentApp || request?.screenSnapshot?.packageName || request?.currentPackage || "",
    packageName: request?.screenSnapshot?.packageName || request?.screenSnapshot?.currentApp || request?.currentPackage || "",
  });
  const supportedSteps = supportedAgentStepsFromBody(request);
  const screenshotInfo = normalizeAgentScreenshot(request);
  const rawDeviceContext = request.deviceContext && typeof request.deviceContext === "object"
    ? request.deviceContext
    : {};
  const deviceContext = mergeVisualAppContextIntoDeviceContext(request, { ...rawDeviceContext });
  if (request.targetAppResolution && typeof request.targetAppResolution === "object") {
    deviceContext.targetAppResolution = request.targetAppResolution;
  }
  const recentAgentActions = request.recentAgentActions.slice(-12);
  const rawAgentMemoryInput = request.agentMemory && typeof request.agentMemory === "object"
    ? request.agentMemory
    : {};
  const agentMemory = normalizeAndroidVisualExecutionProtocol(
    request,
    rawAgentMemoryInput,
    recentAgentActions
  );
  const verifiedSurface = verifiedVisualSurfaceProtocol(
    request,
    snapshot,
    deviceContext,
    agentMemory
  );
  return Object.freeze({
    request,
    goal,
    snapshot,
    supportedSteps,
    screenshotInfo,
    deviceContext,
    recentAgentActions,
    agentMemory,
    verifiedSurface,
    controllerHandoffActive: isControllerHandoffSurface(
      request,
      snapshot,
      deviceContext,
      agentMemory
    ),
    cloudRouteVisualLoopRequest: isCloudRouteVisualLoopRequest(request),
    strictObservationBindingRequired: Boolean(
      isVisualAgentStepRequest(request) || isCloudRouteVisualLoopRequest(request)
    ),
    requestBytes: Math.max(0, Number(request.__debugRequestBytes || 0) || 0),
    readBodyMs: Math.max(0, Number(request.__debugReadBodyMs || 0) || 0),
  });
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

function escapeRegExp(value) {
  return String(value || "").replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
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
    .filter((item, index, arr) => SUPPORTED_AGENT_STEP_TYPE_SET.has(item) && arr.indexOf(item) === index);
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

  const actionIntentRaw = nested.actionIntent && typeof nested.actionIntent === "object"
    ? nested.actionIntent
    : nested.semanticIntent && typeof nested.semanticIntent === "object"
      ? nested.semanticIntent
      : nested.progressContract && typeof nested.progressContract === "object"
        ? nested.progressContract
        : nested;
  const actionIntent = normalizeGuiActionIntent(
    {
      ...args,
      ...actionIntentRaw,
      taskContract: nested.taskContract || actionIntentRaw.taskContract || args.taskContract,
    },
    { c: nested.confidence },
    goal,
    null
  );

  // Preserve the cloud model's structured risk declaration. Android enforces the final
  // confirmation boundary; the backend never infers risk from goal text, labels or coordinates.
  const riskLevel = normalizeRiskLevel(
    nested.riskLevel || nested.risk || args.riskLevel || args.risk || "low"
  );
  const requiresConfirmation = Boolean(
    nested.requiresConfirmation || nested.requireConfirmation ||
      args.requiresConfirmation || args.requireConfirmation
  ) || riskRequiresConfirmation(riskLevel);

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
    purpose: actionIntent?.purpose,
    milestoneId: actionIntent?.milestoneId,
    expectedEvidence: actionIntent?.expectedEvidence || [],
    failureEvidence: actionIntent?.failureEvidence || [],
    exploratory: actionIntent?.exploratory,
    reversible: actionIntent?.reversible,
    confidence: actionIntent?.confidence,
    hypothesisId: actionIntent?.hypothesisId,
    actionIntent: actionIntent || undefined,
    taskContract: actionIntent?.taskContract,
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

const APP_CAPABILITY_ALIASES = Object.freeze({
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
});

function normalizeAppCapability(value) {
  const raw = String(value || "").trim().toLowerCase().replace(/[\s\-]+/g, "_");
  return APP_CAPABILITY_ALIASES[raw] || raw;
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

// ===== AI Ledger source module: 40-agent-orchestration.js =====
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

// Legacy local task-skill scoring was removed from the cloud two-brain architecture.
// DeepSeek owns route/app semantics, GUI Plus owns visual action semantics, and
// Android owns deterministic execution feedback. No local task-skill registry or
// keyword progress scorer remains on the production request path.

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
  const explorationSprawlCount = Number(loopSignals.explorationSprawlCount || executionFeedback.explorationSprawlCount || 0);
  const explorationPressure = computeVisualExplorationPressure(memory);
  const finishVerificationRequested = agentMemoryRequestsFinishVerification(memory);
  const routeRefreshRequested = agentMemoryRequestsRouteRefresh(memory);
  const { taskContract, taskMemory } = visualTaskStateFromMemory(memory, "");

  const lines = [];
  if (taskMemory?.currentMilestoneId) {
    lines.push(`Current milestone=${taskMemory.currentMilestoneId}; progressStatus=${taskMemory.progressStatus || "unknown"}; explorationBudgetRemaining=${taskMemory.remainingExplorationBudget}.`);
  }
  if (taskMemory?.failedHypotheses?.length) {
    lines.push(`Failed hypotheses (do not repeat on the same page/milestone): ${taskMemory.failedHypotheses.slice(-6).map((item) => `${item.hypothesisId}:${item.failureReason || item.purpose || "failed"}`).join(" / ")}`);
  }
  if (taskMemory?.blockedActions?.length) {
    lines.push(`Blocked action clusters: ${taskMemory.blockedActions.slice(-6).map((item) => `${item.actionCluster}:${item.reason || "blocked"}`).join(" / ")}`);
  }
  if (taskMemory?.lastConfirmedPage) {
    lines.push(`Last confirmed useful page: ${JSON.stringify(taskMemory.lastConfirmedPage)}.`);
  }
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
  if (explorationSprawlCount > 0) lines.push(`Android explorationSprawlCount=${explorationSprawlCount}: recent steps changed screens repeatedly without proving convergence; the next action must narrow or correct the route instead of expanding exploration.`);
  if (explorationPressure.pressureLevel !== "low") lines.push(`Android explorationPressure=${explorationPressure.pressureLevel}; executedStepCount=${explorationPressure.executedStepCount}; budgetRemaining=${explorationPressure.budgetRemaining}; reasons=${explorationPressure.reasons.join("/") || "none"}.`);
  return lines.join("\n").slice(0, 1800);
}


function normalizeAgentDeepThinkingMode(value) {
  const raw = String(value || "").trim().toLowerCase().replace(/[-\s]+/g, "_");
  if (["0", "false", "off", "none", "fast", "disable", "disabled"].includes(raw)) return "fast";
  if (["1", "true", "on", "deep", "always", "force", "full"].includes(raw)) return "deep";
  return "adaptive";
}

function computeVisualExplorationPressure(agentMemory = null) {
  const memory = agentMemory && typeof agentMemory === "object" ? agentMemory : {};
  const loopSignals = memory.loopSignals && typeof memory.loopSignals === "object" ? memory.loopSignals : {};
  const executionFeedback = memory.executionFeedback && typeof memory.executionFeedback === "object" ? memory.executionFeedback : {};
  const executedStepCount = Math.max(0, Number(loopSignals.executedStepCount || 0) || 0);
  const loopIndex = Math.max(0, Number(loopSignals.loopIndex || 0) || 0);
  const explorationSprawlCount = Math.max(0, Number(loopSignals.explorationSprawlCount || executionFeedback.explorationSprawlCount || 0) || 0);
  const noProgressCount = Math.max(0, Number(loopSignals.noProgressCount || executionFeedback.noProgressCount || 0) || 0);
  const sameActionCount = Math.max(0, Number(loopSignals.sameActionCount || executionFeedback.sameActionCount || 0) || 0);
  const interactionTurnCount = Math.max(0, Number(memory.interactionTurnCount || memory.interactionHistory?.length || 0) || 0);
  const structuredBudgetRaw = Number(memory.taskMemory?.remainingExplorationBudget);
  const structuredBudgetAvailable = Number.isFinite(structuredBudgetRaw);
  const globalBudgetRemaining = Math.max(0, AGENT_GUI_EXPLORATION_BUDGET_STEPS - executedStepCount);
  const budgetRemaining = structuredBudgetAvailable
    ? Math.max(0, Math.min(globalBudgetRemaining, Math.round(structuredBudgetRaw)))
    : globalBudgetRemaining;
  const reasons = [];
  if (structuredBudgetAvailable) reasons.push(`milestoneBudget=${Math.max(0, Math.round(structuredBudgetRaw))}`);
  if (explorationSprawlCount > 0) reasons.push(`sprawl=${explorationSprawlCount}`);
  if (executedStepCount >= AGENT_GUI_EXPLORATION_PRESSURE_STEPS) reasons.push(`steps=${executedStepCount}`);
  if (loopIndex >= AGENT_GUI_EXPLORATION_BUDGET_STEPS) reasons.push(`loops=${loopIndex}`);
  if (noProgressCount > 0) reasons.push(`noProgress=${noProgressCount}`);
  if (sameActionCount >= 2) reasons.push(`sameAction=${sameActionCount}`);
  if (interactionTurnCount > 1) reasons.push(`interactionTurns=${interactionTurnCount}`);
  const budgetExceeded = Boolean(
    executedStepCount >= AGENT_GUI_EXPLORATION_BUDGET_STEPS ||
      (structuredBudgetAvailable && structuredBudgetRaw <= 0)
  );
  const pressureLevel = budgetExceeded || explorationSprawlCount > 0
    ? "high"
    : executedStepCount >= AGENT_GUI_EXPLORATION_PRESSURE_STEPS || interactionTurnCount > 0
      ? "medium"
      : "low";
  return {
    executedStepCount,
    loopIndex,
    explorationSprawlCount,
    noProgressCount,
    sameActionCount,
    interactionTurnCount,
    budgetRemaining,
    budgetExceeded,
    pressureLevel,
    reasons,
  };
}

function structuralGuiThinkingDecision(agentMemory = null) {
  const m = agentMemory && typeof agentMemory === "object" ? agentMemory : {};
  const s = m.loopSignals || {}, f = m.executionFeedback || {}, t = m.lastToolResponse || {};
  const explorationPressure = computeVisualExplorationPressure(m);
  const noProgress = Math.max(0, Number(s.noProgressCount || 0));
  const explorationSprawl = Math.max(0, Number(s.explorationSprawlCount || f.explorationSprawlCount || 0));
  const sameAction = Math.max(0, Number(s.sameActionCount || 0));
  const executedStepCount = Math.max(0, Number(s.executedStepCount || 0));
  const loopIndex = Math.max(0, Number(s.loopIndex || 0));
  const interactionTurnCount = Math.max(0, Number(m.interactionTurnCount || m.interactionHistory?.length || 0));
  const screenChangedCount = Math.max(0, Number(s.screenChangedCount || f.screenChangedCount || 0));
  const currentMatchesVerified = nullableBooleanFromValue(
    m.surfaceContext?.currentPackageMatchesVerifiedTarget ??
      s.currentPackageMatchesVerifiedTarget ??
      f.currentPackageMatchesVerifiedTarget
  );
  const blocked = runtimeBlockedActionSignatures(m);
  const reasons = [
    executedStepCount <= 1 || loopIndex <= 1 ? "firstVisualTurn" : "",
    t.finishVerificationRequested === true ? "finishVerification" : "",
    s.visualReplanRequested === true || f.visualReplanRequested === true ? "visualReplanRequested" : "",
    t.screenChanged === true && executedStepCount > 0 && executedStepCount <= 3 ? "earlyScreenChange" : "",
    screenChangedCount >= 2 && executedStepCount <= 4 ? `screenChangedCount=${screenChangedCount}` : "",
    currentMatchesVerified === false ? "crossPackageSurface" : "",
    interactionTurnCount > 0 ? `interactionTurnCount=${interactionTurnCount}` : "",
    explorationSprawl > 0 ? `explorationSprawl=${explorationSprawl}` : "",
    explorationPressure.pressureLevel === "high" ? `explorationPressure=high` : "",
    explorationPressure.budgetExceeded ? `explorationBudgetExceeded` : "",
    noProgress >= AGENT_GUI_DEEP_THINKING_MIN_NO_PROGRESS ? `noProgress=${noProgress}` : "",
    sameAction >= 2 ? `sameAction=${sameAction}` : "",
    blocked.length ? `blocked=${blocked.slice(0, 4).join("/")}` : "",
    f.lastResultOk === false || t.success === false ? "lastActionFailed" : "",
  ].filter(Boolean);
  const mode = normalizeAgentDeepThinkingMode(AGENT_GUI_DEEP_THINKING_MODE);
  const enabled = ALIYUN_GUI_ENABLE_THINKING || mode === "deep" || (mode === "adaptive" && reasons.length > 0);
  return {
    mode, enabled,
    level: mode === "deep" ? "deep" : enabled ? "adaptive_deep" : "fast",
    reasons: reasons.slice(0, AGENT_GUI_DEEP_THINKING_REASON_MAX),
    timeoutExtraMs: enabled ? AGENT_GUI_DEEP_THINKING_TIMEOUT_EXTRA_MS : 0,
  };
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

function stableGuiPlusInteractionId(role, content, replyToInteractionId = "", ordinal = 0) {
  const canonical = [
    safeText(role || "unknown", 24),
    safeText(replyToInteractionId || "", 96),
    safeText(content || "", 1200),
    String(Math.max(0, Number(ordinal || 0) || 0)),
  ].join("|");
  return `interaction_${crypto.createHash("sha256").update(canonical).digest("hex").slice(0, 20)}`;
}

function normalizeGuiPlusInteractionHistory(value, maxItems = 16) {
  const source = Array.isArray(value) ? value : [];
  const out = [];
  for (let index = 0; index < source.length; index += 1) {
    const item = source[index];
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
    const replyToInteractionId = safeText(
      item.replyToInteractionId || item.reply_to_interaction_id || item.replyTo || item.parentInteractionId || "",
      96
    );
    const interactionId = safeText(
      item.interactionId || item.interaction_id || item.turnId || item.id || "",
      96
    ) || stableGuiPlusInteractionId(role, content, replyToInteractionId, index);
    out.push({
      interactionId,
      replyToInteractionId,
      role,
      content,
      sensitiveRedacted,
    });
  }

  const bounded = out.slice(-Math.max(1, Math.min(32, Number(maxItems || 16))));
  let latestAssistantId = "";
  return bounded.map((item) => {
    if (item.role === "assistant") {
      latestAssistantId = item.interactionId;
      return item;
    }
    if (item.role === "user" && !item.replyToInteractionId && latestAssistantId) {
      return { ...item, replyToInteractionId: latestAssistantId };
    }
    return item;
  });
}

function guiPlusInteractionHistoryFromRecentActions(recentActions = []) {
  const turns = [];
  let latestQuestionId = "";
  let questionOrdinal = 0;
  let answerOrdinal = 0;
  for (const raw of Array.isArray(recentActions) ? recentActions : []) {
    const line = String(raw || "").trim();
    if (!line) continue;
    if (line.startsWith("guiPlusQuestion:")) {
      const content = line.slice("guiPlusQuestion:".length);
      const interactionId = stableGuiPlusInteractionId("assistant", content, "", questionOrdinal++);
      latestQuestionId = interactionId;
      turns.push({ interactionId, replyToInteractionId: "", role: "assistant", content, sensitiveRedacted: false });
    } else if (line.startsWith("userReply:")) {
      const content = line.slice("userReply:".length);
      turns.push({
        interactionId: stableGuiPlusInteractionId("user", content, latestQuestionId, answerOrdinal++),
        replyToInteractionId: latestQuestionId,
        role: "user",
        content,
        sensitiveRedacted: /敏感输入|private_step|completed_private/i.test(content),
      });
    } else if (line.startsWith("userInstruction:")) {
      const content = line.slice("userInstruction:".length);
      turns.push({
        interactionId: stableGuiPlusInteractionId("user", content, "", answerOrdinal++),
        replyToInteractionId: "",
        role: "user",
        content,
        sensitiveRedacted: false,
      });
    }
  }
  return normalizeGuiPlusInteractionHistory(turns, 16);
}

function mergeGuiPlusInteractionHistories(...sources) {
  const ordered = [];
  const indexById = new Map();
  for (const source of sources) {
    for (const item of normalizeGuiPlusInteractionHistory(source, 32)) {
      const existingIndex = indexById.get(item.interactionId);
      if (existingIndex !== undefined) {
        ordered[existingIndex] = { ...ordered[existingIndex], ...item };
        continue;
      }
      const exactDuplicate = ordered.findIndex((existing) =>
        existing.role === item.role &&
        existing.content === item.content &&
        existing.replyToInteractionId === item.replyToInteractionId
      );
      if (exactDuplicate >= 0) continue;
      indexById.set(item.interactionId, ordered.length);
      ordered.push(item);
    }
  }
  return normalizeGuiPlusInteractionHistory(ordered.slice(-16), 16);
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

function guiPlusAnsweredInteractionIds(history) {
  return new Set(
    normalizeGuiPlusInteractionHistory(history, 32)
      .filter((item) => item.role === "user" && item.replyToInteractionId)
      .map((item) => item.replyToInteractionId)
  );
}

function guiPlusAnsweredQuestionContents(history) {
  const normalized = normalizeGuiPlusInteractionHistory(history, 32);
  const answeredIds = guiPlusAnsweredInteractionIds(normalized);
  return new Set(
    normalized
      .filter((item) => item.role === "assistant" && answeredIds.has(item.interactionId))
      .map((item) => normalizeForMatch(item.content))
      .filter(Boolean)
  );
}

function isGuiPlusQuestionAlreadyAnswered(history, question) {
  const normalizedQuestion = normalizeForMatch(safeText(question || "", 1200));
  return Boolean(normalizedQuestion && guiPlusAnsweredQuestionContents(history).has(normalizedQuestion));
}

function formatGuiPlusInteractionHistoryForPrompt(history) {
  const normalized = normalizeGuiPlusInteractionHistory(history, 16);
  if (!normalized.length) return "none";
  return normalized.map((item, index) => {
    const speaker = item.role === "assistant" ? "GUI Plus" : "User";
    const reply = item.replyToInteractionId ? ` replyTo=${item.replyToInteractionId}` : "";
    return `${index + 1}. ${speaker} [id=${item.interactionId}${reply}]: ${safeText(item.content, 1200)}`;
  }).join("\n");
}

function buildOfficialGuiPlusInteractionMessages(history) {
  return normalizeGuiPlusInteractionHistory(history, 16).map((item) => ({
    role: item.role,
    content: [{
      type: "text",
      text: item.content,
    }],
  }));
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
    explorationSprawlCount: Math.max(0, Number(raw.explorationSprawlCount || 0) || 0),
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
  const taskContract = visualTaskContractFromRequest(
    body,
    memory,
    safeText(body?.agentGoal || body?.goal || body?.message || body?.prompt || "", 240)
  );
  const taskMemory = visualTaskMemoryFromRequest(
    body,
    memory,
    taskContract,
    safeText(body?.agentGoal || body?.goal || body?.message || body?.prompt || "", 240)
  );

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
  const explorationSprawlCount = Math.max(0, Number(bodyFeedback.explorationSprawlCount ?? memoryFeedback.explorationSprawlCount ?? rawLoopSignals.explorationSprawlCount ?? 0) || 0);
  const noProgressCount = Math.max(0, Number(bodyFeedback.noProgressCount ?? memoryFeedback.noProgressCount ?? rawLoopSignals.noProgressCount ?? 0) || 0);
  const sameActionCount = Math.max(0, Number(bodyFeedback.sameActionCount ?? memoryFeedback.sameActionCount ?? rawLoopSignals.sameActionCount ?? 0) || 0);
  const explorationPressure = computeVisualExplorationPressure({
    ...memory,
    interactionTurnCount: memory.interactionTurnCount,
    interactionHistory: memory.interactionHistory,
    loopSignals: {
      ...rawLoopSignals,
      executedStepCount: Math.max(0, Number(rawLoopSignals.executedStepCount || 0) || 0),
      loopIndex: Math.max(0, Number(rawLoopSignals.loopIndex || 0) || 0),
      explorationSprawlCount,
      noProgressCount,
      sameActionCount,
    },
    executionFeedback: {
      ...memoryFeedback,
      ...bodyFeedback,
      explorationSprawlCount,
      noProgressCount,
      sameActionCount,
    },
  });
  visualReplanRequested = Boolean(
    visualReplanRequested ||
      taskMemory?.replanRequested === true ||
      (taskMemory && taskMemory.remainingExplorationBudget <= 0) ||
      explorationSprawlCount > 0 ||
      noProgressCount > 0 ||
      explorationPressure.budgetExceeded === true ||
      lastResultOk === false
  );

  const executionFeedback = {
    ...memoryFeedback,
    ...bodyFeedback,
    lastResultOk,
    lastVerification,
    explorationSprawlCount,
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
    explorationPressureLevel: explorationPressure.pressureLevel,
    explorationBudgetRemaining: taskMemory
      ? taskMemory.remainingExplorationBudget
      : explorationPressure.budgetRemaining,
    explorationBudgetExceeded: taskMemory
      ? taskMemory.remainingExplorationBudget <= 0
      : explorationPressure.budgetExceeded,
    currentMilestoneId: safeText(
      bodyFeedback.currentMilestoneId ||
        memoryFeedback.currentMilestoneId ||
        taskMemory?.currentMilestoneId ||
        taskContract?.currentMilestoneId ||
        "",
      80
    ),
    completedMilestoneIds: taskMemory?.completedMilestoneIds || taskContract?.completedMilestoneIds || [],
    failedHypotheses: taskMemory?.failedHypotheses || [],
    blockedActions: taskMemory?.blockedActions || [],
    lastConfirmedPage: taskMemory?.lastConfirmedPage || null,
    progressStatus: safeText(taskMemory?.progressStatus || bodyFeedback.progressStatus || memoryFeedback.progressStatus || "", 40),
    replanRequested: Boolean(taskMemory?.replanRequested || visualReplanRequested),
    recoveryMode: Boolean(taskMemory?.recoveryMode),
    legacyMode: Boolean(taskMemory?.legacyMode),
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
    explorationSprawlCount,
    sameActionCount,
    lastResultOk,
    lastVerification,
    lastActionSignature,
    finishVerificationRequested,
    visualReplanRequested,
    guiPlusReplanRequested: visualReplanRequested,
    routeRefreshRequested,
    explorationPressureLevel: explorationPressure.pressureLevel,
    explorationBudgetRemaining: explorationPressure.budgetRemaining,
    explorationBudgetExceeded: explorationPressure.budgetExceeded,
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
    taskContract,
    taskMemory,
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
  const explorationPressure = computeVisualExplorationPressure(memory);
  const { taskMemory } = visualTaskStateFromMemory(memory, "");
  return Boolean(
    taskMemory?.replanRequested === true ||
      (taskMemory && taskMemory.remainingExplorationBudget <= 0) ||
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
      loopSignals.lastResultOk === false ||
      explorationPressure.budgetExceeded === true
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
        explorationSprawlCount: Math.max(0, Number(memory.executionFeedback.explorationSprawlCount || 0) || 0),
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
  const { taskContract, taskMemory } = visualTaskStateFromMemory(memory, "");
  const blockedActionSignatures = runtimeBlockedActionSignatures(memory);
  const finishVerificationRequested = agentMemoryRequestsFinishVerification(memory);
  const visualReplanRequested = agentMemoryRequestsGuiPlusReplan(memory);
  const routeRefreshRequested = agentMemoryRequestsRouteRefresh(memory);
  const explorationPressure = computeVisualExplorationPressure(memory);

  return {
    schema: memory.schema || "agent_loop_memory",
    recentActions: Array.isArray(memory.recentActions) ? memory.recentActions.slice(-8) : Array.isArray(recentActions) ? recentActions.slice(-8) : [],
    failedActions: Array.isArray(memory.failedActions) ? memory.failedActions.slice(-8) : [],
    blockedActions: Array.isArray(memory.blockedActions) ? memory.blockedActions.slice(-8) : [],
    verificationEvents: Array.isArray(memory.verificationEvents) ? memory.verificationEvents.slice(-8) : [],
    blockedActionSignatures,
    pendingVerification: pending,
    executionFeedback,
    lastToolResponse,
    finishVerificationRequested,
    visualReplanRequested,
    guiPlusReplanRequested: visualReplanRequested,
    routeRefreshRequested,
    decisionOwner: safeText(memory.decisionOwner || memory.visualDecisionOwner || "", 80),
    exclusiveVisualSession: isExclusiveGuiPlusVisualMemory(memory),
    loopSignals: memory.loopSignals && typeof memory.loopSignals === "object" ? memory.loopSignals : {},
    explorationPressure,
    taskContract,
    taskMemory,
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
      requiresConfirmation: Boolean(item.requiresConfirmation || item.confirm || riskRequiresConfirmation(stepRisk)),
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
  const { taskContract, taskMemory } = visualTaskStateFromMemory(memory, "");
  const finishVerificationRequested = agentMemoryRequestsFinishVerification(memory);
  const routeRefreshRequested = agentMemoryRequestsRouteRefresh(memory);
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
    finishVerificationRequested,
    routeRefreshRequested,
    taskContract,
    taskMemory,
    loopSignals: {
      executedStepCount: Number(loopSignals.executedStepCount || 0),
      loopIndex: Number(loopSignals.loopIndex || 0),
      noProgressCount: Math.max(0, Number(loopSignals.noProgressCount || feedback.noProgressCount || 0) || 0),
      sameActionCount: Math.max(0, Number(loopSignals.sameActionCount || feedback.sameActionCount || 0) || 0),
      lastVerification: safeText(loopSignals.lastVerification || feedback.lastVerification || "unknown", 80),
      finishVerificationRequested,
      routeRefreshRequested,
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
  const protocol = safeText(body?.agentSessionProtocol, 120);
  return Boolean(
    isVisualAgentStepRequest(body) &&
      ANDROID_CLOUD_ROUTE_VISUAL_PROTOCOL_ALIASES.has(protocol)
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

function agentBrainRouteCacheKey(goal, snapshot, deviceContext, agentMemory = null, verifiedSurfaceOverride = null) {
  const device = compactAgentBrainDeviceForRoute(goal, snapshot, deviceContext);
  const screen = agentBrainRouteScreenForPrompt(snapshot);
  const verifiedSurface = verifiedSurfaceOverride || verifiedVisualSurfaceProtocol(null, snapshot, deviceContext, agentMemory);
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

function buildCompactAgentBrainRouteRetryMessages(goal, snapshot, recentActions, deviceContext, agentMemory, verifiedSurfaceOverride = null) {
  const verifiedSurface = verifiedSurfaceOverride || verifiedVisualSurfaceProtocol(null, snapshot, deviceContext, agentMemory);
  const installedApps = agentBrainRouteAppCandidates(goal, snapshot, deviceContext, 160)
    .map((app) => [app.label, app.packageName]);
  const payload = {
    goal: safeText(goal, 240),
    currentPackage: safeText(snapshot?.packageName || snapshot?.currentApp || "", 120),
    installedApps,
    verifiedSurface: {
      surfaceState: verifiedSurface.surfaceState,
      selectedTargetPackage: verifiedSurface.selectedTargetPackage,
      verifiedTargetPackage: verifiedSurface.verifiedTargetPackage,
      currentPackage: verifiedSurface.currentPackage,
      guiPlusEligible: verifiedSurface.guiPlusEligible,
    },
    recentResults: (Array.isArray(recentActions) ? recentActions : [])
      .slice(-6)
      .map((item) => safeText(item, 220))
      .filter(Boolean),
  };
  return [
    {
      role: "system",
      content: [
        "你是 Android 智能体路由主脑。上一次调用超时或没有完整正文。",
        "不要输出思考过程、解释或 Markdown；立即返回一个完整且可解析的 JSON 对象。",
        "只根据原始目标、当前包名、已安装应用目录和 verifiedSurface 返回一个紧凑 JSON。",
        "若当前包名等于已选目标包名但尚未完成绑定，继续返回同一个 open_app，Android 会在前台完成确定性交接。",
        `仅返回：{"route":"device_tool|visual_agent|hybrid|ask_user|refuse","tool":"${[...INTERNAL_TOOL_AGENT_STEP_TYPES, "visual_agent"].join("|")}","appName":"","packageName":"","args":{},"risk":"low|medium|high|critical","requiresConfirmation":false,"reason":"","question":""}`,
      ].join("\n"),
    },
    { role: "user", content: JSON.stringify(payload) },
  ];
}

function buildAgentBrainRouteMessages(goal, snapshot, recentActions, deviceContext, agentMemory, verifiedSurfaceOverride = null) {
  const verifiedSurface = verifiedSurfaceOverride || verifiedVisualSurfaceProtocol(null, snapshot, deviceContext, agentMemory);
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
    "This call only selects the executor and app; GUI Plus performs and confirms consequential actions.",
    "高风险内部工具必须标注 risk 和 requiresConfirmation；不允许的任务 route=refuse。",
    `仅返回一个紧凑 JSON：{"route":"device_tool|visual_agent|hybrid|ask_user|refuse","tool":"${[...INTERNAL_TOOL_AGENT_STEP_TYPES, "visual_agent"].join("|")}","appName":"","packageName":"","args":{},"risk":"low|medium|high|critical","requiresConfirmation":false,"reason":"","question":""}`,
  ].join("\n");

  return [
    { role: "system", content: system },
    { role: "user", content: JSON.stringify(payload) },
  ];
}

async function handleAgentBrainRouteRequest(body, prompt, resolvedModel) {
  const requestContext = buildVisualAgentRequestContext(body, prompt);
  body = requestContext.request;
  const {
    goal,
    snapshot,
    deviceContext,
    agentMemory,
    verifiedSurface,
  } = requestContext;
  const recentActions = requestContext.recentAgentActions;

  if (!goal) {
    return { ok: false, error: "empty_agent_goal", code: "empty_agent_goal", version: WORKER_VERSION };
  }

  let raw = "";
  let parsed = {};
  let route = null;
  let errorText = "";
  const startedAt = Date.now();
  const cacheKey = agentBrainRouteCacheKey(goal, snapshot, deviceContext, agentMemory, verifiedSurface);
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
      buildAgentBrainRouteMessages(goal, snapshot, recentActions, deviceContext, agentMemory, verifiedSurface),
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
  const verifiedSurface = options?.verifiedSurface ||
    verifiedVisualSurfaceProtocol(null, snapshot, deviceContext, agentMemory);
  const useCache = options?.useCache !== false;
  const cacheKey = useCache
    ? agentBrainRouteCacheKey(cleanGoal, snapshot, deviceContext, agentMemory, verifiedSurface)
    : "";
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
    const routeMessages = buildAgentBrainRouteMessages(cleanGoal, snapshot, recentActions, deviceContext, agentMemory, verifiedSurface);
    let raw = "";
    try {
      const firstAttemptTimeoutMs = Math.min(timeoutMs, AGENT_BRAIN_ROUTE_FIRST_ATTEMPT_TIMEOUT_MS);
      raw = await callOpenAICompatible(
        process.env.DEEPSEEK_BASE_URL,
        process.env.DEEPSEEK_API_KEY,
        process.env.DEEPSEEK_MODEL,
        routeMessages,
        "DeepSeek AgentBrain Route Step",
        {
          temperature: 0,
          max_tokens: AGENT_BRAIN_ROUTE_MAX_TOKENS,
          timeoutMs: firstAttemptTimeoutMs,
          response_format: { type: "json_object" },
        }
      );
    } catch (error) {
      const firstError = String(error?.message || error || "");
      const retryableProviderFailure = /(?:empty\b|provider_body_timeout|provider_stream_(?:header|idle|absolute)_timeout|timed?\s*out|timeout)/i.test(firstError);
      if (!retryableProviderFailure) throw error;
      const remainingMs = boundedAgentTimeoutMs(
        AGENT_BRAIN_ROUTE_TIMEOUT_MS,
        agentRemainingBudgetMs(startedAt),
        AGENT_BRAIN_ROUTE_TIMEOUT_MS
      );
      if (remainingMs < 1200) throw error;
      raw = await callOpenAICompatible(
        process.env.DEEPSEEK_BASE_URL,
        process.env.DEEPSEEK_API_KEY,
        process.env.DEEPSEEK_MODEL,
        buildCompactAgentBrainRouteRetryMessages(cleanGoal, snapshot, recentActions, deviceContext, agentMemory, verifiedSurface),
        "DeepSeek AgentBrain Route Step RetryCompact",
        {
          temperature: 0,
          max_tokens: AGENT_BRAIN_ROUTE_RETRY_MAX_TOKENS,
          timeoutMs: remainingMs,
          response_format: { type: "json_object" },
        }
      );
    }
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

function agentBrainRouteStepToAgentStepPayload(route, routeStep, tool, goal) {
  const args = agentBrainStepArgs(routeStep);
  const risk = normalizeAgentBrainRisk(routeStep?.risk || route?.risk);
  const riskLevel = riskRequiresConfirmation(risk) ? "high" : risk === "medium" ? "medium" : "low";
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
    requiresConfirmation: Boolean(routeStep?.requiresConfirmation || routeStep?.confirm || riskRequiresConfirmation(risk)),
    args,
    arguments: args,
  };
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
const INTERNAL_SYSTEM_SETTINGS_PAGE_ALIASES = Object.freeze({
  wi_fi: "wifi", wifi_settings: "wifi", wireless: "wifi",
  battery_settings: "battery", battery_optimization: "battery", power: "battery", power_saving: "battery",
  developer_options: "developer", dev_options: "developer", developer_setting: "developer", development: "developer", application_development: "developer",
  app: "apps", application: "apps", applications: "apps", app_management: "apps",
  mobile_data: "data", data_usage: "data", network_data: "data",
  notifications: "notification", do_not_disturb: "dnd", zen: "dnd", zen_mode: "dnd", dnd_settings: "dnd",
  accessibility_settings: "accessibility", storage_settings: "storage", display_settings: "display", sound_settings: "sound", location_settings: "location",
});
const INTERNAL_SYSTEM_SETTINGS_ALLOWED_PAGES = new Set([
  "system", "wifi", "bluetooth", "battery", "display", "notification", "accessibility", "apps", "storage", "sound", "location", "data", "developer", "dnd",
]);
const INTERNAL_APP_SETTINGS_PAGE_ALIASES = Object.freeze({
  info: "details", app_info: "details", application_info: "details", notifications: "notification",
  permission: "permission", permissions: "permission", battery_optimization: "battery", background: "battery", background_restriction: "battery",
});
const INTERNAL_APP_SETTINGS_ALLOWED_PAGES = new Set(["details", "notification", "permission", "battery"]);
const LEDGER_CATEGORY_ALIASES = Object.freeze({
  food: "餐饮", meal: "餐饮", dining: "餐饮", 餐饮: "餐饮",
  transport: "交通", transportation: "交通", 交通: "交通",
  shopping: "购物", 购物: "购物", housing: "居住", home: "居住", 居住: "居住",
  drink: "饮品", beverage: "饮品", 饮品: "饮品", salary: "工资", wage: "工资", 工资: "工资",
  gift: "礼物", 礼物: "礼物", other: "其他", others: "其他", 其他: "其他",
});
const LEDGER_RANGE_ALIASES = Object.freeze({
  current_month: "current_month", this_month: "current_month", month: "current_month", 本月: "current_month",
  last_month: "last_month", previous_month: "last_month", 上月: "last_month",
  last_30_days: "last_30_days", recent_30_days: "last_30_days", "30_days": "last_30_days", 最近30天: "last_30_days",
  current_year: "current_year", this_year: "current_year", year: "current_year", 本年: "current_year", 今年: "current_year",
  all: "all", all_time: "all", 全部: "all",
});

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
    const canonical = INTERNAL_SYSTEM_SETTINGS_PAGE_ALIASES[rawPage] || rawPage;
    if (INTERNAL_SYSTEM_SETTINGS_ALLOWED_PAGES.has(canonical)) {
      out.page = canonical;
      out.kind = canonical;
    }
  }

  if (tool === "open_app_settings") {
    const rawPage = normalizeText(out.page || out.kind || out.target);
    const canonical = INTERNAL_APP_SETTINGS_PAGE_ALIASES[rawPage] || rawPage;
    if (INTERNAL_APP_SETTINGS_ALLOWED_PAGES.has(canonical)) {
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
    return LEDGER_CATEGORY_ALIASES[category] || "";
  };
  const normalizeLedgerRange = (value) => {
    const range = normalizeText(value);
    return LEDGER_RANGE_ALIASES[range] || "current_month";
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
      requiresConfirmation: Boolean(plan.requiresConfirmation || riskRequiresConfirmation(risk)),
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
        requiresConfirmation: Boolean(decision.requiresConfirmation || riskRequiresConfirmation(finalRisk)),
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

    if (!supportedSteps.includes(decision.tool) || !SUPPORTED_AGENT_STEP_TYPE_SET.has(decision.tool)) {
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

// ===== AI Ledger source module: 50-gui-plus-runtime.js =====
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
      type: SUPPORTED_AGENT_STEP_TYPE_SET.has(actionType) ? actionType : "",
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

function buildAliyunMobileUseToolProtocolPrompt() {
  const toolSchema = {
    type: "function",
    function: {
      name_for_human: "mobile_use",
      name: "mobile_use",
      description: [
        "Use a touchscreen to interact with a mobile device, and take screenshots.",
        "The screen coordinate system is 1000x1000.",
        "Return exactly one grounded action for the current screenshot.",
        "Every action should carry a structured action intent so Android can verify semantic progress instead of treating every screen change as progress.",
      ].join("\n"),
      parameters: {
        type: "object",
        properties: {
          action: {
            type: "string",
            enum: ["click", "swipe", "type", "system_button", "open", "wait", "answer", "interact", "terminate"],
            description: "The single action to perform.",
          },
          coordinate: {
            type: "array",
            description: "(x, y) in the 1000x1000 screen coordinate system. Used by click and swipe.",
          },
          coordinate2: {
            type: "array",
            description: "Swipe end coordinate in the 1000x1000 screen coordinate system.",
          },
          text: {
            type: "string",
            description: "Text for type, open, answer or interact; may also briefly name the visible target.",
          },
          time: { type: "number", description: "Seconds to wait." },
          button: { type: "string", enum: ["Back", "Home", "Menu", "Enter"], description: "System button." },
          status: { type: "string", enum: ["success", "failure"], description: "Task status for terminate." },
          riskLevel: {
            type: "string",
            enum: ["low", "medium", "high", "critical", "financial_transaction", "purchase", "consequential", "irreversible"],
            description: "Semantic risk for this exact action.",
          },
          requiresConfirmation: {
            type: "boolean",
            description: "True only when Android must obtain explicit user confirmation before executing this exact action.",
          },
          purpose: {
            type: "string",
            description: "Why this action advances the current milestone. Required for all new-protocol actions.",
          },
          milestoneId: {
            type: "string",
            description: "Stable identifier of the current subgoal/milestone.",
          },
          expectedEvidence: {
            type: "array",
            items: { type: "string" },
            description: "Concrete text, visual state, focus state or package evidence expected after success.",
          },
          failureEvidence: {
            type: "array",
            items: { type: "string" },
            description: "Concrete evidence that would prove this hypothesis or route failed.",
          },
          exploratory: {
            type: "boolean",
            description: "True only when the action tests an uncertain hypothesis rather than following confirmed evidence.",
          },
          reversible: {
            type: "boolean",
            description: "Whether the action can be safely undone without creating an external effect.",
          },
          confidence: {
            type: "number",
            minimum: 0,
            maximum: 1,
            description: "Confidence that the action serves the stated purpose on the current screenshot.",
          },
          hypothesisId: {
            type: "string",
            description: "Stable hypothesis identifier. Never reuse a failed hypothesis on the same page and milestone.",
          },
          taskContract: {
            type: "object",
            description: "Optional structured task contract. Supply or update it when the current milestone becomes clear.",
            properties: {
              schema: { type: "string" },
              originalGoal: { type: "string" },
              currentMilestoneId: { type: "string" },
              completedMilestoneIds: { type: "array", items: { type: "string" } },
              explorationBudgetPerMilestone: { type: "integer", minimum: 1, maximum: 4 },
              milestones: {
                type: "array",
                items: {
                  type: "object",
                  properties: {
                    id: { type: "string" },
                    title: { type: "string" },
                    successEvidence: { type: "array", items: { type: "string" } },
                    failureEvidence: { type: "array", items: { type: "string" } },
                    completed: { type: "boolean" },
                  },
                  required: ["id"],
                },
              },
            },
          },
        },
        required: ["action"],
      },
      args_format: "Format the arguments as a JSON object.",
    },
  };
  return [
    "# Tools",
    "You may call one function for the current screen.",
    "",
    "You are provided with function signatures within <tools></tools> XML tags:",
    "<tools>",
    JSON.stringify(toolSchema),
    "</tools>",
    "",
    "For the function call, return a JSON object with function name and arguments within <tool_call></tool_call> XML tags:",
    "<tool_call>",
    '{"name":"mobile_use","arguments":{...}}',
    "</tool_call>",
    "",
    "# Response format",
    "1) Action: one short imperative.",
    "2) One <tool_call> block.",
    "",
    "Rules:",
    "- Output exactly one mobile_use action.",
    "- Do not output prose outside Action and the tool_call.",
    "- For new-protocol actions include purpose, milestoneId, expectedEvidence, failureEvidence, exploratory, reversible, confidence and hypothesisId.",
    "- A changed screen is not progress by itself; expectedEvidence must describe what would prove real progress.",
    "- Exploratory swipe requires a concrete search purpose and expectedEvidence.",
    "- Wait requires a concrete loading/processing evidence target.",
    "- Back requires a recovery purpose.",
    "- If finishing, use action=terminate with status=success only when the current screenshot contains completion evidence.",
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
  const explorationPressure = computeVisualExplorationPressure(agentMemory);
  const taskContractContext = visualTaskContractPromptBlock(agentMemory, goal);
  const currentPackage = safeText(
    snapshot?.packageName || snapshot?.currentApp || deviceContext?.currentApp?.packageName || "",
    120
  );
  const explorationSprawlCount = Math.max(0, Number(
    agentMemory?.loopSignals?.explorationSprawlCount ||
    agentMemory?.executionFeedback?.explorationSprawlCount ||
    0
  ) || 0);
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
    explorationSprawlCount > 0
      ? `Route contraction mode is active because Android reported explorationSprawlCount=${explorationSprawlCount}. Treat recent page changes as possible drift, not proof of progress. Your next action must reduce uncertainty from the current screen, or take a reversible corrective step, instead of opening another broad branch.`
      : "Normal route mode: each next action should still be the smallest grounded step that reduces uncertainty from the current screen.",
    explorationPressure.pressureLevel !== "low"
      ? `Exploration pressure is ${explorationPressure.pressureLevel}. executedStepCount=${explorationPressure.executedStepCount}, budgetRemaining=${explorationPressure.budgetRemaining}. Prefer corrective, reversible, or ambiguity-reducing actions. Do not keep consuming global exploration budget unless the screenshot gives strong visible evidence.`
      : "Exploration pressure is low; continue with the smallest grounded step.",
    "If the instruction names an app and that app appears uniquely in the canonical installed-app list, do not ask which app to use and do not tell the user to open it. If it is not foreground, output action=open with the exact canonical label; if it is foreground, continue inside it.",
    "For external-app tasks while assistantHost=true, the normal recovery action is action=open for the intended canonical app. Do not answer that you cannot directly operate the app, and do not delegate ordinary opening, searching, navigation, selection, or clicking steps to the user.",
    "Use contextual visual judgment for user involvement; never classify risk by keyword matching. Continue ordinary preparation, use interact immediately before a consequential external effect, and ask users to complete private input directly in the target app.",
    "Risk protocol: ordinary reversible navigation, search, typing and form preparation must declare riskLevel=low and requiresConfirmation=false. The exact mobile_use action that submits an order, trade, purchase, payment, message, publication, deletion or another consequential external effect must declare the appropriate structured riskLevel and requiresConfirmation=true. This declaration is semantic model output, not backend keyword inference.",
    "For financial or purchase submission, first use interact to show the essential transaction details and obtain the user's intent to proceed. On the fresh screenshot after that reply, return the exact submission action with riskLevel=financial_transaction or purchase and requiresConfirmation=true; Android performs the non-bypassable final confirmation.",
    "The GUI Plus/User interaction history below is authoritative dialogue context. Treat the latest user reply as a direct continuation of your latest interact request, preserve all stated constraints, and continue the same visual session. You may ask another interact question when further clarification is genuinely necessary.",
    "When the interaction history says the user completed a sensitive step in the target app, do not request the secret itself. Inspect the fresh screenshot and continue or ask the user to retry only if the screen still requires private input.",
    "Do not use action=interact merely because the user left subjective preferences unspecified. For requests such as '挑一个合适的', make a reasonable visible choice yourself from the available products or options and continue.",
    "Use action=interact only when contextual visual reasoning finds that the next step requires user participation or a required choice cannot be inferred. Do not classify actions by matching words in labels, goals or page text.",
    "Use action=answer only when the requested visual task has been completed and you are returning the final result to the user. The backend will treat answer as a completion candidate and Android will verify it on a fresh screenshot.",
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
    taskContractContext,
    "",
    "Action-intent protocol: every new-protocol action must include purpose, milestoneId, expectedEvidence, failureEvidence, exploratory, reversible, confidence and hypothesisId. Use exploratory=true only for a bounded hypothesis test. Never repeat a failed hypothesis or blocked action cluster on the same page and milestone.",
    "Exploratory swipe must name what it is searching for in purpose and expectedEvidence. Wait must name the loading evidence. Back must name the recovery purpose. If evidence is ambiguous, prefer one re-observation-compatible action rather than a new random branch.",
    "",
    `Canonical installed apps for action=open: ${canonicalApps}`,
    "",
    "Output one mobile_use action only. After Android executes it, a fresh screenshot and tool_response will be supplied for the next cloud decision."
  ].join("\n").slice(0, 12000);
}

function buildPureOfficialGuiPlusInstruction(goal, recentActions = [], deviceContext = null, agentBrainRoute = null, agentMemory = null) {
  const exclusiveGuiPlusVisualSession = isExclusiveGuiPlusVisualMemory(agentMemory);
  const previousActions = (Array.isArray(recentActions) ? recentActions : [])
    .slice(-12)
    .map((item, index) => `Step ${index + 1}: ${safeText(item, 240)}`)
    .filter(Boolean)
    .join("\n") || "None";
  const routeContext = agentBrainRoute && typeof agentBrainRoute === "object"
    ? agentBrainRoutePromptBlock(agentBrainRoute)
    : "DeepSeek AgentBrain route: visual_agent.";
  const feedback = runtimeVerificationHintForPrompt(agentMemory);
  const thinking = structuralGuiThinkingDecision(agentMemory);
  const explorationPressure = computeVisualExplorationPressure(agentMemory);
  const taskContractContext = visualTaskContractPromptBlock(agentMemory, goal);
  const explorationSprawlCount = Math.max(0, Number(
    agentMemory?.loopSignals?.explorationSprawlCount ||
    agentMemory?.executionFeedback?.explorationSprawlCount ||
    0
  ) || 0);
  const currentPackage = safeText(
    agentMemory?.surfaceContext?.currentPackage ||
      deviceContext?.currentApp?.packageName ||
      "",
    120
  );
  const selectedTargetPackage = safeText(
    agentMemory?.surfaceContext?.selectedTargetPackage || agentMemory?.selectedTargetPackage || "",
    120
  );
  const verifiedTargetPackage = safeText(
    agentMemory?.surfaceContext?.verifiedTargetPackage || agentMemory?.verifiedTargetPackage || "",
    120
  );
  const workSurfaceAppContext = exclusiveGuiPlusVisualSession
    ? [
        currentPackage ? `current_foreground_package=${currentPackage}` : "",
        selectedTargetPackage ? `selected_target_package=${selectedTargetPackage}` : "",
        verifiedTargetPackage ? `verified_target_package=${verifiedTargetPackage}` : "",
      ].filter(Boolean).join("; ") || "verified work surface: current package not provided"
    : `Installed applications available to mobile_use action=open: ${formatCanonicalVisualAppPairs(deviceContext, goal, 24)}.`;
  return [
    "Return exactly one official mobile_use tool call, with no prose.",
    "Preserve the original goal and latest reply. Use the screenshot, action history and Android result; after no progress choose a different grounded route.",
    "Use contextual judgment, not keyword matching. Navigate autonomously; use interact only for genuinely required user participation or immediately before a consequential effect.",
    "Declare riskLevel=low and requiresConfirmation=false for reversible preparation. For the exact action that creates an external effect, declare the semantic riskLevel and requiresConfirmation=true. For financial or purchase submission, interact first with the essential details, then after the user's reply mark the exact submit action financial_transaction or purchase so Android can enforce final confirmation.",
    "Before choosing the next action, briefly self-check internally whether the current screen is still serving the original goal; if not, pick a grounded corrective action instead of continuing along the current branch.",
    "Prefer the smallest action that tests or narrows the current hypothesis from the visible screen. Avoid broad exploration that opens a new branch unless the screenshot gives concrete evidence that the branch is necessary for the original goal.",
    "A changed screen is not automatically progress. If recent steps kept changing pages without clearly reducing uncertainty, first re-anchor on the current screen and choose a corrective, reversible, or locally disambiguating action instead of expanding exploration again.",
    explorationSprawlCount > 0
      ? `Route contraction mode is active (explorationSprawlCount=${explorationSprawlCount}). Do not expand to another global entry, menu, or branch unless the current screenshot itself makes that branch clearly necessary. Prefer a reversible corrective step or an action that narrows ambiguity on the current screen.`
      : "When the current screen offers several plausible branches, prefer the branch with the strongest visible evidence for the original goal.",
    explorationPressure.pressureLevel !== "low"
      ? `Exploration pressure=${explorationPressure.pressureLevel}; executedStepCount=${explorationPressure.executedStepCount}; budgetRemaining=${explorationPressure.budgetRemaining}. If unsure, prefer a corrective or locally disambiguating action over opening another broad branch.`
      : "Exploration budget is healthy; still prefer the most grounded next step.",
    thinking.enabled
      ? `Deep thinking (${thinking.reasons.join(", ") || "configured"}): re-observe the failure and choose a different grounded route without revealing analysis.`
      : "Thinking mode is fast; choose one visually grounded action.",
    `Instruction: ${aliyunGuiDateInfo()}${safeText(goal, 240)}`,
    exclusiveGuiPlusVisualSession
      ? `Verified work-surface app context: ${workSurfaceAppContext}. Stay on this task surface unless the screenshot itself proves that switching apps is required.`
      : workSurfaceAppContext,
    routeContext,
    "Previous actions:",
    previousActions,
    feedback ? `Latest Android tool response:\n${feedback}` : "Latest Android tool response: none",
    taskContractContext,
    "For every action include purpose, milestoneId, expectedEvidence, failureEvidence, exploratory, reversible, confidence and hypothesisId. Keep the same milestoneId while pursuing the same subgoal. Never reuse a failed hypothesis on the same page. When explorationBudgetRemaining is exhausted, do not emit another exploratory swipe/wait/back/click.",
  ].join("\n\n").slice(0, 9000);
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
      exploration_sprawl_count: normalized.explorationSprawlCount || Number(feedback.explorationSprawlCount || 0) || 0,
      finish_verification_requested: Boolean(normalized.finishVerificationRequested),
      current_milestone_id: safeText(feedback.currentMilestoneId || memory.taskMemory?.currentMilestoneId || "", 80),
      progress_status: safeText(feedback.progressStatus || memory.taskMemory?.progressStatus || "", 40),
      exploration_budget_remaining: Number.isFinite(Number(feedback.explorationBudgetRemaining ?? memory.taskMemory?.remainingExplorationBudget))
        ? Math.max(0, Number(feedback.explorationBudgetRemaining ?? memory.taskMemory?.remainingExplorationBudget))
        : null,
      failed_hypotheses: Array.isArray(feedback.failedHypotheses)
        ? feedback.failedHypotheses.slice(-6)
        : Array.isArray(memory.taskMemory?.failedHypotheses)
          ? memory.taskMemory.failedHypotheses.slice(-6)
          : [],
      blocked_actions: Array.isArray(feedback.blockedActions)
        ? feedback.blockedActions.slice(-6)
        : Array.isArray(memory.taskMemory?.blockedActions)
          ? memory.taskMemory.blockedActions.slice(-6)
          : [],
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

function officialGuiCurrentUserContent(screenshotInfo, toolResponseText) {
  const imagePart = currentGuiImagePart(screenshotInfo);
  const content = [];
  if (toolResponseText) {
    content.push({ type: "text", text: "<tool_response>\n" });
    content.push({ type: "text", text: String(toolResponseText) });
    content.push({ type: "text", text: "\n</tool_response>\n" });
  }
  content.push(imagePart);
  return content;
}

function officialGuiHistoricalToolResponseContent(toolResponseText) {
  if (!toolResponseText) return null;
  return [{
    type: "text",
    text: `<tool_response>\n${String(toolResponseText)}\n</tool_response>\n`,
  }];
}


function adaptiveAgentGuiHistoryLimit(agentMemory = null, interactionHistory = []) {
  const memory = agentMemory && typeof agentMemory === "object" ? agentMemory : {};
  const signals = memory.loopSignals && typeof memory.loopSignals === "object" ? memory.loopSignals : {};
  const feedback = memory.executionFeedback && typeof memory.executionFeedback === "object" ? memory.executionFeedback : {};
  const tool = memory.lastToolResponse && typeof memory.lastToolResponse === "object" ? memory.lastToolResponse : {};
  const noProgress = Math.max(0, Number(signals.noProgressCount || feedback.noProgressCount || 0) || 0);
  const structuralFailures = Math.max(0, Number(signals.structuralFailureCount || feedback.structuralFailureCount || 0) || 0);
  const localRetries = Math.max(0, Number(signals.localVisualRetryCount || feedback.localVisualRetryCount || 0) || 0);
  const explorationSprawl = Math.max(0, Number(signals.explorationSprawlCount || feedback.explorationSprawlCount || 0) || 0);
  const sameAction = Math.max(0, Number(signals.sameActionCount || feedback.sameActionCount || 0) || 0);
  const blocked = runtimeBlockedActionSignatures(memory);
  const actionFailed = feedback.lastResultOk === false || tool.success === false;
  const interactionActive = Array.isArray(interactionHistory) && interactionHistory.length > 0;
  const structuredRecoveryMode = memory.taskMemory?.recoveryMode === true || memory.taskMemory?.replanRequested === true;

  if (
    structuredRecoveryMode ||
    explorationSprawl > 0 ||
    noProgress >= 2 ||
    structuralFailures >= 2 ||
    localRetries >= 2 ||
    sameAction >= 2 ||
    blocked.length > 0
  ) {
    return AGENT_GUI_HISTORY_N;
  }
  if (noProgress > 0 || structuralFailures > 0 || localRetries > 0 || actionFailed || interactionActive) {
    return AGENT_GUI_RECOVERY_HISTORY_N;
  }
  return AGENT_GUI_FAST_HISTORY_N;
}

function buildAliyunGuiPlusMessages(goal, snapshot, screenshotInfo, recentActions = [], supportedSteps = SUPPORTED_AGENT_STEP_TYPES, deviceContext = null, agentMemory = null, session = null, routeState = null) {
  const interactionHistory = syncAgentGuiInteractionHistory(
    session,
    resolveGuiPlusInteractionHistory(null, agentMemory, recentActions, session)
  );
  const effectiveAgentMemory = {
    ...(agentMemory && typeof agentMemory === "object" ? agentMemory : {}),
    interactionProtocol: "gui_plus_dialogue_v2_bound_turns",
    interactionHistory,
    interactionTurnCount: interactionHistory.length,
    answeredInteractionIds: [...guiPlusAnsweredInteractionIds(interactionHistory)],
  };
  const messages = [
    {
      role: "system",
      content: buildLeanAliyunMobileUseToolProtocolPrompt(supportedSteps),
    },
  ];
  const historyLimit = adaptiveAgentGuiHistoryLimit(effectiveAgentMemory, interactionHistory);
  const history = Array.isArray(session?.guiHistory)
    ? session.guiHistory.slice(-historyLimit).filter((item) => item?.imageBase64 && item?.output)
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
  const interactionMessages = buildOfficialGuiPlusInteractionMessages(interactionHistory);

  if (history.length > 0) {
    const latestToolResponse = syncLatestAgentGuiToolResponse(session, effectiveAgentMemory)
      || formatOfficialGuiToolResponse(effectiveAgentMemory, history[history.length - 1]);
    const refreshedHistory = Array.isArray(session?.guiHistory)
      ? session.guiHistory.slice(-historyLimit).filter((item) => item?.imageBase64 && item?.output)
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
      const historicalToolResponse = officialGuiHistoricalToolResponseContent(item.toolResponse || "");
      if (historicalToolResponse) {
        messages.push({
          role: "user",
          content: historicalToolResponse,
        });
      }
    });
    messages.push(...interactionMessages);
    messages.push({
      role: "user",
      content: officialGuiCurrentUserContent(screenshotInfo, latestToolResponse),
    });
  } else if (interactionMessages.length > 0) {
    // A missing image-history frame must never erase the GUI Plus/User dialogue. Send the task,
    // replay the dialogue as real assistant/user turns, then attach the latest screen as the final
    // user observation so the model can continue from the user's answer instead of asking again.
    messages.push({
      role: "user",
      content: [{ type: "text", text: instructionPrompt }],
    });
    messages.push(...interactionMessages);
    messages.push({
      role: "user",
      content: officialGuiCurrentUserContent(screenshotInfo, ""),
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

function applyGuiPlusDeclaredRisk(compact, rawOutput) {
  const value = compact && typeof compact === "object" ? compact : {};
  const args = extractAliyunMobileUseToolCall(String(rawOutput || "")) || {};
  const riskLevel = normalizeRiskLevel(
    args.riskLevel || args.risk || value.r || value.risk || value.riskLevel || "low"
  );
  const requiresConfirmation = Boolean(
    args.requiresConfirmation || args.requireConfirmation ||
      value.q || value.confirm || value.requiresConfirmation
  ) || riskRequiresConfirmation(riskLevel);
  return {
    ...value,
    r: riskLevel,
    q: requiresConfirmation,
  };
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
    if (button === "back") return { s: "p", a: "back", t: "返回", c: 0.76, e: "GUI Plus mobile_use system_button Back.", raw: rawText.slice(0, 240) };
    if (button === "home") return { s: "p", a: "home", t: "主页", c: 0.76, e: "GUI Plus mobile_use system_button Home.", raw: rawText.slice(0, 240) };
    if (button === "menu") return { s: "p", a: "recents", t: "多任务", c: 0.7, e: "GUI Plus mobile_use system_button Menu.", raw: rawText.slice(0, 240) };
    return guiPlusNeedUserHelp(text, `Android 当前不支持 system_button=${safeText(args.button || args.text || "", 24)}，已安全暂停。`, rawText);
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
    if (key.includes("back")) return { s: "p", a: "back", t: "返回", c: 0.72, e: "GUI Plus mobile_use key back.", raw: rawText.slice(0, 240) };
    if (key.includes("home")) return { s: "p", a: "home", t: "主页", c: 0.72, e: "GUI Plus mobile_use key home.", raw: rawText.slice(0, 240) };
    return guiPlusNeedUserHelp(text, `Android 当前不支持 mobile_use key=${safeText(args.text || "", 32)}，已安全暂停。`, rawText);
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
      interactionProtocol: "gui_plus_dialogue_v2_bound_turns",
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
    return guiPlusNeedUserHelp(
      text || goal,
      "Android 当前动作协议不支持 long_press，已安全暂停并等待重新规划。",
      rawText
    );
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
      max_tokens: Math.min(ALIYUN_GUI_MAX_TOKENS, AGENT_GUI_PROVIDER_MAX_TOKENS, 512),
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



function proposedGuiPlusQuestion(compact) {
  const value = compact && typeof compact === "object" ? compact : {};
  return safeText(value.e || value.reason || value.t || value.targetText || "", 1200);
}

function shouldSelfReviewGuiPlusInteraction(compact, snapshot = null, deviceContext = null, agentMemory = null, session = null) {
  const value = compact && typeof compact === "object" ? compact : {};
  const controllerHandoffActive = isControllerHandoffSurface(null, snapshot, deviceContext, agentMemory);
  const validControllerOpen = Boolean(
    value.a === "open_app" &&
      safeText(value.appName || "", 100) &&
      safeText(value.packageName || "", 140)
  );
  if (controllerHandoffActive && !validControllerOpen) return true;
  if (value.a !== "need_user_help") return false;
  const history = resolveGuiPlusInteractionHistory(null, agentMemory, [], session);
  return Boolean(
    value.interactionProtocol !== "gui_plus_dialogue_v2_bound_turns" ||
      isGuiPlusQuestionAlreadyAnswered(history, proposedGuiPlusQuestion(value))
  );
}

function buildGuiPlusInteractionSelfReviewPrompt(goal, snapshot, deviceContext, agentMemory, compact, session = null) {
  const currentPackage = safeText(
    snapshot?.packageName || snapshot?.currentApp || deviceContext?.currentApp?.packageName || "",
    120
  );
  const assistantHost = isAssistantHostAppPackage(currentPackage) || snapshotLooksLikeAssistantChat(snapshot);
  const controllerHandoffActive = isControllerHandoffSurface(null, snapshot, deviceContext, agentMemory);
  const interactionHistory = resolveGuiPlusInteractionHistory(null, agentMemory, [], session);
  const repeatedAnsweredQuestion = isGuiPlusQuestionAlreadyAnswered(
    interactionHistory,
    proposedGuiPlusQuestion(compact)
  );
  const explorationSprawlCount = Math.max(0, Number(
    agentMemory?.loopSignals?.explorationSprawlCount ||
    agentMemory?.executionFeedback?.explorationSprawlCount ||
    0
  ) || 0);
  const exclusiveGuiPlusVisualSession = isExclusiveGuiPlusVisualMemory(agentMemory);
  const selectedTargetPackage = safeText(
    agentMemory?.surfaceContext?.selectedTargetPackage || agentMemory?.selectedTargetPackage || "",
    120
  );
  const verifiedTargetPackage = safeText(
    agentMemory?.surfaceContext?.verifiedTargetPackage || agentMemory?.verifiedTargetPackage || "",
    120
  );
  const appContext = exclusiveGuiPlusVisualSession
    ? [
        currentPackage ? `current_foreground_package=${currentPackage}` : "",
        selectedTargetPackage ? `selected_target_package=${selectedTargetPackage}` : "",
        verifiedTargetPackage ? `verified_target_package=${verifiedTargetPackage}` : "",
      ].filter(Boolean).join("; ") || "verified work surface"
    : formatCanonicalVisualAppPairs(deviceContext, goal, 12);
  return [
    "<interaction_self_review>",
    "Your immediately previous response did not satisfy the official mobile_use tool-call protocol. Correct the protocol without changing the task.",
    "This is not a new user instruction. Keep the original task and current screenshot authoritative.",
    `Original instruction: ${safeText(goal, 240)}`,
    `Current foreground package: ${currentPackage || "unknown"}; assistantHost=${assistantHost}; controllerHandoffActive=${controllerHandoffActive}.`,
    explorationSprawlCount > 0
      ? `Route contraction mode remains active (explorationSprawlCount=${explorationSprawlCount}). When correcting the protocol, do not widen exploration; choose a reversible or ambiguity-reducing action grounded in the current screen.`
      : "Normal correction mode: keep the next action minimal and grounded in the current screen.",
    exclusiveGuiPlusVisualSession
      ? `Verified work-surface app context: ${appContext}.`
      : `Canonical installed apps: ${appContext}.`,
    `Previous proposed action: ${safeText(compact?.a || "unknown", 80)}; reason=${safeText(compact?.e || compact?.reason || compact?.t || "", 1200)}.`,
    controllerHandoffActive
      ? "Controller handoff is not a visible loading page. The AI Assistant screenshot is intentionally absent or irrelevant. Replace wait/click/type/swipe/Back/Home/interact/answer/terminate with action=open using the exact canonical target-app label."
      : "Review whether the previous interact is genuinely necessary.",
    `Existing GUI Plus/User dialogue: ${formatGuiPlusInteractionHistoryForPrompt(interactionHistory)}.`,
    repeatedAnsweredQuestion
      ? "The proposed question has already been answered by the user in a bound user turn. Do not ask it again. Consume the existing answer and continue with one executable action grounded in the latest screenshot."
      : "No exact answered-question replay was detected. If interaction is still necessary, ask only for genuinely missing information.",
    "You can directly use mobile_use open, click, type, swipe, Back, Home and wait.",
    "If the previous interact merely asked the user to manually open an installed app, search, navigate, select, click, or perform another ordinary UI step that mobile_use can do, replace it now with the single executable mobile_use action you should perform yourself.",
    "When assistantHost=true and the target app is present in the canonical installed-app list, use action=open with the exact canonical app label instead of claiming that you cannot operate the app.",
    "Keep action=interact only when contextual visual reasoning shows that information or user participation is genuinely necessary, including immediately before an action with a consequential external effect. Never decide this by keyword matching.",
    "If interact is still correct after review, repeat action=interact with a clear question that contains exactly what the user must provide or complete.",
    "Output exactly one mobile_use action in the required Action + <tool_call> format, and nothing else.",
    "</interaction_self_review>",
  ].join("\n");
}

function buildGuiPlusInteractionSelfReviewMessages(messages, raw, goal, snapshot, deviceContext, agentMemory, compact, session = null) {
  return [
    ...(Array.isArray(messages) ? messages : []),
    { role: "assistant", content: String(raw || "") },
    {
      role: "user",
      content: [{
        type: "text",
        text: buildGuiPlusInteractionSelfReviewPrompt(goal, snapshot, deviceContext, agentMemory, compact, session),
      }],
    },
  ];
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
  const deepThinking = structuralGuiThinkingDecision(agentMemory);
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
  let compact = applyGuiPlusDeclaredRisk(
    applyGuiPlusActionIntent(
      normalizeAliyunMobileUseRawToCompact(raw, screenshotInfo, goal, deviceContext),
      raw,
      goal,
      agentMemory
    ),
    raw
  );
  let interactionSelfReviewed = false;
  let interactionSelfReviewChangedAction = false;
  let interactionSelfReviewError = "";

  if (shouldSelfReviewGuiPlusInteraction(compact, snapshot, deviceContext, agentMemory, session)) {
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
          compact,
          session
        );
        const reviewedRaw = await callDashScopeNativeGuiPlus(
          ALIYUN_GUI_MODEL,
          reviewMessages,
          sessionId,
          reviewTimeoutMs,
          { enableThinking: deepThinking.enabled }
        );
        const reviewedCompact = applyGuiPlusDeclaredRisk(
          applyGuiPlusActionIntent(
            normalizeAliyunMobileUseRawToCompact(reviewedRaw, screenshotInfo, goal, deviceContext),
            reviewedRaw,
            goal,
            agentMemory
          ),
          reviewedRaw
        );
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
  if (!SUPPORTED_AGENT_STEP_TYPE_SET.has(type)) return false;
  if (type === "need_user_help") return false;
  return true;
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
  const requiresConfirmation = Boolean(raw.q || raw.confirm || raw.requiresConfirmation) || riskRequiresConfirmation(riskLevel);
  return {
    type,
    appName: type === "open_app" ? safeText(raw.appName || "", 80) || undefined : undefined,
    packageName: type === "open_app" ? safeText(raw.packageName || "", 120) || undefined : undefined,
    targetText: safeText(raw.t || raw.targetText || "", 100) || undefined,
    text: type === "input_text" ? safeText(raw.v || raw.text || "", 240) : undefined,
    inputMode: type === "input_text" ? "focused_direct" : undefined,
    requiresInputNode: type === "input_text" ? false : undefined,
    expectsFocusedInput: type === "input_text" ? true : undefined,
    useFocusedInput: type === "input_text" ? true : undefined,
    direction: type === "swipe" ? normalizeAgentDirection(raw.d || raw.direction || "up") || "up" : undefined,
    x: ["tap_xy", "swipe"].includes(type) && Number.isFinite(Number(raw.x)) ? clamp01(Number(raw.x)) : undefined,
    y: ["tap_xy", "swipe"].includes(type) && Number.isFinite(Number(raw.y)) ? clamp01(Number(raw.y)) : undefined,
    x2: type === "swipe" && Number.isFinite(Number(raw.x2)) ? clamp01(Number(raw.x2)) : undefined,
    y2: type === "swipe" && Number.isFinite(Number(raw.y2)) ? clamp01(Number(raw.y2)) : undefined,
    durationMs: type === "wait" ? Math.max(100, Math.min(60000, Number(raw.ms || 700))) : undefined,
    reason,
    riskLevel,
    requiresConfirmation,
    purpose: safeText(raw.purpose || raw.actionIntent?.purpose || "", 220) || undefined,
    milestoneId: safeText(raw.milestoneId || raw.actionIntent?.milestoneId || "", 80) || undefined,
    expectedEvidence: normalizeVisualEvidenceList(raw.expectedEvidence || raw.actionIntent?.expectedEvidence || [], 12, 180),
    failureEvidence: normalizeVisualEvidenceList(raw.failureEvidence || raw.actionIntent?.failureEvidence || [], 12, 180),
    exploratory: nullableBooleanFromValue(raw.exploratory ?? raw.actionIntent?.exploratory),
    reversible: nullableBooleanFromValue(raw.reversible ?? raw.actionIntent?.reversible),
    confidence: Number.isFinite(Number(raw.confidence ?? raw.actionIntent?.confidence))
      ? clamp01(Number(raw.confidence ?? raw.actionIntent?.confidence))
      : undefined,
    hypothesisId: safeText(raw.hypothesisId || raw.actionIntent?.hypothesisId || "", 100) || undefined,
    actionIntent: raw.actionIntent && typeof raw.actionIntent === "object" ? raw.actionIntent : undefined,
    taskContract: normalizeVisualTaskContract(raw.taskContract || raw.actionIntent?.taskContract || null, goal) || undefined,
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
    verifiedSurface,
  } = context;
  const exclusiveGuiPlusVisualSession = isExclusiveGuiPlusVisualMemory(agentMemory);
  const verifiedSurfaceAllowsGuiPlus = Boolean(
    verifiedSurface?.guiPlusEligible === true &&
      verifiedSurface?.packageMatches === true &&
      verifiedSurface?.observationMatchesExpected === true &&
      safeText(verifiedSurface?.observationId || "", 120)
  );

  if (!exclusiveGuiPlusVisualSession || !verifiedSurfaceAllowsGuiPlus) {
    return {
      ok: false,
      error: "gui_plus_verified_surface_required",
      code: "gui_plus_verified_surface_required",
      message: "GUI Plus requires the current package and observationId to match the Android-verified work surface.",
      verifiedSurfaceProtocol: verifiedSurface || null,
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
    const preCallThinking = structuralGuiThinkingDecision(agentMemory);
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
      retryable: retryableCloudError(error),
      ...baseMeta,
      sourceDetail: deterministicBackendError(error)
        ? "backend_deterministic_error_no_retry"
        : "aliyun_gui_plus_cloud_call_failed_no_local_action",
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
  const { taskContract: currentTaskContract } = visualTaskStateFromMemory(agentMemory, goal);
  const responseTaskContract = mergeVisualTaskContractFromAction(
    goal,
    currentTaskContract,
    agentStep
  );
  if (responseTaskContract && !agentStep.taskContract) {
    agentStep = { ...agentStep, taskContract: responseTaskContract };
  }

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
    taskContract: responseTaskContract,
    taskMemoryAccepted: agentMemory?.taskMemory || null,
    taskExecutionProtocolAccepted: AGENT_TASK_EXECUTION_CONTRACT_SCHEMA,
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

function cachedAgentBrainRouteUsable(route) {
  if (!route || typeof route !== "object") return false;
  const routeName = normalizeAgentBrainRouteName(route.route || "visual_agent");
  if (["refuse", "ask_user"].includes(routeName)) return true;
  const confidence = Number(route.confidence || 0);
  return confidence >= 0.18 || Array.isArray(route.steps);
}

function reusableAgentBrainEntryOpenAppRoute(route, deviceContext) {
  if (!route || typeof route !== "object") return false;
  const routeName = normalizeAgentBrainRouteName(route.route || "visual_agent");
  const steps = Array.isArray(route.steps) ? route.steps : [];
  return steps.some((routeStep) => {
    if (!routeStep || typeof routeStep !== "object") return false;
    const executor = normalizeAgentBrainExecutorName(routeStep.executor || routeStep.route, routeName);
    const tool = normalizeAgentBrainToolName(routeStep.tool || routeStep.action || routeStep.name, executor);
    return executor === "device_tool" && tool === "open_app" && Boolean(
      canonicalInstalledAppForAgentBrainStep(routeStep, deviceContext)
    );
  });
}

function agentBrainEntryOpenAppPackage(route, deviceContext) {
  if (!route || typeof route !== "object") return "";
  const routeName = normalizeAgentBrainRouteName(route.route || "visual_agent");
  const steps = Array.isArray(route.steps) ? route.steps : [];
  for (const routeStep of steps) {
    if (!routeStep || typeof routeStep !== "object") continue;
    const executor = normalizeAgentBrainExecutorName(routeStep.executor || routeStep.route, routeName);
    const tool = normalizeAgentBrainToolName(routeStep.tool || routeStep.action || routeStep.name, executor);
    if (executor !== "device_tool" || tool !== "open_app") continue;
    const app = canonicalInstalledAppForAgentBrainStep(routeStep, deviceContext);
    if (app?.packageName) return safeText(app.packageName, 120);
  }
  return "";
}

async function handleAgentStepRequest(body, prompt, resolvedModel) {
  const startedAt = Date.now();
  const requestContext = buildVisualAgentRequestContext(body, prompt);
  body = requestContext.request;
  const {
    goal,
    snapshot,
    supportedSteps,
    screenshotInfo,
    deviceContext,
    recentAgentActions,
    agentMemory: rawAgentMemory,
    verifiedSurface,
    controllerHandoffActive,
    requestBytes,
    readBodyMs,
    cloudRouteVisualLoopRequest,
    strictObservationBindingRequired,
  } = requestContext;
  const session = getAgentSession(body, goal);
  syncAgentGuiInteractionHistory(session, rawAgentMemory.interactionHistory);
  session.step += 1;

  const exclusiveGuiPlusVisualSession = Boolean(
    verifiedSurface.guiPlusEligible && (
      isExclusiveGuiPlusVisualRequest(body) ||
      isExclusiveGuiPlusVisualMemory(rawAgentMemory) ||
      verifiedSurface.deterministicHandoffRecovery === true
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
    verifiedCurrentPackage: verifiedSurface.currentPackage || "",
    verifiedPackageMatchesCurrent: verifiedSurface.packageMatches === true,
    observationId: verifiedSurface.observationId || "",
    expectedActionObservationId: verifiedSurface.observationId || "",
    actionObservationId: verifiedSurface.observationId || "",
    observationMatchesExpected: verifiedSurface.observationMatchesExpected === true,
    runtimeExecutionContext: {
      schema: "visual_action_response_binding_v1",
      observationId: verifiedSurface.observationId || "",
      expectedActionObservationId: verifiedSurface.observationId || "",
      actionObservationId: verifiedSurface.observationId || "",
      routeEpoch: verifiedSurface.routeEpoch,
      surfaceEpoch: verifiedSurface.surfaceEpoch,
      currentPackage: verifiedSurface.currentPackage || "",
      verifiedTargetPackage: verifiedSurface.verifiedTargetPackage || "",
    },
    verifiedSurfaceProtocol: verifiedSurface,
  };

  const officialGuiPlusLoop = Boolean(
    (screenshotInfo.hasImage || controllerHandoffActive) &&
      guiProviderConfig.provider === "aliyun_gui_plus"
  );

  if (strictObservationBindingRequired && !verifiedSurface.observationMatchesExpected) {
    return {
      ok: false,
      error: "visual_observation_binding_invalid",
      code: "visual_observation_binding_invalid",
      message: "Android visual action requests must provide one identical observationId in runtimeExecutionContext, observationId and expectedActionObservationId.",
      ...baseMeta,
      retryable: true,
      version: WORKER_VERSION,
    };
  }

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

  // v14 is an isolated protocol family: it must never fall through to the legacy
  // task-contract/router stack, including on the first turn without a screenshot.
  if (cloudRouteVisualLoopRequest) {
    const cachedEntryPackage = agentBrainEntryOpenAppPackage(session.agentBrainRoute, deviceContext);
    const currentForegroundPackage = safeText(snapshot?.packageName || snapshot?.currentApp || "", 120);
    const foregroundEntryHandoffRecovery = Boolean(
      cachedEntryPackage &&
      currentForegroundPackage === cachedEntryPackage &&
      !verifiedSurface.guiPlusEligible &&
      ["launching", "planning", "replanning"].includes(verifiedSurface.surfaceState)
    );
    const routeRefreshRequested = Boolean(
      verifiedSurface.routeRefreshRequested || agentMemoryRequestsRouteRefresh(rawAgentMemory)
    ) && !foregroundEntryHandoffRecovery;
    if (routeRefreshRequested) {
      session.agentBrainRoute = null;
      session.agentBrainSource = "";
      session.agentBrainError = "";
    }
    const sessionEntryRoute = (
      foregroundEntryHandoffRecovery ||
      (!routeRefreshRequested && reusableAgentBrainEntryOpenAppRoute(session.agentBrainRoute, deviceContext))
    )
      ? session.agentBrainRoute
      : null;
    const routeResult = exclusiveGuiPlusVisualSession
      ? null
      : sessionEntryRoute
        ? {
            route: sessionEntryRoute,
            source: foregroundEntryHandoffRecovery
              ? "agent_brain_foreground_entry_handoff_recovery"
              : "agent_brain_session_entry_route",
            elapsedMs: 0,
            error: "",
            cached: true,
            foregroundEntryHandoffRecovery,
          }
        : await resolveAgentBrainRouteForStep(
            goal,
            snapshot,
            recentAgentActions,
            deviceContext,
            rawAgentMemory,
            startedAt,
            {
              forceRefresh: routeRefreshRequested,
              useCache: true,
              verifiedSurface,
            }
          );
    const effectiveAgentBrainRoute = routeResult?.route || null;
    const agentBrainMs = Number(routeResult?.elapsedMs || 0);
    const agentBrainSource = routeResult?.source || (exclusiveGuiPlusVisualSession ? "gui_plus_verified_surface" : "deepseek_cloud_route");
    const agentBrainError = routeResult?.error || "";
    if (!exclusiveGuiPlusVisualSession && effectiveAgentBrainRoute) {
      session.agentBrainRoute = effectiveAgentBrainRoute;
      session.agentBrainSource = agentBrainSource;
      session.agentBrainError = "";
    }
    if (!exclusiveGuiPlusVisualSession && !effectiveAgentBrainRoute && agentBrainError) {
      return {
        ok: false,
        error: `DeepSeek 主脑路由失败：${agentBrainError}`,
        code: "agent_brain_route_failed",
        ...baseMeta,
        retryable: true,
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
          foregroundEntryHandoffRecovery,
          cachedEntryPackage,
          currentForegroundPackage,
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
      supportedSteps: supportedSteps.filter((type) => !INTERNAL_TOOL_AGENT_STEP_TYPE_SET.has(type)),
      screenshotInfo,
      deviceContext,
      agentMemory: rawAgentMemory,
      recentAgentActions,
      requestBytes,
      readBodyMs,
      session,
      guiProviderConfig,
      baseMeta,
      verifiedSurface,
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

  // Pass the cloud action through without local semantic judging. Keep the model-declared
  // structured risk metadata intact so Android can enforce confirmation deterministically.
  if (agentStep) {
    const declaredRiskLevel = normalizeRiskLevel(agentStep.riskLevel || agentStep.risk || "low");
    agentStep = {
      ...agentStep,
      riskLevel: declaredRiskLevel,
      requiresConfirmation: Boolean(agentStep.requiresConfirmation) || riskRequiresConfirmation(declaredRiskLevel),
    };
  }
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
  const { taskContract: memoryTaskContract } = visualTaskStateFromMemory(agentMemory, goal);
  const responseTaskContract = mergeVisualTaskContractFromAction(
    goal,
    memoryTaskContract || normalizeVisualTaskContract(taskExecutionContract || body.taskContract || null, goal),
    agentStep
  );
  if (responseTaskContract && !agentStep.taskContract) {
    agentStep = { ...agentStep, taskContract: responseTaskContract };
    agentSteps[0] = agentStep;
    actionBatch[0] = agentStep;
  }

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
    taskContract: responseTaskContract,
    taskMemoryAccepted: agentMemory?.taskMemory || null,
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
      taskRuntime: {
        schema: "cloud_two_brain_runtime_v1",
        localSkillRegistryEnabled: false,
        semanticDecisionOwner: exclusiveGuiPlusVisualSession ? "gui_plus" : "cloud_models",
      },
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

// ===== AI Ledger source module: 60-chat-data-tools.js =====
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

// ===== AI Ledger source module: 70-http-server.js =====
function assertBackendArchitectureContract() {
  const supportedUnique = new Set(SUPPORTED_AGENT_STEP_TYPES);
  if (supportedUnique.size !== SUPPORTED_AGENT_STEP_TYPES.length) {
    throw new Error("backend_contract_duplicate_supported_agent_step");
  }
  for (const type of INTERNAL_TOOL_AGENT_STEP_TYPES) {
    if (!supportedUnique.has(type)) throw new Error(`backend_contract_internal_tool_not_supported:${type}`);
  }
  for (const unsupported of ["long_press", "key"]) {
    if (supportedUnique.has(unsupported)) throw new Error(`backend_contract_unsupported_android_action:${unsupported}`);
  }
  if (!ANDROID_CLOUD_ROUTE_VISUAL_PROTOCOL_ALIASES.has(ANDROID_CLOUD_ROUTE_VISUAL_PROTOCOL)) {
    throw new Error("backend_contract_visual_protocol_alias_missing");
  }
  return true;
}

assertBackendArchitectureContract();

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
          "canonical_visual_request_context",
          "build_time_modular_source",
          ...(ENABLE_CHAT_STICKERS ? ["chat_sticker_v2", "chat_sticker_v1_legacy"] : []),
        ],
        architecture: BACKEND_ARCHITECTURE,
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
      return sendJson(res, structuredResultHttpStatus(routeResult), routeResult);
    }

    if (isAgentOutcomeVerificationRequest(body)) {
      const verifyResult = await handleAgentOutcomeVerificationRequest(body, prompt, resolved === "qwen_vision" ? "qwen" : resolved);
      return sendJson(res, structuredResultHttpStatus(verifyResult), verifyResult);
    }

    if (isVisualAgentStepRequest(body)) {
      const visualAgentResult = await handleVisualAgentStepRoute(body, prompt, resolved === "qwen_vision" ? "qwen" : resolved);
      return sendJson(res, structuredResultHttpStatus(visualAgentResult), visualAgentResult);
    }

    if (isAgentModeRequest(body)) {
      const agentResult = await handleAgentStepRequest(body, prompt, resolved === "qwen_vision" ? "qwen" : resolved);
      return sendJson(res, structuredResultHttpStatus(agentResult), agentResult);﻿
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

        const sticker = selectChatSticker({
          clientKey,
          body,
          prompt,
          reply,
          structuredData,
          sources,
          toolIntent,
        });
        const responseSticker = stickerToResponsePayload(sticker);
        const responseStructuredData = structuredData || stickerToLegacyStructuredData(sticker);

        writeSse(res, {
          type: "done",
          ok: true,
          reply,
          sticker: responseSticker,
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
          structuredData: responseStructuredData,
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
    const hasCommandPayload = Boolean(commandPayload.agentAction || commandPayload.mobileAction || commandPayload.preferenceUpdate);
    const sticker = hasCommandPayload
      ? null
      : selectChatSticker({
          clientKey,
          body,
          prompt,
          reply: cleanReply,
          structuredData,
          sources,
          toolIntent,
        });
    const responseSticker = stickerToResponsePayload(sticker);
    const responseStructuredData = structuredData || stickerToLegacyStructuredData(sticker);

    return sendJson(res, 200, {
      ok: true,
      reply: cleanReply,
      sticker: responseSticker,
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
      structuredData: responseStructuredData,
      structuredError,
      toolIntent,
      intentError,
      deviceIntent,
      deviceIntentError,
      version: WORKER_VERSION,
    });
  } catch (e) {
    if (releaseConcurrency) releaseConcurrency();
    const deterministic = deterministicBackendError(e);
    const status = deterministic
      ? 500
      : Number(e?.statusCode || 0) || (e?.code === "body_too_large" ? 413 : e?.code === "invalid_json" ? 400 : e?.code === "request_body_timeout" ? 408 : 502);
    const code = String(
      deterministic
        ? "backend_contract_error"
        : e?.code || (status >= 500 ? "provider_call_failed" : "request_failed")
    );
    if (requestController.signal.aborted && !responseWritable(res)) return false;
    return sendJson(res, status, {
      ok: false,
      error: sanitizeProviderError(e, 220),
      code,
      retryable: deterministic ? false : retryableCloudError(e),
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
  BACKEND_ARCHITECTURE,
  assertBackendArchitectureContract,
  isVisualAgentStepRequest,
  isCloudRouteVisualLoopRequest,
  buildAgentBrainRouteMessages,
  normalizeAgentBrainRoutePlan,
  verifiedVisualSurfaceProtocol,
  normalizeRiskLevel,
  riskRequiresConfirmation,
  applyGuiPlusDeclaredRisk,
  agentBrainRouteToDirectAgentPlan,
  canonicalizeVisualAgentRequest,
  buildVisualAgentRequestContext,
  normalizeVisualTaskContract,
  normalizeVisualTaskMemory,
  visualTaskStateFromMemory,
  normalizeAgentStepType,
  normalizeDeviceControlAction,
  normalizeAppCapability,
  normalizeInternalDeviceToolArgsForAndroid,
  supportedAgentStepsFromBody,
  normalizeBase64Payload,
  decodedBase64Bytes,
};

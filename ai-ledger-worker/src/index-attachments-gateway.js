import commandWorker from "./index.js";

const GATEWAY_VERSION = "ai-ledger-attachment-gateway-v1";
const JSON_HEADERS = { "content-type": "application/json; charset=utf-8" };
const MAX_ATTACHMENTS = 3;
const MAX_BASE64_CHARS = 6_000_000;
const INLINE_MIME_TYPES = new Set([
  "image/jpeg",
  "image/png",
  "image/webp",
  "image/heic",
  "image/heif",
  "application/pdf",
]);
const TEXT_MIME_RE = /^(text\/|application\/json|text\/javascript|application\/javascript)/i;

export default {
  async fetch(request, env, ctx) {
    const corsHeaders = cors(request, env);
    const url = new URL(request.url);

    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders });

    if (request.method === "GET" && url.pathname === "/health") {
      const base = await readBaseHealth(env, ctx, request);
      return json({
        ok: true,
        version: GATEWAY_VERSION,
        mode: "attachment_gateway_preserve_command_protocol",
        commandWorker: base,
        hasGeminiKey: Boolean(env.GEMINI_API_KEY),
        model: env.GEMINI_MODEL || "gemini-2.5-flash",
        tools: ["attachments.image", "attachments.pdf", "attachments.text", "command_protocol"],
      }, 200, corsHeaders);
    }

    if (request.method !== "POST") return commandWorker.fetch(request, env, ctx);

    const delegateRequest = request.clone();
    let body;
    try {
      body = await request.json();
    } catch {
      return commandWorker.fetch(delegateRequest, env, ctx);
    }

    const attachments = sanitizeAttachments(body?.attachments);
    if (!attachments.length) return commandWorker.fetch(delegateRequest, env, ctx);

    if (!env.GEMINI_API_KEY) {
      return json({
        reply: "图片和文件理解需要 GEMINI_API_KEY。当前云端未检测到 Gemini Key，所以附件暂时无法分析。",
        action: "chat",
        records: [],
        mobileCommand: null,
        source: "attachment_ai_missing_key",
        version: GATEWAY_VERSION,
      }, 200, corsHeaders);
    }

    try {
      const reply = await callGeminiAttachment(env, body, attachments);
      return json({
        reply,
        action: "chat",
        records: [],
        mobileCommand: null,
        source: "gemini_vision",
        version: GATEWAY_VERSION,
      }, 200, corsHeaders);
    } catch (error) {
      return json({
        reply: `附件分析失败：${String(error?.message || error).slice(0, 180)}`,
        action: "chat",
        records: [],
        mobileCommand: null,
        source: "gemini_vision_error",
        version: GATEWAY_VERSION,
      }, 200, corsHeaders);
    }
  },
};

async function readBaseHealth(env, ctx, originalRequest) {
  try {
    const url = new URL(originalRequest.url);
    const req = new Request(`${url.origin}/health`, { method: "GET", headers: originalRequest.headers });
    const res = await commandWorker.fetch(req, env, ctx);
    const data = await res.json().catch(() => null);
    return data || { ok: res.ok, status: res.status };
  } catch (error) {
    return { ok: false, error: String(error?.message || error).slice(0, 120) };
  }
}

function sanitizeAttachments(input) {
  if (!Array.isArray(input)) return [];
  return input.slice(0, MAX_ATTACHMENTS).map((item) => {
    const name = String(item?.name || "未命名文件").slice(0, 120);
    const mimeType = normalizeMime(item?.mimeType || item?.type || "");
    const data = String(item?.data || "").replace(/^data:[^,]+,/, "").trim();
    const size = Number(item?.size) || Math.ceil(data.length * 0.75);
    return { name, mimeType, data, size };
  }).filter((item) => item.data && item.data.length <= MAX_BASE64_CHARS && (INLINE_MIME_TYPES.has(item.mimeType) || TEXT_MIME_RE.test(item.mimeType)));
}

function normalizeMime(value) {
  const type = String(value || "").toLowerCase().split(";")[0].trim();
  if (type === "image/jpg") return "image/jpeg";
  if (!type || type === "application/octet-stream") return "application/octet-stream";
  return type;
}

async function callGeminiAttachment(env, body, attachments) {
  const model = String(env.GEMINI_MODEL || "gemini-2.5-flash").replace(/^models\//, "");
  const endpoint = `${env.GEMINI_API_BASE || "https://generativelanguage.googleapis.com/v1beta/models"}/${model}:generateContent?key=${encodeURIComponent(env.GEMINI_API_KEY)}`;
  const userText = lastUserText(body?.messages, body?.text) || "请分析我上传的图片或文件，提取关键信息并给出简洁结论。";
  const parts = [{ text: buildPrompt(userText, attachments) }];

  for (const attachment of attachments) {
    if (TEXT_MIME_RE.test(attachment.mimeType) && !INLINE_MIME_TYPES.has(attachment.mimeType)) {
      parts.push({ text: `\n\n【文本文件：${attachment.name}】\n${decodeText(attachment.data).slice(0, 12000)}` });
    } else {
      parts.push({ inline_data: { mime_type: attachment.mimeType, data: attachment.data } });
    }
  }

  const response = await fetch(endpoint, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      systemInstruction: {
        parts: [{ text: "你是手机端 AI 助手的附件理解模块。只负责识别图片、截图、PDF 和文本文件内容。不要返回本地执行 command，不要保存账单；如果发现票据/账单，只提取金额、日期、商户等信息，提醒用户确认后可再记账。回答用中文，简洁准确。" }],
      },
      contents: [{ role: "user", parts }],
      generationConfig: { temperature: 0.2, maxOutputTokens: 1200 },
    }),
  });

  const data = await response.json().catch(() => null);
  if (!response.ok) throw new Error(data?.error?.message || `Gemini HTTP ${response.status}`);
  const text = (data?.candidates?.[0]?.content?.parts || []).map((part) => part?.text || "").join("\n").trim();
  return text || "我看到了附件，但没有提取到明确内容。你可以补一句想让我重点看什么。";
}

function buildPrompt(userText, attachments) {
  const list = attachments.map((item, index) => `${index + 1}. ${item.name}（${item.mimeType}，约 ${Math.max(1, Math.round(item.size / 1024))}KB）`).join("\n");
  return [
    `用户问题：${userText}`,
    `上传附件：\n${list}`,
    "请基于附件内容回答。若是截图，重点看界面文字、错误提示和下一步操作；若是图片，描述主体和可见文字；若是文件，总结结构和重点。",
  ].join("\n\n");
}

function lastUserText(messages, fallback) {
  if (Array.isArray(messages)) {
    const last = [...messages].reverse().find((item) => item?.role === "user" && String(item?.content || "").trim());
    if (last) return String(last.content).trim();
  }
  return String(fallback || "").trim();
}

function decodeText(base64) {
  try {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
    return new TextDecoder("utf-8", { fatal: false }).decode(bytes);
  } catch {
    return "[文本文件解码失败]";
  }
}

function cors(request, env) {
  const origin = request.headers.get("Origin") || "";
  const allowed = String(env.ALLOWED_ORIGINS || "*").split(",").map((x) => x.trim()).filter(Boolean);
  const allow = allowed.includes("*") || allowed.includes(origin) ? origin || "*" : allowed[0] || "*";
  return {
    ...JSON_HEADERS,
    "access-control-allow-origin": allow,
    "access-control-allow-methods": "GET, POST, OPTIONS",
    "access-control-allow-headers": "content-type",
    vary: "Origin",
  };
}

function json(payload, status = 200, headers = {}) {
  return new Response(JSON.stringify(payload), { status, headers: { ...JSON_HEADERS, ...headers } });
}

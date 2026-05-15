import smartWorker from "./index-smart.js";

const VERSION = "2026-05-15-multimodal-1";
const JSON_HEADERS = { "content-type": "application/json; charset=utf-8" };
const MAX_ATTACHMENTS = 3;
const MAX_BASE64_CHARS = 6_000_000;
const SUPPORTED_INLINE = new Set([
  "image/jpeg",
  "image/png",
  "image/webp",
  "image/heic",
  "image/heif",
  "application/pdf",
]);
const SUPPORTED_TEXT = /^(text\/|application\/json|text\/javascript|application\/javascript)/i;

export default {
  async fetch(request, env, ctx) {
    const corsHeaders = cors(request, env);
    const url = new URL(request.url);

    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders });

    if (request.method === "GET" && url.pathname === "/health") {
      return json({
        ok: true,
        worker: "ai-ledger-parser",
        version: VERSION,
        mode: "multimodal_smart_router_tools_plus_gemini",
        provider: env.GEMINI_API_KEY ? "gemini" : "workers_ai",
        model: env.GEMINI_API_KEY ? (env.GEMINI_MODEL || "gemini-2.5-flash") : (env.AI_MODEL || "@cf/meta/llama-3.1-8b-instruct"),
        hasGeminiKey: Boolean(env.GEMINI_API_KEY),
        hasAiBinding: Boolean(env.AI),
        tools: ["image_understanding", "pdf_reading", "text_file_reading", "weather", "calculator", "webpage", "wikipedia", "ledger", "mobile_actions"],
      }, 200, corsHeaders);
    }

    if (request.method !== "POST") return smartWorker.fetch(request, env, ctx);

    const delegate = request.clone();
    let body;
    try { body = await request.json(); }
    catch { return smartWorker.fetch(delegate, env, ctx); }

    const attachments = sanitizeAttachments(body?.attachments);
    if (!attachments.length) return smartWorker.fetch(delegate, env, ctx);

    if (!env.GEMINI_API_KEY) {
      return json({
        reply: "图片和文件理解需要 Gemini 多模态能力。现在还没有检测到 GEMINI_API_KEY，所以我暂时只能处理文字对话、天气、计算和网页读取。",
        action: "chat",
        records: [],
        mobileCommand: null,
        source: "attachment_ai_missing_key",
        version: VERSION,
      }, 200, corsHeaders);
    }

    try {
      const result = await runGeminiMultimodal({ env, body, attachments });
      return json({
        reply: result,
        action: "chat",
        records: [],
        mobileCommand: null,
        source: "gemini_vision",
        version: VERSION,
      }, 200, corsHeaders);
    } catch (error) {
      return json({
        reply: `图片/文件理解失败：${String(error?.message || error).slice(0, 180)}`,
        action: "chat",
        records: [],
        mobileCommand: null,
        source: "gemini_vision_error",
        version: VERSION,
      }, 200, corsHeaders);
    }
  },
};

function sanitizeAttachments(input) {
  if (!Array.isArray(input)) return [];
  return input.slice(0, MAX_ATTACHMENTS).map((item) => {
    const name = String(item?.name || "未命名文件").slice(0, 120);
    const mimeType = normalizeMime(item?.mimeType || item?.type || "");
    const data = String(item?.data || "").replace(/^data:[^,]+,/, "").trim();
    const size = Number(item?.size) || Math.ceil(data.length * 0.75);
    return { name, mimeType, data, size };
  }).filter((item) => item.data && item.data.length <= MAX_BASE64_CHARS && (SUPPORTED_INLINE.has(item.mimeType) || SUPPORTED_TEXT.test(item.mimeType)));
}

function normalizeMime(value) {
  const type = String(value || "").toLowerCase().split(";")[0].trim();
  if (type === "image/jpg") return "image/jpeg";
  if (!type || type === "application/octet-stream") return "application/octet-stream";
  return type;
}

async function runGeminiMultimodal({ env, body, attachments }) {
  const model = env.GEMINI_MODEL || "gemini-2.5-flash";
  const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(env.GEMINI_API_KEY)}`;
  const userText = getLastUserText(body?.messages, body?.text) || "请分析我上传的图片或文件，提取关键信息并给出简洁结论。";
  const parts = [
    { text: buildMultimodalPrompt(userText, attachments) },
  ];

  for (const att of attachments) {
    if (SUPPORTED_TEXT.test(att.mimeType) && !SUPPORTED_INLINE.has(att.mimeType)) {
      parts.push({ text: `\n\n【文件：${att.name}】\n${decodeTextAttachment(att.data).slice(0, 12000)}` });
    } else {
      parts.push({ inline_data: { mime_type: att.mimeType, data: att.data } });
    }
  }

  const response = await fetch(endpoint, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      systemInstruction: {
        parts: [{ text: "你是手机端 AI 助手，擅长图片识别、截图分析、PDF/文件摘要和中文问答。回答要简洁、准确；如果是截图，重点说明界面内容、错误原因和下一步操作；如果是票据/账单，可以提取金额、日期、商户和用途，但不要擅自保存账单。" }],
      },
      contents: [{ role: "user", parts }],
      generationConfig: {
        temperature: 0.2,
        maxOutputTokens: 1200,
      },
    }),
  });
  const data = await response.json().catch(() => null);
  if (!response.ok) throw new Error(data?.error?.message || `Gemini HTTP ${response.status}`);
  const text = (data?.candidates?.[0]?.content?.parts || []).map((part) => part?.text || "").join("\n").trim();
  return text || "我看到了附件，但没有提取到明确内容。你可以补一句想让我重点看什么。";
}

function buildMultimodalPrompt(userText, attachments) {
  const list = attachments.map((att, index) => `${index + 1}. ${att.name}（${att.mimeType || "未知类型"}，约 ${Math.max(1, Math.round(att.size / 1024))}KB）`).join("\n");
  return [
    `用户问题：${userText}`,
    `上传附件：\n${list}`,
    "请基于附件内容回答用户问题。若无法读取或内容不清楚，要直接说明。若是图片，请描述主体、文字、界面、可能的问题；若是文件，请总结结构和重点。",
  ].join("\n\n");
}

function getLastUserText(messages, fallback) {
  if (Array.isArray(messages)) {
    const last = [...messages].reverse().find((message) => message?.role === "user" && String(message?.content || "").trim());
    if (last) return String(last.content).trim();
  }
  return String(fallback || "").trim();
}

function decodeTextAttachment(base64) {
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

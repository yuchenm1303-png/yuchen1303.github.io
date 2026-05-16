import commandWorker from "./index.js";

const GATEWAY_VERSION = "ai-ledger-attachment-gateway-v9-nvidia-model-split-vision";
const JSON_HEADERS = { "content-type": "application/json; charset=utf-8" };
const MAX_ATTACHMENTS = 3;
const MAX_BASE64_CHARS = 6_000_000;
const INLINE_MIME_TYPES = new Set(["image/jpeg", "image/png", "image/webp", "image/heic", "image/heif", "application/pdf"]);
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
        mode: "attachment_gateway_strict_model_picker_split_nvidia_models_vision",
        modelPickerRule: "auto allows fallback; manual selection is strict; Kimi and Mistral are separate entries",
        commandWorker: base,
        hasGeminiKey: Boolean(env.GEMINI_API_KEY),
        hasNvidiaKey: Boolean(env.NVIDIA_API_KEY),
        hasWorkersAI: Boolean(env.AI),
        geminiVisionModel: geminiModel(env),
        nvidiaKimiVisionModel: nvidiaVisionModel(env, "kimi"),
        nvidiaMistralVisionModel: nvidiaVisionModel(env, "mistral"),
        tools: ["attachments.image", "attachments.pdf", "attachments.text", "nvidia_vision", "gemini_vision", "workers_ai_vision"],
      }, 200, corsHeaders);
    }

    if (request.method !== "POST") return commandWorker.fetch(request, env, ctx);

    let body;
    try { body = await request.json(); } catch { return commandWorker.fetch(request, env, ctx); }

    const attachments = sanitizeAttachments(body?.attachments);
    if (!attachments.length) return commandWorker.fetch(cloneRequestWithJson(request, body), env, ctx);

    if (!env.GEMINI_API_KEY && !env.NVIDIA_API_KEY && !env.AI) {
      return json(responsePayload("图片和文件理解需要 GEMINI_API_KEY、NVIDIA_API_KEY 或 Workers AI。当前云端没有可用视觉模型，所以附件暂时无法分析。", "attachment_ai_missing_key", modelMeta("Attachment Gateway", "no-vision-model", "未配置视觉模型")), 200, corsHeaders);
    }

    const result = await analyzeAttachmentsWithFallback(env, body, attachments);
    return json(result, 200, corsHeaders);
  },
};

function cloneRequestWithJson(originalRequest, body) {
  const headers = new Headers(originalRequest.headers);
  headers.set("content-type", "application/json; charset=utf-8");
  headers.delete("content-length");
  return new Request(originalRequest.url, { method: "POST", headers, body: JSON.stringify(body) });
}

async function analyzeAttachmentsWithFallback(env, body, attachments) {
  const preference = normalizeModelPreference(body?.modelPreference || body?.aiModelPreference || body?.modelMode);
  const hasImage = hasImageAttachment(attachments);
  const errors = [];
  const steps = buildVisionSteps(preference);

  for (const step of steps) {
    if (step === "kimi" || step === "mistral") {
      if (!env.NVIDIA_API_KEY) { errors.push("NVIDIA: missing NVIDIA_API_KEY"); continue; }
      if (!hasImage) { errors.push("NVIDIA: 当前仅对图片启用多模态接口，PDF 建议切 Gemini 或自动"); continue; }
      try {
        const model = nvidiaVisionModel(env, step);
        const meta = modelMeta("NVIDIA NIM", model, nvidiaVisionLabel(model));
        const reply = await callNvidiaVision(env, body, attachments, model);
        return responsePayload(reply, "nvidia_vision", meta);
      } catch (error) {
        errors.push(`NVIDIA ${step}: ${String(error?.message || error).slice(0, 160)}`);
      }
    }

    if (step === "gemini") {
      if (!env.GEMINI_API_KEY) { errors.push("Gemini: missing GEMINI_API_KEY"); continue; }
      try {
        const meta = modelMeta("Gemini", geminiModel(env), geminiModelLabel(geminiModel(env)));
        const reply = await callGeminiAttachment(env, body, attachments);
        return responsePayload(reply, "gemini_vision", meta);
      } catch (error) {
        errors.push(`Gemini: ${String(error?.message || error).slice(0, 160)}`);
      }
    }

    if (step === "workers") {
      if (!env.AI) { errors.push("Workers AI: binding unavailable"); continue; }
      try {
        const meta = modelMeta("Cloudflare Workers AI", "@cf/llava-hf/llava-1.5-7b-hf", "Workers AI LLaVA 1.5 7B");
        const reply = await callWorkersVisionFallback(env, body, attachments);
        return responsePayload(reply, "workers_ai_vision", meta);
      } catch (error) {
        errors.push(`WorkersAI: ${String(error?.message || error).slice(0, 160)}`);
      }
    }
  }

  const strict = preference !== "auto";
  const meta = strict ? selectedVisionMeta(env, preference) : modelMeta("Attachment Gateway", "all_failed", "附件识别失败");
  const message = strict
    ? `你当前选择的是 ${meta.modelLabel}，但这个模型没有成功完成附件识别。已按“手动选择严格模式”停止回退，避免出现你选 Kimi/Mistral 但实际由 Gemini 回答的情况。可以切回“自动”，或稍后再试。\n\n错误信息：${errors.join("；") || "所选模型不可用"}`
    : `附件分析失败：${errors.join("；") || "所有视觉模型都不可用"}`;
  return responsePayload(message, strict ? "selected_model_failed" : "vision_all_failed", meta);
}

function buildVisionSteps(preference) {
  if (preference === "kimi") return ["kimi"];
  if (preference === "mistral") return ["mistral"];
  if (preference === "gemini") return ["gemini"];
  if (preference === "workers") return ["workers"];
  return ["gemini", "kimi", "mistral", "workers"];
}

function selectedVisionMeta(env, preference) {
  if (preference === "kimi" || preference === "mistral") return modelMeta("NVIDIA NIM", nvidiaVisionModel(env, preference), nvidiaVisionLabel(nvidiaVisionModel(env, preference)));
  if (preference === "gemini") return modelMeta("Gemini", geminiModel(env), geminiModelLabel(geminiModel(env)));
  if (preference === "workers") return modelMeta("Cloudflare Workers AI", "@cf/llava-hf/llava-1.5-7b-hf", "Workers AI LLaVA 1.5 7B");
  return modelMeta("Attachment Gateway", "auto", "自动视觉模型池");
}

function responsePayload(reply, source, meta = {}) {
  return { reply, action: "chat", records: [], mobileCommand: null, source, provider: meta.provider, model: meta.model, modelLabel: meta.modelLabel, version: appendRunLabel(GATEWAY_VERSION, meta.modelLabel) };
}

async function readBaseHealth(env, ctx, originalRequest) {
  try {
    const url = new URL(originalRequest.url);
    const req = new Request(`${url.origin}/health`, { method: "GET", headers: originalRequest.headers });
    const res = await commandWorker.fetch(req, env, ctx);
    return await res.json().catch(() => ({ ok: res.ok, status: res.status }));
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
  const model = geminiModel(env);
  const endpoint = `${env.GEMINI_API_BASE || "https://generativelanguage.googleapis.com/v1beta/models"}/${model}:generateContent?key=${encodeURIComponent(env.GEMINI_API_KEY)}`;
  const userText = lastUserText(body?.messages, body?.text) || "请分析我上传的图片或文件，提取关键信息并给出简洁结论。";
  const parts = [{ text: buildPrompt(userText, attachments) }];
  for (const attachment of attachments) {
    if (TEXT_MIME_RE.test(attachment.mimeType) && !INLINE_MIME_TYPES.has(attachment.mimeType)) parts.push({ text: `\n\n【文本文件：${attachment.name}】\n${decodeText(attachment.data).slice(0, 12000)}` });
    else parts.push({ inline_data: { mime_type: attachment.mimeType, data: attachment.data } });
  }
  const response = await fetch(endpoint, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      systemInstruction: { parts: [{ text: "你是手机端 AI 助手的附件理解模块。只负责识别图片、截图、PDF 和文本文件内容。不要返回本地执行 command。回答用中文，简洁准确。" }] },
      contents: [{ role: "user", parts }],
      generationConfig: { temperature: 0.2, maxOutputTokens: 1400 },
    }),
  });
  const data = await response.json().catch(() => null);
  if (!response.ok) throw new Error(data?.error?.message || `Gemini HTTP ${response.status}`);
  const text = (data?.candidates?.[0]?.content?.parts || []).map((part) => part?.text || "").join("\n").trim();
  return text || "我看到了附件，但没有提取到明确内容。你可以补一句想让我重点看什么。";
}

async function callNvidiaVision(env, body, attachments, model) {
  const endpoint = `${nvidiaBaseUrl(env)}/chat/completions`;
  const userText = lastUserText(body?.messages, body?.text) || "请分析我上传的图片，提取图中文字并说明关键内容。";
  const content = [{ type: "text", text: buildPrompt(userText, attachments) }];
  for (const attachment of attachments) {
    if (attachment.mimeType.startsWith("image/")) content.push({ type: "image_url", image_url: { url: `data:${attachment.mimeType};base64,${attachment.data}` } });
    else if (TEXT_MIME_RE.test(attachment.mimeType)) content.push({ type: "text", text: `\n\n【文本文件：${attachment.name}】\n${decodeText(attachment.data).slice(0, 12000)}` });
  }
  const response = await fetch(endpoint, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${env.NVIDIA_API_KEY}` },
    body: JSON.stringify({
      model,
      messages: [
        { role: "system", content: "你是手机端 AI 助手的多模态识图模块。用中文回答，重点提取截图文字、错误提示、界面信息和用户可执行的下一步。" },
        { role: "user", content },
      ],
      temperature: 0.2,
      max_tokens: 1400,
    }),
  });
  const data = await response.json().catch(() => null);
  if (!response.ok) throw new Error(data?.error?.message || data?.message || `NVIDIA HTTP ${response.status}`);
  const text = data?.choices?.[0]?.message?.content || data?.choices?.[0]?.text || "";
  return String(text || "").trim() || "我看到了图片，但没有提取到明确内容。";
}

async function callWorkersVisionFallback(env, body, attachments) {
  if (!env.AI) throw new Error("Workers AI binding is not available");
  const image = attachments.find((item) => item.mimeType.startsWith("image/"));
  if (!image) {
    const textFiles = attachments.filter((item) => TEXT_MIME_RE.test(item.mimeType));
    if (textFiles.length) return textFiles.map((item) => `【${item.name}】\n${decodeText(item.data).slice(0, 4000)}`).join("\n\n");
    throw new Error("Workers AI 兜底目前只支持图片和文本文件");
  }
  const userText = lastUserText(body?.messages, body?.text) || "请提取图片中的文字，并简要说明图片内容。";
  const bytes = base64ToNumberArray(image.data);
  const prompt = `请用中文回答。用户问题：${userText}\n重点提取图片/截图中的文字、错误提示和可见关键信息。`;
  const result = await env.AI.run("@cf/llava-hf/llava-1.5-7b-hf", { image: bytes, prompt });
  return String(result?.description || result?.response || result?.text || JSON.stringify(result)).slice(0, 1200);
}

function buildPrompt(userText, attachments) {
  const list = attachments.map((item, index) => `${index + 1}. ${item.name}（${item.mimeType}，约 ${Math.max(1, Math.round(item.size / 1024))}KB）`).join("\n");
  return [`用户问题：${userText}`, `上传附件：\n${list}`, "请基于附件内容回答。若是截图，重点看界面文字、错误提示和下一步操作；若是图片，描述主体和可见文字；若是文件，总结结构和重点。"].join("\n\n");
}

function normalizeModelPreference(value) {
  const v = String(value || "auto").toLowerCase().trim();
  if (["auto", "gemini", "kimi", "mistral", "nvidia", "workers", "workers_ai"].includes(v)) {
    if (v === "nvidia") return "kimi";
    if (v === "workers_ai") return "workers";
    return v;
  }
  return "auto";
}

function hasImageAttachment(attachments) { return attachments.some((item) => item.mimeType.startsWith("image/")); }
function geminiModel(env) { return String(env.GEMINI_VISION_MODEL || env.GEMINI_MODEL || "gemini-2.5-flash").replace(/^models\//, ""); }
function geminiModelLabel(model) { const value = String(model || ""); if (/2\.5.*flash/i.test(value)) return "Gemini 2.5 Flash"; if (/2\.5.*pro/i.test(value)) return "Gemini 2.5 Pro"; if (/2\.0.*flash/i.test(value)) return "Gemini 2.0 Flash"; return value || "Gemini"; }
function nvidiaBaseUrl(env) { return String(env.NVIDIA_BASE_URL || "https://integrate.api.nvidia.com/v1").replace(/\/+$/g, ""); }
function pickNvidiaEnvModel(...values) { return values.map((v) => String(v || "").trim()).find(Boolean) || ""; }
function nvidiaVisionModel(env, preference = "kimi") {
  if (preference === "mistral") return pickNvidiaEnvModel(env.NVIDIA_MISTRAL_VISION_MODEL, env.NVIDIA_MISTRAL_MODEL, "mistralai/mistral-medium-3.5-128b");
  return pickNvidiaEnvModel(env.NVIDIA_KIMI_VISION_MODEL, env.NVIDIA_KIMI_MODEL, env.NVIDIA_VISION_MODEL && String(env.NVIDIA_VISION_MODEL).toLowerCase().includes("kimi") ? env.NVIDIA_VISION_MODEL : "", "moonshotai/kimi-k2.6");
}
function nvidiaVisionLabel(model) { const value = String(model || ""); if (/kimi/i.test(value)) return `${value} · via NVIDIA NIM`; if (/qwen/i.test(value)) return `${value} · via NVIDIA NIM`; if (/mistral/i.test(value)) return `${value} · via NVIDIA NIM`; return `${value || "多模态模型"} · via NVIDIA NIM`; }
function modelMeta(provider, model, label) { return { provider: String(provider || ""), model: String(model || ""), modelLabel: String(label || model || provider || "Cloud Model") }; }
function appendRunLabel(version, label) { const clean = String(label || "").trim(); return clean && !String(version || "").includes(clean) ? `${version} · ${clean}` : version; }
function lastUserText(messages, fallback) { if (Array.isArray(messages)) { const last = [...messages].reverse().find((item) => item?.role === "user" && String(item?.content || "").trim()); if (last) return String(last.content).trim(); } return String(fallback || "").trim(); }
function decodeText(base64) { try { const binary = atob(base64); const bytes = new Uint8Array(binary.length); for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i); return new TextDecoder("utf-8", { fatal: false }).decode(bytes); } catch { return "[文本文件解码失败]"; } }
function base64ToNumberArray(base64) { const binary = atob(base64); const bytes = new Array(binary.length); for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i); return bytes; }
function json(payload, status = 200, headers = {}) { return new Response(JSON.stringify(payload), { status, headers: { ...JSON_HEADERS, ...headers } }); }
function cors(request, env) { const origin = request.headers.get("Origin") || ""; const allowed = String(env.ALLOWED_ORIGINS || "*").split(",").map((item) => item.trim()).filter(Boolean); const allowOrigin = allowed.includes("*") || allowed.includes(origin) ? origin || "*" : allowed[0] || "*"; return { ...JSON_HEADERS, "access-control-allow-origin": allowOrigin, "access-control-allow-methods": "GET, POST, OPTIONS", "access-control-allow-headers": "content-type", vary: "Origin" }; }

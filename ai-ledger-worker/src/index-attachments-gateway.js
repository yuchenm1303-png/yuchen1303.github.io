import commandWorker from "./index.js";

const GATEWAY_VERSION = "ai-ledger-attachment-gateway-v2";
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
        hasWorkersAI: Boolean(env.AI),
        model: env.GEMINI_MODEL || "gemini-2.5-flash",
        tools: ["attachments.image", "attachments.pdf", "attachments.text", "rss_web_search", "command_protocol"],
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
    if (!attachments.length && isForcedWebSearch(body)) {
      const search = await searchPublicNews(lastUserText(body?.messages, body?.text));
      if (search.ok) return json(search, 200, corsHeaders);
      return json(createMissingSearchResponse(search.error), 200, corsHeaders);
    }

    if (!attachments.length) return commandWorker.fetch(delegateRequest, env, ctx);

    if (!env.GEMINI_API_KEY && !env.AI) {
      return json({
        reply: "图片和文件理解需要 GEMINI_API_KEY 或 Workers AI。当前云端没有可用视觉模型，所以附件暂时无法分析。",
        action: "chat",
        records: [],
        mobileCommand: null,
        source: "attachment_ai_missing_key",
        version: GATEWAY_VERSION,
      }, 200, corsHeaders);
    }

    try {
      const reply = env.GEMINI_API_KEY
        ? await callGeminiAttachment(env, body, attachments)
        : await callWorkersVisionFallback(env, body, attachments);
      return json({
        reply,
        action: "chat",
        records: [],
        mobileCommand: null,
        source: env.GEMINI_API_KEY ? "gemini_vision" : "workers_ai_vision",
        version: GATEWAY_VERSION,
      }, 200, corsHeaders);
    } catch (error) {
      const message = String(error?.message || error);
      if (isQuotaError(message) && env.AI) {
        try {
          const fallbackReply = await callWorkersVisionFallback(env, body, attachments);
          return json({
            reply: `${fallbackReply}\n\n注：Gemini 当前配额不足，本次已自动切到 Cloudflare Workers AI 兜底识图。`,
            action: "chat",
            records: [],
            mobileCommand: null,
            source: "workers_ai_vision_fallback",
            version: GATEWAY_VERSION,
          }, 200, corsHeaders);
        } catch (fallbackError) {
          return json({
            reply: `Gemini 识图配额已用完，Workers AI 兜底也暂时失败：${String(fallbackError?.message || fallbackError).slice(0, 160)}。可以稍后再试，或换一个新的 Gemini Key/开启 billing。`,
            action: "chat",
            records: [],
            mobileCommand: null,
            source: "vision_quota_exceeded",
            version: GATEWAY_VERSION,
          }, 200, corsHeaders);
        }
      }
      return json({
        reply: `附件分析失败：${message.slice(0, 180)}`,
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

async function callWorkersVisionFallback(env, body, attachments) {
  if (!env.AI) throw new Error("Workers AI binding is not available");
  const image = attachments.find((item) => item.mimeType.startsWith("image/"));
  if (!image) {
    const textFiles = attachments.filter((item) => TEXT_MIME_RE.test(item.mimeType));
    if (textFiles.length) {
      return textFiles.map((item) => `【${item.name}】\n${decodeText(item.data).slice(0, 4000)}`).join("\n\n");
    }
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
  return [
    `用户问题：${userText}`,
    `上传附件：\n${list}`,
    "请基于附件内容回答。若是截图，重点看界面文字、错误提示和下一步操作；若是图片，描述主体和可见文字；若是文件，总结结构和重点。",
  ].join("\n\n");
}

async function searchPublicNews(rawQuery) {
  const query = cleanSearchQuery(rawQuery) || "科技 新闻";
  const rssUrl = `https://news.google.com/rss/search?q=${encodeURIComponent(query)}&hl=zh-CN&gl=CN&ceid=CN:zh-Hans`;
  try {
    const res = await fetch(rssUrl, { headers: { "user-agent": "ai-ledger-worker/1.0" } });
    if (!res.ok) throw new Error(`RSS HTTP ${res.status}`);
    const xml = await res.text();
    const items = parseRssItems(xml).slice(0, 6);
    if (!items.length) throw new Error("RSS empty");
    return buildSearchResponse(query, items, "rss_web_search");
  } catch (rssError) {
    try {
      const gdeltUrl = `https://api.gdeltproject.org/api/v2/doc/doc?query=${encodeURIComponent(query)}&mode=ArtList&format=json&maxrecords=6&sort=HybridRel`;
      const res = await fetch(gdeltUrl, { headers: { "user-agent": "ai-ledger-worker/1.0" } });
      if (!res.ok) throw new Error(`GDELT HTTP ${res.status}`);
      const data = await res.json();
      const items = (data?.articles || []).map((article) => ({
        title: article.title,
        link: article.url,
        source: article.sourceCountry || article.domain || "GDELT",
        pubDate: article.seendate || "",
      })).filter((item) => item.title && item.link).slice(0, 6);
      if (!items.length) throw new Error("GDELT empty");
      return buildSearchResponse(query, items, "gdelt_web_search");
    } catch (gdeltError) {
      return { ok: false, error: `${String(rssError?.message || rssError)}; ${String(gdeltError?.message || gdeltError)}` };
    }
  }
}

function buildSearchResponse(query, items, source) {
  const lines = items.map((item, index) => `${index + 1}. ${item.title}${item.source ? `（${item.source}）` : ""}${item.pubDate ? ` · ${formatDateText(item.pubDate)}` : ""}`);
  return {
    ok: true,
    reply: `已联网搜索公开新闻源，关键词“${query}”。最近相关结果：\n${lines.join("\n")}\n\n这是公开 RSS/新闻索引结果，后续如果接入 Tavily、Brave 或 Serper，可以升级成更完整的网页搜索和引用。`,
    action: "chat",
    records: [],
    mobileCommand: null,
    webSearchUsed: true,
    webSearchMode: "force",
    sources: items.map((item) => ({ title: item.title, url: item.link, source: item.source, publishedAt: item.pubDate })),
    citations: items.map((item) => item.link),
    source,
    version: GATEWAY_VERSION,
  };
}

function createMissingSearchResponse(error) {
  return {
    reply: `当前强制联网已经触发，但公开 RSS/新闻索引也暂时请求失败：${String(error || "unknown").slice(0, 160)}。如果要更稳定的通用搜索，建议后续接入 TAVILY_API_KEY、BRAVE_SEARCH_API_KEY 或 SERPER_API_KEY。`,
    action: "chat",
    records: [],
    mobileCommand: null,
    webSearchUsed: false,
    webSearchMode: "force",
    sources: [],
    citations: [],
    source: "web_search_error",
    version: GATEWAY_VERSION,
  };
}

function parseRssItems(xml) {
  const blocks = String(xml || "").match(/<item>[\s\S]*?<\/item>/gi) || [];
  return blocks.map((block) => {
    const title = decodeXml(extractXml(block, "title")).replace(/\s+-\s+[^-]+$/u, "");
    const link = decodeXml(extractXml(block, "link"));
    const pubDate = decodeXml(extractXml(block, "pubDate"));
    const sourceMatch = block.match(/<source[^>]*>([\s\S]*?)<\/source>/i);
    const source = sourceMatch ? decodeXml(sourceMatch[1]) : "Google News";
    return { title, link, pubDate, source };
  }).filter((item) => item.title && item.link);
}

function extractXml(block, tag) {
  const match = block.match(new RegExp(`<${tag}[^>]*>([\\s\\S]*?)<\\/${tag}>`, "i"));
  return match ? match[1] : "";
}

function decodeXml(value) {
  return String(value || "")
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .trim();
}

function cleanSearchQuery(text) {
  return String(text || "")
    .replace(/搜索一下|搜一下|查一下|联网|今天的|今天|新闻|最新/gu, " ")
    .replace(/\s+/g, " ")
    .trim() || "科技 新闻";
}

function formatDateText(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value || "").slice(0, 20);
  return date.toISOString().slice(0, 10);
}

function isForcedWebSearch(body) {
  return body?.forceWebSearch === true
    || body?.webSearchMode === "force"
    || body?.searchMode === "force"
    || body?.webSearch?.force === true
    || body?.webSearch?.mode === "force";
}

function isQuotaError(message) {
  return /quota|rate.?limit|billing|exceeded|429/i.test(String(message || ""));
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

function base64ToNumberArray(base64) {
  const binary = atob(base64);
  const bytes = new Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes;
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

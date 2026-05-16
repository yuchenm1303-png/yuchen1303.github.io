import orchestrator from "./index-orchestrator.js";

const DIAGNOSTICS_VERSION = "ai-ledger-orchestrator-diagnostics-v1";
const JSON_HEADERS = { "content-type": "application/json; charset=utf-8" };

export default {
  async fetch(request, env, ctx) {
    const requestForDelegate = request.clone();
    const requestForBody = request.clone();

    const response = await orchestrator.fetch(requestForDelegate, env, ctx);

    if (request.method === "GET") {
      return appendHealthInfo(response);
    }

    if (request.method !== "POST") return response;

    let body = null;
    let payload = null;
    try { body = await requestForBody.json(); } catch { return response; }
    try { payload = await response.clone().json(); } catch { return response; }

    if (!shouldDiagnose(payload)) return response;

    const modelPreference = normalizeModelPreference(body?.modelPreference || body?.aiModelPreference || body?.modelMode);
    const diagnosis = await diagnoseModelFailure(env, modelPreference);
    const diagnosisText = formatDiagnosis(diagnosis);

    const reply = [
      String(payload.reply || "模型调用失败。"),
      "",
      "模型诊断信息：",
      diagnosisText,
    ].join("\n");

    const nextPayload = {
      ...payload,
      reply,
      diagnostics: {
        version: DIAGNOSTICS_VERSION,
        modelPreference,
        results: diagnosis,
      },
      version: payload.version ? `${payload.version} · ${DIAGNOSTICS_VERSION}` : DIAGNOSTICS_VERSION,
    };

    return json(nextPayload, response.status, response.headers);
  },
};

async function appendHealthInfo(response) {
  try {
    const data = await response.clone().json();
    if (!data || typeof data !== "object") return response;
    return json({
      ...data,
      diagnosticsWrapper: {
        ok: true,
        version: DIAGNOSTICS_VERSION,
        behavior: "Only runs extra provider probes when model selection fails.",
      },
    }, response.status, response.headers);
  } catch {
    return response;
  }
}

function shouldDiagnose(payload) {
  const source = String(payload?.source || "").toLowerCase();
  const reply = String(payload?.reply || "");
  return source === "selected_model_failed"
    || source === "provider_pool_failed"
    || /所选模型失败|模型.*没有成功返回|云端 AI 暂时不可用|配额不足|quota|rate limit/i.test(reply);
}

async function diagnoseModelFailure(env, modelPreference = "auto") {
  const targets = diagnosticTargets(env, modelPreference);
  if (!targets.length) {
    return [{ id: modelPreference, ok: false, provider: "Model Picker", model: modelPreference, error: "当前选择的模型没有对应的环境变量或服务绑定。" }];
  }

  const results = [];
  for (const target of targets) {
    try {
      if (target.kind === "gemini") {
        await probeGemini(env, target.model);
        results.push({ ...target, ok: true, message: "诊断请求成功，说明 API Key 和模型基本可用；原请求失败可能是临时限流、上下文过长或内容被拒。" });
      } else if (target.kind === "nvidia") {
        await probeNvidia(env, target.model);
        results.push({ ...target, ok: true, message: "诊断请求成功，说明 NVIDIA API Key、Base URL 和模型 ID 基本可用；原请求失败可能是临时限流或上下文问题。" });
      } else if (target.kind === "workers_ai") {
        await probeWorkersAI(env, target.model);
        results.push({ ...target, ok: true, message: "诊断请求成功，Workers AI 绑定可用。" });
      }
    } catch (error) {
      results.push({
        ...target,
        ok: false,
        error: readableError(error),
      });
    }
  }
  return results;
}

function diagnosticTargets(env, modelPreference) {
  const pref = normalizeModelPreference(modelPreference);
  const all = [];

  if (env.NVIDIA_API_KEY) {
    const kimi = nvidiaKimiModel(env);
    const mistral = nvidiaMistralModel(env);
    if (kimi) all.push({ id: "kimi", kind: "nvidia", provider: "NVIDIA NIM", model: kimi, label: nvidiaModelLabel(kimi) });
    if (mistral && mistral !== kimi) all.push({ id: "mistral", kind: "nvidia", provider: "NVIDIA NIM", model: mistral, label: nvidiaModelLabel(mistral) });
  }

  if (env.GEMINI_API_KEY) {
    const model = geminiModel(env);
    all.push({ id: "gemini", kind: "gemini", provider: "Gemini", model, label: geminiModelLabel(model) });
  }

  if (env.AI) {
    all.push({ id: "workers", kind: "workers_ai", provider: "Cloudflare Workers AI", model: String(env.AI_MODEL || "@cf/meta/llama-3.1-8b-instruct"), label: "Workers AI Llama 3.1 8B" });
  }

  if (pref === "auto") return all;
  return all.filter((item) => item.id === pref);
}

async function probeGemini(env, model) {
  const endpoint = `${env.GEMINI_API_BASE || "https://generativelanguage.googleapis.com/v1beta/models"}/${model}:generateContent?key=${encodeURIComponent(env.GEMINI_API_KEY)}`;
  const res = await fetch(endpoint, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      contents: [{ role: "user", parts: [{ text: "请只回复 OK" }] }],
      generationConfig: { temperature: 0, maxOutputTokens: 8 },
    }),
  });
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new Error(data?.error?.message || `Gemini HTTP ${res.status}`);
  const text = data?.candidates?.[0]?.content?.parts?.map((p) => p.text || "").join("\n").trim();
  if (!text) throw new Error("Gemini 返回为空，可能被限流、被安全策略拦截或响应格式异常。" );
  return text;
}

async function probeNvidia(env, model) {
  const endpoint = `${nvidiaBaseUrl(env)}/chat/completions`;
  const res = await fetch(endpoint, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${env.NVIDIA_API_KEY}` },
    body: JSON.stringify({
      model,
      messages: [
        { role: "system", content: "Reply with OK only." },
        { role: "user", content: "OK?" },
      ],
      temperature: 0,
      max_tokens: 8,
    }),
  });
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new Error(data?.error?.message || data?.message || `NVIDIA HTTP ${res.status}`);
  const text = data?.choices?.[0]?.message?.content || data?.choices?.[0]?.text || "";
  if (!String(text).trim()) throw new Error("NVIDIA 返回为空，可能是模型端点不兼容或响应格式异常。" );
  return text;
}

async function probeWorkersAI(env, model) {
  if (!env.AI) throw new Error("Workers AI binding is not available");
  const result = await env.AI.run(model, {
    messages: [
      { role: "system", content: "Reply with OK only." },
      { role: "user", content: "OK?" },
    ],
    max_tokens: 8,
  });
  const text = String(result?.response || result?.text || "").trim();
  if (!text) throw new Error("Workers AI 返回为空。" );
  return text;
}

function formatDiagnosis(results) {
  return results.map((item) => {
    const head = `- ${item.label || item.model || item.id}：`;
    if (item.ok) return `${head}可用。${item.message || "诊断请求成功。"}`;
    return `${head}不可用。${item.error || "未知错误"}`;
  }).join("\n");
}

function readableError(error) {
  const text = String(error?.message || error || "unknown error")
    .replace(/Bearer\s+[A-Za-z0-9._\-]+/g, "Bearer ***")
    .replace(/key=[A-Za-z0-9._\-]+/g, "key=***")
    .slice(0, 500);
  if (/429|quota|rate limit|exceeded/i.test(text)) return `${text}（通常是额度不足或限流）`;
  if (/401|unauthorized|invalid api key/i.test(text)) return `${text}（通常是 API Key 无效或权限不足）`;
  if (/403|permission|forbidden/i.test(text)) return `${text}（通常是账号没有该模型权限）`;
  if (/404|not found|model/i.test(text)) return `${text}（通常是模型 ID 错误，或该模型不是可调用 endpoint）`;
  if (/400|bad request/i.test(text)) return `${text}（通常是请求格式、模型参数或 endpoint 不兼容）`;
  return text;
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

function pickNvidiaEnvModel(...values) {
  return values.map((value) => String(value || "").trim()).find(Boolean) || "";
}

function nvidiaBaseUrl(env) {
  return String(env.NVIDIA_BASE_URL || "https://integrate.api.nvidia.com/v1").replace(/\/+$/g, "");
}

function nvidiaKimiModel(env) {
  return pickNvidiaEnvModel(
    env.NVIDIA_KIMI_MODEL,
    String(env.NVIDIA_CHAT_MODEL || "").toLowerCase().includes("kimi") ? env.NVIDIA_CHAT_MODEL : "",
    env.NVIDIA_VISION_MODEL && String(env.NVIDIA_VISION_MODEL).toLowerCase().includes("kimi") ? env.NVIDIA_VISION_MODEL : "",
    "moonshotai/kimi-k2.6",
  );
}

function nvidiaMistralModel(env) {
  return pickNvidiaEnvModel(
    env.NVIDIA_MISTRAL_MODEL,
    String(env.NVIDIA_PLANNER_MODEL || "").toLowerCase().includes("mistral") ? env.NVIDIA_PLANNER_MODEL : "",
    String(env.NVIDIA_CHAT_MODEL || "").toLowerCase().includes("mistral") ? env.NVIDIA_CHAT_MODEL : "",
    "mistralai/mistral-medium-3.5-128b",
  );
}

function nvidiaModelLabel(model) {
  const value = String(model || "");
  if (/mistral-medium-3\.5-128b/i.test(value)) return "Mistral Medium 3.5 128B · via NVIDIA NIM";
  if (/kimi/i.test(value)) return `${value} · via NVIDIA NIM`;
  return `${value || "NVIDIA Model"} · via NVIDIA NIM`;
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
  return value || "Gemini";
}

function json(payload, status = 200, headers = {}) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { ...JSON_HEADERS, ...Object.fromEntries(new Headers(headers)) },
  });
}

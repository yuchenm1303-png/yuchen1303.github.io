window.AI_LEDGER_CONFIG = {
  aiEndpoint: "https://ai-ledger-parser.552078638.workers.dev",
  aiTimeoutMs: 30000,
  supabaseUrl: "https://nfzkphjbelyltrzgkdwt.supabase.co",
  supabasePublishableKey: "sb_publishable_tE8SeTOj-ERgmqvP4l5Hiw_arCxCJLa"
};

(() => {
  const PATCH_FLAG = '__cloudErrorNormalizerPatched';
  if (window[PATCH_FLAG]) return;
  window[PATCH_FLAG] = true;

  function isAiPayload(body) {
    return body && typeof body === 'object' && Array.isArray(body.messages) && ('ledgerContext' in body || 'clientTools' in body || 'pendingDraft' in body || 'commandProtocol' in body || 'webSearch' in body);
  }

  function lastUserText(body) {
    if (Array.isArray(body?.messages)) {
      const last = [...body.messages].reverse().find((item) => item?.role === 'user' && String(item?.content || '').trim());
      if (last) return String(last.content || '').trim();
    }
    return String(body?.text || '').trim();
  }

  function needsCloud(text) {
    return /(是什么|什么意思|定义|解释|为什么|怎么|如何|天气|下雨|气温|温度|新闻|最新|搜索|查一下|联网|百科|资料|计算|算一下|等于|[0-9]\s*[+\-×÷*/^]\s*[0-9])/u.test(String(text || ''));
  }

  function friendly(data, body) {
    const text = lastUserText(body);
    const raw = String(data?.reply || data?.error || data?.message || data?.code || '').trim();
    if (/quota|rate.?limit|billing|exceeded|429/i.test(raw)) return '云端 AI 当前配额不足或被限流了。可以稍后再试，或更换/升级 Gemini Key。';
    if (/GEMINI_API_KEY|Gemini|502|503|temporarily|暂时|不可用/i.test(raw)) return '云端 AI 暂时不可用，所以这类普通问答没有成功返回。请稍后再试，或检查 Worker / Gemini 配置。';
    if (needsCloud(text)) return raw ? `云端没有成功返回：${raw.slice(0, 180)}` : '云端没有成功返回。这个问题需要云端模型或工具处理，请稍后再试。';
    return raw || '云端 AI 暂时不可用。';
  }

  const originalFetch = window.fetch.bind(window);
  window.fetch = async (input, init = {}) => {
    let body = null;
    try {
      if (String(init?.method || 'GET').toUpperCase() === 'POST' && typeof init?.body === 'string') {
        const parsed = JSON.parse(init.body);
        if (isAiPayload(parsed)) body = parsed;
      }
    } catch {}

    const response = await originalFetch(input, init);
    if (!body || response.ok) return response;

    let data = null;
    try {
      const text = await response.clone().text();
      data = text ? JSON.parse(text) : null;
    } catch {}

    return new Response(JSON.stringify({
      reply: friendly(data, body),
      action: 'chat',
      records: [],
      mobileCommand: null,
      source: data?.source || 'cloud_error_normalized',
      version: data?.version || 'cloud-error-normalizer-v2'
    }), {
      status: 200,
      statusText: 'OK',
      headers: { 'content-type': 'application/json; charset=utf-8' }
    });
  };

  window.CloudErrorNormalizer = { version: '2026-05-16-config-inline-1' };
})();
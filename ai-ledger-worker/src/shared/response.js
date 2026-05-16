const JSON_HEADERS = { "content-type": "application/json; charset=utf-8" };

export function json(payload, status = 200, headers = {}) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { ...JSON_HEADERS, ...Object.fromEntries(new Headers(headers)) },
  });
}

export function cors(request, env) {
  const origin = request.headers.get("Origin") || "";
  const allowed = String(env.ALLOWED_ORIGINS || "*")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
  const allow = allowed.includes("*") || allowed.includes(origin)
    ? origin || "*"
    : allowed[0] || "*";
  return {
    ...JSON_HEADERS,
    "access-control-allow-origin": allow,
    "access-control-allow-methods": "GET, POST, OPTIONS",
    "access-control-allow-headers": "content-type",
    vary: "Origin",
  };
}

export function optionsResponse(headers) {
  return new Response(null, { status: 204, headers });
}

export { JSON_HEADERS };

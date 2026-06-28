"use strict";

const crypto = require("crypto");

const DEFAULT_MAX_TOKEN_CHARS = 4096;
const DEFAULT_VERIFY_TIMEOUT_MS = 2500;
const DEFAULT_CACHE_TTL_MS = 120000;
const DEFAULT_CACHE_MAX = 256;

function readHeader(headers, name) {
  if (!headers || typeof headers !== "object") return "";
  const lower = String(name || "").toLowerCase();
  const direct = headers[lower] ?? headers[name];
  if (Array.isArray(direct)) return String(direct[0] || "").trim();
  return String(direct || "").trim();
}

function normalizeToken(value, maxTokenChars = DEFAULT_MAX_TOKEN_CHARS) {
  const raw = String(value || "").trim();
  return {
    token: raw.length <= maxTokenChars ? raw : "",
    oversized: raw.length > maxTokenChars,
  };
}

function extractBearerToken(headers, maxTokenChars = DEFAULT_MAX_TOKEN_CHARS) {
  const authorization = readHeader(headers, "authorization");
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  return normalizeToken(match?.[1] || "", maxTokenChars);
}

function looksLikeJwt(value) {
  const token = String(value || "").trim();
  const parts = token.split(".");
  if (parts.length !== 3 || parts.some((part) => !part)) return false;
  return parts.every((part) => /^[A-Za-z0-9_-]+$/.test(part));
}

function timingSafeEqualText(leftValue, rightValue) {
  const left = Buffer.from(String(leftValue || ""));
  const right = Buffer.from(String(rightValue || ""));
  if (left.length === 0 || left.length !== right.length) return false;
  return crypto.timingSafeEqual(left, right);
}

function resolveRequestCredentials(headers, options = {}) {
  const maxTokenChars = Math.max(256, Number(options.maxTokenChars || DEFAULT_MAX_TOKEN_CHARS));
  const appHeader = normalizeToken(readHeader(headers, "x-ai-ledger-token"), maxTokenChars);
  const bearer = extractBearerToken(headers, maxTokenChars);

  if (appHeader.oversized || bearer.oversized) {
    return {
      ok: false,
      error: "oversized_credentials",
      appClientToken: "",
      userAccessToken: "",
      appCredentialSource: "none",
      userCredentialSource: "none",
    };
  }

  let appClientToken = "";
  let userAccessToken = "";
  let appCredentialSource = "none";
  let userCredentialSource = "none";

  if (appHeader.token) {
    appClientToken = appHeader.token;
    appCredentialSource = "x-ai-ledger-token";

    if (bearer.token && !timingSafeEqualText(appHeader.token, bearer.token)) {
      if (!looksLikeJwt(bearer.token)) {
        return {
          ok: false,
          error: "ambiguous_authorization_bearer",
          appClientToken: "",
          userAccessToken: "",
          appCredentialSource: "none",
          userCredentialSource: "none",
        };
      }
      userAccessToken = bearer.token;
      userCredentialSource = "authorization_bearer";
    }
  } else if (bearer.token) {
    if (looksLikeJwt(bearer.token)) {
      userAccessToken = bearer.token;
      userCredentialSource = "authorization_bearer";
    } else {
      appClientToken = bearer.token;
      appCredentialSource = "legacy_authorization_bearer";
    }
  }

  return {
    ok: true,
    error: "",
    appClientToken,
    userAccessToken,
    appCredentialSource,
    userCredentialSource,
  };
}

function validateAppClientCredential(credentials, configuredToken, requireClientAuth = false) {
  if (!credentials?.ok) {
    return { ok: false, status: 401, error: credentials?.error || "unauthorized_client" };
  }

  const expected = String(configuredToken || "").trim();
  if (!expected) {
    return requireClientAuth
      ? { ok: false, status: 503, error: "client_auth_not_configured" }
      : { ok: true, mode: "optional_unconfigured" };
  }

  if (!timingSafeEqualText(credentials.appClientToken, expected)) {
    return { ok: false, status: 401, error: "unauthorized_client" };
  }

  return { ok: true, mode: credentials.appCredentialSource || "token" };
}

function decodeJwtPayload(value) {
  if (!looksLikeJwt(value)) return null;
  try {
    const payload = String(value).split(".")[1];
    return JSON.parse(Buffer.from(payload, "base64url").toString("utf8"));
  } catch (_) {
    return null;
  }
}

function jwtExpiryMillis(value) {
  const exp = Number(decodeJwtPayload(value)?.exp || 0);
  return Number.isFinite(exp) && exp > 0 ? exp * 1000 : 0;
}

function tokenCacheKey(value) {
  return crypto.createHash("sha256").update(String(value || "")).digest("hex");
}

function createSupabaseUserVerifier(options = {}) {
  const supabaseUrl = String(options.supabaseUrl || "").trim().replace(/\/+$/g, "");
  const anonKey = String(options.anonKey || "").trim();
  const fetchImpl = options.fetchImpl || globalThis.fetch;
  const timeoutMs = Math.max(500, Number(options.timeoutMs || DEFAULT_VERIFY_TIMEOUT_MS));
  const cacheTtlMs = Math.max(1000, Number(options.cacheTtlMs || DEFAULT_CACHE_TTL_MS));
  const cacheMax = Math.max(8, Number(options.cacheMax || DEFAULT_CACHE_MAX));
  const now = typeof options.now === "function" ? options.now : Date.now;
  const cache = new Map();

  function pruneCache(nowMs) {
    for (const [key, entry] of cache.entries()) {
      if (!entry || entry.expiresAt <= nowMs || cache.size > cacheMax) cache.delete(key);
      if (cache.size <= cacheMax) break;
    }
  }

  function cacheResult(accessToken, result) {
    const nowMs = now();
    const jwtExpiry = jwtExpiryMillis(accessToken);
    const safeJwtExpiry = jwtExpiry > 0 ? Math.max(nowMs, jwtExpiry - 30000) : nowMs + cacheTtlMs;
    const expiresAt = Math.min(nowMs + cacheTtlMs, safeJwtExpiry);
    if (expiresAt <= nowMs) return;
    cache.set(tokenCacheKey(accessToken), { result, expiresAt });
    pruneCache(nowMs);
  }

  async function verify(accessToken) {
    const token = String(accessToken || "").trim();
    if (!token) return { authenticated: false, status: "anonymous", userId: "", email: "" };
    if (!looksLikeJwt(token)) {
      return {
        authenticated: false,
        status: "invalid",
        userId: "",
        email: "",
        error: "invalid_user_token_format",
      };
    }
    if (!supabaseUrl || !anonKey || typeof fetchImpl !== "function") {
      return {
        authenticated: false,
        status: "unavailable",
        userId: "",
        email: "",
        error: "supabase_auth_not_configured",
      };
    }

    const nowMs = now();
    const key = tokenCacheKey(token);
    const cached = cache.get(key);
    if (cached?.expiresAt > nowMs) return { ...cached.result, cacheHit: true };
    if (cached) cache.delete(key);

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(new Error("supabase_auth_timeout")), timeoutMs);
    try {
      const response = await fetchImpl(`${supabaseUrl}/auth/v1/user`, {
        method: "GET",
        headers: {
          apikey: anonKey,
          authorization: `Bearer ${token}`,
          accept: "application/json",
        },
        signal: controller.signal,
      });

      if (!response?.ok) {
        const result = {
          authenticated: false,
          status: response?.status === 401 || response?.status === 403 ? "invalid" : "unavailable",
          userId: "",
          email: "",
          error: response?.status === 401 || response?.status === 403
            ? "invalid_user_token"
            : `supabase_auth_http_${Number(response?.status || 0)}`,
        };
        if (result.status === "invalid") cacheResult(token, result);
        return result;
      }

      const payload = await response.json();
      const userId = String(payload?.id || "").trim();
      if (!userId) {
        return {
          authenticated: false,
          status: "unavailable",
          userId: "",
          email: "",
          error: "supabase_auth_missing_user_id",
        };
      }

      const result = {
        authenticated: true,
        status: "verified",
        userId,
        email: String(payload?.email || "").trim(),
        error: "",
      };
      cacheResult(token, result);
      return result;
    } catch (error) {
      return {
        authenticated: false,
        status: "unavailable",
        userId: "",
        email: "",
        error: error?.name === "AbortError" || controller.signal.aborted
          ? "supabase_auth_timeout"
          : "supabase_auth_request_failed",
      };
    } finally {
      clearTimeout(timer);
    }
  }

  return Object.freeze({ verify });
}

module.exports = Object.freeze({
  createSupabaseUserVerifier,
  decodeJwtPayload,
  extractBearerToken,
  jwtExpiryMillis,
  looksLikeJwt,
  resolveRequestCredentials,
  timingSafeEqualText,
  tokenCacheKey,
  validateAppClientCredential,
});

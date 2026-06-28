"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  createSupabaseUserVerifier,
  resolveRequestCredentials,
  validateAppClientCredential,
} = require("../src/05-auth-runtime.js");

function jwt(payload = { sub: "user-1", exp: Math.floor(Date.now() / 1000) + 3600 }) {
  const encode = (value) => Buffer.from(JSON.stringify(value)).toString("base64url");
  return `${encode({ alg: "HS256", typ: "JWT" })}.${encode(payload)}.signature`;
}

test("新协议会分离 App Token 与 Supabase JWT", () => {
  const userToken = jwt();
  const credentials = resolveRequestCredentials({
    "x-ai-ledger-token": "app-secret",
    authorization: `Bearer ${userToken}`,
  });

  assert.equal(credentials.ok, true);
  assert.equal(credentials.appClientToken, "app-secret");
  assert.equal(credentials.userAccessToken, userToken);
  assert.equal(credentials.appCredentialSource, "x-ai-ledger-token");
  assert.equal(credentials.userCredentialSource, "authorization_bearer");
});

test("旧客户端把同一个 App Token 放进两个 Header 时保持兼容", () => {
  const credentials = resolveRequestCredentials({
    "x-ai-ledger-token": "legacy-secret",
    authorization: "Bearer legacy-secret",
  });

  assert.equal(credentials.ok, true);
  assert.equal(credentials.appClientToken, "legacy-secret");
  assert.equal(credentials.userAccessToken, "");
  assert.equal(validateAppClientCredential(credentials, "legacy-secret", true).ok, true);
});

test("只有 Authorization 且不是 JWT 时按旧版 App Token 处理", () => {
  const credentials = resolveRequestCredentials({ authorization: "Bearer legacy-only" });
  assert.equal(credentials.appClientToken, "legacy-only");
  assert.equal(credentials.appCredentialSource, "legacy_authorization_bearer");
  assert.equal(credentials.userAccessToken, "");
});

test("App Header 与非 JWT Bearer 不一致时拒绝歧义凭据", () => {
  const credentials = resolveRequestCredentials({
    "x-ai-ledger-token": "app-secret",
    authorization: "Bearer another-app-secret",
  });
  assert.equal(credentials.ok, false);
  assert.equal(credentials.error, "ambiguous_authorization_bearer");
});

test("Supabase 用户验证成功后按令牌摘要缓存", async () => {
  let calls = 0;
  const verifier = createSupabaseUserVerifier({
    supabaseUrl: "https://project.supabase.co",
    anonKey: "anon-key",
    fetchImpl: async () => {
      calls += 1;
      return {
        ok: true,
        status: 200,
        json: async () => ({ id: "user-123", email: "user@example.com" }),
      };
    },
  });
  const accessToken = jwt({ sub: "user-123", exp: Math.floor(Date.now() / 1000) + 3600 });

  const first = await verifier.verify(accessToken);
  const second = await verifier.verify(accessToken);

  assert.equal(first.authenticated, true);
  assert.equal(first.userId, "user-123");
  assert.equal(second.cacheHit, true);
  assert.equal(calls, 1);
});

test("Supabase 401 被标记为无效身份而不是匿名", async () => {
  const verifier = createSupabaseUserVerifier({
    supabaseUrl: "https://project.supabase.co",
    anonKey: "anon-key",
    fetchImpl: async () => ({ ok: false, status: 401, json: async () => ({}) }),
  });

  const result = await verifier.verify(jwt());
  assert.equal(result.authenticated, false);
  assert.equal(result.status, "invalid");
  assert.equal(result.error, "invalid_user_token");
});

test("后端未配置 Supabase 时明确降级为 unavailable", async () => {
  const verifier = createSupabaseUserVerifier({});
  const result = await verifier.verify(jwt());
  assert.equal(result.authenticated, false);
  assert.equal(result.status, "unavailable");
  assert.equal(result.error, "supabase_auth_not_configured");
});

"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const policy = require("../src/memory/anchor-first-policy");

test("explicit profile and confirmed preference are anchors", () => {
  assert.equal(policy.isAnchorCandidate({
    id: "profile-1",
    layer: "profile",
    authority: "user_explicit",
    status: "active",
    content: "stable profile fact",
  }), true);
  assert.equal(policy.isAnchorCandidate({
    id: "preference-1",
    layer: "preference",
    authority: "user_confirmed",
    status: "active",
    content: "stable preference",
  }), true);
});

test("low budget keeps anchors visible to the unified reranker", () => {
  const payload = policy.buildUnifiedRerankPayload({
    currentUserMessage: "question",
    recentConversation: [],
    anchors: [{
      id: "anchor",
      layer: "profile",
      authority: "user_explicit",
      status: "active",
      content: "stable fact",
    }],
    dynamicCandidates: [],
  });
  assert.deepEqual(payload.candidates.map((item) => item.id), ["anchor"]);
  assert.equal(policy.dynamicCandidateLimit("low"), 10);
});

test("anchors are not injected when the reranker selects nothing", () => {
  const candidates = [{
    id: "anchor",
    layer: "profile",
    authority: "user_explicit",
    status: "active",
    content: "unrelated stable fact",
  }];
  assert.deepEqual(policy.applyUnifiedRerankResult(candidates, { selected: [] }), []);
});

test("one rerank protocol supports all memory layers", () => {
  const candidates = [
    ["profile", "profile"],
    ["preference", "preference"],
    ["project", "project"],
    ["episode", "episodic"],
  ].map(([id, layer]) => ({
    id,
    layer,
    authority: "user_explicit",
    status: "active",
    content: `${id} content`,
  }));
  const selected = policy.applyUnifiedRerankResult(candidates, {
    selected: candidates.map((item) => ({ id: item.id, reason: "relevant", confidence: 0.9 })),
  });
  assert.deepEqual(selected.map((item) => item.id).sort(), candidates.map((item) => item.id).sort());
});

test("conflict resolution keeps higher authority", () => {
  const base = {
    layer: "profile",
    namespaceType: "account",
    namespaceId: "account",
    conflictKey: "identity.primary",
    status: "active",
    updatedAt: "2026-01-01T00:00:00Z",
  };
  const items = [
    { ...base, id: "low", authority: "system_inferred", content: "low" },
    { ...base, id: "high", authority: "user_confirmed", content: "high" },
  ];
  assert.deepEqual(policy.resolveConflicts(items).map((item) => item.id), ["high"]);
});

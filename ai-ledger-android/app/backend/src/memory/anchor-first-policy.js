"use strict";

const AUTHORITY_RANK = Object.freeze({
  user_explicit: 4,
  user_confirmed: 3,
  migrated: 2,
  system_inferred: 1,
});

const LAYER_RANK = Object.freeze({
  explicit_core: 6,
  preference: 5,
  project: 4,
  profile: 3,
  session: 2,
  episodic: 1,
});

const VALID_BUDGET_LEVELS = new Set(["low", "standard", "expanded"]);
const HIGH_AUTHORITIES = new Set(["user_explicit", "user_confirmed"]);
const STABLE_ANCHOR_LAYERS = new Set(["profile", "preference"]);

function normalizeText(value, max = 1200) {
  return String(value ?? "")
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, max);
}

function normalizeBudgetLevel(value) {
  const clean = String(value || "standard").trim().toLowerCase();
  return VALID_BUDGET_LEVELS.has(clean) ? clean : "standard";
}

function dynamicCandidateLimit(level, limits = {}) {
  const normalized = normalizeBudgetLevel(level);
  if (normalized === "low") return Math.max(1, Number(limits.low || 10));
  if (normalized === "expanded") return Math.max(1, Number(limits.expanded || 52));
  return Math.max(1, Number(limits.standard || 28));
}

function timestamp(value) {
  const parsed = Date.parse(String(value || ""));
  return Number.isFinite(parsed) ? parsed : 0;
}

function temporallyActive(item, nowMs = Date.now()) {
  if (!item || item.status && item.status !== "active") return false;
  const from = timestamp(item.validFrom || item.valid_from);
  const until = timestamp(item.validUntil || item.valid_until);
  if (from && from > nowMs) return false;
  if (until && until <= nowMs) return false;
  return true;
}

function isAnchorCandidate(item, projectId = "") {
  if (!item || !temporallyActive(item)) return false;
  const layer = String(item.layer || "").trim().toLowerCase();
  const authority = String(item.authority || "").trim().toLowerCase();
  const namespaceType = String(item.namespaceType || item.namespace_type || "account").trim().toLowerCase();
  const namespaceId = String(item.namespaceId || item.namespace_id || "account").trim();

  if (item.pinned === true || layer === "explicit_core") return true;
  if (STABLE_ANCHOR_LAYERS.has(layer) && HIGH_AUTHORITIES.has(authority)) return true;
  return Boolean(
    projectId &&
    namespaceType === "project" &&
    namespaceId === projectId &&
    HIGH_AUTHORITIES.has(authority)
  );
}

function objectiveScore(item) {
  return (
    (item?.pinned ? 1_000_000 : 0) +
    Number(item?.priority || 0) * 100_000 +
    Number(AUTHORITY_RANK[item?.authority] || 0) * 10_000 +
    Number(LAYER_RANK[item?.layer] || 0) * 1_000 +
    Math.round(Number(item?.confidence || 0) * 100)
  );
}

function compareObjective(left, right) {
  const score = objectiveScore(right) - objectiveScore(left);
  if (score) return score;
  return timestamp(right?.updatedAt || right?.updated_at || right?.createdAt || right?.created_at) -
    timestamp(left?.updatedAt || left?.updated_at || left?.createdAt || left?.created_at);
}

function compareConflictPrecedence(left, right) {
  const authority = Number(AUTHORITY_RANK[right?.authority] || 0) - Number(AUTHORITY_RANK[left?.authority] || 0);
  if (authority) return authority;
  const layer = Number(LAYER_RANK[right?.layer] || 0) - Number(LAYER_RANK[left?.layer] || 0);
  if (layer) return layer;
  const priority = Number(right?.priority || 0) - Number(left?.priority || 0);
  if (priority) return priority;
  const confidence = Number(right?.confidence || 0) - Number(left?.confidence || 0);
  if (confidence) return confidence;
  return timestamp(right?.updatedAt || right?.updated_at) - timestamp(left?.updatedAt || left?.updated_at);
}

function mergeCandidateGroups(groups, limit = 72, nowMs = Date.now()) {
  const byId = new Map();
  const byHash = new Map();
  for (const group of Array.isArray(groups) ? groups : []) {
    for (const item of Array.isArray(group) ? group : []) {
      const id = String(item?.id || "").trim();
      const content = normalizeText(item?.content, 2400);
      if (!id || !content || !temporallyActive(item, nowMs)) continue;
      const normalized = { ...item, id, content };
      const current = byId.get(id);
      if (current && compareObjective(current, normalized) <= 0) continue;
      const hash = String(item.contentHash || item.content_hash || "").trim();
      if (hash) {
        const duplicateId = byHash.get(hash);
        const duplicate = duplicateId ? byId.get(duplicateId) : null;
        if (duplicate && compareObjective(duplicate, normalized) <= 0) continue;
        if (duplicate) byId.delete(duplicate.id);
        byHash.set(hash, id);
      }
      byId.set(id, normalized);
    }
  }
  return [...byId.values()].sort(compareObjective).slice(0, Math.max(1, Number(limit || 72)));
}

function resolveConflicts(items) {
  const winners = new Map();
  const passthrough = [];
  for (const item of Array.isArray(items) ? items : []) {
    const conflictKey = String(item?.conflictKey || item?.conflict_key || "").trim();
    if (!conflictKey) {
      passthrough.push(item);
      continue;
    }
    const namespaceType = String(item.namespaceType || item.namespace_type || "account");
    const namespaceId = String(item.namespaceId || item.namespace_id || "account");
    const key = `${namespaceType}:${namespaceId}:${conflictKey}`;
    const current = winners.get(key);
    if (!current || compareConflictPrecedence(item, current) < 0) winners.set(key, item);
  }
  return [...passthrough, ...winners.values()].sort(compareObjective);
}

function buildBudgetGatePrompt({ currentUserMessage, recentConversation, projectId, sessionId }) {
  return [
    "你是 AI Ledger 云端长期记忆的检索预算控制器，只能输出严格 JSON。",
    "高权威 Anchor 候选始终进入统一重排，你无权阻止它们出现。你只决定额外动态语义检索的深度。",
    "low 表示少量动态召回；standard 表示标准动态召回；expanded 表示项目、跨轮历史或会话状态的扩展召回。",
    "必须理解完整语义，禁止依靠固定关键词、正则或领域词表。不要回答用户问题。",
    JSON.stringify({ currentUserMessage, recentConversation, projectId, sessionId }),
  ].join("\n");
}

function buildUnifiedRerankPayload({ currentUserMessage, recentConversation, anchors, dynamicCandidates }) {
  const candidates = resolveConflicts(mergeCandidateGroups([anchors, dynamicCandidates]));
  return {
    currentUserMessage: normalizeText(currentUserMessage, 1800),
    recentConversation: Array.isArray(recentConversation) ? recentConversation : [],
    candidates,
    policy: {
      anchorsAreCandidatesNotMandatory: true,
      singleCloudDecisionOwner: true,
      rejectKeywordSpecialCases: true,
      currentUserMessageHasHighestPriority: true,
    },
  };
}

function applyUnifiedRerankResult(candidates, result, maxItems = 16) {
  const normalized = resolveConflicts(Array.isArray(candidates) ? candidates : []);
  const byId = new Map(normalized.map((item) => [String(item.id), item]));
  const selected = [];
  const seen = new Set();
  const rawSelected = Array.isArray(result?.selected)
    ? result.selected
    : Array.isArray(result?.selectedIds)
      ? result.selectedIds.map((id) => ({ id }))
      : [];

  for (const raw of rawSelected) {
    const id = String(typeof raw === "string" ? raw : raw?.id || "").trim();
    const item = byId.get(id);
    if (!item || seen.has(id)) continue;
    seen.add(id);
    selected.push({
      ...item,
      selectionReason: normalizeText(raw?.reason || "semantic_relevance", 220),
      selectionConfidence: Math.max(0, Math.min(1, Number(raw?.confidence ?? 0.5) || 0.5)),
    });
    if (selected.length >= Math.max(1, Number(maxItems || 16))) break;
  }

  // 关键约束：未被统一重排器选择的 Anchor 不得被后端偷偷补回。
  return resolveConflicts(selected).slice(0, Math.max(1, Number(maxItems || 16)));
}

module.exports = Object.freeze({
  normalizeBudgetLevel,
  dynamicCandidateLimit,
  temporallyActive,
  isAnchorCandidate,
  mergeCandidateGroups,
  resolveConflicts,
  buildBudgetGatePrompt,
  buildUnifiedRerankPayload,
  applyUnifiedRerankResult,
});

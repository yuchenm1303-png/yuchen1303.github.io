#!/usr/bin/env node

/**
 * AI Ledger GUI Provider Mock
 *
 * This tiny HTTP service is used to validate the external GUI provider chain
 * without deploying a real grounding model first.
 *
 * Input:  ai-ledger backend external_http GUI provider payload.
 * Output: compact GUI action JSON accepted by the backend:
 *   { s, a, x, y, b, t, r, q, c, e }
 */
const http = require("http");

const PORT = Number(process.env.PORT || process.env.GUI_PROVIDER_PORT || 9100);
const API_KEY = String(process.env.GUI_PROVIDER_API_KEY || process.env.AGENT_GUI_PROVIDER_API_KEY || "").trim();
const MAX_BODY_BYTES = Number(process.env.MAX_BODY_BYTES || 20 * 1024 * 1024);

const HIGH_RISK_WORDS = [
  "支付", "付款", "转账", "红包", "下单", "购买", "删除", "卸载", "授权", "同意",
  "发送", "发给", "提交", "发布", "评论", "私信", "验证码", "密码", "登录",
  "pay", "transfer", "delete", "send", "submit", "publish", "password", "login", "otp",
];

const TARGET_ALIASES = [
  ["联系人", "通讯录", "好友", "联系人页"],
  ["通讯录", "联系人", "好友"],
  ["发现", "发现页"],
  ["朋友圈", "朋友圈入口", "动态"],
  ["我的", "我", "个人中心", "设置"],
  ["首页", "主页", "home"],
  ["搜索", "搜索框", "搜一搜", "查找"],
  ["自选", "自选股", "关注"],
  ["行情", "市场", "quote", "market"],
  ["热榜", "榜单", "排行", "热门", "热股", "热度"],
  ["资讯", "新闻", "消息"],
  ["交易", "买卖", "trade"],
  ["设置", "setting", "settings"],
  ["返回", "back"],
];

function sendJson(res, status, data) {
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "access-control-allow-origin": "*",
    "access-control-allow-methods": "GET, POST, OPTIONS",
    "access-control-allow-headers": "content-type, authorization, x-ai-ledger-provider",
  });
  res.end(JSON.stringify(data));
}

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    let raw = "";

    req.on("data", (chunk) => {
      raw += chunk;
      if (raw.length > MAX_BODY_BYTES) {
        reject(new Error("body_too_large"));
        req.destroy();
      }
    });

    req.on("end", () => {
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch (error) {
        reject(new Error("invalid_json"));
      }
    });

    req.on("error", reject);
  });
}

function normalizeText(value) {
  return String(value || "")
    .toLowerCase()
    .normalize("NFKC")
    .replace(/[\s\u3000，。,.、:：/\\_\-·・]+/g, "")
    .replace(/app$/i, "")
    .replace(/应用$/u, "");
}

function safeText(value, max = 120) {
  return String(value ?? "").replace(/\s+/g, " ").trim().slice(0, max);
}

function isHighRiskGoal(goal) {
  const clean = normalizeText(goal);
  return HIGH_RISK_WORDS.some((word) => clean.includes(normalizeText(word)));
}

function aliasTermsForGoal(goal) {
  const cleanGoal = normalizeText(goal);
  const terms = new Set();

  if (cleanGoal) terms.add(cleanGoal);

  for (const group of TARGET_ALIASES) {
    const normalizedGroup = group.map(normalizeText).filter(Boolean);
    if (normalizedGroup.some((term) => cleanGoal.includes(term))) {
      group.forEach((term) => terms.add(normalizeText(term)));
    }
  }

  // Remove generic command words so "进入联系人" can still match "通讯录".
  const stripped = cleanGoal
    .replace(/帮我|请|麻烦|一下|这个|那个|软件|应用|app|页面|界面|功能/g, "")
    .replace(/打开|开启|启动|进入|找到|查找|查看|看一下|看|去到|跳到|前往|点击|点开/g, "");
  if (stripped && stripped.length >= 2) terms.add(stripped);

  return Array.from(terms).filter((term) => term.length >= 2).slice(0, 16);
}

function parseBounds(bounds) {
  if (bounds && typeof bounds === "object") {
    const left = Number(bounds.left ?? bounds.l ?? bounds.x1);
    const top = Number(bounds.top ?? bounds.t ?? bounds.y1);
    const right = Number(bounds.right ?? bounds.r ?? bounds.x2);
    const bottom = Number(bounds.bottom ?? bounds.b ?? bounds.y2);
    if ([left, top, right, bottom].every(Number.isFinite) && right > left && bottom > top) {
      return { left, top, right, bottom };
    }
  }

  const parts = String(bounds || "")
    .match(/-?\d+(?:\.\d+)?/g)
    ?.map((item) => Number(item)) || [];

  if (parts.length < 4) return null;
  const [left, top, right, bottom] = parts;
  if (![left, top, right, bottom].every(Number.isFinite)) return null;
  if (right <= left || bottom <= top) return null;
  return { left, top, right, bottom };
}

function clamp01(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return undefined;
  return Math.max(0, Math.min(1, n));
}

function screenSize(screen) {
  const screenshot = screen?.screenshot || {};
  let width = Number(screenshot.displayWidth || screenshot.originalWidth || screenshot.screenWidth || screenshot.width) || 0;
  let height = Number(screenshot.displayHeight || screenshot.originalHeight || screenshot.screenHeight || screenshot.height) || 0;

  if (width > 0 && height > 0) return { width, height };

  const nodes = Array.isArray(screen?.clickableNodes) ? screen.clickableNodes : [];
  for (const node of nodes) {
    const box = parseBounds(node.bounds);
    if (!box) continue;
    width = Math.max(width, box.right);
    height = Math.max(height, box.bottom);
  }

  return {
    width: width > 0 ? width : 1080,
    height: height > 0 ? height : 2400,
  };
}

function normalizedTapForNode(node, screen) {
  const box = parseBounds(node?.bounds);
  if (!box) return null;

  const size = screenSize(screen);
  if (size.width <= 0 || size.height <= 0) return null;

  const left = clamp01(box.left / size.width);
  const top = clamp01(box.top / size.height);
  const right = clamp01(box.right / size.width);
  const bottom = clamp01(box.bottom / size.height);
  const x = clamp01((box.left + box.right) / 2 / size.width);
  const y = clamp01((box.top + box.bottom) / 2 / size.height);

  if (![left, top, right, bottom, x, y].every(Number.isFinite)) return null;
  if (right <= left || bottom <= top) return null;

  return {
    x,
    y,
    box: [left, top, right, bottom],
  };
}

function nodeScore(node, goalTerms, screenTexts) {
  const text = safeText(node?.text || node?.label || node?.contentDescription || "", 80);
  const cleanText = normalizeText(text);
  if (!cleanText || cleanText.length < 1) return 0;

  let score = 0;
  for (const term of goalTerms) {
    if (!term) continue;
    if (cleanText === term) score = Math.max(score, 1400 + term.length);
    else if (cleanText.includes(term)) score = Math.max(score, 1100 + term.length);
    else if (term.includes(cleanText) && cleanText.length >= 2) score = Math.max(score, 900 + cleanText.length);
  }

  // A small bonus for visible navigation words on stable screens.
  if (screenTexts.some((item) => normalizeText(item).includes(cleanText))) score += 20;
  if (node.clickable === false && node.editable !== true) score -= 120;

  return score;
}

function findBestClickableNode(goal, screen) {
  const terms = aliasTermsForGoal(goal);
  const nodes = Array.isArray(screen?.clickableNodes) ? screen.clickableNodes : [];
  const screenTexts = Array.isArray(screen?.texts) ? screen.texts : [];

  let best = null;
  let bestScore = 0;

  for (const node of nodes) {
    const tap = normalizedTapForNode(node, screen);
    if (!tap) continue;

    const score = nodeScore(node, terms, screenTexts);
    if (score > bestScore) {
      best = { node, tap, score };
      bestScore = score;
    }
  }

  return bestScore >= 780 ? best : null;
}

function compactNumber(value) {
  return Number(Number(value).toFixed(4));
}

function buildNeedHelp(screen, reason, confidence = 0.2) {
  return {
    s: "u",
    phase: "反思",
    page: safeText(screen?.currentApp || screen?.packageName || "", 60),
    a: "need_user_help",
    r: "low",
    q: false,
    c: confidence,
    e: safeText(reason, 80),
  };
}

function buildTapPlan(goal, screen, match) {
  const node = match.node;
  const tap = match.tap;
  const text = safeText(node.text || node.label || node.contentDescription || "目标控件", 80);
  const confidence = Math.max(0.56, Math.min(0.82, 0.54 + match.score / 5000));

  return {
    s: "p",
    phase: "决策",
    page: safeText(screen?.currentApp || screen?.packageName || "", 60),
    a: "tap_xy",
    x: compactNumber(tap.x),
    y: compactNumber(tap.y),
    b: tap.box.map(compactNumber),
    t: text,
    r: "low",
    q: false,
    c: compactNumber(confidence),
    e: safeText(`节点命中：${text}`, 40),
  };
}

function planFromPayload(body) {
  const goal = safeText(body?.goal || body?.agentGoal || body?.task || body?.message || "", 240);
  const screen = body?.screen && typeof body.screen === "object" ? body.screen : {};

  if (!goal) return buildNeedHelp(screen, "缺少 goal");
  if (isHighRiskGoal(goal)) return buildNeedHelp(screen, "高风险目标不由 mock provider 自动执行", 0.18);

  const match = findBestClickableNode(goal, screen);
  if (match) return buildTapPlan(goal, screen, match);

  return buildNeedHelp(screen, "未找到可靠可点击目标", 0.24);
}

const server = http.createServer(async (req, res) => {
  try {
    if (req.method === "OPTIONS") return sendJson(res, 204, {});

    if (req.method === "GET") {
      return sendJson(res, 200, {
        ok: true,
        name: "ai-ledger-gui-provider-mock",
        mode: "node_affordance_grounding",
        coordinateSystem: "normalized_full_screenshot_0_1",
      });
    }

    if (req.method !== "POST") return sendJson(res, 405, { ok: false, error: "method_not_allowed" });

    if (API_KEY) {
      const auth = String(req.headers.authorization || "");
      if (auth !== `Bearer ${API_KEY}`) return sendJson(res, 401, { ok: false, error: "unauthorized" });
    }

    const body = await readJsonBody(req);
    return sendJson(res, 200, planFromPayload(body));
  } catch (error) {
    return sendJson(res, 500, { ok: false, error: String(error?.message || error || "provider_failed") });
  }
});

server.listen(PORT, () => {
  console.log(`AI Ledger GUI Provider Mock listening on ${PORT}`);
});

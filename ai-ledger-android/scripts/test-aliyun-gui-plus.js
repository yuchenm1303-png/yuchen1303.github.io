#!/usr/bin/env node
/*
 * Low-risk test for Aliyun GUI Plus compact action parser/provider.
 * Default mock mode does not call any remote API and does not depend on Android build.
 *
 * Usage:
 *   node ai-ledger-android/scripts/test-aliyun-gui-plus.js --mock '{"action":"tap","coordinate":[520,1600],"confidence":0.82}' --width 1080 --height 2400
 *   ALIYUN_GUI_API_KEY=... node ai-ledger-android/scripts/test-aliyun-gui-plus.js --image qq.jpg --goal "点击联系人"
 */

const fs = require("fs");
const path = require("path");

function arg(name, fallback = "") {
  const i = process.argv.indexOf(name);
  return i >= 0 && i + 1 < process.argv.length ? process.argv[i + 1] : fallback;
}

function flag(name) { return process.argv.includes(name); }
function clamp01(value) { const n = Number(value); return Number.isFinite(n) ? Math.max(0, Math.min(1, n)) : 0; }
function safeText(value, max = 160) { return String(value ?? "").replace(/\s+/g, " ").trim().slice(0, max); }

function extractJsonCandidate(text) {
  const raw = String(text || "").trim();
  const fenced = raw.match(/```(?:json)?\s*([\s\S]*?)```/i);
  const candidate = fenced ? fenced[1].trim() : raw;
  try { return JSON.parse(candidate); } catch {}
  const objectMatch = candidate.match(/\{[\s\S]*\}/);
  if (objectMatch) { try { return JSON.parse(objectMatch[0]); } catch {} }
  const arrayMatch = candidate.match(/\[\s*-?\d+(?:\.\d+)?\s*,\s*-?\d+(?:\.\d+)?\s*\]/);
  if (arrayMatch) { try { return JSON.parse(arrayMatch[0]); } catch {} }
  return null;
}

function normalizeActionType(value) {
  const raw = String(value || "").toLowerCase().trim().replace(/[-\s]+/g, "_");
  if (["tap", "click", "press", "point", "coordinate_click", "coordinate_tap", "tap_point", "tap_xy"].includes(raw)) return "tap_xy";
  if (["input", "type", "enter_text", "input_text"].includes(raw)) return "input_text";
  if (["swipe", "scroll", "back", "home", "wait", "finish", "need_user_help"].includes(raw)) return raw;
  if (["none", "unknown", "uncertain", "ask_user", "need_help"].includes(raw)) return "need_user_help";
  return raw || "need_user_help";
}

function normalizePoint(rawX, rawY, screenshotInfo) {
  let x = Number(rawX), y = Number(rawY);
  if (!Number.isFinite(x) || !Number.isFinite(y)) return null;
  const w = Number(screenshotInfo.width) || Number(screenshotInfo.displayWidth) || 0;
  const h = Number(screenshotInfo.height) || Number(screenshotInfo.displayHeight) || 0;
  if (x >= 0 && x <= 1 && y >= 0 && y <= 1) return { x: clamp01(x), y: clamp01(y), source: "normalized" };
  if (x >= 0 && x <= 100 && y >= 0 && y <= 100) return { x: clamp01(x / 100), y: clamp01(y / 100), source: "percent" };
  if (w > 1 && h > 1 && x >= 0 && x <= w + 24 && y >= 0 && y <= h + 24) return { x: clamp01(x / w), y: clamp01(y / h), source: "image_pixel" };
  return null;
}

function compactNeedUserHelp(target, reason, raw = "") {
  return { s: "u", a: "need_user_help", x: null, y: null, t: safeText(target || "目标", 80), c: 0, e: safeText(reason || "无法可靠判断，需要用户帮助。", 220), raw: String(raw || "").slice(0, 1200) };
}

function normalizeGuiProviderOutput(rawOutput, screenshotInfo) {
  const parsed = extractJsonCandidate(rawOutput);
  let action = "", xRaw, yRaw, targetText = "点击目标", confidence = 0, reason = "";
  if (Array.isArray(parsed) && parsed.length >= 2) {
    action = "tap_xy"; xRaw = parsed[0]; yRaw = parsed[1]; confidence = 0.55; reason = "模型返回坐标数组。";
  } else if (parsed && typeof parsed === "object") {
    const nested = parsed.agentStep || parsed.step || parsed.actionStep || parsed.result || parsed;
    const coordinate = nested.coordinate || nested.coordinates || nested.point || nested.position || nested.xy || nested.center;
    action = normalizeActionType(nested.action || nested.type || nested.a || nested.operation || parsed.action || parsed.type);
    if (Array.isArray(coordinate) && coordinate.length >= 2) { xRaw = coordinate[0]; yRaw = coordinate[1]; }
    else if (coordinate && typeof coordinate === "object") { xRaw = coordinate.x ?? coordinate[0]; yRaw = coordinate.y ?? coordinate[1]; }
    xRaw = xRaw ?? nested.x ?? nested.targetX ?? nested.cx ?? nested.centerX ?? parsed.x ?? parsed.targetX;
    yRaw = yRaw ?? nested.y ?? nested.targetY ?? nested.cy ?? nested.centerY ?? parsed.y ?? parsed.targetY;
    targetText = safeText(nested.targetText || nested.label || nested.t || nested.text || parsed.t || "点击目标", 80);
    confidence = clamp01(nested.confidence ?? nested.score ?? nested.c ?? parsed.confidence ?? parsed.c ?? 0);
    reason = safeText(nested.reason || nested.evidence || nested.e || parsed.reason || parsed.e || "GUI Plus predicted clickable coordinate.", 180);
  } else {
    const numbers = String(rawOutput || "").match(/-?\d+(?:\.\d+)?/g)?.map(Number).filter(Number.isFinite) || [];
    if (numbers.length >= 2) { action = "tap_xy"; xRaw = numbers[0]; yRaw = numbers[1]; confidence = 0.35; reason = "从非 JSON 文本中提取到坐标，置信度较低。"; }
  }
  if (action !== "tap_xy") return compactNeedUserHelp(targetText, `第一阶段不自动执行 ${action || "unknown"}。${reason}`, rawOutput);
  const point = normalizePoint(xRaw, yRaw, screenshotInfo);
  if (!point) return compactNeedUserHelp(targetText, "模型没有给出可靠坐标，禁止猜测点击。", rawOutput);
  return { s: "p", a: "tap_xy", x: point.x, y: point.y, t: targetText, c: confidence || 0.6, e: reason || "GUI Plus predicted clickable coordinate.", raw: String(rawOutput || "").slice(0, 1200) };
}

async function callAliyunGuiPlus({ imagePath, goal, width, height }) {
  const key = process.env.ALIYUN_GUI_API_KEY || process.env.QWEN_API_KEY;
  if (!key) throw new Error("ALIYUN_GUI_API_KEY or QWEN_API_KEY is required for live test");
  const base = (process.env.ALIYUN_GUI_BASE_URL || "https://dashscope.aliyuncs.com/compatible-mode/v1").replace(/\/+$/g, "");
  const model = process.env.ALIYUN_GUI_MODEL || "gui-plus-2026-02-26";
  const mimeType = /\.png$/i.test(imagePath) ? "image/png" : "image/jpeg";
  const base64 = fs.readFileSync(imagePath).toString("base64");
  const prompt = [
    "你是一个手机 GUI 操作定位模型。必须只输出 JSON。",
    "坐标使用 0-1 归一化坐标。只允许 tap_xy 或 need_user_help。",
    "输出：{\"action\":\"tap_xy\",\"x\":0.0,\"y\":0.0,\"confidence\":0.0,\"reason\":\"...\"}",
    `任务目标：${goal}`,
  ].join("\n");
  const started = Date.now();
  const res = await fetch(`${base}/chat/completions`, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${key}` },
    body: JSON.stringify({ model, messages: [{ role: "user", content: [{ type: "text", text: prompt }, { type: "image_url", image_url: { url: `data:${mimeType};base64,${base64}` } }] }], temperature: 0, max_tokens: Number(process.env.ALIYUN_GUI_MAX_TOKENS || 512), stream: false }),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${text.slice(0, 300)}`);
  const data = JSON.parse(text);
  const raw = data?.choices?.[0]?.message?.content || data?.choices?.[0]?.text || "";
  const compact = normalizeGuiProviderOutput(raw, { width, height, displayWidth: width, displayHeight: height });
  return { elapsedMs: Date.now() - started, compact, raw };
}

(async () => {
  const width = Number(arg("--width", 1080));
  const height = Number(arg("--height", 2400));
  const mock = arg("--mock", "");
  if (mock || flag("--mock")) {
    const raw = mock || '{"action":"tap","coordinate":[520,1600],"confidence":0.82,"reason":"mock coordinate"}';
    console.log(JSON.stringify(normalizeGuiProviderOutput(raw, { width, height, displayWidth: width, displayHeight: height }), null, 2));
    return;
  }
  const imagePath = arg("--image", "");
  const goal = arg("--goal", "点击目标");
  if (!imagePath) throw new Error("--image is required for live test; use --mock for offline parser test");
  const result = await callAliyunGuiPlus({ imagePath: path.resolve(imagePath), goal, width, height });
  console.log(JSON.stringify(result, null, 2));
})().catch((error) => {
  console.error(error.message || error);
  process.exit(1);
});

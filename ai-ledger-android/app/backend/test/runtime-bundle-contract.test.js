"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { spawnSync } = require("node:child_process");

const {
  EXPECTED_MODULE_FILES,
  moduleMarker,
  splitBundleText,
  joinBundleParts,
  inspectBundleText,
  extractBundle,
  buildBundleTextFromSource,
  verifyBundle,
} = require("../scripts/runtime-bundle");

const backendRoot = path.resolve(__dirname, "..");
const bundlePath = path.join(backendRoot, "aliyun-cn-server-web-data-v1.js");

function actualBundleText() {
  return fs.readFileSync(bundlePath, "utf8");
}

function syntheticBundle(newline = "\n") {
  const preamble = `/* deployment preamble */${newline}`;
  const modules = EXPECTED_MODULE_FILES.map((fileName, index) => {
    const suffix = index + 1 < EXPECTED_MODULE_FILES.length ? newline : "";
    return `${moduleMarker(fileName)}${newline}const module_${index} = ${index};${newline}${suffix}`;
  });
  return preamble + modules.join("");
}

test("运行包只包含约定的八个模块且顺序固定", () => {
  const parts = splitBundleText(actualBundleText());
  assert.deepEqual(parts.modules.map((item) => item.fileName), EXPECTED_MODULE_FILES);
});

test("运行包按模块切分后可以逐字节原样重组", () => {
  const original = actualBundleText();
  const parts = splitBundleText(original);
  assert.equal(joinBundleParts(parts), original);
  const inspection = inspectBundleText(original);
  assert.equal(inspection.modules.length, EXPECTED_MODULE_FILES.length);
  assert.ok(inspection.bytes > 0);
});

test("受保护的视觉智能体主链标识仍然存在", () => {
  const source = actualBundleText();
  for (const token of [
    "three_way_observation_binding",
    "independent_tap_grounding_permit",
    "independent_completion_permit",
    "single_frame_single_action",
    "android_risk_confirmation",
    "fresh_screen_finish_verification",
    "health_and_readiness_endpoints",
  ]) {
    assert.ok(source.includes(token), `missing protected token: ${token}`);
  }
});

test("当前部署运行包通过 Node 语法检查", () => {
  const result = spawnSync(process.execPath, ["--check", bundlePath], { encoding: "utf8" });
  assert.equal(result.status, 0, result.stderr || result.stdout);
});

test("提取、构建和漂移检查保持 LF 与 CRLF 内容完全一致", () => {
  for (const newline of ["\n", "\r\n"]) {
    const temporaryRoot = fs.mkdtempSync(path.join(os.tmpdir(), "ai-ledger-runtime-"));
    const temporaryBundle = path.join(temporaryRoot, "runtime.js");
    const temporarySources = path.join(temporaryRoot, "src");
    const original = syntheticBundle(newline);
    fs.writeFileSync(temporaryBundle, original, "utf8");

    extractBundle({ bundlePath: temporaryBundle, sourceDir: temporarySources });
    assert.equal(buildBundleTextFromSource({ sourceDir: temporarySources }), original);
    assert.doesNotThrow(() => verifyBundle({ bundlePath: temporaryBundle, sourceDir: temporarySources }));

    fs.appendFileSync(path.join(temporarySources, EXPECTED_MODULE_FILES[0]), "// drift\n", "utf8");
    assert.throws(
      () => verifyBundle({ bundlePath: temporaryBundle, sourceDir: temporarySources }),
      /runtime_bundle_drift_detected/
    );
  }
});

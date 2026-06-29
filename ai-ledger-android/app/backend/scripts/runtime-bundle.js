#!/usr/bin/env node
"use strict";

const fs = require("node:fs");
const path = require("node:path");

const BACKEND_ROOT = path.resolve(__dirname, "..");
const DEFAULT_BUNDLE_PATH = path.join(BACKEND_ROOT, "aliyun-cn-server-web-data-v1.js");
const DEFAULT_SOURCE_DIR = path.join(BACKEND_ROOT, "src", "runtime");
const PREAMBLE_FILE = "_preamble.js";
const EXPECTED_MODULE_FILES = Object.freeze([
  "00-config-runtime.js",
  "10-http-provider-runtime.js",
  "20-command-protocol.js",
  "30-visual-contract-runtime.js",
  "40-agent-orchestration.js",
  "50-gui-plus-runtime.js",
  "60-chat-data-tools.js",
  "70-http-server.js",
]);
const MODULE_MARKER_PATTERN = /^\/\/ ===== AI Ledger source module: ([^=\r\n]+\.js) =====\r?$/gm;

function moduleMarker(fileName) {
  return `// ===== AI Ledger source module: ${fileName} =====`;
}

function assertCondition(condition, message) {
  if (!condition) throw new Error(message);
}

function splitBundleText(bundleText) {
  assertCondition(typeof bundleText === "string", "runtime_bundle_not_text");
  assertCondition(bundleText.length > 0, "runtime_bundle_empty");

  const markers = [];
  const matcher = new RegExp(MODULE_MARKER_PATTERN.source, MODULE_MARKER_PATTERN.flags);
  let match;
  while ((match = matcher.exec(bundleText)) !== null) {
    markers.push({ fileName: match[1].trim(), start: match.index });
  }

  assertCondition(markers.length > 0, "runtime_module_markers_missing");
  const actualFiles = markers.map((item) => item.fileName);
  assertCondition(
    JSON.stringify(actualFiles) === JSON.stringify(EXPECTED_MODULE_FILES),
    `runtime_module_order_mismatch:${actualFiles.join(",")}`
  );

  const modules = markers.map((marker, index) => {
    const end = index + 1 < markers.length ? markers[index + 1].start : bundleText.length;
    const content = bundleText.slice(marker.start, end);
    assertCondition(content.startsWith(moduleMarker(marker.fileName)), `runtime_module_marker_invalid:${marker.fileName}`);
    assertCondition(content.length > moduleMarker(marker.fileName).length, `runtime_module_empty:${marker.fileName}`);
    if (index + 1 < markers.length) {
      assertCondition(/(?:\r?\n)$/.test(content), `runtime_module_missing_trailing_newline:${marker.fileName}`);
    }
    return Object.freeze({ fileName: marker.fileName, content });
  });

  const preamble = bundleText.slice(0, markers[0].start);
  return Object.freeze({ preamble, modules: Object.freeze(modules) });
}

function joinBundleParts(parts) {
  assertCondition(parts && Array.isArray(parts.modules), "runtime_parts_invalid");
  return String(parts.preamble || "") + parts.modules.map((item) => String(item.content || "")).join("");
}

function inspectBundleText(bundleText) {
  const parts = splitBundleText(bundleText);
  const rebuilt = joinBundleParts(parts);
  assertCondition(rebuilt === bundleText, "runtime_bundle_roundtrip_mismatch");
  return Object.freeze({
    bytes: Buffer.byteLength(bundleText, "utf8"),
    lines: bundleText.split(/\r?\n/).length,
    preambleBytes: Buffer.byteLength(parts.preamble, "utf8"),
    modules: Object.freeze(parts.modules.map((item) => Object.freeze({
      fileName: item.fileName,
      bytes: Buffer.byteLength(item.content, "utf8"),
      lines: item.content.split(/\r?\n/).length,
    }))),
  });
}

function readUtf8(filePath) {
  return fs.readFileSync(filePath, "utf8");
}

function writeUtf8Atomic(filePath, content) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  const temporaryPath = `${filePath}.tmp-${process.pid}-${Date.now()}`;
  fs.writeFileSync(temporaryPath, content, "utf8");
  fs.renameSync(temporaryPath, filePath);
}

function extractBundle({ bundlePath = DEFAULT_BUNDLE_PATH, sourceDir = DEFAULT_SOURCE_DIR, force = false } = {}) {
  const bundleText = readUtf8(bundlePath);
  const parts = splitBundleText(bundleText);
  fs.mkdirSync(sourceDir, { recursive: true });

  const outputs = [];
  const writeSource = (fileName, content) => {
    const outputPath = path.join(sourceDir, fileName);
    if (!force && fs.existsSync(outputPath)) {
      const current = readUtf8(outputPath);
      assertCondition(current === content, `runtime_source_exists_with_different_content:${fileName}`);
      outputs.push(outputPath);
      return;
    }
    writeUtf8Atomic(outputPath, content);
    outputs.push(outputPath);
  };

  const preamblePath = path.join(sourceDir, PREAMBLE_FILE);
  if (parts.preamble) {
    writeSource(PREAMBLE_FILE, parts.preamble);
  } else if (force && fs.existsSync(preamblePath)) {
    fs.unlinkSync(preamblePath);
  }
  for (const item of parts.modules) writeSource(item.fileName, item.content);

  const rebuilt = buildBundleTextFromSource({ sourceDir });
  assertCondition(rebuilt === bundleText, "runtime_extract_roundtrip_mismatch");
  return Object.freeze(outputs);
}

function buildBundleTextFromSource({ sourceDir = DEFAULT_SOURCE_DIR } = {}) {
  const preamblePath = path.join(sourceDir, PREAMBLE_FILE);
  const preamble = fs.existsSync(preamblePath) ? readUtf8(preamblePath) : "";
  const modules = EXPECTED_MODULE_FILES.map((fileName, index) => {
    const filePath = path.join(sourceDir, fileName);
    assertCondition(fs.existsSync(filePath), `runtime_source_missing:${fileName}`);
    const content = readUtf8(filePath);
    assertCondition(content.startsWith(moduleMarker(fileName)), `runtime_source_marker_invalid:${fileName}`);
    if (index + 1 < EXPECTED_MODULE_FILES.length) {
      assertCondition(/(?:\r?\n)$/.test(content), `runtime_source_missing_trailing_newline:${fileName}`);
    }
    return Object.freeze({ fileName, content });
  });
  const bundleText = joinBundleParts({ preamble, modules });
  splitBundleText(bundleText);
  return bundleText;
}

function buildBundle({ bundlePath = DEFAULT_BUNDLE_PATH, sourceDir = DEFAULT_SOURCE_DIR } = {}) {
  const bundleText = buildBundleTextFromSource({ sourceDir });
  writeUtf8Atomic(bundlePath, bundleText);
  return bundleText;
}

function verifyBundle({ bundlePath = DEFAULT_BUNDLE_PATH, sourceDir = DEFAULT_SOURCE_DIR } = {}) {
  const actual = readUtf8(bundlePath);
  const expected = buildBundleTextFromSource({ sourceDir });
  assertCondition(actual === expected, "runtime_bundle_drift_detected:run_npm_run_runtime_build");
  return inspectBundleText(actual);
}

function parseCliArguments(argv) {
  const args = [...argv];
  const command = args.shift() || "inspect";
  const options = { command, force: false, bundlePath: DEFAULT_BUNDLE_PATH, sourceDir: DEFAULT_SOURCE_DIR };
  while (args.length) {
    const token = args.shift();
    if (token === "--force") options.force = true;
    else if (token === "--bundle") options.bundlePath = path.resolve(args.shift() || "");
    else if (token === "--source-dir") options.sourceDir = path.resolve(args.shift() || "");
    else throw new Error(`runtime_unknown_argument:${token}`);
  }
  return options;
}

function printInspection(result) {
  process.stdout.write(`${JSON.stringify({ ok: true, ...result }, null, 2)}\n`);
}

function runCli(argv = process.argv.slice(2)) {
  const options = parseCliArguments(argv);
  if (options.command === "inspect") {
    printInspection(inspectBundleText(readUtf8(options.bundlePath)));
    return;
  }
  if (options.command === "extract") {
    const outputs = extractBundle(options);
    process.stdout.write(`${JSON.stringify({ ok: true, extracted: outputs }, null, 2)}\n`);
    return;
  }
  if (options.command === "build") {
    const bundleText = buildBundle(options);
    printInspection(inspectBundleText(bundleText));
    return;
  }
  if (options.command === "verify") {
    printInspection(verifyBundle(options));
    return;
  }
  throw new Error(`runtime_unknown_command:${options.command}`);
}

if (require.main === module) {
  try {
    runCli();
  } catch (error) {
    process.stderr.write(`[runtime-bundle] ${String(error?.message || error)}\n`);
    process.exitCode = 1;
  }
}

module.exports = Object.freeze({
  EXPECTED_MODULE_FILES,
  PREAMBLE_FILE,
  moduleMarker,
  splitBundleText,
  joinBundleParts,
  inspectBundleText,
  extractBundle,
  buildBundleTextFromSource,
  buildBundle,
  verifyBundle,
  runCli,
});

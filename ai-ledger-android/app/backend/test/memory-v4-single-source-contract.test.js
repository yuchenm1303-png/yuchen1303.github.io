"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const backendRoot = path.resolve(__dirname, "..");
const appRoot = path.resolve(backendRoot, "..");

function read(relativePath) {
  return fs.readFileSync(path.resolve(appRoot, relativePath), "utf8");
}

test("V4 is the only Android long-term-memory data source", () => {
  const repository = read("src/main/java/com/yuchen/ailedger/data/AssistantMemoryRepository.kt");
  assert.match(repository, /MEMORY_TABLE = "assistant_memory_items_v4"/);
  assert.match(repository, /MEMORY_SETTINGS_TABLE = "assistant_memory_settings"/);
  assert.doesNotMatch(repository, /MEMORY_TABLE = "assistant_memories"/);
  assert.doesNotMatch(repository, /record_assistant_memory_usage"/);
  assert.match(repository, /update_assistant_memory_v4_manual/);
});

test("account memory settings and V4 management RPCs are declared", () => {
  const sql = read("backend/supabase/002_memory_v4_single_source.sql");
  assert.match(sql, /create table if not exists public\.assistant_memory_settings/);
  assert.match(sql, /create or replace function public\.create_assistant_memory_v4_manual/);
  assert.match(sql, /create or replace function public\.update_assistant_memory_v4_manual/);
  assert.match(sql, /create or replace function public\.set_assistant_memory_v4_enabled/);
  assert.match(sql, /create or replace function public\.delete_assistant_memory_v4/);
  assert.match(sql, /create or replace function public\.clear_all_assistant_memories_v4/);
  assert.match(sql, /to_regclass\('public\.assistant_memories'\)/);
});

test("custom instructions do not turn cloud memory on", () => {
  const compiler = read("src/main/java/com/yuchen/ailedger/data/AssistantMemoryCompiler.kt");
  assert.match(compiler, /get\(\) = memoryRequested \|\| memorySnapshot != null/);
  assert.match(compiler, /takeIf \{ requestHasText && it\.isNotBlank\(\) \}/);
});

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

test("Android only reads V4 inventory and delegates semantic mutations to the cloud authority", () => {
  const repository = read("src/main/java/com/yuchen/ailedger/data/AssistantMemoryRepository.kt");
  const management = read("src/main/java/com/yuchen/ailedger/service/AssistantMemoryManagementClient.kt");

  assert.match(repository, /MEMORY_TABLE = "assistant_memory_items_v4"/);
  assert.match(repository, /MEMORY_SETTINGS_TABLE = "assistant_memory_settings"/);
  assert.match(management, /MEMORY_MANAGEMENT_REQUEST_SCHEMA = "ai_ledger_memory_management_request_v1"/);
  assert.match(management, /put\("action", "memory_management_mutation"\)/);
  assert.doesNotMatch(repository, /apply_assistant_memory_mutation_v5/);
  assert.doesNotMatch(repository, /MEMORY_TABLE = "assistant_memories"/);
  assert.doesNotMatch(repository, /record_assistant_memory_usage/);
  assert.doesNotMatch(
    repository,
    /create_assistant_memory_v4_manual|update_assistant_memory_v4_manual|set_assistant_memory_v4_enabled|delete_assistant_memory_v4|clear_all_assistant_memories_v4/,
  );
});

test("account memory settings and V4 compatibility RPCs remain declared", () => {
  const sql = read("backend/supabase/002_memory_v4_single_source.sql");
  assert.match(sql, /create table if not exists public\.assistant_memory_settings/);
  assert.match(sql, /create or replace function public\.create_assistant_memory_v4_manual/);
  assert.match(sql, /create or replace function public\.update_assistant_memory_v4_manual/);
  assert.match(sql, /create or replace function public\.set_assistant_memory_v4_enabled/);
  assert.match(sql, /create or replace function public\.delete_assistant_memory_v4/);
  assert.match(sql, /create or replace function public\.clear_all_assistant_memories_v4/);
  assert.match(sql, /to_regclass\('public\.assistant_memories'\)/);
});

test("archived V4 memories stay editable without implicit reactivation", () => {
  const sql = read("backend/supabase/003_memory_v4_archived_edit.sql");
  assert.match(sql, /status in \('active', 'archived'\)/);
  assert.match(sql, /if v_old\.status = 'archived' then/);
  assert.match(sql, /where id = v_old\.id and user_id = v_user_id and status = 'archived'/);
  assert.match(sql, /'updated', v_source_event_id/);
});

test("authenticated chat never sends local custom-instruction or memory bodies", () => {
  const compiler = read("src/main/java/com/yuchen/ailedger/data/AssistantMemoryCompiler.kt");
  const payloadBuilder = read("src/main/java/com/yuchen/ailedger/service/AiWorkerPayloadBuilder.kt");

  assert.match(compiler, /fun personaConfigJson\(\): JSONObject\? = null/);
  assert.match(compiler, /val memorySnapshot: JSONObject\? = null/);
  assert.doesNotMatch(compiler, /\bmemorySnapshot\s*=\s*(?!null\b)/);
  assert.doesNotMatch(payloadBuilder, /AssistantCustomInstructionsRepository/);
  assert.doesNotMatch(payloadBuilder, /currentInstructionsText|effectiveText/);
});

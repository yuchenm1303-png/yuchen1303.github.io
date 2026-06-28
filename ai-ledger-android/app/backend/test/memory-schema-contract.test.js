"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const sql = fs.readFileSync(
  path.join(__dirname, "../supabase/001_memory_foundation.sql"),
  "utf8",
);

const REQUIRED_TABLES = [
  "assistant_memory_source_events",
  "assistant_memory_items_v4",
  "assistant_memory_versions",
  "assistant_memory_embeddings",
  "assistant_memory_usage_events",
  "assistant_memory_feedback",
  "assistant_memory_session_state",
];

test("长期记忆基础迁移包含全部分层存储表", () => {
  for (const table of REQUIRED_TABLES) {
    assert.match(sql, new RegExp(`create table if not exists public\\.${table}\\b`, "i"));
    assert.match(sql, new RegExp(`alter table public\\.${table} enable row level security`, "i"));
  }
});

test("迁移可重复执行且触发器不会重复创建", () => {
  assert.match(sql, /drop trigger if exists assistant_memory_items_set_updated_at/i);
  assert.match(sql, /drop trigger if exists assistant_memory_embeddings_set_updated_at/i);
  assert.match(sql, /drop trigger if exists assistant_memory_session_state_set_updated_at/i);
});

test("原子替代 RPC 同时锁定旧记忆并建立双向替代关系", () => {
  assert.match(sql, /create or replace function public\.supersede_assistant_memory_atomic/i);
  assert.match(sql, /for update;/i);
  assert.match(sql, /supersedes_id/i);
  assert.match(sql, /superseded_by_id/i);
  assert.match(sql, /status = 'superseded'/i);
});

test("usage 事件以请求和记忆为幂等唯一键", () => {
  assert.match(sql, /unique \(user_id, request_id, memory_id, usage_stage\)/i);
  assert.match(sql, /on conflict \(user_id, request_id, memory_id, usage_stage\) do nothing/i);
  assert.match(sql, /set use_count = m\.use_count \+ 1/i);
});

test("候选召回至少具备命名空间、全文与模糊文本索引", () => {
  assert.match(sql, /assistant_memory_items_active_namespace_idx/i);
  assert.match(sql, /using gin \(search_document\)/i);
  assert.match(sql, /gin_trgm_ops/i);
});

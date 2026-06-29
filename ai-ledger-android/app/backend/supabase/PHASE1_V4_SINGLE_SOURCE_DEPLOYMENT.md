# 长期记忆第一阶段部署说明

目标：让 Android 设置页、用户管理操作和云端聊天召回统一使用 `assistant_memory_items_v4`，旧 `assistant_memories` 仅保留为回滚备份。

## 部署顺序

1. 确认已执行 `001_memory_foundation.sql`。
2. 执行 `002_memory_v4_single_source.sql`。
3. 执行 `003_memory_v4_archived_edit.sql`。
4. 部署云端后端 `v169-v4-single-source-account-settings`。
5. 安装由 `dev-update-1` 最新提交构建的 APK。
6. 登录账号，在设置页重新确认“长期记忆总开关”。开关会写入账号级 `assistant_memory_settings`，之后换设备仍保持一致。

不要删除旧 `assistant_memories` 表。迁移 SQL 可重复执行，旧表在第一阶段仅用于回滚和数据核对，不再参与 App 运行或模型召回。

## 数据验收

### 账号级开关

```sql
select
  user_id,
  memory_enabled,
  auto_memory_enabled,
  history_reference_enabled,
  sensitive_policy,
  updated_at
from public.assistant_memory_settings
order by updated_at desc;
```

### V4 记忆是否为唯一运行数据源

```sql
select
  id,
  user_id,
  layer,
  authority,
  status,
  content,
  version,
  metadata,
  updated_at
from public.assistant_memory_items_v4
order by updated_at desc
limit 100;
```

### 编辑是否保留版本

```sql
select
  memory_id,
  version,
  change_type,
  created_at
from public.assistant_memory_versions
order by created_at desc
limit 100;
```

### 模型是否真实使用记忆

```sql
select
  request_id,
  memory_id,
  usage_stage,
  created_at
from public.assistant_memory_usage_events
order by created_at desc
limit 100;
```

一次正常命中应至少出现：

- `model_injected`
- `answer_completed`

## 功能验收

1. App 新增记忆后，只在 `assistant_memory_items_v4` 创建运行数据。
2. 编辑启用中的记忆：内容变化时旧项变为 `superseded`，新项为 `active`。
3. 编辑已停用记忆：仍保持 `archived`，但版本号和版本历史更新。
4. 停用或删除后，后端不再召回该条记忆。
5. 换设备登录后，总开关读取同一个账号级设置。
6. 关闭长期记忆时，自定义指令仍可独立生效。
7. Android 不再调用旧 `record_assistant_memory_usage`；usage 仅由云端按真实注入阶段记录。

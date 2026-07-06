-- AI Ledger 视觉 Skill 账号资产同步表 v1
-- 只保存用户审核后的 Skill 语义、工作流安全边界和不可变审核快照。
-- 禁止保存原始演示截图、无障碍节点树、Resource ID、录制坐标或运行时输入值。

begin;

create table if not exists public.operation_skill_assets_v1 (
  user_id uuid not null references auth.users(id) on delete cascade,
  workflow_id text not null check (char_length(workflow_id) between 1 and 120),
  owner_device_id text not null check (char_length(owner_device_id) between 1 and 120),
  title text not null default '',
  description text not null default '',
  status text not null check (
    status in ('Intent', 'Compiling', 'ReadyForReview', 'Approved', 'Verified', 'Paused', 'Archived')
  ),
  execution_mode text not null default 'CloudVisual' check (
    execution_mode in ('CloudVisual', 'Deterministic', 'AssistedRepair')
  ),
  app_packages text[] not null default '{}',
  skill_json jsonb not null,
  workflow_json jsonb not null,
  approved_snapshot_json jsonb,
  current_version_number integer check (current_version_number is null or current_version_number > 0),
  source_demonstration_id text,
  content_digest text not null check (char_length(content_digest) between 16 and 128),
  learned_at_millis bigint not null default 0 check (learned_at_millis >= 0),
  local_updated_at_millis bigint not null default 0 check (local_updated_at_millis >= 0),
  approved_at_millis bigint check (approved_at_millis is null or approved_at_millis >= 0),
  deleted_at_millis bigint check (deleted_at_millis is null or deleted_at_millis >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, workflow_id)
);

create table if not exists public.operation_skill_asset_versions_v1 (
  user_id uuid not null references auth.users(id) on delete cascade,
  workflow_id text not null check (char_length(workflow_id) between 1 and 120),
  version_id text not null check (char_length(version_id) between 1 and 160),
  version_number integer not null check (version_number > 0),
  owner_device_id text not null check (char_length(owner_device_id) between 1 and 120),
  snapshot_json jsonb not null,
  skill_json jsonb not null,
  content_digest text not null check (char_length(content_digest) between 16 and 128),
  approved_at_millis bigint not null default 0 check (approved_at_millis >= 0),
  created_at timestamptz not null default now(),
  primary key (user_id, workflow_id, version_number)
);

create index if not exists operation_skill_assets_v1_user_updated_idx
  on public.operation_skill_assets_v1 (user_id, local_updated_at_millis desc);

create index if not exists operation_skill_asset_versions_v1_user_workflow_idx
  on public.operation_skill_asset_versions_v1 (user_id, workflow_id, version_number desc);

create or replace function public.touch_operation_skill_asset_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists operation_skill_assets_v1_touch_updated_at on public.operation_skill_assets_v1;
create trigger operation_skill_assets_v1_touch_updated_at
  before update on public.operation_skill_assets_v1
  for each row execute function public.touch_operation_skill_asset_updated_at();

alter table public.operation_skill_assets_v1 enable row level security;
alter table public.operation_skill_asset_versions_v1 enable row level security;

drop policy if exists operation_skill_assets_v1_select_own on public.operation_skill_assets_v1;
create policy operation_skill_assets_v1_select_own
  on public.operation_skill_assets_v1
  for select
  to authenticated
  using (auth.uid() = user_id);

drop policy if exists operation_skill_assets_v1_insert_own on public.operation_skill_assets_v1;
create policy operation_skill_assets_v1_insert_own
  on public.operation_skill_assets_v1
  for insert
  to authenticated
  with check (auth.uid() = user_id);

drop policy if exists operation_skill_assets_v1_update_own on public.operation_skill_assets_v1;
create policy operation_skill_assets_v1_update_own
  on public.operation_skill_assets_v1
  for update
  to authenticated
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

drop policy if exists operation_skill_assets_v1_delete_own on public.operation_skill_assets_v1;
create policy operation_skill_assets_v1_delete_own
  on public.operation_skill_assets_v1
  for delete
  to authenticated
  using (auth.uid() = user_id);

drop policy if exists operation_skill_asset_versions_v1_select_own on public.operation_skill_asset_versions_v1;
create policy operation_skill_asset_versions_v1_select_own
  on public.operation_skill_asset_versions_v1
  for select
  to authenticated
  using (auth.uid() = user_id);

drop policy if exists operation_skill_asset_versions_v1_insert_own on public.operation_skill_asset_versions_v1;
create policy operation_skill_asset_versions_v1_insert_own
  on public.operation_skill_asset_versions_v1
  for insert
  to authenticated
  with check (auth.uid() = user_id);

drop policy if exists operation_skill_asset_versions_v1_update_own on public.operation_skill_asset_versions_v1;
create policy operation_skill_asset_versions_v1_update_own
  on public.operation_skill_asset_versions_v1
  for update
  to authenticated
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

grant select, insert, update, delete on public.operation_skill_assets_v1 to authenticated;
grant select, insert, update on public.operation_skill_asset_versions_v1 to authenticated;

commit;

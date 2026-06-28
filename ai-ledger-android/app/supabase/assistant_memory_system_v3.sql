-- AI Ledger：分层记忆检索系统 V3
-- 在 Supabase SQL Editor 中完整执行本文件。
-- 可重复执行；保留现有 assistant_memories 与 assistant_custom_instructions 数据。

create extension if not exists pgcrypto;

-- =========================================================
-- 一、确保基础表存在
-- =========================================================

create table if not exists public.assistant_memories (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    content text not null,
    category text not null default 'other',
    priority smallint not null default 1,
    pinned boolean not null default false,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.assistant_custom_instructions (
    user_id uuid primary key references auth.users(id) on delete cascade,
    content text not null default '',
    enabled boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- =========================================================
-- 二、V3 元数据：类型、作用域、时效、来源、置信度和使用情况
-- =========================================================

alter table public.assistant_memories
    add column if not exists scope text not null default 'auto';

alter table public.assistant_memories
    add column if not exists confidence double precision not null default 1.0;

alter table public.assistant_memories
    add column if not exists source_type text not null default 'manual';

alter table public.assistant_memories
    add column if not exists valid_from timestamptz;

alter table public.assistant_memories
    add column if not exists valid_until timestamptz;

alter table public.assistant_memories
    add column if not exists supersedes_id uuid references public.assistant_memories(id) on delete set null;

alter table public.assistant_memories
    add column if not exists status text not null default 'active';

alter table public.assistant_memories
    add column if not exists last_used_at timestamptz;

alter table public.assistant_memories
    add column if not exists use_count bigint not null default 0;

alter table public.assistant_memories
    add column if not exists priority smallint not null default 1;

alter table public.assistant_memories
    add column if not exists pinned boolean not null default false;

update public.assistant_memories
set category = case
    when category is null or trim(category) = '' or category = 'manual' then 'other'
    else lower(category)
end;

update public.assistant_memories
set scope = 'auto'
where scope is null or trim(scope) = '';

update public.assistant_memories
set confidence = greatest(0.0, least(1.0, coalesce(confidence, 1.0))),
    source_type = coalesce(nullif(trim(source_type), ''), 'manual'),
    status = coalesce(nullif(trim(status), ''), 'active'),
    use_count = greatest(0, coalesce(use_count, 0)),
    priority = greatest(0, least(3, coalesce(priority, 1)));

-- =========================================================
-- 三、约束：允许逐步扩展，但阻止明显无效数据
-- =========================================================

do $$
declare
    item record;
begin
    for item in
        select conname
        from pg_constraint
        where conrelid = 'public.assistant_memories'::regclass
          and contype = 'c'
          and pg_get_constraintdef(oid) ilike '%content%'
    loop
        execute format(
            'alter table public.assistant_memories drop constraint %I',
            item.conname
        );
    end loop;
end;
$$;

alter table public.assistant_memories
    add constraint assistant_memories_content_length_v3
    check (
        char_length(trim(content)) >= 1
        and char_length(content) <= 2000
    );

alter table public.assistant_memories
    drop constraint if exists assistant_memories_priority_check;

alter table public.assistant_memories
    add constraint assistant_memories_priority_check
    check (priority between 0 and 3);

alter table public.assistant_memories
    drop constraint if exists assistant_memories_confidence_check;

alter table public.assistant_memories
    add constraint assistant_memories_confidence_check
    check (confidence between 0.0 and 1.0);

alter table public.assistant_memories
    drop constraint if exists assistant_memories_category_check;

alter table public.assistant_memories
    add constraint assistant_memories_category_check
    check (category in (
        'profile',
        'preference',
        'project',
        'rule',
        'skill',
        'episode',
        'reflection',
        'other'
    ));

alter table public.assistant_memories
    drop constraint if exists assistant_memories_scope_check;

alter table public.assistant_memories
    add constraint assistant_memories_scope_check
    check (scope in (
        'auto',
        'global',
        'general',
        'english',
        'android',
        'coding',
        'math',
        'writing',
        'finance',
        'travel'
    ));

alter table public.assistant_memories
    drop constraint if exists assistant_memories_source_type_check;

alter table public.assistant_memories
    add constraint assistant_memories_source_type_check
    check (source_type in (
        'manual',
        'conversation',
        'system_inferred',
        'reflection',
        'migration'
    ));

alter table public.assistant_memories
    drop constraint if exists assistant_memories_status_check;

alter table public.assistant_memories
    add constraint assistant_memories_status_check
    check (status in ('active', 'archived', 'superseded'));

alter table public.assistant_memories
    drop constraint if exists assistant_memories_valid_window_check;

alter table public.assistant_memories
    add constraint assistant_memories_valid_window_check
    check (
        valid_until is null
        or valid_from is null
        or valid_until > valid_from
    );

-- =========================================================
-- 四、检索与管理索引
-- =========================================================

create index if not exists assistant_memories_user_retrieval_v3_idx
on public.assistant_memories (
    user_id,
    status,
    enabled,
    scope,
    pinned desc,
    priority desc,
    updated_at desc
);

create index if not exists assistant_memories_user_validity_v3_idx
on public.assistant_memories (
    user_id,
    valid_until,
    valid_from
)
where status = 'active' and enabled = true;

create index if not exists assistant_memories_user_usage_v3_idx
on public.assistant_memories (
    user_id,
    last_used_at desc,
    use_count desc
);

create index if not exists assistant_memories_supersedes_v3_idx
on public.assistant_memories (supersedes_id)
where supersedes_id is not null;

-- =========================================================
-- 五、更新时间触发器
-- =========================================================

create or replace function public.update_assistant_personalization_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists assistant_memories_updated_at_trigger
on public.assistant_memories;

create trigger assistant_memories_updated_at_trigger
before update on public.assistant_memories
for each row
execute function public.update_assistant_personalization_updated_at();

drop trigger if exists assistant_custom_instructions_updated_at_trigger
on public.assistant_custom_instructions;

create trigger assistant_custom_instructions_updated_at_trigger
before update on public.assistant_custom_instructions
for each row
execute function public.update_assistant_personalization_updated_at();

-- =========================================================
-- 六、批量记录“本次回答实际使用了哪些记忆”
-- security invoker + user_id 条件，不能修改其他账号数据。
-- =========================================================

create or replace function public.record_assistant_memory_usage(memory_ids uuid[])
returns integer
language plpgsql
security invoker
set search_path = ''
as $$
declare
    affected integer;
begin
    update public.assistant_memories
    set last_used_at = now(),
        use_count = use_count + 1
    where user_id = (select auth.uid())
      and id = any(memory_ids)
      and status = 'active';

    get diagnostics affected = row_count;
    return affected;
end;
$$;

revoke all on function public.record_assistant_memory_usage(uuid[]) from public;
grant execute on function public.record_assistant_memory_usage(uuid[]) to authenticated;

-- =========================================================
-- 七、RLS：所有个性化数据严格按账号隔离
-- =========================================================

alter table public.assistant_memories enable row level security;
alter table public.assistant_custom_instructions enable row level security;

revoke all on table public.assistant_memories from anon;
revoke all on table public.assistant_custom_instructions from anon;

grant select, insert, update, delete
on table public.assistant_memories
to authenticated;

grant select, insert, update, delete
on table public.assistant_custom_instructions
to authenticated;

drop policy if exists "用户读取自己的长期记忆"
on public.assistant_memories;

drop policy if exists "用户添加自己的长期记忆"
on public.assistant_memories;

drop policy if exists "用户修改自己的长期记忆"
on public.assistant_memories;

drop policy if exists "用户删除自己的长期记忆"
on public.assistant_memories;

create policy "用户读取自己的长期记忆"
on public.assistant_memories
for select
to authenticated
using ((select auth.uid()) = user_id);

create policy "用户添加自己的长期记忆"
on public.assistant_memories
for insert
to authenticated
with check ((select auth.uid()) = user_id);

create policy "用户修改自己的长期记忆"
on public.assistant_memories
for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

create policy "用户删除自己的长期记忆"
on public.assistant_memories
for delete
to authenticated
using ((select auth.uid()) = user_id);

drop policy if exists "用户读取自己的自定义指令"
on public.assistant_custom_instructions;

drop policy if exists "用户添加自己的自定义指令"
on public.assistant_custom_instructions;

drop policy if exists "用户修改自己的自定义指令"
on public.assistant_custom_instructions;

drop policy if exists "用户删除自己的自定义指令"
on public.assistant_custom_instructions;

create policy "用户读取自己的自定义指令"
on public.assistant_custom_instructions
for select
to authenticated
using ((select auth.uid()) = user_id);

create policy "用户添加自己的自定义指令"
on public.assistant_custom_instructions
for insert
to authenticated
with check ((select auth.uid()) = user_id);

create policy "用户修改自己的自定义指令"
on public.assistant_custom_instructions
for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

create policy "用户删除自己的自定义指令"
on public.assistant_custom_instructions
for delete
to authenticated
using ((select auth.uid()) = user_id);

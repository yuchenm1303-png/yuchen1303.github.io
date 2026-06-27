-- AI Ledger：自定义指令 + 长期记忆 V2
-- 在 Supabase SQL Editor 中完整执行本文件。
-- 可重复执行；会升级现有 assistant_memories 表，并新增 assistant_custom_instructions 表。

create extension if not exists pgcrypto;

-- =========================================================
-- 一、长期记忆表：扩容到单条 2000 字，并增加分类、优先级和置顶
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

alter table public.assistant_memories
    add column if not exists priority smallint not null default 1;

alter table public.assistant_memories
    add column if not exists pinned boolean not null default false;

alter table public.assistant_memories
    alter column category set default 'other';

update public.assistant_memories
set category = 'other'
where category is null or trim(category) = '' or category = 'manual';

update public.assistant_memories
set priority = greatest(0, least(3, coalesce(priority, 1)));

-- 删除旧的 content 长度约束（旧版通常限制为 500 字），再建立 V2 约束。
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
    add constraint assistant_memories_content_length_v2
    check (
        char_length(trim(content)) >= 1
        and char_length(content) <= 2000
    );

alter table public.assistant_memories
    drop constraint if exists assistant_memories_priority_check;

alter table public.assistant_memories
    add constraint assistant_memories_priority_check
    check (priority between 0 and 3);

create index if not exists assistant_memories_user_priority_idx
on public.assistant_memories (
    user_id,
    pinned desc,
    priority desc,
    updated_at desc
);

-- =========================================================
-- 二、大段自定义指令表：每个账号一条，最多 12000 字
-- =========================================================

create table if not exists public.assistant_custom_instructions (
    user_id uuid primary key references auth.users(id) on delete cascade,
    content text not null default '',
    enabled boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint assistant_custom_instructions_content_length
        check (char_length(content) <= 12000)
);

-- =========================================================
-- 三、统一更新时间触发器
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
-- 四、RLS：每个账号只能操作自己的数据
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

-- 长期记忆策略

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

-- 自定义指令策略

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

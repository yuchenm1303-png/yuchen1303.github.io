-- AI Ledger 长期记忆 V4 基础结构。
-- 本文件只建立新表、索引、RLS 与原子 RPC，不删除或改写现有 assistant_memories 数据。

begin;

create extension if not exists pgcrypto with schema extensions;
create extension if not exists pg_trgm with schema extensions;
create extension if not exists vector with schema extensions;

create or replace function public.assistant_memory_set_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create table if not exists public.assistant_memory_source_events (
  id uuid primary key default extensions.gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  namespace_type text not null default 'account'
    check (namespace_type in ('account', 'project', 'session')),
  namespace_id text not null default 'account',
  source_type text not null
    check (source_type in ('manual', 'conversation', 'user_feedback', 'system_inferred', 'reflection', 'migration')),
  event_type text not null default 'statement'
    check (event_type in ('statement', 'instruction', 'preference', 'project_update', 'episode', 'correction', 'deletion')),
  source_ref text not null default '',
  content text not null check (length(btrim(content)) > 0),
  content_hash text not null,
  metadata jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  unique (user_id, source_type, source_ref, content_hash)
);

create table if not exists public.assistant_memory_items_v4 (
  id uuid primary key default extensions.gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  layer text not null
    check (layer in ('explicit_core', 'profile', 'preference', 'project', 'episodic', 'session')),
  authority text not null
    check (authority in ('user_explicit', 'user_confirmed', 'system_inferred', 'migrated')),
  namespace_type text not null default 'account'
    check (namespace_type in ('account', 'project', 'session')),
  namespace_id text not null default 'account',
  subject_key text not null default '',
  conflict_key text not null default '',
  content text not null check (length(btrim(content)) > 0),
  content_hash text not null,
  source_event_id uuid references public.assistant_memory_source_events(id) on delete set null,
  status text not null default 'active'
    check (status in ('active', 'archived', 'superseded', 'deleted')),
  confidence double precision not null default 1.0
    check (confidence >= 0.0 and confidence <= 1.0),
  priority smallint not null default 1
    check (priority between 0 and 3),
  pinned boolean not null default false,
  valid_from timestamptz not null default now(),
  valid_until timestamptz,
  supersedes_id uuid references public.assistant_memory_items_v4(id) on delete set null,
  superseded_by_id uuid references public.assistant_memory_items_v4(id) on delete set null,
  version integer not null default 1 check (version > 0),
  use_count bigint not null default 0 check (use_count >= 0),
  last_used_at timestamptz,
  metadata jsonb not null default '{}'::jsonb,
  search_document tsvector generated always as (
    to_tsvector('simple', coalesce(subject_key, '') || ' ' || coalesce(content, ''))
  ) stored,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (valid_until is null or valid_until > valid_from),
  check (supersedes_id is null or supersedes_id <> id),
  check (superseded_by_id is null or superseded_by_id <> id)
);

create table if not exists public.assistant_memory_versions (
  id uuid primary key default extensions.gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  memory_id uuid not null references public.assistant_memory_items_v4(id) on delete cascade,
  version integer not null check (version > 0),
  snapshot jsonb not null,
  change_type text not null
    check (change_type in ('created', 'updated', 'superseded', 'archived', 'restored', 'deleted', 'migrated')),
  source_event_id uuid references public.assistant_memory_source_events(id) on delete set null,
  created_at timestamptz not null default now(),
  unique (memory_id, version)
);

create table if not exists public.assistant_memory_embeddings (
  memory_id uuid primary key references public.assistant_memory_items_v4(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  embedding_model text not null,
  embedding_dimension integer not null check (embedding_dimension > 0),
  embedding extensions.vector not null,
  content_hash text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.assistant_memory_usage_events (
  id uuid primary key default extensions.gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  request_id text not null check (length(btrim(request_id)) > 0),
  memory_id uuid not null references public.assistant_memory_items_v4(id) on delete cascade,
  usage_stage text not null default 'pack_selected'
    check (usage_stage in ('candidate_recalled', 'pack_selected', 'model_injected', 'answer_completed', 'agent_session')),
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  unique (user_id, request_id, memory_id, usage_stage)
);

create table if not exists public.assistant_memory_feedback (
  id uuid primary key default extensions.gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  memory_id uuid not null references public.assistant_memory_items_v4(id) on delete cascade,
  feedback_type text not null
    check (feedback_type in ('correct', 'incorrect', 'irrelevant', 'outdated', 'sensitive', 'delete_requested')),
  note text not null default '',
  request_id text not null default '',
  created_at timestamptz not null default now()
);

create table if not exists public.assistant_memory_session_state (
  user_id uuid not null references auth.users(id) on delete cascade,
  session_id text not null,
  project_id text not null default '',
  goal_hash text not null default '',
  memory_pack jsonb not null default '{}'::jsonb,
  pack_version text not null default 'memory_pack_v1',
  expires_at timestamptz not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, session_id)
);

create index if not exists assistant_memory_source_events_user_namespace_idx
  on public.assistant_memory_source_events (user_id, namespace_type, namespace_id, occurred_at desc);
create index if not exists assistant_memory_source_events_hash_idx
  on public.assistant_memory_source_events (user_id, content_hash);

create index if not exists assistant_memory_items_active_namespace_idx
  on public.assistant_memory_items_v4 (user_id, namespace_type, namespace_id, layer, updated_at desc)
  where status = 'active';
create index if not exists assistant_memory_items_conflict_idx
  on public.assistant_memory_items_v4 (user_id, namespace_type, namespace_id, conflict_key, authority, valid_from desc)
  where status = 'active' and conflict_key <> '';
create index if not exists assistant_memory_items_source_event_idx
  on public.assistant_memory_items_v4 (source_event_id);
create index if not exists assistant_memory_items_search_idx
  on public.assistant_memory_items_v4 using gin (search_document);
create index if not exists assistant_memory_items_content_trgm_idx
  on public.assistant_memory_items_v4 using gin (content extensions.gin_trgm_ops);
create unique index if not exists assistant_memory_items_active_hash_unique
  on public.assistant_memory_items_v4 (user_id, namespace_type, namespace_id, layer, content_hash)
  where status = 'active';

create index if not exists assistant_memory_versions_memory_idx
  on public.assistant_memory_versions (user_id, memory_id, version desc);
create index if not exists assistant_memory_usage_request_idx
  on public.assistant_memory_usage_events (user_id, request_id, created_at desc);
create index if not exists assistant_memory_feedback_memory_idx
  on public.assistant_memory_feedback (user_id, memory_id, created_at desc);
create index if not exists assistant_memory_session_expiry_idx
  on public.assistant_memory_session_state (expires_at);

drop trigger if exists assistant_memory_items_set_updated_at on public.assistant_memory_items_v4;
create trigger assistant_memory_items_set_updated_at
before update on public.assistant_memory_items_v4
for each row execute function public.assistant_memory_set_updated_at();

drop trigger if exists assistant_memory_embeddings_set_updated_at on public.assistant_memory_embeddings;
create trigger assistant_memory_embeddings_set_updated_at
before update on public.assistant_memory_embeddings
for each row execute function public.assistant_memory_set_updated_at();

drop trigger if exists assistant_memory_session_state_set_updated_at on public.assistant_memory_session_state;
create trigger assistant_memory_session_state_set_updated_at
before update on public.assistant_memory_session_state
for each row execute function public.assistant_memory_set_updated_at();

alter table public.assistant_memory_source_events enable row level security;
alter table public.assistant_memory_items_v4 enable row level security;
alter table public.assistant_memory_versions enable row level security;
alter table public.assistant_memory_embeddings enable row level security;
alter table public.assistant_memory_usage_events enable row level security;
alter table public.assistant_memory_feedback enable row level security;
alter table public.assistant_memory_session_state enable row level security;

do $$
declare
  table_name text;
begin
  foreach table_name in array array[
    'assistant_memory_source_events',
    'assistant_memory_items_v4',
    'assistant_memory_versions',
    'assistant_memory_embeddings',
    'assistant_memory_usage_events',
    'assistant_memory_feedback',
    'assistant_memory_session_state'
  ] loop
    execute format('drop policy if exists %I on public.%I', table_name || '_select_own', table_name);
    execute format('create policy %I on public.%I for select using (auth.uid() = user_id)', table_name || '_select_own', table_name);
    execute format('drop policy if exists %I on public.%I', table_name || '_insert_own', table_name);
    execute format('create policy %I on public.%I for insert with check (auth.uid() = user_id)', table_name || '_insert_own', table_name);
    execute format('drop policy if exists %I on public.%I', table_name || '_update_own', table_name);
    execute format('create policy %I on public.%I for update using (auth.uid() = user_id) with check (auth.uid() = user_id)', table_name || '_update_own', table_name);
    execute format('drop policy if exists %I on public.%I', table_name || '_delete_own', table_name);
    execute format('create policy %I on public.%I for delete using (auth.uid() = user_id)', table_name || '_delete_own', table_name);
  end loop;
end;
$$;

create or replace function public.supersede_assistant_memory_atomic(
  p_old_memory_id uuid,
  p_layer text,
  p_authority text,
  p_namespace_type text,
  p_namespace_id text,
  p_subject_key text,
  p_conflict_key text,
  p_content text,
  p_content_hash text,
  p_source_event_id uuid default null,
  p_confidence double precision default 1.0,
  p_priority smallint default 1,
  p_pinned boolean default false,
  p_valid_from timestamptz default now(),
  p_valid_until timestamptz default null,
  p_metadata jsonb default '{}'::jsonb
)
returns public.assistant_memory_items_v4
language plpgsql
security invoker
set search_path = public, extensions
as $$
declare
  v_user_id uuid := auth.uid();
  v_old public.assistant_memory_items_v4;
  v_new public.assistant_memory_items_v4;
begin
  if v_user_id is null then
    raise exception 'authentication_required' using errcode = '28000';
  end if;

  select * into v_old
  from public.assistant_memory_items_v4
  where id = p_old_memory_id and user_id = v_user_id
  for update;

  if not found then
    raise exception 'memory_not_found' using errcode = 'P0002';
  end if;
  if v_old.status <> 'active' then
    raise exception 'memory_not_active' using errcode = 'P0001';
  end if;

  insert into public.assistant_memory_items_v4 (
    user_id, layer, authority, namespace_type, namespace_id, subject_key, conflict_key,
    content, content_hash, source_event_id, status, confidence, priority, pinned,
    valid_from, valid_until, supersedes_id, version, metadata
  ) values (
    v_user_id, p_layer, p_authority, p_namespace_type, p_namespace_id, coalesce(p_subject_key, ''),
    coalesce(p_conflict_key, ''), btrim(p_content), p_content_hash, p_source_event_id, 'active',
    p_confidence, p_priority, p_pinned, p_valid_from, p_valid_until, v_old.id, v_old.version + 1,
    coalesce(p_metadata, '{}'::jsonb)
  ) returning * into v_new;

  update public.assistant_memory_items_v4
  set status = 'superseded',
      superseded_by_id = v_new.id,
      valid_until = least(coalesce(valid_until, now()), now()),
      updated_at = now()
  where id = v_old.id and user_id = v_user_id;

  insert into public.assistant_memory_versions (
    user_id, memory_id, version, snapshot, change_type, source_event_id
  ) values
  (
    v_user_id,
    v_old.id,
    v_old.version,
    to_jsonb(v_old),
    'superseded',
    v_old.source_event_id
  ),
  (
    v_user_id,
    v_new.id,
    v_new.version,
    to_jsonb(v_new),
    'created',
    v_new.source_event_id
  )
  on conflict (memory_id, version) do nothing;

  return v_new;
end;
$$;

create or replace function public.record_assistant_memory_usage_events(
  p_request_id text,
  p_memory_ids uuid[],
  p_usage_stage text default 'pack_selected',
  p_metadata jsonb default '{}'::jsonb
)
returns table(memory_id uuid)
language sql
security invoker
set search_path = public
as $$
  with valid_memories as (
    select distinct m.id
    from unnest(coalesce(p_memory_ids, array[]::uuid[])) as requested(id)
    join public.assistant_memory_items_v4 m
      on m.id = requested.id
     and m.user_id = auth.uid()
     and m.status = 'active'
    where auth.uid() is not null
      and length(btrim(p_request_id)) > 0
  ),
  inserted as (
    insert into public.assistant_memory_usage_events (
      user_id, request_id, memory_id, usage_stage, metadata
    )
    select auth.uid(), btrim(p_request_id), id, p_usage_stage, coalesce(p_metadata, '{}'::jsonb)
    from valid_memories
    on conflict (user_id, request_id, memory_id, usage_stage) do nothing
    returning assistant_memory_usage_events.memory_id
  ),
  updated as (
    update public.assistant_memory_items_v4 m
    set use_count = m.use_count + 1,
        last_used_at = now(),
        updated_at = now()
    where m.id in (select inserted.memory_id from inserted)
      and m.user_id = auth.uid()
    returning m.id
  )
  select id from updated;
$$;

grant select, insert, update, delete on public.assistant_memory_source_events to authenticated;
grant select, insert, update, delete on public.assistant_memory_items_v4 to authenticated;
grant select, insert, update, delete on public.assistant_memory_versions to authenticated;
grant select, insert, update, delete on public.assistant_memory_embeddings to authenticated;
grant select, insert, update, delete on public.assistant_memory_usage_events to authenticated;
grant select, insert, update, delete on public.assistant_memory_feedback to authenticated;
grant select, insert, update, delete on public.assistant_memory_session_state to authenticated;
grant execute on function public.supersede_assistant_memory_atomic(
  uuid, text, text, text, text, text, text, text, text, uuid,
  double precision, smallint, boolean, timestamptz, timestamptz, jsonb
) to authenticated;
grant execute on function public.record_assistant_memory_usage_events(text, uuid[], text, jsonb) to authenticated;

commit;

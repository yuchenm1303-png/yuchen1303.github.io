-- AI Ledger 长期记忆第一阶段：V4 单一数据源与账号级设置。
-- 依赖 001_memory_foundation.sql；可重复执行，不删除旧 assistant_memories 表。

begin;

create table if not exists public.assistant_memory_settings (
  user_id uuid primary key references auth.users(id) on delete cascade,
  memory_enabled boolean not null default false,
  auto_memory_enabled boolean not null default false,
  history_reference_enabled boolean not null default false,
  sensitive_policy text not null default 'confirm'
    check (sensitive_policy in ('confirm', 'block', 'allow')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists assistant_memory_settings_updated_idx
  on public.assistant_memory_settings (updated_at desc);

drop trigger if exists assistant_memory_settings_set_updated_at on public.assistant_memory_settings;
create trigger assistant_memory_settings_set_updated_at
before update on public.assistant_memory_settings
for each row execute function public.assistant_memory_set_updated_at();

alter table public.assistant_memory_settings enable row level security;

drop policy if exists assistant_memory_settings_select_own on public.assistant_memory_settings;
create policy assistant_memory_settings_select_own
  on public.assistant_memory_settings for select
  using (auth.uid() = user_id);

drop policy if exists assistant_memory_settings_insert_own on public.assistant_memory_settings;
create policy assistant_memory_settings_insert_own
  on public.assistant_memory_settings for insert
  with check (auth.uid() = user_id);

drop policy if exists assistant_memory_settings_update_own on public.assistant_memory_settings;
create policy assistant_memory_settings_update_own
  on public.assistant_memory_settings for update
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

drop policy if exists assistant_memory_settings_delete_own on public.assistant_memory_settings;
create policy assistant_memory_settings_delete_own
  on public.assistant_memory_settings for delete
  using (auth.uid() = user_id);

create or replace function public.create_assistant_memory_v4_manual(
  p_content text,
  p_category text default 'other',
  p_scope text default 'auto',
  p_priority smallint default 1,
  p_pinned boolean default false,
  p_valid_until timestamptz default null,
  p_source_type text default 'manual',
  p_confidence double precision default 1.0
)
returns public.assistant_memory_items_v4
language plpgsql
security invoker
set search_path = public, extensions
as $$
declare
  v_user_id uuid := auth.uid();
  v_content text := btrim(coalesce(p_content, ''));
  v_category text := lower(btrim(coalesce(p_category, 'other')));
  v_scope text := lower(replace(btrim(coalesce(p_scope, 'auto')), '-', '_'));
  v_source_type text := lower(btrim(coalesce(p_source_type, 'manual')));
  v_layer text;
  v_authority text;
  v_hash text;
  v_source_event_id uuid;
  v_item public.assistant_memory_items_v4;
begin
  if v_user_id is null then
    raise exception 'authentication_required' using errcode = '28000';
  end if;
  if length(v_content) = 0 then
    raise exception 'memory_content_required' using errcode = '22023';
  end if;
  if p_valid_until is not null and p_valid_until <= now() then
    raise exception 'memory_valid_until_must_be_future' using errcode = '22023';
  end if;

  v_category := case
    when v_category in ('profile', 'preference', 'project', 'rule', 'skill', 'episode', 'reflection', 'other')
      then v_category
    else 'other'
  end;
  v_scope := case
    when v_scope in ('auto', 'global', 'general', 'english', 'android', 'coding', 'math', 'writing', 'finance', 'travel')
      then v_scope
    else 'auto'
  end;
  v_source_type := case
    when v_source_type in ('manual', 'conversation', 'user_feedback', 'system_inferred', 'reflection', 'migration')
      then v_source_type
    else 'manual'
  end;
  v_layer := case
    when v_category in ('rule', 'skill') then 'explicit_core'
    when v_category = 'profile' then 'profile'
    when v_category = 'preference' then 'preference'
    when v_category = 'project' then 'project'
    when v_category in ('episode', 'reflection') then 'episodic'
    else 'profile'
  end;
  v_authority := case
    when v_source_type = 'system_inferred' then 'system_inferred'
    when v_source_type = 'migration' then 'migrated'
    when v_source_type in ('conversation', 'reflection') then 'user_confirmed'
    else 'user_explicit'
  end;
  v_hash := encode(digest(lower(regexp_replace(v_content, '\s+', ' ', 'g')), 'sha256'), 'hex');

  select * into v_item
  from public.assistant_memory_items_v4
  where user_id = v_user_id
    and namespace_type = 'account'
    and namespace_id = 'account'
    and layer = v_layer
    and content_hash = v_hash
    and status = 'active'
  order by updated_at desc
  limit 1;
  if found then
    return v_item;
  end if;

  insert into public.assistant_memory_source_events (
    user_id, namespace_type, namespace_id, source_type, event_type,
    source_ref, content, content_hash, metadata
  ) values (
    v_user_id, 'account', 'account', v_source_type,
    case
      when v_layer = 'explicit_core' then 'instruction'
      when v_layer = 'preference' then 'preference'
      when v_layer = 'project' then 'project_update'
      when v_layer = 'episodic' then 'episode'
      else 'statement'
    end,
    'android_manual:' || extensions.gen_random_uuid()::text,
    v_content,
    v_hash,
    jsonb_build_object('category', v_category, 'scope', v_scope)
  ) returning id into v_source_event_id;

  insert into public.assistant_memory_items_v4 (
    user_id, layer, authority, namespace_type, namespace_id,
    subject_key, conflict_key, content, content_hash, source_event_id,
    status, confidence, priority, pinned, valid_from, valid_until, metadata
  ) values (
    v_user_id, v_layer, v_authority, 'account', 'account',
    v_scope,
    case when v_layer in ('explicit_core', 'preference') then v_layer || ':' || v_scope else '' end,
    v_content, v_hash, v_source_event_id,
    'active', greatest(0.0, least(1.0, coalesce(p_confidence, 1.0))),
    greatest(0, least(3, coalesce(p_priority, 1))),
    coalesce(p_pinned, false), now(), p_valid_until,
    jsonb_build_object(
      'category', v_category,
      'scope', v_scope,
      'source_type', v_source_type,
      'managed_by', 'android_v4'
    )
  ) returning * into v_item;

  insert into public.assistant_memory_versions (
    user_id, memory_id, version, snapshot, change_type, source_event_id
  ) values (
    v_user_id, v_item.id, v_item.version, to_jsonb(v_item), 'created', v_source_event_id
  ) on conflict (memory_id, version) do nothing;

  return v_item;
end;
$$;

create or replace function public.update_assistant_memory_v4_manual(
  p_memory_id uuid,
  p_content text,
  p_category text default 'other',
  p_scope text default 'auto',
  p_priority smallint default 1,
  p_pinned boolean default false,
  p_valid_until timestamptz default null
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
  v_content text := btrim(coalesce(p_content, ''));
  v_category text := lower(btrim(coalesce(p_category, 'other')));
  v_scope text := lower(replace(btrim(coalesce(p_scope, 'auto')), '-', '_'));
  v_layer text;
  v_hash text;
  v_source_event_id uuid;
  v_metadata jsonb;
begin
  if v_user_id is null then
    raise exception 'authentication_required' using errcode = '28000';
  end if;
  if length(v_content) = 0 then
    raise exception 'memory_content_required' using errcode = '22023';
  end if;
  if p_valid_until is not null and p_valid_until <= now() then
    raise exception 'memory_valid_until_must_be_future' using errcode = '22023';
  end if;

  select * into v_old
  from public.assistant_memory_items_v4
  where id = p_memory_id and user_id = v_user_id and status = 'active'
  for update;
  if not found then
    raise exception 'memory_not_active' using errcode = 'P0002';
  end if;

  v_category := case
    when v_category in ('profile', 'preference', 'project', 'rule', 'skill', 'episode', 'reflection', 'other')
      then v_category
    else 'other'
  end;
  v_scope := case
    when v_scope in ('auto', 'global', 'general', 'english', 'android', 'coding', 'math', 'writing', 'finance', 'travel')
      then v_scope
    else 'auto'
  end;
  v_layer := case
    when v_category in ('rule', 'skill') then 'explicit_core'
    when v_category = 'profile' then 'profile'
    when v_category = 'preference' then 'preference'
    when v_category = 'project' then 'project'
    when v_category in ('episode', 'reflection') then 'episodic'
    else 'profile'
  end;
  v_hash := encode(digest(lower(regexp_replace(v_content, '\s+', ' ', 'g')), 'sha256'), 'hex');
  v_metadata := coalesce(v_old.metadata, '{}'::jsonb) || jsonb_build_object(
    'category', v_category,
    'scope', v_scope,
    'source_type', 'manual',
    'managed_by', 'android_v4'
  );

  insert into public.assistant_memory_source_events (
    user_id, namespace_type, namespace_id, source_type, event_type,
    source_ref, content, content_hash, metadata
  ) values (
    v_user_id, v_old.namespace_type, v_old.namespace_id, 'manual', 'correction',
    'android_edit:' || extensions.gen_random_uuid()::text,
    v_content, v_hash,
    jsonb_build_object('memory_id', p_memory_id, 'category', v_category, 'scope', v_scope)
  ) returning id into v_source_event_id;

  if v_old.content_hash = v_hash and v_old.layer = v_layer then
    update public.assistant_memory_items_v4
    set content = v_content,
        source_event_id = v_source_event_id,
        subject_key = v_scope,
        conflict_key = case when v_layer in ('explicit_core', 'preference') then v_layer || ':' || v_scope else '' end,
        priority = greatest(0, least(3, coalesce(p_priority, 1))),
        pinned = coalesce(p_pinned, false),
        valid_until = p_valid_until,
        version = version + 1,
        metadata = v_metadata,
        updated_at = now()
    where id = v_old.id and user_id = v_user_id
    returning * into v_new;

    insert into public.assistant_memory_versions (
      user_id, memory_id, version, snapshot, change_type, source_event_id
    ) values (
      v_user_id, v_new.id, v_new.version, to_jsonb(v_new), 'updated', v_source_event_id
    ) on conflict (memory_id, version) do nothing;
    return v_new;
  end if;

  select * into v_new
  from public.supersede_assistant_memory_atomic(
    p_memory_id,
    v_layer,
    'user_explicit',
    v_old.namespace_type,
    v_old.namespace_id,
    v_scope,
    case when v_layer in ('explicit_core', 'preference') then v_layer || ':' || v_scope else '' end,
    v_content,
    v_hash,
    v_source_event_id,
    v_old.confidence,
    greatest(0, least(3, coalesce(p_priority, 1)))::smallint,
    coalesce(p_pinned, false),
    now(),
    p_valid_until,
    v_metadata
  );
  return v_new;
end;
$$;

create or replace function public.set_assistant_memory_v4_enabled(
  p_memory_id uuid,
  p_enabled boolean
)
returns public.assistant_memory_items_v4
language plpgsql
security invoker
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_item public.assistant_memory_items_v4;
  v_target_status text := case when coalesce(p_enabled, false) then 'active' else 'archived' end;
begin
  if v_user_id is null then
    raise exception 'authentication_required' using errcode = '28000';
  end if;

  update public.assistant_memory_items_v4
  set status = v_target_status,
      valid_until = case
        when v_target_status = 'active' and valid_until is not null and valid_until <= now() then null
        else valid_until
      end,
      version = version + 1,
      updated_at = now()
  where id = p_memory_id
    and user_id = v_user_id
    and status in ('active', 'archived')
  returning * into v_item;

  if not found then
    raise exception 'memory_not_found' using errcode = 'P0002';
  end if;

  insert into public.assistant_memory_versions (
    user_id, memory_id, version, snapshot, change_type, source_event_id
  ) values (
    v_user_id, v_item.id, v_item.version, to_jsonb(v_item),
    case when v_target_status = 'active' then 'restored' else 'archived' end,
    v_item.source_event_id
  ) on conflict (memory_id, version) do nothing;

  return v_item;
end;
$$;

create or replace function public.delete_assistant_memory_v4(
  p_memory_id uuid
)
returns public.assistant_memory_items_v4
language plpgsql
security invoker
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_item public.assistant_memory_items_v4;
begin
  if v_user_id is null then
    raise exception 'authentication_required' using errcode = '28000';
  end if;

  update public.assistant_memory_items_v4
  set status = 'deleted',
      valid_until = least(coalesce(valid_until, now()), now()),
      version = version + 1,
      updated_at = now()
  where id = p_memory_id
    and user_id = v_user_id
    and status <> 'deleted'
  returning * into v_item;

  if not found then
    raise exception 'memory_not_found' using errcode = 'P0002';
  end if;

  insert into public.assistant_memory_versions (
    user_id, memory_id, version, snapshot, change_type, source_event_id
  ) values (
    v_user_id, v_item.id, v_item.version, to_jsonb(v_item), 'deleted', v_item.source_event_id
  ) on conflict (memory_id, version) do nothing;

  return v_item;
end;
$$;

create or replace function public.clear_all_assistant_memories_v4()
returns integer
language plpgsql
security invoker
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_count integer := 0;
begin
  if v_user_id is null then
    raise exception 'authentication_required' using errcode = '28000';
  end if;

  with updated as (
    update public.assistant_memory_items_v4
    set status = 'deleted',
        valid_until = least(coalesce(valid_until, now()), now()),
        version = version + 1,
        updated_at = now()
    where user_id = v_user_id and status in ('active', 'archived')
    returning *
  ), versioned as (
    insert into public.assistant_memory_versions (
      user_id, memory_id, version, snapshot, change_type, source_event_id
    )
    select v_user_id, id, version, to_jsonb(updated), 'deleted', source_event_id
    from updated
    on conflict (memory_id, version) do nothing
    returning 1
  )
  select count(*) into v_count from updated;

  return v_count;
end;
$$;

-- 将旧 V3 数据一次性、幂等地复制到 V4；旧表仅作为回滚备份，不再参与运行。
do $$
begin
  if to_regclass('public.assistant_memories') is not null then
    execute $migration$
      insert into public.assistant_memory_source_events (
        user_id, namespace_type, namespace_id, source_type, event_type,
        source_ref, content, content_hash, metadata, occurred_at, created_at
      )
      select
        m.user_id,
        'account',
        'account',
        'migration',
        case
          when m.category in ('rule', 'skill') then 'instruction'
          when m.category = 'preference' then 'preference'
          when m.category = 'project' then 'project_update'
          when m.category in ('episode', 'reflection') then 'episode'
          else 'statement'
        end,
        'assistant_memories:' || m.id::text,
        btrim(m.content),
        encode(extensions.digest(lower(regexp_replace(btrim(m.content), '\s+', ' ', 'g')), 'sha256'), 'hex'),
        jsonb_build_object(
          'legacy_memory_id', m.id::text,
          'category', coalesce(m.category, 'other'),
          'scope', coalesce(m.scope, 'auto'),
          'source_type', coalesce(m.source_type, 'migration')
        ),
        coalesce(m.updated_at, m.created_at, now()),
        coalesce(m.created_at, now())
      from public.assistant_memories m
      where length(btrim(coalesce(m.content, ''))) > 0
      on conflict (user_id, source_type, source_ref, content_hash) do nothing
    $migration$;

    execute $migration$
      insert into public.assistant_memory_items_v4 (
        user_id, layer, authority, namespace_type, namespace_id,
        subject_key, conflict_key, content, content_hash, source_event_id,
        status, confidence, priority, pinned, valid_from, valid_until,
        supersedes_id, version, use_count, last_used_at, metadata, created_at, updated_at
      )
      select
        m.user_id,
        case
          when m.category in ('rule', 'skill') then 'explicit_core'
          when m.category = 'profile' then 'profile'
          when m.category = 'preference' then 'preference'
          when m.category = 'project' then 'project'
          when m.category in ('episode', 'reflection') then 'episodic'
          else 'profile'
        end,
        case
          when m.source_type = 'system_inferred' then 'system_inferred'
          when m.source_type in ('conversation', 'reflection') then 'user_confirmed'
          else 'migrated'
        end,
        'account',
        'account',
        coalesce(nullif(m.scope, ''), 'auto'),
        case
          when m.category in ('rule', 'skill', 'preference')
            then coalesce(m.category, 'other') || ':' || coalesce(nullif(m.scope, ''), 'auto')
          else ''
        end,
        btrim(m.content),
        encode(extensions.digest(lower(regexp_replace(btrim(m.content), '\s+', ' ', 'g')), 'sha256'), 'hex'),
        e.id,
        case
          when coalesce(m.status, 'active') = 'superseded' then 'superseded'
          when coalesce(m.enabled, true) and coalesce(m.status, 'active') = 'active' then 'active'
          else 'archived'
        end,
        greatest(0.0, least(1.0, coalesce(m.confidence, 1.0))),
        greatest(0, least(3, coalesce(m.priority, 1))),
        coalesce(m.pinned, false),
        coalesce(m.valid_from, m.created_at, now()),
        m.valid_until,
        null,
        1,
        greatest(0, coalesce(m.use_count, 0)),
        m.last_used_at,
        jsonb_build_object(
          'legacy_memory_id', m.id::text,
          'category', coalesce(m.category, 'other'),
          'scope', coalesce(m.scope, 'auto'),
          'source_type', coalesce(m.source_type, 'migration'),
          'managed_by', 'v3_migration'
        ),
        coalesce(m.created_at, now()),
        coalesce(m.updated_at, m.created_at, now())
      from public.assistant_memories m
      join public.assistant_memory_source_events e
        on e.user_id = m.user_id
       and e.source_type = 'migration'
       and e.source_ref = 'assistant_memories:' || m.id::text
      where length(btrim(coalesce(m.content, ''))) > 0
        and not exists (
          select 1
          from public.assistant_memory_items_v4 v
          where v.user_id = m.user_id
            and v.metadata ->> 'legacy_memory_id' = m.id::text
        )
      on conflict do nothing
    $migration$;

    insert into public.assistant_memory_versions (
      user_id, memory_id, version, snapshot, change_type, source_event_id
    )
    select
      v.user_id,
      v.id,
      v.version,
      to_jsonb(v),
      'migrated',
      v.source_event_id
    from public.assistant_memory_items_v4 v
    where v.metadata ->> 'managed_by' = 'v3_migration'
    on conflict (memory_id, version) do nothing;
  end if;
end;
$$;

grant select, insert, update, delete on public.assistant_memory_settings to authenticated;
grant execute on function public.create_assistant_memory_v4_manual(
  text, text, text, smallint, boolean, timestamptz, text, double precision
) to authenticated;
grant execute on function public.update_assistant_memory_v4_manual(
  uuid, text, text, text, smallint, boolean, timestamptz
) to authenticated;
grant execute on function public.set_assistant_memory_v4_enabled(uuid, boolean) to authenticated;
grant execute on function public.delete_assistant_memory_v4(uuid) to authenticated;
grant execute on function public.clear_all_assistant_memories_v4() to authenticated;

commit;

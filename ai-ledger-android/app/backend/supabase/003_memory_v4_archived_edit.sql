-- AI Ledger 长期记忆第一阶段补充：归档记忆可安全编辑且保持归档状态。
-- 依赖 001_memory_foundation.sql 与 002_memory_v4_single_source.sql。

begin;

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
  where id = p_memory_id
    and user_id = v_user_id
    and status in ('active', 'archived')
  for update;
  if not found then
    raise exception 'memory_not_editable' using errcode = 'P0002';
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
    jsonb_build_object(
      'memory_id', p_memory_id,
      'previous_status', v_old.status,
      'category', v_category,
      'scope', v_scope
    )
  ) returning id into v_source_event_id;

  -- 归档记忆不参与模型召回，因此在原行上更新即可；保留 archived 状态和版本快照。
  if v_old.status = 'archived' then
    update public.assistant_memory_items_v4
    set layer = v_layer,
        authority = 'user_explicit',
        subject_key = v_scope,
        conflict_key = case when v_layer in ('explicit_core', 'preference') then v_layer || ':' || v_scope else '' end,
        content = v_content,
        content_hash = v_hash,
        source_event_id = v_source_event_id,
        priority = greatest(0, least(3, coalesce(p_priority, 1))),
        pinned = coalesce(p_pinned, false),
        valid_until = p_valid_until,
        version = version + 1,
        metadata = v_metadata,
        updated_at = now()
    where id = v_old.id and user_id = v_user_id and status = 'archived'
    returning * into v_new;

    insert into public.assistant_memory_versions (
      user_id, memory_id, version, snapshot, change_type, source_event_id
    ) values (
      v_user_id, v_new.id, v_new.version, to_jsonb(v_new), 'updated', v_source_event_id
    ) on conflict (memory_id, version) do nothing;
    return v_new;
  end if;

  -- 只调整元数据时保留同一 active ID；内容或层级变化时创建新版本并原子替代旧版本。
  if v_old.content_hash = v_hash and v_old.layer = v_layer then
    update public.assistant_memory_items_v4
    set content = v_content,
        authority = 'user_explicit',
        source_event_id = v_source_event_id,
        subject_key = v_scope,
        conflict_key = case when v_layer in ('explicit_core', 'preference') then v_layer || ':' || v_scope else '' end,
        priority = greatest(0, least(3, coalesce(p_priority, 1))),
        pinned = coalesce(p_pinned, false),
        valid_until = p_valid_until,
        version = version + 1,
        metadata = v_metadata,
        updated_at = now()
    where id = v_old.id and user_id = v_user_id and status = 'active'
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

grant execute on function public.update_assistant_memory_v4_manual(
  uuid, text, text, text, smallint, boolean, timestamptz
) to authenticated;

commit;

begin;

create index if not exists assistant_memory_items_subject_trgm_idx
  on public.assistant_memory_items_v4
  using gin (subject_key extensions.gin_trgm_ops)
  where status = 'active';

create index if not exists assistant_memory_items_anchor_idx
  on public.assistant_memory_items_v4 (
    user_id, authority, layer, pinned, priority desc, updated_at desc
  )
  where status = 'active';

create or replace function public.get_assistant_memory_anchor_candidates_v4(
  p_project_id text default '',
  p_limit integer default 28
)
returns setof public.assistant_memory_items_v4
language sql
stable
security invoker
set search_path = public, extensions
as $$
  select m.*
  from public.assistant_memory_items_v4 m
  where m.user_id = auth.uid()
    and m.status = 'active'
    and m.valid_from <= now()
    and (m.valid_until is null or m.valid_until > now())
    and (
      m.layer = 'explicit_core'
      or m.pinned = true
      or (
        m.layer in ('profile', 'preference')
        and m.authority in ('user_explicit', 'user_confirmed')
      )
      or (
        btrim(coalesce(p_project_id, '')) <> ''
        and m.namespace_type = 'project'
        and m.namespace_id = btrim(p_project_id)
        and m.authority in ('user_explicit', 'user_confirmed')
      )
    )
  order by
    m.pinned desc,
    case m.authority
      when 'user_explicit' then 4
      when 'user_confirmed' then 3
      when 'migrated' then 2
      else 1
    end desc,
    m.priority desc,
    m.updated_at desc
  limit greatest(1, least(64, coalesce(p_limit, 28)));
$$;

grant execute on function public.get_assistant_memory_anchor_candidates_v4(text, integer)
  to authenticated;

commit;

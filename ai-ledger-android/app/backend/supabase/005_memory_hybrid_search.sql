begin;

create or replace function public.search_assistant_memory_candidates_v4(
  p_query text,
  p_project_id text default '',
  p_session_id text default '',
  p_budget_level text default 'standard',
  p_query_embedding extensions.vector default null,
  p_embedding_model text default '',
  p_limit integer default 28
)
returns setof public.assistant_memory_items_v4
language sql
stable
security invoker
set search_path = public, extensions
as $$
  with params as (
    select
      btrim(coalesce(p_query, '')) as query_text,
      btrim(coalesce(p_project_id, '')) as project_id,
      btrim(coalesce(p_session_id, '')) as session_id,
      greatest(1, least(120, coalesce(p_limit, 28))) as result_limit
  ),
  eligible as (
    select
      m.*,
      case
        when p.query_text = '' then 0.0
        else ts_rank_cd(
          m.search_document,
          websearch_to_tsquery('simple', p.query_text)
        )::double precision
      end as fts_score,
      case
        when p.query_text = '' then 0.0
        else greatest(
          extensions.similarity(m.content, p.query_text),
          extensions.similarity(m.subject_key, p.query_text)
        )::double precision
      end as trigram_score,
      case
        when p_query_embedding is not null
          and e.memory_id is not null
          and e.embedding_dimension = extensions.vector_dims(p_query_embedding)
          and (
            btrim(coalesce(p_embedding_model, '')) = ''
            or e.embedding_model = btrim(p_embedding_model)
          )
        then greatest(0.0, 1.0 - (e.embedding <=> p_query_embedding))::double precision
        else 0.0
      end as vector_score,
      p.query_text,
      p.result_limit
    from public.assistant_memory_items_v4 m
    cross join params p
    left join public.assistant_memory_embeddings e
      on e.memory_id = m.id
     and e.user_id = m.user_id
     and e.content_hash = m.content_hash
    where m.user_id = auth.uid()
      and m.status = 'active'
      and m.valid_from <= now()
      and (m.valid_until is null or m.valid_until > now())
      and (
        m.namespace_type = 'account'
        or (
          p.project_id <> ''
          and m.namespace_type = 'project'
          and m.namespace_id = p.project_id
        )
        or (
          p.session_id <> ''
          and m.namespace_type = 'session'
          and m.namespace_id = p.session_id
        )
      )
  ),
  scored as (
    select
      e.*,
      (
        e.vector_score * 0.62
        + least(1.0, e.fts_score * 4.0) * 0.20
        + e.trigram_score * 0.15
        + case e.authority
            when 'user_explicit' then 0.03
            when 'user_confirmed' then 0.02
            else 0.0
          end
      )::double precision as final_score
    from eligible e
  )
  select
    s.id,
    s.user_id,
    s.layer,
    s.authority,
    s.namespace_type,
    s.namespace_id,
    s.subject_key,
    s.conflict_key,
    s.content,
    s.content_hash,
    s.source_event_id,
    s.status,
    s.confidence,
    s.priority,
    s.pinned,
    s.valid_from,
    s.valid_until,
    s.supersedes_id,
    s.superseded_by_id,
    s.version,
    s.use_count,
    s.last_used_at,
    s.metadata,
    s.search_document,
    s.created_at,
    s.updated_at
  from scored s
  where s.query_text <> ''
    and (
      s.vector_score >= 0.34
      or s.fts_score > 0
      or s.trigram_score >= 0.10
    )
  order by
    s.final_score desc,
    case s.authority
      when 'user_explicit' then 4
      when 'user_confirmed' then 3
      when 'migrated' then 2
      else 1
    end desc,
    s.priority desc,
    s.updated_at desc
  limit (select result_limit from params);
$$;

grant execute on function public.search_assistant_memory_candidates_v4(
  text, text, text, text, extensions.vector, text, integer
) to authenticated;

commit;

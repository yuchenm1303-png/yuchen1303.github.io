-- AI Ledger 智能体统计账号聚合表 v1
-- 仅保存按账号、设备、日期聚合的数值；不保存任务目标、结果、应用名称、动作文本或逐次 Token 事件。

begin;

create table if not exists public.agent_analytics_daily_v1 (
  user_id uuid not null references auth.users(id) on delete cascade,
  device_id text not null check (char_length(device_id) between 1 and 120),
  date_key date not null,
  first_activity_at_millis bigint not null default 0 check (first_activity_at_millis >= 0),
  last_activity_at_millis bigint not null default 0 check (last_activity_at_millis >= 0),
  chat_calls bigint not null default 0 check (chat_calls >= 0),
  chat_failures bigint not null default 0 check (chat_failures >= 0),
  agent_tasks bigint not null default 0 check (agent_tasks >= 0),
  completed_tasks bigint not null default 0 check (completed_tasks >= 0),
  autonomous_completed_tasks bigint not null default 0 check (autonomous_completed_tasks >= 0),
  assisted_completed_tasks bigint not null default 0 check (assisted_completed_tasks >= 0),
  failed_tasks bigint not null default 0 check (failed_tasks >= 0),
  paused_tasks bigint not null default 0 check (paused_tasks >= 0),
  cancelled_tasks bigint not null default 0 check (cancelled_tasks >= 0),
  budget_exceeded_tasks bigint not null default 0 check (budget_exceeded_tasks >= 0),
  model_calls bigint not null default 0 check (model_calls >= 0),
  model_failures bigint not null default 0 check (model_failures >= 0),
  agent_model_turns bigint not null default 0 check (agent_model_turns >= 0),
  input_tokens bigint not null default 0 check (input_tokens >= 0),
  output_tokens bigint not null default 0 check (output_tokens >= 0),
  reasoning_tokens bigint not null default 0 check (reasoning_tokens >= 0),
  cached_input_tokens bigint not null default 0 check (cached_input_tokens >= 0),
  total_tokens bigint not null default 0 check (total_tokens >= 0),
  provider_tokens bigint not null default 0 check (provider_tokens >= 0),
  estimated_tokens bigint not null default 0 check (estimated_tokens >= 0),
  model_latency_ms bigint not null default 0 check (model_latency_ms >= 0),
  request_bytes bigint not null default 0 check (request_bytes >= 0),
  response_bytes bigint not null default 0 check (response_bytes >= 0),
  task_duration_ms bigint not null default 0 check (task_duration_ms >= 0),
  executed_actions bigint not null default 0 check (executed_actions >= 0),
  successful_actions bigint not null default 0 check (successful_actions >= 0),
  failed_actions bigint not null default 0 check (failed_actions >= 0),
  observations bigint not null default 0 check (observations >= 0),
  reobservations bigint not null default 0 check (reobservations >= 0),
  rejected_plans bigint not null default 0 check (rejected_plans >= 0),
  execution_failures bigint not null default 0 check (execution_failures >= 0),
  confirmation_requests bigint not null default 0 check (confirmation_requests >= 0),
  confirmations_accepted bigint not null default 0 check (confirmations_accepted >= 0),
  user_input_requests bigint not null default 0 check (user_input_requests >= 0),
  user_inputs_submitted bigint not null default 0 check (user_inputs_submitted >= 0),
  user_takeovers bigint not null default 0 check (user_takeovers >= 0),
  takeover_resumes bigint not null default 0 check (takeover_resumes >= 0),
  web_searches bigint not null default 0 check (web_searches >= 0),
  image_requests bigint not null default 0 check (image_requests >= 0),
  updated_at timestamptz not null default now(),
  primary key (user_id, device_id, date_key)
);

create index if not exists agent_analytics_daily_v1_user_date_idx
  on public.agent_analytics_daily_v1 (user_id, date_key desc);

alter table public.agent_analytics_daily_v1 enable row level security;

drop policy if exists agent_analytics_daily_v1_select_own on public.agent_analytics_daily_v1;
create policy agent_analytics_daily_v1_select_own
  on public.agent_analytics_daily_v1
  for select
  to authenticated
  using (auth.uid() = user_id);

drop policy if exists agent_analytics_daily_v1_insert_own on public.agent_analytics_daily_v1;
create policy agent_analytics_daily_v1_insert_own
  on public.agent_analytics_daily_v1
  for insert
  to authenticated
  with check (auth.uid() = user_id);

drop policy if exists agent_analytics_daily_v1_update_own on public.agent_analytics_daily_v1;
create policy agent_analytics_daily_v1_update_own
  on public.agent_analytics_daily_v1
  for update
  to authenticated
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

drop policy if exists agent_analytics_daily_v1_delete_own on public.agent_analytics_daily_v1;
create policy agent_analytics_daily_v1_delete_own
  on public.agent_analytics_daily_v1
  for delete
  to authenticated
  using (auth.uid() = user_id);

grant select, insert, update, delete on public.agent_analytics_daily_v1 to authenticated;

create or replace function public.get_agent_analytics_daily_rollup(
  p_since_date date,
  p_exclude_device_id text
)
returns table (
  date_key date,
  first_activity_at_millis bigint,
  last_activity_at_millis bigint,
  chat_calls bigint,
  chat_failures bigint,
  agent_tasks bigint,
  completed_tasks bigint,
  autonomous_completed_tasks bigint,
  assisted_completed_tasks bigint,
  failed_tasks bigint,
  paused_tasks bigint,
  cancelled_tasks bigint,
  budget_exceeded_tasks bigint,
  model_calls bigint,
  model_failures bigint,
  agent_model_turns bigint,
  input_tokens bigint,
  output_tokens bigint,
  reasoning_tokens bigint,
  cached_input_tokens bigint,
  total_tokens bigint,
  provider_tokens bigint,
  estimated_tokens bigint,
  model_latency_ms bigint,
  request_bytes bigint,
  response_bytes bigint,
  task_duration_ms bigint,
  executed_actions bigint,
  successful_actions bigint,
  failed_actions bigint,
  observations bigint,
  reobservations bigint,
  rejected_plans bigint,
  execution_failures bigint,
  confirmation_requests bigint,
  confirmations_accepted bigint,
  user_input_requests bigint,
  user_inputs_submitted bigint,
  user_takeovers bigint,
  takeover_resumes bigint,
  web_searches bigint,
  image_requests bigint
)
language sql
stable
security invoker
set search_path = public
as $$
  select
    a.date_key,
    coalesce(min(nullif(a.first_activity_at_millis, 0)), 0)::bigint as first_activity_at_millis,
    coalesce(max(a.last_activity_at_millis), 0)::bigint as last_activity_at_millis,
    coalesce(sum(a.chat_calls), 0)::bigint as chat_calls,
    coalesce(sum(a.chat_failures), 0)::bigint as chat_failures,
    coalesce(sum(a.agent_tasks), 0)::bigint as agent_tasks,
    coalesce(sum(a.completed_tasks), 0)::bigint as completed_tasks,
    coalesce(sum(a.autonomous_completed_tasks), 0)::bigint as autonomous_completed_tasks,
    coalesce(sum(a.assisted_completed_tasks), 0)::bigint as assisted_completed_tasks,
    coalesce(sum(a.failed_tasks), 0)::bigint as failed_tasks,
    coalesce(sum(a.paused_tasks), 0)::bigint as paused_tasks,
    coalesce(sum(a.cancelled_tasks), 0)::bigint as cancelled_tasks,
    coalesce(sum(a.budget_exceeded_tasks), 0)::bigint as budget_exceeded_tasks,
    coalesce(sum(a.model_calls), 0)::bigint as model_calls,
    coalesce(sum(a.model_failures), 0)::bigint as model_failures,
    coalesce(sum(a.agent_model_turns), 0)::bigint as agent_model_turns,
    coalesce(sum(a.input_tokens), 0)::bigint as input_tokens,
    coalesce(sum(a.output_tokens), 0)::bigint as output_tokens,
    coalesce(sum(a.reasoning_tokens), 0)::bigint as reasoning_tokens,
    coalesce(sum(a.cached_input_tokens), 0)::bigint as cached_input_tokens,
    coalesce(sum(a.total_tokens), 0)::bigint as total_tokens,
    coalesce(sum(a.provider_tokens), 0)::bigint as provider_tokens,
    coalesce(sum(a.estimated_tokens), 0)::bigint as estimated_tokens,
    coalesce(sum(a.model_latency_ms), 0)::bigint as model_latency_ms,
    coalesce(sum(a.request_bytes), 0)::bigint as request_bytes,
    coalesce(sum(a.response_bytes), 0)::bigint as response_bytes,
    coalesce(sum(a.task_duration_ms), 0)::bigint as task_duration_ms,
    coalesce(sum(a.executed_actions), 0)::bigint as executed_actions,
    coalesce(sum(a.successful_actions), 0)::bigint as successful_actions,
    coalesce(sum(a.failed_actions), 0)::bigint as failed_actions,
    coalesce(sum(a.observations), 0)::bigint as observations,
    coalesce(sum(a.reobservations), 0)::bigint as reobservations,
    coalesce(sum(a.rejected_plans), 0)::bigint as rejected_plans,
    coalesce(sum(a.execution_failures), 0)::bigint as execution_failures,
    coalesce(sum(a.confirmation_requests), 0)::bigint as confirmation_requests,
    coalesce(sum(a.confirmations_accepted), 0)::bigint as confirmations_accepted,
    coalesce(sum(a.user_input_requests), 0)::bigint as user_input_requests,
    coalesce(sum(a.user_inputs_submitted), 0)::bigint as user_inputs_submitted,
    coalesce(sum(a.user_takeovers), 0)::bigint as user_takeovers,
    coalesce(sum(a.takeover_resumes), 0)::bigint as takeover_resumes,
    coalesce(sum(a.web_searches), 0)::bigint as web_searches,
    coalesce(sum(a.image_requests), 0)::bigint as image_requests
  from public.agent_analytics_daily_v1 as a
  where a.user_id = auth.uid()
    and a.date_key >= coalesce(p_since_date, date '2020-01-01')
    and a.device_id <> coalesce(p_exclude_device_id, '')
  group by a.date_key
  order by a.date_key asc;
$$;

revoke all on function public.get_agent_analytics_daily_rollup(date, text) from public;
grant execute on function public.get_agent_analytics_daily_rollup(date, text) to authenticated;

commit;

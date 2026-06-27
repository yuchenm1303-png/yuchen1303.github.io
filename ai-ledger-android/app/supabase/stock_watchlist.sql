-- AI Ledger：账号自选股表与 RLS
-- 在 Supabase SQL Editor 中完整执行本文件。

create table if not exists public.stock_watchlist (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    symbol text not null
        check (
            char_length(symbol) = 6
            and symbol ~ '^[0-9A-Z]{6}$'
        ),
    display_name text not null
        check (
            char_length(trim(display_name)) >= 1
            and char_length(display_name) <= 40
        ),
    market text not null default ''
        check (char_length(market) <= 20),
    sort_order integer not null default 0
        check (sort_order >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint stock_watchlist_user_symbol_unique unique (user_id, symbol)
);

create index if not exists stock_watchlist_user_sort_idx
on public.stock_watchlist (user_id, sort_order asc, updated_at desc);

create or replace function public.update_stock_watchlist_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists stock_watchlist_updated_at_trigger
on public.stock_watchlist;

create trigger stock_watchlist_updated_at_trigger
before update on public.stock_watchlist
for each row
execute function public.update_stock_watchlist_updated_at();

alter table public.stock_watchlist enable row level security;

revoke all on table public.stock_watchlist from anon;
grant select, insert, update, delete
on table public.stock_watchlist
to authenticated;

drop policy if exists "用户读取自己的自选股"
on public.stock_watchlist;

drop policy if exists "用户添加自己的自选股"
on public.stock_watchlist;

drop policy if exists "用户修改自己的自选股"
on public.stock_watchlist;

drop policy if exists "用户删除自己的自选股"
on public.stock_watchlist;

create policy "用户读取自己的自选股"
on public.stock_watchlist
for select
to authenticated
using (
    (select auth.uid()) = user_id
);

create policy "用户添加自己的自选股"
on public.stock_watchlist
for insert
to authenticated
with check (
    (select auth.uid()) = user_id
);

create policy "用户修改自己的自选股"
on public.stock_watchlist
for update
to authenticated
using (
    (select auth.uid()) = user_id
)
with check (
    (select auth.uid()) = user_id
);

create policy "用户删除自己的自选股"
on public.stock_watchlist
for delete
to authenticated
using (
    (select auth.uid()) = user_id
);

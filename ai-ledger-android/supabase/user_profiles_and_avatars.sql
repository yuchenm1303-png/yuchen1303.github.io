-- AI Ledger 用户昵称与头像初始化脚本
-- 在 Supabase Dashboard -> SQL Editor 中完整执行一次即可。

begin;

create table if not exists public.user_profiles (
    user_id uuid primary key references auth.users(id) on delete cascade,
    display_name text not null default '',
    avatar_path text,
    avatar_version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint user_profiles_display_name_length
        check (char_length(display_name) between 1 and 24)
);

alter table public.user_profiles enable row level security;

grant select, insert, update on public.user_profiles to authenticated;

-- 让已注册账号也能立即拥有一条资料记录。
insert into public.user_profiles (user_id, display_name)
select
    id,
    left(
        coalesce(
            nullif(split_part(coalesce(email, ''), '@', 1), ''),
            'AI Ledger 用户'
        ),
        24
    )
from auth.users
on conflict (user_id) do nothing;

drop policy if exists "用户读取自己的资料" on public.user_profiles;
create policy "用户读取自己的资料"
on public.user_profiles
for select
to authenticated
using (auth.uid() = user_id);

drop policy if exists "用户创建自己的资料" on public.user_profiles;
create policy "用户创建自己的资料"
on public.user_profiles
for insert
to authenticated
with check (auth.uid() = user_id);

drop policy if exists "用户修改自己的资料" on public.user_profiles;
create policy "用户修改自己的资料"
on public.user_profiles
for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

-- 创建私有头像桶。App 只上传 512x512 WebP，限制为 1 MiB。
insert into storage.buckets (
    id,
    name,
    public,
    file_size_limit,
    allowed_mime_types
)
values (
    'user-avatars',
    'user-avatars',
    false,
    1048576,
    array['image/webp']::text[]
)
on conflict (id) do update set
    public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

-- 头像统一保存在 user-avatars/{userId}/avatar.webp。
-- 每个登录用户只能访问自己 userId 文件夹下的头像。
drop policy if exists "用户读取自己的头像" on storage.objects;
create policy "用户读取自己的头像"
on storage.objects
for select
to authenticated
using (
    bucket_id = 'user-avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "用户上传自己的头像" on storage.objects;
create policy "用户上传自己的头像"
on storage.objects
for insert
to authenticated
with check (
    bucket_id = 'user-avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "用户更新自己的头像" on storage.objects;
create policy "用户更新自己的头像"
on storage.objects
for update
to authenticated
using (
    bucket_id = 'user-avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
)
with check (
    bucket_id = 'user-avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "用户删除自己的头像" on storage.objects;
create policy "用户删除自己的头像"
on storage.objects
for delete
to authenticated
using (
    bucket_id = 'user-avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
);

commit;

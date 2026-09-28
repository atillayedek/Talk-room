create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text not null,
  username text not null check (char_length(username) between 2 and 50),
  bio text not null default '',
  website text not null default '',
  avatar_url text,
  updated_at timestamptz not null default now()
);

create table if not exists public.rooms (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  name text not null check (char_length(name) between 2 and 80),
  password_hash text,
  is_private boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists public.room_members (
  room_id uuid not null references public.rooms(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  joined_at timestamptz not null default now(),
  primary key (room_id, user_id)
);

create index if not exists rooms_created_at_idx on public.rooms(created_at desc);
create index if not exists rooms_owner_id_idx on public.rooms(owner_id);
create index if not exists room_members_user_id_idx on public.room_members(user_id);

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists profiles_touch_updated_at on public.profiles;
create trigger profiles_touch_updated_at
before update on public.profiles
for each row execute function public.touch_updated_at();

alter table public.profiles enable row level security;
alter table public.rooms enable row level security;
alter table public.room_members enable row level security;

drop policy if exists "Profiles are readable by authenticated users" on public.profiles;
create policy "Profiles are readable by authenticated users"
on public.profiles for select
to authenticated
using (true);

drop policy if exists "Users insert own profile" on public.profiles;
create policy "Users insert own profile"
on public.profiles for insert
to authenticated
with check ((select auth.uid()) = id);

drop policy if exists "Users update own profile" on public.profiles;
create policy "Users update own profile"
on public.profiles for update
to authenticated
using ((select auth.uid()) = id)
with check ((select auth.uid()) = id);

drop policy if exists "Rooms are readable by authenticated users" on public.rooms;
create policy "Rooms are readable by authenticated users"
on public.rooms for select
to authenticated
using (true);

drop policy if exists "Members read own memberships" on public.room_members;
create policy "Members read own memberships"
on public.room_members for select
to authenticated
using ((select auth.uid()) = user_id);

-- Room creation, membership writes, password hash checks, and Agora token access
-- are intentionally handled by the Kotlin backend with the service-role key.

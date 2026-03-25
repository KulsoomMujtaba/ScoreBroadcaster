-- =============================================================================
-- Migration: Create profiles table
-- =============================================================================
-- One row per authenticated user. The id matches the Supabase auth.users id so
-- the profile is always owned by exactly one auth user.

create table if not exists public.profiles (
    id            uuid        primary key references auth.users (id) on delete cascade,
    email         text        not null,
    display_name  text        null,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);

-- =============================================================================
-- Row Level Security
-- =============================================================================

alter table public.profiles enable row level security;

-- Each authenticated user may only see their own row.
create policy "profiles_select_own"
    on public.profiles
    for select
    using (auth.uid() = id);

-- Each authenticated user may insert their own row.
create policy "profiles_insert_own"
    on public.profiles
    for insert
    with check (auth.uid() = id);

-- Each authenticated user may update their own row.
create policy "profiles_update_own"
    on public.profiles
    for update
    using (auth.uid() = id)
    with check (auth.uid() = id);

-- =============================================================================
-- Migration: Create matches table (metadata only)
-- =============================================================================
-- One row per match created by an authenticated user.
-- Only metadata is stored here — ball-by-ball events are excluded from remote
-- sync and remain local-only (see FUTURE CONTEXT in problem statement).
--
-- The id matches the local Room match localId so the same UUID is used across
-- local Room storage and this remote table.
--
-- team_a_id and team_b_id store the team name as a stable identifier for now;
-- they will be replaced with foreign keys to the teams table in a future phase
-- when match-to-team linking is formalised.

create table if not exists public.matches (
    id                  uuid        primary key,
    user_id             uuid        not null references auth.users (id) on delete cascade,
    team_a_id           text        not null,
    team_b_id           text        not null,
    match_name          text        not null,
    format              text        not null,
    total_overs         integer     not null default 0,
    toss_winner_team_id text        not null,
    toss_decision       text        not null,
    status              text        not null default 'NOT_STARTED',
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

-- =============================================================================
-- Auto-update updated_at on every row change
-- =============================================================================

create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger matches_set_updated_at
    before update on public.matches
    for each row execute function public.set_updated_at();

-- =============================================================================
-- Row Level Security
-- =============================================================================

alter table public.matches enable row level security;

-- Each authenticated user may only see their own matches.
create policy "matches_select_own"
    on public.matches
    for select
    using (auth.uid() = user_id);

-- Each authenticated user may insert their own matches.
create policy "matches_insert_own"
    on public.matches
    for insert
    with check (auth.uid() = user_id);

-- Each authenticated user may update their own matches.
create policy "matches_update_own"
    on public.matches
    for update
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- Each authenticated user may delete their own matches.
create policy "matches_delete_own"
    on public.matches
    for delete
    using (auth.uid() = user_id);

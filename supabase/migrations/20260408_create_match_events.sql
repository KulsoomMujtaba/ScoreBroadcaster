-- =============================================================================
-- Migration: Create match_events table (append-only event log)
-- =============================================================================
-- One row per ball-by-ball delivery event.
-- Events are never updated or deleted — the table is append-only.
-- The full match state is rebuilt at any time by replaying this log in order.
--
-- event_index is a 0-based global index within a match, assigned by the client
-- at insert time.  It equals the sequence number for first-innings events; for
-- second-innings events it is offset by the total number of first-innings events
-- so the entire match can be sorted by a single column.
--
-- payload stores all delivery details as JSONB (innings number, runs, extras,
-- dismissal fields, bowler/batter stamps).  Storing a typed JSON blob instead
-- of many nullable columns keeps the schema stable as the BallEvent model evolves
-- without requiring new migrations for every new optional field.

create table if not exists public.match_events (
    id           text        primary key,
    match_id     uuid        not null references public.matches (id) on delete cascade,
    user_id      uuid        not null references auth.users (id) on delete cascade,
    event_index  integer     not null,
    event_type   text        not null default 'BALL',
    payload      jsonb       not null,
    created_at   timestamptz not null default now(),

    unique (match_id, event_index)
);

-- =============================================================================
-- Row Level Security
-- =============================================================================

alter table public.match_events enable row level security;

-- Each authenticated user may only read their own match events.
create policy "match_events_select_own"
    on public.match_events
    for select
    using (auth.uid() = user_id);

-- Each authenticated user may insert their own match events.
create policy "match_events_insert_own"
    on public.match_events
    for insert
    with check (auth.uid() = user_id);

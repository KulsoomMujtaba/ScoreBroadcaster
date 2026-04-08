-- =============================================================================
-- Migration: Add publishing fields to matches + viewer RLS policies
-- =============================================================================
-- Adds is_published and share_code to the matches table so that a scorer can
-- publish a match and generate a shareable code.  Viewers (any authenticated
-- user) can then read the match and its events using only the share code.
--
-- is_published — boolean flag set to true when the scorer publishes the match.
-- share_code   — unique 7-character alphanumeric string generated on publish.
--                NULL until the match is published.

alter table public.matches
    add column if not exists is_published boolean     not null default false,
    add column if not exists share_code   text        unique;

-- =============================================================================
-- Viewer read policy for matches
-- =============================================================================
-- Any authenticated user can read a match row when it is published.
-- Combined with the existing "matches_select_own" policy (union semantics),
-- a scorer always sees their own matches AND any published match is readable
-- by all authenticated users via share code lookup.

create policy "matches_select_published"
    on public.matches
    for select
    using (is_published = true);

-- =============================================================================
-- Viewer read policy for match_events
-- =============================================================================
-- Any authenticated user can read events for a published match.
-- The sub-select checks the parent matches row so no extra join is needed.

create policy "match_events_select_published"
    on public.match_events
    for select
    using (
        exists (
            select 1
            from public.matches m
            where m.id = match_id
              and m.is_published = true
        )
    );

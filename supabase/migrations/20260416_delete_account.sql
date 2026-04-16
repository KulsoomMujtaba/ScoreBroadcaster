-- =============================================================================
-- Migration: delete_account RPC function
-- =============================================================================
-- Allows an authenticated user to permanently delete their own account and all
-- associated data. The function runs as the database owner (SECURITY DEFINER)
-- so it has the privileges required to delete from the protected auth.users
-- table. auth.uid() ensures users can only ever delete their own row.
--
-- Deletion order:
--   1. team_players    — child of teams; no user_id column, must go before teams
--   2. teams           — owned by user
--   3. players         — owned by user
--   4. match_events    — owned by user (also cascades from matches)
--   5. matches         — owned by user (also cascades from auth.users)
--   6. profiles        — owned by user (also cascades from auth.users)
--   7. auth.users      — the auth record itself; cascades any remaining rows
-- =============================================================================

CREATE OR REPLACE FUNCTION public.delete_account()
    RETURNS void
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = ''
AS
$$
DECLARE
    uid uuid := auth.uid();
BEGIN
    -- Require a signed-in user; guard against accidental unauthenticated calls.
    IF uid IS NULL THEN
        RAISE EXCEPTION 'delete_account: not authenticated';
    END IF;

    -- 1. Remove team–player associations for teams owned by this user.
    DELETE FROM public.team_players
    WHERE team_id IN (
        SELECT id FROM public.teams WHERE user_id = uid
    );

    -- 2. Remove teams owned by this user.
    DELETE FROM public.teams WHERE user_id = uid;

    -- 3. Remove private player profiles owned by this user.
    DELETE FROM public.players WHERE user_id = uid;

    -- 4. Remove match events owned by this user (cascade would handle this too,
    --    but explicit deletion avoids relying on undocumented cascade chains).
    DELETE FROM public.match_events WHERE user_id = uid;

    -- 5. Remove matches owned by this user.
    DELETE FROM public.matches WHERE user_id = uid;

    -- 6. Remove the profile row owned by this user.
    DELETE FROM public.profiles WHERE id = uid;

    -- 7. Delete the auth record. This cascades any remaining rows that reference
    --    auth.users and invalidates the user's session tokens.
    DELETE FROM auth.users WHERE id = uid;
END;
$$;

-- Allow any authenticated user to call this function on their own behalf.
-- Revoke first to ensure idempotency if the migration is reapplied.
REVOKE EXECUTE ON FUNCTION public.delete_account() FROM authenticated;
GRANT EXECUTE ON FUNCTION public.delete_account() TO authenticated;

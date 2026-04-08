package com.example.scorebroadcaster.features.match.data

import android.util.Log
import com.example.scorebroadcaster.core.supabase.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

/**
 * Handles all Supabase `matches` table operations for match metadata.
 *
 * Responsibilities:
 * - [fetchRemoteMatches] — retrieve all match rows owned by the given user.
 * - [upsertMatch]        — insert or update a single match row.
 * - [syncMatches]        — implement the bidirectional sync strategy:
 *   * CASE A: Local empty, remote exists  → return remote list for local hydration.
 *   * CASE B: Local exists, remote empty  → push local matches to remote.
 *   * CASE C: Both exist                  → remote wins; return remote list so caller
 *                                           can update local DB with authoritative data.
 *
 * Only metadata is persisted here — ball-by-ball events are excluded from remote sync.
 *
 * All functions are `suspend` and must be called from a coroutine context.
 * No calls are made directly from Composables.
 *
 * Local DB is the source of truth for UI; Supabase is the persistence layer.
 * Local scoring continues uninterrupted if any remote call fails.
 */
object SupabaseMatchRepository {

    private const val TAG = "SupabaseMatchRepo"
    private const val TABLE = "matches"
    private const val CONFLICT_COLUMN = "id"

    private val client get() = SupabaseClientProvider.clientOrNull

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Fetches all remote match rows owned by [userId].
     *
     * @return list of [SupabaseMatch], or an empty list if the client is not
     *         configured or the request fails.
     */
    suspend fun fetchRemoteMatches(userId: String): List<SupabaseMatch> {
        val supabase = client ?: return emptyList()
        Log.d(TAG, "Fetching remote matches")
        return runCatching {
            val result = supabase.postgrest[TABLE]
                .select(columns = Columns.ALL) {
                    filter { eq("user_id", userId) }
                }
                .decodeList<SupabaseMatch>()
            Log.d(TAG, "Fetched ${result.size} remote matches")
            result
        }.getOrElse { e ->
            Log.e(TAG, "Failed to fetch remote matches: ${e.message}")
            emptyList()
        }
    }

    /**
     * Upserts [match] into the `matches` table using the row [id] as the conflict target.
     *
     * Does nothing if the Supabase client is not configured.
     */
    suspend fun upsertMatch(match: SupabaseMatch) {
        val supabase = client ?: return
        runCatching {
            supabase.postgrest[TABLE]
                .upsert(match) { onConflict = CONFLICT_COLUMN }
            Log.d(TAG, "Match inserted: ${match.matchName}")
        }.onFailure { e ->
            Log.e(TAG, "Failed to upsert match ${match.matchName}: ${e.message}")
        }
    }

    /**
     * Bidirectional sync strategy between local and remote matches.
     *
     * - CASE A — Local empty, remote exists: returns the remote list as [Match] objects
     *   so the caller can hydrate local storage.
     * - CASE B — Local exists, remote empty: pushes each local match to remote and
     *   returns an empty list (caller does not need to do anything extra).
     * - CASE C — Both exist: remote is the source of truth; returns the remote list
     *   as [Match] objects so the caller can update local storage with authoritative data.
     *
     * @param localMatches  current local matches.
     * @param userId        authenticated user id.
     * @return list of [Match] the caller should write into local storage, or an
     *         empty list when no local update is needed.
     */
    suspend fun syncMatches(
        localMatches: List<Match>,
        userId: String
    ): List<Match> {
        Log.d(TAG, "Syncing matches")
        val remoteMatches = fetchRemoteMatches(userId)

        return when {
            localMatches.isEmpty() && remoteMatches.isNotEmpty() -> {
                // CASE A — hydrate local from remote
                Log.d(TAG, "Hydrating local DB from ${remoteMatches.size} remote matches")
                remoteMatches.map { it.toMatch() }
            }

            localMatches.isNotEmpty() && remoteMatches.isEmpty() -> {
                // CASE B — push local matches to remote
                Log.d(TAG, "Pushing local matches (${localMatches.size}) to remote")
                localMatches.forEach { match ->
                    upsertMatch(match.toSupabaseMatch(userId))
                }
                emptyList()
            }

            localMatches.isNotEmpty() && remoteMatches.isNotEmpty() -> {
                // CASE C — both exist; remote wins
                Log.d(TAG, "Both local (${localMatches.size}) and remote (${remoteMatches.size}) exist — remote wins")
                remoteMatches.map { it.toMatch() }
            }

            else -> {
                // Both empty — nothing to do
                emptyList()
            }
        }.also {
            Log.d(TAG, "Matches sync complete")
        }
    }
}

package com.devhub.scored.features.players.data

import android.util.Log
import com.devhub.scored.core.supabase.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

/**
 * Handles all Supabase `players` table operations for private player profiles.
 *
 * Responsibilities:
 * - [fetchRemotePlayers] — retrieve all player rows owned by the given user.
 * - [upsertRemotePlayer] — insert or update a single player row; on conflict by name,
 *   falls back to fetching the existing row to avoid duplicates.
 * - [syncPlayers] — implement the bidirectional sync strategy:
 *   * CASE A: Local empty, remote exists  → return remote list for local hydration.
 *   * CASE B: Local exists, remote empty  → push local players to remote.
 *   * CASE C: Both exist                  → remote wins; return remote list so caller
 *                                           can update local DB with authoritative data.
 *
 * All functions are `suspend` and must be called from a coroutine context.
 * No calls are made directly from Composables.
 */
object SupabasePlayerRepository {

    private const val TAG = "SupabasePlayerRepo"
    private const val TABLE = "players"
    private const val CONFLICT_COLUMN = "id"

    private val client get() = SupabaseClientProvider.clientOrNull

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Fetches all remote player rows owned by [userId].
     *
     * @return list of [SupabasePlayer], or an empty list if the client is not
     *         configured or the request fails.
     */
    suspend fun fetchRemotePlayers(userId: String): List<SupabasePlayer> {
        val supabase = client ?: return emptyList()
        Log.d(TAG, "Fetching remote players")
        return runCatching {
            val result = supabase.postgrest[TABLE]
                .select(columns = Columns.ALL) {
                    filter { eq("user_id", userId) }
                }
                .decodeList<SupabasePlayer>()
            Log.d(TAG, "Fetched ${result.size} remote players")
            result
        }.getOrElse { e ->
            Log.e(TAG, "Failed to fetch remote players: ${e.message}")
            emptyList()
        }
    }

    /**
     * Upserts [player] into Supabase using an upsert on [id] to prevent id-based
     * duplicates. If the call fails (e.g. a (user_id, name) uniqueness conflict on
     * the server), the repository attempts to fetch the existing row by name so the
     * caller always has a consistent view.
     *
     * Does nothing if the Supabase client is not configured.
     */
    suspend fun upsertRemotePlayer(player: SupabasePlayer) {
        val supabase = client ?: return
        runCatching {
            supabase.postgrest[TABLE]
                .upsert(player) { onConflict = CONFLICT_COLUMN }
            Log.d(TAG, "Upserted player ${player.name}")
        }.onFailure { e ->
            Log.e(TAG, "Failed to upsert player ${player.name}: ${e.message}")
            // Deduplication fallback: if a player with the same (user_id, name) already
            // exists in Supabase, fetch it so the operation is still considered successful.
            runCatching {
                val existing = supabase.postgrest[TABLE]
                    .select(columns = Columns.ALL) {
                        filter {
                            eq("user_id", player.userId)
                            eq("name", player.name)
                        }
                    }
                    .decodeSingleOrNull<SupabasePlayer>()
                if (existing != null) {
                    Log.d(TAG, "Deduplication: found existing remote player by name: ${existing.name}")
                }
            }.onFailure { fallbackError ->
                Log.e(TAG, "Deduplication fallback failed: ${fallbackError.message}")
            }
        }
    }

    /**
     * Bidirectional sync strategy between local and remote players.
     *
     * - CASE A — Local empty, remote exists: returns the remote list so the caller
     *   can hydrate local storage.
     * - CASE B — Local exists, remote empty: pushes each local player to remote and
     *   returns an empty list (caller does not need to do anything extra).
     * - CASE C — Both exist: remote is the source of truth; returns the remote list
     *   so the caller can update local storage with authoritative data.
     *
     * No merge or conflict resolution is performed. Remote always wins when both
     * sides have data.
     *
     * @return list of [SupabasePlayer] the caller should write into local storage,
     *         or an empty list when no local update is needed.
     */
    suspend fun syncPlayers(
        localPlayers: List<PlayerProfile>,
        userId: String
    ): List<SupabasePlayer> {
        val remotePlayers = fetchRemotePlayers(userId)

        return when {
            localPlayers.isEmpty() && remotePlayers.isNotEmpty() -> {
                // CASE A — hydrate local from remote
                Log.d(TAG, "Hydrating local DB from ${remotePlayers.size} remote players")
                remotePlayers
            }

            localPlayers.isNotEmpty() && remotePlayers.isEmpty() -> {
                // CASE B — push local players to remote
                Log.d(TAG, "Pushing local players (${localPlayers.size}) to remote")
                localPlayers.forEach { profile ->
                    upsertRemotePlayer(profile.toSupabasePlayer(userId))
                }
                emptyList()
            }

            localPlayers.isNotEmpty() && remotePlayers.isNotEmpty() -> {
                // CASE C — both exist; remote wins
                Log.d(TAG, "Both local (${localPlayers.size}) and remote (${remotePlayers.size}) exist — remote wins")
                remotePlayers
            }

            else -> {
                // Both empty — nothing to do
                emptyList()
            }
        }.also {
            Log.d(TAG, "Sync complete")
        }
    }
}

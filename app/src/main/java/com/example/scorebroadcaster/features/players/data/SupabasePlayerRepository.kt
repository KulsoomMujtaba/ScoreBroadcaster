package com.example.scorebroadcaster.features.players.data

import android.util.Log
import com.example.scorebroadcaster.core.supabase.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

/**
 * Handles all Supabase `players` table operations for private player profiles.
 *
 * Responsibilities:
 * - [fetchRemotePlayers] — retrieve all player rows owned by the given user.
 * - [insertRemotePlayer] — insert a single player row; skips silently if the
 *   client is not configured or the player already exists (id-based check).
 * - [syncLocalPlayersToRemote] — implement the simple one-way sync strategy:
 *   * If local list is empty  → hydrate local from remote (caller applies changes).
 *   * If local list exists    → push all local players to remote.
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
     * Inserts [player] into Supabase using an upsert on [id] to prevent duplicates.
     *
     * Does nothing if the Supabase client is not configured.
     */
    suspend fun insertRemotePlayer(player: SupabasePlayer) {
        val supabase = client ?: return
        runCatching {
            supabase.postgrest[TABLE]
                .upsert(player) { onConflict = CONFLICT_COLUMN }
            Log.d(TAG, "Inserted player ${player.name}")
        }.onFailure { e ->
            Log.e(TAG, "Failed to insert player ${player.name}: ${e.message}")
        }
    }

    /**
     * Implements the simple sync strategy between local and remote:
     *
     * 1. Fetch remote players for [userId].
     * 2. If [localPlayers] is empty → return the remote list so the caller can
     *    hydrate local storage.
     * 3. If [localPlayers] is not empty → push each local player to remote and
     *    return an empty list (caller does not need to do anything extra).
     *
     * No merge or conflict resolution is performed at this stage.
     *
     * @return list of [SupabasePlayer] to hydrate into local storage when local
     *         is empty, otherwise an empty list.
     */
    suspend fun syncLocalPlayersToRemote(
        localPlayers: List<PlayerProfile>,
        userId: String
    ): List<SupabasePlayer> {
        Log.d(TAG, "Syncing local players to remote")
        val remotePlayers = fetchRemotePlayers(userId)
        return if (localPlayers.isEmpty()) {
            Log.d(TAG, "Local is empty — hydrating from ${remotePlayers.size} remote players")
            remotePlayers
        } else {
            Log.d(TAG, "Local exists (${localPlayers.size}) — pushing to remote")
            localPlayers.forEach { profile ->
                insertRemotePlayer(profile.toSupabasePlayer(userId))
            }
            emptyList()
        }
    }
}

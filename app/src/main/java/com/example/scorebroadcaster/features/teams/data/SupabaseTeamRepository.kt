package com.example.scorebroadcaster.features.teams.data

import android.util.Log
import com.example.scorebroadcaster.core.supabase.SupabaseClientProvider
import com.example.scorebroadcaster.features.players.data.Player
import com.example.scorebroadcaster.features.players.data.PlayerProfile
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import java.util.UUID

/**
 * Handles all Supabase `teams` and `team_players` table operations.
 *
 * Responsibilities:
 * - [fetchRemoteTeams]   — retrieve all team rows owned by the given user.
 * - [fetchTeamPlayers]   — retrieve all player associations for a given team.
 * - [upsertTeam]         — insert or update a single team row.
 * - [upsertTeamPlayers]  — replace team–player associations (delete + insert).
 * - [syncTeams]          — implement the bidirectional sync strategy:
 *   * CASE A: Local empty, remote exists  → hydrate local from remote.
 *   * CASE B: Local exists, remote empty  → push local teams to remote.
 *   * CASE C: Both exist                  → remote wins; return remote teams so
 *                                           caller can update local DB.
 *
 * All functions are `suspend` and must be called from a coroutine context.
 * No calls are made directly from Composables.
 *
 * Only players with a non-null [Player.sourceProfileId] are stored in `team_players`.
 * Ad-hoc (name-only) players are excluded from remote sync.
 */
object SupabaseTeamRepository {

    private const val TAG = "SupabaseTeamRepo"
    private const val TEAMS_TABLE = "teams"
    private const val TEAM_PLAYERS_TABLE = "team_players"
    private const val CONFLICT_COLUMN = "id"

    private val client get() = SupabaseClientProvider.clientOrNull

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Fetches all remote team rows owned by [userId].
     *
     * @return list of [SupabaseTeam], or an empty list if the client is not
     *         configured or the request fails.
     */
    suspend fun fetchRemoteTeams(userId: String): List<SupabaseTeam> {
        val supabase = client ?: return emptyList()
        Log.d(TAG, "Fetching remote teams")
        return runCatching {
            val result = supabase.postgrest[TEAMS_TABLE]
                .select(columns = Columns.ALL) {
                    filter { eq("user_id", userId) }
                }
                .decodeList<SupabaseTeam>()
            Log.d(TAG, "Fetched ${result.size} remote teams")
            result
        }.getOrElse { e ->
            Log.e(TAG, "Failed to fetch remote teams: ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetches all player associations for the given [teamId] from `team_players`.
     *
     * @return list of [SupabaseTeamPlayer], or an empty list on failure.
     */
    suspend fun fetchTeamPlayers(teamId: String): List<SupabaseTeamPlayer> {
        val supabase = client ?: return emptyList()
        Log.d(TAG, "Fetching team players for team $teamId")
        return runCatching {
            supabase.postgrest[TEAM_PLAYERS_TABLE]
                .select(columns = Columns.ALL) {
                    filter { eq("team_id", teamId) }
                }
                .decodeList<SupabaseTeamPlayer>()
        }.getOrElse { e ->
            Log.e(TAG, "Failed to fetch team players for $teamId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Upserts [team] into the `teams` table using the row [id] as the conflict target.
     *
     * Does nothing if the Supabase client is not configured.
     */
    suspend fun upsertTeam(team: SupabaseTeam) {
        val supabase = client ?: return
        runCatching {
            supabase.postgrest[TEAMS_TABLE]
                .upsert(team) { onConflict = CONFLICT_COLUMN }
            Log.d(TAG, "Upserted team ${team.name}")
        }.onFailure { e ->
            Log.e(TAG, "Failed to upsert team ${team.name}: ${e.message}")
        }
    }

    /**
     * Replaces all `team_players` rows for [teamId] by deleting existing rows and
     * inserting new ones derived from [players].
     *
     * Only players whose [Player.sourceProfileId] is non-null are stored; ad-hoc
     * players (created by typing a name directly) have no stable profile ID and are
     * therefore excluded from remote sync.
     *
     * Does nothing if the Supabase client is not configured.
     */
    suspend fun upsertTeamPlayers(teamId: String, players: List<Player>) {
        val supabase = client ?: return
        val rows = players
            .filter { it.sourceProfileId != null }
            .map { player ->
                SupabaseTeamPlayer(
                    id = UUID.randomUUID().toString(),
                    teamId = teamId,
                    playerId = player.sourceProfileId!!
                )
            }
        runCatching {
            supabase.postgrest[TEAM_PLAYERS_TABLE].delete {
                filter { eq("team_id", teamId) }
            }
            if (rows.isNotEmpty()) {
                supabase.postgrest[TEAM_PLAYERS_TABLE].insert(rows)
            }
            Log.d(TAG, "Upserted ${rows.size} team players for team $teamId")
        }.onFailure { e ->
            Log.e(TAG, "Failed to upsert team players for $teamId: ${e.message}")
        }
    }

    /**
     * Bidirectional sync strategy between local and remote teams.
     *
     * - CASE A — Local empty, remote exists: fetches team_players for each remote team,
     *   resolves player names from [localPlayerProfiles], and returns the reconstructed
     *   [SavedTeam] list so the caller can hydrate local storage.
     * - CASE B — Local exists, remote empty: pushes each local team and its players to
     *   remote and returns an empty list (caller does not need to do anything extra).
     * - CASE C — Both exist: remote is the source of truth; reconstructs and returns
     *   the remote team list so the caller can overwrite local storage.
     *
     * @param localTeams         current local saved teams.
     * @param userId             authenticated user id.
     * @param localPlayerProfiles local player profiles used to resolve display names when
     *                            hydrating team-player relationships from remote player ids.
     * @return list of [SavedTeam] the caller should write into local storage, or an
     *         empty list when no local update is needed.
     */
    suspend fun syncTeams(
        localTeams: List<SavedTeam>,
        userId: String,
        localPlayerProfiles: List<PlayerProfile>
    ): List<SavedTeam> {
        val remoteTeams = fetchRemoteTeams(userId)

        return when {
            localTeams.isEmpty() && remoteTeams.isNotEmpty() -> {
                // CASE A — hydrate local from remote
                Log.d(TAG, "Hydrating local DB from ${remoteTeams.size} remote teams")
                hydrateTeams(remoteTeams, localPlayerProfiles)
            }

            localTeams.isNotEmpty() && remoteTeams.isEmpty() -> {
                // CASE B — push local teams to remote
                Log.d(TAG, "Pushing local teams (${localTeams.size}) to remote")
                localTeams.forEach { team ->
                    upsertTeam(team.toSupabaseTeam(userId))
                    upsertTeamPlayers(team.id, team.players)
                }
                emptyList()
            }

            localTeams.isNotEmpty() && remoteTeams.isNotEmpty() -> {
                // CASE C — both exist; remote wins
                Log.d(TAG, "Both local (${localTeams.size}) and remote (${remoteTeams.size}) exist — remote wins")
                hydrateTeams(remoteTeams, localPlayerProfiles)
            }

            else -> {
                // Both empty — nothing to do
                emptyList()
            }
        }.also {
            Log.d(TAG, "Teams sync complete")
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Fetches team_players for each remote team and reconstructs [SavedTeam] objects,
     * resolving player display names via [localPlayerProfiles].
     *
     * Players whose id cannot be found in [localPlayerProfiles] (e.g. deleted profiles)
     * are silently omitted from the reconstructed team.
     */
    private suspend fun hydrateTeams(
        remoteTeams: List<SupabaseTeam>,
        localPlayerProfiles: List<PlayerProfile>
    ): List<SavedTeam> {
        Log.d(TAG, "Syncing team players")
        return remoteTeams.map { remoteTeam ->
            val teamPlayers = fetchTeamPlayers(remoteTeam.id)
            val players = teamPlayers.mapNotNull { tp ->
                val profile = localPlayerProfiles.find { it.id == tp.playerId }
                profile?.let { Player(name = it.displayName, sourceProfileId = tp.playerId) }
            }
            SavedTeam(id = remoteTeam.id, name = remoteTeam.name, players = players)
        }
    }
}

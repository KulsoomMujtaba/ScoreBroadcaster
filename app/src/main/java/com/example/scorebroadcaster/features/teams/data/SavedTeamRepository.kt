package com.example.scorebroadcaster.features.teams.data

import com.example.scorebroadcaster.features.players.data.PlayerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Room-backed repository for reusable saved team templates.
 *
 * Replaces the previous in-memory singleton. Teams now persist across app
 * restarts via the [SavedTeamDao] / Room database.
 *
 * Architecture note:
 * - [teams] returns a synchronous snapshot backed by an in-memory StateFlow that
 *   is kept in sync with Room via [observeAll].
 * - [teamFlow] exposes a reactive stream for consumers that prefer Flow.
 * - Mutations ([addTeam], [removeTeam], [updateTeam]) are non-suspending;
 *   Room writes are dispatched on the provided [scope] and the StateFlow updates
 *   automatically when Room confirms the change.
 * - When a non-null [userId] is supplied to [addTeamWithRemote], [updateTeamWithRemote],
 *   or [syncWithRemote], the repository also mirrors changes to Supabase via
 *   [SupabaseTeamRepository].
 *
 * Sync strategy (see [syncWithRemote]):
 * - CASE A: Local empty, remote has data  → hydrate local from remote.
 * - CASE B: Local has data, remote empty  → push local to remote.
 * - CASE C: Both have data               → remote wins; local DB updated from remote.
 */
class SavedTeamRepository(
    private val dao: SavedTeamDao,
    private val scope: CoroutineScope
) {
    /** Reactive stream of all saved teams, ordered by name. */
    val teamFlow: Flow<List<SavedTeam>> = dao.observeAll()
        .map { entities -> entities.map { it.toDomain() } }

    private val _state = teamFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    /** Synchronous snapshot of the current team list (backed by the Room-derived StateFlow). */
    val teams: List<SavedTeam>
        get() = _state.value

    fun addTeam(team: SavedTeam) {
        scope.launch { dao.insert(team.toEntity()) }
    }

    /**
     * Persist [team] locally and, when [userId] is provided, also upsert it and its
     * player associations into Supabase asynchronously so the UI is never blocked.
     */
    fun addTeamWithRemote(team: SavedTeam, userId: String?) {
        scope.launch {
            dao.insert(team.toEntity())
            if (userId != null) {
                SupabaseTeamRepository.upsertTeam(team.toSupabaseTeam(userId))
                SupabaseTeamRepository.upsertTeamPlayers(team.id, team.players)
            }
        }
    }

    fun removeTeam(id: String) {
        scope.launch {
            val entity = dao.getById(id) ?: return@launch
            dao.delete(entity)
        }
    }

    fun updateTeam(team: SavedTeam) {
        scope.launch { dao.update(team.toEntity()) }
    }

    /**
     * Update [team] locally and, when [userId] is provided, also update the remote team
     * and replace its player associations in Supabase (delete + insert strategy).
     */
    fun updateTeamWithRemote(team: SavedTeam, userId: String?) {
        scope.launch {
            dao.update(team.toEntity())
            if (userId != null) {
                SupabaseTeamRepository.upsertTeam(team.toSupabaseTeam(userId))
                SupabaseTeamRepository.upsertTeamPlayers(team.id, team.players)
            }
        }
    }

    fun findById(id: String): SavedTeam? = _state.value.find { it.id == id }

    /**
     * Run the bidirectional sync strategy for [userId]:
     *
     * 1. Read the current local snapshot.
     * 2. Delegate to [SupabaseTeamRepository.syncTeams] which determines the correct
     *    sync case (A, B, or C) and returns the list of [SavedTeam] objects that should
     *    be written into local storage.
     * 3. Insert / replace each returned team into the local Room database.
     *    [SavedTeamDao.insert] uses [androidx.room.OnConflictStrategy.REPLACE], so
     *    existing rows are updated in place — this handles CASE C where remote wins.
     *
     * All work is done off the main thread; callers do not need to suspend.
     *
     * @param localPlayerProfiles local player profiles used to resolve display names when
     *                            reconstructing team–player relationships from remote player ids.
     */
    fun syncWithRemote(userId: String, localPlayerProfiles: List<PlayerProfile>) {
        scope.launch {
            val local = dao.getAll().map { it.toDomain() }
            val toHydrate = SupabaseTeamRepository.syncTeams(local, userId, localPlayerProfiles)
            toHydrate.forEach { team -> dao.insert(team.toEntity()) }
        }
    }
}

package com.example.scorebroadcaster.features.players.data
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Room-backed repository for private (reusable) player profiles.
 *
 * Replaces the previous in-memory singleton. Profiles now persist across app
 * restarts via the [PlayerProfileDao] / Room database.
 *
 * Architecture note:
 * - [players] returns a synchronous snapshot backed by an in-memory StateFlow that
 *   is kept in sync with Room via [observeAll].
 * - [playerFlow] exposes a reactive stream for consumers that prefer Flow.
 * - Mutations ([addPlayer], [removePlayer], [updatePlayer]) are non-suspending;
 *   Room writes are dispatched on the provided [scope] and the StateFlow updates
 *   automatically when Room confirms the change.
 * - Only [PlayerProfile.playerSourceType] == PRIVATE profiles are stored here today.
 * - When a non-null [userId] is supplied to [addPlayer] or [syncWithRemote], the
 *   repository also mirrors changes to Supabase via [SupabasePlayerRepository].
 */
class SavedPlayerRepository(
    private val dao: PlayerProfileDao,
    private val scope: CoroutineScope
) {    /** Reactive stream of all saved profiles, ordered by display name. */
    val playerFlow: Flow<List<PlayerProfile>> = dao.observeAll()
        .map { entities -> entities.map { it.toDomain() } }

    private val _state = playerFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    /** Synchronous snapshot of the current player list (backed by the Room-derived StateFlow). */
    val players: List<PlayerProfile>
        get() = _state.value

    /**
     * Persist [player] locally and, when [userId] is provided, also insert it
     * into Supabase asynchronously so the UI is never blocked.
     */
    fun addPlayer(player: PlayerProfile, userId: String? = null) {
        scope.launch {
            dao.insert(player.toEntity())
            if (userId != null) {
                SupabasePlayerRepository.insertRemotePlayer(player.toSupabasePlayer(userId))
            }
        }
    }

    fun removePlayer(id: String) {
        scope.launch {
            val entity = dao.getById(id) ?: return@launch
            dao.delete(entity)
        }
    }

    fun updatePlayer(player: PlayerProfile) {
        scope.launch { dao.update(player.toEntity()) }
    }

    fun findById(id: String): PlayerProfile? = _state.value.find { it.id == id }

    /**
     * Run the one-way sync strategy for [userId]:
     *
     * 1. Read the current local snapshot.
     * 2. Delegate to [SupabasePlayerRepository.syncLocalPlayersToRemote].
     * 3. If the result is non-empty (local was empty → remote had data), insert
     *    each remote player into the local Room database to hydrate it.
     *
     * All work is done off the main thread; callers do not need to suspend.
     */
    fun syncWithRemote(userId: String) {
        scope.launch {
            val local = dao.getAll().map { it.toDomain() }
            val toHydrate = SupabasePlayerRepository.syncLocalPlayersToRemote(local, userId)
            toHydrate.forEach { remote -> dao.insert(remote.toPlayerProfile().toEntity()) }
        }
    }
}
